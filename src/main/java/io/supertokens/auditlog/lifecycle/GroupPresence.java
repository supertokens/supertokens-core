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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The "group-presence" of one party in a lifecycle event: the set of tenants a group (identified by its
 * {@code primary_or_recipe_user_id}) is present in at a point in time, as maintained by the existing
 * primary/recipe user-to-tenant mapping.
 *
 * <p>Crucially this is group-presence, <i>not</i> "the user's tenants": it is the exact list the read-side
 * interpreter needs to derive the count effect (e.g. member deletion is the case where after-presence is
 * not derivable from before-presence plus the identity of the deleted member — both lists must be recorded).
 */
public class GroupPresence {

    private static final String USER_ID_KEY = "primaryOrRecipeUserId";
    private static final String TENANT_IDS_KEY = "tenantIds";

    /** The {@code primary_or_recipe_user_id} identifying the group. */
    public final String primaryOrRecipeUserId;

    /** The tenants the group is present in (unmodifiable; may be empty, never null). */
    public final List<String> tenantIds;

    public GroupPresence(String primaryOrRecipeUserId, List<String> tenantIds) {
        if (primaryOrRecipeUserId == null || primaryOrRecipeUserId.isEmpty()) {
            throw new IllegalArgumentException("primaryOrRecipeUserId must be a non-empty string");
        }
        if (tenantIds == null) {
            throw new IllegalArgumentException("tenantIds must not be null");
        }
        for (String tenantId : tenantIds) {
            if (tenantId == null || tenantId.isEmpty()) {
                throw new IllegalArgumentException("tenantIds must not contain a null or empty tenant id");
            }
        }
        this.primaryOrRecipeUserId = primaryOrRecipeUserId;
        this.tenantIds = Collections.unmodifiableList(new ArrayList<>(tenantIds));
    }

    JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty(USER_ID_KEY, primaryOrRecipeUserId);
        JsonArray tenants = new JsonArray();
        for (String tenantId : tenantIds) {
            tenants.add(tenantId);
        }
        json.add(TENANT_IDS_KEY, tenants);
        return json;
    }

    static GroupPresence fromJson(JsonObject json, String fieldName) throws InvalidLifecycleEventPayloadException {
        LifecycleEventPayload.requireKeys(json, fieldName, USER_ID_KEY, TENANT_IDS_KEY);

        JsonElement userId = json.get(USER_ID_KEY);
        if (!isNonEmptyString(userId)) {
            throw new InvalidLifecycleEventPayloadException(
                    fieldName + "." + USER_ID_KEY + " must be a non-empty string");
        }

        JsonElement tenantsElement = json.get(TENANT_IDS_KEY);
        if (!tenantsElement.isJsonArray()) {
            throw new InvalidLifecycleEventPayloadException(fieldName + "." + TENANT_IDS_KEY + " must be an array");
        }
        List<String> tenantIds = new ArrayList<>();
        for (JsonElement element : tenantsElement.getAsJsonArray()) {
            if (!isNonEmptyString(element)) {
                throw new InvalidLifecycleEventPayloadException(
                        fieldName + "." + TENANT_IDS_KEY + " must contain only non-empty strings");
            }
            tenantIds.add(element.getAsString());
        }
        return new GroupPresence(userId.getAsString(), tenantIds);
    }

    private static boolean isNonEmptyString(JsonElement element) {
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()
                && !element.getAsString().isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof GroupPresence)) {
            return false;
        }
        GroupPresence that = (GroupPresence) o;
        return primaryOrRecipeUserId.equals(that.primaryOrRecipeUserId) && tenantIds.equals(that.tenantIds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(primaryOrRecipeUserId, tenantIds);
    }
}
