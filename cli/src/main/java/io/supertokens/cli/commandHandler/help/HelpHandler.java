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

package io.supertokens.cli.commandHandler.help;

import io.supertokens.cli.Main;
import io.supertokens.cli.Utils;
import io.supertokens.cli.commandHandler.CommandHandler;
import io.supertokens.cli.commandHandler.install.InstallHandler;
import io.supertokens.cli.logging.Logging;

import java.util.List;

public class HelpHandler extends CommandHandler {

    @Override
    public void doCommand(String installationDir, boolean viaInstaller, String[] args) {
        Logging.info("");
        Logging.info("  You are using SuperTokens Community");
        Logging.info("");
        Logging.info("  Usage: supertokens [command] [--help] [--version]");
        Logging.info("");
        Logging.info("");
        Logging.info("  Commands:");
        Logging.info("");
        for (CommandHandler command : Main.commandHandler.values()) {
            if (command instanceof InstallHandler) {
                continue;
            }
            if (command != null) {
                String commandName = command.getCommandName();
                if (commandName == null) {
                    continue; // this is an options for main supertokens command
                }
                Logging.info(Utils.formatWithFixedSpaces("->  " + commandName, command.getShortDescription(), 40, 80));
            }
        }
        Logging.info("");
        Logging.info("");
        Logging.info("  Files:");
        Logging.info("");
        printFileInfo("Installation location", installationDir);
        printFileInfo("Default config file", installationDir + "config.yaml");
        printFileInfo("Legal license", installationDir + "LICENSE.md");
        Logging.info("");
        Logging.info("Thank you for checking out SuperTokens :)");
        Logging.info("");
    }

    private void printFileInfo(String key, String value) {
        Logging.info(Utils.formatWithFixedSpaces("    " + key, value, 40, 80));
    }

    @Override
    protected List<Option> getOptionsAndDescription() {
        return null;
    }

    @Override
    public String getShortDescription() {
        return "Displays list of available commands along with their usage and description";
    }

    @Override
    public String getUsage() {
        return "supertokens --help";
    }

    @Override
    public String getCommandName() {
        return null;
    }
}
