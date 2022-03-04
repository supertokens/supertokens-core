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

import javax.annotation.Nonnull;

import com.google.gson.JsonObject;

import io.supertokens.Main;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.usermetadata.sqlStorage.UserMetadataSQLStorage;
import io.supertokens.storageLayer.StorageLayer;

public class UserMetadata {
    public static JsonObject updateUserMetadata(Main main, @Nonnull String userId, @Nonnull JsonObject metadataUpdate)
            throws StorageQueryException, StorageTransactionLogicException {
        UserMetadataSQLStorage storage = StorageLayer.getUserMetadataStorage(main);

        return storage.startTransaction((con) -> {
            JsonObject originalMetadata = storage.getUserMetadata_Transaction(con, userId);

            final JsonObject updatedMetadata = originalMetadata == null ? new JsonObject() : originalMetadata;
            metadataUpdate.entrySet().forEach((entry) -> {
                updatedMetadata.remove(entry.getKey());
                if (!entry.getValue().isJsonNull()) {
                    updatedMetadata.add(entry.getKey(), entry.getValue());
                }
            });

            storage.setUserMetadata_Transaction(con, userId, updatedMetadata);

            return updatedMetadata;
        });
    }

    public static JsonObject getUserMetadata(Main main, @Nonnull String userId) throws StorageQueryException {
        UserMetadataSQLStorage storage = StorageLayer.getUserMetadataStorage(main);

        JsonObject metadata = storage.getUserMetadata(userId);

        if (metadata == null) {
            return new JsonObject();
        }

        return metadata;
    }

    public static void deleteUserMetadata(Main main, @Nonnull String userId) throws StorageQueryException {
        StorageLayer.getUserMetadataStorage(main).deleteUserMetadata(userId);
    }
}
