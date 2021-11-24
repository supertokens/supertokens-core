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

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.apache.tomcat.util.codec.binary.Base64;

import io.supertokens.Main;
import io.supertokens.passwordless.exceptions.RestartFlowException;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicateEmailException;
import io.supertokens.pluginInterface.emailpassword.exceptions.UnknownUserIdException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.passwordless.PasswordlessCode;
import io.supertokens.pluginInterface.passwordless.PasswordlessDevice;
import io.supertokens.pluginInterface.passwordless.UserInfo;
import io.supertokens.pluginInterface.passwordless.exception.DuplicateCodeIdException;
import io.supertokens.pluginInterface.passwordless.exception.DuplicateDeviceIdHashException;
import io.supertokens.pluginInterface.passwordless.exception.DuplicateLinkCodeHashException;
import io.supertokens.pluginInterface.passwordless.exception.DuplicatePhoneNumberException;
import io.supertokens.pluginInterface.passwordless.exception.UnknownDeviceIdHash;
import io.supertokens.pluginInterface.passwordless.sqlStorage.PasswordlessSQLStorage;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.utils.Utils;

public class Passwordless {
// We are storing the "alphabets" like this because we remove a few characters from the normal English alphabet.
    // e.g.: remove easy to confuse chars (oO0, Il)
    private static final String USER_INPUT_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz";
    private static final String USER_INPUT_CODE_NUM_CHARS = "123456789";

    private static Character getRandomAlphaChar(SecureRandom generator) {
        return USER_INPUT_CODE_ALPHABET.charAt(generator.nextInt(USER_INPUT_CODE_ALPHABET.length()));
    }

    private static Character getRandomNumChar(SecureRandom generator) {
        return USER_INPUT_CODE_NUM_CHARS.charAt(generator.nextInt(USER_INPUT_CODE_NUM_CHARS.length()));
    }

