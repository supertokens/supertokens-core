

package io.supertokens.test.oauth.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * Regression tests for race conditions in the OAuth token endpoint.
 *
 * These tests are intentionally NOT wrapped with {@code Utils.retryFlakyTest()} because they
 * are designed to FAIL deterministically when the underlying bug is present and PASS only
 * when the fix is applied.  Retrying on failure would hide the bug.
 */
public class TestOAuthRefreshRaceCondition {

    /** Print on failure but do NOT retry. */
    @Rule
    public TestRule watchman = Utils.getOnFailure();

    @AfterClass
    public static void afterTesting() {
        Utils.afterTesting();
    }

    @Before
    public void beforeEach() {
        Utils.reset();
        OAuthAPIHelper.resetOAuthProvider();
    }

    // -------------------------------------------------------------------------
    // Helpers (duplicated from TestRefreshTokenFlowWithTokenRotationOptions to
    // keep this class self-contained and independent)
    // -------------------------------------------------------------------------

    private static JsonObject createClient(Main main, boolean enableRefreshTokenRotation) throws Exception {
        JsonObject clientBody = new JsonObject();
        JsonArray grantTypes = new JsonArray();
        grantTypes.add(new JsonPrimitive("authorization_code"));
        grantTypes.add(new JsonPrimitive("refresh_token"));
        clientBody.add("grantTypes", grantTypes);
        JsonArray responseTypes = new JsonArray();
        responseTypes.add(new JsonPrimitive("code"));
        responseTypes.add(new JsonPrimitive("id_token"));
        clientBody.add("responseTypes", responseTypes);
        JsonArray redirectUris = new JsonArray();
        redirectUris.add(new JsonPrimitive("http://localhost.com:3000/auth/callback/supertokens"));
        clientBody.add("redirectUris", redirectUris);
        clientBody.addProperty("scope", "openid email offline_access");
        clientBody.addProperty("tokenEndpointAuthMethod", "client_secret_post");
        clientBody.addProperty("enableRefreshTokenRotation", enableRefreshTokenRotation);
        return OAuthAPIHelper.createClient(main, clientBody);
    }

    private static JsonObject completeFlowAndGetTokens(Main main, JsonObject client) throws Exception {
        List<String> allCookies = new ArrayList<>();
        JsonObject authRequestBody = new JsonObject();
        JsonObject params = new JsonObject();
        params.addProperty("client_id", client.get("clientId").getAsString());
        params.addProperty("redirect_uri", "http://localhost.com:3000/auth/callback/supertokens");
        params.addProperty("response_type", "code");
        params.addProperty("scope", "openid offline_access");
        params.addProperty("state", "test12345678");
        authRequestBody.add("params", params);

        JsonObject authResponse = OAuthAPIHelper.auth(main, authRequestBody);
        allCookies.clear();
        for (JsonElement cookieElem : authResponse.get("cookies").getAsJsonArray()) {
            allCookies.add(cookieElem.getAsString().split(";")[0]);
        }

        String redirectTo = authResponse.get("redirectTo").getAsString()
                .replace("{apiDomain}", "http://localhost:3001/auth");
        URL url = new URL(redirectTo);
        Map<String, String> queryParams = splitQuery(url);
        String loginChallenge = queryParams.get("login_challenge");

        Map<String, String> acceptLoginRequestParams = new HashMap<>();
        acceptLoginRequestParams.put("loginChallenge", loginChallenge);
        JsonObject acceptLoginRequestBody = new JsonObject();
        acceptLoginRequestBody.addProperty("subject", "someuserid");
        acceptLoginRequestBody.addProperty("remember", true);
        acceptLoginRequestBody.addProperty("rememberFor", 3600);
        acceptLoginRequestBody.addProperty("identityProviderSessionId", "session-handle");
        JsonObject acceptLoginResponse = OAuthAPIHelper.acceptLoginRequest(main, acceptLoginRequestParams,
                acceptLoginRequestBody);

        redirectTo = acceptLoginResponse.get("redirectTo").getAsString()
                .replace("{apiDomain}", "http://localhost:3001/auth");
        url = new URL(redirectTo);
        queryParams = splitQuery(url);

        JsonObject params2 = new JsonObject();
        for (Map.Entry<String, String> entry : queryParams.entrySet()) {
            params2.addProperty(entry.getKey(), entry.getValue());
        }
        authRequestBody.add("params", params2);
        authRequestBody.addProperty("cookies", String.join("; ", allCookies));
        authResponse = OAuthAPIHelper.auth(main, authRequestBody);

        redirectTo = authResponse.get("redirectTo").getAsString()
                .replace("{apiDomain}", "http://localhost:3001/auth");
        allCookies.clear();
        for (JsonElement cookieElem : authResponse.get("cookies").getAsJsonArray()) {
            allCookies.add(cookieElem.getAsString().split(";")[0]);
        }

        url = new URL(redirectTo);
        queryParams = splitQuery(url);
        String consentChallenge = queryParams.get("consent_challenge");

        JsonObject acceptConsentRequestBody = new JsonObject();
        acceptConsentRequestBody.addProperty("iss", "http://localhost:3001/auth");
        acceptConsentRequestBody.addProperty("tId", "public");
        acceptConsentRequestBody.addProperty("rsub", "someuserid");
        acceptConsentRequestBody.addProperty("sessionHandle", "session-handle");
        acceptConsentRequestBody.add("initialAccessTokenPayload", new JsonObject());
        acceptConsentRequestBody.add("initialIdTokenPayload", new JsonObject());
        JsonArray grantScope = new JsonArray();
        grantScope.add(new JsonPrimitive("openid"));
        grantScope.add(new JsonPrimitive("offline_access"));
        acceptConsentRequestBody.add("grantScope", grantScope);
        acceptConsentRequestBody.add("grantAccessTokenAudience", new JsonArray());
        JsonObject session = new JsonObject();
        session.add("access_token", new JsonObject());
        session.add("id_token", new JsonObject());
        acceptConsentRequestBody.add("session", session);

        Map<String, String> consentParams = new HashMap<>();
        consentParams.put("consentChallenge", consentChallenge);
        JsonObject acceptConsentResponse = OAuthAPIHelper.acceptConsentRequest(main, consentParams,
                acceptConsentRequestBody);

        redirectTo = acceptConsentResponse.get("redirectTo").getAsString()
                .replace("{apiDomain}", "http://localhost:3001/auth");
        url = new URL(redirectTo);
        queryParams = splitQuery(url);

        JsonObject params3 = new JsonObject();
        for (Map.Entry<String, String> entry : queryParams.entrySet()) {
            params3.addProperty(entry.getKey(), entry.getValue());
        }
        authRequestBody.add("params", params3);
        authRequestBody.addProperty("cookies", String.join("; ", allCookies));
        authResponse = OAuthAPIHelper.auth(main, authRequestBody);

        redirectTo = authResponse.get("redirectTo").getAsString()
                .replace("{apiDomain}", "http://localhost:3001/auth");
        url = new URL(redirectTo);
        queryParams = splitQuery(url);
        String authorizationCode = queryParams.get("code");

        JsonObject tokenRequestBody = new JsonObject();
        JsonObject inputBody = new JsonObject();
        inputBody.addProperty("grant_type", "authorization_code");
        inputBody.addProperty("code", authorizationCode);
        inputBody.addProperty("redirect_uri", "http://localhost.com:3000/auth/callback/supertokens");
        inputBody.addProperty("client_id", client.get("clientId").getAsString());
        inputBody.addProperty("client_secret", client.get("clientSecret").getAsString());
        tokenRequestBody.add("inputBody", inputBody);
        tokenRequestBody.addProperty("iss", "http://localhost:3001/auth");
        return OAuthAPIHelper.token(main, tokenRequestBody);
    }

