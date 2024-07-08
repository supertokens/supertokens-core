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

package io.supertokens.test.multitenant;

import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.authRecipe.AuthRecipe;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.pluginInterface.ActiveUsersStorage;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.multitenancy.*;
import io.supertokens.pluginInterface.nonAuthRecipe.NonAuthRecipeStorage;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.useridmapping.UserIdMapping;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.reflections.Reflections;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class AppTenantUserTest {
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
    public void testDeletingAppDeleteNonAuthRecipeData() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.MULTI_TENANCY, EE_FEATURES.MFA});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // this list contains the package names for recipes which dont use UserIdMapping
        ArrayList<String> classesToSkip = new ArrayList<>(
                List.of("io.supertokens.pluginInterface.jwt.JWTRecipeStorage", ActiveUsersStorage.class.getName()));

        Reflections reflections = new Reflections("io.supertokens.pluginInterface");
        Set<Class<? extends NonAuthRecipeStorage>> classes = reflections.getSubTypesOf(NonAuthRecipeStorage.class);
        List<String> names = classes.stream().map(Class::getCanonicalName).collect(Collectors.toList());
        List<String> classNames = new ArrayList<>();
        for (String name : names) {
            if (!name.contains("SQLStorage")) {
                classNames.add(name);
            }
        }

        TenantIdentifier app = new TenantIdentifier(null, "a1", null);
        TenantIdentifier tenant = new TenantIdentifier(null, "a1", "t1");


        for (TenantIdentifier t : new TenantIdentifier[]{app, tenant}) {

            for (String className : classNames) {
                if (classesToSkip.contains(className)) {
                    continue;
                }

                // Create tenants
                Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                        app,
                        new EmailPasswordConfig(true),
                        new ThirdPartyConfig(true, null),
                        new PasswordlessConfig(true),
                        null, null, new JsonObject()
                ), false);

                Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                        tenant,
                        new EmailPasswordConfig(true),
                        new ThirdPartyConfig(true, null),
                        new PasswordlessConfig(true),
                        null, null, new JsonObject()
                ), false);

                Storage tStorage = (
                        StorageLayer.getStorage(t, process.getProcess()));


                AuthRecipeUserInfo user = EmailPassword.signUp(t, tStorage, process.getProcess(), "test@example.com",
                        "password");
                String userId = user.getSupertokensUserId();

                // create entry in nonAuth table
                StorageLayer.getStorage(process.main).addInfoToNonAuthRecipesBasedOnUserId(app, className, userId);

                try {
                    UserIdMapping.findNonAuthStoragesWhereUserIdIsUsedOrAssertIfUsed(
                            t.toAppIdentifier(), tStorage, userId, true);
                    fail(className);
                } catch (Exception ignored) {
                    assertTrue(ignored.getMessage().contains("UserId is already in use"));
                }

                // Delete app/tenant
                Multitenancy.deleteTenant(tenant, process.getProcess());
                Multitenancy.deleteApp(app.toAppIdentifier(), process.getProcess());

                // Create tenants again
                Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                        app,
                        new EmailPasswordConfig(true),
                        new ThirdPartyConfig(true, null),
                        new PasswordlessConfig(true),
                        null, null, new JsonObject()
                ), false);

                Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                        tenant,
                        new EmailPasswordConfig(true),
                        new ThirdPartyConfig(true, null),
                        new PasswordlessConfig(true),
                        null, null, new JsonObject()
                ), false);

                UserIdMapping.findNonAuthStoragesWhereUserIdIsUsedOrAssertIfUsed(t.toAppIdentifier(), tStorage,
                        userId, true);
            }
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testDisassociationOfUserDeletesNonAuthRecipeData() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.MULTI_TENANCY, EE_FEATURES.MFA});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // this list contains the package names for recipes which dont use UserIdMapping
        ArrayList<String> classesToSkip = new ArrayList<>(
                List.of("io.supertokens.pluginInterface.jwt.JWTRecipeStorage", ActiveUsersStorage.class.getName()));

        Reflections reflections = new Reflections("io.supertokens.pluginInterface");
        Set<Class<? extends NonAuthRecipeStorage>> classes = reflections.getSubTypesOf(NonAuthRecipeStorage.class);
        List<String> names = classes.stream().map(Class::getCanonicalName).collect(Collectors.toList());
        List<String> classNames = new ArrayList<>();
        for (String name : names) {
            if (!name.contains("SQLStorage")) {
                classNames.add(name);
            }
        }

        TenantIdentifier app = new TenantIdentifier(null, "a1", null);
        TenantIdentifier tenant = new TenantIdentifier(null, "a1", "t1");

        // Create tenants
        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                app,
                new EmailPasswordConfig(true),
                new ThirdPartyConfig(true, null),
                new PasswordlessConfig(true),
                null, null, new JsonObject()
        ), false);

        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                tenant,
                new EmailPasswordConfig(true),
                new ThirdPartyConfig(true, null),
                new PasswordlessConfig(true),
                null, null, new JsonObject()
        ), false);

        Storage appStorage = (
                StorageLayer.getStorage(app, process.getProcess()));
        Storage tenantStorage = (
                StorageLayer.getStorage(tenant, process.getProcess()));

        for (String className : classNames) {
            if (classesToSkip.contains(className)) {
                continue;
            }

            AuthRecipeUserInfo user = EmailPassword.signUp(app, appStorage, process.getProcess(), "test@example.com",
                    "password");
            String userId = user.getSupertokensUserId();

            Multitenancy.addUserIdToTenant(process.getProcess(), tenant, tenantStorage, userId);

            // create entry in nonAuth table
            tenantStorage.addInfoToNonAuthRecipesBasedOnUserId(tenant, className, userId);

            try {
                UserIdMapping.findNonAuthStoragesWhereUserIdIsUsedOrAssertIfUsed(
                        tenant.toAppIdentifier(), tenantStorage, userId, true);
                fail(className);
            } catch (Exception ignored) {
                assertTrue(ignored.getMessage().contains("UserId is already in use"));
            }

            // Disassociate user
            Multitenancy.removeUserIdFromTenant(process.getProcess(), tenant, tenantStorage, userId, null);

            assertFalse(AuthRecipe.deleteNonAuthRecipeUser(tenant, tenantStorage,
                    userId)); // Nothing deleted indicates that the non auth recipe user data was deleted already

            AuthRecipe.deleteUser(app.toAppIdentifier(), appStorage, userId);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void deletingTenantKeepsTheUserInTheApp() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.MULTI_TENANCY, EE_FEATURES.MFA});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        TenantIdentifier app = new TenantIdentifier(null, "a1", null);
        TenantIdentifier tenant = new TenantIdentifier(null, "a1", "t1");

        // Create tenants
        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                app,
                new EmailPasswordConfig(true),
                new ThirdPartyConfig(true, null),
                new PasswordlessConfig(true),
                null, null, new JsonObject()
        ), false);

        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                tenant,
                new EmailPasswordConfig(true),
                new ThirdPartyConfig(true, null),
                new PasswordlessConfig(true),
                null, null, new JsonObject()
        ), false);

        Storage appStorage = (
                StorageLayer.getStorage(app, process.getProcess()));
        Storage tenantStorage = (
                StorageLayer.getStorage(tenant, process.getProcess()));

        AuthRecipeUserInfo user = EmailPassword.signUp(tenant, tenantStorage, process.getProcess(), "test@example.com",
                "password");
        String userId = user.getSupertokensUserId();

        Multitenancy.deleteTenant(tenant, process.getProcess());

        Multitenancy.addUserIdToTenant(process.getProcess(), app, appStorage,
                userId); // user id must be intact to do this

        AuthRecipeUserInfo appUser = EmailPassword.getUserUsingId(app.toAppIdentifier(), appStorage, userId);

        assertNotNull(appUser);
        assertEquals(userId, appUser.getSupertokensUserId());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
