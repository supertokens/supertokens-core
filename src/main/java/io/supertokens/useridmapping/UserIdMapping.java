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
import io.supertokens.StorageAndUserIdMapping;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.StorageUtils;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.authRecipe.LoginMethod;
import io.supertokens.pluginInterface.emailpassword.exceptions.UnknownUserIdException;
import io.supertokens.pluginInterface.emailverification.EmailVerificationStorage;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.jwt.JWTRecipeStorage;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.session.SessionStorage;
import io.supertokens.pluginInterface.sqlStorage.TransactionConnection;
import io.supertokens.pluginInterface.totp.TOTPStorage;
import io.supertokens.pluginInterface.useridmapping.UserIdMappingStorage;
import io.supertokens.pluginInterface.useridmapping.exception.UnknownSuperTokensUserIdException;
import io.supertokens.pluginInterface.useridmapping.exception.UserIdMappingAlreadyExistsException;
import io.supertokens.pluginInterface.useridmapping.sqlStorage.UserIdMappingSQLStorage;
import io.supertokens.pluginInterface.usermetadata.UserMetadataStorage;
import io.supertokens.pluginInterface.userroles.UserRolesStorage;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import org.jetbrains.annotations.TestOnly;

import javax.annotation.Nullable;
import java.util.*;

public class UserIdMapping {

    @TestOnly
    public static void createUserIdMapping(AppIdentifier appIdentifier, Storage[] storages,
                                           String superTokensUserId, String externalUserId,
                                           String externalUserIdInfo, boolean force)
            throws ServletException, UnknownSuperTokensUserIdException, UserIdMappingAlreadyExistsException,
            StorageQueryException, TenantOrAppNotFoundException {
        createUserIdMapping(appIdentifier, storages, superTokensUserId, externalUserId, externalUserIdInfo,
                force, false);
    }

    @TestOnly
    public static void createUserIdMapping(Main main, AppIdentifier appIdentifier, Storage storage,
                                           String supertokensUserId, String externalUserId, String externalUserIdInfo,
                                           boolean force)
            throws ServletException, UnknownSuperTokensUserIdException, UserIdMappingAlreadyExistsException,
            StorageQueryException, TenantOrAppNotFoundException {
        createUserIdMapping(
                new AppIdentifier(appIdentifier.getConnectionUriDomain(), appIdentifier.getAppId()),
                new Storage[]{storage}, supertokensUserId, externalUserId, externalUserIdInfo, force
        );
    }

