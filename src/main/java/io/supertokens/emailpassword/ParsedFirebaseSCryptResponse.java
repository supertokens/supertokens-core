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

public class ParsedFirebaseSCryptResponse {
    public String passwordHash;
    public String salt;
    public String saltSeparator;
    public int rounds;
    public int memCost;

    public static final String FIREBASE_SCRYPT_PREFIX = "f_scrypt";
    private static final String FIREBASE_SCRYPT_SEPARATOR = "\\$";
    private static final String FIREBASE_SCRYPT_MEM_COST_SEPARATOR = "m=";
    private static final String FIREBASE_SCRYPT_ROUNDS_SEPARATOR = "r=";
    private static final String FIREBASE_SCRYPT_SALT_SEPARATOR = "s=";

    public ParsedFirebaseSCryptResponse(String passwordHash, String salt, String saltSeparator, int rounds,
                                        int memCost) {
        this.passwordHash = passwordHash;
        this.salt = salt;
        this.saltSeparator = saltSeparator;
        this.rounds = rounds;
        this.memCost = memCost;
    }

    public static ParsedFirebaseSCryptResponse fromHashString(String hash) {
        try {
            String[] separatedPasswordHash = hash.split(FIREBASE_SCRYPT_SEPARATOR);

            // check that stored password hash contains 7 fields and after splitting, the first field is empty and the
            // second field has the firebase scrypt prefix
            if (!(separatedPasswordHash.length == 7 && separatedPasswordHash[0].equals("")
                    && separatedPasswordHash[1].equals(FIREBASE_SCRYPT_PREFIX))) {
                return null;
            }

            String passwordHash = separatedPasswordHash[2];
            String salt = separatedPasswordHash[3];
            String saltSeparator = null;
            Integer memCost = null;
            Integer rounds = null;

            for (int i = 4; i < separatedPasswordHash.length; i++) {
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
                return null;
            }
            return new ParsedFirebaseSCryptResponse(passwordHash, salt, saltSeparator, rounds, memCost);
        } catch (Throwable e) {
            return null;
        }
    }
}
