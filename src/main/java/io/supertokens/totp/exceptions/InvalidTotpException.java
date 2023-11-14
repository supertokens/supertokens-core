package io.supertokens.totp.exceptions;

public class InvalidTotpException extends Exception {
    public int currentAttempts;
    public int maxAttempts;

    public InvalidTotpException(int currentAttempts, int maxAttempts) {
        super("Invalid totp");
        this.currentAttempts = currentAttempts;
        this.maxAttempts = maxAttempts;
    }
}
