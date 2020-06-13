/*
 *    Copyright (c) 2020, VRAI Labs and/or its affiliates. All rights reserved.
 *
 *    This program is licensed under the SuperTokens Community License (the
 *    "License") as published by VRAI Labs. You may not use this file except in
 *    compliance with the License. You are not permitted to transfer or
 *    redistribute this file without express written permission from VRAI Labs.
 *
 *    A copy of the License is available in the file titled
 *    "SuperTokensLicense.pdf" inside this repository or included with your copy of
 *    the software or its source code. If you have not received a copy of the
 *    License, please write to VRAI Labs at team@supertokens.io.
 *
 *    Please read the License carefully before accessing, downloading, copying,
 *    using, modifying, merging, transferring or sharing this software. By
 *    undertaking any of these activities, you indicate your agreement to the terms
 *    of the License.
 *
 *    This program is distributed with certain software that is licensed under
 *    separate terms, as designated in a particular file or component or in
 *    included license documentation. VRAI Labs hereby grants you an additional
 *    permission to link the program and your derivative works with the separately
 *    licensed software that they have included with this program, however if you
 *    modify this program, you shall be solely liable to ensure compliance of the
 *    modified program with the terms of licensing of the separately licensed
 *    software.
 *
 *    Unless required by applicable law or agreed to in writing, this program is
 *    distributed under the License on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 *    CONDITIONS OF ANY KIND, either express or implied. See the License for the
 *    specific language governing permissions and limitations under the License.
 *
 */

package io.supertokens;

import io.supertokens.ResourceDistributor.SingletonResource;

import java.util.ArrayList;
import java.util.List;

public class ProcessState extends ResourceDistributor.SingletonResource {

    private static final String RESOURCE_KEY = "io.supertokens.ProcessState";
    private final List<EventAndException> history = new ArrayList<>();

    private ProcessState() {

    }

    public static ProcessState getInstance(Main main) {
        SingletonResource instance = main.getResourceDistributor().getResource(RESOURCE_KEY);
        if (instance == null) {
            instance = main.getResourceDistributor().setResource(RESOURCE_KEY, new ProcessState());
        }
        return (ProcessState) instance;
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
        if (Main.isTesting) {
            history.add(new EventAndException(processState, e));
        }
    }

    /**
     * INIT: Initialization started INIT_FAILURE: Initialization failed
     * STARTED: Initialized successfully SHUTTING_DOWN: Shut down signal received STOPPED
     * APP_ID_MISMATCH: Multiple processes with different appIds detected
     * RETRYING_ACCESS_TOKEN_JWT_VERIFICATION: When access
     * token verification fails due to change in signing key, so we retry it
     * CRON_TASK_ERROR_LOGGING: When an exception is thrown from a Cronjob
     * DEVICE_DRIVER_INFO_LOGGED:When program is saving deviceDriverInfo into ping
     * SERVER_PING: When program is pinging the server with information
     * WAITING_TO_INIT_STORAGE_MODULE: When the program is going to possibly wait to init the storage module
     */
    public enum PROCESS_STATE {
        INIT, INIT_FAILURE, STARTED, SHUTTING_DOWN, STOPPED, APP_ID_MISMATCH,
        RETRYING_ACCESS_TOKEN_JWT_VERIFICATION, CRON_TASK_ERROR_LOGGING, DEVICE_DRIVER_INFO_SAVED,
        SERVER_PING, WAITING_TO_INIT_STORAGE_MODULE
    }

    public static class EventAndException {
        public Exception exception;
        PROCESS_STATE state;

        public EventAndException(PROCESS_STATE state, Exception e) {
            this.state = state;
            this.exception = e;
        }
    }

}
