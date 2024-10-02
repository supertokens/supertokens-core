package io.supertokens.ee.test.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

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
import io.supertokens.featureflag.exceptions.NoLicenseKeyFoundException;
import io.supertokens.webserver.WebserverAPI;

public class DeleteLicenseKeyAPITest {
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
    public void testDeletingLicenseKeyWhenItIsNotSet() throws Exception {
        String[] args = {"../../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        Assert.assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        // check that no LicenseKey exits
        try {
            FeatureFlag.getInstance(process.main).getLicenseKey();
            fail();
        } catch (NoLicenseKeyFoundException ignored) {
        }

        JsonObject response = HttpRequestForTesting.sendJsonDELETERequest(process.getProcess(), "",
                "http://localhost:3567/ee/license",
                null, 10000, 10000, null, WebserverAPI.getLatestCDIVersion().get(), "");
        assertEquals(1, response.entrySet().size());
        assertEquals("OK", response.get("status").getAsString());

        // check that no LicenseKey exits
        try {
            FeatureFlag.getInstance(process.main).getLicenseKey();
            fail();
        } catch (NoLicenseKeyFoundException ignored) {
        }

        process.kill();
        Assert.assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void testDeletingLicenseKey() throws Exception {
        String[] args = {"../../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        Assert.assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        // set license key
        FeatureFlag.getInstance(process.getProcess())
                .setLicenseKeyAndSyncFeatures(EETest.STATELESS_LICENSE_KEY_WITH_TEST_FEATURE_NO_EXP);

        // check that license key is successfully set
        assertEquals(EETest.STATELESS_LICENSE_KEY_WITH_TEST_FEATURE_NO_EXP,
                FeatureFlag.getInstance(process.getProcess()).getLicenseKey());

        JsonObject response = HttpRequestForTesting.sendJsonDELETERequest(process.getProcess(), "",
                "http://localhost:3567/ee/license",
                null, 10000, 10000, null, WebserverAPI.getLatestCDIVersion().get(), "");
        assertEquals(1, response.entrySet().size());
        assertEquals("OK", response.get("status").getAsString());

        // check that no LicenseKey exits
        try {
            FeatureFlag.getInstance(process.main).getLicenseKey();
            fail();
        } catch (NoLicenseKeyFoundException ignored) {
        }

        process.kill();
        Assert.assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

}
