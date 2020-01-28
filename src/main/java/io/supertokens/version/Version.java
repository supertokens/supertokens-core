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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.supertokens.Main;
import io.supertokens.ResourceDistributor;
import io.supertokens.exceptions.QuitProgramException;
import io.supertokens.output.Logging;

import java.io.File;
import java.io.IOException;

public class Version extends ResourceDistributor.SingletonResource {

    private static final String RESOURCE_KEY = "io.supertokens.version.Version";
    private final VersionFile version;
    private final Main main;

    private Version(Main main, String versionFilePath) {
        this.main = main;
        try {
            this.version = loadVersionFile(versionFilePath);
        } catch (IOException e) {
            throw new QuitProgramException(e);
        }
    }

    private static Version getInstance(Main main) {
        return (Version) main.getResourceDistributor().getResource(RESOURCE_KEY);
    }

    public static void loadVersion(Main main, String versionFilePath) {
        if (getInstance(main) != null) {
            return;
        }
        main.getResourceDistributor().setResource(RESOURCE_KEY, new Version(main, versionFilePath));
    }

    public static VersionFile getVersion(Main main) {
        if (getInstance(main) == null) {
            throw new QuitProgramException("Please call loadVersion() before calling getVersion()");
        }
        return getInstance(main).version;
    }

    private VersionFile loadVersionFile(String versionFilePath) throws IOException {
        Logging.info(main, "Loading supertokens version.yaml file.");
        final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        VersionFile version = mapper.readValue(new File(versionFilePath), VersionFile.class);
        version.validate();
        return version;
    }
}
