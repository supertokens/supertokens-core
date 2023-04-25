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
import io.supertokens.ProcessState;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlag;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.featureflag.exceptions.NoLicenseKeyFoundException;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.*;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.webserver.WebserverAPI;
import org.junit.*;
import org.junit.rules.TestRule;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

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

        Assert.assertEquals(FeatureFlag.getInstance(process.getProcess()).getEnabledFeatures().length, 0);

        try {
            FeatureFlag.getInstance(process.getProcess()).getLicenseKey();
            fail();
        } catch (NoLicenseKeyFoundException ignored) {
        }

        JsonObject stats = FeatureFlag.getInstance(process.getProcess()).getPaidFeatureStats();
        Assert.assertEquals(stats.entrySet().size(), 1);
        Assert.assertEquals(stats.get("maus").getAsJsonArray().size(), 30);
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
        Assert.assertEquals(0, response.get("features").getAsJsonArray().size());

        process.kill();
        Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    private final String OPAQUE_KEY_WITH_TOTP_FEATURE = "pXhNK=nYiEsb6gJEOYP2kIR6M0kn4XLvNqcwT1XbX8xHtm44K" +
            "-lQfGCbaeN0Ieeza39fxkXr=tiiUU=DXxDH40Y=4FLT4CE-rG1ETjkXxO4yucLpJvw3uSegPayoISGL";

    @Test
    public void testThatCallingGetFeatureFlagAPIReturnsTotpStats() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        FeatureFlag.getInstance(process.main).setLicenseKeyAndSyncFeatures(OPAQUE_KEY_WITH_TOTP_FEATURE);

        // Get the stats without any users/activity
        {
            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/ee/featureflag",
                    null, 1000, 1000, null, WebserverAPI.getLatestCDIVersion().get(), "");
            Assert.assertEquals("OK", response.get("status").getAsString());

            JsonArray features = response.get("features").getAsJsonArray();
            JsonObject usageStats = response.get("usageStats").getAsJsonObject();
            JsonArray maus = usageStats.get("maus").getAsJsonArray();

            assert features.size() == 1;
            assert features.get(0).getAsString().equals("totp");
            assert maus.size() == 30;
            assert maus.get(0).getAsInt() == 0;
            assert maus.get(29).getAsInt() == 0;

            JsonObject totpStats = usageStats.get("totp").getAsJsonObject();
            JsonArray totpMaus = totpStats.get("maus").getAsJsonArray();
            int totalTotpUsers = totpStats.get("total_users").getAsInt();

            assert totpMaus.size() == 30;
            assert totpMaus.get(0).getAsInt() == 0;
            assert totpMaus.get(29).getAsInt() == 0;

            assert totalTotpUsers == 0;
        }

        // First register 2 users for emailpassword recipe.
        // This also marks them as active.
        JsonObject signUpResponse = Utils.signUpRequest_2_5(process, "random@gmail.com", "validPass123");
        assert signUpResponse.get("status").getAsString().equals("OK");

        JsonObject signUpResponse2 = Utils.signUpRequest_2_5(process, "random2@gmail.com", "validPass123");
        assert signUpResponse2.get("status").getAsString().equals("OK");

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

        // Now check the stats again:
        {
            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/ee/featureflag",
                    null, 1000, 1000, null, WebserverAPI.getLatestCDIVersion().get(), "");
            Assert.assertEquals("OK", response.get("status").getAsString());

            JsonArray features = response.get("features").getAsJsonArray();
            JsonObject usageStats = response.get("usageStats").getAsJsonObject();
            JsonArray maus = usageStats.get("maus").getAsJsonArray();

            assert features.size() == 1;
            assert features.get(0).getAsString().equals("totp");
            assert maus.size() == 30;
            assert maus.get(0).getAsInt() == 2; // 2 users have signed up
            assert maus.get(29).getAsInt() == 2;

            JsonObject totpStats = usageStats.get("totp").getAsJsonObject();
            JsonArray totpMaus = totpStats.get("maus").getAsJsonArray();
            int totalTotpUsers = totpStats.get("total_users").getAsInt();

            assert totpMaus.size() == 30;
            assert totpMaus.get(0).getAsInt() == 1; // only 1 user has TOTP enabled
            assert totpMaus.get(29).getAsInt() == 1;

            assert totalTotpUsers == 1;
        }

        process.kill();
        Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    private final String OPAQUE_KEY_WITH_MULTITENANCY_FEATURE = "ijaleljUd2kU9XXWLiqFYv5br8nutTxbyBqWypQdv2N-" +
            "BocoNriPrnYQd0NXPm8rVkeEocN9ayq0B7c3Pv-BTBIhAZSclXMlgyfXtlwAOJk=9BfESEleW6LyTov47dXu";

    @Test
    public void testFeatureFlagWithMultitenancyFor500Tenants() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        FeatureFlag.getInstance(process.main).setLicenseKeyAndSyncFeatures(OPAQUE_KEY_WITH_MULTITENANCY_FEATURE);

        for (int i=0; i<500; i++) {
            Multitenancy.addNewOrUpdateAppOrTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, null, null),
                    new TenantConfig(
                            new TenantIdentifier(null, null, "t" + i),
                            new EmailPasswordConfig(true),
                            new ThirdPartyConfig(true, null),
                            new PasswordlessConfig(true),
                            new JsonObject()
                    )
            );
            System.out.println("Added tenant " + i);
        }

        JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/ee/featureflag",
                null, 1000, 1000, null, WebserverAPI.getLatestCDIVersion().get(), "");
        Assert.assertEquals("OK", response.get("status").getAsString());

        // TODO

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
