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

package io.supertokens.downloader;

import io.supertokens.downloader.cliParsers.InstallOptionsParser;
import io.supertokens.downloader.exception.QuitProgramException;
import io.supertokens.downloader.fileParsers.LicenseKeyParser;
import io.supertokens.downloader.fileParsers.VersionFileParser;
import io.supertokens.downloader.httpRequest.HTTPRequest;
import io.supertokens.downloader.httpRequest.HTTPResponseException;
import io.supertokens.downloader.logging.Logging;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;

public class Main {

    public static boolean makeConsolePrintSilent = false;

    public static boolean isTesting = false;

    public static void main(String[] args) throws IOException, HTTPResponseException {
        try {
            start(args);
        } catch (Exception e) {
            if (isTesting) {
                throw e;
            }
            Logging.info("Quitting installation because of an error");
            if (e instanceof QuitProgramException) {
                Logging.error(((QuitProgramException) e).exception);
                Logging.error("What caused the crash: " + e.getMessage());
            } else {
                Logging.error(e);
            }
            if (!Main.isTesting) {
                System.exit(1);
            }
        }
        System.exit(0);
    }

    private static void start(String[] args) throws IOException, HTTPResponseException {
        InstallOptionsParser installOptionsParser = new InstallOptionsParser(args);
        VersionFileParser versionFileParser = new VersionFileParser();
        LicenseKeyParser licenseKeyParser = new LicenseKeyParser();
        printWelcomeMessage();

        // ------------
        String mode = licenseKeyParser.getMode();
        String planType = "FREE";
        Boolean withSource = installOptionsParser.installWithSource();

        String coreDependencyJarsGetURL = HTTPRequest
                .getDependencyJarLinksURLForCore(versionFileParser.getCoreVersion(), planType, withSource, mode);
        String pluginInterfaceDependencyJarsGetURL = HTTPRequest
                .getDependencyJarLinksURLForPluginInterface(versionFileParser.getPluginInterfaceVersion(), planType,
                        withSource, mode);
        String pluginDependencyJarsGetURL = HTTPRequest
                .getDependencyJarLinksURLForPlugin(versionFileParser.getPluginVersion(), planType, withSource, mode,
                        versionFileParser.getPluginName());
        String cliDependencyJarsGetURL = HTTPRequest
                .getDependencyJarLinksURLForCLI(versionFileParser.getCoreVersion(), planType, withSource, mode);

        // ------------
        Logging.info("Fetching JAR locations for core for mode: " + mode);
        String[] coreDependencyJarsLinks = HTTPRequest.getDependencyJarLinks(coreDependencyJarsGetURL);

        Logging.info("Fetching JAR locations for plugin-interface for mode: " + mode);
        String[] pluginInterfaceDependencyJarsLinks = HTTPRequest
                .getDependencyJarLinks(pluginInterfaceDependencyJarsGetURL);

        Logging.info("Fetching JAR locations for plugin for mode: " + mode);
        String[] pluginDependencyJarsLinks = HTTPRequest.getDependencyJarLinks(pluginDependencyJarsGetURL);

        Logging.info("Fetching JAR locations for cli for mode: " + mode);
        String[] cliDependencyJarsLinks = HTTPRequest.getDependencyJarLinks(cliDependencyJarsGetURL);

        // ------------
        int total = coreDependencyJarsLinks.length + pluginInterfaceDependencyJarsLinks.length +
                pluginDependencyJarsLinks.length
                + cliDependencyJarsLinks.length;
        int current = 0;
        for (String link : coreDependencyJarsLinks) {
            current++;
            downloadFile(link, "core", current, total);
        }

        for (String link : pluginDependencyJarsLinks) {
            current++;
            downloadFile(link, "plugin", current, total);
        }

        for (String link : pluginInterfaceDependencyJarsLinks) {
            current++;
            downloadFile(link, "plugin-interface", current, total);
        }

        for (String link : cliDependencyJarsLinks) {
            current++;
            downloadFile(link, "cli", current, total);
        }
    }

    private static void printWelcomeMessage() throws IOException, HTTPResponseException {
        String info = HTTPRequest.getWelcomeMessage();
        if (info != null) {
            Logging.info(info);
        }
    }

    private static void downloadFile(String urlStr, String parentDir, int currentCount, int total) throws IOException {
        Logging.info("Downloading (" + currentCount + "/" + total + "): " + urlStr + " into " + parentDir);
        try {
            DownloaderRunnable downloader = new DownloaderRunnable(urlStr, parentDir);
            Thread downloaderThread = new Thread(downloader);
            downloaderThread.start();

            while (!downloader.hasFinished) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ignored) {
                }
                if (!internetIsAvailable()) {
                    throw new IOException("No internet connection");
                }
            }
            try {
                downloaderThread.join();
            } catch (InterruptedException ignored) {
            }

            if (downloader.ioError != null) {
                throw downloader.ioError;
            } else if (downloader.runtimeException != null) {
                throw downloader.runtimeException;
            }
        } catch (Exception e) {
            if (isTesting) {
                throw e;
            } else {
                throw new QuitProgramException(
                        "Error while downloading jar dependencies. Is your internet connection working?", e);
            }
        }
    }

    private static boolean internetIsAvailable() {
        try {
            final URL url = new URL("http://www.google.com");   // no https because we get a SSLHandshake error
            final URLConnection conn = url.openConnection();
            conn.connect();
            conn.getInputStream().close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
