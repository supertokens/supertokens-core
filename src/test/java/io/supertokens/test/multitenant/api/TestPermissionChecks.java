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

package io.supertokens.test.multitenant.api;

import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpResponseException;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.io.IOException;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.*;

public class TestPermissionChecks {
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

    private void createTenant(Main main, TenantIdentifier tenantIdentifier)
            throws TenantOrAppNotFoundException, HttpResponseException, IOException {
        JsonObject coreConfig = new JsonObject();
        StorageLayer.getStorage(new TenantIdentifier(null, null, null), main)
                .modifyConfigToAddANewUserPoolForTesting(coreConfig, 1);
        if (!tenantIdentifier.getConnectionUriDomain().equals(TenantIdentifier.DEFAULT_CONNECTION_URI)) {
            TestMultitenancyAPIHelper.createConnectionUriDomain(
                    main,
                    new TenantIdentifier(null, null, null),
                    tenantIdentifier.getConnectionUriDomain(), true, true, true, coreConfig
            );
        }

        if (!tenantIdentifier.getAppId().equals(TenantIdentifier.DEFAULT_APP_ID)) {
            TestMultitenancyAPIHelper.createApp(
                    main,
                    new TenantIdentifier(tenantIdentifier.getConnectionUriDomain(), null, null),
                    tenantIdentifier.getAppId(), true, true, true, coreConfig
            );
        }

        if (!tenantIdentifier.getTenantId().equals(TenantIdentifier.DEFAULT_TENANT_ID)) {
            TestMultitenancyAPIHelper.createTenant(
                    main,
                    new TenantIdentifier(tenantIdentifier.getConnectionUriDomain(), tenantIdentifier.getAppId(), null),
                    tenantIdentifier.getTenantId(), true, true, true, coreConfig
            );
        }
    }

    @Test
    public void testPermissionsForListConnectionUriDomains() throws Exception {
        TestCase[] testCases = new TestCase[]{
                new TestCase(
                        new TenantIdentifier("127.0.0.1:3567", null, null), null,
                        "Only the public tenantId, public appId and default connectionUriDomain is allowed to list all connectionUriDomains and appIds associated with this core"
                ),
                new TestCase(
                        new TenantIdentifier(null, "a1", null), null,
                        "Only the public tenantId, public appId and default connectionUriDomain is allowed to list all connectionUriDomains and appIds associated with this core"
                ),
                new TestCase(
                        new TenantIdentifier(null, null, "t1"), null,
                        "Only the public tenantId, public appId and default connectionUriDomain is allowed to list all connectionUriDomains and appIds associated with this core"
                ),
                new TestCase(
                        new TenantIdentifier(null, null, null), null, null
                )
        };

        for (TestCase testCase : testCases) {
            Utils.reset();
            String[] args = {"../"};

            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
            FeatureFlagTestContent.getInstance(process.getProcess())
                    .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
            process.startProcess();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

            {
                createTenant(process.getProcess(), testCase.sourceTenant);

                try {
                    TestMultitenancyAPIHelper.listConnectionUriDomains(testCase.sourceTenant, process.getProcess());
                    if (testCase.errorMessage != null) {
                        fail();
                    }
                } catch (HttpResponseException e) {
                    if (testCase.errorMessage == null) {
                        throw e;
                    }
                    assertTrue(e.getMessage().contains(testCase.errorMessage));
                }
            }

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }
    }

