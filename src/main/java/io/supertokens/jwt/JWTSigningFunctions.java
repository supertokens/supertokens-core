/*
 *    Copyright (c) 2021, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.jwt;

import com.auth0.jwt.algorithms.Algorithm;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.jwt.JWTSigningKeyInfo;

import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;

public class JWTSigningFunctions {
    public static String createJWTToken(Main main, String algorithm, JsonObject payload, String jwksDomain, long jwtValidity)
            throws StorageQueryException, StorageTransactionLogicException, NoSuchAlgorithmException, InvalidKeySpecException {
        // TODO: In the future we will have a way for the user to send a custom key id to use
        JWTSigningKeyInfo keyToUse = getKeyToUse(main, algorithm);
        Algorithm signingAlgorithm = getAlgorithmFromString(main, algorithm, keyToUse);

        // Create the claims for the JWT header
        Map<String, Object> headerClaims = new HashMap<>();
        headerClaims.put("alg", algorithm.toUpperCase()); // ALl examples and the RFC have the algorithm in upper case
        headerClaims.put("typ", "JWT");
        headerClaims.put("kid", keyToUse.keyId);

        long currentTimeInMillis = System.currentTimeMillis();
        long jwtExpiry = currentTimeInMillis + (jwtValidity * 1000); // Validity is in seconds

        // Add relevant claims to the payload, note we only add/override ones that we absolutely need to.
        Map<String, Object> jwtPayload = new Gson().fromJson(payload, HashMap.class);
        jwtPayload.put("iss", jwksDomain);
        jwtPayload.put("exp", jwtExpiry);
        jwtPayload.put("iat", currentTimeInMillis);

        // TODO: Add custom payload
        return com.auth0.jwt.JWT.create()
                .withPayload(jwtPayload)
                .withHeader(headerClaims)
                .sign(signingAlgorithm);
    }

    private static JWTSigningKeyInfo getKeyToUse(Main main, String algorithm)
            throws StorageQueryException, StorageTransactionLogicException, NoSuchAlgorithmException {
        return JWTSigningKey.getInstance(main).getKeyForAlgorithm(algorithm);
    }

    private static Algorithm getAlgorithmFromString(Main main, String algorithm, JWTSigningKeyInfo keyToUse) throws NoSuchAlgorithmException, InvalidKeySpecException {
        // TODO: Abstract this away from the main package to avoid a direct dependency on auth0s package
        if (algorithm.equalsIgnoreCase("rs256")) {
            RSAPublicKey publicKey = getPublicKeyFromString(keyToUse.publicKey, "RSA");
            RSAPrivateKey privateKey = getPrivateKeyFromString(keyToUse.privateKey, "RSA");

            return Algorithm.RSA256(publicKey, privateKey);
        }

        throw new NoSuchAlgorithmException(algorithm + " is not a supported JWT signing algorithm");
    }

    private static <T extends PublicKey> T getPublicKeyFromString(String keyCert, String algorithm)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        byte[] decodedKeyBytes = Base64.getDecoder().decode(keyCert);
        X509EncodedKeySpec keySpec =
                new X509EncodedKeySpec(decodedKeyBytes);
        KeyFactory kf = KeyFactory.getInstance(algorithm);
        return (T) kf.generatePublic(keySpec);
    }

    private static <T extends PrivateKey> T getPrivateKeyFromString(String keyCert, String algorithm)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        byte[] decodedKeyBytes = Base64.getDecoder().decode(keyCert);
        X509EncodedKeySpec keySpec =
                new X509EncodedKeySpec(decodedKeyBytes);
        KeyFactory kf = KeyFactory.getInstance(algorithm);
        return (T) kf.generatePrivate(keySpec);
    }

    public static List<JsonObject> getJWKS(Main main)
            throws StorageQueryException, StorageTransactionLogicException, NoSuchAlgorithmException,
            InvalidKeySpecException {
        List<JWTSigningKeyInfo> keys = JWTSigningKey.getInstance(main).getAllSigningKeys();
        List<JsonObject> jwks = new ArrayList<>();

        for (int i = 0; i < keys.size(); i++) {
            JWTSigningKeyInfo currentKeyInfo = keys.get(i);
            RSAPublicKey publicKey = getPublicKeyFromString(currentKeyInfo.publicKey, currentKeyInfo.algorithmType.toUpperCase());
            JsonObject jwk = new JsonObject();

            jwk.addProperty("kty", currentKeyInfo.algorithmType);
            jwk.addProperty("kid", currentKeyInfo.keyId);
            jwk.addProperty("n", publicKey.getModulus().toString());
            jwk.addProperty("e", publicKey.getPublicExponent().toString());
            jwk.addProperty("alg", currentKeyInfo.algorithm.toUpperCase());
            jwk.addProperty("use", "sig");

            jwks.add(jwk);
        }

        return jwks;
    }
}