    public static void createUserIdMapping(AppIdentifier appIdentifier, Storage[] storages,
                                           String superTokensUserId, String externalUserId,
                                           String externalUserIdInfo, boolean force,
                                           boolean makeExceptionForEmailVerification)
            throws UnknownSuperTokensUserIdException,
            UserIdMappingAlreadyExistsException, StorageQueryException, ServletException,
            TenantOrAppNotFoundException {

        // We first need to check if the external user id exists across all app storages because we do not want
        // 2 users from different user pool but same app to point to same external user id.
        // We may still end up having that situation due to race conditions, as we are not taking any app level lock,
        // but we are okay with it as of now, by returning prioritized mapping based on which the tenant the request
        // came from.
        // This issue - https://github.com/supertokens/supertokens-core/issues/610 - must be resolved when the
        // race condition is fixed.
        try { // with external id
            StorageAndUserIdMapping mappingAndStorage =
                    StorageLayer.findStorageAndUserIdMappingForUser(
                            appIdentifier, storages, externalUserId, UserIdType.EXTERNAL);

            if (mappingAndStorage.userIdMapping != null) {
                throw new UserIdMappingAlreadyExistsException(
                        superTokensUserId.equals(mappingAndStorage.userIdMapping.superTokensUserId),
                        externalUserId.equals(mappingAndStorage.userIdMapping.externalUserId)
                );
            }
        } catch (UnknownUserIdException e) {
            // ignore this as we do not want external user id to exist
        }

        StorageAndUserIdMapping mappingAndStorage;
        try {
            mappingAndStorage = StorageLayer.findStorageAndUserIdMappingForUser(
                    appIdentifier, storages, superTokensUserId, UserIdType.SUPERTOKENS);
        } catch (UnknownUserIdException e) {
            throw new UnknownSuperTokensUserIdException();
        }

        Storage userStorage = mappingAndStorage.storage;

        // if a userIdMapping is created with force, then we skip the following checks
        if (!force) {
            // We do not allow for a UserIdMapping to be created when the externalUserId is a SuperTokens userId.
            // There could be a case where User_1 has a userId mapping and a new SuperTokens User, User_2 is created
            // whose userId is equal to the User_1's externalUserId.
            // Theoretically this could happen but the likelihood of generating a non-unique UUID is low enough that we
            // ignore it.

            {
                if (StorageUtils.getAuthRecipeStorage(userStorage).doesUserIdExist(
                        appIdentifier, externalUserId)) {
                    throw new ServletException(new WebserverAPI.BadRequestException(
                            "Cannot create a userId mapping where the externalId is also a SuperTokens userID"));
                }
            }

            if (makeExceptionForEmailVerification) {
                // check that none of the non-auth recipes are using the superTokensUserId
                List<String> storageClasses = findNonAuthStoragesWhereUserIdIsUsedOrAssertIfUsed(appIdentifier,
                        userStorage, superTokensUserId, false);
                if (storageClasses.size() == 1 &&
                        storageClasses.get(0).equals(EmailVerificationStorage.class.getName())) {
                    // if the userId is used in email verification, then we do an exception and update the
                    // isEmailVerified
                    // to the externalUserId. We do this because we automatically set the isEmailVerified to true for
                    // passwordless
                    // and third party sign in up when the user info from provider says the email is verified and If
                    // we don't make
                    // an exception, then the creation of userIdMapping for the user will be blocked. And, to
                    // overcome that the
                    // email will have to be unverified first, then the userIdMapping should be created and then the
                    // email must be
                    // verified again on the externalUserId, which is not a good user experience.
                    StorageUtils.getEmailVerificationStorage(userStorage).updateIsEmailVerifiedToExternalUserId(
                            appIdentifier, superTokensUserId, externalUserId);
                } else if (storageClasses.size() > 0) {
                    String recipeName = storageClasses.get(0);
                    String[] parts = recipeName.split("[.]");
                    recipeName = parts[parts.length - 1];
                    recipeName = recipeName.replace("Storage", "");
                    throw new ServletException(new WebserverAPI.BadRequestException(
                            "UserId is already in use in " + recipeName + " recipe"));
                }
            } else {
                findNonAuthStoragesWhereUserIdIsUsedOrAssertIfUsed(appIdentifier, userStorage, superTokensUserId, true);
            }
        }

        StorageUtils.getUserIdMappingStorage(userStorage)
                .createUserIdMapping(appIdentifier, superTokensUserId,
                        externalUserId, externalUserIdInfo);
    }

    @TestOnly
    public static void createUserIdMapping(Main main,
                                           String superTokensUserId, String externalUserId,
                                           String externalUserIdInfo, boolean force)
            throws ServletException, UnknownSuperTokensUserIdException, UserIdMappingAlreadyExistsException,
            StorageQueryException, UnknownUserIdException {
        createUserIdMapping(main, superTokensUserId, externalUserId, externalUserIdInfo, force, false);
    }

