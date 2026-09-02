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

import io.supertokens.ActiveUsers;
import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.inmemorydb.Start;
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

/**
 * Phase-1 parity: with both writers active (the direct {@code user_last_active} upsert and the async fold from
 * {@code activity_log}), the rollup-derived projection must produce the <em>same</em>
 * {@link ActiveUsers#countUsersActiveSince read-path answer} as the direct write, before any cutover removes
 * the direct write ({@code PLAN-011} unit 8).
 *
 * <p>Each test builds the <em>direct-write state</em> and its mirrored activity log with controlled timestamps,
 * records {@code countUsersActiveSince}, then truncates {@code user_last_active} and folds the log over an
 * explicit window — reproducing "the direct table, then the same table rebuilt purely from the log" — and
 * asserts the count (and the per-user projection) is identical.
 *
 * <p><b>Controlled timestamps, never wall-clock.</b> Until cutover there are two independently-computed
 * "now"s (the direct writer's and the fold's window), so a wall-clock parity test flakes at the exact window
 * boundary. Every timestamp here is a fixed offset from a frozen {@link #BASE} anchor and the fold window is
 * passed explicitly, so the comparison is deterministic.
 *
 * <p>The seeding reproduces the direct-write projection alongside a mirrored activity log at the same
 * timestamp: an activity event (here {@code sign_in}, a folded type) at time {@code t} — which is exactly what
 * the fold reads. Linking additionally deletes the recipe user's direct row
 * ({@code ActiveUsers.updateLastActiveAfterLinking}) and emits an {@code account_linking} event
 * ({@code AuthRecipe.linkAccounts}, unit 5) that the reconcile matches on.
 *
 * <p>Runs on the in-memory (SQLite) store, like {@link ActivityLogRollupTest} and {@link RollupUserLastActiveTest}.
 */
public class ActivityLogRollupParityTest {

    private static final String APP_ID = "public";
    private static final String ACTIVITY_LOG = "activity_log";
    private static final String USER_LAST_ACTIVE = "user_last_active";

    // Frozen anchor — every timestamp below is a fixed offset from this, so nothing depends on wall-clock.
    private static final long BASE = 1_700_000_000_000L;
    // A window start comfortably before every seeded event: the fold sees the whole seeded history.
    private static final long WHOLE_HISTORY_WINDOW = BASE - 10_000;

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
     * Baseline parity across a {@code since} boundary: users active before the boundary are excluded and
     * users active after it are counted, and a user with several activity events collapses to its most recent
     * timestamp — the same answer whether the projection came from the direct write or from a fold over a
     * freshly-truncated {@code user_last_active}.
     */
    @Test
    public void countAndProjectionMatchAcrossTruncateAndRollup() throws Exception {
        TestingProcessManager.TestingProcess process = startInMemoryProcess();
        Main main = process.getProcess();
        Start storage = (Start) StorageLayer.getStorage(main);

        long since = BASE;

        // uOld is active before the boundary; uA/uB/uC after it. uA is active twice — the projection must
        // collapse to its most recent activity (MAX), just as the direct upsert overwrites to the latest.
        recordActivity(storage, "parity-old", BASE - 5_000);
        recordActivity(storage, "parity-A", BASE + 500);
        recordActivity(storage, "parity-A", BASE + 1_000);
        recordActivity(storage, "parity-B", BASE + 2_000);
        recordActivity(storage, "parity-C", BASE + 3_000);

        // Direct-write answer: three users at/after the boundary (uOld excluded).
        int directCountSince = ActiveUsers.countUsersActiveSince(main, since);
        int directCountAll = ActiveUsers.countUsersActiveSince(main, WHOLE_HISTORY_WINDOW);
        assertEquals(3, directCountSince);
        assertEquals(4, directCountAll);

        // Rebuild the projection purely from the log.
        truncateUserLastActive(storage);
        assertEquals(0, ActiveUsers.countUsersActiveSince(main, WHOLE_HISTORY_WINDOW)); // truncate took effect
        runRollup(storage, WHOLE_HISTORY_WINDOW);

        // Parity: identical counts on both sides of the boundary.
        assertEquals(directCountSince, ActiveUsers.countUsersActiveSince(main, since));
        assertEquals(directCountAll, ActiveUsers.countUsersActiveSince(main, WHOLE_HISTORY_WINDOW));

        // Parity down to the per-user projected timestamp, including uA's most-recent collapse.
        assertEquals(Long.valueOf(BASE - 5_000), getLastActive(storage, "parity-old"));
        assertEquals(Long.valueOf(BASE + 1_000), getLastActive(storage, "parity-A"));
        assertEquals(Long.valueOf(BASE + 2_000), getLastActive(storage, "parity-B"));
        assertEquals(Long.valueOf(BASE + 3_000), getLastActive(storage, "parity-C"));

        stopProcess(process);
    }

