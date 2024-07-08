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
import io.supertokens.ProcessState;
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

import java.util.Base64;
import java.util.UUID;

public class PasswordlessCreateCodeAPITest2_11 {
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
    public void testEmail() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject createCodeRequestBody = new JsonObject();
        createCodeRequestBody.addProperty("email", "test@example.com");

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/signinup/code", createCodeRequestBody, 1000, 1000, null,
                SemVer.v2_10.get(), "passwordless");

        checkResponse(response);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testEmailWithUserInputCode() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }
        String exampleCode = "codeYcode";
        JsonObject createCodeRequestBody = new JsonObject();
        createCodeRequestBody.addProperty("email", "test@example.com");
        createCodeRequestBody.addProperty("userInputCode", exampleCode);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/signinup/code", createCodeRequestBody, 1000, 1000, null,
                SemVer.v2_10.get(), "passwordless");

        checkResponse(response, exampleCode);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testPhone() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject createCodeRequestBody = new JsonObject();
        createCodeRequestBody.addProperty("phoneNumber", "+442071838750");

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/signinup/code", createCodeRequestBody, 1000, 1000, null,
                SemVer.v2_10.get(), "passwordless");
        checkResponse(response);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testPhoneWithUserInputCode() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }
        String exampleCode = "codeYcode";
        JsonObject createCodeRequestBody = new JsonObject();
        createCodeRequestBody.addProperty("phoneNumber", "+442071838750");
        createCodeRequestBody.addProperty("userInputCode", exampleCode);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/signinup/code", createCodeRequestBody, 1000, 1000, null,
                SemVer.v2_10.get(), "passwordless");

        checkResponse(response, exampleCode);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testResend() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject createCodeRequestBody = new JsonObject();
        createCodeRequestBody.addProperty("phoneNumber", "+442071838750");

        JsonObject createResp = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/signinup/code", createCodeRequestBody, 1000, 1000, null,
                SemVer.v2_10.get(), "passwordless");

        JsonObject resendCodeRequestBody = new JsonObject();
        resendCodeRequestBody.addProperty("deviceId", createResp.get("deviceId").getAsString());

        JsonObject resendResp = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/signinup/code", resendCodeRequestBody, 1000, 1000, null,
                SemVer.v2_10.get(), "passwordless");

        checkResponse(resendResp);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testResendWithUserInputCode() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }
        String exampleCode = "codeYcode";

        JsonObject createCodeRequestBody = new JsonObject();
        createCodeRequestBody.addProperty("phoneNumber", "+442071838750");

        JsonObject createResp = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/signinup/code", createCodeRequestBody, 1000, 1000, null,
                SemVer.v2_10.get(), "passwordless");

        JsonObject resendCodeRequestBody = new JsonObject();
        resendCodeRequestBody.addProperty("deviceId", createResp.get("deviceId").getAsString());
        resendCodeRequestBody.addProperty("userInputCode", exampleCode);

        JsonObject resendResp = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/signinup/code", resendCodeRequestBody, 1000, 1000, null,
                SemVer.v2_10.get(), "passwordless");

        checkResponse(resendResp, exampleCode);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testResendWithAlreadyUsedUserInputCode() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }
        String exampleCode = "codeYcode";

        JsonObject createCodeRequestBody = new JsonObject();
        createCodeRequestBody.addProperty("phoneNumber", "+442071838750");
        createCodeRequestBody.addProperty("userInputCode", exampleCode);

        JsonObject createResp = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/signinup/code", createCodeRequestBody, 1000, 1000, null,
                SemVer.v2_10.get(), "passwordless");

        JsonObject resendCodeRequestBody = new JsonObject();
        resendCodeRequestBody.addProperty("deviceId", createResp.get("deviceId").getAsString());
        resendCodeRequestBody.addProperty("userInputCode", exampleCode);

        JsonObject resendResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/signinup/code", resendCodeRequestBody, 1000, 1000, null,
                SemVer.v2_10.get(), "passwordless");

        assertEquals("USER_INPUT_CODE_ALREADY_USED_ERROR", resendResponse.get("status").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testResendWithNonExistantDeviceId() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject resendCodeRequestBody = new JsonObject();
        resendCodeRequestBody.addProperty("deviceId", "JWlE/V+Uz8qgaTyFkzOI4FfRrU6fBH85ve2GunoPpz0=");

        JsonObject resendResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/signinup/code", resendCodeRequestBody, 1000, 1000, null,
                SemVer.v2_10.get(), "passwordless");

        assertEquals("RESTART_FLOW_ERROR", resendResponse.get("status").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testAllIds() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject createCodeRequestBody = new JsonObject();
        createCodeRequestBody.addProperty("email", "test@example.com");
        createCodeRequestBody.addProperty("phoneNumber", "+442071838750");
        createCodeRequestBody.addProperty("deviceId", "+442071838750");

        HttpResponseException error = null;
        try {
            HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/signinup/code", createCodeRequestBody, 1000, 1000, null,
                    SemVer.v2_10.get(), "passwordless");
        } catch (HttpResponseException ex) {
            error = ex;
        }

        assertNotNull(error);
        assertEquals(400, error.statusCode);
        assertEquals(
                "Http error. Status Code: 400. Message: Please provide exactly one of email, phoneNumber or deviceId",
                error.getMessage());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testEmailAndPhone() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject createCodeRequestBody = new JsonObject();
        createCodeRequestBody.addProperty("email", "test@example.com");
        createCodeRequestBody.addProperty("phoneNumber", "+442071838750");

        HttpResponseException error = null;
        try {
            HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/signinup/code", createCodeRequestBody, 1000, 1000, null,
                    SemVer.v2_10.get(), "passwordless");
        } catch (HttpResponseException ex) {
            error = ex;
        }

        assertNotNull(error);
        assertEquals(400, error.statusCode);
        assertEquals(
                "Http error. Status Code: 400. Message: Please provide exactly one of email, phoneNumber or deviceId",
                error.getMessage());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testEmailAndDeviceId() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject createCodeRequestBody = new JsonObject();
        createCodeRequestBody.addProperty("email", "test@example.com");
        createCodeRequestBody.addProperty("deviceId", "+442071838750");

        HttpResponseException error = null;
        try {
            HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/signinup/code", createCodeRequestBody, 1000, 1000, null,
                    SemVer.v2_10.get(), "passwordless");
        } catch (HttpResponseException ex) {
            error = ex;
        }

        assertNotNull(error);
        assertEquals(400, error.statusCode);
        assertEquals(
                "Http error. Status Code: 400. Message: Please provide exactly one of email, phoneNumber or deviceId",
                error.getMessage());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testPhoneAndDeviceId() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject createCodeRequestBody = new JsonObject();
        createCodeRequestBody.addProperty("phoneNumber", "+442071838750");
        createCodeRequestBody.addProperty("deviceId", "+442071838750");

        HttpResponseException error = null;
        try {
            HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/signinup/code", createCodeRequestBody, 1000, 1000, null,
                    SemVer.v2_10.get(), "passwordless");
        } catch (HttpResponseException ex) {
            error = ex;
        }

        assertNotNull(error);
        assertEquals(400, error.statusCode);
        assertEquals(
                "Http error. Status Code: 400. Message: Please provide exactly one of email, phoneNumber or deviceId",
                error.getMessage());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * malformed deviceId -> BadRequest
     *
     * @throws Exception
     */
    @Test
    public void testMalformedDeviceId() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject createCodeRequestBody = new JsonObject();
        createCodeRequestBody.addProperty("deviceId", "+442071838750");

        HttpResponseException error = null;
        try {
            HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/signinup/code", createCodeRequestBody, 1000, 1000, null,
                    SemVer.v2_10.get(), "passwordless");
        } catch (HttpResponseException ex) {
            error = ex;
        }

        assertNotNull(error);
        assertEquals(400, error.statusCode);
        assertEquals("Http error. Status Code: 400. Message: Input encoding error in deviceId", error.getMessage());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * only userInputCode -> BadRequest
     *
     * @throws Exception
     */
    @Test
    public void testUserInputCode() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject createCodeRequestBody = new JsonObject();
        createCodeRequestBody.addProperty("userInputCode", "442071838750");

        HttpResponseException error = null;
        try {
            HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/signinup/code", createCodeRequestBody, 1000, 1000, null,
                    SemVer.v2_10.get(), "passwordless");
        } catch (HttpResponseException ex) {
            error = ex;
        }

        assertNotNull(error);
        assertEquals(400, error.statusCode);
        assertEquals(
                "Http error. Status Code: 400. Message: Please provide exactly one of email, phoneNumber or deviceId",
                error.getMessage());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testNoParams() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject createCodeRequestBody = new JsonObject();

        HttpResponseException error = null;
        try {
            HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/signinup/code", createCodeRequestBody, 1000, 1000, null,
                    SemVer.v2_10.get(), "passwordless");
        } catch (HttpResponseException ex) {
            error = ex;
        }

        assertNotNull(error);
        assertEquals(400, error.statusCode);
        assertEquals(
                "Http error. Status Code: 400. Message: Please provide exactly one of email, phoneNumber or deviceId",
                error.getMessage());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    private void checkResponse(JsonObject response) {
        this.checkResponse(response, null);
    }

    private void checkResponse(JsonObject response, String userInputCode) {
        assertEquals("OK", response.get("status").getAsString());
        assertEquals(8, response.entrySet().size());
        assert (response.has("preAuthSessionId"));
        byte[] deviceIdHashBytes = Base64.getUrlDecoder().decode(response.get("preAuthSessionId").getAsString());
        assertEquals(32, deviceIdHashBytes.length);
        assert (response.has("codeId"));
        // This tests that it is actually a UUID
        UUID.fromString(response.get("codeId").getAsString());

        assert (response.has("deviceId"));
        byte[] deviceIdBytes = Base64.getDecoder().decode(response.get("deviceId").getAsString());
        assertEquals(32, deviceIdBytes.length);

        assert (response.has("userInputCode"));
        String respUserInputCode = response.get("userInputCode").getAsString();
        if (userInputCode == null) {
            assertEquals(6, respUserInputCode.length());
        } else {
            assertEquals(userInputCode, respUserInputCode);
        }
        byte[] linkCodeBytes = Base64.getUrlDecoder().decode(response.get("linkCode").getAsString());
        assertEquals(32, linkCodeBytes.length);
        assert (response.has("linkCode"));
        assert ((System.currentTimeMillis() - 200L) < response.get("timeCreated").getAsLong());
        assertEquals(900000, response.get("codeLifetime").getAsLong());
    }
}
