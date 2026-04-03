# Cutover Procedure: Deprecating `all_auth_recipe_users`

This document describes the step-by-step operational procedure for migrating a production SuperTokens deployment from the old table structure (`all_auth_recipe_users`, `*_user_to_tenant` tables) to the new reservation tables (`recipe_user_tenants`, `recipe_user_account_infos`, `primary_user_tenants`).

## Prerequisites

- All code from the migration branch is deployed and available
- Database is accessible with appropriate permissions
- Ability to set `migration_mode` config on all SuperTokens instances
- Ability to monitor application logs

## Migration Modes

| Mode | Old Table Writes | New Table Writes | Old Table Reads | New Table Reads |
|------|-----------------|-----------------|----------------|----------------|
| `LEGACY` (default) | Yes | No | Yes | No |
| `DUAL_WRITE_READ_OLD` | Yes | Yes | Yes | No |
| `DUAL_WRITE_READ_NEW` | Yes | Yes | No | Yes |
| `MIGRATED` | No | Yes | No | Yes |

## Step 1: Deploy with `DUAL_WRITE_READ_OLD`

**Risk: Low** — reads unchanged, only writes are duplicated.

Set config on all instances:
```
migration_mode: DUAL_WRITE_READ_OLD
```

Or via environment variable:
```
SUPERTOKENS_MIGRATION_MODE=DUAL_WRITE_READ_OLD
```

**What happens:**
- All new signups, link operations, email/phone updates write to BOTH old and new tables
- Reads still come from old tables (old data is source of truth)
- Any old instances still in `LEGACY` mode only write to old tables — safe since reads come from old tables

**Verification:**
```sql
-- Create a test user, then verify dual-write:
SELECT COUNT(*) FROM recipe_user_account_infos WHERE app_id = 'public';
-- Should show new entries for recently created users

SELECT COUNT(*) FROM all_auth_recipe_users WHERE app_id = 'public';
-- Should also have entries (still being written)
```

**Rollback:** Set `migration_mode: LEGACY` or remove the config entirely.

## Step 2: Run Backfill

The `BackfillReservationTables` cron job runs automatically when `migration_mode != LEGACY`. It backfills existing users (created before dual-write was enabled) into the new tables.

**Monitor progress via logs:**
```
Backfill starting: 50000 users pending for app public
Backfill progress: 10000/50000 users processed
Backfill progress: 20000/50000 users processed
...
Backfill complete and verified: 50000 users processed
```

**Monitor via SQL:**
```sql
-- Users still needing backfill (time_joined = 0 means pre-migration)
SELECT COUNT(*) FROM app_id_to_user_id
WHERE app_id = 'public' AND time_joined = 0;
-- Target: 0
```

**If backfill seems stuck or slow:**
- The backfill processes 1000 users per batch with `SELECT FOR UPDATE` locking
- High concurrent write load may slow it down due to lock contention
- The cron runs every 5 minutes; each run processes all remaining users in a loop
- Check for long-running transactions that may hold locks

**Rollback:** Not needed — backfill only adds data, never modifies or deletes existing data.

## Step 3: Validate Data Consistency

Run these queries to verify the backfill completed correctly:

```sql
-- 1. No users missing time_joined
SELECT COUNT(*) FROM app_id_to_user_id
WHERE time_joined = 0 AND app_id = 'public';
-- Expected: 0

-- 2. All users have account info entries
SELECT COUNT(*) FROM app_id_to_user_id a
WHERE a.app_id = 'public' AND NOT EXISTS (
    SELECT 1 FROM recipe_user_account_infos rai
    WHERE rai.app_id = a.app_id AND rai.recipe_user_id = a.user_id
);
-- Expected: 0

-- 3. Tenant coverage matches
SELECT
    (SELECT COUNT(*) FROM all_auth_recipe_users WHERE app_id = 'public') AS old_count,
    (SELECT COUNT(*) FROM recipe_user_tenants WHERE app_id = 'public') AS new_count;
-- new_count should be >= old_count (may be higher for users with multiple account infos)

-- 4. All linked users have primary reservations
SELECT DISTINCT a.primary_or_recipe_user_id
FROM app_id_to_user_id a
WHERE a.is_linked_or_is_a_primary_user = TRUE AND a.app_id = 'public'
AND NOT EXISTS (
    SELECT 1 FROM primary_user_tenants pt
    WHERE pt.app_id = a.app_id AND pt.primary_user_id = a.primary_or_recipe_user_id
);
-- Expected: empty
```

