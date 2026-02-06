/*
 *    Copyright (c) 2024, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.test.oauth.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlag;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.totp.TotpLicenseTest;
import io.supertokens.utils.SemVer;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.HashMap;

public class TestClientList5_2 {
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
        OAuthAPIHelper.resetOAuthProvider();
    }

    @Test
    public void testClientList() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.OAUTH});

        Set<String> clientIds = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            JsonObject client = createClient(process.getProcess(), new JsonObject());
            clientIds.add(client.get("clientId").getAsString());
        }

        JsonObject response = listClients(process.getProcess(), new HashMap<>());
        assertEquals(10, response.get("clients").getAsJsonArray().size());

        Set<String> clientIdsFromResponse = new HashSet<>();
        for (JsonElement client : response.get("clients").getAsJsonArray()) {
            clientIdsFromResponse.add(client.getAsJsonObject().get("clientId").getAsString());
        }
        assertEquals(clientIds, clientIdsFromResponse);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testClientListWithPagination() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        FeatureFlag.getInstance(process.getProcess())
                .setLicenseKeyAndSyncFeatures(TotpLicenseTest.OPAQUE_KEY_WITH_MFA_FEATURE);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.OAUTH});

        Set<String> clientIds = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            JsonObject client = createClient(process.getProcess(), new JsonObject());
            if (!client.has("clientId")) {
                System.out.println(client);
            }
            clientIds.add(client.get("clientId").getAsString());
        }

        Map<String, String> queryParams = new HashMap<>();
        queryParams.put("pageSize", "10");

        Set<String> clientIdsFromResponse = new HashSet<>();

        JsonObject response = listClients(process.getProcess(), queryParams);
        assertEquals(10, response.get("clients").getAsJsonArray().size());

        for (JsonElement client : response.get("clients").getAsJsonArray()) {
            clientIdsFromResponse.add(client.getAsJsonObject().get("clientId").getAsString());
        }

        while (response.has("nextPaginationToken")) {
            queryParams.put("pageToken", response.get("nextPaginationToken").getAsString());

            response = listClients(process.getProcess(), queryParams);
            for (JsonElement client : response.get("clients").getAsJsonArray()) {
                clientIdsFromResponse.add(client.getAsJsonObject().get("clientId").getAsString());
            }    
        }

        assertEquals(clientIds, clientIdsFromResponse);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testClientListWithClientNameFilter() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        FeatureFlag.getInstance(process.getProcess())
                .setLicenseKeyAndSyncFeatures(TotpLicenseTest.OPAQUE_KEY_WITH_MFA_FEATURE);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.OAUTH});

        Set<String> clientIds = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            JsonObject clientBody = new JsonObject();
            clientBody.add("clientName", new JsonPrimitive("Hello"));
            JsonObject client = createClient(process.getProcess(), clientBody);
            if (!client.has("clientId")) {
                System.out.println(client);
            }
            clientIds.add(client.get("clientId").getAsString());
        }

        for (int i = 0; i < 100; i++) {
            JsonObject clientBody = new JsonObject();
            clientBody.add("clientName", new JsonPrimitive("World"));
            createClient(process.getProcess(), clientBody);
        }

        Map<String, String> queryParams = new HashMap<>();
        queryParams.put("pageSize", "10");
        queryParams.put("clientName", "Hello");

        Set<String> clientIdsFromResponse = new HashSet<>();

        JsonObject response = listClients(process.getProcess(), queryParams);
        assertEquals(10, response.get("clients").getAsJsonArray().size());

        for (JsonElement client : response.get("clients").getAsJsonArray()) {
            clientIdsFromResponse.add(client.getAsJsonObject().get("clientId").getAsString());
        }

        while (response.has("nextPaginationToken")) {
            queryParams.put("pageToken", response.get("nextPaginationToken").getAsString());

            response = listClients(process.getProcess(), queryParams);
            for (JsonElement client : response.get("clients").getAsJsonArray()) {
                clientIdsFromResponse.add(client.getAsJsonObject().get("clientId").getAsString());
            }    
        }

        assertEquals(clientIds, clientIdsFromResponse);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    private JsonObject createClient(Main main, JsonObject createClientBody) throws Exception {
        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                "http://localhost:3567/recipe/oauth/clients", createClientBody, 1000, 1000, null,
                SemVer.v5_2.get(), "");
        return response;
    }

    private JsonObject listClients(Main main, Map<String, String> queryParams) throws Exception {
        JsonObject response = HttpRequestForTesting.sendGETRequest(main, "",
                "http://localhost:3567/recipe/oauth/clients/list", queryParams, 1000, 1000, null,
                SemVer.v5_2.get(), "");
        return response;
    }
}
