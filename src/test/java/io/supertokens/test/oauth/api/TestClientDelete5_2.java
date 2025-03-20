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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class TestClientDelete5_2 {
    @Rule
    public TestRule watchman = Utils.getOnFailure();

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
    public void testClientDelete() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.restart(args, false);
        Utils.setValueInConfig("oauth_provider_public_service_url", "http://localhost:4444");
        Utils.setValueInConfig("oauth_provider_admin_service_url", "http://localhost:4445");
        Utils.setValueInConfig("oauth_provider_consent_login_base_url", "http://localhost:3001/auth");
        Utils.setValueInConfig("oauth_client_secret_encryption_key", "secret");
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.CREATED_TEST_APP));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        FeatureFlagTestContent.getInstance(process.main)
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[] { EE_FEATURES.OAUTH });

        Set<String> clientIds = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            JsonObject client = createClient(process.getProcess());
            clientIds.add(client.get("clientId").getAsString());
        }

        String clientIdToDelete = clientIds.iterator().next();
        JsonObject resp = deleteClient(process.getProcess(), clientIdToDelete);
        assertEquals("OK", resp.get("status").getAsString());
        assertEquals(true, resp.get("didExist").getAsBoolean());

        JsonObject clients = listClients(process.getProcess());
        Set<String> clientIdsAfterDeletion = new HashSet<>();
        for (JsonElement client : clients.get("clients").getAsJsonArray()) {
            clientIdsAfterDeletion.add(client.getAsJsonObject().get("clientId").getAsString());
        }

        assertFalse(clientIdsAfterDeletion.contains(clientIdToDelete));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testClientDeleteWithInvalidClientId() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        Utils.setValueInConfig("oauth_provider_public_service_url", "http://localhost:4444");
        Utils.setValueInConfig("oauth_provider_admin_service_url", "http://localhost:4445");
        Utils.setValueInConfig("oauth_provider_consent_login_base_url", "http://localhost:3001/auth");
        Utils.setValueInConfig("oauth_client_secret_encryption_key", "secret");
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        FeatureFlagTestContent.getInstance(process.main)
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[] { EE_FEATURES.OAUTH });

        JsonObject resp = deleteClient(process.getProcess(), "non-existent-client-id");
        assertEquals("OK", resp.get("status").getAsString());
        assertEquals(false, resp.get("didExist").getAsBoolean());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    private JsonObject createClient(Main main) throws Exception {
        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                "http://localhost:3567/recipe/oauth/clients", new JsonObject(), 1000, 1000, null,
                SemVer.v5_2.get(), "");
        return response;
    }

    private JsonObject deleteClient(Main main, String clientId) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("clientId", clientId);
        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                "http://localhost:3567/recipe/oauth/clients/remove", body, 1000, 1000, null,
                SemVer.v5_2.get(), "");
        return response;
    }

    private JsonObject listClients(Main main) throws Exception {
        JsonObject response = HttpRequestForTesting.sendGETRequest(main, "",
                "http://localhost:3567/recipe/oauth/clients/list", new HashMap<>(), 1000, 1000, null,
                SemVer.v5_2.get(), "");
        return response;
    }
}
