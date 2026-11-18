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

package io.supertokens.test.oauth;

import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.inmemorydb.Start;
import io.supertokens.inmemorydb.TransactionCounters.Snapshot;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.auditlog.ActivityLogStorage;
import io.supertokens.pluginInterface.auditlog.AuditLogEvent;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.EmailPasswordConfig;
import io.supertokens.pluginInterface.multitenancy.PasswordlessConfig;
import io.supertokens.pluginInterface.multitenancy.TenantConfig;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.ThirdPartyConfig;
import io.supertokens.pluginInterface.oauth.OAuthStorage;
import io.supertokens.pluginInterface.sqlStorage.SQLStorage;
import io.supertokens.session.Session;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.TransactionCountCapture;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpResponseException;
import io.supertokens.useridmapping.UserIdMapping;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The OAuth token and auth APIs write the activity event of the session the tokens carry in the same transaction as
 * the OAuth session row: one transaction and one commit per request, the event attributed to the SuperTokens user
 * id, and a failed event write fails the request with nothing committed. A session tenant with its own database
 * gets its event on its own storage, after the OAuth row commits.
 */
public class OAuthActivityEventTransactionTest {

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
    public void tokenExchangesWriteTheEventInTheSessionRowsTransaction() throws Exception {
        try (MockOAuthProvider provider = new MockOAuthProvider()) {
            TestingProcessManager.TestingProcess process = startInMemory(provider, false);
            if (process == null) {
                return;
            }
            Main main = process.getProcess();
            TenantIdentifier tenant = process.getAppForTesting();
            Storage storage = StorageLayer.getStorage(tenant, main);
            String superTokensUserId = EmailPassword.signUp(tenant, storage, main, "oauth@example.com", "password")
                    .getSupertokensUserId();
            UserIdMapping.createUserIdMapping(main, tenant.toAppIdentifier(), storage, superTokensUserId,
                    "external-oauth", null, false);
            provider.sessionHandle = Session.createNewSession(tenant, storage, main, "external-oauth",
                    new JsonObject(), new JsonObject()).session.handle;
            MockOAuthProvider.addClient(main, tenant.toAppIdentifier(), storage, true);
            // warm-up: the first exchange creates the signing keys
            MockOAuthProvider.token(main, MockOAuthProvider.authorizationCodeRequest());

            long from = since();
            provider.gid = "gid-auth-code";
            Start.resetUserIdMappingCallCountForTesting();
            TransactionCountCapture.Captured<JsonObject> exchange = TransactionCountCapture.captureRequest(
                    () -> MockOAuthProvider.token(main, MockOAuthProvider.authorizationCodeRequest()));
            assertEquals("OK", exchange.response.get("status").getAsString());
            assertEquals(Snapshot.of(1, 1, 0), exchange.counts);
            assertTrue("the event is attributed through the user id mapping",
                    Start.getUserIdMappingCallCountForTesting() > 0);
            assertEquals(1, oauthSessionRows(main, "gid-auth-code"));
            List<AuditLogEvent> events = events(main, tenant.toAppIdentifier(), "oauth_token_exchange", from);
            assertEquals(1, events.size());
            assertEquals(superTokensUserId, events.get(0).recipeUserId);
            assertEquals(superTokensUserId, events.get(0).primaryOrRecipeUserId);

            // rotating refresh
            TransactionCountCapture.Captured<JsonObject> refresh = TransactionCountCapture.captureRequest(
                    () -> MockOAuthProvider.token(main, MockOAuthProvider.refreshRequest(
                            exchange.response.get("refresh_token").getAsString())));
            assertEquals("OK", refresh.response.get("status").getAsString());
            assertEquals(Snapshot.of(1, 1, 0), refresh.counts);
            assertEquals(2, events(main, tenant.toAppIdentifier(), "oauth_token_exchange", from).size());

            stop(process);
        }
    }

