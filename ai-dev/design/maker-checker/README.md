# maker-checker 设计目录

本目录按 AGE（Attractor-Guided Engineering）owner-doc 模式组织，承载 Nop 平台通用 Maker-Checker（四眼原则/制单-复核）机制的设计决策与使用契约。

## 文档结构与阅读顺序

| 文档 | 层级 | 职责 | 何时读 |
|------|------|------|--------|
| `00-vision.md` | Vision | 目标、non-goals、成功标准、硬约束、人必须拍板的决策 | 必读入口 |
| `01-architecture-baseline.md` | Architecture Baseline | 分层、状态机、数据模型变更、配置模型、API 面、模块边界 | 必读 |
| `02-snapshot-nested-data.md` | 专题 | 快照归一化、复杂嵌套数据投影/合并、递归 diff 引擎 | 涉及快照与 diff 时 |
| `03-pending-lock-and-concurrency.md` | 专题 | 待审互斥锁、防重复提交/防修改、撤回/过期、staleness 双保险 | 涉及并发与锁时 |
| `04-review-ui-diff-contract.md` | 专题 | 审核页面使用契约：GraphQL API、DiffTree 结构、前端渲染约定 | 做审核前端时 |

## 职责边界

- 本目录回答"机制如何设计、为什么这样设计"；实现细节看源码锚点（各文档中给出）。
- 现状基线：平台已内置 maker 侧骨架（`io.nop.biz.makerchecker`、GraphQL tryAction、`nop_sys_checker_record` 表），本设计定义补全 checker 侧的目标架构。落地前 ORM 结构变更需按 protected-area 规则走 plan-first。

## 关联

- 运行时审批流（多级会签/条件路由）归 `nop-wf/` 设计域；本机制的 L1↔L2 桥接契约在 `01-architecture-baseline.md` §7。
