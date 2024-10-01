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

package io.supertokens.test.multitenant.api;

import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.exceptions.UnauthorisedException;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.multitenancy.exception.CannotModifyBaseConfigException;
import io.supertokens.passwordless.Passwordless;
import io.supertokens.pluginInterface.ActiveUsersStorage;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.jwt.JWTRecipeStorage;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.nonAuthRecipe.NonAuthRecipeStorage;
import io.supertokens.pluginInterface.usermetadata.UserMetadataStorage;
import io.supertokens.session.Session;
import io.supertokens.session.info.SessionInformationHolder;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpResponseException;
import io.supertokens.thirdparty.InvalidProviderConfigException;
import io.supertokens.thirdparty.ThirdParty;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.reflections.Reflections;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class TestTenantUserAssociation {
    TestingProcessManager.TestingProcess process;

    @AfterClass
    public static void afterTesting() {
        Utils.afterTesting();
    }

    @After
    public void afterEach() throws InterruptedException {
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Before
    public void beforeEach() throws InterruptedException, InvalidProviderConfigException,
            StorageQueryException, FeatureNotEnabledException, TenantOrAppNotFoundException, IOException,
            InvalidConfigException, CannotModifyBaseConfigException, BadPermissionException {
        Utils.reset();

        String[] args = {"../"};

        this.process = TestingProcessManager.start(args);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
    }

    private void createTenants() throws TenantOrAppNotFoundException, HttpResponseException, IOException {
        {
            JsonObject coreConfig = new JsonObject();
            StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                    .modifyConfigToAddANewUserPoolForTesting(coreConfig, 1);

            TestMultitenancyAPIHelper.createApp(
                    process.getProcess(),
                    new TenantIdentifier(null, null, null),
                    "a1", true, true, true,
                    coreConfig);
        }
        {
            JsonObject coreConfig = new JsonObject();
            StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                    .modifyConfigToAddANewUserPoolForTesting(coreConfig, 2);

            TestMultitenancyAPIHelper.createTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, "a1", null),
                    "t1", true, true, true,
                    coreConfig);
        }
        {
            JsonObject coreConfig = new JsonObject();
            StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                    .modifyConfigToAddANewUserPoolForTesting(coreConfig, 2);

            TestMultitenancyAPIHelper.createTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, "a1", null),
                    "t2", true, true, true,
                    coreConfig);
        }
    }

    @Test
    public void testUserAssociationWorksForEmailPasswordUser() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        createTenants();
        JsonObject user = TestMultitenancyAPIHelper.epSignUp(new TenantIdentifier(null, "a1", "t1"), "user@example.com",
                "password", process.getProcess());
        String userId = user.get("id").getAsString();

        JsonObject response = TestMultitenancyAPIHelper.associateUserToTenant(new TenantIdentifier(null, "a1", "t2"),
                userId, process.getProcess());
        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        assertFalse(response.get("wasAlreadyAssociated").getAsBoolean());

        response = TestMultitenancyAPIHelper.associateUserToTenant(new TenantIdentifier(null, "a1", "t2"), userId,
                process.getProcess());
        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        assertTrue(response.get("wasAlreadyAssociated").getAsBoolean());
    }

    @Test
    public void testUserDisassociationWorks() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        createTenants();
        JsonObject user = TestMultitenancyAPIHelper.epSignUp(new TenantIdentifier(null, "a1", "t1"), "user@example.com",
                "password", process.getProcess());
        String userId = user.get("id").getAsString();

        JsonObject response = TestMultitenancyAPIHelper.associateUserToTenant(new TenantIdentifier(null, "a1", "t2"),
                userId, process.getProcess());
        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        assertFalse(response.get("wasAlreadyAssociated").getAsBoolean());

        response = TestMultitenancyAPIHelper.disassociateUserFromTenant(new TenantIdentifier(null, "a1", "t2"), userId,
                process.getProcess());
        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        assertTrue(response.get("wasAssociated").getAsBoolean());

        response = TestMultitenancyAPIHelper.disassociateUserFromTenant(new TenantIdentifier(null, "a1", "t2"), userId,
                process.getProcess());
        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        assertFalse(response.get("wasAssociated").getAsBoolean());

        response = TestMultitenancyAPIHelper.associateUserToTenant(new TenantIdentifier(null, "a1", "t2"), userId,
                process.getProcess());
        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        assertFalse(response.get("wasAlreadyAssociated").getAsBoolean());
    }

    @Test
    public void testUserDisassociationForNotAuthRecipes() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        createTenants();

        Reflections reflections = new Reflections("io.supertokens.pluginInterface");
        Set<Class<? extends NonAuthRecipeStorage>> classes = reflections.getSubTypesOf(NonAuthRecipeStorage.class);
        List<String> names = classes.stream().map(Class::getCanonicalName).collect(Collectors.toList());
        List<String> classNames = new ArrayList<>();
        for (String name : names) {
            if (name.contains("SQLStorage")) {
                continue;
            }

            if (name.equals(UserMetadataStorage.class.getName())
                    || name.equals(JWTRecipeStorage.class.getName())
                    || name.equals(ActiveUsersStorage.class.getName())
            ) {
                // user metadata is app specific and does not have any tenant specific data
                // JWT storage does not have any user specific data
                // Active users storage does not have tenant specific data
                continue;
            }

            classNames.add(name);
        }

        TenantIdentifier t2 = new TenantIdentifier(null, "a1", "t2");

        for (String className : classNames) {
            String userId = "userId";

            StorageLayer.getStorage(t2, process.main).addInfoToNonAuthRecipesBasedOnUserId(t2, className, userId);

            JsonObject response = TestMultitenancyAPIHelper.disassociateUserFromTenant(t2, userId,
                    process.getProcess());
            assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
            assertTrue(response.get("wasAssociated").getAsBoolean());
        }
    }

    @Test
    public void testDisassociateFromAllTenantsAndThenAssociateWithATenantWorks() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        createTenants();
        JsonObject user = TestMultitenancyAPIHelper.epSignUp(new TenantIdentifier(null, "a1", "t1"), "user@example.com",
                "password", process.getProcess());
        String userId = user.get("id").getAsString();

        JsonObject response = TestMultitenancyAPIHelper.disassociateUserFromTenant(
                new TenantIdentifier(null, "a1", "t1"), userId, process.getProcess());
        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        assertTrue(response.get("wasAssociated").getAsBoolean());

        response = TestMultitenancyAPIHelper.associateUserToTenant(new TenantIdentifier(null, "a1", "t2"), userId,
                process.getProcess());
        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        assertFalse(response.get("wasAlreadyAssociated").getAsBoolean());
    }

    @Test
    public void testAssociateOnDifferentStorageIsNotPossible() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        if (StorageLayer.isInMemDb(process.getProcess())) {
            return;
        }

        createTenants();
        JsonObject user = TestMultitenancyAPIHelper.epSignUp(new TenantIdentifier(null, "a1", "t1"), "user@example.com",
                "password", process.getProcess());
        String userId = user.get("id").getAsString();

        JsonObject response = TestMultitenancyAPIHelper.associateUserToTenant(new TenantIdentifier(null, "a1", null),
                userId, process.getProcess());
        assertEquals("UNKNOWN_USER_ID_ERROR", response.get("status").getAsString());
    }

    @Test
    public void testEmailPasswordUsersHaveTenantIds() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        createTenants();

        TenantIdentifier t1 = new TenantIdentifier(null, "a1", "t1");
        TenantIdentifier t2 = new TenantIdentifier(null, "a1", "t2");

        Storage t1Storage = (StorageLayer.getStorage(t1, process.getProcess()));
        Storage t2Storage = (StorageLayer.getStorage(t2, process.getProcess()));

        AuthRecipeUserInfo user = EmailPassword.signUp(t1, t1Storage,
                process.getProcess(), "user@example.com", "password");
        assertArrayEquals(new String[]{"t1"}, user.tenantIds.toArray());

        Multitenancy.addUserIdToTenant(process.getProcess(), t2, t2Storage, user.getSupertokensUserId());
        user = EmailPassword.getUserUsingId(t1.toAppIdentifier(), t1Storage, user.getSupertokensUserId());
        Utils.assertArrayEqualsIgnoreOrder(new String[]{"t1", "t2"}, user.tenantIds.toArray());


        user = EmailPassword.getUserUsingEmail(t1, t1Storage, user.loginMethods[0].email);
        Utils.assertArrayEqualsIgnoreOrder(new String[]{"t1", "t2"}, user.tenantIds.toArray());

        Multitenancy.removeUserIdFromTenant(process.getProcess(), t1, t1Storage, user.getSupertokensUserId(),
                null);
        user = EmailPassword.getUserUsingId(t1.toAppIdentifier(), t1Storage, user.getSupertokensUserId());
        assertArrayEquals(new String[]{"t2"}, user.tenantIds.toArray());
    }

    @Test
    public void testPasswordlessUsersHaveTenantIds1() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        createTenants();

        TenantIdentifier t1 = new TenantIdentifier(null, "a1", "t1");
        TenantIdentifier t2 = new TenantIdentifier(null, "a1", "t2");

        Storage t1Storage = (StorageLayer.getStorage(t1, process.getProcess()));
        Storage t2Storage = (StorageLayer.getStorage(t2, process.getProcess()));

        Passwordless.CreateCodeResponse createCodeResponse = Passwordless.createCode(t1, t1Storage,
                process.getProcess(), "user@example.com", null, null, null);
        Passwordless.ConsumeCodeResponse consumeCodeResponse = Passwordless.consumeCode(t1, t1Storage,
                process.getProcess(),
                createCodeResponse.deviceId, createCodeResponse.deviceIdHash, createCodeResponse.userInputCode, null);
        assertArrayEquals(new String[]{"t1"}, consumeCodeResponse.user.tenantIds.toArray());

        AuthRecipeUserInfo user;
        Multitenancy.addUserIdToTenant(process.getProcess(), t2, t2Storage,
                consumeCodeResponse.user.getSupertokensUserId());
        user = Passwordless.getUserById(t1.toAppIdentifier(), t1Storage,
                consumeCodeResponse.user.getSupertokensUserId());
        Utils.assertArrayEqualsIgnoreOrder(new String[]{"t1", "t2"}, user.tenantIds.toArray());

        user = Passwordless.getUserByEmail(t1, t1Storage, consumeCodeResponse.user.loginMethods[0].email);
        Utils.assertArrayEqualsIgnoreOrder(new String[]{"t1", "t2"}, user.tenantIds.toArray());

        Multitenancy.removeUserIdFromTenant(process.getProcess(), t1, t1Storage,
                consumeCodeResponse.user.getSupertokensUserId(), null);
        user = Passwordless.getUserById(t1.toAppIdentifier(), t1Storage,
                consumeCodeResponse.user.getSupertokensUserId());
        assertArrayEquals(new String[]{"t2"}, user.tenantIds.toArray());
    }

    @Test
    public void testPasswordlessUsersHaveTenantIds2() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        createTenants();

        TenantIdentifier t1 = new TenantIdentifier(null, "a1", "t1");
        TenantIdentifier t2 = new TenantIdentifier(null, "a1", "t2");

        Storage t1Storage = (StorageLayer.getStorage(t1, process.getProcess()));
        Storage t2Storage = (StorageLayer.getStorage(t2, process.getProcess()));

        Passwordless.CreateCodeResponse createCodeResponse = Passwordless.createCode(t1, t1Storage,
                process.getProcess(), null, "+919876543210", null, null);
        Passwordless.ConsumeCodeResponse consumeCodeResponse = Passwordless.consumeCode(t1, t1Storage,
                process.getProcess(),
                createCodeResponse.deviceId, createCodeResponse.deviceIdHash, createCodeResponse.userInputCode, null);
        assertArrayEquals(new String[]{"t1"}, consumeCodeResponse.user.tenantIds.toArray());

        AuthRecipeUserInfo user;
        Multitenancy.addUserIdToTenant(process.getProcess(), t2, t2Storage,
                consumeCodeResponse.user.getSupertokensUserId());
        user = Passwordless.getUserById(t1.toAppIdentifier(), t1Storage,
                consumeCodeResponse.user.getSupertokensUserId());
        Utils.assertArrayEqualsIgnoreOrder(new String[]{"t1", "t2"}, user.tenantIds.toArray());

        user = Passwordless.getUserByPhoneNumber(t1, t1Storage,
                consumeCodeResponse.user.loginMethods[0].phoneNumber);
        Utils.assertArrayEqualsIgnoreOrder(new String[]{"t1", "t2"}, user.tenantIds.toArray());

        Multitenancy.removeUserIdFromTenant(process.getProcess(), t1, t1Storage,
                consumeCodeResponse.user.getSupertokensUserId(), null);
        user = Passwordless.getUserById(t1.toAppIdentifier(), t1Storage,
                consumeCodeResponse.user.getSupertokensUserId());
        assertArrayEquals(new String[]{"t2"}, user.tenantIds.toArray());
    }

    @Test
    public void testThirdPartyUsersHaveTenantIds() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        createTenants();

        TenantIdentifier t1 = new TenantIdentifier(null, "a1", "t1");
        TenantIdentifier t2 = new TenantIdentifier(null, "a1", "t2");

        Storage t1Storage = (StorageLayer.getStorage(t1, process.getProcess()));
        Storage t2Storage = (StorageLayer.getStorage(t2, process.getProcess()));

        ThirdParty.SignInUpResponse signInUpResponse = ThirdParty.signInUp(t1, t1Storage, process.getProcess(),
                "google",
                "googleid", "user@example.com");
        assertArrayEquals(new String[]{"t1"}, signInUpResponse.user.tenantIds.toArray());

        Multitenancy.addUserIdToTenant(process.getProcess(), t2, t2Storage,
                signInUpResponse.user.getSupertokensUserId());
        AuthRecipeUserInfo user = ThirdParty.getUser(
                t1.toAppIdentifier(), t1Storage, signInUpResponse.user.getSupertokensUserId());
        Utils.assertArrayEqualsIgnoreOrder(new String[]{"t1", "t2"}, user.tenantIds.toArray());

        user = ThirdParty.getUsersByEmail(t1, t1Storage, signInUpResponse.user.loginMethods[0].email)[0];
        Utils.assertArrayEqualsIgnoreOrder(new String[]{"t1", "t2"}, user.tenantIds.toArray());

        user = ThirdParty.getUser(t1, t1Storage, "google", "googleid");
        Utils.assertArrayEqualsIgnoreOrder(new String[]{"t1", "t2"}, user.tenantIds.toArray());

        user = ThirdParty.getUser(t2, t2Storage, "google", "googleid");
        Utils.assertArrayEqualsIgnoreOrder(new String[]{"t1", "t2"}, user.tenantIds.toArray());

        Multitenancy.removeUserIdFromTenant(process.getProcess(), t1, t1Storage,
                signInUpResponse.user.getSupertokensUserId(), null);
        user = ThirdParty.getUser(t1.toAppIdentifier(), t1Storage, signInUpResponse.user.getSupertokensUserId());
        assertArrayEquals(new String[]{"t2"}, user.tenantIds.toArray());
    }

    @Test
    public void testThatDisassociateUserFromWrongTenantDoesNotWork() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        createTenants();
        JsonObject user = TestMultitenancyAPIHelper.epSignUp(new TenantIdentifier(null, "a1", "t1"), "user@example.com",
                "password", process.getProcess());
        String userId = user.get("id").getAsString();

        JsonObject response = TestMultitenancyAPIHelper.disassociateUserFromTenant(
                new TenantIdentifier(null, "a1", "t2"), userId, process.getProcess());
        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        assertFalse(response.get("wasAssociated").getAsBoolean());
    }

    @Test
    public void testThatDisassociateUserWithUseridMappingFromWrongTenantDoesNotWork() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        createTenants();
        JsonObject user = TestMultitenancyAPIHelper.epSignUp(new TenantIdentifier(null, "a1", "t1"), "user@example.com",
                "password", process.getProcess());
        String userId = user.get("id").getAsString();

        TestMultitenancyAPIHelper.createUserIdMapping(new TenantIdentifier(null, "a1", null), userId, "externalid",
                process.getProcess());

        JsonObject response = TestMultitenancyAPIHelper.disassociateUserFromTenant(
                new TenantIdentifier(null, "a1", "t2"), "externalid", process.getProcess());
        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        assertFalse(response.get("wasAssociated").getAsBoolean());
    }

    @Test
    public void testAssociateAndDisassociateWithUseridMapping() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        createTenants();
        JsonObject user = TestMultitenancyAPIHelper.epSignUp(new TenantIdentifier(null, "a1", "t1"), "user@example.com",
                "password", process.getProcess());
        String userId = user.get("id").getAsString();

        TestMultitenancyAPIHelper.createUserIdMapping(new TenantIdentifier(null, "a1", null), userId, "externalid",
                process.getProcess());

        JsonObject response = TestMultitenancyAPIHelper.associateUserToTenant(new TenantIdentifier(null, "a1", "t2"),
                "externalid", process.getProcess());
        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        assertFalse(response.get("wasAlreadyAssociated").getAsBoolean());

        response = TestMultitenancyAPIHelper.disassociateUserFromTenant(new TenantIdentifier(null, "a1", "t2"),
                "externalid", process.getProcess());
        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        assertTrue(response.get("wasAssociated").getAsBoolean());

    }

    @Test
    public void testDisassociateUserWithUserIdMappingAndSession() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        createTenants();
        JsonObject user = TestMultitenancyAPIHelper.epSignUp(new TenantIdentifier(null, "a1", "t1"), "user@example.com",
                "password", process.getProcess());
        String userId = user.get("id").getAsString();

        TestMultitenancyAPIHelper.createUserIdMapping(new TenantIdentifier(null, "a1", null), userId, "externalid",
                process.getProcess());

        JsonObject response = TestMultitenancyAPIHelper.associateUserToTenant(new TenantIdentifier(null, "a1", "t2"),
                "externalid", process.getProcess());
        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        assertFalse(response.get("wasAlreadyAssociated").getAsBoolean());

        TenantIdentifier t2 = new TenantIdentifier(null, "a1", "t2");
        Storage t2Storage = (StorageLayer.getStorage(t2, process.getProcess()));

        SessionInformationHolder session = Session.createNewSession(t2, t2Storage,
                process.getProcess(), "externalid", new JsonObject(), new JsonObject());

        response = TestMultitenancyAPIHelper.disassociateUserFromTenant(new TenantIdentifier(null, "a1", "t2"),
                "externalid", process.getProcess());
        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        assertTrue(response.get("wasAssociated").getAsBoolean());

        try {
            Session.getSession(t2, t2Storage, session.session.handle);
            fail();
        } catch (UnauthorisedException e) {
            // OK
        }
    }

    @Test
    public void testThatUserWithSameEmailCannotBeAssociatedToATenantForEp() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        createTenants();
        JsonObject user1 = TestMultitenancyAPIHelper.epSignUp(new TenantIdentifier(null, "a1", "t1"),
                "user@example.com",
                "password", process.getProcess());
        String userId1 = user1.get("id").getAsString();

        TestMultitenancyAPIHelper.epSignUp(new TenantIdentifier(null, "a1", "t2"), "user@example.com",
                "password", process.getProcess());

        JsonObject response = TestMultitenancyAPIHelper.associateUserToTenant(new TenantIdentifier(null, "a1", "t2"),
                userId1, process.getProcess());
        assertEquals("EMAIL_ALREADY_EXISTS_ERROR", response.getAsJsonPrimitive("status").getAsString());
    }

    @Test
    public void testThatUserWithSameThirdPartyInfoCannotBeAssociatedToATenantForTp() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        createTenants();
        JsonObject user1 = TestMultitenancyAPIHelper.tpSignInUp(new TenantIdentifier(null, "a1", "t1"), "google",
                "google-user", "user@example.com",
                process.getProcess());
        String userId1 = user1.get("id").getAsString();

        TestMultitenancyAPIHelper.tpSignInUp(new TenantIdentifier(null, "a1", "t2"), "google", "google-user",
                "user@example.com",
                process.getProcess());

        JsonObject response = TestMultitenancyAPIHelper.associateUserToTenant(new TenantIdentifier(null, "a1", "t2"),
                userId1, process.getProcess());
        assertEquals("THIRD_PARTY_USER_ALREADY_EXISTS_ERROR", response.getAsJsonPrimitive("status").getAsString());
    }

    @Test
    public void testThatUserWithSameEmailCannotBeAssociatedToATenantForPless() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        createTenants();
        JsonObject user1 = TestMultitenancyAPIHelper.plSignInUpEmail(new TenantIdentifier(null, "a1", "t1"),
                "user@example.com",
                process.getProcess());
        String userId1 = user1.get("id").getAsString();

        TestMultitenancyAPIHelper.plSignInUpEmail(new TenantIdentifier(null, "a1", "t2"), "user@example.com",
                process.getProcess());

        JsonObject response = TestMultitenancyAPIHelper.associateUserToTenant(new TenantIdentifier(null, "a1", "t2"),
                userId1, process.getProcess());
        assertEquals("EMAIL_ALREADY_EXISTS_ERROR", response.getAsJsonPrimitive("status").getAsString());
    }

    @Test
    public void testThatUserWithSamePhoneCannotBeAssociatedToATenantForPless() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        createTenants();
        JsonObject user1 = TestMultitenancyAPIHelper.plSignInUpNumber(new TenantIdentifier(null, "a1", "t1"),
                "+919876543210",
                process.getProcess());
        String userId1 = user1.get("id").getAsString();

        TestMultitenancyAPIHelper.plSignInUpNumber(new TenantIdentifier(null, "a1", "t2"), "+919876543210",
                process.getProcess());

        JsonObject response = TestMultitenancyAPIHelper.associateUserToTenant(new TenantIdentifier(null, "a1", "t2"),
                userId1, process.getProcess());
        assertEquals("PHONE_NUMBER_ALREADY_EXISTS_ERROR", response.getAsJsonPrimitive("status").getAsString());
    }
}
