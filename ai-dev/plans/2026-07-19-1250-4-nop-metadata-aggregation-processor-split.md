# 04 nop-metadata Aggregation Executor Processor Split

> Plan Status: draft
> Last Reviewed: 2026-07-19
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

Status: planned

- [ ] 新增 `ai-dev/design/nop-metadata/aggregation-processor-split.md`：定义 7 个 Processor 的边界（哪些方法归哪个 Processor）、共享状态 `AggregationContext` 的字段集、Processor 之间的调用图（无相互调用，全部由入口分派）、每个 Processor 的输入/输出契约。

### Phase 2 - AggregationContext 抽取

Status: planned

- [ ] 抽取 `MetaAggregationExecutor` 内部共享状态（table / query / joinExecutor / fieldResolver / filterTranslator 等）到独立 `AggregationContext` 类。
- [ ] 入口签名不变（保持 BizModel 调用兼容）。

### Phase 3 - 7 Processor 拆分

Status: planned

- [ ] 按 7 个执行路径拆分（EntityAggregation / ExternalAggregation / SqlAggregation / EntityEntityJoinAggregation / ExternalExternalJoinAggregation / MixedSameDbJoinAggregation / CrossDbInMemoryAggregation）。
- [ ] 入口 `MetaAggregationExecutor.executeAggregation` 改为路径分派器。
- [ ] 每个 Processor 有独立单元测试（happy path + error path）。

## Closure Gates

- [ ] `MetaAggregationExecutor` 行数 ≤ 500（仅做路径分派）
- [ ] 7 个 Processor 行数 ≤ 800（每个）
- [ ] `aggregation-processor-split.md` 设计文档完整（Processor 边界 + 共享状态 + 调用图）
- [ ] `TestNopMetaAggregationBizModel` + 相关测试全部通过
- [ ] 每个 Processor 有覆盖测试

## Non-Blocking Follow-ups

- 评估 `CrossDbInMemoryAggregation` 的内存安全阈值（rows 上限）是否需要外部配置
