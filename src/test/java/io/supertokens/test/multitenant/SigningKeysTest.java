/*
 *    Copyright (c) 2023, VRAI Labs and/or its affiliates. All rights reserved.
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
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.jwt.exceptions.UnsupportedJWTSigningAlgorithmException;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.multitenancy.exception.CannotModifyBaseConfigException;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.exceptions.DbInitException;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.*;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.signingkeys.AccessTokenSigningKey;
import io.supertokens.signingkeys.SigningKeys;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.thirdparty.InvalidProviderConfigException;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class SigningKeysTest {
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
    public void normalConfigContinuesToWork()
            throws InterruptedException, IOException, StorageQueryException, StorageTransactionLogicException,
            TenantOrAppNotFoundException, UnsupportedJWTSigningAlgorithmException {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.LOADING_ALL_TENANT_CONFIG));

        assertEquals(
                SigningKeys.getInstance(new AppIdentifier(null, null), process.main).getAllKeys()
                        .size(), 2);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void keysAreGeneratedForAllUserPoolIds()
            throws InterruptedException, IOException, StorageQueryException, StorageTransactionLogicException,
            InvalidConfigException, DbInitException, TenantOrAppNotFoundException, InvalidProviderConfigException,
            FeatureNotEnabledException, CannotModifyBaseConfigException,
            BadPermissionException, UnsupportedJWTSigningAlgorithmException {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        if (StorageLayer.isInMemDb(process.getProcess())) {
            return;
        }

        JsonObject tenantConfig = new JsonObject();
        StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                .modifyConfigToAddANewUserPoolForTesting(tenantConfig, 2);
        tenantConfig.add("access_token_signing_key_update_interval", new JsonPrimitive(200));

        TenantConfig[] tenants = new TenantConfig[]{
                new TenantConfig(new TenantIdentifier("c1", null, null), new EmailPasswordConfig(false),
                        new ThirdPartyConfig(false, new ThirdPartyConfig.Provider[0]),
                        new PasswordlessConfig(false),
                        tenantConfig)};

        for (TenantConfig config : tenants) {
            Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantIdentifier(null, null, null),
                    config);
        }

        List<AppIdentifier> apps = new ArrayList<>();
        for (TenantConfig t : tenants) {
            apps.add(t.tenantIdentifier.toAppIdentifier());
        }
        apps.add(new AppIdentifier(null, null)); // Add base app
        AccessTokenSigningKey.loadForAllTenants(process.getProcess(), apps, new ArrayList<>());

        assertEquals(
                SigningKeys.getInstance(new AppIdentifier(null, null), process.main).getDynamicKeys()
                        .size(), 1);
        assertEquals(
                SigningKeys.getInstance(new AppIdentifier("c1", null), process.main).getDynamicKeys()
                        .size(), 1);
        SigningKeys.KeyInfo baseTenant = SigningKeys.getInstance(
                        new AppIdentifier(null, null), process.main)
                .getDynamicKeys().get(0);
        SigningKeys.KeyInfo c1Tenant = SigningKeys.getInstance(
                        new AppIdentifier("c1", null), process.main)
                .getDynamicKeys().get(0);

        assertNotEquals(baseTenant.createdAtTime, c1Tenant.createdAtTime);
        assertNotEquals(baseTenant.expiryTime, c1Tenant.expiryTime);
        assertTrue(baseTenant.expiryTime + (31 * 3600 * 1000) < c1Tenant.expiryTime);
        assertNotEquals(baseTenant.value, c1Tenant.value);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void signingKeyClassesAreThereForAllTenants()
            throws InterruptedException, IOException, InvalidConfigException, DbInitException, StorageQueryException,
            StorageTransactionLogicException, TenantOrAppNotFoundException, InvalidProviderConfigException,
            FeatureNotEnabledException, CannotModifyBaseConfigException,
            BadPermissionException, UnsupportedJWTSigningAlgorithmException {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        if (StorageLayer.isInMemDb(process.getProcess())) {
            return;
        }

        JsonObject tenantConfig = new JsonObject();
        StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                .modifyConfigToAddANewUserPoolForTesting(tenantConfig, 2);
        tenantConfig.add("access_token_signing_key_update_interval", new JsonPrimitive(200));
        JsonObject tenantConfig2 = new JsonObject();
        StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                .modifyConfigToAddANewUserPoolForTesting(tenantConfig2, 3);
        tenantConfig2.add("access_token_signing_key_update_interval", new JsonPrimitive(400));

        TenantConfig[] tenants = new TenantConfig[]{
                new TenantConfig(new TenantIdentifier("c1", null, null), new EmailPasswordConfig(false),
                        new ThirdPartyConfig(false, new ThirdPartyConfig.Provider[0]),
                        new PasswordlessConfig(false),
                        tenantConfig),
                new TenantConfig(new TenantIdentifier("c2", null, null), new EmailPasswordConfig(false),
                        new ThirdPartyConfig(false, new ThirdPartyConfig.Provider[0]),
                        new PasswordlessConfig(false),
                        tenantConfig2)};

        for (TenantConfig config : tenants) {
            Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantIdentifier(null, null, null),
                    config);
        }

        List<AppIdentifier> apps = new ArrayList<>();
        for (TenantConfig t : tenants) {
            apps.add(t.tenantIdentifier.toAppIdentifier());
        }
        apps.add(new AppIdentifier(null, null)); // Add base app
        AccessTokenSigningKey.loadForAllTenants(process.getProcess(), apps, new ArrayList<>());

        assertEquals(
                SigningKeys.getInstance(new AppIdentifier(null, null), process.main).getDynamicKeys()
                        .size(), 1);
        assertEquals(
                SigningKeys.getInstance(new AppIdentifier("c1", null), process.main).getDynamicKeys()
                        .size(), 1);
        SigningKeys.KeyInfo baseTenant = SigningKeys.getInstance(
                        new AppIdentifier(null, null), process.main)
                .getDynamicKeys().get(0);
        SigningKeys.KeyInfo c1Tenant = SigningKeys.getInstance(
                        new AppIdentifier("c1", null), process.main)
                .getDynamicKeys().get(0);
        SigningKeys.KeyInfo c2Tenant = SigningKeys.getInstance(
                        new AppIdentifier("c2", null), process.main)
                .getDynamicKeys().get(0);
        SigningKeys.KeyInfo c3Tenant = SigningKeys.getInstance(
                        new AppIdentifier("c3", null), process.main)
                .getDynamicKeys().get(0);

        assertNotEquals(baseTenant.createdAtTime, c1Tenant.createdAtTime);
        assertNotEquals(baseTenant.expiryTime, c1Tenant.expiryTime);
        assertNotEquals(baseTenant.expiryTime, c2Tenant.expiryTime);
        assertTrue(baseTenant.expiryTime + (31 * 3600 * 1000) < c1Tenant.expiryTime);
        assertNotEquals(baseTenant.value, c1Tenant.value);
        assertTrue(baseTenant.expiryTime + (60 * 3600 * 1000) < c2Tenant.expiryTime);
        assertNotEquals(baseTenant.value, c2Tenant.value);

        assertEquals(baseTenant.expiryTime, c3Tenant.expiryTime);
        assertEquals(baseTenant.value, c3Tenant.value);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
