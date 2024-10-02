/*
 *    Copyright (c) 2023, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.webserver.api.multitenancy;

import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.multitenancy.exception.CannotDeleteNullConnectionUriDomainException;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.Utils;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public class RemoveConnectionUriDomainAPI extends WebserverAPI {
    private static final long serialVersionUID = -4641988458637882374L;

    public RemoveConnectionUriDomainAPI(Main main) {
        super(main, RECIPE_ID.MULTITENANCY.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/multitenancy/connectionuridomain/remove";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);

        String connectionUriDomain = InputParser.parseStringOrThrowError(input, "connectionUriDomain", false);
        connectionUriDomain = Utils.normalizeAndValidateConnectionUriDomain(connectionUriDomain);

        if (connectionUriDomain.equals(TenantIdentifier.DEFAULT_CONNECTION_URI)) {
            throw new ServletException(new BadPermissionException("Cannot delete the default connection uri domain"));
        }

        try {
            TenantIdentifier sourceTenantIdentifier = this.getTenantIdentifier(req);
            if (!sourceTenantIdentifier.equals(new TenantIdentifier(null, null, null))) {
                throw new BadPermissionException(
                        "Only the public tenantId, public appId and default connectionUriDomain is allowed to delete " +
                                "a connectionUriDomain");
            }

            boolean didExist = Multitenancy.deleteConnectionUriDomain(connectionUriDomain, main);
            JsonObject result = new JsonObject();
            result.addProperty("status", "OK");
            result.addProperty("didExist", didExist);
            super.sendJsonResponse(200, result, resp);

        } catch (TenantOrAppNotFoundException | BadPermissionException | StorageQueryException |
                 CannotDeleteNullConnectionUriDomainException e) {
            throw new ServletException(e);
        }

    }
}
