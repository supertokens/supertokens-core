package io.supertokens.ee.test;

import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.ee.test.httpRequest.HttpRequestForTesting;
import io.supertokens.ee.test.httpRequest.HttpResponseException;
import io.supertokens.featureflag.FeatureFlag;
import io.supertokens.webserver.WebserverAPI;

import static org.junit.Assert.assertEquals;

import org.junit.*;
import org.junit.rules.TestRule;

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
    }

    @Test
    public void testRetrievingLicenseKeyWhenItIsNotSet() throws Exception {
        String[] args = {"../../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/ee/license",
                null, 1000, 1000, null, WebserverAPI.getLatestCDIVersion(), "");
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
        FeatureFlag.getInstance(process.getProcess()).setLicenseKeyAndSyncFeatures(EETest.STATELESS_LICENSE_KEY_WITH_TEST_FEATURE_NO_EXP);

        // retrieve license key
        JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/ee/license",
                null, 1000, 1000, null, WebserverAPI.getLatestCDIVersion(), "");
        
        assertEquals(2, response.entrySet().size());
        assertEquals("OK", response.get("status").getAsString());
        assertEquals(EETest.STATELESS_LICENSE_KEY_WITH_TEST_FEATURE_NO_EXP, response.get("licenseKey").getAsString());
        
        process.kill();
        Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
