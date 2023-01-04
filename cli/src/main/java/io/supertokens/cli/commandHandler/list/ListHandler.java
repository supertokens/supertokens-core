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
                Logging.info("-> Instance PID: " + p.pid + ", address: " + p.hostName + ":" + p.port
                        + ", config file loaded: " + p.configFilePath);
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
