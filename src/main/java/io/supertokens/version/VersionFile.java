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

package io.supertokens.version;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.supertokens.exceptions.QuitProgramException;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VersionFile {
    @JsonProperty
    private String core_version;

    @JsonProperty
    private String plugin_interface_version;

    @JsonProperty
    private String plugin_version;

    @JsonProperty
    private String plugin_name;

    void validate() {
        if (core_version == null || plugin_interface_version == null) {
            throw new QuitProgramException(
                    "version.yaml file seems to be corrupted. Please redownload and install SuperTokens from "
                            + "https://supertokens.com/");
        }
    }

    public String getCoreVersion() {
        return core_version;
    }

    public String getPluginInterfaceVersion() {
        return plugin_interface_version;
    }

    public String getPluginVersion() {
        if (plugin_version == null) {
            // in memory db
            return getCoreVersion();
        }
        return plugin_version;
    }

    public String getPluginName() {
        if (plugin_name == null) {
            return "sqlite";
        }
        return plugin_name;
    }
}
