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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.HashMap;
import java.util.Map;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import com.google.gson.JsonObject;

import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;


public class CountBulkImportUsersTest {
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

    try {
      Map<String, String> params = new HashMap<>();
      params.put("status", "INVALID_STATUS");
      HttpRequestForTesting.sendGETRequest(main, "",
          "http://localhost:3567/bulk-import/users/count",
          params, 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), null);
      fail("The API should have thrown an error");
    } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
      assertEquals(400, e.statusCode);
      assertEquals(
          "Http error. Status Code: 400. Message: Invalid value for status. Pass one of NEW, PROCESSING, or FAILED!",
          e.getMessage());
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

    {
      Map<String, String> params = new HashMap<>();
      JsonObject response = HttpRequestForTesting.sendGETRequest(main, "",
          "http://localhost:3567/bulk-import/users/count",
          params, 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), null);

      assertEquals("OK", response.get("status").getAsString());
      assertEquals(0, response.get("count").getAsLong());
    }

    {
      Map<String, String> params = new HashMap<>();
      params.put("status", "NEW");
      JsonObject response = HttpRequestForTesting.sendGETRequest(main, "",
          "http://localhost:3567/bulk-import/users/count",
          params, 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), null);

      assertEquals("OK", response.get("status").getAsString());
      assertEquals(0, response.get("count").getAsLong());
    }

    {
      Map<String, String> params = new HashMap<>();
      params.put("status", "PROCESSING");
      JsonObject response = HttpRequestForTesting.sendGETRequest(main, "",
          "http://localhost:3567/bulk-import/users/count",
          params, 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), null);

      assertEquals("OK", response.get("status").getAsString());
      assertEquals(0, response.get("count").getAsLong());
    }

    {
      Map<String, String> params = new HashMap<>();
      params.put("status", "FAILED");
      JsonObject response = HttpRequestForTesting.sendGETRequest(main, "",
          "http://localhost:3567/bulk-import/users/count",
          params, 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), null);

      assertEquals("OK", response.get("status").getAsString());
      assertEquals(0, response.get("count").getAsLong());
    }

    process.kill();
    Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
  }

}
