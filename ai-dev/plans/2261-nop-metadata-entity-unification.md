# 2261 nop-metadata 概念缩减：彻底删除 NopMetaTable，功能归一到 NopMetaEntity

> Plan Status: draft
> Last Reviewed: 2026-09-14
> Source: 用户裁定（NopORM 不变式："实体 = 物理表设计 + 关联语义标注"，任一 table 都有 ORM 模型，任一 ORM 模型本质上都对应物理表设计，故独立"逻辑表"概念冗余）；依赖盘点见本计划 Current Baseline（2026-09-14 实测）；关联分析 `ai-dev/analysis/2026-09/2026-09-12a-github-ontology-projects-vs-nop-metadata.md`（裁定补记）
> Related: `feature/nop-ontology` 分支 roadmap WI19（nop-metadata 瘦身）——本计划是其中"删除 NopMetaTable 分支"的独立先行实现，不依赖 nop-ontology 任何产出

## Purpose

把 nop-metadata 中"逻辑表"（NopMetaTable 及其 4 张子表）这一冗余概念彻底删除：其全部功能（三种表类型、字段解析、数据查询、聚合、JOIN、画像、SQL 视图创建、外部表同步）归一到 NopMetaEntity 一个概念上。完成后仓库内（主源码、测试、模型、文档）不再存在 NopMetaTable / MetaTable 概念的任何痕迹，nop-metadata 的目录概念从"Entity + Table 双树"缩减为"Entity 单树"。

## Current Baseline

以下事实于 2026-09-14 对 live repo 实测确认（依赖盘点含 orm.xml 行号与类名，此为摘要）：

