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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.pluginInterface.mfa.MfaStorage;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.httpRequest.HttpResponseException;
import org.junit.Test;

import java.util.HashMap;

import static org.junit.Assert.assertThrows;

public class ListFactorsAPITest extends MfaAPITest {

    private HttpResponseException listFactorsRequestAndReturnException(TestingProcessManager.TestingProcess process, HashMap<String, String> params) {
        return assertThrows(
                HttpResponseException.class,
                () -> listFactorsRequest(process, params));
    }
    @Test
    public void testApi() throws Exception {
        TestSetupResult result = initSteps();
        assert result != null;

        MfaStorage storage = result.storage;
        Main main = result.process.main;

        // Prepare by enabling a factor for a user:
        {
            JsonObject body = new JsonObject();
            body.addProperty("userId", "userId");
            body.addProperty("factor", "f1");
            enableFactorRequest(result.process, body);
        }

        HashMap<String, String> params = new HashMap<>();

        // Missing userId
        {
            Exception e = listFactorsRequestAndReturnException(result.process, params);
            checkFieldMissingErrorResponse(e, "userId");
        }
        // Invalid userId
        {
            params.put("userId", "");
            Exception e = listFactorsRequestAndReturnException(result.process, params);
            checkResponseErrorContains(e, "userId cannot be empty");
        }

        params.put("userId", "userId");
        JsonObject res = listFactorsRequest(result.process, params);
        assert res.get("status").getAsString().equals("OK");

        JsonArray factors = res.get("factors").getAsJsonArray();
        String factor = factors.get(0).getAsString();

        assert factors.size() == 1;
        assert factor.equals("f1");

        // Try for a non-existing user:
        {
            params.put("userId", "userId2");
            res = listFactorsRequest(result.process, params);
            assert res.get("status").getAsString().equals("OK");

            factors = res.get("factors").getAsJsonArray();
            assert factors.size() == 0;
        }
    }
}
