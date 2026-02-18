# 核心错误码参考

## 概述

Nop平台使用结构化的错误码系统，所有错误都通过`ErrorCode`定义，支持国际化消息和参数化错误描述。

## 错误码定义

错误码命名规范：`模块_子模块_具体错误`

示例：`nop.err.dao.entity-not-found`

### 基本错误码

#### 1. 通用API错误 (`ApiErrors`)

| 错误码 | 描述 | 使用场景 |
|---------|------|---------|
| `ERR_TIMEOUT` | 超时 | 操作执行超时 |
| `ERR_CHECK_NOT_EQUALS` | 实际值不等于期待值 | 断言检查失败 |
| `ERR_CHECK_INVALID_POSITION_INDEX` | 下标超出范围 | 数组/集合索引越界 |
| `ERR_CHECK_INVALID_ARGUMENT` | 非法参数 | 参数验证失败 |
| `ERR_CHECK_OBJ_IS_FROZEN` | 对象已被冻结，不允许被修改 | 试图修改不可变对象 |
| `ERR_CONVERT_TO_TYPE_FAIL` | 数据类型转换错误 | 类型转换失败 |
| `ERR_IOC_BEAN_CONTAINER_NOT_INITIALIZED` | Bean容器未初始化 | IoC容器未启动 |

#### 2. 通用错误 (`CommonErrors`)

| 错误码 | 描述 | 使用场景 |
|---------|------|---------|
| `ERR_COLLECTIONS_NOT_LIST` | 对象不是List类型 | 集合类型转换失败 |
| `ERR_COLLECTIONS_NOT_SUPPORT_STREAM` | 集合不支持Stream | 集合操作不支持 |
| `ERR_REFLECT_NEW_INSTANCE_FAIL` | 反射创建实例失败 | 通过反射创建对象失败 |
| `ERR_LOAD_CLASS_NOT_EXPECTED_TYPE` | 加载的类不是期望的类型 | 类型检查失败 |

#### 3. DAO错误

| 错误码 | 描述 | 使用场景 |
|---------|------|---------|
| `ERR_DAO_UNKNOWN_ENTITY` | 实体不存在 | 查询的实体不存在 |

**注意**：更多DAO相关错误码请参考 `io.nop.dao.DaoErrors` 接口定义。

#### 4. 验证错误

| 错误码 | 描述 | 使用场景 |
|---------|------|---------|
| `ERR_VALIDATE_CHECK_FAIL` | 验证检查失败 | 业务规则验证失败 |

**注意**：其他验证相关错误码可能定义在各个业务模块的 `*Errors` 接口中，使用时应根据实际模块导入相应的错误码接口。

## NopException 使用

### 基本用法

```java
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.ApiErrors;

// 抛出简单异常
throw new NopException(ApiErrors.ERR_CHECK_INVALID_ARGUMENT);

// 添加参数
throw new NopException(ApiErrors.ERR_CHECK_INVALID_ARGUMENT)
    .param("paramName", "userId")
    .param("paramValue", userId);
```

### 带自定义描述

```java
throw new NopException(ApiErrors.ERR_CONVERT_TO_TYPE_FAIL)
    .param("value", value)
    .param("targetType", targetClass.getName())
    .description("无法将值转换为指定类型");
```

### 保留原始异常

```java
try {
    // 可能抛出异常的代码
} catch (Exception e) {
    throw new NopException(ApiErrors.ERR_WRAP_EXCEPTION)
        .param("message", e.getMessage())
        .cause(e); // 保留原始异常
}
```

### 事务控制

```java
// 不回滚事务
throw new NopException(ApiErrors.ERR_CHECK_INVALID_ARGUMENT)
    .setNotRollback(true);

// 标记为致命错误
throw new NopException(ApiErrors.ERR_CHECK_INVALID_ARGUMENT)
    .setBizFatal(true);
```

## 常见参数名

### 通用参数

| 参数名 | 含义 | 示例 |
|--------|------|------|
| `ARG_VALUE` | 值 | 值[{value}]无效 |
| `ARG_INDEX` | 索引 | 索引[{index}]超出范围 |
| `ARG_SIZE` | 大小 | size为{size} |
| `ARG_EXPECTED` | 期望值 | 期望值为[{expected}] |
| `ARG_ACTUAL` | 实际值 | 实际值为[{actual}] |
| `ARG_NAME` | 名称 | 名称为[{name}] |
| `ARG_CLASS_NAME` | 类名 | 类名为[{className}] |
| `ARG_BEAN_NAME` | Bean名称 | Bean名称为[{beanName}] |

### 业务参数

```java
// 常见业务参数
.param("userId", userId)
.param("orderId", orderId)
.param("status", status)
.param("errorCode", errorCode)
.param("message", message)
```

## 自定义错误码

### 定义错误码类

```java
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.annotations.core.Locale;
import static io.nop.api.core.exceptions.ErrorCode.define;

@Locale("zh-CN")
public interface MyErrors {
    String ARG_USER_ID = "userId";
    String ARG_ORDER_ID = "orderId";

    ErrorCode ERR_USER_NOT_FOUND = define(
        "my.err.user.not-found",
        "用户不存在，用户ID为[{userId}]",
        ARG_USER_ID
    );

    ErrorCode ERR_ORDER_STATUS_INVALID = define(
        "my.err.order.status-invalid",
        "订单状态无效，当前状态[{status}]，期望状态[{expectedStatus}]",
        "status", "expectedStatus"
    );

    ErrorCode ERR_ORDER_CANNOT_CANCEL = define(
        "my.err.order.cannot-cancel",
        "订单无法取消，订单ID为[{orderId}]",
        ARG_ORDER_ID
    );
}
```

