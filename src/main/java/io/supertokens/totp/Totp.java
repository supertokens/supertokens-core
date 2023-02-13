package io.supertokens.totp;

import java.io.IOException;

import io.supertokens.Main;
import io.supertokens.pluginInterface.totp.TOTPDevice;
import io.supertokens.pluginInterface.totp.sqlStorage.TOTPSQLStorage;
import io.supertokens.storageLayer.StorageLayer;

public class Totp {

    public static CreateDeviceResponse createDevice(Main main, String userId, String deviceName, int skew, int period)
            throws IOException {

        TOTPSQLStorage totpStorage = StorageLayer.getTOTPStorage(main);
        String secret = GenerateDeviceSecret.generate();

        if (userId == null || deviceName == null || secret == null) {
            throw new IllegalArgumentException("userId, deviceName and secret cannot be null");
        }

        try {
            TOTPDevice device = new TOTPDevice(userId, deviceName, secret, skew, period, false);
            totpStorage.createDevice(device);
        } catch (Exception e) {
            throw new IOException(e);
        }

        return new CreateDeviceResponse("deviceName", secret);
    }

    public static void markDeviceAsVerified(Main main, String userId, String deviceName) throws IOException {
        TOTPSQLStorage totpStorage = StorageLayer.getTOTPStorage(main);
        try {
            totpStorage.markDeviceAsVerified(userId, deviceName);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    private static class GenerateDeviceSecret {
        // private final String secret;

        // private GenerateDeviceSecret(String secret) {
        // this.secret = secret;
        // }

        public static String generate() {
            return "XXXX";
        }
    }

    public static class CreateDeviceResponse {
        public String deviceName;
        public String secret;

        public CreateDeviceResponse(String deviceName, String secret) {
            this.deviceName = deviceName;
            this.secret = secret;
        }

    }
}