## Step 4: Switch to `DUAL_WRITE_READ_NEW`

**Risk: Medium** — reads now come from new tables.

**CRITICAL:** All instances must be on the new code. If any instance is still in `LEGACY` mode, it only writes to old tables — but reads now come from new tables, so its writes won't be visible.

**Deployment strategy:**
1. Drain traffic from old instances
2. Update ALL instances to `migration_mode: DUAL_WRITE_READ_NEW`
3. Resume traffic

Or, if all instances already have the new code:
- Rolling update changing only the config value

**Verification:**
- Create a user → verify it's readable
- Link users → verify primary user info is correct
- Update email → verify reflected in API responses
- Dashboard search → verify results are correct

**Rollback:** Set all instances back to `DUAL_WRITE_READ_OLD`. Since dual-write keeps old tables in sync, reading from old tables again is safe.

## Step 5: Monitor in Production

**Duration:** Run in `DUAL_WRITE_READ_NEW` for at least 1-2 weeks.

**Monitor:**
- Error rates (should not increase)
- API latency (should not increase significantly)
- Database query latency (check slow query log)
- Any 500 errors related to user lookups, account linking, or tenant operations

## Step 6: Switch to `MIGRATED`

**Risk: Medium** — old table writes stop.

Set all instances to:
```
migration_mode: MIGRATED
```

**What happens:**
- Writes only go to new tables (+ `app_id_to_user_id` which is always written)
- Reads come from new tables
- `all_auth_recipe_users` and `*_user_to_tenant` tables stop receiving writes
- These tables can be dropped in a future release

**Rollback:** Set back to `DUAL_WRITE_READ_NEW`. **Note:** Any data written in `MIGRATED` mode will NOT be in old tables. If you need to rollback to `DUAL_WRITE_READ_OLD` (reading old tables), you'd need to re-backfill old tables first. **This is the point of no easy rollback to old reads.**

## Step 7: Drop Deprecated Tables (Future Release)

After confirming `MIGRATED` mode is stable (recommended: 1+ release cycles):

```sql
DROP TABLE IF EXISTS emailpassword_user_to_tenant;
DROP TABLE IF EXISTS passwordless_user_to_tenant;
DROP TABLE IF EXISTS thirdparty_user_to_tenant;
DROP TABLE IF EXISTS webauthn_user_to_tenant;
DROP TABLE IF EXISTS all_auth_recipe_users;
```

Also remove from code:
- All `_legacy` query methods
- The `MigrationMode` config and conditional logic
- Old table `CREATE TABLE` statements and indexes
- The `MigrationBackfillStorage` interface and implementations
- The `BackfillReservationTables` cron task

## Timeline Summary

| Step | Duration | Risk | Rollback |
|------|----------|------|----------|
| 1. Deploy DUAL_WRITE_READ_OLD | Minutes | Low | Set LEGACY |
| 2. Run backfill | Minutes to hours | Low | N/A (additive only) |
| 3. Validate | Minutes | None | N/A |
| 4. Switch to DUAL_WRITE_READ_NEW | Minutes | Medium | Set DUAL_WRITE_READ_OLD |
| 5. Monitor | 1-2 weeks | — | Set DUAL_WRITE_READ_OLD |
| 6. Switch to MIGRATED | Minutes | Medium | Set DUAL_WRITE_READ_NEW* |
| 7. Drop tables | Future release | Low | N/A |

\* Rollback from MIGRATED to reading old tables requires re-syncing old tables.

## Multi-App Deployments

The backfill cron runs per-app. For multi-app deployments:
- Each app is processed independently
- Progress is tracked per-app via `time_joined = 0` count
- The `migration_mode` config is per-CUD (Connection URI Domain)
- You can migrate different CUDs at different times
