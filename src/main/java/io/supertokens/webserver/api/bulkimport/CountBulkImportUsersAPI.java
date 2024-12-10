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

package io.supertokens.webserver.api.bulkimport;

import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.bulkimport.BulkImport;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.bulkimport.BulkImportStorage.BULK_IMPORT_USER_STATUS;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public class CountBulkImportUsersAPI extends WebserverAPI {
  public CountBulkImportUsersAPI(Main main) {
    super(main, "bulkimport");
  }

  @Override
  public String getPath() {
    return "/bulk-import/users/count";
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    // API is app specific

    if (StorageLayer.isInMemDb(main)) {
      throw new ServletException(new BadRequestException("This API is not supported in the in-memory database."));
    }

    String statusString = InputParser.getQueryParamOrThrowError(req, "status", true);

    BULK_IMPORT_USER_STATUS status = null;
    if (statusString != null) {
      try {
        status = BULK_IMPORT_USER_STATUS.valueOf(statusString);
      } catch (IllegalArgumentException e) {
        throw new ServletException(
            new BadRequestException("Invalid value for status. Pass one of NEW, PROCESSING, or FAILED!"));
      }
    }

    AppIdentifier appIdentifier = null;
    Storage storage = null;

    try {
      appIdentifier = getAppIdentifier(req);
      storage = enforcePublicTenantAndGetPublicTenantStorage(req);

      long count = BulkImport.getBulkImportUsersCount(appIdentifier, storage, status);

      JsonObject result = new JsonObject();
      result.addProperty("status", "OK");
      result.addProperty("count", count);
      super.sendJsonResponse(200, result, resp);

    } catch (TenantOrAppNotFoundException | BadPermissionException | StorageQueryException e) {
      throw new ServletException(e);
    }
  }
}
