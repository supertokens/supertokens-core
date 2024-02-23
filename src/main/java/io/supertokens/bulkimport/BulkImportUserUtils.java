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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
import io.supertokens.pluginInterface.bulkimport.BulkImportUser;
import io.supertokens.pluginInterface.bulkimport.BulkImportUser.LoginMethod;
import io.supertokens.pluginInterface.bulkimport.BulkImportUser.LoginMethod.EmailPasswordLoginMethod;
import io.supertokens.pluginInterface.bulkimport.BulkImportUser.LoginMethod.ThirdPartyLoginMethod;
import io.supertokens.pluginInterface.bulkimport.BulkImportUser.LoginMethod.PasswordlessLoginMethod;
import io.supertokens.pluginInterface.bulkimport.BulkImportUser.TotpDevice;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantConfig;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.utils.Utils;
import io.supertokens.utils.JsonValidatorUtils.ValueType;

import static io.supertokens.utils.JsonValidatorUtils.parseAndValidateFieldType;
import static io.supertokens.utils.JsonValidatorUtils.validateJsonFieldType;

public class BulkImportUserUtils {
    public static BulkImportUser createBulkImportUserFromJSON(Main main, AppIdentifier appIdentifier, JsonObject userData, String id)
            throws InvalidBulkImportDataException, StorageQueryException, TenantOrAppNotFoundException {
        List<String> errors = new ArrayList<>();

        String externalUserId = parseAndValidateFieldType(userData, "externalUserId", ValueType.STRING, false, String.class,
                errors, ".");
        JsonObject userMetadata = parseAndValidateFieldType(userData, "userMetadata", ValueType.OBJECT, false,
                JsonObject.class, errors, ".");
        List<String> userRoles = getParsedUserRoles(userData, errors);
        List<TotpDevice> totpDevices = getParsedTotpDevices(userData, errors);
        List<LoginMethod> loginMethods = getParsedLoginMethods(main, appIdentifier, userData, errors);

        if (!errors.isEmpty()) {
            throw new InvalidBulkImportDataException(errors);
        }
        return new BulkImportUser(id, externalUserId, userMetadata, userRoles, totpDevices, loginMethods);
    }

    private static List<String> getParsedUserRoles(JsonObject userData, List<String> errors) {
        JsonArray jsonUserRoles = parseAndValidateFieldType(userData, "roles", ValueType.ARRAY_OF_STRING,
                false,
                JsonArray.class, errors, ".");

        if (jsonUserRoles == null) {
            return null;
        }

        // We already know that the jsonUserRoles is an array of non-empty strings, we will normalise each role now
        List<String> userRoles = new ArrayList<>();
        jsonUserRoles.forEach(role -> userRoles.add(validateAndNormaliseUserRole(role.getAsString())));
        return userRoles;
    }

    private static List<TotpDevice> getParsedTotpDevices(JsonObject userData, List<String> errors) {
        JsonArray jsonTotpDevices = parseAndValidateFieldType(userData, "totp", ValueType.ARRAY_OF_OBJECT, false, JsonArray.class, errors, ".");
        if (jsonTotpDevices == null) {
            return null;
        }

        List<TotpDevice> totpDevices = new ArrayList<>();
        for (JsonElement jsonTotpDeviceEl : jsonTotpDevices) {
            JsonObject jsonTotpDevice = jsonTotpDeviceEl.getAsJsonObject();

            String secretKey = parseAndValidateFieldType(jsonTotpDevice, "secretKey", ValueType.STRING, true, String.class, errors, " for a totp device.");
            Integer period = parseAndValidateFieldType(jsonTotpDevice, "period", ValueType.INTEGER, true, Integer.class, errors, " for a totp device.");
            Integer skew = parseAndValidateFieldType(jsonTotpDevice, "skew", ValueType.INTEGER, true, Integer.class, errors, " for a totp device.");
            String deviceName = parseAndValidateFieldType(jsonTotpDevice, "deviceName", ValueType.STRING, false, String.class, errors, " for a totp device.");

            secretKey = validateAndNormaliseTotpSecretKey(secretKey);
            period = validateAndNormaliseTotpPeriod(period, errors);
            skew = validateAndNormaliseTotpSkew(skew, errors);
            deviceName = validateAndNormaliseTotpDeviceName(deviceName);

            if (secretKey != null && period != null && skew != null) {
                totpDevices.add(new TotpDevice(secretKey, period, skew, deviceName));
            }
        }
        return totpDevices;
    }

