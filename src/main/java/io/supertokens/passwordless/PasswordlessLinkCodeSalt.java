package io.supertokens.passwordless;

import java.util.Base64;

public class PasswordlessLinkCodeSalt {
    public final byte[] bytes;

    public PasswordlessLinkCodeSalt(byte[] bytes) {
        this.bytes = bytes;
    }

    public static PasswordlessLinkCodeSalt decodeString(String linkCode) {
        return new PasswordlessLinkCodeSalt(Base64.getDecoder().decode(linkCode));
    }

    public String encode() {
        return Base64.getEncoder().encodeToString(bytes);
    }
}
