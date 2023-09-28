---
name: 📅 Release
about: Release checklist
labels:
---

# 📅 Checklist

## 🔶 Staging 

### Dev Tag
 - [supertokens-core:X.Y](https://github.com/supertokens/supertokens-core/tree/X.Y)
     - [ ] core
        - [ ] check CDI, plugin interface list
     - [ ] plugin-interface
        - [ ] check plugin interface list
     - [ ] mysql-plugin
         - [ ] check plugin interface list
     - [ ] postgresql-plugin
         - [ ] check plugin interface list
     - [ ] mongodb-plugin
         - [ ] check plugin interface list
 - [ ] [supertokens-node:X.Y](https://github.com/supertokens/supertokens-node/tree/X.Y)
   - [ ] check CDI, FDI list
 - [ ] [supertokens-golang:X.Y](https://github.com/supertokens/supertokens-golang/tree/X.Y)
   - [ ] check CDI, FDI list
 - [ ] [supertokens-python:X.Y](https://github.com/supertokens/supertokens-python/tree/X.Y)
   - [ ] check CDI, FDI list
 - [ ] [supertokens-website:X.Y](https://github.com/supertokens/supertokens-website/X.Y)
   - [ ] check FDI list
 - [ ] [supertokens-web-js:X.Y](https://github.com/supertokens/supertokens-web-js/X.Y)
   - [ ] check FDI list
   - [ ] check web-js interface version
   - [ ] Update dependency version of supertokens-website in package.json  from npm registry
 - [ ] [supertokens-auth-react:X.Y](https://github.com/supertokens/supertokens-auth-react/tree/X.Y)
    - [ ] check FDI list
    - [ ] check web-js interface version
    - [ ] Updated dependencies to use supertokens-web-js in package.json from npm registry
    - [ ] Various browsers - Safari, Firefox, Chrome, Edge
    - [ ] Mobile responsiveness
    - [ ] Make sure using with-typescript example that types are correct for every new configs exposed to users
    - [ ] Make sure frontend login UI shows even if backend is not working.
 - [ ] [supertokens-react-native:X.Y](https://github.com/supertokens/supertokens-react-native/X.Y)
    - [ ] check FDI list
 - [ ] [supertokens-android:X.Y](https://github.com/supertokens/supertokens-android/X.Y)
    - [ ] check FDI list 
 - [ ] [supertokens-ios:X.Y](https://github.com/supertokens/supertokens-ios/X.Y)
    - [ ] check FDI list   
 - [ ] [supertokens-flutter:X.Y](https://github.com/supertokens/supertokens-flutter/X.Y)
    - [ ] check FDI list   
 - [ ] [supertokens-dashboard](https://github.com/supertokens/dashboard)

### Others

-  [ ] Example apps in create-supertokens-app CLI
 - [ ] Examples apps in supertokens-auth-react
 - [ ] Examples apps in supertokens-web-js
 - [ ] Examples apps in supertokens-react-native
 - [ ] Examples apps in supertokens-golang
 - [ ] Examples apps in supertokens-python
 - [ ] Examples apps in supertokens-node
 - [ ] Examples apps in android
 - [ ] Example apps in ios 
 - [ ] Example apps in flutter 
 - [ ] [next.js:canary](https://github.com/supertokens/next.js/tree/canary/examples/with-supertokens)
 - [ ] RedwoodJS and playground-auth
 - [ ] Run on netlify (and hence AWS lambda) to check if it works fine there
 - [ ] Test on vercel (with-emailpassword-vercel app) 
 - [ ] SuperTokens Jackson SAML example update
 - [ ] Supabase docs
 - [ ] Capacitor template app: https://github.com/RobSchilderr/capacitor-supertokens-nextjs-turborepo

### 📚 Documentation (test site)

- [ ] All recipe main documentation update
- [ ] Code type checking versions are pointing to X.Y
   - [ ] jsEnv
   - [ ] goEnv
   - [ ] pythonEnv
- [ ] Update table schema in mysql / postgresql section for self hosted
- [ ] community documentation update
- [ ] website changes (test.supertokens.io)
   - [ ] homepage
   - [ ] pricing page feature list
   - [ ] comparison chart in the pricing page
   - [ ] product roadmap page
   - [ ] Update API key code snippet in SaaS dashboard
   - [ ] Update recipe list and links to the docs for supertokens.com dashboard

## 🔥 Production 

### 💻 NPM and core release

 - core
    - [ ] [supertokens-core:X.Y](https://github.com/supertokens/supertokens-core/tree/X.Y)
    - [ ] plugin-interface
    - [ ] mysql-plugin
    - [ ] postgresql-plugin
    - [ ] mongodb-plugin
    - Docker update
       - [ ] MySQL
          - [ ] check if new env cnofigs need to be added 
       - [ ] Postgres
          - [ ] check if new env cnofigs need to be added 
       - [ ] MongoDB
          - [ ] check if new env cnofigs need to be added
    - [ ] try.supertokens.io
      ```
      docker rm try-supertokens -f
      docker rmi supertokens/supertokens-postgresql:<VERSION>
      nano ~/try-supertokens/start_container.sh (update version tag)
      ~/try-supertokens/start_container.sh
      ```
    - [ ] Update SaaS config
    - [ ] Update to tables checked for user count / or to know if a deployment is being used or not
    - [ ] Update logic for deleting all data in dev env if a new table was added and if the data should be removed from it too
    - [ ] Update logic for exporting csv file for registered users
    - [ ] Update SaaS instances to use the latest docker images.
    - [ ] Change [checklist in contributing guide for which tables to pick when migrating data from dev to prod instance](https://test.supertokens.com/docs/contribute/checklists/saas/tables-to-consider-for-data-migration-dev-to-prod).
    - [ ] Update license key used for cores to include nea feature.
    - [ ] Update table schema in mysql / postgresql section for self hosted in docs
    - [ ] Update API that returns the list of paid features in saas dashboard
    - [ ] Update logic for core to core migration for new saas architecture:
       - [ ] transfer of master database information
       - [ ] deletion of master database information related to the CUD being transferred
 - [ ] [supertokens-node:X.Y](https://github.com/supertokens/supertokens-node/tree/X.Y)
 - [ ] [supertokens-golang:X.Y](https://github.com/supertokens/supertokens-golang/tree/X.Y)
 - [ ] [supertokens-website:X.Y](https://github.com/supertokens/supertokens-website/tree/X.Y)
 - [ ] [supertokens-web-js:X.Y](https://github.com/supertokens/supertokens-web-js/tree/X.Y)
 - [ ] [supertokens-auth-react:X.Y](https://github.com/supertokens/supertokens-auth-react/tree/X.Y)
 - [ ] [supertokens-python:X.Y](https://github.com/supertokens/supertokens-python/tree/X.Y)
 - [ ] [supertokens-react-native:X.Y](https://github.com/supertokens/supertokens-react-native/X.Y)
 - [ ] [supertokens-android:X.Y](https://github.com/supertokens/supertokens-android/X.Y)
 - [ ] [supertokens-ios:X.Y](https://github.com/supertokens/supertokens-ios/X.Y)
 - [ ] [supertokens-flutter:X.Y](https://github.com/supertokens/supertokens-flutter/X.Y)
-  [ ] [supertokens-dashboard](https://github.com/supertokens/dashboard)

### 📚 Documentation

- [ ] Pushed to production
- [ ] Post message on discord about new update
- [ ] Updated swaggerhub FDI spec
- [ ] Update frontend-driver-interface repo
- [ ] Updated swaggerhub CDI spec
- [ ] Update core-driver-interface-repo
- [ ] Updated dashboard spec on swaggerhub
- [ ] Update [dashboard spec](https://github.com/supertokens/dashboard/blob/master/api_spec.yaml)
- [ ] Update internal contributing guide to move from previous core version to the latest one
- [ ] Algolia search update for docs
- [ ] robots.txt, sitemap.xml, noindex page update
- Auto generate release note on github:
   - [ ] supertokens-core
   - [ ] supertokens-plugin-interface
   - [ ] supertokens-mysql-plugin
   - [ ] supertokens-postgresql-plugin
   - [ ] supertokens-mongodb-plugin
   - [ ] supertokens-node
   - [ ] supertokens-golang
   - [ ] supertokens-python
   - [ ] supertokens-website
   - [ ] supertokens-web-js
   - [ ] supertokens-auth-react
   - [ ] supertokens-react-native
   - [ ] supertokens-android
   - [ ] supertokens-ios
   - [ ] supertokens-flutter
   - [ ] supertokens-dashboard

### Contents of running try.supertokens.com script:
```bash
docker run -d \
    --restart=always \
    --name try-supertokens \
    --label name=try-supertokens \
    --label type=session-service \
    --label mode=production \
    --log-driver=awslogs --log-opt awslogs-region=ap-south-1 --log-opt awslogs-group=try-supertokens --log-opt awslogs-stream=try-supertokens \
    -e DISABLE_TELEMETRY=true \
    --publish 9999:3567 \
    supertokens/supertokens-postgresql:6.0

sleep 7

curl --location --request POST 'https://try.supertokens.com/recipe/dashboard/user' \
--header 'rid: dashboard' \
--header 'api-key: <YOUR-API-KEY>' \
--header 'Content-Type: application/json' \
--data-raw '{"email": "rishabh@supertokens.com","password": "abcd1234"}'

curl --location --request POST 'https://try.supertokens.com/recipe/dashboard/user' \
--header 'rid: dashboard' \
--header 'api-key: <YOUR-API-KEY>' \
--header 'Content-Type: application/json' \
--data-raw '{"email": "demo@supertokens.com","password": "abcd1234"}'

curl --location --request PUT 'https://try.supertokens.com/recipe/multitenancy/tenant' \
--header 'Content-Type: application/json' \
--data-raw '{
    "tenantId": "tenant1",
    "emailPasswordEnabled": true,
    "thirdPartyEnabled": true,
    "passwordlessEnabled": false
}'

curl --location --request PUT 'https://try.supertokens.com/tenant1/recipe/multitenancy/config/thirdparty' \
--header 'Content-Type: application/json' \
--data-raw '{
  "config": {
    "thirdPartyId": "google-workspaces",
    "name": "Google Workspaces",
    "clients": [
      {
        "clientId": "1060725074195-kmeum4crr01uirfl2op9kd5acmi9jutn.apps.googleusercontent.com",
        "clientSecret": "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",
        "additionalConfig": {
            "hd": "*"
        }
      }
    ]
  }
}'


curl --location --request PUT 'https://try.supertokens.com/recipe/multitenancy/tenant' \
--header 'Content-Type: application/json' \
--data-raw '{
    "tenantId": "tenant2",
    "emailPasswordEnabled": true,
    "thirdPartyEnabled": false,
    "passwordlessEnabled": false
}'

curl --location --request PUT 'https://try.supertokens.com/recipe/multitenancy/tenant' \
--header 'Content-Type: application/json' \
--data-raw '{
    "tenantId": "tenant3",
    "emailPasswordEnabled": false,
    "thirdPartyEnabled": true,
    "passwordlessEnabled": true
}'


curl --location --request PUT 'https://try.supertokens.com/tenant3/recipe/multitenancy/config/thirdparty' \
--header 'Content-Type: application/json' \
--data-raw '{
  "config": {
    "thirdPartyId": "github",
    "name": "GitHub",
    "clients": [
      {
        "clientId": "467101b197249757c71f",
        "clientSecret": "e97051221f4b6426e8fe8d51486396703012f5bd"
      }
    ]
  }
}'
```
