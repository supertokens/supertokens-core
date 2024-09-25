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

import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.httpRequest.HttpResponseException;
import io.supertokens.oauth.OAuth;
import io.supertokens.oauth.exceptions.OAuthAPIException;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.oauth.OAuthAuthResponse;
import io.supertokens.pluginInterface.oauth.sqlStorage.OAuthSQLStorage;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import org.junit.*;
import org.junit.rules.TestRule;

import java.io.IOException;

import static org.junit.Assert.*;

public class OAuthAuthAPITest {
//    TestingProcessManager.TestingProcess process;
//
//    @Rule
//    public TestRule watchman = Utils.getOnFailure();
//
//    @AfterClass
//    public static void afterTesting() {
//        Utils.afterTesting();
//    }
//
//    @Before
//    public void beforeEach() throws InterruptedException {
//        Utils.reset();
//    }
//
//
//    @Test
//    public void testLocalhostChangedToApiDomain()
//            throws StorageQueryException, OAuthAPIException, HttpResponseException, TenantOrAppNotFoundException,
//            InvalidConfigException, IOException, OAuth2ClientAlreadyExistsForAppException,
//            io.supertokens.test.httpRequest.HttpResponseException, InterruptedException {
//
//        String[] args = {"../"};
//
//        this.process = TestingProcessManager.start(args);
//        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
//
//        String clientId = "6030f07e-c8ef-4289-80c9-c18e0bf4f679";
//        String redirectUri = "http://localhost.com:3031/auth/callback/ory";
//        String responseType = "code";
//        String scope = "profile";
//        String state = "%EF%BF%BD%EF%BF%BD%EF%BF%BD%EF%BF%BD%EF%BF%BD%EF%BF%BDv%EF%BF%BD%EF%BF%BD%EF%BF%BD%EF%BF%BD%EF%BF%BD";
//
//        OAuthSQLStorage oAuthStorage = (OAuthSQLStorage) StorageLayer.getStorage(process.getProcess());
//
//        AppIdentifier testApp = new AppIdentifier("", "");
//        oAuthStorage.addClientForApp(testApp, clientId);
//
//        JsonObject requestBody = new JsonObject();
//        requestBody.addProperty("clientId", clientId);
//        requestBody.addProperty("redirectUri", redirectUri);
//        requestBody.addProperty("responseType", responseType);
//        requestBody.addProperty("scope", scope);
//        requestBody.addProperty("state", state);
//
//        OAuthAuthResponse response = OAuth.getAuthorizationUrl(process.getProcess(), new AppIdentifier("", ""),
//                oAuthStorage, requestBody);
//
//        assertNotNull(response);
//        assertNotNull(response.redirectTo);
//        assertNotNull(response.cookies);
//
//        assertTrue(response.redirectTo.startsWith("{apiDomain}/login?login_challenge="));
//        assertTrue(response.cookies.get(0).startsWith("ory_hydra_login_csrf_dev_134972871="));
//
//
//
//        {
//            JsonObject actualResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
//                    "http://localhost:3567/recipe/oauth/auth", requestBody, 1000, 1000, null,
//                    null, RECIPE_ID.OAUTH.toString());
//
//            assertEquals("OK", actualResponse.get("status").getAsString());
//            assertTrue(actualResponse.has("redirectTo"));
//            assertTrue(actualResponse.has("cookies"));
//            assertTrue(actualResponse.get("redirectTo").getAsString().startsWith("{apiDomain}/login?login_challenge="));
//            assertEquals(1, actualResponse.getAsJsonArray("cookies").size());
//            assertTrue(actualResponse.getAsJsonArray("cookies").get(0).getAsString().startsWith("ory_hydra_login_csrf_dev_134972871="));
//        }
//
//        process.kill();
//        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
//    }
//
//    @Test
//    public void testCalledWithWrongClientIdNotInST_exceptionThrown()
//            throws StorageQueryException, OAuth2ClientAlreadyExistsForAppException, IOException,
//            io.supertokens.test.httpRequest.HttpResponseException, InterruptedException {
//
//
//        String[] args = {"../"};
//
//        this.process = TestingProcessManager.start(args);
//        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
//
//        String clientId = "Not-Existing-In-Client-App-Table";
//        String redirectUri = "http://localhost.com:3031/auth/callback/ory";
//        String responseType = "code";
//        String scope = "profile";
//        String state = "%EF%BF%BD%EF%BF%BD%EF%BF%BD%EF%BF%BD%EF%BF%BD%EF%BF%BDv%EF%BF%BD%EF%BF%BD%EF%BF%BD%EF%BF%BD%EF%BF%BD";
//
//        JsonObject requestBody = new JsonObject();
//        requestBody.addProperty("clientId", clientId);
//        requestBody.addProperty("redirectUri", redirectUri);
//        requestBody.addProperty("responseType", responseType);
//        requestBody.addProperty("scope", scope);
//        requestBody.addProperty("state", state);
//
//        OAuthSQLStorage oAuthStorage = (OAuthSQLStorage) StorageLayer.getStorage(process.getProcess());
//
//        AppIdentifier testApp = new AppIdentifier("", "");
//        oAuthStorage.addClientForApp(testApp, clientId);
//
//        OAuthAPIException thrown = assertThrows(OAuthAPIException.class, () -> {
//
//            OAuthAuthResponse response = OAuth.getAuthorizationUrl(process.getProcess(), new AppIdentifier("", ""),
//                    oAuthStorage,  requestBody);
//        });
//
//        String expectedError = "invalid_client";
//        String expectedDescription = "Client authentication failed (e.g., unknown client, no client authentication included, or unsupported authentication method). The requested OAuth 2.0 Client does not exist.";
//
//        assertEquals(expectedError, thrown.error);
//        assertEquals(expectedDescription, thrown.errorDescription);
//
//        {
//            JsonObject actualResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
//                "http://localhost:3567/recipe/oauth/auth", requestBody, 1000, 1000, null,
//                null, RECIPE_ID.OAUTH.toString());
//
//            assertEquals("OAUTH2_AUTH_ERROR", actualResponse.get("status").getAsString());
//            assertTrue(actualResponse.has("error"));
//            assertTrue(actualResponse.has("errorDescription"));
//            assertEquals(expectedError,actualResponse.get("error").getAsString());
//            assertEquals(expectedDescription, actualResponse.get("errorDescription").getAsString());
//        }
//
//        process.kill();
//        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
//    }
//
//    @Test
//    public void testCalledWithWrongClientIdNotInHydraButInST_exceptionThrown()
//            throws StorageQueryException, OAuth2ClientAlreadyExistsForAppException,
//            io.supertokens.test.httpRequest.HttpResponseException, IOException, InterruptedException {
//
//
//        String[] args = {"../"};
//
//        this.process = TestingProcessManager.start(args);
//        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
//
//        String clientId = "6030f07e-c8ef-4289-80c9-c18e0bf4f679NotInHydra";
//        String redirectUri = "http://localhost.com:3031/auth/callback/ory";
//        String responseType = "code";
//        String scope = "profile";
//        String state = "%EF%BF%BD%EF%BF%BD%EF%BF%BD%EF%BF%BD%EF%BF%BD%EF%BF%BDv%EF%BF%BD%EF%BF%BD%EF%BF%BD%EF%BF%BD%EF%BF%BD";
//
//        OAuthSQLStorage oAuthStorage = (OAuthSQLStorage) StorageLayer.getStorage(process.getProcess());
//
//        JsonObject requestBody = new JsonObject();
//        requestBody.addProperty("clientId", clientId);
//        requestBody.addProperty("redirectUri", redirectUri);
//        requestBody.addProperty("responseType", responseType);
//        requestBody.addProperty("scope", scope);
//        requestBody.addProperty("state", state);
//
//        AppIdentifier testApp = new AppIdentifier("", "");
//        oAuthStorage.addClientForApp(testApp, clientId);
//
//        OAuthAPIException thrown = assertThrows(OAuthAPIException.class, () -> {
//
//            OAuthAuthResponse response = OAuth.getAuthorizationUrl(process.getProcess(), new AppIdentifier("", ""),
//                    oAuthStorage,  requestBody);
//        });
//
//        String expectedError = "invalid_client";
//        String expectedDescription = "Client authentication failed (e.g., unknown client, no client authentication included, or unsupported authentication method). The requested OAuth 2.0 Client does not exist.";
//
//        assertEquals(expectedError, thrown.error);
//        assertEquals(expectedDescription, thrown.errorDescription);
//
//        {
//            JsonObject actualResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
//                    "http://localhost:3567/recipe/oauth/auth", requestBody, 1000, 1000, null,
//                    null, RECIPE_ID.OAUTH.toString());
//
//            assertEquals("OAUTH2_AUTH_ERROR", actualResponse.get("status").getAsString());
//            assertTrue(actualResponse.has("error"));
//            assertTrue(actualResponse.has("errorDescription"));
//            assertEquals(expectedError,actualResponse.get("error").getAsString());
//            assertEquals(expectedDescription, actualResponse.get("errorDescription").getAsString());
//
//        }
//
//        process.kill();
//        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
//    }
//
//    @Test
//    public void testCalledWithWrongRedirectUrl_exceptionThrown()
//            throws StorageQueryException, OAuth2ClientAlreadyExistsForAppException,
//            io.supertokens.test.httpRequest.HttpResponseException, IOException, InterruptedException {
//
//
//        String[] args = {"../"};
//
//        this.process = TestingProcessManager.start(args);
//        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
//
//        String clientId = "6030f07e-c8ef-4289-80c9-c18e0bf4f679";
//        String redirectUri = "http://localhost.com:3031/auth/callback/ory_not_the_registered_one";
//        String responseType = "code";
//        String scope = "profile";
//        String state = "%EF%BF%BD%EF%BF%BD%EF%BF%BD%EF%BF%BD%EF%BF%BD%EF%BF%BDv%EF%BF%BD%EF%BF%BD%EF%BF%BD%EF%BF%BD%EF%BF%BD";
//
//        JsonObject requestBody = new JsonObject();
//        requestBody.addProperty("clientId", clientId);
//        requestBody.addProperty("redirectUri", redirectUri);
//        requestBody.addProperty("responseType", responseType);
//        requestBody.addProperty("scope", scope);
//        requestBody.addProperty("state", state);
//
//        OAuthSQLStorage oAuthStorage = (OAuthSQLStorage) StorageLayer.getStorage(process.getProcess());
//
//        AppIdentifier testApp = new AppIdentifier("", "");
//        oAuthStorage.addClientForApp(testApp, clientId);
//
//        OAuthAPIException thrown = assertThrows(OAuthAPIException.class, () -> {
//
//            OAuthAuthResponse response = OAuth.getAuthorizationUrl(process.getProcess(), new AppIdentifier("", ""),
//                    oAuthStorage, requestBody);
//        });
//
//        String expectedError = "invalid_request";
//        String expectedDescription = "The request is missing a required parameter, includes an invalid parameter value, includes a parameter more than once, or is otherwise malformed. The 'redirect_uri' parameter does not match any of the OAuth 2.0 Client's pre-registered redirect urls.";
//
//        assertEquals(expectedError, thrown.error);
//        assertEquals(expectedDescription, thrown.errorDescription);
//
//        {
//
//            JsonObject actualResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
//                    "http://localhost:3567/recipe/oauth/auth", requestBody, 1000, 1000, null,
//                    null, RECIPE_ID.OAUTH.toString());
//
//            assertEquals("OAUTH2_AUTH_ERROR", actualResponse.get("status").getAsString());
//            assertTrue(actualResponse.has("error"));
//            assertTrue(actualResponse.has("errorDescription"));
//            assertEquals(expectedError, actualResponse.get("error").getAsString());
//            assertEquals(expectedDescription, actualResponse.get("errorDescription").getAsString());
//
//        }
//        process.kill();
//        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
//    }
}
