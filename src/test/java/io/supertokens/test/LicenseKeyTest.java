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

package io.supertokens.test;

import io.supertokens.ProcessState.EventAndException;
import io.supertokens.ProcessState.PROCESS_STATE;
import io.supertokens.backendAPI.LicenseKeyVerify;
import io.supertokens.httpRequest.HttpRequestMocking;
import io.supertokens.httpRequest.HttpRequestMocking.URLGetter;
import io.supertokens.licenseKey.LicenseKey;
import io.supertokens.licenseKey.LicenseKey.MODE;
import io.supertokens.licenseKey.LicenseKey.PLAN_TYPE;
import io.supertokens.licenseKey.LicenseKeyContent;
import io.supertokens.licenseKey.LicenseKeyContent.ON_EXPIRY;
import io.supertokens.test.TestingProcessManager.TestingProcess;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.mockito.Mockito;

import javax.net.ssl.HttpsURLConnection;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class LicenseKeyTest extends Mockito {
    // TODO: load commercial & commercial_trial and make sure it doesnt start

    @Rule
    public TestRule watchman = Utils.getOnFailure();

    @AfterClass
    public static void afterTesting() {
        Utils.afterTesting();
    }

    @Before
    public void beforeEach() {
        Utils.reset();
    }

    // server returns that license key is revoked
    @Test
    public void licenseRevokedFromServer() throws Exception {
        final HttpsURLConnection mockCon = mock(HttpsURLConnection.class);
        InputStream inputStrm = new ByteArrayInputStream("{\"verified\": false}".getBytes(StandardCharsets.UTF_8));
        when(mockCon.getInputStream()).thenReturn(inputStrm);
        when(mockCon.getResponseCode()).thenReturn(200);
        when(mockCon.getOutputStream()).thenReturn(new OutputStream() {
            @Override
            public void write(int b) {
            }
        });

        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.start(args, false);
        HttpRequestMocking.getInstance(process.getProcess()).setMockURL(LicenseKeyVerify.REQUEST_ID, new URLGetter() {

            @Override
            public URL getUrl(String url) throws MalformedURLException {
                URLStreamHandler stubURLStreamHandler = new URLStreamHandler() {
                    @Override
                    protected URLConnection openConnection(URL u) {
                        return mockCon;
                    }
                };
                return new URL(null, url, stubURLStreamHandler);
            }
        });

        process.startProcess();
        EventAndException e = process.checkOrWaitForEvent(PROCESS_STATE.INIT_FAILURE);
        assertTrue(e != null && e.exception.getMessage()
                .equals("LicenseKey has been revoked. Please get a new key from your SuperTokens dashboard."));
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
        process.kill();
    }

    // checking everything loads properly from licenseKey
    @Test
    public void loadLicense() throws Exception {
        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.start(args);
        EventAndException e = process.checkOrWaitForEvent(PROCESS_STATE.STARTED);
        assertNotNull(e);
        LicenseKeyContent content = LicenseKey.get(process.getProcess());
        if (content.getUserId() == null || content.getTimeCreated(process.getProcess()) >= System.currentTimeMillis()
                || content.getTimeCreated(process.getProcess()) == 0
                || content.getExpiryTime() != -1 || content.getOnExpiry() != ON_EXPIRY.NA
                || content.getAppId(process.getProcess()) == null || content.getLicenseKeyId() == null
                || content.getMode() != MODE.DEV || content.getPlanType() != PLAN_TYPE.FREE
                || content.getLicenseKeyVersion() == null) {
            throw new Exception("Incorrect parsing of licenseKey file");
        }
        process.kill();
    }

    // license key content changed so signature doesn't match.
    @Test
    public void contentChanged() throws Exception {
        // replace content in licenseKey file
        Path path = Paths.get("../licenseKey");
        String content = Files.readString(path);
        content = content.replaceAll("DEV", "PRODUCTION");
        Files.writeString(path, content);

        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.start(args);
        EventAndException e = process.checkOrWaitForEvent(PROCESS_STATE.INIT_FAILURE);
        assertTrue(e != null && e.exception.getMessage()
                .equals("Failed to verify licenseKey signature. Please visit https://supertokens.io/dashboard to " +
                        "redownload your license key"));
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
        process.kill();
    }

    // no signature loaded
    @Test
    public void noSignature() throws Exception {
        // replace content in licenseKey file
        Path path = Paths.get("../licenseKey");
        String content = Files.readString(path);
        content = content.replaceAll("signature", "signaturee");
        Files.writeString(path, content);

        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.start(args);
        EventAndException e = process.checkOrWaitForEvent(PROCESS_STATE.INIT_FAILURE);
        assertTrue(e != null && e.exception.getMessage().equals("licenseKey file error: signature missing"));
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
        process.kill();
    }

    // valid commercial signature error
    @Test
    public void commercialLicenseError() throws Exception {
        ProcessBuilder pb = new ProcessBuilder("./utils/downloadDevLicenseKey", "COMMERCIAL", "DOWNGRADE");
        pb.directory(new File("../"));
        pb.start().waitFor();

        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.start(args);
        EventAndException e = process.checkOrWaitForEvent(PROCESS_STATE.INIT_FAILURE);
        assertTrue(e != null && e.exception.getMessage().equals(
                "licenseKey file error: invalid expiryTime. Please use a Community licenseKey or redownload it from " +
                        "your SuperTokens dashboard."));
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
        process.kill();
    }

    @Test
    public void commercialTrialLicenseError() throws Exception {
        ProcessBuilder pb = new ProcessBuilder("./utils/downloadDevLicenseKey", "COMMERCIAL_TRIAL", "DOWNGRADE");
        pb.directory(new File("../"));
        pb.start().waitFor();

        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.start(args);
        EventAndException e = process.checkOrWaitForEvent(PROCESS_STATE.INIT_FAILURE);
        assertTrue(e != null && e.exception.getMessage().equals(
                "licenseKey file error: invalid expiryTime. Please use a Community licenseKey or redownload it from " +
                        "your SuperTokens dashboard."));
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
        process.kill();
    }

    // missing license key
    @Test
    public void missingLicenseError() throws Exception {
        new File("../licenseKey").delete();

        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.start(args);
        EventAndException e = process.checkOrWaitForEvent(PROCESS_STATE.INIT_FAILURE);
        assertTrue(e != null && e.exception.getMessage()
                .equals("LicenseKey file is missing. Please visit https://supertokens.io/dashboard to get the " +
                        "licenseKey for this app"));
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
        process.kill();
    }

    // server throws an error when checking for license key
    @Test
    public void licenseRevokedErrorFromServer() throws Exception {

        final HttpsURLConnection mockCon = mock(HttpsURLConnection.class);
        InputStream errStrm = new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8));
        when(mockCon.getErrorStream()).thenReturn(errStrm);
        when(mockCon.getResponseCode()).thenReturn(500);
        when(mockCon.getOutputStream()).thenReturn(new OutputStream() {
            @Override
            public void write(int b) {
            }
        });

        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.start(args, false);

        HttpRequestMocking.getInstance(process.getProcess()).setMockURL(LicenseKeyVerify.REQUEST_ID, new URLGetter() {

            @Override
            public URL getUrl(String url) throws MalformedURLException {
                URLStreamHandler stubURLStreamHandler = new URLStreamHandler() {
                    @Override
                    protected URLConnection openConnection(URL u) {
                        return mockCon;
                    }
                };
                return new URL(null, url, stubURLStreamHandler);
            }
        });

        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));

    }

}
