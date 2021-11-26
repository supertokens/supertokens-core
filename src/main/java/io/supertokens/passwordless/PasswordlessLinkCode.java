package io.supertokens.passwordless;

import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import io.supertokens.utils.Utils;

public class PasswordlessLinkCode {
    private final byte[] bytes;

    public PasswordlessLinkCode(byte[] bytes) {
        this.bytes = bytes;
    }

    public static PasswordlessLinkCode decodeString(String linkCode) {
        return new PasswordlessLinkCode(Base64.getUrlDecoder().decode(linkCode));
    }

    public String encode() {
        return Base64.getUrlEncoder().encodeToString(bytes);
    }

    public PasswordlessLinkCodeHash getHash() throws NoSuchAlgorithmException {
        return new PasswordlessLinkCodeHash(Utils.hashSHA256Bytes(bytes));
    }
}
