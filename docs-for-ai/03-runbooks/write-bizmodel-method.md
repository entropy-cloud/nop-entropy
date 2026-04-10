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
    return doFindList(query, selection, context);
}
```

## 修改方法最小模板

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
