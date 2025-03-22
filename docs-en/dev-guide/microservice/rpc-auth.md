# RPC Access Control Configuration

## Client Configuration

When calling a remote service using an HTTP client, the access token can be transmitted via the Authorization header.

AddAccessTokenHttpClientEnhancer automatically adds the access token to all HTTP requests that match a specific URL pattern.

```yaml
none:
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

HttpClientAuthConfigs supports multiple authentication configurations.

1. For each URL, match the first service configuration.
2. If useContextAccessToken is set, retrieve the Access Token from the IContext context.
3. If an oauthProvider is configured, use OAuth2Client to obtain an access token and add it to the Authorization header.
4. If neither is configured, skip access token processing.
5. Configure OAuth authentication service endpoints using oauthProviders.

## Server Configuration

Generally, the server does not require special configuration. By default, it will enforce login authentication.

* AuthHttpServerFilter checks for the Access Token. It uses the ILoginService.parseAccessToken function to parse the Access Token.