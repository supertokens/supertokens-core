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
import java.util.*;

public abstract class BaseCreateOrUpdate extends WebserverAPI {

    public BaseCreateOrUpdate(Main main) {
        super(main, RECIPE_ID.MULTITENANCY.toString());
    }

    protected void handle(HttpServletRequest req, TenantIdentifier sourceTenantIdentifier,
                          TenantIdentifier targetTenantIdentifier,
                          JsonObject input, HttpServletResponse resp)
            throws ServletException, IOException {

        try {
            if (getVersionFromRequest(req).lesserThan(SemVer.v5_0)) {
                this.handle3_0(req, sourceTenantIdentifier, targetTenantIdentifier, input, resp);
            } else {
                this.handle5_0(req, sourceTenantIdentifier, targetTenantIdentifier, input, resp);
            }
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

    protected void handle_v2(HttpServletRequest req, TenantIdentifier sourceTenantIdentifier,
                          TenantIdentifier targetTenantIdentifier,
                          JsonObject input, HttpServletResponse resp)
            throws ServletException, IOException {

        try {
            this.handle_v2_5_1(req, sourceTenantIdentifier, targetTenantIdentifier, input, resp);
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

    private TenantConfig applyTenantUpdates(
            TenantConfig tenantConfig,
            Boolean emailPasswordEnabled, Boolean thirdPartyEnabled, Boolean passwordlessEnabled,
            boolean hasFirstFactors, String[] firstFactors,
            boolean hasRequiredSecondaryFactors, String[] requiredSecondaryFactors,
            JsonObject coreConfig) {

        if (emailPasswordEnabled != null) {
            tenantConfig = new TenantConfig(
                    tenantConfig.tenantIdentifier,
                    new EmailPasswordConfig(emailPasswordEnabled),
                    tenantConfig.thirdPartyConfig,
                    tenantConfig.passwordlessConfig,
                    tenantConfig.firstFactors,
                    tenantConfig.requiredSecondaryFactors, tenantConfig.coreConfig
            );
        }

        if (thirdPartyEnabled != null) {
            tenantConfig = new TenantConfig(
                    tenantConfig.tenantIdentifier,
                    tenantConfig.emailPasswordConfig,
                    new ThirdPartyConfig(thirdPartyEnabled,
                            tenantConfig.thirdPartyConfig.providers),
                    tenantConfig.passwordlessConfig,
                    tenantConfig.firstFactors,
                    tenantConfig.requiredSecondaryFactors, tenantConfig.coreConfig
            );
        }

        if (passwordlessEnabled != null) {
            tenantConfig = new TenantConfig(
                    tenantConfig.tenantIdentifier,
                    tenantConfig.emailPasswordConfig,
                    tenantConfig.thirdPartyConfig,
                    new PasswordlessConfig(passwordlessEnabled),
                    tenantConfig.firstFactors,
                    tenantConfig.requiredSecondaryFactors, tenantConfig.coreConfig
            );
        }

        if (hasFirstFactors) {
            tenantConfig = new TenantConfig(
                    tenantConfig.tenantIdentifier,
                    tenantConfig.emailPasswordConfig,
                    tenantConfig.thirdPartyConfig,
                    tenantConfig.passwordlessConfig,
                    firstFactors,
                    tenantConfig.requiredSecondaryFactors, tenantConfig.coreConfig
            );
        }

        if (hasRequiredSecondaryFactors) {
            tenantConfig = new TenantConfig(
                    tenantConfig.tenantIdentifier,
                    tenantConfig.emailPasswordConfig,
                    tenantConfig.thirdPartyConfig,
                    tenantConfig.passwordlessConfig,
                    tenantConfig.firstFactors,
                    requiredSecondaryFactors, tenantConfig.coreConfig
            );
        }

        if (coreConfig != null) {
            coreConfig = mergeConfig(tenantConfig.coreConfig, coreConfig);
            tenantConfig = new TenantConfig(
                    tenantConfig.tenantIdentifier,
                    tenantConfig.emailPasswordConfig,
                    tenantConfig.thirdPartyConfig,
                    tenantConfig.passwordlessConfig,
                    tenantConfig.firstFactors,
                    tenantConfig.requiredSecondaryFactors, coreConfig
            );
        }

        return tenantConfig;
    }

    private void createOrUpdate(HttpServletRequest req, TenantIdentifier sourceTenantIdentifier,
                                TenantConfig tenantConfig)
            throws InvalidProviderConfigException, StorageQueryException, FeatureNotEnabledException,
            TenantOrAppNotFoundException, InvalidConfigException, CannotModifyBaseConfigException,
            BadPermissionException, IOException {
        Multitenancy.checkPermissionsForCreateOrUpdate(
                main, sourceTenantIdentifier, tenantConfig.tenantIdentifier);

        Multitenancy.addNewOrUpdateAppOrTenant(main, tenantConfig, shouldProtectProtectedConfig(req), false, true);
    }

    private void handle_v2_5_1(HttpServletRequest req, TenantIdentifier sourceTenantIdentifier,
                           TenantIdentifier targetTenantIdentifier, JsonObject input, HttpServletResponse resp)
            throws ServletException, IOException, InvalidProviderConfigException, StorageQueryException,
            FeatureNotEnabledException, TenantOrAppNotFoundException, InvalidConfigException,
            CannotModifyBaseConfigException, BadPermissionException {

        if (input.has("emailPasswordEnabled")) {
            throw new ServletException(new BadRequestException("emailPasswordEnabled is not allowed in this API version"));
        }

        if (input.has("thirdPartyEnabled")) {
            throw new ServletException(new BadRequestException("thirdPartyEnabled is not allowed in this API version"));
        }

        if (input.has("passwordlessEnabled")) {
            throw new ServletException(new BadRequestException("passwordlessEnabled is not allowed in this API version"));
        }

        JsonObject coreConfig = InputParser.parseJsonObjectOrThrowError(input, "coreConfig", true);

        String[] firstFactors = null;
        boolean hasFirstFactors;
        String[] requiredSecondaryFactors = null;
        boolean hasRequiredSecondaryFactors;

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
                        new ThirdPartyConfig(true, null),
                        new PasswordlessConfig(true),
                        null,
                        null, new JsonObject()
                );
            } else {
                tenantConfig = new TenantConfig(
                        targetTenantIdentifier,
                        new EmailPasswordConfig(true),
                        new ThirdPartyConfig(true, new ThirdPartyConfig.Provider[]{}),
                        new PasswordlessConfig(true),
                        new String[]{}, null, new JsonObject()
                );
            }
            createdNew = true;
        }

        Boolean recipeEnabledValue = hasFirstFactors ? false : null;

        tenantConfig = this.applyTenantUpdates(
                tenantConfig,
                recipeEnabledValue, recipeEnabledValue, recipeEnabledValue,
                hasFirstFactors, firstFactors,
                hasRequiredSecondaryFactors, requiredSecondaryFactors,
                coreConfig);
        this.createOrUpdate(req, sourceTenantIdentifier, tenantConfig);

        JsonObject result = new JsonObject();
        result.addProperty("status", "OK");
        result.addProperty("createdNew", createdNew);
        super.sendJsonResponse(200, result, resp);
    }

    private void handle5_0(HttpServletRequest req, TenantIdentifier sourceTenantIdentifier,
                           TenantIdentifier targetTenantIdentifier, JsonObject input, HttpServletResponse resp)
            throws ServletException, IOException, InvalidProviderConfigException, StorageQueryException,
            FeatureNotEnabledException, TenantOrAppNotFoundException, InvalidConfigException,
            CannotModifyBaseConfigException, BadPermissionException {

        Boolean emailPasswordEnabled = InputParser.parseBooleanOrThrowError(input, "emailPasswordEnabled", true);
        Boolean thirdPartyEnabled = InputParser.parseBooleanOrThrowError(input, "thirdPartyEnabled", true);
        Boolean passwordlessEnabled = InputParser.parseBooleanOrThrowError(input, "passwordlessEnabled", true);
        JsonObject coreConfig = InputParser.parseJsonObjectOrThrowError(input, "coreConfig", true);

        String[] firstFactors = null;
        boolean hasFirstFactors;
        String[] requiredSecondaryFactors = null;
        boolean hasRequiredSecondaryFactors;

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
                        new ThirdPartyConfig(true, null),
                        new PasswordlessConfig(true),
                        null,
                        null, new JsonObject()
                );
            } else {
                tenantConfig = new TenantConfig(
                        targetTenantIdentifier,
                        new EmailPasswordConfig(false),
                        new ThirdPartyConfig(false, null),
                        new PasswordlessConfig(false),
                        null, null, new JsonObject()
                );
            }
            createdNew = true;
        }

        tenantConfig = this.applyTenantUpdates(
                tenantConfig,
                emailPasswordEnabled, thirdPartyEnabled, passwordlessEnabled,
                hasFirstFactors, firstFactors, hasRequiredSecondaryFactors, requiredSecondaryFactors,
                coreConfig);

        // Validate firstFactors and requiredSecondaryFactors
        {
            Set<String> disallowedFactors = new HashSet<>();
            Map<String, String> factorIdToRecipeName = new HashMap<>();
            if (!tenantConfig.emailPasswordConfig.enabled) {
                disallowedFactors.add("emailpassword");

                factorIdToRecipeName.put("emailpassword", "emailPassword");
            }
            if (!tenantConfig.passwordlessConfig.enabled) {
                disallowedFactors.add("otp-email");
                disallowedFactors.add("otp-phone");
                disallowedFactors.add("link-email");
                disallowedFactors.add("link-phone");

                factorIdToRecipeName.put("otp-email", "passwordless");
                factorIdToRecipeName.put("otp-phone", "passwordless");
                factorIdToRecipeName.put("link-email", "passwordless");
                factorIdToRecipeName.put("link-phone", "passwordless");
            }
            if (!tenantConfig.thirdPartyConfig.enabled) {
                disallowedFactors.add("thirdparty");

                factorIdToRecipeName.put("thirdparty", "thirdParty");
            }

            if (tenantConfig.firstFactors != null) {
                for (String factor : tenantConfig.firstFactors) {
                    if (disallowedFactors.contains(factor)) {
                        throw new InvalidConfigException("firstFactors should not contain '" + factor
                                + "' because " + factorIdToRecipeName.get(factor) + " is disabled for the tenant.");
                    }
                }
            }

            if (tenantConfig.requiredSecondaryFactors != null) {
                for (String factor : tenantConfig.requiredSecondaryFactors) {
                    if (disallowedFactors.contains(factor)) {
                        throw new InvalidConfigException("requiredSecondaryFactors should not contain '" + factor
                                + "' because " + factorIdToRecipeName.get(factor) + " is disabled for the tenant.");
                    }
                }
            }
        }

        this.createOrUpdate(req, sourceTenantIdentifier, tenantConfig);

        Multitenancy.addNewOrUpdateAppOrTenant(main, tenantConfig, shouldProtectProtectedConfig(req), false, true);
        JsonObject result = new JsonObject();
        result.addProperty("status", "OK");
        result.addProperty("createdNew", createdNew);
        super.sendJsonResponse(200, result, resp);
    }

    private void handle3_0(HttpServletRequest req, TenantIdentifier sourceTenantIdentifier,
                           TenantIdentifier targetTenantIdentifier, JsonObject input, HttpServletResponse resp)
            throws ServletException, IOException, InvalidProviderConfigException, StorageQueryException,
            FeatureNotEnabledException, TenantOrAppNotFoundException, InvalidConfigException,
            CannotModifyBaseConfigException, BadPermissionException {

        Boolean emailPasswordEnabled = InputParser.parseBooleanOrThrowError(input, "emailPasswordEnabled", true);
        Boolean thirdPartyEnabled = InputParser.parseBooleanOrThrowError(input, "thirdPartyEnabled", true);
        Boolean passwordlessEnabled = InputParser.parseBooleanOrThrowError(input, "passwordlessEnabled", true);
        JsonObject coreConfig = InputParser.parseJsonObjectOrThrowError(input, "coreConfig", true);

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
                        new ThirdPartyConfig(true, null),
                        new PasswordlessConfig(true),
                        null,
                        null, new JsonObject()
                );
            } else {
                tenantConfig = new TenantConfig(
                        targetTenantIdentifier,
                        new EmailPasswordConfig(false),
                        new ThirdPartyConfig(false, null),
                        new PasswordlessConfig(false),
                        null, null, new JsonObject()
                );
            }
            createdNew = true;
        }

        tenantConfig = this.applyTenantUpdates(
                tenantConfig,
                emailPasswordEnabled, thirdPartyEnabled, passwordlessEnabled,
                false, null, false, null,
                coreConfig);
        this.createOrUpdate(req, sourceTenantIdentifier, tenantConfig);

        JsonObject result = new JsonObject();
        result.addProperty("status", "OK");
        result.addProperty("createdNew", createdNew);
        super.sendJsonResponse(200, result, resp);
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
