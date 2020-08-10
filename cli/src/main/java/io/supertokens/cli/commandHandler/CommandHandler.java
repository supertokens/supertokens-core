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
