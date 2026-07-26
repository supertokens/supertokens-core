/*
 *    Copyright (c) 2024, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.exceptions;

/**
 * Classifies a refresh-token reuse detected on the CDI >= 5.5 refresh path (PLAN-002 decision 4).
 *
 * <ul>
 *   <li>{@link #RECENT_PREV}: the presented token equals {@code prev} but the grace window has
 *       expired (attacker replay or a benign late retry after a lost response - indistinguishable
 *       per event).</li>
 *   <li>{@link #ORPHANED_BRANCH}: the presented token's own parent hash equals {@code prev} - it is
 *       a child displaced by an in-window re-rotation (grace hit).</li>
 *   <li>{@link #STALE_LINEAGE}: older / unknown lineage - unambiguously hostile.</li>
 * </ul>
 *
 * The session is revoked regardless of subtype; the subtype only affects how the reuse is reported
 * ({@code recent_token_reuse_behaviour} applies to RECENT_PREV and ORPHANED_BRANCH only).
 */
public enum RefreshTokenReuseSubtype {
    RECENT_PREV,
    ORPHANED_BRANCH,
    STALE_LINEAGE
}
