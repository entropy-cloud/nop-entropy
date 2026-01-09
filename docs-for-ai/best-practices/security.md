# Nop Platform Security Best Practices

## 概述

本文档提供Nop Platform安全开发的最佳实践，帮助开发者构建安全可靠的应用系统。安全是一个多层面的系统工程，涉及认证、授权、数据保护、输入验证等多个方面。

## 认证和授权

### 1. 认证机制

使用Nop Platform内置的权限认证系统：

```java
@BizModel("Auth")
public class AuthBizModel {

    @Inject
    private IAuthService authService;

    @BizMutation
    public LoginResult login(@Name("username") String username,
                          @Name("password") String password) {
        // 验证用户名和密码
        return authService.login(username, password);
    }
}
```

### 2. 权限验证

使用注解进行权限控制：

```java
@BizModel("Order")
public class OrderBizModel extends CrudBizModel<NopOrder> {

    @BizMutation
    @Permission("order:delete")
    public void deleteOrder(@Name("orderId") String orderId) {
        NopOrder order = dao().requireEntityById(orderId);
        dao().deleteEntity(order);
    }
}
```

### 3. 数据权限

基于用户权限过滤数据：

```java
@BizModel("User")
public class UserBizModel extends CrudBizModel<NopAuthUser> {

    @BizQuery
    public List<User> findUsers(QueryBean query) {
        // 自动应用数据权限过滤
        return dao().findAllByQuery(query);
    }
}
```

## 输入验证

### 1. 参数验证

验证所有输入参数：

```java
@BizMutation
public User createUser(@Name("user") User user) {
    // 1. 非空验证
    if (StringHelper.isEmpty(user.getUserName())) {
        throw new NopException(ERR_NAME_REQUIRED);
    }

    // 2. 格式验证
    if (!isValidEmail(user.getEmail())) {
        throw new NopException(ERR_INVALID_EMAIL)
            .param("email", user.getEmail());
    }

    // 3. 长度验证
    if (user.getUserName().length() > 50) {
        throw new NopException(ERR_NAME_TOO_LONG);
    }

    // 4. 业务规则验证
    if (userExists(user.getEmail())) {
        throw new NopException(ERR_EMAIL_EXISTS);
    }

    return dao().saveEntity(user);
}
```

### 2. SQL注入防护

使用参数化查询：

```java
// ✅ 推荐：参数化查询
@BizQuery
public List<User> findUsers(@Name("keyword") String keyword) {
    QueryBean query = new QueryBean();
    query.setFilter(FilterBeans.contains("userName", keyword));
    return dao().findAllByQuery(query);
}

// ❌ 不推荐：字符串拼接
@BizQuery
public List<User> findUsers(@Name("keyword") String keyword) {
    String sql = "SELECT * FROM user WHERE user_name LIKE '%" + keyword + "%'";
    return ormTemplate.queryList(sql);
}
```

### 3. XSS防护

输出时进行HTML转义：

```java
@Component
public class HtmlEscapeUtil {
    public static String escape(String input) {
        if (StringHelper.isEmpty(input)) {
            return input;
        }
        return StringHelper.replace(input,
            "<", "&lt;",
            ">", "&gt;",
            "&", "&amp;",
            "\"", "&quot;"
        );
    }
}
```

## 数据保护

### 1. 敏感数据加密

使用加密算法保护敏感数据：

```java
@Component
public class CryptoService {

    private final AESEncryptor encryptor;

    public String encrypt(String plainText) {
        return encryptor.encrypt(plainText);
    }

    public String decrypt(String cipherText) {
        return encryptor.decrypt(cipherText);
    }
}

@BizMutation
public User createUser(@Name("user") User user) {
    // 加密密码
    user.setPassword(cryptoService.encrypt(user.getPassword()));
    return dao().saveEntity(user);
}
```

### 2. 数据脱敏

日志中隐藏敏感信息：

```java
@BizModel("User")
public class UserBizModel {

    private static final Logger LOG = LoggerFactory.getLogger(UserBizModel.class);

    @BizMutation
    public User createUser(User user) {
        User saved = dao().saveEntity(user);

        // 脱敏记录
        LOG.info("User created: userId={}, email={}",
            saved.getUserId(),
            maskEmail(saved.getEmail()));

        return saved;
    }

    private String maskEmail(String email) {
        if (StringHelper.isEmpty(email)) {
            return email;
        }
        int atIndex = email.indexOf("@");
        String prefix = email.substring(0, Math.min(3, atIndex));
        return prefix + "***" + email.substring(atIndex);
    }
}
```

