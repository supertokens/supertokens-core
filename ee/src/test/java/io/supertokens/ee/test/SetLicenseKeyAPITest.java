package io.supertokens.ee.test;

import static org.junit.Assert.assertEquals;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import com.google.gson.JsonObject;

import io.supertokens.ProcessState.PROCESS_STATE;
import io.supertokens.ee.test.httpRequest.HttpRequestForTesting;
import io.supertokens.featureflag.FeatureFlag;
import io.supertokens.webserver.WebserverAPI;

public class SetLicenseKeyAPITest {
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
    public void testSettingBadInput() throws Exception {
        String[] args = { "../../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        Assert.assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("licenseKey", 123);

        JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                "http://localhost:3567/ee/license",
                requestBody, 1000, 1000, null, WebserverAPI.getLatestCDIVersion(), "");

        System.out.println(response);

        process.kill();
        Assert.assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void testSettingLicenseKeySuccessfully() throws Exception {
        String[] args = { "../../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        Assert.assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("licenseKey", EETest.STATELESS_LICENSE_KEY_WITH_TEST_FEATURE_NO_EXP);

        JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                "http://localhost:3567/ee/license",
                requestBody, 1000, 1000, null, WebserverAPI.getLatestCDIVersion(), "");
        assertEquals(1, response.entrySet().size());
        assertEquals("OK", response.get("status").getAsString());

        // retrieve license key to check that it was correctly set
        String licenseKey = FeatureFlag.getInstance(process.getProcess()).getLicenseKey();
        assertEquals(EETest.STATELESS_LICENSE_KEY_WITH_TEST_FEATURE_NO_EXP, licenseKey);

        process.kill();
        Assert.assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void testSettingInvalidLicenseKey() throws Exception {
        String[] args = { "../../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        Assert.assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        {
            JsonObject requestBody = new JsonObject();

            requestBody.addProperty("licenseKey", "invalidKey123");

            JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                    "http://localhost:3567/ee/license",
                    requestBody, 1000, 1000, null, WebserverAPI.getLatestCDIVersion(), "");

            assertEquals(1, response.entrySet().size());
            assertEquals("INVALID_LICENSE_KEY_ERROR", response.get("status").getAsString());
        }

        process.kill();
        Assert.assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }
}
