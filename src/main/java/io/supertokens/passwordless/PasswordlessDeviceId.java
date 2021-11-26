package io.supertokens.passwordless;

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

    public PasswordlessLinkCode getLinkCode(String userInputCode) throws NoSuchAlgorithmException, InvalidKeyException {
        return new PasswordlessLinkCode(Utils.hmacSHA256(bytes, userInputCode));
    }
}
