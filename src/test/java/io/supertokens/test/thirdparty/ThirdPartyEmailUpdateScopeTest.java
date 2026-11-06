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

package io.supertokens.test.thirdparty;

import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.authRecipe.AuthRecipe;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.emailverification.EmailVerification;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.authRecipe.LoginMethod;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.EmailPasswordConfig;
import io.supertokens.pluginInterface.multitenancy.PasswordlessConfig;
import io.supertokens.pluginInterface.multitenancy.TenantConfig;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.ThirdPartyConfig;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.thirdparty.ThirdParty;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

/**
 * A third party sign in must only change the recipe user of the signing-in tenant, and must only mark as verified
 * the email that the caller said was verified.
 */
public class ThirdPartyEmailUpdateScopeTest {

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

    private static final AppIdentifier APP = new AppIdentifier(null, null);
    private static final TenantIdentifier T1 = new TenantIdentifier(null, null, "t1");
    private static final TenantIdentifier T2 = new TenantIdentifier(null, null, "t2");

    private static void createTenant(Main main, TenantIdentifier tenant) throws Exception {
        Multitenancy.addNewOrUpdateAppOrTenant(main, new TenantIdentifier(null, null, null),
                new TenantConfig(tenant, new EmailPasswordConfig(true), new ThirdPartyConfig(true, null),
                        new PasswordlessConfig(true), null, null, new JsonObject()));
    }

    private static LoginMethod thirdPartyLoginMethod(Storage storage, String recipeUserId) throws Exception {
        AuthRecipeUserInfo user = AuthRecipe.getUserById(APP, storage, recipeUserId);
        assertNotNull(user);
        for (LoginMethod lM : user.loginMethods) {
            if (lM.getSupertokensUserId().equals(recipeUserId)) {
                return lM;
            }
        }
        fail("login method not found");
        return null;
    }

