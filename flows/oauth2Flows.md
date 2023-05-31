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
    status : "CLIENT_ALREADY_EXISTS_ERROR"
} | {
    status : "UNKNOWN_SCOPE_ERROR",
    scope : string
}
```
- Flow diagram: https://app.code2flow.com/flowcharts/6475a4953f4c2482c82a4352


## 2. Get oauth2 client
- Path: "/appid-<appId>/recipe/oauth2/client/<clientId>"
- Method: GET
- Input Params:
    - ```clientId=string```
- Output:
```
{
  status: "OK",
  name: string,
  clientInfo : 
    {
       clientId: string,
       clientSecret: string,
       scopes: string[],
       timeCreated: number
    }
  redirectUris: string[],
  timeUpdated: number,
  authorizationCodes: [
    {
      tenantId: string,
      sessionHandle: string,
      scopes: string[],
      accessType: string,
      codeChallenge: string,
      codeChallengeMethod: string,
      redirectUri: string,
      timeCreated: number,
      timeExpires: number
    }
  ],
  issuedTokens: [
    {
      tenantId: string,
      sessionHandle: string,
      userId: string,
      scopes: string[],
      accessTokenHash: string,
      refreshTokenHash: string,
      timeCreated: number,
      timeAccessTokenExpires: number,
      timeRefreshTokenExpires: number
    }
  ]
}

```
- Flow diagram: https://app.code2flow.com/flowcharts/6475cc5b3f4c2482c82a736a
