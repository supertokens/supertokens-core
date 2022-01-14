package io.supertokens.passwordless;

import java.util.Base64;

public class PasswordlessLinkCodeHash {
    private final String encodedValue;

    public PasswordlessLinkCodeHash(byte[] bytes) {
        // We never do anything further with the bytes, so we can just encode, store and reuse it.
        // If we choose to do storage based on bytes this can change.
        this.encodedValue = Base64.getUrlEncoder().encodeToString(bytes);
    }

    public String encode() {
        return encodedValue;
    }
}