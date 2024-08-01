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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.supertokens.ProcessState;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.oauth.sqlStorage.OAuthSQLStorage;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.httpRequest.HttpResponseException;
import org.junit.*;
import org.junit.rules.TestRule;

import java.io.IOException;

import static org.junit.Assert.*;
import static org.junit.Assert.assertTrue;

public class OAuthRegisterClientAPITest {
    TestingProcessManager.TestingProcess process;

    @Rule
    public TestRule watchman = Utils.getOnFailure();

    @AfterClass
    public static void afterTesting() {
        Utils.afterTesting();
    }

    @Before
    public void beforeEach() throws InterruptedException {
        Utils.reset();
    }

    @Test
    public void testClientRegisteredForApp()
            throws HttpResponseException, IOException, StorageQueryException, InterruptedException {

        String[] args = {"../"};
        this.process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String clientName = "jozef";
        String scope = "profile";

        OAuthSQLStorage oAuthStorage = (OAuthSQLStorage) StorageLayer.getStorage(process.getProcess());


        {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("clientName", clientName);
            requestBody.addProperty("scope", scope);

            JsonArray grantTypes = new JsonArray();
            grantTypes.add(new JsonPrimitive("refresh_token"));
            grantTypes.add(new JsonPrimitive("authorization_code"));
            requestBody.add("grantTypes", grantTypes);

            JsonArray responseTypes = new JsonArray();
            responseTypes.add(new JsonPrimitive("code"));
            responseTypes.add(new JsonPrimitive("id_token"));
            requestBody.add("responseTypes", responseTypes);

            JsonObject actualResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/oauth/registerclient", requestBody, 1000, 1000, null,
                    null, RECIPE_ID.OAUTH.toString());

            assertTrue(actualResponse.has("clientSecret"));
            assertTrue(actualResponse.has("clientId"));

            String clientId = actualResponse.get("clientId").getAsString();

            boolean isClientAlreadyExists = oAuthStorage.isClientIdAlreadyExists(clientId);
            assertTrue(isClientAlreadyExists);

            boolean clientShouldntExists = oAuthStorage.isClientIdAlreadyExists(clientId + "someRandomStringHere");
            assertFalse(clientShouldntExists);

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }
    }

    @Test
    public void testMissingRequiredField_throwsException() throws  InterruptedException {

        String[] args = {"../"};
        this.process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String clientName = "jozef";
        //notice missing 'scope' field!


        {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("clientName", clientName);
            //notice missing 'scope' field

            JsonArray grantTypes = new JsonArray();
            grantTypes.add(new JsonPrimitive("refresh_token"));
            grantTypes.add(new JsonPrimitive("authorization_code"));
            requestBody.add("grantTypes", grantTypes);

            JsonArray responseTypes = new JsonArray();
            responseTypes.add(new JsonPrimitive("code"));
            responseTypes.add(new JsonPrimitive("id_token"));
            requestBody.add("responseTypes", responseTypes);

            io.supertokens.test.httpRequest.HttpResponseException expected = assertThrows(io.supertokens.test.httpRequest.HttpResponseException.class, () -> {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/oauth/registerclient", requestBody, 1000, 1000, null,
                null, RECIPE_ID.OAUTH.toString());
            });

            assertEquals(400, expected.statusCode);
            assertEquals("Http error. Status Code: 400. Message: Invalid Json Input", expected.getMessage());

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }
    }

    @Test
    public void testMoreFieldThanAllowed_throwsException()
            throws InterruptedException {

        String[] args = {"../"};
        this.process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String clientName = "jozef";
        String scope = "scope";
        String maliciousAttempt = "giveMeAllYourBelongings!"; //here!

        {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("clientName", clientName);
            requestBody.addProperty("scope", scope);
            requestBody.addProperty("dontMindMe", maliciousAttempt); //here!

            JsonArray grantTypes = new JsonArray();
            grantTypes.add(new JsonPrimitive("refresh_token"));
            grantTypes.add(new JsonPrimitive("authorization_code"));
            requestBody.add("grantTypes", grantTypes);

            JsonArray responseTypes = new JsonArray();
            responseTypes.add(new JsonPrimitive("code"));
            responseTypes.add(new JsonPrimitive("id_token"));
            requestBody.add("responseTypes", responseTypes);

            io.supertokens.test.httpRequest.HttpResponseException expected = assertThrows(io.supertokens.test.httpRequest.HttpResponseException.class, () -> {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/oauth/registerclient", requestBody, 1000, 1000, null,
                        null, RECIPE_ID.OAUTH.toString());
            });

            assertEquals(400, expected.statusCode);
            assertEquals("Http error. Status Code: 400. Message: Invalid Json Input", expected.getMessage());

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }
    }
}
