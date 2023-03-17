/*
 *    Copyright (c) 2022, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.useridmapping;

import io.supertokens.Main;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.emailpassword.exceptions.UnknownUserIdException;
import io.supertokens.pluginInterface.emailverification.EmailVerificationStorage;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.jwt.JWTRecipeStorage;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.session.SessionStorage;
import io.supertokens.pluginInterface.useridmapping.UserIdMappingStorage;
import io.supertokens.pluginInterface.useridmapping.exception.UnknownSuperTokensUserIdException;
import io.supertokens.pluginInterface.useridmapping.exception.UserIdMappingAlreadyExistsException;
import io.supertokens.pluginInterface.usermetadata.UserMetadataStorage;
import io.supertokens.pluginInterface.userroles.UserRolesStorage;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import org.jetbrains.annotations.TestOnly;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;

public class UserIdMapping {

    public static void createUserIdMapping(AppIdentifier appIdentifier,
                                           String superTokensUserId, String externalUserId,
                                           String externalUserIdInfo, boolean force)
            throws UnknownSuperTokensUserIdException,
            UserIdMappingAlreadyExistsException, StorageQueryException, ServletException, TenantOrAppNotFoundException {

        // if a userIdMapping is created with force, then we skip the following checks
        if (!force) {
            // check that none of the non-auth recipes are using the superTokensUserId
            assertThatUserIdIsNotBeingUsedInNonAuthRecipes(appIdentifier, superTokensUserId);

            // We do not allow for a UserIdMapping to be created when the externalUserId is a SuperTokens userId.
            // There could be a case where User_1 has a userId mapping and a new SuperTokens User, User_2 is created
            // whose userId is equal to the User_1's externalUserId.
            // Theoretically this could happen but the likelihood of generating a non-unique UUID is low enough that we
            // ignore it.

            {
                if (appIdentifier.getAuthRecipeStorage().doesUserIdExist(appIdentifier, externalUserId)) {
                    throw new ServletException(new WebserverAPI.BadRequestException(
                            "Cannot create a userId mapping where the externalId is also a SuperTokens userID"));
                }
            }
        }

        appIdentifier.getUserIdMappingStorage().createUserIdMapping(appIdentifier, superTokensUserId, externalUserId,
                externalUserIdInfo);
    }

    @TestOnly
    public static void createUserIdMapping(Main main,
                                           String superTokensUserId, String externalUserId,
                                           String externalUserIdInfo, boolean force)
            throws UnknownSuperTokensUserIdException,
            UserIdMappingAlreadyExistsException, StorageQueryException, ServletException {
        try {
            Storage storage = StorageLayer.getStorage(main);
            createUserIdMapping(new AppIdentifier(null, null, storage), superTokensUserId, externalUserId,
                    externalUserIdInfo, force);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    public static io.supertokens.pluginInterface.useridmapping.UserIdMapping getUserIdMapping(
            AppIdentifier appIdentifier, String userId,
            UserIdType userIdType)
            throws StorageQueryException, TenantOrAppNotFoundException {
        UserIdMappingStorage storage = appIdentifier.getUserIdMappingStorage();

        if (userIdType == UserIdType.SUPERTOKENS) {
            return storage.getUserIdMapping(appIdentifier, userId, true);
        }
        if (userIdType == UserIdType.EXTERNAL) {
            return storage.getUserIdMapping(appIdentifier, userId, false);
        }

        io.supertokens.pluginInterface.useridmapping.UserIdMapping[] userIdMappings = storage.getUserIdMapping(
                appIdentifier, userId);

        if (userIdMappings.length == 0) {
            return null;
        }

        if (userIdMappings.length == 1) {
            return userIdMappings[0];
        }

        if (userIdMappings.length == 2) {
            for (io.supertokens.pluginInterface.useridmapping.UserIdMapping userIdMapping : userIdMappings) {
                if (userIdMapping.superTokensUserId.equals(userId)) {
                    return userIdMapping;
                }
            }
        }

        throw new IllegalStateException("Retrieved more than 2 UserId Mapping entries for a single userId.");
    }

    @TestOnly
    public static io.supertokens.pluginInterface.useridmapping.UserIdMapping getUserIdMapping(
            Main main, String userId,
            UserIdType userIdType)
            throws StorageQueryException {
        try {
            AppIdentifierStorageAndUserIdMapping appIdentifierStorageAndUserIdMapping =
                    StorageLayer.getAppIdentifierStorageAndUserIdMappingForUser(
                            main, new AppIdentifier(null, null),
                            userId, userIdType
                    );
            return getUserIdMapping(appIdentifierStorageAndUserIdMapping.appIdentifier, userId, userIdType);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        } catch (UnknownUserIdException e) {
            return null;
        }
    }

    public static boolean deleteUserIdMapping(AppIdentifier appIdentifier, String userId,
                                              UserIdType userIdType, boolean force)
            throws StorageQueryException, ServletException, TenantOrAppNotFoundException {

        // referring to
        // https://docs.google.com/spreadsheets/d/17hYV32B0aDCeLnSxbZhfRN2Y9b0LC2xUF44vV88RNAA/edit?usp=sharing
        // we need to check if db is in A3 or A4.
        io.supertokens.pluginInterface.useridmapping.UserIdMapping mapping = getUserIdMapping(appIdentifier,
                userId, UserIdType.ANY);
        UserIdMappingStorage storage = appIdentifier.getUserIdMappingStorage();

        if (mapping != null) {
            if (appIdentifier.getAuthRecipeStorage().doesUserIdExist(appIdentifier, mapping.externalUserId)) {
                // this means that the db is in state A4
                return storage.deleteUserIdMapping(appIdentifier, mapping.superTokensUserId, true);
            }
        } else {
            return false;
        }

        // if a userIdMapping is deleted with force, then we skip the following checks
        if (!force) {
            String externalId = mapping.externalUserId;

            // check if externalId is used in any non-auth recipes
            assertThatUserIdIsNotBeingUsedInNonAuthRecipes(appIdentifier, externalId);
        }

        // db is in state A3
        if (userIdType == UserIdType.SUPERTOKENS) {
            return storage.deleteUserIdMapping(appIdentifier, userId, true);
        }
        if (userIdType == UserIdType.EXTERNAL) {
            return storage.deleteUserIdMapping(appIdentifier, userId, false);
        }

        if (appIdentifier.getAuthRecipeStorage().doesUserIdExist(appIdentifier, userId)) {
            return storage.deleteUserIdMapping(appIdentifier, userId, true);
        }

        return storage.deleteUserIdMapping(appIdentifier, userId, false);
    }

    @TestOnly
    public static boolean deleteUserIdMapping(Main main, String userId,
                                              UserIdType userIdType, boolean force)
            throws StorageQueryException, ServletException {
        try {
            AppIdentifierStorageAndUserIdMapping appIdentifierStorageAndUserIdMappingForUser =
                    StorageLayer.getAppIdentifierStorageAndUserIdMappingForUser(
                    main, new AppIdentifier(null, null), userId, userIdType);
            return deleteUserIdMapping(
                    appIdentifierStorageAndUserIdMappingForUser.appIdentifier, userId, userIdType, force);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        } catch (UnknownUserIdException e) {
            return false;
        }
    }

    public static boolean updateOrDeleteExternalUserIdInfo(AppIdentifier appIdentifier,
                                                           String userId, UserIdType userIdType,
                                                           @Nullable String externalUserIdInfo)
            throws StorageQueryException, TenantOrAppNotFoundException {
        io.supertokens.pluginInterface.useridmapping.UserIdMapping mapping = getUserIdMapping(appIdentifier,
                userId, userIdType);
        if (mapping == null) {
            return false;
        }

        UserIdMappingStorage storage = appIdentifier.getUserIdMappingStorage();

        if (userIdType == UserIdType.SUPERTOKENS) {
            return storage.updateOrDeleteExternalUserIdInfo(appIdentifier, userId, true,
                    externalUserIdInfo);
        }
        if (userIdType == UserIdType.EXTERNAL) {
            return storage.updateOrDeleteExternalUserIdInfo(appIdentifier, userId, false,
                    externalUserIdInfo);
        }

        // userIdType == UserIdType.ANY
        // if userId exists in authRecipeStorage, it means it is a UserIdType.SUPERTOKENS
        if (appIdentifier.getAuthRecipeStorage().doesUserIdExist(appIdentifier, userId)) {
            return storage.updateOrDeleteExternalUserIdInfo(appIdentifier, userId, true,
                    externalUserIdInfo);
        }

        // else treat it as UserIdType.EXTERNAL
        return storage.updateOrDeleteExternalUserIdInfo(appIdentifier, userId, false,
                externalUserIdInfo);
    }

    @TestOnly
    public static boolean updateOrDeleteExternalUserIdInfo(Main main,
                                                           String userId, UserIdType userIdType,
                                                           @Nullable String externalUserIdInfo)
            throws StorageQueryException {
        try {
            AppIdentifierStorageAndUserIdMapping appIdentifierStorageAndUserIdMappingForUser =
                    StorageLayer.getAppIdentifierStorageAndUserIdMappingForUser(
                            main, new AppIdentifier(null, null), userId, userIdType);
            return updateOrDeleteExternalUserIdInfo(appIdentifierStorageAndUserIdMappingForUser.appIdentifier,
                    userId, userIdType, externalUserIdInfo);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        } catch (UnknownUserIdException e) {
            return false;
        }
    }

    public static HashMap<String, String> getUserIdMappingForSuperTokensUserIds(AppIdentifier appIdentifier,
                                                                                ArrayList<String> userIds)
            throws StorageQueryException, TenantOrAppNotFoundException {
        // This is still tenant specific
        return appIdentifier.getUserIdMappingStorage().getUserIdMappingForSuperTokensIds(appIdentifier, userIds);
    }

    @TestOnly
    public static HashMap<String, String> getUserIdMappingForSuperTokensUserIds(Main main,
                                                                                ArrayList<String> userIds)
            throws StorageQueryException {
        try {
            Storage storage = StorageLayer.getStorage(
                    new TenantIdentifier(null, null, null), main);

            return getUserIdMappingForSuperTokensUserIds(
                    new AppIdentifier(null, null, storage), userIds);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void assertThatUserIdIsNotBeingUsedInNonAuthRecipes(AppIdentifier appIdentifier, String userId)
            throws StorageQueryException, ServletException {
        Storage storage = appIdentifier.getStorage();
        {
            if (storage.isUserIdBeingUsedInNonAuthRecipe(appIdentifier,
                    SessionStorage.class.getName(),
                    userId)) {
                throw new ServletException(
                        new WebserverAPI.BadRequestException("UserId is already in use in Session recipe"));
            }
        }
        {
            if (storage.isUserIdBeingUsedInNonAuthRecipe(appIdentifier,
                    UserMetadataStorage.class.getName(),
                    userId)) {
                throw new ServletException(
                        new WebserverAPI.BadRequestException("UserId is already in use in UserMetadata recipe"));
            }
        }
        {
            if (storage.isUserIdBeingUsedInNonAuthRecipe(appIdentifier,
                    UserRolesStorage.class.getName(),
                    userId)) {
                throw new ServletException(
                        new WebserverAPI.BadRequestException("UserId is already in use in UserRoles recipe"));
            }
        }
        {
            if (storage.isUserIdBeingUsedInNonAuthRecipe(appIdentifier,
                    EmailVerificationStorage.class.getName(),
                    userId)) {
                throw new ServletException(
                        new WebserverAPI.BadRequestException("UserId is already in use in EmailVerification recipe"));
            }
        }
        {
            if (storage.isUserIdBeingUsedInNonAuthRecipe(appIdentifier,
                    JWTRecipeStorage.class.getName(),
                    userId)) {
                throw new ServletException(new WebserverAPI.BadRequestException("Should never come here"));
            }
        }
    }
}
