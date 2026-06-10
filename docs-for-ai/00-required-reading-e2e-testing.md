# E2E 测试必读索引

> **用途：** 本文件是 E2E 测试任务的必读文档路由入口。Plan 中 `Required Pre-Reading` 引用本文件时，agent 必须逐个打开并通读下表中列出的每一个文档，不能用"已读本索引"替代阅读子文档。

## 全局必读（写任何 E2E 测试代码之前必须全部读完）

> **本索引文件不包含任何规则内容。** 下表的"为什么必读"列只是说明用途，不是规则摘要。不读原文就会用错 Playwright 模式或遗漏 RPC 验证。
>
> **阅读顺序：** 按表格从上到下逐个打开、通读全文。读完一篇才能读下一篇。

| 文档 | 为什么必读 | 不读会怎样 |
|------|-----------|-----------|
| `02-core-guides/e2e-testing.md` | Playwright 模式、Nop RPC 调用模式、AMIS 页面测试要点、标准 CRUD 操作 | **用原生 Playwright 模式而非 Nop RPC 模式→测试脆弱且不可维护**；遗漏 AMIS 字段名映射→定位元素失败 |

## 按场景选读

> 以下文档按需阅读。判断依据：你接下来要写的代码涉及表格中描述的场景时，必须先读对应的文档再写代码。

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
