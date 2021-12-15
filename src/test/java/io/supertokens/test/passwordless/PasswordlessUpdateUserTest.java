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

import io.supertokens.passwordless.Passwordless;
import io.supertokens.passwordless.exceptions.UserWithoutContactInfoException;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicateEmailException;
import io.supertokens.pluginInterface.passwordless.PasswordlessStorage;
import io.supertokens.pluginInterface.passwordless.UserInfo;
import io.supertokens.pluginInterface.passwordless.exception.DuplicatePhoneNumberException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static io.supertokens.test.passwordless.PasswordlessUtility.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * This UT encompasses tests related to update user
 */
public class PasswordlessUpdateUserTest {

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
     * try update email to an existing one -> DuplicateEmailException + no change
     *
     * @throws Exception
     */
    @Test
    public void updateEmailToAnExistingOne() throws Exception {
        String alternate_email = "alternate_testing@example.com";
        TestingProcessManager.TestingProcess process = startApplicationWithDefaultArgs();

        PasswordlessStorage storage = StorageLayer.getPasswordlessStorage(process.getProcess());
        UserInfo user = null;

        Passwordless.ConsumeCodeResponse consumeCodeResponse = createUserWith(process, EMAIL, null);

        Passwordless.ConsumeCodeResponse consumeCodeResponseAlternate = createUserWith(process, alternate_email, null);

        user = storage.getUserByEmail(EMAIL);
        assertNotNull(user);

        Exception ex = null;
        try {
            Passwordless.updateUser(process.getProcess(), user.id, new Passwordless.FieldUpdate(alternate_email), null);
        } catch (Exception e) {
            ex = e;
        }

        assertNotNull(ex);
        assert (ex instanceof DuplicateEmailException);

        assertEquals(EMAIL, storage.getUserByEmail(EMAIL).email);
    }

    /**
     * try update phone number to an existing one -> DuplicatePhoneNumberException + no change
     *
     * @throws Exception
     */
    @Test
    public void updatePhoneNumberToAnExistingOne() throws Exception {
        String alternate_phoneNumber = PHONE_NUMBER + "1";
        TestingProcessManager.TestingProcess process = startApplicationWithDefaultArgs();

        PasswordlessStorage storage = StorageLayer.getPasswordlessStorage(process.getProcess());
        UserInfo user = null;

        Passwordless.ConsumeCodeResponse consumeCodeResponse = createUserWith(process, null, PHONE_NUMBER);

        Passwordless.ConsumeCodeResponse consumeCodeResponseAlternate = createUserWith(process, null,
                alternate_phoneNumber);

        user = storage.getUserByPhoneNumber(PHONE_NUMBER);
        assertNotNull(user);

        Exception ex = null;
        try {
            Passwordless.updateUser(process.getProcess(), user.id, null,
                    new Passwordless.FieldUpdate(alternate_phoneNumber));
        } catch (Exception e) {
            ex = e;
        }

        assertNotNull(ex);
        assert (ex instanceof DuplicatePhoneNumberException);

        assertEquals(PHONE_NUMBER, storage.getUserByPhoneNumber(PHONE_NUMBER).phoneNumber);
    }

    /**
     * update email leaving phoneNumber
     *
     * @throws Exception
     */
    @Test
    public void updateEmail() throws Exception {
        String alternate_email = "alternate_testing@example.com";
        TestingProcessManager.TestingProcess process = startApplicationWithDefaultArgs();

        PasswordlessStorage storage = StorageLayer.getPasswordlessStorage(process.getProcess());
        UserInfo user = null;

        Passwordless.ConsumeCodeResponse consumeCodeResponse = createUserWith(process, EMAIL, null);

        user = storage.getUserByEmail(EMAIL);
        assertNotNull(user);

        Passwordless.updateUser(process.getProcess(), user.id, new Passwordless.FieldUpdate(alternate_email), null);

        assertEquals(alternate_email, storage.getUserById(user.id).email);
    }

