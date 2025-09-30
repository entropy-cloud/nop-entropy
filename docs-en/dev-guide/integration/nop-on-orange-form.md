# Integrating the Nop Platform with OrangeForms

The Nop Platform is a next-generation low-code development platform built from scratch based on Reversible Computation theory. Its core components do not rely on any third-party libraries and can interoperate with most third-party software. Currently, the Nop Platform can run on top of the Quarkus and Spring frameworks, and is therefore compatible with various Spring-derived frameworks.
The domestic Solon platform is compact and efficient, with good interface isolation, so the Nop Platform can also be easily integrated with the Solon framework.

* For Solon integration, see the [nop-solon](https://gitee.com/canonical-entropy/nop-extensions/tree/master/nop-solon) project.
* For Ruoyi integration, see the [nop-for-ruoyi](https://gitee.com/canonical-entropy/nop-for-ruoyi) project.

In other words, code developed with the Nop Platform can run on multiple base frameworks (Quarkus/Spring/Solon) without any changes, enabling business code to break free from dependencies on the underlying runtime environment.

The [nop-for-orange-form](https://gitee.com/canonical-entropy/nop-for-orange-form) project integrates the Nop Platform with the open-source edition of OrangeForms and demonstrates how to integrate Sa-Token to implement login authentication and action permission checks.

## 1. Configuration Adjustments

### 1.1 Adjusting the application.yml File

The Nop Platform uses the `application.yml` and `bootstrap.yml` files on the classpath as its default configuration files, so you can directly add Nop-related configurations in the `application.yml` file.

```yaml
nop:
  debug: true
  orm:
    init-database-schema: true
  auth:
    enable-action-auth: true
```

* nop.debug enables debug mode. In debug mode, the Nop Platform will expose debugging endpoints such as `/r/DevDoc__graphql` at startup and will automatically output all model files involved to the `_dump` directory after running the Delta merge algorithm.
* nop.orm.init-database-schema enables database initialization. The Nop Platform will automatically initialize the database schema at startup.
* nop.auth.enable-action-auth enables action permission checks. The Nop Platform will call the IActionAuthChecker interface to determine whether the user has permission to access a given service function.

### 1.2 POM File Adjustments

First, modify the root POM of the `OrangeFormsOpen-MybatisPlush` project and upgrade the Spring Boot version to 3.3.3.

```xml
<properties>
  <spring-boot.version>3.3.3</spring-boot.version>
  <spring-boot-admin.version>3.3.3</spring-boot-admin.version>
</properties>
```

> The Nop Platform currently uses Spring Boot version `3.3.3` by default and supports JDK 17 and above up to the latest JDK version. At present, OrangeForms cannot run on JDK 21 or above and must use JDK 17.

Then modify the `pom.xml` of the `application-webadmin` module to include the Nop Platform base packages.

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

        <!--        <dependency>-->
        <!--            <groupId>io.github.entropy-cloud</groupId>-->
        <!--            <artifactId>nop-spring-delta</artifactId>-->
        <!--        </dependency>-->

        <dependency>
            <groupId>io.github.entropy-cloud</groupId>
            <artifactId>nop-sys-web</artifactId>
        </dependency>

        <!-- 业务组件依赖 -->
    </dependencies>
</pom>
```

* The role of `nop-bom` is similar to Spring’s `spring-boot-dependencies`. It defines the version numbers of all components in the Nop Platform so that you don’t need to specify versions when bringing in Nop components.
* `nop-spring-web-orm-starter` plays a role similar to `spring-boot-starter-xxx`. It automatically brings in all components required by NopORM and NopGraphQL. NopORM is analogous to JPA+MyBatis+Spring Data, providing a data access layer abstraction. NopGraphQL is analogous to SpringMVC+SpringGraphQL, allowing a single codebase to expose both REST and GraphQL.
* `nop-sys-web` provides common support in the Nop Platform such as serial numbers and coding rules; it is optional.
* `mybatis-plus-spring-boot3-starter` needs to be updated to version `3.5.9`. The MyBatisPlus version built into OrangeForms is older and cannot integrate with Spring Boot versions `3.3.3` and above.

## 2. Customizing the Database Connection

By default, the Nop Platform uses its own built-in dataSource definition. When integrating with OrangeForms, you can disable the internal data source definition of the Nop Platform and instead use the DataSource automatically created by the Spring framework.

The Nop Platform is built on Reversible Computation and supports full Delta customization. Specifically, without modifying any source code of the Nop Platform, you can customize all logic in the Nop Platform by adding same-named files under the Delta directory.

In the Spring framework, to customize a Bean, you need to add annotations such as `@ConditionalOnProperty` when defining the Bean, and you can only customize the entire Bean definition as a whole; you cannot target the configuration of a single property of a Bean. In the Nop Platform, you don’t need any special annotations in advance; you can use a unified Delta customization mechanism to customize any property of any Bean. Concretely, determine which model file defines the Bean, then add a same-named file under the `_delta` directory, where you can customize the Bean definition.

For the data source definition, add a `resources/_vfs/_delta/default/nop/dao/beans/dao-defaults.beans.xml` file under the application-webadmin project and include the following content:

```xml
<beans x:schema="/nop/schema/beans.xdef" xmlns:x="/nop/schema/xdsl.xdef"
       x:extends="super" x:dump="true">
    <bean id="nopDataSource" x:override="remove"/>

    <bean id="nopHikariConfig" x:override="remove"/>

    <alias name="dataSource" alias="nopDataSource"/>

</beans>
```

* `x:extends="super"` means inheriting the contents of the existing `dao-defaults.beans.xml` file in the platform, and merging the contents of this file with the inherited content. The merged result will be output to the `_dump` directory.
* `x:override="remove"` indicates deleting the Bean definition with the same name from the inherited content.
* `<alias name="dataSource" alias="nopDataSource"/>` means renaming the DataSource automatically created by the Spring framework to `nopDataSource`, so it can be referenced by the Nop Platform.

## 3. Integration with Sa-Token

OrangeForms uses Sa-Token for login authentication and action permission checks, so you need to customize the action-permission check interface in the Nop Platform.

### 3.1 Implementing the IActionAuthChecker Interface Using Sa-Token

First, add the SpringActionAuthChecker class to implement the IActionAuthChecker interface. Internally, the Nop Platform uses this interface to check action permissions.

```java
public class SpringActionAuthChecker implements IActionAuthChecker {

    @Inject
    StpInterface stpInterface;

    @Override
    public boolean isPermitted(String permission, ISecurityContext context) {
        IUserContext userContext = context.getUserContext();
        if (userContext == null)
            return false;

        String userId = userContext.getUserId();
        String loginType = "password";

        List<String> perms = stpInterface.getPermissionList(userId, loginType);

        boolean b = perms.contains(permission);
        // 假定写权限总是隐含读权限
        if (!b && permission.endsWith(":query")) {
            String prefix = StringHelper.removeTail(permission, ":query");
            b = perms.contains(prefix + ":mutation");
        }
        return b;
    }
}
```

By default, the Nop Platform does not use classpath scanning and therefore does not recognize annotations such as `@Component`. Instead, all Bean definitions must be written in `beans.xml` files. Therefore, after adding the SpringActionAuthChecker class, you need to add the following definition in a `beans.xml` file:

```xml
<bean id="nopActionAuthChecker" class="com.orangeforms.webadmin.nop.SpringActionAuthChecker"/>
```

In the example code, this is added to the `resources/_delta/default/nop/dao/beans/dao-defaults.beans.xml` file.

### 3.2 Binding IUserContext After Successful Login

The Nop Platform uses the IUserContext interface to represent logged-in user information. Therefore, after a successful login in OrangeForms, you need to construct an IUserContext object from the internal login information and bind it to the Nop Platform’s context.

Specifically, modify the AuthenticationInterceptor class and add the bindUserContext call.

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
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex)
            throws Exception {
        // 这里需要空注解，否则sonar会不happy。
        AuthHelper.unbindUserContext();
    }
}    
```

You also need to modify the URL patterns recognized by AuthenticationInterceptor in InterceptorConfig.

```java
public class InterceptorConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        // @FIX 修改拦截器配置，增加拦截路径
        //registry.addInterceptor(new AuthenticationInterceptor()).addPathPatterns("/admin/**");
        AuthenticationInterceptor interceptor = new AuthenticationInterceptor();
        registry.addInterceptor(interceptor)
                .addPathPatterns("/admin/**", "/r/**", "/p/**", "/f/**", "/graphql");
    }
}
```

The Nop Platform only uses a few URL endpoints:

* `/r/{bizObjName}__{bizMethod}` invokes the specified method on a BizModel object via REST. The return value will be wrapped in `ApiResponse<T>`.
* `/p/{bizObjName}__{bizMethod}` invokes the specified method on a BizModel object via REST. Unlike `/r/`, the method’s return value will be returned directly, without being wrapped in `ApiResponse`.
* `/f/download` and `/f/upload` are used for file upload and download.
  * `/graphql` is used to invoke methods on BizModel objects via the GraphQL protocol. The same method can be invoked via `/graphql`, `/r/`, and `/p/`.

## 4. Adding the DemoBizModel Example

NopGraphQL integrates NopORM under the hood and can implement most CRUD operations directly via REST requests without writing any code. At the overall design level, it can provide capabilities similar to APIJSON, while also offering stricter security checks and better extensibility. See [Feature comparison between the Nop Platform and APIJSON](https://mp.weixin.qq.com/s/vrQVGs-c0dVWcOJEsOz_nA).

NopGraphQL is much simpler to use than SpringMVC. Take DemoBizModel as an example:

```java
@BizModel("Demo")
public class DemoBizModel {
    @BizQuery
    public String helloWithAuth(@Name("message") String message) {
        return "hello:" + message;
    }

    @BizQuery
    @Auth(publicAccess = true)
    public String hello(@Name("message") @Optional String message) {
        return "hello:" + message;
    }
}
```

* Service object classes don’t need to inherit from any base class; just add the `@BizModel` annotation. If the BizModel is generated from a data model, it will inherit from the CrudBizModel base class, which automatically implements a series of CRUD operations for entities.
* For query methods, add the `@BizQuery` annotation; method parameters can be annotated with `@Name` to specify parameter names.
* You can add the `@Auth` annotation to specify access permissions. `publicAccess` indicates whether anonymous access is allowed. If `@Auth` is not added, permissions are still checked by default, which is equivalent to automatically setting the permission to `{bizObjName}:{methodName}`.
* If a parameter is optional, annotate it with `@Optional`.
* Return values don’t need to be wrapped in Response or ResultBean. The NopGraphQL engine decides how to wrap return values based on the invocation path. For example, for `/r/` calls with a String return type, the result will be wrapped as `ApiResponse<String>`. If an error occurs during execution, the exception code will automatically be translated into a localized error message and returned in the message field of ApiResponse.

Compared with SpringMVC’s Controller mechanism, DemoBizModel has far fewer conventions. The input parameters and return values are plain Java objects without any framework-specific dependencies. You don’t need to specify REST path patterns or whether to use GET or POST.
NopGraphQL introduces several global conventions:

1. The invocation path uses the fixed pattern `/r/{bizObjName}__{methodName}`, where `{bizObjName}` is the BizModel name and `{methodName}` is the BizModel method name.
2. Read-only operations allow both GET and POST, while write operations allow only POST.
3. Write operations are marked with `@BizMutation`. The framework automatically opens a transaction for write operations, so there’s no need to annotate with `@Transactional`.
4. All parameters are passed via URL params or JSON body and are automatically mapped to method parameters by name.

Note that the Nop Platform does not use class scanning (frameworks like Quarkus that support native compilation don’t recommend runtime classpath scanning). It also needs to support the Delta customization mechanism, allowing third parties to adjust Bean configurations without modifying source code. Therefore, merely annotating a class with `@BizModel` will not automatically register the service object; you need to register it in a `beans.xml` file.

In the example code, add the following in the `resource/_vfs/app/demo/beans/app-demo.beans.xml` file:

```xml
<beans x:schema="/nop/schema/beans.xdef" xmlns:x="/nop/schema/xdsl.xdef">
  <bean id="DemoBizModel" class="com.orangeforms.webadmin.nop.DemoBizModel"/>
</beans>
```

In the future, if other projects want to perform customization on top of an existing product, they can add a same-path `app-demo.beans.xml` file under the `_delta` directory and replace the Bean implementation class with a derived class. For example:

```xml
<beans x:schema="/nop/schema/beans.xdef" xmlns:x="/nop/schema/xdsl.xdef" x:extends="super">
  <bean id="DemoBizModel" class="app.ext.DemoBizModelEx"/>
</beans>
```

* With `x:extends="super"`, the merger will match Beans by id and automatically merge all attributes and child node definitions.

Another technical point to note: `/app/demo` is a two-level directory that represents a module directory in the Nop Platform. You need to add an empty `_module` file under this directory. During startup, the Nop Platform scans all `_module` files and automatically loads all `beans.xml` files in the module’s beans directory that are prefixed with `app-`, thereby achieving an effect similar to SpringAutoConfiguration.

## 5. Fixing Integration Issues

OrangeForms globally configures result serialization for all REST requests and automatically serializes JSON return objects to text. However, the Nop Platform has already implemented all JSON serialization conversions under the hood, so this conversion is redundant and should be disabled. Modify the implementation of the CommonWebMvcConfig class:

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

            //@FIX Nop平台的链接不要进行JSON转换
            if (uri.startsWith("/r/") || uri.startsWith("/p/") || uri.startsWith("/f/") || uri.startsWith("/graphql"))
                return false;
            return super.canWrite(type, clazz, mediaType);
        }
    }
}
```

The low-code platform Nop Platform designed based on Reversible Computation has been open sourced:

- gitee: [https://gitee.com/canonical-entropy/nop-entropy](https://gitee.com/canonical-entropy/nop-entropy)
- github: [https://github.com/entropy-cloud/nop-entropy](https://github.com/entropy-cloud/nop-entropy)
- gitcode: [https://gitcode.com/canonical-entropy/nop-entropy](https://gitcode.com/canonical-entropy/nop-entropy)
- Development tutorial: [https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/tutorial/tutorial.md](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/tutorial/tutorial.md)
- Reversible Computation principles and an introduction and Q&A on the Nop Platform: [https://www.bilibili.com/video/BV14u411T715/](https://www.bilibili.com/video/BV14u411T715/)
- Official international site: [https://nop-platform.github.io/](https://nop-platform.github.io/)
- A community Nop development practice site by Crazydan Studio: [https://nop.crazydan.io/](https://nop.crazydan.io/)
<!-- SOURCE_MD5:bcfe6b4bf0d46e3df995509c4a111a4f-->
