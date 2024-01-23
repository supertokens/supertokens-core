/*
 *    Copyright (c) 2021, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.passwordless;

import io.supertokens.Main;
import io.supertokens.authRecipe.AuthRecipe;
import io.supertokens.config.Config;
import io.supertokens.emailpassword.exceptions.EmailChangeNotAllowedException;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.passwordless.exceptions.*;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.authRecipe.LoginMethod;
import io.supertokens.pluginInterface.authRecipe.sqlStorage.AuthRecipeSQLStorage;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicateEmailException;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicateUserIdException;
import io.supertokens.pluginInterface.emailpassword.exceptions.UnknownUserIdException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifierWithStorage;
import io.supertokens.pluginInterface.multitenancy.TenantConfig;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifierWithStorage;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.passwordless.PasswordlessCode;
import io.supertokens.pluginInterface.passwordless.PasswordlessDevice;
import io.supertokens.pluginInterface.passwordless.exception.*;
import io.supertokens.pluginInterface.passwordless.sqlStorage.PasswordlessSQLStorage;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.utils.Utils;
import org.jetbrains.annotations.TestOnly;

import javax.annotation.Nullable;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public class Passwordless {
    private static final String USER_INPUT_CODE_NUM_CHARS = "0123456789";

    private static Character getRandomNumChar(SecureRandom generator) {
        return USER_INPUT_CODE_NUM_CHARS.charAt(generator.nextInt(USER_INPUT_CODE_NUM_CHARS.length()));
    }

    @TestOnly
    public static CreateCodeResponse createCode(Main main, String email, String phoneNumber, @Nullable String deviceId,
                                                @Nullable String userInputCode)
            throws RestartFlowException, DuplicateLinkCodeHashException,
            StorageQueryException, NoSuchAlgorithmException, InvalidKeyException, IOException, Base64EncodingException {
        try {
            Storage storage = StorageLayer.getStorage(main);
            return createCode(
                    new TenantIdentifierWithStorage(null, null, null, storage),
                    main, email, phoneNumber, deviceId, userInputCode);
        } catch (TenantOrAppNotFoundException | BadPermissionException e) {
            throw new IllegalStateException(e);
        }
    }

    public static CreateCodeResponse createCode(TenantIdentifierWithStorage tenantIdentifierWithStorage, Main main,
                                                String email,
                                                String phoneNumber, @Nullable String deviceId,
                                                @Nullable String userInputCode)
            throws RestartFlowException, DuplicateLinkCodeHashException,
            StorageQueryException, NoSuchAlgorithmException, InvalidKeyException, IOException, Base64EncodingException,
            TenantOrAppNotFoundException, BadPermissionException {

        TenantConfig config = Multitenancy.getTenantInfo(main, tenantIdentifierWithStorage);
        if (config == null) {
            throw new TenantOrAppNotFoundException(tenantIdentifierWithStorage);
        }
        if (!config.passwordlessConfig.enabled) {
            throw new BadPermissionException("Passwordless login not enabled for tenant");
        }

        PasswordlessSQLStorage passwordlessStorage = tenantIdentifierWithStorage.getPasswordlessStorage();
        if (deviceId == null) {
            while (true) {
                CreateCodeInfo info = CreateCodeInfo.generate(userInputCode);
                try {
                    passwordlessStorage.createDeviceWithCode(tenantIdentifierWithStorage, email, phoneNumber,
                            info.linkCodeSalt.encode(), info.code);

                    return info.resp;
                } catch (DuplicateLinkCodeHashException | DuplicateCodeIdException | DuplicateDeviceIdHashException e) {
                    // These are retryable, so ignored here.
                    // DuplicateLinkCodeHashException is also always retryable, because linkCodeHash depends on the
                    // deviceId which is generated again during the retry
                }
            }
        } else {
            PasswordlessDeviceId parsedDeviceId = PasswordlessDeviceId.decodeString(deviceId);

            PasswordlessDevice device = passwordlessStorage.getDevice(tenantIdentifierWithStorage,
                    parsedDeviceId.getHash().encode());
            if (device == null) {
                throw new RestartFlowException();
            }
            while (true) {
                CreateCodeInfo info = CreateCodeInfo.generate(userInputCode, deviceId, device.linkCodeSalt);
                try {
                    passwordlessStorage.createCode(tenantIdentifierWithStorage, info.code);

                    return info.resp;
                } catch (DuplicateLinkCodeHashException e) {
                    if (userInputCode != null) {
                        // We only need to rethrow if the user supplied both the deviceId and the
                        // userInputCode,
                        // because in that case the linkCodeHash will always be the same.
                        throw e;
                    }
                    // It's retrieable otherwise
                } catch (UnknownDeviceIdHash e) {
                    throw new RestartFlowException();
                } catch (DuplicateCodeIdException e) {
                    // Retryable, so ignored here.
                }
            }
        }
    }

    private static String generateUserInputCode() {
        SecureRandom generator = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; ++i) {
            sb.append(getRandomNumChar(generator));
        }
        return sb.toString();
    }

    public static DeviceWithCodes getDeviceWithCodesById(
            TenantIdentifierWithStorage tenantIdentifierWithStorage, String deviceId)
            throws StorageQueryException, NoSuchAlgorithmException, Base64EncodingException {
        return getDeviceWithCodesByIdHash(tenantIdentifierWithStorage,
                PasswordlessDeviceId.decodeString(deviceId).getHash().encode());
    }

    @TestOnly
    public static DeviceWithCodes getDeviceWithCodesById(Main main, String deviceId) throws StorageQueryException,
            NoSuchAlgorithmException, Base64EncodingException {
        Storage storage = StorageLayer.getStorage(main);
        return getDeviceWithCodesById(
                new TenantIdentifierWithStorage(null, null, null, storage),
                deviceId);
    }

    @TestOnly
    public static DeviceWithCodes getDeviceWithCodesByIdHash(Main main, String deviceIdHash)
            throws StorageQueryException {
        Storage storage = StorageLayer.getStorage(main);
        return getDeviceWithCodesByIdHash(
                new TenantIdentifierWithStorage(null, null, null, storage),
                deviceIdHash);
    }

    public static DeviceWithCodes getDeviceWithCodesByIdHash(TenantIdentifierWithStorage tenantIdentifierWithStorage,
                                                             String deviceIdHash)
            throws StorageQueryException {
        PasswordlessSQLStorage passwordlessStorage = tenantIdentifierWithStorage.getPasswordlessStorage();

        PasswordlessDevice device = passwordlessStorage.getDevice(tenantIdentifierWithStorage, deviceIdHash);

        if (device == null) {
            return null;
        }
        PasswordlessCode[] codes = passwordlessStorage.getCodesOfDevice(tenantIdentifierWithStorage, deviceIdHash);

        return new DeviceWithCodes(device, codes);
    }

    public static List<DeviceWithCodes> getDevicesWithCodesByEmail(
            TenantIdentifierWithStorage tenantIdentifierWithStorage, String email)
            throws StorageQueryException {
        PasswordlessSQLStorage passwordlessStorage = tenantIdentifierWithStorage.getPasswordlessStorage();

        PasswordlessDevice[] devices = passwordlessStorage.getDevicesByEmail(tenantIdentifierWithStorage, email);
        ArrayList<DeviceWithCodes> result = new ArrayList<DeviceWithCodes>();
        for (PasswordlessDevice device : devices) {
            PasswordlessCode[] codes = passwordlessStorage.getCodesOfDevice(tenantIdentifierWithStorage,
                    device.deviceIdHash);
            result.add(new DeviceWithCodes(device, codes));
        }

        return result;
    }

    @TestOnly
    public static List<DeviceWithCodes> getDevicesWithCodesByEmail(Main main, String email)
            throws StorageQueryException {
        Storage storage = StorageLayer.getStorage(main);
        return getDevicesWithCodesByEmail(
                new TenantIdentifierWithStorage(null, null, null, storage), email);
    }

    public static List<DeviceWithCodes> getDevicesWithCodesByPhoneNumber(
            TenantIdentifierWithStorage tenantIdentifierWithStorage, String phoneNumber)
            throws StorageQueryException {
        PasswordlessSQLStorage passwordlessStorage = tenantIdentifierWithStorage.getPasswordlessStorage();

        PasswordlessDevice[] devices = passwordlessStorage.getDevicesByPhoneNumber(tenantIdentifierWithStorage,
                phoneNumber);
        ArrayList<DeviceWithCodes> result = new ArrayList<DeviceWithCodes>();
        for (PasswordlessDevice device : devices) {
            PasswordlessCode[] codes = passwordlessStorage.getCodesOfDevice(tenantIdentifierWithStorage,
                    device.deviceIdHash);
            result.add(new DeviceWithCodes(device, codes));
        }

        return result;
    }

    @TestOnly
    public static List<DeviceWithCodes> getDevicesWithCodesByPhoneNumber(Main main, String phoneNumber)
            throws StorageQueryException {
        Storage storage = StorageLayer.getStorage(main);
        return getDevicesWithCodesByPhoneNumber(
                new TenantIdentifierWithStorage(null, null, null, storage),
                phoneNumber);
    }

    @TestOnly
    public static ConsumeCodeResponse consumeCode(Main main,
                                                  String deviceId, String deviceIdHashFromUser,
                                                  String userInputCode, String linkCode)
            throws RestartFlowException, ExpiredUserInputCodeException,
            IncorrectUserInputCodeException, DeviceIdHashMismatchException, StorageTransactionLogicException,
            StorageQueryException, NoSuchAlgorithmException, InvalidKeyException, IOException, Base64EncodingException {
        try {
            Storage storage = StorageLayer.getStorage(main);
            return consumeCode(
                    new TenantIdentifierWithStorage(null, null, null, storage),
                    main, deviceId, deviceIdHashFromUser, userInputCode, linkCode, false, true);
        } catch (TenantOrAppNotFoundException | BadPermissionException e) {
            throw new IllegalStateException(e);
        }
    }

    @TestOnly
    public static ConsumeCodeResponse consumeCode(Main main,
                                                  String deviceId, String deviceIdHashFromUser,
                                                  String userInputCode, String linkCode, boolean setEmailVerified)
            throws RestartFlowException, ExpiredUserInputCodeException,
            IncorrectUserInputCodeException, DeviceIdHashMismatchException, StorageTransactionLogicException,
            StorageQueryException, NoSuchAlgorithmException, InvalidKeyException, IOException, Base64EncodingException {
        try {
            Storage storage = StorageLayer.getStorage(main);
            return consumeCode(
                    new TenantIdentifierWithStorage(null, null, null, storage),
                    main, deviceId, deviceIdHashFromUser, userInputCode, linkCode, setEmailVerified, true);
        } catch (TenantOrAppNotFoundException | BadPermissionException e) {
            throw new IllegalStateException(e);
        }
    }

    @TestOnly
    public static ConsumeCodeResponse consumeCode(TenantIdentifierWithStorage tenantIdentifierWithStorage, Main main,
                                                  String deviceId, String deviceIdHashFromUser,
                                                  String userInputCode, String linkCode)
            throws RestartFlowException, ExpiredUserInputCodeException,
            IncorrectUserInputCodeException, DeviceIdHashMismatchException, StorageTransactionLogicException,
            StorageQueryException, NoSuchAlgorithmException, InvalidKeyException, IOException, Base64EncodingException,
            TenantOrAppNotFoundException, BadPermissionException {
        return consumeCode(tenantIdentifierWithStorage, main, deviceId, deviceIdHashFromUser, userInputCode, linkCode,
                false, true);
    }

    public static ConsumeCodeResponse consumeCode(TenantIdentifierWithStorage tenantIdentifierWithStorage, Main main,
                                                  String deviceId, String deviceIdHashFromUser,
                                                  String userInputCode, String linkCode, boolean setEmailVerified, boolean createRecipeUserIfNotExists)
            throws RestartFlowException, ExpiredUserInputCodeException,
            IncorrectUserInputCodeException, DeviceIdHashMismatchException, StorageTransactionLogicException,
            StorageQueryException, NoSuchAlgorithmException, InvalidKeyException, IOException, Base64EncodingException,
            TenantOrAppNotFoundException, BadPermissionException {

        TenantConfig config = Multitenancy.getTenantInfo(main, tenantIdentifierWithStorage);
        if (config == null) {
            throw new TenantOrAppNotFoundException(tenantIdentifierWithStorage);
        }
        if (!config.passwordlessConfig.enabled) {
            throw new BadPermissionException("Passwordless login not enabled for tenant");
        }

        PasswordlessSQLStorage passwordlessStorage = tenantIdentifierWithStorage.getPasswordlessStorage();
        long passwordlessCodeLifetime = Config.getConfig(tenantIdentifierWithStorage, main)
                .getPasswordlessCodeLifetime();
        int maxCodeInputAttempts = Config.getConfig(tenantIdentifierWithStorage, main)
                .getPasswordlessMaxCodeInputAttempts();

        PasswordlessDeviceIdHash deviceIdHash;
        PasswordlessLinkCodeHash linkCodeHash;
        if (linkCode != null) {
            PasswordlessLinkCode parsedCode = PasswordlessLinkCode.decodeString(linkCode);
            linkCodeHash = parsedCode.getHash();

            PasswordlessCode code = passwordlessStorage.getCodeByLinkCodeHash(tenantIdentifierWithStorage,
                    linkCodeHash.encode());
            if (code == null || code.createdAt < (System.currentTimeMillis() - passwordlessCodeLifetime)) {
                throw new RestartFlowException();
            }
            deviceIdHash = new PasswordlessDeviceIdHash(code.deviceIdHash);

        } else {
            PasswordlessDeviceId parsedDeviceId = PasswordlessDeviceId.decodeString(deviceId);

            deviceIdHash = parsedDeviceId.getHash();
            PasswordlessDevice device = passwordlessStorage.getDevice(tenantIdentifierWithStorage,
                    deviceIdHash.encode());
            if (device == null) {
                throw new RestartFlowException();
            }
            PasswordlessLinkCodeSalt linkCodeSalt = PasswordlessLinkCodeSalt.decodeString(device.linkCodeSalt);
            linkCodeHash = parsedDeviceId.getLinkCode(linkCodeSalt, userInputCode).getHash();
        }

        if (!deviceIdHash.encode().equals(deviceIdHashFromUser.replaceAll("=", ""))) {
            throw new DeviceIdHashMismatchException();
        }

        PasswordlessDevice consumedDevice;
        try {
            consumedDevice = passwordlessStorage.startTransaction(con -> {
                PasswordlessDevice device = passwordlessStorage.getDevice_Transaction(tenantIdentifierWithStorage, con,
                        deviceIdHash.encode());
                if (device == null) {
                    throw new StorageTransactionLogicException(new RestartFlowException());
                }
                if (device.failedAttempts >= maxCodeInputAttempts) {
                    // This can happen if the configured maxCodeInputAttempts changes
                    passwordlessStorage.deleteDevice_Transaction(tenantIdentifierWithStorage, con,
                            deviceIdHash.encode());
                    passwordlessStorage.commitTransaction(con);
                    throw new StorageTransactionLogicException(new RestartFlowException());
                }

                PasswordlessCode code = passwordlessStorage.getCodeByLinkCodeHash_Transaction(
                        tenantIdentifierWithStorage, con,
                        linkCodeHash.encode());
                if (code == null || code.createdAt < System.currentTimeMillis() - passwordlessCodeLifetime) {
                    if (deviceId != null) {
                        // If we get here, it means that the user tried to use a userInputCode, but it was incorrect or
                        // the code expired. This means that we need to increment failedAttempts or clean up the device
                        // if it would exceed the configured max.
                        if (device.failedAttempts + 1 >= maxCodeInputAttempts) {
                            passwordlessStorage.deleteDevice_Transaction(tenantIdentifierWithStorage, con,
                                    deviceIdHash.encode());
                            passwordlessStorage.commitTransaction(con);
                            throw new StorageTransactionLogicException(new RestartFlowException());
                        } else {
                            passwordlessStorage.incrementDeviceFailedAttemptCount_Transaction(
                                    tenantIdentifierWithStorage, con,
                                    deviceIdHash.encode());
                            passwordlessStorage.commitTransaction(con);

                            if (code != null) {
                                throw new StorageTransactionLogicException(new ExpiredUserInputCodeException(
                                        device.failedAttempts + 1, maxCodeInputAttempts));
                            } else {
                                throw new StorageTransactionLogicException(new IncorrectUserInputCodeException(
                                        device.failedAttempts + 1, maxCodeInputAttempts));
                            }
                        }
                    }
                    throw new StorageTransactionLogicException(new RestartFlowException());
                }

                if (device.email != null) {
                    passwordlessStorage.deleteDevicesByEmail_Transaction(tenantIdentifierWithStorage, con,
                            device.email);
                } else if (device.phoneNumber != null) {
                    passwordlessStorage.deleteDevicesByPhoneNumber_Transaction(tenantIdentifierWithStorage, con,
                            device.phoneNumber);
                }

                passwordlessStorage.commitTransaction(con);
                return device;
            });
        } catch (StorageTransactionLogicException e) {
            if (e.actualException instanceof ExpiredUserInputCodeException) {
                throw (ExpiredUserInputCodeException) e.actualException;
            }
            if (e.actualException instanceof IncorrectUserInputCodeException) {
                throw (IncorrectUserInputCodeException) e.actualException;
            }
            if (e.actualException instanceof RestartFlowException) {
                throw (RestartFlowException) e.actualException;
            }
            throw e;
        }

        // Getting here means that we successfully consumed the code
        AuthRecipeUserInfo user = null;
        LoginMethod loginMethod = null;
        if (consumedDevice.email != null) {
            AuthRecipeUserInfo[] users = passwordlessStorage.listPrimaryUsersByEmail(tenantIdentifierWithStorage,
                    consumedDevice.email);
            for (AuthRecipeUserInfo currUser : users) {
                for (LoginMethod currLM : currUser.loginMethods) {
                    if (currLM.recipeId == RECIPE_ID.PASSWORDLESS && currLM.email != null && currLM.email.equals(consumedDevice.email) && currLM.tenantIds.contains(tenantIdentifierWithStorage.getTenantId())) {
                        user = currUser;
                        loginMethod = currLM;
                        break;
                    }
                }
            }
        } else {
            AuthRecipeUserInfo[] users = passwordlessStorage.listPrimaryUsersByPhoneNumber(tenantIdentifierWithStorage,
                    consumedDevice.phoneNumber);
            for (AuthRecipeUserInfo currUser : users) {
                for (LoginMethod currLM : currUser.loginMethods) {
                    if (currLM.recipeId == RECIPE_ID.PASSWORDLESS &&
                            currLM.phoneNumber != null && currLM.phoneNumber.equals(consumedDevice.phoneNumber) && currLM.tenantIds.contains(tenantIdentifierWithStorage.getTenantId())) {
                        user = currUser;
                        loginMethod = currLM;
                        break;
                    }
                }
            }
        }

        if (user == null) {
            if (createRecipeUserIfNotExists) {
                while (true) {
                    try {
                        String userId = Utils.getUUID();
                        long timeJoined = System.currentTimeMillis();
                        user = passwordlessStorage.createUser(tenantIdentifierWithStorage, userId, consumedDevice.email,
                                consumedDevice.phoneNumber, timeJoined);

                        // Set email as verified, if using email
                        if (setEmailVerified && consumedDevice.email != null) {
                            try {
                                AuthRecipeUserInfo finalUser = user;
                                tenantIdentifierWithStorage.getEmailVerificationStorage().startTransaction(con -> {
                                    try {
                                        tenantIdentifierWithStorage.getEmailVerificationStorage()
                                                .updateIsEmailVerified_Transaction(tenantIdentifierWithStorage.toAppIdentifier(), con,
                                                        finalUser.getSupertokensUserId(), consumedDevice.email, true);
                                        tenantIdentifierWithStorage.getEmailVerificationStorage()
                                                .commitTransaction(con);

                                        return null;
                                    } catch (TenantOrAppNotFoundException e) {
                                        throw new StorageTransactionLogicException(e);
                                    }
                                });
                                user.loginMethods[0].setVerified(); // newly created user has only one loginMethod
                            } catch (StorageTransactionLogicException e) {
                                if (e.actualException instanceof TenantOrAppNotFoundException) {
                                    throw (TenantOrAppNotFoundException) e.actualException;
                                }
                                throw new StorageQueryException(e);
                            }
                        }

                        return new ConsumeCodeResponse(true, user, consumedDevice.email, consumedDevice.phoneNumber, consumedDevice);
                    } catch (DuplicateEmailException | DuplicatePhoneNumberException e) {
                        // Getting these would mean that between getting the user and trying creating it:
                        // 1. the user managed to do a full create+consume flow
                        // 2. the users email or phoneNumber was updated to the new one (including device cleanup)
                        // These should be almost impossibly rare, so it's safe to just ask the user to restart.
                        // Also, both would make the current login fail if done before the transaction
                        // by cleaning up the device/code this consume would've used.
                        throw new RestartFlowException();
                    } catch (DuplicateUserIdException e) {
                        // We can retry..
                    }
                }
            }
        } else {
            // We do not need this cleanup if we are creating the user, since it uses the email/phoneNumber of the
            // device, which has already been cleaned up
            if (setEmailVerified && consumedDevice.email != null) {
                // Set email verification
                try {
                    LoginMethod finalLoginMethod = loginMethod;
                    tenantIdentifierWithStorage.getEmailVerificationStorage().startTransaction(con -> {
                        try {
                            tenantIdentifierWithStorage.getEmailVerificationStorage()
                                    .updateIsEmailVerified_Transaction(tenantIdentifierWithStorage.toAppIdentifier(), con,
                                            finalLoginMethod.getSupertokensUserId(), consumedDevice.email, true);
                            tenantIdentifierWithStorage.getEmailVerificationStorage()
                                    .commitTransaction(con);

                            return null;
                        } catch (TenantOrAppNotFoundException e) {
                            throw new StorageTransactionLogicException(e);
                        }
                    });
                    loginMethod.setVerified();
                } catch (StorageTransactionLogicException e) {
                    if (e.actualException instanceof TenantOrAppNotFoundException) {
                        throw (TenantOrAppNotFoundException) e.actualException;
                    }
                    throw new StorageQueryException(e);
                }
            }

            if (loginMethod.email != null && !loginMethod.email.equals(consumedDevice.email)) {
                removeCodesByEmail(tenantIdentifierWithStorage, loginMethod.email);
            }
            if (loginMethod.phoneNumber != null && !loginMethod.phoneNumber.equals(consumedDevice.phoneNumber)) {
                removeCodesByPhoneNumber(tenantIdentifierWithStorage, loginMethod.phoneNumber);
            }
        }
        return new ConsumeCodeResponse(false, user, consumedDevice.email, consumedDevice.phoneNumber, consumedDevice);
    }

    @TestOnly
    public static void removeCode(Main main, String codeId)
            throws StorageQueryException, StorageTransactionLogicException {
        Storage storage = StorageLayer.getStorage(main);
        removeCode(new TenantIdentifierWithStorage(null, null, null, storage),
                codeId);
    }

    public static void removeCode(TenantIdentifierWithStorage tenantIdentifierWithStorage, String codeId)
            throws StorageQueryException, StorageTransactionLogicException {
        PasswordlessSQLStorage passwordlessStorage = tenantIdentifierWithStorage.getPasswordlessStorage();

        PasswordlessCode code = passwordlessStorage.getCode(tenantIdentifierWithStorage, codeId);

        if (code == null) {
            return;
        }

        passwordlessStorage.startTransaction(con -> {
            // Locking the device
            passwordlessStorage.getDevice_Transaction(tenantIdentifierWithStorage, con, code.deviceIdHash);

            PasswordlessCode[] allCodes = passwordlessStorage.getCodesOfDevice_Transaction(tenantIdentifierWithStorage,
                    con,
                    code.deviceIdHash);
            if (!Stream.of(allCodes).anyMatch(code::equals)) {
                // Already deleted
                return null;
            }

            if (allCodes.length == 1) {
                // If the device contains only the current code we should delete the device as well.
                passwordlessStorage.deleteDevice_Transaction(tenantIdentifierWithStorage, con, code.deviceIdHash);
            } else {
                // Otherwise we can just delete the code
                passwordlessStorage.deleteCode_Transaction(tenantIdentifierWithStorage, con, codeId);
            }
            passwordlessStorage.commitTransaction(con);
            return null;
        });
    }

    @TestOnly
    public static void removeCodesByEmail(Main main, String email)
            throws StorageQueryException, StorageTransactionLogicException {
        Storage storage = StorageLayer.getStorage(main);
        removeCodesByEmail(
                new TenantIdentifierWithStorage(null, null, null, storage), email);
    }

    public static void removeCodesByEmail(TenantIdentifierWithStorage tenantIdentifierWithStorage, String email)
            throws StorageQueryException, StorageTransactionLogicException {
        PasswordlessSQLStorage passwordlessStorage = tenantIdentifierWithStorage.getPasswordlessStorage();

        passwordlessStorage.startTransaction(con -> {
            passwordlessStorage.deleteDevicesByEmail_Transaction(tenantIdentifierWithStorage, con, email);
            passwordlessStorage.commitTransaction(con);
            return null;
        });
    }

    @TestOnly
    public static void removeCodesByPhoneNumber(Main main,
                                                String phoneNumber)
            throws StorageQueryException, StorageTransactionLogicException {
        Storage storage = StorageLayer.getStorage(main);
        removeCodesByPhoneNumber(
                new TenantIdentifierWithStorage(null, null, null, storage),
                phoneNumber);
    }

    public static void removeCodesByPhoneNumber(TenantIdentifierWithStorage tenantIdentifierWithStorage,
                                                String phoneNumber)
            throws StorageQueryException, StorageTransactionLogicException {
        PasswordlessSQLStorage passwordlessStorage = tenantIdentifierWithStorage.getPasswordlessStorage();

        passwordlessStorage.startTransaction(con -> {
            passwordlessStorage.deleteDevicesByPhoneNumber_Transaction(tenantIdentifierWithStorage, con, phoneNumber);
            passwordlessStorage.commitTransaction(con);
            return null;
        });
    }

    @TestOnly
    @Deprecated
    public static AuthRecipeUserInfo getUserById(Main main, String userId)
            throws StorageQueryException {
        Storage storage = StorageLayer.getStorage(main);
        return getUserById(
                new AppIdentifierWithStorage(null, null, storage), userId);
    }

    @Deprecated
    public static AuthRecipeUserInfo getUserById(AppIdentifierWithStorage appIdentifierWithStorage, String userId)
            throws StorageQueryException {
        AuthRecipeUserInfo result = appIdentifierWithStorage.getAuthRecipeStorage()
                .getPrimaryUserById(appIdentifierWithStorage, userId);
        if (result == null) {
            return null;
        }
        for (LoginMethod lM : result.loginMethods) {
            if (lM.getSupertokensUserId().equals(userId) && lM.recipeId == RECIPE_ID.PASSWORDLESS) {
                return AuthRecipeUserInfo.create(lM.getSupertokensUserId(), result.isPrimaryUser,
                        lM);
            }
        }
        return null;
    }

    @TestOnly
    public static AuthRecipeUserInfo getUserByPhoneNumber(Main main,
                                                          String phoneNumber) throws StorageQueryException {
        Storage storage = StorageLayer.getStorage(main);
        return getUserByPhoneNumber(
                new TenantIdentifierWithStorage(null, null, null, storage),
                phoneNumber);
    }

    @Deprecated
    public static AuthRecipeUserInfo getUserByPhoneNumber(TenantIdentifierWithStorage tenantIdentifierWithStorage,
                                                          String phoneNumber) throws StorageQueryException {
        AuthRecipeUserInfo[] users = tenantIdentifierWithStorage.getPasswordlessStorage()
                .listPrimaryUsersByPhoneNumber(tenantIdentifierWithStorage, phoneNumber);
        for (AuthRecipeUserInfo user : users) {
            for (LoginMethod lM : user.loginMethods) {
                if (lM.recipeId == RECIPE_ID.PASSWORDLESS && lM.phoneNumber.equals(phoneNumber)) {
                    return user;
                }
            }
        }
        return null;
    }

    @Deprecated
    @TestOnly
    public static AuthRecipeUserInfo getUserByEmail(Main main, String email)
            throws StorageQueryException {
        Storage storage = StorageLayer.getStorage(main);
        return getUserByEmail(
                new TenantIdentifierWithStorage(null, null, null, storage), email);
    }

    @Deprecated
    public static AuthRecipeUserInfo getUserByEmail(TenantIdentifierWithStorage tenantIdentifierWithStorage,
                                                    String email)
            throws StorageQueryException {
        AuthRecipeUserInfo[] users = tenantIdentifierWithStorage.getPasswordlessStorage()
                .listPrimaryUsersByEmail(tenantIdentifierWithStorage, email);
        for (AuthRecipeUserInfo user : users) {
            for (LoginMethod lM : user.loginMethods) {
                if (lM.recipeId == RECIPE_ID.PASSWORDLESS && lM.email.equals(email)) {
                    return user;
                }
            }
        }
        return null;
    }

    @TestOnly
    public static void updateUser(Main main, String userId,
                                  FieldUpdate emailUpdate, FieldUpdate phoneNumberUpdate)
            throws StorageQueryException, UnknownUserIdException, DuplicateEmailException,
            DuplicatePhoneNumberException, UserWithoutContactInfoException, EmailChangeNotAllowedException,
            PhoneNumberChangeNotAllowedException {
        Storage storage = StorageLayer.getStorage(main);
        updateUser(new AppIdentifierWithStorage(null, null, storage),
                userId, emailUpdate, phoneNumberUpdate);
    }

    public static void updateUser(AppIdentifierWithStorage appIdentifierWithStorage, String recipeUserId,
                                  FieldUpdate emailUpdate, FieldUpdate phoneNumberUpdate)
            throws StorageQueryException, UnknownUserIdException, DuplicateEmailException,
            DuplicatePhoneNumberException, UserWithoutContactInfoException, EmailChangeNotAllowedException,
            PhoneNumberChangeNotAllowedException {
        PasswordlessSQLStorage storage = appIdentifierWithStorage.getPasswordlessStorage();

        // We do not lock the user here, because we decided that even if the device cleanup used outdated information
        // it wouldn't leave the system in an incosistent state/cause problems.
        AuthRecipeUserInfo user = AuthRecipe.getUserById(appIdentifierWithStorage, recipeUserId);
        if (user == null) {
            throw new UnknownUserIdException();
        }

        LoginMethod lM = Arrays.stream(user.loginMethods)
                .filter(currlM -> currlM.getSupertokensUserId().equals(recipeUserId) && currlM.recipeId == RECIPE_ID.PASSWORDLESS)
                .findFirst().orElse(null);

        if (lM == null) {
            throw new UnknownUserIdException();
        }

        boolean emailWillBeDefined = emailUpdate != null ? emailUpdate.newValue != null : lM.email != null;
        boolean phoneNumberWillBeDefined = phoneNumberUpdate != null ? phoneNumberUpdate.newValue != null
                : lM.phoneNumber != null;
        if (!emailWillBeDefined && !phoneNumberWillBeDefined) {
            throw new UserWithoutContactInfoException();
        }
        try {
            AuthRecipeSQLStorage authRecipeSQLStorage =
                    (AuthRecipeSQLStorage) appIdentifierWithStorage.getAuthRecipeStorage();
            storage.startTransaction(con -> {
                if (emailUpdate != null && !Objects.equals(emailUpdate.newValue, lM.email)) {
                    if (user.isPrimaryUser) {
                        for (String tenantId : user.tenantIds) {
                            AuthRecipeUserInfo[] existingUsersWithNewEmail =
                                    authRecipeSQLStorage.listPrimaryUsersByEmail_Transaction(
                                            appIdentifierWithStorage, con,
                                            emailUpdate.newValue);

                            for (AuthRecipeUserInfo userWithSameEmail : existingUsersWithNewEmail) {
                                if (!userWithSameEmail.tenantIds.contains(tenantId)) {
                                    continue;
                                }
                                if (userWithSameEmail.isPrimaryUser && !userWithSameEmail.getSupertokensUserId().equals(user.getSupertokensUserId())) {
                                    throw new StorageTransactionLogicException(
                                            new EmailChangeNotAllowedException());
                                }
                            }
                        }
                    }
                    try {
                        storage.updateUserEmail_Transaction(appIdentifierWithStorage, con, recipeUserId,
                                emailUpdate.newValue);
                    } catch (UnknownUserIdException | DuplicateEmailException e) {
                        throw new StorageTransactionLogicException(e);
                    }
                    if (lM.email != null) {
                        storage.deleteDevicesByEmail_Transaction(appIdentifierWithStorage, con, lM.email,
                                recipeUserId);
                    }
                    if (emailUpdate.newValue != null) {
                        storage.deleteDevicesByEmail_Transaction(appIdentifierWithStorage, con,
                                emailUpdate.newValue, recipeUserId);
                    }
                }
                if (phoneNumberUpdate != null && !Objects.equals(phoneNumberUpdate.newValue, lM.phoneNumber)) {
                    if (user.isPrimaryUser) {
                        for (String tenantId : user.tenantIds) {
                            AuthRecipeUserInfo[] existingUsersWithNewPhoneNumber =
                                    authRecipeSQLStorage.listPrimaryUsersByPhoneNumber_Transaction(
                                            appIdentifierWithStorage, con,
                                            phoneNumberUpdate.newValue);

                            for (AuthRecipeUserInfo userWithSamePhoneNumber : existingUsersWithNewPhoneNumber) {
                                if (!userWithSamePhoneNumber.tenantIds.contains(tenantId)) {
                                    continue;
                                }
                                if (userWithSamePhoneNumber.isPrimaryUser && !userWithSamePhoneNumber.getSupertokensUserId().equals(user.getSupertokensUserId())) {
                                    throw new StorageTransactionLogicException(
                                            new PhoneNumberChangeNotAllowedException());
                                }
                            }
                        }
                    }
                    try {
                        storage.updateUserPhoneNumber_Transaction(appIdentifierWithStorage, con, recipeUserId,
                                phoneNumberUpdate.newValue);
                    } catch (UnknownUserIdException | DuplicatePhoneNumberException e) {
                        throw new StorageTransactionLogicException(e);
                    }
                    if (lM.phoneNumber != null) {
                        storage.deleteDevicesByPhoneNumber_Transaction(appIdentifierWithStorage, con,
                                lM.phoneNumber, recipeUserId);
                    }
                    if (phoneNumberUpdate.newValue != null) {
                        storage.deleteDevicesByPhoneNumber_Transaction(appIdentifierWithStorage, con,
                                phoneNumberUpdate.newValue, recipeUserId);
                    }
                }
                storage.commitTransaction(con);
                return null;
            });
        } catch (StorageTransactionLogicException e) {
            if (e.actualException instanceof UnknownUserIdException) {
                throw (UnknownUserIdException) e.actualException;
            }

            if (e.actualException instanceof DuplicateEmailException) {
                throw (DuplicateEmailException) e.actualException;
            }

            if (e.actualException instanceof DuplicatePhoneNumberException) {
                throw (DuplicatePhoneNumberException) e.actualException;
            }

            if (e.actualException instanceof EmailChangeNotAllowedException) {
                throw (EmailChangeNotAllowedException) e.actualException;
            }

            if (e.actualException instanceof PhoneNumberChangeNotAllowedException) {
                throw (PhoneNumberChangeNotAllowedException) e.actualException;
            }
        }
    }

    public static class DeviceWithCodes {
        public final PasswordlessDevice device;
        public final PasswordlessCode[] codes;

        public DeviceWithCodes(PasswordlessDevice device, PasswordlessCode[] codes) {
            this.device = device;
            this.codes = codes;
        }
    }

    // This class represents an optional update that can have null as a new value.
    // By passing null instead of this object, we can signify no-update, while passing the object
    // with null (or a new value) can request an update to that value.
    // This is like a specifically named Optional.
    public static class FieldUpdate {
        public final String newValue;

        public FieldUpdate(String newValue) {
            this.newValue = newValue;
        }
    }

    public static class CreateCodeResponse {
        public String deviceIdHash;
        public String codeId;
        public String deviceId;
        public String userInputCode;
        public String linkCode;
        public long timeCreated;

        public CreateCodeResponse(String deviceIdHash, String codeId, String deviceId, String userInputCode,
                                  String linkCode, long timeCreated) {
            this.deviceIdHash = deviceIdHash;
            this.codeId = codeId;
            this.deviceId = deviceId;
            this.userInputCode = userInputCode;
            this.linkCode = linkCode;
            this.timeCreated = timeCreated;
        }
    }

    public static class ConsumeCodeResponse {
        public boolean createdNewUser;

        @Nullable
        public AuthRecipeUserInfo user;
        public String email;
        public String phoneNumber;

        public PasswordlessDevice consumedDevice;

        public ConsumeCodeResponse(boolean createdNewUser, @Nullable AuthRecipeUserInfo user, String email, String phoneNumber, PasswordlessDevice consumedDevice) {
            this.createdNewUser = createdNewUser;
            this.user = user;
            this.email = email;
            this.phoneNumber = phoneNumber;
            this.consumedDevice = consumedDevice;
        }
    }

    private static class CreateCodeInfo {
        public final PasswordlessLinkCodeSalt linkCodeSalt;
        public final CreateCodeResponse resp;
        public final PasswordlessCode code;

        private CreateCodeInfo(String codeId, String deviceId, String deviceIdHash, String linkCode,
                               PasswordlessLinkCodeSalt linkCodeSalt, String linkCodeHash, String userInputCode,
                               Long createdAt) {
            this.linkCodeSalt = linkCodeSalt;
            this.code = new PasswordlessCode(codeId, deviceIdHash, linkCodeHash, createdAt);
            this.resp = new CreateCodeResponse(deviceIdHash, codeId, deviceId, userInputCode, linkCode, createdAt);
        }

        public static CreateCodeInfo generate(String userInputCode)
                throws InvalidKeyException, NoSuchAlgorithmException, IOException {
            SecureRandom generator = new SecureRandom();
            byte[] deviceIdBytes = new byte[32];
            generator.nextBytes(deviceIdBytes);

            byte[] linkCodeSaltBytes = new byte[32];
            generator.nextBytes(linkCodeSaltBytes);

            return generate(userInputCode, new PasswordlessDeviceId(deviceIdBytes),
                    new PasswordlessLinkCodeSalt(linkCodeSaltBytes));
        }

        public static CreateCodeInfo generate(String userInputCode, String deviceIdString, String linkCodeSaltString)
                throws InvalidKeyException, NoSuchAlgorithmException, IOException, Base64EncodingException {
            PasswordlessDeviceId deviceId = PasswordlessDeviceId.decodeString(deviceIdString);
            PasswordlessLinkCodeSalt linkCodeSalt = PasswordlessLinkCodeSalt.decodeString(linkCodeSaltString);
            return generate(userInputCode, deviceId, linkCodeSalt);
        }

        public static CreateCodeInfo generate(String userInputCode, PasswordlessDeviceId deviceId,
                                              PasswordlessLinkCodeSalt linkCodeSalt)
                throws InvalidKeyException, NoSuchAlgorithmException, IOException {
            if (userInputCode == null) {
                userInputCode = generateUserInputCode();
            }

            String codeId = Utils.getUUID();

            String deviceIdStr = deviceId.encode();
            String deviceIdHash = deviceId.getHash().encode();

            PasswordlessLinkCode linkCode = deviceId.getLinkCode(linkCodeSalt, userInputCode);
            String linkCodeStr = linkCode.encode();

            String linkCodeHashStr = linkCode.getHash().encode();

            long createdAt = System.currentTimeMillis();

            return new CreateCodeInfo(codeId, deviceIdStr, deviceIdHash, linkCodeStr, linkCodeSalt, linkCodeHashStr,
                    userInputCode, createdAt);
        }
    }
}
