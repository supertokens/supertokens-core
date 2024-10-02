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

package io.supertokens.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.supertokens.ProcessState;
import io.supertokens.version.Version;
import io.supertokens.version.VersionFile;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.io.File;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class VersionTest {
    @Rule
    public TestRule watchman = Utils.getOnFailure();

    @AfterClass
    public static void afterTesting() {
        Utils.afterTesting();
    }

    @Before
    public void beforeEach() {
        Utils.reset();
    }

    @Test
    public void simpleLoadingOfVersionTest() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        String versionFilePath = args[0] + "version.yaml";

        VersionFile versionFile = mapper.readValue(new File(versionFilePath), VersionFile.class);
        VersionFile versionFileProcess = Version.getVersion(process.getProcess());

        assertTrue(versionFile.getCoreVersion().equals(versionFileProcess.getCoreVersion())
                && versionFile.getPluginInterfaceVersion().equals(versionFileProcess.getPluginInterfaceVersion())
                && versionFile.getPluginName().equals(versionFileProcess.getPluginName())
                && versionFile.getPluginVersion().equals(versionFileProcess.getPluginVersion()));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    @Test
    public void versionFileMissingTest() throws Exception {
        String installDir = "../";

        try {

            ProcessBuilder pb = new ProcessBuilder("cp", "version.yaml", "temp/version.yaml");
            pb.directory(new File(installDir));
            Process process1 = pb.start();
            process1.waitFor();

            pb = new ProcessBuilder("rm", "version.yaml");
            pb.directory(new File(installDir));
            process1 = pb.start();
            process1.waitFor();

            String[] args = {"../"};
            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);

            ProcessState.EventAndException e = process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.INIT_FAILURE);
            assertNotNull(e);
            assertEquals(e.exception.getMessage(),
                    "java.io.FileNotFoundException: ../version.yaml (No such file or directory)");

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

        } finally {

            ProcessBuilder pb = new ProcessBuilder("cp", "temp/version.yaml", "version.yaml");
            pb.directory(new File(installDir));
            Process process1 = pb.start();
            process1.waitFor();

            pb = new ProcessBuilder("rm", "temp/version.yaml");
            pb.directory(new File(installDir));
            process1 = pb.start();
            process1.waitFor();
        }
    }

}
