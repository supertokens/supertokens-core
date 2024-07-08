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
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.supertokens.ProcessState;
import io.supertokens.authRecipe.AuthRecipe;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlag;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.featureflag.exceptions.NoLicenseKeyFoundException;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.*;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.session.Session;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.multitenant.api.TestMultitenancyAPIHelper;
import io.supertokens.webserver.WebserverAPI;
import org.junit.*;
import org.junit.rules.TestRule;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

public class FeatureFlagTest {

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
    public void noLicenseKeyShouldHaveEmptyFeatureFlag()
            throws InterruptedException, StorageQueryException, TenantOrAppNotFoundException {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        Assert.assertNotNull(FeatureFlag.getInstance(process.main).getEeFeatureFlagInstance());

        if (StorageLayer.isInMemDb(process.main)) {
            Assert.assertEquals(FeatureFlag.getInstance(process.getProcess()).getEnabledFeatures().length,
                    EE_FEATURES.values().length);
        } else {
            Assert.assertEquals(FeatureFlag.getInstance(process.getProcess()).getEnabledFeatures().length, 0);
        }

        try {
            FeatureFlag.getInstance(process.getProcess()).getLicenseKey();
            fail();
        } catch (NoLicenseKeyFoundException ignored) {
        }

        JsonObject stats = FeatureFlag.getInstance(process.getProcess()).getPaidFeatureStats();
        Assert.assertEquals(stats.entrySet().size(), 1);
        Assert.assertEquals(stats.get("maus").getAsJsonArray().size(), 31);
        Assert.assertEquals(stats.get("maus").getAsJsonArray().get(0).getAsInt(), 0);
        Assert.assertEquals(stats.get("maus").getAsJsonArray().get(29).getAsInt(), 0);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void missingEEFolderShouldBeSameAsNoLicenseKey()
            throws InterruptedException, StorageQueryException, TenantOrAppNotFoundException {
        FeatureFlag.clearURLClassLoader();
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.EE_FOLDER_LOCATION, "random");
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        Assert.assertNull(FeatureFlag.getInstance(process.main).getEeFeatureFlagInstance());

        Assert.assertEquals(FeatureFlag.getInstance(process.getProcess()).getEnabledFeatures().length, 0);

        try {
            FeatureFlag.getInstance(process.getProcess()).getLicenseKey();
            fail();
        } catch (NoLicenseKeyFoundException ignored) {
        }

        Assert.assertEquals(FeatureFlag.getInstance(process.getProcess()).getPaidFeatureStats().entrySet().size(), 0);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testThatCallingGetFeatureFlagAPIReturnsEmptyArray() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/ee/featureflag",
                null, 1000, 1000, null, WebserverAPI.getLatestCDIVersion().get(), "");
        Assert.assertEquals("OK", response.get("status").getAsString());
        Assert.assertNotNull(response.get("features"));
        if (StorageLayer.isInMemDb(process.main)) {
            Assert.assertEquals(EE_FEATURES.values().length, response.get("features").getAsJsonArray().size());
        } else {
            Assert.assertEquals(0, response.get("features").getAsJsonArray().size());
        }

        process.kill();
        Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    private final String OPAQUE_KEY_WITH_MFA_MULTITENANCY_FEATURE = "wtdfQK80jaEYmM1cqlW=lELizFWJlaHOggzvF59jOAwX7NFx" +
            "dxH1fw0=RTy=BZixibzF5rn85SNKwfFfLcMm6Li3l1DYOVVD3H8XymCcekti217BxXb-Q6y5r-SKwMOG";

    @Test
    public void testThatCallingGetFeatureFlagAPIReturnsMfaStats() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        FeatureFlag.getInstance(process.main).setLicenseKeyAndSyncFeatures(OPAQUE_KEY_WITH_MFA_MULTITENANCY_FEATURE);

        // Get the stats without any users/activity
        {
            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/ee/featureflag",
                    null, 1000, 1000, null, WebserverAPI.getLatestCDIVersion().get(), "");
            Assert.assertEquals("OK", response.get("status").getAsString());

            JsonArray features = response.get("features").getAsJsonArray();
            JsonObject usageStats = response.get("usageStats").getAsJsonObject();
            JsonArray maus = usageStats.get("maus").getAsJsonArray();

