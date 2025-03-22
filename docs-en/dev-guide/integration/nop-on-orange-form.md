# Nop Platform Integration with Orange Single (OrangeForm)

The Nop platform is built from scratch based on reversible computing theory, representing the next-generation low-code development platform. Its core components do not rely on any third-party libraries and can be seamlessly integrated with most third-party software. Currently, the Nop platform can run on Quarkus and Spring frameworks, making it compatible with various Spring-derived frameworks.
The Solon platform is domestically developed and is known for its compactness and good interface isolation, which allows for easy integration with the Solon framework as well.

* For Solon integration, refer to the [nop-solon](https://gitee.com/canonical-entropy/nop-extensions/tree/master/nop-solon) project.
* For Ruoyi integration, refer to the [nop-for-ruoyi](https://gitee.com/canonical-entropy/nop-for-ruoyi) project.

This means that no modifications are required to the business logic when developing with the Nop platform. The code can run on multiple underlying frameworks (Quarkus/Spring/Solon), thereby decoupling the business logic from dependencies on the underlying runtime environment.

The [nop-for-orange-form](https://gitee.com/canonical-entropy/nop-for-orange-form) project demonstrates the integration of Nop platform with the open-source version of OrangeForms. It illustrates how to integrate sa-tokens for login authentication and operation permission checks.

## Configuration Adjustments

### 1.1 `application.yml` File Adjustment

The Nop platform uses `application.yml` and `bootstrap.yml` files in the classpath as its default configuration files. Therefore, you can directly add Nop platform-related configurations to the `application.yml` file.

```yaml
nop:
  debug: true
  orm:
    init-database-schema: true
  auth:
    enable-action-auth: true
```

- **nop.debug**: Enables debug mode. In debug mode, the Nop platform exposes debugging interfaces such as `/r/DevDoc__graphql` during startup and automatically outputs all used model files to the `_dump` directory after completing the Delta difference algorithm.
- **nop.orm.init-database-schema**: Initializes the database schema. The Nop platform will automatically initialize the database table structure at startup.
- **nop.auth.enable-action-auth**: Enables action authentication. The Nop platform calls the `IActionAuthChecker` interface to check whether a user has permission to access a specific service function.

### 1.2 POM File Adjustment

First, modify the root POM file of the `OrangeFormsOpen-MybatisPlush` project to upgrade the Spring Boot version to 3.3.3.

```xml
<properties>
  <spring-boot.version>3.3.3</spring-boot.version>
  <spring-boot-admin.version>3.3.3</spring-boot-admin.version>
</properties>
```

- The current default Spring Boot version in the Nop platform is `3.3.3`, supporting JDK 17 and above.
- However, OrangeSingle cannot run on JDK 21 or higher; it only supports up to JDK 17.

Next, modify the POM file of the `application-webadmin` module to include the basic packages of the Nop platform.

```xml
<pom>
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>io.github.entropy-cloud</groupId>
                <artifactId>nop-bom</artifactId>
                <version>2.0.0-SNAPSHOT</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
            <version>3.5.9</version>
        </dependency>

        <dependency>
            <groupId>io.github.entropy-cloud</groupId>
            <artifactId>nop-spring-web-orm-starter</artifactId>
        </dependency>

        <!--
        <dependency>
            <groupId>io.github.entropy-cloud</groupId>
            <artifactId>nop-spring-delta</artifactId>
        </dependency>
        -->

        <dependency>
            <groupId>io.github.entropy-cloud</groupId>
            <artifactId>nop-sys-web</artifactId>
        </dependency>

        <!-- Business Component Dependencies -->
    </dependencies>
</pom>
```

* `nop-bom` serves a similar purpose to `spring-boot-dependencies`, defining the versions of all Nop platform components, allowing developers to omit version declarations when referencing Nop components.
* `nop-spring-web-orm-starter` functions similarly to `spring-boot-starter-*`, automatically including dependencies for NopORM and NopGraphQL. NopORM acts like JPA + MyBatis + Spring Data by abstracting the data access layer, while NopGraphQL provides support for both REST and GraphQL endpoints in a single codebase.
* `nop-sys-web` provides basic components such as sequence numbers and encoding rules, serving as a general-purpose module.
* The version of `mybatis-plus-spring-boot3-starter` has been updated to `3.5.9` due to its default MyBatis-Plus version being lower than the required `3.3.3` for Spring Boot integration.

## Custom Database Connection

The Nop platform defaults to using its internal `dataSource` definition, which can be disabled when integrating with Spring's `DataSource`. This allows developers to use Spring's own `DataSource` instead of the one provided by Nop.

The foundation of Nop is built on reversible computation theory, supporting full customization through differential calculus without modifying a single line of code in the Nop platform. Specifically, you can add corresponding files to the Delta directory to customize all logic within Nop without altering its source code.

For Spring components that require customization, developers must add `@ConditionalOnProperty` annotations during bean definition and cannot target specific attributes or properties of individual beans for customization. Customization must be applied at the entire bean level rather than to specific bean properties.

# Customizing Bean Properties in Nop Platform

In the Nop platform, no prior annotations are required. You can use unified Delta customization to customize any bean's properties. Specifically, this involves determining which model file defines the bean and then creating a corresponding file in the `_delta` directory. This allows you to customize the bean's definition.

# Example XML Configuration

For data sources, add the following content to the `application-webadmin` project under the `resources/_vfs/_delta/default/nop/dao/beans/` directory:

```xml
<beans x:schema="/nop/schema/beans.xdef" xmlns:x="/nop/schema/xdsl.xdef"
       x:extends="super" x:dump="true">
    <bean id="nopDataSource" x:override="remove"/>

    <bean id="nopHikariConfig" x:override="remove"/>

    <alias name="dataSource" alias="nopDataSource"/>
</beans>
```

- `x:extends=super`: Indicates inheritance from the default `dao-defaults.beans.xml` file. The merged result will be output in the `_dump` directory.
- `x:override="remove"`: Removes the existing bean definition with the same ID.
- `<alias name="dataSource" alias="nopDataSource">`: Creates an alias for `nopDataSource`, allowing it to be referenced in the Nop platform.

# Integrating with SaToken

The Nop platform uses **SaToken** as the login validation and operation permission check framework. To implement this, you need to customize the operation permission check interface.

# 3.1 Implementing IActionAuthChecker Interface

First, implement `SpringActionAuthChecker` class adhering to the `IActionAuthChecker` interface. The Nop platform internally uses this interface for operation permission checks.

```java
public class SpringActionAuthChecker implements IActionAuthChecker {

    @Inject
    private StpInterface stpInterface;

    @Override
    public boolean isPermitted(String permission, ISecurityContext context) {
        IUserContext userContext = context.getUserContext();
        if (userContext == null) {
            return false;
        }

        String userId = userContext.getUserId();
        String loginType = "password";

        List<String> perms = stpInterface.getPermissionList(userId, loginType);

        boolean result = perms.contains(permission);
        // Assuming write permissions implicitly grant read permissions
        if (!result && permission.endsWith(":query")) {
            String prefix = StringHelper.removeTail(permission, ":query");
            result = perms.contains(prefix + ":mutation");
        }
        return result;
    }
}
```

# Adding SpringActionAuthChecker to Beans

Since the Nop platform does not use class scanning, all beans must be explicitly defined in `beans.xml`. After implementing `SpringActionAuthChecker`, add the following to a `beans.xml` file:

```xml
<bean id="nopActionAuthChecker" class="com.orangeforms.webadmin.nop.SpringActionAuthChecker"/>
```

### 2.1 Example Code Location

The example code is located in the file `resources/_delta/default/nop/dao/beans/dao-defaults.beans.xml`.

### 3.2 Binding IUserContext After Login

The Nop platform uses the `IUserContext` interface to represent user login information. After successful login, you need to construct an `IUserContext` object based on internal login data and bind it with the Nop platform's context.

To achieve this, modify the `AuthenticationInterceptor` class by adding a `bindUserContext` call.

```java
public class AuthenticationInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String appCode = this.getAppCodeFromRequest(request);
        if (StrUtil.isNotBlank(appCode)) {
            return this.handleThirdPartyRequest(appCode, request);
        }
        ResponseResult<Void> result = saTokenUtil.handleAuthIntercept(request, handler);
        if (!result.isSuccess()) {
            ResponseResult.output(result.getHttpStatus(), result);
            return false;
        }

        TokenData tokenData = TokenData.takeFromRequest();
        if (tokenData == null) {
            return true;
        }
        AuthHelper.bindUserContext(tokenData);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                              Object handler, Exception ex)
            throws Exception {
        // Empty comment to prevent SonarQube from being unhappy
        AuthHelper.unbindUserContext();
    }
}
```

Additionally, modify the `InterceptorConfig` class to include the URL patterns recognized by `AuthenticationInterceptor`.

```java
public class InterceptorConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        AuthenticationInterceptor interceptor = new AuthenticationInterceptor();
        registry.addInterceptor(interceptor)
                .addPathPatterns("/admin/**", "/r/**", "/p/**", "/f/**", "/graphql");
    }
}
```

### 3.3 Limited URL Endpoints

The Nop platform only uses a limited number of URL endpoints:

- `/r/{bizObjName}__{bizMethod}`: Invokes the specified method on the `BizModel` object using REST.
- The result is wrapped in an `ApiResponse<T>` object.


* `/p/{bizObjName}__{bizMethod}` called using REST to call the specified method on BizModel, different from `/r/` which wraps the result in `ApiResponse`
* `/f/download` and `/f/upload` used for file upload and download
* `/graphql` used to call the specified method on BizModel via GraphQL protocol. The same method can be called using `/graphql`, `/r/`, or `/p/`.


## Four. Add DemoBizModel Example

NopGraphQL integrates NopORM at its core, allowing most CRUD operations to be performed via REST requests without writing any code. From a design perspective, it implements functionality similar to APIJSON, while also providing stricter security validation and better expandability.
See [Comparison of Nop Platform and APIJSON](https://mp.weixin.qq.com/s/vrQVGs-c0dVWcOJEsOz_nA).

Using NopGraphQL is significantly simpler than SpringMVC. For example:

```java
@BizModel("Demo")
public class DemoBizModel {
    @BizQuery
    public String helloWithAuth(@Name("message") @Optional String message) {
        return "hello:" + message;
    }

    @BizQuery
    @Auth(publicAccess = true)
    public String hello(@Name("message") String message) {
        return "hello:" + message;
    }
}
```

* Service objects do not need to inherit from any base class. They only require the `@BizModel` annotation. If generated based on data models, they will inherit from CrudBizModel and automatically implement CRUD operations.
* For query methods, add the `@BizQuery` annotation. Parameters can be annotated with `@Name` to specify their names.
* Add the `@Auth` annotation to specify access permissions. The `publicAccess` property determines if anonymous access is allowed. If not specified, default behavior will check permissions based on `{bizObjName}:{methodName}`.
* If parameters are optional, add the `@Optional` annotation.
* Results do not need additional wrapping as Response or ResultBean. The NopGraphQL engine will package results based on the call type. For example, calling via `/r/` for String returns will be wrapped in `ApiResponse<String>`. Errors will be translated into localized error messages and stored in the `message` field of `ApiResponse`.
* Compared to SpringMVC's Controller mechanism, DemoBizModel has much fewer conventions. Input parameters and return values are standard Java objects without specific framework dependencies or URL pattern specifications.

NopGraphQL introduces some global conventions:

1. URLs use the format `/r/{bizObjName}__{methodName}` where `{bizObjName}` is the BizModel name and `{methodName}` is the method name.
2. Read operations allow both GET and POST methods, while write operations only allow POST.
3. Use `@BizMutation` to mark write operations. The framework automatically handles database transactions without needing the `@Transactional` annotation.
4. Parameters are passed via URL params or JSON body, automatically mapped by name.

Note: Nop platform does not use class scanning (like Quarkus for native compilation) at runtime and requires explicit configuration through `beans.xml`.

Example code to be added in `resource/_vfs/app/demo/beans/app-demo.beans.xml`:
```xml
<bean id="demoBizModel" class="com.example.DemoBizModel">
    <biz-model>Demo</biz-model>
    <biz-query>
        <method name="helloWithAuth">
            <param type="java.lang.String" name="message"/>
        </method>
        <auth public-access="true"/>
    </biz-query>
    <biz-query>
        <method name="hello">
            <param type="java.lang.String" name="message"/>
        </method>
    </biz-query>
</bean>
```

```xml
<beans x:schema="/nop/schema/beans.xdef" xmlns:x="/nop/schema/xdsl.xdef">
  <bean id="DemoBizModel" class="com.orangeforms.webadmin.nop.DemoBizModel"/>
</beans>
```

For future enhancements or customizations in existing projects, you can add a `app-demo.beans.xml` file under the `_delta` directory to override the bean implementation. For example:

```xml
<beans x:schema="/nop/schema/beans.xdef" xmlns:x="/nop/schema/xdsl.xdef" x:extends="super">
  <bean id="DemoBizModel" class="app.ext.DemoBizModelEx" x:extends="super"/>
</beans>
```

When merging using `x:extends=super`, it will find the corresponding bean by ID and automatically merge all its properties and child nodes.

One important technical point is that `/app/demo` refers to a module directory within the Nop platform. In this directory, you need to add an empty `_module` file. The Nop platform will scan all `_module` files upon startup and automatically load modules by looking for `beans.xml` files under the `app-` prefixed directories, achieving behavior similar to Spring's `SpringAutoConfiguration`.

## Five. Fixing Integration Issues

Orange (Nop) has unified JSON serialization for all REST responses throughout the system. However, the Nop platform already handles all JSON serialization internally. Thus, this additional layer is redundant and should be disabled.

To achieve this, modify the `CommonWebMvcConfig` implementation.

```java
@Configuration
public class CommonWebMvcConfig implements WebMvcConfigurer {

    public static class MyFastJsonHttpMessageConverter extends FastJsonHttpMessageConverter {

        @Override
        public boolean canWrite(Type type, Class<?> clazz, MediaType mediaType) {
            HttpServletRequest request = ContextUtil.getHttpRequest();
            if (request == null) {
                return super.canWrite(type, clazz, mediaType);
            }
            if (request.getRequestURI().contains("/v3/api-docs")) {
                return false;
            }
            String uri = request.getRequestURI();

            // Fix: Do not perform JSON conversion for Nop links
            if (uri.startsWith("/r/") || uri.startsWith("/p/") || uri.startsWith("/f/") || uri.startsWith("/graphql")) {
                return false;
            }
            return super.canWrite(type, clazz, mediaType);
        }
    }
}
```

The Nop platform's link handling should not perform JSON conversion. This configuration ensures that requests for specific endpoints are excluded from JSON conversion.

## Five. Fixing Integration Issues

Nop provides a modular architecture that allows developers to extend its functionality by adding modules. For example, you can create a module by placing an empty `_module` file in the `/app/demo` directory. The Nop platform will automatically scan this directory and load any `beans.xml` files found within it, enabling similar behavior to Spring's auto-configuration.

## Five. Integration Fix

The Orange (Nop) platform includes built-in JSON serialization for all REST responses. However, this causes unnecessary duplication since the underlying implementation already handles JSON serialization. To avoid redundant processing:

1. Modify the `CommonWebMvcConfig` class.
2. Extend `FastJsonHttpMessageConverter` to disable automatic JSON conversion for specific endpoints.

```java
public static class MyFastJsonHttpMessageConverter extends FastJsonHttpMessageConverter {

    @Override
    public boolean canWrite(Type type, Class<?> clazz, MediaType mediaType) {
        HttpServletRequest request = ContextUtil.getHttpRequest();
        if (request == null) {
            return super.canWrite(type, clazz, mediaType);
        }
        if (request.getRequestURI().contains("/v3/api-docs")) {
            return false;
        }
        String uri = request.getRequestURI();

        // Fix: Disable JSON conversion for Nop links
        if (uri.startsWith("/r/") || uri.startsWith("/p/") || uri.startsWith("/f/") || uri.startsWith("/graphql")) {
            return false;
        }
        return super.canWrite(type, clazz, mediaType);
    }
}
```

## Five. Integration Fix

The Orange (Nop) platform's JSON serialization for REST responses is redundant due to its internal handling of the same process. To disable this:

1. Extend `FastJsonHttpMessageConverter` and override the `canWrite` method.
2. Check request paths and prevent JSON conversion for specific endpoints.

```java
@Configuration
public class CommonWebMvcConfig implements WebMvcConfigurer {

    public static class MyFastJsonHttpMessageConverter extends FastJsonHttpMessageConverter {

        @Override
        public boolean canWrite(Type type, Class<?> clazz, MediaType mediaType) {
            HttpServletRequest request = ContextUtil.getHttpRequest();
            if (request == null) {
                return super.canWrite(type, clazz, mediaType);
            }
            if (request.getRequestURI().contains("/v3/api-docs")) {
                return false;
            }
            String uri = request.getRequestURI();

            // Disable JSON conversion for Nop links
            if (uri.startsWith("/r/") || uri.startsWith("/p/") || uri.startsWith("/f/") || uri.startsWith("/graphql")) {
                return false;
            }
            return super.canWrite(type, clazz, mediaType);
        }
    }
}
```

## Five. Integration Fix

The Orange (Nop) platform allows developers to extend its functionality by creating custom modules. To do this:

1. Create an empty `_module` file in the `/app/demo` directory.
2. Nop will automatically scan and load any `beans.xml` files within module directories, enabling modular extension.

## Five. Integration Fix

For further customization:

- **GitHub**: [https://github.com/entropy-cloud/nop-entropy](https://github.com/entropy-cloud/nop-entropy)
- **Gitee**: [https://gitee.com/canonical-entropy/nop-entropy](https://gitee.com/canonical-entropy/nop-entropy)

- GitCode: [https://gitcode.com/canonical-entropy/nop-entropy](https://gitcode.com/canonical-entropy/nop-entropy)
- Development Example: [https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/tutorial/tutorial.md](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/tutorial/tutorial.md)
- Reversible Computation Principle and Nok Platform Introduction and Q&A: [https://www.bilibili.com/video/BV14u411T715/](https://www.bilibili.com/video/BV14u411T715/)
- Official International Website: [https://nop-platform.github.io/](https://nop-platform.github.io/)
- Community-Created Nok Platform Development Practice Sharing Site by Crazydan Studio: [https://nop.crazydan.io/](https://nop.crazydan.io/)
