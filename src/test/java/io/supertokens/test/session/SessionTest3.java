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
import io.supertokens.cronjobs.CronTaskTest;
import io.supertokens.cronjobs.deleteExpiredSessions.DeleteExpiredSessions;
import io.supertokens.exceptions.TokenTheftDetectedException;
import io.supertokens.exceptions.TryRefreshTokenException;
import io.supertokens.exceptions.UnauthorisedException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.session.SessionStorage;
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

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
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
    public void revokeSessionWithBlacklistingRefreshSessionAndGetSessionThrows() throws Exception {
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

        Session.createNewSession(process.getProcess(), userId, userDataInJWT, userDataInDatabase);

        assertEquals(((SessionStorage) StorageLayer.getStorage(process.getProcess()))
                .getNumberOfSessions(new TenantIdentifier(null, null, null)), 2);

        Session.revokeSessionUsingSessionHandles(process.getProcess(), new String[]{sessionInfo.session.handle});
        assertEquals(((SessionStorage) StorageLayer.getStorage(process.getProcess()))
                .getNumberOfSessions(new TenantIdentifier(null, null, null)), 1);

        try {
            Session.refreshSession(process.getProcess(), sessionInfo.refreshToken.token, sessionInfo.antiCsrfToken,
                    false, AccessToken.getLatestVersion());
            fail();
        } catch (UnauthorisedException e) {

        }

        try {
            Session.getSession(process.getProcess(), sessionInfo.accessToken.token, sessionInfo.antiCsrfToken, false,
                    true, true);
            fail();
        } catch (UnauthorisedException e) {

        }
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    @Test
    public void removeSessionFromDbButAccessTokenStillValidUntilExpiry() throws Exception {

        Utils.setValueInConfig("access_token_validity", "2"); // 1 second

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

        Session.getSession(process.getProcess(), sessionInfo.accessToken.token, sessionInfo.antiCsrfToken, false, true,
                false);

        Thread.sleep(2500);

        try {
            Session.getSession(process.getProcess(), sessionInfo.accessToken.token, sessionInfo.antiCsrfToken, false,
                    true, false);
            fail();
        } catch (TryRefreshTokenException ignored) {

        }

        try {
            Session.refreshSession(process.getProcess(), sessionInfo.refreshToken.token, sessionInfo.antiCsrfToken,
                    false, AccessToken.getLatestVersion());
            fail();
        } catch (UnauthorisedException ignored) {

        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    @Test
    public void revokeAllSessionsForUserWithoutBlacklisting() throws Exception {

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

        Session.createNewSession(process.getProcess(), "userId2", userDataInJWT, userDataInDatabase);

        assertEquals(((SessionStorage) StorageLayer.getStorage(process.getProcess()))
                .getNumberOfSessions(new TenantIdentifier(null, null, null)), 4);

        assertEquals(Session.revokeAllSessionsForUser(process.getProcess(), userId).length, 3);

        assertEquals(((SessionStorage) StorageLayer.getStorage(process.getProcess()))
                .getNumberOfSessions(new TenantIdentifier(null, null, null)), 1);

        Session.getSession(process.getProcess(), sessionInfo.accessToken.token, sessionInfo.antiCsrfToken, false, true,
                false);
        Session.getSession(process.getProcess(), sessionInfo2.accessToken.token, sessionInfo2.antiCsrfToken, false,
                true, false);
        Session.getSession(process.getProcess(), sessionInfo3.accessToken.token, sessionInfo3.antiCsrfToken, false,
                true, false);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    @Test
    public void removeExpiredSessions() throws Exception {

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

        assertEquals(((SessionStorage) StorageLayer.getStorage(process.getProcess()))
                .getNumberOfSessions(new TenantIdentifier(null, null, null)), 3);

        Thread.sleep(2500);
        Session.createNewSession(process.getProcess(), userId, userDataInJWT, userDataInDatabase);

        assertEquals(((SessionStorage) StorageLayer.getStorage(process.getProcess()))
                .getNumberOfSessions(new TenantIdentifier(null, null, null)), 1);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

}
