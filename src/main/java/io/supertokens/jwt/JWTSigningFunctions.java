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

import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.jwt.exceptions.UnsupportedJWTSigningAlgorithmException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.jwt.JWTAsymmetricSigningKeyInfo;
import io.supertokens.pluginInterface.jwt.JWTSigningKeyInfo;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.signingkeys.JWTSigningKey;
import io.supertokens.signingkeys.SigningKeys;
import io.supertokens.utils.Utils;
import org.jetbrains.annotations.TestOnly;

import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class JWTSigningFunctions {

    @TestOnly
    public static String createJWTToken(Main main, String algorithm,
                                        JsonObject payload, String jwksDomain,
                                        long jwtValidityInSeconds, boolean useDynamicKey)
            throws StorageQueryException, StorageTransactionLogicException, NoSuchAlgorithmException,
            InvalidKeySpecException, JWTCreationException, UnsupportedJWTSigningAlgorithmException {
        try {
            return createJWTToken(new AppIdentifier(null, null), main, algorithm, payload, jwksDomain,
                    jwtValidityInSeconds, useDynamicKey);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Creates and returns a JWT string
     *
     * @param main
     * @param algorithm            The signing algorithm to use when creating the token. Refer to
     *                             {@link JWTSigningKey.SupportedAlgorithms}
     * @param payload              JSON object containing user defined claims to be added to the JWT payload
     * @param jwksDomain           Used as the issuer in the JWT payload
     * @param jwtValidityInSeconds Used to set iat and exp claims in the JWT payload
     * @param useDynamicKey        Set to true to use a dynamic key (AccessTokenSigningKey)
     * @return String token
     * @throws StorageQueryException                   If there is an error interacting with the database
     * @throws StorageTransactionLogicException        If there is an error interacting with the database
     * @throws NoSuchAlgorithmException                If there is an error when using Java's cryptography packages
     * @throws InvalidKeySpecException                 If there is an error when using Java's cryptography packages
     * @throws JWTCreationException                    If there is an error when creating JWTs
     * @throws UnsupportedJWTSigningAlgorithmException If the algorithm provided does not match any of the supported
     *                                                 algorithms
     */
    public static String createJWTToken(AppIdentifier appIdentifier, Main main, String algorithm, JsonObject payload,
                                        String jwksDomain, long jwtValidityInSeconds, boolean useDynamicKey)
            throws StorageQueryException, StorageTransactionLogicException, NoSuchAlgorithmException,
            InvalidKeySpecException, JWTCreationException, UnsupportedJWTSigningAlgorithmException,
            TenantOrAppNotFoundException {
        // TODO: In the future we will have a way for the user to send a custom key id to use
        JWTSigningKey.SupportedAlgorithms supportedAlgorithm;

        try {
            supportedAlgorithm = JWTSigningKey.SupportedAlgorithms.valueOf(algorithm);
        } catch (IllegalArgumentException e) {
            // If it enters this block then the string value provided does not match the algorithms we support
            throw new UnsupportedJWTSigningAlgorithmException();
        }

        long issued = System.currentTimeMillis();
        long expires = System.currentTimeMillis() + (jwtValidityInSeconds * 1000);

        JWTSigningKeyInfo keyToUse;
        if (useDynamicKey) {
            keyToUse = Utils.getJWTSigningKeyInfoFromKeyInfo(
                    SigningKeys.getInstance(appIdentifier, main).getLatestIssuedDynamicKey());
        } else {
            keyToUse = SigningKeys.getInstance(appIdentifier, main)
                    .getStaticKeyForAlgorithm(JWTSigningKey.SupportedAlgorithms.RS256);
        }

        return createJWTToken(supportedAlgorithm, new HashMap<>(), payload, jwksDomain, expires, issued, keyToUse);
    }

    @SuppressWarnings("unchecked")
    public static String createJWTToken(JWTSigningKey.SupportedAlgorithms supportedAlgorithm,
                                        Map<String, Object> headerClaims, JsonObject payload, String jwksDomain,
                                        long jwtExpiryInMs, long jwtIssuedAtInMs, JWTSigningKeyInfo keyToUse)
            throws StorageQueryException, StorageTransactionLogicException, NoSuchAlgorithmException,
            InvalidKeySpecException, JWTCreationException, UnsupportedJWTSigningAlgorithmException,
            TenantOrAppNotFoundException {
        // Get an instance of auth0's Algorithm which is needed when signing using auth0's package
        Algorithm signingAlgorithm = getAuth0Algorithm(supportedAlgorithm, keyToUse);

        // Create the claims for the JWT header
        headerClaims.put("alg", supportedAlgorithm.name().toUpperCase()); // All examples in the RFC have the algorithm
        // in upper case
        headerClaims.put("typ", "JWT");
        headerClaims.put("kid", keyToUse.keyId);

        // Add relevant claims to the payload, note we only add/override ones that we absolutely need to.
        if (jwksDomain != null && !payload.has("iss")) {
            payload.addProperty("iss", jwksDomain);
        }

        JWTCreator.Builder builder = com.auth0.jwt.JWT.create();
        builder.withKeyId(keyToUse.keyId);
        builder.withHeader(headerClaims);
        builder.withIssuedAt(new Date(jwtIssuedAtInMs));
        builder.withExpiresAt(new Date(jwtExpiryInMs));

        if (jwksDomain != null) {
            builder.withIssuer(jwksDomain);
        }
        builder.withPayload(payload.toString());

        return builder.sign(signingAlgorithm);
    }

    private static Algorithm getAuth0Algorithm(JWTSigningKey.SupportedAlgorithms algorithm, JWTSigningKeyInfo keyToUse)
            throws NoSuchAlgorithmException, InvalidKeySpecException, UnsupportedJWTSigningAlgorithmException {
        // TODO: Abstract this away from the main package to avoid a direct dependency on auth0s package
        if (algorithm.equalsString("rs256")) {
            PublicKey publicKey = Utils.getPublicKeyFromString(((JWTAsymmetricSigningKeyInfo) keyToUse).publicKey,
                    algorithm);
            PrivateKey privateKey = Utils.getPrivateKeyFromString(((JWTAsymmetricSigningKeyInfo) keyToUse).privateKey,
                    algorithm);

            if (publicKey instanceof RSAPublicKey && privateKey instanceof RSAPrivateKey) {
                return Algorithm.RSA256((RSAPublicKey) publicKey, (RSAPrivateKey) privateKey);
            }
        }

        throw new UnsupportedJWTSigningAlgorithmException();
    }
}
