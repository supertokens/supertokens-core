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

// Do not modify after and including this line
package io.supertokens.licenseKey;

import io.supertokens.Main;
import io.supertokens.licenseKey.LicenseKey.MODE;

public class LicenseKeyContent {

    private Info info;
    private String signature;

    private LicenseKeyContent() {

    }

    public String getUserId() {
        return info.userId;
    }

    public long getTimeCreated(Main main) {
        if (Main.isTesting && this.getMode() == MODE.DEV) {
            Long timeCreated = LicenseKeyTestContent.getInstance(main).getValue(LicenseKeyTestContent.TIME_CREATED_KEY);
            if (timeCreated != null) {
                return timeCreated;
            }
        }
        return info.timeCreated;
    }

    public long getExpiryTime() {
        return info.expiryTime;
    }

    public ON_EXPIRY getOnExpiry() {
        return info.onExpiry;
    }

    public String getAppId(Main main) {
        if (Main.isTesting && this.getMode() == MODE.DEV) {
            String testAppId = LicenseKeyTestContent.getInstance(main).getValue(LicenseKeyTestContent.APP_ID_KEY);
            if (testAppId != null) {
                return testAppId;
            }
        }
        return info.appId;
    }

    public String getLicenseKeyId() {
        return info.licenseKeyId;
    }

    public LicenseKey.MODE getMode() {
        return info.mode;
    }

    public LicenseKey.PLAN_TYPE getPlanType() {
        return info.planType;
    }

    String getSignature() {
        return signature;
    }

    Info getInfo() {
        return info;
    }

    public String getLicenseKeyVersion() {
        return info.licenseKeyId.split("_")[1];
    }


    public enum ON_EXPIRY {
        NA
    }

    private static class Info {

        private String userId;

        // NOTE: this order is very important since it defines how this will be
        // converted to JSON for verification purposes.
        private long timeCreated;
        private long expiryTime;
        private LicenseKey.PLAN_TYPE planType;
        private ON_EXPIRY onExpiry;
        private String appId;
        private String licenseKeyId;
        private LicenseKey.MODE mode;

        private Info() {

        }

    }

}
// Do not modify before and including this line