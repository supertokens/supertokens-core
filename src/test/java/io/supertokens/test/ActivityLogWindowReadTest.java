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
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.storageLayer.StorageLayer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * In-memory (SQLite) parity for the app-scoped, window-bounded activity-log read
 * ({@code ActivityLogStorage.getActivityLogEntriesForApp}) that lets the count-delta interpreter, burst
 * cap, and shadow audit fold lifecycle events in Java. Mirrors the PostgreSQL plugin's test matrix:
 * half-open window bounds, event-type filtering, app scoping across all of an app's tenants (tenantId
 * preserved), ascending order with a storage-applied limit, payload round-trip (null stays null), and an
 * empty window returning an empty list rather than null — plus the contract's argument validation.
 */
public class ActivityLogWindowReadTest {

    private static final String LIFECYCLE_TYPE = "user_deletion";

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
     * The window is half-open on {@code created_at}: exactly {@code from} is excluded, exactly {@code to}
     * is included, and rows just outside either bound are excluded.
     */
    @Test
    public void windowBoundsAreHalfOpen() throws Exception {
        TestingProcessManager.TestingProcess process = startInMemoryProcess();
        Start storage = (Start) StorageLayer.getStorage(process.getProcess());

        long base = System.currentTimeMillis();
        long from = base + 1000;
        long to = base + 3000;

        insertEvent(storage, "app_bounds", null, LIFECYCLE_TYPE, from, null);       // == from -> excluded
        insertEvent(storage, "app_bounds", null, LIFECYCLE_TYPE, from + 500, null); // inside   -> included
        insertEvent(storage, "app_bounds", null, LIFECYCLE_TYPE, to, null);         // == to   -> included
        insertEvent(storage, "app_bounds", null, LIFECYCLE_TYPE, to + 1, null);     // > to    -> excluded

        List<AuditLogEvent> events = storage.getActivityLogEntriesForApp(new AppIdentifier(null, "app_bounds"),
                Set.of(LIFECYCLE_TYPE), from, to, 100);

        assertEquals(2, events.size());
        assertEquals(from + 500, events.get(0).createdAt);
        assertEquals(to, events.get(1).createdAt);

        stopProcess(process);
    }

    /**
     * The {@code eventTypes} filter is exact: a {@code user_last_active} row inside the same window is not
     * returned when only a lifecycle type is requested.
     */
    @Test
    public void typeFilterExcludesOtherEventTypes() throws Exception {
        TestingProcessManager.TestingProcess process = startInMemoryProcess();
        Start storage = (Start) StorageLayer.getStorage(process.getProcess());

        long base = System.currentTimeMillis();
        long from = base;
        long to = base + 10000;

        insertEvent(storage, "app_types", null, LIFECYCLE_TYPE, base + 1000, null);
        insertEvent(storage, "app_types", null, "sign_in", base + 2000, null);
        insertEvent(storage, "app_types", null, "tenant_association", base + 3000, null);

        List<AuditLogEvent> lifecycleOnly = storage.getActivityLogEntriesForApp(
                new AppIdentifier(null, "app_types"), Set.of(LIFECYCLE_TYPE, "tenant_association"), from, to, 100);

        assertEquals(2, lifecycleOnly.size());
        Set<String> returnedTypes = lifecycleOnly.stream().map(e -> e.eventType).collect(Collectors.toSet());
        assertEquals(Set.of(LIFECYCLE_TYPE, "tenant_association"), returnedTypes);

        stopProcess(process);
    }

    /**
     * App scoping under a single connection-URI domain: every tenant of the requested app is included
     * (with its {@code tenantId} preserved on each row), while another app's events in the same window
     * are excluded.
     */
    @Test
    public void appScopedAcrossAllTenantsTenantIdPreserved() throws Exception {
        TestingProcessManager.TestingProcess process = startInMemoryProcess();
        Start storage = (Start) StorageLayer.getStorage(process.getProcess());

        long base = System.currentTimeMillis();
        long from = base;
        long to = base + 10000;

        insertEvent(storage, "app_scope", null, LIFECYCLE_TYPE, base + 1000, null); // app_scope / public
        insertEvent(storage, "app_scope", "t1", LIFECYCLE_TYPE, base + 2000, null); // app_scope / t1
        insertEvent(storage, "other_app", null, LIFECYCLE_TYPE, base + 3000, null); // different app

        List<AuditLogEvent> events = storage.getActivityLogEntriesForApp(new AppIdentifier(null, "app_scope"),
                Set.of(LIFECYCLE_TYPE), from, to, 100);

        assertEquals(2, events.size());
        for (AuditLogEvent event : events) {
            assertEquals("app_scope", event.appId);
        }
        Set<String> tenantIds = events.stream().map(e -> e.tenantId).collect(Collectors.toSet());
        assertEquals(Set.of("public", "t1"), tenantIds);

        stopProcess(process);
    }

