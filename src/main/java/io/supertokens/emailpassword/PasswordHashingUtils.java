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
import io.supertokens.Main;
import io.supertokens.config.Config;
import io.supertokens.config.CoreConfig;
import io.supertokens.emailpassword.exceptions.UnsupportedPasswordHashingFormatException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import org.apache.tomcat.util.codec.binary.Base64;

import javax.annotation.Nullable;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.util.Objects;

public class PasswordHashingUtils {

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

    public static void assertSuperTokensSupportInputPasswordHashFormat(AppIdentifier appIdentifier,
                                                                       Main main, String passwordHash,
                                                                       @Nullable
                                                                       CoreConfig.PASSWORD_HASHING_ALG hashingAlgorithm)
            throws UnsupportedPasswordHashingFormatException, TenantOrAppNotFoundException {
        if (hashingAlgorithm == null) {
            if (ParsedFirebaseSCryptResponse.fromHashString(passwordHash) != null) {
                // since input hash is in firebase scrypt format we check if firebase scrypt signer key is set
                Config.getConfig(appIdentifier.getAsPublicTenantIdentifier(), main)
                        .getFirebase_password_hashing_signer_key();
                return;
            }

            if (!(isInputHashInBcryptFormat(passwordHash) || isInputHashInArgon2Format(passwordHash))) {

                throw new UnsupportedPasswordHashingFormatException("Password hash is in invalid format");
            }
            return;
        }
        if (hashingAlgorithm.equals(CoreConfig.PASSWORD_HASHING_ALG.ARGON2)) {
            if (!isInputHashInArgon2Format(passwordHash)) {
                throw new UnsupportedPasswordHashingFormatException("Password hash is in invalid Argon2 format");
            }
            return;
        }
        if (hashingAlgorithm.equals(CoreConfig.PASSWORD_HASHING_ALG.BCRYPT)) {
            if (!isInputHashInBcryptFormat(passwordHash)) {
                throw new UnsupportedPasswordHashingFormatException("Password hash is in invalid BCrypt format");
            }
        }

        if (hashingAlgorithm.equals(CoreConfig.PASSWORD_HASHING_ALG.FIREBASE_SCRYPT)) {
            // since input hash is in firebase scrypt format we check if firebase scrypt signer key is set
            Config.getConfig(appIdentifier.getAsPublicTenantIdentifier(), main)
                    .getFirebase_password_hashing_signer_key();
            if (ParsedFirebaseSCryptResponse.fromHashString(passwordHash) == null) {
                throw new UnsupportedPasswordHashingFormatException(
                        "Password hash is in invalid Firebase SCrypt format");
            }
        }
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

    public static boolean verifyFirebaseSCryptPasswordHash(String plainTextPassword, String passwordHash,
                                                           String base64_signer_key) {

        // follows the logic mentioned here
        // https://github.com/SmartMoveSystems/firebase-scrypt-java/blob/master/src/main/java/com/smartmovesystems/hashcheck/FirebaseScrypt.java
        // this is the library recommended by firebase for the java implementation of firebase scrypt
        // https://firebaseopensource.com/projects/firebase/scrypt/
        ParsedFirebaseSCryptResponse response = ParsedFirebaseSCryptResponse.fromHashString(passwordHash);
        if (response == null) {
            return false;
        }

        int N = 1 << response.memCost;
        int p = 1;

        // concatenating decoded salt + separator
        byte[] byteArrTemp = response.salt.getBytes(StandardCharsets.US_ASCII);
        byte[] decodedSaltBytes = Base64.decodeBase64(byteArrTemp, 0, byteArrTemp.length);
        byteArrTemp = response.saltSeparator.getBytes(StandardCharsets.US_ASCII);
        byte[] decodedSaltSepBytes = Base64.decodeBase64(byteArrTemp, 0, byteArrTemp.length);

        byte[] saltConcat = new byte[decodedSaltBytes.length + decodedSaltSepBytes.length];
        System.arraycopy(decodedSaltBytes, 0, saltConcat, 0, decodedSaltBytes.length);
        System.arraycopy(decodedSaltSepBytes, 0, saltConcat, decodedSaltBytes.length, decodedSaltSepBytes.length);

        // hashing password
        byte[] hashedBytes;
        try {
            hashedBytes = SCrypt.scrypt(plainTextPassword.getBytes(StandardCharsets.US_ASCII), saltConcat, N,
                    response.rounds, p, 64);
        } catch (GeneralSecurityException e) {
            return false;
        }
        // encrypting with aes
        byteArrTemp = base64_signer_key.getBytes(StandardCharsets.US_ASCII);
        byte[] signerBytes = Base64.decodeBase64(byteArrTemp, 0, byteArrTemp.length);

        try {
            String CIPHER = "AES/CTR/NoPadding";
            Key key = new SecretKeySpec(hashedBytes, 0, 32, "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(new byte[16]);
            Cipher c = Cipher.getInstance(CIPHER);
            c.init(Cipher.ENCRYPT_MODE, key, ivSpec);
            byte[] encryptedPasswordHash = c.doFinal(signerBytes);
            return Objects.requireNonNull(Base64.encodeBase64String(encryptedPasswordHash))
                    .equals(response.passwordHash);
        } catch (Exception e) {
            return false;
        }
    }
}
