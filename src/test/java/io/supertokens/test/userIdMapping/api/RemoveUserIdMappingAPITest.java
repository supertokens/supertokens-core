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
import io.supertokens.usermetadata.UserMetadata;
import io.supertokens.utils.SemVer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static io.supertokens.test.Utils.createUserIdMappingAndCheckThatItExists;
import static org.junit.Assert.*;

public class RemoveUserIdMappingAPITest {
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

        // dont pass userId
        {
            try {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/userid/map/remove", new JsonObject(), 1000, 1000, null,
                        SemVer.v2_15.get(), "useridmapping");
                throw new Exception("should not come here");
            } catch (HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals(
                        "Http error. Status Code: 400. Message:" + " Field name 'userId' is invalid in JSON input"));
            }
        }

        // pass userId with invalid type
        {
            JsonObject request = new JsonObject();
            request.addProperty("userId", 1);

            try {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/userid/map/remove", new JsonObject(), 1000, 1000, null,
                        SemVer.v2_15.get(), "useridmapping");
                throw new Exception("should not come here");
            } catch (HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals(
                        "Http error. Status Code: 400. Message:" + " Field name 'userId' is invalid in JSON input"));
            }
        }

        // pass userId as an empty string
        {
            JsonObject request = new JsonObject();
            request.addProperty("userId", "");
            try {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/userid/map/remove", request, 1000, 1000, null,
                        SemVer.v2_15.get(), "useridmapping");
                throw new Exception("should not come here");
            } catch (HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals(
                        "Http error. Status Code: 400. Message:" + " Field name 'userId' cannot be an empty String"));
            }
        }

        // pass userIdType as a number
        {
            JsonObject request = new JsonObject();
            request.addProperty("userId", "testUserId");
            request.addProperty("userIdType", 1);
            try {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/userid/map/remove", request, 1000, 1000, null,
                        SemVer.v2_15.get(), "useridmapping");
                throw new Exception("should not come here");
            } catch (HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals("Http error. Status Code: 400. Message:"
                        + " Field name 'userIdType' is invalid in JSON input"));
            }
        }

        // pass userIdType as an empty string
        {
            JsonObject request = new JsonObject();
            request.addProperty("userId", "testUserId");
            request.addProperty("userIdType", "");
            try {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/userid/map/remove", request, 1000, 1000, null,
                        SemVer.v2_15.get(), "useridmapping");
                throw new Exception("should not come here");
            } catch (HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals("Http error. Status Code: 400. Message:"
                        + " Field name 'userIdType' should be one of 'SUPERTOKENS', 'EXTERNAL' or 'ANY'"));
            }
        }

        // pass userIdType with invalid string input
        {
            JsonObject request = new JsonObject();
            request.addProperty("userId", "testUserId");
            request.addProperty("userIdType", "random");

            try {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/userid/map/remove", request, 1000, 1000, null,
                        SemVer.v2_15.get(), "useridmapping");
                throw new Exception("should not come here");
            } catch (HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals("Http error. Status Code: 400. Message:"
                        + " Field name 'userIdType' should be one of 'SUPERTOKENS', 'EXTERNAL' or 'ANY'"));
            }
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testDeletingUserIdMappingsWithUnknownUserIds() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        {
            // delete unknown userId mapping with userIdType as SUPERTOKENS
            JsonObject request = new JsonObject();
            request.addProperty("userId", "unknown");
            request.addProperty("userIdType", "SUPERTOKENS");

            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/userid/map/remove", request, 1000, 1000, null,
                    SemVer.v2_15.get(), "useridmapping");
            assertEquals(2, response.entrySet().size());
            assertEquals("OK", response.get("status").getAsString());
            assertFalse(response.get("didMappingExist").getAsBoolean());
        }

        {
            // delete unknown userId mapping with userIdType as EXTERNAL
            JsonObject request = new JsonObject();
            request.addProperty("userId", "unknown");
            request.addProperty("userIdType", "EXTERNAL");

            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/userid/map/remove", request, 1000, 1000, null,
                    SemVer.v2_15.get(), "useridmapping");
            assertEquals(2, response.entrySet().size());
            assertEquals("OK", response.get("status").getAsString());
            assertFalse(response.get("didMappingExist").getAsBoolean());
        }

        {
            // delete unknown userId mapping with userIdType as ANY
            JsonObject request = new JsonObject();
            request.addProperty("userId", "unknown");
            request.addProperty("userIdType", "ANY");

            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/userid/map/remove", request, 1000, 1000, null,
                    SemVer.v2_15.get(), "useridmapping");
            assertEquals(2, response.entrySet().size());
            assertEquals("OK", response.get("status").getAsString());
            assertFalse(response.get("didMappingExist").getAsBoolean());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testDeletingUserIdMapping() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create a userId mapping
        AuthRecipeUserInfo userInfo = EmailPassword.signUp(process.main, "test@example.com", "testPass123");
        UserIdMapping userIdMapping = new UserIdMapping(userInfo.getSupertokensUserId(), "externalUserId",
                "externalUserIdInfo");
        createUserIdMappingAndCheckThatItExists(process.main, userIdMapping);

        // delete userId mapping with userIdType as SUPERTOKENS
        {
            JsonObject request = new JsonObject();
            request.addProperty("userId", userIdMapping.superTokensUserId);
            request.addProperty("userIdType", "SUPERTOKENS");

            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/userid/map/remove", request, 1000, 1000, null,
                    SemVer.v2_15.get(), "useridmapping");
            assertEquals(2, response.entrySet().size());
            assertEquals("OK", response.get("status").getAsString());
            assertTrue(response.get("didMappingExist").getAsBoolean());

            // retrieve mapping and check that it does not exist
            assertNull(io.supertokens.useridmapping.UserIdMapping.getUserIdMapping(process.main,
                    userIdMapping.superTokensUserId, UserIdType.SUPERTOKENS));
        }

        {
            // create userId mapping
            createUserIdMappingAndCheckThatItExists(process.main, userIdMapping);

            // delete userId mapping with userIdType as EXTERNAL

            JsonObject request = new JsonObject();
            request.addProperty("userId", userIdMapping.externalUserId);
            request.addProperty("userIdType", "EXTERNAL");

            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/userid/map/remove", request, 1000, 1000, null,
                    SemVer.v2_15.get(), "useridmapping");
            assertEquals(2, response.entrySet().size());
            assertEquals("OK", response.get("status").getAsString());
            assertTrue(response.get("didMappingExist").getAsBoolean());

            // retrieve mapping and check that it does not exist
            assertNull(io.supertokens.useridmapping.UserIdMapping.getUserIdMapping(process.main,
                    userIdMapping.externalUserId, UserIdType.EXTERNAL));
        }

        {
            // create userId mapping
            createUserIdMappingAndCheckThatItExists(process.main, userIdMapping);
            // delete userId mapping with superTokensUserId with userIdType ANY
            {
                JsonObject request = new JsonObject();
                request.addProperty("userId", userIdMapping.superTokensUserId);
                request.addProperty("userIdType", "ANY");

                JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/userid/map/remove", request, 1000, 1000, null,
                        SemVer.v2_15.get(), "useridmapping");
                assertEquals(2, response.entrySet().size());
                assertEquals("OK", response.get("status").getAsString());
                assertTrue(response.get("didMappingExist").getAsBoolean());

                // retrieve mapping and check that it does not exist
                assertNull(io.supertokens.useridmapping.UserIdMapping.getUserIdMapping(process.main,
                        userIdMapping.superTokensUserId, UserIdType.ANY));
            }

            // create userId mapping
            createUserIdMappingAndCheckThatItExists(process.main, userIdMapping);
            // delete userId mapping with externalUserId with userIdType ANY
            {
                JsonObject request = new JsonObject();
                request.addProperty("userId", userIdMapping.externalUserId);
                request.addProperty("userIdType", "ANY");

                JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/userid/map/remove", request, 1000, 1000, null,
                        SemVer.v2_15.get(), "useridmapping");
                assertEquals(2, response.entrySet().size());
                assertEquals("OK", response.get("status").getAsString());
                assertTrue(response.get("didMappingExist").getAsBoolean());

                // retrieve mapping and check that it does not exist
                assertNull(io.supertokens.useridmapping.UserIdMapping.getUserIdMapping(process.main,
                        userIdMapping.externalUserId, UserIdType.ANY));
            }
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testDeletingAUserIdMappingWithoutSendingUserIdType() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create a userId mapping
        AuthRecipeUserInfo userInfo = EmailPassword.signUp(process.main, "test@example.com", "testPass123");
        UserIdMapping userIdMapping = new UserIdMapping(userInfo.getSupertokensUserId(), "externalUserId",
                "externalUserIdInfo");
        createUserIdMappingAndCheckThatItExists(process.main, userIdMapping);

        {
            // delete mapping with superTokensUserId
            JsonObject request = new JsonObject();
            request.addProperty("userId", userIdMapping.superTokensUserId);

            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/userid/map/remove", request, 1000, 1000, null,
                    SemVer.v2_15.get(), "useridmapping");
            assertEquals(2, response.entrySet().size());
            assertEquals("OK", response.get("status").getAsString());
            assertTrue(response.get("didMappingExist").getAsBoolean());

            // retrieve mapping and check that it does not exist
            assertNull(io.supertokens.useridmapping.UserIdMapping.getUserIdMapping(process.main,
                    userIdMapping.superTokensUserId, UserIdType.ANY));

        }

        // create mapping
        createUserIdMappingAndCheckThatItExists(process.main, userIdMapping);

        {
            // delete mapping with externalUserId
            JsonObject request = new JsonObject();
            request.addProperty("userId", userIdMapping.externalUserId);

            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/userid/map/remove", request, 1000, 1000, null,
                    SemVer.v2_15.get(), "useridmapping");
            assertEquals(2, response.entrySet().size());
            assertEquals("OK", response.get("status").getAsString());
            assertTrue(response.get("didMappingExist").getAsBoolean());

            // retrieve mapping and check that it does not exist
            assertNull(io.supertokens.useridmapping.UserIdMapping.getUserIdMapping(process.main,
                    userIdMapping.superTokensUserId, UserIdType.ANY));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void deleteUserIdMappingWithAndWithoutForce() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        AuthRecipeUserInfo userInfo = EmailPassword.signUp(process.main, "test@example.com", "testPass123");
        String superTokensUserId = userInfo.getSupertokensUserId();
        String externalId = "externalId";
        io.supertokens.useridmapping.UserIdMapping.createUserIdMapping(process.main, superTokensUserId, externalId,
                null, false);

        JsonObject data = new JsonObject();
        data.addProperty("test", "testData");
        UserMetadata.updateUserMetadata(process.main, externalId, data);
        UserMetadata.getUserMetadata(process.main, externalId);

        // delete mapping without force
        {
            JsonObject request = new JsonObject();
            request.addProperty("userId", externalId);
            try {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/userid/map/remove", request, 1000, 1000, null,
                        SemVer.v2_15.get(), "useridmapping");
                throw new Exception("Should not come here");
            } catch (HttpResponseException e) {
                assertEquals(e.statusCode, 400);
                assertEquals(e.getMessage(),
                        "Http error. Status Code: 400. Message:" + " UserId is already in use in UserMetadata recipe");
            }
        }

        // delete mapping with force
        {
            JsonObject request = new JsonObject();
            request.addProperty("userId", externalId);
            request.addProperty("force", true);
            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/userid/map/remove", request, 1000, 1000, null,
                    SemVer.v2_15.get(), "useridmapping");
            assertEquals(response.get("status").getAsString(), "OK");
        }

        // check that mapping does not exist
        UserIdMapping mapping = io.supertokens.useridmapping.UserIdMapping.getUserIdMapping(process.main,
                superTokensUserId, UserIdType.SUPERTOKENS);
        assertNull(mapping);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

}
