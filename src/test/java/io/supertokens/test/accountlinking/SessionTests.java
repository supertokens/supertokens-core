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

package io.supertokens.test.accountlinking;

import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.authRecipe.AuthRecipe;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.exceptions.UnauthorisedException;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.multitenancy.exception.CannotModifyBaseConfigException;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.*;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.session.Session;
import io.supertokens.session.info.SessionInformationHolder;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.thirdparty.InvalidProviderConfigException;
import io.supertokens.useridmapping.UserIdMapping;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.io.IOException;

import static org.junit.Assert.*;

public class SessionTests {
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

    TenantIdentifier t1, t2, t3, t4;

    private void createTenants(Main main)
            throws StorageQueryException, TenantOrAppNotFoundException, InvalidProviderConfigException,
            FeatureNotEnabledException, IOException, InvalidConfigException,
            CannotModifyBaseConfigException, BadPermissionException {
        // User pool 1 - (null, a1, null)
        // User pool 2 - (null, a1, t1), (null, a1, t2)

        { // tenant 1
            JsonObject config = new JsonObject();
            TenantIdentifier tenantIdentifier = new TenantIdentifier(null, "a1", null);

            StorageLayer.getStorage(new TenantIdentifier(null, null, null), main)
                    .modifyConfigToAddANewUserPoolForTesting(config, 1);

            Multitenancy.addNewOrUpdateAppOrTenant(
                    main,
                    new TenantIdentifier(null, null, null),
                    new TenantConfig(
                            tenantIdentifier,
                            new EmailPasswordConfig(true),
                            new ThirdPartyConfig(true, null),
                            new PasswordlessConfig(true),
                            null, null, config
                    )
            );
        }

        { // tenant 2
            JsonObject config = new JsonObject();
            TenantIdentifier tenantIdentifier = new TenantIdentifier(null, "a1", "t1");

            StorageLayer.getStorage(new TenantIdentifier(null, null, null), main)
                    .modifyConfigToAddANewUserPoolForTesting(config, 1);

            Multitenancy.addNewOrUpdateAppOrTenant(
                    main,
                    new TenantIdentifier(null, "a1", null),
                    new TenantConfig(
                            tenantIdentifier,
                            new EmailPasswordConfig(true),
                            new ThirdPartyConfig(true, null),
                            new PasswordlessConfig(true),
                            null, null, config
                    )
            );
        }

        { // tenant 3
            JsonObject config = new JsonObject();
            TenantIdentifier tenantIdentifier = new TenantIdentifier(null, "a1", "t2");

            StorageLayer.getStorage(new TenantIdentifier(null, null, null), main)
                    .modifyConfigToAddANewUserPoolForTesting(config, 1);

            Multitenancy.addNewOrUpdateAppOrTenant(
                    main,
                    new TenantIdentifier(null, "a1", null),
                    new TenantConfig(
                            tenantIdentifier,
                            new EmailPasswordConfig(true),
                            new ThirdPartyConfig(true, null),
                            new PasswordlessConfig(true),
                            null, null, config
                    )
            );
        }

        { // tenant 4
            JsonObject config = new JsonObject();
            TenantIdentifier tenantIdentifier = new TenantIdentifier(null, "a1", "t3");

            StorageLayer.getStorage(new TenantIdentifier(null, null, null), main)
                    .modifyConfigToAddANewUserPoolForTesting(config, 1);

            Multitenancy.addNewOrUpdateAppOrTenant(
                    main,
                    new TenantIdentifier(null, "a1", null),
                    new TenantConfig(
                            tenantIdentifier,
                            new EmailPasswordConfig(true),
                            new ThirdPartyConfig(true, null),
                            new PasswordlessConfig(true),
                            null, null, config
                    )
            );
        }

        t1 = new TenantIdentifier(null, "a1", null);
        t2 = new TenantIdentifier(null, "a1", "t1");
        t3 = new TenantIdentifier(null, "a1", "t2");
        t4 = new TenantIdentifier(null, "a1", "t3");
    }

