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

package io.supertokens.cli.commandHandler.loadLicense;

import com.google.gson.JsonObject;
import io.supertokens.cli.Main;
import io.supertokens.cli.cliOptionsParsers.CLIOptionsParser;
import io.supertokens.cli.commandHandler.CommandHandler;
import io.supertokens.cli.exception.QuitProgramException;
import io.supertokens.cli.httpRequest.HTTPRequest;
import io.supertokens.cli.httpRequest.HTTPResponseException;
import io.supertokens.cli.logging.Logging;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LoadLicenseHandler extends CommandHandler {

    @Override
    public void doCommand(String installationDir, boolean viaInstaller, String[] args) {
        String path = CLIOptionsParser.parseOption("--path", args);
        String id = CLIOptionsParser.parseOption("--id", args);
        String actualValue = "";
        if (path == null && id == null) {
            Logging.error(
                    "Please provide either the path of the license key, or its license key ID\nUSAGE: " + getUsage());
            Main.exitCode = 1;
            return;
        }
        if (path != null && id != null) {
            Logging.error(
                    "Please provide exactly one optional argument. Either the path of the license key, or its " +
                            "license key ID\nUSAGE: " +
                            getUsage());
            Main.exitCode = 1;
            return;
        }
        try {
            if (path != null) {
                actualValue = Files.readString(new File(path).toPath());
            } else {
                actualValue = getLicenseKeyFromID(id);
            }
            File licenseKey = new File(installationDir + "licenseKey");
            if (!licenseKey.exists()) {
                licenseKey.createNewFile();
            }
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(licenseKey))) {
                writer.write(actualValue);
            }
        } catch (Exception e) {
            if ((e instanceof FileNotFoundException || e instanceof NoSuchFileException) && path != null) {
                if (e.getMessage().contains("Permission denied")) {
                    throw new QuitProgramException("Please try again with root permissions", e);
                }
                throw new QuitProgramException("Provided license key path is invalid or missing?", e);
            }
            if (e.getMessage().contains("Permission denied")) {
                throw new QuitProgramException("Please try again with root permissions", e);
            }
            throw new QuitProgramException("Error saving given license key", e);
        }
        Logging.info("Successfully loaded license key!");
    }

    private String getLicenseKeyFromID(String id) throws IOException {
        try {
            Map<String, String> params = new HashMap<>();
            params.put("licenseKeyId", id);
            JsonObject response = HTTPRequest
                    .sendGETRequest("https://api.supertokens.io/0/license-key/", params, 0, null);
            if (!response.has("latestLicenseKey")) {
                throw new QuitProgramException("'latestLicenseKey' key missing from response JSON", null);
            }
            return response.get("latestLicenseKey").getAsString();
        } catch (HTTPResponseException e) {
            throw new QuitProgramException("Loading license with ID '" + id + "' failed. Have you give the correct ID?",
                    null);
        }
    }

    @Override
    public String getUsage() {
        return "supertokens load-license [--path=<location>] [--id=<License key ID>]";
    }

    @Override
    public String getCommandName() {
        return "load-license [options]";
    }

    @Override
    public String getShortDescription() {
        return "Loads a provided licenseKey file or ID";
    }

    @Override
    public String getLongDescription() {
        return "Loads a provided licenseKey file if the --path option is used, otherwise downloads a license key " +
                "whose license key ID is specified via the --id option";
    }

    @Override
    protected List<Option> getOptionsAndDescription() {
        List<Option> options = new ArrayList<>();
        options.add(new Option("--path",
                "Specify a licenseKey path. This path can be relative or absolute. A licenseKey can be obtained from " +
                        "your " +
                        "SuperTokens dashboard. Any existing licenseKet will get overwritten. Example: \"--path=" +
                        "./downloads/licenseKey\""));
        options.add(new Option("--id",
                "Specify a licenseKey ID that you want to load. A licenseKey ID can be obtained from your SuperTokens" +
                        " dashboard. Any existing licenseKey will get overwritten. Example: " +
                        "\"--id=bdsf32e5-2b9f-42b4-aaz3-36fr412b6bc0_v1\""));
        return options;
    }

}