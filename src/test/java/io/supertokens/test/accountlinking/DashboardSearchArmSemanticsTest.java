/*
 *    Copyright (c) 2025, VRAI Labs and/or its affiliates. All rights reserved.
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
import io.supertokens.authRecipe.AuthRecipe;
import io.supertokens.authRecipe.UserPaginationContainer;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.passwordless.Passwordless;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.dashboard.DashboardSearchTags;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.thirdparty.ThirdParty;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Locks the semantics of the dashboard user-search arms in the in-memory (SQLite) storage's
 * {@code GeneralQueries.getUsers_new} against the postgresql plugin's arm rewrite
 * (supertokens-postgresql-plugin#373): each arm is a case-appropriate prefix match, and the email
 * arm's second clause searches the <b>domain prefix</b> (not a {@code %@q%} contains). Both storages
 * must return identical result sets for the same {@link DashboardSearchTags} so the two CI legs
 * validate the same behaviour; the postgres side asserts these same expectations in
 * {@code DashboardSearchSargabilityTest.testDashboardSearchSemanticsPreserved}.
 *
 * <p>This test runs only on the in-memory storage (the SQLite CI leg). The in-memory
 * {@code SQLiteConfig.getMigrationMode()} is hardcoded to {@code MIGRATED}, so {@code getUsers}
 * always routes through {@code getUsers_new} (the new-tables path) here; on the postgres matrix the
 * default {@code LEGACY} mode exercises a different code path, and the plugin's own suite covers its
 * {@code getUsers_new}.
 */
public class DashboardSearchArmSemanticsTest {
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
    public void testDashboardSearchArmSemanticsPreserved() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }
        // Only the in-memory (SQLite) storage hardcodes MIGRATED and therefore reads via
        // getUsers_new by default; on postgres the default LEGACY mode reads the old per-recipe
        // tables, so this new-tables-semantics test targets the SQLite leg only.
        if (!StorageLayer.isInMemDb(process.getProcess())) {
            return;
        }

        // Email-password user: normalized email "john@gmail.com" (lower-cased at write time).
        AuthRecipeUserInfo emailUser = EmailPassword.signUp(process.getProcess(), "john@gmail.com", "password");
        // Passwordless user: phone only.
        Passwordless.CreateCodeResponse code = Passwordless.createCode(process.getProcess(), null, "+919876543210",
                null, null);
        AuthRecipeUserInfo phoneUser = Passwordless.consumeCode(process.getProcess(), code.deviceId,
                code.deviceIdHash, code.userInputCode, null).user;
        // Third-party user: provider value stored as thirdPartyId + "::" + thirdPartyUserId =
        // "google::USER-XyZ" (NOT lower-normalized), plus its own email row "tp@thirdparty.io".
        AuthRecipeUserInfo tpUser = ThirdParty.signInUp(process.getProcess(), "google", "USER-XyZ",
                "tp@thirdparty.io").user;

        String emailId = emailUser.getSupertokensUserId();
        String phoneId = phoneUser.getSupertokensUserId();
        String tpId = tpUser.getSupertokensUserId();

        // ---- Email local-part prefix: matches the value arm (bare-column prefix). ----
        assertEquals(Set.of(emailId), search(process, new DashboardSearchTags(List.of("john"), null, null)));

        // ---- Email domain prefix: matches the domain arm (substr after '@'), not the local part. ----
        assertEquals(Set.of(emailId), search(process, new DashboardSearchTags(List.of("gmail"), null, null)));
        // The third-party user's own email "tp@thirdparty.io" is found by its domain prefix too.
        assertEquals(Set.of(tpId), search(process, new DashboardSearchTags(List.of("thirdparty"), null, null)));

        // ---- Domain-mid substring must NOT match: proves domain PREFIX, not a contains match. ----
        // "gmail.com" contains "mail" mid-string, but neither the value arm ("john@gmail.com" does not
        // start with "mail") nor the domain arm ("gmail.com" does not start with "mail") matches it.
        assertEquals(Set.of(), search(process, new DashboardSearchTags(List.of("mail"), null, null)));

        // ---- Phone prefix: matches; a non-prefix digit run does not (prefix, not contains). ----
        assertEquals(Set.of(phoneId), search(process, new DashboardSearchTags(null, List.of("+919876"), null)));
        assertEquals(Set.of(), search(process, new DashboardSearchTags(null, List.of("9876"), null)));

        // ---- Provider arm: case-insensitive prefix over the un-normalized "google::USER-XyZ". ----
        // A lower-cased search of the mixed-case stored value matches (arm lower()s both sides)...
        assertEquals(Set.of(tpId),
                search(process, new DashboardSearchTags(null, null, List.of("google::user-xyz"))));
        // ...as does a mixed-case prefix...
        assertEquals(Set.of(tpId),
                search(process, new DashboardSearchTags(null, null, List.of("GOOGLE::USER"))));
        // ...and a bare provider-id prefix, while a non-prefix substring does not.
        assertEquals(Set.of(tpId), search(process, new DashboardSearchTags(null, null, List.of("google"))));
        assertEquals(Set.of(), search(process, new DashboardSearchTags(null, null, List.of("oogle"))));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    private static Set<String> search(TestingProcessManager.TestingProcess process, DashboardSearchTags tags)
            throws Exception {
        UserPaginationContainer container = AuthRecipe.getUsers(process.getProcess(), 100, "DESC", null, null, tags);
        Set<String> ids = new HashSet<>();
        for (AuthRecipeUserInfo user : container.users) {
            ids.add(user.getSupertokensUserId());
        }
        return ids;
    }
}
