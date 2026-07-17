# 0700-2 nop-metadata 联邦 JOIN 查询执行扩展 — sql/external 表参与 JOIN

> Plan Status: active
> Last Reviewed: 2026-07-17
> Source: `ai-dev/design/nop-metadata/nop-metadata-roadmap.md`（P4 联邦查询 deferred）；`ai-dev/design/nop-metadata/01-architecture-baseline.md` §4.4（查询执行）+ §4.4.1（D3/D4/D5 JOIN 路由）；plan `2026-07-16-0800-2` Non-Blocking Follow-ups「sql/external 表作为 JOIN 右表」；plan `2026-07-17-0228-3` Deferred「sql/external Join 支持」
> Mission: nop-metadata
> Work Item: P4+ — sql/external 类型表作为 JOIN 端点的联邦查询执行（queryJoinData 扩展）
> Related: `2026-07-17-0700-1-nop-metadata-join-sql-external-schema-and-validation.md`（predecessor，提供 schema 变更 + 校验；本 plan 依赖其 schema 落地后启动）
> Draft Review: 两轮独立子 agent 对抗性审查（含想象性分析 + live repo 核验）。R1 发现 3 Major + 4 Minor（D1 未 flag 混合端点同库 JOIN 连接载体 / 跨库拼接 join-key 命名空间不一致致静默空集 / Closure Gates 缺 check-plan-checklist 硬门禁）已全部 live-verified 修复（D1 扩展为 4 子问题）。R2 共识 APPROVE-WITH-MINORS（无 Blocker/Major，3 新 Minor 为 cosmetic），可执行性共识达成。

## Purpose

把联邦 JOIN 查询执行（`queryJoinData`）从「仅 entity-entity」扩展到「entity/external/sql 任意组合均可作为 JOIN 端点」。本 plan 关闭 plan 0800-2 的 Non-Blocking Follow-up「sql/external 表作为 JOIN 右表（继承自 0700-2，首版 entity-entity）」。

执行序：predecessor 0700-1（schema + 校验）先落地；本 plan 在其 schema 变更可用后启动。

## Current Baseline

- `queryJoinData(metaTableId, joinId, filter?, limit?, offset?, context)` 已落地于 `NopMetaTableBizModel`（plan 0800-2），经 `MetaJoinExecutor`（`nop-metadata/.../service/query/MetaJoinExecutor.java`）执行：D3 路由按左右 `querySpace` 是否相同分派——同库走原生 JOIN SQL（`orm().executeQuery`，行 200-201）；跨库（不同 querySpace）走应用层拼接（D5 `executeCrossDbMerge`→`fetchEntityRows`，行 206-249，内存按 join key 合并）。`joinType=right` 首版全局显式不支持。
- 当前 JOIN 执行**仅 entity-entity**（架构基线 §4.4 行 883）：`MetaJoinExecutor` 硬编码 entity 路径（`resolveEntityOrThrow`/`requireRegistered`/`resolveEntityColumns`/`fetchEntityRows` 均围绕 `NopMetaEntity`）；`NopMetaTableJoin` 仅引用 NopMetaEntity，sql/external 表无 entity 无法作为端点。0800-2 closure 的 Non-Blocking Follow-up 明确「sql/external 表作为 JOIN 右表」待后续。
- sql/external 单表数据查询已可用（plan 0800-1 `MetaDataSourceResolver` + queryTableData 三路分派：entity→ORM / external→withConnection / sql→withConnection 原生 SELECT）。单表取数能力对三类表均成立。**注意接线**：`MetaJoinExecutor` 经 `MetaQueryContext` 注入的依赖是 `daoProvider / orm / connectionService / dataSourceResolver / fieldResolver / filterTranslator`，无 `NopMetaTableBizModel` 引用——「复用单表取数」须裁定接线方式（见 D1 子问题 3）。
- **混合端点 JOIN 的关键约束**（live 事实）：entity 端点的 `querySpace` 来自 `NopMetaEntity.querySpace`（平台 ORM 管理），该 querySpace 通常**无对应 `NopMetaDataSource`**（`MetaDataSourceResolver.resolveActiveOrThrow` null/无匹配即抛 `ERR_RESOLVE_NO_DATASOURCE`）。而架构基线 §4.4.3 D1（plan 1905-1 落地）已为 entity 表 Catalog/Profiling 建立第三种连接获取机制：平台 `IJdbcTransaction.getConnection()`（经 `ITransactionTemplate.runInTransaction(entityQuerySpace, ...)`）。本 plan 的混合端点同库 JOIN 须裁定如何触达 entity 端点物理表。
- **跨库拼接 key 命名空间约束**（live 事实）：`executeCrossDbMerge` 用 `stringKey(r.get(rightField))` / `stringKey(l.get(leftField))`（行 225/234）按 `leftField`/`rightField` 取 row Map 值。但 entity 行 key 是 **camelCase 属性名**（`orm_propValueByName`，行 273），sql/external 行 key 是**物理列名**（`rs.getMetaData().getFieldName(i)`，H2 常大写）。两侧命名空间不同，混合端点拼接须规范化否则静默空集。
- predecessor 0700-1 落地后：`NopMetaTableJoin` 可引用 sql/external 表端点；join 定义与 Measure 跨表校验已扩展。本 plan 消费该 schema 变更实现执行。

