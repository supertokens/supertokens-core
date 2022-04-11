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

package io.supertokens.config;

import io.supertokens.exceptions.QuitProgramException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.regex.Pattern;

public class ConfigPreprocessor {
    private static final Pattern commentPattern = Pattern.compile("(?:^|[^\\\\])#(.*)"); // (?:^|[^\\])#(.*)
    private static final Pattern envPattern = Pattern.compile("(?:^|[^\\\\])\\$\\{([^}]*)}"); // (?:^|[^\\])\$\{([^}]*)}
    private static final Pattern escEnvPattern = Pattern.compile("\\\\\\$\\{([^}]*)}"); // \\\$\{([^}]*)}

    private final String config;
    private String processedConfig;

    public ConfigPreprocessor(String config) {
        this.config = config;
        this.processedConfig = null;
    }

    private String removeComments(String str) {
        return commentPattern.matcher(str).replaceAll(match -> match.group().substring(0, match.start(1)));
    }

    private String substituteEnvironmentVariables(String str) {
        return envPattern.matcher(str).replaceAll(match -> {
            String envVarName = match.group(1).trim();
            String envVar = System.getenv(envVarName);
            if (envVar == null)
                throw new QuitProgramException("Environment variable \"" + envVarName + "\" does not exist."
                        + "\nUse \"\\${" + envVarName + "}\" if you are not inserting an environment variable here.");
            return envVar;
        });
    }

    private String unescapeEscapedEnvironmentVariables(String str) {
        return escEnvPattern.matcher(str).replaceAll(match -> match.group().substring(1));
    }

    public String getProcessedConfig() {
        if (processedConfig == null) {
            processedConfig = removeComments(config);
            processedConfig = substituteEnvironmentVariables(processedConfig);
            processedConfig = unescapeEscapedEnvironmentVariables(processedConfig);
        }
        return processedConfig;
    }

    public static ConfigPreprocessor loadFromFile(String configFilePath) throws IOException {
        return new ConfigPreprocessor(Files.readString(new File(configFilePath).toPath()));
    }
}
