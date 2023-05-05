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

package io.supertokens.ban;

import io.supertokens.Main;
import io.supertokens.pluginInterface.ban.BannedUserStorage;
import io.supertokens.pluginInterface.ban.exceptions.DuplicateUserIdException;
import io.supertokens.pluginInterface.ban.exceptions.UnknownUserIdException;
import io.supertokens.pluginInterface.ban.exceptions.UserNotBannedException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.storageLayer.StorageLayer;

import javax.annotation.Nonnull;

public class BannedUser {

    public static void insertBannedUser(Main main, @Nonnull String userId) throws StorageQueryException,
            UnknownUserIdException, DuplicateUserIdException {
        BannedUserStorage storage = StorageLayer.getBannedUserStorage(main);
        storage.createNewBannedUser(userId);
    }

    public static void deleteBannedUser(Main main, @Nonnull String userId)
            throws StorageQueryException, UnknownUserIdException, UserNotBannedException {
        if (!StorageLayer.getAuthRecipeStorage(main).doesUserIdExist(userId)) {
            throw new UnknownUserIdException();
        }
        BannedUserStorage storage = StorageLayer.getBannedUserStorage(main);
        storage.removeBannedUser(userId);
    }

    public static boolean isUserBanned(Main main, @Nonnull String userId) throws StorageQueryException,
            UnknownUserIdException {

        if (StorageLayer.getAuthRecipeStorage(main).doesUserIdExist(userId)) {
            throw new UnknownUserIdException();
        }

        BannedUserStorage storage = StorageLayer.getBannedUserStorage(main);
        return storage.isUserBanned(userId);
    }
}
