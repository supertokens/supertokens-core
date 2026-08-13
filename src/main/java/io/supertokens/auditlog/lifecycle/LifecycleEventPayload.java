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
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The structured payload accompanying a {@link LifecycleEventType lifecycle event} in the activity log,
 * stored as JSON in the {@code activity_log.payload} (JSONB) column.
 *
 * <p>Payloads carry the before/after <b>group-presence tenant lists</b> (see {@link GroupPresence}) for
 * every party involved in the mutation — never a pre-computed count delta. The count effect is left to be
 * derived read-side by a single interpreter, so writers only record checkable facts and derivation bugs
 * stay fixable retroactively over the window the raw facts persist. What each type carries:
 *
 * <ul>
 *   <li>{@code ACCOUNT_LINKING} — both groups' before-lists (effect: −1 per tenant in their intersection).</li>
 *   <li>{@code ACCOUNT_UNLINKING} — the remaining group's and the freed member's after-lists
 *       (effect: +1 per tenant in their intersection).</li>
 *   <li>{@code USER_DELETION} — the group's before and after lists (the case where after-presence is not
 *       derivable from before-presence plus the deleted member).</li>
 *   <li>{@code USER_GROUP_DELETION} — the group's before list only.</li>
 *   <li>{@code TENANT_ASSOCIATION} / {@code TENANT_DISASSOCIATION} — the group's before list plus the tenant.</li>
 *   <li>{@code USER_CREATION} — the tenant.</li>
 * </ul>
 *
 * <p>Construct payloads through the {@code for*} factory methods (the one builder emit sites use) and read
 * them back with {@link #fromJson(String)}, which validates the JSON against the schema for its type.
 */
public class LifecycleEventPayload {

    /** Schema version, stored under {@code "v"} so the payload shape can evolve. */
    public static final int SCHEMA_VERSION = 1;

    private static final String V_KEY = "v";
    private static final String TYPE_KEY = "type";
    private static final String GROUPS_BEFORE_KEY = "groupsBefore";
    private static final String REMAINING_GROUP_AFTER_KEY = "remainingGroupAfter";
    private static final String FREED_MEMBER_AFTER_KEY = "freedMemberAfter";
    private static final String GROUP_BEFORE_KEY = "groupBefore";
    private static final String GROUP_AFTER_KEY = "groupAfter";
    private static final String TENANT_ID_KEY = "tenantId";

    public final LifecycleEventType type;

    /** {@code ACCOUNT_LINKING}: both groups' before-lists (exactly two); otherwise {@code null}. */
    public final List<GroupPresence> groupsBefore;
    /** {@code ACCOUNT_UNLINKING}: the remaining group's after-list; otherwise {@code null}. */
    public final GroupPresence remainingGroupAfter;
    /** {@code ACCOUNT_UNLINKING}: the freed member's after-list; otherwise {@code null}. */
    public final GroupPresence freedMemberAfter;
    /** {@code USER_DELETION} / {@code USER_GROUP_DELETION} / {@code TENANT_(DIS)ASSOCIATION}: before-list. */
    public final GroupPresence groupBefore;
    /** {@code USER_DELETION}: the group's after-list; otherwise {@code null}. */
    public final GroupPresence groupAfter;
    /** {@code TENANT_ASSOCIATION} / {@code TENANT_DISASSOCIATION} / {@code USER_CREATION}: the tenant. */
    public final String tenantId;

    private LifecycleEventPayload(LifecycleEventType type, List<GroupPresence> groupsBefore,
            GroupPresence remainingGroupAfter, GroupPresence freedMemberAfter, GroupPresence groupBefore,
            GroupPresence groupAfter, String tenantId) {
        this.type = type;
        this.groupsBefore = groupsBefore == null ? null
                : Collections.unmodifiableList(new ArrayList<>(groupsBefore));
        this.remainingGroupAfter = remainingGroupAfter;
        this.freedMemberAfter = freedMemberAfter;
        this.groupBefore = groupBefore;
        this.groupAfter = groupAfter;
        this.tenantId = tenantId;
    }

    // ---- Builder: one factory per event type, so emit sites always construct a schema-valid payload ----

    public static LifecycleEventPayload forAccountLinking(GroupPresence firstGroupBefore,
            GroupPresence secondGroupBefore) {
        requireNonNull(firstGroupBefore, "firstGroupBefore");
        requireNonNull(secondGroupBefore, "secondGroupBefore");
        return new LifecycleEventPayload(LifecycleEventType.ACCOUNT_LINKING,
                Arrays.asList(firstGroupBefore, secondGroupBefore), null, null, null, null, null);
    }

    public static LifecycleEventPayload forAccountUnlinking(GroupPresence remainingGroupAfter,
            GroupPresence freedMemberAfter) {
        requireNonNull(remainingGroupAfter, "remainingGroupAfter");
        requireNonNull(freedMemberAfter, "freedMemberAfter");
        return new LifecycleEventPayload(LifecycleEventType.ACCOUNT_UNLINKING, null, remainingGroupAfter,
                freedMemberAfter, null, null, null);
    }

    public static LifecycleEventPayload forUserDeletion(GroupPresence groupBefore, GroupPresence groupAfter) {
        requireNonNull(groupBefore, "groupBefore");
        requireNonNull(groupAfter, "groupAfter");
        return new LifecycleEventPayload(LifecycleEventType.USER_DELETION, null, null, null, groupBefore,
                groupAfter, null);
    }

    public static LifecycleEventPayload forUserGroupDeletion(GroupPresence groupBefore) {
        requireNonNull(groupBefore, "groupBefore");
        return new LifecycleEventPayload(LifecycleEventType.USER_GROUP_DELETION, null, null, null, groupBefore,
                null, null);
    }

    public static LifecycleEventPayload forTenantAssociation(GroupPresence groupBefore, String tenantId) {
        requireNonNull(groupBefore, "groupBefore");
        requireNonEmpty(tenantId, "tenantId");
        return new LifecycleEventPayload(LifecycleEventType.TENANT_ASSOCIATION, null, null, null, groupBefore,
                null, tenantId);
    }

    public static LifecycleEventPayload forTenantDisassociation(GroupPresence groupBefore, String tenantId) {
        requireNonNull(groupBefore, "groupBefore");
        requireNonEmpty(tenantId, "tenantId");
        return new LifecycleEventPayload(LifecycleEventType.TENANT_DISASSOCIATION, null, null, null, groupBefore,
                null, tenantId);
    }

    public static LifecycleEventPayload forUserCreation(String tenantId) {
        requireNonEmpty(tenantId, "tenantId");
        return new LifecycleEventPayload(LifecycleEventType.USER_CREATION, null, null, null, null, null, tenantId);
    }

    // ---- Serialization ----

    /** @return the JSON string to store in the {@code activity_log.payload} column. */
    public String toJson() {
        JsonObject json = new JsonObject();
        json.addProperty(V_KEY, SCHEMA_VERSION);
        json.addProperty(TYPE_KEY, type.getValue());
        switch (type) {
            case ACCOUNT_LINKING: {
                JsonArray array = new JsonArray();
                for (GroupPresence group : groupsBefore) {
                    array.add(group.toJson());
                }
                json.add(GROUPS_BEFORE_KEY, array);
                break;
            }
            case ACCOUNT_UNLINKING:
                json.add(REMAINING_GROUP_AFTER_KEY, remainingGroupAfter.toJson());
                json.add(FREED_MEMBER_AFTER_KEY, freedMemberAfter.toJson());
                break;
            case USER_DELETION:
                json.add(GROUP_BEFORE_KEY, groupBefore.toJson());
                json.add(GROUP_AFTER_KEY, groupAfter.toJson());
                break;
            case USER_GROUP_DELETION:
                json.add(GROUP_BEFORE_KEY, groupBefore.toJson());
                break;
            case TENANT_ASSOCIATION:
            case TENANT_DISASSOCIATION:
                json.add(GROUP_BEFORE_KEY, groupBefore.toJson());
                json.addProperty(TENANT_ID_KEY, tenantId);
                break;
            case USER_CREATION:
                json.addProperty(TENANT_ID_KEY, tenantId);
                break;
        }
        return json.toString();
    }

    /**
     * Parses and validates a stored payload against the schema for its declared type.
     *
     * @throws InvalidLifecycleEventPayloadException if the JSON is malformed, the schema version is
     *                                               unrecognised, the type is not a lifecycle event, or a
     *                                               field is missing / unexpected / of the wrong type.
     */
    public static LifecycleEventPayload fromJson(String payload) throws InvalidLifecycleEventPayloadException {
        if (payload == null) {
            throw new InvalidLifecycleEventPayloadException("payload must not be null");
        }
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(payload);
        } catch (JsonParseException e) {
            throw new InvalidLifecycleEventPayloadException("payload is not valid JSON: " + e.getMessage());
        }
        if (parsed == null || !parsed.isJsonObject()) {
            throw new InvalidLifecycleEventPayloadException("payload must be a JSON object");
        }
        JsonObject json = parsed.getAsJsonObject();

        JsonElement version = json.get(V_KEY);
        if (version == null || !version.isJsonPrimitive() || !version.getAsJsonPrimitive().isNumber()
                || version.getAsInt() != SCHEMA_VERSION) {
            throw new InvalidLifecycleEventPayloadException(
                    "payload \"" + V_KEY + "\" must be " + SCHEMA_VERSION);
        }

        JsonElement typeElement = json.get(TYPE_KEY);
        if (typeElement == null || !typeElement.isJsonPrimitive() || !typeElement.getAsJsonPrimitive().isString()) {
            throw new InvalidLifecycleEventPayloadException("payload \"" + TYPE_KEY + "\" must be a string");
        }
        LifecycleEventType type = LifecycleEventType.fromValue(typeElement.getAsString());
        if (type == null) {
            throw new InvalidLifecycleEventPayloadException(
                    "payload \"" + TYPE_KEY + "\" is not a lifecycle event: " + typeElement.getAsString());
        }

        switch (type) {
            case ACCOUNT_LINKING: {
                requireKeys(json, "payload", V_KEY, TYPE_KEY, GROUPS_BEFORE_KEY);
                JsonElement groups = json.get(GROUPS_BEFORE_KEY);
                if (!groups.isJsonArray() || groups.getAsJsonArray().size() != 2) {
                    throw new InvalidLifecycleEventPayloadException(
                            "payload \"" + GROUPS_BEFORE_KEY + "\" must be an array of exactly two groups");
                }
                List<GroupPresence> groupsBefore = new ArrayList<>();
                int i = 0;
                for (JsonElement element : groups.getAsJsonArray()) {
                    groupsBefore.add(parseGroup(element, GROUPS_BEFORE_KEY + "[" + i + "]"));
                    i++;
                }
                return forAccountLinking(groupsBefore.get(0), groupsBefore.get(1));
            }
            case ACCOUNT_UNLINKING:
                requireKeys(json, "payload", V_KEY, TYPE_KEY, REMAINING_GROUP_AFTER_KEY, FREED_MEMBER_AFTER_KEY);
                return forAccountUnlinking(
                        parseGroup(json.get(REMAINING_GROUP_AFTER_KEY), REMAINING_GROUP_AFTER_KEY),
                        parseGroup(json.get(FREED_MEMBER_AFTER_KEY), FREED_MEMBER_AFTER_KEY));
            case USER_DELETION:
                requireKeys(json, "payload", V_KEY, TYPE_KEY, GROUP_BEFORE_KEY, GROUP_AFTER_KEY);
                return forUserDeletion(parseGroup(json.get(GROUP_BEFORE_KEY), GROUP_BEFORE_KEY),
                        parseGroup(json.get(GROUP_AFTER_KEY), GROUP_AFTER_KEY));
            case USER_GROUP_DELETION:
                requireKeys(json, "payload", V_KEY, TYPE_KEY, GROUP_BEFORE_KEY);
                return forUserGroupDeletion(parseGroup(json.get(GROUP_BEFORE_KEY), GROUP_BEFORE_KEY));
            case TENANT_ASSOCIATION:
                requireKeys(json, "payload", V_KEY, TYPE_KEY, GROUP_BEFORE_KEY, TENANT_ID_KEY);
                return forTenantAssociation(parseGroup(json.get(GROUP_BEFORE_KEY), GROUP_BEFORE_KEY),
                        parseTenantId(json));
            case TENANT_DISASSOCIATION:
                requireKeys(json, "payload", V_KEY, TYPE_KEY, GROUP_BEFORE_KEY, TENANT_ID_KEY);
                return forTenantDisassociation(parseGroup(json.get(GROUP_BEFORE_KEY), GROUP_BEFORE_KEY),
                        parseTenantId(json));
            case USER_CREATION:
                requireKeys(json, "payload", V_KEY, TYPE_KEY, TENANT_ID_KEY);
                return forUserCreation(parseTenantId(json));
            default:
                // Unreachable: every LifecycleEventType is handled above.
                throw new InvalidLifecycleEventPayloadException("unhandled lifecycle event type: " + type);
        }
    }

    /** @return whether {@code payload} is a schema-valid lifecycle-event payload. */
    public static boolean isValid(String payload) {
        try {
            fromJson(payload);
            return true;
        } catch (InvalidLifecycleEventPayloadException e) {
            return false;
        }
    }

    // ---- Validation helpers (shared with GroupPresence) ----

    /**
     * Asserts that {@code json} has exactly {@code expectedKeys} — no missing keys, no unexpected extras —
     * so the schema stays closed and a stray field is caught rather than silently ignored.
     */
    static void requireKeys(JsonObject json, String context, String... expectedKeys)
            throws InvalidLifecycleEventPayloadException {
        Set<String> expected = new HashSet<>(Arrays.asList(expectedKeys));
        for (String key : expectedKeys) {
            if (!json.has(key)) {
                throw new InvalidLifecycleEventPayloadException(context + " is missing required key \"" + key + "\"");
            }
        }
        for (String key : json.keySet()) {
            if (!expected.contains(key)) {
                throw new InvalidLifecycleEventPayloadException(context + " has unexpected key \"" + key + "\"");
            }
        }
    }

    private static GroupPresence parseGroup(JsonElement element, String fieldName)
            throws InvalidLifecycleEventPayloadException {
        if (element == null || !element.isJsonObject()) {
            throw new InvalidLifecycleEventPayloadException(fieldName + " must be an object");
        }
        return GroupPresence.fromJson(element.getAsJsonObject(), fieldName);
    }

    private static String parseTenantId(JsonObject json) throws InvalidLifecycleEventPayloadException {
        JsonElement element = json.get(TENANT_ID_KEY);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()
                || element.getAsString().isEmpty()) {
            throw new InvalidLifecycleEventPayloadException(
                    "payload \"" + TENANT_ID_KEY + "\" must be a non-empty string");
        }
        return element.getAsString();
    }

    private static void requireNonNull(Object value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
    }

    private static void requireNonEmpty(String value, String name) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(name + " must be a non-empty string");
        }
    }
}
