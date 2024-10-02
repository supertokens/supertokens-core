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

package io.supertokens.cli.commandHandler.start;

import io.supertokens.cli.Main;
import io.supertokens.cli.OperatingSystem;
import io.supertokens.cli.cliOptionsParsers.CLIOptionsParser;
import io.supertokens.cli.commandHandler.CommandHandler;
import io.supertokens.cli.exception.QuitProgramException;
import io.supertokens.cli.logging.Logging;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class StartHandler extends CommandHandler {

    @Override
    public void doCommand(String installationDir, boolean viaInstaller, String[] args) {
        String space = CLIOptionsParser.parseOption("--with-space", args);
        String configPath = CLIOptionsParser.parseOption("--with-config", args);
        if (configPath != null) {
            configPath = new File(configPath).getAbsolutePath();
        }
        String port = CLIOptionsParser.parseOption("--port", args);
        String host = CLIOptionsParser.parseOption("--host", args);
        boolean foreground = CLIOptionsParser.hasKey("--foreground", args);
        boolean forceNoInMemDB = CLIOptionsParser.hasKey("--no-in-mem-db", args);

        List<String> commands = new ArrayList<>();
        if (OperatingSystem.getOS() == OperatingSystem.OS.WINDOWS) {
            commands.add(installationDir + "jre\\bin\\java.exe");
            commands.add("-classpath");
            commands.add("\"" + installationDir + "core\\*\";\"" + installationDir + "plugin-interface\\*\"");
            if (space != null) {
                commands.add("-Xmx" + space + "M");
            }
            commands.add("io.supertokens.Main");
            commands.add("\"" + installationDir + "\\\""); // so many quotes at the end cause installationDir also ends
            // in \
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
            if (forceNoInMemDB) {
                commands.add("forceNoInMemDB=true");
            }
        } else {
            commands.add(installationDir + "jre/bin/java");
            commands.add("-Djava.security.egd=file:/dev/urandom");
            commands.add("-classpath");
            commands.add(
                    installationDir + "core/*:" + installationDir + "plugin-interface/*:" + installationDir + "ee/*");
            if (space != null) {
                commands.add("-Xmx" + space + "M");
            }
            commands.add("io.supertokens.Main");
            commands.add(installationDir);
            if (configPath != null) {
                commands.add("configFile=" + configPath);
            }
            if (host != null) {
                commands.add("host=" + host);
            }
            if (port != null) {
                commands.add("port=" + port);
            }
            if (forceNoInMemDB) {
                commands.add("forceNoInMemDB=true");
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
                    // 130 means user killed this process and that the shutdownHook will be called,
                    // and if
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
        return "supertokens start [--with-space=<amount in mb>] [--with-config=<config file path>]" + " "
                + "[--port=<value>] " + "[--host=<value>] [--foreground]";
    }

    @Override
    public String getCommandName() {
        return "start [options]";
    }

    @Override
    public String getLongDescription() {
        return "Start an instance of SuperTokens. By default the process will be " + "started as a daemon";
    }

    @Override
    protected List<Option> getOptionsAndDescription() {
        List<Option> options = new ArrayList<>();
        options.add(new Option("--with-space",
                "Sets the amount of space, in MB, to allocate to the JVM. Example to allocate 200MB: "
                        + "\"--with-space=200\""));
        options.add(new Option("--with-config",
                "Specify the location of the config file to load. Can be either relative or absolute. Example: "
                        + "\"--with-config=/usr/config.yaml\""));
        options.add(new Option("--port",
                "Sets the port on which this instance of SuperTokens should run. Example: \"--port=8080\""));
        options.add(new Option("--host",
                "Sets the host on which this instance of SuperTokens should run. Example: \"--host=192.168.0.1\""));
        options.add(
                new Option("--foreground", "Runs this instance of SuperTokens in the foreground (not as a daemon)"));
        return options;
    }

}
