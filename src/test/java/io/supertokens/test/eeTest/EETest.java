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
import com.google.gson.JsonObject;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
 
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
 
    //      1) No license key added to db
    //  *  - good case:
    //  *      - on init -> sets empty array as features in db, isLicenseKeyPresent = false
    //  *      - in API -> returns empty array
    //  *      - in cronjob -> same as on init
    //  *      - remove license key called -> same as on init
    //  *      - on setting license key -> set license key in db, query API and set features in db -> go to (2) License key
    //  * in db
     @Test
     public void testRetrievingEEFeaturesWhenNotSetInDb() throws Exception {
         String[] args = {"../"};
 
         TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
         assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        
         // check that there are no features enabled
         EE_FEATURES[] eeArray = FeatureFlag.getInstance(process.getProcess()).getEnabledFeatures();
         assertEquals(0, eeArray.length);

         // check that isLicenseKeyPresent is false
         assertFalse(FeatureFlag.getInstance(process.getProcess()).isLicenseKeyPresent());
         process.kill();
         assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
     }

     // Check that API returns an empty array
     @Test
     public void testThatCallingGetFeatureFlagAPIReturnsEmptyArray() throws Exception{
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "", "http://localhost:3567/ee/featureflag",
                        null, 1000, 1000, null, Utils.getCdiVersion2_16ForTests(), "");
        assertEquals("OK", response.get("status").getAsString());
        assertNotNull(response.get("features"));
        assertEquals(0, response.get("features").getAsJsonArray().size());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));        

     }
 }
 