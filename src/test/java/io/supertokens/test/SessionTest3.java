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
import io.supertokens.cronjobs.CronTaskTest;
import io.supertokens.cronjobs.deleteExpiredSessions.DeleteExpiredSessions;
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

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public class SessionTest3 {

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
    public void revokeSessionWithBlacklisting()
            throws InterruptedException, StorageQueryException, NoSuchAlgorithmException, InvalidKeyException,
            IOException, InvalidKeySpecException,
            StorageTransactionLogicException, TokenTheftDetectedException, TryRefreshTokenException,
            UnauthorisedException, SignatureException {

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
        assert sessionInfo.refreshToken != null;
        assert sessionInfo.accessToken != null;

        Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                userDataInDatabase);

        assertEquals(StorageLayer.getStorageLayer(process.getProcess()).getNumberOfPastTokens(), 2);
        assertEquals(StorageLayer.getStorageLayer(process.getProcess()).getNumberOfSessions(), 2);

        Session.revokeSessionUsingSessionHandles(process.getProcess(), new String[]{sessionInfo.session.handle});
        assertEquals(StorageLayer.getStorageLayer(process.getProcess()).getNumberOfPastTokens(), 2);
        assertEquals(StorageLayer.getStorageLayer(process.getProcess()).getNumberOfSessions(), 1);

        try {
            Session.refreshSession(process.getProcess(), sessionInfo.refreshToken.token);
            fail();
        } catch (UnauthorisedException e) {

        }

        SessionInformationHolder verifiedSession = Session
                .getSession(process.getProcess(), sessionInfo.accessToken.token, sessionInfo.antiCsrfToken, true);
        assertEquals(verifiedSession.session.userId, sessionInfo.session.userId);
        assertEquals(verifiedSession.session.userDataInJWT.toString(), sessionInfo.session.userDataInJWT.toString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    @Test
    public void removeSessionFromDbButAccessTokenStillValidUntilExpiry()
            throws InterruptedException, StorageQueryException, NoSuchAlgorithmException, InvalidKeyException,
            IOException, InvalidKeySpecException,
            StorageTransactionLogicException, TokenTheftDetectedException, TryRefreshTokenException,
            UnauthorisedException, SignatureException {

        Utils.setValueInConfig("access_token_validity", "1");   // 1 second

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
        assert sessionInfo.refreshToken != null;
        assert sessionInfo.accessToken != null;

        assertEquals(Session.revokeSessionUsingSessionHandles(process.getProcess(),
                new String[]{sessionInfo.session.handle})[0], sessionInfo.session.handle);

        Session.getSession(process.getProcess(), sessionInfo.accessToken.token, sessionInfo.antiCsrfToken,
                true);

        Thread.sleep(1500);

        try {
            Session.getSession(process.getProcess(), sessionInfo.accessToken.token, sessionInfo.antiCsrfToken,
                    true);
            fail();
        } catch (TryRefreshTokenException ignored) {

        }

        try {
            Session.refreshSession(process.getProcess(), sessionInfo.refreshToken.token);
            fail();
        } catch (UnauthorisedException ignored) {

        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    @Test
    public void revokeAllSessionsForUserWithoutBlacklisting()
            throws InterruptedException, StorageQueryException, NoSuchAlgorithmException, InvalidKeyException,
            IOException, InvalidKeySpecException,
            StorageTransactionLogicException, TryRefreshTokenException,
            UnauthorisedException, SignatureException {

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
        assert sessionInfo.refreshToken != null;
        assert sessionInfo.accessToken != null;

        SessionInformationHolder sessionInfo2 = Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                userDataInDatabase);
        assert sessionInfo2.refreshToken != null;
        assert sessionInfo2.accessToken != null;

        SessionInformationHolder sessionInfo3 = Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                userDataInDatabase);
        assert sessionInfo3.refreshToken != null;
        assert sessionInfo3.accessToken != null;

        Session.createNewSession(process.getProcess(), "userId2", userDataInJWT,
                userDataInDatabase);

        assertEquals(StorageLayer.getStorageLayer(process.getProcess()).getNumberOfPastTokens(), 4);
        assertEquals(StorageLayer.getStorageLayer(process.getProcess()).getNumberOfSessions(), 4);

        assertEquals(Session.revokeAllSessionsForUser(process.getProcess(), userId).length, 3);

        assertEquals(StorageLayer.getStorageLayer(process.getProcess()).getNumberOfPastTokens(), 4);
        assertEquals(StorageLayer.getStorageLayer(process.getProcess()).getNumberOfSessions(), 1);

        Session.getSession(process.getProcess(), sessionInfo.accessToken.token, sessionInfo.antiCsrfToken,
                true);
        Session.getSession(process.getProcess(), sessionInfo2.accessToken.token, sessionInfo2.antiCsrfToken,
                true);
        Session.getSession(process.getProcess(), sessionInfo3.accessToken.token, sessionInfo3.antiCsrfToken,
                true);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    @Test
    public void removeExpiredSessions()
            throws InterruptedException, StorageQueryException, NoSuchAlgorithmException, InvalidKeyException,
            IOException, InvalidKeySpecException,
            StorageTransactionLogicException, SignatureException {

        Utils.setValueInConfig("refresh_token_validity", "" + 1.0 / 60.0);

        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);

        CronTaskTest.getInstance(process.getProcess()).setIntervalInSeconds(DeleteExpiredSessions.RESOURCE_KEY, 1);

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

        SessionInformationHolder sessionInfo2 = Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                userDataInDatabase);
        assert sessionInfo2.refreshToken != null;
        assert sessionInfo2.accessToken != null;

        SessionInformationHolder sessionInfo3 = Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                userDataInDatabase);
        assert sessionInfo3.refreshToken != null;
        assert sessionInfo3.accessToken != null;


        assertEquals(StorageLayer.getStorageLayer(process.getProcess()).getNumberOfPastTokens(), 3);
        assertEquals(StorageLayer.getStorageLayer(process.getProcess()).getNumberOfSessions(), 3);

        Thread.sleep(2500);
        Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                userDataInDatabase);

        assertEquals(StorageLayer.getStorageLayer(process.getProcess()).getNumberOfPastTokens(), 4);
        assertEquals(StorageLayer.getStorageLayer(process.getProcess()).getNumberOfSessions(), 1);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    @Test
    public void removeOldOrphanedSessions()
            throws InterruptedException, StorageQueryException, NoSuchAlgorithmException, InvalidKeyException,
            IOException, InvalidKeySpecException,
            StorageTransactionLogicException, SignatureException {

        Utils.setValueInConfig("refresh_token_validity", "" + 1.0 / 60.0);

        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);

        CronTaskTest.getInstance(process.getProcess()).setIntervalInSeconds(DeleteExpiredSessions.RESOURCE_KEY, 1);
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

        SessionInformationHolder sessionInfo2 = Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                userDataInDatabase);
        assert sessionInfo2.refreshToken != null;
        assert sessionInfo2.accessToken != null;

        SessionInformationHolder sessionInfo3 = Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                userDataInDatabase);
        assert sessionInfo3.refreshToken != null;
        assert sessionInfo3.accessToken != null;


        assertEquals(StorageLayer.getStorageLayer(process.getProcess()).getNumberOfPastTokens(), 3);
        assertEquals(StorageLayer.getStorageLayer(process.getProcess()).getNumberOfSessions(), 3);

        Thread.sleep(3500);
        Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                userDataInDatabase);

        assertEquals(StorageLayer.getStorageLayer(process.getProcess()).getNumberOfPastTokens(), 1);
        assertEquals(StorageLayer.getStorageLayer(process.getProcess()).getNumberOfSessions(), 1);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    @Test
    public void doNotRemoveOldOrphanedSessionsIfSessionNotExpired()
            throws InterruptedException, StorageQueryException, NoSuchAlgorithmException, InvalidKeyException,
            IOException, InvalidKeySpecException,
            StorageTransactionLogicException, SignatureException {

        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);

        CronTaskTest.getInstance(process.getProcess()).setIntervalInSeconds(DeleteExpiredSessions.RESOURCE_KEY, 1);
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

        SessionInformationHolder sessionInfo2 = Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                userDataInDatabase);
        assert sessionInfo2.refreshToken != null;
        assert sessionInfo2.accessToken != null;

        SessionInformationHolder sessionInfo3 = Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                userDataInDatabase);
        assert sessionInfo3.refreshToken != null;
        assert sessionInfo3.accessToken != null;


        assertEquals(StorageLayer.getStorageLayer(process.getProcess()).getNumberOfPastTokens(), 3);
        assertEquals(StorageLayer.getStorageLayer(process.getProcess()).getNumberOfSessions(), 3);

        Thread.sleep(2500);
        Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                userDataInDatabase);

        assertEquals(StorageLayer.getStorageLayer(process.getProcess()).getNumberOfPastTokens(), 4);
        assertEquals(StorageLayer.getStorageLayer(process.getProcess()).getNumberOfSessions(), 4);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    @Test
    public void doNotRemoveOldOrphanedSessionsIfNotOrphanedYetButSessionExpired()
            throws InterruptedException, StorageQueryException, NoSuchAlgorithmException, InvalidKeyException,
            IOException, InvalidKeySpecException,
            StorageTransactionLogicException, SignatureException {

        Utils.setValueInConfig("refresh_token_validity", "" + 1.0 / 60.0);

        String[] args = {"../"};
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

        SessionInformationHolder sessionInfo2 = Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                userDataInDatabase);
        assert sessionInfo2.refreshToken != null;
        assert sessionInfo2.accessToken != null;

        SessionInformationHolder sessionInfo3 = Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                userDataInDatabase);
        assert sessionInfo3.refreshToken != null;
        assert sessionInfo3.accessToken != null;


        assertEquals(StorageLayer.getStorageLayer(process.getProcess()).getNumberOfPastTokens(), 3);
        assertEquals(StorageLayer.getStorageLayer(process.getProcess()).getNumberOfSessions(), 3);

        Thread.sleep(2500);
        Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                userDataInDatabase);

        assertEquals(StorageLayer.getStorageLayer(process.getProcess()).getNumberOfPastTokens(), 4);
        assertEquals(StorageLayer.getStorageLayer(process.getProcess()).getNumberOfSessions(), 4);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    @Test
    public void doNotDetectTokenTheftPostPastTokenThresholdTime()
            throws InterruptedException, StorageQueryException, NoSuchAlgorithmException, InvalidKeyException,
            IOException, InvalidKeySpecException,
            StorageTransactionLogicException, UnauthorisedException, TokenTheftDetectedException,
            TryRefreshTokenException, SignatureException {

        String[] args = {"../"};
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
        assert refreshedSession.accessToken != null;

        assertEquals(StorageLayer.getStorageLayer(process.getProcess()).getNumberOfPastTokens(), 2);
        assertEquals(StorageLayer.getStorageLayer(process.getProcess()).getNumberOfSessions(), 1);

        SessionInformationHolder latestSession = Session
                .getSession(process.getProcess(), refreshedSession.accessToken.token, refreshedSession.antiCsrfToken,
                        true);
        assert latestSession.accessToken != null;

        Thread.sleep(2500);

        assertEquals(StorageLayer.getStorageLayer(process.getProcess()).getNumberOfPastTokens(), 1);
        assertEquals(StorageLayer.getStorageLayer(process.getProcess()).getNumberOfSessions(), 1);

        try {
            Session.refreshSession(process.getProcess(), sessionInfo.refreshToken.token);
            fail();
        } catch (UnauthorisedException e) {
            assertEquals(e.getMessage(), "Refresh token not found in database. Please create a new session.");
        }

        Session.getSession(process.getProcess(), refreshedSession.accessToken.token, refreshedSession.antiCsrfToken,
                true);

        Session.getSession(process.getProcess(), latestSession.accessToken.token, refreshedSession.antiCsrfToken, true);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

}
