# 异常处理指南

## 概述

Nop平台提供了统一的异常处理机制，基于`NopException`和`ErrorCode`。所有平台定义的异常都继承自`NopException`，支持错误码、参数、国际化等特性。

**核心类**：
- `io.nop.api.core.exceptions.NopException`: 平台基础异常类
- `io.nop.api.core.exceptions.ErrorCode`: 错误码定义
- `io.nop.api.core.beans.ErrorBean`: 错误信息Bean

## NopException基础

### 创建异常

```java
import io.nop.api.core.exceptions.NopException;

// 方式1：使用错误码
throw new NopException(MyErrors.ERR_MY_ERROR);

// 方式2：使用错误码和原因
throw new NopException(MyErrors.ERR_MY_ERROR, cause);

// 方式3：使用错误码字符串（不推荐）
throw new NopException("err.my.error");
```

### 添加参数

```java
// 添加单个参数
throw new NopException(Errors.ERR_INVALID_VALUE)
    .param("value", actualValue);

// 添加多个参数
throw new NopException(Errors.ERR_USER_NOT_FOUND)
    .param("userId", userId)
    .param("userName", userName);

// 添加map参数
throw new NopException(Errors.ERR_INVALID_INPUT)
    .params(Map.of("field", "email", "value", email));
```

### 设置描述

```java
// 使用自定义描述
throw new NopException(ErrorCode.define("my.error.code", "自定义错误描述"))
    .param("param", value);

// 覆盖默认描述
throw new NopException(MyErrors.ERR_MY_ERROR)
    .description("自定义的详细错误信息");
```

## 错误码定义

### 定义错误码接口

```java
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.annotations.core.Locale;
import static io.nop.api.core.exceptions.ErrorCode.define;

@Locale("zh-CN")
public interface MyErrors {
    // 参数名常量
    String ARG_USER_ID = "userId";
    String ARG_USER_NAME = "userName";

    // 错误码定义
    ErrorCode ERR_USER_NOT_FOUND = define("my.err.user.not-found", "用户[{userId}]不存在", ARG_USER_ID);
    ErrorCode ERR_INVALID_PASSWORD = define("my.err.user.invalid-password", "密码错误");
    ErrorCode ERR_USER_ALREADY_EXISTS = define("my.err.user.already-exists",
        "用户[{userName}]已存在", ARG_USER_NAME);
    ErrorCode ERR_DUPLICATE_USERNAME = define("my.err.user.duplicate-username",
        "用户名[{userName}]已被使用", ARG_USER_NAME);
}
```

### 错误码规范

错误码命名规范：`模块.子模块.操作名-错误类型`

```java
// 正确的命名
"my.err.user.not-found"           // 用户模块，用户不存在
"my.err.user.invalid-password"    // 用户模块，密码无效
"my.err.order.invalid-status"     // 订单模块，状态无效
"my.err.payment.failed"           // 支付模块，支付失败

// 不推荐的命名
"user-not-found"                  // 缺少模块前缀
"error.123"                     // 使用数字，不易理解
"invalid-input"                   // 缺少操作描述
```

## 常见错误场景

### 1. 资源不存在

```java
@BizMutation
public User getUserById(String userId) {
    User user = dao().getEntityById(userId);
    if (user == null) {
        throw new NopException(MyErrors.ERR_USER_NOT_FOUND)
            .param(MyErrors.ARG_USER_ID, userId);
    }
    return user;
}
```

### 2. 参数验证失败

```java
@BizMutation
public void updateUser(String userId, String newName) {
    // 验证参数
    if (StringHelper.isEmpty(userId)) {
        throw new NopException(ApiErrors.ERR_CHECK_INVALID_ARGUMENT)
            .param("param", "userId");
    }

    if (StringHelper.isEmpty(newName)) {
        throw new NopException(ApiErrors.ERR_CHECK_INVALID_ARGUMENT)
            .param("param", "newName");
    }

    if (newName.length() > 50) {
        throw new NopException(MyErrors.ERR_INVALID_USER_NAME)
            .param(MyErrors.ARG_USER_NAME, newName)
            .param("maxLength", 50);
    }

    // 执行更新
    User user = dao().requireEntityById(userId);
    user.setName(newName);
    dao().saveEntity(user);
}
```

