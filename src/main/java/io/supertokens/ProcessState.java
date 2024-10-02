/*
 *    Copyright (c) 2020, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens;

import com.google.gson.JsonObject;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;

import java.util.ArrayList;
import java.util.List;

public class ProcessState extends ResourceDistributor.SingletonResource {

    private static final String RESOURCE_KEY = "io.supertokens.ProcessState";
    private List<EventAndException> history = new ArrayList<>();

    private ProcessState() {

    }

    public static ProcessState getInstance(Main main) {
        try {
            return (ProcessState) main.getResourceDistributor()
                    .getResource(new TenantIdentifier(null, null, null), RESOURCE_KEY);
        } catch (TenantOrAppNotFoundException e) {
            return (ProcessState) main.getResourceDistributor()
                    .setResource(new TenantIdentifier(null, null, null), RESOURCE_KEY, new ProcessState());
        }
    }

    public synchronized EventAndException getLastEventByName(PROCESS_STATE processState) {
        for (int i = history.size() - 1; i >= 0; i--) {
            if (history.get(i).state == processState) {
                return history.get(i);
            }
        }
        return null;
    }

    public synchronized void addState(PROCESS_STATE processState, Exception e) {
        addState(processState, e, null);
    }

    public synchronized void addState(PROCESS_STATE processState, Exception e, JsonObject data) {
        if (Main.isTesting) {
            history.add(new EventAndException(processState, e, data));
        }
    }

    public synchronized void clear() {
        history = new ArrayList<>();
    }

    /**
     * INIT: Initialization started INIT_FAILURE: Initialization failed
     * STARTED: Initialized successfully SHUTTING_DOWN: Shut down signal received STOPPED
     * RETRYING_ACCESS_TOKEN_JWT_VERIFICATION: When access token verification fails due to change in signing key, so
     * we retry it
     * CRON_TASK_ERROR_LOGGING: When an exception is thrown from a Cronjob
     * DEVICE_DRIVER_INFO_LOGGED:When program is saving deviceDriverInfo into ping
     * SERVER_PING: When program is pinging the server with information
     * WAITING_TO_INIT_STORAGE_MODULE: When the program is going to possibly wait to init the storage module
     * GET_SESSION_NEW_TOKENS: When new tokens are being issued in get session
     * DEADLOCK_FOUND: For SQLite transactions
     * CREATING_NEW_TABLE: For SQLite
     * SENDING_TELEMETRY, SENT_TELEMETRY: For Telemetry
     * PASSWORD_HASH_BCRYPT, PASSWORD_HASH_ARGON, PASSWORD_VERIFY_BCRYPT, PASSWORD_VERIFY_ARGON: For testing password
     * hashing
     * ADDING_REMOTE_ADDRESS_FILTER: If IP allow / deny regex has been passed, we add a filter to the tomcat server
     * LICENSE_KEY_CHECK_NETWORK_CALL: Called when license key is added and network call is being made to check it.
     * INVALID_LICENSE_KEY: Called when the licens key check failed
     * SERVER_ERROR_DURING_LICENSE_KEY_CHECK_FAIL: Added when the server request failed during license key check
     * INIT_FAILURE_DUE_TO_LICENSE_KEY_DB_CHECK: Added if license key check in db failed on core start
     * LOADING_ALL_TENANT_CONFIG: Added when the Config.loadAllTenantConfig function is called, either on core start,
     * or during API call which adds / modifies tenant
     * LOADING_ALL_TENANT_STORAGE: Added when the StorageLayer.loadAllTenantStorage function is called, either on
     * core start,
     * * or during API call which adds / modifies tenant
     */
    public enum PROCESS_STATE {
        INIT, INIT_FAILURE, STARTED, SHUTTING_DOWN, STOPPED, RETRYING_ACCESS_TOKEN_JWT_VERIFICATION,
        CRON_TASK_ERROR_LOGGING, WAITING_TO_INIT_STORAGE_MODULE, GET_SESSION_NEW_TOKENS, DEADLOCK_FOUND,
        CREATING_NEW_TABLE, SENDING_TELEMETRY, SENT_TELEMETRY, UPDATING_ACCESS_TOKEN_SIGNING_KEYS,
        PASSWORD_HASH_BCRYPT, PASSWORD_HASH_ARGON, PASSWORD_VERIFY_BCRYPT, PASSWORD_VERIFY_ARGON,
        PASSWORD_VERIFY_FIREBASE_SCRYPT, ADDING_REMOTE_ADDRESS_FILTER, LICENSE_KEY_CHECK_NETWORK_CALL,
        INVALID_LICENSE_KEY, SERVER_ERROR_DURING_LICENSE_KEY_CHECK_FAIL, LOADING_ALL_TENANT_CONFIG,
        LOADING_ALL_TENANT_STORAGE, TENANTS_CHANGED_DURING_REFRESH_FROM_DB
    }

    public static class EventAndException {
        public Exception exception;
        public JsonObject data;
        PROCESS_STATE state;

        public EventAndException(PROCESS_STATE state, Exception e) {
            this.state = state;
            this.exception = e;
            this.data = null;
        }

        public EventAndException(PROCESS_STATE state, Exception e, JsonObject data) {
            this.state = state;
            this.exception = e;
            this.data = data;
        }
    }

}
