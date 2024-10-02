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

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.useridmapping.UserIdMapping;
import io.supertokens.pluginInterface.useridmapping.UserIdMappingStorage;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.httpRequest.HttpResponseException;
import io.supertokens.usermetadata.UserMetadata;
import io.supertokens.utils.SemVer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.*;

public class CreateUserIdMappingAPITest {
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

        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // dont pass either superTokensUserId or externalUserId
        {
            try {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/userid/map", new JsonObject(), 1000, 1000, null,
                        SemVer.v2_15.get(), "useridmapping");
                throw new Exception("should not come here");
            } catch (HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals("Http error. Status Code: 400. Message:"
                        + " Field name 'superTokensUserId' is invalid in JSON input"));
            }
        }

        // dont pass superTokensUserId
        {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("externalUserId", "external-id");
            try {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/userid/map", requestBody, 1000, 1000, null,
                        SemVer.v2_15.get(), "useridmapping");
                throw new Exception("should not come here");
            } catch (HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals("Http error. Status Code: 400. Message:"
                        + " Field name 'superTokensUserId' is invalid in JSON input"));
            }

        }

        // superTokensUserId as a number
        {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("superTokensUserId", 1);
            requestBody.addProperty("externalUserId", "externalUserId");
            try {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/userid/map", requestBody, 1000, 1000, null,
                        SemVer.v2_15.get(), "useridmapping");
                throw new Exception("should not come here");
            } catch (HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals("Http error. Status Code: 400. Message:"
                        + " Field name 'superTokensUserId' is invalid in JSON input"));
            }
        }

        // dont pass externalUserId
        {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("superTokensUserId", "userId");
            try {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/userid/map", requestBody, 1000, 1000, null,
                        SemVer.v2_15.get(), "useridmapping");
                throw new Exception("should not come here");
            } catch (HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals("Http error. Status Code: 400. Message:"
                        + " Field name 'externalUserId' is invalid in JSON input"));
            }
        }
        // externalUserId as a number
        {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("superTokensUserId", "userId");
            requestBody.addProperty("externalUserId", 1);
            try {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/userid/map", requestBody, 1000, 1000, null,
                        SemVer.v2_15.get(), "useridmapping");
                throw new Exception("should not come here");
            } catch (HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals("Http error. Status Code: 400. Message:"
                        + " Field name 'externalUserId' is invalid in JSON input"));
            }
        }

        // externalUserIdInfo as a number
        {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("superTokensUserId", "userId");
            requestBody.addProperty("externalUserId", "userId");
            requestBody.addProperty("externalUserIdInfo", 1);
            try {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/userid/map", requestBody, 1000, 1000, null,
                        SemVer.v2_15.get(), "useridmapping");
                throw new Exception("should not come here");
            } catch (HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals("Http error. Status Code: 400. Message:"
                        + " Field name 'externalUserIdInfo' is invalid in JSON input"));
            }
        }

        // superTokensUserId as an empty string
        {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("superTokensUserId", " ");
            requestBody.addProperty("externalUserId", "userId");
            try {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/userid/map", requestBody, 1000, 1000, null,
                        SemVer.v2_15.get(), "useridmapping");
                throw new Exception("should not come here");
            } catch (HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals("Http error. Status Code: 400. Message:"
                        + " Field name 'superTokensUserId' cannot be an empty String"));
            }
        }

        // externalUserId as an empty string
        {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("superTokensUserId", "userId");
            requestBody.addProperty("externalUserId", " ");
            try {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/userid/map", requestBody, 1000, 1000, null,
                        SemVer.v2_15.get(), "useridmapping");
                throw new Exception("should not come here");
            } catch (HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals("Http error. Status Code: 400. Message:"
                        + " Field name 'externalUserId' cannot be an empty String"));
            }
        }

        // externalUserIdInfo as an empty string
        {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("superTokensUserId", "userId");
            requestBody.addProperty("externalUserId", "userId");
            requestBody.addProperty("externalUserIdInfo", " ");

            try {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/userid/map", requestBody, 1000, 1000, null,
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
    public void testCreatingAUserIdMappingWithAndWithoutForce() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create a User and add some non auth recipe info
        AuthRecipeUserInfo userInfo = EmailPassword.signUp(process.main, "test@example.com", "testPass123");

        // add some metadata to the user
        JsonObject userMetadata = new JsonObject();
        userMetadata.addProperty("test", "testExample");
        UserMetadata.updateUserMetadata(process.main, userInfo.getSupertokensUserId(), userMetadata);
        String superTokensUserId = userInfo.getSupertokensUserId();
        String externalUserId = "externalId";

        // try and create mapping without force
        {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("superTokensUserId", superTokensUserId);
            requestBody.addProperty("externalUserId", externalUserId);
            requestBody.add("externalUserIdInfo", JsonNull.INSTANCE);
            try {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/userid/map", requestBody, 1000, 1000, null,
                        SemVer.v2_15.get(), "useridmapping");
                throw new Exception("should not come here");
            } catch (HttpResponseException e) {
                assertEquals(e.statusCode, 400);
                assertEquals(e.getMessage(),
                        "Http error. Status Code: 400. Message:" + " UserId is already in use in UserMetadata recipe");
            }
        }

        // create mapping with force
        {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("superTokensUserId", superTokensUserId);
            requestBody.addProperty("externalUserId", externalUserId);
            requestBody.add("externalUserIdInfo", JsonNull.INSTANCE);
            requestBody.addProperty("force", true);
            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/userid/map", requestBody, 1000, 1000, null,
                    SemVer.v2_15.get(), "useridmapping");
            assertEquals(response.get("status").getAsString(), "OK");
        }
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testCreatingAUserIdMapping() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        UserIdMappingStorage storage = (UserIdMappingStorage) StorageLayer.getStorage(process.main);

        // create a User
        AuthRecipeUserInfo userInfo = EmailPassword.signUp(process.main, "test@example.com", "testPass123");
        String superTokensUserId = userInfo.getSupertokensUserId();
        String externalUserId = "userId";
        String externalUserIdInfo = "externUserIdInfo";

        JsonObject requestBody = new JsonObject();

        requestBody.addProperty("superTokensUserId", superTokensUserId);
        requestBody.addProperty("externalUserId", externalUserId);
        requestBody.addProperty("externalUserIdInfo", externalUserIdInfo);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/userid/map", requestBody, 1000, 1000, null,
                SemVer.v2_15.get(), "useridmapping");

        assertEquals(1, response.entrySet().size());
        assertEquals("OK", response.get("status").getAsString());

        // check that userIdMapping was created

        UserIdMapping userIdMapping = storage.getUserIdMapping(new AppIdentifier(null, null),
                superTokensUserId, true);

        assertEquals(superTokensUserId, userIdMapping.superTokensUserId);
        assertEquals(externalUserId, userIdMapping.externalUserId);
        assertEquals(externalUserIdInfo, userIdMapping.externalUserIdInfo);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testCreatingAUserIdMappingWithAnUnknownSuperTokensUserId() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        String superTokensUserId = "unknownUser";
        String externalUserId = "userId";

        JsonObject requestBody = new JsonObject();

        requestBody.addProperty("superTokensUserId", superTokensUserId);
        requestBody.addProperty("externalUserId", externalUserId);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/userid/map", requestBody, 1000, 1000, null,
                SemVer.v2_15.get(), "useridmapping");

        assertEquals(1, response.entrySet().size());
        assertEquals("UNKNOWN_SUPERTOKENS_USER_ID_ERROR", response.get("status").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testCreatingUserIdMappingWithExternalUserIdInfoAsNull() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        AuthRecipeUserInfo userInfo = EmailPassword.signUp(process.main, "test@example.com", "testPass123");
        String externalUserId = "externalUserId";
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("superTokensUserId", userInfo.getSupertokensUserId());
        requestBody.addProperty("externalUserId", externalUserId);
        requestBody.add("externalUserIdInfo", null);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/userid/map", requestBody, 1000, 1000, null,
                SemVer.v2_15.get(), "useridmapping");

        UserIdMappingStorage storage = (UserIdMappingStorage) StorageLayer.getStorage(process.main);

        UserIdMapping userIdMapping = storage.getUserIdMapping(new AppIdentifier(null, null),
                userInfo.getSupertokensUserId(),
                true);

        assertNotNull(userIdMapping);
        assertEquals(userInfo.getSupertokensUserId(), userIdMapping.superTokensUserId);
        assertEquals(externalUserId, userIdMapping.externalUserId);
        assertNull(userIdMapping.externalUserIdInfo);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    @Test
    public void testCreatingDuplicateUserIdMapping() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        AuthRecipeUserInfo userInfo = EmailPassword.signUp(process.main, "test@example.com", "testPass123");

        String superTokensUserId = userInfo.getSupertokensUserId();
        String externalUserId = "externalUserId";

        // create UserId mapping
        io.supertokens.useridmapping.UserIdMapping.createUserIdMapping(process.main, superTokensUserId, externalUserId,
                null, false);

        {
            // create a duplicate mapping
            JsonObject requestBody = new JsonObject();

            requestBody.addProperty("superTokensUserId", superTokensUserId);
            requestBody.addProperty("externalUserId", externalUserId);

            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/userid/map", requestBody, 1000, 1000, null,
                    SemVer.v2_15.get(), "useridmapping");

            assertEquals(3, response.entrySet().size());
            assertEquals("USER_ID_MAPPING_ALREADY_EXISTS_ERROR", response.get("status").getAsString());
            assertTrue(response.get("doesSuperTokensUserIdExist").getAsBoolean());
            assertTrue(response.get("doesExternalUserIdExist").getAsBoolean());
        }

        {
            // create a duplicate mapping with superTokensUserId
            JsonObject requestBody = new JsonObject();

            requestBody.addProperty("superTokensUserId", superTokensUserId);
            requestBody.addProperty("externalUserId", "newExternalId");

            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/userid/map", requestBody, 1000, 1000, null,
                    SemVer.v2_15.get(), "useridmapping");

            assertEquals(3, response.entrySet().size());
            assertEquals("USER_ID_MAPPING_ALREADY_EXISTS_ERROR", response.get("status").getAsString());
            assertTrue(response.get("doesSuperTokensUserIdExist").getAsBoolean());
            assertFalse(response.get("doesExternalUserIdExist").getAsBoolean());

        }

        {
            // create a duplicate mapping with externalUserId
            AuthRecipeUserInfo newUserInfo = EmailPassword.signUp(process.main, "test2@example.com", "testPass123");

            JsonObject requestBody = new JsonObject();

            requestBody.addProperty("superTokensUserId", newUserInfo.getSupertokensUserId());
            requestBody.addProperty("externalUserId", externalUserId);

            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/userid/map", requestBody, 1000, 1000, null,
                    SemVer.v2_15.get(), "useridmapping");

            assertEquals(3, response.entrySet().size());
            assertEquals("USER_ID_MAPPING_ALREADY_EXISTS_ERROR", response.get("status").getAsString());
            assertFalse(response.get("doesSuperTokensUserIdExist").getAsBoolean());
            assertTrue(response.get("doesExternalUserIdExist").getAsBoolean());

        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

}
