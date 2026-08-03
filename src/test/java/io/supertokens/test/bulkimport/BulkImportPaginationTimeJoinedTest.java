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

import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.authRecipe.AuthRecipe;
import io.supertokens.authRecipe.UserPaginationContainer;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.sqlStorage.SQLStorage;
import io.supertokens.pluginInterface.sqlStorage.TransactionConnection;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Reproduces the pagination failure from issue #1347 at the level of the DB state that bulk import
 * leaves behind, and verifies that {@link AuthRecipe#updateTimeJoinedForBulkImportedPrimaryUsers}
 * — the normalization step that BulkImport.processUsersImportSteps runs after inserting all login
 * methods — restores the pagination invariant.
 *
 * <p>Bulk import inserts every login-method row with its own time_joined into
 * primary_or_recipe_user_time_joined. For a linked group whose members have divergent per-method
 * time_joined this leaves several distinct primary_or_recipe_user_time_joined values in one group,
 * which is exactly the state simulated here via a targeted UPDATE (the same shape as the buggy
 * insert). The end-to-end bulk-import variant (which drives the cron/proxy-storage path and is
 * therefore Postgres-only) lives in BulkImportFlowTest#testPaginationWalksFullyAfterLinkedImport.
 */
public class BulkImportPaginationTimeJoinedTest {
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

    @Test
    public void testNormalizationFixesTruncatedPaginationOnDivergentLinkedTimes() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        Main main = process.getProcess();
        Storage storage = StorageLayer.getStorage(main);
        AppIdentifier appIdentifier = process.getAppForTesting().toAppIdentifier();

        // Create a linked group (memberLow + memberHigh) plus standalone filler users. The two group
        // members are given widely divergent times, with filler users landing BETWEEN them — the exact
        // shape that makes a page boundary fall inside the linked group.
        AuthRecipeUserInfo memberLow = EmailPassword.signUp(main, "low@example.com", "password");
        AuthRecipeUserInfo memberHigh = EmailPassword.signUp(main, "high@example.com", "password");
        AuthRecipe.createPrimaryUser(main, memberHigh.getSupertokensUserId());
        AuthRecipe.linkAccounts(main, memberLow.getSupertokensUserId(), memberHigh.getSupertokensUserId());
        String primaryUserId = memberHigh.getSupertokensUserId();

        AuthRecipeUserInfo newer1 = EmailPassword.signUp(main, "newer1@example.com", "password");
        AuthRecipeUserInfo newer2 = EmailPassword.signUp(main, "newer2@example.com", "password");

        List<String> fillerIds = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            fillerIds.add(EmailPassword.signUp(main, "filler" + i + "@example.com", "password")
                    .getSupertokensUserId());
        }

        // Assign deterministic times and reproduce the post-bulk-import state:
        //   - filler/standalone rows: primary_or_recipe_user_time_joined == time_joined (a group of one)
        //   - the linked group: each member row carries its OWN time_joined in
        //     primary_or_recipe_user_time_joined (the bulk-import bug), so the group holds two distinct
        //     values (1_000 and 9_000) rather than the group MIN.
        setTimes(storage, newer1.getSupertokensUserId(), 11_000L, 11_000L);
        setTimes(storage, newer2.getSupertokensUserId(), 10_000L, 10_000L);
        setTimes(storage, memberHigh.getSupertokensUserId(), 9_000L, 9_000L);
        long fillerTime = 8_000L;
        for (String fillerId : fillerIds) {
            setTimes(storage, fillerId, fillerTime, fillerTime);
            fillerTime -= 1_000L;
        }
        setTimes(storage, memberLow.getSupertokensUserId(), 1_000L, 1_000L);

        // The complete population is 10 primary users: newer1, newer2, the 7 fillers, and the linked
        // group (represented by its primary user). memberLow is a member of the group, not a separate row.
        Set<String> expected = new HashSet<>();
        expected.add(newer1.getSupertokensUserId());
        expected.add(newer2.getSupertokensUserId());
        expected.addAll(fillerIds);
        expected.add(primaryUserId);

        // Before the fix: the DESC walk truncates — the +1 boundary row lands on the group's high-time
        // member (9_000) but the next-page token is built from the group's Java-side MIN (1_000), so the
        // cursor skips forward past every filler between 1_000 and 9_000.
        WalkResult before = walk(main, "DESC", 2);
        assertTrue("expected the DESC walk to truncate before the fix, but it visited "
                        + before.visited.size() + " of " + expected.size() + " users",
                before.visited.size() < expected.size());

        // Apply the normalization step that the bulk-import path now runs.
        AuthRecipe.updateTimeJoinedForBulkImportedPrimaryUsers(storage, appIdentifier,
                List.of(primaryUserId));

        // getUserById reports the group MIN either way (it is a Java-side computation), but the fix aligns
        // the stored cursor column with it so pagination is consistent.
        assertEquals(1_000L, AuthRecipe.getUserById(main, primaryUserId).timeJoined);

        // After the fix: both directions walk the full population exactly once, and the ASC walk does not
        // cycle.
        WalkResult afterDesc = walk(main, "DESC", 2);
        assertEquals("DESC walk cycled after the fix", false, afterDesc.cycled);
        assertEquals(expected, new HashSet<>(afterDesc.visited));
        assertEquals("DESC walk visited a user more than once", expected.size(), afterDesc.visited.size());

        WalkResult afterAsc = walk(main, "ASC", 2);
        assertEquals("ASC walk cycled after the fix", false, afterAsc.cycled);
        assertEquals(expected, new HashSet<>(afterAsc.visited));
        assertEquals("ASC walk visited a user more than once", expected.size(), afterAsc.visited.size());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    private static class WalkResult {
        final List<String> visited;
        final boolean cycled;

        WalkResult(List<String> visited, boolean cycled) {
            this.visited = visited;
            this.cycled = cycled;
        }
    }

    // Follows the pagination token chain to completion, guarding against the ASC non-termination case
    // with a repeated-token check and a hard page cap.
    private static WalkResult walk(Main main, String order, int limit) throws Exception {
        List<String> visited = new ArrayList<>();
        Set<String> seenTokens = new HashSet<>();
        String token = null;
        int pages = 0;
        while (true) {
            UserPaginationContainer page = AuthRecipe.getUsers(main, limit, order, token, null, null);
            for (AuthRecipeUserInfo u : page.users) {
                visited.add(u.getSupertokensUserId());
            }
            token = page.nextPaginationToken;
            if (token == null) {
                return new WalkResult(visited, false);
            }
            if (!seenTokens.add(token) || ++pages > 1000) {
                return new WalkResult(visited, true);
            }
        }
    }

    // Sets time_joined and primary_or_recipe_user_time_joined for a single user_id in both tables that
    // back user pagination.
    private static void setTimes(Storage storage, String userId, long timeJoined, long primaryOrRecipeTimeJoined)
            throws Exception {
        SQLStorage sqlStorage = (SQLStorage) storage;
        sqlStorage.startTransaction(con -> {
            Connection sqlCon = (Connection) con.getConnection();
            try {
                for (String table : new String[]{"all_auth_recipe_users", "app_id_to_user_id"}) {
                    String query = "UPDATE " + table
                            + " SET time_joined = ?, primary_or_recipe_user_time_joined = ? WHERE user_id = ?";
                    try (PreparedStatement pst = sqlCon.prepareStatement(query)) {
                        pst.setLong(1, timeJoined);
                        pst.setLong(2, primaryOrRecipeTimeJoined);
                        pst.setString(3, userId);
                        pst.executeUpdate();
                    }
                }
                // Keep the login-method (recipe-table) time_joined equal to the row's time_joined, exactly
                // as bulk import does — the next-page token time is the Java-side MIN over these values.
                try (PreparedStatement pst = sqlCon.prepareStatement(
                        "UPDATE emailpassword_users SET time_joined = ? WHERE user_id = ?")) {
                    pst.setLong(1, timeJoined);
                    pst.setString(2, userId);
                    pst.executeUpdate();
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return null;
        });
    }
}
