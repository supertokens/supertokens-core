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
import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.cliOptions.CLIOptions;
import io.supertokens.config.Config;
import io.supertokens.config.CoreConfigTestContent;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.multitenancy.exception.CannotModifyBaseConfigException;
import io.supertokens.multitenancy.exception.DeletionInProgressException;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.*;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.thirdparty.InvalidProviderConfigException;
import io.supertokens.version.Version;
import org.junit.*;
import org.junit.rules.TestRule;

import java.io.File;
import java.io.IOException;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.*;

public class ConfigTest {
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
    public void normalConfigContinuesToWork() throws InterruptedException, IOException {
        String[] args = {"../"};

        Utils.setValueInConfig("refresh_token_validity", "144001");
        Utils.setValueInConfig("access_token_signing_key_dynamic", "false");
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.LOADING_ALL_TENANT_CONFIG));

        Assert.assertEquals(Config.getConfig(process.getProcess()).getRefreshTokenValidity(),
                (long) 144001 * 60 * 1000);
        Assert.assertEquals(Config.getConfig(process.getProcess()).getAccessTokenSigningKeyDynamic(),
                false);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    private String getConfigFileLocation(Main main) {
        return new File(CLIOptions.get(main).getConfigFilePath() == null
                ? CLIOptions.get(main).getInstallationPath() + "config.yaml"
                : CLIOptions.get(main).getConfigFilePath()).getAbsolutePath();
    }

    @Test
    public void normalConfigErrorContinuesToWork() throws InterruptedException, IOException {
        String[] args = {"../"};

        Utils.setValueInConfig("access_token_validity", "-1");
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();

        ProcessState.EventAndException e = process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.INIT_FAILURE);
        assertNotNull(e);
        assertEquals(e.exception.getCause().getMessage(),
                "'access_token_validity' must be between 1 and 86400000 seconds inclusive. The config file can be "
                        + "found here: " + getConfigFileLocation(process.getProcess()));

        assertNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.LOADING_ALL_TENANT_CONFIG, 1000));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void mergingTenantWithBaseConfigWorks()
            throws InterruptedException, IOException, InvalidConfigException, TenantOrAppNotFoundException {
        String[] args = {"../"};

        Utils.setValueInConfig("refresh_token_validity", "144001");
        Utils.setValueInConfig("access_token_signing_key_dynamic", "false");
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        JsonObject tenantConfig = new JsonObject();
        tenantConfig.add("refresh_token_validity", new JsonPrimitive(144002));
        tenantConfig.add("password_reset_token_lifetime", new JsonPrimitive(3600001));
        StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                .modifyConfigToAddANewUserPoolForTesting(tenantConfig, 2);

        Config.loadAllTenantConfig(process.getProcess(), new TenantConfig[]{
                new TenantConfig(new TenantIdentifier("abc", null, null), new EmailPasswordConfig(false),
                        new ThirdPartyConfig(false, new ThirdPartyConfig.Provider[0]),
                        new PasswordlessConfig(false),
                        tenantConfig)});

        Assert.assertEquals(Config.getConfig(process.getProcess()).getRefreshTokenValidity(),
                (long) 144001 * 60 * 1000);
        Assert.assertEquals(Config.getConfig(process.getProcess()).getPasswordResetTokenLifetime(),
                3600000);
        Assert.assertEquals(Config.getConfig(process.getProcess()).getPasswordlessMaxCodeInputAttempts(),
                5);
        Assert.assertEquals(Config.getConfig(process.getProcess()).getAccessTokenSigningKeyDynamic(),
                false);

        Assert.assertEquals(Config.getConfig(new TenantIdentifier("abc", null, null), process.getProcess())
                        .getRefreshTokenValidity(),
                (long) 144002 * 60 * 1000);
        Assert.assertEquals(Config.getConfig(new TenantIdentifier("abc", null, null), process.getProcess())
                        .getPasswordResetTokenLifetime(),
                3600001);
        Assert.assertEquals(Config.getConfig(new TenantIdentifier("abc", null, null), process.getProcess())
                        .getPasswordlessMaxCodeInputAttempts(),
                5);
        Assert.assertEquals(Config.getConfig(new TenantIdentifier("abc", null, null), process.getProcess())
                        .getAccessTokenSigningKeyDynamic(),
                false);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void mergingTenantWithBaseConfigWithInvalidConfigThrowsErrorWorks()
            throws InterruptedException, IOException {
        String[] args = {"../"};

        Utils.setValueInConfig("refresh_token_validity", "144001");
        Utils.setValueInConfig("access_token_signing_key_dynamic", "false");
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        CoreConfigTestContent.getInstance(process.main)
                .setKeyValue(CoreConfigTestContent.VALIDITY_TESTING, true);
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        JsonObject tenantConfig = new JsonObject();
        tenantConfig.add("refresh_token_validity", new JsonPrimitive(1));
        tenantConfig.add("password_reset_token_lifetime", new JsonPrimitive(3600001));

        try {
            Config.loadAllTenantConfig(process.getProcess(), new TenantConfig[]{
                    new TenantConfig(new TenantIdentifier("abc", null, null), new EmailPasswordConfig(false),
                            new ThirdPartyConfig(false, new ThirdPartyConfig.Provider[0]),
                            new PasswordlessConfig(false),
                            tenantConfig)});
            fail();
        } catch (InvalidConfigException e) {
            assert (e.getMessage()
                    .contains("'refresh_token_validity' must be strictly greater than 'access_token_validity'"));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void mergingTenantWithBaseConfigWithConflictingConfigsThrowsError()
            throws InterruptedException, IOException {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        CoreConfigTestContent.getInstance(process.main)
                .setKeyValue(CoreConfigTestContent.VALIDITY_TESTING, true);
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        JsonObject tenantConfig = new JsonObject();
        tenantConfig.add("access_token_signing_key_dynamic", new JsonPrimitive(false));

        try {
            Config.loadAllTenantConfig(process.getProcess(), new TenantConfig[]{
                    new TenantConfig(new TenantIdentifier(null, null, "abc"), new EmailPasswordConfig(false),
                            new ThirdPartyConfig(false, new ThirdPartyConfig.Provider[0]),
                            new PasswordlessConfig(false),
                            tenantConfig)});
            fail();
        } catch (InvalidConfigException e) {
            assert (e.getMessage()
                    .equals("You cannot set different values for access_token_signing_key_dynamic for the same appId"));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void mergingDifferentUserPoolTenantWithBaseConfigWithConflictingConfigsShouldNotThrowsError()
            throws InterruptedException, IOException, InvalidConfigException, TenantOrAppNotFoundException {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        CoreConfigTestContent.getInstance(process.main)
                .setKeyValue(CoreConfigTestContent.VALIDITY_TESTING, true);
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        Storage storage = StorageLayer.getStorage(process.getProcess());
        if (storage.getType() == STORAGE_TYPE.SQL
                && !Version.getVersion(process.getProcess()).getPluginName().equals("sqlite")) {
            JsonObject tenantConfig = new JsonObject();

            if (Version.getVersion(process.getProcess()).getPluginName().equals("postgresql")) {
                tenantConfig.add("postgresql_database_name", new JsonPrimitive("random"));
            } else if (Version.getVersion(process.getProcess()).getPluginName().equals("mysql")) {
                tenantConfig.add("mysql_database_name", new JsonPrimitive("random"));
            } else {
                tenantConfig.add("mongodb_connection_uri", new JsonPrimitive("mongodb://root:root@localhost:27018"));
            }
            tenantConfig.add("access_token_signing_key_dynamic", new JsonPrimitive(false));

            Config.loadAllTenantConfig(process.getProcess(), new TenantConfig[]{
                    new TenantConfig(new TenantIdentifier("abc", null, null), new EmailPasswordConfig(false),
                            new ThirdPartyConfig(false, new ThirdPartyConfig.Provider[0]),
                            new PasswordlessConfig(false),
                            tenantConfig)});

        }

        Assert.assertEquals(Config.getConfig(process.getProcess()).getAccessTokenSigningKeyDynamic(),
                true);

        Assert.assertEquals(Config.getConfig(new TenantIdentifier("abc", null, null), process.getProcess())
                        .getPasswordlessMaxCodeInputAttempts(),
                5);
        Assert.assertEquals(Config.getConfig(new TenantIdentifier("abc", null, null), process.getProcess())
                        .getAccessTokenSigningKeyDynamic(),
                false);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testDifferentWaysToGetConfigBasedOnConnectionURIAndTenantId()
            throws InterruptedException, IOException, InvalidConfigException, TenantOrAppNotFoundException {
        String[] args = {"../"};

        Utils.setValueInConfig("email_verification_token_lifetime", "144001");
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        TenantConfig[] tenants = new TenantConfig[4];

        {
            JsonObject tenantConfig = new JsonObject();

            tenantConfig.add("email_verification_token_lifetime", new JsonPrimitive(144002));
            StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                    .modifyConfigToAddANewUserPoolForTesting(tenantConfig, 2);
            tenants[0] = new TenantConfig(new TenantIdentifier("c1", null, null), new EmailPasswordConfig(false),
                    new ThirdPartyConfig(false, new ThirdPartyConfig.Provider[0]),
                    new PasswordlessConfig(false),
                    tenantConfig);
        }

        {
            JsonObject tenantConfig = new JsonObject();
            StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                    .modifyConfigToAddANewUserPoolForTesting(tenantConfig, 2);
            tenantConfig.add("email_verification_token_lifetime", new JsonPrimitive(144003));
            tenants[1] = new TenantConfig(new TenantIdentifier("c1", null, "t1"), new EmailPasswordConfig(false),
                    new ThirdPartyConfig(false, new ThirdPartyConfig.Provider[0]),
                    new PasswordlessConfig(false),
                    tenantConfig);
        }

        {
            JsonObject tenantConfig = new JsonObject();
            tenantConfig.add("email_verification_token_lifetime", new JsonPrimitive(144004));
            tenants[2] = new TenantConfig(new TenantIdentifier(null, null, "t2"), new EmailPasswordConfig(false),
                    new ThirdPartyConfig(false, new ThirdPartyConfig.Provider[0]),
                    new PasswordlessConfig(false),
                    tenantConfig);
        }

        {
            JsonObject tenantConfig = new JsonObject();
            tenantConfig.add("email_verification_token_lifetime", new JsonPrimitive(144005));
            tenants[3] = new TenantConfig(new TenantIdentifier(null, null, "t1"), new EmailPasswordConfig(false),
                    new ThirdPartyConfig(false, new ThirdPartyConfig.Provider[0]),
                    new PasswordlessConfig(false),
                    tenantConfig);
        }

        Config.loadAllTenantConfig(process.getProcess(), tenants);

        Assert.assertEquals(Config.getConfig(new TenantIdentifier(null, null, null), process.getProcess())
                        .getEmailVerificationTokenLifetime(),
                (long) 144001);
        Assert.assertEquals(Config.getConfig(new TenantIdentifier("c1", null, null), process.getProcess())
                        .getEmailVerificationTokenLifetime(),
                (long) 144002);
        Assert.assertEquals(Config.getConfig(new TenantIdentifier("c1", null, "t1"), process.getProcess())
                        .getEmailVerificationTokenLifetime(),
                (long) 144003);
        Assert.assertEquals(Config.getConfig(new TenantIdentifier(null, null, "t1"), process.getProcess())
                        .getEmailVerificationTokenLifetime(),
                (long) 144005);
        Assert.assertEquals(Config.getConfig(new TenantIdentifier("c2", null, null), process.getProcess())
                        .getEmailVerificationTokenLifetime(),
                (long) 144001);
        Assert.assertEquals(Config.getConfig(new TenantIdentifier("c2", null, "t1"), process.getProcess())
                        .getEmailVerificationTokenLifetime(),
                (long) 144005);
        Assert.assertEquals(Config.getConfig(new TenantIdentifier("c3", null, "t2"), process.getProcess())
                        .getEmailVerificationTokenLifetime(),
                (long) 144004);
        Assert.assertEquals(Config.getConfig(new TenantIdentifier(null, null, "t2"), process.getProcess())
                        .getEmailVerificationTokenLifetime(),
                (long) 144004);


        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testMappingSameUserPoolToDifferentConnectionURIThrowsError()
            throws InterruptedException, IOException, TenantOrAppNotFoundException {
        String[] args = {"../"};

        Utils.setValueInConfig("email_verification_token_lifetime", "144001");
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        TenantConfig[] tenants = new TenantConfig[2];

        {
            JsonObject tenantConfig = new JsonObject();

            tenantConfig.add("email_verification_token_lifetime", new JsonPrimitive(144002));
            StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                    .modifyConfigToAddANewUserPoolForTesting(tenantConfig, 2);
            tenants[0] = new TenantConfig(new TenantIdentifier("c1", null, null), new EmailPasswordConfig(false),
                    new ThirdPartyConfig(false, new ThirdPartyConfig.Provider[0]),
                    new PasswordlessConfig(false),
                    tenantConfig);
        }

        {
            JsonObject tenantConfig = new JsonObject();
            StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                    .modifyConfigToAddANewUserPoolForTesting(tenantConfig, 2);
            tenantConfig.add("email_verification_token_lifetime", new JsonPrimitive(144003));
            tenants[1] = new TenantConfig(new TenantIdentifier("c2", null, null), new EmailPasswordConfig(false),
                    new ThirdPartyConfig(false, new ThirdPartyConfig.Provider[0]),
                    new PasswordlessConfig(false),
                    tenantConfig);
        }

        try {
            Config.loadAllTenantConfig(process.getProcess(), tenants);
            fail();
        } catch (InvalidConfigException e) {
            assert (e.getMessage()
                    .equals("ConnectionUriDomain: c2 cannot be mapped to the same user pool as c1"));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testThatOnlyDefaultConnectionURIAppAndTenantIsAllowedToGetAllTenants()
            throws InterruptedException {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        try {
            Multitenancy.getAllTenants(new TenantIdentifier("c1", null, null),
                    process.getProcess());
            fail();
        } catch (BadPermissionException e) {
            assertEquals(
                    "Only the public tenantId, public appId and default connectionUriDomain is allowed to list all " +
                            "connectionUriDomains and appIds associated with this core",
                    e.getMessage());
        }

        try {
            Multitenancy.getAllTenants(new TenantIdentifier(null, "a1", null),
                    process.getProcess());
            fail();
        } catch (BadPermissionException e) {
            assertEquals(
                    "Only the public tenantId, public appId and default connectionUriDomain is allowed to list all " +
                            "connectionUriDomains and appIds associated with this core",
                    e.getMessage());
        }

        try {
            Multitenancy.getAllTenants(new TenantIdentifier(null, null, "t1"),
                    process.getProcess());
            fail();
        } catch (BadPermissionException e) {
            assertEquals(
                    "Only the public tenantId, public appId and default connectionUriDomain is allowed to list all " +
                            "connectionUriDomains and appIds associated with this core",
                    e.getMessage());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testCreationOfTenantsUsingValidSourceTenant()
            throws InterruptedException, BadPermissionException, InvalidProviderConfigException,
            DeletionInProgressException, StorageQueryException, FeatureNotEnabledException, IOException,
            InvalidConfigException, CannotModifyBaseConfigException, TenantOrAppNotFoundException {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        Multitenancy.addNewOrUpdateAppOrTenant(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                new TenantConfig(
                        new TenantIdentifier(null, null, "t1"),
                        new EmailPasswordConfig(true),
                        new ThirdPartyConfig(true, null),
                        new PasswordlessConfig(true),
                        new JsonObject()
                )
        );

        Multitenancy.addNewOrUpdateAppOrTenant(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                new TenantConfig(
                        new TenantIdentifier(null, "a1", null),
                        new EmailPasswordConfig(true),
                        new ThirdPartyConfig(true, null),
                        new PasswordlessConfig(true),
                        new JsonObject()
                )
        );

        Multitenancy.addNewOrUpdateAppOrTenant(
                process.getProcess(),
                new TenantIdentifier(null, "a1", null),
                new TenantConfig(
                        new TenantIdentifier(null, "a1", "t1"),
                        new EmailPasswordConfig(true),
                        new ThirdPartyConfig(true, null),
                        new PasswordlessConfig(true),
                        new JsonObject()
                )
        );

        Multitenancy.addNewOrUpdateAppOrTenant(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                new TenantConfig(
                        new TenantIdentifier(null, "a2", null),
                        new EmailPasswordConfig(true),
                        new ThirdPartyConfig(true, null),
                        new PasswordlessConfig(true),
                        new JsonObject()
                )
        );

        Multitenancy.addNewOrUpdateAppOrTenant(
                process.getProcess(),
                new TenantIdentifier(null, "a2", null),
                new TenantConfig(
                        new TenantIdentifier(null, "a2", "t1"),
                        new EmailPasswordConfig(true),
                        new ThirdPartyConfig(true, null),
                        new PasswordlessConfig(true),
                        new JsonObject()
                )
        );

        Multitenancy.addNewOrUpdateAppOrTenant(
                process.getProcess(),
                new TenantIdentifier(null, "a2", null),
                new TenantConfig(
                        new TenantIdentifier(null, "a2", "t2"),
                        new EmailPasswordConfig(true),
                        new ThirdPartyConfig(true, null),
                        new PasswordlessConfig(true),
                        new JsonObject()
                )
        );

        JsonObject config = new JsonObject();
        StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                .modifyConfigToAddANewUserPoolForTesting(config, 2);

        Multitenancy.addNewOrUpdateAppOrTenant(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                new TenantConfig(
                        new TenantIdentifier("c1", null, null),
                        new EmailPasswordConfig(true),
                        new ThirdPartyConfig(true, null),
                        new PasswordlessConfig(true),
                        config
                )
        );

        Multitenancy.addNewOrUpdateAppOrTenant(
                process.getProcess(),
                new TenantIdentifier("c1", null, null),
                new TenantConfig(
                        new TenantIdentifier("c1", null, "t1"),
                        new EmailPasswordConfig(true),
                        new ThirdPartyConfig(true, null),
                        new PasswordlessConfig(true),
                        config
                )
        );

        Multitenancy.addNewOrUpdateAppOrTenant(
                process.getProcess(),
                new TenantIdentifier("c1", null, null),
                new TenantConfig(
                        new TenantIdentifier("c1", "a1", null),
                        new EmailPasswordConfig(true),
                        new ThirdPartyConfig(true, null),
                        new PasswordlessConfig(true),
                        config
                )
        );

        Multitenancy.addNewOrUpdateAppOrTenant(
                process.getProcess(),
                new TenantIdentifier("c1", "a1", null),
                new TenantConfig(
                        new TenantIdentifier("c1", "a1", "t1"),
                        new EmailPasswordConfig(true),
                        new ThirdPartyConfig(true, null),
                        new PasswordlessConfig(true),
                        config
                )
        );

        Multitenancy.addNewOrUpdateAppOrTenant(
                process.getProcess(),
                new TenantIdentifier("c1", null, null),
                new TenantConfig(
                        new TenantIdentifier("c1", "a2", null),
                        new EmailPasswordConfig(true),
                        new ThirdPartyConfig(true, null),
                        new PasswordlessConfig(true),
                        config
                )
        );

        Multitenancy.addNewOrUpdateAppOrTenant(
                process.getProcess(),
                new TenantIdentifier("c1", "a2", null),
                new TenantConfig(
                        new TenantIdentifier("c1", "a2", "t1"),
                        new EmailPasswordConfig(true),
                        new ThirdPartyConfig(true, null),
                        new PasswordlessConfig(true),
                        config
                )
        );

        Multitenancy.addNewOrUpdateAppOrTenant(
                process.getProcess(),
                new TenantIdentifier("c1", "a2", null),
                new TenantConfig(
                        new TenantIdentifier("c1", "a2", "t2"),
                        new EmailPasswordConfig(true),
                        new ThirdPartyConfig(true, null),
                        new PasswordlessConfig(true),
                        config
                )
        );

        TenantConfig[] allTenants = Multitenancy.getAllTenants(new TenantIdentifier(null, null, null),
                process.getProcess());
        assertEquals(14, allTenants.length);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testInvalidCasesOfTenantCreation()
            throws InterruptedException, BadPermissionException, InvalidProviderConfigException,
            DeletionInProgressException, StorageQueryException, FeatureNotEnabledException, IOException,
            InvalidConfigException, CannotModifyBaseConfigException, TenantOrAppNotFoundException {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        JsonObject config = new JsonObject();
        StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                .modifyConfigToAddANewUserPoolForTesting(config, 2);

        try {
            Multitenancy.addNewOrUpdateAppOrTenant(
                    process.getProcess(),
                    new TenantIdentifier("c1", null, null),
                    new TenantConfig(
                            new TenantIdentifier("c2", null, null),
                            new EmailPasswordConfig(true),
                            new ThirdPartyConfig(true, null),
                            new PasswordlessConfig(true),
                            config
                    )
            );
            fail();
        } catch (BadPermissionException e) {
            assertEquals("You must use the base tenant to create a new connectionUriDomain", e.getMessage());
        }

        try {
            Multitenancy.addNewOrUpdateAppOrTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, "a1", null),
                    new TenantConfig(
                            new TenantIdentifier(null, "a2", null),
                            new EmailPasswordConfig(true),
                            new ThirdPartyConfig(true, null),
                            new PasswordlessConfig(true),
                            config
                    )
            );
            fail();
        } catch (BadPermissionException e) {
            assertEquals("You must use the public tenantId and public appId to add a new app", e.getMessage());
        }

        try {
            Multitenancy.addNewOrUpdateAppOrTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, null, "t1"),
                    new TenantConfig(
                            new TenantIdentifier(null, null, "t2"),
                            new EmailPasswordConfig(true),
                            new ThirdPartyConfig(true, null),
                            new PasswordlessConfig(true),
                            config
                    )
            );
            fail();
        } catch (BadPermissionException e) {
            assertEquals("You must use the public tenantId to add a new tenant to this app", e.getMessage());
        }

        try {
            Multitenancy.addNewOrUpdateAppOrTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, "a1", "t1"),
                    new TenantConfig(
                            new TenantIdentifier(null, "a1", "t2"),
                            new EmailPasswordConfig(true),
                            new ThirdPartyConfig(true, null),
                            new PasswordlessConfig(true),
                            config
                    )
            );
            fail();
        } catch (BadPermissionException e) {
            assertEquals("You must use the public tenantId to add a new tenant to this app", e.getMessage());
        }

        try {
            Multitenancy.addNewOrUpdateAppOrTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, "a1", null),
                    new TenantConfig(
                            new TenantIdentifier(null, "a2", "t2"),
                            new EmailPasswordConfig(true),
                            new ThirdPartyConfig(true, null),
                            new PasswordlessConfig(true),
                            config
                    )
            );
            fail();
        } catch (BadPermissionException e) {
            assertEquals("You must use the same app to create new tenant", e.getMessage());
        }

        try {
            Multitenancy.addNewOrUpdateAppOrTenant(
                    process.getProcess(),
                    new TenantIdentifier("c1", null, null),
                    new TenantConfig(
                            new TenantIdentifier("c2", null, "t1"),
                            new EmailPasswordConfig(true),
                            new ThirdPartyConfig(true, null),
                            new PasswordlessConfig(true),
                            config
                    )
            );
            fail();
        } catch (BadPermissionException e) {
            assertEquals("You must use the same app to create new tenant", e.getMessage());
        }

        try {
            Multitenancy.addNewOrUpdateAppOrTenant(
                    process.getProcess(),
                    new TenantIdentifier("c1", null, "t1"),
                    new TenantConfig(
                            new TenantIdentifier("c1", null, "t2"),
                            new EmailPasswordConfig(true),
                            new ThirdPartyConfig(true, null),
                            new PasswordlessConfig(true),
                            config
                    )
            );
            fail();
        } catch (BadPermissionException e) {
            assertEquals("You must use the public tenantId to add a new tenant to this app", e.getMessage());
        }

        try {
            Multitenancy.addNewOrUpdateAppOrTenant(
                    process.getProcess(),
                    new TenantIdentifier("c1", "a1", null),
                    new TenantConfig(
                            new TenantIdentifier("c1", "a2", null),
                            new EmailPasswordConfig(true),
                            new ThirdPartyConfig(true, null),
                            new PasswordlessConfig(true),
                            config
                    )
            );
            fail();
        } catch (BadPermissionException e) {
            assertEquals("You must use the public tenantId and public appId to add a new app", e.getMessage());
        }

        try {
            Multitenancy.addNewOrUpdateAppOrTenant(
                    process.getProcess(),
                    new TenantIdentifier("c1", "a1", "t1"),
                    new TenantConfig(
                            new TenantIdentifier("c1", "a1", "t2"),
                            new EmailPasswordConfig(true),
                            new ThirdPartyConfig(true, null),
                            new PasswordlessConfig(true),
                            config
                    )
            );
            fail();
        } catch (BadPermissionException e) {
            assertEquals("You must use the public tenantId to add a new tenant to this app", e.getMessage());
        }

        try {
            Multitenancy.addNewOrUpdateAppOrTenant(
                    process.getProcess(),
                    new TenantIdentifier("c1", "a1", null),
                    new TenantConfig(
                            new TenantIdentifier("c2", "a1", "t1"),
                            new EmailPasswordConfig(true),
                            new ThirdPartyConfig(true, null),
                            new PasswordlessConfig(true),
                            config
                    )
            );
            fail();
        } catch (BadPermissionException e) {
            assertEquals("You must use the same app to create new tenant", e.getMessage());
        }

        try {
            Multitenancy.addNewOrUpdateAppOrTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, null, null),
                    new TenantConfig(
                            new TenantIdentifier("c1", "a1", "t1"),
                            new EmailPasswordConfig(true),
                            new ThirdPartyConfig(true, null),
                            new PasswordlessConfig(true),
                            config
                    )
            );
            fail();
        } catch (BadPermissionException e) {
            assertEquals("You must use the same app to create new tenant", e.getMessage());
        }

        try {
            Multitenancy.addNewOrUpdateAppOrTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, null, null),
                    new TenantConfig(
                            new TenantIdentifier(null, "a1", "t1"),
                            new EmailPasswordConfig(true),
                            new ThirdPartyConfig(true, null),
                            new PasswordlessConfig(true),
                            config
                    )
            );
            fail();
        } catch (BadPermissionException e) {
            assertEquals("You must use the same app to create new tenant", e.getMessage());
        }

        TenantConfig[] allTenants = Multitenancy.getAllTenants(new TenantIdentifier(null, null, null),
                process.getProcess());
        assertEquals(1, allTenants.length);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testUpdationOfDefaultTenant()
            throws InterruptedException, BadPermissionException, InvalidProviderConfigException,
            DeletionInProgressException, StorageQueryException, FeatureNotEnabledException, IOException,
            InvalidConfigException, CannotModifyBaseConfigException, TenantOrAppNotFoundException {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        Multitenancy.addNewOrUpdateAppOrTenant(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                new TenantConfig(
                        new TenantIdentifier(null, null, null),
                        new EmailPasswordConfig(true),
                        new ThirdPartyConfig(true, null),
                        new PasswordlessConfig(false),
                        new JsonObject()
                )
        );

        TenantConfig[] allTenants = Multitenancy.getAllTenants(new TenantIdentifier(null, null, null),
                process.getProcess());
        assertEquals(1, allTenants.length);
        assertFalse(allTenants[0].passwordlessConfig.enabled);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
