package io.supertokens.totp.exceptions;

public class LimitReachedException extends Exception {

    public long retryAfterMs;
    public int currentAttempts;
    public int maxAttempts;

    public LimitReachedException(long retryAfterMs, int currentAttempts, int maxAttempts) {
        super("Retry in " + retryAfterMs + " ms");
        this.retryAfterMs = retryAfterMs;
        this.currentAttempts = currentAttempts;
        this.maxAttempts = maxAttempts;
    }
}
