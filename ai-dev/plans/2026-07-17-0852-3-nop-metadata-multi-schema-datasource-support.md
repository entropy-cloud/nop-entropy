# Multi-Schema Datasource Support (NopMetaTable schema column)

> Plan Status: active
> Last Reviewed: 2026-07-17
> Source: roadmap `ai-dev/design/nop-metadata/nop-metadata-roadmap.md` P2（外部数据源/同步）；架构基线 `01-architecture-baseline.md` §2.3.2 行 189（明确「为 NopMetaTable 增加 schema 列」属 P2-2 Protected Area 变更）+ §2.7.1 D1；recurring deferred：plan 1905-1 / 0228-2 / 0027-1「多 schema 数据源执行（需 NopMetaTable 增加 schema 列）」
> Mission: nop-metadata
> Work Item: P2-multi-schema 多 schema 数据源支持（Catalog/Quality/Profiling 执行）
> Related: `2026-07-16-1905-1`（entity/sql 执行扩展，多 schema follow-up 源头）、`2026-07-16-0225-2`（外部表同步）、`2026-07-16-0420-1`（Catalog）、`2026-07-16-0530-1`（Quality）、`2026-07-16-0530-2`（Profiling）
> Protected Area: ORM 模型结构（`model/*.orm.xml`）—— 按 AGENTS.md 为 `plan-first`，本 plan 经 audit 通过后方可实施。

## Purpose

为 `NopMetaTable` 增加 `schema` 列并贯通外部表同步持久化与 Catalog/Quality/Profiling 三大 JDBC 执行器，使**同一数据源下不同 schema 的同名表可区分**、执行器默认使用持久化 schema（而非仅靠一次性运行时 `schemaPattern` 入参），收口跨多 plan 反复出现的「多 schema 数据源执行」deferred 项。

## Current Baseline

（live repo 核实，2026-07-17）

- `NopMetaTable`（`nop-metadata/model/nop-metadata.orm.xml:995-1057`）**无 `schema` 列**；列仅 `metaTableId, metaModuleId, tableName, displayName, tableType, querySpace, sourceSql, baseEntityId, description, buildSql, version, createdBy/Time, updatedBy/Time, remark`（propId 1–16）。索引仅 `IX_NOP_META_TABLE_MODULE(metaModuleId)`。
- `NopMetaDataSource.querySpace` mandatory + 唯一键 `UK_NOP_META_DS_QUERY_SPACE`（一数据源一 querySpace）。**路由键是 querySpace，不是 schema。**
- 外部表同步 `ExternalTableStructureReader.read`（`nop-metadata-service/.../sync/ExternalTableStructureReader.java:46`）：`metaData.getTables(null, schema, "%", ...)`（:56）读取 `TABLE_NAME`（:58）但**不读 `TABLE_SCHEM`**；`ExternalTableInfo` 不持 schema，`NopMetaTable` 也无处存。
- **当前去重键 = `(metaModuleId, tableName)`**（非 querySpace）：`NopMetaDataSourceBizModel.upsertExternalTable`（:383-389）按 `metaModuleId` + `tableName` `findFirstByQuery`。所有 external 表共享**单一**系统模块 `metaModuleId`（`ensureExternalSystemModule` → `nop/meta-external`，:413-434），故**querySpace 不在去重键内**——同名表即便来自不同数据源/不同 schema 当前也会**互相覆盖**（设计基线 `01-architecture-baseline.md:411` 明确 `(metaModuleId, tableName)` 复合键幂等 upsert）。
- 三大 JDBC 执行器均接受**运行时** `schemaPattern` GraphQL 入参（透传到 JDBC），但**不持久化**：
  - Catalog：`NopMetaDataSourceBizModel.collectCatalog(dataSourceId, schemaPattern?)`（:226）批量入口——当前把**单一** `schemaPattern` 透传给循环内所有表（:257）；另有 `collectCatalogForTable(metaTableId, schemaPattern?)`（:302）单表入口（`table` 在作用域内）。`MetaCatalogCollector.collectForTable`（:63）`qualifyTable`（:148）。
  - Quality：`NopMetaQualityRuleBizModel.executeQualityRule(... schemaPattern?)`（:134）；`MetaQualityRuleExecutor.judge`（:60）`buildFromClause`（:445）。
  - Profiling：`NopMetaTableBizModel.profileTable(metaTableId, schemaPattern?, ...)`（:263）；`MetaTableProfiler.profile`（:90）。
  - **关键**：执行器方法签名仅收 `schemaPattern` 形参，**无法访问 `NopMetaTable`**——默认 schema 解析必须在 **BizModel 层**（持有 `NopMetaTable`）完成，再传入执行器。
  - 每次 `normalizeSchema` / `qualifyTable` 各自重复，schema 仅作一次性 filter，**重新执行必须重传**。
