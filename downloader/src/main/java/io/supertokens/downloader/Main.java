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

package io.supertokens.downloader;

import io.supertokens.downloader.cliParsers.InstallOptionsParser;
import io.supertokens.downloader.exception.QuitProgramException;
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

        // ------------
        String mode = "DEV";
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
        Logging.info("Fetching dependency locations...");
        String[] coreDependencyJarsLinks = HTTPRequest.getDependencyJarLinks(coreDependencyJarsGetURL);

        String[] pluginInterfaceDependencyJarsLinks = HTTPRequest
                .getDependencyJarLinks(pluginInterfaceDependencyJarsGetURL);

        String[] pluginDependencyJarsLinks = HTTPRequest.getDependencyJarLinks(pluginDependencyJarsGetURL);

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

        // we don't have any ee only dependencies which are not also dependencies
        // of the core. So we don't do anything explicitly here.
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
