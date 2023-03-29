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
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifierWithStorage;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifierWithStorage;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.usermetadata.sqlStorage.UserMetadataSQLStorage;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.utils.MetadataUtils;
import org.jetbrains.annotations.TestOnly;

import javax.annotation.Nonnull;

public class UserMetadata {

    @TestOnly
    public static JsonObject updateUserMetadata(Main main,
                                                @Nonnull String userId, @Nonnull JsonObject metadataUpdate)
            throws StorageQueryException, StorageTransactionLogicException {
        Storage storage = StorageLayer.getStorage(main);
        try {
            return updateUserMetadata(
                    new AppIdentifierWithStorage(null, null, storage),
                    userId, metadataUpdate);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    public static JsonObject updateUserMetadata(AppIdentifierWithStorage appIdentifierWithStorage,
                                                @Nonnull String userId, @Nonnull JsonObject metadataUpdate)
            throws StorageQueryException, StorageTransactionLogicException, TenantOrAppNotFoundException {
        UserMetadataSQLStorage storage = appIdentifierWithStorage.getUserMetadataStorage();

        try {
            return storage.startTransaction((con) -> {
                JsonObject originalMetadata = storage.getUserMetadata_Transaction(appIdentifierWithStorage, con,
                        userId);

                JsonObject updatedMetadata = originalMetadata == null ? new JsonObject() : originalMetadata;
                MetadataUtils.shallowMergeMetadataUpdate(updatedMetadata, metadataUpdate);

                try {
                    storage.setUserMetadata_Transaction(appIdentifierWithStorage, con, userId, updatedMetadata);
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

    @TestOnly
    public static JsonObject getUserMetadata(Main main, @Nonnull String userId) throws StorageQueryException {
        Storage storage = StorageLayer.getStorage(main);
        return getUserMetadata(new AppIdentifierWithStorage(null, null, storage), userId);
    }

    public static JsonObject getUserMetadata(AppIdentifierWithStorage appIdentifierWithStorage,
                                             @Nonnull String userId)
            throws StorageQueryException {
        UserMetadataSQLStorage storage = appIdentifierWithStorage.getUserMetadataStorage();

        JsonObject metadata = storage.getUserMetadata(appIdentifierWithStorage, userId);

        if (metadata == null) {
            return new JsonObject();
        }

        return metadata;
    }

    @TestOnly
    public static void deleteUserMetadata(Main main, @Nonnull String userId) throws StorageQueryException {
        Storage storage = StorageLayer.getStorage(main);
        deleteUserMetadata(new AppIdentifierWithStorage(null, null, storage), userId);
    }

    public static void deleteUserMetadata(AppIdentifierWithStorage appIdentifierWithStorage,
                                          @Nonnull String userId) throws StorageQueryException {
        appIdentifierWithStorage.getUserMetadataStorage().deleteUserMetadata(appIdentifierWithStorage, userId);
    }
}
