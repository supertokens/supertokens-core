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

import com.google.gson.*;
import io.supertokens.ProcessState;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.multitenancy.MultitenancyHelper;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.multitenancy.*;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.utils.SemVer;
import io.supertokens.webserver.WebserverAPI;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

public class ApiVersionAPITest {

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

    // * - add a test to read the value of coreDriverInterfaceSupported.json and make sure the versions listed there are
    // * being returned by this API.
    @Test
    public void testThatCoreDriverInterfaceSupportedVersionsAreBeingReturnedByTheAPI() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        StringBuilder fileContent = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new FileReader("../supertokens-core/coreDriverInterfaceSupported.json"))) {
            String currentLine = reader.readLine();
            while (currentLine != null) {
                fileContent.append(currentLine).append(System.lineSeparator());
                currentLine = reader.readLine();
            }
        }
        JsonObject cdiSupported = new JsonParser().parse(fileContent.toString()).getAsJsonObject();
        JsonArray cdiVersions = cdiSupported.get("versions").getAsJsonArray();

        JsonObject apiVersionResponse = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/apiversion", null, 1000, 1000, null, null, "");
        JsonArray apiVersions = apiVersionResponse.get("versions").getAsJsonArray();

        for (int i = 0; i < apiVersions.size(); i++) {
            assertTrue(cdiVersions.contains(apiVersions.get(i)));
        }
        assertEquals(cdiVersions.size(), apiVersions.size());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // - no version needed for this API.
    @Test
    public void testThatNoVersionIsNeededForThisAPI() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // without setting cdi-version header
        JsonObject apiVersionResponse = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/apiversion", null, 1000, 1000, null, null, "");
        assertNotNull(apiVersionResponse.getAsJsonArray("versions"));
        assertTrue(apiVersionResponse.getAsJsonArray("versions").size() >= 1);

        // with setting cdi-version header
        apiVersionResponse = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/apiversion", null, 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(),
                "");
        assertNotNull(apiVersionResponse.getAsJsonArray("versions"));
        assertTrue(apiVersionResponse.getAsJsonArray("versions").size() >= 1);

        process.kill();

        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // - test that all returned versions are correct based on WebserverAPI's supportedVersions set
    @Test
    public void testThatApiVersionsAreBasedOnWebserverAPIsSupportedVersions() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        JsonObject apiVersionResponse = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/apiversion", null, 1000, 1000, null, null, "");

        Set<SemVer> supportedVersions = WebserverAPI.supportedVersions;
        JsonArray apiSupportedVersions = apiVersionResponse.getAsJsonArray("versions");
        for (int i = 0; i < supportedVersions.size(); i++) {
            assertTrue(supportedVersions.contains(new SemVer(apiSupportedVersions.get(i).getAsString())));
        }
        assertEquals(supportedVersions.size(), apiSupportedVersions.size());
        process.kill();

        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    // - check that all returned versions have X.Y format
    @Test
    public void testThatAllReturnedVersionsHaveXYFormat() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        JsonObject apiVersionResponse = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/apiversion", null, 1000, 1000, null, null, "");

        for (JsonElement i : apiVersionResponse.get("versions").getAsJsonArray()) {
            assertTrue(i.getAsString().matches("\\d+\\.\\d+"));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testThatWebsiteAndAPIDomainAreSaved() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        {
            Map<String, String> params = new HashMap<>();
            params.put("websiteDomain", "https://example.com");

            HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/apiversion", params, 1000, 1000, null, null, "");

            assertEquals("https://example.com",
                    Multitenancy.getWebsiteDomain(StorageLayer.getBaseStorage(process.getProcess()),
                            new AppIdentifier(null,
                                    null)));
        }
        {
            Map<String, String> params = new HashMap<>();
            params.put("apiDomain", "https://api.example.com");

            HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/apiversion", params, 1000, 1000, null, null, "");

            assertEquals("https://api.example.com",
                    Multitenancy.getAPIDomain(StorageLayer.getBaseStorage(process.getProcess()),
                            new AppIdentifier(null,
                                    null)));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testAPIVersionWorksEvenIfThereIsAnException() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        if (StorageLayer.isInMemDb(process.getProcess())) {
            return;
        }

        JsonObject tenantConfigJson = new JsonObject();
        tenantConfigJson.add("postgresql_connection_uri",
                new JsonPrimitive("postgresql://root:root@localhost:5432/random"));
        tenantConfigJson.add("mysql_connection_uri",
                new JsonPrimitive("mysql://root:root@localhost:3306/random"));

        TenantIdentifier tid = new TenantIdentifier(null, "a1", null);

        TenantConfig tenantConfig = new TenantConfig(tid,
                new EmailPasswordConfig(true),
                new ThirdPartyConfig(false, new ThirdPartyConfig.Provider[0]),
                new PasswordlessConfig(false),
                null, null,
                tenantConfigJson);
        StorageLayer.getMultitenancyStorage(process.getProcess()).createTenant(tenantConfig);
        MultitenancyHelper.getInstance(process.getProcess())
                .refreshTenantsInCoreBasedOnChangesInCoreConfigOrIfTenantListChanged(true);

        Map<String, String> params = new HashMap<>();
        params.put("websiteDomain", "https://example.com");
        params.put("apiDomain", "https://api.example.com");

        // Should not throw any exception
        HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/appid-a1/apiversion", params, 1000, 1000, null, null, "");

        process.kill(false);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
