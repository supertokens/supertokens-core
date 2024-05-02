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
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.config.Config;
import io.supertokens.config.CoreConfig;
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
import io.supertokens.utils.SemVer;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public abstract class BaseCreateOrUpdate extends WebserverAPI {

    public BaseCreateOrUpdate(Main main) {
        super(main, RECIPE_ID.MULTITENANCY.toString());
    }

    protected void handle(HttpServletRequest req, TenantIdentifier sourceTenantIdentifier,
                          TenantIdentifier targetTenantIdentifier,
                          JsonObject input, HttpServletResponse resp)
            throws ServletException, IOException {

        Boolean emailPasswordEnabled = InputParser.parseBooleanOrThrowError(input, "emailPasswordEnabled", true);
        Boolean thirdPartyEnabled = InputParser.parseBooleanOrThrowError(input, "thirdPartyEnabled", true);
        Boolean passwordlessEnabled = InputParser.parseBooleanOrThrowError(input, "passwordlessEnabled", true);
        JsonObject coreConfig = InputParser.parseJsonObjectOrThrowError(input, "coreConfig", true);

        String[] firstFactors = null;
        boolean hasFirstFactors = false;
        String[] requiredSecondaryFactors = null;
        boolean hasRequiredSecondaryFactors = false;

        Boolean useFirstFactorsFromStaticIfEmpty = null;
        Boolean useThirdPartyProvidersFromStaticIfEmpty = null;

        if (getVersionFromRequest(req).greaterThanOrEqualTo(SemVer.v5_0)) {
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

        if (getVersionFromRequest(req).greaterThanOrEqualTo(SemVer.v5_1)) {
            if (input.has("useFirstFactorsFromStaticIfEmpty")) {
                useFirstFactorsFromStaticIfEmpty = InputParser.parseBooleanOrThrowError(input,
                        "useFirstFactorsFromStaticIfEmpty", false);
            }
            if (input.has("useThirdPartyProvidersFromStaticIfEmpty")) {
                useThirdPartyProvidersFromStaticIfEmpty = InputParser.parseBooleanOrThrowError(input,
                        "useThirdPartyProvidersFromStaticIfEmpty", false);
            }
        }

        if (hasFirstFactors && firstFactors != null && firstFactors.length == 0) {
            throw new ServletException(new BadRequestException("firstFactors cannot be empty. Set null instead to remove all first factors."));
        }

        if (hasRequiredSecondaryFactors && requiredSecondaryFactors != null && requiredSecondaryFactors.length == 0) {
            throw new ServletException(new BadRequestException("requiredSecondaryFactors cannot be empty. Set null instead to remove all required secondary factors."));
        }

        CoreConfig baseConfig = Config.getBaseConfig(main);
        if (baseConfig.getSuperTokensLoadOnlyCUD() != null) {
            if (!(targetTenantIdentifier.getConnectionUriDomain().equals(TenantIdentifier.DEFAULT_CONNECTION_URI) || targetTenantIdentifier.getConnectionUriDomain().equals(baseConfig.getSuperTokensLoadOnlyCUD()))) {
                throw new ServletException(new BadRequestException("Creation of connection uri domain or app or " +
                        "tenant is disallowed"));
            }
        }

        TenantConfig tenantConfig = Multitenancy.getTenantInfo(main,
                new TenantIdentifier(targetTenantIdentifier.getConnectionUriDomain(), targetTenantIdentifier.getAppId(),
                        targetTenantIdentifier.getTenantId()));

        boolean createdNew = false;

        if (tenantConfig == null) {
            // Creating a new tenant/app/cud

            if (targetTenantIdentifier.getTenantId().equals(TenantIdentifier.DEFAULT_TENANT_ID)) {
                // We enable all the recipes by default while creating app or CUD
                tenantConfig = new TenantConfig(
                        targetTenantIdentifier,
                        new EmailPasswordConfig(true),
                        new ThirdPartyConfig(true, true, null),
                        new PasswordlessConfig(true),
                        null, true,
                        null, new JsonObject()
                );
            } else {
                // We disable all recipes by default while creating tenant
                boolean defaultRecipeEnabledState = getVersionFromRequest(req).greaterThanOrEqualTo(SemVer.v5_1);

                // We use static config if empty for first factors & thirdparty providers only if version is less than 5.1
                boolean defaultUseStaticIfEmptyState = !getVersionFromRequest(req).greaterThanOrEqualTo(SemVer.v5_1);

                tenantConfig = new TenantConfig(
                        targetTenantIdentifier,
                        new EmailPasswordConfig(defaultRecipeEnabledState),
                        new ThirdPartyConfig(defaultRecipeEnabledState, defaultUseStaticIfEmptyState, null),
                        new PasswordlessConfig(defaultRecipeEnabledState),
                        null, defaultUseStaticIfEmptyState, null, new JsonObject()
                );
            }
            createdNew = true;
        }

        if (getVersionFromRequest(req).greaterThanOrEqualTo(SemVer.v5_1)) {
            // if using first factors as a way to enable/disable recipes but the tenant was already created using an
            // old CDI, we automatically enable the recipes to make things work seamlessly
            if (hasFirstFactors && firstFactors != null) {
                if (List.of(firstFactors).contains("emailpassword") && emailPasswordEnabled == null) {
                    emailPasswordEnabled = true;
                }
                if (List.of(firstFactors).contains("thirdparty") && thirdPartyEnabled == null) {
                    thirdPartyEnabled = true;
                }
                if ((List.of(firstFactors).contains("otp-email") || List.of(firstFactors).contains("otp-phone") || List.of(firstFactors).contains("link-email") || List.of(firstFactors).contains("link-phone")) && passwordlessEnabled == null) {
                    passwordlessEnabled = true;
                }
            }
        }

        if (emailPasswordEnabled != null) {
            tenantConfig = new TenantConfig(
                    tenantConfig.tenantIdentifier,
                    new EmailPasswordConfig(emailPasswordEnabled),
                    tenantConfig.thirdPartyConfig,
                    tenantConfig.passwordlessConfig,
                    tenantConfig.firstFactors, tenantConfig.useFirstFactorsFromStaticConfigIfEmpty,
                    tenantConfig.requiredSecondaryFactors, tenantConfig.coreConfig
            );
        }

        if (thirdPartyEnabled != null) {
            tenantConfig = new TenantConfig(
                    tenantConfig.tenantIdentifier,
                    tenantConfig.emailPasswordConfig,
                    new ThirdPartyConfig(thirdPartyEnabled,
                            tenantConfig.thirdPartyConfig.useThirdPartyProvidersFromStaticConfigIfEmpty,
                            tenantConfig.thirdPartyConfig.providers),
                    tenantConfig.passwordlessConfig,
                    tenantConfig.firstFactors, tenantConfig.useFirstFactorsFromStaticConfigIfEmpty,
                    tenantConfig.requiredSecondaryFactors, tenantConfig.coreConfig
            );
        }

        if (passwordlessEnabled != null) {
            tenantConfig = new TenantConfig(
                    tenantConfig.tenantIdentifier,
                    tenantConfig.emailPasswordConfig,
                    tenantConfig.thirdPartyConfig,
                    new PasswordlessConfig(passwordlessEnabled),
                    tenantConfig.firstFactors, tenantConfig.useFirstFactorsFromStaticConfigIfEmpty,
                    tenantConfig.requiredSecondaryFactors, tenantConfig.coreConfig
            );
        }

        if (hasFirstFactors) {
            tenantConfig = new TenantConfig(
                    tenantConfig.tenantIdentifier,
                    tenantConfig.emailPasswordConfig,
                    tenantConfig.thirdPartyConfig,
                    tenantConfig.passwordlessConfig,
                    firstFactors, tenantConfig.useFirstFactorsFromStaticConfigIfEmpty,
                    tenantConfig.requiredSecondaryFactors, tenantConfig.coreConfig
            );
            if (useFirstFactorsFromStaticIfEmpty == null) {
                useFirstFactorsFromStaticIfEmpty = false;
            }
        }

        if (hasRequiredSecondaryFactors) {
            tenantConfig = new TenantConfig(
                    tenantConfig.tenantIdentifier,
                    tenantConfig.emailPasswordConfig,
                    tenantConfig.thirdPartyConfig,
                    tenantConfig.passwordlessConfig,
                    tenantConfig.firstFactors, tenantConfig.useFirstFactorsFromStaticConfigIfEmpty,
                    requiredSecondaryFactors, tenantConfig.coreConfig
            );
        }

        if (useFirstFactorsFromStaticIfEmpty != null) {
            tenantConfig = new TenantConfig(
                    tenantConfig.tenantIdentifier,
                    tenantConfig.emailPasswordConfig,
                    tenantConfig.thirdPartyConfig,
                    tenantConfig.passwordlessConfig,
                    tenantConfig.firstFactors, useFirstFactorsFromStaticIfEmpty,
                    tenantConfig.requiredSecondaryFactors, tenantConfig.coreConfig
            );
        }

        if (useThirdPartyProvidersFromStaticIfEmpty != null) {
            tenantConfig = new TenantConfig(
                    tenantConfig.tenantIdentifier,
                    tenantConfig.emailPasswordConfig,
                    new ThirdPartyConfig(tenantConfig.thirdPartyConfig.enabled,
                            useThirdPartyProvidersFromStaticIfEmpty,
                            tenantConfig.thirdPartyConfig.providers),
                    tenantConfig.passwordlessConfig,
                    tenantConfig.firstFactors, tenantConfig.useFirstFactorsFromStaticConfigIfEmpty,
                    tenantConfig.requiredSecondaryFactors, tenantConfig.coreConfig
            );
        }

        if (coreConfig != null) {
            coreConfig = mergeConfig(tenantConfig.coreConfig, coreConfig);
            tenantConfig = new TenantConfig(
                    tenantConfig.tenantIdentifier,
                    tenantConfig.emailPasswordConfig,
                    tenantConfig.thirdPartyConfig,
                    tenantConfig.passwordlessConfig,
                    tenantConfig.firstFactors, tenantConfig.useFirstFactorsFromStaticConfigIfEmpty,
                    tenantConfig.requiredSecondaryFactors, coreConfig
            );
        }

        try {
            Multitenancy.checkPermissionsForCreateOrUpdate(
                    main, sourceTenantIdentifier, tenantConfig.tenantIdentifier);

            Multitenancy.addNewOrUpdateAppOrTenant(main, tenantConfig, shouldProtectProtectedConfig(req), false, true);
            JsonObject result = new JsonObject();
            result.addProperty("status", "OK");
            result.addProperty("createdNew", createdNew);
            super.sendJsonResponse(200, result, resp);

        } catch (BadPermissionException | StorageQueryException | FeatureNotEnabledException
                 | TenantOrAppNotFoundException e) {
            throw new ServletException(e);
        } catch (CannotModifyBaseConfigException e) {
            throw new ServletException(new BadRequestException("Cannot modify base config"));
        } catch (InvalidConfigException e) {
            throw new ServletException(new BadRequestException("Invalid core config: " + e.getMessage()));
        } catch (InvalidProviderConfigException e) {
            throw new ServletException(new BadRequestException("Invalid third party config: " + e.getMessage()));
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
