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

package io.supertokens.emailverification;

import io.supertokens.Main;
import io.supertokens.ResourceDistributor;
import io.supertokens.config.Config;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import org.jetbrains.annotations.TestOnly;

public class EmailVerificationTest extends ResourceDistributor.SingletonResource {
    private static final String RESOURCE_ID = "io.supertokens.emailverification.EmailVerificationTest";
    private Long emailVerificationTokenLifetimeMS = null;
    private Main main;

    private EmailVerificationTest(Main main) {
        this.main = main;
    }

    public static EmailVerificationTest getInstance(Main main) {
        try {
            return (EmailVerificationTest) main.getResourceDistributor()
                    .getResource(new TenantIdentifier(null, null, null), RESOURCE_ID);
        } catch (TenantOrAppNotFoundException e) {
            return (EmailVerificationTest) main.getResourceDistributor()
                    .setResource(new TenantIdentifier(null, null, null), RESOURCE_ID, new EmailVerificationTest(main));
        }
    }

    @TestOnly
    public void setEmailVerificationTokenLifetime(long intervalMS) {
        this.emailVerificationTokenLifetimeMS = intervalMS;
    }

    public long getEmailVerificationTokenLifetime() {
        if (this.emailVerificationTokenLifetimeMS == null) {
            return Config.getBaseConfig(this.main).getEmailVerificationTokenLifetime();
        }
        return this.emailVerificationTokenLifetimeMS;
    }
}
