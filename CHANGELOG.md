# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), and this project adheres
to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [10.0.3]

- Fixes `StorageTransactionLogicException` in bulk import when not using userRoles and totpDevices in import json.
- MFA only required in Bulk Import if it's used in input data
- Fixes issue with reloading all resources when exception occurs while loading a resource, other valid resources were offloaded from the memory. Now we log the exception and continue loading other resources.
- Adds `USE_STRUCTURED_LOGGING` environment variable to control the logging format.

## [10.0.2]

- Fixes `NullPointerException` in user search API.

## [10.0.1]

- Fixes slow queries for account linking
- Masks db password in 500 response

### Migration

If using PostgreSQL, run the following SQL script:

```sql
CREATE INDEX IF NOT EXISTS emailpassword_users_email_index ON emailpassword_users (app_id, email);
CREATE INDEX IF NOT EXISTS emailpassword_user_to_tenant_email_index ON emailpassword_user_to_tenant (app_id, tenant_id, email);

CREATE INDEX IF NOT EXISTS passwordless_users_email_index ON passwordless_users (app_id, email);
CREATE INDEX IF NOT EXISTS passwordless_users_phone_number_index ON passwordless_users (app_id, phone_number);
CREATE INDEX IF NOT EXISTS passwordless_user_to_tenant_email_index ON passwordless_user_to_tenant (app_id, tenant_id, email);
CREATE INDEX IF NOT EXISTS passwordless_user_to_tenant_phone_number_index ON passwordless_user_to_tenant (app_id, tenant_id, phone_number);

CREATE INDEX IF NOT EXISTS thirdparty_user_to_tenant_third_party_user_id_index ON thirdparty_user_to_tenant (app_id, tenant_id, third_party_id, third_party_user_id);
```

If using MySQL, run the following SQL script:

```sql
CREATE INDEX emailpassword_users_email_index ON emailpassword_users (app_id, email);
CREATE INDEX emailpassword_user_to_tenant_email_index ON emailpassword_user_to_tenant (app_id, tenant_id, email);

CREATE INDEX passwordless_users_email_index ON passwordless_users (app_id, email);
CREATE INDEX passwordless_users_phone_number_index ON passwordless_users (app_id, phone_number);
CREATE INDEX passwordless_user_to_tenant_email_index ON passwordless_user_to_tenant (app_id, tenant_id, email);
CREATE INDEX passwordless_user_to_tenant_phone_number_index ON passwordless_user_to_tenant (app_id, tenant_id, phone_number);

CREATE INDEX thirdparty_user_to_tenant_third_party_user_id_index ON thirdparty_user_to_tenant (app_id, tenant_id, third_party_id, third_party_user_id);
```

## [10.0.0]

### Added

- Optimize getUserIdMappingWithEitherSuperTokensUserIdOrExternalUserId query
- Adds property `bulk_migration_parallelism` for fine-tuning the worker threads number
- Adds APIs to bulk import users
  - GET `/bulk-import/users`
  - POST `/bulk-import/users`
  - GET `/bulk-import/users/count`
  - POST `/bulk-import/users/remove`
  - POST `/bulk-import/users/import`
- Adds `ProcessBulkImportUsers` cron job to process bulk import users
- Adds multithreaded worker support for the `ProcessBulkImportUsers` cron job for faster bulk imports
- Adds support for lazy importing users

### Breaking changes

- Includes CUD in the owner field for OAuth clients

### Fixes

- Fixes issue with user id mapping while refreshing session
- Adds indexing for `session_info` table on `user_id, app_id` columns

### Migrations

For PostgreSQL, run the following SQL script:

```sql
CREATE TABLE IF NOT EXISTS bulk_import_users (
    id CHAR(36),
    app_id VARCHAR(64) NOT NULL DEFAULT 'public',
    primary_user_id VARCHAR(36),
    raw_data TEXT NOT NULL,
    status VARCHAR(128) DEFAULT 'NEW',
    error_msg TEXT,
    created_at BIGINT NOT NULL, 
    updated_at BIGINT NOT NULL, 
    CONSTRAINT bulk_import_users_pkey PRIMARY KEY(app_id, id),
    CONSTRAINT bulk_import_users__app_id_fkey FOREIGN KEY(app_id) REFERENCES apps(app_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS bulk_import_users_status_updated_at_index ON bulk_import_users (app_id, status, updated_at);

CREATE INDEX IF NOT EXISTS bulk_import_users_pagination_index1 ON bulk_import_users (app_id, status, created_at DESC, id DESC);
 
CREATE INDEX IF NOT EXISTS bulk_import_users_pagination_index2 ON bulk_import_users (app_id, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS session_info_user_id_app_id_index ON session_info (user_id, app_id);
```

For MySQL run the following SQL script:

```sql
CREATE TABLE IF NOT EXISTS bulk_import_users (
    id CHAR(36),
    app_id VARCHAR(64) NOT NULL DEFAULT 'public',
    primary_user_id VARCHAR(36),
    raw_data TEXT NOT NULL,
    status VARCHAR(128) DEFAULT 'NEW',
    error_msg TEXT,
    created_at BIGINT UNSIGNED NOT NULL, 
    updated_at BIGINT UNSIGNED NOT NULL, 
    PRIMARY KEY (app_id, id),
    FOREIGN KEY(app_id) REFERENCES apps(app_id) ON DELETE CASCADE
);

CREATE INDEX bulk_import_users_status_updated_at_index ON bulk_import_users (app_id, status, updated_at);

CREATE INDEX bulk_import_users_pagination_index1 ON bulk_import_users (app_id, status, created_at DESC, id DESC);
 
CREATE INDEX bulk_import_users_pagination_index2 ON bulk_import_users (app_id, created_at DESC, id DESC);

CREATE INDEX session_info_user_id_app_id_index ON session_info (user_id, app_id);
```

## [9.3.1]

- Includes exception class name in 500 error message


## [9.3.0]

### Changes

- Adds support for OAuth2
    - Added new feature in license key: `OAUTH`
    - Adds new core config:
        - `oauth_provider_public_service_url`
        - `oauth_provider_admin_service_url`
        - `oauth_provider_consent_login_base_url`
        - `oauth_provider_url_configured_in_oauth_provider`
    - Adds following APIs:
        - POST `/recipe/oauth/clients`
        - PUT `/recipe/oauth/clients`
        - GET `/recipe/oauth/clients`
        - GET `/recipe/oauth/clients/list`
        - POST `/recipe/oauth/clients/remove`
        - GET `/recipe/oauth/auth/requests/consent`
        - PUT `/recipe/oauth/auth/requests/consent/accept`
        - PUT `/recipe/oauth/auth/requests/consent/reject`
        - GET `/recipe/oauth/auth/requests/login`
        - PUT `/recipe/oauth/auth/requests/login/accept`
        - PUT `/recipe/oauth/auth/requests/login/reject`
        - GET `/recipe/oauth/auth/requests/logout`
        - PUT `/recipe/oauth/auth/requests/logout/accept`
        - PUT `/recipe/oauth/auth/requests/logout/reject`
        - POST `/recipe/oauth/auth`
        - POST `/recipe/oauth/token`
        - POST `/recipe/oauth/introspect`
        - POST `/recipe/oauth/session/revoke`
        - POST `/recipe/oauth/token/revoke`
        - POST `/recipe/oauth/tokens/revoke`

### Migration

If using PostgreSQL, run the following SQL script:

```sql
CREATE TABLE IF NOT EXISTS oauth_clients (
    app_id VARCHAR(64),
    client_id VARCHAR(255) NOT NULL,
    is_client_credentials_only BOOLEAN NOT NULL,
    PRIMARY KEY (app_id, client_id),
    FOREIGN KEY(app_id) REFERENCES apps(app_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS oauth_sessions (
    gid VARCHAR(255),
    app_id VARCHAR(64) DEFAULT 'public',
    client_id VARCHAR(255) NOT NULL,
    session_handle VARCHAR(128),
    external_refresh_token VARCHAR(255) UNIQUE,
    internal_refresh_token VARCHAR(255) UNIQUE,
    jti TEXT NOT NULL,
    exp BIGINT NOT NULL,
    PRIMARY KEY (gid),
    FOREIGN KEY(app_id, client_id) REFERENCES oauth_clients(app_id, client_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS oauth_session_exp_index ON oauth_sessions(exp DESC);
CREATE INDEX IF NOT EXISTS oauth_session_external_refresh_token_index ON oauth_sessions(app_id, external_refresh_token DESC);

CREATE TABLE IF NOT EXISTS oauth_m2m_tokens (
    app_id VARCHAR(64) DEFAULT 'public',
    client_id VARCHAR(255) NOT NULL,
    iat BIGINT NOT NULL,
    exp BIGINT NOT NULL,
    PRIMARY KEY (app_id, client_id, iat),
    FOREIGN KEY(app_id, client_id) REFERENCES oauth_clients(app_id, client_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS oauth_m2m_token_iat_index ON oauth_m2m_tokens(iat DESC, app_id DESC);
CREATE INDEX IF NOT EXISTS oauth_m2m_token_exp_index ON oauth_m2m_tokens(exp DESC);

CREATE TABLE IF NOT EXISTS oauth_logout_challenges (
    app_id VARCHAR(64) DEFAULT 'public',
    challenge VARCHAR(128) NOT NULL,
    client_id VARCHAR(255) NOT NULL,
    post_logout_redirect_uri VARCHAR(1024),
    session_handle VARCHAR(128),
    state VARCHAR(128),
    time_created BIGINT NOT NULL,
    PRIMARY KEY (app_id, challenge),
    FOREIGN KEY(app_id, client_id) REFERENCES oauth_clients(app_id, client_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS oauth_logout_challenges_time_created_index ON oauth_logout_challenges(time_created DESC);
```

If using MySQL, run the following SQL script:

```sql
CREATE TABLE IF NOT EXISTS oauth_clients (
  app_id VARCHAR(64),
  client_id VARCHAR(255) NOT NULL,
  is_client_credentials_only BOOLEAN NOT NULL,
  PRIMARY KEY (app_id, client_id),
  FOREIGN KEY(app_id) REFERENCES apps(app_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS oauth_sessions (
  gid VARCHAR(255),
  app_id VARCHAR(64) DEFAULT 'public',
  client_id VARCHAR(255) NOT NULL,
  session_handle VARCHAR(128),
  external_refresh_token VARCHAR(255) UNIQUE,
  internal_refresh_token VARCHAR(255) UNIQUE,
  jti TEXT NOT NULL,
  exp BIGINT NOT NULL,
  PRIMARY KEY (gid),
  FOREIGN KEY(app_id, client_id) REFERENCES oauth_clients(app_id, client_id) ON DELETE CASCADE
);

CREATE INDEX oauth_session_exp_index ON oauth_sessions(exp DESC);
CREATE INDEX oauth_session_external_refresh_token_index ON oauth_sessions(app_id, external_refresh_token DESC);

CREATE TABLE oauth_m2m_tokens (
  app_id VARCHAR(64) DEFAULT 'public',
  client_id VARCHAR(255) NOT NULL,
  iat BIGINT UNSIGNED NOT NULL,
  exp BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (app_id, client_id, iat),
  FOREIGN KEY(app_id, client_id) REFERENCES oauth_clients(app_id, client_id) ON DELETE CASCADE
);

CREATE INDEX oauth_m2m_token_iat_index ON oauth_m2m_tokens(iat DESC, app_id DESC);
CREATE INDEX oauth_m2m_token_exp_index ON oauth_m2m_tokens(exp DESC);

CREATE TABLE IF NOT EXISTS oauth_logout_challenges (
  app_id VARCHAR(64) DEFAULT 'public',
  challenge VARCHAR(128) NOT NULL,
  client_id VARCHAR(255) NOT NULL,
  post_logout_redirect_uri VARCHAR(1024),
  session_handle VARCHAR(128),
  state VARCHAR(128),
  time_created BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (app_id, challenge),
  FOREIGN KEY(app_id, client_id) REFERENCES oauth_clients(app_id, client_id) ON DELETE CASCADE
);

CREATE INDEX oauth_logout_challenges_time_created_index ON oauth_logout_challenges(time_created ASC, app_id ASC);
```

## [9.2.3] - 2024-10-09

- Adds support for `--with-temp-dir` in CLI and `tempDirLocation=` in Core
- Adds validation to firstFactors and requiredSecondaryFactors names while creating tenants/apps/etc. to not allow 
  special chars.

## [9.2.2] - 2024-09-04

- Adds index on `last_active_time` for `user_last_active` table to improve the performance of MAU computation.

### Migration

If using PostgreSQL, run the following SQL script:

```sql
CREATE INDEX IF NOT EXISTS user_last_active_last_active_time_index ON user_last_active (last_active_time DESC, app_id DESC);
```

If using MySQL, run the following SQL script:

```sql
CREATE INDEX user_last_active_last_active_time_index ON user_last_active (last_active_time DESC, app_id DESC);
```

## [9.2.1] - 2024-09-02

- Removes the stats that were resulting in high CPU consumption

## [9.2.0] - 2024-08-20

- Adds `SECURITY` feature in `EE_FEATURES`.

## [9.1.2] - 2024-07-24

- Fixes path routing which rejected tenantId stop words even if it was not an exact stop word match. For example, `/hellotenant` is a valid tenantId prefix, however, it was being rejected for the stop word `hello`. - https://github.com/supertokens/supertokens-core/issues/1021
- 500 errors in core returns actual exception, since these APIs are developer facing, it makes easier to debug these errors.

## [9.1.1] - 2024-07-24

### Fixes

- Account linking now properly checks if the login methods of the primary user can be shared with the tenants of the 
  recipe user we are trying to link
- Simplifying email verification token creation

## [9.1.0] - 2024-05-24

### Changes

- Adds support for CDI 3.1 and 5.1
- Adds annotations to properties `CoreConfig` to aid dashboard API.
- Updates `ApiVersionAPI` to optionally accept `websiteDomain` and `apiDomain` for telemetry.
- Adds GET `/recipe/dashboard/tenant/core-config` to fetch the core properties with metadata for dashboard.
- Reports `websiteDomain` and `apiDomain` for each app in telemetry.
- API Key can now be passed using the `Authorization` header: `Authorization: <api-key>`

### Breaking changes

- CUD/App/Tenant Management APIs are deprecated and v2 versions have been added
  - Adds new core API for fetching all the core properties for a tenant
      - GET `/appid-<appid>/<tenantid>/recipe/dashboard/tenant/core-config`
  - Deprecated the following APIs
      - PUT `/recipe/multitenancy/connectionuridomain`
      - GET `/recipe/multitenancy/connectionuridomain/list`
      - PUT `/recipe/multitenancy/app`
      - GET `/recipe/multitenancy/app/list`
      - PUT `/appid-<appid>/recipe/multitenancy/tenant`
      - GET `/appid-<appid>/<tenantid>/recipe/multitenancy/tenant`
      - GET `/appid-<appid>/<tenantid>/recipe/multitenancy/tenant/list`
  - Adds the following APIs to replace the deprecated APIs
      - PUT `/recipe/multitenancy/connectionuridomain/v2`
      - GET `/recipe/multitenancy/connectionuridomain/list/v2`
      - PUT `/recipe/multitenancy/app/v2`
      - GET `/recipe/multitenancy/app/list/v2`
      - PUT `/appid-<appid>/recipe/multitenancy/tenant/v2`
      - GET `/appid-<appid>/<tenantid>/recipe/multitenancy/tenant/v2`
      - GET `/appid-<appid>/<tenantid>/recipe/multitenancy/tenant/list/v2`

- In CDI 5.1, the auth recipe APIs such as emailpassword signIn, thirdParty signInUp, etc would not be blocked if the recipe was disabled using the deprecated APIs. They will be enforced if CDI version <= 5.0 is being passed in the header.

### Fixes

- Updates descriptions in the config.yaml to be consistent with the annotations.
- Adds correct `max-age` for `JWKSPublicAPI` based on dynamic key generation interval.
- Fixes `500` error when using TOTP code longer than 8 characters.

### Migration

Make sure the core is already upgraded to version 9.0.2 before migrating

If using PostgreSQL

