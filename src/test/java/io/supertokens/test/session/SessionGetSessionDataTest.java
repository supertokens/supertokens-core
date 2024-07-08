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

package io.supertokens.test.session;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.exceptions.UnauthorisedException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.session.Session;
import io.supertokens.session.accessToken.AccessToken;
import io.supertokens.session.info.SessionInformationHolder;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

public class SessionGetSessionDataTest {
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

    // * Create session with some user data -> Verify the payload -> Update user data using session handle
    // * -> Verify that the change is reflected
    @Test
    public void updateSessionInfo() throws Exception {

        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        SessionInformationHolder sessionInfo = Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                userDataInDatabase);

        JsonObject sessionDataBeforeUpdate = Session.getSession(process.getProcess(),
                sessionInfo.session.handle).userDataInDatabase;
        assertEquals(userDataInDatabase.toString(), sessionDataBeforeUpdate.toString());

        JsonObject userDataInDatabase2 = new JsonObject();
        userDataInDatabase2.addProperty("key1", "value1");
        userDataInDatabase2.addProperty("key2", 1);
        JsonArray arr = new JsonArray();
        userDataInDatabase2.add("key3", arr);

        Session.updateSession(process.getProcess(), sessionInfo.session.handle, userDataInDatabase2, null,
                AccessToken.getLatestVersion());

        JsonObject sessionDataAfterUpdate = Session.getSession(process.getProcess(),
                sessionInfo.session.handle).userDataInDatabase;
        assertEquals(userDataInDatabase2.toString(), sessionDataAfterUpdate.toString());
        assertNotEquals(sessionDataBeforeUpdate.toString(), sessionDataAfterUpdate.toString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // * Try getting and updating session information for a non-existent session handle -> Verify that both throw
    // * UnauthorisedException for session not existing
    @Test
    public void gettingAndUpdatingSessionDataForNonExistentSession() throws Exception {

        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        try {
            Session.getSession(process.getProcess(), "random");
            fail();
        } catch (UnauthorisedException e) {
            assertEquals(e.getMessage(), "Session does not exist.");
        }

        try {
            Session.updateSession(process.getProcess(), "random", new JsonObject(), null,
                    AccessToken.getLatestVersion());
            fail();
        } catch (UnauthorisedException e) {
            assertEquals(e.getMessage(), "Session does not exist.");
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
