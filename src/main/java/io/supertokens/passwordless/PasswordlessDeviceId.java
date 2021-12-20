package io.supertokens.passwordless;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import io.supertokens.utils.Utils;

public class PasswordlessDeviceId {
    private final byte[] bytes;

    public PasswordlessDeviceId(byte[] bytes) {
        this.bytes = bytes;
    }

    public static PasswordlessDeviceId decodeString(String deviceId) {
        return new PasswordlessDeviceId(Base64.getDecoder().decode(deviceId));
    }

    public String encode() {
        return Base64.getEncoder().encodeToString(bytes);
    }

    public PasswordlessDeviceIdHash getHash() throws NoSuchAlgorithmException {
        return new PasswordlessDeviceIdHash(Utils.hashSHA256Bytes(bytes));
    }

    public PasswordlessLinkCode getLinkCode(PasswordlessLinkCodeSalt linkCodeSalt, String userInputCode)
            throws NoSuchAlgorithmException, InvalidKeyException, IOException {
        // We need the linkCodeSalt while calculating the linkCode because we do not want it to be calculatable on the
        // client-side. If it were, then an attacker could try to guess the userInputCode based on the received deviceId
        // and circumvent the failed attempt count limit by calling the consume endpoint with a linkCode.
        // Since we assume the userInputCode to be low entropy, it's possible it could be brute-forced without setting
        // low limits on the retry count.

        // We mix the salt into the deviceId by concatenating them.
        // HMAC-SHA256 takes 64-byte keys by default (and would hash them otherwise to get 64 bytes)
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        outputStream.write(bytes);
        outputStream.write(linkCodeSalt.bytes);

        return new PasswordlessLinkCode(Utils.hmacSHA256(outputStream.toByteArray(), userInputCode));
    }
}
