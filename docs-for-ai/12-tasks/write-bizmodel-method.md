# 编写 BizModel 方法（@BizQuery/@BizMutation）

## 适用场景

- 需要添加自定义业务方法（非标准 CRUD）
- 需要实现查询/修改操作

## AI 决策提示

- ✅ 查询用 `@BizQuery`，修改用 `@BizMutation`（自动事务）
- ✅ 最后一个参数必须是 `IServiceContext`
- ✅ 所有业务参数必须有 `@Name` 注解
- ✅ 使用 `requireEntity()`/`doFindList()` 而非 `dao().xxx()`
- ❌ 不要在 `@BizMutation` 方法中再加 `@Transactional`

## 必要 Import

```java
import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.biz.crud.CrudBizModel;
import io.nop.core.context.IServiceContext;
import jakarta.inject.Inject;
import static io.nop.api.core.beans.FilterBeans.eq;
```

## 最小闭环

### 查询方法（@BizQuery）

```java
@BizModel("Order")
public class OrderBizModel extends CrudBizModel<LitemallOrder> {

    @BizQuery
    public List<LitemallOrder> getOrdersByUser(
            @Name("userId") String userId,
            FieldSelectionBean selection,
            IServiceContext context) {
        QueryBean query = new QueryBean();
        query.addFilter(eq("userId", userId));
        return doFindList(query, selection, context);
    }
}
```

### 修改方法（@BizMutation）

```java
@BizModel("Order")
public class OrderBizModel extends CrudBizModel<LitemallOrder> {

    @BizMutation  // 自动开启事务，无需 @Transactional
    public LitemallOrder cancel(
            @Name("orderId") String orderId,
            IServiceContext context) {
        LitemallOrder order = requireEntity(orderId, "update", context);
        order.setOrderStatus(ORDER_STATUS_CANCEL);
        updateEntity(order, null, context);
        return order;
    }
}
```

### 多参数用 @RequestBean

```java
@DataBean
public class SubmitOrderRequest implements Serializable {
    private List<String> cartIds;
    private String addressId;
    private String couponId;
}

@BizMutation
public SubmitOrderResult submitOrder(
        @RequestBean SubmitOrderRequest request,
        IServiceContext context) {
    // ...
}
```

## 数据访问方法选择

| 操作 | ✅ 推荐 | ❌ 避免 |
|------|---------|--------|
| 获取实体 | `requireEntity(id, "update", context)` | `dao().getEntityById(id)` |
| 查询列表 | `doFindList(query, selection, context)` | `dao().findListByQuery(query)` |
| 分页查询 | `doFindPage(query, selection, context)` | `dao().findPageByQuery(query)` |
| 保存实体 | `save(data, context)` 或 `updateEntity(entity, action, context)` | `dao().saveEntity(entity)` |
| 删除实体 | `delete(id, context)` | `dao().deleteEntity(entity)` |

## 事务后回调

```java
@BizMutation
public LitemallOrder pay(@Name("orderId") String orderId, IServiceContext context) {
    LitemallOrder order = requireEntity(orderId, "update", context);
    // ... 业务逻辑 ...
    updateEntity(order, null, context);
    
    // 事务提交后执行（如发送通知）
    txn().afterCommit(null, () -> {
        sendNotification(order);
    });
    
    return order;
}
```

## 常见坑

- ❌ `@BizMutation @Transactional` → 重复事务
- ❌ `dao().getEntityById(id)` → 跳过数据权限
- ❌ 参数缺少 `@Name` 注解 → GraphQL 无法识别
- ❌ 返回 `Map<String, Object>` → GraphQL 无法推断类型
- ❌ `@Inject private Foo foo;` → NopIoC 不支持 private 注入

## 相关文档

- `03-development-guide/bizmodel-guide.md`（完整规范）
- `03-development-guide/crud-development.md`
- `12-tasks/extend-crud-with-hooks.md`
