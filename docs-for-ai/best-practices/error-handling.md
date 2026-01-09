# Nop Platform Error Handling Best Practices

## 概述

本文档提供Nop Platform错误处理的最佳实践，帮助开发者构建健壮、可靠的错误处理体系。错误处理是应用系统的关键部分，影响用户体验、系统稳定性和问题排查效率。

## 错误类型

### 1. 业务异常

业务规则违反时抛出：

```java
// 用户已存在
throw new NopException(ERR_EMAIL_EXISTS)
    .param("email", email);

// 用户权限不足
throw new NopException(ERR_PERMISSION_DENIED)
    .param("resource", "order:delete");

// 库存不足
throw new NopException(ERR_INSUFFICIENT_INVENTORY)
    .param("productId", productId)
    .param("available", available)
    .param("requested", requested);
```

### 2. 验证异常

输入参数验证失败时抛出：

```java
// 必填字段缺失
throw new NopException(ERR_NAME_REQUIRED)
    .param("field", "userName");

// 格式验证失败
throw new NopException(ERR_INVALID_EMAIL)
    .param("email", email);

// 长度超出限制
throw new NopException(ERR_NAME_TOO_LONG)
    .param("field", "userName")
    .param("length", userName.length())
    .param("maxLength", 50);
```

### 3. 系统异常

系统级别错误：

```java
// 数据库连接失败
throw new NopException(ERR_DB_CONNECTION_FAILED)
    .cause(originalException);

// 外部服务调用失败
throw new NopException(ERR_EXTERNAL_SERVICE_ERROR)
    .param("service", "payment")
    .cause(originalException);
```

### 4. 未找到异常

资源不存在时抛出：

```java
// 用户不存在
throw new NopException(ERR_USER_NOT_FOUND)
    .param("userId", userId);

// 订单不存在
throw new NopException(ERR_ORDER_NOT_FOUND)
    .param("orderId", orderId);
```

## 错误码规范

### 1. 错误码定义

定义清晰的错误码：

```java
/**
 * 用户相关错误码
 */
public interface UserErrors {
    String ERR_USER_NOT_FOUND = "nop.err.user:not-found";
    String ERR_USER_ALREADY_EXISTS = "nop.err.user:already-exists";
    String ERR_INVALID_EMAIL = "nop.err.user:invalid-email";
    String ERR_PASSWORD_TOO_WEAK = "nop.err.user:password-too-weak";
}

/**
 * 业务错误码
 */
public interface BusinessErrors {
    String ERR_ORDER_NOT_FOUND = "nop.err.order:not-found";
    String ERR_ORDER_STATUS_INVALID = "nop.err.order:status-invalid";
    String ERR_INSUFFICIENT_INVENTORY = "nop.err.order:insufficient-inventory";
}
```

### 2. 错误码分组

按模块分组错误码：

```
nop.err.user:*        - 用户相关
nop.err.auth:*        - 认证相关
nop.err.order:*       - 订单相关
nop.err.payment:*     - 支付相关
nop.err.inventory:*    - 库存相关
nop.err.system:*      - 系统相关
```

### 3. 错误码层次结构

```
nop.err                                    # 错误前缀
  └── user:                          # 模块
        ├── not-found                    # 具体错误
        ├── already-exists
        └── invalid-email
  └── order:
        ├── not-found
        ├── status-invalid
        └── insufficient-inventory
```

## 异常创建

### 1. 基础异常

创建带错误码的异常：

```java
// ✅ 推荐：使用错误码
throw new NopException(ERR_USER_NOT_FOUND)
    .param("userId", userId);

// ❌ 不推荐：使用文本消息
throw new NopException("User not found: " + userId);
```

### 2. 带参数的异常

传递上下文参数：

```java
throw new NopException(ERR_INVALID_INPUT)
    .param("field", "email")
    .param("value", email)
    .param("reason", "invalid format");
```

### 3. 带原因的异常

包装原始异常：

```java
try {
    externalService.call();
} catch (IOException e) {
    throw new NopException(ERR_EXTERNAL_SERVICE_ERROR)
        .param("service", "payment")
        .cause(e);
}
```

### 4. 异常链

保持异常链：

```java
try {
    // 业务操作
} catch (NopException e) {
    // 添加上下文信息
    throw new NopException(ERR_OPERATION_FAILED)
        .description("Failed to process order")
        .cause(e);
}
```

