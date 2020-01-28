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
                        "More than one SuperTokens instance is still running. Please run \"supertokens stop\" and " +
                                "then run the uninstall command again.");
                Main.exitCode = 1;
                return;
            }
            String executableLocation = InstallHandler.getSupertokensScriptLocation(null);
            if (OperatingSystem.getOS() == OperatingSystem.OS.WINDOWS) {
                // we cannot delete files currently being used - hence, we cannot delete anything.
                // That executable will then delete the installation dir and itself
            } else {
                if (!Utils.deleteDirOrFile(new File(installationDir)) ||
                        !Utils.deleteDirOrFile(new File(executableLocation))) {
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
