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

## 核心实体（39 个，完整清单与 `nop-metadata/model/nop-metadata.orm.xml` 一致）

| 实体 | 表名 | 用途 |
|------|------|------|
| NopMetaModule | `nop_meta_module` | 业务模块（聚合多张逻辑表的命名空间） |
| NopMetaOrmModel | `nop_meta_orm_model` | ORM 模型（importOrmModel 导入的模型定义快照，含 sourceContent/isDelta） |
| NopMetaDataSource | `nop_meta_data_source` | 外部数据源配置（jdbc 类型 + 连接信息） |
| NopMetaTable | `nop_meta_table` | 逻辑表（tableType: entity/external/sql） |
| NopMetaEntity | `nop_meta_entity` | ORM 实体（与 NopMetaOrmModel 关联） |
| NopMetaEntityField | `nop_meta_entity_field` | 实体字段 |
| NopMetaEntityRelation | `nop_meta_entity_relation` | 实体关系定义 |
| NopMetaEntityUniqueKey | `nop_meta_entity_unique_key` | 实体唯一键定义 |
| NopMetaEntityIndex | `nop_meta_entity_index` | 实体索引定义 |
| NopMetaDomain | `nop_meta_domain` | 域定义（stdDomain 声明） |
| NopMetaDict | `nop_meta_dict` | 元数据字典定义 |
| NopMetaDictItem | `nop_meta_dict_item` | 字典项 |
| NopMetaSemanticType | `nop_meta_semantic_type` | 语义类型定义（typeName + 字段语义标注） |
| NopMetaTableJoin | `nop_meta_table_join` | 跨表 JOIN 关联定义（端点 + joinType + 关联字段） |
| NopMetaTableMeasure | `nop_meta_table_measure` | 指标定义（aggFunc + 字段引用 + expression） |
| NopMetaTableDimension | `nop_meta_table_dimension` | 维度定义（granularity + 字段引用） |
| NopMetaTableFilter | `nop_meta_table_filter` | 通用 filter 定义（TreeBean） |
| NopMetaPipeline | `nop_meta_pipeline` | 数据管道（pipeline 定义） |
| NopMetaLineageEdge | `nop_meta_lineage_edge` | 血缘边（source/target table + 列级 + transformType） |
| NopMetaGlossary | `nop_meta_glossary` | 词汇表 |
| NopMetaGlossaryTerm | `nop_meta_glossary_term` | 词汇表术语 |
| NopMetaClassification | `nop_meta_classification` | 分类体系 |
| NopMetaTag | `nop_meta_tag` | 分类标签 |
| NopMetaTagLabel | `nop_meta_tag_label` | 语义标注（标签-对象关联，含提审/审批流） |
| NopMetaBusinessDomain | `nop_meta_business_domain` | 业务组织域 |
| NopMetaDataProduct | `nop_meta_data_product` | 数据产品（资产关联） |
| NopMetaQualityRule | `nop_meta_quality_rule` | 质量规则定义（ruleType + entity/field/table 范围） |
| NopMetaQualityCheckpoint | `nop_meta_quality_checkpoint` | 质量检查点（批量执行 + cron 调度） |
| NopMetaQualityResult | `nop_meta_quality_result` | 单规则执行结果（PASS/FAIL/SKIP；含 checkpointId/runId 幂等键列 + 复合 UK） |
| NopMetaQualityScore | `nop_meta_quality_score` | 单表质量评分（按规则通过率聚合） |
| NopMetaProfilingRule | `nop_meta_profiling_rule` | 数据剖析规则 |
| NopMetaProfilingResult | `nop_meta_profiling_result` | 数据剖析结果快照 |
| NopMetaReconciliationConfig | `nop_meta_reconciliation_config` | 对账配置 |
| NopMetaReconciliationEntity | `nop_meta_reconciliation_entity` | 对账候选实体缓存（匹配候选集来源） |
| NopMetaReconciliationResult | `nop_meta_reconciliation_result` | 对账结果（含每行 UNMATCHED/MATCHED 状态） |
| NopMetaCatalog | `nop_meta_catalog` | catalog 运行时统计时序快照（rowCount/sizeBytes/lastModified） |
| NopMetaDataContract | `nop_meta_data_contract` | 数据契约（quality + SLA） |
| NopMetaManifest | `nop_meta_manifest` | 模块清单（自包含 JSON 快照） |
| NopMetaModelChangedEvent | `nop_meta_model_changed_event` | 元数据变更事件（表/模块/数据源 CRUD 的 before/after 快照） |

## 典型使用场景

### 1. 同步外部表 + 联邦查询

