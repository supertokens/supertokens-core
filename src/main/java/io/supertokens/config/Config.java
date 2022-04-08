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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.supertokens.Main;
import io.supertokens.ResourceDistributor;
import io.supertokens.exceptions.QuitProgramException;
import io.supertokens.output.Logging;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Config extends ResourceDistributor.SingletonResource {

    private static final String RESOURCE_KEY = "io.supertokens.config.Config";
    private final Main main;
    private final CoreConfig core;

    private static final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    private static final Pattern envPattern = Pattern.compile("(?:^|[^\\\\])\\$\\{([^}]*)}"); // (?:^|[^\\])\$\{([^}]*)}
    private static final Pattern escEnvPattern = Pattern.compile("\\\\\\$\\{([^}]*)}"); // \\\$\{([^}]*)}

    private Config(Main main, String configFilePath) {
        this.main = main;
        try {
            this.core = loadCoreConfig(configFilePath);
        } catch (IOException e) {
            throw new QuitProgramException(e);
        }
    }

    private static Config getInstance(Main main) {
        return (Config) main.getResourceDistributor().getResource(RESOURCE_KEY);
    }

    public static void loadConfig(Main main, String configFilePath) {
        if (getInstance(main) != null) {
            return;
        }
        main.getResourceDistributor().setResource(RESOURCE_KEY, new Config(main, configFilePath));
    }

    public static CoreConfig getConfig(Main main) {
        if (getInstance(main) == null) {
            throw new QuitProgramException("Please call loadConfig() before calling getConfig()");
        }
        return getInstance(main).core;
    }

    private String preprocessConfig(String configFilePath) throws IOException
    {
        List<String> content = Files.readAllLines(new File(configFilePath).toPath());
        StringBuilder processed = new StringBuilder();
        for(int i = 0; i < content.size(); i++ ) {
            String line = content.get(i);
            Matcher matcher = envPattern.matcher(line);
            final int lineNum = i + 1; // Make final for use in lambda
            String processedLine = matcher.replaceAll(match -> {
                String envVarName = match.group(1).trim();
                String envVar = System.getenv(envVarName);
                if(envVar == null)
                    throw new QuitProgramException("Environment variable \"" + envVarName + "\" does not exist." +
                            " (Line " + lineNum + " of \"" + configFilePath + "\")" +
                            "\nUse \"\\${" + envVarName + "}\" if you are not inserting an environment variable here.");
                return envVar;
            });
            matcher = escEnvPattern.matcher(processedLine);
            processedLine = matcher.replaceAll(match -> match.group().substring(1));
            processed.append(processedLine);
        }
        return processed.toString();
    }

    private CoreConfig loadCoreConfig(String configFilePath) throws IOException {
        Logging.info(main, "Loading supertokens config.");
        String configFileContent = preprocessConfig(configFilePath);
        CoreConfig config = mapper.readValue(configFileContent, CoreConfig.class);
        config.validateAndInitialise(main);
        return config;
    }

}
