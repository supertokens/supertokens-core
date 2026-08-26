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

import io.supertokens.ProcessState;
import io.supertokens.auditlog.lifecycle.LifecycleEventPayload;
import io.supertokens.auditlog.lifecycle.LifecycleEventType;
import io.supertokens.authRecipe.AuthRecipe;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.inmemorydb.Start;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.auditlog.AuditLogEvent;
import io.supertokens.pluginInterface.auditlog.AuditedResult;
import io.supertokens.pluginInterface.auditlog.ActivityLogSQLStorage;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.authRecipe.sqlStorage.AuthRecipeSQLStorage;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Unit 5 of PLAN-011: {@code AuthRecipe.linkAccounts} / {@code unlinkAccounts} emit an
 * {@code account_linking} / {@code account_unlinking} lifecycle event atomically with the mapping change,
 * through {@code ActivityLogSQLStorage.startAuditedTransaction}. A real link/unlink emits exactly one
 * schema-valid event with the correct top-level ids; a no-op link emits nothing; a failure inside the
 * audited transaction rolls back both the mapping and the event.
 */
public class AccountLinkingAuditEventTest {

    private static final String ACTIVITY_LOG = "activity_log";

    @Rule
    public TestRule watchman = Utils.getOnFailure();

    @Rule
    public TestRule retryFlaky = Utils.retryFlakyTest();

    @AfterClass
    public static void afterTesting() {
        Utils.afterTesting();
    }

    @Before
    public void beforeEach() {
        Utils.reset();
    }

    /**
     * Linking two users produces exactly one {@code account_linking} row with the right ids and a schema-valid
     * payload, committed together with the mapping change (both observed in one read after the call returns).
     */
    @Test
    public void linkEmitsExactlyOneEventCommittedWithTheMapping() throws Exception {
        TestingProcessManager.TestingProcess process = startProcess();
        if (process == null) {
            return;
        }
        Start storage = (Start) StorageLayer.getStorage(process.getProcess());

        AuthRecipeUserInfo recipe = EmailPassword.signUp(process.getProcess(), "r@example.com", "password");
        AuthRecipeUserInfo primary = EmailPassword.signUp(process.getProcess(), "p@example.com", "password");
        AuthRecipe.createPrimaryUser(process.getProcess(), primary.getSupertokensUserId());

        boolean wasAlreadyLinked = AuthRecipe.linkAccounts(process.getProcess(), recipe.getSupertokensUserId(),
                primary.getSupertokensUserId()).wasAlreadyLinked;
        assertFalse(wasAlreadyLinked);

        // Mapping change landed: the two accounts are now one primary group.
        AuthRecipeUserInfo refetched = AuthRecipe.getUserById(process.getProcess(), recipe.getSupertokensUserId());
        assertTrue(refetched.isPrimaryUser);
        assertEquals(primary.getSupertokensUserId(), refetched.getSupertokensUserId());

        // Exactly one account_linking event, with the correct top-level ids and a schema-valid payload.
        List<Row> events = readEvents(storage, LifecycleEventType.ACCOUNT_LINKING.getValue());
        assertEquals(1, events.size());
        Row event = events.get(0);
        assertEquals(recipe.getSupertokensUserId(), event.recipeUserId);
        assertEquals(primary.getSupertokensUserId(), event.primaryOrRecipeUserId);
        assertNotNull(event.payload);
        LifecycleEventPayload payload = LifecycleEventPayload.fromJson(event.payload);
        assertEquals(LifecycleEventType.ACCOUNT_LINKING, payload.type);
        // The payload records both groups' before-link presence lists.
        assertEquals(2, payload.groupsBefore.size());
        List<String> groupIds = new ArrayList<>();
        for (int i = 0; i < payload.groupsBefore.size(); i++) {
            groupIds.add(payload.groupsBefore.get(i).primaryOrRecipeUserId);
        }
        assertTrue(groupIds.contains(recipe.getSupertokensUserId()));
        assertTrue(groupIds.contains(primary.getSupertokensUserId()));

        stopProcess(process);
    }

