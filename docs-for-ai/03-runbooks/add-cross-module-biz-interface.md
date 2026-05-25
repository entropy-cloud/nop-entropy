# 新增跨模块 Biz 接口

## 适用场景

- 一个 BizModel 需要调用另一个 BizModel 的能力。
- 希望调用点通过接口注入、可替换、可扩展。

## AI 决策提示

- 被其他 BizModel 调用的方法，优先放到 `I*Biz` 接口。
- `I*Biz` 通常放在 `*-dao/.../biz/`。**不在 `*-api/`。**
- BizModel 实现类 `implements I*Biz`。
- 不要直接注入另一个 BizModel 实现类。
- `I*Biz` 是模块内部 BizModel 间调用的契约接口。`*-api/` 中的 typed service interface（如 `WorkflowService`）是外部系统 RPC 调用的接口，两者定位不同。

## 最小闭环

### 1. 在 `*-dao` 中定义接口

```java
public interface IOrderBiz extends ICrudBiz<Order> {
    @BizMutation("cancel")
    Order cancel(@Name("orderId") String orderId, IServiceContext context);
}
```

### 2. 在 `*-service` 中实现接口

```java
@BizModel("Order")
public class OrderBizModel extends CrudBizModel<Order> implements IOrderBiz {
    @Override
    @BizMutation
    public Order cancel(@Name("orderId") String orderId, IServiceContext context) {
        Order order = requireEntity(orderId, "cancel", context);
        order.setStatus(OrderConstants.CANCELLED);
        updateEntity(order, "cancel", context);
        return order;
    }
}
```

### 3. 在调用方注入接口

```java
@Inject
protected IOrderBiz orderBiz;
```

## 什么时候应该建接口

| 场景 | 是否建议 `I*Biz` |
|------|------------------|
| 被其他 BizModel 调用 | 是 |
| 需要在 Delta 中替换或扩展 | 建议 |
| 只在当前 BizModel 内部使用 | 可不建 |

## 常见坑

1. 直接 `@Inject OrderBizModel`。
2. 接口方法缺少 `@Name` 或 `IServiceContext`。
3. 接口和实现签名不一致。

## 相关文档

- `./write-bizmodel-method.md`
- `../02-core-guides/service-layer.md`
- `../04-reference/source-anchors.md`
