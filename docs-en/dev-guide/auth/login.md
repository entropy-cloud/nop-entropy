# Login Logic

## External Public Links

In [auth-service.beans.xml](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-auth/nop-auth-service/src/main/resources/_vfs/nop/auth/beans/auth-service.beans.xml), nopAuthHttpServerFilter and nopAuthFilterConfig are defined. AuthHttpServerFilter is responsible for performing all user login checks; if it detects that the user is not logged in, it returns an HTTP 401 error or redirects to the login page.

## Public Links

authFilter uses the configuration in AuthFilterConfig to determine which paths are public.

By default, the following paths are open:

1. /r/LoginApi\_\* and other login-related endpoints
2. /q/health\* and other health check endpoints
3. /q/metrics\* and other internal metrics endpoints

```xml
 <bean id="nopAuthFilterConfig" class="io.nop.auth.core.filter.AuthFilterConfig">
        <!-- When unspecified, pages are public by default, mainly js/css/images, etc. -->
        <property name="defaultPublic" value="true"/>

        <property name="publicPaths">
            <list>
                <value>/r/LoginApi_*</value>
                <value>/q/health*</value>
                <value>/q/metrics*</value>
            </list>
        </property>

        <property name="authPaths">
            <list>
                <value>/graphql*</value>
                <!-- REST requests -->
                <value>/r/*</value>
                <!-- Quarkus built-in management pages -->
                <value>/q/*</value>
                <!-- Return content with a specified contentType -->
                <value>/p/*</value>
                <!-- File upload/download -->
                <value>/f/*</value>
            </list>
        </property>
    </bean>
```

## Customize Login Logic

There are two ways to customize the login logic.

### 1. Customize AuthHttpServerFilter

If you need to customize the login logic, you can extend AuthHttpServerFilter, then define a bean with the id nopAuthHttpServerFilter to override the platform’s built-in authFilter.

```xml
<bean id="nopAuthServerFilter" class="xxx.MyFilter" />
```

> Because the built-in nopAuthServerFilter in the platform is marked with `ioc:default=true`, as long as another bean with the same name is found, it will automatically override the platform’s built-in authFilter.

### 2. Customize ILoginService

During actual login verification, authFilter uses the ILoginService interface. You can provide an implementation of ILoginService to override the system’s built-in login logic. Unlike AuthFilter, the Web environment is not accessible here, so logic involving Web environment handling can only be implemented by extending AuthHttpServerFilter (for example, modifying cookie-binding logic, etc.).

Currently, integration with Keycloak single sign-on is implemented by adding the OAuthLoginServiceImpl class; see [sso.md](sso.md).

## Configuration Options

1. nop.auth.login.use-dao-user-context-cache
   When set to true, enables DaoUserContextCache, saving the information from IUserContext into the NopAuthSession table.

2. nop.auth.access-token-expire-seconds
   Access token expiration time in seconds; defaults to 30\*60, i.e., 30 minutes.

3. nop.auth.refresh-token-expire-seconds
   Refresh token expiration time in seconds; defaults to 300\*60, i.e., 5 hours.

<!-- SOURCE_MD5:525bacb5974244c2b0c11f735249725a-->

## Concurrent Session Switch

To control whether the same username can have multiple concurrent login sessions, use the following configuration:

- `nop.auth.login.allow-multiple-sessions-same-user` (default: false)

Behavior:
- When `false` (default): logging in will automatically log out other active sessions under the same username (handled in `LoginServiceImpl.autoLogout`).
- When `true`: allows multiple concurrent sessions for the same username; logging in will not auto-logout other sessions.

Implementation notes:
- This parameter is injected in `LoginServiceImpl` via `@InjectValue("@cfg:nop.auth.login.allow-multiple-sessions-same-user|false")` and is a static, startup-time configuration. It is not dynamically changeable at runtime.
