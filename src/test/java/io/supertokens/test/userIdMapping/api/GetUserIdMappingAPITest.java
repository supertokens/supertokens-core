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
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.httpRequest.HttpResponseException;
import io.supertokens.useridmapping.UserIdMapping;
import io.supertokens.utils.SemVer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.HashMap;

import static org.junit.Assert.*;

public class GetUserIdMappingAPITest {
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

        {
            // do not pass userId
            try {
                HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/userid/map", new HashMap<>(), 1000, 1000, null,
                        SemVer.v2_14.get(), "useridmapping");
                throw new Exception("should not come here");
            } catch (HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals(
                        "Http error. Status Code: 400. Message:" + " Field name 'userId' is missing in GET request"));
            }
        }

        {
            // pass userId as an empty string
            HashMap<String, String> QUERY_PARAM = new HashMap<>();
            QUERY_PARAM.put("userId", "");

            try {
                HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/userid/map", QUERY_PARAM, 1000, 1000, null,
                        SemVer.v2_14.get(), "useridmapping");
                throw new Exception("should not come here");
            } catch (HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals(
                        "Http error. Status Code: 400. Message:" + " Field name 'userId' cannot be an empty String"));
            }
        }

        {
            // pass userIdType as an empty string
            HashMap<String, String> QUERY_PARAM = new HashMap<>();
            QUERY_PARAM.put("userId", "testUserId");
            QUERY_PARAM.put("userIdType", "");

            try {
                HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/userid/map", QUERY_PARAM, 1000, 1000, null,
                        SemVer.v2_14.get(), "useridmapping");
                throw new Exception("should not come here");
            } catch (HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals("Http error. Status Code: 400. Message:"
                        + " Field name 'userIdType' should be one of 'SUPERTOKENS', 'EXTERNAL' or 'ANY'"));
            }
        }

        {
            // pass userIdType as a random string
            HashMap<String, String> QUERY_PARAM = new HashMap<>();
            QUERY_PARAM.put("userId", "testUserId");
            QUERY_PARAM.put("userIdType", "random");

            try {
                HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/userid/map", QUERY_PARAM, 1000, 1000, null,
                        SemVer.v2_14.get(), "useridmapping");
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
    public void testRetrievingUserIdMappingWithUnknownUserId() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        {
            // retrieve userId mapping with unknown userId with SUPERTOKENS as the userIdType
            HashMap<String, String> QUERY_PARAM = new HashMap<>();
            QUERY_PARAM.put("userId", "unknown");
            QUERY_PARAM.put("userIdType", "SUPERTOKENS");

            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/userid/map", QUERY_PARAM, 1000, 1000, null,
                    SemVer.v2_14.get(), "useridmapping");
            assertEquals(1, response.entrySet().size());
            assertEquals("UNKNOWN_MAPPING_ERROR", response.get("status").getAsString());
        }

        {
            // retrieve userId mapping with unknown userId with EXTERNAL as the userIdType
            HashMap<String, String> QUERY_PARAM = new HashMap<>();
            QUERY_PARAM.put("userId", "unknown");
            QUERY_PARAM.put("userIdType", "EXTERNAL");

            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/userid/map", QUERY_PARAM, 1000, 1000, null,
                    SemVer.v2_14.get(), "useridmapping");
            assertEquals(1, response.entrySet().size());
            assertEquals("UNKNOWN_MAPPING_ERROR", response.get("status").getAsString());
        }

        {
            // retrieve userId mapping with unknown userId with ANY as the userIdType
            HashMap<String, String> QUERY_PARAM = new HashMap<>();
            QUERY_PARAM.put("userId", "unknown");
            QUERY_PARAM.put("userIdType", "ANY");

            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/userid/map", QUERY_PARAM, 1000, 1000, null,
                    SemVer.v2_14.get(), "useridmapping");
            assertEquals(1, response.entrySet().size());
            assertEquals("UNKNOWN_MAPPING_ERROR", response.get("status").getAsString());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testRetrieveUserIdMapping() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create a user and map their userId to an external userId
        AuthRecipeUserInfo user = EmailPassword.signUp(process.main, "test@example.com", "testPass123");
        String superTokensUserId = user.getSupertokensUserId();
        String externalUserId = "externalUserId";
        String externalUserIdInfo = "externalUserIdInfo";

        UserIdMapping.createUserIdMapping(process.main, superTokensUserId, externalUserId, externalUserIdInfo, false);

        // retrieve the userId mapping using the superTokensUserId with SUPERTOKENS as the userIdType
        {
            HashMap<String, String> QUERY_PARAM = new HashMap<>();
            QUERY_PARAM.put("userId", superTokensUserId);
            QUERY_PARAM.put("userIdType", "SUPERTOKENS");

            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/userid/map", QUERY_PARAM, 1000, 1000, null,
                    SemVer.v2_14.get(), "useridmapping");

            assertEquals(4, response.entrySet().size());
            assertEquals("OK", response.get("status").getAsString());
            assertEquals(superTokensUserId, response.get("superTokensUserId").getAsString());
            assertEquals(externalUserId, response.get("externalUserId").getAsString());
            assertEquals(externalUserIdInfo, response.get("externalUserIdInfo").getAsString());
        }

        // retrieve the userId mapping using the externalUserId with EXTERNAL as the userIdType
        {
            HashMap<String, String> QUERY_PARAM = new HashMap<>();
            QUERY_PARAM.put("userId", externalUserId);
            QUERY_PARAM.put("userIdType", "EXTERNAL");

            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/userid/map", QUERY_PARAM, 1000, 1000, null,
                    SemVer.v2_14.get(), "useridmapping");

            assertEquals(4, response.entrySet().size());
            assertEquals("OK", response.get("status").getAsString());
            assertEquals(superTokensUserId, response.get("superTokensUserId").getAsString());
            assertEquals(externalUserId, response.get("externalUserId").getAsString());
            assertEquals(externalUserIdInfo, response.get("externalUserIdInfo").getAsString());
        }

        // retrieve the userId mapping with ANY as the userIdType
        {
            {
                // retrieving with superTokensUserId
                HashMap<String, String> QUERY_PARAM = new HashMap<>();
                QUERY_PARAM.put("userId", superTokensUserId);
                QUERY_PARAM.put("userIdType", "ANY");

                JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/userid/map", QUERY_PARAM, 1000, 1000, null,
                        SemVer.v2_14.get(), "useridmapping");

                assertEquals(4, response.entrySet().size());
                assertEquals("OK", response.get("status").getAsString());
                assertEquals(superTokensUserId, response.get("superTokensUserId").getAsString());
                assertEquals(externalUserId, response.get("externalUserId").getAsString());
                assertEquals(externalUserIdInfo, response.get("externalUserIdInfo").getAsString());
            }

            {
                // retrieving with externalUserId
                HashMap<String, String> QUERY_PARAM = new HashMap<>();
                QUERY_PARAM.put("userId", externalUserId);
                QUERY_PARAM.put("userIdType", "ANY");

                JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/userid/map", QUERY_PARAM, 1000, 1000, null,
                        SemVer.v2_14.get(), "useridmapping");

                assertEquals(4, response.entrySet().size());
                assertEquals("OK", response.get("status").getAsString());
                assertEquals(superTokensUserId, response.get("superTokensUserId").getAsString());
                assertEquals(externalUserId, response.get("externalUserId").getAsString());
                assertEquals(externalUserIdInfo, response.get("externalUserIdInfo").getAsString());
            }
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testRetrievingUserIdMappingWithoutSendingUserIdType() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create a user and map their userId to an external userId
        AuthRecipeUserInfo user = EmailPassword.signUp(process.main, "test@example.com", "testPass123");
        String superTokensUserId = user.getSupertokensUserId();
        String externalUserId = "externalUserId";
        String externalUserIdInfo = "externalUserIdInfo";

        UserIdMapping.createUserIdMapping(process.main, superTokensUserId, externalUserId, externalUserIdInfo, false);

        {
            // retrieving with superTokensUserId
            HashMap<String, String> QUERY_PARAM = new HashMap<>();
            QUERY_PARAM.put("userId", superTokensUserId);

            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/userid/map", QUERY_PARAM, 1000, 1000, null,
                    SemVer.v2_14.get(), "useridmapping");

            assertEquals(4, response.entrySet().size());
            assertEquals("OK", response.get("status").getAsString());
            assertEquals(superTokensUserId, response.get("superTokensUserId").getAsString());
            assertEquals(externalUserId, response.get("externalUserId").getAsString());
            assertEquals(externalUserIdInfo, response.get("externalUserIdInfo").getAsString());
        }

        {
            // retrieving with externalUserId
            HashMap<String, String> QUERY_PARAM = new HashMap<>();
            QUERY_PARAM.put("userId", externalUserId);

            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/userid/map", QUERY_PARAM, 1000, 1000, null,
                    SemVer.v2_14.get(), "useridmapping");

            assertEquals(4, response.entrySet().size());
            assertEquals("OK", response.get("status").getAsString());
            assertEquals(superTokensUserId, response.get("superTokensUserId").getAsString());
            assertEquals(externalUserId, response.get("externalUserId").getAsString());
            assertEquals(externalUserIdInfo, response.get("externalUserIdInfo").getAsString());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testRetrieveUserIdMappingWithExternalUserIdInfoAsNull() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create a user and map their userId to an external userId
        AuthRecipeUserInfo user = EmailPassword.signUp(process.main, "test@example.com", "testPass123");
        String superTokensUserId = user.getSupertokensUserId();
        String externalUserId = "externalUserId";

        UserIdMapping.createUserIdMapping(process.main, superTokensUserId, externalUserId, null, false);

        HashMap<String, String> QUERY_PARAM = new HashMap<>();
        QUERY_PARAM.put("userId", superTokensUserId);
        QUERY_PARAM.put("userIdType", "SUPERTOKENS");

        JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/userid/map", QUERY_PARAM, 1000, 1000, null,
                SemVer.v2_14.get(), "useridmapping");
        assertEquals(3, response.entrySet().size());
        assertEquals("OK", response.get("status").getAsString());
        assertEquals(superTokensUserId, response.get("superTokensUserId").getAsString());
        assertEquals(externalUserId, response.get("externalUserId").getAsString());
        assertNull(response.get("externalUserIdInfo"));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

}
