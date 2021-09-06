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
import com.auth0.jwt.exceptions.JWTCreationException;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.jwt.exceptions.UnsupportedJWTSigningAlgorithmException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.jwt.JWTAsymmetricSigningKeyInfo;
import io.supertokens.pluginInterface.jwt.JWTSigningKeyInfo;

import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;

public class JWTSigningFunctions {
    /**
     * Creates and returns a JWT string
     * @param main
     * @param algorithm The signing algorithm to use when creating the token. Refer to {@link JWTSigningKey.SupportedAlgorithms}
     * @param payload JSON object containing user defined claims to be added to the JWT payload
     * @param jwksDomain Used as the issuer in the JWT payload
     * @param jwtValidity Used to set iat anf exp claims in the JWT payload
     * @return String token
     * @throws StorageQueryException If there is an error interacting with the database
     * @throws StorageTransactionLogicException If there is an error interacting with the database
     * @throws NoSuchAlgorithmException If there is an error when using Java's cryptography packages
     * @throws InvalidKeySpecException If there is an error when using Java's cryptography packages
     * @throws JWTCreationException If there is an error when creating JWTs
     * @throws UnsupportedJWTSigningAlgorithmException If the algorithm provided does not match any of the supported algorithms
     */
    public static String createJWTToken(Main main, String algorithm, JsonObject payload, String jwksDomain, long jwtValidity)
            throws StorageQueryException, StorageTransactionLogicException, NoSuchAlgorithmException, InvalidKeySpecException,
            JWTCreationException, UnsupportedJWTSigningAlgorithmException {
        // TODO: In the future we will have a way for the user to send a custom key id to use
        JWTSigningKey.SupportedAlgorithms supportedAlgorithm;

        try {
            supportedAlgorithm = JWTSigningKey.SupportedAlgorithms.valueOf(algorithm);
        } catch (IllegalArgumentException e) {
            // If it enters this block then the string value provided does not match the algorithms we support
            throw new UnsupportedJWTSigningAlgorithmException();
        }

        JWTSigningKeyInfo keyToUse = JWTSigningKey.getInstance(main).getOrCreateAndGetKeyForAlgorithm(supportedAlgorithm);
        // Get an instance of auth0's Algorithm which is needed when signing using auth0's package
        Algorithm signingAlgorithm = getAuth0Algorithm(supportedAlgorithm, keyToUse);

        // Create the claims for the JWT header
        Map<String, Object> headerClaims = new HashMap<>();
        headerClaims.put("alg", supportedAlgorithm.name().toUpperCase()); // All examples in the RFC have the algorithm in upper case
        headerClaims.put("typ", "JWT");
        headerClaims.put("kid", keyToUse.keyId);

        long currentTimeInMillis = System.currentTimeMillis();
        long jwtExpiry = (currentTimeInMillis / 1000) + (jwtValidity); // JWT Expiry is seconds from epoch not millis

        // Add relevant claims to the payload, note we only add/override ones that we absolutely need to.
        Map<String, Object> jwtPayload = new Gson().fromJson(payload, HashMap.class);
        jwtPayload.put("iss", jwksDomain);
        jwtPayload.put("exp", jwtExpiry);
        jwtPayload.put("iat", currentTimeInMillis / 1000); // JWT uses seconds from epoch not millis

        return com.auth0.jwt.JWT.create()
                .withPayload(jwtPayload)
                .withHeader(headerClaims)
                .sign(signingAlgorithm);
    }

    /**
     * Used to return public keys that a JWT verifier will use. Note returns an empty array if there are no keys in storage.
     * @param main
     * @return JSON array containing the JWKs
     * @throws StorageQueryException If there is an error interacting with the database
     * @throws StorageTransactionLogicException If there is an error interacting with the database
     * @throws NoSuchAlgorithmException If there is an error when using Java's cryptography packages
     * @throws InvalidKeySpecException If there is an error when using Java's cryptography packages
     */
    public static List<JsonObject> getJWKS(Main main)
            throws StorageQueryException, StorageTransactionLogicException, NoSuchAlgorithmException,
            InvalidKeySpecException {
        // Retrieve all keys in storage
        List<JWTSigningKeyInfo> keys = JWTSigningKey.getInstance(main).getAllSigningKeys();
        List<JsonObject> jwks = new ArrayList<>();

        for (int i = 0; i < keys.size(); i++) {
            JWTSigningKeyInfo currentKeyInfo = keys.get(i);

            // We only use asymmetric keys
            if (currentKeyInfo instanceof JWTAsymmetricSigningKeyInfo) {
                JWTSigningKey.SupportedAlgorithms algorithm = JWTSigningKey.SupportedAlgorithms.valueOf(currentKeyInfo.algorithm);
                // TODO: In the future with more asymmetric algorithms [ES256 for example] we will need a provider system for the public key + JWK - Nemi
                RSAPublicKey publicKey = getPublicKeyFromString(( (JWTAsymmetricSigningKeyInfo) currentKeyInfo).publicKey, algorithm);
                JsonObject jwk = new JsonObject();

                jwk.addProperty("kty", algorithm.getAlgorithmType());
                jwk.addProperty("kid", currentKeyInfo.keyId);
                jwk.addProperty("n", Base64.getUrlEncoder().encodeToString(publicKey.getModulus().toByteArray()));
                jwk.addProperty("e", Base64.getUrlEncoder().encodeToString(publicKey.getPublicExponent().toByteArray()));
                jwk.addProperty("alg", currentKeyInfo.algorithm.toUpperCase());
                jwk.addProperty("use", "sig");

                jwks.add(jwk);
            }
        }

        return jwks;
    }

    private static Algorithm getAuth0Algorithm(JWTSigningKey.SupportedAlgorithms algorithm, JWTSigningKeyInfo keyToUse) throws NoSuchAlgorithmException, InvalidKeySpecException,
            UnsupportedJWTSigningAlgorithmException {
        // TODO: Abstract this away from the main package to avoid a direct dependency on auth0s package
        if (algorithm.equalsString("rs256")) {
            RSAPublicKey publicKey = getPublicKeyFromString(((JWTAsymmetricSigningKeyInfo) keyToUse).publicKey, algorithm);
            RSAPrivateKey privateKey = getPrivateKeyFromString(((JWTAsymmetricSigningKeyInfo) keyToUse).privateKey, algorithm);

            return Algorithm.RSA256(publicKey, privateKey);
        }

        throw new UnsupportedJWTSigningAlgorithmException();
    }

    private static <T extends PublicKey> T getPublicKeyFromString(String keyCert, JWTSigningKey.SupportedAlgorithms algorithm)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        byte[] decodedKeyBytes = Base64.getDecoder().decode(keyCert);
        X509EncodedKeySpec keySpec =
                new X509EncodedKeySpec(decodedKeyBytes);
        KeyFactory kf = KeyFactory.getInstance(algorithm.getAlgorithmType());
        return (T) kf.generatePublic(keySpec);
    }

    private static <T extends PrivateKey> T getPrivateKeyFromString(String keyCert, JWTSigningKey.SupportedAlgorithms algorithm)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        byte[] decodedKeyBytes = Base64.getDecoder().decode(keyCert);
        PKCS8EncodedKeySpec keySpec =
                new PKCS8EncodedKeySpec(decodedKeyBytes);
        KeyFactory kf = KeyFactory.getInstance(algorithm.getAlgorithmType());
        return (T) kf.generatePrivate(keySpec);
    }
}
