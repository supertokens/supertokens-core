package io.supertokens.totp.exceptions;

public class LimitReachedException extends Exception {

    public int retryInSeconds;

    public LimitReachedException(int retryInSeconds) {
        super("Retry in " + retryInSeconds + " seconds");
        this.retryInSeconds = retryInSeconds;
    }
}
