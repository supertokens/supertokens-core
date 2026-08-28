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

import io.supertokens.auditlog.lifecycle.CountDeltaInterpreter;
import io.supertokens.auditlog.lifecycle.CountDeltaInterpreter.FoldResult;
import io.supertokens.auditlog.lifecycle.GroupPresence;
import io.supertokens.auditlog.lifecycle.LifecycleEventPayload;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * PLAN-010 unit 2: the read-side {@link CountDeltaInterpreter}. Pure-JUnit (no {@code TestingProcess}) — the
 * interpreter does no I/O. Covers the per-event-type delta rules, the burst cap, order-independence, and a
 * property test that folds randomized mutation sequences and checks the result against a brute-force recount.
 */
public class LifecycleEventFoldTest {

    private static GroupPresence group(String userId, String... tenants) {
        return new GroupPresence(userId, Arrays.asList(tenants));
    }

    private static long delta(String tenant, LifecycleEventPayload... events) {
        return CountDeltaInterpreter.computeDeltaForTenant(Arrays.asList(events), tenant);
    }

    // ---------------------------------------------------------------- per-event-type rules

    @Test
    public void userCreationAddsOneToItsTenant() {
        assertEquals(1, delta("t1", LifecycleEventPayload.forUserCreation("t1")));
        assertEquals(0, delta("t2", LifecycleEventPayload.forUserCreation("t1")));
    }

    @Test
    public void userGroupDeletionSubtractsOnePerTenantThePresenceHeld() {
        LifecycleEventPayload event = LifecycleEventPayload.forUserGroupDeletion(group("g1", "t1", "t2"));
        assertEquals(-1, delta("t1", event));
        assertEquals(-1, delta("t2", event));
        assertEquals(0, delta("t3", event));
    }

    @Test
    public void userDeletionIsAfterMinusBefore() {
        // The group loses its presence in t2 (its last member there was deleted) but stays in t1.
        LifecycleEventPayload event = LifecycleEventPayload.forUserDeletion(
                group("g1", "t1", "t2"), group("g1", "t1"));
        assertEquals(0, delta("t1", event));
        assertEquals(-1, delta("t2", event));
    }

    @Test
    public void userDeletionThatChangesNoPresenceIsZero() {
        // A member was deleted but another member kept the group present in every tenant.
        LifecycleEventPayload event = LifecycleEventPayload.forUserDeletion(
                group("g1", "t1", "t2"), group("g1", "t1", "t2"));
        assertEquals(0, delta("t1", event));
        assertEquals(0, delta("t2", event));
    }

    @Test
    public void accountLinkingSubtractsOnePerIntersectionTenant() {
        // Two groups both present in t1 (collapse to one there); only the first is in t2, only the second in t3.
        LifecycleEventPayload event = LifecycleEventPayload.forAccountLinking(
                group("a", "t1", "t2"), group("b", "t1", "t3"));
        assertEquals(-1, delta("t1", event));
        assertEquals(0, delta("t2", event));
        assertEquals(0, delta("t3", event));
    }

    @Test
    public void accountLinkingWithDisjointGroupsIsZero() {
        LifecycleEventPayload event = LifecycleEventPayload.forAccountLinking(
                group("a", "t1"), group("b", "t2"));
        assertTrue(CountDeltaInterpreter.computeDeltas(Collections.singletonList(event)).isEmpty());
    }

    @Test
    public void accountUnlinkingAddsOnePerIntersectionTenant() {
        // After the split both the remaining group and the freed member are present in t1 (now two groups there);
        // t2 only has the remaining group, t3 only the freed member.
        LifecycleEventPayload event = LifecycleEventPayload.forAccountUnlinking(
                group("a", "t1", "t2"), group("b", "t1", "t3"));
        assertEquals(1, delta("t1", event));
        assertEquals(0, delta("t2", event));
        assertEquals(0, delta("t3", event));
    }

