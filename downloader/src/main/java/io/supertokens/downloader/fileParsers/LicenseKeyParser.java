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
