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
import io.supertokens.config.Config;
import io.supertokens.exceptions.TokenTheftDetectedException;
import io.supertokens.exceptions.TryRefreshTokenException;
import io.supertokens.exceptions.UnauthorisedException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.session.Session;
import io.supertokens.session.info.SessionInformationHolder;
import io.supertokens.storageLayer.StorageLayer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;

import static junit.framework.TestCase.*;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

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
    public void checkForNumberOfDeletedSessions()
            throws InterruptedException, StorageQueryException, NoSuchAlgorithmException, InvalidKeyException,
            IOException, InvalidKeySpecException,
            StorageTransactionLogicException, SignatureException, IllegalBlockSizeException, BadPaddingException,
            InvalidAlgorithmParameterException, NoSuchPaddingException {

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
        Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                userDataInDatabase);
        Session.createNewSession(process.getProcess(), "userId2", userDataInJWT,
                userDataInDatabase);

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

        assertEquals(StorageLayer.getStorageLayer(process.getProcess()).getNumberOfSessions(), 2);

        Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                userDataInDatabase);
        Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                userDataInDatabase);
        Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                userDataInDatabase);

        assertEquals(Session.revokeAllSessionsForUser(process.getProcess(), userId).length, 4);

        assertEquals(StorageLayer.getStorageLayer(process.getProcess()).getNumberOfSessions(), 1);

        assertEquals(Session.revokeAllSessionsForUser(process.getProcess(), "userId2").length, 1);

        assertEquals(StorageLayer.getStorageLayer(process.getProcess()).getNumberOfSessions(), 0);

        assertEquals(Session.revokeSessionUsingSessionHandles(process.getProcess(), handles).length, 0);
        assertEquals(Session.revokeAllSessionsForUser(process.getProcess(), "userId2").length, 0);
        assertEquals(Session.revokeSessionUsingSessionHandles(process.getProcess(),
                new String[]{sessionInfo.session.handle}).length, 0);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    @Test
    public void gettingAndUpdatingSessionDataForNonExistantSession()
            throws InterruptedException, StorageQueryException {

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
            Session.updateSession(process.getProcess(), "random", new JsonObject(), null, null);
            fail();
        } catch (UnauthorisedException e) {
            assertEquals(e.getMessage(), "Session does not exist.");
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    @Test
    public void checkingWithDifferentCookiePathAndDomain()
            throws IOException, InterruptedException, StorageQueryException, NoSuchAlgorithmException,
            InvalidKeyException, InvalidAlgorithmParameterException, NoSuchPaddingException, BadPaddingException,
            InvalidKeySpecException, IllegalBlockSizeException, StorageTransactionLogicException, UnauthorisedException,
            TokenTheftDetectedException, TryRefreshTokenException, SignatureException {
        Utils.setValueInConfig("refresh_api_path", "/refreshPath");
        Utils.setValueInConfig("access_token_path", "/accessPath");
        Utils.setValueInConfig("cookie_domain", "localhost");

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

        assert sessionInfo.accessToken != null;
        assert sessionInfo.refreshToken != null;
        assertEquals(sessionInfo.accessToken.cookiePath, "/accessPath");
        assertEquals(sessionInfo.accessToken.domain, "localhost");
        assert sessionInfo.accessToken.cookieSecure != null;
        assertEquals((boolean) sessionInfo.accessToken.cookieSecure,
                Config.getConfig(process.getProcess()).getCookieSecure(process.getProcess()));
        assertEquals(sessionInfo.refreshToken.cookiePath, "/refreshPath");
        assertEquals(sessionInfo.refreshToken.domain, "localhost");
        assert sessionInfo.refreshToken.cookieSecure != null;
        assertEquals((boolean) sessionInfo.refreshToken.cookieSecure,
                Config.getConfig(process.getProcess()).getCookieSecure(process.getProcess()));
        assertNull(sessionInfo.antiCsrfToken);
        assert sessionInfo.idRefreshToken != null;
        assert sessionInfo.idRefreshToken.cookieSecure != null;


        SessionInformationHolder refreshedSession = Session
                .refreshSession(process.getProcess(), sessionInfo.refreshToken.token, sessionInfo.antiCsrfToken);

        assert refreshedSession.accessToken != null;
        assert refreshedSession.refreshToken != null;
        assertNotEquals(refreshedSession.idRefreshToken, sessionInfo.idRefreshToken);
        assertNotEquals(refreshedSession.accessToken.token, sessionInfo.accessToken.token);
        assertNotEquals(refreshedSession.refreshToken.token, sessionInfo.refreshToken.token);
        assertEquals(refreshedSession.session.handle, sessionInfo.session.handle);
        assertEquals(refreshedSession.session.userId, sessionInfo.session.userId);
        assertEquals(refreshedSession.session.userDataInJWT.toString(), sessionInfo.session.userDataInJWT.toString());
        assertEquals(refreshedSession.accessToken.cookiePath, "/accessPath");
        assertEquals(refreshedSession.accessToken.domain, "localhost");
        assert refreshedSession.accessToken.cookieSecure != null;
        assertEquals((boolean) refreshedSession.accessToken.cookieSecure,
                Config.getConfig(process.getProcess()).getCookieSecure(process.getProcess()));
        assertEquals(refreshedSession.refreshToken.cookiePath, "/refreshPath");
        assertEquals(refreshedSession.refreshToken.domain, "localhost");
        assert refreshedSession.refreshToken.cookieSecure != null;
        assertFalse(refreshedSession.refreshToken.cookieSecure);
        assert refreshedSession.idRefreshToken != null;
        assert refreshedSession.idRefreshToken.cookieSecure != null;

        SessionInformationHolder newSession = Session.getSession(process.getProcess(),
                refreshedSession.accessToken.token, refreshedSession.antiCsrfToken, true);

        assert newSession.accessToken != null;
        assertNotEquals(newSession.accessToken.token, refreshedSession.accessToken.token);
        assertNotEquals(newSession.accessToken.expiry, refreshedSession.accessToken.expiry);
        assertNotEquals(newSession.accessToken.createdTime, refreshedSession.accessToken.createdTime);
        assertEquals(newSession.session.userDataInJWT.toString(), refreshedSession.session.userDataInJWT.toString());
        assertEquals(newSession.accessToken.cookiePath, "/accessPath");
        assertEquals(newSession.accessToken.domain, "localhost");
        assert newSession.accessToken.cookieSecure != null;
        assertFalse(newSession.accessToken.cookieSecure);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void checkingWithCookieSecureFalse()
            throws IOException, InterruptedException, StorageQueryException, NoSuchAlgorithmException,
            InvalidKeyException, InvalidAlgorithmParameterException, NoSuchPaddingException, BadPaddingException,
            InvalidKeySpecException, IllegalBlockSizeException, StorageTransactionLogicException, UnauthorisedException,
            TokenTheftDetectedException, TryRefreshTokenException, SignatureException {
        Utils.setValueInConfig("cookie_secure", "false");

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

        assert sessionInfo.accessToken != null;
        assert sessionInfo.refreshToken != null;
        assert sessionInfo.accessToken.cookieSecure != null;
        assertFalse(sessionInfo.accessToken.cookieSecure);
        assert sessionInfo.refreshToken.cookieSecure != null;
        assertFalse(sessionInfo.refreshToken.cookieSecure);
        assertNull(sessionInfo.antiCsrfToken);
        assert sessionInfo.idRefreshToken != null;
        assert sessionInfo.idRefreshToken.cookieSecure != null;

        SessionInformationHolder refreshedSession = Session
                .refreshSession(process.getProcess(), sessionInfo.refreshToken.token, sessionInfo.antiCsrfToken);

        assert refreshedSession.accessToken != null;
        assert refreshedSession.refreshToken != null;
        assertNotEquals(refreshedSession.idRefreshToken, sessionInfo.idRefreshToken);
        assertNotEquals(refreshedSession.accessToken.token, sessionInfo.accessToken.token);
        assertNotEquals(refreshedSession.refreshToken.token, sessionInfo.refreshToken.token);
        assertEquals(refreshedSession.session.handle, sessionInfo.session.handle);
        assertEquals(refreshedSession.session.userId, sessionInfo.session.userId);
        assertEquals(refreshedSession.session.userDataInJWT.toString(), sessionInfo.session.userDataInJWT.toString());
        assert refreshedSession.accessToken.cookieSecure != null;
        assertFalse(refreshedSession.accessToken.cookieSecure);
        assert refreshedSession.refreshToken.cookieSecure != null;
        assertFalse(refreshedSession.refreshToken.cookieSecure);
        assert refreshedSession.idRefreshToken != null;
        assert refreshedSession.idRefreshToken.cookieSecure != null;

        SessionInformationHolder newSession = Session.getSession(process.getProcess(),
                refreshedSession.accessToken.token, refreshedSession.antiCsrfToken, true);

        assert newSession.accessToken != null;
        assertNotEquals(newSession.accessToken.token, refreshedSession.accessToken.token);
        assertNotEquals(newSession.accessToken.expiry, refreshedSession.accessToken.expiry);
        assertNotEquals(newSession.accessToken.createdTime, refreshedSession.accessToken.createdTime);
        assertEquals(newSession.session.userDataInJWT.toString(), refreshedSession.session.userDataInJWT.toString());
        assert newSession.accessToken.cookieSecure != null;
        assert newSession.accessToken.domain == null;
        assertFalse(newSession.accessToken.cookieSecure);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void createVerifyRefreshVerifyRefresh() throws InterruptedException, StorageQueryException,
            NoSuchAlgorithmException, InvalidKeyException,
            IOException, InvalidKeySpecException,
            StorageTransactionLogicException, UnauthorisedException, TryRefreshTokenException,
            TokenTheftDetectedException, SignatureException, IllegalBlockSizeException, BadPaddingException,
            InvalidAlgorithmParameterException, NoSuchPaddingException {

        Utils.setValueInConfig("access_token_validity", "1");

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
        assertEquals(StorageLayer.getStorageLayer(process.getProcess()).getNumberOfSessions(), 1);
        assert sessionInfo.accessToken != null;
        assert sessionInfo.refreshToken != null;
        assertNull(sessionInfo.antiCsrfToken);

        for (int i = 0; i < 5; i++) {
            SessionInformationHolder verifiedSession = Session.getSession(process.getProcess(),
                    sessionInfo.accessToken.token, sessionInfo.antiCsrfToken, true);

            assertEquals(verifiedSession.session.userDataInJWT.toString(), userDataInJWT.toString());
            assertEquals(verifiedSession.session.userId, userId);
            assertEquals(verifiedSession.session.handle, sessionInfo.session.handle);

            verifiedSession = Session.getSession(process.getProcess(),
                    sessionInfo.accessToken.token, sessionInfo.antiCsrfToken, true);

            assertEquals(verifiedSession.session.userDataInJWT.toString(), userDataInJWT.toString());
            assertEquals(verifiedSession.session.userId, userId);
            assertEquals(verifiedSession.session.handle, sessionInfo.session.handle);

            Thread.sleep(1500);

            try {
                Session.getSession(process.getProcess(),
                        sessionInfo.accessToken.token, sessionInfo.antiCsrfToken, true);
                fail();
            } catch (TryRefreshTokenException ignored) {
            }

            sessionInfo = Session
                    .refreshSession(process.getProcess(), sessionInfo.refreshToken.token, sessionInfo.antiCsrfToken);
            assert sessionInfo.refreshToken != null;
            assert sessionInfo.accessToken != null;

            assertEquals(StorageLayer.getStorageLayer(process.getProcess()).getNumberOfSessions(), 1);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void verifyAccessTokenThatIsBelongsToGrandparentRefreshToken()
            throws InterruptedException, StorageQueryException,
            NoSuchAlgorithmException, InvalidKeyException,
            IOException, InvalidKeySpecException,
            StorageTransactionLogicException, UnauthorisedException, TryRefreshTokenException,
            TokenTheftDetectedException, SignatureException, IllegalBlockSizeException, BadPaddingException,
            InvalidAlgorithmParameterException, NoSuchPaddingException {

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
        assertEquals(StorageLayer.getStorageLayer(process.getProcess()).getNumberOfSessions(), 1);
        assert sessionInfo.accessToken != null;
        assert sessionInfo.refreshToken != null;
        assertNull(sessionInfo.antiCsrfToken);

        SessionInformationHolder refreshedSession = Session
                .refreshSession(process.getProcess(), sessionInfo.refreshToken.token, sessionInfo.antiCsrfToken);
        assert refreshedSession.refreshToken != null;
        assert refreshedSession.accessToken != null;


        SessionInformationHolder refreshedSession2 = Session
                .refreshSession(process.getProcess(), refreshedSession.refreshToken.token,
                        refreshedSession.antiCsrfToken);
        assert refreshedSession2.refreshToken != null;

        Session.refreshSession(process.getProcess(), refreshedSession2.refreshToken.token,
                refreshedSession2.antiCsrfToken);


        SessionInformationHolder verifiedSession = Session.getSession(process.getProcess(),
                refreshedSession.accessToken.token, refreshedSession.antiCsrfToken, true);

        assertEquals(verifiedSession.session.userDataInJWT.toString(), userDataInJWT.toString());
        assertEquals(verifiedSession.session.userId, userId);
        assertEquals(verifiedSession.session.handle, sessionInfo.session.handle);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.GET_SESSION_NEW_TOKENS));


        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
