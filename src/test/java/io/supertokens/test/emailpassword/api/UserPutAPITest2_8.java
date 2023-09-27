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
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
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

            JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user", body, 1000, 1000, null, SemVer.v2_8.get(),
                    RECIPE_ID.EMAIL_PASSWORD.toString());

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

            AuthRecipeUserInfo user = EmailPassword.signUp(process.getProcess(), "someemail@gmail.com", "somePass");
            AuthRecipeUserInfo user2 = EmailPassword.signUp(process.getProcess(), "someemail2@gmail.com", "somePass");

            JsonObject body = new JsonObject();
            body.addProperty("userId", user.getSupertokensUserId());
            body.addProperty("email", user2.loginMethods[0].email);

            JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user", body, 1000, 1000, null, SemVer.v2_8.get(),
                    RECIPE_ID.EMAIL_PASSWORD.toString());

            assertEquals("EMAIL_ALREADY_EXISTS_ERROR", response.get("status").getAsString());
            assertEquals(1, response.entrySet().size());
        });
    }

    @Test
    public void testUpdatingEmailNormalisesIt() throws Exception {
        TestingProcessManager.withProcess(process -> {
            if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
                return;
            }

            AuthRecipeUserInfo user = EmailPassword.signUp(process.getProcess(), "someemail@gmail.com", "somePass");

            JsonObject body = new JsonObject();
            body.addProperty("userId", user.getSupertokensUserId());
            body.addProperty("email", "someemail+TEST@gmail.com");

            JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user", body, 1000, 1000, null, SemVer.v2_8.get(),
                    RECIPE_ID.EMAIL_PASSWORD.toString());

            assertEquals("OK", response.get("status").getAsString());
            assertEquals(1, response.entrySet().size());

            EmailPassword.signIn(process.getProcess(), "someemail+test@gmail.com", "somePass");
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
                HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "", "http://localhost:3567/recipe/user",
                        body, 1000, 1000, null, SemVer.v2_8.get(), RECIPE_ID.EMAIL_PASSWORD.toString());

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

            AuthRecipeUserInfo user = EmailPassword.signUp(process.getProcess(), "someemail@gmail.com", "somePass");

            JsonObject body = new JsonObject();
            body.addProperty("userId", user.getSupertokensUserId());
            body.addProperty("email", "someOtherEmail@gmail.com");

            JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user", body, 1000, 1000, null, SemVer.v2_8.get(),
                    RECIPE_ID.EMAIL_PASSWORD.toString());

            assertEquals("OK", response.get("status").getAsString());
            assertEquals(1, response.entrySet().size());

            EmailPassword.signIn(process.main, "someotheremail@gmail.com", "somePass");
        });
    }

    @Test
    public void testSuccessfulUpdateWithOnlyPassword() throws Exception {
        TestingProcessManager.withProcess(process -> {
            if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
                return;
            }

            AuthRecipeUserInfo user = EmailPassword.signUp(process.getProcess(), "someemail@gmail.com", "somePass");

            JsonObject body = new JsonObject();
            body.addProperty("userId", user.getSupertokensUserId());
            body.addProperty("password", "somePass123");

            JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user", body, 1000, 1000, null, SemVer.v2_8.get(),
                    RECIPE_ID.EMAIL_PASSWORD.toString());

            assertEquals("OK", response.get("status").getAsString());
            assertEquals(1, response.entrySet().size());

            EmailPassword.signIn(process.main, "someemail@gmail.com", "somePass123");
        });
    }

}
