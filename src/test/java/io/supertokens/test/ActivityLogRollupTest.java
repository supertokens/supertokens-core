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

import io.supertokens.ProcessState;
import io.supertokens.inmemorydb.Start;
import io.supertokens.pluginInterface.auditlog.AuditLogEvent;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.storageLayer.StorageLayer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

/**
 * In-memory (SQLite) parity for the fold+reconcile rollup that derives {@code user_last_active} from the
 * {@code activity_log}, plus the transactional audit insert. Mirrors the PostgreSQL plugin's
 * ActivityLogRollupTest: fold idempotency, monotonicity (a fold never lowers a stored timestamp), the
 * reconcile that drops a recipe user linked away within the window, and the atomicity of a transactional
 * audit write with its surrounding mutation.
 */
public class ActivityLogRollupTest {

    private static final String APP_ID = "public";
    private static final String ACTIVITY_LOG = "activity_log";
    private static final String USER_LAST_ACTIVE = "user_last_active";

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

    /**
     * Folding the same window twice yields the same projection — the fold is idempotent, so overlapping
     * windows and repeated passes are harmless.
     */
    @Test
    public void foldIsIdempotentAcrossRepeatedPasses() throws Exception {
        TestingProcessManager.TestingProcess process = startInMemoryProcess();
        Start storage = (Start) StorageLayer.getStorage(process.getProcess());

        long base = System.currentTimeMillis();
        String userA = "rollup-idempotent-A";
        String userB = "rollup-idempotent-B";

        insertUserLastActiveEvent(storage, userA, base + 1000);
        insertUserLastActiveEvent(storage, userA, base + 2000); // A's most recent
        insertUserLastActiveEvent(storage, userB, base + 3000);

        runRollup(storage, base - 10000);
        assertEquals(Long.valueOf(base + 2000), getLastActive(storage, userA));
        assertEquals(Long.valueOf(base + 3000), getLastActive(storage, userB));

        // Same window again — the projection must be unchanged.
        runRollup(storage, base - 10000);
        assertEquals(Long.valueOf(base + 2000), getLastActive(storage, userA));
        assertEquals(Long.valueOf(base + 3000), getLastActive(storage, userB));

        stopProcess(process);
    }

    /**
     * A fold must never lower an already-stored last-active timestamp when the window's most-recent
     * activity for that user is older than what is stored (e.g. a value written directly by the Phase-1
     * direct writer, or by an earlier wider fold). {@code MAX(stored, folded)} keeps it monotonic. The
     * window's MAX alone would not catch this: teeth require the stored value to exceed everything the
     * fold sees.
     */
    @Test
    public void foldNeverLowersAStoredTimestamp() throws Exception {
        TestingProcessManager.TestingProcess process = startInMemoryProcess();
        Start storage = (Start) StorageLayer.getStorage(process.getProcess());

        long base = System.currentTimeMillis();
        String user = "rollup-monotonic";

        // Projection already holds a recent value (as if written directly, ahead of the log).
        seedUserLastActive(storage, user, base + 9000);

        // The only activity the fold can see for this user is older than the stored value.
        insertUserLastActiveEvent(storage, user, base + 1000);
        runRollup(storage, base - 10000);

        // MAX(stored, folded) keeps the higher stored timestamp.
        assertEquals(Long.valueOf(base + 9000), getLastActive(storage, user));

        stopProcess(process);
    }

    /**
     * A recipe user active just before being linked to a primary: after fold+reconcile in one pass, only
     * the primary user's projection row remains (the linked-away recipe user's row is reconciled away).
     */
    @Test
    public void reconcileRemovesRecipeUserLinkedAwayInWindow() throws Exception {
        TestingProcessManager.TestingProcess process = startInMemoryProcess();
        Start storage = (Start) StorageLayer.getStorage(process.getProcess());

        long base = System.currentTimeMillis();
        String recipeUser = "rollup-reconcile-R";
        String primaryUser = "rollup-reconcile-P";

        // R was active on its own, then P was active, then R got linked into P — all within the window.
        insertUserLastActiveEvent(storage, recipeUser, base + 1000);
        insertUserLastActiveEvent(storage, primaryUser, base + 2000);
        insertAccountLinkingEvent(storage, recipeUser, primaryUser, base + 3000);

        runRollup(storage, base - 10000);

        // The recipe user's row is gone (folded then reconciled away); the primary user's row remains.
        assertNull(getLastActive(storage, recipeUser));
        assertEquals(Long.valueOf(base + 2000), getLastActive(storage, primaryUser));

        stopProcess(process);
    }

