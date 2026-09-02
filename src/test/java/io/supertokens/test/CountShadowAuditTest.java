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

import io.supertokens.authRecipe.CountShadowAudit;
import io.supertokens.auditlog.lifecycle.GroupPresence;
import io.supertokens.auditlog.lifecycle.InvalidLifecycleEventPayloadException;
import io.supertokens.auditlog.lifecycle.LifecycleEventPayload;
import io.supertokens.pluginInterface.auditlog.LifecycleEventType;
import io.supertokens.pluginInterface.auditlog.AuditLogEvent;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Pure unit tests for {@link CountShadowAudit#evaluate} — the audit's parse/fold/compare logic, with no
 * process, storage, or config (the evaluator does no I/O). The end-to-end wiring against real storage is
 * covered by the integration tests in {@link ApproximateUserCountTest}.
 */
public class CountShadowAuditTest {

    private static final long FROM = 1_000L;
    private static final long TO = 2_000L;

    // ---- Helpers to build activity-log rows carrying lifecycle payloads (only the payload is read) ----

    private static AuditLogEvent event(LifecycleEventPayload payload) {
        return new AuditLogEvent("app", null, "recipeUser", "groupUser", payload.type.getValue(), "success",
                null, null, 0L, payload.toJson());
    }

    private static AuditLogEvent creation(String tenantId) {
        return event(LifecycleEventPayload.forUserCreation(tenantId));
    }

    private static AuditLogEvent groupDeletion(String... tenantIds) {
        return event(LifecycleEventPayload.forUserGroupDeletion(
                new GroupPresence("g", Arrays.asList(tenantIds))));
    }

    // For a correct ledger, previousExactCount + fold(window) equals the fresh exact count -> MATCH.
    @Test
    public void matchWhenFoldExplainsTheCountChange() throws Exception {
        // +1 (create) +1 (create) -1 (group delete) = net +1 in tenant "t1".
        List<AuditLogEvent> window = Arrays.asList(creation("t1"), creation("t1"), groupDeletion("t1"));

        CountShadowAudit.Result result = new CountShadowAudit(10_000)
                .evaluate("t1", 5L, 6L, window, FROM, TO);

        assertEquals(CountShadowAudit.Status.MATCH, result.status);
        assertFalse(result.isDiscrepancy());
        assertEquals(1L, result.foldedDelta);
        assertEquals(6L, result.expectedCount);
        assertEquals(6L, result.freshExactCount);
        assertEquals(5L, result.previousExactCount);
        assertEquals(3, result.eventCount);
        assertEquals(FROM, result.windowFromExclusiveMs);
        assertEquals(TO, result.windowToInclusiveMs);
    }

    // An empty window with no count change is the steady-state match.
    @Test
    public void emptyWindowWithNoCountChangeMatches() throws Exception {
        CountShadowAudit.Result result = new CountShadowAudit(10_000)
                .evaluate("t1", 7L, 7L, Collections.emptyList(), FROM, TO);

        assertEquals(CountShadowAudit.Status.MATCH, result.status);
        assertEquals(0L, result.foldedDelta);
        assertEquals(7L, result.expectedCount);
        assertEquals(0, result.eventCount);
    }

    // The count moved but no event explains it (e.g. a mutation point that forgot to emit) -> discrepancy.
    @Test
    public void discrepancyWhenCountMovesWithoutEvents() throws Exception {
        CountShadowAudit.Result result = new CountShadowAudit(10_000)
                .evaluate("t1", 5L, 6L, Collections.emptyList(), FROM, TO);

        assertEquals(CountShadowAudit.Status.DISCREPANCY, result.status);
        assertTrue(result.isDiscrepancy());
        assertEquals(0L, result.foldedDelta);
        assertEquals(5L, result.expectedCount);   // previous + 0
        assertEquals(6L, result.freshExactCount);  // but the fresh recompute says 6
        assertEquals(0, result.eventCount);
    }

    // Events imply a delta the exact count does not agree with (interpreter/ledger bug) -> discrepancy.
    @Test
    public void discrepancyWhenFoldedDeltaDisagreesWithCount() throws Exception {
        // Window folds to -1, but the count did not actually drop.
        CountShadowAudit.Result result = new CountShadowAudit(10_000)
                .evaluate("t1", 5L, 5L, Arrays.asList(groupDeletion("t1")), FROM, TO);

        assertEquals(CountShadowAudit.Status.DISCREPANCY, result.status);
        assertEquals(-1L, result.foldedDelta);
        assertEquals(4L, result.expectedCount);
        assertEquals(5L, result.freshExactCount);
        assertEquals(1, result.eventCount);
    }

    // A window larger than the burst cap is skipped, not folded, and reported as re-anchor (not a discrepancy).
    @Test
    public void reAnchorWhenWindowExceedsBurstCap() throws Exception {
        List<AuditLogEvent> window = Arrays.asList(creation("t1"), creation("t1"), creation("t1"));

        CountShadowAudit.Result result = new CountShadowAudit(2)
                .evaluate("t1", 5L, 99L, window, FROM, TO);

        assertEquals(CountShadowAudit.Status.RE_ANCHOR_REQUIRED, result.status);
        assertFalse(result.isDiscrepancy());
        assertEquals(0L, result.foldedDelta);           // not folded
        assertEquals(5L, result.expectedCount);         // left at previous
        assertEquals(3, result.eventCount);             // but the count is reported
        assertEquals(99L, result.freshExactCount);
    }

    // A window exactly at the cap is still folded (the cap is a strict "greater than").
    @Test
    public void windowAtBurstCapIsStillFolded() throws Exception {
        List<AuditLogEvent> window = Arrays.asList(creation("t1"), creation("t1"));

        CountShadowAudit.Result result = new CountShadowAudit(2)
                .evaluate("t1", 5L, 7L, window, FROM, TO);

        assertEquals(CountShadowAudit.Status.MATCH, result.status);
        assertEquals(2L, result.foldedDelta);
    }

    // The fold isolates the audited tenant: events for other tenants in the app-scoped window don't leak in.
    @Test
    public void foldIsolatesTheRequestedTenant() throws Exception {
        // t1: +1; t2: +1 then -1 (net 0). Auditing t1 must see only +1.
        List<AuditLogEvent> window = Arrays.asList(creation("t1"), creation("t2"), groupDeletion("t2"));

        CountShadowAudit audit = new CountShadowAudit(10_000);
        assertEquals(1L, audit.evaluate("t1", 3L, 4L, window, FROM, TO).foldedDelta);
        assertEquals(0L, audit.evaluate("t2", 8L, 8L, window, FROM, TO).foldedDelta);
        assertEquals(CountShadowAudit.Status.MATCH, audit.evaluate("t2", 8L, 8L, window, FROM, TO).status);
    }

    // A group-deletion spanning two tenants subtracts one from each; the audit for one tenant sees only its own.
    @Test
    public void groupDeletionAcrossTenantsAffectsEachTenantOnce() throws Exception {
        List<AuditLogEvent> window = Arrays.asList(groupDeletion("t1", "t2"));

        CountShadowAudit audit = new CountShadowAudit(10_000);
        assertEquals(-1L, audit.evaluate("t1", 5L, 4L, window, FROM, TO).foldedDelta);
        assertEquals(-1L, audit.evaluate("t2", 9L, 8L, window, FROM, TO).foldedDelta);
    }

    // A malformed stored payload is itself a ledger-integrity signal: evaluate surfaces it as an exception so
    // the caller can record an audit failure rather than silently passing.
    @Test
    public void malformedPayloadIsSurfaced() {
        AuditLogEvent bad = new AuditLogEvent("app", null, "r", "g",
                LifecycleEventType.USER_CREATION.getValue(), "success", null, null, 0L, "{ not valid json");
        try {
            new CountShadowAudit(10_000).evaluate("t1", 0L, 0L,
                    new ArrayList<>(Arrays.asList(bad)), FROM, TO);
            fail("expected InvalidLifecycleEventPayloadException");
        } catch (InvalidLifecycleEventPayloadException expected) {
            // ok
        }
    }

    @Test
    public void argumentGuards() throws Exception {
        CountShadowAudit audit = new CountShadowAudit(10_000);
        try {
            audit.evaluate(null, 0L, 0L, Collections.emptyList(), FROM, TO);
            fail("expected IllegalArgumentException for null tenantId");
        } catch (IllegalArgumentException expected) {
            // ok
        }
        try {
            audit.evaluate("t1", 0L, 0L, null, FROM, TO);
            fail("expected IllegalArgumentException for null windowEvents");
        } catch (IllegalArgumentException expected) {
            // ok
        }
        try {
            new CountShadowAudit(0);
            fail("expected IllegalArgumentException for non-positive burstCap");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    // The type filter passed to the storage read must cover exactly the lifecycle vocabulary and be immutable.
    @Test
    public void lifecycleEventTypesCoversEveryLifecycleType() {
        assertEquals(LifecycleEventType.values().length, CountShadowAudit.LIFECYCLE_EVENT_TYPES.size());
        for (LifecycleEventType type : LifecycleEventType.values()) {
            assertTrue(CountShadowAudit.LIFECYCLE_EVENT_TYPES.contains(type.getValue()));
        }
        try {
            CountShadowAudit.LIFECYCLE_EVENT_TYPES.add("x");
            fail("expected the type set to be immutable");
        } catch (UnsupportedOperationException expected) {
            // ok
        }
    }
}
