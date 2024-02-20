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
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.supertokens.bulkimport.exceptions.InvalidBulkImportDataException;
import io.supertokens.pluginInterface.bulkimport.BulkImportUser;
import io.supertokens.pluginInterface.bulkimport.BulkImportUser.LoginMethod;
import io.supertokens.pluginInterface.bulkimport.BulkImportUser.LoginMethod.EmailPasswordLoginMethod;
import io.supertokens.pluginInterface.bulkimport.BulkImportUser.LoginMethod.ThirdPartyLoginMethod;
import io.supertokens.pluginInterface.bulkimport.BulkImportUser.LoginMethod.PasswordlessLoginMethod;
import io.supertokens.pluginInterface.bulkimport.BulkImportUser.TotpDevice;
import io.supertokens.pluginInterface.utils.JsonValidatorUtils.ValueType;
import io.supertokens.utils.Utils;

import static io.supertokens.pluginInterface.utils.JsonValidatorUtils.parseAndValidateField;
import static io.supertokens.pluginInterface.utils.JsonValidatorUtils.validateJsonFieldType;

public class BulkImportUserUtils {
    public static BulkImportUser createBulkImportUserFromJSON(JsonObject userData, String id) throws InvalidBulkImportDataException {
        List<String> errors = new ArrayList<>();

        String externalUserId = parseAndValidateField(userData, "externalUserId", ValueType.STRING, false, String.class, errors, ".");
        JsonObject userMetadata = parseAndValidateField(userData, "userMetadata", ValueType.OBJECT, false, JsonObject.class, errors, ".");
        List<String>  userRoles = getParsedUserRoles(userData, errors);
        List<TotpDevice> totpDevices = getParsedTotpDevices(userData, errors);
        List<LoginMethod> loginMethods = getParsedLoginMethods(userData, errors);

        if (!errors.isEmpty()) {
            throw new InvalidBulkImportDataException(errors);
        }
        return new BulkImportUser(id, externalUserId, userMetadata, userRoles, totpDevices, loginMethods);
    } 

    private static List<String> getParsedUserRoles(JsonObject userData, List<String> errors) {
        JsonArray jsonUserRoles = parseAndValidateField(userData, "roles", ValueType.ARRAY_OF_STRING,
                false,
                JsonArray.class, errors, ".");

        if (jsonUserRoles == null) {
            return null;
        }

        List<String> userRoles = new ArrayList<>();
        jsonUserRoles.forEach(role -> userRoles.add(role.getAsString().trim()));
        return userRoles;
    }

    private static List<TotpDevice> getParsedTotpDevices(JsonObject userData, List<String> errors) {
        JsonArray jsonTotpDevices = parseAndValidateField(userData, "totp", ValueType.ARRAY_OF_OBJECT, false, JsonArray.class, errors, ".");
        if (jsonTotpDevices == null) {
            return null;
        }

        List<TotpDevice> totpDevices = new ArrayList<>();
        for (JsonElement jsonTotpDeviceEl : jsonTotpDevices) {
            JsonObject jsonTotpDevice = jsonTotpDeviceEl.getAsJsonObject();

            String secretKey = parseAndValidateField(jsonTotpDevice, "secretKey", ValueType.STRING, true, String.class, errors, " for a totp device.");
            Number period = parseAndValidateField(jsonTotpDevice, "period", ValueType.NUMBER, true, Number.class, errors, " for a totp device.");
            Number skew = parseAndValidateField(jsonTotpDevice, "skew", ValueType.NUMBER, true, Number.class, errors, " for a totp device.");
            String deviceName = parseAndValidateField(jsonTotpDevice, "deviceName", ValueType.STRING, false, String.class, errors, " for a totp device.");

            if (period != null && period.intValue() < 1) {
                errors.add("period should be > 0 for a totp device.");
            }
            if (skew != null && skew.intValue() < 0) {
                errors.add("skew should be >= 0 for a totp device.");
            }

            if(deviceName != null) {
                deviceName = deviceName.trim();
            }
            totpDevices.add(new TotpDevice(secretKey, period.intValue(), skew.intValue(), deviceName));
        }
        return totpDevices;
    }

