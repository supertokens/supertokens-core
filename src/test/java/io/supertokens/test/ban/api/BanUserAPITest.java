/*
 *    Copyright (c) 2023, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.test.ban.api;

import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.ban.BannedUserStorage;
import io.supertokens.pluginInterface.emailpassword.UserInfo;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.httpRequest.HttpResponseException;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.*;

public class BanUserAPITest {
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

        // dont pass userId 
        {
            try {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/ban/user", new JsonObject(), 1000, 1000, null,
                        Utils.getCdiVersion2_15ForTests(), "userId");
                throw new Exception("should not come here");
            } catch (HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals("Http error. Status Code: 400. Message:"
                        + " Field name 'userId' is invalid in JSON input"));
            }
        }

        // userId as a number
        {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("userId", 1);
            try {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/ban/user", requestBody, 1000, 1000, null,
                        Utils.getCdiVersion2_15ForTests(), "banuser");
                throw new Exception("should not come here");
            } catch (HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals("Http error. Status Code: 400. Message:"
                        + " Field name 'userId' is invalid in JSON input"));
            }
        }


        // userId as an empty string
        {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("userId", " ");
            try {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/ban/user", requestBody, 1000, 1000, null,
                        Utils.getCdiVersion2_15ForTests(), "banuser");
                throw new Exception("should not come here");
            } catch (HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals("Http error. Status Code: 400. Message:"
                        + " Field name 'userId' cannot be an empty String"));
            }
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testCreatingBanUserAPI() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        BannedUserStorage storage = StorageLayer.getBannedUserStorage(process.main);

        // create a User
        UserInfo userInfo = EmailPassword.signUp(process.main, "test@example.com", "testPass123");
        String userId = userInfo.id;

        JsonObject requestBody = new JsonObject();

        requestBody.addProperty("userId", userId);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/ban/user", requestBody, 1000, 1000, null,
                Utils.getCdiVersion2_15ForTests(), "banuser");

        assertEquals(1, response.entrySet().size());
        assertEquals("OK", response.get("status").getAsString());

        // check that user was banned and added to banned table

        assertTrue(storage.isUserBanned(userId));
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testCreateBannedUserNonExisting() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }


        // call the api to with random string
        JsonObject requestBody = new JsonObject();

        requestBody.addProperty("userId", "userId");

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/ban/user", requestBody, 1000, 1000, null,
                Utils.getCdiVersion2_15ForTests(), "banuser");

        assertEquals(1, response.entrySet().size());
        assertEquals("USER_ID_INCORRECT_ERROR", response.get("status").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testCreateBannedUserTwice() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        BannedUserStorage storage = StorageLayer.getBannedUserStorage(process.main);
        // create a User
        UserInfo userInfo = EmailPassword.signUp(process.main, "test@example.com", "testPass123");
        String userId = userInfo.id;

        // ban the user

        storage.createNewBannedUser(userId);

        // call the api to ban again
        JsonObject requestBody = new JsonObject();

        requestBody.addProperty("userId", userId);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/ban/user", requestBody, 1000, 1000, null,
                Utils.getCdiVersion2_15ForTests(), "banuser");

        assertEquals(1, response.entrySet().size());
        assertEquals("USER_ALREADY_BANNED_ERROR", response.get("status").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

}
