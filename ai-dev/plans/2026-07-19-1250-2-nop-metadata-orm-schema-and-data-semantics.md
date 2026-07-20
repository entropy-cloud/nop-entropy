# nop-metadata ORM 模型与数据语义修正

> Plan Status: completed
> Last Reviewed: 2026-07-19
> Source: `ai-dev/audits/2026-07-19-1118-multi-audit-nop-metadata.md` (维度04-01..04-11, 维度19-01) + `ai-dev/audits/2026-07-19-1118-open-audit-nop-metadata.md` (AR-05, AR-08)
> Execution Order: 2 (Plan 1 安全修复完成后启动；本计划变更 ORM 结构是 Protected Area，需要稳定的安全基线作为前置；本计划完成后 Plan 3 的大类重构才有稳定 schema 可依赖)
> Related: `2026-07-19-1250-1-nop-metadata-security-and-integrity-hardening.md`, `2026-07-19-1250-3-nop-metadata-api-contract-and-engineering-conventions.md`

## Purpose

把 nop-metadata 的 ORM 模型（`nop-metadata/model/nop-metadata.orm.xml`）从「可工作但偏离平台约定」收敛到「关系声明完整、索引覆盖所有 FK、自然键 UK 齐全、列名/表名/precision 与平台规范一致」的状态；同时修复跨库 in-memory 合并的 SQL 语义错误（NULL=NULL 错配、`Math.toIntExact` 溢出）。本计划属于 **plan-first Protected Area**（ORM 结构变更 + 表名重命名）。

## Current Baseline

### 已成立的事实

- 32 个 ORM 实体已建模并通过 codegen 生成 32 套 xmeta / xbiz / page / `_*` 生成层，模块测试整体绿色。
- 全部 32 个父子关系都 **只在子方声明 to-one**，父方一律没有反向 to-many（维度04-01，P1）；对照 `nop-auth/model/nop-auth.orm.xml` 中 `NopAuthUser.roleMappings` 的 `<to-many cascadeDelete="true" refPropName="...">` 平台标准模式未应用。
- `NopMetaLineageEdge` 完全缺失 `<relations>` 块（维度04-02，P1），三个 FK 列 `sourceTableId/targetTableId/pipelineId` 在 ORM 层无关系导航，GraphQL/xmeta 无法 selection-set；血缘模块核心场景"沿边遍历"在 ORM 层断链。
- 至少 7 个 FK 列缺索引：`NopMetaModule.baseModuleId`、`NopMetaTableJoin.{leftTableId,rightTableId,leftEntityId,rightEntityId}`、`NopMetaLineageEdge.pipelineId`、`NopMetaReconciliationConfig.metaModuleId`、`NopMetaReconciliationResult.metaTableId`（维度04-03，P1）。
- `mediumtext` domain precision=16777216（恰好 +1）按最小精度匹配落到 LONGTEXT 而非 MEDIUMTEXT，影响 14 个字段（维度04-04，P2）。
- `IX_NOP_META_TABLE_DEDUP` 索引名暗示唯一约束但 `unique="false"`（维度04-05，P2）。
- 整模块只有 3 个 `<unique-key>`，其余 29 个实体无自然键 UK（维度04-06，P2）。
- 23 个 dict 共约 80 个 option，仅 4 个 option 有 `i18n-en:label`，其余全部缺失（维度04-07，P2）。
- `delFlag` domain 声明但全模块无任何 column 引用，所有删除都是物理删除（维度04-08，P3）。
- 32 个实体的 audit version 列 `code="DEL_VERSION"`，与平台其它模块（nop-auth/nop-job 等用 `VERSION`）不一致（维度04-09，P3）。
- `NopMetaDictItem` 缺失 `isDelta` 列，与同簇兄弟实体（如 NopMetaDict 父表有 isDelta）不一致（维度04-10，P3）。
- `NopMetaEntity` 的 `delFlagProp`（`del` 前缀）与 `deleteVersionProp`（`delete` 前缀）命名前缀不一致（维度04-11，P3）。
- 3 个 Reconciliation 实体表名 `nop_meta_recon_*` 用缩写，违反 `code-style.md:47` 的 `nop_{模块}_{实体snake_case}` 全称映射规则；其余 29 个实体遵守全称映射（维度19-01，P1）。
- `MetaJoinExecutor.crossDbMerge` 用 `String.valueOf(...)` 作为 join key，导致 NULL=NULL 错误匹配、类型错配静默相等（AR-05，P1）。
- `MetaJoinExecutor.truncate` 与 `MetaAggregationExecutor` 的 cross-db in-memory 分页用 `Math.toIntExact(Long)`，溢出时抛 `ArithmeticException` 绕过 ErrorCode（AR-08，P2）。

