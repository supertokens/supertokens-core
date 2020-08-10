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

package io.supertokens.cli.commandHandler.update;

import io.supertokens.cli.OperatingSystem;
import io.supertokens.cli.Utils;
import io.supertokens.cli.cliOptionsParsers.CLIOptionsParser;
import io.supertokens.cli.commandHandler.CommandHandler;
import io.supertokens.cli.commandHandler.install.InstallHandler;
import io.supertokens.cli.commandHandler.stop.StopHandler;
import io.supertokens.cli.exception.QuitProgramException;
import io.supertokens.cli.httpRequest.HTTPResponseException;
import io.supertokens.cli.logging.Logging;
import io.supertokens.cli.processes.Processes;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

public class UpdateCompletionHandler extends CommandHandler {

    // TODO: print out proper commands

    @Override
    public void doCommand(String installationDir, boolean viaInstaller, String[] args) {
        String originalInstallDir = CLIOptionsParser.parseOption("--originalInstallDir", args);
        if (originalInstallDir == null) {
            throw new QuitProgramException("Please pass --originalInstallDir option", null);
        }
        try {
            doCommandHelper(installationDir, originalInstallDir);
        } catch (Exception e) {
            throw new QuitProgramException("Update failed. Please try again", e);
        } finally {
            // we want to delete the .update folder no matter what and we wait for sometime so that this JVM can end
            String exeFilePath = InstallHandler.getSupertokensScriptLocation(null);
            ProcessBuilder pb;
            if (OperatingSystem.getOS() == OperatingSystem.OS.WINDOWS) {
                pb = new ProcessBuilder("cmd.exe", "/c",
                        "timeout 2 & xcopy /s/Y .update\\supertokensNew.bat \"" + exeFilePath +
                                "\" & rmdir /S /Q .update");
            } else {
                pb = new ProcessBuilder("bash", "-c",
                        "sleep 2 ; cp -f .update/supertokensNew " + exeFilePath +
                                " ; rm -rf .update");
            }
            pb.directory(new File(originalInstallDir));
            try {
                pb.start();
            } catch (IOException ignored) {
            }
        }
    }

