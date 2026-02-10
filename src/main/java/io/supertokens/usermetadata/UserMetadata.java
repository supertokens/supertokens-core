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

package io.supertokens.usermetadata;

import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.ResourceDistributor;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.StorageUtils;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.usermetadata.sqlStorage.UserMetadataSQLStorage;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.utils.MetadataUtils;
import org.jetbrains.annotations.TestOnly;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Map;

public class UserMetadata {

    @TestOnly
    public static JsonObject updateUserMetadata(Main main,
                                                @Nonnull String userId, @Nonnull JsonObject metadataUpdate)
            throws StorageQueryException, StorageTransactionLogicException {
        Storage storage = StorageLayer.getStorage(main);
        try {
            return updateUserMetadata(
                    ResourceDistributor.getAppForTesting().toAppIdentifier(), storage,
                    userId, metadataUpdate);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    public static JsonObject updateUserMetadata(AppIdentifier appIdentifier, Storage storage,
                                                @Nonnull String userId, @Nonnull JsonObject metadataUpdate)
            throws StorageQueryException, StorageTransactionLogicException, TenantOrAppNotFoundException {
        UserMetadataSQLStorage umdStorage = StorageUtils.getUserMetadataStorage(storage);

        try {
            return umdStorage.startTransaction((con) -> {
                JsonObject originalMetadata = umdStorage.getUserMetadata_Transaction(appIdentifier, con,
                        userId);

                JsonObject updatedMetadata = originalMetadata == null ? new JsonObject() : originalMetadata;
                MetadataUtils.shallowMergeMetadataUpdate(updatedMetadata, metadataUpdate);

                try {
                    umdStorage.setUserMetadata_Transaction(appIdentifier, con, userId, updatedMetadata);
                } catch (TenantOrAppNotFoundException e) {
                    throw new StorageTransactionLogicException(e);
                }

                return updatedMetadata;
            });
        } catch (StorageTransactionLogicException e) {
            if (e.actualException instanceof TenantOrAppNotFoundException) {
                throw (TenantOrAppNotFoundException) e.actualException;
            }
            throw e;
        }
    }

    public static void updateMultipleUsersMetadata(AppIdentifier appIdentifier, Storage storage,
                                                @Nonnull Map<String, JsonObject> metadataToUpdateByUserId)
            throws StorageQueryException, StorageTransactionLogicException, TenantOrAppNotFoundException {
        UserMetadataSQLStorage umdStorage = StorageUtils.getUserMetadataStorage(storage);

        try {
            umdStorage.startTransaction(con -> {
                Map<String, JsonObject> originalMetadatas = umdStorage.getMultipleUsersMetadatas_Transaction(appIdentifier, con,
                        new ArrayList<>(metadataToUpdateByUserId.keySet()));

                // updating only the already existing ones. The others don't need update
                for(Map.Entry<String, JsonObject> metadataByUserId : originalMetadatas.entrySet()){
                    JsonObject originalMetadata = metadataByUserId.getValue();
                    String userId = metadataByUserId.getKey();
                    JsonObject updatedMetadata = originalMetadata == null ? new JsonObject() : originalMetadata;
                    MetadataUtils.shallowMergeMetadataUpdate(updatedMetadata, metadataToUpdateByUserId.get(userId));
                    metadataToUpdateByUserId.put(userId, updatedMetadata);
                }

                try {
                    umdStorage.setMultipleUsersMetadatas_Transaction(appIdentifier, con, metadataToUpdateByUserId);
                    umdStorage.commitTransaction(con);
                } catch (TenantOrAppNotFoundException e) {
                    throw new StorageTransactionLogicException(e);
                }

                return null;
            });
        } catch (StorageTransactionLogicException e) {
            if (e.actualException instanceof TenantOrAppNotFoundException) {
                throw (TenantOrAppNotFoundException) e.actualException;
            }
            throw e;
        }
    }

    @TestOnly
    public static JsonObject getUserMetadata(Main main, @Nonnull String userId) throws StorageQueryException {
        Storage storage = StorageLayer.getStorage(main);
        return getUserMetadata(ResourceDistributor.getAppForTesting().toAppIdentifier(), storage, userId);
    }

    public static JsonObject getUserMetadata(AppIdentifier appIdentifier, Storage storage,
                                             @Nonnull String userId)
            throws StorageQueryException {
        UserMetadataSQLStorage umdStorage = StorageUtils.getUserMetadataStorage(storage);

        JsonObject metadata = umdStorage.getUserMetadata(appIdentifier, userId);

        if (metadata == null) {
            return new JsonObject();
        }

        return metadata;
    }

    /**
     * Get metadata for multiple users in a single query.
     * Returns a map of userId to their metadata.
     * Users without metadata will have null as their value.
     */
    public static Map<String, JsonObject> getBulkUserMetadata(AppIdentifier appIdentifier, Storage storage,
                                                              @Nonnull java.util.List<String> userIds)
            throws StorageQueryException {
        if (userIds == null || userIds.isEmpty()) {
            return new java.util.HashMap<>();
        }

        UserMetadataSQLStorage umdStorage = StorageUtils.getUserMetadataStorage(storage);

        try {
            return umdStorage.startTransaction(con -> {
                Map<String, JsonObject> metadataMap = umdStorage.getMultipleUsersMetadatas_Transaction(appIdentifier, con, userIds);

                // Ensure all requested userIds are in the result, with null for missing ones
                Map<String, JsonObject> result = new java.util.HashMap<>();
                for (String userId : userIds) {
                    result.put(userId, metadataMap.get(userId)); // null if not found
                }
                return result;
            });
        } catch (StorageTransactionLogicException e) {
            throw new StorageQueryException(e);
        }
    }

    @TestOnly
    public static void deleteUserMetadata(Main main, @Nonnull String userId) throws StorageQueryException {
        Storage storage = StorageLayer.getStorage(main);
        deleteUserMetadata(ResourceDistributor.getAppForTesting().toAppIdentifier(), storage, userId);
    }

    public static void deleteUserMetadata(AppIdentifier appIdentifier, Storage storage,
                                          @Nonnull String userId) throws StorageQueryException {
        StorageUtils.getUserMetadataStorage(storage).deleteUserMetadata(appIdentifier, userId);
    }
}
