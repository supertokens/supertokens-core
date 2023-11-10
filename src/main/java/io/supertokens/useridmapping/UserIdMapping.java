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

import io.supertokens.AppIdentifierWithStorageAndUserIdMapping;
import io.supertokens.Main;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeStorage;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.authRecipe.LoginMethod;
import io.supertokens.pluginInterface.emailpassword.exceptions.UnknownUserIdException;
import io.supertokens.pluginInterface.emailverification.EmailVerificationStorage;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.jwt.JWTRecipeStorage;
import io.supertokens.pluginInterface.multitenancy.AppIdentifierWithStorage;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifierWithStorage;
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
    public static void createUserIdMapping(Main main, AppIdentifierWithStorage appIdentifierWithStorage,
                                           String superTokensUserId, String externalUserId,
                                           String externalUserIdInfo, boolean force)
            throws ServletException, UnknownSuperTokensUserIdException, UserIdMappingAlreadyExistsException,
            StorageQueryException, TenantOrAppNotFoundException {
        createUserIdMapping(main, appIdentifierWithStorage, superTokensUserId, externalUserId, externalUserIdInfo,
                force, false);
    }

    public static void createUserIdMapping(Main main, AppIdentifierWithStorage appIdentifierWithStorage,
                                           String superTokensUserId, String externalUserId,
                                           String externalUserIdInfo, boolean force, boolean makeExceptionForEmailVerification)
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
            AppIdentifierWithStorageAndUserIdMapping mappingAndStorage =
                    StorageLayer.getAppIdentifierWithStorageAndUserIdMappingForUserWithPriorityForTenantStorage(
                            main, appIdentifierWithStorage, appIdentifierWithStorage.getStorage(), externalUserId,
                            UserIdType.EXTERNAL);

            if (mappingAndStorage.userIdMapping != null) {
                throw new UserIdMappingAlreadyExistsException(
                        superTokensUserId.equals(mappingAndStorage.userIdMapping.superTokensUserId),
                        externalUserId.equals(mappingAndStorage.userIdMapping.externalUserId)
                );
            }
        } catch (UnknownUserIdException e) {
            // ignore this as we do not want external user id to exist
        }

        // if a userIdMapping is created with force, then we skip the following checks
        if (!force) {
            // We do not allow for a UserIdMapping to be created when the externalUserId is a SuperTokens userId.
            // There could be a case where User_1 has a userId mapping and a new SuperTokens User, User_2 is created
            // whose userId is equal to the User_1's externalUserId.
            // Theoretically this could happen but the likelihood of generating a non-unique UUID is low enough that we
            // ignore it.

            {
                if (((AuthRecipeStorage) appIdentifierWithStorage.getStorage()).doesUserIdExist(
                        appIdentifierWithStorage, externalUserId)) {
                    throw new ServletException(new WebserverAPI.BadRequestException(
                            "Cannot create a userId mapping where the externalId is also a SuperTokens userID"));
                }
            }

            if (makeExceptionForEmailVerification) {
                // check that none of the non-auth recipes are using the superTokensUserId
                List<String> storageClasses = findNonAuthStoragesWhereUserIdIsUsedOrAssertIfUsed(appIdentifierWithStorage, superTokensUserId, false);
                if (storageClasses.size() == 1 && storageClasses.get(0).equals(EmailVerificationStorage.class.getName())) {
                    // if the userId is used in email verification, then we do an exception and update the isEmailVerified
                    // to the externalUserId. We do this because we automatically set the isEmailVerified to true for passwordless
                    // and third party sign in up when the user info from provider says the email is verified and If we don't make
                    // an exception, then the creation of userIdMapping for the user will be blocked. And, to overcome that the
                    // email will have to be unverified first, then the userIdMapping should be created and then the email must be
                    // verified again on the externalUserId, which is not a good user experience.
                    appIdentifierWithStorage.getEmailVerificationStorage().updateIsEmailVerifiedToExternalUserId(appIdentifierWithStorage, superTokensUserId, externalUserId);
                } else  if (storageClasses.size() > 0) {
                    String recipeName = storageClasses.get(0);
                    String[] parts = recipeName.split("[.]");
                    recipeName = parts[parts.length - 1];
                    recipeName = recipeName.replace("Storage", "");
                    throw new ServletException(new WebserverAPI.BadRequestException(
                            "UserId is already in use in " + recipeName + " recipe"));
                }
            } else {
                findNonAuthStoragesWhereUserIdIsUsedOrAssertIfUsed(appIdentifierWithStorage, superTokensUserId, true);
            }


        }

        appIdentifierWithStorage.getUserIdMappingStorage()
                .createUserIdMapping(appIdentifierWithStorage, superTokensUserId,
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
                                           String externalUserIdInfo, boolean force, boolean makeExceptionForEmailVerification)
            throws UnknownSuperTokensUserIdException,
            UserIdMappingAlreadyExistsException, StorageQueryException, ServletException, UnknownUserIdException {
        try {
            Storage storage = StorageLayer.getStorage(main);
            createUserIdMapping(main, new AppIdentifierWithStorage(null, null, storage), superTokensUserId,
                    externalUserId,
                    externalUserIdInfo, force, makeExceptionForEmailVerification);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    public static io.supertokens.pluginInterface.useridmapping.UserIdMapping getUserIdMapping(
            AppIdentifierWithStorage appIdentifierWithStorage, String userId,
            UserIdType userIdType)
            throws StorageQueryException {
        UserIdMappingSQLStorage storage = (UserIdMappingSQLStorage) appIdentifierWithStorage.getUserIdMappingStorage();

        try {
            return storage.startTransaction(con -> {
                return getUserIdMapping(con, appIdentifierWithStorage, userId, userIdType);
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
            AppIdentifierWithStorage appIdentifierWithStorage, String userId,
            UserIdType userIdType)
            throws StorageQueryException {
        UserIdMappingSQLStorage storage = (UserIdMappingSQLStorage) appIdentifierWithStorage.getUserIdMappingStorage();

        if (userIdType == UserIdType.SUPERTOKENS) {
            return storage.getUserIdMapping_Transaction(con, appIdentifierWithStorage, userId, true);
        }

        if (userIdType == UserIdType.EXTERNAL) {
            return storage.getUserIdMapping_Transaction(con, appIdentifierWithStorage, userId, false);
        }

        io.supertokens.pluginInterface.useridmapping.UserIdMapping[] userIdMappings = storage.getUserIdMapping_Transaction(
                con, appIdentifierWithStorage, userId);

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
        return getUserIdMapping(new AppIdentifierWithStorage(null, null, storage), userId, userIdType);
    }

    public static boolean deleteUserIdMapping(AppIdentifierWithStorage appIdentifierWithStorage, String userId,
                                              UserIdType userIdType, boolean force)
            throws StorageQueryException, ServletException {

        // referring to
        // https://docs.google.com/spreadsheets/d/17hYV32B0aDCeLnSxbZhfRN2Y9b0LC2xUF44vV88RNAA/edit?usp=sharing
        // we need to check if db is in A3 or A4.
        io.supertokens.pluginInterface.useridmapping.UserIdMapping mapping = getUserIdMapping(appIdentifierWithStorage,
                userId, UserIdType.ANY);
        UserIdMappingStorage storage = appIdentifierWithStorage.getUserIdMappingStorage();

        if (mapping != null) {
            if (((AuthRecipeStorage) appIdentifierWithStorage.getStorage()).doesUserIdExist(
                    appIdentifierWithStorage, mapping.externalUserId)) {
                // this means that the db is in state A4
                return storage.deleteUserIdMapping(appIdentifierWithStorage, mapping.superTokensUserId, true);
            }
        } else {
            return false;
        }

        // if a userIdMapping is deleted with force, then we skip the following checks
        if (!force) {
            String externalId = mapping.externalUserId;

            // check if externalId is used in any non-auth recipes
            findNonAuthStoragesWhereUserIdIsUsedOrAssertIfUsed(appIdentifierWithStorage, externalId, true);
        }

        // db is in state A3
        if (userIdType == UserIdType.SUPERTOKENS) {
            return storage.deleteUserIdMapping(appIdentifierWithStorage, userId, true);
        }
        if (userIdType == UserIdType.EXTERNAL) {
            return storage.deleteUserIdMapping(appIdentifierWithStorage, userId, false);
        }

        if (((AuthRecipeStorage) appIdentifierWithStorage.getStorage()).doesUserIdExist(appIdentifierWithStorage,
                userId)) {
            return storage.deleteUserIdMapping(appIdentifierWithStorage, userId, true);
        }

        return storage.deleteUserIdMapping(appIdentifierWithStorage, userId, false);
    }

    @TestOnly
    public static boolean deleteUserIdMapping(Main main, String userId,
                                              UserIdType userIdType, boolean force)
            throws StorageQueryException, ServletException {
        Storage storage = StorageLayer.getStorage(main);
        return deleteUserIdMapping(
                new AppIdentifierWithStorage(null, null, storage), userId, userIdType, force);
    }

    public static boolean updateOrDeleteExternalUserIdInfo(AppIdentifierWithStorage appIdentifierWithStorage,
                                                           String userId, UserIdType userIdType,
                                                           @Nullable String externalUserIdInfo)
            throws StorageQueryException {
        UserIdMappingStorage storage = appIdentifierWithStorage.getUserIdMappingStorage();

        if (userIdType == UserIdType.SUPERTOKENS) {
            return storage.updateOrDeleteExternalUserIdInfo(appIdentifierWithStorage, userId, true,
                    externalUserIdInfo);
        }
        if (userIdType == UserIdType.EXTERNAL) {
            return storage.updateOrDeleteExternalUserIdInfo(appIdentifierWithStorage, userId, false,
                    externalUserIdInfo);
        }

        // userIdType == UserIdType.ANY
        // if userId exists in authRecipeStorage, it means it is a UserIdType.SUPERTOKENS
        if (((AuthRecipeStorage) appIdentifierWithStorage.getStorage()).doesUserIdExist(appIdentifierWithStorage,
                userId)) {
            return storage.updateOrDeleteExternalUserIdInfo(appIdentifierWithStorage, userId, true,
                    externalUserIdInfo);
        }

        // else treat it as UserIdType.EXTERNAL
        return storage.updateOrDeleteExternalUserIdInfo(appIdentifierWithStorage, userId, false,
                externalUserIdInfo);
    }

    @TestOnly
    public static boolean updateOrDeleteExternalUserIdInfo(Main main,
                                                           String userId, UserIdType userIdType,
                                                           @Nullable String externalUserIdInfo)
            throws StorageQueryException {
        Storage storage = StorageLayer.getStorage(main);
        return updateOrDeleteExternalUserIdInfo(new AppIdentifierWithStorage(
                        null, null, storage),
                userId, userIdType, externalUserIdInfo);
    }

    public static HashMap<String, String> getUserIdMappingForSuperTokensUserIds(
            TenantIdentifierWithStorage tenantIdentifierWithStorage,
            ArrayList<String> userIds)
            throws StorageQueryException {
        // userIds are already filtered for a tenant, so this becomes a tenant specific operation.
        return tenantIdentifierWithStorage.getUserIdMappingStorage().getUserIdMappingForSuperTokensIds(userIds);
    }

    public static HashMap<String, String> getUserIdMappingForSuperTokensUserIds(
            AppIdentifierWithStorage appIdentifierWithStorage,
            ArrayList<String> userIds)
            throws StorageQueryException {
        // userIds are already filtered for a tenant, so this becomes a tenant specific operation.
        return appIdentifierWithStorage.getUserIdMappingStorage().getUserIdMappingForSuperTokensIds(userIds);
    }

    @TestOnly
    public static HashMap<String, String> getUserIdMappingForSuperTokensUserIds(Main main,
                                                                                ArrayList<String> userIds)
            throws StorageQueryException {
        Storage storage = StorageLayer.getStorage(main);
        return getUserIdMappingForSuperTokensUserIds(
                new TenantIdentifierWithStorage(null, null, null, storage), userIds);
    }

    public static List<String> findNonAuthStoragesWhereUserIdIsUsedOrAssertIfUsed(
            AppIdentifierWithStorage appIdentifierWithStorage, String userId, boolean assertIfUsed)
            throws StorageQueryException, ServletException {
        Storage storage = appIdentifierWithStorage.getStorage();
        List<String> result = new ArrayList<>();

        {
            if (storage.isUserIdBeingUsedInNonAuthRecipe(appIdentifierWithStorage,
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
            if (storage.isUserIdBeingUsedInNonAuthRecipe(appIdentifierWithStorage,
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
            if (storage.isUserIdBeingUsedInNonAuthRecipe(appIdentifierWithStorage,
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
            if (storage.isUserIdBeingUsedInNonAuthRecipe(appIdentifierWithStorage,
                    EmailVerificationStorage.class.getName(),
                    userId)) {
                result.add(EmailVerificationStorage.class.getName());
                if (assertIfUsed) {
                    throw new ServletException(
                            new WebserverAPI.BadRequestException("UserId is already in use in EmailVerification recipe"));
                }
            }
        }
        {
            if (storage.isUserIdBeingUsedInNonAuthRecipe(appIdentifierWithStorage,
                    JWTRecipeStorage.class.getName(),
                    userId)) {
                throw new ServletException(new WebserverAPI.BadRequestException("Should never come here"));
            }
        }
        {
            if (storage.isUserIdBeingUsedInNonAuthRecipe(appIdentifierWithStorage, TOTPStorage.class.getName(),
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

    public static void populateExternalUserIdForUsers(AppIdentifierWithStorage appIdentifierWithStorage, AuthRecipeUserInfo[] users)
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
        HashMap<String, String> userIdMappings = getUserIdMappingForSuperTokensUserIds(appIdentifierWithStorage,
                userIdsList);

        for (AuthRecipeUserInfo user : users) {
            user.setExternalUserId(userIdMappings.get(user.getSupertokensUserId()));

            for (LoginMethod lm : user.loginMethods) {
                lm.setExternalUserId(userIdMappings.get(lm.getSupertokensUserId()));
            }
        }
    }

    public static void populateExternalUserIdForUsers(TenantIdentifierWithStorage tenantIdentifierWithStorage, AuthRecipeUserInfo[] users)
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
        HashMap<String, String> userIdMappings = getUserIdMappingForSuperTokensUserIds(tenantIdentifierWithStorage,
                userIdsList);

        for (AuthRecipeUserInfo user : users) {
            user.setExternalUserId(userIdMappings.get(user.getSupertokensUserId()));

            for (LoginMethod lm : user.loginMethods) {
                lm.setExternalUserId(userIdMappings.get(lm.getSupertokensUserId()));
            }
        }
    }
}
