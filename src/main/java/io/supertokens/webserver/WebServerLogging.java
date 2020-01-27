/*
 *    Copyright (c) 2020, VRAI Labs and/or its affiliates. All rights reserved.
 *
 *    This program is licensed under the SuperTokens Community License (the
 *    "License") as published by VRAI Labs. You may not use this file except in
 *    compliance with the License. You are not permitted to transfer or
 *    redistribute this file without express written permission from VRAI Labs.
 *
 *    A copy of the License is available in the file titled
 *    "SuperTokensLicense.pdf" inside this repository or included with your copy of
 *    the software or its source code. If you have not received a copy of the
 *    License, please write to VRAI Labs at team@supertokens.io.
 *
 *    Please read the License carefully before accessing, downloading, copying,
 *    using, modifying, merging, transferring or sharing this software. By
 *    undertaking any of these activities, you indicate your agreement to the terms
 *    of the License.
 *
 *    This program is distributed with certain software that is licensed under
 *    separate terms, as designated in a particular file or component or in
 *    included license documentation. VRAI Labs hereby grants you an additional
 *    permission to link the program and your derivative works with the separately
 *    licensed software that they have included with this program, however if you
 *    modify this program, you shall be solely liable to ensure compliance of the
 *    modified program with the terms of licensing of the separately licensed
 *    software.
 *
 *    Unless required by applicable law or agreed to in writing, this program is
 *    distributed under the License on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 *    CONDITIONS OF ANY KIND, either express or implied. See the License for the
 *    specific language governing permissions and limitations under the License.
 *
 */

package io.supertokens.webserver;

import ch.qos.logback.core.CoreConstants;
import io.supertokens.Main;
import io.supertokens.output.Logging;
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
            Logging.error(main, sb.toString(), false);
        } else {
            Logging.debug(main, sb.toString());
        }
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
    }
}
