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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import com.google.gson.JsonObject;

import io.supertokens.ProcessState;


import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.httpRequest.HttpResponseException;

public class LicenseKeyAPITest {
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
     public void testBadInputForLicenseKeyAPI() throws Exception {
         String[] args = {"../"};
 
         TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
         assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        

        // test passing licenseKey as a invalid type
        JsonObject resquestBody = new JsonObject();
        resquestBody.addProperty("test", 10);

        Exception error = null;
        try {
            HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "", "http://localhost:3567/ee/license",
                        null, 1000, 1000, null, Utils.getCdiVersion2_16ForTests(), "");
        } catch (HttpResponseException e) {
            error = e;
        }

        assertNotNull(error);
        assertEquals("Http error. Status Code: 400. Message: Invalid Json Input", error.getMessage());
                 
         process.kill();
         assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
     }
}