```sql
ALTER TABLE tenant_configs ADD COLUMN IF NOT EXISTS is_first_factors_null BOOLEAN DEFAULT TRUE;
ALTER TABLE tenant_configs ALTER COLUMN is_first_factors_null DROP DEFAULT;
```

If using MySQL

```sql
ALTER TABLE tenant_configs ADD COLUMN is_first_factors_null BOOLEAN DEFAULT TRUE;
ALTER TABLE tenant_configs ALTER COLUMN is_first_factors_null DROP DEFAULT;
```

## [9.0.2] - 2024-04-17

- Fixes issue with core startup when creation of CUD/app/tenant has partial failure

## [9.0.1] - 2024-03-20

- Fixes verify TOTP and verify device APIs to treat any code as invalid
- Fixes the computation of the number of failed attempts when return `INVALID_TOTP_ERROR`

## [9.0.0] - 2024-03-13

### Added

- Supports CDI version `5.0`
- MFA stats in `EEFeatureFlag`
- Adds `ImportTotpDeviceAPI`
- Adds `CheckCodeAPI`

### Changes

- `deviceName` in request body of `CreateOrUpdateTotpDeviceAPI` `POST` is now optional
- Adds `firstFactors` and `requiredSecondaryFactors` in request body of create or update CUD, App and
  Tenant APIs
- Adds `deviceName` in the response of `CreateOrUpdateTotpDeviceAPI` `POST`
- `VerifyTOTPAPI` changes
    - Removes `allowUnverifiedDevices` from request body and unverified devices are not allowed
    - Adds `currentNumberOfFailedAttempts` and `maxNumberOfFailedAttempts` in response when status is
      `INVALID_TOTP_ERROR` or `LIMIT_REACHED_ERROR`
    - Adds status `UNKNOWN_USER_ID_ERROR`
- `VerifyTotpDeviceAPI` changes
    - Adds `currentNumberOfFailedAttempts` and `maxNumberOfFailedAttempts` in response when status is
      `INVALID_TOTP_ERROR` or `LIMIT_REACHED_ERROR`
- Adds `consumedDevice` in the success response of the `ConsumeCodeAPI`
- Adds `preAuthSessionId` input to `DeleteCodeAPI` to be able to delete codes for a device
- Adds a new `useDynamicSigningKey` into the request body of `RefreshSessionAPI`
    - This enables smooth switching between `useDynamicAccessTokenSigningKey` settings by allowing refresh calls to
      change the signing key type of a session
    - This is available after CDI3.0
    - This is required in&after CDI5.0 and optional before
- Adds optional `firstFactors` and `requiredSecondaryFactors` to the create or update connectionUriDomain, app and
  tenant APIs
- Updates Last active while linking accounts
- Marks fake email in email password sign up as verified
- Fixes slow down in useridmapping queries
- Adds core version in the logs
- Fixes issue with session creation when using external user id on a linked account
- Enforces the API call from public tenant for the APIs that are app specific

### Migration

Make sure the core is already upgraded to version 8.0.0 before migrating

If using PostgreSQL

```sql
ALTER TABLE totp_user_devices
    ADD COLUMN IF NOT EXISTS created_at BIGINT default 0;
ALTER TABLE totp_user_devices
    ALTER COLUMN created_at DROP DEFAULT;
```

If using MySQL

```sql
ALTER TABLE totp_user_devices
    ADD COLUMN created_at BIGINT UNSIGNED default 0;
ALTER TABLE totp_user_devices
    ALTER COLUMN created_at DROP DEFAULT;
DROP INDEX all_auth_recipe_users_pagination_index2 ON all_auth_recipe_users;
DROP INDEX all_auth_recipe_users_pagination_index4 ON all_auth_recipe_users;
```

## [8.0.1] - 2024-03-11

- Making this version backward compatible. Breaking changes in `8.0.0` can now be ignored.

## [8.0.0] - 2024-03-04

### Breaking changes

- The following app specific APIs return a 403 when they are called with a tenant ID other than the `public` one. For
  example, if the path is `/users/count/active`, and you call it with `/tenant1/users/count/active`, it will return a
    403. But if you call it with `/public/users/count/active`, or just `/users/count/active`, it will work.

    - GET `/recipe/accountlinking/user/primary/check`
    - GET `/recipe/accountlinking/user/link/check`
    - POST `/recipe/accountlinking/user/primary`
    - POST `/recipe/accountlinking/user/link`
    - POST `/recipe/accountlinking/user/unlink`
    - GET `/users/count/active`
    - POST `/user/remove`
    - GET `/ee/featureflag`
    - GET `/user/id`
    - PUT `/ee/license`
    - DELETE `/ee/license`
    - GET `/ee/license`
    - GET `/requests/stats`
    - GET `/recipe/user` when querying by `userId`
    - GET `/recipe/jwt/jwks`
    - POST `/recipe/jwt`

### Fixes

- Fixes issue with non-auth recipe related storage handling

### Migration

For Postgresql:

```sql
ALTER TABLE user_roles DROP CONSTRAINT IF EXISTS user_roles_role_fkey;
```

For MySQL:

```sql
ALTER TABLE user_roles DROP FOREIGN KEY user_roles_ibfk_1;
ALTER TABLE user_roles DROP FOREIGN KEY user_roles_ibfk_2;
ALTER TABLE user_roles
    ADD FOREIGN KEY (app_id, tenant_id)
        REFERENCES tenants (app_id, tenant_id) ON DELETE CASCADE;
```

## [7.0.18] - 2024-02-19

- Fixes vulnerabilities in dependencies
- Updates telemetry payload
- Fixes Active User tracking to use the right storage

## [7.0.17] - 2024-02-06

- Fixes issue where error logs were printed to StdOut instead of StdErr.
- Adds new config `supertokens_saas_load_only_cud` that makes the core instance load a particular CUD only, irrespective
  of the CUDs present in the db.
- Fixes connection pool handling when connection pool size changes for a tenant.

## [7.0.16] - 2023-12-04

- Returns 400, instead of 500, for badly typed core config while creating CUD, App or Tenant

## [7.0.15] - 2023-11-28

- Adds test for user pagination from old version

## [7.0.14] - 2023-11-21

- Updates test user query speed

### Migration

If using MySQL plugin, run the following SQL script:

```sql
CREATE INDEX app_id_to_user_id_primary_user_id_index ON app_id_to_user_id (primary_or_recipe_user_id);
CREATE INDEX app_id_to_user_id_user_id_index ON app_id_to_user_id (user_id);
```

## [7.0.13] - 2023-11-21

- Adds test to user query speed

### Migration

If using PostgreSQL database, run the following sql script:

```sql
CREATE INDEX IF NOT EXISTS app_id_to_user_id_primary_user_id_index ON app_id_to_user_id (primary_or_recipe_user_id, app_id);
```

## [7.0.12] - 2023-11-16

In this release, the core API routes have been updated to incorporate phone number normalization before processing.
Consequently, existing entries in the database also need to undergo normalization. To facilitate this, we have included
a migration script to normalize phone numbers for all the existing entries.

**NOTE**: You can skip the migration if you are not using passwordless via phone number.

### Migration steps

This script updates the `phone_number` column in the `passwordless_users`, `passwordless_user_to_tenant`,
and `passwordless_devices` tables with their respective normalized values. This script is idempotent and can be run
multiple times without any issue. Follow the steps below to run the script:

1. Ensure that the core is already upgraded to version 7.0.12 (CDI version 4.0)
2. Run the migration script

   Make sure your Node.js version is 16 or above to run the script. Locate the migration script
   at `supertokens-core/migration_scripts/to_version_7_0_12/index.js`. Modify the script by updating
   the `DB_HOST`, `DB_USER`, `DB_PASSWORD`, and `DB_NAME` variables with the correct values. Subsequently, run the
   following commands to initiate the script:

    ```bash
       $ git clone https://github.com/supertokens/supertokens-core.git
       $ cd supertokens-core/migration_scripts/to_version_7_0_12
       $ npm install
       $ npm start
    ```

   Performance Note: On average, the script takes 19s for every 1000 rows with a maximum of 1 connection, 4.7s with a
   maximum of 5 connections (default), and 4.5s with a maximum of 10 connections. Increasing the `MAX_POOL_SIZE` allows
   the script to leverage more connections simultaneously, potentially improving execution speed.

## [7.0.11] - 2023-11-10

- Fixes email verification behaviour with user id mapping

## [7.0.10] - 2023-11-03

- Collects requests stats per app
- Adds `/requests/stats` API to return requests stats for the last day

## [7.0.9] - 2023-11-01

- Tests `verified` in `loginMethods` for users with userId mapping

## [7.0.8] - 2023-10-19

- Tests thirdParty serialization fix

## [7.0.7] - 2023-10-19

- Fixes test that verifies tenant config persistence

## [7.0.6] - 2023-10-18

- Fixes issue with cron tasks that run per app and tenant

## [7.0.5] - 2023-10-13

- Adds postgres testing to the CICD

## [7.0.4] - 2023-10-12

- Fixes user info from primary user id query
- Fixes `deviceIdHash` issue

## [7.0.3] - 2023-10-11

- Fixes issue with duplicate cron task

## [7.0.2] = 2023-10-05

- Fixes `500` error for passwordless login in certain cases - https://github.com/supertokens/supertokens-core/issues/828

## [7.0.1] - 2023-10-04

- Remove padding from link codes and pre-auth session ids in passwordless, but keep support for old format that included
  padding (`=` signs)

## [7.0.0] - 2023-09-19

- Support for CDI version 4.0
- Adds Account Linking feature

### Session recipe changes

- New access token version: v5, which contains a required prop: `rsub`. This contains the recipe user ID that belongs to
  the login method that the user used to login. The `sub` claim in the access token payload is now the primary user ID.
- APIs that return `SessionInformation` (like GET `/recipe/session`) contains userId, recipeUserId in the response.
- Apis that create / modify / refresh a session return the `recipeUserId` in the `session` object in the response.
- Token theft detected response returns userId and recipeUserId

### Db Schema changes

- Adds columns `primary_or_recipe_user_id`, `is_linked_or_is_a_primary_user` and `primary_or_recipe_user_time_joined`
  to `all_auth_recipe_users` table
- Adds columns `primary_or_recipe_user_id` and `is_linked_or_is_a_primary_user` to `app_id_to_user_id` table
- Removes index `all_auth_recipe_users_pagination_index` and addes `all_auth_recipe_users_pagination_index1`,
  `all_auth_recipe_users_pagination_index2`, `all_auth_recipe_users_pagination_index3` and
  `all_auth_recipe_users_pagination_index4` indexes instead on `all_auth_recipe_users` table
- Adds `all_auth_recipe_users_recipe_id_index` on `all_auth_recipe_users` table
- Adds `all_auth_recipe_users_primary_user_id_index` on `all_auth_recipe_users` table
- Adds `email` column to `emailpassword_pswd_reset_tokens` table
- Changes `user_id` foreign key constraint on `emailpassword_pswd_reset_tokens` to `app_id_to_user_id` table

### Migration steps for SQL

1. Ensure that the core is already upgraded to version 6.0.13 (CDI version 3.0)
2. Stop the core instance(s)
3. Run the migration script

    <details>

    <summary>If using PostgreSQL</summary>

    ```sql
    ALTER TABLE all_auth_recipe_users
      ADD COLUMN primary_or_recipe_user_id CHAR(36) NOT NULL DEFAULT ('0');

    ALTER TABLE all_auth_recipe_users
      ADD COLUMN is_linked_or_is_a_primary_user BOOLEAN NOT NULL DEFAULT FALSE;

    ALTER TABLE all_auth_recipe_users
      ADD COLUMN primary_or_recipe_user_time_joined BIGINT NOT NULL DEFAULT 0;

    UPDATE all_auth_recipe_users
      SET primary_or_recipe_user_id = user_id
      WHERE primary_or_recipe_user_id = '0';

    UPDATE all_auth_recipe_users
      SET primary_or_recipe_user_time_joined = time_joined
      WHERE primary_or_recipe_user_time_joined = 0;

    ALTER TABLE all_auth_recipe_users
      ADD CONSTRAINT all_auth_recipe_users_primary_or_recipe_user_id_fkey
        FOREIGN KEY (app_id, primary_or_recipe_user_id)
        REFERENCES app_id_to_user_id (app_id, user_id) ON DELETE CASCADE;

    ALTER TABLE all_auth_recipe_users
      ALTER primary_or_recipe_user_id DROP DEFAULT;

    ALTER TABLE app_id_to_user_id
      ADD COLUMN primary_or_recipe_user_id CHAR(36) NOT NULL DEFAULT ('0');

    ALTER TABLE app_id_to_user_id
      ADD COLUMN is_linked_or_is_a_primary_user BOOLEAN NOT NULL DEFAULT FALSE;

    UPDATE app_id_to_user_id
      SET primary_or_recipe_user_id = user_id
      WHERE primary_or_recipe_user_id = '0';

    ALTER TABLE app_id_to_user_id
      ADD CONSTRAINT app_id_to_user_id_primary_or_recipe_user_id_fkey
        FOREIGN KEY (app_id, primary_or_recipe_user_id)
        REFERENCES app_id_to_user_id (app_id, user_id) ON DELETE CASCADE;

    ALTER TABLE app_id_to_user_id
        ALTER primary_or_recipe_user_id DROP DEFAULT;

    DROP INDEX all_auth_recipe_users_pagination_index;

    CREATE INDEX all_auth_recipe_users_pagination_index1 ON all_auth_recipe_users (
      app_id, tenant_id, primary_or_recipe_user_time_joined DESC, primary_or_recipe_user_id DESC);

    CREATE INDEX all_auth_recipe_users_pagination_index2 ON all_auth_recipe_users (
      app_id, tenant_id, primary_or_recipe_user_time_joined ASC, primary_or_recipe_user_id DESC);

    CREATE INDEX all_auth_recipe_users_pagination_index3 ON all_auth_recipe_users (
      recipe_id, app_id, tenant_id, primary_or_recipe_user_time_joined DESC, primary_or_recipe_user_id DESC);

    CREATE INDEX all_auth_recipe_users_pagination_index4 ON all_auth_recipe_users (
      recipe_id, app_id, tenant_id, primary_or_recipe_user_time_joined ASC, primary_or_recipe_user_id DESC);

    CREATE INDEX all_auth_recipe_users_primary_user_id_index ON all_auth_recipe_users (primary_or_recipe_user_id, app_id);

    CREATE INDEX all_auth_recipe_users_recipe_id_index ON all_auth_recipe_users (app_id, recipe_id, tenant_id);

    ALTER TABLE emailpassword_pswd_reset_tokens DROP CONSTRAINT IF EXISTS emailpassword_pswd_reset_tokens_user_id_fkey;

    ALTER TABLE emailpassword_pswd_reset_tokens ADD CONSTRAINT emailpassword_pswd_reset_tokens_user_id_fkey FOREIGN KEY (app_id, user_id) REFERENCES app_id_to_user_id (app_id, user_id) ON DELETE CASCADE;

    ALTER TABLE emailpassword_pswd_reset_tokens ADD COLUMN email VARCHAR(256);
    ```
    </details>

    <details>

    <summary>If using MySQL</summary>

    ```sql
    ALTER TABLE all_auth_recipe_users
      ADD primary_or_recipe_user_id CHAR(36) NOT NULL DEFAULT ('0');

    ALTER TABLE all_auth_recipe_users
      ADD is_linked_or_is_a_primary_user BOOLEAN NOT NULL DEFAULT FALSE;

    ALTER TABLE all_auth_recipe_users
      ADD primary_or_recipe_user_time_joined BIGINT UNSIGNED NOT NULL DEFAULT 0;

    UPDATE all_auth_recipe_users
      SET primary_or_recipe_user_id = user_id
      WHERE primary_or_recipe_user_id = '0';

    UPDATE all_auth_recipe_users
      SET primary_or_recipe_user_time_joined = time_joined
      WHERE primary_or_recipe_user_time_joined = 0;

    ALTER TABLE all_auth_recipe_users
      ADD FOREIGN KEY (app_id, primary_or_recipe_user_id)
      REFERENCES app_id_to_user_id (app_id, user_id) ON DELETE CASCADE;

    ALTER TABLE all_auth_recipe_users
      ALTER primary_or_recipe_user_id DROP DEFAULT;

    ALTER TABLE app_id_to_user_id
      ADD primary_or_recipe_user_id CHAR(36) NOT NULL DEFAULT ('0');

    ALTER TABLE app_id_to_user_id
      ADD is_linked_or_is_a_primary_user BOOLEAN NOT NULL DEFAULT FALSE;

    UPDATE app_id_to_user_id
      SET primary_or_recipe_user_id = user_id
      WHERE primary_or_recipe_user_id = '0';

    ALTER TABLE app_id_to_user_id
      ADD FOREIGN KEY (app_id, primary_or_recipe_user_id)
      REFERENCES app_id_to_user_id (app_id, user_id) ON DELETE CASCADE;

    ALTER TABLE app_id_to_user_id
      ALTER primary_or_recipe_user_id DROP DEFAULT;

    DROP INDEX all_auth_recipe_users_pagination_index ON all_auth_recipe_users;

    CREATE INDEX all_auth_recipe_users_pagination_index1 ON all_auth_recipe_users (
      app_id, tenant_id, primary_or_recipe_user_time_joined DESC, primary_or_recipe_user_id DESC);

    CREATE INDEX all_auth_recipe_users_pagination_index2 ON all_auth_recipe_users (
      app_id, tenant_id, primary_or_recipe_user_time_joined ASC, primary_or_recipe_user_id DESC);

    CREATE INDEX all_auth_recipe_users_pagination_index3 ON all_auth_recipe_users (
      recipe_id, app_id, tenant_id, primary_or_recipe_user_time_joined DESC, primary_or_recipe_user_id DESC);

    CREATE INDEX all_auth_recipe_users_pagination_index4 ON all_auth_recipe_users (
      recipe_id, app_id, tenant_id, primary_or_recipe_user_time_joined ASC, primary_or_recipe_user_id DESC);

    CREATE INDEX all_auth_recipe_users_primary_user_id_index ON all_auth_recipe_users (primary_or_recipe_user_id, app_id);

    CREATE INDEX all_auth_recipe_users_recipe_id_index ON all_auth_recipe_users (app_id, recipe_id, tenant_id);

    ALTER TABLE emailpassword_pswd_reset_tokens 
      DROP FOREIGN KEY emailpassword_pswd_reset_tokens_ibfk_1;

    ALTER TABLE emailpassword_pswd_reset_tokens
      ADD FOREIGN KEY (app_id, user_id) REFERENCES app_id_to_user_id (app_id, user_id) ON DELETE CASCADE;

    ALTER TABLE emailpassword_pswd_reset_tokens ADD email VARCHAR(256);
    ```

    </details>

