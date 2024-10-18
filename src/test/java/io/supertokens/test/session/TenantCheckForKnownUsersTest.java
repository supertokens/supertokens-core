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

package io.supertokens.test.session;

import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.exceptions.UnauthorisedException;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.multitenancy.*;
import io.supertokens.session.Session;
import io.supertokens.session.accessToken.AccessToken;
import io.supertokens.session.info.SessionInformationHolder;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.utils.SemVer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public class TenantCheckForKnownUsersTest {
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
    public void verifyUnknownUsersSessionCreationWorks() throws Exception {

        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String userId = "userId-not-existing";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        SessionInformationHolder sessionInfo = Session.createNewSession(new TenantIdentifier(null, null, null), StorageLayer.getBaseStorage(process.getProcess()),
                process.getProcess(), userId, userDataInJWT, userDataInDatabase, true, AccessToken.getLatestVersion(), false,
                SemVer.v5_2);

        JsonObject sessionData = Session.getSession(process.getProcess(),
                sessionInfo.session.handle).userDataInDatabase;
        assertEquals(userDataInDatabase.toString(), sessionData.toString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void verifyKnownUsersWithRightTenantSessionCreationWorks() throws Exception {

        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        TenantIdentifier app = new TenantIdentifier(null, "a1", null);
        TenantIdentifier tenant = new TenantIdentifier(null, "a1", "t1");

        // Create tenants
        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                app,
                new EmailPasswordConfig(true),
                new ThirdPartyConfig(true, null),
                new PasswordlessConfig(true),
                null, null, new JsonObject()
        ), false);

        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                tenant,
                new EmailPasswordConfig(true),
                new ThirdPartyConfig(true, null),
                new PasswordlessConfig(true),
                null, null, new JsonObject()
        ), false);

        Storage appStorage = (
                StorageLayer.getStorage(app, process.getProcess()));
        Storage tenantStorage = (
                StorageLayer.getStorage(tenant, process.getProcess()));


        AuthRecipeUserInfo user = EmailPassword.signUp(app, appStorage, process.getProcess(), "test@example.com",
                "password");
        String userId = user.getSupertokensUserId();

        Multitenancy.addUserIdToTenant(process.getProcess(), tenant, tenantStorage, userId);

        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        SessionInformationHolder sessionInfo = Session.createNewSession(new TenantIdentifier(null, "a1", "t1"), StorageLayer.getBaseStorage(process.getProcess()),
                    process.getProcess(), userId, userDataInJWT, userDataInDatabase, true, AccessToken.getLatestVersion(), false,
                SemVer.v5_2);

        JsonObject sessionData = Session.getSession(new TenantIdentifier(null, "a1", "t1"),
                StorageLayer.getBaseStorage(process.getProcess()), sessionInfo.session.handle).userDataInDatabase;
        assertEquals(userDataInDatabase.toString(), sessionData.toString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void verifyKnownUsersSessionCreationWithWrongTenantThrows() throws Exception {

        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        TenantIdentifier app = new TenantIdentifier(null, "a1", null);
        TenantIdentifier tenant = new TenantIdentifier(null, "a1", "t1");
        TenantIdentifier tenant2 = new TenantIdentifier(null, "a1", "t2");

        // Create tenants
        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                app,
                new EmailPasswordConfig(true),
                new ThirdPartyConfig(true, null),
                new PasswordlessConfig(true),
                null, null, new JsonObject()
        ), false);

        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                tenant,
                new EmailPasswordConfig(true),
                new ThirdPartyConfig(true, null),
                new PasswordlessConfig(true),
                null, null, new JsonObject()
        ), false);

        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                tenant2,
                new EmailPasswordConfig(true),
                new ThirdPartyConfig(true, null),
                new PasswordlessConfig(true),
                null, null, new JsonObject()
        ), false);

        Storage appStorage = (
                StorageLayer.getStorage(app, process.getProcess()));
        Storage tenantStorage = (
                StorageLayer.getStorage(tenant, process.getProcess()));


        AuthRecipeUserInfo user = EmailPassword.signUp(app, appStorage, process.getProcess(), "test@example.com",
                "password");
        String userId = user.getSupertokensUserId();

        Multitenancy.addUserIdToTenant(process.getProcess(), tenant, tenantStorage, userId); //user only added to tenant!


        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");


        try {
            SessionInformationHolder sessionInfo = Session.createNewSession(tenant2, StorageLayer.getBaseStorage(process.getProcess()),
                    process.getProcess(), userId, userDataInJWT, userDataInDatabase, true, AccessToken.getLatestVersion(), false,
                    SemVer.v5_2);

            fail();
        } catch (UnauthorisedException e) {
            //pass
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void verifyKnownUsersSessionCreationWithWrongTenantDoesntThrowWithLesserCDI() throws Exception {

        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        TenantIdentifier app = new TenantIdentifier(null, "a1", null);
        TenantIdentifier tenant = new TenantIdentifier(null, "a1", "t1");
        TenantIdentifier tenant2 = new TenantIdentifier(null, "a1", "t2");

        // Create tenants
        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                app,
                new EmailPasswordConfig(true),
                new ThirdPartyConfig(true, null),
                new PasswordlessConfig(true),
                null, null, new JsonObject()
        ), false);

        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                tenant,
                new EmailPasswordConfig(true),
                new ThirdPartyConfig(true, null),
                new PasswordlessConfig(true),
                null, null, new JsonObject()
        ), false);

        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                tenant2,
                new EmailPasswordConfig(true),
                new ThirdPartyConfig(true, null),
                new PasswordlessConfig(true),
                null, null, new JsonObject()
        ), false);

        Storage appStorage = (
                StorageLayer.getStorage(app, process.getProcess()));
        Storage tenantStorage = (
                StorageLayer.getStorage(tenant, process.getProcess()));


        AuthRecipeUserInfo user = EmailPassword.signUp(app, appStorage, process.getProcess(), "test@example.com",
                "password");
        String userId = user.getSupertokensUserId();

        Multitenancy.addUserIdToTenant(process.getProcess(), tenant, tenantStorage, userId); //user only added to tenant!


        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        SessionInformationHolder sessionInfo = Session.createNewSession(tenant2, StorageLayer.getBaseStorage(process.getProcess()),
                process.getProcess(), userId, userDataInJWT, userDataInDatabase, true, AccessToken.getLatestVersion(), false,
                SemVer.v5_1);

        JsonObject sessionData = Session.getSession(tenant2, StorageLayer.getBaseStorage(process.getProcess()), sessionInfo.session.handle).userDataInDatabase;
        assertEquals(userDataInDatabase.toString(), sessionData.toString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

}