    private static List<LoginMethod> getParsedLoginMethods(Main main, AppIdentifier appIdentifier, JsonObject userData, List<String> errors)
            throws StorageQueryException, TenantOrAppNotFoundException {
        JsonArray jsonLoginMethods = parseAndValidateFieldType(userData, "loginMethods", ValueType.ARRAY_OF_OBJECT, true, JsonArray.class, errors, ".");

        if (jsonLoginMethods == null) {
            return new ArrayList<>();
        }

        if (jsonLoginMethods.size() == 0) {
            errors.add("At least one loginMethod is required.");
            return new ArrayList<>();
        }

        validateAndNormaliseIsPrimaryField(jsonLoginMethods, errors);

        List<LoginMethod> loginMethods = new ArrayList<>();

        for (JsonElement jsonLoginMethod : jsonLoginMethods) {
            JsonObject jsonLoginMethodObj = jsonLoginMethod.getAsJsonObject();

            String recipeId = parseAndValidateFieldType(jsonLoginMethodObj, "recipeId", ValueType.STRING, true, String.class, errors, " for a loginMethod.");
            String tenantId = parseAndValidateFieldType(jsonLoginMethodObj, "tenantId", ValueType.STRING, false, String.class, errors, " for a loginMethod.");
            Boolean isVerified = parseAndValidateFieldType(jsonLoginMethodObj, "isVerified", ValueType.BOOLEAN, false, Boolean.class, errors, " for a loginMethod.");
            Boolean isPrimary = parseAndValidateFieldType(jsonLoginMethodObj, "isPrimary", ValueType.BOOLEAN, false, Boolean.class, errors, " for a loginMethod.");
            Integer timeJoined = parseAndValidateFieldType(jsonLoginMethodObj, "timeJoinedInMSSinceEpoch", ValueType.INTEGER, false, Integer.class, errors, " for a loginMethod");

            recipeId = validateAndNormaliseRecipeId(recipeId, errors);
            tenantId= validateAndNormaliseTenantId(main, appIdentifier, tenantId, recipeId, errors);
            isPrimary = validateAndNormaliseIsPrimary(isPrimary);
            isVerified = validateAndNormaliseIsVerified(isVerified);

            long timeJoinedInMSSinceEpoch = validateAndNormaliseTimeJoined(timeJoined);

            if ("emailpassword".equals(recipeId)) {
                String email = parseAndValidateFieldType(jsonLoginMethodObj, "email", ValueType.STRING, true, String.class, errors, " for an emailpassword recipe.");
                String passwordHash = parseAndValidateFieldType(jsonLoginMethodObj, "passwordHash", ValueType.STRING, true, String.class, errors, " for an emailpassword recipe.");
                String hashingAlgorithm = parseAndValidateFieldType(jsonLoginMethodObj, "hashingAlgorithm", ValueType.STRING, true, String.class, errors, " for an emailpassword recipe.");

                email = validateAndNormaliseEmail(email);
                CoreConfig.PASSWORD_HASHING_ALG normalisedHashingAlgorithm = validateAndNormaliseHashingAlgorithm(hashingAlgorithm, errors);
                hashingAlgorithm = normalisedHashingAlgorithm != null ? normalisedHashingAlgorithm.toString() : hashingAlgorithm;
                passwordHash = validateAndNormalisePasswordHash(main, appIdentifier, normalisedHashingAlgorithm, passwordHash, errors);

                EmailPasswordLoginMethod emailPasswordLoginMethod = new EmailPasswordLoginMethod(email, passwordHash, hashingAlgorithm);
                loginMethods.add(new LoginMethod(tenantId, recipeId, isVerified, isPrimary, timeJoinedInMSSinceEpoch, emailPasswordLoginMethod, null, null));
            } else if ("thirdparty".equals(recipeId)) {
                String email = parseAndValidateFieldType(jsonLoginMethodObj, "email", ValueType.STRING, true, String.class, errors, " for a thirdparty recipe.");
                String thirdPartyId = parseAndValidateFieldType(jsonLoginMethodObj, "thirdPartyId", ValueType.STRING, true, String.class, errors, " for a thirdparty recipe.");
                String thirdPartyUserId = parseAndValidateFieldType(jsonLoginMethodObj, "thirdPartyUserId", ValueType.STRING, true, String.class, errors, " for a thirdparty recipe.");

                email = validateAndNormaliseEmail(email);
                thirdPartyId = validateAndNormaliseThirdPartyId(thirdPartyId);
                thirdPartyUserId = validateAndNormaliseThirdPartyUserId(thirdPartyUserId);

                ThirdPartyLoginMethod thirdPartyLoginMethod = new ThirdPartyLoginMethod(email, thirdPartyId, thirdPartyUserId);
                loginMethods.add(new LoginMethod(tenantId, recipeId, isVerified, isPrimary, timeJoinedInMSSinceEpoch, null, thirdPartyLoginMethod, null));
            } else if ("passwordless".equals(recipeId)) {
                String email = parseAndValidateFieldType(jsonLoginMethodObj, "email", ValueType.STRING, false, String.class, errors, " for a passwordless recipe.");
                String phoneNumber = parseAndValidateFieldType(jsonLoginMethodObj, "phoneNumber", ValueType.STRING, false, String.class, errors, " for a passwordless recipe.");

                email = validateAndNormaliseEmail(email);
                phoneNumber = validateAndNormalisePhoneNumber(phoneNumber);

                PasswordlessLoginMethod passwordlessLoginMethod = new PasswordlessLoginMethod(email, phoneNumber);
                loginMethods.add(new LoginMethod(tenantId, recipeId, isVerified, isPrimary, timeJoinedInMSSinceEpoch, null, null, passwordlessLoginMethod));
            }
        }
        return loginMethods;
    }

