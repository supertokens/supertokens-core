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

package io.supertokens.test;

import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.httpRequest.HttpResponseException;
import io.supertokens.utils.SemVer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.*;

public class APITestsWithTrailingSlash {
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
    public void testTrailingSlashesWorks() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        {
            // test that sign up with trailing slash works
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("email", "test@example.com");
            requestBody.addProperty("password", "testPass123");

            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/signup/", requestBody, 1000, 1000, null,
                    SemVer.v2_16.get(), "emailpassword");

            assertEquals(response.get("status").getAsString(), "OK");
        }

        {
            // test that additional forward slashes are ignored
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("email", "test2@example.com");
            requestBody.addProperty("password", "testPass123");

            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/signup////////", requestBody, 1000, 1000, null,
                    SemVer.v2_16.get(), "emailpassword");

            assertEquals(response.get("status").getAsString(), "OK");
        }

        {
            // test that adding any path after the trailing slash throws 404
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("email", "test@example.com");
            requestBody.addProperty("password", "testPass123");

            HttpResponseException error = null;

            try {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/signup/random", requestBody, 1000, 1000, null,
                        SemVer.v2_16.get(), "emailpassword");
            } catch (HttpResponseException e) {
                error = e;
            }
            assertNotNull(error);
            assertEquals(error.statusCode, 404);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testThatAPISWorkWithTrailingSlashes() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        {
            // test user roles recipe, create role api
            JsonObject requestBody = new JsonObject();
            String role = "testRole";
            requestBody.addProperty("role", role);
            JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/role/", requestBody, 1000, 1000, null,
                    SemVer.v2_14.get(), "userroles");
            assertEquals(2, response.entrySet().size());
            assertEquals("OK", response.get("status").getAsString());
            assertTrue(response.get("createdNewRole").getAsBoolean());

        }

        {
            // test user metadata, create metadata

            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("userId", "someUserId");
            JsonObject metadata = new JsonObject();
            metadata.addProperty("someValue", "someData");
            requestBody.add("metadataUpdate", metadata);
            JsonObject resp = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user/metadata/", requestBody, 1000, 1000, null,
                    SemVer.v2_13.get(), "usermetadata");

            assertEquals("OK", resp.get("status").getAsString());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

}
