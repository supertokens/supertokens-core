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

package io.supertokens.utils;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.Phonenumber;
import io.supertokens.Main;
import io.supertokens.config.Config;
import io.supertokens.jwt.exceptions.UnsupportedJWTSigningAlgorithmException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.jwt.JWTAsymmetricSigningKeyInfo;
import io.supertokens.pluginInterface.jwt.JWTSigningKeyInfo;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.signingkeys.JWTSigningKey;
import io.supertokens.signingkeys.SigningKeys;
import io.supertokens.signingkeys.SigningKeys.KeyInfo;

import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Base64.Decoder;
import java.util.Base64.Encoder;
import java.util.List;
import java.util.UUID;

public class Utils {

    /**
     * Normalizes a phone number by trimming and formatting it according to the
     * E.164 standard.
     * <p>
     * The function attempts to parse the given phone number using libphonenumber.
     * If parsing is successful,
     * it formats the phone number according to the E.164 standard. If parsing fails
     * (throws a NumberParseException),
     * it still trims the input and returns the original trimmed phone number.
     *
     * @param phoneNumber The input phone number to be normalized.
     * @return The normalized phone number or the original trimmed phone number if
     * it cannot be parsed.
     */
    public static String normalizeIfPhoneNumber(String phoneNumber) {
        if (phoneNumber == null) {
            return null;
        }

        PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();

        try {
            // Attempt to parse the phone number with default region code "ZZ" (unknown
            // region)
            Phonenumber.PhoneNumber parsedPhoneNumber = phoneNumberUtil.parse(phoneNumber.trim(), "ZZ");

            // Format the parsed phone number according to E.164 standard
            phoneNumber = phoneNumberUtil.format(parsedPhoneNumber, PhoneNumberUtil.PhoneNumberFormat.E164);
        } catch (NumberParseException e) {
            // Parsing failed, use the original trimmed phone number
            phoneNumber = phoneNumber.trim();
        }

        return phoneNumber;
    }

    public static String normaliseEmail(String email) {
        if (email == null) {
            return null;
        }
        // we assume that the email's syntax is correct here.

        // as per https://github.com/supertokens/supertokens-core/issues/89 and
        // https://github.com/supertokens/supertokens-core/issues/171
        email = email.trim();
        email = email.toLowerCase();

        return email;
    }

    public static String convertToBase64Url(String str) {
        return new String(Base64.getUrlEncoder().encode(stringToBytes(str)), StandardCharsets.UTF_8);
    }

    public static String convertToBase64(String str) {
        return new String(Base64.getEncoder().encode(stringToBytes(str)), StandardCharsets.UTF_8);
    }

    // This function deserializes both B64 and B64URL encodings
    public static String convertFromBase64(String str) {
        return new String(Base64.getDecoder().decode(stringToBytes(str.replace("-", "+").replace("_", "/"))),
                StandardCharsets.UTF_8);
    }

    public static String throwableStacktraceToString(Throwable e) {
        if (e == null) {
            return "";
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PrintStream ps = new PrintStream(baos)) {
            e.printStackTrace(ps);
        }
        return baos.toString();
    }