4. Start the new instance(s) of the core (version 7.0.0)

## [6.0.13] - 2023-09-15

- Fixes paid stats reporting for multitenancy

## [6.0.12] - 2023-09-04

- Fixes randomly occurring `serialization error for concurrent update` in `verifySession` API
- Fixes `MISSING_EE_FOLDER_ERROR` error when the core starts up with an empty database

## [6.0.11] - 2023-08-16

- Fixed feature flag cron job

## [6.0.10] - 2023-08-16

- Fixed an encoding/decoding issue for certain access token payloads

## [6.0.9] - 2023-08-14

- Now using decimal notation to add numbers into the access token payload (instead of scientific notation)

## [6.0.8] - 2023-08-01

- Fixes CUD validation starting with number.

## [6.0.7] - 2023-07-28

- Fixes session removing for user with useridmapping when disassociating from tenant.
- Fixes issue with access token migration from version v1 and v2

## [6.0.6] - 2023-07-24

- Adds all ee features enabled for in memory database.

## [6.0.5] - 2023-07-20

- Fixes logging issue in API call where it used to print out the root CUD tenant info when querying with a tenant
  that does not exist.

## [6.0.4] - 2023-07-13

- Fixes tenant prefix in stack trace log
- `supertokens_default_cdi_version` config renamed to `supertokens_max_cdi_version`
- Fixes `/apiversion` GET to return versions until `supertokens_max_cdi_version` if set
- Fixes `/recipe/multitenancy/tenant` GET to return `TENANT_NOT_FOUND_ERROR` with 200 status when tenant was not found

## [6.0.3] - 2023-07-11

- Fixes duplicate users in users search queries when user is associated to multiple tenants
- Fixes wrong tenant id in logging for `APIKeyUnauthorisedException`

## [6.0.2] - 2023-07-04

- Fixes some of the session APIs to return `tenantId`
- argon and bcrypt related configs are now configurable only from config.yaml
- `ip_allow_regex` and `ip_deny_regex` are now protected properties for SaaS
- `hello` is disallowed as a tenantId
- creation of apps enables all recipes by default but not during creation of tenant

## [6.0.1]

- Fixes `Invalid API key` issue on hello API
- Fixes `CreateOrUpdateThirdPartyConfigAPI` as per CDI 3.0
- Fixes `sessionHandle` to include tenant information and the related APIs are now app specific
- Updated GET `/appid-<appId>/<tenantId>/recipe/session/user`
    - Adds `fetchAcrossAllTenants` with default `true` - controls fetching of sessions across all tenants or only a
      particular tenant
- Updated POST `/appid-<appId>/<tenantId>/recipe/session/remove`
    - Adds `revokeAcrossAllTenants` with default `true` - controls revoking of sessions across all tenants or only a
      particular tenant
- Updated telemetry to send `connectionUriDomain`, `appId` and `mau` information
- Updated feature flag stats to report `usersCount` per tenant

## [6.0.0] - 2023-06-02

### Adds

- Support for multitenancy.
- New config `supertokens_saas_secret` added to support multitenancy in SaaS mode.
- New config `supertokens_default_cdi_version` is added to specify the version of CDI core must assume when the version
  is not specified in the request. If this config is not specified, the core will assume the latest version.

### Fixes

- Fixes an issue where session verification would fail for JWTs created using the JWT recipe

### Changes

- Modifies the `/recipe/dashboard/session/verify` API to include the user's email in the response
- Support for multitenancy
    - New APIs to manage apps and tenants
        - `/recipe/multitenancy/connectionuridomain` PUT
        - `/recipe/multitenancy/connectionuridomain/remove` POST
        - `/recipe/multitenancy/connectionuridomain/list` GET
        - `/recipe/multitenancy/app` PUT
        - `/recipe/multitenancy/app/remove` POST
        - `/recipe/multitenancy/app/list` GET
        - `/appid-<appid>/recipe/multitenancy/tenant` PUT
        - `/appid-<appid>/<tenantid>/recipe/multitenancy/tenant` GET
        - `/appid-<appid>/recipe/multitenancy/tenant/remove` POST
        - `/appid-<appid>/recipe/multitenancy/tenant/list` GET
        - `/appid-<appid>/recipe/multitenancy/config/thirdparty` PUT
        - `/appid-<appid>/recipe/multitenancy/config/thirdparty/remove` POST
        - `/appid-<appid>/<tenantid>/recipe/multitenancy/tenant/user` POST
        - `/appid-<appid>/<tenantid>/recipe/multitenancy/tenant/user/remove` POST
    - API paths can be prefixed with `/appid-<appid>/<tenantid>` to perform app or tenant specific operations.

### Migration steps for SQL

