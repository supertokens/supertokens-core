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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.ResourceDistributor;
import io.supertokens.config.CoreConfig;
import io.supertokens.emailpassword.PasswordHashing;
import io.supertokens.pluginInterface.PluginInterfaceTesting;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.useridmapping.UserIdMapping;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.telemetry.TelemetryProvider;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.httpRequest.HttpResponseException;
import io.supertokens.useridmapping.UserIdType;
import io.supertokens.utils.SemVer;
import io.supertokens.webserver.WebserverAPI;
import org.apache.tomcat.util.http.fileupload.FileUtils;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.mockito.Mockito;

import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Random;

import static org.junit.Assert.*;

public abstract class Utils extends Mockito {

    private static ByteArrayOutputStream byteArrayOutputStream;

    public static void afterTesting() {
        TestingProcessManager.killAllIsolatedProcesses();

        String startedDir = ".started" + System.getProperty("org.gradle.test.worker", "");

        String installDir = "../";
        try {

            // remove config.yaml file
            String workerId = System.getProperty("org.gradle.test.worker", "");
            ProcessBuilder pb = new ProcessBuilder("rm", "config" + workerId + ".yaml");
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
            final File dotStartedFolder = new File(installDir + startedDir);
            try {
                FileUtils.deleteDirectory(dotStartedFolder);
            } catch (Exception ignored) {
            }

            TelemetryProvider.resetForTest();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String getCdiVersionStringLatestForTests() {
        return WebserverAPI.getLatestCDIVersion().get();
    }

    public static void reset() {
        TestingProcessManager.killAllIsolatedProcesses();

        Main.isTesting = true;
        Main.isTesting_skipBulkImportUserValidationInCronJob = false;
        PluginInterfaceTesting.isTesting = true;
        Main.makeConsolePrintSilent = true;
        HttpRequestForTesting.disableAddingAppId = false;
        String installDir = "../";
        CoreConfig.setDisableOAuthValidationForTest(false);
        ResourceDistributor.setAppForTesting(TenantIdentifier.BASE_TENANT);
        PasswordHashing.bypassHashCachingInTesting = false;

        try {

            // if the default config is not the same as the current config, we must reset the storage layer
            File ogConfig = new File("../temp/config.yaml");
            String workerId = System.getProperty("org.gradle.test.worker", "");
            File currentConfig = new File("../config" + workerId + ".yaml");
            if (currentConfig.isFile()) {
                byte[] ogConfigContent = Files.readAllBytes(ogConfig.toPath());
                byte[] currentConfigContent = Files.readAllBytes(currentConfig.toPath());
                if (!Arrays.equals(ogConfigContent, currentConfigContent)) {
                    StorageLayer.close();
                }
            }

            ProcessBuilder pb = new ProcessBuilder("cp", "temp/config.yaml", "./config" + workerId + ".yaml");
            pb.directory(new File(installDir));
            Process process = pb.start();
            process.waitFor();

            // in devConfig, it's set to false. However, in config, it's commented. So we comment it out so that it
            // mimics production. Refer to https://github.com/supertokens/supertokens-core/issues/118
            commentConfigValue("disable_telemetry");

            byteArrayOutputStream = new ByteArrayOutputStream();
            System.setErr(new PrintStream(byteArrayOutputStream));
            TelemetryProvider.resetForTest();
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.gc();
    }

    static void commentConfigValue(String key) throws IOException {
        // we close the storage layer since there might be a change in the db related config.
        StorageLayer.close();

        String oldStr = "\n((#\\s)?)" + key + "(:|((:\\s).+))\n";
        String newStr = "\n# " + key + ":";

        StringBuilder originalFileContent = new StringBuilder();
        String workerId = System.getProperty("org.gradle.test.worker", "");

        try (BufferedReader reader = new BufferedReader(new FileReader("../config" + workerId + ".yaml"))) {
            String currentReadingLine = reader.readLine();
            while (currentReadingLine != null) {
                originalFileContent.append(currentReadingLine).append(System.lineSeparator());
                currentReadingLine = reader.readLine();
            }
            String modifiedFileContent = originalFileContent.toString().replaceAll(oldStr, newStr);
            try (BufferedWriter writer = new BufferedWriter(new FileWriter("../config" + workerId + ".yaml"))) {
                writer.write(modifiedFileContent);
            }
        }

    }

    public static void setValueInConfig(String key, String value) throws IOException {
        // we close the storage layer since there might be a change in the db related config.
        StorageLayer.close();

        String oldStr = "\n((#\\s)?)" + key + "(:|((:\\s).+))\n";
        String newStr = "\n" + key + ": " + value + "\n";
        StringBuilder originalFileContent = new StringBuilder();
        String workerId = System.getProperty("org.gradle.test.worker", "");
        try (BufferedReader reader = new BufferedReader(new FileReader("../config" + workerId + ".yaml"))) {
            String currentReadingLine = reader.readLine();
            while (currentReadingLine != null) {
                originalFileContent.append(currentReadingLine).append(System.lineSeparator());
                currentReadingLine = reader.readLine();
            }
            String modifiedFileContent = originalFileContent.toString().replaceAll(oldStr, newStr);
            try (BufferedWriter writer = new BufferedWriter(new FileWriter("../config" + workerId + ".yaml"))) {
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

    public static TestRule retryFlakyTest() {
        return new TestRule() {
            private final int retryCount = 3;

            public Statement apply(Statement base, Description description) {
                return statement(base, description);
            }

            private Statement statement(final Statement base, final Description description) {
                return new Statement() {
                    @Override
                    public void evaluate() throws Throwable {
                        Throwable caughtThrowable = null;

                        // implement retry logic here
                        for (int i = 0; i < retryCount; i++) {
                            try {
                                base.evaluate();
                                return;
                            } catch (Throwable t) {
                                caughtThrowable = t;
                                System.err.println(description.getDisplayName() + ": run " + (i+1) + " failed");
                                TestingProcessManager.killAll();
                                TestServiceUtils.killServices();
                                TestServiceUtils.startServices();
                                Thread.sleep(1000 + new Random().nextInt(3000));
                            }
                        }
                        System.err.println(description.getDisplayName() + ": giving up after " + retryCount + " failures");
                        throw caughtThrowable;
                    }
                };
            }
        };
    }

    public static JsonObject signUpRequest_2_4(TestingProcessManager.TestingProcess process, String email,
                                               String password) throws IOException, HttpResponseException {

        JsonObject signUpRequestBody = new JsonObject();
        signUpRequestBody.addProperty("email", email);
        signUpRequestBody.addProperty("password", password);

        return HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/signup", signUpRequestBody, 1000, 1000, null, SemVer.v2_7.get(),
                "emailpassword");
    }

    public static JsonObject signUpRequest_2_5(TestingProcessManager.TestingProcess process, String email,
                                               String password) throws IOException, HttpResponseException {

        JsonObject signUpRequestBody = new JsonObject();
        signUpRequestBody.addProperty("email", email);
        signUpRequestBody.addProperty("password", password);

        return HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/signup", signUpRequestBody, 1000, 1000, null, SemVer.v2_7.get(),
                "emailpassword");
    }

    public static JsonObject signUpRequest_3_0(TestingProcessManager.TestingProcess process, String email,
                                               String password) throws IOException, HttpResponseException {

        JsonObject signUpRequestBody = new JsonObject();
        signUpRequestBody.addProperty("email", email);
        signUpRequestBody.addProperty("password", password);

        return HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/signup", signUpRequestBody, 1000, 1000, null, SemVer.v3_0.get(),
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
                SemVer.v2_7.get(), "thirdparty");
    }

    public static JsonObject signInUpRequest_2_8(TestingProcessManager.TestingProcess process, String email,
                                                 String thirdPartyId, String thirdPartyUserId)
            throws IOException, HttpResponseException {

        JsonObject emailObject = new JsonObject();
        emailObject.addProperty("id", email);

        JsonObject signUpRequestBody = new JsonObject();
        signUpRequestBody.addProperty("thirdPartyId", thirdPartyId);
        signUpRequestBody.addProperty("thirdPartyUserId", thirdPartyUserId);
        signUpRequestBody.add("email", emailObject);

        return HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/signinup", signUpRequestBody, 1000, 1000, null,
                SemVer.v2_8.get(), "thirdparty");
    }

    public static void checkThatArraysAreEqual(String[] arr1, String[] arr2) {
        Arrays.sort(arr1);
        Arrays.sort(arr2);
        assertArrayEquals(arr1, arr2);
    }

    public static String[] parseJsonArrayToStringArray(JsonArray arr) {
        ArrayList<String> list = new ArrayList<>();
        arr.forEach(element -> {
            list.add(element.getAsString());
        });

        return list.toArray(String[]::new);
    }

    public static void createUserIdMappingAndCheckThatItExists(Main main, UserIdMapping userIdMapping)
            throws Exception {
        io.supertokens.useridmapping.UserIdMapping.createUserIdMapping(main, userIdMapping.superTokensUserId,
                userIdMapping.externalUserId, userIdMapping.externalUserIdInfo, false);
        // retrieve mapping and validate
        UserIdMapping retrievedMapping = io.supertokens.useridmapping.UserIdMapping.getUserIdMapping(main,
                userIdMapping.superTokensUserId, UserIdType.SUPERTOKENS);
        assertEquals(userIdMapping, retrievedMapping);
    }

    public static <T> void assertArrayEqualsIgnoreOrder(T[] array1, T[] array2) {
        assertTrue(
                array1.length == array2.length && Arrays.asList(array1).containsAll(Arrays.asList(array2))
                        && Arrays.asList(array2).containsAll(Arrays.asList(array1)));
    }

    public static java.util.Map<String, String> splitQueryString(String query) throws UnsupportedEncodingException {
        java.util.Map<String, String> queryParams = new HashMap<>();
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            String key = idx > 0 ? URLDecoder.decode(pair.substring(0, idx), "UTF-8") : pair;
            String value = idx > 0 && pair.length() > idx + 1 ? URLDecoder.decode(pair.substring(idx + 1), "UTF-8") : null;
            queryParams.put(key, value);
        }
        return queryParams;
    }

    public static void testFlaky(TestFunction test) throws Exception {
        for (int i = 0; i < 5; i++) {
            try {
                test.run();
                return;
            } catch (Exception e) {
                // retry
            }
        }
    }

    @FunctionalInterface
    public interface TestFunction {
        void run() throws Exception;
    }
}
