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
import io.supertokens.pluginInterface.Storage;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.utils.Utils;
import io.supertokens.webserver.Webserver;
import org.slf4j.LoggerFactory;

public class Logging extends ResourceDistributor.SingletonResource {

    private static final String RESOURCE_ID = "io.supertokens.output.Logging";
    private final Logger infoLogger;
    private final Logger errorLogger;

    private Logging(Main main) {
        this.infoLogger = Config.getConfig(main).getInfoLogPath(main).equals("null")
                ? createLoggerForConsole(main, "io.supertokens.Info." + main.getProcessId())
                : createLoggerForFile(main, Config.getConfig(main).getInfoLogPath(main),
                        "io.supertokens.Info." + main.getProcessId());
        this.errorLogger = Config.getConfig(main).getErrorLogPath(main).equals("null")
                ? createLoggerForConsole(main, "io.supertokens.Error." + main.getProcessId())
                : createLoggerForFile(main, Config.getConfig(main).getErrorLogPath(main),
                        "io.supertokens.Error." + main.getProcessId());
        Storage storage = StorageLayer.getStorage(main);
        if (storage != null) {
            storage.initFileLogging(Config.getConfig(main).getInfoLogPath(main),
                    Config.getConfig(main).getErrorLogPath(main));
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
        return (Logging) main.getResourceDistributor().getResource(RESOURCE_ID);
    }

    public static void initFileLogging(Main main) {
        if (getInstance(main) == null) {
            main.getResourceDistributor().setResource(RESOURCE_ID, new Logging(main));
            StorageLayer.getStorage(main).initFileLogging(Config.getConfig(main).getInfoLogPath(main),
                    Config.getConfig(main).getErrorLogPath(main));
        }
    }

    public static void debug(Main main, String msg) {
        try {
            msg = msg.trim();
            if (getInstance(main) != null) {
                getInstance(main).infoLogger.debug(msg);
            }
        } catch (NullPointerException e) {
            // sometimes logger.debug throws a null pointer exception...
        }
    }

    public static void info(Main main, String msg) {
        try {
            msg = msg.trim();
            systemOut(msg);
            if (getInstance(main) != null) {
                getInstance(main).infoLogger.info(msg);
            }
        } catch (NullPointerException ignored) {
        }
    }

    public static void warn(Main main, String msg) {
        try {
            msg = msg.trim();
            if (getInstance(main) != null) {
                getInstance(main).errorLogger.warn(msg);
            }
        } catch (NullPointerException ignored) {
        }
    }

    public static void error(Main main, String err, boolean toConsoleAsWell) {
        try {
            err = err.trim();
            if (getInstance(main) != null) {
                getInstance(main).errorLogger.error(err);
            }
            if (toConsoleAsWell || getInstance(main) == null) {
                systemErr(err);
            }
        } catch (NullPointerException ignored) {
        }
    }

    public static void error(Main main, String message, boolean toConsoleAsWell, Exception e) {
        try {
            String err = Utils.throwableStacktraceToString(e).trim();
            if (getInstance(main) != null) {
                getInstance(main).errorLogger.error(err);
            } else if (Main.isTesting) {
                systemErr(err);
            }
            if (message != null) {
                message = message.trim();
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
        System.err.println(err);
    }

    public static void stopLogging(Main main) {
        if (getInstance(main) == null) {
            return;
        }
        getInstance(main).infoLogger.detachAndStopAllAppenders();
        getInstance(main).errorLogger.detachAndStopAllAppenders();
        Webserver.getInstance(main).closeLogger();
        Storage storage = StorageLayer.getStorage(main);
        if (storage != null) {
            storage.stopLogging();
        }
    }

    private Logger createLoggerForFile(Main main, String file, String name) {
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        LayoutWrappingEncoder ple = new LayoutWrappingEncoder(main.getProcessId());
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

    private Logger createLoggerForConsole(Main main, String name) {
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        LayoutWrappingEncoder ple = new LayoutWrappingEncoder(main.getProcessId());
        ple.setContext(lc);
        ple.start();
        ConsoleAppender<ILoggingEvent> logConsoleAppender = new ConsoleAppender<>();
        logConsoleAppender.setEncoder(ple);
        logConsoleAppender.setContext(lc);
        logConsoleAppender.start();

        Logger logger = (Logger) LoggerFactory.getLogger(name);
        logger.addAppender(logConsoleAppender);
        logger.setAdditive(false); /* set to true if root should log too */

        return logger;
    }
}