## 异常处理策略

### 1. Try-Catch-Finally模式

标准的异常处理模式：

```java
@BizMutation
public User createUser(User user) {
    try {
        // 1. 验证输入
        validateUser(user);

        // 2. 业务逻辑
        return dao().saveEntity(user);
    } catch (NopException e) {
        // 业务异常：直接抛出
        throw e;
    } catch (Exception e) {
        // 系统异常：包装后抛出
        log.error("Failed to create user", e);
        throw new NopException(ERR_SYSTEM_ERROR)
            .description("Failed to create user")
            .cause(e);
    }
}
```

### 2. 资源关闭模式

确保资源正确释放：

```java
@BizQuery
public InputStream readFile(String filePath) {
    InputStream is = null;
    try {
        is = new FileInputStream(filePath);
        // 处理文件
        return is;
    } catch (IOException e) {
        throw new NopException(ERR_FILE_READ_ERROR)
            .param("path", filePath)
            .cause(e);
    } finally {
        if (is != null) {
            try {
                is.close();
            } catch (IOException e) {
                log.error("Failed to close file", e);
            }
        }
    }
}
```

### 3. 嵌套异常处理

处理多层异常：

```java
@BizMutation
public Order createOrder(OrderData data) {
    try {
        // 1. 验证订单
        validateOrder(data);

        // 2. 创建订单
        Order order = new Order();
        BeanTool.copyProperties(order, data);

        // 3. 更新库存
        try {
            updateInventory(order);
        } catch (InventoryException e) {
            // 库存更新失败：包装为订单异常
            throw new NopException(ERR_ORDER_CREATE_FAILED)
                .description("Failed to update inventory")
                .cause(e);
        }

        // 4. 保存订单
        return dao().saveEntity(order);
    } catch (ValidationException e) {
        // 验证失败
        throw new NopException(ERR_INVALID_INPUT, e);
    }
}
```

## 异常处理层次

### 1. 服务层

服务层处理业务异常：

```java
@BizModel("Order")
public class OrderBizModel extends CrudBizModel<NopOrder> {

    private static final Logger LOG = LoggerFactory.getLogger(OrderBizModel.class);

    @BizMutation
    @Transactional
    public Order createOrder(OrderData data) {
        // 1. 验证输入
        validateOrder(data);

        // 2. 创建订单
        Order order = new Order();
        BeanTool.copyProperties(order, data);

        // 3. 更新库存
        updateInventory(order);

        // 4. 保存订单
        return save(order);
    }

    private void validateOrder(OrderData data) {
        if (StringHelper.isEmpty(data.getUserId())) {
            throw new NopException(ERR_USER_ID_REQUIRED)
                .param("field", "userId");
        }

        if (data.getAmount() == null || data.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new NopException(ERR_INVALID_AMOUNT)
                .param("amount", data.getAmount());
        }

        // 验证用户存在
        User user = userDao.getEntityById(data.getUserId());
        if (user == null) {
            throw new NopException(ERR_USER_NOT_FOUND)
                .param("userId", data.getUserId());
        }
    }

    private void updateInventory(Order order) {
        try {
            inventoryService.updateInventory(
                order.getProductId(),
                order.getQuantity()
            );
        } catch (InventoryException e) {
            LOG.error("Failed to update inventory", e);
            throw new NopException(ERR_INVENTORY_UPDATE_FAILED, e);
        }
    }
}
```

### 2. 控制层

控制层处理HTTP响应：

```java
@RestController
public class OrderController {

    @Inject
    private IOrderBizModel orderBizModel;

    @PostMapping("/order/create")
    public ResponseEntity<?> createOrder(@RequestBody OrderData data) {
        try {
            Order order = orderBizModel.createOrder(data);
            return ResponseEntity.ok(order);
        } catch (NopException e) {
            return ResponseEntity
                .badRequest()
                .body(new ErrorResponse(
                    e.getErrorCode(),
                    e.getMessage()
                ));
        } catch (Exception e) {
            log.error("Unexpected error", e);
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(
                    "SYS_ERROR",
                    "Internal server error"
                ));
        }
    }
}
```

### 3. GraphQL层

GraphQL引擎自动处理异常：

