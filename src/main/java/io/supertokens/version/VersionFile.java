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
        if (core_version == null || plugin_interface_version == null || plugin_version == null || plugin_name == null) {
            throw new QuitProgramException(
                    "version.yaml file seems to be corrupted. Please redownload and install SuperTokens from " +
                            "https://supertokens.io/dashboard");
        }
    }

    public String getCoreVersion() {
        return core_version;
    }

    public String getPluginInterfaceVersion() {
        return plugin_interface_version;
    }

    public String getPluginVersion() {
        return plugin_version;
    }

    public String getPluginName() {
        return plugin_name;
    }
}
