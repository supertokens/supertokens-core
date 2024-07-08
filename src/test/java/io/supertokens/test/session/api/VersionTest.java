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

package io.supertokens.test.session.api;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.session.accessToken.AccessToken;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.httpRequest.HttpResponseException;
import io.supertokens.utils.SemVer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class VersionTest {
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

    private SemVer getCDIVersionForAccessTokenVersion(String version) {
        return switch (version) {
            case "2" -> SemVer.v2_9;
            case "3" -> SemVer.v2_21;
            case "4" -> SemVer.v3_0;
            default -> throw new IllegalStateException("Unexpected value: " + version);
        };
    }

    private JsonObject getSession(Main main, String version, String sessionHandle)
            throws HttpResponseException, IOException {
        Map<String, String> params = new HashMap<>();
        params.put("sessionHandle", sessionHandle);
        return HttpRequestForTesting.sendGETRequest(main, "", "http://localhost:3567/recipe/session",
                params, 1000, 1000, null, getCDIVersionForAccessTokenVersion(version).get(), "session");
    }

    @Test
    public void testVerifySessionAcrossVersions() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String tokenVersions[] = new String[]{"2", "3", "4"};

        for (String createVersion : tokenVersions) {
            for (String updateVersion : tokenVersions) {

                String userId = "userId";
                JsonObject userDataInJWT = new JsonObject();
                userDataInJWT.addProperty("key", "value");
                userDataInJWT.add("nullProp", JsonNull.INSTANCE);
                JsonObject userDataInDatabase = new JsonObject();
                userDataInDatabase.addProperty("key", "value");

                JsonObject sessionRequest = new JsonObject();
                sessionRequest.addProperty("userId", userId);
                sessionRequest.add("userDataInJWT", userDataInJWT);
                sessionRequest.add("userDataInDatabase", userDataInDatabase);
                sessionRequest.addProperty("enableAntiCsrf", false);

                JsonObject sessionInfo = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/session", sessionRequest, 1000, 1000, null,
                        getCDIVersionForAccessTokenVersion(createVersion).get(), "session");

                String accessToken = sessionInfo.get("accessToken").getAsJsonObject().get("token").getAsString();

                AccessToken.AccessTokenInfo accessTokenInfo = AccessToken.getInfoFromAccessToken(process.getProcess(),
                        accessToken, false);
                assertEquals(AccessToken.getVersionFromString(createVersion), accessTokenInfo.version);

                { // Session verify
                    // Should be verified as is without the need for refresh
                    JsonObject request = new JsonObject();
                    request.addProperty("accessToken",
                            sessionInfo.get("accessToken").getAsJsonObject().get("token").getAsString());
                    request.addProperty("doAntiCsrfCheck", true);
                    request.addProperty("enableAntiCsrf", false);
                    request.addProperty("checkDatabase", false);
                    JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                            "http://localhost:3567/recipe/session/verify", request, 1000, 1000, null,
                            getCDIVersionForAccessTokenVersion(updateVersion).get(), "session");

                    junit.framework.TestCase.assertEquals(response.get("status").getAsString(), "OK");

                    assertNotNull(response.get("session").getAsJsonObject().get("handle").getAsString());
                    junit.framework.TestCase.assertEquals(
                            response.get("session").getAsJsonObject().get("userId").getAsString(), userId);
                    junit.framework.TestCase.assertEquals(
                            response.get("session").getAsJsonObject().get("userDataInJWT").getAsJsonObject(),
                            userDataInJWT);
                    if (getCDIVersionForAccessTokenVersion(updateVersion).lesserThan(SemVer.v3_0)) {
                        junit.framework.TestCase.assertEquals(
                                response.get("session").getAsJsonObject().entrySet().size(), 3);
                    } else {
                        junit.framework.TestCase.assertEquals(
                                response.get("session").getAsJsonObject().entrySet().size(), 4);
                    }
                }
            }
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testRegenerateAcrossVersions() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String tokenVersions[] = new String[]{"2", "3", "4"};

        for (String createVersion : tokenVersions) {
            for (String updateVersion : tokenVersions) {

                String userId = "userId";
                JsonObject userDataInJWT = new JsonObject();
                userDataInJWT.addProperty("key", "value");
                userDataInJWT.add("nullProp", JsonNull.INSTANCE);
                JsonObject userDataInDatabase = new JsonObject();
                userDataInDatabase.addProperty("key", "value");

                JsonObject sessionRequest = new JsonObject();
                sessionRequest.addProperty("userId", userId);
                sessionRequest.add("userDataInJWT", userDataInJWT);
                sessionRequest.add("userDataInDatabase", userDataInDatabase);
                sessionRequest.addProperty("enableAntiCsrf", false);

                JsonObject sessionInfo = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/session", sessionRequest, 1000, 1000, null,
                        getCDIVersionForAccessTokenVersion(createVersion).get(), "session");

                { // Session regenerate
                    // Should not update the version
                    JsonObject newUserDataInJWT = new JsonObject();
                    newUserDataInJWT.addProperty("key2", "value2");
                    newUserDataInJWT.add("nullProp", JsonNull.INSTANCE);
                    JsonObject sessionRegenerateRequest = new JsonObject();
                    sessionRegenerateRequest.addProperty("accessToken",
                            sessionInfo.get("accessToken").getAsJsonObject().get("token").getAsString());
                    sessionRegenerateRequest.add("userDataInJWT", newUserDataInJWT);

                    String accessToken = sessionInfo.get("accessToken").getAsJsonObject().get("token").getAsString();

                    AccessToken.AccessTokenInfo accessTokenInfoBefore = AccessToken.getInfoFromAccessToken(
                            process.getProcess(),
                            accessToken, false);

                    JsonObject sessionRegenerateResponse = HttpRequestForTesting.sendJsonPOSTRequest(
                            process.getProcess(), "",
                            "http://localhost:3567/recipe/session/regenerate", sessionRegenerateRequest, 1000, 1000,
                            null,
                            getCDIVersionForAccessTokenVersion(updateVersion).get(), "session");

                    AccessToken.AccessTokenInfo accessTokenInfoAfter = AccessToken.getInfoFromAccessToken(
                            process.getProcess(),
                            sessionRegenerateResponse.get("accessToken").getAsJsonObject().get("token").getAsString(),
                            false);

                    assertEquals(accessTokenInfoBefore.version, accessTokenInfoAfter.version);
                }
            }
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testSessionRefreshAcrossVersions() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String tokenVersions[] = new String[]{"2", "3", "4"};

        for (String createVersion : tokenVersions) {
            for (String updateVersion : tokenVersions) {

                String userId = "userId";
                JsonObject userDataInJWT = new JsonObject();
                userDataInJWT.addProperty("key", "value");
                userDataInJWT.add("nullProp", JsonNull.INSTANCE);
                JsonObject userDataInDatabase = new JsonObject();
                userDataInDatabase.addProperty("key", "value");

                JsonObject sessionRequest = new JsonObject();
                sessionRequest.addProperty("userId", userId);
                sessionRequest.add("userDataInJWT", userDataInJWT);
                sessionRequest.add("userDataInDatabase", userDataInDatabase);
                sessionRequest.addProperty("enableAntiCsrf", false);

                JsonObject sessionInfo = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/session", sessionRequest, 1000, 1000, null,
                        getCDIVersionForAccessTokenVersion(createVersion).get(), "session");

                { // Session regenerate
                    // Should not update the version
                    JsonObject sessionRefreshRequest = new JsonObject();
                    sessionRefreshRequest.addProperty("refreshToken",
                            sessionInfo.get("refreshToken").getAsJsonObject().get("token").getAsString());
                    sessionRefreshRequest.addProperty("enableAntiCsrf", false);

                    String accessToken = sessionInfo.get("accessToken").getAsJsonObject().get("token").getAsString();

                    JsonObject sessionRefreshResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(),
                            "",
                            "http://localhost:3567/recipe/session/refresh", sessionRefreshRequest, 1000, 1000, null,
                            getCDIVersionForAccessTokenVersion(updateVersion).get(), "session");

                    AccessToken.AccessTokenInfo accessTokenInfoAfter = AccessToken.getInfoFromAccessToken(
                            process.getProcess(),
                            sessionRefreshResponse.get("accessToken").getAsJsonObject().get("token").getAsString(),
                            false);

                    assertEquals(AccessToken.getVersionFromString(updateVersion), accessTokenInfoAfter.version);
                }
            }
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testUpdateSessionDataAcrossVersions() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String tokenVersions[] = new String[]{"2", "3", "4"};

        for (String createVersion : tokenVersions) {
            for (String updateVersion : tokenVersions) {

                String userId = "userId";
                JsonObject userDataInJWT = new JsonObject();
                userDataInJWT.addProperty("key", "value");
                userDataInJWT.add("nullProp", JsonNull.INSTANCE);
                JsonObject userDataInDatabase = new JsonObject();
                userDataInDatabase.addProperty("key", "value");

                JsonObject sessionRequest = new JsonObject();
                sessionRequest.addProperty("userId", userId);
                sessionRequest.add("userDataInJWT", userDataInJWT);
                sessionRequest.add("userDataInDatabase", userDataInDatabase);
                sessionRequest.addProperty("enableAntiCsrf", false);

                JsonObject sessionInfo = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/session", sessionRequest, 1000, 1000, null,
                        getCDIVersionForAccessTokenVersion(createVersion).get(), "session");

                { // Update session data
                    JsonObject newUserDataInDatabase = new JsonObject();
                    newUserDataInDatabase.addProperty("key2", "value2");

                    JsonObject putRequestBody = new JsonObject();
                    putRequestBody.addProperty("sessionHandle",
                            sessionInfo.get("session").getAsJsonObject().get("handle").getAsString());
                    putRequestBody.add("userDataInDatabase", newUserDataInDatabase);

                    JsonObject sessionDataResponse = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                            "http://localhost:3567/recipe/session/data", putRequestBody, 1000, 1000, null,
                            getCDIVersionForAccessTokenVersion(updateVersion).get(), "session");

                    assertEquals("OK", sessionDataResponse.get("status").getAsString());

                    JsonObject newSessionInfo = getSession(process.getProcess(), updateVersion,
                            sessionInfo.get("session").getAsJsonObject().get("handle").getAsString());
                    assertEquals(newUserDataInDatabase, newSessionInfo.get("userDataInDatabase").getAsJsonObject());
                }
            }
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testUpdateJWTDataAcrossVersions() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String tokenVersions[] = new String[]{"2", "3", "4"};

        for (String createVersion : tokenVersions) {
            for (String updateVersion : tokenVersions) {

                String userId = "userId";
                JsonObject userDataInJWT = new JsonObject();
                userDataInJWT.addProperty("key", "value");
                userDataInJWT.add("nullProp", JsonNull.INSTANCE);
                JsonObject userDataInDatabase = new JsonObject();
                userDataInDatabase.addProperty("key", "value");

                JsonObject sessionRequest = new JsonObject();
                sessionRequest.addProperty("userId", userId);
                sessionRequest.add("userDataInJWT", userDataInJWT);
                sessionRequest.add("userDataInDatabase", userDataInDatabase);
                sessionRequest.addProperty("enableAntiCsrf", false);

                JsonObject sessionInfo = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/session", sessionRequest, 1000, 1000, null,
                        getCDIVersionForAccessTokenVersion(createVersion).get(), "session");

                { // Update JWT data
                    JsonObject newUserDataInDatabase = new JsonObject();
                    newUserDataInDatabase.addProperty("key2", "value2");

                    JsonObject putRequestBody = new JsonObject();
                    putRequestBody.addProperty("sessionHandle",
                            sessionInfo.get("session").getAsJsonObject().get("handle").getAsString());
                    putRequestBody.add("userDataInJWT", newUserDataInDatabase);

                    JsonObject sessionDataResponse = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                            "http://localhost:3567/recipe/jwt/data", putRequestBody, 1000, 1000, null,
                            getCDIVersionForAccessTokenVersion(updateVersion).get(), "session");

                    assertEquals("OK", sessionDataResponse.get("status").getAsString());

                    JsonObject newSessionInfo = getSession(process.getProcess(), updateVersion,
                            sessionInfo.get("session").getAsJsonObject().get("handle").getAsString());
                    assertEquals(newUserDataInDatabase, newSessionInfo.get("userDataInJWT").getAsJsonObject());
                }
            }
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
