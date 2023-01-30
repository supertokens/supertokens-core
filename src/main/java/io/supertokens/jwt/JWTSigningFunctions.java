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

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;

public class JWTSigningFunctions {
    /**
     * Creates and returns a JWT string
     *
     * @param main
     * @param algorithm   The signing algorithm to use when creating the token. Refer to
     *                    {@link JWTSigningKey.SupportedAlgorithms}
     * @param payload     JSON object containing user defined claims to be added to the JWT payload
     * @param jwksDomain  Used as the issuer in the JWT payload
     * @param jwtValidity Used to set iat anf exp claims in the JWT payload
     * @return String token
     * @throws StorageQueryException                   If there is an error interacting with the database
     * @throws StorageTransactionLogicException        If there is an error interacting with the database
     * @throws NoSuchAlgorithmException                If there is an error when using Java's cryptography packages
     * @throws InvalidKeySpecException                 If there is an error when using Java's cryptography packages
     * @throws JWTCreationException                    If there is an error when creating JWTs
     * @throws UnsupportedJWTSigningAlgorithmException If the algorithm provided does not match any of the supported
     *                                                 algorithms
     */
    public static String createJWTToken(Main main, String algorithm, JsonObject payload, String jwksDomain,
                                        long jwtValidity) throws StorageQueryException, StorageTransactionLogicException, NoSuchAlgorithmException,
            InvalidKeySpecException, JWTCreationException, UnsupportedJWTSigningAlgorithmException {
        // TODO: In the future we will have a way for the user to send a custom key id to use
        JWTSigningKey.SupportedAlgorithms supportedAlgorithm;

        try {
            supportedAlgorithm = JWTSigningKey.SupportedAlgorithms.valueOf(algorithm);
        } catch (IllegalArgumentException e) {
            // If it enters this block then the string value provided does not match the algorithms we support
            throw new UnsupportedJWTSigningAlgorithmException();
        }

        long currentTimeInMillis = System.currentTimeMillis();

        // JWT Expiry is seconds from epoch not millis
        long jwtExpiry = Double.valueOf(Math.ceil((currentTimeInMillis / 1000.0))).longValue() + jwtValidity;
        return createJWTToken(main, supportedAlgorithm, new HashMap<>(), payload, jwksDomain, jwtExpiry, currentTimeInMillis / 1000);
    }

    @SuppressWarnings("unchecked")
    public static String createJWTToken(Main main, JWTSigningKey.SupportedAlgorithms supportedAlgorithm, Map<String, Object> headerClaims, JsonObject payload, String jwksDomain,
            long jwtExpiry, long jwtIssuedAt) throws StorageQueryException, StorageTransactionLogicException, NoSuchAlgorithmException,
            InvalidKeySpecException, JWTCreationException, UnsupportedJWTSigningAlgorithmException {
        JWTSigningKeyInfo keyToUse = JWTSigningKey.getInstance(main)
                .getOrCreateAndGetKeyForAlgorithm(supportedAlgorithm);
        // Get an instance of auth0's Algorithm which is needed when signing using auth0's package
        Algorithm signingAlgorithm = getAuth0Algorithm(supportedAlgorithm, keyToUse);

        // Create the claims for the JWT header
        headerClaims.put("alg", supportedAlgorithm.name().toUpperCase()); // All examples in the RFC have the algorithm
                                                                          // in upper case
        headerClaims.put("typ", "JWT");
        headerClaims.put("kid", keyToUse.keyId);

        // Add relevant claims to the payload, note we only add/override ones that we absolutely need to.
        Map<String, Object> jwtPayload = new Gson().fromJson(payload, HashMap.class);
        if (jwksDomain != null ) {
            jwtPayload.putIfAbsent("iss", jwksDomain);
        }
        jwtPayload.put("exp", jwtExpiry);
        jwtPayload.put("iat", jwtIssuedAt); // JWT uses seconds from epoch not millis

        return com.auth0.jwt.JWT.create().withPayload(jwtPayload).withHeader(headerClaims).sign(signingAlgorithm);
    }

    /**
     * Returns a byte array representation of the specified big integer
     * without the sign bit.
     *
     * @param bigInt The big integer to be converted. Must not be
     *               {@code null}.
     *
     * @return A byte array representation of the big integer, without the
     *         sign bit.
     */
    private static byte[] toBytesUnsigned(final BigInteger bigInt) {

        // Copied from Apache Commons Codec 1.8

        int bitlen = bigInt.bitLength();

        // round bitlen
        bitlen = ((bitlen + 7) >> 3) << 3;
        final byte[] bigBytes = bigInt.toByteArray();

        if (((bigInt.bitLength() % 8) != 0) && (((bigInt.bitLength() / 8) + 1) == (bitlen / 8))) {

            return bigBytes;

        }

        // set up params for copying everything but sign bit
        int startSrc = 0;
        int len = bigBytes.length;

        // if bigInt is exactly byte-aligned, just skip signbit in copy
        if ((bigInt.bitLength() % 8) == 0) {

            startSrc = 1;
            len--;
        }

        final int startDst = bitlen / 8 - len; // to pad w/ nulls as per spec
        final byte[] resizedBytes = new byte[bitlen / 8];
        System.arraycopy(bigBytes, startSrc, resizedBytes, startDst, len);
        return resizedBytes;
    }

