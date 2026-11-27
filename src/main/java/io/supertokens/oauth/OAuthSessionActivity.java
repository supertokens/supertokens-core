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

package io.supertokens.oauth;

import io.supertokens.ActiveUsers;
import io.supertokens.Main;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.auditlog.ActivityEventType;
import io.supertokens.pluginInterface.auditlog.ActivityLogSQLStorage;
import io.supertokens.pluginInterface.auditlog.AuditLogEvent;
import io.supertokens.pluginInterface.auditlog.AuditedResult;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.session.SessionInfo;
import io.supertokens.pluginInterface.session.sqlStorage.SessionSQLStorage;
import io.supertokens.pluginInterface.sqlStorage.TransactionConnection;
import io.supertokens.session.Session;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.useridmapping.UserIdMapping;
import io.supertokens.useridmapping.UserIdType;

import javax.annotation.Nullable;

/**
 * The activity event of an OAuth token or auth request, attributed to the user of the SuperTokens session the
 * tokens were issued for. The OAuth rows live on the app's public-tenant storage, while the event belongs on the
 * storage of the session's tenant, where the user's auth record is.
 * <ul>
 *     <li>When both are the same storage, the event is written in the OAuth write's transaction: call {@link
 *     #inTransaction} from inside it, then {@link #afterCommit}.</li>
 *     <li>When the session's tenant has its own database, the OAuth write commits first and {@link #afterCommit}
 *     writes the event in its own transaction on the session's storage.</li>
 * </ul>
 * Either way, a failed event write fails the request. The session and user id mapping lookups run on the
 * connection of the transaction that writes the event.
 */
public final class OAuthSessionActivity {

    private final Main main;
    private final ActivityEventType eventType;
    @Nullable
    private final String sessionHandle;
    @Nullable
    private final TenantIdentifier sessionTenant;
    @Nullable
    private final Storage sessionStorage;
    private final boolean onOAuthStorage;
    // the event inTransaction added to the OAuth write's transaction, for the bookkeeping after it commits
    @Nullable
    private AuditLogEvent eventInOAuthTransaction;

    private OAuthSessionActivity(Main main, ActivityEventType eventType, @Nullable String sessionHandle,
                                 @Nullable TenantIdentifier sessionTenant, @Nullable Storage sessionStorage,
                                 boolean onOAuthStorage) {
        this.main = main;
        this.eventType = eventType;
        this.sessionHandle = sessionHandle;
        this.sessionTenant = sessionTenant;
        this.sessionStorage = sessionStorage;
        this.onOAuthStorage = onOAuthStorage;
    }

    /**
     * Resolves where the activity of the session with {@code sessionHandle} is recorded. Reads no database. With no
     * session handle (e.g. {@code client_credentials}) or a session tenant that no longer exists, there is no user
     * to attribute the request to, and no event is written.
     */
    public static OAuthSessionActivity of(Main main, AppIdentifier appIdentifier, Storage oauthStorage,
                                          @Nullable String sessionHandle, ActivityEventType eventType) {
        if (sessionHandle == null) {
            return new OAuthSessionActivity(main, eventType, null, null, null, false);
        }
        TenantIdentifier sessionTenant = new TenantIdentifier(appIdentifier.getConnectionUriDomain(),
                appIdentifier.getAppId(), Session.getTenantIdFromSessionHandle(sessionHandle));
        Storage sessionStorage;
        try {
            sessionStorage = StorageLayer.getStorage(sessionTenant, main);
        } catch (TenantOrAppNotFoundException e) {
            return new OAuthSessionActivity(main, eventType, null, null, null, false);
        }
        return new OAuthSessionActivity(main, eventType, sessionHandle, sessionTenant, sessionStorage,
                sessionStorage == oauthStorage);
    }

    /**
     * The audited result of the OAuth write's transaction, running on {@code con} of the OAuth storage: carries the
     * activity event when it is written there, i.e. the session's tenant uses the OAuth storage, the session still
     * exists and the activity throttle keeps the event.
     */
    public <T> AuditedResult<T> inTransaction(TransactionConnection con, T result)
            throws StorageQueryException, TenantOrAppNotFoundException {
        eventInOAuthTransaction = null;
        if (sessionHandle == null) {
            return AuditedResult.withoutAudit(result, "An OAuth request without a SuperTokens session (e.g. "
                    + "client_credentials) has no user to attribute an activity event to.");
        }
        if (!onOAuthStorage) {
            return AuditedResult.withoutAudit(result, "The session's tenant has its own database: the activity "
                    + "event is written there after this transaction commits.");
        }
        AuditLogEvent event = event(con);
        if (event == null) {
            return AuditedResult.withoutAudit(result, "The session no longer exists, or the activity throttle "
                    + "skips the event.");
        }
        eventInOAuthTransaction = event;
        return new AuditedResult<>(result, event);
    }

    /**
     * Call once the OAuth write's transaction has committed. Does the post-commit bookkeeping of an event written
     * in that transaction, or writes the event in its own transaction on the storage of a session tenant that has
     * its own database. Throws if that write fails; the OAuth write stays committed.
     */
    public void afterCommit() throws StorageQueryException, TenantOrAppNotFoundException {
        if (sessionHandle == null) {
            return;
        }
        if (onOAuthStorage) {
            if (eventInOAuthTransaction != null) {
                ActiveUsers.activityEventCommitted(main, sessionTenant.toAppIdentifier(), sessionStorage,
                        eventInOAuthTransaction);
            }
            return;
        }
        ActivityLogSQLStorage auditStorage = (ActivityLogSQLStorage) sessionStorage;
        AuditLogEvent written;
        try {
            written = auditStorage.startAuditedTransaction(sessionTenant.toAppIdentifier(), con -> {
                AuditLogEvent event = event(con);
                if (event == null) {
                    return AuditedResult.withoutAudit(null, "The session no longer exists, or the activity "
                            + "throttle skips the event.");
                }
                return new AuditedResult<>(event, event);
            });
        } catch (StorageTransactionLogicException e) {
            if (e.actualException instanceof TenantOrAppNotFoundException) {
                throw (TenantOrAppNotFoundException) e.actualException;
            }
            throw new IllegalStateException(e.actualException);
        }
        if (written != null) {
            ActiveUsers.activityEventCommitted(main, sessionTenant.toAppIdentifier(), sessionStorage, written);
        }
    }

    // The activity event for the session's user, attributed to their SuperTokens user id, or null when the session
    // no longer exists (or has expired) or the throttle skips it. The lookups run on con, a connection of the session's storage; the
    // mapping lookup is skipped for an app with disable_user_id_mapping on.
    @Nullable
    private AuditLogEvent event(TransactionConnection con)
            throws StorageQueryException, TenantOrAppNotFoundException {
        SessionInfo sessionInfo = ((SessionSQLStorage) sessionStorage).getSessionInfo_Transaction(sessionTenant, con,
                sessionHandle);
        // like Session.getSession, an expired session counts as gone
        if (sessionInfo == null || sessionInfo.expiry <= System.currentTimeMillis()) {
            return null;
        }
        io.supertokens.pluginInterface.useridmapping.UserIdMapping userIdMapping = UserIdMapping.getUserIdMapping(
                main, con, sessionTenant.toAppIdentifier(), sessionStorage, sessionInfo.userId, UserIdType.ANY);
        String userId = userIdMapping != null ? userIdMapping.superTokensUserId : sessionInfo.userId;
        return ActiveUsers.activityEventForTransaction(sessionTenant, main, userId, userId, null, eventType);
    }
}
