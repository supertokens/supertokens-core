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
