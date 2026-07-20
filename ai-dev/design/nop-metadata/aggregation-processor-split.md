# nop-metadata Aggregation Executor Processor Split Design

> Status: draft (待 plan 2026-07-19-1250-4 实施期细化)
> Last Reviewed: 2026-07-19
> Source: `ai-dev/plans/2026-07-19-1250-4-nop-metadata-aggregation-processor-split.md`

## Decision

`MetaAggregationExecutor`（当前 3474 行）按 7 条执行路径拆分为独立 Processor：

1. `EntityAggregation` — entity 单表聚合（经平台 ORM `IOrmTemplate`）
2. `ExternalAggregation` — external 单表聚合（限定表名原生 SQL）
3. `SqlAggregation` — sql 视图聚合（sourceSql 子查询）
4. `EntityEntityJoinAggregation` — entity↔entity 跨表聚合（同库原生 JOIN SQL）
5. `ExternalExternalJoinAggregation` — external↔external 跨表聚合（同库原生 JOIN SQL）
6. `MixedSameDbJoinAggregation` — 同库混合端点 JOIN 聚合
7. `CrossDbInMemoryAggregation` — 跨库应用层聚合（限流 + 内存拼接）

## Shared State

抽出 `AggregationContext` 承载跨 Processor 共享的依赖：

- `MetaQueryContext`（daoProvider / orm / connectionService / tableRefExecutor / dataSourceResolver / fieldResolver / filterTranslator）
- `MetaJoinExecutor`（共享 join 加载 + 端点解析）
- `NopMetaTable table`（目标逻辑表）
- `List<String> measures` / `List<String> dimensions` / `TreeBean filter` / `TreeBean having` / `List<OrderFieldBean> orderBy`

## Dispatch

`MetaAggregationExecutor.executeAggregation` 改为路径分派器：

1. 加载 NopMetaTable
2. 按 tableType + 是否提供 joinId + 端点组合 → 选择 Processor
3. 构造 `AggregationContext`
4. 委托 Processor.execute() 返回结果

## Rationale

- 单类 3474 行难以维护、单测覆盖率难提升、AI 阅读 token 消耗大
- 7 条路径相互独立（无共享内部状态），天然可拆
- 拆分后入口仅做分派（≤ 500 行），每个 Processor 聚焦单一执行路径（≤ 800 行）

## Rejected Alternatives

- **保留单类 + 内部 method 分组**：仍无法降低单类 token 消耗，单测覆盖率仍受限
- **按 tableType 拆（3 类）**：JOIN 路径下端点组合复杂，3 类内部仍需大量分派，效果有限
- **按 measure/dimension/having/orderBy 拆**：与执行路径正交，不解决核心 7 路径分派问题
