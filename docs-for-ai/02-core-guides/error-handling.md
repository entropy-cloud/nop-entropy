# 错误处理与错误码

当前仓库里，业务错误的默认写法是：

**`NopException + ErrorCode + .param(...)`**

## 默认规则

1. 业务错误使用 `NopException`。
2. 统一定义 `ErrorCode`，不要随手抛 `RuntimeException("中文消息")`。
3. 用 `.param(...)` 附带上下文参数。
4. 保留原始异常链。
5. 普通 BizModel 示例中，异常处理应与 `requireEntity()`、`updateEntity()` 这些安全 API 搭配出现。

## 默认写法

### 定义错误码

```java
public interface OrderErrors {
    String ARG_ORDER_ID = "orderId";

    ErrorCode ERR_ORDER_NOT_FOUND = ErrorCode.define(
        "nop.err.order.not-found",
        "订单不存在:{orderId}",
        ARG_ORDER_ID
    );
}
```

### 抛出业务异常

```java
throw new NopException(OrderErrors.ERR_ORDER_NOT_FOUND)
    .param(OrderErrors.ARG_ORDER_ID, orderId);
```

### 包装底层异常

```java
catch (IOException e) {
    throw new NopException(OrderErrors.ERR_ORDER_SYNC_FAILED, e)
        .param(OrderErrors.ARG_ORDER_ID, orderId);
}
```

## 普通 BizModel 中的推荐姿势

1. 先用 `requireEntity()` 获取目标实体。
2. 再做业务规则判断。
3. 规则不满足时抛 `NopException`。

## 不要这样写

| 反模式 | 问题 |
|--------|------|
| `throw new RuntimeException("订单不存在")` | 没有稳定错误码，不利于 i18n 和 API 消费方处理 |
| 丢失原始异常链 | 排查困难 |
| 在普通 BizModel 示例中同时展示底层 DAO + 显式事务 | 容易把边界模式误当默认模板 |

## 错误码设计建议

### 命名

推荐格式：

```text
nop.err.[模块].[子域].[错误]
```

### 参数名

为动态参数定义统一常量，例如：

```java
String ARG_ORDER_ID = "orderId";
```

## API 层的表现

普通情况下，BizModel 抛出的 `NopException` 会被框架转换为结构化错误响应。对业务代码来说，最重要的是：

1. 错误码稳定。
2. 参数完整。
3. 异常链保留。

## 相关文档

- `./service-layer.md`
- `../03-runbooks/error-codes-and-nop-exception.md`
- `../03-runbooks/transaction-boundaries.md`
