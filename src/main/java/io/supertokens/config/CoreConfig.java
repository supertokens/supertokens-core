/*
 *    Copyright (c) 2020, VRAI Labs and/or its affiliates. All rights reserved.
 *
 *    This program is licensed under the SuperTokens Community License (the
 *    "License") as published by VRAI Labs. You may not use this file except in
 *    compliance with the License. You are not permitted to transfer or
 *    redistribute this file without express written permission from VRAI Labs.
 *
 *    A copy of the License is available in the file titled
 *    "SuperTokensLicense.pdf" inside this repository or included with your copy of
 *    the software or its source code. If you have not received a copy of the
 *    License, please write to VRAI Labs at team@supertokens.io.
 *
 *    Please read the License carefully before accessing, downloading, copying,
 *    using, modifying, merging, transferring or sharing this software. By
 *    undertaking any of these activities, you indicate your agreement to the terms
 *    of the License.
 *
 *    This program is distributed with certain software that is licensed under
 *    separate terms, as designated in a particular file or component or in
 *    included license documentation. VRAI Labs hereby grants you an additional
 *    permission to link the program and your derivative works with the separately
 *    licensed software that they have included with this program, however if you
 *    modify this program, you shall be solely liable to ensure compliance of the
 *    modified program with the terms of licensing of the separately licensed
 *    software.
 *
 *    Unless required by applicable law or agreed to in writing, this program is
 *    distributed under the License on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 *    CONDITIONS OF ANY KIND, either express or implied. See the License for the
 *    specific language governing permissions and limitations under the License.
 *
 */

package io.supertokens.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.supertokens.Main;
import io.supertokens.cliOptions.CLIOptions;
import io.supertokens.exceptions.QuitProgramException;
import io.supertokens.licenseKey.LicenseKey;
import io.supertokens.licenseKey.LicenseKey.MODE;

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
    private String access_token_path = "/";

    @JsonProperty
    private boolean enable_anti_csrf = true;

    @JsonProperty
    private double refresh_token_validity = 60 * 2400; // in mins

    @JsonProperty
    private String refresh_api_path = null;

    private final String logDefault = "asdkfahbdfk3kjHS";
    @JsonProperty
    private String info_log_path = logDefault;

    @JsonProperty
    private String error_log_path = logDefault;

    @JsonProperty
    private String cookie_domain = null;

    @JsonProperty
    private Boolean cookie_secure = null;


    @JsonProperty
    private int port = 3567;

    @JsonProperty
    private String host = "localhost";

    @JsonProperty
    private int max_server_pool_size = 10;

    //	TODO: add https in later version
//	# (OPTIONAL) boolean value (true or false). Set to true if you want to enable https requests to SuperTokens.
//	# If you are not running SuperTokens within a closed network along with your API process, for 
//	# example if you are using multiple cloud vendors, then it is recommended to set this to true.
//	# webserver_https_enabled:
    @JsonProperty
    private boolean webserver_https_enabled = false;

    public int getConfigVersion() {
        return core_config_version;
    }

    public long getAccessTokenValidity() {
        return access_token_validity * 1000;
    }

    public boolean getAccessTokenBlacklisting() {
        return access_token_blacklisting;
    }

    public String getAccessTokenPath() {
        return access_token_path;
    }

    public boolean getEnableAntiCSRF() {
        return enable_anti_csrf;
    }

    public long getRefreshTokenValidity() {
        return (long) (refresh_token_validity * 60 * 1000);
    }

    public String getRefreshAPIPath() {
        return refresh_api_path;
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

    public String getCookieDomain() {
        return cookie_domain;
    }

    public boolean getCookieSecure(Main main) {
        if (cookie_secure == null) {
            // if developing return false so that users who are getting started have an easier time.
            return !CLIOptions.get(main).getUserDevProductionMode().equals("DEV");
        }
        return cookie_secure;
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
                    "'core_config_version' is not set in the config.yaml file. Please redownload and install " +
                            "SuperTokens");
        }
        if (getRefreshAPIPath() == null) {
            throw new QuitProgramException(
                    "'refresh_api_path' is not set in the config.yaml file. Please set this value and restart " +
                            "SuperTokens. The config file can be found here: " + getConfigFileLocation(main));
        }
        if (getCookieDomain() == null) {
            throw new QuitProgramException(
                    "'cookie_domain' is not set in the config.yaml file. Please set this value and restart " +
                            "SuperTokens. The config file can be found here: " + getConfigFileLocation(main));
        }
        if (access_token_validity < 1 || access_token_validity > 86400000) {
            throw new QuitProgramException(
                    "'access_token_validity' must be between 1 and 86400000 seconds inclusive. The config file can be" +
                            " found here: " + getConfigFileLocation(main));
        }
        Boolean validityTesting = CoreConfigTestContent.getInstance(main)
                .getValue(CoreConfigTestContent.VALIDITY_TESTING);
        validityTesting = validityTesting == null ? false : validityTesting;
        if ((refresh_token_validity * 60) <= access_token_validity) {
            if (!Main.isTesting || LicenseKey.get(main).getMode() != MODE.DEV || validityTesting) {
                throw new QuitProgramException(
                        "'refresh_token_validity' must be strictly greater than 'access_token_validity'. The config " +
                                "file can be found here: " + getConfigFileLocation(main));
            }
        }

        if (max_server_pool_size <= 0) {
            throw new QuitProgramException("'max_server_pool_size' must be >= 1. The config file can be found here: " +
                    getConfigFileLocation(main));
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