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
import io.supertokens.ProcessState;
import io.supertokens.ResourceDistributor;
import io.supertokens.config.Config;
import io.supertokens.config.CoreConfig;
import org.mindrot.jbcrypt.BCrypt;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class PasswordHashing extends ResourceDistributor.SingletonResource {

    private static final String RESOURCE_KEY = "io.supertokens.emailpassword.PasswordHashing";
    final static int ARGON2_SALT_LENGTH = 16;
    final static int ARGON2_HASH_LENGTH = 32;
    final ExecutorService executorService;
    final Main main;

    private PasswordHashing(Main main) {
        int poolSize = 10; // TODO: get pool size from main
        executorService = Executors.newFixedThreadPool(poolSize);
        this.main = main;
    }

    // argon2 instances are thread safe: https://github.com/phxql/argon2-jvm/issues/35
    private static Argon2 argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id, ARGON2_SALT_LENGTH,
            ARGON2_HASH_LENGTH);

    public static PasswordHashing getInstance(Main main) {
        return (PasswordHashing) main.getResourceDistributor().getResource(RESOURCE_KEY);
    }

    public static void init(Main main) {
        if (getInstance(main) != null) {
            return;
        }
        main.getResourceDistributor().setResource(RESOURCE_KEY, new PasswordHashing(main));
    }

    public String createHashWithSalt(String password) {
        if (Config.getConfig(main).getPasswordHashingAlg() == CoreConfig.PASSWORD_HASHING_ALG.BCRYPT) {
            ProcessState.getInstance(main).addState(ProcessState.PROCESS_STATE.PASSWORD_HASH_BCRYPT, null);
            return BCrypt.hashpw(password, BCrypt.gensalt(11));
        }

        ProcessState.getInstance(main).addState(ProcessState.PROCESS_STATE.PASSWORD_HASH_ARGON, null);
        return argon2.hash(Config.getConfig(main).getArgon2Iterations(), Config.getConfig(main).getArgon2MemoryBytes(),
                Config.getConfig(main).getArgon2Parallelism(), password.toCharArray());
    }

    public boolean verifyPasswordWithHash(String password, String hash) {
        if (hash.startsWith("$argon2id")) { // argon2 hash looks like $argon2id$v=..$m=..,t=..,p=..$tgSmiYOCjQ0im5U6...
            ProcessState.getInstance(main).addState(ProcessState.PROCESS_STATE.PASSWORD_VERIFY_ARGON, null);
            return argon2.verify(hash, password.toCharArray());
        }
        ProcessState.getInstance(main).addState(ProcessState.PROCESS_STATE.PASSWORD_VERIFY_BCRYPT, null);
        return BCrypt.checkpw(password, hash);
    }

    public void close() {
        this.executorService.shutdown();
        // TODO: do we need to do awaitTermination at all?
        try {
            this.executorService.awaitTermination(1, TimeUnit.MINUTES);
        } catch (InterruptedException ignored) {
        }
    }
}