### 3. 业务规则违反

```java
@BizMutation
public void deleteUser(String userId) {
    User user = dao().requireEntityById(userId);

    // 检查业务规则
    if (user.hasActiveOrders()) {
        throw new NopException(MyErrors.ERR_USER_HAS_ACTIVE_ORDERS)
            .param(MyErrors.ARG_USER_ID, userId);
    }

    if (user.isSystemUser()) {
        throw new NopException(MyErrors.ERR_CANNOT_DELETE_SYSTEM_USER)
            .param(MyErrors.ARG_USER_ID, userId);
    }

    // 执行删除
    dao().deleteEntity(user);
}
```

### 4. 数据完整性冲突

```java
@BizMutation
public void registerUser(String userName, String email) {
    // 检查用户名是否已存在
    User nameCheck = new User();
    nameCheck.setUserName(userName);
    if (dao().findFirstByExample(nameCheck) != null) {
        throw new NopException(MyErrors.ERR_DUPLICATE_USERNAME)
            .param(MyErrors.ARG_USER_NAME, userName);
    }

    // 检查邮箱是否已存在
    User emailCheck = new User();
    emailCheck.setEmail(email);
    if (dao().findFirstByExample(emailCheck) != null) {
        throw new NopException(MyErrors.ERR_EMAIL_ALREADY_EXISTS)
            .param("email", email);
    }

    // 创建用户
    User user = new User();
    user.setUserName(userName);
    user.setEmail(email);
    dao().saveEntity(user);
}
```

### 5. 权限不足

```java
@BizMutation
public void updateSensitiveData(String userId, String sensitiveData) {
    // 检查权限
    User user = dao().requireEntityById(userId);

    if (!currentUser.canEditSensitiveData()) {
        throw new NopException(MyErrors.ERR_NO_PERMISSION_TO_EDIT_SENSITIVE_DATA)
            .param(MyErrors.ARG_USER_ID, userId);
    }

    user.setSensitiveData(sensitiveData);
    dao().saveEntity(user);
}
```

## 异常信息获取

### 获取错误信息

```java
try {
    // 调用可能抛异常的代码
    userService.deleteUser(userId);
} catch (NopException e) {
    // 获取错误码
    String errorCode = e.getErrorCode();

    // 获取错误描述
    String description = e.getDescription();

    // 获取参数
    Map<String, Object> params = e.getParams();

    // 获取状态码
    int status = e.getStatus();

    // 打印错误信息
    log.error("Error occurred: code={}, desc={}, params={}",
        errorCode, description, params);
}
```

### 格式化错误信息

```java
public String formatError(NopException e) {
    return String.format("Error[%s]: %s, Params: %s",
        e.getErrorCode(),
        e.getDescription(),
        e.getParams());
}
```

## 异常国际化

### 定义多语言错误信息

```java
@Locale("zh-CN")
public interface MyErrors {
    // 中文错误信息
    ErrorCode ERR_USER_NOT_FOUND = define("my.err.user.not-found", "用户[{userId}]不存在", ARG_USER_ID);
}

@Locale("en-US")
public interface MyErrors {
    // 英文错误信息
    ErrorCode ERR_USER_NOT_FOUND = define("my.err.user.not-found", "User [{userId}] not found", ARG_USER_ID);
}
```

### 使用国际化

```java
// 根据当前Locale获取错误信息
String description = NopException.getDefaultErrorDescription(
    MyErrors.ERR_USER_NOT_FOUND.getErrorCode(),
    MyErrors.ERR_USER_NOT_FOUND.getDescription()
);
```

## 异常包装

### 包装已有异常

```java
public void processExternalData(String data) {
    try {
        // 调用外部服务
        externalService.process(data);
    } catch (ExternalServiceException e) {
        // 包装为NopException
        throw new NopException(MyErrors.ERR_EXTERNAL_SERVICE_FAILED, e)
            .param("externalError", e.getMessage());
    }
}
```

### 包装多种异常