    @Test
    public void testPermissionsForListApps() throws Exception {
        TestCase[] testCases = new TestCase[]{
                new TestCase(
                        new TenantIdentifier("127.0.0.1:3567", "a1", null), null,
                        "Only the public tenantId and public appId is allowed to list all apps associated with this connection uri domain"
                ),
                new TestCase(
                        new TenantIdentifier("127.0.0.1:3567", null, "t1"), null,
                        "Only the public tenantId and public appId is allowed to list all apps associated with this connection uri domain"
                ),
                new TestCase(
                        new TenantIdentifier(null, "a1", null), null,
                        "Only the public tenantId and public appId is allowed to list all apps associated with this connection uri domain"
                ),
                new TestCase(
                        new TenantIdentifier(null, null, "t1"), null,
                        "Only the public tenantId and public appId is allowed to list all apps associated with this connection uri domain"
                ),
                new TestCase(
                        new TenantIdentifier(null, null, null), null, null
                ),
                new TestCase(
                        new TenantIdentifier("127.0.0.1:3567", null, null), null, null
                )
        };

        for (TestCase testCase : testCases) {
            Utils.reset();
            String[] args = {"../"};

            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
            FeatureFlagTestContent.getInstance(process.getProcess())
                    .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
            process.startProcess();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

            {
                createTenant(process.getProcess(), testCase.sourceTenant);

                try {
                    TestMultitenancyAPIHelper.listApps(testCase.sourceTenant, process.getProcess());
                    if (testCase.errorMessage != null) {
                        fail();
                    }
                } catch (HttpResponseException e) {
                    if (testCase.errorMessage == null) {
                        throw e;
                    }
                    assertTrue(e.getMessage().contains(testCase.errorMessage));
                }
            }

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }
    }

    @Test
    public void testPermissionsForListTenants() throws Exception {
        TestCase[] testCases = new TestCase[]{
                new TestCase(
                        new TenantIdentifier("127.0.0.1:3567", "a1", "t1"), null,
                        "Only the public tenantId is allowed to list all tenants associated with this app"
                ),
                new TestCase(
                        new TenantIdentifier("127.0.0.1:3567", null, "t1"), null,
                        "Only the public tenantId is allowed to list all tenants associated with this app"
                ),
                new TestCase(
                        new TenantIdentifier(null, null, "t1"), null,
                        "Only the public tenantId is allowed to list all tenants associated with this app"
                ),
                new TestCase(
                        new TenantIdentifier(null, null, null), null, null
                ),
                new TestCase(
                        new TenantIdentifier("127.0.0.1:3567", null, null), null, null
                ),
                new TestCase(
                        new TenantIdentifier(null, "a1", null), null, null
                ),
                new TestCase(
                        new TenantIdentifier("127.0.0.1:3567", "a1", null), null, null
                )
        };

        for (TestCase testCase : testCases) {
            Utils.reset();
            String[] args = {"../"};

            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
            FeatureFlagTestContent.getInstance(process.getProcess())
                    .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
            process.startProcess();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

            {
                createTenant(process.getProcess(), testCase.sourceTenant);

                try {
                    TestMultitenancyAPIHelper.listTenants(testCase.sourceTenant, process.getProcess());
                    if (testCase.errorMessage != null) {
                        fail();
                    }
                } catch (HttpResponseException e) {
                    if (testCase.errorMessage == null) {
                        throw e;
                    }
                    assertTrue(e.getMessage().contains(testCase.errorMessage));
                }
            }

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }
    }

    @Test
    public void testPermissionsForCreateOrUpdateConnectionUriDomain() throws Exception {
        TestCase[] testCases = new TestCase[]{
                new TestCase(
                        new TenantIdentifier(null, null, null),
                        new TenantIdentifier("127.0.0.1:3567", null, null),
                        null
                ),
                new TestCase(
                        new TenantIdentifier("127.0.0.1:3567", null, null),
                        new TenantIdentifier("127.0.0.1:3567", null, null),
                        null
                ),
                new TestCase(
                        new TenantIdentifier("127.0.0.1:3567", null, null),
                        new TenantIdentifier("localhost.org:3567", null, null),
                        "You must use the default or same connectionUriDomain to create/update a connectionUriDomain"
                ),
                new TestCase(
                        new TenantIdentifier("127.0.0.1:3567", null, null),
                        new TenantIdentifier(null, null, null),
                        "Cannot modify base config"
                )
        };

        for (TestCase testCase : testCases) {
            Utils.reset();
            String[] args = {"../"};

            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
            FeatureFlagTestContent.getInstance(process.getProcess())
                    .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
            process.startProcess();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

            {
                createTenant(process.getProcess(), testCase.sourceTenant);

                try {
                    JsonObject coreConfig = new JsonObject();
                    StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                            .modifyConfigToAddANewUserPoolForTesting(coreConfig, 2);

                    TestMultitenancyAPIHelper.createConnectionUriDomain(process.getProcess(),
                            testCase.sourceTenant,
                            testCase.targetTenant.getConnectionUriDomain(),
                            true, true, true, coreConfig
                    );
                    if (testCase.errorMessage != null) {
                        fail();
                    }
                } catch (HttpResponseException e) {
                    if (testCase.errorMessage == null) {
                        throw e;
                    }
                    assertTrue(e.getMessage().contains(testCase.errorMessage));
                }
            }

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }
    }

