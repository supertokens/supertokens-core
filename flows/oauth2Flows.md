# Flow diagrams
## 1. Create oauth2 client 
- Path: "/appid-<appId>/recipe/oauth2/client"
- Method: POST 
- Input:
``` 
{
    redirectUris: string[],
    name: string,
    scopes: string[]
}
```
- Output:
```
{
    status : "OK",
    clientInfo : {
            clientId: string,
            clientSecret: string,
            scopes: string[],
            timeCreated: number
    }
} | {
    status : "CLIENT_ALREADY_EXISTS_ERROR" | "UNKNOWN_SCOPE_ERROR"
}
```
- Flow diagram: https://app.code2flow.com/flowcharts/6475a4953f4c2482c82a4352

## 2. Get oauth2 client  [in progress]
- Path: "appid-<appId>/recipe/oauth2/client"
- Method: GET
- Query Parameter Name : clientId (`Type : String`)
- Output:
```
{
  "status": "OK",
  "redirectUris": string[]
  "name": string,
  "scopes": string[],
  "timeCreated": number,
  "timeUpdated": number,
  "authorizationCodes": [
    {
      "tenantId": string,
      "sessionHandle": string,
      "scopes": string[],
      "accessType": string,
      "codeChallenge": string,
      "codeChallengeMethod": string,
      "redirectUri": string,
      "timeCreated": number,
      "timeExpires": number
    }
  ],
  "issuedTokens": [
    {
      "tenantId": string,
      "sessionHandle": string,
      "userId": string,
      "scopes": string[],
      "accessTokenHash": string,
      "refreshTokenHash": string,
      "timeCreated": number,
      "timeAccessTokenExpires": number,
      "timeRefreshTokenExpires": number
    }
  ]
}

```
- Flow diagram: https://app.code2flow.com/flowcharts/6475cc5b3f4c2482c82a736a

## Todo's:
- [ ] Add implementation of `upsertOAuth2ClientScope_Transaction()` in postgres-plugin
- [ ] Update changes to Create oauth2 client api contract in `core-driver-interface` 
- [ ] Add `create_oauth2_client` api implementation in `supertokens-core`
- [ ] Add test cases for create `oauth2_client` api  in `supertokens-core`