1. Ensure that the core is already upgraded to version 5.0.0 (CDI version 2.21)
2. Stop the core instance(s)
3. Run the migration script

    <details>

    <summary>If using PostgreSQL</summary>

   #### Run the following SQL script

    ```sql
    -- General Tables

    CREATE TABLE IF NOT EXISTS apps  (
      app_id VARCHAR(64) NOT NULL DEFAULT 'public',
      created_at_time BIGINT,
      CONSTRAINT apps_pkey PRIMARY KEY(app_id)
    );

    INSERT INTO apps (app_id, created_at_time) 
      VALUES ('public', 0) ON CONFLICT DO NOTHING;

    ------------------------------------------------------------

    CREATE TABLE IF NOT EXISTS tenants (
      app_id VARCHAR(64) NOT NULL DEFAULT 'public',
      tenant_id VARCHAR(64) NOT NULL DEFAULT 'public',
      created_at_time BIGINT ,
      CONSTRAINT tenants_pkey
        PRIMARY KEY (app_id, tenant_id),
      CONSTRAINT tenants_app_id_fkey FOREIGN KEY(app_id)
        REFERENCES apps (app_id) ON DELETE CASCADE
    );

    INSERT INTO tenants (app_id, tenant_id, created_at_time) 
      VALUES ('public', 'public', 0) ON CONFLICT DO NOTHING;

    CREATE INDEX IF NOT EXISTS tenants_app_id_index ON tenants (app_id);

    ------------------------------------------------------------

    ALTER TABLE key_value
      ADD COLUMN IF NOT EXISTS app_id VARCHAR(64) DEFAULT 'public',
      ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) DEFAULT 'public';

    ALTER TABLE key_value
      DROP CONSTRAINT key_value_pkey;

    ALTER TABLE key_value
      ADD CONSTRAINT key_value_pkey 
        PRIMARY KEY (app_id, tenant_id, name);

    ALTER TABLE key_value
      DROP CONSTRAINT IF EXISTS key_value_tenant_id_fkey;

    ALTER TABLE key_value
      ADD CONSTRAINT key_value_tenant_id_fkey 
        FOREIGN KEY (app_id, tenant_id)
        REFERENCES tenants (app_id, tenant_id) ON DELETE CASCADE;

    CREATE INDEX IF NOT EXISTS key_value_tenant_id_index ON key_value (app_id, tenant_id);

    ------------------------------------------------------------

    CREATE TABLE IF NOT EXISTS app_id_to_user_id (
      app_id VARCHAR(64) NOT NULL DEFAULT 'public',
      user_id CHAR(36) NOT NULL,
      recipe_id VARCHAR(128) NOT NULL,
      CONSTRAINT app_id_to_user_id_pkey
        PRIMARY KEY (app_id, user_id),
      CONSTRAINT app_id_to_user_id_app_id_fkey
        FOREIGN KEY(app_id) REFERENCES apps (app_id) ON DELETE CASCADE
    );

    INSERT INTO app_id_to_user_id (user_id, recipe_id) 
      SELECT user_id, recipe_id
      FROM all_auth_recipe_users ON CONFLICT DO NOTHING;

    CREATE INDEX IF NOT EXISTS app_id_to_user_id_app_id_index ON app_id_to_user_id (app_id);

    ------------------------------------------------------------

    ALTER TABLE all_auth_recipe_users
      ADD COLUMN IF NOT EXISTS app_id VARCHAR(64) DEFAULT 'public',
      ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) DEFAULT 'public';

    ALTER TABLE all_auth_recipe_users
      DROP CONSTRAINT all_auth_recipe_users_pkey CASCADE;

    ALTER TABLE all_auth_recipe_users
      ADD CONSTRAINT all_auth_recipe_users_pkey 
        PRIMARY KEY (app_id, tenant_id, user_id);

    ALTER TABLE all_auth_recipe_users
      DROP CONSTRAINT IF EXISTS all_auth_recipe_users_tenant_id_fkey;

    ALTER TABLE all_auth_recipe_users
      ADD CONSTRAINT all_auth_recipe_users_tenant_id_fkey 
        FOREIGN KEY (app_id, tenant_id)
        REFERENCES tenants (app_id, tenant_id) ON DELETE CASCADE;

    ALTER TABLE all_auth_recipe_users
      DROP CONSTRAINT IF EXISTS all_auth_recipe_users_user_id_fkey;

    ALTER TABLE all_auth_recipe_users
      ADD CONSTRAINT all_auth_recipe_users_user_id_fkey 
        FOREIGN KEY (app_id, user_id)
        REFERENCES app_id_to_user_id (app_id, user_id) ON DELETE CASCADE;

    DROP INDEX all_auth_recipe_users_pagination_index;

    CREATE INDEX all_auth_recipe_users_pagination_index ON all_auth_recipe_users (time_joined DESC, user_id DESC, tenant_id DESC, app_id DESC);

    CREATE INDEX IF NOT EXISTS all_auth_recipe_user_id_index ON all_auth_recipe_users (app_id, user_id);

    CREATE INDEX IF NOT EXISTS all_auth_recipe_tenant_id_index ON all_auth_recipe_users (app_id, tenant_id);

    -- Multitenancy

    CREATE TABLE IF NOT EXISTS tenant_configs (
      connection_uri_domain VARCHAR(256) DEFAULT '',
      app_id VARCHAR(64) DEFAULT 'public',
      tenant_id VARCHAR(64) DEFAULT 'public',
      core_config TEXT,
      email_password_enabled BOOLEAN,
      passwordless_enabled BOOLEAN,
      third_party_enabled BOOLEAN,
      CONSTRAINT tenant_configs_pkey
        PRIMARY KEY (connection_uri_domain, app_id, tenant_id)
    );

    ------------------------------------------------------------

    CREATE TABLE IF NOT EXISTS tenant_thirdparty_providers (
      connection_uri_domain VARCHAR(256) DEFAULT '',
      app_id VARCHAR(64) DEFAULT 'public',
      tenant_id VARCHAR(64) DEFAULT 'public',
      third_party_id VARCHAR(28) NOT NULL,
      name VARCHAR(64),
      authorization_endpoint TEXT,
      authorization_endpoint_query_params TEXT,
      token_endpoint TEXT,
      token_endpoint_body_params TEXT,
      user_info_endpoint TEXT,
      user_info_endpoint_query_params TEXT,
      user_info_endpoint_headers TEXT,
      jwks_uri TEXT,
      oidc_discovery_endpoint TEXT,
      require_email BOOLEAN,
      user_info_map_from_id_token_payload_user_id VARCHAR(64),
      user_info_map_from_id_token_payload_email VARCHAR(64),
      user_info_map_from_id_token_payload_email_verified VARCHAR(64),
      user_info_map_from_user_info_endpoint_user_id VARCHAR(64),
      user_info_map_from_user_info_endpoint_email VARCHAR(64),
      user_info_map_from_user_info_endpoint_email_verified VARCHAR(64),
      CONSTRAINT tenant_thirdparty_providers_pkey
        PRIMARY KEY (connection_uri_domain, app_id, tenant_id, third_party_id),
      CONSTRAINT tenant_thirdparty_providers_tenant_id_fkey
        FOREIGN KEY(connection_uri_domain, app_id, tenant_id)
        REFERENCES tenant_configs (connection_uri_domain, app_id, tenant_id) ON DELETE CASCADE
    );

    CREATE INDEX IF NOT EXISTS tenant_thirdparty_providers_tenant_id_index ON tenant_thirdparty_providers (connection_uri_domain, app_id, tenant_id);

    ------------------------------------------------------------

    CREATE TABLE IF NOT EXISTS tenant_thirdparty_provider_clients (
      connection_uri_domain VARCHAR(256) DEFAULT '',
      app_id VARCHAR(64) DEFAULT 'public',
      tenant_id VARCHAR(64) DEFAULT 'public',
      third_party_id VARCHAR(28) NOT NULL,
      client_type VARCHAR(64) NOT NULL DEFAULT '',
      client_id VARCHAR(256) NOT NULL,
      client_secret TEXT,
      scope VARCHAR(128)[],
      force_pkce BOOLEAN,
      additional_config TEXT,
      CONSTRAINT tenant_thirdparty_provider_clients_pkey
        PRIMARY KEY (connection_uri_domain, app_id, tenant_id, third_party_id, client_type),
      CONSTRAINT tenant_thirdparty_provider_clients_third_party_id_fkey
        FOREIGN KEY (connection_uri_domain, app_id, tenant_id, third_party_id)
        REFERENCES tenant_thirdparty_providers (connection_uri_domain, app_id, tenant_id, third_party_id) ON DELETE CASCADE
    );

    CREATE INDEX IF NOT EXISTS tenant_thirdparty_provider_clients_third_party_id_index ON tenant_thirdparty_provider_clients (connection_uri_domain, app_id, tenant_id, third_party_id);

    -- Session

    ALTER TABLE session_info
      ADD COLUMN IF NOT EXISTS app_id VARCHAR(64) DEFAULT 'public',
      ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) DEFAULT 'public';

    ALTER TABLE session_info
      DROP CONSTRAINT session_info_pkey CASCADE;

    ALTER TABLE session_info
      ADD CONSTRAINT session_info_pkey 
        PRIMARY KEY (app_id, tenant_id, session_handle);

    ALTER TABLE session_info
      DROP CONSTRAINT IF EXISTS session_info_tenant_id_fkey;

    ALTER TABLE session_info
      ADD CONSTRAINT session_info_tenant_id_fkey 
        FOREIGN KEY (app_id, tenant_id)
        REFERENCES tenants (app_id, tenant_id) ON DELETE CASCADE;

    CREATE INDEX IF NOT EXISTS session_expiry_index ON session_info (expires_at);

    CREATE INDEX IF NOT EXISTS session_info_tenant_id_index ON session_info (app_id, tenant_id);

    ------------------------------------------------------------

    ALTER TABLE session_access_token_signing_keys
      ADD COLUMN IF NOT EXISTS app_id VARCHAR(64) DEFAULT 'public';

    ALTER TABLE session_access_token_signing_keys
      DROP CONSTRAINT session_access_token_signing_keys_pkey CASCADE;

    ALTER TABLE session_access_token_signing_keys
      ADD CONSTRAINT session_access_token_signing_keys_pkey 
        PRIMARY KEY (app_id, created_at_time);

    ALTER TABLE session_access_token_signing_keys
      DROP CONSTRAINT IF EXISTS session_access_token_signing_keys_app_id_fkey;

    ALTER TABLE session_access_token_signing_keys
      ADD CONSTRAINT session_access_token_signing_keys_app_id_fkey 
        FOREIGN KEY (app_id)
        REFERENCES apps (app_id) ON DELETE CASCADE;

    CREATE INDEX IF NOT EXISTS access_token_signing_keys_app_id_index ON session_access_token_signing_keys (app_id);

    -- JWT

    ALTER TABLE jwt_signing_keys
      ADD COLUMN IF NOT EXISTS app_id VARCHAR(64) DEFAULT 'public';

    ALTER TABLE jwt_signing_keys
      DROP CONSTRAINT jwt_signing_keys_pkey CASCADE;

    ALTER TABLE jwt_signing_keys
      ADD CONSTRAINT jwt_signing_keys_pkey 
        PRIMARY KEY (app_id, key_id);

    ALTER TABLE jwt_signing_keys
      DROP CONSTRAINT IF EXISTS jwt_signing_keys_app_id_fkey;

    ALTER TABLE jwt_signing_keys
      ADD CONSTRAINT jwt_signing_keys_app_id_fkey 
        FOREIGN KEY (app_id)
        REFERENCES apps (app_id) ON DELETE CASCADE;

    CREATE INDEX IF NOT EXISTS jwt_signing_keys_app_id_index ON jwt_signing_keys (app_id);

    -- EmailVerification

    ALTER TABLE emailverification_verified_emails
      ADD COLUMN IF NOT EXISTS app_id VARCHAR(64) DEFAULT 'public';

    ALTER TABLE emailverification_verified_emails
      DROP CONSTRAINT emailverification_verified_emails_pkey CASCADE;

    ALTER TABLE emailverification_verified_emails
      ADD CONSTRAINT emailverification_verified_emails_pkey 
        PRIMARY KEY (app_id, user_id, email);

    ALTER TABLE emailverification_verified_emails
      DROP CONSTRAINT IF EXISTS emailverification_verified_emails_app_id_fkey;

    ALTER TABLE emailverification_verified_emails
      ADD CONSTRAINT emailverification_verified_emails_app_id_fkey 
        FOREIGN KEY (app_id)
        REFERENCES apps (app_id) ON DELETE CASCADE;

    CREATE INDEX IF NOT EXISTS emailverification_verified_emails_app_id_index ON emailverification_verified_emails (app_id);

    ------------------------------------------------------------

    ALTER TABLE emailverification_tokens
      ADD COLUMN IF NOT EXISTS app_id VARCHAR(64) DEFAULT 'public',
      ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) DEFAULT 'public';

    ALTER TABLE emailverification_tokens
      DROP CONSTRAINT emailverification_tokens_pkey CASCADE;

    ALTER TABLE emailverification_tokens
      ADD CONSTRAINT emailverification_tokens_pkey 
        PRIMARY KEY (app_id, tenant_id, user_id, email, token);

    ALTER TABLE emailverification_tokens
      DROP CONSTRAINT IF EXISTS emailverification_tokens_tenant_id_fkey;

    ALTER TABLE emailverification_tokens
      ADD CONSTRAINT emailverification_tokens_tenant_id_fkey 
        FOREIGN KEY (app_id, tenant_id)
        REFERENCES tenants (app_id, tenant_id) ON DELETE CASCADE;

    CREATE INDEX IF NOT EXISTS emailverification_tokens_tenant_id_index ON emailverification_tokens (app_id, tenant_id);

    -- EmailPassword

    ALTER TABLE emailpassword_users
      ADD COLUMN IF NOT EXISTS app_id VARCHAR(64) DEFAULT 'public';

    ALTER TABLE emailpassword_users
      DROP CONSTRAINT emailpassword_users_pkey CASCADE;

    ALTER TABLE emailpassword_users
      DROP CONSTRAINT IF EXISTS emailpassword_users_email_key CASCADE;

    ALTER TABLE emailpassword_users
      ADD CONSTRAINT emailpassword_users_pkey 
        PRIMARY KEY (app_id, user_id);

    ALTER TABLE emailpassword_users
      DROP CONSTRAINT IF EXISTS emailpassword_users_user_id_fkey;

    ALTER TABLE emailpassword_users
      ADD CONSTRAINT emailpassword_users_user_id_fkey 
        FOREIGN KEY (app_id, user_id)
        REFERENCES app_id_to_user_id (app_id, user_id) ON DELETE CASCADE;

    ------------------------------------------------------------

    CREATE TABLE IF NOT EXISTS emailpassword_user_to_tenant (
      app_id VARCHAR(64) DEFAULT 'public',
      tenant_id VARCHAR(64) DEFAULT 'public',
      user_id CHAR(36) NOT NULL,
      email VARCHAR(256) NOT NULL,
      CONSTRAINT emailpassword_user_to_tenant_email_key
        UNIQUE (app_id, tenant_id, email),
      CONSTRAINT emailpassword_user_to_tenant_pkey
        PRIMARY KEY (app_id, tenant_id, user_id),
      CONSTRAINT emailpassword_user_to_tenant_user_id_fkey
        FOREIGN KEY (app_id, tenant_id, user_id)
        REFERENCES all_auth_recipe_users (app_id, tenant_id, user_id) ON DELETE CASCADE
    );

    ALTER TABLE emailpassword_user_to_tenant
      DROP CONSTRAINT IF EXISTS emailpassword_user_to_tenant_email_key;

    ALTER TABLE emailpassword_user_to_tenant
      ADD CONSTRAINT emailpassword_user_to_tenant_email_key
        UNIQUE (app_id, tenant_id, email);

    ALTER TABLE emailpassword_user_to_tenant
      DROP CONSTRAINT IF EXISTS emailpassword_user_to_tenant_user_id_fkey;

    ALTER TABLE emailpassword_user_to_tenant
      ADD CONSTRAINT emailpassword_user_to_tenant_user_id_fkey
        FOREIGN KEY (app_id, tenant_id, user_id)
        REFERENCES all_auth_recipe_users (app_id, tenant_id, user_id) ON DELETE CASCADE;

    INSERT INTO emailpassword_user_to_tenant (user_id, email)
      SELECT user_id, email FROM emailpassword_users ON CONFLICT DO NOTHING;

    ------------------------------------------------------------

    ALTER TABLE emailpassword_pswd_reset_tokens
      ADD COLUMN IF NOT EXISTS app_id VARCHAR(64) DEFAULT 'public';

    ALTER TABLE emailpassword_pswd_reset_tokens
      DROP CONSTRAINT emailpassword_pswd_reset_tokens_pkey CASCADE;

    ALTER TABLE emailpassword_pswd_reset_tokens
      ADD CONSTRAINT emailpassword_pswd_reset_tokens_pkey 
        PRIMARY KEY (app_id, user_id, token);

    ALTER TABLE emailpassword_pswd_reset_tokens
      DROP CONSTRAINT IF EXISTS emailpassword_pswd_reset_tokens_user_id_fkey;

    ALTER TABLE emailpassword_pswd_reset_tokens
      ADD CONSTRAINT emailpassword_pswd_reset_tokens_user_id_fkey 
        FOREIGN KEY (app_id, user_id)
        REFERENCES emailpassword_users (app_id, user_id) ON DELETE CASCADE;

    CREATE INDEX IF NOT EXISTS emailpassword_pswd_reset_tokens_user_id_index ON emailpassword_pswd_reset_tokens (app_id, user_id);

    -- Passwordless

    ALTER TABLE passwordless_users
      ADD COLUMN IF NOT EXISTS app_id VARCHAR(64) DEFAULT 'public';

    ALTER TABLE passwordless_users
      DROP CONSTRAINT passwordless_users_pkey CASCADE;

    ALTER TABLE passwordless_users
      ADD CONSTRAINT passwordless_users_pkey 
        PRIMARY KEY (app_id, user_id);

    ALTER TABLE passwordless_users
      DROP CONSTRAINT IF EXISTS passwordless_users_email_key;

    ALTER TABLE passwordless_users
      DROP CONSTRAINT IF EXISTS passwordless_users_phone_number_key;

    ALTER TABLE passwordless_users
      DROP CONSTRAINT IF EXISTS passwordless_users_user_id_fkey;

    ALTER TABLE passwordless_users
      ADD CONSTRAINT passwordless_users_user_id_fkey 
        FOREIGN KEY (app_id, user_id)
        REFERENCES app_id_to_user_id (app_id, user_id) ON DELETE CASCADE;

    ------------------------------------------------------------

    CREATE TABLE IF NOT EXISTS passwordless_user_to_tenant (
      app_id VARCHAR(64) DEFAULT 'public',
      tenant_id VARCHAR(64) DEFAULT 'public',
      user_id CHAR(36) NOT NULL,
      email VARCHAR(256),
      phone_number VARCHAR(256),
      CONSTRAINT passwordless_user_to_tenant_email_key
        UNIQUE (app_id, tenant_id, email),
      CONSTRAINT passwordless_user_to_tenant_phone_number_key
        UNIQUE (app_id, tenant_id, phone_number),
      CONSTRAINT passwordless_user_to_tenant_pkey
        PRIMARY KEY (app_id, tenant_id, user_id),
      CONSTRAINT passwordless_user_to_tenant_user_id_fkey
        FOREIGN KEY (app_id, tenant_id, user_id)
        REFERENCES all_auth_recipe_users (app_id, tenant_id, user_id) ON DELETE CASCADE
    );

    ALTER TABLE passwordless_user_to_tenant
      DROP CONSTRAINT IF EXISTS passwordless_user_to_tenant_user_id_fkey;

    ALTER TABLE passwordless_user_to_tenant
      ADD CONSTRAINT passwordless_user_to_tenant_user_id_fkey
        FOREIGN KEY (app_id, tenant_id, user_id)
        REFERENCES all_auth_recipe_users (app_id, tenant_id, user_id) ON DELETE CASCADE;

    INSERT INTO passwordless_user_to_tenant (user_id, email, phone_number)
      SELECT user_id, email, phone_number FROM passwordless_users ON CONFLICT DO NOTHING;

    ------------------------------------------------------------

    ALTER TABLE passwordless_devices
      ADD COLUMN IF NOT EXISTS app_id VARCHAR(64) DEFAULT 'public',
      ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) DEFAULT 'public';

    ALTER TABLE passwordless_devices
      DROP CONSTRAINT passwordless_devices_pkey CASCADE;

    ALTER TABLE passwordless_devices
      ADD CONSTRAINT passwordless_devices_pkey 
        PRIMARY KEY (app_id, tenant_id, device_id_hash);

    ALTER TABLE passwordless_devices
      DROP CONSTRAINT IF EXISTS passwordless_devices_tenant_id_fkey;

    ALTER TABLE passwordless_devices
      ADD CONSTRAINT passwordless_devices_tenant_id_fkey 
        FOREIGN KEY (app_id, tenant_id)
        REFERENCES tenants (app_id, tenant_id) ON DELETE CASCADE;

    DROP INDEX IF EXISTS passwordless_devices_email_index;

    CREATE INDEX IF NOT EXISTS passwordless_devices_email_index ON passwordless_devices (app_id, tenant_id, email);

    DROP INDEX IF EXISTS passwordless_devices_phone_number_index;

    CREATE INDEX IF NOT EXISTS passwordless_devices_phone_number_index ON passwordless_devices (app_id, tenant_id, phone_number);

    CREATE INDEX IF NOT EXISTS passwordless_devices_tenant_id_index ON passwordless_devices (app_id, tenant_id);

    ------------------------------------------------------------

    ALTER TABLE passwordless_codes
      ADD COLUMN IF NOT EXISTS app_id VARCHAR(64) DEFAULT 'public',
      ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) DEFAULT 'public';

    ALTER TABLE passwordless_codes
      DROP CONSTRAINT passwordless_codes_pkey CASCADE;

    ALTER TABLE passwordless_codes
      ADD CONSTRAINT passwordless_codes_pkey 
        PRIMARY KEY (app_id, tenant_id, code_id);

    ALTER TABLE passwordless_codes
      DROP CONSTRAINT IF EXISTS passwordless_codes_device_id_hash_fkey;

    ALTER TABLE passwordless_codes
      ADD CONSTRAINT passwordless_codes_device_id_hash_fkey 
        FOREIGN KEY (app_id, tenant_id, device_id_hash)
        REFERENCES passwordless_devices (app_id, tenant_id, device_id_hash) ON DELETE CASCADE;

    ALTER TABLE passwordless_codes
      DROP CONSTRAINT passwordless_codes_link_code_hash_key;

    ALTER TABLE passwordless_codes
      DROP CONSTRAINT IF EXISTS passwordless_codes_link_code_hash_key;

    ALTER TABLE passwordless_codes
      ADD CONSTRAINT passwordless_codes_link_code_hash_key
        UNIQUE (app_id, tenant_id, link_code_hash);

    DROP INDEX IF EXISTS passwordless_codes_created_at_index;

    CREATE INDEX IF NOT EXISTS passwordless_codes_created_at_index ON passwordless_codes (app_id, tenant_id, created_at);

    DROP INDEX IF EXISTS passwordless_codes_device_id_hash_index;
    CREATE INDEX IF NOT EXISTS passwordless_codes_device_id_hash_index ON passwordless_codes (app_id, tenant_id, device_id_hash);

    -- ThirdParty

    ALTER TABLE thirdparty_users
      ADD COLUMN IF NOT EXISTS app_id VARCHAR(64) DEFAULT 'public';

    ALTER TABLE thirdparty_users
      DROP CONSTRAINT thirdparty_users_pkey CASCADE;

    ALTER TABLE thirdparty_users
      DROP CONSTRAINT IF EXISTS thirdparty_users_user_id_key CASCADE;

    ALTER TABLE thirdparty_users
      ADD CONSTRAINT thirdparty_users_pkey 
        PRIMARY KEY (app_id, user_id);

    ALTER TABLE thirdparty_users
      DROP CONSTRAINT IF EXISTS thirdparty_users_user_id_fkey;

    ALTER TABLE thirdparty_users
      ADD CONSTRAINT thirdparty_users_user_id_fkey 
        FOREIGN KEY (app_id, user_id)
        REFERENCES app_id_to_user_id (app_id, user_id) ON DELETE CASCADE;

    DROP INDEX IF EXISTS thirdparty_users_thirdparty_user_id_index;

    CREATE INDEX IF NOT EXISTS thirdparty_users_thirdparty_user_id_index ON thirdparty_users (app_id, third_party_id, third_party_user_id);

    DROP INDEX IF EXISTS thirdparty_users_email_index;

    CREATE INDEX IF NOT EXISTS thirdparty_users_email_index ON thirdparty_users (app_id, email);

    ------------------------------------------------------------

    CREATE TABLE IF NOT EXISTS thirdparty_user_to_tenant (
      app_id VARCHAR(64) DEFAULT 'public',
      tenant_id VARCHAR(64) DEFAULT 'public',
      user_id CHAR(36) NOT NULL,
      third_party_id VARCHAR(28) NOT NULL,
      third_party_user_id VARCHAR(256) NOT NULL,
      CONSTRAINT thirdparty_user_to_tenant_third_party_user_id_key
        UNIQUE (app_id, tenant_id, third_party_id, third_party_user_id),
      CONSTRAINT thirdparty_user_to_tenant_pkey
        PRIMARY KEY (app_id, tenant_id, user_id),
      CONSTRAINT thirdparty_user_to_tenant_user_id_fkey
        FOREIGN KEY (app_id, tenant_id, user_id)
        REFERENCES all_auth_recipe_users (app_id, tenant_id, user_id) ON DELETE CASCADE
    );

    ALTER TABLE thirdparty_user_to_tenant
      DROP CONSTRAINT IF EXISTS thirdparty_user_to_tenant_third_party_user_id_key;

    ALTER TABLE thirdparty_user_to_tenant
      ADD CONSTRAINT thirdparty_user_to_tenant_third_party_user_id_key
        UNIQUE (app_id, tenant_id, third_party_id, third_party_user_id);

    ALTER TABLE thirdparty_user_to_tenant
      DROP CONSTRAINT IF EXISTS thirdparty_user_to_tenant_user_id_fkey;

    ALTER TABLE thirdparty_user_to_tenant
      ADD CONSTRAINT thirdparty_user_to_tenant_user_id_fkey
        FOREIGN KEY (app_id, tenant_id, user_id)
        REFERENCES all_auth_recipe_users (app_id, tenant_id, user_id) ON DELETE CASCADE;

    INSERT INTO thirdparty_user_to_tenant (user_id, third_party_id, third_party_user_id)
      SELECT user_id, third_party_id, third_party_user_id FROM thirdparty_users ON CONFLICT DO NOTHING;

    -- UserIdMapping

    ALTER TABLE userid_mapping
      ADD COLUMN IF NOT EXISTS app_id VARCHAR(64) DEFAULT 'public';

    ALTER TABLE userid_mapping
      DROP CONSTRAINT IF EXISTS userid_mapping_pkey CASCADE;

    ALTER TABLE userid_mapping
      ADD CONSTRAINT userid_mapping_pkey 
        PRIMARY KEY (app_id, supertokens_user_id, external_user_id);

    ALTER TABLE userid_mapping
      DROP CONSTRAINT IF EXISTS userid_mapping_supertokens_user_id_key;

    ALTER TABLE userid_mapping
      ADD CONSTRAINT userid_mapping_supertokens_user_id_key
        UNIQUE (app_id, supertokens_user_id);

    ALTER TABLE userid_mapping
      DROP CONSTRAINT IF EXISTS userid_mapping_external_user_id_key;

    ALTER TABLE userid_mapping
      ADD CONSTRAINT userid_mapping_external_user_id_key
        UNIQUE (app_id, external_user_id);

    ALTER TABLE userid_mapping
      DROP CONSTRAINT IF EXISTS userid_mapping_supertokens_user_id_fkey;

    ALTER TABLE userid_mapping
      ADD CONSTRAINT userid_mapping_supertokens_user_id_fkey 
        FOREIGN KEY (app_id, supertokens_user_id)
        REFERENCES app_id_to_user_id (app_id, user_id) ON DELETE CASCADE;

    CREATE INDEX IF NOT EXISTS userid_mapping_supertokens_user_id_index ON userid_mapping (app_id, supertokens_user_id);

    -- UserRoles

    ALTER TABLE roles
      ADD COLUMN IF NOT EXISTS app_id VARCHAR(64) DEFAULT 'public';

    ALTER TABLE roles
      DROP CONSTRAINT roles_pkey CASCADE;

    ALTER TABLE roles
      ADD CONSTRAINT roles_pkey 
        PRIMARY KEY (app_id, role);

    ALTER TABLE roles
      DROP CONSTRAINT IF EXISTS roles_app_id_fkey;

    ALTER TABLE roles
      ADD CONSTRAINT roles_app_id_fkey 
        FOREIGN KEY (app_id)
        REFERENCES apps (app_id) ON DELETE CASCADE;

    CREATE INDEX IF NOT EXISTS roles_app_id_index ON roles (app_id);

    ------------------------------------------------------------

    ALTER TABLE role_permissions
      ADD COLUMN IF NOT EXISTS app_id VARCHAR(64) DEFAULT 'public';

    ALTER TABLE role_permissions
      DROP CONSTRAINT role_permissions_pkey CASCADE;

    ALTER TABLE role_permissions
      ADD CONSTRAINT role_permissions_pkey 
        PRIMARY KEY (app_id, role, permission);

    ALTER TABLE role_permissions
      DROP CONSTRAINT IF EXISTS role_permissions_role_fkey;

    ALTER TABLE role_permissions
      ADD CONSTRAINT role_permissions_role_fkey 
        FOREIGN KEY (app_id, role)
        REFERENCES roles (app_id, role) ON DELETE CASCADE;

    DROP INDEX IF EXISTS role_permissions_permission_index;

    CREATE INDEX IF NOT EXISTS role_permissions_permission_index ON role_permissions (app_id, permission);

    CREATE INDEX IF NOT EXISTS role_permissions_role_index ON role_permissions (app_id, role);

    ------------------------------------------------------------

    ALTER TABLE user_roles
      ADD COLUMN IF NOT EXISTS app_id VARCHAR(64) DEFAULT 'public',
      ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) DEFAULT 'public';

    ALTER TABLE user_roles
      DROP CONSTRAINT user_roles_pkey CASCADE;

    ALTER TABLE user_roles
      ADD CONSTRAINT user_roles_pkey 
        PRIMARY KEY (app_id, tenant_id, user_id, role);

    ALTER TABLE user_roles
      DROP CONSTRAINT IF EXISTS user_roles_tenant_id_fkey;

    ALTER TABLE user_roles
      ADD CONSTRAINT user_roles_tenant_id_fkey 
        FOREIGN KEY (app_id, tenant_id)
        REFERENCES tenants (app_id, tenant_id) ON DELETE CASCADE;

    ALTER TABLE user_roles
      DROP CONSTRAINT IF EXISTS user_roles_role_fkey;

    ALTER TABLE user_roles
      ADD CONSTRAINT user_roles_role_fkey 
        FOREIGN KEY (app_id, role)
        REFERENCES roles (app_id, role) ON DELETE CASCADE;

    DROP INDEX IF EXISTS user_roles_role_index;

    CREATE INDEX IF NOT EXISTS user_roles_role_index ON user_roles (app_id, tenant_id, role);

    CREATE INDEX IF NOT EXISTS user_roles_tenant_id_index ON user_roles (app_id, tenant_id);

    CREATE INDEX IF NOT EXISTS user_roles_app_id_role_index ON user_roles (app_id, role);

    -- UserMetadata

    ALTER TABLE user_metadata
      ADD COLUMN IF NOT EXISTS app_id VARCHAR(64) DEFAULT 'public';

    ALTER TABLE user_metadata
      DROP CONSTRAINT user_metadata_pkey CASCADE;

    ALTER TABLE user_metadata
      ADD CONSTRAINT user_metadata_pkey 
        PRIMARY KEY (app_id, user_id);

    ALTER TABLE user_metadata
      DROP CONSTRAINT IF EXISTS user_metadata_app_id_fkey;

    ALTER TABLE user_metadata
      ADD CONSTRAINT user_metadata_app_id_fkey 
        FOREIGN KEY (app_id)
        REFERENCES apps (app_id) ON DELETE CASCADE;

    CREATE INDEX IF NOT EXISTS user_metadata_app_id_index ON user_metadata (app_id);

    -- Dashboard

    ALTER TABLE dashboard_users
      ADD COLUMN IF NOT EXISTS app_id VARCHAR(64) DEFAULT 'public';

    ALTER TABLE dashboard_users
      DROP CONSTRAINT dashboard_users_pkey CASCADE;

    ALTER TABLE dashboard_users
      ADD CONSTRAINT dashboard_users_pkey 
        PRIMARY KEY (app_id, user_id);

    ALTER TABLE dashboard_users
      DROP CONSTRAINT IF EXISTS dashboard_users_email_key;

    ALTER TABLE dashboard_users
      ADD CONSTRAINT dashboard_users_email_key
        UNIQUE (app_id, email);

    ALTER TABLE dashboard_users
      DROP CONSTRAINT IF EXISTS dashboard_users_app_id_fkey;

    ALTER TABLE dashboard_users
      ADD CONSTRAINT dashboard_users_app_id_fkey 
        FOREIGN KEY (app_id)
        REFERENCES apps (app_id) ON DELETE CASCADE;

    CREATE INDEX IF NOT EXISTS dashboard_users_app_id_index ON dashboard_users (app_id);

    ------------------------------------------------------------

    ALTER TABLE dashboard_user_sessions
      ADD COLUMN IF NOT EXISTS app_id VARCHAR(64) DEFAULT 'public';

    ALTER TABLE dashboard_user_sessions
      DROP CONSTRAINT dashboard_user_sessions_pkey CASCADE;

    ALTER TABLE dashboard_user_sessions
      ADD CONSTRAINT dashboard_user_sessions_pkey 
        PRIMARY KEY (app_id, session_id);

    ALTER TABLE dashboard_user_sessions
      DROP CONSTRAINT IF EXISTS dashboard_user_sessions_user_id_fkey;

    ALTER TABLE dashboard_user_sessions
      ADD CONSTRAINT dashboard_user_sessions_user_id_fkey 
        FOREIGN KEY (app_id, user_id)
        REFERENCES dashboard_users (app_id, user_id) ON DELETE CASCADE;

    CREATE INDEX IF NOT EXISTS dashboard_user_sessions_user_id_index ON dashboard_user_sessions (app_id, user_id);

    -- TOTP

    ALTER TABLE totp_users
      ADD COLUMN IF NOT EXISTS app_id VARCHAR(64) DEFAULT 'public';

    ALTER TABLE totp_users
      DROP CONSTRAINT totp_users_pkey CASCADE;

    ALTER TABLE totp_users
      ADD CONSTRAINT totp_users_pkey 
        PRIMARY KEY (app_id, user_id);

    ALTER TABLE totp_users
      DROP CONSTRAINT IF EXISTS totp_users_app_id_fkey;

    ALTER TABLE totp_users
      ADD CONSTRAINT totp_users_app_id_fkey 
        FOREIGN KEY (app_id)
        REFERENCES apps (app_id) ON DELETE CASCADE;

    CREATE INDEX IF NOT EXISTS totp_users_app_id_index ON totp_users (app_id);

    ------------------------------------------------------------

    ALTER TABLE totp_user_devices
      ADD COLUMN IF NOT EXISTS app_id VARCHAR(64) DEFAULT 'public';

    ALTER TABLE totp_user_devices
      DROP CONSTRAINT totp_user_devices_pkey;

    ALTER TABLE totp_user_devices
      ADD CONSTRAINT totp_user_devices_pkey 
        PRIMARY KEY (app_id, user_id, device_name);

    ALTER TABLE totp_user_devices
      DROP CONSTRAINT IF EXISTS totp_user_devices_user_id_fkey;

    ALTER TABLE totp_user_devices
      ADD CONSTRAINT totp_user_devices_user_id_fkey 
        FOREIGN KEY (app_id, user_id)
        REFERENCES totp_users (app_id, user_id) ON DELETE CASCADE;

    CREATE INDEX IF NOT EXISTS totp_user_devices_user_id_index ON totp_user_devices (app_id, user_id);

    ------------------------------------------------------------

    ALTER TABLE totp_used_codes
      ADD COLUMN IF NOT EXISTS app_id VARCHAR(64) DEFAULT 'public',
      ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) DEFAULT 'public';

    ALTER TABLE totp_used_codes
      DROP CONSTRAINT totp_used_codes_pkey CASCADE;

    ALTER TABLE totp_used_codes
      ADD CONSTRAINT totp_used_codes_pkey 
        PRIMARY KEY (app_id, tenant_id, user_id, created_time_ms);

    ALTER TABLE totp_used_codes
      DROP CONSTRAINT IF EXISTS totp_used_codes_user_id_fkey;

    ALTER TABLE totp_used_codes
      ADD CONSTRAINT totp_used_codes_user_id_fkey 
        FOREIGN KEY (app_id, user_id)
        REFERENCES totp_users (app_id, user_id) ON DELETE CASCADE;

    ALTER TABLE totp_used_codes
      DROP CONSTRAINT IF EXISTS totp_used_codes_tenant_id_fkey;

    ALTER TABLE totp_used_codes
      ADD CONSTRAINT totp_used_codes_tenant_id_fkey 
        FOREIGN KEY (app_id, tenant_id)
        REFERENCES tenants (app_id, tenant_id) ON DELETE CASCADE;

    DROP INDEX IF EXISTS totp_used_codes_expiry_time_ms_index;

    CREATE INDEX IF NOT EXISTS totp_used_codes_expiry_time_ms_index ON totp_used_codes (app_id, tenant_id, expiry_time_ms);

    CREATE INDEX IF NOT EXISTS totp_used_codes_user_id_index ON totp_used_codes (app_id, user_id);

    CREATE INDEX IF NOT EXISTS totp_used_codes_tenant_id_index ON totp_used_codes (app_id, tenant_id);

    -- ActiveUsers

    ALTER TABLE user_last_active
      ADD COLUMN IF NOT EXISTS app_id VARCHAR(64) DEFAULT 'public';

    ALTER TABLE user_last_active
      DROP CONSTRAINT user_last_active_pkey CASCADE;

    ALTER TABLE user_last_active
      ADD CONSTRAINT user_last_active_pkey 
        PRIMARY KEY (app_id, user_id);

    ALTER TABLE user_last_active
      DROP CONSTRAINT IF EXISTS user_last_active_app_id_fkey;

    ALTER TABLE user_last_active
      ADD CONSTRAINT user_last_active_app_id_fkey 
        FOREIGN KEY (app_id)
        REFERENCES apps (app_id) ON DELETE CASCADE;

    CREATE INDEX IF NOT EXISTS user_last_active_app_id_index ON user_last_active (app_id);

    ```

    </details>

    <details>

    <summary>If using MySQL</summary>

   #### Run the following SQL script

    ```sql
    -- helper stored procedures
    DELIMITER //

    CREATE PROCEDURE st_drop_all_fkeys()
    BEGIN
      DECLARE done INT DEFAULT FALSE;
      DECLARE dropCommand VARCHAR(255);
      DECLARE dropCur CURSOR for 
              SELECT concat('ALTER TABLE ', table_schema,'.',table_name,' DROP FOREIGN KEY ', constraint_name, ';') 
              FROM information_schema.table_constraints
              WHERE constraint_type='FOREIGN KEY' 
                  AND table_schema = DATABASE()
                  AND table_name in (
                    'all_auth_recipe_users',
                    'dashboard_user_sessions',
                    'dashboard_users',
                    'emailpassword_pswd_reset_tokens',
                    'emailpassword_users',
                    'emailverification_tokens',
                    'emailverification_verified_emails',
                    'jwt_signing_keys',
                    'key_value',
                    'passwordless_codes',
                    'passwordless_devices',
                    'passwordless_users',
                    'role_permissions',
                    'roles',
                    'session_access_token_signing_keys',
                    'session_info',
                    'thirdparty_users',
                    'totp_used_codes',
                    'totp_user_devices',
                    'totp_users',
                    'user_last_active',
                    'user_metadata',
                    'user_roles',
                    'userid_mapping'
                  );

      DECLARE CONTINUE handler for NOT found SET done = true;
        OPEN dropCur;

        read_loop: LOOP
            FETCH dropCur INTO dropCommand;
            IF done THEN
                leave read_loop;
            END IF;

            SET @sdropCommand = dropCommand;

            PREPARE dropClientUpdateKeyStmt FROM @sdropCommand;

            EXECUTE dropClientUpdateKeyStmt;

            DEALLOCATE prepare dropClientUpdateKeyStmt;
        END LOOP;

        CLOSE dropCur;
    END //

    --

    CREATE PROCEDURE st_drop_all_pkeys()
    BEGIN
      DECLARE done INT DEFAULT FALSE;
      DECLARE dropCommand VARCHAR(255);
      DECLARE dropCur CURSOR for 
              SELECT concat('ALTER TABLE ', table_schema,'.',table_name,' DROP PRIMARY KEY ', ';') 
              FROM information_schema.table_constraints
              WHERE constraint_type='PRIMARY KEY' 
                  AND table_schema = DATABASE()
                  AND table_name in (
                    'all_auth_recipe_users',
                    'dashboard_user_sessions',
                    'dashboard_users',
                    'emailpassword_pswd_reset_tokens',
                    'emailpassword_users',
                    'emailverification_tokens',
                    'emailverification_verified_emails',
                    'jwt_signing_keys',
                    'key_value',
                    'passwordless_codes',
                    'passwordless_devices',
                    'passwordless_users',
                    'role_permissions',
                    'roles',
                    'session_access_token_signing_keys',
                    'session_info',
                    'thirdparty_users',
                    'totp_used_codes',
                    'totp_user_devices',
                    'totp_users',
                    'user_last_active',
                    'user_metadata',
                    'user_roles',
                    'userid_mapping'
                  );

      DECLARE CONTINUE handler for NOT found SET done = true;
        OPEN dropCur;

        read_loop: LOOP
            FETCH dropCur INTO dropCommand;
            IF done THEN
                leave read_loop;
            END IF;

            SET @sdropCommand = dropCommand;

            PREPARE dropClientUpdateKeyStmt FROM @sdropCommand;

            EXECUTE dropClientUpdateKeyStmt;

            DEALLOCATE prepare dropClientUpdateKeyStmt;
        END LOOP;

        CLOSE dropCur;
    END //

    --

    CREATE PROCEDURE st_drop_all_keys()
    BEGIN
      DECLARE done INT DEFAULT FALSE;
      DECLARE dropCommand VARCHAR(255);
      DECLARE dropCur CURSOR for 
              SELECT concat('ALTER TABLE ', table_schema,'.',table_name,' DROP INDEX ', constraint_name, ';') 
              FROM information_schema.table_constraints
              WHERE constraint_type='UNIQUE' 
                  AND table_schema = DATABASE()
                  AND table_name in (
                    'all_auth_recipe_users',
                    'dashboard_user_sessions',
                    'dashboard_users',
                    'emailpassword_pswd_reset_tokens',
                    'emailpassword_users',
                    'emailverification_tokens',
                    'emailverification_verified_emails',
                    'jwt_signing_keys',
                    'key_value',
                    'passwordless_codes',
                    'passwordless_devices',
                    'passwordless_users',
                    'role_permissions',
                    'roles',
                    'session_access_token_signing_keys',
                    'session_info',
                    'thirdparty_users',
                    'totp_used_codes',
                    'totp_user_devices',
                    'totp_users',
                    'user_last_active',
                    'user_metadata',
                    'user_roles',
                    'userid_mapping'
                  );

      DECLARE CONTINUE handler for NOT found SET done = true;
        OPEN dropCur;

        read_loop: LOOP
            FETCH dropCur INTO dropCommand;
            IF done THEN
                leave read_loop;
            END IF;

            SET @sdropCommand = dropCommand;

            PREPARE dropClientUpdateKeyStmt FROM @sdropCommand;

            EXECUTE dropClientUpdateKeyStmt;

            DEALLOCATE prepare dropClientUpdateKeyStmt;
        END LOOP;

        CLOSE dropCur;
    END //

    --

    CREATE PROCEDURE st_drop_all_indexes()
    BEGIN
      DECLARE done INT DEFAULT FALSE;
      DECLARE dropCommand VARCHAR(255);
      DECLARE dropCur CURSOR for 
              SELECT DISTINCT concat('ALTER TABLE ', table_schema, '.', table_name, ' DROP INDEX ', index_name, ';')
              FROM information_schema.statistics
              WHERE NON_UNIQUE = 1 
                AND table_schema = database()
                AND table_name in (
                  'all_auth_recipe_users',
                  'dashboard_user_sessions',
                  'dashboard_users',
                  'emailpassword_pswd_reset_tokens',
                  'emailpassword_users',
                  'emailverification_tokens',
                  'emailverification_verified_emails',
                  'jwt_signing_keys',
                  'key_value',
                  'passwordless_codes',
                  'passwordless_devices',
                  'passwordless_users',
                  'role_permissions',
                  'roles',
                  'session_access_token_signing_keys',
                  'session_info',
                  'thirdparty_users',
                  'totp_used_codes',
                  'totp_user_devices',
                  'totp_users',
                  'user_last_active',
                  'user_metadata',
                  'user_roles',
                  'userid_mapping'
                );

      DECLARE CONTINUE handler for NOT found SET done = true;
        OPEN dropCur;

        read_loop: LOOP
            FETCH dropCur INTO dropCommand;
            IF done THEN
                leave read_loop;
            END IF;

            SET @sdropCommand = dropCommand;

            PREPARE dropClientUpdateKeyStmt FROM @sdropCommand;

            EXECUTE dropClientUpdateKeyStmt;

            DEALLOCATE prepare dropClientUpdateKeyStmt;
        END LOOP;

        CLOSE dropCur;
    END //

    --

    CREATE PROCEDURE st_add_column_if_not_exists(
    IN p_table_name varchar(50), 
    IN p_column_name varchar(50),
    IN p_column_type varchar(50),
    IN p_additional varchar(100),
    OUT p_status_message varchar(100))
        READS SQL DATA
    BEGIN
        DECLARE v_count INT;
        
        # Check wether column exist or not
        SELECT count(*) INTO v_count
        FROM information_schema.columns
        WHERE table_schema = database()
            AND table_name   = p_table_name
            AND column_name  = p_column_name;
            
        IF v_count > 0 THEN
          # Return column already exists message
          SELECT 'Column already Exists' INTO p_status_message;
        ELSE
            # Add Column and return success message
          set @ddl_addcolumn=CONCAT('ALTER TABLE ',database(),'.',p_table_name,
          ' ADD COLUMN ',p_column_name,' ',p_column_type,' ',p_additional);
        prepare add_column_sql from @ddl_addcolumn;
        execute add_column_sql;
          SELECT 'Column Successfully  Created!' INTO p_status_message;
        END IF;
    END //

    DELIMITER ;
    -- Drop constraints and indexes

    CALL st_drop_all_fkeys();
    CALL st_drop_all_keys();
    CALL st_drop_all_pkeys();
    CALL st_drop_all_indexes(); 

    -- General Tables

    CREATE TABLE IF NOT EXISTS apps  (
      app_id VARCHAR(64) NOT NULL DEFAULT 'public',
      created_at_time BIGINT UNSIGNED
    );

    ALTER TABLE apps
      ADD PRIMARY KEY(app_id);

    INSERT IGNORE INTO apps (app_id, created_at_time) 
      VALUES ('public', 0);

    --

    CREATE TABLE IF NOT EXISTS tenants (
      app_id VARCHAR(64) NOT NULL DEFAULT 'public',
      tenant_id VARCHAR(64) NOT NULL DEFAULT 'public',
      created_at_time BIGINT UNSIGNED
    );

    ALTER TABLE tenants
      ADD PRIMARY KEY(app_id, tenant_id);

    ALTER TABLE tenants
      ADD FOREIGN KEY (app_id)
        REFERENCES apps (app_id) ON DELETE CASCADE;

    INSERT IGNORE INTO tenants (app_id, tenant_id, created_at_time) 
      VALUES ('public', 'public', 0);

    --

    CALL st_add_column_if_not_exists('key_value', 'app_id', 'VARCHAR(64)', 'NOT NULL DEFAULT \'public\'', @status_message);
    CALL st_add_column_if_not_exists('key_value', 'tenant_id', 'VARCHAR(64)', 'NOT NULL DEFAULT \'public\'', @status_message);

    ALTER TABLE key_value
      ADD PRIMARY KEY (app_id, tenant_id, name);

    ALTER TABLE key_value
      ADD FOREIGN KEY (app_id, tenant_id)
        REFERENCES tenants (app_id, tenant_id) ON DELETE CASCADE;

    --

    CREATE TABLE IF NOT EXISTS app_id_to_user_id (
      app_id VARCHAR(64) NOT NULL DEFAULT 'public',
      user_id CHAR(36) NOT NULL,
      recipe_id VARCHAR(128) NOT NULL
    );

    ALTER TABLE app_id_to_user_id
      ADD PRIMARY KEY (app_id, user_id);

    ALTER TABLE app_id_to_user_id
      ADD FOREIGN KEY (app_id)
        REFERENCES apps (app_id) ON DELETE CASCADE;

    INSERT IGNORE INTO app_id_to_user_id (user_id, recipe_id) 
      SELECT user_id, recipe_id
      FROM all_auth_recipe_users;

    --

    CALL st_add_column_if_not_exists('all_auth_recipe_users', 'app_id', 'VARCHAR(64)', 'NOT NULL DEFAULT \'public\'', @status_message);
    CALL st_add_column_if_not_exists('all_auth_recipe_users', 'tenant_id', 'VARCHAR(64)', 'NOT NULL DEFAULT \'public\'', @status_message);

    ALTER TABLE all_auth_recipe_users
      ADD PRIMARY KEY (app_id, tenant_id, user_id);

    ALTER TABLE all_auth_recipe_users
      ADD FOREIGN KEY (app_id, tenant_id)
        REFERENCES tenants (app_id, tenant_id) ON DELETE CASCADE;

    ALTER TABLE all_auth_recipe_users
      ADD FOREIGN KEY (app_id, user_id)
        REFERENCES app_id_to_user_id (app_id, user_id) ON DELETE CASCADE;

    CREATE INDEX all_auth_recipe_users_pagination_index ON all_auth_recipe_users (time_joined DESC, user_id DESC, tenant_id DESC, app_id DESC);

    -- Multitenancy

    CREATE TABLE IF NOT EXISTS tenant_configs (
      connection_uri_domain VARCHAR(256) DEFAULT '',
      app_id VARCHAR(64) DEFAULT 'public',
      tenant_id VARCHAR(64) DEFAULT 'public',
      core_config TEXT,
      email_password_enabled BOOLEAN,
      passwordless_enabled BOOLEAN,
      third_party_enabled BOOLEAN
    );

    ALTER TABLE tenant_configs
      ADD PRIMARY KEY (connection_uri_domain, app_id, tenant_id);

    --

    CREATE TABLE IF NOT EXISTS tenant_thirdparty_providers (
      connection_uri_domain VARCHAR(256) DEFAULT '',
      app_id VARCHAR(64) DEFAULT 'public',
      tenant_id VARCHAR(64) DEFAULT 'public',
      third_party_id VARCHAR(28) NOT NULL,
      name VARCHAR(64),
      authorization_endpoint TEXT,
      authorization_endpoint_query_params TEXT,
      token_endpoint TEXT,
      token_endpoint_body_params TEXT,
      user_info_endpoint TEXT,
      user_info_endpoint_query_params TEXT,
      user_info_endpoint_headers TEXT,
      jwks_uri TEXT,
      oidc_discovery_endpoint TEXT,
      require_email BOOLEAN,
      user_info_map_from_id_token_payload_user_id VARCHAR(64),
      user_info_map_from_id_token_payload_email VARCHAR(64),
      user_info_map_from_id_token_payload_email_verified VARCHAR(64),
      user_info_map_from_user_info_endpoint_user_id VARCHAR(64),
      user_info_map_from_user_info_endpoint_email VARCHAR(64),
      user_info_map_from_user_info_endpoint_email_verified VARCHAR(64)
    );

    ALTER TABLE tenant_thirdparty_providers
      ADD PRIMARY KEY (connection_uri_domain, app_id, tenant_id, third_party_id);

    ALTER TABLE tenant_thirdparty_providers
      ADD FOREIGN KEY (connection_uri_domain, app_id, tenant_id)
        REFERENCES tenant_configs (connection_uri_domain, app_id, tenant_id) ON DELETE CASCADE;

    --

    CREATE TABLE IF NOT EXISTS tenant_thirdparty_provider_clients (
      connection_uri_domain VARCHAR(256) DEFAULT '',
      app_id VARCHAR(64) DEFAULT 'public',
      tenant_id VARCHAR(64) DEFAULT 'public',
      third_party_id VARCHAR(28) NOT NULL,
      client_type VARCHAR(64) NOT NULL DEFAULT '',
      client_id VARCHAR(256) NOT NULL,
      client_secret TEXT,
      scope TEXT,
      force_pkce BOOLEAN,
      additional_config TEXT
    );

    ALTER TABLE tenant_thirdparty_provider_clients
      ADD PRIMARY KEY (connection_uri_domain, app_id, tenant_id, third_party_id, client_type);

    ALTER TABLE tenant_thirdparty_provider_clients
      ADD FOREIGN KEY (connection_uri_domain, app_id, tenant_id, third_party_id)
        REFERENCES tenant_thirdparty_providers (connection_uri_domain, app_id, tenant_id, third_party_id) ON DELETE CASCADE;


    -- Session

    CALL st_add_column_if_not_exists('session_info', 'app_id', 'VARCHAR(64)', 'NOT NULL DEFAULT \'public\'', @status_message);
    CALL st_add_column_if_not_exists('session_info', 'tenant_id', 'VARCHAR(64)', 'NOT NULL DEFAULT \'public\'', @status_message);

    ALTER TABLE session_info
      ADD PRIMARY KEY (app_id, tenant_id, session_handle);

    ALTER TABLE session_info
      ADD FOREIGN KEY (app_id, tenant_id)
        REFERENCES tenants (app_id, tenant_id) ON DELETE CASCADE;

    CREATE INDEX session_expiry_index ON session_info (expires_at);

    --

    CALL st_add_column_if_not_exists('session_access_token_signing_keys', 'app_id', 'VARCHAR(64)', 'NOT NULL DEFAULT \'public\'', @status_message);

    ALTER TABLE session_access_token_signing_keys
      ADD PRIMARY KEY (app_id, created_at_time);

    ALTER TABLE session_access_token_signing_keys
      ADD FOREIGN KEY (app_id)
        REFERENCES apps (app_id) ON DELETE CASCADE;

    -- JWT

    CALL st_add_column_if_not_exists('jwt_signing_keys', 'app_id', 'VARCHAR(64)', 'NOT NULL DEFAULT \'public\'', @status_message);

    ALTER TABLE jwt_signing_keys
      ADD PRIMARY KEY (app_id, key_id);

    ALTER TABLE jwt_signing_keys
      ADD FOREIGN KEY (app_id)
        REFERENCES apps (app_id) ON DELETE CASCADE;

    -- EmailVerification

    CALL st_add_column_if_not_exists('emailverification_verified_emails', 'app_id', 'VARCHAR(64)', 'NOT NULL DEFAULT \'public\'', @status_message);

    ALTER TABLE emailverification_verified_emails
      ADD PRIMARY KEY (app_id, user_id, email);

    ALTER TABLE emailverification_verified_emails
      ADD FOREIGN KEY (app_id)
        REFERENCES apps (app_id) ON DELETE CASCADE;

    --

    CALL st_add_column_if_not_exists('emailverification_tokens', 'app_id', 'VARCHAR(64)', 'NOT NULL DEFAULT \'public\'', @status_message);
    CALL st_add_column_if_not_exists('emailverification_tokens', 'tenant_id', 'VARCHAR(64)', 'NOT NULL DEFAULT \'public\'', @status_message);

    ALTER TABLE emailverification_tokens
      ADD PRIMARY KEY (app_id, tenant_id, user_id, email, token);

    ALTER TABLE emailverification_tokens
      ADD FOREIGN KEY (app_id, tenant_id)
        REFERENCES tenants (app_id, tenant_id) ON DELETE CASCADE;

    ALTER TABLE emailverification_tokens
      ADD CONSTRAINT token UNIQUE (token);

    CREATE INDEX emailverification_tokens_index ON emailverification_tokens(token_expiry);

    -- EmailPassword

    CALL st_add_column_if_not_exists('emailpassword_users', 'app_id', 'VARCHAR(64)', 'NOT NULL DEFAULT \'public\'', @status_message);

    ALTER TABLE emailpassword_users
      ADD PRIMARY KEY (app_id, user_id);

    ALTER TABLE emailpassword_users
      ADD FOREIGN KEY (app_id, user_id)
        REFERENCES app_id_to_user_id (app_id, user_id) ON DELETE CASCADE;

    -- --

    CREATE TABLE IF NOT EXISTS emailpassword_user_to_tenant (
      app_id VARCHAR(64) DEFAULT 'public',
      tenant_id VARCHAR(64) DEFAULT 'public',
      user_id CHAR(36) NOT NULL,
      email VARCHAR(256) NOT NULL
    );

    ALTER TABLE emailpassword_user_to_tenant
      ADD PRIMARY KEY (app_id, tenant_id, user_id);

    ALTER TABLE emailpassword_user_to_tenant
      ADD CONSTRAINT email UNIQUE (app_id, tenant_id, email);

    ALTER TABLE emailpassword_user_to_tenant
      ADD CONSTRAINT FOREIGN KEY (app_id, tenant_id, user_id)
        REFERENCES all_auth_recipe_users (app_id, tenant_id, user_id) ON DELETE CASCADE;

    INSERT IGNORE INTO emailpassword_user_to_tenant (user_id, email)
      SELECT user_id, email FROM emailpassword_users;

    --

    CALL st_add_column_if_not_exists('emailpassword_pswd_reset_tokens', 'app_id', 'VARCHAR(64)', 'NOT NULL DEFAULT \'public\'', @status_message);

    ALTER TABLE emailpassword_pswd_reset_tokens
      ADD PRIMARY KEY (app_id, user_id, token);

    ALTER TABLE emailpassword_pswd_reset_tokens
      ADD FOREIGN KEY (app_id, user_id)
        REFERENCES emailpassword_users (app_id, user_id) ON DELETE CASCADE;

    ALTER TABLE emailpassword_pswd_reset_tokens
      ADD CONSTRAINT token UNIQUE (token);

    CREATE INDEX emailpassword_password_reset_token_expiry_index ON emailpassword_pswd_reset_tokens (token_expiry);

    -- Passwordless

    CALL st_add_column_if_not_exists('passwordless_users', 'app_id', 'VARCHAR(64)', 'NOT NULL DEFAULT \'public\'', @status_message);

    ALTER TABLE passwordless_users
      ADD PRIMARY KEY (app_id, user_id);

    ALTER TABLE passwordless_users
      ADD FOREIGN KEY (app_id, user_id)
        REFERENCES app_id_to_user_id (app_id, user_id) ON DELETE CASCADE;

    --

    CREATE TABLE IF NOT EXISTS passwordless_user_to_tenant (
      app_id VARCHAR(64) DEFAULT 'public',
      tenant_id VARCHAR(64) DEFAULT 'public',
      user_id CHAR(36) NOT NULL,
      email VARCHAR(256),
      phone_number VARCHAR(256)
    );

    ALTER TABLE passwordless_user_to_tenant
      ADD PRIMARY KEY (app_id, tenant_id, user_id);

    ALTER TABLE passwordless_user_to_tenant
      ADD CONSTRAINT email UNIQUE (app_id, tenant_id, email);

    ALTER TABLE passwordless_user_to_tenant
      ADD CONSTRAINT phone_number UNIQUE (app_id, tenant_id, phone_number);

    ALTER TABLE passwordless_user_to_tenant
      ADD FOREIGN KEY (app_id, tenant_id, user_id)
        REFERENCES all_auth_recipe_users (app_id, tenant_id, user_id) ON DELETE CASCADE;

    INSERT IGNORE INTO passwordless_user_to_tenant (user_id, email, phone_number)
      SELECT user_id, email, phone_number FROM passwordless_users;

    --

    CALL st_add_column_if_not_exists('passwordless_devices', 'app_id', 'VARCHAR(64)', 'NOT NULL DEFAULT \'public\'', @status_message);
    CALL st_add_column_if_not_exists('passwordless_devices', 'tenant_id', 'VARCHAR(64)', 'NOT NULL DEFAULT \'public\'', @status_message);

    ALTER TABLE passwordless_devices
      ADD PRIMARY KEY (app_id, tenant_id, device_id_hash);

    ALTER TABLE passwordless_devices
      ADD FOREIGN KEY (app_id, tenant_id)
        REFERENCES tenants (app_id, tenant_id) ON DELETE CASCADE;

    CREATE INDEX passwordless_devices_email_index ON passwordless_devices (app_id, tenant_id, email);

    CREATE INDEX passwordless_devices_phone_number_index ON passwordless_devices (app_id, tenant_id, phone_number);

    --

    CALL st_add_column_if_not_exists('passwordless_codes', 'app_id', 'VARCHAR(64)', 'NOT NULL DEFAULT \'public\'', @status_message);
    CALL st_add_column_if_not_exists('passwordless_codes', 'tenant_id', 'VARCHAR(64)', 'NOT NULL DEFAULT \'public\'', @status_message);

    ALTER TABLE passwordless_codes
      ADD PRIMARY KEY (app_id, tenant_id, code_id);

    ALTER TABLE passwordless_codes
      ADD FOREIGN KEY (app_id, tenant_id, device_id_hash)
        REFERENCES passwordless_devices (app_id, tenant_id, device_id_hash) ON DELETE CASCADE;

    ALTER TABLE passwordless_codes
      ADD CONSTRAINT link_code_hash
        UNIQUE (app_id, tenant_id, link_code_hash);

    CREATE INDEX passwordless_codes_created_at_index ON passwordless_codes (app_id, tenant_id, created_at);

    -- ThirdParty

    CALL st_add_column_if_not_exists('thirdparty_users', 'app_id', 'VARCHAR(64)', 'NOT NULL DEFAULT \'public\'', @status_message);

    ALTER TABLE thirdparty_users
      ADD PRIMARY KEY (app_id, user_id);

    ALTER TABLE thirdparty_users
      ADD FOREIGN KEY (app_id, user_id)
        REFERENCES app_id_to_user_id (app_id, user_id) ON DELETE CASCADE;

    CREATE INDEX thirdparty_users_thirdparty_user_id_index ON thirdparty_users (app_id, third_party_id, third_party_user_id);

    CREATE INDEX thirdparty_users_email_index ON thirdparty_users (app_id, email);

    --

    CREATE TABLE IF NOT EXISTS thirdparty_user_to_tenant (
      app_id VARCHAR(64) DEFAULT 'public',
      tenant_id VARCHAR(64) DEFAULT 'public',
      user_id CHAR(36) NOT NULL,
      third_party_id VARCHAR(28) NOT NULL,
      third_party_user_id VARCHAR(256) NOT NULL
    );

    ALTER TABLE thirdparty_user_to_tenant
      ADD PRIMARY KEY (app_id, tenant_id, user_id);

    ALTER TABLE thirdparty_user_to_tenant
      ADD CONSTRAINT third_party_user_id
        UNIQUE (app_id, tenant_id, third_party_id, third_party_user_id);

    ALTER TABLE thirdparty_user_to_tenant
      ADD FOREIGN KEY (app_id, tenant_id, user_id)
        REFERENCES all_auth_recipe_users (app_id, tenant_id, user_id) ON DELETE CASCADE;

    INSERT IGNORE INTO thirdparty_user_to_tenant (user_id, third_party_id, third_party_user_id)
      SELECT user_id, third_party_id, third_party_user_id FROM thirdparty_users;

    -- UserIdMapping

    CALL st_add_column_if_not_exists('userid_mapping', 'app_id', 'VARCHAR(64)', 'NOT NULL DEFAULT \'public\'', @status_message);

    ALTER TABLE userid_mapping
      ADD PRIMARY KEY (app_id, supertokens_user_id, external_user_id);

    ALTER TABLE userid_mapping
      ADD CONSTRAINT supertokens_user_id
        UNIQUE (app_id, supertokens_user_id);

    ALTER TABLE userid_mapping
      ADD CONSTRAINT external_user_id
        UNIQUE (app_id, external_user_id);

    ALTER TABLE userid_mapping
      ADD FOREIGN KEY (app_id, supertokens_user_id)
        REFERENCES app_id_to_user_id (app_id, user_id) ON DELETE CASCADE;

    -- UserRoles

    CALL st_add_column_if_not_exists('roles', 'app_id', 'VARCHAR(64)', 'NOT NULL DEFAULT \'public\'', @status_message);

    ALTER TABLE roles
      ADD PRIMARY KEY (app_id, role);

    ALTER TABLE roles
      ADD FOREIGN KEY (app_id)
        REFERENCES apps (app_id) ON DELETE CASCADE;

    --

    CALL st_add_column_if_not_exists('role_permissions', 'app_id', 'VARCHAR(64)', 'NOT NULL DEFAULT \'public\'', @status_message);

    ALTER TABLE role_permissions
      ADD PRIMARY KEY (app_id, role, permission);

    ALTER TABLE role_permissions
      ADD FOREIGN KEY (app_id, role)
        REFERENCES roles (app_id, role) ON DELETE CASCADE;

    CREATE INDEX role_permissions_permission_index ON role_permissions (app_id, permission);

    --

    CALL st_add_column_if_not_exists('user_roles', 'app_id', 'VARCHAR(64)', 'NOT NULL DEFAULT \'public\'', @status_message);
    CALL st_add_column_if_not_exists('user_roles', 'tenant_id', 'VARCHAR(64)', 'NOT NULL DEFAULT \'public\'', @status_message);

    ALTER TABLE user_roles
      ADD PRIMARY KEY (app_id, tenant_id, user_id, role);

    ALTER TABLE user_roles
      ADD FOREIGN KEY (app_id, role)
        REFERENCES roles (app_id, role) ON DELETE CASCADE;

    ALTER TABLE user_roles
      ADD FOREIGN KEY (app_id, tenant_id)
        REFERENCES tenants (app_id, tenant_id) ON DELETE CASCADE;

    CREATE INDEX user_roles_role_index ON user_roles (app_id, tenant_id, role);

    -- UserMetadata

    CALL st_add_column_if_not_exists('user_metadata', 'app_id', 'VARCHAR(64)', 'NOT NULL DEFAULT \'public\'', @status_message);

    ALTER TABLE user_metadata
      ADD PRIMARY KEY (app_id, user_id);

    ALTER TABLE user_metadata
      ADD FOREIGN KEY (app_id)
        REFERENCES apps (app_id) ON DELETE CASCADE;

    -- Dashboard

    CALL st_add_column_if_not_exists('dashboard_users', 'app_id', 'VARCHAR(64)', 'NOT NULL DEFAULT \'public\'', @status_message);

    ALTER TABLE dashboard_users
      ADD PRIMARY KEY (app_id, user_id);

    ALTER TABLE dashboard_users
      ADD CONSTRAINT email
        UNIQUE (app_id, email);

    ALTER TABLE dashboard_users
      ADD FOREIGN KEY (app_id)
        REFERENCES apps (app_id) ON DELETE CASCADE;

    --

    CALL st_add_column_if_not_exists('dashboard_user_sessions', 'app_id', 'VARCHAR(64)', 'NOT NULL DEFAULT \'public\'', @status_message);

    ALTER TABLE dashboard_user_sessions
      ADD PRIMARY KEY (app_id, session_id);

    ALTER TABLE dashboard_user_sessions
      ADD FOREIGN KEY (app_id, user_id)
        REFERENCES dashboard_users (app_id, user_id) ON DELETE CASCADE;

    CREATE INDEX dashboard_user_sessions_expiry_index ON dashboard_user_sessions (expiry);

    -- TOTP

    CALL st_add_column_if_not_exists('totp_users', 'app_id', 'VARCHAR(64)', 'NOT NULL DEFAULT \'public\'', @status_message);

    ALTER TABLE totp_users
      ADD PRIMARY KEY (app_id, user_id);

    ALTER TABLE totp_users
      ADD FOREIGN KEY (app_id)
        REFERENCES apps (app_id) ON DELETE CASCADE;

    --

    CALL st_add_column_if_not_exists('totp_user_devices', 'app_id', 'VARCHAR(64)', 'NOT NULL DEFAULT \'public\'', @status_message);

    ALTER TABLE totp_user_devices
      ADD PRIMARY KEY (app_id, user_id, device_name);

    ALTER TABLE totp_user_devices
      ADD FOREIGN KEY (app_id, user_id)
        REFERENCES totp_users (app_id, user_id) ON DELETE CASCADE;

    --

    CALL st_add_column_if_not_exists('totp_used_codes', 'app_id', 'VARCHAR(64)', 'NOT NULL DEFAULT \'public\'', @status_message);
    CALL st_add_column_if_not_exists('totp_used_codes', 'tenant_id', 'VARCHAR(64)', 'NOT NULL DEFAULT \'public\'', @status_message);

    ALTER TABLE totp_used_codes
      ADD PRIMARY KEY (app_id, tenant_id, user_id, created_time_ms);

    ALTER TABLE totp_used_codes
      ADD FOREIGN KEY (app_id, user_id)
        REFERENCES totp_users (app_id, user_id) ON DELETE CASCADE;

    ALTER TABLE totp_used_codes
      ADD FOREIGN KEY (app_id, tenant_id)
        REFERENCES tenants (app_id, tenant_id) ON DELETE CASCADE;

    CREATE INDEX totp_used_codes_expiry_time_ms_index ON totp_used_codes (app_id, tenant_id, expiry_time_ms);

    -- ActiveUsers

    CALL st_add_column_if_not_exists('user_last_active', 'app_id', 'VARCHAR(64)', 'NOT NULL DEFAULT \'public\'', @status_message);

    ALTER TABLE user_last_active
      ADD PRIMARY KEY (app_id, user_id);

    ALTER TABLE user_last_active
      ADD FOREIGN KEY (app_id)
        REFERENCES apps (app_id) ON DELETE CASCADE;

    -- Drop procedures

    DROP PROCEDURE st_drop_all_fkeys;

    DROP PROCEDURE st_drop_all_keys;

    DROP PROCEDURE st_drop_all_pkeys;

    DROP PROCEDURE st_drop_all_indexes;

    DROP PROCEDURE st_add_column_if_not_exists;
    ```

    </details>

