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
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.emailpassword.UserInfo;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.assertEquals;

public class UserPutAPITest2_8 {
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
    public void testQueryingOfUnknownUserId() throws Exception {
        TestingProcessManager.withProcess(process -> {
            if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
                return;
            }

            JsonObject body = new JsonObject();
            body.addProperty("userId", "someUserId");
            body.addProperty("email", "someemail@gmail.com");

            JsonObject response = io.supertokens.test.httpRequest.HttpRequest
                    .sendJsonPUTRequest(process.getProcess(), "",
                            "http://localhost:3567/recipe/user", body, 1000,
                            1000,
                            null, Utils.getCdiVersion2_8ForTests(), RECIPE_ID.EMAIL_PASSWORD.toString());

            assertEquals("UNKNOWN_USER_ID_ERROR", response.get("status").getAsString());
            assertEquals(1, response.entrySet().size());
        });
    }

    @Test
    public void testQueryingWithEmailThatAlreadyExists() throws Exception {
        TestingProcessManager.withProcess(process -> {
            if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
                return;
            }

            UserInfo user = EmailPassword.signUp(process.getProcess(), "someemail@gmail.com", "somePass");
            UserInfo user2 = EmailPassword.signUp(process.getProcess(), "someemail2@gmail.com", "somePass");


            JsonObject body = new JsonObject();
            body.addProperty("userId", user.id);
            body.addProperty("email", user2.email);

            JsonObject response = io.supertokens.test.httpRequest.HttpRequest
                    .sendJsonPUTRequest(process.getProcess(), "",
                            "http://localhost:3567/recipe/user", body, 1000,
                            1000,
                            null, Utils.getCdiVersion2_8ForTests(), RECIPE_ID.EMAIL_PASSWORD.toString());

            assertEquals("EMAIL_ALREADY_EXISTS_ERROR", response.get("status").getAsString());
            assertEquals(1, response.entrySet().size());
        });
    }

    @Test
    public void testQueryingWithoutEmailAndPassword() throws Exception {
        TestingProcessManager.withProcess(process -> {
            if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
                return;
            }

            JsonObject body = new JsonObject();
            body.addProperty("userId", "someUserId");

            try {
                io.supertokens.test.httpRequest.HttpRequest
                        .sendJsonPUTRequest(process.getProcess(), "",
                                "http://localhost:3567/recipe/user", body, 1000,
                                1000,
                                null, Utils.getCdiVersion2_8ForTests(), RECIPE_ID.EMAIL_PASSWORD.toString());

            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                assertEquals(400, e.statusCode);
                assertEquals("Http error. Status Code: 400. Message: You have to provide either email or password.",
                        e.getMessage());
            }
        });
    }

    @Test
    public void testSuccessfulUpdate() throws Exception {
        TestingProcessManager.withProcess(process -> {
            if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
                return;
            }

            UserInfo user = EmailPassword.signUp(process.getProcess(), "someemail@gmail.com", "somePass");

            JsonObject body = new JsonObject();
            body.addProperty("userId", user.id);
            body.addProperty("email", "someOtherEmail@gmail.com");

            JsonObject response = io.supertokens.test.httpRequest.HttpRequest
                    .sendJsonPUTRequest(process.getProcess(), "",
                            "http://localhost:3567/recipe/user", body, 1000,
                            1000,
                            null, Utils.getCdiVersion2_8ForTests(), RECIPE_ID.EMAIL_PASSWORD.toString());

            assertEquals("OK", response.get("status").getAsString());
            assertEquals(1, response.entrySet().size());
        });
    }

}
