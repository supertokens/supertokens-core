/*
 *    Copyright (c) 2022, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.emailpassword;

import com.lambdaworks.crypto.SCrypt;
import io.supertokens.emailpassword.exceptions.UnsupportedPasswordHashingFormatException;
import org.apache.tomcat.util.codec.binary.Base64;

import javax.annotation.Nullable;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Objects;

public class PasswordHashingUtils {

    public static class ParsedFireBaseSCryptResponse {
        String passwordHash;
        String salt;
        String saltSeparator;
        int rounds;
        int memCost;

        public ParsedFireBaseSCryptResponse(String passwordHash, String salt, String saltSeparator, int rounds,
                int memCost) {
            this.passwordHash = passwordHash;
            this.salt = salt;
            this.saltSeparator = saltSeparator;
            this.rounds = rounds;
            this.memCost = memCost;
        }
    }

    private static final String FIREBASE_SCRYPT_PREFIX = "f_scrypt";
    private static final String FIREBASE_SCRYPT_SEPARATOR = "\\$";
    private static final String FIREBASE_SCRYPT_MEM_COST_SEPARATOR = "m=";
    private static final String FIREBASE_SCRYPT_ROUNDS_SEPARATOR = "r=";
    private static final String FIREBASE_SCRYPT_SALT_SEPARATOR = "s=";

    public static String replaceUnsupportedIdentifierForBcryptPasswordHashVerification(String hash) {
        // JbCrypt only supports $2a as the identifier. Identifiers like $2b, $2x and $2y are not recognized by JBcrypt
        // even though the actual password hash can be verified.
        // We can simply replace the identifier with $2a and BCrypt will also be able to verify password hashes
        // generated with other identifiers
        if (hash.startsWith("$2b") || hash.startsWith("$2x") || hash.startsWith("$2y")) {
            // we replace the unsupported identifier with $2a
            return "$2a" + hash.substring(3);
        }
        return hash;
    }

    public static void assertSuperTokensSupportInputPasswordHashFormat(String passwordHash,
            @Nullable PasswordHashingAlgorithm hashingAlgorithm) throws UnsupportedPasswordHashingFormatException {
        if (hashingAlgorithm == null) {
            if (!(isInputHashInBcryptFormat(passwordHash) || isInputHashInArgon2Format(passwordHash)
                    || isInputHashInFirebaseSCryptFormat(passwordHash))) {
                throw new UnsupportedPasswordHashingFormatException("Password hash is in invalid format");
            }
            return;
        }
        if (hashingAlgorithm.equals(PasswordHashingAlgorithm.ARGON2)) {
            if (!isInputHashInArgon2Format(passwordHash)) {
                throw new UnsupportedPasswordHashingFormatException("Password hash is in invalid Argon2 format");
            }
            return;
        }
        if (hashingAlgorithm.equals(PasswordHashingAlgorithm.BCRYPT)) {
            if (!isInputHashInBcryptFormat(passwordHash)) {
                throw new UnsupportedPasswordHashingFormatException("Password hash is in invalid BCrypt format");
            }
        }

        if (hashingAlgorithm.equals(PasswordHashingAlgorithm.FIREBASE_SCRYPT)) {
            if (!isInputHashInFirebaseSCryptFormat(passwordHash)) {
                throw new UnsupportedPasswordHashingFormatException(
                        "Password hash is in invalid Firebase SCrypt format");
            }
        }
    }

    public static String updatePasswordHashWithPrefixIfRequired(String passwordHash,
            PasswordHashingAlgorithm hashingAlgorithm) {
        if (hashingAlgorithm == null) {
            return passwordHash;
        }

        if (hashingAlgorithm == PasswordHashingAlgorithm.FIREBASE_SCRYPT) {
            if (doesPasswordHashHaveFireBaseSCryptPrefix(passwordHash)) {
                return passwordHash;
            }
            return addFirebaseSCryptPrefixToPasswordHash(passwordHash);
        }

        return passwordHash;
    }

    public static boolean isInputHashInBcryptFormat(String hash) {
        // bcrypt hash starts with the algorithm identifier which can be $2a$, $2y$, $2b$ or $2x$,
        // the number of rounds, the salt and finally the hashed password value.
        return (hash.startsWith("$2a") || hash.startsWith("$2x") || hash.startsWith("$2y") || hash.startsWith("$2b"));
    }

    public static boolean isInputHashInArgon2Format(String hash) {
        // argon2 hash looks like $argon2id or $argon2d or $argon2i $v=..$m=..,t=..,p=..$tgSmiYOCjQ0im5U6...
        return (hash.startsWith("$argon2id") || hash.startsWith("$argon2i") || hash.startsWith("$argon2d"));
    }

    public static boolean isInputHashInFirebaseSCryptFormat(String hash) {
        // the firebase scrypt hash we store is a "$" separated string containing an algorithm identifier prefix,
        // password hash, salt, memory cost, rounds, salt separator
        // eg. f_scrypt$passwordHash$salt$m=memorycost$r=rounds$s=saltSeparator

        if (doesPasswordHashHaveFireBaseSCryptPrefix(hash)) {
            String[] separatedHash = hash.split(FIREBASE_SCRYPT_SEPARATOR);
            if (separatedHash.length != 6) {
                return false;
            }

            boolean containsMemCost = false;
            boolean containsRounds = false;
            boolean containsSaltSeparator = false;

            for (int i = 3; i < separatedHash.length; i++) {
                // does hash contain memory cost
                if (separatedHash[i].startsWith(FIREBASE_SCRYPT_MEM_COST_SEPARATOR)) {
                    // check that memory cost is a number
                    try {
                        Integer.parseInt(separatedHash[3].split(FIREBASE_SCRYPT_MEM_COST_SEPARATOR)[1]);
                        containsMemCost = true;
                    } catch (NumberFormatException e) {
                        return false;
                    }
                }

                // does hash contain rounds
                if (separatedHash[i].startsWith(FIREBASE_SCRYPT_ROUNDS_SEPARATOR)) {
                    // check that rounds is a number
                    try {
                        Integer.parseInt(separatedHash[4].split(FIREBASE_SCRYPT_ROUNDS_SEPARATOR)[1]);
                        containsRounds = true;
                    } catch (NumberFormatException e) {
                        return false;
                    }
                }

                // does hash contain salt separator
                if (separatedHash[i].startsWith(FIREBASE_SCRYPT_SALT_SEPARATOR)) {
                    containsSaltSeparator = true;
                }

            }

            return (containsMemCost && containsRounds && containsSaltSeparator);
        }
        return false;
    }

    private static boolean doesPasswordHashHaveFireBaseSCryptPrefix(String passwordHash) {
        return passwordHash.startsWith(FIREBASE_SCRYPT_PREFIX);
    }

    private static String addFirebaseSCryptPrefixToPasswordHash(String passwordHash) {
        return FIREBASE_SCRYPT_PREFIX + "$" + passwordHash;
    }

    private static ParsedFireBaseSCryptResponse retrieveFirebaseSCryptInfoFromStoredPasswordHash(String hash) {

        // split string using the separator
        String[] separatedPasswordHash = hash.split(FIREBASE_SCRYPT_SEPARATOR);
        String passwordHash = separatedPasswordHash[1];
        String salt = separatedPasswordHash[2];
        String saltSeparator = null;
        Integer memCost = null;
        Integer rounds = null;

        for (int i = 3; i < separatedPasswordHash.length; i++) {
            if (separatedPasswordHash[i].startsWith(FIREBASE_SCRYPT_MEM_COST_SEPARATOR)) {
                memCost = Integer.parseInt(separatedPasswordHash[i].split(FIREBASE_SCRYPT_MEM_COST_SEPARATOR)[1]);
                continue;
            }
            if (separatedPasswordHash[i].startsWith(FIREBASE_SCRYPT_ROUNDS_SEPARATOR)) {
                rounds = Integer.parseInt(separatedPasswordHash[i].split(FIREBASE_SCRYPT_ROUNDS_SEPARATOR)[1]);
                continue;
            }
            if (separatedPasswordHash[i].startsWith(FIREBASE_SCRYPT_SALT_SEPARATOR)) {
                saltSeparator = separatedPasswordHash[i].split(FIREBASE_SCRYPT_SALT_SEPARATOR)[1];
            }
        }

        if (passwordHash == null || salt == null || saltSeparator == null || memCost == null || rounds == null) {
            throw new IllegalStateException("Missing params in Firebase SCrypt password hash");
        }
        return new ParsedFireBaseSCryptResponse(passwordHash, salt, saltSeparator, rounds, memCost);
    }

    public static boolean verifyFirebaseSCryptPasswordHash(String plainTextPassword, String passwordHash,
            String base64_signer_key) {

        ParsedFireBaseSCryptResponse response = retrieveFirebaseSCryptInfoFromStoredPasswordHash(passwordHash);

        passwordHash = response.passwordHash;
        String salt = response.salt;
        int memCost = response.memCost;
        int rounds = response.rounds;
        String saltSep = response.saltSeparator;

        int N = 1 << memCost;
        int p = 1;

        // concatenating decoded salt + separator
        byte[] decodedSaltBytes = Base64.decodeBase64(salt.getBytes(StandardCharsets.US_ASCII));
        byte[] decodedSaltSepBytes = Base64.decodeBase64(saltSep.getBytes(StandardCharsets.US_ASCII));

        byte[] saltConcat = new byte[decodedSaltBytes.length + decodedSaltSepBytes.length];
        System.arraycopy(decodedSaltBytes, 0, saltConcat, 0, decodedSaltBytes.length);
        System.arraycopy(decodedSaltSepBytes, 0, saltConcat, decodedSaltBytes.length, decodedSaltSepBytes.length);

        // hashing password
        byte[] hashedBytes;
        try {
            hashedBytes = SCrypt.scrypt(plainTextPassword.getBytes(StandardCharsets.US_ASCII), saltConcat, N, rounds, p,
                    64);
        } catch (GeneralSecurityException e) {
            return false;
        }
        // encrypting with aes
        byte[] signerBytes = Base64.decodeBase64(base64_signer_key.getBytes(StandardCharsets.US_ASCII));
        byte[] cipherTextBytes = encrypt(signerBytes, hashedBytes);

        return new String(Objects.requireNonNull(Base64.encodeBase64(cipherTextBytes))).equals(passwordHash);
    }

    private static byte[] encrypt(byte[] signer, byte[] derivedKey) {
        String CIPHER = "AES/CTR/NoPadding";
        try {
            Key key = new SecretKeySpec(derivedKey, 0, 32, "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(new byte[16]);
            Cipher c = Cipher.getInstance(CIPHER);
            c.init(Cipher.ENCRYPT_MODE, key, ivSpec);
            return c.doFinal(signer);
        } catch (Exception ex) {
            return null;
        }
    }
}
