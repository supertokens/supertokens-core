/*
 *    Copyright (c) 2020, VRAI Labs and/or its affiliates. All rights reserved.
 *
 *    This program is licensed under the SuperTokens Community License (the
 *    "License") as published by VRAI Labs. You may not use this file except in
 *    compliance with the License. You are not permitted to transfer or
 *    redistribute this file without express written permission from VRAI Labs.
 *
 *    A copy of the License is available in the file titled
 *    "SuperTokensLicense.pdf" inside this repository or included with your copy of
 *    the software or its source code. If you have not received a copy of the
 *    License, please write to VRAI Labs at team@supertokens.io.
 *
 *    Please read the License carefully before accessing, downloading, copying,
 *    using, modifying, merging, transferring or sharing this software. By
 *    undertaking any of these activities, you indicate your agreement to the terms
 *    of the License.
 *
 *    This program is distributed with certain software that is licensed under
 *    separate terms, as designated in a particular file or component or in
 *    included license documentation. VRAI Labs hereby grants you an additional
 *    permission to link the program and your derivative works with the separately
 *    licensed software that they have included with this program, however if you
 *    modify this program, you shall be solely liable to ensure compliance of the
 *    modified program with the terms of licensing of the separately licensed
 *    software.
 *
 *    Unless required by applicable law or agreed to in writing, this program is
 *    distributed under the License on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 *    CONDITIONS OF ANY KIND, either express or implied. See the License for the
 *    specific language governing permissions and limitations under the License.
 *
 */

package io.supertokens.test;

import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.config.Config;
import io.supertokens.cronjobs.CronTaskTest;
import io.supertokens.cronjobs.deletePastOrphanedTokens.DeletePastOrphanedTokens;
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
            StorageTransactionLogicException, SignatureException {

        String[] args = {"../", "DEV"};
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

        String[] args = {"../", "DEV"};
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
    public void nonOrphanedChildNotRemoved()
            throws InterruptedException, StorageQueryException, IOException,
            NoSuchAlgorithmException,
            StorageTransactionLogicException, InvalidKeyException, InvalidKeySpecException,
            UnauthorisedException, TokenTheftDetectedException, SignatureException {

        String[] args = {"../", "DEV"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);

        CronTaskTest.getInstance(process.getProcess()).setIntervalInSeconds(DeletePastOrphanedTokens.RESOURCE_KEY, 1);
        DeletePastOrphanedTokens.getInstance(process.getProcess())
                .setTimeInMSForHowLongToKeepThePastTokensForTesting(1000);

        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        SessionInformationHolder sessionInfo = Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                userDataInDatabase);
        assert sessionInfo.refreshToken != null;
        assert sessionInfo.accessToken != null;

        SessionInformationHolder refreshedSession = Session
                .refreshSession(process.getProcess(), sessionInfo.refreshToken.token);
        assert refreshedSession.refreshToken != null;

        Thread.sleep(2500);

        Session.refreshSession(process.getProcess(), refreshedSession.refreshToken.token);

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

        String[] args = {"../", "DEV"};
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
        assertNotNull(sessionInfo.antiCsrfToken);
        assert sessionInfo.idRefreshToken != null;
        assert sessionInfo.idRefreshToken.cookieSecure != null;


        SessionInformationHolder refreshedSession = Session
                .refreshSession(process.getProcess(), sessionInfo.refreshToken.token);

        assert refreshedSession.accessToken != null;
        assert refreshedSession.refreshToken != null;
        assertNotEquals(refreshedSession.antiCsrfToken, sessionInfo.antiCsrfToken);
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

        String[] args = {"../", "DEV"};
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
        assertNotNull(sessionInfo.antiCsrfToken);
        assert sessionInfo.idRefreshToken != null;
        assert sessionInfo.idRefreshToken.cookieSecure != null;

        SessionInformationHolder refreshedSession = Session
                .refreshSession(process.getProcess(), sessionInfo.refreshToken.token);

        assert refreshedSession.accessToken != null;
        assert refreshedSession.refreshToken != null;
        assertNotEquals(refreshedSession.antiCsrfToken, sessionInfo.antiCsrfToken);
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
        assert newSession.accessToken.domain != null;
        assertFalse(newSession.accessToken.cookieSecure);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void createVerifyRefreshVerifyRefresh() throws InterruptedException, StorageQueryException,
            NoSuchAlgorithmException, InvalidKeyException,
            IOException, InvalidKeySpecException,
            StorageTransactionLogicException, UnauthorisedException, TryRefreshTokenException,
            TokenTheftDetectedException, SignatureException {

        Utils.setValueInConfig("access_token_validity", "1");

        String[] args = {"../", "DEV"};
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
        assertEquals(StorageLayer.getStorageLayer(process.getProcess()).getNumberOfPastTokens(), 1);
        assertEquals(StorageLayer.getStorageLayer(process.getProcess()).getNumberOfSessions(), 1);
        assert sessionInfo.accessToken != null;
        assert sessionInfo.refreshToken != null;
        assertNotNull(sessionInfo.antiCsrfToken);

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
                    .refreshSession(process.getProcess(), sessionInfo.refreshToken.token);
            assert sessionInfo.refreshToken != null;
            assert sessionInfo.accessToken != null;

            assertEquals(StorageLayer.getStorageLayer(process.getProcess()).getNumberOfPastTokens(), 2 + i);
            assertEquals(StorageLayer.getStorageLayer(process.getProcess()).getNumberOfSessions(), 1);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
