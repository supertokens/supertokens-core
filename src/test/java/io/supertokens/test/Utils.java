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
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.httpRequest.HttpResponseException;
import io.supertokens.webserver.WebserverAPI;
import org.apache.tomcat.util.http.fileupload.FileUtils;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.mockito.Mockito;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Arrays;
import java.util.regex.Pattern;

public abstract class Utils extends Mockito {

    private static ByteArrayOutputStream byteArrayOutputStream;
    private static final String newLine = System.lineSeparator();

    public static void afterTesting() {
        String installDir = "../";
        try {

            // remove config.yaml file
            Files.delete(new File(installDir + "config.yaml").toPath()); // Use behavior of rm command

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

    public static String getCdiVersion2_7ForTests() {
        return "2.7";
    }

    public static String getCdiVersion2_8ForTests() {
        return "2.8";
    }

    public static String getCdiVersion2_9ForTests() {
        return "2.9";
    }

    public static String getCdiVersion2_10ForTests() {
        return "2.10";
    }

    public static String getCdiVersion2_11ForTests() {
        return "2.11";
    }

    public static String getCdiVersion2_12ForTests() {
        return "2.12";
    }

    public static String getCdiVersion2_13ForTests() {
        return "2.13";
    }

    public static String getCdiVersionLatestForTests() {
        return WebserverAPI.getLatestCDIVersion();
    }

    public static void reset() {
        Main.isTesting = true;
        PluginInterfaceTesting.isTesting = true;
        Main.makeConsolePrintSilent = true;
        String installDir = "../";
        try {
            // if the default config is not the same as the current config, we must reset the storage layer
            Path ogConfig = new File(installDir + "temp/config.yaml").toPath();
            Path currentConfig = new File(installDir + "config.yaml").toPath();
            if (currentConfig.toFile().isFile()) {
                byte[] ogConfigContent = Files.readAllBytes(ogConfig);
                byte[] currentConfigContent = Files.readAllBytes(currentConfig);
                if (!Arrays.equals(ogConfigContent, currentConfigContent)) {
                    StorageLayer.close();
                }
            }

            Files.copy(ogConfig, currentConfig, StandardCopyOption.REPLACE_EXISTING); // Use behavior of cp command

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
        System.gc();
    }

    private static void replaceConfigValue(String regex, String newStr) throws IOException {
        // we close the storage layer since there might be a change in the db related config.
        StorageLayer.close();
        Path configPath = new File("../config.yaml").toPath();
        String originalFileContent = Files.readString(configPath);
        String modifiedFileContent = originalFileContent.replaceAll(regex, newStr);
        String normalizedFileContent = modifiedFileContent.replaceAll("\r?\n", newLine); // Normalize line endings
        Files.writeString(configPath, normalizedFileContent);
    }

    public static void commentConfigValue(String key) throws IOException {
        String find = "\r?\n#?\\s*" + Pattern.quote(key) + ":.*\r?\n";
        String replace = newLine + "# " + key + ":" + newLine;
        replaceConfigValue(find, replace);
    }

    public static void setValueInConfig(String key, String value) throws IOException {
        String find = "\r?\n#?\\s*" + Pattern.quote(key) + ":.*\r?\n";
        String replace = newLine + "# " + key + ": " + value + newLine;
        replaceConfigValue(find, replace);
    }

    public static TestRule getOnFailure() {
        return new TestWatcher() {
            @Override
            protected void failed(Throwable e, Description description) {
                System.out.println(byteArrayOutputStream.toString(StandardCharsets.UTF_8));
            }
        };
    }

    public static JsonObject signUpRequest_2_4(TestingProcessManager.TestingProcess process, String email,
            String password) throws IOException, HttpResponseException {

        JsonObject signUpRequestBody = new JsonObject();
        signUpRequestBody.addProperty("email", email);
        signUpRequestBody.addProperty("password", password);

        return HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/signup", signUpRequestBody, 1000, 1000, null, getCdiVersion2_7ForTests(),
                "emailpassword");
    }

    public static JsonObject signUpRequest_2_5(TestingProcessManager.TestingProcess process, String email,
            String password) throws IOException, HttpResponseException {

        JsonObject signUpRequestBody = new JsonObject();
        signUpRequestBody.addProperty("email", email);
        signUpRequestBody.addProperty("password", password);

        return HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/signup", signUpRequestBody, 1000, 1000, null, getCdiVersion2_7ForTests(),
                "emailpassword");
    }

    public static JsonObject signInUpRequest_2_7(TestingProcessManager.TestingProcess process, String email,
            boolean isVerified, String thirdPartyId, String thirdPartyUserId)
            throws IOException, HttpResponseException {

        JsonObject emailObject = new JsonObject();
        emailObject.addProperty("id", email);
        emailObject.addProperty("isVerified", isVerified);

        JsonObject signUpRequestBody = new JsonObject();
        signUpRequestBody.addProperty("thirdPartyId", thirdPartyId);
        signUpRequestBody.addProperty("thirdPartyUserId", thirdPartyUserId);
        signUpRequestBody.add("email", emailObject);

        return HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/signinup", signUpRequestBody, 1000, 1000, null,
                getCdiVersion2_7ForTests(), "thirdparty");
    }

    public static JsonObject signInUpRequest_2_8(TestingProcessManager.TestingProcess process, String email,
            String thirdPartyId, String thirdPartyUserId) throws IOException, HttpResponseException {

        JsonObject emailObject = new JsonObject();
        emailObject.addProperty("id", email);

        JsonObject signUpRequestBody = new JsonObject();
        signUpRequestBody.addProperty("thirdPartyId", thirdPartyId);
        signUpRequestBody.addProperty("thirdPartyUserId", thirdPartyUserId);
        signUpRequestBody.add("email", emailObject);

        return HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/signinup", signUpRequestBody, 1000, 1000, null,
                getCdiVersion2_8ForTests(), "thirdparty");
    }
}
