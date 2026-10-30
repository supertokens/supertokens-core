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
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import org.jetbrains.annotations.TestOnly;
import org.mindrot.jbcrypt.BCrypt;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

public class PasswordHashing extends ResourceDistributor.SingletonResource {

    private static final String RESOURCE_KEY = "io.supertokens.emailpassword.PasswordHashing";
    final static int ARGON2_SALT_LENGTH = 16;
    final static int ARGON2_HASH_LENGTH = 32;
    final BlockingQueue<Object> argon2BoundedQueue;
    final BlockingQueue<Object> firebaseSCryptBoundedQueue;
    final Main main;

    private final Map<String, String> cachedPasswordHashForTesting = new HashMap<>();
    public static boolean bypassHashCachingInTesting = false;

    // One dummy hash per hashing algorithm and cost parameters, used to do the same password verification
    // work when a sign-in names an account that does not exist.
    private static final String DUMMY_PASSWORD = "supertokens-dummy-password";
    private final Map<String, String> dummyPasswordHashes = new ConcurrentHashMap<>();

    private PasswordHashing(Main main) {
        this.argon2BoundedQueue = new LinkedBlockingQueue<>(
                Config.getBaseConfig(main).getArgon2HashingPoolSize());
        this.firebaseSCryptBoundedQueue = new LinkedBlockingQueue<>(
                Config.getBaseConfig(main).getFirebaseSCryptPasswordHashingPoolSize());
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
        try {
            return (PasswordHashing) main.getResourceDistributor()
                    .getResource(new TenantIdentifier(null, null, null), RESOURCE_KEY);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void init(Main main) {
        main.getResourceDistributor()
                .setResource(new TenantIdentifier(null, null, null), RESOURCE_KEY, new PasswordHashing(main));
    }

    @TestOnly
    public String createHashWithSalt(String password) {
        try {
            return createHashWithSalt(ResourceDistributor.getAppForTesting().toAppIdentifier(), password);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    public String createHashWithSalt(AppIdentifier appIdentifier, String password)
            throws TenantOrAppNotFoundException {

        if (Main.isTesting && !bypassHashCachingInTesting && cachedPasswordHashForTesting.containsKey(password)) {
            return cachedPasswordHashForTesting.get(password);
        }

        String passwordHash = "";

        TenantIdentifier tenantIdentifier = appIdentifier.getAsPublicTenantIdentifier();
        if (Config.getConfig(tenantIdentifier, main).getPasswordHashingAlg() ==
                CoreConfig.PASSWORD_HASHING_ALG.BCRYPT) {
            ProcessState.getInstance(main).addState(ProcessState.PROCESS_STATE.PASSWORD_HASH_BCRYPT, null);
            passwordHash = BCrypt.hashpw(password,
                    BCrypt.gensalt(Config.getConfig(tenantIdentifier, main).getBcryptLogRounds()));
        } else if (Config.getConfig(tenantIdentifier, main).getPasswordHashingAlg() ==
                CoreConfig.PASSWORD_HASHING_ALG.ARGON2) {
            ProcessState.getInstance(main).addState(ProcessState.PROCESS_STATE.PASSWORD_HASH_ARGON, null);
            passwordHash = withConcurrencyLimited(
                    () -> argon2id.hash(Config.getConfig(tenantIdentifier, main).getArgon2Iterations(),
                            Config.getConfig(tenantIdentifier, main).getArgon2MemoryKb(),
                            Config.getConfig(tenantIdentifier, main).getArgon2Parallelism(),
                            password.toCharArray()), this.argon2BoundedQueue);
        }

        try {
            PasswordHashingUtils.assertSuperTokensSupportInputPasswordHashFormat(appIdentifier, main,
                    passwordHash, null);
        } catch (UnsupportedPasswordHashingFormatException e) {
            throw new IllegalStateException(e);
        }

        if (Main.isTesting) {
            cachedPasswordHashForTesting.put(password, passwordHash);
        }
        return passwordHash;
    }

    public interface Func<T> {
        T op() throws TenantOrAppNotFoundException;
    }

    private <T> T withConcurrencyLimited(Func<T> func, BlockingQueue<Object> blockingQueue)
            throws TenantOrAppNotFoundException {
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

    @TestOnly
    public boolean verifyPasswordWithHash(String password, String hash) {
        try {
            return verifyPasswordWithHash(new AppIdentifier(null, null), password, hash);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    public boolean verifyPasswordWithHash(AppIdentifier appIdentifier, String password, String hash)
            throws TenantOrAppNotFoundException {

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
                            Config.getConfig(appIdentifier.getAsPublicTenantIdentifier(), main)
                                    .getFirebase_password_hashing_signer_key()),
                    this.firebaseSCryptBoundedQueue);
        }

        return false;
    }

    /**
     * Verifies the given password against a dummy hash created with the app's configured hashing algorithm and
     * cost parameters, and discards the result. Sign-in paths call this when no account matches, so that the
     * response takes about as long as a wrong password for an existing account.
     */
    public void verifyPasswordWithDummyHash(AppIdentifier appIdentifier, String password)
            throws TenantOrAppNotFoundException {
        verifyPasswordWithHash(appIdentifier, password, getDummyPasswordHash(appIdentifier));
    }

    private String getDummyPasswordHash(AppIdentifier appIdentifier) throws TenantOrAppNotFoundException {
        CoreConfig config = Config.getConfig(appIdentifier.getAsPublicTenantIdentifier(), main);
        boolean argon2 = config.getPasswordHashingAlg() == CoreConfig.PASSWORD_HASHING_ALG.ARGON2;
        int argon2Iterations = config.getArgon2Iterations();
        int argon2MemoryKb = config.getArgon2MemoryKb();
        int argon2Parallelism = config.getArgon2Parallelism();
        int bcryptLogRounds = config.getBcryptLogRounds();
        String key = argon2
                ? "argon2:" + argon2Iterations + ":" + argon2MemoryKb + ":" + argon2Parallelism
                : "bcrypt:" + bcryptLogRounds;

        String hash = dummyPasswordHashes.get(key);
        if (hash != null) {
            return hash;
        }
        if (argon2) {
            hash = withConcurrencyLimited(
                    () -> argon2id.hash(argon2Iterations, argon2MemoryKb, argon2Parallelism,
                            DUMMY_PASSWORD.toCharArray()), this.argon2BoundedQueue);
        } else {
            hash = BCrypt.hashpw(DUMMY_PASSWORD, BCrypt.gensalt(bcryptLogRounds));
        }
        String existing = dummyPasswordHashes.putIfAbsent(key, hash);
        return existing != null ? existing : hash;
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
