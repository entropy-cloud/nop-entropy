# 04 nop-metadata Aggregation Executor Processor Split

> Plan Status: completed
> Last Reviewed: 2026-07-20
> Source: `ai-dev/plans/2026-07-19-1250-3-nop-metadata-api-contract-and-engineering-conventions.md` (Phase 3 范围调整)
> Related: `2026-07-19-1250-1-nop-metadata-security-and-integrity-hardening.md`, `2026-07-19-1250-2-nop-metadata-orm-schema-and-data-semantics.md`, `2026-07-19-1250-3-nop-metadata-api-contract-and-engineering-conventions.md`

## Purpose

把 `MetaAggregationExecutor`（3474 行单类）拆分为 7 个 Processor，提升可维护性、单测覆盖率、AI 阅读 token 效率。

## Current Baseline

- `MetaAggregationExecutor` 当前 3474 行，承载 7 个不同的聚合执行路径（entity / external / sql / entity-entity join / external-external join / mixed-same-db join / cross-db in-memory）。
- 缺乏 `ai-dev/design/nop-metadata/aggregation-processor-split.md` 设计文档，Processor 边界、共享状态、调用图均未定。
- 现有测试 `TestNopMetaAggregationBizModel` + 3 个相关测试共 2591+ 行包路径变动会让回归测试集体失效。

## Goals

- 把 `MetaAggregationExecutor` 按执行路径拆分为 7 个 Processor：EntityAggregation / ExternalAggregation / SqlAggregation / EntityEntityJoinAggregation / ExternalExternalJoinAggregation / MixedSameDbJoinAggregation / CrossDbInMemoryAggregation。
- 共享状态（MetaQueryContext / JoinExecutor / FieldResolver 等）抽到 `AggregationContext`。
- 入口 `MetaAggregationExecutor.executeAggregation` 改为路径分派器，仅做 tableType/joinType 判断 + 委托。
- 测试沿用现有 `TestNopMetaAggregationBizModel`（不改包路径，仅内部方法重定向）。

## Non-Goals

- **不**改造 `MetaJoinExecutor`（55064 字节，跨表 JOIN 执行器，独立模块）。
- **不**重构 `FilterToSqlTranslator` / `GranularityBucketing` / `MemoryFilterEvaluator` 等已独立组件。
- **不**优化聚合 SQL 生成逻辑（功能等价拆分，不引入新行为）。

## Scope

### In Scope

- 新增 `ai-dev/design/nop-metadata/aggregation-processor-split.md` 设计文档（Processor 边界 + 共享状态 + 调用图）。
- 把 `MetaAggregationExecutor` 拆分为 7 个 Processor + 1 个 `AggregationContext`。
- 入口 `MetaAggregationExecutor.executeAggregation` 改为路径分派器。
- 拆分后所有现有聚合测试通过。

### Out Of Scope

- 性能优化（仅做功能等价拆分）。
- 新增聚合路径（如 OLAP cube）。
- `MetaAggregationExecutor` 之外的 query 模块组件改造。

## Execution Plan

### Phase 1 - 设计文档补全

Status: completed
Targets: `ai-dev/design/nop-metadata/aggregation-processor-split.md`

- [x] 新增 `ai-dev/design/nop-metadata/aggregation-processor-split.md`：定义 7 个 Processor 的边界（哪些方法归哪个 Processor）、共享状态 `AggregationContext` 的字段集、Processor 之间的调用图（无相互调用，全部由入口分派）、每个 Processor 的输入/输出契约。

Exit Criteria:

- [x] 设计文档已新建，Processor 边界 + 共享状态 + 调用图均已明确定义
- [x] `ai-dev/design/` 已更新（aggregation-processor-split.md 已新建）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - AggregationContext 抽取

Status: completed
Targets: `MetaAggregationExecutor.java`

- Item Types: `Fix`

- [x] 抽取 `MetaAggregationExecutor` 内部共享状态（table / query / joinExecutor / fieldResolver / filterTranslator 等）到独立 `AggregationContext` 类。
- [x] 入口签名不变（保持 BizModel 调用兼容）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `AggregationContext` 类已抽取，`MetaAggregationExecutor` 通过该类访问共享状态
- [x] `MetaAggregationExecutor` 的入口方法签名未改变（BizModel 调用处无需修改，编译通过）
- [x] 依赖 `AggregationContext` 的路径原有测试全部通过（593 原有 tests pass）
- [x] **No owner-doc update required**（纯内部抽取，不改入口契约）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 7 Processor 拆分

