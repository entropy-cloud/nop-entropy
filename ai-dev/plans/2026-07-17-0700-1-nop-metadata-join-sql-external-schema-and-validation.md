# 0700-1 nop-metadata 跨表关联扩展 — sql/external 表作为 Join 端点 + Measure 跨表校验

> Plan Status: completed
> Last Reviewed: 2026-07-17
> Source: `ai-dev/design/nop-metadata/nop-metadata-roadmap.md`（P3/P4 deferred）；`ai-dev/design/nop-metadata/01-architecture-baseline.md` §2.5（逻辑表）+ §2.5.2 D2（Measure/Dimension 字段引用校验范围）+ §4.4（查询执行）；plan `2026-07-17-0228-3` Deferred But Adjudicated「sql/external Join 支持」Successor Required: yes
> Mission: nop-metadata
> Work Item: P3+/P4+ — sql/external 表作为 NopMetaTableJoin 端点（建模 + 校验）+ 其 Measure/Dimension 跨表字段引用校验
> Related: `2026-07-17-0228-3-nop-metadata-bi-semantic-cross-table-measure-validation.md`（predecessor，entity-entity 跨表 Measure 校验已 done，遗留 sql/external 为本 plan 目标）；`2026-07-17-0700-2-nop-metadata-federated-join-sql-external-execution.md`（successor，依赖本 plan 的 schema 变更执行 sql/external 联邦 JOIN 查询）
> Draft Review: 两轮独立子 agent 对抗性审查（含想象性分析 + live repo 核验）。R1 发现 4 Major + 5 Minor（owner-doc deferred 措辞散落 5 处未枚举 / D1 缺迁移与 ERR_JOIN_ENTITY_ID_NULL 放宽裁定 / Phase 3 可达集机制未独立为 Decision 且误述沿用 Approach A / D1↔Phase3 强耦合）已全部 live-verified 修复（D2 上移 Phase 1）。R2 共识 APPROVE-WITH-MINORS（无 Blocker/Major，4 新 Minor 为 cosmetic），可执行性共识达成。

## Purpose

把 BI 语义层跨表关联从「仅 entity 类型表」扩展到「entity / external / sql 三类表均可作为 NopMetaTableJoin 端点」，并使 sql/external 类型表的 Measure/Dimension 跨表字段引用可校验。本 plan 关闭 plan 0228-3 中 `Successor Required: yes` 的「sql/external Join 支持」deferred 项（其 Measure 跨表校验随之）。

执行序：本 plan（建模 + 校验）先行；successor 0700-2（联邦 JOIN 查询执行）在本 plan schema 变更落地后启动。

> **可用性边界说明**：本 plan 落地后，sql/external 表可建模为 Join 端点且其 Measure/Dimension 跨表字段引用可**校验**，但 sql/external 端点的 JOIN **查询执行**（queryJoinData）仍未实现（属 successor 0700-2）。即本 plan 交付「建模 + 防悬空校验」，端到端可执行 feature 在 successor 完成后才成立。

## Current Baseline