    @Test
    public void testCreateSessionWithRecipeUserIdReturnsSessionWithPrimaryUserId() throws Exception {
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

        AuthRecipeUserInfo user1 = EmailPassword.signUp(process.getProcess(), "test1@example.com", "password");
        AuthRecipeUserInfo user2 = EmailPassword.signUp(process.getProcess(), "test2@example.com", "password");
        AuthRecipe.createPrimaryUser(process.getProcess(), user1.getSupertokensUserId());
        AuthRecipe.linkAccounts(process.getProcess(), user2.getSupertokensUserId(), user1.getSupertokensUserId());

        SessionInformationHolder session = Session.createNewSession(process.getProcess(), user2.getSupertokensUserId(),
                new JsonObject(), new JsonObject());

        assertEquals(user1.getSupertokensUserId(), session.session.userId);
        assertEquals(user2.getSupertokensUserId(), session.session.recipeUserId);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testSessionIsRemovedWhileLinking() throws Exception {
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

        AuthRecipeUserInfo user1 = EmailPassword.signUp(process.getProcess(), "test1@example.com", "password");
        AuthRecipeUserInfo user2 = EmailPassword.signUp(process.getProcess(), "test2@example.com", "password");
        AuthRecipe.createPrimaryUser(process.getProcess(), user1.getSupertokensUserId());

        UserIdMapping.createUserIdMapping(process.getProcess(), user2.getSupertokensUserId(), "extid2", null, false);

        SessionInformationHolder session1 = Session.createNewSession(process.getProcess(),
                user1.getSupertokensUserId(), new JsonObject(), new JsonObject());
        SessionInformationHolder session2 = Session.createNewSession(process.getProcess(), "extid2", new JsonObject(),
                new JsonObject());

        AuthRecipe.linkAccounts(process.getProcess(), user2.getSupertokensUserId(), user1.getSupertokensUserId());

        assertNotNull(Session.getSession(process.getProcess(), session1.session.handle));
        try {
            Session.getSession(process.getProcess(), session2.session.handle);
            fail();
        } catch (UnauthorisedException e) {
            // ok
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testSessionIsRemovedWhileUnlinking() throws Exception {
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

        AuthRecipeUserInfo user1 = EmailPassword.signUp(process.getProcess(), "test1@example.com", "password");
        AuthRecipeUserInfo user2 = EmailPassword.signUp(process.getProcess(), "test2@example.com", "password");
        AuthRecipe.createPrimaryUser(process.getProcess(), user1.getSupertokensUserId());

        UserIdMapping.createUserIdMapping(process.getProcess(), user1.getSupertokensUserId(), "extid1", null, false);
        UserIdMapping.createUserIdMapping(process.getProcess(), user2.getSupertokensUserId(), "extid2", null, false);

        AuthRecipe.linkAccounts(process.getProcess(), user2.getSupertokensUserId(), user1.getSupertokensUserId());

        SessionInformationHolder session1 = Session.createNewSession(process.getProcess(),
                "extid1", new JsonObject(), new JsonObject());

        AuthRecipe.unlinkAccounts(process.getProcess(), user1.getSupertokensUserId());

        try {
            Session.getSession(process.getProcess(), session1.session.handle);
            fail();
        } catch (UnauthorisedException e) {
            // ok
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testSessionBehaviourWhenUserBelongsTo2TenantsAndThenLinkedToSomeOtherUser1() throws Exception {
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

        createTenants(process.getProcess());

        Storage t1Storage = (StorageLayer.getStorage(t1, process.getProcess()));
        Storage t2Storage = (StorageLayer.getStorage(t2, process.getProcess()));

        AuthRecipeUserInfo user1 = EmailPassword.signUp(t1, t1Storage, process.getProcess(), "test@example.com",
                "password");
        AuthRecipeUserInfo user2 = EmailPassword.signUp(t1, t1Storage, process.getProcess(), "test1@example.com",
                "password");

        AuthRecipe.createPrimaryUser(process.getProcess(), t1.toAppIdentifier(),
                t1Storage, user2.getSupertokensUserId());
        Multitenancy.addUserIdToTenant(process.getProcess(), t2, t2Storage, user1.getSupertokensUserId());

        SessionInformationHolder session1 = Session.createNewSession(t2, t2Storage, process.getProcess(),
                user1.getSupertokensUserId(), new JsonObject(), new JsonObject());

        // Linking user1 to user2 on t1 should revoke the session
        AuthRecipe.linkAccounts(process.getProcess(), t1.toAppIdentifier(), t1Storage,
                user1.getSupertokensUserId(), user2.getSupertokensUserId());

        try {
            Session.getSession(t2, t2Storage, session1.session.handle);
            fail();
        } catch (UnauthorisedException e) {
            // ok
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testSessionBehaviourWhenUserBelongsTo2TenantsAndThenLinkedToSomeOtherUser2() throws Exception {
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

        createTenants(process.getProcess());

        Storage t1Storage = (StorageLayer.getStorage(t1, process.getProcess()));
        Storage t2Storage = (StorageLayer.getStorage(t2, process.getProcess()));

        AuthRecipeUserInfo user1 = EmailPassword.signUp(t1, t1Storage, process.getProcess(), "test@example.com",
                "password");
        AuthRecipeUserInfo user2 = EmailPassword.signUp(t1, t1Storage, process.getProcess(), "test1@example.com",
                "password");

        AuthRecipe.createPrimaryUser(process.getProcess(), t1.toAppIdentifier(),
                t1Storage, user2.getSupertokensUserId());

        SessionInformationHolder session1 = Session.createNewSession(t2, t2Storage, process.getProcess(),
                user1.getSupertokensUserId(), new JsonObject(), new JsonObject());

        // Linking user1 to user2 on t1 should revoke the session
        AuthRecipe.linkAccounts(process.getProcess(), t1.toAppIdentifier(), t1Storage,
                user1.getSupertokensUserId(), user2.getSupertokensUserId());

        try {
            // session gets removed on t2 as well
            Session.getSession(t2, t2Storage, session1.session.handle);
            fail();
        } catch (UnauthorisedException e) {
            // ok
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testSessionBehaviourWhenUserBelongsTo2TenantsAndThenLinkedToSomeOtherUser3() throws Exception {
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

        createTenants(process.getProcess());

        Storage t1Storage = (StorageLayer.getStorage(t1, process.getProcess()));
        Storage t2Storage = (StorageLayer.getStorage(t2, process.getProcess()));

        AuthRecipeUserInfo user1 = EmailPassword.signUp(t1, t1Storage, process.getProcess(), "test@example.com",
                "password");
        AuthRecipeUserInfo user2 = EmailPassword.signUp(t1, t1Storage, process.getProcess(), "test1@example.com",
                "password");

        AuthRecipe.createPrimaryUser(process.getProcess(), t1.toAppIdentifier(), t1Storage,
                user2.getSupertokensUserId());
        AuthRecipe.linkAccounts(process.getProcess(), t1.toAppIdentifier(), t1Storage, user1.getSupertokensUserId(),
                user2.getSupertokensUserId());

        SessionInformationHolder session1 = Session.createNewSession(t2, t2Storage, process.getProcess(),
                user1.getSupertokensUserId(), new JsonObject(), new JsonObject());

        AuthRecipe.unlinkAccounts(process.getProcess(), t1.toAppIdentifier(), t1Storage, user2.getSupertokensUserId());

        // session must be intact
        Session.getSession(t2, t2Storage, session1.session.handle);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testCreateSessionUsesPrimaryUserIdEvenWhenTheUserIsNotInThatTenant() throws Exception {
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

        createTenants(process.getProcess());

        Storage t1Storage = (StorageLayer.getStorage(t1, process.getProcess()));
        Storage t2Storage = (StorageLayer.getStorage(t2, process.getProcess()));

        AuthRecipeUserInfo user1 = EmailPassword.signUp(t1, t1Storage, process.getProcess(), "test@example.com",
                "password");
        AuthRecipeUserInfo user2 = EmailPassword.signUp(t1, t1Storage, process.getProcess(), "test1@example.com",
                "password");

        AuthRecipe.createPrimaryUser(process.getProcess(), t1.toAppIdentifier(), t1Storage,
                user2.getSupertokensUserId());
        AuthRecipe.linkAccounts(process.getProcess(), t1.toAppIdentifier(), t1Storage, user1.getSupertokensUserId(),
                user2.getSupertokensUserId());

        SessionInformationHolder session1 = Session.createNewSession(t2, t2Storage, process.getProcess(),
                user1.getSupertokensUserId(), new JsonObject(), new JsonObject());

        // Should still consider the primaryUserId
        assertEquals(user2.getSupertokensUserId(), session1.session.userId);
        assertEquals(user1.getSupertokensUserId(), session1.session.recipeUserId);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testGetSessionForUserWithAndWithoutIncludingAllLinkedAccounts() throws Exception {
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

        AuthRecipeUserInfo user1 = EmailPassword.signUp(process.getProcess(), "test1@example.com", "password");
        AuthRecipeUserInfo user2 = EmailPassword.signUp(process.getProcess(), "test2@example.com", "password");
        AuthRecipe.createPrimaryUser(process.getProcess(), user1.getSupertokensUserId());
        AuthRecipe.linkAccounts(process.getProcess(), user2.getSupertokensUserId(), user1.getSupertokensUserId());

        SessionInformationHolder session1 = Session.createNewSession(process.getProcess(), user1.getSupertokensUserId(),
                new JsonObject(), new JsonObject());
        SessionInformationHolder session2 = Session.createNewSession(process.getProcess(), user2.getSupertokensUserId(),
                new JsonObject(), new JsonObject());


        Storage baseTenant = (StorageLayer.getBaseStorage(process.getProcess()));

        {
            String[] sessions = Session.getAllNonExpiredSessionHandlesForUser(TenantIdentifier.BASE_TENANT, baseTenant,
                    user1.getSupertokensUserId(),
                    false);
            assertEquals(1, sessions.length);
            assertEquals(session1.session.handle, sessions[0]);
        }
        {
            String[] sessions = Session.getAllNonExpiredSessionHandlesForUser(TenantIdentifier.BASE_TENANT, baseTenant,
                    user2.getSupertokensUserId(),
                    false);
            assertEquals(1, sessions.length);
            assertEquals(session2.session.handle, sessions[0]);
        }

        {
            String[] sessions = Session.getAllNonExpiredSessionHandlesForUser(TenantIdentifier.BASE_TENANT, baseTenant,
                    user1.getSupertokensUserId(),
                    true);
            assertEquals(2, sessions.length);
        }
        {
            String[] sessions = Session.getAllNonExpiredSessionHandlesForUser(TenantIdentifier.BASE_TENANT, baseTenant,
                    user2.getSupertokensUserId(),
                    true);
            assertEquals(2, sessions.length);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testRevokeSessionsForUserWithAndWithoutIncludingAllLinkedAccounts() throws Exception {
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


        AuthRecipeUserInfo user1 = EmailPassword.signUp(process.getProcess(), "test1@example.com", "password");
        AuthRecipeUserInfo user2 = EmailPassword.signUp(process.getProcess(), "test2@example.com", "password");
        AuthRecipe.createPrimaryUser(process.getProcess(), user1.getSupertokensUserId());
        AuthRecipe.linkAccounts(process.getProcess(), user2.getSupertokensUserId(), user1.getSupertokensUserId());

        {
            SessionInformationHolder session1 = Session.createNewSession(process.getProcess(),
                    user1.getSupertokensUserId(),
                    new JsonObject(), new JsonObject());
            SessionInformationHolder session2 = Session.createNewSession(process.getProcess(),
                    user2.getSupertokensUserId(),
                    new JsonObject(), new JsonObject());


            Storage baseTenant = (
                    StorageLayer.getBaseStorage(process.getProcess()));
            Session.revokeAllSessionsForUser(process.getProcess(), TenantIdentifier.BASE_TENANT, baseTenant,
                    user1.getSupertokensUserId(), true);

            try {
                Session.getSession(process.getProcess(), session1.session.handle);
                fail();
            } catch (UnauthorisedException e) {
                // ok
            }
            try {
                Session.getSession(process.getProcess(), session2.session.handle);
                fail();
            } catch (UnauthorisedException e) {
                // ok
            }
        }

        {
            SessionInformationHolder session1 = Session.createNewSession(process.getProcess(),
                    user1.getSupertokensUserId(),
                    new JsonObject(), new JsonObject());
            SessionInformationHolder session2 = Session.createNewSession(process.getProcess(),
                    user2.getSupertokensUserId(),
                    new JsonObject(), new JsonObject());

            Storage baseTenant = (
                    StorageLayer.getBaseStorage(process.getProcess()));
            Session.revokeAllSessionsForUser(process.getProcess(), TenantIdentifier.BASE_TENANT, baseTenant,
                    user2.getSupertokensUserId(), true);

            try {
                Session.getSession(process.getProcess(), session1.session.handle);
                fail();
            } catch (UnauthorisedException e) {
                // ok
            }
            try {
                Session.getSession(process.getProcess(), session2.session.handle);
                fail();
            } catch (UnauthorisedException e) {
                // ok
            }
        }

        {
            SessionInformationHolder session1 = Session.createNewSession(process.getProcess(),
                    user1.getSupertokensUserId(),
                    new JsonObject(), new JsonObject());
            SessionInformationHolder session2 = Session.createNewSession(process.getProcess(),
                    user2.getSupertokensUserId(),
                    new JsonObject(), new JsonObject());

            Storage baseTenant = (
                    StorageLayer.getBaseStorage(process.getProcess()));
            Session.revokeAllSessionsForUser(process.getProcess(), TenantIdentifier.BASE_TENANT, baseTenant,
                    user1.getSupertokensUserId(), false);

            try {
                Session.getSession(process.getProcess(), session1.session.handle);
                fail();
            } catch (UnauthorisedException e) {
                // ok
            }

            Session.getSession(process.getProcess(), session2.session.handle);
        }

        {
            SessionInformationHolder session1 = Session.createNewSession(process.getProcess(),
                    user1.getSupertokensUserId(),
                    new JsonObject(), new JsonObject());
            SessionInformationHolder session2 = Session.createNewSession(process.getProcess(),
                    user2.getSupertokensUserId(),
                    new JsonObject(), new JsonObject());

            Storage baseTenant = (
                    StorageLayer.getBaseStorage(process.getProcess()));
            Session.revokeAllSessionsForUser(process.getProcess(), TenantIdentifier.BASE_TENANT, baseTenant,
                    user2.getSupertokensUserId(), false);

            Session.getSession(process.getProcess(), session1.session.handle);

            try {
                Session.getSession(process.getProcess(), session2.session.handle);
                fail();
            } catch (UnauthorisedException e) {
                // ok
            }
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testCreateSessionWithUserIdMappedForRecipeUser() throws Exception {
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

        AuthRecipeUserInfo user1 = EmailPassword.signUp(process.getProcess(), "test1@example.com", "password");
        AuthRecipeUserInfo user2 = EmailPassword.signUp(process.getProcess(), "test2@example.com", "password");
        AuthRecipe.createPrimaryUser(process.getProcess(), user1.getSupertokensUserId());
        AuthRecipe.linkAccounts(process.getProcess(), user2.getSupertokensUserId(), user1.getSupertokensUserId());

        UserIdMapping.createUserIdMapping(process.getProcess(), user1.getSupertokensUserId(), "extid1", null, false);
        UserIdMapping.createUserIdMapping(process.getProcess(), user2.getSupertokensUserId(), "extid2", null, false);

        SessionInformationHolder session1 = Session.createNewSession(process.getProcess(), user1.getSupertokensUserId(),
                new JsonObject(), new JsonObject());
        SessionInformationHolder session2 = Session.createNewSession(process.getProcess(), user2.getSupertokensUserId(),
                new JsonObject(), new JsonObject());
        SessionInformationHolder session3 = Session.createNewSession(process.getProcess(), "extid1", new JsonObject(),
                new JsonObject());
        SessionInformationHolder session4 = Session.createNewSession(process.getProcess(), "extid2", new JsonObject(),
                new JsonObject());

        assertEquals("extid1", session1.session.userId);
        assertEquals("extid1", session1.session.recipeUserId);
        assertEquals("extid1", session2.session.userId);
        assertEquals("extid2", session2.session.recipeUserId);
        assertEquals("extid1", session3.session.userId);
        assertEquals("extid1", session3.session.recipeUserId);
        assertEquals("extid1", session4.session.userId);
        assertEquals("extid2", session4.session.recipeUserId);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
