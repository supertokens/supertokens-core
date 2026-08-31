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

import io.supertokens.auditlog.lifecycle.GroupPresence;
import io.supertokens.auditlog.lifecycle.InvalidLifecycleEventPayloadException;
import io.supertokens.auditlog.lifecycle.LifecycleEventPayload;
import io.supertokens.auditlog.lifecycle.LifecycleEventType;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class LifecycleEventPayloadTest {

    private static GroupPresence group(String userId, String... tenants) {
        return new GroupPresence(userId, Arrays.asList(tenants));
    }

    /** Round-trip through JSON must preserve the exact serialization. */
    private static LifecycleEventPayload roundTrip(LifecycleEventPayload payload) throws Exception {
        String json = payload.toJson();
        LifecycleEventPayload parsed = LifecycleEventPayload.fromJson(json);
        assertEquals(payload.type, parsed.type);
        assertEquals(json, parsed.toJson());
        assertTrue(LifecycleEventPayload.isValid(json));
        LifecycleEventPayload.validate(json); // must not throw for a well-formed payload
        return parsed;
    }

    // ---------------------------------------------------------------- vocabulary

    @Test
    public void testEventTypeValuesAndLookup() {
        assertEquals("account_linking", LifecycleEventType.ACCOUNT_LINKING.getValue());
        assertEquals("account_unlinking", LifecycleEventType.ACCOUNT_UNLINKING.getValue());
        assertEquals("user_deletion", LifecycleEventType.USER_DELETION.getValue());
        assertEquals("user_group_deletion", LifecycleEventType.USER_GROUP_DELETION.getValue());
        assertEquals("tenant_association", LifecycleEventType.TENANT_ASSOCIATION.getValue());
        assertEquals("tenant_disassociation", LifecycleEventType.TENANT_DISASSOCIATION.getValue());
        assertEquals("user_creation", LifecycleEventType.USER_CREATION.getValue());
        assertEquals("user_import", LifecycleEventType.USER_IMPORT.getValue());

        // exactly eight event types in the vocabulary
        assertEquals(8, LifecycleEventType.values().length);

        for (LifecycleEventType type : LifecycleEventType.values()) {
            assertEquals(type, LifecycleEventType.fromValue(type.getValue()));
            assertTrue(LifecycleEventType.isLifecycleEvent(type.getValue()));
        }

        // activity events and unknown strings are not lifecycle events
        assertNull(LifecycleEventType.fromValue("user_last_active"));
        assertFalse(LifecycleEventType.isLifecycleEvent("user_last_active"));
        assertNull(LifecycleEventType.fromValue("not_an_event"));
        assertNull(LifecycleEventType.fromValue(null));
        assertFalse(LifecycleEventType.isLifecycleEvent(null));
    }

    // ---------------------------------------------------------------- per-type round trips

    @Test
    public void testAccountLinkingRoundTrip() throws Exception {
        LifecycleEventPayload p = LifecycleEventPayload.forAccountLinking(
                group("u1", "public", "t2"), group("u2", "public"));
        LifecycleEventPayload parsed = roundTrip(p);
        assertEquals(2, parsed.groupsBefore.size());
        assertEquals(group("u1", "public", "t2"), parsed.groupsBefore.get(0));
        assertEquals(group("u2", "public"), parsed.groupsBefore.get(1));
        assertEquals("{\"schemaVersion\":1,\"type\":\"account_linking\",\"groupsBefore\":["
                + "{\"primaryOrRecipeUserId\":\"u1\",\"tenantIds\":[\"public\",\"t2\"]},"
                + "{\"primaryOrRecipeUserId\":\"u2\",\"tenantIds\":[\"public\"]}]}", p.toJson());
    }

    @Test
    public void testAccountUnlinkingRoundTrip() throws Exception {
        LifecycleEventPayload p = LifecycleEventPayload.forAccountUnlinking(
                group("u1", "public"), group("u2", "public", "t2"));
        LifecycleEventPayload parsed = roundTrip(p);
        assertEquals(group("u1", "public"), parsed.remainingGroupAfter);
        assertEquals(group("u2", "public", "t2"), parsed.freedMemberAfter);
    }

    @Test
    public void testUserDeletionRoundTrip() throws Exception {
        LifecycleEventPayload p = LifecycleEventPayload.forUserDeletion(
                group("u1", "public", "t2"), group("u1", "public"));
        LifecycleEventPayload parsed = roundTrip(p);
        assertEquals(group("u1", "public", "t2"), parsed.groupBefore);
        assertEquals(group("u1", "public"), parsed.groupAfter);
    }

    @Test
    public void testUserGroupDeletionRoundTrip() throws Exception {
        LifecycleEventPayload p = LifecycleEventPayload.forUserGroupDeletion(group("u1", "public", "t2"));
        LifecycleEventPayload parsed = roundTrip(p);
        assertEquals(group("u1", "public", "t2"), parsed.groupBefore);
        assertNull(parsed.groupAfter);
    }

    @Test
    public void testTenantAssociationRoundTrip() throws Exception {
        LifecycleEventPayload p = LifecycleEventPayload.forTenantAssociation(group("u1", "public"), "t2");
        LifecycleEventPayload parsed = roundTrip(p);
        assertEquals(group("u1", "public"), parsed.groupBefore);
        assertEquals("t2", parsed.tenantId);
    }

    @Test
    public void testTenantDisassociationRoundTrip() throws Exception {
        LifecycleEventPayload p = LifecycleEventPayload.forTenantDisassociation(group("u1", "public", "t2"), "t2");
        LifecycleEventPayload parsed = roundTrip(p);
        assertEquals(group("u1", "public", "t2"), parsed.groupBefore);
        assertEquals("t2", parsed.tenantId);
    }

    @Test
    public void testUserCreationRoundTrip() throws Exception {
        LifecycleEventPayload p = LifecycleEventPayload.forUserCreation("public");
        LifecycleEventPayload parsed = roundTrip(p);
        assertEquals("public", parsed.tenantId);
        assertNull(parsed.groupBefore);
        assertEquals("{\"schemaVersion\":1,\"type\":\"user_creation\",\"tenantId\":\"public\"}", p.toJson());
    }

    @Test
    public void testUserImportRoundTrip() throws Exception {
        LifecycleEventPayload p = LifecycleEventPayload.forUserImport("public");
        LifecycleEventPayload parsed = roundTrip(p);
        assertEquals(LifecycleEventType.USER_IMPORT, parsed.type);
        assertEquals("public", parsed.tenantId);
        assertNull(parsed.groupBefore);
        assertEquals("{\"schemaVersion\":1,\"type\":\"user_import\",\"tenantId\":\"public\"}", p.toJson());
    }

    @Test
    public void testGroupPresenceWithNoTenantsIsAllowed() throws Exception {
        // a freed member can end up present in zero tenants; empty list is valid, null is not
        LifecycleEventPayload p = LifecycleEventPayload.forUserGroupDeletion(
                new GroupPresence("u1", Collections.<String>emptyList()));
        LifecycleEventPayload parsed = roundTrip(p);
        assertTrue(parsed.groupBefore.tenantIds.isEmpty());
    }

    // ---------------------------------------------------------------- builder guards

    @Test
    public void testBuilderRejectsNullAndEmptyArguments() {
        assertThrowsIllegalArgument(() -> LifecycleEventPayload.forAccountLinking(null, group("u1", "public")));
        assertThrowsIllegalArgument(() -> LifecycleEventPayload.forUserDeletion(group("u1"), null));
        assertThrowsIllegalArgument(() -> LifecycleEventPayload.forTenantAssociation(group("u1"), null));
        assertThrowsIllegalArgument(() -> LifecycleEventPayload.forTenantAssociation(group("u1"), ""));
        assertThrowsIllegalArgument(() -> LifecycleEventPayload.forUserCreation(null));
        assertThrowsIllegalArgument(() -> new GroupPresence(null, Collections.<String>emptyList()));
        assertThrowsIllegalArgument(() -> new GroupPresence("", Collections.<String>emptyList()));
        assertThrowsIllegalArgument(() -> new GroupPresence("u1", null));
        assertThrowsIllegalArgument(() -> new GroupPresence("u1", Arrays.asList("public", (String) null)));
        assertThrowsIllegalArgument(() -> new GroupPresence("u1", Arrays.asList("public", "")));
    }

    // ---------------------------------------------------------------- validator rejections

    @Test
    public void testRejectsMalformedTopLevel() {
        assertInvalid(null);
        assertInvalid("");
        assertInvalid("not json");
        assertInvalid("[]"); // not an object
        assertInvalid("\"a string\"");
        assertInvalid("{}"); // missing schemaVersion/type
        assertInvalid("{\"schemaVersion\":2,\"type\":\"user_creation\",\"tenantId\":\"public\"}"); // wrong version
        // schemaVersion not a number
        assertInvalid("{\"schemaVersion\":\"1\",\"type\":\"user_creation\",\"tenantId\":\"public\"}");
        assertInvalid("{\"schemaVersion\":1,\"type\":123,\"tenantId\":\"public\"}"); // type not a string
        // not a lifecycle type
        assertInvalid("{\"schemaVersion\":1,\"type\":\"user_last_active\",\"tenantId\":\"public\"}");
        assertInvalid("{\"schemaVersion\":1,\"type\":\"nonsense\",\"tenantId\":\"public\"}"); // unknown type
    }

    @Test
    public void testRejectsMissingAndUnexpectedKeys() {
        // missing the required groupBefore
        assertInvalid("{\"schemaVersion\":1,\"type\":\"user_group_deletion\"}");
        // unexpected extra key alongside a complete, otherwise-valid payload
        assertInvalid("{\"schemaVersion\":1,\"type\":\"user_creation\",\"tenantId\":\"public\",\"extra\":true}");
        // user_creation must not carry a group
        assertInvalid("{\"schemaVersion\":1,\"type\":\"user_creation\",\"tenantId\":\"public\","
                + "\"groupBefore\":{\"primaryOrRecipeUserId\":\"u1\",\"tenantIds\":[]}}");
    }

    @Test
    public void testRejectsBadGroupShape() {
        // groupBefore not an object
        assertInvalid("{\"schemaVersion\":1,\"type\":\"user_group_deletion\",\"groupBefore\":\"u1\"}");
        // missing tenantIds inside the group
        assertInvalid("{\"schemaVersion\":1,\"type\":\"user_group_deletion\","
                + "\"groupBefore\":{\"primaryOrRecipeUserId\":\"u1\"}}");
        // tenantIds not an array
        assertInvalid("{\"schemaVersion\":1,\"type\":\"user_group_deletion\","
                + "\"groupBefore\":{\"primaryOrRecipeUserId\":\"u1\",\"tenantIds\":\"public\"}}");
        // empty userId
        assertInvalid("{\"schemaVersion\":1,\"type\":\"user_group_deletion\","
                + "\"groupBefore\":{\"primaryOrRecipeUserId\":\"\",\"tenantIds\":[]}}");
        // a non-string tenant element
        assertInvalid("{\"schemaVersion\":1,\"type\":\"user_group_deletion\","
                + "\"groupBefore\":{\"primaryOrRecipeUserId\":\"u1\",\"tenantIds\":[1]}}");
        // an extra key inside the group object
        assertInvalid("{\"schemaVersion\":1,\"type\":\"user_group_deletion\","
                + "\"groupBefore\":{\"primaryOrRecipeUserId\":\"u1\",\"tenantIds\":[],\"x\":1}}");
    }

    @Test
    public void testRejectsWrongAccountLinkingArity() {
        // account_linking must carry exactly two groups
        assertInvalid("{\"schemaVersion\":1,\"type\":\"account_linking\",\"groupsBefore\":["
                + "{\"primaryOrRecipeUserId\":\"u1\",\"tenantIds\":[]}]}");
        assertInvalid("{\"schemaVersion\":1,\"type\":\"account_linking\",\"groupsBefore\":["
                + "{\"primaryOrRecipeUserId\":\"u1\",\"tenantIds\":[]},"
                + "{\"primaryOrRecipeUserId\":\"u2\",\"tenantIds\":[]},"
                + "{\"primaryOrRecipeUserId\":\"u3\",\"tenantIds\":[]}]}");
        // groupsBefore not an array
        assertInvalid("{\"schemaVersion\":1,\"type\":\"account_linking\","
                + "\"groupsBefore\":{\"primaryOrRecipeUserId\":\"u1\",\"tenantIds\":[]}}");
    }

    @Test
    public void testRejectsEmptyTenantIdScalar() {
        assertInvalid("{\"schemaVersion\":1,\"type\":\"user_creation\",\"tenantId\":\"\"}");
        assertInvalid("{\"schemaVersion\":1,\"type\":\"user_creation\",\"tenantId\":5}");
    }

    // ---------------------------------------------------------------- helpers

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static void assertThrowsIllegalArgument(ThrowingRunnable runnable) {
        try {
            runnable.run();
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // ok
        } catch (Exception e) {
            fail("expected IllegalArgumentException but got " + e);
        }
    }

    private static void assertInvalid(String payload) {
        assertFalse("expected payload to be rejected: " + payload, LifecycleEventPayload.isValid(payload));
        try {
            LifecycleEventPayload.fromJson(payload);
            fail("expected InvalidLifecycleEventPayloadException for: " + payload);
        } catch (InvalidLifecycleEventPayloadException expected) {
            // ok
        }
        try {
            LifecycleEventPayload.validate(payload);
            fail("expected validate() to reject: " + payload);
        } catch (InvalidLifecycleEventPayloadException expected) {
            // ok
        }
    }
}
