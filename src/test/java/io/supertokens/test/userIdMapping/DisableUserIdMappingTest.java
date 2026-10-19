/*
 *    Copyright (c) 2026, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.test.userIdMapping;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.authRecipe.AuthRecipe;
import io.supertokens.bulkimport.BulkImport;
import io.supertokens.bulkimport.BulkImportUserUtils;
import io.supertokens.bulkimport.exceptions.InvalidBulkImportDataException;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.inmemorydb.Start;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.session.Session;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.StorageUtils;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.bulkimport.BulkImportUser;
import io.supertokens.pluginInterface.bulkimport.exceptions.BulkImportBatchInsertException;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.EmailPasswordConfig;
import io.supertokens.pluginInterface.multitenancy.PasswordlessConfig;
import io.supertokens.pluginInterface.multitenancy.TenantConfig;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.ThirdPartyConfig;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.bulkimport.BulkImportTestUtils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.httpRequest.HttpResponseException;
import io.supertokens.useridmapping.UserIdMapping;
import io.supertokens.useridmapping.UserIdType;
import io.supertokens.usermetadata.UserMetadata;
import jakarta.servlet.ServletException;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import static org.junit.Assert.*;

public class DisableUserIdMappingTest {

    private static final String BASE = "http://localhost:3567";

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
    public void requestsDoNotLookUpUserIdMappingsWhenDisabled() throws Exception {
        Utils.setValueInConfig("disable_user_id_mapping", "true");
        TestingProcessManager.TestingProcess process = startInMemory(true);
        if (process == null) {
            return;
        }

        Map<String, Long> counts = countUserIdMappingCallsPerRequest(process);
        for (Map.Entry<String, Long> entry : counts.entrySet()) {
            assertEquals(entry.getKey(), 0L, (long) entry.getValue());
        }

        stop(process);
    }

    // Shows that the counter sees the lookups the disabled run skips.
    @Test
    public void requestsLookUpUserIdMappingsWhenEnabled() throws Exception {
        TestingProcessManager.TestingProcess process = startInMemory(false);
        if (process == null) {
            return;
        }

        // these make no user id mapping lookup in core even with mapping enabled
        Set<String> noLookup = Set.of("email-password sign-up", "session verify (checkDatabase)", "session remove",
                "user roles add", "user roles get", "webauthn sign-up");
        Map<String, Long> counts = countUserIdMappingCallsPerRequest(process);
        for (Map.Entry<String, Long> entry : counts.entrySet()) {
            assertEquals(entry.getKey(), !noLookup.contains(entry.getKey()), entry.getValue() > 0);
        }

        stop(process);
    }

    @Test
    public void creatingAndUpdatingMappingsIsRejectedWhenDisabled() throws Exception {
        Utils.setValueInConfig("disable_user_id_mapping", "true");
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        String userId = emailPasswordSignUp(process, "user@example.com").get("id").getAsString();

        JsonObject mapping = new JsonObject();
        mapping.addProperty("superTokensUserId", userId);
        mapping.addProperty("externalUserId", "external-id");
        assertBadRequest(() -> post(process, "/recipe/userid/map", mapping, "useridmapping"));

        try {
            UserIdMapping.createUserIdMapping(process.getProcess(), userId, "external-id", null, false);
            fail();
        } catch (ServletException e) {
            assertEquals(UserIdMapping.USER_ID_MAPPING_DISABLED_MESSAGE, e.getRootCause().getMessage());
        }

        JsonObject info = new JsonObject();
        info.addProperty("userId", userId);
        info.addProperty("externalUserIdInfo", "info");
        assertBadRequest(() -> HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                BASE + "/recipe/userid/external-user-id-info", info, 1000, 1000, null,
                Utils.getCdiVersionStringLatestForTests(), "useridmapping"));

        // removal stays allowed
        JsonObject remove = new JsonObject();
        remove.addProperty("userId", userId);
        JsonObject removeResponse = post(process, "/recipe/userid/map/remove", remove, "useridmapping");
        assertEquals("OK", removeResponse.get("status").getAsString());
        assertFalse(removeResponse.get("didMappingExist").getAsBoolean());

        stop(process);
    }

    // A mapping created before the opt-out (the documented config refresh race) is hidden from reads but can
    // still be removed.
    @Test
    public void mappingCreatedBeforeTheOptOutIsHiddenButRemovable() throws Exception {
        Utils.setValueInConfig("disable_user_id_mapping", "true");
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        Main main = process.getProcess();
        AppIdentifier app = process.getAppForTesting().toAppIdentifier();
        AuthRecipeUserInfo user = EmailPassword.signUp(main, "user@example.com", "password123");
        StorageUtils.getUserIdMappingStorage(StorageLayer.getStorage(main))
                .createUserIdMapping(app, user.getSupertokensUserId(), "external-id", null);

        assertNull(UserIdMapping.getUserIdMapping(main, user.getSupertokensUserId(), UserIdType.ANY));
        assertNull(UserIdMapping.getUserIdMapping(main, "external-id", UserIdType.EXTERNAL));

        assertTrue(UserIdMapping.deleteUserIdMapping(main, user.getSupertokensUserId(), UserIdType.SUPERTOKENS,
                false));
        assertFalse(StorageUtils.getUserIdMappingStorage(StorageLayer.getStorage(main)).hasUserIdMappings(app));

        stop(process);
    }

    // A mapping left over from the config refresh window can be removed over the API by its external id, with
    // userIdType EXTERNAL, ANY, or none (the SDK default, which the core treats as ANY).
    @Test
    public void leftoverMappingIsRemovableByItsExternalIdOverTheApi() throws Exception {
        Utils.setValueInConfig("disable_user_id_mapping", "true");
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        Main main = process.getProcess();
        AppIdentifier app = process.getAppForTesting().toAppIdentifier();
        AuthRecipeUserInfo user = EmailPassword.signUp(main, "user@example.com", "password123");

        for (String userIdType : new String[]{"EXTERNAL", "ANY", null}) {
            plantLeftoverMapping(main, app, user.getSupertokensUserId(), "external-id");

            JsonObject remove = new JsonObject();
            remove.addProperty("userId", "external-id");
            if (userIdType != null) {
                remove.addProperty("userIdType", userIdType);
            }
            JsonObject response = postOk(process, "/recipe/userid/map/remove", remove, "useridmapping");
            assertTrue(String.valueOf(userIdType), response.get("didMappingExist").getAsBoolean());
            assertFalse(String.valueOf(userIdType),
                    StorageUtils.getUserIdMappingStorage(StorageLayer.getStorage(main)).hasUserIdMappings(app));
        }

        stop(process);
    }

    // Deleting a user with a leftover mapping also deletes what is stored under the external id: its sessions
    // (so their refresh tokens stop working) and its metadata. Works by either id.
    @Test
    public void deletingAUserRemovesSessionsAndMetadataStoredUnderALeftoverMapping() throws Exception {
        Utils.setValueInConfig("disable_user_id_mapping", "true");
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        Main main = process.getProcess();
        AppIdentifier app = process.getAppForTesting().toAppIdentifier();

        for (boolean deleteByExternalId : new boolean[]{false, true}) {
            String externalId = "external-id-" + deleteByExternalId;
            AuthRecipeUserInfo user = EmailPassword.signUp(main, externalId + "@example.com", "password123");
            plantLeftoverMapping(main, app, user.getSupertokensUserId(), externalId);
            createSession(process, externalId);
            JsonObject metadata = new JsonObject();
            metadata.addProperty("key", "value");
            UserMetadata.updateUserMetadata(main, externalId, metadata);
            assertEquals(1, Session.getAllNonExpiredSessionHandlesForUser(main, externalId).length);

            JsonObject remove = new JsonObject();
            remove.addProperty("userId", deleteByExternalId ? externalId : user.getSupertokensUserId());
            postOk(process, "/user/remove", remove, null);

            assertEquals(0, Session.getAllNonExpiredSessionHandlesForUser(main, externalId).length);
            assertEquals(0, UserMetadata.getUserMetadata(main, externalId).entrySet().size());
            assertFalse(StorageUtils.getUserIdMappingStorage(StorageLayer.getStorage(main)).hasUserIdMappings(app));
        }

        stop(process);
    }

    // Linking a user with a leftover mapping revokes its sessions, which are stored under the external id.
    @Test
    public void linkingAUserRevokesSessionsStoredUnderALeftoverMapping() throws Exception {
        Utils.setValueInConfig("disable_user_id_mapping", "true");
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.ACCOUNT_LINKING});
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        Main main = process.getProcess();
        AppIdentifier app = process.getAppForTesting().toAppIdentifier();

        AuthRecipeUserInfo primary = EmailPassword.signUp(main, "primary@example.com", "password123");
        AuthRecipeUserInfo user = EmailPassword.signUp(main, "user@example.com", "password123");
        plantLeftoverMapping(main, app, user.getSupertokensUserId(), "external-id");
        createSession(process, "external-id");
        assertEquals(1, Session.getAllNonExpiredSessionHandlesForUser(main, "external-id").length);

        AuthRecipe.createPrimaryUser(main, primary.getSupertokensUserId());
        AuthRecipe.linkAccounts(main, user.getSupertokensUserId(), primary.getSupertokensUserId());

        assertEquals(0, Session.getAllNonExpiredSessionHandlesForUser(main, "external-id").length);

        stop(process);
    }

    @Test
    public void cannotBeEnabledForAnAppWithMappings() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        Main main = process.getProcess();

        TenantIdentifier a1 = new TenantIdentifier(null, process.getAppForTesting().getAppId() + "a1", null);
        Multitenancy.addNewOrUpdateAppOrTenant(main, appConfig(a1, false), false);
        Storage a1Storage = StorageLayer.getStorage(a1, main);
        AuthRecipeUserInfo user = EmailPassword.signUp(a1, a1Storage, main, "user@example.com", "password123");
        UserIdMapping.createUserIdMapping(main, a1.toAppIdentifier(), a1Storage, user.getSupertokensUserId(),
                "external-id", null, false);

        try {
            Multitenancy.addNewOrUpdateAppOrTenant(main, appConfig(a1, true), false);
            fail();
        } catch (InvalidConfigException e) {
            assertEquals("disable_user_id_mapping cannot be enabled because the app has existing user id mappings",
                    e.getMessage());
        }
        assertFalse(UserIdMapping.isUserIdMappingDisabled(main, a1.toAppIdentifier()));

        UserIdMapping.deleteUserIdMapping(a1.toAppIdentifier(), a1Storage, "external-id", UserIdType.EXTERNAL,
                false);
        Multitenancy.addNewOrUpdateAppOrTenant(main, appConfig(a1, true), false);
        assertTrue(UserIdMapping.isUserIdMappingDisabled(main, a1.toAppIdentifier()));

        try {
            UserIdMapping.createUserIdMapping(main, a1.toAppIdentifier(), a1Storage, user.getSupertokensUserId(),
                    "external-id", null, false);
            fail();
        } catch (ServletException e) {
            assertEquals(UserIdMapping.USER_ID_MAPPING_DISABLED_MESSAGE, e.getRootCause().getMessage());
        }

        // other apps are unaffected
        assertFalse(UserIdMapping.isUserIdMappingDisabled(main, process.getAppForTesting().toAppIdentifier()));

        stop(process);
    }

    // The check runs against the database the new config selects. A database this core has not loaded can't be
    // checked without initialising it, so the flag can't be on in the update that moves the app there.
    @Test
    public void cannotKeepTheFlagOnWhileMovingToADatabaseThatIsNotLoaded() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        Main main = process.getProcess();
        if (StorageLayer.isInMemDb(main)) {
            // the in-memory storage has a single user pool, so there is no other database to move to
            stop(process);
            return;
        }

        TenantIdentifier a1 = new TenantIdentifier(null, process.getAppForTesting().getAppId() + "a1", null);
        Multitenancy.addNewOrUpdateAppOrTenant(main, appConfig(a1, true), false);

        TenantConfig moved = appConfig(a1, true);
        moved.coreConfig.addProperty("postgresql_database_name", "st_disable_uidm_not_loaded");
        try {
            Multitenancy.addNewOrUpdateAppOrTenant(main, moved, false);
            fail();
        } catch (InvalidConfigException e) {
            assertEquals("disable_user_id_mapping cannot be on while the app moves to a database that is not loaded "
                    + "yet: add the database with disable_user_id_mapping off, then turn it on", e.getMessage());
        }

        stop(process);
    }

    @Test
    public void startupCheckFailsForAppsWithMappingsWhenTheBaseConfigDisablesMapping() throws Exception {
        Utils.setValueInConfig("disable_user_id_mapping", "true");
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        // without mappings, the core starts
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        Main main = process.getProcess();
        UserIdMapping.assertNoUserIdMappingsWhereDisabledByBaseConfig(main);

        // The in-memory database does not survive a restart, so plant a mapping behind the core's back and run the
        // check that startup runs.
        AppIdentifier app = process.getAppForTesting().toAppIdentifier();
        AuthRecipeUserInfo user = EmailPassword.signUp(main, "user@example.com", "password123");
        StorageUtils.getUserIdMappingStorage(StorageLayer.getStorage(main))
                .createUserIdMapping(app, user.getSupertokensUserId(), "external-id", null);
        try {
            UserIdMapping.assertNoUserIdMappingsWhereDisabledByBaseConfig(main);
            fail();
        } catch (InvalidConfigException e) {
            assertEquals("disable_user_id_mapping is enabled, but these apps have user id mappings (connection uri "
                    + "domain | app id): " + app, e.getMessage());
        }

        stop(process);
    }

    @Test
    public void bulkImportRejectsExternalUserIdsWhenDisabled() throws Exception {
        Utils.setValueInConfig("disable_user_id_mapping", "true");
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        Main main = process.getProcess();
        AppIdentifier app = process.getAppForTesting().toAppIdentifier();
        List<BulkImportUser> users = BulkImportTestUtils.generateBulkImportUserWithRoles(1, List.of("public"), 0,
                List.of());

        try {
            new BulkImportUserUtils(new String[0]).createBulkImportUserFromJSON(main, app,
                    users.get(0).toJsonObject(), BulkImportUserUtils.IDMode.GENERATE);
            fail();
        } catch (InvalidBulkImportDataException e) {
            assertTrue(e.errors.toString(), e.errors.contains(
                    "Cannot set externalUserId: " + UserIdMapping.USER_ID_MAPPING_DISABLED_MESSAGE));
        }

        // users already queued when the config was turned on
        try {
            BulkImport.createMultipleUserIdMapping(main, app, users, new Storage[]{StorageLayer.getStorage(main)});
            fail();
        } catch (StorageTransactionLogicException e) {
            Map<String, Exception> errors = ((BulkImportBatchInsertException) e.actualException).exceptionByUserId;
            assertEquals("E035: Cannot set externalUserId: " + UserIdMapping.USER_ID_MAPPING_DISABLED_MESSAGE,
                    errors.get(BulkImportUserUtils.getPrimaryLoginMethod(users.get(0)).superTokensUserId).getMessage());
        }

        stop(process);
    }

    // Runs one request of each kind the opt-out covers and returns how many times each asked the storage for a user
    // id mapping.
    private static Map<String, Long> countUserIdMappingCallsPerRequest(TestingProcessManager.TestingProcess process)
            throws Exception {
        Map<String, Long> counts = new LinkedHashMap<>();
        // warm-up: the first session creates the signing keys
        createSession(process, emailPasswordSignUp(process, "warm-up@example.com").get("id").getAsString());

        String userId = count(counts, "email-password sign-up",
                () -> emailPasswordSignUp(process, "user@example.com")).get("id").getAsString();
        count(counts, "email-password sign-in", () -> {
            JsonObject body = new JsonObject();
            body.addProperty("email", "user@example.com");
            body.addProperty("password", "validPass123");
            JsonObject response = post(process, "/recipe/signin", body, "emailpassword");
            assertEquals("OK", response.get("status").getAsString());
            return response;
        });

        JsonObject session = count(counts, "session create", () -> createSession(process, userId));
        String handle = session.getAsJsonObject("session").get("handle").getAsString();
        JsonObject refreshed = count(counts, "session refresh", () -> {
            JsonObject body = new JsonObject();
            body.addProperty("refreshToken", session.getAsJsonObject("refreshToken").get("token").getAsString());
            body.addProperty("enableAntiCsrf", false);
            body.addProperty("useDynamicSigningKey", true);
            return postOk(process, "/recipe/session/refresh", body, "session");
        });
        count(counts, "session verify (checkDatabase)", () -> {
            JsonObject body = new JsonObject();
            body.addProperty("accessToken", refreshed.getAsJsonObject("accessToken").get("token").getAsString());
            body.addProperty("doAntiCsrfCheck", false);
            body.addProperty("enableAntiCsrf", false);
            body.addProperty("checkDatabase", true);
            return postOk(process, "/recipe/session/verify", body, "session");
        });
        count(counts, "session remove", () -> {
            JsonObject body = new JsonObject();
            JsonArray handles = new JsonArray();
            handles.add(handle);
            body.add("sessionHandles", handles);
            return postOk(process, "/recipe/session/remove", body, "session");
        });

        Map<String, String> byUserId = new HashMap<>();
        byUserId.put("userId", userId);
        count(counts, "get user by id", () -> get(process, "/user/id", byUserId, null));
        Map<String, String> byEmail = new HashMap<>();
        byEmail.put("email", "user@example.com");
        byEmail.put("doUnionOfAccountInfo", "false");
        count(counts, "get users by account info", () -> get(process, "/users/by-accountinfo", byEmail, null));

        count(counts, "user metadata get", () -> get(process, "/recipe/user/metadata", byUserId, "usermetadata"));
        count(counts, "user metadata update", () -> {
            JsonObject body = new JsonObject();
            body.addProperty("userId", userId);
            body.add("metadataUpdate", new JsonObject());
            return put(process, "/recipe/user/metadata", body, "usermetadata");
        });
        JsonObject role = new JsonObject();
        role.addProperty("role", "admin");
        put(process, "/recipe/role", role, "userroles");
        count(counts, "user roles add", () -> {
            JsonObject body = new JsonObject();
            body.addProperty("userId", userId);
            body.addProperty("role", "admin");
            return put(process, "/recipe/user/role", body, "userroles");
        });
        count(counts, "user roles get", () -> get(process, "/recipe/user/roles", byUserId, "userroles"));

        count(counts, "third-party sign-in-up (new user)",
                () -> thirdPartySignInUp(process, "tp-user", "tp-user@example.com"));
        count(counts, "third-party sign-in-up (existing user)",
                () -> thirdPartySignInUp(process, "tp-user", "tp-user@example.com"));

        JsonObject code = createCode(process, "pl-user@example.com");
        count(counts, "passwordless consume-code (new user)", () -> consumeCode(process, code));
        JsonObject secondCode = createCode(process, "pl-user@example.com");
        count(counts, "passwordless consume-code (existing user)", () -> consumeCode(process, secondCode));

        JsonObject webAuthnUser = count(counts, "webauthn sign-up",
                () -> io.supertokens.test.webauthn.Utils.registerUserWithCredentials(process.getProcess(),
                        "wa-user@example.com"));
        count(counts, "webauthn sign-in", () -> {
            JsonObject response = io.supertokens.test.webauthn.Utils.signInWithUser(process.getProcess(),
                    webAuthnUser);
            assertEquals("OK", response.get("status").getAsString());
            return response;
        });

        System.out.println("[userid-mapping-calls] " + counts);
        return counts;
    }

    private static <T> T count(Map<String, Long> counts, String request, Callable<T> call) throws Exception {
        Start.resetUserIdMappingCallCountForTesting();
        T result = call.call();
        counts.put(request, Start.getUserIdMappingCallCountForTesting());
        return result;
    }

    // Writes a mapping straight to the storage, as a core that had not yet picked up disable_user_id_mapping would.
    private static void plantLeftoverMapping(Main main, AppIdentifier app, String superTokensUserId,
                                             String externalUserId) throws Exception {
        StorageUtils.getUserIdMappingStorage(StorageLayer.getStorage(main))
                .createUserIdMapping(app, superTokensUserId, externalUserId, null);
    }

    private static TenantConfig appConfig(TenantIdentifier app, boolean disableUserIdMapping) {
        JsonObject coreConfig = new JsonObject();
        coreConfig.addProperty("disable_user_id_mapping", disableUserIdMapping);
        return new TenantConfig(app, new EmailPasswordConfig(true), new ThirdPartyConfig(true, null),
                new PasswordlessConfig(true), null, null, coreConfig);
    }

    private static void assertBadRequest(Callable<?> request) throws Exception {
        try {
            request.call();
            fail();
        } catch (HttpResponseException e) {
            assertEquals(400, e.statusCode);
            assertEquals("Http error. Status Code: 400. Message: " + UserIdMapping.USER_ID_MAPPING_DISABLED_MESSAGE,
                    e.getMessage());
        }
    }

    // disable_user_id_mapping in config.yaml only takes effect in a process of its own: the shared test process
    // keeps the base config it started with.
    private static TestingProcessManager.TestingProcess startInMemory(boolean isolated) throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = isolated ? TestingProcessManager.startIsolatedProcess(args)
                : TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        if (!(StorageLayer.getStorage(process.getProcess()) instanceof Start)) {
            // The call counter exists only in the in-memory storage.
            stop(process);
            return null;
        }
        return process;
    }

    private static void stop(TestingProcessManager.TestingProcess process) throws Exception {
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    private static JsonObject post(TestingProcessManager.TestingProcess process, String path, JsonObject body,
                                   String rid) throws Exception {
        return HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "", BASE + path, body, 1000, 1000,
                null, Utils.getCdiVersionStringLatestForTests(), rid);
    }

    private static JsonObject postOk(TestingProcessManager.TestingProcess process, String path, JsonObject body,
                                     String rid) throws Exception {
        JsonObject response = post(process, path, body, rid);
        assertEquals("OK", response.get("status").getAsString());
        return response;
    }

    private static JsonObject put(TestingProcessManager.TestingProcess process, String path, JsonObject body,
                                  String rid) throws Exception {
        JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "", BASE + path, body,
                1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), rid);
        assertEquals("OK", response.get("status").getAsString());
        return response;
    }

    private static JsonObject get(TestingProcessManager.TestingProcess process, String path,
                                  Map<String, String> params, String rid) throws Exception {
        JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "", BASE + path, params,
                1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), rid);
        assertEquals("OK", response.get("status").getAsString());
        return response;
    }

    private static JsonObject emailPasswordSignUp(TestingProcessManager.TestingProcess process, String email)
            throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("email", email);
        body.addProperty("password", "validPass123");
        return postOk(process, "/recipe/signup", body, "emailpassword").getAsJsonObject("user");
    }

    private static JsonObject createSession(TestingProcessManager.TestingProcess process, String userId)
            throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("userId", userId);
        body.add("userDataInJWT", new JsonObject());
        body.add("userDataInDatabase", new JsonObject());
        body.addProperty("enableAntiCsrf", false);
        return postOk(process, "/recipe/session", body, "session");
    }

    private static JsonObject thirdPartySignInUp(TestingProcessManager.TestingProcess process,
                                                 String thirdPartyUserId, String email) throws Exception {
        JsonObject emailObject = new JsonObject();
        emailObject.addProperty("id", email);
        emailObject.addProperty("isVerified", false);
        JsonObject body = new JsonObject();
        body.addProperty("thirdPartyId", "google");
        body.addProperty("thirdPartyUserId", thirdPartyUserId);
        body.add("email", emailObject);
        return postOk(process, "/recipe/signinup", body, "thirdparty");
    }

    private static JsonObject createCode(TestingProcessManager.TestingProcess process, String email)
            throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("email", email);
        return postOk(process, "/recipe/signinup/code", body, "passwordless");
    }

    private static JsonObject consumeCode(TestingProcessManager.TestingProcess process, JsonObject code)
            throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("deviceId", code.get("deviceId").getAsString());
        body.addProperty("preAuthSessionId", code.get("preAuthSessionId").getAsString());
        body.addProperty("userInputCode", code.get("userInputCode").getAsString());
        return postOk(process, "/recipe/signinup/code/consume", body, "passwordless");
    }
}
