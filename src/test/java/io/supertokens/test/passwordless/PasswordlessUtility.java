/*
 *    Copyright (c) 2021, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.test.passwordless;

import io.supertokens.ProcessState;
import io.supertokens.passwordless.Passwordless;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;

import static org.junit.Assert.assertNotNull;

/**
 * Utility class for Passwordless tests
 */
public class PasswordlessUtility {

    // CONSTANTS
    public static final String EMAIL = "test@example.com";
    public static final String PHONE_NUMBER = "+442071838750";

    /**
     * Helper function to create a user
     *
     * @param email
     * @param phoneNumber
     * @throws Exception
     */
    public static Passwordless.ConsumeCodeResponse createUserWith(TestingProcessManager.TestingProcess process,
                                                                  String email, String phoneNumber) throws Exception {

        Passwordless.CreateCodeResponse createCodeResponse = Passwordless.createCode(process.getProcess(), email,
                phoneNumber, null, null);
        assertNotNull(createCodeResponse);

        Passwordless.ConsumeCodeResponse consumeCodeResponse = Passwordless.consumeCode(process.getProcess(),
                createCodeResponse.deviceId, createCodeResponse.deviceIdHash, createCodeResponse.userInputCode, null);
        assertNotNull(consumeCodeResponse);

        return consumeCodeResponse;
    }

}
