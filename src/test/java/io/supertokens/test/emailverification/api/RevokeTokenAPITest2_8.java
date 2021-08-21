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
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpResponseException;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.io.IOException;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.fail;

public class RevokeTokenAPITest2_8 {
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
    public void testThrowBadRequest() throws Exception {
        TestingProcessManager.withProcess(process -> {
            if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
                return;
            }

            Main main = process.getProcess();

            try {
                JsonObject emptyBody = new JsonObject();
                makeRequest(main, emptyBody);
                fail();
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                assertEquals(e.statusCode, 400);
            }
        });
    }

    @Test
    public void testRevokeTokenForValidParameters() throws Exception {
        TestingProcessManager.withProcess(process -> {
            Main main = process.getProcess();

            if (StorageLayer.getStorage(main).getType() != STORAGE_TYPE.SQL) {
                return;
            }

            JsonObject body = new JsonObject();
            body.addProperty("userId", "someUserId");
            body.addProperty("email", "someEmail@gmail.com");

            JsonObject response = makeRequest(main, body);
            String responseStatus = response.get("status").getAsString();

            assertEquals("OK", responseStatus);
        });
    }

    private JsonObject makeRequest(Main main, JsonObject body) throws IOException, HttpResponseException {
        return io.supertokens.test.httpRequest.HttpRequest.sendJsonPOSTRequest(main, "",
                "http://localhost:3567/recipe/user/email/verify/token/remove", body, 1000, 1000, null,
                Utils.getCdiVersion2_8ForTests(), RECIPE_ID.EMAIL_VERIFICATION.toString());
    }
}
