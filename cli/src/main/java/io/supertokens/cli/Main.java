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

package io.supertokens.cli;

import io.supertokens.cli.commandHandler.CommandHandler;
import io.supertokens.cli.commandHandler.hashingCalibrate.HashingCalibrateHandler;
import io.supertokens.cli.commandHandler.help.HelpHandler;
import io.supertokens.cli.commandHandler.install.InstallHandler;
import io.supertokens.cli.commandHandler.list.ListHandler;
import io.supertokens.cli.commandHandler.start.StartHandler;
import io.supertokens.cli.commandHandler.stop.StopHandler;
import io.supertokens.cli.commandHandler.uninstall.UninstallHandler;
import io.supertokens.cli.commandHandler.version.VersionHandler;
import io.supertokens.cli.exception.QuitProgramException;
import io.supertokens.cli.logging.Logging;

import java.util.HashMap;
import java.util.Map;

public class Main {

    public static boolean makeConsolePrintSilent = false;

    public static boolean isTesting = false;

    public static Map<String, CommandHandler> commandHandler = new HashMap<>();

    public static int exitCode = 0;

    private static Thread shutdownHook;
    private static Thread mainThread = Thread.currentThread();
    private static boolean doNotWaitInShutdownHook = false;

    public static void main(String[] args) {
        try {
            start(args);
        } catch (Exception e) {
            if (isTesting) {
                throw e;
            }
            if (e instanceof QuitProgramException) {
                if (!e.getMessage().equals("")) {
                    if (((QuitProgramException) e).exception != null) {
                        Logging.error(((QuitProgramException) e).exception);
                    }
                    Logging.error(e.getMessage());
                }
            } else {
                Logging.error(e);
            }
            exitCode = 1;
        }
        if (!Main.isTesting) {
            doNotWaitInShutdownHook = true;
            System.exit(exitCode);
        }
    }

    // java -classpath "./cli/*" io.supertokens.cli.Main <args>
    // - args: true [--path <path location>] --> via installer is true
    // - args: false <installation path> <command> <...command args>
    private static void start(String[] args) {
        boolean viaInstaller = Boolean.parseBoolean(args[0]);
        String installationDir;
        String command;
        String[] options;

        if (viaInstaller) {
            installationDir = "ignored";
            command = "install";
            options = java.util.Arrays.stream(args, 1, args.length).toArray(String[]::new);
        } else {
            installationDir = args[1];

            if (args.length == 2) {
                args = new String[]{args[0], args[1], "--help"};
            }

            command = args[2];
            options = java.util.Arrays.stream(args, 3, args.length).toArray(String[]::new);
        }

        initCommandHandlers();
        if (commandHandler.containsKey(command)) {
            CommandHandler handler = commandHandler.get(command);
            if (handler != null) {
                handler.handleCommand(installationDir, viaInstaller, options);
            }
        } else {
            throw new QuitProgramException("Unknown command '" + command
                    + "'. Please use \"supertokens --help\" to see the list of available commands", null);
        }
    }

    private static void initCommandHandlers() {
        commandHandler.put("--help", new HelpHandler());
        commandHandler.put("--version", new VersionHandler());
        commandHandler.put("install", new InstallHandler());
        commandHandler.put("uninstall", new UninstallHandler());
        commandHandler.put("start", new StartHandler());
        commandHandler.put("stop", new StopHandler());
        commandHandler.put("list", new ListHandler());
        commandHandler.put("hashingCalibrate", new HashingCalibrateHandler());
    }

    public static void removeShutdownHook() {
        if (shutdownHook != null) {
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            } catch (IllegalStateException e) {
                // we are shutting down already.. so doesn't matter
            }
        }
    }

    public static void handleKillSignalForWhenItHappens() {
        shutdownHook = new Thread(() -> {
            if (doNotWaitInShutdownHook) {
                return;
            }
            try {
                mainThread.join();
            } catch (InterruptedException ignored) {
            }
        });
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }
}