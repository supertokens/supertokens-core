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

package io.supertokens.cli.commandHandler.uninstall;

import io.supertokens.cli.Main;
import io.supertokens.cli.OperatingSystem;
import io.supertokens.cli.Utils;
import io.supertokens.cli.commandHandler.CommandHandler;
import io.supertokens.cli.commandHandler.install.InstallHandler;
import io.supertokens.cli.exception.QuitProgramException;
import io.supertokens.cli.logging.Logging;
import io.supertokens.cli.processes.Processes;

import java.io.File;
import java.util.List;

public class UninstallHandler extends CommandHandler {

    @Override
    public void doCommand(String installationDir, boolean viaInstaller, String[] args) {
        try {
            List<Processes.RunningProcess> processes = Processes.getRunningProcesses(installationDir);
            if (processes.size() > 0) {
                Logging.error(
                        "More than one SuperTokens instance is still running. Please run \"supertokens stop\" and "
                                + "then run the uninstall command again.");
                Main.exitCode = 1;
                return;
            }
            String executableLocation = InstallHandler.getSupertokensScriptLocation(null);
            if (OperatingSystem.getOS() == OperatingSystem.OS.WINDOWS) {
                // we cannot delete files currently being used - hence, we cannot delete anything.
                // That executable will then delete the installation dir and itself
            } else {
                if (!Utils.deleteDirOrFile(new File(installationDir))
                        || !Utils.deleteDirOrFile(new File(executableLocation))) {
                    Logging.error("Failed to uninstall SuperTokens. Try again with root permissions");
                    Main.exitCode = 1;
                    return;
                }
                Logging.info("Uninstallation successful. Sorry to see you go :(");
            }
        } catch (Exception e) {
            throw new QuitProgramException("Failed to uninstall. Please try again.", e);
        }
    }

    @Override
    public String getShortDescription() {
        return "Uninstalls SuperTokens";
    }

    @Override
    public String getUsage() {
        return "supertokens uninstall";
    }

    @Override
    public String getCommandName() {
        return "uninstall";
    }

    @Override
    protected List<Option> getOptionsAndDescription() {
        return null;
    }

}
