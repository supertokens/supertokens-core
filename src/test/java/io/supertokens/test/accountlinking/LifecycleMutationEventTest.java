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

import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.auditlog.lifecycle.LifecycleEventPayload;
import io.supertokens.auditlog.lifecycle.LifecycleEventType;
import io.supertokens.authRecipe.AuthRecipe;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.emailverification.EmailVerification;
import io.supertokens.passwordless.Passwordless;
import io.supertokens.thirdparty.ThirdParty;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.sqlStorage.SQLStorage;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.useridmapping.UserIdMapping;
import org.junit.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit 1 of PLAN-010: the count-affecting mutations that can be wired entirely within supertokens-core emit a
 * schema-valid lifecycle event atomically with the mutation, through
 * {@code ActivityLogSQLStorage.startAuditedTransaction}:
 *
 * <ul>
 *   <li>{@code AuthRecipe.deleteUser} — {@code user_deletion} (a member is removed, the group survives, before
 *       and after presence recorded) or {@code user_group_deletion} (the whole group is removed, before only).</li>
 *   <li>{@code Multitenancy.addUserIdToTenant} — {@code tenant_association} (group before-presence plus the
 *       tenant), emitted only when the user was not already in the tenant.</li>
 * </ul>
 *
 * <p>The {@code user_creation} and {@code tenant_disassociation} points from the same PLAN-010 unit (issue #1397)
 * are covered here too, now that the storage layer exposes the connection-taking sign-up / tenant-removal variants
 * they need (plugin-interface #216, core in-memory #1396):
 *
 * <ul>
 *   <li>the interactive sign-up paths (email-password sign-up and password-hash import, third-party sign-in-up
 *       creation, passwordless user creation, WebAuthn sign-up) each emit one {@code user_creation} event
 *       carrying the tenant, atomically with the created user;</li>
 *   <li>{@code Multitenancy.removeUserIdFromTenant} emits a {@code tenant_disassociation} event (group
 *       before-presence plus the tenant), only when a mapping was actually removed.</li>
 * </ul>
 */
public class LifecycleMutationEventTest extends MultitenantTestBase {

    private static final String ACTIVITY_LOG = "activity_log";

    // ---- user_group_deletion ----

    /**
     * Deleting a standalone (single login method) user removes the whole group: exactly one
     * {@code user_group_deletion} event with the group's before-presence and no after-list.
     */
    @Test
    public void deletingStandaloneUserEmitsGroupDeletion() throws Exception {
        TestingProcessManager.TestingProcess process = startProcess();
        if (process == null) {
            return;
        }
        SQLStorage storage = (SQLStorage) StorageLayer.getStorage(process.getProcess());

        AuthRecipeUserInfo user = EmailPassword.signUp(process.getProcess(), "u@example.com", "password");
        AuthRecipe.deleteUser(process.getProcess(), user.getSupertokensUserId(), false);

        List<Row> events = readEvents(storage, LifecycleEventType.USER_GROUP_DELETION.getValue());
        assertEquals(1, events.size());
        Row event = events.get(0);
        assertEquals(user.getSupertokensUserId(), event.recipeUserId);
        assertEquals(user.getSupertokensUserId(), event.primaryOrRecipeUserId);
        LifecycleEventPayload payload = LifecycleEventPayload.fromJson(event.payload);
        assertEquals(LifecycleEventType.USER_GROUP_DELETION, payload.type);
        assertEquals(user.getSupertokensUserId(), payload.groupBefore.primaryOrRecipeUserId);
        assertTrue(payload.groupBefore.tenantIds.contains("public"));

        stopProcess(process);
    }

    /**
     * Deleting a primary user with linked members and {@code removeAllLinkedAccounts=true} removes the whole
     * group: exactly one {@code user_group_deletion} event (not one per member).
     */
    @Test
    public void deletingLinkedGroupWithRemoveAllEmitsSingleGroupDeletion() throws Exception {
        TestingProcessManager.TestingProcess process = startProcess();
        if (process == null) {
            return;
        }
        SQLStorage storage = (SQLStorage) StorageLayer.getStorage(process.getProcess());

        AuthRecipeUserInfo primary = EmailPassword.signUp(process.getProcess(), "p@example.com", "password");
        AuthRecipeUserInfo member = EmailPassword.signUp(process.getProcess(), "m@example.com", "password");
        AuthRecipe.createPrimaryUser(process.getProcess(), primary.getSupertokensUserId());
        AuthRecipe.linkAccounts(process.getProcess(), member.getSupertokensUserId(),
                primary.getSupertokensUserId());

        AuthRecipe.deleteUser(process.getProcess(), primary.getSupertokensUserId(), true);

        List<Row> events = readEvents(storage, LifecycleEventType.USER_GROUP_DELETION.getValue());
        assertEquals(1, events.size());
        LifecycleEventPayload payload = LifecycleEventPayload.fromJson(events.get(0).payload);
        assertEquals(LifecycleEventType.USER_GROUP_DELETION, payload.type);
        assertEquals(primary.getSupertokensUserId(), payload.groupBefore.primaryOrRecipeUserId);
        // No member-level user_deletion events are emitted for a whole-group delete.
        assertEquals(0, readEvents(storage, LifecycleEventType.USER_DELETION.getValue()).size());

        stopProcess(process);
    }

    // ---- user_deletion (member removed, group survives) ----

    /**
     * Deleting one member of a linked group with {@code removeAllLinkedAccounts=false} emits a
     * {@code user_deletion} event carrying the group's before and after presence; the group survives.
     */
    @Test
    public void deletingOneMemberEmitsUserDeletion() throws Exception {
        TestingProcessManager.TestingProcess process = startProcess();
        if (process == null) {
            return;
        }
        SQLStorage storage = (SQLStorage) StorageLayer.getStorage(process.getProcess());

        AuthRecipeUserInfo primary = EmailPassword.signUp(process.getProcess(), "p@example.com", "password");
        AuthRecipeUserInfo member = EmailPassword.signUp(process.getProcess(), "m@example.com", "password");
        AuthRecipe.createPrimaryUser(process.getProcess(), primary.getSupertokensUserId());
        AuthRecipe.linkAccounts(process.getProcess(), member.getSupertokensUserId(),
                primary.getSupertokensUserId());

        // Delete just the member; the group (identified by the primary id) survives.
        AuthRecipe.deleteUser(process.getProcess(), member.getSupertokensUserId(), false);

        AuthRecipeUserInfo survivor = AuthRecipe.getUserById(process.getProcess(), primary.getSupertokensUserId());
        assertNotNull(survivor);

        List<Row> events = readEvents(storage, LifecycleEventType.USER_DELETION.getValue());
        assertEquals(1, events.size());
        Row event = events.get(0);
        assertEquals(member.getSupertokensUserId(), event.recipeUserId);
        assertEquals(primary.getSupertokensUserId(), event.primaryOrRecipeUserId);
        LifecycleEventPayload payload = LifecycleEventPayload.fromJson(event.payload);
        assertEquals(LifecycleEventType.USER_DELETION, payload.type);
        assertEquals(primary.getSupertokensUserId(), payload.groupBefore.primaryOrRecipeUserId);
        assertEquals(primary.getSupertokensUserId(), payload.groupAfter.primaryOrRecipeUserId);
        // Both members were only in "public", so the group stays in "public" after the member is removed.
        assertTrue(payload.groupBefore.tenantIds.contains("public"));
        assertTrue(payload.groupAfter.tenantIds.contains("public"));
        // No group-deletion event for a surviving group.
        assertEquals(0, readEvents(storage, LifecycleEventType.USER_GROUP_DELETION.getValue()).size());

        stopProcess(process);
    }

    /**
     * The member-deletion case the schema exists for: after-presence is NOT derivable from before-presence plus
     * the deleted member's identity. A linked group is present in {@code public} (both members) and in {@code t1}
     * (only the member being deleted). Deleting that member drops the group from {@code t1}, which the reader can
     * only know because the event records the recomputed after-list.
     */
    @Test
    public void memberDeletionRecordsShrunkAfterPresence() throws Exception {
        TestingProcessManager.TestingProcess process = startProcess();
        if (process == null) {
            return;
        }
        Main main = process.getProcess();
        initTenantIdentifiers();
        createTenants(main);
        // t1 = a1's public tenant, t2 = a1's "t1" tenant (same storage pool).
        Storage a1Storage = StorageLayer.getStorage(t1, main);
        Storage t2Storage = StorageLayer.getStorage(t2, main);

        AuthRecipeUserInfo primary = EmailPassword.signUp(t1, a1Storage, main, "p@example.com", "password");
        AuthRecipeUserInfo member = EmailPassword.signUp(t1, a1Storage, main, "m@example.com", "password");
        AuthRecipe.createPrimaryUser(main, t1.toAppIdentifier(), a1Storage, primary.getSupertokensUserId());
        AuthRecipe.linkAccounts(main, t1.toAppIdentifier(), a1Storage, member.getSupertokensUserId(),
                primary.getSupertokensUserId());

        // Put only the member into t2, so the group's presence in t2 depends solely on that member.
        assertTrue(Multitenancy.addUserIdToTenant(main, t2, t2Storage, member.getSupertokensUserId()));

        AuthRecipeUserInfo beforeDelete = AuthRecipe.getUserById(t1.toAppIdentifier(), a1Storage,
                primary.getSupertokensUserId());
        assertTrue(beforeDelete.tenantIds.contains("t1"));

        AuthRecipe.deleteUser(t1.toAppIdentifier(), a1Storage, member.getSupertokensUserId(), false, null);

        List<Row> events = readEvents((SQLStorage) a1Storage, LifecycleEventType.USER_DELETION.getValue());
        assertEquals(1, events.size());
        LifecycleEventPayload payload = LifecycleEventPayload.fromJson(events.get(0).payload);
        assertEquals(LifecycleEventType.USER_DELETION, payload.type);
        // Before: the group was in both public and t1.
        assertTrue(payload.groupBefore.tenantIds.contains("public"));
        assertTrue(payload.groupBefore.tenantIds.contains("t1"));
        // After: the deleted member was the group's only presence in t1, so the group is no longer in t1.
        assertTrue(payload.groupAfter.tenantIds.contains("public"));
        assertFalse(payload.groupAfter.tenantIds.contains("t1"));

        stopProcess(process);
    }

    // ---- no-op ----

    /**
     * Deleting a user that does not exist is a no-op and emits no lifecycle event.
     */
    @Test
    public void deletingNonexistentUserEmitsNoEvent() throws Exception {
        TestingProcessManager.TestingProcess process = startProcess();
        if (process == null) {
            return;
        }
        SQLStorage storage = (SQLStorage) StorageLayer.getStorage(process.getProcess());

        AuthRecipe.deleteUser(process.getProcess(), "00000000-0000-0000-0000-000000000000", true);

        assertEquals(0, readEvents(storage, LifecycleEventType.USER_GROUP_DELETION.getValue()).size());
        assertEquals(0, readEvents(storage, LifecycleEventType.USER_DELETION.getValue()).size());

        stopProcess(process);
    }

    // ---- user id mapping (the emitted event carries the SuperTokens recipe id, not the external id) ----

    /**
     * Deleting a standalone user through its external (mapped) id still records a {@code user_group_deletion}
     * event, and the row's {@code recipe_user_id} / {@code primary_or_recipe_user_id} are the SuperTokens id, not
     * the external id. This exercises the mapped-id resolution the delete emit path performs before capturing the
     * group snapshot; without it a mapped deletion would be silently dropped from the ledger.
     */
    @Test
    public void deletingMappedStandaloneUserEmitsGroupDeletionWithSuperTokensId() throws Exception {
        TestingProcessManager.TestingProcess process = startProcess();
        if (process == null) {
            return;
        }
        SQLStorage storage = (SQLStorage) StorageLayer.getStorage(process.getProcess());

        AuthRecipeUserInfo user = EmailPassword.signUp(process.getProcess(), "u@example.com", "password");
        String externalId = "ext-standalone";
        UserIdMapping.createUserIdMapping(process.getProcess(), user.getSupertokensUserId(), externalId, null,
                false);

        // Delete through the external id — the mapping is resolved to the SuperTokens id internally.
        AuthRecipe.deleteUser(process.getProcess(), externalId, false);

        List<Row> events = readEvents(storage, LifecycleEventType.USER_GROUP_DELETION.getValue());
        assertEquals(1, events.size());
        Row event = events.get(0);
        assertEquals(user.getSupertokensUserId(), event.recipeUserId);
        assertEquals(user.getSupertokensUserId(), event.primaryOrRecipeUserId);
        LifecycleEventPayload payload = LifecycleEventPayload.fromJson(event.payload);
        assertEquals(LifecycleEventType.USER_GROUP_DELETION, payload.type);
        assertEquals(user.getSupertokensUserId(), payload.groupBefore.primaryOrRecipeUserId);
        assertTrue(payload.groupBefore.tenantIds.contains("public"));

        stopProcess(process);
    }

    /**
     * Deleting one linked member through its external (mapped) id emits a {@code user_deletion} event whose
     * {@code recipe_user_id} is the freed member's SuperTokens id (from {@code userIdMapping.superTokensUserId}),
     * not the external id, while {@code primary_or_recipe_user_id} stays the surviving group's primary id.
     */
    @Test
    public void deletingMappedMemberEmitsUserDeletionWithSuperTokensRecipeId() throws Exception {
        TestingProcessManager.TestingProcess process = startProcess();
        if (process == null) {
            return;
        }
        SQLStorage storage = (SQLStorage) StorageLayer.getStorage(process.getProcess());

        AuthRecipeUserInfo primary = EmailPassword.signUp(process.getProcess(), "p@example.com", "password");
        AuthRecipeUserInfo member = EmailPassword.signUp(process.getProcess(), "m@example.com", "password");
        String memberExternalId = "ext-member";
        UserIdMapping.createUserIdMapping(process.getProcess(), member.getSupertokensUserId(), memberExternalId,
                null, false);
        AuthRecipe.createPrimaryUser(process.getProcess(), primary.getSupertokensUserId());
        AuthRecipe.linkAccounts(process.getProcess(), member.getSupertokensUserId(),
                primary.getSupertokensUserId());

        // Delete just the mapped member through its external id; the group (the primary) survives.
        AuthRecipe.deleteUser(process.getProcess(), memberExternalId, false);

        AuthRecipeUserInfo survivor = AuthRecipe.getUserById(process.getProcess(), primary.getSupertokensUserId());
        assertNotNull(survivor);

        List<Row> events = readEvents(storage, LifecycleEventType.USER_DELETION.getValue());
        assertEquals(1, events.size());
        Row event = events.get(0);
        // The emitted recipe id is the member's SuperTokens id, never the external id.
        assertEquals(member.getSupertokensUserId(), event.recipeUserId);
        assertFalse(memberExternalId.equals(event.recipeUserId));
        assertEquals(primary.getSupertokensUserId(), event.primaryOrRecipeUserId);
        LifecycleEventPayload payload = LifecycleEventPayload.fromJson(event.payload);
        assertEquals(LifecycleEventType.USER_DELETION, payload.type);
        assertEquals(primary.getSupertokensUserId(), payload.groupBefore.primaryOrRecipeUserId);
        assertEquals(primary.getSupertokensUserId(), payload.groupAfter.primaryOrRecipeUserId);
        assertEquals(0, readEvents(storage, LifecycleEventType.USER_GROUP_DELETION.getValue()).size());

        stopProcess(process);
    }

    // ---- tenant_association ----

    /**
     * Associating a user with a new tenant emits exactly one {@code tenant_association} event carrying the
     * group's before-presence and the tenant it was added to; associating again is a no-op that emits nothing.
     */
    @Test
    public void associatingUserWithTenantEmitsEvent() throws Exception {
        TestingProcessManager.TestingProcess process = startProcess();
        if (process == null) {
            return;
        }
        Main main = process.getProcess();
        initTenantIdentifiers();
        createTenants(main);
        Storage a1Storage = StorageLayer.getStorage(t1, main);
        Storage t2Storage = StorageLayer.getStorage(t2, main);

        AuthRecipeUserInfo user = EmailPassword.signUp(t1, a1Storage, main, "u@example.com", "password");

        boolean added = Multitenancy.addUserIdToTenant(main, t2, t2Storage, user.getSupertokensUserId());
        assertTrue(added);

        List<Row> events = readEvents((SQLStorage) a1Storage, LifecycleEventType.TENANT_ASSOCIATION.getValue());
        assertEquals(1, events.size());
        Row event = events.get(0);
        assertEquals(user.getSupertokensUserId(), event.recipeUserId);
        assertEquals(user.getSupertokensUserId(), event.primaryOrRecipeUserId);
        LifecycleEventPayload payload = LifecycleEventPayload.fromJson(event.payload);
        assertEquals(LifecycleEventType.TENANT_ASSOCIATION, payload.type);
        assertEquals("t1", payload.tenantId);
        assertEquals(user.getSupertokensUserId(), payload.groupBefore.primaryOrRecipeUserId);
        // Before the association the user was only in a1's public tenant.
        assertTrue(payload.groupBefore.tenantIds.contains("public"));
        assertFalse(payload.groupBefore.tenantIds.contains("t1"));

        // Associating again is a no-op — still exactly one event.
        boolean addedAgain = Multitenancy.addUserIdToTenant(main, t2, t2Storage, user.getSupertokensUserId());
        assertFalse(addedAgain);
        assertEquals(1, readEvents((SQLStorage) a1Storage, LifecycleEventType.TENANT_ASSOCIATION.getValue()).size());

        stopProcess(process);
    }

    // ---- user_creation ----

    /**
     * An email-password sign-up emits exactly one {@code user_creation} event: the row's recipe and group ids are
     * the new user's SuperTokens id and the payload carries the tenant it was created in.
     */
    @Test
    public void emailPasswordSignUpEmitsUserCreation() throws Exception {
        TestingProcessManager.TestingProcess process = startProcess();
        if (process == null) {
            return;
        }
        SQLStorage storage = (SQLStorage) StorageLayer.getStorage(process.getProcess());

        AuthRecipeUserInfo user = EmailPassword.signUp(process.getProcess(), "u@example.com", "password");

        List<Row> events = readEvents(storage, LifecycleEventType.USER_CREATION.getValue());
        assertEquals(1, events.size());
        Row event = events.get(0);
        assertEquals(user.getSupertokensUserId(), event.recipeUserId);
        assertEquals(user.getSupertokensUserId(), event.primaryOrRecipeUserId);
        LifecycleEventPayload payload = LifecycleEventPayload.fromJson(event.payload);
        assertEquals(LifecycleEventType.USER_CREATION, payload.type);
        assertEquals("public", payload.tenantId);

        stopProcess(process);
    }

    /**
     * A fake-email sign-up is pre-verified on the same audited connection as the user creation (the fake-email
     * branch folded onto the audited transaction). This pins both halves of that path: the user still comes out
     * verified, and exactly one {@code user_creation} event is emitted.
     */
    @Test
    public void fakeEmailSignUpVerifiesAndEmitsUserCreation() throws Exception {
        TestingProcessManager.TestingProcess process = startProcess();
        if (process == null) {
            return;
        }
        SQLStorage storage = (SQLStorage) StorageLayer.getStorage(process.getProcess());

        String fakeEmail = "st-user@stfakeemail.supertokens.com";
        AuthRecipeUserInfo user = EmailPassword.signUp(process.getProcess(), fakeEmail, "password");

        // The fake email is pre-verified in the same transaction that created the user.
        assertTrue(EmailVerification.isEmailVerified(process.getProcess(), user.getSupertokensUserId(), fakeEmail));

        List<Row> events = readEvents(storage, LifecycleEventType.USER_CREATION.getValue());
        assertEquals(1, events.size());
        Row event = events.get(0);
        assertEquals(user.getSupertokensUserId(), event.recipeUserId);
        LifecycleEventPayload payload = LifecycleEventPayload.fromJson(event.payload);
        assertEquals(LifecycleEventType.USER_CREATION, payload.type);
        assertEquals("public", payload.tenantId);

        stopProcess(process);
    }

    /**
     * Importing an email-password user by password hash (the CDI import-with-password-hash API) emits a
     * {@code user_import} event, not {@code user_creation}: like bulk import it counts toward user totals but
     * is excluded from the last-active rollup / MAU fold ({@code user_import} is not in the fold set), so an
     * import never inflates the active-user count.
     */
    @Test
    public void importUserWithPasswordHashEmitsUserImport() throws Exception {
        TestingProcessManager.TestingProcess process = startProcess();
        if (process == null) {
            return;
        }
        SQLStorage storage = (SQLStorage) StorageLayer.getStorage(process.getProcess());

        // A bcrypt hash is auto-detected (hashingAlgorithm = null).
        String bcryptHash = "$2a$10$GzEm3vKoAqnJCTWesRARCe/ovjt/07qjvcH9jbLUg44Fn77gMZkmm";
        EmailPassword.ImportUserResponse response = EmailPassword.importUserWithPasswordHash(process.getProcess(),
                "import@example.com", bcryptHash, null);
        assertFalse(response.didUserAlreadyExist);

        List<Row> imports = readEvents(storage, LifecycleEventType.USER_IMPORT.getValue());
        assertEquals(1, imports.size());
        Row event = imports.get(0);
        assertEquals(response.user.getSupertokensUserId(), event.recipeUserId);
        assertEquals(response.user.getSupertokensUserId(), event.primaryOrRecipeUserId);
        LifecycleEventPayload payload = LifecycleEventPayload.fromJson(event.payload);
        assertEquals(LifecycleEventType.USER_IMPORT, payload.type);
        assertEquals("public", payload.tenantId);

        // And crucially it does NOT emit user_creation (which is in the fold set and would count toward MAU).
        assertEquals(0, readEvents(storage, LifecycleEventType.USER_CREATION.getValue()).size());

        stopProcess(process);
    }

    /**
     * A third-party sign-in-up emits a {@code user_creation} event only when it creates a new user: signing in
     * again with the same third-party identity is a sign-in, not a creation, so no further event is emitted.
     */
    @Test
    public void thirdPartySignInUpEmitsUserCreationForNewUserOnly() throws Exception {
        TestingProcessManager.TestingProcess process = startProcess();
        if (process == null) {
            return;
        }
        SQLStorage storage = (SQLStorage) StorageLayer.getStorage(process.getProcess());

        ThirdParty.SignInUpResponse first = ThirdParty.signInUp(process.getProcess(), "google", "tp-user-1",
                "tp@example.com");
        assertTrue(first.createdNewUser);

        List<Row> events = readEvents(storage, LifecycleEventType.USER_CREATION.getValue());
        assertEquals(1, events.size());
        Row event = events.get(0);
        assertEquals(first.user.getSupertokensUserId(), event.recipeUserId);
        LifecycleEventPayload payload = LifecycleEventPayload.fromJson(event.payload);
        assertEquals(LifecycleEventType.USER_CREATION, payload.type);
        assertEquals("public", payload.tenantId);

        // Signing in again with the same third-party identity does not create a user — still exactly one event.
        ThirdParty.SignInUpResponse second = ThirdParty.signInUp(process.getProcess(), "google", "tp-user-1",
                "tp@example.com");
        assertFalse(second.createdNewUser);
        assertEquals(1, readEvents(storage, LifecycleEventType.USER_CREATION.getValue()).size());

        stopProcess(process);
    }

    /**
     * Creating a passwordless user emits a {@code user_creation} event carrying the tenant it was created in.
     */
    @Test
    public void passwordlessUserCreationEmitsEvent() throws Exception {
        TestingProcessManager.TestingProcess process = startProcess();
        if (process == null) {
            return;
        }
        Main main = process.getProcess();
        SQLStorage storage = (SQLStorage) StorageLayer.getStorage(main);

        AuthRecipeUserInfo user = Passwordless.createPasswordlessUser(new TenantIdentifier(null, null, null),
                storage, "pless@example.com", null, System.currentTimeMillis());

        List<Row> events = readEvents(storage, LifecycleEventType.USER_CREATION.getValue());
        assertEquals(1, events.size());
        Row event = events.get(0);
        assertEquals(user.getSupertokensUserId(), event.recipeUserId);
        assertEquals(user.getSupertokensUserId(), event.primaryOrRecipeUserId);
        LifecycleEventPayload payload = LifecycleEventPayload.fromJson(event.payload);
        assertEquals(LifecycleEventType.USER_CREATION, payload.type);
        assertEquals("public", payload.tenantId);

        stopProcess(process);
    }

    /**
     * A WebAuthn sign-up emits exactly one {@code user_creation} event carrying the tenant it was created in.
     * WebAuthn is the one converted path where the emit lands inside the pre-existing {@code while(true)} retry
     * loop of the audited transaction, so it gets its own assertion like the other recipes. Driven through the
     * HTTP sign-up flow (register options + passkey) because that is the only way to produce a valid credential.
     */
    @Test
    public void webAuthnSignUpEmitsUserCreation() throws Exception {
        TestingProcessManager.TestingProcess process = startProcess();
        if (process == null) {
            return;
        }
        SQLStorage storage = (SQLStorage) StorageLayer.getStorage(process.getProcess());

        JsonObject signUpResponse = io.supertokens.test.webauthn.Utils.registerUserWithCredentials(
                process.getProcess(), "webauthn@example.com");
        assertEquals("OK", signUpResponse.get("status").getAsString());
        String recipeUserId = signUpResponse.get("recipeUserId").getAsString();

        List<Row> events = readEvents(storage, LifecycleEventType.USER_CREATION.getValue());
        assertEquals(1, events.size());
        Row event = events.get(0);
        assertEquals(recipeUserId, event.recipeUserId);
        assertEquals(recipeUserId, event.primaryOrRecipeUserId);
        LifecycleEventPayload payload = LifecycleEventPayload.fromJson(event.payload);
        assertEquals(LifecycleEventType.USER_CREATION, payload.type);
        assertEquals("public", payload.tenantId);

        stopProcess(process);
    }

    // ---- tenant_disassociation ----

    /**
     * Removing a user from a tenant emits exactly one {@code tenant_disassociation} event carrying the group's
     * before-presence and the tenant it was removed from; removing again is a no-op that emits nothing.
     */
    @Test
    public void disassociatingUserFromTenantEmitsEvent() throws Exception {
        TestingProcessManager.TestingProcess process = startProcess();
        if (process == null) {
            return;
        }
        Main main = process.getProcess();
        initTenantIdentifiers();
        createTenants(main);
        Storage a1Storage = StorageLayer.getStorage(t1, main);
        Storage t2Storage = StorageLayer.getStorage(t2, main);

        AuthRecipeUserInfo user = EmailPassword.signUp(t1, a1Storage, main, "u@example.com", "password");
        assertTrue(Multitenancy.addUserIdToTenant(main, t2, t2Storage, user.getSupertokensUserId()));

        // Now the group is in both "public" and "t1"; remove it from "t1".
        boolean removed = Multitenancy.removeUserIdFromTenant(main, t2, t2Storage, user.getSupertokensUserId(),
                null);
        assertTrue(removed);

        List<Row> events = readEvents((SQLStorage) a1Storage, LifecycleEventType.TENANT_DISASSOCIATION.getValue());
        assertEquals(1, events.size());
        Row event = events.get(0);
        assertEquals(user.getSupertokensUserId(), event.recipeUserId);
        assertEquals(user.getSupertokensUserId(), event.primaryOrRecipeUserId);
        LifecycleEventPayload payload = LifecycleEventPayload.fromJson(event.payload);
        assertEquals(LifecycleEventType.TENANT_DISASSOCIATION, payload.type);
        assertEquals("t1", payload.tenantId);
        assertEquals(user.getSupertokensUserId(), payload.groupBefore.primaryOrRecipeUserId);
        // Before the disassociation the group was present in both public and t1.
        assertTrue(payload.groupBefore.tenantIds.contains("public"));
        assertTrue(payload.groupBefore.tenantIds.contains("t1"));

        // Removing again is a no-op — still exactly one event.
        boolean removedAgain = Multitenancy.removeUserIdFromTenant(main, t2, t2Storage,
                user.getSupertokensUserId(), null);
        assertFalse(removedAgain);
        assertEquals(1, readEvents((SQLStorage) a1Storage, LifecycleEventType.TENANT_DISASSOCIATION.getValue()).size());

        stopProcess(process);
    }

    /**
     * Removing a user from a tenant it was never associated with emits no {@code tenant_disassociation} event.
     */
    @Test
    public void disassociatingUserNotInTenantEmitsNothing() throws Exception {
        TestingProcessManager.TestingProcess process = startProcess();
        if (process == null) {
            return;
        }
        Main main = process.getProcess();
        initTenantIdentifiers();
        createTenants(main);
        Storage a1Storage = StorageLayer.getStorage(t1, main);
        Storage t2Storage = StorageLayer.getStorage(t2, main);

        // User exists only in a1's public tenant, never added to t2 ("t1").
        AuthRecipeUserInfo user = EmailPassword.signUp(t1, a1Storage, main, "u@example.com", "password");

        boolean removed = Multitenancy.removeUserIdFromTenant(main, t2, t2Storage, user.getSupertokensUserId(),
                null);
        assertFalse(removed);
        assertEquals(0, readEvents((SQLStorage) a1Storage, LifecycleEventType.TENANT_DISASSOCIATION.getValue()).size());

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

    private List<Row> readEvents(SQLStorage storage, String eventType) throws Exception {
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
