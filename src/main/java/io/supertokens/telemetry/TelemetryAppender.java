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

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public class TelemetryAppender {

    private final static TelemetryAppender instance = new TelemetryAppender();

    public static TelemetryAppender getInstance() {
        return instance;
    }

    private TelemetryAppender() {
    }

    public void appendEventToCurrentSpan(String eventName, Map<String, String> eventData, long timestamp) {
        Span.current().addEvent(eventName, fromMap(eventData), timestamp, TimeUnit.MILLISECONDS);
    }

    public void appendAttributesToCurrentSpan(Map<String, String> attributes) {
        Span.current().setAllAttributes(fromMap(attributes));
    }

    private Attributes fromMap(Map<String, String> map) {
        AttributesBuilder ab = Attributes.builder();
        if(map != null) {
            for (Map.Entry<String, String> e : map.entrySet()) {
                ab.put(e.getKey(), e.getValue());
            }
        }
        return ab.build();
    }
}