    @Test
    public void failedEventWriteFailsTheExchangeAndWritesNoSessionRow() throws Exception {
        try (MockOAuthProvider provider = new MockOAuthProvider()) {
            TestingProcessManager.TestingProcess process = startInMemory(provider, false);
            if (process == null) {
                return;
            }
            Main main = process.getProcess();
            TenantIdentifier tenant = process.getAppForTesting();
            Storage storage = StorageLayer.getStorage(tenant, main);
            provider.sessionHandle = Session.createNewSession(tenant, storage, main, "some-user", new JsonObject(),
                    new JsonObject()).session.handle;
            MockOAuthProvider.addClient(main, tenant.toAppIdentifier(), storage, true);

            long from = since();
            provider.gid = "gid-failed";
            failActivityWrites(main, "oauth_token_exchange");
            try {
                MockOAuthProvider.token(main, MockOAuthProvider.authorizationCodeRequest());
                fail("expected the exchange to fail");
            } catch (HttpResponseException e) {
                assertEquals(500, e.statusCode);
            }
            assertEquals(0, oauthSessionRows(main, "gid-failed"));
            assertEquals(0, events(main, tenant.toAppIdentifier(), "oauth_token_exchange", from).size());

            allowActivityWrites(main);
            assertEquals("OK", MockOAuthProvider.token(main, MockOAuthProvider.authorizationCodeRequest())
                    .get("status").getAsString());
            assertEquals(1, oauthSessionRows(main, "gid-failed"));
            assertEquals(1, events(main, tenant.toAppIdentifier(), "oauth_token_exchange", from).size());

            stop(process);
        }
    }

    @Test
    public void nonRotatingRefreshWritesTheEventWithTheMappingUpdate() throws Exception {
        try (MockOAuthProvider provider = new MockOAuthProvider()) {
            TestingProcessManager.TestingProcess process = startInMemory(provider, false);
            if (process == null) {
                return;
            }
            Main main = process.getProcess();
            TenantIdentifier tenant = process.getAppForTesting();
            AppIdentifier app = tenant.toAppIdentifier();
            Storage storage = StorageLayer.getStorage(tenant, main);
            provider.sessionHandle = Session.createNewSession(tenant, storage, main, "some-user", new JsonObject(),
                    new JsonObject()).session.handle;
            MockOAuthProvider.addClient(main, app, storage, false);
            provider.gid = "gid-non-rotating";
            String refreshToken = MockOAuthProvider.token(main, MockOAuthProvider.authorizationCodeRequest())
                    .get("refresh_token").getAsString();
            assertNull(((OAuthStorage) storage).getRefreshTokenMapping(app, refreshToken));

            long from = since();
            failActivityWrites(main, "oauth_token_exchange");
            try {
                MockOAuthProvider.token(main, MockOAuthProvider.refreshRequest(refreshToken));
                fail("expected the refresh to fail");
            } catch (HttpResponseException e) {
                assertEquals(500, e.statusCode);
            }
            // the mapping update rolled back with the event
            assertNull(((OAuthStorage) storage).getRefreshTokenMapping(app, refreshToken));
            assertEquals(0, events(main, app, "oauth_token_exchange", from).size());

            allowActivityWrites(main);
            TransactionCountCapture.Captured<JsonObject> refresh = TransactionCountCapture.captureRequest(
                    () -> MockOAuthProvider.token(main, MockOAuthProvider.refreshRequest(refreshToken)));
            assertEquals("OK", refresh.response.get("status").getAsString());
            assertEquals(Snapshot.of(1, 1, 0), refresh.counts);
            assertNotNull(((OAuthStorage) storage).getRefreshTokenMapping(app, refreshToken));
            List<AuditLogEvent> events = events(main, app, "oauth_token_exchange", from);
            assertEquals(1, events.size());
            assertEquals("some-user", events.get(0).primaryOrRecipeUserId);

            stop(process);
        }
    }

