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
import io.supertokens.exceptions.UnauthorisedException;
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

import static org.junit.Assert.*;

public class SessionGetJWTDataTest {
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

    // *- create session with some JWT payload -> verify to see payload is proper -> change JWT payload using session
    // * handle -> check this is reflected
    @Test
    public void testVerifyJWTPayloadChangePayloadUsingSessionHandle() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // createSession with JWT payload
        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        SessionInformationHolder sessionInfo = Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                userDataInDatabase);

        // verify to see payload is proper
        assertEquals(sessionInfo.session.userDataInJWT, userDataInJWT);

        // change JWT payload using session handle
        JsonObject newUserDataInJwt = new JsonObject();
        newUserDataInJwt.addProperty("key", "value2");
        Session.updateSession(process.getProcess(), sessionInfo.session.handle, null, newUserDataInJwt,
                AccessToken.getLatestVersion());

        // check that this change is reflected

        assertEquals(Session.getSession(process.getProcess(), sessionInfo.session.handle).userDataInJWT,
                newUserDataInJwt);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // * - create session with some JWT payload -> verify to see payload is proper -> change JWT payload to be empty
    // using
    // * session handle -> check this is reflected
    @Test
    public void testVerifyJWTPayloadChangeToEmptyPayloadUsingSessionHandle() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // createSession with JWT payload
        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        SessionInformationHolder sessionInfo = Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                userDataInDatabase);

        // verify to see payload is proper
        assertEquals(sessionInfo.session.userDataInJWT, userDataInJWT);

        // change JWT payload to be empty using session handle
        JsonObject emptyUserDataInJwt = new JsonObject();
        Session.updateSession(process.getProcess(), sessionInfo.session.handle, null, emptyUserDataInJwt,
                AccessToken.getLatestVersion());

        // check this is reflected
        assertEquals(Session.getSession(process.getProcess(), sessionInfo.session.handle).userDataInJWT,
                emptyUserDataInJwt);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // * - create session with some JWT payload -> verify to see payload is proper -> pass null to
    // changeJWTPayloadInDatabase
    // * function -> check that JWT payload has not changed is reflected
    @Test
    public void testVerifyJWTPayloadSetPayloadToNullUsingSessionHandle() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // createSession with JWT payload
        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        SessionInformationHolder sessionInfo = Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                userDataInDatabase);

        // verify to see payload is proper
        assertEquals(sessionInfo.session.userDataInJWT, userDataInJWT);

        // change JWT payload to be null
        Session.updateSession(process.getProcess(), sessionInfo.session.handle, userDataInDatabase, null,
                AccessToken.getLatestVersion());

        // check that jwtData does not change
        assertEquals(Session.getSession(process.getProcess(), sessionInfo.session.handle).userDataInJWT, userDataInJWT);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // * - create session -> let it expire -> call get function -> make sure you get unauthorised error
    @Test
    public void testExpireSessionCallGetAndCheckUnauthorised() throws Exception {

        String[] args = {"../"};

        Utils.setValueInConfig("refresh_token_validity", "" + 1.0 / 60);// 1 second validity (value in mins)

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // createSession with JWT payload
        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        SessionInformationHolder sessionInfo = Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                userDataInDatabase);

        // Let it expire
        Thread.sleep(2000);

        // Get session information
        try {
            Session.getSession(process.getProcess(), sessionInfo.session.handle);
            fail();
        } catch (UnauthorisedException e) {
            assertEquals(e.getMessage(), "Session does not exist.");
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // * - create session -> revoke the session -> call get function -> make sure you get unauthorised error
    @Test
    public void testRevokedSessionCallGetAndCheckUnauthorised() throws Exception {

        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // createSession with JWT payload
        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        SessionInformationHolder sessionInfo = Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                userDataInDatabase);

        // Revoke the session
        String[] sessionHandles = {sessionInfo.session.handle};
        Session.revokeSessionUsingSessionHandles(process.getProcess(), sessionHandles);

        // call update function
        try {
            Session.getSession(process.getProcess(), sessionInfo.session.handle);
            fail();
        } catch (UnauthorisedException e) {
            assertEquals(e.getMessage(), "Session does not exist.");
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testGetSessionWithExpiredAccessTokenDoesNotThrowError() throws Exception {
        String[] args = {"../"};

        Utils.setValueInConfig("access_token_validity", "1");// 1 second validity

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // createSession with JWT payload
        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        SessionInformationHolder sessionInfo = Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                userDataInDatabase);

        // Wait for access token to expire
        Thread.sleep(1000);

        // Get session details
        Session.getSession(process.getProcess(), sessionInfo.session.handle);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
