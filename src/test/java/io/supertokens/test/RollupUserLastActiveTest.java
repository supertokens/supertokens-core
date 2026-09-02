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
import io.supertokens.cronjobs.CronTaskTest;
import io.supertokens.cronjobs.rollupUserLastActive.RollupDirtySignal;
import io.supertokens.cronjobs.rollupUserLastActive.RollupUserLastActive;
import io.supertokens.inmemorydb.Start;
import io.supertokens.pluginInterface.KeyValueInfo;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.storageLayer.StorageLayer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.mockito.Mockito;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Behavioural tests for the {@link RollupUserLastActive} cron on the in-memory (SQLite) storage: the
 * dirty-flag idle gate, the run-start watermark advance, the first-run existence check, the long-gap
 * catch-up clamped to retention, and the periodic unconditional backstop fold.
 *
 * <p>The cron auto-registered by {@code Main} is neutralised per test by giving it an enormous initial
 * wait and interval, so the only passes that run are the ones the test drives explicitly on a fresh
 * {@link RollupUserLastActive#getNewInstanceForTesting} instance. Advisory-lock collapse under concurrent
 * instances is a PostgreSQL property (no advisory lock in the single-instance in-memory store) and is
 * covered by the postgresql plugin's own rollup tests.
 */
public class RollupUserLastActiveTest {

    private static final String APP_ID = "public";
    private static final String ACTIVITY_LOG = "activity_log";
    private static final String USER_LAST_ACTIVE = "user_last_active";

    private static final long DAY_MS = 24L * 60L * 60L * 1000L;

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
     * A dirty pass folds pending activity and advances the watermark; a subsequent clean, non-forced pass
     * skips entirely (does not fold newly-inserted activity, does not move the watermark); once re-armed,
     * the next pass folds the new activity.
     */
    @Test
    public void dirtyPassFoldsAndAdvancesWatermarkWhileACleanPassIsANoOp() throws Exception {
        TestFixture f = startFixture();

        long base = System.currentTimeMillis();
        String userA = "rollup-cron-A";
        String userB = "rollup-cron-B";

        insertUserLastActiveEvent(f.storage, userA, base + 1000);
        RollupDirtySignal.getInstance(f.main).markDirty(f.userPoolId);

        f.cron.runOncePerStorageForTesting(f.representative, f.storage);
        assertEquals(Long.valueOf(base + 1000), getLastActive(f.storage, userA));
        Long watermarkAfterFold = getWatermark(f.storage, f.representative);
        assertNotNull(watermarkAfterFold);

        // New activity, but the flag is clean and this is not a forced pass — the cron must skip it.
        insertUserLastActiveEvent(f.storage, userB, base + 2000);
        f.cron.runOncePerStorageForTesting(f.representative, f.storage);
        assertNull(getLastActive(f.storage, userB));
        assertEquals(watermarkAfterFold, getWatermark(f.storage, f.representative));

        // Re-arm the flag (as an emit path would) — now the pass folds the new activity.
        RollupDirtySignal.getInstance(f.main).markDirty(f.userPoolId);
        f.cron.runOncePerStorageForTesting(f.representative, f.storage);
        assertEquals(Long.valueOf(base + 2000), getLastActive(f.storage, userB));

        stopFixture(f);
    }

    /**
     * The first pass after startup cannot trust a clean flag, so it runs the cheap existence check and folds
     * activity that predates the flag (e.g. rows written by the direct last-active writer).
     */
    @Test
    public void firstRunExistenceCheckFoldsPendingActivityDespiteACleanFlag() throws Exception {
        TestFixture f = startFixture();

        long base = System.currentTimeMillis();
        String user = "rollup-cron-startup";

        // Activity exists, but nothing marked the flag dirty.
        insertUserLastActiveEvent(f.storage, user, base + 1000);

        f.cron.runOncePerStorageForTesting(f.representative, f.storage);

        assertEquals(Long.valueOf(base + 1000), getLastActive(f.storage, user));
        assertNotNull(getWatermark(f.storage, f.representative));

        stopFixture(f);
    }

    /**
     * The first pass after startup, with a clean flag and nothing to fold, skips without writing a
     * watermark (the existence check short-circuits it).
     */
    @Test
    public void firstRunExistenceCheckSkipsWhenThereIsNothingToFold() throws Exception {
        TestFixture f = startFixture();

        f.cron.runOncePerStorageForTesting(f.representative, f.storage);

        assertNull(getWatermark(f.storage, f.representative));

        stopFixture(f);
    }

    /**
     * A long outage: the watermark lags far behind. The next window widens to catch the backlog, but is
     * clamped to the retention horizon — activity older than retention (whose partition would be dropped
     * anyway) is not folded, while activity within retention is.
     */
    @Test
    public void longGapFoldsBacklogClampedToRetention() throws Exception {
        TestFixture f = startFixture();

        long now = System.currentTimeMillis();
        String userWithinRetention = "rollup-cron-within";
        String userBeyondRetention = "rollup-cron-beyond";

        // Watermark far in the past — as if the cron had not run for 60 days.
        setWatermark(f.storage, f.representative, now - 60 * DAY_MS);

        // Default retention is 31 days.
        insertUserLastActiveEvent(f.storage, userWithinRetention, now - 20 * DAY_MS);
        insertUserLastActiveEvent(f.storage, userBeyondRetention, now - 40 * DAY_MS);

        RollupDirtySignal.getInstance(f.main).markDirty(f.userPoolId);
        f.cron.runOncePerStorageForTesting(f.representative, f.storage);

        assertEquals(Long.valueOf(now - 20 * DAY_MS), getLastActive(f.storage, userWithinRetention));
        assertNull(getLastActive(f.storage, userBeyondRetention));

        stopFixture(f);
    }

    /**
     * Even with a clean flag, the backstop tick (every Nth pass) folds unconditionally, so a lost dirty
     * signal cannot stall the projection indefinitely.
     */
    @Test
    public void backstopTickFoldsWithoutADirtyFlag() throws Exception {
        TestFixture f = startFixture();

        long base = System.currentTimeMillis();
        String user = "rollup-cron-backstop";

        // Pass 1: first run, clean flag, nothing to fold -> existence check skips.
        f.cron.runOncePerStorageForTesting(f.representative, f.storage);

        // Activity arrives but the flag stays clean (e.g. the instance that saw it died).
        insertUserLastActiveEvent(f.storage, user, base + 1000);

        // Passes 2..6 are clean, non-forced -> all skip; the activity stays unfolded.
        for (int i = 0; i < 5; i++) {
            f.cron.runOncePerStorageForTesting(f.representative, f.storage);
        }
        assertNull(getLastActive(f.storage, user));

        // The 7th pass is the backstop (passCount hits the interval) -> folds despite the clean flag.
        f.cron.runOncePerStorageForTesting(f.representative, f.storage);
        assertEquals(Long.valueOf(base + 1000), getLastActive(f.storage, user));

        stopFixture(f);
    }

    /**
     * When the fold transaction throws, the dirty flag — consumed at the top of the pass — must be re-armed
     * so the next tick retries the storage instead of silently dropping the pending activity. Forces the
     * failure by removing the projection table the fold writes into.
     */
    @Test
    public void aFailedFoldReArmsTheDirtyFlagForRetry() throws Exception {
        TestFixture f = startFixture();

        long base = System.currentTimeMillis();
        insertUserLastActiveEvent(f.storage, "rollup-cron-fail", base + 1000);
        RollupDirtySignal.getInstance(f.main).markDirty(f.userPoolId);

        // Make the fold's INSERT fail: drop the table it writes into.
        dropUserLastActiveTable(f.storage);

        try {
            f.cron.runOncePerStorageForTesting(f.representative, f.storage);
            fail("expected the fold to throw once its target table is gone");
        } catch (Exception expected) {
            // The pass consumed the dirty flag at its top; the failure handler must re-arm it.
        }

        // Re-armed: consuming it now returns true (and clears it again).
        assertTrue(RollupDirtySignal.getInstance(f.main).consumeDirty(f.userPoolId));

        stopFixture(f);
    }

    /**
     * A pass whose fold is skipped — a concurrent instance holds the storage's rollup lock, so the fold
     * returns {@code false} and writes nothing — must not advance the watermark, and must re-arm the dirty
     * flag so the next tick retries. This pins the catch-up loss case: with a null watermark, advancing on a
     * skipped fold would strand the deep backlog forever, since later windows only reach back the margin.
     * Modelled by a spy whose {@code rollupLastActiveFromActivityLog_Transaction} reports it did nothing;
     * every other call (transaction, key-value, existence check) runs against the real shared storage.
     */
    @Test
    public void aSkippedFoldDoesNotAdvanceTheWatermarkAndReArmsForRetry() throws Exception {
        TestFixture f = startFixture();

        long base = System.currentTimeMillis();
        insertUserLastActiveEvent(f.storage, "rollup-skip", base + 1000);
        RollupDirtySignal.getInstance(f.main).markDirty(f.userPoolId);

        // A lock loser: the fold reports it folded nothing (another instance holds the rollup lock).
        Start skippingStorage = Mockito.spy(f.storage);
        Mockito.doReturn(false).when(skippingStorage)
                .rollupLastActiveFromActivityLog_Transaction(Mockito.any(), Mockito.anyLong());

        // Null watermark before the pass — the deep-catch-up loss case.
        assertNull(getWatermark(f.storage, f.representative));

        f.cron.runOncePerStorageForTesting(f.representative, skippingStorage);

        // The skipped fold advanced nothing: the watermark stays absent, so the deep window is re-foldable.
        assertNull(getWatermark(f.storage, f.representative));
        // And the dirty flag is re-armed for the next tick.
        assertTrue(RollupDirtySignal.getInstance(f.main).consumeDirty(f.userPoolId));

        stopFixture(f);
    }

    // ---- fixture ----

    private static class TestFixture {
        io.supertokens.Main main;
        TestingProcessManager.TestingProcess process;
        Start storage;
        TenantIdentifier representative;
        String userPoolId;
        RollupUserLastActive cron;
    }

    private TestFixture startFixture() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args, false);
        process.getProcess().setForceInMemoryDB();
        // Neutralise the auto-registered rollup cron so only the test-driven passes run.
        CronTaskTest.getInstance(process.getProcess())
                .setInitialWaitTimeInSeconds(RollupUserLastActive.RESOURCE_KEY, 24 * 3600);
        CronTaskTest.getInstance(process.getProcess())
                .setIntervalInSeconds(RollupUserLastActive.RESOURCE_KEY, 24 * 3600);
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        TestFixture f = new TestFixture();
        f.main = process.getProcess();
        f.process = process;
        f.storage = (Start) StorageLayer.getStorage(process.getProcess());
        List<List<TenantIdentifier>> tenantsInfo = StorageLayer.getTenantsWithUniqueUserPoolId(process.getProcess());
        f.representative = tenantsInfo.get(0).get(0);
        f.userPoolId = f.storage.getUserPoolId();
        f.cron = RollupUserLastActive.getNewInstanceForTesting(process.getProcess(), tenantsInfo);
        return f;
    }

    private void stopFixture(TestFixture f) throws Exception {
        f.process.kill();
        assertNotNull(f.process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // ---- helpers ----

    private Long getWatermark(Start storage, TenantIdentifier representative) throws Exception {
        KeyValueInfo info = storage.getKeyValue(representative, RollupUserLastActive.WATERMARK_KEY);
        return info == null ? null : Long.parseLong(info.value);
    }

    private void setWatermark(Start storage, TenantIdentifier representative, long millis) throws Exception {
        storage.setKeyValue(representative, RollupUserLastActive.WATERMARK_KEY,
                new KeyValueInfo(String.valueOf(millis)));
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

    private void dropUserLastActiveTable(Start storage) throws Exception {
        storage.startTransaction(con -> {
            Connection sqlCon = (Connection) con.getConnection();
            try (PreparedStatement pst = sqlCon.prepareStatement("DROP TABLE " + USER_LAST_ACTIVE)) {
                pst.executeUpdate();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            storage.commitTransaction(con);
            return null;
        });
    }

    private void insertUserLastActiveEvent(Start storage, String userId, long createdAt) throws Exception {
        // The fold now credits a user only if their auth record (app_id_to_user_id) lives on this storage, so
        // seed a mapping for the user. Best-effort and idempotent.
        ensureUserMappingBestEffort(storage, userId);
        // For an activity event the user is its own primary_or_recipe_user_id. 'sign_in' is a folded type.
        String query = "INSERT INTO " + ACTIVITY_LOG
                + " (app_id, tenant_id, recipe_user_id, primary_or_recipe_user_id, event_type, status, created_at)"
                + " VALUES (?, 'public', ?, ?, 'sign_in', 'success', ?)";
        storage.startTransaction(con -> {
            Connection sqlCon = (Connection) con.getConnection();
            try (PreparedStatement pst = sqlCon.prepareStatement(query)) {
                pst.setString(1, APP_ID);
                pst.setString(2, userId);
                pst.setString(3, userId);
                pst.setLong(4, createdAt);
                pst.executeUpdate();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            storage.commitTransaction(con);
            return null;
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
}
