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

/**
 * The vocabulary of "lifecycle" events written to the activity log — the count-affecting mutations
 * whose effect on user counts cannot be reconstructed from current state after the fact (deletions and
 * link/unlink rewrites destroy their own evidence), so they are recorded as they happen.
 *
 * <p>These events form a distinct class from "activity" events (sign-ins, refreshes): lifecycle events
 * are mandatory and unthrottled (written in the mutation's transaction, exempt from the activity-log
 * verbosity flag which gates activity pings only), whereas activity events are flag-gated/throttleable.
 *
 * <p>The string {@link #getValue() value} is what lands in the {@code activity_log.event_type} column;
 * the structured JSON that accompanies each event lives in {@link LifecycleEventPayload}. This class
 * defines the vocabulary only — it has no emit sites.
 */
public enum LifecycleEventType {

    /** Two account groups are linked into one. */
    ACCOUNT_LINKING("account_linking"),

    /** A member is unlinked from its account group, becoming a standalone group again. */
    ACCOUNT_UNLINKING("account_unlinking"),

    /** A single member (recipe user) is deleted from a group; the group survives. */
    USER_DELETION("user_deletion"),

    /** An entire group (a primary user and all its linked members) is deleted. */
    USER_GROUP_DELETION("user_group_deletion"),

    /** A group is associated with (added to) a tenant. */
    TENANT_ASSOCIATION("tenant_association"),

    /** A group is disassociated from (removed from) a tenant. */
    TENANT_DISASSOCIATION("tenant_disassociation"),

    /** A new user is created in a tenant. */
    USER_CREATION("user_creation"),

    /**
     * A user is brought in by bulk import rather than created interactively. Counted toward user totals
     * exactly like {@link #USER_CREATION} (a +1 in the tenant it lands in), but recorded under its own
     * type so an imported user is distinguishable from an organically created one and is excluded from the
     * last-active rollup (which an interactive sign-up may separately feed, but an import must not: an
     * imported user is present, not active).
     */
    USER_IMPORT("user_import");

    private final String value;

    LifecycleEventType(String value) {
        this.value = value;
    }

    /** The string stored in the {@code activity_log.event_type} column. */
    public String getValue() {
        return value;
    }

    /**
     * @return the lifecycle event type with the given {@code event_type} value, or {@code null} if the
     * value is not a lifecycle event (e.g. an activity event such as {@code user_last_active}).
     */
    public static LifecycleEventType fromValue(String value) {
        if (value != null) {
            for (LifecycleEventType type : values()) {
                if (type.value.equals(value)) {
                    return type;
                }
            }
        }
        return null;
    }

    /** @return whether the given {@code event_type} value denotes a lifecycle event. */
    public static boolean isLifecycleEvent(String value) {
        return fromValue(value) != null;
    }
}