    private static TestingProcessManager.TestingProcess startWithLinking() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        return process;
    }

    @Test
    public void testSignInPicksTheUserWithThisTenantsLoginMethodNotAPrimaryUserOfTheTenant() throws Exception {
        TestingProcessManager.TestingProcess process = startWithLinking();
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            process.kill();
            return;
        }
        Main main = process.getProcess();
        createTenant(main, T1);
        createTenant(main, T2);
        Storage storage = StorageLayer.getStorage(T1, main);

        // identity X is primary user P in t1, and P becomes a member of t2 through a linked email-password user
        ThirdParty.SignInUpResponse p = ThirdParty.signInUp(T1, storage, main, "oidc", "sub-x", "x@example.com",
                false);
        String primaryId = p.user.getSupertokensUserId();
        AuthRecipe.createPrimaryUser(main, APP, storage, primaryId);
        AuthRecipeUserInfo ep = EmailPassword.signUp(T2, storage, main, "other@example.com", "password123");
        AuthRecipe.linkAccounts(main, APP, storage, ep.getSupertokensUserId(), primaryId);

        // X signs in at t2: a separate user U, and every later sign in at t2 must keep finding U (not fail on P)
        ThirdParty.SignInUpResponse u = ThirdParty.signInUp(T2, storage, main, "oidc", "sub-x", "x2@example.com",
                false);
        assertTrue(u.createdNewUser);
        String uId = u.user.getSupertokensUserId();
        for (String email : new String[]{"x2@example.com", "x2-new@example.com"}) {
            ThirdParty.SignInUpResponse again = ThirdParty.signInUp(T2, storage, main, "oidc", "sub-x", email, false);
            assertFalse(again.createdNewUser);
            assertEquals(uId, again.user.getSupertokensUserId());
        }
        assertEquals("x2-new@example.com", thirdPartyLoginMethod(storage, uId).email);
        assertEquals("x@example.com", thirdPartyLoginMethod(storage, primaryId).email);

        process.kill();
    }

    @Test
    public void testSignInUpdatesOnlyThisTenantsLinkedRecipeUserOfTheSameIdentity() throws Exception {
        TestingProcessManager.TestingProcess process = startWithLinking();
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            process.kill();
            return;
        }
        Main main = process.getProcess();
        createTenant(main, T1);
        createTenant(main, T2);
        Storage storage = StorageLayer.getStorage(T1, main);

        // R1 (identity X, t1) is primary, R2 (identity X, t2) is linked to it
        String r1 = ThirdParty.signInUp(T1, storage, main, "oidc", "sub-y", "y1@example.com", false)
                .user.getSupertokensUserId();
        AuthRecipe.createPrimaryUser(main, APP, storage, r1);
        String r2 = ThirdParty.signInUp(T2, storage, main, "oidc", "sub-y", "y2@example.com", false)
                .user.getSupertokensUserId();
        AuthRecipe.linkAccounts(main, APP, storage, r2, r1);

        ThirdParty.SignInUpResponse res = ThirdParty.signInUp(T2, storage, main, "oidc", "sub-y",
                "y2-new@example.com", true);
        assertFalse(res.createdNewUser);
        assertEquals(r1, res.user.getSupertokensUserId());
        assertEquals("y2-new@example.com", thirdPartyLoginMethod(storage, r2).email);
        assertEquals("y1@example.com", thirdPartyLoginMethod(storage, r1).email);
        assertTrue(EmailVerification.isEmailVerified(APP, storage, r2, "y2-new@example.com"));
        assertFalse(EmailVerification.isEmailVerified(APP, storage, r1, "y2-new@example.com"));

        process.kill();
    }

    @Test
    public void testIssue106_signInInOneTenantDoesNotRewriteOtherTenantsUser() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            process.kill();
            return;
        }

        Main main = process.getProcess();
        createTenant(main, T1);
        createTenant(main, T2);
        Storage storage = StorageLayer.getStorage(T1, main);

        // the same provider identity signs in to two tenants: one recipe user per tenant
        ThirdParty.SignInUpResponse r1 = ThirdParty.signInUp(T1, storage, main, "oidc", "sub-123",
                "user1@example.com", true);
        assertTrue(r1.createdNewUser);
        String user1Id = r1.user.getSupertokensUserId();
        AuthRecipe.createPrimaryUser(main, APP, storage, user1Id);

        ThirdParty.SignInUpResponse r2 = ThirdParty.signInUp(T2, storage, main, "oidc", "sub-123",
                "user2@example.com", true);
        assertTrue(r2.createdNewUser);
        String user2Id = r2.user.getSupertokensUserId();
        assertNotEquals(user1Id, user2Id);

        // a sign in on t2 with a changed email only updates t2's user
        ThirdParty.SignInUpResponse r2b = ThirdParty.signInUp(T2, storage, main, "oidc", "sub-123",
                "user2-new@example.com", false);
        assertFalse(r2b.createdNewUser);
        assertEquals(user2Id, r2b.user.getSupertokensUserId());
        assertEquals("user2-new@example.com", thirdPartyLoginMethod(storage, user2Id).email);

        LoginMethod user1LM = thirdPartyLoginMethod(storage, user1Id);
        assertEquals("user1@example.com", user1LM.email);
        assertTrue(user1LM.verified);
        AuthRecipeUserInfo[] t1ByOldEmail = AuthRecipe.getUsersByAccountInfo(T1, storage, false,
                "user1@example.com", null, null, null, null);
        assertEquals(1, t1ByOldEmail.length);
        assertEquals(user1Id, t1ByOldEmail[0].getSupertokensUserId());
        assertEquals(0, AuthRecipe.getUsersByAccountInfo(T1, storage, false,
                "user2-new@example.com", null, null, null, null).length);

        // and the other way around: a sign in on t1 with a changed email does not reach t2's user
        ThirdParty.signInUp(T1, storage, main, "oidc", "sub-123", "user1-new@example.com", true);
        assertEquals("user1-new@example.com", thirdPartyLoginMethod(storage, user1Id).email);
        assertEquals("user2-new@example.com", thirdPartyLoginMethod(storage, user2Id).email);
        assertFalse(EmailVerification.isEmailVerified(APP, storage, user2Id, "user1-new@example.com"));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * Collects the storage interfaces of the plugin's storage class, so that a delegating proxy can stand in for it.
     */
    private static Class<?>[] storageInterfaces(Class<?> cls) {
        Set<Class<?>> result = new LinkedHashSet<>();
        ClassLoader loader = Storage.class.getClassLoader();
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            List<Class<?>> pending = new ArrayList<>(Arrays.asList(c.getInterfaces()));
            while (!pending.isEmpty()) {
                Class<?> i = pending.remove(0);
                pending.addAll(Arrays.asList(i.getInterfaces()));
                try {
                    if (Class.forName(i.getName(), false, loader) == i) {
                        result.add(i);
                    }
                } catch (ClassNotFoundException ignored) {
                    // not visible to the core, not needed by it
                }
            }
        }
        return result.toArray(new Class<?>[0]);
    }

    @Test
    public void testIssue6_concurrentSignInDoesNotVerifyOtherEmail() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            process.kill();
            return;
        }

        Main main = process.getProcess();
        TenantIdentifier tenant = new TenantIdentifier(null, null, null);
        Storage storage = StorageLayer.getStorage(main);

        String userId = ThirdParty.signInUp(tenant, storage, main, "google", "sub-1", "start@example.com", false)
                .user.getSupertokensUserId();

        // Interleave deterministically: once the sign in below has committed its email update and re-reads the user,
        // another sign in for the same identity, with an email that is NOT verified, commits first.
        AtomicBoolean interleaved = new AtomicBoolean(false);
        Storage interposed = (Storage) Proxy.newProxyInstance(Storage.class.getClassLoader(),
                storageInterfaces(storage.getClass()), (proxy, method, methodArgs) -> {
                    if (method.getName().equals("getPrimaryUserByThirdPartyInfo") &&
                            interleaved.compareAndSet(false, true)) {
                        ThirdParty.signInUp(tenant, storage, main, "google", "sub-1", "unverified@example.com",
                                false);
                    }
                    try {
                        return method.invoke(storage, methodArgs);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });

        ThirdParty.SignInUpResponse response = ThirdParty.signInUp(tenant, interposed, main, "google", "sub-1",
                "verified@example.com", true);
        assertTrue("the concurrent sign in was not interleaved", interleaved.get());
        assertEquals(userId, response.user.getSupertokensUserId());

        // the email that was only ever sent as unverified must not become verified
        assertFalse(EmailVerification.isEmailVerified(APP, storage, userId, "unverified@example.com"));
        LoginMethod lM = response.user.loginMethods[0];
        assertEquals("unverified@example.com", lM.email);
        assertFalse(lM.verified);
        assertFalse(AuthRecipe.getUserById(APP, storage, userId).loginMethods[0].verified);

        // without interference, the vouched-for email is marked verified as before
        ThirdParty.SignInUpResponse again = ThirdParty.signInUp(tenant, storage, main, "google", "sub-1",
                "verified@example.com", true);
        assertEquals("verified@example.com", again.user.loginMethods[0].email);
        assertTrue(again.user.loginMethods[0].verified);
        assertTrue(EmailVerification.isEmailVerified(APP, storage, userId, "verified@example.com"));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
