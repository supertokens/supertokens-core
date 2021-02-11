/*
 *    Copyright (c) 2020, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.test;

import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.storageLayer.StorageLayer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/*
 * TODO:
 *  - good input
 *  - Sign up with unnormalised email, and sign in with normailised email to get the same user.
 *  - bad input
 *     - simple bad input
 *     - email sub object's fiels are missing
 *  - all error states
 * */

public class SignInUpAPITest2_7 {

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

    //good input
    // failure condition: test fails if signinup response does not match api spec
    @Test
    public void testGoodInput() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject response = Utils
                .signInUpRequest_2_7(process, "test@example.com", false, "testThirdPartyId",
                        "testThirdPartyUserId");
        checkSignInUpResponse(response, "testThirdPartyId", "testThirdPartyUserId", "test@example.com", true);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    //Sign up with unnormalised email, and sign in with normailised email to get the same user.
    // failure condition: test fails if signin causes a new user to be created
    @Test
    public void testEmailNormalisation() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject response_1 = Utils
                .signInUpRequest_2_7(process, "TeSt@example.com", false, "testThirdPartyId",
                        "testThirdPartyUserId");
        checkSignInUpResponse(response_1, "testThirdPartyId", "testThirdPartyUserId", "test@example.com", true);

        JsonObject response_2 = Utils
                .signInUpRequest_2_7(process, "test@example.com", false, "testThirdPartyId",
                        "testThirdPartyUserId");
        checkSignInUpResponse(response_2, "testThirdPartyId", "testThirdPartyUserId", "test@example.com", false);

        JsonObject response_1_user = response_1.getAsJsonObject("user");
        JsonObject response_2_user = response_2.getAsJsonObject("user");

        assertEquals(response_1_user.get("id").getAsString(), response_2_user.get("id").getAsString());
        assertEquals(response_1_user.get("timeJoined").getAsString(), response_2_user.get("timeJoined").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    public static void checkSignInUpResponse(JsonObject response, String thirdPartyId, String thirdPartyUserId,
                                             String email, boolean createdNewUser) {
        assertEquals("OK", response.get("status").getAsString());
        assertEquals(3, response.entrySet().size());
        assertEquals(createdNewUser, response.get("createdNewUser").getAsBoolean());

        JsonObject user = response.getAsJsonObject("user");
        assertNotNull(user.get("id"));
        assertNotNull(user.get("timeJoined"));

        JsonObject userThirdParty = user.getAsJsonObject("thirdParty");
        assertEquals(3, userThirdParty.entrySet().size());
        assertEquals(thirdPartyId, userThirdParty.get("id").getAsString());
        assertEquals(thirdPartyUserId, userThirdParty.get("userId").getAsString());
        assertEquals(email, userThirdParty.get("email").getAsString());

    }

}
