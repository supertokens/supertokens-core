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
import io.supertokens.passwordless.exceptions.ExpiredUserInputCodeException;
import io.supertokens.passwordless.exceptions.IncorrectUserInputCodeException;
import io.supertokens.passwordless.exceptions.RestartFlowException;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicateEmailException;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicateUserIdException;
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

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.annotation.Nullable;

import org.apache.tomcat.util.codec.binary.Base64;

public class Passwordless {
    // We are storing the "alphabets" like this because we might want to change this later,
    // e.g.: remove easy to confuse chars (oO0, Il)
    private static final String USER_INPUT_CODE_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final String USER_INPUT_CODE_NUM_CHARS = "0123456789";

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

        boolean gotDeviceId = deviceId != null;
        boolean gotUserInputCode = userInputCode != null;

        if (userInputCode == null) {
            userInputCode = generateUserInputCode();
        }

        byte[] deviceIdBytes = new byte[32];
        String deviceIdHash = null;
        if (gotDeviceId) {
            deviceIdBytes = Base64.decodeBase64(deviceId);
            deviceIdHash = Base64.encodeBase64URLSafeString(Utils.hashSHA256Bytes(deviceIdBytes));
            PasswordlessDevice device = passwordlessStorage.getDevice(deviceIdHash);
            if (device == null) {
                throw new RestartFlowException();
            }
        }