1. **双树现状**：`nop-metadata/model/nop-metadata.orm.xml` 中 NopMetaTable（19 列，UK=(metaModuleId, tableName, isDelta, metaSchema)）与 NopMetaEntity（31 列，UK=(ormModelId, entityName)）之间**零 ORM relation**，唯一纽带是 `NopMetaTable.baseEntityId` 裸字符串列（无 FK），配对靠导入侧逻辑。
2. **NopMetaTable 子表 4 张**：Measure（aggFunc+expression，已引用 entityFieldId）、Dimension（dimensionType+granularity，已引用 entityFieldId）、Filter（TreeBean definition+isDefault）、Join（joinType+双端点 FK：left/right 各自二选一 {entityId, tableId}，写时校验互斥）。
3. **真 FK 依赖面（以 orm.xml 为准）**：指向 NopMetaTable 的非子表 to-one 共 9 个——NopMetaCatalog、NopMetaProfilingRule、NopMetaProfilingResult、NopMetaDataContract、NopMetaReconciliationConfig、NopMetaReconciliationResult、NopMetaQualityScore、NopMetaLineageEdge（sourceTable/targetTable 双端点）；另 NopMetaModule 反向 to-many `tables`；子表侧 to-one（4 子表的 metaTable + Join.leftTable/rightTable）随子表改造一并处置。
4. **字符串软引用 4 类**：搜索索引短名 `"MetaTable"`（NopMetaTableBizModel/NopMetaModuleBizModel/NopMetaIndexBuilder/NopMetadataHelper）；TagLabel 与 DataProduct 的 `"NopMetaTable"`（LineageTagPropagationProcessor/AutoClassificationProcessor/NopMetaDataProductBizModel.LINKABLE_ASSET_TYPES）；ModelChangedEvent 的 `"NopMetaTable"`；QualityRule 的 entityType 取值 `"table"`（dict `meta/quality-entity-type`）。
5. **执行路径**：entity 路径要求平台已注册 ORM 实体（`orm.isValidEntityName` 校验后走 `daoProvider.dao(entityName)`），**不从 NopMetaTable 行合成模型**；external/sql 路径为裸 JDBC（列结构 JSON 存于 `NopMetaTable.buildSql`）。聚合按 tableType 分派 7 路处理器（Entity/External/Sql × JOIN 组合），unsupported tableType 显式抛错。
6. **NopMetaEntity 现有列已具备大半承接能力**：entityName/tableName/querySpace/dbCatalog/dbSchema/businessDomainId/persistDriver 等已在；缺口为：类型判别列（物理/SQL 视图/外部）、sourceSql、外部列结构 JSON 的持久化位置。
7. **双写来源**：`OrmModelImporter.buildEntityTable`（:179）导入平台 orm.xml 时为每个实体同步创建一条 tableType=`entity` 的 NopMetaTable 行（不设 baseEntityId）；`syncExternalTables`（NopMetaDataSourceBizModel）为外部表创建 tableType=`external` 的 NopMetaTable 行（列结构 JSON 存 buildSql，模块固定 `nop/meta-external`，仅支持 MySQL/PostgreSQL/H2）。
8. **代码面规模**：主源码 94 个 Java 文件引用 NopMetaTable（含 5 个 Biz 接口、5 个 BizModel、1 个 QueryAction、api 模块 7+ 个 Table 命名 DTO、约 20 个 query/quality/lineage/profiling/sync 服务类）；测试 79 个文件（分组见 §测试迁移）；Web 5 个页面目录（NopMetaTable/Join/Filter/Dimension/Measure，各含 view.xml/lib.xjs/_gen）；`nop-metadata-meta/_templates/` 下 5 个 Table 系模板；`nop-metadata/deploy/sql/{mysql,oracle,postgresql}/` 三方言 DDL 含 nop_meta_table 系建表/删除/升级脚本；`docs-for-ai/03-modules/nop-metadata.md` 16 处提及（含 `INopMetaTableBiz` API 契约段）；`docs-for-ai/01-repo-map/module-groups.md` 与 `docs-for-ai/04-reference/source-anchors.md` 各有引用；design 侧 `ai-dev/design/nop-metadata/` 多份文档含 MetaTable 表述。
9. **仓库硬门**：`.github/workflows/maven.yml` 对 nop-metadata 执行 `ai-dev/tools/run-nop-metadata-invariants.sh`（6 项不变式：silent-swallow/INV-UK unique-key constraint 属性/sensitive-literal/limit 负值/silent-wrong-result 快照/error-param 对称性）；dict 存在 orm.xml 与 `_vfs/dict/meta/*.dict.yaml` 双处定义，需保持一致。
10. **模块外无引用**：nop-metadata 之外无任何模块引用 NopMetaTable（删除不产生跨模块破坏）。
11. **测试基线**：执行 kickoff 时须实测记录 `./mvnw test -pl nop-metadata -am` 基线（历史证据：2026-07-22 语义层 Phase 3 时 685 tests pass）。

## Goals

- `nop-metadata` 中 NopMetaTable、NopMetaTableMeasure、NopMetaTableDimension、NopMetaTableFilter、NopMetaTableJoin 五个实体及其全部生成物、Biz 接口、页面、模板被删除；主源码与测试中 NopMetaTable/MetaTable 符号与字符串清零。
- 全部功能归一到 NopMetaEntity：三种表类型由 NopMetaEntity 的类型判别列承载（物理 / SQL 视图 / 外部）；SQL 视图 SQL、外部列结构、querySpace、schema、业务域全部是 NopMetaEntity 的列；Measure/Dimension/Filter/Join 变为 NopMetaEntity 作用域的子实体（改名，metaTableId→metaEntityId，Join 双端点 FK 收敛为纯实体端点）。
- 对外 API（GraphQL 动作）以实体为中心的命名在 `INopMetaEntityBiz` 上可用，`docs-for-ai/03-modules/nop-metadata.md` API 契约同步。
- `syncExternalTables` 与 `OrmModelImporter` 只产出 NopMetaEntity（双写消失）。
- 全量测试迁移后 `./mvnw test -pl nop-metadata -am` 全绿；外部源注册→联邦聚合→血缘/标签传播→搜索命中端到端验证通过。

## Non-Goals

