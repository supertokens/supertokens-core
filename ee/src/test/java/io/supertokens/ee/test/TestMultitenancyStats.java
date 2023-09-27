package io.supertokens.ee.test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.cronjobs.CronTaskTest;
import io.supertokens.ee.EEFeatureFlag;
import io.supertokens.ee.cronjobs.EELicenseCheck;
import io.supertokens.ee.test.httpRequest.HttpRequestForTesting;
import io.supertokens.featureflag.FeatureFlag;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.multitenancy.*;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.webserver.WebserverAPI;
import org.junit.*;
import org.junit.rules.TestRule;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TestMultitenancyStats {
    @Rule
    public TestRule watchman = Utils.getOnFailure();

    @AfterClass
    public static void afterTesting() {
        Utils.afterTesting();
    }

    @Before
    public void beforeEach() {
        Utils.reset();
        FeatureFlag.clearURLClassLoader();
    }

    private final String OPAQUE_KEY_WITH_MULTITENANCY_FEATURE = "ijaleljUd2kU9XXWLiqFYv5br8nutTxbyBqWypQdv2N-" +
            "BocoNriPrnYQd0NXPm8rVkeEocN9ayq0B7c3Pv-BTBIhAZSclXMlgyfXtlwAOJk=9BfESEleW6LyTov47dXu";

    @Test
    public void testPaidStatsIsSentForAllAppsInMultitenancy() throws Exception {
        String[] args = {"../../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        CronTaskTest.getInstance(process.main).setIntervalInSeconds(EELicenseCheck.RESOURCE_KEY, 1);
        Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        if (StorageLayer.isInMemDb(process.main)) {
            // cause we keep all features enabled in memdb anyway
            return;
        }

        {
            // Add the license
            JsonObject requestBody = new JsonObject();

            requestBody.addProperty("licenseKey", OPAQUE_KEY_WITH_MULTITENANCY_FEATURE);

            HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                    "http://localhost:3567/ee/license",
                    requestBody, 10000, 10000, null, WebserverAPI.getLatestCDIVersion().get(), "");
        }

        {
            // Create tenants and apps
            JsonObject config = new JsonObject();
            StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                    .modifyConfigToAddANewUserPoolForTesting(config, 1);

            Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                    new TenantIdentifier("127.0.0.1", null, null),
                    new EmailPasswordConfig(true),
                    new ThirdPartyConfig(true, null),
                    new PasswordlessConfig(true),
                    config
            ), false);

            Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                    new TenantIdentifier("127.0.0.1", "a1", null),
                    new EmailPasswordConfig(true),
                    new ThirdPartyConfig(true, null),
                    new PasswordlessConfig(true),
                    config
            ), false);

            Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                    new TenantIdentifier("127.0.0.1", "a1", "t1"),
                    new EmailPasswordConfig(true),
                    new ThirdPartyConfig(true, null),
                    new PasswordlessConfig(true),
                    config
            ), false);
        }

        Thread.sleep(2000); // Let all the cron tasks run

        List<JsonObject> requests = EEFeatureFlag.getLicenseCheckRequests();
        Set<TenantIdentifier> tenantIdentifiers = new HashSet<>();

        for (JsonObject request : requests) {
            if (request.has("paidFeatureUsageStats")) {
                JsonObject paidStats = request.getAsJsonObject("paidFeatureUsageStats");
                if (paidStats.has("multi_tenancy")) {
                    JsonObject mtStats = paidStats.getAsJsonObject("multi_tenancy");
                    String cud = mtStats.get("connectionUriDomain").getAsString();
                    String appId = mtStats.get("appId").getAsString();

                    JsonArray tenants = mtStats.get("tenants").getAsJsonArray();
                    for (JsonElement tenantElem : tenants) {
                        JsonObject tenant = tenantElem.getAsJsonObject();
                        String tenantId = tenant.get("tenantId").getAsString();

                        tenantIdentifiers.add(new TenantIdentifier(cud, appId, tenantId));
                    }
                }
            }
        }

        Assert.assertEquals(tenantIdentifiers.size(), 4);
        Assert.assertTrue(tenantIdentifiers.contains(new TenantIdentifier(null, null, null)));
        Assert.assertTrue(tenantIdentifiers.contains(new TenantIdentifier("127.0.0.1", null, null)));
        Assert.assertTrue(tenantIdentifiers.contains(new TenantIdentifier("127.0.0.1", "a1", null)));
        Assert.assertTrue(tenantIdentifiers.contains(new TenantIdentifier("127.0.0.1", "a1", "t1")));

        process.kill();
        Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
