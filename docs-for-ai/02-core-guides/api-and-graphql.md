# GraphQL 与 API 默认模式

当前仓库中，GraphQL / API 层默认建立在 BizModel 之上。

## 默认结论

1. 普通 API 能力优先通过 BizModel 暴露。
2. 查询使用 `@BizQuery`，修改使用 `@BizMutation`。
3. 不要把旧式直接 `dao()` 示例当作当前 GraphQL 默认模板。
4. 返回复杂结构时优先 `@DataBean` DTO，而不是 `Map<String, Object>`。
5. 扩展返回字段时优先 `@BizLoader`；字段不存在时优先 Delta + `@BizLoader(autoCreateField = true)` + `@LazyLoad`。

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
   curl -X POST /r/NopCodeTypeHierarchy__get -d '{"indexId":"test","qualifiedName":"com.example.Foo","direction":"super","maxDepth":3}'
   # 错误（不要包裹 data）
   curl -X POST /r/NopCodeTypeHierarchy__get -d '{"data":{"indexId":"test",...}}'
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
| 返回集合或分页 | `List<T>` / `PageBean<T>` / 框架默认分页结果 |

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

## 相关文档

- `./service-layer.md`
- `./error-handling.md`
- `../03-runbooks/add-bizloader-field.md`
- `../03-runbooks/extend-api-with-delta-bizloader.md`
- `../04-reference/safe-api-reference.md`
