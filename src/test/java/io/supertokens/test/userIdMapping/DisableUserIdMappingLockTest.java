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

import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.auditlog.UnauditedTransaction;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.emailverification.EmailVerification;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.multitenancy.MultitenancyHelper;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.StorageUtils;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.EmailPasswordConfig;
import io.supertokens.pluginInterface.multitenancy.PasswordlessConfig;
import io.supertokens.pluginInterface.multitenancy.TenantConfig;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.ThirdPartyConfig;
import io.supertokens.pluginInterface.multitenancy.sqlStorage.MultitenancySQLStorage;
import io.supertokens.pluginInterface.useridmapping.sqlStorage.UserIdMappingSQLStorage;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.useridmapping.UserIdMapping;
import io.supertokens.useridmapping.UserIdMappingOptOut;
import io.supertokens.useridmapping.UserIdType;
import jakarta.servlet.ServletException;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

// disable_user_id_mapping and mapping creation exclude each other through a database lock, mapping creation reads
// the stored config, and a core that finds mappings for an app with the flag on keeps reading them.
public class DisableUserIdMappingLockTest {

    private static final String ENABLED_WITH_MAPPINGS =
            "disable_user_id_mapping cannot be enabled because the app has existing user id mappings";

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

    // Another core turned the flag on, and this core hasn't refreshed its config yet: creating a mapping here is
    // refused anyway, because creation reads the flag from the stored config.
    @Test
    public void mappingCreationReadsTheFlagFromTheStoredConfig() throws Exception {
        TestingProcessManager.TestingProcess process = start();
        Main main = process.getProcess();
        TenantIdentifier app = process.getAppForTesting();
        Storage storage = StorageLayer.getStorage(app, main);
        AuthRecipeUserInfo user = EmailPassword.signUp(app, storage, main, "user@example.com", "password123");

        StorageLayer.getMultitenancyStorage(main).overwriteTenantConfig(appConfig(app, true, false));
        assertFalse("this core must not have picked up the change yet",
                UserIdMapping.isUserIdMappingDisabled(main, app.toAppIdentifier()));

        assertMappingCreationRefused(main, app.toAppIdentifier(), storage, user.getSupertokensUserId());
        assertFalse(StorageUtils.getUserIdMappingStorage(storage).hasUserIdMappings(app.toAppIdentifier()));

        stop(process);
    }

    // Same stale core, with the email verification exception: the refused creation must not have moved the user's
    // verified email and verification tokens to the external id, which would have no mapping pointing at it.
    @Test
    public void refusedMappingCreationLeavesEmailVerificationWithTheUser() throws Exception {
        TestingProcessManager.TestingProcess process = start();
        Main main = process.getProcess();
        TenantIdentifier app = process.getAppForTesting();
        AppIdentifier appIdentifier = app.toAppIdentifier();
        Storage storage = StorageLayer.getStorage(app, main);
        AuthRecipeUserInfo user = EmailPassword.signUp(app, storage, main, "user@example.com", "password123");
        String superTokensUserId = user.getSupertokensUserId();
        EmailVerification.verifyEmail(app, storage, EmailVerification.generateEmailVerificationToken(app, storage,
                main, superTokensUserId, "user@example.com"));
        String pendingToken = EmailVerification.generateEmailVerificationToken(app, storage, main, superTokensUserId,
                "other@example.com");

        StorageLayer.getMultitenancyStorage(main).overwriteTenantConfig(appConfig(app, true, false));
        assertFalse("this core must not have picked up the change yet",
                UserIdMapping.isUserIdMappingDisabled(main, appIdentifier));

        try {
            UserIdMapping.createUserIdMapping(main, appIdentifier, new Storage[]{storage}, superTokensUserId,
                    "external-id", null, false, true);
            fail();
        } catch (ServletException e) {
            assertEquals(UserIdMapping.USER_ID_MAPPING_DISABLED_MESSAGE, e.getRootCause().getMessage());
        }

        assertFalse(StorageUtils.getUserIdMappingStorage(storage).hasUserIdMappings(appIdentifier));
        assertTrue(EmailVerification.isEmailVerified(appIdentifier, storage, superTokensUserId, "user@example.com"));
        assertFalse(EmailVerification.isEmailVerified(appIdentifier, storage, "external-id", "user@example.com"));
        assertEquals(superTokensUserId, EmailVerification.verifyEmail(app, storage, pendingToken).id);

        stop(process);
    }