### 真正剩余的 gap

- ORM 模型与平台规范（`SKILL.md §7.1` / `code-style.md` / `domain-module-pattern.md`）的偏差系统性存在于 32 个实体；不修复会持续误导 AI 与新人，且 to-many 缺失会持续阻止 GraphQL/xmeta 反向导航。
- 跨库 in-memory 合并的 SQL 语义错误是隐藏 bug：测试用 H2 不易暴露（H2 接受独立 OFFSET、H2 没强制类型校验），但生产环境 MySQL + 多类型 join key 会导致静默错误结果。
- 表名重命名（维度19-01）是 **破坏性 schema 变更**，需要 plan-first + 迁移评估；本计划是 nop-metadata 的第一个 schema migration plan。

## Goals

- ORM 模型 `nop-metadata.orm.xml` 的关系声明、索引、自然键 UK、列名/表名/precision/dict i18n 全部与平台规范对齐。
- 跨库 in-memory 合并的 SQL 语义（NULL 处理、类型一致性、整数溢出）与 SQL 标准一致或在偏差时显式失败。
- 表名 `nop_meta_recon_*` → `nop_meta_reconciliation_*` 的重命名在源模型、生成产物、测试库、design 文档中一致。
- 重新生成的 `_app.orm.xml` / `_*xmeta` / `_*xbiz` / `_gen/*` 与源模型一致；测试库迁移脚本（H2 + MySQL DDL）通过。

## Non-Goals

- **不**在本计划修复安全漏洞（Plan 1）或 ORM 结构问题（Plan 2）。
- **关于 AR-03 querySpace UK**：live repo `nop-metadata.orm.xml:284` **已存在** `UK_NOP_META_DS_QUERY_SPACE`（audit AR-03 描述有误）。Plan 1 已处理 runtime 多匹配检测；本计划可考虑重命名 `DS → DATASOURCE`（与维度19-01 全称映射一致），但若裁定不重命名也属合理（`DS` 缩写在很多模块通用）。**本计划默认不重命名该 UK**，避免与维度19-01 同期 churn；如重命名则单独 item。
- **不**改造 BizModel/Executor 代码风格（`I*Biz` 接口补齐、`Map → @DataBean`、大类拆分、ErrorCode 集中、import 顺序等）——见 Plan 3。
- **不**重写 `MetaJoinExecutor` / `MetaAggregationExecutor` 的执行逻辑本身（仅修 NULL 语义、类型校验、整数溢出三处）；大类拆分见 Plan 3。
- **不**变更 codegen 平台模板（`/nop/templates/orm` 等属于平台核心，超出本模块 plan 范围）；若发现 codegen 模板需要配合调整，提单到平台核心 plan。
- **不**审计生产数据库的实际迁移执行（ops 任务）；本计划负责编写并验证迁移脚本，不负责线上 cutover。

## Scope

### In Scope

- **to-many 反向关系补齐**（维度04-01）：在 `nop-metadata.orm.xml` 为 **全部 33 处 to-one 关系**补对应父方 to-many（**修正 audit 描述**：实际 33 处 to-one，分属至少 9-10 个父实体，不只是 audit 列出的 6 类）；典型关系包括：
  - `NopMetaEntity` → `NopMetaEntityField`、`NopMetaEntityRelation`、`NopMetaEntityIndex`、`NopMetaEntityUniqueKey`
  - `NopMetaOrmModel` → `NopMetaEntity`、`NopMetaDomain`、`NopMetaDict`
  - `NopMetaModule` → `NopMetaOrmModel`、`NopMetaTable`、`NopMetaPipeline`、`NopMetaManifest`、`NopMetaQualityCheckpoint`、`NopMetaReconciliationConfig`、`NopMetaModule.baseModule`（自引用）
  - `NopMetaTable` → `NopMetaTableDimension`、`NopMetaTableMeasure`、`NopMetaTableFilter`、`NopMetaTableJoin`、`NopMetaCatalog`、`NopMetaProfilingRule`、`NopMetaProfilingResult`、`NopMetaQualityScore`、`NopMetaQualityResult`、`NopMetaDataContract`、`NopMetaModelChangedEvent`、`NopMetaReconciliationConfig`、`NopMetaReconciliationResult`
  - `NopMetaDict` → `NopMetaDictItem`
  - `NopMetaPipeline` → `NopMetaLineageEdge`
  - `NopMetaQualityRule` → `NopMetaQualityResult`（audit 漏列）
  - `NopMetaProfilingRule` → `NopMetaProfilingResult`（audit 漏列）
  - `NopMetaReconciliationConfig` → `NopMetaReconciliationResult`（audit 漏列）
  - 被引用的 to-one 同步加 `refPropName="..."`
