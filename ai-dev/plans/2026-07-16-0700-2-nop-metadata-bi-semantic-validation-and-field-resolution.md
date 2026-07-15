# 2 nop-metadata BI 语义层 Measure/Dimension/Filter/Join 校验与字段解析

> Plan Status: active
> Last Reviewed: 2026-07-16
> Source: `ai-dev/design/nop-metadata/nop-metadata-roadmap.md` P3（P3-2 + P3-3 + P3-4 + P3-5）；`ai-dev/design/nop-metadata/01-architecture-baseline.md` §2.5 逻辑表（Measure/Join）+ §八 待定问题
> Mission: nop-metadata
> Work Item: P3-2 — MetaTableMeasure 管理；P3-3 — MetaTableDimension 管理；P3-4 — MetaTableFilter 管理；P3-5 — MetaTableJoin 管理
> Draft Review: PASS（两轮独立子 agent 对抗性审查，ses_0980548bfffe / ses_097fdd932ffe，2 Blocker + 2 Major 已修并经 live repo 证据核验，双执行序闭关自洽性已验证）
> Related: `2026-07-16-0700-1-nop-metadata-sql-view-creation-and-field-parsing.md`（P3-1，**硬前驱**——提供 sql 表 SELECT 字段解析器，本 plan 的 MetaTableFieldResolver 对 sql 表复用该解析器）、`292-nop-metadata-implementation-roadmap.md`（P1 已建模 Table/Measure/Dimension/Filter/Join 21 实体 + CRUD）、`2026-07-16-0420-2-nop-metadata-lineage-collection-and-traversal.md`（P2-5 提供 external/sql 表列软引用先例）

## Purpose

让 nop-metadata 的 BI 语义层从"五个实体有 CRUD 表结构"推进到"指标/维度/过滤/关联的定义**字段引用可解析、可校验、语义可消费**"。具体：提供一个**跨表类型（entity/sql/external）的字段解析服务** + 在 Measure/Dimension/Filter/Join 上叠加**字段引用与一致性校验**，并补齐 Dimension/Filter 在架构基线 §2.5 中**缺失的语义规格**（设计 gap）。本 plan 收口 P3 语义层的管理结果面，使 BI 语义模型可被 P4 联邦查询正确消费。

## Current Baseline

- **五个 BI 语义实体已建模 + CRUD 自动暴露**（P1，`nop-metadata/model/nop-metadata.orm.xml`）：
  - `NopMetaTable`（`orm.xml:959-1021`）：tableType(entity/sql/external)/querySpace/sourceSql/buildSql。
  - `NopMetaTableMeasure`（`orm.xml:1091-1153`）：`measureId`/`metaTableId`(mandatory)/`measureName`(mandatory)/`displayName`/`entityFieldId`(nullable)/`aggFunc`(dict `meta/agg-func`：sum/count/avg/min/max/countDistinct)/`expression`/`format`/`currencyUnit`/`description`/`extConfig`。to-one metaTable。索引 `IX_NOP_META_MEASURE_TABLE`。
  - `NopMetaTableDimension`（`orm.xml:1026-1086`）：`dimensionId`/`metaTableId`(mandatory)/`dimensionName`(mandatory)/`displayName`/`entityFieldId`(nullable)/`dimensionType`(dict `meta/dimension-type`)/`granularity`/`format`/`sortOrder`/`extConfig`。to-one metaTable。索引 `IX_NOP_META_DIM_TABLE`。
  - `NopMetaTableFilter`（`orm.xml:1158-1214`）：`filterId`/`metaTableId`(mandatory)/`filterName`(mandatory)/`displayName`/`definition`(domain `json-4000`, **mandatory**)/`description`/`isDefault`(bool)/`extConfig`。to-one metaTable。索引 `IX_NOP_META_FILTER_TABLE`。
  - `NopMetaTableJoin`（`orm.xml:1219-1288`）：`joinId`/`metaTableId`(mandatory)/`joinType`(dict `meta/join-type`, mandatory)/`leftEntityId`/`rightEntityId`/`leftField`/`rightField`/`alias`。to-one metaTable/leftEntity/rightEntity（P1+-6 已补 left/right to-one）。索引 `IX_NOP_META_JOIN_TABLE`。
