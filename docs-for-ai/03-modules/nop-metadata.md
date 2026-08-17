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
    unresolved
  }
}

query {
  NopMetaLineageEdge__getImpactAnalysis(metaTableId: "t-1", columnName: "AMOUNT")
}
```

**返回字段语义（P1-3，plan 2026-08-15-1913-2）**：`sourceTables` = **已解析**源表的 **metaTable ID** 集（去重保序；表级/列级为目录命中的源表，指标级 = 宿主表自身 `[metaTableId]`——measure 边为自环，仅当产出 ≥1 条边，0 条边时空列表）；`unresolved` = 未解析引用描述（**异质**：表级为完整表名，列级/指标级为 `"target <- source (reason)"` 诊断串）。两列表互不串入（修复前表级把 unresolved 误植进 sourceTables、列级/指标级恒空）。

### 4. 质量检查点批量执行（含 cron 调度）

```graphql
mutation {
  NopMetaQualityCheckpoint__executeCheckpoint(checkpointId: "cp-1") {
    runId
    totalRuleCount
    executedRuleCount
    skipCount
    ruleResults { qualityRuleId ruleName status actualValue expectedValue message }
    errors { code message detail source refType refValue }
  }
}
```

**执行结果 DTO（AR-14，R8.1）**：检查点路径填充 `totalRuleCount`（= 解析后规则集大小，含异常/SKIP 规则）、`skipCount`（显式 SKIP 计数）与 `ruleResults`（每条规则一个条目，含异常规则补写的 ERROR 条目——`ruleResults` 条目数 = `totalRuleCount`，计数可对账；resultCount/passCount/failCount/errors 为单规则执行路径语义，检查点路径保持默认值）。

**executeCheckpoint 返回值字段清单（P2-20 裁决，plan 2026-08-16-0549-2）**：`CheckpointExecutionResultDTO` 终态字段 = `checkpointId / runId / totalRuleCount / executedRuleCount / passCount / failCount / errorCount / skipCount / affectedTableIds / ruleResults / errors / autoScore / scoreSkipped`。裁决 = **移除**规则结果明细/执行错误明细的两个冗余 `List<Map<String,Object>>` 字段（原形态与类型化字段并存且只写不读），前提为全键类型化承接：

- `ruleResults`（`QualityRuleResultDTO`）增补 `ruleName / actualValue / expectedValue`（承接原 Map 条目的 6 键：qualityRuleId / ruleName / status / actualValue / expectedValue / message——P2-20 前仅 3 键有损投影）；
- `errors`（`ErrorDTO`）增补 `source / refType / refValue`；错误条目四族逐键承接：execution（规则执行异常：source→source、qualityRuleId→code、ruleName→detail、error→message）、resolution（引用解析失败：refType/refValue 同名承接）、autoScore（评分失败：metaTableId→code）、scheduler（调度入口失败：`MetaQualityCheckpointScheduler.buildErrorResult` 直接构造）。`code` 的"错误所涉标识符"语义沿 `NopMetaQualityRuleBizModel`（code=qualityRuleId, detail=ruleName）既有惯例；
- **迁移面结论（显式记录）**：全仓消费面清点 = 零外部消费（web / e2e / xmeta / view / page 零命中，rg 全仓核对 2026-08-16），无可迁移面；GraphQL schema 运行时生成，无静态 schema 文件需同步。契约变更（移除两字段 + DTO 增补承接字段）经字段级等价/损失对照表逐键裁定（对照表见当日 daily log），无未经裁定的信息损失。

**运行期（concurrent）幂等（R4.3）**：每次执行生成唯一 `runId`（UUID），结果行写入 `checkpointId`/`runId` 列（`NopMetaQualityResult` 复合 UK `(checkpointId, runId, qualityRuleId)` 兜底拒绝同 runId 重复写行，可空列 NULL 不参与冲突判定——单规则执行路径两列保持 null）。执行入口有 per-checkpoint 运行标记（进程内锁，覆盖 executor + autoScore + dispatchActions 全程）：**同一检查点并发/重复触发时第二次执行显式 fail-fast**（错误码 `checkpoint-already-running`），不静默重复执行、不重复投递 webhook/notify。保留的时序语义：顺序重复执行（间隔超过单次耗时）合法，每次执行 = 新 runId = 新结果行。cron 与手动并发时 cron 侧被拒绝仅记 WARN 日志。跨进程分布式锁不做（单实例 supported baseline）。

**regex 规则方言例外（P2-08，R8.1 收窄）**：regex 规则执行时若目标数据库方言**真实不支持** `REGEXP` 运算符（按方言不支持签名集合匹配：`not supported` / `unknown function` / `syntax error at or near`（PostgreSQL 不支持 REGEXP 运算符的真实签名）），`MetaQualityRuleExecutor.judgeRegex` 返回 **SKIP** 判定 + `LOG.warn` 留证 + `details.reason="regexp-unsupported-dialect"` 标记——这是"无静默跳过"原则下经裁定的显式例外（SKIP 本身是可见结果而非静默跳过，调用方/页面可据此区分"未执行"与"通过"）。**SKIP 仅保留给真实方言不支持场景**：MySQL/H2 等支持 REGEXP 的方言上，规则级正则错误（如非法 pattern，报错消息可能含 "regexp"/"syntax" 字样）显式 **ERROR**（status=ERROR + message），不误判 SKIP——失败规则不得从 pass/fail 统计中静默消失（AR-11 行为收紧）；其余失败路径（SQL 执行失败等）仍显式报 ERROR。

## 多 schema 支持（R4.2）

`NopMetaTable.metaSchema` 记录外部源 schema，使**多 schema 同名外部表可共存**（单模块内同表名不同源 schema 互不冲突）：

- **metaSchema 可空语义**：`metaSchema`（`META_SCHEMA VARCHAR(100)`）为**可空列**——`NULL` 表示默认 schema（entity 表 `OrmModelImporter.buildEntityTable` 与 SQL 表 `NopMetaTableBizModel.createSqlTable` 均保持 null）；external 表由 `upsertExternalTable` 写入实际源 schema，匹配前经 `normalizeSchemaForMatch` 归一化（null/空串/纯空白 → null）。
- **4 列 UK**：`NopMetaTable` 唯一键 `UK_NOP_META_TABLE_MODULE_NAME = (metaModuleId, tableName, isDelta, metaSchema)`（`nop-metadata.orm.xml` `NopMetaTable` unique-key，R4.2 在 R3.19 三列基础上扩展 schema 维度）。租户部署的 UK 变体 `(NOP_TENANT_ID, META_MODULE_ID, TABLE_NAME, IS_DELTA, META_SCHEMA)` 由 xgen 从模型派生再生成（`_add_tenant_nop-metadata.sql`，禁止手编）。
- **存量部署升级 SQL**：非租户存量库（R4.2 前 3 列 UK）需执行 `deploy/sql/{mysql,postgresql,oracle}/upgrade-nop-meta-table-uk.sql`（drop + add 4 列 UK，三方言）。**前置条件**：R3.19 前零 UK 时代建库可能存在 `(metaModuleId, tableName, isDelta)` 重复行，须先去重，否则 `add constraint` 显式失败（fail-fast by design）。新装库由 `_create_nop-metadata.sql` 覆盖，无需 upgrade 脚本。

## 语义层 FQN 唯一性与 NULL-distinct 守卫（plan 2026-08-16-0920-1）

**FQN 全局唯一（P2-29 裁定）**：`NopMetaGlossaryTerm` / `NopMetaTag` 的 `fullyQualifiedName` 各自只有**全局 UK**（`UK_NOP_META_GLOSSARY_TERM_FQN` / `UK_NOP_META_TAG_FQN`）——非 NULL FQN 全局唯一蕴含 per-scope 唯一，原 per-scope UK（`..._G_FQN` / `..._CLS_FQN`）为逻辑被蕴含的冗余约束，已删除（行为零变化：删除前被拒的写入删除后仍被拒）。GlossaryTerm FQN 可空（多行 NULL FQN 共存，NULL-distinct）；Tag FQN mandatory 且自动构建带 scope 前缀（`NopMetaTagBizModel.save` 仅在 null 时填充，手填非空 FQN 可绕过前缀但仍受全局唯一约束）。**存量库升级**：执行 `deploy/sql/{mysql,postgresql,oracle}/upgrade-nop-meta-fqn-uk.sql`（DROP 2 个 per-scope 约束，无数据前置条件）。

**TagLabel GLOSSARY 查重守卫（P2-01 裁定选项 ii）**：`UK_NOP_META_TAG_LABEL (entityType,entityId,tagId,source)` 对 `source=Glossary && tagId=NULL` 的行不生效（复合 UK 任一列 NULL 即豁免）。`NopMetaTagLabelBizModel` 在 save/update 之后按 `(entityType, entityId, source, glossaryTermId, tagId IS NULL)` 查重（自排除），命中抛 `nop.err.metadata.tag-label-duplicate-glossary-term` 并回滚；tagId 非 NULL 的行（含 GLOSSARY source）继续由 DB UK 拒绝。UK 保持不变（裸扩列与哨兵方案经 NULL-distinct/Oracle ''≡NULL 分析否决，见 plan 裁决表）。

**BusinessDomain 根域重名守卫（P2-28 裁定）**：`UK_NOP_META_BUSINESS_DOMAIN_PARENT_NAME (parentDomainId,name)` 对根域（parentDomainId NULL）不生效。`NopMetaBusinessDomainBizModel` 在 save/update 之后对根域按 `(parentDomainId IS NULL, name)` 查重（自排除），命中抛 `nop.err.metadata.business-domain-duplicate-root-name` 并回滚；非根域由 DB UK 拒绝。UK 与列语义均不变（sentinel 改造否决理由见 plan 裁决表）。

## 状态机字段写路径分层契约（plan 2026-08-16-0920-2）

**契约**：TagLabel / DataContract / QualityResult 三实体的 5 个状态机字段（`NopMetaTagLabel.state/approveStatus`、`NopMetaDataContract.status/approveStatus`、`NopMetaQualityResult.status`）**只经专用路径变更**，GraphQL `__update` 输入面不收（delta xmeta props override `updatable="false"`）：

- **专用路径**（唯一合法翻转通道，`.xbiz`/Java 实体直写不经 InputBean 校验，不受收紧影响）：审批动作 `approval-support.xbiz`（submitForApproval/withdrawApproval/approve/reject/reverseApprove）+ 各实体 delta xbiz 的 approve/reject（含 DataContract status 生命周期 DRAFT→ACTIVE→DEPRECATED→RETIRED 与 TagLabel state Confirmed/Suggested）+ `NopMetaTagLabelBizModel` save 缺省注入 + 执行/re-judge 引擎（`QualityResultWriter` insert / `QualityAlertWorkflowProcessor.reJudge`）。
- **运行时语义**：`__update` 对这些字段是**静默丢弃**（`ObjMetaBasedValidator.validateForUpdate` 按 `updatable` 过滤，非 updatable prop 跳过），不抛错；同请求内其他可更新字段正常生效。断言按「值不变/输入面不收」设计（不得写成抛错拒绝）。
- **insert 面保留**（逐字段 Why-Not，详见 plan 裁决表）：3 个 ORM-mandatory 字段（QualityResult.status / DataContract.status / TagLabel.state）无 default 需创建面供值；TagLabel.state 的 BizModel 内部缺省注入本身经 insert 面落库；approveStatus×2 创建时携带是 `submitForApproval` 守卫语义输入的一部分。
- **ORM 层不可收紧的机制事实**：所有合法状态写入都走标准实体更新 flush——ORM 列 `updatable="false"` 会在 flush 时抛 `ERR_ORM_ENTITY_PROP_NOT_UPDATABLE` 打断全部审批翻转，不是可用机制。
- 回归测试：`TestNopMetaStateFieldGuard`（双路径 8 例：`__update` 静默丢弃 + 专用路径仍翻转 + P2-26 删除保留/关系可查询；变异验证 = 移除 state override 即红）。

**checkpointId 语义（P2-26 裁定）**：`NopMetaQualityResult.checkpointId` 为显式化的 soft-FK 弱引用（结果侧 `checkpoint` to-one + 检查点侧 `qualityResults` to-many，**无 cascadeDelete、无 DB 级 FOREIGN KEY**）。**检查点删除后结果行无条件保留**（时序历史事实语义，孤儿行为是裁定后的显式行为）；与 `qualityRule→results` 的级联（规则拥有其结果）刻意不对称——检查点是执行配置，结果是历史事实。唯一写位点 = `QualityResultWriter`（insert-only）。

## API 契约（I*Biz 接口）

每个 BizModel 都实现了对应的 `INopMeta*Biz` 接口（位于 `nop-metadata-dao` 模块的 `io.nop.metadata.biz` 包），声明全部自定义 `@BizQuery` / `@BizMutation` 方法签名。跨模块 `@Inject INopMeta*Biz` 可直接调用接口方法，避免依赖具体实现类。

## 查询分页契约（AR-09 裁定，plan 2026-08-06-0553-3）

`queryTableData` / `queryJoinData` / `queryAggregation` 的 `limit` 语义（负值统一显式拒绝 INV-LIMIT，正值上限差异为有意裁定）：

- **`queryTableData`**（数据浏览入口）：`limit < 0` → **显式拒绝**（`nop.err.metadata.pagination-limit-invalid`，INV-LIMIT）；`limit` 缺省（null/0）给默认值 1000，超大正值静默封顶（上限 10000 或配置 `nop.metadata.query.max-limit`）——浏览语义下正值封顶安全（MA7.4-03）。
- **`queryJoinData` / `queryAggregation`**（分析/分页入口）：`limit < 0` → **显式拒绝**（`nop.err.metadata.pagination-limit-invalid`，错误可诊断，不做静默钳制——静默改 limit 会让分页语义静默漂移）；`limit` 缺省（null/0）给默认值 1000（**有界**，不提供"无界"选项）；`limit > Integer.MAX_VALUE` → 截断层显式拒绝（`pagination-limit-too-large`）；`offset` 为 null 或 ≤0 视为不偏移。三条 JOIN 路径（同库 table-table / external↔external / mixed）与跨库内存合并路径语义一致。

## 导入失败路径语义（AR-08 裁定，plan 2026-08-06-0553-3）

`importOrmModel` / `importOrmModels`（NopMetaModule）按 **per-path 独立事务**（REQUIRES_NEW）执行：DB 持久化 + 搜索索引写入 + 变更事件在同一事务单元内——

- **成功**：三态一致提交（DB 行落库 + 索引文档写入 + 事件行写入）。
- **失败**（任一阶段）：内层事务回滚 DB + 已写索引文档反向清理（removeDocs 对账）+ 事件不写入——三态一致回滚，不存在"报失败但数据已提交"的静默分裂；批量路径 per-path 隔离（单路径失败不中断其余路径，结果按路径 success/error 返回）。
- **级联删除索引清理**：`NopMetaModule` / `NopMetaEntity` 删除前收集被级联删除子实体 id，删除后 removeFromIndex（模块：MetaEntity/MetaEntityField/MetaTable；实体：MetaEntity + 其 MetaEntityField），搜索不再返回已删实体。

**例外（Pseudo-BizModel）**：`NopMetaSearchBizModel`（`@BizModel("NopMetaSearch")`，位于 `nop-metadata-service/.../search/`）无对应 `INopMetaSearchBiz` 接口——其搜索索引跨 NopMetaTable / NopMetaEntity / NopMetaEntityField / NopMetaGlossaryTerm 等多实体，无单一对应实体；当前无跨模块调用方（接口 deferred），`rebuildSearchIndex` / `searchMetadata` 两方法仅经 GraphQL 访问。`searchMetadata` 的 `limit` 语义（AR-23④，R8.2）：缺省 null → 20；`limit > 100` → 封顶 100（既有语义保持）；**`limit < 0` → 显式拒绝**（`nop.err.metadata.search-limit-invalid`，不做静默钳制、不直通引擎——沿 AR-09 分页契约先例）。`rebuildSearchIndex` 的 `IndexResult` 中 `refreshBlocking` 失败计入 `failed`（`errors` 含 refresh 信息，`indexed` 如实反映已 addDocs 数，AR-23③）——索引重建失败可观测，不再静默报"成功"。

**items 返回类型合理例外（P2-24）**：`queryTableData` / `queryAggregation` / `queryJoinData` 的返回 `items` 为 `List<Map<String,Object>>`（原始行 Map 列表）而非强类型 DTO——这是经裁定的合理例外：行结构由任意外部源 schema / 用户选择 Measure-Dimension 动态决定，无法预先声明固定 DTO 字段；API 契约仍以 `items` 语义（列名 → 值）对外稳定。

**`selection` 参数显式 no-op（F4，plan 2026-08-14-0707-2）**：`queryTableData` / `queryJoinData` / `queryAggregation` 声明的 `FieldSelectionBean selection` 参数由 GraphQL 引擎（`ReflectionBizModelBuilder`）自动注入**响应字段选择集**（DTO 级，如 `{ tableType items }`）——它**不是**调用方显式传入的行列过滤规范。由于结果为不透明 `List<Map<String,Object>>`（GraphQL 无法对 Map 行做字段级选择），`selection` 在此三方法为**显式 no-op**：行的所有列原样返回，不做基于 selection 的 key 过滤。这与 `CrudBizModel`（结果为 ORM 实体，selection 经 `fetchResultWithSelection` 驱动字段装载）语义不同。该 no-op 地位已在方法 Javadoc 与回归测试中显式声明，不再"静默接受又丢弃"。与 P2-24 carve-out 边界：P2-24 覆盖 `items` 返回类型（List<Map>），F4 覆盖 `selection` 参数语义（no-op），两者正交。若未来需要行列裁剪，应新增显式 `fields` 参数。

**@RequestBean 参数数例外裁定（P2-25，plan 2026-08-16-0226-3）**：`queryJoinData`（6 个 `@Name` 参数）/ `queryAggregation`（10 个 `@Name` 参数）超出 `docs-for-ai/02-core-guides/service-layer.md`「参数与返回值」的 1–5 参数规则（超过 5 个参数用 `@RequestBean` + `@DataBean` DTO），为**显式裁定例外，签名保持不迁移**：两方法签名是已发布的对外 GraphQL 契约（AR-09 分页裁定 / F4 selection no-op 裁定的落点方法，`limit` 负值拒绝、HAVING 白名单、orderBy 语义等拒绝点固定在 BizModel 入口参数上），迁移 @RequestBean 会改变对外 GraphQL 参数形态、破坏契约兼容性。该规则 owner 文档（service-layer.md）无既有"裁定例外"登记机制可复用，故在本模块 owner doc 局部登记（即本段）。

**SLA 契约：分数 amount 与非法值语义（AR-01/AR-02，plan 2026-08-14-0707-2）**：`MetaContractChecker.toDurationMillis` 把 SLA `{interval/value, unit}` 结构归一为毫秒——
- **分数 amount**（AR-01）：先按 `double` 换算毫秒（`amount * unitMillis`）再取整，**不**在单位级 `(long)amount` 截断。`{"interval":0.5,"unit":"hour"}` → 1_800_000 ms（非 0）；`0.5w` → 3.5 day 的毫秒（非 0）。覆盖通用路径与 week 分支两条换算路径。
- **非数字 amount**（AR-02）：不可解析的 amount（如非数字 String `"oops"`）映射 `nop.err.metadata.contract-sla-invalid`（带 `contractId` + 错误值），**不**逃逸裸 `NumberFormatException`。与 AR-22（未知 unit fail-fast）共同保证：未知 unit 与不可解析 amount 均显式失败，无静默跳过。

**cross-DB 内存聚合 group-key 语义（AR-03，plan 2026-08-14-0707-2）**：`AggregationHelper.memoryGroupBy`（cross-DB 内存聚合）用**结构性 key**（值级 `equals`/`hashCode` 的 `List<Object>`）分组，不再用分隔符（`\u0001`）拼接 String key + null 哨兵（`\u0000`）。这消除了控制字符入参导致的分组碰撞：`("a","\u0001b")` 与 `("a\u0001","b")` 产出两个不同分组；`null` 与字面量 `"\u0000"` 不碰撞。内存路径与 SQL 路径（SQL GROUP BY 天然按值分组）对含控制字符的脏数据产出一致（R8.3 不变式）。

**cross-DB 内存聚合数值精度语义（AR-10，plan 2026-08-14-1133-2；比较路径对齐：Cycle 2 E1/E2，plan 2026-08-15-0820-3）**：`AggregationHelper.toBigDecimal`（`SumAcc`/`AvgAcc` 累加时的统一数值归一）按入参类型分派无损精度——**整数类型**（`Long`/`Integer`/`Short`/`Byte`/`AtomicLong`/`AtomicInteger`）走 `BigDecimal.valueOf(longValue())`（Long > 2^53 经 `doubleValue()` 会丢低位）；**浮点类型**（`Float`/`Double`）保持 `doubleValue()`（小数不截断）；`BigInteger`/`BigDecimal` 原有无损分支不变。**String 数值**（部分 JDBC driver 以 String 交付数值）走 trim 后 `new BigDecimal(s)`（解析失败 → null + DEBUG 信号），不再直接 return null 被 `SumAcc` 的 `if(n!=null)` 静默跳过——String 数值列 SUM/AVG 不再静默为 null。非数值 String 仍返回 null（不抛异常打断聚合）。**比较路径同语义（E1/E2）**：`MemoryOrderByComparator`（ORDER BY）与 `MemoryFilterEvaluator`（WHERE 内存过滤）的私有 `toBigDecimal` 拷贝已改为委托 `AggregationHelper.toBigDecimal`——Long > 2^53 的等值过滤不再因 doubleValue 塌缩错配结果行、超精度长整型排序不再并列，String 数值字面量/行值按数值参与比较（旧 optimization-candidate 裁定已被 Cycle 2 裁决推翻：静默错算家族以"是否存在静默错算"为判据，不以影响面为判据）。

**lineage/manifest/reconciliation 正确性语义（AR-07/08/09/11/12/13，plan 2026-08-14-1133-3）**：
- **AR-07 血缘源表按 full 去重**：`SqlSourceTableExtractor.extract` 按 `fullName`（schema-qualified 名）去重——跨 schema 同名表（`dbo.users` 与 `sales.users`）的 fullName 不同，各自独立产出一条边，不再因 simpleName 相同而塌缩。残留歧义：同一 fullName 经不同别名引用仍为一条边（语义正确——指向同一物理表）。
- **AR-08 CTE 名排除**：WITH 子句声明的 CTE 名被预收集并排除——匹配 CTE 名的 `SqlSingleTableSource` 不作为物理源表上报（符合 SQL 作用域语义：CTE 名遮蔽同名物理表时，CTE 名被排除，物理表引用不补回）。CTE 体内部的源表仍被正常收集。
- **AR-09 manifest 邻接表去重 + 自环过滤**：`MetaManifestBuilder` 的 `addEdge` 追加前检查 `!list.contains(value)`（重复关系不产重复邻居）+ 自环过滤（`key.equals(value)` 即 owner==target 不追加）。`parentMap` 与 `childMap` 双向邻接表均去重 + 自环过滤——重复 `NopMetaEntityRelation` 行不再膨胀图度数。
- **AR-11 视图推断尾分号处理**：`SqlViewFieldTypeInferrer.inferWithinConnection` 包装前 `trim()` + 循环剥尾分号（`while (endsWith(";")) strip+trim`）——sourceSql 带尾分号（`SELECT a FROM t;`）时内层子查询 `SELECT * FROM (SELECT a FROM t) _t LIMIT 0` 不再被 JDBC 驱动拒绝。
- **AR-12 reconciliation locale-insensitive**：`LocalReconciliationProcessor.levenshteinSimilarity` 使用 `toLowerCase(Locale.ROOT)`——默认 locale（如 tr-TR）下 `"I".toLowerCase()` → `"ı"` 不再导致同一数据在不同 JVM locale 下匹配结果不同。与精确路径（`equalsIgnoreCase` 天然 locale-insensitive）一致。
- **INV-LOCALE 机器比较语义全量 locale-insensitive（Cycle 2 / I4'，plan 2026-08-15-0820-3）**：模块内全部机器比较用途的 case-mapping（registry/集合键归一化、白名单/blocklist token 比对、类型/方言分类、配置 token 匹配、结构化 extras token）统一 `Locale.ROOT`（40 处类别清扫，含 2 处安全语义缺陷：JDBC URL 危险参数 blocklist 与 custom_sql sandbox 关键字扫描在 tr-TR 默认 locale 下的注入探测绕过）。防回退门禁：`check-silent-wrong-result.mjs --rule locale`（零命中阻断）。同批：`MetaTableProfiler.isStringType` 改 exact-match `Set.of`（沿 AR-05 形态，消除与 `isNumericType` 的同族双标准）；列级血缘边去重键 / existing-edge map 键与 auto-classification warn 去重键改结构性 `List` 键（沿 AR-03 形态，不依赖列名/正则 pattern 不含分隔符的格式假设）。
- **AR-13 reconciliation 候选有界**：`ReconciliationExecutor.execute` 传入默认 `DEFAULT_CANDIDATE_LIMIT = 50`（非 null）——所有 fuzzy 候选（score > 阈值）经 `LocalReconciliationProcessor.reconcile` 截断为 ≤50，details JSON 候选序列化有界，大候选池下不会 OOM/多秒序列化。config-driven limit（extConfig JSON 键或新增 ORM 列）为 Non-Blocking Follow-up（含 ORM Protected Area 风险）。

全部 14 个非空 I*Biz 接口（plan 2026-07-19-1250-3 Phase 1 补齐 9 个；P1-2（plan 2026-08-15-1913-2）补齐其余 5 个——与 `nop-metadata-dao` `io.nop.metadata.biz` 包 live 接口逐一核对；P2-17（plan 2026-08-16-0549-2）起本清单由 `TestNopMetaBizInterfaceCompleteness` 程序化全集守卫钉死——文件系统扫描 biz 包源目录 + 反射比对方法集，新增非空接口/新增自定义方法未登记即红；守卫只覆盖驻留本包的 I*Biz，接口移包属结构性变更需同步守卫）：

- `INopMetaTableBiz` — profileTable / createSqlTable / previewSqlFields / resolveTableFields / queryTableData / queryJoinData / queryAggregation
- `INopMetaDataSourceBiz` — testConnection / syncExternalTables / collectCatalog / collectCatalogForTable
- `INopMetaModuleBiz` — importOrmModel / importOrmModels / releaseModule / generateManifest
- `INopMetaLineageEdgeBiz` — recordLineage / extractLineageFromSql / extractColumnLineageFromSql / extractMeasureLineage / getUpstream / getDownstream / getLineagePath / getImpactAnalysis
- `INopMetaQualityRuleBiz` — executeQualityRule / executeQualityRulesForDataSource / judgeByRuleId
- `INopMetaQualityCheckpointBiz` — executeCheckpoint
- `INopMetaQualityScoreBiz` — computeQualityScore
- `INopMetaDataContractBiz` — checkContract / checkContractReadOnly
- `INopMetaProfilingRuleBiz` — executeProfilingRule
- `INopMetaDataProductBiz` — linkAsset / unlinkAsset / getLinkedAssets
- `INopMetaQualityResultBiz` — approve / reject
- `INopMetaReconciliationConfigBiz` — executeReconciliation
- `INopMetaReconciliationResultBiz` — confirmMatch / batchConfirmMatches
- `INopMetaTagLabelBiz` — propagateTags / suggestTags

**DataProduct 挂链语义（P1-4/P1-5，plan 2026-08-15-1913-2）**：`linkAsset`/`unlinkAsset` 入口对 `dataProductId` 做 `requireEntity` 聚合根校验——不存在/伪造 ID 抛 `nop.err.dao.unknown-entity`（聚合根层），与"存在产品但无标签"的 `nop.err.metadata.link-asset-not-found`（标签层）语义区分；`entityId` 由 `LINKABLE_ASSET_TYPES` 白名单治理 entityType、保持不透明资产引用（TagLabel 通用标注语义）。`linkAsset` 跨聚合创建走 TagLabel 属主 save 管线（`bizObject invoke("save")`，沿 GlossaryTerm 传播先例）——Automated 标签 `state=Suggested` + 自动提审（`wf:wfName=tagLabelConfirmApproval`，fail-loud，匿名上下文显式失败）。

**`NopMetaTagLabel.xbiz` approve/reject XPL 事实源**：`INopMetaTagLabelBiz` 接口只声明 Java 侧自定义方法（propagateTags/suggestTags）；approve / reject / submitForApproval / withdrawApproval 由 `NopMetaTagLabel.xbiz`（extends `/nop/wf/base/approval-support.xbiz`）以 XPL 脚本提供，是该 biz 对象 GraphQL mutation 的事实源（不在 Java BizModel / I*Biz 接口中）。

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
| `nop-metadata-api` | DTO 类（`io.nop.metadata.api.dto.*`，30 个 `@DataBean`），供 Biz 接口和跨模块调用契约引用 |
| `nop-metadata-core` | 共享常量（`_NopMetadataCoreConstants`，125 个表/数据源/血缘/质量等枚举常量）——**无 dto 包** |
| `nop-metadata-dao` | ORM 实体 + BizModel 接口（`INopMeta*Biz`）+ 模型载入/映射（`dao/model/OrmModelImporter`——ORM 模型导入映射器，驻留裁定见下）—— Biz 接口因引用 dao.entity.* 类型而驻留在此，不迁至 api |
| `nop-metadata-codegen` | Codegen 生成入口（`nop-metadata/nop-metadata-codegen/src/test/java/io/nop/metadata/codegen/NopMetadataCodeGen.java`）；实际模板在 `nop-metadata-meta/_templates/` |
| `nop-metadata-meta` | xmeta（`_vfs/nop/metadata/model/*/`，78 个）+ dict（`dict/meta/*.dict.yaml`）+ i18n + codegen xgen 脚本（precompile/postcompile）+ `_templates/` |
| `nop-metadata-service` | BizModel 实现 + Executor / Processor / Helper + 全部 xbiz（`_vfs/nop/metadata/model/<Entity>/*.xbiz`）+ NopMetaSearch xmeta（`_vfs/nop/metadata/model/NopMetaSearch/NopMetaSearch.xmeta`——Pseudo-BizModel 无对应 ORM 实体，live 无 NopMetaSearch.xwf）+ 3 个审批流定义（`_vfs/nop/wf/metaDataContractApproval/v1.xwf` / `_vfs/nop/wf/qualityBreachApproval/v1.xwf` / `_vfs/nop/wf/tagLabelConfirmApproval/v1.xwf`，分别为 DataContract / QualityBreach / TagLabelConfirm 提审审批） |
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

**test-scope 基建依赖（P2-26 + P2-35 裁定）**：上表只列 compile 依赖；`nop-metadata-service` 还以 `test` scope 引入基建依赖——`nop-metadata-codegen`（DDL/codegen 验证）、`nop-search-core`（搜索测试）、`nop-search-lucene`（搜索测试的默认 impl，经运行时 SPI 到达；main 代码只依赖 `io.nop.search.api` 抽象，零 lucene import——**生产部署需要搜索功能时由宿主应用显式引入 `nop-search-lucene` 或替换为其他 `nop-search-*` impl**，模块自身不传递任何搜索实现）、`nop-job-local`（cron 调度 AutoTest，生产环境由宿主应用提供调度器）、`nop-autotest-junit`（Nop AutoTest）、junit-jupiter(+params)、H2/MySQL 驱动（`localDb` 测试）与 mockito-core。这些依赖不参与运行时装配，仅为测试支撑。

`INopMeta*Biz` 接口驻留在 `nop-metadata-dao` 而非 `nop-metadata-api`，因为这些接口的类型参数引用 `dao.entity.*` 实体类，移入 api 会导致循环依赖（api → dao → api）。

**`OrmModelImporter` 驻留 dao 裁定（P2-03，plan 2026-08-16-0549-3 Phase 3）**：`nop-metadata-dao` 的 `dao/model/OrmModelImporter`（ORM 模型 → 元数据行的映射器）**维持 dao 驻留**。依赖方向论据：service 依赖 dao，模型载入/映射属 dao 载入域——载入器驻留 dao 的同形先例 = `nop-wf-dao` 的 `dao/store/DaoWorkflowModelLoader`（工作流模型载入器驻留 dao）；`nop-auth-dao` 不作同形先例引用（无严格同形物，最近邻仅为 `dao/mapper` 的 SqlLibMapper 空接口）。审计发现的真实缺口为文档侧（模块结构表此前漏列 `model/` 子包），非代码迁移需求。

## 失败路径显式化

nop-metadata 严格遵循"无静默跳过"原则（plan 2026-07-19-1250-3 Phase 2 维度09-07）：

- 表不存在 / 数据源不存在 / DISABLED / 非 jdbc 类型 / 不支持的方言 / SQL 解析失败 / 字段引用非法 → **显式抛 `NopException` + ErrorCode**，不静默空集、不伪造值
- **外部表结构扫描故障分类（AR-23⑤，R8.2）**：`syncExternalTables` 扫描外部库时的真实故障（连接中断/权限/元数据访问失败——含 `getDatabaseProductName` / `getTables` 抛 `SQLException`）显式抛 `nop.err.metadata.external-table-scan-failed`（携带**真实** `databaseProductName` + 原始异常消息），与方言不支持（`datasource-type-not-supported`，方言白名单门禁）区分——真实扫描故障不再误报为"方言不支持"；`COLUMN_SIZE` / `DECIMAL_DIGITS` 为 NULL 时 `precision`/`scale` 保留 **JSON null**（不伪造 0，structure JSON 消费方不读这两个字段）
- 批量操作（syncExternalTables / collectCatalog / executeCheckpoint）per-row try/catch 隔离失败 + 收集到 errors 列表，不中断整批
- **syncExternalTables 原子性契约（AR-17，R8.4b）**：`syncExternalTables` 是**部分持久化**语义（非全量原子）——每表 upsert 在 per-key 锁 + `REQUIRES_NEW` 独立事务内独立提交（R6.3 裁定，plan-2026-08-05-2157-3）：scan 中途失败或单表失败时**已同步表保持持久化**（不整体回滚），失败表记入 `errors` 且不中断整批；**scan 级失败**（`structureReader.read` 抛 / 连接中断）异常向上传播（fail-loud），且失败路径**仍发布**变更事件（`NopMetaModelChangedEvent`，changeSource=SYNC）——事件行经 `REQUIRES_NEW` 独立事务提交（沿每表 upsert 先例），不随外层事务回滚消失；事件价值是"sync 尝试发生 + 已部分持久化"的下游通知（dataSource 实体在 sync 期间不变，before/after 快照等同，非实体 diff）
- ErrorCode 已集中到 `NopMetadataErrors.java`，命名前缀 `nop.err.metadata.*`（plan Phase 2 渐进迁移）
- **错误消息识别性参数一致性（INV-ERROR-PARAM，plan 2026-08-15-1913-3 + 2026-08-16-0226-2）**：throw 点所用 ErrorCode 描述声明的识别性占位符 `{xxx}` 必须有对应 `.param()` 键（字面量或 `NopMetadataErrors.ARG_X`）——缺键/键错配时运行时渲染字面 `{xxx}`，失败对象身份对用户丢失。null 防御/值语义不存在分支用无必需占位符的错误码（不削既有码占位符——其他 throw 点可能已传齐）；禁止传 null 凑键覆盖（渲染空串空壳）。**define 声明面一致性（P2-10）**：`*Errors.java` 中每个 `ErrorCode.define` 尾部声明的 ARG_* 值集合必须与描述占位符集合完全一致（对称差为空，比对经 ARG 常量**值**解析，禁止常量名比对）；define 在 src/main 零引用（或仅测试引用）即死码，须删除或 `// invariant-ok:` 显式裁定。CI 门禁：`ai-dev/tools/run-nop-metadata-invariants.sh` guard 6（零命中 hard-gate；变量形态错误码须 `// invariant-ok:` 人工归类标注；`{error}` 附注占位符豁免已于 2026-08-16 收口——`-- {error}` 描述族 throw 点必须传 `.param(ARG_ERROR, …)`，缺参即红）

## 安全契约（攻击面闭环）

nop-metadata 对三处安全敏感路径维持 fail-closed / 默认脱敏 / fail-fast 契约：

- **HAVING SQL 注入防御（F1，plan 2026-08-14-0707-1）**：`MetaAggregationExecutor.preprocessHavingArithmetic` 在递归入口对每个 having 叶子**清除**客户端可伪造的 `havingExprResolved` 标记（`TreeBean.createFromJson` 把任意 JSON key 写为 attr，故该标记可被伪造），仅经 `expr` 路径逐 token 白名单校验后重新置位。`AggregationHelper.nameResolverFor` 仅对带合法标记的叶子允许直通拼接；未带标记 / 伪造标记的原始用户 name 一律抛 `ERR_AGGR_HAVING_UNKNOWN_NAME`（fail-fast），禁止原样进入 HAVING SQL。
- **JDBC URL 主机校验 / SSRF 防御（F2，plan 2026-08-14-0707-1；F2 再审计补全，plan 2026-08-15-1913-1）**：`MetaDataSourceConnectionProcessor.validateJdbcUrl` 对 URL 中**每一**主机（含逗号分隔多主机 `jdbc:mysql://h1,h2/db` 与 MySQL Connector/J `address=(host=...)` / `(host=...,port=...)` 形式）执行 `HostSecurityUtil.isInternalHost`，任一为内网且未加白即抛 `ERR_DATASOURCE_JDBC_URL_BLOCKED`（fail-closed 默认禁内网，覆盖 RFC1918 + link-local + loopback + IP 记法变体）。多主机路径不静默放行内网第二主机。F2 再审计补全两个驱动语义级绕过的处置：
  - **query 参数主机参与逐主机校验**：query 中的 `host=`/`hostaddr=` 参数主机（对齐 pgjdbc `Driver.parseURL` 语义：参数值 percent-decode 后校验、参数名大小写不敏感、重复参数逐值校验、单值逗号分隔逐值校验）与 authority 主机合并纳入同一逐主机内网校验——pgjdbc 中 query `host=` 完全覆盖 authority 主机，不提取即整层校验被绕过。空值/裸 token（`?host=` / `?host`）= 驱动回落默认主机（localhost），同样 fail-closed。
  - **hostless 属性组 fail-closed**：含 `=`/`(`/`)` 却无 `host=` 键命中的属性组段（如 `(port=3306)`、`address=(port=3306)`）= 驱动隐式 localhost（MySQL Connector/J hostless 属性组默认 localhost），显式拒绝（reason 标识 hostless）。误伤面（接受）：键匹配大小写敏感，`(HOST=...)` 段无键命中同样按 hostless 拒绝——驱动侧键大小写不敏感，fail-closed 方向安全；authority 中合法的 `(host=good.com,port=3306)` 形态不受影响（`host=` 键命中正常提取）。
- **JDBC URL 危险参数 blocklist 完备（F5，plan 2026-08-14-1133-1）**：`MetaDataSourceConnectionProcessor.DANGEROUS_URL_TOKENS` 在 AR-02 原 13 个 token 之上补全触发反射类加载的参数族（RCE-chain 潜力，取决于部署 classpath）：MySQL `socketfactory`/`statementinterceptors`/`detectcustomcollatz`，PostgreSQL `sslfactory`（SSL SocketFactory 反射实例化）与 `options=`（向 backend 透传命令行风格选项）。`driverClassName` 白名单缓解但不能替代 URL 参数侧的 fail-closed——含上述参数的 URL 在 `validateJdbcUrl` 危险参数检查阶段（主机校验之前）即 fail-fast `ERR_DATASOURCE_JDBC_URL_BLOCKED`。属 defense-in-depth over-block 哲学（`options=` 等可致 `autoOptions=...` 类合法参数被误拒，与既有 `SET`/`init=` 等 over-block 一致）。
- **凭证脱敏含 `@` 口令不再泄漏（F6，plan 2026-08-14-1133-1）**：`redactJdbcUrl` 改为与 `extractHosts` 一致——定位 authority 段（`://` 后到首个 `/` 或 `?`）后用 `lastIndexOf('@')` 找 userinfo 边界并 substring-replace userinfo 段。修复前 `CREDENTIAL_PATTERN` 正则在第一个 `@` 处停止，含 `@` 的口令尾部泄漏进 redacted 错误消息（`user:p@ss@host` → `ss@host` 泄漏）。现在 `user:p@ss@host:3306/db` → `host:3306/db`，口令片段不再出现在任何 ErrorCode 参数或消息中。
- **驱动异常消息进 error param 前经 URL 脱敏（P2-08，plan 2026-08-16-0226-1）**：驱动/底层异常消息可回显完整 JDBC URL（userinfo 形态含口令），原样入 param 会击穿 F6 对 jdbcUrl 参数的脱敏。`MetaDataSourceConnectionProcessor.redactJdbcUrlsInText`（public static）对消息中每个 `jdbc:\S+` 匹配段（先剥尾部收尾标点）经 `redactJdbcUrl` 脱敏回填，无命中原样返回；接入点 = `newNopConnectException`（建连点）+ `MetaQualityRuleExecutor.messageOf`（单点覆盖该文件全部 judgment message / error param 面）+ `parseConnectionConfig`（Nop JSON 解析器 readerState 上下文窗回显凭据载体输入的片段——实测无 `jdbc:` 前缀锚定、过滤不可覆盖，故该面抑制解析器消息回显、原始异常经 cause 链保留）。已知边界（F6 既有语义）：query 形态口令（`?user=x&password=y`）与无 `://` 的 Oracle thin 形态不在脱敏范围。
- **空/畸形主机 enforced fail-closed（F7，plan 2026-08-14-1133-1）**：`validateJdbcUrl` 在主机校验循环中对每个 `extractHosts` 产出做 `isPlausibleHostShape` 形状校验——空串（`jdbc:mysql:///db`）、以 `/` 开头（path 片段）、纯端口（`:3306`，以 `:` 开头且无第二个冒号）显式拒绝 `ERR_DATASOURCE_JDBC_URL_BLOCKED`（reason="host unparseable"），不再依赖驱动拒绝的 lucky fail-closed。合法无括号 IPv6 字面量（`::1`，以 `:` 开头但含第二个冒号）不被误伤，仍经 `HostSecurityUtil.isInternalHost` 判定。
- **webhook 主机形状校验 fail-closed（P2-06，plan 2026-08-16-0226-1，对齐 F7 先例）**：`CheckpointActionDispatcher.validateWebhookUrl` 对 `extractWebhookHost` 提取结果做形状校验——null/空串（空 authority、未闭合 IPv6 括号 `https://[::1/hook`）、`/` 前缀垃圾串（`https:///hook` → `/hook`）、纯端口（`https://:8080/hook` → `:8080`）、percent 编码残片（含 `%`）显式抛 `ERR_CHECKPOINT_WEBHOOK_URL_BLOCKED`（reason="implausible host shape"），不再静默跳过校验分支（修复前依赖"垃圾 host 建连失败"的 lucky fail-closed，与 JDBC 侧 F7 防御纵深不对称）。合法向量（域名/IPv4/无括号与方括号 IPv6/userinfo/端口/allowed-hosts 命中）判定结果不变。
- **IPv6 字面量判定不触发 DNS（F8，plan 2026-08-14-1133-1）**：`HostSecurityUtil.isInternalIpv6Literal` 入口增加 charset 前置过滤——仅"字符集 ⊆ [0-9a-fA-F:.] 且 ≥2 冒号"的输入才调用 `InetAddress.getByName`；否则视为非字面量直接 return false（不触发 DNS）。修复前含 `:` 但含非 hex 字母（g-z）的串（如 `gzzz::1`）路由到本方法后直接调 `getByName` 触发 DNS 查找，违反类 javadoc "纯确定性解析，不触发 DNS" 契约。过滤逻辑与 `isIpLiteral` 的 charset 检查逐项一致。
- **custom_sql blocklist PG 关键字补全（F9，plan 2026-08-14-1133-1）**：`MetaQualityRuleExecutor.CUSTOM_SQL_FORBIDDEN_WORDS` 补全 PG 脚本执行/时序攻击/系统目录族——`DO`（执行匿名 PL/pgSQL 代码块，RCE 等价面）、`PG_SLEEP`（时序盲注/DoS）、`PG_CATALOG`（系统目录 schema）、`PG_STAT_USER_TABLES`（系统统计视图，信息泄漏），以及 `WITH`（CTE 递归）。**`WITH` 的 CTE false-positive tradeoff**：合法分析型 CTE 查询（`WITH t AS (...) SELECT ...`）会被阻断——与既有 over-block 哲学一致（custom_sql 面向受限 SQL 场景，已含 `SET` 等常见关键字，宁可误拒不放过）；合法 CTE 需求应改用子查询或专用 measure 表达式通道（经 `ExpressionMeasureValidator` 校验）。**DRY 裁定**：与 `ExpressionMeasureValidator.KEYWORD_BLACKLIST`/`FUNCTION_BLACKLIST` 保持分离（不抽取共享常量）——两者语义域（整条 SQL 文本 vs SELECT 片段表达式）、分词与匹配机制（token 精确匹配 + fail-closed vs word-boundary + 字符串字面量先抽取）、演进路径均不同；重叠项的"恰好同名"是语义域交集的自然结果，非 DRY 缺陷。**custom_sql 结果持久化面只落 sqlHash（P2-07，plan 2026-08-16-0226-1）**：`judgeCustomSql` 的 `QualityResult.details` 不再写 SQL 全文（原 `details.sql` 为每结果行重复落盘，且 SQL 可内嵌敏感字面量）——SQL 全文的权威存储在规则本体（`NopMetaQualityRule.sqlExpression` 列优先 / params JSON 的 `sql` 键），details 只保留 `sqlHash`（SHA-256 短摘要）供审计追溯，与 AR-16 裁定的日志面"只记 sqlHash"同口径。
- **事件快照脱敏契约（AR-04/AR-07，plan 2026-08-14-0707-1）**：`MetaModelChangedEventPublisher.buildEntitySnapshot(Object entity)` 三分支（ORM / Map / POJO）对同一敏感 key 产出一致脱敏——敏感列（ORM `tagSet=sensitive` 或兜底列名集 `connectionConfig`/`password`/`jdbcUrl` 等）返回固定 `REDACTED_VALUE`（不读取实际值）。POJO 回退分支不再以反射序列化泄露敏感字段（stringify→parse 得到 Map 后路由回 Map 分支脱敏）。
- **connectionConfig 受控写路径与读脱敏（P1-1，plan 2026-08-15-1913-2）**：`NopMetaDataSource.connectionConfig` 持有明文 JDBC 凭据，契约分两面——**读出口脱敏**：`published="false"` 使该字段不在 GraphQL 查询输出类型中（findPage/findList/get 永不返回）；**受控写路径**：`insertable="true" updatable="true"`，`NopMetaDataSource__save` 可写入该字段，`__update` 在提交体显式含该键时更新（凭据轮换）；update 提交体不含该键时**不触碰已存配置**（`OrmEntityCopier` 只拷贝提交体中存在的 key——edit 表单不含该字段，表单提交永不清空配置）。`queryable/sortable="false"`：不允许以该字段作为过滤/排序条件探测凭据。表单字段落点：**仅 add 表单**（留存层 `NopMetaDataSource.view.xml`，textarea 控件，沿 `NopAuthUser.password` 先例）；edit/view 表单不显示（`published=false` 使字段不在查询输出类型，edit 页 `initApi` 的 `{@formSelection}` 选中该字段会报错）。`connectionConfigComponent`（惰性 JSON 解析组件，`internal="true"`）保持锁死、无写路径。`testConnection` / `syncExternalTables` / `collectCatalog` 均消费 save 写入的配置（写路径端到端可达，回归测试钉死）。
- **`testConnection` 注解语义与兼容性影响（P2-18，plan 2026-08-16-0226-3）**：`testConnection` 为只读探测（requireEntity + 建连读 `DatabaseMetaData`，无任何写操作），注解自 `@BizMutation` 修正为 `@BizQuery`（`INopMetaDataSourceBiz` 接口 + `NopMetaDataSourceBizModel` 实现双面一致；对齐同模块 judgeByRuleId / checkContractReadOnly 同类探测先例）。**GraphQL operation 类型由 mutation 翻转为 query**（`ReflectionBizModelBuilder` 按注解归类）——仓库内引用面（6 处测试调用 + 文档示例）已同步为 `query { NopMetaDataSource__testConnection(...) }`；对外部第三方调用方不做迁移承诺（模块 API 演进期，调用方需自行将 mutation 调用改为 query）。**action-auth 授权面影响（部署侧须知）**：无显式 `@Auth` 的 action 由平台生成默认权限串 `NopMetaDataSource:<opType>|NopMetaDataSource:testConnection`（`ReflectionBizModelBuilder` 默认 auth 分支），翻转使粗粒度兜底从 `:mutation` 换为 `:query`——启用 action-auth 的部署中：已授权细粒度 `NopMetaDataSource:testConnection` 功能点的角色不受影响；仅授粗粒度 `:mutation` 的角色失去该操作（fail-closed 收窄）；仅授粗粒度 `:query` 的角色（通常为更广的只读角色集）**新获得**该操作（SSRF 触发面可能扩大，需部署侧知情评估）。建议启用 action-auth 的部署为 `testConnection` 显式配置细粒度功能点条目、不依赖粗粒度兜底；SSRF 主防御（F2 fail-closed URL 主机校验、F5 危险参数 blocklist、F7 主机形状校验）与注解无关、不受翻转影响。

## 参考文档

- 平台主文档：`docs-for-ai/03-modules/nop-metadata.md`（本文档）
- I*Biz 接口契约（`nop-metadata-dao` 模块 `io.nop.metadata.biz` 包 `INopMeta*Biz.java`）：每个 BizModel 都有对应接口声明全部自定义方法签名（唯一例外：NopMetaSearchBizModel Pseudo-BizModel 无接口，见上「API 契约」段）
- DTO 规格（`nop-metadata-api/.../dto/`）：30 个 `@DataBean` DTO 类承载 API 返回值强类型契约
- ErrorCode 集中化（`nop-metadata-service/.../NopMetadataErrors.java`）：跨文件去重 + ARG_* 参数常量
- 模块级异常（`NopMetadataException`）：替代 `IllegalArgumentException` / `UnsupportedOperationException` / 裸 `RuntimeException`

> 设计决策、执行计划、修复记录等内部资料位于 `ai-dev/` 目录（按 AGENTS.md 文档分区约定，docs-for-ai 不引用 ai-dev 路径）。
