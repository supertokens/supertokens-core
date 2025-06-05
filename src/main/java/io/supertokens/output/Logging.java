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
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.samplers.Sampler;
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
import org.slf4j.bridge.SLF4JBridgeHandler;
import org.slf4j.event.Level;

import java.util.concurrent.TimeUnit;

import static io.opentelemetry.semconv.ServiceAttributes.SERVICE_NAME;

public class Logging extends ResourceDistributor.SingletonResource {

    private static final String RESOURCE_ID = "io.supertokens.output.Logging";
    private final Logger infoLogger;
    private final Logger errorLogger;
    private final Logger otelLogger;
    private final io.opentelemetry.api.logs.Logger otelAppender;
    private static final org.slf4j.Logger slf4jLogger = LoggerFactory.getLogger("slf4j-logger");

    private final OpenTelemetry openTelemetry;

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
        openTelemetry = initializeOpenTelemetry();
        // Install OpenTelemetry in logback appender
        io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender.install(
                openTelemetry);

        // Route JUL logs to slf4j
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();

        // Log using log4j API
        this.otelAppender = createOpenTelemetryAppender(main, "otelAppender", openTelemetry);

        this.infoLogger = Config.getBaseConfig(main).getInfoLogPath(main).equals("null")
                ? createLoggerForConsole(main, "io.supertokens.Info", LOG_LEVEL.INFO)
                : createLoggerForFile(main, Config.getBaseConfig(main).getInfoLogPath(main),
                "io.supertokens.Info");
        this.errorLogger = Config.getBaseConfig(main).getErrorLogPath(main).equals("null")
                ? createLoggerForConsole(main, "io.supertokens.Error", LOG_LEVEL.ERROR)
                : createLoggerForFile(main, Config.getBaseConfig(main).getErrorLogPath(main),
                "io.supertokens.Error");
        this.otelLogger = createLoggerForOtel(main, "io.supertokens.Otel", LOG_LEVEL.INFO, openTelemetry);
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

