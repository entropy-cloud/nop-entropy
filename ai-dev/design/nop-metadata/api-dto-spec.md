# nop-metadata API DTO 规格文档

> Status: active contract spec
> Last Reviewed: 2026-07-19
> Source: `ai-dev/plans/2026-07-19-1250-3-nop-metadata-api-contract-and-engineering-conventions.md` Phase 1
> Related: audit `2026-07-19-1118-multi-audit-nop-metadata.md` (维度03-02)

## Purpose

为 nop-metadata BizModel 层 22+ 处返回 `Map<String, Object>` / `List<Map<String, Object>>` 的 `@BizQuery` / `@BizMutation` 方法定义强类型 `@DataBean` DTO 替代品。

## Design Decisions

- DTO 放在 `nop-metadata-service/.../dto/`（仅服务层内部使用，无跨模块 typed RPC 需求）。
- 共享 DTO（如 `ErrorDTO`）放在 `nop-metadata-dao/.../dto/`（多个 BizModel 共享）。
- 字段命名与原 Map key 完全一致，便于迁移期前后兼容对照。
- 每个方法新增 DTO 返回的 overload，原 Map 版本保留（避免现有测试集体失效）；新代码用 DTO 版本。
- `@DataBean` 注解由 `io.nop.api.core.annotations.data.DataBean` 提供。

## DTO Field Specifications

### 1. 共享 DTO

#### `ErrorDTO`（放在 `nop-metadata-dao/.../dto/`）
- `String code` — 错误码（如 `metadata.datasource-connect-failed`）
- `String message` — 错误摘要
- `String detail` — 上下文详情（可选）

#### `KeyValueDTO`（放在 `nop-metadata-dao/.../dto/`）
- `String name`
- `String value`

### 2. Aggregation（来源：`NopMetaTableBizModel.queryAggregation`）

#### `AggregationResultDTO`
- `List<AggregationRowDTO> items`

#### `AggregationRowDTO`
- `Map<String, Object> dimensions` — 维度值（dimensionName → value）
- `Map<String, Object> measures` — 指标聚合值（measureName → aggValue）

### 3. Profiling（来源：`NopMetaTableBizModel.profileTable` / `NopMetaProfilingRuleBizModel.executeProfilingRule`）

#### `ProfileResultDTO`
- `String profilingResultId`
- `int columnCount`
- `List<ProfilingColumnStatsDTO> columns`
- `List<String> unavailable`
- `List<ErrorDTO> errors`

#### `ProfilingColumnStatsDTO`
- `String columnName`
- `Long rowCount`
- `Long nullCount`
- `Double nullRatio`
- `Object minValue`
- `Object maxValue`
- `Map<String, Object> extra`

### 4. Test Connection（来源：`NopMetaDataSourceBizModel.testConnection`）

#### `TestConnectionResultDTO`
- `boolean connected`
- `String databaseProductName`
- `String error`

### 5. Sync External Tables（来源：`NopMetaDataSourceBizModel.syncExternalTables`）

#### `SyncExternalTablesResultDTO`
- `int syncedTableCount`
- `List<ErrorDTO> errors`

### 6. Collect Catalog（来源：`NopMetaDataSourceBizModel.collectCatalog` / `collectCatalogForTable`）

#### `CollectCatalogResultDTO`
- `int tableCount`
- `List<CollectCatalogTableDTO> tables`
- `List<ErrorDTO> errors`

#### `CollectCatalogTableDTO`
- `String tableName`
- `String schema`
- `String tableType`
- `Long rowCount`
- `Long sizeBytes`

### 7. SQL View（来源：`NopMetaTableBizModel.createSqlTable` / `previewSqlFields` / `resolveTableFields`）

#### `CreateSqlTableResultDTO`
- `String metaTableId`
- `String tableName`
- `String tableType`
- `List<SqlViewFieldDTO> fields`

#### `PreviewSqlFieldsResultDTO`
- `List<SqlViewFieldDTO> fields`

#### `ResolveTableFieldsResultDTO`
- `String tableType`
- `List<ResolvedTableFieldDTO> fields`

#### `SqlViewFieldDTO`
- `String name`
- `String alias`
- `String type`

#### `ResolvedTableFieldDTO`
- `String name`
- `String sourceType`
- `String type`

### 8. Query Table Data（来源：`NopMetaTableBizModel.queryTableData`）

#### `QueryTableDataResultDTO`
- `String tableType`
- `List<Map<String, Object>> items`（行数据；row schema 跟随表结构动态变化，保留 Map）

### 9. Join Data（来源：`NopMetaTableBizModel.queryJoinData`）

#### `QueryJoinDataResultDTO`
- `List<Map<String, Object>> items`

### 10. Lineage Extract（来源：`NopMetaLineageEdgeBizModel.recordLineage` / `extractLineageFromSql` / `extractColumnLineageFromSql` / `extractMeasureLineage`）

