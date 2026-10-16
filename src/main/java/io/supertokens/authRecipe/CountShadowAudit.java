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

package io.supertokens.authRecipe;

import io.supertokens.auditlog.lifecycle.CountDeltaInterpreter;
import io.supertokens.auditlog.lifecycle.InvalidLifecycleEventPayloadException;
import io.supertokens.auditlog.lifecycle.LifecycleEventPayload;
import io.supertokens.pluginInterface.auditlog.LifecycleEventType;
import io.supertokens.pluginInterface.auditlog.AuditLogEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The shadow audit for the approximate user count (PLAN-010 unit 3): the promotion gate that checks the
 * lifecycle-event ledger and its {@link CountDeltaInterpreter interpreter} against a freshly recomputed exact
 * count, once per background anchor refresh.
 *
 * <p>The invariant checked is the ledger completeness property the eventual anchor+fold serving path
 * (units 4–5) will rely on: between two consecutive anchor refreshes the exact tenant user count must move by
 * exactly the net delta folded from the lifecycle events committed in that window. Concretely, with
 * {@code previousExactCount} the exact count taken at the previous refresh (snapshot time {@code T_prev}) and
 * {@code freshExactCount} the exact count taken at this refresh ({@code T_now}), the events in
 * {@code (T_prev, T_now]} must satisfy
 * <pre>{@code previousExactCount + fold(events).deltaForTenant(tenant) == freshExactCount}.</pre>
 *
 * <p><b>Why two exact counts rather than the served anchors.</b> PLAN-009's rebased anchor is
 * {@code C - countJoinedSince(X)} taken at a snapshot {@code T} but rebased onto a join-time boundary
 * {@code X = T - skew}; it is deliberately creations-only-exact and reflects deletions/link-merges only up to
 * its snapshot {@code T}, not up to {@code X}. Folding an event window from that anchor would double-count the
 * non-creation mutations that landed in the snapshot's skew margin {@code (X, T]}, producing systematic
 * false-positive discrepancies that are not ledger bugs. Anchoring the audit on the true exact count at each
 * refresh's snapshot — and bounding the window by the same snapshot times — makes the check exact for a
 * correct ledger: every count-affecting mutation between the two snapshots is folded exactly once. This keeps
 * a reported discrepancy meaning what the plan says it means — a real ledger or interpreter bug localized to
 * one refresh window — rather than a boundary artifact.
 *
 * <p><b>Burst cap.</b> A window larger than the {@link #getBurstCap() burst cap} (a bulk import or mass
 * deletion) is not folded — the fresh anchor has already re-based past it and folding would be slow and
 * pointless — so the audit reports {@link Status#RE_ANCHOR_REQUIRED} and is simply skipped for that window,
 * mirroring the interpreter's own re-anchor signal. This is not a discrepancy.
 *
 * <p>This class is a pure function over its inputs: {@link #evaluate} fetches nothing and logs nothing. The
 * I/O (reading the fresh exact count and the event window, and reporting a discrepancy) lives in
 * {@link ApproximateUserCount}, which owns the anchor-refresh path this audit runs inside.
 */
public final class CountShadowAudit {

    /**
     * The {@code event_type} values the audit folds — every {@link LifecycleEventType lifecycle event}. Passed
     * to the storage window read as the (non-empty) type filter so activity pings never enter the fold.
     */
    public static final Set<String> LIFECYCLE_EVENT_TYPES;

    static {
        Set<String> types = new LinkedHashSet<>();
        for (LifecycleEventType type : LifecycleEventType.values()) {
            types.add(type.getValue());
        }
        LIFECYCLE_EVENT_TYPES = Collections.unmodifiableSet(types);
    }

    private final int burstCap;

    /**
     * @param burstCap the maximum number of events in a window to fold; a larger window is skipped with
     *                 {@link Status#RE_ANCHOR_REQUIRED}. Must be positive.
     */
    public CountShadowAudit(int burstCap) {
        if (burstCap <= 0) {
            throw new IllegalArgumentException("burstCap must be positive");
        }
        this.burstCap = burstCap;
    }

    /** @return the event-count ceiling above which a window is skipped instead of folded. */
    public int getBurstCap() {
        return burstCap;
    }

    /**
     * Evaluates the audit for one tenant over one refresh window. Pure: parses each event's payload, folds the
     * per-tenant delta with the shared {@link CountDeltaInterpreter} semantics, and compares
     * {@code previousExactCount + delta} against {@code freshExactCount}.
     *
     * @param tenantId             the tenant whose count is audited
     * @param previousExactCount   the exact tenant user count at the previous refresh's snapshot
     * @param freshExactCount      the exact tenant user count at this refresh's snapshot
     * @param windowEvents         the lifecycle events committed in {@code (windowFromExclusiveMs,
     *                             windowToInclusiveMs]}, app-scoped across the tenant's app; may include events
     *                             for other tenants (the fold isolates this tenant). At most {@code burstCap+1}
     *                             need be supplied — one over the cap is enough to detect an over-cap window.
     * @param windowFromExclusiveMs the previous refresh's snapshot time (exclusive lower bound), for context
     * @param windowToInclusiveMs   this refresh's snapshot time (inclusive upper bound), for context
     * @return the audit {@link Result}: {@link Status#MATCH}, {@link Status#DISCREPANCY}, or
     * {@link Status#RE_ANCHOR_REQUIRED}
     * @throws InvalidLifecycleEventPayloadException if an event's stored payload does not parse against the
     *                                               lifecycle-payload schema (itself a ledger-integrity signal)
     */
    public Result evaluate(String tenantId, long previousExactCount, long freshExactCount,
            List<AuditLogEvent> windowEvents, long windowFromExclusiveMs, long windowToInclusiveMs)
            throws InvalidLifecycleEventPayloadException {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId must not be null");
        }
        if (windowEvents == null) {
            throw new IllegalArgumentException("windowEvents must not be null");
        }
        int eventCount = windowEvents.size();
        if (eventCount > burstCap) {
            return new Result(Status.RE_ANCHOR_REQUIRED, tenantId, previousExactCount, freshExactCount,
                    0L, previousExactCount, eventCount, windowFromExclusiveMs, windowToInclusiveMs);
        }
        List<LifecycleEventPayload> payloads = new ArrayList<>(eventCount);
        for (AuditLogEvent event : windowEvents) {
            payloads.add(LifecycleEventPayload.fromJson(event.payload));
        }
        long foldedDelta = CountDeltaInterpreter.computeDeltaForTenant(payloads, tenantId);
        long expectedCount = previousExactCount + foldedDelta;
        Status status = expectedCount == freshExactCount ? Status.MATCH : Status.DISCREPANCY;
        return new Result(status, tenantId, previousExactCount, freshExactCount, foldedDelta, expectedCount,
                eventCount, windowFromExclusiveMs, windowToInclusiveMs);
    }

    /** The outcome class of an audit window. */
    public enum Status {
        /** {@code previousExactCount + foldedDelta} equalled {@code freshExactCount}: ledger consistent. */
        MATCH,
        /** They differed: a ledger or interpreter bug localized to this window. Never served — logged. */
        DISCREPANCY,
        /** The window exceeded the burst cap and was skipped rather than folded (not a discrepancy). */
        RE_ANCHOR_REQUIRED
    }

    /** The full result of one audit window, carrying every value the discrepancy log/metric needs. */
    public static final class Result {

        public final Status status;
        public final String tenantId;
        /** Exact tenant count at the previous refresh's snapshot. */
        public final long previousExactCount;
        /** Exact tenant count at this refresh's snapshot — the value that is served. */
        public final long freshExactCount;
        /** Net per-tenant delta folded from the window ({@code 0} when the window was skipped). */
        public final long foldedDelta;
        /** {@code previousExactCount + foldedDelta}; equals {@code freshExactCount} exactly when {@code MATCH}. */
        public final long expectedCount;
        /** Number of events in the window (whether or not it was folded). */
        public final int eventCount;
        /** Window lower bound (exclusive): the previous refresh's snapshot time. */
        public final long windowFromExclusiveMs;
        /** Window upper bound (inclusive): this refresh's snapshot time. */
        public final long windowToInclusiveMs;

        Result(Status status, String tenantId, long previousExactCount, long freshExactCount, long foldedDelta,
                long expectedCount, int eventCount, long windowFromExclusiveMs, long windowToInclusiveMs) {
            this.status = status;
            this.tenantId = tenantId;
            this.previousExactCount = previousExactCount;
            this.freshExactCount = freshExactCount;
            this.foldedDelta = foldedDelta;
            this.expectedCount = expectedCount;
            this.eventCount = eventCount;
            this.windowFromExclusiveMs = windowFromExclusiveMs;
            this.windowToInclusiveMs = windowToInclusiveMs;
        }

        public boolean isDiscrepancy() {
            return status == Status.DISCREPANCY;
        }
    }
}
