# 307 nop-metadata DTO 迁移与 data-auth 扩展

> Plan Status: active
> Last Reviewed: 2026-07-21
> Source: `ai-dev/audits/2026-07-20-1816-open-audit-nop-metadata.md` (R-03, R-04); `ai-dev/audits/2026-07-20-1816-multi-audit-nop-metadata/summary.md`

## Purpose

完成 nop-metadata 审计中 2 项部分修复的工程债务：BizModel 方法从 `Map<String, Object>` 切换到已定义的 `@DataBean` DTO，以及扩大 `data-auth.xml` 行级权限实体覆盖范围。

## Current Baseline

- R-03: 29 个 `@DataBean` DTO 已在 `nop-metadata-service/.../dto/` 下定义，`TestNopMetaDtoResults.java` 已验证 DTO 字段可达。但 ~20 个 BizModel 自定义方法仍返回 `Map<String, Object>` 而非具体 DTO，包括 `queryTableData`、`testConnection`、`syncExternalTables`、`profileTable`、`queryAggregation` 等高频方法。
- R-04: `data-auth.xml` 当前覆盖 `NopMetaDataSource`、`NopMetaQualityCheckpoint`、`NopMetaModelChangedEvent` 共 3 个敏感实体。其余 29+ 实体无行级权限配置，包括含 `custom_sql` 的 `NopMetaQualityRule`、含 SQL 模板的 `NopMetaProfilingRule`、含对账数据的 `NopMetaReconciliationResult` 等。

## Goals

- 高频 BizModel 方法返回类型从 `Map<String, Object>` 切换为对应 `@DataBean` DTO，实现强类型 GraphQL schema 推导
- `data-auth.xml` 扩展覆盖 5 个高风险实体，建立行级权限防护
- 所有变更通过 `./mvnw compile && ./mvnw test` 验证

## Non-Goals

- 不涉及 `queryAggregation` 的 11 参数 `@RequestBean` 封装（参数数优化是独立议题）
- 不涉及 I*Biz 接口方法补齐（P2 问题，不属于本次 P3 范围）
- 不完成全量 29+ 实体的 data-auth 覆盖（仅覆盖最高风险的 5 个）

## Scope

### In Scope

- R-03: 修改 `NopMetaTableBizModel`（7 处）、`NopMetaDataSourceBizModel`（4 处）共 11 个高频方法的返回类型为对应 DTO，调整返回体构造
- R-04: 为 `NopMetaQualityRule`、`NopMetaProfilingRule`、`NopMetaReconciliationResult`、`NopMetaDataContract`、`NopMetaBusinessDomain` 共 5 个实体新增 `data-auth.xml` 行级权限配置

### Out Of Scope

- 其他 BizModel（QualityRule、LineageEdge、QualityCheckpoint、QualityScore、DataContract）的 DTO 迁移
- `queryAggregation` 方法的 `@RequestBean` 参数封装
- `data-auth.xml` 的全量实体覆盖
- 多租户场景的端到端权限测试

## Execution Plan

### Phase 1 - BizModel 高频方法 DTO 返回类型迁移

Status: planned
Targets: `nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaTableBizModel.java`, `NopMetaDataSourceBizModel.java`, `nop-metadata-service/src/test/java/.../TestNopMetaDtoResults.java`

- Item Types: `Fix`, `Proof`

**方法归属纠正（基于 live code 审计）：**
- `NopMetaTableBizModel`（7 个方法）：`profileTable`, `createSqlTable`, `previewSqlFields`, `resolveTableFields`, `queryTableData`, `queryJoinData`, `queryAggregation`
- `NopMetaDataSourceBizModel`（4 个方法）：`testConnection`, `syncExternalTables`, `collectCatalog`, `collectCatalogForTable`

- [ ] `NopMetaTableBizModel.profileTable` → `ProfileResultDTO`：注意 DTO 中 `columns: List<ProfilingColumnStatsDTO>` 替代当前 `columnUnavailable + columnCount` 分离模式，需调整返回体结构
- [ ] `NopMetaTableBizModel.createSqlTable` → `CreateSqlTableResultDTO`：嵌套字段 `fields: List<Map>` → `List<SqlViewFieldDTO>` 转换
- [ ] `NopMetaTableBizModel.previewSqlFields` → `PreviewSqlFieldsResultDTO`（已存在，含 `List<SqlViewFieldDTO> fields`，直接可用）
- [ ] `NopMetaTableBizModel.resolveTableFields` → `ResolveTableFieldsResultDTO`：嵌套 `fields: List<Map>` → `List<ResolvedTableFieldDTO>` 转换
- [ ] `NopMetaTableBizModel.queryTableData` → `QueryTableDataResultDTO`：确认字段映射一致
- [ ] `NopMetaTableBizModel.queryJoinData` → `QueryJoinDataResultDTO`：字段映射验证
- [ ] `NopMetaTableBizModel.queryAggregation` → `AggregationResultDTO`：字段映射验证
- [ ] `NopMetaDataSourceBizModel.testConnection` → `TestConnectionResultDTO`：注意 `connectionService.testConnect()` 目前返回 `Map<String, Object>`，需改为返回 DTO 或包装
- [ ] `NopMetaDataSourceBizModel.syncExternalTables` → `SyncExternalTablesResultDTO`：当前 `errors: [{tableName, error}]` 与 DTO 的 `errors: List<ErrorDTO>` 结构不匹配——注意 `ErrorDTO` 只有 `{code, message, detail}` 不含 `tableName`，直接映射会丢失表名信息。需决定：扩展 `ErrorDTO` 增加 `tableName` 或使用独立错误结构
- [ ] `NopMetaDataSourceBizModel.collectCatalog` → `CollectCatalogResultDTO`：DTO 用 `tableCount` 替代当前 `collectedCount`，且新增 `tables` 字段，需确认业务语义并调整映射
- [ ] `NopMetaDataSourceBizModel.collectCatalogForTable` → `CollectCatalogResultDTO`（或新建 DTO）：单表变体，返回结构包含 `{metaTableId, rowCount, indexCount, unavailable, ...}`，与 `CollectCatalogResultDTO` 形状不同，需确认复用还是新建

