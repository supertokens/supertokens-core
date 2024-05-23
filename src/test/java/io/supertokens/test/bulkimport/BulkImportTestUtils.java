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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.supertokens.Main;
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
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.EmailPasswordConfig;
import io.supertokens.pluginInterface.multitenancy.PasswordlessConfig;
import io.supertokens.pluginInterface.multitenancy.TenantConfig;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.ThirdPartyConfig;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.totp.TOTPDevice;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.thirdparty.InvalidProviderConfigException;
import io.supertokens.totp.Totp;
import io.supertokens.usermetadata.UserMetadata;
import io.supertokens.userroles.UserRoles;

public class BulkImportTestUtils {

    public static List<BulkImportUser> generateBulkImportUser(int numberOfUsers) {
        return generateBulkImportUser(numberOfUsers, List.of("public", "t1"), 0);
    }

    public static List<BulkImportUser> generateBulkImportUser(int numberOfUsers, List<String> tenants, int startIndex) {
        List<BulkImportUser> users = new ArrayList<>();
        JsonParser parser = new JsonParser();

        for (int i = startIndex; i < numberOfUsers + startIndex; i++) {
            String email = "user" + i + "@example.com";
            String id = io.supertokens.utils.Utils.getUUID();
            String externalId = io.supertokens.utils.Utils.getUUID();

            JsonObject userMetadata = parser.parse("{\"key1\":\"value1\",\"key2\":{\"key3\":\"value3\"}}")
                    .getAsJsonObject();

            List<UserRole> userRoles = new ArrayList<>();
            userRoles.add(new UserRole("role1", tenants));
            userRoles.add(new UserRole("role2", tenants));

            List<TotpDevice> totpDevices = new ArrayList<>();
            totpDevices.add(new TotpDevice("secretKey", 30, 1, "deviceName"));

            List<LoginMethod> loginMethods = new ArrayList<>();
            long currentTimeMillis = System.currentTimeMillis();
            loginMethods.add(new LoginMethod(tenants, "emailpassword", true, true, currentTimeMillis, email, "$2a",
                    "BCRYPT", null, null, null));
            loginMethods.add(new LoginMethod(tenants, "thirdparty", true, false, currentTimeMillis, email, null, null,
                    "thirdPartyId" + i, "thirdPartyUserId" + i, null));
            loginMethods.add(new LoginMethod(tenants, "passwordless", true, false, currentTimeMillis, email, null, null,
                    null, null, null));
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

    public static void assertBulkImportUserAndAuthRecipeUserAreEqual(AppIdentifier appIdentifier,
            TenantIdentifier tenantIdentifier, Storage storage, BulkImportUser bulkImportUser,
            AuthRecipeUserInfo authRecipeUser) throws StorageQueryException, TenantOrAppNotFoundException {
        for (io.supertokens.pluginInterface.authRecipe.LoginMethod lm1 : authRecipeUser.loginMethods) {
            bulkImportUser.loginMethods.forEach(lm2 -> {
                if (lm2.recipeId.equals(lm1.recipeId.toString())) {
                    assertLoginMethodEquals(lm1, lm2);
                }
            });
        }
        assertEquals(bulkImportUser.externalUserId, authRecipeUser.getSupertokensOrExternalUserId());
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

    private static void assertLoginMethodEquals(io.supertokens.pluginInterface.authRecipe.LoginMethod lm1,
            io.supertokens.pluginInterface.bulkimport.BulkImportUser.LoginMethod lm2) {
        assertEquals(lm1.email, lm2.email);
        assertEquals(lm1.verified, lm2.isVerified);
        assertTrue(lm2.tenantIds.containsAll(lm1.tenantIds) && lm1.tenantIds.containsAll(lm2.tenantIds));

        switch (lm2.recipeId) {
            case "emailpassword":
                assertEquals(lm1.passwordHash, lm2.passwordHash);
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
