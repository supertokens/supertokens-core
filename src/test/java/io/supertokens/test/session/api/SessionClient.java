/*
 *    Copyright (c) 2021, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.test.session.api;

import com.google.gson.JsonObject;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.httpRequest.HttpResponseException;

import java.io.IOException;

public class SessionClient {
    private final TestingProcessManager.TestingProcess process;
    private final String cdiVersion;

    private final String BASE_URL = "http://localhost:3567";
    private final int CONNECTION_TIMEOUT_MS = 1000;
    private final int READ_TIMEOUT_MS = 1000;

    public SessionClient(TestingProcessManager.TestingProcess process, String cdiVersion) {
        this.process = process;
        this.cdiVersion = cdiVersion;
    }

    public JsonObject createSession(JsonObject body) throws IOException, HttpResponseException {
        return io.supertokens.test.httpRequest.HttpRequest
                .sendJsonPOSTRequest(process.getProcess(), "", BASE_URL + "/recipe/session",
                        body, CONNECTION_TIMEOUT_MS, READ_TIMEOUT_MS, null, cdiVersion, "session");
    }

    public JsonObject verifySession(JsonObject body) throws IOException, HttpResponseException {
        return io.supertokens.test.httpRequest.HttpRequest
                .sendJsonPOSTRequest(process.getProcess(), "", BASE_URL + "/recipe/session/verify", body, CONNECTION_TIMEOUT_MS, READ_TIMEOUT_MS, null, cdiVersion, "session");
    }
}
