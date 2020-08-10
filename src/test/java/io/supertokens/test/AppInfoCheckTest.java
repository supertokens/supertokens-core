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
        String[] args = {"../"};

        TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        assertEquals(AppInfoCheck.getInstance(process.getProcess()).getInitialWaitTimeSeconds(), 0);
        assertEquals(AppInfoCheck.getInstance(process.getProcess()).getIntervalTimeSeconds(), (5 * 60));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void testThatTwoAppsStartAndStopCorrectly() throws Exception {
        String[] args = {"../"};

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
        String[] args = {"../"};

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
}