    /**
     * The fold must never resurrect a projection row for an app that no longer exists. {@code activity_log}
     * rows are intentionally retained after an app is deleted (no app_id cascade), while {@code
     * user_last_active} cascades on app delete; folding a since-deleted app's activity would re-insert a row
     * that violates the {@code user_last_active -> apps} foreign key (this is what surfaced on PostgreSQL
     * once the test DB stopped being reset). The {@code EXISTS (apps)} guard confines the fold to still-
     * existing apps: a user_last_active event whose app_id is absent from {@code apps} is skipped, while a
     * concurrent event for an existing app in the same window still folds normally.
     */
    @Test
    public void foldSkipsActivityForAppMissingFromApps() throws Exception {
        TestingProcessManager.TestingProcess process = startInMemoryProcess();
        Start storage = (Start) StorageLayer.getStorage(process.getProcess());

        long base = System.currentTimeMillis();
        String existingAppUser = "rollup-app-guard-existing";
        String deletedAppUser = "rollup-app-guard-deleted";
        // "public" is present in the apps table; this id never is, standing in for a deleted app whose
        // activity_log rows still linger.
        String missingAppId = "app-that-was-deleted";

        insertUserLastActiveEventForApp(storage, APP_ID, existingAppUser, base + 1000);
        insertUserLastActiveEventForApp(storage, missingAppId, deletedAppUser, base + 2000);

        runRollup(storage, base - 10000);

        // The existing app's user is folded; the missing app's user is skipped (no resurrected row).
        assertEquals(Long.valueOf(base + 1000), getLastActiveForApp(storage, APP_ID, existingAppUser));
        assertNull(getLastActiveForApp(storage, missingAppId, deletedAppUser));

        stopProcess(process);
    }

