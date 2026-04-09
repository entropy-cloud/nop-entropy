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
