package io.supertokens.totp.exceptions;

public class LimitReachedException extends Exception {

    public long retryInMs;

    public LimitReachedException(long retryInSeconds) {
        super("Retry in " + retryInSeconds + " seconds");
        this.retryInMs = retryInSeconds;
    }
}