- **BizModel 现状**：`NopMetaTableMeasureBizModel`/`NopMetaTableDimensionBizModel`/`NopMetaTableFilterBizModel`/`NopMetaTableJoinBizModel` **全部仅为 `CrudBizModel`，无任何自定义 action 或校验**（确认：`NopMetaTableMeasureBizModel.java` 仅 setEntityName，15 行）。今天可保存 `entityFieldId` 指向不存在字段的 Measure、`leftField`/`rightField` 指向不存在列的 Join、`definition` 为任意 JSON 的 Filter——**无字段引用校验、无跨实体一致性校验**。这是 P3-2~P3-5 要补的真实行为。
- **设计 gap（硬前置门禁）**：架构基线 §2.5 **只描述了 Measure 和 Join 的语义**；**Dimension（dimensionType/granularity 语义）和 Filter（definition JSON 结构）无任何规格**。§2.5 文字仅出现在 Measure/Join。Dimension 的 `dimensionType` dict 值 + `granularity`（时间粒度）语义、Filter 的 `definition` JSON 结构（条件树格式）须由 item 1.1 裁定并写入 §2.5，否则"管理"无可校验契约。
- **字段来源跨表类型差异（本 plan 核心难点）**：
  - `entity` 表：字段来自 `NopMetaEntityField`（经 `baseEntityId`→NopMetaEntity→fields）。
  - `external` 表：列结构在 `buildSql` JSON（A2 方案，无字段实体），需运行时从 JSON 解析列名。
  - `sql` 表：字段来自 `sourceSql` 的 SELECT 解析（**P3-1 Plan 1 产出解析器**，硬前驱）。
  - ⇒ "解析某表的可用字段"需要按 tableType 分派——这是 MetaTableFieldResolver 服务的核心。
- **dict 现状**：`meta/agg-func`（6 值）、`meta/dimension-type`、`meta/join-type`、`meta/table-type` 均已存在。框架已对 aggFunc/dimensionType/joinType/tableType 列做 dict 值校验（保存非法 dict 值会被框架拒绝）。**本 plan 校验重点是 dict 之外的跨引用一致性**（entityFieldId/字段名是否存在、join 实体/字段是否匹配）。
- **测试基建**：Nop AutoTest 可用；P1 已有导入 orm.xml 产出 entity 表 + 字段的测试数据可复用。`./mvnw clean install -pl nop-metadata -T 1C` BUILD SUCCESS。

## Goals

- Dimension / Filter 语义规格已裁定并写入 `01-architecture-baseline.md` §2.5（补齐 gap）：dimensionType 取值语义 + granularity（时间维度粒度）+ Filter.definition JSON 条件结构（Decision item 1.1）
- `MetaTableFieldResolver` 服务可用：输入 metaTableId → 按 tableType 分派解析可用字段（entity→MetaEntityField；external→buildSql JSON 列；sql→P3-1 SELECT 解析器）→ 返回字段列表。无字段/不可解析显式返回（不静默空）
- Measure / Dimension save 时校验 `entityFieldId`（或字段名引用）确实属于该表的可用字段集合；不合法显式失败（不静默存入悬空引用）
- Join save 时校验 `leftEntityId`/`rightEntityId` 存在且 `leftField`/`rightField` 属于对应实体的可用字段；joinType 已由 dict 校验；不一致显式失败
- Filter save 时校验 `definition` JSON 符合 item 1.1 裁定的条件结构（非法结构显式失败，不静默存入）
- 一个 query action `resolveTableFields(metaTableId)` 统一字段解析入口（若 Plan 1 未提供跨类型版本则本 plan 提供；若 Plan 1 已提供 sql 版本则本 plan 扩展为全 tableType）

## Non-Goals

- SQL 视图创建 / SELECT 字段解析 —— P3-1（Plan 1，硬前驱）
- BI 语义模型的**查询执行**（聚合 SQL 生成 / JOIN 执行）—— P4 联邦查询
- 复杂 Filter 表达式引擎（OR/AND/嵌套条件求值执行）—— 首版只校验 definition 结构合法性 + 简单条件模型，执行在 P4
- 物化字段实体（MetaTableField）—— §4.2 不存储；本 plan 字段解析为运行时只读视图
- 跨表 Measure（指标引用 join 右表字段）的完整校验 —— 首版校验指标字段属于"主表+其 join 右表"字段并集（item 1.1 裁定范围），复杂跨表表达式校验为 follow-up
- UI 层管理界面 —— 本 plan 只提供后端 action + 校验 + GraphQL 暴露

