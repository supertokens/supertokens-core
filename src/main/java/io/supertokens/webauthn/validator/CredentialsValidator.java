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

import com.google.gson.JsonObject;
import io.supertokens.webauthn.exception.WebauthNInvalidFormatException;

import java.util.Base64;

public class CredentialsValidator {

    public static void validateCredential(JsonObject credentialObject) throws WebauthNInvalidFormatException {
        validateCredentialId(credentialObject);
        validateCredentialType(credentialObject);
    }

    private static void validateCredentialId(JsonObject credentialObject) throws WebauthNInvalidFormatException {
        if(credentialObject.has("id") && !credentialObject.get("id").isJsonNull()) {
            String credentialId = credentialObject.get("id").getAsString();
            if (credentialId == null || credentialId.isEmpty()) {
                throw new WebauthNInvalidFormatException("credential id is empty!");
            }
            try {
                Base64.getUrlDecoder().decode(credentialId);
            } catch (IllegalStateException e) {
                throw new WebauthNInvalidFormatException("Credential ID has to be base64 url encoded");
            }
        } else {
            throw new WebauthNInvalidFormatException("credential id is missing!");
        }
    }

    private static void validateCredentialType(JsonObject credentialObject) throws WebauthNInvalidFormatException {
        if(credentialObject.has("type") && !credentialObject.get("type").isJsonNull()) {
            String credentialType = credentialObject.get("type").getAsString();
            if (credentialType == null || credentialType.isEmpty()) {
                throw new WebauthNInvalidFormatException("credential type is empty!");
            }
            if(!credentialType.equalsIgnoreCase("public-key")){
                throw new WebauthNInvalidFormatException("not supported credential type: '" + credentialType + "'");
            }
        } else {
            throw new WebauthNInvalidFormatException("credential type is missing!");
        }
    }
}
