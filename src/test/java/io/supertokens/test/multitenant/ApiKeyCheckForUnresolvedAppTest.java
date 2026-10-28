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
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.multitenancy.*;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.httpRequest.HttpResponseException;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * The API-key and IP checks must not be skipped when the app named in the request path does not resolve: they are
 * then enforced against the nearest loaded ancestor (the connection URI domain's public app, then the base config).
 */
public class ApiKeyCheckForUnresolvedAppTest {
    @Rule
    public TestRule watchman = Utils.getOnFailure();

    @Rule
    public TestRule retryFlaky = Utils.retryFlakyTest();

    private static final String BASE_API_KEY = "baseApiKeyForTesting-0123456789abcdef";
    private static final String CUD_API_KEY = "cudApiKeyForTesting-0123456789abcdef";

    @AfterClass
    public static void afterTesting() {
        Utils.afterTesting();
    }

    @Before
    public void beforeEach() {
        Utils.reset();
    }

    private static final int NO_ERROR = 200;

    private int getStatus(TestingProcessManager.TestingProcess process, String url, String apiKey)
            throws Exception {
        Map<String, String> headers = new HashMap<>();
        if (apiKey != null) {
            headers.put("api-key", apiKey);
        }
        try {
            HttpRequestForTesting.sendGETRequestWithHeaders(process.getProcess(), "", url, null, headers, 2000,
                    2000, null, Utils.getCdiVersionStringLatestForTests(), "");
            return NO_ERROR;
        } catch (HttpResponseException e) {
            return e.statusCode;
        }
    }

    private HttpResponseException getError(TestingProcessManager.TestingProcess process, String url, String apiKey)
            throws Exception {
        Map<String, String> headers = new HashMap<>();
        if (apiKey != null) {
            headers.put("api-key", apiKey);
        }
        try {
            HttpRequestForTesting.sendGETRequestWithHeaders(process.getProcess(), "", url, null, headers, 2000,
                    2000, null, Utils.getCdiVersionStringLatestForTests(), "");
            fail("expected " + url + " to fail");
            return null;
        } catch (HttpResponseException e) {
            return e;
        }
    }

    private int postStatus(TestingProcessManager.TestingProcess process, String url, String apiKey)
            throws Exception {
        try {
            HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "", url, new JsonObject(), 2000, 2000,
                    null, Utils.getCdiVersionStringLatestForTests(), apiKey, "");
            return NO_ERROR;
        } catch (HttpResponseException e) {
            return e.statusCode;
        }
    }

    @Test
    public void testUnknownAppIdReturnsSameStatusAsWrongKey() throws Exception {
        String[] args = {"../"};
        Utils.setValueInConfig("api_keys", BASE_API_KEY);
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // control: an existing app without a key
        HttpResponseException knownApp = getError(process, "http://localhost:3567/appid-public/users/count", null);
        assertEquals(401, knownApp.statusCode);

        // an unknown app gets exactly the same answer
        HttpResponseException unknownApp = getError(process, "http://localhost:3567/appid-ghost/users/count", null);
        assertEquals(knownApp.statusCode, unknownApp.statusCode);
        assertEquals(knownApp.getMessage(), unknownApp.getMessage());

        assertEquals(401, getStatus(process, "http://localhost:3567/appid-ghost/users/count",
                "wrongApiKeyForTesting-0123456789abcdef"));
        assertEquals(401, getStatus(process, "http://localhost:3567/appid-ghost/recipe/multitenancy/tenant/v2",
                null));
        assertEquals(401, getStatus(process, "http://localhost:3567/appid-ghost/ghosttenant/users/count", null));
        assertEquals(401, postStatus(process, "http://localhost:3567/appid-ghost/recipe/signin", null));
        assertEquals(401, postStatus(process, "http://localhost:3567/appid-ghost/recipe/session/refresh", null));

        // with a valid key the unknown app is still reported as not found
        HttpResponseException withKey = getError(process, "http://localhost:3567/appid-ghost/users/count",
                BASE_API_KEY);
        assertEquals(400, withKey.statusCode);
        assertTrue(withKey.getMessage().contains("AppId or tenantId not found"));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    private void createCudWithApp(TestingProcessManager.TestingProcess process, JsonObject extraCudConfig)
            throws Exception {
        JsonObject cudConfig = new JsonObject();
        cudConfig.add("api_keys", new JsonPrimitive(CUD_API_KEY));
        if (extraCudConfig != null) {
            for (Map.Entry<String, com.google.gson.JsonElement> e : extraCudConfig.entrySet()) {
                cudConfig.add(e.getKey(), e.getValue());
            }
        }
        StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                .modifyConfigToAddANewUserPoolForTesting(cudConfig, 2);

        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(),
                new TenantConfig(new TenantIdentifier("127.0.0.1", null, null), new EmailPasswordConfig(true),
                        new ThirdPartyConfig(true, null), new PasswordlessConfig(true),
                        null, null, cudConfig), false);
        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(),
                new TenantConfig(new TenantIdentifier("127.0.0.1", "a1", null), new EmailPasswordConfig(true),
                        new ThirdPartyConfig(true, null), new PasswordlessConfig(true),
                        null, null, new JsonObject()), false);
    }

    @Test
    public void testIssue113_apiKeyCheckFailsClosedOnUnknownApp() throws Exception {
        String[] args = {"../"};
        Utils.setValueInConfig("host", "\"0.0.0.0\"");
        Utils.setValueInConfig("api_keys", BASE_API_KEY);
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args, false);
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

        createCudWithApp(process, null);

        // control: the app on its own connection URI domain
        assertEquals(NO_ERROR, getStatus(process, "http://127.0.0.1:3567/appid-a1/users/count", CUD_API_KEY));
        assertEquals(401, getStatus(process, "http://127.0.0.1:3567/appid-a1/users/count", null));

        // The same app id under a connection URI domain where it does not exist (here: a host that is not a
        // known CUD, so the request resolves to the base CUD). This is also how a request is resolved if the
        // CUD is momentarily not found. The key check must not be skipped.
        assertEquals(401, getStatus(process, "http://localhost:3567/appid-a1/users/count", null));
        assertEquals(401, getStatus(process, "http://localhost:3567/appid-a1/users/count", CUD_API_KEY));
        assertEquals(401, postStatus(process, "http://localhost:3567/appid-a1/recipe/signin", null));
        assertEquals(400, getStatus(process, "http://localhost:3567/appid-a1/users/count", BASE_API_KEY));

        // An unknown app on a known CUD is held to that CUD's keys
        assertEquals(401, getStatus(process, "http://127.0.0.1:3567/appid-ghost/users/count", null));
        assertEquals(401, getStatus(process, "http://127.0.0.1:3567/appid-ghost/users/count", BASE_API_KEY));
        assertEquals(401, postStatus(process, "http://127.0.0.1:3567/appid-ghost/recipe/signin", null));
        assertEquals(400, getStatus(process, "http://127.0.0.1:3567/appid-ghost/users/count", CUD_API_KEY));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testUnknownAppIdIsSubjectToIpRulesOfConnectionUriDomain() throws Exception {
        String[] args = {"../"};
        Utils.setValueInConfig("host", "\"0.0.0.0\"");
        Utils.setValueInConfig("api_keys", BASE_API_KEY);
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args, false);
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

        JsonObject ipConfig = new JsonObject();
        ipConfig.addProperty("ip_deny_regex", ".*");
        createCudWithApp(process, ipConfig);

        // control: the CUD's apps are subject to its IP rules
        assertEquals(403, getStatus(process, "http://127.0.0.1:3567/appid-a1/users/count", CUD_API_KEY));
        // an unknown app on that CUD is subject to them as well
        assertEquals(403, getStatus(process, "http://127.0.0.1:3567/appid-ghost/users/count", CUD_API_KEY));
        assertEquals(403, getStatus(process, "http://127.0.0.1:3567/appid-ghost/users/count", null));
        // the base CUD has no IP rules
        assertEquals(NO_ERROR, getStatus(process, "http://localhost:3567/users/count", BASE_API_KEY));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