            if (StorageLayer.isInMemDb(process.main)) {
                assert features.size() == EE_FEATURES.values().length;
            } else {
                assert features.size() == 2; // MFA + MULTITENANCY
            }
            assert features.contains(new JsonPrimitive("mfa"));
            assert maus.size() == 31;
            assert maus.get(0).getAsInt() == 0;
            assert maus.get(29).getAsInt() == 0;

            JsonObject mfaStats = usageStats.get("mfa").getAsJsonObject();
            int totalMfaUsers = mfaStats.get("totalUserCountWithMoreThanOneLoginMethodOrTOTPEnabled").getAsInt();
            JsonArray mfaMaus = mfaStats.get("mauWithMoreThanOneLoginMethodOrTOTPEnabled").getAsJsonArray();

            assert mfaMaus.size() == 31;
            assert mfaMaus.get(0).getAsInt() == 0;
            assert mfaMaus.get(29).getAsInt() == 0;

            assert totalMfaUsers == 0;
        }

        // First register 2 users for emailpassword recipe.
        // This also marks them as active.
        JsonObject signUpResponse = Utils.signUpRequest_2_5(process, "random@gmail.com", "validPass123");
        assert signUpResponse.get("status").getAsString().equals("OK");

        JsonObject signUpResponse2 = Utils.signUpRequest_2_5(process, "random2@gmail.com", "validPass123");
        assert signUpResponse2.get("status").getAsString().equals("OK");

        {
            // Now enable TOTP for the first user by registering a device.
            JsonObject body = new JsonObject();
            body.addProperty("userId", signUpResponse.get("user").getAsJsonObject().get("id").getAsString());
            body.addProperty("deviceName", "d1");
            body.addProperty("skew", 0);
            body.addProperty("period", 30);
            JsonObject res = HttpRequestForTesting.sendJsonPOSTRequest(
                    process.getProcess(),
                    "",
                    "http://localhost:3567/recipe/totp/device",
                    body,
                    1000,
                    1000,
                    null,
                    Utils.getCdiVersionStringLatestForTests(),
                    "totp");
            assert res.get("status").getAsString().equals("OK");
        }

        // Now check the stats again:
        {
            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/ee/featureflag",
                    null, 1000, 1000, null, WebserverAPI.getLatestCDIVersion().get(), "");
            Assert.assertEquals("OK", response.get("status").getAsString());

            JsonArray features = response.get("features").getAsJsonArray();
            JsonObject usageStats = response.get("usageStats").getAsJsonObject();
            JsonArray maus = usageStats.get("maus").getAsJsonArray();

            if (StorageLayer.isInMemDb(process.main)) {
                assert features.size() == EE_FEATURES.values().length;
            } else {
                assert features.size() == 2; // MFA + MULTITENANCY
            }

            assert features.contains(new JsonPrimitive("mfa"));
            assert maus.size() == 31;
            assert maus.get(0).getAsInt() == 2; // 2 users have signed up
            assert maus.get(29).getAsInt() == 2;

            JsonObject mfaStats = usageStats.get("mfa").getAsJsonObject();
            int totalMfaUsers = mfaStats.get("totalUserCountWithMoreThanOneLoginMethodOrTOTPEnabled").getAsInt();
            JsonArray mfaMaus = mfaStats.get("mauWithMoreThanOneLoginMethodOrTOTPEnabled").getAsJsonArray();

            assert mfaMaus.size() == 31;
            assert mfaMaus.get(0).getAsInt() == 1; // only 1 user has TOTP enabled
            assert mfaMaus.get(29).getAsInt() == 1;

            assert totalMfaUsers == 1;
        }

        {
            // Test with account linking
            JsonObject user1 = Utils.signUpRequest_2_5(process, "test1@gmail.com", "validPass123");
            assert signUpResponse.get("status").getAsString().equals("OK");

            JsonObject user2 = Utils.signUpRequest_2_5(process, "test2@gmail.com", "validPass123");
            assert signUpResponse2.get("status").getAsString().equals("OK");

            AuthRecipe.createPrimaryUser(process.getProcess(),
                    user1.get("user").getAsJsonObject().get("id").getAsString());
            AuthRecipe.linkAccounts(process.getProcess(), user2.get("user").getAsJsonObject().get("id").getAsString(),
                    user1.get("user").getAsJsonObject().get("id").getAsString());

            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/ee/featureflag",
                    null, 1000, 1000, null, WebserverAPI.getLatestCDIVersion().get(), "");
            Assert.assertEquals("OK", response.get("status").getAsString());

            JsonArray features = response.get("features").getAsJsonArray();
            JsonObject usageStats = response.get("usageStats").getAsJsonObject();
            JsonArray maus = usageStats.get("maus").getAsJsonArray();

            if (StorageLayer.isInMemDb(process.main)) {
                assert features.size() == EE_FEATURES.values().length;
            } else {
                assert features.size() == 2; // MFA + MULTITENANCY
            }

            assert features.contains(new JsonPrimitive("mfa"));
            assert maus.size() == 31;
            assert maus.get(0).getAsInt() == 4; // 2 users have signed up
            assert maus.get(29).getAsInt() == 4;

            {
                JsonObject mfaStats = usageStats.get("mfa").getAsJsonObject();
                int totalMfaUsers = mfaStats.get("totalUserCountWithMoreThanOneLoginMethodOrTOTPEnabled").getAsInt();
                JsonArray mfaMaus = mfaStats.get("mauWithMoreThanOneLoginMethodOrTOTPEnabled").getAsJsonArray();

                assert mfaMaus.size() == 31;
                assert mfaMaus.get(0).getAsInt() == 2; // 1 TOTP user + 1 account linked user
                assert mfaMaus.get(29).getAsInt() == 2;

                assert totalMfaUsers == 2;
            }

            // Add TOTP to the linked user
            {
                JsonObject body = new JsonObject();
                body.addProperty("userId", user1.get("user").getAsJsonObject().get("id").getAsString());
                body.addProperty("deviceName", "d1");
                body.addProperty("skew", 0);
                body.addProperty("period", 30);
                JsonObject res = HttpRequestForTesting.sendJsonPOSTRequest(
                        process.getProcess(),
                        "",
                        "http://localhost:3567/recipe/totp/device",
                        body,
                        1000,
                        1000,
                        null,
                        Utils.getCdiVersionStringLatestForTests(),
                        "totp");
                assert res.get("status").getAsString().equals("OK");
            }
        }

        {
            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/ee/featureflag",
                    null, 1000, 1000, null, WebserverAPI.getLatestCDIVersion().get(), "");
            Assert.assertEquals("OK", response.get("status").getAsString());

            JsonArray features = response.get("features").getAsJsonArray();
            JsonObject usageStats = response.get("usageStats").getAsJsonObject();
            JsonArray maus = usageStats.get("maus").getAsJsonArray();

            { // MFA stats should still count 2 users
                JsonObject mfaStats = usageStats.get("mfa").getAsJsonObject();
                int totalMfaUsers = mfaStats.get("totalUserCountWithMoreThanOneLoginMethodOrTOTPEnabled").getAsInt();
                JsonArray mfaMaus = mfaStats.get("mauWithMoreThanOneLoginMethodOrTOTPEnabled").getAsJsonArray();

                assert mfaMaus.size() == 31;
                assert mfaMaus.get(0).getAsInt() == 2; // 1 TOTP user + 1 account linked user
                assert mfaMaus.get(29).getAsInt() == 2;

                assert totalMfaUsers == 2;
            }
        }

        { // Associate the user with multiple tenants and still the stats should be same
            Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                    new TenantIdentifier(null, null, "t1"),
                    new EmailPasswordConfig(true),
                    new ThirdPartyConfig(true, null),
                    new PasswordlessConfig(true),
                    null, null,
                    new JsonObject()
            ), false);
            Multitenancy.addUserIdToTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, null, "t1"), (StorageLayer.getStorage(process.getProcess())),
                    signUpResponse.get("user").getAsJsonObject().get("id").getAsString()
            );
            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/ee/featureflag",
                    null, 1000, 1000, null, WebserverAPI.getLatestCDIVersion().get(), "");
            Assert.assertEquals("OK", response.get("status").getAsString());

            JsonArray features = response.get("features").getAsJsonArray();
            JsonObject usageStats = response.get("usageStats").getAsJsonObject();
            JsonArray maus = usageStats.get("maus").getAsJsonArray();

            { // MFA stats should still count 2 users
                JsonObject mfaStats = usageStats.get("mfa").getAsJsonObject();
                int totalMfaUsers = mfaStats.get("totalUserCountWithMoreThanOneLoginMethodOrTOTPEnabled").getAsInt();
                JsonArray mfaMaus = mfaStats.get("mauWithMoreThanOneLoginMethodOrTOTPEnabled").getAsJsonArray();

                assert mfaMaus.size() == 31;
                assert mfaMaus.get(0).getAsInt() == 2; // 1 TOTP user + 1 account linked user
                assert mfaMaus.get(29).getAsInt() == 2;

                assert totalMfaUsers == 2;
            }
        }

        process.kill();
        Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    private final static String OPAQUE_KEY_WITH_MFA_FEATURE = "Qk8olVa=v-9PU" +
            "=snnUFMF4ihMCx4zVBOO6Jd7Nrg6Cg5YyFliEj252ADgpwEpDLfFowA0U5OyVo3XL" +
            "=U4FMft2HDHCDGg9hWD4iwQQiyjMRi6Mu03CVbAxIkNGaXtJ53";


    private final String OPAQUE_KEY_WITH_MULTITENANCY_FEATURE = "ijaleljUd2kU9XXWLiqFYv5br8nutTxbyBqWypQdv2N-" +
            "BocoNriPrnYQd0NXPm8rVkeEocN9ayq0B7c3Pv-BTBIhAZSclXMlgyfXtlwAOJk=9BfESEleW6LyTov47dXu";

    @Test
    public void testFeatureFlagWithMultitenancyFor500Tenants() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        FeatureFlag.getInstance(process.main).setLicenseKeyAndSyncFeatures(OPAQUE_KEY_WITH_MULTITENANCY_FEATURE);

        for (int i = 0; i < 500; i++) {
            TenantIdentifier tenantIdentifier = new TenantIdentifier(null, null, "t" + i);
            Multitenancy.addNewOrUpdateAppOrTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, null, null),
                    new TenantConfig(
                            tenantIdentifier,
                            new EmailPasswordConfig(true),
                            new ThirdPartyConfig(true, null),
                            new PasswordlessConfig(true),
                            null, null,
                            new JsonObject()
                    )
            );

            System.out.println("Added tenant " + i);
        }

        long startTime = System.currentTimeMillis();
        JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/ee/featureflag",
                null, 5000, 5000, null, WebserverAPI.getLatestCDIVersion().get(), "");

        long timeTaken = System.currentTimeMillis() - startTime;
        assertTrue(timeTaken < 2500);
        Assert.assertEquals("OK", response.get("status").getAsString());

        JsonArray multitenancyStats = response.get("usageStats").getAsJsonObject().get("multi_tenancy")
                .getAsJsonObject().get("tenants").getAsJsonArray();
        assertEquals(501, multitenancyStats.size());

        String userPoolId = null;
        for (JsonElement elem : multitenancyStats) {
            if (userPoolId == null) {
                userPoolId = elem.getAsJsonObject().get("userPoolId").getAsString();
            }
            // ensure all userPoolIds are same
            assertEquals(userPoolId, elem.getAsJsonObject().get("userPoolId").getAsString());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testThatMultitenantStatsAreAccurate() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        FeatureFlag.getInstance(process.main).setLicenseKeyAndSyncFeatures(OPAQUE_KEY_WITH_MULTITENANCY_FEATURE);

        for (int i = 0; i < 5; i++) {
            JsonObject coreConfig = new JsonObject();
            StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                    .modifyConfigToAddANewUserPoolForTesting(coreConfig, i + 1);

            TenantIdentifier tenantIdentifier = new TenantIdentifier(null, null, "t" + i);
            Multitenancy.addNewOrUpdateAppOrTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, null, null),
                    new TenantConfig(
                            tenantIdentifier,
                            new EmailPasswordConfig(true),
                            new ThirdPartyConfig(true, null),
                            new PasswordlessConfig(true),
                            null, null,
                            coreConfig
                    )
            );

            Storage storage = (
                    StorageLayer.getStorage(tenantIdentifier, process.getProcess()));
            if (i % 3 == 0) {
                // Create a user
                EmailPassword.signUp(
                        tenantIdentifier, storage, process.getProcess(), "user@example.com",
                        "password");
            } else if (i % 3 == 1) {
                // Create a session
                Session.createNewSession(
                        tenantIdentifier, storage, process.getProcess(), "userid", new JsonObject(),
                        new JsonObject());
            } else {
                // Create an enterprise provider
                Multitenancy.addNewOrUpdateAppOrTenant(
                        process.getProcess(),
                        new TenantIdentifier(null, null, null),
                        new TenantConfig(
                                tenantIdentifier,
                                new EmailPasswordConfig(true),
                                new ThirdPartyConfig(true, new ThirdPartyConfig.Provider[]{
                                        new ThirdPartyConfig.Provider("okta", "Okta", null, null, null, null, null,
                                                null, null, null, null, null, null, null)
                                }),
                                new PasswordlessConfig(true),
                                null, null,
                                coreConfig
                        )
                );
            }
        }

        JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/ee/featureflag",
                null, 1000, 1000, null, WebserverAPI.getLatestCDIVersion().get(), "");
        Assert.assertEquals("OK", response.get("status").getAsString());

        JsonArray multitenancyStats = response.get("usageStats").getAsJsonObject().get("multi_tenancy")
                .getAsJsonObject().get("tenants").getAsJsonArray();
        assertEquals(6, multitenancyStats.size());

        Set<String> userPoolIds = new HashSet<>();
        for (JsonElement tenantStat : multitenancyStats) {
            JsonObject tenantStatObj = tenantStat.getAsJsonObject();
            String tenantId = tenantStatObj.get("tenantId").getAsString();

            if (!StorageLayer.isInMemDb(process.getProcess())) {
                // Ensure each userPoolId is unique
                String userPoolId = tenantStatObj.get("userPoolId").getAsString();
                assertFalse(userPoolIds.contains(userPoolId));
                userPoolIds.add(userPoolId);
            }

            if (tenantId.equals("public")) {
                assertFalse(tenantStatObj.get("hasUsersOrSessions").getAsBoolean());
                assertFalse(tenantStatObj.get("hasEnterpriseLogin").getAsBoolean());
                assertEquals(0, tenantStatObj.get("usersCount").getAsLong());
            } else if (tenantId.equals("t0")) {
                assertTrue(tenantStatObj.get("hasUsersOrSessions").getAsBoolean());
                assertFalse(tenantStatObj.get("hasEnterpriseLogin").getAsBoolean());
                assertEquals(1, tenantStatObj.get("usersCount").getAsLong());
            } else if (tenantId.equals("t1")) {
                assertTrue(tenantStatObj.get("hasUsersOrSessions").getAsBoolean());
                assertFalse(tenantStatObj.get("hasEnterpriseLogin").getAsBoolean());
                assertEquals(0, tenantStatObj.get("usersCount").getAsLong());
            } else if (tenantId.equals("t2")) {
                assertFalse(tenantStatObj.get("hasUsersOrSessions").getAsBoolean());
                assertTrue(tenantStatObj.get("hasEnterpriseLogin").getAsBoolean());
                assertEquals(0, tenantStatObj.get("usersCount").getAsLong());
            }
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testThatMultitenantStatsAreAccurateForAnApp() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        FeatureFlag.getInstance(process.main).setLicenseKeyAndSyncFeatures(OPAQUE_KEY_WITH_MULTITENANCY_FEATURE);

        Multitenancy.addNewOrUpdateAppOrTenant(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                new TenantConfig(
                        new TenantIdentifier(null, "a1", null),
                        new EmailPasswordConfig(true),
                        new ThirdPartyConfig(true, null),
                        new PasswordlessConfig(true),
                        null, null,
                        new JsonObject()
                )
        );

        for (int i = 0; i < 5; i++) {
            JsonObject coreConfig = new JsonObject();
            StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                    .modifyConfigToAddANewUserPoolForTesting(coreConfig, i + 1);

            TenantIdentifier tenantIdentifier = new TenantIdentifier(null, "a1", "t" + i);
            Multitenancy.addNewOrUpdateAppOrTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, "a1", null),
                    new TenantConfig(
                            tenantIdentifier,
                            new EmailPasswordConfig(true),
                            new ThirdPartyConfig(true, null),
                            new PasswordlessConfig(true),
                            null, null,
                            coreConfig
                    )
            );

            Storage storage = (
                    StorageLayer.getStorage(tenantIdentifier, process.getProcess()));
            if (i % 3 == 0) {
                // Create a user
                EmailPassword.signUp(
                        tenantIdentifier, storage, process.getProcess(), "user@example.com",
                        "password");
            } else if (i % 3 == 1) {
                // Create a session
                Session.createNewSession(
                        tenantIdentifier, storage, process.getProcess(), "userid", new JsonObject(),
                        new JsonObject());
            } else {
                // Create an enterprise provider
                Multitenancy.addNewOrUpdateAppOrTenant(
                        process.getProcess(),
                        new TenantIdentifier(null, "a1", null),
                        new TenantConfig(
                                tenantIdentifier,
                                new EmailPasswordConfig(true),
                                new ThirdPartyConfig(true, new ThirdPartyConfig.Provider[]{
                                        new ThirdPartyConfig.Provider("okta", "Okta", null, null, null, null, null,
                                                null, null, null, null, null, null, null)
                                }),
                                new PasswordlessConfig(true),
                                null, null,
                                coreConfig
                        )
                );
            }
        }

        JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/appid-a1/ee/featureflag",
                null, 1000, 1000, null, WebserverAPI.getLatestCDIVersion().get(), "");
        Assert.assertEquals("OK", response.get("status").getAsString());

        assertFalse(response.get("usageStats").getAsJsonObject().has("account_linking"));
        JsonArray multitenancyStats = response.get("usageStats").getAsJsonObject().get("multi_tenancy")
                .getAsJsonObject().get("tenants").getAsJsonArray();
        assertEquals(6, multitenancyStats.size());

        Set<String> userPoolIds = new HashSet<>();
        for (JsonElement tenantStat : multitenancyStats) {
            JsonObject tenantStatObj = tenantStat.getAsJsonObject();
            String tenantId = tenantStatObj.get("tenantId").getAsString();

            if (!StorageLayer.isInMemDb(process.getProcess())) {
                // Ensure each userPoolId is unique
                String userPoolId = tenantStatObj.get("userPoolId").getAsString();
                assertFalse(userPoolIds.contains(userPoolId));
                userPoolIds.add(userPoolId);
            }

            if (tenantId.equals("public")) {
                assertFalse(tenantStatObj.get("hasUsersOrSessions").getAsBoolean());
                assertFalse(tenantStatObj.get("hasEnterpriseLogin").getAsBoolean());
                assertEquals(0, tenantStatObj.get("usersCount").getAsLong());
            } else if (tenantId.equals("t0")) {
                assertTrue(tenantStatObj.get("hasUsersOrSessions").getAsBoolean());
                assertFalse(tenantStatObj.get("hasEnterpriseLogin").getAsBoolean());
                assertEquals(1, tenantStatObj.get("usersCount").getAsLong());
            } else if (tenantId.equals("t1")) {
                assertTrue(tenantStatObj.get("hasUsersOrSessions").getAsBoolean());
                assertFalse(tenantStatObj.get("hasEnterpriseLogin").getAsBoolean());
                assertEquals(0, tenantStatObj.get("usersCount").getAsLong());
            } else if (tenantId.equals("t2")) {
                assertFalse(tenantStatObj.get("hasUsersOrSessions").getAsBoolean());
                assertTrue(tenantStatObj.get("hasEnterpriseLogin").getAsBoolean());
                assertEquals(0, tenantStatObj.get("usersCount").getAsLong());
            }
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testThatMultitenantStatsAreAccurateForACud() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        if (StorageLayer.isInMemDb(process.getProcess())) {
            return;
        }

        FeatureFlag.getInstance(process.main).setLicenseKeyAndSyncFeatures(OPAQUE_KEY_WITH_MULTITENANCY_FEATURE);

        {
            JsonObject coreConfig = new JsonObject();
            StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                    .modifyConfigToAddANewUserPoolForTesting(coreConfig, 1);
            Multitenancy.addNewOrUpdateAppOrTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, null, null),
                    new TenantConfig(
                            new TenantIdentifier("127.0.0.1", null, null),
                            new EmailPasswordConfig(true),
                            new ThirdPartyConfig(true, null),
                            new PasswordlessConfig(true),
                            null, null,
                            coreConfig
                    )
            );
        }

        for (int i = 0; i < 5; i++) {
            JsonObject coreConfig = new JsonObject();
            StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                    .modifyConfigToAddANewUserPoolForTesting(coreConfig, i + 2);

            TenantIdentifier tenantIdentifier = new TenantIdentifier("127.0.0.1", null, "t" + i);
            Multitenancy.addNewOrUpdateAppOrTenant(
                    process.getProcess(),
                    new TenantIdentifier("127.0.0.1", null, null),
                    new TenantConfig(
                            tenantIdentifier,
                            new EmailPasswordConfig(true),
                            new ThirdPartyConfig(true, null),
                            new PasswordlessConfig(true),
                            null, null,
                            coreConfig
                    )
            );

            Storage storage = (
                    StorageLayer.getStorage(tenantIdentifier, process.getProcess()));
            if (i % 3 == 0) {
                // Create a user
                EmailPassword.signUp(
                        tenantIdentifier, storage, process.getProcess(), "user@example.com",
                        "password");
            } else if (i % 3 == 1) {
                // Create a session
                Session.createNewSession(
                        tenantIdentifier, storage, process.getProcess(), "userid", new JsonObject(),
                        new JsonObject());
            } else {
                // Create an enterprise provider
                Multitenancy.addNewOrUpdateAppOrTenant(
                        process.getProcess(),
                        new TenantIdentifier("127.0.0.1", null, null),
                        new TenantConfig(
                                tenantIdentifier,
                                new EmailPasswordConfig(true),
                                new ThirdPartyConfig(true, new ThirdPartyConfig.Provider[]{
                                        new ThirdPartyConfig.Provider("okta", "Okta", null, null, null, null, null,
                                                null, null, null, null, null, null, null)
                                }),
                                new PasswordlessConfig(true),
                                null, null,
                                coreConfig
                        )
                );
            }
        }

        JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://127.0.0.1:3567/ee/featureflag",
                null, 1000, 1000, null, WebserverAPI.getLatestCDIVersion().get(), "");
        Assert.assertEquals("OK", response.get("status").getAsString());

        JsonArray multitenancyStats = response.get("usageStats").getAsJsonObject().get("multi_tenancy")
                .getAsJsonObject().get("tenants").getAsJsonArray();
        assertEquals(6, multitenancyStats.size());

        Set<String> userPoolIds = new HashSet<>();
        for (JsonElement tenantStat : multitenancyStats) {
            JsonObject tenantStatObj = tenantStat.getAsJsonObject();
            String tenantId = tenantStatObj.get("tenantId").getAsString();

            if (!StorageLayer.isInMemDb(process.getProcess())) {
                // Ensure each userPoolId is unique
                String userPoolId = tenantStatObj.get("userPoolId").getAsString();
                assertFalse(userPoolIds.contains(userPoolId));
                userPoolIds.add(userPoolId);
            }

            if (tenantId.equals("public")) {
                assertFalse(tenantStatObj.get("hasUsersOrSessions").getAsBoolean());
                assertFalse(tenantStatObj.get("hasEnterpriseLogin").getAsBoolean());
                assertEquals(0, tenantStatObj.get("usersCount").getAsLong());
            } else if (tenantId.equals("t0")) {
                assertTrue(tenantStatObj.get("hasUsersOrSessions").getAsBoolean());
                assertFalse(tenantStatObj.get("hasEnterpriseLogin").getAsBoolean());
                assertEquals(1, tenantStatObj.get("usersCount").getAsLong());
            } else if (tenantId.equals("t1")) {
                assertTrue(tenantStatObj.get("hasUsersOrSessions").getAsBoolean());
                assertFalse(tenantStatObj.get("hasEnterpriseLogin").getAsBoolean());
                assertEquals(0, tenantStatObj.get("usersCount").getAsLong());
            } else if (tenantId.equals("t2")) {
                assertFalse(tenantStatObj.get("hasUsersOrSessions").getAsBoolean());
                assertTrue(tenantStatObj.get("hasEnterpriseLogin").getAsBoolean());
                assertEquals(0, tenantStatObj.get("usersCount").getAsLong());
            }
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testPaidFeaturesAreEnabledIfUsingInMemoryDatabase() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        TenantIdentifier tenantIdentifier = new TenantIdentifier(null, null, "t1");
        try {
            Multitenancy.addNewOrUpdateAppOrTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, null, null),
                    new TenantConfig(
                            tenantIdentifier,
                            new EmailPasswordConfig(true),
                            new ThirdPartyConfig(true, null),
                            new PasswordlessConfig(true),
                            null, null,
                            new JsonObject()
                    )
            );
            assert StorageLayer.isInMemDb(process.getProcess());
        } catch (FeatureNotEnabledException e) {
            assert !StorageLayer.isInMemDb(process.getProcess());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testNetworkCallIsMadeInCoreInit() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        if (StorageLayer.isInMemDb(process.getProcess())) {
            return;
        }

        // While adding license
        TestMultitenancyAPIHelper.addLicense(OPAQUE_KEY_WITH_MULTITENANCY_FEATURE, process.getProcess());
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.LICENSE_KEY_CHECK_NETWORK_CALL));
        ProcessState.getInstance(process.getProcess()).clear();

        process.kill(false);


        // Restart core and check if the call was made during init
        process = TestingProcessManager.start(args);
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.LICENSE_KEY_CHECK_NETWORK_CALL));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    private final String OPAQUE_KEY_WITH_DASHBOARD_FEATURE =
            "EBy9Z4IRJ7BYyLP8AXxjq997o3RPaDekAE4CMGxduglUaEH9hugXzIduxvHIjpkFccVCZaHJIacMi8NJJg4I" +
                    "=ruc3bZbT43QOLJbGu01cgACmVu2VOjQzFbT3lXiAKOR";

    private final String OPAQUE_KEY_WITH_ACCOUNT_LINKING_FEATURE = "N2uEOdEzd1XZZ5VBSTGYaM7Ia4s8wAqRWFAxLqTYrB6GQ=" +
            "vssOLo3c=PkFgcExkaXs=IA-d9UWccoNKsyUgNhOhcKtM1bjC5OLrYRpTAgN-2EbKYsQGGQRQHuUN4EO1V";

    private final String OPAQUE_KEY_WTIH_MFA_FEATURE = "F1a=1VUxo7-tHNqFDwuhkkCPCB378A57uRU4=rVW01XBv63YizRb6ItTBu" +
            "FHXQIvmceLTlOekCmHv7mwzEZJJKmO9N8pclQSbs4UBz8pzW5d107TIctJgBwy4upnBHUf";

    @Test
    public void testPaidStatsContainsAllEnabledFeatures() throws Exception {
        String[] args = {"../"};

        EE_FEATURES[] allFeatures = EE_FEATURES.values();

        List<EE_FEATURES> featuresToIgnore = List.of(EE_FEATURES.TEST);

        String[] licenses = new String[]{
                OPAQUE_KEY_WITH_MULTITENANCY_FEATURE,
                OPAQUE_KEY_WITH_MFA_FEATURE,
                OPAQUE_KEY_WITH_DASHBOARD_FEATURE,
                OPAQUE_KEY_WITH_ACCOUNT_LINKING_FEATURE
        };

        Set<EE_FEATURES> requiredFeatures = new HashSet<>();
        requiredFeatures.addAll(Arrays.asList(allFeatures));
        requiredFeatures.removeAll(featuresToIgnore);

        Set<EE_FEATURES> foundFeatures = new HashSet<>();

        for (String license : licenses) {
            Utils.reset();
            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
            process.startProcess();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

            if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
                return;
            }

            if (StorageLayer.isInMemDb(process.getProcess())) {
                return;
            }

            // While adding license
            TestMultitenancyAPIHelper.addLicense(license, process.getProcess());
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.LICENSE_KEY_CHECK_NETWORK_CALL));
            ProcessState.getInstance(process.getProcess()).clear();

            process.kill(false);

            // Restart core and check if the call was made during init
            process = TestingProcessManager.start(args);
            process.startProcess();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
            ProcessState.EventAndException event = process.checkOrWaitForEvent(
                    ProcessState.PROCESS_STATE.LICENSE_KEY_CHECK_NETWORK_CALL);
            assertNotNull(event);
            assertNotNull(event.data);

            JsonObject paidStatsObj = event.data.get("paidFeatureUsageStats").getAsJsonObject();
            for (EE_FEATURES feature : allFeatures) {
                if (featuresToIgnore.contains(feature)) {
                    continue;
                }
                if (paidStatsObj.has(feature.toString())) {
                    foundFeatures.add(feature);
                }
            }

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }

        assertEquals(requiredFeatures, foundFeatures);
    }
}
