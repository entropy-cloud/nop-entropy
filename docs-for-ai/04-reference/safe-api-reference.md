# 安全 API 速查

本页只收录普通 BizModel / `CrudBizModel` 场景下应优先使用的 API。

## 获取实体

| 场景 | 优先方法 |
|------|---------|
| 不存在直接抛错 | `requireEntity(id, action, context)` |
| 可返回 `null` | `get(id, ignoreUnknown, context)` |
| 批量获取 | `batchGet(ids, ignoreUnknown, context)` |

## 查询

| 场景 | 优先方法 |
|------|---------|
| 列表查询 | `doFindList(query, selection, context)` |
| 分页查询 | `doFindPage(query, selection, context)` |
| 总数 | `findCount(query, context)` |
| 第一条 | `findFirst(query, selection, context)` |

## 写操作

| 场景 | 优先方法 |
|------|---------|
| 前端 Map 数据新建 | `save(data, context)` |
| 前端 Map 数据更新 | `update(data, context)` |
| 已拿到实体后更新 | `updateEntity(entity, action, context)` |
| 已拿到实体后删除 | `deleteEntity(entity, action, context)` |
| 按 id 删除 | `delete(id, context)` |

## 事务后回调

```java
txn().afterCommit(null, () -> {
    sendNotification(order);
});
```

## 查询构造

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

## 常用注解

```java
@BizModel
@BizQuery
@BizMutation
@RequestBean
@DataBean
@Name("orderId")
@Inject
@InjectValue("@cfg:app.value")
```

## 普通 BizModel 中默认不要这样写

| 不推荐 | 推荐 |
|--------|------|
| `dao().getEntityById(id)` | `requireEntity(id, action, context)` |
| `dao().findAllByQuery(query)` | `doFindList(query, selection, context)` |
| `dao().findPageByQuery(query)` | `doFindPage(query, selection, context)` |
| `dao().saveEntity(entity)` | `save(data, context)` 或 `updateEntity(...)` |
| `@BizMutation @Transactional` | 只用 `@BizMutation` |

## 什么时候才可以直接使用 DAO

以下属于边界层：

1. store / infra 层
2. 显式 `REQUIRES_NEW`
3. 版本锁、调度、底层批量操作
4. 框架内部能力

## 相关文档

- `../02-core-guides/service-layer.md`
- `./source-anchors.md`