    private static JsonObject refreshToken(Main main, JsonObject client, String refreshToken) throws Exception {
        JsonObject tokenRequestBody = new JsonObject();
        JsonObject inputBody = new JsonObject();
        inputBody.addProperty("grant_type", "refresh_token");
        inputBody.addProperty("refresh_token", refreshToken);
        inputBody.addProperty("client_id", client.get("clientId").getAsString());
        inputBody.addProperty("client_secret", client.get("clientSecret").getAsString());
        tokenRequestBody.add("access_token", new JsonObject());
        tokenRequestBody.add("id_token", new JsonObject());
        tokenRequestBody.add("inputBody", inputBody);
        tokenRequestBody.addProperty("iss", "http://localhost:3001/auth");
        return OAuthAPIHelper.token(main, tokenRequestBody);
    }

    private static Map<String, String> splitQuery(URL url) throws UnsupportedEncodingException {
        Map<String, String> queryPairs = new LinkedHashMap<>();
        String query = url.getQuery();
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            queryPairs.put(URLDecoder.decode(pair.substring(0, idx), "UTF-8"),
                    URLDecoder.decode(pair.substring(idx + 1), "UTF-8"));
        }
        return queryPairs;
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    /**
     * Regression test for the race condition where concurrent refresh-token calls with rotation
     * disabled would all read the same Hydra-internal token from the DB, then race to exchange it
     * with Hydra. Because Hydra rotates the token on the first successful exchange, all subsequent
     * threads that carry the now-stale token receive "token_inactive".
     *
     * <p>Fix: {@code SELECT … FOR UPDATE} on the {@code oauth_sessions} row serialises the
     * read-exchange-write cycle so every concurrent call takes its turn and all succeed.</p>
     *
     * <p>This test deliberately avoids {@code Utils.retryFlakyTest()} because it must FAIL when
     * the bug is present and PASS only when the fix is in place.</p>
     */
    @Test
    public void testConcurrentRefreshWithoutRotationRaceCondition() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.OAUTH});

        JsonObject client = createClient(process.getProcess(), false);
        JsonObject tokens = completeFlowAndGetTokens(process.getProcess(), client);
        final String externalRefreshToken = tokens.get("refresh_token").getAsString();

        // Use more threads than the default DB connection-pool size to maximise contention.
        final int concurrency = 20;
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        CountDownLatch ready = new CountDownLatch(concurrency); // threads signal when ready
        CountDownLatch start = new CountDownLatch(1);           // main fires the starting gun

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<String> failures = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < concurrency; i++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    JsonObject resp = refreshToken(process.getProcess(), client, externalRefreshToken);
                    if ("OK".equals(resp.get("status").getAsString())) {
                        successCount.incrementAndGet();
                    } else {
                        failureCount.incrementAndGet();
                        failures.add(resp.toString());
                    }
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    failures.add(e.getMessage());
                }
            });
        }

        ready.await();   // wait until all threads are poised at start.await()
        start.countDown(); // fire all threads simultaneously

        executor.shutdown();
        assertTrue("Timed out waiting for concurrent refreshes",
                executor.awaitTermination(2, TimeUnit.MINUTES));

        assertEquals("All concurrent non-rotating refresh calls must succeed — failures: " + failures,
                concurrency, successCount.get());
        assertEquals("No refresh call should fail", 0, failureCount.get());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
