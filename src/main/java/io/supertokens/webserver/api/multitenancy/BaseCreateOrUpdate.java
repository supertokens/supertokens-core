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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.multitenancy.exception.CannotModifyBaseConfigException;
import io.supertokens.multitenancy.exception.DeletionInProgressException;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.*;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.thirdparty.InvalidProviderConfigException;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Map;

public abstract class BaseCreateOrUpdate extends WebserverAPI {

    public BaseCreateOrUpdate(Main main) {
        super(main, RECIPE_ID.MULTITENANCY.toString());
    }

    protected void handle(TenantIdentifier sourceTenantIdentifier, TenantIdentifier targetTenantIdentifier, Boolean emailPasswordEnabled, Boolean thirdPartyEnabled, Boolean passwordlessEnabled, JsonObject coreConfig, HttpServletResponse resp)
            throws ServletException, IOException {

        TenantConfig tenantConfig = Multitenancy.getTenantInfo(main,
                new TenantIdentifier(targetTenantIdentifier.getConnectionUriDomain(), targetTenantIdentifier.getAppId(), targetTenantIdentifier.getTenantId()));

        boolean createdNew = false;

        if (tenantConfig == null) {
            tenantConfig = new TenantConfig(
                    targetTenantIdentifier,
                    new EmailPasswordConfig(false),
                    new ThirdPartyConfig(false, null),
                    new PasswordlessConfig(false),
                    new JsonObject()
            );
            createdNew = true;
        }

        if (emailPasswordEnabled != null) {
            tenantConfig = new TenantConfig(
                    tenantConfig.tenantIdentifier,
                    new EmailPasswordConfig(emailPasswordEnabled),
                    tenantConfig.thirdPartyConfig,
                    tenantConfig.passwordlessConfig,
                    tenantConfig.coreConfig
            );
        }

        if (thirdPartyEnabled != null) {
            tenantConfig = new TenantConfig(
                    tenantConfig.tenantIdentifier,
                    tenantConfig.emailPasswordConfig,
                    new ThirdPartyConfig(thirdPartyEnabled, tenantConfig.thirdPartyConfig.providers),
                    tenantConfig.passwordlessConfig,
                    tenantConfig.coreConfig
            );
        }

        if (passwordlessEnabled != null) {
            tenantConfig = new TenantConfig(
                    tenantConfig.tenantIdentifier,
                    tenantConfig.emailPasswordConfig,
                    tenantConfig.thirdPartyConfig,
                    new PasswordlessConfig(passwordlessEnabled),
                    tenantConfig.coreConfig
            );
        }

        if (coreConfig != null) {
            coreConfig = mergeConfig(tenantConfig.coreConfig, coreConfig);
            tenantConfig = new TenantConfig(
                    tenantConfig.tenantIdentifier,
                    tenantConfig.emailPasswordConfig,
                    tenantConfig.thirdPartyConfig,
                    tenantConfig.passwordlessConfig,
                    coreConfig
            );
        }

        try {
            Multitenancy.addNewOrUpdateAppOrTenant(
                    main, sourceTenantIdentifier, tenantConfig, false); // TODO do sass check
            JsonObject result = new JsonObject();
            result.addProperty("status", "OK");
            result.addProperty("createdNew", createdNew);
            super.sendJsonResponse(200, result, resp);

        } catch (DeletionInProgressException | CannotModifyBaseConfigException | BadPermissionException |
                 StorageQueryException | FeatureNotEnabledException |
                 InvalidProviderConfigException | TenantOrAppNotFoundException e) {
            throw new ServletException(e);
        } catch (InvalidConfigException e) {
            JsonObject result = new JsonObject();
            result.addProperty("status", "INVALID_CORE_CONFIG_ERROR");
            result.addProperty("message", e.getMessage());
            super.sendJsonResponse(200, result, resp);
        }
    }

    private JsonObject mergeConfig(JsonObject baseConfig, JsonObject newConfig) {
        JsonObject result = new JsonObject();
        for (Map.Entry<String, JsonElement> entry : baseConfig.entrySet()) {
            result.add(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, JsonElement> entry : newConfig.entrySet()) {
            if (entry.getValue().isJsonNull() && result.has(entry.getKey())) {
                result.remove(entry.getKey());
                continue;
            }
            result.add(entry.getKey(), entry.getValue());
        }
        return result;
    }
}
