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
import io.supertokens.pluginInterface.authRecipe.AuthRecipeStorage;
import io.supertokens.pluginInterface.emailverification.EmailVerificationStorage;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.session.SessionStorage;
import io.supertokens.pluginInterface.useridmapping.UserIdMappingStorage;
import io.supertokens.pluginInterface.useridmapping.exception.UnknownSuperTokensUserIdException;
import io.supertokens.pluginInterface.useridmapping.exception.UserIdMappingAlreadyExistsException;
import io.supertokens.pluginInterface.usermetadata.UserMetadataStorage;
import io.supertokens.pluginInterface.userroles.UserRolesStorage;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.userroles.UserRoles;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;

public class UserIdMapping {

    public static void createUserIdMapping(Main main, String superTokensUserId, String externalUserId,
            String externalUserIdInfo)
            throws UnknownSuperTokensUserIdException, UserIdMappingAlreadyExistsException, StorageQueryException {
        createUserIdMapping(main, superTokensUserId, externalUserId, externalUserIdInfo, false);
    }

    public static void createUserIdMapping(Main main, String superTokensUserId, String externalUserId,
            String externalUserIdInfo, boolean force)
            throws UnknownSuperTokensUserIdException, UserIdMappingAlreadyExistsException, StorageQueryException {
        // if a userIdMapping is created with force, then we skip the checks to see if the superTokensUserId is being
        // used in non auth recipes.
        if (!force) {
            // check that none of the non-auth recipes are using the superTokensUserId
            {
                SessionStorage storage = StorageLayer.getSessionStorage(main);
                if (storage.isUserIdBeingUsedInNonAuthRecipe(SessionStorage.class.getName(), superTokensUserId)) {
                    throw new IllegalStateException("SuperTokens Id is already in use in Session recipe");
                }
            }

            {
                UserMetadataStorage storage = StorageLayer.getUserMetadataStorage(main);
                if (storage.isUserIdBeingUsedInNonAuthRecipe(UserMetadataStorage.class.getName(), superTokensUserId)) {
                    throw new IllegalStateException("SuperTokens Id is already in use in UserMetadata recipe");
                }
            }

            {
                UserRolesStorage storage = StorageLayer.getUserRolesStorage(main);
                if (storage.isUserIdBeingUsedInNonAuthRecipe(UserRolesStorage.class.getName(), superTokensUserId)) {
                    throw new IllegalStateException("SuperTokens Id is already in use in UserRoles recipe");
                }
            }
            {
                EmailVerificationStorage storage = StorageLayer.getEmailVerificationStorage(main);
                if (storage.isUserIdBeingUsedInNonAuthRecipe(EmailVerificationStorage.class.getName(),
                        superTokensUserId)) {
                    throw new IllegalStateException("SuperTokens Id is already in use in EmailVerification recipe");
                }
            }

            // UserId Mapping cases we do not allow,
            {
                // Case 1:
                // User_1: superTokensUserId_1 <-> externalUserId
                // User_2: superTokensUserId_2 <-> superTokensUserId_1

                // Case 2:
                // User_1: superTokensUserId_1 <-> superTokensUserId_2
                // User_2: superTokensUserId_2 <-> superTokensUserId_1

                // check if a mapping exists with superTokensUserId
                {
                    // check if the externalId is the superTokensUserId for another user who already has a mapping
                    io.supertokens.pluginInterface.useridmapping.UserIdMapping response = UserIdMapping
                            .getUserIdMapping(main, externalUserId, UserIdType.SUPERTOKENS);

                    if (response != null && response.externalUserId.equals(superTokensUserId)) {
                        throw new IllegalStateException("Invalid Mapping State");
                    }
                }
            }
        }

        StorageLayer.getUserIdMappingStorage(main).createUserIdMapping(superTokensUserId, externalUserId,
                externalUserIdInfo);
    }