**设计决策（完成前裁定）：**
- [ ] 评估 DTO 结构不匹配的方法（标记了"注意"的），决定是调整 DTO 定义还是调整返回体——记录在 `ai-dev/design/` 或 Phase 1 Exit Criteria 中
- [ ] 对于 `ErrorDTO` 缺少 `tableName` 的问题，裁定：扩展 `ErrorDTO`、新建含 `tableName` 的错误 DTO、或设计替代方案
- [ ] 对于嵌套字段类型转换（如 `List<Map>` → `List<DTO>`），确认转换逻辑的位置（BizModel 内内联 vs 抽取 helper）

- [ ] 调整每个方法的返回体构造，从 `LinkedHashMap` + `put` 改为 DTO 构造函数或 builder
- [ ] 更新 `TestNopMetaDtoResults.java`：新增针对每个切换方法的具体集成测试，验证通过 GraphQL 查询时返回的 JSON 结构包含正确的 DTO 字段名称和类型

Exit Criteria:

- [ ] 11 个 BizModel 方法签名从 `Map<String, Object>` 改为具体 `@DataBean` DTO
- [ ] 返回体使用 DTO 构造而非 `LinkedHashMap` + `put`
- [ ] 每个 DTO 结构不匹配已做出设计决策（调整 DTO 或调整返回体），且记录在 Exit Criteria 中
- [ ] `./mvnw compile -pl nop-metadata -am` 通过
- [ ] 测试覆盖：每个方法至少一个集成测试验证 DTO 返回正确性（例如 GraphQL schema JSON 包含 `ProfileResultDTO` 字段名）
- [ ] **GraphQL schema 推导验证**：确认 GraphQL schema 输出（通过 `nop-graphql-core` 的 schema 导出机制或 `devtools/` 端点）显示 DTO 类型字段而非 `Map`，证明强类型已生效
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - data-auth.xml 实体覆盖扩展

Status: planned
Targets: `nop-metadata-service/src/main/resources/_vfs/nop/metadata/auth/nop-metadata.data-auth.xml`

- Item Types: `Fix`, `Proof`

- [ ] 按敏感度排序，确定 5 个最高风险实体的行级权限过滤条件
- [ ] 为 `NopMetaQualityRule`（含 `custom_sql`）配置行级权限——建议模式：`createdBy == $user.userId`（需确认实体是否有 `createdBy` 列）
- [ ] 为 `NopMetaProfilingRule`（含 SQL 模板）配置行级权限——建议模式：`createdBy == $user.userId`
- [ ] 为 `NopMetaReconciliationResult`（含对账数据）配置行级权限——建议模式：基于 `dataSourceId` 关联用户可见数据源
- [ ] 为 `NopMetaDataContract`（含合约配置）配置行级权限——建议模式：`createdBy == $user.userId` 或基于角色
- [ ] 为 `NopMetaBusinessDomain`（业务域树）配置行级权限——建议模式：树形结构需要基于角色的可见性策略
- [ ] 为每个实体确认是否有所需的 filter 列（如 `createdBy`），若缺失则在 ORM 模型或过滤策略中补充
- [ ] 编写测试：使用 mock 用户上下文（`IServiceContext`）调用 BizModel 查询，验证 `data-auth` 规则在写入操作中触发且正确过滤结果（无需多租户 E2E 环境，通过 `IDataAuthChecker` mock 或 `IBizObjectManager` 集成测试验证）

Exit Criteria:

- [ ] `data-auth.xml` 中新增 5 个实体的 `<obj>` 配置，含合理的行级过滤规则
- [ ] 每个实体的 filter 列在 ORM 模型中存在或已设计替代策略
- [ ] `./mvnw compile -pl nop-metadata -am` 通过
- [ ] 测试覆盖：mock 用户上下文验证 data-auth 过滤规则被正确应用（至少 1 个集成测试断言 filter 生效）
- [ ] **接线验证**：确认新增的 data-auth 规则在 BizModel 的 `@BizMutation`/`@BizQuery` 执行路径中被触发（非空壳配置）
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] Phase 1 和 Phase 2 的 Exit Criteria 全部勾选
- [ ] `./mvnw compile -pl nop-metadata -am` 通过
- [ ] `./mvnw test -pl nop-metadata -am` 通过
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] Anti-Hollow Check：(a) DTO 返回类型在 GraphQL 端确实生效（可通过 schema 输出验证），(b) data-auth 规则在运行时被 BizModel 的写入操作触发

## Deferred But Adjudicated

### 全量 29+ 实体 data-auth 覆盖

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 当前 3 + 5 = 8 个最高风险实体已覆盖；其余实体风险相对可控（低敏感度业务数据）
- Successor Required: no

## Non-Blocking Follow-ups

- 剩余 BizModel（QualityRule、LineageEdge 等）的 DTO 迁移可后续分批进行
- `queryAggregation` 的 `@RequestBean` 参数封装是独立的优化项

## Closure

Status Note:
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:

Follow-up:

- 剩余 BizModel DTO 迁移（非高频方法）