    @Test
    public void implicitFlowWritesTheAuthorizeEventInTheSessionRowsTransaction() throws Exception {
        try (MockOAuthProvider provider = new MockOAuthProvider()) {
            TestingProcessManager.TestingProcess process = startInMemory(provider, false);
            if (process == null) {
                return;
            }
            Main main = process.getProcess();
            TenantIdentifier tenant = process.getAppForTesting();
            AppIdentifier app = tenant.toAppIdentifier();
            Storage storage = StorageLayer.getStorage(tenant, main);
            provider.sessionHandle = Session.createNewSession(tenant, storage, main, "some-user", new JsonObject(),
                    new JsonObject()).session.handle;
            MockOAuthProvider.addClient(main, app, storage, true);
            MockOAuthProvider.auth(main, MockOAuthProvider.implicitAuthRequest());

            long from = since();
            provider.gid = "gid-implicit-failed";
            failActivityWrites(main, "oauth_authorize");
            try {
                MockOAuthProvider.auth(main, MockOAuthProvider.implicitAuthRequest());
                fail("expected the auth request to fail");
            } catch (HttpResponseException e) {
                assertEquals(500, e.statusCode);
            }
            assertEquals(0, oauthSessionRows(main, "gid-implicit-failed"));
            assertEquals(0, events(main, app, "oauth_authorize", from).size());
            allowActivityWrites(main);

            provider.gid = "gid-implicit";
            TransactionCountCapture.Captured<JsonObject> auth = TransactionCountCapture.captureRequest(
                    () -> MockOAuthProvider.auth(main, MockOAuthProvider.implicitAuthRequest()));
            assertEquals("OK", auth.response.get("status").getAsString());
            assertTrue(auth.response.get("redirectTo").getAsString().contains("#access_token="));
            assertEquals(Snapshot.of(1, 1, 0), auth.counts);
            assertEquals(1, oauthSessionRows(main, "gid-implicit"));
            List<AuditLogEvent> events = events(main, app, "oauth_authorize", from);
            assertEquals(1, events.size());
            assertEquals("some-user", events.get(0).primaryOrRecipeUserId);

            stop(process);
        }
    }

    @Test
    public void clientCredentialsWritesItsTwoRowsInOneTransactionWithoutAnEvent() throws Exception {
        try (MockOAuthProvider provider = new MockOAuthProvider()) {
            TestingProcessManager.TestingProcess process = startInMemory(provider, false);
            if (process == null) {
                return;
            }
            Main main = process.getProcess();
            TenantIdentifier tenant = process.getAppForTesting();
            AppIdentifier app = tenant.toAppIdentifier();
            Storage storage = StorageLayer.getStorage(tenant, main);
            MockOAuthProvider.addClient(main, app, storage, true);
            provider.issueRefreshToken = false;
            MockOAuthProvider.token(main, MockOAuthProvider.clientCredentialsRequest());

            long from = since();
            provider.gid = "gid-m2m";
            TransactionCountCapture.Captured<JsonObject> m2m = TransactionCountCapture.captureRequest(
                    () -> MockOAuthProvider.token(main, MockOAuthProvider.clientCredentialsRequest()));
            assertEquals("OK", m2m.response.get("status").getAsString());
            assertEquals(Snapshot.of(1, 1, 0), m2m.counts);
            assertEquals(1, oauthSessionRows(main, "gid-m2m"));
            assertEquals(2, ((OAuthStorage) storage).countTotalNumberOfOAuthM2MTokensCreatedSince(app, 0));
            assertEquals(0, events(main, app, "oauth_token_exchange", from).size());

            stop(process);
        }
    }

