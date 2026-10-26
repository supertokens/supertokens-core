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

import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.multitenancy.MultitenancyHelper;
import io.supertokens.pluginInterface.KeyValueInfo;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.jwt.sqlstorage.JWTRecipeSQLStorage;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.EmailPasswordConfig;
import io.supertokens.pluginInterface.multitenancy.PasswordlessConfig;
import io.supertokens.pluginInterface.multitenancy.TenantConfig;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.ThirdPartyConfig;
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

    @Test
    public void noSigningKeyIsCreatedForANewAppWhileItsStorageIsStillInitialising() throws Exception {
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(new String[]{"../"});
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        Main main = process.getProcess();
        FeatureFlagTestContent.getInstance(main)
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        // the new app shares the base storage, so marking that storage covers it
        Storage storage = StorageLayer.getStorage(main);
        AppIdentifier app = new AppIdentifier(null, "deferredkeys" + System.nanoTime());

        StorageLayer.setStorageStillInitialisingForTesting(storage, true);
        try {
            // creating the app loads its per-app resources, as startup or a tenant refresh does
            Multitenancy.addNewOrUpdateAppOrTenant(main, new TenantIdentifier(null, null, null),
                    new TenantConfig(app.getAsPublicTenantIdentifier(), new EmailPasswordConfig(true),
                            new ThirdPartyConfig(true, null), new PasswordlessConfig(true), null, null,
                            new JsonObject()));
            // no key of any kind was created, so the storage was not queried for one
            assertEquals(0, countAccessTokenSigningKeys((SessionSQLStorage) storage, app));
            assertEquals(0, countJWTSigningKeys((JWTRecipeSQLStorage) storage, app));
        } finally {
            StorageLayer.setStorageStillInitialisingForTesting(storage, false);
        }

        // first use, once the storage is ready, creates both
        assertEquals(2, SigningKeys.getInstance(app, main).getAllKeys().size());
        assertEquals(1, countAccessTokenSigningKeys((SessionSQLStorage) storage, app));
        assertEquals(1, countJWTSigningKeys((JWTRecipeSQLStorage) storage, app));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    private static int countJWTSigningKeys(JWTRecipeSQLStorage storage, AppIdentifier app) throws Exception {
        return storage.startTransaction(con -> storage.getJWTSigningKeys_Transaction(app, con).size());
    }

    private static int countAccessTokenSigningKeys(SessionSQLStorage storage, AppIdentifier app) throws Exception {
        return storage.startTransaction(con -> storage.getAccessTokenSigningKeys_Transaction(app, con).length);
    }
}
