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

package io.supertokens.cli.processes;

import com.google.gson.JsonObject;
import io.supertokens.cli.httpRequest.HTTPRequest;
import io.supertokens.cli.httpRequest.HTTPResponseException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Processes {
    public static class RunningProcess {
        public final String hostName;
        public final String port;
        public final String pid;
        public String configFilePath;  // set via an API call
        public String devProductionMode; // set via an API call

        RunningProcess(String hostName, String port, String pid) {
            this.hostName = hostName;
            this.port = port;
            this.pid = pid;
        }

        public void fetchConfigFilePath() throws IOException, HTTPResponseException {
            Map<String, String> params = new HashMap<>();
            params.put("pid", this.pid);
            JsonObject result = HTTPRequest
                    .sendGETRequest("http://" + this.hostName + ":" + this.port + "/config", params, null, "2.0");
            this.configFilePath = result.get("path").getAsString();
        }

        public void fetchDevProductionMode() throws IOException, HTTPResponseException {
            Map<String, String> params = new HashMap<>();
            params.put("pid", this.pid);
            JsonObject result = HTTPRequest
                    .sendGETRequest("http://" + this.hostName + ":" + this.port + "/devproductionmode", params, null,
                            "2.0");
            this.devProductionMode = result.get("mode").getAsString();
        }
    }

    public static List<RunningProcess> getRunningProcesses(String installationDir) throws IOException {
        List<RunningProcess> result = new ArrayList<>();
        File dotStarted = new File(installationDir + ".started");
        if (!dotStarted.exists()) {
            return result;
        }
        File[] processes = dotStarted.listFiles();
        if (processes == null) {
            return result;
        }
        for (File process : processes) {
            String currPid = Files.readString(process.toPath());
            String[] splitted = process.getName().split("-");
            StringBuilder hostName = new StringBuilder();
            for (int i = 0; i < splitted.length - 1; i++) {
                hostName.append(splitted[i]);
                if (i != splitted.length - 2) {
                    hostName.append("-");
                }
            }
            String port = splitted[splitted.length - 1];
            result.add(new RunningProcess(hostName.toString(), port, currPid));
        }
        return result;
    }
}
