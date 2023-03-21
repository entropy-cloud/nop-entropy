# 启用SSO支持

系统内置了对Keycloak单点登录服务器的支持，可以直接集成外部的keycloak服务器，而完全不用在系统中创建用户表。

1. 引入nop-auth-sso模块，这里提供了OAuthLoginServiceImpl，它会替换nop-auth-service模块提供的缺省实现LoginServiceImpl
2. 在application.yaml中启用sso相关的配置
```yaml
nop:
  auth:
    sso:
      enabled: false
      server-url: http://localhost:8041
      realm: app
      client-id: test-client
      client-secret: qpgEjwXqd1TpgaA3aIi1jd4AVTLCrs8o
```

* nop.auth.sso.server-url对应与keycloak单点服务器的url
* nop.auth.sso.realm是keycloak中配置的realm
* nop.auth.sso.client-id和nop.auth.sso.client-secrete是keycloak服务其中配置的客户端信息