- 不实现 nop-ontology（本计划只是其 WI19 瘦身清单中"删除 MetaTable 分支"的独立先行，不依赖也不阻塞 ontology 分支）。
- 不新增业务功能（纯概念缩减 + 行为保持重构；external/sql 的执行行为、聚合七路分派语义保持不变）。
- 不做生产环境数据迁移工具（项目处于 2.0.0-SNAPSHOT 开发阶段，数据迁移策略见 Deferred But Adjudicated）。
- 不改 nop-metadata 之外的任何模块（已验证模块外零引用）。
- 不在本计划内删除 nop-metadata 其他定义面实体（OrmModel/EntityField/Relation 等的进一步瘦身归 ontology 分支 WI19，避免一个计划两个结果面）。

## Scope

### In Scope

- `nop-metadata/model/nop-metadata.orm.xml`：NopMetaEntity 扩列（类型判别、sourceSql、外部列结构持久化）；4 张子表改名换挂点到 metaEntityId；11 个 to-one FK 与 1 个反向关系改指 NopMetaEntity；删除 NopMetaTable 系 5 实体；相关 dict（`meta/table-type`→实体类型判别、`meta/quality-entity-type` 的 table→entity）更新。
- codegen 全量再生（dao/meta/web）+ 5 个 Table 系模板删除。
- 服务层：NopMetaTableBizModel + NopMetaTableQueryAction 合并进 NopMetaEntityBizModel（动作改名映射见设计文档）；MetaTableQueryExecutor/MetaTableFieldResolver/MetaTableReferenceResolver/MetaTableProfiler 等类改名并按新类型判别列分派；syncExternalTables 与 OrmModelImporter 改为只产出 NopMetaEntity；4 类字符串软引用全部切换。
- 测试迁移（约 80 文件，逐组迁移或显式裁定归并，不允许静默删除测试）。
- 文档：`ai-dev/design/nop-metadata/13-entity-unification.md`（目标模型裁定）、`docs-for-ai/03-modules/nop-metadata.md` API 契约与实体清单更新。

### Out Of Scope

- nop-ontology 分支的任何文件。
- nop-metadata 之外的模块（含 `docs-for-ai/` 中其他模块文档）。
- 生产升级脚本 / 数据迁移工具。

## Execution Plan

执行位置：从 master 新建独立 worktree 分支（建议名 `feat-nop-metadata-entity-unification`），按仓库 worktree 惯例合回 master。本计划为 plan-first 区域产物：ORM 模型结构变更，实施前需通过 plan audit。

### Phase 1 - 目标模型设计裁定（Decision/Proof）

Status: planned
Targets: `ai-dev/design/nop-metadata/13-entity-unification.md`

- Item Types: `Decision | Proof`

- [ ] 产出设计文档 `13-entity-unification.md`，至少裁定：①NopMetaEntity 新增列及 dict（类型判别列名与取值：物理/SQL 视图/外部；sourceSql；外部列结构 JSON 的列名与格式——现藏于 Table.buildSql）；②**多 schema 共存与 UK 重设计（原 R4.2 能力保持）**：现靠 `NopMetaTable.metaSchema` + 4 列 UK + `normalizeSchemaForMatch` 实现同名异 schema 共存（覆盖 `TestNopMetaTableMultiSchemaUpsert`/`TestNopMetaTableConcurrentNullSchemaUpsert`），合并后 NopMetaEntity UK=(ormModelId, entityName) 如何表达——dbSchema 进 UK、或 schema 编码进 entityName、或其他，必须显式裁定；③4 张子表改名与挂点（metaTableId→metaEntityId）、Join 双端点 FK 收敛为纯实体端点的规则；④**动作改名映射表 + api DTO 处置**：resolveTableFields/queryTableData/profileTable/createSqlTable → 实体中心命名（queryAggregation/queryJoinData 是否保持原名），以及 api 模块 7+ 个 Table 命名 DTO（QueryTableDataResultDTO/ResolveTableFieldsResultDTO/CreateSqlTableResultDTO/PreviewSqlFieldsResultDTO/ResolvedTableFieldDTO/QueryJoinDataResultDTO/SqlViewFieldDTO 等，出现在 GraphQL schema 类型面）的改名/保留裁定；⑤执行类改名清单（MetaTableQueryExecutor/MetaTableFieldResolver/MetaTableReferenceResolver/MetaTableProfiler → Entity 前缀）与 7 路聚合分派的新分派键；⑥字符串软引用迁移映射（search 短名/TagLabel/DataProduct/ModelChangedEvent/QualityRule dict）；⑦Entity 模块归属链（Entity→OrmModel→Module）下 syncExternalTables 系统模块的建模方式 + NopMetaModuleBizModel 级联删除/索引清理链路重写；⑧测试迁移分组策略（79 文件逐组：迁移/合并/显式移除+替代覆盖）。
- [ ] Phase 1 kickoff 时实测并记录 `./mvnw test -pl nop-metadata -am` 基线结果（写入本计划 Current Baseline 附录或日志）。
- [ ] 设计文档通过用户/独立审计确认后本 Phase 才可标 completed（设计裁定是 Phase 2 的输入契约）。

