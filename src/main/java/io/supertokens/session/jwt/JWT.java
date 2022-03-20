/*
 *    Copyright (c) 2020, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.session.jwt;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.supertokens.session.accessToken.AccessToken;
import io.supertokens.utils.Utils;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;

public class JWT {
    private static String HEADERv1 = null;
    private static String HEADERv2 = null;
    private static String HEADERv3 = null;

    private static void initHeader() {
        if (HEADERv1 == null) {
            JsonObject header = new JsonObject();
            header.addProperty("alg", "RS256");
            header.addProperty("typ", "JWT");
            header.addProperty("version", "1");
            JWT.HEADERv1 = Utils.convertToBase64(header.toString());
        }
        if (HEADERv2 == null) {
            JsonObject header = new JsonObject();
            header.addProperty("alg", "RS256");
            header.addProperty("typ", "JWT");
            header.addProperty("version", "2");
            JWT.HEADERv2 = Utils.convertToBase64(header.toString());
        }
        if (HEADERv3 == null) {
            JsonObject header = new JsonObject();
            header.addProperty("alg", "RS256");
            header.addProperty("typ", "JWT");
            header.addProperty("version", "3");
            JWT.HEADERv3 = Utils.convertToBase64(header.toString());
        }
    }

    public static String createJWT(JsonElement jsonObj, String privateSigningKey, AccessToken.VERSION version)
            throws InvalidKeyException, NoSuchAlgorithmException, InvalidKeySpecException, SignatureException {
        initHeader();
        String payload = Utils.convertToBase64(jsonObj.toString());
        String header = version == AccessToken.VERSION.V1 ? JWT.HEADERv1
                : (version == AccessToken.VERSION.V2 ? JWT.HEADERv2 : JWT.HEADERv3);
        String signature = Utils.signWithPrivateKey(header + "." + payload, privateSigningKey);
        return header + "." + payload + "." + signature;
    }

    public static JWTInfo verifyJWTAndGetPayload(String jwt, String publicSigningKey)
            throws InvalidKeyException, NoSuchAlgorithmException, JWTException {
        initHeader();
        String[] splittedInput = jwt.split("\\.");
        if (splittedInput.length != 3) {
            throw new JWTException("Invalid JWT");
        }
        // checking header
        AccessToken.VERSION version = parseVersionHeader(splittedInput[0]);
        if (version == null) {
            throw new JWTException("JWT header mismatch");
        }
        // verifying signature
        String payload = splittedInput[1];
        try {
            if (!Utils.verifyWithPublicKey(splittedInput[0] + "." + payload, splittedInput[2], publicSigningKey)) {
                throw new JWTException("JWT verification failed");
            }
        } catch (InvalidKeySpecException | SignatureException e) {
            throw new JWTException("JWT verification failed");
        }
        return new JWTInfo(new JsonParser().parse(Utils.convertFromBase64(splittedInput[1])), version);
    }

    private static AccessToken.VERSION parseVersionHeader(String header) {
        if (header.equals(JWT.HEADERv1)) {
            return AccessToken.VERSION.V1;
        } else if (header.equals(JWT.HEADERv2)) {
            return AccessToken.VERSION.V2;
        } else if (header.equals(JWT.HEADERv3)) {
            return AccessToken.VERSION.V3;
        }

        return null;
    }

    public static JWTInfo getPayloadWithoutVerifying(String jwt) {
        initHeader();
        String[] splittedInput = jwt.split("\\.");
        AccessToken.VERSION version = parseVersionHeader(splittedInput[0]);
        if (version == null) {
            version = AccessToken.VERSION.V3;
        }

        return new JWTInfo(new JsonParser().parse(Utils.convertFromBase64(splittedInput[1])), version);
    }

    public static class JWTException extends Exception {

        private static final long serialVersionUID = 1L;

        JWTException(String err) {
            super(err);
        }
    }

    public static class JWTInfo {
        public final JsonElement payload;

        public final AccessToken.VERSION version;

        public JWTInfo(JsonElement payload, AccessToken.VERSION version) {
            this.payload = payload;
            this.version = version;
        }
    }
}
