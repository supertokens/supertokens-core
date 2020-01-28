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

public class InstallHandler extends CommandHandler {
    @Override
    public void doCommand(String ignored, boolean viaInstaller, String[] args) {
        String installationDir = getToInstallDir(args);
        String exeLoc = CLIOptionsParser.parseOption("--exeLoc", args);
        if (!viaInstaller) {
            Logging.info(
                    "SuperTokens is already installed! Run \"supertokens --help\" to see the list of all available " +
                            "commands");
            return;
        }
        try {
            boolean superTokensAlreadyInstalled = isSuperTokensAlreadyInstalled(exeLoc);
            if (superTokensAlreadyInstalled) {
                boolean thisIsDifferent = isThisDifferentToExistingInstallation();
                if (thisIsDifferent) {
                    Logging.info(
                            "It seems that you want to override the current SuperTokens installation with a new one.");
                    boolean dbIsSame = isTheDbForCurrentAndInstalledTheSame();
                    if (dbIsSame) {
                        Logging.info("You can do so using the following command and then delete this folder:");
                        Logging.info(getUpdateCommand());
                    } else {
                        Logging.info("You can do so by running the following two commands in the current directory:");
                        Logging.info("supertokens uninstall");
                        Logging.info("install");
                    }
                } else {
                    Logging.info(
                            "This version of SuperTokens is already installed! Run \"supertokens --help\" to see the " +
                                    "list " +
                                    "of all available " +
                                    "commands");
                }
            } else {
                moveContentToInstallationDir(installationDir);
                createSupertokensScript(installationDir, exeLoc);
                Logging.info("Successfully installed SuperTokens! You can now delete this directory safely");
                Logging.info("Run \"supertokens --help\" to see list of available commands");
                Logging.info(
                        "Please fill in the compulsory fields in the config file located here: " + installationDir +
                                "config.yaml");
            }
        } catch (Exception e) {
            throw new QuitProgramException("error while installing SuperTokens. Please try again", e);
        }
    }

    private String getUpdateCommand() throws IOException {
        VersionFile version = VersionHandler.getVersion("");
        return "supertokens update --core=" + version.getCoreVersion() + " --storage=" + version.getPluginVersion();
    }

    private boolean isTheDbForCurrentAndInstalledTheSame() throws IOException {
        VersionFile otherVersionFile = getVersionFileForAlreadyInstalled();
        VersionFile thisVersionFile = VersionHandler.getVersion("");
        return otherVersionFile.getPluginName().equals(thisVersionFile.getPluginName());
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
        Utils.copyFolderOrFile(new File("config.yaml"), new File(installationDir + "config.yaml"));
        Utils.copyFolderOrFile(new File("version.yaml"), new File(installationDir + "version.yaml"));
        Utils.copyFolderOrFile(new File("jre"), new File(installationDir + "jre"));
        Utils.copyFolderOrFile(new File("licenseKey"), new File(installationDir + "licenseKey"));
        Utils.copyFolderOrFile(new File("config.yaml.original"), new File(installationDir + "config.yaml.original"));
        Utils.copyFolderOrFile(new File("LICENSE.md"), new File(installationDir + "LICENSE.md"));
        Utils.copyFolderOrFile(new File("OpenSourceLicenses.pdf"),
                new File(installationDir + "OpenSourceLicenses.pdf"));
        Utils.copyFolderOrFile(new File("SuperTokensLicense.pdf"),
                new File(installationDir + "SuperTokensLicense.pdf"));

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
            content += "@echo off\n";
            content += "set st_install_loc=" + installationDir + "\n";
            content +=
                    "\"%st_install_loc%jre\\bin\"\\java -classpath \"%st_install_loc%cli\\*\" io.supertokens.cli.Main" +
                            " " +
                            "false " +
                            "\"%st_install_loc%\\\" %*\n" +
                            "IF %errorlevel% NEQ 0 (\necho exiting\ngoto:eof\n)\nIF \"%1\" == \"uninstall\" (\nrmdir " +
                            "/S /Q " +
                            "\"%st_install_loc%\"\ndel \"%~f0\"\n)" +
                            "\nIF \"%1\" == \"update\" (\n" +
                            "\"%st_install_loc%.update\"\\supertokensExe.bat update-complete " +
                            "--originalInstallDir=\"%st_install_loc%\\\"" +
                            "\n)" +
                            "\n:eof";
        } else {
            content += "st_install_loc=" + installationDir + "\n";
            content +=
                    "${st_install_loc}jre/bin/java -classpath " +
                            "\"${st_install_loc}cli/*\" io.supertokens.cli.Main " +
                            "false $st_install_loc $@\n" +
                            "if [ $? -eq 0 ] && [ \"$#\" -ne 0 ] && [ $1 == update ]\n" +
                            "then\n" +
                            "${st_install_loc}/.update/supertokensExe update-complete " +
                            "--originalInstallDir=$st_install_loc\n" +
                            "fi\n";
        }
        File f = new File(location);
        if (!f.exists()) {
            boolean success = f.createNewFile();
            if (!success) {
                throw new QuitProgramException(
                        "Installation failed. Try again with" +
                                ((OperatingSystem.getOS() == OperatingSystem.OS.WINDOWS) ? " root permissions." :
                                        " sudo."), null);
            }
        }
        f.setExecutable(true, false);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(f))) {
            writer.write(content);
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
            //  Files\\supertokens\\bin\\"
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
        return "Install SuperTokens. Please do not use this command. Instead, call the install script provided to you" +
                " when you download SuperTokens";
    }

    @Override
    public String getCommandName() {
        return "install";
    }

    @Override
    protected List<Option> getOptionsAndDescription() {
        List<Option> options = new ArrayList<>();
        options.add(new Option("--path",
                "Specify an installation directory. This path can be relative or absolute. Example: " +
                        "\"--path=/usr/local/supertokens/\""));
        options.add(new Option("--with-source",
                "If this is set, then all 3rd party dependencies will be installed along with their source. This " +
                        "option has no affect on the execution of SuperTokens."));
        return options;
    }
}
