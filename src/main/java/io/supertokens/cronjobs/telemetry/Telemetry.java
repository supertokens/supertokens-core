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

import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.ResourceDistributor;
import io.supertokens.config.Config;
import io.supertokens.cronjobs.CronTask;
import io.supertokens.cronjobs.CronTaskTest;
import io.supertokens.httpRequest.HttpRequest;
import io.supertokens.httpRequest.HttpRequestMocking;
import io.supertokens.pluginInterface.KeyValueInfo;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.utils.Utils;
import io.supertokens.version.Version;

public class Telemetry extends CronTask {

    private static final String TELEMETRY_ID_DB_KEY = "TELEMETRY_ID";

    public static final String REQUEST_ID = "telemetry";

    public static final String RESOURCE_KEY = "io.supertokens.cronjobs.telemetry.Telemetry";

    private Telemetry(Main main) {
        super("Telemetry", main);
    }

    public static Telemetry getInstance(Main main) {
        ResourceDistributor.SingletonResource instance = main.getResourceDistributor().getResource(RESOURCE_KEY);
        if (instance == null) {
            instance = main.getResourceDistributor().setResource(RESOURCE_KEY, new Telemetry(main));
        }
        return (Telemetry) instance;
    }

    @Override
    protected void doTask() throws Exception {
        String plugin = Version.getVersion(main).getPluginName();

        if (plugin.equals("sqlite") || Config.getConfig(main).isTelemetryDisabled()) {
            // we do not send any info in this case since it's not under development / production env or the user has
            // disabled Telemetry
            return;
        }

        ProcessState.getInstance(main).addState(ProcessState.PROCESS_STATE.SENDING_TELEMETRY, null);

        Storage storage = StorageLayer.getStorageLayer(main);

        KeyValueInfo telemetryId = storage.getKeyValue(TELEMETRY_ID_DB_KEY);

        if (telemetryId == null) {
            telemetryId = new KeyValueInfo(Utils.getUUID());
            storage.setKeyValue(TELEMETRY_ID_DB_KEY, telemetryId);
        }

        String coreVersion = Version.getVersion(main).getCoreVersion();

        // following the API spec mentioned here:
        // https://github.com/supertokens/supertokens-core/issues/116#issuecomment-725465665

        JsonObject json = new JsonObject();
        json.addProperty("telemetryId", telemetryId.value);
        json.addProperty("superTokensVersion", coreVersion);

        String url = "https://api.supertokens.io/0/st/telemetry";

        // we call the API only if we are not testing the core, of if the request can be mocked (in case a test wants
        // to use this)
        if (!Main.isTesting || HttpRequestMocking.getInstance(main).getMockURL(REQUEST_ID, url) != null) {
            HttpRequest.sendJsonPOSTRequest(main, REQUEST_ID, url,
                    json, 10000, 10000, 0);
            ProcessState.getInstance(main).addState(ProcessState.PROCESS_STATE.SENT_TELEMETRY, null);
        }
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
