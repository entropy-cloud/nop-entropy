# nop-metadata — 联邦式元数据 / BI 语义层 / 血缘 / 质量 / 对账

## 功能概览

nop-metadata 是 Nop 平台的**联邦式元数据中心**，承担五类职责：

1. **元数据目录（Catalog）**：跨数据源（JDBC）/SQL 视图/ORM 实体的统一逻辑表抽象；支持从外部库 `syncExternalTables` 自动同步物理表结构到逻辑表。
2. **BI 语义层（Semantic Layer）**：在逻辑表之上定义 Measure（指标）/ Dimension（维度）/ Join（关联）/ Filter（过滤），通过 `queryAggregation` / `queryJoinData` / `queryTableData` 提供 EQL/GraphQL 查询入口。
3. **血缘追踪（Lineage）**：从 SQL AST 自动抽取表级 + 列级 + 指标级血缘；支持上下游追溯与影响分析。
4. **数据质量（Quality）**：定义质量规则 + 检查点批量执行 + 自动评分；支持 webhook / notify 动作分发执行摘要。
5. **数据对账（Reconciliation）**：配置驱动（columnName + matchStrategy）的双向数据比对，支持精确/模糊匹配。

- 联邦式查询：external（原生 SQL）/ entity（平台 ORM）/ sql（用户视图 SQL）三类 tableType 统一查询入口
- 跨库 JOIN：同库走原生 JOIN SQL，跨库走应用层拼接（限流 + 显式失败）
- 元数据变更事件（`NopMetaModelChangedEvent`）：表/模块/数据源 CRUD 自动记录 before/after 快照

## 核心实体

| 实体 | 表名 | 用途 |
|------|------|------|
| NopMetaModule | `nop_meta_module` | 业务模块（聚合多张逻辑表的命名空间） |
| NopMetaTable | `nop_meta_table` | 逻辑表（tableType: entity/external/sql） |
| NopMetaDataSource | `nop_meta_data_source` | 外部数据源配置（jdbc 类型 + 连接信息） |
| NopMetaEntity | `nop_meta_entity` | ORM 实体（与 NopMetaOrmModel 关联） |
| NopMetaEntityField | `nop_meta_entity_field` | 实体字段 |
| NopMetaTableJoin | `nop_meta_table_join` | 跨表 JOIN 关联定义（端点 + joinType + 关联字段） |
| NopMetaTableMeasure | `nop_meta_table_measure` | 指标定义（aggFunc + 字段引用 + expression） |
| NopMetaTableDimension | `nop_meta_table_dimension` | 维度定义（granularity + 字段引用） |
| NopMetaTableFilter | `nop_meta_table_filter` | 通用 filter 定义（TreeBean） |
| NopMetaLineageEdge | `nop_meta_lineage_edge` | 血缘边（source/target table + 列级 + transformType） |
| NopMetaQualityRule | `nop_meta_quality_rule` | 质量规则定义（ruleType + entity/field/table 范围） |
| NopMetaQualityCheckpoint | `nop_meta_quality_checkpoint` | 质量检查点（批量执行 + cron 调度） |
| NopMetaQualityResult | `nop_meta_quality_result` | 单规则执行结果（PASS/FAIL/SKIP） |
| NopMetaQualityScore | `nop_meta_quality_score` | 单表质量评分（按规则通过率聚合） |
| NopMetaProfilingRule | `nop_meta_profiling_rule` | 数据剖析规则 |
| NopMetaProfilingResult | `nop_meta_profiling_result` | 数据剖析结果快照 |
| NopMetaReconciliationConfig | `nop_meta_reconciliation_config` | 对账配置 |
| NopMetaReconciliationResult | `nop_meta_reconciliation_result` | 对账结果（含每行 UNMATCHED/MATCHED 状态） |
| NopMetaCatalog | `nop_meta_catalog` | catalog 运行时统计时序快照（rowCount/sizeBytes/lastModified） |
| NopMetaDataContract | `nop_meta_data_contract` | 数据契约（quality + SLA） |
| NopMetaManifest | `nop_meta_manifest` | 模块清单（自包含 JSON 快照） |

## 典型使用场景

### 1. 同步外部表 + 联邦查询

```graphql
mutation {
  NopMetaDataSource__syncExternalTables(dataSourceId: "ds-1", schemaPattern: "PUBLIC") {
    syncedTableCount
    errors { tableName error }
  }
}

query {
  NopMetaTable__queryTableData(metaTableId: "t-1", limit: 10) {
    tableType
    items
  }
}
```

### 2. BI 指标聚合查询（GROUP BY + 跨表 JOIN）

```graphql
query {
  NopMetaTable__queryAggregation(
    metaTableId: "t-1",
    measures: ["total_amount", "count_orders"],
    dimensions: ["region", "month"],
    joinId: "j-region",
    limit: 100
  ) { items }
}
```

### 3. 血缘抽取（从 SQL 自动追踪表/列级血缘）

```graphql
mutation {
  NopMetaLineageEdge__extractColumnLineageFromSql(metaTableId: "t-1") {
    edgeCount
    sourceTables
  }
}

query {
  NopMetaLineageEdge__getImpactAnalysis(metaTableId: "t-1", columnName: "AMOUNT")
}
```

