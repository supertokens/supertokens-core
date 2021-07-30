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

import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.httpRequest.HttpResponseException;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.assertEquals;

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
    public void testFailRequestIfBadBody() throws Exception {
        TestingProcessManager.withProcess(process -> {
            Main main = process.getProcess();

            if (StorageLayer.getStorage(main).getType() != STORAGE_TYPE.SQL) {
                return;
            }

            try {
                JsonObject bodyWithoutUserId = new JsonObject();

                io.supertokens.test.httpRequest.HttpRequest.sendJsonPOSTRequest(
                        main,
                        "",
                        "http://localhost:3567/user/remove",
                        bodyWithoutUserId,
                        1000,
                        1000,
                        null,
                        Utils.getCdiVersion2_8ForTests(),
                        "");

            } catch (HttpResponseException e) {
                assertEquals(400, e.statusCode);
                assertEquals("Http error. Status Code: 400. Message: Field name 'userId' is invalid in JSON input", e.getMessage());
            }
        });
    }

    @Test
    public void testReturnErrorWhenUserIdDoesntExist() throws Exception {
        TestingProcessManager.withProcess(process -> {
            if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
                return;
            }

            // given
            // there's no user in database

            // when
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("userId", "inexistent user id");

            JsonObject response = io.supertokens.test.httpRequest.HttpRequest.sendJsonPOSTRequest(
                    process.getProcess(),
                    "",
                    "http://localhost:3567/user/remove",
                    requestBody,
                    1000,
                    1000,
                    null,
                    Utils.getCdiVersion2_8ForTests(),
                    ""
            );

            String status = response.get("status").getAsString();

            // then
            assertEquals("UNKNOWN_USER_ID_ERROR", status);
        });
    }
}
