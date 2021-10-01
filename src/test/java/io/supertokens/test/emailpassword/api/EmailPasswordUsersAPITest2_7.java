/*
 *    Copyright (c) 2021, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.test.emailpassword.api;

import io.supertokens.ProcessState;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.HashMap;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class EmailPasswordUsersAPITest2_7 {

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
    public void testBadInput() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }
        {
            HashMap<String, String> QueryParams = new HashMap<String, String>();
            QueryParams.put("limit", "1001");
            try {
                HttpRequestForTesting.sendGETRequest(process.getProcess(), "", "http://localhost:3567/recipe/users",
                        QueryParams, 1000, 1000, null, Utils.getCdiVersion2_7ForTests(), "emailpassword");
                throw new Exception("Should not come here");
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                assertTrue(e.statusCode == 400
                        && e.getMessage().equals("Http error. Status Code: 400. Message: max limit allowed is 1000"));
            }
        }

        {
            HashMap<String, String> QueryParams = new HashMap<String, String>();
            QueryParams.put("limit", "-1");
            try {
                HttpRequestForTesting.sendGETRequest(process.getProcess(), "", "http://localhost:3567/recipe/users",
                        QueryParams, 1000, 1000, null, Utils.getCdiVersion2_7ForTests(), "emailpassword");
                throw new Exception("Should not come here");
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage()
                        .equals("Http error. Status Code: 400. Message: limit must a positive integer with "
                                + "max value 1000"));
            }
        }

        {
            HashMap<String, String> QueryParams = new HashMap<String, String>();
            QueryParams.put("timeJoinedOrder", "AESC");
            try {
                HttpRequestForTesting.sendGETRequest(process.getProcess(), "", "http://localhost:3567/recipe/users",
                        QueryParams, 1000, 1000, null, Utils.getCdiVersion2_7ForTests(), "emailpassword");
                throw new Exception("Should not come here");
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals(
                        "Http error. Status Code: 400. Message: timeJoinedOrder can be either ASC OR " + "DESC"));
            }
        }

        {
            HashMap<String, String> QueryParams = new HashMap<String, String>();
            QueryParams.put("paginationToken", "randomString");
            try {
                HttpRequestForTesting.sendGETRequest(process.getProcess(), "", "http://localhost:3567/recipe/users",
                        QueryParams, 1000, 1000, null, Utils.getCdiVersion2_7ForTests(), "emailpassword");
                throw new Exception("Should not come here");
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                assertTrue(e.statusCode == 400
                        && e.getMessage().equals("Http error. Status Code: 400. Message: invalid pagination token"));
            }
        }

        {
            HashMap<String, String> QueryParams = new HashMap<String, String>();
            QueryParams.put("paginationToken", "cmFuZG9tU3RyaW5n");
            try {
                HttpRequestForTesting.sendGETRequest(process.getProcess(), "", "http://localhost:3567/recipe/users",
                        QueryParams, 1000, 1000, null, Utils.getCdiVersion2_7ForTests(), "emailpassword");
                throw new Exception("Should not come here");
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                assertTrue(e.statusCode == 400
                        && e.getMessage().equals("Http error. Status Code: 400. Message: invalid pagination token"));
            }
        }

        {
            HashMap<String, String> QueryParams = new HashMap<String, String>();
            QueryParams.put("paginationToken", "OWIxZGViNGQtM2I3ZC00YmFkLTliZGQtMmIwZDdiM2RjYjZkOzNzZHNkczQyMzQyMzQ=");
            try {
                HttpRequestForTesting.sendGETRequest(process.getProcess(), "", "http://localhost:3567/recipe/users",
                        QueryParams, 1000, 1000, null, Utils.getCdiVersion2_7ForTests(), "emailpassword");
                throw new Exception("Should not come here");
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                assertTrue(e.statusCode == 400
                        && e.getMessage().equals("Http error. Status Code: 400. Message: invalid pagination token"));
            }
        }
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
