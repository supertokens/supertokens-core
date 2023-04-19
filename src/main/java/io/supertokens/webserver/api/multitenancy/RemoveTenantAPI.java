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
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.api.multitenancy.BaseRemove;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public class RemoveTenantAPI extends BaseRemove {
    private static final long serialVersionUID = -4641988458637882374L;

    public RemoveTenantAPI(Main main) {
        super(main);
    }

    @Override
    public String getPath() {
        return "/recipe/multitenancy/tenant/remove";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);

        String tenantId = InputParser.parseStringOrThrowError(input, "tenantId", false);

        try {
            TenantIdentifier sourceTenantIdentifier = this.getTenantIdentifierWithStorageFromRequest(req);

            super.handle(
                    sourceTenantIdentifier,
                    new TenantIdentifier(sourceTenantIdentifier.getConnectionUriDomain(), sourceTenantIdentifier.getAppId(), tenantId),
                    resp);

        } catch (TenantOrAppNotFoundException e) {
            throw new ServletException(e);
        }

    }
}
