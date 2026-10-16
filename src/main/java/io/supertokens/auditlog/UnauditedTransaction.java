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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a domain method that deliberately opens a raw
 * {@code SQLStorage.startTransaction(...)} without going through
 * {@code ActivityLogSQLStorage.startAuditedTransaction(...)}, and is therefore
 * exempt from the compile-time guard enforced by {@link AuditEnforcementAspect}.
 *
 * <p>This is an allowlist for the legacy backlog: every entry is a state-changing
 * operation that is not yet audited in its own transaction. New call sites must
 * instead use {@code startAuditedTransaction(...)} (or, for a genuinely
 * event-less mutation, {@code AuditedResult.withoutAudit(...)}). Adding an
 * annotation here grows the {@code @UnauditedTransaction} baseline and is
 * flagged for review; the baseline is shrink-only and burns down as sites are
 * converted.
 *
 * <p>{@link RetentionPolicy#CLASS} is sufficient — the guard is a compile-time
 * AspectJ {@code declare error} matched via {@code withincode(...)}; nothing
 * reads the annotation at runtime.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface UnauditedTransaction {
    /**
     * A short explanation of why this method's transaction is not yet audited.
     */
    String justification();
}