```graphql
# 查询
query {
  Order {
    createOrder(order: {userId: "test", amount: 100}) {
      orderId
      orderNo
      __typename
    }
  }
}

# 错误响应
{
  "data": {
    "Order": {
      "createOrder": null,
      "__typename": "Order"
    }
  },
  "errors": [
    {
      "message": "User not found",
      "path": ["Order", "createOrder"],
      "extensions": {
        "code": "nop.err.user:not-found",
        "args": {
          "userId": "test"
        }
      }
    }
  ]
}
```

## 异常日志记录

### 1. 错误日志格式

统一错误日志格式：

```java
private static final Logger LOG = LoggerFactory.getLogger(MyClass.class);

@BizMutation
public void someMethod() {
    try {
        // 业务逻辑
    } catch (NopException e) {
        // 业务异常：记录错误信息
        LOG.error("Business error: code={}, message={}",
            e.getErrorCode(),
            e.getMessage()
        );
        throw e;
    } catch (Exception e) {
        // 系统异常：记录堆栈
        LOG.error("System error: operation=someMethod",
            e,
            () -> String.format("userId=%s, operation=%s",
                session.getUserId(), "someMethod")
            )
        );
        throw new NopException(ERR_SYSTEM_ERROR, e);
    }
}
```

### 2. 关键日志信息

记录关键上下文信息：

```java
try {
    processOrder(orderId);
} catch (Exception e) {
    LOG.error("Failed to process order: orderId={}, userId={}, status={}",
        orderId,
        session.getUserId(),
        order.getStatus(),
        e
    );
    throw e;
}
```

### 3. 日志级别控制

合理使用日志级别：

```java
// DEBUG: 详细的调试信息
LOG.debug("Processing request: {}", request);

// INFO: 一般业务信息
LOG.info("Order created: orderId={}", orderId);

// WARN: 警告信息
LOG.warn("Slow query: {}ms", costTime);

// ERROR: 错误信息
LOG.error("Failed to process: {}", error, exception);
```

## 异常转换

### 1. REST API异常转换

将异常转换为REST响应：

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NopException.class)
    public ResponseEntity<ErrorResponse> handleNopException(NopException e) {
        return ResponseEntity
            .badRequest()
            .body(new ErrorResponse(
                e.getErrorCode(),
                e.getMessage()
            ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("Unexpected error", e);
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ErrorResponse(
                "SYS_ERROR",
                "Internal server error"
            ));
    }
}
```

### 2. GraphQL异常转换

GraphQL引擎自动转换异常：

```java
@BizModel("Order")
public class OrderBizModel {

    @BizMutation
    public Order createOrder(OrderData data) {
        // NopException自动转换为GraphQL错误
        if (StringHelper.isEmpty(data.getUserId())) {
            throw new NopException(ERR_USER_ID_REQUIRED);
        }
        // ...
    }
}
```

### 3. 异步异常转换

处理异步操作中的异常：

```java
@Scheduled(cron = "0 */5 * * * ?")
public void processOrders() {
    List<Order> orders = getPendingOrders();
    for (Order order : orders) {
        try {
            processOrder(order);
        } catch (Exception e) {
            log.error("Failed to process order: orderId={}",
                order.getOrderId(), e);
            // 继续处理其他订单
            continue;
        }
    }
}
```

## 恢复策略

### 1. 重试机制

对可重试的错误进行重试：

```java
@Component
public class RetryService {

    @Inject
    private IOrderService orderService;

    @Retryable(
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000)
    )
    public Order createOrder(OrderData data) {
        return orderService.createOrder(data);
    }
}
```

### 2. 降级策略

服务不可用时提供降级方案：

```java
@Component
public class FallbackService {

    @HystrixCommand(
        fallbackMethod = "getOrderFallback",
        commandProperties = {
            @HystrixProperty(name = "execution.isolation.thread.timeoutInMilliseconds", value = "3000")
        }
    )
    public Order getOrder(String orderId) {
        return orderService.getOrder(orderId);
    }

    public Order getOrderFallback(String orderId) {
        // 从缓存获取订单
        Order order = cacheManager.get("order", orderId);
        if (order != null) {
            return order;
        }

        // 返回默认订单对象
        return new Order();
    }
}
```

### 3. 熔断器

连续失败时停止调用：

```java
@Component
public class CircuitBreakerService {

    @CircuitBreaker(
        failureThreshold = 5,
        resetTimeout = 30000
    )
    public Order getOrder(String orderId) {
        return orderService.getOrder(orderId);
    }
}
```

## 异常通知

### 1. 异常告警

关键异常发送告警：

```java
@Component
public class ExceptionNotifier {

