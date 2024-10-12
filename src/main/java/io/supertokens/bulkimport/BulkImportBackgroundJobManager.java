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

package io.supertokens.bulkimport;

import io.supertokens.Main;
import io.supertokens.cronjobs.Cronjobs;
import io.supertokens.cronjobs.bulkimport.ProcessBulkImportUsers;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;

public class BulkImportBackgroundJobManager {

    public static BULK_IMPORT_BACKGROUND_PROCESS_STATUS startBackgroundJob(Main main, Integer batchSize) throws TenantOrAppNotFoundException {
        ProcessBulkImportUsers processBulkImportUsersCron = (ProcessBulkImportUsers) main.getResourceDistributor().getResource(new TenantIdentifier(null, null, null), ProcessBulkImportUsers.RESOURCE_KEY);
        processBulkImportUsersCron.setBatchSize(batchSize);
        Cronjobs.addCronjob(main, processBulkImportUsersCron);
        return BULK_IMPORT_BACKGROUND_PROCESS_STATUS.ACTIVE;
    }

    public static BULK_IMPORT_BACKGROUND_PROCESS_STATUS stopBackgroundJob(Main main) throws TenantOrAppNotFoundException {
        ProcessBulkImportUsers processBulkImportUsersCron = (ProcessBulkImportUsers) main.getResourceDistributor().getResource(new TenantIdentifier(null, null, null), ProcessBulkImportUsers.RESOURCE_KEY);
        Cronjobs.removeCronjob(main, processBulkImportUsersCron);
        return BULK_IMPORT_BACKGROUND_PROCESS_STATUS.INACTIVE;
    }

    public static BULK_IMPORT_BACKGROUND_PROCESS_STATUS checkBackgroundJobStatus(Main main)
            throws TenantOrAppNotFoundException {
        ProcessBulkImportUsers processBulkImportUsersCron = (ProcessBulkImportUsers) main.getResourceDistributor().getResource(new TenantIdentifier(null, null, null), ProcessBulkImportUsers.RESOURCE_KEY);
        BULK_IMPORT_BACKGROUND_PROCESS_STATUS status;
        if(Cronjobs.isCronjobLoaded(main, processBulkImportUsersCron)){
            status = BULK_IMPORT_BACKGROUND_PROCESS_STATUS.ACTIVE;
        } else {
            status = BULK_IMPORT_BACKGROUND_PROCESS_STATUS.INACTIVE;
        }
        return status;
    }

    public enum BULK_IMPORT_BACKGROUND_PROCESS_STATUS {
        ACTIVE, INACTIVE;
    }
}
