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

package io.supertokens.test.bulkimport.apis;

import static io.supertokens.test.bulkimport.BulkImportTestUtils.generateBulkImportUser;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.multitenancy.exception.CannotModifyBaseConfigException;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.bulkimport.BulkImportUser;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.EmailPasswordConfig;
import io.supertokens.pluginInterface.multitenancy.PasswordlessConfig;
import io.supertokens.pluginInterface.multitenancy.TenantConfig;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.ThirdPartyConfig;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.thirdparty.InvalidProviderConfigException;
import io.supertokens.userroles.UserRoles;

public class AddBulkImportUsersTest {
    private String genericErrMsg = "Data has missing or invalid fields. Please check the users field for more details.";

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
    public void shouldThrow400IfUsersAreMissingInRequestBody() throws Exception {
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(new String[] { "../" });
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // CASE 1: users field is not present
        testBadRequest(process.getProcess(), new JsonObject(), "Field name 'users' is invalid in JSON input");

        // CASE 2: users field type in incorrect
        testBadRequest(process.getProcess(), new JsonParser().parse("{\"users\": \"string\"}").getAsJsonObject(),
                "Field name 'users' is invalid in JSON input");

        // CASE 3: users array is empty
        testBadRequest(process.getProcess(), generateUsersJson(0).getAsJsonObject(),
                "{\"error\":\"You need to add at least one user.\"}");

        // CASE 4: users array length is greater than 10000
        testBadRequest(process.getProcess(), generateUsersJson(10001).getAsJsonObject(),
                "{\"error\":\"You can only add 10000 users at a time.\"}");

        process.kill();
        Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void shouldThrow400IfLoginMethodsAreMissingInUserObject() throws Exception {
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(new String[] { "../" });
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // CASE 1: loginMethods field is not present
        testBadRequest(process.getProcess(), new JsonParser().parse("{\"users\":[{}]}").getAsJsonObject(),
                "{\"error\":\"" + genericErrMsg
                        + "\",\"users\":[{\"index\":0,\"errors\":[\"loginMethods is required.\"]}]}");

        // CASE 2: loginMethods field type in incorrect
        testBadRequest(process.getProcess(),
                new JsonParser().parse("{\"users\":[{\"loginMethods\": \"string\"}]}").getAsJsonObject(),
                "{\"error\":\"" + genericErrMsg
                        + "\",\"users\":[{\"index\":0,\"errors\":[\"loginMethods should be of type array of object.\"]}]}");

        // CASE 3: loginMethods array is empty
        testBadRequest(process.getProcess(),
                new JsonParser().parse("{\"users\":[{\"loginMethods\": []}]}").getAsJsonObject(),
                "{\"error\":\"" + genericErrMsg
                        + "\",\"users\":[{\"index\":0,\"errors\":[\"At least one loginMethod is required.\"]}]}");

        process.kill();
        Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void shouldThrow400IfNonRequiredFieldsHaveInvalidType() throws Exception {
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(new String[] { "../" });
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject requestBody = new JsonParser()
                .parse("{\"users\":[{\"externalUserId\":[],\"userMetaData\":[],\"userRoles\":{},\"totpDevices\":{}}]}")
                .getAsJsonObject();

        testBadRequest(process.getProcess(), requestBody,
                "{\"error\":\"" + genericErrMsg
                        + "\",\"users\":[{\"index\":0,\"errors\":[\"externalUserId should be of type string.\",\"userRoles should be of type array of object.\",\"totpDevices should be of type array of object.\",\"loginMethods is required.\"]}]}");

        process.kill();
        Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void shouldThrow400IfNonUniqueExternalIdsArePassed() throws Exception {
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(new String[] { "../" });
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }
        JsonObject requestBody = new JsonParser()
                .parse("{\"users\":[{\"externalUserId\":\"id1\"}, {\"externalUserId\":\"id1\"}]}")
                .getAsJsonObject();

        testBadRequest(process.getProcess(), requestBody, "{\"error\":\"" + genericErrMsg
                + "\",\"users\":[{\"index\":0,\"errors\":[\"loginMethods is required.\"]},{\"index\":1,\"errors\":[\"loginMethods is required.\",\"externalUserId id1 is not unique. It is already used by another user.\"]}]}");

        process.kill();
        Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void shouldThrow400IfTotpDevicesAreNotPassedCorrectly() throws Exception {
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(new String[] { "../" });
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // CASE 1: MFA must be enabled to import totp devices
        JsonObject requestBody = new JsonParser()
                .parse("{\"users\":[{\"totpDevices\":[{\"secret\": \"secret\"}]}]}")
                .getAsJsonObject();

        testBadRequest(process.getProcess(), requestBody, "{\"error\":\"" + genericErrMsg
                + "\",\"users\":[{\"index\":0,\"errors\":[\"MFA must be enabled to import totp devices.\",\"loginMethods is required.\"]}]}");

        // CASE 2: secretKey is required in totpDevices
        setFeatureFlags(process.getProcess(), new EE_FEATURES[] { EE_FEATURES.MFA });
        testBadRequest(process.getProcess(), requestBody, "{\"error\":\"" + genericErrMsg
                + "\",\"users\":[{\"index\":0,\"errors\":[\"secretKey is required for a totp device.\",\"loginMethods is required.\"]}]}");

        process.kill();
        Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void shouldThrow400IfUserRolesAreNotPassedCorrectly() throws Exception {
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(new String[] { "../" });
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // Create user roles
        {
            UserRoles.createNewRoleOrModifyItsPermissions(process.getProcess(), "role1", null);
        }

        // CASE 1: tenantIds is required for a user role
        JsonObject requestBody = new JsonParser()
                .parse("{\"users\":[{\"userRoles\":[{\"role\":\"role1\"}]}]}")
                .getAsJsonObject();

        testBadRequest(process.getProcess(), requestBody, "{\"error\":\"" + genericErrMsg
                + "\",\"users\":[{\"index\":0,\"errors\":[\"tenantIds is required for a user role.\",\"loginMethods is required.\"]}]}");

        // CASE 2: Role doesn't exist
        JsonObject requestBody2 = new JsonParser()
                .parse("{\"users\":[{\"userRoles\":[{\"role\":\"role5\", \"tenantIds\": [\"public\"]}]}]}")
                .getAsJsonObject();

        testBadRequest(process.getProcess(), requestBody2, "{\"error\":\"" + genericErrMsg
                + "\",\"users\":[{\"index\":0,\"errors\":[\"Role role5 does not exist.\",\"loginMethods is required.\"]}]}");

        process.kill();
        Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void shouldThrow400IfLoginMethodsHaveInvalidFieldType() throws Exception {
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(new String[] { "../" });
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // CASE 1: Field type is invalid
        JsonObject requestBody = new JsonParser()
                .parse(
                        "{\"users\":[{\"loginMethods\":[{\"recipeId\":[],\"tenantIds\":{},\"isPrimary\":[],\"isVerified\":[],\"timeJoinedInMSSinceEpoch\":[]}]}]}")
                .getAsJsonObject();

        testBadRequest(process.getProcess(), requestBody, "{\"error\":\"" + genericErrMsg
                + "\",\"users\":[{\"index\":0,\"errors\":[\"recipeId should be of type string for a loginMethod.\",\"tenantIds should be of type array of string for a loginMethod.\",\"isVerified should be of type boolean for a loginMethod.\",\"isPrimary should be of type boolean for a loginMethod.\",\"timeJoinedInMSSinceEpoch should be of type integer for a loginMethod\"]}]}");

        // CASE 2: recipeId is invalid
        JsonObject requestBody2 = new JsonParser()
                .parse("{\"users\":[{\"loginMethods\":[{\"recipeId\":\"invalid_recipe_id\"}]}]}")
                .getAsJsonObject();

        testBadRequest(process.getProcess(), requestBody2, "{\"error\":\"" + genericErrMsg
                + "\",\"users\":[{\"index\":0,\"errors\":[\"Invalid recipeId for loginMethod. Pass one of emailpassword, thirdparty or, passwordless!\"]}]}");

        process.kill();
        Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void shouldThrow400IfEmailPasswordRecipeHasInvalidFieldTypes() throws Exception {
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(new String[] { "../" });
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // CASE 1: email, passwordHash and hashingAlgorithm are not present
        JsonObject requestBody = new JsonParser()
                .parse("{\"users\":[{\"loginMethods\":[{\"recipeId\":\"emailpassword\"}]}]}")
                .getAsJsonObject();

        testBadRequest(process.getProcess(), requestBody, "{\"error\":\"" + genericErrMsg
                + "\",\"users\":[{\"index\":0,\"errors\":[\"email is required for an emailpassword recipe.\",\"passwordHash is required for an emailpassword recipe.\",\"hashingAlgorithm is required for an emailpassword recipe.\"]}]}");

        // CASE 2: email, passwordHash and hashingAlgorithm field type is incorrect
        JsonObject requestBody2 = new JsonParser()
                .parse(
                        "{\"users\":[{\"loginMethods\":[{\"recipeId\":\"emailpassword\",\"email\":[],\"passwordHash\":[],\"hashingAlgorithm\":[]}]}]}")
                .getAsJsonObject();

        testBadRequest(process.getProcess(), requestBody2, "{\"error\":\"" + genericErrMsg
                + "\",\"users\":[{\"index\":0,\"errors\":[\"email should be of type string for an emailpassword recipe.\",\"passwordHash should be of type string for an emailpassword recipe.\",\"hashingAlgorithm should be of type string for an emailpassword recipe.\"]}]}");

        // CASE 3: hashingAlgorithm is not one of bcrypt, argon2, firebase_scrypt
        JsonObject requestBody3 = new JsonParser()
                .parse(
                        "{\"users\":[{\"loginMethods\":[{\"recipeId\":\"emailpassword\",\"email\":\"johndoe@gmail.com\",\"passwordHash\":\"$2a\",\"hashingAlgorithm\":\"invalid_algorithm\"}]}]}")
                .getAsJsonObject();

        testBadRequest(process.getProcess(), requestBody3, "{\"error\":\"" + genericErrMsg
                + "\",\"users\":[{\"index\":0,\"errors\":[\"Invalid hashingAlgorithm for emailpassword recipe. Pass one of bcrypt, argon2 or, firebase_scrypt!\"]}]}");

        process.kill();
        Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void shouldThrow400IfThirdPartyRecipeHasInvalidFieldTypes() throws Exception {
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(new String[] { "../" });
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // CASE 1: email, thirdPartyId and thirdPartyUserId are not present
        JsonObject requestBody = new JsonParser()
                .parse("{\"users\":[{\"loginMethods\":[{\"recipeId\":\"thirdparty\"}]}]}")
                .getAsJsonObject();

        testBadRequest(process.getProcess(), requestBody, "{\"error\":\"" + genericErrMsg
                + "\",\"users\":[{\"index\":0,\"errors\":[\"email is required for a thirdparty recipe.\",\"thirdPartyId is required for a thirdparty recipe.\",\"thirdPartyUserId is required for a thirdparty recipe.\"]}]}");

        // CASE 2: email, passwordHash and thirdPartyUserId field type is incorrect
        JsonObject requestBody2 = new JsonParser()
                .parse(
                        "{\"users\":[{\"loginMethods\":[{\"recipeId\":\"thirdparty\",\"email\":[],\"thirdPartyId\":[],\"thirdPartyUserId\":[]}]}]}")
                .getAsJsonObject();

        testBadRequest(process.getProcess(), requestBody2, "{\"error\":\"" + genericErrMsg
                + "\",\"users\":[{\"index\":0,\"errors\":[\"email should be of type string for a thirdparty recipe.\",\"thirdPartyId should be of type string for a thirdparty recipe.\",\"thirdPartyUserId should be of type string for a thirdparty recipe.\"]}]}");

        process.kill();
        Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void shouldThrow400IfPasswordlessRecipeHasInvalidFieldTypes() throws Exception {
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(new String[] { "../" });
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // CASE 1: email and phoneNumber are not present
        JsonObject requestBody = new JsonParser()
                .parse("{\"users\":[{\"loginMethods\":[{\"recipeId\":\"passwordless\"}]}]}")
                .getAsJsonObject();

        testBadRequest(process.getProcess(), requestBody, "{\"error\":\"" + genericErrMsg
                + "\",\"users\":[{\"index\":0,\"errors\":[\"Either email or phoneNumber is required for a passwordless recipe.\"]}]}");

        // CASE 2: email and phoneNumber field type is incorrect
        JsonObject requestBody2 = new JsonParser()
                .parse(
                        "{\"users\":[{\"loginMethods\":[{\"recipeId\":\"passwordless\",\"email\":[],\"phoneNumber\":[]}]}]}")
                .getAsJsonObject();

        testBadRequest(process.getProcess(), requestBody2, "{\"error\":\"" + genericErrMsg
                + "\",\"users\":[{\"index\":0,\"errors\":[\"email should be of type string for a passwordless recipe.\",\"phoneNumber should be of type string for a passwordless recipe.\",\"Either email or phoneNumber is required for a passwordless recipe.\"]}]}");

        process.kill();
        Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void shouldThrow400IfAUserHasMultipleLoginMethodsAndAccountLinkingIsDisabled() throws Exception {
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(new String[] { "../" });
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }
        JsonObject requestBody = new JsonParser()
                .parse("{\"users\":[{\"loginMethods\":[{\"recipeId\":\"emailpassword\",\"email\":\"johndoe@gmail.com\",\"passwordHash\":\"$2a\",\"hashingAlgorithm\":\"bcrypt\",\"isPrimary\":true},{\"recipeId\":\"passwordless\",\"email\":\"johndoe@gmail.com\"}]}]}")
                .getAsJsonObject();

        testBadRequest(process.getProcess(), requestBody, "{\"error\":\"" + genericErrMsg
                + "\",\"users\":[{\"index\":0,\"errors\":[\"Account linking must be enabled to import multiple loginMethods.\"]}]}");

        process.kill();
        Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void shouldThrow400IfInvalidTenantIdIsPassed() throws Exception {
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(new String[] { "../" });
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // CASE 1: Multitenancy is not enabled
        JsonObject requestBody = new JsonParser()
                .parse(
                        "{\"users\":[{\"loginMethods\":[{\"tenantIds\":[\"invalid\"],\"recipeId\":\"passwordless\",\"email\":\"johndoe@gmail.com\"}]}]}")
                .getAsJsonObject();

        testBadRequest(process.getProcess(), requestBody, "{\"error\":\"" + genericErrMsg
                + "\",\"users\":[{\"index\":0,\"errors\":[\"Multitenancy must be enabled before importing users to a different tenant.\"]}]}");

        // CASE 2: Invalid tenantId
        setFeatureFlags(process.getProcess(),
                new EE_FEATURES[] { EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY });

        JsonObject requestBody2 = new JsonParser()
                .parse(
                        "{\"users\":[{\"loginMethods\":[{\"tenantIds\":[\"invalid\"],\"recipeId\":\"passwordless\",\"email\":\"johndoe@gmail.com\"}]}]}")
                .getAsJsonObject();

        testBadRequest(process.getProcess(), requestBody2, "{\"error\":\"" + genericErrMsg
                + "\",\"users\":[{\"index\":0,\"errors\":[\"Invalid tenantId: invalid for passwordless recipe.\"]}]}");

        // CASE 3: Two or more tenants do not share the same storage

        createTenants(process.getProcess());

        JsonObject requestBody3 = new JsonParser().parse(
                "{\"users\":[{\"loginMethods\":[{\"tenantIds\":[\"public\"],\"recipeId\":\"passwordless\",\"email\":\"johndoe@gmail.com\"}, {\"tenantIds\":[\"t2\"],\"recipeId\":\"thirdparty\", \"email\":\"johndoe@gmail.com\", \"thirdPartyId\":\"id\", \"thirdPartyUserId\":\"id\"}]}]}")
                .getAsJsonObject();

        testBadRequest(process.getProcess(), requestBody3, "{\"error\":\"" + genericErrMsg
                + "\",\"users\":[{\"index\":0,\"errors\":[\"All tenants for a user must share the same storage for thirdparty recipe.\"]}]}");

        process.kill();
        Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void shouldThrow400IfTwoLoginMethodsHaveIsPrimaryTrue() throws Exception {
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(new String[] { "../" });
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        setFeatureFlags(process.getProcess(),
                new EE_FEATURES[] { EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY });

        JsonObject requestBody = new JsonParser()
                .parse("{\"users\":[{\"loginMethods\":[{\"recipeId\":\"emailpassword\",\"email\":\"johndoe@gmail.com\",\"passwordHash\":\"$2a\",\"hashingAlgorithm\":\"bcrypt\",\"isPrimary\":true},{\"recipeId\":\"passwordless\",\"email\":\"johndoe@gmail.com\",\"isPrimary\":true}]}]}")
                .getAsJsonObject();

        testBadRequest(process.getProcess(), requestBody, "{\"error\":\"" + genericErrMsg
                + "\",\"users\":[{\"index\":0,\"errors\":[\"No two loginMethods can have isPrimary as true.\"]}]}");

        process.kill();
        Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void shouldReturn200Response() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        setFeatureFlags(process.getProcess(), new EE_FEATURES[] { EE_FEATURES.MFA });

        // Create user roles before inserting bulk users
        {
            UserRoles.createNewRoleOrModifyItsPermissions(process.getProcess(), "role1", null);
            UserRoles.createNewRoleOrModifyItsPermissions(process.getProcess(), "role2", null);
        }

        JsonObject request = generateUsersJson(10000);
        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/bulk-import/users",
                request, 1000, 10000, null, Utils.getCdiVersionStringLatestForTests(), null);
        assertEquals("OK", response.get("status").getAsString());

        process.kill();
        Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void shouldNormaliseFields() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        setFeatureFlags(process.getProcess(), new EE_FEATURES[] { EE_FEATURES.MFA });

        // Create user roles before inserting bulk users
        {
            UserRoles.createNewRoleOrModifyItsPermissions(process.getProcess(), "role1", null);
            UserRoles.createNewRoleOrModifyItsPermissions(process.getProcess(), "role2", null);
        }

        JsonObject request = generateUsersJson(1);
        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/bulk-import/users",
                request, 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), null);
        assertEquals("OK", response.get("status").getAsString());

        JsonObject getResponse = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/bulk-import/users",
                new HashMap<>(), 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), null);

        assertEquals("OK", getResponse.get("status").getAsString());
        JsonArray bulkImportUsers = getResponse.get("users").getAsJsonArray();
        assertEquals(1, bulkImportUsers.size());

        JsonObject bulkImportUserJson = bulkImportUsers.get(0).getAsJsonObject();

        // Test if default values were set in totpDevices
        JsonArray totpDevices = bulkImportUserJson.getAsJsonArray("totpDevices");
        for (int i = 0; i < totpDevices.size(); i++) {
            JsonObject totpDevice = totpDevices.get(i).getAsJsonObject();
            assertEquals(30, totpDevice.get("period").getAsInt());
            assertEquals(1, totpDevice.get("skew").getAsInt());
        }

        JsonArray loginMethods = bulkImportUserJson.getAsJsonArray("loginMethods");
        for (int i = 0; i < loginMethods.size(); i++) {
            JsonObject loginMethod = loginMethods.get(i).getAsJsonObject();
            if (loginMethod.has("email")) {
                assertEquals("johndoe+0@gmail.com", loginMethod.get("email").getAsString());
            }
            if (loginMethod.has("phoneNumber")) {
                assertEquals("+919999999999", loginMethod.get("phoneNumber").getAsString());
            }
            if (loginMethod.has("hashingAlgorithm")) {
                assertEquals("ARGON2", loginMethod.get("hashingAlgorithm").getAsString());
            }
        }

        process.kill();
        Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void shouldFailIfANewFieldWasAddedToBulkImportUser() throws Exception {
        List<BulkImportUser> bulkImportUsers = generateBulkImportUser(1);
        BulkImportUser user = bulkImportUsers.get(0);

        checkFields(user, "BulkImportUser",
                Arrays.asList("id", "externalUserId", "userMetadata", "userRoles", "totpDevices",
                        "loginMethods", "status", "primaryUserId", "errorMessage", "createdAt",
                        "updatedAt"));

        checkLoginMethodFields(user.loginMethods.get(0), "LoginMethod",
                Arrays.asList("tenantIds", "isVerified", "isPrimary", "timeJoinedInMSSinceEpoch",
                        "recipeId", "email", "passwordHash", "hashingAlgorithm",
                        "phoneNumber", "thirdPartyId", "thirdPartyUserId", "externalUserId", "superTokensUserId"));

        checkTotpDeviceFields(user.totpDevices.get(0), "TotpDevice",
                Arrays.asList("secretKey", "period", "skew", "deviceName"));

        checkUserRoleFields(user.userRoles.get(0), "UserRole",
                Arrays.asList("role", "tenantIds"));
    }

    private void checkFields(Object object, String objectType, List<String> expectedFields) {
        Field[] actualFields = object.getClass().getDeclaredFields();
        List<String> actualFieldNames = Arrays.stream(actualFields)
                .map(Field::getName)
                .collect(Collectors.toList());

        List<String> extraFields = actualFieldNames.stream()
                .filter(fieldName -> !expectedFields.contains(fieldName))
                .collect(Collectors.toList());

        if (!extraFields.isEmpty()) {
            fail("The following extra field(s) are present in " + objectType + ": " + String.join(", ", extraFields));
        }
    }

    private void checkLoginMethodFields(BulkImportUser.LoginMethod loginMethod, String objectType,
            List<String> expectedFields) {
        checkFields(loginMethod, objectType, expectedFields);
    }

    private void checkTotpDeviceFields(BulkImportUser.TotpDevice totpDevice, String objectType,
            List<String> expectedFields) {
        checkFields(totpDevice, objectType, expectedFields);
    }

    private void checkUserRoleFields(BulkImportUser.UserRole userRole, String objectType, List<String> expectedFields) {
        checkFields(userRole, objectType, expectedFields);
    }

    private String getResponseMessageFromError(String response) {
        return response.substring(response.indexOf("Message: ") + "Message: ".length());
    }

    private void testBadRequest(Main main, JsonObject requestBody, String expectedErrorMessage) throws Exception {
        try {
            HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                    "http://localhost:3567/bulk-import/users",
                    requestBody, 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), null);

            fail("The API should have thrown an error");
        } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
            String responseString = getResponseMessageFromError(e.getMessage());
            assertEquals(400, e.statusCode);
            assertEquals(responseString, expectedErrorMessage);
        }
    }

    public static JsonObject generateUsersJson(int numberOfUsers) {
        JsonObject userJsonObject = new JsonObject();
        JsonParser parser = new JsonParser();

        JsonArray usersArray = new JsonArray();
        for (int i = 0; i < numberOfUsers; i++) {
            JsonObject user = new JsonObject();

            user.addProperty("externalUserId", UUID.randomUUID().toString());
            user.add("userMetadata", parser.parse("{\"key1\":\"value1\",\"key2\":{\"key3\":\"value3\"}}"));
            user.add("userRoles", parser.parse(
                    "[{\"role\":\"role1\", \"tenantIds\": [\"public\"]},{\"role\":\"role2\", \"tenantIds\": [\"public\"]}]"));
            user.add("totpDevices", parser.parse("[{\"secretKey\":\"secretKey\",\"deviceName\":\"deviceName\"}]"));

            JsonArray tenanatIds = parser.parse("[\"public\"]").getAsJsonArray();
            String email = " johndoe+" + i + "@gmail.com ";

            JsonArray loginMethodsArray = new JsonArray();
            loginMethodsArray.add(createEmailLoginMethod(email, tenanatIds));
            loginMethodsArray.add(createThirdPartyLoginMethod(email, tenanatIds));
            loginMethodsArray.add(createPasswordlessLoginMethod(email, tenanatIds));
            user.add("loginMethods", loginMethodsArray);

            usersArray.add(user);
        }

        userJsonObject.add("users", usersArray);
        return userJsonObject;
    }

    private static JsonObject createEmailLoginMethod(String email, JsonArray tenantIds) {
        JsonObject loginMethod = new JsonObject();
        loginMethod.add("tenantIds", tenantIds);
        loginMethod.addProperty("email", email);
        loginMethod.addProperty("recipeId", "emailpassword");
        loginMethod.addProperty("passwordHash",
                "$argon2d$v=19$m=12,t=3,p=1$aGI4enNvMmd0Zm0wMDAwMA$r6p7qbr6HD+8CD7sBi4HVw");
        loginMethod.addProperty("hashingAlgorithm", "argon2");
        loginMethod.addProperty("isVerified", true);
        loginMethod.addProperty("isPrimary", true);
        loginMethod.addProperty("timeJoinedInMSSinceEpoch", 0);
        return loginMethod;
    }

    private static JsonObject createThirdPartyLoginMethod(String email, JsonArray tenantIds) {
        JsonObject loginMethod = new JsonObject();
        loginMethod.add("tenantIds", tenantIds);
        loginMethod.addProperty("recipeId", "thirdparty");
        loginMethod.addProperty("email", email);
        loginMethod.addProperty("thirdPartyId", "google");
        loginMethod.addProperty("thirdPartyUserId", "112618388912586834161");
        loginMethod.addProperty("isVerified", true);
        loginMethod.addProperty("isPrimary", false);
        loginMethod.addProperty("timeJoinedInMSSinceEpoch", 0);
        return loginMethod;
    }

    private static JsonObject createPasswordlessLoginMethod(String email, JsonArray tenantIds) {
        JsonObject loginMethod = new JsonObject();
        loginMethod.add("tenantIds", tenantIds);
        loginMethod.addProperty("email", email);
        loginMethod.addProperty("recipeId", "passwordless");
        loginMethod.addProperty("phoneNumber", "+91-9999999999");
        loginMethod.addProperty("isVerified", true);
        loginMethod.addProperty("isPrimary", false);
        loginMethod.addProperty("timeJoinedInMSSinceEpoch", 0);
        return loginMethod;
    }

    private void createTenants(Main main)
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

    private void setFeatureFlags(Main main, EE_FEATURES[] features) {
        FeatureFlagTestContent.getInstance(main).setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, features);
    }
}
