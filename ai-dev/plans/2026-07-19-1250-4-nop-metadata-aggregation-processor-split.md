# nop-metadata MetaAggregationExecutor 拆分

> Plan Status: active
> Last Reviewed: 2026-07-19
> Source: `ai-dev/audits/2026-07-19-1118-multi-audit-nop-metadata.md` 维度02-02（split from `2026-07-19-1250-3-nop-metadata-api-contract-and-engineering-conventions.md` Phase 3）
> Execution Order: 4 (Plan 1/2/3 完成后启动；本计划是 Plan 3 的 successor，专门处理 MetaAggregationExecutor 大类拆分)
> Related: `2026-07-19-1250-3-nop-metadata-api-contract-and-engineering-conventions.md`

## Purpose

把 `MetaAggregationExecutor`（3474 行，含 14 个嵌套类、96 个方法、12+ ErrorCode）拆分为 7 个 Processor + Accumulator/SideResolver helper。Phase 1 先起草 `ai-dev/design/nop-metadata/aggregation-processor-split.md` 设计文档（每个 Processor 接管的方法清单、共享 helper 归属、Processor 间调用图、Accumulator 接口契约）；Phase 2 在 design doc 落地后才进入实施。Phase 顺序天然保证"先设计、后拆分"，不需要用 plan-level status 来额外 gating。

## Current Baseline

- `MetaAggregationExecutor` 单类 3474 行，混合 7 条执行路径（entity-聚合 / external-聚合 / sql-聚合 / entity-entity JOIN / external-external JOIN / mixed-same-db JOIN / cross-db 内存聚合）+ 6 个内存聚合 Accumulator + 3 个 JOIN 字段解析器 + 12+ ErrorCode。
- 现有测试：`TestNopMetaAggregationBizModel` + `TestHavingArithmeticPreprocess` + `TestExpressionMeasureValidator` + `TestMemoryFilterAndOrderBy` 共 2591+ 行。
- 维度02-02（P2）建议拆分为同包多 Processor。

## Goals

- `MetaAggregationExecutor` 拆分为 7 个 Processor + helper，单文件行数 ≤ 600。
- 拆分前后所有现有测试（2591+ 行）全绿，行为完全等价。
- 设计文档 `ai-dev/design/nop-metadata/aggregation-processor-split.md` 完整描述每个 Processor 的方法清单、共享状态、调用图。

## Non-Goals

- 不重构 `NopMetaTableBizModel`（已在 Plan 3 Phase 3 拆出 `MetaTableQueryExecutor`）。
- 不改变聚合语义或 ErrorCode 字符串。
- 不在本计划修复安全漏洞（Plan 1）、ORM 结构（Plan 2）或 ErrorCode 集中化（Plan 3）。

## Scope

### In Scope

- 起草 `ai-dev/design/nop-metadata/aggregation-processor-split.md`（**前置 design doc**）。
- 拆分 7 个 Processor（按执行路径）：`EntityAggregationProcessor` / `ExternalAggregationProcessor` / `SqlAggregationProcessor` / `EntityEntityJoinAggregationProcessor` / `ExternalExternalJoinAggregationProcessor` / `MixedSameDbJoinAggregationProcessor` / `CrossDbInMemoryAggregationProcessor`。
- 6 个 Accumulator 提为独立文件或合并为 `MemAggAccumulator` + factory。
- 3 个 SideResolver 提为独立 helper。
- 12+ ErrorCode 上移到 `NopMetadataErrors.java`。**所有权裁定**：若 Plan 3 (`2026-07-19-1250-3-...`) Phase 2 已先完成集中化，则本 plan 只做引用迁移；否则本 plan 接管集中化工作并在 Closure 中记录"Plan 3 Phase 2 由本 plan 替代"。启动 Phase 2 前先核对 Plan 3 状态。

### Out Of Scope

- 改变聚合行为或 ErrorCode 字符串。
- 跨模块 API 变更。

## Execution Plan

### Phase 1 - 设计文档起草

Status: planned
Targets: `ai-dev/design/nop-metadata/aggregation-processor-split.md`

- Item Types: `Decision | Proof`

- [ ] 列出每个 Processor 接管的方法清单（96 个方法的归属）
- [ ] 列出共享 helper（`memoryGroupBy` / `truncateCrossDb` / `buildResult` / `stringKey` 等）的归属与可见性
- [ ] 列出 Processor 间调用图（如 `executeCrossDbJoinAggregation` 是否调用 `executeSameDbJoinAggregation`）
- [ ] 列出 Accumulator 接口契约与 factory 设计
- [ ] 列出现有测试包路径迁移计划

Exit Criteria:

- [ ] design doc 存在并被独立子 agent 审过
- [ ] design doc 含 96 方法归属表、共享 helper 表、调用图
- [ ] **No owner-doc update required**：本 Phase 仅产出 `ai-dev/design/` 内部文档，不改变 live baseline 或 public contract
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 实施拆分

Status: planned
Targets: `nop-metadata-service/.../query/`（7 Processor + helper 文件）

- Item Types: `Fix | Proof`

- [ ] 按 design doc 拆分 7 Processor + Accumulator + SideResolver + helper
- [ ] 现有测试全绿（Proof）
- [ ] 新增测试覆盖每个 Processor 至少一条 happy path + 一条 error path（Proof）

Exit Criteria:

- [ ] `MetaAggregationExecutor` 不再以单一 3474 行类存在
- [ ] 单文件行数 ≤ 600
- [ ] 拆分前后所有现有测试（2591+ 行）通过
- [ ] **无静默跳过**：拆分过程中无方法被遗漏（Minimum Rules #24）
- [ ] **接线验证**：测试中通过 mock/spy 验证拆分后的 Processor 在 BizModel 入口被运行时调用（Minimum Rules #23）
- [ ] **端到端验证**：至少一条聚合查询路径从 BizModel 入口 → Processor → Accumulator → 结果返回完整跑通（Minimum Rules #22）
- [ ] **No owner-doc update required**：纯内部重构，不改 public contract；若实施过程中发现需更新 `docs-for-ai/`，在此处补记具体文件
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] design doc 完整并被审过
- [ ] 拆分完成，单文件 ≤ 600 行
- [ ] 现有测试全绿
- [ ] **不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift**
- [ ] **受影响的 owner docs 已同步到 live baseline**，或明确写明 No owner-doc update required（本 plan 默认 No owner-doc update required：纯内部重构）
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：拆分后所有 Processor 在运行时被调用（不只是类存在）
- [ ] `./mvnw compile -pl nop-metadata -am -T 1C`
- [ ] `./mvnw test -pl nop-metadata -am -T 1C`
- [ ] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

_暂无。本 plan 启动后如发现可延期项再补。_

## Non-Blocking Follow-ups

- 暂无。本 plan 完成后视情况评估是否进一步细化 Accumulator factory 模式（如 `MemAggAccumulator` 抽象是否值得作为平台 helper 上提）。

## Closure

Status Note: <<完成时填写>>
Completed: <<YYYY-MM-DD>>

Closure Audit Evidence:

- Reviewer / Agent: <<独立审阅者或独立子 agent>>
- Audit Session: <<session ID>>
- Evidence: <<每条 Exit Criterion 的验证结果>>

Follow-up:

- <<only non-blocking follow-up；confirmed live defect 不得出现在这里；完成时写 "no remaining plan-owned work">>
