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

package io.supertokens.cli.commandHandler.help;

import io.supertokens.cli.Main;
import io.supertokens.cli.Utils;
import io.supertokens.cli.commandHandler.CommandHandler;
import io.supertokens.cli.commandHandler.install.InstallHandler;
import io.supertokens.cli.commandHandler.update.UpdateCompletionHandler;
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
            if (command instanceof InstallHandler || command instanceof UpdateCompletionHandler) {
                continue;
            }
            if (command != null) {
                String commandName = command.getCommandName();
                if (commandName == null) {
                    continue;   // this is an options for main supertokens command
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
        printFileInfo("License key file", installationDir + "licenseKey");
        printFileInfo("Legal license", installationDir + "LICENSE.md");
        Logging.info("");
        Logging.info("Thank you for checking out SuperTokens :)");
        Logging.info("");
    }

    private void printFileInfo(String key, String value) {
        Logging.info(
                Utils.formatWithFixedSpaces("    " + key, value, 40, 80));
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
