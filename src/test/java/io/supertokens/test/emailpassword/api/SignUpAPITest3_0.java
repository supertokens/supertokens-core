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

package io.supertokens.test.emailpassword.api;

import com.google.gson.JsonObject;
import io.supertokens.ActiveUsers;
import io.supertokens.ProcessState;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeStorage;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
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

import static org.junit.Assert.*;

/*
 * TODO:
 *  - Check for bad input (missing fields)
 *  - Check good input works and that user is there in db (and then call sign in)
 *  - Test the normalise email function
 *  - Test that only the normalised email is saved in the db
 *  - Test that giving an empty password throws a bad input error
 * */

public class SignUpAPITest3_0 {

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

    // Check good input works and that user is there in db (and then call sign in)
    @Test
    public void testGoodInput() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        long startTS = System.currentTimeMillis();

        JsonObject signUpResponse = Utils.signUpRequest_3_0(process, "random@gmail.com", "validPass123");
        assertEquals(signUpResponse.get("status").getAsString(), "OK");
        assertEquals(signUpResponse.entrySet().size(), 2);

        JsonObject signUpUser = signUpResponse.get("user").getAsJsonObject();
        assertEquals(signUpUser.get("email").getAsString(), "random@gmail.com");
        assertNotNull(signUpUser.get("id"));

        int activeUsers = ActiveUsers.countUsersActiveSince(process.getProcess(), startTS);
        assert (activeUsers == 1);
        AuthRecipeUserInfo user = ((AuthRecipeStorage) StorageLayer.getStorage(process.getProcess()))
                .listPrimaryUsersByEmail(new TenantIdentifier(null, null, null), "random@gmail.com")[0];
        assertEquals(user.loginMethods[0].email, signUpUser.get("email").getAsString());
        assertEquals(user.getSupertokensUserId(), signUpUser.get("id").getAsString());

        JsonObject responseBody = new JsonObject();
        responseBody.addProperty("email", "random@gmail.com");
        responseBody.addProperty("password", "validPass123");

        JsonObject signInResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/signin", responseBody, 1000, 1000, null, SemVer.v2_7.get(),
                "emailpassword");

        assertEquals(signInResponse.get("status").getAsString(), "OK");
        assertEquals(signInResponse.entrySet().size(), 2);

        assertEquals(signInResponse.get("user").getAsJsonObject().get("id").getAsString(),
                signUpUser.get("id").getAsString());
        assertEquals(signInResponse.get("user").getAsJsonObject().get("email").getAsString(),
                signUpUser.get("email").getAsString());
        signInResponse.get("user").getAsJsonObject().get("timeJoined").getAsLong();
        assertEquals(signInResponse.get("user").getAsJsonObject().entrySet().size(), 3);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