    /**
     * Linking parity: a recipe user active just before being linked into a primary is not double-counted after
     * one rollup pass. The direct-write state already collapsed the two into the primary (the recipe user's
     * direct row was deleted on link); the fold+reconcile reproduces exactly that — the recipe user's folded
     * row is reconciled away in the same pass — so both count the pair as one active user.
     */
    @Test
    public void linkedRecipeUserIsNotDoubleCountedAfterRollup() throws Exception {
        TestingProcessManager.TestingProcess process = startInMemoryProcess();
        Main main = process.getProcess();
        Start storage = (Start) StorageLayer.getStorage(main);

        long since = BASE;
        String recipeUser = "parity-link-R";
        String primaryUser = "parity-link-P";

        // R active, then P active, then R linked into P — all after the boundary.
        recordActivity(storage, recipeUser, BASE + 1_000);
        recordActivity(storage, primaryUser, BASE + 2_000);
        // Linking: the direct writer drops R's row and refreshes P to the link time; the mapping change emits
        // a mirrored user_last_active for P and the account_linking event the reconcile matches on.
        deleteDirectRow(storage, recipeUser);
        recordActivity(storage, primaryUser, BASE + 3_000);
        insertAccountLinkingEvent(storage, recipeUser, primaryUser, BASE + 3_000);

        // Direct-write answer: the pair counts once, as the single primary user.
        int directCount = ActiveUsers.countUsersActiveSince(main, since);
        assertEquals(1, directCount);
        assertNull(getLastActive(storage, recipeUser));
        assertEquals(Long.valueOf(BASE + 3_000), getLastActive(storage, primaryUser));

        // Rebuild purely from the log: fold resurrects R momentarily, reconcile removes it in the same pass.
        truncateUserLastActive(storage);
        runRollup(storage, WHOLE_HISTORY_WINDOW);

        // Parity: still one active user, still just the primary — no double count.
        assertEquals(directCount, ActiveUsers.countUsersActiveSince(main, since));
        assertEquals(1, ActiveUsers.countUsersActiveSince(main, WHOLE_HISTORY_WINDOW));
        assertNull(getLastActive(storage, recipeUser));
        assertEquals(Long.valueOf(BASE + 3_000), getLastActive(storage, primaryUser));

        stopProcess(process);
    }

    /**
     * Unlink parity: a user unlinked from a primary regains a standalone last-active row. The
     * {@code account_linking} that once folded it away has aged out of the fold window (a later pass advances
     * past it), so the reconcile no longer removes the freed user; its own post-unlink activity folds back into
     * a standalone projection row — matching the direct write, which simply upserted that user's row when it was
     * active again.
     */
    @Test
    public void unlinkedUserRegainsStandaloneRowAfterRollup() throws Exception {
        TestingProcessManager.TestingProcess process = startInMemoryProcess();
        Main main = process.getProcess();
        Start storage = (Start) StorageLayer.getStorage(main);

        long since = BASE;
        String freedUser = "parity-unlink-U";
        String primaryUser = "parity-unlink-P";

        // U was linked into P in a prior window (before WHOLE_HISTORY_WINDOW, so out of this fold's reach).
        insertAccountLinkingEvent(storage, freedUser, primaryUser, BASE - 100_000);
        // U is unlinked, then active again on its own; P stays active too. All within this window.
        insertAccountUnlinkingEvent(storage, freedUser, primaryUser, BASE + 1_000);
        recordActivity(storage, freedUser, BASE + 2_000);
        recordActivity(storage, primaryUser, BASE + 2_500);

        // Direct-write answer: two standalone active users, U among them.
        int directCount = ActiveUsers.countUsersActiveSince(main, since);
        assertEquals(2, directCount);
        assertEquals(Long.valueOf(BASE + 2_000), getLastActive(storage, freedUser));

        // Rebuild purely from the log: the stale account_linking is outside the window, so reconcile leaves
        // U's freshly-folded standalone row intact.
        truncateUserLastActive(storage);
        runRollup(storage, WHOLE_HISTORY_WINDOW);

        // Parity: U regained its standalone row and both users are counted.
        assertEquals(directCount, ActiveUsers.countUsersActiveSince(main, since));
        assertEquals(Long.valueOf(BASE + 2_000), getLastActive(storage, freedUser));
        assertEquals(Long.valueOf(BASE + 2_500), getLastActive(storage, primaryUser));

        stopProcess(process);
    }

