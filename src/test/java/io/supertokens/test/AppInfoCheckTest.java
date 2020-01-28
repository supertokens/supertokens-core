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

import io.supertokens.ProcessState.EventAndException;
import io.supertokens.ProcessState.PROCESS_STATE;
import io.supertokens.cronjobs.CronTaskTest;
import io.supertokens.cronjobs.appInfoCheck.AppInfoCheck;
import io.supertokens.licenseKey.LicenseKeyTestContent;
import io.supertokens.test.TestingProcessManager.TestingProcess;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.*;

public class AppInfoCheckTest {

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
    public void testNonTestingIntervalAndStartTime() throws Exception {
        String[] args = {"../", "DEV"};

        TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        assertEquals(AppInfoCheck.getInstance(process.getProcess()).getInitialWaitTimeSeconds(), 0);
        assertEquals(AppInfoCheck.getInstance(process.getProcess()).getIntervalTimeSeconds(), (5 * 60));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void testThatTwoAppsStartAndStopCorrectly() throws Exception {
        String[] args = {"../", "DEV"};

        TestingProcess process1 = TestingProcessManager.start(args);
        EventAndException startEvent1 = process1.checkOrWaitForEvent(PROCESS_STATE.STARTED);
        assertNotNull(startEvent1);

        Utils.setValueInConfig("port", "8081");
        TestingProcess process2 = TestingProcessManager.start(args);
        EventAndException startEvent2 = process2.checkOrWaitForEvent(PROCESS_STATE.STARTED);
        assertNotNull(startEvent2);

        process1.kill();
        EventAndException stopEvent1 = process1.checkOrWaitForEvent(PROCESS_STATE.STOPPED);
        assertNotNull(stopEvent1);

        process2.kill();
        EventAndException stopEvent2 = process2.checkOrWaitForEvent(PROCESS_STATE.STOPPED);
        assertNotNull(stopEvent2);

    }

    @Test
    public void testThatUsingDifferentAppIdsForMultipleProcessesCausesAnError() throws Exception {
        String[] args = {"../", "DEV"};

        TestingProcess process1 = TestingProcessManager.start(args, false);
        CronTaskTest.getInstance(process1.getProcess()).setIntervalInSeconds(AppInfoCheck.RESOURCE_KEY, 1);

        process1.startProcess();
        EventAndException startEvent1 = process1.checkOrWaitForEvent(PROCESS_STATE.STARTED);
        assertNotNull(startEvent1);

        Utils.setValueInConfig("port", "8081");
        TestingProcess process2 = TestingProcessManager.start(args, false);
        CronTaskTest.getInstance(process2.getProcess()).setIntervalInSeconds(AppInfoCheck.RESOURCE_KEY, 1);
        LicenseKeyTestContent.getInstance(process2.getProcess()).setKeyValue(LicenseKeyTestContent.APP_ID_KEY,
                "testAppId");

        process2.startProcess();
        EventAndException startEvent2 = process2.checkOrWaitForEvent(PROCESS_STATE.STARTED);
        assertNotNull(startEvent2);

        EventAndException licenseEvent1 = process1.checkOrWaitForEvent(PROCESS_STATE.APP_ID_MISMATCH);
        assertNotNull(licenseEvent1);

        EventAndException stopEvent1 = process1.checkOrWaitForEvent(PROCESS_STATE.STOPPED);
        assertNotNull(stopEvent1);

        EventAndException stopEvent2 = process2.checkOrWaitForEvent(PROCESS_STATE.STOPPED, 2000);
        assertNull(stopEvent2);

        process2.kill();
        stopEvent2 = process2.checkOrWaitForEvent(PROCESS_STATE.STOPPED);
        assertNotNull(stopEvent2);
    }

    @Test
    public void testThatUsingDifferentModesForMultipleProcessesCausesAnError() throws Exception {
        String[] args1 = {"../", "DEV"};
        String[] args2 = {"../", "PRODUCTION"};

        TestingProcess process1 = TestingProcessManager.start(args1, false);

        CronTaskTest testCheck1 = CronTaskTest.getInstance(process1.getProcess());
        testCheck1.setIntervalInSeconds(AppInfoCheck.RESOURCE_KEY, 1);

        process1.startProcess();
        EventAndException startEvent1 = process1.checkOrWaitForEvent(PROCESS_STATE.STARTED);
        assertNotNull(startEvent1);

        Utils.setValueInConfig("port", "8081");
        TestingProcess process2 = TestingProcessManager.start(args2, false);

        CronTaskTest testCheck2 = CronTaskTest.getInstance(process2.getProcess());
        testCheck2.setIntervalInSeconds(AppInfoCheck.RESOURCE_KEY, 1);

        process2.startProcess();
        EventAndException startEvent2 = process2.checkOrWaitForEvent(PROCESS_STATE.STARTED);
        assertNotNull(startEvent2);

        EventAndException modeEvent1 = process1.checkOrWaitForEvent(PROCESS_STATE.DEV_PROD_MODE_MISMATCH);
        assertNotNull(modeEvent1);

        EventAndException stopEvent1 = process1.checkOrWaitForEvent(PROCESS_STATE.STOPPED);
        assertNotNull(stopEvent1);

        EventAndException stopEvent2 = process2.checkOrWaitForEvent(PROCESS_STATE.STOPPED, 2000);
        assertNull(stopEvent2);

        process2.kill();
        stopEvent2 = process2.checkOrWaitForEvent(PROCESS_STATE.STOPPED);
        assertNotNull(stopEvent2);
    }
}
