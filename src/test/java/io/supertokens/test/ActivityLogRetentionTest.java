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
import io.supertokens.pluginInterface.auditlog.ActivityLogStorage;
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

public class ActivityLogRetentionTest {

    private static final long MILLIS_PER_DAY = 24L * 60 * 60 * 1000;

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
     * The in-memory (SQLite) store has no partitions to drop, so its activity log retention is a
     * direct delete run by the same maintenance hook the cron calls: entries older than the 31-day
     * window must be removed, recent ones kept. (The PostgreSQL retention behaviour is covered by
     * the plugin's own ActivityLogPartitionTest.)
     */
    @Test
    public void inMemoryActivityLogRetentionDeletesOldEntries() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args, false);
        process.getProcess().setForceInMemoryDB();
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        Start storage = (Start) StorageLayer.getStorage(process.getProcess());
        ActivityLogStorage activityLog = (ActivityLogStorage) storage;

        long now = System.currentTimeMillis();
        activityLog.createActivityLogEntry(TenantIdentifier.BASE_TENANT,
                makeEvent("recent_event", now));
        activityLog.createActivityLogEntry(TenantIdentifier.BASE_TENANT,
                makeEvent("expired_event", now - 40 * MILLIS_PER_DAY));
        assertEquals(2, countActivityLogRows(storage));

        activityLog.maintainActivityLogPartitions();

        assertEquals(1, countActivityLogRows(storage));

        // Idempotent: nothing further to delete.
        activityLog.maintainActivityLogPartitions();
        assertEquals(1, countActivityLogRows(storage));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    private static AuditLogEvent makeEvent(String eventType, long createdAt) {
        return new AuditLogEvent("public", "public", null, null, eventType, null, null, null, createdAt, null);
    }

    private static int countActivityLogRows(Start storage) throws Exception {
        return storage.startTransaction(con -> {
            Connection sqlCon = (Connection) con.getConnection();
            try (PreparedStatement pst = sqlCon.prepareStatement("SELECT COUNT(*) FROM activity_log")) {
                try (ResultSet rs = pst.executeQuery()) {
                    rs.next();
                    return rs.getInt(1);
                }
            }
        });
    }
}
