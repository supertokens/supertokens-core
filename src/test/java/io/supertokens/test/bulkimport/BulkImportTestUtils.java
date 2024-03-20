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


package io.supertokens.test.bulkimport;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.supertokens.pluginInterface.bulkimport.BulkImportUser;
import io.supertokens.pluginInterface.bulkimport.BulkImportUser.LoginMethod;
import io.supertokens.pluginInterface.bulkimport.BulkImportUser.TotpDevice;
import io.supertokens.pluginInterface.bulkimport.BulkImportUser.UserRole;

public class BulkImportTestUtils {
    public static List<BulkImportUser> generateBulkImportUser(int numberOfUsers) {
        List<BulkImportUser> users = new ArrayList<>();
        JsonParser parser = new JsonParser();

        for (int i = 0; i < numberOfUsers; i++) {
            String email = "user" + i + "@example.com";
            String id = io.supertokens.utils.Utils.getUUID();
            String externalId = io.supertokens.utils.Utils.getUUID();

            JsonObject userMetadata = parser.parse("{\"key1\":\"value1\",\"key2\":{\"key3\":\"value3\"}}").getAsJsonObject();

            List<UserRole> userRoles = new ArrayList<>();
            userRoles.add(new UserRole("role1", List.of("public")));
            userRoles.add(new UserRole("role2", List.of("public")));

            List<TotpDevice> totpDevices = new ArrayList<>();
            totpDevices.add(new TotpDevice("secretKey", 30, 1, "deviceName"));

            List<LoginMethod> loginMethods = new ArrayList<>();
            long currentTimeMillis =  System.currentTimeMillis();
            loginMethods.add(new LoginMethod(List.of("public", "t1"), "emailpassword", true, true, currentTimeMillis, email, "$2a", "BCRYPT", null, null, null));
            loginMethods.add(new LoginMethod(List.of("public", "t1"), "thirdparty", true, false, currentTimeMillis,  email,  null, null, "thirdPartyId" + i, "thirdPartyUserId" + i, null));
            loginMethods.add(new LoginMethod(List.of("public", "t1"), "passwordless", true, false, currentTimeMillis, email, null, null, null, null, null));
            users.add(new BulkImportUser(id, externalId, userMetadata, userRoles, totpDevices, loginMethods));
        }
        return users;
    }
}