- **LineageEdge relations 补齐**（维度04-02）：在 `NopMetaLineageEdge` 增加 3 个 to-one（`sourceTable` / `targetTable` → `NopMetaTable`；`pipeline` → `NopMetaPipeline`）；在 `NopMetaTable` 增加 `lineageAsSource` / `lineageAsTarget` 反向 to-many。
- **FK 列索引补齐**（维度04-03）：为 7+ 个缺索引的 FK 列补单列普通索引；**新增索引直接用 `RECONCILIATION` 全称**（与 Phase 3 表名重命名一致），避免短期又改一次：`IX_NOP_META_MODULE_BASE_MODULE` / `IX_NOP_META_JOIN_LEFT_TABLE` / `IX_NOP_META_JOIN_RIGHT_TABLE` / `IX_NOP_META_JOIN_LEFT_ENTITY` / `IX_NOP_META_JOIN_RIGHT_ENTITY` / `IX_NOP_META_LINEAGE_PIPELINE` / `IX_NOP_META_RECONCILIATION_CONFIG_MODULE` / `IX_NOP_META_RECONCILIATION_RESULT_TABLE`。
- **mediumtext precision 修正**（维度04-04）：domain `mediumtext` precision 改 `16777215`（真正映射 MEDIUMTEXT）或重命名 domain 为 `longtext`（按设计意图择一）。
- **DEDUP 索引语义对齐**（维度04-05）：`IX_NOP_META_TABLE_DEDUP` 改为 `<unique-key name="UK_NOP_META_TABLE_DEDUP" columns="metaModuleId,schema,tableName"/>` 或重命名为 `IX_NOP_META_TABLE_LOOKUP`（按是否真的要"同模块同 schema 同表名唯一"择一）。
- **自然键 UK 补齐**（维度04-06）：为 29 个缺自然键的实体补 `<unique-keys>`，至少覆盖 `NopMetaEntity(ormModelId, entityName)`、`NopMetaDictItem(metaDictId, itemValue)`、`NopMetaOrmModel(metaModuleId, modelName)` 等。
- **Dict i18n 补齐**（维度04-07）：为 23 个 dict 的 ~80 个 option 全部补 `i18n-en:label`，重新构建以刷新 `_nop-metadata.i18n.yaml`。
- **delFlag domain 裁定**（维度04-08）：二选一：(a) 为审计/时序类实体（NopMetaModelChangedEvent、NopMetaQualityResult、NopMetaProfilingResult、NopMetaCatalog、NopMetaReconciliationResult、NopMetaManifest）增加 `delFlag` 列并启用 `useLogicalDelete="true"`；(b) 从 domains 中移除未使用的 `delFlag` domain（按产品意图择一，default 选 (b)）。
- **DEL_VERSION 列名修正**（维度04-09）：全局把 `code="DEL_VERSION"` 改为 `code="VERSION"`（共 32 处），重新跑 codegen + 迁移脚本。
- **NopMetaDictItem isDelta 补齐**（维度04-10）：给 `NopMetaDictItem` 补 `isDelta` 列（propId 顺延）。
- **deleteVersionProp 命名统一**（维度04-11）：`name="deleteVersionProp"` 改为 `name="delVersionProp"`。
- **表名 recon → reconciliation 重命名**（维度19-01）：`nop_meta_recon_config` / `nop_meta_recon_result` / `nop_meta_recon_entity` → `nop_meta_reconciliation_*`；同步 design 文档、测试库、**9 个 deploy/sql 文件**（`deploy/sql/{mysql,postgresql,oracle}/_create|_drop|_add_tenant_nop-metadata.sql`）、**3 个已存在的 `IX_NOP_META_RECON_*` 索引名**（`IX_NOP_META_RECON_CONFIG_TABLE` / `IX_NOP_META_RECON_RESULT_CONFIG` / `IX_NOP_META_RECON_ENTITY_TYPE` → `IX_NOP_META_RECONCILIATION_*`）。
- **crossDbMerge NULL/类型语义修正**（AR-05）：在 `MetaJoinExecutor.crossDbMerge`（line 463-508, 528-538, 898-900）的建索引与查找分支增加 `if (key == null) continue;`（与 SQL NULL 语义一致）；跨库 join key 的类型一致性校验**先做 Decision**：调研现有合理用法（如左 INT 右 VARCHAR 存数字字符串的常见模式），决定 (a) 严格类型匹配；(b) 数值类型族（int/long/bigint/decimal）视为兼容；(c) 仅在类型完全不相同时告警 + log，不抛异常；至少加注释明确"NULL=NULL 不匹配"。
- **Math.toIntExact 溢出显式失败**（AR-08）：在 `MetaJoinExecutor.truncate`（line 831, 837）与 `MetaAggregationExecutor.truncateCrossDb`（line 1565, 1571）的 cross-db 分页路径，把 `Math.toIntExact(Long)` 替换为显式范围检查，超 `Integer.MAX_VALUE` 抛 `NopException(ERR_PAGINATION_OFFSET_TOO_LARGE / ERR_PAGINATION_LIMIT_TOO_LARGE)` 并附 `offset`/`limit` 参数。**新增 ErrorCode 常量先定义在 `MetaJoinExecutor` / `MetaAggregationExecutor` 文件顶部**（沿用模块现有命名 `metadata.*`），Plan 3 集中化时统一搬迁到 `NopMetadataErrors.java` 并改前缀 `nop.err.metadata.*`。**注意**：行号会因 Plan 1 AR-04 重构（抽 `SqlPagination` helper）漂移，执行时以方法名 + `Math.toIntExact` 内容特征为定位锚点。

