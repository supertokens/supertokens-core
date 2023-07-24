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
import io.supertokens.ResourceDistributor;
import io.supertokens.featureflag.exceptions.InvalidLicenseKeyException;
import io.supertokens.featureflag.exceptions.NoLicenseKeyFoundException;
import io.supertokens.httpRequest.HttpResponseException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.storageLayer.StorageLayer;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

public class FeatureFlag extends ResourceDistributor.SingletonResource {

    private static final String RESOURCE_KEY = "io.supertokens.featureflag.FeatureFlag";
    private final EEFeatureFlagInterface eeFeatureFlag;
    private static URLClassLoader ucl = null;
    private Main main;

    public static EEFeatureFlagInterface getNewEEFeatureFlagInterfaceInstance(Main main, AppIdentifier appIdentifier) {
        if (FeatureFlag.ucl == null) {
            return null;
        } else {
            EEFeatureFlagInterface result = null;
            ServiceLoader<EEFeatureFlagInterface> sl = ServiceLoader.load(EEFeatureFlagInterface.class, ucl);
            Iterator<EEFeatureFlagInterface> it = sl.iterator();
            if (it.hasNext()) {
                result = it.next();
            }
            if (result != null) {
                result.constructor(main, appIdentifier);
            }
            return result;
        }
    }

    private FeatureFlag(Main main, String eeFolderPath) throws MalformedURLException {
        this.main = main;
        String locationForTest = FeatureFlagTestContent.getInstance(main)
                .getValue(FeatureFlagTestContent.EE_FOLDER_LOCATION);
        if (locationForTest != null) {
            eeFolderPath = locationForTest;
        }
        File loc = new File(eeFolderPath);
        EEFeatureFlagInterface eeLayerTemp = null;

        File[] flist = loc.listFiles(file -> file.getPath().toLowerCase().endsWith(".jar"));

        if (flist != null) {
            URL[] urls = new URL[flist.length];
            for (int i = 0; i < flist.length; i++) {
                urls[i] = flist[i].toURI().toURL();
            }
            if (FeatureFlag.ucl == null) {
                // we have this as a static variable because
                // in prod, this is loaded just once anyway.
                // During testing, we just want to load the jars
                // once too cause the JARs don't change across tests either.
                FeatureFlag.ucl = new URLClassLoader(urls);
            }
        }
        this.eeFeatureFlag = getNewEEFeatureFlagInterfaceInstance(main, new AppIdentifier(null, null));
    }

    private FeatureFlag(Main main, AppIdentifier appIdentifier) {
        this.main = main;
        this.eeFeatureFlag = getNewEEFeatureFlagInterfaceInstance(main, appIdentifier);
    }

    public JsonObject getPaidFeatureStats() throws StorageQueryException, TenantOrAppNotFoundException {
        if (this.eeFeatureFlag == null) {
            return new JsonObject();
        }
        return eeFeatureFlag.getPaidFeatureStats();
    }

    @TestOnly
    public static FeatureFlag getInstance(Main main) {
        try {
            return (FeatureFlag) main.getResourceDistributor()
                    .getResource(new AppIdentifier(null, null), RESOURCE_KEY);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    public static FeatureFlag getInstance(Main main, AppIdentifier appIdentifier) throws TenantOrAppNotFoundException {
        return (FeatureFlag) main.getResourceDistributor()
                .getResource(appIdentifier, RESOURCE_KEY);
    }

    public static void initForBaseTenant(Main main, String eeFolderPath) throws MalformedURLException {
        main.getResourceDistributor().setResource(new TenantIdentifier(null, null, null), RESOURCE_KEY,
                new FeatureFlag(main, eeFolderPath));
    }

    public static void loadForAllTenants(Main main, List<AppIdentifier> apps,
                                         List<TenantIdentifier> tenantsThatChanged) {
        try {
            main.getResourceDistributor().withResourceDistributorLock(() -> {
                Map<ResourceDistributor.KeyClass, ResourceDistributor.SingletonResource> existingResources =
                        main.getResourceDistributor()
                                .getAllResourcesWithResourceKey(RESOURCE_KEY);
                main.getResourceDistributor().clearAllResourcesWithResourceKey(RESOURCE_KEY);
                for (AppIdentifier app : apps) {
                    ResourceDistributor.SingletonResource resource = existingResources.get(
                            new ResourceDistributor.KeyClass(
                                    app,
                                    RESOURCE_KEY));
                    if (resource != null && !tenantsThatChanged.contains(app.getAsPublicTenantIdentifier())) {
                        main.getResourceDistributor()
                                .setResource(app,
                                        RESOURCE_KEY,
                                        resource);
                    } else {
                        main.getResourceDistributor()
                                .setResource(
                                        app,
                                        RESOURCE_KEY,
                                        new FeatureFlag(main, app));
                    }
                }
                return null;
            });
        } catch (ResourceDistributor.FuncException e) {
            throw new RuntimeException(e);
        }
    }

    public EE_FEATURES[] getEnabledFeatures() throws StorageQueryException, TenantOrAppNotFoundException {
        if (FeatureFlagTestContent.getInstance(main).getValue(FeatureFlagTestContent.ENABLED_FEATURES) != null) {
            return FeatureFlagTestContent.getInstance(main).getValue(FeatureFlagTestContent.ENABLED_FEATURES);
        }
        if (this.eeFeatureFlag == null) {
            return new EE_FEATURES[]{};
        }
        if (StorageLayer.isInMemDb(main)) {
            return EE_FEATURES.values();
        }
        return this.eeFeatureFlag.getEnabledFeatures();
    }

    public boolean syncFeatureFlagWithLicenseKey()
            throws StorageQueryException, HttpResponseException, IOException, InvalidLicenseKeyException,
            TenantOrAppNotFoundException {
        if (this.eeFeatureFlag == null) {
            return false;
        }
        this.eeFeatureFlag.syncFeatureFlagWithLicenseKey();
        return true;
    }

    public boolean setLicenseKeyAndSyncFeatures(String licenseKey)
            throws StorageQueryException, HttpResponseException, IOException, InvalidLicenseKeyException,
            TenantOrAppNotFoundException {
        if (this.eeFeatureFlag == null) {
            return false;
        }
        this.eeFeatureFlag.setLicenseKeyAndSyncFeatures(licenseKey);
        return true;
    }

    public void removeLicenseKeyAndSyncFeatures()
            throws StorageQueryException, HttpResponseException, IOException, TenantOrAppNotFoundException {
        if (this.eeFeatureFlag == null) {
            return;
        }
        this.eeFeatureFlag.removeLicenseKeyAndSyncFeatures();
    }

    public String getLicenseKey() throws NoLicenseKeyFoundException, StorageQueryException,
            TenantOrAppNotFoundException {
        if (this.eeFeatureFlag == null) {
            throw new NoLicenseKeyFoundException();
        }
        return this.eeFeatureFlag.getLicenseKeyFromDb();
    }

    @TestOnly
    public EEFeatureFlagInterface getEeFeatureFlagInstance() {
        return eeFeatureFlag;
    }

    @TestOnly
    public static void clearURLClassLoader() {
        /*
         * This is needed for certain tests that test the loading of the jar
         */
        if (FeatureFlag.ucl != null) {
            try {
                FeatureFlag.ucl.close();
            } catch (IOException ignored) {
            }
            FeatureFlag.ucl = null;
        }
    }
}