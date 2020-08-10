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
import ch.qos.logback.core.Layout;
import ch.qos.logback.core.encoder.EncoderBase;
import io.supertokens.Main;

import java.nio.charset.StandardCharsets;

class LayoutWrappingEncoder extends EncoderBase<ILoggingEvent> {

    private Layout<ILoggingEvent> layout;

    LayoutWrappingEncoder(Main main) {
        layout = new CustomLayout(main);
    }

    @Override
    public byte[] encode(ILoggingEvent event) {
        String txt = layout.doLayout(event);
        return convertToBytes(txt);
    }

    private byte[] convertToBytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public byte[] headerBytes() {
        return null;
    }

    @Override
    public byte[] footerBytes() {
        return null;
    }
}
