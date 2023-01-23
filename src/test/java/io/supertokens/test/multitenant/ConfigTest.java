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
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.multitenancy.EmailPasswordConfig;
import io.supertokens.pluginInterface.multitenancy.PasswordlessConfig;
import io.supertokens.pluginInterface.multitenancy.TenantConfig;
import io.supertokens.pluginInterface.multitenancy.ThirdPartyConfig;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import org.junit.*;
import org.junit.rules.TestRule;

import java.io.File;
import java.io.IOException;

import static junit.framework.TestCase.assertEquals;
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
    public void mergingTenantWithBaseConfigWorks() throws InterruptedException, IOException, InvalidConfigException {
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

        Config.assertAllTenantConfigsAreValid(process.main,
                Config.loadAllTenantConfig(process.getProcess(), new TenantConfig[]{
                        new TenantConfig("abc", null, new EmailPasswordConfig(false),
                                new ThirdPartyConfig(false, new ThirdPartyConfig.Provider[0]),
                                new PasswordlessConfig(false),
                                tenantConfig)}));

        Assert.assertEquals(Config.getConfig(process.getProcess()).getRefreshTokenValidity(),
                (long) 144001 * 60 * 1000);
        Assert.assertEquals(Config.getConfig(process.getProcess()).getPasswordResetTokenLifetime(),
                3600000);
        Assert.assertEquals(Config.getConfig(process.getProcess()).getPasswordlessMaxCodeInputAttempts(),
                5);
        Assert.assertEquals(Config.getConfig(process.getProcess()).getAccessTokenSigningKeyDynamic(),
                false);

        Assert.assertEquals(Config.getConfig("abc", null, process.getProcess()).getRefreshTokenValidity(),
                (long) 144002 * 60 * 1000);
        Assert.assertEquals(Config.getConfig("abc", null, process.getProcess()).getPasswordResetTokenLifetime(),
                3600001);
        Assert.assertEquals(Config.getConfig("abc", null, process.getProcess()).getPasswordlessMaxCodeInputAttempts(),
                5);
        Assert.assertEquals(Config.getConfig("abc", null, process.getProcess()).getAccessTokenSigningKeyDynamic(),
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
            Config.assertAllTenantConfigsAreValid(process.main,
                    Config.loadAllTenantConfig(process.getProcess(), new TenantConfig[]{
                            new TenantConfig("abc", null, new EmailPasswordConfig(false),
                                    new ThirdPartyConfig(false, new ThirdPartyConfig.Provider[0]),
                                    new PasswordlessConfig(false),
                                    tenantConfig)}));
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
            Config.assertAllTenantConfigsAreValid(process.main,
                    Config.loadAllTenantConfig(process.getProcess(), new TenantConfig[]{
                            new TenantConfig("abc", null, new EmailPasswordConfig(false),
                                    new ThirdPartyConfig(false, new ThirdPartyConfig.Provider[0]),
                                    new PasswordlessConfig(false),
                                    tenantConfig)}));
            fail();
        } catch (InvalidConfigException e) {
            assert (e.getMessage()
                    .equals("You cannot set different values for access_token_signing_key_dynamic for the same user " +
                            "pool"));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
