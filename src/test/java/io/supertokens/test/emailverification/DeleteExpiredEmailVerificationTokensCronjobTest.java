/*
 *    Copyright (c) 2021, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.test.emailverification;

import io.supertokens.ProcessState;
import io.supertokens.cronjobs.CronTaskTest;
import io.supertokens.cronjobs.deleteExpiredEmailVerificationTokens.DeleteExpiredEmailVerificationTokens;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.emailverification.EmailVerification;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.emailpassword.UserInfo;
import io.supertokens.pluginInterface.emailverification.EmailVerificationStorage;
import io.supertokens.pluginInterface.emailverification.EmailVerificationTokenInfo;
import io.supertokens.pluginInterface.emailverification.sqlStorage.EmailVerificationSQLStorage;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.assertNotNull;

public class DeleteExpiredEmailVerificationTokensCronjobTest {
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
    public void checkingCronJob() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        CronTaskTest.getInstance(process.getProcess())
                .setIntervalInSeconds(DeleteExpiredEmailVerificationTokens.RESOURCE_KEY, 2);
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        UserInfo user = EmailPassword.signUp(process.getProcess(), "test1@example.com", "password");

        String tok = EmailVerification.generateEmailVerificationToken(process.getProcess(), user.id, user.email);
        String tok2 = EmailVerification.generateEmailVerificationToken(process.getProcess(), user.id, user.email);

        io.supertokens.emailverification.EmailVerificationTest.getInstance(process.getProcess())
                .setEmailVerificationTokenLifetime(10);

        EmailVerification.generateEmailVerificationToken(process.getProcess(), user.id, user.email);
        EmailVerification.generateEmailVerificationToken(process.getProcess(), user.id, user.email);

        assert (((EmailVerificationSQLStorage) StorageLayer.getStorage(process.getProcess()))
                .getAllEmailVerificationTokenInfoForUser(new AppIdentifier(null, null), user.id, user.email).length ==
                4);

        Thread.sleep(3000);

        EmailVerificationTokenInfo[] tokens = ((EmailVerificationSQLStorage) StorageLayer.getStorage(process.getProcess()))
                .getAllEmailVerificationTokenInfoForUser(new AppIdentifier(null, null), user.id, user.email);

        assert (tokens.length == 2);

        assert (!tokens[0].token.equals(tokens[1].token));
        assert (tokens[0].token.equals(io.supertokens.utils.Utils.hashSHA256(tok))
                || tokens[0].token.equals(io.supertokens.utils.Utils.hashSHA256(tok2)));
        assert (tokens[1].token.equals(io.supertokens.utils.Utils.hashSHA256(tok))
                || tokens[1].token.equals(io.supertokens.utils.Utils.hashSHA256(tok2)));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

}
