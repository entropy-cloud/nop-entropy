# CRUD 开发指南

本文档说明如何在 Nop 平台上使用 `CrudBizModel` 完成标准 CRUD，以及何时通过扩展点补充逻辑。

它不重复定义 BizModel 的全部规则；BizModel 通用规范以 `bizmodel-guide.md` 为准。

---

## 一、默认模式

```java
@BizModel("Order")
public class OrderBizModel extends CrudBizModel<Order> {
    public OrderBizModel() {
        setEntityName(Order.class.getName());
    }
}
```

只要模型和元数据完整，很多 CRUD 场景无需再写 Java 代码。

---

## 二、优先使用的能力

| 场景 | 默认能力 |
|------|---------|
| 分页查询 | `findPage()` / `doFindPage()` |
| 列表查询 | `findList()` / `doFindList()` |
| 获取实体 | `get()` / `requireEntity()` |
| 新建 | `save()` |
| 更新 | `update()` / `updateEntity()` |
| 删除 | `delete()` / `deleteEntity()` |

不要把原始 DAO 调用作为 `CrudBizModel` 派生类的默认模板。

---

## 三、通过扩展点补逻辑

### 保存前

```java
@Override
protected void defaultPrepareSave(EntityData<Order> entityData, IServiceContext context) {
    super.defaultPrepareSave(entityData, context);
    Order order = entityData.getEntity();
    order.setSource("online");
}
```

### 查询前

```java
@Override
protected void defaultPrepareQuery(QueryBean query, IServiceContext context) {
    super.defaultPrepareQuery(query, context);
    query.addFilter(FilterBeans.eq("status", 1));
}
```

### 删除前

```java
@Override
protected void defaultPrepareDelete(Order entity, IServiceContext context) {
    super.defaultPrepareDelete(entity, context);
    if (!entity.canDelete()) {
        throw new NopException(OrderErrors.ERR_ORDER_CANNOT_DELETE)
            .param(OrderErrors.ARG_ORDER_ID, entity.getOrderId());
    }
}
```

---

## 四、什么时候写自定义 CRUD 方法

适合：

1. 标准 `save/update/delete` 不足以表达业务动作
2. 需要领域动作，如 `cancel/approve/publish`
3. 需要对默认 CRUD 做组合封装

```java
@BizMutation
public Order publish(@Name("id") String id, IServiceContext context) {
    Order order = requireEntity(id, "publish", context);
    order.setPublished(true);
    updateEntity(order, "publish", context);
    return order;
}
```

---

## 五、反模式

1. 在 `CrudBizModel` 中直接 `dao().saveEntity()`
2. 在 `CrudBizModel` 中直接 `dao().findPageByQuery()`
3. 通过手工事务替代默认 `@BizMutation`
4. 把本可通过扩展点完成的逻辑全部重写成底层 CRUD 流程

---

## 六、相关文档

- `./bizmodel-guide.md`
- `../12-tasks/extend-crud-with-hooks.md`
- `../12-tasks/write-bizmodel-method.md`