        SecureRandom generator = new SecureRandom();
        while (true) {
            if (!gotDeviceId) {
                generator.nextBytes(deviceIdBytes);
                deviceId = Base64.encodeBase64String(deviceIdBytes);
                deviceIdHash = Base64.encodeBase64URLSafeString(Utils.hashSHA256Bytes(deviceIdBytes));
            }
            String codeId = Utils.getUUID();

            byte[] linkCodeBytes = Utils.hmacSHA256(deviceIdBytes, userInputCode);
            byte[] linkCodeHashBytes = Utils.hashSHA256Bytes(linkCodeBytes);

            String linkCode = Base64.encodeBase64URLSafeString(linkCodeBytes);
            String linkCodeHash = Base64.encodeBase64String(linkCodeHashBytes);

            long createdAt = System.currentTimeMillis();

            PasswordlessCode code = new PasswordlessCode(codeId, deviceIdHash, linkCodeHash, createdAt);
            try {
                if (!gotDeviceId) {
                    passwordlessStorage.createDeviceWithCode(email, phoneNumber, code);
                } else {
                    passwordlessStorage.createCode(code);
                }
                return new CreateCodeResponse(deviceIdHash, codeId, deviceId, userInputCode, linkCode, createdAt);
            } catch (DuplicateLinkCodeHashException e) {
                if (gotDeviceId && gotUserInputCode) {
                    // We only need to rethrow if the user supplied both the deviceId and the userInputCode,
                    // because in that case the linkCodeHash will always be the same.
                    throw e;
                }
                // It's retrieable otherwise
            } catch (UnknownDeviceIdHash e) {
                throw new RestartFlowException();
            } catch (DuplicateCodeIdException | DuplicateDeviceIdHashException e) {
                // These are retryable, so ignored here.
            }
        }
    }

    private static String generateUserInputCode() {
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

    public static ConsumeCodeResponse consumeCode(Main main, String deviceId, String userInputCode, String linkCode)
            throws RestartFlowException, ExpiredUserInputCodeException, IncorrectUserInputCodeException,
            StorageTransactionLogicException, StorageQueryException, NoSuchAlgorithmException, InvalidKeyException {
        PasswordlessSQLStorage passwordlessStorage = StorageLayer.getPasswordlessStorage(main);
        long passwordlessCodeLifetime = Config.getConfig(main).getPasswordlessCodeLifetime();
        String deviceIdHash;
        String linkCodeHash;
        if (linkCode != null) {
            byte[] linkCodeBytes = Base64.decodeBase64URLSafe(linkCode);
            byte[] linkCodeHashBytes = Utils.hashSHA256Bytes(linkCodeBytes);
            linkCodeHash = Base64.encodeBase64String(linkCodeHashBytes);

            PasswordlessCode code = passwordlessStorage.getCodeByLinkCodeHash(linkCodeHash);
            if (code == null || code.createdAt < System.currentTimeMillis() - passwordlessCodeLifetime) {
                throw new RestartFlowException();
            }
            deviceIdHash = code.deviceIdHash;
        } else {
            byte[] deviceIdBytes = Base64.decodeBase64(deviceId);
            deviceIdHash = Base64.encodeBase64URLSafeString(Utils.hashSHA256Bytes(deviceIdBytes));

            byte[] linkCodeBytes = Utils.hmacSHA256(deviceIdBytes, userInputCode);
            byte[] linkCodeHashBytes = Utils.hashSHA256Bytes(linkCodeBytes);
            linkCodeHash = Base64.encodeBase64String(linkCodeHashBytes);
        }
        int maxCodeInputAttempts = Config.getConfig(main).getPasswordlessMaxCodeInputAttempts();
        PasswordlessDevice consumedDevice;
        try {
            consumedDevice = passwordlessStorage.startTransaction(con -> {
                PasswordlessDevice device = passwordlessStorage.getDevice_Transaction(con, deviceIdHash);
                if (device == null) {
                    throw new StorageTransactionLogicException(new RestartFlowException());
                }
                if (device.failedAttempts >= maxCodeInputAttempts) {
                    passwordlessStorage.deleteDevice_Transaction(con, deviceIdHash);
                    passwordlessStorage.commitTransaction(con);
                    throw new StorageTransactionLogicException(new RestartFlowException());
                }
                PasswordlessCode code = passwordlessStorage.getCodeByLinkCodeHash_Transaction(con, linkCodeHash);
                if (code == null || code.createdAt < System.currentTimeMillis() - passwordlessCodeLifetime) {
                    if (deviceId != null) {
                        if (device.failedAttempts + 1 == maxCodeInputAttempts) {
                            passwordlessStorage.deleteDevice_Transaction(con, deviceIdHash);
                            passwordlessStorage.commitTransaction(con);
                            throw new StorageTransactionLogicException(new RestartFlowException());
                        } else {
                            passwordlessStorage.incrementDeviceFailedAttemptCount_Transaction(con, deviceIdHash);
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

                // Code exists and is valid
                UserInfo user = device.email != null ? passwordlessStorage.getUserByEmail(device.email)
                        : passwordlessStorage.getUserByPhoneNumber(device.phoneNumber);

                if (user == null) {
                    if (device.email != null) {
                        passwordlessStorage.deleteDevicesByEmail_Transaction(con, device.email);
                    } else if (device.phoneNumber != null) {
                        passwordlessStorage.deleteDevicesByPhoneNumber_Transaction(con, device.phoneNumber);
                    }
                } else {
                    if (user.email != null) {
                        passwordlessStorage.deleteDevicesByEmail_Transaction(con, user.email);
                    }
                    if (user.phoneNumber != null) {
                        passwordlessStorage.deleteDevicesByPhoneNumber_Transaction(con, user.phoneNumber);
                    }
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
            throw e;
        }

        UserInfo user = consumedDevice.email != null ? passwordlessStorage.getUserByEmail(consumedDevice.email)
                : passwordlessStorage.getUserByPhoneNumber(consumedDevice.phoneNumber);
        if (user == null) {
            while (true) {
                try {
                    if (user == null) {
                        String userId = Utils.getUUID();
                        long timeJoined = System.currentTimeMillis();
                        user = new UserInfo(userId, consumedDevice.email, consumedDevice.phoneNumber, timeJoined);
                        passwordlessStorage.createUser(user);
                        return new ConsumeCodeResponse(true, user);
                    }
                } catch (DuplicateEmailException | DuplicatePhoneNumberException e) {
                    throw new RestartFlowException();
                } catch (DuplicateUserIdException e) {
                    // We can retry..
                }
            }
        }
        return new ConsumeCodeResponse(false, user);
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
}