## Design Decisions

> D1/D2 为硬前置门禁，须在 item 1.1 裁定并写入 `01-architecture-baseline.md` §2.5 后实现。

### D1. Dimension / Filter 语义规格（待 item 1.1 裁定，补齐 §2.5 gap）

- **Dimension.dimensionType**：裁定取值语义（参考 `meta/dimension-type` dict 现值）——区分普通维度 / 时间维度 / 地理维度等；`granularity`（时间粒度，如 day/week/month/quarter/year）仅在 dimensionType=时间维度时生效。结论写入 §2.5（Dimension 实体描述，当前 §2.5 缺失）。
- **Filter.definition JSON 结构（Decision 待裁定）**：候选——(a) 复用平台过滤树（`TreeBean`，由 `FilterBeans` 构建的 `{type, name, op, value}` / AND/OR 树——注意是 `TreeBean` filter 树，非整个 `QueryBean`；`QueryBean` 含 limit/offset/orderBy 等，过滤只是其 `filter` 子树）；(b) 简化条件树（`{column, operator, value}` 数组 + combinator）；(c) 自由 JSON。**推荐 (a) 对齐平台 TreeBean filter 树**（P4 可直接执行，避免再造；反序列化用 `JsonTool.parseBean(json, TreeBean.class)`）。item 1.1 裁定并写入 §2.5（Filter 实体描述）。**`definition` 列为 `json-4000`（precision 4000）**：复杂嵌套条件序列化超 4000 字符时显式失败（不截断、不静默），item 1.1 记录该限制。`isDefault` 语义（每表至多一个默认过滤器）是否首版强制也由 item 1.1 裁定。

### D2. 字段引用校验范围 + 跨表字段集合（待 item 1.1 裁定）

- **校验落点（Decision 待 item 1.1 裁定）**：Measure/Dimension/Filter/Join 的校验挂在 save override（CrudBizModel.save 可重写，已验证 `public T save(@Name("data") Map, IServiceContext)`）或自定义 @BizMutation action（如 saveMeasure/saveJoin）。**注意：本模块目前无 save override 先例**（既有自定义 action 均为新增 @BizMutation/@BizQuery，如 profileTable/collectCatalog）——item 1.1 须裁定引入 save override 新模式，还是改用自定义 @BizMutation action 承载校验。
- **可用字段集合范围**：
  - entity 表：**手动 query**（`NopMetaTable.baseEntityId` 为 plain string、无 ORM relation；按 baseEntityId 作 metaEntityId 查 `NopMetaEntityField`）。**`baseEntityId` 为 null（ORM nullable，架构基线 §2.5 称"可选"）时显式失败**（不静默空）。
  - external 表：buildSql JSON 解析的列名集合（结构已确定：JSON 数组，列名 key=`columnName`，见 `NopMetaDataSourceBizModel.serializeColumns`）。
  - sql 表：sourceSql SELECT 解析字段集合（P3-1 解析器）。
  - **Measure/Dimension 字段引用存储裁定（硬前置子项，item 1.1 须先于 item 1.3 落地）**：`entityFieldId` 列语义为"实体字段 ID"。entity 表可硬匹配 entityFieldId→NopMetaEntityField 主键；**external/sql 表无字段实体**，字段引用须裁定存储方式——(a) 复用 `entityFieldId` 列存字段名字符串（语义重载）/ (b) 字段名存 `extConfig` / (c) 其他。**此裁定是 item 1.3/1.4 实现路径的硬前置**，若裁定为 (b) 则 item 1.3 校验读取 extConfig 而非 entityFieldId。item 1.3/1.4 须等该子项落地。
  - **expression 型 Measure**：`entityFieldId` 为 null（表达式指标，用 `expression` 列）时跳过字段引用校验，`expression` 内容首版不校验（Non-Goal）。
  - **Join 字段引用**：leftField 属于 leftEntityId 实体字段集合、rightField 属于 rightEntityId 实体字段集合（Join 的 leftEntity/rightEntity to-one 已存在，P1+-6）；实体不存在显式失败。Join 现仅关联 entity，sql/external 表 join 语义为 follow-up，item 1.1 确认首版范围。
