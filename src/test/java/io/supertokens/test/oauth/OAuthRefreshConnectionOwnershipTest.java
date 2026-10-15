/*
 *    Copyright (c) 2025, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.test.oauth;

import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.inmemorydb.ConnectionPool;
import io.supertokens.oauth.HttpRequestForOAuthProvider;
import io.supertokens.oauth.OAuth;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.oauth.OAuthStorage;
import io.supertokens.signingkeys.JWTSigningKey;
import io.supertokens.signingkeys.SigningKeys;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.oauth.api.OAuthAPIHelper;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Deterministic, single-connection-ownership regression test for the non-rotating OAuth refresh path
 * (PLAN-017 / CORE-2).
 *
 * <p>Background: {@code OAuthTokenAPI.handleNonRotatingRefresh} opens one outer SQL transaction and holds its
 * connection for the full OAuth-provider round-trip. The regression (Core PR #1246) let helpers reached inside that
 * transaction borrow <em>additional</em> pool connections (revocation read, signing-key read, client lookup).
 * With a small pool this causes hold-and-wait exhaustion and 5-second HikariCP acquisition timeouts.
 *
 * <p>These tests reproduce that structurally on the in-memory SQLite storage without any real HTTP or timing:
 * <ul>
 *   <li>The OAuth-provider HTTP round-trip is replaced by a deterministic in-process stub
 *       ({@link OAuth#doOAuthProxyFormPOSTTestHook}). The stub is installed <em>after</em> the
 *       {@code getOAuthClientById} client-existence check inside {@code doOAuthProxyFormPOST}, so it removes only
 *       the network dependency and does not mask any storage borrow.</li>
 *   <li>The in-memory connection pool is instrumented ({@link ConnectionPool#startBorrowTracking}) to record the
 *       peak number of connections a single thread holds at once. A correct implementation holds exactly one
 *       (the outer transaction connection); any nested borrow pushes the per-thread peak to two.</li>
 * </ul>
 *
 * <p>This test is intentionally NOT an extension of {@code TestOAuthRefreshRaceCondition}: that suite tolerates
 * Core exceptions as infra noise and can miss this regression (RCA §9).
 *
 * <p>Unlike the api tests, this needs no running oauth-provider container: everything is stubbed.
 */
public class OAuthRefreshConnectionOwnershipTest {

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

    private static final String CLIENT_ID = "test-client-id";
    private static final String CLIENT_SECRET = "test-client-secret";
    private static final String GID = "test-gid-0001";
    private static final String JTI = "test-jti-0001";
    private static final String EXTERNAL_RT = "st_rt_external_0001";
    private static final String INTERNAL_RT = "ory_rt_internal_0001";

    /**
     * Craft a minimal well-formed JWT that the OAuth provider would return as an access token. The non-rotating refresh
     * handler re-signs it with the Core signing keys ({@code OAuthToken.reSignToken} decodes without verifying),
     * so the signature here is arbitrary. Deliberately carries no {@code sessionHandle} so the best-effort
     * {@code updateLastActive} side-call (which may target another storage) is not exercised.
     */
    private static String fakeProviderAccessToken() {
        long now = System.currentTimeMillis() / 1000L;
        JsonObject header = new JsonObject();
        header.addProperty("typ", "JWT");
        header.addProperty("alg", "RS256");
        header.addProperty("kid", "test-kid"); // required by JWT.preParseJWTInfo; not used since we re-sign
        JsonObject payload = new JsonObject();
        payload.addProperty("gid", GID);
        payload.addProperty("jti", JTI);
        payload.addProperty("client_id", CLIENT_ID);
        payload.addProperty("sub", "someuserid");
        payload.addProperty("iat", now);
        payload.addProperty("exp", now + 3600);
        payload.addProperty("scp", "openid offline_access");
        Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();
        String h = enc.encodeToString(header.toString().getBytes(StandardCharsets.UTF_8));
        String p = enc.encodeToString(payload.toString().getBytes(StandardCharsets.UTF_8));
        String s = enc.encodeToString("not-verified".getBytes(StandardCharsets.UTF_8));
        return h + "." + p + "." + s;
    }

    /** Deterministic provider stub for introspect + exchange, driven by request path (no HTTP, no released con). */
    private static OAuth.DoOAuthProxyFormPOSTTestHook providerStub() {
        long exp = System.currentTimeMillis() / 1000L + 3600;
        return (path, proxyToAdmin, formFields) -> {
            JsonObject json = new JsonObject();
            if (path.equals("/oauth2/token")) {
                // Token exchange → access-token / refresh-token pair.
                json.addProperty("access_token", fakeProviderAccessToken());
                json.addProperty("refresh_token", "ory_rt_rotated_0001");
                json.addProperty("expires_in", 3600);
                json.addProperty("token_type", "bearer");
                json.addProperty("scope", "openid offline_access");
            } else {
                // Introspection (pre-exchange refresh-token check and post-exchange expiry read). gid present,
                // no jti → the revocation read takes the by-GID branch. active:true keeps the flow going.
                json.addProperty("active", true);
                json.addProperty("gid", GID);
                json.addProperty("exp", exp);
                json.addProperty("client_id", CLIENT_ID);
                json.addProperty("scope", "openid offline_access");
            }
            return new HttpRequestForOAuthProvider.Response(200, json.toString(), json, new HashMap<>());
        };
    }

