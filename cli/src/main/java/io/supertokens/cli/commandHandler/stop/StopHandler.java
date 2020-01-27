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

package io.supertokens.cli.commandHandler.stop;

import io.supertokens.cli.Main;
import io.supertokens.cli.OperatingSystem;
import io.supertokens.cli.cliOptionsParsers.CLIOptionsParser;
import io.supertokens.cli.commandHandler.CommandHandler;
import io.supertokens.cli.exception.QuitProgramException;
import io.supertokens.cli.logging.Logging;
import io.supertokens.cli.processes.Processes;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class StopHandler extends CommandHandler {
    @Override
    public void doCommand(String installationDir, boolean viaInstaller, String[] args) {
        try {
            String toKillPID = CLIOptionsParser.parseOption("--id", args);
            List<Processes.RunningProcess> processes = Processes.getRunningProcesses(installationDir);
            if (processes.size() == 0) {
                Logging.info("No SuperTokens instances are running.");
                return;
            }
            boolean killedAll = true;
            boolean killedTarget = false;
            for (Processes.RunningProcess process : processes) {
                if (toKillPID != null) {
                    if (toKillPID.equals(process.pid)) {
                        killedAll = killedAll && stopProcess(installationDir, process, false);
                        killedTarget = true;
                        break;
                    }
                } else {
                    killedAll = stopProcess(installationDir, process, false) && killedAll;
                }
            }
            if (toKillPID != null && !killedTarget) {
                Logging.info("Unknown PID: " + toKillPID +
                        ". Please use \"supertokens list\" to list all currently running instances along " +
                        "with their PIDs");
                Main.exitCode = 1;
                return;
            }
            if (!killedAll) {
                Logging.info("Failed to stop all SuperTokens instances. Try again with root permissions.");
                Main.exitCode = 1;
                return;
            }
        } catch (Exception e) {
            throw new QuitProgramException("Could not stop SuperTokens instances. Please try again", e);
        }
    }

    public static boolean stopProcess(String installationDir, Processes.RunningProcess runningProcess, boolean silent)
            throws InterruptedException, IOException {
        int exitCode = -1;
        if (OperatingSystem.getOS() == OperatingSystem.OS.WINDOWS) {
            // There seems to be no graceful way to shutdown console based processes in windows. So we force kill it
            // and remove the .started file
            ProcessBuilder pb = new ProcessBuilder("taskkill", "/pid", runningProcess.pid, "/F");
            Process process = pb.start();
            process.waitFor();
            exitCode = process.exitValue();
            if (exitCode == 0) {
                File f = new File(installationDir + ".started\\" + runningProcess.hostName + "-" + runningProcess.port);
                exitCode = f.delete() ? 0 : 1;
                // TODO: we also need to delete the webserver-temp folder's content for this process. This can be
                //  done in multiple ways, but all of them require changing the core's code
            }
        } else {
            ProcessBuilder pb = new ProcessBuilder("kill", runningProcess.pid);
            Process process = pb.start();
            process.waitFor();
            exitCode = process.exitValue();
        }
        if (!silent) {
            if (exitCode == 0) {
                Logging.info(
                        "Successfully stopped SuperTokens with PID " + runningProcess.pid + ", running on " +
                                runningProcess.hostName + ":" +
                                runningProcess.port);
            } else {
                Logging.error("Failed to stop SuperTokens with PID " + runningProcess.pid + ", running on " +
                        runningProcess.hostName + ":" +
                        runningProcess.port);
            }
        }
        return exitCode == 0;
    }

    @Override
    public String getUsage() {
        return "supertokens stop [--id=<PID>]";
    }

    @Override
    public String getCommandName() {
        return "stop [options]";
    }

    @Override
    public String getShortDescription() {
        return "Stops all (if no options are provided) or one specific instance of SuperTokens";
    }

    @Override
    protected List<Option> getOptionsAndDescription() {
        List<Option> options = new ArrayList<>();
        options.add(
                new Option("--id",
                        "Stop an instance of SuperTokens that has a specific PID. An instance's PID can be obtained " +
                                "via the \"supertokens list\" command. Example: \"--id=7634\""));
        return options;
    }
}
