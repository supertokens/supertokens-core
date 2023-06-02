/*
 *    Copyright (c) 2022, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.featureflag;

import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.featureflag.exceptions.InvalidLicenseKeyException;
import io.supertokens.featureflag.exceptions.NoLicenseKeyFoundException;
import io.supertokens.httpRequest.HttpResponseException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import org.jetbrains.annotations.TestOnly;

import java.io.IOException;

public interface EEFeatureFlagInterface {
    @TestOnly
    void updateEnabledFeaturesValueReadFromDbTime(long newTime);

    void constructor(Main main, AppIdentifier appIdentifier);

    EE_FEATURES[] getEnabledFeatures() throws StorageQueryException, TenantOrAppNotFoundException;

    void syncFeatureFlagWithLicenseKey() throws StorageQueryException, HttpResponseException, IOException,
            InvalidLicenseKeyException, TenantOrAppNotFoundException;

    void setLicenseKeyAndSyncFeatures(String licenseKey)
            throws StorageQueryException, HttpResponseException, IOException, InvalidLicenseKeyException,
            TenantOrAppNotFoundException;

    void removeLicenseKeyAndSyncFeatures()
            throws StorageQueryException, HttpResponseException, IOException, TenantOrAppNotFoundException;

    String getLicenseKeyFromDb() throws NoLicenseKeyFoundException, StorageQueryException, TenantOrAppNotFoundException;

    @TestOnly
    Boolean getIsLicenseKeyPresent();

    JsonObject getPaidFeatureStats() throws StorageQueryException, TenantOrAppNotFoundException;
}
