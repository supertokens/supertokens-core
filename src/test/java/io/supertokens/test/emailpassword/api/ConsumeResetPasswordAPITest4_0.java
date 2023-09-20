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
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.useridmapping.UserIdMapping;
import io.supertokens.utils.SemVer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.*;

public class ConsumeResetPasswordAPITest4_0 {

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
                        "http://localhost:3567/recipe/user/password/reset/token/consume", null, 1000, 1000, null,
                        SemVer.v4_0.get(), "emailpassword");
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
                        "http://localhost:3567/recipe/user/password/reset/token/consume", requestBody, 1000, 1000, null,
                        SemVer.v4_0.get(), "emailpassword");
                throw new Exception("Should not come here");
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals(
                        "Http error. Status Code: 400. Message: Field name 'token' is invalid in " + "JSON input"));
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

        AuthRecipeUserInfo user = EmailPassword.signUp(process.main, "random@gmail.com", "validPass123");

        String userId = user.getSupertokensUserId();

        String token = EmailPassword.generatePasswordResetToken(process.main, userId, "random@gmail.com");

        JsonObject resetPasswordBody = new JsonObject();
        resetPasswordBody.addProperty("token", token);

        JsonObject passwordResetResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/user/password/reset/token/consume", resetPasswordBody, 1000, 1000, null,
                SemVer.v4_0.get(), "emailpassword");
        assertEquals(passwordResetResponse.get("status").getAsString(), "OK");
        assertEquals(passwordResetResponse.get("email").getAsString(), "random@gmail.com");
        assertEquals(passwordResetResponse.get("userId").getAsString(), user.getSupertokensUserId());
        assertEquals(passwordResetResponse.entrySet().size(), 3);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testGoodInputWithUserIdMapping() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        AuthRecipeUserInfo user = EmailPassword.signUp(process.main, "random@gmail.com", "validPass123");
        UserIdMapping.createUserIdMapping(process.main, user.getSupertokensUserId(), "e1", null, false);

        String userId = user.getSupertokensUserId();

        String token = EmailPassword.generatePasswordResetToken(process.main, userId, "random@gmail.com");

        JsonObject resetPasswordBody = new JsonObject();
        resetPasswordBody.addProperty("token", token);

        JsonObject passwordResetResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/user/password/reset/token/consume", resetPasswordBody, 1000, 1000, null,
                SemVer.v4_0.get(), "emailpassword");
        assertEquals(passwordResetResponse.get("status").getAsString(), "OK");
        assertEquals(passwordResetResponse.get("email").getAsString(), "random@gmail.com");
        assertEquals(passwordResetResponse.get("userId").getAsString(), "e1");
        assertEquals(passwordResetResponse.entrySet().size(), 3);

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
        resetPasswordBody.addProperty("token", "randomToken");

        JsonObject passwordResetResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/user/password/reset/token/consume", resetPasswordBody, 1000, 1000, null,
                SemVer.v4_0.get(), "emailpassword");

        assertEquals(passwordResetResponse.get("status").getAsString(), "RESET_PASSWORD_INVALID_TOKEN_ERROR");
        assertEquals(passwordResetResponse.entrySet().size(), 1);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
