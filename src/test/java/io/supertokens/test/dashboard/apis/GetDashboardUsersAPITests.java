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

package io.supertokens.test.dashboard.apis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.HashMap;

import io.supertokens.utils.SemVer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import io.supertokens.ProcessState.PROCESS_STATE;
import io.supertokens.dashboard.Dashboard;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.httpRequest.HttpRequest;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.dashboard.DashboardUser;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;

public class GetDashboardUsersAPITests {
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
    public void testRetrievingDashboardUsers() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // enable dashboard feature
        FeatureFlagTestContent.getInstance(process.getProcess()).setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES,
                new EE_FEATURES[]{EE_FEATURES.DASHBOARD_LOGIN});

        // create multiple users
        ArrayList<DashboardUser> createdUsers = new ArrayList<>();

        for (int i = 0; i < 3; i++) {
            DashboardUser user = Dashboard.signUpDashboardUser(process.getProcess(), "test" + i + "@example.com",
                    "testPasswordHash");
            createdUsers.add(user);
        }

        // retrieve users and check that there were correctly created

        JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/dashboard/users", new HashMap<>(), 1000, 1000, null,
                SemVer.v2_18.get(), "dashboard");
        assertEquals(2, response.entrySet().size());
        assertEquals("OK", response.get("status").getAsString());

        JsonArray retrievedUsers = response.get("users").getAsJsonArray();

        assertEquals(createdUsers.size(), retrievedUsers.size());

        // check that the correct users were returned and in the correct order
        for (int i = 0; i < createdUsers.size(); i++) {
            assertEquals(createdUsers.get(i).userId,
                    retrievedUsers.get(i).getAsJsonObject().get("userId").getAsString());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void testRetrievingDashboardUsersOnlyReturnsUnsuspendedUsers() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // enable dashboard feature
        FeatureFlagTestContent.getInstance(process.getProcess()).setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES,
                new EE_FEATURES[]{EE_FEATURES.DASHBOARD_LOGIN});

        // create multiple users
        ArrayList<DashboardUser> createdUsers = new ArrayList<>();

        for (int i = 0; i < Dashboard.MAX_NUMBER_OF_FREE_DASHBOARD_USERS + 1; i++) {
            DashboardUser user = Dashboard.signUpDashboardUser(process.getProcess(), "test" + i + "@example.com",
                    "testPasswordHash");
            createdUsers.add(user);
        }

        // remove dashboard feature
        FeatureFlagTestContent.getInstance(process.getProcess()).setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES,
                new EE_FEATURES[]{});

        // retrieve users and check that there were correctly created

        JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/dashboard/users", new HashMap<>(), 1000, 1000, null,
                SemVer.v2_18.get(), "dashboard");
        assertEquals(2, response.entrySet().size());
        assertEquals("OK", response.get("status").getAsString());

        JsonArray retrievedUsers = response.get("users").getAsJsonArray();

        assertEquals(Dashboard.MAX_NUMBER_OF_FREE_DASHBOARD_USERS, retrievedUsers.size());
        for (int i = 0; i < Dashboard.MAX_NUMBER_OF_FREE_DASHBOARD_USERS; i++) {
            assertEquals(createdUsers.get(i).userId,
                    retrievedUsers.get(i).getAsJsonObject().get("userId").getAsString());
        }


        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }
}
