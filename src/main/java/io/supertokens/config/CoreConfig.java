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
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.cliOptions.CLIOptions;
import io.supertokens.config.annotations.*;
import io.supertokens.pluginInterface.ConfigFieldInfo;
import io.supertokens.pluginInterface.LOG_LEVEL;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.utils.SemVer;
import io.supertokens.webserver.Utils;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import org.apache.catalina.filters.RemoteAddrFilter;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;
import java.util.regex.PatternSyntaxException;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CoreConfig {

    // Annotations and their meaning
    // @ConfigDescription: This is a description of the config field. Note that this description should match with the
    // description in the config.yaml and devConfig.yaml file.
    // @EnumProperty: The property has fixed set of values (like an enum)
    // @ConfigYamlOnly: The property is configurable only from the config.yaml file.
    // @NotConflictingInApp: The property cannot have different values for tenants within an app
    // @IgnoreForAnnotationCheck: Set this if the property is neither @ConfigYamlOnly nor @NotConflictingInApp, or should
    // simply be ignored by the test (if the property is just an internal member and not an exposed config) that checks
    // for annotations on all properties.
    // @HideFromDashboard: The property should not be shown in the dashboard

    @IgnoreForAnnotationCheck
    public static final String[] PROTECTED_CONFIGS = new String[]{
            "ip_allow_regex",
            "ip_deny_regex",
    };

    @IgnoreForAnnotationCheck
    @JsonProperty
    @ConfigDescription("The version of the core config.")
    private int core_config_version = -1;

    @NotConflictingInApp
    @JsonProperty
    @ConfigDescription("Time in seconds for how long an access token is valid for. [Default: 3600 (1 hour)]")
    private long access_token_validity = 3600; // in seconds

    @NotConflictingInApp
    @JsonProperty
    @ConfigDescription(
            "Deprecated, please see changelog. Only used in CDI<=2.18 If true, allows for immediate revocation of any" +
                    " access token. Keep in mind that setting this to true will result in a db query for each API " +
                    "call that requires authentication. (Default: false)")
    private boolean access_token_blacklisting = false;

    @NotConflictingInApp
    @JsonProperty
    @ConfigDescription("Time in mins for how long a refresh token is valid for. [Default: 60 * 2400 (100 days)]")
    private double refresh_token_validity = 60 * 2400; // in mins

    @IgnoreForAnnotationCheck
    @JsonProperty
    @ConfigDescription(
            "Time in milliseconds for how long a password reset token / link is valid for. [Default: 3600000 (1 hour)]")
    private long password_reset_token_lifetime = 3600000; // in MS

    @IgnoreForAnnotationCheck
    @JsonProperty
    @ConfigDescription(
            "Time in milliseconds for how long an email verification token / link is valid for. [Default: 24 * 3600 *" +
                    " 1000 (1 day)]")
    private long email_verification_token_lifetime = 24 * 3600 * 1000; // in MS

    @IgnoreForAnnotationCheck
    @JsonProperty
    @ConfigDescription(
            "The maximum number of code input attempts per login before the user needs to restart. (Default: 5)")
    private int passwordless_max_code_input_attempts = 5;

    @IgnoreForAnnotationCheck
    @JsonProperty
    @ConfigDescription(
            "Time in milliseconds for how long a passwordless code is valid for. [Default: 900000 (15 mins)]")
    private long passwordless_code_lifetime = 900000; // in MS

    @IgnoreForAnnotationCheck
    @JsonProperty
    @ConfigDescription("The maximum number of invalid TOTP attempts that will trigger rate limiting. (Default: 5)")
    private int totp_max_attempts = 5;

    @IgnoreForAnnotationCheck
    @JsonProperty
    @ConfigDescription(
            "The time in seconds for which the user will be rate limited once totp_max_attempts is crossed. [Default:" +
                    " 900 (15 mins)]")
    private int totp_rate_limit_cooldown_sec = 900; // in seconds (Default 15 mins)

    @IgnoreForAnnotationCheck
    private final String logDefault = "asdkfahbdfk3kjHS";

    @ConfigYamlOnly
    @JsonProperty
    @ConfigDescription(
            "Give the path to a file (on your local system) in which the SuperTokens service can write INFO logs to. " +
                    "Set it to \"null\" if you want it to log to standard output instead. (Default: installation " +
                    "directory/logs/info.log)")
    private String info_log_path = logDefault;

    @ConfigYamlOnly
    @JsonProperty
    @ConfigDescription(
            "Give the path to a file (on your local system) in which the SuperTokens service can write ERROR logs to." +
                    " Set it to \"null\" if you want it to log to standard error instead. (Default: installation " +
                    "directory/logs/error.log)")
    private String error_log_path = logDefault;

    @NotConflictingInApp
    @JsonProperty
    @ConfigDescription(
            "Deprecated, please see changelog. If this is set to true, the access tokens created using CDI<=2.18 will" +
                    " be signed using a static signing key. (Default: true)")
    private boolean access_token_signing_key_dynamic = true;

    @NotConflictingInApp
    @JsonProperty("access_token_dynamic_signing_key_update_interval")
    @JsonAlias({"access_token_dynamic_signing_key_update_interval", "access_token_signing_key_update_interval"})
    @ConfigDescription("Time in hours for how frequently the dynamic signing key will change. [Default: 168 (1 week)]")
    private double access_token_dynamic_signing_key_update_interval = 168; // in hours

    @ConfigYamlOnly
    @JsonProperty
    @ConfigDescription("The port at which SuperTokens service runs. (Default: 3567)")
    private int port = 3567;

    @ConfigYamlOnly
    @JsonProperty
    @ConfigDescription(
            "The host on which SuperTokens service runs. Values here can be localhost, example.com, 0.0.0.0 or any IP" +
                    " address associated with your machine. (Default: localhost)")
    private String host = "localhost";

    @ConfigYamlOnly
    @JsonProperty
    @ConfigDescription("Sets the max thread pool size for incoming http server requests. (Default: 10)")
    private int max_server_pool_size = 10;

    @NotConflictingInApp
    @JsonProperty
    @HideFromDashboard
    @ConfigDescription(
            "The API keys to query an instance using this config file. The format is \"key1,key2,key3\". Keys can " +
                    "only contain '=', '-' and alpha-numeric (including capital) chars. Each key must have a minimum " +
                    "length of 20 chars. (Default: null)")
    private String api_keys = null;

    @NotConflictingInApp
    @JsonProperty
    @ConfigDescription(
            "Learn more about Telemetry here: https://github.com/supertokens/supertokens-core/wiki/Telemetry. " +
                    "(Default: false)")
    private boolean disable_telemetry = false;

    @NotConflictingInApp
    @JsonProperty
    @ConfigDescription("The password hashing algorithm to use. Values are \"ARGON2\" | \"BCRYPT\". (Default: BCRYPT)")
    @EnumProperty({"ARGON2", "BCRYPT"})
    private String password_hashing_alg = "BCRYPT";

    @ConfigYamlOnly
    @JsonProperty
    @ConfigDescription("Number of iterations for argon2 password hashing. (Default: 1)")
    private int argon2_iterations = 1;

    @ConfigYamlOnly
    @JsonProperty
    @ConfigDescription("Amount of memory in kb for argon2 password hashing. [Default: 87795 (85 mb)]")
    private int argon2_memory_kb = 87795; // 85 mb

    @ConfigYamlOnly
    @JsonProperty
    @ConfigDescription("Amount of parallelism for argon2 password hashing. (Default: 2)")
    private int argon2_parallelism = 2;

    @ConfigYamlOnly
    @JsonProperty
    @ConfigDescription(
            "Number of concurrent argon2 hashes that can happen at the same time for sign up or sign in requests. " +
                    "(Default: 1)")
    private int argon2_hashing_pool_size = 1;

    @ConfigYamlOnly
    @JsonProperty
    @ConfigDescription(
            "Number of concurrent firebase scrypt hashes that can happen at the same time for sign in requests. " +
                    "(Default: 1)")
    private int firebase_password_hashing_pool_size = 1;

    @ConfigYamlOnly
    @JsonProperty
    @ConfigDescription("Number of rounds to set for bcrypt password hashing. (Default: 11)")
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
    @ConfigDescription("Used to prepend a base path to all APIs when querying the core.")
    private String base_path = "";

    @ConfigYamlOnly
    @JsonProperty
    @ConfigDescription(
            "Logging level for the core. Values are \"DEBUG\" | \"INFO\" | \"WARN\" | \"ERROR\" | \"NONE\". (Default:" +
                    " INFO)")
    @EnumProperty({"DEBUG", "INFO", "WARN", "ERROR", "NONE"})
    private String log_level = "INFO";

    @NotConflictingInApp
    @JsonProperty
    @ConfigDescription("The signer key used for firebase scrypt password hashing. (Default: null)")
    private String firebase_password_hashing_signer_key = null;

    @IgnoreForAnnotationCheck
    @JsonProperty
    @ConfigDescription(
            "Regex for allowing requests from IP addresses that match with the value. For example, use the value of " +
                    "127\\.\\d+\\.\\d+\\.\\d+|::1|0:0:0:0:0:0:0:1 to allow only localhost to query the core")
    private String ip_allow_regex = null;

    @IgnoreForAnnotationCheck
    @JsonProperty
    @ConfigDescription(
            "Regex for denying requests from IP addresses that match with the value. Comment this value to deny no IP" +
                    " address.")
    private String ip_deny_regex = null;

    @ConfigYamlOnly
    @JsonProperty
    @HideFromDashboard
    @ConfigDescription(
            "This is used when deploying the core in SuperTokens SaaS infrastructure. If set, limits what database " +
                    "information is shown to / modifiable by the dev when they query the core to get the information " +
                    "about their tenants. It only exposes that information when this key is used instead of the " +
                    "regular api_keys config.")
    private String supertokens_saas_secret = null;

    @NotConflictingInApp
    @JsonProperty
    @HideFromDashboard
    @ConfigDescription(
            "This is used when the core needs to assume a specific CDI version when CDI version is not specified in " +
                    "the request. When set to null, the core will assume the latest version of the CDI. (Default: " +
                    "null)")
    private String supertokens_max_cdi_version = null;

    @ConfigYamlOnly
    @JsonProperty
    @ConfigDescription(
            "If specified, the supertokens service will only load the specified CUD even if there are more CUDs in " +
                    "the database and block all other CUDs from being used from this instance.")
    private String supertokens_saas_load_only_cud = null;

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

    public String getSuperTokensLoadOnlyCUD() {
        return supertokens_saas_load_only_cud;
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

    public long getAccessTokenValidityInMillis() {
        return access_token_validity * 1000;
    }

    public boolean getAccessTokenBlacklisting() {
        return access_token_blacklisting;
    }

    public long getRefreshTokenValidityInMillis() {
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

    public long getAccessTokenDynamicSigningKeyUpdateIntervalInMillis() {
        return (long) (access_token_dynamic_signing_key_update_interval * 3600 * 1000);
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

    void normalizeAndValidate(Main main, boolean includeConfigFilePath) throws InvalidConfigException {
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
                    "'access_token_validity' must be between 1 and 86400000 seconds inclusive." +
                            (includeConfigFilePath ? " The config file can be"
                                    + " found here: " + getConfigFileLocation(main) : ""));
        }
        Boolean validityTesting = CoreConfigTestContent.getInstance(main)
                .getValue(CoreConfigTestContent.VALIDITY_TESTING);
        validityTesting = validityTesting == null ? false : validityTesting;

        if ((refresh_token_validity * 60) <= access_token_validity) {
            if (!Main.isTesting || validityTesting) {
                throw new InvalidConfigException(
                        "'refresh_token_validity' must be strictly greater than 'access_token_validity'." +
                                (includeConfigFilePath ? " The config file can be"
                                        + " found here: " + getConfigFileLocation(main) : ""));
            }
        }

        if (!Main.isTesting || validityTesting) { // since in testing we make this really small
            if (access_token_dynamic_signing_key_update_interval < 1) {
                throw new InvalidConfigException(
                        "'access_token_dynamic_signing_key_update_interval' must be greater than, equal to 1 hour." +
                                (includeConfigFilePath ? " The config file can be"
                                        + " found here: " + getConfigFileLocation(main) : ""));
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
                    "'max_server_pool_size' must be >= 1." +
                            (includeConfigFilePath ? " The config file can be"
                                    + " found here: " + getConfigFileLocation(main) : ""));
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
                    throw new InvalidConfigException(
                            "Provided regular expression is invalid for ip_allow_regex config");
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

        if (supertokens_max_cdi_version != null) {
            try {
                SemVer version = new SemVer(supertokens_max_cdi_version);

                if (!WebserverAPI.supportedVersions.contains(version)) {
                    throw new InvalidConfigException("supertokens_max_cdi_version is not a supported version");
                }
            } catch (IllegalArgumentException e) {
                throw new InvalidConfigException("supertokens_max_cdi_version is not a valid semantic version");
            }
        }

        for (String fieldId : CoreConfig.getValidFields()) {
            try {
                Field field = CoreConfig.class.getDeclaredField(fieldId);
                if (field.isAnnotationPresent(EnumProperty.class)) {
                    String[] allowedValues = field.getAnnotation(EnumProperty.class).value();
                    try {
                        String value = field.get(this) != null ? field.get(this).toString() : null;
                        if (!Arrays.asList(Arrays.stream(allowedValues).map(str -> str.toLowerCase()).toArray())
                                .contains(value.toLowerCase())) {
                            throw new InvalidConfigException(
                                    fieldId + " property is not set correctly. It must be one of "
                                            + Arrays.toString(allowedValues));
                        }
                    } catch (IllegalAccessException e) {
                        throw new InvalidConfigException("Could not access field " + fieldId);
                    }
                }
            } catch (NoSuchFieldException e) {
                continue;
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

        if (supertokens_saas_load_only_cud != null) {
            try {
                supertokens_saas_load_only_cud = Utils
                        .normalizeAndValidateConnectionUriDomain(supertokens_saas_load_only_cud, true);
            } catch (ServletException e) {
                throw new InvalidConfigException("supertokens_saas_load_only_cud is invalid");
            }
        }

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
        // these are all configs that are per core. So we do not allow the developer to
        // set these dynamically.
        for (Field field : CoreConfig.class.getDeclaredFields()) {
            if (field.isAnnotationPresent(ConfigYamlOnly.class)) {
                if (config.has(field.getName())) {
                    throw new InvalidConfigException(
                            field.getName() + " can only be set via the core's base config setting");
                }
            }
        }
    }

    public static ArrayList<ConfigFieldInfo> getConfigFieldsInfoForDashboard(Main main,
                                                                             TenantIdentifier tenantIdentifier)
            throws IOException, TenantOrAppNotFoundException {
        JsonObject tenantConfig = new Gson().toJsonTree(Config.getConfig(tenantIdentifier, main)).getAsJsonObject();

        JsonObject defaultConfig = new Gson().toJsonTree(new CoreConfig()).getAsJsonObject();

        ArrayList<ConfigFieldInfo> result = new ArrayList<ConfigFieldInfo>();

        for (String fieldId : CoreConfig.getValidFields()) {
            try {
                Field field = CoreConfig.class.getDeclaredField(fieldId);
                // If fieldId is not annotated with JsonProperty
                // or is annotated with ConfigYamlOnly, then skip
                if (!field.isAnnotationPresent(JsonProperty.class)
                        || field.isAnnotationPresent(ConfigYamlOnly.class)
                        || field.isAnnotationPresent(HideFromDashboard.class)
                        || fieldId.equals("core_config_version")) {
                    continue;
                }

                String key = field.getName();
                String description = field.isAnnotationPresent(ConfigDescription.class)
                        ? field.getAnnotation(ConfigDescription.class).value()
                        : "";

                if (description.contains("Deprecated")) {
                    continue;
                }

                boolean isDifferentAcrossTenants = !field.isAnnotationPresent(NotConflictingInApp.class);

                String valueType = null;

                Class<?> fieldType = field.getType();

                if (fieldType == String.class) {
                    valueType = "string";
                } else if (fieldType == boolean.class) {
                    valueType = "boolean";
                } else if (fieldType == int.class || fieldType == long.class || fieldType == double.class) {
                    valueType = "number";
                } else {
                    throw new RuntimeException("Unknown field type " + fieldType.getName());
                }

                String[] possibleValues = null;

                if (field.isAnnotationPresent(EnumProperty.class)) {
                    valueType = "enum";
                    possibleValues = field.getAnnotation(EnumProperty.class).value();
                }

                JsonElement value = tenantConfig.get(field.getName());

                JsonElement defaultValue = defaultConfig.get(field.getName());
                boolean isNullable = defaultValue == null;

                result.add(new ConfigFieldInfo(
                        key, valueType, value, description, isDifferentAcrossTenants,
                        possibleValues, isNullable, defaultValue, false, false));

            } catch (NoSuchFieldException e) {
                continue;
            }
        }

        return result;
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

    public String getMaxCDIVersion() {
        return this.supertokens_max_cdi_version;
    }
}
