package io.supertokens.ee.test.api;

import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.ProcessState.PROCESS_STATE;
import io.supertokens.ee.test.EETest;
import io.supertokens.ee.test.TestingProcessManager;
import io.supertokens.ee.test.Utils;
import io.supertokens.ee.test.httpRequest.HttpRequestForTesting;
import io.supertokens.featureflag.FeatureFlag;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.webserver.WebserverAPI;
import org.junit.*;
import org.junit.rules.TestRule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class GetLicenseKeyAPITest {
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

    @Test
    public void testRetrievingLicenseKeyWhenItIsNotSet() throws Exception {
        String[] args = {"../../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/ee/license",
                null, 10000, 10000, null, WebserverAPI.getLatestCDIVersion().get(), "");
        Assert.assertEquals(1, response.entrySet().size());
        Assert.assertEquals("NO_LICENSE_KEY_FOUND_ERROR", response.get("status").getAsString());

        process.kill();
        Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testRetrievingLicenseKeyWhenItIsSet() throws Exception {
        String[] args = {"../../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // set license key
        FeatureFlag.getInstance(process.getProcess())
                .setLicenseKeyAndSyncFeatures(EETest.STATELESS_LICENSE_KEY_WITH_TEST_FEATURE_NO_EXP);

        // retrieve license key
        JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/ee/license",
                null, 10000, 10000, null, WebserverAPI.getLatestCDIVersion().get(), "");

        assertEquals(2, response.entrySet().size());
        assertEquals("OK", response.get("status").getAsString());
        assertEquals(EETest.STATELESS_LICENSE_KEY_WITH_TEST_FEATURE_NO_EXP, response.get("licenseKey").getAsString());

        process.kill();
        Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testRetrievingLicenseKeyWhenEEFolderDoesNotExist() throws Exception {
        FeatureFlag.clearURLClassLoader();
        String[] args = {"../../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);

        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.EE_FOLDER_LOCATION, "random");
        process.startProcess();

        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        Assert.assertNull(FeatureFlag.getInstance(process.main).getEeFeatureFlagInstance());

        Assert.assertEquals(FeatureFlag.getInstance(process.getProcess()).getEnabledFeatures().length, 0);

        // retrieve license key
        JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/ee/license",
                null, 10000, 10000, null, WebserverAPI.getLatestCDIVersion().get(), "");

        Assert.assertEquals(1, response.entrySet().size());
        Assert.assertEquals("NO_LICENSE_KEY_FOUND_ERROR", response.get("status").getAsString());

        process.kill();
        Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