- `NopMetaTableJoin` 当前建模（`nop-metadata/model/nop-metadata.orm.xml:1255`）仅支持 entity 端点：列 `joinType` / `leftEntityId`(→NopMetaEntity, nullable) / `rightEntityId`(→NopMetaEntity, nullable) / `leftField` / `rightField`(字段名字符串) / `alias`；ORM relation 经 `leftEntityId`/`rightEntityId` 指向 NopMetaEntity（orm.xml:1308/1315）。无任何 table 引用列。
- `NopMetaTableJoinBizModel.validateJoinSide`（service 层）当前对 `entityId == null` **显式抛 `ERR_JOIN_ENTITY_ID_NULL`**——即 entityId 是事实 mandatory（列 nullable 但校验层强制）。
- 跨表 Measure/Dimension 字段引用校验已落地（plan 0228-3）：`MetaTableFieldResolver.resolveAllowedEntityIds(table, joinDao)`（`nop-metadata-service/.../service/field/MetaTableFieldResolver.java:155`）构建 `allowedEntityIds = {baseEntityId ∪ 该表所有 NopMetaTableJoin 的 rightEntityId}`；`validateFieldReference`（行 205）校验 `field.getMetaEntityId() ∈ allowedEntityIds`（PK 归属语义，仅 entity 端点）。
- 架构基线 §2.5.2 D3（行 379）明确：external/sql 类型表的 Measure/Dimension **不做跨表校验**——因 `NopMetaTableJoin` 仅引用 NopMetaEntity，sql/external 表无 entity 无法作为 join 端点。这是 plan 0228-3 显式排除并 deferred 的部分。
- 0228-3 Deferred But Adjudicated「sql/external Join 支持」：Classification=optimization candidate → 重分类为本 plan 的 `Fix`（已确认的 contract gap：join 建模与校验对 sql/external 缺失），Successor Required: yes（本 plan 即该 successor）。
- 三类逻辑表均已可单表查询（plan 0800-1）/ 聚合（plan 0800-2）；entity-entity JOIN 查询已可执行（plan 0800-2 MetaJoinExecutor）。sql/external 表的列结构经 **`buildSql`**（external 表列 JSON，由 `NopMetaDataSourceBizModel.setBuildSql(columnsJson)` 写、`MetaTableFieldResolver.resolveExternalFields` 读 `table.getBuildSql()`，orm.xml 中列名 `BUILD_SQL`）或 SELECT 解析（sql 表，经 `SqlSelectFieldExtractor`）存储，单表字段集合可解析。**repo 无 `columnsJson` 列，实际为 `buildSql`。**

## Goals

- `NopMetaTableJoin` 能建模一个或两个端点为 sql/external 类型表的跨表关联（不仅限 entity 端点）。
- sql/external 端点的 join 字段（`leftField`/`rightField`）校验真实存在——从该 sql/external 表的可解析列集合（external 读 `buildSql` / sql 读 SELECT 解析列）中确认。
- sql/external 类型表的 Measure/Dimension 跨表字段引用可校验（当该表存在指向其它表的 NopMetaTableJoin 时）。
- 模型变更决策记录入 `ai-dev/design/nop-metadata/01-architecture-baseline.md` §2.5（行 332）+ §2.5.2 D2/D3（行 364/379）（收口建模相关 deferred 措辞）。

## Non-Goals

- **不**实现 sql/external 表参与 JOIN 的查询执行（`queryJoinData`）。查询执行属 successor plan 0700-2，依赖本 plan 的 schema 变更。
- **不**做 join 可达性递归（A→B→C 间接可达 + 环路检测）——0228-3 已裁定 `Successor Required: no`，本 plan 沿用仅直连语义。
- **不**改 entity-entity 既有 join 校验/执行的**取值路径**（entity 端点经 leftEntityId/rightEntityId + PK 归属校验的逻辑不变）；但需显式放宽端点 mandatory 语义——从「entityId-only」放宽为「entity/table 二选一」（见 Phase 1 D1 裁定），这是为支持 table 端点的必要行为变更，非 entity 路径取值逻辑改动。
- **不**做 Filter（TreeBean）跨表字段解析——0228-3 已裁定 `Successor Required: no`。

## Scope

### In Scope

- ORM schema 变更（Protected Area，plan-first）：扩展 `NopMetaTableJoin` 使其可引用 sql/external 表作为端点。
- Join 定义校验扩展：端点为 sql/external 表时，`leftField`/`rightField` 须属于该表可解析列集合。
- `MetaTableFieldResolver` 扩展：sql/external 类型表的 Measure/Dimension 跨表字段引用校验。
- 设计文档更新（§2.5.2 收口 deferred）。

### Out Of Scope

- sql/external JOIN 查询执行（→ plan 0700-2）。
- 递归 join 可达性（→ watch-only）。
- Filter 跨表字段解析（→ watch-only）。

