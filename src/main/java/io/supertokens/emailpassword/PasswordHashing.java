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
import org.jetbrains.annotations.TestOnly;
import org.mindrot.jbcrypt.BCrypt;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class PasswordHashing extends ResourceDistributor.SingletonResource {

    private static final String RESOURCE_KEY = "io.supertokens.emailpassword.PasswordHashing";
    final static int ARGON2_SALT_LENGTH = 16;
    final static int ARGON2_HASH_LENGTH = 32;
    final BlockingQueue<Object> boundedQueue;
    final Main main;

    private PasswordHashing(Main main) {
        this.boundedQueue = new LinkedBlockingQueue<>(Config.getConfig(main).getArgon2HashingPoolSize());
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
            return BCrypt.hashpw(password, BCrypt.gensalt(Config.getConfig(main).getBcryptLogRounds()));
        }

        ProcessState.getInstance(main).addState(ProcessState.PROCESS_STATE.PASSWORD_HASH_ARGON, null);

        return withConcurrencyLimited(() -> argon2.hash(Config.getConfig(main).getArgon2Iterations(),
                Config.getConfig(main).getArgon2MemoryKb(), Config.getConfig(main).getArgon2Parallelism(),
                password.toCharArray()));
    }

    public interface Func<T> {
        T op();
    }

    private <T> T withConcurrencyLimited(Func<T> func) {
        Object waiter = new Object();
        try {
            while (!this.boundedQueue.contains(waiter)) {
                try {
                    // put will wait for there to be an empty slot in the queue and return
                    // only when there is a slot for waiter
                    this.boundedQueue.put(waiter);
                } catch (InterruptedException ignored) {
                }
            }

            return func.op();

        } finally {
            this.boundedQueue.remove(waiter);
        }
    }

    public boolean verifyPasswordWithHash(String password, String hash) {
        if (hash.startsWith("$argon2id")) { // argon2 hash looks like $argon2id$v=..$m=..,t=..,p=..$tgSmiYOCjQ0im5U6...
            ProcessState.getInstance(main).addState(ProcessState.PROCESS_STATE.PASSWORD_VERIFY_ARGON, null);

            return withConcurrencyLimited(() -> argon2.verify(hash, password.toCharArray()));
        }
        ProcessState.getInstance(main).addState(ProcessState.PROCESS_STATE.PASSWORD_VERIFY_BCRYPT, null);

        String bCryptPasswordHash = replaceUnsupportedIdentifierForPasswordHash(hash);
        return BCrypt.checkpw(password, bCryptPasswordHash);
    }

    private String replaceUnsupportedIdentifierForPasswordHash(String hash) {
        // Identifiers $2b, $2x and $2y are not recognized JBcrypt which only recognizes $2a, but, the actual hashed
        // password can be verified using JBcrypt, so we replace the identifier for verification
        if (hash.startsWith("$2b") || hash.startsWith("$2x") || hash.startsWith("$2y")) {
            // we replace the unsupported identifier with $2a
            return "$2a" + hash.substring(3);
        }
        return hash;
    }

    public boolean isInputAValidPasswordHash(String hash) {
        // argon2 hash looks like $argon2id$v=..$m=..,t=..,p=..$tgSmiYOCjQ0im5U6...
        // bcrypt hash starts with the algorithm identifier which can be $2a$, $2y$, $2b$ or $2x$,
        // the number of rounds, the salt and finally the hashed password.
        return (hash.startsWith("$argon2id") || hash.startsWith("$2a") || hash.startsWith("$2x")
                || hash.startsWith("$2y") || hash.startsWith("$2b"));
    }

    @TestOnly
    public int getBlockedQueueSize() {
        return this.boundedQueue.size();
    }
}
