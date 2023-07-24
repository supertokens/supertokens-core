package io.supertokens.ee.test.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.supertokens.ProcessState.PROCESS_STATE;
import io.supertokens.ee.test.EETest;
import io.supertokens.ee.test.TestingProcessManager;
import io.supertokens.ee.test.Utils;
import io.supertokens.ee.test.httpRequest.HttpRequestForTesting;
import io.supertokens.featureflag.FeatureFlag;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.webserver.WebserverAPI;
import org.junit.*;
import org.junit.rules.TestRule;

import static org.junit.Assert.assertEquals;

public class GetFeatureFlagAPITest {
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
    public void testRetrievingFeatureFlagInfoWhenNoLicenseKeyIsSet() throws Exception {
        String[] args = {"../../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        Assert.assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        if (StorageLayer.isInMemDb(process.main)) {
            // cause we keep all features enabled in memdb anyway
            return;
        }

        JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/ee/featureflag",
                null, 1000, 1000, null, WebserverAPI.getLatestCDIVersion().get(), "");
        assertEquals(3, response.entrySet().size());
        assertEquals("OK", response.get("status").getAsString());
        assertEquals(0, response.get("features").getAsJsonArray().size());
        JsonObject usageStats = response.get("usageStats").getAsJsonObject();

        if (StorageLayer.getStorage(process.getProcess()).getType() == STORAGE_TYPE.SQL) {
            JsonArray mauArr = usageStats.get("maus").getAsJsonArray();
            assertEquals(1, usageStats.entrySet().size());
            assertEquals(30, mauArr.size());
            assertEquals(0, mauArr.get(0).getAsInt());
            assertEquals(0, mauArr.get(29).getAsInt());
        } else {
            assertEquals(0, usageStats.entrySet().size());
        }

        process.kill();
        Assert.assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void testRetrievingFeatureFlagInfoWhenLicenseKeyIsSet() throws Exception {
        String[] args = {"../../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        Assert.assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        if (StorageLayer.isInMemDb(process.main)) {
            // cause we keep all features enabled in memdb anyway
            return;
        }

        FeatureFlag.getInstance(process.getProcess())
                .setLicenseKeyAndSyncFeatures(EETest.STATELESS_LICENSE_KEY_WITH_TEST_FEATURE_WITH_EXP);

        JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/ee/featureflag",
                null, 1000, 1000, null, WebserverAPI.getLatestCDIVersion().get(), "");
        assertEquals(3, response.entrySet().size());
        assertEquals("OK", response.get("status").getAsString());
        assertEquals(1, response.get("features").getAsJsonArray().size());
        assertEquals("test", response.get("features").getAsJsonArray().get(0).getAsString());
        JsonObject usageStats = response.get("usageStats").getAsJsonObject();

        if (StorageLayer.getStorage(process.getProcess()).getType() == STORAGE_TYPE.SQL) {
            JsonArray mauArr = usageStats.get("maus").getAsJsonArray();
            assertEquals(1, usageStats.entrySet().size());
            assertEquals(30, mauArr.size());
            assertEquals(0, mauArr.get(0).getAsInt());
            assertEquals(0, mauArr.get(29).getAsInt());
        } else {
            assertEquals(0, usageStats.entrySet().size());
        }


        process.kill();
        Assert.assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }
}
