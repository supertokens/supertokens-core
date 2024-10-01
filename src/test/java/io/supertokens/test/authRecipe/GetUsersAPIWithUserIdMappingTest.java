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
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.useridmapping.UserIdMappingStorage;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.utils.SemVer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.ArrayList;
import java.util.HashMap;

import static org.junit.Assert.assertEquals;
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
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        UserIdMappingStorage storage = (UserIdMappingStorage) StorageLayer.getStorage(process.main);
        ArrayList<String> externalUserIdList = new ArrayList<>();

        for (int i = 1; i <= 10; i++) {
            // create User
            AuthRecipeUserInfo userInfo = EmailPassword.signUp(process.main, "test" + i + "@example.com",
                    "testPass123");
            String superTokensUserId = userInfo.getSupertokensUserId();
            String externalUserId = "externalId" + i;
            externalUserIdList.add(externalUserId);

            // create a userId mapping
            storage.createUserIdMapping(new AppIdentifier(null, null), superTokensUserId, externalUserId,
                    null);
        }

        JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/users", null, 1000, 1000, null, SemVer.v2_15.get(), null);
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

    @Test
    public void createMultipleUsersAndMapTheirIdsRetrieveUsersUsingPaginationTokenAndCheckThatExternalIdIsReturned()
            throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        UserIdMappingStorage storage = (UserIdMappingStorage) StorageLayer.getStorage(process.main);
        ArrayList<String> externalUserIdList = new ArrayList<>();

        for (int i = 1; i <= 20; i++) {
            // create User
            AuthRecipeUserInfo userInfo = EmailPassword.signUp(process.main, "test" + i + "@example.com",
                    "testPass123");
            String superTokensUserId = userInfo.getSupertokensUserId();
            String externalUserId = "externalId" + i;
            externalUserIdList.add(externalUserId);

            // create a userId mapping
            storage.createUserIdMapping(new AppIdentifier(null, null), superTokensUserId, externalUserId,
                    null);
        }

        HashMap<String, String> queryParams = new HashMap<>();
        queryParams.put("limit", "10");

        JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/users", queryParams, 1000, 1000, null, SemVer.v2_15.get(), null);

        assertEquals("OK", response.get("status").getAsString());
        JsonArray users = response.getAsJsonArray("users");
        assertEquals(10, users.size());

        for (int i = 0; i < users.size(); i++) {
            JsonObject user = users.get(i).getAsJsonObject().get("user").getAsJsonObject();
            assertEquals(externalUserIdList.get(i), user.get("id").getAsString());
        }

        // use the pagination token to query the remaining users
        String paginationToken = response.get("nextPaginationToken").getAsString();
        HashMap<String, String> queryParams_2 = new HashMap<>();
        queryParams_2.put("limit", "10");
        queryParams_2.put("paginationToken", paginationToken);

        JsonObject response_2 = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/users", queryParams_2, 1000, 1000, null, SemVer.v2_15.get(),
                null);

        assertEquals("OK", response_2.get("status").getAsString());
        JsonArray users_2 = response_2.getAsJsonArray("users");
        assertEquals(10, users_2.size());

        for (int i = 0; i < users_2.size(); i++) {
            JsonObject user = users_2.get(i).getAsJsonObject().get("user").getAsJsonObject();
            assertEquals(externalUserIdList.get(i + 10), user.get("id").getAsString());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
