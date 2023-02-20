package io.supertokens.totp;

import io.supertokens.Main;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.totp.TOTPDevice;
import io.supertokens.pluginInterface.totp.TOTPUsedCode;
import io.supertokens.pluginInterface.totp.exception.DeviceAlreadyExistsException;
import io.supertokens.pluginInterface.totp.exception.TotpNotEnabledException;
import io.supertokens.pluginInterface.totp.exception.UnknownDeviceException;
import io.supertokens.pluginInterface.totp.sqlStorage.TOTPSQLStorage;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.totp.exceptions.InvalidTotpException;
import io.supertokens.totp.exceptions.LimitReachedException;

public class Totp {

    public static String generateSecret() {
        return "XXXX";
    }

    public static boolean checkCode(TOTPDevice device, String code) {
        if (code.startsWith("XXXX")) {
            return true;
        }
        return false;
    }

    public static String createDevice(Main main, String userId, String deviceName, int skew, int period)
            throws StorageQueryException, DeviceAlreadyExistsException {

        TOTPSQLStorage totpStorage = StorageLayer.getTOTPStorage(main);

        String secret = generateSecret();
        TOTPDevice device = new TOTPDevice(userId, deviceName, secret, period, skew, false);
        totpStorage.createDevice(device);

        return secret;
    }

    public static boolean verifyDevice(Main main, String userId, String deviceName, String code)
            throws StorageQueryException, TotpNotEnabledException, UnknownDeviceException, InvalidTotpException {
        // Here boolean return value tells whether the device was already verified

        boolean deviceAlreadyVerified = false;

        TOTPSQLStorage totpStorage = StorageLayer.getTOTPStorage(main);

        TOTPDevice[] devices = totpStorage.getDevices(userId);
        if (devices.length == 0) {
            throw new TotpNotEnabledException();
        }

        // Find the device:
        TOTPDevice matchingDevice = null;
        for (TOTPDevice device : devices) {
            if (device.deviceName.equals(deviceName)) {
                deviceAlreadyVerified = device.verified;
                matchingDevice = device;
                break;
            }
        }
        if (matchingDevice == null) {
            throw new UnknownDeviceException();
        }

        // Check if the code is unused:
        TOTPUsedCode[] usedCodes = totpStorage.getUsedCodes(userId);
        for (TOTPUsedCode usedCode : usedCodes) {
            if (usedCode.code.equals(code)) {
                throw new InvalidTotpException();
            }
        }

        // Insert the code into the list of used codes:
        TOTPUsedCode newCode = new TOTPUsedCode(userId, code, true, System.currentTimeMillis() + 1000 * 60 * 5);
        totpStorage.insertUsedCode(newCode);

        // Check if the code is valid:
        if (!checkCode(matchingDevice, code)) {
            throw new InvalidTotpException();
        }

        totpStorage.markDeviceAsVerified(userId, deviceName);
        return deviceAlreadyVerified;
    }

    public static void verifyCode(Main main, String userId, String code, boolean allowUnverifiedDevices)
            throws StorageQueryException, TotpNotEnabledException, InvalidTotpException,
            LimitReachedException {
        TOTPSQLStorage totpStorage = StorageLayer.getTOTPStorage(main);

        // Check if the user has any devices:
        TOTPDevice[] devices = totpStorage.getDevices(userId);
        if (devices.length == 0) {
            throw new TotpNotEnabledException();
        }

        // FIXME: Every unused_code should be linked to the device it was used for.
        // Otherwise, if a user has multiple devices, and they use the same code for
        // both,
        // then the code will be considered as used for both devices. This could cause
        // UX
        // issues.
        // If we do this, then it also means that we need to assign a device ID to each
        // device (OR use
        // (userId, deviceName) as the ID)

        // Check if the code has been successfully used by the user (for any device):
        TOTPUsedCode[] usedCodes = totpStorage.getUsedCodes(userId);
        for (TOTPUsedCode usedCode : usedCodes) {
            if (usedCode.code.equals(code) && usedCode.isValidCode) {
                throw new InvalidTotpException();
            }
        }

        // Try different devices until we find one that works:
        boolean isValid = false;
        for (TOTPDevice device : devices) {
            // Check if the code is valid for this device:
            if (checkCode(device, code)) {
                isValid = true;
                break;
            }
        }

        // Insert the code into the list of used codes:
        TOTPUsedCode newCode = new TOTPUsedCode(userId, code, isValid, System.currentTimeMillis() + 1000 * 60 * 5);
        totpStorage.insertUsedCode(newCode);

        if (isValid) {
            return;
        }

        // Check if last 5 codes are all invalid:
        int WINDOW_SIZE = 5;
        int invalidCodes = 0;
        for (int i = usedCodes.length - 1; i >= 0 && i >= usedCodes.length - WINDOW_SIZE; i--) {
            if (!usedCodes[i].isValidCode) {
                invalidCodes++;
            }
        }
        if (invalidCodes == WINDOW_SIZE) {
            throw new LimitReachedException();
        }

        // Code is invalid and the user has not exceeded the limit:
        throw new InvalidTotpException();
    }

    public static void deleteDevice(Main main, String userId, String deviceName)
            throws StorageQueryException, UnknownDeviceException, TotpNotEnabledException {
        TOTPSQLStorage totpStorage = StorageLayer.getTOTPStorage(main);

        try {
            totpStorage.deleteDevice(userId, deviceName);
        } catch (UnknownDeviceException e) {
            // See if any device exists for the user:
            TOTPDevice[] devices = totpStorage.getDevices(userId);
            if (devices.length == 0) {
                throw new TotpNotEnabledException();
            } else {
                throw e;
            }
        }
    }

    public static void updateDeviceName(Main main, String userId, String oldDeviceName, String newDeviceName)
            throws StorageQueryException, DeviceAlreadyExistsException, UnknownDeviceException,
            TotpNotEnabledException {
        TOTPSQLStorage totpStorage = StorageLayer.getTOTPStorage(main);
        try {
            totpStorage.updateDeviceName(userId, oldDeviceName, newDeviceName);
        } catch (UnknownDeviceException e) {
            // See if any device exists for the user:
            TOTPDevice[] devices = totpStorage.getDevices(userId);
            if (devices.length == 0) {
                throw new TotpNotEnabledException();
            } else {
                throw e;
            }
        }
    }

    public static TOTPDevice[] getDevices(Main main, String userId)
            throws StorageQueryException, TotpNotEnabledException {
        TOTPSQLStorage totpStorage = StorageLayer.getTOTPStorage(main);

        TOTPDevice[] devices = totpStorage.getDevices(userId);
        if (devices.length == 0) {
            throw new TotpNotEnabledException();
        }
        return devices;
    }

}
