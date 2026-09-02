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

package io.supertokens.auditlog;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.DeclareError;

/**
 * Compile-time guard enforcing that state-changing domain operations enter
 * transactions through {@code ActivityLogSQLStorage.startAuditedTransaction(...)}
 * — which requires audit events and owns the commit — rather than by calling
 * {@code SQLStorage.startTransaction(...)} directly.
 *
 * <p>The core build compiles with ajc (see {@code io.freefair.aspectj} in
 * build.gradle, alongside {@link io.supertokens.telemetry.MethodSpanner}), so a
 * matched {@code declare error} pointcut is a hard compilation failure at the
 * offending line — earlier than any test and impossible to skip.
 *
 * <p>Scope:
 * <ul>
 *   <li>{@code within(io.supertokens..*)} — only our own code, not libraries.</li>
 *   <li>{@code !within(io.supertokens.inmemorydb..*)} — the in-memory storage
 *       <em>implements</em> transactions; it is a storage layer, not domain code.</li>
 *   <li>{@code !within(io.supertokens.test..*)} — test code (the whole test source
 *       set lives under this package) drives transactions directly to build
 *       scenarios; the audit invariant is a production-code rule.</li>
 *   <li>{@code !withincode(@UnauditedTransaction ...)} — the allowlist escape
 *       hatch for the legacy backlog.</li>
 * </ul>
 *
 * <p>The combinator's own internal {@code startTransaction} call lives in
 * plugin-interface, outside the {@code io.supertokens..*} compilation units
 * matched here, so it does not trip the rule. {@code declare error} accepts only
 * statically evaluable pointcuts ({@code call}/{@code within}/{@code withincode}),
 * which is exactly what makes this a true compile-time check.
 */
@Aspect
public class AuditEnforcementAspect {

    @DeclareError("call(* io.supertokens.pluginInterface.sqlStorage.SQLStorage.startTransaction(..))"
            + " && within(io.supertokens..*)"
            + " && !within(io.supertokens.inmemorydb..*)"
            + " && !within(io.supertokens.test..*)"
            + " && !withincode(@io.supertokens.auditlog.UnauditedTransaction * *(..))")
    static final String RAW_TXN = "Domain code must not call SQLStorage.startTransaction directly."
            + " Use ActivityLogSQLStorage.startAuditedTransaction(...) (which requires audit events and"
            + " owns the commit), or annotate the enclosing method with"
            + " @UnauditedTransaction(justification = \"...\") — allowlist additions are review-flagged.";
}
