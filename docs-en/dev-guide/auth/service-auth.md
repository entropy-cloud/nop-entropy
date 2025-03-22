# Service Authentication

Service authentication between services works the same way as for regular users, using the `accessToken`. In the implementation of `LoginService`, it can be checked whether the `accessToken` corresponds to a specific user. If not, a system user context can be created.

## Exposing All Services

The header `nop.auth.service-public: true` automatically allows all service objects to be accessed anonymously. If the `Authorization` HTTP header is sent with an empty or invalid `accessToken`, a system context will be created instead of returning a "not logged in" error message.

In the configuration file `auth-service.beans.xml`, the service paths have been configured using `nopAuthFilterConfig`.

```xml
<bean id="nopAuthFilterConfig" class="io.nop.auth.core.filter.AuthFilterConfig">
    <property name="servicePaths">
        <list>
            <value>/graphql</value>
            <!-- REST requests -->
            <value>/r/*</value>
            <!-- Returns with specified content type -->
            <value>/p/*</value>
            <!-- File upload/download -->
            <value>/f/*</value>
        </list>
    </property>

    <property name="servicePublic" value="@cfg:nop.auth.service-public|false"/>
</bean>
```

* The system user context can be set using HTTP headers such as `nop-tenant`, `nop-timezone`, and `nop-locale`, which specify the `tenantId` and `locale` respectively.
* The `id` of a system user is fixed as `sys`.