### 3. 最小权限原则

只授予必要的权限：

```java
// ✅ 推荐：授予具体权限
@Permission("order:create")
public void createOrder(Order order) {
    // ...
}

@Permission("order:delete")
public void deleteOrder(String orderId) {
    // ...
}

// ❌ 不推荐：授予宽泛权限
@Permission("order:*")
public void processOrder(Order order) {
    // ...
}
```

## 会话管理

### 1. 会话超时

配置合理的会话超时时间：

```yaml
# application.yaml
session:
  timeout: 1800  # 30分钟
```

### 2. 会话固定

使用会话ID固定化：

```java
@Component
public class SessionManager {

    public void login(String userId, HttpServletRequest request) {
        // 生成会话ID
        String sessionId = generateSessionId();

        // 保存会话信息
        session.setAttribute(userId, sessionId);

        // 设置Cookie
        Cookie cookie = new Cookie("JSESSIONID", sessionId);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        response.addCookie(cookie);
    }
}
```

### 3. 会话失效

提供安全的退出机制：

```java
@BizMutation
public void logout() {
    // 1. 失效会话
    session.invalidate();

    // 2. 清除缓存
    cacheManager.remove("userCache", session.getUserId());

    // 3. 记录日志
    logLogout(session.getUserId());
}
```

## 密码安全

### 1. 密码复杂度

验证密码复杂度：

```java
@Component
public class PasswordValidator {

    private static final int MIN_LENGTH = 8;
    private static final Pattern UPPER = Pattern.compile("[A-Z]");
    private static final Pattern LOWER = Pattern.compile("[a-z]");
    private static final Pattern DIGIT = Pattern.compile("[0-9]");
    private static final Pattern SPECIAL = Pattern.compile("[!@#$%^&*]");

    public void validate(String password) {
        if (password.length() < MIN_LENGTH) {
            throw new NopException(ERR_PASSWORD_TOO_SHORT);
        }

        if (!UPPER.matcher(password).find()) {
            throw new NopException(ERR_PASSWORD_NO_UPPER);
        }

        if (!LOWER.matcher(password).find()) {
            throw new NopException(ERR_PASSWORD_NO_LOWER);
        }

        if (!DIGIT.matcher(password).find()) {
            throw new NopException(ERR_PASSWORD_NO_DIGIT);
        }
    }
}

@BizMutation
public void changePassword(@Name("userId") String userId,
                       @Name("oldPassword") String oldPassword,
                       @Name("newPassword") String newPassword) {
    // 验证旧密码
    if (!verifyOldPassword(userId, oldPassword)) {
        throw new NopException(ERR_OLD_PASSWORD_WRONG);
    }

    // 验证新密码复杂度
    passwordValidator.validate(newPassword);

    // 加密并保存
    saveNewPassword(userId, cryptoService.encrypt(newPassword));
}
```

### 2. 密码存储

使用BCrypt等安全哈希算法：

```java
@Component
public class PasswordEncoder {

    private final BCryptPasswordEncoder encoder;

    public String encode(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String encodedPassword) {
        return encoder.matches(rawPassword, encodedPassword);
    }
}
```

### 3. 密码重置

安全的密码重置流程：

```java
@BizMutation
public void requestPasswordReset(@Name("email") String email) {
    // 1. 验证邮箱存在
    if (!emailExists(email)) {
        throw new NopException(ERR_EMAIL_NOT_FOUND);
    }

    // 2. 生成重置令牌
    String token = generateResetToken(email);

    // 3. 发送邮件
    sendResetEmail(email, token);

    // 4. 记录日志
    logPasswordResetRequest(email);
}
```

## API安全

### 1. HTTPS配置

强制使用HTTPS：

```yaml
# application.yaml
server:
  ssl:
    enabled: true
    key-store: classpath:keystore.p12
    key-store-password: password
    key-store-type: PKCS12
```

### 2. CORS配置