    private static List<LoginMethod> getParsedLoginMethods(JsonObject userData, List<String> errors) {
      JsonArray jsonLoginMethods = parseAndValidateField(userData, "loginMethods", ValueType.ARRAY_OF_OBJECT, true, JsonArray.class, errors, ".");

      if (jsonLoginMethods == null) {
          return new ArrayList<>();
      }

      if (jsonLoginMethods.size() == 0) {
          errors.add("At least one loginMethod is required.");
          return new ArrayList<>();
      }

      Boolean hasPrimaryLoginMethod = false;

      List<LoginMethod> loginMethods = new ArrayList<>();
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

          String recipeId = parseAndValidateField(jsonLoginMethodObj, "recipeId", ValueType.STRING, true, String.class, errors, " for a loginMethod.");
          String tenantId = parseAndValidateField(jsonLoginMethodObj, "tenantId", ValueType.STRING, false, String.class, errors, " for a loginMethod.");
          Boolean isVerified = parseAndValidateField(jsonLoginMethodObj, "isVerified", ValueType.BOOLEAN, false, Boolean.class, errors, " for a loginMethod.");
          Boolean isPrimary = parseAndValidateField(jsonLoginMethodObj, "isPrimary", ValueType.BOOLEAN, false, Boolean.class, errors, " for a loginMethod.");
          Number timeJoined = parseAndValidateField(jsonLoginMethodObj, "timeJoinedInMSSinceEpoch", ValueType.NUMBER, false, Number.class, errors, " for a loginMethod");
          Long timeJoinedInMSSinceEpoch = timeJoined != null ? timeJoined.longValue() : 0;

          if ("emailpassword".equals(recipeId)) {
            String email = parseAndValidateField(jsonLoginMethodObj, "email", ValueType.STRING, true, String.class, errors, " for an emailpassword recipe.");
            String passwordHash = parseAndValidateField(jsonLoginMethodObj, "passwordHash", ValueType.STRING, true, String.class, errors, " for an emailpassword recipe.");
            String hashingAlgorithm = parseAndValidateField(jsonLoginMethodObj, "hashingAlgorithm", ValueType.STRING, true, String.class, errors, " for an emailpassword recipe.");

            email = email != null ? Utils.normaliseEmail(email) : null;
            hashingAlgorithm = hashingAlgorithm != null ? hashingAlgorithm.trim().toUpperCase() : null;

            EmailPasswordLoginMethod emailPasswordLoginMethod = new EmailPasswordLoginMethod(email, passwordHash, hashingAlgorithm);
            loginMethods.add(new LoginMethod(tenantId, recipeId, isVerified, isPrimary, timeJoinedInMSSinceEpoch, emailPasswordLoginMethod, null, null));
          } else if ("thirdparty".equals(recipeId)) {
            String email = parseAndValidateField(jsonLoginMethodObj, "email", ValueType.STRING, true, String.class, errors, " for a thirdparty recipe.");
            String thirdPartyId = parseAndValidateField(jsonLoginMethodObj, "thirdPartyId", ValueType.STRING, true, String.class, errors, " for a thirdparty recipe.");
            String thirdPartyUserId = parseAndValidateField(jsonLoginMethodObj, "thirdPartyUserId", ValueType.STRING, true, String.class, errors, " for a thirdparty recipe.");

            email = email != null ? Utils.normaliseEmail(email) : null;

            ThirdPartyLoginMethod thirdPartyLoginMethod = new ThirdPartyLoginMethod(email, thirdPartyId, thirdPartyUserId);
            loginMethods.add(new LoginMethod(tenantId, recipeId, isVerified, isPrimary, timeJoinedInMSSinceEpoch, null, thirdPartyLoginMethod, null));
          } else if ("passwordless".equals(recipeId)) {
            String email = parseAndValidateField(jsonLoginMethodObj, "email", ValueType.STRING, false, String.class, errors, " for a passwordless recipe.");
            String phoneNumber = parseAndValidateField(jsonLoginMethodObj, "phoneNumber", ValueType.STRING, false, String.class, errors, " for a passwordless recipe.");

            email = email != null ? Utils.normaliseEmail(email) : null;
            phoneNumber = Utils.normalizeIfPhoneNumber(phoneNumber);

            PasswordlessLoginMethod passwordlessLoginMethod = new PasswordlessLoginMethod(email, phoneNumber);
            loginMethods.add(new LoginMethod(tenantId, recipeId, isVerified, isPrimary, timeJoinedInMSSinceEpoch, null, null, passwordlessLoginMethod));
          } else if (recipeId != null) {
            errors.add("Invalid recipeId for loginMethod. Pass one of emailpassword, thirdparty or, passwordless!");
          }
      }
      return loginMethods;
  }
}
