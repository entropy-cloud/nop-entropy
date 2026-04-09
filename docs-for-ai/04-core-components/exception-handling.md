# 异常处理与错误码规范

本文档是 Nop 平台在业务代码中的异常处理规范。

默认规则：

1. 业务错误使用 `NopException + ErrorCode`
2. 用 `.param(...)` 附带上下文参数
3. 保留原始异常链
4. 在普通 BizModel 中，异常示例应配合 `requireEntity()` / `updateEntity()` 等安全 API 使用

---

## 一、默认写法

### 1. 定义 ErrorCode

```java
public interface OrderErrors {
    String ARG_ORDER_ID = "orderId";

    ErrorCode ERR_ORDER_NOT_FOUND = ErrorCode.define(
        "nop.err.order.not-found",
        "订单不存在:{orderId}",
        ARG_ORDER_ID
    );

    ErrorCode ERR_ORDER_STATUS_INVALID = ErrorCode.define(
        "nop.err.order.invalid-status",
        "订单状态不允许执行当前操作:{orderId}",
        ARG_ORDER_ID
    );
}
```

### 2. 抛出业务异常

```java
throw new NopException(OrderErrors.ERR_ORDER_NOT_FOUND)
    .param(OrderErrors.ARG_ORDER_ID, orderId);
```

### 3. 包装底层异常

```java
try {
    externalService.call();
} catch (IOException e) {
    throw new NopException(OrderErrors.ERR_ORDER_STATUS_INVALID, e)
        .param(OrderErrors.ARG_ORDER_ID, orderId);
}
```

---

## 二、普通 BizModel 中的推荐写法

```java
@BizModel("Order")
public class OrderBizModel extends CrudBizModel<Order> {

    @BizMutation
    public Order cancel(@Name("orderId") String orderId, IServiceContext context) {
        Order order = requireEntity(orderId, "cancel", context);

        if (!order.canBeCancelled()) {
            throw new NopException(OrderErrors.ERR_ORDER_STATUS_INVALID)
                .param(OrderErrors.ARG_ORDER_ID, orderId);
        }

        order.setStatus(OrderConstants.CANCELLED);
        updateEntity(order, "cancel", context);
        return order;
    }
}
```

这里有两个重点：

1. 不要把 `dao().getEntityById()` 写成 BizModel 默认示例
2. 不要在普通 `@BizMutation` 方法上再叠加 `@Transactional`

---

## 三、什么时候可以用更底层的事务/DAO 语义

边界层代码可以更靠近 DAO 和显式事务，例如：

- store / infra 层
- `REQUIRES_NEW`
- 调度、版本检查、底层批量处理

代表性模式可参考：`io.nop.job.dao.store.JobScheduleStoreImpl`

这类场景中可以出现：

- `@Transactional(propagation = TransactionPropagation.REQUIRES_NEW)`
- `daoFor(...)`
- `saveEntityDirectly(...)`
- `updateEntityDirectly(...)`

但这些写法不应作为普通 BizModel 的默认模板。

---

## 四、异常设计建议

### 1. 错误码命名

推荐格式：

```text
nop.err.[模块].[子域].[错误]
```

### 2. 参数名常量

为可变参数统一定义常量：

```java
String ARG_ORDER_ID = "orderId";
```

### 3. 不要硬编码业务错误文本

避免：

```java
throw new RuntimeException("订单不存在");
```

改为：

```java
throw new NopException(OrderErrors.ERR_ORDER_NOT_FOUND)
    .param(OrderErrors.ARG_ORDER_ID, orderId);
```

### 4. 需要额外控制时再使用高级标记

例如：

- `.httpStatus(...)`
- `.bizFatal(true)`
- `.notRollback(true)`

这些属于高级场景，不应成为普通业务示例的默认内容。

---

## 五、GraphQL / REST 中的表现

普通情况下，BizModel 抛出的 `NopException` 会被框架转换为结构化错误响应，包含：

- 错误码
- 描述
- 参数
- 状态码（如果有）

因此，业务层最重要的是：

1. 使用稳定错误码
2. 补充足够的 `.param(...)`
3. 保留异常链

---

## 六、相关文档

- `./error-codes.md`
- `../12-tasks/error-codes-and-nop-exception.md`
- `../03-development-guide/bizmodel-guide.md`
- `../07-best-practices/error-handling.md`
- `../13-reference/source-anchors.md`
