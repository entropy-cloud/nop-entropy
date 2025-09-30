
# Service Authentication

The authentication method between services is the same as for regular users, using an accessToken. In the implementation of LoginService, you can determine whether the accessToken corresponds to a specific user; if not, you can create a system user context.

## Expose All Services

`nop.auth.service-public: true` will automatically allow all service objects to be accessed anonymously. If the Authorization HTTP header carries an empty accessToken or fails to parse, a system context will be created automatically, instead of returning a [Not Logged In] error.

In the `auth-service.beans.xml` configuration file, service paths are configured via nopAuthFilterConfig,

```xml
<bean id="nopAuthFilterConfig" class="io.nop.auth.core.filter.AuthFilterConfig">
        <property name="servicePaths">
            <list>
                <value>/graphql*</value>
                <!-- REST request -->
                <value>/r/*</value>
                <!-- Return content with the specified contentType -->
                <value>/p/*</value>
                <!-- File upload and download -->
                <value>/f/*</value>
            </list>
        </property>
    
        <property name="servicePublic" value="@cfg:nop.auth.service-public|false"/>
    </bean>
```

* You can use HTTP headers such as nop-tenant, nop-timezone, nop-locale to set the system user context's tenantId and locale, among other information
* The system user's ID is fixed as sys

<!-- SOURCE_MD5:44bf0b809991e879a4398b0461e6f565-->