    @Test
    public void testPermissionsForCreateOrUpdateApp() throws Exception {
        TestCase[] testCases = new TestCase[]{
                new TestCase(
                        new TenantIdentifier(null, null, null),
                        new TenantIdentifier(null, "a1", null),
                        null
                ),
                new TestCase(
                        new TenantIdentifier(null, "a1", null),
                        new TenantIdentifier(null, "a1", null),
                        null
                ),
                new TestCase(
                        new TenantIdentifier("127.0.0.1:3567", null, null),
                        new TenantIdentifier("127.0.0.1:3567", "a1", null),
                        null
                ),
                new TestCase(
                        new TenantIdentifier("127.0.0.1:3567", "a1", null),
                        new TenantIdentifier("127.0.0.1:3567", "a1", null),
                        null
                ),
                new TestCase(
                        new TenantIdentifier(null, null, "t1"),
                        new TenantIdentifier(null, "a1", null),
                        "You must use the public tenantId and, public or same appId to add/update an app"
                ),
                new TestCase(
                        new TenantIdentifier("127.0.0.1:3567", null, "t1"),
                        new TenantIdentifier("127.0.0.1:3567", "a1", null),
                        "You must use the public tenantId and, public or same appId to add/update an app"
                ),
                new TestCase(
                        new TenantIdentifier(null, "a1", null),
                        new TenantIdentifier(null, "a2", null),
                        "You must use the public tenantId and, public or same appId to add/update an app"
                ),
                new TestCase(
                        new TenantIdentifier("127.0.0.1:3567", "a1", null),
                        new TenantIdentifier("127.0.0.1:3567", "a2", null),
                        "You must use the public tenantId and, public or same appId to add/update an app"
                ),
        };

        for (TestCase testCase : testCases) {
            Utils.reset();
            String[] args = {"../"};

            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
            FeatureFlagTestContent.getInstance(process.getProcess())
                    .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
            process.startProcess();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

            {
                createTenant(process.getProcess(), testCase.sourceTenant);

                try {
                    JsonObject coreConfig = new JsonObject();
                    StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                            .modifyConfigToAddANewUserPoolForTesting(coreConfig, 2);

                    TestMultitenancyAPIHelper.createApp(process.getProcess(),
                            testCase.sourceTenant,
                            testCase.targetTenant.getAppId(),
                            true, true, true, coreConfig
                    );
                    if (testCase.errorMessage != null) {
                        fail();
                    }
                } catch (HttpResponseException e) {
                    if (testCase.errorMessage == null) {
                        throw e;
                    }
                    assertTrue(e.getMessage().contains(testCase.errorMessage));
                }
            }

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }
    }

