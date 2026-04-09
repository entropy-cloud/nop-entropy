# 选择放在 Entity、BizModel 还是 Processor

## 适用场景

- 你已经知道要加一段业务逻辑，但不确定应该放在哪一层

## AI 决策提示

- ✅ 只读领域事实、状态判断、纯函数：优先放 Entity
- ✅ 普通查询/修改、跨聚合协调、外部服务调用：优先放 BizModel
- ✅ 方法过长、流程复杂、需要复用：拆成 Processor
- ✅ 只有跨多个 Processor 复用的单一动作，才继续抽 Step

## 最小闭环

### 1. 放 Entity

适合：

- `isXxx()`
- `canXxx()`
- `calculateXxx()`

```java
public class Order extends _Order {
    public boolean canBeCancelled() {
        return OrderConstants.PENDING.equals(getStatus());
    }
}
```

### 2. 放 BizModel

适合：

- `@BizQuery`
- `@BizMutation`
- 需要 `IServiceContext`
- 需要 `requireEntity()` / `doFindList()`

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

### 3. 放 Processor

适合：

- 单个 BizModel 方法超过约 50 行
- 逻辑明显是“多步骤流程”
- 需要在多个 BizModel 之间复用

```java
public class OrderSubmitProcessor {
    public SubmitOrderResult process(SubmitOrderRequest request, IServiceContext context) {
        validateRequest(request, context);
        lockInventory(request, context);
        createOrder(request, context);
        return buildResult(context);
    }
}
```

## 快速判断表

| 场景 | 默认位置 |
|------|---------|
| 纯读取当前实体字段 | Entity |
| 判断实体状态 | Entity |
| GraphQL/REST 暴露的方法 | BizModel |
| 需要事务写操作 | BizModel |
| 复杂流程 orchestration | Processor |
| 跨多个 Processor 复用的单一动作 | Step |

## 常见坑

- ❌ 在 Entity 中写持久化、事务、外部服务调用
- ❌ BizModel 一个方法塞满全部流程步骤
- ❌ 还没出现复用就过早抽 Step

## 相关文档

- `03-development-guide/bizmodel-guide.md`
- `03-development-guide/processor-development.md`
- `03-development-guide/ddd-in-nop.md`
