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

package io.supertokens.test;

import io.supertokens.Main;
import io.supertokens.pluginInterface.PluginInterfaceTesting;
import org.apache.tomcat.util.http.fileupload.FileUtils;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.mockito.Mockito;

import java.io.*;
import java.nio.charset.StandardCharsets;

abstract class Utils extends Mockito {


    private static ByteArrayOutputStream byteArrayOutputStream;

    public static void afterTesting() {
        String installDir = "../";
        try {
            // we remove the license key file
            ProcessBuilder pb = new ProcessBuilder("rm", "licenseKey");
            pb.directory(new File(installDir));
            Process process = pb.start();
            process.waitFor();

            // remove config.yaml file
            pb = new ProcessBuilder("rm", "config.yaml");
            pb.directory(new File(installDir));
            process = pb.start();
            process.waitFor();

            // remove webserver-temp folders created by tomcat
            final File webserverTemp = new File(installDir + "webserver-temp");
            try {
                FileUtils.deleteDirectory(webserverTemp);
            } catch (Exception ignored) {
            }

            // remove .started folders created by processes
            final File dotStartedFolder = new File(installDir + ".started");
            try {
                FileUtils.deleteDirectory(dotStartedFolder);
            } catch (Exception ignored) {
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static String getCdiVersion2ForTests() {
        return "2.1";
    }

    public static void reset() {
        Main.isTesting = true;
        PluginInterfaceTesting.isTesting = true;
        Main.makeConsolePrintSilent = true;
        String installDir = "../";
        try {
            // move from temp folder to installDir
            ProcessBuilder pb = new ProcessBuilder("cp", "temp/licenseKey", "./licenseKey");
            pb.directory(new File(installDir));
            Process process = pb.start();
            process.waitFor();

            pb = new ProcessBuilder("cp", "temp/config.yaml", "./config.yaml");
            pb.directory(new File(installDir));
            process = pb.start();
            process.waitFor();

            TestingProcessManager.killAll();
            TestingProcessManager.deleteAllInformation();
            TestingProcessManager.killAll();

            byteArrayOutputStream = new ByteArrayOutputStream();
            System.setErr(new PrintStream(byteArrayOutputStream));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static void setValueInConfig(String key, String value) throws IOException {
        String oldStr = "((#\\s)?)" + key + "(:|((:\\s).+))\n";
        String newStr = key + ": " + value + "\n";
        StringBuilder originalFileContent = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader("../config.yaml"))) {
            String currentReadingLine = reader.readLine();
            while (currentReadingLine != null) {
                originalFileContent.append(currentReadingLine).append(System.lineSeparator());
                currentReadingLine = reader.readLine();
            }
            String modifiedFileContent = originalFileContent.toString().replaceAll(oldStr, newStr);
            try (BufferedWriter writer = new BufferedWriter(new FileWriter("../config.yaml"))) {
                writer.write(modifiedFileContent);
            }
        }
    }

    public static TestRule getOnFailure() {
        return new TestWatcher() {
            @Override
            protected void failed(Throwable e, Description description) {
                System.out.println(byteArrayOutputStream.toString(StandardCharsets.UTF_8));
            }
        };
    }

}
