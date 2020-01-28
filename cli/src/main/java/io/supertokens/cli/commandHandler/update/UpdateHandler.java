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

package io.supertokens.cli.commandHandler.update;

import io.supertokens.cli.Main;
import io.supertokens.cli.OperatingSystem;
import io.supertokens.cli.Utils;
import io.supertokens.cli.cliOptionsParsers.CLIOptionsParser;
import io.supertokens.cli.commandHandler.CommandHandler;
import io.supertokens.cli.commandHandler.version.VersionFile;
import io.supertokens.cli.commandHandler.version.VersionHandler;
import io.supertokens.cli.exception.QuitProgramException;
import io.supertokens.cli.licenseKey.LicenseKey;
import io.supertokens.cli.licenseKey.LicenseKeyContent;
import io.supertokens.cli.logging.Logging;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class UpdateHandler extends CommandHandler {

    private static final String SUPERTOKENS_EXTRACTED_NAME = "supertokens";
    private static final String ZIP_NAME = SUPERTOKENS_EXTRACTED_NAME + ".zip";
    private static final String INSTALLATION_DIR = "supertokensInstallation";
    public static final String NEW_EXE_FILE_WINDOWS = "supertokensExe.bat";
    public static final String NEW_EXE_FILE = "supertokensExe";

    // TODO: print helpful messages
    @Override
    public void doCommand(String installationDir, boolean viaInstaller, String[] args) {
        String coreVersion = CLIOptionsParser.parseOption("--core", args);
        String storageVersion = CLIOptionsParser.parseOption("--storage", args);

        // 1 if .update folder already exists, remove it and create one again
        File updateFolder = new File(installationDir + ".update");
        if (updateFolder.exists()) {
            boolean success = Utils.deleteDirOrFile(updateFolder);
            if (!success) {
                UpdateUtils.throwRootPermissionNeededError();
            }
        }
        boolean success = updateFolder.mkdir();
        if (!success) {
            UpdateUtils.throwRootPermissionNeededError();
        }
        boolean removeTheWholeUpdateFolder = true;
        try {
            doPostCreationOfUpdateFolder(updateFolder, coreVersion, storageVersion, installationDir);
            if (Main.exitCode == 0) {   // the command above succeeded, so we remove only the zip folder.
                removeTheWholeUpdateFolder = false;
            }
        } finally {
            if (!removeTheWholeUpdateFolder) {
                // we only keep the installed dir and executable
                // we remove the zip file
                boolean ignored = Utils
                        .deleteDirOrFile(
                                new File(Utils.normaliseDirectoryPath(updateFolder.getAbsolutePath()) + ZIP_NAME));
                // we remove the extracted folder
                ignored = Utils.deleteDirOrFile(new File(Utils
                        .normaliseDirectoryPath(Utils.normaliseDirectoryPath(updateFolder.getAbsolutePath()) +
                                SUPERTOKENS_EXTRACTED_NAME)));
            } else {
                boolean ignored = Utils.deleteDirOrFile(updateFolder);
            }
        }
    }


    private void doPostCreationOfUpdateFolder(File updateFolder, String targetCoreVersion, String targetStorageVersion,
                                              String installationDir) {

        String dotUpdateFilePath = Utils.normaliseDirectoryPath(updateFolder.getAbsolutePath());
        String zipFilePath = Utils.normaliseDirectoryPath(updateFolder.getAbsolutePath()) + ZIP_NAME;
        String extractedFilePath = Utils
                .normaliseDirectoryPath(Utils.normaliseDirectoryPath(updateFolder.getAbsolutePath()) +
                        SUPERTOKENS_EXTRACTED_NAME);
        String installationDirLocation = Utils.normaliseDirectoryPath(
                Utils.normaliseDirectoryPath(updateFolder.getAbsolutePath()) + INSTALLATION_DIR);

        // 1) we load the license key to get appId and licenseKeyId. This also verifies the signature
        LicenseKeyContent licenseKeyContent;
        try {
            licenseKeyContent = LicenseKey
                    .loadAndCheckContent(installationDir + "licenseKey");
        } catch (FileNotFoundException e) {
            throw new QuitProgramException(
                    "LicenseKey file not found. Please go to your SuperTokens dashboard and download a licenseKey. " +
                            "Then use the \"supertokens load-license\" command.",
                    e);
        }


        // 2) we load the current version file so to send info to the API
        VersionFile version = null;
        try {
            version = VersionHandler.getVersion(installationDir);
        } catch (IOException e) {
            UpdateUtils.throwSomethingWentWrongError(e);
        }


        // 3) we now attempt downloading the new version
        try {
            String response = UpdateUtils.downloadFile(UpdateUtils
                    .getUrlForDownload(licenseKeyContent.getAppId(), version.getCoreVersion(),
                            version.getPluginVersion(), OperatingSystem.getOS(),
                            licenseKeyContent.getLicenseKeyId(),
                            targetCoreVersion, targetStorageVersion), zipFilePath, "0");

            if (response != null) {
                if (response.equals("incompatible")) {
                    Logging.info("The passed core and storage versions are incompatible, refer to compatibility " +
                            "tables at https://supertokens.io/docs/pro/compatibility");
                    Main.exitCode = 1;
                    return;
                }
                if (response.equals("upgrade error")) {
                    Logging.info("It seems like you need to upgrade from community to pro, " +
                            "please try again by running \"supertokens update\" (do not pass any core and " +
                            "storage version)");
                    Main.exitCode = 1;
                    return;
                }
                if (response.equals("revoked")) {
                    Logging.info("Your license key has been revoked.");
                    Main.exitCode = 1;
                    return;
                }
                if (response.equals("no change")) {
                    Logging.info("It seems like you are already up to date, nothing more to do.");
                    Main.exitCode = 1;
                    return;
                }
                UpdateUtils.throwSomethingWentWrongError(null);
            } else {
                if (!Utils.isZipFile(new File(zipFilePath))) {
                    throw new IOException("Downloaded file is not a zip");
                }
            }
        } catch (IOException e) {
            UpdateUtils.throwSomethingWentWrongError(e);
        }

        // 4) Extract from the zip to the .update folder
        try {
            Utils.unzip(zipFilePath, dotUpdateFilePath);

            // unzipping does not keep the executable status of the files. So we should change that
            // TODO: Ideally there should be a way to extract it while maintaining "executability"
            if (OperatingSystem.getOS() == OperatingSystem.OS.WINDOWS) {
                File install = new File(extractedFilePath + "install.bat");
                install.setExecutable(true, false);
                Utils.makeAllExeFilesInFolderExecutable(new File(extractedFilePath + "jre"));
            } else if (OperatingSystem.getOS() == OperatingSystem.OS.MAC) {
                new File(extractedFilePath + "install").setExecutable(true, false);
                new File(extractedFilePath + "jre/bin/java").setExecutable(true, false);
                new File(extractedFilePath + "jre/bin/rmid").setExecutable(true, false);
                new File(extractedFilePath + "jre/bin/rmiregistry").setExecutable(true, false);
                new File(extractedFilePath + "jre/bin/jrunscript").setExecutable(true, false);
                new File(extractedFilePath + "jre/bin/keytool").setExecutable(true, false);
                new File(extractedFilePath + "jre/lib/jspawnhelper").setExecutable(true, false);
            } else {
                new File(extractedFilePath + "install").setExecutable(true, false);
                new File(extractedFilePath + "jre/bin/java").setExecutable(true, false);
                new File(extractedFilePath + "jre/bin/rmid").setExecutable(true, false);
                new File(extractedFilePath + "jre/bin/rmiregistry").setExecutable(true, false);
                new File(extractedFilePath + "jre/bin/jrunscript").setExecutable(true, false);
                new File(extractedFilePath + "jre/bin/keytool").setExecutable(true, false);
                new File(extractedFilePath + "jre/lib/jspawnhelper").setExecutable(true, false);
                new File(extractedFilePath + "jre/lib/jexec").setExecutable(true, false);
            }
        } catch (AccessDeniedException e) {
            UpdateUtils.throwRootPermissionNeededError();
        } catch (IOException e) {
            UpdateUtils.throwSomethingWentWrongError(e);
        }
        Utils.deleteDirOrFile(new File(zipFilePath));


        // 5) Ask for acceptance of license change if necessary
        // TODO: prompt for license agreement, only when the legal license of the incoming version is different to
        //  this one
        boolean doLegalPrompt = true;
        try {
            doLegalPrompt = doLegalPrompt(extractedFilePath, licenseKeyContent, version);
        } catch (Exception ignored) {

        }

        if (doLegalPrompt) {
            String newVersion = null;
            try {
                String[] newCoreVersion = UpdateUtils.coreVersionParser(new File(extractedFilePath + "version.yaml"))
                        .split("\\.");
                newVersion = newCoreVersion[0] + "." + newCoreVersion[1];
            } catch (IOException ignored) {
            }
            Logging.info("");
            if (newVersion == null) {
                Logging.infoNoNewLine(
                        "The license agreement for the updated version may have changed. Please go through the new " +
                                "license " +
                                "agreement before continuing. Do you accept the new license? [Y/n]: ");
            } else {
                Logging.infoNoNewLine(
                        "The license agreement for the updated version may have changed. Please go through the " +
                                "license " +
                                "agreement for version " + newVersion +
                                " before continuing. Do you accept the new license? [Y/n]:");
            }
            Scanner sc = new Scanner(System.in);
            String acceptCondition = sc.nextLine();
            if (!acceptCondition.toUpperCase().equals("Y") && !acceptCondition.toUpperCase().equals("YES")) {
                Logging.info("Aborting Update");
                Main.exitCode = 1;
                return;
            }
        }


        // 6) Check the core folder within installation directory and check if there is even one file with "-sources
        // .jar"
        Logging.info("Update in progress...");
        List<String> commandList = new ArrayList<>();
        commandList.add(OperatingSystem.getOS() == OperatingSystem.OS.WINDOWS ?
                extractedFilePath + "install.bat" :
                "./install");
        commandList.add("--path=" + installationDirLocation);
        commandList.add("--exeLoc=" + dotUpdateFilePath +
                (OperatingSystem.getOS() == OperatingSystem.OS.WINDOWS ? NEW_EXE_FILE_WINDOWS : NEW_EXE_FILE));


        if (UpdateUtils.containsSources(new File(installationDir + "core"))) {
            commandList.add("--with-source");
        }

        ProcessBuilder pb = new ProcessBuilder(commandList);
        pb.directory(new File(extractedFilePath));
        boolean success = false;
        try {
            // TODO: print something while the below command is being executed
            Process process = pb.start();
            process.waitFor();
            success = process.exitValue() == 0;
        } catch (InterruptedException | IOException ignored) {
        }
        if (!success) {
            throw new QuitProgramException("Failed to update. Please check your internet connection and try again.",
                    null);
        }
    }

    private boolean doLegalPrompt(String extractedFilePath, LicenseKeyContent oldLicenseKey,
                                  VersionFile oldVersion) throws IOException {
        // load the planType of the new binary manually
        String newLicenseKeyPlanType = UpdateUtils.planTypeParser(new File(extractedFilePath + "licenseKey"));
        String oldLicenseKeyPlanType = "COMMERCIAL";    // we treat COMMERCIAL and  COMMERCIAL_TRIAL as the same for
        // this comparison purposes
        if (oldLicenseKey.getPlanType() == LicenseKey.PLAN_TYPE.FREE) {
            oldLicenseKeyPlanType = "FREE";
        }

        // load the core version of the new binary manually
        String[] newCoreVersion = UpdateUtils.coreVersionParser(new File(extractedFilePath + "version.yaml"))
                .split("\\.");
        String[] oldCoreVersion = oldVersion.getCoreVersion().split("\\.");

        // return true if (this plan type is FREE and that plan type is not FREE) || this core version (X.Y) != that
        // core version (X.Y)
        return ((oldLicenseKeyPlanType.equals("FREE") && !newLicenseKeyPlanType.equals("FREE")) ||
                !(newCoreVersion[0].equals(oldCoreVersion[0]) && newCoreVersion[1].equals(oldCoreVersion[1])));
    }


    @Override
    public String getShortDescription() {
        return "Update SuperTokens to the latest compatible or a specific version";
    }

    @Override
    public String getLongDescription() {
        return "Update SuperTokens to the latest compatible (if no options are provided) or a specific version. " +
                "Updating involves fetching new " +
                "versions of the core, plugin-interface and plugin modules. Depending on the version specified, you " +
                "may also need to check for changes in the driver and frontend SDK versions. To see a compatibility " +
                "matrix, please visit: https://supertokens.io/docs/community/compatibility";
    }

    @Override
    protected List<Option> getOptionsAndDescription() {
        List<Option> options = new ArrayList<>();
        options.add(
                new Option("--core",
                        "Specify a version for the core module of SuperTokens to change to. Either in X.Y or X.Y.Z " +
                                "format. " +
                                "Example: \"--core=2.0\" " +
                                "or \"--core=2.0.1\""));
        options.add(new Option("--storage",
                "Specify a version for the storage module of SuperTokens to change to. Either in X.Y or X.Y.Z format." +
                        " " +
                        "Example: \"--core=2.0\" " +
                        "or \"--core=2.0.1\""));
        return options;
    }

    @Override
    public String getUsage() {
        return "supertokens update [--core=X.Y[.Z]] [--storage=X.Y[.Z]]";
    }

    @Override
    public String getCommandName() {
        return "update [options]";
    }
}

