package io.supertokens.ee;

public interface Logging {
    void info(String msg, boolean toConsoleAsWell);

    void debug(String msg);
}
