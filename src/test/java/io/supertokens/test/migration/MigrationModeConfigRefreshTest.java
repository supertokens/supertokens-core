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

package io.supertokens.test.migration;

import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.pluginInterface.MigrationMode;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.migration.MigrationBackfillStorage;
import io.supertokens.pluginInterface.multitenancy.EmailPasswordConfig;
import io.supertokens.pluginInterface.multitenancy.PasswordlessConfig;
import io.supertokens.pluginInterface.multitenancy.TenantConfig;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.ThirdPartyConfig;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Regression test for the migration_mode tenant-config update never reaching the live storage
 * instance.
 *
 * <p>When a tenant's {@code coreConfig.migration_mode} is changed through the multitenancy CRUD
 * path, {@code MultitenancyHelper.refreshAfterKnownTenantChange} rebuilds the storage layer via
 * {@link StorageLayer#loadAllTenantStorage}. That method reuses an existing storage instance when
 * the {@code userPoolId + connectionPoolId} is unchanged. {@code migration_mode} is
 * {@code @IgnoreForAnnotationCheck} in the plugin config (neither a user-pool nor a
 * connection-pool property), so a mode change never alters the pool identity — and before the fix
 * the reused instance kept serving its stale config until the next core restart.
 *
 * <p>The fix refreshes the reused instance's config in place (loadConfig), so the new mode goes
 * live immediately. This test asserts exactly that: no restart between the write and the read.
 *
 * <p>Only meaningful on a storage plugin that reads {@code migration_mode} from per-tenant config
 * (PostgreSQL). The in-memory SQLite storage resolves the mode from a static test-only field, so it
 * cannot exercise this path and the test is skipped there (CI's postgresql matrix covers it).
 */
public class MigrationModeConfigRefreshTest {

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

    // A dedicated app pointed at its own database, so it gets its own storage instance (a distinct
    // userPoolId), isolated from the base tenant's storage. The database is held constant across the
    // mode update below, so migration_mode is the only thing that changes — and since it is not a
    // pool property, the pool identity is unchanged and the existing storage instance is reused. That
    // is precisely the storage-reuse path that regressed.
    private static final TenantIdentifier APP = new TenantIdentifier(null, "a1", null);
    private static final String APP_DB = "st1";

    private TenantConfig appConfig(MigrationMode mode) {
        JsonObject coreConfig = new JsonObject();
        coreConfig.addProperty("postgresql_database_name", APP_DB);
        coreConfig.addProperty("migration_mode", mode.name());
        return new TenantConfig(APP,
                new EmailPasswordConfig(true),
                new ThirdPartyConfig(true, null),
                new PasswordlessConfig(true),
                null, null, coreConfig);
    }

    private MigrationMode liveMode(TestingProcessManager.TestingProcess process) throws Exception {
        Storage storage = StorageLayer.getStorage(APP, process.getProcess());
        return ((MigrationBackfillStorage) storage).getMigrationMode();
    }

    @Test
    public void migrationModeUpdateAppliesToLiveStorageWithoutRestart() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        Storage baseStorage = StorageLayer.getStorage(process.getProcess());
        Assume.assumeTrue(baseStorage.getType() == STORAGE_TYPE.SQL);
        // Skip on in-memory SQLite: it resolves migration_mode from a static field, not per-tenant
        // config, so it cannot exercise the storage-config-reuse path this test guards.
        Assume.assumeFalse(StorageLayer.isInMemDb(process.getProcess()));

        // Create the app already carrying migration_mode=LEGACY.
        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), appConfig(MigrationMode.LEGACY), false);
        assertEquals(MigrationMode.LEGACY, liveMode(process));

        // Update ONLY the migration_mode (pool size unchanged → same pool identity → storage reused).
        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(),
                appConfig(MigrationMode.DUAL_WRITE_READ_OLD), false);

        // The regression: before the fix this still returned LEGACY (stale reused-instance config)
        // and only a core restart activated the persisted value.
        assertEquals(MigrationMode.DUAL_WRITE_READ_OLD, liveMode(process));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
