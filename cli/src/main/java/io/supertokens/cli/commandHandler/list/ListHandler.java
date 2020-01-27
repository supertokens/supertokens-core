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

package io.supertokens.cli.commandHandler.list;

import io.supertokens.cli.commandHandler.CommandHandler;
import io.supertokens.cli.exception.QuitProgramException;
import io.supertokens.cli.logging.Logging;
import io.supertokens.cli.processes.Processes;

import java.util.List;

public class ListHandler extends CommandHandler {
    @Override
    public void doCommand(String installationDir, boolean viaInstaller, String[] args) {
        try {
            List<Processes.RunningProcess> processes = Processes.getRunningProcesses(installationDir);
            if (processes.size() == 0) {
                Logging.info("No SuperTokens instances running.");
            }
            for (Processes.RunningProcess p : processes) {
                p.fetchConfigFilePath();
                p.fetchDevProductionMode();
                Logging.info("-> Instance PID: " + p.pid + ", address: " + p.hostName + ":" + p.port +
                        ", config file loaded: " + p.configFilePath + ", running mode: " +
                        (p.devProductionMode.equals("DEV") ? "development" : "production"));
            }
        } catch (Exception e) {
            throw new QuitProgramException("Could not execute list command.", e);
        }
    }

    @Override
    protected List<Option> getOptionsAndDescription() {
        return null;
    }

    @Override
    public String getShortDescription() {
        return "List information about all currently running SuperTokens instances";
    }

    @Override
    public String getUsage() {
        return "supertokens list";
    }

    @Override
    public String getCommandName() {
        return "list";
    }
}
