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

package io.supertokens.cli.commandHandler;

import io.supertokens.cli.Utils;
import io.supertokens.cli.cliOptionsParsers.CLIOptionsParser;
import io.supertokens.cli.logging.Logging;

import java.util.List;

public abstract class CommandHandler {

    public void handleCommand(String installationDir, boolean viaInstaller, String[] args) {
        if (handleHelp(args)) {
            return;
        }
        doCommand(installationDir, viaInstaller, args);
    }

    private boolean handleHelp(String[] args) {
        if (CLIOptionsParser.hasKey("--help", args)) {
            printHelp();
            return true;
        }
        return false;
    }

    protected abstract void doCommand(String installationDir, boolean viaInstaller, String[] args);

    protected void printHelp() {
        Logging.info("");
        Logging.info("  Usage: " + getUsage());
        Logging.info("");
        Logging.info("  " + getLongDescription());
        Logging.info("");
        List<Option> options = getOptionsAndDescription();
        if (options != null && options.size() != 0) {
            Logging.info("  Options:");
            Logging.info("");
            for (Option o : options) {
                Logging.info(Utils.formatWithFixedSpaces("    " + o.option, o.description, 40, 80));
            }
            Logging.info("");
        }
    }

    protected String getLongDescription() {
        return getShortDescription();
    }

    protected abstract List<Option> getOptionsAndDescription();

    public abstract String getShortDescription();

    public abstract String getUsage();

    public abstract String getCommandName();

    protected static class Option {
        String option;
        String description;

        public Option(String option, String description) {
            this.option = option;
            this.description = description;
        }
    }
}