```java
public void processData(String data) {
    try {
        // 可能抛出多种异常的代码
        complexOperation(data);
    } catch (IOException e) {
        throw new NopException(MyErrors.ERR_IO_ERROR, e);
    } catch (TimeoutException e) {
        throw new NopException(ApiErrors.ERR_TIMEOUT, e);
    } catch (Exception e) {
        throw new NopException(MyErrors.ERR_PROCESSING_FAILED, e);
    }
}
```

## 异常链

### 保留原始异常

```java
public void operation1() {
    try {
        operation2();
    } catch (NopException e) {
        // 保留原始异常链
        throw new NopException(MyErrors.ERR_OPERATION_FAILED, e)
            .param("operation", "operation1");
    }
}

public void operation2() {
    try {
        // ...
    } catch (Exception e) {
        throw new NopException(MyErrors.ERR_SUB_OPERATION_FAILED, e);
    }
}
```

### 获取异常链

```java
public void printExceptionChain(Throwable e) {
    Throwable current = e;
    while (current != null) {
        if (current instanceof NopException) {
            NopException ne = (NopException) current;
            log.error("NopException: code={}, desc={}",
                ne.getErrorCode(), ne.getDescription());
        }
        current = current.getCause();
    }
}
```

## 特殊异常处理

### 不回滚事务

```java
@Transactional
public void operationWithNoRollback(String id) {
    try {
        // 执行操作
        dao().saveEntity(entity);
    } catch (NopException e) {
        // 标记不回滚
        e.setNotRollback(true);
        throw e;
    }
}
```

### 致命异常

```java
@Transactional
public void criticalOperation(String id) {
    try {
        // 执行操作
        dao().saveEntity(entity);
    } catch (NopException e) {
        // 标记为致命错误
        e.setBizFatal(true);
        throw e;
    }
}
```

### 中断异常

```java
public void operationWithBreak(String id) {
    try {
        // 执行循环
        for (int i = 0; i < 100; i++) {
            processItem(i);

            // 需要中断时
            if (shouldBreak()) {
                throw new NopBreakException();
            }
        }
    } catch (NopBreakException e) {
        // 正常的中断，不是错误
        log.info("Operation interrupted");
    }
}
```

## 异常在GraphQL中的表现

### GraphQL错误响应

当BizModel方法抛出`NopException`时，GraphQL会自动转换为错误响应：

```graphql
# 查询
query {
  userById(userId: "123") {
    id
    name
  }
}

# 错误响应
{
  "data": {
    "userById": null
  },
  "errors": [
    {
      "message": "用户[123]不存在",
      "extensions": {
        "code": "my.err.user.not-found",
        "status": 404,
        "params": {
          "userId": "123"
        }
      }
    }
  ]
}
```

### REST API错误响应

```json
{
  "code": "my.err.user.not-found",
  "message": "用户[123]不存在",
  "status": 404,
  "errors": {
    "userId": "123"
  }
}
```

## 异常最佳实践

### 1. 使用有意义的错误码

```java
// 推荐：描述性的错误码
ErrorCode ERR_USER_NOT_FOUND = define("my.err.user.not-found", "用户不存在");
ErrorCode ERR_PASSWORD_TOO_WEAK = define("my.err.user.password-too-weak", "密码太弱");

// 避免：模糊的错误码
// ErrorCode ERR_ERROR = define("my.err.error", "错误");
```

### 2. 包含足够的上下文信息

```java
// 推荐：提供详细的参数
throw new NopException(MyErrors.ERR_USER_NOT_FOUND)
    .param("userId", userId)
    .param("userName", userName)
    .param("queryType", "byId");

// 避免：信息不足的错误
// throw new NopException(MyErrors.ERR_USER_NOT_FOUND);
```

### 3. 使用参数占位符

```java
// 推荐：在错误信息中使用参数占位符
ErrorCode ERR_INVALID_VALUE = define("my.err.invalid-value",
    "字段[{fieldName}]的值[{value}]无效", "fieldName", "value");

// 错误信息会自动替换为："字段[age]的值[-5]无效"

// 避免：硬编码的错误信息
// ErrorCode ERR_INVALID_VALUE = define("my.err.invalid-value",
//     "字段" + fieldName + "的值" + value + "无效");
```

