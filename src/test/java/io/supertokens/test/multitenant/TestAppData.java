/*
 *    Copyright (c) 2023, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.test.multitenant;

import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;
import com.google.gson.JsonObject;
import io.supertokens.ActiveUsers;
import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.dashboard.Dashboard;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.emailverification.EmailVerification;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.passwordless.Passwordless;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.emailpassword.UserInfo;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.*;
import io.supertokens.pluginInterface.totp.TOTPDevice;
import io.supertokens.session.Session;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.thirdparty.ThirdParty;
import io.supertokens.totp.Totp;
import io.supertokens.useridmapping.UserIdMapping;
import io.supertokens.usermetadata.UserMetadata;
import io.supertokens.userroles.UserRoles;
import org.apache.commons.codec.binary.Base32;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.Key;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class TestAppData {
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

    private String[] removeStrings(String[] arr, String[] toRemove) {
        return Arrays.stream(arr).filter(s -> !Arrays.asList(toRemove).contains(s)).toArray(String[]::new);
    }

    private static String generateTotpCode(Main main, TOTPDevice device, int step)
            throws InvalidKeyException, StorageQueryException {
        final TimeBasedOneTimePasswordGenerator totp = new TimeBasedOneTimePasswordGenerator(
                Duration.ofSeconds(device.period));

        byte[] keyBytes = new Base32().decode(device.secretKey);
        Key key = new SecretKeySpec(keyBytes, "HmacSHA1");

        return totp.generateOneTimePasswordString(key, Instant.now().plusSeconds(step * device.period));
    }

    @Test
    public void testThatDeletingAppDeleteDataFromAllTables() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES,
                        new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY, EE_FEATURES.TOTP});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        String[] tablesToIgnore = new String[]{"tenant_thirdparty_provider_clients", "tenant_thirdparty_providers"};

        TenantIdentifier app = new TenantIdentifier(null, "a1", null);

        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                app,
                new EmailPasswordConfig(true),
                new ThirdPartyConfig(true, null),
                new PasswordlessConfig(true),
                new JsonObject()
        ), false);

        TenantIdentifierWithStorage appWithStorage = app.withStorage(
                StorageLayer.getStorage(app, process.getProcess()));

        String[] allTableNames = appWithStorage.getStorage().getAllTablesInTheDatabase();
        allTableNames = removeStrings(allTableNames, tablesToIgnore);
        Arrays.sort(allTableNames);

        // Add all recipe data
        UserInfo epUser = EmailPassword.signUp(appWithStorage, process.getProcess(), "test@example.com", "password");
        EmailPassword.generatePasswordResetTokenBeforeCdi4_0(appWithStorage, process.getProcess(), epUser.getSupertokensUserId());

        ThirdParty.SignInUpResponse tpUser = ThirdParty.signInUp(appWithStorage, process.getProcess(), "google",
                "googleid", "test@example.com");

        Passwordless.CreateCodeResponse code = Passwordless.createCode(appWithStorage, process.getProcess(),
                "test@example.com", null, null, null);
        Passwordless.ConsumeCodeResponse plUser = Passwordless.consumeCode(appWithStorage, process.getProcess(),
                code.deviceId, code.deviceIdHash, code.userInputCode, null);
        Passwordless.createCode(appWithStorage, process.getProcess(), "test@example.com", null, null, null);

        Dashboard.signUpDashboardUser(appWithStorage.toAppIdentifierWithStorage(), process.getProcess(),
                "user@example.com", "password");
        Dashboard.signInDashboardUser(appWithStorage.toAppIdentifierWithStorage(), process.getProcess(),
                "user@example.com", "password");

        String evToken = EmailVerification.generateEmailVerificationToken(appWithStorage, process.getProcess(),
                epUser.getSupertokensUserId(), epUser.email);
        EmailVerification.verifyEmail(appWithStorage, evToken);
        EmailVerification.generateEmailVerificationToken(appWithStorage, process.getProcess(), tpUser.user.getSupertokensUserId(),
                tpUser.user.loginMethods[0].email);

        Session.createNewSession(appWithStorage, process.getProcess(), epUser.getSupertokensUserId(), new JsonObject(), new JsonObject());

        UserRoles.createNewRoleOrModifyItsPermissions(appWithStorage.toAppIdentifierWithStorage(), "role",
                new String[]{"permission1", "permission2"});
        UserRoles.addRoleToUser(appWithStorage, epUser.getSupertokensUserId(), "role");

        TOTPDevice totpDevice = Totp.registerDevice(appWithStorage.toAppIdentifierWithStorage(), process.getProcess(),
                epUser.getSupertokensUserId(), "test", 1, 3);
        Totp.verifyCode(appWithStorage, process.getProcess(), epUser.getSupertokensUserId(),
                generateTotpCode(process.getProcess(), totpDevice, 0), true);

        ActiveUsers.updateLastActive(appWithStorage.toAppIdentifierWithStorage(), process.getProcess(), epUser.getSupertokensUserId());

        UserMetadata.updateUserMetadata(appWithStorage.toAppIdentifierWithStorage(), epUser.getSupertokensUserId(), new JsonObject());

        UserIdMapping.createUserIdMapping(process.getProcess(), appWithStorage.toAppIdentifierWithStorage(),
                plUser.user.getSupertokensUserId(), "externalid", null, false);

        String[] tablesThatHaveData = appWithStorage.getStorage()
                .getAllTablesInTheDatabaseThatHasDataForAppId(app.getAppId());
        tablesThatHaveData = removeStrings(tablesThatHaveData, tablesToIgnore);
        Arrays.sort(tablesThatHaveData);

        assertEquals(allTableNames, tablesThatHaveData);

        // Delete the app
        Multitenancy.deleteApp(app.toAppIdentifier(), process.getProcess());

        // Check no data is remaining in any of the tables
        tablesThatHaveData = appWithStorage.getStorage().getAllTablesInTheDatabaseThatHasDataForAppId(app.getAppId());
        tablesThatHaveData = removeStrings(tablesThatHaveData, tablesToIgnore);
        assertEquals(0, tablesThatHaveData.length);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
