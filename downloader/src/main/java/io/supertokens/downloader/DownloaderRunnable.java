/*
 *    Copyright (c) 2023, SuperTokens, Inc. All rights reserved.
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