    /**
     * Ordering-guard parity: a user linked, then unlinked, then active again — all inside one fold window, so
     * the {@code account_linking} event never ages out. The reconcile must not scrub the user, because its
     * post-unlink activity is newer than the stale link. Without the {@code al.created_at >= last_active_time}
     * guard the correlated {@code EXISTS} would delete the still-active user's row (the link event is in the
     * window and matches on {@code app_id}/{@code recipe_user_id}), diverging from the PostgreSQL reconcile,
     * which keeps it. Mirrors the direct writer, which simply upserted U's row when it was active again.
     */
    @Test
    public void reactivatedAfterUnlinkInSameWindowKeepsRow() throws Exception {
        TestingProcessManager.TestingProcess process = startInMemoryProcess();
        Main main = process.getProcess();
        Start storage = (Start) StorageLayer.getStorage(main);

        long since = BASE;
        String freedUser = "parity-reactivate-U";
        String primaryUser = "parity-reactivate-P";

        // U linked into P, then unlinked, then active again on its own — all after the boundary, so the
        // account_linking event stays inside the fold window. P stays active too.
        insertAccountLinkingEvent(storage, freedUser, primaryUser, BASE + 1_000);
        insertAccountUnlinkingEvent(storage, freedUser, primaryUser, BASE + 2_000);
        recordActivity(storage, freedUser, BASE + 3_000);
        recordActivity(storage, primaryUser, BASE + 3_500);

        // Direct-write answer: two standalone active users, U among them at its reactivation time.
        int directCount = ActiveUsers.countUsersActiveSince(main, since);
        assertEquals(2, directCount);
        assertEquals(Long.valueOf(BASE + 3_000), getLastActive(storage, freedUser));

        // Rebuild purely from the log: the fold credits U at its reactivation, and the ordering guard keeps the
        // in-window account_linking (BASE + 1_000) from scrubbing U's newer row (BASE + 3_000).
        truncateUserLastActive(storage);
        runRollup(storage, WHOLE_HISTORY_WINDOW);

        // Parity: U keeps its standalone row and both users are counted — no divergence from Postgres.
        assertEquals(directCount, ActiveUsers.countUsersActiveSince(main, since));
        assertEquals(Long.valueOf(BASE + 3_000), getLastActive(storage, freedUser));
        assertEquals(Long.valueOf(BASE + 3_500), getLastActive(storage, primaryUser));

        stopProcess(process);
    }

    // ---- process lifecycle ----

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

    // ---- seeding: the direct-write state and its mirrored log ----

    /**
     * One unit of activity as {@code ActiveUsers.updateLastActive} produces it: the direct {@code user_last_active}
     * upsert (monotonic, keeping the most recent time) plus the mirrored {@code user_last_active} activity-log
     * event at the same instant.
     */
    private void recordActivity(Start storage, String userId, long createdAt) throws Exception {
        upsertDirectRow(storage, userId, createdAt);
        // 'sign_in' is a folded activity type; it stands in for the activity event ActiveUsers.updateLastActive
        // appends alongside the (now-retired) direct upsert this parity test simulates.
        insertActivityLogRow(storage, userId, userId, "sign_in", createdAt);
    }

