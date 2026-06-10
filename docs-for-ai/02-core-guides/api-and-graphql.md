# GraphQL 与 API 默认模式

当前仓库中，GraphQL / API 层默认建立在 BizModel 之上。

## 默认结论

1. 普通 API 能力优先通过 BizModel 暴露。
2. 查询使用 `@BizQuery`，修改使用 `@BizMutation`。
3. 不要把旧式直接 `dao()` 示例当作当前 GraphQL 默认模板。
4. 返回复杂结构时优先 `@DataBean` DTO，而不是 `Map<String, Object>`。
5. 扩展返回字段时优先 `@BizLoader`；字段不存在时优先 Delta + `@BizLoader(autoCreateField = true)` + `@LazyLoad`。
6. **Nop 平台内置 BizModel 对象名以 `Nop` 为前缀**（如 `NopAuthUser`、`NopCodeIndex`），用于与用户业务对象区分，避免 GraphQL 类型名冲突。用户自定义 BizModel 不需要 `Nop` 前缀。

## 最小理解模型

```text
BizModel 方法
  -> 框架暴露 GraphQL / RPC 能力
  -> 选择字段、错误转换、事务边界由框架处理
```

## BizModel REST Adapter Contract

对前端或外部调用来说，当前仓库里最稳定的通用 REST adapter 入口是：

1. `/r/{operationName}`
2. `/p/{query}`

其中 `operationName` 在 BizModel 场景下通常是：

`{bizObj}__{method}`

默认判断：

1. 通用 adapter 下，`@BizQuery` 可通过 `GET /r/{operationName}` 或 `POST /r/{operationName}` 调用。
2. 通用 adapter 下，`@BizMutation` 走 `POST /r/{operationName}`。
3. `POST` 默认使用 JSON body。
4. **RPC JSON body 格式：参数直接平铺，不包裹 `data` 字段。**
   ```bash
   # 正确
   curl -X POST /r/NopAuthUser__findPage -d '{"query":{"userName_like":"admin"},"offset":0,"limit":10}'
   # 错误（不要包裹 data）
   curl -X POST /r/NopAuthUser__findPage -d '{"data":{"query":{...}}}'
   ```
5. `GET` 场景会通过 `@args` 和普通 query 参数做特殊处理。
6. `/p/PageProvider__getPage?path=xxx.page.yaml` — 获取页面 AMIS JSON，返回前端渲染所需的完整 schema。

补充说明：仓库里的生成型 typed API 目前是明显偏 `POST` 风格的，即使是 `@BizQuery` 生成接口也常见 `POST`，不要把通用 adapter 行为和 typed API 代码生成结果混为一谈。

## 普通 API 方法的默认写法

### 查询

- `@BizQuery`
- `QueryBean`
- `FieldSelectionBean`
- `IServiceContext`

### 修改

- `@BizMutation`
- `requireEntity()` / `save()` / `updateEntity()`
- 必要时 `txn().afterCommit(...)`

注意：`txn().afterCommit(...)` 依赖当前已有事务。普通 `@BizMutation` 默认有事务；query 场景不要把它当作无条件可用模板。

## 返回值建议

| 场景 | 推荐 |
|------|------|
| 返回实体本身 | 直接返回实体 |
| 返回多个业务字段 | `@DataBean` DTO |
| 返回集合或分页 | `List<T>` / `PageBean<T>`（`io.nop.api.core.beans.PageBean`）/ 框架默认分页结果 |

## 扩展返回字段的默认做法

1. 给已有字段补加载逻辑：`@BizLoader`。
2. 给已有 API 增加新字段但不改基础代码：Delta + `@BizLoader(autoCreateField = true)`。
3. 昂贵字段默认配合 `@LazyLoad`，只在 selection 请求时计算。

## GraphQL relation 查询元数据

对于关联字段，XMeta / prop meta 还可以提供这些查询元数据：

1. `graphql:filter`
2. `graphql:orderBy`

它们会在 GraphQL ORM fetcher 中和运行时 query 一起合并，用来给 relation / connection 查询补默认过滤与排序。

## 当前仓库里应避免的旧模板

1. `dao().getEntityById()` 作为 GraphQL 查询默认样例。
2. `@BizMutation @Transactional`。
3. `@Inject private`。
4. 把底层 DAO / infra 的实现方式直接提升为 API 层模板。

## `@query:` AMIS API URL 机制

