# 安全 API 速查（普通 BizModel 场景）

本页只收录 **AI 在普通 BizModel / CrudBizModel 场景下应优先使用的 API**。

它不是原始 DAO 全量手册。

如果你正在写普通业务服务，默认先看这里；如果你在写 store / infra 层，再去看底层 DAO 能力。

---

## 一、CrudBizModel 默认优先 API

### 1. 获取实体

| 场景 | 优先方法 |
|------|---------|
| 不存在直接抛错 | `requireEntity(id, action, context)` |
| 可返回 `null` | `get(id, ignoreUnknown, context)` |
| 批量获取 | `batchGet(ids, ignoreUnknown, context)` |

### 2. 查询

| 场景 | 优先方法 |
|------|---------|
| 列表查询 | `doFindList(query, selection, context)` |
| 分页查询 | `doFindPage(query, selection, context)` |
| 总数 | `findCount(query, context)` |
| 第一条 | `findFirst(query, selection, context)` |

### 3. 写操作

| 场景 | 优先方法 |
|------|---------|
| 前端 Map 数据新建 | `save(data, context)` |
| 前端 Map 数据更新 | `update(data, context)` |
| 已拿到实体后更新 | `updateEntity(entity, action, context)` |
| 已拿到实体后删除 | `deleteEntity(entity, action, context)` |
| 按 id 删除 | `delete(id, context)` |

### 4. 事务后回调

```java
txn().afterCommit(null, () -> {
    sendNotification(order);
});
```

---

## 二、常用查询构造

### QueryBean

```java
QueryBean query = new QueryBean();
query.addFilter(FilterBeans.eq("status", 1));
query.setLimit(20);
```

### FilterBeans

```java
FilterBeans.eq("status", 1);
FilterBeans.in("id", ids);
FilterBeans.and(filter1, filter2);
FilterBeans.or(filter1, filter2);
FilterBeans.contains("name", keyword);
```

---

## 三、BizModel 常用注解

```java
@BizModel("Order")
@BizQuery
@BizMutation
@RequestBean
@DataBean
@Name("orderId")
```

IoC / 配置相关：

```java
@Inject
@InjectValue("@cfg:app.value")
```

注意：`@Inject` 不支持 `private` 字段。

---

## 四、普通 BizModel 中默认不要这样写

| 不推荐 | 推荐 |
|--------|------|
| `dao().getEntityById(id)` | `requireEntity(id, action, context)` |
| `dao().findAllByQuery(query)` | `doFindList(query, selection, context)` |
| `dao().findPageByQuery(query)` | `doFindPage(query, selection, context)` |
| `dao().saveEntity(entity)` | `save(data, context)` 或 `saveEntity(entity, action, context)` |
| `dao().updateEntity(entity)` | `updateEntity(entity, action, context)` |
| `dao().deleteEntity(entity)` | `deleteEntity(entity, action, context)` |

原因：`CrudBizModel` 会统一处理数据权限、逻辑删除、对象元数据过滤、回调和默认行为。

---

## 五、什么时候可以直接使用 DAO

以下场景可以使用原始 DAO，但应明确说明自己处于边界层：

1. store / infra 层
2. 显式 `REQUIRES_NEW` 事务
3. 版本锁、调度、底层批量操作
4. 框架内部能力

代表性模式：`io.nop.job.dao.store.JobScheduleStoreImpl`

这个类会在 store 层中组合：

- `daoFor(...)`
- `saveEntityDirectly(...)`
- `updateEntityDirectly(...)`
- `@Transactional(propagation = REQUIRES_NEW)`

这类写法**不是普通 BizModel 模板**。

---

## 六、最小示例

```java
@BizModel("Order")
public class OrderBizModel extends CrudBizModel<Order> {

    public OrderBizModel() {
        setEntityName(Order.class.getName());
    }

    @BizQuery
    public List<Order> getOrdersByUser(@Name("userId") String userId,
                                       FieldSelectionBean selection,
                                       IServiceContext context) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("userId", userId));
        return doFindList(query, selection, context);
    }

    @BizMutation
    public Order cancel(@Name("orderId") String orderId, IServiceContext context) {
        Order order = requireEntity(orderId, "cancel", context);
        order.setStatus(OrderConstants.CANCELLED);
        updateEntity(order, "cancel", context);
        return order;
    }
}
```

---

## 七、相关文档

- `../12-tasks/write-bizmodel-method.md`
- `../03-development-guide/bizmodel-guide.md`
- `../03-development-guide/crud-development.md`
- `../04-core-components/ioc-container.md`
- `../13-reference/source-anchors.md`
