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

}
