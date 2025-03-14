# RPC访问权限配置

## 客户端配置

使用http client调用远程服务时可以通过Authorization头传递access token。

AddAccessTokenHttpClientEnhancer为所有满足特定URL pattern的http请求自动添加access token。

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

HttpClientAuthConfigs包含多个认证配置。

1. 对于每个URL，匹配到第一个服务配置
2. 如果设置了useContextAccessToken，则从IContext上下文中读取accessToken
3. 如果设置了oauthProvider，则使用OAuth2Client获取access token，并添加到Authorization头中
4. 如果都没有配置，则会跳过accessToken处理
5. 通过oauthProviders配置OAuth认证服务端的链接


## 服务端配置

一般情况下服务端不需要进行特殊配置，缺省就会要求登录认证。

* AuthHttpServerFilter中会检查accessToken。通过`ILoginService.parseAccessToken`函数来解析accessToken。