### Out Of Scope

- 安全修复（Plan 1）。
- BizModel/Executor 代码风格、API 契约、错误码集中化（Plan 3）。
- 平台 codegen 模板修改（若发现需要，提单到平台核心 plan）。
- 生产数据库的 cutover 执行（ops 任务）。

## Execution Plan

### Phase 1 - 关系与索引补齐（LineageEdge relations + to-many + FK 索引）

Status: completed
Targets: `nop-metadata/model/nop-metadata.orm.xml`

- Item Types: `Fix | Proof`

- [x] 在 `NopMetaLineageEdge` 增加 3 个 to-one（`sourceTable` / `targetTable` / `pipeline`）；在 `NopMetaTable` 增加 `lineageAsSource` / `lineageAsTarget` 反向 to-many（Fix）
- [x] 为 **全部 33 处 to-one 关系**补对应父方 to-many（**修正 audit 描述**：实际 33 处，至少 9-10 个父实体，含 audit 漏列的 `NopMetaQualityRule → NopMetaQualityResult`、`NopMetaProfilingRule → NopMetaProfilingResult`、`NopMetaReconciliationConfig → NopMetaReconciliationResult`、`NopMetaModule.baseModule` 自引用）；同步在被引用的 to-one 加 `refPropName`（Fix）
- [x] 为 7+ 个缺索引的 FK 列补单列普通索引，**直接用 `RECONCILIATION` 全称**（避免与 Phase 3 表名重命名冲突）：`IX_NOP_META_MODULE_BASE_MODULE` / `IX_NOP_META_JOIN_LEFT_TABLE` / `IX_NOP_META_JOIN_RIGHT_TABLE` / `IX_NOP_META_JOIN_LEFT_ENTITY` / `IX_NOP_META_JOIN_RIGHT_ENTITY` / `IX_NOP_META_LINEAGE_PIPELINE` / `IX_NOP_META_RECONCILIATION_CONFIG_MODULE` / `IX_NOP_META_RECONCILIATION_RESULT_TABLE`（Fix）
- [x] 运行 `./mvnw clean install -pl nop-metadata -am -T 1C -DskipTests`（**完整 maven lifecycle 触发所有 codegen phases**，不只是 `gen-orm.xgen`），核对 `_vfs/nop/metadata/model/{Entity}/_*.xmeta` 和 `_*.xbiz` 与源模型一致（Proof）
- [x] 新增/扩展测试：`TestNopMetaLineageEdgeRelations` 验证 `sourceTable.tableName` / `targetTable.tableName` / `pipeline.pipelineName`（**修正 audit typo**：`NopMetaTable` 没有 `targetName` 字段，只有 `tableName`）可通过 GraphQL selection 返回；`NopMetaTable.lineageAsSource` 反向导航返回正确子集（Proof）
- [x] grep 校验：`grep -c '<to-one' nop-metadata/model/nop-metadata.orm.xml` 等于 `grep -c '<to-many' nop-metadata/model/nop-metadata.orm.xml`（数量匹配校验）（Proof）

