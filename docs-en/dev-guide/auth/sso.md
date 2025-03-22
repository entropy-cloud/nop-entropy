# Enable SSO Support

The system natively supports Keycloak single sign-on (SSO) servers and can directly integrate with external Keycloak servers without relying on the internal NopAuthUser table. User roles are also fetched directly from the Keycloak server.

1. Import the `nop-auth-sso` module, which provides an OAuth-based login implementation (`OAuthLoginServiceImpl`) that replaces the default `LoginServiceImpl` from the `nop-auth-service` module.
2. Enable SSO configuration in your `application.yaml`.

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

* `nop.auth.sso.server-url` corresponds to the URL of the Keycloak server.
* `nop.auth.sso.realm` refers to the realm configured in Keycloak.
* `nop.auth.sso.client-id` and `nop.auth.sso.client-secret` are the client credentials from the Keycloak server.

## Single Sign-out

Single sign-out can be triggered by accessing the following endpoint:

```bash
POST /r/LoginApi__ssoLogout
```

```json
{
  "accessToken": "xxx"
}
```

## Access Control

The Nop platform only uses user and role configurations from the Keycloak server for authentication. For finer-grained access control over resources, you need to configure these settings within the Nop platform. The platform allows you to specify which roles can access which resources to enforce user access restrictions.
