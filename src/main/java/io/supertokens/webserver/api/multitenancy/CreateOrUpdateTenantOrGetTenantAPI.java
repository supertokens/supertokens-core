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
import io.supertokens.config.CoreConfig;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.pluginInterface.multitenancy.*;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.utils.SemVer;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.Utils;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@Deprecated
public class CreateOrUpdateTenantOrGetTenantAPI extends BaseCreateOrUpdate {

    private static final long serialVersionUID = -4641988458637882374L;

    public CreateOrUpdateTenantOrGetTenantAPI(Main main) {
        super(main);
    }

    @Override
    public String getPath() {
        return "/recipe/multitenancy/tenant";
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);

        String tenantId = InputParser.parseStringOrThrowError(input, "tenantId", true);
        if (tenantId != null) {
            tenantId = Utils.normalizeAndValidateTenantId(tenantId);
        }

        TenantIdentifier sourceTenantIdentifier;
        try {
            sourceTenantIdentifier = getTenantIdentifier(req);
        } catch (TenantOrAppNotFoundException e) {
            throw new ServletException(e);
        }

        super.handle(
                req, resp, sourceTenantIdentifier,
                new TenantIdentifier(sourceTenantIdentifier.getConnectionUriDomain(), sourceTenantIdentifier.getAppId(),
                        tenantId),
                input, false);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        try {
            TenantIdentifier tenantIdentifier = getTenantIdentifier(req);
            TenantConfig config = Multitenancy.getTenantInfo(main, tenantIdentifier);
            if (config == null) {
                throw new TenantOrAppNotFoundException(tenantIdentifier);
            }
            boolean shouldProtect = shouldProtectProtectedConfig(req);
            JsonObject result;
            if (getVersionFromRequest(req).lesserThan(SemVer.v5_0)) {
                result = config.toJsonLesserThanOrEqualTo4_0(shouldProtect, getTenantStorage(req),
                        CoreConfig.PROTECTED_CONFIGS);
            } else {
                result = config.toJson5_0(shouldProtect, getTenantStorage(req), CoreConfig.PROTECTED_CONFIGS);
            }

            result.addProperty("status", "OK");

            super.sendJsonResponse(200, result, resp);
        } catch (TenantOrAppNotFoundException e) {
            JsonObject result = new JsonObject();
            result.addProperty("status", "TENANT_NOT_FOUND_ERROR");

            super.sendJsonResponse(200, result, resp);
        }
    }
}
