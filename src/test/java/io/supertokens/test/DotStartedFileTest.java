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

import io.supertokens.ProcessState;
import io.supertokens.config.Config;
import io.supertokens.test.httpRequest.HttpRequestForTesting;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.*;

public class DotStartedFileTest {
    @Rule
    public TestRule watchman = Utils.getOnFailure();

    @Rule
    public TestRule retryFlaky = Utils.retryFlakyTest();

    @AfterClass
    public static void afterTesting() {
        Utils.afterTesting();
    }

    @Before
    public void beforeEach() {
        Utils.reset();
    }

    @Test
    public void fiveProcessInParallelDotStartedFileTest() throws Exception {
        // Use dynamic port allocation to avoid conflicts during parallel test execution
        TestingProcessManager.TestingProcess process1 = TestingProcessManager.startIsolatedProcess(new String[]{"../"}, true);
        assertNotNull(process1.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        TestingProcessManager.TestingProcess process2 = TestingProcessManager.startIsolatedProcess(new String[]{"../"}, true);
        assertNotNull(process2.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        TestingProcessManager.TestingProcess process3 = TestingProcessManager.startIsolatedProcess(new String[]{"../"}, true);
        assertNotNull(process3.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        TestingProcessManager.TestingProcess process4 = TestingProcessManager.startIsolatedProcess(new String[]{"../"}, true);
        assertNotNull(process4.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        TestingProcessManager.TestingProcess process5 = TestingProcessManager.startIsolatedProcess(new String[]{"../"}, true);
        assertNotNull(process5.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        File[] flist = new File("../.started" + System.getProperty("org.gradle.test.worker", "")).listFiles();
        assert flist != null;
        int processCounter = flist.length;
        assertEquals(processCounter, 5);

        process1.kill();
        assertNotNull(process1.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

        processCounter--;
        flist = new File("../.started" + System.getProperty("org.gradle.test.worker", "")).listFiles();
        assert flist != null;
        assertEquals(processCounter, flist.length);

        process2.kill();
        assertNotNull(process2.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

        processCounter--;
        flist = new File("../.started" + System.getProperty("org.gradle.test.worker", "")).listFiles();
        assert flist != null;
        assertEquals(processCounter, flist.length);

        process3.kill();
        assertNotNull(process3.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

        processCounter--;
        flist = new File("../.started" + System.getProperty("org.gradle.test.worker", "")).listFiles();
        assert flist != null;
        assertEquals(processCounter, flist.length);

        process4.kill();
        assertNotNull(process4.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

        processCounter--;
        flist = new File("../.started" + System.getProperty("org.gradle.test.worker", "")).listFiles();
        assert flist != null;
        assertEquals(processCounter, flist.length);

        process5.kill();
        assertNotNull(process5.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

        processCounter--;
        flist = new File("../.started" + System.getProperty("org.gradle.test.worker", "")).listFiles();
        assert flist != null;
        assertEquals(processCounter, flist.length);
    }

    @Test
    public void dotStartedFileNameAndContentTest() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String host = "localhost";
        String basePathCheck = "/test";
        String port = "" + HttpRequestForTesting.corePort;
        String hostPortNameCheck = host + "-" + port;

        File loc = new File("../.started" + System.getProperty("org.gradle.test.worker", ""));

        File[] dotStartedNameAndContent = loc.listFiles();
        assert dotStartedNameAndContent != null;
        assertEquals(1, dotStartedNameAndContent.length);
        assertEquals(dotStartedNameAndContent[0].getName(), hostPortNameCheck);

        String[] dotStartedContent = Files.readString(Paths.get(dotStartedNameAndContent[0].getPath())).split("\n");
        String line = dotStartedContent[0];
        assertEquals(line, Long.toString(ProcessHandle.current().pid()));
        line = dotStartedContent.length > 1 ? dotStartedContent[1] : "";
        assertEquals(line, Config.getConfig(process.getProcess()).getBasePath());
        assertEquals(line, "");

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

        // Ensure that base_path is set in .started file
        Utils.setValueInConfig("base_path", basePathCheck);

        process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        port = "" + HttpRequestForTesting.corePort;
        hostPortNameCheck = host + "-" + port;

        dotStartedNameAndContent = loc.listFiles();
        assert dotStartedNameAndContent != null;
        assertEquals(1, dotStartedNameAndContent.length);
        assertEquals(dotStartedNameAndContent[0].getName(), hostPortNameCheck);

        dotStartedContent = Files.readString(Paths.get(dotStartedNameAndContent[0].getPath())).split("\n");
        line = dotStartedContent[0];
        assertEquals(line, Long.toString(ProcessHandle.current().pid()));
        line = dotStartedContent.length > 1 ? dotStartedContent[1] : "";
        assertEquals(line, Config.getConfig(process.getProcess()).getBasePath());
        assertEquals(line, basePathCheck);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void processFailToStartDotStartedFileTest() throws Exception {
        String[] args = {"../"};
        String installDir = "../";

        Utils.setValueInConfig("access_token_validity", "-1");

        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.INIT_FAILURE));

        File dotStartedFile = new File("../.started/localhost-3567");
        assertFalse(dotStartedFile.isFile());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void dotStartedFileAtTempDirLocation() throws Exception {
        String tempDirLocation = new File("../tempDir/").getAbsolutePath();
        String[] args = {"../", "tempDirLocation=" + tempDirLocation};

        String host = "localhost";
        String port = "8081";
        String hostPortNameCheck = host + "-" + port;

        Utils.setValueInConfig("port", port);

        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        File loc = new File(tempDirLocation + "/.started" + System.getProperty("org.gradle.test.worker", ""));

        File[] dotStartedNameAndContent = loc.listFiles();
        assert dotStartedNameAndContent != null;
        assertEquals(1, dotStartedNameAndContent.length);
        assertEquals(dotStartedNameAndContent[0].getName(), hostPortNameCheck);

        String[] dotStartedContent = Files.readString(Paths.get(dotStartedNameAndContent[0].getPath())).split("\n");
        String line = dotStartedContent[0];
        assertEquals(line, Long.toString(ProcessHandle.current().pid()));
        line = dotStartedContent.length > 1 ? dotStartedContent[1] : "";
        assertEquals(line, Config.getConfig(process.getProcess()).getBasePath());
        assertEquals(line, "");

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
