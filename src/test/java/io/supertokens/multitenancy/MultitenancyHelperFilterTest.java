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
 * {@link MultitenancyHelper#filterTenantConfigsForLoadOnlyCUD}. These exercise the normalization gap
 * directly: a stored connectionUriDomain that differs from the configured value only by
 * case / port cannot be created through the API (the request path strips it), so it is constructed
 * here with a raw {@link TenantIdentifier}.
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

    // A CUD whose stored connectionUriDomain differs from the configured load-only value ONLY by the
    // normalization the config already applies (port stripped) must be retained, not silently dropped.
    @Test
    public void cudDifferingOnlyByPortIsRetained() {
        // supertokens_saas_load_only_cud is normalized on config load to "example.com" (port stripped).
        String loadOnlyCUD = "example.com";
        // ...but a stored connectionUriDomain can carry the port, and getConnectionUriDomain() only
        // trims + lowercases, so a raw .equals would not match.
        String storedCUD = "example.com:3567";
        assertNotEquals("precondition: raw compare would have dropped this CUD (the bug)", storedCUD, loadOnlyCUD);

        List<TenantConfig> dropped = new ArrayList<>();
        TenantConfig[] filtered = MultitenancyHelper.filterTenantConfigsForLoadOnlyCUD(
                new TenantConfig[]{tenantConfigForCUD(storedCUD)}, loadOnlyCUD, dropped);

        assertTrue(containsCUD(filtered, storedCUD));
        assertTrue(dropped.isEmpty());
    }

    // Case-only difference must also be retained.
    @Test
    public void cudDifferingOnlyByCaseIsRetained() {
        String loadOnlyCUD = "example.com";
        String storedCUD = "Example.Com";

        List<TenantConfig> dropped = new ArrayList<>();
        TenantConfig[] filtered = MultitenancyHelper.filterTenantConfigsForLoadOnlyCUD(
                new TenantConfig[]{tenantConfigForCUD(storedCUD)}, loadOnlyCUD, dropped);

        // getConnectionUriDomain() lower-cases, so the retained entry reads back as "example.com".
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

    // A genuinely different CUD is dropped and reported so a live-CUD wipe can be logged loudly.
    @Test
    public void unrelatedCUDIsDroppedAndReported() {
        String loadOnlyCUD = "example.com";
        TenantConfig defaultTenant = tenantConfigForCUD(TenantIdentifier.DEFAULT_CONNECTION_URI);
        TenantConfig kept = tenantConfigForCUD("example.com:8080");
        TenantConfig droppedCUD = tenantConfigForCUD("other.com");

        List<TenantConfig> dropped = new ArrayList<>();
        TenantConfig[] filtered = MultitenancyHelper.filterTenantConfigsForLoadOnlyCUD(
                new TenantConfig[]{defaultTenant, kept, droppedCUD}, loadOnlyCUD, dropped);

        assertTrue(containsCUD(filtered, TenantIdentifier.DEFAULT_CONNECTION_URI));
        assertTrue(containsCUD(filtered, "example.com:8080"));
        assertFalse(containsCUD(filtered, "other.com"));

        assertEquals(1, dropped.size());
        assertEquals("other.com", dropped.get(0).tenantIdentifier.getConnectionUriDomain());
    }

    // The normalizer used for comparison must be tolerant of the empty (default) string and must strip
    // the port/case the config normalizer strips.
    @Test
    public void normalizeConnectionUriDomainForComparison() {
        assertEquals(TenantIdentifier.DEFAULT_CONNECTION_URI,
                MultitenancyHelper.normalizeConnectionUriDomainForComparison(
                        TenantIdentifier.DEFAULT_CONNECTION_URI));
        assertEquals(TenantIdentifier.DEFAULT_CONNECTION_URI,
                MultitenancyHelper.normalizeConnectionUriDomainForComparison(null));
        assertEquals("example.com",
                MultitenancyHelper.normalizeConnectionUriDomainForComparison("Example.Com:9000"));
        assertEquals("127.0.0.1",
                MultitenancyHelper.normalizeConnectionUriDomainForComparison("127.0.0.1:3567"));
    }
}