    // a token whose session was revoked (or has expired) is still exchanged; there is no user to record activity for
    @Test
    public void tokenOfARevokedSessionIsExchangedWithoutAnEvent() throws Exception {
        try (MockOAuthProvider provider = new MockOAuthProvider()) {
            TestingProcessManager.TestingProcess process = startInMemory(provider, false);
            if (process == null) {
                return;
            }
            Main main = process.getProcess();
            TenantIdentifier tenant = process.getAppForTesting();
            AppIdentifier app = tenant.toAppIdentifier();
            Storage storage = StorageLayer.getStorage(tenant, main);
            String handle = Session.createNewSession(tenant, storage, main, "some-user", new JsonObject(),
                    new JsonObject()).session.handle;
            Session.revokeSessionUsingSessionHandles(main, app, storage, new String[]{handle});
            provider.sessionHandle = handle;
            MockOAuthProvider.addClient(main, app, storage, true);
            MockOAuthProvider.token(main, MockOAuthProvider.authorizationCodeRequest());

            long from = since();
            provider.gid = "gid-revoked-session";
            TransactionCountCapture.Captured<JsonObject> exchange = TransactionCountCapture.captureRequest(
                    () -> MockOAuthProvider.token(main, MockOAuthProvider.authorizationCodeRequest()));
            assertEquals("OK", exchange.response.get("status").getAsString());
            assertEquals(Snapshot.of(1, 1, 0), exchange.counts);
            assertEquals(1, oauthSessionRows(main, "gid-revoked-session"));
            assertEquals(0, events(main, app, "oauth_token_exchange", from).size());

            stop(process);
        }
    }

    @Test
    public void exchangeReadsNoUserIdMappingWhenMappingIsDisabled() throws Exception {
        try (MockOAuthProvider provider = new MockOAuthProvider()) {
            TestingProcessManager.TestingProcess process = startInMemory(provider, true);
            if (process == null) {
                return;
            }
            Main main = process.getProcess();
            TenantIdentifier tenant = process.getAppForTesting();
            AppIdentifier app = tenant.toAppIdentifier();
            Storage storage = StorageLayer.getStorage(tenant, main);
            String userId = EmailPassword.signUp(tenant, storage, main, "oauth@example.com", "password")
                    .getSupertokensUserId();
            provider.sessionHandle = Session.createNewSession(tenant, storage, main, userId, new JsonObject(),
                    new JsonObject()).session.handle;
            MockOAuthProvider.addClient(main, app, storage, true);
            MockOAuthProvider.token(main, MockOAuthProvider.authorizationCodeRequest());

            long from = since();
            Start.resetUserIdMappingCallCountForTesting();
            assertEquals("OK", MockOAuthProvider.token(main, MockOAuthProvider.authorizationCodeRequest())
                    .get("status").getAsString());
            assertEquals(0, Start.getUserIdMappingCallCountForTesting());
            List<AuditLogEvent> events = events(main, app, "oauth_token_exchange", from);
            assertEquals(1, events.size());
            assertEquals(userId, events.get(0).primaryOrRecipeUserId);

            stop(process);
        }
    }

    // The OAuth rows are on the app's public-tenant storage; the event goes to the storage of the session's tenant.
    @Test
    public void sessionOfATenantWithItsOwnDatabaseGetsItsEventThere() throws Exception {
        try (MockOAuthProvider provider = new MockOAuthProvider()) {
            String[] args = {"../"};
            TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args, false);
            FeatureFlagTestContent.getInstance(process.getProcess()).setKeyValue(
                    FeatureFlagTestContent.ENABLED_FEATURES,
                    new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY, EE_FEATURES.OAUTH});
            process.startProcess();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
            Main main = process.getProcess();
            if (StorageLayer.getStorage(main).getType() != STORAGE_TYPE.SQL) {
                stop(process);
                return;
            }
            TenantIdentifier appPublic = new TenantIdentifier(null, "oauth-own-db", null);
            TenantIdentifier ownDbTenant = new TenantIdentifier(null, "oauth-own-db", "t1");
            JsonObject ownPool = new JsonObject();
            StorageLayer.getBaseStorage(main).modifyConfigToAddANewUserPoolForTesting(ownPool, 1);
            Multitenancy.addNewOrUpdateAppOrTenant(main, TenantIdentifier.BASE_TENANT,
                    new TenantConfig(appPublic, new EmailPasswordConfig(true), new ThirdPartyConfig(true, null),
                            new PasswordlessConfig(true), null, null, provider.coreConfig()));
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
            AppIdentifier app = appPublic.toAppIdentifier();
            String userId = EmailPassword.signUp(ownDbTenant, ownStorage, main, "own-db@example.com", "password")
                    .getSupertokensUserId();
            provider.sessionHandle = Session.createNewSession(ownDbTenant, ownStorage, main, userId,
                    new JsonObject(), new JsonObject()).session.handle;
            provider.gid = "gid-own-db";
            MockOAuthProvider.addClient(main, app, publicStorage, true);

