# 编写 BizModel 方法

## 适用场景

- 需要添加自定义查询或修改动作。
- 标准 CRUD 之外还要补充业务接口。

## AI 决策提示

- 查询用 `@BizQuery`，修改用 `@BizMutation`。
- 最后一个参数通常是 `IServiceContext`。
- 参数少时用 `@Name`，复杂入参用 `@RequestBean`。
- 普通数据访问优先 `requireEntity()`、`doFindList()`、`doFindPage()`。

## 查询方法最小模板

```java
@BizQuery
public List<Order> getOrdersByUser(@Name("userId") String userId,
                                   FieldSelectionBean selection,
                                   IServiceContext context) {
    QueryBean query = new QueryBean();
    query.addFilter(FilterBeans.eq("userId", userId));
    return doFindList(query, this::invokeDefaultPrepareQuery, selection, context);
}
```

> **注意：`doFindList` 签名是 4 参数** `(query, prepareQuery, selection, context)`，`prepareQuery` 是 `BiConsumer<QueryBean, IServiceContext>`，多数场景传 `this::invokeDefaultPrepareQuery`。传 `null` 则跳过默认预处理。

## 修改方法最小模板

### 更新已有实体

```java
@BizMutation
public Order cancel(@Name("orderId") String orderId,
                    IServiceContext context) {
    Order order = requireEntity(orderId, "cancel", context);
    order.setStatus(OrderConstants.CANCELLED);
    updateEntity(order, "cancel", context);
    return order;
}
```

### 程序化创建新实体

在 BizModel 内部构造实体对象（非前端 Map 输入）时，用 `saveEntity`：

```java
@BizMutation
public Order createOrder(@Name("addressId") String addressId,
                         IServiceContext context) {
    Order order = new Order();
    order.setUserId(context.getUserId());
    order.setOrderSn(generateSn());
    // ... 设置字段 ...
    saveEntity(order, "create", context);
    return order;
}
```

> **`save(Map)` vs `saveEntity(entity)`**：`save(Map, context)` 用于前端传入的 `Map<String, Object>` 数据；`saveEntity(entity, action, context)` 用于 BizModel 内部已持有实体对象时直接持久化。两者都含权限检查和 afterEntityChange 触发。

## `do*` 方法的组合式回调

CrudBizModel 的 `doFindList`、`doSave`、`doUpdate`、`doDelete` 等管道方法都有一个 `prepare*` 回调参数，用于组合式扩展。不需要子类 override，在调用时直接传入逻辑即可。

```java
// 组合式：注入额外的查询条件
doFindList(query, (q, ctx) -> {
    q.addFilter(FilterBeans.eq("userId", ctx.getUserId()));
    q.addOrderField("createTime", false);
}, null, context);

// 继承式：传 this::invokeDefaultPrepareQuery，走子类 override 的 defaultPrepareQuery
// 详见 extend-crud-with-hooks.md
```

更多细节见 `../04-reference/safe-api-reference.md` 中的"组合式回调参数"一节。

## 多参数场景

默认使用 `@RequestBean` + `@DataBean` DTO，不要用复杂 `Map<String, Object>` 充当正式接口契约。

## 事务后回调

```java
txn().afterCommit(null, () -> {
    notifyPaid(order);
});
```

这个写法只适用于当前已经在事务里的修改动作。普通 `@BizMutation` 可以这样写；`@BizQuery` 不要直接照搬。

## 常见坑

1. `@BizMutation @Transactional`
2. `dao().getEntityById(id)` 作为默认模板
3. 参数缺少 `@Name`
4. 复杂返回值用 `Map<String, Object>`
5. `@Inject private Foo foo;`

## 相关文档

- `../02-core-guides/service-layer.md`
- `./custom-query-with-querybean.md`
- `./create-request-response-dto.md`
- `./transaction-boundaries.md`
