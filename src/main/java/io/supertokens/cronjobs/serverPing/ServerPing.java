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

// Do not modify after and including this line
package io.supertokens.cronjobs.serverPing;

import io.supertokens.Main;
import io.supertokens.ResourceDistributor.SingletonResource;
import io.supertokens.backendAPI.Ping;
import io.supertokens.cronjobs.CronTask;

public class ServerPing extends CronTask {

    private static final String RESOURCE_KEY = "io.supertokens.cronjobs.serverPing.ServerPing";

    private ServerPing(Main main) {
        super("ServerPing", main);
    }

    public static ServerPing getInstance(Main main) {
        SingletonResource instance = main.getResourceDistributor().getResource(RESOURCE_KEY);
        if (instance == null) {
            instance = main.getResourceDistributor().setResource(RESOURCE_KEY, new ServerPing(main));
        }
        return (ServerPing) instance;
    }

    @Override
    protected synchronized void doTask() throws Exception {
        Ping.getInstance(main).doPing();
    }

    @Override
    public int getIntervalTimeSeconds() {
        return 24 * 3600;
    }

    @Override
    public int getInitialWaitTimeSeconds() {
        return 0;   // send a ping immediately when the process starts
    }
}
// Do not modify before and including this line