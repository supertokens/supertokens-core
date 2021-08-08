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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.emailverification.EmailVerification;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.httpRequest.HttpRequest;
import io.supertokens.test.httpRequest.HttpResponseException;
import org.junit.*;
import org.junit.rules.TestRule;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.fail;

public class GetTokensForUserAPITest2_8 {
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
    public void testThrowWhenMissingUserIdOrEmail() throws Exception {
        TestingProcessManager.withProcess(process -> {
            Main main = process.getProcess();

            if (StorageLayer.getStorage(main).getType() != STORAGE_TYPE.SQL) {
                return;
            }

            try {
                Map<String, String> query = new HashMap<>();

                getTokensForUser(main, query);
                fail("Test function should throw, but it didn't");
            } catch (HttpResponseException e) {
                Assert.assertEquals(400, e.statusCode);
            }

            try {
                Map<String, String> query = new HashMap<>();

                query.put("email", "john.doe@example.com");

                getTokensForUser(main, query);
            } catch (HttpResponseException e) {
                Assert.assertEquals(e.statusCode, 400);
                Assert.assertTrue(e.getMessage().contains("userId"));
            }

            try {
                Map<String, String> query = new HashMap<>();

                query.put("userId", "mockUserId");

                getTokensForUser(main, query);
                fail("Test function should throw, but it didn't");
            } catch (HttpResponseException e) {
                Assert.assertEquals(e.statusCode, 400);
                Assert.assertTrue(e.getMessage().contains("email"));
            }
        });
    }

    @Test
    public void testNotReturnTokenInfosWhenParametersDontMatch() throws Exception {
        TestingProcessManager.withProcess(process -> {
            Main main = process.getProcess();

            if (StorageLayer.getStorage(main).getType() != STORAGE_TYPE.SQL) {
                return;
            }

            // userId mismatch
            {
                // given
                EmailVerification.generateEmailVerificationToken(main, "mockUserId", "john.doe@example.com");

                Map<String, String> query = new HashMap<>();

                query.put("userId", "mismatchingUserId");
                query.put("email", "john.doe@example.com");

                // when
                JsonObject response = getTokensForUser(main, query);

                String status = response.get("status").getAsString();
                JsonArray tokens = response.get("tokens").getAsJsonArray();

                // then
                Assert.assertEquals("OK", status);
                Assert.assertEquals(0, tokens.size());
            }

            // email mismatch
            {
                // given
                EmailVerification.generateEmailVerificationToken(main, "mockUserId", "john.doe@example.com");

                Map<String, String> query = new HashMap<>();

                query.put("userId", "mockUserId");
                query.put("email", "dave.doe@example.com");

                // when
                JsonObject response = getTokensForUser(main, query);

                String status = response.get("status").getAsString();
                JsonArray tokens = response.get("tokens").getAsJsonArray();

                // then
                Assert.assertEquals("OK", status);
                Assert.assertEquals(0, tokens.size());
            }
        });
    }

    @Test
    public void testReturnArrayOfTokenInfos() throws Exception {
        TestingProcessManager.withProcess(process -> {
            Main main = process.getProcess();

            if (StorageLayer.getStorage(main).getType() != STORAGE_TYPE.SQL) {
                return;
            }

            // given
            EmailVerification.generateEmailVerificationToken(main, "mockUserId", "john.doe@example.com");
            EmailVerification.generateEmailVerificationToken(main, "mockUserId", "john.doe@example.com");

            Map<String, String> query = new HashMap<>();

            query.put("userId", "mockUserId");
            query.put("email", "john.doe@example.com");

            // when
            JsonObject response = getTokensForUser(main, query);

            String status = response.get("status").getAsString();
            JsonArray tokens = response.get("tokens").getAsJsonArray();

            // then
            Assert.assertEquals("OK", status);
            Assert.assertEquals(2, tokens.size());
            tokens.forEach(token -> {
                String email = token.getAsJsonObject().get("email").getAsString();
                String userId = token.getAsJsonObject().get("userId").getAsString();

                Assert.assertEquals("john.doe@example.com", email);
                Assert.assertEquals("mockUserId", userId);
            });
        });
    }

    private JsonObject getTokensForUser(Main main, Map<String, String> query) throws IOException, HttpResponseException {
        return HttpRequest.sendGETRequest(main, "", "http://localhost:3567/recipe/user/email/tokens", query,
                        1000,
                        1000, null, Utils.getCdiVersion2_8ForTests(), RECIPE_ID.EMAIL_VERIFICATION.toString());
    }
}
