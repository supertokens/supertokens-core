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

package io.supertokens.webserver;

import ch.qos.logback.core.CoreConstants;
import io.supertokens.Main;
import io.supertokens.output.Logging;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.utils.Utils;

import java.util.logging.Handler;
import java.util.logging.LogRecord;

public class WebServerLogging extends Handler {

    private final Main main;

    WebServerLogging(Main main) {
        this.main = main;
    }

    @Override
    public void publish(LogRecord record) {
        StringBuilder sb = new StringBuilder();

        sb.append(CoreConstants.LINE_SEPARATOR);
        sb.append(record.getInstant());
        sb.append(" | ");
        sb.append(record.getSourceClassName());
        sb.append(" | ");
        sb.append(record.getSourceMethodName());
        sb.append(" | ");
        sb.append(record.getMessage());
        sb.append(" | ");
        sb.append(record.getLoggerName());
        sb.append(" | ");
        sb.append(record.getLevel().toString());

        if (record.getThrown() != null) {
            sb.append(" | ");
            sb.append(Utils.throwableStacktraceToString(record.getThrown()));
            Logging.error(main, TenantIdentifier.BASE_TENANT, sb.toString(), false); // TODO logging
        } else {
            Logging.debug(main, TenantIdentifier.BASE_TENANT, sb.toString()); // TODO logging
        }
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
    }
}
