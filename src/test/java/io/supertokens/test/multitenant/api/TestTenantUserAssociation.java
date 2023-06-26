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
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.multitenancy.exception.CannotModifyBaseConfigException;
import io.supertokens.passwordless.Passwordless;
import io.supertokens.pluginInterface.ActiveUsersStorage;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.emailpassword.UserInfo;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.jwt.JWTRecipeStorage;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifierWithStorage;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.nonAuthRecipe.NonAuthRecipeStorage;
import io.supertokens.pluginInterface.usermetadata.UserMetadataStorage;
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
        JsonObject user = TestMultitenancyAPIHelper.epSignUp(new TenantIdentifier(null, "a1", "t1"), "user@example.com", "password", process.getProcess());
        String userId = user.get("id").getAsString();

        JsonObject response = TestMultitenancyAPIHelper.associateUserToTenant(new TenantIdentifier(null, "a1", "t2"), userId, process.getProcess());
        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        assertFalse(response.get("wasAlreadyAssociated").getAsBoolean());

        response = TestMultitenancyAPIHelper.associateUserToTenant(new TenantIdentifier(null, "a1", "t2"), userId, process.getProcess());
        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        assertTrue(response.get("wasAlreadyAssociated").getAsBoolean());
    }

    @Test
    public void testUserDisassociationWorks() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        createTenants();
        JsonObject user = TestMultitenancyAPIHelper.epSignUp(new TenantIdentifier(null, "a1", "t1"), "user@example.com", "password", process.getProcess());
        String userId = user.get("id").getAsString();

        JsonObject response = TestMultitenancyAPIHelper.associateUserToTenant(new TenantIdentifier(null, "a1", "t2"), userId, process.getProcess());
        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        assertFalse(response.get("wasAlreadyAssociated").getAsBoolean());

        response = TestMultitenancyAPIHelper.disassociateUserFromTenant(new TenantIdentifier(null, "a1", "t2"), userId, process.getProcess());
        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        assertTrue(response.get("wasAssociated").getAsBoolean());

        response = TestMultitenancyAPIHelper.disassociateUserFromTenant(new TenantIdentifier(null, "a1", "t2"), userId, process.getProcess());
        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        assertFalse(response.get("wasAssociated").getAsBoolean());

        response = TestMultitenancyAPIHelper.associateUserToTenant(new TenantIdentifier(null, "a1", "t2"), userId, process.getProcess());
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
                    || name.equals(JWTRecipeStorage.class.getName()) || name.equals(ActiveUsersStorage.class.getName())) {
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

            JsonObject response = TestMultitenancyAPIHelper.disassociateUserFromTenant(t2, userId, process.getProcess());
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
        JsonObject user = TestMultitenancyAPIHelper.epSignUp(new TenantIdentifier(null, "a1", "t1"), "user@example.com", "password", process.getProcess());
        String userId = user.get("id").getAsString();

        JsonObject response = TestMultitenancyAPIHelper.disassociateUserFromTenant(new TenantIdentifier(null, "a1", "t1"), userId, process.getProcess());
        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        assertTrue(response.get("wasAssociated").getAsBoolean());

        response = TestMultitenancyAPIHelper.associateUserToTenant(new TenantIdentifier(null, "a1", "t2"), userId, process.getProcess());
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
        JsonObject user = TestMultitenancyAPIHelper.epSignUp(new TenantIdentifier(null, "a1", "t1"), "user@example.com", "password", process.getProcess());
        String userId = user.get("id").getAsString();

        JsonObject response = TestMultitenancyAPIHelper.associateUserToTenant(new TenantIdentifier(null, "a1", null), userId, process.getProcess());
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

        TenantIdentifierWithStorage t1WithStorage = t1.withStorage(StorageLayer.getStorage(t1, process.getProcess()));
        TenantIdentifierWithStorage t2WithStorage = t2.withStorage(StorageLayer.getStorage(t2, process.getProcess()));

        UserInfo user = EmailPassword.signUp(t1WithStorage,
                process.getProcess(), "user@example.com", "password");
        assertArrayEquals(new String[]{"t1"}, user.tenantIds);

        Multitenancy.addUserIdToTenant(process.getProcess(), t2WithStorage, user.id);
        user = EmailPassword.getUserUsingId(t1WithStorage.toAppIdentifierWithStorage(), user.id);
        Utils.assertArrayEqualsIgnoreOrder(new String[]{"t1", "t2"}, user.tenantIds);


        user = EmailPassword.getUserUsingEmail(t1WithStorage, user.email);
        Utils.assertArrayEqualsIgnoreOrder(new String[]{"t1", "t2"}, user.tenantIds);

        Multitenancy.removeUserIdFromTenant(process.getProcess(), t1WithStorage, user.id);
        user = EmailPassword.getUserUsingId(t1WithStorage.toAppIdentifierWithStorage(), user.id);
        assertArrayEquals(new String[]{"t2"}, user.tenantIds);
    }

    @Test
    public void testPasswordlessUsersHaveTenantIds1() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        createTenants();

        TenantIdentifier t1 = new TenantIdentifier(null, "a1", "t1");
        TenantIdentifier t2 = new TenantIdentifier(null, "a1", "t2");

        TenantIdentifierWithStorage t1WithStorage = t1.withStorage(StorageLayer.getStorage(t1, process.getProcess()));
        TenantIdentifierWithStorage t2WithStorage = t2.withStorage(StorageLayer.getStorage(t2, process.getProcess()));

        Passwordless.CreateCodeResponse createCodeResponse = Passwordless.createCode(t1WithStorage,
                process.getProcess(), "user@example.com", null, null, null);
        Passwordless.ConsumeCodeResponse consumeCodeResponse = Passwordless.consumeCode(t1WithStorage, process.getProcess(),
                createCodeResponse.deviceId, createCodeResponse.deviceIdHash, createCodeResponse.userInputCode, null);
        assertArrayEquals(new String[]{"t1"}, consumeCodeResponse.user.tenantIds);

        io.supertokens.pluginInterface.passwordless.UserInfo user;
        Multitenancy.addUserIdToTenant(process.getProcess(), t2WithStorage, consumeCodeResponse.user.id);
        user = Passwordless.getUserById(t1WithStorage.toAppIdentifierWithStorage(), consumeCodeResponse.user.id);
        Utils.assertArrayEqualsIgnoreOrder(new String[]{"t1", "t2"}, user.tenantIds);

        user = Passwordless.getUserByEmail(t1WithStorage, consumeCodeResponse.user.email);
        Utils.assertArrayEqualsIgnoreOrder(new String[]{"t1", "t2"}, user.tenantIds);

        Multitenancy.removeUserIdFromTenant(process.getProcess(), t1WithStorage, consumeCodeResponse.user.id);
        user = Passwordless.getUserById(t1WithStorage.toAppIdentifierWithStorage(), consumeCodeResponse.user.id);
        assertArrayEquals(new String[]{"t2"}, user.tenantIds);
    }

    @Test
    public void testPasswordlessUsersHaveTenantIds2() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        createTenants();

        TenantIdentifier t1 = new TenantIdentifier(null, "a1", "t1");
        TenantIdentifier t2 = new TenantIdentifier(null, "a1", "t2");

        TenantIdentifierWithStorage t1WithStorage = t1.withStorage(StorageLayer.getStorage(t1, process.getProcess()));
        TenantIdentifierWithStorage t2WithStorage = t2.withStorage(StorageLayer.getStorage(t2, process.getProcess()));

        Passwordless.CreateCodeResponse createCodeResponse = Passwordless.createCode(t1WithStorage,
                process.getProcess(), null, "+919876543210", null, null);
        Passwordless.ConsumeCodeResponse consumeCodeResponse = Passwordless.consumeCode(t1WithStorage, process.getProcess(),
                createCodeResponse.deviceId, createCodeResponse.deviceIdHash, createCodeResponse.userInputCode, null);
        assertArrayEquals(new String[]{"t1"}, consumeCodeResponse.user.tenantIds);

        io.supertokens.pluginInterface.passwordless.UserInfo user;
        Multitenancy.addUserIdToTenant(process.getProcess(), t2WithStorage, consumeCodeResponse.user.id);
        user = Passwordless.getUserById(t1WithStorage.toAppIdentifierWithStorage(), consumeCodeResponse.user.id);
        Utils.assertArrayEqualsIgnoreOrder(new String[]{"t1", "t2"}, user.tenantIds);

        user = Passwordless.getUserByPhoneNumber(t1WithStorage, consumeCodeResponse.user.phoneNumber);
        Utils.assertArrayEqualsIgnoreOrder(new String[]{"t1", "t2"}, user.tenantIds);

        Multitenancy.removeUserIdFromTenant(process.getProcess(), t1WithStorage, consumeCodeResponse.user.id);
        user = Passwordless.getUserById(t1WithStorage.toAppIdentifierWithStorage(), consumeCodeResponse.user.id);
        assertArrayEquals(new String[]{"t2"}, user.tenantIds);
    }

    @Test
    public void testThirdPartyUsersHaveTenantIds() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        createTenants();

        TenantIdentifier t1 = new TenantIdentifier(null, "a1", "t1");
        TenantIdentifier t2 = new TenantIdentifier(null, "a1", "t2");

        TenantIdentifierWithStorage t1WithStorage = t1.withStorage(StorageLayer.getStorage(t1, process.getProcess()));
        TenantIdentifierWithStorage t2WithStorage = t2.withStorage(StorageLayer.getStorage(t2, process.getProcess()));

        ThirdParty.SignInUpResponse signInUpResponse = ThirdParty.signInUp(t1WithStorage, process.getProcess(), "google",
                "googleid", "user@example.com");
        assertArrayEquals(new String[]{"t1"}, signInUpResponse.user.tenantIds);

        Multitenancy.addUserIdToTenant(process.getProcess(), t2WithStorage, signInUpResponse.user.id);
        io.supertokens.pluginInterface.thirdparty.UserInfo user = ThirdParty.getUser(
                t1WithStorage.toAppIdentifierWithStorage(), signInUpResponse.user.id);
        Utils.assertArrayEqualsIgnoreOrder(new String[]{"t1", "t2"}, user.tenantIds);

        user = ThirdParty.getUsersByEmail(t1WithStorage, signInUpResponse.user.email)[0];
        Utils.assertArrayEqualsIgnoreOrder(new String[]{"t1", "t2"}, user.tenantIds);

        user = ThirdParty.getUser(t1WithStorage, "google", "googleid");
        Utils.assertArrayEqualsIgnoreOrder(new String[]{"t1", "t2"}, user.tenantIds);

        user = ThirdParty.getUser(t2WithStorage, "google", "googleid");
        Utils.assertArrayEqualsIgnoreOrder(new String[]{"t1", "t2"}, user.tenantIds);

        Multitenancy.removeUserIdFromTenant(process.getProcess(), t1WithStorage, signInUpResponse.user.id);
        user = ThirdParty.getUser(t1WithStorage.toAppIdentifierWithStorage(), signInUpResponse.user.id);
        assertArrayEquals(new String[]{"t2"}, user.tenantIds);
    }

    @Test
    public void testThatDisassociateUserFromWrongTenantDoesNotWork() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        createTenants();
        JsonObject user = TestMultitenancyAPIHelper.epSignUp(new TenantIdentifier(null, "a1", "t1"), "user@example.com", "password", process.getProcess());
        String userId = user.get("id").getAsString();

        JsonObject response = TestMultitenancyAPIHelper.disassociateUserFromTenant(new TenantIdentifier(null, "a1", "t2"), userId, process.getProcess());
        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        assertFalse(response.get("wasAssociated").getAsBoolean());
    }

    @Test
    public void testThatDisassociateUserWithUseridMappingFromWrongTenantDoesNotWork() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        createTenants();
        JsonObject user = TestMultitenancyAPIHelper.epSignUp(new TenantIdentifier(null, "a1", "t1"), "user@example.com", "password", process.getProcess());
        String userId = user.get("id").getAsString();

        TestMultitenancyAPIHelper.createUserIdMapping(new TenantIdentifier(null, "a1", "t1"), userId, "externalid", process.getProcess());

        JsonObject response = TestMultitenancyAPIHelper.disassociateUserFromTenant(new TenantIdentifier(null, "a1", "t2"), "externalid", process.getProcess());
        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        assertFalse(response.get("wasAssociated").getAsBoolean());
    }

    @Test
    public void testAssociateAndDisassociateWithUseridMapping() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        createTenants();
        JsonObject user = TestMultitenancyAPIHelper.epSignUp(new TenantIdentifier(null, "a1", "t1"), "user@example.com", "password", process.getProcess());
        String userId = user.get("id").getAsString();

        TestMultitenancyAPIHelper.createUserIdMapping(new TenantIdentifier(null, "a1", "t1"), userId, "externalid", process.getProcess());

        JsonObject response = TestMultitenancyAPIHelper.associateUserToTenant(new TenantIdentifier(null, "a1", "t2"), "externalid", process.getProcess());
        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        assertFalse(response.get("wasAlreadyAssociated").getAsBoolean());

        response = TestMultitenancyAPIHelper.disassociateUserFromTenant(new TenantIdentifier(null, "a1", "t2"), "externalid", process.getProcess());
        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        assertTrue(response.get("wasAssociated").getAsBoolean());

    }
}
