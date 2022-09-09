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

import io.supertokens.emailpassword.exceptions.UnsupportedPasswordHashingFormatException;

import javax.annotation.Nullable;

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

    public static void assertSuperTokensSupportInputPasswordHashFormat(String passwordHash,
            @Nullable PasswordHashingAlgorithm hashingAlgorithm) throws UnsupportedPasswordHashingFormatException {
        if (hashingAlgorithm == null) {
            if (!(isInputHashInBcryptFormat(passwordHash)
                    || isInputHashInArgon2Format(passwordHash) /* || isInputHashInScryptFormat(passwordHash) */)) {
                throw new UnsupportedPasswordHashingFormatException("Password hash is in invalid format");
            }
        }
        if (hashingAlgorithm.equals(PasswordHashingAlgorithm.ARGON2)) {
            if (!isInputHashInArgon2Format(passwordHash)) {
                throw new UnsupportedPasswordHashingFormatException("Password hash is in invalid Argon2 format");
            }
        }
        if (hashingAlgorithm.equals(PasswordHashingAlgorithm.BCRYPT)) {
            if (!isInputHashInBcryptFormat(passwordHash)) {
                throw new UnsupportedPasswordHashingFormatException("Password hash is in invalid BCrypt format");
            }
        }

//        if (hashingAlgorithm.equals(PasswordHashingAlgorithm.SCRYPT)) {
//            if (!isInputHashInScryptFormat(passwordHash)) {
//                throw new UnsupportedPasswordHashingFormatException("Password hash is in invalid SCrypt format");
//            }
//        }
    }

    public static String updatePasswordHashWithPrefixIfRequired(String passwordHash,
            PasswordHashingAlgorithm hashingAlgorithm) {
        if (hashingAlgorithm == null) {
            return passwordHash;
        }

//        if (hashingAlgorithm == PasswordHashingAlgorithm.SCRYPT){
//            if (doesPasswordHashHaveScrpytPrefix(passwordHash)){
//                return passwordHash;
//            }
//            return addScryptPrefixToPasswordHash(passwordHash);
//        }

        return passwordHash;
    }

//    public static boolean isInputHashInScryptFormat(String hash) {
//        if(doesPasswordHashHaveScrpytPrefix(hash)){
//            return true;
//        }
//        if (isInputHashInBcryptFormat(hash) || isInputHashInArgon2Format(hash)){
//            return false;
//        }
//        return true;
//    }

    public static boolean isInputHashInBcryptFormat(String hash) {
        // bcrypt hash starts with the algorithm identifier which can be $2a$, $2y$, $2b$ or $2x$,
        // the number of rounds, the salt and finally the hashed password value.
        return (hash.startsWith("$2a") || hash.startsWith("$2x") || hash.startsWith("$2y") || hash.startsWith("$2b"));
    }

    public static boolean isInputHashInArgon2Format(String hash) {
        // argon2 hash looks like $argon2id or $argon2d or $argon2i $v=..$m=..,t=..,p=..$tgSmiYOCjQ0im5U6...
        return (hash.startsWith("$argon2id") || hash.startsWith("$argon2i") || hash.startsWith("$argon2d"));
    }

//    private static  boolean doesPasswordHashHaveScrpytPrefix(String passwordHash){
//        // TODO:
//        return false;
//    }

//    private static String addScryptPrefixToPasswordHash(String passwordHash){
//        // TODO:
//        return passwordHash;
//    }
}
