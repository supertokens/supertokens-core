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

package io.supertokens.webserver.api.multitenancy.thirdparty;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlag;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.multitenancy.exception.CannotModifyBaseConfigException;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.*;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.thirdparty.InvalidProviderConfigException;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CreateOrUpdateThirdPartyConfigAPI extends WebserverAPI {

    private static final long serialVersionUID = -4641988458637882374L;

    public CreateOrUpdateThirdPartyConfigAPI(Main main) {
        super(main, RECIPE_ID.MULTITENANCY.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/multitenancy/config/thirdparty";
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);
        String thirdPartyId = InputParser.parseStringOrThrowError(input, "thirdPartyId", false);
        thirdPartyId = thirdPartyId.trim();

        try {
            TenantIdentifierWithStorage tenantIdentifier = this.getTenantIdentifierWithStorageFromRequest(req);

            if (!tenantIdentifier.equals(TenantIdentifier.BASE_TENANT)) {
                if (Arrays.stream(FeatureFlag.getInstance(main, new AppIdentifier(null, null)).getEnabledFeatures())
                        .noneMatch(ee_features -> ee_features == EE_FEATURES.MULTI_TENANCY)) {
                    throw new FeatureNotEnabledException(EE_FEATURES.MULTI_TENANCY);
                }
            }

            TenantConfig config = Multitenancy.getTenantInfo(main, tenantIdentifier);
            if (config == null) {
                throw new TenantOrAppNotFoundException(tenantIdentifier);
            }
            List<ThirdPartyConfig.Provider> newProviders = new ArrayList<>();

            boolean found = false;

            for (ThirdPartyConfig.Provider provider: config.thirdPartyConfig.providers) {
                // Loop through all the existing thirdParty providers in the db

                if (!provider.thirdPartyId.equals(thirdPartyId)) {
                    // if the thirdPartyId is not the same as the one we are trying to update, add it to the new list
                    newProviders.add(provider);
                } else {
                    // if the thirdPartyId is the same as the one we are trying to update, add the one from json input
                    // to the new list
                    ThirdPartyConfig.Provider newProvider = new Gson().fromJson(input, ThirdPartyConfig.Provider.class);
                    newProviders.add(newProvider);
                    found = true;
                }
            }
            if (!found) {
                // if the thirdPartyId is not found in the db, add the one from json input to the new list
                ThirdPartyConfig.Provider newProvider = new Gson().fromJson(input, ThirdPartyConfig.Provider.class);
                newProviders.add(newProvider);
            }
            TenantConfig updatedConfig = new TenantConfig(
                    config.tenantIdentifier,
                    config.emailPasswordConfig,
                    new ThirdPartyConfig(
                            config.thirdPartyConfig.enabled, newProviders.toArray(new ThirdPartyConfig.Provider[0])),
                    config.passwordlessConfig,
                    config.coreConfig);

            Multitenancy.addNewOrUpdateAppOrTenant(main, updatedConfig, shouldProtectDbConfig(req));

            JsonObject result = new JsonObject();
            result.addProperty("status", "OK");
            result.addProperty("createdNew", !found);
            super.sendJsonResponse(200, result, resp);

        } catch (CannotModifyBaseConfigException | BadPermissionException |
                 StorageQueryException | FeatureNotEnabledException | TenantOrAppNotFoundException e) {
            throw new ServletException(e);
        } catch (InvalidConfigException e) {
            throw new ServletException(new BadRequestException("Invalid core config: " + e.getMessage()));
        } catch (InvalidProviderConfigException e) {
            throw new ServletException(new BadRequestException("Invalid third party config: " + e.getMessage()));
        }
    }
}