4. Start the new instance(s) of the core (version 6.0.0)

## [5.0.0] - 2023-04-05

### Changes

- Updated the `java-jwt` dependency version
- Increases free Dashboard user count to 3

### Fixes

- Fixed creating JWTs using MongoDB if a key already exists

### Breaking changes

- Using an internal `SemVer` class to handle version numbers. This will make handling CDI version ranges easier.
- Support for CDI version `2.21`
    - Removed POST `/recipe/handshake`
    - Added `useDynamicSigningKey` into `createNewSession` (POST `/recipe/session`), replacing
      `access_token_signing_key_dynamic` used in CDI<=2.18
    - Added `useStaticSigningKey` into `createSignedJWT` (POST `/recipe/jwt`)
    - Added `checkDatabase` into `verifySession` (POST `/recipe/session/verify`), replacing
      `access_token_blacklisting` used in CDI<=2.18
    - Removed `idRefreshToken`, `jwtSigningPublicKey`, `jwtSigningPublicKeyExpiryTime` and `jwtSigningPublicKeyList`
      from responses
    - Deprecated GET `/recipe/jwt/jwks`
    - Added GET `/.well-known/jwks.json`: a standard jwks
- Added new access token version
    - Uses standard prop names (i.e.: `sub` instead of `userId`)
    - Contains the id of the signing key in the header (as `kid`)
    - Stores the user payload merged into the root level, instead of the `userData` prop