    public static CreateCodeResponse createCode(Main main, String email, String phoneNumber, @Nullable String deviceId,
            @Nullable String userInputCode) throws RestartFlowException, DuplicateLinkCodeHashException,
            StorageQueryException, NoSuchAlgorithmException, InvalidKeyException {
        PasswordlessSQLStorage passwordlessStorage = StorageLayer.getPasswordlessStorage(main);
        if (deviceId == null) {
            while (true) {
                CreateCodeInfo info = CreateCodeInfo.generate(userInputCode);
                try {
                    passwordlessStorage.createDeviceWithCode(email, phoneNumber, info.code);

                    return info.resp;
                } catch (DuplicateLinkCodeHashException | DuplicateCodeIdException | DuplicateDeviceIdHashException e) {
                    // These are retryable, so ignored here.
                    // DuplicateLinkCodeHashException is also always retryable, because linkCodeHash depends on the
                    // deviceId which is generated again during the retry
                }
            }
        } else {
            while (true) {
                CreateCodeInfo info = CreateCodeInfo.generate(userInputCode, deviceId);
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
        // This logic is based on the idea that we wanted to incorporate letters as well
        // as numbers in the code.
        // We are allowing at most 2 letters in a row, to try and avoid generating slurs
        // or other abusive codes.

        // Note: this implementation gives an equal chance to either numbers or letters,
        // so the probability of any
        // character is lower than the probability of a number, but the distribution is
        // uniform inside both alphabets.

        SecureRandom generator = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        int prevAlphaCharCount = 0;
        for (int i = 0; i < 6; ++i) {
            if ((i < 2 || prevAlphaCharCount < 2) && generator.nextBoolean()) {
                ++prevAlphaCharCount;
                sb.append(getRandomAlphaChar(generator));
            } else {
                prevAlphaCharCount = 0;
                sb.append(getRandomNumChar(generator));
            }
        }
        return sb.toString();
    }

    public static DeviceWithCodes getDeviceWithCodesById(Main main, String deviceId)
            throws StorageQueryException, StorageTransactionLogicException, NoSuchAlgorithmException {
        byte[] deviceIdBytes = Base64.decodeBase64(deviceId);
        String deviceIdHash = Base64.encodeBase64URLSafeString(Utils.hashSHA256Bytes(deviceIdBytes));

        return getDeviceWithCodesByIdHash(main, deviceIdHash);
    }

    public static DeviceWithCodes getDeviceWithCodesByIdHash(Main main, String deviceIdHash)
            throws StorageQueryException, StorageTransactionLogicException {
        PasswordlessSQLStorage passwordlessStorage = StorageLayer.getPasswordlessStorage(main);

        PasswordlessDevice device = passwordlessStorage.getDevice(deviceIdHash);

        if (device == null) {
            return null;
        }
        PasswordlessCode[] codes = passwordlessStorage.getCodesOfDevice(deviceIdHash);

        return new DeviceWithCodes(device, codes);
    }

    public static List<DeviceWithCodes> getDevicesWithCodesByEmail(Main main, String email)
            throws StorageQueryException, StorageTransactionLogicException {
        PasswordlessSQLStorage passwordlessStorage = StorageLayer.getPasswordlessStorage(main);

        PasswordlessDevice[] devices = passwordlessStorage.getDevicesByEmail(email);
        ArrayList<DeviceWithCodes> result = new ArrayList<DeviceWithCodes>();
        for (PasswordlessDevice device : devices) {
            PasswordlessCode[] codes = passwordlessStorage.getCodesOfDevice(device.deviceIdHash);
            result.add(new DeviceWithCodes(device, codes));
        }

        return result;
    }

    public static List<DeviceWithCodes> getDevicesWithCodesByPhoneNumber(Main main, String phoneNumber)
            throws StorageQueryException, StorageTransactionLogicException {
        PasswordlessSQLStorage passwordlessStorage = StorageLayer.getPasswordlessStorage(main);

        PasswordlessDevice[] devices = passwordlessStorage.getDevicesByPhoneNumber(phoneNumber);
        ArrayList<DeviceWithCodes> result = new ArrayList<DeviceWithCodes>();
        for (PasswordlessDevice device : devices) {
            PasswordlessCode[] codes = passwordlessStorage.getCodesOfDevice(device.deviceIdHash);
            result.add(new DeviceWithCodes(device, codes));
        }

        return result;
    }

    public static void removeCode(Main main, String codeId)
            throws StorageQueryException, StorageTransactionLogicException {
        PasswordlessSQLStorage passwordlessStorage = StorageLayer.getPasswordlessStorage(main);

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

    public static UserInfo getUserById(Main main, String userId) throws StorageQueryException {
        return StorageLayer.getPasswordlessStorage(main).getUserById(userId);
    }

    public static UserInfo getUserByPhoneNumber(Main main, String phoneNumber) throws StorageQueryException {
        return StorageLayer.getPasswordlessStorage(main).getUserByPhoneNumber(phoneNumber);
    }

    public static UserInfo getUserByEmail(Main main, String email) throws StorageQueryException {
        return StorageLayer.getPasswordlessStorage(main).getUserByEmail(email);
    }

    public static void updateUser(Main main, String userId, FieldUpdate emailUpdate, FieldUpdate phoneNumberUpdate)
            throws StorageQueryException, UnknownUserIdException, DuplicateEmailException,
            DuplicatePhoneNumberException {
        PasswordlessSQLStorage storage = StorageLayer.getPasswordlessStorage(main);

        // We do not lock the user here, because we decided that even if the device cleanup used outdated information
        // it wouldn't leave the system in an incosistent state/cause problems.
        UserInfo user = storage.getUserById(userId);
        if (user == null) {
            throw new UnknownUserIdException();
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

    private static class CreateCodeInfo {
        public final CreateCodeResponse resp;
        public final PasswordlessCode code;

        private CreateCodeInfo(String codeId, String deviceId, String deviceIdHash, String linkCode,
                String linkCodeHash, String userInputCode, Long createdAt) {
            this.code = new PasswordlessCode(codeId, deviceIdHash, linkCodeHash, createdAt);
            this.resp = new CreateCodeResponse(deviceIdHash, codeId, deviceId, userInputCode, linkCode, createdAt);
        }

        public static CreateCodeInfo generate(String userInputCode)
                throws InvalidKeyException, NoSuchAlgorithmException {
            SecureRandom generator = new SecureRandom();
            byte[] deviceIdBytes = new byte[32];
            generator.nextBytes(deviceIdBytes);
            return generate(userInputCode, deviceIdBytes);
        }

        public static CreateCodeInfo generate(String userInputCode, String deviceId)
                throws InvalidKeyException, NoSuchAlgorithmException {
            byte[] deviceIdBytes = Base64.decodeBase64(deviceId);
            return generate(userInputCode, deviceIdBytes);
        }

        public static CreateCodeInfo generate(String userInputCode, byte[] deviceIdBytes)
                throws InvalidKeyException, NoSuchAlgorithmException {
            if (userInputCode == null) {
                userInputCode = generateUserInputCode();
            }

            String deviceId = Base64.encodeBase64String(deviceIdBytes);
            String deviceIdHash = Base64.encodeBase64URLSafeString(Utils.hashSHA256Bytes(deviceIdBytes));
            String codeId = Utils.getUUID();

            byte[] linkCodeBytes = Utils.hmacSHA256(deviceIdBytes, userInputCode);
            byte[] linkCodeHashBytes = Utils.hashSHA256Bytes(linkCodeBytes);

            String linkCode = Base64.encodeBase64URLSafeString(linkCodeBytes);
            String linkCodeHash = Base64.encodeBase64String(linkCodeHashBytes);

            long createdAt = System.currentTimeMillis();

            return new CreateCodeInfo(codeId, deviceId, deviceIdHash, linkCode, linkCodeHash, userInputCode, createdAt);
        }
    }
}