### 4. 合理使用异常层次

```java
// 推荐：定义特定的业务异常
public class MyBusinessException extends NopException {
    public MyBusinessException(ErrorCode errorCode) {
        super(errorCode);
    }
}

// 使用
throw new MyBusinessException(MyErrors.ERR_USER_NOT_FOUND);

// 避免：只使用通用异常
// throw new NopException(MyErrors.ERR_USER_NOT_FOUND);
```

### 5. 区分业务异常和技术异常

```java
// 业务异常：正常的业务流程，应该向用户展示
throw new NopException(MyErrors.ERR_USER_NOT_FOUND);

// 技术异常：不应该向用户展示的内部错误
throw new NopException(MyErrors.ERR_DATABASE_CONNECTION_FAILED);
```

## 异常处理模式

### 1. Try-Catch模式

```java
public void process(String id) {
    try {
        // 执行操作
        User user = dao().requireEntityById(id);
        user.setStatus(1);
        dao().saveEntity(user);

    } catch (NopException e) {
        // 处理业务异常
        log.warn("Business error: {}", e.getMessage());
        throw e;

    } catch (Exception e) {
        // 处理其他异常
        log.error("Unexpected error", e);
        throw new NopException(MyErrors.ERR_PROCESSING_FAILED, e);
    }
}
```

### 2. Result模式

```java
public class Result<T> {
    private boolean success;
    private T data;
    private NopException error;

    // getters and setters
}

public Result<User> getUserById(String userId) {
    Result<User> result = new Result<>();
    try {
        User user = dao().getEntityById(userId);
        if (user == null) {
            result.setError(new NopException(MyErrors.ERR_USER_NOT_FOUND)
                .param("userId", userId));
        } else {
            result.setData(user);
            result.setSuccess(true);
        }
    } catch (Exception e) {
        result.setError(new NopException(MyErrors.ERR_PROCESSING_FAILED, e));
        result.setSuccess(false);
    }
    return result;
}
```

### 3. Optional模式

```java
import java.util.Optional;

public Optional<User> findUserById(String userId) {
    try {
        User user = dao().getEntityById(userId);
        return Optional.ofNullable(user);
    } catch (Exception e) {
        log.error("Error finding user", e);
        return Optional.empty();
    }
}

// 使用
Optional<User> userOpt = findUserById(userId);
if (userOpt.isPresent()) {
    User user = userOpt.get();
    // 处理用户
} else {
    // 用户不存在
}
```

## 异常监听和日志

### 异常监听器

```java
public class ExceptionListener {
    public void onException(NopException e) {
        // 记录到数据库
        ErrorLog log = new ErrorLog();
        log.setErrorCode(e.getErrorCode());
        log.setDescription(e.getDescription());
        log.setParams(JsonTool.instance().beanToJson(e.getParams()));
        errorLogDao.saveEntity(log);

        // 发送告警
        if (e.isBizFatal()) {
            alertService.sendCriticalAlert(e);
        }
    }
}
```

### 异常日志

```java
@Slf4j
public class UserService {
    public void deleteUser(String userId) {
        try {
            User user = dao().requireEntityById(userId);
            dao().deleteEntity(user);

            log.info("User deleted successfully: userId={}", userId);

        } catch (NopException e) {
            log.error("Failed to delete user: userId={}, error={}",
                userId, e.getMessage(), e);
            throw e;
        }
    }
}
```

## 异常恢复策略

### 1. 重试机制

```java
public void operationWithRetry(String id) {
    int maxRetries = 3;
    int retryCount = 0;

    while (retryCount < maxRetries) {
        try {
            process(id);
            return; // 成功，退出

        } catch (NopException e) {
            if (isRetryable(e)) {
                retryCount++;
                log.warn("Retry attempt {}/{} for operation {}",
                    retryCount, maxRetries, id);
                Thread.sleep(1000 * retryCount); // 指数退避
            } else {
                throw e; // 不可重试，直接抛出
            }
        }
    }
}

private boolean isRetryable(NopException e) {
    // 判断异常是否可以重试
    return e.getErrorCode().equals("my.err.timeout") ||
           e.getErrorCode().equals("my.err.service-unavailable");
}
```

