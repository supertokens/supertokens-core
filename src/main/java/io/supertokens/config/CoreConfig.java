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

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.cliOptions.CLIOptions;
import io.supertokens.config.annotations.ConfigYamlOnly;
import io.supertokens.config.annotations.IgnoreForAnnotationCheck;
import io.supertokens.config.annotations.NotConflictingInApp;
import io.supertokens.pluginInterface.LOG_LEVEL;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.utils.SemVer;
import io.supertokens.webserver.WebserverAPI;
import org.apache.catalina.filters.RemoteAddrFilter;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;
import java.util.regex.PatternSyntaxException;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CoreConfig {

    @IgnoreForAnnotationCheck
    public static final String[] PROTECTED_CONFIGS = new String[]{
            "ip_allow_regex",
            "ip_deny_regex",
    };

    @IgnoreForAnnotationCheck
    @JsonProperty
    private int core_config_version = -1;

    @NotConflictingInApp
    @JsonProperty
    private long access_token_validity = 3600; // in seconds

    @NotConflictingInApp
    @JsonProperty
    private boolean access_token_blacklisting = false;

    @NotConflictingInApp
    @JsonProperty
    private double refresh_token_validity = 60 * 2400; // in mins

    @IgnoreForAnnotationCheck
    @JsonProperty
    private long password_reset_token_lifetime = 3600000; // in MS

    @IgnoreForAnnotationCheck
    @JsonProperty
    private long email_verification_token_lifetime = 24 * 3600 * 1000; // in MS

    @IgnoreForAnnotationCheck
    @JsonProperty
    private int passwordless_max_code_input_attempts = 5;

    @IgnoreForAnnotationCheck
    @JsonProperty
    private long passwordless_code_lifetime = 900000; // in MS

    @IgnoreForAnnotationCheck
    @JsonProperty
    private int totp_max_attempts = 5;

    @IgnoreForAnnotationCheck
    @JsonProperty
    private int totp_rate_limit_cooldown_sec = 900; // in seconds (Default 15 mins)

    @IgnoreForAnnotationCheck
    private final String logDefault = "asdkfahbdfk3kjHS";

    @ConfigYamlOnly
    @JsonProperty
    private String info_log_path = logDefault;

    @ConfigYamlOnly
    @JsonProperty
    private String error_log_path = logDefault;

    @NotConflictingInApp
    @JsonProperty
    private boolean access_token_signing_key_dynamic = true;

    @NotConflictingInApp
    @JsonProperty("access_token_dynamic_signing_key_update_interval")
    @JsonAlias({"access_token_dynamic_signing_key_update_interval", "access_token_signing_key_update_interval"})
    private double access_token_dynamic_signing_key_update_interval = 168; // in hours

    @ConfigYamlOnly
    @JsonProperty
    private int port = 3567;

    @ConfigYamlOnly
    @JsonProperty
    private String host = "localhost";

    @ConfigYamlOnly
    @JsonProperty
    private int max_server_pool_size = 10;

    @NotConflictingInApp
    @JsonProperty
    private String api_keys = null;

    @NotConflictingInApp
    @JsonProperty
    private boolean disable_telemetry = false;

    @NotConflictingInApp
    @JsonProperty
    private String password_hashing_alg = "BCRYPT";

    @ConfigYamlOnly
    @JsonProperty
    private int argon2_iterations = 1;

    @ConfigYamlOnly
    @JsonProperty
    private int argon2_memory_kb = 87795; // 85 mb

    @ConfigYamlOnly
    @JsonProperty
    private int argon2_parallelism = 2;

    @ConfigYamlOnly
    @JsonProperty
    private int argon2_hashing_pool_size = 1;

    @ConfigYamlOnly
    @JsonProperty
    private int firebase_password_hashing_pool_size = 1;

    @ConfigYamlOnly
    @JsonProperty
    private int bcrypt_log_rounds = 11;

    // TODO: add https in later version
    // # (OPTIONAL) boolean value (true or false). Set to true if you want to enable
    // https requests to SuperTokens.
    // # If you are not running SuperTokens within a closed network along with your
    // API process, for
    // # example if you are using multiple cloud vendors, then it is recommended to
    // set this to true.
    // # webserver_https_enabled:
    @ConfigYamlOnly
    @JsonProperty
    private boolean webserver_https_enabled = false;

    @ConfigYamlOnly
    @JsonProperty
    private String base_path = "";

    @ConfigYamlOnly
    @JsonProperty
    private String log_level = "INFO";

    @NotConflictingInApp
    @JsonProperty
    private String firebase_password_hashing_signer_key = null;

    @IgnoreForAnnotationCheck
    @JsonProperty
    private String ip_allow_regex = null;

    @IgnoreForAnnotationCheck
    @JsonProperty
    private String ip_deny_regex = null;

    @ConfigYamlOnly
    @JsonProperty
    private String supertokens_saas_secret = null;

    @NotConflictingInApp
    @JsonProperty
    private String supertokens_default_cdi_version = null;

    @IgnoreForAnnotationCheck
    private Set<LOG_LEVEL> allowedLogLevels = null;

    @IgnoreForAnnotationCheck
    private boolean isNormalizedAndValid = false;

    public static Set<String> getValidFields() {
        CoreConfig coreConfig = new CoreConfig();
        JsonObject coreConfigObj = new GsonBuilder().serializeNulls().create().toJsonTree(coreConfig).getAsJsonObject();

        Set<String> validFields = new HashSet<>();
        for (Map.Entry<String, JsonElement> entry : coreConfigObj.entrySet()) {
            validFields.add(entry.getKey());
        }

        // Adding the aliases
        validFields.add("access_token_signing_key_update_interval");
        return validFields;
    }

    public String getIpAllowRegex() {
        return ip_allow_regex;
    }

    public String getIpDenyRegex() {
        return ip_deny_regex;
    }

    public Set<LOG_LEVEL> getLogLevels(Main main) {
        if (allowedLogLevels != null) {
            return allowedLogLevels;
        }
        LOG_LEVEL logLevel = LOG_LEVEL.valueOf(this.log_level);
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
        return base_path;
    }

    public enum PASSWORD_HASHING_ALG {
        ARGON2, BCRYPT, FIREBASE_SCRYPT
    }

    public int getArgon2HashingPoolSize() {
        return argon2_hashing_pool_size;
    }

    public int getFirebaseSCryptPasswordHashingPoolSize() {
        return firebase_password_hashing_pool_size;
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
        return access_token_validity;
    }

    public boolean getAccessTokenBlacklisting() {
        return access_token_blacklisting;
    }

    public long getRefreshTokenValidity() {
        return (long) (refresh_token_validity);
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

    public int getTotpMaxAttempts() {
        return totp_max_attempts;
    }

    /**
     * TOTP rate limit cooldown time (in seconds)
     */
    public int getTotpRateLimitCooldownTimeSec() {
        return totp_rate_limit_cooldown_sec;
    }

    public boolean isTelemetryDisabled() {
        return disable_telemetry;
    }

    public String getInfoLogPath(Main main) {
        return info_log_path;
    }

    public String getErrorLogPath(Main main) {
        return error_log_path;
    }

    public boolean getAccessTokenSigningKeyDynamic() {
        return access_token_signing_key_dynamic;
    }

    public long getAccessTokenDynamicSigningKeyUpdateInterval() {
        return (long) (access_token_dynamic_signing_key_update_interval);
    }

    public String[] getAPIKeys() {
        if (api_keys == null) {
            return null;
        }
        return api_keys.trim().replaceAll("\\s", "").split(",");
    }

    public String getSuperTokensSaaSSecret() {
        return supertokens_saas_secret;
    }

    public int getPort(Main main) {
        return port;
    }

    public String getHost(Main main) {
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

    void normalizeAndValidate(Main main) throws InvalidConfigException {
        if (isNormalizedAndValid) {
            return;
        }

        // Validate
        if (core_config_version == -1) {
            throw new InvalidConfigException(
                    "'core_config_version' is not set in the config.yaml file. Please redownload and install "
                            + "SuperTokens");
        }
        if (access_token_validity < 1 || access_token_validity > 86400000) {
            throw new InvalidConfigException(
                    "'access_token_validity' must be between 1 and 86400000 seconds inclusive. The config file can be"
                            + " found here: " + getConfigFileLocation(main));
        }
        Boolean validityTesting = CoreConfigTestContent.getInstance(main)
                .getValue(CoreConfigTestContent.VALIDITY_TESTING);
        validityTesting = validityTesting == null ? false : validityTesting;

        if ((refresh_token_validity * 60) <= access_token_validity) {
            if (!Main.isTesting || validityTesting) {
                throw new InvalidConfigException(
                        "'refresh_token_validity' must be strictly greater than 'access_token_validity'. The config "
                                + "file can be found here: " + getConfigFileLocation(main));
            }
        }

        if (!Main.isTesting || validityTesting) { // since in testing we make this really small
            if (access_token_dynamic_signing_key_update_interval < 1) {
                throw new InvalidConfigException(
                        "'access_token_dynamic_signing_key_update_interval' must be greater than, equal to 1 hour. The "
                                + "config file can be found here: " + getConfigFileLocation(main));
            }
        }

        if (password_reset_token_lifetime <= 0) {
            throw new InvalidConfigException("'password_reset_token_lifetime' must be >= 0");
        }

        if (email_verification_token_lifetime <= 0) {
            throw new InvalidConfigException("'email_verification_token_lifetime' must be >= 0");
        }

        if (passwordless_code_lifetime <= 0) {
            throw new InvalidConfigException("'passwordless_code_lifetime' must be > 0");
        }

        if (passwordless_max_code_input_attempts <= 0) {
            throw new InvalidConfigException("'passwordless_max_code_input_attempts' must be > 0");
        }

        if (totp_max_attempts <= 0) {
            throw new InvalidConfigException("'totp_max_attempts' must be > 0");
        }

        if (totp_rate_limit_cooldown_sec <= 0) {
            throw new InvalidConfigException("'totp_rate_limit_cooldown_sec' must be > 0");
        }

        if (max_server_pool_size <= 0) {
            throw new InvalidConfigException(
                    "'max_server_pool_size' must be >= 1. The config file can be found here: "
                            + getConfigFileLocation(main));
        }

        if (api_keys != null) {
            String[] keys = api_keys.split(",");
            for (int i = 0; i < keys.length; i++) {
                String currKey = keys[i].trim();
                if (currKey.length() < 20) {
                    throw new InvalidConfigException(
                            "One of the API keys is too short. Please use at least 20 characters");
                }
                for (int y = 0; y < currKey.length(); y++) {
                    char currChar = currKey.charAt(y);
                    if (!(currChar == '=' || currChar == '-' || (currChar >= '0' && currChar <= '9')
                            || (currChar >= 'a' && currChar <= 'z') || (currChar >= 'A' && currChar <= 'Z'))) {
                        throw new InvalidConfigException(
                                "Invalid characters in API key. Please only use '=', '-' and alpha-numeric (including"
                                        + " capitals)");
                    }
                }
            }
        }

        if (supertokens_saas_secret != null) {
            if (api_keys == null) {
                throw new InvalidConfigException(
                        "supertokens_saas_secret can only be used when api_key is also defined");
            }
            if (supertokens_saas_secret.length() < 40) {
                throw new InvalidConfigException(
                        "supertokens_saas_secret is too short. Please use at least 40 characters");
            }
            for (int y = 0; y < supertokens_saas_secret.length(); y++) {
                char currChar = supertokens_saas_secret.charAt(y);
                if (!(currChar == '=' || currChar == '-' || (currChar >= '0' && currChar <= '9')
                        || (currChar >= 'a' && currChar <= 'z') || (currChar >= 'A' && currChar <= 'Z'))) {
                    throw new InvalidConfigException(
                            "Invalid characters in supertokens_saas_secret key. Please only use '=', '-' and " +
                                    "alpha-numeric (including"
                                    + " capitals)");
                }
            }
        }

        if (!password_hashing_alg.equalsIgnoreCase("ARGON2") && !password_hashing_alg.equalsIgnoreCase("BCRYPT")) {
            throw new InvalidConfigException("'password_hashing_alg' must be one of 'ARGON2' or 'BCRYPT'");
        }

        if (password_hashing_alg.equalsIgnoreCase("ARGON2")) {
            if (argon2_iterations <= 0) {
                throw new InvalidConfigException("'argon2_iterations' must be >= 1");
            }

            if (argon2_parallelism <= 0) {
                throw new InvalidConfigException("'argon2_parallelism' must be >= 1");
            }

            if (argon2_memory_kb <= 0) {
                throw new InvalidConfigException("'argon2_memory_kb' must be >= 1");
            }

            if (argon2_hashing_pool_size <= 0) {
                throw new InvalidConfigException("'argon2_hashing_pool_size' must be >= 1");
            }

            if (argon2_hashing_pool_size > max_server_pool_size) {
                throw new InvalidConfigException(
                        "'argon2_hashing_pool_size' must be <= 'max_server_pool_size'");
            }
        } else if (password_hashing_alg.equalsIgnoreCase("BCRYPT")) {
            if (bcrypt_log_rounds <= 0) {
                throw new InvalidConfigException("'bcrypt_log_rounds' must be >= 1");
            }
        }

        if (base_path != null && !base_path.equals("") && !base_path.equals("/")) {
            if (base_path.contains(" ")) {
                throw new InvalidConfigException("Invalid characters in base_path config");
            }
        }

        if (!log_level.equalsIgnoreCase("info") && !log_level.equalsIgnoreCase("none")
                && !log_level.equalsIgnoreCase("error") && !log_level.equalsIgnoreCase("warn")
                && !log_level.equalsIgnoreCase("debug")) {
            throw new InvalidConfigException(
                    "'log_level' config must be one of \"NONE\",\"DEBUG\", \"INFO\", \"WARN\" or \"ERROR\".");
        }

        {
            // IP Filter validation
            RemoteAddrFilter filter = new RemoteAddrFilter();
            if (ip_allow_regex != null) {
                try {
                    filter.setAllow(ip_allow_regex);
                } catch (PatternSyntaxException e) {
                    throw new InvalidConfigException("Provided regular expression is invalid for ip_allow_regex config");
                }
            }
            if (ip_deny_regex != null) {
                try {
                    filter.setDeny(ip_deny_regex);
                } catch (PatternSyntaxException e) {
                    throw new InvalidConfigException("Provided regular expression is invalid for ip_deny_regex config");
                }
            }
        }

        if (supertokens_default_cdi_version != null) {
            try {
                SemVer version = new SemVer(supertokens_default_cdi_version);

                if (!WebserverAPI.supportedVersions.contains(version)) {
                    throw new InvalidConfigException("supertokens_default_cdi_version is not a supported version");
                }
            } catch (IllegalArgumentException e) {
                throw new InvalidConfigException("supertokens_default_cdi_version is not a valid semantic version");
            }
        }

        // Normalize
        if (ip_allow_regex != null) {
            ip_allow_regex = ip_allow_regex.trim();
            if (ip_allow_regex.equals("")) {
                ip_allow_regex = null;
            }
        }
        if (ip_deny_regex != null) {
            ip_deny_regex = ip_deny_regex.trim();
            if (ip_deny_regex.equals("")) {
                ip_deny_regex = null;
            }
        }

        if (log_level != null) {
            log_level = log_level.trim().toUpperCase();
        }

        { // info_log_path
            if (info_log_path == null || info_log_path.equalsIgnoreCase("null")) {
                info_log_path = "null";
            } else {
                if (info_log_path.equals(logDefault)) {
                    // this works for windows as well
                    info_log_path = CLIOptions.get(main).getInstallationPath() + "logs/info.log";
                }
            }
        }

        { // error_log_path
            if (error_log_path == null || error_log_path.equalsIgnoreCase("null")) {
                error_log_path = "null";
            } else {
                if (error_log_path.equals(logDefault)) {
                    // this works for windows as well
                    error_log_path = CLIOptions.get(main).getInstallationPath() + "logs/error.log";
                }
            }
        }

        { // base_path
            String n_base_path = this.base_path; // Don't modify the original value from the config
            if (n_base_path == null || n_base_path.equals("/") || n_base_path.isEmpty()) {
                base_path = "";
            } else {
                while (n_base_path.contains("//")) { // Catch corner case where there are multiple '/' together
                    n_base_path = n_base_path.replace("//", "/");
                }
                if (!n_base_path.startsWith("/")) { // Add leading '/'
                    n_base_path = "/" + n_base_path;
                }
                if (n_base_path.endsWith("/")) { // Remove trailing '/'
                    n_base_path = n_base_path.substring(0, n_base_path.length() - 1);
                }
                base_path = n_base_path;
            }
        }

        // the reason we do Math.max below is that if the password hashing algo is
        // bcrypt,
        // then we don't check the argon2 hashing pool size config at all. In this case,
        // if the user gives a <= 0 number, it crashes the core (since it creates a
        // blockedqueue in PaswordHashing
        // .java with length <= 0). So we do a Math.max
        argon2_hashing_pool_size = Math.max(1, argon2_hashing_pool_size);

        firebase_password_hashing_pool_size = Math.max(1, firebase_password_hashing_pool_size);

        if (api_keys != null) {
            String[] apiKeys = api_keys.trim().replaceAll("\\s", "").split(",");
            Arrays.sort(apiKeys);
            api_keys = String.join(",", apiKeys);
        }
        if (supertokens_saas_secret != null) {
            supertokens_saas_secret = supertokens_saas_secret.trim();
        }

        Integer cliPort = CLIOptions.get(main).getPort();
        if (cliPort != null) {
            port = cliPort;
        }

        String cliHost = CLIOptions.get(main).getHost();
        if (cliHost != null) {
            host = cliHost;
        }

        access_token_validity = access_token_validity * 1000;
        access_token_dynamic_signing_key_update_interval = access_token_dynamic_signing_key_update_interval * 3600 * 1000;
        refresh_token_validity = refresh_token_validity * 60 * 1000;

        isNormalizedAndValid = true;
    }

    public void createLoggingFile(Main main) throws IOException {
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

    static void assertThatCertainConfigIsNotSetForAppOrTenants(JsonObject config) throws InvalidConfigException {
        // these are all configs that are per core. So we do not allow the developer to set these dynamically.
        for (Field field : CoreConfig.class.getDeclaredFields()) {
            if (field.isAnnotationPresent(ConfigYamlOnly.class)) {
                if (config.has(field.getName())) {
                    throw new InvalidConfigException(field.getName() + " can only be set via the core's base config setting");
                }
            }
        }
    }

    void assertThatConfigFromSameAppIdAreNotConflicting(CoreConfig other) throws InvalidConfigException {
        // we do not allow different values for this across tenants in the same app
        for (Field field : CoreConfig.class.getDeclaredFields()) {
            if (field.isAnnotationPresent(NotConflictingInApp.class)) {
                try {
                    if (!Objects.equals(field.get(this), field.get(other))) {
                        throw new InvalidConfigException(
                                "You cannot set different values for " + field.getName() +
                                        " for the same appId");
                    }
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    public String getDefaultCDIVersion() {
        return this.supertokens_default_cdi_version;
    }
}