```graphql
mutation {
  NopMetaDataSource__syncExternalTables(dataSourceId: "ds-1", schemaPattern: "PUBLIC") {
    syncedTableCount
    errors { code message detail }
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
    runId
    totalRuleCount
    executedRuleCount
    skipCount
    ruleResults { qualityRuleId status message }
    errors { code message }
  }
}
```

**执行结果 DTO（AR-14，R8.1）**：检查点路径填充 `totalRuleCount`（= 解析后规则集大小，含异常/SKIP 规则）、`skipCount`（显式 SKIP 计数）与 `ruleResults`（每条规则一个条目，含异常规则补写的 ERROR 条目——`ruleResults` 条目数 = `totalRuleCount`，计数可对账；条目字段为 qualityRuleId + status + message；resultCount/passCount/failCount/errors 为单规则执行路径语义，检查点路径保持默认值）。

**运行期（concurrent）幂等（R4.3）**：每次执行生成唯一 `runId`（UUID），结果行写入 `checkpointId`/`runId` 列（`NopMetaQualityResult` 复合 UK `(checkpointId, runId, qualityRuleId)` 兜底拒绝同 runId 重复写行，可空列 NULL 不参与冲突判定——单规则执行路径两列保持 null）。执行入口有 per-checkpoint 运行标记（进程内锁，覆盖 executor + autoScore + dispatchActions 全程）：**同一检查点并发/重复触发时第二次执行显式 fail-fast**（错误码 `checkpoint-already-running`），不静默重复执行、不重复投递 webhook/notify。保留的时序语义：顺序重复执行（间隔超过单次耗时）合法，每次执行 = 新 runId = 新结果行。cron 与手动并发时 cron 侧被拒绝仅记 WARN 日志。跨进程分布式锁不做（单实例 supported baseline）。

**regex 规则方言例外（P2-08，R8.1 收窄）**：regex 规则执行时若目标数据库方言**真实不支持** `REGEXP` 运算符（按方言不支持签名集合匹配：`not supported` / `unknown function` / `syntax error at or near`（PostgreSQL 不支持 REGEXP 运算符的真实签名）），`MetaQualityRuleExecutor.judgeRegex` 返回 **SKIP** 判定 + `LOG.warn` 留证 + `details.reason="regexp-unsupported-dialect"` 标记——这是"无静默跳过"原则下经裁定的显式例外（SKIP 本身是可见结果而非静默跳过，调用方/页面可据此区分"未执行"与"通过"）。**SKIP 仅保留给真实方言不支持场景**：MySQL/H2 等支持 REGEXP 的方言上，规则级正则错误（如非法 pattern，报错消息可能含 "regexp"/"syntax" 字样）显式 **ERROR**（status=ERROR + message），不误判 SKIP——失败规则不得从 pass/fail 统计中静默消失（AR-11 行为收紧）；其余失败路径（SQL 执行失败等）仍显式报 ERROR。

## 多 schema 支持（R4.2）

`NopMetaTable.metaSchema` 记录外部源 schema，使**多 schema 同名外部表可共存**（单模块内同表名不同源 schema 互不冲突）：

- **metaSchema 可空语义**：`metaSchema`（`META_SCHEMA VARCHAR(100)`）为**可空列**——`NULL` 表示默认 schema（entity 表 `OrmModelImporter.buildEntityTable` 与 SQL 表 `NopMetaTableBizModel.createSqlTable` 均保持 null）；external 表由 `upsertExternalTable` 写入实际源 schema，匹配前经 `normalizeSchemaForMatch` 归一化（null/空串/纯空白 → null）。
- **4 列 UK**：`NopMetaTable` 唯一键 `UK_NOP_META_TABLE_MODULE_NAME = (metaModuleId, tableName, isDelta, metaSchema)`（`nop-metadata.orm.xml` `NopMetaTable` unique-key，R4.2 在 R3.19 三列基础上扩展 schema 维度）。租户部署的 UK 变体 `(NOP_TENANT_ID, META_MODULE_ID, TABLE_NAME, IS_DELTA, META_SCHEMA)` 由 xgen 从模型派生再生成（`_add_tenant_nop-metadata.sql`，禁止手编）。
- **存量部署升级 SQL**：非租户存量库（R4.2 前 3 列 UK）需执行 `deploy/sql/{mysql,postgresql,oracle}/upgrade-nop-meta-table-uk.sql`（drop + add 4 列 UK，三方言）。**前置条件**：R3.19 前零 UK 时代建库可能存在 `(metaModuleId, tableName, isDelta)` 重复行，须先去重，否则 `add constraint` 显式失败（fail-fast by design）。新装库由 `_create_nop-metadata.sql` 覆盖，无需 upgrade 脚本。

## API 契约（I*Biz 接口）

