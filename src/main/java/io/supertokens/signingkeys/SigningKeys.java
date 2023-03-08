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
import io.supertokens.jwt.exceptions.UnsupportedJWTSigningAlgorithmException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.jwt.JWTAsymmetricSigningKeyInfo;
import io.supertokens.pluginInterface.jwt.JWTSigningKeyInfo;
import io.supertokens.utils.Utils;

import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.supertokens.utils.Utils.getPublicKeyFromString;

public class SigningKeys extends ResourceDistributor.SingletonResource {
    private static final String RESOURCE_KEY = "io.supertokens.signingKeys.SigningKeys";
    private final Main main;

    private List<KeyInfo> dynamicKeys;
    private List<JWTSigningKeyInfo> staticKeys;

    private SigningKeys(Main main) {
        this.main = main;
    }

    public static void init(Main main) {
        SigningKeys instance = (SigningKeys) main.getResourceDistributor()
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
            throw new IllegalStateException("SigningKeys have not been initialized");
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
            getDynamicKeys().stream().map(Utils::getJWTSigningKeyInfoFromKeyInfo),
            getStaticKeys().stream()
        ).collect(Collectors.toList());
    }

    public List<KeyInfo> getDynamicKeys() throws StorageQueryException, StorageTransactionLogicException {
        CoreConfig config = Config.getConfig(main);

        if (this.dynamicKeys == null) {
            this.dynamicKeys = AccessTokenSigningKey.getInstance(main).getOrCreateAndGetSigningKeys();
        }

        // This filters the list down to keys that can be used to verify tokens
        List<KeyInfo> res = this.dynamicKeys.stream().filter(k -> k.expiryTime >= System.currentTimeMillis()).collect(Collectors.toList());

        // Here we check if the first (and latest) key can be used to sign new tokens (and that it exists)
        if (res.size() == 0 || System.currentTimeMillis() > res.get(0).createdAtTime + config.getAccessTokenDynamicSigningKeyUpdateInterval()) {

            updateKeyCacheIfNotChanged(res.stream().map(Utils::getJWTSigningKeyInfoFromKeyInfo).collect(Collectors.toList()));
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

    public JWTSigningKeyInfo getStaticKeyForAlgorithm(JWTSigningKey.SupportedAlgorithms algorithm)
            throws StorageQueryException, StorageTransactionLogicException, UnsupportedJWTSigningAlgorithmException {
        JWTSigningKeyInfo key = JWTSigningKey.getInstance(main).getOrCreateAndGetKeyForAlgorithm(algorithm);

        List<JWTSigningKeyInfo> keyCache = getAllKeys();
        // if the new key is not in the cache, we know we need to refresh it, except if something in the background already refreshed it
        if (keyCache.stream().noneMatch(k -> Objects.equals(k.keyId, key.keyId))) {
            updateKeyCacheIfNotChanged(keyCache);
        }

        return key;
    }

    public KeyInfo getLatestIssuedDynamicKey() throws StorageQueryException, StorageTransactionLogicException {
        return getDynamicKeys().get(0);
    }

    public long getDynamicSigningKeyExpiryTime() throws StorageQueryException, StorageTransactionLogicException {
        long createdAtTime = getLatestIssuedDynamicKey().createdAtTime;
        return createdAtTime + Config.getConfig(main).getAccessTokenDynamicSigningKeyUpdateInterval();
    }

    // This function is synchronized because we only want a single function to clear (and refresh) the key cache.
    // If multiple threads try to refresh it at the same time, we can avoid multiple trips to the DB by checking if their info is
    // up-to-date, i.e.: if all currently cached keys were known to them.
    public synchronized void updateKeyCacheIfNotChanged(List<JWTSigningKeyInfo> oldKeyInfo)
            throws StorageQueryException, StorageTransactionLogicException {
        // we cannot use read write locks for keyInfo because in getKey, we would
        // have to upgrade from the readLock to a
        // writeLock - which is not possible:
        // https://docs.oracle.com/javase/7/docs/api/java/util/concurrent/locks/ReentrantReadWriteLock.html

        if (this.dynamicKeys == null ||
            // First we disregard expired keys - it doesn't matter if they were known or not
            this.dynamicKeys.stream().filter(k -> k.expiryTime >= System.currentTimeMillis())
                // then check if all keys currently in the cache exists in the parameter
                .allMatch(storedKey -> oldKeyInfo.stream().anyMatch(oldKey -> Objects.equals(oldKey.keyId, storedKey.id)))) {
            // key has not changed since we previously tried to use it... So we update it from the db, creating a new key if necessary
            ProcessState.getInstance(this.main)
                    .addState(ProcessState.PROCESS_STATE.UPDATING_ACCESS_TOKEN_SIGNING_KEYS, null);
            this.dynamicKeys = AccessTokenSigningKey.getInstance(main).getOrCreateAndGetSigningKeys();
        }

        if (this.staticKeys == null ||
            // we want to refresh if all keys we are storing were known before
            this.staticKeys.stream().allMatch(storedKey -> oldKeyInfo.stream().anyMatch(oldKey -> Objects.equals(oldKey.keyId, storedKey.keyId)))) {
            // key has not changed since we previously tried to use it... So we update it from the db
            ProcessState.getInstance(this.main)
                    .addState(ProcessState.PROCESS_STATE.UPDATING_ACCESS_TOKEN_SIGNING_KEYS, null);
            this.staticKeys = JWTSigningKey.getInstance(main).getAllSigningKeys();
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

        // Retrieve all keys in storage
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

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || !(o instanceof KeyInfo)) return false;

            KeyInfo keyInfo = (KeyInfo) o;

            if (createdAtTime != keyInfo.createdAtTime) return false;
            if (expiryTime != keyInfo.expiryTime) return false;
            if (!id.equals(keyInfo.id)) return false;
            if (!value.equals(keyInfo.value)) return false;
            return algorithm.equals(keyInfo.algorithm);
        }

        @Override
        public int hashCode() {
            int result = id.hashCode();
            result = 31 * result + value.hashCode();
            result = 31 * result + (int) (createdAtTime ^ (createdAtTime >>> 32));
            result = 31 * result + (int) (expiryTime ^ (expiryTime >>> 32));
            result = 31 * result + algorithm.hashCode();
            return result;
        }
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