    public static io.supertokens.pluginInterface.useridmapping.UserIdMapping getUserIdMapping(Main main, String userId,
            UserIdType userIdType) throws StorageQueryException {
        UserIdMappingStorage storage = StorageLayer.getUserIdMappingStorage(main);

        if (userIdType == UserIdType.SUPERTOKENS) {
            return storage.getUserIdMapping(userId, true);
        }
        if (userIdType == UserIdType.EXTERNAL) {
            return storage.getUserIdMapping(userId, false);
        }

        io.supertokens.pluginInterface.useridmapping.UserIdMapping[] userIdMappings = storage.getUserIdMapping(userId);

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

    public static boolean deleteUserIdMapping(Main main, String userId, UserIdType userIdType)
            throws StorageQueryException {
        return deleteUserIdMapping(main, userId, userIdType, false);
    }

    public static boolean deleteUserIdMapping(Main main, String userId, UserIdType userIdType, boolean force)
            throws StorageQueryException {
        // if a userIdMapping is deleted with force, then we skip the checks to see if the externalUserId is being
        // used in non auth recipes.
        if (!force) {
            String externalId;
            if (userIdType == UserIdType.EXTERNAL) {
                externalId = userId;
            } else {
                io.supertokens.pluginInterface.useridmapping.UserIdMapping userIdMapping = getUserIdMapping(main,
                        userId, UserIdType.ANY);
                if (userIdMapping != null) {
                    externalId = userIdMapping.externalUserId;
                } else {
                    return false;
                }
            }
            // check if externalId is used in any non-auth recipes
            {
                SessionStorage storage = StorageLayer.getSessionStorage(main);
                if (storage.isUserIdBeingUsedInNonAuthRecipe(SessionStorage.class.getName(), externalId)) {
                    throw new IllegalStateException("External Id is already in use in Session recipe");
                }
            }

            {
                UserMetadataStorage storage = StorageLayer.getUserMetadataStorage(main);
                if (storage.isUserIdBeingUsedInNonAuthRecipe(UserMetadataStorage.class.getName(), externalId)) {
                    throw new IllegalStateException("External Id is already in use in UserMetadata recipe");
                }
            }

            {
                UserRolesStorage storage = StorageLayer.getUserRolesStorage(main);
                if (storage.isUserIdBeingUsedInNonAuthRecipe(UserRolesStorage.class.getName(), externalId)) {
                    throw new IllegalStateException("External Id is already in use in UserRoles recipe");
                }
            }
            {
                EmailVerificationStorage storage = StorageLayer.getEmailVerificationStorage(main);
                if (storage.isUserIdBeingUsedInNonAuthRecipe(EmailVerificationStorage.class.getName(), externalId)) {
                    throw new IllegalStateException("External Id is already in use in EmailVerification recipe");
                }
            }
        }
        UserIdMappingStorage storage = StorageLayer.getUserIdMappingStorage(main);

        if (userIdType == UserIdType.SUPERTOKENS) {
            return storage.deleteUserIdMapping(userId, true);
        }
        if (userIdType == UserIdType.EXTERNAL) {
            return storage.deleteUserIdMapping(userId, false);
        }

        AuthRecipeStorage authRecipeStorage = StorageLayer.getAuthRecipeStorage(main);
        if (authRecipeStorage.doesUserIdExist(userId)) {
            return storage.deleteUserIdMapping(userId, true);
        }

        return storage.deleteUserIdMapping(userId, false);
    }

    public static boolean updateOrDeleteExternalUserIdInfo(Main main, String userId, UserIdType userIdType,
            @Nullable String externalUserIdInfo) throws StorageQueryException {
        UserIdMappingStorage storage = StorageLayer.getUserIdMappingStorage(main);

        if (userIdType == UserIdType.SUPERTOKENS) {
            return storage.updateOrDeleteExternalUserIdInfo(userId, true, externalUserIdInfo);
        }
        if (userIdType == UserIdType.EXTERNAL) {
            return storage.updateOrDeleteExternalUserIdInfo(userId, false, externalUserIdInfo);
        }

        AuthRecipeStorage authRecipeStorage = StorageLayer.getAuthRecipeStorage(main);
        if (authRecipeStorage.doesUserIdExist(userId)) {
            return storage.updateOrDeleteExternalUserIdInfo(userId, true, externalUserIdInfo);
        }

        return storage.updateOrDeleteExternalUserIdInfo(userId, false, externalUserIdInfo);
    }

    public static HashMap<String, String> getUserIdMappingForSuperTokensUserIds(Main main, ArrayList<String> userIds)
            throws StorageQueryException {
        return StorageLayer.getUserIdMappingStorage(main).getUserIdMappingForSuperTokensIds(userIds);
    }

}
