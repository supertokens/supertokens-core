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

package io.supertokens.webserver.api.core;

import com.google.gson.JsonObject;
import io.supertokens.AppIdentifierWithStorageAndUserIdMapping;
import io.supertokens.Main;
import io.supertokens.authRecipe.AuthRecipe;
import io.supertokens.pluginInterface.emailpassword.exceptions.UnknownUserIdException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.useridmapping.UserIdType;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public class DeleteUserAPI extends WebserverAPI {

    private static final long serialVersionUID = -2225750492558064634L;

    public DeleteUserAPI(Main main) {
        super(main, "");
    }

    @Override
    public String getPath() {
        return "/user/remove";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        // this API is app specific
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);
        String userId = InputParser.parseStringOrThrowError(input, "userId", false);
        try {
            AppIdentifierWithStorageAndUserIdMapping appIdentifierWithStorageAndUserIdMapping =
                    this.getAppIdentifierWithStorageAndUserIdMappingFromRequest(req, userId, UserIdType.ANY);

            AuthRecipe.deleteUser(appIdentifierWithStorageAndUserIdMapping.appIdentifierWithStorage, userId,
                    appIdentifierWithStorageAndUserIdMapping.userIdMapping);
        } catch (StorageQueryException | TenantOrAppNotFoundException | StorageTransactionLogicException e) {
            throw new ServletException(e);
        } catch (UnknownUserIdException e) {
            // Do nothing
        }

        JsonObject result = new JsonObject();
        result.addProperty("status", "OK");
        super.sendJsonResponse(200, result, resp);
    }
}
