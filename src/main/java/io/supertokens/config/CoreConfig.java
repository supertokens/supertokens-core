/*
 *    Copyright (c) 2020, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.supertokens.Main;
import io.supertokens.cliOptions.CLIOptions;
import io.supertokens.exceptions.QuitProgramException;
import io.supertokens.pluginInterface.LOG_LEVEL;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CoreConfig {

    @JsonProperty
    private int core_config_version = -1;

    @JsonProperty
    private long access_token_validity = 3600; // in seconds

    @JsonProperty
    private boolean access_token_blacklisting = false;

    @JsonProperty
    private double refresh_token_validity = 60 * 2400; // in mins

    @JsonProperty
    private long password_reset_token_lifetime = 3600000; // in MS

    @JsonProperty
    private long email_verification_token_lifetime = 24 * 3600 * 1000; // in MS

    @JsonProperty
    private int passwordless_max_code_input_attempts = 5;

    @JsonProperty
    private long passwordless_code_lifetime = 900000; // in MS

    private final String logDefault = "asdkfahbdfk3kjHS";
    @JsonProperty
    private String info_log_path = logDefault;

    @JsonProperty
    private String error_log_path = logDefault;

    @JsonProperty
    private boolean access_token_signing_key_dynamic = true;

    @JsonProperty
    private double access_token_signing_key_update_interval = 168; // in hours

    @JsonProperty
    private int port = 3567;

    @JsonProperty
    private String host = "localhost";

    @JsonProperty
    private int max_server_pool_size = 10;

    @JsonProperty
    private String api_keys = null;

    @JsonProperty
    private boolean disable_telemetry = false;

    @JsonProperty
    private String password_hashing_alg = "BCRYPT";

    @JsonProperty
    private int argon2_iterations = 1;

    @JsonProperty
    private int argon2_memory_kb = 87795; // 85 mb

    @JsonProperty
    private int argon2_parallelism = 2;

    @JsonProperty
    private int argon2_hashing_pool_size = 1;

    @JsonProperty
    private int firebase_password_hashing_pool_size = 1;

    @JsonProperty
    private int bcrypt_log_rounds = 11;

    // TODO: add https in later version
//	# (OPTIONAL) boolean value (true or false). Set to true if you want to enable https requests to SuperTokens.
//	# If you are not running SuperTokens within a closed network along with your API process, for 
//	# example if you are using multiple cloud vendors, then it is recommended to set this to true.
//	# webserver_https_enabled:
    @JsonProperty
    private boolean webserver_https_enabled = false;

    @JsonProperty
    private String base_path = "";

    @JsonProperty
    private String log_level = "INFO";

    @JsonProperty
    private String firebase_password_hashing_signer_key = null;

    @JsonProperty
    private String ip_allow_regex = null;

    @JsonProperty
    private String ip_deny_regex = null;

    private Set<LOG_LEVEL> allowedLogLevels = null;

    public String getIpAllowRegex() {
        if (ip_allow_regex != null && ip_allow_regex.trim().equals("")) {
            return null;
        }
        return ip_allow_regex;
    }

    public String getIpDenyRegex() {
        if (ip_deny_regex != null && ip_deny_regex.trim().equals("")) {
            return null;
        }
        return ip_deny_regex;
    }

    public Set<LOG_LEVEL> getLogLevels(Main main) {
        if (allowedLogLevels != null) {
            return allowedLogLevels;
        }
        LOG_LEVEL logLevel = LOG_LEVEL.valueOf(this.log_level.toUpperCase());
        allowedLogLevels = new HashSet<>();
        if (logLevel == LOG_LEVEL.NONE) {
            return allowedLogLevels;
        }
        allowedLogLevels.add(LOG_LEVEL.ERROR);
        if (logLevel == LOG_LEVEL.ERROR) {
            return allowedLogLevels;
        }
        allowedLogLevels.add(LOG_LEVEL.WARN);
        if (logLevel == LOG_LEVEL.WARN) {
            return allowedLogLevels;
        }
        allowedLogLevels.add(LOG_LEVEL.INFO);
        if (logLevel == LOG_LEVEL.INFO) {
            return allowedLogLevels;
        }
        allowedLogLevels.add(LOG_LEVEL.DEBUG);
        return allowedLogLevels;
    }

    public String getBasePath() {
        String base_path = this.base_path; // Don't modify the original value from the config
        if (base_path == null || base_path.equals("/") || base_path.isEmpty()) {
            return "";
        }
        while (base_path.contains("//")) { // Catch corner case where there are multiple '/' together
            base_path = base_path.replace("//", "/");
        }
        if (!base_path.startsWith("/")) { // Add leading '/'
            base_path = "/" + base_path;
        }
        if (base_path.endsWith("/")) { // Remove trailing '/'
            base_path = base_path.substring(0, base_path.length() - 1);
        }
        return base_path;
    }

    public enum PASSWORD_HASHING_ALG {
        ARGON2, BCRYPT, FIREBASE_SCRYPT
    }

    public int getArgon2HashingPoolSize() {
        // the reason we do Math.max below is that if the password hashing algo is bcrypt,
        // then we don't check the argon2 hashing pool size config at all. In this case,
        // if the user gives a <= 0 number, it crashes the core (since it creates a blockedqueue in PaswordHashing
        // .java with length <= 0). So we do a Math.max
        return Math.max(1, argon2_hashing_pool_size);
    }

    public int getFirebaseSCryptPasswordHashingPoolSize() {
        return Math.max(1, firebase_password_hashing_pool_size);
    }

    public int getArgon2Iterations() {
        return argon2_iterations;
    }

    public int getBcryptLogRounds() {
        return bcrypt_log_rounds;
    }

    public int getArgon2MemoryKb() {
        return argon2_memory_kb;
    }

    public int getArgon2Parallelism() {
        return argon2_parallelism;
    }

    public String getFirebase_password_hashing_signer_key() {
        if (firebase_password_hashing_signer_key == null) {
            throw new IllegalStateException("'firebase_password_hashing_signer_key' cannot be null");
        }
        return firebase_password_hashing_signer_key;
    }

    public PASSWORD_HASHING_ALG getPasswordHashingAlg() {
        return PASSWORD_HASHING_ALG.valueOf(password_hashing_alg.toUpperCase());
    }

    @TestOnly
    public void setPasswordHashingAlg(PASSWORD_HASHING_ALG algo) {
        this.password_hashing_alg = algo.toString();
    }

    public int getConfigVersion() {
        return core_config_version;
    }

    public long getAccessTokenValidity() {
        return access_token_validity * 1000;
    }

    public boolean getAccessTokenBlacklisting() {
        return access_token_blacklisting;
    }

    public long getRefreshTokenValidity() {
        return (long) (refresh_token_validity * 60 * 1000);
    }

    public long getPasswordResetTokenLifetime() {
        return password_reset_token_lifetime;
    }

    public long getEmailVerificationTokenLifetime() {
        return email_verification_token_lifetime;
    }

    public int getPasswordlessMaxCodeInputAttempts() {
        return passwordless_max_code_input_attempts;
    }

    public long getPasswordlessCodeLifetime() {
        return passwordless_code_lifetime;
    }

    public boolean isTelemetryDisabled() {
        return disable_telemetry;
    }

    public String getInfoLogPath(Main main) {
        if (info_log_path == null || info_log_path.equalsIgnoreCase("null")) {
            return "null";
        }
        if (info_log_path.equals(logDefault)) {
            // this works for windows as well
            return CLIOptions.get(main).getInstallationPath() + "logs/info.log";
        }
        return info_log_path;
    }

    public String getErrorLogPath(Main main) {
        if (error_log_path == null || error_log_path.equalsIgnoreCase("null")) {
            return "null";
        }
        if (error_log_path.equals(logDefault)) {
            // this works for windows as well
            return CLIOptions.get(main).getInstallationPath() + "logs/error.log";
        }
        return error_log_path;
    }

    public boolean getAccessTokenSigningKeyDynamic() {
        return access_token_signing_key_dynamic;
    }

    public long getAccessTokenSigningKeyUpdateInterval() {
        return access_token_signing_key_dynamic ? (long) (access_token_signing_key_update_interval * 3600 * 1000)
                : (10L * 365 * 24 * 3600 * 1000);
    }

    public String[] getAPIKeys() {
        if (api_keys == null) {
            return null;
        }
        return api_keys.trim().replaceAll("\\s", "").split(",");
    }

    public int getPort(Main main) {
        Integer cliPort = CLIOptions.get(main).getPort();
        if (cliPort != null) {
            return cliPort;
        }
        return port;
    }

    public String getHost(Main main) {
        String cliHost = CLIOptions.get(main).getHost();
        if (cliHost != null) {
            return cliHost;
        }
        return host;
    }

    public int getMaxThreadPoolSize() {
        return max_server_pool_size;
    }

    public boolean getHttpsEnabled() {
        return webserver_https_enabled;
    }

    private String getConfigFileLocation(Main main) {
        return new File(CLIOptions.get(main).getConfigFilePath() == null
                ? CLIOptions.get(main).getInstallationPath() + "config.yaml"
                : CLIOptions.get(main).getConfigFilePath()).getAbsolutePath();
    }

    void validateAndInitialise(Main main) throws IOException {
        if (getConfigVersion() == -1) {
            throw new QuitProgramException(
                    "'core_config_version' is not set in the config.yaml file. Please redownload and install "
                            + "SuperTokens");
        }
        if (access_token_validity < 1 || access_token_validity > 86400000) {
            throw new QuitProgramException(
                    "'access_token_validity' must be between 1 and 86400000 seconds inclusive. The config file can be"
                            + " found here: " + getConfigFileLocation(main));
        }
        Boolean validityTesting = CoreConfigTestContent.getInstance(main)
                .getValue(CoreConfigTestContent.VALIDITY_TESTING);
        validityTesting = validityTesting == null ? false : validityTesting;
        if ((refresh_token_validity * 60) <= access_token_validity) {
            if (!Main.isTesting || validityTesting) {
                throw new QuitProgramException(
                        "'refresh_token_validity' must be strictly greater than 'access_token_validity'. The config "
                                + "file can be found here: " + getConfigFileLocation(main));
            }
        }

        if (!Main.isTesting || validityTesting) { // since in testing we make this really small
            if (access_token_signing_key_update_interval < 1) {
                throw new QuitProgramException(
                        "'access_token_signing_key_update_interval' must be greater than, equal to 1 hour. The "
                                + "config file can be found here: " + getConfigFileLocation(main));
            }
        }

        if (password_reset_token_lifetime <= 0) {
            throw new QuitProgramException("'password_reset_token_lifetime' must be >= 0");
        }

        if (email_verification_token_lifetime <= 0) {
            throw new QuitProgramException("'email_verification_token_lifetime' must be >= 0");
        }

        if (passwordless_code_lifetime <= 0) {
            throw new QuitProgramException("'passwordless_code_lifetime' must be > 0");
        }

        if (passwordless_max_code_input_attempts <= 0) {
            throw new QuitProgramException("'passwordless_max_code_input_attempts' must be > 0");
        }

        if (max_server_pool_size <= 0) {
            throw new QuitProgramException("'max_server_pool_size' must be >= 1. The config file can be found here: "
                    + getConfigFileLocation(main));
        }

        if (api_keys != null) {
            String[] keys = api_keys.split(",");
            for (int i = 0; i < keys.length; i++) {
                String currKey = keys[i].trim();
                if (currKey.length() < 20) {
                    throw new QuitProgramException(
                            "One of the API keys is too short. Please use at least 20 characters");
                }
                for (int y = 0; y < currKey.length(); y++) {
                    char currChar = currKey.charAt(y);
                    if (!(currChar == '=' || currChar == '-' || (currChar >= '0' && currChar <= '9')
                            || (currChar >= 'a' && currChar <= 'z') || (currChar >= 'A' && currChar <= 'Z'))) {
                        throw new QuitProgramException(
                                "Invalid characters in API key. Please only use '=', '-' and alpha-numeric (including"
                                        + " capitals)");
                    }
                }
            }
        }

        if (!password_hashing_alg.equalsIgnoreCase("ARGON2") && !password_hashing_alg.equalsIgnoreCase("BCRYPT")) {
            throw new QuitProgramException("'password_hashing_alg' must be one of 'ARGON2' or 'BCRYPT'");
        }

        if (password_hashing_alg.equalsIgnoreCase("ARGON2")) {
            if (argon2_iterations <= 0) {
                throw new QuitProgramException("'argon2_iterations' must be >= 1");
            }

            if (argon2_parallelism <= 0) {
                throw new QuitProgramException("'argon2_parallelism' must be >= 1");
            }

            if (argon2_memory_kb <= 0) {
                throw new QuitProgramException("'argon2_memory_kb' must be >= 1");
            }

            if (argon2_hashing_pool_size <= 0) {
                throw new QuitProgramException("'argon2_hashing_pool_size' must be >= 1");
            }

            if (argon2_hashing_pool_size > max_server_pool_size) {
                throw new QuitProgramException("'argon2_hashing_pool_size' must be <= 'max_server_pool_size'");
            }
        } else if (password_hashing_alg.equalsIgnoreCase("BCRYPT")) {
            if (bcrypt_log_rounds <= 0) {
                throw new QuitProgramException("'bcrypt_log_rounds' must be >= 1");
            }
        }

        if (base_path != null && !base_path.equals("") && !base_path.equals("/")) {
            if (base_path.contains(" ")) {
                throw new QuitProgramException("Invalid characters in base_path config");
            }
        }

        if (!log_level.equalsIgnoreCase("info") && !log_level.equalsIgnoreCase("none")
                && !log_level.equalsIgnoreCase("error") && !log_level.equalsIgnoreCase("warn")
                && !log_level.equalsIgnoreCase("debug")) {
            throw new QuitProgramException(
                    "'log_level' config must be one of \"NONE\",\"DEBUG\", \"INFO\", \"WARN\" or \"ERROR\".");
        }

        if (!getInfoLogPath(main).equals("null")) {
            File infoLog = new File(getInfoLogPath(main));
            if (!infoLog.exists()) {
                File parent = infoLog.getParentFile();
                if (parent != null) {
                    parent.mkdirs();
                }
                infoLog.createNewFile();
            }
        }

        if (!getErrorLogPath(main).equals("null")) {
            File errorLog = new File(getErrorLogPath(main));
            if (!errorLog.exists()) {
                File parent = errorLog.getParentFile();
                if (parent != null) {
                    parent.mkdirs();
                }
                errorLog.createNewFile();
            }
        }
    }

}