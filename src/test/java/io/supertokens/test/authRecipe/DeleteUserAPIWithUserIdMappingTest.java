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

import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.thirdparty.ThirdParty;
import io.supertokens.useridmapping.UserIdMapping;
import io.supertokens.useridmapping.UserIdType;
import io.supertokens.usermetadata.UserMetadata;
import io.supertokens.utils.SemVer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

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
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // deleting with superTokensUserId
        {
            // create User
            AuthRecipeUserInfo userInfo = EmailPassword.signUp(process.main, "test@example.com", "testPass123");
            String superTokensUserId = userInfo.getSupertokensUserId();
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
                    SemVer.v2_15.get(), "");
            assertEquals("OK", deleteResponse.get("status").getAsString());

            // check that user doesnt exist
            {
                AuthRecipeUserInfo response = EmailPassword.getUserUsingId(process.main, superTokensUserId);
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

    // In reference to https://docs.google.com/spreadsheets/d/17hYV32B0aDCeLnSxbZhfRN2Y9b0LC2xUF44vV88RNAA/edit#gid=0
    // test intermediate state behavior, deleting superTokensUserId_1
    @Test
    public void testDeleteUserBehaviorInIntermediateStateWithUser_1sUserId() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create an EmailPassword User
        AuthRecipeUserInfo userInfo_1 = EmailPassword.signUp(process.main, "test@example.com", "testPassword123");

        // associate some data with user
        JsonObject data = new JsonObject();
        data.addProperty("test", "testData");
        UserMetadata.updateUserMetadata(process.main, userInfo_1.getSupertokensUserId(), data);

        // create a new User who we would like to migrate the EmailPassword user to
        ThirdParty.SignInUpResponse userInfo_2 = ThirdParty.signInUp(process.main, "google", "test-google",
                "test123@example.com");

        // force create a mapping between the thirdParty user and EmailPassword user
        UserIdMapping.createUserIdMapping(process.main, userInfo_2.user.getSupertokensUserId(),
                userInfo_1.getSupertokensUserId(), null, true);

        // delete User with EmailPassword userId
        {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("userId", userInfo_1.getSupertokensUserId());

            JsonObject deleteResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/user/remove", requestBody, 1000, 1000, null,
                    SemVer.v2_15.get(), "emailpassword");
            assertEquals("OK", deleteResponse.get("status").getAsString());

        }
        // check that only auth tables for EmailPassword user have been deleted and the userMetadata table entries still
        // exist
        {
            AuthRecipeUserInfo epUser = EmailPassword.getUserUsingId(process.main, userInfo_1.getSupertokensUserId());
            assertNull(epUser);

            JsonObject epUserMetadata = UserMetadata.getUserMetadata(process.main, userInfo_1.getSupertokensUserId());
            assertNotNull(epUserMetadata);
            assertEquals(epUserMetadata.get("test").getAsString(), "testData");
        }
        // check that the mapping still exists
        {
            io.supertokens.pluginInterface.useridmapping.UserIdMapping mapping = UserIdMapping
                    .getUserIdMapping(process.main, userInfo_2.user.getSupertokensUserId(), UserIdType.ANY);
            assertNotNull(mapping);
            assertEquals(mapping.superTokensUserId, userInfo_2.user.getSupertokensUserId());
            assertEquals(mapping.externalUserId, userInfo_1.getSupertokensUserId());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // test intermediate state behavior, deleting superTokensUserId_2
    @Test
    public void testDeleteUserBehaviorInIntermediateStateWithUser_2sUserId() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create an EmailPassword User
        AuthRecipeUserInfo userInfo_1 = EmailPassword.signUp(process.main, "test@example.com", "testPassword123");

        // associate some data with user
        JsonObject data = new JsonObject();
        data.addProperty("test", "testData");
        UserMetadata.updateUserMetadata(process.main, userInfo_1.getSupertokensUserId(), data);

        // create a new User who we would like to migrate the EmailPassword user to
        ThirdParty.SignInUpResponse userInfo_2 = ThirdParty.signInUp(process.main, "google", "test-google",
                "test123@example.com");

        // force create a mapping between the thirdParty user and EmailPassword user
        UserIdMapping.createUserIdMapping(process.main, userInfo_2.user.getSupertokensUserId(),
                userInfo_1.getSupertokensUserId(), null, true);

        // delete User with ThirdParty users id
        {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("userId", userInfo_2.user.getSupertokensUserId());

            JsonObject deleteResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/user/remove", requestBody, 1000, 1000, null,
                    SemVer.v2_15.get(), "thirdparty");
            assertEquals("OK", deleteResponse.get("status").getAsString());

        }
        // check that only auth tables for thirdParty user have been deleted and the userMetadata table entries still
        // exist
        {
            AuthRecipeUserInfo tpUserInfo = ThirdParty.getUser(process.main,
                    userInfo_2.user.getSupertokensUserId());
            assertNull(tpUserInfo);

            JsonObject epUserMetadata = UserMetadata.getUserMetadata(process.main, userInfo_1.getSupertokensUserId());
            assertNotNull(epUserMetadata);
            assertEquals(epUserMetadata.get("test").getAsString(), "testData");
        }
        // check that the mapping is also deleted
        {
            io.supertokens.pluginInterface.useridmapping.UserIdMapping mapping = UserIdMapping
                    .getUserIdMapping(process.main, userInfo_2.user.getSupertokensUserId(), UserIdType.ANY);
            assertNull(mapping);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
