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
import io.supertokens.config.CoreConfig;
import io.supertokens.config.CoreConfigTestContent;
import io.supertokens.config.annotations.ConfigYamlOnly;
import io.supertokens.config.annotations.IgnoreForAnnotationCheck;
import io.supertokens.config.annotations.NotConflictingInApp;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlag;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.multitenancy.exception.CannotModifyBaseConfigException;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.*;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.session.refreshToken.RefreshTokenKey;
import io.supertokens.signingkeys.AccessTokenSigningKey;
import io.supertokens.signingkeys.JWTSigningKey;
import io.supertokens.signingkeys.SigningKeys;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.thirdparty.InvalidProviderConfigException;
import io.supertokens.version.Version;
import org.junit.*;
import org.junit.rules.TestRule;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;

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

        Assert.assertEquals(Config.getConfig(process.getProcess()).getRefreshTokenValidityInMillis(),
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

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        if (StorageLayer.isInMemDb(process.getProcess())) {
            return;
        }

        JsonObject tenantConfig = new JsonObject();
        tenantConfig.add("refresh_token_validity", new JsonPrimitive(144002));
        tenantConfig.add("password_reset_token_lifetime", new JsonPrimitive(3600001));
        StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                .modifyConfigToAddANewUserPoolForTesting(tenantConfig, 2);

        Config.loadAllTenantConfig(process.getProcess(), new TenantConfig[]{
                new TenantConfig(new TenantIdentifier("abc", null, null), new EmailPasswordConfig(false),
                        new ThirdPartyConfig(false, new ThirdPartyConfig.Provider[0]),
                        new PasswordlessConfig(false),
                        null, null, tenantConfig)}, new ArrayList<>());

        Assert.assertEquals(Config.getConfig(process.getProcess()).getRefreshTokenValidityInMillis(),
                (long) 144001 * 60 * 1000);
        Assert.assertEquals(Config.getConfig(process.getProcess()).getPasswordResetTokenLifetime(),
                3600000);
        Assert.assertEquals(Config.getConfig(process.getProcess()).getPasswordlessMaxCodeInputAttempts(),
                5);
        Assert.assertEquals(Config.getConfig(process.getProcess()).getAccessTokenSigningKeyDynamic(),
                false);

        Assert.assertEquals(Config.getConfig(new TenantIdentifier("abc", null, null), process.getProcess())
                        .getRefreshTokenValidityInMillis(),
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
                            null, null, tenantConfig)}, new ArrayList<>());
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

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject tenantConfig = new JsonObject();
        tenantConfig.add("access_token_signing_key_dynamic", new JsonPrimitive(false));

        try {
            Config.loadAllTenantConfig(process.getProcess(), new TenantConfig[]{
                    new TenantConfig(new TenantIdentifier(null, null, "abc"), new EmailPasswordConfig(false),
                            new ThirdPartyConfig(false, new ThirdPartyConfig.Provider[0]),
                            new PasswordlessConfig(false),
                            null, null, tenantConfig)}, new ArrayList<>());
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

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        if (StorageLayer.isInMemDb(process.getProcess())) {
            return;
        }

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
                            null, null, tenantConfig)}, new ArrayList<>());

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

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        if (StorageLayer.isInMemDb(process.getProcess())) {
            return;
        }

        TenantConfig[] tenants = new TenantConfig[4];

        {
            JsonObject tenantConfig = new JsonObject();

            tenantConfig.add("email_verification_token_lifetime", new JsonPrimitive(144002));
            StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                    .modifyConfigToAddANewUserPoolForTesting(tenantConfig, 2);
            tenants[0] = new TenantConfig(new TenantIdentifier("c1", null, null), new EmailPasswordConfig(false),
                    new ThirdPartyConfig(false, new ThirdPartyConfig.Provider[0]),
                    new PasswordlessConfig(false),
                    null, null, tenantConfig);
        }

        {
            JsonObject tenantConfig = new JsonObject();
            StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                    .modifyConfigToAddANewUserPoolForTesting(tenantConfig, 2);
            tenantConfig.add("email_verification_token_lifetime", new JsonPrimitive(144003));
            tenants[1] = new TenantConfig(new TenantIdentifier("c1", null, "t1"), new EmailPasswordConfig(false),
                    new ThirdPartyConfig(false, new ThirdPartyConfig.Provider[0]),
                    new PasswordlessConfig(false),
                    null, null, tenantConfig);
        }

        {
            JsonObject tenantConfig = new JsonObject();
            tenantConfig.add("email_verification_token_lifetime", new JsonPrimitive(144004));
            tenants[2] = new TenantConfig(new TenantIdentifier(null, null, "t2"), new EmailPasswordConfig(false),
                    new ThirdPartyConfig(false, new ThirdPartyConfig.Provider[0]),
                    new PasswordlessConfig(false),
                    null, null, tenantConfig);
        }

        {
            JsonObject tenantConfig = new JsonObject();
            tenantConfig.add("email_verification_token_lifetime", new JsonPrimitive(144005));
            tenants[3] = new TenantConfig(new TenantIdentifier(null, null, "t1"), new EmailPasswordConfig(false),
                    new ThirdPartyConfig(false, new ThirdPartyConfig.Provider[0]),
                    new PasswordlessConfig(false),
                    null, null, tenantConfig);
        }

        Config.loadAllTenantConfig(process.getProcess(), tenants, new ArrayList<>());

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

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        if (StorageLayer.isInMemDb(process.getProcess())) {
            return;
        }

        TenantConfig[] tenants = new TenantConfig[2];

        {
            JsonObject tenantConfig = new JsonObject();

            tenantConfig.add("email_verification_token_lifetime", new JsonPrimitive(144002));
            StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                    .modifyConfigToAddANewUserPoolForTesting(tenantConfig, 2);
            tenants[0] = new TenantConfig(new TenantIdentifier("c1", null, null), new EmailPasswordConfig(false),
                    new ThirdPartyConfig(false, new ThirdPartyConfig.Provider[0]),
                    new PasswordlessConfig(false),
                    null, null, tenantConfig);
        }

        {
            JsonObject tenantConfig = new JsonObject();
            StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                    .modifyConfigToAddANewUserPoolForTesting(tenantConfig, 2);
            tenantConfig.add("email_verification_token_lifetime", new JsonPrimitive(144003));
            tenants[1] = new TenantConfig(new TenantIdentifier("c2", null, null), new EmailPasswordConfig(false),
                    new ThirdPartyConfig(false, new ThirdPartyConfig.Provider[0]),
                    new PasswordlessConfig(false),
                    null, null, tenantConfig);
        }

        try {
            Config.loadAllTenantConfig(process.getProcess(), tenants, new ArrayList<>());
            fail();
        } catch (InvalidConfigException e) {
            assert (e.getMessage()
                    .equals("ConnectionUriDomain: c2 cannot be mapped to the same user pool as c1"));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testCreationOfTenantsUsingValidSourceTenant()
            throws InterruptedException, BadPermissionException, InvalidProviderConfigException,
            StorageQueryException, FeatureNotEnabledException, IOException,
            InvalidConfigException, CannotModifyBaseConfigException, TenantOrAppNotFoundException {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        Multitenancy.addNewOrUpdateAppOrTenant(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                new TenantConfig(
                        new TenantIdentifier(null, null, "t1"),
                        new EmailPasswordConfig(true),
                        new ThirdPartyConfig(true, null),
                        new PasswordlessConfig(true),
                        null, null, new JsonObject()
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
                        null, null, new JsonObject()
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
                        null, null, new JsonObject()
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
                        null, null, new JsonObject()
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
                        null, null, new JsonObject()
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
                        null, null, new JsonObject()
                )
        );

        JsonObject config = new JsonObject();
        StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                .modifyConfigToAddANewUserPoolForTesting(config, 2);

        if (!StorageLayer.isInMemDb(process.getProcess())) {
            Multitenancy.addNewOrUpdateAppOrTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, null, null),
                    new TenantConfig(
                            new TenantIdentifier("c1", null, null),
                            new EmailPasswordConfig(true),
                            new ThirdPartyConfig(true, null),
                            new PasswordlessConfig(true),
                            null, null, config
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
                            null, null, config
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
                            null, null, config
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
                            null, null, config
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
                            null, null, config
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
                            null, null, config
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
                            null, null, config
                    )
            );
        }

        TenantConfig[] allTenants = Multitenancy.getAllTenants(process.getProcess());
        if (StorageLayer.isInMemDb(process.getProcess())) {
            assertEquals(7, allTenants.length);
        } else {
            assertEquals(14, allTenants.length);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testInvalidCasesOfTenantCreation()
            throws InterruptedException, BadPermissionException, InvalidProviderConfigException,
            StorageQueryException, FeatureNotEnabledException, IOException,
            InvalidConfigException, CannotModifyBaseConfigException, TenantOrAppNotFoundException {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

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
                            null, null, config
                    )
            );
            fail();
        } catch (BadPermissionException e) {
            assertEquals("You must use the default or same connectionUriDomain to create/update a connectionUriDomain",
                    e.getMessage());
        }

        try {
            Multitenancy.addNewOrUpdateAppOrTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, null, null),
                    new TenantConfig(
                            new TenantIdentifier(null, "a1", null),
                            new EmailPasswordConfig(true),
                            new ThirdPartyConfig(true, null),
                            new PasswordlessConfig(true),
                            null, null, config
                    )
            );

            Multitenancy.addNewOrUpdateAppOrTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, "a1", null),
                    new TenantConfig(
                            new TenantIdentifier(null, "a2", null),
                            new EmailPasswordConfig(true),
                            new ThirdPartyConfig(true, null),
                            new PasswordlessConfig(true),
                            null, null, config
                    )
            );
            fail();
        } catch (BadPermissionException e) {
            assertEquals("You must use the public or same app to add/update an app", e.getMessage());
        }

        try {
            Multitenancy.addNewOrUpdateAppOrTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, null, null),
                    new TenantConfig(
                            new TenantIdentifier(null, null, "t1"),
                            new EmailPasswordConfig(true),
                            new ThirdPartyConfig(true, null),
                            new PasswordlessConfig(true),
                            null, null, config
                    )
            );

            Multitenancy.addNewOrUpdateAppOrTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, null, "t1"),
                    new TenantConfig(
                            new TenantIdentifier(null, null, "t2"),
                            new EmailPasswordConfig(true),
                            new ThirdPartyConfig(true, null),
                            new PasswordlessConfig(true),
                            null, null, config
                    )
            );
            fail();
        } catch (BadPermissionException e) {
            assertEquals("You must use the public or same tenant to add/update a tenant", e.getMessage());
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
                            null, null, config
                    )
            );
            fail();
        } catch (BadPermissionException e) {
            assertEquals("You must use the public or same tenant to add/update a tenant", e.getMessage());
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
                            null, null, config
                    )
            );
            fail();
        } catch (BadPermissionException e) {
            assertEquals("You must use the same app to create/update a tenant", e.getMessage());
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
                            null, null, config
                    )
            );
            fail();
        } catch (BadPermissionException e) {
            assertEquals("You must use the same app to create/update a tenant", e.getMessage());
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
                            null, null, config
                    )
            );
            fail();
        } catch (BadPermissionException e) {
            assertEquals("You must use the public or same tenant to add/update a tenant", e.getMessage());
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
                            null, null, config
                    )
            );
            fail();
        } catch (BadPermissionException e) {
            assertEquals("You must use the public or same app to add/update an app", e.getMessage());
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
                            null, null, config
                    )
            );
            fail();
        } catch (BadPermissionException e) {
            assertEquals("You must use the public or same tenant to add/update a tenant", e.getMessage());
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
                            null, null, config
                    )
            );
            fail();
        } catch (BadPermissionException e) {
            assertEquals("You must use the same app to create/update a tenant", e.getMessage());
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
                            null, null, config
                    )
            );
            fail();
        } catch (BadPermissionException e) {
            assertEquals("You must use the same app to create/update a tenant", e.getMessage());
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
                            null, null, config
                    )
            );
            fail();
        } catch (BadPermissionException e) {
            assertEquals("You must use the same app to create/update a tenant", e.getMessage());
        }

        TenantConfig[] allTenants = Multitenancy.getAllTenants(process.getProcess());
        assertEquals(3, allTenants.length);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testUpdationOfDefaultTenant()
            throws InterruptedException, BadPermissionException, InvalidProviderConfigException,
            StorageQueryException, FeatureNotEnabledException, IOException,
            InvalidConfigException, CannotModifyBaseConfigException, TenantOrAppNotFoundException {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        Multitenancy.addNewOrUpdateAppOrTenant(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                new TenantConfig(
                        new TenantIdentifier(null, null, null),
                        new EmailPasswordConfig(true),
                        new ThirdPartyConfig(true, null),
                        new PasswordlessConfig(false),
                        null, null, new JsonObject()
                )
        );

        TenantConfig[] allTenants = Multitenancy.getAllTenants(process.getProcess());
        assertEquals(1, allTenants.length);
        assertFalse(allTenants[0].passwordlessConfig.enabled);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testThatDifferentTenantsInSameAppCannotHaveDifferentAPIKeys() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        { // Create an app with API key
            JsonObject coreConfig = new JsonObject();
            coreConfig.addProperty("api_keys", "asdfasdfasdfasdfasdf");

            Multitenancy.addNewOrUpdateAppOrTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, null, null),
                    new TenantConfig(
                            new TenantIdentifier(null, "a1", null),
                            new EmailPasswordConfig(true),
                            new ThirdPartyConfig(true, null),
                            new PasswordlessConfig(false),
                            null, null, coreConfig
                    )
            );
        }

        { // Create an tenant with different API key
            JsonObject coreConfig = new JsonObject();
            coreConfig.addProperty("api_keys", "qwerqwerqwerqwerqwer");

            try {
                Multitenancy.addNewOrUpdateAppOrTenant(
                        process.getProcess(),
                        new TenantIdentifier(null, "a1", null),
                        new TenantConfig(
                                new TenantIdentifier(null, "a1", "t1"),
                                new EmailPasswordConfig(true),
                                new ThirdPartyConfig(true, null),
                                new PasswordlessConfig(false),
                                null, null, coreConfig
                        )
                );
                fail();
            } catch (InvalidConfigException e) {
                assertEquals("You cannot set different values for api_keys for the same appId", e.getMessage());
            }
        }

        { // Allow same API key or no setting
            Multitenancy.addNewOrUpdateAppOrTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, "a1", null),
                    new TenantConfig(
                            new TenantIdentifier(null, "a1", "t1"),
                            new EmailPasswordConfig(true),
                            new ThirdPartyConfig(true, null),
                            new PasswordlessConfig(false),
                            null, null, new JsonObject()
                    )
            );

            JsonObject coreConfig = new JsonObject();
            coreConfig.addProperty("api_keys", "asdfasdfasdfasdfasdf");

            Multitenancy.addNewOrUpdateAppOrTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, "a1", null),
                    new TenantConfig(
                            new TenantIdentifier(null, "a1", "t2"),
                            new EmailPasswordConfig(true),
                            new ThirdPartyConfig(true, null),
                            new PasswordlessConfig(false),
                            null, null, coreConfig
                    )
            );
        }

        { // Create another app with different API key
            JsonObject coreConfig = new JsonObject();
            coreConfig.addProperty("api_keys", "qwerqwerqwerqwerqwer");

            Multitenancy.addNewOrUpdateAppOrTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, null, null),
                    new TenantConfig(
                            new TenantIdentifier(null, "a2", null),
                            new EmailPasswordConfig(true),
                            new ThirdPartyConfig(true, null),
                            new PasswordlessConfig(false),
                            null, null, coreConfig
                    )
            );
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    @Test
    public void testConfigNormalisation() throws Exception {
        TenantIdentifier[][] testCases = new TenantIdentifier[][]{
                new TenantIdentifier[]{
                        new TenantIdentifier("c1", null, null),
                },
                new TenantIdentifier[]{
                        new TenantIdentifier("c1", null, null),
                        new TenantIdentifier("c1", "a1", null),
                },
                new TenantIdentifier[]{
                        new TenantIdentifier("c1", null, null),
                        new TenantIdentifier("c1", "a1", null),
                        new TenantIdentifier("c1", "a1", "t1"),
                },
                new TenantIdentifier[]{
                        new TenantIdentifier("c1", null, null),
                        new TenantIdentifier("c1", null, "t1"),
                },
                new TenantIdentifier[]{
                        new TenantIdentifier("a1", null, null),
                },
                new TenantIdentifier[]{
                        new TenantIdentifier(null, "a1", null),
                        new TenantIdentifier(null, "a1", "t1"),
                },
                new TenantIdentifier[]{
                        new TenantIdentifier(null, null, "t1"),
                },
        };

        for (TenantIdentifier[] testCase : testCases) {

            Utils.reset();
            String[] args = {"../"};

            Utils.setValueInConfig("email_verification_token_lifetime", "1000");
            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
            FeatureFlagTestContent.getInstance(process.getProcess())
                    .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
            process.startProcess();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

            if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
                return;
            }

            if (StorageLayer.isInMemDb(process.getProcess())) {
                if (!testCase[0].getConnectionUriDomain().equals(TenantIdentifier.DEFAULT_CONNECTION_URI)) {
                    continue;
                }
            }

            for (int i = 0; i < testCase.length; i++) {
                TenantIdentifier tenantIdentifier = testCase[i];

                { // create without setting value and test it
                    JsonObject coreConfigJson = new JsonObject();
                    StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                            .modifyConfigToAddANewUserPoolForTesting(coreConfigJson, 1);

                    Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                            tenantIdentifier,
                            new EmailPasswordConfig(true),
                            new ThirdPartyConfig(true, null),
                            new PasswordlessConfig(true),
                            null, null, coreConfigJson
                    ), false);

                    CoreConfig coreConfig = Config.getConfig(tenantIdentifier, process.getProcess());
                    // Check for previous value in the hierarchy
                    assertEquals((i + 1) * 1000, coreConfig.getEmailVerificationTokenLifetime());

                }

                { // set a new value and test that it works
                    JsonObject coreConfigJson = new JsonObject();
                    coreConfigJson.addProperty("email_verification_token_lifetime", (i + 2) * 1000);
                    StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                            .modifyConfigToAddANewUserPoolForTesting(coreConfigJson, 1);

                    Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                            tenantIdentifier,
                            new EmailPasswordConfig(true),
                            new ThirdPartyConfig(true, null),
                            new PasswordlessConfig(true),
                            null, null, coreConfigJson
                    ), false);

                    CoreConfig coreConfig2 = Config.getConfig(tenantIdentifier, process.getProcess());
                    assertEquals((i + 2) * 1000, coreConfig2.getEmailVerificationTokenLifetime());
                }
            }

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }
    }

    @Test
    public void testTenantConfigIsNormalisedFromCUD1() throws Exception {
        String[] args = {"../"};

        Utils.setValueInConfig("email_verification_token_lifetime", "1000");
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        { // create app without value
            JsonObject coreConfigJson = new JsonObject();
            StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                    .modifyConfigToAddANewUserPoolForTesting(coreConfigJson, 1);
            TenantIdentifier tenantIdentifier = new TenantIdentifier(null, "a1", null);
            Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                    tenantIdentifier,
                    new EmailPasswordConfig(true),
                    new ThirdPartyConfig(true, null),
                    new PasswordlessConfig(true),
                    null, null, coreConfigJson
            ), false);

            CoreConfig coreConfig = Config.getConfig(tenantIdentifier, process.getProcess());
            // Check for previous value in the hierarchy
            assertEquals(1000, coreConfig.getEmailVerificationTokenLifetime());
        }

        { // create tenant without value
            JsonObject coreConfigJson = new JsonObject();
            StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                    .modifyConfigToAddANewUserPoolForTesting(coreConfigJson, 1);
            TenantIdentifier tenantIdentifier = new TenantIdentifier(null, "a1", "t1");
            Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                    tenantIdentifier,
                    new EmailPasswordConfig(true),
                    new ThirdPartyConfig(true, null),
                    new PasswordlessConfig(true),
                    null, null, coreConfigJson
            ), false);

            CoreConfig coreConfig = Config.getConfig(tenantIdentifier, process.getProcess());
            // Check for previous value in the hierarchy
            assertEquals(1000, coreConfig.getEmailVerificationTokenLifetime());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testTenantConfigIsNormalisedFromCUD2() throws Exception {
        String[] args = {"../"};

        Utils.setValueInConfig("email_verification_token_lifetime", "1000");
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

        { // create cud with value
            JsonObject coreConfigJson = new JsonObject();
            coreConfigJson.addProperty("email_verification_token_lifetime", 2000);
            StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                    .modifyConfigToAddANewUserPoolForTesting(coreConfigJson, 1);
            TenantIdentifier tenantIdentifier = new TenantIdentifier("c1", null, null);
            Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                    tenantIdentifier,
                    new EmailPasswordConfig(true),
                    new ThirdPartyConfig(true, null),
                    new PasswordlessConfig(true),
                    null, null, coreConfigJson
            ), false);

            CoreConfig coreConfig = Config.getConfig(tenantIdentifier, process.getProcess());
            // Check for previous value in the hierarchy
            assertEquals(2000, coreConfig.getEmailVerificationTokenLifetime());
        }

        { // create app without value
            JsonObject coreConfigJson = new JsonObject();
            StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                    .modifyConfigToAddANewUserPoolForTesting(coreConfigJson, 1);
            TenantIdentifier tenantIdentifier = new TenantIdentifier("c1", "a1", null);
            Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                    tenantIdentifier,
                    new EmailPasswordConfig(true),
                    new ThirdPartyConfig(true, null),
                    new PasswordlessConfig(true),
                    null, null, coreConfigJson
            ), false);

            CoreConfig coreConfig = Config.getConfig(tenantIdentifier, process.getProcess());
            // Check for previous value in the hierarchy
            assertEquals(2000, coreConfig.getEmailVerificationTokenLifetime());
        }

        { // create tenant without value
            JsonObject coreConfigJson = new JsonObject();
            StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                    .modifyConfigToAddANewUserPoolForTesting(coreConfigJson, 1);
            TenantIdentifier tenantIdentifier = new TenantIdentifier("c1", "a1", "t1");
            Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                    tenantIdentifier,
                    new EmailPasswordConfig(true),
                    new ThirdPartyConfig(true, null),
                    new PasswordlessConfig(true),
                    null, null, coreConfigJson
            ), false);

            CoreConfig coreConfig = Config.getConfig(tenantIdentifier, process.getProcess());
            // Check for previous value in the hierarchy
            assertEquals(2000, coreConfig.getEmailVerificationTokenLifetime());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testInvalidConfigWhileCreatingNewTenant() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        try {
            JsonObject coreConfig = new JsonObject();
            coreConfig.addProperty("foo", "bar");
            Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                    new TenantIdentifier(null, null, "t1"),
                    new EmailPasswordConfig(true),
                    new ThirdPartyConfig(true, null),
                    new PasswordlessConfig(true),
                    null, null, coreConfig
            ), false);
            fail();
        } catch (InvalidConfigException e) {
            assertEquals("Invalid config key: foo", e.getMessage());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testThatConfigChangesReloadsConfig() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        TenantIdentifier t1 = new TenantIdentifier(null, null, "t1");

        {
            JsonObject coreConfig = new JsonObject();
            Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                    t1,
                    new EmailPasswordConfig(true),
                    new ThirdPartyConfig(true, null),
                    new PasswordlessConfig(true),
                    null, null, coreConfig
            ), false);
        }

        {
            Config configBefore = Config.getInstance(t1, process.getProcess());

            ProcessState.getInstance(process.getProcess()).clear();

            JsonObject coreConfig = new JsonObject();
            Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                    t1,
                    new EmailPasswordConfig(true),
                    new ThirdPartyConfig(true, null),
                    new PasswordlessConfig(true),
                    null, null, coreConfig
            ), false);

            assertNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.TENANTS_CHANGED_DURING_REFRESH_FROM_DB));

            Config configAfter = Config.getInstance(t1, process.getProcess());

            assertEquals(configBefore, configAfter);
        }

        {
            Config configBefore = Config.getInstance(t1, process.getProcess());

            JsonObject coreConfig = new JsonObject();
            coreConfig.addProperty("email_verification_token_lifetime", 2000);
            Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                    t1,
                    new EmailPasswordConfig(true),
                    new ThirdPartyConfig(true, null),
                    new PasswordlessConfig(true),
                    null, null, coreConfig
            ), false);

            Config configAfter = Config.getInstance(t1, process.getProcess());

            assertNotEquals(configBefore, configAfter);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testThatConfigChangesInAppReloadsConfigInTenant() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        TenantIdentifier a1 = new TenantIdentifier(null, "a1", null);
        TenantIdentifier t1 = new TenantIdentifier(null, "a1", "t1");

        {
            JsonObject coreConfig = new JsonObject();
            Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                    a1,
                    new EmailPasswordConfig(true),
                    new ThirdPartyConfig(true, null),
                    new PasswordlessConfig(true),
                    null, null, coreConfig
            ), false);
            Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                    t1,
                    new EmailPasswordConfig(true),
                    new ThirdPartyConfig(true, null),
                    new PasswordlessConfig(true),
                    null, null, coreConfig
            ), false);
        }

        {
            ProcessState.getInstance(process.getProcess()).clear();

            Config configBefore = Config.getInstance(t1, process.getProcess());

            JsonObject coreConfig = new JsonObject();
            Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                    a1,
                    new EmailPasswordConfig(true),
                    new ThirdPartyConfig(true, null),
                    new PasswordlessConfig(true),
                    null, null, coreConfig
            ), false);
            Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                    t1,
                    new EmailPasswordConfig(true),
                    new ThirdPartyConfig(true, null),
                    new PasswordlessConfig(true),
                    null, null, coreConfig
            ), false);

            assertNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.TENANTS_CHANGED_DURING_REFRESH_FROM_DB));

            Config configAfter = Config.getInstance(t1, process.getProcess());

            assertEquals(configBefore, configAfter);
        }

        {
            Config configBefore = Config.getInstance(t1, process.getProcess());

            JsonObject coreConfig = new JsonObject();
            coreConfig.addProperty("email_verification_token_lifetime", 2000);
            Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                    a1,
                    new EmailPasswordConfig(true),
                    new ThirdPartyConfig(true, null),
                    new PasswordlessConfig(true),
                    null, null, coreConfig
            ), false);

            Config configAfter = Config.getInstance(t1, process.getProcess());

            assertNotEquals(configBefore, configAfter);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testThatConfigChangesReloadsStorageLayer() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        TenantIdentifier t1 = new TenantIdentifier(null, null, "t1");

        {
            JsonObject coreConfig = new JsonObject();
            Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                    t1,
                    new EmailPasswordConfig(true),
                    new ThirdPartyConfig(true, null),
                    new PasswordlessConfig(true),
                    null, null, coreConfig
            ), false);
        }

        {
            ProcessState.getInstance(process.getProcess()).clear();
            Storage storageLayerBefore = StorageLayer.getStorage(t1, process.getProcess());

            JsonObject coreConfig = new JsonObject();
            Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                    t1,
                    new EmailPasswordConfig(true),
                    new ThirdPartyConfig(true, null),
                    new PasswordlessConfig(true),
                    null, null, coreConfig
            ), false);

            assertNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.TENANTS_CHANGED_DURING_REFRESH_FROM_DB));
            Storage storageLayerAfter = StorageLayer.getStorage(t1, process.getProcess());

            assertEquals(storageLayerBefore, storageLayerAfter);
        }

        {
            Storage storageLayerBefore = StorageLayer.getStorage(t1, process.getProcess());

            JsonObject coreConfig = new JsonObject();
            coreConfig.addProperty("email_verification_token_lifetime", 2000);
            Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                    t1,
                    new EmailPasswordConfig(true),
                    new ThirdPartyConfig(true, null),
                    new PasswordlessConfig(true),
                    null, null, coreConfig
            ), false);

            Storage storageLayerAfter = StorageLayer.getStorage(t1, process.getProcess());

            assertEquals(storageLayerBefore, storageLayerAfter);
        }

        if (!StorageLayer.isInMemDb(process.getProcess())) {
            Storage storageLayerBefore = StorageLayer.getStorage(t1, process.getProcess());

            JsonObject coreConfig = new JsonObject();
            StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                    .modifyConfigToAddANewUserPoolForTesting(coreConfig, 1);

            Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                    t1,
                    new EmailPasswordConfig(true),
                    new ThirdPartyConfig(true, null),
                    new PasswordlessConfig(true),
                    null, null, coreConfig
            ), false);

            Storage storageLayerAfter = StorageLayer.getStorage(t1, process.getProcess());

            assertNotEquals(storageLayerBefore, storageLayerAfter);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testThatConfigChangesReloadsFeatureFlag() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        AppIdentifier t1 = new AppIdentifier(null, "a1");

        {
            JsonObject coreConfig = new JsonObject();
            Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                    t1.getAsPublicTenantIdentifier(),
                    new EmailPasswordConfig(true),
                    new ThirdPartyConfig(true, null),
                    new PasswordlessConfig(true),
                    null, null, coreConfig
            ), false);
        }

        {
            ProcessState.getInstance(process.getProcess()).clear();

            FeatureFlag featureFlagBefore = FeatureFlag.getInstance(process.getProcess(), t1);

            JsonObject coreConfig = new JsonObject();
            Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                    t1.getAsPublicTenantIdentifier(),
                    new EmailPasswordConfig(true),
                    new ThirdPartyConfig(true, null),
                    new PasswordlessConfig(true),
                    null, null, coreConfig
            ), false);

            assertNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.TENANTS_CHANGED_DURING_REFRESH_FROM_DB));
            FeatureFlag featureFlagAfter = FeatureFlag.getInstance(process.getProcess(), t1);

            assertEquals(featureFlagBefore, featureFlagAfter);
        }

        {
            FeatureFlag featureFlagBefore = FeatureFlag.getInstance(process.getProcess(), t1);

            JsonObject coreConfig = new JsonObject();
            coreConfig.addProperty("email_verification_token_lifetime", 2000);
            Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                    t1.getAsPublicTenantIdentifier(),
                    new EmailPasswordConfig(true),
                    new ThirdPartyConfig(true, null),
                    new PasswordlessConfig(true),
                    null, null, coreConfig
            ), false);

            FeatureFlag featureFlagAfter = FeatureFlag.getInstance(process.getProcess(), t1);

            assertNotEquals(featureFlagBefore, featureFlagAfter);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testThatConfigChangesReloadsSigningKeys() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        AppIdentifier t1 = new AppIdentifier(null, "a1");

        {
            JsonObject coreConfig = new JsonObject();
            Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                    t1.getAsPublicTenantIdentifier(),
                    new EmailPasswordConfig(true),
                    new ThirdPartyConfig(true, null),
                    new PasswordlessConfig(true),
                    null, null, coreConfig
            ), false);
        }

        {
            ProcessState.getInstance(process.getProcess()).clear();

            AccessTokenSigningKey accessTokenSigningKeyBefore = AccessTokenSigningKey.getInstance(t1,
                    process.getProcess());
            RefreshTokenKey refreshTokenKeyBefore = RefreshTokenKey.getInstance(t1, process.getProcess());
            JWTSigningKey jwtSigningKeyBefore = JWTSigningKey.getInstance(t1, process.getProcess());
            SigningKeys signingKeysBefore = SigningKeys.getInstance(t1, process.getProcess());

            JsonObject coreConfig = new JsonObject();
            Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                    t1.getAsPublicTenantIdentifier(),
                    new EmailPasswordConfig(true),
                    new ThirdPartyConfig(true, null),
                    new PasswordlessConfig(true),
                    null, null, coreConfig
            ), false);

            assertNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.TENANTS_CHANGED_DURING_REFRESH_FROM_DB));

            AccessTokenSigningKey accessTokenSigningKeyAfter = AccessTokenSigningKey.getInstance(t1,
                    process.getProcess());
            RefreshTokenKey refreshTokenKeyAfter = RefreshTokenKey.getInstance(t1, process.getProcess());
            JWTSigningKey jwtSigningKeyAfter = JWTSigningKey.getInstance(t1, process.getProcess());
            SigningKeys signingKeysAfter = SigningKeys.getInstance(t1, process.getProcess());

            assertEquals(accessTokenSigningKeyBefore, accessTokenSigningKeyAfter);
            assertEquals(refreshTokenKeyBefore, refreshTokenKeyAfter);
            assertEquals(jwtSigningKeyBefore, jwtSigningKeyAfter);
            assertEquals(signingKeysBefore, signingKeysAfter);
        }

        {
            AccessTokenSigningKey accessTokenSigningKeyBefore = AccessTokenSigningKey.getInstance(t1,
                    process.getProcess());
            RefreshTokenKey refreshTokenKeyBefore = RefreshTokenKey.getInstance(t1, process.getProcess());
            JWTSigningKey jwtSigningKeyBefore = JWTSigningKey.getInstance(t1, process.getProcess());
            SigningKeys signingKeysBefore = SigningKeys.getInstance(t1, process.getProcess());

            JsonObject coreConfig = new JsonObject();
            coreConfig.addProperty("email_verification_token_lifetime", 2000);
            Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                    t1.getAsPublicTenantIdentifier(),
                    new EmailPasswordConfig(true),
                    new ThirdPartyConfig(true, null),
                    new PasswordlessConfig(true),
                    null, null, coreConfig
            ), false);

            AccessTokenSigningKey accessTokenSigningKeyAfter = AccessTokenSigningKey.getInstance(t1,
                    process.getProcess());
            RefreshTokenKey refreshTokenKeyAfter = RefreshTokenKey.getInstance(t1, process.getProcess());
            JWTSigningKey jwtSigningKeyAfter = JWTSigningKey.getInstance(t1, process.getProcess());
            SigningKeys signingKeysAfter = SigningKeys.getInstance(t1, process.getProcess());

            assertNotEquals(accessTokenSigningKeyBefore, accessTokenSigningKeyAfter);
            assertNotEquals(refreshTokenKeyBefore, refreshTokenKeyAfter);
            assertNotEquals(jwtSigningKeyBefore, jwtSigningKeyAfter);
            assertNotEquals(signingKeysBefore, signingKeysAfter);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testLoadAllTenantConfigWithDifferentConfigSavedInTheDb() throws Exception {
        // What is saved in db is not overwritten
        // New apps/tenants are added to the loaded config

        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // Save in db
        JsonObject config = new JsonObject();
        config.addProperty("email_verification_token_lifetime", 100);
        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                new TenantIdentifier(null, "a1", null),
                new EmailPasswordConfig(true),
                new ThirdPartyConfig(true, null),
                new PasswordlessConfig(true),
                null, null, config
        ), false);

        // Now load a new set of configs
        JsonObject config1 = new JsonObject();
        config1.addProperty("email_verification_token_lifetime", 200);
        JsonObject config2 = new JsonObject();
        config2.addProperty("email_verification_token_lifetime", 300);
        JsonObject config3 = new JsonObject();
        config3.addProperty("email_verification_token_lifetime", 400);
        JsonObject config4 = new JsonObject();
        config4.addProperty("email_verification_token_lifetime", 500);

        TenantConfig[] tenantConfigs = new TenantConfig[]{
                new TenantConfig(
                        new TenantIdentifier(null, null, null),
                        new EmailPasswordConfig(true),
                        new ThirdPartyConfig(false, null),
                        new PasswordlessConfig(true),
                        null, null, config1
                ),
                new TenantConfig(
                        new TenantIdentifier(null, "a2", null),
                        new EmailPasswordConfig(true),
                        new ThirdPartyConfig(false, null),
                        new PasswordlessConfig(true),
                        null, null, config2
                ),
                new TenantConfig(
                        new TenantIdentifier(null, "a2", "t1"),
                        new EmailPasswordConfig(true),
                        new ThirdPartyConfig(true, null),
                        new PasswordlessConfig(true),
                        null, null, config3
                ),
                new TenantConfig(
                        new TenantIdentifier(null, "a1", null),
                        new EmailPasswordConfig(false),
                        new ThirdPartyConfig(true, null),
                        new PasswordlessConfig(true),
                        null, null, config4
                ),
        };
        Config.loadAllTenantConfig(process.getProcess(), tenantConfigs);

        assertEquals(
                300,
                Config.getConfig(new TenantIdentifier(null, "a2", null), process.getProcess())
                        .getEmailVerificationTokenLifetime()
        );
        assertEquals(
                400,
                Config.getConfig(new TenantIdentifier(null, "a2", "t1"), process.getProcess())
                        .getEmailVerificationTokenLifetime()
        );
        assertEquals(
                100,
                Config.getConfig(new TenantIdentifier(null, "a1", null), process.getProcess())
                        .getEmailVerificationTokenLifetime()
        );

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testThatMistypedConfigThrowsError() throws Exception {
        String[] args = {"../"};

        Utils.setValueInConfig("email_verification_token_lifetime", "144001");
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject mistypedConfig = new JsonObject();
        mistypedConfig.addProperty("foo", "bar");

        try {
            Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                    new TenantIdentifier(null, "a1", null),
                    new EmailPasswordConfig(true),
                    new ThirdPartyConfig(true, null),
                    new PasswordlessConfig(true),
                    null, null, mistypedConfig
            ), false);
            fail();
        } catch (InvalidConfigException e) {
            assertTrue(e.getMessage().contains("Invalid config key: foo"));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testCoreSpecificConfigIsNotAllowedForNewTenants() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        String[] disallowedConfigs = new String[]{
                "port",
                "host",
                "info_log_path",
                "error_log_path",
                "max_server_pool_size",
                "base_path",
                "argon2_hashing_pool_size",
                "log_level",
                "firebase_password_hashing_pool_size",
                "supertokens_saas_secret",
                "supertokens_max_cdi_version"
        };

        for (String disallowedConfig : disallowedConfigs) {
            JsonObject config = new JsonObject();
            if (disallowedConfig.contains("size") || disallowedConfig.contains("port")) {
                config.addProperty(disallowedConfig, 1000);
            } else {
                config.addProperty(disallowedConfig, "somevalue");
            }

            try {
                Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                        new TenantIdentifier(null, "a1", null),
                        new EmailPasswordConfig(true),
                        new ThirdPartyConfig(true, null),
                        new PasswordlessConfig(true),
                        null, null, config
                ), false);
                fail();
            } catch (InvalidConfigException e) {
                assertTrue(e.getMessage().contains(disallowedConfig));
            }
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testAllConflictingConfigs() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        String[] disallowed = new String[]{
                "port",
                "host",
                "info_log_path",
                "error_log_path",
                "max_server_pool_size",
                "base_path",
                "argon2_hashing_pool_size",
                "log_level",
                "firebase_password_hashing_pool_size",
                "supertokens_saas_secret",
                "argon2_iterations",
                "argon2_memory_kb",
                "argon2_parallelism",
                "bcrypt_log_rounds",
                "supertokens_saas_load_only_cud"
        };
        Object[] disallowedValues = new Object[]{
                3567, // port
                "localhost", // host
                "info.log", // info_log_path
                "error.log", // error_log_path
                15, // max_server_pool_size
                "/new-base", // base_path
                12, // argon2_hashing_pool_size
                "DEBUG", // log_level
                12, // firebase_password_hashing_pool_size
                "abcd1234abcd1234", // supertokens_saas_secret
                1, // argon2_iterations
                87795, // argon2_memory_kb
                2, // argon2_parallelism
                11, // bcrypt_log_rounds
                "mydomain.com", // supertokens_saas_load_only_cud
        };

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

        for (int i = 0; i < disallowed.length; i++) {
            process = TestingProcessManager.start(args, false);
            FeatureFlagTestContent.getInstance(process.getProcess())
                    .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
            process.startProcess();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

            JsonObject config = new JsonObject();
            String property = disallowed[i];
            Object value = disallowedValues[i];

            if (value instanceof Integer) {
                config.addProperty(disallowed[i], (Integer) disallowedValues[i]);
            } else if (value instanceof String) {
                config.addProperty(disallowed[i], (String) disallowedValues[i]);
            } else if (value instanceof Boolean) {
                config.addProperty(disallowed[i], (Boolean) disallowedValues[i]);
            } else {
                throw new Exception("Unknown type");
            }

            try {
                Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                        new TenantIdentifier(null, "a1", null),
                        new EmailPasswordConfig(true),
                        new ThirdPartyConfig(true, null),
                        new PasswordlessConfig(true),
                        null, null, config
                ), false);
                fail();
            } catch (InvalidConfigException e) {
                assertTrue(e.getMessage().contains(property));
            }

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }

        String[] conflictingInSameUserPool = new String[]{
                "access_token_validity",
                "access_token_blacklisting",
                "refresh_token_validity",
                "access_token_signing_key_dynamic",
                "access_token_dynamic_signing_key_update_interval",
                "api_keys",
                "disable_telemetry",
                "password_hashing_alg",
                "firebase_password_hashing_signer_key",
                "supertokens_max_cdi_version",
        };
        Object[][] conflictingValues = new Object[][]{
                new Object[]{3600, 3601}, // access_token_validity
                new Object[]{true, false}, // access_token_blacklisting
                new Object[]{60 * 2400, 61 * 2400}, // refresh_token_validity
                new Object[]{true, false}, // access_token_signing_key_dynamic
                new Object[]{168, 169}, // access_token_dynamic_signing_key_update_interval
                new Object[]{"abcd1234abcd1234abcd1234abcd1234", "qwer1234qwer1234qwer1234qwer1234"}, // api_keys
                new Object[]{true, false}, // disable_telemetry
                new Object[]{"BCRYPT", "ARGON2"}, // password_hashing_alg
                new Object[]{"abcd1234abcd1234abcd1234abcd1234", "qwer1234qwer1234qwer1234qwer1234"},
                // firebase_password_hashing_signer_key
                new Object[]{"2.21", "3.0"}, // supertokens_max_cdi_version
        };

        for (int i = 0; i < conflictingInSameUserPool.length; i++) {
            process = TestingProcessManager.start(args, false);
            FeatureFlagTestContent.getInstance(process.getProcess())
                    .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
            process.startProcess();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

            JsonObject config = new JsonObject();
            String property = conflictingInSameUserPool[i];
            Object[] values = conflictingValues[i];


            if (values[0] instanceof Integer) {
                config.addProperty(conflictingInSameUserPool[i], (Integer) values[0]);
            } else if (values[0] instanceof String) {
                config.addProperty(conflictingInSameUserPool[i], (String) values[0]);
            } else if (values[0] instanceof Boolean) {
                config.addProperty(conflictingInSameUserPool[i], (Boolean) values[0]);
            } else {
                throw new Exception("Unknown type");
            }

            Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                    new TenantIdentifier(null, "a1", null),
                    new EmailPasswordConfig(true),
                    new ThirdPartyConfig(true, null),
                    new PasswordlessConfig(true),
                    null, null, config
            ), false);

            JsonObject config2 = new JsonObject();

            if (values[1] instanceof Integer) {
                config2.addProperty(conflictingInSameUserPool[i], (Integer) values[1]);
            } else if (values[1] instanceof String) {
                config2.addProperty(conflictingInSameUserPool[i], (String) values[1]);
            } else if (values[1] instanceof Boolean) {
                config2.addProperty(conflictingInSameUserPool[i], (Boolean) values[1]);
            } else {
                throw new Exception("Unknown type");
            }

            try {
                Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                        new TenantIdentifier(null, "a1", "t1"),
                        new EmailPasswordConfig(true),
                        new ThirdPartyConfig(true, null),
                        new PasswordlessConfig(true),
                        null, null, config2
                ), false);
                fail();
            } catch (InvalidConfigException e) {
                assertTrue(e.getMessage().contains(property));
                assertTrue(e.getMessage().contains("same appId"));
            }

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }
    }

    @Test
    public void testAllConfigFieldsAreAnnotated() throws Exception {
        for (Field field : CoreConfig.class.getDeclaredFields()) {
            if (field.isAnnotationPresent(IgnoreForAnnotationCheck.class)) {
                continue;
            }

            if (!(field.isAnnotationPresent(ConfigYamlOnly.class) ||
                    field.isAnnotationPresent(NotConflictingInApp.class))) {
                fail(field.getName() + " does not have ConfigYamlOnly or NotConflictingInApp annotation");
            }
        }
    }
}
