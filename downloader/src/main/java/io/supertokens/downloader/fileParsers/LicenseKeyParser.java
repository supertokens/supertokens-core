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

package io.supertokens.downloader.fileParsers;

import io.supertokens.downloader.exception.QuitProgramException;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class LicenseKeyParser {
    private static final String FILE_TO_READ = "licenseKey";
    private static final String MODE = "mode";
    private String mode;

    private static String extractStringData(String key, String line) {
        try {
            String result = line.split(key + "\":")[1].split(",")[0].trim();
            result = result.substring(1, result.length() - 2);
            return result;
        } catch (Exception e) {
            return null;
        }
    }

    public LicenseKeyParser() {
        // we have this in try catch since it's possible that the licenseKey is missing, in which case we still want
        // to enable the installation of the binary
        try {
            List<String> allLines = Files.readAllLines(Paths.get(FILE_TO_READ));
            String mode = null;

            for (String line : allLines) {
                mode = extractStringData(MODE, line);
            }
            if (mode == null) {
                throw new QuitProgramException(
                        "licenseKey doesn't seem to have valid content. Please redownload SuperTokens by visiting " +
                                "your " +
                                "SuperTokens dashboard.", null);
            }
            this.mode = mode;
        } catch (Exception e) {
            this.mode = null;
        }
    }


    public String getMode() {
        if (this.mode == null) {
            return "PRODUCTION";
        }
        return this.mode;
    }
}
