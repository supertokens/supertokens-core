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

package io.supertokens.cli.commandHandler.start;

import io.supertokens.cli.Main;
import io.supertokens.cli.OperatingSystem;
import io.supertokens.cli.cliOptionsParsers.CLIOptionsParser;
import io.supertokens.cli.commandHandler.CommandHandler;
import io.supertokens.cli.exception.QuitProgramException;
import io.supertokens.cli.logging.Logging;
import io.supertokens.cli.processes.Processes;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class StartHandler extends CommandHandler {

    @Override
    public void doCommand(String installationDir, boolean viaInstaller, String[] args) {
        boolean isDevMode = CLIOptionsParser.hasKey("dev", args);
        boolean isProductionMode = CLIOptionsParser.hasKey("production", args);
        if (!isDevMode && !isProductionMode) {
            Logging.info("ERROR: Please specify if you are running this instance for development or production use");
            super.printHelp();
            Main.exitCode = 1;
            return;
        }
        if (isDevMode && isProductionMode) {
            Logging.info(
                    "ERROR: Please specify only one of \"dev\" or \"production\" options, and not both.");
            Main.exitCode = 1;
            return;
        }
        if (isProductionMode) {
            boolean preventFromStarting = false;
            try {
                List<Processes.RunningProcess> runningProcesses = Processes.getRunningProcesses(installationDir);
                preventFromStarting = runningProcesses.size() > 0;
            } catch (IOException e) {
                preventFromStarting = true;
            }
            if (preventFromStarting) {
                Logging.info(
                        "ERROR: You can only run one instance in production mode on the Community version.\n\nIf you " +
                                "want to multiple instances for production use, please upgrade to the Pro version by " +
                                "visiting your SuperTokens dashboard.\n");
                Main.exitCode = 1;
                return;
            }
        }
        String mode = isDevMode ? "DEV" : "PRODUCTION";
        String space = CLIOptionsParser.parseOption("--with-space", args);
        String configPath = CLIOptionsParser.parseOption("--with-config", args);
        if (configPath != null) {
            configPath = new File(configPath).getAbsolutePath();
        }
        String port = CLIOptionsParser.parseOption("--port", args);
        String host = CLIOptionsParser.parseOption("--host", args);
        boolean foreground = CLIOptionsParser.hasKey("--foreground", args);

        List<String> commands = new ArrayList<>();
        if (OperatingSystem.getOS() == OperatingSystem.OS.WINDOWS) {
            commands.add(installationDir + "jre\\bin\\java.exe");
            commands.add("-classpath");
            commands.add("\"" + installationDir + "core\\*\";\"" + installationDir + "plugin-interface\\*\"");
            if (space != null) {
                commands.add("-Xmx" + space + "M");
            }
            commands.add("io.supertokens.Main");
            commands.add(
                    "\"" + installationDir + "\\\"");  // so many quotes at the end cause installationDir also ends in \
            commands.add(mode);
            if (configPath != null) {
                configPath = configPath.replace("\\", "\\\\");
                commands.add("configFile=" + configPath);
            }
            if (host != null) {
                commands.add("host=" + host);
            }
            if (port != null) {
                commands.add("port=" + port);
            }
        } else {
            commands.add(installationDir + "jre/bin/java");
            commands.add("-Djava.security.egd=file:/dev/urandom");
            commands.add("-classpath");
            commands.add(installationDir + "core/*:" + installationDir + "plugin-interface/*");
            if (space != null) {
                commands.add("-Xmx" + space + "M");
            }
            commands.add("io.supertokens.Main");
            commands.add(installationDir);
            commands.add(mode);
            if (configPath != null) {
                commands.add("configFile=" + configPath);
            }
            if (host != null) {
                commands.add("host=" + host);
            }
            if (port != null) {
                commands.add("port=" + port);
            }
        }
        if (!foreground) {
            try {
                ProcessBuilder pb = new ProcessBuilder(commands);
                pb.redirectErrorStream(true);
                Process process = pb.start();
                try (InputStreamReader in = new InputStreamReader(process.getInputStream());
                     BufferedReader reader = new BufferedReader(in)) {
                    String line;
                    boolean success = false;
                    while ((line = reader.readLine()) != null) {
                        Logging.info(line); // TODO: make error go to Logging.error and other go to Logging.info - later
                        if (line.startsWith("Started SuperTokens on")) {
                            success = true;
                            break;
                        }
                    }
                    if (!success) {
                        throw new QuitProgramException("", null);
                    }
                }
            } catch (Exception e) {
                throw new QuitProgramException("Unable to start SuperTokens. Please try again", e);
            }
        } else {
            try {
                Main.handleKillSignalForWhenItHappens();
                ProcessBuilder pb = new ProcessBuilder(commands);
                pb.inheritIO();
                Process process = pb.start();
                process.waitFor();
                int exitValue = process.exitValue();
                if (process.exitValue() != 0 && exitValue != 130) {
                    // 130 means user killed this process and that the shutdownHook will be called, and if
                    // we throw an error, then for some reason this process doesn't quit.
                    throw new QuitProgramException("", null);
                }
            } catch (Exception e) {
                throw new QuitProgramException("Unable to start SuperTokens. Please try again", e);
            } finally {
                Main.removeShutdownHook();
            }
        }
    }

    @Override
    public String getShortDescription() {
        return "Start an instance of SuperTokens";
    }


    @Override
    public String getUsage() {
        return "supertokens start [dev | production] [--with-space=<amount in mb>] [--with-config=<config file path>]" +
                " " +
                "[--port=<value>] " +
                "[--host=<value>] [--foreground]";
    }

    @Override
    public String getCommandName() {
        return "start [options]";
    }

    @Override
    public String getLongDescription() {
        return "Start an instance of SuperTokens for development or production use. By default the process will be " +
                "started as a daemon";
    }

    @Override
    protected List<Option> getOptionsAndDescription() {
        List<Option> options = new ArrayList<>();
        options.add(
                new Option("dev",
                        "Start an instance for development use. You can run unlimited number of instances in this " +
                                "mode."));
        options.add(new Option("production",
                "Start an instance for production use. You can run unlimited number of instances in this mode only if" +
                        " you have a non expired licenseKey. To get a new licenseKey, or check for expiry of your " +
                        "current one, please visit your SuperTokens dashboard."));
        options.add(new Option("--with-space",
                "Sets the amount of space, in MB, to allocate to the JVM. Example to allocate 200MB: " +
                        "\"--with-space=200\""));
        options.add(new Option("--with-config",
                "Specify the location of the config file to load. Can be either relative or absolute. Example: " +
                        "\"--with-config=/usr/config.yaml\""));
        options.add(new Option("--port",
                "Sets the port on which this instance of SuperTokens should run. Example: \"--port=8080\""));
        options.add(new Option("--host",
                "Sets the host on which this instance of SuperTokens should run. Example: \"--host=192.168.0.1\""));
        options.add(new Option("--foreground",
                "Runs this instance of SuperTokens in the foreground (not as a daemon)"));
        return options;
    }

}
