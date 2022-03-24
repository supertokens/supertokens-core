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

public class RegenerateTokenTest {
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

    // * - create session with some userData & grants -> verify userData & grants exists -> regenerate with different
    // userData & grants -> verify ->
    // * check userData, grants and lmrt are different.
    @Test
    public void testCrateSessionWithUserDataRegenerateWithUpdatesAndCheck() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // - create session with some data
        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");
        JsonObject initialGrantPayload = Utils.getExampleGrantPayload(0);

        SessionInformationHolder sessionInfo = Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                initialGrantPayload, userDataInDatabase, false);

        assert sessionInfo.accessToken != null;
        AccessToken.AccessTokenInfo accessTokenInfoBefore = AccessToken.getInfoFromAccessToken(process.getProcess(),
                sessionInfo.accessToken.token, false);

        assertEquals(accessTokenInfoBefore.userData, userDataInJWT);
        assertEquals(accessTokenInfoBefore.grants, initialGrantPayload);

        // regenerate with different payload
        JsonObject newUserDataInJWT = new JsonObject();
        newUserDataInJWT.addProperty("key2", "value2");
        JsonObject newGrantPayload = Utils.getExampleGrantPayload(1);

        SessionInformationHolder newSessionInfo = Session.regenerateToken(process.getProcess(),
                sessionInfo.accessToken.token, newUserDataInJWT, newGrantPayload, true);

        // Verify
        assert newSessionInfo.accessToken != null;
        SessionInformationHolder getSessionResponse = Session.getSession(process.getProcess(),
                newSessionInfo.accessToken.token, sessionInfo.antiCsrfToken, false, true);

        // Check that grant payload updated
        assertEquals(getSessionResponse.session.grants, newGrantPayload);
        // check userData and lmrt is different.
        assertEquals(getSessionResponse.session.userDataInJWT, newUserDataInJWT);

        AccessToken.AccessTokenInfo accessTokenInfoAfter = AccessToken.getInfoFromAccessToken(process.getProcess(),
                newSessionInfo.accessToken.token, false);
        assertNotEquals(accessTokenInfoBefore.lmrt, accessTokenInfoAfter.lmrt);

        assertNotEquals(accessTokenInfoAfter.grants, accessTokenInfoBefore.grants);
        assertNotEquals(accessTokenInfoAfter.userData, accessTokenInfoBefore.userData);

        process.kill();
        process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED);

    }

    // * - create session with some userData & grants -> verify userData & grants exists -> regenerate with different
    // userData & no grant update -> verify ->
    // * check userData and lmrt are different but grants are the same
    @Test
    public void testCrateSessionWithUserDataRegenerateWithOnlyUserDataUpdateAndCheck() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // - create session with some data
        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");
        JsonObject initialGrantPayload = Utils.getExampleGrantPayload(0);

        SessionInformationHolder sessionInfo = Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                initialGrantPayload, userDataInDatabase, false);

        assert sessionInfo.accessToken != null;
        AccessToken.AccessTokenInfo accessTokenInfoBefore = AccessToken.getInfoFromAccessToken(process.getProcess(),
                sessionInfo.accessToken.token, false);

        assertEquals(accessTokenInfoBefore.userData, userDataInJWT);
        assertEquals(accessTokenInfoBefore.grants, initialGrantPayload);

        // Check that userData payload updated
        JsonObject newUserDataInJWT = new JsonObject();
        newUserDataInJWT.addProperty("key2", "value2");

        SessionInformationHolder newSessionInfo = Session.regenerateToken(process.getProcess(),
                sessionInfo.accessToken.token, newUserDataInJWT, null, true);

        // Verify
        assert newSessionInfo.accessToken != null;
        SessionInformationHolder getSessionResponse = Session.getSession(process.getProcess(),
                newSessionInfo.accessToken.token, sessionInfo.antiCsrfToken, false, true);

        // Check that grant payload didn't change
        assertEquals(getSessionResponse.session.grants, initialGrantPayload);
        // check userData and lmrt is different.
        assertEquals(getSessionResponse.session.userDataInJWT, newUserDataInJWT);

        AccessToken.AccessTokenInfo accessTokenInfoAfter = AccessToken.getInfoFromAccessToken(process.getProcess(),
                newSessionInfo.accessToken.token, false);
        assertNotEquals(accessTokenInfoBefore.lmrt, accessTokenInfoAfter.lmrt);

        assertNotEquals(accessTokenInfoAfter.userData, accessTokenInfoBefore.userData);
        assertEquals(accessTokenInfoAfter.grants, accessTokenInfoBefore.grants);

        process.kill();
        process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED);
    }

    // -> create V2 session -> verify userData exists
    // -> regenerate with different userData & no grant update not allowing upgrade -> verify
    // -> check userData and lmrt are different but grants are the same
    @Test
    public void testCrateV2SessionWithUserDataRegenerateWithOnlyUserDataUpdateAndCheck() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // - create session with some data
        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        SessionInformationHolder sessionInfo = Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                null, userDataInDatabase, false);

        assert sessionInfo.accessToken != null;
        AccessToken.AccessTokenInfo accessTokenInfoBefore = AccessToken.getInfoFromAccessToken(process.getProcess(),
                sessionInfo.accessToken.token, false);

        assertEquals(accessTokenInfoBefore.userData, userDataInJWT);
        assertEquals(accessTokenInfoBefore.grants, null); // Also checks that it's V2

        // Check that userData payload updated
        JsonObject newUserDataInJWT = new JsonObject();
        newUserDataInJWT.addProperty("key2", "value2");

        SessionInformationHolder newSessionInfo = Session.regenerateToken(process.getProcess(),
                sessionInfo.accessToken.token, newUserDataInJWT, null, false);

        // Verify
        assert newSessionInfo.accessToken != null;
        SessionInformationHolder getSessionResponse = Session.getSession(process.getProcess(),
                newSessionInfo.accessToken.token, sessionInfo.antiCsrfToken, false, true);

        // Check that grant payload didn't change
        assertEquals(accessTokenInfoBefore.grants, null); // Also checks that it's V2
        // check userData and lmrt is different.
        assertEquals(getSessionResponse.session.userDataInJWT, newUserDataInJWT);

        AccessToken.AccessTokenInfo accessTokenInfoAfter = AccessToken.getInfoFromAccessToken(process.getProcess(),
                newSessionInfo.accessToken.token, false);
        assertNotEquals(accessTokenInfoBefore.lmrt, accessTokenInfoAfter.lmrt);

        assertNotEquals(accessTokenInfoAfter.userData, accessTokenInfoBefore.userData);
        assertEquals(accessTokenInfoAfter.grants, accessTokenInfoBefore.grants);

        process.kill();
        process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED);
    }

    // -> create V2 session -> verify userData exists
    // -> regenerate with different userData & no grant update allowing upgrade -> verify
    // -> check userData and lmrt are different but grants are the same
    @Test
    public void testCrateV2SessionWithUserDataRegenerateWithOnlyUserDataUpdateAndUpgrade() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // - create session with some data
        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        SessionInformationHolder sessionInfo = Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                null, userDataInDatabase, false);

        assert sessionInfo.accessToken != null;
        AccessToken.AccessTokenInfo accessTokenInfoBefore = AccessToken.getInfoFromAccessToken(process.getProcess(),
                sessionInfo.accessToken.token, true);

        assertEquals(accessTokenInfoBefore.userData, userDataInJWT);
        assertEquals(accessTokenInfoBefore.grants, null); // Also checks that it's V2

        // Check that userData payload updated
        JsonObject newUserDataInJWT = new JsonObject();
        newUserDataInJWT.addProperty("key2", "value2");

        SessionInformationHolder newSessionInfo = Session.regenerateToken(process.getProcess(),
                sessionInfo.accessToken.token, newUserDataInJWT, null, false);

        // Verify
        assert newSessionInfo.accessToken != null;
        SessionInformationHolder getSessionResponse = Session.getSession(process.getProcess(),
                newSessionInfo.accessToken.token, sessionInfo.antiCsrfToken, false, true);

        // Check that grant payload didn't change
        assertEquals(accessTokenInfoBefore.grants, new JsonObject()); // Also checks that it's V3 with empty grants

        // check userData and lmrt is different.
        assertEquals(getSessionResponse.session.userDataInJWT, newUserDataInJWT);

        AccessToken.AccessTokenInfo accessTokenInfoAfter = AccessToken.getInfoFromAccessToken(process.getProcess(),
                newSessionInfo.accessToken.token, false);
        assertNotEquals(accessTokenInfoBefore.lmrt, accessTokenInfoAfter.lmrt);

        assertNotEquals(accessTokenInfoAfter.userData, accessTokenInfoBefore.userData);
        assertEquals(accessTokenInfoAfter.grants, accessTokenInfoBefore.grants);

        process.kill();
        process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED);
    }

    // * - create session with some userData & grants -> verify userData & grants exists -> regenerate with different
    // grants & no userData -> verify ->
    // * check grants and lmrt are different but userData is the same
    @Test
    public void testCrateSessionWithUserDataRegenerateWithOnlyGrantsUpdateAndCheck() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // - create session with some data
        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");
        JsonObject initialGrantPayload = Utils.getExampleGrantPayload(0);

        SessionInformationHolder sessionInfo = Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                initialGrantPayload, userDataInDatabase, false);

        assert sessionInfo.accessToken != null;
        AccessToken.AccessTokenInfo accessTokenInfoBefore = AccessToken.getInfoFromAccessToken(process.getProcess(),
                sessionInfo.accessToken.token, false);

        assertEquals(accessTokenInfoBefore.userData, userDataInJWT);
        assertEquals(accessTokenInfoBefore.grants, initialGrantPayload);

        // regenerate with different payload
        JsonObject newGrantPayload = Utils.getExampleGrantPayload(1);

        SessionInformationHolder newSessionInfo = Session.regenerateToken(process.getProcess(),
                sessionInfo.accessToken.token, null, newGrantPayload, true);

        // Verify
        assert newSessionInfo.accessToken != null;
        SessionInformationHolder getSessionResponse = Session.getSession(process.getProcess(),
                newSessionInfo.accessToken.token, sessionInfo.antiCsrfToken, false, true);

        // Check that grant payload updated
        assertEquals(getSessionResponse.session.grants, newGrantPayload);
        // check userData is the same.
        assertEquals(getSessionResponse.session.userDataInJWT, userDataInJWT);

        AccessToken.AccessTokenInfo accessTokenInfoAfter = AccessToken.getInfoFromAccessToken(process.getProcess(),
                newSessionInfo.accessToken.token, false);
        assertNotEquals(accessTokenInfoBefore.lmrt, accessTokenInfoAfter.lmrt);

        assertEquals(accessTokenInfoAfter.userData, accessTokenInfoBefore.userData);
        assertNotEquals(accessTokenInfoAfter.grants, accessTokenInfoBefore.grants);

        process.kill();
        process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED);
    }

    // * - create session with some userData & grants -> verify userData & grants exists -> regenerate with empty
    // userData & grants -> verify -> check
    // * userData & grants and lmrt is different & expiry time is same.
    @Test
    public void testSessionRegenerateWithEmptyPayload() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // - create session with some data
        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");
        JsonObject initialGrantPayload = Utils.getExampleGrantPayload();

        SessionInformationHolder sessionInfo = Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                initialGrantPayload, userDataInDatabase, false);

        assert sessionInfo.accessToken != null;
        AccessToken.AccessTokenInfo accessTokenInfoBefore = AccessToken.getInfoFromAccessToken(process.getProcess(),
                sessionInfo.accessToken.token, false);

        // verify payload exists
        assertEquals(accessTokenInfoBefore.userData, userDataInJWT);
        assertEquals(accessTokenInfoBefore.grants, initialGrantPayload);

        // regenerate with empty payload
        JsonObject emptyPayload = new JsonObject();

        SessionInformationHolder newSessionInfo = Session.regenerateToken(process.getProcess(),
                sessionInfo.accessToken.token, emptyPayload, emptyPayload, true);

        // Verify
        assert newSessionInfo.accessToken != null;
        SessionInformationHolder getSessionResponse = Session.getSession(process.getProcess(),
                newSessionInfo.accessToken.token, sessionInfo.antiCsrfToken, false, true);
        assertEquals(getSessionResponse.session.userDataInJWT, emptyPayload);
        assertEquals(getSessionResponse.session.grants, emptyPayload);

        AccessToken.AccessTokenInfo accessTokenInfoAfter = AccessToken.getInfoFromAccessToken(process.getProcess(),
                newSessionInfo.accessToken.token, false);

        // check payload and lmrt is different & expiry time is same.
        assertEquals(accessTokenInfoAfter.userData, emptyPayload);
        assertEquals(accessTokenInfoAfter.grants, emptyPayload);
        assertNotEquals(accessTokenInfoAfter.lmrt, accessTokenInfoBefore.lmrt);
        assertEquals(accessTokenInfoAfter.expiryTime, accessTokenInfoBefore.expiryTime);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // * - create session with some payload -> verify payload exists -> regenerate with no payload -> verify -> check
    // * payload is same, but lmrt is different & expiry time is same.
    @Test
    public void testSessionRegenerateWithNoPayload() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // - create session with some data
        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");
        JsonObject initialGrantPayload = Utils.getExampleGrantPayload();

        SessionInformationHolder sessionInfo = Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                initialGrantPayload, userDataInDatabase, false);

        assert sessionInfo.accessToken != null;
        AccessToken.AccessTokenInfo accessTokenInfoBefore = AccessToken.getInfoFromAccessToken(process.getProcess(),
                sessionInfo.accessToken.token, false);
        assertEquals(accessTokenInfoBefore.userData, userDataInJWT);

        // regenerate with no payload

        SessionInformationHolder newSessionInfo = Session.regenerateToken(process.getProcess(),
                sessionInfo.accessToken.token, null, null, true);

        // Verify

        assert newSessionInfo.accessToken != null;
        SessionInformationHolder getSessionResponse = Session.getSession(process.getProcess(),
                newSessionInfo.accessToken.token, sessionInfo.antiCsrfToken, false, true);

        assertEquals(getSessionResponse.session.userDataInJWT, userDataInJWT);
        assertEquals(getSessionResponse.session.grants, initialGrantPayload);

        AccessToken.AccessTokenInfo accessTokenInfoAfter = AccessToken.getInfoFromAccessToken(process.getProcess(),
                newSessionInfo.accessToken.token, false);
        // check payload & expiry time is same nd lmrt is different.

        assertEquals(accessTokenInfoAfter.userData, userDataInJWT);
        assertEquals(accessTokenInfoAfter.grants, initialGrantPayload);
        assertNotEquals(accessTokenInfoAfter.lmrt, accessTokenInfoBefore.lmrt);
        assertEquals(accessTokenInfoAfter.expiryTime, accessTokenInfoBefore.expiryTime);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // * - create session with some payload -> verify payload exists -> let it expire
    // -> regenerate with different payload (should return accessToken as null) -> refresh -> verify
    // -> check payload and lmrt is different & expiry time is same.
    @Test
    public void testSessionRegenerateWithTokenExpiryAndRefresh() throws Exception {
        String[] args = { "../" };

        Utils.setValueInConfig("access_token_validity", "1");// 1 second validity

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");
        JsonObject initialGrantPayload = Utils.getExampleGrantPayload(0);

        // - create session with some payload
        SessionInformationHolder sessionInfo = Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                initialGrantPayload, userDataInDatabase, false);

        assert sessionInfo.accessToken != null;
        AccessToken.AccessTokenInfo accessTokenInfoBefore = AccessToken.getInfoFromAccessToken(process.getProcess(),
                sessionInfo.accessToken.token, false);

        // verify payload exists
        assertEquals(accessTokenInfoBefore.userData, userDataInJWT);
        assertEquals(accessTokenInfoBefore.grants, initialGrantPayload);

        // let it expire
        Thread.sleep(2000);

        // regenerate with different payload (should return accessToken as null)

        JsonObject newUserDataInJWT = new JsonObject();
        newUserDataInJWT.addProperty("key2", "value2");
        JsonObject grantPayload = Utils.getExampleGrantPayload(1);

        SessionInformationHolder newSessionInfo = Session.regenerateToken(process.getProcess(),
                sessionInfo.accessToken.token, newUserDataInJWT, grantPayload, true);

        assertNull(newSessionInfo.accessToken);

        assert sessionInfo.refreshToken != null;
        SessionInformationHolder refreshSessionInfo = Session.refreshSession(process.getProcess(),
                sessionInfo.refreshToken.token, sessionInfo.antiCsrfToken, false, true);

        // Verify
        assert refreshSessionInfo.accessToken != null;
        SessionInformationHolder getSessionResponse = Session.getSession(process.getProcess(),
                refreshSessionInfo.accessToken.token, refreshSessionInfo.antiCsrfToken, false, true);

        assertEquals(getSessionResponse.session.userDataInJWT, newUserDataInJWT);
        assertEquals(getSessionResponse.session.grants, grantPayload);

        // check payload and lmrt is different & expiry time is same.
        assert getSessionResponse.accessToken != null;
        AccessToken.AccessTokenInfo accessTokenInfoAfter = AccessToken
                .getInfoFromAccessTokenWithoutVerifying(getSessionResponse.accessToken.token);

        assertEquals(accessTokenInfoAfter.userData, newUserDataInJWT);
        assertEquals(accessTokenInfoAfter.grants, grantPayload);
        assertNotEquals(accessTokenInfoAfter.expiryTime, accessTokenInfoBefore.expiryTime); // expiry time is different
                                                                                            // for now, but later we
                                                                                            // will fix this and then
                                                                                            // this test will fail
        assertNotEquals(accessTokenInfoAfter.lmrt, accessTokenInfoBefore.lmrt);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    // * - create session with some payload -> verify payload exists -> change JWT signing key -> regenerate with
    // different payload -> refresh -> verify -> check payload and lmrt is different & expiry time is same.
    @Test
    public void testChangeJWTSigningKeyAndRegenerateWithDifferentPayload() throws Exception {
        String[] args = { "../" };

        Utils.setValueInConfig("access_token_signing_key_update_interval", "0.00027"); // 1 second
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");
        JsonObject initialGrantPayload = Utils.getExampleGrantPayload(0);

        // - create session with some payload
        SessionInformationHolder sessionInfo = Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                initialGrantPayload, userDataInDatabase, false);

        assert sessionInfo.accessToken != null;
        AccessToken.AccessTokenInfo accessTokenInfoBefore = AccessToken.getInfoFromAccessToken(process.getProcess(),
                sessionInfo.accessToken.token, false);

        // verify payload exists
        assertEquals(accessTokenInfoBefore.userData, userDataInJWT);
        assertEquals(accessTokenInfoBefore.grants, initialGrantPayload);

        // change JWT signing key by waiting for 2 seconds, access_token_signing_key_update_interval set to 1 second
        Thread.sleep(2000);

        // regenerate with different payload
        JsonObject newUserDataInJWT = new JsonObject();
        newUserDataInJWT.addProperty("key2", "value2");
        JsonObject grantPayload = Utils.getExampleGrantPayload(1);

        SessionInformationHolder regenerateSessionInfo = Session.regenerateToken(process.getProcess(),
                sessionInfo.accessToken.token, newUserDataInJWT, grantPayload, true);

        assertEquals(regenerateSessionInfo.session.userDataInJWT, newUserDataInJWT);
        assertEquals(regenerateSessionInfo.session.grants, grantPayload);

        // refresh
        assert sessionInfo.refreshToken != null;
        SessionInformationHolder refreshSessionInfo = Session.refreshSession(process.getProcess(),
                sessionInfo.refreshToken.token, sessionInfo.antiCsrfToken, false, true);

        // Verify
        assert refreshSessionInfo.accessToken != null;
        SessionInformationHolder getSessionResponse = Session.getSession(process.getProcess(),
                refreshSessionInfo.accessToken.token, refreshSessionInfo.antiCsrfToken, false, true);

        assertEquals(getSessionResponse.session.userDataInJWT, newUserDataInJWT);
        assertEquals(getSessionResponse.session.grants, grantPayload);

        AccessToken.AccessTokenInfo accessTokenInfoAfter = AccessToken.getInfoFromAccessToken(process.getProcess(),
                refreshSessionInfo.accessToken.token, false);

        assertNotEquals(accessTokenInfoAfter.lmrt, accessTokenInfoBefore.lmrt);
        assertEquals(accessTokenInfoAfter.userData, newUserDataInJWT);
        assertEquals(accessTokenInfoAfter.grants, grantPayload);

        assertNotEquals(accessTokenInfoAfter.expiryTime, accessTokenInfoBefore.expiryTime);// expiry time is different
                                                                                           // for now, but later we will
                                                                                           // fix this and then this
                                                                                           // test will fail

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // * - create session with some payload -> remove session from db -> regenerate with different payload -> should
    // throw unauthorised error
    @Test
    public void testCreateSessionRemoveFromDBRegenerateShouldThrowUnauthorisedError() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");
        JsonObject initialGrantPayload = Utils.getExampleGrantPayload(0);

        // - create session with some payload
        SessionInformationHolder sessionInfo = Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                initialGrantPayload, userDataInDatabase, false);

        // verify payload exists
        assertEquals(sessionInfo.session.userDataInJWT, userDataInJWT);
        assertEquals(sessionInfo.session.grants, initialGrantPayload);

        // remove session from db
        Session.revokeAllSessionsForUser(process.getProcess(), userId);

        // regenerate with different payload
        JsonObject newUserDataInJWT = new JsonObject();
        newUserDataInJWT.addProperty("key2", "value2");
        JsonObject newGrantPayload = Utils.getExampleGrantPayload(1);

        // should throw unauthorised error
        try {
            assert sessionInfo.accessToken != null;
            Session.regenerateToken(process.getProcess(), sessionInfo.accessToken.token, newUserDataInJWT,
                    newGrantPayload, true);
            fail();
        } catch (UnauthorisedException e) {
            assertEquals(e.getMessage(), "Session does not exist.");
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // * - create session with some payload -> verify -> check lmrt & payload is same & expiry time is same
    // -> refresh -> check lmrt & payload is same & expiry time is same -> verify
    // -> check payload and lmrt are same & expiry time is different.
    @Test
    public void testCreateSessionRefreshAndCheckAccessToken() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");
        JsonObject grantPayload = Utils.getExampleGrantPayload();

        // - create session with some payload
        SessionInformationHolder sessionInfo = Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                grantPayload, userDataInDatabase, false);

        assert sessionInfo.accessToken != null;
        AccessToken.AccessTokenInfo accessTokenInfoBefore = AccessToken.getInfoFromAccessToken(process.getProcess(),
                sessionInfo.accessToken.token, false);

        // verify payload exists
        assertEquals(accessTokenInfoBefore.userData, userDataInJWT);
        assertEquals(accessTokenInfoBefore.grants, grantPayload);

        // verify
        SessionInformationHolder getSession = Session.getSession(process.getProcess(), sessionInfo.accessToken.token,
                sessionInfo.antiCsrfToken, false, true);
        assertEquals(getSession.session.userDataInJWT, userDataInJWT);
        assertEquals(getSession.session.grants, grantPayload);

        AccessToken.AccessTokenInfo accessTokenInfoAfter = AccessToken.getInfoFromAccessToken(process.getProcess(),
                sessionInfo.accessToken.token, false);

        // check lmrt & payload is same & expiry time is same
        assertEquals(accessTokenInfoAfter.lmrt, accessTokenInfoBefore.lmrt);
        assertEquals(accessTokenInfoAfter.userData, userDataInJWT);
        assertEquals(accessTokenInfoAfter.grants, grantPayload);
        assertEquals(accessTokenInfoAfter.expiryTime, accessTokenInfoBefore.expiryTime);

        // refresh
        assert sessionInfo.refreshToken != null;
        SessionInformationHolder refreshSessionInfo = Session.refreshSession(process.getProcess(),
                sessionInfo.refreshToken.token, sessionInfo.antiCsrfToken, false, true);

        assert refreshSessionInfo.accessToken != null;
        AccessToken.AccessTokenInfo accessTokenInfoAfterRefresh = AccessToken
                .getInfoFromAccessToken(process.getProcess(), refreshSessionInfo.accessToken.token, false);

        // check lmrt & payload is same & expiry time is same

        assertNotEquals(accessTokenInfoAfterRefresh.lmrt, accessTokenInfoBefore.lmrt); // this is supposed to be the
                                                                                       // same. But for now, the feature
                                                                                       // is not implemented fully yet.
        assertEquals(accessTokenInfoAfterRefresh.userData, userDataInJWT);
        assertEquals(accessTokenInfoAfterRefresh.grants, grantPayload);
        assertNotEquals(accessTokenInfoAfterRefresh.expiryTime, accessTokenInfoBefore.expiryTime);

        // verify
        getSession = Session.getSession(process.getProcess(), refreshSessionInfo.accessToken.token,
                refreshSessionInfo.antiCsrfToken, false, true);

        assert getSession.accessToken != null;

        AccessToken.AccessTokenInfo accessTokenInfoAfterVerify = AccessToken
                .getInfoFromAccessToken(process.getProcess(), getSession.accessToken.token, false);

        // check payload and lmrt are same & expiry time is same.

        assertEquals(accessTokenInfoAfterVerify.lmrt, accessTokenInfoAfterRefresh.lmrt);
        assertEquals(accessTokenInfoAfterVerify.userData, userDataInJWT);
        assertEquals(accessTokenInfoAfterVerify.grants, grantPayload);
        assertNotEquals(accessTokenInfoAfterVerify.expiryTime, accessTokenInfoAfterRefresh.expiryTime);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

}
