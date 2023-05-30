# Flow diagrams
## Create oauth 2 client
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
    clientId: string,
    clientSecretBase64: string,
    scopes: string[]
    timeCreated: number
} | {
    status : "CLIENT_ALREADY_EXISTS_ERROR" | "UNKNOWN_SCOPES_ERROR"
}
```
Flow diagram: https://app.code2flow.com/flowcharts/6475a4953f4c2482c82a4352