/*
 *    Copyright (c) 2020, VRAI Labs and/or its affiliates. All rights reserved.
 *
 *    This software is licensed under the Apache License, Version 2.0 (the
 *    "License") as published by the Apache Software Foundation.
 *
 *    You may not use this file except in compliance with the License. You may
 *    obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *    License for the specific language governing permissions and limitations
 *    under the License.
 */

package io.supertokens.ee.test;

import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.ee.test.httpRequest.HttpRequestForTesting;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlag;
import io.supertokens.webserver.WebserverAPI;
import org.junit.*;
import org.junit.rules.TestRule;

public class EETest {

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
    public void testRetrievingEEFeaturesWhenNotSetInDb() throws Exception {
        String[] args = {"../../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // check that there are no features enabled
        EE_FEATURES[] eeArray = FeatureFlag.getInstance(process.getProcess()).getEnabledFeatures();
        Assert.assertEquals(0, eeArray.length);

        // check that isLicenseKeyPresent is false
        Assert.assertFalse(
                FeatureFlag.getInstance(process.getProcess()).getEeFeatureFlagInstance().getIsLicenseKeyPresent());
        process.kill();
        Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testThatCallingGetFeatureFlagAPIReturnsEmptyArray() throws Exception {
        String[] args = {"../../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/ee/featureflag",
                null, 1000, 1000, null, WebserverAPI.getLatestCDIVersion(), "");
        Assert.assertEquals("OK", response.get("status").getAsString());
        Assert.assertNotNull(response.get("features"));
        Assert.assertEquals(0, response.get("features").getAsJsonArray().size());

        process.kill();
        Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testRemovingLicenseKey() throws Exception {
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
    public void testDeletingLicenseKeyWhenItDoesNotExist() throws Exception {
        String[] args = {"../../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        JsonObject response = HttpRequestForTesting.sendJsonDELETERequest(process.getProcess(), "",
                "http://localhost:3567/ee/license",
                null, 1000, 1000, null, WebserverAPI.getLatestCDIVersion(), "");
        Assert.assertEquals("OK", response.get("status").getAsString());

        process.kill();
        Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testRetrievingLicenseKeyWhenItIsNotSet() throws Exception {
        String[] args = {"../../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/ee/license",
                null, 1000, 1000, null, WebserverAPI.getLatestCDIVersion(), "");
        Assert.assertEquals("NO_LICENSE_KEY_FOUND_ERROR", response.get("status").getAsString());

        process.kill();
        Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
 