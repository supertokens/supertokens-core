/*
 *    Copyright (c) 2026, VRAI Labs and/or its affiliates. All rights reserved.
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
import com.webauthn4j.data.extension.client.AuthenticationExtensionClientOutput;
import com.webauthn4j.data.AuthenticatorAssertionResponse;
import com.webauthn4j.data.PublicKeyCredential;
import com.webauthn4j.test.EmulatorUtil;
import io.supertokens.ProcessState;
import io.supertokens.inmemorydb.Start;
import io.supertokens.inmemorydb.TransactionCounters.Snapshot;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

/**
 * Pins how many transactions and commits each hot API makes today on the in-memory storage, measured with
 * {@link TransactionCountCapture} around one HTTP request. The commit-reduction follow-ups lower these numbers as
 * they land, so update the expected value in the same change; any other difference is a regression.
 * <p>
 * Each expected value reads {@code Snapshot.of(transactionsOpened, writingCommits, autocommitWrites)}. Activity
 * events are written on every request here, because the activity throttle is off under {@code Main.isTesting}.
 * Each test warms up first (e.g. signing keys are created on first use), so the measured request is a
 * steady-state one.
 */
public class TransactionCountBaselineTest {

    private static final String BASE = "http://localhost:3567";

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
    public void sessionApis() throws Exception {
        TestingProcessManager.TestingProcess process = startInMemory();
        if (process == null) {
            return;
        }
        Baseline baseline = new Baseline();
        String userId = emailPasswordSignUp(process, "session-user@example.com").get("id").getAsString();

        // warm-up: the first session creates the signing keys
        createSession(process, userId);

        JsonObject session = baseline.measure("session create", Snapshot.of(0, 0, 2),
                () -> createSession(process, userId));
        String handle = session.getAsJsonObject("session").get("handle").getAsString();

        JsonObject refreshed = baseline.measure("session refresh", Snapshot.of(1, 1, 1),
                () -> refreshSession(process, session.getAsJsonObject("refreshToken").get("token").getAsString()));

        baseline.measure("session verify (checkDatabase)", Snapshot.of(0, 0, 0),
                () -> verifySession(process, refreshed.getAsJsonObject("accessToken").get("token").getAsString()));

        baseline.measure("session info", Snapshot.of(0, 0, 0), () -> {
            Map<String, String> params = new HashMap<>();
            params.put("sessionHandle", handle);
            JsonObject response = get(process, "/recipe/session", params, "session");
            assertEquals(handle, response.get("sessionHandle").getAsString());
            return response;
        });

        baseline.measure("session remove", Snapshot.of(0, 0, 1), () -> {
            JsonObject body = new JsonObject();
            JsonArray handles = new JsonArray();
            handles.add(handle);
            body.add("sessionHandles", handles);
            JsonObject response = post(process, "/recipe/session/remove", body, "session");
            assertEquals("OK", response.get("status").getAsString());
            assertEquals(handles, response.getAsJsonArray("sessionHandlesRevoked"));
            return response;
        });

        baseline.assertMatches();
        stop(process);
    }

    @Test
    public void emailPasswordApis() throws Exception {
        TestingProcessManager.TestingProcess process = startInMemory();
        if (process == null) {
            return;
        }
        Baseline baseline = new Baseline();
        emailPasswordSignUp(process, "warm-up@example.com");

        baseline.measure("email-password sign-up", Snapshot.of(1, 1, 1),
                () -> emailPasswordSignUp(process, "ep-user@example.com"));

        baseline.measure("email-password sign-in", Snapshot.of(0, 0, 2), () -> {
            JsonObject body = new JsonObject();
            body.addProperty("email", "ep-user@example.com");
            body.addProperty("password", "validPass123");
            JsonObject response = post(process, "/recipe/signin", body, "emailpassword");
            assertEquals("OK", response.get("status").getAsString());
            return response;
        });

        baseline.assertMatches();
        stop(process);
    }

    @Test
    public void thirdPartySignInUp() throws Exception {
        TestingProcessManager.TestingProcess process = startInMemory();
        if (process == null) {
            return;
        }
        Baseline baseline = new Baseline();
        thirdPartySignInUp(process, "warm-up", "warm-up@example.com");

        baseline.measure("third-party sign-in-up (new user)", Snapshot.of(1, 1, 1),
                () -> thirdPartySignInUp(process, "tp-user", "tp-user@example.com"));
        baseline.measure("third-party sign-in-up (existing user)", Snapshot.of(1, 0, 2),
                () -> thirdPartySignInUp(process, "tp-user", "tp-user@example.com"));

        baseline.assertMatches();
        stop(process);
    }