每个 BizModel 都实现了对应的 `INopMeta*Biz` 接口（位于 `nop-metadata-dao` 模块的 `io.nop.metadata.biz` 包），声明全部自定义 `@BizQuery` / `@BizMutation` 方法签名。跨模块 `@Inject INopMeta*Biz` 可直接调用接口方法，避免依赖具体实现类。

## 查询分页契约（AR-09 裁定，plan 2026-08-06-0553-3）

`queryTableData` / `queryJoinData` / `queryAggregation` 的 `limit` 语义（两个入口差异为有意裁定）：

- **`queryTableData`**（数据浏览入口）：`limit` 缺省（null/≤0）给默认值 1000，超大 limit 静默封顶（上限 10000 或配置 `nop.metadata.query.max-limit`）——浏览语义下封顶安全。
- **`queryJoinData` / `queryAggregation`**（分析/分页入口）：`limit < 0` → **显式拒绝**（`nop.err.metadata.pagination-limit-invalid`，错误可诊断，不做静默钳制——静默改 limit 会让分页语义静默漂移）；`limit` 缺省（null/0）给默认值 1000（**有界**，不提供"无界"选项）；`limit > Integer.MAX_VALUE` → 截断层显式拒绝（`pagination-limit-too-large`）；`offset` 为 null 或 ≤0 视为不偏移。三条 JOIN 路径（同库 table-table / external↔external / mixed）与跨库内存合并路径语义一致。

## 导入失败路径语义（AR-08 裁定，plan 2026-08-06-0553-3）

`importOrmModel` / `importOrmModels`（NopMetaModule）按 **per-path 独立事务**（REQUIRES_NEW）执行：DB 持久化 + 搜索索引写入 + 变更事件在同一事务单元内——

- **成功**：三态一致提交（DB 行落库 + 索引文档写入 + 事件行写入）。
- **失败**（任一阶段）：内层事务回滚 DB + 已写索引文档反向清理（removeDocs 对账）+ 事件不写入——三态一致回滚，不存在"报失败但数据已提交"的静默分裂；批量路径 per-path 隔离（单路径失败不中断其余路径，结果按路径 success/error 返回）。
- **级联删除索引清理**：`NopMetaModule` / `NopMetaEntity` 删除前收集被级联删除子实体 id，删除后 removeFromIndex（模块：MetaEntity/MetaEntityField/MetaTable；实体：MetaEntity + 其 MetaEntityField），搜索不再返回已删实体。

**例外（Pseudo-BizModel）**：`NopMetaSearchBizModel`（`@BizModel("NopMetaSearch")`，位于 `nop-metadata-service/.../search/`）无对应 `INopMetaSearchBiz` 接口——其搜索索引跨 NopMetaTable / NopMetaEntity / NopMetaEntityField / NopMetaGlossaryTerm 等多实体，无单一对应实体；当前无跨模块调用方（接口 deferred），`rebuildSearchIndex` / `searchMetadata` 两方法仅经 GraphQL 访问。`searchMetadata` 的 `limit` 语义（AR-23④，R8.2）：缺省 null → 20；`limit > 100` → 封顶 100（既有语义保持）；**`limit < 0` → 显式拒绝**（`nop.err.metadata.search-limit-invalid`，不做静默钳制、不直通引擎——沿 AR-09 分页契约先例）。`rebuildSearchIndex` 的 `IndexResult` 中 `refreshBlocking` 失败计入 `failed`（`errors` 含 refresh 信息，`indexed` 如实反映已 addDocs 数，AR-23③）——索引重建失败可观测，不再静默报"成功"。

**items 返回类型合理例外（P2-24）**：`queryTableData` / `queryAggregation` / `queryJoinData` 的返回 `items` 为 `List<Map<String,Object>>`（原始行 Map 列表）而非强类型 DTO——这是经裁定的合理例外：行结构由任意外部源 schema / 用户选择 Measure-Dimension 动态决定，无法预先声明固定 DTO 字段；API 契约仍以 `items` 语义（列名 → 值）对外稳定。

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
| `nop-metadata-api` | DTO 类（`io.nop.metadata.api.dto.*`，31 个 `@DataBean`），供 Biz 接口和跨模块调用契约引用 |
| `nop-metadata-core` | 共享常量（`_NopMetadataCoreConstants`，125 个表/数据源/血缘/质量等枚举常量）——**无 dto 包** |
| `nop-metadata-dao` | ORM 实体 + BizModel 接口（`INopMeta*Biz`）—— Biz 接口因引用 dao.entity.* 类型而驻留在此，不迁至 api |
| `nop-metadata-codegen` | Codegen 生成入口（`nop-metadata/nop-metadata-codegen/src/test/java/io/nop/metadata/codegen/NopMetadataCodeGen.java`）；实际模板在 `nop-metadata-meta/_templates/` |
| `nop-metadata-meta` | xmeta（`_vfs/nop/metadata/model/*/`，78 个）+ dict（`dict/meta/*.dict.yaml`）+ i18n + codegen xgen 脚本（precompile/postcompile）+ `_templates/` |
| `nop-metadata-service` | BizModel 实现 + Executor / Processor / Helper + 全部 xbiz（`_vfs/nop/metadata/model/<Entity>/*.xbiz`） |
| `nop-metadata-web` | 页面（view.xml/page.yaml）+ action-auth + i18n + `_module` 标记（无 beans.xml/xbiz；GraphQL 注册由 service 模块 beans + @BizModel 驱动） |
| `nop-metadata-app` | Quarkus 启动入口（demo 应用） |

