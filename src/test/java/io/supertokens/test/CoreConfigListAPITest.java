/*
 *    Copyright (c) 2024, VRAI Labs and/or its affiliates. All rights reserved.
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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.config.CoreConfig;
import io.supertokens.config.annotations.ConfigDescription;
import io.supertokens.httpRequest.HttpRequest;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.utils.SemVer;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.io.FileReader;
import java.io.BufferedReader;
import java.lang.reflect.Field;

public class CoreConfigListAPITest {
    @Rule
    public TestRule watchman = Utils.getOnFailure();

    @AfterClass
    public static void afterTesting() {
        Utils.afterTesting();
    }

    @Before
    public void beforeEach() {
        Utils.reset();
    }

    @Test
    public void testRetreivingConfigProperties() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        JsonObject response = HttpRequest.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/core-config/list", null,
                1000, 1000, null);

        assertEquals(response.get("status").getAsString(), "OK");
        JsonArray result = response.get("config").getAsJsonArray();

        for (int i = 0; i < result.size(); i++) {
            JsonObject config = result.get(i).getAsJsonObject();
            assertTrue(config.get("name").getAsJsonPrimitive().isString());
            assertTrue(config.get("description").getAsJsonPrimitive().isString());
            assertTrue(config.get("isDifferentAcrossTenants").getAsJsonPrimitive().isBoolean());
            assertTrue(config.get("type").getAsJsonPrimitive().isString());

            String type = config.get("type").getAsString();
            assertTrue(type.equals("number") || type.equals("boolean") || type.equals("string") || type.equals("enum"));

            if (type.equals("enum")) {
                assertTrue(config.get("options").getAsJsonArray().size() > 0);
            }

        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testProtectedConfigsAreHandledCorrectlyIfSuperTokensSaaSSecretIsSet() throws Exception {
        String[] args = { "../" };

        String saasSecret = "hg40239oirjgBHD9450=Beew123--hg40239oirjgBHD9450=Beew123--hg40239oirjgBHD9450=Beew123-";
        Utils.setValueInConfig("supertokens_saas_secret", saasSecret);
        String apiKey = "hg40239oirjgBHD9450=Beew123--hg40239oiBeew123-";
        Utils.setValueInConfig("api_keys", apiKey);

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String[] protectedPluginFields = StorageLayer.getBaseStorage(process.main)
                        .getProtectedConfigsFromSuperTokensSaaSUsers();

        // Make sure that the protected fields are not included in the response
        // if supertokens_saas_secret is set and it is not used for api_key
        {
            JsonObject response = HttpRequestForTesting.sendJsonRequest(process.getProcess(), "",
                    "http://localhost:3567/core-config/list",
                    null, 1000, 1000, null,
                    SemVer.v3_0.get(), "GET", apiKey, "");

            assertEquals(response.get("status").getAsString(), "OK");
            JsonArray result = response.get("config").getAsJsonArray();

            for (int i = 0; i < result.size(); i++) {
                String fieldName = result.get(i).getAsJsonObject().get("name").getAsString();
                if (Arrays.asList(protectedPluginFields).contains(fieldName)
                        || Arrays.asList(CoreConfig.PROTECTED_CONFIGS).contains(fieldName)) {
                    fail("Protected config " + fieldName
                            + " is included in the response even though supertokens_saas_secret is set and api key does not match.");
                }
            }
        }

        // Make sure that the protected fields are included in the response
        // if supertokens_saas_secret is set and it is used for the api_key
        {
            JsonObject response = HttpRequestForTesting.sendJsonRequest(process.getProcess(), "",
                    "http://localhost:3567/core-config/list",
                    null, 1000, 1000, null,
                    SemVer.v3_0.get(), "GET", saasSecret, "");

            assertEquals(response.get("status").getAsString(), "OK");
            JsonArray result = response.get("config").getAsJsonArray();

            Set<String> allProtectedFields = new HashSet<String>();
            allProtectedFields.addAll(Arrays.asList(CoreConfig.PROTECTED_CONFIGS));
            allProtectedFields.addAll(Arrays.asList(protectedPluginFields));

            for (int i = 0; i < result.size(); i++) {
                String fieldName = result.get(i).getAsJsonObject().get("name").getAsString();
               // Ensure that all the protected fields are included in the response
                if (allProtectedFields.contains(fieldName)) {
                    allProtectedFields.remove(fieldName);
                }
            }

            if (allProtectedFields.size() > 0) {
                fail("Protected configs " + allProtectedFields.toString() + " are not included in the response even though supertokens_saas_secret is added in the request.");
            }
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testMatchConfigPropertiesDescription() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // access_token_signing_key_update_interval is an alias for
        // access_token_dynamic_signing_key_update_interval,
        // we don't have a description for core_config_version
        // and webserver_https_enabled is not present in the config.yaml file
        // so we skip these properties.
        String[] ignoredProperties = { "access_token_signing_key_update_interval", "core_config_version",
                "webserver_https_enabled" };

        // Match the descriptions in the config.yaml file with the descriptions in the
        // CoreConfig class
        matchYamlAndConfigDescriptions("./config.yaml", ignoredProperties);

        // Match the descriptions in the devConfig.yaml file with the descriptions in
        // the CoreConfig class
        String[] devConfigIgnoredProperties = Arrays.copyOf(ignoredProperties, ignoredProperties.length + 1);
        // We ignore this property in devConfig.yaml because it has a different
        // description
        // in devConfig.yaml and has a default value
        devConfigIgnoredProperties[ignoredProperties.length] = "disable_telemetry";
        matchYamlAndConfigDescriptions("./devConfig.yaml", devConfigIgnoredProperties);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    private void matchYamlAndConfigDescriptions(String path, String[] ignoreProperties) throws Exception {
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            // Get the content of the file as string
            String content = reader.lines().collect(Collectors.joining(System.lineSeparator()));
            // Find the line that contains 'core_config_version', and then split
            // the file after that line
            String allProperties = content.split("core_config_version:\\s*\\d+\n\n")[1];

            // Split by all the other allProperties string by new line
            String[] properties = allProperties.split("\n\n");
            // This will contain the description of each property from the yaml file
            Map<String, String> propertyDescriptions = new HashMap<String, String>();

            for (int i = 0; i < properties.length; i++) {
                String possibleProperty = properties[i].trim();
                String[] lines = possibleProperty.split("\n");
                // This ensures that it is a property with a description as a comment
                // at the top
                if (lines[lines.length - 1].endsWith(":")) {
                    String propertyKeyString = lines[lines.length - 1];
                    // Remove the comment "# " from the start
                    String propertyKey = propertyKeyString.substring(2, propertyKeyString.length() - 1);
                    String propertyDescription = "";
                    // Remove the comment "# " from the start and merge all the lines to form the
                    // description
                    for (int j = 0; j < lines.length - 1; j++) {
                        propertyDescription = propertyDescription + " " + lines[j].substring(2);
                    }
                    propertyDescription = propertyDescription.trim();

                    propertyDescriptions.put(propertyKey, propertyDescription);
                }
            }

            for (String fieldId : CoreConfig.getValidFields()) {
                if (Arrays.asList(ignoreProperties).contains(fieldId)) {
                    continue;
                }

                Field field = CoreConfig.class.getDeclaredField(fieldId);

                // Skip fields that are not annotated with JsonProperty
                if (!field.isAnnotationPresent(JsonProperty.class)) {
                    continue;
                }

                String descriptionInConfig = field.getAnnotation(ConfigDescription.class).value();
                String descriptionInYaml = propertyDescriptions.get(fieldId);

                if (descriptionInYaml == null) {
                    fail("Unable to find description or property for " + fieldId + " in " + path + " file");
                }

                // Remove the default value from config, since we add default value at the end
                // config description
                descriptionInConfig = descriptionInConfig.replaceAll("\\s\\[Default:.*|\\s\\(Default:.*", "").trim();
                // Remove period from end if present, since not all descriptions in
                // config.yaml have that
                descriptionInConfig = descriptionInConfig.replaceAll("\\.$", "").trim();

                // Assert that description in yaml contains the description in config
                if (!descriptionInYaml.contains(descriptionInConfig)) {
                    fail("Description in config class for " + fieldId + " does not match description in " + path
                            + " file");
                }
            }
        }
    }

}