    @Test
    public void testPermissionsForCreateOrUpdateTenant() throws Exception {
        TestCase[] testCases = new TestCase[]{
                new TestCase(
                        new TenantIdentifier(null, null, null),
                        new TenantIdentifier(null, null, "t1"),
                        null
                ),
                new TestCase(
                        new TenantIdentifier(null, null, "t1"),
                        new TenantIdentifier(null, null, "t1"),
                        null
                ),
                new TestCase(
                        new TenantIdentifier(null, "a1", null),
                        new TenantIdentifier(null, "a1", "t1"),
                        null
                ),
                new TestCase(
                        new TenantIdentifier(null, "a1", "t1"),
                        new TenantIdentifier(null, "a1", "t1"),
                        null
                ),
                new TestCase(
                        new TenantIdentifier("127.0.0.1:3567", "a1", null),
                        new TenantIdentifier("127.0.0.1:3567", "a1", "t1"),
                        null
                ),
                new TestCase(
                        new TenantIdentifier("127.0.0.1:3567", "a1", "t1"),
                        new TenantIdentifier("127.0.0.1:3567", "a1", "t1"),
                        null
                ),
                new TestCase(
                        new TenantIdentifier(null, null, "t1"),
                        new TenantIdentifier(null, null, "t2"),
                        "You must use the public or same tenantId to add/update a tenant"
                ),
                new TestCase(
                        new TenantIdentifier(null, "a1", "t1"),
                        new TenantIdentifier(null, "a1", "t2"),
                        "You must use the public or same tenantId to add/update a tenant"
                ),
                new TestCase(
                        new TenantIdentifier("127.0.0.1:3567", "a1", "t1"),
                        new TenantIdentifier("127.0.0.1:3567", "a1", "t2"),
                        "You must use the public or same tenantId to add/update a tenant"
                ),
        };

        for (TestCase testCase : testCases) {
            Utils.reset();
            String[] args = {"../"};

            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
            FeatureFlagTestContent.getInstance(process.getProcess())
                    .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
            process.startProcess();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

            {
                createTenant(process.getProcess(), testCase.sourceTenant);

                try {
                    JsonObject coreConfig = new JsonObject();
                    StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                            .modifyConfigToAddANewUserPoolForTesting(coreConfig, 2);

                    TestMultitenancyAPIHelper.createTenant(process.getProcess(),
                            testCase.sourceTenant,
                            testCase.targetTenant.getTenantId(),
                            true, true, true, coreConfig
                    );
                    if (testCase.errorMessage != null) {
                        fail();
                    }
                } catch (HttpResponseException e) {
                    if (testCase.errorMessage == null) {
                        throw e;
                    }
                    assertTrue(e.getMessage().contains(testCase.errorMessage));
                }
            }

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }
    }

    @Test
    public void testPermissionsForDeleteConnectionUriDomain() throws Exception {
        TestCase[] testCases = new TestCase[]{
                new TestCase(
                        new TenantIdentifier(null, null, null),
                        new TenantIdentifier("127.0.0.1:3567", null, null),
                        null
                ),
                new TestCase(
                        new TenantIdentifier(null, null, null),
                        new TenantIdentifier("", null, null),
                        "Cannot delete the default connection uri domain"
                ),
                new TestCase(
                        new TenantIdentifier("127.0.0.1:3567", null, null),
                        new TenantIdentifier("127.0.0.1:3567", null, null),
                        "Only the public tenantId, public appId and default connectionUriDomain is allowed to delete a connectionUriDomain"
                ),
        };

        for (TestCase testCase : testCases) {
            Utils.reset();
            String[] args = {"../"};

            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
            FeatureFlagTestContent.getInstance(process.getProcess())
                    .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
            process.startProcess();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

            {
                createTenant(process.getProcess(), testCase.sourceTenant);

                try {
                    TestMultitenancyAPIHelper.deleteConnectionUriDomain(testCase.sourceTenant,
                            testCase.targetTenant.getConnectionUriDomain(), process.getProcess());
                    if (testCase.errorMessage != null) {
                        fail();
                    }
                } catch (HttpResponseException e) {
                    if (testCase.errorMessage == null) {
                        throw e;
                    }
                    assertTrue(e.getMessage().contains(testCase.errorMessage));
                }
            }

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }
    }

    @Test
    public void testPermissionsForDeleteApp() throws Exception {
        TestCase[] testCases = new TestCase[]{
                new TestCase(
                        new TenantIdentifier(null, null, null),
                        new TenantIdentifier(null, "a1", null),
                        null
                ),
                new TestCase(
                        new TenantIdentifier("127.0.0.1:3567", null, null),
                        new TenantIdentifier("127.0.0.1:3567", "a1", null),
                        null
                ),
                new TestCase(
                        new TenantIdentifier(null, null, "t1"),
                        new TenantIdentifier(null, "a1", null),
                        "Only the public tenantId and public appId is allowed to delete an app"
                ),
                new TestCase(
                        new TenantIdentifier(null, "a1", null),
                        new TenantIdentifier(null, "a1", null),
                        "Only the public tenantId and public appId is allowed to delete an app"
                ),
                new TestCase(
                        new TenantIdentifier("127.0.0.1:3567", null, "t1"),
                        new TenantIdentifier("127.0.0.1:3567", "a1", null),
                        "Only the public tenantId and public appId is allowed to delete an app"
                ),
                new TestCase(
                        new TenantIdentifier("127.0.0.1:3567", "a1", null),
                        new TenantIdentifier("127.0.0.1:3567", "a1", null),
                        "Only the public tenantId and public appId is allowed to delete an app"
                ),
                new TestCase(
                        new TenantIdentifier(null, null, null),
                        new TenantIdentifier(null, "public", null),
                        "Cannot delete the public app, use remove connection uri domain API instead"
                ),
        };

        for (TestCase testCase : testCases) {
            Utils.reset();
            String[] args = {"../"};

            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
            FeatureFlagTestContent.getInstance(process.getProcess())
                    .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
            process.startProcess();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

            {
                createTenant(process.getProcess(), testCase.sourceTenant);

                try {
                    TestMultitenancyAPIHelper.deleteApp(testCase.sourceTenant, testCase.targetTenant.getAppId(),
                            process.getProcess());
                    if (testCase.errorMessage != null) {
                        fail();
                    }
                } catch (HttpResponseException e) {
                    if (testCase.errorMessage == null) {
                        throw e;
                    }
                    assertTrue(e.getMessage().contains(testCase.errorMessage));
                }
            }

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }
    }

