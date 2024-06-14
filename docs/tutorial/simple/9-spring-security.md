# Nop入门：集成使用SpringSecurity

与Spring集成的时候有些人希望沿用以前的登录认证机制，不想引入Nop平台的用户、角色表等，此时可以不引入`nop-auth-service`服务，然后适配少数几个接口即可。

示例工程可以参见 nop-spring-security-demo。

## 一. 引入nop-spring-web-starter依赖

这个包会使用SpringBoot的配置自发现机制启动Nop平台，并注册SpringGraphWebService。

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

然后在application.yaml配置文件中启用如下配置：

```yaml
nop:
  auth:
    enable-data-auth: true
    enable-action-auth: true
    http-server-filter:
      enabled: false
```

这里启用了数据权限和操作权限校验，并禁用了内置的`AuthHttpServerFilter`，原先是由这个Filter负责检查登录状态。

## 二. 实现IActionAuthChecker接口

Nop平台内部使用IActionAuthChecker接口来检查操作权限，而SpringSecurity则是使用PermissionEvaluator接口

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

    // 这里如何构造token需要根据具体授权框架的要求来
    UserIdToken token = new UserIdToken(userContext.getUserId());
    return permissionEvaluator.hasPermission(token, "BizObject", permission);
  }
}
```

一般情况下Nop平台中的permission格式为 `{bizObjName}:{method}`，代码生成时会生成`NopAuthUser:query`
和`NopAuthUser:mutation`这种权限标识，它们分别对应读操作（GraphQL的query）和写操作（GraphQL的mutation）。
自己根据业务情况可以进一步细化，比如将upload和download独立出来等。

当然，具体permission的格式并没有硬性要求，都是自己内部约定的，只要底层的PermissionEvaluator能够识别即可。

## 三. SpringSecurity登录成功后初始化Nop平台的用户上下文

```javascript
 void onLoginSuccess(IContext context, HttpServletRequest request) {
      String userId = "nop";
      String userName = "123";

      // 这里应该按照具体框架要求设置token，这里仅仅是
      SecurityContext secureContext = SecurityContextHolder.getContext();
      secureContext.setAuthentication(new UserIdToken(userId));
      request.setAttribute(RequestAttributeSecurityContextRepository.DEFAULT_REQUEST_ATTR_NAME, secureContext);

      // context上保存了一些最基本的信息，IUserContext是更多的用户相关信息
      context.setUserId(userId);
      context.setUserName(userName);

      // 这里模拟登录成功后设置user上下文
      UserContextImpl userContext = new UserContextImpl();
      userContext.setAccessToken("aaa");
      userContext.setLastAccessTime(CoreMetrics.currentTimeMillis());
      userContext.setUserId(userId);
      userContext.setUserName(userName);
      userContext.setRoles(Set.of("manager", "checker"));

      IUserContext.set(userContext);
  }
```

* SecurityContextHolder是SpringSecurity登录验证成功后保存安全上下文的地方。这里使用的UserIdToken仅仅是一个示例，具体保存什么内容要看具体登录框架的要求。
* 为了支持异步调用，这里还需要把安全上下文保存到HttpServletRequest上，否则Controller返回异步结果时会出现上下文丢失的情况。

## 四. 调整SpringSecurity的配置

Nop平台的ContextHttpServerFilter需要在SpringSecurity的认证Filter之前执行，在认证Filter中会需要调用上面的onLoginSuccess函数注册用户上下文，此时要求Nop平台的IContext已经初始化完毕。

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

## 五. 使用`@Auth`注解

在业务代码中我们可以使用`@Auth`注解来声明权限约束。

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

在Spring的Controller中可以使用`@PreAuthorize`注解

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
