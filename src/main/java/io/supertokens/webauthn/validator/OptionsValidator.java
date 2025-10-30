/*
 *    Copyright (c) 2025, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.webauthn.validator;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import io.supertokens.webauthn.exception.InvalidWebauthNOptionsException;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

public class OptionsValidator {

    public static void validateOptions(String origin, String rpId, Long timeout, String attestation,
                                       String userVerification, String residentKey, JsonElement supportedAlgorithmIds,
                                       Boolean userPresence)
            throws InvalidWebauthNOptionsException {
        validateOrigin(origin, rpId);
        validateTimeout(timeout);
        validateUserVerification(userVerification);
        validateAttestation(attestation);
        validateResidentKey(residentKey);
        validateSupportedAlgorithmIds(supportedAlgorithmIds);
        validateUserPresence(userPresence);
    }

    private static void validateOrigin(String origin, String rpId) throws InvalidWebauthNOptionsException {
        // Support Android origins (android:apk-key-hash:<base64Url-string-without-padding-of-fingerprint>)
        if (origin.startsWith("android:apk-key-hash:")) {
            String hash = origin.substring("android:apk-key-hash:".length());

            // Validate that the hash is not empty
            if (hash.isEmpty()) {
                throw new InvalidWebauthNOptionsException("Android origin must contain a valid base64 hash");
            }

            // Accept URL-safe base64 (A-Za-z0-9-_ only)
            if (!hash.matches("^[A-Za-z0-9\\-_]+$")) {
                throw new InvalidWebauthNOptionsException("Android origin hash must be valid URL-safe base64");
            }

            // Validate length: SHA256 is 32 bytes, base64-urlsafe encoding is 43 chars
            if (hash.length() != 43) {
                throw new InvalidWebauthNOptionsException("Android origin hash must be 43 characters (base64 of signing certificate's SHA 256 fingerprint)");
            }

            return;
        }

        // Validate standard HTTP(S) origins
        try {
            URL originUrl = new URL(origin);
            if (!originUrl.getHost().endsWith(rpId)) {
                throw new InvalidWebauthNOptionsException("Origin does not match RelyingParty Id");
            }
        } catch (MalformedURLException e) {
            throw new InvalidWebauthNOptionsException("Origin has to be a valid url!");
        }
    }

    private static void validateTimeout(Long timeout) throws InvalidWebauthNOptionsException {
        if (timeout == null || timeout < 0L) {
            throw new InvalidWebauthNOptionsException("Timeout must be a positive value");
        }
    }

    private static void validateEnumeratedStrValues(String toValidate, List<String> possibleValues, String fieldName)
            throws InvalidWebauthNOptionsException {
        if (!possibleValues.contains(toValidate)) {
            throw new InvalidWebauthNOptionsException("Invalid value '" + toValidate + "' for " + fieldName + ". Must be one of: " + String.join(", ", possibleValues));
        }
    }

    private static void validateAttestation(String attestation) throws InvalidWebauthNOptionsException {
        validateEnumeratedStrValues(attestation, List.of("none", "indirect", "direct", "enterprise"), "attestation");
    }

    private static void validateUserVerification(String userVerification) throws InvalidWebauthNOptionsException {
        validateEnumeratedStrValues(userVerification, List.of("required", "discouraged", "preferred"),
                "userVerification");
    }

    private static void validateResidentKey(String residentKey) throws InvalidWebauthNOptionsException {
        validateEnumeratedStrValues(residentKey, List.of("required", "discouraged", "preferred"), "residentKey");
    }

    private static void validateSupportedAlgorithmIds(JsonElement supportedAlgorithmIds) throws InvalidWebauthNOptionsException {
        if (supportedAlgorithmIds == null){
            throw new InvalidWebauthNOptionsException("supportedAlgorithmIds should not be null");
        }
        try {
            JsonArray supportedAlgos = supportedAlgorithmIds.getAsJsonArray();
            for(int i = 0; i < supportedAlgos.size(); i++){
                try {
                    supportedAlgos.get(i).getAsLong();
                } catch (NumberFormatException | IllegalStateException e) {
                    throw new InvalidWebauthNOptionsException(e.getMessage());
                }
            }
        } catch (IllegalStateException e) {
            throw new InvalidWebauthNOptionsException("supportedAlgorithmIds has to be a JsonArray");
        }

    }

    private static void validateUserPresence(Boolean userPresence) throws InvalidWebauthNOptionsException {
        if (userPresence == null ){
            throw new InvalidWebauthNOptionsException("userPresence can't be null");
        }
    }
}
