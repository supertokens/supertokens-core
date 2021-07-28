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

package io.supertokens.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.httpRequest.HttpResponseException;
import io.supertokens.test.session.api.SessionClient;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class DeleteUserAPITest2_8 {
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
    public void returnErrorWhenUserIdDoesntExist() throws Exception {
        TestingProcessManager.withProcess(process -> {
            if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
                return;
            }

            // given
            // there's no user in database

            // when
            JsonObject response = deleteUser(process, "inexistent user id");

            String status = response.get("status").getAsString();

            // then
            assertEquals("UNKNOWN_USER_ID_ERROR", status);
        });
    }

    @Test
    public void removeUsersSession() throws Exception {
        TestingProcessManager.withProcess(process -> {
            if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
                return;
            }

            // given
            JsonObject signUpResponse = Utils.signUpRequest_2_5(process, "john.doe@example.com", "password");
            JsonObject user = signUpResponse.getAsJsonObject("user");
            String userId = user.get("id").getAsString();

            SessionClient sessionClient = new SessionClient(process, Utils.getCdiVersion2_8ForTests());

            // session is created
            JsonObject createSessionBody = new JsonObject();
            createSessionBody.addProperty("userId", userId);
            createSessionBody.addProperty("enableAntiCsrf", false);
            createSessionBody.add("userDataInJWT", new JsonObject());
            createSessionBody.add("userDataInDatabase", new JsonObject());

            JsonObject session = sessionClient.createSession(createSessionBody);
            String accessToken = session
                    .getAsJsonObject("accessToken")
                    .get("token")
                    .getAsString();

            // session is valid
            JsonObject verifySessionBody = new JsonObject();
            verifySessionBody.addProperty("accessToken", accessToken);
            verifySessionBody.addProperty("enableAntiCsrf", false);
            verifySessionBody.addProperty("doAntiCsrfCheck", false);

            JsonObject verification = sessionClient.verifySession(verifySessionBody);
            String verificationStatus = verification.get("status").getAsString();

            assertEquals("OK", verificationStatus);

            // when
            deleteUser(process, "testUserId");

            verification = sessionClient.verifySession(verifySessionBody);
            verificationStatus = verification.get("status").getAsString();

            // then
            assertNotEquals("OK", verificationStatus);
        });
    }

    private JsonObject deleteUser(TestingProcessManager.TestingProcess process, String userId) throws IOException,
            HttpResponseException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("userId", userId);

        return io.supertokens.test.httpRequest.HttpRequest.sendJsonPOSTRequest(
                process.getProcess(),
                "",
                "http://localhost:3567/users/remove",
                requestBody,
                1000,
                1000,
                null,
                Utils.getCdiVersion2_8ForTests(),
                ""
        );
    }
}
