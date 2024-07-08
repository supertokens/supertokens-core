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
import io.supertokens.passwordless.exceptions.UserWithoutContactInfoException;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicateEmailException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.passwordless.PasswordlessStorage;
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
import static org.junit.Assert.*;

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
     * try to update email of an user to an email that already exists -> DuplicateEmailException + no change
     *
     * @throws Exception
     */
    @Test
    public void updateEmailToAnExistingOne() throws Exception {
        String alternate_email = "alternate_testing@example.com";
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessStorage storage = (PasswordlessStorage) StorageLayer.getStorage(process.getProcess());

        createUserWith(process, EMAIL, null);
        createUserWith(process, alternate_email, null);

        AuthRecipeUserInfo[] user = storage.listPrimaryUsersByEmail(new TenantIdentifier(null, null, null), EMAIL);
        assert (user.length == 1);

        AuthRecipeUserInfo[] user_two = storage.listPrimaryUsersByEmail(new TenantIdentifier(null, null, null),
                alternate_email);
        assert (user_two.length == 1);

        Exception ex = null;
        try {
            Passwordless.updateUser(process.getProcess(), user[0].getSupertokensUserId(),
                    new Passwordless.FieldUpdate(alternate_email),
                    null);
        } catch (Exception e) {
            ex = e;
        }

        assertNotNull(ex);
        assert (ex instanceof DuplicateEmailException);

        assertEquals(EMAIL,
                storage.listPrimaryUsersByEmail(new TenantIdentifier(null, null, null),
                        EMAIL)[0].loginMethods[0].email);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * try update phone number to an existing one -> DuplicatePhoneNumberException + no change
     *
     * @throws Exception
     */
    @Test
    public void updatePhoneNumberToAnExistingOne() throws Exception {
        String alternate_phoneNumber = PHONE_NUMBER + "1";
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessStorage storage = (PasswordlessStorage) StorageLayer.getStorage(process.getProcess());

        createUserWith(process, null, PHONE_NUMBER);
        createUserWith(process, null, alternate_phoneNumber);

        AuthRecipeUserInfo[] user = storage.listPrimaryUsersByPhoneNumber(new TenantIdentifier(null, null, null),
                PHONE_NUMBER);
        assert (user.length == 1);
        AuthRecipeUserInfo[] user_two = storage.listPrimaryUsersByPhoneNumber(new TenantIdentifier(null, null, null),
                alternate_phoneNumber);
        assert (user_two.length == 1);

        Exception ex = null;
        try {
            Passwordless.updateUser(process.getProcess(), user[0].getSupertokensUserId(), null,
                    new Passwordless.FieldUpdate(alternate_phoneNumber));
        } catch (Exception e) {
            ex = e;
        }

        assertNotNull(ex);
        assert (ex instanceof DuplicatePhoneNumberException);

        assertEquals(PHONE_NUMBER,
                storage.listPrimaryUsersByPhoneNumber(new TenantIdentifier(null, null, null),
                        PHONE_NUMBER)[0].loginMethods[0].phoneNumber);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    /**
     * update email leaving phoneNumber
     *
     * @throws Exception
     */
    @Test
    public void updateEmail() throws Exception {
        String alternate_email = "alternate_testing@example.com";
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessStorage storage = (PasswordlessStorage) StorageLayer.getStorage(process.getProcess());

        createUserWith(process, EMAIL, null);

        AuthRecipeUserInfo[] user = storage.listPrimaryUsersByEmail(new TenantIdentifier(null, null, null), EMAIL);
        assert (user.length == 1);

        Passwordless.updateUser(process.getProcess(), user[0].getSupertokensUserId(),
                new Passwordless.FieldUpdate(alternate_email), null);

        assertEquals(alternate_email,
                storage.getPrimaryUserById(new AppIdentifier(null, null),
                        user[0].getSupertokensUserId()).loginMethods[0].email);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    /**
     * update phone leaving email
     *
     * @throws Exception
     */
    @Test
    public void updatePhoneNumber() throws Exception {
        String alternate_phoneNumber = PHONE_NUMBER + "1";
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessStorage storage = (PasswordlessStorage) StorageLayer.getStorage(process.getProcess());

        createUserWith(process, null, PHONE_NUMBER);

        AuthRecipeUserInfo[] user = storage.listPrimaryUsersByPhoneNumber(new TenantIdentifier(null, null, null),
                PHONE_NUMBER);
        assert (user.length == 1);

        Passwordless.updateUser(process.getProcess(), user[0].getSupertokensUserId(), null,
                new Passwordless.FieldUpdate(alternate_phoneNumber));

        assertEquals(alternate_phoneNumber,
                storage.getPrimaryUserById(new AppIdentifier(null, null),
                        user[0].getSupertokensUserId()).loginMethods[0].phoneNumber);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    /**
     * clear email + set phoneNumber
     *
     * @throws Exception
     */
    @Test
    public void clearEmailSetPhoneNumber() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessStorage storage = (PasswordlessStorage) StorageLayer.getStorage(process.getProcess());

        createUserWith(process, EMAIL, null);

        AuthRecipeUserInfo[] user = storage.listPrimaryUsersByEmail(new TenantIdentifier(null, null, null), EMAIL);
        assert (user.length == 1);

        Passwordless.updateUser(process.getProcess(), user[0].getSupertokensUserId(),
                new Passwordless.FieldUpdate(null),
                new Passwordless.FieldUpdate(PHONE_NUMBER));

        assertEquals(PHONE_NUMBER,
                storage.getPrimaryUserById(new AppIdentifier(null, null),
                        user[0].getSupertokensUserId()).loginMethods[0].phoneNumber);
        assertNull(storage.getPrimaryUserById(new AppIdentifier(null, null),
                user[0].getSupertokensUserId()).loginMethods[0].email);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    /**
     * clear phone + set email
     *
     * @throws Exception
     */
    @Test
    public void clearPhoneNumberSetEmail() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessStorage storage = (PasswordlessStorage) StorageLayer.getStorage(process.getProcess());

        createUserWith(process, null, PHONE_NUMBER);

        AuthRecipeUserInfo[] user = storage.listPrimaryUsersByPhoneNumber(new TenantIdentifier(null, null, null),
                PHONE_NUMBER);
        assert (user.length == 1);

        Passwordless.updateUser(process.getProcess(), user[0].getSupertokensUserId(),
                new Passwordless.FieldUpdate(EMAIL),
                new Passwordless.FieldUpdate(null));

        assertEquals(EMAIL,
                storage.getPrimaryUserById(new AppIdentifier(null, null),
                        user[0].getSupertokensUserId()).loginMethods[0].email);
        assertNull(storage.getPrimaryUserById(new AppIdentifier(null, null),
                user[0].getSupertokensUserId()).loginMethods[0].phoneNumber);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    /**
     * clear both email and phone -> UserWithoutContactInfoException
     *
     * @throws Exception
     */
    @Test
    public void clearPhoneNumberAndEmail() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessStorage storage = (PasswordlessStorage) StorageLayer.getStorage(process.getProcess());

        createUserWith(process, null, PHONE_NUMBER);

        AuthRecipeUserInfo[] user = storage.listPrimaryUsersByPhoneNumber(new TenantIdentifier(null, null, null),
                PHONE_NUMBER);
        assert (user.length == 1);
        Exception ex = null;

        try {
            Passwordless.updateUser(process.getProcess(), user[0].getSupertokensUserId(),
                    new Passwordless.FieldUpdate(null),
                    new Passwordless.FieldUpdate(null));
        } catch (Exception e) {
            ex = e;
        }

        assertNotNull(ex);
        assert (ex instanceof UserWithoutContactInfoException);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    /**
     * clear email of an email only user
     *
     * @throws Exception
     */
    @Test
    public void clearEmailOfEmailOnlyUser() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessStorage storage = (PasswordlessStorage) StorageLayer.getStorage(process.getProcess());

        createUserWith(process, EMAIL, null);

        AuthRecipeUserInfo[] user = storage.listPrimaryUsersByEmail(new TenantIdentifier(null, null, null), EMAIL);
        assert (user.length == 1);

        Exception ex = null;

        try {
            Passwordless.updateUser(process.getProcess(), user[0].getSupertokensUserId(),
                    new Passwordless.FieldUpdate(null), null);
        } catch (Exception e) {
            ex = e;
        }

        assertNotNull(ex);
        assert (ex instanceof UserWithoutContactInfoException);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    /**
     * clear phone of a phone only user
     *
     * @throws Exception
     */
    @Test
    public void clearPhoneOfPhoneOnlyUser() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessStorage storage = (PasswordlessStorage) StorageLayer.getStorage(process.getProcess());

        createUserWith(process, null, PHONE_NUMBER);

        AuthRecipeUserInfo[] user = storage.listPrimaryUsersByPhoneNumber(new TenantIdentifier(null, null, null),
                PHONE_NUMBER);
        assert (user.length == 1);

        Exception ex = null;

        try {
            Passwordless.updateUser(process.getProcess(), user[0].getSupertokensUserId(), null,
                    new Passwordless.FieldUpdate(null));
        } catch (Exception e) {
            ex = e;
        }

        assertNotNull(ex);
        assert (ex instanceof UserWithoutContactInfoException);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    /**
     * set both email and phone
     *
     * @throws Exception
     */
    @Test
    public void setPhoneNumberSetEmail() throws Exception {
        String alternate_phoneNumber = PHONE_NUMBER + "1";
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessStorage storage = (PasswordlessStorage) StorageLayer.getStorage(process.getProcess());

        createUserWith(process, null, PHONE_NUMBER);

        AuthRecipeUserInfo[] user = storage.listPrimaryUsersByPhoneNumber(new TenantIdentifier(null, null, null),
                PHONE_NUMBER);
        assert (user.length == 1);

        Passwordless.updateUser(process.getProcess(), user[0].getSupertokensUserId(),
                new Passwordless.FieldUpdate(EMAIL),
                new Passwordless.FieldUpdate(alternate_phoneNumber));

        assertEquals(EMAIL,
                storage.getPrimaryUserById(new AppIdentifier(null, null),
                        user[0].getSupertokensUserId()).loginMethods[0].email);
        assertEquals(alternate_phoneNumber,
                storage.getPrimaryUserById(new AppIdentifier(null, null),
                        user[0].getSupertokensUserId()).loginMethods[0].phoneNumber);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

}
