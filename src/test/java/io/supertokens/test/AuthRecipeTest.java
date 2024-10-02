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

import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.authRecipe.AuthRecipe;
import io.supertokens.authRecipe.UserPaginationContainer;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.emailverification.EmailVerification;
import io.supertokens.emailverification.exception.EmailVerificationInvalidTokenException;
import io.supertokens.passwordless.Passwordless;
import io.supertokens.passwordless.Passwordless.CreateCodeResponse;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.session.Session;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.thirdparty.ThirdParty;
import io.supertokens.usermetadata.UserMetadata;
import io.supertokens.version.Version;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.reflections.Reflections;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class AuthRecipeTest {
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
    public void getUsersCount() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        {
            long count = AuthRecipe.getUsersCount(process.getProcess(), null);
            assert (count == 0);
        }

        EmailPassword.signUp(process.getProcess(), "test0@example.com", "password0");
        EmailPassword.signUp(process.getProcess(), "test1@example.com", "password1");

        {
            long count = AuthRecipe.getUsersCount(process.getProcess(), null);
            assert (count == 2);
        }

        {
            long count = AuthRecipe.getUsersCount(process.getProcess(), new RECIPE_ID[]{RECIPE_ID.EMAIL_PASSWORD});
            assert (count == 2);
        }

        {
            long count = AuthRecipe.getUsersCount(process.getProcess(), new RECIPE_ID[]{RECIPE_ID.THIRD_PARTY});
            assert (count == 0);
        }

        {
            long count = AuthRecipe.getUsersCount(process.getProcess(), new RECIPE_ID[]{RECIPE_ID.PASSWORDLESS});
            assert (count == 0);
        }

        String thirdPartyId = "testThirdParty";
        String thirdPartyUserId_1 = "thirdPartyUserIdA";
        String email_1 = "testA@example.com";

        ThirdParty.signInUp(process.getProcess(), thirdPartyId, thirdPartyUserId_1, email_1);

        {
            long count = AuthRecipe.getUsersCount(process.getProcess(), new RECIPE_ID[]{});
            assert (count == 3);
        }

        {
            long count = AuthRecipe.getUsersCount(process.getProcess(),
                    new RECIPE_ID[]{RECIPE_ID.EMAIL_PASSWORD, RECIPE_ID.THIRD_PARTY});
            assert (count == 3);
        }

        {
            long count = AuthRecipe.getUsersCount(process.getProcess(), new RECIPE_ID[]{RECIPE_ID.EMAIL_PASSWORD});
            assert (count == 2);
        }

        {
            long count = AuthRecipe.getUsersCount(process.getProcess(), new RECIPE_ID[]{RECIPE_ID.THIRD_PARTY});
            assert (count == 1);
        }

        {
            long count = AuthRecipe.getUsersCount(process.getProcess(), new RECIPE_ID[]{RECIPE_ID.PASSWORDLESS});
            assert (count == 0);
        }

        {
            CreateCodeResponse createCode = Passwordless.createCode(process.getProcess(), "testX@example.com", null,
                    null, null);
            Passwordless.consumeCode(process.getProcess(), null, createCode.deviceIdHash, null, createCode.linkCode);
        }
        {
            CreateCodeResponse createCode = Passwordless.createCode(process.getProcess(), null, "+442071838750", null,
                    null);
            Passwordless.consumeCode(process.getProcess(), null, createCode.deviceIdHash, null, createCode.linkCode);
        }

        {
            long count = AuthRecipe.getUsersCount(process.getProcess(), new RECIPE_ID[]{});
            assert (count == 5);
        }

        {
            long count = AuthRecipe.getUsersCount(process.getProcess(),
                    new RECIPE_ID[]{RECIPE_ID.EMAIL_PASSWORD, RECIPE_ID.PASSWORDLESS});
            assert (count == 4);
        }

        {
            long count = AuthRecipe.getUsersCount(process.getProcess(), new RECIPE_ID[]{RECIPE_ID.EMAIL_PASSWORD});
            assert (count == 2);
        }

        {
            long count = AuthRecipe.getUsersCount(process.getProcess(), new RECIPE_ID[]{RECIPE_ID.THIRD_PARTY});
            assert (count == 1);
        }

        {
            long count = AuthRecipe.getUsersCount(process.getProcess(), new RECIPE_ID[]{RECIPE_ID.PASSWORDLESS});
            assert (count == 2);
        }
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void paginationTest() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        {
            UserPaginationContainer users = AuthRecipe.getUsers(process.getProcess(), 100, "ASC", null, null, null);
            assert (users.nextPaginationToken == null);
            assert (users.users.length == 0);
        }

        {
            UserPaginationContainer users = AuthRecipe.getUsers(process.getProcess(), 100, "ASC", null,
                    new RECIPE_ID[]{RECIPE_ID.EMAIL_PASSWORD, RECIPE_ID.THIRD_PARTY}, null);
            assert (users.nextPaginationToken == null);
            assert (users.users.length == 0);
        }

        AuthRecipeUserInfo user1 = EmailPassword.signUp(process.getProcess(), "test0@example.com", "password0");
        AuthRecipeUserInfo user2 = EmailPassword.signUp(process.getProcess(), "test1@example.com", "password1");
        AuthRecipeUserInfo user3 = EmailPassword.signUp(process.getProcess(), "test20@example.com", "password2");
        AuthRecipeUserInfo user4 = EmailPassword.signUp(process.getProcess(), "test3@example.com", "password3");

        {
            UserPaginationContainer users = AuthRecipe.getUsers(process.getProcess(), 100, "ASC", null,
                    new RECIPE_ID[]{RECIPE_ID.EMAIL_PASSWORD}, null);
            assert (users.nextPaginationToken == null);
            assert (users.users.length == 4);
            assert (users.users[0].loginMethods[0].recipeId.toString().equals("emailpassword"));
            assert (users.users[0].equals(user1));
            assert (users.users[1].loginMethods[0].recipeId.toString().equals("emailpassword"));
            assert (users.users[1].equals(user2));
            assert (users.users[2].loginMethods[0].recipeId.toString().equals("emailpassword"));
            assert (users.users[2].equals(user3));
            assert (users.users[3].loginMethods[0].recipeId.toString().equals("emailpassword"));
            assert (users.users[3].equals(user4));

        }

        {
            UserPaginationContainer users = AuthRecipe.getUsers(process.getProcess(), 100, "ASC", null,
                    new RECIPE_ID[]{RECIPE_ID.EMAIL_PASSWORD, RECIPE_ID.THIRD_PARTY}, null);
            assert (users.nextPaginationToken == null);
            assert (users.users.length == 4);
            assert (users.users[0].loginMethods[0].recipeId.toString().equals("emailpassword"));
            assert (users.users[0].equals(user1));
            assert (users.users[1].loginMethods[0].recipeId.toString().equals("emailpassword"));
            assert (users.users[1].equals(user2));
            assert (users.users[2].loginMethods[0].recipeId.toString().equals("emailpassword"));
            assert (users.users[2].equals(user3));
            assert (users.users[3].loginMethods[0].recipeId.toString().equals("emailpassword"));
            assert (users.users[3].equals(user4));
        }

        {
            UserPaginationContainer users = AuthRecipe.getUsers(process.getProcess(), 100, "DESC", null,
                    new RECIPE_ID[]{RECIPE_ID.EMAIL_PASSWORD}, null);
            assert (users.nextPaginationToken == null);
            assert (users.users.length == 4);
            assert (users.users[3].loginMethods[0].recipeId.toString().equals("emailpassword"));
            assert (users.users[3].equals(user1));
            assert (users.users[2].loginMethods[0].recipeId.toString().equals("emailpassword"));
            assert (users.users[2].equals(user2));
            assert (users.users[1].loginMethods[0].recipeId.toString().equals("emailpassword"));
            assert (users.users[1].equals(user3));
            assert (users.users[0].loginMethods[0].recipeId.toString().equals("emailpassword"));
            assert (users.users[0].equals(user4));

        }

        {
            UserPaginationContainer users = AuthRecipe.getUsers(process.getProcess(), 100, "DESC", null,
                    new RECIPE_ID[]{RECIPE_ID.THIRD_PARTY, RECIPE_ID.EMAIL_PASSWORD}, null);
            assert (users.nextPaginationToken == null);
            assert (users.users.length == 4);
            assert (users.users[3].loginMethods[0].recipeId.toString().equals("emailpassword"));
            assert (users.users[3].equals(user1));
            assert (users.users[2].loginMethods[0].recipeId.toString().equals("emailpassword"));
            assert (users.users[2].equals(user2));
            assert (users.users[1].loginMethods[0].recipeId.toString().equals("emailpassword"));
            assert (users.users[1].equals(user3));
            assert (users.users[0].loginMethods[0].recipeId.toString().equals("emailpassword"));
            assert (users.users[0].equals(user4));
        }

        {
            UserPaginationContainer users = AuthRecipe.getUsers(process.getProcess(), 100, "DESC", null,
                    new RECIPE_ID[]{RECIPE_ID.THIRD_PARTY, RECIPE_ID.SESSION}, null);
            assert (users.nextPaginationToken == null);
            assert (users.users.length == 0);
        }

        {
            UserPaginationContainer users = AuthRecipe.getUsers(process.getProcess(), 2, "DESC", null,
                    new RECIPE_ID[]{RECIPE_ID.THIRD_PARTY, RECIPE_ID.EMAIL_PASSWORD}, null);
            assert (users.nextPaginationToken != null);
            assert (users.users.length == 2);
            assert (users.users[1].loginMethods[0].recipeId.toString().equals("emailpassword"));
            assert (users.users[1].equals(user3));
            assert (users.users[0].loginMethods[0].recipeId.toString().equals("emailpassword"));
            assert (users.users[0].equals(user4));
        }

        {
            UserPaginationContainer users = AuthRecipe.getUsers(process.getProcess(), 3, "ASC", null,
                    new RECIPE_ID[]{RECIPE_ID.THIRD_PARTY, RECIPE_ID.EMAIL_PASSWORD}, null);
            assert (users.nextPaginationToken != null);
            assert (users.users.length == 3);
            assert (users.users[0].loginMethods[0].recipeId.toString().equals("emailpassword"));
            assert (users.users[0].equals(user1));
            assert (users.users[1].loginMethods[0].recipeId.toString().equals("emailpassword"));
            assert (users.users[1].equals(user2));
            assert (users.users[2].loginMethods[0].recipeId.toString().equals("emailpassword"));
            assert (users.users[2].equals(user3));
        }

        ///////////////////////////////////////////////////////////////////////////////////

        String thirdPartyId = "testThirdParty";
        String thirdPartyUserId_1 = "thirdPartyUserIdA";
        String email_1 = "testA@example.com";

        AuthRecipeUserInfo user5 = ThirdParty.signInUp(process.getProcess(),
                thirdPartyId, thirdPartyUserId_1, email_1).user;

        {
            UserPaginationContainer users = AuthRecipe.getUsers(process.getProcess(), 100, "ASC", null,
                    new RECIPE_ID[]{RECIPE_ID.EMAIL_PASSWORD}, null);
            assert (users.nextPaginationToken == null);
            assert (users.users.length == 4);
            assert (users.users[0].loginMethods[0].recipeId.toString().equals("emailpassword"));
            assert (users.users[0].equals(user1));
            assert (users.users[1].loginMethods[0].recipeId.toString().equals("emailpassword"));
            assert (users.users[1].equals(user2));
            assert (users.users[2].loginMethods[0].recipeId.toString().equals("emailpassword"));
            assert (users.users[2].equals(user3));
            assert (users.users[3].loginMethods[0].recipeId.toString().equals("emailpassword"));
            assert (users.users[3].equals(user4));

        }

        {
            UserPaginationContainer users = AuthRecipe.getUsers(process.getProcess(), 100, "ASC", null,
                    new RECIPE_ID[]{RECIPE_ID.THIRD_PARTY}, null);
            assert (users.nextPaginationToken == null);
            assert (users.users.length == 1);
            assert (users.users[0].loginMethods[0].recipeId.toString().equals("thirdparty"));
            assert (users.users[0].equals(user5));

        }

        {
            UserPaginationContainer users = AuthRecipe.getUsers(process.getProcess(), 100, "ASC", null,
                    new RECIPE_ID[]{RECIPE_ID.EMAIL_PASSWORD, RECIPE_ID.THIRD_PARTY}, null);
            assert (users.nextPaginationToken == null);
            assert (users.users.length == 5);
            assert (users.users[0].loginMethods[0].recipeId.toString().equals("emailpassword"));
            assert (users.users[0].equals(user1));
            assert (users.users[1].loginMethods[0].recipeId.toString().equals("emailpassword"));
            assert (users.users[1].equals(user2));
            assert (users.users[2].loginMethods[0].recipeId.toString().equals("emailpassword"));
            assert (users.users[2].equals(user3));
            assert (users.users[3].loginMethods[0].recipeId.toString().equals("emailpassword"));
            assert (users.users[3].equals(user4));
            assert (users.users[4].loginMethods[0].recipeId.toString().equals("thirdparty"));
            assert (users.users[4].equals(user5));
        }

        {
            UserPaginationContainer users = AuthRecipe.getUsers(process.getProcess(), 100, "DESC", null,
                    new RECIPE_ID[]{RECIPE_ID.EMAIL_PASSWORD}, null);
            assert (users.nextPaginationToken == null);
            assert (users.users.length == 4);
            assert (users.users[3].loginMethods[0].recipeId.toString().equals("emailpassword"));
            assert (users.users[3].equals(user1));
            assert (users.users[2].loginMethods[0].recipeId.toString().equals("emailpassword"));
            assert (users.users[2].equals(user2));
            assert (users.users[1].loginMethods[0].recipeId.toString().equals("emailpassword"));
            assert (users.users[1].equals(user3));
            assert (users.users[0].loginMethods[0].recipeId.toString().equals("emailpassword"));
            assert (users.users[0].equals(user4));

        }

        {
            UserPaginationContainer users = AuthRecipe.getUsers(process.getProcess(), 100, "DESC", null,
                    new RECIPE_ID[]{RECIPE_ID.THIRD_PARTY}, null);
            assert (users.nextPaginationToken == null);
            assert (users.users.length == 1);
            assert (users.users[0].loginMethods[0].recipeId.toString().equals("thirdparty"));
            assert (users.users[0].equals(user5));

        }

        {
            UserPaginationContainer users = AuthRecipe.getUsers(process.getProcess(), 100, "DESC", null,
                    new RECIPE_ID[]{}, null);
            assert (users.nextPaginationToken == null);
            assert (users.users.length == 5);
            assert (users.users[4].loginMethods[0].recipeId.toString().equals("emailpassword"));
            assert (users.users[4].equals(user1));
            assert (users.users[3].loginMethods[0].recipeId.toString().equals("emailpassword"));
            assert (users.users[3].equals(user2));
            assert (users.users[2].loginMethods[0].recipeId.toString().equals("emailpassword"));
            assert (users.users[2].equals(user3));
            assert (users.users[1].loginMethods[0].recipeId.toString().equals("emailpassword"));
            assert (users.users[1].equals(user4));
            assert (users.users[0].loginMethods[0].recipeId.toString().equals("thirdparty"));
            assert (users.users[0].equals(user5));
        }

        {
            UserPaginationContainer users = AuthRecipe.getUsers(process.getProcess(), 100, "DESC", null,
                    new RECIPE_ID[]{RECIPE_ID.SESSION}, null);
            assert (users.nextPaginationToken == null);
            assert (users.users.length == 0);
        }

        {
            UserPaginationContainer users = AuthRecipe.getUsers(process.getProcess(), 2, "DESC", null,
                    new RECIPE_ID[]{RECIPE_ID.THIRD_PARTY, RECIPE_ID.EMAIL_PASSWORD}, null);
            assert (users.nextPaginationToken != null);
            assert (users.users.length == 2);
            assert (users.users[1].loginMethods[0].recipeId.toString().equals("emailpassword"));
            assert (users.users[1].equals(user4));
            assert (users.users[0].loginMethods[0].recipeId.toString().equals("thirdparty"));
            assert (users.users[0].equals(user5));
        }

        {
            UserPaginationContainer users = AuthRecipe.getUsers(process.getProcess(), 3, "ASC", null,
                    new RECIPE_ID[]{RECIPE_ID.THIRD_PARTY, RECIPE_ID.EMAIL_PASSWORD}, null);
            assert (users.nextPaginationToken != null);
            assert (users.users.length == 3);
            assert (users.users[0].loginMethods[0].recipeId.toString().equals("emailpassword"));
            assert (users.users[0].equals(user1));
            assert (users.users[1].loginMethods[0].recipeId.toString().equals("emailpassword"));
            assert (users.users[1].equals(user2));
            assert (users.users[2].loginMethods[0].recipeId.toString().equals("emailpassword"));
            assert (users.users[2].equals(user3));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void randomPaginationTest() throws Exception {
        int numberOfUsers = 500;
        int[] limits = new int[]{10, 14, 20, 23, 50, 100, 110, 150, 200, 510};
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        Map<String, Function<Object, AuthRecipeUserInfo>> signUpMap = getSignUpMap(process);

        List<String> authRecipes = getAuthRecipes();

        for (String className : authRecipes) {
            if (!signUpMap.containsKey(className)) {
                fail();
            }
        }

        // we create multiple users in parallel...
        List<AuthRecipeUserInfo> usersCreated = new ArrayList<>();

        ExecutorService es = Executors.newFixedThreadPool(500);
        for (int i = 0; i < numberOfUsers; i++) {
            if (Math.random() > 0.5) {
                while (true) {
                    String currUserType = authRecipes.get((int) (Math.random() * authRecipes.size()));
                    AuthRecipeUserInfo user = signUpMap.get(currUserType).apply(null);
                    if (user != null) {
                        synchronized (usersCreated) {
                            usersCreated.add(user);
                            break;
                        }
                    }
                }
            } else {
                es.execute(() -> {
                    while (true) {
                        String currUserType = authRecipes.get((int) (Math.random() * authRecipes.size()));
                        AuthRecipeUserInfo user = signUpMap.get(currUserType).apply(null);
                        if (user != null) {
                            synchronized (usersCreated) {
                                usersCreated.add(user);
                                return;
                            }
                        }
                    }
                });
            }
        }
        es.shutdown();
        boolean finished = es.awaitTermination(2, TimeUnit.MINUTES);

        if (!finished || usersCreated.size() != numberOfUsers) {
            fail();
        }

        boolean isMySQL = Version.getVersion(process.getProcess()).getPluginName().equals("mysql");

        for (int limit : limits) {

            // now we paginate in asc order
            {
                // we sort users in usersCreated based on timeJoined since we added them in parallel...
                usersCreated.sort((o1, o2) -> {
                    if (o1.timeJoined != o2.timeJoined) {
                        return (int) (o1.timeJoined - o2.timeJoined);
                    }
                    if (isMySQL) {
                        return o1.getSupertokensUserId().compareTo(o2.getSupertokensUserId());
                    }
                    return o2.getSupertokensUserId().compareTo(o1.getSupertokensUserId());
                });

                // we make sure it's sorted properly..
                {
                    long timeJoined = 0;
                    for (AuthRecipeUserInfo u : usersCreated) {
                        if (timeJoined > u.timeJoined) {
                            fail();
                        }
                    }
                }

                int indexIntoUsers = 0;
                String paginationToken = null;
                do {
                    UserPaginationContainer users = AuthRecipe.getUsers(process.getProcess(), limit, "ASC",
                            paginationToken, null, null);

                    for (AuthRecipeUserInfo uc : users.users) {
                        AuthRecipeUserInfo expected = usersCreated.get(indexIntoUsers);
                        AuthRecipeUserInfo actualUser = uc;

                        assert (actualUser.equals(expected) && uc.loginMethods[0].recipeId.toString()
                                .equals(expected.loginMethods[0].recipeId.toString()));
                        indexIntoUsers++;
                    }

                    paginationToken = users.nextPaginationToken;
                } while (paginationToken != null);

                assert (indexIntoUsers == usersCreated.size());
            }

            // now we paginate in desc order
            {
                // we sort users in usersCreated based on timeJoined since we added them in parallel...
                usersCreated.sort((o1, o2) -> {
                    if (o1.timeJoined != o2.timeJoined) {
                        return (int) (o1.timeJoined - o2.timeJoined);
                    }
                    return o1.getSupertokensUserId().compareTo(o2.getSupertokensUserId());
                });

                // we make sure it's sorted properly..
                {
                    long timeJoined = 0;
                    for (AuthRecipeUserInfo u : usersCreated) {
                        if (timeJoined > u.timeJoined) {
                            fail();
                        }
                    }
                }
                int indexIntoUsers = usersCreated.size() - 1;
                String paginationToken = null;
                do {
                    UserPaginationContainer users = AuthRecipe.getUsers(process.getProcess(), limit, "DESC",
                            paginationToken, null, null);

                    for (AuthRecipeUserInfo uc : users.users) {
                        AuthRecipeUserInfo expected = usersCreated.get(indexIntoUsers);
                        AuthRecipeUserInfo actualUser = uc;

                        assert (actualUser.equals(expected) && uc.loginMethods[0].recipeId.toString()
                                .equals(expected.loginMethods[0].recipeId.toString()));
                        indexIntoUsers--;
                    }

                    paginationToken = users.nextPaginationToken;
                } while (paginationToken != null);

                assert (indexIntoUsers == -1);
            }
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void deleteUserTest() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        Map<String, Function<Object, AuthRecipeUserInfo>> signUpMap = getSignUpMap(process);

        List<String> authRecipes = getAuthRecipes();

        for (String className : authRecipes) {
            if (!signUpMap.containsKey(className)) {
                fail();
            }
        }

        for (String userType : authRecipes) {
            AuthRecipeUserInfo user1 = signUpMap.get(userType).apply(null);
            JsonObject testMetadata = new JsonObject();
            testMetadata.addProperty("test", "test");
            UserMetadata.updateUserMetadata(process.getProcess(), user1.getSupertokensUserId(), testMetadata);
            Session.createNewSession(process.getProcess(), user1.getSupertokensUserId(), new JsonObject(),
                    new JsonObject());
            String emailVerificationToken = EmailVerification.generateEmailVerificationToken(process.getProcess(),
                    user1.getSupertokensUserId(), "email");
            EmailVerification.verifyEmail(process.getProcess(), emailVerificationToken);

            AuthRecipeUserInfo user2 = signUpMap.get(userType).apply(null);
            Session.createNewSession(process.getProcess(), user2.getSupertokensUserId(), new JsonObject(),
                    new JsonObject());
            String emailVerificationToken2 = EmailVerification.generateEmailVerificationToken(process.getProcess(),
                    user2.getSupertokensUserId(), "email");

            assertEquals(2,
                    AuthRecipe.getUsersCount(process.getProcess(), new RECIPE_ID[]{user1.loginMethods[0].recipeId}));
            AuthRecipe.deleteUser(process.getProcess(), user1.getSupertokensUserId());
            assertEquals(1,
                    AuthRecipe.getUsersCount(process.getProcess(), new RECIPE_ID[]{user1.loginMethods[0].recipeId}));
            assertEquals(0, Session.getAllNonExpiredSessionHandlesForUser(process.getProcess(),
                    user1.getSupertokensUserId()).length);
            assertEquals(1, Session.getAllNonExpiredSessionHandlesForUser(process.getProcess(),
                    user2.getSupertokensUserId()).length);
            assertFalse(EmailVerification.isEmailVerified(process.getProcess(), user1.getSupertokensUserId(), "email"));
            assertEquals(0,
                    UserMetadata.getUserMetadata(process.getProcess(), user1.getSupertokensUserId()).entrySet().size());

            AuthRecipe.deleteUser(process.getProcess(), user2.getSupertokensUserId());
            assertEquals(0,
                    AuthRecipe.getUsersCount(process.getProcess(), new RECIPE_ID[]{user1.loginMethods[0].recipeId}));
            assertEquals(0, Session.getAllNonExpiredSessionHandlesForUser(process.getProcess(),
                    user2.getSupertokensUserId()).length);
            assertEquals(0,
                    UserMetadata.getUserMetadata(process.getProcess(), user2.getSupertokensUserId()).entrySet().size());

            Exception error = null;
            try {
                EmailVerification.verifyEmail(process.getProcess(), emailVerificationToken2);
            } catch (EmailVerificationInvalidTokenException ex) {
                error = ex;
            }
            assertNotNull(error);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    private static List<String> getAuthRecipes() {
        return Arrays.asList("emailpassword", "thirdparty", "passwordless");
    }

    private static Map<String, Function<Object, AuthRecipeUserInfo>> getSignUpMap(
            TestingProcessManager.TestingProcess process) {
        AtomicInteger count = new AtomicInteger();

        Map<String, Function<Object, AuthRecipeUserInfo>> signUpMap = new HashMap<>();
        signUpMap.put("emailpassword", o -> {
            try {
                return EmailPassword.signUp(process.getProcess(), "test" + count.getAndIncrement() + "@example.com",
                        "password0");
            } catch (Exception ignored) {
            }
            return null;
        });
        signUpMap.put("thirdparty", o -> {
            try {
                String thirdPartyId = "testThirdParty";
                String thirdPartyUserId = "thirdPartyUserId" + count.getAndIncrement();
                String email = "test" + count.getAndIncrement() + "@example.com";

                return ThirdParty.signInUp(process.getProcess(), thirdPartyId, thirdPartyUserId, email).user;
            } catch (Exception ignored) {
            }
            return null;
        });
        signUpMap.put("passwordless", o -> {
            try {
                String email = "test" + count.getAndIncrement() + "@example.com";
                CreateCodeResponse createCode = Passwordless.createCode(process.getProcess(), email, null, null, null);
                return Passwordless.consumeCode(process.getProcess(), null, createCode.deviceIdHash, null,
                        createCode.linkCode).user;
            } catch (Exception ignored) {
            }
            return null;
        });

        // add more recipes...

        return signUpMap;
    }
}
