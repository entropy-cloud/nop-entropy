# Login Logic

## External Public Links

The login logic is defined in the `[auth-service.beans.xml](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-auth/nop-auth-service/src/main/resources/_vfs/nop/auth/beans/auth-service.beans.xml)` file, which contains `nopAuthHttpServerFilter` and `nopAuthFilterConfig`. The `AuthHttpServerFilter` handles all user login checks. If no login is detected, it returns a HTTP 401 error or redirects to the login page.

## Public Links

The `authFilter` uses the configurations from `AuthFilterConfig` to determine which paths are public.

By default, the following paths are opened:

1. `/r/LoginApi_*` - Related to login
2. `/q/health*` - Health check interfaces
3. `/q/metrics*` - Internal metrics interfaces

## Custom Login Logic

There are two ways to customize the login logic:

### 1. Customize `AuthHttpServerFilter`

You can inherit from `AuthHttpServerFilter` and define a bean with the same ID (`nopAuthHttpServerFilter`). This will override the default authentication filter.

```xml
<bean id="nopAuthServerFilter" class="xxx.MyFilter" />
```

> Note: The built-in `nopAuthServerFilter` has the property `ioc:default=true`. If another bean with the same ID is defined, it will automatically replace the default `authFilter`.

### 2. Customize `ILoginService`

The `authFilter` uses the `ILoginService` interface for login verification. You can provide your own implementation of this interface to override the default login logic. This method is preferred if you need to handle cookie binding or other web-related logic, as it cannot be done directly with `AuthHttpServerFilter`.

To implement custom login logic using `ILoginService`, see [sso.md](sso.md).

## Configuration Items

1. `nop.auth.login.use-dao-user-context-cache`  
   Setting this to `true` enables `DaoUserContextCache`. This cache stores user context data in the `NopAuthSession` table, which is derived from `IUserContext`.


2. nop.auth.access-token-expire-seconds
   Access token expiration time, default to 30\*60, which is 30 minutes.

3. nop.auth.refresh-token-expire-seconds
   Refresh token expiration time, default to 300\*60, which is 5 hours.