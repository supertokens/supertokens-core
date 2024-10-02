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
import com.google.gson.JsonPrimitive;
import io.supertokens.ProcessState;
import io.supertokens.ProcessState.PROCESS_STATE;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.multitenancy.exception.CannotModifyBaseConfigException;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.*;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager.TestingProcess;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.httpRequest.HttpResponseException;
import io.supertokens.thirdparty.InvalidProviderConfigException;
import io.supertokens.webserver.RecipeRouter;
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
import org.mockito.Mockito;

import java.io.IOException;
import java.util.HashMap;

import static org.junit.Assert.*;

public class PathRouterTest extends Mockito {

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
    public void test500ErrorMessage() throws Exception {
        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        Webserver.getInstance(process.getProcess()).addAPI(new WebserverAPI(process.getProcess(), "") {

            private static final long serialVersionUID = 1L;

            @Override
            public boolean checkAPIKey(HttpServletRequest req) {
                return false;
            }

            @Override
            public String getPath() {
                return "/test/servlet-exception";
            }

            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                    throws IOException, ServletException {
                throw new ServletException(new RuntimeException("Test Exception"));
            }
        });

        Webserver.getInstance(process.getProcess()).addAPI(new WebserverAPI(process.getProcess(), "") {

            private static final long serialVersionUID = 1L;

            @Override
            public boolean checkAPIKey(HttpServletRequest req) {
                return false;
            }

            @Override
            public String getPath() {
                return "/test/runtime-exception";
            }

            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                    throws IOException, ServletException {
                throw new RuntimeException("Runtime Exception");
            }
        });

        {
            try {
                String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                        "http://localhost:3567/test/servlet-exception", new HashMap<>(), 1000, 1000, null,
                        Utils.getCdiVersionStringLatestForTests(), "");
                fail();
            } catch (HttpResponseException e) {
                assertEquals(500, e.statusCode);
                assertEquals("Http error. Status Code: 500. Message: Test Exception", e.getMessage());
            }
        }