#### `LineageRecordResultDTO`
- `int edgeCount`

#### `LineageExtractResultDTO`
- `String metaTableId`
- `int edgeCount`
- `List<String> sourceTables`

### 11. Quality Rule Execution（来源：`NopMetaQualityRuleBizModel.executeQualityRule` / `executeQualityRulesForDataSource`）

#### `QualityRuleResultDTO`
- `String qualityRuleId`
- `int resultCount`
- `int passCount`
- `int failCount`
- `List<ErrorDTO> errors`

#### `QualityRulesForDataSourceResultDTO`
- `String dataSourceId`
- `int totalRuleCount`
- `int executedRuleCount`
- `List<QualityRuleResultDTO> ruleResults`
- `List<ErrorDTO> errors`

### 12. Quality Checkpoint Execution（来源：`NopMetaQualityCheckpointBizModel.executeCheckpoint`）

#### `CheckpointExecutionResultDTO`
- `String checkpointId`
- `int totalRuleCount`
- `int executedRuleCount`
- `List<QualityRuleResultDTO> ruleResults`
- `List<ErrorDTO> errors`

### 13. Quality Score（来源：`NopMetaQualityScoreBizModel.computeQualityScore`）

#### `QualityScoreResultDTO`
- `String metaTableId`
- `String qualityScoreId`
- `double score`
- `int totalRules`
- `int passedRules`
- `int failedRules`
- `int skippedRules`

### 14. Contract Check（来源：`NopMetaDataContractBizModel.checkContract`）

#### `ContractCheckResultDTO`
- `String contractId`
- `boolean passed`
- `List<ErrorDTO> errors`

### 15. Import ORM Models（来源：`NopMetaModuleBizModel.importOrmModels`）

#### `ImportOrmModelResultDTO`
- `String metaModuleId`
- `String moduleName`
- `boolean success`
- `String error`

## Method → DTO 映射表

| BizModel | Method | DTO |
|----------|--------|-----|
| NopMetaTableBizModel | profileTable | ProfileResultDTO |
| NopMetaTableBizModel | createSqlTable | CreateSqlTableResultDTO |
| NopMetaTableBizModel | previewSqlFields | PreviewSqlFieldsResultDTO |
| NopMetaTableBizModel | resolveTableFields | ResolveTableFieldsResultDTO |
| NopMetaTableBizModel | queryTableData | QueryTableDataResultDTO |
| NopMetaTableBizModel | queryJoinData | QueryJoinDataResultDTO |
| NopMetaTableBizModel | queryAggregation | AggregationResultDTO |
| NopMetaDataSourceBizModel | testConnection | TestConnectionResultDTO |
| NopMetaDataSourceBizModel | syncExternalTables | SyncExternalTablesResultDTO |
| NopMetaDataSourceBizModel | collectCatalog | CollectCatalogResultDTO |
| NopMetaDataSourceBizModel | collectCatalogForTable | CollectCatalogResultDTO |
| NopMetaLineageEdgeBizModel | recordLineage | LineageRecordResultDTO |
| NopMetaLineageEdgeBizModel | extractLineageFromSql | LineageExtractResultDTO |
| NopMetaLineageEdgeBizModel | extractColumnLineageFromSql | LineageExtractResultDTO |
| NopMetaLineageEdgeBizModel | extractMeasureLineage | LineageExtractResultDTO |
| NopMetaQualityRuleBizModel | executeQualityRule | QualityRuleResultDTO |
| NopMetaQualityRuleBizModel | executeQualityRulesForDataSource | QualityRulesForDataSourceResultDTO |
| NopMetaQualityCheckpointBizModel | executeCheckpoint | CheckpointExecutionResultDTO |
| NopMetaQualityScoreBizModel | computeQualityScore | QualityScoreResultDTO |
| NopMetaDataContractBizModel | checkContract | ContractCheckResultDTO |
| NopMetaProfilingRuleBizModel | executeProfilingRule | ProfileResultDTO |
| NopMetaModuleBizModel | importOrmModels | List&lt;ImportOrmModelResultDTO&gt; |

## Notes

- 共享 `ErrorDTO` + `KeyValueDTO` 放在 dao 层；其余 DTO 放在 service 层（仅服务层使用）。
- `Map<String, Object>` 内部嵌套结构（如查询行数据 items）保留 Map，因为 schema 跟随物理表结构动态变化，强行引入 DTO 反而损失灵活性（这是 plan Non-Goals 中"50+ @SuppressWarnings 完整 DTO 化延后"的同一裁定）。
- 每个 BizModel 新增 DTO overload；原 Map 版本保留兼容。新增方法以 `@BizQuery` / `@BizMutation` 直接暴露给 GraphQL（xbiz 自动生成 schema 时 DTO 字段就是 GraphQL field，前端可用 selection 下推）。
