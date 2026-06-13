# 安全 API 速查

本页只收录普通 BizModel / `CrudBizModel` 场景下应优先使用的 API。

## 常用类型全限定名

文档中使用简写，实际 import 时注意区分包名：

| 简写 | 全限定名 |
|------|---------|
| `PageBean` | `io.nop.api.core.beans.PageBean` |
| `QueryBean` | `io.nop.api.core.beans.query.QueryBean` |
| `FieldSelectionBean` | `io.nop.api.core.beans.FieldSelectionBean` |
| `IServiceContext` | `io.nop.core.context.IServiceContext` |
| `FilterBeans` | `io.nop.api.core.beans.FilterBeans` |
| `ApiRequest` | `io.nop.api.core.beans.ApiRequest` |
| `ApiResponse` | `io.nop.api.core.beans.ApiResponse` |

> **注意：`PageBean` 在 `io.nop.api.core.beans` 包，与 `QueryBean`（在 `.query` 子包）不同。不要写成 `io.nop.api.core.beans.query.PageBean`。**

## 跨实体访问：业务代码 vs 底层代码

BizModel 中访问**非自身实体**时，根据代码的层级选择路径：

### 业务代码（绝大多数场景）

业务 BizModel 中的常规读写**需要走权限和 Meta 管道**，因此默认通过 I*Biz：

```
1. 已持有实体的关联对象？
   → 是：ORM 关系 getter（如 cart.getGoods()）          ← 最直接

2. 按ID获取 / 查询其他实体
   → 注入 I*Biz 接口，调用 get() / findList() 等         ← 走权限管道

3. 需要原子 SQL（如库存扣减 WHERE number >= ${num}）
   → @SqlLibMapper，注释说明为什么需要绕过管道
```

```java
@Inject
ILitemallGoodsProductBiz goodsProductBiz;

// 业务代码：走权限管道
LitemallGoodsProduct product = goodsProductBiz.get(productId, false, context);
LitemallGoodsProduct product = goodsProductBiz.requireEntity(productId, null, context);

List<LitemallGoodsProduct> list = goodsProductBiz.findList(query, null, context);
```

### 底层代码（store / infra / 框架内部 / 批量操作）

底层代码**有意跳过权限和 Meta 管道**，直接操作 DAO：

```java
// 底层代码：有意绕过权限，直接操作
LitemallGoodsProduct product = daoProvider().daoFor(LitemallGoodsProduct.class)
        .requireEntityById(productId);
```

**不要在业务 BizModel 中混用底层写法。** 业务代码里用 `daoProvider().daoFor()` 省事，等于静默跳过了数据权限和 Meta 过滤，后续排查问题很难发现。

### @SqlLibMapper

用于需要原子 SQL 的场景（如 `WHERE number >= ${num}` 的库存扣减），这既不是业务代码的默认选择，也不是底层代码的特权，而是独立的能力，按需使用即可。

## 获取实体

### 自身实体（CrudBizModel<T> 内置方法）

| 场景 | 优先方法 |
|------|---------|
| 不存在直接抛错 | `requireEntity(id, actionName, context)` |
| 可返回 `null` | `get(id, ignoreUnknown, context)` |
| 批量获取 | `batchGet(ids, ignoreUnknown, context)` |

### 其他实体

| 场景 | 优先方法 |
|------|---------|
| 已持有实体，读取其关联实体 | ORM 关系 getter（如 `cart.getGoods()`） |
| 按ID获取其他实体 | 注入 `I*Biz`，调用 `get()` / `requireEntity()` |
| 查询其他实体 | 注入 `I*Biz`，调用 `findList()` / `findPage()` |

> **`requireEntity()`/`get()` vs 关系 getter**：`requireEntity()` 和 `get()` 内部调用 `checkDataAuth` + `checkMetaFilter`，会校验当前用户对该实体的数据访问权限。如果只需要读取已加载实体上的关联数据（内部业务逻辑），直接用关系 getter 即可，无需额外权限检查。仅当需要校验访问权限时（如前端传入 id 获取实体）才走 `requireEntity()`/`get()`。

> **`I*Biz.get()` vs `daoProvider().daoFor()`**：`I*Biz.get()` 走完整管道（权限、Meta、逻辑删除）。`daoProvider().daoFor()` 绕过全部管道。在 BizModel 中，默认永远用 `I*Biz`。只有在 `I*Biz` 确实无法满足需求时才降级到 `daoProvider()`，且必须写注释说明原因。

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
| 列表查询（需要定制 prepareQuery） | `doFindList(query, prepareQuery, selection, context)` |
| 列表查询（不需要定制 prepareQuery） | `findList(query, selection, context)` |
| 分页查询（需要定制 prepareQuery） | `doFindPage(query, prepareQuery, selection, context)` |
| 分页查询（不需要定制 prepareQuery） | `findPage(query, selection, context)` |
| 总数 | `findCount(query, context)` |
| 第一条 | `findFirst(query, selection, context)` |

> **QueryBean 会被原地修改**：以上所有方法都会直接修改传入的 `query` 对象（追加权限过滤、排序、limit 裁剪等），不做防御性拷贝。如果需要复用 query，调用前用 `query.cloneInstance()` 拷贝。详见 `service-layer.md` 查询章节。

> **`doFindList` vs `findList`**：
> - `doFindList(query, prepareQuery, selection, context)` — 4 参数，`prepareQuery` 是 `BiConsumer<QueryBean, IServiceContext>`，用于组合式注入默认过滤/排序。需要定制查询预处理时使用。
> - `findList(query, selection, context)` — 3 参数，内部自动调用 `this::invokeDefaultPrepareQuery`。不需要定制 prepareQuery 时直接用这个更简洁。实体方法内通过 `requireBiz` 获取 `I*Biz` 后也只能调用这个 3 参数版本。
> - `doFindPage` / `findPage` 同理。