- **granularity 值域（Minor，item 1.1 顺带裁定）**：`NopMetaTableDimension.granularity` 为 plain string 列、**无 dict 约束**（仓库无 granularity*.dict.yaml）。item 1.1 裁定值域为"自由 string（文档约定）"还是"新增 dict"（后者涉及 meta 模块资源变更）。
- **降级（不静默通过）**：字段集合解析失败（sql sourceSql 不可解析 / external buildSql JSON 损坏 / entity baseEntityId null）→ 显式失败抛 inline ErrorCode（不静默跳过校验、不静默存入）。

## Scope

### In Scope

- `01-architecture-baseline.md` §2.5：补齐 Dimension + Filter 语义规格（D1）
- `MetaTableFieldResolver` 服务：metaTableId → 按 tableType 解析可用字段（entity/external/sql 分派，sql 复用 P3-1 解析器）
- Measure / Dimension / Filter / Join save 校验：字段引用 + 实体一致性 + Filter.definition 结构（D2）
- AutoTest：建 entity 表 → 录入合法/非法 Measure/Dimension/Join/Filter → 断言合法通过、非法显式失败 + resolveTableFields 跨表类型返回正确字段集

### Out Of Scope

- P3-1 SQL 视图创建 + SELECT 解析器（Plan 1，硬前驱）
- P4 查询执行（聚合/JOIN/Filter 执行）
- 物化 MetaTableField 实体（§4.2 不存储）
- 复杂 Filter 求值引擎、跨表 Measure 表达式完整校验（follow-up）
- UI 管理界面

## Execution Plan

### Phase 1 - 语义规格裁定 + 字段解析服务 + 校验

Status: planned
Targets: `ai-dev/design/nop-metadata/01-architecture-baseline.md` §2.5（补 Dimension/Filter）、新增 `MetaTableFieldResolver`（service 层）、`NopMetaTableBizModel`（暴露/扩展 `resolveTableFields` 跨类型入口）、`NopMetaTableMeasureBizModel`/`NopMetaTableDimensionBizModel`/`NopMetaTableFilterBizModel`/`NopMetaTableJoinBizModel`（save 校验）、对应 `Test*BizModel.java`

- Item Types: `Decision`（D1 Dimension/Filter 语义 + D2 校验范围/字段集合，硬前置）+ `Proof`（新功能：字段解析服务 + 校验）

> **硬前置门禁（item 1.1）**：D1/D2 必须先裁定（只裁定不写代码），写入 §2.5 后再落地解析服务与校验。**硬前驱依赖**：本 plan 的 MetaTableFieldResolver 对 sql 表复用 P3-1（Plan 1）的 SELECT 字段解析器——若 Plan 1 未 done，sql 表字段解析路径不可用（首版可先实现 entity/external 分派，sql 分派标注 TODO 并在 Plan 1 done 后接线，但不得静默跳过）。

