# 事务管理规范

本文档说明 Nop 平台中与 AI 生成代码最相关的事务规则。

默认结论：

1. 普通 BizModel 写操作用 `@BizMutation`
2. 不要在普通 `@BizMutation` 上再叠加 `@Transactional`
3. 需要提交后回调时，用 `txn().afterCommit(...)`
4. 只有边界层场景才显式使用 `@Transactional(REQUIRES_NEW)` 或 `ITransactionTemplate`

---

## 一、普通 BizModel 的默认规则

### 1. 写操作

```java
@BizMutation
public Order cancel(@Name("orderId") String orderId, IServiceContext context) {
    Order order = requireEntity(orderId, "cancel", context);
    order.setStatus(OrderConstants.CANCELLED);
    updateEntity(order, "cancel", context);
    return order;
}
```

说明：

- `@BizMutation` 默认已带事务
- 不需要额外加 `@Transactional`

### 2. 查询操作

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

说明：

- `@BizQuery` 不需要再加 `@Transactional(readOnly = true)`

---

## 二、事务后回调

普通 BizModel 中最常见的事务相关扩展点是 `afterCommit`：

```java
@BizMutation
public Order pay(@Name("orderId") String orderId, IServiceContext context) {
    Order order = requireEntity(orderId, "pay", context);
    order.setStatus(OrderConstants.PAID);
    updateEntity(order, "pay", context);

    txn().afterCommit(null, () -> {
        notifyPaid(order);
    });
    return order;
}
```

适用场景：

- 发消息
- 发通知
- 更新缓存
- 事务提交后才能安全执行的副作用

---

## 三、什么时候才显式使用 `@Transactional`

以下属于边界层场景：

1. 非 BizModel 方法
2. store / infra 层
3. 需要 `REQUIRES_NEW`
4. 多事务组或底层事务模板控制

代表性模式：

```java
@Transactional(propagation = TransactionPropagation.REQUIRES_NEW)
public void insertManualFire(...) {
    fireDao().saveEntityDirectly(fire);
    scheduleDao().updateEntityDirectly(schedule);
}
```

这类写法可参考：`io.nop.job.dao.store.JobScheduleStoreImpl`

但它不是普通 BizModel 默认模板。

---

## 四、什么时候用 `ITransactionTemplate`

适合：

1. 需要显式指定事务组
2. 需要手工包裹一段非 BizModel 逻辑
3. 需要异步事务模板

```java
@Inject
protected ITransactionTemplate txnTemplate;

public void process() {
    txnTemplate.runInTransaction(null, TransactionPropagation.REQUIRED, txn -> {
        // ...
        return null;
    });
}
```

---

## 五、反模式

1. `@BizMutation @Transactional`
2. `@BizQuery @Transactional(readOnly = true)`
3. 在普通 BizModel 中用原始 DAO 操作代替 `updateEntity()` / `save()` / `delete()`
4. 把 infra/store 层的 `REQUIRES_NEW + saveEntityDirectly()` 写法当成业务层默认模板

---

## 六、源码锚点

- `io.nop.biz.service.BizActionInvoker`
- `io.nop.job.dao.store.JobScheduleStoreImpl`
- `io.nop.biz.crud.CrudBizModel`

## 七、相关文档

- `../12-tasks/transaction-boundaries.md`
- `../12-tasks/write-bizmodel-method.md`
- `./exception-handling.md`
- `../13-reference/source-anchors.md`