- Session handling function now throw if the user payload contains protected props (`sub`, `iat`, `exp`,
  `sessionHandle`, `refreshTokenHash1`, `parentRefreshTokenHash1`, `antiCsrfToken`)
    - A related exception type was added as `AccessTokenPayloadError`
- Refactored the handling of signing keys
- `createNewSession` now takes a `useStaticKey` parameter instead of depending on the
  `access_token_signing_key_dynamic` config value
- `createJWTToken` now supports signing by a dynamic key
- `getSession` now takes a `checkDatabase` parameter instead of using the `access_token_blacklisting` config value
- Updated plugin interface version to 2.21

### Configuration Changes

- `access_token_signing_key_dynamic` is now deprecated, only used for requests with CDI<=2.18
- `access_token_blacklisting` is now deprecated, only used for requests with CDI<=2.18
- Renamed `access_token_signing_key_update_interval` to `access_token_dynamic_signing_key_update_interval`

### Database Changes

- Added new `useStaticKey` field into session info
- Manual migration is also required if `access_token_signing_key_dynamic` was set to false

#### Migration steps for SQL

- If using `access_token_signing_key_dynamic` false:
    ```sql
    ALTER TABLE session_info ADD COLUMN use_static_key BOOLEAN NOT NULL DEFAULT(true);
    ALTER TABLE session_info ALTER COLUMN use_static_key DROP DEFAULT;
    ```
    ```sql
    INSERT INTO jwt_signing_keys(key_id, key_string, algorithm, created_at)
      select CONCAT('s-', created_at_time) as key_id, value as key_string, 'RS256' as algorithm, created_at_time as created_at
      from session_access_token_signing_keys;
    ```
