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

package io.supertokens.saml;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.supertokens.saml.exceptions.InvalidAttributeMappingException;

/**
 * A SAML client's optional attribute mapping: for each of userId / email / firstName / lastName, an ordered list of
 * assertion claim names to read the value from. Fields the mapping leaves out use the default lists below.
 * <p>
 * Only clients with a mapping are resolved through here; clients without one keep the built-in chain in
 * {@link SAML#getUserInfo}, which this is only compared against.
 */
public final class SAMLAttributeMapping {

    public static final String USER_ID = "userId";
    public static final String EMAIL = "email";
    public static final String FIRST_NAME = "firstName";
    public static final String LAST_NAME = "lastName";

    // The Subject NameID, as keyed by SAML.extractAssertionClaims.
    public static final String NAME_ID = "NameID";

    public static final String OBJECT_IDENTIFIER_CLAIM =
            "http://schemas.microsoft.com/identity/claims/objectidentifier";
    public static final String NAME_CLAIM = "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/name";
    public static final String EMAIL_ADDRESS_CLAIM =
            "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress";

    // Today's built-in rules, except that a blank NameID falls through (see resolve).
    public static final List<String> DEFAULT_USER_ID = List.of(NAME_ID, OBJECT_IDENTIFIER_CLAIM, NAME_CLAIM);
    public static final List<String> DEFAULT_EMAIL = List.of(EMAIL_ADDRESS_CLAIM, "mail", "email", NAME_ID);
    public static final List<String> DEFAULT_FIRST_NAME = List.of();
    public static final List<String> DEFAULT_LAST_NAME = List.of();

    private static final Map<String, List<String>> DEFAULTS = Map.of(
            USER_ID, DEFAULT_USER_ID,
            EMAIL, DEFAULT_EMAIL,
            FIRST_NAME, DEFAULT_FIRST_NAME,
            LAST_NAME, DEFAULT_LAST_NAME);

    public static final int MAX_CLAIM_NAMES = 10;
    public static final int MAX_CLAIM_NAME_LENGTH = 512;

    // Kinds for the comparison log of clients without a mapping.
    public static final String KIND_BLANK_NAME_ID = "blank-nameid";
    public static final String KIND_OTHER = "other";

    private SAMLAttributeMapping() {
    }

    /**
     * Validates an `attributeMapping` request value. Returns null for JSON null and for `{}` (both mean "no
     * mapping"); otherwise returns the object as given.
     */
    public static JsonObject parse(JsonElement value) throws InvalidAttributeMappingException {
        if (value == null || value.isJsonNull()) {
            return null;
        }
        if (!value.isJsonObject()) {
            throw new InvalidAttributeMappingException("attributeMapping must be an object or null");
        }
        JsonObject mapping = value.getAsJsonObject();
        for (Map.Entry<String, JsonElement> entry : mapping.entrySet()) {
            String key = entry.getKey();
            if (!DEFAULTS.containsKey(key)) {
                throw new InvalidAttributeMappingException("attributeMapping has an unknown key: " + key);
            }
            String field = "attributeMapping." + key;
            JsonElement names = entry.getValue();
            if (!names.isJsonArray() || names.getAsJsonArray().isEmpty()
                    || names.getAsJsonArray().size() > MAX_CLAIM_NAMES) {
                throw new InvalidAttributeMappingException(
                        field + " must be an array of 1 to " + MAX_CLAIM_NAMES + " claim names");
            }
            for (JsonElement name : names.getAsJsonArray()) {
                if (!name.isJsonPrimitive() || !name.getAsJsonPrimitive().isString()) {
                    throw new InvalidAttributeMappingException(field + " must only contain strings");
                }
                String s = name.getAsString();
                if (s.trim().isEmpty()) {
                    throw new InvalidAttributeMappingException(field + " must not contain empty claim names");
                }
                if (s.length() > MAX_CLAIM_NAME_LENGTH) {
                    throw new InvalidAttributeMappingException(
                            field + " must not contain claim names longer than " + MAX_CLAIM_NAME_LENGTH +
                                    " characters");
                }
            }
        }
        return mapping.isEmpty() ? null : mapping;
    }

    /**
     * Resolves one field from the assertion claims: walks the mapping's list for the field (or the default list
     * when the mapping is null or leaves the field out) and returns the first value of the first claim that is
     * present with a non-blank first value, or null. Names match exactly. A `NameID` candidate counts only when
     * its value is non-null and not blank, and for `email` only when it contains `@`; it is returned untrimmed.
     * Never throws.
     */
    public static String resolve(String field, JsonObject claims, JsonObject mapping) {
        if (claims == null) {
            return null;
        }
        for (String name : candidates(field, mapping)) {
            String value = firstValue(claims, name);
            if (value == null || value.trim().isEmpty()) {
                continue;
            }
            if (NAME_ID.equals(name) && EMAIL.equals(field) && !value.contains("@")) {
                continue;
            }
            return value;
        }
        return null;
    }

    /**
     * Why the default-list resolution differs from the built-in chain for these claims: `blank-nameid` when the
     * assertion's NameID is present but null or blank (the expected difference), `other` otherwise.
     */
    public static String mismatchKind(JsonObject claims) {
        try {
            if (claims != null && claims.has(NAME_ID)) {
                JsonElement nameIds = claims.get(NAME_ID);
                if (!nameIds.isJsonArray() || nameIds.getAsJsonArray().isEmpty()) {
                    return KIND_BLANK_NAME_ID;
                }
                JsonElement first = nameIds.getAsJsonArray().get(0);
                if (first.isJsonNull() || (first.isJsonPrimitive() && first.getAsString().trim().isEmpty())) {
                    return KIND_BLANK_NAME_ID;
                }
            }
        } catch (RuntimeException ignored) {
            // fall through
        }
        return KIND_OTHER;
    }

    private static List<String> candidates(String field, JsonObject mapping) {
        if (mapping != null && mapping.has(field) && mapping.get(field).isJsonArray()) {
            List<String> names = new ArrayList<>();
            for (JsonElement name : mapping.getAsJsonArray(field)) {
                if (name.isJsonPrimitive()) {
                    names.add(name.getAsString());
                }
            }
            return names;
        }
        return DEFAULTS.getOrDefault(field, List.of());
    }

    private static String firstValue(JsonObject claims, String name) {
        JsonElement values = claims.get(name);
        if (values == null || !values.isJsonArray()) {
            return null;
        }
        JsonArray array = values.getAsJsonArray();
        if (array.isEmpty()) {
            return null;
        }
        JsonElement first = array.get(0);
        if (first == null || !first.isJsonPrimitive()) {
            return null;
        }
        return first.getAsString();
    }
}
