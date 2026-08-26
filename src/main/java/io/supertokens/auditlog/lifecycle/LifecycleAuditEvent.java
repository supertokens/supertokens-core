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

import io.supertokens.pluginInterface.auditlog.AuditLogEvent;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;

/**
 * Builds the {@link AuditLogEvent activity-log row} for a {@link LifecycleEventType lifecycle event}: the
 * top-level {@code recipe_user_id} / {@code primary_or_recipe_user_id} columns a downstream consumer matches
 * on, plus the schema-valid {@link LifecycleEventPayload} JSON payload.
 *
 * <p>This is the single place emit sites go through to construct a lifecycle event, so new event types are
 * added here (one factory method) rather than by hand-assembling an {@code AuditLogEvent} at each mutation
 * point. The row is written on the mutation's own connection via
 * {@code ActivityLogSQLStorage.startAuditedTransaction}, so the event and the state change it records land
 * together or not at all.
 *
 * <p>Lifecycle events are app-level and recorded against the app's public tenant (a null event tenant, which
 * the combinator resolves to {@code public}); the read-side interpreter derives per-tenant count effects from
 * the payload's group-presence lists rather than from the row's tenant.
 */
public final class LifecycleAuditEvent {

    // Mirrors the status written for the user_last_active activity rows.
    private static final String STATUS_SUCCESS = "success";

    private LifecycleAuditEvent() {
    }

    /**
     * An {@code account_linking} event: recipe user {@code recipeUserId} was linked into primary
     * {@code primaryUserId}. The payload carries both groups' before-link presence lists.
     */
    public static AuditLogEvent forAccountLinking(AppIdentifier appIdentifier, String recipeUserId,
            String primaryUserId, GroupPresence recipeGroupBefore, GroupPresence primaryGroupBefore,
            long createdAt) {
        return build(appIdentifier, recipeUserId, primaryUserId,
                LifecycleEventPayload.forAccountLinking(recipeGroupBefore, primaryGroupBefore), createdAt);
    }

    /**
     * An {@code account_unlinking} event: recipe user {@code recipeUserId} was freed from primary
     * {@code primaryUserId}. The payload carries the remaining group's and the freed member's after-unlink
     * presence lists.
     */
    public static AuditLogEvent forAccountUnlinking(AppIdentifier appIdentifier, String recipeUserId,
            String primaryUserId, GroupPresence remainingGroupAfter, GroupPresence freedMemberAfter,
            long createdAt) {
        return build(appIdentifier, recipeUserId, primaryUserId,
                LifecycleEventPayload.forAccountUnlinking(remainingGroupAfter, freedMemberAfter), createdAt);
    }

    private static AuditLogEvent build(AppIdentifier appIdentifier, String recipeUserId,
            String primaryOrRecipeUserId, LifecycleEventPayload payload, long createdAt) {
        return new AuditLogEvent(
                appIdentifier.getAppId(),
                null, // public tenant — startAuditedTransaction resolves a null event tenant to "public"
                recipeUserId,
                primaryOrRecipeUserId,
                payload.type.getValue(),
                STATUS_SUCCESS,
                null,
                null,
                createdAt,
                payload.toJson());
    }
}