- If using `access_token_signing_key_dynamic` true or not set:
    - ```sql
  ALTER TABLE session_info ADD COLUMN use_static_key BOOLEAN NOT NULL DEFAULT(false);
  ALTER TABLE session_info ALTER COLUMN use_static_key DROP DEFAULT;
    ```

#### Migration steps for MongoDB

- If using `access_token_signing_key_dynamic` false:
    ```
    db.session_info.update({},
      {
        "$set": {
          "use_static_key": true
        }
      });
    ```
    ```
    db.key_value.aggregate([
      {
        "$match": {
          _id: "access_token_signing_key_list"
        }
      },
      {
        $unwind: "$keys"
      },
      {
        $addFields: {
          _id: {
            "$concat": [
              "s-",
              {
                $convert: {
                  input: "$keys.created_at_time",
                  to: "string"
                }
              }
            ]
          },
          "key_string": "$keys.value",
          "algorithm": "RS256",
          "created_at": "$keys.created_at_time",
        }
      },
      {
        "$project": {
          "keys": 0,
          
        }
      },
      {
        "$merge": {
          "into": "jwt_signing_keys",
          
        }
      }
  ]);
    ```

- If using `access_token_signing_key_dynamic` true or not set:
    ```
    db.session_info.update({},
      {
        "$set": {
          "use_static_key": false
        }
      });
    ```

