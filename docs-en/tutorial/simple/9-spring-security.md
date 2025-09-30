# Getting Started with Nop: Integrating with Spring Security

When integrating with Spring, some people prefer to keep their existing login and authentication mechanisms and do not want to introduce the Nop platform’s user and role tables. In this case, you can omit the `nop-auth-service` and adapt just a few interfaces.

Refer to the sample project nop-spring-security-demo.

## 1. Add the nop-spring-web-starter dependency

This package uses Spring Boot’s auto-configuration discovery mechanism to start the Nop platform and register SpringGraphWebService.

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

Then enable the following settings in the application.yaml config file:

```yaml
nop:
  auth:
    enable-data-auth: true
    enable-action-auth: true
    http-server-filter:
      enabled: false
```

Here we enable data permission and action permission checks, and disable the built-in `AuthHttpServerFilter`, which originally was responsible for checking the login status.

## 2. Implement the IActionAuthChecker interface

Within the Nop platform, the IActionAuthChecker interface is used to check action permissions, while Spring Security uses the PermissionEvaluator interface.

```java
public class SpringActionAuthChecker implements IActionAuthChecker {
  static final Logger LOG = LoggerFactory.getLogger(SpringActionAuthChecker.class);

  @Inject
  PermissionEvaluator permissionEvaluator;

  @Override
  public boolean isPermitted(String permission, ISecurityContext context) {
    LOG.info("nop.check-action-auth:permission={},user={}", permission, context.getUserContext());

    if (context.getUserContext() == null)
      return false;
    IUserContext userContext = context.getUserContext();

    // How to construct the token here depends on your specific authorization framework
    UserIdToken token = new UserIdToken(userContext.getUserId());
    return permissionEvaluator.hasPermission(token, "BizObject", permission);
  }
}
```

In general, permission strings in the Nop platform follow the format `{bizObjName}:{method}`. During code generation, permission identifiers like `NopAuthUser:query` and `NopAuthUser:mutation` are generated, corresponding to read operations (GraphQL query) and write operations (GraphQL mutation), respectively. You can further refine them based on your business needs, for example, separating upload and download.

Of course, there is no hard requirement for the exact permission format—it’s an internal convention—as long as the underlying PermissionEvaluator can recognize it.

## 3. Initialize the Nop platform user context after a successful Spring Security login

```javascript
 void onLoginSuccess(IContext context, HttpServletRequest request) {
      String userId = "nop";
      String userName = "123";

      // The token should be set according to the specific framework's requirements; this is just an example
      SecurityContext secureContext = SecurityContextHolder.getContext();
      secureContext.setAuthentication(new UserIdToken(userId));
      request.setAttribute(RequestAttributeSecurityContextRepository.DEFAULT_REQUEST_ATTR_NAME, secureContext);

      // The context holds some basic information, while IUserContext contains more user-related information
      context.setUserId(userId);
      context.setUserName(userName);

      // Simulate setting the user context after a successful login
      UserContextImpl userContext = new UserContextImpl();
      userContext.setAccessToken("aaa");
      userContext.setLastAccessTime(CoreMetrics.currentTimeMillis());
      userContext.setUserId(userId);
      userContext.setUserName(userName);
      userContext.setRoles(Set.of("manager", "checker"));

      IUserContext.set(userContext);
  }
```

- SecurityContextHolder is where Spring Security stores the security context after successful authentication. The UserIdToken used here is only an example; what to store specifically depends on your login framework.
- To support asynchronous calls, you also need to save the security context on the HttpServletRequest; otherwise, the context may be lost when a Controller returns an asynchronous result.

## 4. Adjust Spring Security configuration

The Nop platform’s ContextHttpServerFilter needs to run before Spring Security’s authentication filter. In the authentication filter, you will need to call the onLoginSuccess function above to register the user context, which requires that the Nop platform’s IContext has already been initialized.

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
                // Initialize the Nop Context before authFilter
                .addFilterBefore(config.registerSysFilter().getFilter(), WebAuthFilter.class);
        return http.build();
    }

    // Other configurations
}
```

## 5. Use the `@Auth` annotation

In business code, you can use the `@Auth` annotation to declare permission constraints.

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

In Spring Controllers, you can use the `@PreAuthorize` annotation

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
<!-- SOURCE_MD5:95c7baa6d46ab03e215ca9919bdff563-->
