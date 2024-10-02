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

package io.supertokens.test.passwordless;

import io.supertokens.ProcessState;
import io.supertokens.passwordless.Passwordless;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static io.supertokens.test.passwordless.PasswordlessUtility.*;
import static org.junit.Assert.*;

/**
 * This UT encompasses
 */

public class PasswordlessGetUserTest {

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

    /**
     * getUserById
     * with email set
     *
     * @throws Exception
     */
    @Test
    public void getUserByIdWithEmail() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        Passwordless.ConsumeCodeResponse consumeCodeResponse = createUserWith(process, EMAIL, null);

        AuthRecipeUserInfo user = Passwordless.getUserById(process.getProcess(),
                consumeCodeResponse.user.getSupertokensUserId());
        assertNotNull(user);
        assertEquals(user.loginMethods[0].email, EMAIL);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    /**
     * with phone number set
     *
     * @throws Exception
     */
    @Test
    public void getUserByIdWithPhoneNumber() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        Passwordless.ConsumeCodeResponse consumeCodeResponse = createUserWith(process, null, PHONE_NUMBER);

        AuthRecipeUserInfo user = Passwordless.getUserById(process.getProcess(),
                consumeCodeResponse.user.getSupertokensUserId());
        assertNotNull(user);
        assertEquals(user.loginMethods[0].phoneNumber, PHONE_NUMBER);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    /**
     * with both email and phoneNumber set
     *
     * @throws Exception
     */
    @Test
    public void getUserByIdWithEmailAndPhoneNumber() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        Passwordless.ConsumeCodeResponse consumeCodeResponse = createUserWith(process, EMAIL, PHONE_NUMBER);

        AuthRecipeUserInfo user = Passwordless.getUserById(process.getProcess(),
                consumeCodeResponse.user.getSupertokensUserId());
        assertNotNull(user);
        assertEquals(user.loginMethods[0].email, EMAIL);
        assertEquals(user.loginMethods[0].phoneNumber, PHONE_NUMBER);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    /**
     * returns null if it doesn't exist
     *
     * @throws Exception
     */
    @Test
    public void getUserByInvalidId() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        Passwordless.ConsumeCodeResponse consumeCodeResponse = createUserWith(process, EMAIL, null);

        AuthRecipeUserInfo user = Passwordless.getUserById(process.getProcess(),
                consumeCodeResponse.user.getSupertokensUserId() + "1");
        assertNull(user);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    /**
     * getUserByEmail
     *
     * @throws Exception
     */
    @Test
    public void getUserByEmail() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        createUserWith(process, EMAIL, null);

        AuthRecipeUserInfo user = Passwordless.getUserByEmail(process.getProcess(), EMAIL);
        assertNotNull(user);
        assertEquals(user.loginMethods[0].email, EMAIL);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    /**
     * getUserByEmail
     *
     * @throws Exception
     */
    @Test
    public void getUserByInvalidEmail() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        createUserWith(process, EMAIL, null);

        AuthRecipeUserInfo user = Passwordless.getUserByEmail(process.getProcess(), EMAIL + "a");
        assertNull(user);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    /**
     * getUserByPhoneNumber
     *
     * @throws Exception
     */
    @Test
    public void getUserByPhoneNumber() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        createUserWith(process, null, PHONE_NUMBER);

        AuthRecipeUserInfo user = Passwordless.getUserByPhoneNumber(process.getProcess(), PHONE_NUMBER);
        assertNotNull(user);
        assertEquals(user.loginMethods[0].phoneNumber, PHONE_NUMBER);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * getUserByPhoneNumber
     *
     * @throws Exception
     */
    @Test
    public void getUserByInvalidPhoneNumber() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        createUserWith(process, null, PHONE_NUMBER);

        AuthRecipeUserInfo user = Passwordless.getUserByPhoneNumber(process.getProcess(), PHONE_NUMBER + "1");
        assertNull(user);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

}
