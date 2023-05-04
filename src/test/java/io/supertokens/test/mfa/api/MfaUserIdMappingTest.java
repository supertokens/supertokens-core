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
import io.supertokens.Main;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.pluginInterface.emailpassword.UserInfo;
import io.supertokens.useridmapping.UserIdMapping;
import org.junit.Test;

import java.util.HashMap;

public class MfaUserIdMappingTest extends MfaAPITest {
    @Test
    public void testExternalUserIdTranslation() throws Exception {
        TestSetupResult result = initSteps(true);
        Main main = result.process.getProcess();

        JsonObject body = new JsonObject();
        UserInfo user = EmailPassword.signUp(main, "test@example.com", "testPass123");
        String superTokensUserId = user.id;
        String externalUserId = "external-user-id";

        // Create user id mapping first:
        UserIdMapping.createUserIdMapping(main, superTokensUserId, externalUserId, null, false);

        body.addProperty("userId", superTokensUserId);
        body.addProperty("factor", "f1");

        // Enable factor f1 for user (use superTokensUserId for this):
        JsonObject res = enableFactorRequest(result.process, body);
        assert res.get("status").getAsString().equals("OK");
        assert res.get("didExist").getAsBoolean() == false;

        // Now use external user for all requests instead of superTokensUserId:
        body.addProperty("userId", externalUserId);

        // Enable factor f2 for user:
        body.addProperty("factor", "f2");
        res = enableFactorRequest(result.process, body);
        assert res.get("status").getAsString().equals("OK");
        assert res.get("didExist").getAsBoolean() == false;

        // List factors for user:
        HashMap<String, String> params = new HashMap<>();
        params.put("userId", externalUserId);
        res = listFactorsRequest(result.process, params);
        assert res.get("status").getAsString().equals("OK");
        assert res.get("factors").getAsJsonArray().size() == 2;
        assert res.get("factors").getAsJsonArray().get(0).getAsString().equals("f1");
        assert res.get("factors").getAsJsonArray().get(1).getAsString().equals("f2");

        // Disable factor f1 for user:
        body.addProperty("factor", "f1");
        res = disableFactorRequest(result.process, body);
        assert res.get("status").getAsString().equals("OK");
        assert res.get("didExist").getAsBoolean() == true;

        // List factors for user:
        res = listFactorsRequest(result.process, params);
        assert res.get("status").getAsString().equals("OK");
        assert res.get("factors").getAsJsonArray().size() == 1;
        assert res.get("factors").getAsJsonArray().get(0).getAsString().equals("f2");
    }
}
