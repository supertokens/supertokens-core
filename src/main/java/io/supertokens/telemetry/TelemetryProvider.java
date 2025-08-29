/*
 *    Copyright (c) 2025, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.telemetry;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.supertokens.Main;
import io.supertokens.ResourceDistributor;
import io.supertokens.config.Config;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.opentelemetry.OtelProvider;
import org.jetbrains.annotations.TestOnly;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.opentelemetry.semconv.ServiceAttributes.SERVICE_NAME;

public class TelemetryProvider extends ResourceDistributor.SingletonResource implements OtelProvider {

    private final OpenTelemetry openTelemetry;

    public static synchronized TelemetryProvider getInstance(Main main) {
        TelemetryProvider instance = null;
        try {
            instance = (TelemetryProvider) main.getResourceDistributor()
                    .getResource(TenantIdentifier.BASE_TENANT, RESOURCE_ID);
        } catch (TenantOrAppNotFoundException ignored) {
        }
        return instance;
    }

    public static void initialize(Main main) {
        main.getResourceDistributor()
                .setResource(TenantIdentifier.BASE_TENANT, RESOURCE_ID, new TelemetryProvider(main));
    }

    @Override
    public void createLogEvent(TenantIdentifier tenantIdentifier, String logMessage,
                               String logLevel) {
        createLogEvent(tenantIdentifier, logMessage, logLevel, Map.of());
    }

    @Override
    public void createLogEvent(TenantIdentifier tenantIdentifier, String logMessage,
                               String logLevel, Map<String, String> additionalAttributes) {
        if (openTelemetry == null) {
            return; // no telemetry provider available
        }
        SpanBuilder spanBuilder = createSpanBuilder(tenantIdentifier, logLevel, additionalAttributes);


        spanBuilder.startSpan()
                .addEvent("log",
                        Attributes.builder()
                                .put("message", logMessage)
                                .build(),
                        System.currentTimeMillis(), TimeUnit.MILLISECONDS)
                .end();
    }

    private SpanBuilder createSpanBuilder(TenantIdentifier tenantIdentifier, String spanName,
                                          Map<String, String> additionalAttributes) {
        SpanBuilder spanBuilder = openTelemetry.getTracer("core-tracer")
                .spanBuilder(spanName)
                .setParent(Context.current());

        return addAttributesToSpanBuilder(spanBuilder, tenantIdentifier, additionalAttributes);
    }

    private SpanBuilder addAttributesToSpanBuilder(SpanBuilder spanBuilder, TenantIdentifier tenantIdentifier,
                                                   Map<String, String> additionalAttributes) {
        spanBuilder
                .setAttribute("tenant.connectionUriDomain", tenantIdentifier.getConnectionUriDomain())
                .setAttribute("tenant.appId", tenantIdentifier.getAppId())
                .setAttribute("tenant.tenantId", tenantIdentifier.getTenantId());

        if (additionalAttributes != null && !additionalAttributes.isEmpty()) {
            // Add additional attributes to the span
            for (Map.Entry<String, String> attribute : additionalAttributes.entrySet()) {
                spanBuilder.setAttribute(attribute.getKey(), attribute.getValue());
            }
        }

        return spanBuilder;
    }

    public static Span startSpan(Main main, TenantIdentifier tenantIdentifier, String spanName) {
        Span span = getInstance(main).openTelemetry.getTracer("core-tracer")
                .spanBuilder(spanName)
                .setParent(Context.current())
                .setAttribute("tenant.connectionUriDomain", tenantIdentifier.getConnectionUriDomain())
                .setAttribute("tenant.appId", tenantIdentifier.getAppId())
                .setAttribute("tenant.tenantId", tenantIdentifier.getTenantId())
                .startSpan();

        span.makeCurrent(); // Set the span as the current context
        return span;
    }

    public static Span endSpan(Span span) {
        if (span != null) {
            span.end();
        }
        return span;
    }

    public static Span addEventToSpan(Span span, String eventName, Attributes attributes) {
        if (span != null) {
            span.addEvent(eventName, attributes, System.currentTimeMillis(), TimeUnit.MILLISECONDS);
        }
        return span;
    }


    private static OpenTelemetry initializeOpenTelemetry(Main main) {
        String collectorUri = Config.getBaseConfig(main).getOtelCollectorConnectionURI();

        if (collectorUri == null || collectorUri.isEmpty()) {
            return null;
        }

        if (getInstance(main) != null && getInstance(main).openTelemetry != null) {
            return getInstance(main).openTelemetry; // already initialized
        }
        
        Resource resource = Resource.getDefault().toBuilder()
                .put(SERVICE_NAME, "supertokens-core")
                .build();

        SdkTracerProvider sdkTracerProvider =
                SdkTracerProvider.builder()
                        .setResource(resource)
                        .addSpanProcessor(SimpleSpanProcessor.create(OtlpGrpcSpanExporter.builder()
                                .setEndpoint(collectorUri) // otel collector
                                .build()))
                        .build();

        OpenTelemetrySdk sdk =
                OpenTelemetrySdk.builder()
                        .setTracerProvider(sdkTracerProvider)
                        .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                        .setLoggerProvider(
                                SdkLoggerProvider.builder()
                                        .setResource(resource)
                                        .addLogRecordProcessor(
                                                BatchLogRecordProcessor.builder(
                                                                OtlpGrpcLogRecordExporter.builder()
                                                                        .setEndpoint(collectorUri)
                                                                        .build())

                                                        .build())
                                        .build())
                        .build();

        // Add hook to close SDK, which flushes logs
        Runtime.getRuntime().addShutdownHook(new Thread(sdk::close));
        return sdk;
    }

    @TestOnly
    public static void resetForTest() {
        GlobalOpenTelemetry.resetForTest();
    }

    public static void closeTelemetry(Main main) {
        OpenTelemetry telemetry = getInstance(main).openTelemetry;
        if (telemetry instanceof OpenTelemetrySdk) {
            ((OpenTelemetrySdk) telemetry).close();
        }
    }

    private TelemetryProvider(Main main) {
        openTelemetry = initializeOpenTelemetry(main);
    }
}