## [4.6.0] - 2023-03-30

- Add Optional Search Tags to Pagination API to enable dashboard search

### New APIs:

- `GET /user/search/tags` retrieves the available search tags

## [4.5.0] - 2023-03-27

- Add TOTP recipe

### Database changes:

- Add new tables for TOTP recipe:
    - `totp_users` that stores the users that have enabled TOTP
    - `totp_user_devices` that stores devices (each device has its own secret) for each user
    - `totp_used_codes` that stores used codes for each user. This is to implement rate limiting and prevent replay
      attacks.
    - `user_last_active` that stores the last active time for each user.

### New APIs:

- `GET /users/count/active` to fetch the number of active users after the given timestamp.
- `POST /recipe/totp/device` to create a new device as well as the user if it doesn't exist.
- `POST /recipe/totp/device/verify` to verify a device. This is to ensure that the user has access to the device.
- `POST /recipe/totp/verify` to verify a code and continue the login flow.
- `PUT /recipe/totp/device` to update the name of a device. Name is just a string that the user can set to identify the
  device.
- `GET /recipe/totp/device/list` to get all devices for a user.
- `POST /recipe/totp/device/remove` to remove a device. If the user has no more devices, the user is also removed.

## [4.4.2] - 2023-03-16

- Adds null check in email normalisation to fix: https://github.com/supertokens/supertokens-node/issues/514

## [4.4.1] - 2023-03-09

- Normalises email in all APIs in which email was not being
  normalised: https://github.com/supertokens/supertokens-core/issues/577

## [4.4.0] - 2023-02-21

### Added

- Dashboard Recipe
- Support with CDI version `2.18`

### Database Changes

- Adds `dashboard_users` table
- Adds `dashboard_user_sessions` table

## [4.3.0] - 2023-01-05

- Adds feature flag, ee folder and APIs to add / remove license keys for enterprise features.

## [4.2.1] - 2022-11-24

- Updates the type of `access_token_validity` in the CoreConfig from `int` to `long`

## [4.2.0] - 2022-11-07

- Update dependencies for security updates: https://github.com/supertokens/supertokens-core/issues/525

## [4.1.1] - 2022-10-13

- Updates core routes to now allow for trailing slashes

## [4.1.0] - 2022-09-22

- Adds request IP allow & deny list: https://github.com/supertokens/supertokens-core/issues/511

## [4.0.1] - 2022-09-19

- Fixes bug related to implementationDependencies.json

## [4.0.0] - 2022-09-19

### Added

- EmailPassword User migration API which allows you to import users with their email and password hashes.
- Support to import users with password hashes from Firebase
- Support with CDI version `2.16`
- Hello API on `/` route.

### Database Changes

- Updates the `password_hash` column in the `emailpassword_users` table from `VARCHAR(128)` to `VARCHAR(256)` to support
  more password hash lengths.
- Updates the `third_party_user_id` column in the `thirdparty_users` table from `VARCHAR(128)` to `VARCHAR(256)` to
  resolve https://github.com/supertokens/supertokens-core/issues/306

- For legacy users who are self hosting the SuperTokens core run the following command to update your database with the
  changes:
    - With MySql:
      `ALTER TABLE thirdparty_users MODIFY third_party_user_id VARCHAR(256); ALTER TABLE emailpassword_users MODIFY password_hash VARCHAR(256);`
    - With PostgreSQL:
      `ALTER TABLE thirdparty_users ALTER COLUMN third_party_user_id TYPE VARCHAR(256); ALTER TABLE emailpassword_users ALTER COLUMN password_hash TYPE VARCHAR(256);`

## [3.16.2] - 2022-09-02

### Bug fixes

- Updated java-jwt to handle `null` claims in JWTs

## [3.16.1] - 2022-09-02

### Bug fixes

- Fixed handling of `null` in access token payloads: https://github.com/supertokens/supertokens-core/issues/499

## [3.16.0] - 2022-08-18

- Changes logging level of API start / finished & Cronjob start / finished to be `INFO` level instead of `DEBUG` level.
- Added new config `log_level` to set logging level. Possible values are `DEBUG` | `INFO` | `WARN` | `ERROR` |
  `NONE`. As an example, setting the log level to `WARN` would make the core print out `WARN` and `ERROR` level logs.

## [3.15.1] - 2022-08-10

- Updates UserIdMapping recipe to resolve UserId Mappings for Auth recipes in the core itself

## [3.15.0] - 2022-07-25

- Adds UserIdMapping recipe
- Support for collecting and displaying failing tests

### Database changes

- Adds `userid_mapping` table

## [3.14.0] - 2022-06-07

- Fixes `/recipe/session/user GET` to return only session handles that have not expired.
- Support for new plugin interface version (v2.15)
- Checks for if the session has expired in `updateSession` before calling the update function.

## [3.13.0] - 2022-05-05

- Adds UserRoles recipe
- Fixes base_path config option not being observed when running `supertokens list`
- Adds base_path normalization logic

### Database changes

- Adds `roles`, `role_permissions` and `user_roles` table

## [3.12.1] - 2022-04-02

### Changes

- Changed default `--with_argon2_hashing_pool_size` in `hashingCalibrate` CLI command to 1.

## [3.12.0] - 2022-04-01

- Adds github action for running tests against in memory db.
- Adds github action for checking if "Run tests" action was completed (to run in PRs)
- Fixes how config values are changed during tests.
- Adds 60 mins timeout to github action jobs
- Moves deleting user metadata to happen before deleting the actual user.
- Adds support for argon2 hashing.
- Adds colours to CLI output (in case of errors).

### New config:

- `password_hashing_alg`
- `argon2_iterations`
- `argon2_memory_kb`
- `argon2_parallelism`
- `argon2_hashing_pool_size`
- `bcrypt_log_rounds`

### New CLI command:

- `supertokens hashingCalibrate`: Used to calibrate argon2 and bcrypt passing hashing params.

## [3.11.0] - 2022-03-19

### Changes

- Fixes memory leak during testing.
- Updated plugin interface version
- Adds usermetadata recipe
- Update CONTRIBUTING.md with instructions for gitpod setup

### Database changes

- Added `user_metadata` table

## [3.10.0] - 2022-02-23

- Updated plugin interface version
- Fixed ResultSet instances to avoid Memory Leaks

## [3.9.1] - 2022-02-16

- Fixed https://github.com/supertokens/supertokens-core/issues/373: Catching `StorageTransactionLogicException` in
  transaction helper function for retries

## [3.9.0] - 2022-01-31

### Changes

- Supporting CDI v2.12
- Adding the `userId` to the reponse of `recipe/user/password/reset`
- Adds support for providing base path for all APIs: https://github.com/supertokens/supertokens-node/issues/252
- Add workflow to verify if pr title follows conventional commits

### New config param:

- `base_path` - default is `""` (No base path)

## [3.8.0] - 2022-01-14

### Added

- Added Passwordless recipe ( with unit test coverage )

### Database changes

- Adds new tables for passwordless:
    - `passwordless_users` that stores the users of the passwordless recipe
    - `passwordless_devices` that stores devices/information about passwordless login attempts
    - `passwordless_codes` that stores the codes each device can consume to finish the login process

### Changes

- New recipeId in `/users` response with a corresponding new user type

## [3.7.0] - 2021-12-16

### Added

- Delete user endpoint

## [3.6.1] - 2021-11-15

### Fixes

- Issue with JWT expiry always being lower than expected
- Modulus and exponent for JsonWebKeys are now sent as unsigned when fetching public keys from the /jwt/jwks.json
  endpoint. Both values are url encoded without any padding.

### Changes

- JWT creation logic to add a `iss` claim only if none is provided

## [3.6.0] - 2021-08-26

### Added

- New config values `password_reset_token_lifetime`
  and `email_verification_token_lifetime`: https://github.com/supertokens/supertokens-core/issues/297
- Added support for multiple access token signing keys: https://github.com/supertokens/supertokens-core/issues/305
- Updated CDI version
- Added a table to store access token signing keys into SQL schema, called `session_access_token_signing_keys`
- New JWT recipe to create JWT tokens using SuperTokens
- New table `jwt_signing_keys` added to store keys used by the JWT recipe

## [3.5.3] - 2021-09-20

### Changes

- Explicitly adds UTF-8 compatible conversion when encoding / decoding base64 strings.

## [3.5.2] - 2021-09-01

### Fixes

- Issue with verifying refresh token throwing an unauthorised exception due to a db connection error.
- Sends far ahead jwt signing key expiry time in case updating them is
  disabled: https://github.com/supertokens/supertokens-core/issues/304

### Changes

- Changes JWT signing key update interval to not be limited to 720 hours

## [3.5.1] - 2021-08-25

### Added

- Logs non "OK" status code from APIs for debugging purposes.

### Fixed:

- Always throws unauthorised response if refresh token is not valid - previously it was throwing a 500 error in case it
  was not properly base 64 encoded.

## [3.5.0] - 2021-06-20

### Changed

- Make emailverificaiton tables take a generic userId: https://github.com/supertokens/supertokens-core/issues/258
- Adds new count and pagination APIs: https://github.com/supertokens/supertokens-core/issues/259
- Adds new API to get session data, and deprecates older one to get session and JWT payload separately:
  https://github.com/supertokens/supertokens-core/issues/255
- Removed `isVerified` boolean from thirdparty sign in up API as per CDI spec 2.8, and hence does not do email
  verification in this API either. Also related to https://github.com/supertokens/supertokens-core/issues/295

### Added

- Add `GET /recipe/users/by-email?email=john@example.com` endpoint for ThirdParty recipe to fetch all users with given
  email
- Add new emailverification APIs for remove tokens and unverify email.
- Add `PUT /recipe/user` for emailpassword recipe to change user's password or email.

## [3.4.2] - 2021-06-27

### Fixes

- `NullPointerException` that is thrown in `AccessTokenSigningKey.java` class when the `keyInfo` object is accessed in
  parallel after the signing key has expired: https://github.com/supertokens/supertokens-core/issues/282

## [3.4.1] - 2021-06-18

### Added

- `test_mode` to the options for running the core so that it can be run in test mode whilst being tested by the backend
  SDK.
- Adds `jwtSigningPublicKey` and `jwtSigningPublicKeyExpiryTime` to API response when returning `TRY_REFRESH_TOKEN
  ` from session verify.

## [3.4.0] - 2021-04-22

### Changed

- Uses Open JDK 15.0.1

## [3.3.0] - 2021-02-16

### Changed

- Extracted email verification into its own recipe
- ThirdParty recipe API

## [3.2.0] - 2021-01-26

### Changed

- Normalises email by making it all lower case
- Changes in handshake API
- Changes in config
- Changes in session create, verify and refresh APis

## [3.1.0] - 2021-01-14

### Changed

- Used rowmapper for in memory db
- Adds email verification APIs
- Adds user pagination APIs
- Adds timeJoined to whenever a user object is returned from an API

## [3.0.1] - 2020-10-27

### Changed

- Makes Hello API do a db query as well for better status checking

## [3.0.0] - 2020-10-25

### Changed

- Changes as per CDI 2.4: https://github.com/supertokens/core-driver-interface/issues/1
- In memory db uses the SQL interface
- Emailpassword recipe functions and APIs
- Deprecates the need for a separate SQLite repo (since the in mem one already exists within the core)

## [2.5.2] - 2020-10-25

### Fixed

- Issue #84 - Correct access token signing key expiry not being sent by APIs

## [2.5.1] - 2020-10-08

### Changed

- Fixed issue of docker image hanging when run in foreground

## [2.5.0] - 2020-10-08

### Added

- Updates the access token if blacklisting is switched on and the JWT payload has been changed somehow
- API key support
- JWT Api Key Rotation

### Removed

- Compatibility with the inefficient method for handling refresh tokens.

## [2.4.0] - 2020-09-09

### Added

- CSRF check in refresh API
- set csrf config to `false` by default
- compatibility with CDI 2.3

### Fixed

- When regenerating session, uses old access tokens' parentRefreshTokenHash1 instead of null

### Changed

- Optimises refresh token to not store old tokens in the database
- removes the need for a license key
- removes API Pings

## [2.3.0] - 2020-08-11

### Changed

- Makes default session expiry status code 401
- Makes default refresh API path "/session/refresh"
- Compatibility with CDI 2.2. Makes `cookie_domain` default value to not set, so that it will work with any API
- Makes sameSite = lax by default
- If licenseKey is missing, then dependency jars are downloaded in DEV mode

## [2.2.3] - 2020-08-10

### Changes

- Makes license Apache 2.0

## [2.2.2] - 2020-07-02

### Fixed

- Changes how versioning works to make it per API call.
- Supports CDI 2.1

## [2.2.1] - 2020-05-14

### Fixed

- Forcing of no in memory database flag to start command on Linux fixed

## [2.2.0] - 2020-05-20

### Added

- Uses in memory database in dev mode if database is not configured
- Removes the need to specify dev / production when running the start command

## [2.1.0] - 2020-04-30

### Added

- Compatibility with CDI 2.0
- API versions
- SameSite cookie option
- Updating of JWT payload
- Session expired status code configuration
- Partial lmrt support

## [2.0.0] - 2020-04-07

### Added

- Compatibility with NoSQL databases like MongoDB
- Setting sameSite cookie option. However, this is not usable in this release.

## [1.1.1] - 2020-03-23

### Changed

- Adds #!/bin/bash in scripts

## [1.1.0] - 2020-03-23

### Changed

- Allow for an unlimited number of SuperTokens instances in production mode
- License changes to reflect the above
