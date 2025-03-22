# Introduction to Nop: Integrating with Spring Security

When integrating Spring with SpringSecurity, some users prefer to maintain their previous login authentication mechanism and do not want to introduce user, role tables from the Nop platform. In this case, you can avoid introducing the `nop-auth-service` service and only adapt a few interfaces.

For example, refer to the `nop-spring-security-demo` for sample projects.

## 1. Importing nop-spring-web-starter Dependency

This package will use SpringBoot's auto-discovery mechanism to start up the Nop platform and register `SpringGraphWebService`.

```xml
<dependencies>
  <dependency>
    <groupId>io.github.entropy-cloud</groupId>
    <artifactId>nop-spring-web-starter</artifactId>
  </dependency>

  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
  </dependency>
</dependencies>
```

Then, enable the following configuration in the `application.yaml` configuration file:

```yaml
nop:
  auth:
    enable-data-auth: true
    enable-action-auth: true
    http-server-filter:
      enabled: false
```

This enables data permission and operation permission checks while disabling the built-in `AuthHttpServerFilter`, which was previously responsible for checking login status.

## 2. Implementing IActionAuthChecker Interface

The Nop platform uses the `IActionAuthChecker` interface to check operation permissions, while SpringSecurity uses the `PermissionEvaluator` interface.

```java
public class SpringActionAuthChecker implements IActionAuthChecker {
    private static final Logger LOG = LoggerFactory.getLogger(SpringActionAuthChecker.class);

    @Inject
    private PermissionEvaluator permissionEvaluator;

    @Override
    public boolean isPermitted(String permission, ISecurityContext context) {
        LOG.info("nop.check-action-auth:permission={},user={}", permission, context.getUserContext());

        if (context.getUserContext() == null) {
            return false;
        }
        final IUserContext userContext = context.getUserContext();

        // Token construction needs to be determined based on the specific authorization framework
        UserIdToken token = new UserIdToken(userContext.getUserId());
        return permissionEvaluator.hasPermission(token, "BizObject", permission);
    }
}
```

Generally, the permission format in the Nop platform is `{bizObjName}:{method}`, and when generating code, it will generate permissions like `NopAuthUser:query` and `NopAuthUser:mutation`, corresponding to read operations (GraphQL query) and write operations (GraphQL mutation). Based on specific business requirements, you can further refine by separating upload and download, etc.

Of course, the permission format does not have strict requirements. It is internally agreed upon as needed, as long as the underlying `PermissionEvaluator` can recognize it.

## 3. SpringSecurity Login Subsequently Initializes Nop Platform's User Context


```javascript
 void onLoginSuccess(IContext context, HttpServletRequest request) {
      String userId = "nop";
      String userName = "123";

      // Here should follow framework-specific token setting logic, just example provided
      SecurityContext secureContext = SecurityContextHolder.getContext();
      secureContext.setAuthentication(new UserIdToken(userId));
      request.setAttribute(RequestAttributeSecurityContextRepository.DEFAULT_REQUEST_ATTR_NAME, secureContext);

      // context stores basic information, while IUserContext holds more user-related data
      context.setUserId(userId);
      context.setUserName(userName);

      // simulate setting user context after login success
      UserContextImpl userContext = new UserContextImpl();
      userContext.setAccessToken("aaa");
      userContext.setLastAccessTime(CoreMetrics.currentTimeMillis());
      userContext.setUserId(userId);
      userContext.setUserName(userName);
      userContext.setRoles(Set.of("manager", "checker"));

      IUserContext.set(userContext);
  }
```

* SecurityContextHolder is the place in SpringSecurity where the security context is stored after successful login. The UserIdToken used here is just an example; the actual content depends on the specific login framework.
* To support asynchronous calls, we need to save the security context into the HttpServletRequest. Otherwise, when the controller returns an asynchronous result, the context may be lost.

## Four. Adjust SpringSecurity Configuration

The ContextHttpServerFilter in the Nop platform needs to be executed before the SpringSecurity filter chain. Within the SpringSecurity filter, the onLoginSuccess function will be called to register the user context. At this point, it is assumed that the IContext from the Nop platform has already been initialized.

```javascript
@EnableWebSecurity
@EnableMethodSecurity
@Configuration
public class SpringSecurityConfig {
    @Inject
    SpringHttpServerFilterConfiguration config;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeRequests()
                .requestMatchers("/r/**").authenticated()
                .requestMatchers("/p/**").authenticated()
                .requestMatchers("/f/**").authenticated()
                .requestMatchers("/graphql").authenticated()
                .requestMatchers("/error").permitAll();

        http
                .csrf(Customizer.withDefaults())
                .addFilterAfter(authFilter(), CsrfFilter.class)
                // 将Nop Context的初始化放到authFilter之前
                .addFilterBefore(config.registerSysFilter().getFilter(), WebAuthFilter.class);
        return http.build();
    }

    // 其他配置
}
```

## Five. 使用`@Auth`注解

在业务代码中，我们可以使用`@Auth`注解来声明权限约束。

```java
@BizModel("Demo")
public class DemoBizModel {
    @BizQuery
    @Auth(permissions = "Demo:hello")
    public String hello(@Name("message") String message) {
        return "hello:" + message;
    }
}
```

在Spring的Controller中，可以使用`@PreAuthorize`注解

```java
@RestController
public class DemoController {

    @GetMapping("/hello")
    @PreAuthorize("hasPermission('Biz','Demo:hello')")
    public String hello(@RequestParam("message") String message) {
        return "Hi," + message;
    }
}
```