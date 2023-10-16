/*
 *    Copyright (c) 2023, SuperTokens, Inc. All rights reserved.
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
