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

package io.supertokens.test;

import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.pluginInterface.PluginInterfaceTesting;
import io.supertokens.test.httpRequest.HttpResponseException;
import io.supertokens.webserver.WebserverAPI;
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

            // remove config.yaml file
            ProcessBuilder pb = new ProcessBuilder("rm", "config.yaml");
            pb.directory(new File(installDir));
            Process process = pb.start();
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

    static String getCdiVersion2_4ForTests() {
        return "2.4";
    }

    static String getCdiVersion2_5ForTests() {
        return "2.5";
    }

    static String getCdiVersionLatestForTests() {
        return WebserverAPI.getLatestCDIVersion();
    }

    public static void reset() {
        Main.isTesting = true;
        PluginInterfaceTesting.isTesting = true;
        Main.makeConsolePrintSilent = true;
        String installDir = "../";
        try {

            ProcessBuilder pb = new ProcessBuilder("cp", "temp/config.yaml", "./config.yaml");
            pb.directory(new File(installDir));
            Process process = pb.start();
            process.waitFor();

            // in devConfig, it's set to false. However, in config, it's commented. So we comment it out so that it
            // mimics production. Refer to https://github.com/supertokens/supertokens-core/issues/118
            commentConfigValue("disable_telemetry");

            TestingProcessManager.killAll();
            TestingProcessManager.deleteAllInformation();
            TestingProcessManager.killAll();

            byteArrayOutputStream = new ByteArrayOutputStream();
            System.setErr(new PrintStream(byteArrayOutputStream));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static void commentConfigValue(String key) throws IOException {
        String oldStr = "((#\\s)?)" + key + "(:|((:\\s).+))\n";
        String newStr = "# " + key + ":";

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

    public static JsonObject signUpRequest(TestingProcessManager.TestingProcess process, String email, String password)
            throws IOException, HttpResponseException {

        JsonObject signUpRequestBody = new JsonObject();
        signUpRequestBody.addProperty("email", email);
        signUpRequestBody.addProperty("password", password);

        return io.supertokens.test.httpRequest.HttpRequest
                .sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/signup", signUpRequestBody, 1000,
                        1000,
                        null, getCdiVersion2_4ForTests());
    }

}
