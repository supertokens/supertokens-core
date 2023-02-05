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
import io.supertokens.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
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
        try {
            return updateUserMetadata(new TenantIdentifier(null, null, null), main, userId, metadataUpdate);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException("Should never come here");
        }
    }

    public static JsonObject updateUserMetadata(TenantIdentifier tenantIdentifier, Main main,
                                                @Nonnull String userId, @Nonnull JsonObject metadataUpdate)
            throws StorageQueryException, StorageTransactionLogicException, TenantOrAppNotFoundException {
        UserMetadataSQLStorage storage = StorageLayer.getUserMetadataStorage(tenantIdentifier, main);

        return storage.startTransaction((con) -> {
            JsonObject originalMetadata = storage.getUserMetadata_Transaction(con, userId);

            JsonObject updatedMetadata = originalMetadata == null ? new JsonObject() : originalMetadata;
            MetadataUtils.shallowMergeMetadataUpdate(updatedMetadata, metadataUpdate);

            storage.setUserMetadata_Transaction(con, userId, updatedMetadata);

            return updatedMetadata;
        });
    }

    @TestOnly
    public static JsonObject getUserMetadata(Main main, @Nonnull String userId) throws StorageQueryException {
        try {
            return getUserMetadata(new TenantIdentifier(null, null, null), main, userId);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException("Should never come here");
        }
    }

    public static JsonObject getUserMetadata(TenantIdentifier tenantIdentifier, Main main,
                                             @Nonnull String userId)
            throws StorageQueryException, TenantOrAppNotFoundException {
        UserMetadataSQLStorage storage = StorageLayer.getUserMetadataStorage(tenantIdentifier, main);

        JsonObject metadata = storage.getUserMetadata(userId);

        if (metadata == null) {
            return new JsonObject();
        }

        return metadata;
    }

    @TestOnly
    public static void deleteUserMetadata(Main main, @Nonnull String userId) throws StorageQueryException {
        try {
            deleteUserMetadata(new TenantIdentifier(null, null, null), main, userId);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException("Should never come here");
        }
    }

    public static void deleteUserMetadata(TenantIdentifier tenantIdentifier, Main main,
                                          @Nonnull String userId) throws StorageQueryException,
            TenantOrAppNotFoundException {
        StorageLayer.getUserMetadataStorage(tenantIdentifier, main).deleteUserMetadata(userId);
    }
}
