/*
 *    Copyright (c) 2023, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.test.accountlinking.api;

import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.authRecipe.AuthRecipe;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.httpRequest.HttpResponseException;
import io.supertokens.useridmapping.UserIdMapping;
import io.supertokens.useridmapping.UserIdType;
import io.supertokens.usermetadata.UserMetadata;
import io.supertokens.webserver.WebserverAPI;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.io.IOException;

import static org.junit.Assert.*;

public class DeleteUserTest {
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

    private void deleteUserAPICall(Main main, String userId)
            throws HttpResponseException, IOException {
        deleteUserAPICall(main, userId, null);
    }

    private void deleteUserAPICall(Main main, String userId, Boolean removeAllLinkedAccounts)
            throws HttpResponseException, IOException {
        JsonObject params = new JsonObject();
        params.addProperty("userId", userId);
        if (removeAllLinkedAccounts != null) {
            params.addProperty("removeAllLinkedAccounts", removeAllLinkedAccounts);
        }

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                "http://localhost:3567/user/remove", params, 1000, 1000, null,
                WebserverAPI.getLatestCDIVersion().get(), "");
        assertEquals("OK", response.get("status").getAsString());
    }

    @Test
    public void deleteLinkedUserWithoutRemovingAllUsers() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        AuthRecipeUserInfo r1 = EmailPassword.signUp(process.main, "test@example.com", "pass123");

        AuthRecipeUserInfo r2 = EmailPassword.signUp(process.main, "test2@example.com", "pass123");

        AuthRecipe.createPrimaryUser(process.main, r2.getSupertokensUserId());

        assert (!AuthRecipe.linkAccounts(process.main, r1.getSupertokensUserId(),
                r2.getSupertokensUserId()).wasAlreadyLinked);

        deleteUserAPICall(process.main, r1.getSupertokensUserId(), false);

        assertNull(AuthRecipe.getUserById(process.main, r1.getSupertokensUserId()));

        AuthRecipeUserInfo user = AuthRecipe.getUserById(process.main, r2.getSupertokensUserId());

        assert (user.loginMethods.length == 1);
        assert (user.isPrimaryUser);
        assert (user.getSupertokensUserId().equals(r2.getSupertokensUserId()));
        assert (user.loginMethods[0].getSupertokensUserId().equals(r2.getSupertokensUserId()));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void deleteLinkedPrimaryUserWithoutRemovingAllUsers() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        AuthRecipeUserInfo r1 = EmailPassword.signUp(process.main, "test@example.com", "pass123");

        AuthRecipeUserInfo r2 = EmailPassword.signUp(process.main, "test2@example.com", "pass123");

        AuthRecipe.createPrimaryUser(process.main, r2.getSupertokensUserId());

        assert (!AuthRecipe.linkAccounts(process.main, r1.getSupertokensUserId(),
                r2.getSupertokensUserId()).wasAlreadyLinked);

        deleteUserAPICall(process.main, r2.getSupertokensUserId(), false);

        AuthRecipeUserInfo userP = AuthRecipe.getUserById(process.main, r2.getSupertokensUserId());

        AuthRecipeUserInfo user = AuthRecipe.getUserById(process.main, r1.getSupertokensUserId());

        assert (user.loginMethods.length == 1);
        assert (user.isPrimaryUser);
        assert (user.getSupertokensUserId().equals(r2.getSupertokensUserId()));
        assert (user.loginMethods[0].getSupertokensUserId().equals(r1.getSupertokensUserId()));
        assert (userP.equals(user));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void deleteLinkedPrimaryUserRemovingAllUsers() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        AuthRecipeUserInfo r1 = EmailPassword.signUp(process.main, "test@example.com", "pass123");

        AuthRecipeUserInfo r2 = EmailPassword.signUp(process.main, "test2@example.com", "pass123");

        AuthRecipe.createPrimaryUser(process.main, r2.getSupertokensUserId());

        assert (!AuthRecipe.linkAccounts(process.main, r1.getSupertokensUserId(),
                r2.getSupertokensUserId()).wasAlreadyLinked);

        deleteUserAPICall(process.main, r2.getSupertokensUserId());

        AuthRecipeUserInfo userP = AuthRecipe.getUserById(process.main, r2.getSupertokensUserId());

        AuthRecipeUserInfo user = AuthRecipe.getUserById(process.main, r1.getSupertokensUserId());

        assert (user == null && userP == null);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void deleteLinkedPrimaryUserRemovingAllUsers2() throws Exception {

        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        AuthRecipeUserInfo r1 = EmailPassword.signUp(process.main, "test@example.com", "pass123");

        AuthRecipeUserInfo r2 = EmailPassword.signUp(process.main, "test2@example.com", "pass123");

        AuthRecipe.createPrimaryUser(process.main, r2.getSupertokensUserId());

        assert (!AuthRecipe.linkAccounts(process.main, r1.getSupertokensUserId(),
                r2.getSupertokensUserId()).wasAlreadyLinked);

        deleteUserAPICall(process.main, r1.getSupertokensUserId());

        AuthRecipeUserInfo userP = AuthRecipe.getUserById(process.main, r2.getSupertokensUserId());

        AuthRecipeUserInfo user = AuthRecipe.getUserById(process.main, r1.getSupertokensUserId());

        assert (user == null && userP == null);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void deleteUserTestWithUserIdMapping1() throws Exception {
        /*
         * recipe user r1 is mapped to e1 which has some metadata with e1 as the key. r1 gets linked to r2 which is
         * mapped to e2 with some metadata associated with it. Now we want to delete r1. This should clear r1 entry,
         * e1 entry, and e1 metadata, but should not clear e2 stuff at all.
         * */
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        AuthRecipeUserInfo r1 = EmailPassword.signUp(process.main, "test@example.com", "pass123");
        UserIdMapping.createUserIdMapping(process.main, r1.getSupertokensUserId(), "e1", null, false);
        JsonObject metadata = new JsonObject();
        metadata.addProperty("k1", "v1");
        UserMetadata.updateUserMetadata(process.main, "e1", metadata);

        AuthRecipeUserInfo r2 = EmailPassword.signUp(process.main, "test2@example.com", "pass123");
        UserIdMapping.createUserIdMapping(process.main, r2.getSupertokensUserId(), "e2", null, false);
        UserMetadata.updateUserMetadata(process.main, "e2", metadata);

        AuthRecipe.createPrimaryUser(process.main, r2.getSupertokensUserId());

        assert (!AuthRecipe.linkAccounts(process.main, r1.getSupertokensUserId(),
                r2.getSupertokensUserId()).wasAlreadyLinked);

        deleteUserAPICall(process.main, r1.getSupertokensUserId(), false);

        assertNull(AuthRecipe.getUserById(process.main, r1.getSupertokensUserId()));

        assertNull(AuthRecipe.getUserById(process.main, "e2"));

        assertNotNull(AuthRecipe.getUserById(process.main, r2.getSupertokensUserId()));

        assertEquals(UserMetadata.getUserMetadata(process.main, "e1"), new JsonObject());
        assertEquals(UserMetadata.getUserMetadata(process.main, r1.getSupertokensUserId()), new JsonObject());
        assertEquals(UserMetadata.getUserMetadata(process.main, "e2"), metadata);
        assertEquals(UserMetadata.getUserMetadata(process.main, r2.getSupertokensUserId()), new JsonObject());
        assert (UserIdMapping.getUserIdMapping(process.main, r2.getSupertokensUserId(), UserIdType.SUPERTOKENS) !=
                null);
        assert (UserIdMapping.getUserIdMapping(process.main, r1.getSupertokensUserId(), UserIdType.SUPERTOKENS) ==
                null);


        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void deleteUserTestWithUserIdMapping2() throws Exception {
        /*
         * recipe user r1 exists. r1 gets linked to r2 which is mapped to e2 with some metadata associated with it.
         * Now we want to delete r1 with linked all recipes as true. This should clear r1, r2 entry clear e2 metadata.
         * */
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        AuthRecipeUserInfo r1 = EmailPassword.signUp(process.main, "test@example.com", "pass123");
        UserIdMapping.createUserIdMapping(process.main, r1.getSupertokensUserId(), "e1", null, false);
        JsonObject metadata = new JsonObject();
        metadata.addProperty("k1", "v1");
        UserMetadata.updateUserMetadata(process.main, "e1", metadata);

        AuthRecipeUserInfo r2 = EmailPassword.signUp(process.main, "test2@example.com", "pass123");
        UserIdMapping.createUserIdMapping(process.main, r2.getSupertokensUserId(), "e2", null, false);
        UserMetadata.updateUserMetadata(process.main, "e2", metadata);

        AuthRecipe.createPrimaryUser(process.main, r2.getSupertokensUserId());

        assert (!AuthRecipe.linkAccounts(process.main, r1.getSupertokensUserId(),
                r2.getSupertokensUserId()).wasAlreadyLinked);

        deleteUserAPICall(process.main, r1.getSupertokensUserId());

        assertNull(AuthRecipe.getUserById(process.main, r1.getSupertokensUserId()));

        assertNull(AuthRecipe.getUserById(process.main, "e2"));

        assertNull(AuthRecipe.getUserById(process.main, r2.getSupertokensUserId()));

        assertEquals(UserMetadata.getUserMetadata(process.main, "e1"), new JsonObject());
        assertEquals(UserMetadata.getUserMetadata(process.main, r1.getSupertokensUserId()), new JsonObject());
        assertEquals(UserMetadata.getUserMetadata(process.main, "e2"), new JsonObject());
        assertEquals(UserMetadata.getUserMetadata(process.main, r2.getSupertokensUserId()), new JsonObject());
        assert (UserIdMapping.getUserIdMapping(process.main, r2.getSupertokensUserId(), UserIdType.SUPERTOKENS) ==
                null);
        assert (UserIdMapping.getUserIdMapping(process.main, r1.getSupertokensUserId(), UserIdType.SUPERTOKENS) ==
                null);


        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void deleteUserTestWithUserIdMapping3() throws Exception {
        /*
         * three recipes are linked, r1, r2, r3 (primary user is r1). We have external user ID mapping for all three
         * with some metadata. First we delete r1. This should not delete metadata and linked accounts. Then we
         * delete r2 - this should delete metadata of r2 and r2. The we delete r3 - this should delete metadata of r3
         *  and r1
         * */
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        AuthRecipeUserInfo r1 = EmailPassword.signUp(process.main, "test@example.com", "pass123");
        UserIdMapping.createUserIdMapping(process.main, r1.getSupertokensUserId(), "e1", null, false);
        JsonObject metadata = new JsonObject();
        metadata.addProperty("k1", "v1");
        UserMetadata.updateUserMetadata(process.main, "e1", metadata);

        AuthRecipeUserInfo r2 = EmailPassword.signUp(process.main, "test2@example.com", "pass123");
        UserIdMapping.createUserIdMapping(process.main, r2.getSupertokensUserId(), "e2", null, false);
        UserMetadata.updateUserMetadata(process.main, "e2", metadata);

        AuthRecipeUserInfo r3 = EmailPassword.signUp(process.main, "test3@example.com", "pass123");
        UserIdMapping.createUserIdMapping(process.main, r3.getSupertokensUserId(), "e3", null, false);
        UserMetadata.updateUserMetadata(process.main, "e3", metadata);

        AuthRecipe.createPrimaryUser(process.main, r2.getSupertokensUserId());

        assert (!AuthRecipe.linkAccounts(process.main, r1.getSupertokensUserId(),
                r2.getSupertokensUserId()).wasAlreadyLinked);
        assert (!AuthRecipe.linkAccounts(process.main, r3.getSupertokensUserId(),
                r1.getSupertokensUserId()).wasAlreadyLinked);

        deleteUserAPICall(process.main, r1.getSupertokensUserId(), false);

        assertNull(AuthRecipe.getUserById(process.main, r1.getSupertokensUserId()));

        assertEquals(UserMetadata.getUserMetadata(process.main, "e1"), new JsonObject());
        assertEquals(UserMetadata.getUserMetadata(process.main, r1.getSupertokensUserId()), new JsonObject());

        {
            AuthRecipeUserInfo userR2 = AuthRecipe.getUserById(process.main, r2.getSupertokensUserId());
            AuthRecipeUserInfo userR3 = AuthRecipe.getUserById(process.main, r3.getSupertokensUserId());
            assert (userR2.equals(userR3));
            assert (userR2.loginMethods.length == 2);
            assertEquals(UserMetadata.getUserMetadata(process.main, "e2"), metadata);
            assertEquals(UserMetadata.getUserMetadata(process.main, "e3"), metadata);
            assert (UserIdMapping.getUserIdMapping(process.main, r2.getSupertokensUserId(), UserIdType.SUPERTOKENS) !=
                    null);
            assert (UserIdMapping.getUserIdMapping(process.main, r3.getSupertokensUserId(), UserIdType.SUPERTOKENS) !=
                    null);
            assert (UserIdMapping.getUserIdMapping(process.main, r1.getSupertokensUserId(), UserIdType.SUPERTOKENS) ==
                    null);
        }

        deleteUserAPICall(process.main, r2.getSupertokensUserId(), false);

        {
            AuthRecipeUserInfo userR2 = AuthRecipe.getUserById(process.main, r2.getSupertokensUserId());
            AuthRecipeUserInfo userR3 = AuthRecipe.getUserById(process.main, r3.getSupertokensUserId());
            assert (userR2.equals(userR3));
            assert (userR2.loginMethods.length == 1);
            assertEquals(UserMetadata.getUserMetadata(process.main, "e2"), metadata);
            assertEquals(UserMetadata.getUserMetadata(process.main, "e3"), metadata);
            assert (UserIdMapping.getUserIdMapping(process.main, r2.getSupertokensUserId(), UserIdType.SUPERTOKENS) !=
                    null);
            assert (UserIdMapping.getUserIdMapping(process.main, r3.getSupertokensUserId(), UserIdType.SUPERTOKENS) !=
                    null);
            assert (UserIdMapping.getUserIdMapping(process.main, r1.getSupertokensUserId(), UserIdType.SUPERTOKENS) ==
                    null);
        }

        deleteUserAPICall(process.main, r3.getSupertokensUserId(), false);

        {
            AuthRecipeUserInfo userR2 = AuthRecipe.getUserById(process.main, r2.getSupertokensUserId());
            AuthRecipeUserInfo userR3 = AuthRecipe.getUserById(process.main, r3.getSupertokensUserId());
            assert (userR2 == null && userR3 == null);
            assertEquals(UserMetadata.getUserMetadata(process.main, "e2"), new JsonObject());
            assertEquals(UserMetadata.getUserMetadata(process.main, "e3"), new JsonObject());
            assert (UserIdMapping.getUserIdMapping(process.main, r2.getSupertokensUserId(), UserIdType.SUPERTOKENS) ==
                    null);
            assert (UserIdMapping.getUserIdMapping(process.main, r3.getSupertokensUserId(), UserIdType.SUPERTOKENS) ==
                    null);
            assert (UserIdMapping.getUserIdMapping(process.main, r1.getSupertokensUserId(), UserIdType.SUPERTOKENS) ==
                    null);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