    private static JsonObject refreshRequestBody() {
        JsonObject inputBody = new JsonObject();
        inputBody.addProperty("grant_type", "refresh_token");
        inputBody.addProperty("refresh_token", EXTERNAL_RT);
        inputBody.addProperty("client_id", CLIENT_ID);
        inputBody.addProperty("client_secret", CLIENT_SECRET);
        JsonObject body = new JsonObject();
        body.add("inputBody", inputBody);
        body.add("access_token", new JsonObject()); // required (non-null) for the refresh_token grant
        body.add("id_token", new JsonObject());
        body.addProperty("iss", "http://localhost:3001/auth");
        return body;
    }

    /**
     * Precreate everything the refresh path reads so the only DB work during the measured refresh is the
     * handler's own: a non-rotating OAuth client and a matching OAuth session row (which also registers the
     * external→internal refresh-token mapping and makes the gid resolve as "not revoked"). Optionally warms the
     * signing-key caches so the signing read is served from cache (isolating the revocation borrow).
     */
    private static void seedStorage(Main main, AppIdentifier appIdentifier, boolean warmSigningKeys)
            throws Exception {
        OAuthStorage storage = (OAuthStorage) StorageLayer.getStorage(main);
        // Store via OAuth.addOrUpdateClient so the secret is encrypted the same way the create-client API does
        // (OAuth.getOAuthClientById decrypts it on read).
        OAuth.addOrUpdateClient(main, appIdentifier, storage, CLIENT_ID, CLIENT_SECRET,
                false /* isClientCredentialsOnly */, false /* enableRefreshTokenRotation */);
        long exp = System.currentTimeMillis() / 1000L + 3600;
        storage.createOrUpdateOAuthSession(appIdentifier, GID, CLIENT_ID, EXTERNAL_RT, INTERNAL_RT,
                null /* sessionHandle */, JTI, exp);

        if (warmSigningKeys) {
            SigningKeys signingKeys = SigningKeys.getInstance(appIdentifier, main);
            signingKeys.getAllKeys();
            signingKeys.getStaticKeyForAlgorithm(JWTSigningKey.SupportedAlgorithms.RS256);
        }
    }

    /**
     * Primary: a single non-rotating refresh must hold exactly one pool connection at a time. Signing keys are
     * warmed first, so this isolates the nested revocation borrow (the largest remaining leak in the RCA).
     */
    @Test
    public void nonRotatingRefresh_holdsExactlyOneConnection() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.OAUTH});

        Main main = process.getProcess();
        AppIdentifier appIdentifier = process.getAppForTesting().toAppIdentifier();
        seedStorage(main, appIdentifier, true /* warm signing keys */);

        OAuth.doOAuthProxyFormPOSTTestHook = providerStub();
        try {
            ConnectionPool.startBorrowTracking(false /* peak gauge only, do not enforce */);
            JsonObject response = OAuthAPIHelper.token(main, refreshRequestBody());
            ConnectionPool.stopBorrowTracking();

            assertEquals("OK", response.get("status").getAsString());
            assertFalse("non-rotating refresh must not surface a new refresh_token",
                    response.has("refresh_token"));

            int peak = ConnectionPool.getPeakConcurrentConnectionsPerThread();
            assertEquals("a non-rotating refresh must hold at most one DB connection at a time on any thread;"
                    + " a peak of " + peak + " means a helper borrowed a nested connection while the outer"
                    + " transaction connection was held (hold-and-wait pool exhaustion under a bounded pool)",
                    1, peak);
        } finally {
            OAuth.doOAuthProxyFormPOSTTestHook = null;
            ConnectionPool.stopBorrowTracking();
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * Second isolation: with the signing-key cache cold and a dynamic signing key, prove the signing borrow is
     * gone too. Tested separately from the revocation borrow so an early failure can't mask a later one (RCA §10).
     */
    @Test
    public void nonRotatingRefresh_holdsOneConnection_coldDynamicSigningKey() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.OAUTH});

        Main main = process.getProcess();
        AppIdentifier appIdentifier = process.getAppForTesting().toAppIdentifier();
        seedStorage(main, appIdentifier, false /* do NOT warm — signing key cold */);

        // useStaticSigningKey=false → dynamic-key path.
        JsonObject body = refreshRequestBody();
        body.addProperty("useStaticSigningKey", false);

        OAuth.doOAuthProxyFormPOSTTestHook = providerStub();
        try {
            ConnectionPool.startBorrowTracking(false);
            JsonObject response = OAuthAPIHelper.token(main, body);
            ConnectionPool.stopBorrowTracking();

            assertEquals("OK", response.get("status").getAsString());
            int peak = ConnectionPool.getPeakConcurrentConnectionsPerThread();
            assertEquals("cold/dynamic signing key non-rotating refresh must still hold at most one DB"
                    + " connection at a time (peak was " + peak + ")", 1, peak);
        } finally {
            OAuth.doOAuthProxyFormPOSTTestHook = null;
            ConnectionPool.stopBorrowTracking();
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
