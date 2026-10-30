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

package io.supertokens.test.multitenant.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.config.CoreConfig;
import io.supertokens.config.annotations.ConfigDescription;
import io.supertokens.httpRequest.HttpRequest;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.DatabaseTestHelper;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;

import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.webserver.api.dashboard.GetTenantCoreConfigForDashboardAPI;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.io.FileReader;
import java.io.BufferedReader;
import java.lang.reflect.Field;

public class GetTenantCoreConfigForDashboardAPITest {
    @Rule
    public TestRule watchman = Utils.getOnFailure();

    @Rule
    public TestRule retryFlaky = Utils.retryFlakyTest();

    @AfterClass
    public static void afterTesting() {
        Utils.afterTesting();
    }

    @Before
    public void beforeEach() {
        Utils.reset();
    }

    @Test
    public void testRetrievingConfigProperties() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        int port = HttpRequestForTesting.corePort;

        JsonObject response = HttpRequest.sendGETRequest(process.getProcess(), "",
                "http://localhost:" + port + "/recipe/dashboard/tenant/core-config", null,
                1000, 1000, null);

        assertEquals(response.get("status").getAsString(), "OK");
        JsonArray result = response.get("config").getAsJsonArray();

        try {
            String[] fields = new String[]{"key", "valueType", "value", "description", "isDifferentAcrossTenants",
                    "possibleValues", "isNullable", "defaultValue", "isPluginProperty", "isPluginPropertyEditable"};

            for (int i = 0; i < result.size(); i++) {
                JsonObject config = result.get(i).getAsJsonObject();
                assertEquals(fields.length, config.entrySet().size());
                for (String field : fields) {
                    assertTrue(config.has(field));
                }

                // check for some known fields
                switch (config.get("key").getAsString()) {
                    case "port":
                        fail(); // config yaml only properties should not be returned
                        break;
                    case "access_token_validity":
                        assertEquals("number", config.get("valueType").getAsString());
                        assertEquals(3600, config.get("defaultValue").getAsInt());
                        assertFalse(config.get("isDifferentAcrossTenants").getAsBoolean());
                        break;
                    case "password_hashing_alg":
                        assertTrue(config.get("possibleValues").isJsonArray());
                        assertEquals(2, config.get("possibleValues").getAsJsonArray().size());
                        break;
                }
            }
        } catch (NullPointerException e) {
            throw e;
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testIssue15_dashboardCoreConfigMasksSecrets() throws Exception {
        String[] args = {"../"};

        String signerKey = "dashboardSignerKeyValue1234567890";
        Utils.setValueInConfig("firebase_password_hashing_signer_key", "\"" + signerKey + "\"");

        String dbUser = DatabaseTestHelper.getUser();
        String dbPassword = DatabaseTestHelper.getPassword();
        String dbHost = DatabaseTestHelper.getHost() == null ? "localhost" : DatabaseTestHelper.getHost();
        String dbPort = DatabaseTestHelper.getPort() == null ? "5432" : DatabaseTestHelper.getPort();
        // no path, so the per-test database name set by Utils.reset is kept
        String connectionUriPrefix = "postgresql://" + dbUser + ":";
        String connectionUriSuffix = "@" + dbHost + ":" + dbPort;
        // only has an effect when the postgresql plugin is loaded (its config is in config.yaml)
        Utils.setValueInConfig("postgresql_connection_uri",
                "\"" + connectionUriPrefix + dbPassword + connectionUriSuffix + "\"");

        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // no supertokens_saas_secret is configured, so nothing is filtered as "protected"
        JsonObject response = HttpRequest.sendGETRequest(process.getProcess(), "",
                "http://localhost:" + HttpRequestForTesting.corePort + "/recipe/dashboard/tenant/core-config", null,
                1000, 1000, null);
        assertEquals("OK", response.get("status").getAsString());

        String responseString = response.toString();
        assertFalse(responseString.contains(signerKey));

        Map<String, JsonObject> fieldsByKey = new HashMap<>();
        for (int i = 0; i < response.get("config").getAsJsonArray().size(); i++) {
            JsonObject field = response.get("config").getAsJsonArray().get(i).getAsJsonObject();
            fieldsByKey.put(field.get("key").getAsString(), field);
        }

        assertFalse(fieldsByKey.containsKey("firebase_password_hashing_signer_key"));
        assertFalse(fieldsByKey.containsKey("api_keys"));
        // non-secret config is still returned
        assertTrue(fieldsByKey.containsKey("access_token_validity"));

        boolean isPostgres = StorageLayer.getStorage(process.getProcess()).getClass().getName()
                .startsWith("io.supertokens.storage.postgresql");
        if (isPostgres) {
            assertEquals(GetTenantCoreConfigForDashboardAPI.MASKED_VALUE,
                    fieldsByKey.get("postgresql_password").get("value").getAsString());
            assertEquals(connectionUriPrefix + GetTenantCoreConfigForDashboardAPI.MASKED_VALUE + connectionUriSuffix,
                    fieldsByKey.get("postgresql_connection_uri").get("value").getAsString());
            // non-secret database properties are still shown
            assertEquals(dbUser, fieldsByKey.get("postgresql_user").get("value").getAsString());
            assertFalse(responseString.contains(":" + dbPassword + "@"));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testDashboardCoreConfigMaskingOfConnectionStrings() {
        String m = GetTenantCoreConfigForDashboardAPI.MASKED_VALUE;

        assertEquals("postgresql://user:" + m + "@host:5432/db",
                GetTenantCoreConfigForDashboardAPI.maskCredentialsInString("postgresql://user:p4ss@host:5432/db"));
        // a password containing '@' is fully masked
        assertEquals("mysql://user:" + m + "@host/db",
                GetTenantCoreConfigForDashboardAPI.maskCredentialsInString("mysql://user:p@ss@host/db"));
        // credentials passed as query parameters / connection attributes
        assertEquals("postgresql://host/db?sslmode=require&password=" + m + "&sslpassword=" + m,
                GetTenantCoreConfigForDashboardAPI.maskCredentialsInString(
                        "postgresql://host/db?sslmode=require&password=abc&sslpassword=def"));
        assertEquals("password=" + m + "&allowPublicKeyRetrieval=true",
                GetTenantCoreConfigForDashboardAPI.maskCredentialsInString(
                        "password=abc&allowPublicKeyRetrieval=true"));
        // a secret parameter value is masked up to the next '&', even if it contains ';' or whitespace
        assertEquals("postgresql://host/db?password=" + m + "&sslmode=require",
                GetTenantCoreConfigForDashboardAPI.maskCredentialsInString(
                        "postgresql://host/db?password=Ab3;xYz9Qw&sslmode=require"));
        assertEquals("password=" + m + "&allowPublicKeyRetrieval=true",
                GetTenantCoreConfigForDashboardAPI.maskCredentialsInString(
                        "password=Ab3 xYz9&allowPublicKeyRetrieval=true"));
        assertEquals("sslmode=require;password=" + m,
                GetTenantCoreConfigForDashboardAPI.maskCredentialsInString("sslmode=require;password=a b;c"));
        // user info is only looked for in the authority: an '@' in the query does not mask host/port/db
        assertEquals("postgresql://srv.example.com:5432/postgres?user=admin@srv&password=" + m + "&sslmode=require",
                GetTenantCoreConfigForDashboardAPI.maskCredentialsInString(
                        "postgresql://srv.example.com:5432/postgres?user=admin@srv&password=pw&sslmode=require"));
        assertEquals("postgresql://user:" + m + "@host:5432/db?application_name=svc@prod",
                GetTenantCoreConfigForDashboardAPI.maskCredentialsInString(
                        "postgresql://user:pass@host:5432/db?application_name=svc@prod"));
        assertEquals("postgresql://host:5432/db#a:b@c",
                GetTenantCoreConfigForDashboardAPI.maskCredentialsInString("postgresql://host:5432/db#a:b@c"));
        // a password containing '/', '?' or '#' is masked too (the user info then ends at the last '@')
        assertEquals("postgresql://u:" + m + "@h/db",
                GetTenantCoreConfigForDashboardAPI.maskCredentialsInString("postgresql://u:pa/ss@h/db"));
        assertEquals("postgresql://u:" + m + "@h:5432/db?sslmode=require",
                GetTenantCoreConfigForDashboardAPI.maskCredentialsInString(
                        "postgresql://u:p?a#s/s@h:5432/db?sslmode=require"));
        // user info without a colon (e.g. a token) is masked as a whole
        assertEquals("https://" + m + "@collector:4317",
                GetTenantCoreConfigForDashboardAPI.maskCredentialsInString("https://TOKEN@collector:4317"));
        assertEquals("postgresql://" + m + "@host:5432/db",
                GetTenantCoreConfigForDashboardAPI.maskCredentialsInString("postgresql://user@host:5432/db"));
        // very long values are not inspected but fully masked
        assertEquals(m, GetTenantCoreConfigForDashboardAPI.maskCredentialsInString(
                "password".repeat(GetTenantCoreConfigForDashboardAPI.MAX_INSPECTED_VALUE_LENGTH)));
        // inspection is a linear pass: a value at the size limit made of repeated keywords without any '='
        // (the worst case for a backtracking regex) is handled quickly and left unchanged
        String keywords = "passwordtoken".repeat(GetTenantCoreConfigForDashboardAPI.MAX_INSPECTED_VALUE_LENGTH / 13);
        long start = System.nanoTime();
        for (int i = 0; i < 100; i++) {
            assertEquals(keywords, GetTenantCoreConfigForDashboardAPI.maskCredentialsInString(keywords));
        }
        assertTrue(System.nanoTime() - start < 5_000_000_000L);
        // values without credentials are unchanged
        for (String unchanged : new String[]{"postgresql://host:5432/db", "postgresql://[::1]:5432/db?x=a@b",
                "postgresql://host:/db?x=a@b",
                "allowPublicKeyRetrieval=true", "localhost", "BCRYPT", ""}) {
            assertEquals(unchanged, GetTenantCoreConfigForDashboardAPI.maskCredentialsInString(unchanged));
        }

        for (String secretKey : new String[]{"postgresql_password", "mysql_password", "api_keys",
                "firebase_password_hashing_signer_key", "supertokens_saas_secret",
                "oauth_client_secret_encryption_key"}) {
            assertTrue(secretKey, GetTenantCoreConfigForDashboardAPI.isSecretConfigKey(secretKey));
        }
        for (String nonSecretKey : new String[]{"postgresql_user", "postgresql_host", "postgresql_connection_uri",
                "password_hashing_alg", "password_reset_token_lifetime", "passwordless_code_lifetime",
                "access_token_validity", "postgresql_key_value_table_name"}) {
            assertFalse(nonSecretKey, GetTenantCoreConfigForDashboardAPI.isSecretConfigKey(nonSecretKey));
        }
    }

    @Test
    public void testNoVisibleCoreConfigIsTreatedAsSecretByName() throws Exception {
        // Core secrets are hidden with @HideFromDashboard; the name based masking must not hit any
        // config that is meant to be shown.
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        for (io.supertokens.pluginInterface.ConfigFieldInfo field : CoreConfig.getConfigFieldsInfoForDashboard(
                process.getProcess(), io.supertokens.pluginInterface.multitenancy.TenantIdentifier.BASE_TENANT)) {
            assertFalse(field.key, GetTenantCoreConfigForDashboardAPI.isSecretConfigKey(field.key));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testMatchConfigPropertiesDescription() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // access_token_signing_key_update_interval is an alias for
        // access_token_dynamic_signing_key_update_interval,
        // we don't have a description for core_config_version
        // and webserver_https_enabled is not present in the config.yaml file
        // so we skip these properties.
        String[] ignoredProperties = {"access_token_signing_key_update_interval", "core_config_version",
                "webserver_https_enabled"};

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
                } else if (lines[lines.length - 1].contains(": ") && (
                                !lines[lines.length - 1].contains("Default:") &&
                                !lines[lines.length - 1].contains("Important:")
                        )) {
                    String propertyKeyString = lines[lines.length - 1];
                    String propertyKey = propertyKeyString.split(":")[0].trim();
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