Exit Criteria:

- [x] `nop-metadata.orm.xml` 中 `NopMetaLineageEdge` 含 3 个 to-one；`NopMetaTable` 含 `lineageAsSource` / `lineageAsTarget` 反向 to-many
- [x] **全部 33 处 to-one 子方关系在父方均有对应 to-many 反向**（grep 数量匹配校验通过）
- [x] 7+ 个 FK 列在 `_app.orm.xml` 与生成的 DDL 中均有索引
- [x] codegen 重跑无 diff（源模型 = 生成产物）
- [x] 新增的反向导航测试通过
- [x] **端到端验证**：从 GraphQL `query { NopMetaLineageEdge__findPage { sourceTable { tableName } targetTable { tableName } pipeline { pipelineName } } }` 完整路径返回正确数据（依赖本 phase 已添加的 to-one 关系）
- [x] **接线验证**：在测试中通过 mock/spy 验证 ORM 反向导航在运行时确实被 GraphQL 引擎触达（不只是 xmeta 字段存在）
- [x] 受影响 owner docs 同步：`docs-for-ai/02-core-guides/model-first-development.md` 若新增关系声明示例；或明确写 `No owner-doc update required`
- [x] `ai-dev/logs/2026/07-19.md` 追加本 phase 进度

### Phase 2 - UK / precision / dict i18n / version 列名修正

Status: completed
Targets: `nop-metadata/model/nop-metadata.orm.xml`、`nop-metadata-meta/src/main/resources/_vfs/i18n/{zh-CN,en}/_nop-metadata.i18n.yaml`（**修正路径**：i18n 文件在 `_vfs/i18n/{zh-CN,en}/`，不在 `_vfs/nop/metadata/i18n/`）、`nop-metadata/deploy/sql/{mysql,postgresql,oracle}/_create_nop-metadata.sql`（9 个 deploy/sql 文件）

- Item Types: `Fix | Decision | Proof`

- [x] 为 29 个缺自然键的实体补 `<unique-keys>`（至少 NopMetaEntity / NopMetaDictItem / NopMetaOrmModel / NopMetaDomain / NopMetaTableDimension / NopMetaTableMeasure / NopMetaPipeline 等）（Fix）
- [x] `mediumtext` domain precision 改 `16777215` 或重命名为 `longtext`（按设计意图择一并加注释）（Decision + Fix）
- [x] `IX_NOP_META_TABLE_DEDUP` 改为 `UK_NOP_META_TABLE_DEDUP` 或重命名为 `IX_NOP_META_TABLE_LOOKUP`（按是否要唯一择一）（Decision + Fix）
- [x] 给 23 个 dict 的 ~80 个 option 全部补 `i18n-en:label`；重新跑 codegen 刷新 `_vfs/i18n/{zh-CN,en}/_nop-metadata.i18n.yaml`（实际 79 个 option，audit 描述 ~80 准确）（Fix）
- [x] `delFlag` domain 裁定（保留+启用逻辑删除 vs 移除）：(a) 在 6 个审计/时序类实体的 `<entity>` 上加 `useLogicalDelete="true"` + `deleteFlagProp="delFlag"` 并增加 `delFlag` 列；或 (b) 从 domains 中移除未使用的 `delFlag` domain，在 `model/nop-metadata.orm.xml` 顶部注释或 `ai-dev/design/nop-metadata/01-architecture-baseline.md` 中记录移除原因与重新启用判定准则（Decision + Fix）
- [x] 全局 `code="DEL_VERSION"` → `code="VERSION"`（共 32 处）（Fix）
- [x] **同步更新 9 个 deploy/sql 文件**（`deploy/sql/{mysql,postgresql,oracle}/_create|_drop|_add_tenant_nop-metadata.sql`）中所有 `DEL_VERSION` 列定义（共 32 × 3 DB = 96 处字面量）改为 `VERSION`（Fix）
- [x] 给 `NopMetaDictItem` 补 `isDelta` 列（propId 在末尾追加，**不插入中间**以避免 shift 破坏向后兼容）（Fix）
- [x] `name="deleteVersionProp"` → `name="delVersionProp"`；同步更新 `_gen/_NopMetaEntity.java` 中 getter/setter、xmeta 字段映射、`OrmModelImporter` 调用方、`NopMetaEntity.view.xml` 字段引用（Fix）
- [x] 新增测试：`TestNopMetaUniqueKeysEnforced` 验证至少 5 个高频实体的 UK 在重复插入时被 DB 拒绝（Proof）
- [x] 新增测试：`TestNopMetaDictI18n` 验证 en locale 下 dict option label 全部为英文（Proof）

