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

package io.supertokens.multitenancy;

import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.ResourceDistributor;
import io.supertokens.config.Config;
import io.supertokens.cronjobs.Cronjobs;
import io.supertokens.featureflag.FeatureFlag;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.jwt.exceptions.UnsupportedJWTSigningAlgorithmException;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.multitenancy.exception.CannotModifyBaseConfigException;
import io.supertokens.output.Logging;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.*;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.saml.SAMLCertificate;
import io.supertokens.pluginInterface.opentelemetry.WithinOtelSpan;
import io.supertokens.session.refreshToken.RefreshTokenKey;
import io.supertokens.signingkeys.AccessTokenSigningKey;
import io.supertokens.signingkeys.JWTSigningKey;
import io.supertokens.signingkeys.SigningKeys;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.thirdparty.InvalidProviderConfigException;
import io.supertokens.utils.SemVer;
import io.supertokens.webserver.Utils;
import jakarta.servlet.ServletException;

import java.io.IOException;
import java.util.*;

import static io.supertokens.multitenancy.Multitenancy.getTenantInfo;

public class MultitenancyHelper extends ResourceDistributor.SingletonResource {

    public static final String RESOURCE_KEY = "io.supertokens.multitenancy.Multitenancy";
    private Main main;
    private volatile TenantConfig[] tenantConfigs;

    // when the core has `supertokens_saas_load_only_cud` set, the tenantConfigs array will be filtered
    // based on the config value. However, we need to keep all the list of CUDs from the db to be able
    // to check if the CUD is present in the DB or not, while processing the requests.
    private volatile Set<String> dangerous_allCUDsFromDb = new HashSet<>();

    private MultitenancyHelper(Main main) throws StorageQueryException {
        this.main = main;
        TenantConfig[] allTenantsFromDb = getAllTenantsFromDb();
        this.tenantConfigs = this.getFilteredTenantConfigs(allTenantsFromDb);
        Set<String> cuds = new HashSet<>();
        for (TenantConfig config : allTenantsFromDb) {
            cuds.add(config.tenantIdentifier.getConnectionUriDomain());
        }
        this.dangerous_allCUDsFromDb = cuds;
    }

