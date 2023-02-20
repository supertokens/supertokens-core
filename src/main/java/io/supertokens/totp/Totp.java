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

        TOTPSQLStorage totpStorage = StorageLayer.getTOTPStorage(main);

        boolean deviceAlreadyVerified = totpStorage.markDeviceAsVerified(userId, deviceName);

        if (deviceAlreadyVerified)
            return true;

        try {
            verifyCode(main, userId, code, true);
            return false;
        } catch (LimitReachedException e) {
            throw new InvalidTotpException();
        }
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

        // If allowUnverifiedDevices is false, then remove all unverified devices from
        // the list:
        if (!allowUnverifiedDevices) {
            int verifiedDeviceCount = 0;
            for (TOTPDevice device : devices) {
                if (device.verified) {
                    verifiedDeviceCount++;
                }
            }

            TOTPDevice[] verifiedDevices = new TOTPDevice[verifiedDeviceCount];
            int index = 0;
            for (TOTPDevice device : devices) {
                if (device.verified) {
                    verifiedDevices[index] = device;
                    index++;
                }
            }

            devices = verifiedDevices;
        }

        // Try different devices until we find one that works:
        boolean isValid = false;
        TOTPDevice matchingDevice = null;
        for (TOTPDevice device : devices) {
            // Check if the code is valid for this device:
            if (checkCode(device, code)) {
                isValid = true;
                matchingDevice = device;
                break;
            }
        }

        // Check if the code has been successfully used by the user (for any of their
        // devices):
        TOTPUsedCode[] usedCodes = totpStorage.getUsedCodes(userId);
        for (TOTPUsedCode usedCode : usedCodes) {
            if (usedCode.code.equals(code)) { // FIXME: Only check for the same device for better UX?
                throw new InvalidTotpException();
            }
        }

        // Insert the code into the list of used codes:
        TOTPUsedCode newCode = null;
        if (matchingDevice == null) {
            // TODO: Verify that this doesn't pile up OR gets deleted very quickly:
            int expireAfterSeconds = 60 * 5; // 5 minutes
            newCode = new TOTPUsedCode(userId, null, code, isValid,
                    System.currentTimeMillis() + 1000 * expireAfterSeconds);
        } else {
            int expireAfterSeconds = matchingDevice.period * (2 * matchingDevice.period + 1);
            newCode = new TOTPUsedCode(userId, matchingDevice.deviceName, code, isValid,
                    System.currentTimeMillis() + 1000 * expireAfterSeconds);
        }
        totpStorage.insertUsedCode(newCode);

        if (isValid) {
            return;
        }

        // Now we know that the code is invalid.

        // Check if last N codes are all invalid:
        // Note that usedCodes will get updated when:
        // - A valid code is used: It will break the chain of invalid codes.
        // - Cron job runs: deletes expired codes every 1 hour
        int WINDOW_SIZE = 3;
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
