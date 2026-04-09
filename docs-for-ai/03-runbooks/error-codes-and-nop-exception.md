# 错误码与 NopException

## 适用场景

- 参数校验失败、业务规则冲突、权限不足等需要返回稳定错误码。

## AI 决策提示

- 业务错误使用 `NopException + ErrorCode`。
- 通过 `.param(...)` 传递上下文参数。
- 不要直接抛 `RuntimeException("中文消息")`。

## 最小闭环

### 1. 定义错误码

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

### 2. 使用错误码

```java
throw new NopException(OrderErrors.ERR_ORDER_NOT_FOUND)
    .param(OrderErrors.ARG_ORDER_ID, orderId);
```

## Processor 里的要求

Processor 中不要直接内联大量错误码常量。优先放到模块级 `XxxErrors` 或 Processor 专用的 `XxxProcessorErrors` 接口里。

## 常见坑

1. 直接抛字符串异常。
2. 参数名与模板占位符不一致。
3. 在 Processor 内就地散落定义错误码。

## 相关文档

- `../02-core-guides/error-handling.md`
