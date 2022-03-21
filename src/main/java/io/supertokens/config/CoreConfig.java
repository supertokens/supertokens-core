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

import java.io.File;
import java.io.IOException;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CoreConfig {

    @JsonProperty
    private int core_config_version = -1;

    @JsonProperty
    private int access_token_validity = 3600; // in seconds

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
    private String password_hashing_alg = "ARGON2";

    @JsonProperty
    private int argon2_iterations = 3;

    @JsonProperty
    private int argon2_memory_bytes = 65536;

    @JsonProperty
    private int argon2_parallelism = 1;

    // TODO: add https in later version
//	# (OPTIONAL) boolean value (true or false). Set to true if you want to enable https requests to SuperTokens.
//	# If you are not running SuperTokens within a closed network along with your API process, for 
//	# example if you are using multiple cloud vendors, then it is recommended to set this to true.
//	# webserver_https_enabled:
    @JsonProperty
    private boolean webserver_https_enabled = false;

    @JsonProperty
    private String base_path = "";

    public String getBasePath() {
        if (base_path == null || base_path.equals("/")) {
            return "";
        }
        return base_path;
    }

    public enum PASSWORD_HASHING_ALG {
        ARGON2, BCRYPT
    }

    public int getArgon2Iterations() {
        return argon2_iterations;
    }

    public int getArgon2MemoryBytes() {
        return argon2_memory_bytes;
    }

    public int getArgon2Parallelism() {
        return argon2_parallelism;
    }

    public PASSWORD_HASHING_ALG getPasswordHashingAlg() {
        return PASSWORD_HASHING_ALG.valueOf(password_hashing_alg);
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

        if (!password_hashing_alg.equals("ARGON2") && !password_hashing_alg.equals("BCRYPT")) {
            throw new QuitProgramException("'password_hashing_alg' must be one of 'ARGON2' or 'BCRYPT'");
        }

        if (argon2_iterations <= 0) {
            throw new QuitProgramException("'argon2_iterations' must be >= 1");
        }

        if (argon2_parallelism <= 0) {
            throw new QuitProgramException("'argon2_parallelism' must be >= 1");
        }

        if (argon2_memory_bytes <= 0) {
            throw new QuitProgramException("'argon2_memory_bytes' must be >= 1");
        }

        if (base_path != null && !base_path.equals("") && !base_path.equals("/")) {
            if (base_path.contains(" ")) {
                throw new QuitProgramException("Invalid characters in base_path config");
            }
            if (!base_path.startsWith("/")) {
                throw new QuitProgramException("base_path must start with a '/'");
            }
            if (base_path.endsWith("/")) {
                throw new QuitProgramException("base_path cannot end with '/'");
            }
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