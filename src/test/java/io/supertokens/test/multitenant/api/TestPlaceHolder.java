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

import io.supertokens.ProcessState;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.test.TestingProcessManager;
import org.junit.Test;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public class TestPlaceHolder {
    @Test
    public void testThatOnlyDefaultConnectionURIAppAndTenantIsAllowedToGetAllTenants() {
//        String[] args = {"../"};
//
//        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
//        FeatureFlagTestContent.getInstance(process.getProcess())
//                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
//        process.startProcess();
//        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
//
//        try {
//            Multitenancy.getAllTenants(new TenantIdentifier("c1", null, null),
//                    process.getProcess());
//            fail();
//        } catch (BadPermissionException e) {
//            assertEquals(
//                    "Only the public tenantId, public appId and default connectionUriDomain is allowed to list all " +
//                            "connectionUriDomains and appIds associated with this core",
//                    e.getMessage());
//        }
//
//        try {
//            Multitenancy.getAllTenants(new TenantIdentifier(null, "a1", null),
//                    process.getProcess());
//            fail();
//        } catch (BadPermissionException e) {
//            assertEquals(
//                    "Only the public tenantId, public appId and default connectionUriDomain is allowed to list all " +
//                            "connectionUriDomains and appIds associated with this core",
//                    e.getMessage());
//        }
//
//        try {
//            Multitenancy.getAllTenants(new TenantIdentifier(null, null, "t1"),
//                    process.getProcess());
//            fail();
//        } catch (BadPermissionException e) {
//            assertEquals(
//                    "Only the public tenantId, public appId and default connectionUriDomain is allowed to list all " +
//                            "connectionUriDomains and appIds associated with this core",
//                    e.getMessage());
//        }
//
//        process.kill();
//        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
