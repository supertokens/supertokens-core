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