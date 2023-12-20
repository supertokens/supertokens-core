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

import com.google.gson.JsonArray;
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
import java.util.Arrays;
import java.util.HashSet;

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

        Boolean emailPasswordEnabled = InputParser.parseBooleanOrThrowError(input, "emailPasswordEnabled", true);
        Boolean thirdPartyEnabled = InputParser.parseBooleanOrThrowError(input, "thirdPartyEnabled", true);
        Boolean passwordlessEnabled = InputParser.parseBooleanOrThrowError(input, "passwordlessEnabled", true);
        JsonObject coreConfig = InputParser.parseJsonObjectOrThrowError(input, "coreConfig", true);

        Boolean totpEnabled = null;
        String[] firstFactors = null;
        boolean hasFirstFactors = false;
        String[] requiredSecondaryFactors = null;
        boolean hasRequiredSecondaryFactors = false;

        if (getVersionFromRequest(req).greaterThanOrEqualTo(SemVer.v5_0)) {
            totpEnabled = InputParser.parseBooleanOrThrowError(input, "totpEnabled", true);
            hasFirstFactors = input.has("firstFactors");
            if (hasFirstFactors && !input.get("firstFactors").isJsonNull()) {
                JsonArray firstFactorsArr = InputParser.parseArrayOrThrowError(input, "firstFactors", true);
                firstFactors = new String[firstFactorsArr.size()];
                for (int i = 0; i < firstFactors.length; i++) {
                    firstFactors[i] = InputParser.parseStringFromElementOrThrowError(firstFactorsArr.get(i), "firstFactors", false);
                }
                if (firstFactors.length != new HashSet<>(Arrays.asList(firstFactors)).size()) {
                    throw new ServletException(new BadRequestException("firstFactors input should not contain duplicate values"));
                }
            }
            hasRequiredSecondaryFactors = input.has("requiredSecondaryFactors");
            if (hasRequiredSecondaryFactors && !input.get("requiredSecondaryFactors").isJsonNull()) {
                JsonArray requiredSecondaryFactorsArr = InputParser.parseArrayOrThrowError(input, "requiredSecondaryFactors", true);
                requiredSecondaryFactors = new String[requiredSecondaryFactorsArr.size()];
                for (int i = 0; i < requiredSecondaryFactors.length; i++) {
                    requiredSecondaryFactors[i] = InputParser.parseStringFromElementOrThrowError(requiredSecondaryFactorsArr.get(i), "requiredSecondaryFactors", false);
                }
                if (requiredSecondaryFactors.length != new HashSet<>(Arrays.asList(requiredSecondaryFactors)).size()) {
                    throw new ServletException(new BadRequestException("requiredSecondaryFactors input should not contain duplicate values"));
                }
            }
        }

        TenantIdentifier sourceTenantIdentifier;
        try {
            sourceTenantIdentifier = this.getTenantIdentifierWithStorageFromRequest(req);
        } catch (TenantOrAppNotFoundException e) {
            throw new ServletException(e);
        }

        super.handle(
                req, sourceTenantIdentifier,
                new TenantIdentifier(sourceTenantIdentifier.getConnectionUriDomain(), sourceTenantIdentifier.getAppId(), tenantId),
                emailPasswordEnabled, thirdPartyEnabled, passwordlessEnabled,
                totpEnabled, hasFirstFactors, firstFactors, hasRequiredSecondaryFactors, requiredSecondaryFactors,
                coreConfig, resp);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        try {
            TenantIdentifierWithStorage tenantIdentifier = this.getTenantIdentifierWithStorageFromRequest(req);
            TenantConfig config = Multitenancy.getTenantInfo(main, tenantIdentifier);
            if (config == null) {
                throw new TenantOrAppNotFoundException(tenantIdentifier);
            }
            boolean shouldProtect = shouldProtectProtectedConfig(req);
            JsonObject result = config.toJson(shouldProtect, tenantIdentifier.getStorage(), CoreConfig.PROTECTED_CONFIGS);
            result.addProperty("status", "OK");

            if (getVersionFromRequest(req).lesserThan(SemVer.v5_0)) {
                result.remove("totp");
                result.remove("mfa");
            }

            super.sendJsonResponse(200, result, resp);
        } catch (TenantOrAppNotFoundException e) {
            JsonObject result = new JsonObject();
            result.addProperty("status", "TENANT_NOT_FOUND_ERROR");

            super.sendJsonResponse(200, result, resp);
        }
    }
}
