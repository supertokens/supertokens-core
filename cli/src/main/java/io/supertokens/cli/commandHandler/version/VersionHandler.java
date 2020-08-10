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

package io.supertokens.cli.commandHandler.version;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.supertokens.cli.commandHandler.CommandHandler;
import io.supertokens.cli.exception.QuitProgramException;
import io.supertokens.cli.logging.Logging;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class VersionHandler extends CommandHandler {
    @Override
    public void doCommand(String installationDir, boolean viaInstaller, String[] args) {
        try {
            VersionFile version = getVersion(installationDir);
            Logging.info("You are using SuperTokens Community");
            Logging.info("SuperTokens Core version: " + version.getCoreVersion());
            Logging.info("Plugin Interface version: " + version.getPluginInterfaceVersion());
            Logging.info("Database Plugin name: " + version.getPluginName());
            Logging.info("Database Plugin version: " + version.getPluginVersion());
            Logging.info("Java version: OpenJDK " + System.getProperty("java.version"));
            Logging.info("Installation directory: " + new File(installationDir).getAbsolutePath());
        } catch (IOException e) {
            throw new QuitProgramException("Error while reading version.yaml file", e);
        }
    }

    @Override
    protected List<Option> getOptionsAndDescription() {
        return null;
    }

    public static VersionFile getVersion(String installationDir) throws IOException {
        final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        VersionFile version = mapper.readValue(new File(installationDir + "version.yaml"), VersionFile.class);
        version.validate();
        return version;
    }

    @Override
    public String getShortDescription() {
        return "Display the current version of the core, plugin-interface and plugin";
    }

    @Override
    public String getUsage() {
        return "supertokens --version";
    }

    @Override
    public String getCommandName() {
        return null;
    }

}
