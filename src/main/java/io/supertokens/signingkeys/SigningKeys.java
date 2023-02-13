/*
 *    Copyright (c) 2023, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.signingkeys;

import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.ResourceDistributor;
import io.supertokens.config.Config;
import io.supertokens.config.CoreConfig;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.jwt.JWTAsymmetricSigningKeyInfo;
import io.supertokens.pluginInterface.jwt.JWTSigningKeyInfo;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SigningKeys extends ResourceDistributor.SingletonResource {
    private static final String RESOURCE_KEY = "io.supertokens.signingKeys.SigningKeys";
    private final Main main;

    private List<KeyInfo> dynamicKeys;
    private List<JWTSigningKeyInfo> staticKeys;

    private SigningKeys(Main main) {
        this.main = main;
    }

    public static void init(Main main) {
        AccessTokenSigningKey instance = (AccessTokenSigningKey) main.getResourceDistributor()
                .getResource(RESOURCE_KEY);
        if (instance != null) {
            return;
        }
        main.getResourceDistributor().setResource(RESOURCE_KEY, new SigningKeys(main));
    }

    public static SigningKeys getInstance(Main main) {
        SigningKeys instance = (SigningKeys) main.getResourceDistributor()
                .getResource(RESOURCE_KEY);
        if (instance == null) {
            init(main);
        }
        return (SigningKeys) main.getResourceDistributor().getResource(RESOURCE_KEY);
    }

    public JWTSigningKeyInfo getSigningKeyById(String kid) throws StorageQueryException, StorageTransactionLogicException {
        return this.getAllKeys().stream()
                .filter(jwtSigningKeyInfo -> Objects.equals(jwtSigningKeyInfo.keyId, kid)).findAny()
                .orElse(null);
    }

    public List<JWTSigningKeyInfo> getAllKeys()
            throws StorageQueryException, StorageTransactionLogicException {

        return Stream.concat(
            getDynamicKeys().stream().map(SigningKeys::getJWTSigningKeyInfoFromKeyInfo),
            getStaticKeys().stream()
        ).collect(Collectors.toList());
    }

    public List<KeyInfo> getDynamicKeys() throws StorageQueryException, StorageTransactionLogicException {
        CoreConfig config = Config.getConfig(main);

        if (this.dynamicKeys == null) {
            this.dynamicKeys = AccessTokenSigningKey.getInstance(main).getOrCreateAndGetSigningKeys();
        }

        List<KeyInfo> res = this.dynamicKeys.stream().filter(k -> k.expiryTime >= System.currentTimeMillis()).collect(Collectors.toList());

        if (res.size() == 0 || System.currentTimeMillis() > res.get(0).createdAtTime
                + config.getAccessTokenDynamicSigningKeyUpdateInterval()) {
            this.dynamicKeys = null;
            return getDynamicKeys();
        }

        return res;
    }

    public List<JWTSigningKeyInfo> getStaticKeys() throws StorageQueryException, StorageTransactionLogicException {
        if (this.staticKeys == null) {
            this.staticKeys = JWTSigningKey.getInstance(main).getAllSigningKeys();
        }

        return this.staticKeys;
    }
    public KeyInfo getLatestIssuedDynamicKey() throws StorageQueryException, StorageTransactionLogicException {
        return getDynamicKeys().get(0);
    }

    public synchronized long getDynamicSigningKeyExpiryTime() throws StorageQueryException, StorageTransactionLogicException {
        long createdAtTime = getLatestIssuedDynamicKey().createdAtTime;
        return createdAtTime + Config.getConfig(main).getAccessTokenDynamicSigningKeyUpdateInterval();
    }

    public synchronized void removeKeyFromMemoryIfItHasNotChanged(List<JWTSigningKeyInfo> oldKeyInfo)
            throws StorageQueryException, StorageTransactionLogicException {
        // we cannot use read write locks for keyInfo because in getKey, we would
        // have to upgrade from the readLock to a
        // writeLock - which is not possible:
        // https://docs.oracle.com/javase/7/docs/api/java/util/concurrent/locks/ReentrantReadWriteLock.html

        if (this.dynamicKeys != null && this.staticKeys != null &&
                // we want to refresh if all keys we are storing were known before
            this.getAllKeys().stream().allMatch(storedKey -> oldKeyInfo.stream().anyMatch(oldKey -> Objects.equals(oldKey.keyId, storedKey.keyId)))) {
            // key has not changed since we previously tried to use it... So we can make it null.
            // otherwise we might end up making this null unnecessarily.

            ProcessState.getInstance(this.main)
                    .addState(ProcessState.PROCESS_STATE.SETTING_ACCESS_TOKEN_SIGNING_KEY_TO_NULL, null);
            this.staticKeys = null;
            this.dynamicKeys = null;
        }
    }

    /**
     * Used to return public keys that a JWT verifier will use. Note returns an empty array if there are no keys in
     * storage.
     *
     * @return JSON array containing the JWKs
     * @throws StorageQueryException            If there is an error interacting with the database
     * @throws StorageTransactionLogicException If there is an error interacting with the database
     * @throws NoSuchAlgorithmException         If there is an error when using Java's cryptography packages
     * @throws InvalidKeySpecException          If there is an error when using Java's cryptography packages
     */
    public List<JsonObject> getJWKS() throws StorageQueryException, StorageTransactionLogicException,
            NoSuchAlgorithmException, InvalidKeySpecException {
        List<JsonObject> jwks = new ArrayList<>();

        // Retrieve all static keys in storage
        List<JWTSigningKeyInfo> keys = this.getAllKeys();
        for (JWTSigningKeyInfo currentKeyInfo : keys) {
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

    public static class KeyInfo {
        public String id;
        public String value;
        public long createdAtTime;
        public long expiryTime;
        public String algorithm;


        KeyInfo(String id, String value, long createdAtTime, String algorithm) {
            this(id, value, createdAtTime, Long.MAX_VALUE, algorithm);
        }

        KeyInfo(String id, String value, long createdAtTime, long validityDuration, String algorithm) {
            this.id = id;
            this.value = value;
            this.createdAtTime = createdAtTime;
            this.expiryTime = createdAtTime + validityDuration;
            this.algorithm = algorithm;
        }
    }

    public static PublicKey getPublicKeyFromString(String keyCert, JWTSigningKey.SupportedAlgorithms algorithm)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        byte[] decodedKeyBytes = Base64.getDecoder().decode(keyCert);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(decodedKeyBytes);
        KeyFactory kf = KeyFactory.getInstance(algorithm.getAlgorithmType());
        return kf.generatePublic(keySpec);
    }

    public static PrivateKey getPrivateKeyFromString(String keyCert, JWTSigningKey.SupportedAlgorithms algorithm)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        byte[] decodedKeyBytes = Base64.getDecoder().decode(keyCert);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decodedKeyBytes);
        KeyFactory kf = KeyFactory.getInstance(algorithm.getAlgorithmType());
        return kf.generatePrivate(keySpec);
    }

    public static JWTSigningKeyInfo getJWTSigningKeyInfoFromKeyInfo(KeyInfo keyInfo) {
        return new JWTAsymmetricSigningKeyInfo(keyInfo.id, keyInfo.createdAtTime, keyInfo.algorithm, keyInfo.value);
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
}
