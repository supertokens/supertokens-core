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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.supertokens.Main;
import io.supertokens.ResourceDistributor;
import io.supertokens.exceptions.QuitProgramException;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.output.Logging;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;

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
        try {
            return (Version) main.getResourceDistributor()
                    .getResource(new TenantIdentifier(null, null, null), RESOURCE_KEY);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void loadVersion(Main main, String versionFilePath) {
        main.getResourceDistributor()
                .setResource(new TenantIdentifier(null, null, null), RESOURCE_KEY, new Version(main, versionFilePath));
    }

    public static VersionFile getVersion(Main main) {
        if (getInstance(main) == null) {
            throw new QuitProgramException("Please call loadVersion() before calling getVersion()");
        }
        return getInstance(main).version;
    }

    private VersionFile loadVersionFile(String versionFilePath) throws IOException {
        Logging.info(main, TenantIdentifier.BASE_TENANT, "Loading supertokens version.yaml file.", true);
        final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        VersionFile version = mapper.readValue(new File(versionFilePath), VersionFile.class);
        version.validate();
        return version;
    }
}
