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

package io.supertokens.webserver.api;

import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.cliOptions.CLIOptions;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;

public class ConfigAPI extends WebserverAPI {
    private static final long serialVersionUID = -4641988458637882374L;

    public ConfigAPI(Main main) {
        super(main);
    }

    @Override
    public String getPath() {
        return "/config";
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        String pid = InputParser.getQueryParamOrThrowError(req, "pid", false);

        if ((ProcessHandle.current().pid() + "").equals(pid)) {
            String path = CLIOptions.get(main).getConfigFilePath() == null
                    ? CLIOptions.get(main).getInstallationPath() + "config.yaml"
                    : CLIOptions.get(main).getConfigFilePath();
            File f = new File(path);
            path = f.getAbsolutePath();
            JsonObject result = new JsonObject();
            result.addProperty("status", "OK");
            result.addProperty("path", path);
            super.sendJsonResponse(200, result, resp);
        } else {
            JsonObject result = new JsonObject();
            result.addProperty("status", "NOT ALLOWED");
            super.sendJsonResponse(200, result, resp);
        }
    }
}