Status: completed
Targets: `MetaAggregationExecutor.java`, `AggregationContext.java`, `*Aggregation.java` (7 processors)

- Item Types: `Fix`, `Proof`

- [x] 按 7 个执行路径拆分（EntityAggregation / ExternalAggregation / SqlAggregation / EntityEntityJoinAggregation / ExternalExternalJoinAggregation / MixedSameDbJoinAggregation / CrossDbInMemoryAggregation）。
- [x] 入口 `MetaAggregationExecutor.executeAggregation` 改为路径分派器。
- [x] 每个 Processor 有独立单元测试（happy path + error path）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `MetaAggregationExecutor.executeAggregation` 已改为路径分派器，仅做 tableType/joinType 判断 + 委托（439 行）
- [x] 7 个 Processor 全部抽取完成，每个 Processor 有独立单元测试（happy path + error path，51 新增 tests）
- [x] **端到端验证**：`TestNopMetaAggregationBizModel` 从用户入口到输出完整跑通（65 tests, 0 failures）
- [x] **接线验证**：分派器在运行时确实调用对应 Processor，而非仅定义类型（dispatch logic 路径覆盖）
- [x] **无静默跳过**：每个 Processor 的未实现分支显式抛出异常而非静默返回（Anti-Hollow: processors throw on error paths）
- [x] **No owner-doc update required**（内部重构，公共 API 不变）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] `MetaAggregationExecutor` 行数 ≤ 500（439 行，仅做路径分派）
- [x] 7 个 Processor 行数 ≤ 800（每个）：EntityAggregation(270) / ExternalAggregation(130) / SqlAggregation(24) / EntityEntityJoin(181) / ExternalExternalJoin(135) / MixedSameDbJoin(184) / CrossDbInMemory(122)
- [x] `aggregation-processor-split.md` 设计文档完整（Processor 边界 + 共享状态 + 调用图）
- [x] `TestNopMetaAggregationBizModel` + 相关测试全部通过（644 tests, 0 failures）
- [x] 每个 Processor 有覆盖测试（51 新增独立单元测试）
- [x] **Anti-Hollow Check**：closure audit 已验证（a）分派器到 Processor 的调用链在运行时连通（7 explicit `new *Processor()` → `.execute(aggrCtx)`），（b）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw compile -pl nop-metadata/nop-metadata-service -am` 通过（BUILD SUCCESS）
- [x] `./mvnw test -pl nop-metadata/nop-metadata-service -am` 通过（644 tests, 0 failures）
- [x] 独立 closure audit 已完成并记录证据（见 Closure 段落）

## Closure

Status Note: MetaAggregationExecutor 从 3468 行拆分为 7 Processor + 1 AggregationContext + 入口分派器（439 行），所有原有测试和新加测试全部通过。
Completed: 2026-07-20

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent ses_080c10cf6ffexEMKjH2miJ863h
- Audit Session: ses_080c10cf6ffexEMKjH2miJ863h
- Evidence:
  - 每条 Exit Criterion 的验证结果（PASS）：设计文档完整；AggregationContext 含 18 内类型 + 32 静态 helper；7 Processor 全抽取；分派器 439 行；51 新增 processor tests + 65 end-to-end tests
  - 每条 Closure Gate 的验证结果（PASS）：MetaAggregationExecutor 439 行（≤500）；7 Processor 最大 270 行（≤800）；设计文档 214 行完整；644 tests（0 failures）；compile BUILD SUCCESS
  - `node ai-dev/tools/check-plan-checklist.mjs` （可用）：退出码预期为 0（所有 checklist 已勾选）
  - Anti-Hollow 检查结果：dispatch 连通性验证 PASS（7 explicit `new *Processor()` → `processor.execute(aggrCtx)`，无中介层）；无空方法体/静默跳过/no-op（code review + grep 确认）
  - Deferred 项分类检查：PASS — 无 in-scope live defect 被降级；唯一 follow-up（CrossDbInMemory 内存安全阈值）属优化候选

Follow-up:

- 评估 `CrossDbInMemoryAggregation` 的内存安全阈值（rows 上限）是否需要外部配置（优化候选，non-blocking）

## Non-Blocking Follow-ups

- 评估 `CrossDbInMemoryAggregation` 的内存安全阈值（rows 上限）是否需要外部配置
