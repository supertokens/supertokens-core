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

package io.supertokens.cli.commandHandler.update;

import io.supertokens.cli.exception.QuitProgramException;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

public class DownloaderRunnable implements Runnable {

    private final String urlStr;
    private final String file;
    private final String apiVersion;

    String output = null;
    IOException ioError = null;
    RuntimeException runtimeException = null;
    boolean hasFinished = false;

    DownloaderRunnable(String urlStr, String file, String apiVersion) {
        this.urlStr = urlStr;
        this.file = file;
        this.apiVersion = apiVersion;
    }

    @Override
    public void run() {
        try {
            output = doDownload();
        } catch (IOException e) {
            ioError = e;
        } catch (RuntimeException e) {
            runtimeException = e;
        }
        hasFinished = true;
    }

    private String doDownload() throws IOException {
        URL url = new URL(urlStr);
        File fileObj = new File(file);

        boolean success = fileObj.createNewFile();

        if (!success) {
            UpdateUtils.throwRootPermissionNeededError();
        }

        //get the response code
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestProperty("api-version", apiVersion);
        connection.setRequestMethod("GET");
        int responseCode = connection.getResponseCode();

        // download the zip file if the response code is 200
        if (responseCode == 200) {
            try (ReadableByteChannel rbc = Channels.newChannel(connection.getInputStream());
                 FileOutputStream fos = new FileOutputStream(fileObj.getAbsolutePath())) {
                fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
            }
            return null;
        } else if (responseCode == 201) {
            StringBuilder response = new StringBuilder();
            try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
            }
            return response.toString();
        } else {
            throw new QuitProgramException(
                    "Something went wrong, please try again later, or contact us at team@supertokens.io if the " +
                            "problem persists",
                    new Exception("Update API returned status code: " + responseCode));
        }
    }

}