## Execution Plan

### Phase 1 - schema 建模决策 + 可达集机制裁定 + ORM 变更

Status: completed
Targets: `nop-metadata/model/nop-metadata.orm.xml`（`NopMetaTableJoin` 实体，行 1255）；`ai-dev/design/nop-metadata/01-architecture-baseline.md` §2.5（行 332）+ §2.5.2 D2/D3（行 364/379）

- Item Types: `Decision | Fix`

- [x] **D1 schema 建模裁定**（Decision，Protected Area）：裁定 `NopMetaTableJoin` 如何引用 sql/external 表端点。须逐项裁定并写入 §2.5.2：
  1. 端点引用列设计（是否新增 leftTableId/rightTableId → NopMetaTable）；
  2. 端点互斥规则（一个端点要么是 entity 要么是 table；entityId/tableId 同时非空或同时为空如何报错）；
  3. 端点 tableType 取值范围（external/sql；是否允许 entity-type NopMetaTable 作为 table 端点——若允许，可达集需混合 entity 字段名 + table 列名，见 D2）；
  4. **向后兼容/迁移影响**：新增 table 引用列必须 nullable 且对既有 entity-entity join 数据无破坏；codegen 后 `_NopMetaTableJoin.java` 字段读写真实；
  5. **`ERR_JOIN_ENTITY_ID_NULL` 放宽裁定**：`NopMetaTableJoinBizModel.validateJoinSide` 当前对 `entityId == null` 抛 `ERR_JOIN_ENTITY_ID_NULL`。须裁定放宽为「entity/table 二选一」（table 端点合法时不再要求 entityId 非空），与 Non-Goals 调和：entity 端点取值路径不变，仅端点 mandatory 语义从 entityId-only 放宽。
- [x] **D2 sql/external 可达集构造机制裁定**（Decision）：裁定 sql/external 类型表 Measure/Dimension 跨表字段引用校验的可达集构造。**注意：此机制与 entity 端点的 PK 归属语义（0228-3 Approach A：`allowedEntityIds.contains(field.getMetaEntityId())`）本质不同**——sql/external 表 Measure 字段引用是 **name-based**（字段名字符串，非 entityFieldId PK 路径）。须裁定：可达列名集合 = `{该表可解析列名 ∪ 其 NopMetaTableJoin 可达端点表的列名}`（端点表按 D1 端点引用解析），Measure/Dimension 引用的字段名须 ∈ 可达列名集合。D1 端点范围裁定直接影响 D2 可达集形态（若 D1 允许 entity-type table 端点，D2 须混合解析），故 D2 与 D1 同 Phase 一并裁定。裁定写入 §2.5.2 D3。
- [x] **ORM schema 变更**（Fix，Protected Area）：按 D1 裁定修改 `nop-metadata/model/nop-metadata.orm.xml` 的 `NopMetaTableJoin` 实体（新增 table 引用列 + relation），重新 codegen 生成 `_NopMetaTableJoin.java` / `NopMetaTableJoin.java`。

Exit Criteria:

- [x] §2.5.2 已记录 D1 建模决策（端点引用方式 + entity/table 互斥规则 + tableType 范围 + 迁移影响 + ERR_JOIN_ENTITY_ID_NULL 放宽裁定）
- [x] §2.5.2 D3 已记录 D2 可达集构造机制（name-based 并集，区别于 entity 的 PK 归属）
- [x] **建模相关 deferred 措辞已收口**（共 5 处，逐项核对）：§2.5 行 332「sql/external 表 join 语义为 follow-up」→ 更新为已支持；§2.5.2 D2 行 364「sql/external 表的 join 语义为 follow-up」→ 更新；§2.5.2 D3 行 379「external/sql 无法作为 join 端点」→ 更新；§4.4.1 行 883「无法作为 join 端点」建模部分 → 标注「modeling supported (本 plan)，execution pending successor 0700-2」；§4.4.1 D3 行 889「external/sql querySpace 仅在 follow-up 的 external join」执行部分 → 标注「execution → successor 0700-2」
- [x] `NopMetaTableJoin` 的 ORM 实体支持引用 sql/external 表端点（codegen 后的实体类含对应字段），`./mvnw compile -pl nop-metadata -am` 通过
- [x] 新增列对既有 entity-entity join 数据无破坏（既有 leftEntityId/rightEntityId 路径不变，新列可空）
- [x] **无静默跳过**：codegen 后新字段读写真实存在（非 placeholder）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - Join 定义校验扩展（sql/external 端点）

