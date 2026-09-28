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
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.inmemorydb.ConnectionPool;
import io.supertokens.passwordless.Passwordless;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.authRecipe.sqlStorage.AuthRecipeSQLStorage;
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

    @Test
    public void transactionalReadsMatchNonTransactionalAndAvoidNestedAcquisition() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
            return;
        }

        Main main = process.getProcess();
        TenantIdentifier tenant = process.getAppForTesting();
        Storage storage = StorageLayer.getBaseStorage(main);
        AuthRecipeSQLStorage sqlStorage = (AuthRecipeSQLStorage) storage;

        // A shared email across two unrelated (unlinked) recipe users, so the email lookup returns more than one
        // primary user and ordering matters. Plus a passwordless phone user, and a passwordless email-only user.
        String sharedEmail = "shared@example.com";
        AuthRecipeUserInfo epUser = EmailPassword.signUp(main, sharedEmail, "password123");
        AuthRecipeUserInfo tpUser = ThirdParty.signInUp(main, "google", "google-user-1", sharedEmail).user;

        String phone = "+911234567890";
        Passwordless.CreateCodeResponse phoneCode = Passwordless.createCode(main, null, phone, null, "123456");
        AuthRecipeUserInfo plPhoneUser = Passwordless.consumeCode(main, phoneCode.deviceId, phoneCode.deviceIdHash,
                phoneCode.userInputCode, null).user;

        String plEmail = "pless@example.com";
        Passwordless.CreateCodeResponse emailCode = Passwordless.createCode(main, plEmail, null, null, "123456");
        AuthRecipeUserInfo plEmailUser = Passwordless.consumeCode(main, emailCode.deviceId, emailCode.deviceIdHash,
                emailCode.userInputCode, null).user;

        // ── (1) parity: transaction read == non-transaction read, for every field of the account-info surface ──
        for (String email : new String[]{sharedEmail, plEmail, "does-not-exist@example.com"}) {
            AuthRecipeUserInfo[] nonTx = sqlStorage.listPrimaryUsersByEmail(tenant, email);
            AuthRecipeUserInfo[] tx = sqlStorage.startTransaction(
                    con -> sqlStorage.listPrimaryUsersByEmail_Transaction(tenant, con, email));
            assertEquals("listPrimaryUsersByEmail parity for " + email, ids(nonTx), ids(tx));
        }
        assertEquals("sanity: shared email resolves to two primary users", 2,
                sqlStorage.listPrimaryUsersByEmail(tenant, sharedEmail).length);

        for (String ph : new String[]{phone, "+910000000000"}) {
            AuthRecipeUserInfo[] nonTx = sqlStorage.listPrimaryUsersByPhoneNumber(tenant, ph);
            AuthRecipeUserInfo[] tx = sqlStorage.startTransaction(
                    con -> sqlStorage.listPrimaryUsersByPhoneNumber_Transaction(tenant, con, ph));
            assertEquals("listPrimaryUsersByPhoneNumber parity for " + ph, ids(nonTx), ids(tx));
        }

        for (AuthRecipeUserInfo u : new AuthRecipeUserInfo[]{epUser, tpUser, plPhoneUser, plEmailUser}) {
            String uid = u.getSupertokensUserId();
            AuthRecipeUserInfo nonTx = AuthRecipe.getUserById(tenant.toAppIdentifier(), storage, uid);
            AuthRecipeUserInfo tx = sqlStorage.startTransaction(
                    con -> AuthRecipe.getUserById_Transaction(tenant.toAppIdentifier(), con, storage, uid));
            assertEquals("getUserById parity for " + uid, nonTx.getSupertokensUserId(), tx.getSupertokensUserId());
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

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