    private static String validateAndNormaliseUserRole(String role) {
        // We just trim the role the CreateRoleAPI.java
        return role.trim();
    }

    private static String validateAndNormaliseTotpSecretKey(String secretKey) {
         // We don't perform any normalisation on the secretKey in ImportTotpDeviceAPI.java
        return secretKey;
    }

    private static Integer validateAndNormaliseTotpPeriod(Number period, List<String> errors) {
        // We don't perform any normalisation on the period in ImportTotpDeviceAPI.java other than checking if it is > 0
        if (period != null && period.intValue() < 1) {
            errors.add("period should be > 0 for a totp device.");
            return null;
        }
        return period != null ? period.intValue() : null;
    }

    private static Integer validateAndNormaliseTotpSkew(Number skew, List<String> errors) {
        // We don't perform any normalisation on the period in ImportTotpDeviceAPI.java other than checking if it is >= 0
        if (skew != null && skew.intValue() < 0) {
            errors.add("skew should be >= 0 for a totp device.");
            return null;
        }
        return skew != null ? skew.intValue() : null;
    }

    private static String validateAndNormaliseTotpDeviceName(String deviceName) {
        // We normalise the deviceName as per the ImportTotpDeviceAPI.java
        return deviceName != null ? deviceName.trim() : null;
    }

