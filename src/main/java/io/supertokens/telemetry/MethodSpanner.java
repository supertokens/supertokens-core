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
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

@Aspect
public class MethodSpanner {

    /*
     * Span attributes are exported to the tracing backend, which must never receive secrets. Method arguments and
     * return values can be anything (configs with DB credentials and API keys, passwords, tokens, user objects...),
     * so we only ever record their type. The value is recorded only for an explicit allow-list of types that are
     * known not to carry secrets.
     */
    private static final Set<Class<?>> TYPES_WITH_RECORDABLE_VALUES = Set.of(
            Boolean.class, Integer.class, Long.class, Short.class, Byte.class, Double.class, Float.class);

    @Around("execution(* (@io.supertokens.pluginInterface.opentelemetry.WithinOtelSpan *).*(..))")
    public Object anyMethodInClassAnnotatedWithWithinOtelSpan(ProceedingJoinPoint joinPoint) throws Throwable {
        return withinOtelSpan(joinPoint);
    }

    @Around("execution(@io.supertokens.pluginInterface.opentelemetry.WithinOtelSpan * *(..))")
    public Object withinOtelSpan(ProceedingJoinPoint joinPoint) throws Throwable {
        Span span = GlobalOpenTelemetry.get().getTracer("core-tracer")
                .spanBuilder(joinPoint.getSignature().getDeclaringTypeName() + "." + joinPoint.getSignature().getName())
                .startSpan();
        try (Scope spanScope = span.makeCurrent()) {
            span.setAttribute("method.arguments", describeArguments(joinPoint.getArgs()));
            try {
                Object result = joinPoint.proceed(); //run the actual method
                if (result != null) {
                    span.setAttribute("method.returns", describeValue(result));
                } else {
                    span.setAttribute("method.returns", "void/null");
                }
                span.setStatus(StatusCode.OK);
                return result;
            } catch (Throwable e) {
                // Exception messages can contain configuration values or user input, so only the type is recorded.
                String exceptionType = e.getClass().getName();
                span.addEvent("exception",
                        Attributes.of(AttributeKey.stringKey("exception.type"), exceptionType));
                span.setStatus(StatusCode.ERROR, exceptionType);
                throw e;
            }
        } finally {
            span.end();
        }
    }

    public static String describeArguments(Object[] arguments) {
        StringBuilder sb = new StringBuilder("{");
        if (arguments != null) {
            for (int i = 0; i < arguments.length; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(i).append(": ").append(describeValue(arguments[i]));
            }
        }
        return sb.append("}").toString();
    }

    public static String describeValue(Object value) {
        if (value == null) {
            return "null";
        }
        Class<?> clazz = value.getClass();
        String typeName = clazz.getCanonicalName() != null ? clazz.getCanonicalName() : clazz.getName();

        if (TYPES_WITH_RECORDABLE_VALUES.contains(clazz)) {
            return typeName + " -> " + value;
        }
        if (value instanceof Enum<?>) {
            // name() and not toString(), which an enum can override
            return typeName + " -> " + ((Enum<?>) value).name();
        }
        if (clazz == TenantIdentifier.class) {
            TenantIdentifier tenantIdentifier = (TenantIdentifier) value;
            return typeName + " -> " + tenantIdentifier.getConnectionUriDomain() + " | "
                    + tenantIdentifier.getAppId() + " | " + tenantIdentifier.getTenantId();
        }
        if (clazz == AppIdentifier.class) {
            AppIdentifier appIdentifier = (AppIdentifier) value;
            return typeName + " -> " + appIdentifier.getConnectionUriDomain() + " | " + appIdentifier.getAppId();
        }
        if (value instanceof Collection<?>) {
            return typeName + " (size: " + ((Collection<?>) value).size() + ")";
        }
        if (value instanceof Map<?, ?>) {
            return typeName + " (size: " + ((Map<?, ?>) value).size() + ")";
        }
        if (clazz.isArray()) {
            return typeName + " (length: " + Array.getLength(value) + ")";
        }
        // Anything else (Strings, JsonObjects, configs, user objects...) may contain secrets: record the type only.
        return typeName;
    }

}
