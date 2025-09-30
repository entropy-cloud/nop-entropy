# RPC Access Control Configuration

## Client Configuration

When invoking remote services using an HTTP client, you can pass the access token via the Authorization header.

AddAccessTokenHttpClientEnhancer automatically adds the access token to all HTTP requests that match specific URL patterns.

```yaml
nop:
  http:
    client:
      auth-configs:
        http-clients:
          serviceA:
            urlPattern: http://localhost:9090.*
            useContextAccessToken: true
          serviceB:
            urlPattern: http://localhost:9091.*
            oauthProvider: oauthServer
        oauthProviders:
            oauthServer:
               tokenUri: http://localhost:9092/oauth/token
               clientId: abc
               clientSecret: 123
```

HttpClientAuthConfigs contains multiple authentication configurations.

1. For each URL, the first matching service configuration is used
2. If useContextAccessToken is set, read the accessToken from the IContext
3. If oauthProvider is set, use OAuth2Client to obtain the access token and add it to the Authorization header
4. If neither is configured, accessToken handling is skipped
5. Configure the OAuth authorization server URLs via oauthProviders


## Server-Side Configuration

In most cases, the server does not require special configuration; by default, login authentication is required.

* In AuthHttpServerFilter, the accessToken is checked. The access token is parsed via the `ILoginService.parseAccessToken` function.

<!-- SOURCE_MD5:72e6a35ae7a0cf76e960dc1ddb431369-->
