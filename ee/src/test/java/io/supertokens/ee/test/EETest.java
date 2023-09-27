package io.supertokens.ee.test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import io.supertokens.ProcessState;
import io.supertokens.ee.EEFeatureFlag;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlag;
import io.supertokens.featureflag.exceptions.InvalidLicenseKeyException;
import io.supertokens.featureflag.exceptions.NoLicenseKeyFoundException;
import io.supertokens.httpRequest.HttpRequestMocking;
import io.supertokens.httpRequest.HttpResponseException;
import io.supertokens.pluginInterface.KeyValueInfo;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.version.Version;
import org.junit.*;
import org.junit.rules.TestRule;
import org.mockito.Mockito;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.Assert.*;

public class EETest extends Mockito {

    public static final String OPAQUE_LICENSE_KEY_WITH_TEST_FEATURE =
            "t7D8y1ekZ-sdGXaPBeY0q3lSV3TraGTDG9Uj6CiHpFT2Zmke0COrW" +
                    "=oP8ELgZcyUUdWFWVJD2Hu=BWtONBh8LlDNvg2d7sI2WnsludXyng=PT56UcKdbVexCcj7zg-Aa";
    public static final String OPAQUE_LICENSE_KEY_WITH_EMPTY_FEATURE =
            "HrExZ9Vq7mnvyjTlgO8DTTwiXTDnbaPihQSaqCcftxVKTc=c-y5XkJnZRbudRysk-GFH0FU7x5W387P-yCPHnB" +
                    "-rMKbcci8kFDwwfiwAsM9sSl-s1UbrqWXjP0EqF2uO";
    public static final String OPAQUE_LICENSE_KEY_THAT_IS_REVOKED = "niFM" +
            "=FhjugpjM2gcoIDMAAk776gX8EnxkhGpxi8YMv7HHUEBC-tXXJBBOL45QOJ5yivFQdBJFHU=MWfNhu8hCxLCm1=nfZQ5V-FTDT2Hm" +
            "=4VKm6JgG4NXdzqqAx4dzwl";
    public static final String OPAQUE_LICENSE_KEY_WITH_TEST_AND_RANDOM_FEATURE = "hXtnJKthVhKdcAO1692agPrPNx3t" +
            "=Kh5iZKuHbvfHe-iC5EnFcjQJWHmXnWtq40nRu-YEDYvj8N3R1V5M-bNj1iBc0a=-gZTP=fORe84aV-xrQlM6qGynJJSqYKfjd7i";
    public static final String OPAQUE_LICENSE_KEY_WITH_RANDOM_FEATURE = "ANg4QU6GaxjlTDjXQKyNP55CexB87" +
            "=yOetPcQa5XaRe2HUbPh90RLVDj6AFfniKSOVC-KRZUNyyFq2=41SwpyutJcpS3yQWKEeGJpMMjTLwcFetFoJTZeezwi3=fdPXj";
    public static final String OPAQUE_INVALID_LICENSE_KEY = "abcd";

