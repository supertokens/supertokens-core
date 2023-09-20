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

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.supertokens.ProcessState.EventAndException;
import io.supertokens.ProcessState.PROCESS_STATE;
import io.supertokens.exceptions.AccessTokenPayloadError;
import io.supertokens.exceptions.TryRefreshTokenException;
import io.supertokens.jwt.JWTSigningFunctions;
import io.supertokens.jwt.exceptions.UnsupportedJWTSigningAlgorithmException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.session.Session;
import io.supertokens.session.accessToken.AccessToken;
import io.supertokens.session.accessToken.AccessToken.AccessTokenInfo;
import io.supertokens.session.info.SessionInformationHolder;
import io.supertokens.session.info.TokenInfo;
import io.supertokens.session.jwt.JWT;
import io.supertokens.signingkeys.SigningKeys;
import io.supertokens.test.Retry;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.TestingProcessManager.TestingProcess;
import io.supertokens.test.Utils;
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
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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

    @Rule
    public Retry retry = new Retry(3);

    // * - create session with some data -> expire -> get access token without verifying, check payload is fine.
    @Test
    public void testCreateSessionWithDataExpireGetAccessTokenAndCheckPayload() throws Exception {
        String[] args = {"../"};

        Utils.setValueInConfig("access_token_validity", "1"); // 1 second validity

        TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        // - create session with some data
        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("jwtKey", "jwtValue");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        SessionInformationHolder sessionInfo = Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                userDataInDatabase);

        // expire
        Thread.sleep(2000);

        // get access token without verifying
        assert sessionInfo.accessToken != null;
        AccessToken.AccessTokenInfo accessTokenInfo = AccessToken
                .getInfoFromAccessTokenWithoutVerifying(sessionInfo.accessToken.token);

        // check payload is fine
        assertEquals(accessTokenInfo.userData, userDataInJWT);
        assertEquals(accessTokenInfo.recipeUserId, userId);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));

    }

    // * - create session with some data -> expire -> get access token without verifying, check payload is fine.
    @Test
    public void testCreateSessionV2WithDataExpireGetAccessTokenAndCheckPayload() throws Exception {
        String[] args = {"../"};

        Utils.setValueInConfig("access_token_validity", "1"); // 1 second validity

        TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        // - create session with some data
        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("jwtKey", "jwtValue");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        SessionInformationHolder sessionInfo = Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                userDataInDatabase, false, AccessToken.getLatestVersion(), false);

        // expire
        Thread.sleep(2000);

        // get access token without verifying
        assert sessionInfo.accessToken != null;
        AccessToken.AccessTokenInfo accessTokenInfo = AccessToken
                .getInfoFromAccessTokenWithoutVerifying(sessionInfo.accessToken.token);

        // check payload is fine
        assertEquals(accessTokenInfo.userData, userDataInJWT);
        assertEquals(accessTokenInfo.recipeUserId, userId);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));

    }

    // * - create session with some old expiry time for access token -> check the created token's expiry time is
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

        SessionInformationHolder sessionInfo = Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                userDataInDatabase, false, AccessToken.getLatestVersion(), false);

        assert sessionInfo.accessToken != null;
        AccessTokenInfo accessTokenInfo = AccessToken.getInfoFromAccessToken(process.getProcess(),
                sessionInfo.accessToken.token, false);

        long value = System.currentTimeMillis() - 5000;

        TokenInfo newAccessTokenInfo = AccessToken.createNewAccessToken(process.getProcess(),
                sessionInfo.session.handle, userId, accessTokenInfo.refreshTokenHash1,
                accessTokenInfo.parentRefreshTokenHash1, userDataInDatabase, accessTokenInfo.antiCsrfToken,
                value, AccessToken.getLatestVersion(), false);

        AccessTokenInfo customAccessToken = AccessToken
                .getInfoFromAccessTokenWithoutVerifying(newAccessTokenInfo.token);
        assertEquals(customAccessToken.expiryTime, value / 1000 * 1000);
    }

    // * - create session with some old expiry time for access token -> check the created token's expiry time is
    // what you gave
    @Test
    public void testSessionWithOldExpiryTimeForAccessTokenV2() throws Exception {
        String[] args = {"../"};

        TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        // - create session with some data
        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        SessionInformationHolder sessionInfo = Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                userDataInDatabase, false, AccessToken.VERSION.V2, false);

        assert sessionInfo.accessToken != null;
        AccessTokenInfo accessTokenInfo = AccessToken.getInfoFromAccessToken(process.getProcess(),
                sessionInfo.accessToken.token, false);

        long value = System.currentTimeMillis() - 5000;

        TokenInfo newAccessTokenInfo = AccessToken.createNewAccessToken(process.getProcess(),
                sessionInfo.session.handle, userId, accessTokenInfo.refreshTokenHash1,
                accessTokenInfo.parentRefreshTokenHash1, userDataInDatabase, accessTokenInfo.antiCsrfToken,
                value, AccessToken.VERSION.V2, false);

        AccessTokenInfo customAccessToken = AccessToken
                .getInfoFromAccessTokenWithoutVerifying(newAccessTokenInfo.token);
        assertEquals(customAccessToken.expiryTime, value);
    }

    // * - create access token version 2 -> get version -> should be 2
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

        SessionInformationHolder sessionInfo = Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                userDataInDatabase, false, AccessToken.VERSION.V2, false);

        assert sessionInfo.accessToken != null;
        AccessToken.AccessTokenInfo accessTokenInfo = AccessToken.getInfoFromAccessToken(process.getProcess(),
                sessionInfo.accessToken.token, false);

        assertEquals(accessTokenInfo.version, AccessToken.VERSION.V2);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));

    }

    // good case test
    @Test
    public void inputOutputTest() throws Exception {
        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.start(args);
        EventAndException e = process.checkOrWaitForEvent(PROCESS_STATE.STARTED);
        assertNotNull(e);
        JsonObject jsonObj = new JsonObject();
        String testValue = "asdf???123";
        jsonObj.addProperty("key", testValue);

        // db key
        long expiryTime = System.currentTimeMillis() + 1000;
        TokenInfo newToken = AccessToken.createNewAccessToken(process.getProcess(), "sessionHandle", "userId",
                "refreshTokenHash1", "parentRefreshTokenHash1", jsonObj, "antiCsrfToken", expiryTime,
                AccessToken.getLatestVersion(), false);
        AccessTokenInfo info = AccessToken.getInfoFromAccessToken(process.getProcess(), newToken.token, true);
        assertEquals("sessionHandle", info.sessionHandle);
        assertEquals("userId", info.recipeUserId);
        assertEquals("refreshTokenHash1", info.refreshTokenHash1);
        assertEquals("parentRefreshTokenHash1", info.parentRefreshTokenHash1);
        assertEquals(testValue, info.userData.get("key").getAsString());
        assertEquals("antiCsrfToken", info.antiCsrfToken);
        assertEquals(expiryTime / 1000 * 1000, info.expiryTime);

        JsonObject payload = (JsonObject) new JsonParser()
                .parse(io.supertokens.utils.Utils.convertFromBase64(newToken.token.split("\\.")[1]));
        // This throws if the number is in scientific (E) format
        assertEquals(expiryTime / 1000, Long.parseLong(payload.get("exp").toString()));

        JWT.JWTPreParseInfo jwtInfo = JWT.preParseJWTInfo(newToken.token);
        assertNotNull(jwtInfo.kid);
        assertEquals(jwtInfo.version, AccessToken.getLatestVersion());

        process.kill();
    }

    @Test
    public void inputOutputTestStatic() throws Exception {
        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.start(args);
        EventAndException e = process.checkOrWaitForEvent(PROCESS_STATE.STARTED);
        assertNotNull(e);
        JsonObject jsonObj = new JsonObject();
        String testValue = "asdf???123";
        jsonObj.addProperty("key", testValue);

        // db key
        long expiryTime = System.currentTimeMillis() + 1000;
        TokenInfo newToken = AccessToken.createNewAccessToken(process.getProcess(), "sessionHandle", "userId",
                "refreshTokenHash1", "parentRefreshTokenHash1", jsonObj, "antiCsrfToken", expiryTime,
                AccessToken.getLatestVersion(), true);
        System.out.println(newToken.token);
        AccessTokenInfo info = AccessToken.getInfoFromAccessToken(process.getProcess(), newToken.token, true);
        assertEquals("sessionHandle", info.sessionHandle);
        assertEquals("userId", info.recipeUserId);
        assertEquals("refreshTokenHash1", info.refreshTokenHash1);
        assertEquals("parentRefreshTokenHash1", info.parentRefreshTokenHash1);
        assertEquals(testValue, info.userData.get("key").getAsString());
        assertEquals("antiCsrfToken", info.antiCsrfToken);
        assertEquals(expiryTime / 1000 * 1000, info.expiryTime);

        JsonObject payload = (JsonObject) new JsonParser()
                .parse(io.supertokens.utils.Utils.convertFromBase64(newToken.token.split("\\.")[1]));
        // This throws if the number is in scientific (E) format
        assertEquals(expiryTime / 1000, Long.parseLong(payload.get("exp").toString()));

        JWT.JWTPreParseInfo jwtInfo = JWT.preParseJWTInfo(newToken.token);
        assertNotNull(jwtInfo.kid);
        assertEquals(jwtInfo.version, AccessToken.getLatestVersion());
        process.kill();
    }

    @Test
    public void inputOutputTestV2() throws Exception {
        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.start(args);
        EventAndException e = process.checkOrWaitForEvent(PROCESS_STATE.STARTED);
        assertNotNull(e);
        JsonObject jsonObj = new JsonObject();
        String testValue = "asdf???123";
        jsonObj.addProperty("key", testValue);

        // db key
        long expiryTime = System.currentTimeMillis() + 1000;
        TokenInfo newToken = AccessToken.createNewAccessToken(process.getProcess(), "sessionHandle", "userId",
                "refreshTokenHash1", "parentRefreshTokenHash1", jsonObj, "antiCsrfToken", expiryTime,
                AccessToken.VERSION.V2, false);
        AccessTokenInfo info = AccessToken.getInfoFromAccessToken(process.getProcess(), newToken.token, true);
        assertEquals("sessionHandle", info.sessionHandle);
        assertEquals("userId", info.recipeUserId);
        assertEquals("refreshTokenHash1", info.refreshTokenHash1);
        assertEquals("parentRefreshTokenHash1", info.parentRefreshTokenHash1);
        assertEquals(testValue, info.userData.get("key").getAsString());
        assertEquals("antiCsrfToken", info.antiCsrfToken);
        assertEquals(expiryTime, info.expiryTime);

        JsonObject payload = (JsonObject) new JsonParser()
                .parse(io.supertokens.utils.Utils.convertFromBase64(newToken.token.split("\\.")[1]));
        // This throws if the number is in scientific (E) format
        assertEquals(expiryTime, Long.parseLong(payload.get("expiryTime").toString()));

        process.kill();
    }

    @Test
    public void inputOutputTestv1() throws InterruptedException, InvalidKeyException, NoSuchAlgorithmException,
            StorageQueryException, StorageTransactionLogicException, TryRefreshTokenException,
            UnsupportedEncodingException, InvalidKeySpecException, SignatureException,
            UnsupportedJWTSigningAlgorithmException, AccessTokenPayloadError {
        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.start(args);
        EventAndException e = process.checkOrWaitForEvent(PROCESS_STATE.STARTED);
        assertNotNull(e);
        JsonObject jsonObj = new JsonObject();
        String testValue = "asdf???123";
        jsonObj.addProperty("key", testValue);

        // db key
        TokenInfo newToken = AccessToken.createNewAccessTokenV1(process.getProcess(), "sessionHandle", "userId",
                "refreshTokenHash1", "parentRefreshTokenHash1", jsonObj, "antiCsrfToken");
        AccessTokenInfo info = AccessToken.getInfoFromAccessToken(process.getProcess(), newToken.token, true);
        assertEquals("sessionHandle", info.sessionHandle);
        assertEquals("userId", info.recipeUserId);
        assertEquals("refreshTokenHash1", info.refreshTokenHash1);
        assertEquals("parentRefreshTokenHash1", info.parentRefreshTokenHash1);
        assertEquals(testValue, info.userData.get("key").getAsString());
        assertEquals("antiCsrfToken", info.antiCsrfToken);

        JsonObject payload = (JsonObject) new JsonParser()
                .parse(io.supertokens.utils.Utils.convertFromBase64(newToken.token.split("\\.")[1]));
        // This throws if the number is in scientific (E) format
        Long.parseLong(payload.get("expiryTime").toString());

        process.kill();
    }

    // short interval for signing key
    @Test
    public void signingKeyShortInterval()
            throws InterruptedException, StorageQueryException, TenantOrAppNotFoundException,
            StorageTransactionLogicException, IOException, UnsupportedJWTSigningAlgorithmException {
        Utils.setValueInConfig("access_token_dynamic_signing_key_update_interval", "0.00027"); // 1 second

        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.start(args);
        EventAndException e = process.checkOrWaitForEvent(PROCESS_STATE.STARTED);
        assertNotNull(e);
        String keyBefore = SigningKeys.getInstance(process.getProcess()).getLatestIssuedDynamicKey().toString();
        Thread.sleep(1500);
        String keyAfter = SigningKeys.getInstance(process.getProcess()).getLatestIssuedDynamicKey().toString();
        assertNotEquals(keyBefore, keyAfter);
        assertTrue(SigningKeys.getInstance(process.getProcess()).getDynamicSigningKeyExpiryTime() != Long.MAX_VALUE);
        process.kill();
    }

    @Test
    public void signingKeyChangeDoesNotThrow() throws Exception {
        Utils.setValueInConfig("access_token_dynamic_signing_key_update_interval", "0.00027"); // 1 second

        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.start(args);
        EventAndException e = process.checkOrWaitForEvent(PROCESS_STATE.STARTED);
        assertNotNull(e);
        JsonObject jsonObj = new JsonObject();
        jsonObj.addProperty("key", "value");

        TokenInfo tokenInfo = AccessToken.createNewAccessToken(process.getProcess(), "sessionHandle", "userId",
                "refreshTokenHash1", "parentRefreshTokenHash1", jsonObj, "antiCsrfToken",
                null, AccessToken.getLatestVersion(), false);
        Thread.sleep(1500);

        try {
            AccessToken.getInfoFromAccessToken(process.getProcess(), tokenInfo.token, true);
        } catch (TryRefreshTokenException ex) {
            fail();
            process.kill();
        }
        process.kill();
    }

    @Test
    public void accessTokenShortLifetimeThrowsRefreshTokenError()
            throws Exception {
        Utils.setValueInConfig("access_token_validity", "1"); // 1 second

        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.start(args);
        EventAndException e = process.checkOrWaitForEvent(PROCESS_STATE.STARTED);
        assertNotNull(e);

        JsonObject jsonObj = new JsonObject();
        jsonObj.addProperty("key", "value");

        TokenInfo tokenInfo = AccessToken.createNewAccessToken(process.getProcess(), "sessionHandle", "userId",
                "refreshTokenHash1", "parentRefreshTokenHash1", jsonObj, "antiCsrfToken",
                null, AccessToken.getLatestVersion(), false);
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
    public void verifyRandomAccessTokenFailure()
            throws InterruptedException, StorageQueryException, StorageTransactionLogicException,
            UnsupportedJWTSigningAlgorithmException {
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

    // See https://github.com/supertokens/supertokens-core/issues/282
    @Test
    public void keyChangeThreadSafetyTest() throws Exception {
        Utils.setValueInConfig("access_token_dynamic_signing_key_update_interval", "0.00027"); // 1 second
        Utils.setValueInConfig("access_token_validity", "1"); // 1 second

        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.start(args);
        EventAndException e = process.checkOrWaitForEvent(PROCESS_STATE.STARTED);
        assertNotNull(e);
        JsonObject jsonObj = new JsonObject();
        jsonObj.addProperty("key", "value");

        TokenInfo tokenInfo = AccessToken.createNewAccessToken(process.getProcess(), "sessionHandle", "userId",
                "refreshTokenHash1", "parentRefreshTokenHash1", jsonObj, "antiCsrfToken",
                null, AccessToken.getLatestVersion(), false);

        Thread.sleep(3500);

        ExecutorService es = Executors.newFixedThreadPool(1000);

        AtomicBoolean hasNullPointerException = new AtomicBoolean(false);

        for (int i = 0; i < 4000; i++) {
            es.execute(() -> {
                try {
                    AccessToken.getInfoFromAccessToken(process.getProcess(), tokenInfo.token, true);
                } catch (NullPointerException ex) {
                    hasNullPointerException.set(true);
                } catch (Exception ignored) {
                }
            });
        }

        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.UPDATING_ACCESS_TOKEN_SIGNING_KEYS));

        es.shutdown();
        es.awaitTermination(2, TimeUnit.MINUTES);

        assert (!hasNullPointerException.get());

        process.kill();
    }

    @Test
    public void testThatWeUseLatestVersionIfHeaderHasNoVersion() throws Exception {
        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.start(args);
        EventAndException e = process.checkOrWaitForEvent(PROCESS_STATE.STARTED);
        assertNotNull(e);

        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        JsonObject userDataInDatabase = new JsonObject();
        SessionInformationHolder sessionInfo = Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                userDataInDatabase);

        String accessToken = sessionInfo.accessToken.token;
        String payloadString = accessToken.split("\\.")[1];
        String decodedPayload = new String(Base64.getUrlDecoder().decode(payloadString));
        JsonObject accessTokenPayload = new Gson().fromJson(decodedPayload, JsonObject.class);
        accessTokenPayload.remove("iat");
        accessTokenPayload.remove("exp");

        String algorithm = "RS256";
        String jwksDomain = "http://localhost";
        long validity = 3600;

        String jwt = JWTSigningFunctions.createJWTToken(process.main, algorithm, accessTokenPayload, jwksDomain,
                validity, false);

        String header = jwt.split("\\.")[0];
        String decodedHeader = new String(Base64.getUrlDecoder().decode(header));
        JsonObject headerObject = new Gson().fromJson(decodedHeader, JsonObject.class);
        // Verify that by default the version is not present
        assertNull(headerObject.get("version"));

        JWT.JWTPreParseInfo info = JWT.preParseJWTInfo(jwt);
        assert info.version == AccessToken.getLatestVersion();
    }
}