- ORM 再生成机制（已核实）：codegen 由 `nop-metadata-codegen/postcompile/gen-orm.xgen`（读 `model/nop-metadata.orm.xml` 生成 dao/entity/xbiz）与 `nop-metadata-meta/precompile/gen-meta.xgen`（生成 xmeta）经 `exec-maven-plugin` 触发。`nop-metadata/pom.xml` 是 `packaging=pom` 聚合器，`./mvnw install -pl nop-metadata -am` **只构建父 pom、不触发任何 codegen**——须显式构建 codegen/meta 模块（或全量构建）。
- 列级血缘（`SqlColumnLineageExtractor`）**完全无 schema 概念**（纯 SQL 文本解析）—— 归入本 plan Non-Goals/Deferred（独立机制）。
- `NopMetaEntity` 已有 `dbCatalog`(propId 22)/`dbSchema`(propId 23)（:444-447），描述 **entity 物理位置**（非 BI 逻辑表）。entity 类型表的 schema 由其 baseEntity 承担，**不需** NopMetaTable.schema。

## Goals

- `NopMetaTable` 新增可空 `schema` 列（propId 17），语义=external/sql 逻辑表的源 schema；entity 类型表留空（schema 由 baseEntity.dbSchema 承担）。
- 外部表同步读取 `TABLE_SCHEM` 并持久化到 `NopMetaTable.schema`；**去重键由 `(metaModuleId, tableName)` 收敛为 `(metaModuleId, schema, tableName)`**（保留 metaModuleId 维持系统模块归属），同名不同 schema 的表可区分。配套裁定是否在 ORM 增唯一键/复合索引（见 Phase 1/2）。
- Catalog / Quality / Profiling 执行器：未显式传 `schemaPattern` 时，默认取 `NopMetaTable.schema`（非空时），使「持久化一次、多次执行无需重传」成立；显式入参仍可覆盖。默认解析发生在 **BizModel 层**（持有 `NopMetaTable`），执行器仅收最终 `schemaPattern`。
- 迁移零破坏：既有数据 schema 为 null → 执行器行为与改动前完全一致（null schemaPattern = 不过滤）。
- 失败路径显式：未注册数据源 / 数据源禁用 / 不支持方言 沿用既有显式失败（不静默）。

## Non-Goals

- **列级血缘多 schema**：`SqlColumnLineageExtractor` 纯 SQL 解析机制不同，schema 限定需独立设计（simpleTable 限定 + 目录匹配），归入独立 follow-up。
- **NopMetaDataSource 多 catalog**：本 plan 只加 table 级 schema 列，不改 datasource 模型。
- **entity 类型表的 schema 重映射**：entity 表 schema 由 baseEntity.dbSchema 承担，本 plan 不为 entity 表填充 NopMetaTable.schema。
- 跨 catalog 路由 / 分区级 catalog 细化：超出 schema 限定范围。

## Scope

### In Scope

- ORM：`NopMetaTable` 增 `schema` 列（可空）+ 再生成（Protected Area，plan-first）。
- 外部表同步：读 `TABLE_SCHEM`、持久化、按 querySpace+schema+tableName 区分同名表。
- Catalog / Quality / Profiling：默认 schemaPattern = NopMetaTable.schema（显式入参可覆盖）。
- 迁移与回归：null schema 零行为变更。
- AutoTest：多 schema 数据源同步 + 各执行器默认 schema 执行。

### Out Of Scope

