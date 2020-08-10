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

package io.supertokens.downloader.logging;

import io.supertokens.downloader.Main;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

public class Logging {

    public static void info(String msg) {
        if (!Main.makeConsolePrintSilent) {
            System.out.println(msg);
        }
    }

    public static void error(String err) {
        System.err.println(err);
    }

    public static void error(Throwable err) {
        System.err.println(throwableStacktraceToString(err));
    }

    private static String throwableStacktraceToString(Throwable e) {
        if (e == null) {
            return "";
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PrintStream ps = new PrintStream(baos)) {
            e.printStackTrace(ps);
        }
        return baos.toString();
    }

}