    private static void validateAndNormaliseIsPrimaryField(JsonArray jsonLoginMethods, List<String> errors) {
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

    private static String validateAndNormaliseRecipeId(String recipeId, List<String> errors) {
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

    private static String validateAndNormaliseTenantId(Main main, AppIdentifier appIdentifier, String tenantId, String recipeId, List<String> errors)
            throws StorageQueryException, TenantOrAppNotFoundException {
        if (tenantId == null || tenantId.equals(TenantIdentifier.DEFAULT_TENANT_ID)) {
            return tenantId;
        }

        if (Arrays.stream(FeatureFlag.getInstance(main, appIdentifier).getEnabledFeatures())
                .noneMatch(t -> t == EE_FEATURES.MULTI_TENANCY)) {
            errors.add("Multitenancy must be enabled before importing users to a different tenant.");
            return tenantId;
        }
 
        // We make the tenantId lowercase while parsing from the request in WebserverAPI.java
        String normalisedTenantId = tenantId.trim().toLowerCase();
        TenantConfig[] allTenantConfigs = Multitenancy.getAllTenantsForApp(appIdentifier, main);
        ArrayList<String> validTenantIds = new ArrayList<>();
        Arrays.stream(allTenantConfigs)
                .forEach(tenantConfig -> validTenantIds.add(tenantConfig.tenantIdentifier.getTenantId()));

        if (!validTenantIds.contains(normalisedTenantId)) {
            errors.add("Invalid tenantId: " + tenantId + " for " + recipeId + " recipe.");
        }
        return normalisedTenantId;
    }

    private static Boolean validateAndNormaliseIsPrimary(Boolean isPrimary) {
        // We set the default value as false
        return isPrimary == null ? false : isPrimary;
    }

    private static Boolean validateAndNormaliseIsVerified(Boolean isVerified) {
        // We set the default value as false
        return isVerified == null ? false : isVerified;
    }

    private static long validateAndNormaliseTimeJoined(Integer timeJoined) {
        // We default timeJoined to 0 if it is null
        return timeJoined != null ? timeJoined.longValue() : 0;
    }

    private static String validateAndNormaliseEmail(String email) {
        // We normalise the email as per the SignUpAPI.java
        return email != null ? Utils.normaliseEmail(email) : null;
    }

    private static CoreConfig.PASSWORD_HASHING_ALG validateAndNormaliseHashingAlgorithm(String hashingAlgorithm, List<String> errors) {
        if (hashingAlgorithm == null) {
            return null;
        }
        
        try {
            // We trim the hashingAlgorithm and make it uppercase as per the ImportUserWithPasswordHashAPI.java
            return CoreConfig.PASSWORD_HASHING_ALG.valueOf(hashingAlgorithm.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            errors.add("Invalid hashingAlgorithm for emailpassword recipe. Pass one of bcrypt, argon2 or, firebase_scrypt!");
            return null;
        }
    }

    private static String validateAndNormalisePasswordHash(Main main, AppIdentifier appIdentifier, CoreConfig.PASSWORD_HASHING_ALG hashingAlgorithm, String passwordHash, List<String> errors) throws TenantOrAppNotFoundException {
        if (hashingAlgorithm == null || passwordHash == null) {
            return passwordHash;
        }
        
        // We trim the passwordHash and validate it as per ImportUserWithPasswordHashAPI.java
        passwordHash = passwordHash.trim();

        try {
            PasswordHashingUtils.assertSuperTokensSupportInputPasswordHashFormat(appIdentifier, main, passwordHash, hashingAlgorithm);
        } catch (UnsupportedPasswordHashingFormatException  e) { 
            errors.add(e.getMessage());
        }

        return passwordHash;
    }

    private static String validateAndNormaliseThirdPartyId(String thirdPartyId) {
        // We don't perform any normalisation on the thirdPartyId in SignInUpAPI.java
       return thirdPartyId;
   }

    private static String validateAndNormaliseThirdPartyUserId(String thirdPartyUserId) {
        // We don't perform any normalisation on the thirdPartyUserId in SignInUpAPI.java
       return thirdPartyUserId;
   }

    private static String validateAndNormalisePhoneNumber(String phoneNumber) {
        // We normalise the phoneNumber as per the CreateCodeAPI.java
        return phoneNumber != null ?  Utils.normalizeIfPhoneNumber(phoneNumber) : null;
    }

}
