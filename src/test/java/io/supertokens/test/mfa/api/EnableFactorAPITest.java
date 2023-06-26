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

package io.supertokens.test.mfa.api;

import com.google.gson.JsonObject;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.httpRequest.HttpResponseException;
import io.supertokens.test.mfa.MfaTestBase;
import org.junit.Test;

import java.util.HashMap;

import static org.junit.Assert.assertThrows;

public class EnableFactorAPITest extends MfaTestBase {
    private HttpResponseException enableFactorRequestAndReturnException(TestingProcessManager.TestingProcess process, JsonObject body) {
        return assertThrows(
                HttpResponseException.class,
                () -> enableFactorRequest(process, body));
    }

    @Test
    public void testApi() throws Exception {
        TestSetupResult result = initSteps();
        assert result != null;

        JsonObject body = new JsonObject();
        // Missing userId/factor
        {
            Exception e  = enableFactorRequestAndReturnException(result.process, body);
            checkFieldMissingErrorResponse(e, "userId");

            body.addProperty("userId", "");
            e  = enableFactorRequestAndReturnException(result.process, body);
            checkFieldMissingErrorResponse(e, "factor");
        }
        // Invalid userId/factor
        {
            body.addProperty("factor", "");
            Exception e = enableFactorRequestAndReturnException(result.process, body);
            checkResponseErrorContains(e, "userId cannot be empty");

            body.addProperty("userId", "userId");
            e = enableFactorRequestAndReturnException(result.process, body);
            checkResponseErrorContains(e, "factor cannot be empty");
        }

        body.addProperty("factor", "f1");
        // Should pass now:
        JsonObject res = enableFactorRequest(result.process, body);
        assert res.get("status").getAsString().equals("OK");
        assert res.get("didExist").getAsBoolean() == false;

        // Repeat the same request, should pass but didExist should be true
        res = enableFactorRequest(result.process, body);
        assert res.get("status").getAsString().equals("OK");
        assert res.get("didExist").getAsBoolean() == true;

        // Check that the factors were actually enabled
        {
            HashMap<String, String> params = new HashMap<>();
            params.put("userId", "userId");
            res = listFactorsRequest(result.process, params);
            assert res.get("status").getAsString().equals("OK");
            assert res.get("factors").getAsJsonArray().size() == 1;
            assert res.get("factors").getAsJsonArray().get(0).getAsString().equals("f1");
        }

    }
}
