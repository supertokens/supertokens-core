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
