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

package io.supertokens.test.session;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.inmemorydb.Start;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.auditlog.ActivityLogStorage;
import io.supertokens.pluginInterface.auditlog.AuditLogEvent;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.EmailPasswordConfig;
import io.supertokens.pluginInterface.multitenancy.PasswordlessConfig;
import io.supertokens.pluginInterface.multitenancy.TenantConfig;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.ThirdPartyConfig;
import io.supertokens.pluginInterface.session.SessionInfo;
import io.supertokens.pluginInterface.session.SessionStorage;
import io.supertokens.pluginInterface.sqlStorage.SQLStorage;
import io.supertokens.session.Session;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.httpRequest.HttpResponseException;
import io.supertokens.useridmapping.UserIdMapping;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

/**
 * Session refresh and sign-out write their activity event ({@code token_refresh} / {@code sign_out}) in the same
 * transaction as the session write: the event is attributed to the SuperTokens user id, revoking by session handle
 * records a {@code sign_out} for each user whose session it revoked, and when the event write fails the request
 * fails and the session write is rolled back.
 */
public class SessionActivityEventTransactionTest {

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
    public void refreshWritesTokenRefreshForTheSuperTokensUserId() throws Exception {
        TestingProcessManager.TestingProcess process = startSql(false);
        if (process == null) {
            return;
        }
        Main main = process.getProcess();
        AuthRecipeUserInfo user = EmailPassword.signUp(main, "refresh@example.com", "password");
        UserIdMapping.createUserIdMapping(main, user.getSupertokensUserId(), "external-refresh", null, false);
        JsonObject session = createSession(process, "external-refresh");

        long from = System.currentTimeMillis() - 1;
        JsonObject refreshed = refresh(process, refreshToken(session));
        assertEquals("OK", refreshed.get("status").getAsString());

        List<AuditLogEvent> events = events(main, "token_refresh", from);
        assertEquals(1, events.size());
        assertEquals(user.getSupertokensUserId(), events.get(0).recipeUserId);
        assertEquals(user.getSupertokensUserId(), events.get(0).primaryOrRecipeUserId);

        stop(process);
    }

    @Test
    public void failedTokenRefreshWriteFailsTheRefreshAndKeepsTheSession() throws Exception {
        TestingProcessManager.TestingProcess process = startInMemory();
        if (process == null) {
            return;
        }
        Main main = process.getProcess();
        String userId = EmailPassword.signUp(main, "refresh-fail@example.com", "password").getSupertokensUserId();
        JsonObject session = createSession(process, userId);
        String handle = session.getAsJsonObject("session").get("handle").getAsString();
        SessionInfo before = sessionRow(main, handle);

        long from = System.currentTimeMillis() - 1;
        failActivityWrites(main, "token_refresh");
        try {
            refresh(process, refreshToken(session));
            fail("expected the refresh to fail");
        } catch (HttpResponseException e) {
            assertEquals(500, e.statusCode);
        }
        SessionInfo after = sessionRow(main, handle);
        assertEquals(before.refreshTokenHash2, after.refreshTokenHash2);
        assertNull(after.prevRefreshTokenHash2);
        assertEquals(0, events(main, "token_refresh", from).size());

        allowActivityWrites(main);
        assertEquals("OK", refresh(process, refreshToken(session)).get("status").getAsString());
        assertEquals(1, events(main, "token_refresh", from).size());

        stop(process);
    }

