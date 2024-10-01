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
import io.supertokens.pluginInterface.multitenancy.TenantConfig;
import io.supertokens.thirdparty.InvalidProviderConfigException;
import io.supertokens.utils.SemVer;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.TestOnly;

import java.io.IOException;
import java.util.*;

public abstract class BaseCreateOrUpdate extends WebserverAPI {

    public BaseCreateOrUpdate(Main main) {
        super(main, RECIPE_ID.MULTITENANCY.toString());
    }

    protected void handle(HttpServletRequest req, HttpServletResponse resp, TenantIdentifier sourceTenantIdentifier,
                          TenantIdentifier targetTenantIdentifier,
                          JsonObject input, boolean isV2)
            throws ServletException, IOException {

        try {
            // Try fetching the existing tenant config

            TenantConfig tenantConfig = Multitenancy.getTenantInfo(main,
                    new TenantIdentifier(targetTenantIdentifier.getConnectionUriDomain(),
                            targetTenantIdentifier.getAppId(),
                            targetTenantIdentifier.getTenantId()));

            boolean createdNew = false;
            if (tenantConfig == null) {
                // tenant config does not exist, so this would be a create operation
                tenantConfig = createBaseConfigForVersion(getVersionFromRequest(req), targetTenantIdentifier, isV2);

                createdNew = true;
            }

            { // common to all versions
                CoreConfig baseConfig = Config.getBaseConfig(main);
                if (baseConfig.getSuperTokensLoadOnlyCUD() != null) {
                    if (!(targetTenantIdentifier.getConnectionUriDomain()
                            .equals(TenantIdentifier.DEFAULT_CONNECTION_URI) ||
                            targetTenantIdentifier.getConnectionUriDomain()
                                    .equals(baseConfig.getSuperTokensLoadOnlyCUD()))) {
                        throw new ServletException(
                                new BadRequestException("Creation of connection uri domain or app or " +
                                        "tenant is disallowed"));
                    }
                }

                JsonObject coreConfig = InputParser.parseJsonObjectOrThrowError(input, "coreConfig", true);

                if (coreConfig != null) {
                    coreConfig = mergeConfig(tenantConfig.coreConfig, coreConfig);
                    tenantConfig = new TenantConfig(
                            tenantConfig.tenantIdentifier,
                            tenantConfig.emailPasswordConfig,
                            tenantConfig.thirdPartyConfig,
                            tenantConfig.passwordlessConfig,
                            tenantConfig.firstFactors,
                            tenantConfig.requiredSecondaryFactors,
                            coreConfig
                    );
                }
            }

            // Apply updates based on CDI version
            tenantConfig = applyTenantUpdates(tenantConfig, getVersionFromRequest(req), isV2, input);

            // Write tenant config to db
            createOrUpdate(req, sourceTenantIdentifier, tenantConfig);

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

    @TestOnly
    public static TenantConfig createBaseConfigForVersionForTest(SemVer version, TenantIdentifier tenantIdentifier,
                                                                 boolean isV2) {
        return createBaseConfigForVersion(version, tenantIdentifier, isV2);
    }

    private static TenantConfig createBaseConfigForVersion(SemVer version, TenantIdentifier tenantIdentifier,
                                                           boolean isV2) {
        if (!isV2) {
            // Deprecated API implementations
            if (version.greaterThanOrEqualTo(SemVer.v5_0)) {
                // >= 5.0
                if (tenantIdentifier.getTenantId().equals(TenantIdentifier.DEFAULT_TENANT_ID)) {
                    // create public tenant
                    return new TenantConfig(
                            tenantIdentifier,
                            new EmailPasswordConfig(true),
                            new ThirdPartyConfig(true, null),
                            new PasswordlessConfig(true),
                            null,
                            null, new JsonObject()
                    );
                } else {
                    // create non-public tenant
                    return new TenantConfig(
                            tenantIdentifier,
                            new EmailPasswordConfig(false),
                            new ThirdPartyConfig(false, null),
                            new PasswordlessConfig(false),
                            null, null, new JsonObject()
                    );
                }
            } else {
                // < 5.0
                if (tenantIdentifier.getTenantId().equals(TenantIdentifier.DEFAULT_TENANT_ID)) {
                    // create public tenant
                    return new TenantConfig(
                            tenantIdentifier,
                            new EmailPasswordConfig(true),
                            new ThirdPartyConfig(true, null),
                            new PasswordlessConfig(true),
                            null,
                            null, new JsonObject()
                    );
                } else {
                    // create non-public tenant
                    return new TenantConfig(
                            tenantIdentifier,
                            new EmailPasswordConfig(false),
                            new ThirdPartyConfig(false, null),
                            new PasswordlessConfig(false),
                            new String[]{}, null, new JsonObject()
                    );
                }
            }
        } else {
            // V2 API implementations
            if (tenantIdentifier.getTenantId().equals(TenantIdentifier.DEFAULT_TENANT_ID)) {
                // create public tenant
                return new TenantConfig(
                        tenantIdentifier,
                        new EmailPasswordConfig(true),
                        new ThirdPartyConfig(true, null),
                        new PasswordlessConfig(true),
                        null,
                        null, new JsonObject()
                );
            } else {
                // create non-public tenant
                return new TenantConfig(
                        tenantIdentifier,
                        new EmailPasswordConfig(true),
                        new ThirdPartyConfig(true, new ThirdPartyConfig.Provider[]{}),
                        new PasswordlessConfig(true),
                        new String[]{}, null, new JsonObject()
                );
            }
        }
    }

    @TestOnly
    public static TenantConfig applyTenantUpdatesForTest(
            TenantConfig tenantConfig,
            SemVer version,
            boolean isV2,
            JsonObject input) throws ServletException, InvalidConfigException {
        return applyTenantUpdates(tenantConfig, version, isV2, input);
    }

    private static TenantConfig applyTenantUpdates(
            TenantConfig tenantConfig,
            SemVer version,
            boolean isV2,
            JsonObject input) throws ServletException, InvalidConfigException {

        if (!isV2) {
            // Deprecated API implementations
            if (version.greaterThanOrEqualTo(SemVer.v5_0)) {
                // >= 5.0
                tenantConfig = applyTenantUpdates_5_0(tenantConfig, input);
            } else {
                // < 5.0
                tenantConfig = applyTenantUpdates_3_0(tenantConfig, input);
            }
        } else {
            // V2 API implementations
            tenantConfig = applyV2TenantUpdates_5_1(tenantConfig, input);
        }

        return tenantConfig;
    }

    private static TenantConfig applyTenantUpdates_3_0(TenantConfig tenantConfig, JsonObject input)
            throws ServletException {
        Boolean emailPasswordEnabled = InputParser.parseBooleanOrThrowError(input, "emailPasswordEnabled", true);
        Boolean thirdPartyEnabled = InputParser.parseBooleanOrThrowError(input, "thirdPartyEnabled", true);
        Boolean passwordlessEnabled = InputParser.parseBooleanOrThrowError(input, "passwordlessEnabled", true);

        Set<String> firstFactors =
                tenantConfig.firstFactors == null ? null : new HashSet<>(Set.of(tenantConfig.firstFactors));

        // Enabling recipes
        if (Boolean.TRUE.equals(emailPasswordEnabled) &&
                !tenantConfig.emailPasswordConfig.isEnabledInLesserThanOrEqualTo4_0(tenantConfig.firstFactors)) {
            tenantConfig = new TenantConfig(
                    tenantConfig.tenantIdentifier,
                    new EmailPasswordConfig(true),
                    tenantConfig.thirdPartyConfig,
                    tenantConfig.passwordlessConfig,
                    tenantConfig.firstFactors,
                    tenantConfig.requiredSecondaryFactors,
                    tenantConfig.coreConfig
            );

            if (firstFactors != null) {
                firstFactors.add("emailpassword");
            }
        }
        if (Boolean.TRUE.equals(thirdPartyEnabled) &&
                !tenantConfig.thirdPartyConfig.isEnabledInLesserThanOrEqualTo4_0(tenantConfig.firstFactors)) {
            tenantConfig = new TenantConfig(
                    tenantConfig.tenantIdentifier,
                    tenantConfig.emailPasswordConfig,
                    new ThirdPartyConfig(true, tenantConfig.thirdPartyConfig.providers),
                    tenantConfig.passwordlessConfig,
                    tenantConfig.firstFactors,
                    tenantConfig.requiredSecondaryFactors,
                    tenantConfig.coreConfig
            );

            if (firstFactors != null) {
                firstFactors.add("thirdparty");
            }
        }
        if (Boolean.TRUE.equals(passwordlessEnabled) &&
                !tenantConfig.passwordlessConfig.isEnabledInLesserThanOrEqualTo4_0(tenantConfig.firstFactors)) {
            tenantConfig = new TenantConfig(
                    tenantConfig.tenantIdentifier,
                    tenantConfig.emailPasswordConfig,
                    tenantConfig.thirdPartyConfig,
                    new PasswordlessConfig(true),
                    tenantConfig.firstFactors,
                    tenantConfig.requiredSecondaryFactors,
                    tenantConfig.coreConfig
            );

            if (firstFactors != null) {
                firstFactors.add("otp-phone");
                firstFactors.add("otp-email");
                firstFactors.add("link-phone");
                firstFactors.add("link-email");
            }
        }

        // Disabling recipes
        if (firstFactors == null && (
                tenantConfig.emailPasswordConfig.enabled == false ||
                        tenantConfig.thirdPartyConfig.enabled == false ||
                        tenantConfig.passwordlessConfig.enabled == false ||
                        Boolean.FALSE.equals(emailPasswordEnabled) ||
                        Boolean.FALSE.equals(thirdPartyEnabled) ||
                        Boolean.FALSE.equals(passwordlessEnabled)
        )) {
            // since the boolean states corresponds to the first factors
            // setting it to all factors now, and later we will remove the disabled ones
            firstFactors = new HashSet<>(
                    Set.of("emailpassword", "thirdparty", "otp-phone", "otp-email", "link-phone", "link-email"));
        }

        if (tenantConfig.emailPasswordConfig.enabled == false || Boolean.FALSE.equals(emailPasswordEnabled)) {
//            tenantConfig = new TenantConfig(
//                    tenantConfig.tenantIdentifier,
//                    new EmailPasswordConfig(false),
//                    tenantConfig.thirdPartyConfig,
//                    tenantConfig.passwordlessConfig,
//                    tenantConfig.firstFactors,
//                    tenantConfig.requiredSecondaryFactors,
//                    tenantConfig.coreConfig
//            );

            firstFactors.remove("emailpassword");
        }

        if (tenantConfig.thirdPartyConfig.enabled == false || Boolean.FALSE.equals(thirdPartyEnabled)) {
//            tenantConfig = new TenantConfig(
//                    tenantConfig.tenantIdentifier,
//                    tenantConfig.emailPasswordConfig,
//                    new ThirdPartyConfig(tenantConfig.thirdPartyConfig.enabled, tenantConfig.thirdPartyConfig
//                    .providers),
//                    tenantConfig.passwordlessConfig,
//                    tenantConfig.firstFactors,
//                    tenantConfig.requiredSecondaryFactors,
//                    tenantConfig.coreConfig
//            );

            firstFactors.remove("thirdparty");
        }

        if (tenantConfig.passwordlessConfig.enabled == false || Boolean.FALSE.equals(passwordlessEnabled)) {
//            tenantConfig = new TenantConfig(
//                    tenantConfig.tenantIdentifier,
//                    tenantConfig.emailPasswordConfig,
//                    tenantConfig.thirdPartyConfig,
//                    new PasswordlessConfig(false),
//                    tenantConfig.firstFactors,
//                    tenantConfig.requiredSecondaryFactors,
//                    tenantConfig.coreConfig
//            );

            firstFactors.remove("otp-phone");
            firstFactors.remove("otp-email");
            firstFactors.remove("link-phone");
            firstFactors.remove("link-email");
        }

        // finally, set the updated first factors
        tenantConfig = new TenantConfig(
                tenantConfig.tenantIdentifier,
                tenantConfig.emailPasswordConfig,
                tenantConfig.thirdPartyConfig,
                tenantConfig.passwordlessConfig,
                firstFactors == null ? null : firstFactors.toArray(new String[0]),
                tenantConfig.requiredSecondaryFactors,
                tenantConfig.coreConfig
        );

        return tenantConfig;
    }

    private static TenantConfig applyTenantUpdates_5_0(TenantConfig tenantConfig, JsonObject input)
            throws ServletException, InvalidConfigException {
        Boolean emailPasswordEnabled = InputParser.parseBooleanOrThrowError(input, "emailPasswordEnabled", true);
        Boolean thirdPartyEnabled = InputParser.parseBooleanOrThrowError(input, "thirdPartyEnabled", true);
        Boolean passwordlessEnabled = InputParser.parseBooleanOrThrowError(input, "passwordlessEnabled", true);

        // first factors
        String[] firstFactors = null;
        boolean hasFirstFactors;
        hasFirstFactors = input.has("firstFactors");
        if (hasFirstFactors && !input.get("firstFactors").isJsonNull()) {
            JsonArray firstFactorsArr = InputParser.parseArrayOrThrowError(input, "firstFactors", true);
            firstFactors = new String[firstFactorsArr.size()];
            for (int i = 0; i < firstFactors.length; i++) {
                firstFactors[i] = InputParser.parseStringFromElementOrThrowError(firstFactorsArr.get(i), "firstFactors",
                        false);
            }
            if (firstFactors.length != new HashSet<>(Arrays.asList(firstFactors)).size()) {
                throw new ServletException(
                        new BadRequestException("firstFactors input should not contain duplicate values"));
            }
        }

        if (hasFirstFactors && firstFactors != null && firstFactors.length == 0) {
            throw new ServletException(new BadRequestException(
                    "firstFactors cannot be empty. Set null instead to remove all first factors."));
        }

        // required secondary factors
        String[] requiredSecondaryFactors = null;
        boolean hasRequiredSecondaryFactors;
        hasRequiredSecondaryFactors = input.has("requiredSecondaryFactors");
        if (hasRequiredSecondaryFactors && !input.get("requiredSecondaryFactors").isJsonNull()) {
            JsonArray requiredSecondaryFactorsArr = InputParser.parseArrayOrThrowError(input,
                    "requiredSecondaryFactors", true);
            requiredSecondaryFactors = new String[requiredSecondaryFactorsArr.size()];
            for (int i = 0; i < requiredSecondaryFactors.length; i++) {
                requiredSecondaryFactors[i] = InputParser.parseStringFromElementOrThrowError(
                        requiredSecondaryFactorsArr.get(i), "requiredSecondaryFactors", false);
            }
            if (requiredSecondaryFactors.length != new HashSet<>(Arrays.asList(requiredSecondaryFactors)).size()) {
                throw new ServletException(
                        new BadRequestException("requiredSecondaryFactors input should not contain duplicate values"));
            }
        }

        if (hasRequiredSecondaryFactors && requiredSecondaryFactors != null && requiredSecondaryFactors.length == 0) {
            throw new ServletException(new BadRequestException(
                    "requiredSecondaryFactors cannot be empty. Set null instead to remove all required secondary " +
                            "factors."));
        }

        // check for conflicting updates
        if (Boolean.FALSE.equals(emailPasswordEnabled)) {
            if (hasFirstFactors && firstFactors != null && List.of(firstFactors).contains("emailpassword")) {
                throw new InvalidConfigException(
                        "firstFactors should not contain 'emailpassword' because emailPassword is disabled for the " +
                                "tenant.");
            }
            if (hasRequiredSecondaryFactors && requiredSecondaryFactors != null &&
                    List.of(requiredSecondaryFactors).contains("emailpassword")) {
                throw new InvalidConfigException(
                        "requiredSecondaryFactors should not contain 'emailpassword' because emailPassword is " +
                                "disabled for the tenant.");
            }
        }

        if (Boolean.FALSE.equals(thirdPartyEnabled)) {
            if (hasFirstFactors && firstFactors != null && List.of(firstFactors).contains("thirdparty")) {
                throw new InvalidConfigException(
                        "firstFactors should not contain 'thirdparty' because thirdParty is disabled for the tenant.");
            }
            if (hasRequiredSecondaryFactors && requiredSecondaryFactors != null &&
                    List.of(requiredSecondaryFactors).contains("thirdparty")) {
                throw new InvalidConfigException(
                        "requiredSecondaryFactors should not contain 'thirdparty' because thirdParty is disabled for " +
                                "the tenant.");
            }
        }

        if (Boolean.FALSE.equals(passwordlessEnabled)) {
            if (hasFirstFactors && firstFactors != null && List.of(firstFactors).contains("otp-phone")) {
                throw new InvalidConfigException(
                        "firstFactors should not contain 'otp-phone' because passwordless is disabled for the tenant.");
            }
            if (hasFirstFactors && firstFactors != null && List.of(firstFactors).contains("otp-email")) {
                throw new InvalidConfigException(
                        "firstFactors should not contain 'otp-email' because passwordless is disabled for the tenant.");
            }
            if (hasFirstFactors && firstFactors != null && List.of(firstFactors).contains("link-phone")) {
                throw new InvalidConfigException(
                        "firstFactors should not contain 'link-phone' because passwordless is disabled for the tenant" +
                                ".");
            }
            if (hasFirstFactors && firstFactors != null && List.of(firstFactors).contains("link-email")) {
                throw new InvalidConfigException(
                        "firstFactors should not contain 'link-email' because passwordless is disabled for the tenant" +
                                ".");
            }
            if (hasRequiredSecondaryFactors && requiredSecondaryFactors != null &&
                    List.of(requiredSecondaryFactors).contains("otp-phone")) {
                throw new InvalidConfigException(
                        "requiredSecondaryFactors should not contain 'otp-phone' because passwordless is disabled for" +
                                " the tenant.");
            }
            if (hasRequiredSecondaryFactors && requiredSecondaryFactors != null &&
                    List.of(requiredSecondaryFactors).contains("otp-email")) {
                throw new InvalidConfigException(
                        "requiredSecondaryFactors should not contain 'otp-email' because passwordless is disabled for" +
                                " the tenant.");
            }
            if (hasRequiredSecondaryFactors && requiredSecondaryFactors != null &&
                    List.of(requiredSecondaryFactors).contains("link-phone")) {
                throw new InvalidConfigException(
                        "requiredSecondaryFactors should not contain 'link-phone' because passwordless is disabled " +
                                "for the tenant.");
            }
            if (hasRequiredSecondaryFactors && requiredSecondaryFactors != null &&
                    List.of(requiredSecondaryFactors).contains("link-email")) {
                throw new InvalidConfigException(
                        "requiredSecondaryFactors should not contain 'link-email' because passwordless is disabled " +
                                "for the tenant.");
            }
        }

        // All validation done, continuing with updates

        if (Boolean.FALSE.equals(emailPasswordEnabled)) {
            if (hasFirstFactors && firstFactors != null && List.of(firstFactors).contains("emailpassword")) {
                throw new InvalidConfigException(
                        "firstFactors should not contain 'emailpassword' because emailPassword is disabled for the " +
                                "tenant.");
            }
            if (hasRequiredSecondaryFactors && requiredSecondaryFactors != null &&
                    List.of(requiredSecondaryFactors).contains("emailpassword")) {
                throw new InvalidConfigException(
                        "requiredSecondaryFactors should not contain 'emailpassword' because emailPassword is " +
                                "disabled for the tenant.");
            }
        }

        if (Boolean.FALSE.equals(thirdPartyEnabled)) {
            if (hasFirstFactors && firstFactors != null && List.of(firstFactors).contains("thirdparty")) {
                throw new InvalidConfigException(
                        "firstFactors should not contain 'thirdparty' because thirdParty is disabled for the tenant.");
            }
            if (hasRequiredSecondaryFactors && requiredSecondaryFactors != null &&
                    List.of(requiredSecondaryFactors).contains("thirdparty")) {
                throw new InvalidConfigException(
                        "requiredSecondaryFactors should not contain 'thirdparty' because thirdParty is disabled for " +
                                "the tenant.");
            }
        }

        if (Boolean.FALSE.equals(passwordlessEnabled)) {
            if (hasFirstFactors && firstFactors != null && List.of(firstFactors).contains("otp-phone")) {
                throw new InvalidConfigException(
                        "firstFactors should not contain 'otp-phone' because passwordless is disabled for the tenant.");
            }
            if (hasFirstFactors && firstFactors != null && List.of(firstFactors).contains("otp-email")) {
                throw new InvalidConfigException(
                        "firstFactors should not contain 'otp-email' because passwordless is disabled for the tenant.");
            }
            if (hasFirstFactors && firstFactors != null && List.of(firstFactors).contains("link-phone")) {
                throw new InvalidConfigException(
                        "firstFactors should not contain 'link-phone' because passwordless is disabled for the tenant" +
                                ".");
            }
            if (hasFirstFactors && firstFactors != null && List.of(firstFactors).contains("link-email")) {
                throw new InvalidConfigException(
                        "firstFactors should not contain 'link-email' because passwordless is disabled for the tenant" +
                                ".");
            }
            if (hasRequiredSecondaryFactors && requiredSecondaryFactors != null &&
                    List.of(requiredSecondaryFactors).contains("otp-phone")) {
                throw new InvalidConfigException(
                        "requiredSecondaryFactors should not contain 'otp-phone' because passwordless is disabled for" +
                                " the tenant.");
            }
            if (hasRequiredSecondaryFactors && requiredSecondaryFactors != null &&
                    List.of(requiredSecondaryFactors).contains("otp-email")) {
                throw new InvalidConfigException(
                        "requiredSecondaryFactors should not contain 'otp-email' because passwordless is disabled for" +
                                " the tenant.");
            }
            if (hasRequiredSecondaryFactors && requiredSecondaryFactors != null &&
                    List.of(requiredSecondaryFactors).contains("link-phone")) {
                throw new InvalidConfigException(
                        "requiredSecondaryFactors should not contain 'link-phone' because passwordless is disabled " +
                                "for the tenant.");
            }
            if (hasRequiredSecondaryFactors && requiredSecondaryFactors != null &&
                    List.of(requiredSecondaryFactors).contains("link-email")) {
                throw new InvalidConfigException(
                        "requiredSecondaryFactors should not contain 'link-email' because passwordless is disabled " +
                                "for the tenant.");
            }
        }

        List<String> updateKeys = new ArrayList<>();
        List<Object> updateValues = new ArrayList<>();

        if ((Boolean.TRUE.equals(emailPasswordEnabled) &&
                !tenantConfig.emailPasswordConfig.isEnabledIn5_0(tenantConfig.firstFactors)) ||
                (Boolean.TRUE.equals(thirdPartyEnabled) &&
                        !tenantConfig.thirdPartyConfig.isEnabledIn5_0(tenantConfig.firstFactors)) ||
                (Boolean.TRUE.equals(passwordlessEnabled) &&
                        !tenantConfig.passwordlessConfig.isEnabledIn5_0(tenantConfig.firstFactors))
        ) {
            if (tenantConfig.firstFactors != null && tenantConfig.firstFactors.length == 0) {
                tenantConfig = new TenantConfig(
                        tenantConfig.tenantIdentifier,
                        new EmailPasswordConfig(false),
                        new ThirdPartyConfig(false, tenantConfig.thirdPartyConfig.providers),
                        new PasswordlessConfig(false),
                        null,
                        tenantConfig.requiredSecondaryFactors,
                        tenantConfig.coreConfig
                );
            }
        }

        if (Boolean.TRUE.equals(emailPasswordEnabled)) {
            updateKeys.add("emailPasswordEnabled");
            updateValues.add(true);
        }

        if (Boolean.TRUE.equals(thirdPartyEnabled)) {
            updateKeys.add("thirdPartyEnabled");
            updateValues.add(true);
        }

        if (Boolean.TRUE.equals(passwordlessEnabled)) {
            updateKeys.add("passwordlessEnabled");
            updateValues.add(true);
        }

        if (Boolean.FALSE.equals(emailPasswordEnabled)) {
            updateKeys.add("emailPasswordEnabled");
            updateValues.add(false);
        }

        if (Boolean.FALSE.equals(thirdPartyEnabled)) {
            updateKeys.add("thirdPartyEnabled");
            updateValues.add(false);
        }

        if (Boolean.FALSE.equals(passwordlessEnabled)) {
            updateKeys.add("passwordlessEnabled");
            updateValues.add(false);
        }

        if (Boolean.FALSE.equals(emailPasswordEnabled) || Boolean.FALSE.equals(thirdPartyEnabled) ||
                Boolean.FALSE.equals(passwordlessEnabled)) {
            if (tenantConfig.firstFactors != null && !hasFirstFactors) {
                Set<String> firstFactorsSet = new HashSet<>(Set.of(tenantConfig.firstFactors));

                if (Boolean.FALSE.equals(emailPasswordEnabled)) {
                    firstFactorsSet.remove("emailpassword");
                }

                if (Boolean.FALSE.equals(thirdPartyEnabled)) {
                    firstFactorsSet.remove("thirdparty");
                }

                if (Boolean.FALSE.equals(passwordlessEnabled)) {
                    firstFactorsSet.remove("otp-phone");
                    firstFactorsSet.remove("otp-email");
                    firstFactorsSet.remove("link-phone");
                    firstFactorsSet.remove("link-email");
                }

                if (!Set.of(tenantConfig.firstFactors).equals(firstFactorsSet)) {
                    updateKeys.add("firstFactors");
                    updateValues.add(firstFactorsSet.toArray(new String[0]));
                }
            }

            if (!hasRequiredSecondaryFactors && tenantConfig.requiredSecondaryFactors != null) {
                Set<String> requiredSecondaryFactorsSet = new HashSet<>(Set.of(tenantConfig.requiredSecondaryFactors));

                if (Boolean.FALSE.equals(emailPasswordEnabled)) {
                    requiredSecondaryFactorsSet.remove("emailpassword");
                }

                if (Boolean.FALSE.equals(thirdPartyEnabled)) {
                    requiredSecondaryFactorsSet.remove("thirdparty");
                }

                if (Boolean.FALSE.equals(passwordlessEnabled)) {
                    requiredSecondaryFactorsSet.remove("otp-phone");
                    requiredSecondaryFactorsSet.remove("otp-email");
                    requiredSecondaryFactorsSet.remove("link-phone");
                    requiredSecondaryFactorsSet.remove("link-email");
                }

                if (!Set.of(tenantConfig.requiredSecondaryFactors).equals(requiredSecondaryFactorsSet)) {
                    updateKeys.add("requiredSecondaryFactors");
                    updateValues.add(requiredSecondaryFactorsSet.toArray(new String[0]));
                }
            }
        }

        if (hasFirstFactors) {
            if (firstFactors == null) {
                updateKeys.add("firstFactors");
                updateValues.add(null);
            } else {
                updateKeys.add("firstFactors");
                updateValues.add(firstFactors);

                for (String factor : firstFactors) {
                    if (factor.equals("emailpassword") &&
                            !tenantConfig.emailPasswordConfig.isEnabledIn5_0(tenantConfig.firstFactors)) {
                        updateKeys.add("emailPasswordEnabled");
                        updateValues.add(true);
                    }
                    if (factor.equals("thirdparty") &&
                            !tenantConfig.thirdPartyConfig.isEnabledIn5_0(tenantConfig.firstFactors)) {
                        updateKeys.add("thirdPartyEnabled");
                        updateValues.add(true);
                    }
                    if ((factor.equals("otp-phone") || factor.equals("otp-email") || factor.equals("link-phone") ||
                            factor.equals("link-email")) &&
                            !tenantConfig.passwordlessConfig.isEnabledIn5_0(tenantConfig.firstFactors)) {
                        updateKeys.add("passwordlessEnabled");
                        updateValues.add(true);
                    }
                }
            }
        }

        if (hasRequiredSecondaryFactors) {
            if (requiredSecondaryFactors == null) {
                updateKeys.add("requiredSecondaryFactors");
                updateValues.add(null);
            } else {
                updateKeys.add("requiredSecondaryFactors");
                updateValues.add(requiredSecondaryFactors);

                for (String factor : requiredSecondaryFactors) {
                    boolean enablingSomeRecipe = false;
                    if (factor.equals("emailpassword") &&
                            !tenantConfig.emailPasswordConfig.isEnabledIn5_0(tenantConfig.firstFactors)) {
                        updateKeys.add("emailPasswordEnabled");
                        updateValues.add(true);
                        if (!Boolean.TRUE.equals(emailPasswordEnabled)) {
                            enablingSomeRecipe = true;
                        }
                    }
                    if (factor.equals("thirdparty") &&
                            !tenantConfig.thirdPartyConfig.isEnabledIn5_0(tenantConfig.firstFactors)) {
                        updateKeys.add("thirdPartyEnabled");
                        updateValues.add(true);
                        if (!Boolean.TRUE.equals(thirdPartyEnabled)) {
                            enablingSomeRecipe = true;
                        }
                    }
                    if ((factor.equals("otp-phone") || factor.equals("otp-email") || factor.equals("link-phone") ||
                            factor.equals("link-email")) &&
                            !tenantConfig.passwordlessConfig.isEnabledIn5_0(tenantConfig.firstFactors)) {
                        updateKeys.add("passwordlessEnabled");
                        updateValues.add(true);
                        if (!Boolean.TRUE.equals(passwordlessEnabled)) {
                            enablingSomeRecipe = true;
                        }
                    }

                    if (!hasFirstFactors && enablingSomeRecipe == true && tenantConfig.firstFactors == null) {
                        Set<String> firstFactorsSet = new HashSet<>();

                        if ((tenantConfig.emailPasswordConfig.enabled || Boolean.TRUE.equals(emailPasswordEnabled)) &&
                                !Boolean.FALSE.equals(emailPasswordEnabled)) {
                            firstFactorsSet.add("emailpassword");
                        }
                        if ((tenantConfig.thirdPartyConfig.enabled || Boolean.TRUE.equals(thirdPartyEnabled)) &&
                                !Boolean.FALSE.equals(thirdPartyEnabled)) {
                            firstFactorsSet.add("thirdparty");
                        }
                        if ((tenantConfig.passwordlessConfig.enabled || Boolean.TRUE.equals(passwordlessEnabled)) &&
                                !Boolean.FALSE.equals(passwordlessEnabled)) {
                            firstFactorsSet.add("otp-phone");
                            firstFactorsSet.add("otp-email");
                            firstFactorsSet.add("link-phone");
                            firstFactorsSet.add("link-email");
                        }

                        if (firstFactorsSet.size() < 6) {
                            updateKeys.add("firstFactors");
                            updateValues.add(firstFactorsSet.toArray(new String[0]));
                        }
                    }
                }
            }
        }

        // Check for conflicting updates
        Map<String, Object> finalUpdates = new HashMap<>();
        for (int idx = 0; idx < updateKeys.size(); idx++) {
            String key = updateKeys.get(idx);
            Object value = updateValues.get(idx);

            if (!finalUpdates.containsKey(key)) {
                finalUpdates.put(key, value);
                continue;
            }

            if (key.equals("firstFactors") || key.equals("requiredSecondaryFactors")) {
                if (value == null || finalUpdates.get(key) == null) {
                    if (Objects.equals(value, finalUpdates.get(key))) {
                        continue;
                    } else {
                        throw new IllegalStateException("should never come here");
                    }
                }
                Set<String> existingSet = new HashSet<>(Arrays.asList((String[]) finalUpdates.get(key)));
                Set<String> newSet = new HashSet<>(Arrays.asList((String[]) value));

                if (existingSet.equals(newSet)) {
                    continue;
                }

                throw new IllegalStateException("should never come here");
            } else {
                if (!finalUpdates.get(key).equals(value)) {
                    throw new IllegalStateException("should never come here");
                }
            }
        }

        // Apply the updates
        for (Map.Entry keyValue : finalUpdates.entrySet()) {
            if (keyValue.getKey().equals("emailPasswordEnabled")) {
                tenantConfig = new TenantConfig(
                        tenantConfig.tenantIdentifier,
                        new EmailPasswordConfig((Boolean) keyValue.getValue()),
                        tenantConfig.thirdPartyConfig,
                        tenantConfig.passwordlessConfig,
                        tenantConfig.firstFactors,
                        tenantConfig.requiredSecondaryFactors,
                        tenantConfig.coreConfig
                );
            } else if (keyValue.getKey().equals("thirdPartyEnabled")) {
                tenantConfig = new TenantConfig(
                        tenantConfig.tenantIdentifier,
                        tenantConfig.emailPasswordConfig,
                        new ThirdPartyConfig((Boolean) keyValue.getValue(), tenantConfig.thirdPartyConfig.providers),
                        tenantConfig.passwordlessConfig,
                        tenantConfig.firstFactors,
                        tenantConfig.requiredSecondaryFactors,
                        tenantConfig.coreConfig
                );
            } else if (keyValue.getKey().equals("passwordlessEnabled")) {
                tenantConfig = new TenantConfig(
                        tenantConfig.tenantIdentifier,
                        tenantConfig.emailPasswordConfig,
                        tenantConfig.thirdPartyConfig,
                        new PasswordlessConfig((Boolean) keyValue.getValue()),
                        tenantConfig.firstFactors,
                        tenantConfig.requiredSecondaryFactors,
                        tenantConfig.coreConfig
                );
            } else if (keyValue.getKey().equals("firstFactors")) {
                tenantConfig = new TenantConfig(
                        tenantConfig.tenantIdentifier,
                        tenantConfig.emailPasswordConfig,
                        tenantConfig.thirdPartyConfig,
                        tenantConfig.passwordlessConfig,
                        (String[]) keyValue.getValue(),
                        tenantConfig.requiredSecondaryFactors,
                        tenantConfig.coreConfig
                );
            } else if (keyValue.getKey().equals("requiredSecondaryFactors")) {
                tenantConfig = new TenantConfig(
                        tenantConfig.tenantIdentifier,
                        tenantConfig.emailPasswordConfig,
                        tenantConfig.thirdPartyConfig,
                        tenantConfig.passwordlessConfig,
                        tenantConfig.firstFactors,
                        (String[]) keyValue.getValue(),
                        tenantConfig.coreConfig
                );
            }
        }

        // if firstFactors or requiredSecondaryFactors are set, make sure
        // appropriate recipes are enabled
        if (tenantConfig.firstFactors != null) {
            for (String factor : tenantConfig.firstFactors) {
                if (factor.equals("emailpassword")) {
                    tenantConfig = new TenantConfig(
                            tenantConfig.tenantIdentifier,
                            new EmailPasswordConfig(true),
                            tenantConfig.thirdPartyConfig,
                            tenantConfig.passwordlessConfig,
                            tenantConfig.firstFactors,
                            tenantConfig.requiredSecondaryFactors,
                            tenantConfig.coreConfig
                    );
                }
                if (factor.equals("thirdparty")) {
                    tenantConfig = new TenantConfig(
                            tenantConfig.tenantIdentifier,
                            tenantConfig.emailPasswordConfig,
                            new ThirdPartyConfig(true, tenantConfig.thirdPartyConfig.providers),
                            tenantConfig.passwordlessConfig,
                            tenantConfig.firstFactors,
                            tenantConfig.requiredSecondaryFactors,
                            tenantConfig.coreConfig
                    );
                }
                if (factor.equals("otp-phone") || factor.equals("otp-email") || factor.equals("link-phone") ||
                        factor.equals("link-email")) {
                    tenantConfig = new TenantConfig(
                            tenantConfig.tenantIdentifier,
                            tenantConfig.emailPasswordConfig,
                            tenantConfig.thirdPartyConfig,
                            new PasswordlessConfig(true),
                            tenantConfig.firstFactors,
                            tenantConfig.requiredSecondaryFactors,
                            tenantConfig.coreConfig
                    );
                }
            }
        }
        if (tenantConfig.requiredSecondaryFactors != null) {
            for (String factor : tenantConfig.requiredSecondaryFactors) {
                if (factor.equals("emailpassword")) {
                    tenantConfig = new TenantConfig(
                            tenantConfig.tenantIdentifier,
                            new EmailPasswordConfig(true),
                            tenantConfig.thirdPartyConfig,
                            tenantConfig.passwordlessConfig,
                            tenantConfig.firstFactors,
                            tenantConfig.requiredSecondaryFactors,
                            tenantConfig.coreConfig
                    );
                }
                if (factor.equals("thirdparty")) {
                    tenantConfig = new TenantConfig(
                            tenantConfig.tenantIdentifier,
                            tenantConfig.emailPasswordConfig,
                            new ThirdPartyConfig(true, tenantConfig.thirdPartyConfig.providers),
                            tenantConfig.passwordlessConfig,
                            tenantConfig.firstFactors,
                            tenantConfig.requiredSecondaryFactors,
                            tenantConfig.coreConfig
                    );
                }
                if (factor.equals("otp-phone") || factor.equals("otp-email") || factor.equals("link-phone") ||
                        factor.equals("link-email")) {
                    tenantConfig = new TenantConfig(
                            tenantConfig.tenantIdentifier,
                            tenantConfig.emailPasswordConfig,
                            tenantConfig.thirdPartyConfig,
                            new PasswordlessConfig(true),
                            tenantConfig.firstFactors,
                            tenantConfig.requiredSecondaryFactors,
                            tenantConfig.coreConfig
                    );
                }
            }
        }

        return tenantConfig;
    }

    private static TenantConfig applyV2TenantUpdates_5_1(TenantConfig tenantConfig, JsonObject input)
            throws ServletException {
        if (input.has("emailPasswordEnabled")) {
            throw new ServletException(
                    new BadRequestException("emailPasswordEnabled is not a valid input for this API"));
        }
        if (input.has("thirdPartyEnabled")) {
            throw new ServletException(new BadRequestException("thirdParty is not a valid input for this API"));
        }
        if (input.has("passwordlessEnabled")) {
            throw new ServletException(new BadRequestException("passwordless is not a valid input for this API"));
        }

        // first factors
        String[] firstFactors = null;
        boolean hasFirstFactors;
        hasFirstFactors = input.has("firstFactors");
        if (hasFirstFactors && !input.get("firstFactors").isJsonNull()) {
            JsonArray firstFactorsArr = InputParser.parseArrayOrThrowError(input, "firstFactors", true);
            firstFactors = new String[firstFactorsArr.size()];
            for (int i = 0; i < firstFactors.length; i++) {
                firstFactors[i] = InputParser.parseStringFromElementOrThrowError(firstFactorsArr.get(i), "firstFactors",
                        false);
            }
            if (firstFactors.length != new HashSet<>(Arrays.asList(firstFactors)).size()) {
                throw new ServletException(
                        new BadRequestException("firstFactors input should not contain duplicate values"));
            }
        }

        // required secondary factors
        String[] requiredSecondaryFactors = null;
        boolean hasRequiredSecondaryFactors;
        hasRequiredSecondaryFactors = input.has("requiredSecondaryFactors");
        if (hasRequiredSecondaryFactors && !input.get("requiredSecondaryFactors").isJsonNull()) {
            JsonArray requiredSecondaryFactorsArr = InputParser.parseArrayOrThrowError(input,
                    "requiredSecondaryFactors", true);
            requiredSecondaryFactors = new String[requiredSecondaryFactorsArr.size()];
            for (int i = 0; i < requiredSecondaryFactors.length; i++) {
                requiredSecondaryFactors[i] = InputParser.parseStringFromElementOrThrowError(
                        requiredSecondaryFactorsArr.get(i), "requiredSecondaryFactors", false);
            }
            if (requiredSecondaryFactors.length != new HashSet<>(Arrays.asList(requiredSecondaryFactors)).size()) {
                throw new ServletException(
                        new BadRequestException("requiredSecondaryFactors input should not contain duplicate values"));
            }
        }

        if (hasFirstFactors || hasRequiredSecondaryFactors) {
            // in v2, we like to have all booleans enabled. So, if the firstFactors is null, we apply the existing
            // boolean states on to the first factors
            if (tenantConfig.firstFactors == null) {
                Set<String> firstFactorsSet = new HashSet<>();
                if (tenantConfig.emailPasswordConfig.enabled) {
                    firstFactorsSet.add("emailpassword");
                }
                if (tenantConfig.thirdPartyConfig.enabled) {
                    firstFactorsSet.add("thirdparty");
                }
                if (tenantConfig.passwordlessConfig.enabled) {
                    firstFactorsSet.add("otp-phone");
                    firstFactorsSet.add("otp-email");
                    firstFactorsSet.add("link-phone");
                    firstFactorsSet.add("link-email");
                }
                if (firstFactorsSet.size() == 6) {
                    firstFactorsSet = null;
                }
                tenantConfig = new TenantConfig(
                        tenantConfig.tenantIdentifier,
                        new EmailPasswordConfig(true),
                        new ThirdPartyConfig(true, tenantConfig.thirdPartyConfig.providers),
                        new PasswordlessConfig(true),
                        firstFactorsSet == null ? null : firstFactorsSet.toArray(new String[0]),
                        tenantConfig.requiredSecondaryFactors,
                        tenantConfig.coreConfig
                );
            } else {
                tenantConfig = new TenantConfig(
                        tenantConfig.tenantIdentifier,
                        new EmailPasswordConfig(true),
                        new ThirdPartyConfig(true, tenantConfig.thirdPartyConfig.providers),
                        new PasswordlessConfig(true),
                        tenantConfig.firstFactors,
                        tenantConfig.requiredSecondaryFactors,
                        tenantConfig.coreConfig
                );
            }

            if (hasFirstFactors) {
                tenantConfig = new TenantConfig(
                        tenantConfig.tenantIdentifier,
                        tenantConfig.emailPasswordConfig,
                        tenantConfig.thirdPartyConfig,
                        tenantConfig.passwordlessConfig,
                        firstFactors,
                        tenantConfig.requiredSecondaryFactors,
                        tenantConfig.coreConfig
                );
            }

            if (hasRequiredSecondaryFactors) {
                tenantConfig = new TenantConfig(
                        tenantConfig.tenantIdentifier,
                        tenantConfig.emailPasswordConfig,
                        tenantConfig.thirdPartyConfig,
                        tenantConfig.passwordlessConfig,
                        tenantConfig.firstFactors,
                        requiredSecondaryFactors,
                        tenantConfig.coreConfig
                );
            }
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
