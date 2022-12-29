package io.supertokens.ee.test;

import io.supertokens.ProcessState;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlag;
import io.supertokens.featureflag.exceptions.InvalidLicenseKeyException;
import io.supertokens.httpRequest.HttpResponseException;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.version.Version;
import org.junit.*;
import org.junit.rules.TestRule;

import java.io.IOException;

import static org.junit.Assert.fail;

public class EETest {

    public static final String OPAQUE_LICENSE_KEY_WITH_TEST_FEATURE =
            "t7D8y1ekZ-sdGXaPBeY0q3lSV3TraGTDG9Uj6CiHpFT2Zmke0COrW" +
                    "=oP8ELgZcyUUdWFWVJD2Hu=BWtONBh8LlDNvg2d7sI2WnsludXyng=PT56UcKdbVexCcj7zg-Aa";
    public static final String OPAQUE_INVALID_LICENSE_KEY = "abcd";

    public static final String STATELESS_LICENSE_KEY_WITH_TEST_FEATURE = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9" +
            ".eyJlbmFibGVkRmVhdHVyZXMiOlsidGVzdCJdLCJzY29wZSI6InRlc3QtRW5hYmxlZEZlYXR1cmVzPVRFU1QtTm8tRVhQIiwic3ViIjoiMGRkMDhlZmYtYzBmMy00ZDk1LTkxZjgtNDAzMTllNzA2ZGVmIiwiaWF0IjoxNjcyMzAyMjQ3fQ.EwKBbr3Fbo5qR2cbGjJgUl38ypjBu6pRmWQE5sCHAzuD1HnGRWxgRkjVMfcTPrT1QA3VNVLcRgEhTJOMGWIffKjK3YrI5d7qNHSiNfgYaf3qbTbn4LJCObATxa9cPhi3dK1VQJtMbGWo5SGwEGKG27G0bhJyVTmeeMilNJ-N5k0hodRJrOn97milkljJYGiewC9AhM35b1p7fuoxDOG69E6ZMlrQfCHSnheQEjFLtkaLHUptzmU57vsyizK85zm-1NL-f4bLPjtWBcYpzhI89MCss1fCiYEHJiMqh6SAeI1R5VTouer3Kp9JqfbF33CGOYj-dSHLrPkA6ME-gFtdlQ";


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
    public void testLoadingValidOpaqueKey() throws Exception {
        String[] args = {"../../"};

        {
            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

            FeatureFlag.getInstance(process.main).setLicenseKeyAndSyncFeatures(OPAQUE_LICENSE_KEY_WITH_TEST_FEATURE);
            Assert.assertNotNull(
                    process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.LICENSE_KEY_CHECK_NETWORK_CALL));

            Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures().length, 1);
            Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures()[0], EE_FEATURES.TEST);

            process.kill();
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }
    }

    @Test
    public void testNetworkCallMadeOnCoreStartIfLicenseKeyPresent() throws Exception {
        String[] args = {"../../"};

        // we do this test only for non in mem db cause it requires saving the license key across
        // core restarts..

        {
            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

            if (StorageLayer.getStorage(process.getProcess()).getType() == STORAGE_TYPE.SQL
                    && !Version.getVersion(process.getProcess()).getPluginName().equals("sqlite")) {
                FeatureFlag.getInstance(process.main)
                        .setLicenseKeyAndSyncFeatures(OPAQUE_LICENSE_KEY_WITH_TEST_FEATURE);
                Assert.assertNotNull(
                        process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.LICENSE_KEY_CHECK_NETWORK_CALL));

                Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures().length, 1);
                Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures()[0], EE_FEATURES.TEST);
            }

            process.kill();
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }

        {
            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
            Assert.assertNull(
                    process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.LICENSE_KEY_CHECK_NETWORK_CALL, 1000));
            process.startProcess();
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

            if (StorageLayer.getStorage(process.getProcess()).getType() == STORAGE_TYPE.SQL
                    && !Version.getVersion(process.getProcess()).getPluginName().equals("sqlite")) {

                Assert.assertNotNull(
                        process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.LICENSE_KEY_CHECK_NETWORK_CALL));

                Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures().length, 1);
                Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures()[0], EE_FEATURES.TEST);

            }

            process.kill();
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }
    }

    @Test
    public void invalidNewLicenseKeyNotAllowed()
            throws InterruptedException, StorageQueryException, HttpResponseException, IOException {
        String[] args = {"../../"};

        {
            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

            try {
                FeatureFlag.getInstance(process.main).setLicenseKeyAndSyncFeatures(OPAQUE_INVALID_LICENSE_KEY);
                fail();
            } catch (InvalidLicenseKeyException ignored) {
            }

            process.kill();
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }
    }

    @Test
    public void testLoadingValidStatelessKey() throws Exception {
        String[] args = {"../../"};

        {
            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

            FeatureFlag.getInstance(process.main).setLicenseKeyAndSyncFeatures(STATELESS_LICENSE_KEY_WITH_TEST_FEATURE);
            Assert.assertNull(
                    process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.LICENSE_KEY_CHECK_NETWORK_CALL, 1000));

            Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures().length, 1);
            Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures()[0], EE_FEATURES.TEST);

            process.kill();
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }
    }

    @Test
    public void testNetworkCallIsNotMadeOnCoreStartIfStatelessLicenseKeyPresent() throws Exception {
        String[] args = {"../../"};

        // we do this test only for non in mem db cause it requires saving the license key across
        // core restarts..

        {
            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

            if (StorageLayer.getStorage(process.getProcess()).getType() == STORAGE_TYPE.SQL
                    && !Version.getVersion(process.getProcess()).getPluginName().equals("sqlite")) {
                FeatureFlag.getInstance(process.main)
                        .setLicenseKeyAndSyncFeatures(STATELESS_LICENSE_KEY_WITH_TEST_FEATURE);
                Assert.assertNull(
                        process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.LICENSE_KEY_CHECK_NETWORK_CALL, 1000));

                Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures().length, 1);
                Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures()[0], EE_FEATURES.TEST);
            }

            process.kill();
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }

        {
            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
            Assert.assertNull(
                    process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.LICENSE_KEY_CHECK_NETWORK_CALL, 1000));
            process.startProcess();
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

            if (StorageLayer.getStorage(process.getProcess()).getType() == STORAGE_TYPE.SQL
                    && !Version.getVersion(process.getProcess()).getPluginName().equals("sqlite")) {

                Assert.assertNull(
                        process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.LICENSE_KEY_CHECK_NETWORK_CALL, 1000));

                Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures().length, 1);
                Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures()[0], EE_FEATURES.TEST);

            }

            process.kill();
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }
    }
}
 