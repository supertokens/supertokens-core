/*
 *    Copyright (c) 2024, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.bulkimport;

import java.util.Base64;

public class BulkImportUserPaginationToken {
    public final String bulkImportUserId;
    public final long createdAt;

    public BulkImportUserPaginationToken(String bulkImportUserId, long timeJoined) {
        this.bulkImportUserId = bulkImportUserId;
        this.createdAt = timeJoined;
    }

    public static BulkImportUserPaginationToken extractTokenInfo(String token) throws InvalidTokenException {
        try {
            String decodedPaginationToken = new String(Base64.getDecoder().decode(token));
            String[] splitDecodedToken = decodedPaginationToken.split(";");
            if (splitDecodedToken.length != 2) {
                throw new InvalidTokenException();
            }
            String bulkImportUserId = splitDecodedToken[0];
            long timeJoined = Long.parseLong(splitDecodedToken[1]);
            return new BulkImportUserPaginationToken(bulkImportUserId, timeJoined);
        } catch (Exception e) {
            throw new InvalidTokenException();
        }
    }

    public String generateToken() {
        return new String(Base64.getEncoder().encode((this.bulkImportUserId + ";" + this.createdAt).getBytes()));
    }

    public static class InvalidTokenException extends Exception {

        private static final long serialVersionUID = 6289026174830695478L;
    }
}
