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

public class DisableFactorAPITest extends MfaTestBase {
    private HttpResponseException disableFactorRequestAndReturnException(TestingProcessManager.TestingProcess process, JsonObject body) {
        return assertThrows(
                HttpResponseException.class,
                () -> disableFactorRequest(process, body));
    }

    @Test
    public void testApi() throws Exception {
        TestSetupResult result = initSteps();
        assert result != null;

        // Prepare by enabling 2 factors for a user
        {
            JsonObject body = new JsonObject();
            body.addProperty("userId", "userId");
            body.addProperty("factor", "f1");
            enableFactorRequest(result.process, body);

            body.addProperty("factor", "f2");
            enableFactorRequest(result.process, body);
        }

        JsonObject body = new JsonObject();
        // Missing userId/factor
        {
            Exception e  = disableFactorRequestAndReturnException(result.process, body);
            checkFieldMissingErrorResponse(e, "userId");

            body.addProperty("userId", "");
            e  = disableFactorRequestAndReturnException(result.process, body);
            checkFieldMissingErrorResponse(e, "factor");
        }
        // Invalid userId/factor
        {
            body.addProperty("factor", "");
            Exception e = disableFactorRequestAndReturnException(result.process, body);
            checkResponseErrorContains(e, "userId cannot be empty");

            body.addProperty("userId", "userId");
            e = disableFactorRequestAndReturnException(result.process, body);
            checkResponseErrorContains(e, "factor cannot be empty");
        }

        body.addProperty("factor", "f1");
        // Should pass now:
        JsonObject res = disableFactorRequest(result.process, body);
        assert res.get("status").getAsString().equals("OK");
        assert res.get("didExist").getAsBoolean() == true;

        // Repeat the same request, should pass but wasAlreadyEnabled should be true
        res = disableFactorRequest(result.process, body);
        assert res.get("status").getAsString().equals("OK");
        assert res.get("didExist").getAsBoolean() == false;

        HashMap<String, String> params = new HashMap<>();
        params.put("userId", "userId");

        // Check that the factor was actually disabled
        res = listFactorsRequest(result.process, params);
        assert res.get("status").getAsString().equals("OK");
        assert res.get("factors").getAsJsonArray().size() == 1;
        assert res.get("factors").getAsJsonArray().get(0).getAsString().equals("f2");
    }
}
