/*
 *    Copyright (c) 2020, VRAI Labs and/or its affiliates. All rights reserved.
 *
 *    This software is licensed under the Apache License, Version 2.0 (the
 *    "License") as published by the Apache Software Foundation.
 *
 *    You may not use this file except in compliance with the License. You may
 *    obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *    License for the specific language governing permissions and limitations
 *    under the License.
 */

package io.supertokens.output;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.FileAppender;
import io.supertokens.Main;
import io.supertokens.ResourceDistributor;
import io.supertokens.config.Config;
import io.supertokens.pluginInterface.LOG_LEVEL;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.utils.Utils;
import io.supertokens.version.Version;
import io.supertokens.webserver.Webserver;
import org.slf4j.LoggerFactory;

public class Logging extends ResourceDistributor.SingletonResource {

    private static final String RESOURCE_ID = "io.supertokens.output.Logging";
    private final Logger infoLogger;
    private final Logger errorLogger;

    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_BLACK = "\u001B[30m";
    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_YELLOW = "\u001B[33m";
    public static final String ANSI_BLUE = "\u001B[34m";
    public static final String ANSI_PURPLE = "\u001B[35m";
    public static final String ANSI_CYAN = "\u001B[36m";
    public static final String ANSI_WHITE = "\u001B[37m";

    private Logging(Main main) {
        this.infoLogger = Config.getBaseConfig(main).getInfoLogPath(main).equals("null")
                ? createLoggerForConsole(main, "io.supertokens.Info", LOG_LEVEL.INFO)
                : createLoggerForFile(main, Config.getBaseConfig(main).getInfoLogPath(main),
                "io.supertokens.Info");
        this.errorLogger = Config.getBaseConfig(main).getErrorLogPath(main).equals("null")
                ? createLoggerForConsole(main, "io.supertokens.Error", LOG_LEVEL.ERROR)
                : createLoggerForFile(main, Config.getBaseConfig(main).getErrorLogPath(main),
                "io.supertokens.Error");
        Storage storage = StorageLayer.getBaseStorage(main);
        if (storage != null) {
            storage.initFileLogging(Config.getBaseConfig(main).getInfoLogPath(main),
                    Config.getBaseConfig(main).getErrorLogPath(main));
        }
        try {
            // we wait here for a bit so that the loggers can be properly initialised..
            // as sometimes, when using the logger immediately after, causes a
            // NullPointerException
            Thread.sleep(100);
        } catch (InterruptedException ignored) {
        }
    }

    private static Logging getInstance(Main main) {
        try {
            return (Logging) main.getResourceDistributor()
                    .getResource(new TenantIdentifier(null, null, null), RESOURCE_ID);
        } catch (TenantOrAppNotFoundException e) {
            return null;
        }
    }

    public static void initFileLogging(Main main) {
        main.getResourceDistributor()
                .setResource(new TenantIdentifier(null, null, null), RESOURCE_ID, new Logging(main));
    }

    private static String prependTenantIdentifierToMessage(TenantIdentifier tenantIdentifier, String msg) {
        if (tenantIdentifier == null) {
            tenantIdentifier = TenantIdentifier.BASE_TENANT;
        }
        return "Tenant(" +
                tenantIdentifier.getConnectionUriDomain() +
                ", " +
                tenantIdentifier.getAppId() +
                ", " +
                tenantIdentifier.getTenantId() +
                ") | " +
                msg;
    }

    public static void debug(Main main, TenantIdentifier tenantIdentifier, String msg) {
        if (!Config.getBaseConfig(main).getLogLevels(main).contains(LOG_LEVEL.DEBUG)) {
            return;
        }
        try {
            msg = msg.trim();
            msg = prependTenantIdentifierToMessage(tenantIdentifier, msg);
            if (getInstance(main) != null) {
                getInstance(main).infoLogger.debug(msg);
            }
        } catch (NullPointerException e) {
            // sometimes logger.debug throws a null pointer exception...
        }
    }

    public static void info(Main main, TenantIdentifier tenantIdentifier, String msg, boolean toConsoleAsWell) {
        if (!Config.getBaseConfig(main).getLogLevels(main).contains(LOG_LEVEL.INFO)) {
            return;
        }
        try {
            msg = msg.trim();
            if (toConsoleAsWell) {
                if (tenantIdentifier.equals(TenantIdentifier.BASE_TENANT)) {
                    systemOut(msg);
                } else {
                    systemOut(prependTenantIdentifierToMessage(tenantIdentifier, msg));
                }
            }
            msg = prependTenantIdentifierToMessage(tenantIdentifier, msg);
            if (getInstance(main) != null) {
                getInstance(main).infoLogger.info(msg);
            }
        } catch (NullPointerException ignored) {
        }
    }