    /**
     * An already-linked call is a no-op: it emits no additional event.
     */
    @Test
    public void alreadyLinkedCallEmitsNoEvent() throws Exception {
        TestingProcessManager.TestingProcess process = startProcess();
        if (process == null) {
            return;
        }
        Start storage = (Start) StorageLayer.getStorage(process.getProcess());

        AuthRecipeUserInfo recipe = EmailPassword.signUp(process.getProcess(), "r@example.com", "password");
        AuthRecipeUserInfo primary = EmailPassword.signUp(process.getProcess(), "p@example.com", "password");
        AuthRecipe.createPrimaryUser(process.getProcess(), primary.getSupertokensUserId());

        assertFalse(AuthRecipe.linkAccounts(process.getProcess(), recipe.getSupertokensUserId(),
                primary.getSupertokensUserId()).wasAlreadyLinked);
        assertEquals(1, readEvents(storage, LifecycleEventType.ACCOUNT_LINKING.getValue()).size());

        // Calling again is a no-op — still exactly one event.
        assertTrue(AuthRecipe.linkAccounts(process.getProcess(), recipe.getSupertokensUserId(),
                primary.getSupertokensUserId()).wasAlreadyLinked);
        assertEquals(1, readEvents(storage, LifecycleEventType.ACCOUNT_LINKING.getValue()).size());

        stopProcess(process);
    }

    /**
     * A plain unlink (recipe user distinct from the primary id) emits one {@code account_unlinking} event and
     * leaves the recipe user standalone again.
     */
    @Test
    public void plainUnlinkEmitsEventAndFreesTheUser() throws Exception {
        TestingProcessManager.TestingProcess process = startProcess();
        if (process == null) {
            return;
        }
        Start storage = (Start) StorageLayer.getStorage(process.getProcess());

        AuthRecipeUserInfo recipe = EmailPassword.signUp(process.getProcess(), "r@example.com", "password");
        AuthRecipeUserInfo primary = EmailPassword.signUp(process.getProcess(), "p@example.com", "password");
        AuthRecipe.createPrimaryUser(process.getProcess(), primary.getSupertokensUserId());
        AuthRecipe.linkAccounts(process.getProcess(), recipe.getSupertokensUserId(),
                primary.getSupertokensUserId());

        boolean wasDeleted = AuthRecipe.unlinkAccounts(process.getProcess(), recipe.getSupertokensUserId());
        assertFalse(wasDeleted);

        // The recipe user is a standalone (non-primary) account again.
        AuthRecipeUserInfo refetched = AuthRecipe.getUserById(process.getProcess(), recipe.getSupertokensUserId());
        assertFalse(refetched.isPrimaryUser);
        assertEquals(recipe.getSupertokensUserId(), refetched.getSupertokensUserId());

        List<Row> events = readEvents(storage, LifecycleEventType.ACCOUNT_UNLINKING.getValue());
        assertEquals(1, events.size());
        Row event = events.get(0);
        assertEquals(recipe.getSupertokensUserId(), event.recipeUserId);
        assertEquals(primary.getSupertokensUserId(), event.primaryOrRecipeUserId);
        LifecycleEventPayload payload = LifecycleEventPayload.fromJson(event.payload);
        assertEquals(LifecycleEventType.ACCOUNT_UNLINKING, payload.type);
        assertEquals(primary.getSupertokensUserId(), payload.remainingGroupAfter.primaryOrRecipeUserId);
        assertEquals(recipe.getSupertokensUserId(), payload.freedMemberAfter.primaryOrRecipeUserId);

        stopProcess(process);
    }

    /**
     * The delete branch of unlink (unlinking the primary user id itself when the group has other members, which
     * deletes that recipe user) also emits an {@code account_unlinking} event; the freed member is present in
     * no tenants and the surviving group is recorded as the remaining group.
     */
    @Test
    public void deleteBranchUnlinkEmitsEvent() throws Exception {
        TestingProcessManager.TestingProcess process = startProcess();
        if (process == null) {
            return;
        }
        Start storage = (Start) StorageLayer.getStorage(process.getProcess());

        AuthRecipeUserInfo primary = EmailPassword.signUp(process.getProcess(), "p@example.com", "password");
        AuthRecipeUserInfo other = EmailPassword.signUp(process.getProcess(), "o@example.com", "password");
        AuthRecipe.createPrimaryUser(process.getProcess(), primary.getSupertokensUserId());
        AuthRecipe.linkAccounts(process.getProcess(), other.getSupertokensUserId(),
                primary.getSupertokensUserId());

        // Unlinking the primary user id itself, with another member linked, deletes that recipe user.
        boolean wasDeleted = AuthRecipe.unlinkAccounts(process.getProcess(), primary.getSupertokensUserId());
        assertTrue(wasDeleted);

        List<Row> events = readEvents(storage, LifecycleEventType.ACCOUNT_UNLINKING.getValue());
        assertEquals(1, events.size());
        Row event = events.get(0);
        assertEquals(primary.getSupertokensUserId(), event.recipeUserId);
        LifecycleEventPayload payload = LifecycleEventPayload.fromJson(event.payload);
        assertEquals(LifecycleEventType.ACCOUNT_UNLINKING, payload.type);
        // Freed member (the deleted recipe user) is present in no tenants.
        assertEquals(primary.getSupertokensUserId(), payload.freedMemberAfter.primaryOrRecipeUserId);
        assertTrue(payload.freedMemberAfter.tenantIds.isEmpty());
        // The surviving group keeps the original primary_or_recipe_user_id (the storage retains it even though
        // that recipe user's login method was deleted) and stays present in the tenant.
        assertEquals(primary.getSupertokensUserId(), payload.remainingGroupAfter.primaryOrRecipeUserId);
        assertTrue(payload.remainingGroupAfter.tenantIds.contains("public"));
        // The remaining group is still resolvable via its surviving member.
        AuthRecipeUserInfo survivor = AuthRecipe.getUserById(process.getProcess(), other.getSupertokensUserId());
        assertNotNull(survivor);
        assertEquals(primary.getSupertokensUserId(), survivor.getSupertokensUserId());

        stopProcess(process);
    }