Exit Criteria:

- [ ] `ai-dev/design/nop-metadata/13-entity-unification.md` 存在且覆盖上述 8 项裁定，每项含"选了什么、拒绝了什么"
- [ ] 基线测试结果已记录
- [ ] 裁定已获确认（audit 记录）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 模型与服务层一次性切换（Fix）

Status: planned
Targets: `nop-metadata/model/nop-metadata.orm.xml`、`nop-metadata-dao`、`nop-metadata-service`、`nop-metadata-meta`、`nop-metadata-api`

- Item Types: `Fix`

> 本 Phase 是原子切换：orm 模型、codegen、dao/service 全部主源码在一个提交序列内完成，期间 main compile 不绿是预期中间态；Phase 出口才要求 compile 全绿。**本 Phase 期间不得顺手修改测试文件**（test-compile 挂是预期，测试迁移与逐组处置登记是 Phase 3 的门槛，提前改测试会使其流于形式）。

- [ ] orm 模型改造：NopMetaEntity 扩列；4 子表改名换挂点；9 个非子表 to-one FK 与 Module 反向关系改指 NopMetaEntity；删除 NopMetaTable 系 5 实体；dict 更新（`meta/table-type`→实体类型判别、`meta/quality-entity-type` 的 table→entity）且 orm.xml 与 `_vfs/dict/meta/*.dict.yaml` 双处同步
- [ ] codegen 全量再生（dao/meta/web/i18n）；删除 5 个 Table 系 `_templates` 模板（`_MetadataPropagation.json` 经查为空 `{}`，无需迁移）
- [ ] NopMetaTableBizModel + NopMetaTableQueryAction 的全部动作合并进 NopMetaEntityBizModel（按设计文档改名映射），`INopMetaTableBiz` 等 5 个 Biz 接口删除并按映射并入 `INopMetaEntityBiz`；api 模块 Table 命名 DTO 按设计文档裁定改名/保留
- [ ] 执行链改造：MetaTableQueryExecutor/FieldResolver/ReferenceResolver/Profiler 改名并按新类型判别列分派（7 路聚合分派语义保持不变）；external 列结构改从 NopMetaEntity 新列读取
- [ ] syncExternalTables 改为在系统模块 OrmModel 下写 NopMetaEntity 行（含外部列结构）；OrmModelImporter 删除 buildEntityTable 双写
- [ ] NopMetaModuleBizModel 级联删除与索引清理链路重写（tables→实体链路 + 新短名索引清理 + 事件类型更新）
- [ ] 4 类字符串软引用全部切换（search 短名、TagLabel/DataProduct entityType、ModelChangedEvent entityType、QualityRule dict 值）
- [ ] deploy/sql 三方言 DDL 处置：查明 `nop-metadata/deploy/sql/` 的生成入口或显式裁定手编规则，使 `nop_meta_table` 系 DDL 移除、NopMetaEntity 新列 DDL 补齐（三方言一致）
- [ ] Web 页面：删除 5 个 Table 页面目录，重新 codegen，确认 NopMetaEntity 页面暴露新动作