### 4. 质量检查点批量执行（含 cron 调度）

```graphql
mutation {
  NopMetaQualityCheckpoint__executeCheckpoint(checkpointId: "cp-1") {
    totalRuleCount
    executedRuleCount
    ruleResults { qualityRuleId passCount failCount }
    errors { code message }
  }
}
```

## API 契约（I*Biz 接口）

每个 BizModel 都实现了对应的 `INopMeta*Biz` 接口（位于 `nop-metadata-dao/.../biz/`），声明全部自定义 `@BizQuery` / `@BizMutation` 方法签名。跨模块 `@Inject INopMeta*Biz` 可直接调用接口方法，避免依赖具体实现类。

主要 I*Biz 接口（plan 2026-07-19-1250-3 Phase 1 补齐）：

- `INopMetaTableBiz` — profileTable / createSqlTable / previewSqlFields / resolveTableFields / queryTableData / queryJoinData / queryAggregation
- `INopMetaDataSourceBiz` — testConnection / syncExternalTables / collectCatalog / collectCatalogForTable
- `INopMetaModuleBiz` — importOrmModel / importOrmModels / releaseModule / generateManifest
- `INopMetaLineageEdgeBiz` — recordLineage / extractLineageFromSql / extractColumnLineageFromSql / extractMeasureLineage / getUpstream / getDownstream / getLineagePath / getImpactAnalysis
- `INopMetaQualityRuleBiz` / `INopMetaQualityCheckpointBiz` / `INopMetaQualityScoreBiz` — 质量规则/检查点/评分
- `INopMetaDataContractBiz` / `INopMetaProfilingRuleBiz` — 契约 / 剖析

## 关键内部组件（source anchors）

参见 `docs-for-ai/04-reference/source-anchors.md` 的 `META-001..005`：

- `META-001 MetaAggregationExecutor` — 指标/维度聚合执行器（7 路径分派：entity/external/sql × 单表/JOIN/跨库）
- `META-002 MetaTableReferenceResolver` — 逻辑表 → TableReference 解析（按 tableType 分派 entity/external/sql 端点）
- `META-003 MetaQualityRuleExecutor` — 单条质量规则执行（not_null/unique/regex/volume/custom_sql 等）
- `META-004 SqlColumnLineageExtractor` — SQL AST 列级血缘抽取（SELECT 列 → 源列）
- `META-005 MetaQualityCheckpointScheduler` — cron 调度器（启动 scanner + 运行时增量 + beanMethod invoker）

## 模块结构

| 子模块 | 用途 |
|--------|------|
| `nop-metadata-core` | 共享常量（`_NopMetadataCoreConstants`，70+ 表/数据源/血缘/质量等枚举常量）+ 29 个 `@DataBean` DTO 类（`nop-metadata-core/dto/`） |
| `nop-metadata-api` | 跨模块 API 接口定义 |
| `nop-metadata-dao` | ORM 实体 + BizModel 接口（`INopMeta*Biz`） |
| `nop-metadata-codegen` | Codegen 模板元数据快照 |
| `nop-metadata-meta` | xbeans / xmeta / 模型定义 |
| `nop-metadata-service` | BizModel 实现 + Executor / Processor / Helper |
| `nop-metadata-web` | GraphQL / xbiz 自动注册入口 |
| `nop-metadata-app` | Quarkus 启动入口（demo 应用） |

## 失败路径显式化

nop-metadata 严格遵循"无静默跳过"原则（plan 2026-07-19-1250-3 Phase 2 维度09-07）：

- 表不存在 / 数据源不存在 / DISABLED / 非 jdbc 类型 / 不支持的方言 / SQL 解析失败 / 字段引用非法 → **显式抛 `NopException` + ErrorCode**，不静默空集、不伪造值
- 批量操作（syncExternalTables / collectCatalog / executeCheckpoint）per-row try/catch 隔离失败 + 收集到 errors 列表，不中断整批
- ErrorCode 已集中到 `NopMetadataErrors.java`，命名前缀 `nop.err.metadata.*`（plan Phase 2 渐进迁移）

## 参考文档

- 平台主文档：`docs-for-ai/03-modules/nop-metadata.md`（本文档）
- I*Biz 接口契约（`nop-metadata-dao/.../biz/INopMeta*Biz.java`）：每个 BizModel 都有对应接口声明全部自定义方法签名
- DTO 规格（`nop-metadata-core/dto/`）：29 个 `@DataBean` DTO 类承载 API 返回值强类型契约
- ErrorCode 集中化（`nop-metadata-service/.../NopMetadataErrors.java`）：跨文件去重 + ARG_* 参数常量
- 模块级异常（`NopMetadataException`）：替代 `IllegalArgumentException` / `UnsupportedOperationException` / 裸 `RuntimeException`

> 设计决策、执行计划、修复记录等内部资料位于 `ai-dev/` 目录（按 AGENTS.md 文档分区约定，docs-for-ai 不引用 ai-dev 路径）。