正确配置CORS：

```java
@Component
public class CorsFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                     FilterChain chain) throws IOException, ServletException {
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        httpResponse.setHeader("Access-Control-Allow-Origin", "https://trusted-domain.com");
        httpResponse.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE");
        httpResponse.setHeader("Access-Control-Allow-Credentials", "true");

        chain.doFilter(request, response);
    }
}
```

### 3. 速率限制

防止API滥用：

```java
@Component
public class RateLimiter {

    private final LoadingCache<String, Integer> requestCounts;

    public boolean checkRateLimit(String userId, int maxRequests) {
        Integer count = requestCounts.get(userId);
        if (count == null) {
            count = 0;
        }

        if (count >= maxRequests) {
            return false;  // 超过限制
        }

        requestCounts.put(userId, count + 1, 1, TimeUnit.MINUTES);
        return true;
    }
}
```

## 日志安全

### 1. 敏感信息处理

日志中不记录敏感信息：

```java
// ✅ 推荐：脱敏日志
LOG.info("User login: userId={}, ip={}", userId, ipAddress);

// ❌ 不推荐：记录密码
LOG.info("User login: userId={}, password={}", userId, password);
```

### 2. 日志级别控制

合理使用日志级别：

```java
// DEBUG: 详细的调试信息
LOG.debug("Processing request: {}", request);

// INFO: 一般业务信息
LOG.info("User created: userId={}", userId);

// WARN: 警告信息
LOG.warn("Slow query: {}ms", costTime);

// ERROR: 错误信息
LOG.error("Failed to process: {}", error, exception);
```

### 3. 日志访问控制

保护日志文件访问：

```bash
# 设置日志文件权限
chmod 600 /var/log/nop/application.log

# 确保日志目录安全
chown root:root /var/log/nop/
chmod 700 /var/log/nop/
```

## 配置安全

### 1. 配置文件加密

加密敏感配置：

```yaml
# application-encrypted.yaml
datasource:
  url: ENC(encrypted-database-url)
  username: ENC(encrypted-username)
  password: ENC(encrypted-password)
```

### 2. 环境变量

使用环境变量存储敏感信息：

```bash
# 设置环境变量
export DB_PASSWORD=secure_password
export API_KEY=secure_api_key

# 启动应用
java -jar app.jar
```

### 3. 配置文件权限

限制配置文件访问：

```bash
# 设置配置文件权限
chmod 600 /opt/nop/application.yaml

# 确保配置目录安全
chmod 700 /opt/nop/config/
```

## 依赖安全

### 1. 依赖扫描

定期扫描依赖漏洞：

```bash
# 使用OWASP Dependency Check
mvn org.owasp:dependency-check:check

# 使用Snyk
snyk test
```

### 2. 依赖更新

及时更新依赖版本：

```xml
<dependencies>
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <version>42.6.0</version>  <!-- 使用最新稳定版 -->
    </dependency>
</dependencies>
```

### 3. 依赖审查

审查新引入的依赖：

1. 检查依赖维护状态
2. 检查已知安全漏洞
3. 评估依赖必要性
4. 考虑替代方案

## 安全测试

### 1. 单元测试

编写安全相关的单元测试：

```java
@Test
public void testSqlInjectionProtection() {
    // 尝试SQL注入
    String maliciousInput = "admin' OR '1'='1";

    List<User> users = userService.findUsers(maliciousInput);

    // 验证不会发生SQL注入
    assertTrue(users.isEmpty());
}
```

### 2. 渗透测试

进行安全渗透测试：

1. **SQL注入测试**
2. **XSS攻击测试**
3. **CSRF攻击测试**
4. **权限绕过测试**
5. **会话劫持测试**

### 3. 安全审计

定期进行安全审计：

```java
@Component
public class SecurityAuditor {

    public void auditLogin(String userId, String ipAddress, boolean success) {
        SecurityLog log = new SecurityLog();
        log.setUserId(userId);
        log.setIpAddress(ipAddress);
        log.setEventType(success ? "LOGIN_SUCCESS" : "LOGIN_FAILURE");
        log.setCreateTime(new Date());

        securityLogDao.saveEntity(log);
    }
}
```

## 合规性

### 1. GDPR合规

满足数据保护要求：

