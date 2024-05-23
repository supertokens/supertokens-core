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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.util.List;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

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
      try {
        JsonObject request = new JsonObject();
        HttpRequestForTesting.sendJsonPOSTRequest(main, "",
            "http://localhost:3567/bulk-import/import",
            request, 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), null);
        fail("The API should have thrown an error");
      } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
        assertEquals(400, e.statusCode);
        assertEquals("Http error. Status Code: 400. Message: Field name 'user' is invalid in JSON input",
            e.getMessage());
      }
    }

    {
      FeatureFlagTestContent.getInstance(main).setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES,
          new EE_FEATURES[] { EE_FEATURES.MULTI_TENANCY, EE_FEATURES.MFA, EE_FEATURES.ACCOUNT_LINKING });

      try {
        JsonObject request = new JsonObject();
        List<BulkImportUser> users = BulkImportTestUtils.generateBulkImportUser(1);
        request.add("user", users.get(0).toJsonObject());

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
    BulkImportTestUtils.createTenants(main);

    // Create user roles
    {
      UserRoles.createNewRoleOrModifyItsPermissions(main, "role1", null);
      UserRoles.createNewRoleOrModifyItsPermissions(main, "role2", null);
    }

    JsonObject request = new JsonObject();
    List<BulkImportUser> users = BulkImportTestUtils.generateBulkImportUser(1);
    request.add("user", users.get(0).toJsonObject());

    JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(main, "",
        "http://localhost:3567/bulk-import/import",
        request, 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), null);

    assertEquals("OK", response.get("status").getAsString());
    assertNotNull(response.get("user"));

    process.kill();
    Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
  }

}
