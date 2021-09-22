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

package io.supertokens.test.thirdparty.api;

import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.thirdparty.ThirdParty;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertNotNull;

/*
 * TODO:
 *  - from EmailPasswordUsersCountAPITest2_7
 * */

public class ThirdPartyUsersCountAPITest2_7 {

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

    // - from EmailPasswordUsersCountAPITest2_7
    // failure condition: number of signed up users not matching the count
    @Test
    public void testAPI() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        {
            JsonObject response = HttpRequestForTesting
                    .sendGETRequest(process.getProcess(), "",
                            "http://localhost:3567/recipe/users/count", null, 1000,
                            1000,
                            null, Utils.getCdiVersion2_7ForTests(), "thirdparty");
            assertEquals(response.get("status").getAsString(), "OK");
            assertEquals(response.get("count").getAsLong(), 0);
        }

        ThirdParty.signInUp(process.getProcess(), "thirdPartyId",
                "thirdPartyUserId", "test@example.com");
        ThirdParty.signInUp(process.getProcess(), "thirdPartyId",
                "thirdPartyUserId1", "test1@example.com");
        ThirdParty.signInUp(process.getProcess(), "thirdPartyId",
                "thirdPartyUserId2", "test2@example.com");
        ThirdParty.signInUp(process.getProcess(), "thirdPartyId",
                "thirdPartyUserId3", "test3@example.com");
        ThirdParty.signInUp(process.getProcess(), "thirdPartyId",
                "thirdPartyUserId4", "test4@example.com");

        {
            JsonObject response = HttpRequestForTesting
                    .sendGETRequest(process.getProcess(), "",
                            "http://localhost:3567/recipe/users/count", null, 1000,
                            1000,
                            null, Utils.getCdiVersion2_7ForTests(), "thirdparty");
            assertEquals(response.get("status").getAsString(), "OK");
            assertEquals(response.get("count").getAsLong(), 5);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

}
