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
import io.supertokens.jwt.exceptions.UnsupportedJWTSigningAlgorithmException;
import io.supertokens.pluginInterface.KeyValueInfo;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.jwt.JWTSigningKeyInfo;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.session.SessionStorage;
import io.supertokens.signingkeys.AccessTokenSigningKey;
import io.supertokens.signingkeys.SigningKeys;
import io.supertokens.signingkeys.SigningKeys.KeyInfo;
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
    public void legacySigningKeysAreMigratedProperly() throws InterruptedException, NoSuchAlgorithmException,
            StorageQueryException, StorageTransactionLogicException, TenantOrAppNotFoundException,
            UnsupportedJWTSigningAlgorithmException {
        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.start(args);

        EventAndException e = process.checkOrWaitForEvent(PROCESS_STATE.STARTED);
        assertNotNull(e);

        io.supertokens.utils.Utils.PubPriKey rsaKeys = io.supertokens.utils.Utils.generateNewPubPriKey();
        String signingKey = rsaKeys.toString();
        KeyValueInfo newKey = new KeyValueInfo(signingKey, System.currentTimeMillis());
        SessionStorage sessionStorage = (SessionStorage) StorageLayer.getStorage(process.getProcess());
        sessionStorage.removeAccessTokenSigningKeysBefore(new AppIdentifier(null, null),
                System.currentTimeMillis() + 1000);
        sessionStorage.setKeyValue(new TenantIdentifier(null, null, null), "access_token_signing_key", newKey);
        AccessTokenSigningKey accessTokenSigningKeyInstance = AccessTokenSigningKey.getInstance(process.getProcess());
        accessTokenSigningKeyInstance.transferLegacyKeyToNewTable();
        assertEquals(SigningKeys.getInstance(process.getProcess()).getAllKeys().size(), 2);
        List<KeyInfo> keys = SigningKeys.getInstance(process.getProcess()).getDynamicKeys();
        assertEquals(keys.size(), 1);
        assertEquals(keys.get(0).createdAtTime, newKey.createdAtTime);
        assertEquals(keys.get(0).value, newKey.value);
        assertNull(sessionStorage.getKeyValue(new TenantIdentifier(null, null, null), "access_token_signing_key"));
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void getDynamicKeysReturnsOrdered() throws Exception {
        Utils.setValueInConfig("access_token_dynamic_signing_key_update_interval", "0.00027"); // 1 seconds

        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.start(args);

        EventAndException e = process.checkOrWaitForEvent(PROCESS_STATE.STARTED);
        assertNotNull(e);

        io.supertokens.utils.Utils.PubPriKey rsaKeys = io.supertokens.utils.Utils.generateNewPubPriKey();
        String signingKey = rsaKeys.toString();
        KeyValueInfo legacyKey = new KeyValueInfo(signingKey, System.currentTimeMillis() - 2000);
        // 2 seconds in the past

        SessionStorage sessionStorage = (SessionStorage) StorageLayer.getStorage(process.getProcess());
        sessionStorage.removeAccessTokenSigningKeysBefore(new AppIdentifier(null, null),
                System.currentTimeMillis() + 1000);
        sessionStorage.setKeyValue(new TenantIdentifier(null, null, null), "access_token_signing_key", legacyKey);

        AccessTokenSigningKey accessTokenSigningKeyInstance = AccessTokenSigningKey.getInstance(process.getProcess());
        accessTokenSigningKeyInstance.transferLegacyKeyToNewTable();

        SigningKeys.getInstance(process.getProcess()).getAllKeys();

        // Wait for access_token_dynamic_signing_key_update_interval + margin
        Thread.sleep(1500);

        List<KeyInfo> dynamicKeys = SigningKeys.getInstance(process.getProcess()).getDynamicKeys();
        // We get a migrated + 1 expired and 1 fresh dynamic key
        assertEquals(dynamicKeys.size(), 3);

        // The first one should be the latest key
        KeyInfo key = SigningKeys.getInstance(process.getProcess()).getLatestIssuedDynamicKey();
        assertEquals(dynamicKeys.get(0).createdAtTime, key.createdAtTime);
        assertEquals(dynamicKeys.get(0).value, key.value);

        // The oldest one should be the legacy key.
        assertEquals(dynamicKeys.get(2).createdAtTime, legacyKey.createdAtTime);
        assertEquals(dynamicKeys.get(2).value, legacyKey.value);

        // Keys should be ordered by createdAtTime descending
        for (int i = 0; i < dynamicKeys.size() - 1; ++i) {
            assertTrue(dynamicKeys.get(i).createdAtTime > dynamicKeys.get(i + 1).createdAtTime);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void getAllKeysFiltersOldKeys()
            throws IOException, InterruptedException, InvalidKeyException, NoSuchAlgorithmException,
            StorageQueryException, StorageTransactionLogicException, InvalidKeySpecException, SignatureException,
            TenantOrAppNotFoundException, UnsupportedJWTSigningAlgorithmException {
        Utils.setValueInConfig("access_token_dynamic_signing_key_update_interval", "0.00027"); // 1 seconds
        Utils.setValueInConfig("access_token_validity", "1");

        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.start(args);

        EventAndException e = process.checkOrWaitForEvent(PROCESS_STATE.STARTED);
        assertNotNull(e);

        SigningKeys signingKeysInstace = SigningKeys.getInstance(process.getProcess());

        List<KeyInfo> oldKeys = signingKeysInstace.getDynamicKeys();
        assertEquals(oldKeys.size(), 1);

        // Wait for access_token_dynamic_signing_key_update_interval + 2 * access_token_validity + margin
        Thread.sleep(3500);

        List<KeyInfo> newKeys = signingKeysInstace.getDynamicKeys();
        assertEquals(newKeys.size(), 1);

        assertNotEquals(newKeys.get(0).value, oldKeys.get(0).value);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void migratingStaticSigningKeys() throws Exception {
        Utils.setValueInConfig("access_token_signing_key_dynamic", "false");

        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.start(args);

        EventAndException e = process.checkOrWaitForEvent(PROCESS_STATE.STARTED);
        assertNotNull(e);

        io.supertokens.utils.Utils.PubPriKey rsaKeys = io.supertokens.utils.Utils.generateNewPubPriKey();
        String signingKey = rsaKeys.toString();
        KeyValueInfo legacyKey = new KeyValueInfo(signingKey, System.currentTimeMillis() - 2629743830l); // 1 month old

        SessionStorage sessionStorage = (SessionStorage) StorageLayer.getStorage(process.getProcess());
        int expectedSize = 2;
        if (sessionStorage.getType() == STORAGE_TYPE.NOSQL_1) {
            sessionStorage.deleteAllInformation();
            expectedSize = 1;
        }
        sessionStorage.setKeyValue(new TenantIdentifier(null, null, null), "access_token_signing_key", legacyKey);

        AccessTokenSigningKey accessTokenSigningKeyInstance = AccessTokenSigningKey.getInstance(process.getProcess());
        accessTokenSigningKeyInstance.transferLegacyKeyToNewTable();

        accessTokenSigningKeyInstance.cleanExpiredAccessTokenSigningKeys();
        List<JWTSigningKeyInfo> keys = SigningKeys.getInstance(process.getProcess()).getStaticKeys();

        assertEquals(keys.size(), expectedSize);

        assertEquals(legacyKey.value, keys.get(expectedSize - 1).keyString);
        assertEquals(keys.get(expectedSize - 1).keyId.substring(0, 2), "s-");
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }
}