    @TestOnly
    public static void createUserIdMapping(Main main,
                                           String superTokensUserId, String externalUserId,
                                           String externalUserIdInfo, boolean force,
                                           boolean makeExceptionForEmailVerification)
            throws UnknownSuperTokensUserIdException,
            UserIdMappingAlreadyExistsException, StorageQueryException, ServletException, UnknownUserIdException {
        try {
            Storage storage = StorageLayer.getStorage(main);
            createUserIdMapping(new AppIdentifier(null, null), new Storage[]{storage}, superTokensUserId,
                    externalUserId, externalUserIdInfo, force, makeExceptionForEmailVerification);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    public static io.supertokens.pluginInterface.useridmapping.UserIdMapping getUserIdMapping(
            AppIdentifier appIdentifier, Storage storage, String userId,
            UserIdType userIdType)
            throws StorageQueryException {
        UserIdMappingSQLStorage uidMappingStorage =
                (UserIdMappingSQLStorage) storage;

        try {
            return uidMappingStorage.startTransaction(con -> {
                return getUserIdMapping(con, appIdentifier, uidMappingStorage, userId, userIdType);
            });
        } catch (StorageTransactionLogicException e) {
            if (e.actualException instanceof StorageQueryException) {
                throw (StorageQueryException) e.actualException;
            } else {
                throw new IllegalStateException(e.actualException);
            }
        }
    }

    public static io.supertokens.pluginInterface.useridmapping.UserIdMapping getUserIdMapping(
            TransactionConnection con,
            AppIdentifier appIdentifier, Storage storage, String userId,
            UserIdType userIdType)
            throws StorageQueryException {
        UserIdMappingSQLStorage uidMappingStorage =
                (UserIdMappingSQLStorage) storage;

        if (userIdType == UserIdType.SUPERTOKENS) {
            return uidMappingStorage.getUserIdMapping_Transaction(con, appIdentifier, userId, true);
        }

        if (userIdType == UserIdType.EXTERNAL) {
            return uidMappingStorage.getUserIdMapping_Transaction(con, appIdentifier, userId, false);
        }

        io.supertokens.pluginInterface.useridmapping.UserIdMapping[] userIdMappings =
                uidMappingStorage.getUserIdMapping_Transaction(
                        con, appIdentifier, userId);

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
        Storage storage = StorageLayer.getStorage(main);
        return getUserIdMapping(new AppIdentifier(null, null), storage, userId, userIdType);
    }

    public static boolean deleteUserIdMapping(AppIdentifier appIdentifier, Storage storage, String userId,
                                              UserIdType userIdType, boolean force)
            throws StorageQueryException, ServletException {

        // referring to
        // https://docs.google.com/spreadsheets/d/17hYV32B0aDCeLnSxbZhfRN2Y9b0LC2xUF44vV88RNAA/edit?usp=sharing
        // we need to check if db is in A3 or A4.
        io.supertokens.pluginInterface.useridmapping.UserIdMapping mapping = getUserIdMapping(appIdentifier,
                storage, userId, UserIdType.ANY);
        UserIdMappingStorage uidMappingStorage = StorageUtils.getUserIdMappingStorage(storage);

        if (mapping != null) {
            if (StorageUtils.getAuthRecipeStorage(storage).doesUserIdExist(
                    appIdentifier, mapping.externalUserId)) {
                // this means that the db is in state A4
                return uidMappingStorage.deleteUserIdMapping(appIdentifier, mapping.superTokensUserId, true);
            }
        } else {
            return false;
        }

        // if a userIdMapping is deleted with force, then we skip the following checks
        if (!force) {
            String externalId = mapping.externalUserId;

            // check if externalId is used in any non-auth recipes
            findNonAuthStoragesWhereUserIdIsUsedOrAssertIfUsed(appIdentifier, storage, externalId, true);
        }

        // db is in state A3
        if (userIdType == UserIdType.SUPERTOKENS) {
            return uidMappingStorage.deleteUserIdMapping(appIdentifier, userId, true);
        }
        if (userIdType == UserIdType.EXTERNAL) {
            return uidMappingStorage.deleteUserIdMapping(appIdentifier, userId, false);
        }

        if (StorageUtils.getAuthRecipeStorage(storage).doesUserIdExist(appIdentifier,
                userId)) {
            return uidMappingStorage.deleteUserIdMapping(appIdentifier, userId, true);
        }

        return uidMappingStorage.deleteUserIdMapping(appIdentifier, userId, false);
    }

    @TestOnly
    public static boolean deleteUserIdMapping(Main main, String userId,
                                              UserIdType userIdType, boolean force)
            throws StorageQueryException, ServletException {
        Storage storage = StorageLayer.getStorage(main);
        return deleteUserIdMapping(
                new AppIdentifier(null, null), storage, userId, userIdType, force);
    }

    public static boolean updateOrDeleteExternalUserIdInfo(AppIdentifier appIdentifier, Storage storage,
                                                           String userId, UserIdType userIdType,
                                                           @Nullable String externalUserIdInfo)
            throws StorageQueryException {
        UserIdMappingStorage uidMappingStorage = StorageUtils.getUserIdMappingStorage(storage);

        if (userIdType == UserIdType.SUPERTOKENS) {
            return uidMappingStorage.updateOrDeleteExternalUserIdInfo(appIdentifier, userId, true,
                    externalUserIdInfo);
        }
        if (userIdType == UserIdType.EXTERNAL) {
            return uidMappingStorage.updateOrDeleteExternalUserIdInfo(appIdentifier, userId, false,
                    externalUserIdInfo);
        }

        // userIdType == UserIdType.ANY
        // if userId exists in authRecipeStorage, it means it is a UserIdType.SUPERTOKENS
        if (StorageUtils.getAuthRecipeStorage(storage).doesUserIdExist(appIdentifier,
                userId)) {
            return uidMappingStorage.updateOrDeleteExternalUserIdInfo(appIdentifier, userId, true,
                    externalUserIdInfo);
        }

        // else treat it as UserIdType.EXTERNAL
        return uidMappingStorage.updateOrDeleteExternalUserIdInfo(appIdentifier, userId, false,
                externalUserIdInfo);
    }

    @TestOnly
    public static boolean updateOrDeleteExternalUserIdInfo(Main main,
                                                           String userId, UserIdType userIdType,
                                                           @Nullable String externalUserIdInfo)
            throws StorageQueryException {
        Storage storage = StorageLayer.getStorage(main);
        return updateOrDeleteExternalUserIdInfo(new AppIdentifier(null, null), storage,
                userId, userIdType, externalUserIdInfo);
    }

    public static HashMap<String, String> getUserIdMappingForSuperTokensUserIds(
            AppIdentifier appIdentifier,
            Storage storage,
            ArrayList<String> userIds)
            throws StorageQueryException {
        // userIds are already filtered for a tenant
        return StorageUtils.getUserIdMappingStorage(storage).getUserIdMappingForSuperTokensIds(appIdentifier, userIds);
    }

    @TestOnly
    public static HashMap<String, String> getUserIdMappingForSuperTokensUserIds(Main main,
                                                                                ArrayList<String> userIds)
            throws StorageQueryException {
        Storage storage = StorageLayer.getStorage(main);
        return getUserIdMappingForSuperTokensUserIds(
                new AppIdentifier(null, null), storage, userIds);
    }

    public static List<String> findNonAuthStoragesWhereUserIdIsUsedOrAssertIfUsed(
            AppIdentifier appIdentifier, Storage storage, String userId, boolean assertIfUsed)
            throws StorageQueryException, ServletException {
        List<String> result = new ArrayList<>();

        {
            if (storage.isUserIdBeingUsedInNonAuthRecipe(appIdentifier,
                    SessionStorage.class.getName(),
                    userId)) {
                result.add(SessionStorage.class.getName());
                if (assertIfUsed) {
                    throw new ServletException(
                            new WebserverAPI.BadRequestException("UserId is already in use in Session recipe"));
                }
            }
        }
        {
            if (storage.isUserIdBeingUsedInNonAuthRecipe(appIdentifier,
                    UserMetadataStorage.class.getName(),
                    userId)) {
                result.add(UserMetadataStorage.class.getName());
                if (assertIfUsed) {
                    throw new ServletException(
                            new WebserverAPI.BadRequestException("UserId is already in use in UserMetadata recipe"));
                }
            }
        }
        {
            if (storage.isUserIdBeingUsedInNonAuthRecipe(appIdentifier,
                    UserRolesStorage.class.getName(),
                    userId)) {
                result.add(UserRolesStorage.class.getName());
                if (assertIfUsed) {
                    throw new ServletException(
                            new WebserverAPI.BadRequestException("UserId is already in use in UserRoles recipe"));
                }
            }
        }
        {
            if (storage.isUserIdBeingUsedInNonAuthRecipe(appIdentifier,
                    EmailVerificationStorage.class.getName(),
                    userId)) {
                result.add(EmailVerificationStorage.class.getName());
                if (assertIfUsed) {
                    throw new ServletException(
                            new WebserverAPI.BadRequestException(
                                    "UserId is already in use in EmailVerification recipe"));
                }
            }
        }
        {
            if (storage.isUserIdBeingUsedInNonAuthRecipe(appIdentifier,
                    JWTRecipeStorage.class.getName(),
                    userId)) {
                throw new ServletException(new WebserverAPI.BadRequestException("Should never come here"));
            }
        }
        {
            if (storage.isUserIdBeingUsedInNonAuthRecipe(appIdentifier, TOTPStorage.class.getName(),
                    userId)) {
                result.add(TOTPStorage.class.getName());
                if (assertIfUsed) {
                    throw new ServletException(
                            new WebserverAPI.BadRequestException("UserId is already in use in TOTP recipe"));
                }
            }
        }
        return result;
    }

    public static void populateExternalUserIdForUsers(AppIdentifier appIdentifier, Storage storage,
                                                      AuthRecipeUserInfo[] users)
            throws StorageQueryException {
        Set<String> userIds = new HashSet<>();

        for (AuthRecipeUserInfo user : users) {
            userIds.add(user.getSupertokensUserId());

            for (LoginMethod lm : user.loginMethods) {
                userIds.add(lm.getSupertokensUserId());
            }
        }
        ArrayList<String> userIdsList = new ArrayList<>(userIds);
        userIdsList.addAll(userIds);
        HashMap<String, String> userIdMappings = getUserIdMappingForSuperTokensUserIds(appIdentifier, storage,
                userIdsList);

        for (AuthRecipeUserInfo user : users) {
            user.setExternalUserId(userIdMappings.get(user.getSupertokensUserId()));

            for (LoginMethod lm : user.loginMethods) {
                lm.setExternalUserId(userIdMappings.get(lm.getSupertokensUserId()));
            }
        }
    }
}
