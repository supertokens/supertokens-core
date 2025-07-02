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

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.supertokens.Main;
import io.supertokens.ResourceDistributor;
import io.supertokens.output.Logging;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;

import static io.opentelemetry.semconv.ServiceAttributes.SERVICE_NAME;

public class TelemetryProvider extends ResourceDistributor.SingletonResource {

    private static final String RESOURCE_ID = "io.supertokens.telemetry.TelemetryProvider";

    public static Logging getInstance(Main main) {
        try {
            return (Logging) main.getResourceDistributor()
                    .getResource(new TenantIdentifier(null, null, null), RESOURCE_ID);
        } catch (TenantOrAppNotFoundException e) {
            return null;
        }
    }

    private static OpenTelemetry initializeOpenTelemetry() {
        OpenTelemetrySdk sdk =
                OpenTelemetrySdk.builder()
                        .setTracerProvider(SdkTracerProvider.builder().setSampler(Sampler.alwaysOn()).build())
                        .setLoggerProvider(
                                SdkLoggerProvider.builder()
                                        .setResource(
                                                Resource.getDefault().toBuilder()
                                                        .put(SERVICE_NAME, "supertokens-logger")
                                                        .build())
                                        .addLogRecordProcessor(
                                                BatchLogRecordProcessor.builder(
                                                                OtlpGrpcLogRecordExporter.builder()
                                                                        .setEndpoint("http://otel-collector:4317")
                                                                        .build())
                                                        .build())
                                        .build())
                        .build();

        // Add hook to close SDK, which flushes logs
        Runtime.getRuntime().addShutdownHook(new Thread(sdk::close));

        return sdk;
    }
}
