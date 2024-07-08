/*
 *    Copyright (c) 2021, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.test.passwordless.api;

import com.google.gson.JsonObject;
import io.supertokens.ActiveUsers;
import io.supertokens.ProcessState;
import io.supertokens.authRecipe.AuthRecipe;
import io.supertokens.passwordless.Passwordless;
import io.supertokens.passwordless.Passwordless.CreateCodeResponse;
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

public class PasswordlessCheckCodeAPITest5_0 {
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
    public void testBadInput() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        long startTs = System.currentTimeMillis();

        String email = "test@example.com";
        CreateCodeResponse createResp = Passwordless.createCode(process.getProcess(), email, null, null, null);
        {
            HttpResponseException error = null;
            try {
                JsonObject consumeCodeRequestBody = new JsonObject();

                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/signinup/code/check", consumeCodeRequestBody, 1000, 1000, null,
                        SemVer.v5_0.get(), "passwordless");
            } catch (HttpResponseException ex) {
                error = ex;
            }

            assertNotNull(error);
            assertEquals(400, error.statusCode);
            assertEquals(
                    "Http error. Status Code: 400. Message: Field name 'preAuthSessionId' is invalid in JSON input",
                    error.getMessage());
        }

        {
            HttpResponseException error = null;
            try {
                JsonObject consumeCodeRequestBody = new JsonObject();
                consumeCodeRequestBody.addProperty("preAuthSessionId", createResp.deviceIdHash);

                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/signinup/code/check", consumeCodeRequestBody, 1000, 1000, null,
                        SemVer.v5_0.get(), "passwordless");
            } catch (HttpResponseException ex) {
                error = ex;
            }

            assertNotNull(error);
            assertEquals(400, error.statusCode);
            assertEquals(
                    "Http error. Status Code: 400. Message: Please provide exactly one of linkCode or " +
                            "deviceId+userInputCode",
                    error.getMessage());
        }

        {
            HttpResponseException error = null;
            try {
                JsonObject consumeCodeRequestBody = new JsonObject();
                consumeCodeRequestBody.addProperty("linkCode", createResp.linkCode);
                consumeCodeRequestBody.addProperty("deviceId", createResp.deviceId);
                consumeCodeRequestBody.addProperty("preAuthSessionId", createResp.deviceIdHash);
                consumeCodeRequestBody.addProperty("userInputCode", createResp.userInputCode);

                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/signinup/code/check", consumeCodeRequestBody, 1000, 1000, null,
                        SemVer.v5_0.get(), "passwordless");
            } catch (HttpResponseException ex) {
                error = ex;
            }

            assertNotNull(error);
            assertEquals(400, error.statusCode);
            assertEquals(
                    "Http error. Status Code: 400. Message: Please provide exactly one of linkCode or " +
                            "deviceId+userInputCode",
                    error.getMessage());
        }

        {
            HttpResponseException error = null;
            try {
                JsonObject consumeCodeRequestBody = new JsonObject();
                consumeCodeRequestBody.addProperty("preAuthSessionId", createResp.deviceIdHash);
                consumeCodeRequestBody.addProperty("linkCode", createResp.linkCode);
                consumeCodeRequestBody.addProperty("userInputCode", createResp.userInputCode);

                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/signinup/code/check", consumeCodeRequestBody, 1000, 1000, null,
                        SemVer.v5_0.get(), "passwordless");
            } catch (HttpResponseException ex) {
                error = ex;
            }

            assertNotNull(error);
            assertEquals(400, error.statusCode);
            assertEquals(
                    "Http error. Status Code: 400. Message: Please provide exactly one of linkCode or " +
                            "deviceId+userInputCode",
                    error.getMessage());
        }
        {
            HttpResponseException error = null;
            try {
                JsonObject consumeCodeRequestBody = new JsonObject();
                consumeCodeRequestBody.addProperty("preAuthSessionId", createResp.deviceIdHash);
                consumeCodeRequestBody.addProperty("userInputCode", createResp.userInputCode);

                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/signinup/code/check", consumeCodeRequestBody, 1000, 1000, null,
                        SemVer.v5_0.get(), "passwordless");
            } catch (HttpResponseException ex) {
                error = ex;
            }

            assertNotNull(error);
            assertEquals(400, error.statusCode);
            assertEquals(
                    "Http error. Status Code: 400. Message: Please provide exactly one of linkCode or " +
                            "deviceId+userInputCode",
                    error.getMessage());
        }
        {
            HttpResponseException error = null;
            try {
                JsonObject consumeCodeRequestBody = new JsonObject();
                consumeCodeRequestBody.addProperty("deviceId", createResp.deviceId);
                consumeCodeRequestBody.addProperty("preAuthSessionId", createResp.deviceIdHash);

                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/signinup/code/check", consumeCodeRequestBody, 1000, 1000, null,
                        SemVer.v5_0.get(), "passwordless");
            } catch (HttpResponseException ex) {
                error = ex;
            }

            assertNotNull(error);
            assertEquals(400, error.statusCode);
            assertEquals(
                    "Http error. Status Code: 400. Message: Please provide exactly one of linkCode or " +
                            "deviceId+userInputCode",
                    error.getMessage());
        }

        {
            HttpResponseException error = null;
            try {
                JsonObject consumeCodeRequestBody = new JsonObject();
                consumeCodeRequestBody.addProperty("deviceId", createResp.deviceId);
                consumeCodeRequestBody.addProperty("preAuthSessionId", createResp.deviceIdHash + "asdf");
                consumeCodeRequestBody.addProperty("userInputCode", createResp.userInputCode);

                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/signinup/code/check", consumeCodeRequestBody, 1000, 1000, null,
                        SemVer.v5_0.get(), "passwordless");
            } catch (HttpResponseException ex) {
                error = ex;
            }

            assertNotNull(error);
            assertEquals(400, error.statusCode);
            assertEquals("Http error. Status Code: 400. Message: preAuthSessionId and deviceId doesn't match",
                    error.getMessage());
        }

        {
            HttpResponseException error = null;
            try {
                JsonObject consumeCodeRequestBody = new JsonObject();
                consumeCodeRequestBody.addProperty("preAuthSessionId", createResp.deviceIdHash + "asdf");
                consumeCodeRequestBody.addProperty("linkCode", createResp.linkCode);

                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/signinup/code/check", consumeCodeRequestBody, 1000, 1000, null,
                        SemVer.v5_0.get(), "passwordless");
            } catch (HttpResponseException ex) {
                error = ex;
            }

            assertNotNull(error);
            assertEquals(400, error.statusCode);
            assertEquals("Http error. Status Code: 400. Message: preAuthSessionId and deviceId doesn't match",
                    error.getMessage());
        }

        /*
         * malformed linkCode -> BadRequest
         */
        {
            HttpResponseException error = null;
            try {
                JsonObject consumeCodeRequestBody = new JsonObject();
                consumeCodeRequestBody.addProperty("preAuthSessionId", createResp.deviceIdHash);
                consumeCodeRequestBody.addProperty("linkCode", createResp.linkCode + "==#");

                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/signinup/code/check", consumeCodeRequestBody, 1000, 1000, null,
                        SemVer.v5_0.get(), "passwordless");
            } catch (HttpResponseException ex) {
                error = ex;
            }

            assertNotNull(error);
            assertEquals(400, error.statusCode);
            assertEquals("Http error. Status Code: 400. Message: Input encoding error in linkCode", error.getMessage());
        }

        /*
         * malformed deviceId -> BadRequest
         * TODO: throwing 500 error
         */
        {
            HttpResponseException error = null;
            try {
                JsonObject consumeCodeRequestBody = new JsonObject();
                consumeCodeRequestBody.addProperty("preAuthSessionId", createResp.deviceIdHash);
                consumeCodeRequestBody.addProperty("deviceId", createResp.deviceId + "==#");

                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/signinup/code/check", consumeCodeRequestBody, 1000, 1000, null,
                        SemVer.v5_0.get(), "passwordless");
            } catch (HttpResponseException ex) {
                error = ex;
            }

            assertNotNull(error);
            assertEquals(400, error.statusCode);
            assertEquals(
                    "Http error. Status Code: 400. Message: Please provide exactly one of linkCode or " +
                            "deviceId+userInputCode",
                    error.getMessage());
        }

        int activeUsers = ActiveUsers.countUsersActiveSince(process.getProcess(), startTs);
        assert (activeUsers == 0);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testLinkCode() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        long startTs = System.currentTimeMillis();

        String email = "test@example.com";
        CreateCodeResponse createResp = Passwordless.createCode(process.getProcess(), email, null, null, null);

        JsonObject consumeCodeRequestBody = new JsonObject();
        consumeCodeRequestBody.addProperty("preAuthSessionId", createResp.deviceIdHash);
        consumeCodeRequestBody.addProperty("linkCode", createResp.linkCode);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/signinup/code/check", consumeCodeRequestBody, 1000, 1000, null,
                SemVer.v5_0.get(), "passwordless");

        checkResponse(response, email, null);

        int activeUsers = ActiveUsers.countUsersActiveSince(process.getProcess(), startTs);
        assert (activeUsers == 0);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testExpiredLinkCode() throws Exception {
        String[] args = {"../"};

        Utils.setValueInConfig("passwordless_code_lifetime", "100");

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        long startTs = System.currentTimeMillis();

        String email = "test@example.com";
        CreateCodeResponse createResp = Passwordless.createCode(process.getProcess(), email, null, null, null);
        Thread.sleep(150);
        JsonObject consumeCodeRequestBody = new JsonObject();
        consumeCodeRequestBody.addProperty("preAuthSessionId", createResp.deviceIdHash);
        consumeCodeRequestBody.addProperty("linkCode", createResp.linkCode);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/signinup/code/check", consumeCodeRequestBody, 1000, 1000, null,
                SemVer.v5_0.get(), "passwordless");

        assertEquals("RESTART_FLOW_ERROR", response.get("status").getAsString());

        int activeUsers = ActiveUsers.countUsersActiveSince(process.getProcess(), startTs);
        assert (activeUsers == 0);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testUserInputCode() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        long startTs = System.currentTimeMillis();

        String email = "test@example.com";
        CreateCodeResponse createResp = Passwordless.createCode(process.getProcess(), email, null, null, null);

        JsonObject consumeCodeRequestBody = new JsonObject();
        consumeCodeRequestBody.addProperty("deviceId", createResp.deviceId);
        consumeCodeRequestBody.addProperty("preAuthSessionId", createResp.deviceIdHash);
        consumeCodeRequestBody.addProperty("userInputCode", createResp.userInputCode);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/signinup/code/check", consumeCodeRequestBody, 1000, 1000, null,
                SemVer.v5_0.get(), "passwordless");

        checkResponse(response, email, null);

        int activeUsers = ActiveUsers.countUsersActiveSince(process.getProcess(), startTs);
        assert (activeUsers == 0);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testUserInputCodeDoesNotDeleteTheCode() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        long startTs = System.currentTimeMillis();

        String email = "test@example.com";
        CreateCodeResponse createResp = Passwordless.createCode(process.getProcess(), email, null, null, null);

        JsonObject consumeCodeRequestBody = new JsonObject();
        consumeCodeRequestBody.addProperty("deviceId", createResp.deviceId);
        consumeCodeRequestBody.addProperty("preAuthSessionId", createResp.deviceIdHash);
        consumeCodeRequestBody.addProperty("userInputCode", createResp.userInputCode);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/signinup/code/check", consumeCodeRequestBody, 1000, 1000, null,
                SemVer.v5_0.get(), "passwordless");

        checkResponse(response, email, null);

        // should be able to call again, if the code is not deleted
        response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/signinup/code/check", consumeCodeRequestBody, 1000, 1000, null,
                SemVer.v5_0.get(), "passwordless");

        checkResponse(response, email, null);

        int activeUsers = ActiveUsers.countUsersActiveSince(process.getProcess(), startTs);
        assert (activeUsers == 0);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testExpiredUserInputCode() throws Exception {
        String[] args = {"../"};

        Utils.setValueInConfig("passwordless_code_lifetime", "100");
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        long startTs = System.currentTimeMillis();

        String email = "test@example.com";
        CreateCodeResponse createResp = Passwordless.createCode(process.getProcess(), email, null, null, null);
        Thread.sleep(150);

        JsonObject consumeCodeRequestBody = new JsonObject();
        consumeCodeRequestBody.addProperty("deviceId", createResp.deviceId);
        consumeCodeRequestBody.addProperty("preAuthSessionId", createResp.deviceIdHash);
        consumeCodeRequestBody.addProperty("userInputCode", createResp.userInputCode);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/signinup/code/check", consumeCodeRequestBody, 1000, 1000, null,
                SemVer.v5_0.get(), "passwordless");

        assertEquals("EXPIRED_USER_INPUT_CODE_ERROR", response.get("status").getAsString());

        int activeUsers = ActiveUsers.countUsersActiveSince(process.getProcess(), startTs);
        assert (activeUsers == 0);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testIncorrectUserInputCode() throws Exception {
        String[] args = {"../"};

        Utils.setValueInConfig("passwordless_max_code_input_attempts", "2"); // Only 2 code entries permitted (1 retry)

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        String email = "test@example.com";
        CreateCodeResponse createResp = Passwordless.createCode(process.getProcess(), email, null, null, null);

        JsonObject consumeCodeRequestBody = new JsonObject();
        consumeCodeRequestBody.addProperty("deviceId", createResp.deviceId);
        consumeCodeRequestBody.addProperty("preAuthSessionId", createResp.deviceIdHash);
        consumeCodeRequestBody.addProperty("userInputCode", createResp.userInputCode + "nope");

        {
            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/signinup/code/check", consumeCodeRequestBody, 1000, 1000, null,
                    SemVer.v5_0.get(), "passwordless");

            assertEquals("INCORRECT_USER_INPUT_CODE_ERROR", response.get("status").getAsString());
        }

        {
            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/signinup/code/check", consumeCodeRequestBody, 1000, 1000, null,
                    SemVer.v5_0.get(), "passwordless");

            assertEquals("RESTART_FLOW_ERROR", response.get("status").getAsString());
        }
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testConsumeCodeWithoutCreatingUser() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        long startTs = System.currentTimeMillis();

        String email = "test@example.com";
        CreateCodeResponse createResp = Passwordless.createCode(process.getProcess(), email, null, null, null);

        JsonObject consumeCodeRequestBody = new JsonObject();
        consumeCodeRequestBody.addProperty("deviceId", createResp.deviceId);
        consumeCodeRequestBody.addProperty("preAuthSessionId", createResp.deviceIdHash);
        consumeCodeRequestBody.addProperty("userInputCode", createResp.userInputCode);
        consumeCodeRequestBody.addProperty("createRecipeUserIfNotExists", false);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/signinup/code/check", consumeCodeRequestBody, 1000, 1000, null,
                SemVer.v5_0.get(), "passwordless");

        checkResponse(response, email, null);

        int activeUsers = ActiveUsers.countUsersActiveSince(process.getProcess(), startTs);
        assert (activeUsers == 0);

        assertEquals(0,
                AuthRecipe.getUsersCount(process.getProcess(), null)); // ensure that no user was actually created

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testVerifyCodeReturnsUserIfItAlreadyExists() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        long startTs = System.currentTimeMillis();

        String email = "test@example.com";
        CreateCodeResponse createResp = Passwordless.createCode(process.getProcess(), email, null, null, null);
        Passwordless.consumeCode(process.getProcess(), createResp.deviceId, createResp.deviceIdHash,
                createResp.userInputCode, null);

        createResp = Passwordless.createCode(process.getProcess(), email, null, null, null);

        JsonObject consumeCodeRequestBody = new JsonObject();
        consumeCodeRequestBody.addProperty("deviceId", createResp.deviceId);
        consumeCodeRequestBody.addProperty("preAuthSessionId", createResp.deviceIdHash);
        consumeCodeRequestBody.addProperty("userInputCode", createResp.userInputCode);
        consumeCodeRequestBody.addProperty("createRecipeUserIfNotExists", false);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/signinup/code/check", consumeCodeRequestBody, 1000, 1000, null,
                SemVer.v5_0.get(), "passwordless");

        checkResponse(response, email, null);

        int activeUsers = ActiveUsers.countUsersActiveSince(process.getProcess(), startTs);
        assert (activeUsers == 0);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testBadInputWithoutCreatingUser() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        long startTs = System.currentTimeMillis();

        String email = "test@example.com";
        CreateCodeResponse createResp = Passwordless.createCode(process.getProcess(), email, null, null, null);
        {
            HttpResponseException error = null;
            try {
                JsonObject consumeCodeRequestBody = new JsonObject();

                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/signinup/code/check", consumeCodeRequestBody, 1000, 1000, null,
                        SemVer.v5_0.get(), "passwordless");
            } catch (HttpResponseException ex) {
                error = ex;
            }

            assertNotNull(error);
            assertEquals(400, error.statusCode);
            assertEquals(
                    "Http error. Status Code: 400. Message: Field name 'preAuthSessionId' is invalid in JSON input",
                    error.getMessage());
        }

        {
            HttpResponseException error = null;
            try {
                JsonObject consumeCodeRequestBody = new JsonObject();
                consumeCodeRequestBody.addProperty("preAuthSessionId", createResp.deviceIdHash);
                consumeCodeRequestBody.addProperty("createRecipeUserIfNotExists", false);

                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/signinup/code/check", consumeCodeRequestBody, 1000, 1000, null,
                        SemVer.v5_0.get(), "passwordless");
            } catch (HttpResponseException ex) {
                error = ex;
            }

            assertNotNull(error);
            assertEquals(400, error.statusCode);
            assertEquals(
                    "Http error. Status Code: 400. Message: Please provide exactly one of linkCode or " +
                            "deviceId+userInputCode",
                    error.getMessage());
        }

        {
            HttpResponseException error = null;
            try {
                JsonObject consumeCodeRequestBody = new JsonObject();
                consumeCodeRequestBody.addProperty("linkCode", createResp.linkCode);
                consumeCodeRequestBody.addProperty("deviceId", createResp.deviceId);
                consumeCodeRequestBody.addProperty("preAuthSessionId", createResp.deviceIdHash);
                consumeCodeRequestBody.addProperty("userInputCode", createResp.userInputCode);
                consumeCodeRequestBody.addProperty("createRecipeUserIfNotExists", false);

                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/signinup/code/check", consumeCodeRequestBody, 1000, 1000, null,
                        SemVer.v5_0.get(), "passwordless");
            } catch (HttpResponseException ex) {
                error = ex;
            }

            assertNotNull(error);
            assertEquals(400, error.statusCode);
            assertEquals(
                    "Http error. Status Code: 400. Message: Please provide exactly one of linkCode or " +
                            "deviceId+userInputCode",
                    error.getMessage());
        }

        {
            HttpResponseException error = null;
            try {
                JsonObject consumeCodeRequestBody = new JsonObject();
                consumeCodeRequestBody.addProperty("preAuthSessionId", createResp.deviceIdHash);
                consumeCodeRequestBody.addProperty("linkCode", createResp.linkCode);
                consumeCodeRequestBody.addProperty("userInputCode", createResp.userInputCode);
                consumeCodeRequestBody.addProperty("createRecipeUserIfNotExists", false);

                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/signinup/code/check", consumeCodeRequestBody, 1000, 1000, null,
                        SemVer.v5_0.get(), "passwordless");
            } catch (HttpResponseException ex) {
                error = ex;
            }

            assertNotNull(error);
            assertEquals(400, error.statusCode);
            assertEquals(
                    "Http error. Status Code: 400. Message: Please provide exactly one of linkCode or " +
                            "deviceId+userInputCode",
                    error.getMessage());
        }
        {
            HttpResponseException error = null;
            try {
                JsonObject consumeCodeRequestBody = new JsonObject();
                consumeCodeRequestBody.addProperty("preAuthSessionId", createResp.deviceIdHash);
                consumeCodeRequestBody.addProperty("userInputCode", createResp.userInputCode);
                consumeCodeRequestBody.addProperty("createRecipeUserIfNotExists", false);

                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/signinup/code/check", consumeCodeRequestBody, 1000, 1000, null,
                        SemVer.v5_0.get(), "passwordless");
            } catch (HttpResponseException ex) {
                error = ex;
            }

            assertNotNull(error);
            assertEquals(400, error.statusCode);
            assertEquals(
                    "Http error. Status Code: 400. Message: Please provide exactly one of linkCode or " +
                            "deviceId+userInputCode",
                    error.getMessage());
        }
        {
            HttpResponseException error = null;
            try {
                JsonObject consumeCodeRequestBody = new JsonObject();
                consumeCodeRequestBody.addProperty("deviceId", createResp.deviceId);
                consumeCodeRequestBody.addProperty("preAuthSessionId", createResp.deviceIdHash);
                consumeCodeRequestBody.addProperty("createRecipeUserIfNotExists", false);

                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/signinup/code/check", consumeCodeRequestBody, 1000, 1000, null,
                        SemVer.v5_0.get(), "passwordless");
            } catch (HttpResponseException ex) {
                error = ex;
            }

            assertNotNull(error);
            assertEquals(400, error.statusCode);
            assertEquals(
                    "Http error. Status Code: 400. Message: Please provide exactly one of linkCode or " +
                            "deviceId+userInputCode",
                    error.getMessage());
        }

        {
            HttpResponseException error = null;
            try {
                JsonObject consumeCodeRequestBody = new JsonObject();
                consumeCodeRequestBody.addProperty("deviceId", createResp.deviceId);
                consumeCodeRequestBody.addProperty("preAuthSessionId", createResp.deviceIdHash + "asdf");
                consumeCodeRequestBody.addProperty("userInputCode", createResp.userInputCode);
                consumeCodeRequestBody.addProperty("createRecipeUserIfNotExists", false);

                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/signinup/code/check", consumeCodeRequestBody, 1000, 1000, null,
                        SemVer.v5_0.get(), "passwordless");
            } catch (HttpResponseException ex) {
                error = ex;
            }

            assertNotNull(error);
            assertEquals(400, error.statusCode);
            assertEquals("Http error. Status Code: 400. Message: preAuthSessionId and deviceId doesn't match",
                    error.getMessage());
        }

        {
            HttpResponseException error = null;
            try {
                JsonObject consumeCodeRequestBody = new JsonObject();
                consumeCodeRequestBody.addProperty("preAuthSessionId", createResp.deviceIdHash + "asdf");
                consumeCodeRequestBody.addProperty("linkCode", createResp.linkCode);
                consumeCodeRequestBody.addProperty("createRecipeUserIfNotExists", false);

                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/signinup/code/check", consumeCodeRequestBody, 1000, 1000, null,
                        SemVer.v5_0.get(), "passwordless");
            } catch (HttpResponseException ex) {
                error = ex;
            }

            assertNotNull(error);
            assertEquals(400, error.statusCode);
            assertEquals("Http error. Status Code: 400. Message: preAuthSessionId and deviceId doesn't match",
                    error.getMessage());
        }

        /*
         * malformed linkCode -> BadRequest
         */
        {
            HttpResponseException error = null;
            try {
                JsonObject consumeCodeRequestBody = new JsonObject();
                consumeCodeRequestBody.addProperty("preAuthSessionId", createResp.deviceIdHash);
                consumeCodeRequestBody.addProperty("linkCode", createResp.linkCode + "==#");
                consumeCodeRequestBody.addProperty("createRecipeUserIfNotExists", false);

                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/signinup/code/check", consumeCodeRequestBody, 1000, 1000, null,
                        SemVer.v5_0.get(), "passwordless");
            } catch (HttpResponseException ex) {
                error = ex;
            }

            assertNotNull(error);
            assertEquals(400, error.statusCode);
            assertEquals("Http error. Status Code: 400. Message: Input encoding error in linkCode", error.getMessage());
        }

        /*
         * malformed deviceId -> BadRequest
         * TODO: throwing 500 error
         */
        {
            HttpResponseException error = null;
            try {
                JsonObject consumeCodeRequestBody = new JsonObject();
                consumeCodeRequestBody.addProperty("preAuthSessionId", createResp.deviceIdHash);
                consumeCodeRequestBody.addProperty("deviceId", createResp.deviceId + "==#");
                consumeCodeRequestBody.addProperty("createRecipeUserIfNotExists", false);

                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/signinup/code/check", consumeCodeRequestBody, 1000, 1000, null,
                        SemVer.v5_0.get(), "passwordless");
            } catch (HttpResponseException ex) {
                error = ex;
            }

            assertNotNull(error);
            assertEquals(400, error.statusCode);
            assertEquals(
                    "Http error. Status Code: 400. Message: Please provide exactly one of linkCode or " +
                            "deviceId+userInputCode",
                    error.getMessage());
        }

        int activeUsers = ActiveUsers.countUsersActiveSince(process.getProcess(), startTs);
        assert (activeUsers == 0);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testLinkCodeWithoutCreatingUser() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        long startTs = System.currentTimeMillis();

        String email = "test@example.com";
        CreateCodeResponse createResp = Passwordless.createCode(process.getProcess(), email, null, null, null);

        JsonObject consumeCodeRequestBody = new JsonObject();
        consumeCodeRequestBody.addProperty("preAuthSessionId", createResp.deviceIdHash);
        consumeCodeRequestBody.addProperty("linkCode", createResp.linkCode);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/signinup/code/check", consumeCodeRequestBody, 1000, 1000, null,
                SemVer.v5_0.get(), "passwordless");

        checkResponse(response, email, null);

        int activeUsers = ActiveUsers.countUsersActiveSince(process.getProcess(), startTs);
        assert (activeUsers == 0);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testExpiredLinkCodeWithoutCreatingUser() throws Exception {
        String[] args = {"../"};

        Utils.setValueInConfig("passwordless_code_lifetime", "100");

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        long startTs = System.currentTimeMillis();

        String email = "test@example.com";
        CreateCodeResponse createResp = Passwordless.createCode(process.getProcess(), email, null, null, null);
        Thread.sleep(150);
        JsonObject consumeCodeRequestBody = new JsonObject();
        consumeCodeRequestBody.addProperty("preAuthSessionId", createResp.deviceIdHash);
        consumeCodeRequestBody.addProperty("linkCode", createResp.linkCode);
        consumeCodeRequestBody.addProperty("createRecipeUserIfNotExists", false);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/signinup/code/check", consumeCodeRequestBody, 1000, 1000, null,
                SemVer.v5_0.get(), "passwordless");

        assertEquals("RESTART_FLOW_ERROR", response.get("status").getAsString());

        int activeUsers = ActiveUsers.countUsersActiveSince(process.getProcess(), startTs);
        assert (activeUsers == 0);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testExpiredUserInputCodeWithoutCreatingUser() throws Exception {
        String[] args = {"../"};

        Utils.setValueInConfig("passwordless_code_lifetime", "100");
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        long startTs = System.currentTimeMillis();

        String email = "test@example.com";
        CreateCodeResponse createResp = Passwordless.createCode(process.getProcess(), email, null, null, null);
        Thread.sleep(150);

        JsonObject consumeCodeRequestBody = new JsonObject();
        consumeCodeRequestBody.addProperty("deviceId", createResp.deviceId);
        consumeCodeRequestBody.addProperty("preAuthSessionId", createResp.deviceIdHash);
        consumeCodeRequestBody.addProperty("userInputCode", createResp.userInputCode);
        consumeCodeRequestBody.addProperty("createRecipeUserIfNotExists", false);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/signinup/code/check", consumeCodeRequestBody, 1000, 1000, null,
                SemVer.v5_0.get(), "passwordless");

        assertEquals("EXPIRED_USER_INPUT_CODE_ERROR", response.get("status").getAsString());

        int activeUsers = ActiveUsers.countUsersActiveSince(process.getProcess(), startTs);
        assert (activeUsers == 0);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testIncorrectUserInputCodeWithoutCreatingUser() throws Exception {
        String[] args = {"../"};

        Utils.setValueInConfig("passwordless_max_code_input_attempts", "2"); // Only 2 code entries permitted (1 retry)

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        String email = "test@example.com";
        CreateCodeResponse createResp = Passwordless.createCode(process.getProcess(), email, null, null, null);

        JsonObject consumeCodeRequestBody = new JsonObject();
        consumeCodeRequestBody.addProperty("deviceId", createResp.deviceId);
        consumeCodeRequestBody.addProperty("preAuthSessionId", createResp.deviceIdHash);
        consumeCodeRequestBody.addProperty("userInputCode", createResp.userInputCode + "nope");
        consumeCodeRequestBody.addProperty("createRecipeUserIfNotExists", false);

        {
            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/signinup/code/check", consumeCodeRequestBody, 1000, 1000, null,
                    SemVer.v5_0.get(), "passwordless");

            assertEquals("INCORRECT_USER_INPUT_CODE_ERROR", response.get("status").getAsString());
        }

        {
            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/signinup/code/check", consumeCodeRequestBody, 1000, 1000, null,
                    SemVer.v5_0.get(), "passwordless");

            assertEquals("RESTART_FLOW_ERROR", response.get("status").getAsString());
        }
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    private void checkResponse(JsonObject response, String email, String phoneNumber) {
        assertEquals("OK", response.get("status").getAsString());

        assertEquals(2, response.entrySet().size());
        assertTrue(response.has("consumedDevice"));

        JsonObject consumedDevice = response.get("consumedDevice").getAsJsonObject();
        assertEquals(3, consumedDevice.entrySet().size());
        assertTrue(consumedDevice.has("preAuthSessionId"));
        assertTrue(consumedDevice.has("failedCodeInputAttemptCount"));

        if (email != null) {
            assertEquals(email, consumedDevice.get("email").getAsString());
        } else {
            assertTrue(consumedDevice.get("phoneNumber").isJsonNull());
        }

    }
}