## Example 查询（强类型等值匹配）

`IEntityDao` 提供一族 `byExample` 方法，以**实体对象本身**作为查询条件。与 `QueryBean`（字符串字段名 + 动态条件）不同，`byExample` 是**强类型**的——通过实体的 setter 设置匹配字段，编译器可以检查字段名和类型。

### API 列表

| 方法 | 返回 | 说明 |
|------|------|------|
| `findFirstByExample(T example)` | `T` 或 `null` | 返回第一条匹配记录 |
| `requireFirstByExample(T example)` | `T` | 返回第一条匹配，未找到抛 `ERR_DAO_MISSING_ENTITY_WITH_PROPS` |
| `findAllByExample(T example)` | `List<T>` | 返回全部匹配 |
| `findAllByExample(T example, List<OrderFieldBean> orderBy)` | `List<T>` | 带排序 |
| `findPageByExample(T example, orderBy, offset, limit)` | `List<T>` | 分页 |
| `countByExample(T example)` | `long` | 计数 |
| `deleteByExample(T example)` | `long` | 按条件删除 |

### 匹配规则

- 只有**已初始化的属性**（通过 setter 显式赋值的）才生成 `WHERE col = ?` 条件，未设置的属性被忽略。
- 所有条件之间为 **AND**，运算符固定为 **等值（=）**。
- 多租户和版本化实体的过滤条件由框架自动追加。

### 代码示例

```java
// 强类型：编译器检查字段名和类型
NopAuthUser example = dao().newEntity();
example.setOpenId(openId);
NopAuthUser user = dao().findFirstByExample(example);
```

### byExample vs QueryBean 选择

| 维度 | `byExample` | `byQuery` (QueryBean) |
|------|-------------|----------------------|
| 类型安全 | **强类型**（实体 setter） | 弱类型（字符串字段名） |
| 条件运算符 | 仅等值 `=` | `=`, `!=`, `>`, `<`, `IN`, `LIKE`, `AND/OR` 嵌套等 |
| 适用场景 | 按已知字段精确匹配（唯一键查重、按外键查关联） | 复杂条件、范围查询、模糊搜索 |
| 排序 | 通过 `OrderFieldBean` 参数 | 通过 `QueryBean.addOrder` |
| 分页 | `findPageByExample` | `findPageByQuery` / `findPage` |

**规则：** 简单等值匹配优先 `byExample`（类型安全、代码简洁）；需要非等值条件、OR 组合或动态过滤时用 `QueryBean`。

> `byExample` 方法是 `IEntityDao` 层 API，不走 `CrudBizModel` 的权限/Meta 管道。在 BizModel 中使用时，如需数据权限过滤，应改用 `findList(query, selection, context)` 等 CrudBizModel 方法。CrudBizModel 内部的唯一性校验（`checkUniqueForSaveEntity`）自身使用了 `findFirstByExample`，这是平台内部的正确用法。

## 写操作

| 场景 | 优先方法 |
|------|---------|
| **创建实体实例** | **`newEntity()`**（不要 `new Order()`） |
| 前端 Map 数据新建 | `save(data, context)` |
| 前端 Map 数据更新 | `update(data, context)` |
| **程序化创建新实体**（已持有实体对象） | **`saveEntity(entity, actionName, context)`** |
| 已拿到实体后更新 | `updateEntity(entity, actionName, context)` |
| 已拿到实体后删除 | `deleteEntity(entity, actionName, context)` |
| 按 id 删除 | `delete(id, context)` |

> **必须用 `newEntity()` 而不是 `new Order()`**：实体可能被 Delta 机制扩展为派生类。`newEntity()` 通过 DAO 创建实例，确保返回正确的派生类。直接 `new` 会丢失 Delta 增强的字段和行为。

> **`save` vs `saveEntity` 区别**：
> - `save(Map, context)` 接收前端传入的 `Map<String, Object>`，经 xmeta 校验和 `OrmEntityCopier` 拷贝到实体后持久化。适用于 GraphQL/REST 前端请求。
> - `saveEntity(entity, actionName, context)` 直接持久化已构造好的实体对象，含权限检查、唯一性校验、afterEntityChange 触发。适用于 BizModel 内部程序化创建实体（如从购物车行创建订单）。<br/>
> 注意：`actionName` 是 `String` 类型，平台内置方法默认为 `"save"`/`"update"`/`"delete"`。传 `null` 即可使用默认值，不要传 `boolean`。

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

## 反模式速查

| 不要这样写 | 应该这样写 |
|--------|------|
| `dao().getEntityById(id)` | `requireEntity(id, actionName, context)` |
| `dao().findAllByQuery(query)` | `findList(query, selection, context)` 或 `doFindList(query, prepareQuery, selection, context)` |
| `dao().findPageByQuery(query)` | `findPage(query, selection, context)` 或 `doFindPage(query, prepareQuery, selection, context)` |
| `dao().saveEntity(entity)` | `saveEntity(entity, actionName, context)`（程序化创建）或 `save(data, context)`（前端 Map） |
| `daoProvider().daoFor(Xxx.class)` 获取其他实体 | 业务代码：注入 `I*Biz`，用 `get()` / `requireEntity()` / `findList()`。底层代码可以直接用 `daoProvider()` |
| `@BizMutation @Transactional` | 只用 `@BizMutation` |
| `I*Biz.get()` 获取已有 ORM 关系的关联实体 | 用关系 getter（如 `cart.getGoods()`）。`I*Biz.get()` 会触发 `checkDataAuth` 权限检查，仅当需要校验访问权限时才使用 |

## 相关文档

- `../02-core-guides/service-layer.md`
- `./source-anchors.md`
