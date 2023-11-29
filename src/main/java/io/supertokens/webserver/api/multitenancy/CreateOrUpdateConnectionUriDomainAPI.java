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
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
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

public class CreateOrUpdateConnectionUriDomainAPI extends BaseCreateOrUpdate {

    private static final long serialVersionUID = -4641988458637882374L;

    public CreateOrUpdateConnectionUriDomainAPI(Main main) {
        super(main);
    }

    @Override
    public String getPath() {
        return "/recipe/multitenancy/connectionuridomain";
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);

        String connectionUriDomain = InputParser.parseStringOrThrowError(input, "connectionUriDomain", true);
        if (connectionUriDomain != null) {
            connectionUriDomain = Utils.normalizeAndValidateConnectionUriDomain(connectionUriDomain);
        }
        Boolean emailPasswordEnabled = InputParser.parseBooleanOrThrowError(input, "emailPasswordEnabled", true);
        Boolean thirdPartyEnabled = InputParser.parseBooleanOrThrowError(input, "thirdPartyEnabled", true);
        Boolean passwordlessEnabled = InputParser.parseBooleanOrThrowError(input, "passwordlessEnabled", true);
        JsonObject coreConfig = InputParser.parseJsonObjectOrThrowError(input, "coreConfig", true);

        Boolean totpEnabled = null;
        String[] firstFactors = null;
        boolean hasFirstFactors = false;
        String[] defaultRequiredFactorIds = null;
        boolean hasDefaultRequiredFactorIds = false;

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
            hasDefaultRequiredFactorIds = input.has("defaultRequiredFactorIds");
            if (hasDefaultRequiredFactorIds && !input.get("defaultRequiredFactorIds").isJsonNull()) {
                JsonArray defaultRequiredFactorIdsArr = InputParser.parseArrayOrThrowError(input, "defaultRequiredFactorIds", true);
                defaultRequiredFactorIds = new String[defaultRequiredFactorIdsArr.size()];
                for (int i = 0; i < defaultRequiredFactorIds.length; i++) {
                    defaultRequiredFactorIds[i] = InputParser.parseStringFromElementOrThrowError(defaultRequiredFactorIdsArr.get(i), "defaultRequiredFactorIds", false);
                }
                if (defaultRequiredFactorIds.length != new HashSet<>(Arrays.asList(defaultRequiredFactorIds)).size()) {
                    throw new ServletException(new BadRequestException("defaultRequiredFactorIds input should not contain duplicate values"));
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
                new TenantIdentifier(connectionUriDomain, null, null),
                emailPasswordEnabled, thirdPartyEnabled, passwordlessEnabled,
                totpEnabled, hasFirstFactors, firstFactors, hasDefaultRequiredFactorIds, defaultRequiredFactorIds,
                coreConfig, resp);

    }
}
