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

package io.supertokens.test.saml;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.supertokens.saml.SAMLAttributeMapping;
import io.supertokens.saml.exceptions.InvalidAttributeMappingException;
import org.junit.Test;

import static io.supertokens.saml.SAMLAttributeMapping.*;
import static org.junit.Assert.*;

/**
 * The attribute-mapping resolver and validator on their own, against hand-built claims in the shape
 * SAML.extractAssertionClaims produces (each claim an array; NameID as extracted, possibly blank or null).
 */
public class SAMLAttributeMappingResolverTest {

    private static JsonObject claims(Object... keyValues) {
        JsonObject c = new JsonObject();
        for (int i = 0; i < keyValues.length; i += 2) {
            JsonArray values = new JsonArray();
            Object v = keyValues[i + 1];
            if (v instanceof String[]) {
                for (String s : (String[]) v) {
                    values.add(s);
                }
            } else if (v == null) {
                values.add(JsonNull.INSTANCE);
            } else {
                values.add((String) v);
            }
            c.add((String) keyValues[i], values);
        }
        return c;
    }

    private static JsonObject json(String s) {
        return JsonParser.parseString(s).getAsJsonObject();
    }

    @Test
    public void defaultsAreTodaysRules() {
        assertEquals(java.util.List.of(NAME_ID, OBJECT_IDENTIFIER_CLAIM, NAME_CLAIM), DEFAULT_USER_ID);
        assertEquals(java.util.List.of(EMAIL_ADDRESS_CLAIM, "mail", "email", NAME_ID), DEFAULT_EMAIL);
        assertTrue(DEFAULT_FIRST_NAME.isEmpty());
        assertTrue(DEFAULT_LAST_NAME.isEmpty());

        JsonObject c = claims(NAME_ID, "nid", OBJECT_IDENTIFIER_CLAIM, "oid", NAME_CLAIM, "name",
                "mail", "m@example.com", "email", "e@example.com", "givenname", "Ada");
        assertEquals("nid", resolve(USER_ID, c, null));
        assertEquals("m@example.com", resolve(EMAIL, c, null));
        assertNull(resolve(FIRST_NAME, c, null));
        assertNull(resolve(LAST_NAME, c, null));

        assertEquals("oid", resolve(USER_ID, claims(OBJECT_IDENTIFIER_CLAIM, "oid", NAME_CLAIM, "name"), null));
        assertEquals("name", resolve(USER_ID, claims(NAME_CLAIM, "name"), null));
        assertEquals("a@example.com", resolve(EMAIL, claims(EMAIL_ADDRESS_CLAIM, "a@example.com",
                "mail", "m@example.com"), null));
        assertEquals("e@example.com", resolve(EMAIL, claims("email", "e@example.com", NAME_ID, "n@example.com"),
                null));
    }

    @Test
    public void mappingListIsWalkedInOrderAndMissingFieldsUseDefaults() {
        JsonObject mapping = json("{\"userId\": [\"id\", \"uid\", \"NameID\"], \"firstName\": [\"fn\"]}");
        assertEquals("id-1", resolve(USER_ID, claims("uid", "uid-1", "id", "id-1", NAME_ID, "nid"), mapping));
        assertEquals("uid-1", resolve(USER_ID, claims("uid", "uid-1", NAME_ID, "nid"), mapping));
        assertEquals("nid", resolve(USER_ID, claims(NAME_ID, "nid"), mapping));
        // email is not in the mapping, so the default list applies.
        assertEquals("m@example.com", resolve(EMAIL, claims("mail", "m@example.com"), mapping));
        assertEquals("Ada", resolve(FIRST_NAME, claims("fn", "Ada"), mapping));
        assertNull(resolve(LAST_NAME, claims("ln", "L"), mapping));
    }

    @Test
    public void missingClaimsResolveToNull() {
        JsonObject mapping = json("{\"userId\": [\"id\"], \"email\": [\"mail\"]}");
        assertNull(resolve(USER_ID, claims("other", "x"), mapping));
        assertNull(resolve(EMAIL, claims(), mapping));
        assertNull(resolve(USER_ID, new JsonObject(), null));
        assertNull(resolve(USER_ID, null, mapping));
    }

    @Test
    public void namesMatchExactly() {
        JsonObject mapping = json("{\"userId\": [\"ID\", \"nameid\"]}");
        assertNull(resolve(USER_ID, claims("id", "x", NAME_ID, "nid"), mapping));
    }

    @Test
    public void firstValueWins() {
        JsonObject mapping = json("{\"userId\": [\"groups\"]}");
        assertEquals("first", resolve(USER_ID, claims("groups", new String[]{"first", "second"}), mapping));
    }

    @Test
    public void nameIDMustContainAtForEmail() {
        JsonObject mapping = json("{\"email\": [\"NameID\", \"mail\"]}");
        assertEquals("m@example.com", resolve(EMAIL, claims(NAME_ID, "persistent-id", "mail", "m@example.com"),
                mapping));
        assertEquals("n@example.com", resolve(EMAIL, claims(NAME_ID, "n@example.com", "mail", "m@example.com"),
                mapping));
        assertNull(resolve(EMAIL, claims(NAME_ID, "persistent-id"), null));
        // The @ rule is only for NameID: an attribute is taken as is.
        assertEquals("not-an-email", resolve(EMAIL, claims("mail", "not-an-email"), mapping));
        // ... and only for email: NameID without @ is a fine userId.
        assertEquals("persistent-id", resolve(USER_ID, claims(NAME_ID, "persistent-id"), null));
    }

