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
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.httpRequest.HttpResponseException;
import io.supertokens.utils.SemVer;
import org.junit.*;
import org.junit.rules.TestRule;

import java.io.IOException;

public class UnverifyEmailAPITest2_8 {
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
    public void testThrowBadRequestRequest() throws Exception {
        TestingProcessManager.withProcess(process -> {
            Main main = process.getProcess();

            if (StorageLayer.getStorage(main).getType() != STORAGE_TYPE.SQL) {
                return;
            }

            // given
            JsonObject emptyBody = new JsonObject();

            try {
                // when
                unverifyEmail(main, emptyBody);
                // then
            } catch (HttpResponseException e) {
                Assert.assertEquals(400, e.statusCode);
            }
        });
    }

    @Test
    public void testSucceedUnverifyEmail() throws Exception {
        TestingProcessManager.withProcess(process -> {
            Main main = process.getProcess();

            if (StorageLayer.getStorage(main).getType() != STORAGE_TYPE.SQL) {
                return;
            }

            // given
            JsonObject body = new JsonObject();
            body.addProperty("userId", "mockUserId");
            body.addProperty("email", "john.doe@example.com");

            String token = EmailVerification.generateEmailVerificationToken(main, "mockUserId", "john.doe@example.com");
            EmailVerification.verifyEmail(main, token);

            // when
            JsonObject response = unverifyEmail(main, body);

            String responseStatus = response.get("status").getAsString();

            // then
            Assert.assertEquals("OK", responseStatus);
        });
    }

    private JsonObject unverifyEmail(Main main, JsonObject body) throws IOException, HttpResponseException {
        return HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                "http://localhost:3567/recipe/user/email/verify/remove", body, 1000, 1000, null,
                SemVer.v2_8.get(), RECIPE_ID.EMAIL_VERIFICATION.toString());
    }
}
