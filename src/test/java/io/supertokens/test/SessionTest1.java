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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.supertokens.Main;
import io.supertokens.ProcessState;
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
import java.io.UnsupportedEncodingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.*;

public class SessionTest1 {

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
    public void createAndGetSession() throws InterruptedException, StorageQueryException,
            NoSuchAlgorithmException, InvalidKeyException,
            IOException, InvalidKeySpecException,
            StorageTransactionLogicException, TryRefreshTokenException, UnauthorisedException, SignatureException,
            IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException, NoSuchPaddingException {

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
        assertNull(sessionInfo.antiCsrfToken);
        assert sessionInfo.idRefreshToken != null;

        SessionInformationHolder verifiedSession = Session.getSession(process.getProcess(),
                sessionInfo.accessToken.token, sessionInfo.antiCsrfToken, true);

        assertNull(verifiedSession.accessToken);
        assertEquals(verifiedSession.session.userDataInJWT.toString(), userDataInJWT.toString());
        assertEquals(verifiedSession.session.userId, userId);
        assertEquals(verifiedSession.session.handle, sessionInfo.session.handle);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void createAndGetSessionNoAntiCSRF() throws InterruptedException, StorageQueryException,
            NoSuchAlgorithmException, InvalidKeyException,
            IOException, InvalidKeySpecException,
            StorageTransactionLogicException, TryRefreshTokenException, UnauthorisedException, SignatureException,
            IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException, NoSuchPaddingException {

        Utils.setValueInConfig("enable_anti_csrf", "false");

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
        assertNull(sessionInfo.antiCsrfToken);

        SessionInformationHolder verifiedSession = Session.getSession(process.getProcess(),
                sessionInfo.accessToken.token, null, false);

        assertNull(verifiedSession.accessToken);
        assertEquals(verifiedSession.session.userDataInJWT.toString(), userDataInJWT.toString());
        assertEquals(verifiedSession.session.userId, userId);
        assertEquals(verifiedSession.session.handle, sessionInfo.session.handle);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void createSessionWhichExpiresInOneSecondCheck() throws InterruptedException, StorageQueryException,
            NoSuchAlgorithmException, InvalidKeyException, InvalidAlgorithmParameterException, NoSuchPaddingException
            , BadPaddingException, IOException, InvalidKeySpecException, IllegalBlockSizeException,
            StorageTransactionLogicException, UnauthorisedException, SignatureException {

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

        assert sessionInfo.accessToken != null;

        Thread.sleep(1500);

        try {
            Session.getSession(process.getProcess(),
                    sessionInfo.accessToken.token, sessionInfo.antiCsrfToken, true);
            fail();
        } catch (TryRefreshTokenException ignored) {
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void createNewSessionAndAlterJWTPayload() throws InterruptedException, StorageQueryException,
            NoSuchAlgorithmException, InvalidKeyException,
            IOException, InvalidKeySpecException,
            StorageTransactionLogicException, UnauthorisedException, SignatureException, IllegalBlockSizeException,
            BadPaddingException, InvalidAlgorithmParameterException, NoSuchPaddingException {

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
        assertNull(sessionInfo.antiCsrfToken);

        String token = sessionInfo.accessToken.token;
        String[] splittedToken = token.split("\\.");
        JsonObject payload =
                (JsonObject) new JsonParser().parse(io.supertokens.utils.Utils.convertFromBase64(splittedToken[1]));
        payload.addProperty("new", "value");
        String newPayload = io.supertokens.utils.Utils.convertToBase64(payload.toString());
        token = splittedToken[0] + "." + newPayload + "." + splittedToken[2];

        try {
            Session.getSession(process.getProcess(),
                    token, sessionInfo.antiCsrfToken, true);
            fail();
        } catch (TryRefreshTokenException ignored) {
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void createAndGetSessionWithEmptyJWTPayload() throws InterruptedException, StorageQueryException,
            NoSuchAlgorithmException, InvalidKeyException,
            UnsupportedEncodingException, InvalidKeySpecException,
            StorageTransactionLogicException, TryRefreshTokenException, UnauthorisedException, SignatureException,
            IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException, NoSuchPaddingException {

        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        JsonObject userDataInDatabase = new JsonObject();

        SessionInformationHolder sessionInfo = Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                userDataInDatabase);

        assertEquals(sessionInfo.session.userId, userId);
        assertEquals(sessionInfo.session.userDataInJWT.toString(), userDataInJWT.toString());
        assertEquals(StorageLayer.getStorageLayer(process.getProcess()).getNumberOfSessions(), 1);
        assert sessionInfo.accessToken != null;
        assertNull(sessionInfo.antiCsrfToken);

        SessionInformationHolder verifiedSession = Session.getSession(process.getProcess(),
                sessionInfo.accessToken.token, sessionInfo.antiCsrfToken, true);

        assertNull(verifiedSession.accessToken);
        assertEquals(verifiedSession.session.userDataInJWT.toString(), userDataInJWT.toString());
        assertEquals(verifiedSession.session.userId, userId);
        assertEquals(verifiedSession.session.handle, sessionInfo.session.handle);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void createAndGetSessionWithComplexJWTPayload() throws InterruptedException, StorageQueryException,
            NoSuchAlgorithmException, InvalidKeyException,
            UnsupportedEncodingException, InvalidKeySpecException,
            StorageTransactionLogicException, TryRefreshTokenException, UnauthorisedException, SignatureException,
            IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException, NoSuchPaddingException {

        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key1", "value1");

        JsonArray arr = new JsonArray();
        JsonObject el1 = new JsonObject();
        el1.addProperty("el0", "val0");
        el1.addProperty("el1", "val1");
        arr.add(el1);
        arr.add(new JsonObject());
        userDataInJWT.add("complex", arr);

        JsonObject userDataInDatabase = new JsonObject();

        SessionInformationHolder sessionInfo = Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                userDataInDatabase);

        assertEquals(sessionInfo.session.userId, userId);
        assertEquals(sessionInfo.session.userDataInJWT.toString(), userDataInJWT.toString());
        assertEquals(StorageLayer.getStorageLayer(process.getProcess()).getNumberOfSessions(), 1);
        assert sessionInfo.accessToken != null;
        assertNull(sessionInfo.antiCsrfToken);

        SessionInformationHolder verifiedSession = Session.getSession(process.getProcess(),
                sessionInfo.accessToken.token, sessionInfo.antiCsrfToken, true);

        assertNull(verifiedSession.accessToken);
        assertEquals(verifiedSession.session.userDataInJWT.toString(), userDataInJWT.toString());
        assertEquals(verifiedSession.session.userId, userId);
        assertEquals(verifiedSession.session.handle, sessionInfo.session.handle);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }


    @Test
    public void createAndGetSessionWithSigningKeyChange()
            throws InterruptedException, StorageQueryException, NoSuchAlgorithmException, InvalidKeyException,
            InvalidAlgorithmParameterException, NoSuchPaddingException, BadPaddingException,
            IOException, InvalidKeySpecException, IllegalBlockSizeException,
            StorageTransactionLogicException, TryRefreshTokenException, UnauthorisedException,
            TokenTheftDetectedException, SignatureException {

        Utils.setValueInConfig("access_token_signing_key_update_interval", "0.00027");  // 1 second

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

        Thread.sleep(1500);

        try {
            Session.getSession(process.getProcess(),
                    sessionInfo.accessToken.token, sessionInfo.antiCsrfToken, true);
            fail();
        } catch (TryRefreshTokenException ignored) {
        }

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
        assert refreshedSession.idRefreshToken != null;

        SessionInformationHolder newSession = Session.getSession(process.getProcess(),
                refreshedSession.accessToken.token, refreshedSession.antiCsrfToken, true);

        assert newSession.accessToken != null;
        assertNotEquals(newSession.accessToken.token, refreshedSession.accessToken.token);
        assertNotEquals(newSession.accessToken.expiry, refreshedSession.accessToken.expiry);
        assertNotEquals(newSession.accessToken.createdTime, refreshedSession.accessToken.createdTime);
        assertEquals(newSession.session.userDataInJWT.toString(), refreshedSession.session.userDataInJWT.toString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }


    @Test
    public void refreshSessionTestWithAntiCsrf()
            throws IOException, InterruptedException, StorageQueryException, NoSuchAlgorithmException,
            InvalidKeyException,
            InvalidKeySpecException, StorageTransactionLogicException,
            TryRefreshTokenException, UnauthorisedException, TokenTheftDetectedException, SignatureException,
            IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException, NoSuchPaddingException {

        Utils.setValueInConfig("access_token_validity", "1");
        Utils.setValueInConfig("enable_anti_csrf", "true");

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

        Thread.sleep(1500);

        try {
            Session.getSession(process.getProcess(),
                    sessionInfo.accessToken.token, sessionInfo.antiCsrfToken, true);
            fail();
        } catch (TryRefreshTokenException ignored) {
        }

        SessionInformationHolder refreshedSession = Session
                .refreshSession(process.getProcess(), sessionInfo.refreshToken.token, sessionInfo.antiCsrfToken);

        assert refreshedSession.accessToken != null;
        assert refreshedSession.refreshToken != null;
        assertNotEquals(refreshedSession.antiCsrfToken, sessionInfo.antiCsrfToken);
        assertNotEquals(refreshedSession.idRefreshToken, sessionInfo.idRefreshToken);
        assertNotEquals(refreshedSession.accessToken.token, sessionInfo.accessToken.token);
        assertNotEquals(refreshedSession.refreshToken.token, sessionInfo.refreshToken.token);
        assertEquals(refreshedSession.session.handle, sessionInfo.session.handle);
        assertEquals(refreshedSession.session.userId, sessionInfo.session.userId);
        assertEquals(refreshedSession.session.userDataInJWT.toString(), sessionInfo.session.userDataInJWT.toString());
        assertEquals(StorageLayer.getStorageLayer(process.getProcess()).getNumberOfSessions(), 1);

        SessionInformationHolder newSession = Session.getSession(process.getProcess(),
                refreshedSession.accessToken.token, refreshedSession.antiCsrfToken, true);

        assert newSession.accessToken != null;
        assertNotEquals(newSession.accessToken.token, refreshedSession.accessToken.token);
        assertNotEquals(newSession.accessToken.expiry, refreshedSession.accessToken.expiry);
        assertNotEquals(newSession.accessToken.createdTime, refreshedSession.accessToken.createdTime);
        assertEquals(newSession.session.userDataInJWT.toString(), refreshedSession.session.userDataInJWT.toString());

        SessionInformationHolder newSession2 = Session.getSession(process.getProcess(),
                newSession.accessToken.token, refreshedSession.antiCsrfToken, true);
        assert newSession2.accessToken == null;

        Thread.sleep(1500);

        try {
            Session.getSession(process.getProcess(),
                    newSession.accessToken.token, newSession.antiCsrfToken, true);
            fail();
        } catch (TryRefreshTokenException ignored) {
        }

        SessionInformationHolder refreshedSession2 = Session
                .refreshSession(process.getProcess(), refreshedSession.refreshToken.token,
                        refreshedSession.antiCsrfToken);
        assertEquals(StorageLayer.getStorageLayer(process.getProcess()).getNumberOfSessions(), 1);

        assert refreshedSession2.accessToken != null;
        assertNotEquals(refreshedSession2.accessToken.token, newSession.accessToken.token);
        assertNotEquals(refreshedSession2.antiCsrfToken, refreshedSession.antiCsrfToken);
        assertNotEquals(refreshedSession2.idRefreshToken, refreshedSession.idRefreshToken);

        SessionInformationHolder newSession3 = Session.getSession(process.getProcess(),
                refreshedSession2.accessToken.token, refreshedSession2.antiCsrfToken, true);

        assert newSession3.accessToken != null;
        assertNotEquals(newSession3.accessToken.token, refreshedSession2.accessToken.token);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    @Test
    public void refreshSessionTestWithNoAntiCsrf()
            throws IOException, InterruptedException, StorageQueryException, NoSuchAlgorithmException,
            InvalidKeyException,
            InvalidKeySpecException, StorageTransactionLogicException,
            TryRefreshTokenException, UnauthorisedException, TokenTheftDetectedException, SignatureException,
            IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException, NoSuchPaddingException {

        Utils.setValueInConfig("access_token_validity", "1");
        Utils.setValueInConfig("enable_anti_csrf", "false");

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
        assert sessionInfo.antiCsrfToken == null;

        Thread.sleep(1500);

        try {
            Session.getSession(process.getProcess(),
                    sessionInfo.accessToken.token, null, true);
            fail();
        } catch (TryRefreshTokenException ignored) {
        }

        SessionInformationHolder refreshedSession = Session
                .refreshSession(process.getProcess(), sessionInfo.refreshToken.token, sessionInfo.antiCsrfToken);

        assert refreshedSession.accessToken != null;
        assert refreshedSession.refreshToken != null;
        assert refreshedSession.antiCsrfToken == null;
        assertNotEquals(refreshedSession.idRefreshToken, sessionInfo.idRefreshToken);
        assertNotEquals(refreshedSession.accessToken.token, sessionInfo.accessToken.token);
        assertNotEquals(refreshedSession.refreshToken.token, sessionInfo.refreshToken.token);
        assertEquals(refreshedSession.session.handle, sessionInfo.session.handle);
        assertEquals(refreshedSession.session.userId, sessionInfo.session.userId);
        assertEquals(refreshedSession.session.userDataInJWT.toString(), sessionInfo.session.userDataInJWT.toString());
        assertEquals(StorageLayer.getStorageLayer(process.getProcess()).getNumberOfSessions(), 1);

        SessionInformationHolder newSession = Session.getSession(process.getProcess(),
                refreshedSession.accessToken.token, null, true);

        assert newSession.accessToken != null;
        assertNotEquals(newSession.accessToken.token, refreshedSession.accessToken.token);
        assertNotEquals(newSession.accessToken.expiry, refreshedSession.accessToken.expiry);
        assertNotEquals(newSession.accessToken.createdTime, refreshedSession.accessToken.createdTime);
        assertEquals(newSession.session.userDataInJWT.toString(), refreshedSession.session.userDataInJWT.toString());

        SessionInformationHolder newSession2 = Session.getSession(process.getProcess(),
                newSession.accessToken.token, null, true);
        assert newSession2.accessToken == null;

        Thread.sleep(1500);

        try {
            Session.getSession(process.getProcess(),
                    newSession.accessToken.token, null, false);
            fail();
        } catch (TryRefreshTokenException ignored) {
        }

        SessionInformationHolder refreshedSession2 = Session
                .refreshSession(process.getProcess(), refreshedSession.refreshToken.token,
                        refreshedSession.antiCsrfToken);
        assertEquals(StorageLayer.getStorageLayer(process.getProcess()).getNumberOfSessions(), 1);

        assert refreshedSession2.accessToken != null;
        assertNotEquals(refreshedSession2.accessToken.token, newSession.accessToken.token);
        assertNotEquals(refreshedSession2.idRefreshToken, refreshedSession.idRefreshToken);

        SessionInformationHolder newSession3 = Session.getSession(process.getProcess(),
                refreshedSession2.accessToken.token, null, true);

        assert newSession3.accessToken != null;
        assertNotEquals(newSession3.accessToken.token, refreshedSession2.accessToken.token);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    @Test
    public void createAndGetSessionBadAntiCsrfFailure() throws InterruptedException, StorageQueryException,
            NoSuchAlgorithmException, InvalidKeyException, InvalidAlgorithmParameterException, NoSuchPaddingException
            , BadPaddingException, IOException, InvalidKeySpecException, IllegalBlockSizeException,
            StorageTransactionLogicException, UnauthorisedException, SignatureException {

        Utils.setValueInConfig("enable_anti_csrf", "true");
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

        try {
            Session.getSession(process.getProcess(),
                    sessionInfo.accessToken.token, "should fail!", true);
            fail();
        } catch (TryRefreshTokenException e) {
            assertEquals(e.getMessage(), "anti-csrf check failed");
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void refreshTokenExpiresAfterShortTime()
            throws InterruptedException, IOException, StorageQueryException, NoSuchAlgorithmException,
            InvalidKeyException,
            InvalidKeySpecException, StorageTransactionLogicException,
            TryRefreshTokenException, UnauthorisedException, TokenTheftDetectedException, SignatureException,
            IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException, NoSuchPaddingException {

        Utils.setValueInConfig("refresh_token_validity", "" + 1.5 / 60.0);

        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        Main main = process.getProcess();

        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        {
            // Part 1
            SessionInformationHolder sessionInfo = Session.createNewSession(main, userId, userDataInJWT,
                    userDataInDatabase);
            assert sessionInfo.refreshToken != null;
            assert sessionInfo.accessToken != null;

            SessionInformationHolder newRefreshedSession = Session
                    .refreshSession(main, sessionInfo.refreshToken.token, sessionInfo.antiCsrfToken);
            assert newRefreshedSession.refreshToken != null;

            assertEquals(StorageLayer.getStorageLayer(process.getProcess()).getNumberOfSessions(), 1);

            Session.getSession(main, sessionInfo.accessToken.token, sessionInfo.antiCsrfToken, true);

            Thread.sleep(2000);

            try {
                Session.refreshSession(main, newRefreshedSession.refreshToken.token, newRefreshedSession.antiCsrfToken);
                fail();
            } catch (UnauthorisedException ignored) {

            }
            assertEquals(StorageLayer.getStorageLayer(process.getProcess()).getNumberOfSessions(), 1);
        }

        // Part 2
        {
            SessionInformationHolder sessionInfo = Session.createNewSession(main, userId, userDataInJWT,
                    userDataInDatabase);
            assert sessionInfo.refreshToken != null;
            assert sessionInfo.accessToken != null;
            assertEquals(StorageLayer.getStorageLayer(process.getProcess()).getNumberOfSessions(), 2);

            SessionInformationHolder newRefreshedSession = Session
                    .refreshSession(main, sessionInfo.refreshToken.token, sessionInfo.antiCsrfToken);
            assert newRefreshedSession.refreshToken != null;
            assert newRefreshedSession.accessToken != null;
            assertNotEquals(newRefreshedSession.accessToken.token, sessionInfo.accessToken.token);
            assertNotEquals(newRefreshedSession.refreshToken.token, sessionInfo.refreshToken.token);
            assertEquals(StorageLayer.getStorageLayer(process.getProcess()).getNumberOfSessions(), 2);

            Thread.sleep(500);

            SessionInformationHolder newRefreshedSession2 = Session
                    .refreshSession(main, newRefreshedSession.refreshToken.token, newRefreshedSession.antiCsrfToken);
            assert newRefreshedSession2.refreshToken != null;
            assert newRefreshedSession2.accessToken != null;
            assertNotEquals(newRefreshedSession.accessToken.token, newRefreshedSession2.accessToken.token);
            assertNotEquals(newRefreshedSession.refreshToken.token, newRefreshedSession2.refreshToken.token);
            assertEquals(StorageLayer.getStorageLayer(process.getProcess()).getNumberOfSessions(), 2);

            Thread.sleep(500);

            SessionInformationHolder newRefreshedSession3 = Session
                    .refreshSession(main, newRefreshedSession2.refreshToken.token, newRefreshedSession2.antiCsrfToken);
            assert newRefreshedSession3.refreshToken != null;
            assert newRefreshedSession3.accessToken != null;
            assertNotEquals(newRefreshedSession3.accessToken.token, newRefreshedSession2.accessToken.token);
            assertNotEquals(newRefreshedSession3.refreshToken.token, newRefreshedSession2.refreshToken.token);
            assertEquals(StorageLayer.getStorageLayer(process.getProcess()).getNumberOfSessions(), 2);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

}
