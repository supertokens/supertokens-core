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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * core_version: X.Y.Z
 * plugin_interface_version: X.Y.Z
 * plugin_version: X.Y.Z
 * plugin_name: name
 */
public class VersionFileParser {
    private static final String FILE_TO_READ = "version.yaml";
    private static final String CORE_VERSION = "core_version";
    private static final String PLUGIN_INTERFACE_VERSION = "plugin_interface_version";
    private static final String PLUGIN_VERSION = "plugin_version";
    private static final String PLUGIN_NAME = "plugin_name";
    private final String coreVersion;
    private final String pluginName;
    private final String pluginVersion;
    private final String pluginInterfaceVersion;

    public VersionFileParser() throws IOException {
        List<String> allLines = Files.readAllLines(Paths.get(FILE_TO_READ));
        String coreVersion = null;
        String pluginInterfaceVersion = null;
        String pluginVersion = null;
        String pluginName = null;
        for (String line : allLines) {
            if (line.contains(CORE_VERSION)) {
                coreVersion = line.split(":")[1].trim();
            } else if (line.contains(PLUGIN_INTERFACE_VERSION)) {
                pluginInterfaceVersion = line.split(":")[1].trim();
            } else if (line.contains(PLUGIN_VERSION)) {
                pluginVersion = line.split(":")[1].trim();
            } else if (line.contains(PLUGIN_NAME)) {
                pluginName = line.split(":")[1].trim();
            }
        }
        if (coreVersion == null || pluginInterfaceVersion == null || pluginVersion == null ||
                pluginName == null) {
            throw new QuitProgramException(
                    "version.yaml doesn't seem to have valid content. Please redownload SuperTokens by visiting " +
                            "your SuperTokens dashboard.", null);
        }
        this.coreVersion = coreVersion;
        this.pluginInterfaceVersion = pluginInterfaceVersion;
        this.pluginName = pluginName;
        this.pluginVersion = pluginVersion;
    }

    public String getCoreVersion() {
        return this.coreVersion;
    }

    public String getPluginVersion() {
        return this.pluginVersion;
    }

    public String getPluginName() {
        return this.pluginName;
    }

    public String getPluginInterfaceVersion() {
        return this.pluginInterfaceVersion;
    }
}