- [ ] 1.1 **Dimension/Filter 语义规格 + 字段引用校验范围决策（硬前置门禁，Decision only）**：基于 live repo 核查并裁定 D1/D2——确认 `meta/dimension-type` dict 现值（categorical/temporal/geographical）+ 裁定 dimensionType/granularity 语义（`granularity` 列无 dict，裁定为自由 string 文档约定或新增 dict）；裁定 Filter.definition JSON 结构（推荐对齐平台 **TreeBean filter 树**——非整个 QueryBean，过滤是其 filter 子树；实测确认 `JsonTool.parseBean(json, TreeBean.class)` 可行 + json-4000 超限显式失败）；裁定 isDefault 是否首版强制唯一；裁定校验落点（**本模块无 save override 先例**——裁定引入 save override 新模式 or 自定义 @BizMutation action）；裁定可用字段集合范围（**entity 手动 query** baseEntityId→NopMetaEntityField，baseEntityId null 显式失败 / external→buildSql JSON `columnName` / sql→P3-1 解析字段名）；**裁定 Measure/Dimension 字段引用存储方式（entityFieldId 硬匹配 vs 字段名存 extConfig）——此为 item 1.3/1.4 硬前置子项**；裁定 Join 字段校验范围（首版仅 entity join）；**裁定 resolveTableFields 与 Plan 1（0700-1）的 ownership**（Plan 1 sql-only → 本 plan 扩展全 tableType）。**只裁定不写代码**。结论写入 `01-architecture-baseline.md` §2.5（新增 Dimension + Filter 实体语义描述，补齐当前 §2.5 仅有 Measure/Join 的 gap）
- [ ] 1.2 新增 `MetaTableFieldResolver` 服务（依赖 1.1，无状态）：输入 metaTableId → 按 tableType 分派——**entity：手动 query（`NopMetaTable.baseEntityId` 为 plain string 列、无 ORM relation；`NopMetaEntity` 亦无 fields to-many——须按 `baseEntityId` 作 metaEntityId 查 `NopMetaEntityField` 集合，不得用关系遍历）。`baseEntityId` 为 null（ORM nullable）时显式失败抛 inline ErrorCode（不静默空）**；external：解析 buildSql JSON（**结构已确定：JSON 数组，元素 key 含 `columnName`/`dataType`/`nullable` 等，见 `NopMetaDataSourceBizModel.serializeColumns`——列名取 `columnName`**）取列名集合；sql：调 P3-1 SELECT 字段解析器（Plan 1 产出）→ 返回统一字段列表（字段名 + 来源类型）。解析失败/无字段显式抛 inline ErrorCode（不静默返回空集合）
- [ ] 1.2b 在 `NopMetaTableBizModel` 暴露/扩展 `@BizQuery resolveTableFields(@Name("metaTableId") String metaTableId, IServiceContext)` 作为跨类型统一字段解析入口，内部委托 `MetaTableFieldResolver`。**与 Plan 1（0700-1）的 ownership 裁定（item 1.1）**：Plan 1 的 resolveTableFields 为 **sql-only** 版本（落 NopMetaTableBizModel）；本 plan 将其**扩展为全 tableType**（entity/external/sql 分派）。若 Plan 1 先 done，本 item 为修改既有 method 增加分派分支；若 Plan 1 未 done，sql 分派抛 UnsupportedOperationException（显式失败），entity/external 分派独立可用
- [ ] 1.3 Measure / Dimension save 校验（**依赖 1.1 的字段引用存储裁定子项**）：在 save override（或 item 1.1 裁定的落点）中，对非空字段引用（entity 表 entityFieldId / external/sql 表字段名，按 item 1.1 裁定的存储方式读取），经 MetaTableFieldResolver 解析该表可用字段集合，校验引用属于集合；不合法抛 inline ErrorCode（不静默存入）。**expression 型 Measure（entityFieldId 为 null）跳过字段引用校验，expression 内容首版不校验（Non-Goal）**。aggFunc/dimensionType 已由 dict 校验（确认无需重复）
- [ ] 1.4 Join save 校验（依赖 1.1）：校验 leftEntityId/rightEntityId 对应 NopMetaEntity 存在；leftField 属于 leftEntity 字段集合、rightField 属于 rightEntity 字段集合（经 entity→NopMetaEntityField 解析）；不一致抛 inline ErrorCode
- [ ] 1.5 Filter save 校验（依赖 1.1）：校验 definition JSON 符合 item 1.1 裁定的条件结构（对齐 QueryBean 结构则校验可反序列化为合法过滤条件树）；非法结构抛 inline ErrorCode。isDefault 唯一性（若 item 1.1 裁定强制）
- [ ] 1.6 错误码按现有模式在各 BizModel 内 inline 定义（field-not-found / entity-not-found / filter-definition-invalid / field-resolve-failed 等）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] D1/D2 决策已裁定并写入 §2.5（Dimension + Filter 语义补齐）；`./mvnw clean install -pl nop-metadata -T 1C` BUILD SUCCESS，既有回归测试全过
- [ ] `MetaTableFieldResolver` 对 entity 表返回 NopMetaEntityField 集合、external 表返回 buildSql JSON 列集合、sql 表返回 P3-1 解析字段集合（若 Plan 1 已 done）；字段列表与实测数据一致
- [ ] Measure save：合法 entityFieldId/字段名通过、非法引用（指向不存在字段）显式失败；Dimension save 同理
- [ ] Join save：leftField/rightField 属于对应实体字段集合时通过；实体不存在 / 字段不属于实体时显式失败
- [ ] Filter save：definition 符合 item 1.1 裁定结构时通过；非法 JSON 结构显式失败
- [ ] 字段解析失败路径显式：sql sourceSql 不可解析 / external buildSql JSON 损坏 → 显式失败抛 inline ErrorCode，**不静默跳过校验、不静默存入悬空引用、不吞异常**
- [ ] **端到端验证**：从导入 orm.xml 产 entity 表（或建 external/sql 表）→ resolveTableFields 返回字段 → 录入合法 Measure 通过 / 录入非法 Measure 被拒 的完整路径已验证（见 Minimum Rules #22）
- [ ] **接线验证**：save 校验运行时确实调用了 MetaTableFieldResolver（非法引用被拒证明解析器被调用），非空壳（见 Minimum Rules #23）
- [ ] **无静默跳过**：非法引用/结构显式失败；sql 分派若 Plan 1 未 done 则抛 UnsupportedOperationException（不静默跳过校验）；无空方法体 / 吞异常（见 Minimum Rules #24）
- [ ] **新功能测试**：新增测试覆盖 Measure（合法+非法引用）+ Dimension（合法+非法+时间维度 granularity）+ Join（合法+非法实体/字段）+ Filter（合法+非法结构）+ resolveTableFields（entity/external/sql 三类），全绿（见 Minimum Rules #25）
- [ ] `ai-dev/design/nop-metadata/01-architecture-baseline.md` §2.5（Dimension + Filter 语义）已补齐
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：所有条目 + Phase Exit Criteria 全部 `[x]` 后才能 `completed`。

