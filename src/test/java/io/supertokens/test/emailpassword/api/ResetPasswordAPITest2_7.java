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

package io.supertokens.test.emailpassword.api;

import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.utils.SemVer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.*;

/*
 * TODO:
 *  - Check for bad input (missing fields)
 *  - Check good input works
 *  - Check for all types of output
 *  - Check that invalid method throws 400 error
 *  - Check that empty password throws 400 error
 * */

public class ResetPasswordAPITest2_7 {

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

    // Check for bad input (missing fields)
    @Test
    public void testBadInput() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        {
            try {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/user/password/reset", null, 1000, 1000, null,
                        SemVer.v2_7.get(), "emailpassword");
                throw new Exception("Should not come here");
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                assertTrue(e.statusCode == 400
                        && e.getMessage().equals("Http error. Status Code: 400. Message: Invalid Json Input"));
            }
        }

        {
            JsonObject requestBody = new JsonObject();
            try {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/user/password/reset", requestBody, 1000, 1000, null,
                        SemVer.v2_7.get(), "emailpassword");
                throw new Exception("Should not come here");
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals(
                        "Http error. Status Code: 400. Message: Field name 'method' is invalid in " + "JSON input"));
            }
        }

        {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("token", "randomToken");
            requestBody.addProperty("newPassword", "randomPassword");
            try {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/user/password/reset", requestBody, 1000, 1000, null,
                        SemVer.v2_7.get(), "emailpassword");
                throw new Exception("Should not come here");
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals(
                        "Http error. Status Code: 400. Message: Field name 'method' is invalid in " + "JSON input"));
            }
        }

        {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("method", "token");
            requestBody.addProperty("newPassword", "randomPassword");

            try {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/user/password/reset", requestBody, 1000, 1000, null,
                        SemVer.v2_7.get(), "emailpassword");
                throw new Exception("Should not come here");
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals(
                        "Http error. Status Code: 400. Message: Field name 'token' is invalid in " + "JSON input"));
            }
        }

        {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("method", "token");
            requestBody.addProperty("token", "randomToken");

            try {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/user/password/reset", requestBody, 1000, 1000, null,
                        SemVer.v2_7.get(), "emailpassword");
                throw new Exception("Should not come here");
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage()
                        .equals("Http error. Status Code: 400. Message: Field name 'newPassword' is invalid " + "in "
                                + "JSON input"));
            }
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // Check good input works
    @Test
    public void testGoodInput() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject signUpResponse = Utils.signUpRequest_2_4(process, "random@gmail.com", "validPass123");
        assertEquals(signUpResponse.get("status").getAsString(), "OK");
        assertEquals(signUpResponse.entrySet().size(), 2);

        String userId = signUpResponse.getAsJsonObject("user").get("id").getAsString();

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("userId", userId);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/user/password/reset/token", requestBody, 1000, 1000, null,
                SemVer.v2_7.get(), "emailpassword");

        assertEquals(response.get("status").getAsString(), "OK");
        assertEquals(response.entrySet().size(), 2);

        String token = response.get("token").getAsString();

        JsonObject resetPasswordBody = new JsonObject();
        resetPasswordBody.addProperty("method", "token");
        resetPasswordBody.addProperty("token", token);
        resetPasswordBody.addProperty("newPassword", "newValidPass123");

        JsonObject passwordResetResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/user/password/reset", resetPasswordBody, 1000, 1000, null,
                SemVer.v2_7.get(), "emailpassword");
        assertEquals(passwordResetResponse.get("status").getAsString(), "OK");
        assertEquals(passwordResetResponse.entrySet().size(), 1);

        JsonObject signInRequestBody = new JsonObject();
        signInRequestBody.addProperty("email", "random@gmail.com");
        signInRequestBody.addProperty("password", "validPass123");

        response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/signin", signInRequestBody, 1000, 1000, null,
                SemVer.v2_7.get(), "emailpassword");

        assertEquals(response.get("status").getAsString(), "WRONG_CREDENTIALS_ERROR");
        assertEquals(response.entrySet().size(), 1);

        signInRequestBody = new JsonObject();
        signInRequestBody.addProperty("email", "random@gmail.com");
        signInRequestBody.addProperty("password", "newValidPass123");

        response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/signin", signInRequestBody, 1000, 1000, null,
                SemVer.v2_7.get(), "emailpassword");

        assertEquals(response.get("status").getAsString(), "OK");
        assertEquals(response.entrySet().size(), 2);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // Check for all types of output
    // Failure condition: passing a valid password reset token will fail the test
    @Test
    public void testALLTypesOfOutPut() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject resetPasswordBody = new JsonObject();
        resetPasswordBody.addProperty("method", "token");
        resetPasswordBody.addProperty("token", "randomToken");
        resetPasswordBody.addProperty("newPassword", "newValidPass123");

        JsonObject passwordResetResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/user/password/reset", resetPasswordBody, 1000, 1000, null,
                SemVer.v2_7.get(), "emailpassword");

        assertEquals(passwordResetResponse.get("status").getAsString(), "RESET_PASSWORD_INVALID_TOKEN_ERROR");
        assertEquals(passwordResetResponse.entrySet().size(), 1);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // Check that invalid method throws 400 error
    // Failure condition: passing a valid method will fail the test
    @Test
    public void testInvalidMethodThrows400Error() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject resetPasswordBody = new JsonObject();
        resetPasswordBody.addProperty("method", "invalidMethod");
        resetPasswordBody.addProperty("token", "randomToken");
        resetPasswordBody.addProperty("newPassword", "newValidPass123");

        try {
            HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user/password/reset", resetPasswordBody, 1000, 1000, null,
                    SemVer.v2_7.get(), "emailpassword");

        } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
            assertTrue(e.statusCode == 400 && e.getMessage()
                    .equals("Http error. Status Code: 400. Message: Unsupported method for password reset"));
        }
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // Check that empty password throws 400 error
    // Failure condition: submitting a valid password will fail the test
    @Test
    public void testEmptyPasswordThrows400Error() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject resetPasswordBody = new JsonObject();
        resetPasswordBody.addProperty("method", "token");
        resetPasswordBody.addProperty("token", "randomToken");
        resetPasswordBody.addProperty("newPassword", "");

        try {
            HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user/password/reset", resetPasswordBody, 1000, 1000, null,
                    SemVer.v2_7.get(), "emailpassword");

        } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
            assertTrue(e.statusCode == 400 && e.getMessage()
                    .equals("Http error. Status Code: 400. Message: Password cannot be an empty string"));
        }
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