    public static void warn(Main main, TenantIdentifier tenantIdentifier, String msg) {
        if (!Config.getBaseConfig(main).getLogLevels(main).contains(LOG_LEVEL.WARN)) {
            return;
        }
        try {
            msg = msg.trim();
            msg = prependTenantIdentifierToMessage(tenantIdentifier, msg);
            if (getInstance(main) != null) {
                getInstance(main).errorLogger.warn(msg);
            }
        } catch (NullPointerException ignored) {
        }
    }

    public static void error(Main main, TenantIdentifier tenantIdentifier, String err, boolean toConsoleAsWell) {
        try {
            if (!Config.getConfig(new TenantIdentifier(null, null, null), main).getLogLevels(main)
                    .contains(LOG_LEVEL.ERROR)) {
                return;
            }
        } catch (Throwable ignored) {
            // if it comes here, it means that the config was not loaded and that we are trying
            // to log some other error. In this case, we want to log it anyway, so we catch any
            // error and continue below.
        }
        try {
            err = err.trim();
            err = prependTenantIdentifierToMessage(tenantIdentifier, err);
            if (getInstance(main) != null) {
                getInstance(main).errorLogger.error(err);
            }
            if (toConsoleAsWell || getInstance(main) == null) {
                systemErr(err);
            }
        } catch (NullPointerException ignored) {
        }
    }

    public static void error(Main main, TenantIdentifier tenantIdentifier, String message, boolean toConsoleAsWell,
                             Exception e) {
        try {
            if (!Config.getConfig(new TenantIdentifier(null, null, null), main).getLogLevels(main)
                    .contains(LOG_LEVEL.ERROR)) {
                return;
            }
        } catch (Throwable ignored) {
            // if it comes here, it means that the config was not loaded and that we are trying
            // to log some other error. In this case, we want to log it anyway, so we catch any
            // error and continue below.
        }
        try {
            String err = Utils.throwableStacktraceToString(e).trim();
            if (getInstance(main) != null) {
                err = prependTenantIdentifierToMessage(tenantIdentifier, err);
                getInstance(main).errorLogger.error(err);
            } else if (Main.isTesting) {
                systemErr(err);
            }
            if (message != null) {
                message = message.trim();
                message = prependTenantIdentifierToMessage(tenantIdentifier, message);
                if (getInstance(main) != null) {
                    getInstance(main).errorLogger.error(message);
                }
                if (toConsoleAsWell || getInstance(main) == null) {
                    systemErr(message);
                }
            }
        } catch (NullPointerException ignored) {
        }
    }

    private static void systemOut(String msg) {
        if (!Main.makeConsolePrintSilent) {
            System.out.println(msg);
        }
    }

    private static void systemErr(String err) {
        System.err.println(Logging.ANSI_RED + err + Logging.ANSI_RESET);
    }

    public static void stopLogging(Main main) {
        if (getInstance(main) == null) {
            return;
        }
        getInstance(main).infoLogger.getLoggerContext().stop();
        getInstance(main).errorLogger.getLoggerContext().stop();
        getInstance(main).infoLogger.getLoggerContext().getStatusManager().clear();
        getInstance(main).errorLogger.getLoggerContext().getStatusManager().clear();
        getInstance(main).infoLogger.detachAndStopAllAppenders();
        getInstance(main).errorLogger.detachAndStopAllAppenders();
        Webserver.getInstance(main).closeLogger();
        StorageLayer.stopLogging(main);
    }

    private Logger createLoggerForFile(Main main, String file, String name) {
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        LayoutWrappingEncoder ple = new LayoutWrappingEncoder(main.getProcessId(),
                Version.getVersion(main).getCoreVersion());
        ple.setContext(lc);
        ple.start();
        FileAppender<ILoggingEvent> fileAppender = new FileAppender<>();
        fileAppender.setFile(file);
        fileAppender.setEncoder(ple);
        fileAppender.setContext(lc);
        fileAppender.start();

        Logger logger = (Logger) LoggerFactory.getLogger(name);
        logger.addAppender(fileAppender);
        logger.setAdditive(false); /* set to true if root should log too */

        return logger;
    }

    private Logger createLoggerForConsole(Main main, String name, LOG_LEVEL logLevel) {
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        LayoutWrappingEncoder ple = new LayoutWrappingEncoder(main.getProcessId(),
                Version.getVersion(main).getCoreVersion());
        ple.setContext(lc);
        ple.start();
        ConsoleAppender<ILoggingEvent> logConsoleAppender = new ConsoleAppender<>();
        logConsoleAppender.setTarget(logLevel == LOG_LEVEL.ERROR ? "System.err" : "System.out");
        logConsoleAppender.setEncoder(ple);
        logConsoleAppender.setContext(lc);
        logConsoleAppender.start();

        Logger logger = (Logger) LoggerFactory.getLogger(name);
        logger.addAppender(logConsoleAppender);
        logger.setAdditive(false); /* set to true if root should log too */

        return logger;
    }
}
