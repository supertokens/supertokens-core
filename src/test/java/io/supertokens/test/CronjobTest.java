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

import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.cronjobs.CronTask;
import io.supertokens.cronjobs.Cronjobs;
import io.supertokens.exceptions.QuitProgramException;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class CronjobTest {

    static int normalCronjobCounter = 0;
    static int errorCronjobCounter = 0;

    static class QuitProgramExceptionCronjob extends CronTask {

        private static final String RESOURCE_ID = "io.supertokens.test.CronjobTest" + ".QuitProgramExceptionCronjob";

        private QuitProgramExceptionCronjob(Main main, List<List<TenantIdentifier>> tenants) {
            super("QuitProgramExceptionCronjob", main, tenants, false);
        }

        public static QuitProgramExceptionCronjob getInstance(Main main) {
            try {
                return (QuitProgramExceptionCronjob) main.getResourceDistributor()
                        .getResource(new TenantIdentifier(null, null, null), RESOURCE_ID);
            } catch (TenantOrAppNotFoundException e) {
                List<TenantIdentifier> tenants = new ArrayList<>();
                tenants.add(new TenantIdentifier(null, null, null));
                List<List<TenantIdentifier>> finalList = new ArrayList<>();
                finalList.add(tenants);
                return (QuitProgramExceptionCronjob) main.getResourceDistributor()
                        .setResource(new TenantIdentifier(null, null, null), RESOURCE_ID,
                                new QuitProgramExceptionCronjob(main, finalList));
            }
        }

        @Override
        protected void doTaskPerStorage(Storage storage) {
            throw new QuitProgramException("Cronjob Threw QuitProgramException");

        }

        @Override
        public int getIntervalTimeSeconds() {
            return 1;
        }

        @Override
        public int getInitialWaitTimeSeconds() {
            return 0;
        }
    }

    static class ErrorCronjob extends CronTask {

        private static final String RESOURCE_ID = "io.supertokens.test.CronjobTest.ErrorCronjob";

        private ErrorCronjob(Main main, List<List<TenantIdentifier>> tenants) {
            super("ErrorCronjob", main, tenants, false);
        }

        public static ErrorCronjob getInstance(Main main) {
            try {
                return (ErrorCronjob) main.getResourceDistributor()
                        .getResource(new TenantIdentifier(null, null, null), RESOURCE_ID);
            } catch (TenantOrAppNotFoundException e) {
                List<TenantIdentifier> tenants = new ArrayList<>();
                tenants.add(new TenantIdentifier(null, null, null));
                List<List<TenantIdentifier>> finalList = new ArrayList<>();
                finalList.add(tenants);
                return (ErrorCronjob) main.getResourceDistributor()
                        .setResource(RESOURCE_ID, new ErrorCronjob(main, finalList));
            }
        }

        @Override
        protected void doTaskPerStorage(Storage s) throws Exception {
            errorCronjobCounter++;
            throw new Exception("ERROR thrown from ErrorCronjobTest");

        }

        @Override
        public int getIntervalTimeSeconds() {
            return 1;
        }

        @Override
        public int getInitialWaitTimeSeconds() {
            return 0;
        }
    }

    static class NormalCronjob extends CronTask {

        private static final String RESOURCE_ID = "io.supertokens.test.CronjobTest.NormalCronjob";

        private NormalCronjob(Main main, List<List<TenantIdentifier>> tenants) {
            super("NormalCronjob", main, tenants, false);
        }

        public static NormalCronjob getInstance(Main main) {
            try {
                return (NormalCronjob) main.getResourceDistributor()
                        .getResource(new TenantIdentifier(null, null, null), RESOURCE_ID);
            } catch (TenantOrAppNotFoundException e) {
                List<TenantIdentifier> tenants = new ArrayList<>();
                tenants.add(new TenantIdentifier(null, null, null));
                List<List<TenantIdentifier>> finalList = new ArrayList<>();
                finalList.add(tenants);
                return (NormalCronjob) main.getResourceDistributor()
                        .setResource(new TenantIdentifier(null, null, null), RESOURCE_ID,
                                new NormalCronjob(main, finalList));
            }
        }

        @Override
        protected void doTaskPerStorage(Storage s) {
            normalCronjobCounter++;
        }

        @Override
        public int getIntervalTimeSeconds() {
            return 1;
        }

        @Override
        public int getInitialWaitTimeSeconds() {
            return 0;
        }
    }

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
    public void testThatCronjobThrowsQuitProgramExceptionAndQuits() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);

        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        Cronjobs.addCronjob(process.getProcess(), QuitProgramExceptionCronjob.getInstance(process.getProcess()));

        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

        process.kill();

    }

    @Test
    public void testThatCronjobThrowsError() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);

        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        Cronjobs.addCronjob(process.getProcess(), ErrorCronjob.getInstance(process.getProcess()));

        ProcessState.EventAndException e = process
                .checkOrWaitForEvent(ProcessState.PROCESS_STATE.CRON_TASK_ERROR_LOGGING);
        assertNotNull(e);
        assertEquals(e.exception.getMessage(), "ERROR thrown from ErrorCronjobTest");

        Thread.sleep(5000);

        assertTrue(errorCronjobCounter >= 4 && errorCronjobCounter <= 8);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    @Test
    public void testNormalCronjob() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        assertEquals(normalCronjobCounter, 0);
        Cronjobs.addCronjob(process.getProcess(), NormalCronjob.getInstance(process.getProcess()));

        Thread.sleep(5000);
        assertTrue(normalCronjobCounter > 3 && normalCronjobCounter < 8);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

}
