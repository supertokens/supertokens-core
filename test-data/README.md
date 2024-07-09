# Tenant create/update test cases

This folder contains test cases for the test: `io.supertokens.test.multitenant.api.TestTenantCreationBehaviour.
testCrossVersionCreateAndUpdateCases`.

The create / update tenant APIs have different inputs between CDI version 3.0-4.0, 5.0 and v2 API has been introduced
in 5.1. We created a playground to test how the input / output fares cross versions. Code for that is included in
this folder.

All the json files here constitute all possible test cases for the test mentioned above.
Each test case has the following structure:

```js
{
  "tenantId": "public", // public or non public
  "cv": "4", // CDI version used for tenant creation
  "uv": "4", // CDI version used fo updating the tenant
  "cbody": {
    // ... body of the create operation
  },
  "ubody": {
    // ... body of the update operation
  },
  "tenantState": { // state stored in the db
    "tenantId": "public",
    "emailPasswordEnabled": true,
    "passwordlessEnabled": true,
    "thirdPartyEnabled": true,
    "firstFactors": null,
    "requiredSecondaryFactors": null
  },
  "g4": { // expected output from GET tenant API for CDI version 4
    "emailPasswordEnabled": true,
    "thirdPartyEnabled": true,
    "passwordlessEnabled": true
  },
  "g5": { // expected output from GET tenant API for CDI version 5
    "emailPasswordEnabled": true,
    "thirdPartyEnabled": true,
    "passwordlessEnabled": true,
    "firstFactors": null,
    "requiredSecondaryFactors": null
  },
  "gv2": { // expected output from GET tenant API for CDI version 5.1
    "firstFactors": null, 
    "requiredSecondaryFactors": null },
  "invalidConfig": false // Indicates if create or update should result in an invalid config error
}
```

These test cases can be generated using the playground app in this folder.

Note that the test case files have been broken down into multiple files to be able to add to the git.