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
 * The vocabulary of "activity" events written to the activity log — user-initiated interactions that mark a
 * user as recently active. Unlike {@link LifecycleEventType lifecycle events} (count-affecting mutations
 * recorded in their own transaction), activity events are best-effort pings emitted outside any mutation
 * transaction, and feed the last-active rollup fold: {@code last_active(user) = MAX(created_at)} over these
 * events (plus the activity-implying lifecycle events {@code user_creation} and {@code account_linking}).
 *
 * <p>The string {@link #getValue() value} is what lands in the {@code activity_log.event_type} column. These
 * replaced the single synthetic {@code user_last_active} event, which is retired: the concrete interaction
 * (a sign-in, a refresh, a session create, ...) is now recorded directly and the fold counts it.
 *
 * <p>{@link #isThrottled() Throttling}: {@code sign_in} and {@code sign_out} emit unthrottled (low-volume,
 * user-initiated, audit-meaningful); the rest keep the shared 5-minute per-{@code (app, user)} throttle that
 * caps high-volume activity-log inserts. Either way every emit refreshes the recency cache
 * ({@code ActiveUsers.wasRecentlyActive} keeps its meaning).
 */
public enum ActivityEventType {

    /** An interactive sign-in of an existing user (emailpassword / webauthn / thirdparty / passwordless). */
    SIGN_IN("sign_in", false),

    /** A session was explicitly revoked for a user. */
    SIGN_OUT("sign_out", false),

    /** A session's tokens were refreshed. */
    TOKEN_REFRESH("token_refresh", true),

    /** A new session was created for a user. */
    SESSION_CREATE("session_create", true),

    /** An OAuth token exchange resolved to a session user. */
    OAUTH_TOKEN_EXCHANGE("oauth_token_exchange", true),

    /** An OAuth authorization request resolved to a session user. */
    OAUTH_AUTHORIZE("oauth_authorize", true);

    private final String value;
    private final boolean throttled;

    ActivityEventType(String value, boolean throttled) {
        this.value = value;
        this.throttled = throttled;
    }

    /** The string stored in the {@code activity_log.event_type} column. */
    public String getValue() {
        return value;
    }

    /**
     * @return whether emits of this type are subject to the shared 5-minute per-{@code (app, user)} throttle.
     * {@code sign_in}/{@code sign_out} return {@code false} (always emitted); the rest return {@code true}.
     */
    public boolean isThrottled() {
        return throttled;
    }
}