view.xml 中 `<api url="@query:BizObjName__actionName?param=$param"/>` 的处理流程：

1. **后端**（`WebPageHelper.fixPage`）：仅转义空格/换行，`@query:` 原样输出到 AMIS JSON
2. **前端**（`nop-chaos/packages/nop-core/src/core/graphql.ts`）：
   - 解析 URL，取最后一个 `_` 后的方法名后缀作为 `stdAction`
   - `stdAction` 匹配 `operationRegistry` → 使用预定义参数签名和参数构建器
   - `stdAction` 不匹配 → 调用 `guessDefinition(data)` 从表单数据自动推断参数类型
3. **生成 GraphQL 查询**：POST 到 `/graphql`，参数从表单数据映射

### 前端 `operationRegistry` 标准动作签名

`stdAction` 匹配到以下动作名时，前端**强制使用预定义参数**，URL 中的自定义参数被忽略：

| stdAction | 预定义参数 | 参数构建器语义 | 对应 BizModel 方法 |
|-----------|-----------|--------------|-------------------|
| `get` | `id: String`, `ignoreUnknown: Boolean` | 按字段名从 data 取值，转 String/Boolean | `get(id)` — 按 ID 获取单条 |
| `findPage` | `query: QueryBeanInput` | 从 data 中收集 `filter_` 前缀字段构造查询条件 + `pageSize`/`offset`/`orderBy` | `findPage(query)` — 分页查询 |
| `findList` | `query: QueryBeanInput` | 同 findPage 但不分页 | `findList(query)` — 列表查询 |
| `findFirst` | `query: QueryBeanInput` | 同 findPage，取第一条 | `findFirst(query)` — 取首条 |
| `save` | `data: Map` | 从 data 中提取所有非 `__`/`@`/`_` 前缀字段 | `save(data)` — 新增 |
| `update` | `data: Map` | 同 save | `update(data)` — 修改 |
| `saveOrUpdate` | `data: Map` | 同 save | `saveOrUpdate(data)` — 新增或修改 |
| `upsert` | `data: Map` | 同 save | `upsert(data)` — 存在则更新 |
| `copyForNew` | `data: Map` | 同 save | `copyForNew(data)` — 复制新增 |
| `delete` | `id: String` | 按字段名取值转 String | `delete(id)` — 按 ID 删除 |
| `batchGet` | `ids: [String]` | 按字段名取值，字符串按逗号分割 | `batchGet(ids)` — 批量获取 |
| `batchDelete` | `ids: [String]` | 同 batchGet | `batchDelete(ids)` — 批量删除 |
| `batchModify` | `data: [Map]`, `delIds: [String]` | 批量数据 + 删除 ID 列表 | `batchModify(data, delIds)` — 批量修改 |

说明：这里描述的是前端 `operationRegistry` 对 BizModel 标准动作的运行时参数形状，因此仍以 `Map`/`[Map]` 表示通用 adapter 载荷；它不等同于 `*-api` 中 `ICrudApi<I, O>` 的客户端强类型契约。生成的 CRUD typed API 可将 `save/update/saveOrUpdate/copyForNew/batchModify` 等实体输入统一表达为 `I` 或 `List<I>`。

### 非标准动作的参数推断

当 `stdAction` 不在 `operationRegistry` 中时，前端 `guessDefinition(data)` 逐字段推断：
- `String` 值 → `String` 类型
- 整数 → `Int` 类型
- 浮点数 → `Float` 类型
- 布尔 → `Boolean` 类型
- 对象 → `Map` 类型
- 数组 → `[String]` 类型

额外以 `v_` 为前缀的字段（`guessExtArgDefinitions`）会追加到标准动作的参数列表中，实现向标准动作传递自定义参数。

### 命名规则

**自定义 BizModel 的 `@BizQuery`/`@BizMutation` 方法名不得与上表中的标准动作名重名。** `GraphQLBizModel` 在注册时会检查同名方法，同优先级下直接抛 `ERR_GRAPHQL_DUPLICATE_ACTION`。不同优先级时高优先级覆盖低优先级（delta 机制依赖此行为）。自定义方法用不同的名字，如 `getById`、`saveOrder`。

## 相关文档

- `./service-layer.md`
- `./error-handling.md`
- `../03-runbooks/add-bizloader-field.md`
- `../03-runbooks/extend-api-with-delta-bizloader.md`
- `../04-reference/safe-api-reference.md`
