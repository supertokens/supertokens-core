/*
 *    Copyright (c) 2020, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.test;

import io.supertokens.ProcessState;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicateEmailException;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicateUserIdException;
import io.supertokens.storageLayer.StorageLayer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.assertNotNull;

/*
 * TODO:
 *  - Check that StorageLayer.getEmailPasswordStorageLayer throws as exception if the storage type is not SQL (and
 *   vice versa)
 *  - Test normaliseEmail function
 *  - Test UpdatableBCrypt class
 *     - test time taken for hash
 *     - test hashing and verifying with short passwords and > 100 char password
 *  - Good input / output test
 * */

public class EmailPasswordTest {
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
    public void clashingUserIdDuringSignUp() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        StorageLayer.getEmailPasswordStorageLayer(process.getProcess()).signUp("8ed86166-bfd8-4234-9dfe-abca9606dbd5",
                "test@example.com", "passwordHash", System.currentTimeMillis());

        try {
            StorageLayer.getEmailPasswordStorageLayer(process.getProcess())
                    .signUp("8ed86166-bfd8-4234-9dfe-abca9606dbd5",
                            "test1@example.com", "passwordHash", System.currentTimeMillis());
            assert (false);
        } catch (DuplicateUserIdException e) {

        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void clashingEmailIdDuringSignUp() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        EmailPassword.signUp(process.getProcess(), "test@example.com", "password");

        try {
            EmailPassword.signUp(process.getProcess(), "test@example.com", "password");
            assert (false);
        } catch (DuplicateEmailException e) {

        }
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void clashingEmailAndUserIdDuringSignUp() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        StorageLayer.getEmailPasswordStorageLayer(process.getProcess()).signUp("8ed86166-bfd8-4234-9dfe-abca9606dbd5",
                "test@example.com", "passwordHash", System.currentTimeMillis());

        try {
            StorageLayer.getEmailPasswordStorageLayer(process.getProcess())
                    .signUp("8ed86166-bfd8-4234-9dfe-abca9606dbd5",
                            "test@example.com", "passwordHash", System.currentTimeMillis());
            assert (false);
        } catch (DuplicateUserIdException e) {

        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
