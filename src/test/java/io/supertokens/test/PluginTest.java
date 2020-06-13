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

package io.supertokens.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.supertokens.ProcessState;
import io.supertokens.version.VersionFile;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Plugin:
 * <p>
 * TODO: plugin tests - do later...
 * <p>
 * - plugin folder exists, with more than one storage plugin
 */


public class PluginTest {

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
    public void missingPluginFolderTest() throws Exception {
        String[] args = {"../"};

        try {
            //copy plugin directory to temp directory
            copyDirectoryToDirectory(new File(args[0] + "plugin"), new File(args[0] + "temp/plugin"));

            //delete plugin directory
            delete(new File(args[0] + "plugin"));

            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
            ProcessState.EventAndException e = process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.INIT_FAILURE);
            assertNotNull(e);
            assertEquals(e.exception.getMessage(),
                    "No database plugin found. Please redownload and install SuperTokens");

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

        } finally {
            //copy temp/plugin directory to plugin directory
            copyDirectoryToDirectory(new File(args[0] + "temp/plugin"), new File(args[0] + "plugin"));

            //delete temp/plugin directory
            delete(new File(args[0] + "temp/plugin"));
        }

    }

    @Test
    public void emptyPluginFolderTest() throws Exception {
        String[] args = {"../"};
        try {
            //copy plugin directory to temp/plugin directory
            copyDirectoryToDirectory(new File(args[0] + "plugin"), new File(args[0] + "temp/plugin"));

            //delete plugin directory
            delete(new File(args[0] + "plugin"));

            //create empty plugin directory
            new File(args[0] + "plugin").mkdir();

            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
            ProcessState.EventAndException e = process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.INIT_FAILURE);
            assertNotNull(e);
            assertEquals(e.exception.getMessage(),
                    "No database plugin found. Please redownload and install SuperTokens");

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

        } finally {
            //copy temp/plugin directory to plugin directory
            copyDirectoryToDirectory(new File(args[0] + "temp/plugin"), new File(args[0] + "plugin"));

            //delete temp/plugin directory
            delete(new File(args[0] + "temp/plugin"));
        }
    }

    @Test
    public void doesNotContainPluginTest() throws Exception {
        String[] args = {"../"};

        final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        String versionFilePath = args[0] + "version.yaml";
        VersionFile versionFile = mapper.readValue(new File(versionFilePath), VersionFile.class);

        String pluginName = versionFile.getPluginName() + "-plugin-" + versionFile.getPluginVersion() + ".jar";

        try {
            //copy storage plugin file to temp
            copyFile(new File(args[0] + "plugin/" + pluginName), new File(args[0] + "temp/"));

            //delete storage plugin
            delete(new File(args[0] + "plugin/" + pluginName));

            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
            ProcessState.EventAndException e = process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.INIT_FAILURE);
            assertNotNull(e);
            assertEquals(e.exception.getMessage(),
                    "No database plugin found. Please redownload and install SuperTokens");

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

        } finally {
            //copy storage plugin in temp to plugin directory
            copyFile(new File(args[0] + "temp/" + pluginName), new File(args[0] + "plugin/"));

            //delete storage plugin from temp
            delete(new File(args[0] + "temp/" + pluginName));

        }
    }

    private void copyDirectoryToDirectory(File src, File dest) throws IOException {
        File[] files = src.listFiles();
        assert files != null;
        for (File file : files) {
            if (file.isFile()) {
                copyFile(file, dest);
            }
        }
    }

    private void copyFile(File toCopy, File mainDestination) throws IOException {
        if (!mainDestination.exists()) {
            mainDestination.mkdirs();
        }
        Path to = Paths.get(mainDestination.getAbsolutePath() +
                File.separatorChar + toCopy.getName());

        Files.copy(toCopy.toPath(), to, StandardCopyOption.REPLACE_EXISTING);
    }

    private void delete(File toDelete) {
        if (!toDelete.exists()) {
            return;
        }
        if (toDelete.isDirectory()) {
            File[] files = toDelete.listFiles();
            assert files != null;
            for (File file : files) {
                delete(file);
            }
            toDelete.delete();
        } else {
            toDelete.delete();
        }

    }

//    @Test
//    public void moreThanOneStoragePluginTest() throws Exception {
//        String[] args = {"../"};
//        String installDir = "../";
//        final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
//        String versionFilePath = args[0] + "version.yaml";
//        VersionFile versionFile = mapper.readValue(new File(versionFilePath), VersionFile.class);
//
//        String pluginName = versionFile.getPluginName() + "-plugin-" + versionFile.getPluginVersion() + ".jar";
//        String pluginName1 =
//                versionFile.getPluginName() + "temp" + "-plugin-" + versionFile.getPluginVersion() + ".jar";
//
//
//        try {
//            ProcessBuilder pb = new ProcessBuilder("cp", "plugin/" + pluginName, "plugin/" + pluginName1);
//
//            pb.directory(new File(installDir));
//            Process process1 = pb.start();
//            process1.waitFor();
//
//            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
//            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.INIT_FAILURE));
//
//            process.kill();
//            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
//
//
//        } finally {
//            ProcessBuilder pb = new ProcessBuilder("rm", "plugin/" + pluginName1);
//
//            pb.directory(new File(installDir));
//            Process process1 = pb.start();
//            process1.waitFor();
//
//        }
//
//
//    }

}
