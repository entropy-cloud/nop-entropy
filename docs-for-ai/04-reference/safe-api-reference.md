# 安全 API 速查

本页只收录普通 BizModel / `CrudBizModel` 场景下应优先使用的 API。

## 获取实体

| 场景 | 优先方法 |
|------|---------|
| 不存在直接抛错 | `requireEntity(id, action, context)` |
| 可返回 `null` | `get(id, ignoreUnknown, context)` |
| 批量获取 | `batchGet(ids, ignoreUnknown, context)` |

## 组合式回调参数（`do*` 方法的设计模式）

CrudBizModel 的所有内部管道方法（`doFindList`、`doSave`、`doUpdate`、`doDelete` 等）都有一个 `prepare*` 回调参数，这是平台的核心扩展机制：**通过组合而非继承来注入自定义逻辑**。

| 回调参数 | 类型 | 所在方法 | 用途 |
|------|------|------|------|
| `prepareQuery` | `BiConsumer<QueryBean, IServiceContext>` | `doFindList`, `doFindPage`, `updateByQuery`, `deleteByQuery` | 注入默认过滤条件、排序规则、查询预处理 |
| `prepareSave` | `BiConsumer<EntityData<T>, IServiceContext>` | `doSave`, `copyForNew` | 保存前补字段、校验、初始化状态 |
| `prepareUpdate` | `BiConsumer<EntityData<T>, IServiceContext>` | `doUpdate`, `updateByQuery` | 更新前补字段、校验 |
| `prepareDelete` | `BiConsumer<T, IServiceContext>` | `doDelete`, `deleteByQuery` | 删除前校验、引用检查 |

**如何使用：**

- 传 `this::invokeDefaultPrepareQuery` — 走子类 override 的 `defaultPrepareQuery` 逻辑（继承式扩展）
- 传 lambda — 内联组合式扩展，只影响当前调用
- 传 `null` — 跳过预处理，直接执行

```java
// 组合式：在单次调用中注入自定义过滤
doFindList(query, (q, ctx) -> {
    q.addFilter(FilterBeans.eq("userId", ctx.getUserId()));
}, null, context);

// 继承式：子类 override defaultPrepareQuery，所有调用自动生效
// （见 extend-crud-with-hooks.md）
```

> **不要因为回调参数多就直接退回 `dao().findAllByQuery()`**。`do*` 方法包含了权限检查、逻辑删除、预处理等管道逻辑，绕过它们会丢失平台保障。

## 查询

| 场景 | 优先方法 |
|------|---------|
| 列表查询 | `doFindList(query, this::invokeDefaultPrepareQuery, selection, context)` |
| 分页查询 | `doFindPage(query, this::invokeDefaultPrepareQuery, selection, context)` |
| 总数 | `findCount(query, context)` |
| 第一条 | `findFirst(query, selection, context)` |

> **`doFindList` 签名是 4 参数**：`(query, prepareQuery, selection, context)`。`prepareQuery` 的用法见上方"组合式回调参数"一节。

## 写操作

| 场景 | 优先方法 |
|------|---------|
| 前端 Map 数据新建 | `save(data, context)` |
| 前端 Map 数据更新 | `update(data, context)` |
| **程序化创建新实体**（已持有实体对象） | **`saveEntity(entity, action, context)`** |
| 已拿到实体后更新 | `updateEntity(entity, action, context)` |
| 已拿到实体后删除 | `deleteEntity(entity, action, context)` |
| 按 id 删除 | `delete(id, context)` |

> **`save` vs `saveEntity` 区别**：
> - `save(Map, context)` 接收前端传入的 `Map<String, Object>`，经 xmeta 校验和 `OrmEntityCopier` 拷贝到实体后持久化。适用于 GraphQL/REST 前端请求。
> - `saveEntity(entity, action, context)` 直接持久化已构造好的实体对象，含权限检查、唯一性校验、afterEntityChange 触发。适用于 BizModel 内部程序化创建实体（如从购物车行创建订单）。

## 事务后回调

```java
txn().afterCommit(null, () -> {
    sendNotification(order);
});
```

前提：当前已经处于事务中。普通 `@BizMutation` 默认满足；query 默认不满足。

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
| `dao().findAllByQuery(query)` | `doFindList(query, this::invokeDefaultPrepareQuery, selection, context)` |
| `dao().findPageByQuery(query)` | `doFindPage(query, this::invokeDefaultPrepareQuery, selection, context)` |
| `dao().saveEntity(entity)` | `saveEntity(entity, action, context)`（程序化创建）或 `save(data, context)`（前端 Map） |
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
