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
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Aspect
public class MethodSpanner {

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
            Map<String, String> methodArguments = new HashMap<>();
            for (Object argument : joinPoint.getArgs()) {
                if (argument != null) {
                    methodArguments.put(argument.getClass().getCanonicalName(), String.valueOf(argument));
                } else {
                    methodArguments.put("null", "null");
                }

            }
            span.setAttribute("method.arguments", methodArguments.keySet().stream().map(key -> key + ": " + methodArguments.get(key))
                    .collect(Collectors.joining(", ", "{", "}")));
            try {
                Object result = joinPoint.proceed(); //run the actual method
                if (result != null) {
                    span.setAttribute("method.returns",
                            result.getClass().getCanonicalName() + " -> " + result);
                } else {
                    span.setAttribute("method.returns", "void/null");
                }
                span.setStatus(StatusCode.OK);
                return result;
            } catch (Throwable e) {
                span.recordException(e);
                span.setStatus(StatusCode.ERROR);
                throw e;
            }
        } finally {
            span.end();
        }
    }

}
