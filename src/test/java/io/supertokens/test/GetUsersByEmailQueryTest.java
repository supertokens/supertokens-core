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

import io.supertokens.webserver.api.thirdparty.getUsersByEmail.GetUsersByEmailQuery;
import org.junit.Test;
import org.mockito.Mockito;

import javax.servlet.http.HttpServletRequest;

import static org.junit.Assert.assertEquals;

public class GetUsersByEmailQueryTest extends Mockito {

    @Test(expected = GetUsersByEmailQuery.InvalidQueryException.class)
    public void testThrowErrorWhenEmailIsNull() throws Exception {
        // given
        HttpServletRequest request = mock(HttpServletRequest.class);

        when(request.getParameter("email")).thenReturn(null);

        // when
        new GetUsersByEmailQuery(request);

        // then it should throw
    }

    @Test
    public void testReturnWhateverInEmailParameter() throws Exception {
        // given
        HttpServletRequest request = mock(HttpServletRequest.class);

        when(request.getParameter("email")).thenReturn("john.doe@example.com");

        // when
        GetUsersByEmailQuery query = new GetUsersByEmailQuery(request);

        // then
        assertEquals("john.doe@example.com", query.getEmail());
    }
}