- [ ] Dimension / Filter 语义规格已写入 §2.5（补齐 gap）
- [ ] MetaTableFieldResolver 跨 entity/external 解析可用字段；sql 分派在 Plan 1（P3-1）done 前为显式 UnsupportedOperationException（非静默跳过），Plan 1 done 后接通（与 Deferred But Adjudicated 一致）
- [ ] Measure/Dimension/Filter/Join save 校验可用，非法引用/结构显式失败
- [ ] 不存在空壳实现（无空方法体 / 静默跳过 / 吞异常；sql 分派未接通时显式失败非静默）
- [ ] 必要 focused verification 已完成（合法/非法各路径测试全绿）
- [ ] `./mvnw clean install -pl nop-metadata -T 1C` BUILD SUCCESS（含测试）
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0
- [ ] 受影响的 owner docs 已同步（`01-architecture-baseline.md` §2.5）
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 已验证 save 校验运行时确实调用字段解析器并拒绝非法引用（端到端连通）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2026-07-16-0700-2-nop-metadata-bi-semantic-validation-and-field-resolution.md --strict` 退出码 0

## Deferred But Adjudicated

### sql 类型表字段解析（若 Plan 1 未先 done）

- Classification: `optimization candidate`
- Why Not Blocking Closure: MetaTableFieldResolver 的 sql 分派复用 P3-1 SELECT 解析器。entity/external 分派独立可用。若 Plan 1 先 done 则此项不成立；若本 plan 先于 Plan 1 执行，sql 分派抛 UnsupportedOperationException（显式失败），entity/external 校验已满足"语义层字段引用可校验"核心结果面
- Successor Required: yes（P3-1 Plan 1 接通 sql 分派）

### 跨表 Measure 表达式 / sql-external 表 Join 校验

- Classification: `optimization candidate`
- Why Not Blocking Closure: 首版 Measure/Dimension 校验主表字段集合、Join 校验 entity 实体字段。跨表表达式（指标引用 join 右表字段）与 sql/external 表的 join 语义属独立复杂度，P4 查询执行前可按需补充
- Successor Required: yes（P4 前增量）

## Non-Blocking Follow-ups

- 复杂 Filter 求值引擎（嵌套 OR/AND/表达式，执行在 P4）
- isDefault 默认过滤器的运行时自动应用（首版仅校验唯一性，应用在 P4 查询）
- Measure/Dimension 批量定义 action（首版单条 CRUD + 校验）
- Dimension 时间维度的自动时间桶生成（granularity→SQL DATE_TRUNC，执行在 P4）

## Closure

Status Note: <<完成或关闭时填写>>
Completed: <<YYYY-MM-DD>>

Closure Audit Evidence:

- Reviewer / Agent: <<独立审阅者或独立子 agent>>
- Audit Session: <<session ID>>
- Evidence: <<每条 Exit Criterion / Closure Gate 验证结果>>

Follow-up:

- <<只记录 non-blocking follow-up；confirmed live defect 不得出现在这里>>
