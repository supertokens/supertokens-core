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

package io.supertokens.test.authRecipe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.HashMap;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import io.supertokens.ProcessState.PROCESS_STATE;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.passwordless.Passwordless;
import io.supertokens.passwordless.Passwordless.CreateCodeResponse;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.thirdparty.ThirdParty;

public class GetUsersWithSearchTagsAPITest {
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
    public void testSearchingForUsers() throws Exception {
        String[] args = { "../" };
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create emailpassword user
        ArrayList<String> userIds = new ArrayList<>();
        userIds.add(EmailPassword.signUp(process.getProcess(), "test@example.com", "testPass123").id);
        userIds.add(EmailPassword.signUp(process.getProcess(), "test2@example.com", "testPass123").id);

        // create thirdparty user
        userIds.add(ThirdParty.signInUp(process.getProcess(), "testTPID", "test", "test2@example.com").user.id);

        // create passwordless user
        CreateCodeResponse createCodeResponse = Passwordless.createCode(process.getProcess(), "test@example.com", null,
                null, null);
        userIds.add(Passwordless.consumeCode(process.getProcess(), createCodeResponse.deviceId,
                createCodeResponse.deviceIdHash,
                createCodeResponse.userInputCode, null).user.id);

        // search with partial input for email field
        HashMap<String, String> params = new HashMap<>();
        params.put("email", "test");

        JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/users", params, 1000, 1000, null, Utils.getCdiVersion2_18ForTests(), null);
        assertEquals("OK", response.get("status").getAsString());
        assertEquals(4, response.get("users").getAsJsonArray().size());
        JsonArray users = response.get("users").getAsJsonArray();

        for (int i = 0; i < userIds.size(); i++) {
            assertEquals(userIds.get(i), users.get(i).getAsJsonObject().get("user").getAsJsonObject().get("id").getAsString());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

}
