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
        public final String basePath;
        public String configFilePath; // set via an API call

        RunningProcess(String hostName, String port, String pid, String basePath) {
            this.hostName = hostName;
            this.port = port;
            this.pid = pid;
            this.basePath = basePath;
        }

        public void fetchConfigFilePath() throws IOException, HTTPResponseException {
            Map<String, String> params = new HashMap<>();
            params.put("pid", this.pid);
            JsonObject result = HTTPRequest.sendGETRequest(
                    "http://" + this.hostName + ":" + this.port + this.basePath + "/config", params, null);
            this.configFilePath = result.get("path").getAsString();
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
            String[] dotStartedContent = Files.readString(process.toPath()).split("\n");
            String currPid = dotStartedContent[0];
            String basePath = dotStartedContent.length > 1 ? dotStartedContent[1] : "";
            String[] splitted = process.getName().split("-");
            StringBuilder hostName = new StringBuilder();
            for (int i = 0; i < splitted.length - 1; i++) {
                hostName.append(splitted[i]);
                if (i != splitted.length - 2) {
                    hostName.append("-");
                }
            }
            String port = splitted[splitted.length - 1];
            result.add(new RunningProcess(hostName.toString(), port, currPid, basePath));
        }
        return result;
    }
}
