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
import io.supertokens.exceptions.TryRefreshTokenException;
import io.supertokens.exceptions.UnauthorisedException;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.session.SessionStorage;
import io.supertokens.session.Session;
import io.supertokens.session.accessToken.AccessToken;
import io.supertokens.session.info.SessionInformationHolder;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import org.junit.*;
import org.junit.rules.TestRule;

import static junit.framework.TestCase.*;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public class SessionTest4 {

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
    public void checkForNumberOfDeletedSessions() throws Exception {

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

        assertEquals(Session.revokeSessionUsingSessionHandles(process.getProcess(),
                new String[]{sessionInfo.session.handle})[0], sessionInfo.session.handle);

        SessionInformationHolder sessionInfo2 = Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                userDataInDatabase);
        SessionInformationHolder sessionInfo3 = Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                userDataInDatabase);
        SessionInformationHolder sessionInfo4 = Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                userDataInDatabase);
        Session.createNewSession(process.getProcess(), userId, userDataInJWT, userDataInDatabase);
        Session.createNewSession(process.getProcess(), "userId2", userDataInJWT, userDataInDatabase);

        String[] handles = {sessionInfo2.session.handle, sessionInfo3.session.handle, sessionInfo4.session.handle};
        String[] actuallyRevoked = Session.revokeSessionUsingSessionHandles(process.getProcess(), handles);
        boolean revokedAll = true;
        assertEquals(actuallyRevoked.length, 3);
        for (String str : handles) {
            boolean revokedThis = false;
            for (String revoked : actuallyRevoked) {
                revokedThis = revokedThis || revoked.equals(str);
            }
            revokedAll = revokedAll && revokedThis;
        }
        assertTrue(revokedAll);

        assertEquals(((SessionStorage) StorageLayer.getStorage(process.getProcess()))
                .getNumberOfSessions(new TenantIdentifier(null, null, null)), 2);

        Session.createNewSession(process.getProcess(), userId, userDataInJWT, userDataInDatabase);
        Session.createNewSession(process.getProcess(), userId, userDataInJWT, userDataInDatabase);
        Session.createNewSession(process.getProcess(), userId, userDataInJWT, userDataInDatabase);

        assertEquals(Session.revokeAllSessionsForUser(process.getProcess(), userId).length, 4);

        assertEquals(((SessionStorage) StorageLayer.getStorage(process.getProcess()))
                .getNumberOfSessions(new TenantIdentifier(null, null, null)), 1);

        assertEquals(Session.revokeAllSessionsForUser(process.getProcess(), "userId2").length, 1);

        assertEquals(((SessionStorage) StorageLayer.getStorage(process.getProcess()))
                .getNumberOfSessions(new TenantIdentifier(null, null, null)), 0);

        assertEquals(Session.revokeSessionUsingSessionHandles(process.getProcess(), handles).length, 0);
        assertEquals(Session.revokeAllSessionsForUser(process.getProcess(), "userId2").length, 0);
        assertEquals(Session.revokeSessionUsingSessionHandles(process.getProcess(),
                new String[]{sessionInfo.session.handle}).length, 0);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    @Test
    public void gettingAndUpdatingSessionDataForNonExistantSession() throws Exception {

        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        try {
            Session.getSessionData(process.getProcess(), "random");
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

    @Test
    public void createVerifyRefreshVerifyRefresh() throws Exception {
        Utils.setValueInConfig("access_token_validity", "3");

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
        assertEquals(sessionInfo.session.userId, userId);
        assertEquals(sessionInfo.session.userDataInJWT.toString(), userDataInJWT.toString());
        assertEquals(((SessionStorage) StorageLayer.getStorage(process.getProcess()))
                .getNumberOfSessions(new TenantIdentifier(null, null, null)), 1);
        assert sessionInfo.accessToken != null;
        assert sessionInfo.refreshToken != null;
        assertNull(sessionInfo.antiCsrfToken);

        for (int i = 0; i < 5; i++) {
            SessionInformationHolder verifiedSession = Session.getSession(process.getProcess(),
                    sessionInfo.accessToken.token, sessionInfo.antiCsrfToken, false, true, false);

            assertEquals(verifiedSession.session.userDataInJWT.toString(), userDataInJWT.toString());
            assertEquals(verifiedSession.session.userId, userId);
            assertEquals(verifiedSession.session.handle, sessionInfo.session.handle);

            verifiedSession = Session.getSession(process.getProcess(), sessionInfo.accessToken.token,
                    sessionInfo.antiCsrfToken, false, true, false);

            assertEquals(verifiedSession.session.userDataInJWT.toString(), userDataInJWT.toString());
            assertEquals(verifiedSession.session.userId, userId);
            assertEquals(verifiedSession.session.handle, sessionInfo.session.handle);

            Thread.sleep(3500);

            try {
                Session.getSession(process.getProcess(), sessionInfo.accessToken.token, sessionInfo.antiCsrfToken,
                        false, true, false);
                fail();
            } catch (TryRefreshTokenException ignored) {
            }

            sessionInfo = Session.refreshSession(process.getProcess(), sessionInfo.refreshToken.token,
                    sessionInfo.antiCsrfToken, false, AccessToken.getLatestVersion());
            assert sessionInfo.refreshToken != null;
            assert sessionInfo.accessToken != null;

            assertEquals(((SessionStorage) StorageLayer.getStorage(process.getProcess()))
                    .getNumberOfSessions(new TenantIdentifier(null, null, null)), 1);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void verifyAccessTokenThatIsBelongsToGrandparentRefreshToken() throws Exception {

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
        assertEquals(sessionInfo.session.userId, userId);
        assertEquals(sessionInfo.session.userDataInJWT.toString(), userDataInJWT.toString());
        assertEquals(((SessionStorage) StorageLayer.getStorage(process.getProcess()))
                .getNumberOfSessions(new TenantIdentifier(null, null, null)), 1);
        assert sessionInfo.accessToken != null;
        assert sessionInfo.refreshToken != null;
        assertNull(sessionInfo.antiCsrfToken);

        SessionInformationHolder refreshedSession = Session.refreshSession(process.getProcess(),
                sessionInfo.refreshToken.token, sessionInfo.antiCsrfToken, false, AccessToken.getLatestVersion());
        assert refreshedSession.refreshToken != null;
        assert refreshedSession.accessToken != null;

        SessionInformationHolder refreshedSession2 = Session.refreshSession(process.getProcess(),
                refreshedSession.refreshToken.token, refreshedSession.antiCsrfToken, false,
                AccessToken.getLatestVersion());
        assert refreshedSession2.refreshToken != null;

        Session.refreshSession(process.getProcess(), refreshedSession2.refreshToken.token,
                refreshedSession2.antiCsrfToken, false, AccessToken.getLatestVersion());

        SessionInformationHolder verifiedSession = Session.getSession(process.getProcess(),
                refreshedSession.accessToken.token, refreshedSession.antiCsrfToken, false, true, false);

        assertEquals(verifiedSession.session.userDataInJWT.toString(), userDataInJWT.toString());
        assertEquals(verifiedSession.session.userId, userId);
        assertEquals(verifiedSession.session.handle, sessionInfo.session.handle);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.GET_SESSION_NEW_TOKENS));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void passInvalidRefreshTokenShouldGiveUnauthorisedError() throws Exception {

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

        try {
            Session.refreshSession(process.getProcess(), "INVALID_TOKEN" + sessionInfo.refreshToken,
                    sessionInfo.antiCsrfToken, true, AccessToken.getLatestVersion());
            Assert.fail();
        } catch (UnauthorisedException ignored) {
        }

        try {
            Session.refreshSession(process.getProcess(), sessionInfo.refreshToken + "INVALID_TOKEN",
                    sessionInfo.antiCsrfToken, true, AccessToken.getLatestVersion());
            Assert.fail();
        } catch (UnauthorisedException ignored) {
        }
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void checkThatExpiredSessionIsNotReturnedForUserNorCanItBeUpdated() throws Exception {

        Utils.setValueInConfig("access_token_validity", "3");
        Utils.setValueInConfig("refresh_token_validity", "0.08"); // 5 seconds

        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        SessionInformationHolder expiredSession = Session.createNewSession(process.getProcess(), "user",
                new JsonObject(), new JsonObject());
        Session.createNewSession(process.getProcess(), "user", new JsonObject(), new JsonObject());
        Session.createNewSession(process.getProcess(), "user", new JsonObject(), new JsonObject());
        Session.createNewSession(process.getProcess(), "user1", new JsonObject(), new JsonObject());

        Thread.sleep(6000);

        SessionInformationHolder nonExpiredSession = Session.createNewSession(process.getProcess(), "user",
                new JsonObject(), new JsonObject());
        Session.createNewSession(process.getProcess(), "user", new JsonObject(), new JsonObject());
        Session.createNewSession(process.getProcess(), "user1", new JsonObject(), new JsonObject());
        String[] sessions = Session.getAllNonExpiredSessionHandlesForUser(process.getProcess(), "user");

        assert sessions.length == 2;

        try {
            Session.getSession(process.getProcess(), expiredSession.session.handle);
            throw new Exception("Test failed");
        } catch (UnauthorisedException ignored) {
        }

        try {
            Session.updateSession(process.getProcess(), expiredSession.session.handle, new JsonObject(), null,
                    AccessToken.getLatestVersion());
            throw new Exception("Test failed");
        } catch (UnauthorisedException ignored) {
        }

        Session.getSession(process.getProcess(), nonExpiredSession.session.handle);
        Session.updateSession(process.getProcess(), nonExpiredSession.session.handle, new JsonObject(), null,
                AccessToken.getLatestVersion());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // session tests with long access and refresh token lifetimes
    @Test
    public void testCreatingSessionsWithLongAccessAndRefreshTokenLifeTimes() throws Exception {

        Utils.setValueInConfig("access_token_validity", "63072000"); // 2 years in seconds
        Utils.setValueInConfig("refresh_token_validity", "1051200"); // 2 years in minutes

        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        SessionInformationHolder sessionInfo = Session.createNewSession(process.getProcess(), "user", new JsonObject(),
                new JsonObject());
        long twoYearsInSeconds = 63072000;

        assertEquals(sessionInfo.accessToken.expiry - sessionInfo.accessToken.createdTime, twoYearsInSeconds * 1000);
        assertEquals(sessionInfo.refreshToken.expiry - sessionInfo.refreshToken.createdTime, twoYearsInSeconds * 1000);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testCreatingSessionsWithLongAccessAndRefreshTokenLifeTimesAndRefreshingTokens() throws Exception {

        Utils.setValueInConfig("access_token_validity", "63072000"); // 2 years in seconds
        Utils.setValueInConfig("refresh_token_validity", "1051200"); // 2 years in minutes

        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        SessionInformationHolder sessionInfo = Session.createNewSession(process.getProcess(), "user", new JsonObject(),
                new JsonObject());
        long twoYearsInSeconds = 63072000;

        assertEquals(sessionInfo.accessToken.expiry - sessionInfo.accessToken.createdTime, twoYearsInSeconds * 1000);
        assertEquals(sessionInfo.refreshToken.expiry - sessionInfo.refreshToken.createdTime, twoYearsInSeconds * 1000);

        SessionInformationHolder sessionInfo2 = Session.refreshSession(process.main, sessionInfo.refreshToken.token,
                null, false, AccessToken.getLatestVersion());

        assertFalse(sessionInfo.accessToken.token.equals(sessionInfo2.accessToken.token));
        assertFalse(sessionInfo.refreshToken.token.equals(sessionInfo2.refreshToken.token));

        assertEquals(sessionInfo2.accessToken.expiry - sessionInfo2.accessToken.createdTime, twoYearsInSeconds * 1000);
        assertEquals(sessionInfo2.refreshToken.expiry - sessionInfo2.refreshToken.createdTime,
                twoYearsInSeconds * 1000);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void createNewSessionAndUpdateSession() throws Exception {

        Utils.setValueInConfig("access_token_validity", "63072000"); // 2 years in seconds
        Utils.setValueInConfig("refresh_token_validity", "1051200"); // 2 years in minutes
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        SessionInformationHolder sessionInfo = Session.createNewSession(process.getProcess(), "user", new JsonObject(),
                new JsonObject());
        long twoYearsInSeconds = 63072000;

        assertEquals(sessionInfo.accessToken.expiry - sessionInfo.accessToken.createdTime, twoYearsInSeconds * 1000);
        assertEquals(sessionInfo.refreshToken.expiry - sessionInfo.refreshToken.createdTime, twoYearsInSeconds * 1000);
        JsonObject sessionData = new JsonObject();
        sessionData.addProperty("test", "value");

        JsonObject jwtData = new JsonObject();
        jwtData.addProperty("test", "value");

        Session.updateSession(process.main, sessionInfo.session.handle, sessionData, jwtData,
                AccessToken.getLatestVersion());

        io.supertokens.pluginInterface.session.SessionInfo sessionInfo2 = Session.getSession(process.main,
                sessionInfo.session.handle);

        assertEquals(sessionInfo2.expiry - sessionInfo2.timeCreated, twoYearsInSeconds * 1000);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
