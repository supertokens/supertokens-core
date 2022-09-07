/*
 *    Copyright (c) 2022, VRAI Labs and/or its affiliates. All rights reserved.
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
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.HttpRequestTest;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ImportUserWithPasswordHashAPITest {
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
    public void badInputTest() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // do not pass input
        try {
            HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user/passwordhash/import", null, 1000, 1000, null,
                    Utils.getCdiVersion2_16ForTests(), "emailpassword");
            throw new Exception("Should not come here");
        } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
            assertTrue(e.statusCode == 400
                    && e.getMessage().equals("Http error. Status Code: 400. Message: Invalid Json Input"));
        }

        // pass empty json body
        try {

            JsonObject requestBody = new JsonObject();
            HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user/passwordhash/import", requestBody, 1000, 1000, null,
                    Utils.getCdiVersion2_16ForTests(), "emailpassword");
            throw new Exception("Should not come here");
        } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
            assertTrue(e.statusCode == 400 && e.getMessage()
                    .equals("Http error. Status Code: 400. Message: Field name 'email' is invalid in JSON input"));
        }

        // pass empty json body
        try {
            JsonObject requestBody = new JsonObject();
            HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user/passwordhash/import", requestBody, 1000, 1000, null,
                    Utils.getCdiVersion2_16ForTests(), "emailpassword");
            throw new Exception("Should not come here");
        } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
            assertTrue(e.statusCode == 400 && e.getMessage()
                    .equals("Http error. Status Code: 400. Message: Field name 'email' is invalid in JSON input"));
        }

        // missing email in request body
        try {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("passwordHash", "somePasswordHash");
            HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user/passwordhash/import", requestBody, 1000, 1000, null,
                    Utils.getCdiVersion2_16ForTests(), "emailpassword");
            throw new Exception("Should not come here");
        } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
            assertTrue(e.statusCode == 400 && e.getMessage()
                    .equals("Http error. Status Code: 400. Message: Field name 'email' is invalid in JSON input"));
        }

        // missing passwordHash
        try {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("email", "test@example.com");
            HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user/passwordhash/import", requestBody, 1000, 1000, null,
                    Utils.getCdiVersion2_16ForTests(), "emailpassword");
            throw new Exception("Should not come here");
        } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
            assertTrue(e.statusCode == 400 && e.getMessage().equals(
                    "Http error. Status Code: 400. Message: Field name 'passwordHash' is invalid in JSON input"));
        }

        // passing an empty passwordHash
        try {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("email", "test@example.com");
            requestBody.addProperty("passwordHash", "");
            HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user/passwordhash/import", requestBody, 1000, 1000, null,
                    Utils.getCdiVersion2_16ForTests(), "emailpassword");
            throw new Exception("Should not come here");
        } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
            assertTrue(e.statusCode == 400 && e.getMessage()
                    .equals("Http error. Status Code: 400. Message: Password hash cannot be an empty string"));
        }

        // passing a random string as passwordHash/ invalid format
        try {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("email", "test@example.com");
            requestBody.addProperty("passwordHash", "invalidPasswordHash");
            HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user/passwordhash/import", requestBody, 1000, 1000, null,
                    Utils.getCdiVersion2_16ForTests(), "emailpassword");
            throw new Exception("Should not come here");
        } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
            assertTrue(e.statusCode == 400 && e.getMessage()
                    .equals("Http error. Status Code: 400. Message: Password Hash is not in Bcrypt or Argon2 format"));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
