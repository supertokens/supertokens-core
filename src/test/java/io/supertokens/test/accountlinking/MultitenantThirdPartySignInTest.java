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
import org.junit.Test;

/**
 * Tests for third-party sign-in/up email change conflicts with account linking.
 * Verifies that TP sign-in email changes are blocked when they would conflict
 * with another primary user in the same tenant.
 *
 * Extracted from MultitenantTest.testVariousCases (cases 25-27, 30, 44-47).
 */
public class MultitenantThirdPartySignInTest extends MultitenantTestBase {

    @Test
    public void testThirdPartySignInEmailConflicts() throws Exception {
        initTenantIdentifiers();

        runTestCases(
                // Case 25: TP sign-in with updated email allowed (cross-tenant)
                new TestCase(new TestCaseStep[]{
                        new CreateThirdPartyUser(t1, "google", "googleid1", "test1@example.com"),
                        new CreateThirdPartyUser(t2, "google", "googleid2", "test2@example.com"),
                        new CreateEmailPasswordUser(t2, "test2@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new LinkAccounts(t1, 0, 2),
                        new CreateThirdPartyUser(t1, "google", "googleid1", "test2@example.com"),
                }),

                // Case 26: TP sign-in email change blocked (conflicting primary, same tenant)
                new TestCase(new TestCaseStep[]{
                        new CreateThirdPartyUser(t1, "google", "googleid1", "test1@example.com"),
                        new CreateThirdPartyUser(t1, "google", "googleid3", "test3@example.com"),
                        new CreateEmailPasswordUser(t2, "test2@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new LinkAccounts(t1, 0, 2),
                        new MakePrimaryUser(t2, 1),
                        new CreateThirdPartyUser(t1, "google", "googleid1", "test3@example.com").expect(
                                new EmailChangeNotAllowedException()),
                }),

                // Case 27: TP sign-in email change blocked (reverse direction)
                new TestCase(new TestCaseStep[]{
                        new CreateThirdPartyUser(t1, "google", "googleid1", "test1@example.com"),
                        new CreateThirdPartyUser(t1, "google", "googleid3", "test3@example.com"),
                        new CreateEmailPasswordUser(t2, "test2@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new LinkAccounts(t1, 0, 2),
                        new MakePrimaryUser(t2, 1),
                        new CreateThirdPartyUser(t1, "google", "googleid3", "test1@example.com").expect(
                                new EmailChangeNotAllowedException()),
                }),

                // Case 30: TP email change blocked within linked group (two TP users, same primary)
                new TestCase(new TestCaseStep[]{
                        new CreateThirdPartyUser(t1, "google", "googleid1", "test1@example.com"),
                        new CreateThirdPartyUser(t2, "google", "googleid2", "test2@example.com"),
                        new CreateThirdPartyUser(t1, "google", "googleid3", "test3@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new LinkAccounts(t1, 0, 1),
                        new MakePrimaryUser(t1, 2),
                        new CreateThirdPartyUser(t1, "google", "googleid1", "test3@example.com").expect(
                                new EmailChangeNotAllowedException()),
                        new CreateThirdPartyUser(t1, "google", "googleid3", "test1@example.com").expect(
                                new EmailChangeNotAllowedException()),
                }),

                // Case 44: TP sign-in no conflict when neither is primary
                new TestCase(new TestCaseStep[]{
                        new CreateEmailPasswordUser(t1, "test1@example.com"),
                        new CreateThirdPartyUser(t1, "google", "googleid", "test2@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new CreateThirdPartyUser(t1, "google", "googleid", "test2@example.com"), // allowed
                }),
                // Case 45: TP sign-in no conflict when TP is primary
                new TestCase(new TestCaseStep[]{
                        new CreateEmailPasswordUser(t1, "test1@example.com"),
                        new CreateThirdPartyUser(t1, "google", "googleid", "test2@example.com"),
                        new MakePrimaryUser(t1, 1),
                        new CreateThirdPartyUser(t1, "google", "googleid", "test2@example.com"), // allowed
                }),
                // Case 46: TP sign-in email conflict when both primary
                new TestCase(new TestCaseStep[]{
                        new CreateEmailPasswordUser(t1, "test1@example.com"),
                        new CreateThirdPartyUser(t1, "google", "googleid", "test2@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new MakePrimaryUser(t1, 1),
                        new CreateThirdPartyUser(t1, "google", "googleid", "test1@example.com").expect(
                                new EmailChangeNotAllowedException()),
                }),
                // Case 47: Cross-tenant TP sign-in email conflict with linked user
                new TestCase(new TestCaseStep[]{
                        new CreateEmailPasswordUser(t1, "test1@example.com"),
                        new CreateEmailPasswordUser(t2, "test3@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new LinkAccounts(t1, 0, 1),
                        new CreateThirdPartyUser(t2, "google", "googleid", "test2@example.com"),
                        new MakePrimaryUser(t2, 2),
                        new CreateThirdPartyUser(t2, "google", "googleid", "test1@example.com").expect(
                                new EmailChangeNotAllowedException()),
                })
        );
    }
}
