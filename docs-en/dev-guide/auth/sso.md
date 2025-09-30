# Enable SSO Support

The system has built-in support for the Keycloak single sign-on server. It can directly integrate with an external Keycloak server without using the NopAuthUser table built into the nop-auth module at all. User roles are also retrieved directly from the Keycloak server.

1. Introduce the nop-auth-sso module, which provides OAuthLoginServiceImpl; it replaces the default implementation LoginServiceImpl provided by the nop-auth-service module
2. Enable SSO-related settings in application.yaml

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

* nop.auth.sso.server-url corresponds to the Keycloak SSO server URL
* nop.auth.sso.realm is the realm configured in Keycloak
* nop.auth.sso.client-id and nop.auth.sso.client-secret are the client information configured on the Keycloak server

## Single Sign-Out

By accessing the following endpoint, you can invoke nop.auth.sso.logout-url to log out:

```
POST /r/LoginApi__ssoLogout

{
   "accessToken": "xxx"
}
```

## Permission Constraints

The Nop platform only uses the user and role configuration from the SSO server. For finer-grained resource access permissions, configuration is required within the Nop platform. The Nop platform controls user operation permissions by specifying which resource objects a Role can access.

<!-- SOURCE_MD5:a9967c3ce9dc988961005b92849b09fa-->
