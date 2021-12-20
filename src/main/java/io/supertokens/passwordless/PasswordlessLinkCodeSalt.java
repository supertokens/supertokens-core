package io.supertokens.passwordless;

import java.util.Base64;

import io.supertokens.passwordless.exceptions.Base64EncodingException;

public class PasswordlessLinkCodeSalt {
    public final byte[] bytes;

    public PasswordlessLinkCodeSalt(byte[] bytes) {
        this.bytes = bytes;
    }

    public static PasswordlessLinkCodeSalt decodeString(String linkCodeSalt) throws Base64EncodingException {
        try {
            return new PasswordlessLinkCodeSalt(Base64.getDecoder().decode(linkCodeSalt));
        } catch (IllegalArgumentException ex) {
            throw new Base64EncodingException("LinkCodeSalt");
        }
    }

    public String encode() {
        return Base64.getEncoder().encodeToString(bytes);
    }
}
