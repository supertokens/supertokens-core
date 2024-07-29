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

package io.supertokens.cronjobs.telemetry;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.authRecipe.AuthRecipe;
import io.supertokens.config.Config;
import io.supertokens.cronjobs.CronTask;
import io.supertokens.cronjobs.CronTaskTest;
import io.supertokens.dashboard.Dashboard;
import io.supertokens.httpRequest.HttpRequest;
import io.supertokens.httpRequest.HttpRequestMocking;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.pluginInterface.ActiveUsersStorage;
import io.supertokens.pluginInterface.KeyValueInfo;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.dashboard.DashboardUser;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.utils.Utils;
import io.supertokens.version.Version;
import org.jetbrains.annotations.TestOnly;

import java.util.List;

public class Telemetry extends CronTask {

    public static final String TELEMETRY_ID_DB_KEY = "TELEMETRY_ID";

    public static final String REQUEST_ID = "telemetry";

    public static final String RESOURCE_KEY = "io.supertokens.cronjobs.telemetry.Telemetry";

    private Telemetry(Main main, List<List<TenantIdentifier>> tenants) {
        super("Telemetry", main, tenants, true);
    }

    @TestOnly
    public static Telemetry getInstance(Main main) {
        try {
            return (Telemetry) main.getResourceDistributor()
                    .getResource(new TenantIdentifier(null, null, null), RESOURCE_KEY);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    public static Telemetry init(Main main, List<List<TenantIdentifier>> tenantsInfo) {
        return (Telemetry) main.getResourceDistributor()
                .setResource(new TenantIdentifier(null, null, null), RESOURCE_KEY,
                        new Telemetry(main, tenantsInfo));
    }

    @Override
    protected void doTaskPerApp(AppIdentifier app) throws Exception {
        if (StorageLayer.isInMemDb(main) ||
                Config.getConfig(app.getAsPublicTenantIdentifier(), main).isTelemetryDisabled()) {
            // we do not send any info in this case since it's not under development / production env or the user
            // has
            // disabled Telemetry
            return;
        }

        ProcessState.getInstance(main).addState(ProcessState.PROCESS_STATE.SENDING_TELEMETRY, null);

        KeyValueInfo telemetryId = Telemetry.getTelemetryId(main, app);

        String coreVersion = Version.getVersion(main).getCoreVersion();

        // following the API spec mentioned here:
        // https://github.com/supertokens/supertokens-core/issues/116#issuecomment-725465665

        JsonObject json = new JsonObject();
        json.addProperty("telemetryId", telemetryId.value);
        json.addProperty("superTokensVersion", coreVersion);

        json.addProperty("appId", app.getAppId());
        json.addProperty("connectionUriDomain", app.getConnectionUriDomain());

        { // website and API domains
            String websiteDomain = Multitenancy.getWebsiteDomain(
                    StorageLayer.getStorage(app.getAsPublicTenantIdentifier(), main), app);
            String apiDomain = Multitenancy.getAPIDomain(
                    StorageLayer.getStorage(app.getAsPublicTenantIdentifier(), main), app);

            if (websiteDomain != null) {
                json.addProperty("websiteDomain", websiteDomain);
            }
            if (apiDomain != null) {
                json.addProperty("apiDomain", apiDomain);
            }
        }

        if (StorageLayer.getBaseStorage(main).getType() == STORAGE_TYPE.SQL) {
            { // Users count across all tenants
                Storage[] storages = StorageLayer.getStoragesForApp(main, app);

                json.addProperty("usersCount",
                        AuthRecipe.getUsersCountAcrossAllTenants(app, storages, null));
            }

            { // Dashboard user emails
                // Dashboard APIs are app specific and are always stored on the public tenant
                DashboardUser[] dashboardUsers = Dashboard.getAllDashboardUsers(
                        app, StorageLayer.getStorage(app.getAsPublicTenantIdentifier(), main), main);
                JsonArray dashboardUserEmails = new JsonArray();
                for (DashboardUser user : dashboardUsers) {
                    dashboardUserEmails.add(new JsonPrimitive(user.email));
                }

                json.add("dashboardUserEmails", dashboardUserEmails);
            }

            { // MAUs
                // Active users are always tracked on the public tenant, so we use the public tenant's storage
                ActiveUsersStorage activeUsersStorage = (ActiveUsersStorage) StorageLayer.getStorage(
                        app.getAsPublicTenantIdentifier(), main);

                JsonArray mauArr = new JsonArray();

                long now = System.currentTimeMillis();

                for (int i = 1; i <= 31; i++) {
                    long timestamp = now - (i * 24 * 60 * 60 * 1000L);
                    int mau = activeUsersStorage.countUsersActiveSince(app, timestamp);
                    mauArr.add(new JsonPrimitive(mau));
                }

                json.add("maus", mauArr);
            }

        } else {
            json.addProperty("usersCount", -1);
            json.add("dashboardUserEmails", new JsonArray());
            json.add("maus", new JsonArray());
        }

        String url = "https://api.supertokens.io/0/st/telemetry";

        // we call the API only if we are not testing the core, of if the request can be mocked (in case a test
        // wants
        // to use this)
        if (!Main.isTesting || HttpRequestMocking.getInstance(main).getMockURL(REQUEST_ID, url) != null) {
            HttpRequest.sendJsonPOSTRequest(main, REQUEST_ID, url, json, 10000, 10000, 6);
            ProcessState.getInstance(main).addState(ProcessState.PROCESS_STATE.SENT_TELEMETRY, null);
        }
    }

    public static KeyValueInfo getTelemetryId(Main main, AppIdentifier appIdentifier)
            throws StorageQueryException, TenantOrAppNotFoundException {
        if (StorageLayer.isInMemDb(main) ||
                Config.getConfig(appIdentifier.getAsPublicTenantIdentifier(), main).isTelemetryDisabled()) {
            return null;
        }
        Storage storage = StorageLayer.getStorage(appIdentifier.getAsPublicTenantIdentifier(), main);

        KeyValueInfo telemetryId = storage.getKeyValue(appIdentifier.getAsPublicTenantIdentifier(),
                TELEMETRY_ID_DB_KEY);

        if (telemetryId == null) {
            telemetryId = new KeyValueInfo(Utils.getUUID());
            storage.setKeyValue(appIdentifier.getAsPublicTenantIdentifier(), TELEMETRY_ID_DB_KEY, telemetryId);
        }
        return telemetryId;
    }

    @Override
    public int getIntervalTimeSeconds() {
        if (Main.isTesting) {
            Integer interval = CronTaskTest.getInstance(main).getIntervalInSeconds(RESOURCE_KEY);
            if (interval != null) {
                return interval;
            }
        }
        return (24 * 3600); // once a day.
    }

    @Override
    public int getInitialWaitTimeSeconds() {
        // We send a ping start one immediately (equal to sending server start event)
        return 0;
    }
}
