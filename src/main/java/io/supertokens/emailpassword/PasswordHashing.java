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

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import io.supertokens.Main;
import io.supertokens.config.Config;
import io.supertokens.config.CoreConfig;
import org.mindrot.jbcrypt.BCrypt;

public class PasswordHashing {

    final static int ARGON2_SALT_LENGTH = 32;
    final static int ARGON2_HASH_LENGTH = 64;

    public static String createHashWithSalt(Main main, String password) {
        if (Config.getConfig(main).getPasswordHashingAlg() == CoreConfig.PASSWORD_HASHING_ALG.BCRYPT) {
            return BCrypt.hashpw(password, BCrypt.gensalt(11));
        }

        Argon2 argon2 = getArgon2Instance();
        return argon2.hash(Config.getConfig(main).getArgon2Iterations(), Config.getConfig(main).getArgon2MemoryBytes(),
                Config.getConfig(main).getArgon2Parallelism(), password.toCharArray());
    }

    public static boolean verifyPasswordWithHash(String password, String hash) {
        if (hash.startsWith("$argon2id")) { // argon2 hash looks like $argon2id$v=..$m=..,t=..,p=..$tgSmiYOCjQ0im5U6...
            Argon2 argon2 = getArgon2Instance();
            return argon2.verify(hash, password.toCharArray());
        }
        return BCrypt.checkpw(password, hash);
    }

    private static Argon2 getArgon2Instance() {
        return Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id, ARGON2_SALT_LENGTH, ARGON2_HASH_LENGTH);
    }
}
