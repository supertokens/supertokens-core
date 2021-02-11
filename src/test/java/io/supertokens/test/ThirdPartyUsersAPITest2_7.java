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

package io.supertokens.test;

import io.supertokens.ProcessState;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.thirdparty.ThirdParty;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.HashMap;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/*
 * TODO:
 *  - Check for bad input (missing fields)
 *  - Check good input works (add 5 users)
 *    - no params passed should return 5 users
 *    - only limit passed (limit: 2. users are returned in ASC order based on timeJoined)
 *    - limit and timeJoinedOrder passed (limit: 2, timeJoinedOrder: DESC. users are returned in DESC order based on
 * timeJoined)
 * */

public class ThirdPartyUsersAPITest2_7 {

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

    // Check for bad input (missing fields)
    @Test
    public void testBadInput() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }
        {
            HashMap<String, String> QueryParams = new HashMap<>();
            QueryParams.put("paginationToken", "randomValue");
            try {
                io.supertokens.test.httpRequest.HttpRequest
                        .sendGETRequest(process.getProcess(), "",
                                "http://localhost:3567/recipe/users", QueryParams, 1000,
                                1000,
                                null, Utils.getCdiVersion2_7ForTests(), ThirdParty.RECIPE_ID);
                throw new Exception("Should not come here");
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                assertTrue(e.statusCode == 400 &&
                        e.getMessage().equals("Http error. Status Code: 400. Message: invalid pagination token"));
            }
        }
        {
            HashMap<String, String> QueryParams = new HashMap<>();
            QueryParams.put("limit", "randomValue");
            try {
                io.supertokens.test.httpRequest.HttpRequest
                        .sendGETRequest(process.getProcess(), "",
                                "http://localhost:3567/recipe/users", QueryParams, 1000,
                                1000,
                                null, Utils.getCdiVersion2_7ForTests(), ThirdParty.RECIPE_ID);
                throw new Exception("Should not come here");
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                assertTrue(e.statusCode == 400 &&
                        e.getMessage()
                                .equals("Http error. Status Code: 400. Message: Field name 'limit' must be an int in " +
                                        "the GET request"));
            }
        }
        {
            HashMap<String, String> QueryParams = new HashMap<>();
            QueryParams.put("timeJoinedOrder", "randomValue");
            try {
                io.supertokens.test.httpRequest.HttpRequest
                        .sendGETRequest(process.getProcess(), "",
                                "http://localhost:3567/recipe/users", QueryParams, 1000,
                                1000,
                                null, Utils.getCdiVersion2_7ForTests(), ThirdParty.RECIPE_ID);
                throw new Exception("Should not come here");
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                assertTrue(e.statusCode == 400 &&
                        e.getMessage()
                                .equals("Http error. Status Code: 400. Message: timeJoinedOrder can be either ASC OR " +
                                        "DESC"));
            }
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
