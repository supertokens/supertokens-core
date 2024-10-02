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

package io.supertokens.test.userIdMapping.api;

import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.useridmapping.UserIdMapping;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.httpRequest.HttpResponseException;
import io.supertokens.useridmapping.UserIdType;
import io.supertokens.utils.SemVer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.*;

public class UpdateExternalUserIdInfoTest {
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
    public void testBadInput() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // pass no input
        {
            try {
                HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/userid/external-user-id-info", new JsonObject(), 1000, 1000, null,
                        SemVer.v2_15.get(), "useridmapping");
                throw new Exception("should not come here");
            } catch (HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals(
                        "Http error. Status Code: 400. Message:" + " Field name 'userId' is invalid in JSON input"));
            }
        }

        // pass userId as invalid type
        {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("userId", 1);
            requestBody.addProperty("userIdType", "SUPERTOKENS");
            requestBody.addProperty("externalUserIdInfo", "newInfo");

            try {
                HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/userid/external-user-id-info", requestBody, 1000, 1000, null,
                        SemVer.v2_15.get(), "useridmapping");
                throw new Exception("should not come here");
            } catch (HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals(
                        "Http error. Status Code: 400. Message:" + " Field name 'userId' is invalid in JSON input"));
            }
        }

        // pass userId as an empty string
        {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("userId", "");
            requestBody.addProperty("userIdType", "SUPERTOKENS");
            requestBody.addProperty("externalUserIdInfo", "newInfo");

            try {
                HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/userid/external-user-id-info", requestBody, 1000, 1000, null,
                        SemVer.v2_15.get(), "useridmapping");
                throw new Exception("should not come here");
            } catch (HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals(
                        "Http error. Status Code: 400. Message:" + " Field name 'userId' cannot be an empty String"));
            }
        }

        // pass userIdType as an empty string
        {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("userId", "testUserId");
            requestBody.addProperty("userIdType", "");
            requestBody.addProperty("externalUserIdInfo", "newInfo");

            try {
                HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/userid/external-user-id-info", requestBody, 1000, 1000, null,
                        SemVer.v2_15.get(), "useridmapping");
                throw new Exception("should not come here");
            } catch (HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals("Http error. Status Code: 400. Message:"
                        + " Field name 'userIdType' should be one of 'SUPERTOKENS', 'EXTERNAL' or 'ANY'"));
            }
        }

        // pass userIdType as a random string
        {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("userId", "testUserId");
            requestBody.addProperty("userIdType", "random");
            requestBody.addProperty("externalUserIdInfo", "newInfo");

            try {
                HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/userid/external-user-id-info", requestBody, 1000, 1000, null,
                        SemVer.v2_15.get(), "useridmapping");
                throw new Exception("should not come here");
            } catch (HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals("Http error. Status Code: 400. Message:"
                        + " Field name 'userIdType' should be one of 'SUPERTOKENS', 'EXTERNAL' or 'ANY'"));
            }
        }

        // not passing externalUserIdInfo
        {
            JsonObject response = new JsonObject();
            response.addProperty("userId", "testUserId");
            response.addProperty("userIdType", "SUPERTOKENS");

            try {
                HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/userid/external-user-id-info", response, 1000, 1000, null,
                        SemVer.v2_15.get(), "useridmapping");
                throw new Exception("should not come here");
            } catch (HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals("Http error. Status Code: 400. Message:"
                        + " Field name 'externalUserIdInfo' is invalid in JSON input"));
            }
        }

        // passing externalUserIdInfo as an empty string
        {
            JsonObject response = new JsonObject();
            response.addProperty("userId", "testUserId");
            response.addProperty("userIdType", "SUPERTOKENS");
            response.addProperty("externalUserIdInfo", "");

            try {
                HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/userid/external-user-id-info", response, 1000, 1000, null,
                        SemVer.v2_15.get(), "useridmapping");
                throw new Exception("should not come here");
            } catch (HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals("Http error. Status Code: 400. Message:"
                        + " Field name 'externalUserIdInfo' cannot be an empty String"));
            }
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testUpdatingExternalUserIdInfoWithUnknownUserId() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        {
            // with userIdType SUPERTOKENS
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("userId", "unknown");
            requestBody.addProperty("userIdType", "SUPERTOKENS");
            requestBody.addProperty("externalUserIdInfo", "info");

            JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/userid/external-user-id-info", requestBody, 1000, 1000, null,
                    SemVer.v2_15.get(), "useridmapping");
            assertEquals(1, response.entrySet().size());
            assertEquals("UNKNOWN_MAPPING_ERROR", response.get("status").getAsString());
        }

        {
            // with userIdType EXTERNAL
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("userId", "unknown");
            requestBody.addProperty("userIdType", "EXTERNAL");
            requestBody.addProperty("externalUserIdInfo", "info");

            JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/userid/external-user-id-info", requestBody, 1000, 1000, null,
                    SemVer.v2_15.get(), "useridmapping");
            assertEquals(1, response.entrySet().size());
            assertEquals("UNKNOWN_MAPPING_ERROR", response.get("status").getAsString());
        }

        {
            // with userIdType ANY
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("userId", "unknown");
            requestBody.addProperty("userIdType", "ANY");
            requestBody.addProperty("externalUserIdInfo", "info");

            JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/userid/external-user-id-info", requestBody, 1000, 1000, null,
                    SemVer.v2_15.get(), "useridmapping");
            assertEquals(1, response.entrySet().size());
            assertEquals("UNKNOWN_MAPPING_ERROR", response.get("status").getAsString());
        }

        {
            // not passing userIdType ANY
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("userId", "unknown");
            requestBody.addProperty("externalUserIdInfo", "info");

            JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/userid/external-user-id-info", requestBody, 1000, 1000, null,
                    SemVer.v2_15.get(), "useridmapping");
            assertEquals(1, response.entrySet().size());
            assertEquals("UNKNOWN_MAPPING_ERROR", response.get("status").getAsString());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testUpdatingExternalUserIdInfoWithSuperTokensUserId() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create userId mapping with externalUserIdInfo
        String externalUserIdInfo = "externalUserIdInfo";
        AuthRecipeUserInfo userInfo = EmailPassword.signUp(process.main, "test@example.com", "testPass123");
        UserIdMapping userIdMapping = new io.supertokens.pluginInterface.useridmapping.UserIdMapping(
                userInfo.getSupertokensUserId(),
                "externalUserIdInfo", externalUserIdInfo);

        Utils.createUserIdMappingAndCheckThatItExists(process.main, userIdMapping);

        {
            // update mapping with new externalUserIdInfo with userIdType SUPERTOKENS
            String newExternalUserIdInfo = "externalUserIdInfo_1";
            UserIdMapping updatedUserIdMapping = new UserIdMapping(userIdMapping.superTokensUserId,
                    userIdMapping.externalUserId, newExternalUserIdInfo);

            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("userId", updatedUserIdMapping.superTokensUserId);
            requestBody.addProperty("userIdType", "SUPERTOKENS");
            requestBody.addProperty("externalUserIdInfo", updatedUserIdMapping.externalUserIdInfo);

            JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/userid/external-user-id-info", requestBody, 1000, 1000, null,
                    SemVer.v2_15.get(), "useridmapping");
            assertEquals(1, response.entrySet().size());
            assertEquals("OK", response.get("status").getAsString());

            // retrieve mapping and check that externalUserIdInfo got updated
            UserIdMapping retrievedMapping = io.supertokens.useridmapping.UserIdMapping.getUserIdMapping(process.main,
                    userIdMapping.superTokensUserId, UserIdType.SUPERTOKENS);
            assertNotNull(retrievedMapping);
            assertEquals(updatedUserIdMapping, retrievedMapping);
        }

        {
            // update mapping with new externalUserIdInfo with userIdType ANY
            String newExternalUserIdInfo = "externalUserIdInfo_2";
            UserIdMapping updatedUserIdMapping = new UserIdMapping(userIdMapping.superTokensUserId,
                    userIdMapping.externalUserId, newExternalUserIdInfo);

            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("userId", updatedUserIdMapping.superTokensUserId);
            requestBody.addProperty("userIdType", "ANY");
            requestBody.addProperty("externalUserIdInfo", updatedUserIdMapping.externalUserIdInfo);

            JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/userid/external-user-id-info", requestBody, 1000, 1000, null,
                    SemVer.v2_15.get(), "useridmapping");
            assertEquals(1, response.entrySet().size());
            assertEquals("OK", response.get("status").getAsString());

            // retrieve mapping and check that externalUserIdInfo got updated
            UserIdMapping retrievedMapping = io.supertokens.useridmapping.UserIdMapping.getUserIdMapping(process.main,
                    userIdMapping.superTokensUserId, UserIdType.SUPERTOKENS);
            assertNotNull(retrievedMapping);
            assertEquals(updatedUserIdMapping, retrievedMapping);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testUpdatingExternalUserIdInfoWithExternalUserId() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create userId mapping with externalUserIdInfo
        String externalUserIdInfo = "externalUserIdInfo";
        AuthRecipeUserInfo userInfo = EmailPassword.signUp(process.main, "test@example.com", "testPass123");
        UserIdMapping userIdMapping = new io.supertokens.pluginInterface.useridmapping.UserIdMapping(
                userInfo.getSupertokensUserId(),
                "externalUserIdInfo", externalUserIdInfo);

        Utils.createUserIdMappingAndCheckThatItExists(process.main, userIdMapping);

        {
            // update mapping with new externalUserIdInfo with userIdType External
            String newExternalUserIdInfo = "externalUserIdInfo_1";
            UserIdMapping updatedUserIdMapping = new UserIdMapping(userIdMapping.superTokensUserId,
                    userIdMapping.externalUserId, newExternalUserIdInfo);

            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("userId", updatedUserIdMapping.externalUserId);
            requestBody.addProperty("userIdType", "EXTERNAL");
            requestBody.addProperty("externalUserIdInfo", updatedUserIdMapping.externalUserIdInfo);

            JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/userid/external-user-id-info", requestBody, 1000, 1000, null,
                    SemVer.v2_15.get(), "useridmapping");
            assertEquals(1, response.entrySet().size());
            assertEquals("OK", response.get("status").getAsString());

            // retrieve mapping and check that externalUserIdInfo got updated
            UserIdMapping retrievedMapping = io.supertokens.useridmapping.UserIdMapping.getUserIdMapping(process.main,
                    userIdMapping.superTokensUserId, UserIdType.SUPERTOKENS);
            assertNotNull(retrievedMapping);
            assertEquals(updatedUserIdMapping, retrievedMapping);
        }

        {
            // update mapping with new externalUserIdInfo with userIdType ANY
            String newExternalUserIdInfo = "externalUserIdInfo_2";
            UserIdMapping updatedUserIdMapping = new UserIdMapping(userIdMapping.superTokensUserId,
                    userIdMapping.externalUserId, newExternalUserIdInfo);

            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("userId", updatedUserIdMapping.externalUserId);
            requestBody.addProperty("userIdType", "ANY");
            requestBody.addProperty("externalUserIdInfo", updatedUserIdMapping.externalUserIdInfo);

            JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/userid/external-user-id-info", requestBody, 1000, 1000, null,
                    SemVer.v2_15.get(), "useridmapping");
            assertEquals(1, response.entrySet().size());
            assertEquals("OK", response.get("status").getAsString());

            // retrieve mapping and check that externalUserIdInfo got updated
            UserIdMapping retrievedMapping = io.supertokens.useridmapping.UserIdMapping.getUserIdMapping(process.main,
                    userIdMapping.superTokensUserId, UserIdType.SUPERTOKENS);
            assertNotNull(retrievedMapping);
            assertEquals(updatedUserIdMapping, retrievedMapping);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testDeletingExternalUserIdInfo() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create userId mapping with externalUserIdInfo
        String externalUserIdInfo = "externalUserIdInfo";
        AuthRecipeUserInfo userInfo = EmailPassword.signUp(process.main, "test@example.com", "testPass123");
        UserIdMapping userIdMapping = new io.supertokens.pluginInterface.useridmapping.UserIdMapping(
                userInfo.getSupertokensUserId(),
                "externalUserIdInfo", externalUserIdInfo);

        Utils.createUserIdMappingAndCheckThatItExists(process.main, userIdMapping);

        // delete mapping by passing externalUserIdInfo as JSON null
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("userId", userIdMapping.superTokensUserId);
        requestBody.add("externalUserIdInfo", null);

        JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/userid/external-user-id-info", requestBody, 1000, 1000, null,
                SemVer.v2_15.get(), "useridmapping");
        assertEquals(1, response.entrySet().size());
        assertEquals("OK", response.get("status").getAsString());

        // retrieve mapping and check that externalUserIdInfo is null
        UserIdMapping retrievedMapping = io.supertokens.useridmapping.UserIdMapping.getUserIdMapping(process.main,
                userIdMapping.superTokensUserId, UserIdType.ANY);
        assertNotNull(retrievedMapping);
        assertEquals(userIdMapping.superTokensUserId, retrievedMapping.superTokensUserId);
        assertEquals(userIdMapping.externalUserId, retrievedMapping.externalUserId);
        assertNull(retrievedMapping.externalUserIdInfo);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
