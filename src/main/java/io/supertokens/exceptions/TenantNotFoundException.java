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

package io.supertokens.exceptions;

public class TenantNotFoundException extends Exception {

    private static final long serialVersionUID = 1L;
    private String tenantId;

    public TenantNotFoundException(String connectionUriDomain, String tenantId) {
        super("Tenant with the following connectionURIDomain and tenantId not found: (" + connectionUriDomain +
                ", " + tenantId + ")");
        this.tenantId = tenantId;
    }

    public String getTenantId() {
        if (this.tenantId == null || this.tenantId.equals("")) {
            return "defaultTenantId";
        }
        return this.tenantId;
    }
}
