package io.supertokens.passwordless;

import java.util.Base64;

public class PasswordlessDeviceIdHash {
    private final String encodedValue;

    public PasswordlessDeviceIdHash(byte[] bytes) {
        // We never do anything further with the bytes, so we can just encode, store and reuse it.
        // If we choose to do storage based on bytes this can change.
        this.encodedValue = Base64.getUrlEncoder().encodeToString(bytes).replaceAll("=", "");
    }

    public PasswordlessDeviceIdHash(String encodedValue) {
        this.encodedValue = encodedValue;
    }

    public String encode() {
        return encodedValue;
    }
}