    @Test
    public void forcingTheFlagOnIgnoresExistingMappings() throws Exception {
        TestingProcessManager.TestingProcess process = start();
        Main main = process.getProcess();
        TenantIdentifier app = process.getAppForTesting();
        AppIdentifier appIdentifier = app.toAppIdentifier();
        Storage storage = StorageLayer.getStorage(app, main);
        AuthRecipeUserInfo user = EmailPassword.signUp(app, storage, main, "user@example.com", "password123");
        UserIdMapping.createUserIdMapping(main, appIdentifier, storage, user.getSupertokensUserId(), "external-id",
                null, false);

        try {
            Multitenancy.addNewOrUpdateAppOrTenant(main, appConfig(app, true, false), false);
            fail();
        } catch (InvalidConfigException e) {
            assertEquals(ENABLED_WITH_MAPPINGS, e.getMessage());
        }

        Multitenancy.addNewOrUpdateAppOrTenant(main, appConfig(app, true, true), false);
        assertTrue(UserIdMappingOptOut.isReadSkipped(main, appIdentifier));
        assertNull(UserIdMapping.getUserIdMapping(main, appIdentifier, storage, "external-id", UserIdType.ANY));

        // forced: reloading the storages finds the mapping but keeps ignoring it
        reloadStorages(main, app);
        assertTrue(UserIdMappingOptOut.isReadSkipped(main, appIdentifier));
        assertNull(UserIdMapping.getUserIdMapping(main, appIdentifier, storage, "external-id", UserIdType.ANY));

        AuthRecipeUserInfo other = EmailPassword.signUp(app, storage, main, "other@example.com", "password123");
        assertMappingCreationRefused(main, appIdentifier, storage, other.getSupertokensUserId());

        stop(process);
    }

    // A mapping that appears behind the core's back (here written straight to the storage, as a restored database
    // would have it) is found when the storages load: the core keeps reading mappings for the app until the last one
    // is removed.
    @Test
    public void mappingsFoundWhenStoragesLoadAreStillRead() throws Exception {
        TestingProcessManager.TestingProcess process = start();
        Main main = process.getProcess();
        TenantIdentifier app = process.getAppForTesting();
        AppIdentifier appIdentifier = app.toAppIdentifier();
        Storage storage = StorageLayer.getStorage(app, main);
        AuthRecipeUserInfo user = EmailPassword.signUp(app, storage, main, "user@example.com", "password123");

        Multitenancy.addNewOrUpdateAppOrTenant(main, appConfig(app, true, false), false);
        assertTrue(UserIdMappingOptOut.isReadSkipped(main, appIdentifier));

        StorageUtils.getUserIdMappingStorage(storage)
                .createUserIdMapping(appIdentifier, user.getSupertokensUserId(), "external-id", null);
        reloadStorages(main, app);

        assertFalse(UserIdMappingOptOut.isReadSkipped(main, appIdentifier));
        assertEquals(user.getSupertokensUserId(), UserIdMapping.getUserIdMapping(main, appIdentifier, storage,
                "external-id", UserIdType.ANY).superTokensUserId);

        JsonObject remove = new JsonObject();
        remove.addProperty("userId", "external-id");
        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                "http://localhost:3567/recipe/userid/map/remove", remove, 1000, 1000, null,
                Utils.getCdiVersionStringLatestForTests(), "useridmapping");
        assertTrue(response.get("didMappingExist").getAsBoolean());
        assertTrue(UserIdMappingOptOut.isReadSkipped(main, appIdentifier));