    @Test
    public void passwordlessApis() throws Exception {
        TestingProcessManager.TestingProcess process = startInMemory();
        if (process == null) {
            return;
        }
        Baseline baseline = new Baseline();
        consumeCode(process, createCode(process, "warm-up@example.com"));

        JsonObject code = baseline.measure("passwordless create-code", Snapshot.of(1, 1, 0),
                () -> createCode(process, "pl-user@example.com"));
        baseline.measure("passwordless consume-code (new user)", Snapshot.of(3, 3, 1),
                () -> consumeCode(process, code));

        JsonObject secondCode = createCode(process, "pl-user@example.com");
        baseline.measure("passwordless consume-code (existing user)", Snapshot.of(2, 1, 2),
                () -> consumeCode(process, secondCode));

        baseline.assertMatches();
        stop(process);
    }

    @Test
    public void webAuthnSignIn() throws Exception {
        TestingProcessManager.TestingProcess process = startInMemory();
        if (process == null) {
            return;
        }
        Baseline baseline = new Baseline();
        prepareWebAuthnSignIn(process, io.supertokens.test.webauthn.Utils.registerUserWithCredentials(
                process.getProcess(), "warm-up@example.com")).call();

        JsonObject user = io.supertokens.test.webauthn.Utils.registerUserWithCredentials(process.getProcess(),
                "wa-user@example.com");
        baseline.measure("webauthn sign-in", Snapshot.of(1, 1, 1), prepareWebAuthnSignIn(process, user));

        baseline.assertMatches();
        stop(process);
    }

    @Test
    public void readApis() throws Exception {
        TestingProcessManager.TestingProcess process = startInMemory();
        if (process == null) {
            return;
        }
        Baseline baseline = new Baseline();
        String userId = emailPasswordSignUp(process, "read-user@example.com").get("id").getAsString();
        Map<String, String> byUserId = new HashMap<>();
        byUserId.put("userId", userId);

        baseline.measure("get user by id", Snapshot.of(0, 0, 0),
                () -> get(process, "/user/id", byUserId, null));

        Map<String, String> byEmail = new HashMap<>();
        byEmail.put("email", "read-user@example.com");
        byEmail.put("doUnionOfAccountInfo", "false");
        baseline.measure("get users by account info", Snapshot.of(0, 0, 0),
                () -> get(process, "/users/by-accountinfo", byEmail, null));

        baseline.measure("user metadata get", Snapshot.of(0, 0, 0),
                () -> get(process, "/recipe/user/metadata", byUserId, "usermetadata"));

        baseline.measure("user roles get", Snapshot.of(0, 0, 0),
                () -> get(process, "/recipe/user/roles", byUserId, "userroles"));

        // The same reads for a user with a user id mapping, addressed by the external id: the lookup resolves
        // the mapping instead of finding the id in the auth recipes.
        String mappedUserId = emailPasswordSignUp(process, "mapped-user@example.com").get("id").getAsString();
        JsonObject mapping = new JsonObject();
        mapping.addProperty("superTokensUserId", mappedUserId);
        mapping.addProperty("externalUserId", "external-read-user");
        assertEquals("OK", post(process, "/recipe/userid/map", mapping, "useridmapping").get("status").getAsString());
        Map<String, String> byExternalId = new HashMap<>();
        byExternalId.put("userId", "external-read-user");

        baseline.measure("get user by id (external id)", Snapshot.of(0, 0, 0), () -> {
            JsonObject response = get(process, "/user/id", byExternalId, null);
            assertEquals("external-read-user", response.getAsJsonObject("user").get("id").getAsString());
            return response;
        });

        baseline.measure("user metadata get (external id)", Snapshot.of(0, 0, 0),
                () -> get(process, "/recipe/user/metadata", byExternalId, "usermetadata"));

        baseline.measure("user roles get (external id)", Snapshot.of(0, 0, 0),
                () -> get(process, "/recipe/user/roles", byExternalId, "userroles"));

        baseline.assertMatches();
        stop(process);
    }

    // Collects every measurement and reports all mismatches together, so a change shows its full effect at once.
    private static class Baseline {
        private final List<String> lines = new ArrayList<>();
        private boolean mismatch = false;

        <T> T measure(String api, Snapshot expected, Callable<T> request) throws Exception {
            TransactionCountCapture.Captured<T> captured = TransactionCountCapture.captureRequest(request);
            boolean matches = expected.equals(captured.counts);
            mismatch |= !matches;
            lines.add((matches ? "  ok       " : "  MISMATCH ") + api + ": expected " + expected + ", got "
                    + captured.counts);
            return captured.response;
        }

        void assertMatches() {
            String report = String.join("\n", lines);
            System.out.println("[transaction-count-baseline]\n" + report);
            if (mismatch) {
                fail("transaction counts differ from the baseline:\n" + report);
            }
        }
    }

