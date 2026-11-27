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

import io.supertokens.Main;
import io.supertokens.output.Logging;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.auditlog.ActivityLogStorage;
import io.supertokens.pluginInterface.auditlog.AuditLogEvent;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;

public class AuditLog {

    /**
     * Writes {@code event} to the activity_log table (best-effort, never throws). For callers that record an event
     * after their mutation has committed. An event that must commit or fail with its mutation is written inside
     * the mutation's {@code startAuditedTransaction} instead, where a failed write fails the request.
     */
    public static void emit(Main main, Storage storage, TenantIdentifier tenantIdentifier, AuditLogEvent event) {
        if (storage instanceof ActivityLogStorage) {
            try {
                ((ActivityLogStorage) storage).createActivityLogEntry(tenantIdentifier, event);
            } catch (Exception e) {
                Logging.error(main, tenantIdentifier,
                        "Failed to write audit log entry [" + event.eventType + "]: " + e.getMessage(), false);
            }
        }
    }
}
