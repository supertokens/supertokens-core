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
import io.supertokens.bulkimport.BulkImportBackgroundJobManager;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.Serial;

public class BulkImportBackgroundJobManagerAPI extends WebserverAPI {

    @Serial
    private static final long serialVersionUID = 2380841048110043408L;

    public BulkImportBackgroundJobManagerAPI(Main main) {
        super(main, "bulkimport");
    }

    @Override
    public String getPath() {
        return "/bulk-import/backgroundjob";
    }

    //TODO what's stopping two independent calls interfering with each-other?
    // A -> START -> STARTED.
    // B -> START -> STARTED
    // A -> STOP -> STOPPED
    // B -> waits
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {

        if (StorageLayer.isInMemDb(main)) {
            throw new ServletException(new BadRequestException("This API is not supported in the in-memory database."));
        }

        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);
        String commandText = InputParser.parseStringOrThrowError(input, "command", false);
        Integer batchSize = InputParser.parseIntOrThrowError(input, "batchSize", true);

        BULK_IMPORT_BACKGROUND_PROCESS_COMMAND command = BULK_IMPORT_BACKGROUND_PROCESS_COMMAND.valueOf(commandText);
        BulkImportBackgroundJobManager.BULK_IMPORT_BACKGROUND_PROCESS_STATUS result = null;
        try {
            if (command.equals(BULK_IMPORT_BACKGROUND_PROCESS_COMMAND.START)){
                if(batchSize == null) {
                    batchSize = BulkImport.PROCESS_USERS_BATCH_SIZE;
                }
                result = BulkImportBackgroundJobManager.startBackgroundJob(main, batchSize);

            } else if (command.equals(BULK_IMPORT_BACKGROUND_PROCESS_COMMAND.STOP)){
                result = BulkImportBackgroundJobManager.stopBackgroundJob(main);
            }
        } catch (TenantOrAppNotFoundException e) {
            throw new RuntimeException(e);
        }

        JsonObject response = new JsonObject();
        response.addProperty("status", "OK");
        response.addProperty("jobStatus", result.name());
        sendJsonResponse(200, response, resp);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {

        if (StorageLayer.isInMemDb(main)) {
            throw new ServletException(new BadRequestException("This API is not supported in the in-memory database."));
        }

        BulkImportBackgroundJobManager.BULK_IMPORT_BACKGROUND_PROCESS_STATUS processStatus;
        try {
            processStatus = BulkImportBackgroundJobManager.checkBackgroundJobStatus(main);
        } catch (TenantOrAppNotFoundException e) {
            throw new RuntimeException(e);
        }

        JsonObject response = new JsonObject();
        response.addProperty("status", "OK");
        response.addProperty("jobStatus", processStatus.name());
        sendJsonResponse(200, response, resp);
    }

    public enum BULK_IMPORT_BACKGROUND_PROCESS_COMMAND {
        START, STOP;
    }

}
