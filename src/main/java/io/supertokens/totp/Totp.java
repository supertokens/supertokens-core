package io.supertokens.totp;

import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;
import io.supertokens.Main;
import io.supertokens.config.Config;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.mfa.Mfa;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.StorageUtils;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.totp.TOTPDevice;
import io.supertokens.pluginInterface.totp.TOTPUsedCode;
import io.supertokens.pluginInterface.totp.exception.DeviceAlreadyExistsException;
import io.supertokens.pluginInterface.totp.exception.UnknownDeviceException;
import io.supertokens.pluginInterface.totp.exception.UnknownTotpUserIdException;
import io.supertokens.pluginInterface.totp.exception.UsedCodeAlreadyExistsException;
import io.supertokens.pluginInterface.totp.sqlStorage.TOTPSQLStorage;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.totp.exceptions.InvalidTotpException;
import io.supertokens.totp.exceptions.LimitReachedException;
import org.apache.commons.codec.binary.Base32;
import org.jetbrains.annotations.TestOnly;

import javax.crypto.KeyGenerator;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;

public class Totp {
    private static String generateSecret() throws NoSuchAlgorithmException {
        // Reference: https://github.com/jchambers/java-otp
        final String TOTP_ALGORITHM = "HmacSHA1";

        final KeyGenerator keyGenerator = KeyGenerator.getInstance(TOTP_ALGORITHM);
        keyGenerator.init(160); // 160 bits = 20 bytes

        return new Base32().encodeToString(keyGenerator.generateKey().getEncoded());
    }

    private static boolean checkCode(TOTPDevice device, String code) {
        final TimeBasedOneTimePasswordGenerator totp = new TimeBasedOneTimePasswordGenerator(
                Duration.ofSeconds(device.period), 6);

        byte[] keyBytes = new Base32().decode(device.secretKey);
        Key key = new SecretKeySpec(keyBytes, "HmacSHA1");

        final int period = device.period;
        final int skew = device.skew;

        // Check if code is valid for any of the time periods in the skew:
        for (int i = -skew; i <= skew; i++) {
            try {
                if (totp.generateOneTimePasswordString(key, Instant.now().plusSeconds(i * period)).equals(code)) {
                    return true;
                }
            } catch (InvalidKeyException e) {
                // This should never happen because we are always using a valid secretKey.
                return false;
            }
        }

        return false;
    }

