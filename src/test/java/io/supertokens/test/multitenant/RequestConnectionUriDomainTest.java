/*
 *    Copyright (c) 2023, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.test.multitenant;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.supertokens.ProcessState;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.httpRequest.HttpRequest;
import io.supertokens.httpRequest.HttpResponseException;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.multitenancy.exception.CannotModifyBaseConfigException;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.*;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.thirdparty.InvalidProviderConfigException;
import io.supertokens.webserver.Webserver;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.io.IOException;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.*;

public class RequestConnectionUriDomainTest {
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
    public void basicTesting() throws InterruptedException, IOException, HttpResponseException {
        String[] args = {"../"};

        Utils.setValueInConfig("host", "\"0.0.0.0\"");
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        Webserver.getInstance(process.getProcess()).addAPI(new WebserverAPI(process.getProcess(), "") {

            @Override
            public String getPath() {
                return "/test";
            }

            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException,
                    ServletException {
                try {
                    super.sendTextResponse(200, getTenantIdentifier(req).getConnectionUriDomain(), resp);
                } catch (TenantOrAppNotFoundException e) {
                    throw new ServletException(e);
                }
            }
        });

        {
            String response = HttpRequest.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/test", null, 1000, 1000, null);
            assertEquals("", response);
        }

        {
            String response = HttpRequest.sendGETRequest(process.getProcess(), "",
                    "http://127.0.0.1:3567/test", null, 1000, 1000, null);
            assertEquals("", response);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void basicTestingWithDifferentAPIKey()
            throws InterruptedException, IOException, HttpResponseException, InvalidConfigException,
            io.supertokens.test.httpRequest.HttpResponseException, TenantOrAppNotFoundException,
            InvalidProviderConfigException, StorageQueryException, FeatureNotEnabledException,
            CannotModifyBaseConfigException, BadPermissionException {
        String[] args = {"../"};

        Utils.setValueInConfig("host", "\"0.0.0.0\"");
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

        JsonObject tenantConfig = new JsonObject();
        tenantConfig.add("api_keys", new JsonPrimitive("abctijenbogweg=-2438243u98"));
        StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                .modifyConfigToAddANewUserPoolForTesting(tenantConfig, 2);
        JsonObject tenant2Config = new JsonObject();
        tenant2Config.add("api_keys", new JsonPrimitive("abcasdfaliojmo3jenbogweg=-9382923"));
        StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                .modifyConfigToAddANewUserPoolForTesting(tenant2Config, 3);

        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(),
                new TenantConfig(new TenantIdentifier("localhost", null, null), new EmailPasswordConfig(false),
                        new ThirdPartyConfig(false, new ThirdPartyConfig.Provider[0]),
                        new PasswordlessConfig(false),
                        null, null, tenantConfig), false);
        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(),
                new TenantConfig(new TenantIdentifier("127.0.0.1", null, null), new EmailPasswordConfig(false),
                        new ThirdPartyConfig(false, new ThirdPartyConfig.Provider[0]),
                        new PasswordlessConfig(false),
                        null, null, tenant2Config), false);

        Webserver.getInstance(process.getProcess()).addAPI(new WebserverAPI(process.getProcess(), "") {

            @Override
            public String getPath() {
                return "/test";
            }

            @Override
            protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException,
                    ServletException {
                try {
                    super.sendTextResponse(200,
                            super.getTenantIdentifier(req).getConnectionUriDomain() + "," +
                                    super.getTenantIdentifier(req).getTenantId(), resp);
                } catch (TenantOrAppNotFoundException e) {
                    throw new ServletException(e);
                }
            }
        });

        {
            String response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/test", new JsonObject(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(),
                    "abctijenbogweg=-2438243u98", "");
            assertEquals("localhost,public", response);
        }

        {
            String response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://127.0.0.1:3567/test", new JsonObject(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(),
                    "abcasdfaliojmo3jenbogweg=-9382923", "");
            assertEquals("127.0.0.1,public", response);
        }
        {
            try {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/test", new JsonObject(), 1000, 1000, null,
                        Utils.getCdiVersionStringLatestForTests(),
                        "", "");
                fail();
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                assertTrue(e.statusCode == 401
                        && e.getMessage().equals("Http error. Status Code: 401. Message: Invalid API key"));
            }
        }

        {
            try {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://127.0.0.1:3567/test", new JsonObject(), 1000, 1000, null,
                        Utils.getCdiVersionStringLatestForTests(),
                        "abctijenbogweg=-2438243u98", "");
                fail();
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                assertTrue(e.statusCode == 401
                        && e.getMessage().equals("Http error. Status Code: 401. Message: Invalid API key"));
            }
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void basicTestingWithDifferentAPIKeyAndTenantId()
            throws InterruptedException, IOException, HttpResponseException, InvalidConfigException,
            io.supertokens.test.httpRequest.HttpResponseException, TenantOrAppNotFoundException,
            InvalidProviderConfigException, StorageQueryException, FeatureNotEnabledException,
            CannotModifyBaseConfigException, BadPermissionException {
        String[] args = {"../"};

        Utils.setValueInConfig("host", "\"0.0.0.0\"");
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

        JsonObject tenantConfig = new JsonObject();
        tenantConfig.add("api_keys", new JsonPrimitive("abctijenbogweg=-2438243u98"));
        StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                .modifyConfigToAddANewUserPoolForTesting(tenantConfig, 2);
        JsonObject tenant2Config = new JsonObject();
        tenant2Config.add("api_keys", new JsonPrimitive("abcasdfaliojmo3jenbogweg=-9382923"));
        StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                .modifyConfigToAddANewUserPoolForTesting(tenant2Config, 3);

        Multitenancy.addNewOrUpdateAppOrTenant(
                process.getProcess(),
                new TenantConfig(new TenantIdentifier("localhost", null, null), new EmailPasswordConfig(false),
                        new ThirdPartyConfig(false, new ThirdPartyConfig.Provider[0]),
                        new PasswordlessConfig(false),
                        null, null, tenantConfig),
                false
        );
        Multitenancy.addNewOrUpdateAppOrTenant(
                process.getProcess(),
                new TenantConfig(new TenantIdentifier("localhost", null, "t1"), new EmailPasswordConfig(false),
                        new ThirdPartyConfig(false, new ThirdPartyConfig.Provider[0]),
                        new PasswordlessConfig(false),
                        null, null, tenantConfig),
                false
        );
        Multitenancy.addNewOrUpdateAppOrTenant(
                process.getProcess(),
                new TenantConfig(new TenantIdentifier("127.0.0.1", null, null), new EmailPasswordConfig(false),
                        new ThirdPartyConfig(false, new ThirdPartyConfig.Provider[0]),
                        new PasswordlessConfig(false),
                        null, null, tenant2Config),
                false
        );
        Multitenancy.addNewOrUpdateAppOrTenant(
                process.getProcess(),
                new TenantConfig(new TenantIdentifier("127.0.0.1", null, "t1"), new EmailPasswordConfig(false),
                        new ThirdPartyConfig(false, new ThirdPartyConfig.Provider[0]),
                        new PasswordlessConfig(false),
                        null, null, tenant2Config),
                false
        );

        Webserver.getInstance(process.getProcess()).addAPI(new WebserverAPI(process.getProcess(), "") {

            @Override
            public String getPath() {
                return "/test";
            }

            @Override
            protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException,
                    ServletException {
                try {
                    super.sendTextResponse(200,
                            super.getTenantIdentifier(req).getConnectionUriDomain() + "," +
                                    super.getTenantIdentifier(req).getTenantId(), resp);
                } catch (TenantOrAppNotFoundException e) {
                    throw new ServletException(e);
                }
            }
        });

        {
            String response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/test", new JsonObject(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(),
                    "abctijenbogweg=-2438243u98", "");
            assertEquals("localhost,public", response);
        }

        {
            String response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://127.0.0.1:3567/test", new JsonObject(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(),
                    "abcasdfaliojmo3jenbogweg=-9382923", "");
            assertEquals("127.0.0.1,public", response);
        }
        {
            String response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/t1/test", new JsonObject(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(),
                    "abctijenbogweg=-2438243u98", "");
            assertEquals("localhost,t1", response);
        }

        {
            String response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://127.0.0.1:3567/t1/test", new JsonObject(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(),
                    "abcasdfaliojmo3jenbogweg=-9382923", "");
            assertEquals("127.0.0.1,t1", response);
        }

        {
            try {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/test", new JsonObject(), 1000, 1000, null,
                        Utils.getCdiVersionStringLatestForTests(),
                        "", "");
                fail();
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                assertTrue(e.statusCode == 401
                        && e.getMessage().equals("Http error. Status Code: 401. Message: Invalid API key"));
            }
        }

        {
            try {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://127.0.0.1:3567/test", new JsonObject(), 1000, 1000, null,
                        Utils.getCdiVersionStringLatestForTests(),
                        "abctijenbogweg=-2438243u98", "");
                fail();
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                assertTrue(e.statusCode == 401
                        && e.getMessage().equals("Http error. Status Code: 401. Message: Invalid API key"));
            }
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
