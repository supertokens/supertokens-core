/*
 *    Copyright (c) 2023, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.test.session.api;

import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.config.Config;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.session.accessToken.AccessToken;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.utils.SemVer;
import org.junit.*;
import org.junit.rules.TestRule;

import java.time.Instant;

import static junit.framework.TestCase.*;
import static junit.framework.TestCase.assertNull;
import static org.junit.Assert.assertNotNull;

public class VerifySessionAPITest3_0 {
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
    public void successOutputCheckV3AccessToken() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        userDataInJWT.add("nullProp", JsonNull.INSTANCE);
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        JsonObject sessionRequest = new JsonObject();
        sessionRequest.addProperty("userId", userId);
        sessionRequest.add("userDataInJWT", userDataInJWT);
        sessionRequest.add("userDataInDatabase", userDataInDatabase);
        sessionRequest.addProperty("enableAntiCsrf", false);

        JsonObject sessionInfo = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session", sessionRequest, 1000, 1000, null,
                SemVer.v2_21.toString(), "session");

        JsonObject request = new JsonObject();
        request.addProperty("accessToken", sessionInfo.get("accessToken").getAsJsonObject().get("token").getAsString());
        request.addProperty("doAntiCsrfCheck", true);
        request.addProperty("enableAntiCsrf", false);
        request.addProperty("checkDatabase", false);
        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session/verify", request, 1000, 1000, null,
                SemVer.v3_0.get(), "session");

        assertEquals(response.get("status").getAsString(), "OK");

        assertNotNull(response.get("session").getAsJsonObject().get("handle").getAsString());
        assertEquals(response.get("session").getAsJsonObject().get("userId").getAsString(), userId);
        assertEquals(response.get("session").getAsJsonObject().get("userDataInJWT").getAsJsonObject(), userDataInJWT);
        assertEquals(response.get("session").getAsJsonObject().entrySet().size(), 4);

        assertEquals(response.entrySet().size(), 2);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void checkThattIdIsNotPresentInV3() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        JsonObject sessionRequest = new JsonObject();
        sessionRequest.addProperty("userId", userId);
        sessionRequest.add("userDataInJWT", userDataInJWT);
        sessionRequest.add("userDataInDatabase", userDataInDatabase);
        sessionRequest.addProperty("enableAntiCsrf", false);

        JsonObject sessionInfo = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session", sessionRequest, 1000, 1000, null,
                SemVer.v2_21.get(), "session");

        JsonObject request = new JsonObject();
        request.addProperty("accessToken", sessionInfo.get("accessToken").getAsJsonObject().get("token").getAsString());
        request.addProperty("doAntiCsrfCheck", true);
        request.addProperty("enableAntiCsrf", false);
        request.addProperty("checkDatabase", false);
        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session/verify", request, 1000, 1000, null,
                SemVer.v3_0.get(), "session");

        assertEquals(response.get("status").getAsString(), "OK");

        String accessToken = sessionInfo.get("accessToken").getAsJsonObject().get("token").getAsString();

        DecodedJWT decodedJWT = com.auth0.jwt.JWT.decode(accessToken);

        Claim headerAlg = decodedJWT.getHeaderClaim("alg");
        Claim headerType = decodedJWT.getHeaderClaim("typ");
        Claim headerKeyId = decodedJWT.getHeaderClaim("kid");
        Assert.assertTrue(!headerAlg.isNull() && !headerType.isNull() && !headerKeyId.isNull());
        assert headerAlg.asString().equalsIgnoreCase("rs256");
        assert headerType.asString().equalsIgnoreCase("jwt");
        assert !headerKeyId.asString().isEmpty();

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
    public void successOutputCheckNewAccessTokenUpgradeToV4() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        JsonObject sessionRequest = new JsonObject();
        sessionRequest.addProperty("userId", userId);
        sessionRequest.add("userDataInJWT", userDataInJWT);
        sessionRequest.add("userDataInDatabase", userDataInDatabase);
        sessionRequest.addProperty("enableAntiCsrf", false);
        JsonObject sessionInfo = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session", sessionRequest, 1000, 1000, null,
                SemVer.v2_21.get(), "session");

        JsonObject refreshRequest = new JsonObject();
        refreshRequest.addProperty("refreshToken",
                sessionInfo.get("refreshToken").getAsJsonObject().get("token").getAsString());
        refreshRequest.addProperty("enableAntiCsrf", false);
        sessionInfo = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session/refresh", refreshRequest, 1000, 1000, null,
                SemVer.v3_0.get(), "session");

        JsonObject request = new JsonObject();
        request.addProperty("accessToken", sessionInfo.get("accessToken").getAsJsonObject().get("token").getAsString());
        request.addProperty("doAntiCsrfCheck", true);
        request.addProperty("enableAntiCsrf", false);
        request.addProperty("checkDatabase", false);
        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session/verify", request, 1000, 1000, null,
                SemVer.v3_0.get(), "session");

        assertEquals(response.get("status").getAsString(), "OK");

        assertNotNull(response.get("session").getAsJsonObject().get("handle").getAsString());
        assertEquals(response.get("session").getAsJsonObject().get("userId").getAsString(), userId);
        assertEquals(response.get("session").getAsJsonObject().get("userDataInJWT").getAsJsonObject().toString(),
                userDataInJWT.toString());
        assertEquals(response.get("session").getAsJsonObject().entrySet().size(), 4);

        assertTrue(response.get("accessToken").getAsJsonObject().has("token"));
        assertTrue(response.get("accessToken").getAsJsonObject().has("expiry"));
        assertTrue(response.get("accessToken").getAsJsonObject().has("createdTime"));

        assertEquals(response.get("accessToken").getAsJsonObject().entrySet().size(), 3);

        assertEquals(response.entrySet().size(), 3);

        String accessToken = response.get("accessToken").getAsJsonObject().get("token").getAsString();

        DecodedJWT decodedJWT = com.auth0.jwt.JWT.decode(accessToken);

        Claim headerAlg = decodedJWT.getHeaderClaim("alg");
        Claim headerType = decodedJWT.getHeaderClaim("typ");
        Claim headerKeyId = decodedJWT.getHeaderClaim("kid");
        Assert.assertTrue(!headerAlg.isNull() && !headerType.isNull() && !headerKeyId.isNull());
        assert headerAlg.asString().equalsIgnoreCase("rs256");
        assert headerType.asString().equalsIgnoreCase("jwt");
        assert !headerKeyId.asString().isEmpty();

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

        // [antiCsrfToken, sub, sessionHandle, exp, iat, parentRefreshTokenHash1, key, refreshTokenHash1, tId]
        assertEquals(9, decodedJWT.getClaims().size());
        assertEquals("value", decodedJWT.getClaim("key").asString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testAccessTokenInfoOnV3AccessTokenPointsToPublicTenant() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        JsonObject sessionRequest = new JsonObject();
        sessionRequest.addProperty("userId", userId);
        sessionRequest.add("userDataInJWT", userDataInJWT);
        sessionRequest.add("userDataInDatabase", userDataInDatabase);
        sessionRequest.addProperty("enableAntiCsrf", false);
        JsonObject sessionInfo = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session", sessionRequest, 1000, 1000, null,
                SemVer.v2_21.get(), "session");

        AccessToken.AccessTokenInfo accessTokenInfo = AccessToken.getInfoFromAccessToken(new AppIdentifier(null, null),
                process.getProcess(), sessionInfo.get("accessToken").getAsJsonObject().get("token").getAsString(),
                false);

        assertEquals(TenantIdentifier.DEFAULT_TENANT_ID, accessTokenInfo.tenantIdentifier.getTenantId());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testV4AccessTokenOnOlderCDIWorksFine() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        userDataInJWT.add("nullProp", JsonNull.INSTANCE);
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        JsonObject sessionRequest = new JsonObject();
        sessionRequest.addProperty("userId", userId);
        sessionRequest.add("userDataInJWT", userDataInJWT);
        sessionRequest.add("userDataInDatabase", userDataInDatabase);
        sessionRequest.addProperty("enableAntiCsrf", false);

        JsonObject sessionInfo = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session", sessionRequest, 1000, 1000, null,
                SemVer.v3_0.get(), "session");

        JsonObject request = new JsonObject();
        request.addProperty("accessToken", sessionInfo.get("accessToken").getAsJsonObject().get("token").getAsString());
        request.addProperty("doAntiCsrfCheck", true);
        request.addProperty("enableAntiCsrf", false);
        request.addProperty("checkDatabase", false);
        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session/verify", request, 1000, 1000, null,
                SemVer.v2_21.get(), "session");

        assertEquals(response.get("status").getAsString(), "OK");

        assertNotNull(response.get("session").getAsJsonObject().get("handle").getAsString());
        assertEquals(response.get("session").getAsJsonObject().get("userId").getAsString(), userId);
        assertEquals(response.get("session").getAsJsonObject().get("userDataInJWT").getAsJsonObject(), userDataInJWT);
        assertEquals(response.get("session").getAsJsonObject().entrySet().size(), 3);

        assertEquals(response.entrySet().size(), 2);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testV3AccessTokenWithCustomtIdPayload() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        userDataInJWT.addProperty("tId", "tenant1");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        JsonObject sessionRequest = new JsonObject();
        sessionRequest.addProperty("userId", userId);
        sessionRequest.add("userDataInJWT", userDataInJWT);
        sessionRequest.add("userDataInDatabase", userDataInDatabase);
        sessionRequest.addProperty("enableAntiCsrf", false);
        JsonObject sessionInfo = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session", sessionRequest, 1000, 1000, null,
                SemVer.v2_21.get(), "session");

        AccessToken.AccessTokenInfo accessTokenInfo = AccessToken.getInfoFromAccessToken(new AppIdentifier(null, null),
                process.getProcess(), sessionInfo.get("accessToken").getAsJsonObject().get("token").getAsString(),
                false);

        assertEquals(TenantIdentifier.DEFAULT_TENANT_ID, accessTokenInfo.tenantIdentifier.getTenantId());


        {
            String accessToken = sessionInfo.get("accessToken").getAsJsonObject().get("token").getAsString();
            DecodedJWT decodedJWT = com.auth0.jwt.JWT.decode(accessToken);
            assertEquals(decodedJWT.getClaim("tId").asString(), "tenant1");
        }

        JsonObject sessionRefreshRequest = new JsonObject();
        sessionRefreshRequest.addProperty("refreshToken",
                sessionInfo.get("refreshToken").getAsJsonObject().get("token").getAsString());
        sessionRefreshRequest.addProperty("enableAntiCsrf", false);

        JsonObject sessionRefreshResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session/refresh", sessionRefreshRequest, 1000, 1000, null,
                SemVer.v2_21.get(), "session");

        assertEquals("OK", sessionRefreshResponse.get("status").getAsString());

        {
            String accessToken = sessionRefreshResponse.get("accessToken").getAsJsonObject().get("token").getAsString();
            DecodedJWT decodedJWT = com.auth0.jwt.JWT.decode(accessToken);
            assertEquals(decodedJWT.getClaim("tId").asString(), "tenant1");
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testV3AccessTokenWithCustomtIdPayloadAfterRefreshInV4() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        userDataInJWT.addProperty("tId", "tenant1");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        JsonObject sessionRequest = new JsonObject();
        sessionRequest.addProperty("userId", userId);
        sessionRequest.add("userDataInJWT", userDataInJWT);
        sessionRequest.add("userDataInDatabase", userDataInDatabase);
        sessionRequest.addProperty("enableAntiCsrf", false);
        JsonObject sessionInfo = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session", sessionRequest, 1000, 1000, null,
                SemVer.v2_21.get(), "session");

        AccessToken.AccessTokenInfo accessTokenInfo = AccessToken.getInfoFromAccessToken(new AppIdentifier(null, null),
                process.getProcess(), sessionInfo.get("accessToken").getAsJsonObject().get("token").getAsString(),
                false);

        assertEquals(TenantIdentifier.DEFAULT_TENANT_ID, accessTokenInfo.tenantIdentifier.getTenantId());

        JsonObject sessionRefreshRequest = new JsonObject();
        sessionRefreshRequest.addProperty("refreshToken",
                sessionInfo.get("refreshToken").getAsJsonObject().get("token").getAsString());
        sessionRefreshRequest.addProperty("enableAntiCsrf", false);

        JsonObject sessionRefreshResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session/refresh", sessionRefreshRequest, 1000, 1000, null,
                SemVer.v3_0.get(), "session");

        assertEquals("UNAUTHORISED", sessionRefreshResponse.get("status").getAsString());
        assertEquals("The user payload contains protected field", sessionRefreshResponse.get("message").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
