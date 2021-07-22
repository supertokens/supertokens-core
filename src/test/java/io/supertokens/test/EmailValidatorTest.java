/*
 *    Copyright (c) 2021, VRAI Labs and/or its affiliates. All rights reserved.
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

import io.supertokens.utils.EmailValidator;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EmailValidatorTest {

    @Test
    public void testShouldReturnTrueForValidEmails() {
        List<String> validEmails = Arrays.asList(
                "john.doe@example.com",
                "jd@e.co",
                "j+d@example.com",
                "j_d@example.com",
                "jd@example.dot.com"
        );

        validEmails.forEach(email -> assertTrue("Email " + email + " should be valid", EmailValidator.isValid(email)));
    }

    @Test
    public void testShouldReturnFalseForInvalidEmails() {
        List<String> invalidEmails = Arrays.asList(
                "",
                " ",
                " @example.com",
                " jd@example.com", // leading whitespace
                "jd@example.com ", // trailing whitespace
                "@example.com",
                "jd@ex",
                "@.com",
                "jd@",
                "jd@example.c",
                "jd.example.com",
                "jd@ex.co1",
                "jd@ex_ample.com"
        );

        invalidEmails.forEach(email -> assertFalse("Email " + email + " should be invalid", EmailValidator.isValid(email)));
    }
}
