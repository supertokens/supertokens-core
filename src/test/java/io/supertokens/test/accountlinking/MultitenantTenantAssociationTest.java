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

package io.supertokens.test.accountlinking;

import io.supertokens.authRecipe.exception.AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException;
import io.supertokens.emailpassword.exceptions.WrongCredentialsException;
import io.supertokens.multitenancy.exception.*;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicateEmailException;
import io.supertokens.pluginInterface.passwordless.exception.DuplicatePhoneNumberException;
import io.supertokens.pluginInterface.thirdparty.exception.DuplicateThirdPartyUserException;
import org.junit.Test;

/**
 * Tests for associating users to tenants after account linking.
 * Verifies duplicate email/phone/thirdparty conflicts when associating
 * linked or primary users across tenants.
 *
 * Extracted from MultitenantTest.testVariousCases (cases 0-11, 19-24, 28-29).
 */
public class MultitenantTenantAssociationTest extends MultitenantTestBase {

    @Test
    public void testTenantAssociationWithLinkedAccounts() throws Exception {
        initTenantIdentifiers();

        runTestCases(
                // Case 0: EP+Pless same email, MakePrimary then Associate
                new TestCase(new TestCaseStep[]{
                        new CreateEmailPasswordUser(t1, "test@example.com"),
                        new CreatePlessUserWithEmail(t2, "test@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new AssociateUserToTenant(t2, 0),
                }),
                // Case 1: EP+Pless same email, Associate then MakePrimary
                new TestCase(new TestCaseStep[]{
                        new CreateEmailPasswordUser(t1, "test@example.com"),
                        new CreatePlessUserWithEmail(t2, "test@example.com"),
                        new AssociateUserToTenant(t2, 0),
                        new MakePrimaryUser(t1, 0),
                }),
                // Case 2: EP+Pless same email, Link then SignIn → WrongCredentials
                new TestCase(new TestCaseStep[]{
                        new CreateEmailPasswordUser(t1, "test@example.com"),
                        new CreatePlessUserWithEmail(t2, "test@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new LinkAccounts(t1, 0, 1),
                        new SignInEmailPasswordUser(t2, 0).expect(new WrongCredentialsException())
                }),

                // Case 3: EP+EP linked, associate 3rd EP → DuplicateEmail on same tenant, allowed on other
                new TestCase(new TestCaseStep[]{
                        new CreateEmailPasswordUser(t1, "test1@example.com"),
                        new CreateEmailPasswordUser(t2, "test2@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new LinkAccounts(t1, 0, 1),
                        new CreateEmailPasswordUser(t3, "test1@example.com"),
                        new AssociateUserToTenant(t1, 2).expect(new DuplicateEmailException()),
                        new AssociateUserToTenant(t2, 2), // Allowed
                }),

                // Case 4: EP+EP linked, associate 3rd EP (primary) → AnotherPrimaryWithEmail
                new TestCase(new TestCaseStep[]{
                        new CreateEmailPasswordUser(t1, "test1@example.com"),
                        new CreateEmailPasswordUser(t2, "test2@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new LinkAccounts(t1, 0, 1),
                        new CreateEmailPasswordUser(t3, "test1@example.com"),
                        new MakePrimaryUser(t3, 2),
                        new AssociateUserToTenant(t2, 2).expect(
                                new AnotherPrimaryUserWithEmailAlreadyExistsException("")),
                }),

                // Case 5: EP+EP linked, associate then MakePrimary → AccountInfoAlreadyAssociated
                new TestCase(new TestCaseStep[]{
                        new CreateEmailPasswordUser(t1, "test1@example.com"),
                        new CreateEmailPasswordUser(t2, "test2@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new LinkAccounts(t1, 0, 1),
                        new CreateEmailPasswordUser(t3, "test1@example.com"),
                        new AssociateUserToTenant(t2, 2),
                        new MakePrimaryUser(t3, 2).expect(
                                new AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException("", "")),
                }),

                // Case 6: EP+Pless linked, associate 3rd EP → DuplicateEmail / allowed
                new TestCase(new TestCaseStep[]{
                        new CreateEmailPasswordUser(t1, "test1@example.com"),
                        new CreatePlessUserWithEmail(t2, "test2@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new LinkAccounts(t1, 0, 1),
                        new CreateEmailPasswordUser(t3, "test1@example.com"),
                        new AssociateUserToTenant(t1, 2).expect(new DuplicateEmailException()),
                        new AssociateUserToTenant(t2, 2), // Allowed
                }),

                // Case 7: EP+Pless linked, associate 3rd EP (primary) → AnotherPrimaryWithEmail
                new TestCase(new TestCaseStep[]{
                        new CreateEmailPasswordUser(t1, "test1@example.com"),
                        new CreatePlessUserWithEmail(t2, "test2@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new LinkAccounts(t1, 0, 1),
                        new CreateEmailPasswordUser(t3, "test1@example.com"),
                        new MakePrimaryUser(t3, 2),
                        new AssociateUserToTenant(t2, 2).expect(
                                new AnotherPrimaryUserWithEmailAlreadyExistsException("")),
                }),

                // Case 8: EP+Pless linked, associate then MakePrimary → AccountInfoAlreadyAssociated
                new TestCase(new TestCaseStep[]{
                        new CreateEmailPasswordUser(t1, "test1@example.com"),
                        new CreatePlessUserWithEmail(t2, "test2@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new LinkAccounts(t1, 0, 1),
                        new CreateEmailPasswordUser(t3, "test1@example.com"),
                        new AssociateUserToTenant(t2, 2),
                        new MakePrimaryUser(t3, 2).expect(
                                new AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException("", "")),
                }),

                // Case 9: Pless+EP linked, associate 3rd EP → allowed (different recipe primary)
                new TestCase(new TestCaseStep[]{
                        new CreatePlessUserWithEmail(t1, "test1@example.com"),
                        new CreateEmailPasswordUser(t2, "test2@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new LinkAccounts(t1, 0, 1),
                        new CreateEmailPasswordUser(t3, "test1@example.com"),
                        new AssociateUserToTenant(t1, 2),
                        new AssociateUserToTenant(t2, 2), // Allowed
                }),

                // Case 10: Pless+EP linked, associate 3rd EP (primary) → AnotherPrimaryWithEmail
                new TestCase(new TestCaseStep[]{
                        new CreatePlessUserWithEmail(t1, "test1@example.com"),
                        new CreateEmailPasswordUser(t2, "test2@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new LinkAccounts(t1, 0, 1),
                        new CreateEmailPasswordUser(t3, "test1@example.com"),
                        new MakePrimaryUser(t3, 2),
                        new AssociateUserToTenant(t2, 2).expect(
                                new AnotherPrimaryUserWithEmailAlreadyExistsException("")),
                }),

                // Case 11: Pless+EP linked, associate then MakePrimary → AccountInfoAlreadyAssociated
                new TestCase(new TestCaseStep[]{
                        new CreatePlessUserWithEmail(t1, "test1@example.com"),
                        new CreateEmailPasswordUser(t2, "test2@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new LinkAccounts(t1, 0, 1),
                        new CreateEmailPasswordUser(t3, "test1@example.com"),
                        new AssociateUserToTenant(t2, 2),
                        new MakePrimaryUser(t3, 2).expect(
                                new AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException("", "")),
                }),

                // Case 19: EP+TP linked, associate 3rd EP → DuplicateEmail / allowed
                new TestCase(new TestCaseStep[]{
                        new CreateEmailPasswordUser(t1, "test1@example.com"),
                        new CreateThirdPartyUser(t2, "google", "googleid", "test2@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new LinkAccounts(t1, 0, 1),
                        new CreateEmailPasswordUser(t3, "test1@example.com"),
                        new AssociateUserToTenant(t1, 2).expect(new DuplicateEmailException()),
                        new AssociateUserToTenant(t2, 2), // Allowed
                }),

                // Case 20: EP+TP linked, associate 3rd EP (primary) → AnotherPrimaryWithEmail
                new TestCase(new TestCaseStep[]{
                        new CreateEmailPasswordUser(t1, "test1@example.com"),
                        new CreateThirdPartyUser(t2, "google", "googleid", "test2@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new LinkAccounts(t1, 0, 1),
                        new CreateEmailPasswordUser(t3, "test1@example.com"),
                        new MakePrimaryUser(t3, 2),
                        new AssociateUserToTenant(t2, 2).expect(
                                new AnotherPrimaryUserWithEmailAlreadyExistsException("")),
                }),

                // Case 21: EP+TP linked, associate then MakePrimary → AccountInfoAlreadyAssociated
                new TestCase(new TestCaseStep[]{
                        new CreateEmailPasswordUser(t1, "test1@example.com"),
                        new CreateThirdPartyUser(t2, "google", "googleid", "test2@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new LinkAccounts(t1, 0, 1),
                        new CreateEmailPasswordUser(t3, "test1@example.com"),
                        new AssociateUserToTenant(t2, 2),
                        new MakePrimaryUser(t3, 2).expect(
                                new AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException("", "")),
                }),

                // Case 22: TP+EP linked, associate 3rd EP → allowed (TP primary)
                new TestCase(new TestCaseStep[]{
                        new CreateThirdPartyUser(t1, "google", "googleid", "test1@example.com"),
                        new CreateEmailPasswordUser(t2, "test2@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new LinkAccounts(t1, 0, 1),
                        new CreateEmailPasswordUser(t3, "test1@example.com"),
                        new AssociateUserToTenant(t1, 2),
                        new AssociateUserToTenant(t2, 2), // Allowed
                }),

                // Case 23: TP+EP linked, associate 3rd EP (primary) → AnotherPrimaryWithEmail
                new TestCase(new TestCaseStep[]{
                        new CreateThirdPartyUser(t1, "google", "googleid", "test1@example.com"),
                        new CreateEmailPasswordUser(t2, "test2@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new LinkAccounts(t1, 0, 1),
                        new CreateEmailPasswordUser(t3, "test1@example.com"),
                        new MakePrimaryUser(t3, 2),
                        new AssociateUserToTenant(t2, 2).expect(
                                new AnotherPrimaryUserWithEmailAlreadyExistsException("")),
                }),

                // Case 24: TP+EP linked, associate then MakePrimary → AccountInfoAlreadyAssociated
                new TestCase(new TestCaseStep[]{
                        new CreateThirdPartyUser(t1, "google", "googleid", "test1@example.com"),
                        new CreateEmailPasswordUser(t2, "test2@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new LinkAccounts(t1, 0, 1),
                        new CreateEmailPasswordUser(t3, "test1@example.com"),
                        new AssociateUserToTenant(t2, 2),
                        new MakePrimaryUser(t3, 2).expect(
                                new AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException("", "")),
                }),

                // Case 28: Pless phone dup + AnotherPrimaryWithPhone on associate
                new TestCase(new TestCaseStep[]{
                        new CreatePlessUserWithPhone(t1, "+1000001"),
                        new CreatePlessUserWithPhone(t2, "+1000002"),
                        new CreatePlessUserWithPhone(t3, "+1000001"),
                        new MakePrimaryUser(t1, 0),
                        new LinkAccounts(t1, 0, 1),
                        new MakePrimaryUser(t3, 2),
                        new AssociateUserToTenant(t1, 2).expect(new DuplicatePhoneNumberException()),
                        new AssociateUserToTenant(t2, 2).expect(
                                new AnotherPrimaryUserWithPhoneNumberAlreadyExistsException("")),
                }),

                // Case 29: TP ID dup + AnotherPrimaryWithTP on associate
                new TestCase(new TestCaseStep[]{
                        new CreateThirdPartyUser(t1, "google", "googleid1", "test1@example.com"),
                        new CreateThirdPartyUser(t2, "google", "googleid2", "test2@example.com"),
                        new CreateThirdPartyUser(t3, "google", "googleid1", "test3@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new LinkAccounts(t1, 0, 1),
                        new MakePrimaryUser(t3, 2),
                        new AssociateUserToTenant(t1, 2).expect(new DuplicateThirdPartyUserException()),
                        new AssociateUserToTenant(t2, 2).expect(
                                new AnotherPrimaryUserWithThirdPartyInfoAlreadyExistsException("")),
                })
        );
    }
}
