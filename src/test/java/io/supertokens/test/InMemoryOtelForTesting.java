/*
 *    Copyright (c) 2026, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.test;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Installs a global OpenTelemetry SDK (as the --javaagent would) that keeps every exported span and log record in
 * memory, so that tests can assert on what would reach the telemetry backend. Install it after Utils.reset() and
 * before starting the core, and close it at the end of the test.
 */
public class InMemoryOtelForTesting implements AutoCloseable {

    private final List<SpanData> spans = new ArrayList<>();
    private final List<LogRecordData> logRecords = new ArrayList<>();
    private final OpenTelemetrySdk sdk;

    private InMemoryOtelForTesting() {
        SpanExporter spanExporter = new SpanExporter() {
            @Override
            public CompletableResultCode export(Collection<SpanData> exported) {
                synchronized (spans) {
                    spans.addAll(exported);
                }
                return CompletableResultCode.ofSuccess();
            }

            @Override
            public CompletableResultCode flush() {
                return CompletableResultCode.ofSuccess();
            }

            @Override
            public CompletableResultCode shutdown() {
                return CompletableResultCode.ofSuccess();
            }
        };
        LogRecordExporter logRecordExporter = new LogRecordExporter() {
            @Override
            public CompletableResultCode export(Collection<LogRecordData> exported) {
                synchronized (logRecords) {
                    logRecords.addAll(exported);
                }
                return CompletableResultCode.ofSuccess();
            }

            @Override
            public CompletableResultCode flush() {
                return CompletableResultCode.ofSuccess();
            }

            @Override
            public CompletableResultCode shutdown() {
                return CompletableResultCode.ofSuccess();
            }
        };

        GlobalOpenTelemetry.resetForTest();
        sdk = OpenTelemetrySdk.builder()
                .setTracerProvider(SdkTracerProvider.builder()
                        .addSpanProcessor(SimpleSpanProcessor.create(spanExporter)).build())
                .setLoggerProvider(SdkLoggerProvider.builder()
                        .addLogRecordProcessor(SimpleLogRecordProcessor.create(logRecordExporter)).build())
                .buildAndRegisterGlobal();
    }

    public static InMemoryOtelForTesting install() {
        return new InMemoryOtelForTesting();
    }

    public List<SpanData> getSpans() {
        synchronized (spans) {
            return new ArrayList<>(spans);
        }
    }

    public List<LogRecordData> getLogRecords() {
        synchronized (logRecords) {
            return new ArrayList<>(logRecords);
        }
    }

    /**
     * Every exported string: span names, span attributes, span event attributes, log bodies and log attributes.
     */
    public String getAllExportedText() {
        StringBuilder sb = new StringBuilder();
        for (SpanData span : getSpans()) {
            sb.append(span.getName()).append('\n');
            sb.append(span.getAttributes()).append('\n');
            sb.append(span.getStatus()).append('\n');
            span.getEvents().forEach(event -> sb.append(event.getName()).append(event.getAttributes()).append('\n'));
        }
        for (LogRecordData logRecord : getLogRecords()) {
            sb.append(logRecord.getBodyValue() == null ? "" : logRecord.getBodyValue().asString()).append('\n');
            sb.append(logRecord.getAttributes()).append('\n');
        }
        return sb.toString();
    }

    @Override
    public void close() {
        sdk.close();
        GlobalOpenTelemetry.resetForTest();
    }
}