```java
@BizMutation
public void deleteUser(@Name("userId") String userId) {
    // 1. 软删除
    User user = dao().getEntityById(userId);
    user.setDeleted(true);
    user.setDeletedTime(new Date());
    dao().saveEntity(user);

    // 2. 记录删除日志
    logDeletion(userId, "GDPR deletion request");

    // 3. 通知相关系统
    notifyDeletion(userId);
}
```

### 2. 数据保留策略

定义数据保留期限：

```java
@Scheduled(cron = "0 0 2 * * ?")  // 每天凌晨2点
public void cleanupOldData() {
    Date cutoffDate = DateHelper.addDays(new Date(), -90);  // 90天前

    // 删除90天前的数据
    QueryBean query = new QueryBean();
    query.setFilter(FilterBeans.lt("createTime", cutoffDate));

    int deleted = dao().deleteByQuery(query);
    log.info("Deleted {} old records", deleted);
}
```

### 3. 审计日志

保留完整的审计日志：

```java
@Aspect
@Component
public class AuditAspect {

    @Around("@annotation(Auditable)")
    public Object audit(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = joinPoint.getSignature();
        Object[] args = joinPoint.getArgs();

        // 记录操作前
        AuditLog before = createAuditLog(method, args);

        try {
            Object result = joinPoint.proceed();

            // 记录操作成功
            before.setStatus("SUCCESS");
            auditLogDao.saveEntity(before);

            return result;
        } catch (Exception e) {
            // 记录操作失败
            before.setStatus("FAILURE");
            before.setError(e.getMessage());
            auditLogDao.saveEntity(before);
            throw e;
        }
    }
}
```

## 常见安全漏洞

| 漏洞类型 | 风险 | 预防措施 |
|---------|------|----------|
| SQL注入 | 数据泄露 | 使用参数化查询 |
| XSS攻击 | 脚本注入 | 输入验证、输出转义 |
| CSRF攻击 | 跨站请求伪造 | CSRF Token |
| 会话劫持 | 身份冒用 | 安全Cookie、会话固定 |
| 路径遍历 | 文件系统访问 | 输入验证、白名单 |
| 不安全的反序列化 | 远程代码执行 | 输入验证、限制反序列化 |
| 敏感信息泄露 | 信息暴露 | 日志脱敏、错误处理 |

## 安全检查清单

### 开发阶段

- [ ] 所有输入参数都经过验证
- [ ] 使用参数化查询防止SQL注入
- [ ] 输出时进行XSS防护
- [ ] 敏感数据加密存储
- [ ] 实现最小权限原则
- [ ] 正确配置会话管理
- [ ] 实现密码复杂度要求

### 部署阶段

- [ ] 启用HTTPS
- [ ] 正确配置CORS
- [ ] 配置速率限制
- [ ] 限制配置文件访问权限
- [ ] 加密敏感配置
- [ ] 配置防火墙规则
- [ ] 启用安全日志

### 运维阶段

- [ ] 定期更新依赖
- [ ] 进行安全扫描
- [ ] 执行安全审计
- [ ] 监控异常访问
- [ ] 定期备份
- [ ] 制定应急响应计划

## 相关文档

- [异常处理指南](../getting-started/core/exception-guide.md)
- [服务层开发指南](../getting-started/service/service-layer-development.md)
- [事务管理指南](../getting-started/core/transaction-guide.md)
- [代码风格规范](./code-style.md)

## 总结

Nop Platform安全是一个持续的过程，需要从多个层面考虑：

1. **认证和授权**: 使用Nop Platform内置权限系统
2. **输入验证**: 验证所有输入，防止注入攻击
3. **数据保护**: 加密敏感数据，脱敏日志
4. **会话管理**: 合理的会话超时和安全退出
5. **API安全**: HTTPS、CORS、速率限制
6. **日志安全**: 不记录敏感信息，保护日志文件
7. **依赖安全**: 定期扫描和更新依赖
8. **合规性**: 满足GDPR等法规要求
9. **安全测试**: 渗透测试、安全审计

遵循这些安全最佳实践，可以构建安全可靠的Nop Platform应用系统。

---

**文档版本**: 1.0
**最后更新**: 2025-01-09
**作者**: AI Assistant (Sisyphus)
