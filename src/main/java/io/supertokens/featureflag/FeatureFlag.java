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
import io.supertokens.cronjobs.telemetry.Telemetry;
import io.supertokens.ee.EEFeatureFlag;
import io.supertokens.ee.EE_FEATURES;
import io.supertokens.ee.httpRequest.HttpResponseException;
import io.supertokens.exceptions.QuitProgramException;
import io.supertokens.output.Logging;
import io.supertokens.pluginInterface.KeyValueInfo;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.version.Version;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Iterator;
import java.util.ServiceLoader;

public class FeatureFlag extends ResourceDistributor.SingletonResource {

    private static final String RESOURCE_KEY = "io.supertokens.featureflag.FeatureFlag";
    private final EEFeatureFlag eeFeatureFlag;
    private static URLClassLoader ucl = null;

    private FeatureFlag(Main main, String eeFolderPath) throws MalformedURLException {
        File loc = new File(eeFolderPath);
        EEFeatureFlag eeLayerTemp = null;

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

            ServiceLoader<EEFeatureFlag> sl = ServiceLoader.load(EEFeatureFlag.class, ucl);
            Iterator<EEFeatureFlag> it = sl.iterator();
            if (it.hasNext()) {
                eeLayerTemp = it.next();
            }
        }

        if (eeLayerTemp != null) {
            this.eeFeatureFlag = eeLayerTemp;
            try {
                this.eeFeatureFlag.constructor(StorageLayer.getStorage(main),
                        Version.getVersion(main).getCoreVersion(), new io.supertokens.ee.Logging() {
                            @Override
                            public void info(String msg, boolean toConsoleAsWell) {
                                Logging.info(main, msg, toConsoleAsWell);
                            }

                            @Override
                            public void debug(String msg) {
                                Logging.debug(main, msg);
                            }

                            @Override
                            public void error(String message, boolean toConsoleAsWell, Exception e) {
                                Logging.error(main, message, toConsoleAsWell, e);
                            }
                        }, () -> {
                            KeyValueInfo info = Telemetry.getTelemetryId(main);
                            return info == null ? null : info.value;
                        }, this::getPaidFeatureStats);
            } catch (StorageQueryException e) {
                throw new QuitProgramException(e);
            }
        } else {
            Logging.info(main, "Missing ee folder", true);
            eeFeatureFlag = null;
        }
    }

    public JsonObject getPaidFeatureStats() throws StorageQueryException {
        JsonObject result = new JsonObject();
        // TODO:
        return result;
    }

    public static FeatureFlag getInstance(Main main) {
        return (FeatureFlag) main.getResourceDistributor().getResource(RESOURCE_KEY);
    }

    public static void init(Main main, String eeFolderPath) throws MalformedURLException {
        if (getInstance(main) != null) {
            return;
        }
        main.getResourceDistributor().setResource(RESOURCE_KEY,
                new FeatureFlag(main, eeFolderPath));
    }

    public EE_FEATURES[] getEnabledFeatures() throws StorageQueryException {
        if (this.eeFeatureFlag == null) {
            return new EE_FEATURES[]{};
        }
        return this.eeFeatureFlag.getEnabledFeatures();
    }

    public boolean forceSyncWithServer()
            throws StorageQueryException, HttpResponseException, IOException, EEFeatureFlag.InvalidLicenseKeyException {
        if (this.eeFeatureFlag == null) {
            return false;
        }
        this.eeFeatureFlag.forceSyncFeatureFlagWithLicenseKey();
        return true;
    }

    public boolean setLicenseKeyAndSyncFeatures(String licenseKey)
            throws StorageQueryException, HttpResponseException, IOException, EEFeatureFlag.InvalidLicenseKeyException {
        if (this.eeFeatureFlag == null) {
            return false;
        }
        this.eeFeatureFlag.setLicenseKeyAndSyncFeatures(licenseKey);
        return true;
    }

    public void removeLicenseKeyAndSyncFeatures() throws StorageQueryException, HttpResponseException, IOException {
        if (this.eeFeatureFlag == null) {
            return;
        }
        this.eeFeatureFlag.removeLicenseKeyAndSyncFeatures();
    }

    public String getLicenseKey() throws EEFeatureFlag.NoLicenseKeyFoundException, StorageQueryException {
        if (this.eeFeatureFlag == null) {
            throw new EEFeatureFlag.NoLicenseKeyFoundException();
        }
        return this.eeFeatureFlag.getLicenseKeyFromDb();
    }
}
