/*
 *    Copyright (c) 2024, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.test.bulkimport.apis;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.bulkimport.BulkImportUser;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.bulkimport.BulkImportTestUtils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.userroles.UserRoles;
import org.junit.*;
import org.junit.rules.TestRule;

import java.util.List;

import static org.junit.Assert.*;

public class ImportUserTest {
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
  public void shouldReturn400Error() throws Exception {
    String[] args = { "../" };

    TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
    assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
    Main main = process.getProcess();

    if (StorageLayer.getStorage(main).getType() != STORAGE_TYPE.SQL || StorageLayer.isInMemDb(main)) {
      return;
    }

    {
      FeatureFlagTestContent.getInstance(main).setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES,
          new EE_FEATURES[] { EE_FEATURES.MULTI_TENANCY, EE_FEATURES.MFA, EE_FEATURES.ACCOUNT_LINKING });

      try {
        List<BulkImportUser> users = BulkImportTestUtils.generateBulkImportUser(1);
        JsonObject request = users.get(0).toJsonObject();

        HttpRequestForTesting.sendJsonPOSTRequest(main, "",
            "http://localhost:3567/bulk-import/import",
            request, 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), null);
        fail("The API should have thrown an error");
      } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
        assertEquals(400, e.statusCode);
        assertEquals(
            "Http error. Status Code: 400. Message: {\"errors\":[\"Role role1 does not exist.\",\"Invalid tenantId: t1 for a user role.\",\"Role role2 does not exist.\",\"Invalid tenantId: t1 for a user role.\",\"Invalid tenantId: t1 for emailpassword recipe.\",\"Invalid tenantId: t1 for thirdparty recipe.\",\"Invalid tenantId: t1 for passwordless recipe.\"]}",
            e.getMessage());
      }
    }

    process.kill();
    Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
  }

  @Test
  public void shouldReturn200Response() throws Exception {
    String[] args = { "../" };

    TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
    assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
    Main main = process.getProcess();

    if (StorageLayer.getStorage(main).getType() != STORAGE_TYPE.SQL || StorageLayer.isInMemDb(main)) {
      return;
    }

    FeatureFlagTestContent.getInstance(main).setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES,
        new EE_FEATURES[] { EE_FEATURES.MULTI_TENANCY, EE_FEATURES.MFA, EE_FEATURES.ACCOUNT_LINKING });

    // Create tenants
    BulkImportTestUtils.createTenants(process);

    // Create user roles
    {
      UserRoles.createNewRoleOrModifyItsPermissions(main, "role1", null);
      UserRoles.createNewRoleOrModifyItsPermissions(main, "role2", null);
    }
    List<BulkImportUser> users = BulkImportTestUtils.generateBulkImportUser(1);
    JsonObject request = users.get(0).toJsonObject();

    JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(main, "",
        "http://localhost:3567/bulk-import/import",
        request, 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), null);

    assertEquals("OK", response.get("status").getAsString());
    assertNotNull(response.get("user"));

    process.kill();
    Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
  }

    @Test
    public void shouldImportUserWithoutRoles() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        Main main = process.getProcess();

        if (StorageLayer.getStorage(main).getType() != STORAGE_TYPE.SQL || StorageLayer.isInMemDb(main)) {
            return;
        }

        FeatureFlagTestContent.getInstance(main).setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES,
                new EE_FEATURES[] { EE_FEATURES.MULTI_TENANCY, EE_FEATURES.MFA, EE_FEATURES.ACCOUNT_LINKING });

        List<BulkImportUser> users = BulkImportTestUtils.generateBulkImportUserWithRoles(1, List.of("public"), 0, List.of());
        JsonObject request = users.get(0).toJsonObject();

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                "http://localhost:3567/bulk-import/import",
                request, 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), null);

        assertEquals("OK", response.get("status").getAsString());
        assertNotNull(response.get("user"));

        process.kill();
        Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void shouldImportUserWithOneLoginMethod() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        Main main = process.getProcess();

        if (StorageLayer.getStorage(main).getType() != STORAGE_TYPE.SQL || StorageLayer.isInMemDb(main)) {
            return;
        }

        FeatureFlagTestContent.getInstance(main).setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES,
                new EE_FEATURES[] { EE_FEATURES.MULTI_TENANCY, EE_FEATURES.MFA, EE_FEATURES.ACCOUNT_LINKING });

       BulkImportUser user = new BulkImportUser("random-id-lol", "random", null, List.of(), List.of(), List.of(
               new BulkImportUser.LoginMethod(List.of("public"), "emailpassword", true, false, System.currentTimeMillis(),
                       "test@testing.com", "$2a", "BCRYPT", null, null,
                       null, null, io.supertokens.utils.Utils.getUUID())));
        JsonObject request = user.toJsonObject();

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                "http://localhost:3567/bulk-import/import",
                request, 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), null);

        assertEquals("OK", response.get("status").getAsString());
        assertNotNull(response.get("user"));

        process.kill();
        Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void shouldImportBareMinimumUser() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        Main main = process.getProcess();

        if (StorageLayer.getStorage(main).getType() != STORAGE_TYPE.SQL || StorageLayer.isInMemDb(main)) {
            return;
        }

        FeatureFlagTestContent.getInstance(main).setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES,
                new EE_FEATURES[] { EE_FEATURES.MULTI_TENANCY, EE_FEATURES.MFA, EE_FEATURES.ACCOUNT_LINKING });

        BulkImportUser user = new BulkImportUser("random-id-lol", null, null, List.of(), List.of(), List.of(
                new BulkImportUser.LoginMethod(List.of("public"), "emailpassword", true, false, System.currentTimeMillis(),
                        "test@testing.com", "$2a", "BCRYPT", null, null,
                        null, null, io.supertokens.utils.Utils.getUUID())));
        JsonObject request = user.toJsonObject();
        request.remove("totpDevices");
        request.remove("userRoles");

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                "http://localhost:3567/bulk-import/import",
                request, 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), null);

        assertEquals("OK", response.get("status").getAsString());
        assertNotNull(response.get("user"));

        process.kill();
        Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void shouldImportFailWithoutAccountLinkingEnabled() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        Main main = process.getProcess();

        if (StorageLayer.getStorage(main).getType() != STORAGE_TYPE.SQL || StorageLayer.isInMemDb(main)) {
            return;
        }

        FeatureFlagTestContent.getInstance(main).setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES,
                new EE_FEATURES[] { EE_FEATURES.MULTI_TENANCY});

        String userJsonStr = "{\"id\":\"random-id-lol\",\"loginMethods\":[{\"tenantIds\":[\"public\"],\"isVerified\":true,\"isPrimary\":true,\"timeJoinedInMSSinceEpoch\":1741077729471,\"recipeId\":\"emailpassword\",\"email\":\"test@sometestmail.com\",\"passwordHash\":\"$2a\",\"hashingAlgorithm\":\"BCRYPT\"}]}";
        JsonObject request = new Gson().fromJson(userJsonStr, JsonObject.class);

        try {
            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                    "http://localhost:3567/bulk-import/import",
                    request, 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), null);
            fail();
        } catch (Exception e) {
            assertTrue(
                    e.getMessage().contains("E019: Account linking feature is not enabled for this app. Please contact support to enable it."));

        }

        process.kill();
        Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void shouldImportSucceedWithoutAccountLinkingEnabled() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        Main main = process.getProcess();

        if (StorageLayer.getStorage(main).getType() != STORAGE_TYPE.SQL || StorageLayer.isInMemDb(main)) {
            return;
        }

        FeatureFlagTestContent.getInstance(main).setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES,
                new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});

        BulkImportTestUtils.createTenants(process);

        String userJsonStr = """
                    {
                    "externalUserId":"some-text-you-like",
                    "userRoles":[
                        {
                            "role":"user",
                                "tenantIds":[
                                    "public",
                                    "t1"
                                ]
                        }
                     ],
                        "loginMethods":[
                            {
                            "tenantIds":[
                                "public",
                                "t1"
                            ],
                                "isVerified":true,
                                "isPrimary":false,
                                "timeJoinedInMSSinceEpoch":1506523117518,
                                "recipeId":"emailpassword",
                                "email":"something0@testing.com",
                                // passwordHash is randomly generated
                                "passwordHash":"$argon2id$v=19$m=23298,t=5,p=12$+AlWgiuzC2vqlKrde9G0SG$PmpDeTU2e6ORbHwUMi7MOavS0M3sUJlc9rX/o+nnSxt",
                                "hashingAlgorithm":"argon2"
                            }
                        ]
                }""";

        String user2JsonStr = """
                    {
                    "userRoles":[
                        {
                            "role":"user",
                                "tenantIds":[
                                    "public",
                                    "t1"
                                ]
                        }
                     ],
                        "loginMethods":[
                            {
                            "tenantIds":[
                                "public",
                                "t1"
                            ],
                                "isVerified":true,
                                "isPrimary":false,
                                "timeJoinedInMSSinceEpoch":1506523117518,
                                "recipeId":"emailpassword",
                                "email":"something1@testing.com",
                                // passwordHash is randomly generated
                                "passwordHash":"$argon2id$v=19$m=23298,t=5,p=12$+AlWgiuzC2vqlKrde9G0SG$PmpDeTU2e6ORbHwUMi7MOavS0M3sUJlc9rX/o+nnSxt",
                                "hashingAlgorithm":"argon2"
                            }
                        ]
                }""";
        // Create user roles
        UserRoles.createNewRoleOrModifyItsPermissions(main, "user", null);

        JsonObject request = new Gson().fromJson(userJsonStr, JsonObject.class);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                "http://localhost:3567/bulk-import/import",
                request, 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), null);

        assertEquals("OK", response.get("status").getAsString());
        assertNotNull(response.get("user"));

        request = new Gson().fromJson(user2JsonStr, JsonObject.class);
        JsonObject response2 = HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                "http://localhost:3567/bulk-import/import",
                request, 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), null);

        assertEquals("OK", response.get("status").getAsString());
        assertNotNull(response.get("user"));

        process.kill();
        Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void shouldImportSucceedWithoutMFAEnabledIfMFANotUsed() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        Main main = process.getProcess();

        if (StorageLayer.getStorage(main).getType() != STORAGE_TYPE.SQL || StorageLayer.isInMemDb(main)) {
            return;
        }

        FeatureFlagTestContent.getInstance(main).setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES,
                new EE_FEATURES[] { EE_FEATURES.MULTI_TENANCY, EE_FEATURES.ACCOUNT_LINKING});

        String userJsonStr = "{\"id\":\"random-id-lol\",\"loginMethods\":[{\"tenantIds\":[\"public\"],\"isVerified\":true,\"isPrimary\":true,\"timeJoinedInMSSinceEpoch\":1741077729471,\"recipeId\":\"emailpassword\",\"email\":\"test@sometestmail.com\",\"passwordHash\":\"$2a\",\"hashingAlgorithm\":\"BCRYPT\"}]}";
        JsonObject request = new Gson().fromJson(userJsonStr, JsonObject.class);
        //note that we are not adding any totp devices


        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                "http://localhost:3567/bulk-import/import",
                request, 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), null);

        assertEquals("OK", response.get("status").getAsString());
        assertNotNull(response.get("user"));

        process.kill();
        Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

}