Status: completed
Targets: `NopMetaTableJoinBizModel`（`nop-metadata/.../service/entity/NopMetaTableJoinBizModel.java`）；列结构解析复用点（sql 表经 `SqlSelectFieldExtractor` 解析 / external 表读 `buildSql` JSON，由 `MetaTableFieldResolver.resolveExternalFields` 解析）

- Item Types: `Decision | Fix`

- [x] **端点解析与字段校验**（Fix）：`NopMetaTableJoin` save/update 时，当端点为 sql/external 表，校验 `leftField`/`rightField` 属于该表的可解析列集合（sql 表经 SELECT 字段集合、external 表经 `buildSql` JSON 解析）；端点不存在或字段不在列集合时显式失败。
- [x] **entity/table 端点互斥校验**（Fix，依 D1 互斥规则）：按 D1 裁定的互斥规则校验每个端点的引用一致性（entity 端点引用 NopMetaEntity、table 端点引用 NopMetaTable；entityId/tableId 同时非空或同时为空的报错形态依 D1），不一致时显式失败；同步落地 D1 的 `ERR_JOIN_ENTITY_ID_NULL` 放宽（table 端点合法时放行）。

Exit Criteria:

- [x] 存在 AutoTest：save 一个 sql 表端点的 NopMetaTableJoin（合法 leftField/rightField）成功落盘
- [x] 存在 AutoTest：sql/external 端点的 leftField/rightField 不属于该表列集合时 save 显式失败（错误码 + .param）
- [x] 存在 AutoTest：entity/table 端点互斥规则被违反时显式失败；table 端点合法时不再因 entityId==null 报 ERR_JOIN_ENTITY_ID_NULL
- [x] **接线验证**：NopMetaTableJoinBizModel save 路径确实调用新增的字段校验逻辑（test 断言失败场景被拦截，非静默放行）
- [x] **无静默跳过**：sql 表列集合不可解析（如 SELECT 为空）时显式失败而非返回空集合放行
- [x] `./mvnw test -pl nop-metadata -am` 通过
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - sql/external Measure/Dimension 跨表字段引用校验

Status: completed
Targets: `MetaTableFieldResolver`（`nop-metadata/.../service/field/MetaTableFieldResolver.java:155/205`）；`NopMetaTableMeasureBizModel` / `NopMetaTableDimensionBizModel`

- Item Types: `Fix`（实现 Phase 1 D2 裁定的机制）

- [x] **可达列名集构造**（Fix，落地 D2）：扩展 `MetaTableFieldResolver`，为 sql/external 类型表构建 name-based 可达列名集合 `{该表可解析列名 ∪ 其 NopMetaTableJoin 可达端点表的列名}`（端点表按 D1 端点引用解析；entity 端点贡献其实体字段名，table 端点贡献其列名）。**不沿用** entity 的 `allowedEntityIds` PK 归属路径——sql/external 字段引用是 name-based。
- [x] **sql/external 端字段引用解析**（Fix）：sql/external 类型表 Measure/Dimension 引用字段时，按 D2 可达列名集合校验字段名存在性（非 entityFieldId PK 路径）。

Exit Criteria:

