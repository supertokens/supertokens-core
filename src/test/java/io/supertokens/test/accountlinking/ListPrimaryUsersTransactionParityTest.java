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

package io.supertokens.test.accountlinking;

import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.authRecipe.AuthRecipe;
import io.supertokens.config.Config;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.emailpassword.exceptions.ResetPasswordInvalidTokenException;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.inmemorydb.ConnectionPool;
import io.supertokens.inmemorydb.config.SQLiteConfig;
import io.supertokens.passwordless.Passwordless;
import io.supertokens.pluginInterface.MigrationMode;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.authRecipe.sqlStorage.AuthRecipeSQLStorage;
import io.supertokens.pluginInterface.emailpassword.PasswordResetTokenInfo;
import io.supertokens.pluginInterface.emailpassword.sqlStorage.EmailPasswordSQLStorage;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.thirdparty.ThirdParty;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.Arrays;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

/**
 * PLAN-018 Unit 3: the new {@code listPrimaryUsersByEmail_Transaction} /
 * {@code listPrimaryUsersByPhoneNumber_Transaction} storage reads (and {@code AuthRecipe.getUserById_Transaction})
 * must (1) return exactly what their non-transaction forms return, and (2) run on the caller's transaction
 * connection so they never borrow a SECOND connection from the same pool inside a startTransaction — the
 * nested same-pool acquisition (pool-exhaustion) pattern the connection guard flags.
 */
public class ListPrimaryUsersTransactionParityTest {

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

    private static String ids(AuthRecipeUserInfo[] users) {
        // Preserve order (both forms sort by timeJoined) so parity covers ordering too.
        return Arrays.stream(users).map(u -> u.getSupertokensUserId()).collect(Collectors.joining(","));
    }

    private static String messageChain(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (Throwable c = t; c != null && c != c.getCause(); c = c.getCause()) {
            sb.append(c.getClass().getSimpleName()).append(": ").append(c.getMessage()).append(" | ");
        }
        return sb.toString();
    }