    /**
     * Used to return public keys that a JWT verifier will use. Note returns an empty array if there are no keys in
     * storage.
     *
     * @param main
     * @return JSON array containing the JWKs
     * @throws StorageQueryException            If there is an error interacting with the database
     * @throws StorageTransactionLogicException If there is an error interacting with the database
     * @throws NoSuchAlgorithmException         If there is an error when using Java's cryptography packages
     * @throws InvalidKeySpecException          If there is an error when using Java's cryptography packages
     */
    public static List<JsonObject> getJWKS(Main main) throws StorageQueryException, StorageTransactionLogicException,
            NoSuchAlgorithmException, InvalidKeySpecException {
        // Retrieve all keys in storage
        List<JWTSigningKeyInfo> keys = JWTSigningKey.getInstance(main).getAllSigningKeys();
        List<JsonObject> jwks = new ArrayList<>();

        for (int i = 0; i < keys.size(); i++) {
            JWTSigningKeyInfo currentKeyInfo = keys.get(i);

            // We only use asymmetric keys
            if (currentKeyInfo instanceof JWTAsymmetricSigningKeyInfo) {
                JWTSigningKey.SupportedAlgorithms algorithm = JWTSigningKey.SupportedAlgorithms
                        .valueOf(currentKeyInfo.algorithm);
                // TODO: In the future with more asymmetric algorithms [ES256 for example] we will need a provider
                // system for the public key + JWK - Nemi
                PublicKey publicKey = getPublicKeyFromString(((JWTAsymmetricSigningKeyInfo) currentKeyInfo).publicKey,
                        algorithm);

                if (publicKey instanceof RSAPublicKey) {
                    JsonObject jwk = new JsonObject();

                    // Most verifiers seem to expect kty and alg to be in upper case so forcing that here
                    jwk.addProperty("kty", algorithm.getAlgorithmType().toUpperCase());
                    jwk.addProperty("kid", currentKeyInfo.keyId);
                    jwk.addProperty("n", Base64.getUrlEncoder().withoutPadding()
                            .encodeToString(toBytesUnsigned(((RSAPublicKey) publicKey).getModulus())));
                    jwk.addProperty("e", Base64.getUrlEncoder().withoutPadding()
                            .encodeToString(toBytesUnsigned(((RSAPublicKey) publicKey).getPublicExponent())));
                    jwk.addProperty("alg", currentKeyInfo.algorithm.toUpperCase());
                    jwk.addProperty("use", "sig"); // We generate JWKs that are meant to be used for signature
                                                   // verification

                    jwks.add(jwk);
                } else {
                    // we don't do anything here because there could be other keys in the array
                    // that could still be valid.
                }
            }
        }

        return jwks;
    }

    private static Algorithm getAuth0Algorithm(JWTSigningKey.SupportedAlgorithms algorithm, JWTSigningKeyInfo keyToUse)
            throws NoSuchAlgorithmException, InvalidKeySpecException, UnsupportedJWTSigningAlgorithmException {
        // TODO: Abstract this away from the main package to avoid a direct dependency on auth0s package
        if (algorithm.equalsString("rs256")) {
            PublicKey publicKey = getPublicKeyFromString(((JWTAsymmetricSigningKeyInfo) keyToUse).publicKey, algorithm);
            PrivateKey privateKey = getPrivateKeyFromString(((JWTAsymmetricSigningKeyInfo) keyToUse).privateKey,
                    algorithm);

            if (publicKey instanceof RSAPublicKey && privateKey instanceof RSAPrivateKey) {
                return Algorithm.RSA256((RSAPublicKey) publicKey, (RSAPrivateKey) privateKey);
            }
        }

        throw new UnsupportedJWTSigningAlgorithmException();
    }

    private static PublicKey getPublicKeyFromString(String keyCert, JWTSigningKey.SupportedAlgorithms algorithm)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        byte[] decodedKeyBytes = Base64.getDecoder().decode(keyCert);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(decodedKeyBytes);
        KeyFactory kf = KeyFactory.getInstance(algorithm.getAlgorithmType());
        return kf.generatePublic(keySpec);
    }

    private static PrivateKey getPrivateKeyFromString(String keyCert, JWTSigningKey.SupportedAlgorithms algorithm)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        byte[] decodedKeyBytes = Base64.getDecoder().decode(keyCert);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decodedKeyBytes);
        KeyFactory kf = KeyFactory.getInstance(algorithm.getAlgorithmType());
        return kf.generatePrivate(keySpec);
    }

}
