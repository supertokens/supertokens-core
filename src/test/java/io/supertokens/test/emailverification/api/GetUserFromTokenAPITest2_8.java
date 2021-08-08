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

package io.supertokens.test.emailverification.api;

import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.emailverification.EmailVerification;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequest;
import io.supertokens.test.httpRequest.HttpResponseException;
import org.junit.*;
import org.junit.rules.TestRule;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class GetUserFromTokenAPITest2_8 {
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
    public void testReturnErrorWhenTokenDoesntExist() throws Exception {
        TestingProcessManager.withProcess(process -> {
            Main main = process.getProcess();

            if (StorageLayer.getStorage(main).getType() != STORAGE_TYPE.SQL) {
                return;
            }

            // given there's no token

            // when
            JsonObject response = getUserByToken(main, "inexistent token");

            String responseStatus = response.get("status").getAsString();

            // then
            Assert.assertEquals("EMAIL_VERIFICATION_INVALID_TOKEN_ERROR", responseStatus);
            Assert.assertNull(response.get("user"));
        });
    }

    @Test
    public void testReturnUserWhenTokenExists() throws Exception {
        TestingProcessManager.withProcess(process -> {
            Main main = process.getProcess();

            if (StorageLayer.getStorage(main).getType() != STORAGE_TYPE.SQL) {
                return;
            }

            // given
            String token = EmailVerification.generateEmailVerificationToken(main, "mockUserId", "john.doe@example.com");

            // when
            JsonObject response = getUserByToken(main, token);
            String responseStatus = response.get("status").getAsString();
            JsonObject responseUser = response.get("user").getAsJsonObject();

            String userId = responseUser.get("id").getAsString();
            String email = responseUser.get("email").getAsString();

            // then
            Assert.assertEquals("OK", responseStatus);
            Assert.assertEquals("mockUserId", userId);
            Assert.assertEquals("john.doe@example.com", email);
        });
    }

    private JsonObject getUserByToken(Main main, String token) throws IOException, HttpResponseException {
        Map<String, String> query = new HashMap<>();

        query.put("token", token);

        return HttpRequest.sendGETRequest(main, "",
                "http://localhost:3567/recipe/user/email", query, 1000,
                1000,
                null, Utils.getCdiVersion2_8ForTests(), RECIPE_ID.EMAIL_VERIFICATION.toString());
    }
}
