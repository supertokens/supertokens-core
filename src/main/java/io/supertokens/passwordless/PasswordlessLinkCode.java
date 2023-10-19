package io.supertokens.passwordless;

import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import io.supertokens.passwordless.exceptions.Base64EncodingException;
import io.supertokens.utils.Utils;

public class PasswordlessLinkCode {
    private final byte[] bytes;

    public PasswordlessLinkCode(byte[] bytes) {
        this.bytes = bytes;
    }

    public static PasswordlessLinkCode decodeString(String linkCode) throws Base64EncodingException {
        try {
            return new PasswordlessLinkCode(Base64.getUrlDecoder().decode(linkCode));

        } catch (IllegalArgumentException ex) {
            throw new Base64EncodingException("linkCode");
        }
    }

    public String encode() {
        return Base64.getUrlEncoder().encodeToString(bytes).replaceAll("=", "");
    }

    public PasswordlessLinkCodeHash getHash() throws NoSuchAlgorithmException {
        return new PasswordlessLinkCodeHash(Utils.hashSHA256Bytes(bytes));
    }
}
