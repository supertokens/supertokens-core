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

package io.supertokens.test.oauth.api;

import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.httpRequest.HttpResponseException;
import io.supertokens.utils.SemVer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.*;

/**
 * Malformed input to the OAuth token API must result in a 400, not a 500. None of these requests
 * reach the OAuth provider, so no provider setup is needed.
 */
public class TestMalformedInput5_2 {
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

    @Test
    public void testTokenAPIMalformedInputReturns400() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[] { EE_FEATURES.OAUTH });

        { // non-primitive value in the SDK body used to surface as a 500 from getAsString
            JsonObject inputBody = new JsonObject();
            inputBody.addProperty("grant_type", "client_credentials");
            inputBody.add("scope", new JsonObject());

            JsonObject body = new JsonObject();
            body.addProperty("iss", "http://localhost:3567/auth");
            body.add("inputBody", inputBody);
            body.add("access_token", new JsonObject());
            body.add("id_token", new JsonObject());

            HttpResponseException e = assertThrows(HttpResponseException.class, () -> {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/oauth/token", body, 1000, 1000, null,
                        SemVer.v5_2.get(), "");
            });
            assertEquals(400, e.statusCode);
            assertEquals("Http error. Status Code: 400. Message: malformed request body or authorization header",
                    e.getMessage());
        }

        { // malformed basic auth header used to surface as a 500 from the base64 decode
            JsonObject inputBody = new JsonObject();
            inputBody.addProperty("grant_type", "client_credentials");

            JsonObject body = new JsonObject();
            body.addProperty("iss", "http://localhost:3567/auth");
            body.add("inputBody", inputBody);
            body.add("access_token", new JsonObject());
            body.add("id_token", new JsonObject());
            body.addProperty("authorizationHeader", "Basic !!!not-base64!!!");

            HttpResponseException e = assertThrows(HttpResponseException.class, () -> {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/oauth/token", body, 1000, 1000, null,
                        SemVer.v5_2.get(), "");
            });
            assertEquals(400, e.statusCode);
            assertEquals("Http error. Status Code: 400. Message: malformed request body or authorization header",
                    e.getMessage());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

}