    @TestOnly
    public static TOTPDevice registerDevice(Main main, String userId,
                                            String deviceName, int skew, int period)
            throws StorageQueryException, DeviceAlreadyExistsException, NoSuchAlgorithmException,
            FeatureNotEnabledException {
        try {
            return registerDevice(new AppIdentifier(null, null), StorageLayer.getStorage(main),
                    main, userId, deviceName, skew, period);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    public static TOTPDevice createDevice(Main main, AppIdentifier appIdentifier, Storage storage, String userId,
                                          String deviceName, int skew, int period, String secretKey, boolean verified,
                                          long createdAt)
            throws DeviceAlreadyExistsException, StorageQueryException, FeatureNotEnabledException,
            TenantOrAppNotFoundException {

        Mfa.checkForMFAFeature(appIdentifier, main);

        if (deviceName != null) {
            TOTPSQLStorage totpStorage = StorageUtils.getTOTPStorage(storage);
            try {
                return totpStorage.startTransaction(con -> {
                    try {
                        TOTPDevice existingDevice = totpStorage.getDeviceByName_Transaction(con, appIdentifier, userId,
                                deviceName);
                        if (existingDevice == null) {
                            return totpStorage.createDevice_Transaction(con, appIdentifier, new TOTPDevice(
                                    userId, deviceName, secretKey, period, skew, verified, createdAt
                            ));
                        } else if (!existingDevice.verified) {
                            totpStorage.deleteDevice_Transaction(con, appIdentifier, userId, deviceName);
                            return totpStorage.createDevice_Transaction(con, appIdentifier, new TOTPDevice(
                                    userId, deviceName, secretKey, period, skew, verified, createdAt
                            ));
                        } else {
                            throw new StorageTransactionLogicException(new DeviceAlreadyExistsException());
                        }
                    } catch (TenantOrAppNotFoundException | DeviceAlreadyExistsException e) {
                        throw new StorageTransactionLogicException(e);
                    }
                });
            } catch (StorageTransactionLogicException e) {
                if (e.actualException instanceof DeviceAlreadyExistsException) {
                    throw (DeviceAlreadyExistsException) e.actualException;
                }
                throw new StorageQueryException(e.actualException);
            }
        }

        TOTPSQLStorage totpStorage = StorageUtils.getTOTPStorage(storage);
        TOTPDevice[] devices = totpStorage.getDevices(appIdentifier, userId);
        int verifiedDevicesCount = Arrays.stream(devices).filter(d -> d.verified).toArray().length;

        while (true) {
            try {
                return createDevice(main, appIdentifier, storage,
                        userId,
                        "TOTP Device " + verifiedDevicesCount,
                        skew,
                        period,
                        secretKey,
                        verified,
                        createdAt
                );
            } catch (DeviceAlreadyExistsException e) {
            }
            verifiedDevicesCount++;
        }
    }

    public static TOTPDevice registerDevice(AppIdentifier appIdentifier, Storage storage, Main main, String userId,
                                            String deviceName, int skew, int period)
            throws StorageQueryException, DeviceAlreadyExistsException, NoSuchAlgorithmException,
            FeatureNotEnabledException, TenantOrAppNotFoundException {

        String secretKey = generateSecret();

        return createDevice(main, appIdentifier, storage, userId, deviceName, skew, period, secretKey, false,
                System.currentTimeMillis());
    }

    private static void checkAndStoreCode(TenantIdentifier tenantIdentifier, Storage storage, Main main,
                                          String userId, TOTPDevice[] devices,
                                          String code)
            throws InvalidTotpException, UnknownTotpUserIdException,
            LimitReachedException, StorageQueryException, StorageTransactionLogicException,
            TenantOrAppNotFoundException {
        // Note that the TOTP cron runs every 1 hour, so all the expired tokens can stay
        // in the db for at max 1 hour after expiry.

        // If we filter expired codes in rate limiting logic, then because of
        // differences in expiry time of different codes, we might end up with a
        // situation where:
        // Case 1: users might get released from the rate limiting too early because of
        // some invalid codes in the checking window were expired.
        // Case 2: users might face random rate limiting because if some valid codes
        // expire and if it leads to N contagious invalid
        // codes, then the user will be rate limited for no reason.

        // For examaple, assume 0 means expired; 1 means non-expired:
        // Also, assume that totp_max_attempts is 3, totp_rate_limit_cooldown_time is
        // 15 minutes, and totp_invalid_code_expiry is 5 minutes.

        // Example for Case 1:
        // User is rate limited and the used codes are like this: [1, 1, 0, 0, 0]. Now
        // if 1st zero (invalid code) expires in 5 mins and we filter
        // out expired codes, we'll end up [1, 1, 0, 0]. This doesn't contain 3
        // contiguous invalid codes, so the user will be released from rate limiting in
        // 5 minutes instead of 15 minutes.

        // Example for Case 2:
        // User has used codes like this: [0, 1, 0, 0].
        // The 1st one (valid code) will expire in merely 1.5 minutes (assuming skew = 2
        // and period = 30s). So now if we filter out expired codes, we'll see
        // [0, 0, 0] and this contains 3 contagious invalid codes, so now the user will
        // be rate limited for no reason.

        // That's why we need to fetch all the codes (expired + non-expired).
        // TOTPUsedCode[] usedCodes =

        TOTPSQLStorage totpSQLStorage = StorageUtils.getTOTPStorage(storage);

        try {
            totpSQLStorage.startTransaction(con -> {
                try {
                    TOTPUsedCode[] usedCodes = totpSQLStorage.getAllUsedCodesDescOrder_Transaction(con,
                            tenantIdentifier, userId);

                    // N represents # of invalid attempts that will trigger rate limiting:
                    int N = Config.getConfig(tenantIdentifier, main).getTotpMaxAttempts(); // (Default 5)

                    // filter only invalid codes, stop at first valid code
                    TOTPUsedCode[] invalidUsedCodes = Arrays.stream(usedCodes).limit(N).takeWhile(
                            usedCode -> !usedCode.isValid).toArray(TOTPUsedCode[]::new);

                    int rateLimitResetTimeInMs = Config.getConfig(tenantIdentifier, main)
                            .getTotpRateLimitCooldownTimeSec() *
                            1000; // (Default 15 mins)

                    // Count how many of the latest invalid codes fall under the rate limiting compared
                    // to the latest invalid attempt
                    long invalidOutOfN = 0;
                    if (invalidUsedCodes.length > 0) {
                        invalidOutOfN = Arrays.stream(invalidUsedCodes).limit(N).takeWhile(
                                usedCode -> usedCode.createdTime >
                                        invalidUsedCodes[0].createdTime - rateLimitResetTimeInMs
                        ).count();
                    }

                    // Check if the user has been rate limited:
                    if (invalidOutOfN == N) {
                        // All of the latest N attempts were invalid:
                        long latestInvalidCodeCreatedTime = invalidUsedCodes[0].createdTime;
                        long now = System.currentTimeMillis();

                        if (now - latestInvalidCodeCreatedTime < rateLimitResetTimeInMs) {
                            // Less than rateLimitResetTimeInMs (default = 15 mins) time has elasped since
                            // the last invalid code:
                            long timeLeftMs = (rateLimitResetTimeInMs - (now - latestInvalidCodeCreatedTime));
                            throw new StorageTransactionLogicException(
                                    new LimitReachedException(timeLeftMs, (int) invalidOutOfN, N));

                            // If we insert the used code here, then it will further delay the user from
                            // being able to login. So not inserting it here.
                        }

                        // since we are past the cool down period, the user can retry all the attempts
                        invalidOutOfN = 0;
                    }

                    if (code.length() > 8) {
                        throw new StorageTransactionLogicException(
                                new InvalidTotpException((int) invalidOutOfN + 1, N));
                    }

                    // Check if the code is valid for any device:
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

                    // Check if the code has been previously used by the user and it was valid (and
                    // is still valid). If so, this could be a replay attack. So reject it.
                    if (isValid) {
                        for (TOTPUsedCode usedCode : usedCodes) {
                            // One edge case is that if the user has 2 devices, and they are used back to
                            // back (within 90 seconds) such that the code of the first device was
                            // regenerated by the second device, then it won't allow the second device's
                            // code to be used until it is expired.
                            // But this would be rare so we can ignore it for now.
                            if (usedCode.code.equals(code) && usedCode.isValid
                                    && usedCode.expiryTime > System.currentTimeMillis()) {
                                isValid = false;
                                // We found a matching device but the code
                                // will be considered invalid here.
                            }
                        }
                    }

                    // Insert the code into the list of used codes:

                    // If device is found, calculate used code expiry time for that device (based on
                    // its period and skew). Otherwise, use the max used code expiry time of all the
                    // devices.
                    int maxUsedCodeExpiry = Arrays.stream(devices)
                            .mapToInt(device -> device.period * (2 * device.skew + 1))
                            .max()
                            .orElse(0);
                    int expireInSec = (matchingDevice != null)
                            ? matchingDevice.period * (2 * matchingDevice.skew + 1)
                            : maxUsedCodeExpiry;

                    long now = System.currentTimeMillis();
                    TOTPUsedCode newCode = new TOTPUsedCode(userId,
                            code,
                            isValid, now + 1000L * expireInSec, now);
                    try {
                        totpSQLStorage.insertUsedCode_Transaction(con, tenantIdentifier, newCode);
                        totpSQLStorage.commitTransaction(con);
                    } catch (UnknownTotpUserIdException e) {
                        throw new StorageTransactionLogicException(e);
                    } catch (UsedCodeAlreadyExistsException e) {
                        throw new StorageTransactionLogicException(new InvalidTotpException((int) invalidOutOfN, N));
                    }

                    if (!isValid) {
                        // transaction has been committed, so we can directly throw the exception:
                        throw new StorageTransactionLogicException(
                                new InvalidTotpException((int) invalidOutOfN + 1, N));
                    }

                    return null;
                } catch (TenantOrAppNotFoundException e) {
                    throw new StorageTransactionLogicException(e);
                }
            });
            return; // exit the while loop
        } catch (StorageTransactionLogicException e) {
            // throwing errors will also help exit the while loop:
            if (e.actualException instanceof TenantOrAppNotFoundException) {
                throw (TenantOrAppNotFoundException) e.actualException;
            } else if (e.actualException instanceof LimitReachedException) {
                throw (LimitReachedException) e.actualException;
            } else if (e.actualException instanceof InvalidTotpException) {
                throw (InvalidTotpException) e.actualException;
            } else if (e.actualException instanceof UnknownTotpUserIdException) {
                throw (UnknownTotpUserIdException) e.actualException;
            } else {
                throw e;
            }
        }
    }

    @TestOnly
    public static boolean verifyDevice(Main main,
                                       String userId, String deviceName, String code)
            throws UnknownDeviceException, InvalidTotpException,
            LimitReachedException, StorageQueryException, StorageTransactionLogicException {
        try {
            return verifyDevice(new TenantIdentifier(null, null, null),
                    StorageLayer.getStorage(main), main, userId, deviceName, code);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    public static boolean verifyDevice(TenantIdentifier tenantIdentifier, Storage storage, Main main,
                                       String userId, String deviceName, String code)
            throws UnknownDeviceException, InvalidTotpException,
            LimitReachedException, StorageQueryException, StorageTransactionLogicException,
            TenantOrAppNotFoundException {
        // Here boolean return value tells whether the device has been
        // newly verified (true) OR it was already verified (false)

        TOTPSQLStorage totpStorage = StorageUtils.getTOTPStorage(storage);
        TOTPDevice matchingDevice = null;

        // Here one race condition is that the same device
        // is to be verified twice in parallel. In that case,
        // both the API calls will return true, but that's okay.

        // Check if the user has any devices:
        TOTPDevice[] devices = totpStorage.getDevices(tenantIdentifier.toAppIdentifier(), userId);
        if (devices.length == 0) {
            throw new UnknownDeviceException();
        }

        // Check if the requested device exists:
        for (TOTPDevice device : devices) {
            if (device.deviceName.equals(deviceName)) {
                matchingDevice = device;
                if (device.verified) {
                    return false; // Was already verified
                }
                break;
            }
        }

        // No device found:
        if (matchingDevice == null) {
            throw new UnknownDeviceException();
        }

        // At this point, even if device is suddenly deleted/renamed by another API
        // call. We will still check the code against the new set of devices and store
        // it in the used codes table. However, the device will not be marked as
        // verified in the devices table (because it was deleted/renamed). So the user
        // gets a UnknownDevceException.
        // This behaviour is okay so we can ignore it.
        try {
            checkAndStoreCode(tenantIdentifier, storage, main, userId, new TOTPDevice[]{matchingDevice}, code);
        } catch (UnknownTotpUserIdException e) {
            // User must have deleted the device in parallel.
            throw new UnknownDeviceException();
        }
        // Will reach here only if the code is valid:
        totpStorage.markDeviceAsVerified(tenantIdentifier.toAppIdentifier(), userId, deviceName);
        return true; // Newly verified
    }

    @TestOnly
    public static void verifyCode(Main main, String userId, String code)
            throws InvalidTotpException, UnknownTotpUserIdException, LimitReachedException,
            StorageQueryException, StorageTransactionLogicException, FeatureNotEnabledException {
        try {
            verifyCode(new TenantIdentifier(null, null, null), StorageLayer.getStorage(main), main,
                    userId, code);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void verifyCode(TenantIdentifier tenantIdentifier, Storage storage, Main main, String userId,
                                  String code)
            throws InvalidTotpException, UnknownTotpUserIdException, LimitReachedException,
            StorageQueryException, StorageTransactionLogicException, FeatureNotEnabledException,
            TenantOrAppNotFoundException {

        Mfa.checkForMFAFeature(tenantIdentifier.toAppIdentifier(), main);

        TOTPSQLStorage totpStorage = StorageUtils.getTOTPStorage(storage);

        // Check if the user has any devices:
        TOTPDevice[] devices = totpStorage.getDevices(tenantIdentifier.toAppIdentifier(), userId);
        if (devices.length == 0) {
            // No devices found. So we can't verify the code anyway.
            throw new UnknownTotpUserIdException();
        }

        // Filter out unverified devices:
        devices = Arrays.stream(devices).filter(device -> device.verified).toArray(TOTPDevice[]::new);

        // At this point, even if some of the devices are suddenly deleted/renamed by
        // another API call. We will still check the code against the updated set of
        // devices and store it in the used codes table. This behaviour is okay so we
        // can ignore it.

        // UnknownTotpUserIdException will be thrown when
        // the User has deleted the device in parallel
        // since they cannot un-verify a device (no API exists)
        checkAndStoreCode(tenantIdentifier, storage, main, userId, devices, code);
    }

    @TestOnly
    public static void removeDevice(Main main, String userId,
                                    String deviceName)
            throws StorageQueryException, UnknownDeviceException,
            StorageTransactionLogicException {
        try {
            removeDevice(new AppIdentifier(null, null), StorageLayer.getStorage(main),
                    userId, deviceName);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Delete device and also delete the user if deleting the last device
     */
    public static void removeDevice(AppIdentifier appIdentifier, Storage storage, String userId,
                                    String deviceName)
            throws StorageQueryException, UnknownDeviceException,
            StorageTransactionLogicException, TenantOrAppNotFoundException {
        TOTPSQLStorage totpStorage = StorageUtils.getTOTPStorage(storage);

        try {
            totpStorage.startTransaction(con -> {
                int deletedCount = totpStorage.deleteDevice_Transaction(con, appIdentifier, userId, deviceName);
                if (deletedCount == 0) {
                    throw new StorageTransactionLogicException(new UnknownDeviceException());
                }

                // Some device(s) were deleted. Check if user has any other device left:
                // This also takes a lock on the user devices.
                TOTPDevice[] devices = totpStorage.getDevices_Transaction(con, appIdentifier, userId);
                if (devices.length == 0) {
                    // no device left. delete user
                    totpStorage.removeUser_Transaction(con, appIdentifier, userId);
                }

                totpStorage.commitTransaction(con);
                return null;
            });
            return;
        } catch (StorageTransactionLogicException e) {
            if (e.actualException instanceof UnknownDeviceException) {
                throw (UnknownDeviceException) e.actualException;
            }

            throw e;
        }
    }

    @TestOnly
    public static void updateDeviceName(Main main, String userId,
                                        String oldDeviceName, String newDeviceName)
            throws StorageQueryException, DeviceAlreadyExistsException, UnknownDeviceException {
        try {
            updateDeviceName(new AppIdentifier(null, null), StorageLayer.getStorage(main),
                    userId, oldDeviceName, newDeviceName);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void updateDeviceName(AppIdentifier appIdentifier, Storage storage, String userId,
                                        String oldDeviceName, String newDeviceName)
            throws StorageQueryException, DeviceAlreadyExistsException, UnknownDeviceException,
            TenantOrAppNotFoundException {
        TOTPSQLStorage totpStorage = StorageUtils.getTOTPStorage(storage);
        totpStorage.updateDeviceName(appIdentifier, userId, oldDeviceName, newDeviceName);
    }

    @TestOnly
    public static TOTPDevice[] getDevices(Main main, String userId)
            throws StorageQueryException {
        try {
            return getDevices(new AppIdentifier(null, null), StorageLayer.getStorage(main),
                    userId);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    public static TOTPDevice[] getDevices(AppIdentifier appIdentifier, Storage storage, String userId)
            throws StorageQueryException, TenantOrAppNotFoundException {
        TOTPSQLStorage totpStorage = StorageUtils.getTOTPStorage(storage);

        TOTPDevice[] devices = totpStorage.getDevices(appIdentifier, userId);
        return devices;
    }
}