### 使用自定义错误码

```java
import io.nop.api.core.exceptions.NopException;

public void cancelOrder(String orderId) {
    Order order = dao().getEntityById(orderId);
    if (order == null) {
        throw new NopException(MyErrors.ERR_ORDER_NOT_FOUND)
            .param("orderId", orderId);
    }

    if (!canCancel(order)) {
        throw new NopException(MyErrors.ERR_ORDER_CANNOT_CANCEL)
            .param("orderId", orderId);
    }

    // 执行取消逻辑
}
```

## 国际化支持

### 定义多语言错误消息

```java
// 中文错误消息
@Locale("zh-CN")
public interface MyErrorsZh {
    ErrorCode ERR_USER_NOT_FOUND = define(
        "my.err.user.not-found",
        "用户不存在，用户ID为[{userId}]",
        ARG_USER_ID
    );
}

// 英文错误消息
@Locale("en-US")
public interface MyErrorsEn {
    ErrorCode ERR_USER_NOT_FOUND = define(
        "my.err.user.not-found",
        "User not found with userId [{userId}]",
        ARG_USER_ID
    );
}
```

### 切换语言

```java
import io.nop.api.core.message.IMessageFormatter;
import io.nop.api.core.message.MessageFormatter;

// 设置当前语言
IMessageFormatter formatter = MessageFormatter.instance();
formatter.setLocale(Locale.US);

// 错误消息会自动使用英文
```

## 常见场景

### 场景1：实体不存在

```java
import io.nop.dao.DaoErrors;

public User getUser(String userId) {
    User user = dao().getEntityById(userId);
    if (user == null) {
        throw new NopException(DaoErrors.ERR_DAO_UNKNOWN_ENTITY)
            .param("entityName", "User")
            .param("entityId", userId);
    }
    return user;
}
```

### 场景2：参数验证

```java
public void createUser(String username, String password) {
    if (StringHelper.isEmpty(username)) {
        throw new NopException(ApiErrors.ERR_CHECK_INVALID_ARGUMENT)
            .param("field", "username")
            .param("reason", "不能为空");
    }

    if (password.length() < 6) {
        throw new NopException(ApiErrors.ERR_CHECK_INVALID_ARGUMENT)
            .param("field", "password")
            .param("reason", "长度不能少于6位");
    }

    // 创建用户
}
```

### 场景3：业务规则验证

```java
public void transfer(String fromId, String toId, BigDecimal amount) {
    Account from = accountDao().getEntityById(fromId);
    Account to = accountDao().getEntityById(toId);

    if (from.getBalance().compareTo(amount) < 0) {
        // 使用自定义模块错误码
        throw new NopException(MyModuleErrors.ERR_INSUFFICIENT_BALANCE)
            .param("accountId", fromId)
            .param("balance", from.getBalance())
            .param("amount", amount);
    }

    // 执行转账
}
```

## 最佳实践

### 1. 使用预定义错误码

```java
// ✅ 推荐：使用预定义错误码
throw new NopException(ApiErrors.ERR_CHECK_INVALID_ARGUMENT)
    .param("field", "username");

// ❌ 不推荐：硬编码错误消息
throw new NopException("username参数无效");
```

### 2. 提供充分的参数

```java
// ✅ 推荐：提供详细参数
throw new NopException(ApiErrors.ERR_DAO_ENTITY_NOT_FOUND)
    .param("entityType", "User")
    .param("id", userId);

// ❌ 不推荐：参数不足
throw new NopException(ApiErrors.ERR_DAO_ENTITY_NOT_FOUND);
```

### 3. 保留原始异常

```java
// ✅ 推荐：保留原始异常
try {
    doSomething();
} catch (Exception e) {
    throw new NopException(ApiErrors.ERR_WRAP_EXCEPTION)
        .cause(e);
}

// ❌ 不推荐：丢失原始异常
try {
    doSomething();
} catch (Exception e) {
    log.error("Error", e);
    throw new NopException("操作失败");
}
```

### 4. 使用业务相关的错误码

```java
// ✅ 推荐：使用业务错误码
throw new NopException(MyErrors.ERR_USER_NOT_FOUND)
    .param("userId", userId);

// ❌ 不推荐：使用通用错误码
throw new NopException(ApiErrors.ERR_CHECK_INVALID_ARGUMENT)
    .param("reason", "用户不存在");
```

### 5. 错误消息国际化

```java
// ✅ 推荐：支持国际化
ErrorCode ERR_USER_NOT_FOUND = define(
    "my.err.user.not-found",
    "用户不存在，用户ID为[{userId}]",
    ARG_USER_ID
);

// ❌ 不推荐：硬编码中文
throw new NopException("用户不存在");
```

## 错误码命名规范

### 命名格式

```
模块名.子模块名.具体错误
```

### 示例

```
# DAO模块
nop.err.dao.unknown-entity
nop.err.dao.txn.not-in-transaction

# 验证模块
nop.err.validate.check-fail

# 业务模块（示例）
mymodule.err.user.not-found
mymodule.err.order.cannot-cancel
```

### 命名原则

1. **使用小写和连字符**
2. **避免使用中文拼音**
3. **错误码应该具有描述性**
4. **同一模块的错误码使用相同前缀**

## 相关文档

- [异常处理指南](./exception-handling.md)
- [错误码定义](./error-code.md)
