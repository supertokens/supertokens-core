package io.supertokens.ee.test;

import io.supertokens.ProcessState;
import io.supertokens.cronjobs.CronTaskTest;
import io.supertokens.ee.EEFeatureFlag;
import io.supertokens.ee.cronjobs.EELicenseCheck;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlag;
import io.supertokens.pluginInterface.KeyValueInfo;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.version.Version;
import org.junit.*;
import org.junit.rules.TestRule;

public class CronjobTest {

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
    public void cronjobUpdatesStatefulKey()
            throws InterruptedException, StorageQueryException, TenantOrAppNotFoundException {
        String[] args = {"../../"};

        {
            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

            StorageLayer.getStorage(process.main)
                    .setKeyValue(new TenantIdentifier(null, null, null), EEFeatureFlag.LICENSE_KEY_IN_DB,
                            new KeyValueInfo(EETest.OPAQUE_LICENSE_KEY_WITH_TEST_FEATURE));

            process.kill();
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }

        {
            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
            CronTaskTest.getInstance(process.main).setIntervalInSeconds(EELicenseCheck.RESOURCE_KEY, 4);
            process.startProcess();
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

            if (StorageLayer.getStorage(process.getProcess()).getType() == STORAGE_TYPE.SQL
                    && !Version.getVersion(process.getProcess()).getPluginName().equals("sqlite")) {
                Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures().length, 1);

                StorageLayer.getStorage(process.main)
                        .setKeyValue(new TenantIdentifier(null, null, null), EEFeatureFlag.LICENSE_KEY_IN_DB,
                                new KeyValueInfo(EETest.OPAQUE_INVALID_LICENSE_KEY));

                // wait for cronjob to run once which could clear the features cause the new key is invalid
                ProcessState.getInstance(process.main).clear();
                Thread.sleep(5000);
                Assert.assertNotNull(
                        process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.LICENSE_KEY_CHECK_NETWORK_CALL, 1000));
                Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures().length, 0);
            }
            process.kill();
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }
    }

    @Test
    public void cronjobDoesNotUpdatesStatefulKeyIfItDoesntRun()
            throws InterruptedException, StorageQueryException, TenantOrAppNotFoundException {
        String[] args = {"../../"};

        {
            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

            StorageLayer.getStorage(process.main)
                    .setKeyValue(new TenantIdentifier(null, null, null), EEFeatureFlag.LICENSE_KEY_IN_DB,
                            new KeyValueInfo(EETest.OPAQUE_LICENSE_KEY_WITH_TEST_FEATURE));

            process.kill();
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }

        {
            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
            process.startProcess();
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

            if (StorageLayer.getStorage(process.getProcess()).getType() == STORAGE_TYPE.SQL
                    && !Version.getVersion(process.getProcess()).getPluginName().equals("sqlite")) {
                Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures().length, 1);

                StorageLayer.getStorage(process.main)
                        .setKeyValue(new TenantIdentifier(null, null, null), EEFeatureFlag.LICENSE_KEY_IN_DB,
                                new KeyValueInfo(EETest.OPAQUE_INVALID_LICENSE_KEY));

                // the cronjob shouldn't run so there should be no change in the feature flag
                ProcessState.getInstance(process.main).clear();
                Thread.sleep(5000);
                Assert.assertNull(
                        process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.LICENSE_KEY_CHECK_NETWORK_CALL, 1000));

                Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures().length, 1);
            }
            process.kill();
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }
    }

    @Test
    public void cronjobUpdatesStatelessKey()
            throws InterruptedException, StorageQueryException, TenantOrAppNotFoundException {
        String[] args = {"../../"};

        {
            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

            StorageLayer.getStorage(process.main)
                    .setKeyValue(new TenantIdentifier(null, null, null), EEFeatureFlag.LICENSE_KEY_IN_DB,
                            new KeyValueInfo(EETest.STATELESS_LICENSE_KEY_WITH_TEST_FEATURE_NO_EXP));

            process.kill();
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }

        {
            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
            CronTaskTest.getInstance(process.main).setIntervalInSeconds(EELicenseCheck.RESOURCE_KEY, 4);
            process.startProcess();
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

            if (StorageLayer.getStorage(process.getProcess()).getType() == STORAGE_TYPE.SQL
                    && !Version.getVersion(process.getProcess()).getPluginName().equals("sqlite")) {
                Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures().length, 1);

                StorageLayer.getStorage(process.main)
                        .setKeyValue(new TenantIdentifier(null, null, null), EEFeatureFlag.LICENSE_KEY_IN_DB,
                                new KeyValueInfo(EETest.STATELESS_INVALID_LICENSE_KEY));

                // wait for cronjob to run once which could clear the features cause the new key is invalid
                ProcessState.getInstance(process.main).clear();
                Thread.sleep(5000);
                Assert.assertNull(
                        process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.LICENSE_KEY_CHECK_NETWORK_CALL, 1000));
                Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures().length, 0);
            }
            process.kill();
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }
    }

    @Test
    public void cronjobDoesNotUpdatesStatelessKeyIfItDoesntRun()
            throws InterruptedException, StorageQueryException, TenantOrAppNotFoundException {
        String[] args = {"../../"};

        {
            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

            StorageLayer.getStorage(process.main)
                    .setKeyValue(new TenantIdentifier(null, null, null), EEFeatureFlag.LICENSE_KEY_IN_DB,
                            new KeyValueInfo(EETest.STATELESS_LICENSE_KEY_WITH_TEST_FEATURE_NO_EXP));

            process.kill();
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }

        {
            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
            process.startProcess();
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

            if (StorageLayer.getStorage(process.getProcess()).getType() == STORAGE_TYPE.SQL
                    && !Version.getVersion(process.getProcess()).getPluginName().equals("sqlite")) {
                Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures().length, 1);

                StorageLayer.getStorage(process.main)
                        .setKeyValue(new TenantIdentifier(null, null, null), EEFeatureFlag.LICENSE_KEY_IN_DB,
                                new KeyValueInfo(EETest.STATELESS_INVALID_LICENSE_KEY));

                // the cronjob shouldn't run so there should be no change in the feature flag
                ProcessState.getInstance(process.main).clear();
                Thread.sleep(5000);
                Assert.assertNull(
                        process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.LICENSE_KEY_CHECK_NETWORK_CALL, 1000));

                Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures().length, 1);
            }
            process.kill();
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }
    }

    @Test
    public void testThatInvalidOpaqueKeyOnCronjobCausesNoFeaturesToBeLoaded() throws Exception {
        String[] args = {"../../"};

        {
            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
            CronTaskTest.getInstance(process.main).setIntervalInSeconds(EELicenseCheck.RESOURCE_KEY, 4);
            process.startProcess();
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

            FeatureFlag.getInstance(process.main)
                    .setLicenseKeyAndSyncFeatures(EETest.OPAQUE_LICENSE_KEY_WITH_TEST_FEATURE);
            Assert.assertNotNull(
                    process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.LICENSE_KEY_CHECK_NETWORK_CALL));

            Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures().length, 1);
            Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures()[0], EE_FEATURES.TEST);

            StorageLayer.getStorage(process.main)
                    .setKeyValue(new TenantIdentifier(null, null, null), EEFeatureFlag.LICENSE_KEY_IN_DB,
                            new KeyValueInfo(EETest.OPAQUE_INVALID_LICENSE_KEY));

            Thread.sleep(5000);

            Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures().length, 0);

            process.kill();
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }
    }

    @Test
    public void testThatInvalidStatelessKeyOnCronjobCausesNoFeaturesToBeLoaded() throws Exception {
        String[] args = {"../../"};

        {
            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
            CronTaskTest.getInstance(process.main).setIntervalInSeconds(EELicenseCheck.RESOURCE_KEY, 4);
            process.startProcess();
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

            FeatureFlag.getInstance(process.main)
                    .setLicenseKeyAndSyncFeatures(EETest.STATELESS_LICENSE_KEY_WITH_TEST_AND_RANDOM_FEATURE_WITH_EXP);

            Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures().length, 1);
            Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures()[0], EE_FEATURES.TEST);

            StorageLayer.getStorage(process.main)
                    .setKeyValue(new TenantIdentifier(null, null, null), EEFeatureFlag.LICENSE_KEY_IN_DB,
                            new KeyValueInfo(EETest.STATELESS_INVALID_LICENSE_KEY));

            Thread.sleep(5000);

            Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures().length, 0);

            process.kill();
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }
    }

    @Test
    public void cronjobRemovesFeaturesIfJWTExpired() throws Exception {
        String[] args = {"../../"};

        {
            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
            CronTaskTest.getInstance(process.main).setIntervalInSeconds(EELicenseCheck.RESOURCE_KEY, 4);
            process.startProcess();
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

            FeatureFlag.getInstance(process.main)
                    .setLicenseKeyAndSyncFeatures(EETest.STATELESS_LICENSE_KEY_WITH_TEST_AND_RANDOM_FEATURE_WITH_EXP);

            Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures().length, 1);
            Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures()[0], EE_FEATURES.TEST);

            StorageLayer.getStorage(process.main)
                    .setKeyValue(new TenantIdentifier(null, null, null), EEFeatureFlag.LICENSE_KEY_IN_DB,
                            new KeyValueInfo(EETest.STATELESS_LICENSE_KEY_EXPIRED));

            Thread.sleep(5000);

            Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures().length, 0);

            process.kill();
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }
    }
}
 