- [x] 存在 AutoTest：sql/external 表定义 NopMetaTableJoin 后，其 Measure 引用可达表字段时校验通过
- [x] 存在 AutoTest：sql/external 表 Measure 引用不可达字段时 save 显式失败
- [x] 既有 entity-entity 跨表 Measure/Dimension 校验用例（0228-3 产出）仍全绿（回归）
- [x] **端到端验证**：从「创建 sql 表 → 创建以该 sql 表为端点的 NopMetaTableJoin → save 该表的 Measure 引用 join 可达字段」完整路径跑通且校验生效
- [x] **接线验证**：NopMetaTableMeasureBizModel/DimensionBizModel save override 确实调用扩展后的字段校验（失败用例被拦截）
- [x] **无静默跳过**：无可解析列集合的 sql/external 表，其 Measure 字段引用校验显式失败而非放行
- [x] §2.5.2 D3 已记录 D2 可达集语义（Phase 1 已裁定）；否则明确写 `No owner-doc update required`
- [x] `./mvnw test -pl nop-metadata -am` 通过
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> 关闭条件：本 section + 各 Phase Exit Criteria 全部 `[x]` 后，Plan Status 才改 `completed`（需独立 closure audit）。

- [x] 0228-3 Deferred「sql/external Join 支持」（Successor Required: yes）在本 plan 落地后收口
- [x] `NopMetaTableJoin` 可建模 sql/external 端点，且其字段引用校验已落地（非空壳）
- [x] sql/external 类型表 Measure/Dimension 跨表字段引用校验已落地（name-based 可达集，非空壳）
- [x] 既有 entity-entity join 校验/执行行为无回归（既有测试全绿）
- [x] §2.5/§2.5.2（行 332/364/379）建模 deferred 措辞已收口；§4.4.1（行 883/889）执行 deferred 措辞已标注 successor 0700-2 归属（不在本 plan 收口）
- [x] 不存在被静默降级到 deferred 的 in-scope live defect
- [x] 受影响 owner docs（`docs-for-ai/`）已同步或明确 `No owner-doc update required`
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：校验逻辑在 save 路径运行时确实被调用（非仅类型存在）
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2026-07-17-0700-1-nop-metadata-join-sql-external-schema-and-validation.md --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0
- [x] `./mvnw compile -pl nop-metadata -am`
- [x] `./mvnw test -pl nop-metadata -am`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### sql/external JOIN 查询执行（queryJoinData）

- Classification: `out-of-scope improvement`（相对本 plan 结果面）
- Why Not Blocking Closure: 本 plan 结果面是「建模 + 校验」。查询执行是独立结果面（用户可执行 sql/external 联邦 JOIN），依赖本 plan schema 变更，属 successor plan 0700-2。
- Successor Required: yes
- Successor Path: `2026-07-17-0700-2-nop-metadata-federated-join-sql-external-execution.md`

## Non-Blocking Follow-ups

- Join 可达性递归（A→B→C 间接可达 + 环路检测）— 0228-3 已裁定 watch-only，Successor Required: no。
- Filter（TreeBean）跨表字段解析 — 0228-3 已裁定 watch-only，Successor Required: no。

## Closure

