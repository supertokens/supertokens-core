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

import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.pluginInterface.session.SessionInfo;
import io.supertokens.session.Session;
import io.supertokens.session.info.SessionInformationHolder;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.*;

public class UpdateSessionGrantPayloadTest {

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
    public void testUpdateGrantPayload() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // createSession with JWT payload
        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");
        JsonObject grantPayload = Utils.getExampleGrantPayload(0);

        SessionInformationHolder sessionInfo = Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                grantPayload, userDataInDatabase, false);

        // verify to see payload is proper
        assertEquals(sessionInfo.session.userDataInJWT, userDataInJWT);

        // change JWT payload using session handle
        JsonObject newGrantPayload = Utils.getExampleGrantPayload(1);
        Session.updateSession(process.getProcess(), sessionInfo.session.handle, null, null, newGrantPayload, null);

        // check that this change is reflected
        SessionInfo session = Session.getSession(process.getProcess(), sessionInfo.session.handle);
        assertEquals(newGrantPayload, session.grants);
        // check that everything else is the same
        assertEquals(userDataInDatabase, session.userDataInDatabase);
        assertEquals(userDataInJWT, session.userDataInJWT);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testUpdateEverything() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // createSession with JWT payload
        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");
        JsonObject grantPayload = Utils.getExampleGrantPayload(0);

        SessionInformationHolder sessionInfo = Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                grantPayload, userDataInDatabase, false);

        // verify to see payload is proper
        assertEquals(sessionInfo.session.userDataInJWT, userDataInJWT);

        // change JWT payload using session handle
        JsonObject newGrantPayload = Utils.getExampleGrantPayload(1);
        JsonObject newUserDataInJWT = new JsonObject();
        newUserDataInJWT.addProperty("key", "value2");
        JsonObject newUserDataInDatabase = new JsonObject();
        newUserDataInDatabase.addProperty("key", "value2");

        Session.updateSession(process.getProcess(), sessionInfo.session.handle, newUserDataInDatabase, newUserDataInJWT,
                newGrantPayload, null);

        // check that this change is reflected
        SessionInfo session = Session.getSession(process.getProcess(), sessionInfo.session.handle);
        assertEquals(newGrantPayload, session.grants);
        // check that everything else is the same
        assertEquals(newUserDataInDatabase, session.userDataInDatabase);
        assertEquals(newUserDataInJWT, session.userDataInJWT);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testUpdateGrantPayloadAndUserDataInJWT() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // createSession with JWT payload
        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");
        JsonObject grantPayload = Utils.getExampleGrantPayload(0);

        SessionInformationHolder sessionInfo = Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                grantPayload, userDataInDatabase, false);

        // verify to see payload is proper
        assertEquals(sessionInfo.session.userDataInJWT, userDataInJWT);

        // change JWT payload using session handle
        JsonObject newGrantPayload = Utils.getExampleGrantPayload(1);
        JsonObject newUserDataInJWT = new JsonObject();
        newUserDataInJWT.addProperty("key", "value2");

        Session.updateSession(process.getProcess(), sessionInfo.session.handle, userDataInDatabase, newUserDataInJWT,
                newGrantPayload, null);

        // check that this change is reflected
        SessionInfo session = Session.getSession(process.getProcess(), sessionInfo.session.handle);
        assertEquals(newGrantPayload, session.grants);
        // check that everything else is the same
        assertEquals(userDataInDatabase, session.userDataInDatabase);
        assertEquals(newUserDataInJWT, session.userDataInJWT);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testUpdateGrantPayloadAndUserDataInDB() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // createSession with JWT payload
        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");
        JsonObject grantPayload = Utils.getExampleGrantPayload(0);

        SessionInformationHolder sessionInfo = Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                grantPayload, userDataInDatabase, false);

        // verify to see payload is proper
        assertEquals(sessionInfo.session.userDataInJWT, userDataInJWT);

        // change JWT payload using session handle
        JsonObject newGrantPayload = Utils.getExampleGrantPayload(1);
        JsonObject newUserDataInDatabase = new JsonObject();
        newUserDataInDatabase.addProperty("key", "value2");

        Session.updateSession(process.getProcess(), sessionInfo.session.handle, newUserDataInDatabase, userDataInJWT,
                newGrantPayload, null);

        // check that this change is reflected
        SessionInfo session = Session.getSession(process.getProcess(), sessionInfo.session.handle);
        assertEquals(newGrantPayload, session.grants);
        // check that everything else is the same
        assertEquals(newUserDataInDatabase, session.userDataInDatabase);
        assertEquals(userDataInJWT, session.userDataInJWT);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testUpdateGrantPayloadToEmptyObj() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // createSession with JWT payload
        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");
        JsonObject grantPayload = Utils.getExampleGrantPayload(0);

        SessionInformationHolder sessionInfo = Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                grantPayload, userDataInDatabase, false);

        // verify to see payload is proper
        assertEquals(sessionInfo.session.userDataInJWT, userDataInJWT);

        // change JWT payload using session handle
        JsonObject newGrantPayload = new JsonObject();
        Session.updateSession(process.getProcess(), sessionInfo.session.handle, null, null, newGrantPayload, null);

        // check that this change is reflected
        SessionInfo session = Session.getSession(process.getProcess(), sessionInfo.session.handle);
        assertEquals(newGrantPayload, session.grants);
        // check that everything else is the same
        assertEquals(userDataInDatabase, session.userDataInDatabase);
        assertEquals(userDataInJWT, session.userDataInJWT);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testUpdateNothingThrows() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // createSession with JWT payload
        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");
        JsonObject grantPayload = Utils.getExampleGrantPayload(0);

        SessionInformationHolder sessionInfo = Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                grantPayload, userDataInDatabase, false);

        // verify to see payload is proper
        assertEquals(sessionInfo.session.userDataInJWT, userDataInJWT);

        Exception error = null;
        try {
            Session.updateSession(process.getProcess(), sessionInfo.session.handle, null, null, null, null);
        } catch (Exception e) {
            error = e;
        }

        assertNotNull(error);
        assertEquals("sessionData, jwtPayload and grantPayload all null when updating session info",
                error.getCause().getMessage());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
