/*
 *    Copyright (c) 2023, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.test.multitenant;

import io.supertokens.ProcessState;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.multitenancy.exception.CannotModifyBaseConfigException;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.TenantConfig;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.multitenant.generator.ConfigGenerator;
import io.supertokens.thirdparty.InvalidProviderConfigException;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

import static org.junit.Assert.*;

public class RandomConfigTest {
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
    public void randomlyTestLoadConfig()
            throws InterruptedException, ClassNotFoundException, InvocationTargetException, NoSuchMethodException,
            IllegalAccessException, InstantiationException, StorageQueryException,
            FeatureNotEnabledException, IOException, CannotModifyBaseConfigException, BadPermissionException,
            TenantOrAppNotFoundException {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.LOADING_ALL_TENANT_CONFIG));

        int okCount = 0;
        int notOkCount = 0;

        for (int i = 0; i < 10000; i++) {
            ConfigGenerator.GeneratedValueAndExpectation generated = ConfigGenerator.generate(TenantConfig.class);
            boolean isOk = ConfigGenerator.isOk(generated.expectation);

            try {
                TenantConfig tenantConfig = (TenantConfig) generated.value;
                Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantIdentifier(null, null, null),
                        tenantConfig);
                if (!isOk) {
                    List<String> exceptions = ConfigGenerator.getExceptions(generated.expectation);
                    System.out.print("No exception was raised: ");
                    System.out.println(exceptions);
                }
                assertTrue(isOk); // Assert that config was expected to be valid
                okCount++;

                TenantConfig persistedTenantConfig = Multitenancy.getTenantInfo(process.getProcess(),
                        tenantConfig.tenantIdentifier);
                assertTrue(tenantConfig.deepEquals(persistedTenantConfig));

            } catch (InvalidProviderConfigException | InvalidConfigException e) {
                assertFalse(isOk);
                boolean exceptionMatched = ConfigGenerator.matchExceptionInExpectation(e.getMessage(),
                        generated.expectation);
                if (!exceptionMatched) {
                    List<String> exceptions = ConfigGenerator.getExceptions(generated.expectation);
                    System.out.printf("[%s] was not matched: ", e.getMessage());
                    System.out.println(exceptions);
                }
                assertTrue(exceptionMatched);
                notOkCount++;
            }
        }

        System.out.printf("Tested %d valid configs and %d invalid configs", okCount, notOkCount);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