    private static final Logger LOG = LoggerFactory.getLogger(ExceptionNotifier.class);

    @EventListener
    public void handleException(ExceptionEvent event) {
        if (event.isCritical()) {
            // 发送告警邮件
            sendAlertEmail(event);

            // 记录到告警系统
            logToAlertSystem(event);

            LOG.error("Critical exception: {}", event);
        }
    }

    private void sendAlertEmail(ExceptionEvent event) {
        // 邮件通知
        EmailMessage message = new EmailMessage();
        message.setTo("admin@example.com");
        message.setSubject("Critical Alert: " + event.getErrorCode());
        message.setContent(event.toString());
        emailService.send(message);
    }
}
```

### 2. 用户友好错误

返回用户可理解的错误信息：

```java
@BizMutation
public User createUser(User user) {
    try {
        return dao().saveEntity(user);
    } catch (DuplicateKeyException e) {
        // 返回用户友好的错误信息
        throw new NopException(ERR_EMAIL_EXISTS)
            .param("email", user.getEmail())
            .description("该邮箱已被注册，请使用其他邮箱");
    }
}
```

### 3. 错误跟踪

记录错误以便追踪：

```java
@Component
public class ErrorTracker {

    @Inject
    private IErrorLogDao errorLogDao;

    public void trackError(String operation, Exception e) {
        ErrorLog log = new ErrorLog();
        log.setOperation(operation);
        log.setErrorType(e.getClass().getSimpleName());
        log.setErrorMessage(e.getMessage());
        log.setStackTrace(StringHelper.exceptionToString(e));
        log.setCreateTime(new Date());
        log.setUserId(session.getUserId());
        log.setRequestId(MDC.get("requestId"));

        errorLogDao.saveEntity(log);
    }
}
```

## 常见错误处理问题

| 问题 | 原因 | 解决方案 |
|------|------|---------|
| 吞掉异常 | 没有记录或处理异常 | 记录并传播异常 |
| 过度捕获 | 捕获过于宽泛的异常 | 捕获具体异常类型 |
| 错误信息不明确 | 缺少上下文信息 | 添加参数和描述 |
| 日志不足 | 未记录关键信息 | 记录完整的上下文 |
| 异常链断裂 | 使用throw new而不是.cause() | 保持异常链 |
| 未处理资源 | 未关闭数据库连接等 | 使用try-finally |
| 错误码不规范 | 错误码不统一 | 定义统一的错误码体系 |

## 错误处理清单

### 异常创建

- [ ] 使用正确的错误码
- [ ] 添加必要的参数
- [ ] 提供描述性信息
- [ ] 保持异常链
- [ ] 记录原始异常

### 异常处理

- [ ] 在合适的层次处理异常
- [ ] 提供用户友好的错误信息
- [ ] 实现恢复策略
- [ ] 记录错误日志
- [ ] 发送关键异常告警

### 异常转换

- [ ] REST API正确转换异常
- [ ] GraphQL自动处理异常
- [ ] 异步异常正确处理
- [ ] 统一异常响应格式

## 相关文档

- [异常处理指南](../getting-started/core/exception-guide.md)
- [错误码定义](../getting-started/common/error-code.md)
- [服务层开发指南](../getting-started/service/service-layer-development.md)
- [事务管理指南](../getting-started/core/transaction-guide.md)

## 总结

Nop Platform错误处理是一个系统的工程，需要从多个层面考虑：

1. **错误类型区分**: 业务异常、验证异常、系统异常、未找到异常
2. **错误码规范**: 统一的错误码分组和层次结构
3. **异常创建**: 使用错误码、添加参数、保持异常链
4. **处理策略**: Try-Catch-Finally、资源关闭、嵌套处理
5. **层次处理**: 服务层、控制层、GraphQL层
6. **日志记录**: 统一日志格式、关键信息、级别控制
7. **异常转换**: REST API、GraphQL、异步操作
8. **恢复策略**: 重试机制、降级策略、熔断器
9. **异常通知**: 告警通知、用户友好错误、错误跟踪

遵循这些最佳实践，可以构建健壮、可靠、易于维护的错误处理体系。

---

**文档版本**: 1.0
**最后更新**: 2025-01-09
**作者**: AI Assistant (Sisyphus)
