package io.supertokens.ee.test.api;

import static org.junit.Assert.assertEquals;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import com.google.gson.JsonObject;

import io.supertokens.ProcessState.PROCESS_STATE;
import io.supertokens.ee.test.EETest;
import io.supertokens.ee.test.TestingProcessManager;
import io.supertokens.ee.test.Utils;
import io.supertokens.ee.test.httpRequest.HttpRequestForTesting;
import io.supertokens.featureflag.FeatureFlag;
import io.supertokens.webserver.WebserverAPI;

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
        String[] args = { "../../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        Assert.assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/ee/featureflag",
                null, 1000, 1000, null, WebserverAPI.getLatestCDIVersion(), "");
        assertEquals(3, response.entrySet().size());
        assertEquals("OK", response.get("status").getAsString());
        assertEquals(0, response.get("features").getAsJsonArray().size());
        assertEquals(0, response.get("usageStats").getAsJsonObject().entrySet().size());

        process.kill();
        Assert.assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void testRetrievingFeatureFlagInfoWhenLicenseKeyIsSet() throws Exception {
        String[] args = { "../../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        Assert.assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        FeatureFlag.getInstance(process.getProcess())
                .setLicenseKeyAndSyncFeatures(EETest.STATELESS_LICENSE_KEY_WITH_TEST_FEATURE_WITH_EXP);

        JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/ee/featureflag",
                null, 1000, 1000, null, WebserverAPI.getLatestCDIVersion(), "");
        assertEquals(3, response.entrySet().size());
        assertEquals("OK", response.get("status").getAsString());
        assertEquals(1, response.get("features").getAsJsonArray().size());
        assertEquals("test", response.get("features").getAsJsonArray().get(0).getAsString());
        assertEquals(0, response.get("usageStats").getAsJsonObject().entrySet().size());

        process.kill();
        Assert.assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }
}