    @Test
    public void blankNameIDFallsThrough() {
        JsonObject mapping = json("{\"userId\": [\"NameID\", \"uid\"], \"email\": [\"NameID\", \"mail\"]}");
        for (String blank : new String[]{"", "   ", "\t", null}) {
            JsonObject c = claims(NAME_ID, blank, "uid", "u-1", "mail", "m@example.com");
            assertEquals("u-1", resolve(USER_ID, c, mapping));
            assertEquals("m@example.com", resolve(EMAIL, c, mapping));
            // The defaults too.
            assertEquals("oid", resolve(USER_ID, claims(NAME_ID, blank, OBJECT_IDENTIFIER_CLAIM, "oid"), null));
            assertNull(resolve(USER_ID, claims(NAME_ID, blank), null));
        }
    }

    @Test
    public void nonBlankNameIDIsReturnedUntrimmed() {
        assertEquals("  a@example.com ", resolve(USER_ID, claims(NAME_ID, "  a@example.com "), null));
        assertEquals("  a@example.com ", resolve(EMAIL, claims(NAME_ID, "  a@example.com "), null));
    }

    @Test
    public void malformedClaimsDoNotThrow() {
        JsonObject c = new JsonObject();
        c.addProperty("id", "not-an-array");
        c.add("empty", new JsonArray());
        JsonArray nested = new JsonArray();
        nested.add(new JsonObject());
        c.add("nested", nested);
        JsonObject mapping = json("{\"userId\": [\"id\", \"empty\", \"nested\"]}");
        assertNull(resolve(USER_ID, c, mapping));
        assertNull(resolve("unknownField", c, null));
    }

    @Test
    public void mismatchKindIsBlankNameIDOnlyForABlankOrNullNameID() {
        assertEquals(KIND_BLANK_NAME_ID, mismatchKind(claims(NAME_ID, "")));
        assertEquals(KIND_BLANK_NAME_ID, mismatchKind(claims(NAME_ID, "  ")));
        assertEquals(KIND_BLANK_NAME_ID, mismatchKind(claims(NAME_ID, null)));
        assertEquals(KIND_OTHER, mismatchKind(claims(NAME_ID, "nid")));
        assertEquals(KIND_OTHER, mismatchKind(claims("mail", "m@example.com")));
        assertEquals(KIND_OTHER, mismatchKind(null));
    }

    @Test
    public void parseTreatsNullAndEmptyAsNoMapping() throws Exception {
        assertNull(SAMLAttributeMapping.parse(null));
        assertNull(SAMLAttributeMapping.parse(JsonNull.INSTANCE));
        assertNull(SAMLAttributeMapping.parse(new JsonObject()));
        JsonObject m = json("{\"userId\": [\"user id\"], \"email\": [\"mail\"], \"firstName\": [\"fn\"], " +
                "\"lastName\": [\"ln\"]}");
        assertEquals(m, SAMLAttributeMapping.parse(m));
    }

    @Test
    public void parseRejectsInvalidValuesNamingTheKey() {
        String[][] cases = {
                {"\"x\"", "attributeMapping must be an object or null"},
                {"[]", "attributeMapping must be an object or null"},
                {"true", "attributeMapping must be an object or null"},
                {"{\"userID\": [\"id\"]}", "attributeMapping has an unknown key: userID"},
                {"{\"email\": {}}", "attributeMapping.email must be an array of 1 to 10 claim names"},
                {"{\"email\": []}", "attributeMapping.email must be an array of 1 to 10 claim names"},
                {"{\"email\": [\"a\",\"a\",\"a\",\"a\",\"a\",\"a\",\"a\",\"a\",\"a\",\"a\",\"a\"]}",
                        "attributeMapping.email must be an array of 1 to 10 claim names"},
                {"{\"userId\": [true]}", "attributeMapping.userId must only contain strings"},
                {"{\"userId\": [[\"id\"]]}", "attributeMapping.userId must only contain strings"},
                {"{\"userId\": [\" \"]}", "attributeMapping.userId must not contain empty claim names"},
                {"{\"userId\": [\"NameID \"]}",
                        "attributeMapping.userId must not contain claim names with leading or trailing whitespace"},
                {"{\"lastName\": [\"sn\", \"surname\\r\"]}",
                        "attributeMapping.lastName must not contain claim names with leading or trailing whitespace"},
                {"{\"userId\": [\"" + "x".repeat(513) + "\"]}",
                        "attributeMapping.userId must not contain claim names longer than 512 characters"},
        };
        for (String[] c : cases) {
            try {
                SAMLAttributeMapping.parse(JsonParser.parseString(c[0]));
                fail("expected " + c[0] + " to be rejected");
            } catch (InvalidAttributeMappingException e) {
                assertEquals(c[1], e.getMessage());
            }
        }
    }
}