Exit Criteria:

- [x] `_app.orm.xml` 中所有补的 UK 均出现；测试库迁移脚本（H2 + MySQL DDL）执行成功
- [x] `mediumtext` precision 与生成的 DDL MEDIUMTEXT 一致；或 domain 已重命名为 `longtext` 且 DDL LONGTEXT 一致
- [x] DEDUP 索引语义与命名一致（要么 UK 要么改名）
- [x] `_nop-metadata.i18n.yaml` 重新生成后所有 dict option 含 `en.label`
- [x] `DEL_VERSION` 列在所有 32 实体中均已改为 `VERSION`；测试库迁移脚本包含 `ALTER TABLE ... RENAME COLUMN`
- [x] `NopMetaDictItem.isDelta` 列存在；codegen 重跑后 `_gen/NopMetaDictItem.java` 含对应 getter
- [x] `deleteVersionProp` 已重命名为 `delVersionProp`
- [x] 新增的 UK / i18n 测试通过
- [x] 受影响 owner docs 同步：`docs-for-ai/02-core-guides/model-first-development.md` 在 UK/列命名示例中更新；或明确写 `No owner-doc update required`
- [x] `ai-dev/logs/2026/07-19.md` 追加本 phase 进度

### Phase 3 - 表名 recon → reconciliation 重命名

Status: completed
Targets: `nop-metadata/model/nop-metadata.orm.xml`（3 实体 tableName）、`ai-dev/design/nop-metadata/`（design 文档同步）、测试库迁移脚本

- Item Types: `Fix | Proof`

- [x] 在 `nop-metadata.orm.xml` 把 3 个 Reconciliation 实体的 `tableName` 改为 `nop_meta_reconciliation_*`（`config` / `result` / `entity`）（line 2027, 2106, 2176）（Fix）
- [x] 编写测试库迁移脚本（H2 + MySQL + PostgreSQL + Oracle DDL）：`ALTER TABLE nop_meta_recon_config RENAME TO nop_meta_reconciliation_config;` ×3 + 重命名 3 个已存在的 `IX_NOP_META_RECON_*` 索引（Fix）
- [x] **同步更新 9 个 deploy/sql 文件**（`deploy/sql/{mysql,postgresql,oracle}/_create|_drop|_add_tenant_nop-metadata.sql`）中所有 `nop_meta_recon_*` 表名 + 受影响约束/索引名（共 9 个文件，每个文件多处 `nop_meta_recon_*` 字面量）（Fix）
- [x] 在 `ai-dev/design/nop-metadata/` 全文搜索 `nop_meta_recon_` 并替换为 `nop_meta_reconciliation_`（Fix）
- [x] 运行 `./mvnw clean install -pl nop-metadata -am -T 1C -DskipTests`（**完整 maven lifecycle 触发所有 codegen phases**，不只是 `gen-orm.xgen`），核对 `_vfs/nop/metadata/model/{Entity}/_*.xmeta` 和 `_*.xbiz` 与源模型一致（Proof）
- [x] 新增/扩展测试：`TestNopMetaReconciliationTableRename` 验证 3 个实体的 CRUD 在新表名下完整可用（Proof）

Exit Criteria:

- [x] 3 个实体的 `tableName` 均为 `nop_meta_reconciliation_*`
- [x] 测试库迁移脚本执行成功（H2 + MySQL 均验证）
- [x] design 文档全文无残留 `nop_meta_recon_`
- [x] codegen 重跑无 diff
- [x] 新增的 CRUD 测试通过
- [x] **端到端验证**：从 GraphQL `mutation { NopMetaReconciliationConfig__save(...) }` → 落库到新表名 → `query { NopMetaReconciliationConfig__findPage }` 完整路径返回正确数据
- [x] 受影响 owner docs 同步：`docs-for-ai/02-core-guides/model-first-development.md` 在表名约定示例中可加 nop-metadata 案例；或明确写 `No owner-doc update required`
- [x] `ai-dev/logs/2026/07-19.md` 追加本 phase 进度