    private void doCommandHelper(String installationDir, String originalInstallDir)
            throws IOException, HTTPResponseException, InterruptedException {

        // 1) Fetch all running processes and their information
        List<Processes.RunningProcess> runningProcesses = Processes.getRunningProcesses(originalInstallDir);
        for (Processes.RunningProcess runningProcess : runningProcesses) {
            try {
                runningProcess.fetchConfigFilePath();
            } catch (Exception ignored) {
                // this will cause an error to be thrown below where it will not be able to start all processes
            }
        }

        // 2) Stop all the running processes
        for (Processes.RunningProcess runningProcess : runningProcesses) {
            Logging.info("Stopping process on: " + runningProcess.hostName + ":" + runningProcess.port);
            boolean killed = StopHandler.stopProcess(originalInstallDir, runningProcess, true);
            if (!killed) {
                throw new QuitProgramException(
                        "One of the SuperTokens processes might have stopped during the update process. Please run " +
                                "\"supertokens list\" command to check which ones are still running.", null);
            }
        }

        // 3) Copy files to actual installation dir
        try {
            {
                File cli = new File(originalInstallDir + "cli");
                Utils.deleteDirOrFile(cli);
                Utils.copyFolderOrFile(new File(installationDir + "cli"), cli);
            }
            {
                File core = new File(originalInstallDir + "core");
                Utils.deleteDirOrFile(core);
                Utils.copyFolderOrFile(new File(installationDir + "core"), core);
            }
            {
                File downloader = new File(originalInstallDir + "downloader");
                Utils.deleteDirOrFile(downloader);
                Utils.copyFolderOrFile(new File(installationDir + "downloader"), downloader);
            }
            {
                File jre = new File(originalInstallDir + "jre");
                Utils.deleteDirOrFile(jre);
                Utils.copyFolderOrFile(new File(installationDir + "jre"), jre);
            }
            {
                File licenseKey = new File(originalInstallDir + "licenseKey");
                Utils.deleteDirOrFile(licenseKey);
                Utils.copyFolderOrFile(new File(installationDir + "licenseKey"), licenseKey);
            }
            {
                File plugin = new File(originalInstallDir + "plugin");
                Utils.deleteDirOrFile(plugin);
                Utils.copyFolderOrFile(new File(installationDir + "plugin"), plugin);
            }
            {
                File pluginInterface = new File(originalInstallDir + "plugin-interface");
                Utils.deleteDirOrFile(pluginInterface);
                Utils.copyFolderOrFile(new File(installationDir + "plugin-interface"), pluginInterface);
            }
            {
                File version = new File(originalInstallDir + "version.yaml");
                Utils.deleteDirOrFile(version);
                Utils.copyFolderOrFile(new File(installationDir + "version.yaml"), version);
            }
            {
                File license = new File(originalInstallDir + "LICENSE.md");
                Utils.deleteDirOrFile(license);
                Utils.copyFolderOrFile(new File(installationDir + "LICENSE.md"), license);
            }
            {
                File license = new File(originalInstallDir + "OpenSourceLicenses.pdf");
                Utils.deleteDirOrFile(license);
                Utils.copyFolderOrFile(new File(installationDir + "OpenSourceLicenses.pdf"), license);
            }
            {
                File license = new File(originalInstallDir + "SuperTokensLicense.pdf");
                Utils.deleteDirOrFile(license);
                Utils.copyFolderOrFile(new File(installationDir + "SuperTokensLicense.pdf"), license);
            }
            // supertokens executable will be copied in finally block in calling function
        } catch (IOException e) {
            throw new QuitProgramException("Critical error while updating. Please delete " + originalInstallDir +
                    " and install SuperTokens again. Apologies for the inconvenience.", e);
        }

        // 4) Restart all the processes that had been killed. In case any fails, continue anyways.
        boolean allStarted = true;
        try {
            // TODO: if the update command is run via sudo, then that is also used for these children processes.
            for (Processes.RunningProcess runningProcess : runningProcesses) {
                if (runningProcess.configFilePath == null) {
                    throw new Exception("Config file location missing");
                }
                Logging.info("Restarting process on: " + runningProcess.hostName + ":" + runningProcess.port);
                ProcessBuilder pb;
                if (OperatingSystem.getOS() == OperatingSystem.OS.WINDOWS) {
                    pb = new ProcessBuilder("cmd.exe", "/c",
                            "\"" + originalInstallDir +
                                    "jre\\bin\"\\java -classpath \"cli\\*\" io.supertokens.cli.Main false " +
                                    ".\\ start --with-config=" +
                                    runningProcess.configFilePath + " --port=" + runningProcess.port + " --host=" +
                                    runningProcess.hostName);
                } else {
                    pb = new ProcessBuilder("bash", "-c",
                            "jre/bin/java -classpath \"./cli/*\" io.supertokens.cli.Main false " +
                                    "./ start --with-config=" +
                                    runningProcess.configFilePath + " --port=" + runningProcess.port + " --host=" +
                                    runningProcess.hostName);
                }
                pb.directory(new File(originalInstallDir));
                Process p = pb.start();
                p.waitFor();
                if (p.exitValue() != 0) {
                    allStarted = false;
                }
            }
        } catch (Exception ignored) {
        }
        if (!allStarted) {
            Logging.info(
                    "Some of the SuperTokens process failed to restart. Please run \"supertokens list\" to see a list" +
                            " " +
                            "of currently running processes.");
        }

        // 5) copy new supertokens script and make changes to installation directory area in that script.
        String dotUpdate = OperatingSystem.getOS() == OperatingSystem.OS.WINDOWS ?
                originalInstallDir + ".update\\" : originalInstallDir + ".update/";
        String superTokensNewLoc =
                OperatingSystem.getOS() == OperatingSystem.OS.WINDOWS ? "supertokensNew.bat" : "supertokensNew";
        String superTokensOld =
                OperatingSystem.getOS() == OperatingSystem.OS.WINDOWS ? UpdateHandler.NEW_EXE_FILE_WINDOWS :
                        UpdateHandler.NEW_EXE_FILE;
        File newExe = new File(dotUpdate + superTokensNewLoc);
        Utils.copyFolderOrFile(new File(dotUpdate + superTokensOld), newExe);
        {
            boolean ignored = newExe.setExecutable(true, false);
        }
        List<String> fileContent = Files.readAllLines(newExe.toPath(), StandardCharsets.UTF_8);

        int y = 0;
        for (int i = 0; i < fileContent.size(); i++) {
            if (fileContent.get(i).startsWith("st_install_loc")) {
                y = i;
                fileContent.set(i, "st_install_loc=" + originalInstallDir);
                break;
            } else if (fileContent.get(i).startsWith("set st_install_loc=")) {
                fileContent.set(i, "set st_install_loc=" + originalInstallDir);
                break;
            }
        }
        Files.write(newExe.toPath(), fileContent, StandardCharsets.UTF_8);

        Logging.info("Update successful!");
    }

    @Override
    protected List<Option> getOptionsAndDescription() {
        return null;
    }

    @Override
    public String getShortDescription() {
        throw new RuntimeException("should not come here");
    }


    @Override
    public String getUsage() {
        throw new RuntimeException("should not come here");
        // supertokens update-complete --insideUpdate=boolean
    }

    @Override
    public String getCommandName() {
        throw new RuntimeException("should not come here");
    }
}
