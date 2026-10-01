# 2291 DuckDB WI9 — 文档与路由收口

> Plan Status: completed
> Last Reviewed: 2026-10-01
> Source: `ai-dev/backlog/duckdb-integration-roadmap.md` WI9；WI0-WI8 交付与 closure audit 记录；快速对抗审查 agent_d3319755（EXECUTABLE，6 建议全部吸收）
> Related: 2282-2290（全部 WI plans）

## Purpose

把 WI0-WI8 建立的 nop-duckdb 能力面沉淀为 docs-for-ai 使用文档并完成路由登记，使后续开发 AI 可路由、可正确使用模块；同步 roadmap 状态与当日 log。纯文档计划（无代码变更）。

## Current Baseline

- WI0-WI8 全部完成且各有独立 closure audit PASS；nop-duckdb 57 测全绿；roadmap 除 WI9 与里程碑外全部勾选
- docs-for-ai 当前零 nop-duckdb 内容（各 WI 审计确认）；owner doc 归属 `docs-for-ai/03-modules/`（nop-retry.md/nop-treesitter.md 先例：功能概览/核心 API/配置/约束结构）
- INDEX.md 路由先例（nop-stream/nop-treesitter 行式）；source-anchors 锚点表先例（TS-001..004 nop-treesitter 行）；module-groups.md 顶层模块组先例（nop-jq 行）
- 已沉淀的关键约束面（来自各 WI plan/log/audit）：单写者（跨进程 file-locked bizFatal、同 JVM 多连接安全）、内存预算（memory-limit/threads/temp-directory 三配置键封闭集、64MB 过紧/128MB 溢出档实测）、写边界（业务写回只走 ORM）、表生命周期=连接生命周期、CSV 桥类型漂移边界、方言 50 函数净集与 ST_* spatial 裁定、task step 输入输出契约

## Goals

- owner doc `docs-for-ai/03-modules/nop-duckdb.md`：模块定位（数据文件与本地库的分析执行层）、安装依赖、配置项（三配置键 + 语义与实测档位）、执行层 API（IDuckDbEngine/DuckDbFiles 五 API + 错误码表）、task step（输入输出/防注入/retry 语义/续跑）、硬约束（单写者/内存/写边界/表生命周期）、方言说明（50 函数净集、ST_* 裁定、锁不支持）、测试入口
- 路由三处：INDEX.md 增 nop-duckdb 行；01-repo-map/module-groups.md 登记顶层模块组；04-reference/source-anchors.md 增 DUK-00x 锚点（DuckDbEngine/DuckDbFiles/DuckDbSqlTaskStep/NopDuckDbErrors）
- `check-doc-links --strict` 退出码 0；当日 log 状态一致

## Non-Goals

- 不改任何生产代码/测试；不为 nop-duckdb 建 GraphQL/页面层文档（无该层）；不改 roadmap 历史文本（WI 状态勾选按 Cross-Cutting #8 机制进行）

## Scope

### In Scope

- 新建 `docs-for-ai/03-modules/nop-duckdb.md`
- 修改 `docs-for-ai/INDEX.md`、`docs-for-ai/01-repo-map/module-groups.md`、`docs-for-ai/04-reference/source-anchors.md`
- roadmap WI9 勾选（audit 后）与当日 log
- `node ai-dev/tools/check-doc-links.mjs --strict` 通过

### Out Of Scope

- 代码/测试变更；docs-for-ai 其他页面重构

## Execution Plan

### Phase 1 - owner doc 与路由

Status: completed
Targets: `docs-for-ai/03-modules/nop-duckdb.md`、`docs-for-ai/INDEX.md`、`docs-for-ai/01-repo-map/module-groups.md`、`docs-for-ai/04-reference/source-anchors.md`

- Item Types: `Proof`

