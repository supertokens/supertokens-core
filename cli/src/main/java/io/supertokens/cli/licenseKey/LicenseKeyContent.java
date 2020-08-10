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
package io.supertokens.cli.licenseKey;

import io.supertokens.cli.Main;

public class LicenseKeyContent {

    private Info info;
    private String signature;
    private LicenseKey.PLAN_TYPE originalPlanType;

    private LicenseKeyContent() {

    }

    public String getUserId() {
        return info.userId;
    }

    public long getTimeCreated(Main main) {
        return info.timeCreated;
    }

    public long getExpiryTime() {
        return info.expiryTime;
    }

    public ON_EXPIRY getOnExpiry() {
        return info.onExpiry;
    }

    public String getAppId() {
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

    void downgradePlanTypeToFree() {
        info.planType = LicenseKey.PLAN_TYPE.FREE;
    }

    public String getLicenseKeyVersion() {
        return info.licenseKeyId.split("_")[1];
    }

    void setOriginalPlayType() {
        originalPlanType = info.planType;
    }

    public LicenseKey.PLAN_TYPE getOriginalPlanType() {
        return originalPlanType;
    }

    public enum ON_EXPIRY {
        DOWNGRADE, STOP, NA
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