    private static TestingProcessManager.TestingProcess startWithAccountLinking() throws InterruptedException {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.ACCOUNT_LINKING});
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        return process;
    }

    private static void stop(TestingProcessManager.TestingProcess process) throws InterruptedException {
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * Runs the parity + guard checks once per in-memory migration mode, so the {@code _legacy_Transaction} branches
     * (old-table reads, legacy email union incl. WebAuthn, HashSet dedup) are exercised as well as the
     * {@code _new_Transaction} ones. The mode is static and governs writes too, so each mode gets a fresh process.
     */
    @Test
    public void transactionalReadsMatchNonTransactionalAndAvoidNestedAcquisition() throws Exception {
        try {
            for (MigrationMode mode : MigrationMode.values()) {
                Utils.reset();
                SQLiteConfig.setMigrationModeForTesting(mode);
                runParityAndGuard(mode);
            }
        } finally {
            SQLiteConfig.setMigrationModeForTesting(MigrationMode.MIGRATED);
        }
    }

    private void runParityAndGuard(MigrationMode mode) throws Exception {
        TestingProcessManager.TestingProcess process = startWithAccountLinking();

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            stop(process);
            return;
        }

        Main main = process.getProcess();
        TenantIdentifier tenant = process.getAppForTesting();
        Storage storage = StorageLayer.getBaseStorage(main);
        AuthRecipeSQLStorage sqlStorage = (AuthRecipeSQLStorage) storage;

        // A shared email across three unrelated (unlinked) recipe users (EP, TP, WebAuthn — three branches of the
        // legacy email union), so the email lookup returns more than one primary user and ordering matters. Plus a
        // passwordless phone user, and a passwordless email-only user (the fourth branch).
        String sharedEmail = "shared@example.com";
        AuthRecipeUserInfo epUser = EmailPassword.signUp(main, sharedEmail, "password123");
        AuthRecipeUserInfo tpUser = ThirdParty.signInUp(main, "google", "google-user-1", sharedEmail).user;
        String webauthnUserId = io.supertokens.test.webauthn.Utils.registerUserWithCredentials(main, sharedEmail)
                .getAsJsonObject("user").get("id").getAsString();

        String phone = "+911234567890";
        Passwordless.CreateCodeResponse phoneCode = Passwordless.createCode(main, null, phone, null, "123456");
        AuthRecipeUserInfo plPhoneUser = Passwordless.consumeCode(main, phoneCode.deviceId, phoneCode.deviceIdHash,
                phoneCode.userInputCode, null).user;

        String plEmail = "pless@example.com";
        Passwordless.CreateCodeResponse emailCode = Passwordless.createCode(main, plEmail, null, null, "123456");
        AuthRecipeUserInfo plEmailUser = Passwordless.consumeCode(main, emailCode.deviceId, emailCode.deviceIdHash,
                emailCode.userInputCode, null).user;

        // Linked accounts: an EP primary and a TP recipe user on the same email, linked — several recipe rows
        // resolve to the same primary_or_recipe_user_id, which is what the DISTINCT / dedup logic is for. A
        // passwordless phone user linked to the same primary makes the phone lookup resolve through the link too.
        String linkedEmail = "linked@example.com";
        AuthRecipeUserInfo linkedPrimary = EmailPassword.signUp(main, linkedEmail, "password123");
        AuthRecipeUserInfo linkedTp = ThirdParty.signInUp(main, "google", "google-user-2", linkedEmail).user;
        String linkedPhone = "+919999999999";
        Passwordless.CreateCodeResponse linkedPhoneCode = Passwordless.createCode(main, null, linkedPhone, null,
                "123456");
        AuthRecipeUserInfo linkedPl = Passwordless.consumeCode(main, linkedPhoneCode.deviceId,
                linkedPhoneCode.deviceIdHash, linkedPhoneCode.userInputCode, null).user;
        AuthRecipe.createPrimaryUser(main, linkedPrimary.getSupertokensUserId());
        AuthRecipe.linkAccounts(main, linkedTp.getSupertokensUserId(), linkedPrimary.getSupertokensUserId());
        AuthRecipe.linkAccounts(main, linkedPl.getSupertokensUserId(), linkedPrimary.getSupertokensUserId());

        // ── (1) parity: transaction read == non-transaction read, for every field of the account-info surface ──
        for (String email : new String[]{sharedEmail, plEmail, linkedEmail, "does-not-exist@example.com"}) {
            AuthRecipeUserInfo[] nonTx = sqlStorage.listPrimaryUsersByEmail(tenant, email);
            AuthRecipeUserInfo[] tx = sqlStorage.startTransaction(
                    con -> sqlStorage.listPrimaryUsersByEmail_Transaction(tenant, con, email));
            assertEquals(mode + ": listPrimaryUsersByEmail parity for " + email, ids(nonTx), ids(tx));
        }
        assertEquals(mode + ": sanity: shared email resolves to three primary users", 3,
                sqlStorage.listPrimaryUsersByEmail(tenant, sharedEmail).length);
        AuthRecipeUserInfo[] linkedByEmail = sqlStorage.startTransaction(
                con -> sqlStorage.listPrimaryUsersByEmail_Transaction(tenant, con, linkedEmail));
        assertEquals(mode + ": linked email resolves to the one primary user", 1, linkedByEmail.length);
        assertEquals(linkedPrimary.getSupertokensUserId(), linkedByEmail[0].getSupertokensUserId());
        assertEquals(mode + ": linked primary carries all three login methods", 3,
                linkedByEmail[0].loginMethods.length);

        for (String ph : new String[]{phone, linkedPhone, "+910000000000"}) {
            AuthRecipeUserInfo[] nonTx = sqlStorage.listPrimaryUsersByPhoneNumber(tenant, ph);
            AuthRecipeUserInfo[] tx = sqlStorage.startTransaction(
                    con -> sqlStorage.listPrimaryUsersByPhoneNumber_Transaction(tenant, con, ph));
            assertEquals(mode + ": listPrimaryUsersByPhoneNumber parity for " + ph, ids(nonTx), ids(tx));
        }
        assertEquals(mode + ": linked phone resolves to the primary user", linkedPrimary.getSupertokensUserId(),
                ids(sqlStorage.listPrimaryUsersByPhoneNumber(tenant, linkedPhone)));

        String[] uids = {epUser.getSupertokensUserId(), tpUser.getSupertokensUserId(), webauthnUserId,
                plPhoneUser.getSupertokensUserId(), plEmailUser.getSupertokensUserId(),
                linkedPrimary.getSupertokensUserId(), linkedTp.getSupertokensUserId(),
                linkedPl.getSupertokensUserId()};
        for (String uid : uids) {
            AuthRecipeUserInfo nonTx = AuthRecipe.getUserById(tenant.toAppIdentifier(), storage, uid);
            AuthRecipeUserInfo tx = sqlStorage.startTransaction(
                    con -> AuthRecipe.getUserById_Transaction(tenant.toAppIdentifier(), con, storage, uid));
            assertEquals(mode + ": getUserById parity for " + uid, nonTx.getSupertokensUserId(),
                    tx.getSupertokensUserId());
            assertEquals(mode + ": getUserById login-method parity for " + uid, nonTx.loginMethods.length,
                    tx.loginMethods.length);
        }

        // ── (2) guard: the _Transaction reads must NOT borrow a second connection inside a startTransaction ──
        if (storage instanceof io.supertokens.inmemorydb.Start) {
            String uid = epUser.getSupertokensUserId();
            ConnectionPool.setThrowOnNestedAcquisition(true);
            try {
                // Positive: threading `con` through the reads keeps everything on the transaction connection.
                sqlStorage.startTransaction(
                        con -> sqlStorage.listPrimaryUsersByEmail_Transaction(tenant, con, sharedEmail));
                sqlStorage.startTransaction(
                        con -> sqlStorage.listPrimaryUsersByPhoneNumber_Transaction(tenant, con, phone));
                sqlStorage.startTransaction(
                        con -> AuthRecipe.getUserById_Transaction(tenant.toAppIdentifier(), con, storage, uid));

                // Negative control: the non-transaction read borrows a second connection and trips the guard,
                // proving the guard is armed and that the _Transaction variants are what avoids it.
                try {
                    sqlStorage.startTransaction(con -> AuthRecipe.getUserById(tenant.toAppIdentifier(), storage, uid));
                    fail("expected the nested same-pool acquisition guard to fire on the non-transaction read");
                } catch (Exception e) {
                    assertTrue("guard message not found in exception chain: " + messageChain(e),
                            messageChain(e).contains("Nested same-pool connection acquisition"));
                }
            } finally {
                ConnectionPool.setThrowOnNestedAcquisition(false);
            }
        }

        stop(process);
    }

    private static void addNullEmailResetToken(Main main, TenantIdentifier tenant, String userId, String token)
            throws Exception {
        // A token stored without an email is what a token generated before the email column existed looks like.
        ((EmailPasswordSQLStorage) StorageLayer.getStorage(main)).addPasswordResetToken(tenant.toAppIdentifier(),
                new PasswordResetTokenInfo(userId, io.supertokens.utils.Utils.hashSHA256(token),
                        System.currentTimeMillis() + Config.getConfig(main).getPasswordResetTokenLifetime(), null));
    }

    /**
     * End-to-end cover for the one runtime call site this change rewires: the {@code matchedToken.email == null}
     * branch of {@code EmailPassword.consumeResetPasswordToken} reads the user on the transaction connection. With
     * the guard in throw mode, a revert to the non-transaction {@code AuthRecipe.getUserById} fails this test.
     */
    @Test
    public void consumeNullEmailResetTokenReadsUserOnTransactionConnection() throws Exception {
        TestingProcessManager.TestingProcess process = startWithAccountLinking();

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            stop(process);
            return;
        }

        Main main = process.getProcess();
        TenantIdentifier tenant = process.getAppForTesting();
        Storage storage = StorageLayer.getStorage(main);
        boolean inMemory = storage instanceof io.supertokens.inmemorydb.Start;

        AuthRecipeUserInfo user = EmailPassword.signUp(main, "reset@example.com", "password123");
        String userId = user.getSupertokensUserId();

        // Positive: an unlinked EP user — the email is resolved from the user's EP login method.
        addNullEmailResetToken(main, tenant, userId, "token-unlinked");
        if (inMemory) {
            ConnectionPool.setThrowOnNestedAcquisition(true);
        }
        try {
            EmailPassword.ConsumeResetPasswordTokenResult result = EmailPassword.consumeResetPasswordToken(
                    tenant, storage, "token-unlinked");
            assertEquals(userId, result.userId);
            assertEquals("reset@example.com", result.email);
        } finally {
            ConnectionPool.setThrowOnNestedAcquisition(false);
        }

        // Negative: once the user is linked to a second login method the null-email token is ambiguous and must be
        // rejected (the loginMethods.length > 1 check, which now reads through the transaction path).
        AuthRecipeUserInfo tpUser = ThirdParty.signInUp(main, "google", "google-reset", "reset@example.com").user;
        AuthRecipe.createPrimaryUser(main, userId);
        AuthRecipe.linkAccounts(main, tpUser.getSupertokensUserId(), userId);

        addNullEmailResetToken(main, tenant, userId, "token-linked");
        if (inMemory) {
            ConnectionPool.setThrowOnNestedAcquisition(true);
        }
        try {
            EmailPassword.consumeResetPasswordToken(tenant, storage, "token-linked");
            fail("expected ResetPasswordInvalidTokenException for a null-email token on a linked user");
        } catch (ResetPasswordInvalidTokenException expected) {
            // ok
        } finally {
            ConnectionPool.setThrowOnNestedAcquisition(false);
        }

        stop(process);
    }
}
