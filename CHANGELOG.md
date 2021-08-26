# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), and this project adheres
to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [3.6.0] - 2021-08-26

### Added

- New config values `password_reset_token_lifetime`
  and `email_verification_token_lifetime`: https://github.com/supertokens/supertokens-core/issues/297

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