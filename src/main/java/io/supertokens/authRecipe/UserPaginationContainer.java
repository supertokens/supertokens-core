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

package io.supertokens.authRecipe;

import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class UserPaginationContainer {
    public final AuthRecipeUserInfo[] users;
    public final String nextPaginationToken;

    public UserPaginationContainer(@Nonnull AuthRecipeUserInfo[] users, @Nullable String nextPaginationToken) {
        this.users = users;
        this.nextPaginationToken = nextPaginationToken;
    }
}
