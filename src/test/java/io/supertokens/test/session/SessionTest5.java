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

import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.config.Config;
import io.supertokens.session.Session;
import io.supertokens.session.accessToken.AccessToken;
import io.supertokens.session.info.SessionInformationHolder;
import io.supertokens.session.jwt.JWT;
import io.supertokens.signingkeys.AccessTokenSigningKey;
import io.supertokens.signingkeys.SigningKeys;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import org.junit.*;
import org.junit.rules.TestRule;

import java.time.Instant;
import java.util.Base64;

import static junit.framework.TestCase.*;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class SessionTest5 {

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
    public void checkCreateV2TokenEncoding() throws Exception {

        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        SessionInformationHolder sessionInfo = Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                userDataInDatabase, false, AccessToken.VERSION.V2, false);

        String[] tokenParts = sessionInfo.accessToken.token.split("\\.");
        assertEquals(tokenParts.length, 3);

        // The decoding process throws if if it's the wrong type
        Base64.getDecoder().decode(tokenParts[0]);
        Base64.getDecoder().decode(tokenParts[1]);
        Base64.getDecoder().decode(tokenParts[2]);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    @Test
    public void checkCreateStaticV2TokenEncoding() throws Exception {

        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        SessionInformationHolder sessionInfo = Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                userDataInDatabase, false, AccessToken.VERSION.V2, true);

        String[] tokenParts = sessionInfo.accessToken.token.split("\\.");
        assertEquals(tokenParts.length, 3);

        // The decoding process throws if if it's the wrong type
        Base64.getDecoder().decode(tokenParts[0]);
        Base64.getDecoder().decode(tokenParts[1]);
        Base64.getDecoder().decode(tokenParts[2]);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    @Test
    public void checkRefreshV2TokenEncoding() throws Exception {

        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        SessionInformationHolder createInfo = Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                userDataInDatabase, false, AccessToken.VERSION.V2, false);
        SessionInformationHolder sessionInfo = Session.refreshSession(process.getProcess(),
                createInfo.refreshToken.token, null, false, AccessToken.VERSION.V2);

        String[] tokenParts = sessionInfo.accessToken.token.split("\\.");
        assertEquals(tokenParts.length, 3);

        // The decoding process throws if if it's the wrong type
        Base64.getDecoder().decode(tokenParts[0]);
        Base64.getDecoder().decode(tokenParts[1]);
        Base64.getDecoder().decode(tokenParts[2]);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    @Test
    public void checkCreateV3TokenEncoding() throws Exception {

        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        SessionInformationHolder sessionInfo = Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                userDataInDatabase, false, AccessToken.VERSION.V3, false);

        String[] tokenParts = sessionInfo.accessToken.token.split("\\.");
        assertEquals(tokenParts.length, 3);

        // The decoding process throws if if it's the wrong type
        Base64.getUrlDecoder().decode(tokenParts[0]);
        Base64.getUrlDecoder().decode(tokenParts[1]);
        Base64.getUrlDecoder().decode(tokenParts[2]);

        DecodedJWT decodedJWT = com.auth0.jwt.JWT.decode(sessionInfo.accessToken.token);

        Claim headerAlg = decodedJWT.getHeaderClaim("alg");
        Claim headerType = decodedJWT.getHeaderClaim("typ");
        Claim headerKeyId = decodedJWT.getHeaderClaim("kid");
        assertTrue(!headerAlg.isNull() && !headerType.isNull() && !headerKeyId.isNull());
        assert headerAlg.asString().equalsIgnoreCase("rs256");
        assert headerType.asString().equalsIgnoreCase("jwt");
        assert !headerKeyId.asString().isEmpty();
        assert headerKeyId.asString().startsWith("d-");

        assertEquals(userId, decodedJWT.getSubject());

        Instant issued = decodedJWT.getIssuedAtAsInstant();
        assert issued.isAfter(Instant.now().minusMillis(1500));
        assert issued.isBefore(Instant.now().plusMillis(1500));

        Instant expires = decodedJWT.getExpiresAtAsInstant();
        long validityInMS = Config.getConfig(process.getProcess()).getAccessTokenValidityInMillis();
        assert expires.isAfter(Instant.now().plusMillis(validityInMS).minusMillis(1500));
        assert expires.isBefore(Instant.now().plusMillis(validityInMS).plusMillis(1500));

        assertNull(decodedJWT.getIssuer());
        assertNull(decodedJWT.getAudience());
        assertNull(decodedJWT.getNotBefore());

        // [antiCsrfToken, sub, sessionHandle, exp, iat, parentRefreshTokenHash1, key, refreshTokenHash1]
        assertEquals(8, decodedJWT.getClaims().size());
        assertEquals("value", decodedJWT.getClaim("key").asString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    @Test
    public void checkCreateStaticV3TokenEncoding() throws Exception {

        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        SessionInformationHolder sessionInfo = Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                userDataInDatabase, false, AccessToken.VERSION.V3, true);

        String[] tokenParts = sessionInfo.accessToken.token.split("\\.");
        assertEquals(tokenParts.length, 3);

        // The decoding process throws if if it's the wrong type
        Base64.getUrlDecoder().decode(tokenParts[0]);
        Base64.getUrlDecoder().decode(tokenParts[1]);
        Base64.getUrlDecoder().decode(tokenParts[2]);

        DecodedJWT decodedJWT = com.auth0.jwt.JWT.decode(sessionInfo.accessToken.token);

        Claim headerAlg = decodedJWT.getHeaderClaim("alg");
        Claim headerType = decodedJWT.getHeaderClaim("typ");
        Claim headerKeyId = decodedJWT.getHeaderClaim("kid");
        assertTrue(!headerAlg.isNull() && !headerType.isNull() && !headerKeyId.isNull());
        assert headerAlg.asString().equalsIgnoreCase("rs256");
        assert headerType.asString().equalsIgnoreCase("jwt");
        assert !headerKeyId.asString().isEmpty();
        assert headerKeyId.asString().startsWith("s-");

        assertEquals(userId, decodedJWT.getSubject());

        Instant issued = decodedJWT.getIssuedAtAsInstant();
        assert issued.isAfter(Instant.now().minusMillis(1500));
        assert issued.isBefore(Instant.now().plusMillis(1500));

        Instant expires = decodedJWT.getExpiresAtAsInstant();
        long validityInMS = Config.getConfig(process.getProcess()).getAccessTokenValidityInMillis();

        assert expires.isAfter(Instant.now().plusMillis(validityInMS).minusMillis(1500));
        assert expires.isBefore(Instant.now().plusMillis(validityInMS).plusMillis(1500));

        assertNull(decodedJWT.getIssuer());
        assertNull(decodedJWT.getAudience());
        assertNull(decodedJWT.getNotBefore());

        // [antiCsrfToken, sub, sessionHandle, exp, iat, parentRefreshTokenHash1, key, refreshTokenHash1]
        assertEquals(8, decodedJWT.getClaims().size());
        assertEquals("value", decodedJWT.getClaim("key").asString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    @Test
    public void checkRefreshV3TokenEncoding() throws Exception {

        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        SessionInformationHolder createInfo = Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                userDataInDatabase, false, AccessToken.getLatestVersion(), false);
        SessionInformationHolder sessionInfo = Session.refreshSession(process.getProcess(),
                createInfo.refreshToken.token, null, false, AccessToken.getLatestVersion());

        String[] tokenParts = sessionInfo.accessToken.token.split("\\.");
        assertEquals(tokenParts.length, 3);

        // The decoding process throws if if it's the wrong type
        Base64.getUrlDecoder().decode(tokenParts[0]);
        Base64.getUrlDecoder().decode(tokenParts[1]);
        Base64.getUrlDecoder().decode(tokenParts[2]);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    @Test
    public void checkRefreshKidChangesAfterDynamicSigningKeyChange() throws Exception {
        Utils.setValueInConfig("access_token_dynamic_signing_key_update_interval", "0.00027"); // 1 second

        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        SessionInformationHolder createInfo = Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                userDataInDatabase, false, AccessToken.getLatestVersion(), false);
        SessionInformationHolder createInfoStatic = Session.createNewSession(process.getProcess(), userId,
                userDataInJWT,
                userDataInDatabase, false, AccessToken.getLatestVersion(), true);

        Thread.sleep(1500);

        SessionInformationHolder sessionInfo = Session.refreshSession(process.getProcess(),
                createInfo.refreshToken.token, null, false, AccessToken.getLatestVersion());
        SessionInformationHolder sessionInfoStatic = Session.refreshSession(process.getProcess(),
                createInfoStatic.refreshToken.token, null, false, AccessToken.getLatestVersion());

        JWT.JWTPreParseInfo preParseInfoCreate = JWT.preParseJWTInfo(createInfo.accessToken.token);
        JWT.JWTPreParseInfo preParseInfoRefresh = JWT.preParseJWTInfo(sessionInfo.accessToken.token);
        JWT.JWTPreParseInfo preParseInfoCreateStatic = JWT.preParseJWTInfo(createInfoStatic.accessToken.token);
        JWT.JWTPreParseInfo preParseInfoRefreshStatic = JWT.preParseJWTInfo(sessionInfoStatic.accessToken.token);

        // The different signing methods added used different kids
        assertNotEquals(preParseInfoCreate.kid, preParseInfoCreateStatic.kid);
        // The non-static kid updated after the old key expired
        assertNotEquals(preParseInfoCreate.kid, preParseInfoRefresh.kid);
        // The static kid remained unchanged
        assertEquals(preParseInfoCreateStatic.kid, preParseInfoRefreshStatic.kid);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void checkDynamicKeyOverlap() throws Exception {
        Utils.setValueInConfig("access_token_dynamic_signing_key_update_interval", "0.00081"); // 3 seconds

        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        AccessTokenSigningKey.getInstance(process.getProcess()).setDynamicSigningKeyOverlapMS(1000);
        SigningKeys signingKeysInstance = SigningKeys.getInstance(process.getProcess());
        assertEquals(2, signingKeysInstance.getAllKeys().size());
        assertEquals(1, signingKeysInstance.getDynamicKeys().size());

        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        SessionInformationHolder createInfo = Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                userDataInDatabase, false, AccessToken.getLatestVersion(), false);
        JWT.JWTPreParseInfo preParseInfoCreate = JWT.preParseJWTInfo(createInfo.accessToken.token);

        Thread.sleep(2000);
        assertEquals(3, signingKeysInstance.getAllKeys().size());
        assertEquals(2, signingKeysInstance.getDynamicKeys().size());

        SessionInformationHolder createInfoDuringOverlap = Session.createNewSession(process.getProcess(), userId,
                userDataInJWT,
                userDataInDatabase, false, AccessToken.getLatestVersion(), false);
        JWT.JWTPreParseInfo preParseInfoCreateDuringOverlap = JWT.preParseJWTInfo(
                createInfoDuringOverlap.accessToken.token);

        Thread.sleep(1000);
        assertEquals(2, signingKeysInstance.getDynamicKeys().size());
        assertEquals(3, signingKeysInstance.getAllKeys().size());

        SessionInformationHolder createInfoAfterOverlap = Session.createNewSession(process.getProcess(), userId,
                userDataInJWT,
                userDataInDatabase, false, AccessToken.getLatestVersion(), false);
        JWT.JWTPreParseInfo preParseInfoAfterOverlap = JWT.preParseJWTInfo(createInfoAfterOverlap.accessToken.token);

        assertEquals(preParseInfoCreate.kid, preParseInfoCreateDuringOverlap.kid);
        assertNotEquals(preParseInfoCreateDuringOverlap.kid, preParseInfoAfterOverlap.kid);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }
}
