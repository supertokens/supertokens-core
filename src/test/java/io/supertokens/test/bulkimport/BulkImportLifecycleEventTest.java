/*
 *    Copyright (c) 2026, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.test.bulkimport;

import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.auditlog.lifecycle.LifecycleEventPayload;
import io.supertokens.auditlog.lifecycle.LifecycleEventType;
import io.supertokens.bulkimport.BulkImport;
import io.supertokens.bulkimport.BulkImportUserUtils;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.auditlog.ActivityLogStorage;
import io.supertokens.pluginInterface.auditlog.AuditLogEvent;
import io.supertokens.pluginInterface.bulkimport.BulkImportUser;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.userroles.UserRoles;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.List;
import java.util.Set;

import static io.supertokens.test.bulkimport.BulkImportTestUtils.generateBulkImportUser;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Verifies the lifecycle events {@code BulkImport.processUsersImportSteps} writes for imported users: one
 * {@code user_import} for the first tenant a user lands in, and one {@code tenant_association} for every
 * remaining tenant. The bulk-import proxy path is unsupported on the in-memory db, so these run on Postgres/MySQL
 * CI only; the read-side counting of that event sequence is covered storage-agnostically by
 * {@code LifecycleEventFoldTest}.
 */
public class BulkImportLifecycleEventTest {
    @Rule
    public TestRule watchman = Utils.getOnFailure();

    @Rule
    public TestRule retryFlaky = Utils.retryFlakyTest();

    @AfterClass
    public static void afterTesting() {
        Utils.afterTesting();
    }

    @Before
    public void beforeEach() {
        Utils.reset();
    }

    @Test
    public void singleTenantImportEmitsOneUserImportEventAndNoAssociation() throws Exception {
        String[] args = { "../" };
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        Main main = process.getProcess();

        if (StorageLayer.getStorage(main).getType() != STORAGE_TYPE.SQL || StorageLayer.isInMemDb(main)) {
            return;
        }

        FeatureFlagTestContent.getInstance(main).setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES,
                new EE_FEATURES[] { EE_FEATURES.MULTI_TENANCY, EE_FEATURES.MFA, EE_FEATURES.ACCOUNT_LINKING });
        UserRoles.createNewRoleOrModifyItsPermissions(main, "role1", null);
        UserRoles.createNewRoleOrModifyItsPermissions(main, "role2", null);

        AppIdentifier appIdentifier = process.getAppForTesting().toAppIdentifier();
        BulkImportUser user = generateBulkImportUser(1, List.of("public"), 0).get(0);
        String groupUserId = BulkImportUserUtils.getPrimaryLoginMethod(user).superTokensUserId;

        long from = System.currentTimeMillis() - 1;
        BulkImport.importUser(main, appIdentifier, user);
        long to = System.currentTimeMillis() + 1;

        List<AuditLogEvent> imports = readEvents(main, appIdentifier, LifecycleEventType.USER_IMPORT, from, to);
        assertEquals(1, imports.size());
        assertEquals(groupUserId, imports.get(0).recipeUserId);
        assertEquals(groupUserId, imports.get(0).primaryOrRecipeUserId);
        assertEquals("public", LifecycleEventPayload.fromJson(imports.get(0).payload).tenantId);

        // Only one tenant, so no tenant_association is written.
        assertEquals(0, readEvents(main, appIdentifier, LifecycleEventType.TENANT_ASSOCIATION, from, to).size());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void multiTenantImportEmitsUserImportPlusAssociationPerRemainingTenant() throws Exception {
        String[] args = { "../" };
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        Main main = process.getProcess();

        if (StorageLayer.getStorage(main).getType() != STORAGE_TYPE.SQL || StorageLayer.isInMemDb(main)) {
            return;
        }

        FeatureFlagTestContent.getInstance(main).setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES,
                new EE_FEATURES[] { EE_FEATURES.MULTI_TENANCY, EE_FEATURES.MFA, EE_FEATURES.ACCOUNT_LINKING });
        // t1 shares the public tenant's user pool, so the whole group lives on one storage.
        BulkImportTestUtils.createTenants(process);
        UserRoles.createNewRoleOrModifyItsPermissions(main, "role1", null);
        UserRoles.createNewRoleOrModifyItsPermissions(main, "role2", null);

        AppIdentifier appIdentifier = process.getAppForTesting().toAppIdentifier();
        // A user present in two tenants: public (the one it is "imported" into) and t1 (an association).
        BulkImportUser user = generateBulkImportUser(1, List.of("public", "t1"), 0).get(0);
        String groupUserId = BulkImportUserUtils.getPrimaryLoginMethod(user).superTokensUserId;

        long from = System.currentTimeMillis() - 1;
        BulkImport.importUser(main, appIdentifier, user);
        long to = System.currentTimeMillis() + 1;

        List<AuditLogEvent> imports = readEvents(main, appIdentifier, LifecycleEventType.USER_IMPORT, from, to);
        assertEquals(1, imports.size());
        assertEquals(groupUserId, imports.get(0).recipeUserId);
        assertEquals("public", LifecycleEventPayload.fromJson(imports.get(0).payload).tenantId);

        List<AuditLogEvent> associations =
                readEvents(main, appIdentifier, LifecycleEventType.TENANT_ASSOCIATION, from, to);
        assertEquals(1, associations.size());
        assertEquals(groupUserId, associations.get(0).recipeUserId);
        assertEquals(groupUserId, associations.get(0).primaryOrRecipeUserId);
        LifecycleEventPayload assocPayload = LifecycleEventPayload.fromJson(associations.get(0).payload);
        assertEquals("t1", assocPayload.tenantId);
        // The association's before-presence is the tenant the user_import already put the group in, so the
        // read-side interpreter counts t1 as newly present (a +1) rather than a duplicate.
        assertEquals(List.of("public"), assocPayload.groupBefore.tenantIds);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    private static List<AuditLogEvent> readEvents(Main main, AppIdentifier appIdentifier, LifecycleEventType type,
            long fromExclusive, long toInclusive) throws Exception {
        ActivityLogStorage storage = (ActivityLogStorage) StorageLayer.getStorage(main);
        return storage.getActivityLogEntriesForApp(appIdentifier, Set.of(type.getValue()), fromExclusive,
                toInclusive, 100);
    }
}
