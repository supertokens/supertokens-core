/*
 *    Copyright (c) 2024, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.webserver.api.emailpassword;

import io.supertokens.Main;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.multitenancy.MultitenancyHelper;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.pluginInterface.multitenancy.TenantConfig;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.utils.SemVer;
import jakarta.servlet.ServletException;

public class Utils {
    public static void assertIfEmailPasswordIsEnabledForTenant(Main main, TenantIdentifier tenantIdentifier,
                                                               SemVer version) throws ServletException {
        TenantConfig config = Multitenancy.getTenantInfo(main, tenantIdentifier);
        if (!MultitenancyHelper.isEmailPasswordEnabled(config, version)) {
            throw new ServletException(new BadPermissionException("Email password login not enabled for tenant"));
        }
    }
}