        stop(process);
    }

    // A storage the core gave up waiting for at startup (storage-init timeout) is not queried for mappings: that
    // query would wait for its pool and stall startup again. Its apps keep reading mappings, the startup check
    // skips them, and they are checked once the storage is up.
    @Test
    public void appsOnAStorageStillInitialisingAreCheckedOnceItIsUp() throws Exception {
        Utils.setValueInConfig("disable_user_id_mapping", "true");
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(new String[]{"../"});
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        Main main = process.getProcess();
        AppIdentifier appIdentifier = new AppIdentifier(null, null);
        Storage storage = StorageLayer.getStorage(TenantIdentifier.BASE_TENANT, main);
        assertTrue(UserIdMappingOptOut.isReadSkipped(main, appIdentifier));

        CompletableFuture<Void> initialised = new CompletableFuture<>();
        UserIdMappingOptOut.addStoragesStillInitialising(main, Map.of(storage, initialised));
        UserIdMappingOptOut.forgetAllChecks(main);
        UserIdMappingOptOut.checkApps(main, List.of(appIdentifier));
        assertFalse("not checked while its storage initialises, so it keeps reading mappings",
                UserIdMappingOptOut.isReadSkipped(main, appIdentifier));
        initialised.complete(null);
        assertTrue(UserIdMappingOptOut.isReadSkipped(main, appIdentifier));

        // the startup check skips it too, although the database has a mapping
        AuthRecipeUserInfo user = EmailPassword.signUp(TenantIdentifier.BASE_TENANT, storage, main,
                "user@example.com", "password123");
        StorageUtils.getUserIdMappingStorage(storage)
                .createUserIdMapping(appIdentifier, user.getSupertokensUserId(), "external-id", null);
        CompletableFuture<Void> initialisedAgain = new CompletableFuture<>();
        UserIdMappingOptOut.addStoragesStillInitialising(main, Map.of(storage, initialisedAgain));
        UserIdMappingOptOut.forgetAllChecks(main);
        UserIdMappingOptOut.checkApps(main, List.of(appIdentifier));
        UserIdMapping.assertNoUserIdMappingsWhereDisabledByBaseConfig(main);

        initialisedAgain.complete(null);
        assertFalse(UserIdMappingOptOut.isReadSkipped(main, appIdentifier));
        try {
            UserIdMapping.assertNoUserIdMappingsWhereDisabledByBaseConfig(main);
            fail();
        } catch (InvalidConfigException e) {
            assertTrue(e.getMessage().startsWith("disable_user_id_mapping is enabled, but these apps have user id"));
        }

        stop(process);
    }

    // A mapping creation holds the creation lock until it commits. Turning the flag on waits for it, then sees the
    // mapping and is refused.
    @Test
    public void turningTheFlagOnWaitsForAMappingCreationAndThenIsRefused() throws Exception {
        TestingProcessManager.TestingProcess process = start();
        Main main = process.getProcess();
        if (StorageLayer.isInMemDb(main)) {
            stop(process); // the in-memory database runs one transaction at a time, so it has no such locks
            return;
        }
        TenantIdentifier app = process.getAppForTesting();
        AppIdentifier appIdentifier = app.toAppIdentifier();
        Storage storage = StorageLayer.getStorage(app, main);
        AuthRecipeUserInfo user = EmailPassword.signUp(app, storage, main, "user@example.com", "password123");

        CountDownLatch inserted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Object> creation = executor.submit(() -> UserIdMappingOptOut.createWhileUserIdMappingIsEnabled(
                    main, appIdentifier, storage, con -> {
                        ((UserIdMappingSQLStorage) storage).createUserIdMapping_Transaction(con, appIdentifier,
                                user.getSupertokensUserId(), "external-id", null);
                        inserted.countDown();
                        await(release);
                        return null;
                    }));
            assertTrue(inserted.await(10, TimeUnit.SECONDS));

            Future<Object> enabling = executor.submit(() -> {
                Multitenancy.addNewOrUpdateAppOrTenant(main, appConfig(app, true, false), false);
                return null;
            });
            Thread.sleep(1000);
            assertFalse("turning the flag on must wait for the mapping creation", enabling.isDone());

            release.countDown();
            creation.get(10, TimeUnit.SECONDS);
            try {
                enabling.get(10, TimeUnit.SECONDS);
                fail();
            } catch (ExecutionException e) {
                assertTrue(e.getCause() instanceof InvalidConfigException);
                assertEquals(ENABLED_WITH_MAPPINGS, e.getCause().getMessage());
            }
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
        assertFalse(UserIdMapping.isUserIdMappingDisabled(main, appIdentifier));
        assertTrue(StorageUtils.getUserIdMappingStorage(storage).hasUserIdMappings(appIdentifier));

        stop(process);
    }

    // Turning the flag on holds the lock exclusively until its config is committed. A mapping creation waits for it,
    // then reads the flag as on and is refused, although this core's config still has it off.
    @Test
    @UnauditedTransaction(justification = "Test: holds the lock that turning disable_user_id_mapping on takes.")
    public void mappingCreationWaitsForTurningTheFlagOnAndThenIsRefused() throws Exception {
        TestingProcessManager.TestingProcess process = start();
        Main main = process.getProcess();
        if (StorageLayer.isInMemDb(main)) {
            stop(process); // the in-memory database runs one transaction at a time, so it has no such locks
            return;
        }
        TenantIdentifier app = process.getAppForTesting();
        AppIdentifier appIdentifier = app.toAppIdentifier();
        Storage storage = StorageLayer.getStorage(app, main);
        AuthRecipeUserInfo user = EmailPassword.signUp(app, storage, main, "user@example.com", "password123");
        MultitenancySQLStorage base = (MultitenancySQLStorage) StorageLayer.getBaseStorage(main);
        assertEquals("the test database holds both the tenant configs and the app's users",
                base.getUserPoolId(), storage.getUserPoolId());

        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Object> enabling = executor.submit(() -> base.startTransaction(con -> {
                ((UserIdMappingSQLStorage) base).lockAppForDisablingUserIdMapping_Transaction(con, appIdentifier);
                try {
                    base.overwriteTenantConfig_Transaction(con, appConfig(app, true, false));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                locked.countDown();
                await(release);
                base.commitTransaction(con);
                return null;
            }));
            assertTrue(locked.await(10, TimeUnit.SECONDS));

            Future<Object> creation = executor.submit(() -> {
                UserIdMapping.createUserIdMapping(main, appIdentifier, storage, user.getSupertokensUserId(),
                        "external-id", null, false);
                return null;
            });
            Thread.sleep(1000);
            assertFalse("the mapping creation must wait for the flag to be turned on", creation.isDone());

            release.countDown();
            enabling.get(10, TimeUnit.SECONDS);
            try {
                creation.get(10, TimeUnit.SECONDS);
                fail();
            } catch (ExecutionException e) {
                assertTrue(e.getCause() instanceof ServletException);
                assertEquals(UserIdMapping.USER_ID_MAPPING_DISABLED_MESSAGE,
                        ((ServletException) e.getCause()).getRootCause().getMessage());
            }
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
        assertFalse(StorageUtils.getUserIdMappingStorage(storage).hasUserIdMappings(appIdentifier));

        stop(process);
    }

    private static void assertMappingCreationRefused(Main main, AppIdentifier appIdentifier, Storage storage,
                                                     String superTokensUserId) throws Exception {
        try {
            UserIdMapping.createUserIdMapping(main, appIdentifier, storage, superTokensUserId, "new-external-id",
                    null, false);
            fail();
        } catch (ServletException e) {
            assertEquals(UserIdMapping.USER_ID_MAPPING_DISABLED_MESSAGE, e.getRootCause().getMessage());
        }
    }

    private static void reloadStorages(Main main, TenantIdentifier app) {
        MultitenancyHelper.getInstance(main).forceReloadAllResources(List.of(app));
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("timed out");
            }
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    private static TenantConfig appConfig(TenantIdentifier app, boolean disableUserIdMapping, boolean force) {
        JsonObject coreConfig = new JsonObject();
        coreConfig.addProperty("disable_user_id_mapping", disableUserIdMapping);
        coreConfig.addProperty("disable_user_id_mapping_force", force);
        return new TenantConfig(app, new EmailPasswordConfig(true), new ThirdPartyConfig(true, null),
                new PasswordlessConfig(true), null, null, coreConfig);
    }

    private static TestingProcessManager.TestingProcess start() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        return process;
    }

    private static void stop(TestingProcessManager.TestingProcess process) throws Exception {
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
