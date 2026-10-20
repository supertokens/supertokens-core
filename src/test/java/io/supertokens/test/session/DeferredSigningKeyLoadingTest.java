/*
 *    Copyright (c) 2026, VRAI Labs and/or its affiliates. All rights reserved.
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

import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.multitenancy.MultitenancyHelper;
import io.supertokens.pluginInterface.KeyValueInfo;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.session.sqlStorage.SessionSQLStorage;
import io.supertokens.signingkeys.SigningKeys;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * When startup (or a tenant refresh) continues past a storage that is still initialising, the per-app signing-key
 * resources must not query it while they are built, or the boot thread waits for that storage after all. They load
 * on first use instead, and a legacy access token signing key is still moved into the new table before any key is
 * read, so no new key is generated in its place.
 */
public class DeferredSigningKeyLoadingTest {

    private static final String LEGACY_KEY_NAME = "access_token_signing_key";

    @Rule
    public TestRule watchman = Utils.getOnFailure();

    @Rule
    public TestRule retryFlaky = Utils.retryFlakyTest();

    @AfterClass
    public static void afterTesting() {
        Utils.afterTesting();
    }

    @Before
    public void beforeEach() {
        Utils.reset();
    }

    @Test
    public void signingKeysAreNotPrefetchedWhileTheStorageIsStillInitialisingAndLoadOnFirstUse() throws Exception {
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(new String[]{"../"});
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        Main main = process.getProcess();
        AppIdentifier app = process.getAppForTesting().toAppIdentifier();
        Storage storage = StorageLayer.getStorage(app.getAsPublicTenantIdentifier(), main);
        SessionSQLStorage sessionStorage = (SessionSQLStorage) storage;

        sessionStorage.removeAccessTokenSigningKeysBefore(app, System.currentTimeMillis() + 1000);
        assertEquals(0, countAccessTokenSigningKeys(sessionStorage, app));

        StorageLayer.setStorageStillInitialisingForTesting(storage, true);
        try {
            // what startup does right after the storages are loaded, with this app's storage still initialising
            MultitenancyHelper.getInstance(main).loadSigningKeys(List.of(app.getAsPublicTenantIdentifier()));
            assertEquals("the storage was queried while it was still initialising", 0,
                    countAccessTokenSigningKeys(sessionStorage, app));
        } finally {
            StorageLayer.setStorageStillInitialisingForTesting(storage, false);
        }

        // first use, once the storage is ready
        assertEquals(1, SigningKeys.getInstance(app, main).getDynamicKeys().size());
        assertEquals(1, countAccessTokenSigningKeys(sessionStorage, app));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void aLegacyKeyIsStillMovedBeforeFirstUseWhenThePrefetchWasSkipped() throws Exception {
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(new String[]{"../"});
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        Main main = process.getProcess();
        AppIdentifier app = process.getAppForTesting().toAppIdentifier();
        Storage storage = StorageLayer.getStorage(app.getAsPublicTenantIdentifier(), main);
        SessionSQLStorage sessionStorage = (SessionSQLStorage) storage;

        sessionStorage.removeAccessTokenSigningKeysBefore(app, System.currentTimeMillis() + 1000);
        KeyValueInfo legacyKey = new KeyValueInfo(io.supertokens.utils.Utils.generateNewPubPriKey().toString(),
                System.currentTimeMillis());
        sessionStorage.setKeyValue(app.getAsPublicTenantIdentifier(), LEGACY_KEY_NAME, legacyKey);

        StorageLayer.setStorageStillInitialisingForTesting(storage, true);
        try {
            MultitenancyHelper.getInstance(main).loadSigningKeys(List.of(app.getAsPublicTenantIdentifier()));
            assertNotNull("the legacy key was moved while the storage was still initialising",
                    sessionStorage.getKeyValue(app.getAsPublicTenantIdentifier(), LEGACY_KEY_NAME));
        } finally {
            StorageLayer.setStorageStillInitialisingForTesting(storage, false);
        }

        // first use: the legacy key is moved and used, not replaced by a newly generated key, so access tokens
        // signed with it keep verifying
        List<SigningKeys.KeyInfo> keys = SigningKeys.getInstance(app, main).getDynamicKeys();
        assertEquals(1, keys.size());
        assertEquals(legacyKey.value, keys.get(0).value);
        assertEquals(legacyKey.createdAtTime, keys.get(0).createdAtTime);
        assertNull(sessionStorage.getKeyValue(app.getAsPublicTenantIdentifier(), LEGACY_KEY_NAME));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    private static int countAccessTokenSigningKeys(SessionSQLStorage storage, AppIdentifier app) throws Exception {
        return storage.startTransaction(con -> storage.getAccessTokenSigningKeys_Transaction(app, con).length);
    }
}
