/*
 *    Copyright (c) 2023, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.test.thirdparty.api;

import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.emailverification.EmailVerification;
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

public class EmailVerificationTest {
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
    public void testEmailVerificationOnSignInUp_v3_0() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        {
            JsonObject emailObject = new JsonObject();
            emailObject.addProperty("id", "test@example.com");

            JsonObject signUpRequestBody = new JsonObject();
            signUpRequestBody.addProperty("thirdPartyId", "google");
            signUpRequestBody.addProperty("thirdPartyUserId", "google-user");
            signUpRequestBody.add("email", emailObject);

            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/signinup", signUpRequestBody, 1000, 1000, null,
                    SemVer.v3_0.get(), "thirdparty");

            String userId = response.get("user").getAsJsonObject().get("id").getAsString();
            assertFalse(EmailVerification.isEmailVerified(process.getProcess(), userId, "test@example.com"));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testEmailVerificationOnSignInUp_v4_0() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        { // Expects emailVerified in emailObject
            JsonObject emailObject = new JsonObject();
            emailObject.addProperty("id", "test@example.com");

            JsonObject signUpRequestBody = new JsonObject();
            signUpRequestBody.addProperty("thirdPartyId", "google");
            signUpRequestBody.addProperty("thirdPartyUserId", "google-user");
            signUpRequestBody.add("email", emailObject);

            try {
                JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/signinup", signUpRequestBody, 1000, 1000, null,
                        SemVer.v4_0.get(), "thirdparty");
                fail();
            } catch (HttpResponseException e) {
                assertEquals(400, e.statusCode);
                assertTrue(e.getMessage().contains("Http error. Status Code: 400. Message: Field name 'isVerified' is invalid in JSON input"));
            }
        }

        {
            JsonObject emailObject = new JsonObject();
            emailObject.addProperty("id", "test@example.com");
            emailObject.addProperty("isVerified", true);

            JsonObject signUpRequestBody = new JsonObject();
            signUpRequestBody.addProperty("thirdPartyId", "google");
            signUpRequestBody.addProperty("thirdPartyUserId", "google-user");
            signUpRequestBody.add("email", emailObject);

            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/signinup", signUpRequestBody, 1000, 1000, null,
                    SemVer.v4_0.get(), "thirdparty");

            String userId = response.get("user").getAsJsonObject().get("id").getAsString();
            assertTrue(EmailVerification.isEmailVerified(process.getProcess(), userId, "test@example.com"));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
