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

package io.supertokens.test.passwordless.api;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.passwordless.UserInfo;
import io.supertokens.pluginInterface.passwordless.PasswordlessStorage;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.*;

public class UserPutAPITest2_10 {
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
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        String userId = "userId";

        String email = "test@example.com";
        String email2 = "test2@example.com";
        String phoneNumber = "+442071838750";

        PasswordlessStorage storage = StorageLayer.getPasswordlessStorage(process.getProcess());
        storage.createUser(new UserInfo(userId, email, null, System.currentTimeMillis()));
        storage.createUser(new UserInfo("userId2", email2, null, System.currentTimeMillis()));
        storage.createUser(new UserInfo("userId3", null, phoneNumber, System.currentTimeMillis()));

        {
            JsonObject createCodeRequestBody = new JsonObject();
            createCodeRequestBody.addProperty("userId", "notexists");
            createCodeRequestBody.addProperty("email", "notexists");

            JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user", createCodeRequestBody, 1000, 1000, null,
                    Utils.getCdiVersion2_10ForTests(), "passwordless");

            assertEquals("UNKNOWN_USER_ID_ERROR", response.get("status").getAsString());
        }

        {
            JsonObject createCodeRequestBody = new JsonObject();
            createCodeRequestBody.addProperty("userId", userId);
            createCodeRequestBody.addProperty("email", email2);

            JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user", createCodeRequestBody, 1000, 1000, null,
                    Utils.getCdiVersion2_10ForTests(), "passwordless");

            assertEquals("EMAIL_ALREADY_EXISTS_ERROR", response.get("status").getAsString());
        }

        {
            JsonObject createCodeRequestBody = new JsonObject();
            createCodeRequestBody.addProperty("userId", userId);
            createCodeRequestBody.addProperty("phoneNumber", phoneNumber);

            JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user", createCodeRequestBody, 1000, 1000, null,
                    Utils.getCdiVersion2_10ForTests(), "passwordless");

            assertEquals("PHONE_NUMBER_ALREADY_EXISTS_ERROR", response.get("status").getAsString());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testEmailToPhone() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        String userId = "userId";
        String phoneNumber = "+442071838750";

        PasswordlessStorage storage = StorageLayer.getPasswordlessStorage(process.getProcess());
        String email = "email";
        storage.createUser(new UserInfo(userId, email, null, System.currentTimeMillis()));

        JsonObject createCodeRequestBody = new JsonObject();
        createCodeRequestBody.addProperty("userId", userId);
        createCodeRequestBody.add("email", JsonNull.INSTANCE);
        createCodeRequestBody.addProperty("phoneNumber", phoneNumber);

        JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/user", createCodeRequestBody, 1000, 1000, null,
                Utils.getCdiVersion2_10ForTests(), "passwordless");

        assertEquals("OK", response.get("status").getAsString());

        assertNull(storage.getUserByEmail(email));
        assertNotNull(storage.getUserByPhoneNumber(phoneNumber));
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