    /**
     * A transactional audit write plus a mutation on one connection, with a failure injected after the
     * write, must roll back together — neither the audit row nor the mutation survives.
     */
    @Test
    public void auditWriteAndMutationRollBackTogetherOnFailure() throws Exception {
        TestingProcessManager.TestingProcess process = startInMemoryProcess();
        Start storage = (Start) StorageLayer.getStorage(process.getProcess());

        long base = System.currentTimeMillis();
        String user = "rollup-atomic";

        try {
            storage.startTransaction(con -> {
                Connection sqlCon = (Connection) con.getConnection();
                // A mutation on the projection table...
                String upsert = "INSERT INTO " + USER_LAST_ACTIVE + " (app_id, user_id, last_active_time)"
                        + " VALUES (?, ?, ?)";
                try (PreparedStatement pst = sqlCon.prepareStatement(upsert)) {
                    pst.setString(1, APP_ID);
                    pst.setString(2, user);
                    pst.setLong(3, base);
                    pst.executeUpdate();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                // ...and its audit entry on the SAME connection.
                storage.createActivityLogEntry_Transaction(con, new TenantIdentifier(null, null, null),
                        new AuditLogEvent(APP_ID, "public", user, user, "user_last_active", "success",
                                null, null, base, null));
                // Injected failure after both writes: startTransaction rolls the connection back.
                throw new RuntimeException("injected failure after audit write");
            });
            fail("expected the injected failure to propagate");
        } catch (Exception e) {
            // expected — the transaction rolled back
        }

        // Both writes are gone.
        assertNull(getLastActive(storage, user));
        assertEquals(0, countActivityLogEventsForUser(storage, user));

        stopProcess(process);
    }

    // ---- helpers ----

    private TestingProcessManager.TestingProcess startInMemoryProcess() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args, false);
        process.getProcess().setForceInMemoryDB();
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        return process;
    }

    private void stopProcess(TestingProcessManager.TestingProcess process) throws Exception {
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    private void runRollup(Start storage, long windowStartMillis) throws Exception {
        storage.startTransaction(con -> {
            storage.rollupLastActiveFromActivityLog_Transaction(con, windowStartMillis);
            storage.commitTransaction(con);
            return null;
        });
    }

    private Long getLastActive(Start storage, String userId) throws Exception {
        String query = "SELECT last_active_time FROM " + USER_LAST_ACTIVE + " WHERE app_id = ? AND user_id = ?";
        return storage.startTransaction(con -> {
            Connection sqlCon = (Connection) con.getConnection();
            try (PreparedStatement pst = sqlCon.prepareStatement(query)) {
                pst.setString(1, APP_ID);
                pst.setString(2, userId);
                try (ResultSet rs = pst.executeQuery()) {
                    return rs.next() ? Long.valueOf(rs.getLong(1)) : null;
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void seedUserLastActive(Start storage, String userId, long lastActiveTime) throws Exception {
        String query = "INSERT INTO " + USER_LAST_ACTIVE + " (app_id, user_id, last_active_time) VALUES (?, ?, ?)";
        storage.startTransaction(con -> {
            Connection sqlCon = (Connection) con.getConnection();
            try (PreparedStatement pst = sqlCon.prepareStatement(query)) {
                pst.setString(1, APP_ID);
                pst.setString(2, userId);
                pst.setLong(3, lastActiveTime);
                pst.executeUpdate();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            storage.commitTransaction(con);
            return null;
        });
    }

    private Long getLastActiveForApp(Start storage, String appId, String userId) throws Exception {
        String query = "SELECT last_active_time FROM " + USER_LAST_ACTIVE + " WHERE app_id = ? AND user_id = ?";
        return storage.startTransaction(con -> {
            Connection sqlCon = (Connection) con.getConnection();
            try (PreparedStatement pst = sqlCon.prepareStatement(query)) {
                pst.setString(1, appId);
                pst.setString(2, userId);
                try (ResultSet rs = pst.executeQuery()) {
                    return rs.next() ? Long.valueOf(rs.getLong(1)) : null;
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void insertUserLastActiveEvent(Start storage, String userId, long createdAt) throws Exception {
        // For a user_last_active event the user is its own primary_or_recipe_user_id.
        insertActivityLogRow(storage, APP_ID, userId, userId, "user_last_active", createdAt);
    }

    private void insertUserLastActiveEventForApp(Start storage, String appId, String userId, long createdAt)
            throws Exception {
        insertActivityLogRow(storage, appId, userId, userId, "user_last_active", createdAt);
    }

    private void insertAccountLinkingEvent(Start storage, String recipeUserId, String primaryUserId, long createdAt)
            throws Exception {
        insertActivityLogRow(storage, APP_ID, recipeUserId, primaryUserId, "account_linking", createdAt);
    }

    private void insertActivityLogRow(Start storage, String appId, String recipeUserId, String primaryOrRecipeUserId,
                                      String eventType, long createdAt) throws Exception {
        String query = "INSERT INTO " + ACTIVITY_LOG
                + " (app_id, tenant_id, recipe_user_id, primary_or_recipe_user_id, event_type, status, created_at)"
                + " VALUES (?, 'public', ?, ?, ?, 'success', ?)";
        storage.startTransaction(con -> {
            Connection sqlCon = (Connection) con.getConnection();
            try (PreparedStatement pst = sqlCon.prepareStatement(query)) {
                pst.setString(1, appId);
                pst.setString(2, recipeUserId);
                pst.setString(3, primaryOrRecipeUserId);
                pst.setString(4, eventType);
                pst.setLong(5, createdAt);
                pst.executeUpdate();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            storage.commitTransaction(con);
            return null;
        });
    }

    private int countActivityLogEventsForUser(Start storage, String userId) throws Exception {
        String query = "SELECT COUNT(*) FROM " + ACTIVITY_LOG + " WHERE primary_or_recipe_user_id = ?";
        return storage.startTransaction(con -> {
            Connection sqlCon = (Connection) con.getConnection();
            try (PreparedStatement pst = sqlCon.prepareStatement(query)) {
                pst.setString(1, userId);
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
