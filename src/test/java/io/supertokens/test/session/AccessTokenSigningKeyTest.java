/*
 *    Copyright (c) 2021, VRAI Labs and/or its affiliates. All rights reserved.
 *
 *    This software is licensed under the Apache License, Version 2.0 (the
 *    "License") as published by the Apache Software Foundation.
 *
 *    You may not use this file except in compliance with the License. You may
 *    obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *    License for the specific language governing permissions and limitations
 *    under the License.
 */

package io.supertokens.test.session;

import io.supertokens.ProcessState.EventAndException;
import io.supertokens.ProcessState.PROCESS_STATE;
import io.supertokens.pluginInterface.KeyValueInfo;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.session.SessionStorage;
import io.supertokens.session.accessToken.AccessTokenSigningKey;
import io.supertokens.session.accessToken.AccessTokenSigningKey.KeyInfo;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.TestingProcessManager.TestingProcess;
import io.supertokens.test.Utils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.util.List;

import static org.junit.Assert.*;

public class AccessTokenSigningKeyTest {
    @Rule
    public TestRule watchman = Utils.getOnFailure();

    @AfterClass
    public static void afterTesting() {
        Utils.afterTesting();
    }

    @Before
    public void beforeEach() {
        Utils.reset();
    }

    @Test
    public void legacySigningKeysAreMigratedProperly()
            throws InterruptedException,
            NoSuchAlgorithmException, StorageQueryException, StorageTransactionLogicException {
        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.start(args);

        EventAndException e = process.checkOrWaitForEvent(PROCESS_STATE.STARTED);
        assertNotNull(e);

        io.supertokens.utils.Utils.PubPriKey rsaKeys = io.supertokens.utils.Utils.generateNewPubPriKey();
        String signingKey = rsaKeys.toString();
        KeyValueInfo newKey = new KeyValueInfo(signingKey, System.currentTimeMillis());
        SessionStorage sessionStorage = StorageLayer.getSessionStorage(process.getProcess());
        sessionStorage.setKeyValue("access_token_signing_key", newKey);
        AccessTokenSigningKey accessTokenSigningKeyInstance = AccessTokenSigningKey.getInstance(process.getProcess());
        accessTokenSigningKeyInstance.transferLegacyKeyToNewTable();
        assertEquals(accessTokenSigningKeyInstance.getAllKeys().size(), 1);
        AccessTokenSigningKey.KeyInfo key = accessTokenSigningKeyInstance.getLatestIssuedKey();
        assertEquals(key.createdAtTime, newKey.createdAtTime);
        assertEquals(key.value, newKey.value);
        assertEquals(sessionStorage.getKeyValue("access_token_signing_key"), null);
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void getAllKeysReturnsOrdered()
            throws IOException, InterruptedException,
            InvalidKeyException, NoSuchAlgorithmException, StorageQueryException, StorageTransactionLogicException,
            InvalidKeySpecException, SignatureException {
        Utils.setValueInConfig("access_token_signing_key_update_interval", "0.00027"); // 1 seconds

        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.start(args);

        EventAndException e = process.checkOrWaitForEvent(PROCESS_STATE.STARTED);
        assertNotNull(e);

        io.supertokens.utils.Utils.PubPriKey rsaKeys = io.supertokens.utils.Utils.generateNewPubPriKey();
        String signingKey = rsaKeys.toString();
        KeyValueInfo legacyKey = new KeyValueInfo(signingKey,
                System.currentTimeMillis() - 2000); // 2 seconds in the past

        SessionStorage sessionStorage = StorageLayer.getSessionStorage(process.getProcess());
        sessionStorage.setKeyValue("access_token_signing_key", legacyKey);

        AccessTokenSigningKey accessTokenSigningKeyInstance = AccessTokenSigningKey.getInstance(process.getProcess());
        accessTokenSigningKeyInstance.transferLegacyKeyToNewTable();

        accessTokenSigningKeyInstance.getAllKeys();

        // Wait for access_token_signing_key_update_interval + margin
        Thread.sleep(1500);

        List<KeyInfo> allKeys = accessTokenSigningKeyInstance.getAllKeys();
        assertEquals(allKeys.size(), 3);

        // The first one should be the latest key
        AccessTokenSigningKey.KeyInfo key = accessTokenSigningKeyInstance.getLatestIssuedKey();
        assertEquals(allKeys.get(0).createdAtTime, key.createdAtTime);
        assertEquals(allKeys.get(0).value, key.value);

        // The oldest one should be the legacy key.
        assertEquals(allKeys.get(2).createdAtTime, legacyKey.createdAtTime);
        assertEquals(allKeys.get(2).value, legacyKey.value);

        // Keys should be ordered by createdAtTime descending
        for (int i = 0; i < allKeys.size() - 1; ++i) {
            assertTrue(allKeys.get(i).createdAtTime > allKeys.get(i + 1).createdAtTime);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void getAllKeysFiltersOldKeys()
            throws IOException, InterruptedException,
            InvalidKeyException, NoSuchAlgorithmException, StorageQueryException, StorageTransactionLogicException,
            InvalidKeySpecException, SignatureException {
        Utils.setValueInConfig("access_token_signing_key_update_interval", "0.00027"); // 1 seconds
        Utils.setValueInConfig("access_token_validity", "1");

        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.start(args);

        EventAndException e = process.checkOrWaitForEvent(PROCESS_STATE.STARTED);
        assertNotNull(e);

        AccessTokenSigningKey accessTokenSigningKeyInstance = AccessTokenSigningKey.getInstance(process.getProcess());

        List<KeyInfo> oldKeys = accessTokenSigningKeyInstance.getAllKeys();
        assertEquals(oldKeys.size(), 1);

        // Wait for access_token_signing_key_update_interval + 2 * access_token_validity + margin
        Thread.sleep(3500);

        List<KeyInfo> newKeys = accessTokenSigningKeyInstance.getAllKeys();
        assertEquals(newKeys.size(), 1);

        assertNotEquals(newKeys.get(0).value, oldKeys.get(0).value);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void migratingStaticSigningKeys()
            throws IOException, InterruptedException,
            InvalidKeyException, NoSuchAlgorithmException, StorageQueryException, StorageTransactionLogicException,
            InvalidKeySpecException, SignatureException {
        Utils.setValueInConfig("access_token_signing_key_dynamic", "false");

        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.start(args);

        EventAndException e = process.checkOrWaitForEvent(PROCESS_STATE.STARTED);
        assertNotNull(e);

        io.supertokens.utils.Utils.PubPriKey rsaKeys = io.supertokens.utils.Utils.generateNewPubPriKey();
        String signingKey = rsaKeys.toString();
        KeyValueInfo legacyKey = new KeyValueInfo(signingKey, System.currentTimeMillis() - 2629743830l); // 1 month old

        SessionStorage sessionStorage = StorageLayer.getSessionStorage(process.getProcess());
        sessionStorage.setKeyValue("access_token_signing_key", legacyKey);

        AccessTokenSigningKey accessTokenSigningKeyInstance = AccessTokenSigningKey.getInstance(process.getProcess());
        accessTokenSigningKeyInstance.transferLegacyKeyToNewTable();

        accessTokenSigningKeyInstance.cleanExpiredAccessTokenSigningKeys();
        List<KeyInfo> keys = accessTokenSigningKeyInstance.getAllKeys();
        assertEquals(keys.size(), 1);

        assertEquals(legacyKey.value, keys.get(0).value);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }
}
