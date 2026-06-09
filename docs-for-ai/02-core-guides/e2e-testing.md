# E2E 测试默认模式

> **受众**：基于 Nop 平台构建业务应用的开发者和 AI。本页描述基于 Playwright 的 E2E 测试模式，适用于验证 RPC API 和 AMIS 前端页面。

## 何时写 E2E 测试

1. 需要验证 RPC API 端到端正确性（跨 HTTP 层）
2. 需要验证 AMIS 前端页面的交互流程（登录、CRUD、搜索）
3. 进程内集成测试不足以覆盖的场景（如前端路由、弹窗、表单提交）

## Nop RPC 调用模式

E2E 测试通过 `POST /r/{EntityName}__{action}` 调用后端：

```typescript
// 1. 登录获取 token
const resp = await request.post('/r/LoginApi__login', {
  data: { principalId: 'nop', principalSecret: '123', loginType: 1 },
});
const { accessToken } = await resp.json();

// 2. 带 token 调用业务 API
const result = await request.post('/r/NopAuthUser__findPage', {
  headers: { Authorization: `Bearer ${accessToken}` },
  data: { query: { offset: 0, limit: 10 } },
});
const json = await result.json();
// json.status === 0 表示成功, json.data 是返回数据
```

## 标准 CRUD 操作

| 操作 | 参数 | 说明 |
|------|------|------|
| `Entity__get` | `{ id }` | 按 ID 查询 |
| `Entity__findPage` | `{ query: { offset, limit, filter? } }` | 分页查询 |
| `Entity__save` | `{ data: { ...fields } }` | 新增（无 id）或全量更新（含 id） |
| `Entity__update` | `{ data: { id, ...fields } }` | 部分更新 |
| `Entity__delete` | `{ id }` | 按 ID 删除 |

## 浏览器 E2E 测试要点

1. **登录**：SPA 前端需要登录才能访问页面（`nop`/`123`）
2. **页面 URL**：`/#/type-hierarchy-main`（不是 `/#/page?pagePath=...`），URL 格式为 `/#/{pageId}`
3. **API 路由**：AMIS 表单 `@query:` API 走 `/graphql`（POST），RPC 测试走 `/r/{operation}`
4. **字段名**：`editMode="query"` 自动加 `filter_` 前缀，`editMode="edit"` 不加前缀
5. **无 meta 表单**：必须在 view.xml `<cells>` 中配置 `domain` 和 `label`，否则字段渲染为 `static` 且无标签
6. **BizModel 命名**：方法名不得与标准 CRUD（`get`/`findPage`/`save`/`update`/`delete` 等）重名，否则前端 `@query:` API 参数被忽略

## 在业务项目中搭建 E2E 测试

创建标准 Playwright 项目，按上述 RPC 调用模式编写测试即可。后端 URL 格式与上面描述一致（`/r/{EntityName}__{action}`）。

## 平台内部参考

平台自身在 `nop-entropy-e2e/` 中有完整的参考实现（共享库 API、RPC helpers、Page Objects、AMIS selectors）。详见 `nop-entropy-e2e/README.md`。**注意**：该文档面向平台开发者，路径和模块结构是 nop-entropy 仓库特有的。

## 相关文档

- `../00-required-reading-e2e-testing.md`
- `testing.md`（单元/集成测试）
