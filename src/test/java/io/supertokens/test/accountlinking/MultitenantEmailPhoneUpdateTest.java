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

import io.supertokens.emailpassword.exceptions.EmailChangeNotAllowedException;
import io.supertokens.passwordless.exceptions.PhoneNumberChangeNotAllowedException;
import org.junit.Test;

/**
 * Tests for email/phone update restrictions with account linking.
 * Verifies that email/phone updates are blocked when they would conflict
 * with another primary user, and allowed when safe.
 *
 * Extracted from MultitenantTest.testVariousCases (cases 12-18, 38-43).
 */
public class MultitenantEmailPhoneUpdateTest extends MultitenantTestBase {

    @Test
    public void testEmailPhoneUpdateRestrictionsWithLinkedAccounts() throws Exception {
        initTenantIdentifiers();

        runTestCases(
                // Case 12: Pless email update allowed (cross-tenant, different recipes)
                new TestCase(new TestCaseStep[]{
                        new CreatePlessUserWithEmail(t1, "test1@example.com"),
                        new CreatePlessUserWithEmail(t2, "test2@example.com"),
                        new CreateEmailPasswordUser(t2, "test2@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new LinkAccounts(t1, 0, 2),
                        new UpdatePlessUserEmail(t1, 0, "test2@example.com"),
                }),

                // Case 13: Pless email update blocked (same tenant, conflicting primary)
                new TestCase(new TestCaseStep[]{
                        new CreatePlessUserWithEmail(t1, "test1@example.com"),
                        new CreatePlessUserWithEmail(t1, "test3@example.com"),
                        new CreateEmailPasswordUser(t2, "test2@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new LinkAccounts(t1, 0, 2),
                        new MakePrimaryUser(t2, 1),
                        new UpdatePlessUserEmail(t1, 0, "test3@example.com").expect(
                                new EmailChangeNotAllowedException()),
                }),

                // Case 14: Pless email update blocked (reverse direction)
                new TestCase(new TestCaseStep[]{
                        new CreatePlessUserWithEmail(t1, "test1@example.com"),
                        new CreatePlessUserWithEmail(t1, "test3@example.com"),
                        new CreateEmailPasswordUser(t2, "test2@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new LinkAccounts(t1, 0, 2),
                        new MakePrimaryUser(t2, 1),
                        new UpdatePlessUserEmail(t1, 1, "test1@example.com").expect(
                                new EmailChangeNotAllowedException()),
                }),

                // Case 15: Pless phone update blocked (same tenant, conflicting primary)
                new TestCase(new TestCaseStep[]{
                        new CreatePlessUserWithPhone(t1, "+1000001"),
                        new CreatePlessUserWithPhone(t1, "+1000003"),
                        new CreatePlessUserWithPhone(t2, "+1000002"),
                        new MakePrimaryUser(t1, 0),
                        new LinkAccounts(t1, 0, 2),
                        new MakePrimaryUser(t2, 1),
                        new UpdatePlessUserPhone(t1, 0, "+1000003").expect(new PhoneNumberChangeNotAllowedException()),
                }),

                // Case 16: Pless phone update blocked (reverse direction)
                new TestCase(new TestCaseStep[]{
                        new CreatePlessUserWithPhone(t1, "+1000001"),
                        new CreatePlessUserWithPhone(t1, "+1000003"),
                        new CreatePlessUserWithPhone(t2, "+1000002"),
                        new MakePrimaryUser(t1, 0),
                        new LinkAccounts(t1, 0, 2),
                        new MakePrimaryUser(t2, 1),
                        new UpdatePlessUserPhone(t1, 1, "+1000001").expect(new PhoneNumberChangeNotAllowedException()),
                }),

                // Case 17: EP email update blocked (same tenant, conflicting primary)
                new TestCase(new TestCaseStep[]{
                        new CreateEmailPasswordUser(t1, "test1@example.com"),
                        new CreateEmailPasswordUser(t1, "test3@example.com"),
                        new CreateEmailPasswordUser(t2, "test2@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new LinkAccounts(t1, 0, 2),
                        new MakePrimaryUser(t2, 1),
                        new UpdateEmailPasswordUserEmail(t1, 0, "test3@example.com").expect(
                                new EmailChangeNotAllowedException()),
                }),

                // Case 18: EP email update blocked (reverse direction)
                new TestCase(new TestCaseStep[]{
                        new CreateEmailPasswordUser(t1, "test1@example.com"),
                        new CreateEmailPasswordUser(t1, "test3@example.com"),
                        new CreateEmailPasswordUser(t2, "test2@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new LinkAccounts(t1, 0, 2),
                        new MakePrimaryUser(t2, 1),
                        new UpdateEmailPasswordUserEmail(t1, 1, "test1@example.com").expect(
                                new EmailChangeNotAllowedException()),
                }),

                // Case 38: EP+Pless same tenant, update pless email to match EP (allowed, non-primary pless)
                new TestCase(new TestCaseStep[]{
                        new CreateEmailPasswordUser(t1, "test1@example.com"),
                        new CreatePlessUserWithEmail(t1, "test2@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new UpdatePlessUserEmail(t1, 1, "test1@example.com"),
                }),
                // Case 39: Pless+EP same tenant, update EP email to match pless (allowed)
                new TestCase(new TestCaseStep[]{
                        new CreatePlessUserWithEmail(t1, "test1@example.com"),
                        new CreateEmailPasswordUser(t1, "test2@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new UpdateEmailPasswordUserEmail(t1, 1, "test1@example.com"),
                }),

                // Case 40: EP+Pless same tenant, pless is primary, update pless email (allowed)
                new TestCase(new TestCaseStep[]{
                        new CreateEmailPasswordUser(t1, "test1@example.com"),
                        new CreatePlessUserWithEmail(t1, "test2@example.com"),
                        new MakePrimaryUser(t1, 1),
                        new UpdatePlessUserEmail(t1, 1, "test1@example.com"),
                }),
                // Case 41: Pless+EP same tenant, EP is primary, update EP email (allowed)
                new TestCase(new TestCaseStep[]{
                        new CreatePlessUserWithEmail(t1, "test1@example.com"),
                        new CreateEmailPasswordUser(t1, "test2@example.com"),
                        new MakePrimaryUser(t1, 1),
                        new UpdateEmailPasswordUserEmail(t1, 1, "test1@example.com"),
                }),

                // Case 42: EP+Pless both primary, update pless email → EmailChangeNotAllowed
                new TestCase(new TestCaseStep[]{
                        new CreateEmailPasswordUser(t1, "test1@example.com"),
                        new CreatePlessUserWithEmail(t1, "test2@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new MakePrimaryUser(t1, 1),
                        new UpdatePlessUserEmail(t1, 1, "test1@example.com").expect(
                                new EmailChangeNotAllowedException()),
                }),
                // Case 43: Pless+EP both primary, update EP email → EmailChangeNotAllowed
                new TestCase(new TestCaseStep[]{
                        new CreatePlessUserWithEmail(t1, "test1@example.com"),
                        new CreateEmailPasswordUser(t1, "test2@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new MakePrimaryUser(t1, 1),
                        new UpdateEmailPasswordUserEmail(t1, 1, "test1@example.com").expect(
                                new EmailChangeNotAllowedException()),
                })
        );
    }
}
