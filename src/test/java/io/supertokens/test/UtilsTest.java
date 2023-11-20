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

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.*;

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
        assert (io.supertokens.utils.Utils.convertFromBase64(
                        io.supertokens.utils.Utils.convertToBase64("łukasz 馬 / 马"))
                .equals("łukasz 馬 / 马"));
    }

    @Test
    public void pubPriKeyShouldHandleSemicolonSeparator() {
        io.supertokens.utils.Utils.PubPriKey parsed = new io.supertokens.utils.Utils.PubPriKey("pub;pri");

        assert (parsed.privateKey.equals("pri"));
        assert (parsed.publicKey.equals("pub"));
    }

    @Test
    public void pubPriKeyShouldHandleBarSeparator() {
        io.supertokens.utils.Utils.PubPriKey parsed = new io.supertokens.utils.Utils.PubPriKey("pub|pri");

        assert (parsed.privateKey.equals("pri"));
        assert (parsed.publicKey.equals("pub"));
    }

    @Test
    public void testNormalizeValidPhoneNumber() {
        {
            String inputPhoneNumber = "+1 650-555-1234";
            String expectedNormalizedPhoneNumber = "+16505551234";
            String actualNormalizedPhoneNumber = io.supertokens.utils.Utils.normalizeIfPhoneNumber(inputPhoneNumber);
            assertEquals(expectedNormalizedPhoneNumber, actualNormalizedPhoneNumber);
        }
        {
            String inputPhoneNumber = "+640223334444";
            String expectedNormalizedPhoneNumber = "+64223334444";
            String actualNormalizedPhoneNumber = io.supertokens.utils.Utils.normalizeIfPhoneNumber(inputPhoneNumber);
            assertEquals(expectedNormalizedPhoneNumber, actualNormalizedPhoneNumber);
        }
    }

    @Test
    public void testNormalizeInvalidPhoneNumber() {
        String inputPhoneNumber = "  johndoe@gmail.com  ";
        String expectedTrimmedPhoneNumber = inputPhoneNumber.trim();
        String actualNormalizedPhoneNumber = io.supertokens.utils.Utils.normalizeIfPhoneNumber(inputPhoneNumber);
        assertEquals(expectedTrimmedPhoneNumber, actualNormalizedPhoneNumber);
    }

    @Test
    public void testNormalizeNullPhoneNumber() {
        String inputPhoneNumber = null;
        assertNull(io.supertokens.utils.Utils.normalizeIfPhoneNumber(inputPhoneNumber));
    }

    @Test
    public void testNormalizeEmptyPhoneNumber() {
        // Test with an empty input
        String inputPhoneNumber = "";
        assertEquals("", io.supertokens.utils.Utils.normalizeIfPhoneNumber(inputPhoneNumber));
    }

    @Test
    public void testNormalizeWhitespacePhoneNumber() {
        // Test with a phone number containing only whitespace
        String inputPhoneNumber = "   ";
        assertEquals("", io.supertokens.utils.Utils.normalizeIfPhoneNumber(inputPhoneNumber));
    }
}