    public static String hashSHA256(String base) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(stringToBytes(base));
        return bytesToString(hash);
    }

    public static byte[] hashSHA256Bytes(byte[] base) throws NoSuchAlgorithmException {
        return MessageDigest.getInstance("SHA-256").digest(base);
    }

    public static String hashSHA256Base64UrlSafe(byte[] base) throws NoSuchAlgorithmException {
        byte[] hashBytes = Utils.hashSHA256Bytes(base);
        return Base64.getUrlEncoder().encodeToString(hashBytes);
    }

    public static String hashSHA256Base64(byte[] base) throws NoSuchAlgorithmException {
        byte[] hashBytes = Utils.hashSHA256Bytes(base);
        return Base64.getEncoder().encodeToString(hashBytes);
    }

    public static byte[] hmacSHA256(byte[] key, String data) throws InvalidKeyException, NoSuchAlgorithmException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(stringToBytes(data));
    }

    public static String generateNewSigningKey() throws NoSuchAlgorithmException, InvalidKeySpecException {

        byte[] random = new byte[64];
        byte[] salt = new byte[64];

        new SecureRandom().nextBytes(random);
        new SecureRandom().nextBytes(salt);

        int iterations = 1000;
        return iterations + ":" + toHex(salt) + ":"
                + toHex(pbkdf2(bytesToString(random).toCharArray(), salt, iterations, 64 * 8));
    }

    public static String bytesToString(byte[] bArr) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bArr) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static byte[] stringToBytes(String str) {
        return str.getBytes(StandardCharsets.UTF_8);
    }

    public static String toHex(byte[] array) {
        BigInteger bi = new BigInteger(1, array);
        String hex = bi.toString(16);
        int paddingLength = (array.length * 2) - hex.length();
        if (paddingLength > 0) {
            return String.format("%0" + paddingLength + "d", 0) + hex;
        } else {
            return hex;
        }
    }

    public static String encrypt(String plaintext, String masterKey)
            throws NoSuchAlgorithmException, InvalidKeySpecException, NoSuchPaddingException, InvalidKeyException,
            InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException {

        // Prepare the nonce
        SecureRandom secureRandom = new SecureRandom();

        // Nonce should be 12 bytes
        byte[] iv = new byte[12];
        secureRandom.nextBytes(iv);

        // Prepare your key/password
        byte[] key = pbkdf2(masterKey.toCharArray(), iv, 100, 32 * 8);
        SecretKey secretKey = new SecretKeySpec(key, "AES");

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iv);

        // Encryption mode on!
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

        // Encrypt the data
        byte[] encryptedData = cipher.doFinal(stringToBytes(plaintext));

        // Concatenate everything and return the final data
        ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + encryptedData.length);
        byteBuffer.put(iv);
        byteBuffer.put(encryptedData);

        Encoder encoder = Base64.getEncoder();
        return encoder.encodeToString(byteBuffer.array());
    }

    /**
     * Decrypts text by given key
     *
     * @param encdata   - base64 encoded input data
     * @param masterkey - key used to decrypt
     * @return String decrypted (original) text
     */
    public static String decrypt(String encdata, String masterkey)
            throws NoSuchAlgorithmException, InvalidKeySpecException, NoSuchPaddingException, InvalidKeyException,
            InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException {

        // Wrap the data into a byte buffer to ease the reading process
        Decoder decoder = Base64.getDecoder();
        ByteBuffer byteBuffer = ByteBuffer.wrap(decoder.decode(encdata));

        byte[] iv = new byte[12];
        byteBuffer.get(iv);

        // Prepare your key/password
        SecretKey secretKey = new SecretKeySpec(Utils.pbkdf2(masterkey.toCharArray(), iv, 100, 32 * 8), "AES");

        // get the rest of encrypted data
        byte[] cipherBytes = new byte[byteBuffer.remaining()];
        byteBuffer.get(cipherBytes);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iv);

        // Encryption mode on!
        cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

        // Encrypt the data
        return new String(cipher.doFinal(cipherBytes));
    }

    public static byte[] pbkdf2(char[] text, byte[] salt, int iterationCount, int keyLength)
            throws NoSuchAlgorithmException, InvalidKeySpecException {

        KeySpec spec = new PBEKeySpec(text, salt, iterationCount, keyLength);
        SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512");
        return f.generateSecret(spec).getEncoded();
    }

    public static PubPriKey generateNewPubPriKey() throws NoSuchAlgorithmException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        PublicKey pub = kp.getPublic();
        PrivateKey pvt = kp.getPrivate();

        Base64.Encoder encoder = Base64.getEncoder();
        String pubStr = encoder.encodeToString(pub.getEncoded());
        String priStr = encoder.encodeToString(pvt.getEncoded());
        return new PubPriKey(pubStr, priStr);
    }

    public static String signWithPrivateKey(String content, String privateKey, boolean urlEncode)
            throws NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException, SignatureException {
        Signature sign = Signature.getInstance("SHA256withRSA");
        Base64.Decoder decoder = Base64.getDecoder();
        PKCS8EncodedKeySpec ks = new PKCS8EncodedKeySpec(decoder.decode(privateKey));
        KeyFactory kf = KeyFactory.getInstance("RSA");
        PrivateKey pvt = kf.generatePrivate(ks);

        sign.initSign(pvt);
        sign.update(stringToBytes(content));
        Base64.Encoder encoder = urlEncode ? Base64.getUrlEncoder() : Base64.getEncoder();
        return encoder.encodeToString(sign.sign());
    }

    public static boolean verifyWithPublicKey(String content, String signature, String publicKey, boolean urlEncoded)
            throws NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException, SignatureException {
        Signature sign = Signature.getInstance("SHA256withRSA");
        Base64.Decoder keyDecoder = Base64.getDecoder();
        X509EncodedKeySpec ks = new X509EncodedKeySpec(keyDecoder.decode(publicKey));
        KeyFactory kf = KeyFactory.getInstance("RSA");
        PublicKey pub = kf.generatePublic(ks);

        Base64.Decoder decoder = urlEncoded ? Base64.getUrlDecoder() : Base64.getDecoder();
        sign.initVerify(pub);
        sign.update(stringToBytes(content));
        return sign.verify(decoder.decode(signature));
    }

    public static class PubPriKey {
        public String publicKey;
        public String privateKey;

        PubPriKey(String publicKey, String privateKey) {
            this.publicKey = publicKey;
            this.privateKey = privateKey;
        }

        public PubPriKey(String s) {
            // We split by both | and ; because in old versions we used to use ";" in
            // dynamic and "|" in static keys
            // Now we are consolidating all of them to use "|", but by handling legacy keys,
            // we can avoid the need
            // for manual key migration.
            // I.e.: this way only people who set access_token_signing_key_dynamic to false
            // has to do manual
            // migration instead of everyone.
            // for everyone else, the key rotation should get it done.
            String[] parts = s.split("[|;]");

            this.publicKey = parts[0];
            this.privateKey = parts[1];
        }

        @Override
        public String toString() {
            return publicKey + "|" + privateKey;
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

    public static String getUUID() {
        return UUID.randomUUID().toString();
    }

    public static String exceptionStacktraceToString(Exception e) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);
        e.printStackTrace(ps);
        ps.close();
        return baos.toString();
    }

    public static JsonObject addLegacySigningKeyInfos(AppIdentifier appIdentifier, Main main, JsonObject result,
                                                      boolean addKeyList)
            throws StorageQueryException, StorageTransactionLogicException, UnsupportedJWTSigningAlgorithmException,
            TenantOrAppNotFoundException {
        if (Config.getConfig(appIdentifier.getAsPublicTenantIdentifier(), main).getAccessTokenSigningKeyDynamic()) {
            result.addProperty("jwtSigningPublicKey",
                    new Utils.PubPriKey(
                            SigningKeys.getInstance(appIdentifier, main).getLatestIssuedDynamicKey().value).publicKey);
            result.addProperty("jwtSigningPublicKeyExpiryTime",
                    SigningKeys.getInstance(appIdentifier, main).getDynamicSigningKeyExpiryTime());

            if (addKeyList) {
                List<KeyInfo> keys = SigningKeys.getInstance(appIdentifier, main).getDynamicKeys();

                JsonArray jwtSigningPublicKeyListJSON = new JsonArray();
                for (KeyInfo keyInfo : keys) {
                    JsonObject keyJSON = new JsonObject();
                    keyJSON.addProperty("publicKey", new PubPriKey(keyInfo.value).publicKey);
                    keyJSON.addProperty("expiryTime", keyInfo.expiryTime);
                    keyJSON.addProperty("createdAt", keyInfo.createdAtTime);
                    jwtSigningPublicKeyListJSON.add(keyJSON);
                }

                result.add("jwtSigningPublicKeyList", jwtSigningPublicKeyListJSON);
            }
        } else {
            JWTSigningKeyInfo keyInfo = SigningKeys.getInstance(appIdentifier, main)
                    .getStaticKeyForAlgorithm(JWTSigningKey.SupportedAlgorithms.RS256);
            result.addProperty("jwtSigningPublicKey", new Utils.PubPriKey(keyInfo.keyString).publicKey);
            result.addProperty("jwtSigningPublicKeyExpiryTime", 10L * 365 * 24 * 3600 * 1000);

            if (addKeyList) {
                JsonArray jwtSigningPublicKeyListJSON = new JsonArray();
                JsonObject keyJSON = new JsonObject();
                keyJSON.addProperty("publicKey", new Utils.PubPriKey(keyInfo.keyString).publicKey);
                keyJSON.addProperty("expiryTime", keyInfo.createdAtTime + 10L * 365 * 24 * 3600 * 1000);
                keyJSON.addProperty("createdAt", keyInfo.createdAtTime);
                jwtSigningPublicKeyListJSON.add(keyJSON);
                result.add("jwtSigningPublicKeyList", jwtSigningPublicKeyListJSON);
            }
        }

        return result;
    }

    public static JsonElement toJsonTreeWithNulls(Object src) {
        return new GsonBuilder().serializeNulls().create().toJsonTree(src);
    }
}
