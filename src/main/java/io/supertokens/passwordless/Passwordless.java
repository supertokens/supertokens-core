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
import io.supertokens.config.Config;
import io.supertokens.exceptions.TenantOrAppNotFoundException;
import io.supertokens.passwordless.exceptions.*;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicateEmailException;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicateUserIdException;
import io.supertokens.pluginInterface.emailpassword.exceptions.UnknownUserIdException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.passwordless.PasswordlessCode;
import io.supertokens.pluginInterface.passwordless.PasswordlessDevice;
import io.supertokens.pluginInterface.passwordless.UserInfo;
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
            return createCode(new TenantIdentifier(null, null, null), main, email, phoneNumber, deviceId,
                    userInputCode);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException("Should never come here");
        }
    }

    public static CreateCodeResponse createCode(TenantIdentifier tenantIdentifier, Main main, String email,
                                                String phoneNumber, @Nullable String deviceId,
                                                @Nullable String userInputCode)
            throws RestartFlowException, DuplicateLinkCodeHashException,
            StorageQueryException, NoSuchAlgorithmException, InvalidKeyException, IOException, Base64EncodingException,
            TenantOrAppNotFoundException {
        PasswordlessSQLStorage passwordlessStorage = StorageLayer.getPasswordlessStorage(tenantIdentifier,
                main);
        if (deviceId == null) {
            while (true) {
                CreateCodeInfo info = CreateCodeInfo.generate(userInputCode);
                try {
                    passwordlessStorage.createDeviceWithCode(email, phoneNumber, info.linkCodeSalt.encode(), info.code);

                    return info.resp;
                } catch (DuplicateLinkCodeHashException | DuplicateCodeIdException | DuplicateDeviceIdHashException e) {
                    // These are retryable, so ignored here.
                    // DuplicateLinkCodeHashException is also always retryable, because linkCodeHash depends on the
                    // deviceId which is generated again during the retry
                }
            }
        } else {
            PasswordlessDeviceId parsedDeviceId = PasswordlessDeviceId.decodeString(deviceId);

            PasswordlessDevice device = passwordlessStorage.getDevice(parsedDeviceId.getHash().encode());
            if (device == null) {
                throw new RestartFlowException();
            }
            while (true) {
                CreateCodeInfo info = CreateCodeInfo.generate(userInputCode, deviceId, device.linkCodeSalt);
                try {
                    passwordlessStorage.createCode(info.code);

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

    public static DeviceWithCodes getDeviceWithCodesById(TenantIdentifier tenantIdentifier, Main main,
                                                         String deviceId) throws StorageQueryException,
            StorageTransactionLogicException, NoSuchAlgorithmException, Base64EncodingException,
            TenantOrAppNotFoundException {
        return getDeviceWithCodesByIdHash(tenantIdentifier, main,
                PasswordlessDeviceId.decodeString(deviceId).getHash().encode());
    }

    @TestOnly
    public static DeviceWithCodes getDeviceWithCodesById(Main main, String deviceId) throws StorageQueryException,
            StorageTransactionLogicException, NoSuchAlgorithmException, Base64EncodingException {
        try {
            return getDeviceWithCodesById(new TenantIdentifier(null, null, null), main, deviceId);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException("Should never come here");
        }
    }

    @TestOnly
    public static DeviceWithCodes getDeviceWithCodesByIdHash(Main main,
                                                             String deviceIdHash)
            throws StorageQueryException, StorageTransactionLogicException {
        try {
            return getDeviceWithCodesByIdHash(new TenantIdentifier(null, null, null), main, deviceIdHash);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException("Should never come here");
        }
    }

    public static DeviceWithCodes getDeviceWithCodesByIdHash(TenantIdentifier tenantIdentifier, Main main,
                                                             String deviceIdHash)
            throws StorageQueryException, StorageTransactionLogicException, TenantOrAppNotFoundException {
        PasswordlessSQLStorage passwordlessStorage = StorageLayer.getPasswordlessStorage(tenantIdentifier,
                main);

        PasswordlessDevice device = passwordlessStorage.getDevice(deviceIdHash);

        if (device == null) {
            return null;
        }
        PasswordlessCode[] codes = passwordlessStorage.getCodesOfDevice(deviceIdHash);

        return new DeviceWithCodes(device, codes);
    }

    public static List<DeviceWithCodes> getDevicesWithCodesByEmail(TenantIdentifier tenantIdentifier,
                                                                   Main main, String email)
            throws StorageQueryException, StorageTransactionLogicException, TenantOrAppNotFoundException {
        PasswordlessSQLStorage passwordlessStorage = StorageLayer.getPasswordlessStorage(tenantIdentifier,
                main);

        PasswordlessDevice[] devices = passwordlessStorage.getDevicesByEmail(email);
        ArrayList<DeviceWithCodes> result = new ArrayList<DeviceWithCodes>();
        for (PasswordlessDevice device : devices) {
            PasswordlessCode[] codes = passwordlessStorage.getCodesOfDevice(device.deviceIdHash);
            result.add(new DeviceWithCodes(device, codes));
        }

        return result;
    }

    @TestOnly
    public static List<DeviceWithCodes> getDevicesWithCodesByEmail(Main main, String email)
            throws StorageQueryException, StorageTransactionLogicException {
        try {
            return getDevicesWithCodesByEmail(new TenantIdentifier(null, null, null), main, email);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException("Should never come here");
        }
    }

    public static List<DeviceWithCodes> getDevicesWithCodesByPhoneNumber(TenantIdentifier tenantIdentifier,
                                                                         Main main, String phoneNumber)
            throws StorageQueryException, StorageTransactionLogicException, TenantOrAppNotFoundException {
        PasswordlessSQLStorage passwordlessStorage = StorageLayer.getPasswordlessStorage(tenantIdentifier,
                main);

        PasswordlessDevice[] devices = passwordlessStorage.getDevicesByPhoneNumber(phoneNumber);
        ArrayList<DeviceWithCodes> result = new ArrayList<DeviceWithCodes>();
        for (PasswordlessDevice device : devices) {
            PasswordlessCode[] codes = passwordlessStorage.getCodesOfDevice(device.deviceIdHash);
            result.add(new DeviceWithCodes(device, codes));
        }

        return result;
    }

    @TestOnly
    public static List<DeviceWithCodes> getDevicesWithCodesByPhoneNumber(Main main, String phoneNumber)
            throws StorageQueryException, StorageTransactionLogicException {
        try {
            return getDevicesWithCodesByPhoneNumber(new TenantIdentifier(null, null, null), main, phoneNumber);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException("Should never come here");
        }
    }

    @TestOnly
    public static ConsumeCodeResponse consumeCode(Main main,
                                                  String deviceId, String deviceIdHashFromUser,
                                                  String userInputCode, String linkCode)
            throws RestartFlowException, ExpiredUserInputCodeException,
            IncorrectUserInputCodeException, DeviceIdHashMismatchException, StorageTransactionLogicException,
            StorageQueryException, NoSuchAlgorithmException, InvalidKeyException, IOException, Base64EncodingException {
        try {
            return consumeCode(new TenantIdentifier(null, null, null), main, deviceId, deviceIdHashFromUser,
                    userInputCode, linkCode);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException("Should never come here");
        }
    }

    public static ConsumeCodeResponse consumeCode(TenantIdentifier tenantIdentifier, Main main,
                                                  String deviceId, String deviceIdHashFromUser,
                                                  String userInputCode, String linkCode)
            throws RestartFlowException, ExpiredUserInputCodeException,
            IncorrectUserInputCodeException, DeviceIdHashMismatchException, StorageTransactionLogicException,
            StorageQueryException, NoSuchAlgorithmException, InvalidKeyException, IOException, Base64EncodingException,
            TenantOrAppNotFoundException {
        PasswordlessSQLStorage passwordlessStorage = StorageLayer.getPasswordlessStorage(tenantIdentifier,
                main);
        long passwordlessCodeLifetime = Config.getConfig(tenantIdentifier, main)
                .getPasswordlessCodeLifetime();
        int maxCodeInputAttempts = Config.getConfig(tenantIdentifier, main)
                .getPasswordlessMaxCodeInputAttempts();

        PasswordlessDeviceIdHash deviceIdHash;
        PasswordlessLinkCodeHash linkCodeHash;
        if (linkCode != null) {
            PasswordlessLinkCode parsedCode = PasswordlessLinkCode.decodeString(linkCode);
            linkCodeHash = parsedCode.getHash();

            PasswordlessCode code = passwordlessStorage.getCodeByLinkCodeHash(linkCodeHash.encode());
            if (code == null || code.createdAt < (System.currentTimeMillis() - passwordlessCodeLifetime)) {
                throw new RestartFlowException();
            }
            deviceIdHash = new PasswordlessDeviceIdHash(code.deviceIdHash);

        } else {
            PasswordlessDeviceId parsedDeviceId = PasswordlessDeviceId.decodeString(deviceId);

            deviceIdHash = parsedDeviceId.getHash();
            PasswordlessDevice device = passwordlessStorage.getDevice(deviceIdHash.encode());
            if (device == null) {
                throw new RestartFlowException();
            }
            PasswordlessLinkCodeSalt linkCodeSalt = PasswordlessLinkCodeSalt.decodeString(device.linkCodeSalt);
            linkCodeHash = parsedDeviceId.getLinkCode(linkCodeSalt, userInputCode).getHash();
        }

        if (!deviceIdHash.encode().equals(deviceIdHashFromUser)) {
            throw new DeviceIdHashMismatchException();
        }

        PasswordlessDevice consumedDevice;
        try {
            consumedDevice = passwordlessStorage.startTransaction(con -> {
                PasswordlessDevice device = passwordlessStorage.getDevice_Transaction(con, deviceIdHash.encode());
                if (device == null) {
                    throw new StorageTransactionLogicException(new RestartFlowException());
                }
                if (device.failedAttempts >= maxCodeInputAttempts) {
                    // This can happen if the configured maxCodeInputAttempts changes
                    passwordlessStorage.deleteDevice_Transaction(con, deviceIdHash.encode());
                    passwordlessStorage.commitTransaction(con);
                    throw new StorageTransactionLogicException(new RestartFlowException());
                }

                PasswordlessCode code = passwordlessStorage.getCodeByLinkCodeHash_Transaction(con,
                        linkCodeHash.encode());
                if (code == null || code.createdAt < System.currentTimeMillis() - passwordlessCodeLifetime) {
                    if (deviceId != null) {
                        // If we get here, it means that the user tried to use a userInputCode, but it was incorrect or
                        // the code expired. This means that we need to increment failedAttempts or clean up the device
                        // if it would exceed the configured max.
                        if (device.failedAttempts + 1 >= maxCodeInputAttempts) {
                            passwordlessStorage.deleteDevice_Transaction(con, deviceIdHash.encode());
                            passwordlessStorage.commitTransaction(con);
                            throw new StorageTransactionLogicException(new RestartFlowException());
                        } else {
                            passwordlessStorage.incrementDeviceFailedAttemptCount_Transaction(con,
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
                    passwordlessStorage.deleteDevicesByEmail_Transaction(con, device.email);
                } else if (device.phoneNumber != null) {
                    passwordlessStorage.deleteDevicesByPhoneNumber_Transaction(con, device.phoneNumber);
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
        UserInfo user = consumedDevice.email != null ? passwordlessStorage.getUserByEmail(consumedDevice.email)
                : passwordlessStorage.getUserByPhoneNumber(consumedDevice.phoneNumber);
        if (user == null) {
            while (true) {
                try {
                    String userId = Utils.getUUID();
                    long timeJoined = System.currentTimeMillis();
                    user = new UserInfo(userId, consumedDevice.email, consumedDevice.phoneNumber, timeJoined);
                    passwordlessStorage.createUser(user);
                    return new ConsumeCodeResponse(true, user);
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
        } else {
            // We do not need this cleanup if we are creating the user, since it uses the email/phoneNumber of the
            // device, which has already been cleaned up
            if (user.email != null && !user.email.equals(consumedDevice.email)) {
                removeCodesByEmail(tenantIdentifier, main, user.email);
            }
            if (user.phoneNumber != null && !user.phoneNumber.equals(consumedDevice.phoneNumber)) {
                removeCodesByPhoneNumber(tenantIdentifier, main, user.phoneNumber);
            }
        }
        return new ConsumeCodeResponse(false, user);
    }

    @TestOnly
    public static void removeCode(Main main, String codeId)
            throws StorageQueryException, StorageTransactionLogicException {
        try {
            removeCode(new TenantIdentifier(null, null, null), main, codeId);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException("Should never come here");
        }
    }

    public static void removeCode(TenantIdentifier tenantIdentifier, Main main, String codeId)
            throws StorageQueryException, StorageTransactionLogicException, TenantOrAppNotFoundException {
        PasswordlessSQLStorage passwordlessStorage = StorageLayer.getPasswordlessStorage(tenantIdentifier,
                main);

        PasswordlessCode code = passwordlessStorage.getCode(codeId);

        if (code == null) {
            return;
        }

        passwordlessStorage.startTransaction(con -> {
            // Locking the device
            passwordlessStorage.getDevice_Transaction(con, code.deviceIdHash);

            PasswordlessCode[] allCodes = passwordlessStorage.getCodesOfDevice_Transaction(con, code.deviceIdHash);
            if (!Stream.of(allCodes).anyMatch(code::equals)) {
                // Already deleted
                return null;
            }

            if (allCodes.length == 1) {
                // If the device contains only the current code we should delete the device as well.
                passwordlessStorage.deleteDevice_Transaction(con, code.deviceIdHash);
            } else {
                // Otherwise we can just delete the code
                passwordlessStorage.deleteCode_Transaction(con, codeId);
            }
            passwordlessStorage.commitTransaction(con);
            return null;
        });
    }

    @TestOnly
    public static void removeCodesByEmail(Main main, String email)
            throws StorageQueryException, StorageTransactionLogicException {
        try {
            removeCodesByEmail(new TenantIdentifier(null, null, null), main, email);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException("Should never come here");
        }
    }

    public static void removeCodesByEmail(TenantIdentifier tenantIdentifier, Main main, String email)
            throws StorageQueryException, StorageTransactionLogicException, TenantOrAppNotFoundException {
        PasswordlessSQLStorage passwordlessStorage = StorageLayer.getPasswordlessStorage(tenantIdentifier,
                main);

        passwordlessStorage.startTransaction(con -> {
            passwordlessStorage.deleteDevicesByEmail_Transaction(con, email);
            passwordlessStorage.commitTransaction(con);
            return null;
        });
    }

    @TestOnly
    public static void removeCodesByPhoneNumber(Main main,
                                                String phoneNumber)
            throws StorageQueryException, StorageTransactionLogicException {
        try {
            removeCodesByPhoneNumber(new TenantIdentifier(null, null, null), main, phoneNumber);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException("Should never come here");
        }
    }

    public static void removeCodesByPhoneNumber(TenantIdentifier tenantIdentifier, Main main,
                                                String phoneNumber)
            throws StorageQueryException, StorageTransactionLogicException, TenantOrAppNotFoundException {
        PasswordlessSQLStorage passwordlessStorage = StorageLayer.getPasswordlessStorage(tenantIdentifier,
                main);

        passwordlessStorage.startTransaction(con -> {
            passwordlessStorage.deleteDevicesByPhoneNumber_Transaction(con, phoneNumber);
            passwordlessStorage.commitTransaction(con);
            return null;
        });
    }

    @TestOnly
    public static UserInfo getUserById(Main main, String userId)
            throws StorageQueryException {
        try {
            return getUserById(new TenantIdentifier(null, null, null), main, userId);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException("Should never come here");
        }
    }

    public static UserInfo getUserById(TenantIdentifier tenantIdentifier, Main main, String userId)
            throws StorageQueryException, TenantOrAppNotFoundException {
        return StorageLayer.getPasswordlessStorage(tenantIdentifier, main).getUserById(userId);
    }

    @TestOnly
    public static UserInfo getUserByPhoneNumber(Main main,
                                                String phoneNumber) throws StorageQueryException {
        try {
            return getUserByPhoneNumber(new TenantIdentifier(null, null, null), main, phoneNumber);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException("Should never come here");
        }
    }

    public static UserInfo getUserByPhoneNumber(TenantIdentifier tenantIdentifier, Main main,
                                                String phoneNumber)
            throws StorageQueryException, TenantOrAppNotFoundException {
        return StorageLayer.getPasswordlessStorage(tenantIdentifier, main)
                .getUserByPhoneNumber(phoneNumber);
    }

    @TestOnly
    public static UserInfo getUserByEmail(Main main, String email)
            throws StorageQueryException {
        try {
            return getUserByEmail(new TenantIdentifier(null, null, null), main, email);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException("Should never come here");
        }
    }


    public static UserInfo getUserByEmail(TenantIdentifier tenantIdentifier, Main main, String email)
            throws StorageQueryException, TenantOrAppNotFoundException {
        return StorageLayer.getPasswordlessStorage(tenantIdentifier, main).getUserByEmail(email);
    }

    @TestOnly
    public static void updateUser(Main main, String userId,
                                  FieldUpdate emailUpdate, FieldUpdate phoneNumberUpdate)
            throws StorageQueryException, UnknownUserIdException, DuplicateEmailException,
            DuplicatePhoneNumberException, UserWithoutContactInfoException {
        try {
            updateUser(new TenantIdentifier(null, null, null), main, userId, emailUpdate, phoneNumberUpdate);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException("Should never come here");
        }
    }

    public static void updateUser(TenantIdentifier tenantIdentifier, Main main, String userId,
                                  FieldUpdate emailUpdate, FieldUpdate phoneNumberUpdate)
            throws StorageQueryException, UnknownUserIdException, DuplicateEmailException,
            DuplicatePhoneNumberException, UserWithoutContactInfoException, TenantOrAppNotFoundException {
        PasswordlessSQLStorage storage = StorageLayer.getPasswordlessStorage(tenantIdentifier, main);

        // We do not lock the user here, because we decided that even if the device cleanup used outdated information
        // it wouldn't leave the system in an incosistent state/cause problems.
        UserInfo user = storage.getUserById(userId);
        if (user == null) {
            throw new UnknownUserIdException();
        }
        boolean emailWillBeDefined = emailUpdate != null ? emailUpdate.newValue != null : user.email != null;
        boolean phoneNumberWillBeDefined = phoneNumberUpdate != null ? phoneNumberUpdate.newValue != null
                : user.phoneNumber != null;
        if (!emailWillBeDefined && !phoneNumberWillBeDefined) {
            throw new UserWithoutContactInfoException();
        }
        try {
            storage.startTransaction(con -> {
                if (emailUpdate != null && !Objects.equals(emailUpdate.newValue, user.email)) {
                    try {
                        storage.updateUserEmail_Transaction(con, userId, emailUpdate.newValue);
                    } catch (UnknownUserIdException | DuplicateEmailException e) {
                        throw new StorageTransactionLogicException(e);
                    }
                    if (user.email != null) {
                        storage.deleteDevicesByEmail_Transaction(con, user.email);
                    }
                    if (emailUpdate.newValue != null) {
                        storage.deleteDevicesByEmail_Transaction(con, emailUpdate.newValue);
                    }
                }
                if (phoneNumberUpdate != null && !Objects.equals(phoneNumberUpdate.newValue, user.phoneNumber)) {
                    try {
                        storage.updateUserPhoneNumber_Transaction(con, userId, phoneNumberUpdate.newValue);
                    } catch (UnknownUserIdException | DuplicatePhoneNumberException e) {
                        throw new StorageTransactionLogicException(e);
                    }
                    if (user.phoneNumber != null) {
                        storage.deleteDevicesByPhoneNumber_Transaction(con, user.phoneNumber);
                    }
                    if (phoneNumberUpdate.newValue != null) {
                        storage.deleteDevicesByPhoneNumber_Transaction(con, phoneNumberUpdate.newValue);
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
        public UserInfo user;

        public ConsumeCodeResponse(boolean createdNewUser, UserInfo user) {
            this.createdNewUser = createdNewUser;
            this.user = user;
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
