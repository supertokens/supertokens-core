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

package io.supertokens.useridmapping;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.ResourceDistributor;
import io.supertokens.auditlog.UnauditedTransaction;
import io.supertokens.config.Config;
import io.supertokens.output.Logging;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantConfig;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.DuplicateClientTypeException;
import io.supertokens.pluginInterface.multitenancy.exceptions.DuplicateThirdPartyIdException;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.multitenancy.sqlStorage.MultitenancySQLStorage;
import io.supertokens.pluginInterface.sqlStorage.TransactionConnection;
import io.supertokens.pluginInterface.useridmapping.exception.UnknownSuperTokensUserIdException;
import io.supertokens.pluginInterface.useridmapping.exception.UserIdMappingAlreadyExistsException;
import io.supertokens.pluginInterface.useridmapping.sqlStorage.UserIdMappingSQLStorage;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.webserver.WebserverAPI;

import jakarta.servlet.ServletException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * disable_user_id_mapping is an optimisation: an app that has no user id mappings can skip reading them. This class
 * keeps that true.
 *
 * <ul>
 *     <li>Creating a mapping and turning the flag on exclude each other through a lock in the database the mapping
 *     goes to (see {@link #createWhileUserIdMappingIsEnabled} and {@link #overwriteTenantConfigDisablingUserIdMapping}).
 *     Mapping creation reads the flag from the stored config under that lock, so a core whose config is stale
 *     can't create a mapping after the flag is turned on through the config API. A flag set in config.yaml / env is
 *     each core's own value until it restarts.</li>
 *     <li>Reads are skipped only for apps whose databases were checked and had no mappings ({@link #isReadSkipped}).
 *     A core checks that after it loads the app's databases; a reload that keeps them as they were keeps the
 *     result. If it finds mappings anyway (a restored database, a flag set in config.yaml), it keeps reading them
 *     and logs an error: it fails closed. An app served by a
 *     storage that outlived the storage-init timeout is checked once that storage is up, and reads mappings until
 *     then: querying it earlier would wait for its pool, which would stall startup again.</li>
 *     <li>disable_user_id_mapping_force turns the check off: the flag can be turned on with mappings present, and
 *     those mappings are ignored. Turning force off is checked like turning the flag on.</li>
 * </ul>
 */
public class UserIdMappingOptOut extends ResourceDistributor.SingletonResource {

    private static final String RESOURCE_KEY = "io.supertokens.useridmapping.UserIdMappingOptOut";
    private static final String CONFIG_KEY = "disable_user_id_mapping";

    // Apps with disable_user_id_mapping on (and not forced) whose databases this core checked and found without
    // user id mappings. Only these skip mapping reads.
    private final Set<AppIdentifier> appsCheckedWithoutMappings = ConcurrentHashMap.newKeySet();

    // Storages whose initialisation outlived the storage-init timeout, until it finishes, and the apps whose check
    // waits for one of them.
    private final Map<Storage, CompletableFuture<Void>> storagesStillInitialising = new ConcurrentHashMap<>();
    private final Set<AppIdentifier> appsAwaitingStorageInit = ConcurrentHashMap.newKeySet();

    private UserIdMappingOptOut() {
    }

    private static UserIdMappingOptOut getInstance(Main main) {
        return (UserIdMappingOptOut) main.getResourceDistributor()
                .setResource(TenantIdentifier.BASE_TENANT, RESOURCE_KEY, new UserIdMappingOptOut());
    }

    public static boolean isForced(Main main, AppIdentifier appIdentifier) {
        try {
            return Config.getConfig(appIdentifier.getAsPublicTenantIdentifier(), main)
                    .isUserIdMappingDisabledByForce();
        } catch (TenantOrAppNotFoundException e) {
            return false;
        }
    }

    // Whether the core and the storage may skip user id mapping reads for the app.
    public static boolean isReadSkipped(Main main, AppIdentifier appIdentifier) {
        if (!UserIdMapping.isUserIdMappingDisabled(main, appIdentifier)) {
            return false;
        }
        return isForced(main, appIdentifier) || getInstance(main).appsCheckedWithoutMappings.contains(appIdentifier);
    }

    // Called before storages are (re)loaded: until they are checked again, the apps read mappings.
    public static void forgetCheck(Main main, Collection<AppIdentifier> appIdentifiers) {
        getInstance(main).appsCheckedWithoutMappings.removeAll(appIdentifiers);
    }

    // Called before a full storage reload: apps whose storages it keeps as they were keep their check.
    public static void forgetChecksExcept(Main main, Set<AppIdentifier> appsWithUnchangedStorages) {
        getInstance(main).appsCheckedWithoutMappings.retainAll(appsWithUnchangedStorages);
    }

    // Called once the app's storages are initialised: checks each app with the flag on (and not forced) for user id
    // mappings. Only apps without any skip mapping reads; for the others the core keeps reading and logs an error.
    public static void checkApps(Main main, Collection<AppIdentifier> appIdentifiers) {
        checkApps(main, appIdentifiers, true);
    }

    // Called with the storages a load gave up waiting for. Apps they serve are checked once they finish initialising.
    public static void addStoragesStillInitialising(Main main, Map<Storage, CompletableFuture<Void>> storages) {
        UserIdMappingOptOut instance = getInstance(main);
        for (Map.Entry<Storage, CompletableFuture<Void>> entry : storages.entrySet()) {
            Storage storage = entry.getKey();
            CompletableFuture<Void> initialised = entry.getValue();
            instance.storagesStillInitialising.put(storage, initialised);
            initialised.whenComplete((ignored, initFailure) -> {
                instance.storagesStillInitialising.remove(storage, initialised);
                try {
                    checkApps(main, new ArrayList<>(instance.appsAwaitingStorageInit));
                    StorageLayer.refreshAppsWithUserIdMappingDisabled(main);
                } catch (RuntimeException e) {
                    Logging.error(main, TenantIdentifier.BASE_TENANT,
                            "Could not check apps for user id mappings after their storage initialised, so they " +
                                    "keep reading them: " + e.getMessage(), false, e);
                }
            });
        }
    }

    public static boolean isServedByAStorageStillInitialising(Main main, AppIdentifier appIdentifier) {
        UserIdMappingOptOut instance = getInstance(main);
        if (instance.storagesStillInitialising.isEmpty()) {
            return false;
        }
        for (Storage storage : StorageLayer.getStorageInstancesForApp(main, appIdentifier)) {
            if (instance.storagesStillInitialising.containsKey(storage)) {
                return true;
            }
        }
        return false;
    }

    // After a user id mapping was removed from an app that has the flag on but reads mappings because it had some:
    // checks it again, so it skips mapping reads as soon as the last one is gone. Other cores notice at their next
    // storage load, and read mappings until then.
    public static void checkAgainAfterMappingRemoval(Main main, AppIdentifier appIdentifier) {
        if (!UserIdMapping.isUserIdMappingDisabled(main, appIdentifier) || isForced(main, appIdentifier)
                || getInstance(main).appsCheckedWithoutMappings.contains(appIdentifier)) {
            return;
        }
        checkApps(main, List.of(appIdentifier), false);
        if (getInstance(main).appsCheckedWithoutMappings.contains(appIdentifier)) {
            StorageLayer.refreshAppsWithUserIdMappingDisabled(main);
        }
    }

    private static void checkApps(Main main, Collection<AppIdentifier> appIdentifiers, boolean logIfMappingsFound) {
        UserIdMappingOptOut instance = getInstance(main);
        for (AppIdentifier appIdentifier : appIdentifiers) {
            if (!UserIdMapping.isUserIdMappingDisabled(main, appIdentifier) || isForced(main, appIdentifier)) {
                instance.appsCheckedWithoutMappings.remove(appIdentifier);
                instance.appsAwaitingStorageInit.remove(appIdentifier);
                continue;
            }
            // Kept by a storage reload that left the app's storages as they were (see StorageLayer), or already
            // checked by a storage that finished initialising.
            if (instance.appsCheckedWithoutMappings.contains(appIdentifier)) {
                instance.appsAwaitingStorageInit.remove(appIdentifier);
                continue;
            }
            // Marked as awaiting before looking, so a storage finishing in between still finds the app.
            instance.appsAwaitingStorageInit.add(appIdentifier);
            if (isServedByAStorageStillInitialising(main, appIdentifier)) {
                instance.appsCheckedWithoutMappings.remove(appIdentifier);
                // The storage may have finished, and its callback checked the app, before the line above: look
                // again, so that result isn't lost.
                if (isServedByAStorageStillInitialising(main, appIdentifier)) {
                    continue;
                }
            }
            instance.appsAwaitingStorageInit.remove(appIdentifier);
            try {
                if (UserIdMapping.hasUserIdMappings(main, appIdentifier)) {
                    instance.appsCheckedWithoutMappings.remove(appIdentifier);
                    if (!logIfMappingsFound) {
                        continue;
                    }
                    Logging.error(main, appIdentifier.getAsPublicTenantIdentifier(),
                            "disable_user_id_mapping is on, but the app's database has user id mappings, so the " +
                                    "core keeps reading them. Remove the mappings, or set " +
                                    "disable_user_id_mapping_force to true to ignore them.", false);
                } else {
                    instance.appsCheckedWithoutMappings.add(appIdentifier);
                }
            } catch (StorageQueryException | TenantOrAppNotFoundException e) {
                instance.appsCheckedWithoutMappings.remove(appIdentifier);
                Logging.error(main, appIdentifier.getAsPublicTenantIdentifier(),
                        "Could not check the app for user id mappings, so the core keeps reading them: " +
                                e.getMessage(), false, e);
            }
        }
    }

    public interface MappingCreation<T> {
        T create(TransactionConnection con)
                throws StorageQueryException, UnknownSuperTokensUserIdException, UserIdMappingAlreadyExistsException;
    }

    /**
     * Runs {@code creation} in a transaction on {@code storage}, the database the mappings go to, after taking the
     * shared mapping-creation lock and reading disable_user_id_mapping from the stored config. Turning the flag on
     * takes the same lock exclusively before it checks for mappings, so either the mapping is committed first and
     * that check sees it, or this reads the flag as on and refuses. For the bulk import proxy storage the
     * transaction (and the lock) lasts until the batch is committed; on the claim connection that is the end of the
     * chunk, so the bulk import worker imports the claim database's users last (see
     * {@link #overwriteTenantConfigDisablingUserIdMapping}).
     */
    @UnauditedTransaction(justification = "User id mapping inserts are not audit events (the auto-commit insert "
            + "this replaces was unaudited); the transaction only adds the creation lock and the config read.")
    public static <T> T createWhileUserIdMappingIsEnabled(Main main, AppIdentifier appIdentifier, Storage storage,
                                                          MappingCreation<T> creation)
            throws StorageQueryException, ServletException, UnknownSuperTokensUserIdException,
            UserIdMappingAlreadyExistsException {
        UserIdMappingSQLStorage mappingStorage = (UserIdMappingSQLStorage) storage;
        try {
            return mappingStorage.startTransaction(con -> {
                mappingStorage.lockAppForUserIdMappingCreation_Transaction(con, appIdentifier);
                if (isDisabledInStoredConfig(main, appIdentifier, storage, con)) {
                    throw new StorageTransactionLogicException(new ServletException(
                            new WebserverAPI.BadRequestException(UserIdMapping.USER_ID_MAPPING_DISABLED_MESSAGE)));
                }
                T result;
                try {
                    result = creation.create(con);
                } catch (UnknownSuperTokensUserIdException | UserIdMappingAlreadyExistsException e) {
                    throw new StorageTransactionLogicException(e);
                }
                mappingStorage.commitTransaction(con);
                return result;
            });
        } catch (StorageTransactionLogicException e) {
            if (e.actualException instanceof ServletException servletException) {
                throw servletException;
            }
            if (e.actualException instanceof UnknownSuperTokensUserIdException unknown) {
                throw unknown;
            }
            if (e.actualException instanceof UserIdMappingAlreadyExistsException exists) {
                throw exists;
            }
            if (e.actualException instanceof StorageQueryException storageQueryException) {
                throw storageQueryException;
            }
            throw new StorageQueryException(e.actualException);
        }
    }

    // The flag as the stored config sets it for the app, merged like Config.getNormalisedConfigForTenant: the app's
    // public tenant, then its connection uri domain, then this core's config.yaml. The first level that has the key
    // wins, and an explicit null there means the default (off), as it does in the loaded config. The base app only
    // uses config.yaml, which has no row to lock.
    private static boolean isDisabledInStoredConfig(Main main, AppIdentifier appIdentifier, Storage storage,
                                                    TransactionConnection con)
            throws StorageQueryException, StorageTransactionLogicException {
        TenantIdentifier appTenant = appIdentifier.getAsPublicTenantIdentifier();
        boolean fromConfigYaml = Config.getBaseConfig(main).isUserIdMappingDisabled();
        if (appTenant.equals(TenantIdentifier.BASE_TENANT)) {
            return fromConfigYaml;
        }
        TenantIdentifier cudTenant = new TenantIdentifier(appIdentifier.getConnectionUriDomain(), null, null);
        List<TenantIdentifier> chain = appTenant.equals(cudTenant) ? List.of(appTenant) : List.of(appTenant,
                cudTenant);
        for (TenantIdentifier tenantIdentifier : chain) {
            JsonObject coreConfig = readStoredCoreConfig(main, storage, con, tenantIdentifier);
            if (coreConfig != null && coreConfig.has(CONFIG_KEY)) {
                JsonElement value = coreConfig.get(CONFIG_KEY);
                return !value.isJsonNull() && value.getAsBoolean();
            }
        }
        return fromConfigYaml;
    }

    // Reads on the mapping transaction's connection when the tenant configs live in the same database, so there is
    // only one connection. Otherwise reads the committed row from the base storage, while the lock is held.
    @UnauditedTransaction(justification = "Read-only: reads one tenant's stored core config.")
    private static JsonObject readStoredCoreConfig(Main main, Storage storage, TransactionConnection con,
                                                   TenantIdentifier tenantIdentifier)
            throws StorageQueryException, StorageTransactionLogicException {
        Storage baseStorage = StorageLayer.getBaseStorage(main);
        if (isSameDatabase(storage, baseStorage)) {
            return ((MultitenancySQLStorage) storage).getTenantCoreConfig_Transaction(con, tenantIdentifier);
        }
        MultitenancySQLStorage base = (MultitenancySQLStorage) baseStorage;
        return base.startTransaction(baseCon -> {
            JsonObject coreConfig = base.getTenantCoreConfig_Transaction(baseCon, tenantIdentifier);
            base.commitTransaction(baseCon);
            return coreConfig;
        });
    }

    private static boolean isSameDatabase(Storage a, Storage b) {
        return a.getUserPoolId().equals(b.getUserPoolId());
    }

    // An app whose disable_user_id_mapping a config write turns on, and the loaded storage of a database it uses.
    public record DisablingTarget(AppIdentifier appIdentifier, Storage storage) {
    }

    /**
     * Writes {@code newTenant} in a base storage transaction that first, for every target, takes the exclusive
     * mapping-creation lock in the target's database and checks it has no user id mappings for the app. The config
     * is committed before those locks are released, so a mapping creation that waited on them reads the flag as on.
     * Locks are taken in a fixed order (database, then app), so two such writes can't deadlock. Mapping creation
     * holds at most one database's lock at a time, except in the bulk import worker, which holds the claim
     * database's lock until the chunk commits and so takes it after every other database's.
     */
    @UnauditedTransaction(justification = "Tenant config writes are not audit events (overwriteTenantConfig is "
            + "unaudited); the transaction adds the mapping-creation locks and checks around the same write.")
    public static void overwriteTenantConfigDisablingUserIdMapping(Main main, TenantConfig newTenant,
                                                                   List<DisablingTarget> targets)
            throws StorageQueryException, InvalidConfigException, TenantOrAppNotFoundException,
            DuplicateThirdPartyIdException, DuplicateClientTypeException {
        MultitenancySQLStorage base = (MultitenancySQLStorage) StorageLayer.getBaseStorage(main);
        Map<String, List<DisablingTarget>> targetsByDatabase = new TreeMap<>();
        for (DisablingTarget target : targets) {
            targetsByDatabase.computeIfAbsent(target.storage().getUserPoolId(), k -> new ArrayList<>()).add(target);
        }
        List<List<DisablingTarget>> groups = new ArrayList<>(targetsByDatabase.values());
        for (List<DisablingTarget> group : groups) {
            group.sort(Comparator.comparing((DisablingTarget t) -> t.appIdentifier().getConnectionUriDomain())
                    .thenComparing(t -> t.appIdentifier().getAppId()));
        }
        try {
            base.startTransaction(baseCon -> {
                lockCheckAndWrite(base, baseCon, newTenant, groups, 0);
                return null;
            });
        } catch (StorageTransactionLogicException e) {
            Exception actual = e.actualException;
            if (actual instanceof InvalidConfigException invalidConfig) {
                throw invalidConfig;
            }
            if (actual instanceof TenantOrAppNotFoundException notFound) {
                throw notFound;
            }
            if (actual instanceof DuplicateThirdPartyIdException duplicateThirdPartyId) {
                throw duplicateThirdPartyId;
            }
            if (actual instanceof DuplicateClientTypeException duplicateClientType) {
                throw duplicateClientType;
            }
            if (actual instanceof StorageQueryException storageQueryException) {
                throw storageQueryException;
            }
            throw new StorageQueryException(actual);
        }
    }

    @UnauditedTransaction(justification = "Tenant config writes are not audit events (overwriteTenantConfig is "
            + "unaudited); the transaction only holds the mapping-creation lock in another database.")
    private static void lockCheckAndWrite(MultitenancySQLStorage base, TransactionConnection baseCon,
                                          TenantConfig newTenant, List<List<DisablingTarget>> groups, int index)
            throws StorageQueryException, StorageTransactionLogicException {
        if (index == groups.size()) {
            try {
                base.overwriteTenantConfig_Transaction(baseCon, newTenant);
            } catch (TenantOrAppNotFoundException | DuplicateThirdPartyIdException | DuplicateClientTypeException e) {
                throw new StorageTransactionLogicException(e);
            }
            base.commitTransaction(baseCon);
            return;
        }
        List<DisablingTarget> group = groups.get(index);
        Storage storage = group.get(0).storage();
        if (isSameDatabase(storage, base)) {
            lockAndAssertNoMappings((UserIdMappingSQLStorage) base, baseCon, group);
            lockCheckAndWrite(base, baseCon, newTenant, groups, index + 1);
            return;
        }
        UserIdMappingSQLStorage mappingStorage = (UserIdMappingSQLStorage) storage;
        mappingStorage.startTransaction(con -> {
            lockAndAssertNoMappings(mappingStorage, con, group);
            lockCheckAndWrite(base, baseCon, newTenant, groups, index + 1);
            mappingStorage.commitTransaction(con);
            return null;
        });
    }

    private static void lockAndAssertNoMappings(UserIdMappingSQLStorage storage, TransactionConnection con,
                                                List<DisablingTarget> group)
            throws StorageQueryException, StorageTransactionLogicException {
        for (DisablingTarget target : group) {
            storage.lockAppForDisablingUserIdMapping_Transaction(con, target.appIdentifier());
            if (storage.hasUserIdMappings_Transaction(con, target.appIdentifier())) {
                throw new StorageTransactionLogicException(new InvalidConfigException(
                        UserIdMapping.CANNOT_DISABLE_WITH_MAPPINGS_MESSAGE));
            }
        }
    }
}
