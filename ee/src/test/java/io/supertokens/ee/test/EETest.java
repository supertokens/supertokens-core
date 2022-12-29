package io.supertokens.ee.test;

import io.supertokens.ProcessState;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlag;
import org.junit.*;
import org.junit.rules.TestRule;

public class EETest {

    public static final String OPAQUE_LICENSE_KEY = "TODO";
    public static final String OPAQUE_INVALID_LICENSE_KEY = "abcd";


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
    public void testRemovingLicenseKeyWhenItIsNotSet() throws Exception {
        String[] args = {"../../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        FeatureFlag featureFlag = FeatureFlag.getInstance(process.getProcess());

        // call removeLicenseKeyAndSyncFeatures
        featureFlag.removeLicenseKeyAndSyncFeatures();

        // check that there are no features enabled
        EE_FEATURES[] eeArray = featureFlag.getEnabledFeatures();
        Assert.assertEquals(0, eeArray.length);

        // check that isLicenseKeyPresent is false
        Assert.assertFalse(featureFlag.getEeFeatureFlagInstance().getIsLicenseKeyPresent());

        process.kill();
        Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testNoNetworkCallMadeWhenNoLicenseKey() throws Exception {
        String[] args = {"../../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        Assert.assertNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.LICENSE_KEY_CHECK_NETWORK_CALL, 2000));

        process.kill();
        Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testNetworkCallMadeWhenLicenseKeyPresent() throws Exception {
        String[] args = {"../../"};

        {
            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

            FeatureFlag.getInstance(process.main).setLicenseKeyAndSyncFeatures(OPAQUE_LICENSE_KEY);

            process.kill();
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }

        {
            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

            Assert.assertNotNull(
                    process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.LICENSE_KEY_CHECK_NETWORK_CALL));

            process.kill();
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }
    }
}
 