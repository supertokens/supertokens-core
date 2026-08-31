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
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Single source of truth for the {@code activity_log.event_type} values that feed the last-active rollup
 * fold, so the fold query and the {@code hasUnfoldedActivitySince} existence check cannot drift apart. The
 * PostgreSQL plugin mirrors the same set in its own SQL (supertokens-postgresql-plugin#398); both halves must
 * ship together.
 *
 * <p>The set is the six {@link ActivityEventType activity events} plus the two lifecycle events that imply
 * activity: {@code user_creation} (an interactive creation counts as activity — the fold reads it in place of
 * a sign-up ping) and {@code account_linking} (credits the primary user via {@code primary_or_recipe_user_id};
 * the reconcile separately drops the linked-away recipe user's row). Everything else is excluded — notably
 * {@code user_import} (imported != active) and the retired {@code user_last_active} (no writer remains).
 */
public final class LastActiveFoldEvents {

    private LastActiveFoldEvents() {
    }

    /** The {@code event_type} values the fold credits toward a user's recency. Insertion order is preserved. */
    public static final Set<String> FOLD_EVENT_TYPES;

    static {
        Set<String> types = new LinkedHashSet<>();
        for (ActivityEventType type : ActivityEventType.values()) {
            types.add(type.getValue());
        }
        types.add(LifecycleEventType.USER_CREATION.getValue());
        types.add(LifecycleEventType.ACCOUNT_LINKING.getValue());
        FOLD_EVENT_TYPES = Collections.unmodifiableSet(types);
    }

    /**
     * @return the fold set as a SQL {@code IN}-list body, e.g. {@code 'sign_in', 'token_refresh', ...}. Safe to
     * inline into a query string: every value is a compile-time enum constant, never user input.
     */
    public static String sqlInList() {
        return FOLD_EVENT_TYPES.stream().map(v -> "'" + v + "'").collect(Collectors.joining(", "));
    }
}
