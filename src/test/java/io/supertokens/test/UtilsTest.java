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

package io.supertokens.test;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.core.util.VersionUtil;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

public class UtilsTest {
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

    @Test
    public void encodeDecodeBase64WithUTF() {
        assert (io.supertokens.utils.Utils.convertFromBase64(io.supertokens.utils.Utils.convertToBase64("łukasz 馬 / 马"))
                .equals("łukasz 馬 / 马"));
    }

    @Test
    public void isOlderVersionThan() {
        assert (io.supertokens.utils.Utils.isOlderVersionThan(VersionUtil.parseVersion("2.12", null, null),
                io.supertokens.utils.Utils.VER_2_13));
        assert (!io.supertokens.utils.Utils.isOlderVersionThan(VersionUtil.parseVersion("3.0", null, null),
                io.supertokens.utils.Utils.VER_2_13));
        assert (io.supertokens.utils.Utils.isOlderVersionThan(VersionUtil.parseVersion("1.1000", null, null),
                io.supertokens.utils.Utils.VER_2_13));
        assert (!io.supertokens.utils.Utils.isOlderVersionThan(VersionUtil.parseVersion("2.13", null, null),
                io.supertokens.utils.Utils.VER_2_13));
        assert (!io.supertokens.utils.Utils.isOlderVersionThan(VersionUtil.parseVersion("2.14", null, null),
                io.supertokens.utils.Utils.VER_2_13));

        assert (io.supertokens.utils.Utils.isOlderVersionThan(VersionUtil.parseVersion("2.1", null, null),
                VersionUtil.parseVersion("2.10", null, null)));
        assert (!io.supertokens.utils.Utils.isOlderVersionThan(VersionUtil.parseVersion("2.100", null, null),
                VersionUtil.parseVersion("2.10", null, null)));
    }
}