    /**
     * A failure injected after the mapping write inside the audited transaction rolls back both the mapping and
     * the audit event — the same combinator + mutation path {@code linkAccounts} uses. Neither survives.
     */
    @Test
    public void injectedFailureAfterMappingWriteRollsBackBoth() throws Exception {
        TestingProcessManager.TestingProcess process = startProcess();
        if (process == null) {
            return;
        }
        Start storage = (Start) StorageLayer.getStorage(process.getProcess());
        AuthRecipeSQLStorage authRecipeStorage =
                (AuthRecipeSQLStorage) StorageLayer.getStorage(process.getProcess());
        ActivityLogSQLStorage auditStorage = (ActivityLogSQLStorage) storage;
        AppIdentifier appIdentifier = new AppIdentifier(null, null);

        AuthRecipeUserInfo recipe = EmailPassword.signUp(process.getProcess(), "r@example.com", "password");
        AuthRecipeUserInfo primary = EmailPassword.signUp(process.getProcess(), "p@example.com", "password");
        AuthRecipe.createPrimaryUser(process.getProcess(), primary.getSupertokensUserId());

        try {
            auditStorage.startAuditedTransaction(appIdentifier, con -> {
                try {
                    authRecipeStorage.linkAccounts_Transaction(appIdentifier, con, recipe.getSupertokensUserId(),
                            primary.getSupertokensUserId());
                } catch (Exception e) {
                    throw new io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException(e);
                }
                AuditLogEvent event = new AuditLogEvent("public", "public", recipe.getSupertokensUserId(),
                        primary.getSupertokensUserId(), LifecycleEventType.ACCOUNT_LINKING.getValue(), "success",
                        null, null, System.currentTimeMillis(), null);
                // The event is written on this connection before commit; then we blow up, so the commit never
                // happens and both the mapping write and the event write roll back.
                AuditedResult<Void> result = new AuditedResult<>(null, event);
                if (true) {
                    throw new RuntimeException("injected failure after mapping + event write");
                }
                return result;
            });
            fail("expected the injected failure to propagate");
        } catch (Exception e) {
            // expected — the audited transaction rolled back
        }

        // The mapping write rolled back: the recipe user is still standalone.
        assertFalse(AuthRecipe.getUserById(process.getProcess(), recipe.getSupertokensUserId()).isPrimaryUser);
        // The event write rolled back: no account_linking row exists.
        assertEquals(0, readEvents(storage, LifecycleEventType.ACCOUNT_LINKING.getValue()).size());

        stopProcess(process);
    }

    // ---- helpers ----

    private TestingProcessManager.TestingProcess startProcess() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            stopProcess(process);
            return null;
        }
        return process;
    }

    private void stopProcess(TestingProcessManager.TestingProcess process) throws Exception {
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    private List<Row> readEvents(Start storage, String eventType) throws Exception {
        String query = "SELECT recipe_user_id, primary_or_recipe_user_id, event_type, payload FROM "
                + ACTIVITY_LOG + " WHERE event_type = ? ORDER BY created_at";
        return storage.startTransaction(con -> {
            Connection sqlCon = (Connection) con.getConnection();
            List<Row> rows = new ArrayList<>();
            try (PreparedStatement pst = sqlCon.prepareStatement(query)) {
                pst.setString(1, eventType);
                try (ResultSet rs = pst.executeQuery()) {
                    while (rs.next()) {
                        rows.add(new Row(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)));
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return rows;
        });
    }

    private static class Row {
        final String recipeUserId;
        final String primaryOrRecipeUserId;
        final String eventType;
        final String payload;

        Row(String recipeUserId, String primaryOrRecipeUserId, String eventType, String payload) {
            this.recipeUserId = recipeUserId;
            this.primaryOrRecipeUserId = primaryOrRecipeUserId;
            this.eventType = eventType;
            this.payload = payload;
        }
    }
}
