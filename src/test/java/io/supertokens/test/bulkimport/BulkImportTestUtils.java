/*
 *    Copyright (c) 2024, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.test.bulkimport;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.supertokens.Main;
import io.supertokens.emailpassword.PasswordHashing;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.multitenancy.exception.CannotModifyBaseConfigException;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.bulkimport.BulkImportUser;
import io.supertokens.pluginInterface.bulkimport.BulkImportUser.LoginMethod;
import io.supertokens.pluginInterface.bulkimport.BulkImportUser.TotpDevice;
import io.supertokens.pluginInterface.bulkimport.BulkImportUser.UserRole;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.*;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.totp.TOTPDevice;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.thirdparty.InvalidProviderConfigException;
import io.supertokens.totp.Totp;
import io.supertokens.usermetadata.UserMetadata;
import io.supertokens.userroles.UserRoles;
import io.supertokens.utils.Utils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.*;

public class BulkImportTestUtils {

    public static List<BulkImportUser> generateBulkImportUser(int numberOfUsers) {
        return generateBulkImportUser(numberOfUsers, List.of("public", "t1"), 0);
    }

    public static List<BulkImportUser> generateBulkImportUser(int numberOfUsers, List<String> tenants, int startIndex) {
        return generateBulkImportUserWithRoles(numberOfUsers, tenants, startIndex, List.of("role1", "role2"));
    }

    public static List<BulkImportUser> generateBulkImportUserWithRoles(int numberOfUsers, List<String> tenants, int startIndex, List<String> roles) {
        List<BulkImportUser> users = new ArrayList<>();
        JsonParser parser = new JsonParser();

        for (int i = startIndex; i < numberOfUsers + startIndex; i++) {
            String email = "user" + i + "@example.com";
            String externalId = io.supertokens.utils.Utils.getUUID() + "-" + i;
            String id = "somebogus" + Utils.getUUID();
            JsonObject userMetadata = parser.parse("{\"key1\":\""+id+"\",\"key2\":{\"key3\":\"value3\"}}")
                    .getAsJsonObject();

            List<UserRole> userRoles = new ArrayList<>();
            for(String roleName : roles) {
                userRoles.add(new UserRole(roleName, tenants));
            }

            List<TotpDevice> totpDevices = new ArrayList<>();
            totpDevices.add(new TotpDevice("secretKey", 30, 1, "deviceName"));

            List<LoginMethod> loginMethods = new ArrayList<>();
            long currentTimeMillis = System.currentTimeMillis();
            Random random = new Random();
            loginMethods.add(new LoginMethod(tenants, "emailpassword", random.nextBoolean(), true, currentTimeMillis, email, "$2a",
                    "BCRYPT", null, null, null, null, io.supertokens.utils.Utils.getUUID()));
            loginMethods
                    .add(new LoginMethod(tenants, "thirdparty", random.nextBoolean(), false, currentTimeMillis, email, null, null, null,
                            "thirdPartyId" + i, "thirdPartyUserId" + i, null, io.supertokens.utils.Utils.getUUID()));
            loginMethods.add(
                    new LoginMethod(tenants, "passwordless", random.nextBoolean(), false, currentTimeMillis, email, null, null, null,
                            null, null, null, io.supertokens.utils.Utils.getUUID()));
            id = loginMethods.get(0).superTokensUserId;
            users.add(new BulkImportUser(id, externalId, userMetadata, userRoles, totpDevices, loginMethods));
        }
        return users;
    }

    public static List<BulkImportUser> generateBulkImportUserPlainTextPasswordAndRoles(int numberOfUsers, List<String> tenants, int startIndex, List<String> roles) {
        List<BulkImportUser> users = new ArrayList<>();
        JsonParser parser = new JsonParser();

        for (int i = startIndex; i < numberOfUsers + startIndex; i++) {
            String email = "user" + i + "@example.com";
            String id = io.supertokens.utils.Utils.getUUID();
            String externalId = io.supertokens.utils.Utils.getUUID();

            JsonObject userMetadata = parser.parse("{\"key1\":\""+id+"\",\"key2\":{\"key3\":\"value3\"}}")
                    .getAsJsonObject();

            List<UserRole> userRoles = new ArrayList<>();
            for(String roleName : roles) {
                userRoles.add(new UserRole(roleName, tenants));
            }

            List<TotpDevice> totpDevices = new ArrayList<>();
            totpDevices.add(new TotpDevice("secretKey", 30, 1, "deviceName"));

            List<LoginMethod> loginMethods = new ArrayList<>();
            long currentTimeMillis = System.currentTimeMillis();
            loginMethods.add(new LoginMethod(tenants, "emailpassword", true, true, currentTimeMillis, email, null,
                    null, "password"+i, null, null, null, io.supertokens.utils.Utils.getUUID()));
            loginMethods
                    .add(new LoginMethod(tenants, "thirdparty", true, false, currentTimeMillis, email, null, null, null,
                            "thirdPartyId" + i, "thirdPartyUserId" + i, null, io.supertokens.utils.Utils.getUUID()));
            loginMethods.add(
                    new LoginMethod(tenants, "passwordless", true, false, currentTimeMillis, email, null, null, null,
                            null, null, null, io.supertokens.utils.Utils.getUUID()));
            id = loginMethods.get(0).superTokensUserId;
            users.add(new BulkImportUser(id, externalId, userMetadata, userRoles, totpDevices, loginMethods));
        }
        return users;
    }

    public static List<BulkImportUser> generateBulkImportUserWithEmailPasswordAndRoles(int numberOfUsers, List<String> tenants, int startIndex, List<String> roles) {
        List<BulkImportUser> users = new ArrayList<>();
        JsonParser parser = new JsonParser();

        for (int i = startIndex; i < numberOfUsers + startIndex; i++) {
            String email = "user" + i + "@example.com";
            String id = io.supertokens.utils.Utils.getUUID();
            String externalId = io.supertokens.utils.Utils.getUUID();

            JsonObject userMetadata = parser.parse("{\"key1\":\""+id+"\",\"key2\":{\"key3\":\"value3\"}}")
                    .getAsJsonObject();

            List<UserRole> userRoles = new ArrayList<>();
            for(String roleName : roles) {
                userRoles.add(new UserRole(roleName, tenants));
            }

            List<TotpDevice> totpDevices = new ArrayList<>();
            totpDevices.add(new TotpDevice("secretKey", 30, 1, "deviceName"));

            List<LoginMethod> loginMethods = new ArrayList<>();
            long currentTimeMillis = System.currentTimeMillis();
            loginMethods.add(new LoginMethod(tenants, "emailpassword", true, true, currentTimeMillis, email, null,
                    null, "password"+i, null, null, null, io.supertokens.utils.Utils.getUUID()));
            id = loginMethods.get(0).superTokensUserId;
            users.add(new BulkImportUser(id, externalId, userMetadata, userRoles, totpDevices, loginMethods));
        }
        return users;
    }

    public static void createTenants(Main main)
            throws StorageQueryException, TenantOrAppNotFoundException, InvalidProviderConfigException,
            FeatureNotEnabledException, IOException, InvalidConfigException,
            CannotModifyBaseConfigException, BadPermissionException {
        // User pool 1 - (null, null, null), (null, null, t1)
        // User pool 2 - (null, null, t2)

        { // tenant 1
            TenantIdentifier tenantIdentifier = new TenantIdentifier(null, null, "t1");

            Multitenancy.addNewOrUpdateAppOrTenant(
                    main,
                    new TenantIdentifier(null, null, null),
                    new TenantConfig(
                            tenantIdentifier,
                            new EmailPasswordConfig(true),
                            new ThirdPartyConfig(true, null),
                            new PasswordlessConfig(true),
                            null, null, new JsonObject()));
        }
        { // tenant 2
            JsonObject config = new JsonObject();
            TenantIdentifier tenantIdentifier = new TenantIdentifier(null, null, "t2");

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
                            null, null, config));
        }
    }

    public static void createTenantsWithinOneUserPool(Main main)
            throws StorageQueryException, TenantOrAppNotFoundException, InvalidProviderConfigException,
            FeatureNotEnabledException, IOException, InvalidConfigException,
            CannotModifyBaseConfigException, BadPermissionException {
        // User pool 1 - (null, null, null), (null, null, t1), (null, null, t2)

        { // tenant 1
            TenantIdentifier tenantIdentifier = new TenantIdentifier(null, null, "t1");

            Multitenancy.addNewOrUpdateAppOrTenant(
                    main,
                    new TenantIdentifier(null, null, null),
                    new TenantConfig(
                            tenantIdentifier,
                            new EmailPasswordConfig(true),
                            new ThirdPartyConfig(true, null),
                            new PasswordlessConfig(true),
                            null, null, new JsonObject()));
        }
        { // tenant 2
            TenantIdentifier tenantIdentifier = new TenantIdentifier(null, null, "t2");

            Multitenancy.addNewOrUpdateAppOrTenant(
                    main,
                    new TenantIdentifier(null, null, null),
                    new TenantConfig(
                            tenantIdentifier,
                            new EmailPasswordConfig(true),
                            new ThirdPartyConfig(true, null),
                            new PasswordlessConfig(true),
                            null, null, new JsonObject()));
        }
    }

    public static void assertBulkImportUserAndAuthRecipeUserAreEqual(Main main, AppIdentifier appIdentifier,
            TenantIdentifier tenantIdentifier, Storage storage, BulkImportUser bulkImportUser,
            AuthRecipeUserInfo authRecipeUser) throws StorageQueryException, TenantOrAppNotFoundException {
        for (io.supertokens.pluginInterface.authRecipe.LoginMethod lm1 : authRecipeUser.loginMethods) {
            for (LoginMethod lm2 : bulkImportUser.loginMethods) {
                if (lm2.recipeId.equals(lm1.recipeId.toString())) {
                    assertLoginMethodEquals(main, lm1, lm2);
                }
            }
        }
        assertEquals(bulkImportUser.externalUserId, authRecipeUser.getSupertokensOrExternalUserId());
        assertEquals(bulkImportUser.id, authRecipeUser.getSupertokensUserId());
        assertEquals(bulkImportUser.userMetadata,
                UserMetadata.getUserMetadata(appIdentifier, storage, authRecipeUser.getSupertokensOrExternalUserId()));

        String[] createdUserRoles = UserRoles.getRolesForUser(tenantIdentifier, storage,
                authRecipeUser.getSupertokensOrExternalUserId());
        String[] bulkImportUserRoles = bulkImportUser.userRoles.stream().map(r -> r.role).toArray(String[]::new);
        assertArrayEquals(bulkImportUserRoles, createdUserRoles);

        TOTPDevice[] createdTotpDevices = Totp.getDevices(appIdentifier, storage,
                authRecipeUser.getSupertokensOrExternalUserId());
        assertTotpDevicesEquals(createdTotpDevices, bulkImportUser.totpDevices.toArray(new TotpDevice[0]));
    }

    private static void assertLoginMethodEquals(Main main, io.supertokens.pluginInterface.authRecipe.LoginMethod lm1,
            io.supertokens.pluginInterface.bulkimport.BulkImportUser.LoginMethod lm2)
            throws TenantOrAppNotFoundException {
        assertEquals(lm1.getSupertokensUserId(), lm2.superTokensUserId);
        assertEquals(lm1.email, lm2.email);
        assertEquals(lm1.verified, lm2.isVerified);
        assertTrue(lm2.tenantIds.containsAll(lm1.tenantIds) && lm1.tenantIds.containsAll(lm2.tenantIds));

        switch (lm2.recipeId) {
            case "emailpassword":
                // If lm2.passwordHash is null then the user was imported using plainTextPassword
                // We check if the plainTextPassword matches the stored passwordHash
                if (lm2.passwordHash == null) {
                    assertTrue(PasswordHashing.getInstance(main).verifyPasswordWithHash(lm2.plainTextPassword,
                            lm1.passwordHash));
                } else {
                    assertEquals(lm1.passwordHash, lm2.passwordHash);
                }
                break;
            case "thirdparty":
                assertEquals(lm1.thirdParty.id, lm2.thirdPartyId);
                assertEquals(lm1.thirdParty.userId, lm2.thirdPartyUserId);
                break;
            case "passwordless":
                assertEquals(lm1.phoneNumber, lm2.phoneNumber);
                break;
            default:
                break;
        }
    }

    private static void assertTotpDevicesEquals(TOTPDevice[] createdTotpDevices, TotpDevice[] bulkImportTotpDevices) {
        assertEquals(createdTotpDevices.length, bulkImportTotpDevices.length);
        for (int i = 0; i < createdTotpDevices.length; i++) {
            assertEquals(createdTotpDevices[i].deviceName, bulkImportTotpDevices[i].deviceName);
            assertEquals(createdTotpDevices[i].period, bulkImportTotpDevices[i].period);
            assertEquals(createdTotpDevices[i].secretKey, bulkImportTotpDevices[i].secretKey);
            assertEquals(createdTotpDevices[i].skew, bulkImportTotpDevices[i].skew);
        }
    }
}
