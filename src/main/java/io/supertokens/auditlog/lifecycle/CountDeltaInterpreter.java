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

package io.supertokens.auditlog.lifecycle;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The read-side interpreter of {@link LifecycleEventType lifecycle events} (PLAN-010 unit 2): folds the events
 * committed in a time window into the net per-tenant user-count delta they imply, so a fast count can be served
 * as {@code anchor + fold(events since the anchor)} rather than by recomputing the exact count per request.
 *
 * <p>The "count" for a tenant is the number of distinct account groups (a primary user with its linked members,
 * or a standalone recipe user) present in that tenant. Each lifecycle event records the before/after
 * <b>group-presence</b> tenant lists of the parties it touched (see {@link GroupPresence}); the count effect is
 * derived here from those lists, per {@link LifecycleEventType}:
 *
 * <ul>
 *   <li>{@code USER_CREATION} / {@code USER_IMPORT} — {@code +1} for the tenant the (new or imported) user
 *       lands in. The two are folded identically here; the distinction is for the last-active rollup, not the
 *       user count.</li>
 *   <li>{@code USER_GROUP_DELETION} — {@code -1} for every tenant the group was present in.</li>
 *   <li>{@code USER_DELETION} — {@code after − before} per tenant: {@code -1} for each tenant the group left
 *       (its last member there was deleted) and {@code +1} for any it newly appears in (not expected for a
 *       deletion, handled for completeness). This is the case where after-presence is not derivable from
 *       before-presence alone, so both lists are recorded.</li>
 *   <li>{@code ACCOUNT_LINKING} — {@code -1} for every tenant in the intersection of the two groups' before
 *       lists (two groups present there collapse into one).</li>
 *   <li>{@code ACCOUNT_UNLINKING} — {@code +1} for every tenant in the intersection of the remaining group's
 *       and the freed member's after lists (one group present there splits into two).</li>
 *   <li>{@code TENANT_ASSOCIATION} — {@code +1} for the tenant, unless the group was already present there
 *       (a second member joining a tenant the group already occupies changes no count).</li>
 *   <li>{@code TENANT_DISASSOCIATION} — {@code -1} for the tenant if the group was present there before and
 *       absent after (the removed member was the group's last presence in the tenant); {@code 0} if another
 *       member keeps the group in the tenant. Like {@code USER_DELETION}, both lists are recorded because the
 *       after-presence is not derivable from the before-presence and the removed member alone.</li>
 * </ul>
 *
 * <p>Each event's delta is computed solely from its own recorded snapshots, so the fold is an order-independent
 * sum of per-event deltas — the property {@code LifecycleEventFoldTest} checks against a brute-force recount over
 * randomized mutation sequences.
 *
 * <p><b>Burst cap.</b> When the window holds more than {@link #getBurstCap()} events — a bulk import or mass
 * deletion — folding is both slow and pointless (the anchor is about to be far behind regardless), so
 * {@link #fold(List)} reports {@link FoldResult#reAnchorRequired} instead of a delta, signalling the caller to
 * trigger an immediate anchor re-count. The threshold is a policy knob; the serving path (a later unit) wires it
 * to configuration.
 *
 * <p>This class is a pure function over its inputs — it performs no I/O. Fetching the window's events from
 * storage and combining the fold with an anchor count belong to the serving path.
 */
public final class CountDeltaInterpreter {

    /**
     * Default event-count ceiling above which {@link #fold(List)} asks for a re-anchor instead of folding.
     * A recommended starting point only; the serving path is expected to make this configurable.
     */
    public static final int DEFAULT_BURST_CAP = 10_000;

    private final int burstCap;

    public CountDeltaInterpreter() {
        this(DEFAULT_BURST_CAP);
    }

    /**
     * @param burstCap the maximum number of events to fold; a window larger than this triggers a re-anchor
     *                 instead. Must be positive.
     */
    public CountDeltaInterpreter(int burstCap) {
        if (burstCap <= 0) {
            throw new IllegalArgumentException("burstCap must be positive");
        }
        this.burstCap = burstCap;
    }

    /** @return the event-count ceiling above which {@link #fold(List)} requests a re-anchor. */
    public int getBurstCap() {
        return burstCap;
    }

    /**
     * Folds a window of lifecycle events into their net per-tenant count delta, or reports that a re-anchor is
     * needed if the window exceeds the {@link #getBurstCap() burst cap}.
     *
     * @param events the lifecycle events in the window, in any order (the fold is order-independent).
     * @return a {@link FoldResult} asking for a re-anchor if {@code events.size()} exceeds the burst cap;
     * otherwise one carrying the folded per-tenant deltas.
     */
    public FoldResult fold(List<LifecycleEventPayload> events) {
        if (events == null) {
            throw new IllegalArgumentException("events must not be null");
        }
        if (events.size() > burstCap) {
            return FoldResult.reAnchor(events.size());
        }
        return FoldResult.folded(computeDeltas(events), events.size());
    }

    /**
     * The pure fold: the net per-tenant count delta implied by {@code events}, ignoring the burst cap. Tenants
     * whose net delta is zero are omitted. Package-visible callers that have already decided not to re-anchor can
     * use this directly.
     *
     * @param events the lifecycle events, in any order.
     * @return an unmodifiable map from tenant id to its net (non-zero) count delta.
     */
    public static Map<String, Long> computeDeltas(List<LifecycleEventPayload> events) {
        if (events == null) {
            throw new IllegalArgumentException("events must not be null");
        }
        Map<String, Long> deltas = new HashMap<>();
        for (LifecycleEventPayload event : events) {
            applyEvent(event, deltas);
        }
        deltas.values().removeIf(delta -> delta == 0);
        return Collections.unmodifiableMap(deltas);
    }

    /**
     * The net count delta {@code events} imply for a single tenant, ignoring the burst cap.
     *
     * @param events   the lifecycle events, in any order.
     * @param tenantId the tenant to compute the delta for.
     */
    public static long computeDeltaForTenant(List<LifecycleEventPayload> events, String tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId must not be null");
        }
        return computeDeltas(events).getOrDefault(tenantId, 0L);
    }

    private static void applyEvent(LifecycleEventPayload event, Map<String, Long> deltas) {
        switch (event.type) {
            case USER_CREATION:
            case USER_IMPORT:
                // A bulk-imported user is counted toward totals exactly like an interactively created one:
                // a +1 in the tenant it lands in. (The type distinction matters only to the last-active
                // rollup, which folds user_creation but excludes user_import, and never enters this fold.)
                add(deltas, event.tenantId, 1);
                break;
            case USER_GROUP_DELETION:
                for (String tenantId : new HashSet<>(event.groupBefore.tenantIds)) {
                    add(deltas, tenantId, -1);
                }
                break;
            case USER_DELETION: {
                Set<String> before = new HashSet<>(event.groupBefore.tenantIds);
                Set<String> after = new HashSet<>(event.groupAfter.tenantIds);
                for (String tenantId : before) {
                    if (!after.contains(tenantId)) {
                        add(deltas, tenantId, -1);
                    }
                }
                for (String tenantId : after) {
                    if (!before.contains(tenantId)) {
                        add(deltas, tenantId, 1);
                    }
                }
                break;
            }
            case ACCOUNT_LINKING: {
                Set<String> firstGroup = new HashSet<>(event.groupsBefore.get(0).tenantIds);
                for (String tenantId : new HashSet<>(event.groupsBefore.get(1).tenantIds)) {
                    if (firstGroup.contains(tenantId)) {
                        add(deltas, tenantId, -1);
                    }
                }
                break;
            }
            case ACCOUNT_UNLINKING: {
                Set<String> remaining = new HashSet<>(event.remainingGroupAfter.tenantIds);
                for (String tenantId : new HashSet<>(event.freedMemberAfter.tenantIds)) {
                    if (remaining.contains(tenantId)) {
                        add(deltas, tenantId, 1);
                    }
                }
                break;
            }
            case TENANT_ASSOCIATION:
                if (!event.groupBefore.tenantIds.contains(event.tenantId)) {
                    add(deltas, event.tenantId, 1);
                }
                break;
            case TENANT_DISASSOCIATION:
                // Removing one member's tenant mapping decrements the count only if the group actually left the
                // tenant — it was present before and, with that member gone, is absent after. A multi-member
                // group that still has a member in the tenant stays present, so the count is unchanged.
                if (event.groupBefore.tenantIds.contains(event.tenantId)
                        && !event.groupAfter.tenantIds.contains(event.tenantId)) {
                    add(deltas, event.tenantId, -1);
                }
                break;
            default:
                // Unreachable: every LifecycleEventType is handled above.
                throw new IllegalStateException("unhandled lifecycle event type: " + event.type);
        }
    }

    private static void add(Map<String, Long> deltas, String tenantId, long delta) {
        deltas.merge(tenantId, delta, Long::sum);
    }

    /**
     * The outcome of folding a window: either the net per-tenant deltas, or a signal that the window was too
     * large to fold and the caller should trigger an anchor re-count instead.
     */
    public static final class FoldResult {

        /** Whether the window exceeded the burst cap, so the caller should re-anchor rather than fold. */
        public final boolean reAnchorRequired;

        /** The number of events in the window (whether or not it was folded). */
        public final int eventCount;

        private final Map<String, Long> deltaByTenant;

        private FoldResult(boolean reAnchorRequired, int eventCount, Map<String, Long> deltaByTenant) {
            this.reAnchorRequired = reAnchorRequired;
            this.eventCount = eventCount;
            this.deltaByTenant = deltaByTenant;
        }

        static FoldResult folded(Map<String, Long> deltaByTenant, int eventCount) {
            return new FoldResult(false, eventCount, deltaByTenant);
        }

        static FoldResult reAnchor(int eventCount) {
            return new FoldResult(true, eventCount, Collections.emptyMap());
        }

        /**
         * @return the net per-tenant deltas (tenants with a zero net delta omitted). Empty when
         * {@link #reAnchorRequired} is set.
         */
        public Map<String, Long> deltasByTenant() {
            return deltaByTenant;
        }

        /**
         * @return the net count delta for a single tenant, or {@code 0} if the tenant is unaffected. Always
         * {@code 0} when {@link #reAnchorRequired} is set.
         */
        public long deltaForTenant(String tenantId) {
            if (tenantId == null) {
                throw new IllegalArgumentException("tenantId must not be null");
            }
            return deltaByTenant.getOrDefault(tenantId, 0L);
        }
    }
}