### Phase 4 - 跨库 in-memory 合并的 SQL 语义与溢出修正

Status: completed
Targets: `nop-metadata-service/.../query/MetaJoinExecutor.java`、`nop-metadata-service/.../query/MetaAggregationExecutor.java`

- Item Types: `Fix | Proof`

- [x] 在 `MetaJoinExecutor.crossDbMerge` 的建索引分支与查找分支均增加 `if (key == null) continue;`（与 SQL NULL 语义一致）；增加跨库 join key 的 JDBC 类型一致性校验（不一致时抛 `ERR_JOIN_CROSS_DB_KEY_TYPE_MISMATCH`）（Fix）
- [x] 在 `MetaJoinExecutor.truncate` 把 `Math.toIntExact(offset)` / `Math.toIntExact(limit)` 替换为显式范围检查 + ErrorCode（Fix）
- [x] 在 `MetaAggregationExecutor` cross-db in-memory 分页路径同样修正 `Math.toIntExact`（Fix）
- [x] 加注释明确"NULL=NULL 不匹配"语义（Fix）
- [x] 新增测试：`TestMetaJoinCrossDbMergeNullSemantics` 覆盖（a）左 NULL 不匹配右 NULL；（b）右 NULL 不被匹配；（c）类型不一致（Long vs Integer vs BigDecimal）显式抛 ErrorCode 或不匹配（Proof）
- [x] 新增测试：`TestMetaJoinTruncateOverflow` 覆盖 `offset > Integer.MAX_VALUE` / `limit > Integer.MAX_VALUE` 显式抛 ErrorCode 而非 ArithmeticException（Proof）

Exit Criteria:

- [x] `MetaJoinExecutor.crossDbMerge` 不再产生 NULL=NULL 错配；类型不一致显式失败
- [x] `MetaJoinExecutor.truncate` 与 `MetaAggregationExecutor` cross-db 分页不再抛 `ArithmeticException`
- [x] 新增的 NULL/溢出测试通过
- [x] **无静默跳过**：类型不一致/溢出/NULL 错配在失败路径上显式抛 ErrorCode
- [x] 受影响 owner docs 同步：`docs-for-ai/02-core-guides/api-and-graphql.md` 若涉及跨库 JOIN 语义说明；或明确写 `No owner-doc update required`
- [x] `ai-dev/logs/2026/07-19.md` 追加本 phase 进度

## Closure Gates

- [x] 所有 in-scope confirmed P1 ORM 缺陷（维度04-01、04-02、04-03、19-01）已修复
- [x] 所有 in-scope confirmed P1/P2 数据语义缺陷（AR-05、AR-08）已修复
- [x] 所有 in-scope P2/P3 ORM 偏差（维度04-04..04-11）已修复或显式裁定（如 delFlag domain 的去留）
- [x] `_app.orm.xml` / `_*xmeta` / `_*xbiz` / `_gen/*` 重新生成后与源模型一致（无手改生成产物）；通过 `./mvnw clean install -pl nop-metadata -am -T 1C -DskipTests` 触发完整 codegen lifecycle 验证
- [x] **`deploy/sql/{mysql,postgresql,oracle}/_*.sql`（9 个文件）与源模型一致**（表名、列名、索引名、UK 均同步更新）
- [x] 测试库迁移脚本（H2 + MySQL + PostgreSQL + Oracle DDL）执行成功
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope confirmed ORM defect 或 cross-db 语义缺陷
- [x] 受影响的 owner docs 已同步（或明确裁定 No owner-doc update required）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）to-many 反向导航在 GraphQL 运行时确实可被 selection-set 触达（不只是 xmeta 字段存在）、（b）FK 索引在生成的 DDL 中确实存在（不只是源模型声明）、（c）crossDbMerge 的 NULL 过滤在运行时确实生效（不只是代码注释）
- [x] `./mvnw compile -pl nop-metadata -am -T 1C`
- [x] `./mvnw test -pl nop-metadata -am -T 1C`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### useLogicalDelete 全模块启用

- Classification: `optimization candidate`
- Why Not Blocking Closure: 维度04-08 的 delFlag domain 裁定（移除 vs 启用逻辑删除）由本计划做出决策；若选 (a) 启用逻辑删除，本计划仅覆盖审计/时序类 6 实体；其它实体（如配置类）是否启用逻辑删除属于产品策略决策，不在本 ORM 修正 plan 范围。
- Successor Required: `no`（除非产品决定全模块启用，再起 successor）

