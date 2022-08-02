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

package io.supertokens.test.authRecipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeStorage;
import io.supertokens.pluginInterface.emailpassword.UserInfo;
import io.supertokens.pluginInterface.useridmapping.UserIdMappingStorage;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.ArrayList;
import java.util.HashMap;

import static org.junit.Assert.*;
import static org.junit.Assert.assertNotNull;

public class GetUsersAPIWithUserIdMappingTest {
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
    public void createMultipleUsersAndMapTheirIdsRetrieveAllUsersAndCheckThatExternalIdIsReturned() throws Exception {
        String[] args = { "../" };
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        UserIdMappingStorage storage = StorageLayer.getUserIdMappingStorage(process.main);
        ArrayList<String> externalUserIdList = new ArrayList<>();

        for (int i = 1; i <= 10; i++) {
            // create User
            UserInfo userInfo = EmailPassword.signUp(process.main, "test" + i + "@example.com", "testPass123");
            String superTokensUserId = userInfo.id;
            String externalUserId = "externalId" + i;
            externalUserIdList.add(externalUserId);

            // create a userId mapping
            storage.createUserIdMapping(superTokensUserId, externalUserId, null);
        }

        JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/users", null, 1000, 1000, null, Utils.getCdiVersion2_15ForTests(), null);
        assertEquals("OK", response.get("status").getAsString());
        JsonArray users = response.getAsJsonArray("users");
        assertEquals(10, users.size());
        for (int i = 0; i < users.size(); i++) {
            JsonObject user = users.get(i).getAsJsonObject().get("user").getAsJsonObject();
            assertEquals(externalUserIdList.get(i), user.get("id").getAsString());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
