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
import io.supertokens.cli.exception.QuitProgramException;
import io.supertokens.cli.logging.Logging;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

class UpdateUtils {
    static String getUrlForDownload(String appId, String currentCoreVersion, String currentPluginVersion,
                                    OperatingSystem.OS osEnum, String licenseKeyId, String targetCore,
                                    String targetPlugin) {
        String os;
        if (osEnum.equals(OperatingSystem.OS.LINUX)) {
            os = "linux";
        } else if (osEnum.equals(OperatingSystem.OS.WINDOWS)) {
            os = "windows";
        } else {
            os = "mac";
        }

        String urlForDownload = Utils.SERVER_URL + "/app/" + appId + "/update" + "/download?currentCore=" +
                currentCoreVersion + "&currentPlugin=" +
                currentPluginVersion + "&os=" + os + "&currLicenseKeyId=" +
                licenseKeyId;
        if (targetCore == null && targetPlugin == null) {
            return urlForDownload;
        }
        if (targetCore != null && targetPlugin == null) {
            return urlForDownload + "&targetCore=" + targetCore;
        }
        if (targetCore == null) {
            return urlForDownload + "&targetPlugin=" + targetPlugin;
        }
        return urlForDownload + "&targetCore=" + targetCore + "&targetPlugin=" + targetPlugin;
    }

    static String downloadFile(String urlStr, String targetFile, String apiVersion) throws IOException {
        DownloaderRunnable downloader = new DownloaderRunnable(urlStr, targetFile, apiVersion);
        Thread downloaderThread = new Thread(downloader);
        downloaderThread.start();

        while (!downloader.hasFinished) {
            Logging.info("Checking and downloading update...");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignored) {
            }
            if (!Utils.internetIsAvailable()) {
                throw new QuitProgramException("Failed to update. Please check your internet connection and try again",
                        null);
            }
        }

        try {
            downloaderThread.join();
        } catch (InterruptedException ignored) {
        }

        if (downloader.ioError != null) {
            throw (IOException) downloader.ioError;
        } else if (downloader.runtimeException != null) {
            throw (RuntimeException) downloader.runtimeException;
        }
        return downloader.output;
    }

    public static boolean containsSources(File file) {
        File[] listOfFiles = file.listFiles();
        if (listOfFiles == null) {
            return false;
        }
        for (File coreFile : listOfFiles) {
            if (coreFile.getName().contains("-sources.jar")) {
                return true;
            }
        }
        return false;
    }

    //function is used to read contents of a file into a string
    public static String readFileToString(File file) throws IOException {
        String content = null;
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String currentLine = br.readLine();
            while (currentLine != null) {
                content += currentLine + "\n";
                currentLine = br.readLine();
            }

        }
        return content;
    }

    //function is used to parse the plan type from the downloaded licenseKey file
    public static String planTypeParser(File licenseKey) throws IOException {
        String licenseKeyContent = readFileToString(licenseKey);
        for (String licenseKeyElement : licenseKeyContent.split(",")) {
            if (licenseKeyElement.contains("planType")) {
                return licenseKeyElement.split(":")[1].trim();
            }
        }
        return null;
    }

    // function is used to parse the coreVersion from the downloaded version file
    public static String coreVersionParser(File version) throws IOException {
        String versionContent = readFileToString(version);
        for (String versionContentElement : versionContent.split("\n")) {
            if (versionContentElement.contains("core_version")) {
                return versionContentElement.split(":")[1].trim();
            }
        }
        return null;
    }

    static void throwRootPermissionNeededError() {
        if (OperatingSystem.getOS().equals(OperatingSystem.OS.WINDOWS)) {
            throw new QuitProgramException(
                    "Failed to update. Please try using the command again as an Administrator", null);
        } else {
            throw new QuitProgramException(
                    "Failed to update. Please try using the command again with sudo", null);
        }
    }

    static void throwSomethingWentWrongError(Exception e) {
        throw new QuitProgramException(
                "Something went wrong, please try again later, or contact us at team@supertokens.io if the " +
                        "problem persists", e);
    }

}
