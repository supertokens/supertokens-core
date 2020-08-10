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

// Do not modify after and including this line
package io.supertokens.licenseKey;

import com.google.gson.Gson;
import io.supertokens.Main;
import io.supertokens.ResourceDistributor;
import io.supertokens.backendAPI.LicenseKeyVerify;
import io.supertokens.exceptions.QuitProgramException;
import io.supertokens.licenseKey.LicenseKeyContent.ON_EXPIRY;
import io.supertokens.output.Logging;
import io.supertokens.utils.Constants;
import io.supertokens.utils.Utils;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.Reader;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class LicenseKey extends ResourceDistributor.SingletonResource {
    private static final String RESOURCE_KEY = "io.supertokens.licenseKey.LicenseKey";
    private final LicenseKeyContent content;
    private final Main main;

    private LicenseKey(Main main, Reader licenseKeyReader) {
        this.main = main;
        LicenseKeyContent content = loadAndCheckContent(licenseKeyReader);
        verifyLicenseKeySignature(content);
        this.content = content;
    }

    private static LicenseKey getInstance(Main main) {
        return (LicenseKey) main.getResourceDistributor().getResource(RESOURCE_KEY);
    }

    public static void load(Main main, String licenseKeyFilePath) {
        if (getInstance(main) != null) {
            return;
        }
        Logging.info(main, "Loading licenseKey file.");
        try {
            Reader licenseKeyReader = new FileReader(licenseKeyFilePath);
            LicenseKey instance = new LicenseKey(main, licenseKeyReader);
            main.getResourceDistributor().setResource(RESOURCE_KEY, instance);
            instance.checkExpiryAndServer();
        } catch (FileNotFoundException e) {
            throw new QuitProgramException(
                    "LicenseKey file is missing. Please visit https://supertokens.io/dashboard to get the licenseKey " +
                            "for this app");
        }
    }

    public static LicenseKeyContent get(Main main) {
        if (getInstance(main) == null) {
            throw new QuitProgramException("Please call load() before get()");
        }
        return getInstance(main).content;
    }

    // return a string if new licenseKey was downloaded. Else null
    private void checkExpiryAndServer() {
        if (!verifyLicenseKeyFromServer(content)) {
            throw new QuitProgramException(
                    "LicenseKey has been revoked. Please get a new key from your SuperTokens dashboard.");
        }
    }

    private boolean verifyLicenseKeyFromServer(LicenseKeyContent content) {
        try {
            return LicenseKeyVerify.verify(main, content.getLicenseKeyId(), content.getAppId(main));
        } catch (Exception e) {
            /*
             * we catch all responses here as to enable users to use this service regardless
             * of what fails
             */
            Logging.error(main, "Error while verifying licenseKey from server", true, e);
        }
        return true;
    }

    private void verifyLicenseKeySignature(LicenseKeyContent content) {
        try {

            String toVerify = new Gson().toJson(content.getInfo());
            String hashFromInput = Utils.hashSHA256(toVerify);

            Signature sig = Signature.getInstance("SHA1WithRSA");
            PublicKey puK = KeyFactory.getInstance("RSA").generatePublic(
                    new X509EncodedKeySpec(Base64.getDecoder().decode(Constants.LICENSE_KEY_SIGNATURE_PUBLIC_KEY)));
            sig.initVerify(puK);
            sig.update(hashFromInput.getBytes());

            if (!sig.verify(Base64.getDecoder().decode(content.getSignature()))) {
                throw new QuitProgramException(
                        "Failed to verify licenseKey signature. Please visit https://supertokens.io/dashboard to " +
                                "redownload your license key");
            }

        } catch (InvalidKeySpecException | NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            throw new QuitProgramException(e);
        }
    }

    // if licenseKeyContent != null, then it will be used
    private LicenseKeyContent loadAndCheckContent(Reader licenseKeyReader) {
        Gson gson = new Gson();
        LicenseKeyContent content = gson.fromJson(
                licenseKeyReader,
                LicenseKeyContent.class);

        if (content.getUserId() == null) {
            throw new QuitProgramException("licenseKey file error: userId missing");
        }

        if (content.getTimeCreated(main) == 0 || content.getTimeCreated(main) >= System.currentTimeMillis()) {
            throw new QuitProgramException("licenseKey file error: timeCreated missing or invalid");
        }

        if (content.getExpiryTime() != -1) {
            throw new QuitProgramException(
                    "licenseKey file error: invalid expiryTime. Please use a Community licenseKey or redownload it " +
                            "from" +
                            " your SuperTokens dashboard.");
        }

        if (content.getOnExpiry() == null || content.getOnExpiry() != ON_EXPIRY.NA) {
            throw new QuitProgramException(
                    "licenseKey file error: invalid onExpiry value. Please use a Community licenseKey or redownload " +
                            "it " +
                            "from your SuperTokens dashboard.");
        }

        if (content.getAppId(main) == null) {
            throw new QuitProgramException("licenseKey file error: appId missing");
        }

        if (content.getLicenseKeyId() == null) {
            throw new QuitProgramException("licenseKey file error: licenseKeyId missing");
        }

        if (content.getMode() == null) {
            throw new QuitProgramException("licenseKey file error: mode missing");
        }

        if (content.getPlanType() != PLAN_TYPE.FREE) {
            throw new QuitProgramException(
                    "licenseKey file error: wrong planType. Please use a Community licenseKey or redownload it from " +
                            "your SuperTokens dashboard.");
        }

        if (content.getSignature() == null) {
            throw new QuitProgramException("licenseKey file error: signature missing");
        }

        if (!content.getLicenseKeyVersion().matches("v[0-9]+")) {
            throw new QuitProgramException("licenseKey file error: version has invalid");
        }

        return content;
    }

    public enum MODE {
        PRODUCTION, DEV
    }

    public enum PLAN_TYPE {
        FREE("FREE");

        private String playType;

        PLAN_TYPE(String playType) {
            this.playType = playType;
        }

        @Override
        public String toString() {
            return playType;
        }
    }
}
// Do not modify before and including this line