Status Note: 本 plan 把 BI 语义层跨表关联从「仅 entity 类型表」扩展到「entity/external/sql 三类表均可作为 NopMetaTableJoin 端点」，并使 sql/external 类型表的 Measure/Dimension 跨表字段引用经 name-based 可达列名集合可校验。建模（ORM + 校验）与防悬空校验均已落地并通过 14 个新增 AutoTest + 既有 30 个回归用例验证（TestNopMetaBiSemanticBizModel 44 tests 全绿）。可用性边界：sql/external 端点的 JOIN 查询执行（queryJoinData）属 successor plan 0700-2，本 plan 不含。0228-3 的「sql/external Join 支持」`Successor Required: yes` deferred 项随本 plan 收口。设计决策 D1（端点建模放宽）+ D2（name-based 可达集）已写入架构基线 §2.5.2 D4，5 处建模/执行 deferred 措辞已收口/标注归属。
Completed: 2026-07-17

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，未参与实现），task_id `ses_092a80e25ffeYqb2DbufqMoTSd`，子代理类型 general
- Audit Session: ses_092a80e25ffeYqb2DbufqMoTSd
- Evidence:
  - Phase 1 Exit Criteria 全 PASS：D1+D2 决策写入 `01-architecture-baseline.md` §2.5.2 D4（行 382-393）；ORM 源 `model/nop-metadata.orm.xml:1295-1335` 新增 LEFT_TABLE_ID(propId15)/RIGHT_TABLE_ID(propId16) + leftTable/rightTable relation；codegen 产物 `_gen/_NopMetaTableJoin.java:80-86,208-212,900-936` 字段真实读写（非 placeholder）；5 处 deferred 措辞收口（§2.5:332 / §2.5.2 D2:364 / D3:379 / §4.4.1:896+:902 标注 successor 0700-2）。
  - Phase 2 Exit Criteria 全 PASS：`NopMetaTableJoinBizModel.save`(95)→`validateJoin`(108)→`validateJoinSide`(127)→`validateTableEndpoint`(182)/`validateEntityEndpoint`(151) 调用链真实（非空壳）；新增 6 ErrorCode 失败分支均 throw + .param；AutoTest `testJoinSaveSqlTableEndpointValid`/`testJoinSaveTableEndpointFieldNotInTableFails`/`testJoinSaveBothEndpointsSetFails`/`testJoinSaveTableEndpointEntityTypeFails`/`testJoinSaveTableEndpointRelaxesEntityIdNull`/`testJoinSaveNoEndpointFails` 覆盖。
  - Phase 3 Exit Criteria 全 PASS：`MetaTableFieldResolver.resolveAllowedFieldNames`(277-295) 经 `joinDao.findAllByQuery` 加载 join 并 union 端点列名（`addEndpointFieldNames`(303-326)，端点表不存在显式抛 `ERR_FIELD_RESOLVE_TABLE_NOT_FOUND` 不静默跳过）；`validateFieldReference` external/sql 分支(248)调用之；Measure/Dimension BizModel 传 tableDao。AutoTest `testSqlTableMeasureCrossTableViaTableEndpointValid`/`testExternalTableMeasureCrossTableViaEntityEndpointValid`/`testSqlTableMeasureCrossTableDanglingFails`/`testResolverResolveAllowedFieldNames` 覆盖。
  - Closure Gates 全 PASS（含 entity-entity 回归：0228-3 跨表用例在 44 绿中）。
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0。
  - Anti-Hollow 检查：(a) save 调用链运行时连通——save→validateJoin→validateJoinSide→endpoint 校验→resolver.resolveFieldNames，每环有真实 body + throw；(b) 端到端路径连通——`testSqlTableMeasureCrossTableViaTableEndpointValid` 从 saveSqlTable→saveTableJoin→Measure save 完整跑通；(c) `scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0（0 findings）；(d) 无空方法体/吞异常/continue 跳过——所有失败分支显式 throw ErrorCode。
  - Deferred 项分类检查：唯一 deferred「sql/external JOIN 查询执行」为 `out-of-scope improvement`（successor plan 0700-2），无 in-scope live defect 被降级。
  - `./mvnw test -pl nop-metadata/nop-metadata-service -am` → 280 tests, 0 failures, 0 errors；`./mvnw compile -pl nop-metadata -am` BUILD SUCCESS；`check-doc-links.mjs --strict` 退出码 0。

Follow-up:

- 无剩余 plan-owned work。sql/external 端点 JOIN 查询执行（queryJoinData）属 successor plan 0700-2（依赖本 plan schema 变更，路径 `ai-dev/plans/2026-07-17-0700-2-nop-metadata-federated-join-sql-external-execution.md`）。
- 变更尚未提交 git（按 opencode 规则未自动 commit）；AGENTS.md 建议完成显著特性后立即提交。