    private static OpenTelemetry initializeOpenTelemetry() {
        OpenTelemetrySdk sdk =
                OpenTelemetrySdk.builder()
                        .setTracerProvider(SdkTracerProvider.builder().setSampler(Sampler.alwaysOn())
                                .build())
                        .setLoggerProvider(
                                SdkLoggerProvider.builder()
                                        .setResource(
                                                Resource.getDefault().toBuilder()
                                                        .put(SERVICE_NAME, "supertokens-logger")
                                                        .build())
                                        .addLogRecordProcessor(
                                                BatchLogRecordProcessor.builder(
//                                                                OtlpHttpLogRecordExporter.builder()
//                                                                        .setEndpoint("http://172.21.0.4:4318")
//                                                                        .build())
                                                                OtlpGrpcLogRecordExporter.builder()
                                                                        .setEndpoint("http://localhost:4317")
                                                                        .build())

                                                        .build())
                                        .build())
                        .buildAndRegisterGlobal();

        // Add hook to close SDK, which flushes logs
        Runtime.getRuntime().addShutdownHook(new Thread(sdk::close));

        return sdk;
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
            if (getInstance(main) != null) {
                getInstance(main).infoLogger.debug(getFormattedMessage(tenantIdentifier, msg));
            }
        } catch (NullPointerException e) {
            // sometimes logger.debug throws a null pointer exception...
        }
    }

    private static String getFormattedMessage(TenantIdentifier tenantIdentifier, String msg, Exception e) {
        JsonObject msgObj = new JsonObject();
        msgObj.addProperty("message", msg);
        msgObj.add("tenant", new JsonObject());
        msgObj.getAsJsonObject("tenant").addProperty("connectionUriDomain", tenantIdentifier.getConnectionUriDomain());
        msgObj.getAsJsonObject("tenant").addProperty("appId", tenantIdentifier.getAppId());
        msgObj.getAsJsonObject("tenant").addProperty("tenantId", tenantIdentifier.getTenantId());

        if (e != null) {
            String stackTrace = Utils.throwableStacktraceToString(e);
            String[] stackTraceArr = stackTrace.split("\n");
            JsonArray stackTraceArrObj = new JsonArray();
            for (String stackTraceElement : stackTraceArr) {
                stackTraceArrObj.add(new JsonPrimitive(stackTraceElement));
            }

            msgObj.add("exception", stackTraceArrObj);
        }

        return msgObj.toString();
    }

    private static String getFormattedMessage(TenantIdentifier tenantIdentifier, String msg) {
        return getFormattedMessage(tenantIdentifier, msg, null);
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
            msg = getFormattedMessage(tenantIdentifier, msg);

            if (getInstance(main) != null) {
                String finalMsg = msg;
                maybeRunWithSpan(() -> {
                    getInstance(main).infoLogger.info(finalMsg);
                    getInstance(main).otelAppender.logRecordBuilder()
                            .setSeverity(Severity.INFO)
                            .setBody(finalMsg + " otelAppender with span")
                            .setSeverityText("INFO")
                            .emit();
                    getInstance(main).otelLogger.info("otelLogger with span: " + finalMsg);
                    getInstance(main).otelLogger.makeLoggingEventBuilder(Level.INFO)
                            .log(finalMsg + " otelLogger with span with loggingEventBuilder");
                }, true);
                getInstance(main).openTelemetry.getTracer("core-tracer")
                        .spanBuilder("info")
                        .setAttribute("tenant.connectionUriDomain", tenantIdentifier.getConnectionUriDomain())
                        .setAttribute("tenant.appId", tenantIdentifier.getAppId())
                        .setAttribute("tenant.tenantId", tenantIdentifier.getTenantId())
                        .startSpan().addEvent("log",
                                Attributes.builder().put("message", finalMsg).build(),
                                System.currentTimeMillis(), TimeUnit.MILLISECONDS)
                        .end();

                getInstance(main).otelLogger.info("otelLogger without span: " + finalMsg);

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
            msg = getFormattedMessage(tenantIdentifier, msg);
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
            if (getInstance(main) != null) {
                getInstance(main).errorLogger.error(getFormattedMessage(tenantIdentifier, err));
            }
            if (toConsoleAsWell || getInstance(main) == null) {
                systemErr(prependTenantIdentifierToMessage(tenantIdentifier, err));
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
                // Not required to log here as the error is already included in the formatted message
                // err = getFormattedMessage(tenantIdentifier, err);
                // getInstance(main).errorLogger.error(err);
            } else if (Main.isTesting) {
                systemErr(err);
            }
            if (message != null) {
                message = message.trim();
                if (getInstance(main) != null) {
                    getInstance(main).errorLogger.error(getFormattedMessage(tenantIdentifier, message, e));
                }
                if (toConsoleAsWell || getInstance(main) == null) {
                    systemErr(prependTenantIdentifierToMessage(tenantIdentifier, message));
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

    private io.opentelemetry.api.logs.Logger createOpenTelemetryAppender(Main main, String name, OpenTelemetry otel) {
//        OpenTelemetryAppender appender = new OpenTelemetryAppender();
//        appender.setName(name);
//        appender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
//        appender.setOpenTelemetry(otel);
//        appender.start();
//        return appender;
        io.opentelemetry.api.logs.Logger customAppenderLogger =
                otel.getLogsBridge()
                        .get("core-tracer"); //was: name
        return customAppenderLogger;
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

    private Logger createLoggerForOtel(Main main, String name, LOG_LEVEL logLevel, OpenTelemetry otel) {
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        LayoutWrappingEncoder ple = new LayoutWrappingEncoder(main.getProcessId(),
                Version.getVersion(main).getCoreVersion());
        ple.setContext(lc);
        ple.start();
        OpenTelemetryAppender logOtelAppender = new OpenTelemetryAppender();
        logOtelAppender.setName(name);
        logOtelAppender.setCaptureArguments(true);
        logOtelAppender.setCaptureExperimentalAttributes(true);
        logOtelAppender.setContext(lc);
        logOtelAppender.setCaptureLoggerContext(true);
        logOtelAppender.setOpenTelemetry(otel);
        logOtelAppender.start();

        Logger logger = (Logger) LoggerFactory.getLogger(name);
        logger.addAppender(logOtelAppender);
        logger.setAdditive(false); /* set to true if root should log too */

        return logger;
    }

    private static void maybeRunWithSpan(Runnable runnable, boolean withSpan) {
        if (!withSpan) {
            runnable.run();
            return;
        }
        Span span = GlobalOpenTelemetry.getTracer("core-tracer").spanBuilder("iDontKnow").startSpan();
        try (Scope unused = span.makeCurrent()) {
            runnable.run();
        } finally {
            span.end();
        }
    }
}