- 列级血缘 schema（→ Deferred）、datasource 多 catalog、entity 表 schema 重映射（→ Non-Goals）。

## Risks And Rollback

- **ORM 结构变更（Protected Area）**：`schema` 列新增需经 plan audit 后实施；改 `model/nop-metadata.orm.xml`（非 `_app.orm.xml` 等生成物），再经 codegen 模块重生成。回滚=移除该列 + 还原同步/执行器改动。
- **去重键变更的副作用**：当前去重 `(metaModuleId, tableName)` 对**跨数据源同名表也会覆盖**（querySpace 不在键内）。本 plan 改为 `(metaModuleId, schema, tableName)`——**schema 仍不在键内时跨数据源同名同 schema 表仍会覆盖**。是否进一步把 querySpace 纳入去重键为独立裁定（本 plan 不改 querySpace 维度，仅加 schema），须有迁移说明 + 测试覆盖跨数据源行为。
- **唯一性保证**：当前去重纯应用层（`findFirstByQuery`，无唯一约束）。Phase 1/2 须裁定是否新增唯一键/复合索引（见执行项），否则并发同步可能产生重复行。

## Execution Plan

### Phase 1 - ORM schema 列（Protected Area）+ 重生成 + 迁移

Status: completed
Targets: `nop-metadata/model/nop-metadata.orm.xml`（NopMetaTable 增列）；重生成（经 codegen/meta 模块，**非** `_app.orm.xml` / `_gen/` 手改，后者位于 `nop-metadata-dao/src/main/resources/_vfs/nop/metadata/orm/`）

- Item Types: `Decision`（schema 列建模 + 唯一键裁定）、`Fix`、`Proof`

- [x] `NopMetaTable` 新增 `schema` 列（propId 17，`stdSqlType=VARCHAR`，precision 100，可空，displayName=源schema，i18n-en）；不动既有列 propId。
- [x] **唯一键/索引裁定（Decision）**：**增复合索引 `IX_NOP_META_TABLE_DEDUP(metaModuleId, schema, tableName)`**（非唯一，加速新去重查询），保留既有 `IX_NOP_META_TABLE_MODULE(metaModuleId)` 供 to-one metaModule 关系查找。**不增 `<unique-key>`**：去重仍为应用层 `findFirstByQuery`，且跨数据源同名同 schema 表仍允许（querySpace 不在去重键内，见 Phase 2 Decision）；唯一键会破坏跨数据源同名表的既有覆盖语义，故 defer（非阻塞理由：应用层去重已保证幂等，并发同步产生重复行属既有非保证项，沿用 1905-1 follow-up）。
- [x] 经 codegen 重生成：构建 `nop-metadata-codegen`（`postcompile/gen-orm.xgen` 生成 dao/entity/xbiz）+ `nop-metadata-meta`（`precompile/gen-meta.xgen` 生成 xmeta）——命令须含这两个模块，例如 `./mvnw clean install -pl nop-metadata/nop-metadata-codegen,nop-metadata/nop-metadata-meta -am`（随后构建 dao/service），或全量 `./mvnw clean install -T 1C`。**`./mvnw install -pl nop-metadata -am` 无效（仅父 pom）。**
- [x] 迁移：新列 nullable，既有行默认 null；确认 H2 初始化 / 升级 DDL 不破坏既有数据。
- [x] GraphQL：`schema` 字段随 CRUD 自动暴露（xbiz 生成），无需手写 action。

Exit Criteria:

- [x] `NopMetaTable` 含可空 `schema` 列；**核实生成物** `nop-metadata-dao/.../_gen/_NopMetaTable.java` 真实含 `schema` 字段（`_PROP_ID_BOUND` 由 17→18）——**这是 codegen 是否生效的硬证据**。
- [x] `./mvnw compile -pl nop-metadata/nop-metadata-codegen,nop-metadata/nop-metadata-meta,nop-metadata/nop-metadata-dao,nop-metadata/nop-metadata-service -am` 通过（含 codegen 模块）。注：`compile` 阶段位于 `generate-test-resources`（gen-orm.xgen 绑定）之前，故此命令仅作**再生成后的编译校验**，再生成本身须用上面的 `clean install`。
- [x] 既有 entity/sql/external 表（schema=null）CRUD 与查询零回归（回归 AutoTest 通过）。
- [x] **无静默跳过**：新增列在生成的 entity/xmeta 中真实出现（非遗漏，#24）。
- [x] design `01-architecture-baseline.md` §2.3.2 行 189 的「已知缺口」标注更新为「已补 schema 列」+ 记录唯一键裁定；roadmap P2-multi-schema 同步。
- [x] `ai-dev/logs/2026/07-17.md` 追加条目。