- [x] 撰写 owner doc（内容与 live 代码逐项对照：配置键名来自 DuckDbEngine @InjectValue、错误码来自 NopDuckDbErrors、API 签名来自 DuckDbFiles/DuckDbSqlTaskStep、档位数据来自 WI4/WI7 实测）
- [x] INDEX.md 路由行（使用 nop-duckdb 场景 → owner doc）
- [x] module-groups.md 顶层模块组行（对齐 nop-jq 行文风格：定位/结构/消费方）
- [x] source-anchors DUK 锚点 4-6 条（引擎连接管理/文件面/任务步骤/错误码/方言）
- [x] `check-doc-links --strict` 退出码 0
- 路由位置（审查 S5 固化）：INDEX.md `## 快速路由` 表 nop-bytecode 行后；module-groups.md 根模块分组表内插入（实际落点 nop-lint 行后/nop-refactor 行前，一行偏移无功能影响——审计 Minor 1 备注）；source-anchors DUK 表插在 TS-004 后、列格式对齐 TS-001..004 行式
- owner doc 增补面（审查 S2/S3/S4/S6）：config fingerprint 注册表失败模式、错误码逐码 bizFatal 标注与 retry 联动、分析侧接入路径（第二数据源 + query-space-to-dialect 路由）、native 平台矩阵失败形态

Exit Criteria:

- [x] owner doc 存在且与 live 代码一致（配置键/错误码/API 名抽查全部可对上；执行修正：docs-for-ai 引用 ai-dev 路径触发 BOUNDARY 规则 2 errors——两处改按边界规则以文字指称不链接）
- [x] 三处路由可从 INDEX.md 一跳到达 owner doc；module-groups/source-anchors 各含 nop-duckdb 条目（INDEX 2 行、module-groups 1 行、source-anchors DUK-001..005）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] 当日 `ai-dev/logs/` 条目已更新 WI9 收口记录
- No new test required: 纯文档计划（guide Rule 25）

## Closure Gates

> 纯文档计划：构建验证条目按 guide 从 Closure Gates 移除（无代码变更）。

- [x] 所有 in-scope 事项完成（owner doc + 三路由 + log）
- [x] 行为/契约结果已达成：后续开发 AI 可经 INDEX 一跳路由到 nop-duckdb 使用文档，文档与 live 一致
- [x] 必要 focused verification 已完成（check-doc-links + 内容抽查）
- [x] 不存在被静默降级的 in-scope 事项
- [x] 独立子 agent closure-audit 已完成并记录证据（抽查文档与 live 代码一致性）
- [x] **Anti-Hollow Check**：文档中每个配置键/错误码/API 名均能在源码中找到定义（抽查核对），无编造接口
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2291-duckdb-wi9-docs-and-routing.md --strict` 退出码 0

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（无）

## Closure

Status Note: WI9 收口。独立 closure audit（fresh subagent）裁决 CAN CLOSE、0 blocker：owner doc 与 live 代码全面核对（三配置键逐一对照 @InjectValue、10 错误码逐码核对含 8 bizFatal 抛出点定位到行、API 签名/数字全部一致、逐节扫无编造接口）；三处路由落点正确且一跳可达；BOUNDARY 规则零违反；check-doc-links/check-plan-checklist 双工具 EXIT=0（审计员复跑）。3 条 Minor 已修正（位置偏移备注、续跑语义一句补入 owner doc、commit 随本收口落地）。
Completed: 2026-10-01

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure auditor 子代理（fresh session，read-only audit）
- Audit Session: agent_54b18b9a-87d1-4742-b98b-0689d377d6ac
- Evidence:
  - owner doc 一致性 PASS：配置键/错误码（含逐码 bizFatal 抛出点行号）/API 签名/数字（50 函数/10 错误码/57 测/1.5.6.0）全部与源码核对一致；逐节扫无编造接口
  - 三处路由 PASS（INDEX 2 行一跳可达；module-groups 格式对齐；DUK-001..005 对齐 TS 行式）
  - 工具门：check-doc-links --strict EXIT=0、check-plan-checklist EXIT=0（审计员复跑）
  - BOUNDARY 规则 PASS（零 ai-dev 引用，文字指称合规）
  - 审计 Minor 修复记录：plan 位置备注、owner doc 补续跑语义、commit 落地

Follow-up:

- no remaining plan-owned work
