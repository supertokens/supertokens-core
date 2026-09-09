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

package io.supertokens.test.multitenant;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.supertokens.ProcessState;
import io.supertokens.config.Config;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.multitenancy.*;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import org.junit.*;
import org.junit.rules.TestRule;

import java.util.ArrayList;

import static org.junit.Assert.*;

/**
 * Tests for activity_log_retention_days: a connection-URI-domain-level, protected core config.
 */
public class ActivityLogRetentionConfigTest {
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

    private static TenantConfig tenant(TenantIdentifier id, JsonObject coreConfig) {
        return new TenantConfig(id, new EmailPasswordConfig(true),
                new ThirdPartyConfig(true, new ThirdPartyConfig.Provider[0]),
                new PasswordlessConfig(true), null, null, coreConfig);
    }

    // The default is 31 and a self-hosted operator can set it freely in the base config; every app and
    // tenant under the connection URI domain resolves that same value.
    @Test
    public void retentionSetAtCudInheritsToAllAppsAndTenants() throws Exception {
        String[] args = {"../"};

        Utils.setValueInConfig("activity_log_retention_days", "7");
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        assertEquals(7, Config.getConfig(new TenantIdentifier(null, null, null), process.getProcess())
                .getActivityLogRetentionDays());

        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantIdentifier(null, null, null),
                tenant(new TenantIdentifier(null, "a1", null), new JsonObject()));
        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantIdentifier(null, "a1", null),
                tenant(new TenantIdentifier(null, "a1", "t1"), new JsonObject()));

        // Both the app and the tenant under it inherit the CUD-level value.
        assertEquals(7, Config.getConfig(new TenantIdentifier(null, "a1", null), process.getProcess())
                .getActivityLogRetentionDays());
        assertEquals(7, Config.getConfig(new TenantIdentifier(null, "a1", "t1"), process.getProcess())
                .getActivityLogRetentionDays());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // A differing override below the CUD level fails validation. This exercises the all-tenants path
    // (assertAllTenantConfigsAreValid). The conflicting app (a1) shares the base CUD (null) but a
    // different app id, so it is only the CUD-level check (not the per-app check) that rejects it.
    @Test
    public void conflictingRetentionBelowCudFailsValidation_allTenantsPath() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject conflicting = new JsonObject();
        conflicting.add("activity_log_retention_days", new JsonPrimitive(7)); // base default is 31

        try {
            Config.loadAllTenantConfig(process.getProcess(), new TenantConfig[]{
                    tenant(new TenantIdentifier(null, "a1", null), conflicting)}, new ArrayList<>());
            fail();
        } catch (InvalidConfigException e) {
            assertEquals("You cannot set different values for activity_log_retention_days for the same " +
                    "connectionUriDomain", e.getMessage());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // Same rule, exercised through the incremental single-tenant fast path (assertSingleTenantConfigIsValid),
    // which is what addNewOrUpdateAppOrTenant uses.
    @Test
    public void conflictingRetentionBelowCudFailsValidation_singleTenantPath() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject conflicting = new JsonObject();
        conflicting.add("activity_log_retention_days", new JsonPrimitive(7)); // base default is 31

        try {
            Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantIdentifier(null, null, null),
                    tenant(new TenantIdentifier(null, "a1", null), conflicting));
            fail();
        } catch (InvalidConfigException e) {
            assertEquals("You cannot set different values for activity_log_retention_days for the same " +
                    "connectionUriDomain", e.getMessage());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // With a saas secret configured, a non-secret request that tries to change the protected retention
    // config is rejected with a bad-permission error.
    @Test
    public void nonSaaSSecretRequestCannotChangeRetention() throws Exception {
        String[] args = {"../"};

        String saasSecret = "hg40239oirjgBHD9450=Beew123--hg40239oirjgBHD9450=Beew123--hg40239oirjgBHD9450=Beew123-";
        Utils.setValueInConfig("supertokens_saas_secret", saasSecret);
        Utils.setValueInConfig("api_keys", "hg40239oirjgBHD9450=Beew123--hg40239oiBeew123-");
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject withRetention = new JsonObject();
        withRetention.add("activity_log_retention_days", new JsonPrimitive(7));

        try {
            // shouldPreventProtectedConfigUpdate = true simulates a non-saas-secret caller
            Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(),
                    tenant(new TenantIdentifier(null, null, "t1"), withRetention), true);
            fail();
        } catch (BadPermissionException e) {
            assertEquals("Not allowed to modify protected configs.", e.getMessage());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // Values below the floor (>= 1, comfortably above the 10-minute rollup interval) are rejected.
    @Test
    public void retentionBelowOneFailsValidation() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject invalid = new JsonObject();
        invalid.add("activity_log_retention_days", new JsonPrimitive(0));

        try {
            Config.loadAllTenantConfig(process.getProcess(), new TenantConfig[]{
                    tenant(new TenantIdentifier(null, null, "t1"), invalid)}, new ArrayList<>());
            fail();
        } catch (InvalidConfigException e) {
            assertTrue(e.getMessage().contains("activity_log_retention_days must be >= 1"));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // Two connection URI domains on separate user pools can carry different retention values; each
    // resolves independently, which is what the cleanup cron reads per storage to drop partitions at
    // different horizons. Requires a real DB (separate user pools), so it is skipped for the in-memory DB.
    @Test
    public void twoCudsCanHaveDifferentRetention() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }
        if (StorageLayer.isInMemDb(process.getProcess())) {
            return;
        }

        JsonObject c1Config = new JsonObject();
        StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                .modifyConfigToAddANewUserPoolForTesting(c1Config, 1);
        c1Config.add("activity_log_retention_days", new JsonPrimitive(7));

        JsonObject c2Config = new JsonObject();
        StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                .modifyConfigToAddANewUserPoolForTesting(c2Config, 2);
        c2Config.add("activity_log_retention_days", new JsonPrimitive(15));

        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantIdentifier(null, null, null),
                tenant(new TenantIdentifier("c1", null, null), c1Config));
        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantIdentifier(null, null, null),
                tenant(new TenantIdentifier("c2", null, null), c2Config));

        assertEquals(7, Config.getConfig(new TenantIdentifier("c1", null, null), process.getProcess())
                .getActivityLogRetentionDays());
        assertEquals(15, Config.getConfig(new TenantIdentifier("c2", null, null), process.getProcess())
                .getActivityLogRetentionDays());
        // the base CUD keeps the default
        assertEquals(31, Config.getConfig(new TenantIdentifier(null, null, null), process.getProcess())
                .getActivityLogRetentionDays());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
