/*
 *    Copyright (c) 2025, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.test;

import io.supertokens.ProcessState;
import io.supertokens.config.Config;
import io.supertokens.config.CoreConfig;
import io.supertokens.config.annotations.EnvName;
import io.supertokens.config.annotations.IgnoreForAnnotationCheck;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.Assert.*;

public class EnvConfigTest {

    @Rule
    public TestRule watchman = Utils.getOnFailure();

    @Rule
    public TestRule retryFlaky = Utils.retryFlakyTest();

    @AfterClass
    public static void afterTesting() {
        Utils.afterTesting();
    }

    @Before
    public void beforeEach() {
        Utils.reset();
    }

    private static void setEnv(String key, String value) {
        try {
            Map<String, String> env = System.getenv();
            Class<?> cl = env.getClass();
            Field field = cl.getDeclaredField("m");
            field.setAccessible(true);
            Map<String, String> writableEnv = (Map<String, String>) field.get(env);
            writableEnv.put(key, value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to set environment variable", e);
        }
    }

    private static void removeEnv(String key) {
        try {
            Map<String, String> env = System.getenv();
            Class<?> cl = env.getClass();
            Field field = cl.getDeclaredField("m");
            field.setAccessible(true);
            Map<String, String> writableEnv = (Map<String, String>) field.get(env);
            writableEnv.remove(key);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to set environment variable", e);
        }
    }

    @Test
    public void testAllCoreConfigFieldsHaveEnvNameAssociatedWithIt() throws Exception {
        for (Field field : CoreConfig.class.getDeclaredFields()) {
            if (!field.isAnnotationPresent(IgnoreForAnnotationCheck.class)) {
                assertTrue(field.getName() + " does not have env defined!", field.isAnnotationPresent(EnvName.class));
            }
        }
    }

    @Test
    public void testEnvVarsAreLoadingOnBaseTenant() throws Exception {
        Object[][] testCases = new Object[][]{
                // ACCESS_TOKEN_VALIDITY: must be between 1 and 86400000 seconds inclusive
                new Object[]{"ACCESS_TOKEN_VALIDITY", "3600", (long) 3600},
                new Object[]{"ACCESS_TOKEN_VALIDITY", "7200", (long) 7200},
                new Object[]{"ACCESS_TOKEN_VALIDITY", "1", (long) 1}, // minimum valid value
                new Object[]{"ACCESS_TOKEN_VALIDITY", "86400000", (long) 86400000}, // maximum valid value
                
                // REFRESH_TOKEN_VALIDITY: in minutes, must be > access_token_validity when converted to seconds
                // Default access_token_validity is 3600 seconds, so refresh_token_validity must be > 60 minutes
                new Object[]{"REFRESH_TOKEN_VALIDITY", "120", (double) 120}, // 2 hours > 1 hour default
                new Object[]{"REFRESH_TOKEN_VALIDITY", "1440", (double) 1440}, // 24 hours
                
                // ACCESS_TOKEN_BLACKLISTING: boolean
                new Object[]{"ACCESS_TOKEN_BLACKLISTING", "true", true},
                new Object[]{"ACCESS_TOKEN_BLACKLISTING", "false", false},
                
                // PASSWORD_RESET_TOKEN_LIFETIME: must be > 0 (in milliseconds)
                new Object[]{"PASSWORD_RESET_TOKEN_LIFETIME", "3600000", (long) 3600000}, // 1 hour
                new Object[]{"PASSWORD_RESET_TOKEN_LIFETIME", "7200000", (long) 7200000}, // 2 hours
                new Object[]{"PASSWORD_RESET_TOKEN_LIFETIME", "1", (long) 1}, // minimum valid
                
                // EMAIL_VERIFICATION_TOKEN_LIFETIME: must be > 0 (in milliseconds)
                new Object[]{"EMAIL_VERIFICATION_TOKEN_LIFETIME", "86400000", (long) 86400000}, // 1 day
                new Object[]{"EMAIL_VERIFICATION_TOKEN_LIFETIME", "172800000", (long) 172800000}, // 2 days
                new Object[]{"EMAIL_VERIFICATION_TOKEN_LIFETIME", "1", (long) 1}, // minimum valid
                
                // PASSWORDLESS_MAX_CODE_INPUT_ATTEMPTS: must be > 0
                new Object[]{"PASSWORDLESS_MAX_CODE_INPUT_ATTEMPTS", "5", 5},
                new Object[]{"PASSWORDLESS_MAX_CODE_INPUT_ATTEMPTS", "10", 10},
                new Object[]{"PASSWORDLESS_MAX_CODE_INPUT_ATTEMPTS", "1", 1}, // minimum valid
                
                // PASSWORDLESS_CODE_LIFETIME: must be > 0 (in milliseconds)
                new Object[]{"PASSWORDLESS_CODE_LIFETIME", "900000", (long) 900000}, // 15 minutes
                new Object[]{"PASSWORDLESS_CODE_LIFETIME", "600000", (long) 600000}, // 10 minutes
                new Object[]{"PASSWORDLESS_CODE_LIFETIME", "1", (long) 1}, // minimum valid
                
                // TOTP_MAX_ATTEMPTS: must be > 0
                new Object[]{"TOTP_MAX_ATTEMPTS", "5", 5},
                new Object[]{"TOTP_MAX_ATTEMPTS", "3", 3},
                new Object[]{"TOTP_MAX_ATTEMPTS", "1", 1}, // minimum valid
                
                // TOTP_RATE_LIMIT_COOLDOWN_SEC: must be > 0 (in seconds)
                new Object[]{"TOTP_RATE_LIMIT_COOLDOWN_SEC", "900", 900}, // 15 minutes
                new Object[]{"TOTP_RATE_LIMIT_COOLDOWN_SEC", "300", 300}, // 5 minutes
                new Object[]{"TOTP_RATE_LIMIT_COOLDOWN_SEC", "1", 1}, // minimum valid
                
                // ACCESS_TOKEN_SIGNING_KEY_DYNAMIC: boolean
                new Object[]{"ACCESS_TOKEN_SIGNING_KEY_DYNAMIC", "true", true},
                new Object[]{"ACCESS_TOKEN_SIGNING_KEY_DYNAMIC", "false", false},
                
                // ACCESS_TOKEN_DYNAMIC_SIGNING_KEY_UPDATE_INTERVAL: must be >= 1 hour (in hours)
                new Object[]{"ACCESS_TOKEN_DYNAMIC_SIGNING_KEY_UPDATE_INTERVAL", "168", (double) 168}, // 1 week
                new Object[]{"ACCESS_TOKEN_DYNAMIC_SIGNING_KEY_UPDATE_INTERVAL", "24", (double) 24}, // 1 day
                new Object[]{"ACCESS_TOKEN_DYNAMIC_SIGNING_KEY_UPDATE_INTERVAL", "1", (double) 1}, // minimum valid
                
                // MAX_SERVER_POOL_SIZE: must be >= 1
                new Object[]{"MAX_SERVER_POOL_SIZE", "10", 10},
                new Object[]{"MAX_SERVER_POOL_SIZE", "5", 5},
                new Object[]{"MAX_SERVER_POOL_SIZE", "1", 1}, // minimum valid
                
                // DISABLE_TELEMETRY: boolean
                new Object[]{"DISABLE_TELEMETRY", "false", false},
                new Object[]{"DISABLE_TELEMETRY", "true", true},
                
                // PASSWORD_HASHING_ALG: must be "ARGON2" or "BCRYPT"
                new Object[]{"PASSWORD_HASHING_ALG", "BCRYPT", "BCRYPT"},
                new Object[]{"PASSWORD_HASHING_ALG", "ARGON2", "ARGON2"},
                
                // ARGON2_ITERATIONS: must be >= 1
                new Object[]{"ARGON2_ITERATIONS", "1", 1}, // minimum valid
                new Object[]{"ARGON2_ITERATIONS", "3", 3},
                
                // ARGON2_MEMORY_KB: must be >= 1
                new Object[]{"ARGON2_MEMORY_KB", "87795", 87795}, // default
                new Object[]{"ARGON2_MEMORY_KB", "1024", 1024},
                new Object[]{"ARGON2_MEMORY_KB", "1", 1}, // minimum valid
                
                // ARGON2_PARALLELISM: must be >= 1
                new Object[]{"ARGON2_PARALLELISM", "2", 2}, // default
                new Object[]{"ARGON2_PARALLELISM", "4", 4},
                new Object[]{"ARGON2_PARALLELISM", "1", 1}, // minimum valid
                
                // ARGON2_HASHING_POOL_SIZE: must be >= 1
                new Object[]{"ARGON2_HASHING_POOL_SIZE", "1", 1}, // minimum valid
                new Object[]{"ARGON2_HASHING_POOL_SIZE", "2", 2},
                
                // FIREBASE_PASSWORD_HASHING_POOL_SIZE: must be >= 1
                new Object[]{"FIREBASE_PASSWORD_HASHING_POOL_SIZE", "1", 1}, // minimum valid
                new Object[]{"FIREBASE_PASSWORD_HASHING_POOL_SIZE", "3", 3},
                
                // BCRYPT_LOG_ROUNDS: must be >= 1
                new Object[]{"BCRYPT_LOG_ROUNDS", "11", 11}, // default
                new Object[]{"BCRYPT_LOG_ROUNDS", "10", 10},
                new Object[]{"BCRYPT_LOG_ROUNDS", "1", 1}, // minimum valid
                
                // LOG_LEVEL: must be one of "DEBUG", "INFO", "WARN", "ERROR", "NONE"
                new Object[]{"LOG_LEVEL", "INFO", "INFO"},
                new Object[]{"LOG_LEVEL", "DEBUG", "DEBUG"},
                new Object[]{"LOG_LEVEL", "WARN", "WARN"},
                new Object[]{"LOG_LEVEL", "ERROR", "ERROR"},
                new Object[]{"LOG_LEVEL", "NONE", "NONE"},
                
                // BULK_MIGRATION_PARALLELISM: must be >= 1
                new Object[]{"BULK_MIGRATION_PARALLELISM", "1", 1}, // minimum valid
                new Object[]{"BULK_MIGRATION_PARALLELISM", "4", 4},
                
                // BULK_MIGRATION_BATCH_SIZE: must be >= 1
                new Object[]{"BULK_MIGRATION_BATCH_SIZE", "8000", 8000}, // default
                new Object[]{"BULK_MIGRATION_BATCH_SIZE", "1000", 1000},
                new Object[]{"BULK_MIGRATION_BATCH_SIZE", "1", 1}, // minimum valid
                
                // WEBAUTHN_RECOVER_ACCOUNT_TOKEN_LIFETIME: must be > 0 (in milliseconds)
                new Object[]{"WEBAUTHN_RECOVER_ACCOUNT_TOKEN_LIFETIME", "3600000", (long) 3600000}, // 1 hour
                new Object[]{"WEBAUTHN_RECOVER_ACCOUNT_TOKEN_LIFETIME", "7200000", (long) 7200000}, // 2 hours
                new Object[]{"WEBAUTHN_RECOVER_ACCOUNT_TOKEN_LIFETIME", "1", (long) 1}, // minimum valid
                
                // OTEL_COLLECTOR_CONNECTION_URI: string
                new Object[]{"OTEL_COLLECTOR_CONNECTION_URI", "http://localhost:4317", "http://localhost:4317"},
                new Object[]{"OTEL_COLLECTOR_CONNECTION_URI", "https://otel.example.com:4317", "https://otel.example.com:4317"},
                
                // BASE_PATH: string (can be empty, "/", or valid path)
                new Object[]{"BASE_PATH", "", ""},
                new Object[]{"BASE_PATH", "/", ""},
                new Object[]{"BASE_PATH", "/api", "/api"},
                new Object[]{"BASE_PATH", "/v1/auth", "/v1/auth"},
                
                // API_KEYS: string with specific format constraints (minimum 20 chars, specific character set)
                new Object[]{"API_KEYS", "abcdefghijklmnopqrstuvwxyz", "abcdefghijklmnopqrstuvwxyz"},
                new Object[]{"API_KEYS", "key1-with-dashes-and=equals123,another-key-12345678901", "another-key-12345678901,key1-with-dashes-and=equals123"}, // gets sorted
                
                // FIREBASE_PASSWORD_HASHING_SIGNER_KEY: string (any non-empty value)
                new Object[]{"FIREBASE_PASSWORD_HASHING_SIGNER_KEY", "test-signer-key-12345", "test-signer-key-12345"},
                
                // IP_ALLOW_REGEX: string (valid regex pattern)
                new Object[]{"IP_ALLOW_REGEX", "127\\.\\d+\\.\\d+\\.\\d+", "127\\.\\d+\\.\\d+\\.\\d+"},
                new Object[]{"IP_ALLOW_REGEX", ".*", ".*"},
                
                // IP_DENY_REGEX: string (valid regex pattern)
                new Object[]{"IP_DENY_REGEX", "192\\.168\\..*", "192\\.168\\..*"},
                
                // SUPERTOKENS_MAX_CDI_VERSION: string (valid semantic version)
                new Object[]{"SUPERTOKENS_MAX_CDI_VERSION", "5.0", "5.0"},
                new Object[]{"SUPERTOKENS_MAX_CDI_VERSION", "4.0", "4.0"}
        };

        for (Object[] testCase : testCases) {
            String[] args = {"../"};
            setEnv(testCase[0].toString(), testCase[1].toString());

            TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
            ProcessState.EventAndException startEvent = process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED);
            assertNotNull(startEvent);

            CoreConfig config = Config.getBaseConfig(process.getProcess());
            boolean fieldChecked = false;
            for (Field field : config.getClass().getDeclaredFields()) {
                if (!field.isAnnotationPresent(EnvName.class)) {
                    continue;
                }

                field.setAccessible(true);
                if (field.getAnnotationsByType(EnvName.class)[0].value().equals(testCase[0].toString())) {
                    assertEquals("Failed for env var: " + testCase[0] + " with value: " + testCase[1], 
                            testCase[2], field.get(config));
                    fieldChecked = true;
                }
            }
            assertTrue("No field found for env var: " + testCase[0], fieldChecked);

            process.kill();
            ProcessState.EventAndException stopEvent = process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED);
            assertNotNull(stopEvent);

            removeEnv(testCase[0].toString());
        }
    }
}
