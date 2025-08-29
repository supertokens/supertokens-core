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

import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.opentelemetry.OtelProvider;
import io.supertokens.pluginInterface.opentelemetry.RunnableWithOtel;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

public enum WebRequestTelemetryHandler {

    INSTANCE;

    private OtelProvider telemetryProvider;

    public synchronized void initializeOtelProvider(OtelProvider otelProvider) {
        if (this.telemetryProvider == null) {
            this.telemetryProvider = otelProvider;
        }
    }

    public <T> void wrapRequestInSpan(HttpServletRequest servletRequest, TenantIdentifier tenantIdentifier,
                                      RunnableWithOtel<T> requestHandler) {
        Map<String, String> requestAttributes = getRequestAttributes(servletRequest);
        telemetryProvider.wrapInSpanWithReturn(tenantIdentifier, "httpRequest", requestAttributes, requestHandler);
    }

    public void createSpan(TenantIdentifier tenantIdentifier, String name, Map<String, String> attributes) {
        telemetryProvider.createSpanWithAttributes(tenantIdentifier, name, attributes);
    }

    public <T> T wrapInSpan(TenantIdentifier tenantIdentifier, String name, Map<String, String> attributes,
                            RunnableWithOtel<T> runnable) {
        return (T) telemetryProvider.wrapInSpanWithReturn(tenantIdentifier, name, attributes, runnable);
    }


    private Map<String, String> getRequestAttributes(HttpServletRequest servletRequest) {
        Map<String, String> requestAttributes = new HashMap<>();
        requestAttributes.put("http.method", servletRequest.getMethod());
        requestAttributes.put("http.url", servletRequest.getRequestURL().toString());
        if (servletRequest.getQueryString() != null) {
            requestAttributes.put("http.query_string", servletRequest.getQueryString());
        }
        requestAttributes.put("http.user_agent", servletRequest.getHeader("User-Agent"));
        requestAttributes.put("http.client_ip", servletRequest.getRemoteAddr());
        Enumeration<String> headersEnumeration = servletRequest.getHeaderNames();
        while(headersEnumeration.hasMoreElements()) {
            String headerName = headersEnumeration.nextElement();
            requestAttributes.put("http.header." + headerName.toLowerCase(), servletRequest.getHeader(headerName));
        }
        return requestAttributes;
    }

}
