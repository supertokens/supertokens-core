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
import com.google.gson.JsonPrimitive;
import io.supertokens.session.accessToken.AccessToken;
import io.supertokens.utils.Utils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;

public class JWT {
    private static String HEADERv1 = null;
    private static String HEADERv2 = null;

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
    }

    public static String createAndSignLegacyAccessToken(JsonElement jsonObj, String privateSigningKey,
                                                        AccessToken.VERSION version)
            throws InvalidKeyException, NoSuchAlgorithmException, InvalidKeySpecException, SignatureException {
        initHeader();
        String payload;
        String header;
        header = version == AccessToken.VERSION.V1 ? JWT.HEADERv1 : JWT.HEADERv2;
        payload = Utils.convertToBase64(jsonObj.toString());
        String signature = Utils.signWithPrivateKey(header + "." + payload, privateSigningKey,
                version != AccessToken.VERSION.V1 && version != AccessToken.VERSION.V2);
        return header + "." + payload + "." + signature;
    }

    public static JWTPreParseInfo preParseJWTInfo(String jwt) throws JWTException {
        initHeader();
        String[] splittedInput = jwt.split("\\.");
        if (splittedInput.length != 3) {
            throw new JWTException("Invalid JWT");
        }

        if (splittedInput[0].equals(JWT.HEADERv1)) {
            return new JWTPreParseInfo(splittedInput, AccessToken.VERSION.V1, null);
        }

        if (splittedInput[0].equals(JWT.HEADERv2)) {
            return new JWTPreParseInfo(splittedInput, AccessToken.VERSION.V2, null);
        }

        JsonObject parsedHeader = new JsonParser().parse(Utils.convertFromBase64(splittedInput[0])).getAsJsonObject();

        if (parsedHeader.get("typ") == null) {
            throw new JWTException("JWT header missing - typ");
        }
        JsonPrimitive typ = parsedHeader.get("typ").getAsJsonPrimitive();
        if (!typ.isString() || !typ.getAsString().equals("JWT")) {
            throw new JWTException("JWT header mismatch - typ");
        }

        if (parsedHeader.get("alg") == null) {
            throw new JWTException("JWT header missing - alg");
        }
        JsonPrimitive alg = parsedHeader.get("alg").getAsJsonPrimitive();
        if (!alg.isString() || !alg.getAsString().equals("RS256")) {
            throw new JWTException("JWT header mismatch - alg");
        }

        JsonElement versionElement = parsedHeader.get("version");
        // We default to using the latest access token version here for JWTs not created by the session recipe
        String versionString = AccessToken.getVersionStringFromAccessTokenVersion(AccessToken.getLatestVersion());

        if (versionElement != null) {
            JsonPrimitive version = versionElement.getAsJsonPrimitive();
            if (!version.isString() || version.getAsString().equals("1") || version.getAsString().equals("2")) {
                throw new JWTException("JWT header mismatch - version");
            }

            versionString = version.getAsString();
        }

        JsonPrimitive kid = parsedHeader.get("kid").getAsJsonPrimitive();
        if (parsedHeader.get("kid") == null) {
            throw new JWTException("JWT header missing - kid");
        }
        if (!kid.isString()) {
            throw new JWTException("JWT header mismatch - kid");
        }
        return new JWTPreParseInfo(splittedInput, AccessToken.getVersionFromString(versionString), kid.getAsString());
    }

    public static JWTInfo verifyJWTAndGetPayload(JWTPreParseInfo jwt, String publicSigningKey)
            throws InvalidKeyException, NoSuchAlgorithmException, JWTException {

        try {
            if (!Utils.verifyWithPublicKey(jwt.header + "." + jwt.payload, jwt.signature, publicSigningKey,
                    jwt.version != AccessToken.VERSION.V1 && jwt.version != AccessToken.VERSION.V2)) {
                throw new JWTException("JWT verification failed");
            }
        } catch (InvalidKeySpecException | SignatureException e) {
            throw new JWTException("JWT verification failed");
        }
        return new JWTInfo(new JsonParser().parse(Utils.convertFromBase64(jwt.payload)).getAsJsonObject(), jwt.version);
    }

    public static JWTInfo getPayloadWithoutVerifying(String jwt) throws JWTException {
        JWTPreParseInfo jwtInfo = preParseJWTInfo(jwt);
        return new JWTInfo(new JsonParser().parse(Utils.convertFromBase64(jwtInfo.payload)).getAsJsonObject(),
                jwtInfo.version);
    }

    public static class JWTException extends Exception {

        private static final long serialVersionUID = 1L;

        JWTException(String err) {
            super(err);
        }
    }

    public static class JWTPreParseInfo {
        @Nonnull
        public final String header;
        @Nonnull
        public final String payload;
        @Nonnull
        public final String signature;

        @Nonnull
        public final AccessToken.VERSION version;

        @Nullable
        public final String kid;

        public JWTPreParseInfo(String[] splittedInput, AccessToken.VERSION version, String kid) throws JWTException {
            if (splittedInput.length != 3) {
                throw new JWTException("Invalid JWT");
            }

            this.header = splittedInput[0];
            this.payload = splittedInput[1];
            this.signature = splittedInput[2];

            this.version = version;
            this.kid = kid;
        }
    }

    public static class JWTInfo {
        public final JsonObject payload;

        public final AccessToken.VERSION version;

        public JWTInfo(JsonObject payload, AccessToken.VERSION version) {
            this.payload = payload;
            this.version = version;
        }
    }
}