    private static TestingProcessManager.TestingProcess startInMemory() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        if (!(StorageLayer.getStorage(process.getProcess()) instanceof Start)) {
            // The counters exist only in the in-memory storage.
            stop(process);
            return null;
        }
        return process;
    }

    private static void stop(TestingProcessManager.TestingProcess process) throws Exception {
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    private static JsonObject post(TestingProcessManager.TestingProcess process, String path, JsonObject body,
                                   String rid) throws Exception {
        return HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "", BASE + path, body, 1000, 1000,
                null, Utils.getCdiVersionStringLatestForTests(), rid);
    }

    private static JsonObject get(TestingProcessManager.TestingProcess process, String path,
                                  Map<String, String> params, String rid) throws Exception {
        JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "", BASE + path, params,
                1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), rid);
        assertEquals("OK", response.get("status").getAsString());
        return response;
    }

    // Returns the created user.
    private static JsonObject emailPasswordSignUp(TestingProcessManager.TestingProcess process, String email)
            throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("email", email);
        body.addProperty("password", "validPass123");
        JsonObject response = post(process, "/recipe/signup", body, "emailpassword");
        assertEquals("OK", response.get("status").getAsString());
        return response.getAsJsonObject("user");
    }

    private static JsonObject createSession(TestingProcessManager.TestingProcess process, String userId)
            throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("userId", userId);
        body.add("userDataInJWT", new JsonObject());
        body.add("userDataInDatabase", new JsonObject());
        body.addProperty("enableAntiCsrf", false);
        JsonObject response = post(process, "/recipe/session", body, "session");
        assertEquals("OK", response.get("status").getAsString());
        return response;
    }

    private static JsonObject refreshSession(TestingProcessManager.TestingProcess process, String refreshToken)
            throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("refreshToken", refreshToken);
        body.addProperty("enableAntiCsrf", false);
        body.addProperty("useDynamicSigningKey", true);
        JsonObject response = post(process, "/recipe/session/refresh", body, "session");
        assertEquals("OK", response.get("status").getAsString());
        return response;
    }

    private static JsonObject verifySession(TestingProcessManager.TestingProcess process, String accessToken)
            throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("accessToken", accessToken);
        body.addProperty("doAntiCsrfCheck", false);
        body.addProperty("enableAntiCsrf", false);
        body.addProperty("checkDatabase", true);
        JsonObject response = post(process, "/recipe/session/verify", body, "session");
        assertEquals("OK", response.get("status").getAsString());
        return response;
    }

    private static JsonObject thirdPartySignInUp(TestingProcessManager.TestingProcess process,
                                                 String thirdPartyUserId, String email) throws Exception {
        JsonObject emailObject = new JsonObject();
        emailObject.addProperty("id", email);
        emailObject.addProperty("isVerified", false);
        JsonObject body = new JsonObject();
        body.addProperty("thirdPartyId", "google");
        body.addProperty("thirdPartyUserId", thirdPartyUserId);
        body.add("email", emailObject);
        JsonObject response = post(process, "/recipe/signinup", body, "thirdparty");
        assertEquals("OK", response.get("status").getAsString());
        return response;
    }

    private static JsonObject createCode(TestingProcessManager.TestingProcess process, String email)
            throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("email", email);
        JsonObject response = post(process, "/recipe/signinup/code", body, "passwordless");
        assertEquals("OK", response.get("status").getAsString());
        return response;
    }

    private static JsonObject consumeCode(TestingProcessManager.TestingProcess process, JsonObject code)
            throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("deviceId", code.get("deviceId").getAsString());
        body.addProperty("preAuthSessionId", code.get("preAuthSessionId").getAsString());
        body.addProperty("userInputCode", code.get("userInputCode").getAsString());
        JsonObject response = post(process, "/recipe/signinup/code/consume", body, "passwordless");
        assertEquals("OK", response.get("status").getAsString());
        return response;
    }

    // Runs the sign-in options request now and returns the sign-in request, so only the latter is measured.
    private static Callable<JsonObject> prepareWebAuthnSignIn(TestingProcessManager.TestingProcess process,
                                                              JsonObject user) throws Exception {
        JsonObject options = io.supertokens.test.webauthn.Utils.signInOptions(process.getProcess());
        Map<String, PublicKeyCredential<AuthenticatorAssertionResponse, AuthenticationExtensionClientOutput>>
                credentials = io.supertokens.test.webauthn.Utils.createPublicKeyRequestOptions(options,
                EmulatorUtil.createClientPlatform(EmulatorUtil.FIDO_U2F_AUTHENTICATOR),
                user.get("webauthnCredentialId").getAsString());
        Map.Entry<String, PublicKeyCredential<AuthenticatorAssertionResponse, AuthenticationExtensionClientOutput>>
                credential = credentials.entrySet().iterator().next();
        return () -> {
            JsonObject response = io.supertokens.test.webauthn.Utils.signIn(process.getProcess(),
                    credential.getKey(), credential.getValue());
            assertEquals("OK", response.get("status").getAsString());
            return response;
        };
    }
}
