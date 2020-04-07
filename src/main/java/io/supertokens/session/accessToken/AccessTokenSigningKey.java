/*
 *    Copyright (c) 2020, VRAI Labs and/or its affiliates. All rights reserved.
 *
 *    This program is licensed under the SuperTokens Community License (the
 *    "License") as published by VRAI Labs. You may not use this file except in
 *    compliance with the License. You are not permitted to transfer or
 *    redistribute this file without express written permission from VRAI Labs.
 *
 *    A copy of the License is available in the file titled
 *    "SuperTokensLicense.pdf" inside this repository or included with your copy of
 *    the software or its source code. If you have not received a copy of the
 *    License, please write to VRAI Labs at team@supertokens.io.
 *
 *    Please read the License carefully before accessing, downloading, copying,
 *    using, modifying, merging, transferring or sharing this software. By
 *    undertaking any of these activities, you indicate your agreement to the terms
 *    of the License.
 *
 *    This program is distributed with certain software that is licensed under
 *    separate terms, as designated in a particular file or component or in
 *    included license documentation. VRAI Labs hereby grants you an additional
 *    permission to link the program and your derivative works with the separately
 *    licensed software that they have included with this program, however if you
 *    modify this program, you shall be solely liable to ensure compliance of the
 *    modified program with the terms of licensing of the separately licensed
 *    software.
 *
 *    Unless required by applicable law or agreed to in writing, this program is
 *    distributed under the License on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 *    CONDITIONS OF ANY KIND, either express or implied. See the License for the
 *    specific language governing permissions and limitations under the License.
 *
 */

package io.supertokens.session.accessToken;

import io.supertokens.Main;
import io.supertokens.ResourceDistributor;
import io.supertokens.config.Config;
import io.supertokens.config.CoreConfig;
import io.supertokens.exceptions.QuitProgramException;
import io.supertokens.output.Logging;
import io.supertokens.pluginInterface.KeyValueInfo;
import io.supertokens.pluginInterface.KeyValueInfoWithLastUpdated;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.noSqlStorage.NoSQLStorage_1;
import io.supertokens.pluginInterface.sqlStorage.SQLStorage;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.utils.Utils;

import java.security.NoSuchAlgorithmException;

public class AccessTokenSigningKey extends ResourceDistributor.SingletonResource {

    private static final String RESOURCE_KEY = "io.supertokens.session.accessToken.AccessTokenSigningKey";
    private final Main main;
    private KeyInfo keyInfo;

    private AccessTokenSigningKey(Main main) {
        this.main = main;
        try {
            this.getKey();
        } catch (StorageQueryException | StorageTransactionLogicException e) {
            Logging.error(main, "Error while fetching access token signing key", false, e);
        }
    }

    public static void init(Main main) {
        AccessTokenSigningKey instance = (AccessTokenSigningKey) main.getResourceDistributor()
                .getResource(RESOURCE_KEY);
        if (instance != null) {
            return;
        }
        main.getResourceDistributor().setResource(RESOURCE_KEY, new AccessTokenSigningKey(main));
    }

    public static AccessTokenSigningKey getInstance(Main main) {
        AccessTokenSigningKey instance = (AccessTokenSigningKey) main.getResourceDistributor()
                .getResource(RESOURCE_KEY);
        if (instance == null) {
            init(main);
        }
        return (AccessTokenSigningKey) main.getResourceDistributor().getResource(RESOURCE_KEY);
    }

    void removeKeyFromMemory() {
        this.keyInfo = null;
    }

    public Utils.PubPriKey getKey() throws StorageQueryException, StorageTransactionLogicException {
        if (this.keyInfo == null) {
            this.keyInfo = maybeGenerateNewKeyAndUpdateInDb();
        }
        return new Utils.PubPriKey(this.keyInfo.value);
    }

    public long getKeyExpiryTime() throws StorageQueryException, StorageTransactionLogicException {
        this.getKey();
        return Long.MAX_VALUE;  // since keys never expire in free version
    }

    private KeyInfo maybeGenerateNewKeyAndUpdateInDb() throws StorageQueryException, StorageTransactionLogicException {
        Storage storage = StorageLayer.getStorageLayer(main);
        CoreConfig config = Config.getConfig(main);

        if (storage.getType() == STORAGE_TYPE.SQL) {

            SQLStorage sqlStorage = (SQLStorage) storage;

            // start transaction
            return sqlStorage.startTransaction(con -> {
                KeyInfo key = null;
                KeyValueInfo keyFromStorage = sqlStorage.getAccessTokenSigningKey_Transaction(con);
                if (keyFromStorage != null) {
                    key = new KeyInfo(keyFromStorage.value, keyFromStorage.createdAtTime);
                }

                if (key == null) {
                    String signingKey;
                    try {
                        Utils.PubPriKey rsaKeys = Utils.generateNewPubPriKey();
                        signingKey = rsaKeys.toString();
                    } catch (NoSuchAlgorithmException e) {
                        throw new StorageTransactionLogicException(e);
                    }
                    key = new KeyInfo(signingKey, System.currentTimeMillis());
                    sqlStorage.setAccessTokenSigningKey_Transaction(con,
                            new KeyValueInfo(key.value, key.createdAtTime));
                }

                sqlStorage.commitTransaction(con);
                return key;

            });
        } else if (storage.getType() == STORAGE_TYPE.NOSQL_1) {
            NoSQLStorage_1 noSQLStorage = (NoSQLStorage_1) storage;

            while (true) {

                KeyInfo key = null;
                KeyValueInfoWithLastUpdated keyFromStorage = noSQLStorage.getAccessTokenSigningKey_Transaction();
                if (keyFromStorage != null) {
                    key = new KeyInfo(keyFromStorage.value, keyFromStorage.createdAtTime);
                }

                if (key == null) {
                    String signingKey;
                    try {
                        Utils.PubPriKey rsaKeys = Utils.generateNewPubPriKey();
                        signingKey = rsaKeys.toString();
                    } catch (NoSuchAlgorithmException e) {
                        throw new StorageTransactionLogicException(e);
                    }
                    key = new KeyInfo(signingKey, System.currentTimeMillis());
                    boolean success = noSQLStorage.setAccessTokenSigningKey_Transaction(
                            new KeyValueInfoWithLastUpdated(key.value, key.createdAtTime,
                                    null));
                    if (!success) {
                        // something else already updated this particular field. So we must try again.
                        continue;
                    }
                }

                return key;

            }
        }

        throw new QuitProgramException("Unsupported storage type detected");
    }

    private static class KeyInfo {
        String value;
        long createdAtTime;

        KeyInfo(String value, long createdAtTime) {
            this.value = value;
            this.createdAtTime = createdAtTime;
        }
    }

}
