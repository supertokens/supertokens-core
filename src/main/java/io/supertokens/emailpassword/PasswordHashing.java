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
import io.supertokens.emailpassword.exceptions.UnsupportedPasswordHashingFormatException;
import org.jetbrains.annotations.TestOnly;
import org.mindrot.jbcrypt.BCrypt;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class PasswordHashing extends ResourceDistributor.SingletonResource {

    private static final String RESOURCE_KEY = "io.supertokens.emailpassword.PasswordHashing";
    final static int ARGON2_SALT_LENGTH = 16;
    final static int ARGON2_HASH_LENGTH = 32;
    final BlockingQueue<Object> argon2BoundedQueue;
    final BlockingQueue<Object> firebaseSCryptBoundedQueue;
    final Main main;

    private PasswordHashing(Main main) {
        this.argon2BoundedQueue = new LinkedBlockingQueue<>(Config.getConfig(main).getArgon2HashingPoolSize());
        this.firebaseSCryptBoundedQueue = new LinkedBlockingQueue<>(
                Config.getConfig(main).getFirebaseSCryptPasswordHashingPoolSize());
        this.main = main;
    }

    // argon2 instances are thread safe: https://github.com/phxql/argon2-jvm/issues/35
    private static Argon2 argon2id = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id, ARGON2_SALT_LENGTH,
            ARGON2_HASH_LENGTH);
    private static Argon2 argon2i = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2i, ARGON2_SALT_LENGTH,
            ARGON2_HASH_LENGTH);
    private static Argon2 argon2d = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2d, ARGON2_SALT_LENGTH,
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

        String passwordHash = "";

        if (Config.getConfig(main).getPasswordHashingAlg() == CoreConfig.PASSWORD_HASHING_ALG.BCRYPT) {
            ProcessState.getInstance(main).addState(ProcessState.PROCESS_STATE.PASSWORD_HASH_BCRYPT, null);
            passwordHash = BCrypt.hashpw(password, BCrypt.gensalt(Config.getConfig(main).getBcryptLogRounds()));
        } else if (Config.getConfig(main).getPasswordHashingAlg() == CoreConfig.PASSWORD_HASHING_ALG.ARGON2) {
            ProcessState.getInstance(main).addState(ProcessState.PROCESS_STATE.PASSWORD_HASH_ARGON, null);
            passwordHash = withConcurrencyLimited(() -> argon2id.hash(Config.getConfig(main).getArgon2Iterations(),
                    Config.getConfig(main).getArgon2MemoryKb(), Config.getConfig(main).getArgon2Parallelism(),
                    password.toCharArray()), this.argon2BoundedQueue);
        }

        try {
            PasswordHashingUtils.assertSuperTokensSupportInputPasswordHashFormat(main, passwordHash, null);
        } catch (UnsupportedPasswordHashingFormatException e) {
            throw new IllegalStateException(e);
        }
        return passwordHash;
    }

    public interface Func<T> {
        T op();
    }

    private <T> T withConcurrencyLimited(Func<T> func, BlockingQueue<Object> blockingQueue) {
        Object waiter = new Object();
        try {
            while (!blockingQueue.contains(waiter)) {
                try {
                    // put will wait for there to be an empty slot in the queue and return
                    // only when there is a slot for waiter
                    blockingQueue.put(waiter);
                } catch (InterruptedException ignored) {
                }
            }

            return func.op();
        } finally {
            blockingQueue.remove(waiter);
        }
    }

    public boolean verifyPasswordWithHash(String password, String hash) {

        if (PasswordHashingUtils.isInputHashInArgon2Format(hash)) {
            ProcessState.getInstance(main).addState(ProcessState.PROCESS_STATE.PASSWORD_VERIFY_ARGON, null);
            if (hash.startsWith("$argon2id")) {
                return withConcurrencyLimited(() -> argon2id.verify(hash, password.toCharArray()),
                        this.argon2BoundedQueue);
            }

            if (hash.startsWith("$argon2i")) {
                return withConcurrencyLimited(() -> argon2i.verify(hash, password.toCharArray()),
                        this.argon2BoundedQueue);
            }

            if (hash.startsWith("$argon2d")) {
                return withConcurrencyLimited(() -> argon2d.verify(hash, password.toCharArray()),
                        this.argon2BoundedQueue);
            }
        } else if (PasswordHashingUtils.isInputHashInBcryptFormat(hash)) {
            ProcessState.getInstance(main).addState(ProcessState.PROCESS_STATE.PASSWORD_VERIFY_BCRYPT, null);
            String bCryptPasswordHash = PasswordHashingUtils
                    .replaceUnsupportedIdentifierForBcryptPasswordHashVerification(hash);
            return BCrypt.checkpw(password, bCryptPasswordHash);
        } else if (ParsedFirebaseSCryptResponse.fromHashString(hash) != null) {
            ProcessState.getInstance(main).addState(ProcessState.PROCESS_STATE.PASSWORD_VERIFY_FIREBASE_SCRYPT, null);
            return withConcurrencyLimited(
                    () -> PasswordHashingUtils.verifyFirebaseSCryptPasswordHash(password, hash,
                            Config.getConfig(main).getFirebase_password_hashing_signer_key()),
                    this.firebaseSCryptBoundedQueue);
        }

        return false;
    }

    @TestOnly
    public int getArgon2BlockedQueueSize() {
        return this.argon2BoundedQueue.size();
    }

    @TestOnly
    public int getFirebaseSCryptBlockedQueueSize() {
        return this.firebaseSCryptBoundedQueue.size();
    }
}
