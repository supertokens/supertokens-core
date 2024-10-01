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

package io.supertokens.cli.commandHandler.install;

import io.supertokens.cli.OperatingSystem;
import io.supertokens.cli.Utils;
import io.supertokens.cli.cliOptionsParsers.CLIOptionsParser;
import io.supertokens.cli.commandHandler.CommandHandler;
import io.supertokens.cli.commandHandler.version.VersionFile;
import io.supertokens.cli.commandHandler.version.VersionHandler;
import io.supertokens.cli.exception.QuitProgramException;
import io.supertokens.cli.logging.Logging;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class InstallHandler extends CommandHandler {
    @Override
    public void doCommand(String ignored, boolean viaInstaller, String[] args) {
        String installationDir = getToInstallDir(args);
        String exeLoc = CLIOptionsParser.parseOption("--exeLoc", args);
        if (!viaInstaller) {
            Logging.info(
                    "SuperTokens is already installed! Run \"supertokens --help\" to see the list of all available "
                            + "commands");
            return;
        }
        try {
            boolean superTokensAlreadyInstalled = isSuperTokensAlreadyInstalled(exeLoc);
            if (superTokensAlreadyInstalled) {
                boolean thisIsDifferent = isThisDifferentToExistingInstallation();
                if (thisIsDifferent) {
                    Logging.info(
                            "It seems that you want to override the current SuperTokens installation with a new one.");
                    Logging.info("You can do so by running the following two commands in the current directory:");
                    Logging.info("supertokens uninstall");
                    Logging.info("install");
                } else {
                    Logging.info(
                            "This version of SuperTokens is already installed! Run \"supertokens --help\" to see the "
                                    + "list " + "of all available " + "commands");
                }
            } else {
                moveContentToInstallationDir(installationDir);
                createSupertokensScript(installationDir, exeLoc);
                Logging.info("Successfully installed SuperTokens! You can now delete this directory safely");
                Logging.info("Run \"supertokens --help\" to see list of available commands");
            }
        } catch (Exception e) {
            if (e.getMessage().toLowerCase().contains("permission denied") && !(e instanceof QuitProgramException)) {
                throw new QuitProgramException("Installation failed. Try again with"
                        + ((OperatingSystem.getOS() == OperatingSystem.OS.WINDOWS) ? " root permissions." : " sudo."),
                        null);
            } else {
                throw new QuitProgramException("error while installing SuperTokens. Please try again", e);
            }
        }
    }

    private VersionFile getVersionFileForAlreadyInstalled() throws IOException {
        String firstCommand = "supertokens";
        if (OperatingSystem.getOS() == OperatingSystem.OS.WINDOWS) {
            firstCommand = "supertokens.bat";
        }
        ProcessBuilder pb = new ProcessBuilder(firstCommand, "--version");
        Process process = pb.start();
        String result = "";
        try (InputStreamReader in = new InputStreamReader(process.getInputStream());
             BufferedReader reader = new BufferedReader(in)) {
            StringBuilder builder = new StringBuilder();
            String line = null;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
                builder.append(System.getProperty("line.separator"));
            }
            result = builder.toString();
        }
        result = Utils.normaliseDirectoryPath(result.split("Installation directory:")[1].trim());
        return VersionHandler.getVersion(result);
    }

    private boolean isThisDifferentToExistingInstallation() throws IOException {
        VersionFile otherVersionFile = getVersionFileForAlreadyInstalled();
        VersionFile thisVersionFile = VersionHandler.getVersion("");
        return !otherVersionFile.equals(thisVersionFile);
    }

    private void moveContentToInstallationDir(String installationDir) throws IOException {
        Utils.copyFolderOrFile(new File("core"), new File(installationDir + "core"));
        Utils.copyFolderOrFile(new File("plugin-interface"), new File(installationDir + "plugin-interface"));
        Utils.copyFolderOrFile(new File("plugin"), new File(installationDir + "plugin"));
        Utils.copyFolderOrFile(new File("cli"), new File(installationDir + "cli"));
        Utils.copyFolderOrFile(new File("downloader"), new File(installationDir + "downloader"));
        Utils.copyFolderOrFile(new File("ee"), new File(installationDir + "ee"));
        Utils.copyFolderOrFile(new File("config.yaml"), new File(installationDir + "config.yaml"));
        Utils.copyFolderOrFile(new File("version.yaml"), new File(installationDir + "version.yaml"));
        Utils.copyFolderOrFile(new File("jre"), new File(installationDir + "jre"));
        Utils.copyFolderOrFile(new File("config.yaml.original"), new File(installationDir + "config.yaml.original"));
        Utils.copyFolderOrFile(new File("LICENSE.md"), new File(installationDir + "LICENSE.md"));

        // create log folder for process to write
        // TODO: This still doesn't make it possible for app to run in non root mode in Windows
        File logs = new File(installationDir + "logs");
        logs.mkdirs();
        logs.setWritable(true, false);

        // create .started folder for the process to write
        File dotStarted = new File(installationDir + ".started");
        dotStarted.mkdirs();
        dotStarted.setWritable(true, false);

        // create webserver-temp folder for process to write
        File webserverTemp = new File(installationDir + "webserver-temp");
        webserverTemp.mkdirs();
        webserverTemp.setWritable(true, false);
    }

    private void createSupertokensScript(String installationDir, String exeLoc) throws IOException {
        String content = "";
        String location = getSupertokensScriptLocation(exeLoc);
        installationDir = Utils.normaliseDirectoryPath(new File(installationDir).getAbsolutePath());
        if (OperatingSystem.getOS() == OperatingSystem.OS.WINDOWS) {
            content += this.getResourceFileAsString("/install-windows.bat");
        } else {
            content += this.getResourceFileAsString("/install-linux.sh");
        }
        content = content.replace("$ST_INSTALL_LOC", installationDir);
        File f = new File(location);
        if (!f.exists()) {
            boolean success = f.createNewFile();
            if (!success) {
                throw new QuitProgramException("Installation failed. Try again with"
                        + ((OperatingSystem.getOS() == OperatingSystem.OS.WINDOWS) ? " root permissions." : " sudo."),
                        null);
            }
        }
        f.setExecutable(true, false);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(f))) {
            writer.write(content);
        }
    }

    /**
     * Reads given resource file as a string.
     *
     * @param fileName path to the resource file
     * @return the file's contents
     * @throws IOException if read fails for any reason
     */
    private String getResourceFileAsString(String fileName) throws IOException {
        try (InputStream is = this.getClass().getResourceAsStream(fileName)) {
            if (is == null) {
                Logging.error("Failed to load resource named " + fileName);
                return null;
            }
            try (InputStreamReader isr = new InputStreamReader(is); BufferedReader reader = new BufferedReader(isr)) {
                return reader.lines().collect(Collectors.joining(System.lineSeparator()));
            }
        }
    }

    private boolean isSuperTokensAlreadyInstalled(String exeLoc) {
        try {
            String firstCommand = exeLoc == null ? "supertokens" : exeLoc;
            if (OperatingSystem.getOS() == OperatingSystem.OS.WINDOWS) {
                firstCommand = exeLoc == null ? "supertokens.bat" : exeLoc;
            }
            ProcessBuilder pb = new ProcessBuilder(firstCommand, "--version");
            Process process = pb.start();
            process.waitFor();
            return process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    // returns the location of the installation dir where SuperTokens needs to be installed.
    private String getToInstallDir(String[] args) {
        String installationDir = CLIOptionsParser.parseOption("--path", args);
        installationDir = installationDir == null ? getDefaultInstallationDir() : installationDir;
        if (OperatingSystem.getOS() == OperatingSystem.OS.WINDOWS) {
            if (!installationDir.endsWith("\\")) {
                installationDir += "\\";
            }
        } else {
            if (!installationDir.endsWith("/")) {
                installationDir += "/";
            }
        }
        return installationDir;
    }

    public static String getDefaultInstallationDir() {
        if (OperatingSystem.getOS() == OperatingSystem.OS.WINDOWS) {
            return "C:\\Program Files\\supertokens\\";
        } else if (OperatingSystem.getOS() == OperatingSystem.OS.MAC) {
            return "/usr/local/etc/supertokens/";
        } else {
            return "/usr/lib/supertokens/";
        }
    }

    public static String getSupertokensScriptLocation(String exeLoc) {
        if (exeLoc != null) {
            return exeLoc;
        }
        if (OperatingSystem.getOS() == OperatingSystem.OS.WINDOWS) {
            // TODO: In windows, the convention is to edit the %PATH% variable to point to "C:\\Program
            // Files\\supertokens\\bin\\"
            return "C:\\Windows\\System32\\supertokens.bat";
        } else if (OperatingSystem.getOS() == OperatingSystem.OS.MAC) {
            return "/usr/local/bin/supertokens";
        } else {
            return "/usr/bin/supertokens";
        }
    }

//    @Override
//    public String getDescription() {
//        return "Install SuperTokens in the specified directory. If a directory is not specified, SuperTokens will" +
//                " be " +
//                "installed in " + getDefaultInstallationDir() +
//                "\n* [--path=<path location>] -> (Optional) Specifies the installation path\n* [--with-source] " +
//                "-> " +
//                "(Optional) Downloads any 3rd party dependencies with their source code.";
//    }

    @Override
    public String getUsage() {
        // --exeName is there, but user does not need to know about it.
        return "supertokens install [--path=<path location>] [--with-source]";
    }

    @Override
    public String getShortDescription() {
        return "Install SuperTokens. Please do not use this command. Instead, call the install script provided to you"
                + " when you download SuperTokens";
    }

    @Override
    public String getCommandName() {
        return "install";
    }

    @Override
    protected List<Option> getOptionsAndDescription() {
        List<Option> options = new ArrayList<>();
        options.add(new Option("--path",
                "Specify an installation directory. This path can be relative or absolute. Example: "
                        + "\"--path=/usr/local/supertokens/\""));
        options.add(new Option("--with-source",
                "If this is set, then all 3rd party dependencies will be installed along with their source. This "
                        + "option has no affect on the execution of SuperTokens."));
        return options;
    }
}