    /**
     * update phone leaving email
     *
     * @throws Exception
     */
    @Test
    public void updatePhoneNumber() throws Exception {
        String alternate_phoneNumber = PHONE_NUMBER + "1";
        TestingProcessManager.TestingProcess process = startApplicationWithDefaultArgs();

        PasswordlessStorage storage = StorageLayer.getPasswordlessStorage(process.getProcess());
        UserInfo user = null;

        Passwordless.ConsumeCodeResponse consumeCodeResponse = createUserWith(process, null, PHONE_NUMBER);

        user = storage.getUserByPhoneNumber(PHONE_NUMBER);
        assertNotNull(user);

        Passwordless.updateUser(process.getProcess(), user.id, null,
                new Passwordless.FieldUpdate(alternate_phoneNumber));

        assertEquals(alternate_phoneNumber, storage.getUserById(user.id).phoneNumber);
    }

    /**
     * clear email + set phoneNumber
     *
     * @throws Exception
     */
    @Test
    public void clearEmailSetPhoneNumber() throws Exception {
        TestingProcessManager.TestingProcess process = startApplicationWithDefaultArgs();

        PasswordlessStorage storage = StorageLayer.getPasswordlessStorage(process.getProcess());
        UserInfo user = null;

        Passwordless.ConsumeCodeResponse consumeCodeResponse = createUserWith(process, EMAIL, null);

        user = storage.getUserByEmail(EMAIL);
        assertNotNull(user);

        Passwordless.updateUser(process.getProcess(), user.id, new Passwordless.FieldUpdate(null),
                new Passwordless.FieldUpdate(PHONE_NUMBER));

        assertEquals(PHONE_NUMBER, storage.getUserById(user.id).phoneNumber);
        assertEquals(null, storage.getUserById(user.id).email);

    }

    /**
     * clear phone + set email
     *
     * @throws Exception
     */
    @Test
    public void clearPhoneNumberSetEmail() throws Exception {
        TestingProcessManager.TestingProcess process = startApplicationWithDefaultArgs();

        PasswordlessStorage storage = StorageLayer.getPasswordlessStorage(process.getProcess());
        UserInfo user = null;

        Passwordless.ConsumeCodeResponse consumeCodeResponse = createUserWith(process, null, PHONE_NUMBER);

        user = storage.getUserByPhoneNumber(PHONE_NUMBER);
        assertNotNull(user);

        Passwordless.updateUser(process.getProcess(), user.id, new Passwordless.FieldUpdate(EMAIL),
                new Passwordless.FieldUpdate(null));

        assertEquals(EMAIL, storage.getUserById(user.id).email);
        assertEquals(null, storage.getUserById(user.id).phoneNumber);

    }

    /**
     * clear both email and phone -> UserWithoutContactInfoException
     *
     * @throws Exception
     */
    @Test
    public void clearPhoneNumberAndEmail() throws Exception {
        TestingProcessManager.TestingProcess process = startApplicationWithDefaultArgs();

        PasswordlessStorage storage = StorageLayer.getPasswordlessStorage(process.getProcess());
        UserInfo user = null;

        Passwordless.ConsumeCodeResponse consumeCodeResponse = createUserWith(process, null, PHONE_NUMBER);

        user = storage.getUserByPhoneNumber(PHONE_NUMBER);
        assertNotNull(user);
        Exception ex = null;

        try {
            Passwordless.updateUser(process.getProcess(), user.id, new Passwordless.FieldUpdate(null),
                    new Passwordless.FieldUpdate(null));
        } catch (Exception e) {
            ex = e;
        }

        assertNotNull(ex);
        assert (ex instanceof UserWithoutContactInfoException);
    }

    /**
     * set both email and phone
     *
     * @throws Exception
     */
    @Test
    public void setPhoneNumberSetEmail() throws Exception {
        String alternate_phoneNumber = PHONE_NUMBER + "1";
        TestingProcessManager.TestingProcess process = startApplicationWithDefaultArgs();

        PasswordlessStorage storage = StorageLayer.getPasswordlessStorage(process.getProcess());
        UserInfo user = null;

        Passwordless.ConsumeCodeResponse consumeCodeResponse = createUserWith(process, null, PHONE_NUMBER);

        user = storage.getUserByPhoneNumber(PHONE_NUMBER);
        assertNotNull(user);

        Passwordless.updateUser(process.getProcess(), user.id, new Passwordless.FieldUpdate(EMAIL),
                new Passwordless.FieldUpdate(alternate_phoneNumber));

        assertEquals(EMAIL, storage.getUserById(user.id).email);
        assertEquals(alternate_phoneNumber, storage.getUserById(user.id).phoneNumber);

    }

}