### 子模块依赖规则

| 子模块 | 依赖 |
|--------|------|
| `nop-metadata-api` | `nop-api-core`（DTO 基类） |
| `nop-metadata-core` | `nop-metadata-api`、`nop-api-core` |
| `nop-metadata-dao` | `nop-metadata-api`、`nop-api-core`、`nop-orm` |
| `nop-metadata-meta` | 纯模型定义，无 Java 代码依赖 |
| `nop-metadata-service` | `nop-metadata-api`、`nop-metadata-core`、`nop-metadata-dao`、`nop-metadata-meta`、`nop-biz`、框架模块 |
| `nop-metadata-web` | `nop-metadata-service` + Web 入口 |
| `nop-metadata-app` | `nop-metadata-web` + Quarkus 启动器 |

**test-scope 基建依赖（P2-26）**：上表只列 compile 依赖；`nop-metadata-service` 还以 `test` scope 引入基建依赖——`nop-metadata-codegen`（DDL/codegen 验证）、`nop-search-core`（搜索测试）、`nop-job-local`（cron 调度 AutoTest，生产环境由宿主应用提供调度器）、`nop-autotest-junit`（Nop AutoTest）、junit-jupiter(+params)、H2/MySQL 驱动（`localDb` 测试）与 mockito-core。这些依赖不参与运行时装配，仅为测试支撑。

`INopMeta*Biz` 接口驻留在 `nop-metadata-dao` 而非 `nop-metadata-api`，因为这些接口的类型参数引用 `dao.entity.*` 实体类，移入 api 会导致循环依赖（api → dao → api）。

## 失败路径显式化

nop-metadata 严格遵循"无静默跳过"原则（plan 2026-07-19-1250-3 Phase 2 维度09-07）：

- 表不存在 / 数据源不存在 / DISABLED / 非 jdbc 类型 / 不支持的方言 / SQL 解析失败 / 字段引用非法 → **显式抛 `NopException` + ErrorCode**，不静默空集、不伪造值
- **外部表结构扫描故障分类（AR-23⑤，R8.2）**：`syncExternalTables` 扫描外部库时的真实故障（连接中断/权限/元数据访问失败——含 `getDatabaseProductName` / `getTables` 抛 `SQLException`）显式抛 `nop.err.metadata.external-table-scan-failed`（携带**真实** `databaseProductName` + 原始异常消息），与方言不支持（`datasource-type-not-supported`，方言白名单门禁）区分——真实扫描故障不再误报为"方言不支持"；`COLUMN_SIZE` / `DECIMAL_DIGITS` 为 NULL 时 `precision`/`scale` 保留 **JSON null**（不伪造 0，structure JSON 消费方不读这两个字段）
- 批量操作（syncExternalTables / collectCatalog / executeCheckpoint）per-row try/catch 隔离失败 + 收集到 errors 列表，不中断整批
- ErrorCode 已集中到 `NopMetadataErrors.java`，命名前缀 `nop.err.metadata.*`（plan Phase 2 渐进迁移）

## 参考文档

- 平台主文档：`docs-for-ai/03-modules/nop-metadata.md`（本文档）
- I*Biz 接口契约（`nop-metadata-dao` 模块 `io.nop.metadata.biz` 包 `INopMeta*Biz.java`）：每个 BizModel 都有对应接口声明全部自定义方法签名（唯一例外：NopMetaSearchBizModel Pseudo-BizModel 无接口，见上「API 契约」段）
- DTO 规格（`nop-metadata-api/.../dto/`）：31 个 `@DataBean` DTO 类承载 API 返回值强类型契约
- ErrorCode 集中化（`nop-metadata-service/.../NopMetadataErrors.java`）：跨文件去重 + ARG_* 参数常量
- 模块级异常（`NopMetadataException`）：替代 `IllegalArgumentException` / `UnsupportedOperationException` / 裸 `RuntimeException`

> 设计决策、执行计划、修复记录等内部资料位于 `ai-dev/` 目录（按 AGENTS.md 文档分区约定，docs-for-ai 不引用 ai-dev 路径）。