    private void upsertDirectRow(Start storage, String userId, long lastActiveTime) throws Exception {
        // ON CONFLICT ... MAX mirrors the direct upsert keeping the latest activity for a repeatedly-active user.
        String query = "INSERT INTO " + USER_LAST_ACTIVE + " (app_id, user_id, last_active_time) VALUES (?, ?, ?)"
                + " ON CONFLICT (app_id, user_id) DO UPDATE SET last_active_time ="
                + " MAX(" + USER_LAST_ACTIVE + ".last_active_time, excluded.last_active_time)";
        runUpdate(storage, query, pst -> {
            pst.setString(1, APP_ID);
            pst.setString(2, userId);
            pst.setLong(3, lastActiveTime);
        });
    }

    private void deleteDirectRow(Start storage, String userId) throws Exception {
        String query = "DELETE FROM " + USER_LAST_ACTIVE + " WHERE app_id = ? AND user_id = ?";
        runUpdate(storage, query, pst -> {
            pst.setString(1, APP_ID);
            pst.setString(2, userId);
        });
    }

    private void truncateUserLastActive(Start storage) throws Exception {
        runUpdate(storage, "DELETE FROM " + USER_LAST_ACTIVE, pst -> {
        });
    }

    private void insertAccountLinkingEvent(Start storage, String recipeUserId, String primaryUserId, long createdAt)
            throws Exception {
        insertActivityLogRow(storage, recipeUserId, primaryUserId, "account_linking", createdAt);
    }

    private void insertAccountUnlinkingEvent(Start storage, String recipeUserId, String primaryUserId, long createdAt)
            throws Exception {
        insertActivityLogRow(storage, recipeUserId, primaryUserId, "account_unlinking", createdAt);
    }

    private void insertActivityLogRow(Start storage, String recipeUserId, String primaryOrRecipeUserId,
                                      String eventType, long createdAt) throws Exception {
        // The fold now credits a user only if their auth record (app_id_to_user_id) lives on this storage, so
        // seed a mapping for the credited primary_or_recipe_user_id. Best-effort and idempotent.
        ensureUserMappingBestEffort(storage, primaryOrRecipeUserId);
        String query = "INSERT INTO " + ACTIVITY_LOG
                + " (app_id, tenant_id, recipe_user_id, primary_or_recipe_user_id, event_type, status, created_at)"
                + " VALUES (?, 'public', ?, ?, ?, 'success', ?)";
        runUpdate(storage, query, pst -> {
            pst.setString(1, APP_ID);
            pst.setString(2, recipeUserId);
            pst.setString(3, primaryOrRecipeUserId);
            pst.setString(4, eventType);
            pst.setLong(5, createdAt);
        });
    }

    private void ensureUserMappingBestEffort(Start storage, String userId) throws Exception {
        String query = "INSERT OR IGNORE INTO app_id_to_user_id"
                + " (app_id, user_id, recipe_id, primary_or_recipe_user_id) VALUES (?, ?, 'emailpassword', ?)";
        storage.startTransaction(con -> {
            Connection sqlCon = (Connection) con.getConnection();
            try (PreparedStatement pst = sqlCon.prepareStatement(query)) {
                pst.setString(1, APP_ID);
                pst.setString(2, userId);
                pst.setString(3, userId);
                pst.executeUpdate();
                storage.commitTransaction(con);
            } catch (Exception ignored) {
                // No app row (deleted-app case): the fold's residency guard must skip this user.
            }
            return null;
        });
    }

    // ---- rollup + read helpers ----

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

    private interface StatementBinder {
        void bind(PreparedStatement pst) throws Exception;
    }

    private void runUpdate(Start storage, String query, StatementBinder binder) throws Exception {
        storage.startTransaction(con -> {
            Connection sqlCon = (Connection) con.getConnection();
            try (PreparedStatement pst = sqlCon.prepareStatement(query)) {
                binder.bind(pst);
                pst.executeUpdate();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            storage.commitTransaction(con);
            return null;
        });
    }
}
