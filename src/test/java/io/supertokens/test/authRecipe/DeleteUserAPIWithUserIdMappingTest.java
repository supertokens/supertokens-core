/*
 *    Copyright (c) 2022, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.test.authRecipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.emailpassword.UserInfo;
import io.supertokens.pluginInterface.useridmapping.UserIdMappingStorage;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.useridmapping.UserIdMapping;
import io.supertokens.useridmapping.UserIdType;
import io.supertokens.usermetadata.UserMetadata;
import junit.framework.TestCase;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.ArrayList;

import static org.junit.Assert.*;

public class DeleteUserAPIWithUserIdMappingTest {
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
    public void createAUserMapTheirIdCreateMetadataWithExternalIdAndDelete() throws Exception {
        String[] args = { "../" };
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // deleting with superTokensUserId
        {
            // create User
            UserInfo userInfo = EmailPassword.signUp(process.main, "test@example.com", "testPass123");
            String superTokensUserId = userInfo.id;
            String externalId = "externalId";

            // map their id
            UserIdMapping.createUserIdMapping(process.main, superTokensUserId, externalId, null, false);

            // create UserMetadata with the externalId
            JsonObject testData = new JsonObject();
            testData.addProperty("testKey", "testValue");
            UserMetadata.updateUserMetadata(process.main, externalId, testData);

            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("userId", superTokensUserId);

            JsonObject deleteResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/user/remove", requestBody, 1000, 1000, null,
                    Utils.getCdiVersion2_15ForTests(), "");
            assertEquals("OK", deleteResponse.get("status").getAsString());

            // check that user doesnt exist
            {
                UserInfo response = EmailPassword.getUserUsingId(process.main, superTokensUserId);
                assertNull(response);
            }

            // check that userMetadata does not exist
            {
                JsonObject response = UserMetadata.getUserMetadata(process.main, externalId);
                assertEquals(0, response.entrySet().size());
            }

            // check that mapping does not exist
            {
                io.supertokens.pluginInterface.useridmapping.UserIdMapping response = UserIdMapping
                        .getUserIdMapping(process.main, superTokensUserId, UserIdType.SUPERTOKENS);
                assertNull(response);
            }
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