### 2. 降级策略

```java
public User getUserWithFallback(String userId) {
    try {
        // 尝试从缓存获取
        User user = cacheService.getUser(userId);
        if (user != null) {
            return user;
        }

        // 尝试从数据库获取
        user = dao().getEntityById(userId);
        if (user != null) {
            cacheService.putUser(userId, user);
            return user;
        }

        throw new NopException(MyErrors.ERR_USER_NOT_FOUND)
            .param("userId", userId);

    } catch (NopException e) {
        if (isCacheError(e)) {
            // 缓存失败，降级到数据库
            log.warn("Cache failed, fallback to database: {}", e.getMessage());
            return dao().requireEntityById(userId);
        }
        throw e;
    }
}
```

### 3. 断路器模式

```java
public class CircuitBreaker {
    private int failureCount = 0;
    private int threshold = 5;
    private long lastFailureTime = 0;
    private long timeout = 60000; // 60秒

    public boolean isCircuitOpen() {
        if (failureCount >= threshold) {
            return System.currentTimeMillis() - lastFailureTime < timeout;
        }
        return false;
    }

    public void recordFailure() {
        failureCount++;
        lastFailureTime = System.currentTimeMillis();
    }

    public void recordSuccess() {
        failureCount = 0;
    }
}

public void operationWithCircuitBreaker(String id) {
    if (circuitBreaker.isCircuitOpen()) {
        throw new NopException(MyErrors.ERR_CIRCUIT_OPEN);
    }

    try {
        process(id);
        circuitBreaker.recordSuccess();

    } catch (NopException e) {
        circuitBreaker.recordFailure();
        throw e;
    }
}
```

## 常见问题

### Q1: NopException和普通Exception有什么区别？

**A**:
- `NopException`: 平台统一的异常类，支持错误码、国际化、参数等
- `Exception`: Java普通异常，建议只用于捕获，不要直接抛出

### Q2: 如何选择错误码？

**A**:
```java
// 推荐：使用定义好的错误码
throw new NopException(MyErrors.ERR_USER_NOT_FOUND);

// 避免：硬编码错误码
// throw new NopException("my.err.user.not-found");
```

### Q3: 异常会影响事务吗？

**A**: 会。当抛出`NopException`时，如果方法在`@Transactional`中，事务会自动回滚。如果不想回滚，可以设置：

```java
e.setNotRollback(true);
```

### Q4: 如何自定义异常信息？

**A**:
```java
// 方式1：定义新的ErrorCode
ErrorCode ERR_CUSTOM_ERROR = define("my.err.custom", "自定义错误");

// 方式2：使用description覆盖
throw new NopException(MyErrors.ERR_MY_ERROR)
    .description("自定义的详细错误信息");
```

### Q5: 异常信息如何国际化？

**A**: 使用`@Locale`注解定义不同语言的错误信息：

```java
@Locale("zh-CN")
public interface MyErrors {
    ErrorCode ERR_ERROR = define("my.err.error", "中文错误信息");
}

@Locale("en-US")
public interface MyErrors {
    ErrorCode ERR_ERROR = define("my.err.error", "English error message");
}
```

## 相关文档

- [事务管理指南](./transaction.md) - 事务管理完整指南
- [IoC容器指南](./ioc-container.md) - 依赖注入容器使用
- [服务层开发指南](../03-development-guide/service-layer.md) - BizModel开发详解
- [GraphQL服务开发指南](../03-development-guide/api-development.md) - GraphQL API开发
- [错误码定义](../common/error-code.md) - 错误码规范

## 总结

Nop平台的异常处理提供了：

1. **统一的异常体系**：所有异常继承自`NopException`
2. **结构化的错误信息**：包含错误码、参数、描述、状态等
3. **国际化支持**：支持多语言错误信息
4. **灵活的参数传递**：支持单个参数、多个参数、map参数
5. **异常链支持**：保留原始异常信息

在实际开发中：
- 定义清晰的错误码体系
- 使用有意义的错误信息
- 提供足够的上下文参数
- 区分业务异常和技术异常
- 合理使用异常处理模式