### Phase 2 - 外部表同步持久化 schema + 去重键收敛

Status: planned
Targets: `nop-metadata-service/.../sync/ExternalTableStructureReader.java`、`ExternalTableInfo`、`NopMetaDataSourceBizModel.upsertExternalTable`（:383-389）

- Item Types: `Fix`、`Decision`（去重键 querySpace 维度）、`Proof`

- [ ] `ExternalTableStructureReader.read` 读取 `TABLE_SCHEM`（`rs.getString("TABLE_SCHEM")`），写入 `ExternalTableInfo.schema`。
- [ ] 同步写库路径：将 schema 持久化到 `NopMetaTable.schema`；**去重键由 `(metaModuleId, tableName)` 收敛为 `(metaModuleId, schema, tableName)`**（更新 `upsertExternalTable` :387-388 的 filter），同名不同 schema 不再互相覆盖。
- [ ] **跨数据源行为裁定（Decision）**：明确「跨数据源同名同 schema 表」是否仍覆盖（querySpace 不在键内）；写入迁移说明 + 测试覆盖。
- [ ] 失败路径显式：TABLE_SCHEM 为 null（部分方言）→ schema 留空（不伪造、不静默），沿用 null=不过滤语义。

Exit Criteria:

- [ ] AutoTest：同一数据源两个 schema 下同名表，`syncExternalTables` 后产两条 `NopMetaTable`（schema 各异），互不覆盖。
- [ ] AutoTest：跨数据源同名表行为符合 Phase 2 裁定（迁移说明一致）。
- [ ] **端到端验证**：注册多 schema 数据源 → sync → 断言两张同名表分别持正确 schema（#22）。
- [ ] **接线验证**：sync 写库路径确实写入了 `schema` 字段且去重 filter 含 schema（非仅读到内存丢弃，#23）。
- [ ] **无静默跳过**：TABLE_SCHEM 缺失时 schema 留空且可观测，非伪造（#24）。
- [ ] 既有单 schema 同步用例零回归。
- [ ] design `05-metadata-import.md` §四 + `01-architecture-baseline.md` §2.5.1 / §（去重键 :411）标注 schema 持久化 + 新去重键；roadmap 同步。
- [ ] `ai-dev/logs/2026/07-17.md` 追加条目。

### Phase 3 - Catalog/Quality/Profiling 默认 schema（BizModel 层解析）

Status: planned
Targets: `NopMetaDataSourceBizModel`（含 `collectCatalog` 批量循环 :257、`collectCatalogForTable` :302）、`NopMetaQualityRuleBizModel`（:134）、`NopMetaTableBizModel.profileTable`（:263）；执行器签名不变（仅收最终 schemaPattern）

- Item Types: `Fix`、`Proof`

- [ ] **默认 schema 解析在 BizModel 层**：单表入口（`collectCatalogForTable` / `executeQualityRule` / `profileTable`）持有 `NopMetaTable`，当调用方未显式传 `schemaPattern`（null/空）且 `table.schema` 非空时，默认 schemaPattern = `table.schema`；显式入参优先覆盖。再传入执行器。
- [ ] **批量入口 `collectCatalog` 结构变更**：当前把单一 `schemaPattern` 透传循环内所有表（:257）；改为**逐表**默认解析（每表可能不同 schema）——循环内按各 `NopMetaTable.schema` 解析最终 schemaPattern 再调用 `MetaCatalogCollector`。
- [ ] null schema（entity 表 / 旧数据）→ schemaPattern 维持 null（不过滤），行为与改动前一致。
- [ ] 失败路径显式：未注册数据源 / 禁用 / 不支持方言沿用既有显式失败。

Exit Criteria:

