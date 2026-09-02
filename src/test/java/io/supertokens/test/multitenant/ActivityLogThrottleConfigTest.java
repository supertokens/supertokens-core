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

import static org.junit.Assert.*;

/**
 * Tests for activity_log_throttle_enabled: a connection-URI-domain-level, protected core config that toggles
 * the per-(app, user) throttle on the semantic activity events feeding the last-active fold. The throttle's
 * runtime effect is bypassed under Main.isTesting (as it always has been), so these tests pin the config
 * surface — default, inheritance, the no-conflict-within-a-CUD rule, and protection — rather than the
 * collapse behavior itself.
 */
public class ActivityLogThrottleConfigTest {
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

    // The default is true (throttle on, preserving the pre-config behavior), and a self-hosted operator can
    // turn it off in the base config; every app and tenant under the connection URI domain resolves that same
    // value.
    @Test
    public void throttleDefaultsTrueAndCudOverrideInheritsToAllAppsAndTenants() throws Exception {
        String[] args = {"../"};

        Utils.setValueInConfig("activity_log_throttle_enabled", "false");
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        assertFalse(Config.getConfig(new TenantIdentifier(null, null, null), process.getProcess())
                .getActivityLogThrottleEnabled());

        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantIdentifier(null, null, null),
                tenant(new TenantIdentifier(null, "a1", null), new JsonObject()));
        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantIdentifier(null, "a1", null),
                tenant(new TenantIdentifier(null, "a1", "t1"), new JsonObject()));

        // Both the app and the tenant under it inherit the CUD-level value.
        assertFalse(Config.getConfig(new TenantIdentifier(null, "a1", null), process.getProcess())
                .getActivityLogThrottleEnabled());
        assertFalse(Config.getConfig(new TenantIdentifier(null, "a1", "t1"), process.getProcess())
                .getActivityLogThrottleEnabled());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // When nothing is configured the default holds.
    @Test
    public void throttleDefaultsTrueWhenUnset() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        assertTrue(Config.getConfig(new TenantIdentifier(null, null, null), process.getProcess())
                .getActivityLogThrottleEnabled());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // A differing override below the CUD level fails validation (the connection-URI-domain no-conflict rule),
    // exercised through the incremental single-tenant fast path used by addNewOrUpdateAppOrTenant.
    @Test
    public void conflictingThrottleBelowCudFailsValidation() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject conflicting = new JsonObject();
        conflicting.add("activity_log_throttle_enabled", new JsonPrimitive(false)); // base default is true

        try {
            Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantIdentifier(null, null, null),
                    tenant(new TenantIdentifier(null, "a1", null), conflicting));
            fail();
        } catch (InvalidConfigException e) {
            assertEquals("You cannot set different values for activity_log_throttle_enabled for the same " +
                    "connectionUriDomain", e.getMessage());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // With a saas secret configured, a non-secret request that tries to change the protected throttle config
    // is rejected with a bad-permission error.
    @Test
    public void nonSaaSSecretRequestCannotChangeThrottle() throws Exception {
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

        JsonObject withThrottle = new JsonObject();
        withThrottle.add("activity_log_throttle_enabled", new JsonPrimitive(false));

        try {
            // shouldPreventProtectedConfigUpdate = true simulates a non-saas-secret caller
            Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(),
                    tenant(new TenantIdentifier(null, null, "t1"), withThrottle), true);
            fail();
        } catch (BadPermissionException e) {
            assertEquals("Not allowed to modify protected configs.", e.getMessage());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
