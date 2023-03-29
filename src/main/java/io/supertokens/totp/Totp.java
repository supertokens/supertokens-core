package io.supertokens.totp;

import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;

import javax.crypto.KeyGenerator;
import javax.crypto.spec.SecretKeySpec;

import io.supertokens.Main;
import io.supertokens.config.Config;

import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;

import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlag;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.totp.TOTPDevice;
import io.supertokens.pluginInterface.totp.TOTPStorage;
import io.supertokens.pluginInterface.totp.TOTPUsedCode;
import io.supertokens.pluginInterface.totp.exception.DeviceAlreadyExistsException;
import io.supertokens.pluginInterface.totp.exception.TotpNotEnabledException;
import io.supertokens.pluginInterface.totp.exception.UnknownDeviceException;
import io.supertokens.pluginInterface.totp.exception.UsedCodeAlreadyExistsException;
import io.supertokens.pluginInterface.totp.sqlStorage.TOTPSQLStorage;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.totp.exceptions.InvalidTotpException;
import io.supertokens.totp.exceptions.LimitReachedException;
import org.apache.commons.codec.binary.Base32;
import org.jetbrains.annotations.TestOnly;

import static io.supertokens.featureflag.FeatureFlag.getInstance;

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

    private static boolean isTotpEnabled(Main main) throws StorageQueryException {
        EE_FEATURES[] features = FeatureFlag.getInstance(main).getEnabledFeatures();
        for (EE_FEATURES f : features) {
            if (f == EE_FEATURES.TOTP) {
                return true;
            }
        }
        return false;
    }


    public static TOTPDevice registerDevice(Main main, String userId, String deviceName, int skew, int period)
            throws StorageQueryException, DeviceAlreadyExistsException, NoSuchAlgorithmException,
            FeatureNotEnabledException {

        if (!isTotpEnabled(main)){
            throw new FeatureNotEnabledException(
                    "TOTP feature is not enabled. Please subscribe to a SuperTokens core license key to enable this feature.");
        }

        TOTPSQLStorage totpStorage = StorageLayer.getTOTPStorage(main);

        String secret = generateSecret();
        TOTPDevice device = new TOTPDevice(userId, deviceName, secret, period, skew, false);
        totpStorage.createDevice(device);

        return device;
    }

    private static void checkAndStoreCode(Main main, TOTPStorage totpStorage, String userId, TOTPDevice[] devices,
            String code)
            throws InvalidTotpException, TotpNotEnabledException,
            LimitReachedException, StorageQueryException, StorageTransactionLogicException {
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

        TOTPSQLStorage totpSQLStorage = (TOTPSQLStorage) totpStorage;

        while (true) {
            try {
                totpSQLStorage.startTransaction(con -> {
                    TOTPUsedCode[] usedCodes = totpSQLStorage.getAllUsedCodesDescOrder_Transaction(con,
                            userId);

                    // N represents # of invalid attempts that will trigger rate limiting:
                    int N = Config.getConfig(main).getTotpMaxAttempts(); // (Default 5)
                    // Count # of contiguous invalids in latest N attempts (stop at first valid):
                    long invalidOutOfN = Arrays.stream(usedCodes).limit(N).takeWhile(usedCode -> !usedCode.isValid)
                            .count();
                    int rateLimitResetTimeInMs = Config.getConfig(main).getTotpRateLimitCooldownTimeSec() * 1000; // (Default
                    // 15 mins)

                    // Check if the user has been rate limited:
                    if (invalidOutOfN == N) {
                        // All of the latest N attempts were invalid:
                        long latestInvalidCodeCreatedTime = usedCodes[0].createdTime;
                        long now = System.currentTimeMillis();

                        if (now - latestInvalidCodeCreatedTime < rateLimitResetTimeInMs) {
                            // Less than rateLimitResetTimeInMs (default = 15 mins) time has elasped since
                            // the last invalid code:
                            long timeLeftMs = (rateLimitResetTimeInMs - (now - latestInvalidCodeCreatedTime));
                            throw new StorageTransactionLogicException(new LimitReachedException(timeLeftMs));

                            // If we insert the used code here, then it will further delay the user from
                            // being able to login. So not inserting it here.
                        }
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
                    int expireInSec = (matchingDevice != null) ? matchingDevice.period * (2 * matchingDevice.skew + 1)
                            : maxUsedCodeExpiry;

                    long now = System.currentTimeMillis();
                    TOTPUsedCode newCode = new TOTPUsedCode(userId, code, isValid, now + 1000 * expireInSec, now);
                    try {
                        totpSQLStorage.insertUsedCode_Transaction(con, newCode);
                        totpSQLStorage.commitTransaction(con);
                    } catch (UsedCodeAlreadyExistsException | TotpNotEnabledException e) {
                        throw new StorageTransactionLogicException(e);
                    }

                    if (!isValid) {
                        // transaction has been committed, so we can directly throw the exception:
                        throw new StorageTransactionLogicException(new InvalidTotpException());
                    }

                    return null;
                });
                return; // exit the while loop
            } catch (StorageTransactionLogicException e) {
                // throwing errors will also help exit the while loop:
                if (e.actualException instanceof LimitReachedException) {
                    throw (LimitReachedException) e.actualException;
                } else if (e.actualException instanceof InvalidTotpException) {
                    throw (InvalidTotpException) e.actualException;
                } else if (e.actualException instanceof TotpNotEnabledException) {
                    throw (TotpNotEnabledException) e.actualException;
                } else if (e.actualException instanceof UsedCodeAlreadyExistsException) {
                    // retry the transaction after a small delay:
                    int delayInMs = (int) (Math.random() * 10 + 1);
                    try {
                        Thread.sleep(delayInMs);
                        continue;
                    } catch (InterruptedException err) {
                        // ignore the error and retry
                        continue;
                    }
                } else {
                    throw e;
                }
            }
        }
    }

    public static boolean verifyDevice(Main main, String userId, String deviceName, String code)
            throws TotpNotEnabledException, UnknownDeviceException, InvalidTotpException,
            LimitReachedException, StorageQueryException, StorageTransactionLogicException {
        // Here boolean return value tells whether the device has been
        // newly verified (true) OR it was already verified (false)

        TOTPSQLStorage totpStorage = StorageLayer.getTOTPStorage(main);
        TOTPDevice matchingDevice = null;

        // Here one race condition is that the same device
        // is to be verified twice in parallel. In that case,
        // both the API calls will return true, but that's okay.

        // Check if the user has any devices:
        TOTPDevice[] devices = totpStorage.getDevices(userId);
        if (devices.length == 0) {
            throw new TotpNotEnabledException();
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
        checkAndStoreCode(main, totpStorage, userId, new TOTPDevice[] { matchingDevice }, code);
        // Will reach here only if the code is valid:
        totpStorage.markDeviceAsVerified(userId, deviceName);
        return true; // Newly verified
    }

    public static void verifyCode(Main main, String userId, String code, boolean allowUnverifiedDevices)
            throws TotpNotEnabledException, InvalidTotpException, LimitReachedException,
            StorageQueryException, StorageTransactionLogicException, FeatureNotEnabledException {

        if (!isTotpEnabled(main)){
            throw new FeatureNotEnabledException(
                    "TOTP feature is not enabled. Please subscribe to a SuperTokens core license key to enable this feature.");
        }

        TOTPSQLStorage totpStorage = StorageLayer.getTOTPStorage(main);

        // Check if the user has any devices:
        TOTPDevice[] devices = totpStorage.getDevices(userId);
        if (devices.length == 0) {
            throw new TotpNotEnabledException();
        }

        // Filter out unverified devices:
        if (!allowUnverifiedDevices) {
            devices = Arrays.stream(devices).filter(device -> device.verified).toArray(TOTPDevice[]::new);
        }

        // At this point, even if some of the devices are suddenly deleted/renamed by
        // another API call. We will still check the code against the updated set of
        // devices and store it in the used codes table. This behaviour is okay so we
        // can ignore it.
        checkAndStoreCode(main, totpStorage, userId, devices, code);
    }

    /** Delete device and also delete the user if deleting the last device */
    public static void removeDevice(Main main, String userId, String deviceName)
            throws StorageQueryException, UnknownDeviceException, TotpNotEnabledException,
            StorageTransactionLogicException {
        TOTPSQLStorage storage = StorageLayer.getTOTPStorage(main);

        try {
            storage.startTransaction(con -> {
                int deletedCount = storage.deleteDevice_Transaction(con, userId, deviceName);
                if (deletedCount == 0) {
                    throw new StorageTransactionLogicException(new UnknownDeviceException());
                }

                // Some device(s) were deleted. Check if user has any other device left:
                // This also takes a lock on the user devices.
                TOTPDevice[] devices = storage.getDevices_Transaction(con, userId);
                if (devices.length == 0) {
                    // no device left. delete user
                    storage.removeUser_Transaction(con, userId);
                }

                storage.commitTransaction(con);
                return null;
            });
            return;
        } catch (StorageTransactionLogicException e) {
            if (e.actualException instanceof UnknownDeviceException) {
                // Check if any device exists for the user:
                TOTPDevice[] devices = storage.getDevices(userId);
                if (devices.length == 0) {
                    throw new TotpNotEnabledException();
                }

                throw (UnknownDeviceException) e.actualException;
            }

            throw e;
        }
    }

    public static void updateDeviceName(Main main, String userId, String oldDeviceName, String newDeviceName)
            throws StorageQueryException, DeviceAlreadyExistsException, UnknownDeviceException,
            TotpNotEnabledException {
        TOTPSQLStorage totpStorage = StorageLayer.getTOTPStorage(main);
        try {
            totpStorage.updateDeviceName(userId, oldDeviceName, newDeviceName);
        } catch (UnknownDeviceException e) {
            // Check if any device exists for the user:
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
