package io.supertokens.ee;

public interface Logging {
    void info(String msg, boolean toConsoleAsWell);

    void debug(String msg);

    void error(String message, boolean toConsoleAsWell, Exception e);
}
