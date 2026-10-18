# EQL 在线编辑与测试工具设计

> 本目录按 AGE（Attractor-Guided Engineering）owner-doc 模式组织：Vision 与 Architecture Baseline 为必备层，其余按实际需要分层。
> 本目录为规范性文档：只描述当前有效设计；变更历史见 git 与 AI 开发记录区的时效性文档；不记录 plan/WI/审计轮次。

## 1. 子系统定位

为 EQL（Nop 实体查询语言）提供 Web 在线编辑与测试能力：实体/属性树、可配置补全、语法与语义校验、格式化、执行、结果表格/分页/导出、历史与多标签。交互形态对标在线版 DBeaver，但范围收窄为「EQL + Nop 数据源」而非通用多数据库客户端。

本设计覆盖三个仓库的协作边界：

- `nop-chaos-flux`：字段级 `code-editor` 的方言化与执行/校验/结果协议（通用能力，不含 EQL 字面量分支）。
- `nop-chaos-next`：宿主壳层与 flux bundle 打包/交付。
- `nop-entropy`：`nop-eql-console` 后端模块、工作台页面、菜单权限。

## 2. 文档结构与阅读顺序

| 顺序 | 文档 | 层级 | 职责 |
|---|---|---|---|
| 1 | [`00-vision.md`](00-vision.md) | Vision | 产品定位、使用场景、成功标准、不可违反约束、Non-goals、需人工裁定的决策点、收敛路径 |
| 2 | [`01-architecture-baseline.md`](01-architecture-baseline.md) | Architecture Baseline | 分层与依赖方向、核心对象职责契约、关键决策 D1—D7、单一事实源、兼容性约束 |
| 3 | [`02-frontend-dialect-and-editor-contract.md`](02-frontend-dialect-and-editor-contract.md) | 前端契约 | Dialect Profile、EntitySchema、entity-path 补全、格式化、校验、执行与结果协议、页面级结果区 |
| 4 | [`03-eql-console-api-and-security.md`](03-eql-console-api-and-security.md) | 后端契约 | `nop-eql-console` 模块分层、BizModel API、诊断与错误模型、安全门控矩阵、配置项、权限点 |
| 5 | [`04-workbench-page-and-delivery.md`](04-workbench-page-and-delivery.md) | 交付契约 | 工作台页面结构、页面/菜单资源、数据流、三仓交付链路与验收契约 |

扩展方向：多数据源切换、查询历史持久化、EXPLAIN 可视化、NL2EQL 不在当前基线内，见 `00-vision.md` 的 Non-goals 与 Phase 边界。

## 3. 文档职责边界

| 文档 | 回答 | 不回答 |
|---|---|---|
| `00-vision.md` | 为什么做、做到什么程度、什么不做、哪些必须由人定 | 具体接口字段、页面布局 |
| `01-architecture-baseline.md` | 系统如何分层、谁依赖谁、核心对象是什么、为什么这样选 | 单个字段级契约细节 |
| `02-frontend-dialect-and-editor-contract.md` | 编辑器侧配置与运行时契约（前端可感知的命名/语义/兼容规则） | 后端实现、页面壳层 |
| `03-eql-console-api-and-security.md` | 后端 API、错误模型、安全边界、配置项 | 前端如何渲染、页面菜单 |
| `04-workbench-page-and-delivery.md` | 页面/菜单/三仓交付与验收 | 协议字段定义（引用 02/03） |

## 4. 与代码的关系

设计文档表达架构决策与使用契约；类签名、方法实现、内部数据结构以源码为唯一事实。文档中的接口名、端点名、配置键名、权限点名、诊断字段名属于**使用契约**，修改这些命名必须先改本文档。

## 5. 相关规范

- 平台使用规范：`docs-for-ai/02-core-guides/eql-and-database-compatibility.md`、`docs-for-ai/02-core-guides/api-and-graphql.md`、`docs-for-ai/02-core-guides/service-layer.md`、`docs-for-ai/02-core-guides/error-handling.md`
- 前端渲染契约：`docs-for-ai/02-core-guides/flux-rendering.md`
- 代码编辑器的既有设计契约：`nop-chaos-flux:docs/components/code-editor/design.md`
