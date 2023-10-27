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
import io.supertokens.ProcessState;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.multitenancy.exception.CannotModifyBaseConfigException;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.thirdparty.InvalidProviderConfigException;
import io.supertokens.utils.SemVer;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

public class TestFirstFactorResponseInSignInUpAPIs {
    TestingProcessManager.TestingProcess process;

    @AfterClass
    public static void afterTesting() {
        Utils.afterTesting();
    }

    @After
    public void afterEach() throws InterruptedException {
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Before
    public void beforeEach() throws InterruptedException, InvalidProviderConfigException,
            StorageQueryException, FeatureNotEnabledException, TenantOrAppNotFoundException, IOException,
            InvalidConfigException, CannotModifyBaseConfigException, BadPermissionException {
        Utils.reset();

        String[] args = {"../"};

        this.process = TestingProcessManager.start(args);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY, EE_FEATURES.MFA});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
    }

    @Test
    public void testEmailPasswordSignUp() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject config = new JsonObject();
        StorageLayer.getBaseStorage(process.getProcess()).modifyConfigToAddANewUserPoolForTesting(config, 1);
        TenantIdentifier t1 = new TenantIdentifier(null, null, "t1");

        { // first factors not configured
            TestMultitenancyAPIHelper.createTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, null, null),
                    "t1", true, true, true,
                    null, true, null, false, null,
                    config, SemVer.v4_1);

            JsonObject response = TestMultitenancyAPIHelper.epSignUpAndGetResponse(t1, "test1@example.com", "password", process.getProcess(), SemVer.v4_1);
            assertTrue(response.has("tenantHasFirstFactors"));
            assertFalse(response.get("tenantHasFirstFactors").getAsBoolean());
        }

        { // first factors configured to empty array
            TestMultitenancyAPIHelper.createTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, null, null),
                    "t1", true, true, true,
                    null, true, new String[]{}, false, null,
                    config, SemVer.v4_1);

            JsonObject response = TestMultitenancyAPIHelper.epSignUpAndGetResponse(t1, "test2@example.com", "password", process.getProcess(), SemVer.v4_1);
            assertTrue(response.has("tenantHasFirstFactors"));
            assertTrue(response.get("tenantHasFirstFactors").getAsBoolean());
            assertFalse(response.get("isValidFirstFactor").getAsBoolean());
        }

        { // first factors configured, does not contain emailpassword
            TestMultitenancyAPIHelper.createTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, null, null),
                    "t1", true, true, true,
                    null, true, new String[]{"thirdparty"}, false, null,
                    config, SemVer.v4_1);

            JsonObject response = TestMultitenancyAPIHelper.epSignUpAndGetResponse(t1, "test3@example.com", "password", process.getProcess(), SemVer.v4_1);
            assertTrue(response.has("tenantHasFirstFactors"));
            assertTrue(response.get("tenantHasFirstFactors").getAsBoolean());
            assertFalse(response.get("isValidFirstFactor").getAsBoolean());
        }

        { // first factors configured, contains emailpassword
            TestMultitenancyAPIHelper.createTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, null, null),
                    "t1", true, true, true,
                    null, true, new String[]{"emailpassword"}, false, null,
                    config, SemVer.v4_1);

            JsonObject response = TestMultitenancyAPIHelper.epSignUpAndGetResponse(t1, "test4@example.com", "password", process.getProcess(), SemVer.v4_1);
            assertTrue(response.has("tenantHasFirstFactors"));
            assertTrue(response.get("tenantHasFirstFactors").getAsBoolean());
            assertTrue(response.get("isValidFirstFactor").getAsBoolean());
        }
    }

    @Test
    public void testEmailPasswordSignIn() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject config = new JsonObject();
        StorageLayer.getBaseStorage(process.getProcess()).modifyConfigToAddANewUserPoolForTesting(config, 1);
        TenantIdentifier t1 = new TenantIdentifier(null, null, "t1");

        { // create tenant and user
            TestMultitenancyAPIHelper.createTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, null, null),
                    "t1", true, true, true,
                    null, true, null, false, null,
                    config, SemVer.v4_1);

            TestMultitenancyAPIHelper.epSignUpAndGetResponse(t1, "test@example.com", "password", process.getProcess(), SemVer.v4_1);
        }

        { // first factors not configured
            TestMultitenancyAPIHelper.createTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, null, null),
                    "t1", true, true, true,
                    null, true, null, false, null,
                    config, SemVer.v4_1);

            JsonObject response = TestMultitenancyAPIHelper.epSignInAndGetResponse(t1, "test@example.com", "password", process.getProcess(), SemVer.v4_1);
            assertTrue(response.has("tenantHasFirstFactors"));
            assertFalse(response.get("tenantHasFirstFactors").getAsBoolean());
        }

        { // first factors configured to empty array
            TestMultitenancyAPIHelper.createTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, null, null),
                    "t1", true, true, true,
                    null, true, new String[]{}, false, null,
                    config, SemVer.v4_1);

            JsonObject response = TestMultitenancyAPIHelper.epSignInAndGetResponse(t1, "test@example.com", "password", process.getProcess(), SemVer.v4_1);
            assertTrue(response.has("tenantHasFirstFactors"));
            assertTrue(response.get("tenantHasFirstFactors").getAsBoolean());
            assertFalse(response.get("isValidFirstFactor").getAsBoolean());
        }

        { // first factors configured, does not contain emailpassword
            TestMultitenancyAPIHelper.createTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, null, null),
                    "t1", true, true, true,
                    null, true, new String[]{"thirdparty"}, false, null,
                    config, SemVer.v4_1);

            JsonObject response = TestMultitenancyAPIHelper.epSignInAndGetResponse(t1, "test@example.com", "password", process.getProcess(), SemVer.v4_1);
            assertTrue(response.has("tenantHasFirstFactors"));
            assertTrue(response.get("tenantHasFirstFactors").getAsBoolean());
            assertFalse(response.get("isValidFirstFactor").getAsBoolean());
        }

        { // first factors configured, contains emailpassword
            TestMultitenancyAPIHelper.createTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, null, null),
                    "t1", true, true, true,
                    null, true, new String[]{"emailpassword"}, false, null,
                    config, SemVer.v4_1);

            JsonObject response = TestMultitenancyAPIHelper.epSignInAndGetResponse(t1, "test@example.com", "password", process.getProcess(), SemVer.v4_1);
            assertTrue(response.has("tenantHasFirstFactors"));
            assertTrue(response.get("tenantHasFirstFactors").getAsBoolean());
            assertTrue(response.get("isValidFirstFactor").getAsBoolean());
        }
    }

    @Test
    public void testThirdPartySignInUp() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject config = new JsonObject();
        StorageLayer.getBaseStorage(process.getProcess()).modifyConfigToAddANewUserPoolForTesting(config, 1);
        TenantIdentifier t1 = new TenantIdentifier(null, null, "t1");

        { // first factors not configured
            TestMultitenancyAPIHelper.createTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, null, null),
                    "t1", true, true, true,
                    null, true, null, false, null,
                    config, SemVer.v4_1);

            JsonObject response = TestMultitenancyAPIHelper.tpSignInUpAndGetResponse(t1, "google", "gid1", "test1@example.com", process.getProcess(), SemVer.v4_1);
            assertTrue(response.has("tenantHasFirstFactors"));
            assertFalse(response.get("tenantHasFirstFactors").getAsBoolean());
        }

        { // first factors configured to empty array
            TestMultitenancyAPIHelper.createTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, null, null),
                    "t1", true, true, true,
                    null, true, new String[]{}, false, null,
                    config, SemVer.v4_1);

            JsonObject response = TestMultitenancyAPIHelper.tpSignInUpAndGetResponse(t1, "google", "gid1", "test1@example.com", process.getProcess(), SemVer.v4_1);
            assertTrue(response.has("tenantHasFirstFactors"));
            assertTrue(response.get("tenantHasFirstFactors").getAsBoolean());
            assertFalse(response.get("isValidFirstFactor").getAsBoolean());
        }

        { // first factors configured, does not contain thirdparty
            TestMultitenancyAPIHelper.createTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, null, null),
                    "t1", true, true, true,
                    null, true, new String[]{"emailpassword"}, false, null,
                    config, SemVer.v4_1);

            JsonObject response = TestMultitenancyAPIHelper.tpSignInUpAndGetResponse(t1, "google", "gid1", "test1@example.com", process.getProcess(), SemVer.v4_1);
            assertTrue(response.has("tenantHasFirstFactors"));
            assertTrue(response.get("tenantHasFirstFactors").getAsBoolean());
            assertFalse(response.get("isValidFirstFactor").getAsBoolean());
        }

        { // first factors configured, contains thirdparty
            TestMultitenancyAPIHelper.createTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, null, null),
                    "t1", true, true, true,
                    null, true, new String[]{"thirdparty"}, false, null,
                    config, SemVer.v4_1);

            JsonObject response = TestMultitenancyAPIHelper.tpSignInUpAndGetResponse(t1, "google", "gid1", "test1@example.com", process.getProcess(), SemVer.v4_1);
            assertTrue(response.has("tenantHasFirstFactors"));
            assertTrue(response.get("tenantHasFirstFactors").getAsBoolean());
            assertTrue(response.get("isValidFirstFactor").getAsBoolean());
        }
    }

    @Test
    public void testPasswordlessEmailOTP() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject config = new JsonObject();
        StorageLayer.getBaseStorage(process.getProcess()).modifyConfigToAddANewUserPoolForTesting(config, 1);
        TenantIdentifier t1 = new TenantIdentifier(null, null, "t1");

        { // first factors not configured
            TestMultitenancyAPIHelper.createTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, null, null),
                    "t1", true, true, true,
                    null, true, null, false, null,
                    config, SemVer.v4_1);

            JsonObject response = TestMultitenancyAPIHelper.plSignInUpWithEmailOTP(t1, "test1@example.com", process.getProcess(), SemVer.v4_1);
            assertTrue(response.has("tenantHasFirstFactors"));
            assertFalse(response.get("tenantHasFirstFactors").getAsBoolean());
        }

        { // first factors configured to empty array
            TestMultitenancyAPIHelper.createTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, null, null),
                    "t1", true, true, true,
                    null, true, new String[]{}, false, null,
                    config, SemVer.v4_1);

            JsonObject response = TestMultitenancyAPIHelper.plSignInUpWithEmailOTP(t1, "test2@example.com", process.getProcess(), SemVer.v4_1);
            assertTrue(response.has("tenantHasFirstFactors"));
            assertTrue(response.get("tenantHasFirstFactors").getAsBoolean());
            assertFalse(response.get("isValidFirstFactor").getAsBoolean());
        }

        { // first factors configured, does not contain otp-email
            TestMultitenancyAPIHelper.createTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, null, null),
                    "t1", true, true, true,
                    null, true, new String[]{"thirdparty"}, false, null,
                    config, SemVer.v4_1);

            JsonObject response = TestMultitenancyAPIHelper.plSignInUpWithEmailOTP(t1, "test3@example.com", process.getProcess(), SemVer.v4_1);
            assertTrue(response.has("tenantHasFirstFactors"));
            assertTrue(response.get("tenantHasFirstFactors").getAsBoolean());
            assertFalse(response.get("isValidFirstFactor").getAsBoolean());
        }

        { // first factors configured, contains otp-email
            TestMultitenancyAPIHelper.createTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, null, null),
                    "t1", true, true, true,
                    null, true, new String[]{"otp-email"}, false, null,
                    config, SemVer.v4_1);

            JsonObject response = TestMultitenancyAPIHelper.plSignInUpWithEmailOTP(t1, "test4@example.com", process.getProcess(), SemVer.v4_1);
            assertTrue(response.has("tenantHasFirstFactors"));
            assertTrue(response.get("tenantHasFirstFactors").getAsBoolean());
            assertTrue(response.get("isValidFirstFactor").getAsBoolean());
        }
    }

    @Test
    public void testPasswordlessPhoneOTP() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject config = new JsonObject();
        StorageLayer.getBaseStorage(process.getProcess()).modifyConfigToAddANewUserPoolForTesting(config, 1);
        TenantIdentifier t1 = new TenantIdentifier(null, null, "t1");

        { // first factors not configured
            TestMultitenancyAPIHelper.createTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, null, null),
                    "t1", true, true, true,
                    null, true, null, false, null,
                    config, SemVer.v4_1);

            JsonObject response = TestMultitenancyAPIHelper.plSignInUpWithPhoneOTP(t1, "+919876543210", process.getProcess(), SemVer.v4_1);
            assertTrue(response.has("tenantHasFirstFactors"));
            assertFalse(response.get("tenantHasFirstFactors").getAsBoolean());
        }

        { // first factors configured to empty array
            TestMultitenancyAPIHelper.createTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, null, null),
                    "t1", true, true, true,
                    null, true, new String[]{}, false, null,
                    config, SemVer.v4_1);

            JsonObject response = TestMultitenancyAPIHelper.plSignInUpWithPhoneOTP(t1, "+919876543210", process.getProcess(), SemVer.v4_1);
            assertTrue(response.has("tenantHasFirstFactors"));
            assertTrue(response.get("tenantHasFirstFactors").getAsBoolean());
            assertFalse(response.get("isValidFirstFactor").getAsBoolean());
        }

        { // first factors configured, does not contain otp-phone
            TestMultitenancyAPIHelper.createTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, null, null),
                    "t1", true, true, true,
                    null, true, new String[]{"thirdparty"}, false, null,
                    config, SemVer.v4_1);

            JsonObject response = TestMultitenancyAPIHelper.plSignInUpWithPhoneOTP(t1, "+919876543210", process.getProcess(), SemVer.v4_1);
            assertTrue(response.has("tenantHasFirstFactors"));
            assertTrue(response.get("tenantHasFirstFactors").getAsBoolean());
            assertFalse(response.get("isValidFirstFactor").getAsBoolean());
        }

        { // first factors configured, contains otp-phone
            TestMultitenancyAPIHelper.createTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, null, null),
                    "t1", true, true, true,
                    null, true, new String[]{"otp-phone"}, false, null,
                    config, SemVer.v4_1);

            JsonObject response = TestMultitenancyAPIHelper.plSignInUpWithPhoneOTP(t1, "+919876543210", process.getProcess(), SemVer.v4_1);
            assertTrue(response.has("tenantHasFirstFactors"));
            assertTrue(response.get("tenantHasFirstFactors").getAsBoolean());
            assertTrue(response.get("isValidFirstFactor").getAsBoolean());
        }
    }

    @Test
    public void testPasswordlessEmailLink() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject config = new JsonObject();
        StorageLayer.getBaseStorage(process.getProcess()).modifyConfigToAddANewUserPoolForTesting(config, 1);
        TenantIdentifier t1 = new TenantIdentifier(null, null, "t1");

        { // first factors not configured
            TestMultitenancyAPIHelper.createTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, null, null),
                    "t1", true, true, true,
                    null, true, null, false, null,
                    config, SemVer.v4_1);

            JsonObject response = TestMultitenancyAPIHelper.plSignInUpWithEmailLink(t1, "test1@example.com", process.getProcess(), SemVer.v4_1);
            assertTrue(response.has("tenantHasFirstFactors"));
            assertFalse(response.get("tenantHasFirstFactors").getAsBoolean());
        }

        { // first factors configured to empty array
            TestMultitenancyAPIHelper.createTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, null, null),
                    "t1", true, true, true,
                    null, true, new String[]{}, false, null,
                    config, SemVer.v4_1);

            JsonObject response = TestMultitenancyAPIHelper.plSignInUpWithEmailLink(t1, "test2@example.com", process.getProcess(), SemVer.v4_1);
            assertTrue(response.has("tenantHasFirstFactors"));
            assertTrue(response.get("tenantHasFirstFactors").getAsBoolean());
            assertFalse(response.get("isValidFirstFactor").getAsBoolean());
        }

        { // first factors configured, does not contain link-email
            TestMultitenancyAPIHelper.createTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, null, null),
                    "t1", true, true, true,
                    null, true, new String[]{"thirdparty"}, false, null,
                    config, SemVer.v4_1);

            JsonObject response = TestMultitenancyAPIHelper.plSignInUpWithEmailLink(t1, "test3@example.com", process.getProcess(), SemVer.v4_1);
            assertTrue(response.has("tenantHasFirstFactors"));
            assertTrue(response.get("tenantHasFirstFactors").getAsBoolean());
            assertFalse(response.get("isValidFirstFactor").getAsBoolean());
        }

        { // first factors configured, contains link-email
            TestMultitenancyAPIHelper.createTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, null, null),
                    "t1", true, true, true,
                    null, true, new String[]{"link-email"}, false, null,
                    config, SemVer.v4_1);

            JsonObject response = TestMultitenancyAPIHelper.plSignInUpWithEmailLink(t1, "test4@example.com", process.getProcess(), SemVer.v4_1);
            assertTrue(response.has("tenantHasFirstFactors"));
            assertTrue(response.get("tenantHasFirstFactors").getAsBoolean());
            assertTrue(response.get("isValidFirstFactor").getAsBoolean());
        }
    }

    @Test
    public void testPasswordlessPhoneLink() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject config = new JsonObject();
        StorageLayer.getBaseStorage(process.getProcess()).modifyConfigToAddANewUserPoolForTesting(config, 1);
        TenantIdentifier t1 = new TenantIdentifier(null, null, "t1");

        { // first factors not configured
            TestMultitenancyAPIHelper.createTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, null, null),
                    "t1", true, true, true,
                    null, true, null, false, null,
                    config, SemVer.v4_1);

            JsonObject response = TestMultitenancyAPIHelper.plSignInUpWithPhoneLink(t1, "+919876543210", process.getProcess(), SemVer.v4_1);
            assertTrue(response.has("tenantHasFirstFactors"));
            assertFalse(response.get("tenantHasFirstFactors").getAsBoolean());
        }

        { // first factors configured to empty array
            TestMultitenancyAPIHelper.createTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, null, null),
                    "t1", true, true, true,
                    null, true, new String[]{}, false, null,
                    config, SemVer.v4_1);

            JsonObject response = TestMultitenancyAPIHelper.plSignInUpWithPhoneLink(t1, "+919876543210", process.getProcess(), SemVer.v4_1);
            assertTrue(response.has("tenantHasFirstFactors"));
            assertTrue(response.get("tenantHasFirstFactors").getAsBoolean());
            assertFalse(response.get("isValidFirstFactor").getAsBoolean());
        }

        { // first factors configured, does not contain link-phone
            TestMultitenancyAPIHelper.createTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, null, null),
                    "t1", true, true, true,
                    null, true, new String[]{"thirdparty"}, false, null,
                    config, SemVer.v4_1);

            JsonObject response = TestMultitenancyAPIHelper.plSignInUpWithPhoneLink(t1, "+919876543210", process.getProcess(), SemVer.v4_1);
            assertTrue(response.has("tenantHasFirstFactors"));
            assertTrue(response.get("tenantHasFirstFactors").getAsBoolean());
            assertFalse(response.get("isValidFirstFactor").getAsBoolean());
        }

        { // first factors configured, contains link-phone
            TestMultitenancyAPIHelper.createTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, null, null),
                    "t1", true, true, true,
                    null, true, new String[]{"link-phone"}, false, null,
                    config, SemVer.v4_1);

            JsonObject response = TestMultitenancyAPIHelper.plSignInUpWithPhoneLink(t1, "+919876543210", process.getProcess(), SemVer.v4_1);
            assertTrue(response.has("tenantHasFirstFactors"));
            assertTrue(response.get("tenantHasFirstFactors").getAsBoolean());
            assertTrue(response.get("isValidFirstFactor").getAsBoolean());
        }
    }
}