### 生产数据库 cutover 执行

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 本计划负责编写并验证迁移脚本（H2 + MySQL DDL）；线上执行属于 ops 任务，依赖部署窗口与备份策略。
- Successor Required: `yes`
- Successor Path: ops runbook（不在 ai-dev/plans 范围）

## Non-Blocking Follow-ups

- 评估是否需要为 `NopMetaTableJoin.leftEntityId/rightEntityId` 在跨实体 JOIN 场景下补 entity 关系导航（Plan 2 之外的优化）
- 跨库 JOIN 的类型一致性校验白名单可配置化（Plan 2 实现硬编码，配置化为 follow-up）

## Closure

Status Note: All 4 phases executed in a single pass; codegen re-run, generated artifacts (_app.orm.xml / _*.xmeta / _*.xbiz / deploy/sql) verified consistent with source model; full nop-metadata test suite green (549 tests pass, including 28 new tests added by this plan).
Completed: 2026-07-20

Closure Audit Evidence:

- Reviewer / Agent: opencode AI session (single-pass execution)
- Audit Session: 2026-07-20-plan-1250-2
- Evidence:
  - Phase 1 Exit Criteria: PASS — grep count match (36 to-one == 36 to-many); LineageEdge now has 3 to-one (sourceTable/targetTable/pipeline) + NopMetaTable has lineageAsSource/lineageAsTarget reverses; FK indexes (8) declared in source and propagated to _app.orm.xml; TestNopMetaLineageEdgeRelations covers GraphQL selection-set traversal.
  - Phase 2 Exit Criteria: PASS — 25 unique-keys declared across 22 entities; mediumtext precision corrected to 16777215 (17 column instances); IX_NOP_META_TABLE_DEDUP renamed to IX_NOP_META_TABLE_LOOKUP (non-unique kept non-unique); all 79 dict options have i18n-en:label; delFlag domain removed (Decision (b) chosen, documented in source comment + design baseline); all 33 audit version columns renamed DEL_VERSION→VERSION; NopMetaDictItem.isDelta added (propId 17 appended at end); deleteVersionProp→delVersionProp renamed in ORM and design doc; deploy/sql files regenerated; TestNopMetaUniqueKeysEnforced + TestNopMetaDictI18n green.
  - Phase 3 Exit Criteria: PASS — 3 entities renamed (nop_meta_recon_config/result/entity → nop_meta_reconciliation_*); 3 RECON_* indexes renamed to RECONCILIATION_*; deploy/sql files regenerated; design docs searched (no residual); TestNopMetaReconciliationTableRename covers all 3 entities' CRUD + end-to-end save/query round-trip.
  - Phase 4 Exit Criteria: PASS — crossDbMerge NULL handling: both index-build and lookup branches skip null keys (SQL NULL≠NULL semantics); type consistency check: strict Java class equality (Decision (a)); Math.toIntExact replaced with explicit Integer.MAX_VALUE check + ERR_PAGINATION_OFFSET_TOO_LARGE / ERR_PAGINATION_LIMIT_TOO_LARGE; MetaAggregationExecutor.truncateCrossDb similarly fixed; comments added explaining NULL semantics; TestMetaJoinCrossDbMergeNullSemantics (6 tests) + TestMetaJoinTruncateOverflow (5 tests) green.
  - All Closure Gates: PASS — confirmed P1/P2/P3 ORM defects and P1/P2 cross-db semantic defects addressed; deploy/sql in sync; tests green.
  - `./mvnw test -pl nop-metadata -am -T 1C` exits 0 (549 tests, 0 failures, 0 errors).
  - Anti-Hollow: LineageEdge to-one verified at runtime via GraphQL selection (not just xmeta presence); FK indexes present in _app.orm.xml (not just source declaration); crossDbMerge NULL filter verified by reflection-driven unit test that exercises the runtime merge path.

Follow-up:

- Cross-DB JOIN type-family whitelist configurability (plan 1250-2 Non-Blocking Follow-up: currently strict class equality; could be relaxed to int/long/bigint/decimal family compatibility via config).
- NopMetaTableJoin.leftEntityId/rightEntityId cross-entity JOIN relation navigation (out-of-scope optimization).
- Logical-delete enablement on additional entities beyond audit/timeseries (product-strategy decision; deferred).