    @Test
    public void revokingByHandleWritesOneSignOutPerRevokedUser() throws Exception {
        TestingProcessManager.TestingProcess process = startSql(false);
        if (process == null) {
            return;
        }
        Main main = process.getProcess();
        AuthRecipeUserInfo mapped = EmailPassword.signUp(main, "mapped@example.com", "password");
        UserIdMapping.createUserIdMapping(main, mapped.getSupertokensUserId(), "external-mapped", null, false);
        String other = EmailPassword.signUp(main, "other@example.com", "password").getSupertokensUserId();
        String mapped1 = handle(createSession(process, "external-mapped"));
        String mapped2 = handle(createSession(process, "external-mapped"));
        String other1 = handle(createSession(process, other));
        String untouched = handle(createSession(process, other));

        long from = System.currentTimeMillis() - 1;
        JsonObject removed = removeHandles(process, mapped1, mapped2, other1, "unknown-handle");
        assertEquals(Set.of(mapped1, mapped2, other1), handlesOf(removed));

        List<AuditLogEvent> signOuts = events(main, "sign_out", from);
        assertEquals(Set.of(mapped.getSupertokensUserId(), other),
                signOuts.stream().map(e -> e.primaryOrRecipeUserId).collect(Collectors.toSet()));
        assertEquals(2, signOuts.size());
        for (AuditLogEvent event : signOuts) {
            assertEquals(event.primaryOrRecipeUserId, event.recipeUserId);
        }
        assertNotNull(sessionRow(main, untouched));

        // nothing revoked, nothing recorded
        from = System.currentTimeMillis() - 1;
        assertEquals(0, removeHandles(process, mapped1, "unknown-handle").getAsJsonArray("sessionHandlesRevoked")
                .size());
        assertEquals(0, events(main, "sign_out", from).size());

        stop(process);
    }

    @Test
    public void failedSignOutWriteFailsTheRevocationAndKeepsTheSessions() throws Exception {
        TestingProcessManager.TestingProcess process = startInMemory();
        if (process == null) {
            return;
        }
        Main main = process.getProcess();
        String userId = EmailPassword.signUp(main, "remove-fail@example.com", "password").getSupertokensUserId();
        String byHandle = handle(createSession(process, userId));
        String byUser = handle(createSession(process, userId));

        long from = System.currentTimeMillis() - 1;
        failActivityWrites(main, "sign_out");
        try {
            removeHandles(process, byHandle);
            fail("expected the revocation by handle to fail");
        } catch (HttpResponseException e) {
            assertEquals(500, e.statusCode);
        }
        try {
            removeUser(process, userId);
            fail("expected the revocation for the user to fail");
        } catch (HttpResponseException e) {
            assertEquals(500, e.statusCode);
        }
        assertNotNull(sessionRow(main, byHandle));
        assertNotNull(sessionRow(main, byUser));
        assertEquals(0, events(main, "sign_out", from).size());

        allowActivityWrites(main);
        assertEquals(Set.of(byHandle, byUser), handlesOf(removeUser(process, userId)));
        List<AuditLogEvent> signOuts = events(main, "sign_out", from);
        assertEquals(1, signOuts.size());
        assertEquals(userId, signOuts.get(0).primaryOrRecipeUserId);

        stop(process);
    }

    // Removing by userId records a sign_out when it revoked a session or the id is a user of the app. An id that is
    // neither writes nothing, so an arbitrary or over-long id gets the documented 200 instead of failing on the
    // activity log's user_id column.
    @Test
    public void removingByAnUnknownUserIdWithNoSessionsWritesNoSignOut() throws Exception {
        TestingProcessManager.TestingProcess process = startSql(false);
        if (process == null) {
            return;
        }
        Main main = process.getProcess();

        long from = System.currentTimeMillis() - 1;
        assertEquals(Set.of(), handlesOf(removeUser(process, "not-a-user")));
        assertEquals(Set.of(), handlesOf(removeUser(process, "x".repeat(200))));
        assertEquals(0, events(main, "sign_out", from).size());

        // a user of the app without sessions still gets its sign_out, as before, also through its external id
        String known = EmailPassword.signUp(main, "known@example.com", "password").getSupertokensUserId();
        AuthRecipeUserInfo mapped = EmailPassword.signUp(main, "mapped-known@example.com", "password");
        UserIdMapping.createUserIdMapping(main, mapped.getSupertokensUserId(), "external-known", null, false);
        from = System.currentTimeMillis() - 1;
        assertEquals(Set.of(), handlesOf(removeUser(process, known)));
        assertEquals(Set.of(), handlesOf(removeUser(process, "external-known")));
        assertEquals(Set.of(known, mapped.getSupertokensUserId()), events(main, "sign_out", from).stream()
                .map(e -> e.primaryOrRecipeUserId).collect(Collectors.toSet()));

        // a revoked session of an id that is not a user (sessions can be created for any id) still records one
        String sessionOnly = handle(createSession(process, "session-only-id"));
        from = System.currentTimeMillis() - 1;
        assertEquals(Set.of(sessionOnly), handlesOf(removeUser(process, "session-only-id")));
        List<AuditLogEvent> signOuts = events(main, "sign_out", from);
        assertEquals(1, signOuts.size());
        assertEquals("session-only-id", signOuts.get(0).primaryOrRecipeUserId);

        stop(process);
    }

