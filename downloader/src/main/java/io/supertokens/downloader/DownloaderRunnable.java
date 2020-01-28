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

package io.supertokens.downloader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

public class DownloaderRunnable implements Runnable {

    private final String urlStr;
    private final String parentDir;

    IOException ioError = null;
    RuntimeException runtimeException = null;
    boolean hasFinished = false;

    DownloaderRunnable(String urlStr, String parentDir) {
        this.urlStr = urlStr;
        this.parentDir = parentDir;
    }

    @Override
    public void run() {
        try {
            doDownload();
        } catch (IOException e) {
            ioError = e;
        } catch (RuntimeException e) {
            runtimeException = e;
        }
        hasFinished = true;
    }

    private void doDownload() throws IOException {
        URL url = new URL(urlStr);
        String[] urlSplit = urlStr.split("/");
        String fileName = urlSplit[urlSplit.length - 1];
        String file = parentDir + (OperatingSystem.getOS() == OperatingSystem.OS.WINDOWS ? "\\" : "/") + fileName;
        File fileObj = new File(file);
        if (fileObj.exists()) { // in case some past download partially succeeded
            boolean ignored = fileObj.delete();
        }
        try (ReadableByteChannel rbc = Channels.newChannel(url.openStream());
             FileOutputStream fos = new FileOutputStream(file)) {
            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
        }
    }

}
