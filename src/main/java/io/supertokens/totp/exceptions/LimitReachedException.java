package io.supertokens.totp.exceptions;

public class LimitReachedException extends Exception {

    public long retryAfterMs;

    public LimitReachedException(long retryAfterMs) {
        super("Retry in " + retryAfterMs + " ms");
        this.retryAfterMs = retryAfterMs;
    }
}
