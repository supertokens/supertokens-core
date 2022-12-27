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

package io.supertokens.test.eeTest;

import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.ee.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlag;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.*;

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

    String testLicenseKey = "test";

    /*
     * Different scenarios:
     *
     * 1) No license key added to db
     *  - good case:
     *      - on init -> sets empty array as features in db, isLicenseKeyPresent = false
     *      - in API -> returns empty array
     *      - in cronjob -> same as on init
     *      - remove license key called -> same as on init
     *      - on setting license key -> set license key in db, query API and set features in db -> go to (2) License key
     * in db
     *  - database not working case:
     *      - on init -> core stops.
     *      - in API -> return empty array
     *      - in cronjob -> error will be thrown in cronjob -> cronjob will ignore it
     *      - remove license key called -> error thrown in API
     *      - on setting license key -> error thrown in API
     *  - API not working case
     *      - on init -> Not applicable since no license key present
     *      - in API -> Not applicable since no license key present
     *      - in cronjob -> Not applicable since no license key present
     *      - remove license key called -> Not applicable since no license key present
     *      - on setting license key -> license key will be set in db, but enabled features will not be -> results in
     *  API
     *  error. -> calling getEnabledFeatures will lead to returning the older enabled features from the db, or if
     * nothing
     *  existed, then no features.
     *
     * 2) License key in db
     *  - good case:
     *      - on init -> query API and set features in db
     *      - in API ->
     *          - if last synced attempt more than 24 hours ago
     *              - query API and set feature in db
     *              - return those features
     *          - if last sync within 24 hours
     *              - if features in db last read before 4 hours
     *                  - query db and return those features
     *              - else return feature list from memory
     *      - in cronjob -> same as init
     *      - remove license key called -> same as init
     *      - on setting license key -> same as init
     *  - database not working case
     *      - on init -> core not started
     *      - in API ->
     *          - no API call is made since license key fetching from db would fail
     *          - if last read time of features from db is more than 4 hours ago -> throw an error
     *          - else return enabled features from cache (memory)
     *      - in cronjob -> error will be thrown in cronjob -> cronjob will ignore it
     *      - remove license key called -> error thrown in API
     *      - on setting license key -> error thrown in API
     *  - API not working case
     *      - on init -> error ignored.
     *      - in API ->
     *          - if last synced attempt more than 24 hours ago
     *              - API call made -> fails -> read features enabled from db or cache
     *          - else read features enabled from db or cache
     *      - in cronjob -> call API -> fail -> cronjob ignores errors
     *      - remove license key called -> cleared from db, no API called, enabled features set to empty in db and in
     *  memory
     *      - on setting license key -> key set in db, API call failed resulting in API error -> calling
     * getEnabledFeatures will return the older enabled features from the db, or if nothing existed, then no features.
     * */

    @Test
    public void testRetrievingEEFeaturesWhenNotSetInDb() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // check that there are no features enabled
        EE_FEATURES[] eeArray = FeatureFlag.getInstance(process.getProcess()).getEnabledFeatures();
        assertEquals(0, eeArray.length);

        // check that isLicenseKeyPresent is false
        assertFalse(FeatureFlag.getInstance(process.getProcess()).getEeFeatureFlagInstance().getIsLicenseKeyPresent());
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // Check that API returns an empty array
    @Test
    public void testThatCallingGetFeatureFlagAPIReturnsEmptyArray() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/ee/featureflag",
                null, 1000, 1000, null, Utils.getCdiVersion2_16ForTests(), "");
        assertEquals("OK", response.get("status").getAsString());
        assertNotNull(response.get("features"));
        assertEquals(0, response.get("features").getAsJsonArray().size());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    // remove license key called -> same as on init
    @Test
    public void testRemovingLicenseKey() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        FeatureFlag featureFlag = FeatureFlag.getInstance(process.getProcess());

        // call removeLicenseKeyAndSyncFeatures
        featureFlag.removeLicenseKeyAndSyncFeatures();

        // check that there are no features enabled
        EE_FEATURES[] eeArray = featureFlag.getEnabledFeatures();
        assertEquals(0, eeArray.length);

        // check that isLicenseKeyPresent is false
        assertFalse(featureFlag.getEeFeatureFlagInstance().getIsLicenseKeyPresent());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testDeletingLicenseKeyWhenItDoesNotExist() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        JsonObject response = HttpRequestForTesting.sendJsonDELETERequest(process.getProcess(), "",
                "http://localhost:3567/ee/license",
                null, 1000, 1000, null, Utils.getCdiVersion2_16ForTests(), "");
        assertEquals("OK", response.get("status").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testRetrievingLicenseKeyWhenItIsNotSet() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/ee/license",
                null, 1000, 1000, null, Utils.getCdiVersion2_16ForTests(), "");
        assertEquals("NO_LICENSE_KEY_FOUND_ERROR", response.get("status").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
 