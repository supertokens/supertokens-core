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

import io.supertokens.webauthn.exception.InvalidWebauthNOptionsException;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

public class OptionsValidator {

    public static void validateOptions(String origin, String rpId, Long timeout, String attestation,
                                        String userVerification, String residentKey)
            throws InvalidWebauthNOptionsException {
        validateOrigin(origin, rpId);
        validateTimeout(timeout);
        validateUserVerification(userVerification);
        validateAttestation(attestation);
        validateResidentKey(residentKey);
    }

    private static void validateOrigin(String origin, String rpId) throws InvalidWebauthNOptionsException {
        try {
            URL originUrl = new URL(origin);
            URL rpIdUrl = new URL(rpId);
            if (!originUrl.getHost().contains(rpIdUrl.getHost())) {
                throw new InvalidWebauthNOptionsException("Origin does not match RelyingParty Id");
            }
        } catch (MalformedURLException e) {
            throw new InvalidWebauthNOptionsException("Origin and rpId have to be valid urls!");
        }
    }

    private static void validateTimeout(Long timeout) throws InvalidWebauthNOptionsException {
        if (timeout == null || timeout < 0) {
            throw new InvalidWebauthNOptionsException("Timeout must be a positive long value");
        }
    }

    private static void validateEnumeratedStrValues(String toValidate, List<String> possibleValues, String fieldName)
            throws InvalidWebauthNOptionsException {
        if (!possibleValues.contains(toValidate)) {
            throw new InvalidWebauthNOptionsException("Invalid value " + toValidate + " for " + fieldName);
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
}