    // A user is only associated with tenants on their own user pool. Should a tenant on another database still hold
    // sessions stored under the user's id, revoking across all tenants revokes them there too, in a separate write.
    @Test
    public void revokingAcrossTenantsAlsoRevokesSessionsOnATenantOfAnotherDatabase() throws Exception {
        TestingProcessManager.TestingProcess process = startSql(true);
        if (process == null) {
            return;
        }
        Main main = process.getProcess();
        TenantIdentifier appPublic = new TenantIdentifier(null, "signout-own-db", null);
        TenantIdentifier ownDbTenant = new TenantIdentifier(null, "signout-own-db", "t1");
        JsonObject ownPool = new JsonObject();
        StorageLayer.getBaseStorage(main).modifyConfigToAddANewUserPoolForTesting(ownPool, 1);
        Multitenancy.addNewOrUpdateAppOrTenant(main, TenantIdentifier.BASE_TENANT,
                new TenantConfig(appPublic, new EmailPasswordConfig(true), new ThirdPartyConfig(true, null),
                        new PasswordlessConfig(true), null, null, new JsonObject()));
        Multitenancy.addNewOrUpdateAppOrTenant(main, appPublic,
                new TenantConfig(ownDbTenant, new EmailPasswordConfig(true), new ThirdPartyConfig(true, null),
                        new PasswordlessConfig(true), null, null, ownPool));
        Storage publicStorage = StorageLayer.getStorage(appPublic, main);
        Storage ownStorage = StorageLayer.getStorage(ownDbTenant, main);
        if (publicStorage.getUserPoolId().equals(ownStorage.getUserPoolId())) {
            // one user pool for every tenant (the in-memory db): the scenario isn't reachable
            stop(process);
            return;
        }

        String userId = EmailPassword.signUp(appPublic, publicStorage, main, "own-db@example.com", "password")
                .getSupertokensUserId();
        String onUserStorage = Session.createNewSession(appPublic, publicStorage, main, userId, new JsonObject(),
                new JsonObject()).session.handle;
        // sessions don't need a user, so one can be stored under the user's id on the other database
        String onOtherStorage = Session.createNewSession(ownDbTenant, ownStorage, main, userId, new JsonObject(),
                new JsonObject()).session.handle;

        long from = System.currentTimeMillis() - 1;
        JsonObject remove = new JsonObject();
        remove.addProperty("userId", userId);
        remove.addProperty("revokeAcrossAllTenants", true);
        JsonObject removed = HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                BASE + "/appid-signout-own-db/recipe/session/remove", remove, 1000, 1000, null,
                Utils.getCdiVersionStringLatestForTests(), "session");
        assertEquals("OK", removed.get("status").getAsString());
        assertEquals(Set.of(onUserStorage, onOtherStorage), handlesOf(removed));

        List<AuditLogEvent> signOuts = ((ActivityLogStorage) publicStorage).getActivityLogEntriesForApp(
                appPublic.toAppIdentifier(), Set.of("sign_out"), from, System.currentTimeMillis() + 1000, 100);
        assertEquals(1, signOuts.size());
        assertEquals(userId, signOuts.get(0).primaryOrRecipeUserId);

