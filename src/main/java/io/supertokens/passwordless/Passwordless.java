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

import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import org.apache.tomcat.util.codec.binary.Base64;

import io.supertokens.Main;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.passwordless.PasswordlessCode;
import io.supertokens.pluginInterface.passwordless.PasswordlessDevice;
import io.supertokens.pluginInterface.passwordless.sqlStorage.PasswordlessSQLStorage;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.utils.Utils;

public class Passwordless {
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

    public static class DeviceWithCodes {
        public final PasswordlessDevice device;
        public final PasswordlessCode[] codes;

        public DeviceWithCodes(PasswordlessDevice device, PasswordlessCode[] codes) {
            this.device = device;
            this.codes = codes;
        }
    }
}