    public static final String STATELESS_LICENSE_KEY_WITH_TEST_FEATURE_NO_EXP = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9" +
            ".eyJlbmFibGVkRmVhdHVyZXMiOlsidGVzdCJdLCJzY29wZSI6InRlc3QtRW5hYmxlZEZlYXR1cmVzPVRFU1QtTm8tRVhQIiwic3ViIjoiMGRkMDhlZmYtYzBmMy00ZDk1LTkxZjgtNDAzMTllNzA2ZGVmIiwiaWF0IjoxNjcyMzAyMjQ3fQ.EwKBbr3Fbo5qR2cbGjJgUl38ypjBu6pRmWQE5sCHAzuD1HnGRWxgRkjVMfcTPrT1QA3VNVLcRgEhTJOMGWIffKjK3YrI5d7qNHSiNfgYaf3qbTbn4LJCObATxa9cPhi3dK1VQJtMbGWo5SGwEGKG27G0bhJyVTmeeMilNJ-N5k0hodRJrOn97milkljJYGiewC9AhM35b1p7fuoxDOG69E6ZMlrQfCHSnheQEjFLtkaLHUptzmU57vsyizK85zm-1NL-f4bLPjtWBcYpzhI89MCss1fCiYEHJiMqh6SAeI1R5VTouer3Kp9JqfbF33CGOYj-dSHLrPkA6ME-gFtdlQ";
    public static final String STATELESS_LICENSE_KEY_WITH_EMPTY_FEATURE_NO_EXP =
            "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9" +
                    ".eyJlbmFibGVkRmVhdHVyZXMiOltdLCJzY29wZSI6InRlc3QtRW5hYmxlZEZlYXR1cmVzPVtdLU5vLUVYUCIsInN1YiI6IjBkZDA4ZWZmLWMwZjMtNGQ5NS05MWY4LTQwMzE5ZTcwNmRlZiIsImlhdCI6MTY3MjMwMjE5Mn0.KhwaPVV9cD7Slzh-55DlJdeCZ-L7rlYv7oXNdHkowrQFsVuQjYJ-42D_o5Ucidf8JpOiTK8b9PZL_es40WqDXOYJ1sikL8Ruc8zXN3NTjZRXZAK1W9zREdTcuUuFI967N-OikixSGHwCc0__BhyKhYCAA9GDD2Up9t4GHmZPXgQFEFYovLQ7KTaCeAjX_PKIucLAJ4puUiQ5LTcUPT5DSHRaV0YB7r6CcBWJaOnxw2HmdEXmpXZ-bGjFYh0I8j1RxmORu55S6UmAWaJO7aH1SybJvXd0imwu98x75r3Pl8-_r0-Pvg4wOtgqV6-By0degLMpYPrXEXD8VsODfg2DDw";
    public static final String STATELESS_LICENSE_KEY_EXPIRED = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9" +
            ".eyJlbmFibGVkRmVhdHVyZXMiOltdLCJzY29wZSI6InRlc3QtRW5hYmxlZEZlYXR1cmVzPVtdLUVYUCIsInN1YiI6IjBkZDA4ZWZmLWMwZjMtNGQ5NS05MWY4LTQwMzE5ZTcwNmRlZiIsImV4cCI6MTY3MjMwMjU0MSwiaWF0IjoxNjcyMzAyNTQxfQ.LfoAg6t6mX4CqziaFgGpWmVinTThSkAhHk-nqmShXabvTyU0bss_UIG-ekcXY14iDBWNu9tZPuB7is3i5k4eU9oknFkb9syw0eYguAfwieDgS0pVtHhHQvq440hUhhSRPKzjxrByfgHlC2G0wyTFOdZclfgL3z6CYBMpXfvVW0jxc-1k-vhVw3nk0ZK_wY8ymPfJR-U0v531VCo934yNOv3x31HSp6RpM9nOw7olOpPyQDihUSgexw1cgkYQziOJ96x1FWrWSXGF57vSzct2cZ3OwOKZWxLJQeP8j4vzwMzm0KRNJtgzlP8cnl5rqqxkubJkaBYDJylE4FdBuN7oOg";
    public static final String STATELESS_LICENSE_KEY_WITH_TEST_FEATURE_WITH_EXP =
            "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9" +
                    ".eyJlbmFibGVkRmVhdHVyZXMiOlsidGVzdCJdLCJzY29wZSI6InRlc3QtRW5hYmxlZEZlYXR1cmVzPVRFU1QtTk9OLUVYUCIsInN1YiI6IjBkZDA4ZWZmLWMwZjMtNGQ5NS05MWY4LTQwMzE5ZTcwNmRlZiIsImV4cCI6MTkyNDc2MzM5OSwiaWF0IjoxNjcyMzAyNjIxfQ.pzgPwvW4aw0bLLt4Og8J2gcDFeM0XEFJpIsIQ75k7V1YuJPHNLYQ4n3q6_fArR1m7M4gA-irlGvuguwPRak42mtyH9Tj6FNd4Afstylus3V6A1Q_IBHBioDesEzWD0qm7fxQAKQT7GOkxq2yUbfaD8TvZj_gdWlSRQfZR0OvymkqkJ6CR-kK-ovCXeUakxSA8tQABQy0_H1KD0W71lraOejMdeTbcGljXnkYa_rgnml115wvu_SHEezugSnqwSyZ0Eoq5vZsDLZ8J0U0fuDt8Um7JtvfgkSrLvcpGkXL9THuxVC9bfuq_i-169hjSS02mu2mp5es2CkIw5YW-oddPg";
    public static final String STATELESS_LICENSE_KEY_EMPTY_FEATURE_WITH_EXP = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9" +
            ".eyJlbmFibGVkRmVhdHVyZXMiOltdLCJzY29wZSI6InRlc3QtRW5hYmxlZEZlYXR1cmVzPVtdLU5PTi1FWFAiLCJzdWIiOiIwZGQwOGVmZi1jMGYzLTRkOTUtOTFmOC00MDMxOWU3MDZkZWYiLCJleHAiOjE5MjQ3NjM0MjgsImlhdCI6MTY3MjMwMjY1MH0.CxUOCUA7Rqp94FOKIQ9pFLYcEwwXh8WZelJqmFhVpeBKQA9_YWki5xML08uJE-3Ls64m1T4a9kGmgc887qPlrWLRwoFm0UGlXC5QLLR3XBQ5h7SBijMb-5pS8fUD6Qh9sM1U2cT6gWrz52QBUNtBh9UmXiRznAx_5-uduKORXmCRbsgMntyDmZudzE6vP-cGoZqoCZDntCXCBCeBimdaH2zrjI4ylGFlFbqUdv3pUCW5fgtUSgOUHv4Vpy-2ruw8QuXi6ybapZSgb9uxv15h7ExtC9IyKHv7hhtuyx0F9V4U7zaoDsKQP3TdoznDG2kh8uWdlt6FGZDwvVxhVrSUng";
    public static final String STATELESS_LICENSE_KEY_WITH_TEST_AND_RANDOM_FEATURE_WITH_EXP =
            "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9" +
                    ".eyJlbmFibGVkRmVhdHVyZXMiOlsiYWJjZGUiLCJ0ZXN0Il0sInNjb3BlIjoidGVzdC1FbmFibGVkRmVhdHVyZXM9QUJDREUsVEVTVC1OT04tRVhQIiwic3ViIjoiMGRkMDhlZmYtYzBmMy00ZDk1LTkxZjgtNDAzMTllNzA2ZGVmIiwiZXhwIjoxOTI0NzYzOTU4LCJpYXQiOjE2NzIzMDMxODB9.JInMiQWfnIRlAV_Ss1g5L8y0szywDufOvjJdbokP3V_2IdhxiYAI6mo5jiu7iUoNL4qBndX4Vqc1q_zYftltuT1RqjkRn7Swi1zAqoDWPpvwRTGHDOCvRhanZCH_BnDB7VcJ3YmJZPfjZbmhEm53ohkrAuqOLiv4zdt0-CPbuFG3hbScRlB_7FDEhimKbSY6X2BLhzf9nuELK2N-T2IK7TdWldqM40m76eCCdN2tiRL_N8_hX6dgmd449jShlUh4FWdB4CU6LnUjUOsCMM2aLhkGAj6VKkdYK9woUgaKNZRzkc5GGCD5z0sA2U8i137qXbI9N_xtmO7EuUmnPXtaaA";
    public static final String STATELESS_LICENSE_KEY_WITH_RANDOM_FEATURE_WITH_EXP =
            "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9" +
                    ".eyJlbmFibGVkRmVhdHVyZXMiOlsiYWJjZGUiXSwic2NvcGUiOiJ0ZXN0LUVuYWJsZWRGZWF0dXJlcz1BQkNERS1OT04tRVhQIiwic3ViIjoiMGRkMDhlZmYtYzBmMy00ZDk1LTkxZjgtNDAzMTllNzA2ZGVmIiwiZXhwIjoxOTI0NzYzOTk5LCJpYXQiOjE2NzIzMDMyMjF9.i4p7gV5ZiTBnXazEqDpxpps1zyPZYBY4UrqL8aynrvp5Ubd4JL-NfJ4swnB9onDs-FkV7gsivzh6fIe6YUhvsJAfRzumEy0CGrqN2NSVtV_Gn_3DKClu_A5WBk1usAdJK0-vCrgY5C1RvrEnxUBWIl7Ss1yKdjqdMSi3It60Jt9RMJxSqQq4D_MPo2YQ0veCCzCc9RJ3XEYaYdDSvzRDVfYAc3LvQd0k2Ii1dNbiJMznp-PuxNfYe_1gXsXnZZPtaCSSLmVlhP-LA7vC-TncKOn5sox2i-Q1e7Wl5KEeGRTsgS8G0AEguidJDX9G1NFjhBsdFY99mRoqNyPerJ7N0Q";
    public static final String STATELESS_INVALID_LICENSE_KEY =
            "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9" +
                    ".eyJlbmFibGVkRmVhdHVyZXMiOlsidGVzdCJdLCJzY29wZSI6InRlc3QtRW5hYmxlZEZlYXR1cmVzPVRFU1QtTm8tRVhQIiwic3ViIjoiMGRkMDhlZmYtYzBmMy00ZDk1LTkxZjgtNDAzMTllNzA2ZGVmIiwiaWF0IjoxNjcyMzAyMjQ3fQ.EwKBbr3Fbo5qR2cbGjJgUl38ypjBu6pRmWQE5sCHAzuD1HnGRWxgRkjVMfcTPrT1QA3VNVLcRgEhTJOMGWIffKjK3YrI5d7qNHSiNfgYaf3qbTbn4LJCObATxa9cPhi3dK1VQJtMbGWo5SGwEGKG27G0bhJyVTmeeMilNJ-N5k0hodRJrOn97milkljJYGiewC9AhM35b1p7fuoxDOG69E6ZMlrQfCHSnheQEjFLtkaLHUptzmU57vsyizK85zm-1NL-f4bLPjtWBcYpzhI89MCYs1fCiYEHJiMqh6SAeI1R5VTouer3Kp9JqfbF33CGOYj-dSHLrPkA6ME-gFtdlQ";


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

