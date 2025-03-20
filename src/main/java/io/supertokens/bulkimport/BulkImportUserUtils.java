/*
 *    Copyright (c) 2024, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.bulkimport;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.bulkimport.exceptions.InvalidBulkImportDataException;
import io.supertokens.config.CoreConfig;
import io.supertokens.emailpassword.PasswordHashingUtils;
import io.supertokens.emailpassword.exceptions.UnsupportedPasswordHashingFormatException;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlag;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.bulkimport.BulkImportUser;
import io.supertokens.pluginInterface.bulkimport.BulkImportUser.LoginMethod;
import io.supertokens.pluginInterface.bulkimport.BulkImportUser.TotpDevice;
import io.supertokens.pluginInterface.bulkimport.BulkImportUser.UserRole;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantConfig;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.utils.JsonValidatorUtils.ValueType;
import io.supertokens.utils.Utils;

import java.util.*;

import static io.supertokens.utils.JsonValidatorUtils.parseAndValidateFieldType;
import static io.supertokens.utils.JsonValidatorUtils.validateJsonFieldType;

public class BulkImportUserUtils {
    private String[] allUserRoles;
    private Set<String> allExternalUserIds;

    public BulkImportUserUtils(String[] allUserRoles) {
        this.allUserRoles = allUserRoles;
        this.allExternalUserIds = new HashSet<>();
    }

    public BulkImportUser createBulkImportUserFromJSON(Main main, AppIdentifier appIdentifier, JsonObject userData, IDMode idMode)
            throws InvalidBulkImportDataException, StorageQueryException, TenantOrAppNotFoundException {
        List<String> errors = new ArrayList<>();

        String externalUserId = parseAndValidateFieldType(userData, "externalUserId", ValueType.STRING, false,
                String.class,
                errors, ".");
        JsonObject userMetadata = parseAndValidateFieldType(userData, "userMetadata", ValueType.OBJECT, false,
                JsonObject.class, errors, ".");
        List<UserRole> userRoles = getParsedUserRoles(main, appIdentifier, userData, errors);
        List<TotpDevice> totpDevices = getParsedTotpDevices(main, appIdentifier, userData, errors);
        List<LoginMethod> loginMethods = getParsedLoginMethods(main, appIdentifier, userData, errors, idMode);

        externalUserId = validateAndNormaliseExternalUserId(externalUserId, errors);

        validateTenantIdsForRoleAndLoginMethods(main, appIdentifier, userRoles, loginMethods, errors);

        if (!errors.isEmpty()) {
            throw new InvalidBulkImportDataException(errors);
        }
        String id = getPrimaryLoginMethod(loginMethods).superTokensUserId;
        return new BulkImportUser(id, externalUserId, userMetadata, userRoles, totpDevices, loginMethods);
    }

    private List<UserRole> getParsedUserRoles(Main main, AppIdentifier appIdentifier, JsonObject userData,
            List<String> errors) throws StorageQueryException, TenantOrAppNotFoundException {
        JsonArray jsonUserRoles = parseAndValidateFieldType(userData, "userRoles", ValueType.ARRAY_OF_OBJECT, false,
                JsonArray.class, errors, ".");

        if (jsonUserRoles == null) {
            return null;
        }

        List<UserRole> userRoles = new ArrayList<>();

        for (JsonElement jsonUserRoleEl : jsonUserRoles) {
            JsonObject jsonUserRole = jsonUserRoleEl.getAsJsonObject();

            String role = parseAndValidateFieldType(jsonUserRole, "role", ValueType.STRING, true, String.class, errors,
                    " for a user role.");
            JsonArray jsonTenantIds = parseAndValidateFieldType(jsonUserRole, "tenantIds", ValueType.ARRAY_OF_STRING,
                    true, JsonArray.class, errors, " for a user role.");

            role = validateAndNormaliseUserRole(role, errors);
            List<String> normalisedTenantIds = validateAndNormaliseTenantIds(main, appIdentifier, jsonTenantIds, errors,
                    " for a user role.");

            if (role != null && normalisedTenantIds != null) {
                userRoles.add(new UserRole(role, normalisedTenantIds));
            }
        }
        return userRoles;
    }

    private List<TotpDevice> getParsedTotpDevices(Main main, AppIdentifier appIdentifier, JsonObject userData,
            List<String> errors) throws StorageQueryException, TenantOrAppNotFoundException {
        JsonArray jsonTotpDevices = parseAndValidateFieldType(userData, "totpDevices", ValueType.ARRAY_OF_OBJECT, false,
                JsonArray.class, errors, ".");

        if (jsonTotpDevices == null) {
            return null;
        }

        if (Arrays.stream(FeatureFlag.getInstance(main, appIdentifier).getEnabledFeatures())
                .noneMatch(t -> t == EE_FEATURES.MFA)) {
            errors.add("MFA must be enabled to import totp devices.");
            return null;
        }

        List<TotpDevice> totpDevices = new ArrayList<>();
        for (JsonElement jsonTotpDeviceEl : jsonTotpDevices) {
            JsonObject jsonTotpDevice = jsonTotpDeviceEl.getAsJsonObject();

            String secretKey = parseAndValidateFieldType(jsonTotpDevice, "secretKey", ValueType.STRING, true,
                    String.class, errors, " for a totp device.");
            Integer period = parseAndValidateFieldType(jsonTotpDevice, "period", ValueType.INTEGER, false,
                    Integer.class, errors, " for a totp device.");
            Integer skew = parseAndValidateFieldType(jsonTotpDevice, "skew", ValueType.INTEGER, false, Integer.class,
                    errors, " for a totp device.");
            String deviceName = parseAndValidateFieldType(jsonTotpDevice, "deviceName", ValueType.STRING, false,
                    String.class, errors, " for a totp device.");

            secretKey = validateAndNormaliseTotpSecretKey(secretKey, errors);
            period = validateAndNormaliseTotpPeriod(period, errors);
            skew = validateAndNormaliseTotpSkew(skew, errors);
            deviceName = validateAndNormaliseTotpDeviceName(deviceName, errors);

            if (secretKey != null && period != null && skew != null) {
                totpDevices.add(new TotpDevice(secretKey, period, skew, deviceName));
            }
        }
        return totpDevices;
    }

    private List<LoginMethod> getParsedLoginMethods(Main main, AppIdentifier appIdentifier, JsonObject userData,
            List<String> errors, IDMode idMode)
            throws StorageQueryException, TenantOrAppNotFoundException {
        JsonArray jsonLoginMethods = parseAndValidateFieldType(userData, "loginMethods", ValueType.ARRAY_OF_OBJECT,
                true, JsonArray.class, errors, ".");

        if (jsonLoginMethods == null) {
            return new ArrayList<>();
        }

        if (jsonLoginMethods.size() == 0) {
            errors.add("At least one loginMethod is required.");
            return new ArrayList<>();
        }

        if (jsonLoginMethods.size() > 1) {
            if (!Utils.isAccountLinkingEnabled(main, appIdentifier)) {
                errors.add("Account linking must be enabled to import multiple loginMethods.");
            }
        }

        validateAndNormaliseIsPrimaryField(jsonLoginMethods, errors);

        List<LoginMethod> loginMethods = new ArrayList<>();

        for (JsonElement jsonLoginMethod : jsonLoginMethods) {
            JsonObject jsonLoginMethodObj = jsonLoginMethod.getAsJsonObject();

            String recipeId = parseAndValidateFieldType(jsonLoginMethodObj, "recipeId", ValueType.STRING, true,
                    String.class, errors, " for a loginMethod.");
            JsonArray tenantIds = parseAndValidateFieldType(jsonLoginMethodObj, "tenantIds", ValueType.ARRAY_OF_STRING,
                    false, JsonArray.class, errors, " for a loginMethod.");
            Boolean isVerified = parseAndValidateFieldType(jsonLoginMethodObj, "isVerified", ValueType.BOOLEAN, false,
                    Boolean.class, errors, " for a loginMethod.");
            Boolean isPrimary = parseAndValidateFieldType(jsonLoginMethodObj, "isPrimary", ValueType.BOOLEAN, false,
                    Boolean.class, errors, " for a loginMethod.");
            Long timeJoined = parseAndValidateFieldType(jsonLoginMethodObj, "timeJoinedInMSSinceEpoch", ValueType.LONG,
                    false, Long.class, errors, " for a loginMethod");


            recipeId = validateAndNormaliseRecipeId(recipeId, errors);
            List<String> normalisedTenantIds = validateAndNormaliseTenantIds(main, appIdentifier, tenantIds, errors,
                    " for " + recipeId + " recipe.");
            isPrimary = validateAndNormaliseIsPrimary(isPrimary);
            isVerified = validateAndNormaliseIsVerified(isVerified);

            long timeJoinedInMSSinceEpoch = validateAndNormaliseTimeJoined(timeJoined, errors);

            String supertokensUserId = switch (idMode) {
                case READ_STORED -> parseAndValidateFieldType(jsonLoginMethodObj, "superTokensUserId", ValueType.STRING,
                        true, String.class, errors, " for a loginMethod");
                case GENERATE -> Utils.getUUID();
            };

            if ("emailpassword".equals(recipeId)) {
                String email = parseAndValidateFieldType(jsonLoginMethodObj, "email", ValueType.STRING, true,
                        String.class, errors, " for an emailpassword recipe.");
                String passwordHash = parseAndValidateFieldType(jsonLoginMethodObj, "passwordHash", ValueType.STRING,
                        false, String.class, errors, " for an emailpassword recipe.");
                String hashingAlgorithm = parseAndValidateFieldType(jsonLoginMethodObj, "hashingAlgorithm",
                        ValueType.STRING, false, String.class, errors, " for an emailpassword recipe.");
                String plainTextPassword = parseAndValidateFieldType(jsonLoginMethodObj, "plainTextPassword",
                        ValueType.STRING, false, String.class, errors, " for an emailpassword recipe.");

                if ((passwordHash == null || hashingAlgorithm == null) && plainTextPassword == null) {
                    errors.add("Either (passwordHash, hashingAlgorithm) or plainTextPassword is required for an emailpassword recipe.");
                }

                email = validateAndNormaliseEmail(email, errors);
                CoreConfig.PASSWORD_HASHING_ALG normalisedHashingAlgorithm = validateAndNormaliseHashingAlgorithm(
                        hashingAlgorithm, errors);
                hashingAlgorithm = normalisedHashingAlgorithm != null ? normalisedHashingAlgorithm.toString()
                        : hashingAlgorithm;
                passwordHash = validateAndNormalisePasswordHash(main, appIdentifier, normalisedHashingAlgorithm,
                        passwordHash, errors);

                loginMethods.add(new LoginMethod(normalisedTenantIds, recipeId, isVerified, isPrimary,
                        timeJoinedInMSSinceEpoch, email, passwordHash, hashingAlgorithm, plainTextPassword,
                        null, null, null, supertokensUserId));
            } else if ("thirdparty".equals(recipeId)) {
                String email = parseAndValidateFieldType(jsonLoginMethodObj, "email", ValueType.STRING, true,
                        String.class, errors, " for a thirdparty recipe.");
                String thirdPartyId = parseAndValidateFieldType(jsonLoginMethodObj, "thirdPartyId", ValueType.STRING,
                        true, String.class, errors, " for a thirdparty recipe.");
                String thirdPartyUserId = parseAndValidateFieldType(jsonLoginMethodObj, "thirdPartyUserId",
                        ValueType.STRING, true, String.class, errors, " for a thirdparty recipe.");

                email = validateAndNormaliseEmail(email, errors);
                thirdPartyId = validateAndNormaliseThirdPartyId(thirdPartyId, errors);
                thirdPartyUserId = validateAndNormaliseThirdPartyUserId(thirdPartyUserId, errors);

                loginMethods.add(new LoginMethod(normalisedTenantIds, recipeId, isVerified, isPrimary,
                        timeJoinedInMSSinceEpoch, email, null, null, null,
                        thirdPartyId, thirdPartyUserId, null, supertokensUserId));
            } else if ("passwordless".equals(recipeId)) {
                String email = parseAndValidateFieldType(jsonLoginMethodObj, "email", ValueType.STRING, false,
                        String.class, errors, " for a passwordless recipe.");
                String phoneNumber = parseAndValidateFieldType(jsonLoginMethodObj, "phoneNumber", ValueType.STRING,
                        false, String.class, errors, " for a passwordless recipe.");

                email = validateAndNormaliseEmail(email, errors);
                phoneNumber = validateAndNormalisePhoneNumber(phoneNumber, errors);

                if (email == null && phoneNumber == null) {
                    errors.add("Either email or phoneNumber is required for a passwordless recipe.");
                }

                loginMethods.add(new LoginMethod(normalisedTenantIds, recipeId, isVerified, isPrimary,
                        timeJoinedInMSSinceEpoch, email, null, null, null,
                        null, null, phoneNumber, supertokensUserId));
            }
        }
        return loginMethods;
    }

    private String validateAndNormaliseExternalUserId(String externalUserId, List<String> errors) {
        if (externalUserId == null) {
            return null;
        }

        if (externalUserId.length() > 128) {
            errors.add("externalUserId " + externalUserId + " is too long. Max length is 128.");
        }

        if (!allExternalUserIds.add(externalUserId)) {
            errors.add("externalUserId " + externalUserId + " is not unique. It is already used by another user.");
        }

        // We just trim the externalUserId as per the UpdateExternalUserIdInfoAPI.java
        return externalUserId.trim();
    }

    private String validateAndNormaliseUserRole(String role, List<String> errors) {
        if (role.length() > 255) {
            errors.add("role " + role + " is too long. Max length is 255.");
        }

        // We just trim the role as per the CreateRoleAPI.java
        String normalisedRole = role.trim();

        if (!Arrays.asList(allUserRoles).contains(normalisedRole)) {
            errors.add("Role " + normalisedRole + " does not exist.");
        }

        return normalisedRole;
    }

    private String validateAndNormaliseTotpSecretKey(String secretKey, List<String> errors) {
        if (secretKey == null) {
            return null;
        }

        if (secretKey.length() > 256) {
            errors.add("TOTP secretKey " + secretKey + " is too long. Max length is 256.");
        }

        // We don't perform any normalisation on the secretKey in ImportTotpDeviceAPI.java
        return secretKey;
    }

    private Integer validateAndNormaliseTotpPeriod(Integer period, List<String> errors) {
        // We default to 30 if period is null
        if (period == null) {
            return 30;
        }

        if (period.intValue() < 1) {
            errors.add("period should be > 0 for a totp device.");
            return null;
        }
        return period;
    }

    private Integer validateAndNormaliseTotpSkew(Integer skew, List<String> errors) {
        // We default to 1 if skew is null
        if (skew == null) {
            return 1;
        }

        if (skew.intValue() < 0) {
            errors.add("skew should be >= 0 for a totp device.");
            return null;
        }
        return skew;
    }

    private String validateAndNormaliseTotpDeviceName(String deviceName, List<String> errors) {
        if (deviceName == null) {
            return null;
        }

        if (deviceName.length() > 256) {
            errors.add("TOTP deviceName " + deviceName + " is too long. Max length is 256.");
        }

        // We normalise the deviceName as per the ImportTotpDeviceAPI.java
        return deviceName.trim();
    }

    private void validateAndNormaliseIsPrimaryField(JsonArray jsonLoginMethods, List<String> errors) {
        // We are validating that only one loginMethod has isPrimary as true
        boolean hasPrimaryLoginMethod = false;
        for (JsonElement jsonLoginMethod : jsonLoginMethods) {
            JsonObject jsonLoginMethodObj = jsonLoginMethod.getAsJsonObject();
            if (validateJsonFieldType(jsonLoginMethodObj, "isPrimary", ValueType.BOOLEAN)) {
                if (jsonLoginMethodObj.get("isPrimary").getAsBoolean()) {
                    if (hasPrimaryLoginMethod) {
                        errors.add("No two loginMethods can have isPrimary as true.");
                    }
                    hasPrimaryLoginMethod = true;
                }
            }
        }
    }

    private String validateAndNormaliseRecipeId(String recipeId, List<String> errors) {
        if (recipeId == null) {
            return null;
        }

        // We don't perform any normalisation on the recipeId after reading it from request header.
        // We will validate it as is.
        if (!Arrays.asList("emailpassword", "thirdparty", "passwordless").contains(recipeId)) {
            errors.add("Invalid recipeId for loginMethod. Pass one of emailpassword, thirdparty or, passwordless!");
        }
        return recipeId;
    }

    private List<String> validateAndNormaliseTenantIds(Main main, AppIdentifier appIdentifier,
            JsonArray tenantIds, List<String> errors, String errorSuffix)
            throws StorageQueryException, TenantOrAppNotFoundException {
        if (tenantIds == null) {
            return List.of(TenantIdentifier.DEFAULT_TENANT_ID); // Default to DEFAULT_TENANT_ID ("public")
        }

        List<String> normalisedTenantIds = new ArrayList<>();

        for (JsonElement tenantIdEl : tenantIds) {
            String tenantId = tenantIdEl.getAsString();
            tenantId = validateAndNormaliseTenantId(main, appIdentifier, tenantId, errors, errorSuffix);

            if (tenantId != null) {
                normalisedTenantIds.add(tenantId);
            }
        }
        return normalisedTenantIds;
    }

    private String validateAndNormaliseTenantId(Main main, AppIdentifier appIdentifier, String tenantId,
            List<String> errors, String errorSuffix)
            throws StorageQueryException, TenantOrAppNotFoundException {
        if (tenantId == null || tenantId.equals(TenantIdentifier.DEFAULT_TENANT_ID)) {
            return tenantId;
        }

        if (Arrays.stream(FeatureFlag.getInstance(main, appIdentifier).getEnabledFeatures())
                .noneMatch(t -> t == EE_FEATURES.MULTI_TENANCY)) {
            errors.add("Multitenancy must be enabled before importing users to a different tenant.");
            return null;
        }

        // We make the tenantId lowercase while parsing from the request in WebserverAPI.java
        String normalisedTenantId = tenantId.trim().toLowerCase();
        TenantConfig[] allTenantConfigs = Multitenancy.getAllTenantsForApp(appIdentifier, main);
        Set<String> validTenantIds = new HashSet<>();
        Arrays.stream(allTenantConfigs)
                .forEach(tenantConfig -> validTenantIds.add(tenantConfig.tenantIdentifier.getTenantId()));

        if (!validTenantIds.contains(normalisedTenantId)) {
            errors.add("Invalid tenantId: " + tenantId + errorSuffix);
            return null;
        }
        return normalisedTenantId;
    }

    private Boolean validateAndNormaliseIsPrimary(Boolean isPrimary) {
        // We set the default value as false
        return isPrimary == null ? false : isPrimary;
    }

    private Boolean validateAndNormaliseIsVerified(Boolean isVerified) {
        // We set the default value as false
        return isVerified == null ? false : isVerified;
    }

    private long validateAndNormaliseTimeJoined(Long timeJoined, List<String> errors) {
        // We default timeJoined to currentTime if it is null
        if (timeJoined == null) {
            return System.currentTimeMillis();
        }

        if (timeJoined > System.currentTimeMillis()) {
            errors.add("timeJoined cannot be in future for a loginMethod.");
        }

        if (timeJoined < 0) {
            errors.add("timeJoined cannot be < 0 for a loginMethod.");
        }

        return timeJoined.longValue();
    }

    private String validateAndNormaliseEmail(String email, List<String> errors) {
        if (email == null) {
            return null;
        }

        if (email.length() > 255) {
            errors.add("email " + email + " is too long. Max length is 256.");
        }

        // We normalise the email as per the SignUpAPI.java
        return Utils.normaliseEmail(email);
    }

    private CoreConfig.PASSWORD_HASHING_ALG validateAndNormaliseHashingAlgorithm(String hashingAlgorithm,
            List<String> errors) {
        if (hashingAlgorithm == null) {
            return null;
        }

        try {
            // We trim the hashingAlgorithm and make it uppercase as per the ImportUserWithPasswordHashAPI.java
            return CoreConfig.PASSWORD_HASHING_ALG.valueOf(hashingAlgorithm.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            errors.add(
                    "Invalid hashingAlgorithm for emailpassword recipe. Pass one of bcrypt, argon2 or, firebase_scrypt!");
            return null;
        }
    }

    private String validateAndNormalisePasswordHash(Main main, AppIdentifier appIdentifier,
            CoreConfig.PASSWORD_HASHING_ALG hashingAlgorithm, String passwordHash, List<String> errors)
            throws TenantOrAppNotFoundException {
        if (hashingAlgorithm == null || passwordHash == null) {
            return passwordHash;
        }

        if (passwordHash.length() > 256) {
            errors.add("passwordHash is too long. Max length is 256.");
        }

        // We trim the passwordHash and validate it as per ImportUserWithPasswordHashAPI.java
        passwordHash = passwordHash.trim();

        try {
            PasswordHashingUtils.assertSuperTokensSupportInputPasswordHashFormat(appIdentifier, main, passwordHash,
                    hashingAlgorithm);
        } catch (UnsupportedPasswordHashingFormatException e) {
            errors.add(e.getMessage());
        }

        return passwordHash;
    }

    private String validateAndNormaliseThirdPartyId(String thirdPartyId, List<String> errors) {
        if (thirdPartyId == null) {
            return null;
        }

        if (thirdPartyId.length() > 28) {
            errors.add("thirdPartyId " + thirdPartyId + " is too long. Max length is 28.");
        }

        // We don't perform any normalisation on the thirdPartyId in SignInUpAPI.java
        return thirdPartyId;
    }

    private String validateAndNormaliseThirdPartyUserId(String thirdPartyUserId, List<String> errors) {
        if (thirdPartyUserId == null) {
            return null;
        }

        if (thirdPartyUserId.length() > 256) {
            errors.add("thirdPartyUserId " + thirdPartyUserId + " is too long. Max length is 256.");
        }

        // We don't perform any normalisation on the thirdPartyUserId in SignInUpAPI.java
        return thirdPartyUserId;
    }

    private String validateAndNormalisePhoneNumber(String phoneNumber, List<String> errors) {
        if (phoneNumber == null) {
            return null;
        }

        if (phoneNumber.length() > 256) {
            errors.add("phoneNumber " + phoneNumber + " is too long. Max length is 256.");
        }

        // We normalise the phoneNumber as per the CreateCodeAPI.java
        return Utils.normalizeIfPhoneNumber(phoneNumber);
    }

    private void validateTenantIdsForRoleAndLoginMethods(Main main, AppIdentifier appIdentifier,
            List<UserRole> userRoles, List<LoginMethod> loginMethods, List<String> errors)
            throws TenantOrAppNotFoundException {
        if (loginMethods == null) {
            return;
        }

        // First validate that tenantIds provided for userRoles also exist in the loginMethods
        if (userRoles != null) {
            for (UserRole userRole : userRoles) {
                for (String tenantId : userRole.tenantIds) {
                    if (!tenantId.equals(TenantIdentifier.DEFAULT_TENANT_ID) && loginMethods.stream()
                            .noneMatch(loginMethod -> loginMethod.tenantIds.contains(tenantId))) {
                        errors.add("TenantId " + tenantId + " for a user role does not exist in loginMethods.");
                    }
                }
            }
        }

        // Now validate that all the tenants share the same storage
        String commonTenantUserPoolId = null;
        for (LoginMethod loginMethod : loginMethods) {
            for (String tenantId : loginMethod.tenantIds) {
                TenantIdentifier tenantIdentifier = new TenantIdentifier(appIdentifier.getConnectionUriDomain(),
                        appIdentifier.getAppId(), tenantId);
                Storage storage = StorageLayer.getStorage(tenantIdentifier, main);
                String tenantUserPoolId = storage.getUserPoolId();

                if (commonTenantUserPoolId == null) {
                    commonTenantUserPoolId = tenantUserPoolId;
                } else if (!commonTenantUserPoolId.equals(tenantUserPoolId)) {
                    errors.add("All tenants for a user must share the same database for " + loginMethod.recipeId
                            + " recipe.");
                    break; // Break to avoid adding the same error multiple times for the same loginMethod
                }
            }
        }
    }

    public static BulkImportUser.LoginMethod getPrimaryLoginMethod(BulkImportUser user) {
        return getPrimaryLoginMethod(user.loginMethods);
    }

    // Returns the primary loginMethod of the user. If no loginMethod is marked as
    // primary, then the oldest loginMethod is returned.
    public static BulkImportUser.LoginMethod getPrimaryLoginMethod(List<LoginMethod> loginMethods) {
        BulkImportUser.LoginMethod oldestLM = loginMethods.get(0);
        for (BulkImportUser.LoginMethod lm : loginMethods) {
            if (lm.isPrimary) {
                return lm;
            }

            if (lm.timeJoinedInMSSinceEpoch < oldestLM.timeJoinedInMSSinceEpoch) {
                oldestLM = lm;
            }
        }
        return oldestLM;
    }

    public enum IDMode {
        GENERATE,
        READ_STORED;
    }

    public static Map<String, String> collectRecipeIdsToPrimaryIds(List<BulkImportUser> users) {
        Map<String, String> recipeUserIdByPrimaryUserId = new HashMap<>();
        if(users == null){
            return recipeUserIdByPrimaryUserId;
        }
        for(BulkImportUser user: users){
            LoginMethod primaryLM = BulkImportUserUtils.getPrimaryLoginMethod(user);
            for (LoginMethod lm : user.loginMethods) {
                if (lm.getSuperTokenOrExternalUserId().equals(primaryLM.getSuperTokenOrExternalUserId())) {
                    continue;
                }
                recipeUserIdByPrimaryUserId.put(lm.getSuperTokenOrExternalUserId(),
                        primaryLM.getSuperTokenOrExternalUserId());
            }
        }
        return recipeUserIdByPrimaryUserId;
    }

    public static LoginMethod findLoginMethodForUser(List<BulkImportUser> users, String recipeUserId) {
        if(users == null || users.isEmpty() || recipeUserId == null){
            return null;
        }
        for(BulkImportUser user: users) {
            for (LoginMethod loginMethod : user.loginMethods) {
                if (recipeUserId.equals(loginMethod.superTokensUserId)) {
                    return loginMethod;
                }
            }
        }
        return null;
    }

    public static BulkImportUser findUserByPrimaryId(List<BulkImportUser> users, String primaryUserId) {
        if(users == null || users.isEmpty() || primaryUserId == null){
            return null;
        }
        for(BulkImportUser user: users) {
            if(primaryUserId.equals(user.primaryUserId)){
                return user;
            }
        }
        return null;
    }
}
