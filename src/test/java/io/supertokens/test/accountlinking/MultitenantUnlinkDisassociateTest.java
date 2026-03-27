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

import io.supertokens.Main;
import io.supertokens.authRecipe.AuthRecipe;
import io.supertokens.authRecipe.exception.RecipeUserIdAlreadyLinkedWithAnotherPrimaryUserIdException;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicateEmailException;
import io.supertokens.pluginInterface.emailpassword.exceptions.UnknownUserIdException;
import io.supertokens.storageLayer.StorageLayer;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for unlink, disassociate, and re-association flows with account linking.
 * Verifies behavior when unlinking primary users, disassociating from tenants,
 * and re-associating to different tenants.
 *
 * Extracted from MultitenantTest.testVariousCases (cases 31-37).
 */
public class MultitenantUnlinkDisassociateTest extends MultitenantTestBase {

    @Test
    public void testUnlinkAndDisassociateFlows() throws Exception {
        initTenantIdentifiers();

        runTestCases(
                // Case 31: EP: Unlink primary → AssociateToTenant → UnknownUserIdException
                new TestCase(new TestCaseStep[]{
                        new CreateEmailPasswordUser(t1, "test@example.com"),
                        new CreateEmailPasswordUser(t1, "test2@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new LinkAccounts(t1, 0, 1),
                        new UnlinkAccount(t1, 0),
                        new AssociateUserToTenant(t2, 0).expect(new UnknownUserIdException()),
                }),

                // Case 32: Pless: Unlink primary → AssociateToTenant → UnknownUserIdException
                new TestCase(new TestCaseStep[]{
                        new CreatePlessUserWithEmail(t1, "test@example.com"),
                        new CreatePlessUserWithEmail(t1, "test2@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new LinkAccounts(t1, 0, 1),
                        new UnlinkAccount(t1, 0),
                        new AssociateUserToTenant(t2, 0).expect(new UnknownUserIdException()),
                }),

                // Case 33: TP: Unlink primary → AssociateToTenant → UnknownUserIdException
                new TestCase(new TestCaseStep[]{
                        new CreateThirdPartyUser(t1, "google", "googleid1", "test@example.com"),
                        new CreateThirdPartyUser(t1, "google", "googleid2", "test2@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new LinkAccounts(t1, 0, 1),
                        new UnlinkAccount(t1, 0),
                        new AssociateUserToTenant(t2, 0).expect(new UnknownUserIdException()),
                }),

                // Case 34: EP: Disassociate, reassociate to different tenant, verify membership
                new TestCase(new TestCaseStep[]{
                        new CreateEmailPasswordUser(t1, "test@example.com"),
                        new CreateEmailPasswordUser(t1, "test2@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new LinkAccounts(t1, 0, 1),
                        new DisassociateUserFromTenant(t1, 0),
                        new AssociateUserToTenant(t2, 0),
                        new TestCaseStep() {
                            @Override
                            public void execute(Main main) throws Exception {
                                Storage t1Storage = (StorageLayer.getStorage(t1, main));
                                AuthRecipeUserInfo user = AuthRecipe.getUserById(t1.toAppIdentifier(), t1Storage,
                                        TestCase.users.get(0).getSupertokensUserId());
                                assertEquals(2, user.loginMethods.length);
                                assertTrue(user.loginMethods[0].tenantIds.contains(t2.getTenantId()));
                                assertTrue(user.loginMethods[1].tenantIds.contains(t1.getTenantId()));
                            }
                        }
                }),

                // Case 35: EP: Disassociate both, MakePrimary both, re-associate → DuplicateEmail + link fails
                new TestCase(new TestCaseStep[]{
                        new CreateEmailPasswordUser(t1, "test@example.com"),
                        new DisassociateUserFromTenant(t1, 0),
                        new CreateEmailPasswordUser(t1, "test@example.com"),
                        new DisassociateUserFromTenant(t1, 1),
                        new MakePrimaryUser(t1, 0),
                        new MakePrimaryUser(t1, 1),
                        new AssociateUserToTenant(t1, 0),
                        new AssociateUserToTenant(t1, 1).expect(new DuplicateEmailException()),
                        new LinkAccounts(t1, 0, 1).expect(
                                new RecipeUserIdAlreadyLinkedWithAnotherPrimaryUserIdException(null, "")),
                }),

                // Case 36: EP: Disassociate, MakePrimary, associate, link, re-associate → DuplicateEmail on t1, allowed t2
                new TestCase(new TestCaseStep[]{
                        new CreateEmailPasswordUser(t1, "test@example.com"),
                        new DisassociateUserFromTenant(t1, 0),
                        new CreateEmailPasswordUser(t1, "test@example.com"),
                        new DisassociateUserFromTenant(t1, 1),
                        new MakePrimaryUser(t1, 0),
                        new AssociateUserToTenant(t1, 0),
                        new LinkAccounts(t1, 0, 1),
                        new AssociateUserToTenant(t1, 1).expect(new DuplicateEmailException()),
                        new AssociateUserToTenant(t2, 1),
                }),

                // Case 37: Unlink then delete linked user → primary also gone
                new TestCase(new TestCaseStep[]{
                        new CreateEmailPasswordUser(t1, "test1@example.com"),
                        new CreateEmailPasswordUser(t1, "test2@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new LinkAccounts(t1, 0, 1),
                        new UnlinkAccount(t1, 0),
                        new TestCaseStep() {
                            @Override
                            public void execute(Main main) throws Exception {
                                Storage t1Storage = (StorageLayer.getStorage(t1, main));
                                AuthRecipe.deleteUser(t1.toAppIdentifier(), t1Storage,
                                        TestCase.users.get(1).getSupertokensUserId());
                            }
                        },
                        new TestCaseStep() {
                            @Override
                            public void execute(Main main) throws Exception {
                                Storage t1Storage = (StorageLayer.getStorage(t1, main));
                                AuthRecipeUserInfo user = AuthRecipe.getUserById(t1.toAppIdentifier(), t1Storage,
                                        TestCase.users.get(0).getSupertokensUserId());
                                assertNull(user);
                            }
                        }
                })
        );
    }
}
