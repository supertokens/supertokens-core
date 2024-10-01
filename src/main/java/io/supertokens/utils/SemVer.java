/*
 *    Copyright (c) 2023, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.utils;

public class SemVer implements Comparable<SemVer> {
    public static final SemVer v2_7 = new SemVer("2.7");
    public static final SemVer v2_8 = new SemVer("2.8");
    public static final SemVer v2_9 = new SemVer("2.9");
    public static final SemVer v2_10 = new SemVer("2.10");
    public static final SemVer v2_11 = new SemVer("2.11");
    public static final SemVer v2_12 = new SemVer("2.12");
    public static final SemVer v2_13 = new SemVer("2.13");
    public static final SemVer v2_14 = new SemVer("2.14");
    public static final SemVer v2_15 = new SemVer("2.15");
    public static final SemVer v2_16 = new SemVer("2.16");
    public static final SemVer v2_17 = new SemVer("2.17");
    public static final SemVer v2_18 = new SemVer("2.18");
    public static final SemVer v2_19 = new SemVer("2.19");
    public static final SemVer v2_20 = new SemVer("2.20");
    public static final SemVer v2_21 = new SemVer("2.21");
    public static final SemVer v3_0 = new SemVer("3.0");
    public static final SemVer v3_1 = new SemVer("3.1");
    public static final SemVer v4_0 = new SemVer("4.0");
    public static final SemVer v5_0 = new SemVer("5.0");
    public static final SemVer v5_1 = new SemVer("5.1");

    final private String version;

    public final String get() {
        return this.version;
    }

    public SemVer(String version) {
        if (version == null) {
            throw new IllegalArgumentException("Version can not be null");
        }

        if (!version.matches("[0-9]+(\\.[0-9]+)*")) {
            throw new IllegalArgumentException("Invalid version format");
        }

        this.version = version;
    }

    public boolean betweenInclusive(SemVer min, SemVer max) {
        return min.compareTo(this) <= 0 && this.compareTo(max) <= 0;
    }

    public boolean greaterThanOrEqualTo(SemVer min) {
        return min.compareTo(this) <= 0;
    }

    public boolean greaterThan(SemVer min) {
        return min.compareTo(this) < 0;
    }

    public boolean lesserThan(SemVer max) {
        return this.compareTo(max) < 0;
    }

    @Override
    public int compareTo(SemVer that) {
        if (that == null) {
            return 1;
        }

        String[] thisParts = this.get().split("\\.");
        String[] thatParts = that.get().split("\\.");

        int length = Math.max(thisParts.length, thatParts.length);

        for (int i = 0; i < length; i++) {
            int thisPart = i < thisParts.length ? Integer.parseInt(thisParts[i]) : 0;
            int thatPart = i < thatParts.length ? Integer.parseInt(thatParts[i]) : 0;

            if (thisPart < thatPart) {
                return -1;
            }

            if (thisPart > thatPart) {
                return 1;
            }
        }

        return 0;
    }

    @Override
    public boolean equals(Object that) {
        if (this == that) {
            return true;
        }

        if (that == null) {
            return false;
        }

        if (!(that instanceof SemVer)) {
            return false;
        }

        return this.compareTo((SemVer) that) == 0;
    }

    @Override
    public int hashCode() {
        return version.hashCode();
    }

    @Override
    public String toString() {
        return version;
    }
}
