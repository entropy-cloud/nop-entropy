# 数据访问分层规范

本文档不再把 `IEntityDao` 当成普通业务代码的默认入口，而是明确区分两种场景：

1. **普通 BizModel 场景**：优先 `CrudBizModel` 安全 API
2. **store / infra 场景**：才直接使用 `IDaoProvider` / `IEntityDao`

---

## 一、普通 BizModel 场景

### 默认规则

| 场景 | 推荐 |
|------|------|
| 获取实体 | `requireEntity(id, action, context)` |
| 查询列表 | `doFindList(query, selection, context)` |
| 分页查询 | `doFindPage(query, selection, context)` |
| 新建 | `save(data, context)` |
| 更新 | `update(data, context)` 或 `updateEntity(entity, action, context)` |
| 删除 | `delete(id, context)` 或 `deleteEntity(entity, action, context)` |

### 典型示例

```java
@BizModel("Order")
public class OrderBizModel extends CrudBizModel<Order> {

    @BizQuery
    public List<Order> getOrdersByUser(@Name("userId") String userId,
                                       FieldSelectionBean selection,
                                       IServiceContext context) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("userId", userId));
        return doFindList(query, selection, context);
    }
}
```

---

## 二、store / infra 场景

### 何时直接用 DAO

以下场景可以直接访问 `IEntityDao`：

1. store 层
2. 调度/锁/版本检查
3. `REQUIRES_NEW` 等显式事务边界
4. 框架底层能力

### 获取 DAO 的方式

```java
@Inject
protected IDaoProvider daoProvider;

IEntityDao<Order> orderDao = daoProvider.daoFor(Order.class);
```

### 边界示例

```java
IOrmEntityDao<Order> dao = (IOrmEntityDao<Order>) daoProvider.daoFor(Order.class);
QueryBean query = new QueryBean();
query.setLimit(20);
return dao.findAllByQuery(query);
```

这个模式常见于：`io.nop.job.dao.store.JobScheduleStoreImpl`

---

## 三、不要混淆两层 API

### 普通 BizModel 中不要优先写

```java
dao().getEntityById(id)
dao().findAllByQuery(query)
dao().findPageByQuery(query)
dao().saveEntity(entity)
dao().updateEntity(entity)
dao().deleteEntity(entity)
```

### 原因

`CrudBizModel` 会统一处理：

- 数据权限
- 逻辑删除
- 查询条件预处理
- 对象元数据约束
- 默认回调流程

---

## 四、Example / QueryBean / batchLoad 的使用边界

### QueryBean

- 普通 BizModel：构造 `QueryBean`，再交给 `doFindList()` / `doFindPage()`
- store / infra：可以直接交给 DAO

### Example

- 更适合底层 DAO 场景
- 普通 BizModel 中不作为首选教学入口

### batchLoad

- 适合处理关联属性加载
- 如果场景是 GraphQL 扩展字段，优先考虑 `@BizLoader`

---

## 五、源码锚点

- `io.nop.biz.crud.CrudBizModel`
- `io.nop.orm.biz.ICrudBiz`
- `io.nop.job.dao.store.JobScheduleStoreImpl`

## 六、相关文档

- `../09-quick-reference/api-reference.md`
- `./bizmodel-guide.md`
- `./querybean-guide.md`
- `../13-reference/source-anchors.md`
