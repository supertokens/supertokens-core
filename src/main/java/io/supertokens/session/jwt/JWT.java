/*
 *    Copyright (c) 2020, VRAI Labs and/or its affiliates. All rights reserved.
 *
 *    This program is licensed under the SuperTokens Community License (the
 *    "License") as published by VRAI Labs. You may not use this file except in
 *    compliance with the License. You are not permitted to transfer or
 *    redistribute this file without express written permission from VRAI Labs.
 *
 *    A copy of the License is available in the file titled
 *    "SuperTokensLicense.pdf" inside this repository or included with your copy of
 *    the software or its source code. If you have not received a copy of the
 *    License, please write to VRAI Labs at team@supertokens.io.
 *
 *    Please read the License carefully before accessing, downloading, copying,
 *    using, modifying, merging, transferring or sharing this software. By
 *    undertaking any of these activities, you indicate your agreement to the terms
 *    of the License.
 *
 *    This program is distributed with certain software that is licensed under
 *    separate terms, as designated in a particular file or component or in
 *    included license documentation. VRAI Labs hereby grants you an additional
 *    permission to link the program and your derivative works with the separately
 *    licensed software that they have included with this program, however if you
 *    modify this program, you shall be solely liable to ensure compliance of the
 *    modified program with the terms of licensing of the separately licensed
 *    software.
 *
 *    Unless required by applicable law or agreed to in writing, this program is
 *    distributed under the License on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 *    CONDITIONS OF ANY KIND, either express or implied. See the License for the
 *    specific language governing permissions and limitations under the License.
 *
 */

package io.supertokens.session.jwt;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.supertokens.utils.Utils;

import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;

public class JWT {
    private static String HEADER = null;

    private static void initHeader() throws UnsupportedEncodingException {
        if (HEADER == null) {
            JsonObject header = new JsonObject();
            header.addProperty("alg", "RS256");
            header.addProperty("typ", "JWT");
            header.addProperty("version", "1");
            JWT.HEADER = Utils.convertToBase64(header.toString());
        }
    }

    public static String createJWT(JsonElement jsonObj, String privateSigningKey)
            throws InvalidKeyException, NoSuchAlgorithmException, UnsupportedEncodingException,
            InvalidKeySpecException, SignatureException {
        initHeader();
        String payload = Utils.convertToBase64(jsonObj.toString());
        String signature = Utils.signWithPrivateKey(JWT.HEADER + "." + payload, privateSigningKey);
        return JWT.HEADER + "." + payload + "." + signature;
    }

    public static JsonElement verifyJWTAndGetPayload(String jwt, String publicSigningKey)
            throws InvalidKeyException, NoSuchAlgorithmException, JWTException, UnsupportedEncodingException {
        initHeader();
        String[] splittedInput = jwt.split("\\.");
        if (splittedInput.length != 3) {
            throw new JWTException("Invalid JWT");
        }
        // checking header
        if (!splittedInput[0].equals(JWT.HEADER)) {
            throw new JWTException("JWT header mismatch");
        }
        // verifying signature
        String payload = splittedInput[1];
        try {
            if (!Utils.verifyWithPublicKey(JWT.HEADER + "." + payload, splittedInput[2], publicSigningKey)) {
                throw new JWTException("JWT verification failed");
            }
        } catch (InvalidKeySpecException | SignatureException e) {
            throw new JWTException("JWT verification failed");
        }
        return new JsonParser().parse(Utils.convertFromBase64(splittedInput[1]));
    }

    public static class JWTException extends Exception {

        private static final long serialVersionUID = 1L;

        JWTException(String err) {
            super(err);
        }
    }
}