    public static MultitenancyHelper getInstance(Main main) {
        try {
            return (MultitenancyHelper) main.getResourceDistributor()
                    .getResource(new TenantIdentifier(null, null, null), RESOURCE_KEY);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void init(Main main) throws StorageQueryException, IOException {
        main.getResourceDistributor()
                .setResource(new TenantIdentifier(null, null, null), RESOURCE_KEY, new MultitenancyHelper(main));
        if (getTenantInfo(main, new TenantIdentifier(null, null, null)) == null) {
            // we create the default base tenant
            try {
                Multitenancy.addNewOrUpdateAppOrTenant(main,
                        new TenantConfig(
                                new TenantIdentifier(null, null, null),
                                new EmailPasswordConfig(true),
                                new ThirdPartyConfig(true, null),
                                new PasswordlessConfig(true),
                                null, null, new JsonObject()), false, false, false);
                // Not force reloading all resources here (the last boolean in the function above)
                // because the ucl for the FeatureFlag is not yet loaded and results in an empty
                // instance of eeFeatureFlag. This is applicable only when the core is starting on
                // an empty database as no tenants are loaded from the db yet.
            } catch (CannotModifyBaseConfigException | BadPermissionException | FeatureNotEnabledException |
                     InvalidConfigException | InvalidProviderConfigException | TenantOrAppNotFoundException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private TenantConfig[] getAllTenantsFromDb() throws StorageQueryException {
        if (StorageLayer.getBaseStorage(main).getType() != STORAGE_TYPE.SQL) {
            return new TenantConfig[]{
                    new TenantConfig(
                            TenantIdentifier.BASE_TENANT,
                            new EmailPasswordConfig(true),
                            new ThirdPartyConfig(true, null),
                            new PasswordlessConfig(true),
                            null, null, new JsonObject()
                    )
            };
        }
        return StorageLayer.getMultitenancyStorage(main).getAllTenants();
    }

    @WithinOtelSpan
    public List<TenantIdentifier> refreshTenantsInCoreBasedOnChangesInCoreConfigOrIfTenantListChanged(
            boolean reloadAllResources) {
        try {
            return main.getResourceDistributor().withResourceDistributorLock(() -> {
                try {
                    TenantConfig[] tenantsFromDb = getAllTenantsFromDb();
                    TenantConfig[] filteredTenantsFromDb = this.getFilteredTenantConfigs(tenantsFromDb);

                    Map<ResourceDistributor.KeyClass, JsonObject> normalizedTenantsFromDb =
                            Config.getNormalisedConfigsForAllTenants(
                                    filteredTenantsFromDb, Config.getBaseConfigAsJsonObject(main));

                    Map<ResourceDistributor.KeyClass, JsonObject> normalizedTenantsFromMemory =
                            Config.getNormalisedConfigsForAllTenants(
                                    this.tenantConfigs, Config.getBaseConfigAsJsonObject(main));

                    List<TenantIdentifier> tenantsThatChanged = new ArrayList<>();

                    for (Map.Entry<ResourceDistributor.KeyClass, JsonObject> entry :
                            normalizedTenantsFromMemory.entrySet()) {
                        JsonObject tenantConfigFromMemory = entry.getValue();
                        JsonObject tenantConfigFromDb = normalizedTenantsFromDb.get(entry.getKey());

                        if (!tenantConfigFromMemory.equals(tenantConfigFromDb)) {
                            tenantsThatChanged.add(entry.getKey().getTenantIdentifier());
                        }
                    }

                    boolean sameNumberOfTenants =
                            filteredTenantsFromDb.length == this.tenantConfigs.length;

                    Set<String> cuds = new HashSet<>();
                    for (TenantConfig tenant : tenantsFromDb) {
                        cuds.add(tenant.tenantIdentifier.getConnectionUriDomain());
                    }
                    this.dangerous_allCUDsFromDb = cuds;
                    this.tenantConfigs = filteredTenantsFromDb;
                    if (tenantsThatChanged.size() == 0 && sameNumberOfTenants) {
                        return tenantsThatChanged;
                    }

                    ProcessState.getInstance(main)
                            .addState(ProcessState.PROCESS_STATE.TENANTS_CHANGED_DURING_REFRESH_FROM_DB, null);

                    // this order is important. For example, storageLayer depends on config, and cronjobs depends on
                    // storageLayer
                    if (reloadAllResources) {
                        forceReloadAllResources(tenantsThatChanged);
                    } else {
                        // we do these two here cause they don't really depend on any table in the db, and these
                        // two are required for allocating any further resource for this tenant
                        loadConfig(tenantsThatChanged);
                        loadStorageLayer();
                    }
                    return tenantsThatChanged;
                } catch (Exception e) {
                    Logging.error(main, TenantIdentifier.BASE_TENANT, e.getMessage(), false, e);
                    return new ArrayList<>();
                }
            });
        } catch (ResourceDistributor.FuncException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Fast path for addNewOrUpdateAppOrTenant: instead of doing the full 2 × getNormalisedConfigsForAllTenants
     * diff, it only normalizes configs for the affected tenants (the changed tenant + its children
     * in the inheritance hierarchy). Detects actual changes to avoid replacing resources when
     * configs haven't changed (preserving object identity for tests that check assertEquals).
     */
    public List<TenantIdentifier> refreshAfterKnownTenantChange(TenantIdentifier changedTenant) {
        try {
            return main.getResourceDistributor().withResourceDistributorLock(() -> {
                try {
                    TenantConfig[] tenantsFromDb = getAllTenantsFromDb();
                    TenantConfig[] filteredTenantsFromDb = this.getFilteredTenantConfigs(tenantsFromDb);

                    boolean sameNumberOfTenants =
                            filteredTenantsFromDb.length == this.tenantConfigs.length;

                    // Build set of existing tenant IDs for new-tenant detection
                    Set<TenantIdentifier> existingTenantIds = new HashSet<>();
                    for (TenantConfig tc : this.tenantConfigs) {
                        existingTenantIds.add(tc.tenantIdentifier);
                    }

                    // Compute the affected set: the changed tenant + children that inherit from it
                    List<TenantIdentifier> affectedTenants = new ArrayList<>();
                    affectedTenants.add(changedTenant);

                    // If the changed tenant is a parent (app or CUD level), child tenants
                    // inherit its config and may also need reloading
                    if (changedTenant.getTenantId().equals(TenantIdentifier.DEFAULT_TENANT_ID)) {
                        for (TenantConfig tc : filteredTenantsFromDb) {
                            TenantIdentifier ti = tc.tenantIdentifier;
                            if (ti.equals(changedTenant)) continue;

                            boolean isChild;
                            if (changedTenant.getAppId().equals(TenantIdentifier.DEFAULT_APP_ID)) {
                                // CUD level parent: affects all tenants in this CUD
                                isChild = ti.getConnectionUriDomain()
                                        .equals(changedTenant.getConnectionUriDomain());
                            } else {
                                // App level parent: affects all tenants in this app
                                isChild = ti.getConnectionUriDomain()
                                        .equals(changedTenant.getConnectionUriDomain())
                                        && ti.getAppId().equals(changedTenant.getAppId());
                            }
                            if (isChild) {
                                affectedTenants.add(ti);
                            }
                        }
                    }

                    // For each affected tenant that already exists, check if its normalized
                    // config actually changed compared to the in-memory state.
                    // New tenants are NOT added to tenantsThatChanged — they are handled by
                    // the full loadConfig + loadStorageLayer calls below (which create resources
                    // for all tenants in tenantConfigs). This matches the behavior of the
                    // original refreshTenantsInCoreBasedOnChangesInCoreConfigOrIfTenantListChanged.
                    JsonObject baseConfig = Config.getBaseConfigAsJsonObject(main);
                    List<TenantIdentifier> tenantsThatChanged = new ArrayList<>();
                    for (TenantIdentifier affected : affectedTenants) {
                        if (existingTenantIds.contains(affected)) {
                            JsonObject normFromDb = Config.getNormalisedConfigForTenant(
                                    affected, filteredTenantsFromDb, baseConfig);
                            JsonObject normFromMemory = Config.getNormalisedConfigForTenant(
                                    affected, this.tenantConfigs, baseConfig);
                            if (!normFromDb.equals(normFromMemory)) {
                                tenantsThatChanged.add(affected);
                            }
                        }
                        // New tenants (not in existingTenantIds) are skipped here;
                        // sameNumberOfTenants will be false, preventing early return.
                    }

                    this.dangerous_allCUDsFromDb.clear();
                    for (TenantConfig tenant : tenantsFromDb) {
                        this.dangerous_allCUDsFromDb.add(tenant.tenantIdentifier.getConnectionUriDomain());
                    }
                    this.tenantConfigs = filteredTenantsFromDb;

                    if (tenantsThatChanged.isEmpty() && sameNumberOfTenants) {
                        return tenantsThatChanged;
                    }

                    ProcessState.getInstance(main)
                            .addState(ProcessState.PROCESS_STATE.TENANTS_CHANGED_DURING_REFRESH_FROM_DB, null);

                    // Use the same full loading as the original code path.
                    // Config + storage are loaded here; the finally block only needs
                    // to load feature flags, signing keys, and refresh cronjobs.
                    loadConfig(tenantsThatChanged);
                    loadStorageLayer();

                    return tenantsThatChanged;
                } catch (Exception e) {
                    Logging.error(main, TenantIdentifier.BASE_TENANT, e.getMessage(), false, e);
                    return new ArrayList<>();
                }
            });
        } catch (ResourceDistributor.FuncException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Called from addNewOrUpdateAppOrTenant's finally block (when forceReloadResources=true).
     * Config + storage are already loaded by refreshAfterKnownTenantChange, so this only
     * loads feature flags, signing keys, and refreshes cronjobs for the changed tenants.
     */
    public void incrementalReloadResources(List<TenantIdentifier> tenantsThatChanged) {
        try {
            main.getResourceDistributor().withResourceDistributorLock(() -> {
                try {
                    // Config and storage are already loaded by refreshAfterKnownTenantChange.
                    // Only load the remaining resources here.
                    loadFeatureFlag(tenantsThatChanged);
                    loadSigningKeys(tenantsThatChanged);
                    refreshCronjobs();
                } catch (Exception e) {
                    Logging.error(main, TenantIdentifier.BASE_TENANT, e.getMessage(), false, e);
                }
                return null;
            });
        } catch (ResourceDistributor.FuncException e) {
            throw new IllegalStateException(e);
        }
    }

    public void forceReloadAllResources(List<TenantIdentifier> tenantsThatChanged) {
        try {
            main.getResourceDistributor().withResourceDistributorLock(() -> {
                try {
                    loadConfig(tenantsThatChanged);
                    loadStorageLayer();
                    loadFeatureFlag(tenantsThatChanged);
                    loadSigningKeys(tenantsThatChanged);
                    refreshCronjobs();
                } catch (Exception e) {
                    Logging.error(main, TenantIdentifier.BASE_TENANT, e.getMessage(), false, e);
                }
                return null;
            });
        } catch (ResourceDistributor.FuncException e) {
            throw new IllegalStateException(e);
        }
    }

    public void loadConfig(List<TenantIdentifier> tenantsThatChanged) throws IOException, InvalidConfigException {
        Config.loadAllTenantConfig(main, this.tenantConfigs, tenantsThatChanged);
    }

    public void loadConfigIncremental(List<TenantIdentifier> tenantsThatChanged)
            throws IOException, InvalidConfigException {
        Config.loadConfigForChangedTenants(main, this.tenantConfigs, tenantsThatChanged);
    }

    public void loadStorageLayer() throws IOException, InvalidConfigException {
        StorageLayer.loadAllTenantStorage(main, this.tenantConfigs);
    }

    public void loadStorageLayerIncremental(List<TenantIdentifier> tenantsThatChanged)
            throws IOException, InvalidConfigException {
        StorageLayer.loadStorageForChangedTenants(main, this.tenantConfigs, tenantsThatChanged);
    }

    public void loadFeatureFlag(List<TenantIdentifier> tenantsThatChanged) {
        List<AppIdentifier> apps = new ArrayList<>();
        Set<AppIdentifier> appsSet = new HashSet<>();
        for (TenantConfig t : this.tenantConfigs) {
            if (appsSet.contains(t.tenantIdentifier.toAppIdentifier())) {
                continue;
            }
            apps.add(t.tenantIdentifier.toAppIdentifier());
            appsSet.add(t.tenantIdentifier.toAppIdentifier());
        }
        FeatureFlag.loadForAllTenants(main, apps, tenantsThatChanged);
    }

    public void loadSigningKeys(List<TenantIdentifier> tenantsThatChanged)
            throws UnsupportedJWTSigningAlgorithmException {
        List<AppIdentifier> apps = new ArrayList<>();
        Set<AppIdentifier> appsSet = new HashSet<>();
        for (TenantConfig t : this.tenantConfigs) {
            if (appsSet.contains(t.tenantIdentifier.toAppIdentifier())) {
                continue;
            }
            apps.add(t.tenantIdentifier.toAppIdentifier());
            appsSet.add(t.tenantIdentifier.toAppIdentifier());
        }
        AccessTokenSigningKey.loadForAllTenants(main, apps, tenantsThatChanged);
        RefreshTokenKey.loadForAllTenants(main, apps, tenantsThatChanged);
        SAMLCertificate.loadForAllTenants(main, apps, tenantsThatChanged);
        JWTSigningKey.loadForAllTenants(main, apps, tenantsThatChanged);
        SigningKeys.loadForAllTenants(main, apps, tenantsThatChanged);
    }

    public void refreshCronjobs() {
        List<List<TenantIdentifier>> list = StorageLayer.getTenantsWithUniqueUserPoolId(main);
        Cronjobs.getInstance(main).setTenantsInfo(list);
    }

    public TenantConfig[] getAllTenants() {
        // Capture the volatile reference first so the loop operates on a stable snapshot,
        // even if a refresh reassigns tenantConfigs mid-iteration.
        TenantConfig[] snapshot = this.tenantConfigs;
        TenantConfig[] result = new TenantConfig[snapshot.length];
        for (int i = 0; i < snapshot.length; i++) {
            result[i] = new TenantConfig(snapshot[i]);
        }
        return result;
    }

    private TenantConfig[] getFilteredTenantConfigs(TenantConfig[] inputTenantConfigs) {
        String loadOnlyCUD = Config.getBaseConfig(main).getSuperTokensLoadOnlyCUD();

        if (loadOnlyCUD == null) {
            return inputTenantConfigs;
        }

        List<TenantConfig> droppedTenantConfigs = new ArrayList<>();
        TenantConfig[] filtered = filterTenantConfigsForLoadOnlyCUD(inputTenantConfigs, loadOnlyCUD,
                droppedTenantConfigs);

        for (TenantConfig dropped : droppedTenantConfigs) {
            // Only a CUD that is *currently loaded* (has live resources in the distributor) is worth
            // shouting about: replaceResourcesWithResourceKey is about to wipe its resources, which would
            // otherwise surface as a log-free TenantOrAppNotFoundException on every request for that CUD.
            if (isConnectionUriDomainCurrentlyLoaded(dropped.tenantIdentifier.getConnectionUriDomain())) {
                Logging.error(main, dropped.tenantIdentifier,
                        "supertokens_saas_load_only_cud is set to '" + loadOnlyCUD + "', which excludes the "
                                + "previously-loaded connectionUriDomain '"
                                + dropped.tenantIdentifier.getConnectionUriDomain()
                                + "'. Its resources are being removed and requests for it will fail. Check that "
                                + "supertokens_saas_load_only_cud matches the stored connectionUriDomain.", true);
            }
        }

        return filtered;
    }

    /**
     * Filters {@code inputTenantConfigs} down to the CUDs that a core configured with
     * {@code supertokens_saas_load_only_cud} should load: the default CUD is always kept, and a CUD is
     * kept when it matches {@code loadOnlyCUD}. The match is done on the connectionUriDomain
     * <b>normalized the same way</b> as {@code loadOnlyCUD} (see
     * {@link #normalizeConnectionUriDomainForComparison}), so a stored connectionUriDomain and the
     * configured value that differ only by case / scheme / port / trailing-slash still match instead of
     * silently dropping a CUD the instance is meant to serve. Any excluded CUD is added to
     * {@code droppedTenantConfigs} (when non-null) so the caller can flag a drop of a live CUD.
     * <p>
     * Package-private and static (no {@code main}) so it can be unit-tested directly.
     */
    static TenantConfig[] filterTenantConfigsForLoadOnlyCUD(TenantConfig[] inputTenantConfigs, String loadOnlyCUD,
                                                            List<TenantConfig> droppedTenantConfigs) {
        String normalizedLoadOnlyCUD = normalizeConnectionUriDomainForComparison(loadOnlyCUD);

        List<TenantConfig> filtered = new ArrayList<>();
        for (TenantConfig tenantConfig : inputTenantConfigs) {
            String connectionUriDomain = tenantConfig.tenantIdentifier.getConnectionUriDomain();
            if (connectionUriDomain.equals(TenantIdentifier.DEFAULT_CONNECTION_URI)
                    || normalizeConnectionUriDomainForComparison(connectionUriDomain).equals(normalizedLoadOnlyCUD)) {
                filtered.add(tenantConfig);
            } else if (droppedTenantConfigs != null) {
                droppedTenantConfigs.add(tenantConfig);
            }
        }
        return filtered.toArray(new TenantConfig[0]);
    }

    /**
     * Normalizes a connectionUriDomain with the same normalizer the tenant-create / request-routing
     * path uses ({@link Utils#normalizeAndValidateConnectionUriDomain}), so comparisons are
     * normalized-to-normalized. The configured {@code supertokens_saas_load_only_cud} is already
     * normalized on config load (see {@code CoreConfig}), but a DB-sourced connectionUriDomain read via
     * {@link TenantIdentifier#getConnectionUriDomain()} is only trimmed and lower-cased, so this closes
     * the case/port gap between the two operands. (The non-throwing variant only strips case and port;
     * a scheme or trailing slash hits its internal path check, is caught, and is returned unchanged, but
     * a stored connectionUriDomain can never carry either because the create path normalizes with the
     * throwing variant.)
     */
    static String normalizeConnectionUriDomainForComparison(String connectionUriDomain) {
        if (connectionUriDomain == null
                || connectionUriDomain.equals(TenantIdentifier.DEFAULT_CONNECTION_URI)) {
            return TenantIdentifier.DEFAULT_CONNECTION_URI;
        }
        try {
            return Utils.normalizeAndValidateConnectionUriDomain(connectionUriDomain, false);
        } catch (ServletException e) {
            // The non-throwing variant only throws for an empty string, which is handled above. Fall back
            // to the same trim+lowercase that TenantIdentifier.getConnectionUriDomain() applies.
            return connectionUriDomain.trim().toLowerCase();
        }
    }

    private boolean isConnectionUriDomainCurrentlyLoaded(String connectionUriDomain) {
        TenantConfig[] currentlyLoaded = this.tenantConfigs;
        if (currentlyLoaded == null) {
            return false;
        }
        for (TenantConfig tenantConfig : currentlyLoaded) {
            if (tenantConfig.tenantIdentifier.getConnectionUriDomain().equals(connectionUriDomain)) {
                return true;
            }
        }
        return false;
    }

    public boolean isConnectionUriDomainPresentInDb(String cud) {
        return this.dangerous_allCUDsFromDb.contains(cud);
    }

    public static boolean isEmailPasswordEnabled(TenantConfig tenantConfig, SemVer version) {
        if (version.greaterThanOrEqualTo(SemVer.v5_1)) {
            return true;
        } else if (version.greaterThanOrEqualTo(SemVer.v5_0)) {
            return tenantConfig.emailPasswordConfig.isEnabledIn5_0(tenantConfig.firstFactors);
        } else {
            return tenantConfig.emailPasswordConfig.isEnabledInLesserThanOrEqualTo4_0(tenantConfig.firstFactors);
        }
    }

    public static boolean isThirdPartyEnabled(TenantConfig tenantConfig, SemVer version) {
        if (version.greaterThanOrEqualTo(SemVer.v5_1)) {
            return true;
        } else if (version.greaterThanOrEqualTo(SemVer.v5_0)) {
            return tenantConfig.thirdPartyConfig.isEnabledIn5_0(tenantConfig.firstFactors);
        } else {
            return tenantConfig.thirdPartyConfig.isEnabledInLesserThanOrEqualTo4_0(tenantConfig.firstFactors);
        }
    }

    public static boolean isPasswordlessEnabled(TenantConfig tenantConfig, SemVer version) {
        if (version.greaterThanOrEqualTo(SemVer.v5_1)) {
            return true;
        } else if (version.greaterThanOrEqualTo(SemVer.v5_0)) {
            return tenantConfig.passwordlessConfig.isEnabledIn5_0(tenantConfig.firstFactors);
        } else {
            return tenantConfig.passwordlessConfig.isEnabledInLesserThanOrEqualTo4_0(tenantConfig.firstFactors);
        }
    }
}
