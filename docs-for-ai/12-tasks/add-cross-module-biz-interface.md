# 新增跨模块 Biz 接口

## 适用场景

- 一个 BizModel 需要调用另一个 BizModel 的方法
- 希望该方法能通过接口注入、可替换、可扩展

## AI 决策提示

- ✅ 被其他 BizModel 调用的方法，放到 `I*Biz` 接口
- ✅ `I*Biz` 一般放在 `*-dao` 模块
- ✅ BizModel 实现类 `implements I*Biz`
- ❌ 不要直接注入另一个 BizModel 实现类

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

## 什么时候必须建接口

| 场景 | 是否需要 `I*Biz` |
|------|-------------------|
| 被其他 BizModel 调用 | 是 |
| 只通过 GraphQL/REST 暴露 | 可不建 |
| 需要在 Delta 中替换/扩展 | 建议建 |

## 常见坑

- ❌ 直接 `@Inject OrderBizModel`
- ❌ 接口方法缺少 `@Name` / `IServiceContext`
- ❌ 接口和实现签名不一致

## 相关文档

- `03-development-guide/bizmodel-guide.md`
- `12-tasks/write-bizmodel-method.md`
