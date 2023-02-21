# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), and this project adheres
to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [unreleased]

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
