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

    // Mirrors the status written for the semantic activity rows.
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

    /**
     * A {@code user_deletion} event: a single member (recipe user) {@code recipeUserId} was deleted from the
     * group identified by {@code groupUserId}, which survives. The payload carries the group's before and after
     * presence lists — member deletion is the case where after-presence is not derivable from before-presence
     * plus the identity of the deleted member, so both are recorded.
     */
    public static AuditLogEvent forUserDeletion(AppIdentifier appIdentifier, String recipeUserId,
            String groupUserId, GroupPresence groupBefore, GroupPresence groupAfter, long createdAt) {
        return build(appIdentifier, recipeUserId, groupUserId,
                LifecycleEventPayload.forUserDeletion(groupBefore, groupAfter), createdAt);
    }

    /**
     * A {@code user_group_deletion} event: the entire group identified by {@code groupUserId} (a primary user
     * and all its linked members, or a standalone recipe user) was deleted. The payload carries the group's
     * before-list only — after deletion the group is present in no tenants.
     */
    public static AuditLogEvent forUserGroupDeletion(AppIdentifier appIdentifier, String groupUserId,
            GroupPresence groupBefore, long createdAt) {
        return build(appIdentifier, groupUserId, groupUserId,
                LifecycleEventPayload.forUserGroupDeletion(groupBefore), createdAt);
    }

    /**
     * A {@code tenant_association} event: the group identified by {@code groupUserId} (via member
     * {@code recipeUserId}) was added to tenant {@code tenantId}. The payload carries the group's presence list
     * before the association plus the tenant it was added to.
     */
    public static AuditLogEvent forTenantAssociation(AppIdentifier appIdentifier, String recipeUserId,
            String groupUserId, GroupPresence groupBefore, String tenantId, long createdAt) {
        return build(appIdentifier, recipeUserId, groupUserId,
                LifecycleEventPayload.forTenantAssociation(groupBefore, tenantId), createdAt);
    }

    /**
     * A {@code tenant_disassociation} event: the member {@code recipeUserId} of the group identified by
     * {@code groupUserId} was removed from tenant {@code tenantId}. The payload carries the group's presence
     * list before and after the disassociation plus the tenant it was removed from — removing one member's
     * mapping only drops the group from the tenant if no other member remains there, so the after-list is
     * recorded rather than assumed.
     */
    public static AuditLogEvent forTenantDisassociation(AppIdentifier appIdentifier, String recipeUserId,
            String groupUserId, GroupPresence groupBefore, GroupPresence groupAfter, String tenantId,
            long createdAt) {
        return build(appIdentifier, recipeUserId, groupUserId,
                LifecycleEventPayload.forTenantDisassociation(groupBefore, groupAfter, tenantId), createdAt);
    }

    /**
     * A {@code user_creation} event: a new recipe user {@code recipeUserId} was created in tenant
     * {@code tenantId}. A freshly created user is its own group, so it is recorded against itself as both the
     * recipe user and the group; the payload carries only the tenant it was created in (the group's presence
     * afterwards is exactly that single tenant, derivable read-side without a stored list).
     */
    public static AuditLogEvent forUserCreation(AppIdentifier appIdentifier, String recipeUserId,
            String tenantId, long createdAt) {
        return build(appIdentifier, recipeUserId, recipeUserId,
                LifecycleEventPayload.forUserCreation(tenantId), createdAt);
    }

    /**
     * A {@code user_import} event: the user identified by {@code groupUserId} was brought in by bulk import
     * and lands in tenant {@code tenantId}. Counted toward user totals exactly like {@code user_creation}, so
     * (like a freshly created user) the group is recorded against itself as both the recipe user and the
     * group and the payload carries only the tenant. The distinct type is what lets a downstream consumer
     * exclude imports from the last-active rollup while still counting them.
     */
    public static AuditLogEvent forUserImport(AppIdentifier appIdentifier, String groupUserId,
            String tenantId, long createdAt) {
        return build(appIdentifier, groupUserId, groupUserId,
                LifecycleEventPayload.forUserImport(tenantId), createdAt);
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
