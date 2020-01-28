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

import io.supertokens.ProcessState;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.io.*;

import static org.junit.Assert.*;

public class DotStartedFileTest {
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
    public void fiveProcessInParallelDotStartedFileTest() throws Exception {
        String[] args = {"../", "DEV"};

        TestingProcessManager.TestingProcess process1 = TestingProcessManager.start(args);
        assertNotNull(process1.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        Utils.setValueInConfig("port", "8081");

        TestingProcessManager.TestingProcess process2 = TestingProcessManager.start(args);
        assertNotNull(process2.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        Utils.setValueInConfig("port", "8082");

        TestingProcessManager.TestingProcess process3 = TestingProcessManager.start(args);
        assertNotNull(process3.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        Utils.setValueInConfig("port", "8083");

        TestingProcessManager.TestingProcess process4 = TestingProcessManager.start(args);
        assertNotNull(process4.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        Utils.setValueInConfig("port", "8084");

        TestingProcessManager.TestingProcess process5 = TestingProcessManager.start(args);
        assertNotNull(process5.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        File[] flist = new File("../.started").listFiles();
        assert flist != null;
        int processCounter = flist.length;
        assertEquals(processCounter, 5);

        process1.kill();
        assertNotNull(process1.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

        processCounter--;
        flist = new File("../.started").listFiles();
        assert flist != null;
        assertEquals(processCounter, flist.length);

        process2.kill();
        assertNotNull(process2.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

        processCounter--;
        flist = new File("../.started").listFiles();
        assert flist != null;
        assertEquals(processCounter, flist.length);

        process3.kill();
        assertNotNull(process3.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

        processCounter--;
        flist = new File("../.started").listFiles();
        assert flist != null;
        assertEquals(processCounter, flist.length);

        process4.kill();
        assertNotNull(process4.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

        processCounter--;
        flist = new File("../.started").listFiles();
        assert flist != null;
        assertEquals(processCounter, flist.length);

        process5.kill();
        assertNotNull(process5.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

        processCounter--;
        flist = new File("../.started").listFiles();
        assert flist != null;
        assertEquals(processCounter, flist.length);
    }


    @Test
    public void dotStartedFileNameAndContentTest() throws Exception {
        String[] args = {"../", "DEV"};
        String host = "localhost";
        String port = "8081";
        String hostPortNameCheck = host + "-" + port;

        Utils.setValueInConfig("port", port);

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        File loc = new File("../.started");

        File[] dotStartedNameAndContent = loc.listFiles();
        assert dotStartedNameAndContent != null;
        assertEquals(1, dotStartedNameAndContent.length);
        assertEquals(dotStartedNameAndContent[0].getName(), hostPortNameCheck);

        try (InputStream is = new FileInputStream(dotStartedNameAndContent[0].getPath());
             BufferedReader buf = new BufferedReader(new InputStreamReader(is))) {

            String line = buf.readLine();
            assertEquals(line, Long.toString(ProcessHandle.current().pid()));

        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    @Test
    public void processFailToStartDotStartedFileTest() throws Exception {
        String[] args = {"../", "DEV"};
        String installDir = "../";

        ProcessBuilder pb = new ProcessBuilder("rm", "./licenseKey");
        pb.directory(new File(installDir));
        Process process1 = pb.start();
        process1.waitFor();

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.INIT_FAILURE));

        File dotStartedFile = new File("../.started/localhost-3567");
        assertFalse(dotStartedFile.isFile());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