    /**
     * Rows come back ascending by {@code created_at}, and {@code limit} is applied in the query so the
     * result is exactly the oldest {@code limit} rows of the window.
     */
    @Test
    public void ascendingOrderAndLimitReturnsOldest() throws Exception {
        TestingProcessManager.TestingProcess process = startInMemoryProcess();
        Start storage = (Start) StorageLayer.getStorage(process.getProcess());

        long base = System.currentTimeMillis();
        long from = base;
        long to = base + 10000;

        // Insert out of chronological order to prove the ORDER BY, not insertion order.
        insertEvent(storage, "app_limit", null, LIFECYCLE_TYPE, base + 3000, null);
        insertEvent(storage, "app_limit", null, LIFECYCLE_TYPE, base + 1000, null);
        insertEvent(storage, "app_limit", null, LIFECYCLE_TYPE, base + 5000, null);
        insertEvent(storage, "app_limit", null, LIFECYCLE_TYPE, base + 2000, null);
        insertEvent(storage, "app_limit", null, LIFECYCLE_TYPE, base + 4000, null);

        List<AuditLogEvent> oldestThree = storage.getActivityLogEntriesForApp(new AppIdentifier(null, "app_limit"),
                Set.of(LIFECYCLE_TYPE), from, to, 3);

        assertEquals(3, oldestThree.size());
        assertEquals(base + 1000, oldestThree.get(0).createdAt);
        assertEquals(base + 2000, oldestThree.get(1).createdAt);
        assertEquals(base + 3000, oldestThree.get(2).createdAt);

        // A cap of size+1 returns the whole window and shows it is not over the cap.
        List<AuditLogEvent> all = storage.getActivityLogEntriesForApp(new AppIdentifier(null, "app_limit"),
                Set.of(LIFECYCLE_TYPE), from, to, 6);
        assertEquals(5, all.size());
        for (int i = 1; i < all.size(); i++) {
            assertTrue(all.get(i - 1).createdAt <= all.get(i).createdAt);
        }

        stopProcess(process);
    }

    /**
     * Payload is returned as the stored text unchanged, and a null payload stays null.
     */
    @Test
    public void payloadRoundTripAndNullStaysNull() throws Exception {
        TestingProcessManager.TestingProcess process = startInMemoryProcess();
        Start storage = (Start) StorageLayer.getStorage(process.getProcess());

        long base = System.currentTimeMillis();
        long from = base;
        long to = base + 10000;

        String json = "{\"before\":[\"public\",\"t1\"],\"after\":[\"public\"]}";
        insertEvent(storage, "app_payload", null, LIFECYCLE_TYPE, base + 1000, json);
        insertEvent(storage, "app_payload", null, LIFECYCLE_TYPE, base + 2000, null);

        List<AuditLogEvent> events = storage.getActivityLogEntriesForApp(new AppIdentifier(null, "app_payload"),
                Set.of(LIFECYCLE_TYPE), from, to, 100);

        assertEquals(2, events.size());
        assertEquals(json, events.get(0).payload);
        assertNull(events.get(1).payload);

        stopProcess(process);
    }

    /**
     * A window with no matching events returns an empty list, never null.
     */
    @Test
    public void emptyWindowReturnsEmptyListNotNull() throws Exception {
        TestingProcessManager.TestingProcess process = startInMemoryProcess();
        Start storage = (Start) StorageLayer.getStorage(process.getProcess());

        long base = System.currentTimeMillis();

        List<AuditLogEvent> events = storage.getActivityLogEntriesForApp(new AppIdentifier(null, "app_empty"),
                Set.of(LIFECYCLE_TYPE), base, base + 10000, 100);

        assertNotNull(events);
        assertTrue(events.isEmpty());

        stopProcess(process);
    }

    /**
     * The contract forbids an unfiltered read: {@code limit <= 0} and an empty {@code eventTypes} each
     * raise {@code IllegalArgumentException}.
     */
    @Test
    public void invalidArgumentsThrow() throws Exception {
        TestingProcessManager.TestingProcess process = startInMemoryProcess();
        Start storage = (Start) StorageLayer.getStorage(process.getProcess());

        long base = System.currentTimeMillis();
        AppIdentifier app = new AppIdentifier(null, "app_invalid");

        try {
            storage.getActivityLogEntriesForApp(app, Set.of(LIFECYCLE_TYPE), base, base + 1000, 0);
            fail("expected IllegalArgumentException for limit <= 0");
        } catch (IllegalArgumentException expected) {
            // expected
        }

        try {
            storage.getActivityLogEntriesForApp(app, new HashSet<>(), base, base + 1000, 100);
            fail("expected IllegalArgumentException for empty eventTypes");
        } catch (IllegalArgumentException expected) {
            // expected
        }

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

    private void insertEvent(Start storage, String appId, String tenantId, String eventType, long createdAt,
                             String payload) throws Exception {
        // The insert takes app_id/tenant_id from the TenantIdentifier; the rest of the row from the event.
        TenantIdentifier tenantIdentifier = new TenantIdentifier(null, appId, tenantId);
        AuditLogEvent event = new AuditLogEvent(appId, tenantId, "ru-" + createdAt, "pru-" + createdAt,
                eventType, "success", null, null, createdAt, payload);
        storage.createActivityLogEntry(tenantIdentifier, event);
    }
}