    @Test
    public void testPermissionsForDeleteTenant() throws Exception {
        TestCase[] testCases = new TestCase[]{
                new TestCase(
                        new TenantIdentifier(null, null, null),
                        new TenantIdentifier(null, null, "t1"),
                        null
                ),
                new TestCase(
                        new TenantIdentifier(null, "a1", null),
                        new TenantIdentifier(null, "a1", "t1"),
                        null
                ),
                new TestCase(
                        new TenantIdentifier("127.0.0.1:3567", "a1", null),
                        new TenantIdentifier("127.0.0.1:3567", "a1", "t1"),
                        null
                ),
                new TestCase(
                        new TenantIdentifier(null, null, "t1"),
                        new TenantIdentifier(null, null, "t2"),
                        "Only the public tenantId is allowed to delete a tenant"
                ),
                new TestCase(
                        new TenantIdentifier(null, "a1", "t1"),
                        new TenantIdentifier(null, "a1", "t2"),
                        "Only the public tenantId is allowed to delete a tenant"
                ),
                new TestCase(
                        new TenantIdentifier("127.0.0.1:3567", "a1", "t1"),
                        new TenantIdentifier("127.0.0.1:3567", "a1", "t2"),
                        "Only the public tenantId is allowed to delete a tenant"
                ),
                new TestCase(
                        new TenantIdentifier(null, null, null),
                        new TenantIdentifier(null, null, "public"),
                        "Cannot delete public tenant, use remove app API instead"
                ),
                new TestCase(
                        new TenantIdentifier(null, "a1", null),
                        new TenantIdentifier(null, "a1", "public"),
                        "Cannot delete public tenant, use remove app API instead"
                ),
                new TestCase(
                        new TenantIdentifier("127.0.0.1:3567", "a1", null),
                        new TenantIdentifier("127.0.0.1:3567", "a1", "public"),
                        "Cannot delete public tenant, use remove app API instead"
                )
        };

        for (TestCase testCase : testCases) {
            Utils.reset();
            String[] args = {"../"};

            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
            FeatureFlagTestContent.getInstance(process.getProcess())
                    .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
            process.startProcess();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

            {
                createTenant(process.getProcess(), testCase.sourceTenant);

                try {
                    TestMultitenancyAPIHelper.deleteTenant(testCase.sourceTenant, testCase.targetTenant.getTenantId(),
                            process.getProcess());
                    if (testCase.errorMessage != null) {
                        fail();
                    }
                } catch (HttpResponseException e) {
                    if (testCase.errorMessage == null) {
                        throw e;
                    }
                    assertTrue(e.getMessage().contains(testCase.errorMessage));
                }
            }

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }
    }

    private static class TestCase {
        public final TenantIdentifier sourceTenant;
        public final TenantIdentifier targetTenant;
        public final String errorMessage;

        public TestCase(TenantIdentifier sourceTenant, TenantIdentifier targetTenant, String errorMessage) {
            this.sourceTenant = sourceTenant;
            this.targetTenant = targetTenant;
            this.errorMessage = errorMessage;
        }
    }
}
