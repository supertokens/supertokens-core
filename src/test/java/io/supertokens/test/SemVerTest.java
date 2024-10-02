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

import io.supertokens.utils.SemVer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

public class SemVerTest {
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
    public void betweenInclusive() {
        assert (!SemVer.v2_7.betweenInclusive(SemVer.v2_8, SemVer.v2_10));
        assert (SemVer.v2_8.betweenInclusive(SemVer.v2_8, SemVer.v2_10));
        assert (SemVer.v2_9.betweenInclusive(SemVer.v2_8, SemVer.v2_10));
        assert (SemVer.v2_10.betweenInclusive(SemVer.v2_8, SemVer.v2_10));
        assert (!SemVer.v2_11.betweenInclusive(SemVer.v2_8, SemVer.v2_10));
    }

    @Test
    public void greaterThanOrEqualTo() {
        assert (!SemVer.v2_7.greaterThanOrEqualTo(SemVer.v2_8));
        assert (SemVer.v2_8.greaterThanOrEqualTo(SemVer.v2_8));
        assert (SemVer.v2_9.greaterThanOrEqualTo(SemVer.v2_8));
    }

    @Test
    public void lesserThan() {
        assert (SemVer.v2_7.lesserThan(SemVer.v2_8));
        assert (!SemVer.v2_8.lesserThan(SemVer.v2_8));
        assert (!SemVer.v2_9.lesserThan(SemVer.v2_8));
    }
}
