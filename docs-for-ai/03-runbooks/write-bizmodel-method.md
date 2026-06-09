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

在 BizModel 内部构造实体对象（非前端 Map 输入）时，用 `newEntity()` 创建 + `saveEntity()` 持久化：

```java
// 创建本 BizModel 对应的实体
@BizMutation
public Order createOrder(@Name("addressId") String addressId,
                         IServiceContext context) {
    Order order = newEntity();
    order.setUserId(context.getUserId());
    order.setOrderSn(generateSn());
    // ... 设置字段 ...
    saveEntity(order, "create", context);
    return order;
}
```

**创建其他实体时，通过注入的 `I*Biz` 调用 `newEntity()`**：

```java
// OrderBizModel 中创建 OrderGoods（不是本 BizModel 的实体）
@Inject
IOrderGoodsBiz orderGoodsBiz;

OrderGoods item = orderGoodsBiz.newEntity();
item.setOrderId(order.getId());
item.setGoodsId(goodsId);
orderGoodsBiz.saveEntity(item, "create", context);
```

> **禁止 `new Order()` / `new OrderGoods()`**：实体可能被 Delta 机制扩展为派生类。`newEntity()` 通过 DAO 创建实例，确保返回正确的派生类。创建自身实体用 `newEntity()`（继承自 `CrudBizModel`），创建其他实体用 `xxxBiz.newEntity()`。

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

## 写后自检清单

**写完每个 BizModel 方法后，必须逐条检查以下规则。** 不要把清单当阅读材料跳过——它是写完代码后的硬性校验步骤。

- [ ] 没有多余的 `@Transactional`（`@BizMutation` 已自带事务）
- [ ] 数据获取走 `requireEntity()` / `doFindList()` / `doFindPage()`，没有直接 `dao().getEntityById()`
- [ ] 所有参数有 `@Name` 注解（或使用 `@RequestBean`）
- [ ] **返回值没有使用 `Map<String, Object>`** — 多字段返回用 `@DataBean` DTO，单实体返回直接返回实体
- [ ] `@Inject` 字段不是 `private`（使用 package-private 或 protected）
- [ ] 新建实体用 `newEntity()` / `xxxBiz.newEntity()`，没有直接 `new XxxEntity()`
- [ ] BizModel 新增的 public 方法已同步到 `I*Biz` 接口（含注解和 `@Name`）
- [ ] `I*Biz` 接口方法有 `@BizQuery` / `@BizMutation` / `@BizAction` 之一
- [ ] 程序化创建实体用 `saveEntity()`，不是 `save(Map)`

> **为什么强调逐条检查而不是"读了就知道"：** 实现阶段注意力集中在让逻辑跑通，容易跳过文档中已明确的约定。把规则变成写后自检步骤，是防止"读了规则但写出来违反规则"的最有效方式。

## 相关文档

- `../02-core-guides/service-layer.md`
- `./custom-query-with-querybean.md`
- `./create-request-response-dto.md`
- `./transaction-boundaries.md`