- [ ] AutoTest：多 schema 表，执行 Catalog/Quality/Profiling **不传** schemaPattern → 默认按持久化 schema 执行，命中正确 schema 的表（与显式传该 schema 结果一致）。
- [ ] AutoTest：显式传 schemaPattern → 覆盖持久化 schema（验证覆盖语义）。
- [ ] AutoTest：批量 `collectCatalog` 对多表（不同 schema）逐表命中正确 schema。
- [ ] **端到端验证**：sync（持久化 schema）→ 直接执行（不重传 schema）→ 命中正确表（#22）。
- [ ] **接线验证**：默认 schema 解析确在 BizModel 层生效并被执行器接收（非执行器内部猜，#23）。
- [ ] **无静默跳过**：schema 缺失时维持 null=不过滤（可观测），非伪造（#24）。
- [ ] 既有单 schema 执行用例零回归。
- [ ] design `01-architecture-baseline.md` §2.7.1 D1 + `06-data-quality-extended.md` 标注默认 schema 行为（BizModel 层）；roadmap 同步。
- [ ] `ai-dev/logs/2026/07-17.md` 追加条目。

## Closure Gates

- [ ] `NopMetaTable.schema` 列已落地（核实 `_gen/_NopMetaTable.java` 含该字段），三大执行器 + 同步已贯通，多 schema 同名表可区分且执行命中正确 schema。
- [ ] 去重键收敛为 `(metaModuleId, schema, tableName)`；唯一键/索引裁定已落地或显式 defer（含非阻塞理由）。
- [ ] null schema（旧数据/entity 表）行为零回归（未持久化 schema 时与改动前逐字一致）。
- [ ] 显式 schemaPattern 覆盖持久化 schema 的语义成立。
- [ ] 默认 schema 解析确在 BizModel 层生效（含 `collectCatalog` 批量逐表解析）。
- [ ] 未注册/禁用/不支持方言显式失败（无静默）。
- [ ] ORM 变更经 plan audit（Protected Area plan-first）确认。
- [ ] design §2.3.2/§2.5.1/§2.7.1 + `05`/`06` + roadmap 已同步到 live baseline。
- [ ] `./mvnw compile -pl nop-metadata/nop-metadata-codegen,nop-metadata/nop-metadata-meta,nop-metadata/nop-metadata-dao,nop-metadata/nop-metadata-service -am` 通过（含 codegen 模块）。
- [ ] `./mvnw test -pl nop-metadata -am` 通过。
- [ ] checkstyle / 代码规范检查通过。
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（Minimum Rules #26）。
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0。
- [ ] 独立子 agent closure-audit 已完成并记录证据。
- [ ] **Anti-Hollow Check**：sync 写库路径与三执行器默认 schema 路径在运行时被真实调用（端到端 sync→execute 命中正确 schema，非空壳/非退化为 null）。

## Deferred But Adjudicated

### 列级血缘多 schema

- Classification: `optimization candidate`
- Why Not Blocking Closure: `SqlColumnLineageExtractor` 纯 SQL 文本解析、无 JDBC schemaPattern 机制，schema 限定需独立设计（simpleTable 限定 + 目录匹配 disambiguation）；Catalog/Quality/Profiling 多 schema 执行已使「多 schema 数据源可执行」核心结果面成立。
- Successor Required: `no`（按需 follow-up）

### 多 schema 跨 catalog 路由

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 跨 catalog 需 datasource 多 catalog 模型变更，独立复杂度；schema 列已覆盖绝大多数多 schema 场景。
- Successor Required: `no`

## Non-Blocking Follow-ups

- 既存三处 `resolveDataSourceOrThrow` 重复 + 各 `normalizeSchema`/`qualifyTable` 收敛（沿用 0800-1/1905-1 follow-up，不构成本 plan 阻塞）。
- `NopMetaTable.querySpace` 索引（entity/sql 覆盖后查询量增加时加索引，沿用 1905-1 follow-up）。
- 列级血缘 schema 限定（独立 follow-up）。

## Closure

Status Note:
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Audit Session:
- Evidence:

Follow-up:

- no remaining plan-owned work（closure 时确认或改写）
