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
import io.supertokens.pluginInterface.session.SessionInfo;
import io.supertokens.pluginInterface.session.sqlStorage.SessionSQLStorage;
import io.supertokens.session.Session;
import io.supertokens.session.accessToken.AccessToken;
import io.supertokens.session.info.SessionInformationHolder;
import io.supertokens.storageLayer.StorageLayer;
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

    @Rule
    public TestRule retryFlaky = Utils.retryFlakyTest();

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

        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
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

    // PLAN-002 unit 8 (decision 7) — regenerate re-issues strictly in place: the returned access token keeps the
    // original absolute expiry (no validity re-resolution and no re-roll of access_token_validity_jitter) and
    // carries the refresh-token lineage fields over unchanged. Jitter is set to its maximum and wall-clock is
    // advanced between mint and regenerate so that any re-resolution (now + validity * (1 - jitter)) would land on a
    // different expiry than the original, making the exact-equality assertion meaningful.
    @Test
    public void testRegenerateReIssuesInPlaceWithoutReRollingJitterOrLineage() throws Exception {
        String[] args = {"../"};

        Utils.setValueInConfig("access_token_validity_jitter", "0.25");

        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

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

        // advance wall-clock so a re-resolved validity would differ from the original expiry
        Thread.sleep(1500);

        JsonObject newUserDataInJWT = new JsonObject();
        newUserDataInJWT.addProperty("key2", "value2");

        SessionInformationHolder newSessionInfo = Session.regenerateToken(process.getProcess(),
                sessionInfo.accessToken.token, newUserDataInJWT);

        assert newSessionInfo.accessToken != null;
        AccessToken.AccessTokenInfo accessTokenInfoAfter = AccessToken.getInfoFromAccessToken(process.getProcess(),
                newSessionInfo.accessToken.token, false);

        // payload was updated, but the absolute expiry is byte-identical (no jitter re-roll, no validity
        // re-resolution)
        assertEquals(accessTokenInfoAfter.userData, newUserDataInJWT);
        assertEquals(accessTokenInfoBefore.expiryTime, accessTokenInfoAfter.expiryTime);

        // lineage fields are carried over unchanged
        assertEquals(accessTokenInfoBefore.refreshTokenHash1, accessTokenInfoAfter.refreshTokenHash1);
        assertEquals(accessTokenInfoBefore.parentRefreshTokenHash1, accessTokenInfoAfter.parentRefreshTokenHash1);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // PLAN-002 unit 8 (decision 7) — regenerate never reads or writes the CDI 5.5 rotation state
    // (refresh_token_hash_2 / prev_refresh_token_hash_2 / refresh_token_rotated_at). Regenerating a live token
    // leaves the current refresh-token hash intact and the grace-window columns null.
    @Test
    public void testRegenerateLiveTokenDoesNotTouchRotationState() throws Exception {
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
        String handle = sessionInfo.session.handle;

        SessionSQLStorage storage = (SessionSQLStorage) StorageLayer.getStorage(process.getProcess());

        SessionInfo before = storage.getSession(process.getAppForTesting(), handle);
        assertNull(before.prevRefreshTokenHash2);
        assertNull(before.refreshTokenRotatedAt);
        String refreshTokenHash2Before = before.refreshTokenHash2;
        assertNotNull(refreshTokenHash2Before);

        JsonObject newUserDataInJWT = new JsonObject();
        newUserDataInJWT.addProperty("key2", "value2");

        assert sessionInfo.accessToken != null;
        SessionInformationHolder newSessionInfo = Session.regenerateToken(process.getProcess(),
                sessionInfo.accessToken.token, newUserDataInJWT);
        assert newSessionInfo.accessToken != null;

        SessionInfo after = storage.getSession(process.getAppForTesting(), handle);
        // rotation state is completely untouched: current hash unchanged, grace-window columns still null
        assertEquals(refreshTokenHash2Before, after.refreshTokenHash2);
        assertNull(after.prevRefreshTokenHash2);
        assertNull(after.refreshTokenRotatedAt);
        // the payload update did land
        assertEquals(newUserDataInJWT, after.userDataInJWT);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // PLAN-002 unit 8 (decision 7) — for an expired input token, regenerate performs a DB-only payload update and
    // returns no access token, and still touches no rotation state.
    @Test
    public void testRegenerateExpiredTokenIsDbOnlyAndDoesNotTouchRotationState() throws Exception {
        String[] args = {"../"};

        Utils.setValueInConfig("access_token_validity", "2"); // 2 second validity

        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        SessionInformationHolder sessionInfo = Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                userDataInDatabase);
        String handle = sessionInfo.session.handle;

        SessionSQLStorage storage = (SessionSQLStorage) StorageLayer.getStorage(process.getProcess());
        SessionInfo before = storage.getSession(process.getAppForTesting(), handle);
        String refreshTokenHash2Before = before.refreshTokenHash2;
        assertNull(before.prevRefreshTokenHash2);
        assertNull(before.refreshTokenRotatedAt);

        // let the access token expire
        Thread.sleep(2500);

        JsonObject newUserDataInJWT = new JsonObject();
        newUserDataInJWT.addProperty("key2", "value2");

        assert sessionInfo.accessToken != null;
        SessionInformationHolder newSessionInfo = Session.regenerateToken(process.getProcess(),
                sessionInfo.accessToken.token, newUserDataInJWT);

        // no access token returned for an expired input; the client must refresh to obtain a new one
        assertNull(newSessionInfo.accessToken);

        SessionInfo after = storage.getSession(process.getAppForTesting(), handle);
        // DB-only payload update landed
        assertEquals(newUserDataInJWT, after.userDataInJWT);
        // rotation state untouched
        assertEquals(refreshTokenHash2Before, after.refreshTokenHash2);
        assertNull(after.prevRefreshTokenHash2);
        assertNull(after.refreshTokenRotatedAt);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
