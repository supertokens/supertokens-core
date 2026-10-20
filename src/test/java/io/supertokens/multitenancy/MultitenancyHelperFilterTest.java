/*
 *    Copyright (c) 2025, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.multitenancy;

import com.google.gson.JsonObject;
import io.supertokens.pluginInterface.multitenancy.EmailPasswordConfig;
import io.supertokens.pluginInterface.multitenancy.PasswordlessConfig;
import io.supertokens.pluginInterface.multitenancy.TenantConfig;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.ThirdPartyConfig;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Pure unit tests (no running core / no DB) for the {@code supertokens_saas_load_only_cud} filter in
 * {@link MultitenancyHelper#filterTenantConfigsForLoadOnlyCUD}. The filter keeps the default CUD plus
 * any CUD whose (already-normalized) connectionUriDomain exactly equals the (already-normalized)
 * configured value, and reports every excluded CUD so the caller can log a drop of a live CUD.
 */
public class MultitenancyHelperFilterTest {

    private static TenantConfig tenantConfigForCUD(String connectionUriDomain) {
        return new TenantConfig(
                new TenantIdentifier(connectionUriDomain, null, null),
                new EmailPasswordConfig(true),
                new ThirdPartyConfig(true, null),
                new PasswordlessConfig(true),
                null, null, new JsonObject());
    }

    private static boolean containsCUD(TenantConfig[] configs, String connectionUriDomain) {
        for (TenantConfig config : configs) {
            if (config.tenantIdentifier.getConnectionUriDomain().equals(connectionUriDomain)) {
                return true;
            }
        }
        return false;
    }

    // A CUD whose (normalized) connectionUriDomain equals the configured load-only value is retained and
    // is never reported as dropped.
    @Test
    public void matchingCUDIsRetained() {
        String loadOnlyCUD = "example.com";
        String storedCUD = "example.com";

        List<TenantConfig> dropped = new ArrayList<>();
        TenantConfig[] filtered = MultitenancyHelper.filterTenantConfigsForLoadOnlyCUD(
                new TenantConfig[]{tenantConfigForCUD(storedCUD)}, loadOnlyCUD, dropped);

        assertEquals(1, filtered.length);
        assertTrue(containsCUD(filtered, "example.com"));
        assertTrue(dropped.isEmpty());
    }

    // The default (base) CUD is always retained regardless of the load-only value, and is never reported
    // as dropped.
    @Test
    public void defaultCUDIsAlwaysRetained() {
        List<TenantConfig> dropped = new ArrayList<>();
        TenantConfig[] filtered = MultitenancyHelper.filterTenantConfigsForLoadOnlyCUD(
                new TenantConfig[]{tenantConfigForCUD(TenantIdentifier.DEFAULT_CONNECTION_URI)}, "example.com",
                dropped);

        assertEquals(1, filtered.length);
        assertTrue(containsCUD(filtered, TenantIdentifier.DEFAULT_CONNECTION_URI));
        assertTrue(dropped.isEmpty());
    }

    // A genuinely different CUD is dropped and reported so a live-CUD wipe can be logged loudly, while the
    // default CUD and the matching CUD are retained.
    @Test
    public void unrelatedCUDIsDroppedAndReported() {
        String loadOnlyCUD = "example.com";
        TenantConfig defaultTenant = tenantConfigForCUD(TenantIdentifier.DEFAULT_CONNECTION_URI);
        TenantConfig kept = tenantConfigForCUD("example.com");
        TenantConfig droppedCUD = tenantConfigForCUD("other.com");

        List<TenantConfig> dropped = new ArrayList<>();
        TenantConfig[] filtered = MultitenancyHelper.filterTenantConfigsForLoadOnlyCUD(
                new TenantConfig[]{defaultTenant, kept, droppedCUD}, loadOnlyCUD, dropped);

        assertTrue(containsCUD(filtered, TenantIdentifier.DEFAULT_CONNECTION_URI));
        assertTrue(containsCUD(filtered, "example.com"));
        assertFalse(containsCUD(filtered, "other.com"));

        assertEquals(1, dropped.size());
        assertEquals("other.com", dropped.get(0).tenantIdentifier.getConnectionUriDomain());
    }
}
