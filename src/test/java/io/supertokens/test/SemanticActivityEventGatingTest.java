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

import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.passwordless.Passwordless;
import io.supertokens.passwordless.Passwordless.CreateCodeResponse;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.sqlStorage.SQLStorage;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.utils.SemVer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * API-level gating of the {@code sign_in} activity event. The created-new-user branch of an auth endpoint (a
 * sign-up) writes no {@code sign_in} row — the in-transaction {@code user_creation} lifecycle event records
 * that activity for the fold — while a returning user's sign-in writes exactly one {@code sign_in} row. This
 * pins the {@code !createdNewUser} gate in thirdparty {@code SignInUpAPI} and passwordless
 * {@code ConsumeCodeAPI}: it is exactly the split this change introduces at the API layer, so a regression
 * (a sign-up double-recording, or a returning sign-in going unrecorded) would break here.
 */
public class SemanticActivityEventGatingTest {

    @Rule
    public TestRule watchman = Utils.getOnFailure();

    @Rule
    public TestRule retryFlaky = Utils.retryFlakyTest();

    @AfterClass
    public static void afterTesting() {
        Utils.afterTesting();
    }

    @Before
    public void beforeEach() {
        Utils.reset();
    }

    @Test
    public void thirdPartyCreatedNewUserWritesNoSignInReturningUserWritesOne() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            process.kill();
            return;
        }
        SQLStorage storage = (SQLStorage) StorageLayer.getStorage(process.getProcess());

        // First sign-in-up creates the user: createdNewUser == true, and no sign_in activity row is written
        // (the user_creation lifecycle event covers the fold).
        JsonObject resp = thirdPartySignInUp(process, "google", "google-user", "test@example.com");
        assertEquals("OK", resp.get("status").getAsString());
        assertTrue(resp.get("createdNewUser").getAsBoolean());
        assertEquals(0, countEvents(storage, "sign_in"));

        // The same third-party user signs in again: createdNewUser == false, exactly one sign_in row.
        resp = thirdPartySignInUp(process, "google", "google-user", "test@example.com");
        assertEquals("OK", resp.get("status").getAsString());
        assertFalse(resp.get("createdNewUser").getAsBoolean());
        assertEquals(1, countEvents(storage, "sign_in"));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void passwordlessCreatedNewUserWritesNoSignInReturningUserWritesOne() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            process.kill();
            return;
        }
        SQLStorage storage = (SQLStorage) StorageLayer.getStorage(process.getProcess());
        String email = "test@example.com";

        // First consume creates the user: createdNewUser == true, no sign_in activity row.
        JsonObject resp = passwordlessConsume(process, email);
        assertEquals("OK", resp.get("status").getAsString());
        assertTrue(resp.get("createdNewUser").getAsBoolean());
        assertEquals(0, countEvents(storage, "sign_in"));

        // The returning user consumes a fresh code: createdNewUser == false, exactly one sign_in row.
        resp = passwordlessConsume(process, email);
        assertEquals("OK", resp.get("status").getAsString());
        assertFalse(resp.get("createdNewUser").getAsBoolean());
        assertEquals(1, countEvents(storage, "sign_in"));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    private JsonObject thirdPartySignInUp(TestingProcessManager.TestingProcess process, String thirdPartyId,
            String thirdPartyUserId, String email) throws Exception {
        JsonObject emailObject = new JsonObject();
        emailObject.addProperty("id", email);
        emailObject.addProperty("isVerified", false);
        JsonObject body = new JsonObject();
        body.addProperty("thirdPartyId", thirdPartyId);
        body.addProperty("thirdPartyUserId", thirdPartyUserId);
        body.add("email", emailObject);
        return HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/signinup", body, 1000, 1000, null, SemVer.v4_0.get(), "thirdparty");
    }

    private JsonObject passwordlessConsume(TestingProcessManager.TestingProcess process, String email)
            throws Exception {
        CreateCodeResponse createResp = Passwordless.createCode(process.getProcess(), email, null, null, null);
        JsonObject body = new JsonObject();
        body.addProperty("preAuthSessionId", createResp.deviceIdHash);
        body.addProperty("linkCode", createResp.linkCode);
        return HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/signinup/code/consume", body, 1000, 1000, null,
                SemVer.v4_0.get(), "passwordless");
    }

    private int countEvents(SQLStorage storage, String eventType) throws Exception {
        return storage.startTransaction(con -> {
            Connection sqlCon = (Connection) con.getConnection();
            try (PreparedStatement pst = sqlCon.prepareStatement(
                    "SELECT COUNT(*) FROM activity_log WHERE event_type = ?")) {
                pst.setString(1, eventType);
                try (ResultSet rs = pst.executeQuery()) {
                    rs.next();
                    return rs.getInt(1);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
}