## Goals

- `queryJoinData` 能执行端点含 sql/external 类型表的 NopMetaTableJoin（entity↔sql、entity↔external、sql↔external、sql↔sql 等组合）。
- JOIN 路由沿用 0800-2 既有 D3/D4/D5 语义（同 querySpace→原生 JOIN SQL；不同 querySpace→应用层拼接），对 sql/external 端点一致适用。
- sql/external 端点的 join 字段解析为该表真实列名（external 读 `buildSql` / sql 读 SELECT 解析）。

## Non-Goals

- **不**改 ORM schema 或 join/Measure 校验（predecessor 0700-1 的结果面）。
- **不**实现 `joinType=right`（沿用 0800-2 全局显式不支持裁定）。
- **不**改既有 entity-entity 同库 JOIN 路径（保持 `orm().executeQuery`，行 200-201 不重写为 withConnection）。
- **不**做递归多跳 JOIN 执行（A→B→C 单次查询内多跳，沿用单 join 直连语义）。
- **不**做聚合查询（`queryAggregation`）的 sql/external JOIN 扩展——聚合执行已覆盖三类型单表（0800-2 D6），JOIN 聚合为本 plan 之外的增量。

## Scope

### In Scope

- `MetaJoinExecutor` / `queryJoinData` 扩展：识别并执行端点含 sql/external 表的 NopMetaTableJoin。
- sql/external 端点的列名解析（join 字段 → 真实列名）。
- 路由裁定：sql/external 端点与对端的 querySpace 比较分派（同库原生 JOIN SQL / 跨库应用层拼接），含混合端点连接载体与 key 命名空间规范化。

### Out Of Scope

- ORM schema 变更（→ 0700-1）。
- joinType=right（→ 沿用显式失败裁定）。
- 多跳递归 JOIN（→ watch-only）。
- JOIN 聚合（→ 独立增量）。

## Execution Plan

### Phase 1 - sql/external 端点 JOIN 执行路由与取数

Status: planned
Targets: `MetaJoinExecutor`（`nop-metadata/.../service/query/MetaJoinExecutor.java`）；`NopMetaTableBizModel.queryJoinData`；列名解析复用点（external 读 `buildSql` / sql 经 `SqlSelectFieldExtractor`）

- Item Types: `Decision | Fix`

- [ ] **D1 sql/external 端点路由裁定**（Decision）：裁定端点含 sql/external 表时的分派规则，逐项裁定并写入 §4.4.1：
  1. **querySpace 来源区分**：entity 端点 querySpace 取自 `NopMetaEntity.querySpace`，external/sql 端点取自 `NopMetaTable.querySpace`（§4.4.1 D3 行 889），路由比较须 cross-source；
  2. **混合端点同库 JOIN 的连接载体**：entity 端点物理表经何种连接触达——裁定是否引入 §4.4.3 D1 平台 `IJdbcTransaction.getConnection()` 机制（`ITransactionTemplate.runInTransaction(entityQuerySpace, ...)`），或限制首版混合端点同库 JOIN 仅在 entity querySpace 已注册 `NopMetaDataSource` 时支持（否则显式失败）。**明确既有 entity-entity 同库路径保持 `orm().executeQuery` 不变（不重写为 withConnection）**；external/sql↔external/sql 同库 JOIN 走 withConnection 原生 JOIN SQL（标识符白名单 + 参数绑定，沿用 0800-2 external 聚合范式）；
  3. **单表取数接线方式**：`MetaJoinExecutor` 无 `NopMetaTableBizModel` 引用，裁定跨库拼接（D5）如何取 sql/external 单表行——在 executor 内复用 `MetaQueryContext` 现有依赖（connectionService/dataSourceResolver）实现取数，避免循环依赖（不注入 BizModel）；
  4. **跨库拼接 key 命名空间规范化**：entity 行（camelCase 属性名）与 sql/external 行（物理列名）命名空间不同，须统一规范化或按解析后的列名重建 join-key 索引；因命名空间错配导致的**整体不命中不得静默返回空集**（与正常 inner join 无匹配行丢弃区分，Anti-Hollow）。
- [ ] **列名解析**（Fix）：JOIN 执行时把 sql/external 端点的 join 字段解析为真实列名（sql→SELECT 解析列；external→`buildSql` JSON 列），供原生 JOIN SQL 或应用层拼接使用。
- [ ] **JOIN 执行扩展**（Fix）：扩展 `MetaJoinExecutor` 执行端点含 sql/external 表的 join（按 D1 路由），结果 schema 沿用 0800-2 约定（右表列冲突加 `<alias>.` 前缀）。