Exit Criteria:

- [ ] **清零验证（多口径组合）**：`grep -rni "metatable\|meta_table" nop-metadata/ --include="*.java" --include="*.xml" --include="*.xmeta" --include="*.xbiz" --include="*.xjs" --include="*.yaml" --include="*.json" --include="*.sql" --include="*.page.yaml"` 在非生成物范围零命中；`_gen`/`_app.orm.xml`/`_templates` 等再生产物以"重新执行 codegen 后零命中"为准（即豁免定义 = 再生产物中不残留，而非 grep 排除）
- [ ] `./mvnw compile -pl nop-metadata -am` 退出码 0
- [ ] `ai-dev/tools/run-nop-metadata-invariants.sh` 退出码 0（含新 UK 的 INV-UK constraint 属性）
- [ ] **接线验证**：NopMetaEntityBizModel 的查询/聚合动作运行时确实调用改名后的执行器（代码追踪或单测断言，Minimum Rules #23）
- [ ] **无静默跳过**：按新类型判别列分派时，未知类型值显式抛错而非静默走默认分支（Minimum Rules #24）
- [ ] 新增行为（类型判别分派、外部列结构从 Entity 读取、syncExternalTables 写实体行、多 schema 共存新机制）每项有对应新单测（Minimum Rules #25）
- [ ] `ai-dev/design/nop-metadata/13-entity-unification.md` 与落地实现无偏差（有偏差则回写设计文档）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 测试迁移（Fix/Proof）

Status: planned
Targets: `nop-metadata/nop-metadata-service/src/test/`

- Item Types: `Fix | Proof`

- [ ] 按设计文档 §测试迁移分组逐组迁移 80 个测试文件：聚合执行 16、字段解析 5、SQL/Entity 执行 4、外部同步 3、血缘 5、质量 7、画像 5、对账 4、Join 1、CRUD/守卫 10、搜索 3、标注 3、数据产品 3、契约 1、事件/传播 3、不变式 3、tableref 1、连接 1（数目为盘点值，执行时按实测为准）
- [ ] 测试辅助（AggregationTestHelper/BiSemanticTestHelper/LineageTestBase）改为构建 NopMetaEntity 测试夹具
- [ ] 每个被删除/合并的测试类在分组清单中显式登记处置（迁移到哪 / 被哪个替代覆盖），不允许静默删除
- [ ] 为改名后的 API 动作补契约断言（动作存在性 + 关键行为），覆盖 `INopMetaEntityBiz` 全部公开动作

Exit Criteria:

- [ ] `./mvnw test -pl nop-metadata -am` 全绿，测试数不低于基线（迁移是等价或增强，不允许覆盖缩水）
- [ ] 测试源码中 `MetaTable` 符号/字符串清零（分组清单中显式豁免项除外——如有须在设计文档登记理由）
- [ ] `./mvnw test-compile -pl nop-metadata -am` 退出码 0
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 端到端验证与文档/数据收口（Proof/Fix）

Status: planned
Targets: `nop-metadata`、`docs-for-ai/03-modules/nop-metadata.md`

- Item Types: `Proof | Fix | Decision`

- [ ] **端到端验证**（Minimum Rules #22）：一条集成测试或成文验证记录走通——注册外部数据源 → syncExternalTables 产出 EXTERNAL 实体 → resolveEntityFields → queryData → queryAggregation（含跨源 JOIN 聚合七路中至少 entity-entity 同库与 external-external 跨库两路）→ TagLabel 标注 + 血缘边传播 → 搜索索引按新短名命中（含索引重建/刷新步骤，防旧 entityType 幽灵文档）
- [ ] 数据迁移裁定执行：开发库按裁定重建/迁移（见 Deferred But Adjudicated 首条），启动自检通过
- [ ] `docs-for-ai/03-modules/nop-metadata.md` 全量更新：实体清单去 Table、API 契约改为 `INopMetaEntityBiz` 新动作名、多 schema 段（R4.2）改为新机制语义、搜索/级联删除段落同步；`docs-for-ai/01-repo-map/module-groups.md` 与 `docs-for-ai/04-reference/source-anchors.md` 中的引用同步
- [ ] `ai-dev/design/nop-metadata/` 全部文档按 live baseline 修订（全量 grep 扫描，不限于点名文档；design 只写最终状态）
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

