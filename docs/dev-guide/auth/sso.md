# 启用SSO支持

系统内置了对Keycloak单点登录服务器的支持，可以直接集成外部的keycloak服务器，而完全不使用nop-auth模块内置的NopAuthUser表。用户的角色也是直接从keycloak服务器获取。

1. 引入nop-auth-sso模块，这里提供了OAuthLoginServiceImpl，它会替换nop-auth-service模块提供的缺省实现LoginServiceImpl
2. 在application.yaml中启用sso相关的配置

```yaml
nop:
  auth:
    sso:
      enabled: true
      server-url: http://localhost:8041
      realm: app
      client-id: test-client
      client-secret: qpgEjwXqd1TpgaA3aIi1jd4AVTLCrs8o
```

* nop.auth.sso.server-url对应于keycloak单点服务器的url
* nop.auth.sso.realm是keycloak中配置的realm
* nop.auth.sso.client-id和nop.auth.sso.client-secret是keycloak服务器中配置的客户端信息

## 单点退出

通过访问如下链接可以调用nop.auth.sso.logout-url来退出点点

```
POST /r/LoginApi__ssoLogout

{
   "accessToken": "xxx"
}
```

## 权限限制

Nop平台只使用了单点服务器的用户和角色配置，对于更细粒度的资源访问权限，则需要在Nop平台内部配置。Nop平台通过指定Role可以访问哪些资源对象来控制用户操作权限。