Exit Criteria:

- [ ] §4.4.1 已记录 D1 路由裁定（含 querySpace 来源区分、混合端点连接载体裁定、单表取数接线、key 命名空间规范化）
- [ ] 存在 AutoTest：entity↔sql（或 entity↔external）同库 JOIN 经原生 JOIN SQL 执行，断言 join 后行数据正确（非空、join key 值匹配）
- [ ] 存在 AutoTest：sql↔external（或任意组合）不同 querySpace 经应用层拼接执行，断言内存合并结果正确（非空、具体 key 值匹配）
- [ ] **跨库混合端点命名空间**（Anti-Hollow）：AutoTest 断言 entity↔sql 跨库 JOIN 不返回静默空集——join key 经命名空间规范化后实际命中（断言行数>0 且 key 值匹配，而非空结果）
- [ ] 存在 AutoTest：join 字段在 sql/external 端点不存在时执行显式失败（错误码 + .param）
- [ ] 存在 AutoTest：混合端点同库 JOIN 且 entity querySpace 无 NopMetaDataSource 时，按 D1 子问题 2 裁定显式失败或成功（与裁定一致）
- [ ] **端到端验证**：从「queryJoinData(metaTableId, joinId)」入口，经 MetaJoinExecutor 路由分派，到返回 join 后行数据，完整路径跑通（端点含 sql/external 表）
- [ ] **接线验证**：queryJoinData → MetaJoinExecutor → sql/external 端点执行分支在运行时确实被调用（test 断言取数发生，非返回 entity 路径结果）
- [ ] **无静默跳过**：端点 tableType 组合不支持时显式失败而非返回空结果；joinType=right 沿用显式失败；跨库 join key 未匹配不静默丢弃
- [ ] `./mvnw compile -pl nop-metadata -am` 通过
- [ ] `./mvnw test -pl nop-metadata -am` 通过
- [ ] §4.4.1 已更新（若 D1 改变语义）；否则明确写 `No owner-doc update required`
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 回归与边界

Status: planned
Targets: `TestNopMetaJoinBizModel`（`nop-metadata/.../service/TestNopMetaJoinBizModel.java`）

- Item Types: `Proof`

- [ ] **既有 entity-entity JOIN 用例回归**（Proof）：0800-2 的 5 个 JOIN 用例（`testSameDbJoinReturnsRealRows`/`testCrossDbJoinAppLayerMerge`/`testJoinTypeRightExplicitlyFails`/`testJoinNotFoundFails`/`testJoinFieldNotResolvedFails`）在 schema 变更后仍全绿。
- [ ] **边界用例**（Proof）：两端点均为 sql/external 同库/跨库组合的显式覆盖。

Exit Criteria:

- [ ] 既有 entity-entity JOIN 测试全绿（无回归）
- [ ] sql/external 端点组合（含同库/跨库/right 失败）有显式覆盖
- [ ] `./mvnw test -pl nop-metadata -am` 全绿
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] 0800-2 Non-Blocking Follow-up「sql/external 表作为 JOIN 右表」在本 plan 落地后收口
- [ ] `queryJoinData` 可执行端点含 sql/external 表的 JOIN（非空壳）
- [ ] 既有 entity-entity JOIN 执行无回归
- [ ] §4.4.1 design doc 已记录 sql/external 端点路由裁定
- [ ] 不存在被静默降级到 deferred 的 in-scope live defect
- [ ] 受影响 owner docs 已同步或明确 `No owner-doc update required`
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：MetaJoinExecutor 的 sql/external 执行分支在运行时被调用（端到端 test 断言取数发生）；跨库混合端点 JOIN 不返回静默空集
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2026-07-17-0700-2-nop-metadata-federated-join-sql-external-execution.md --strict` 退出码 0
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0
- [ ] `./mvnw compile -pl nop-metadata -am`
- [ ] `./mvnw test -pl nop-metadata -am`
- [ ] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### JOIN 聚合（queryAggregation）的 sql/external JOIN 扩展

- Classification: `optimization candidate`
- Why Not Blocking Closure: 本 plan 结果面是 queryJoinData（行级 JOIN）。聚合执行已覆盖三类型单表（0800-2 D6）；JOIN + 聚合组合为独立增量，非本 plan 必需。
- Successor Required: no

## Non-Blocking Follow-ups

- 多跳递归 JOIN 执行（A→B→C 单查询内多跳）— watch-only，沿用 0800-2 沿革。
- sql 表列类型推断（方案 B/C）— 沿用 0800-1 follow-up。

## Closure

Status Note: <<完成时填写>>
Completed: <<YYYY-MM-DD>>

Closure Audit Evidence:

- Reviewer / Agent: <<独立子 agent>>
- Evidence: <<每条 Exit Criterion + Closure Gate 验证结果；check-plan-checklist / scan-hollow 退出码>>

Follow-up:

- <<完成时填写>>
