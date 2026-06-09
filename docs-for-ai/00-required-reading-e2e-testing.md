# E2E 测试必读索引

> **用途：** 计划阶段和执行阶段的 `Required Pre-Reading` 入口。E2E 测试任务只需引用本文件，执行时按实际涉及的内容项选择阅读。

## 全局必读（任何 E2E 测试任务都要读）

| 文档 | 为什么必读 |
|------|-----------|
| `02-core-guides/e2e-testing.md` | Playwright 模式、Nop RPC 调用模式、AMIS 页面测试要点、标准 CRUD 操作 |

## 按场景选读

### RPC API 测试

| 文档 | 场景 |
|------|------|
| `02-core-guides/e2e-testing.md` → Nop RPC 调用模式 | 端到端验证 GraphQL/RPC API |
| `02-core-guides/e2e-testing.md` → 标准 CRUD 操作 | `get`/`findPage`/`save`/`update`/`delete` 端到端 |

### 浏览器 UI 测试

| 文档 | 场景 |
|------|------|
| `02-core-guides/e2e-testing.md` → 浏览器 E2E 测试要点 | 登录、页面路由、AMIS 表单交互、字段名映射 |

### 平台内部参考

| 文档 | 场景 |
|------|------|
| `nop-entropy-e2e/README.md` | 平台内部共享库、Page Objects、AMIS selectors（仅平台开发者） |

> 计划阶段只引用本索引确定路径；执行阶段才实际阅读具体文档。流程由 plan guide 定义。