    @Rule
    public Retry retry = new Retry(3);

    @Test
    public void testRemovingLicenseKeyWhenItIsNotSet() throws Exception {
        String[] args = {"../../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.isInMemDb(process.main)) {
            // cause we keep all features enabled in memdb anyway
            return;
        }

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

            if (StorageLayer.isInMemDb(process.main)) {
                // cause we keep all features enabled in memdb anyway
                return;
            }

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
    public void invalidNewStatefulLicenseKeyNotAllowed()
            throws InterruptedException, StorageQueryException, HttpResponseException, IOException,
            TenantOrAppNotFoundException {
        String[] args = {"../../"};

        {
            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

            try {
                FeatureFlag.getInstance(process.main).setLicenseKeyAndSyncFeatures(OPAQUE_INVALID_LICENSE_KEY);
                fail();
            } catch (InvalidLicenseKeyException ignored) {
            }

            Assert.assertNotNull(
                    process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.LICENSE_KEY_CHECK_NETWORK_CALL));

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

            if (StorageLayer.isInMemDb(process.main)) {
                // cause we keep all features enabled in memdb anyway
                return;
            }

            FeatureFlag.getInstance(process.main).setLicenseKeyAndSyncFeatures(
                    STATELESS_LICENSE_KEY_WITH_TEST_FEATURE_NO_EXP);
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
                        .setLicenseKeyAndSyncFeatures(STATELESS_LICENSE_KEY_WITH_TEST_FEATURE_NO_EXP);
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

    @Test
    public void invalidNewStatelessLicenseKeyNotAllowed()
            throws InterruptedException, StorageQueryException, HttpResponseException, IOException,
            TenantOrAppNotFoundException {
        String[] args = {"../../"};

        {
            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

            try {
                FeatureFlag.getInstance(process.main).setLicenseKeyAndSyncFeatures(STATELESS_INVALID_LICENSE_KEY);
                fail();
            } catch (InvalidLicenseKeyException ignored) {
            }

            Assert.assertNull(
                    process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.LICENSE_KEY_CHECK_NETWORK_CALL, 1000));

            process.kill();
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }
    }

    @Test
    public void startCoreWithInvalidStatelessLicenseKey()
            throws InterruptedException, StorageQueryException, HttpResponseException, IOException,
            TenantOrAppNotFoundException {
        String[] args = {"../../"};

        {
            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

            StorageLayer.getStorage(process.main)
                    .setKeyValue(new TenantIdentifier(null, null, null), EEFeatureFlag.LICENSE_KEY_IN_DB,
                            new KeyValueInfo(STATELESS_INVALID_LICENSE_KEY));

            process.kill();
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }

        {
            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

            if (StorageLayer.getStorage(process.getProcess()).getType() == STORAGE_TYPE.SQL
                    && !Version.getVersion(process.getProcess()).getPluginName().equals("sqlite")) {
                try {
                    FeatureFlag.getInstance(process.main).getLicenseKey();
                    fail();
                } catch (NoLicenseKeyFoundException ignored) {
                }

                Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.INVALID_LICENSE_KEY));
                Assert.assertNull(
                        process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.LICENSE_KEY_CHECK_NETWORK_CALL, 1000));

                Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures().length, 0);
            }

            process.kill();
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }
    }

    @Test
    public void startCoreWithInvalidStatefulLicenseKey()
            throws InterruptedException, StorageQueryException, HttpResponseException, IOException,
            TenantOrAppNotFoundException {
        String[] args = {"../../"};

        {
            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

            StorageLayer.getStorage(process.main)
                    .setKeyValue(new TenantIdentifier(null, null, null), EEFeatureFlag.LICENSE_KEY_IN_DB,
                            new KeyValueInfo(OPAQUE_INVALID_LICENSE_KEY));

            process.kill();
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }

        {
            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

            if (StorageLayer.getStorage(process.getProcess()).getType() == STORAGE_TYPE.SQL
                    && !Version.getVersion(process.getProcess()).getPluginName().equals("sqlite")) {
                try {
                    FeatureFlag.getInstance(process.main).getLicenseKey();
                    fail();
                } catch (NoLicenseKeyFoundException ignored) {
                }

                Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.INVALID_LICENSE_KEY));
                Assert.assertNotNull(
                        process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.LICENSE_KEY_CHECK_NETWORK_CALL));

                Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures().length, 0);
            }

            process.kill();
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }
    }

    @Test
    public void startCoreWithValidStatefulKeyBut500ServerResponseHasNoSideEffect()
            throws InterruptedException, StorageQueryException, HttpResponseException, IOException,
            InvalidLicenseKeyException, TenantOrAppNotFoundException {
        String[] args = {"../../"};

        {
            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

            FeatureFlag.getInstance(process.main).setLicenseKeyAndSyncFeatures(OPAQUE_LICENSE_KEY_WITH_TEST_FEATURE);

            process.kill();
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }

        {
            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            final HttpURLConnection mockCon = mock(HttpURLConnection.class);
            InputStream inputStrm = new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8));
            when(mockCon.getInputStream()).thenReturn(inputStrm);
            when(mockCon.getErrorStream()).thenReturn(inputStrm);
            when(mockCon.getResponseCode()).thenReturn(500);
            when(mockCon.getOutputStream()).thenReturn(new OutputStream() {
                @Override
                public void write(int b) {
                    output.write(b);
                }
            });
            HttpRequestMocking.getInstance(process.getProcess()).setMockURL(EEFeatureFlag.REQUEST_ID,
                    new HttpRequestMocking.URLGetter() {

                        @Override
                        public URL getUrl(String url) throws MalformedURLException {
                            URLStreamHandler stubURLStreamHandler = new URLStreamHandler() {
                                @Override
                                protected URLConnection openConnection(URL u) {
                                    return mockCon;
                                }
                            };
                            return new URL(null, url, stubURLStreamHandler);
                        }
                    });
            process.startProcess();
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

            if (StorageLayer.getStorage(process.getProcess()).getType() == STORAGE_TYPE.SQL
                    && !Version.getVersion(process.getProcess()).getPluginName().equals("sqlite")) {
                Assert.assertNotNull(process.checkOrWaitForEvent(
                        ProcessState.PROCESS_STATE.SERVER_ERROR_DURING_LICENSE_KEY_CHECK_FAIL, 1000));

                Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures().length, 1);
            }

            process.kill();
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }
    }

    @Test
    public void startCoreWithValidStatefulKeyButNoServerResponseHasNoSideEffect()
            throws InterruptedException, StorageQueryException, HttpResponseException, IOException,
            InvalidLicenseKeyException, TenantOrAppNotFoundException {
        String[] args = {"../../"};

        {
            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

            FeatureFlag.getInstance(process.main).setLicenseKeyAndSyncFeatures(OPAQUE_LICENSE_KEY_WITH_TEST_FEATURE);

            process.kill();
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }

        {
            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
            HttpRequestMocking.getInstance(process.getProcess()).setMockURL(EEFeatureFlag.REQUEST_ID,
                    new HttpRequestMocking.URLGetter() {

                        @Override
                        public URL getUrl(String url) throws MalformedURLException {
                            // this URL does not exist, so it's as good as the server not existing when queried
                            return new URL("https://dnsflkjahsdpfiouahopjbnakfjds.supertokens.com");
                        }
                    });
            process.startProcess();
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

            if (StorageLayer.getStorage(process.getProcess()).getType() == STORAGE_TYPE.SQL
                    && !Version.getVersion(process.getProcess()).getPluginName().equals("sqlite")) {
                Assert.assertNotNull(process.checkOrWaitForEvent(
                        ProcessState.PROCESS_STATE.SERVER_ERROR_DURING_LICENSE_KEY_CHECK_FAIL, 1000));
                Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures().length, 1);
            }

            process.kill();
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }
    }

    @Test
    public void licenseCheckServerNotWorkingShouldYieldErrorInCaseOfSettingKey()
            throws InterruptedException, StorageQueryException,
            InvalidLicenseKeyException, IOException, TenantOrAppNotFoundException {
        String[] args = {"../../"};

        {
            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            final HttpURLConnection mockCon = mock(HttpURLConnection.class);
            InputStream inputStrm = new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8));
            when(mockCon.getInputStream()).thenReturn(inputStrm);
            when(mockCon.getErrorStream()).thenReturn(inputStrm);
            when(mockCon.getResponseCode()).thenReturn(500);
            when(mockCon.getOutputStream()).thenReturn(new OutputStream() {
                @Override
                public void write(int b) {
                    output.write(b);
                }
            });
            HttpRequestMocking.getInstance(process.getProcess()).setMockURL(EEFeatureFlag.REQUEST_ID,
                    new HttpRequestMocking.URLGetter() {

                        @Override
                        public URL getUrl(String url) throws MalformedURLException {
                            URLStreamHandler stubURLStreamHandler = new URLStreamHandler() {
                                @Override
                                protected URLConnection openConnection(URL u) {
                                    return mockCon;
                                }
                            };
                            return new URL(null, url, stubURLStreamHandler);
                        }
                    });
            process.startProcess();
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

            try {
                FeatureFlag.getInstance(process.main).setLicenseKeyAndSyncFeatures(OPAQUE_INVALID_LICENSE_KEY);
                fail();
            } catch (HttpResponseException ignored) {
            }

            process.kill();
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }
    }

    @Test
    public void licenseCheckServerNotReachableShouldYieldErrorInCaseOfSettingKey()
            throws InterruptedException, StorageQueryException,
            InvalidLicenseKeyException, HttpResponseException, TenantOrAppNotFoundException {
        String[] args = {"../../"};

        {
            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
            HttpRequestMocking.getInstance(process.getProcess()).setMockURL(EEFeatureFlag.REQUEST_ID,
                    new HttpRequestMocking.URLGetter() {

                        @Override
                        public URL getUrl(String url) throws MalformedURLException {
                            // this URL does not exist, so it's as good as the server not existing when queried
                            return new URL("https://dnsflkjahsdpfiouahopjbnakfjds.supertokens.com");
                        }
                    });
            process.startProcess();
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

            try {
                FeatureFlag.getInstance(process.main).setLicenseKeyAndSyncFeatures(OPAQUE_INVALID_LICENSE_KEY);
                fail();
            } catch (IOException ignored) {
            }

            process.kill();
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }
    }

    @Test
    public void checkThatRemovingOpaqueLicenseKeyHasNoNetworkCall()
            throws InterruptedException, StorageQueryException,
            InvalidLicenseKeyException, HttpResponseException, IOException, TenantOrAppNotFoundException {
        String[] args = {"../../"};

        {
            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

            if (StorageLayer.isInMemDb(process.main)) {
                // cause we keep all features enabled in memdb anyway
                return;
            }

            FeatureFlag.getInstance(process.main).setLicenseKeyAndSyncFeatures(OPAQUE_LICENSE_KEY_WITH_TEST_FEATURE);

            Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures().length, 1);
            ProcessState.getInstance(process.main).clear();

            FeatureFlag.getInstance(process.main).removeLicenseKeyAndSyncFeatures();
            Assert.assertNull(
                    process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.LICENSE_KEY_CHECK_NETWORK_CALL, 1000));
            Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures().length, 0);

            process.kill();
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }
    }

    @Test
    public void addingInvalidKeyAfterAddingCorrectKeyShouldHaveNoEffect()
            throws InterruptedException, StorageQueryException,
            InvalidLicenseKeyException, HttpResponseException, IOException, TenantOrAppNotFoundException {
        String[] args = {"../../"};

        {
            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

            if (StorageLayer.isInMemDb(process.main)) {
                // cause we keep all features enabled in memdb anyway
                return;
            }

            FeatureFlag.getInstance(process.main).setLicenseKeyAndSyncFeatures(OPAQUE_LICENSE_KEY_WITH_TEST_FEATURE);

            Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures().length, 1);
            ProcessState.getInstance(process.main).clear();

            try {
                FeatureFlag.getInstance(process.main).setLicenseKeyAndSyncFeatures(OPAQUE_INVALID_LICENSE_KEY);
                fail();
            } catch (InvalidLicenseKeyException ignored) {
            }
            Assert.assertNotNull(
                    process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.LICENSE_KEY_CHECK_NETWORK_CALL, 1000));
            Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures().length, 1);

            process.kill();
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }
    }

    @Test
    public void addingInvalidStatelessKeyAfterAddingCorrectKeyShouldHaveNoEffect()
            throws InterruptedException, StorageQueryException,
            InvalidLicenseKeyException, HttpResponseException, IOException, TenantOrAppNotFoundException {
        String[] args = {"../../"};

        {
            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

            if (StorageLayer.isInMemDb(process.main)) {
                // cause we keep all features enabled in memdb anyway
                return;
            }

            FeatureFlag.getInstance(process.main).setLicenseKeyAndSyncFeatures(OPAQUE_LICENSE_KEY_WITH_TEST_FEATURE);

            Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures().length, 1);
            ProcessState.getInstance(process.main).clear();

            try {
                FeatureFlag.getInstance(process.main).setLicenseKeyAndSyncFeatures(STATELESS_INVALID_LICENSE_KEY);
                fail();
            } catch (InvalidLicenseKeyException ignored) {
            }

            try {
                FeatureFlag.getInstance(process.main).setLicenseKeyAndSyncFeatures(STATELESS_LICENSE_KEY_EXPIRED);
                fail();
            } catch (InvalidLicenseKeyException ignored) {
            }
            Assert.assertNull(
                    process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.LICENSE_KEY_CHECK_NETWORK_CALL, 1000));
            Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures().length, 1);

            process.kill();
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }
    }

    @Test
    public void updateOpaqueToOpaqueLicenseKey()
            throws InterruptedException, StorageQueryException,
            InvalidLicenseKeyException, HttpResponseException, IOException, TenantOrAppNotFoundException {
        String[] args = {"../../"};

        {
            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

            if (StorageLayer.isInMemDb(process.main)) {
                // cause we keep all features enabled in memdb anyway
                return;
            }

            FeatureFlag.getInstance(process.main).setLicenseKeyAndSyncFeatures(OPAQUE_LICENSE_KEY_WITH_TEST_FEATURE);
            Assert.assertNotNull(
                    process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.LICENSE_KEY_CHECK_NETWORK_CALL));
            Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures().length, 1);

            ProcessState.getInstance(process.main).clear();
            FeatureFlag.getInstance(process.main).setLicenseKeyAndSyncFeatures(OPAQUE_LICENSE_KEY_WITH_EMPTY_FEATURE);
            Assert.assertNotNull(
                    process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.LICENSE_KEY_CHECK_NETWORK_CALL));
            Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures().length, 0);

            ProcessState.getInstance(process.main).clear();
            FeatureFlag.getInstance(process.main)
                    .setLicenseKeyAndSyncFeatures(OPAQUE_LICENSE_KEY_WITH_TEST_AND_RANDOM_FEATURE);
            Assert.assertNotNull(
                    process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.LICENSE_KEY_CHECK_NETWORK_CALL));
            Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures().length, 1);
            Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures()[0], EE_FEATURES.TEST);

            process.kill();
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }
    }

    @Test
    public void updateOpaqueToStatelessLicenseKey()
            throws InterruptedException, StorageQueryException,
            InvalidLicenseKeyException, HttpResponseException, IOException, TenantOrAppNotFoundException {
        String[] args = {"../../"};

        {
            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

            if (StorageLayer.isInMemDb(process.main)) {
                // cause we keep all features enabled in memdb anyway
                return;
            }

            FeatureFlag.getInstance(process.main).setLicenseKeyAndSyncFeatures(OPAQUE_LICENSE_KEY_WITH_TEST_FEATURE);
            Assert.assertNotNull(
                    process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.LICENSE_KEY_CHECK_NETWORK_CALL));
            Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures().length, 1);

            ProcessState.getInstance(process.main).clear();
            FeatureFlag.getInstance(process.main)
                    .setLicenseKeyAndSyncFeatures(STATELESS_LICENSE_KEY_EMPTY_FEATURE_WITH_EXP);
            Assert.assertNull(
                    process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.LICENSE_KEY_CHECK_NETWORK_CALL, 500));
            Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures().length, 0);

            FeatureFlag.getInstance(process.main)
                    .setLicenseKeyAndSyncFeatures(STATELESS_LICENSE_KEY_WITH_TEST_AND_RANDOM_FEATURE_WITH_EXP);
            Assert.assertNull(
                    process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.LICENSE_KEY_CHECK_NETWORK_CALL, 500));
            Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures().length, 1);
            Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures()[0], EE_FEATURES.TEST);

            process.kill();
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }
    }

    @Test
    public void updateStatelessToOpaqueLicenseKey()
            throws InterruptedException, StorageQueryException,
            InvalidLicenseKeyException, HttpResponseException, IOException, TenantOrAppNotFoundException {
        String[] args = {"../../"};

        {
            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

            if (StorageLayer.isInMemDb(process.main)) {
                // cause we keep all features enabled in memdb anyway
                return;
            }

            FeatureFlag.getInstance(process.main)
                    .setLicenseKeyAndSyncFeatures(STATELESS_LICENSE_KEY_WITH_TEST_FEATURE_NO_EXP);
            Assert.assertNull(
                    process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.LICENSE_KEY_CHECK_NETWORK_CALL, 500));
            Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures().length, 1);

            ProcessState.getInstance(process.main).clear();
            FeatureFlag.getInstance(process.main).setLicenseKeyAndSyncFeatures(OPAQUE_LICENSE_KEY_WITH_EMPTY_FEATURE);
            Assert.assertNotNull(
                    process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.LICENSE_KEY_CHECK_NETWORK_CALL, 500));
            Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures().length, 0);

            ProcessState.getInstance(process.main).clear();
            FeatureFlag.getInstance(process.main)
                    .setLicenseKeyAndSyncFeatures(OPAQUE_LICENSE_KEY_WITH_TEST_AND_RANDOM_FEATURE);
            Assert.assertNotNull(
                    process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.LICENSE_KEY_CHECK_NETWORK_CALL, 500));
            Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures().length, 1);
            Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures()[0], EE_FEATURES.TEST);

            process.kill();
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }
    }

    @Test
    public void updateStatelessToStatelessLicenseKey()
            throws InterruptedException, StorageQueryException,
            InvalidLicenseKeyException, HttpResponseException, IOException, TenantOrAppNotFoundException {
        String[] args = {"../../"};

        {
            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

            if (StorageLayer.isInMemDb(process.main)) {
                // cause we keep all features enabled in memdb anyway
                return;
            }


            FeatureFlag.getInstance(process.main)
                    .setLicenseKeyAndSyncFeatures(STATELESS_LICENSE_KEY_WITH_TEST_FEATURE_NO_EXP);
            Assert.assertNull(
                    process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.LICENSE_KEY_CHECK_NETWORK_CALL, 500));
            Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures().length, 1);

            FeatureFlag.getInstance(process.main)
                    .setLicenseKeyAndSyncFeatures(STATELESS_LICENSE_KEY_EMPTY_FEATURE_WITH_EXP);
            Assert.assertNull(
                    process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.LICENSE_KEY_CHECK_NETWORK_CALL, 500));
            Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures().length, 0);

            FeatureFlag.getInstance(process.main)
                    .setLicenseKeyAndSyncFeatures(STATELESS_LICENSE_KEY_WITH_TEST_AND_RANDOM_FEATURE_WITH_EXP);
            Assert.assertNull(
                    process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.LICENSE_KEY_CHECK_NETWORK_CALL, 500));
            Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures().length, 1);
            Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures()[0], EE_FEATURES.TEST);

            process.kill();
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }
    }

    @Test
    public void testRemovingOpaqueLicenseKey()
            throws InterruptedException, StorageQueryException,
            InvalidLicenseKeyException, HttpResponseException, IOException, TenantOrAppNotFoundException {
        String[] args = {"../../"};

        {
            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

            if (StorageLayer.isInMemDb(process.main)) {
                // cause we keep all features enabled in memdb anyway
                return;
            }

            FeatureFlag.getInstance(process.main).setLicenseKeyAndSyncFeatures(OPAQUE_LICENSE_KEY_WITH_TEST_FEATURE);
            Assert.assertNotNull(
                    process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.LICENSE_KEY_CHECK_NETWORK_CALL));
            Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures().length, 1);

            ProcessState.getInstance(process.main).clear();
            FeatureFlag.getInstance(process.main).removeLicenseKeyAndSyncFeatures();
            Assert.assertNull(
                    process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.LICENSE_KEY_CHECK_NETWORK_CALL, 500));
            Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures().length, 0);

            process.kill();
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }
    }

    @Test
    public void testRemovingStatelessLicenseKey()
            throws InterruptedException, StorageQueryException,
            InvalidLicenseKeyException, HttpResponseException, IOException, TenantOrAppNotFoundException {
        String[] args = {"../../"};

        {
            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

            if (StorageLayer.isInMemDb(process.main)) {
                // cause we keep all features enabled in memdb anyway
                return;
            }

            FeatureFlag.getInstance(process.main)
                    .setLicenseKeyAndSyncFeatures(STATELESS_LICENSE_KEY_WITH_TEST_FEATURE_NO_EXP);
            Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures().length, 1);

            ProcessState.getInstance(process.main).clear();
            FeatureFlag.getInstance(process.main).removeLicenseKeyAndSyncFeatures();
            Assert.assertNull(
                    process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.LICENSE_KEY_CHECK_NETWORK_CALL, 500));
            Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures().length, 0);

            process.kill();
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }
    }

    @Test
    public void testVariousStatelessKeys()
            throws InterruptedException, StorageQueryException,
            InvalidLicenseKeyException, HttpResponseException, IOException, TenantOrAppNotFoundException {
        String[] args = {"../../"};

        {
            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
            if (StorageLayer.isInMemDb(process.main)) {
                // cause we keep all features enabled in memdb anyway
                return;
            }


            FeatureFlag.getInstance(process.main)
                    .setLicenseKeyAndSyncFeatures(STATELESS_LICENSE_KEY_WITH_EMPTY_FEATURE_NO_EXP);
            Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures().length, 0);


            FeatureFlag.getInstance(process.main)
                    .setLicenseKeyAndSyncFeatures(STATELESS_LICENSE_KEY_EMPTY_FEATURE_WITH_EXP);
            Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures().length, 0);

            FeatureFlag.getInstance(process.main)
                    .setLicenseKeyAndSyncFeatures(STATELESS_LICENSE_KEY_WITH_TEST_FEATURE_WITH_EXP);
            Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures().length, 1);
            Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures()[0], EE_FEATURES.TEST);

            try {
                FeatureFlag.getInstance(process.main)
                        .setLicenseKeyAndSyncFeatures(STATELESS_LICENSE_KEY_EXPIRED);
                fail();
            } catch (InvalidLicenseKeyException ignored) {
            }
            // same state as previous key
            Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures().length, 1);
            Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures()[0], EE_FEATURES.TEST);

            try {
                FeatureFlag.getInstance(process.main)
                        .setLicenseKeyAndSyncFeatures(STATELESS_INVALID_LICENSE_KEY);
                fail();
            } catch (InvalidLicenseKeyException ignored) {
            }
            // same state as previous key
            Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures().length, 1);
            Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures()[0], EE_FEATURES.TEST);

            FeatureFlag.getInstance(process.main)
                    .setLicenseKeyAndSyncFeatures(STATELESS_LICENSE_KEY_WITH_RANDOM_FEATURE_WITH_EXP);
            Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures().length, 0);

            process.kill();
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }
    }

    @Test
    public void testVariousOpaqueKeys()
            throws InterruptedException, StorageQueryException,
            InvalidLicenseKeyException, HttpResponseException, IOException, TenantOrAppNotFoundException {
        String[] args = {"../../"};

        {
            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

            if (StorageLayer.isInMemDb(process.main)) {
                // cause we keep all features enabled in memdb anyway
                return;
            }

            FeatureFlag.getInstance(process.main)
                    .setLicenseKeyAndSyncFeatures(OPAQUE_LICENSE_KEY_WITH_EMPTY_FEATURE);
            Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures().length, 0);


            FeatureFlag.getInstance(process.main)
                    .setLicenseKeyAndSyncFeatures(OPAQUE_LICENSE_KEY_WITH_TEST_AND_RANDOM_FEATURE);
            Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures().length, 1);
            Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures()[0], EE_FEATURES.TEST);

            FeatureFlag.getInstance(process.main)
                    .setLicenseKeyAndSyncFeatures(OPAQUE_LICENSE_KEY_WITH_TEST_FEATURE);
            Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures().length, 1);
            Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures()[0], EE_FEATURES.TEST);

            try {
                FeatureFlag.getInstance(process.main)
                        .setLicenseKeyAndSyncFeatures(OPAQUE_LICENSE_KEY_THAT_IS_REVOKED);
                fail();
            } catch (InvalidLicenseKeyException ignored) {
            }
            // same state as previous key
            Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures().length, 1);
            Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures()[0], EE_FEATURES.TEST);

            try {
                FeatureFlag.getInstance(process.main)
                        .setLicenseKeyAndSyncFeatures(OPAQUE_INVALID_LICENSE_KEY);
                fail();
            } catch (InvalidLicenseKeyException ignored) {
            }
            // same state as previous key
            Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures().length, 1);
            Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures()[0], EE_FEATURES.TEST);

            FeatureFlag.getInstance(process.main)
                    .setLicenseKeyAndSyncFeatures(OPAQUE_LICENSE_KEY_WITH_RANDOM_FEATURE);
            Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures().length, 0);

            process.kill();
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }
    }

    @Test
    public void testThatInvalidOpaqueKeyOnCoreStartCausesNoFeaturesToBeLoaded() throws Exception {
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

                StorageLayer.getStorage(process.main)
                        .setKeyValue(new TenantIdentifier(null, null, null), EEFeatureFlag.LICENSE_KEY_IN_DB,
                                new KeyValueInfo(OPAQUE_INVALID_LICENSE_KEY));
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

                Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures().length, 0);
            }

            process.kill();
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }
    }

    @Test
    public void testThatInvalidstatelessKeyOnCoreStartCausesNoFeaturesToBeLoaded() throws Exception {
        String[] args = {"../../"};

        // we do this test only for non in mem db cause it requires saving the license key across
        // core restarts..

        {
            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

            if (StorageLayer.getStorage(process.getProcess()).getType() == STORAGE_TYPE.SQL
                    && !Version.getVersion(process.getProcess()).getPluginName().equals("sqlite")) {
                FeatureFlag.getInstance(process.main)
                        .setLicenseKeyAndSyncFeatures(STATELESS_LICENSE_KEY_WITH_TEST_FEATURE_WITH_EXP);

                Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures().length, 1);
                Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures()[0], EE_FEATURES.TEST);

                StorageLayer.getStorage(process.main)
                        .setKeyValue(new TenantIdentifier(null, null, null), EEFeatureFlag.LICENSE_KEY_IN_DB,
                                new KeyValueInfo(STATELESS_INVALID_LICENSE_KEY));
            }

            process.kill();
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }

        {
            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
            process.startProcess();
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

            if (StorageLayer.getStorage(process.getProcess()).getType() == STORAGE_TYPE.SQL
                    && !Version.getVersion(process.getProcess()).getPluginName().equals("sqlite")) {

                Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures().length, 0);
            }

            process.kill();
            Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }
    }

    @Test
    public void gettingFeatureFlagInAPIDoesntAlwaysQueryDb() throws Exception {
        String[] args = {"../../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.isInMemDb(process.main)) {
            // cause we keep all features enabled in memdb anyway
            return;
        }

        FeatureFlag.getInstance(process.main)
                .setLicenseKeyAndSyncFeatures(STATELESS_LICENSE_KEY_WITH_TEST_FEATURE_WITH_EXP);

        Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures().length, 1);
        Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures()[0], EE_FEATURES.TEST);

        StorageLayer.getStorage(process.main)
                .setKeyValue(new TenantIdentifier(null, null, null), EEFeatureFlag.FEATURE_FLAG_KEY_IN_DB,
                        new KeyValueInfo(""));

        Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures().length, 1);
        Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures()[0], EE_FEATURES.TEST);

        process.kill();
        Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void gettingFeatureFlagInAPIQueriesDbAfterCertainAmountOfTime() throws Exception {
        String[] args = {"../../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.isInMemDb(process.main)) {
            // cause we keep all features enabled in memdb anyway
            return;
        }

        FeatureFlag.getInstance(process.main)
                .setLicenseKeyAndSyncFeatures(STATELESS_LICENSE_KEY_WITH_TEST_FEATURE_WITH_EXP);

        Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures().length, 1);
        Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures()[0], EE_FEATURES.TEST);

        EE_FEATURES[] features = new EE_FEATURES[]{EE_FEATURES.ACCOUNT_LINKING};
        JsonArray json = new JsonArray();
        Arrays.stream(features).forEach(ee_features -> json.add(new JsonPrimitive(ee_features.toString())));
        StorageLayer.getStorage(process.main)
                .setKeyValue(new TenantIdentifier(null, null, null), EEFeatureFlag.FEATURE_FLAG_KEY_IN_DB,
                        new KeyValueInfo(json.toString()));

        FeatureFlag.getInstance(process.main).getEeFeatureFlagInstance()
                .updateEnabledFeaturesValueReadFromDbTime(System.currentTimeMillis() - (1000 * 3600 * 5));

        Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures().length, 1);
        Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures()[0], EE_FEATURES.ACCOUNT_LINKING);

        process.kill();
        Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void gettingEmptyFeatureFlagFromDb() throws Exception {
        String[] args = {"../../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.isInMemDb(process.main)) {
            // cause we keep all features enabled in memdb anyway
            return;
        }

        EE_FEATURES[] features = new EE_FEATURES[]{};
        JsonArray json = new JsonArray();
        Arrays.stream(features).forEach(ee_features -> json.add(new JsonPrimitive(ee_features.toString())));
        StorageLayer.getStorage(process.main)
                .setKeyValue(new TenantIdentifier(null, null, null), EEFeatureFlag.FEATURE_FLAG_KEY_IN_DB,
                        new KeyValueInfo(json.toString()));

        FeatureFlag.getInstance(process.main).getEeFeatureFlagInstance()
                .updateEnabledFeaturesValueReadFromDbTime(System.currentTimeMillis() - (1000 * 3600 * 5));

        EE_FEATURES[] featuresFromDb = FeatureFlag.getInstance(process.main).getEnabledFeatures();
        Assert.assertEquals(featuresFromDb.length, 0);

        process.kill();
        Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void gettingMissingFeatureFlagFromDb() throws Exception {
        String[] args = {"../../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.isInMemDb(process.main)) {
            // cause we keep all features enabled in memdb anyway
            return;
        }

        FeatureFlag.getInstance(process.main).getEeFeatureFlagInstance()
                .updateEnabledFeaturesValueReadFromDbTime(System.currentTimeMillis() - (1000 * 3600 * 5));

        EE_FEATURES[] featuresFromDb = FeatureFlag.getInstance(process.main).getEnabledFeatures();
        Assert.assertEquals(featuresFromDb.length, 0);

        process.kill();
        Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void gettingInvalidFeatureFlagShouldIgnoreItFromDb() throws Exception {
        String[] args = {"../../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.isInMemDb(process.main)) {
            // cause we keep all features enabled in memdb anyway
            return;
        }

        JsonArray json = new JsonArray();
        json.add(new JsonPrimitive("random"));
        json.add(new JsonPrimitive("test"));
        StorageLayer.getStorage(process.main)
                .setKeyValue(new TenantIdentifier(null, null, null), EEFeatureFlag.FEATURE_FLAG_KEY_IN_DB,
                        new KeyValueInfo(json.toString()));

        FeatureFlag.getInstance(process.main).getEeFeatureFlagInstance()
                .updateEnabledFeaturesValueReadFromDbTime(System.currentTimeMillis() - (1000 * 3600 * 5));

        EE_FEATURES[] featuresFromDb = FeatureFlag.getInstance(process.main).getEnabledFeatures();
        Assert.assertEquals(featuresFromDb.length, 1);
        Assert.assertEquals(FeatureFlag.getInstance(process.main).getEnabledFeatures()[0], EE_FEATURES.TEST);

        process.kill();
        Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testLicenseKeyCheckAPIInput()
            throws InterruptedException, IOException {
        String[] args = {"../../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        final HttpURLConnection mockCon = mock(HttpURLConnection.class);
        InputStream inputStrm = new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8));
        when(mockCon.getInputStream()).thenReturn(inputStrm);
        when(mockCon.getErrorStream()).thenReturn(inputStrm);
        when(mockCon.getResponseCode()).thenReturn(200);
        when(mockCon.getOutputStream()).thenReturn(new OutputStream() {
            @Override
            public void write(int b) {
                output.write(b);
            }
        });
        HttpRequestMocking.getInstance(process.getProcess()).setMockURL(EEFeatureFlag.REQUEST_ID,
                new HttpRequestMocking.URLGetter() {

                    @Override
                    public URL getUrl(String url) throws MalformedURLException {
                        URLStreamHandler stubURLStreamHandler = new URLStreamHandler() {
                            @Override
                            protected URLConnection openConnection(URL u) {
                                return mockCon;
                            }
                        };
                        return new URL(null, url, stubURLStreamHandler);
                    }
                });
        process.startProcess();
        Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        try {
            FeatureFlag.getInstance(process.main).setLicenseKeyAndSyncFeatures(OPAQUE_LICENSE_KEY_WITH_TEST_FEATURE);
        } catch (Exception ignored) {
            // we have this cause the mocked API response is not as per spec.
        }
        String requestBodyStr = output.toString();
        JsonObject j = new JsonParser().parse(requestBodyStr).getAsJsonObject();

        if (StorageLayer.getStorage(process.getProcess()).getType() == STORAGE_TYPE.SQL) {
            if (Version.getVersion(process.getProcess()).getPluginName().equals("sqlite")) {
                assertEquals(j.entrySet().size(), 3);
            } else {
                assertEquals(j.entrySet().size(), 4);
                assertNotNull(j.get("telemetryId"));
            }

            assertNotNull(j.get("licenseKey"));
            assertNotNull(j.get("superTokensVersion"));
            JsonObject paidFeatureUsageStats = j.getAsJsonObject("paidFeatureUsageStats");
            JsonArray mauArr = paidFeatureUsageStats.get("maus").getAsJsonArray();
            assertEquals(paidFeatureUsageStats.entrySet().size(), 1);
            assertEquals(mauArr.size(), 30);
            assertEquals(mauArr.get(0).getAsInt(), 0);
            assertEquals(mauArr.get(29).getAsInt(), 0);
        }

        process.kill();
        Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
 