            long from = since();
            JsonObject response = io.supertokens.test.httpRequest.HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                    "http://localhost:3567/appid-oauth-own-db/recipe/oauth/token",
                    MockOAuthProvider.authorizationCodeRequest(), 5000, 20000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals("OK", response.get("status").getAsString());

            List<AuditLogEvent> onOwnStorage = ((ActivityLogStorage) ownStorage).getActivityLogEntriesForApp(app,
                    Set.of("oauth_token_exchange"), from, System.currentTimeMillis() + 1000, 100);
            assertEquals(1, onOwnStorage.size());
            assertEquals(userId, onOwnStorage.get(0).primaryOrRecipeUserId);
            assertEquals("t1", onOwnStorage.get(0).tenantId);
            assertEquals(0, ((ActivityLogStorage) publicStorage).getActivityLogEntriesForApp(app,
                    Set.of("oauth_token_exchange"), from, System.currentTimeMillis() + 1000, 100).size());
            // the OAuth session row is on the public-tenant storage
            assertTrue(((OAuthStorage) publicStorage).revokeOAuthTokenByGID(app, "gid-own-db"));

            stop(process);
        }
    }

    // The start of a window that holds the events of the requests made from here on, not of those made before.
    private static long since() throws InterruptedException {
        Thread.sleep(5);
        return System.currentTimeMillis() - 1;
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

    private static int oauthSessionRows(Main main, String gid) throws Exception {
        SQLStorage storage = (SQLStorage) StorageLayer.getStorage(main);
        return storage.startTransaction(con -> {
            try (PreparedStatement pst = ((Connection) con.getConnection()).prepareStatement(
                    "SELECT COUNT(*) FROM oauth_sessions WHERE gid = ?")) {
                pst.setString(1, gid);
                try (ResultSet result = pst.executeQuery()) {
                    result.next();
                    return result.getInt(1);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static List<AuditLogEvent> events(Main main, AppIdentifier app, String eventType, long from)
            throws Exception {
        return ((ActivityLogStorage) StorageLayer.getStorage(main)).getActivityLogEntriesForApp(app,
                Set.of(eventType), from, System.currentTimeMillis() + 1000, 100);
    }

    // The shared process with its app using the provider, and disable_user_id_mapping as given.
    private static TestingProcessManager.TestingProcess startInMemory(MockOAuthProvider provider,
                                                                      boolean disableUserIdMapping)
            throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        if (!(StorageLayer.getStorage(process.getProcess()) instanceof Start)) {
            // the failures are injected with an in-memory db trigger, and the counters exist only there
            stop(process);
            return null;
        }
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.OAUTH});
        JsonObject coreConfig = provider.coreConfig();
        if (disableUserIdMapping) {
            coreConfig.addProperty("disable_user_id_mapping", true);
        }
        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(process.getAppForTesting(),
                new EmailPasswordConfig(true), new ThirdPartyConfig(true, null), new PasswordlessConfig(true), null,
                null, coreConfig), false);
        return process;
    }

    private static void stop(TestingProcessManager.TestingProcess process) throws Exception {
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
