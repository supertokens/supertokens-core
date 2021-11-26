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
import io.supertokens.authRecipe.AuthRecipe;
import io.supertokens.authRecipe.UserPaginationContainer;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.passwordless.Passwordless;
import io.supertokens.passwordless.Passwordless.CreateCodeResponse;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.emailpassword.UserInfo;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.thirdparty.ThirdParty;
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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

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
        String[] args = { "../" };

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
            long count = AuthRecipe.getUsersCount(process.getProcess(), new RECIPE_ID[] { RECIPE_ID.EMAIL_PASSWORD });
            assert (count == 2);
        }

        {
            long count = AuthRecipe.getUsersCount(process.getProcess(), new RECIPE_ID[] { RECIPE_ID.THIRD_PARTY });
            assert (count == 0);
        }

        {
            long count = AuthRecipe.getUsersCount(process.getProcess(), new RECIPE_ID[] { RECIPE_ID.PASSWORDLESS });
            assert (count == 0);
        }

        String thirdPartyId = "testThirdParty";
        String thirdPartyUserId_1 = "thirdPartyUserIdA";
        String email_1 = "testA@example.com";

        ThirdParty.signInUp(process.getProcess(), thirdPartyId, thirdPartyUserId_1, email_1);

        {
            long count = AuthRecipe.getUsersCount(process.getProcess(), new RECIPE_ID[] {});
            assert (count == 3);
        }

        {
            long count = AuthRecipe.getUsersCount(process.getProcess(),
                    new RECIPE_ID[] { RECIPE_ID.EMAIL_PASSWORD, RECIPE_ID.THIRD_PARTY });
            assert (count == 3);
        }

        {
            long count = AuthRecipe.getUsersCount(process.getProcess(), new RECIPE_ID[] { RECIPE_ID.EMAIL_PASSWORD });
            assert (count == 2);
        }

        {
            long count = AuthRecipe.getUsersCount(process.getProcess(), new RECIPE_ID[] { RECIPE_ID.THIRD_PARTY });
            assert (count == 1);
        }

        {
            long count = AuthRecipe.getUsersCount(process.getProcess(), new RECIPE_ID[] { RECIPE_ID.PASSWORDLESS });
            assert (count == 0);
        }

        {
            CreateCodeResponse createCode = Passwordless.createCode(process.getProcess(), "testX@example.com", null,
                    null, null);
            Passwordless.consumeCode(process.getProcess(), null, null, createCode.linkCode);
        }
        {
            CreateCodeResponse createCode = Passwordless.createCode(process.getProcess(), null, "+442071838750", null,
                    null);
            Passwordless.consumeCode(process.getProcess(), null, null, createCode.linkCode);
        }

        {
            long count = AuthRecipe.getUsersCount(process.getProcess(), new RECIPE_ID[] {});
            assert (count == 5);
        }

        {
            long count = AuthRecipe.getUsersCount(process.getProcess(),
                    new RECIPE_ID[] { RECIPE_ID.EMAIL_PASSWORD, RECIPE_ID.PASSWORDLESS });
            assert (count == 4);
        }

        {
            long count = AuthRecipe.getUsersCount(process.getProcess(), new RECIPE_ID[] { RECIPE_ID.EMAIL_PASSWORD });
            assert (count == 2);
        }

        {
            long count = AuthRecipe.getUsersCount(process.getProcess(), new RECIPE_ID[] { RECIPE_ID.THIRD_PARTY });
            assert (count == 1);
        }

        {
            long count = AuthRecipe.getUsersCount(process.getProcess(), new RECIPE_ID[] { RECIPE_ID.PASSWORDLESS });
            assert (count == 2);
        }
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void paginationTest() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        {
            UserPaginationContainer users = AuthRecipe.getUsers(process.getProcess(), 100, "ASC", null, null);
            assert (users.nextPaginationToken == null);
            assert (users.users.length == 0);
        }

        {
            UserPaginationContainer users = AuthRecipe.getUsers(process.getProcess(), 100, "ASC", null,
                    new RECIPE_ID[] { RECIPE_ID.EMAIL_PASSWORD, RECIPE_ID.THIRD_PARTY });
            assert (users.nextPaginationToken == null);
            assert (users.users.length == 0);
        }

        UserInfo user1 = EmailPassword.signUp(process.getProcess(), "test0@example.com", "password0");
        UserInfo user2 = EmailPassword.signUp(process.getProcess(), "test1@example.com", "password1");
        UserInfo user3 = EmailPassword.signUp(process.getProcess(), "test20@example.com", "password2");
        UserInfo user4 = EmailPassword.signUp(process.getProcess(), "test3@example.com", "password3");

        {
            UserPaginationContainer users = AuthRecipe.getUsers(process.getProcess(), 100, "ASC", null,
                    new RECIPE_ID[] { RECIPE_ID.EMAIL_PASSWORD });
            assert (users.nextPaginationToken == null);
            assert (users.users.length == 4);
            assert (users.users[0].recipeId.equals("emailpassword"));
            assert (users.users[0].user.equals(user1));
            assert (users.users[1].recipeId.equals("emailpassword"));
            assert (users.users[1].user.equals(user2));
            assert (users.users[2].recipeId.equals("emailpassword"));
            assert (users.users[2].user.equals(user3));
            assert (users.users[3].recipeId.equals("emailpassword"));
            assert (users.users[3].user.equals(user4));

        }

        {
            UserPaginationContainer users = AuthRecipe.getUsers(process.getProcess(), 100, "ASC", null,
                    new RECIPE_ID[] { RECIPE_ID.EMAIL_PASSWORD, RECIPE_ID.THIRD_PARTY });
            assert (users.nextPaginationToken == null);
            assert (users.users.length == 4);
            assert (users.users[0].recipeId.equals("emailpassword"));
            assert (users.users[0].user.equals(user1));
            assert (users.users[1].recipeId.equals("emailpassword"));
            assert (users.users[1].user.equals(user2));
            assert (users.users[2].recipeId.equals("emailpassword"));
            assert (users.users[2].user.equals(user3));
            assert (users.users[3].recipeId.equals("emailpassword"));
            assert (users.users[3].user.equals(user4));
        }

        {
            UserPaginationContainer users = AuthRecipe.getUsers(process.getProcess(), 100, "DESC", null,
                    new RECIPE_ID[] { RECIPE_ID.EMAIL_PASSWORD });
            assert (users.nextPaginationToken == null);
            assert (users.users.length == 4);
            assert (users.users[3].recipeId.equals("emailpassword"));
            assert (users.users[3].user.equals(user1));
            assert (users.users[2].recipeId.equals("emailpassword"));
            assert (users.users[2].user.equals(user2));
            assert (users.users[1].recipeId.equals("emailpassword"));
            assert (users.users[1].user.equals(user3));
            assert (users.users[0].recipeId.equals("emailpassword"));
            assert (users.users[0].user.equals(user4));

        }

        {
            UserPaginationContainer users = AuthRecipe.getUsers(process.getProcess(), 100, "DESC", null,
                    new RECIPE_ID[] { RECIPE_ID.THIRD_PARTY, RECIPE_ID.EMAIL_PASSWORD });
            assert (users.nextPaginationToken == null);
            assert (users.users.length == 4);
            assert (users.users[3].recipeId.equals("emailpassword"));
            assert (users.users[3].user.equals(user1));
            assert (users.users[2].recipeId.equals("emailpassword"));
            assert (users.users[2].user.equals(user2));
            assert (users.users[1].recipeId.equals("emailpassword"));
            assert (users.users[1].user.equals(user3));
            assert (users.users[0].recipeId.equals("emailpassword"));
            assert (users.users[0].user.equals(user4));
        }

        {
            UserPaginationContainer users = AuthRecipe.getUsers(process.getProcess(), 100, "DESC", null,
                    new RECIPE_ID[] { RECIPE_ID.THIRD_PARTY, RECIPE_ID.SESSION });
            assert (users.nextPaginationToken == null);
            assert (users.users.length == 0);
        }

        {
            UserPaginationContainer users = AuthRecipe.getUsers(process.getProcess(), 2, "DESC", null,
                    new RECIPE_ID[] { RECIPE_ID.THIRD_PARTY, RECIPE_ID.EMAIL_PASSWORD });
            assert (users.nextPaginationToken != null);
            assert (users.users.length == 2);
            assert (users.users[1].recipeId.equals("emailpassword"));
            assert (users.users[1].user.equals(user3));
            assert (users.users[0].recipeId.equals("emailpassword"));
            assert (users.users[0].user.equals(user4));
        }

        {
            UserPaginationContainer users = AuthRecipe.getUsers(process.getProcess(), 3, "ASC", null,
                    new RECIPE_ID[] { RECIPE_ID.THIRD_PARTY, RECIPE_ID.EMAIL_PASSWORD });
            assert (users.nextPaginationToken != null);
            assert (users.users.length == 3);
            assert (users.users[0].recipeId.equals("emailpassword"));
            assert (users.users[0].user.equals(user1));
            assert (users.users[1].recipeId.equals("emailpassword"));
            assert (users.users[1].user.equals(user2));
            assert (users.users[2].recipeId.equals("emailpassword"));
            assert (users.users[2].user.equals(user3));
        }

        ///////////////////////////////////////////////////////////////////////////////////

        String thirdPartyId = "testThirdParty";
        String thirdPartyUserId_1 = "thirdPartyUserIdA";
        String email_1 = "testA@example.com";

        io.supertokens.pluginInterface.thirdparty.UserInfo user5 = ThirdParty.signInUp(process.getProcess(),
                thirdPartyId, thirdPartyUserId_1, email_1).user;

        {
            UserPaginationContainer users = AuthRecipe.getUsers(process.getProcess(), 100, "ASC", null,
                    new RECIPE_ID[] { RECIPE_ID.EMAIL_PASSWORD });
            assert (users.nextPaginationToken == null);
            assert (users.users.length == 4);
            assert (users.users[0].recipeId.equals("emailpassword"));
            assert (users.users[0].user.equals(user1));
            assert (users.users[1].recipeId.equals("emailpassword"));
            assert (users.users[1].user.equals(user2));
            assert (users.users[2].recipeId.equals("emailpassword"));
            assert (users.users[2].user.equals(user3));
            assert (users.users[3].recipeId.equals("emailpassword"));
            assert (users.users[3].user.equals(user4));

        }

        {
            UserPaginationContainer users = AuthRecipe.getUsers(process.getProcess(), 100, "ASC", null,
                    new RECIPE_ID[] { RECIPE_ID.THIRD_PARTY });
            assert (users.nextPaginationToken == null);
            assert (users.users.length == 1);
            assert (users.users[0].recipeId.equals("thirdparty"));
            assert (users.users[0].user.equals(user5));

        }

        {
            UserPaginationContainer users = AuthRecipe.getUsers(process.getProcess(), 100, "ASC", null,
                    new RECIPE_ID[] { RECIPE_ID.EMAIL_PASSWORD, RECIPE_ID.THIRD_PARTY });
            assert (users.nextPaginationToken == null);
            assert (users.users.length == 5);
            assert (users.users[0].recipeId.equals("emailpassword"));
            assert (users.users[0].user.equals(user1));
            assert (users.users[1].recipeId.equals("emailpassword"));
            assert (users.users[1].user.equals(user2));
            assert (users.users[2].recipeId.equals("emailpassword"));
            assert (users.users[2].user.equals(user3));
            assert (users.users[3].recipeId.equals("emailpassword"));
            assert (users.users[3].user.equals(user4));
            assert (users.users[4].recipeId.equals("thirdparty"));
            assert (users.users[4].user.equals(user5));
        }

        {
            UserPaginationContainer users = AuthRecipe.getUsers(process.getProcess(), 100, "DESC", null,
                    new RECIPE_ID[] { RECIPE_ID.EMAIL_PASSWORD });
            assert (users.nextPaginationToken == null);
            assert (users.users.length == 4);
            assert (users.users[3].recipeId.equals("emailpassword"));
            assert (users.users[3].user.equals(user1));
            assert (users.users[2].recipeId.equals("emailpassword"));
            assert (users.users[2].user.equals(user2));
            assert (users.users[1].recipeId.equals("emailpassword"));
            assert (users.users[1].user.equals(user3));
            assert (users.users[0].recipeId.equals("emailpassword"));
            assert (users.users[0].user.equals(user4));

        }

        {
            UserPaginationContainer users = AuthRecipe.getUsers(process.getProcess(), 100, "DESC", null,
                    new RECIPE_ID[] { RECIPE_ID.THIRD_PARTY });
            assert (users.nextPaginationToken == null);
            assert (users.users.length == 1);
            assert (users.users[0].recipeId.equals("thirdparty"));
            assert (users.users[0].user.equals(user5));

        }

        {
            UserPaginationContainer users = AuthRecipe.getUsers(process.getProcess(), 100, "DESC", null,
                    new RECIPE_ID[] {});
            assert (users.nextPaginationToken == null);
            assert (users.users.length == 5);
            assert (users.users[4].recipeId.equals("emailpassword"));
            assert (users.users[4].user.equals(user1));
            assert (users.users[3].recipeId.equals("emailpassword"));
            assert (users.users[3].user.equals(user2));
            assert (users.users[2].recipeId.equals("emailpassword"));
            assert (users.users[2].user.equals(user3));
            assert (users.users[1].recipeId.equals("emailpassword"));
            assert (users.users[1].user.equals(user4));
            assert (users.users[0].recipeId.equals("thirdparty"));
            assert (users.users[0].user.equals(user5));
        }

        {
            UserPaginationContainer users = AuthRecipe.getUsers(process.getProcess(), 100, "DESC", null,
                    new RECIPE_ID[] { RECIPE_ID.SESSION });
            assert (users.nextPaginationToken == null);
            assert (users.users.length == 0);
        }

        {
            UserPaginationContainer users = AuthRecipe.getUsers(process.getProcess(), 2, "DESC", null,
                    new RECIPE_ID[] { RECIPE_ID.THIRD_PARTY, RECIPE_ID.EMAIL_PASSWORD });
            assert (users.nextPaginationToken != null);
            assert (users.users.length == 2);
            assert (users.users[1].recipeId.equals("emailpassword"));
            assert (users.users[1].user.equals(user4));
            assert (users.users[0].recipeId.equals("thirdparty"));
            assert (users.users[0].user.equals(user5));
        }

        {
            UserPaginationContainer users = AuthRecipe.getUsers(process.getProcess(), 3, "ASC", null,
                    new RECIPE_ID[] { RECIPE_ID.THIRD_PARTY, RECIPE_ID.EMAIL_PASSWORD });
            assert (users.nextPaginationToken != null);
            assert (users.users.length == 3);
            assert (users.users[0].recipeId.equals("emailpassword"));
            assert (users.users[0].user.equals(user1));
            assert (users.users[1].recipeId.equals("emailpassword"));
            assert (users.users[1].user.equals(user2));
            assert (users.users[2].recipeId.equals("emailpassword"));
            assert (users.users[2].user.equals(user3));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void randomPaginationTest() throws Exception {
        int numberOfUsers = 500;
        int[] limits = new int[] { 10, 14, 20, 23, 50, 100, 110, 150, 200, 510 };
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        Map<String, Function<Object, ? extends AuthRecipeUserInfo>> signUpMap = getSignUpMap(process);

        List<String> classes = getUserInfoClassNameList();

        for (String className : classes) {
            if (!signUpMap.containsKey(className)) {
                fail();
            }
        }

        // we create multiple users in parallel...
        List<AuthRecipeUserInfo> usersCreated = new ArrayList<>();

        ExecutorService es = Executors.newCachedThreadPool();
        for (int i = 0; i < numberOfUsers; i++) {
            if (Math.random() > 0.5) {
                while (true) {
                    String currUserType = classes.get((int) (Math.random() * classes.size()));
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
                        String currUserType = classes.get((int) (Math.random() * classes.size()));
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

        for (int limit : limits) {

            // now we paginate in asc order
            {
                // we sort users in usersCreated based on timeJoined since we added them in parallel...
                usersCreated.sort((o1, o2) -> {
                    if (o1.timeJoined != o2.timeJoined) {
                        return (int) (o1.timeJoined - o2.timeJoined);
                    }
                    return o2.id.compareTo(o1.id);
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
                            paginationToken, null);

                    for (UserPaginationContainer.UsersContainer uc : users.users) {
                        AuthRecipeUserInfo expected = usersCreated.get(indexIntoUsers);
                        AuthRecipeUserInfo actualUser = uc.user;

                        assert (actualUser.equals(expected) && uc.recipeId.equals(expected.getRecipeId().toString()));
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
                    return o1.id.compareTo(o2.id);
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
                            paginationToken, null);

                    for (UserPaginationContainer.UsersContainer uc : users.users) {
                        AuthRecipeUserInfo expected = usersCreated.get(indexIntoUsers);
                        AuthRecipeUserInfo actualUser = uc.user;

                        assert (actualUser.equals(expected) && uc.recipeId.equals(expected.getRecipeId().toString()));
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

    private static List<String> getUserInfoClassNameList() {
        Reflections reflections = new Reflections("io.supertokens");
        Set<Class<? extends AuthRecipeUserInfo>> classes = reflections.getSubTypesOf(AuthRecipeUserInfo.class);

        return classes.stream().map(Class::getCanonicalName).collect(Collectors.toList());
    }

    private static Map<String, Function<Object, ? extends AuthRecipeUserInfo>> getSignUpMap(
            TestingProcessManager.TestingProcess process) {
        AtomicInteger count = new AtomicInteger();

        Map<String, Function<Object, ? extends AuthRecipeUserInfo>> signUpMap = new HashMap<>();
        signUpMap.put("io.supertokens.pluginInterface.emailpassword.UserInfo", o -> {
            try {
                return EmailPassword.signUp(process.getProcess(), "test" + count.getAndIncrement() + "@example.com",
                        "password0");
            } catch (Exception ignored) {
            }
            return null;
        });
        signUpMap.put("io.supertokens.pluginInterface.thirdparty.UserInfo", o -> {
            try {
                String thirdPartyId = "testThirdParty";
                String thirdPartyUserId = "thirdPartyUserId" + count.getAndIncrement();
                String email = "test" + count.getAndIncrement() + "@example.com";

                return ThirdParty.signInUp(process.getProcess(), thirdPartyId, thirdPartyUserId, email).user;
            } catch (Exception ignored) {
            }
            return null;
        });
        signUpMap.put("io.supertokens.pluginInterface.passwordless.UserInfo", o -> {
            try {
                String email = "test" + count.getAndIncrement() + "@example.com";
                CreateCodeResponse createCode = Passwordless.createCode(process.getProcess(), email, null, null, null);
                return Passwordless.consumeCode(process.getProcess(), null, null, createCode.linkCode).user;
            } catch (Exception ignored) {
            }
            return null;
        });

        // add more recipes...

        return signUpMap;
    }
}