        stop(process);
    }

    // Makes every activity-log insert of eventType fail (the in-memory db only).
    private static void failActivityWrites(Main main, String eventType) throws Exception {
        execute(main, "CREATE TRIGGER fail_activity_writes BEFORE INSERT ON activity_log WHEN NEW.event_type = '"
                + eventType + "' BEGIN SELECT RAISE(ABORT, 'injected activity-log write failure'); END");
    }

    private static void allowActivityWrites(Main main) throws Exception {
        execute(main, "DROP TRIGGER fail_activity_writes");
    }

    private static void execute(Main main, String statement) throws Exception {
        SQLStorage storage = (SQLStorage) StorageLayer.getStorage(main);
        storage.startTransaction(con -> {
            try (PreparedStatement pst = ((Connection) con.getConnection()).prepareStatement(statement)) {
                pst.executeUpdate();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            storage.commitTransaction(con);
            return null;
        });
    }

    private static List<AuditLogEvent> events(Main main, String eventType, long from) throws Exception {
        return ((ActivityLogStorage) StorageLayer.getStorage(main)).getActivityLogEntriesForApp(
                new AppIdentifier(null, null), Set.of(eventType), from, System.currentTimeMillis() + 1000, 100);
    }

    private static SessionInfo sessionRow(Main main, String handle) throws Exception {
        return ((SessionStorage) StorageLayer.getStorage(main)).getSession(TenantIdentifier.BASE_TENANT, handle);
    }

    private static TestingProcessManager.TestingProcess startInMemory() throws Exception {
        TestingProcessManager.TestingProcess process = startSql(false);
        if (process != null && !(StorageLayer.getStorage(process.getProcess()) instanceof Start)) {
            // the failure is injected with an in-memory db trigger
            stop(process);
            return null;
        }
        return process;
    }

    private static TestingProcessManager.TestingProcess startSql(boolean multitenancy) throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args, false);
        if (multitenancy) {
            FeatureFlagTestContent.getInstance(process.getProcess())
                    .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        }
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            stop(process);
            return null;
        }
        return process;
    }

    private static void stop(TestingProcessManager.TestingProcess process) throws Exception {
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    private static JsonObject createSession(TestingProcessManager.TestingProcess process, String userId)
            throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("userId", userId);
        body.add("userDataInJWT", new JsonObject());
        body.add("userDataInDatabase", new JsonObject());
        body.addProperty("enableAntiCsrf", false);
        JsonObject response = post(process, "/recipe/session", body);
        assertEquals("OK", response.get("status").getAsString());
        return response;
    }

    private static String handle(JsonObject session) {
        return session.getAsJsonObject("session").get("handle").getAsString();
    }

    private static String refreshToken(JsonObject session) {
        return session.getAsJsonObject("refreshToken").get("token").getAsString();
    }

    private static JsonObject refresh(TestingProcessManager.TestingProcess process, String refreshToken)
            throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("refreshToken", refreshToken);
        body.addProperty("enableAntiCsrf", false);
        body.addProperty("useDynamicSigningKey", true);
        return post(process, "/recipe/session/refresh", body);
    }

    private static JsonObject removeHandles(TestingProcessManager.TestingProcess process, String... handles)
            throws Exception {
        JsonObject body = new JsonObject();
        JsonArray array = new JsonArray();
        for (String handle : handles) {
            array.add(handle);
        }
        body.add("sessionHandles", array);
        JsonObject response = post(process, "/recipe/session/remove", body);
        assertEquals("OK", response.get("status").getAsString());
        return response;
    }

    private static JsonObject removeUser(TestingProcessManager.TestingProcess process, String userId)
            throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("userId", userId);
        JsonObject response = post(process, "/recipe/session/remove", body);
        assertEquals("OK", response.get("status").getAsString());
        return response;
    }

    private static Set<String> handlesOf(JsonObject removed) {
        Set<String> handles = new HashSet<>();
        removed.getAsJsonArray("sessionHandlesRevoked").forEach(h -> handles.add(h.getAsString()));
        return handles;
    }

    private static JsonObject post(TestingProcessManager.TestingProcess process, String path, JsonObject body)
            throws Exception {
        return HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "", BASE + path, body, 1000, 1000,
                null, Utils.getCdiVersionStringLatestForTests(), "session");
    }
}