Exit Criteria:

- [ ] 端到端验证完成且证据写入本计划 Closure 或日志
- [ ] `grep -rni "metatable\|meta_table" docs-for-ai/ ai-dev/design/` 零命中（历史 plans/archived 不回写，豁免清单见 Closure 证据）
- [ ] 数据迁移裁定已执行并有验证证据
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] 所有 in-scope 结构债（双树）已消除：orm 模型中仅存 NopMetaEntity 单树
- [ ] 行为/契约结果已达成：原 NopMetaTable 全部公开能力在 NopMetaEntity 上可用且语义不变（external/sql 执行、七路聚合、字段解析、画像、血缘、对账、质量）
- [ ] 主源码与测试 `MetaTable` 清零验证通过
- [ ] 端到端验证（Phase 4 首条）完成
- [ ] 无被静默降级的 in-scope live defect 或 contract drift
- [ ] 受影响 owner docs 已同步（docs-for-ai + design）
- [ ] 独立子 agent closure-audit 完成并写入 Closure Evidence
- [ ] Anti-Hollow Check：调用链追踪确认 BizModel→执行器→数据路径连通；`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0
- [ ] `./mvnw compile -pl nop-metadata -am` 退出码 0
- [ ] `./mvnw test -pl nop-metadata -am` 全绿
- [ ] `ai-dev/tools/run-nop-metadata-invariants.sh` 退出码 0（仓库 CI 硬门，防止"本地 closure 绿、GitHub CI 红"）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2261-nop-metadata-entity-unification.md --strict` 退出码 0

## Deferred But Adjudicated

### 存量数据迁移（开发库）

- Classification: `watch-only residual`（待 Phase 4 裁定执行方式）
- Why Not Blocking Closure: 项目处于 2.0.0-SNAPSHOT 开发阶段，无生产部署；nop-metadata 数据为开发/演示数据，可重建（模块导入与外部同步均为可重放操作）。Phase 4 将裁定执行"开发库重建"还是"一次性 UPDATE 迁移脚本"，并留验证证据。
- Successor Required: no

### TestNopMetaReconciliationTableRename 等以"表"为场景语义的测试

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 测试迁移保证等价覆盖（场景改为实体重命名），是否需要新增"物理表改名检测"等增强场景属于对账功能增强，不属于概念缩减结果面。
- Successor Required: no

## Non-Blocking Follow-ups

- BI 指标/维度进一步下沉为 ORM 语义标注（Measure/Dimension 折叠进字段级注解）——超出本计划"删除 Table 概念"的结果面，留待本体层 ObjectType 属性语义设计时统一考量（关联 `feature/nop-ontology` roadmap WI20）。
- `nop/meta-external` 系统模块的命名与生命周期随设计文档裁定复核。

## Risks And Rollback

- **原子切换风险**：Phase 2 中间态 main compile 不绿，若中断需 `git reset` 回 Phase 1 末提交；以小步提交（模型→codegen→逐包服务）降低回滚粒度。
- **外部列结构语义漂移**：buildSql 中的列 JSON 格式迁移到 NopMetaEntity 新列时必须保持字段解析器兼容（同格式迁移，不改格式），否则 external 路径全断——Phase 2 Exit Criteria 的新单测覆盖此点。
- **测试覆盖缩水**：Phase 3 以"测试数不低于基线 + 分组处置登记"双门槛防止静默删测试。
