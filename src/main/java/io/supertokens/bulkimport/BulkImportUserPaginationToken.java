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

    public BulkImportUserPaginationToken(String bulkImportUserId) {
        this.bulkImportUserId = bulkImportUserId;
    }

    public static BulkImportUserPaginationToken extractTokenInfo(String token) throws InvalidTokenException {
        try {
            String bulkImportUserId = new String(Base64.getDecoder().decode(token));
            return new BulkImportUserPaginationToken(bulkImportUserId);
        } catch (Exception e) {
            throw new InvalidTokenException();
        }
    }

    public String generateToken() {
        return new String(Base64.getEncoder().encode((this.bulkImportUserId).getBytes()));
    }

    public static class InvalidTokenException extends Exception {

        private static final long serialVersionUID = 6289026174830695478L;
    }
}
