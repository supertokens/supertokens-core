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

    // * - create session with some payload -> verify payload exists -> regenerate with different payload -> verify ->
    // * check payload and lmrt is different.
    @Test
    public void testCrateSessionWithPayloadRegenerateWithDifferentPayloadAndCheck() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // - create session with some data
        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        SessionInformationHolder sessionInfo = Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                userDataInDatabase);

        assert sessionInfo.accessToken != null;
        AccessToken.AccessTokenInfo accessTokenInfoBefore = AccessToken.getInfoFromAccessToken(process.getProcess(),
                sessionInfo.accessToken.token, false);

        assertEquals(accessTokenInfoBefore.userData, userDataInJWT);

        // regenerate with different payload
        JsonObject newUserDataInJWT = new JsonObject();
        newUserDataInJWT.addProperty("key2", "value2");

        SessionInformationHolder newSessionInfo = Session.regenerateToken(process.getProcess(),
                sessionInfo.accessToken.token, newUserDataInJWT);

        // Verify
        assert newSessionInfo.accessToken != null;
        SessionInformationHolder getSessionResponse = Session.getSession(process.getProcess(),
                newSessionInfo.accessToken.token, sessionInfo.antiCsrfToken, false, true, false);

        // check payload and lmrt is different.
        assertEquals(getSessionResponse.session.userDataInJWT, newUserDataInJWT);

        AccessToken.AccessTokenInfo accessTokenInfoAfter = AccessToken.getInfoFromAccessToken(process.getProcess(),
                newSessionInfo.accessToken.token, false);

        assertNotEquals(accessTokenInfoAfter.userData, accessTokenInfoBefore.userData);

        process.kill();
        process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED);

    }

    // * - create session with some payload -> verify payload exists -> regenerate with empty payload -> verify -> check
    // * payload and lmrt is different & expiry time is same.
    @Test
    public void testSessionRegenerateWithEmptyPayload() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // - create session with some data
        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        SessionInformationHolder sessionInfo = Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                userDataInDatabase);

        assert sessionInfo.accessToken != null;
        AccessToken.AccessTokenInfo accessTokenInfoBefore = AccessToken.getInfoFromAccessToken(process.getProcess(),
                sessionInfo.accessToken.token, false);

        // verify payload exists
        assertEquals(accessTokenInfoBefore.userData, userDataInJWT);

        // regenerate with empty payload
        JsonObject emptyUserDataInJWT = new JsonObject();

        SessionInformationHolder newSessionInfo = Session.regenerateToken(process.getProcess(),
                sessionInfo.accessToken.token, emptyUserDataInJWT);

        // Verify
        assert newSessionInfo.accessToken != null;
        SessionInformationHolder getSessionResponse = Session.getSession(process.getProcess(),
                newSessionInfo.accessToken.token, sessionInfo.antiCsrfToken, false, true, false);
        assertEquals(getSessionResponse.session.userDataInJWT, emptyUserDataInJWT);

        AccessToken.AccessTokenInfo accessTokenInfoAfter = AccessToken.getInfoFromAccessToken(process.getProcess(),
                newSessionInfo.accessToken.token, false);

        // check payload and lmrt is different & expiry time is same.
        assertEquals(accessTokenInfoAfter.userData, emptyUserDataInJWT);
        assertEquals(accessTokenInfoAfter.expiryTime, accessTokenInfoBefore.expiryTime);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // * - create session with some payload -> verify payload exists -> regenerate with no payload -> verify -> check
    // * payload is same, but lmrt is different & expiry time is same.
    @Test
    public void testSessionRegenerateWithNoPayload() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // - create session with some data
        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        SessionInformationHolder sessionInfo = Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                userDataInDatabase);

        assert sessionInfo.accessToken != null;
        AccessToken.AccessTokenInfo accessTokenInfoBefore = AccessToken.getInfoFromAccessToken(process.getProcess(),
                sessionInfo.accessToken.token, false);
        assertEquals(accessTokenInfoBefore.userData, userDataInJWT);

        // regenerate with no payload

        SessionInformationHolder newSessionInfo = Session.regenerateToken(process.getProcess(),
                sessionInfo.accessToken.token, null);

        // Verify

        assert newSessionInfo.accessToken != null;
        SessionInformationHolder getSessionResponse = Session.getSession(process.getProcess(),
                newSessionInfo.accessToken.token, sessionInfo.antiCsrfToken, false, true, false);

        assertEquals(getSessionResponse.session.userDataInJWT, userDataInJWT);

        AccessToken.AccessTokenInfo accessTokenInfoAfter = AccessToken.getInfoFromAccessToken(process.getProcess(),
                newSessionInfo.accessToken.token, false);
        // check payload & expiry time is same nd lmrt is different.

        assertEquals(accessTokenInfoAfter.userData, userDataInJWT);
        assertEquals(accessTokenInfoAfter.expiryTime, accessTokenInfoBefore.expiryTime);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // * - create session with some payload -> verify payload exists -> let it expire -> regenerate with different
    // payload (should return accessToken as null) -> refresh -> verify -> check payload and lmrt is different &
    // expiry time is same.
    @Test
    public void testSessionRegenerateWithTokenExpiryAndRefresh() throws Exception {
        String[] args = {"../"};

        Utils.setValueInConfig("access_token_validity", "2");// 1 second validity

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        // - create session with some payload
        SessionInformationHolder sessionInfo = Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                userDataInDatabase);

        assert sessionInfo.accessToken != null;
        AccessToken.AccessTokenInfo accessTokenInfoBefore = AccessToken.getInfoFromAccessToken(process.getProcess(),
                sessionInfo.accessToken.token, false);

        // verify payload exists
        assertEquals(accessTokenInfoBefore.userData, userDataInJWT);

        // let it expire
        Thread.sleep(2500);

        // regenerate with different payload (should return accessToken as null)

        JsonObject newUserDataInJWT = new JsonObject();
        newUserDataInJWT.addProperty("key2", "value2");

        SessionInformationHolder newSessionInfo = Session.regenerateToken(process.getProcess(),
                sessionInfo.accessToken.token, newUserDataInJWT);

        assertNull(newSessionInfo.accessToken);

        assert sessionInfo.refreshToken != null;
        SessionInformationHolder refreshSessionInfo = Session.refreshSession(process.getProcess(),
                sessionInfo.refreshToken.token, sessionInfo.antiCsrfToken, false, AccessToken.getLatestVersion());

        // Verify
        assert refreshSessionInfo.accessToken != null;
        SessionInformationHolder getSessionResponse = Session.getSession(process.getProcess(),
                refreshSessionInfo.accessToken.token, refreshSessionInfo.antiCsrfToken, false, true, false);

        assertEquals(getSessionResponse.session.userDataInJWT, newUserDataInJWT);

        // check payload is different & expiry time is same.

        assert getSessionResponse.accessToken != null;
        AccessToken.AccessTokenInfo accessTokenInfoAfter = AccessToken
                .getInfoFromAccessTokenWithoutVerifying(getSessionResponse.accessToken.token);

        assertEquals(accessTokenInfoAfter.userData, newUserDataInJWT);
        // expiry time is different for now, but later we will fix this and then this test will fail
        assertNotEquals(accessTokenInfoAfter.expiryTime, accessTokenInfoBefore.expiryTime);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    // * - create session with some payload -> verify payload exists -> change JWT signing key -> regenerate with
    // different payload -> refresh -> verify -> check payload and lmrt is
    // different & expiry time is same.

    @Test
    public void testChangeJWTSigningKeyAndRegenerateWithDifferentPayload() throws Exception {
        String[] args = {"../"};

        Utils.setValueInConfig("access_token_dynamic_signing_key_update_interval", "0.00027"); // 1 second
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        // - create session with some payload
        SessionInformationHolder sessionInfo = Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                userDataInDatabase);

        assert sessionInfo.accessToken != null;
        AccessToken.AccessTokenInfo accessTokenInfoBefore = AccessToken.getInfoFromAccessToken(process.getProcess(),
                sessionInfo.accessToken.token, false);

        // verify payload exists
        assertEquals(accessTokenInfoBefore.userData, userDataInJWT);

        // change JWT signing key by waiting for 1.5 seconds, access_token_dynamic_signing_key_update_interval set to
        // 1 second
        Thread.sleep(2000);

        // regenerate with different payload
        JsonObject newUserDataInJWT = new JsonObject();
        newUserDataInJWT.addProperty("key2", "value2");

        SessionInformationHolder regenerateSessionInfo = Session.regenerateToken(process.getProcess(),
                sessionInfo.accessToken.token, newUserDataInJWT);

        assertEquals(regenerateSessionInfo.session.userDataInJWT, newUserDataInJWT);

        // refresh
        assert sessionInfo.refreshToken != null;
        SessionInformationHolder refreshSessionInfo = Session.refreshSession(process.getProcess(),
                sessionInfo.refreshToken.token, sessionInfo.antiCsrfToken, false, AccessToken.getLatestVersion());

        // Verify
        assert refreshSessionInfo.accessToken != null;
        SessionInformationHolder getSessionResponse = Session.getSession(process.getProcess(),
                refreshSessionInfo.accessToken.token, refreshSessionInfo.antiCsrfToken, false, true, false);

        assertEquals(getSessionResponse.session.userDataInJWT, newUserDataInJWT);

        AccessToken.AccessTokenInfo accessTokenInfoAfter = AccessToken.getInfoFromAccessToken(process.getProcess(),
                refreshSessionInfo.accessToken.token, false);

        assertEquals(accessTokenInfoAfter.userData, newUserDataInJWT);

        assertNotEquals(accessTokenInfoAfter.expiryTime, accessTokenInfoBefore.expiryTime);// expiry time is different
        // for now, but later we will
        // fix this
        // and then this test will fail

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    // * - create session with some payload -> remove session from db -> regenerate with different payload -> should
    // throw unauthorised error

    @Test
    public void testCreateSessionRemoveFromDBRegenerateShouldThrowUnauthorisedError() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        // - create session with some payload
        SessionInformationHolder sessionInfo = Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                userDataInDatabase);

        // verify payload exists
        assertEquals(sessionInfo.session.userDataInJWT, userDataInJWT);

        // remove session from db
        Session.revokeAllSessionsForUser(process.getProcess(), userId);

        // regenerate with different payload
        JsonObject newUserDataInJWT = new JsonObject();
        newUserDataInJWT.addProperty("key2", "value2");

        // should throw unauthorised error
        try {
            assert sessionInfo.accessToken != null;
            Session.regenerateToken(process.getProcess(), sessionInfo.accessToken.token, newUserDataInJWT);
            fail();
        } catch (UnauthorisedException e) {
            assertEquals(e.getMessage(), "Session does not exist.");
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // * - create session with some payload -> verify -> check lmrt & payload is same & expiry time is same ->
    // refresh ->
    // * check lmrt & payload is
    // * same & expiry time is same -> verify -> check payload and lmrt are same & expiry time is different.
    @Test
    public void testCreateSessionRefreshAndCheckAccessTokenV2() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        // - create session with some payload
        SessionInformationHolder sessionInfo = Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                userDataInDatabase, false, AccessToken.VERSION.V2, false);

        assert sessionInfo.accessToken != null;
        AccessToken.AccessTokenInfo accessTokenInfoBefore = AccessToken.getInfoFromAccessToken(process.getProcess(),
                sessionInfo.accessToken.token, false);

        // verify payload exists
        assertEquals(accessTokenInfoBefore.userData, userDataInJWT);

        // verify
        SessionInformationHolder getSession = Session.getSession(process.getProcess(), sessionInfo.accessToken.token,
                sessionInfo.antiCsrfToken, false, true, false);
        assertEquals(getSession.session.userDataInJWT, userDataInJWT);

        AccessToken.AccessTokenInfo accessTokenInfoAfter = AccessToken.getInfoFromAccessToken(process.getProcess(),
                sessionInfo.accessToken.token, false);

        // check payload is same & expiry time is same
        assertEquals(accessTokenInfoAfter.userData, userDataInJWT);
        assertEquals(accessTokenInfoAfter.expiryTime, accessTokenInfoBefore.expiryTime);

        // refresh
        assert sessionInfo.refreshToken != null;
        SessionInformationHolder refreshSessionInfo = Session.refreshSession(process.getProcess(),
                sessionInfo.refreshToken.token, sessionInfo.antiCsrfToken, false, AccessToken.VERSION.V2);

        assert refreshSessionInfo.accessToken != null;
        AccessToken.AccessTokenInfo accessTokenInfoAfterRefresh = AccessToken
                .getInfoFromAccessToken(process.getProcess(), refreshSessionInfo.accessToken.token, false);

        // check payload is same & expiry time is same

        assertEquals(accessTokenInfoAfterRefresh.userData, userDataInJWT);
        assertNotEquals(accessTokenInfoAfterRefresh.expiryTime, accessTokenInfoBefore.expiryTime);

        // verify
        getSession = Session.getSession(process.getProcess(), refreshSessionInfo.accessToken.token,
                refreshSessionInfo.antiCsrfToken, false, true, false);

        assert getSession.accessToken != null;

        AccessToken.AccessTokenInfo accessTokenInfoAfterVerify = AccessToken
                .getInfoFromAccessToken(process.getProcess(), getSession.accessToken.token, false);

        // check payload are same & expiry time is same.

        assertEquals(accessTokenInfoAfterVerify.userData, userDataInJWT);
        assertNotEquals(accessTokenInfoAfterVerify.expiryTime, accessTokenInfoAfterRefresh.expiryTime);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    // * - create session with some payload -> verify -> check lmrt & payload is same & expiry time is same ->
    // refresh ->
    // * check lmrt & payload is
    // * same & expiry time is same -> verify -> check payload and lmrt are same & expiry time is different.
    @Test
    public void testCreateSessionRefreshAndCheckAccessTokenV3() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        // - create session with some payload
        SessionInformationHolder sessionInfo = Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                userDataInDatabase, false, AccessToken.getLatestVersion(), false);

        assert sessionInfo.accessToken != null;
        AccessToken.AccessTokenInfo accessTokenInfoBefore = AccessToken.getInfoFromAccessToken(process.getProcess(),
                sessionInfo.accessToken.token, false);

        // verify payload exists
        assertEquals(accessTokenInfoBefore.userData, userDataInJWT);

        // verify
        SessionInformationHolder getSession = Session.getSession(process.getProcess(), sessionInfo.accessToken.token,
                sessionInfo.antiCsrfToken, false, true, false);
        assertEquals(getSession.session.userDataInJWT, userDataInJWT);

        AccessToken.AccessTokenInfo accessTokenInfoAfter = AccessToken.getInfoFromAccessToken(process.getProcess(),
                sessionInfo.accessToken.token, false);

        // check payload is same & expiry time is same
        assertEquals(accessTokenInfoAfter.userData, userDataInJWT);
        assertEquals(accessTokenInfoAfter.expiryTime, accessTokenInfoBefore.expiryTime);

        // We need to wait at least a second to make sure the expiry times are different
        Thread.sleep(1000);
        // refresh
        assert sessionInfo.refreshToken != null;
        SessionInformationHolder refreshSessionInfo = Session.refreshSession(process.getProcess(),
                sessionInfo.refreshToken.token, sessionInfo.antiCsrfToken, false, AccessToken.getLatestVersion());

        assert refreshSessionInfo.accessToken != null;
        AccessToken.AccessTokenInfo accessTokenInfoAfterRefresh = AccessToken
                .getInfoFromAccessToken(process.getProcess(), refreshSessionInfo.accessToken.token, false);

        // check payload is same & expiry time is same
        assertEquals(accessTokenInfoAfterRefresh.userData, userDataInJWT);
        assertNotEquals(accessTokenInfoAfterRefresh.expiryTime, accessTokenInfoBefore.expiryTime);

        Thread.sleep(1000);

        // verify
        getSession = Session.getSession(process.getProcess(), refreshSessionInfo.accessToken.token,
                refreshSessionInfo.antiCsrfToken, false, true, false);

        assert getSession.accessToken != null;

        AccessToken.AccessTokenInfo accessTokenInfoAfterVerify = AccessToken
                .getInfoFromAccessToken(process.getProcess(), getSession.accessToken.token, false);

        // check payload & expiry time is same.
        assertEquals(accessTokenInfoAfterVerify.userData, userDataInJWT);
        assertNotEquals(accessTokenInfoAfterVerify.expiryTime, accessTokenInfoAfterRefresh.expiryTime);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }


    // * - create session with some payload -> verify -> check lmrt & payload is same & expiry time is same ->
    // refresh ->
    // * check lmrt & payload is
    // * same & expiry time is same -> verify -> check payload and lmrt are same & expiry time is different.
    @Test
    public void testCreateSessionRefreshAndCheckAccessTokenMigrationToV3() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        // - create session with some payload
        SessionInformationHolder sessionInfo = Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                userDataInDatabase, false, AccessToken.VERSION.V2, false);

        assert sessionInfo.accessToken != null;
        AccessToken.AccessTokenInfo accessTokenInfoBefore = AccessToken.getInfoFromAccessToken(process.getProcess(),
                sessionInfo.accessToken.token, false);

        // verify payload exists
        assertEquals(accessTokenInfoBefore.userData, userDataInJWT);

        // verify
        SessionInformationHolder getSession = Session.getSession(process.getProcess(), sessionInfo.accessToken.token,
                sessionInfo.antiCsrfToken, false, true, false);
        assertEquals(getSession.session.userDataInJWT, userDataInJWT);

        AccessToken.AccessTokenInfo accessTokenInfoAfter = AccessToken.getInfoFromAccessToken(process.getProcess(),
                sessionInfo.accessToken.token, false);

        // check payload is same & expiry time is same
        assertEquals(accessTokenInfoAfter.userData, userDataInJWT);
        assertEquals(accessTokenInfoAfter.expiryTime, accessTokenInfoBefore.expiryTime);

        // refresh
        assert sessionInfo.refreshToken != null;
        SessionInformationHolder refreshSessionInfo = Session.refreshSession(process.getProcess(),
                sessionInfo.refreshToken.token, sessionInfo.antiCsrfToken, false, AccessToken.getLatestVersion());

        assert refreshSessionInfo.accessToken != null;
        AccessToken.AccessTokenInfo accessTokenInfoAfterRefresh = AccessToken
                .getInfoFromAccessToken(process.getProcess(), refreshSessionInfo.accessToken.token, false);

        // check payload is same & expiry time is same
        assertEquals(accessTokenInfoAfterRefresh.userData, userDataInJWT);
        assertNotEquals(accessTokenInfoAfterRefresh.expiryTime, accessTokenInfoBefore.expiryTime);
        Thread.sleep(1000);

        // verify
        getSession = Session.getSession(process.getProcess(), refreshSessionInfo.accessToken.token,
                refreshSessionInfo.antiCsrfToken, false, true, false);

        assert getSession.accessToken != null;

        AccessToken.AccessTokenInfo accessTokenInfoAfterVerify = AccessToken
                .getInfoFromAccessToken(process.getProcess(), getSession.accessToken.token, false);

        // check payload is same & expiry time is same.
        assertEquals(accessTokenInfoAfterVerify.userData, userDataInJWT);
        assertNotEquals(accessTokenInfoAfterVerify.expiryTime, accessTokenInfoAfterRefresh.expiryTime);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
