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
import io.supertokens.ProcessState.EventAndException;
import io.supertokens.ProcessState.PROCESS_STATE;
import io.supertokens.exceptions.TryRefreshTokenException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.session.Session;
import io.supertokens.session.accessToken.AccessToken;
import io.supertokens.session.accessToken.AccessToken.AccessTokenInfo;
import io.supertokens.session.accessToken.AccessTokenSigningKey;
import io.supertokens.session.info.SessionInformationHolder;
import io.supertokens.session.info.TokenInfo;
import io.supertokens.test.TestingProcessManager.TestingProcess;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;

import static org.junit.Assert.*;

public class AccessTokenTest {
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

    // *  - create session with some data -> expire -> get access token without verifying, check payload is fine.
    @Test
    public void testCreateSessionWithDataExpireGetAccessTokenAndCheckPayload() throws Exception {
        String[] args = {"../"};

        Utils.setValueInConfig("access_token_validity", "1"); // 1 second validity

        TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        // - create session with some data
        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("jwtKey",
                "jwtValue");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        SessionInformationHolder sessionInfo = Session
                .createNewSession(process.getProcess(), userId, userDataInJWT, userDataInDatabase);

        //expire
        Thread.sleep(2000);

        //get access token without verifying
        assert sessionInfo.accessToken != null;
        AccessToken.AccessTokenInfo accessTokenInfo = AccessToken
                .getInfoFromAccessTokenWithoutVerifying(sessionInfo.accessToken.token);

        //check payload is fine
        assertEquals(accessTokenInfo.userData, userDataInJWT);
        assertEquals(accessTokenInfo.userId, userId);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));

    }


    // *  - create session with some old expiry time for access token -> check the created token's expiry time is
    // what you gave
    @Test
    public void testSessionWithOldExpiryTimeForAccessToken() throws Exception {
        String[] args = {"../"};

        TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        // - create session with some data
        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        SessionInformationHolder sessionInfo = Session
                .createNewSession(process.getProcess(), userId, userDataInJWT, userDataInDatabase);

        assert sessionInfo.accessToken != null;
        AccessTokenInfo accessTokenInfo = AccessToken
                .getInfoFromAccessToken(process.getProcess(), sessionInfo.accessToken.token, false);

        long value = System.currentTimeMillis() - 5000;

        assert accessTokenInfo.lmrt != null;
        TokenInfo newAccessTokenInfo = AccessToken
                .createNewAccessToken(process.getProcess(), sessionInfo.session.handle, userId,
                        accessTokenInfo.refreshTokenHash1, accessTokenInfo.parentRefreshTokenHash1, userDataInDatabase,
                        accessTokenInfo.antiCsrfToken, accessTokenInfo.lmrt, value);

        AccessTokenInfo customAccessToken = AccessToken
                .getInfoFromAccessTokenWithoutVerifying(newAccessTokenInfo.token);
        assertEquals(customAccessToken.expiryTime, value);


    }

    // *  - create access token version 1 -> get version -> should be 1
    @Test
    public void testCreateAccessTokenVersion1AndCheck() throws Exception {
        String[] args = {"../"};

        TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        // - create session with some data
        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        SessionInformationHolder sessionInfo = Session
                .createNewSessionV1(process.getProcess(), userId, userDataInJWT, userDataInDatabase);

        assert sessionInfo.accessToken != null;
        AccessToken.AccessTokenInfo accessTokenInfo = AccessToken
                .getInfoFromAccessToken(process.getProcess(), sessionInfo.accessToken.token, false);

        assertEquals(AccessToken.getAccessTokenVersion(accessTokenInfo), AccessToken.VERSION.V1);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    // *  - create access token version 2 -> get version -> should be 2
    @Test
    public void testCreateAccessTokenVersion2AndCheck() throws Exception {
        String[] args = {"../"};

        TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        // - create session with some data
        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        SessionInformationHolder sessionInfo = Session
                .createNewSession(process.getProcess(), userId, userDataInJWT, userDataInDatabase);

        assert sessionInfo.accessToken != null;
        AccessToken.AccessTokenInfo accessTokenInfo = AccessToken
                .getInfoFromAccessToken(process.getProcess(), sessionInfo.accessToken.token, false);

        assertEquals(AccessToken.getAccessTokenVersion(accessTokenInfo), AccessToken.VERSION.V2);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));


    }


    // good case test
    @Test
    public void inputOutputTest() throws InterruptedException, InvalidKeyException,
            NoSuchAlgorithmException, StorageQueryException, StorageTransactionLogicException, TryRefreshTokenException,
            UnsupportedEncodingException, InvalidKeySpecException, SignatureException {
        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.start(args);
        EventAndException e = process.checkOrWaitForEvent(PROCESS_STATE.STARTED);
        assertNotNull(e);
        JsonObject jsonObj = new JsonObject();
        jsonObj.addProperty("key", "value");

        // db key
        long lmrt = System.currentTimeMillis();
        long expiryTime = System.currentTimeMillis() + 1000;
        TokenInfo newToken = AccessToken.createNewAccessToken(process.getProcess(), "sessionHandle", "userId",
                "refreshTokenHash1", "parentRefreshTokenHash1", jsonObj, "antiCsrfToken", lmrt,
                expiryTime);
        AccessTokenInfo info = AccessToken.getInfoFromAccessToken(process.getProcess(), newToken.token, true);
        assertEquals("sessionHandle", info.sessionHandle);
        assertEquals("userId", info.userId);
        assertEquals("refreshTokenHash1", info.refreshTokenHash1);
        assertEquals("parentRefreshTokenHash1", info.parentRefreshTokenHash1);
        assertEquals("value", info.userData.get("key").getAsString());
        assertEquals("antiCsrfToken", info.antiCsrfToken);
        assert info.lmrt != null;
        assertEquals(lmrt, info.lmrt.longValue());
        assertEquals(expiryTime, info.expiryTime);
        process.kill();
    }

    @Test
    public void inputOutputTestv1() throws InterruptedException, InvalidKeyException,
            NoSuchAlgorithmException, StorageQueryException, StorageTransactionLogicException, TryRefreshTokenException,
            UnsupportedEncodingException, InvalidKeySpecException, SignatureException {
        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.start(args);
        EventAndException e = process.checkOrWaitForEvent(PROCESS_STATE.STARTED);
        assertNotNull(e);
        JsonObject jsonObj = new JsonObject();
        jsonObj.addProperty("key", "value");

        // db key
        TokenInfo newToken = AccessToken.createNewAccessTokenV1(process.getProcess(), "sessionHandle", "userId",
                "refreshTokenHash1", "parentRefreshTokenHash1", jsonObj, "antiCsrfToken");
        AccessTokenInfo info = AccessToken.getInfoFromAccessToken(process.getProcess(), newToken.token, true);
        assertEquals("sessionHandle", info.sessionHandle);
        assertEquals("userId", info.userId);
        assertEquals("refreshTokenHash1", info.refreshTokenHash1);
        assertEquals("parentRefreshTokenHash1", info.parentRefreshTokenHash1);
        assertEquals("value", info.userData.get("key").getAsString());
        assertEquals("antiCsrfToken", info.antiCsrfToken);
        assertNull(info.lmrt);
        process.kill();
    }

    // short interval for signing key
    @Test
    public void signingKeyShortInterval() throws InterruptedException,
            StorageQueryException, StorageTransactionLogicException, IOException {
        Utils.setValueInConfig("access_token_signing_key_update_interval", "0.00027"); // 1 second

        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.start(args);
        EventAndException e = process.checkOrWaitForEvent(PROCESS_STATE.STARTED);
        assertNotNull(e);
        String keyBefore = AccessTokenSigningKey.getInstance(process.getProcess()).getKey().toString();
        Thread.sleep(1500);
        String keyAfter = AccessTokenSigningKey.getInstance(process.getProcess()).getKey().toString();
        assertNotEquals(keyBefore, keyAfter);
        assertTrue(AccessTokenSigningKey.getInstance(process.getProcess()).getKeyExpiryTime() != Long.MAX_VALUE);
        process.kill();
    }

    @Test
    public void signingKeyChangeThrowsRefreshTokenError()
            throws IOException, InterruptedException,
            InvalidKeyException, NoSuchAlgorithmException, StorageQueryException, StorageTransactionLogicException,
            InvalidKeySpecException, SignatureException {
        Utils.setValueInConfig("access_token_signing_key_update_interval", "0.00027"); // 1 second

        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.start(args);
        EventAndException e = process.checkOrWaitForEvent(PROCESS_STATE.STARTED);
        assertNotNull(e);
        JsonObject jsonObj = new JsonObject();
        jsonObj.addProperty("key", "value");

        TokenInfo tokenInfo = AccessToken.createNewAccessToken(process.getProcess(), "sessionHandle", "userId",
                "refreshTokenHash1", "parentRefreshTokenHash1", jsonObj, "antiCsrfToken", System.currentTimeMillis(),
                null);
        Thread.sleep(1500);

        try {
            AccessToken.getInfoFromAccessToken(process.getProcess(), tokenInfo.token, true);
        } catch (TryRefreshTokenException ex) {
            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.RETRYING_ACCESS_TOKEN_JWT_VERIFICATION));
            process.kill();
            return;
        }
        process.kill();
        fail();
    }

    @Test
    public void accessTokenShortLifetimeThrowsRefreshTokenError()
            throws IOException, InterruptedException,
            InvalidKeyException, NoSuchAlgorithmException, StorageQueryException, StorageTransactionLogicException,
            InvalidKeySpecException, SignatureException {
        Utils.setValueInConfig("access_token_validity", "1"); // 1 second

        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.start(args);
        EventAndException e = process.checkOrWaitForEvent(PROCESS_STATE.STARTED);
        assertNotNull(e);

        JsonObject jsonObj = new JsonObject();
        jsonObj.addProperty("key", "value");

        TokenInfo tokenInfo = AccessToken.createNewAccessToken(process.getProcess(), "sessionHandle", "userId",
                "refreshTokenHash1", "parentRefreshTokenHash1", jsonObj, "antiCsrfToken", System.currentTimeMillis(),
                null);
        Thread.sleep(1500);

        try {
            AccessToken.getInfoFromAccessToken(process.getProcess(), tokenInfo.token, true);
        } catch (TryRefreshTokenException ex) {
            assertEquals("Access token expired", ex.getMessage());
            process.kill();
            return;
        }
        process.kill();
        fail();
    }

    @Test
    public void verifyRandomAccessTokenFailure() throws InterruptedException,
            StorageQueryException, StorageTransactionLogicException {
        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        try {
            AccessToken.getInfoFromAccessToken(process.getProcess(), "token", true);
        } catch (TryRefreshTokenException e) {
            return;
        }
        fail();
    }

}