    @Test
    public void tenantAssociationAddsOneOnlyWhenGroupWasNotAlreadyPresent() {
        // Group newly present in t3 -> +1.
        assertEquals(1, delta("t3", LifecycleEventPayload.forTenantAssociation(group("g", "t1", "t2"), "t3")));
        // A second member joining a tenant the group already occupies -> no count change.
        assertEquals(0, delta("t1", LifecycleEventPayload.forTenantAssociation(group("g", "t1", "t2"), "t1")));
    }

    @Test
    public void tenantDisassociationSubtractsOneWhenGroupWasPresent() {
        assertEquals(-1, delta("t1", LifecycleEventPayload.forTenantDisassociation(group("g", "t1", "t2"), "t1")));
        // Disassociating from a tenant the group is not recorded as present in changes nothing.
        assertEquals(0, delta("t3", LifecycleEventPayload.forTenantDisassociation(group("g", "t1", "t2"), "t3")));
    }

    // ---------------------------------------------------------------- fold behaviour

    @Test
    public void emptyWindowFoldsToNoDeltas() {
        FoldResult result = new CountDeltaInterpreter().fold(Collections.<LifecycleEventPayload>emptyList());
        assertFalse(result.reAnchorRequired);
        assertEquals(0, result.eventCount);
        assertTrue(result.deltasByTenant().isEmpty());
        assertEquals(0, result.deltaForTenant("t1"));
    }

    @Test
    public void zeroNetTenantsAreOmitted() {
        // create in t1 then delete the whole group: net zero, tenant pruned from the map.
        List<LifecycleEventPayload> events = Arrays.asList(
                LifecycleEventPayload.forUserCreation("t1"),
                LifecycleEventPayload.forUserGroupDeletion(group("g", "t1")));
        Map<String, Long> deltas = CountDeltaInterpreter.computeDeltas(events);
        assertTrue(deltas.isEmpty());
    }

    @Test
    public void foldIsOrderIndependent() {
        List<LifecycleEventPayload> events = new ArrayList<>(Arrays.asList(
                LifecycleEventPayload.forUserCreation("t1"),
                LifecycleEventPayload.forUserCreation("t1"),
                LifecycleEventPayload.forAccountLinking(group("a", "t1"), group("b", "t1")),
                LifecycleEventPayload.forUserCreation("t2"),
                LifecycleEventPayload.forTenantAssociation(group("a", "t1"), "t2")));
        Map<String, Long> forward = CountDeltaInterpreter.computeDeltas(events);
        Collections.reverse(events);
        Map<String, Long> reversed = CountDeltaInterpreter.computeDeltas(events);
        assertEquals(forward, reversed);
    }

    // ---------------------------------------------------------------- burst cap

    @Test
    public void windowWithinTheCapIsFolded() {
        CountDeltaInterpreter interpreter = new CountDeltaInterpreter(3);
        List<LifecycleEventPayload> events = Arrays.asList(
                LifecycleEventPayload.forUserCreation("t1"),
                LifecycleEventPayload.forUserCreation("t1"),
                LifecycleEventPayload.forUserCreation("t1"));
        FoldResult result = interpreter.fold(events);
        assertFalse(result.reAnchorRequired);
        assertEquals(3, result.eventCount);
        assertEquals(3, result.deltaForTenant("t1"));
    }

    @Test
    public void windowExceedingTheCapRequestsReAnchorInsteadOfFolding() {
        CountDeltaInterpreter interpreter = new CountDeltaInterpreter(3);
        List<LifecycleEventPayload> events = Arrays.asList(
                LifecycleEventPayload.forUserCreation("t1"),
                LifecycleEventPayload.forUserCreation("t1"),
                LifecycleEventPayload.forUserCreation("t1"),
                LifecycleEventPayload.forUserCreation("t1"));
        FoldResult result = interpreter.fold(events);
        assertTrue(result.reAnchorRequired);
        assertEquals(4, result.eventCount);
        assertTrue(result.deltasByTenant().isEmpty());
        assertEquals(0, result.deltaForTenant("t1"));
    }

