/*
 *    Copyright (c) 2020, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.cliOptions.CLIOptions;
import io.supertokens.config.Config;
import io.supertokens.config.CoreConfig;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.multitenancy.exception.CannotModifyBaseConfigException;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.*;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.httpRequest.HttpResponseException;
import io.supertokens.test.multitenant.api.TestMultitenancyAPIHelper;
import io.supertokens.thirdparty.InvalidProviderConfigException;
import io.supertokens.utils.SemVer;
import org.junit.*;
import org.junit.rules.TestRule;

import java.io.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.*;

public class SuperTokensSaaSSecretTest {

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

    // * - set API key and check that config.getAPIKeys() does not return null
    @Test
    public void testGetApiKeysDoesNotReturnNullWhenAPIKeyIsSet() throws Exception {
        String[] args = {"../"};

        Utils.setValueInConfig("supertokens_saas_secret",
                "abctijenbogweg=-2438243u98-abctijenbocdsfcegweg=-2438243u98ef23c"); // set api_keys
        Utils.setValueInConfig("api_keys", "adslfkj398erchpsodihfp3w9q8ehcpioh");

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String apiKey = Config.getConfig(process.getProcess()).getSuperTokensSaaSSecret();
        assertNotNull(apiKey);
        assertEquals(apiKey, "abctijenbogweg=-2438243u98-abctijenbocdsfcegweg=-2438243u98ef23c");

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // * - don't set API key and check that config.getAPIKeys() returns null
    @Test
    public void testGetApiKeysReturnsNullWhenAPIKeyIsNotSet() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        assertNull(Config.getConfig(process.getProcess()).getSuperTokensSaaSSecret());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    // * - set an invalid API key and check that an error is thrown.
    @Test
    public void testErrorIsThrownWhenInvalidApiKeyIsSet() throws Exception {
        String[] args = {"../"};

        // api key length less that minimum length 20
        Utils.setValueInConfig("supertokens_saas_secret", "abc"); // set api_keys

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        ProcessState.EventAndException event = process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.INIT_FAILURE);
        assertNotNull(event);
        assertEquals(event.exception.getCause().getMessage(),
                "supertokens_saas_secret can only be used when api_key is also defined");

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

        Utils.reset();

        // api key length less that minimum length 20
        Utils.setValueInConfig("supertokens_saas_secret", "abc"); // set api_keys
        Utils.setValueInConfig("api_keys", "adslfkj398erchpsodihfp3w9q8ehcpioh");

        process = TestingProcessManager.start(args);
        event = process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.INIT_FAILURE);
        assertNotNull(event);
        assertEquals(event.exception.getCause().getMessage(),
                "supertokens_saas_secret is too short. Please use at least 40 characters");

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

        Utils.reset();

        // setting api key with non-supported symbols
        Utils.setValueInConfig("supertokens_saas_secret",
                "abC&^0t4t3t40t4@#%greognradsfadsfiu3b8cuhbosjiadbfiiubio8"); // set api_keys
        Utils.setValueInConfig("api_keys", "adslfkj398erchpsodihfp3w9q8ehcpioh");
        process = TestingProcessManager.start(args);

        event = process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.INIT_FAILURE);
        assertNotNull(event);
        assertEquals(event.exception.getCause().getMessage(),
                "Invalid characters in supertokens_saas_secret key. Please only use '=', '-' and alpha-numeric " +
                        "(including" +
                        " capitals)");

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    // * - set one valid, and one invalid API key and check error is thrown
    @Test
    public void testSettingValidAndInvalidApiKeysAndErrorIsThrown() throws Exception {
        String[] args = {"../"};
        String validKey = "abdein30934=-";
        String invalidKey = "%93*4=JN39";

        Utils.setValueInConfig("supertokens_saas_secret", validKey + "," + invalidKey);
        Utils.setValueInConfig("api_keys", "adslfkj398erchpsodihfp3w9q8ehcpioh");

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        ProcessState.EventAndException event = process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.INIT_FAILURE);
        assertNotNull(event);
        assertEquals(event.exception.getCause().getMessage(),
                "supertokens_saas_secret is too short. Please use at least 40 characters");

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // * - set a valid API key (with small and capital letter, numbers, =, -) and check that creating a new session
    // * requires that key (send request without key and it should fail with 401 and proper message, and then send
    // * with key and it should succeed and then send with wrong key and check it fails).
    @Test
    public void testCreatingSessionWithAndWithoutAPIKey() throws Exception {
        String[] args = {"../"};

        String apiKey = "hg40239oirjgBHD9450=Beew123--hg40239oirjgBHD9450=Beew123--hg40239oirjgBHD9450=Beew123-";
        Utils.setValueInConfig("supertokens_saas_secret", apiKey); // set api_keys
        Utils.setValueInConfig("api_keys", "adslfkj398erchpsodihfp3w9q8ehcpioh");

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        JsonObject request = new JsonObject();
        request.addProperty("userId", userId);
        request.add("userDataInJWT", userDataInJWT);
        request.add("userDataInDatabase", userDataInDatabase);
        request.addProperty("enableAntiCsrf", false);

        try {
            HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/recipe/session",
                    request, 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), null);
            fail();
        } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
            assertTrue(e.statusCode == 401
                    && e.getMessage().equals("Http error. Status Code: 401. Message: Invalid API key"));
        }

        JsonObject sessionInfo = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session", request, 1000, 1000, null,
                SemVer.v3_0.get(),
                apiKey, "");
        assertEquals(sessionInfo.get("status").getAsString(), "OK");
        checkSessionResponse(sessionInfo, process, userId, userDataInJWT);

        try {
            HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/recipe/session",
                    request, 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), "abd#%034t0g4in40t40v0j");
            fail();
        } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
            assertTrue(e.statusCode == 401
                    && e.getMessage().equals("Http error. Status Code: 401. Message: Invalid API key"));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // * - set API key and check that you can still call /config and /hello without it
    @Test
    public void testSettingAPIKeyAndCallingConfigAndHelloWithoutIt() throws Exception {
        String[] args = {"../"};

        String apiKey = "hg40239oirjgBHD9450=Beew123-dsvfaihjbco3iucbs897dgv087dsgav08uagsd08";
        Utils.setValueInConfig("supertokens_saas_secret", apiKey); // set supertokens_saas_secret
        Utils.setValueInConfig("api_keys", "adslfkj398erchpsodihfp3w9q8ehcpioh");

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/hello", null, 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), "");
        assertEquals(response, "Hello");

        // map to store pid as parameter
        Map<String, String> map = new HashMap<>();
        map.put("pid", ProcessHandle.current().pid() + "");
        JsonObject response2 = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/config", map, 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), "");

        File f = new File(CLIOptions.get(process.getProcess()).getInstallationPath() + "config.yaml");
        String path = f.getAbsolutePath();

        assertEquals(response2.get("status").getAsString(), "OK");
        assertEquals(response2.get("path").getAsString(), path);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testCreatingSessionWithAndWithoutAPIKeyWhenSuperTokensSaaSSecretIsAlsoSet() throws Exception {
        String[] args = {"../"};

        String saasSecret = "hg40239oirjgBHD9450=Beew123--hg40239oirjgBHD9450=Beew123--hg40239oirjgBHD9450=Beew123-";
        Utils.setValueInConfig("supertokens_saas_secret", saasSecret);

        String apiKey = "hg40239oirjgBHD9450=Beew123--hg40239oir";
        Utils.setValueInConfig("api_keys", apiKey);

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        JsonObject request = new JsonObject();
        request.addProperty("userId", userId);
        request.add("userDataInJWT", userDataInJWT);
        request.add("userDataInDatabase", userDataInDatabase);
        request.addProperty("enableAntiCsrf", false);

        try {
            HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/recipe/session",
                    request, 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), null);
            fail();
        } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
            assertTrue(e.statusCode == 401
                    && e.getMessage().equals("Http error. Status Code: 401. Message: Invalid API key"));
        }

        {
            JsonObject sessionInfo = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/session", request, 1000, 1000, null,
                    SemVer.v3_0.get(),
                    apiKey, "");
            assertEquals(sessionInfo.get("status").getAsString(), "OK");
            checkSessionResponse(sessionInfo, process, userId, userDataInJWT);
        }

        {
            JsonObject sessionInfo = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/session", request, 1000, 1000, null,
                    SemVer.v3_0.get(),
                    saasSecret, "");
            assertEquals(sessionInfo.get("status").getAsString(), "OK");
            checkSessionResponse(sessionInfo, process, userId, userDataInJWT);
        }

        try {
            HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/recipe/session",
                    request, 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), "abd#%034t0g4in40t40v0j");
            fail();
        } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
            assertTrue(e.statusCode == 401
                    && e.getMessage().equals("Http error. Status Code: 401. Message: Invalid API key"));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void gettingTenantShouldNotExposeSuperTokensSaaSSecret()
            throws InterruptedException, IOException, InvalidConfigException, TenantOrAppNotFoundException,
            InvalidProviderConfigException, StorageQueryException,
            FeatureNotEnabledException, CannotModifyBaseConfigException, BadPermissionException {
        String[] args = {"../"};

        String saasSecret = "hg40239oirjgBHD9450=Beew123--hg40239oirjgBHD9450=Beew123--hg40239oirjgBHD9450=Beew123-";
        Utils.setValueInConfig("supertokens_saas_secret", saasSecret);
        Utils.setValueInConfig("refresh_token_validity", "144001");
        Utils.setValueInConfig("access_token_signing_key_dynamic", "false");
        Utils.setValueInConfig("api_keys", "adslfkj398erchpsodihfp3w9q8ehcpioh");
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        Multitenancy.addNewOrUpdateAppOrTenant(process.main, new TenantIdentifier(null, null, null),
                new TenantConfig(new TenantIdentifier(null, null, "t1"), new EmailPasswordConfig(false),
                        new ThirdPartyConfig(false, new ThirdPartyConfig.Provider[0]),
                        new PasswordlessConfig(false),
                        null, null, new JsonObject()));

        TenantConfig[] tenantConfigs = Multitenancy.getAllTenants(process.main);

        assertEquals(tenantConfigs.length, 2);
        assertEquals(tenantConfigs[0].tenantIdentifier, new TenantIdentifier(null, null, null));
        assertFalse(tenantConfigs[0].coreConfig.has("supertokens_saas_secret"));

        assertEquals(tenantConfigs[1].tenantIdentifier, new TenantIdentifier(null, null, "t1"));
        assertFalse(tenantConfigs[1].coreConfig.has("supertokens_saas_secret"));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testThatTenantCannotSetSuperTokensSaasSecret()
            throws InterruptedException, IOException, InvalidConfigException, TenantOrAppNotFoundException,
            InvalidProviderConfigException, StorageQueryException,
            FeatureNotEnabledException, CannotModifyBaseConfigException, BadPermissionException {
        String[] args = {"../"};

        String saasSecret = "hg40239oirjgBHD9450=Beew123--hg40239oirjgBHD9450=Beew123--hg40239oirjgBHD9450=Beew123-";
        Utils.setValueInConfig("supertokens_saas_secret", saasSecret);
        Utils.setValueInConfig("api_keys", "adslfkj398erchpsodihfp3w9q8ehcpioh");
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        try {
            JsonObject j = new JsonObject();
            j.addProperty("supertokens_saas_secret", saasSecret);
            Multitenancy.addNewOrUpdateAppOrTenant(process.main, new TenantIdentifier(null, null, null),
                    new TenantConfig(new TenantIdentifier(null, null, "t1"), new EmailPasswordConfig(false),
                            new ThirdPartyConfig(false, new ThirdPartyConfig.Provider[0]),
                            new PasswordlessConfig(false),
                            null, null, j));
            fail();
        } catch (InvalidConfigException e) {
            assertEquals(e.getMessage(), "supertokens_saas_secret can only be set via the core's base config setting");
        }
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    public static void checkSessionResponse(JsonObject response, TestingProcessManager.TestingProcess process,
                                            String userId, JsonObject userDataInJWT) {
        assertNotNull(response.get("session").getAsJsonObject().get("handle").getAsString());
        assertEquals(response.get("session").getAsJsonObject().get("userId").getAsString(), userId);
        assertEquals(response.get("session").getAsJsonObject().get("userDataInJWT").getAsJsonObject().toString(),
                userDataInJWT.toString());
        assertEquals(response.get("session").getAsJsonObject().entrySet().size(), 4);

        assertTrue(response.get("accessToken").getAsJsonObject().has("token"));
        assertTrue(response.get("accessToken").getAsJsonObject().has("expiry"));
        assertTrue(response.get("accessToken").getAsJsonObject().has("createdTime"));
        assertEquals(response.get("accessToken").getAsJsonObject().entrySet().size(), 3);

        assertTrue(response.get("refreshToken").getAsJsonObject().has("token"));
        assertTrue(response.get("refreshToken").getAsJsonObject().has("expiry"));
        assertTrue(response.get("refreshToken").getAsJsonObject().has("createdTime"));
        assertEquals(response.get("refreshToken").getAsJsonObject().entrySet().size(), 3);
    }

    private static final String[] PROTECTED_CORE_CONFIG = new String[]{"ip_allow_regex", "ip_deny_regex"};
    private static final Object[] PROTECTED_CORE_CONFIG_VALUES = new String[]{
            "127\\\\.\\\\d+\\\\.\\\\d+\\\\.\\\\d+|::1|0:0:0:0:0:0:0:1", "192.0.0.1"};

    @Test
    public void testThatTenantCannotSetProtectedConfigIfSuperTokensSaaSSecretIsSet()
            throws InterruptedException, IOException, InvalidConfigException, TenantOrAppNotFoundException,
            InvalidProviderConfigException, StorageQueryException,
            FeatureNotEnabledException, CannotModifyBaseConfigException, HttpResponseException {
        String[] args = {"../"};

        String saasSecret = "hg40239oirjgBHD9450=Beew123--hg40239oirjgBHD9450=Beew123--hg40239oirjgBHD9450=Beew123-";
        Utils.setValueInConfig("supertokens_saas_secret", saasSecret);
        String apiKey = "hg40239oirjgBHD9450=Beew123--hg40239oiBeew123-";
        Utils.setValueInConfig("api_keys", apiKey);
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.isInMemDb(process.getProcess())) {
            return;
        }

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        for (int i = 0; i < PROTECTED_CORE_CONFIG.length; i++) {
            try {
                JsonObject j = new JsonObject();
                j.addProperty(PROTECTED_CORE_CONFIG[i], "");
                Multitenancy.addNewOrUpdateAppOrTenant(process.main,
                        new TenantConfig(new TenantIdentifier(null, null, "t1"), new EmailPasswordConfig(false),
                                new ThirdPartyConfig(false, new ThirdPartyConfig.Provider[0]),
                                new PasswordlessConfig(false),
                                null, null, j), true);
                fail();
            } catch (BadPermissionException e) {
                assertEquals(e.getMessage(), "Not allowed to modify protected configs.");
            }
        }

        try {
            JsonObject coreConfig = new JsonObject();
            StorageLayer.getBaseStorage(process.getProcess()).modifyConfigToAddANewUserPoolForTesting(coreConfig, 1);

            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("appId", "a1");
            requestBody.addProperty("emailPasswordEnabled", true);
            requestBody.addProperty("thirdPartyEnabled", true);
            requestBody.addProperty("passwordlessEnabled", true);
            requestBody.add("coreConfig", coreConfig);

            JsonObject response = HttpRequestForTesting.sendJsonRequest(process.getProcess(), "",
                    HttpRequestForTesting.getMultitenantUrl(TenantIdentifier.BASE_TENANT, "/recipe/multitenancy/app"),
                    requestBody, 1000, 2500, null,
                    SemVer.v3_0.get(), "PUT", apiKey, "multitenancy");

            Assert.assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
            fail();
        } catch (HttpResponseException e) {
            Assert.assertTrue(e.getMessage().contains("Not allowed to modify DB related configs."));
        }

        JsonObject coreConfig = new JsonObject();
        StorageLayer.getBaseStorage(process.getProcess()).modifyConfigToAddANewUserPoolForTesting(coreConfig, 1);

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("appId", "a1");
        requestBody.addProperty("emailPasswordEnabled", true);
        requestBody.addProperty("thirdPartyEnabled", true);
        requestBody.addProperty("passwordlessEnabled", true);
        requestBody.add("coreConfig", coreConfig);

        JsonObject response = HttpRequestForTesting.sendJsonRequest(process.getProcess(), "",
                HttpRequestForTesting.getMultitenantUrl(TenantIdentifier.BASE_TENANT, "/recipe/multitenancy/app"),
                requestBody, 1000, 2500, null,
                SemVer.v3_0.get(), "PUT", saasSecret, "multitenancy");

        Assert.assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testThatTenantCannotGetProtectedConfigIfSuperTokensSaaSSecretIsSet()
            throws InterruptedException, IOException, InvalidConfigException, TenantOrAppNotFoundException,
            InvalidProviderConfigException, StorageQueryException,
            FeatureNotEnabledException, CannotModifyBaseConfigException, BadPermissionException, HttpResponseException {
        String[] args = {"../"};

        String saasSecret = "hg40239oirjgBHD9450=Beew123--hg40239oirjgBHD9450=Beew123--hg40239oirjgBHD9450=Beew123-";
        Utils.setValueInConfig("supertokens_saas_secret", saasSecret);
        String apiKey = "hg40239oirjgBHD9450=Beew123--hg40239oiBeew123-";
        Utils.setValueInConfig("api_keys", apiKey);
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.isInMemDb(process.getProcess())) {
            return;
        }

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        for (int i = 0; i < PROTECTED_CORE_CONFIG.length; i++) {
            JsonObject j = new JsonObject();
            if (PROTECTED_CORE_CONFIG_VALUES[i] instanceof String) {
                j.addProperty(PROTECTED_CORE_CONFIG[i], (String) PROTECTED_CORE_CONFIG_VALUES[i]);
            } else if (PROTECTED_CORE_CONFIG_VALUES[i] instanceof Integer) {
                j.addProperty(PROTECTED_CORE_CONFIG[i], (Integer) PROTECTED_CORE_CONFIG_VALUES[i]);
            }
            Multitenancy.addNewOrUpdateAppOrTenant(process.main, new TenantIdentifier(null, null, null),
                    new TenantConfig(new TenantIdentifier(null, null, "t" + i), new EmailPasswordConfig(false),
                            new ThirdPartyConfig(false, new ThirdPartyConfig.Provider[0]),
                            new PasswordlessConfig(false),
                            null, null, j));

            {
                JsonObject response = HttpRequestForTesting.sendJsonRequest(process.getProcess(), "",
                        HttpRequestForTesting.getMultitenantUrl(TenantIdentifier.BASE_TENANT,
                                "/recipe/multitenancy/tenant/list"),
                        null, 1000, 1000, null,
                        SemVer.v3_0.get(), "GET", apiKey, "multitenancy");

                Assert.assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());

                boolean found = false;
                for (JsonElement tenant : response.get("tenants").getAsJsonArray()) {
                    JsonObject tenantObj = tenant.getAsJsonObject();

                    if (tenantObj.get("tenantId").getAsString().equals("t" + i)) {
                        found = true;

                        assertFalse(tenantObj.get("coreConfig").getAsJsonObject().has(PROTECTED_CORE_CONFIG[i]));
                    }
                }
                Assert.assertTrue(found);
            }

            {
                JsonObject response = HttpRequestForTesting.sendJsonRequest(process.getProcess(), "",
                        HttpRequestForTesting.getMultitenantUrl(TenantIdentifier.BASE_TENANT,
                                "/recipe/multitenancy/tenant/list"),
                        null, 1000, 1000, null,
                        SemVer.v3_0.get(), "GET", saasSecret, "multitenancy");

                Assert.assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());

                boolean found = false;
                for (JsonElement tenant : response.get("tenants").getAsJsonArray()) {
                    JsonObject tenantObj = tenant.getAsJsonObject();

                    if (tenantObj.get("tenantId").getAsString().equals("t" + i)) {
                        found = true;

                        Assert.assertTrue(tenantObj.get("coreConfig").getAsJsonObject().has(PROTECTED_CORE_CONFIG[i]));
                    }
                }
                Assert.assertTrue(found);
            }
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testLogContainsCorrectCud() throws Exception {
        String[] args = {"../"};

        String saasSecret = "hg40239oirjgBHD9450=Beew123--hg40239oirjgBHD9450=Beew123--hg40239oirjgBHD9450=Beew123-";
        Utils.setValueInConfig("supertokens_saas_secret", saasSecret);
        String apiKey = "hg40239oirjgBHD9450=Beew123--hg40239oiBeew123-";
        Utils.setValueInConfig("api_keys", apiKey);
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.isInMemDb(process.getProcess())) {
            return;
        }

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        TenantIdentifier cud = new TenantIdentifier("127.0.0.1", null, null);
        JsonObject coreConfig = new JsonObject();
        StorageLayer.getStorage(process.getProcess()).modifyConfigToAddANewUserPoolForTesting(coreConfig, 1);
        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                cud,
                new EmailPasswordConfig(true),
                new ThirdPartyConfig(true, new ThirdPartyConfig.Provider[0]),
                new PasswordlessConfig(true),
                null, null, coreConfig
        ), false);

        { // clear the logs
            FileWriter f = new FileWriter(Config.getConfig(process.getProcess()).getInfoLogPath(process.getProcess()));
            f.flush();
            f.close();
        }

        try {
            TestMultitenancyAPIHelper.epSignUp(cud, "test@example.com", "password", process.getProcess());
            fail();
        } catch (HttpResponseException e) {
            // API key should have failed
            assertTrue(e.getMessage().contains("Invalid API key"));
        }

        try (BufferedReader reader = new BufferedReader(
                new FileReader(Config.getConfig(process.getProcess()).getInfoLogPath(process.getProcess())))) {
            String currentReadingLine = reader.readLine();
            boolean found = false;
            while (currentReadingLine != null) {
                if (currentReadingLine.contains("/recipe/signup")) {
                    found = true;
                    assertTrue(currentReadingLine.contains("Tenant(127.0.0.1, public, public)"));
                }

                currentReadingLine = reader.readLine();
            }
            assertTrue(found);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testThatAllProtectedPropsAreTested() throws Exception {
        for (String protectedConfig : CoreConfig.PROTECTED_CONFIGS) {
            assertTrue(Arrays.asList(PROTECTED_CORE_CONFIG).contains(protectedConfig));
        }
    }
}