        {
            try {
                String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                        "http://localhost:3567/test/runtime-exception", new HashMap<>(), 1000, 1000, null,
                        Utils.getCdiVersionStringLatestForTests(), "");
                fail();
            } catch (HttpResponseException e) {
                assertEquals(500, e.statusCode);
                assertEquals("Http error. Status Code: 500. Message: Runtime Exception", e.getMessage());
            }
        }
    }

    @Test
    public void basicTenantIdFetchingTest()
            throws InterruptedException, IOException, HttpResponseException, InvalidProviderConfigException,
            StorageQueryException, FeatureNotEnabledException, TenantOrAppNotFoundException, InvalidConfigException,
            CannotModifyBaseConfigException, BadPermissionException {
        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        Multitenancy.addNewOrUpdateAppOrTenant(
                process.getProcess(),
                new TenantConfig(
                        new TenantIdentifier(null, null, "t1"),
                        new EmailPasswordConfig(false),
                        new ThirdPartyConfig(false, null),
                        new PasswordlessConfig(false),
                        null, null, new JsonObject()
                ),
                false
        );
        Multitenancy.addNewOrUpdateAppOrTenant(
                process.getProcess(),
                new TenantConfig(
                        new TenantIdentifier(null, null, "t2"),
                        new EmailPasswordConfig(false),
                        new ThirdPartyConfig(false, null),
                        new PasswordlessConfig(false),
                        null, null, new JsonObject()
                ),
                false
        );
        Multitenancy.addNewOrUpdateAppOrTenant(
                process.getProcess(),
                new TenantConfig(
                        new TenantIdentifier(null, null, "hello"),
                        new EmailPasswordConfig(false),
                        new ThirdPartyConfig(false, null),
                        new PasswordlessConfig(false),
                        null, null, new JsonObject()
                ),
                false
        );

        String[] paths = new String[]{"/test", "/recipe/test", "/test/t1", "/t1/t1"};

        for (String p : paths) {
            Webserver.getInstance(process.getProcess()).addAPI(new WebserverAPI(process.getProcess(), "") {

                private static final long serialVersionUID = 1L;

                @Override
                public boolean checkAPIKey(HttpServletRequest req) {
                    return false;
                }

                @Override
                public String getPath() {
                    return p;
                }

                @Override
                protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                        throws IOException, ServletException {
                    try {
                        super.sendTextResponse(200,
                                this.getTenantIdentifier(req).getConnectionUriDomain() + "," +
                                        this.getTenantIdentifier(req).getTenantId(), resp);
                    } catch (TenantOrAppNotFoundException e) {
                        throw new ServletException(e);
                    }
                }
            });
        }

        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/test", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",public", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/test", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",public", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/t2/recipe/test", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",t2", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/t1/test", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",t1", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/test/t1", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",public", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/t1/test/t1", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",t1", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/t1/t1", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",public", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/t1/t1/t1", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",t1", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/public/t1/t1", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",public", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/public/t1/t1", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",public", response);
        }
        {
            try {
                HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                        "http://localhost:3567/test/t1/t2", new HashMap<>(), 1000, 1000, null,
                        Utils.getCdiVersionStringLatestForTests(), "");
                fail();
            } catch (HttpResponseException e) {
                assertEquals(e.statusCode, 404);
                assertEquals(e.getMessage(), "Http error. Status Code: 404. Message: Not found");
            }
        }
        {
            try {
                HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                        "http://localhost:3567/t2/t1/test/t1", new HashMap<>(), 1000, 1000, null,
                        Utils.getCdiVersionStringLatestForTests(), "");
                fail();
            } catch (HttpResponseException e) {
                assertEquals(e.statusCode, 404);
                assertEquals(e.getMessage(), "Http error. Status Code: 404. Message: Not found");
            }
        }
        {
            try {
                HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                        "http://localhost:3567/t2/t1/t1/t1", new HashMap<>(), 1000, 1000, null,
                        Utils.getCdiVersionStringLatestForTests(), "");
                fail();
            } catch (HttpResponseException e) {
                assertEquals(e.statusCode, 404);
                assertEquals(e.getMessage(), "Http error. Status Code: 404. Message: Not found");
            }
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void basicTenantIdFetchingWihQueryParamTest()
            throws InterruptedException, IOException, HttpResponseException, InvalidProviderConfigException,
            StorageQueryException, FeatureNotEnabledException, TenantOrAppNotFoundException, InvalidConfigException,
            CannotModifyBaseConfigException, BadPermissionException {
        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        Multitenancy.addNewOrUpdateAppOrTenant(
                process.getProcess(),
                new TenantConfig(
                        new TenantIdentifier(null, null, "t1"),
                        new EmailPasswordConfig(false),
                        new ThirdPartyConfig(false, null),
                        new PasswordlessConfig(false),
                        null, null, new JsonObject()
                ),
                false
        );
        Multitenancy.addNewOrUpdateAppOrTenant(
                process.getProcess(),
                new TenantConfig(
                        new TenantIdentifier(null, null, "t2"),
                        new EmailPasswordConfig(false),
                        new ThirdPartyConfig(false, null),
                        new PasswordlessConfig(false),
                        null, null, new JsonObject()
                ),
                false
        );
        Multitenancy.addNewOrUpdateAppOrTenant(
                process.getProcess(),
                new TenantConfig(
                        new TenantIdentifier(null, null, "hello"),
                        new EmailPasswordConfig(false),
                        new ThirdPartyConfig(false, null),
                        new PasswordlessConfig(false),
                        null, null, new JsonObject()
                ),
                false
        );
        String[] paths = new String[]{"/test", "/recipe/test", "/test/t1", "/t1/t1"};

        for (String p : paths) {
            Webserver.getInstance(process.getProcess()).addAPI(new WebserverAPI(process.getProcess(), "") {

                private static final long serialVersionUID = 1L;

                @Override
                public boolean checkAPIKey(HttpServletRequest req) {
                    return false;
                }

                @Override
                public String getPath() {
                    return p;
                }

                @Override
                protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                        throws IOException, ServletException {
                    try {
                        super.sendTextResponse(200,
                                this.getTenantIdentifier(req).getConnectionUriDomain() + "," +
                                        this.getTenantIdentifier(req).getTenantId(), resp);
                    } catch (TenantOrAppNotFoundException e) {
                        throw new ServletException(e);
                    }
                }
            });
        }

        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/test?a=b&c=d", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",public", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/test?t1=b", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",public", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/t2/recipe/test?a=b&c=d", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",t2", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/t1/test?a=b&c=d", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",t1", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/test/t1?a=b&c=d", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",public", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/t1/test/t1?a=b&c=d", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",t1", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/t1/t1?a=b&c=d", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",public", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/t1/t1/t1?a=b&c=d", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",t1", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/public/t1/t1?a=b&c=d", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",public", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/public/t1/t1?a=b&c=d", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",public", response);
        }
        {
            try {
                HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                        "http://localhost:3567/test/t1/t2?a=b&c=d", new HashMap<>(), 1000, 1000, null,
                        Utils.getCdiVersionStringLatestForTests(), "");
                fail();
            } catch (HttpResponseException e) {
                assertEquals(e.statusCode, 404);
                assertEquals(e.getMessage(), "Http error. Status Code: 404. Message: Not found");
            }
        }
        {
            try {
                HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                        "http://localhost:3567/t2/t1/test/t1?a=b&c=d", new HashMap<>(), 1000, 1000, null,
                        Utils.getCdiVersionStringLatestForTests(), "");
                fail();
            } catch (HttpResponseException e) {
                assertEquals(e.statusCode, 404);
                assertEquals(e.getMessage(), "Http error. Status Code: 404. Message: Not found");
            }
        }
        {
            try {
                HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                        "http://localhost:3567/t2/t1/t1/t1?a=b&c=d", new HashMap<>(), 1000, 1000, null,
                        Utils.getCdiVersionStringLatestForTests(), "");
                fail();
            } catch (HttpResponseException e) {
                assertEquals(e.statusCode, 404);
                assertEquals(e.getMessage(), "Http error. Status Code: 404. Message: Not found");
            }
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void basicTenantIdFetchingWithBasePathTest()
            throws InterruptedException, IOException, HttpResponseException, InvalidProviderConfigException,
            StorageQueryException, FeatureNotEnabledException, TenantOrAppNotFoundException, InvalidConfigException,
            CannotModifyBaseConfigException, BadPermissionException {
        String[] args = {"../"};
        Utils.setValueInConfig("base_path", "base_path");
        TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        Multitenancy.addNewOrUpdateAppOrTenant(
                process.getProcess(),
                new TenantConfig(
                        new TenantIdentifier(null, null, "t1"),
                        new EmailPasswordConfig(false),
                        new ThirdPartyConfig(false, null),
                        new PasswordlessConfig(false),
                        null, null, new JsonObject()
                ),
                false
        );
        Multitenancy.addNewOrUpdateAppOrTenant(
                process.getProcess(),
                new TenantConfig(
                        new TenantIdentifier(null, null, "t2"),
                        new EmailPasswordConfig(false),
                        new ThirdPartyConfig(false, null),
                        new PasswordlessConfig(false),
                        null, null, new JsonObject()
                ),
                false
        );
        Multitenancy.addNewOrUpdateAppOrTenant(
                process.getProcess(),
                new TenantConfig(
                        new TenantIdentifier(null, null, "hello"),
                        new EmailPasswordConfig(false),
                        new ThirdPartyConfig(false, null),
                        new PasswordlessConfig(false),
                        null, null, new JsonObject()
                ),
                false
        );
        String[] paths = new String[]{"/test", "/recipe/test", "/test/t1", "/t1/t1"};

        for (String p : paths) {
            Webserver.getInstance(process.getProcess()).addAPI(new WebserverAPI(process.getProcess(), "") {

                private static final long serialVersionUID = 1L;

                @Override
                public boolean checkAPIKey(HttpServletRequest req) {
                    return false;
                }

                @Override
                public String getPath() {
                    return p;
                }

                @Override
                protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                        throws IOException, ServletException {
                    try {
                        super.sendTextResponse(200,
                                this.getTenantIdentifier(req).getConnectionUriDomain() + "," +
                                        this.getTenantIdentifier(req).getTenantId(), resp);
                    } catch (TenantOrAppNotFoundException e) {
                        throw new ServletException(e);
                    }
                }
            });
        }

        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/base_path/test", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",public", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/base_path/recipe/test", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",public", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/base_path/t2/recipe/test", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",t2", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/base_path/t1/test", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",t1", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/base_path/test/t1", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",public", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/base_path/t1/test/t1?t1=a", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",t1", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/base_path/t1/t1?a=b", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",public", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/base_path/t1/t1/t1", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",t1", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/base_path/public/t1/t1", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",public", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/base_path/public/t1/t1", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",public", response);
        }
        {
            try {
                HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                        "http://localhost:3567/base_path/test/t1/t2", new HashMap<>(), 1000, 1000, null,
                        Utils.getCdiVersionStringLatestForTests(), "");
                fail();
            } catch (HttpResponseException e) {
                assertEquals(e.statusCode, 404);
                assertEquals(e.getMessage(), "Http error. Status Code: 404. Message: Not found");
            }
        }
        {
            try {
                HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                        "http://localhost:3567/base_path/t2/t1/test/t1", new HashMap<>(), 1000, 1000, null,
                        Utils.getCdiVersionStringLatestForTests(), "");
                fail();
            } catch (HttpResponseException e) {
                assertEquals(e.statusCode, 404);
                assertEquals(e.getMessage(), "Http error. Status Code: 404. Message: Not found");
            }
        }
        {
            try {
                HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                        "http://localhost:3567/base_path/t2/t1/t1/t1", new HashMap<>(), 1000, 1000, null,
                        Utils.getCdiVersionStringLatestForTests(), "");
                fail();
            } catch (HttpResponseException e) {
                assertEquals(e.statusCode, 404);
                assertEquals(e.getMessage(), "Http error. Status Code: 404. Message: Not found");
            }
        }
        {
            try {
                HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                        "http://localhost:3567/test/t1", new HashMap<>(), 1000, 1000, null,
                        Utils.getCdiVersionStringLatestForTests(), "");
                fail();
            } catch (HttpResponseException e) {
                assertEquals(e.statusCode, 404);
            }
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void basicTenantIdFetchingWithBasePathTest2()
            throws InterruptedException, IOException, HttpResponseException, InvalidProviderConfigException,
            StorageQueryException, FeatureNotEnabledException, TenantOrAppNotFoundException, InvalidConfigException,
            CannotModifyBaseConfigException, BadPermissionException {
        String[] args = {"../"};
        Utils.setValueInConfig("base_path", "/base/path");
        TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        Multitenancy.addNewOrUpdateAppOrTenant(
                process.getProcess(),
                new TenantConfig(
                        new TenantIdentifier(null, null, "t1"),
                        new EmailPasswordConfig(false),
                        new ThirdPartyConfig(false, null),
                        new PasswordlessConfig(false),
                        null, null, new JsonObject()
                ),
                false
        );
        Multitenancy.addNewOrUpdateAppOrTenant(
                process.getProcess(),
                new TenantConfig(
                        new TenantIdentifier(null, null, "t2"),
                        new EmailPasswordConfig(false),
                        new ThirdPartyConfig(false, null),
                        new PasswordlessConfig(false),
                        null, null, new JsonObject()
                ),
                false
        );
        Multitenancy.addNewOrUpdateAppOrTenant(
                process.getProcess(),
                new TenantConfig(
                        new TenantIdentifier(null, null, "hello"),
                        new EmailPasswordConfig(false),
                        new ThirdPartyConfig(false, null),
                        new PasswordlessConfig(false),
                        null, null, new JsonObject()
                ),
                false
        );

        String[] paths = new String[]{"/test", "/recipe/test", "/test/t1", "/t1/t1"};

        for (String p : paths) {
            Webserver.getInstance(process.getProcess()).addAPI(new WebserverAPI(process.getProcess(), "") {

                private static final long serialVersionUID = 1L;

                @Override
                public boolean checkAPIKey(HttpServletRequest req) {
                    return false;
                }

                @Override
                public String getPath() {
                    return p;
                }

                @Override
                protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                        throws IOException, ServletException {
                    try {
                        super.sendTextResponse(200,
                                this.getTenantIdentifier(req).getConnectionUriDomain() + "," +
                                        this.getTenantIdentifier(req).getTenantId(), resp);
                    } catch (TenantOrAppNotFoundException e) {
                        throw new ServletException(e);
                    }
                }
            });
        }

        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/base/path/test", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",public", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/base/path/recipe/test", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",public", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/base/path/t2/recipe/test", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",t2", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/base/path/t1/test", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",t1", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/base/path/test/t1", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",public", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/base/path/t1/test/t1?t1=a", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",t1", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/base/path/t1/t1?a=b", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",public", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/base/path/t1/t1/t1", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",t1", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/base/path/public/t1/t1", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",public", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/base/path/public/t1/t1", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",public", response);
        }
        {
            try {
                HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                        "http://localhost:3567/base/path/test/t1/t2", new HashMap<>(), 1000, 1000, null,
                        Utils.getCdiVersionStringLatestForTests(), "");
                fail();
            } catch (HttpResponseException e) {
                assertEquals(e.statusCode, 404);
                assertEquals(e.getMessage(), "Http error. Status Code: 404. Message: Not found");
            }
        }
        {
            try {
                HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                        "http://localhost:3567/base/path/t2/t1/test/t1", new HashMap<>(), 1000, 1000, null,
                        Utils.getCdiVersionStringLatestForTests(), "");
                fail();
            } catch (HttpResponseException e) {
                assertEquals(e.statusCode, 404);
                assertEquals(e.getMessage(), "Http error. Status Code: 404. Message: Not found");
            }
        }
        {
            try {
                HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                        "http://localhost:3567/base/path/t2/t1/t1/t1", new HashMap<>(), 1000, 1000, null,
                        Utils.getCdiVersionStringLatestForTests(), "");
                fail();
            } catch (HttpResponseException e) {
                assertEquals(e.statusCode, 404);
                assertEquals(e.getMessage(), "Http error. Status Code: 404. Message: Not found");
            }
        }
        {
            try {
                HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                        "http://localhost:3567/test/t1", new HashMap<>(), 1000, 1000, null,
                        Utils.getCdiVersionStringLatestForTests(), "");
                fail();
            } catch (HttpResponseException e) {
                assertEquals(e.statusCode, 404);
            }
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void basicTenantIdFetchingWithBasePathTest3()
            throws InterruptedException, IOException, HttpResponseException, InvalidProviderConfigException,
            StorageQueryException, FeatureNotEnabledException, TenantOrAppNotFoundException, InvalidConfigException,
            CannotModifyBaseConfigException, BadPermissionException {
        String[] args = {"../"};
        Utils.setValueInConfig("base_path", "/t1");
        TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        Multitenancy.addNewOrUpdateAppOrTenant(
                process.getProcess(),
                new TenantConfig(
                        new TenantIdentifier(null, null, "t1"),
                        new EmailPasswordConfig(false),
                        new ThirdPartyConfig(false, null),
                        new PasswordlessConfig(false),
                        null, null, new JsonObject()
                ),
                false
        );
        Multitenancy.addNewOrUpdateAppOrTenant(
                process.getProcess(),
                new TenantConfig(
                        new TenantIdentifier(null, null, "t2"),
                        new EmailPasswordConfig(false),
                        new ThirdPartyConfig(false, null),
                        new PasswordlessConfig(false),
                        null, null, new JsonObject()
                ),
                false
        );
        Multitenancy.addNewOrUpdateAppOrTenant(
                process.getProcess(),
                new TenantConfig(
                        new TenantIdentifier(null, null, "hello"),
                        new EmailPasswordConfig(false),
                        new ThirdPartyConfig(false, null),
                        new PasswordlessConfig(false),
                        null, null, new JsonObject()
                ),
                false
        );

        String[] paths = new String[]{"/test", "/recipe/test", "/test/t1", "/t1/t1"};

        for (String p : paths) {
            Webserver.getInstance(process.getProcess()).addAPI(new WebserverAPI(process.getProcess(), "") {

                private static final long serialVersionUID = 1L;

                @Override
                public boolean checkAPIKey(HttpServletRequest req) {
                    return false;
                }

                @Override
                public String getPath() {
                    return p;
                }

                @Override
                protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                        throws IOException, ServletException {
                    try {
                        super.sendTextResponse(200,
                                this.getTenantIdentifier(req).getConnectionUriDomain() + "," +
                                        this.getTenantIdentifier(req).getTenantId(), resp);
                    } catch (TenantOrAppNotFoundException e) {
                        throw new ServletException(e);
                    }
                }
            });
        }

        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/t1/test", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",public", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/t1/recipe/test", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",public", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/t1/t2/recipe/test", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",t2", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/t1/t1/test", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",t1", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/t1/test/t1", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",public", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/t1/t1/test/t1?t1=a", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",t1", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/t1/t1/t1?a=b", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",public", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/t1/t1/t1/t1", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",t1", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/t1/public/t1/t1", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",public", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/t1/public/t1/t1", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",public", response);
        }
        {
            try {
                HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                        "http://localhost:3567/t1/test/t1/t2", new HashMap<>(), 1000, 1000, null,
                        Utils.getCdiVersionStringLatestForTests(), "");
                fail();
            } catch (HttpResponseException e) {
                assertEquals(e.statusCode, 404);
                assertEquals(e.getMessage(), "Http error. Status Code: 404. Message: Not found");
            }
        }
        {
            try {
                HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                        "http://localhost:3567/t1/t2/t1/test/t1", new HashMap<>(), 1000, 1000, null,
                        Utils.getCdiVersionStringLatestForTests(), "");
                fail();
            } catch (HttpResponseException e) {
                assertEquals(e.statusCode, 404);
                assertEquals(e.getMessage(), "Http error. Status Code: 404. Message: Not found");
            }
        }
        {
            try {
                HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                        "http://localhost:3567/t1/t2/t1/t1/t1", new HashMap<>(), 1000, 1000, null,
                        Utils.getCdiVersionStringLatestForTests(), "");
                fail();
            } catch (HttpResponseException e) {
                assertEquals(e.statusCode, 404);
                assertEquals(e.getMessage(), "Http error. Status Code: 404. Message: Not found");
            }
        }
        {
            try {
                HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                        "http://localhost:3567/test/t1", new HashMap<>(), 1000, 1000, null,
                        Utils.getCdiVersionStringLatestForTests(), "");
                fail();
            } catch (HttpResponseException e) {
                assertEquals(e.statusCode, 404);
            }
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }


    @Test
    public void withRecipeRouterTest() throws Exception {
        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        Multitenancy.addNewOrUpdateAppOrTenant(
                process.getProcess(),
                new TenantConfig(
                        new TenantIdentifier(null, null, "t1"),
                        new EmailPasswordConfig(false),
                        new ThirdPartyConfig(false, null),
                        new PasswordlessConfig(false),
                        null, null, new JsonObject()
                ),
                false);
        Multitenancy.addNewOrUpdateAppOrTenant(
                process.getProcess(),
                new TenantConfig(
                        new TenantIdentifier(null, null, "t2"),
                        new EmailPasswordConfig(false),
                        new ThirdPartyConfig(false, null),
                        new PasswordlessConfig(false),
                        null, null, new JsonObject()
                ),
                false);

        Webserver.getInstance(process.getProcess())
                .addAPI(new RecipeRouter(process.main, new WebserverAPI(process.getProcess(), "") {

                    private static final long serialVersionUID = 1L;

                    @Override
                    public boolean checkAPIKey(HttpServletRequest req) {
                        return false;
                    }

                    @Override
                    public String getPath() {
                        return "/test";
                    }

                    @Override
                    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                            throws IOException, ServletException {
                        try {
                            super.sendTextResponse(200,
                                    this.getTenantIdentifier(req).getConnectionUriDomain() + "," +
                                            this.getTenantIdentifier(req).getTenantId() +
                                            ",",
                                    resp);
                        } catch (TenantOrAppNotFoundException e) {
                            throw new ServletException(e);
                        }
                    }
                }, new WebserverAPI(process.getProcess(), "r1") {

                    private static final long serialVersionUID = 1L;

                    @Override
                    public boolean checkAPIKey(HttpServletRequest req) {
                        return false;
                    }

                    @Override
                    public String getPath() {
                        return "/test";
                    }

                    @Override
                    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                            throws IOException, ServletException {
                        try {
                            super.sendTextResponse(200,
                                    this.getTenantIdentifier(req).getConnectionUriDomain() + "," +
                                            this.getTenantIdentifier(req).getTenantId() +
                                            ",r1",
                                    resp);
                        } catch (TenantOrAppNotFoundException e) {
                            throw new ServletException(e);
                        }
                    }
                }));

        Webserver.getInstance(process.getProcess()).addAPI(new WebserverAPI(process.getProcess(), "") {

            private static final long serialVersionUID = 1L;

            @Override
            public boolean checkAPIKey(HttpServletRequest req) {
                return false;
            }

            @Override
            public String getPath() {
                return "/t1/test";
            }

            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException,
                    ServletException {
                try {
                    super.sendTextResponse(200,
                            this.getTenantIdentifier(req).getConnectionUriDomain() + "," +
                                    this.getTenantIdentifier(req).getTenantId(), resp);
                } catch (TenantOrAppNotFoundException e) {
                    throw new ServletException(e);
                }
            }
        });

        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/test", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",public,", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/t2/test", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",t2,", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/t1/test", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",public", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/t1/test", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "r1");
            assertEquals(",public", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/test", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "r1");
            assertEquals(",public,r1", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/t2/test", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "r1");
            assertEquals(",t2,r1", response);
        }
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void errorFromAddAPICauseOfSameRoute() throws Exception {
        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        Webserver.getInstance(process.getProcess())
                .addAPI(new RecipeRouter(process.main, new WebserverAPI(process.getProcess(), "") {

                    private static final long serialVersionUID = 1L;

                    @Override
                    public boolean checkAPIKey(HttpServletRequest req) {
                        return false;
                    }

                    @Override
                    public String getPath() {
                        return "/test";
                    }

                    @Override
                    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                            throws IOException, ServletException {
                        try {
                            super.sendTextResponse(200,
                                    this.getTenantIdentifier(req).getConnectionUriDomain() + "," +
                                            this.getTenantIdentifier(req).getTenantId() +
                                            ",",
                                    resp);
                        } catch (TenantOrAppNotFoundException e) {
                            throw new ServletException(e);
                        }
                    }
                }));

        try {
            Webserver.getInstance(process.getProcess()).addAPI(new WebserverAPI(process.getProcess(), "") {

                private static final long serialVersionUID = 1L;

                @Override
                public boolean checkAPIKey(HttpServletRequest req) {
                    return false;
                }

                @Override
                public String getPath() {
                    return "/test";
                }

                @Override
                protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                        throws IOException, ServletException {
                    try {
                        super.sendTextResponse(200,
                                this.getTenantIdentifier(req).getConnectionUriDomain() + "," +
                                        this.getTenantIdentifier(req).getTenantId(), resp);
                    } catch (TenantOrAppNotFoundException e) {
                        throw new ServletException(e);
                    }
                }
            });
            fail();
        } catch (IllegalStateException e) {
            assertEquals(e.getMessage(), "APIs given to the router cannot have the same path");
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void errorFromRecipeRouterCauseOfSameRouteAndRid() throws Exception {
        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        try {
            Webserver.getInstance(process.getProcess())
                    .addAPI(new RecipeRouter(process.main, new WebserverAPI(process.getProcess(), "r1") {

                        private static final long serialVersionUID = 1L;

                        @Override
                        public boolean checkAPIKey(HttpServletRequest req) {
                            return false;
                        }

                        @Override
                        public String getPath() {
                            return "/test";
                        }

                        @Override
                        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                                throws IOException, ServletException {
                            try {
                                super.sendTextResponse(200,
                                        this.getTenantIdentifier(req).getConnectionUriDomain() + "," +
                                                this.getTenantIdentifier(req).getTenantId() + ",",
                                        resp);
                            } catch (TenantOrAppNotFoundException e) {
                                throw new ServletException(e);
                            }
                        }
                    }, new WebserverAPI(process.getProcess(), "r1") {

                        private static final long serialVersionUID = 1L;

                        @Override
                        public boolean checkAPIKey(HttpServletRequest req) {
                            return false;
                        }

                        @Override
                        public String getPath() {
                            return "/test";
                        }

                        @Override
                        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                                throws IOException, ServletException {
                            try {
                                super.sendTextResponse(200,
                                        this.getTenantIdentifier(req).getConnectionUriDomain() + "," +
                                                this.getTenantIdentifier(req).getTenantId() + ",r1",
                                        resp);
                            } catch (TenantOrAppNotFoundException e) {
                                throw new ServletException(e);
                            }
                        }
                    }));
            fail();
        } catch (Exception e) {
            assertEquals(e.getMessage(), "Same rid used in recipe router");
        }

        try {
            Webserver.getInstance(process.getProcess())
                    .addAPI(new RecipeRouter(process.main, new WebserverAPI(process.getProcess(), "") {

                        private static final long serialVersionUID = 1L;

                        @Override
                        public boolean checkAPIKey(HttpServletRequest req) {
                            return false;
                        }

                        @Override
                        public String getPath() {
                            return "/t1/test";
                        }

                        @Override
                        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                                throws IOException, ServletException {
                            try {
                                super.sendTextResponse(200,
                                        this.getTenantIdentifier(req).getConnectionUriDomain() + "," +
                                                this.getTenantIdentifier(req).getTenantId() + ",",
                                        resp);
                            } catch (TenantOrAppNotFoundException e) {
                                throw new ServletException(e);
                            }
                        }
                    }, new WebserverAPI(process.getProcess(), "r1") {

                        private static final long serialVersionUID = 1L;

                        @Override
                        public boolean checkAPIKey(HttpServletRequest req) {
                            return false;
                        }

                        @Override
                        public String getPath() {
                            return "/test";
                        }

                        @Override
                        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                                throws IOException, ServletException {
                            try {
                                super.sendTextResponse(200,
                                        this.getTenantIdentifier(req).getConnectionUriDomain() + "," +
                                                this.getTenantIdentifier(req).getTenantId() + ",r1",
                                        resp);
                            } catch (TenantOrAppNotFoundException e) {
                                throw new ServletException(e);
                            }
                        }
                    }));
            fail();
        } catch (Exception e) {
            assertEquals(e.getMessage(), "All APIs given to router do not have the same path");
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void tenantNotFoundTest()
            throws InterruptedException, IOException, io.supertokens.httpRequest.HttpResponseException,
            InvalidConfigException,
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
        JsonObject tenant2Config = new JsonObject();
        StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                .modifyConfigToAddANewUserPoolForTesting(tenantConfig, 2);
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
                    super.sendTextResponse(200, super.getTenantIdentifier(req).getConnectionUriDomain() + "," +
                            this.getTenantIdentifier(req).getTenantId(), resp);
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
            try {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://127.0.0.1:3567/t2/test", new JsonObject(), 1000, 1000, null,
                        Utils.getCdiVersionStringLatestForTests(),
                        "abcasdfaliojmo3jenbogweg=-9382923", "");
                fail();
            } catch (HttpResponseException e) {
                assertEquals(e.statusCode, 400);
                assertEquals(e.getMessage(),
                        "Http error. Status Code: 400. Message: AppId or tenantId not found => Tenant with the " +
                                "following connectionURIDomain, appId and tenantId combination not found: (127.0.0" +
                                ".1, " +
                                "public, t2)");
            }
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

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void tenantNotFoundTest2()
            throws InterruptedException, IOException, io.supertokens.httpRequest.HttpResponseException,
            InvalidConfigException,
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
                new TenantConfig(new TenantIdentifier(null, null, "t2"), new EmailPasswordConfig(false),
                        new ThirdPartyConfig(false, new ThirdPartyConfig.Provider[0]),
                        new PasswordlessConfig(false),
                        null, null, new JsonObject()),
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
                    super.sendTextResponse(200, super.getTenantIdentifier(req).getConnectionUriDomain() + "," +
                                    this.getTenantIdentifier(req).getTenantId(),
                            resp);
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
            assertEquals(",public", response);
        }
        {
            String response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/t1/test", new JsonObject(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(),
                    "abctijenbogweg=-2438243u98", "");
            assertEquals("localhost,t1", response);
        }

        {
            try {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://127.0.0.1:3567/t1/test", new JsonObject(), 1000, 1000, null,
                        Utils.getCdiVersionStringLatestForTests(),
                        "abcasdfaliojmo3jenbogweg=-9382923", "");
                fail();
            } catch (HttpResponseException e) {
                assertEquals(e.statusCode, 400);
                assertEquals(e.getMessage(),
                        "Http error. Status Code: 400. Message: AppId or tenantId not found => Tenant with the " +
                                "following connectionURIDomain, appId and tenantId combination not found: (, public, " +
                                "t1)");
            }
        }
        {
            String response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://127.0.0.1:3567/t2/test", new JsonObject(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(),
                    "", "");
            assertEquals(",t2", response);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void tenantNotFoundTest3()
            throws InterruptedException, IOException, io.supertokens.httpRequest.HttpResponseException,
            InvalidConfigException,
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

        Multitenancy.addNewOrUpdateAppOrTenant(
                process.getProcess(),
                new TenantConfig(
                        new TenantIdentifier("localhost", null, null),
                        new EmailPasswordConfig(false),
                        new ThirdPartyConfig(false, new ThirdPartyConfig.Provider[0]),
                        new PasswordlessConfig(false),
                        null, null, tenantConfig),
                false
        );
        Multitenancy.addNewOrUpdateAppOrTenant(
                process.getProcess(),
                new TenantConfig(
                        new TenantIdentifier("localhost", null, "t1"),
                        new EmailPasswordConfig(false),
                        new ThirdPartyConfig(false, new ThirdPartyConfig.Provider[0]),
                        new PasswordlessConfig(false),
                        null, null, tenantConfig),
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
                    super.sendTextResponse(200, super.getTenantIdentifier(req).getConnectionUriDomain() + "," +
                                    this.getTenantIdentifier(req).getTenantId(),
                            resp);
                } catch (TenantOrAppNotFoundException e) {
                    throw new ServletException(e);
                }
            }
        });

        {
            try {
                String response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/t2/test", new JsonObject(), 1000, 1000, null,
                        Utils.getCdiVersionStringLatestForTests(),
                        "abctijenbogweg=-2438243u98", "");
                fail();
            } catch (HttpResponseException e) {
                assertEquals(e.statusCode, 400);
                assertEquals(e.getMessage(),
                        "Http error. Status Code: 400. Message: AppId or tenantId not found => Tenant with the " +
                                "following connectionURIDomain, appId and tenantId combination not found: " +
                                "(localhost, " +
                                "public, t2)");
            }
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void basicAppIdTesting()
            throws InterruptedException, IOException, HttpResponseException, InvalidProviderConfigException,
            StorageQueryException, FeatureNotEnabledException, TenantOrAppNotFoundException, InvalidConfigException,
            CannotModifyBaseConfigException, BadPermissionException {
        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        Multitenancy.addNewOrUpdateAppOrTenant(
                process.getProcess(),
                new TenantConfig(
                        new TenantIdentifier(null, "abc", null),
                        new EmailPasswordConfig(false),
                        new ThirdPartyConfig(false, null),
                        new PasswordlessConfig(false),
                        null, null, new JsonObject()
                ),
                false
        );
        Multitenancy.addNewOrUpdateAppOrTenant(
                process.getProcess(),
                new TenantConfig(
                        new TenantIdentifier(null, "abc", "t2"),
                        new EmailPasswordConfig(false),
                        new ThirdPartyConfig(false, null),
                        new PasswordlessConfig(false),
                        null, null, new JsonObject()
                ),
                false
        );
        Multitenancy.addNewOrUpdateAppOrTenant(
                process.getProcess(),
                new TenantConfig(
                        new TenantIdentifier(null, "appid-abc", null),
                        new EmailPasswordConfig(false),
                        new ThirdPartyConfig(false, null),
                        new PasswordlessConfig(false),
                        null, null, new JsonObject()
                ),
                false
        );
        Multitenancy.addNewOrUpdateAppOrTenant(
                process.getProcess(),
                new TenantConfig(
                        new TenantIdentifier(null, "appid-abc", "t2"),
                        new EmailPasswordConfig(false),
                        new ThirdPartyConfig(false, null),
                        new PasswordlessConfig(false),
                        null, null, new JsonObject()
                ),
                false
        );
        Multitenancy.addNewOrUpdateAppOrTenant(
                process.getProcess(),
                new TenantConfig(
                        new TenantIdentifier(null, null, "t1"),
                        new EmailPasswordConfig(false),
                        new ThirdPartyConfig(false, null),
                        new PasswordlessConfig(false),
                        null, null, new JsonObject()
                ),
                false
        );
        Multitenancy.addNewOrUpdateAppOrTenant(
                process.getProcess(),
                new TenantConfig(
                        new TenantIdentifier(null, null, "t2"),
                        new EmailPasswordConfig(false),
                        new ThirdPartyConfig(false, null),
                        new PasswordlessConfig(false),
                        null, null, new JsonObject()
                ),
                false
        );
        Multitenancy.addNewOrUpdateAppOrTenant(
                process.getProcess(),
                new TenantConfig(
                        new TenantIdentifier(null, null, "hello"),
                        new EmailPasswordConfig(false),
                        new ThirdPartyConfig(false, null),
                        new PasswordlessConfig(false),
                        null, null, new JsonObject()
                ),
                false
        );

        String[] paths = new String[]{"/test", "/recipe/test", "/appid-abc/recipe/test", "/test/t1", "/t1/t1",
                "/appid-abc/t1/t1"};

        for (String p : paths) {
            Webserver.getInstance(process.getProcess()).addAPI(new WebserverAPI(process.getProcess(), "") {

                private static final long serialVersionUID = 1L;

                @Override
                public boolean checkAPIKey(HttpServletRequest req) {
                    return false;
                }

                @Override
                public String getPath() {
                    return p;
                }

                @Override
                protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                        throws IOException, ServletException {
                    try {
                        super.sendTextResponse(200,
                                this.getTenantIdentifier(req).getConnectionUriDomain() + "," +
                                        this.getTenantIdentifier(req).getAppId() + "," +
                                        this.getTenantIdentifier(req).getTenantId(), resp);
                    } catch (TenantOrAppNotFoundException e) {
                        throw new ServletException(e);
                    }
                }
            });
        }

        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/appid-abc/test", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",abc,public", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/appid-public/public/test", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",public,public", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/appid-Public/public/test", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",public,public", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/appid-abc/test/t1", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",abc,public", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/appid-abc/t2/test", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",abc,t2", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/appid-appid-abc/t2/test", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",appid-abc,t2", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/appid-aBc/t2/test", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",abc,t2", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/appid-/t2/test", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",public,t2", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/test", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",public,public", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/test", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",public,public", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/t2/recipe/test", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",public,t2", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/t1/test", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",public,t1", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/test/t1", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",public,public", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/t1/test/t1", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",public,t1", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/t1/t1", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",public,public", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/t1/t1/t1", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",public,t1", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/public/t1/t1", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",public,public", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/public/t1/t1", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",public,public", response);
        }
        {
            try {
                HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                        "http://localhost:3567/test/t1/t2", new HashMap<>(), 1000, 1000, null,
                        Utils.getCdiVersionStringLatestForTests(), "");
                fail();
            } catch (HttpResponseException e) {
                assertEquals(e.statusCode, 404);
                assertEquals(e.getMessage(), "Http error. Status Code: 404. Message: Not found");
            }
        }
        {
            try {
                HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                        "http://localhost:3567/t2/t1/test/t1", new HashMap<>(), 1000, 1000, null,
                        Utils.getCdiVersionStringLatestForTests(), "");
                fail();
            } catch (HttpResponseException e) {
                assertEquals(e.statusCode, 404);
                assertEquals(e.getMessage(), "Http error. Status Code: 404. Message: Not found");
            }
        }
        {
            try {
                HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                        "http://localhost:3567/t2/t1/t1/t1", new HashMap<>(), 1000, 1000, null,
                        Utils.getCdiVersionStringLatestForTests(), "");
                fail();
            } catch (HttpResponseException e) {
                assertEquals(e.statusCode, 404);
                assertEquals(e.getMessage(), "Http error. Status Code: 404. Message: Not found");
            }
        }
        {
            try {
                HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                        "http://localhost:3567/appid-aBc/t2/random/test", new HashMap<>(), 1000, 1000, null,
                        Utils.getCdiVersionStringLatestForTests(), "");
                fail();
            } catch (HttpResponseException e) {
                assertEquals(e.statusCode, 404);
                assertEquals(e.getMessage(), "Http error. Status Code: 404. Message: Not found");
            }
        }
        {
            try {
                HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                        "http://localhost:3567/random/appid-abc/test/t1", new HashMap<>(), 1000, 1000, null,
                        Utils.getCdiVersionStringLatestForTests(), "");
                fail();
            } catch (HttpResponseException e) {
                assertEquals(e.statusCode, 404);
                assertEquals(e.getMessage(), "Http error. Status Code: 404. Message: Not found");
            }
        }
        {
            try {
                HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                        "http://localhost:3567/appid-abc/appid-abc/appid-abc/test/t1", new HashMap<>(), 1000, 1000,
                        null,
                        Utils.getCdiVersionStringLatestForTests(), "");
                fail();
            } catch (HttpResponseException e) {
                assertEquals(e.statusCode, 404);
                assertEquals(e.getMessage(), "Http error. Status Code: 404. Message: Not found");
            }
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void basicAppIdWithBasePathTesting()
            throws InterruptedException, IOException, HttpResponseException, InvalidProviderConfigException,
            StorageQueryException, FeatureNotEnabledException, TenantOrAppNotFoundException, InvalidConfigException,
            CannotModifyBaseConfigException, BadPermissionException {
        String[] args = {"../"};
        Utils.setValueInConfig("base_path", "base_path");
        TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        Multitenancy.addNewOrUpdateAppOrTenant(
                process.getProcess(),
                new TenantConfig(
                        new TenantIdentifier(null, "abc", null),
                        new EmailPasswordConfig(false),
                        new ThirdPartyConfig(false, null),
                        new PasswordlessConfig(false),
                        null, null, new JsonObject()
                ),
                false
        );
        Multitenancy.addNewOrUpdateAppOrTenant(
                process.getProcess(),
                new TenantConfig(
                        new TenantIdentifier(null, "abc", "t2"),
                        new EmailPasswordConfig(false),
                        new ThirdPartyConfig(false, null),
                        new PasswordlessConfig(false),
                        null, null, new JsonObject()
                ),
                false
        );
        Multitenancy.addNewOrUpdateAppOrTenant(
                process.getProcess(),
                new TenantConfig(
                        new TenantIdentifier(null, "appid-abc", null),
                        new EmailPasswordConfig(false),
                        new ThirdPartyConfig(false, null),
                        new PasswordlessConfig(false),
                        null, null, new JsonObject()
                ),
                false
        );
        Multitenancy.addNewOrUpdateAppOrTenant(
                process.getProcess(),
                new TenantConfig(
                        new TenantIdentifier(null, "appid-abc", "t2"),
                        new EmailPasswordConfig(false),
                        new ThirdPartyConfig(false, null),
                        new PasswordlessConfig(false),
                        null, null, new JsonObject()
                ),
                false
        );
        Multitenancy.addNewOrUpdateAppOrTenant(
                process.getProcess(),
                new TenantConfig(
                        new TenantIdentifier(null, null, "t1"),
                        new EmailPasswordConfig(false),
                        new ThirdPartyConfig(false, null),
                        new PasswordlessConfig(false),
                        null, null, new JsonObject()
                ),
                false
        );
        Multitenancy.addNewOrUpdateAppOrTenant(
                process.getProcess(),
                new TenantConfig(
                        new TenantIdentifier(null, null, "t2"),
                        new EmailPasswordConfig(false),
                        new ThirdPartyConfig(false, null),
                        new PasswordlessConfig(false),
                        null, null, new JsonObject()
                ),
                false
        );
        Multitenancy.addNewOrUpdateAppOrTenant(
                process.getProcess(),
                new TenantConfig(
                        new TenantIdentifier(null, null, "hello"),
                        new EmailPasswordConfig(false),
                        new ThirdPartyConfig(false, null),
                        new PasswordlessConfig(false),
                        null, null, new JsonObject()
                ),
                false
        );

        String[] paths = new String[]{"/test", "/recipe/test", "/appid-abc/recipe/test", "/test/t1", "/t1/t1",
                "/appid-abc/t1/t1"};

        for (String p : paths) {
            Webserver.getInstance(process.getProcess()).addAPI(new WebserverAPI(process.getProcess(), "") {

                private static final long serialVersionUID = 1L;

                @Override
                public boolean checkAPIKey(HttpServletRequest req) {
                    return false;
                }

                @Override
                public String getPath() {
                    return p;
                }

                @Override
                protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                        throws IOException, ServletException {
                    try {
                        super.sendTextResponse(200,
                                this.getTenantIdentifier(req).getConnectionUriDomain() + "," +
                                        this.getTenantIdentifier(req).getAppId() + "," +
                                        this.getTenantIdentifier(req).getTenantId(), resp);
                    } catch (TenantOrAppNotFoundException e) {
                        throw new ServletException(e);
                    }
                }
            });
        }

        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/base_path/appid-abc/test", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",abc,public", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/base_path/appid-abc/t2/test", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",abc,t2", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/base_path/appid-appid-abc/t2/test", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",appid-abc,t2", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/base_path/appid-aBc/t2/test", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",abc,t2", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/base_path/appid-/t2/test", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",public,t2", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/base_path/test", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",public,public", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/base_path/recipe/test", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",public,public", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/base_path/t2/recipe/test", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",public,t2", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/base_path/t1/test", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",public,t1", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/base_path/test/t1", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",public,public", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/base_path/t1/test/t1", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",public,t1", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/base_path/t1/t1", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",public,public", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/base_path/t1/t1/t1", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",public,t1", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/base_path/public/t1/t1", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",public,public", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/base_path/public/t1/t1", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",public,public", response);
        }
        {
            try {
                HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                        "http://localhost:3567/base_path/test/t1/t2", new HashMap<>(), 1000, 1000, null,
                        Utils.getCdiVersionStringLatestForTests(), "");
                fail();
            } catch (HttpResponseException e) {
                assertEquals(e.statusCode, 404);
                assertEquals(e.getMessage(), "Http error. Status Code: 404. Message: Not found");
            }
        }
        {
            try {
                HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                        "http://localhost:3567/base_path/t2/t1/test/t1", new HashMap<>(), 1000, 1000, null,
                        Utils.getCdiVersionStringLatestForTests(), "");
                fail();
            } catch (HttpResponseException e) {
                assertEquals(e.statusCode, 404);
                assertEquals(e.getMessage(), "Http error. Status Code: 404. Message: Not found");
            }
        }
        {
            try {
                HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                        "http://localhost:3567/base_path/t2/t1/t1/t1", new HashMap<>(), 1000, 1000, null,
                        Utils.getCdiVersionStringLatestForTests(), "");
                fail();
            } catch (HttpResponseException e) {
                assertEquals(e.statusCode, 404);
                assertEquals(e.getMessage(), "Http error. Status Code: 404. Message: Not found");
            }
        }
        {
            try {
                HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                        "http://localhost:3567/base_path/random/appid-abc/test/t1", new HashMap<>(), 1000, 1000, null,
                        Utils.getCdiVersionStringLatestForTests(), "");
                fail();
            } catch (HttpResponseException e) {
                assertEquals(e.statusCode, 404);
                assertEquals(e.getMessage(), "Http error. Status Code: 404. Message: Not found");
            }
        }
        {
            try {
                HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                        "http://localhost:3567/base_path/appid-abc/appid-abc/appid-abc/test/t1", new HashMap<>(), 1000,
                        1000,
                        null,
                        Utils.getCdiVersionStringLatestForTests(), "");
                fail();
            } catch (HttpResponseException e) {
                assertEquals(e.statusCode, 404);
                assertEquals(e.getMessage(), "Http error. Status Code: 404. Message: Not found");
            }
        }
        {
            try {
                HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                        "http://localhost:3567/appid-abc/appid-abc/appid-abc/test/t1", new HashMap<>(), 1000,
                        1000,
                        null,
                        Utils.getCdiVersionStringLatestForTests(), "");
                fail();
            } catch (HttpResponseException e) {
                assertEquals(e.statusCode, 404);
            }
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void basicAppIdWithBase2PathTesting()
            throws InterruptedException, IOException, HttpResponseException, InvalidProviderConfigException,
            StorageQueryException, FeatureNotEnabledException, TenantOrAppNotFoundException, InvalidConfigException,
            CannotModifyBaseConfigException, BadPermissionException {
        String[] args = {"../"};
        Utils.setValueInConfig("base_path", "appid-path");
        TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        Multitenancy.addNewOrUpdateAppOrTenant(
                process.getProcess(),
                new TenantConfig(
                        new TenantIdentifier(null, "abc", null),
                        new EmailPasswordConfig(false),
                        new ThirdPartyConfig(false, null),
                        new PasswordlessConfig(false),
                        null, null, new JsonObject()
                ),
                false
        );
        Multitenancy.addNewOrUpdateAppOrTenant(
                process.getProcess(),
                new TenantConfig(
                        new TenantIdentifier(null, "abc", "t2"),
                        new EmailPasswordConfig(false),
                        new ThirdPartyConfig(false, null),
                        new PasswordlessConfig(false),
                        null, null, new JsonObject()
                ),
                false
        );
        Multitenancy.addNewOrUpdateAppOrTenant(
                process.getProcess(),
                new TenantConfig(
                        new TenantIdentifier(null, "appid-abc", null),
                        new EmailPasswordConfig(false),
                        new ThirdPartyConfig(false, null),
                        new PasswordlessConfig(false),
                        null, null, new JsonObject()
                ),
                false
        );
        Multitenancy.addNewOrUpdateAppOrTenant(
                process.getProcess(),
                new TenantConfig(
                        new TenantIdentifier(null, "appid-abc", "t2"),
                        new EmailPasswordConfig(false),
                        new ThirdPartyConfig(false, null),
                        new PasswordlessConfig(false),
                        null, null, new JsonObject()
                ),
                false
        );
        Multitenancy.addNewOrUpdateAppOrTenant(
                process.getProcess(),
                new TenantConfig(
                        new TenantIdentifier(null, null, "t1"),
                        new EmailPasswordConfig(false),
                        new ThirdPartyConfig(false, null),
                        new PasswordlessConfig(false),
                        null, null, new JsonObject()
                ),
                false
        );
        Multitenancy.addNewOrUpdateAppOrTenant(
                process.getProcess(),
                new TenantConfig(
                        new TenantIdentifier(null, null, "t2"),
                        new EmailPasswordConfig(false),
                        new ThirdPartyConfig(false, null),
                        new PasswordlessConfig(false),
                        null, null, new JsonObject()
                ),
                false
        );
        Multitenancy.addNewOrUpdateAppOrTenant(
                process.getProcess(),
                new TenantConfig(
                        new TenantIdentifier(null, null, "hello"),
                        new EmailPasswordConfig(false),
                        new ThirdPartyConfig(false, null),
                        new PasswordlessConfig(false),
                        null, null, new JsonObject()
                ),
                false
        );

        String[] paths = new String[]{"/test", "/recipe/test", "/appid-abc/recipe/test", "/test/t1", "/t1/t1",
                "/appid-abc/t1/t1"};

        for (String p : paths) {
            Webserver.getInstance(process.getProcess()).addAPI(new WebserverAPI(process.getProcess(), "") {

                private static final long serialVersionUID = 1L;

                @Override
                public boolean checkAPIKey(HttpServletRequest req) {
                    return false;
                }

                @Override
                public String getPath() {
                    return p;
                }

                @Override
                protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                        throws IOException, ServletException {
                    try {
                        super.sendTextResponse(200,
                                this.getTenantIdentifier(req).getConnectionUriDomain() + "," +
                                        this.getTenantIdentifier(req).getAppId() + "," +
                                        this.getTenantIdentifier(req).getTenantId(), resp);
                    } catch (TenantOrAppNotFoundException e) {
                        throw new ServletException(e);
                    }
                }
            });
        }

        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/appid-path/appid-abc/test", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",abc,public", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/appid-path/appid-abc/t2/test", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",abc,t2", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/appid-path/appid-appid-abc/t2/test", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",appid-abc,t2", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/appid-path/appid-aBc/t2/test", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",abc,t2", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/appid-path/appid-/t2/test", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",public,t2", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/appid-path/test", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",public,public", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/appid-path/recipe/test", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",public,public", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/appid-path/t2/recipe/test", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",public,t2", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/appid-path/t1/test", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",public,t1", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/appid-path/test/t1", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",public,public", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/appid-path/t1/test/t1", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",public,t1", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/appid-path/t1/t1", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",public,public", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/appid-path/t1/t1/t1", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",public,t1", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/appid-path/public/t1/t1", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",public,public", response);
        }
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/appid-path/public/t1/t1", new HashMap<>(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(",public,public", response);
        }
        {
            try {
                HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                        "http://localhost:3567/appid-path/test/t1/t2", new HashMap<>(), 1000, 1000, null,
                        Utils.getCdiVersionStringLatestForTests(), "");
                fail();
            } catch (HttpResponseException e) {
                assertEquals(e.statusCode, 404);
                assertEquals(e.getMessage(), "Http error. Status Code: 404. Message: Not found");
            }
        }
        {
            try {
                HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                        "http://localhost:3567/appid-path/t2/t1/test/t1", new HashMap<>(), 1000, 1000, null,
                        Utils.getCdiVersionStringLatestForTests(), "");
                fail();
            } catch (HttpResponseException e) {
                assertEquals(e.statusCode, 404);
                assertEquals(e.getMessage(), "Http error. Status Code: 404. Message: Not found");
            }
        }
        {
            try {
                HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                        "http://localhost:3567/appid-path/t2/t1/t1/t1", new HashMap<>(), 1000, 1000, null,
                        Utils.getCdiVersionStringLatestForTests(), "");
                fail();
            } catch (HttpResponseException e) {
                assertEquals(e.statusCode, 404);
                assertEquals(e.getMessage(), "Http error. Status Code: 404. Message: Not found");
            }
        }
        {
            try {
                HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                        "http://localhost:3567/appid-path/random/appid-abc/test/t1", new HashMap<>(), 1000, 1000, null,
                        Utils.getCdiVersionStringLatestForTests(), "");
                fail();
            } catch (HttpResponseException e) {
                assertEquals(e.statusCode, 404);
                assertEquals(e.getMessage(), "Http error. Status Code: 404. Message: Not found");
            }
        }
        {
            try {
                HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                        "http://localhost:3567/appid-path/appid-abc/appid-abc/appid-abc/test/t1", new HashMap<>(), 1000,
                        1000,
                        null,
                        Utils.getCdiVersionStringLatestForTests(), "");
                fail();
            } catch (HttpResponseException e) {
                assertEquals(e.statusCode, 404);
                assertEquals(e.getMessage(), "Http error. Status Code: 404. Message: Not found");
            }
        }
        {
            try {
                HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                        "http://localhost:3567/appid-abc/appid-abc/appid-abc/test/t1", new HashMap<>(), 1000,
                        1000,
                        null,
                        Utils.getCdiVersionStringLatestForTests(), "");
                fail();
            } catch (HttpResponseException e) {
                assertEquals(e.statusCode, 404);
            }
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void tenantNotFoundWithAppIdTest()
            throws InterruptedException, IOException, io.supertokens.httpRequest.HttpResponseException,
            InvalidConfigException,
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
                new TenantConfig(new TenantIdentifier("localhost", "app1", null), new EmailPasswordConfig(false),
                        new ThirdPartyConfig(false, new ThirdPartyConfig.Provider[0]),
                        new PasswordlessConfig(false),
                        null, null, tenantConfig),
                false
        );
        Multitenancy.addNewOrUpdateAppOrTenant(
                process.getProcess(),
                new TenantConfig(new TenantIdentifier("localhost", "app1", "t1"), new EmailPasswordConfig(false),
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
        Webserver.getInstance(process.getProcess()).addAPI(new WebserverAPI(process.getProcess(), "") {

            @Override
            public String getPath() {
                return "/test";
            }

            @Override
            protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException,
                    ServletException {
                try {
                    super.sendTextResponse(200, super.getTenantIdentifier(req).getConnectionUriDomain() + "," +
                                    this.getTenantIdentifier(req).getAppId() + "," +
                                    this.getTenantIdentifier(req).getTenantId(),
                            resp);
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
            assertEquals("localhost,public,public", response);
        }

        {
            try {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://127.0.0.1:3567/appid-app1/test", new JsonObject(), 1000, 1000, null,
                        Utils.getCdiVersionStringLatestForTests(),
                        "abcasdfaliojmo3jenbogweg=-9382923", "");
                fail();
            } catch (HttpResponseException e) {
                assertEquals(e.statusCode, 400);
                assertEquals(e.getMessage(),
                        "Http error. Status Code: 400. Message: AppId or tenantId not found => Tenant with the " +
                                "following connectionURIDomain, appId and tenantId combination not found: (127.0.0" +
                                ".1, " +
                                "app1, public)");
            }
        }

        Multitenancy.addNewOrUpdateAppOrTenant(
                process.getProcess(),
                new TenantConfig(new TenantIdentifier("127.0.0.1", "app1", null), new EmailPasswordConfig(false),
                        new ThirdPartyConfig(false, new ThirdPartyConfig.Provider[0]),
                        new PasswordlessConfig(false),
                        null, null, tenant2Config),
                false
        );
        Multitenancy.addNewOrUpdateAppOrTenant(
                process.getProcess(),
                new TenantConfig(new TenantIdentifier("127.0.0.1", "app1", "t1"), new EmailPasswordConfig(false),
                        new ThirdPartyConfig(false, new ThirdPartyConfig.Provider[0]),
                        new PasswordlessConfig(false),
                        null, null, tenant2Config),
                false
        );

        {
            String response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/appid-app1/t1/test", new JsonObject(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(),
                    "abctijenbogweg=-2438243u98", "");
            assertEquals("localhost,app1,t1", response);
        }

        {
            String response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://127.0.0.1:3567/appid-app1/t1/test", new JsonObject(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(),
                    "abcasdfaliojmo3jenbogweg=-9382923", "");
            assertEquals("127.0.0.1,app1,t1", response);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void tenantNotFoundWithAppIdTest2()
            throws InterruptedException, IOException, io.supertokens.httpRequest.HttpResponseException,
            InvalidConfigException,
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
                new TenantConfig(new TenantIdentifier("localhost", "app1", null), new EmailPasswordConfig(false),
                        new ThirdPartyConfig(false, new ThirdPartyConfig.Provider[0]),
                        new PasswordlessConfig(false),
                        null, null, tenantConfig),
                false
        );
        Multitenancy.addNewOrUpdateAppOrTenant(
                process.getProcess(),
                new TenantConfig(new TenantIdentifier("localhost", "app1", "t1"), new EmailPasswordConfig(false),
                        new ThirdPartyConfig(false, new ThirdPartyConfig.Provider[0]),
                        new PasswordlessConfig(false),
                        null, null, tenantConfig),
                false
        );
        Multitenancy.addNewOrUpdateAppOrTenant(
                process.getProcess(),
                new TenantConfig(new TenantIdentifier(null, "app2", null), new EmailPasswordConfig(false),
                        new ThirdPartyConfig(false, new ThirdPartyConfig.Provider[0]),
                        new PasswordlessConfig(false),
                        null, null, new JsonObject()),
                false
        );
        Multitenancy.addNewOrUpdateAppOrTenant(
                process.getProcess(),
                new TenantConfig(new TenantIdentifier(null, "app2", "t2"), new EmailPasswordConfig(false),
                        new ThirdPartyConfig(false, new ThirdPartyConfig.Provider[0]),
                        new PasswordlessConfig(false),
                        null, null, new JsonObject()),
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
                    super.sendTextResponse(200, super.getTenantIdentifier(req).getConnectionUriDomain() + "," +
                                    this.getTenantIdentifier(req).getAppId() + "," +
                                    this.getTenantIdentifier(req).getTenantId(),
                            resp);
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
            assertEquals("localhost,public,public", response);
        }

        {
            String response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://127.0.0.1:3567/test", new JsonObject(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(),
                    "abcasdfaliojmo3jenbogweg=-9382923", "");
            assertEquals(",public,public", response);
        }
        {
            String response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/appid-app1/t1/test", new JsonObject(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(),
                    "abctijenbogweg=-2438243u98", "");
            assertEquals("localhost,app1,t1", response);
        }

        {
            try {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://127.0.0.1:3567/t1/test", new JsonObject(), 1000, 1000, null,
                        Utils.getCdiVersionStringLatestForTests(),
                        "abcasdfaliojmo3jenbogweg=-9382923", "");
                fail();
            } catch (HttpResponseException e) {
                assertEquals(e.statusCode, 400);
                assertEquals(e.getMessage(),
                        "Http error. Status Code: 400. Message: AppId or tenantId not found => Tenant with the " +
                                "following connectionURIDomain, appId and tenantId combination not found: (, public, " +
                                "t1)");
            }
        }
        {
            try {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://127.0.0.1:3567/appid-app1/t1/test", new JsonObject(), 1000, 1000, null,
                        Utils.getCdiVersionStringLatestForTests(),
                        "abcasdfaliojmo3jenbogweg=-9382923", "");
                fail();
            } catch (HttpResponseException e) {
                assertEquals(e.statusCode, 400);
                assertEquals(e.getMessage(),
                        "Http error. Status Code: 400. Message: AppId or tenantId not found => Tenant with the " +
                                "following connectionURIDomain, appId and tenantId combination not found: (, app1, " +
                                "t1)");
            }
        }
        {
            try {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://127.0.0.1:3567/t2/test", new JsonObject(), 1000, 1000, null,
                        Utils.getCdiVersionStringLatestForTests(),
                        "abcasdfaliojmo3jenbogweg=-9382923", "");
                fail();
            } catch (HttpResponseException e) {
                assertEquals(e.statusCode, 400);
                assertEquals(e.getMessage(),
                        "Http error. Status Code: 400. Message: AppId or tenantId not found => Tenant with the " +
                                "following connectionURIDomain, appId and tenantId combination not found: (, public, " +
                                "t2)");
            }
        }
        {
            String response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://127.0.0.1:3567/appid-app2/t2/test", new JsonObject(), 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(),
                    "", "");
            assertEquals(",app2,t2", response);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void tenantNotFoundWithAppIdTest3()
            throws InterruptedException, IOException, io.supertokens.httpRequest.HttpResponseException,
            InvalidConfigException,
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

        Multitenancy.addNewOrUpdateAppOrTenant(
                process.getProcess(),
                new TenantConfig(
                        new TenantIdentifier("localhost", null, null),
                        new EmailPasswordConfig(false),
                        new ThirdPartyConfig(false, new ThirdPartyConfig.Provider[0]),
                        new PasswordlessConfig(false),
                        null, null, tenantConfig),
                false
        );
        Multitenancy.addNewOrUpdateAppOrTenant(
                process.getProcess(),
                new TenantConfig(
                        new TenantIdentifier("localhost", "app1", "t1"),
                        new EmailPasswordConfig(false),
                        new ThirdPartyConfig(false, new ThirdPartyConfig.Provider[0]),
                        new PasswordlessConfig(false),
                        null, null, tenantConfig),
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
                    super.sendTextResponse(200, super.getTenantIdentifier(req).getConnectionUriDomain() + "," +
                                    this.getTenantIdentifier(req).getAppId() + "," +
                                    this.getTenantIdentifier(req).getTenantId(),
                            resp);
                } catch (TenantOrAppNotFoundException e) {
                    throw new ServletException(e);
                }
            }
        });

        {
            try {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/t2/test", new JsonObject(), 1000, 1000, null,
                        Utils.getCdiVersionStringLatestForTests(),
                        "abctijenbogweg=-2438243u98", "");
                fail();
            } catch (HttpResponseException e) {
                assertEquals(e.statusCode, 400);
                assertEquals(e.getMessage(),
                        "Http error. Status Code: 400. Message: AppId or tenantId not found => Tenant with the " +
                                "following connectionURIDomain, appId and tenantId combination not found: " +
                                "(localhost, " +
                                "public, t2)");
            }
        }

        {
            try {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/appid-app1/test", new JsonObject(), 1000, 1000, null,
                        Utils.getCdiVersionStringLatestForTests(),
                        "abctijenbogweg=-2438243u98", "");
                fail();
            } catch (HttpResponseException e) {
                assertEquals(e.statusCode, 400);
                assertEquals(e.getMessage(),
                        "Http error. Status Code: 400. Message: AppId or tenantId not found => Tenant with the " +
                                "following connectionURIDomain, appId and tenantId combination not found: " +
                                "(localhost, " +
                                "app1, public)");
            }
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
