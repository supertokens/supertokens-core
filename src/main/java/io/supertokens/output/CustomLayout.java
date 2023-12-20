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

package io.supertokens.output;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.CoreConstants;
import ch.qos.logback.core.LayoutBase;
import io.supertokens.Main;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

class CustomLayout extends LayoutBase<ILoggingEvent> {

    private String processID;
    private String coreVersion;

    CustomLayout(String processID, String coreVersion) {
        super();
        this.processID = processID;
        this.coreVersion = coreVersion;
    }

    @Override
    public String doLayout(ILoggingEvent event) {
        StringBuilder sbuf = new StringBuilder();

        DateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy HH:mm:ss:SSS Z");
        sbuf.append(dateFormat.format(new Date(event.getTimeStamp())));
        sbuf.append(" | ");

        sbuf.append(event.getLevel());
        sbuf.append(" | ");

        sbuf.append("pid: ");
        sbuf.append(this.processID);
        sbuf.append(" | ");

        sbuf.append("v" + coreVersion);
        sbuf.append(" | ");

        sbuf.append("[");
        sbuf.append(event.getThreadName());
        sbuf.append("] thread");
        sbuf.append(" | ");

        sbuf.append(event.getCallerData()[1]);
        sbuf.append(" | ");

        sbuf.append(event.getFormattedMessage());
        sbuf.append(CoreConstants.LINE_SEPARATOR);
        sbuf.append(CoreConstants.LINE_SEPARATOR);

        return sbuf.toString();
    }
}