    @Test
    public void nonPositiveBurstCapIsRejected() {
        for (int cap : new int[]{0, -1}) {
            try {
                new CountDeltaInterpreter(cap);
                fail("expected IllegalArgumentException for burstCap=" + cap);
            } catch (IllegalArgumentException expected) {
                // ok
            }
        }
    }

    @Test
    public void nullInputsAreRejected() {
        try {
            new CountDeltaInterpreter().fold(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // ok
        }
        try {
            CountDeltaInterpreter.computeDeltaForTenant(Collections.<LifecycleEventPayload>emptyList(), null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    // ---------------------------------------------------------------- property test

    /**
     * Property: over a randomized sequence of mutations applied to an initially empty world, folding the emitted
     * lifecycle events reproduces the exact per-tenant group counts computed by brute force. Because every
     * sequence starts from an empty world (all counts zero), the net fold equals the final counts.
     */
    @Test
    public void foldMatchesBruteForceRecountOverRandomSequences() {
        String[] tenantPool = {"public", "t1", "t2", "t3"};
        for (long seed = 0; seed < 300; seed++) {
            World world = new World(tenantPool, new Random(seed));
            int steps = 5 + world.rnd.nextInt(45);
            for (int i = 0; i < steps; i++) {
                world.step();
            }

            Map<String, Long> folded = CountDeltaInterpreter.computeDeltas(world.events);
            Map<String, Long> bruteForce = world.bruteForceCounts();
            assertEquals("seed " + seed + " (" + world.events.size() + " events)", bruteForce, folded);

            // Fold below the cap agrees with the pure fold; above it, re-anchor is signalled.
            FoldResult under = new CountDeltaInterpreter(Math.max(1, world.events.size())).fold(world.events);
            assertFalse("seed " + seed, under.reAnchorRequired);
            assertEquals(bruteForce, under.deltasByTenant());
            if (world.events.size() > 1) {
                assertTrue("seed " + seed,
                        new CountDeltaInterpreter(world.events.size() - 1).fold(world.events).reAnchorRequired);
            }
        }
    }

    /**
     * A brute-force model of the count-affecting mutations, emitting the same lifecycle events the core would.
     * A group is a set of members; each member is the set of tenants it belongs to; a group is "present" in a
     * tenant iff any of its members is. Group presence (the union) is exactly what each event records.
     */
    private static final class World {
        private final String[] tenantPool;
        private final Random rnd;
        private final Map<String, List<Set<String>>> groups = new HashMap<>(); // groupId -> members (tenant sets)
        private final List<LifecycleEventPayload> events = new ArrayList<>();
        private int nextId = 0;

        World(String[] tenantPool, Random rnd) {
            this.tenantPool = tenantPool;
            this.rnd = rnd;
        }

        private String newId() {
            return "g" + (nextId++);
        }

        private String randomTenant() {
            return tenantPool[rnd.nextInt(tenantPool.length)];
        }

        private String randomGroupId() {
            List<String> ids = new ArrayList<>(groups.keySet());
            return ids.get(rnd.nextInt(ids.size()));
        }

        /** Union of a group's members' tenant sets — its group-presence. */
        private static GroupPresence presence(String id, List<Set<String>> members) {
            Set<String> union = new TreeSet<>();
            for (Set<String> member : members) {
                union.addAll(member);
            }
            return new GroupPresence(id, new ArrayList<>(union));
        }

        private GroupPresence presence(String id) {
            return presence(id, groups.get(id));
        }

        void step() {
            // Bootstrap while empty; otherwise pick a mutation, falling back to create when preconditions fail.
            int choice = groups.isEmpty() ? 0 : rnd.nextInt(7);
            switch (choice) {
                case 0: create(); break;
                case 1: link(); break;
                case 2: unlink(); break;
                case 3: memberDelete(); break;
                case 4: groupDelete(); break;
                case 5: associate(); break;
                case 6: disassociate(); break;
                default: create(); break;
            }
        }

        private void create() {
            String id = newId();
            String tenant = randomTenant();
            Set<String> member = new LinkedHashSet<>();
            member.add(tenant);
            List<Set<String>> members = new ArrayList<>();
            members.add(member);
            groups.put(id, members);
            events.add(LifecycleEventPayload.forUserCreation(tenant));
        }

        private void link() {
            if (groups.size() < 2) {
                create();
                return;
            }
            String a = randomGroupId();
            String b;
            do {
                b = randomGroupId();
            } while (b.equals(a));
            events.add(LifecycleEventPayload.forAccountLinking(presence(a), presence(b)));
            groups.get(a).addAll(groups.get(b)); // a absorbs b's members
            groups.remove(b);
        }

        private void unlink() {
            String id = groupWithAtLeast(2);
            if (id == null) {
                create();
                return;
            }
            List<Set<String>> members = groups.get(id);
            Set<String> freed = members.remove(rnd.nextInt(members.size()));
            String freedId = newId();
            GroupPresence remainingAfter = presence(id);
            List<Set<String>> freedMembers = new ArrayList<>();
            freedMembers.add(freed);
            GroupPresence freedAfter = presence(freedId, freedMembers);
            events.add(LifecycleEventPayload.forAccountUnlinking(remainingAfter, freedAfter));
            groups.put(freedId, freedMembers);
        }

        private void memberDelete() {
            String id = groupWithAtLeast(2);
            if (id == null) {
                create();
                return;
            }
            List<Set<String>> members = groups.get(id);
            GroupPresence before = presence(id);
            members.remove(rnd.nextInt(members.size()));
            GroupPresence after = presence(id);
            events.add(LifecycleEventPayload.forUserDeletion(before, after));
        }

        private void groupDelete() {
            String id = randomGroupId();
            events.add(LifecycleEventPayload.forUserGroupDeletion(presence(id)));
            groups.remove(id);
        }

        private void associate() {
            String id = randomGroupId();
            List<Set<String>> members = groups.get(id);
            Set<String> member = members.get(rnd.nextInt(members.size()));
            List<String> candidates = new ArrayList<>();
            for (String tenant : tenantPool) {
                if (!member.contains(tenant)) {
                    candidates.add(tenant);
                }
            }
            if (candidates.isEmpty()) {
                create();
                return;
            }
            String tenant = candidates.get(rnd.nextInt(candidates.size()));
            events.add(LifecycleEventPayload.forTenantAssociation(presence(id), tenant));
            member.add(tenant);
        }

        private void disassociate() {
            String id = randomGroupId();
            GroupPresence before = presence(id);
            if (before.tenantIds.isEmpty()) {
                create();
                return;
            }
            String tenant = before.tenantIds.get(rnd.nextInt(before.tenantIds.size()));
            events.add(LifecycleEventPayload.forTenantDisassociation(before, tenant));
            for (Set<String> member : groups.get(id)) { // group-level: the group leaves the tenant entirely
                member.remove(tenant);
            }
        }

        private String groupWithAtLeast(int memberCount) {
            List<String> candidates = new ArrayList<>();
            for (Map.Entry<String, List<Set<String>>> entry : groups.entrySet()) {
                if (entry.getValue().size() >= memberCount) {
                    candidates.add(entry.getKey());
                }
            }
            return candidates.isEmpty() ? null : candidates.get(rnd.nextInt(candidates.size()));
        }

        /** The exact per-tenant group counts of the final world (zero-count tenants omitted). */
        Map<String, Long> bruteForceCounts() {
            Map<String, Long> counts = new HashMap<>();
            for (String id : groups.keySet()) {
                for (String tenant : presence(id).tenantIds) {
                    counts.merge(tenant, 1L, Long::sum);
                }
            }
            counts.values().removeIf(count -> count == 0);
            return counts;
        }
    }
}
