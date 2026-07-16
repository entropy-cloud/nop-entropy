# 0800-1 nop-metadata 联邦查询 — 单表数据查询接口（P4-1）

> Plan Status: completed
> Last Reviewed: 2026-07-16
> Source: `ai-dev/design/nop-metadata/nop-metadata-roadmap.md` P4-1；`ai-dev/design/nop-metadata/01-architecture-baseline.md` §设计结论 #9 + §2.5 + §七 + §八；plan 0700-1/0700-2 Non-Blocking Follow-ups（entity/sql 类型表执行归 P4）
> Related: `2026-07-16-0800-2-nop-metadata-cross-table-join-and-aggregation.md`（successor，依赖本 plan 的单表查询基础）
> Draft Review: 经两轮独立子 agent 对抗性审查（含想象性分析 + live repo 核验），Blocker/Major 已全部收敛，consensus 达成（无 Blocker）。

## Purpose

把 P4-1（MetaTable 单表数据查询接口）从 `todo` 推进到 `done`：建立 nop-metadata 联邦查询的执行架构——一个统一查询 action，对任意 `tableType`（entity/external/sql）的逻辑表返回行数据，通过 `querySpace` 路由到正确的数据库。这是 P4 的地基，也解除 P2/P3 多个 plan 反复 deferred 的「entity/sql 类型表执行（querySpace→数据源解析）」阻塞（其扩展本身仍为 follow-up，但解析能力在本 plan 落地）。

## Current Baseline

- `NopMetaTableBizModel`（`nop-metadata-service/.../entity/NopMetaTableBizModel.java`）已有 createSqlTable / previewSqlFields / resolveTableFields（P3-1）/ profileTable（P2-7），**尚无任何"查表数据"action**。
- `IMetaDataSourceConnectionService.withConnection(datasourceType, connectionConfig, BiConsumer<Connection,DatabaseMetaData>)`（`.../service/connection/`）是按需建连 callback，服务 Catalog/质量/剖析（均为 **external-only**）。entity/sql 类型表的 Catalog/质量/剖析执行被反复 deferred（querySpace→数据源解析未建，归 P4，见 plan 0225-3/0420-1/0420-2/0530-1/0530-2/0700-1 的 Non-Blocking Follow-ups）。
- `MetaTableFieldResolver`（`.../service/field/`）按 tableType 分派返回可用字段集合（entity/external/sql，P3-2~P3-5），是字段引用校验基础。
- `SqlSelectFieldExtractor`（`.../service/sqlview/`）解析 sql 表 `sourceSql` 的 SELECT 字段名/别名（P3-1，方案 A，type=null）。
- 三类表的物理解析路径现状（关键差异，本 plan 要统一）：
  - **entity 表**：引用一个真实 ORM 实体（`baseEntityId → MetaEntity.entityName`；import 时 `OrmModelImporter.buildEntityTable` 写入 `table.setQuerySpace(em.getQuerySpace())`，故 entity 表 querySpace 来自 `MetaEntity.querySpace`）。其数据由平台 ORM（`IOrmTemplate`）管理——**不经 NopMetaDataSource**。**关键前置**：目标实体必须注册在 metadata 服务运行的同一 `IOrmSessionFactory` 中（否则需显式失败，不得静默空集）。注意：模块自身测试环境只加载 `nop-metadata.orm.xml`，故 entity 路径的端到端测试须用一个本模块已注册且被 importOrmModel 填充数据的实体表（如 `NopMetaModule` 对应的 entity 表）作 fixture。
  - **external 表**：`querySpace` 取自注册数据源（按 `NopMetaDataSource.querySpace == 表.querySpace` 匹配；现为 `findFirstByQuery` 取**首条匹配，非强制唯一**），数据在**外部库**，无 ORM 实体，经 `withConnection` 跑原生 SQL。
  - **sql 表**：`sourceSql` 为 SQL 文本，`querySpace` 列在表上但 `createSqlTable` 允许其为 **null**（§2.5）。其 querySpace 归属解析——**待裁定**（见 D2）。
- querySpace→数据源解析已有**三处重复的 private `resolveDataSourceOrThrow`**（`NopMetaTableBizModel:324`、`NopMetaQualityRuleBizModel:275`、`NopMetaProfilingRuleBizModel:125`），均 `findFirstByQuery` 取首条、**无 multi-match warning**（baseline §2.7.1 D1 文述"记录 warning"但未落地）。
- 标识符注入防护已有先例：`MetaQualityRuleExecutor.IDENTIFIER_PATTERN`（`^[A-Za-z_][A-Za-z0-9_]*$` 白名单 + PreparedStatement 值绑定，§2.7.1 D3）。
- 架构硬约束（§设计结论 #9 + §七）：所有查询走现有 ORM 层，实体 `querySpace` 字段已承担路由，**不引入额外 Driver/QuerySpace 抽象层**。
- §八 待定问题（与本 plan 相关）：无直接条目；querySpace→数据源/sql 表 querySpace 归属属本 plan 需新裁定的设计点。

## Goals

- 提供统一查询 action，输入 `metaTableId`（+ 可选过滤/分页），返回该逻辑表的行数据，**按 tableType 路由到正确执行路径**。
- 落地 querySpace→数据源/执行路径的解析能力，使 external 与 sql 表可被查询。
- 三类 tableType 各有端到端验证（真实返回行数据，非空壳）。
- 把关键设计裁定（D1 三路分派、D2 sql 表 querySpace 归属）写入 `01-architecture-baseline.md`。

## Non-Goals

- 跨表 JOIN（P4-2，successor plan 0800-2）。
- 指标/维度聚合查询（P4-3，successor plan 0800-2）。
- 将 Catalog/质量/剖析的执行范围从 external 扩展到 entity/sql（属各功能的独立 follow-up；本 plan 只提供解析能力，不改造它们）。
- 数据契约 MetaDataContract（P4-4）、Reconciliation（P4-5）。
- sql 表字段类型推断（方案 B/C，仍为 0700-1 follow-up）。

## Scope

### In Scope

- D1 裁定：单表查询的三路分派（entity→ORM、external→withConnection 原生 SQL、sql→withConnection 执行 sourceSql）。
- D2 裁定：sql 表 querySpace 的数据源归属解析策略（平台 ORM querySpace vs 外部 NopMetaDataSource）。
- querySpace→执行路径解析（external/sql 表定位到 NopMetaDataSource）。
- 统一查询 action（落点 `NopMetaTableBizModel`），含过滤/分页参数。
- 失败路径显式化（表不存在/无数据源/DISABLED/非 jdbc/解析失败均显式失败，不静默空集）。
- 三类 tableType 端到端测试。

### Out Of Scope

- JOIN、聚合、granularity→DATE_TRUNC、isDefault 运行时应用（归 0800-2）。
- entity/sql 表的 Catalog/质量/剖析执行扩展。
- 写操作（INSERT/UPDATE/DELETE），本 plan 仅只读查询。

## Execution Plan

### Phase 1 - 设计裁定 + querySpace 执行路径解析

Status: completed
Targets: `ai-dev/design/nop-metadata/01-architecture-baseline.md`（新增「§4.4 查询执行」节）；`nop-metadata-service` 新增 querySpace 解析组件

- Item Types: `Decision | Fix | Proof`

- [x] **D1 裁定并写入设计文档「§4.4 查询执行」**：单表查询按 tableType 三路分派，每路须明确执行机制与失败语义：
  - **entity** → 经 `IOrmTemplate`：`QueryBean.sourceName = metaEntity.entityName`（即 ORM 注册的实体名）+ `orm().findListByQuery(query)` 返回 `List<Map<String,Object>>`（或实体转 Map）；前置——实体须注册于运行时 `IOrmSessionFactory`，否则显式失败抛 inline ErrorCode（**不静默空集**）；返回字段集合与 `MetaTableFieldResolver` entity 分派一致。
  - **external** → 经 `withConnection` 跑限定表名的原生 SQL（querySpace→NopMetaDataSource 首条匹配）。
  - **sql** → 见 D2。
  - 裁定须与 §设计结论 #9 + §七 一致。
- [x] **D2 裁定并写入设计文档**：sql 表 querySpace 归属——**首版规则：sql 表要求非 null 且匹配到一个 NopMetaDataSource；querySpace 为 null 或匹配不到 NopMetaDataSource 时显式失败抛 inline ErrorCode（不静默空集、不伪造路由）。** "平台 ORM querySpace fallback" 分支**首版不做**（移入 Non-Blocking Follow-up），因无清晰机制对平台 querySpace 跑任意用户 SQL 文本。null/无匹配的失败语义写入 §4.4。
- [x] 实现 querySpace→NopMetaDataSource 解析（共享组件，落 `.../service/`，语义对齐 §2.7.1 D1 物理解析路径：`NopMetaDataSource.querySpace == 目标 querySpace` 的 `findFirstByQuery` 首条；找不到显式失败抛 inline ErrorCode）。**承认既有三处 `resolveDataSourceOrThrow` 重复**——本 plan 不强制重构它们（见 Non-Blocking Follow-up），新组件独立实现，避免与本 plan scope 混入。
- [x] 单测：querySpace 解析的 found / not-found（显式失败） / DISABLED / 多匹配（取首条）路径。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] D1/D2 裁定已写入 `01-architecture-baseline.md`「§4.4 查询执行」节；**具体性门禁**：D1 必须点名 entity 路径的 API（`QueryBean.sourceName=entityName` + `orm().findListByQuery`）+ 实体须注册前置 + 失败语义；D2 必须裁定 null/无匹配 querySpace 的行为，且 §4.4 内无未决的 "TBD"
- [x] querySpace→数据源解析对 found/not-found/DISABLED/多匹配均有显式行为（非静默空）
- [x] **无静默跳过**：解析失败抛 inline ErrorCode，不返回 null/空集合当作正常（Minimum Rules #24）
- [x] `ai-dev/design/nop-metadata/01-architecture-baseline.md` 已更新（D1/D2 裁定）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 统一单表查询 action + 三类表端到端验证

Status: completed
Targets: `nop-metadata-service/.../entity/NopMetaTableBizModel.java`；`.../service/`（查询执行组件）；`nop-metadata-service` 测试

- Item Types: `Fix | Proof`

- [x] 实现统一查询 action（落点 `NopMetaTableBizModel`，`@BizQuery`），签名含 `metaTableId` + 可选 filter（TreeBean，复用 §2.5.2 D1 的 Filter definition 结构）/ limit / offset；按 D1 三路分派执行。
- [x] entity 分派：经 `NopMetaTable.baseEntityId → NopMetaEntity.entityName` 解析 → `QueryBean.sourceName = entityName` + `orm().findListByQuery(query)`；filter/分页翻译到 ORM QueryBean；**实体未注册于运行时 IOrmSessionFactory 时显式失败抛 inline ErrorCode（不静默空集）**；返回字段集合与 `MetaTableFieldResolver` entity 分派一致。
- [x] external 分派：querySpace→NopMetaDataSource（首条匹配）→`withConnection`，对限定物理表名跑 `SELECT ... FROM <table> [WHERE] [LIMIT/OFFSET]`。**filter→WHERE 翻译复用 §2.7.1 D3 注入防护**：标识符经 `IDENTIFIER_PATTERN`（`^[A-Za-z_][A-Za-z0-9_]*$`）白名单，值用 PreparedStatement 绑定（参考 `MetaQualityRuleExecutor` 先例）。列名取自 `buildSql` JSON 的 `columnName`。
- [x] sql 分派：querySpace 解析（D2：非 null 且匹配 NopMetaDataSource，否则显式失败）→`withConnection` 执行 `sourceSql`（包一层子查询 + LIMIT/OFFSET 分页；filter 应用到外层，同样复用 §2.7.1 D3 白名单 + 参数绑定）。
- [x] **方言范围**：external/sql 路径的分页与 WHERE 翻译首版限定 H2 / MySQL / PostgreSQL（`LIMIT/OFFSET` 便携语法）；其他方言执行时显式失败（不静默）。
- [x] 失败路径显式：表不存在 / querySpace 无数据源 / DISABLED / 非 jdbc（由 withConnection 抛）/ sql querySpace null 或无匹配 / 实体未注册 均显式失败抛 inline ErrorCode（不静默空集、不吞异常）。
- [x] **端到端测试**：三类 tableType 各至少一条——真实建表/造数 → 调查询 action → 断言返回真实行数据（断言具体列值，stub 立即失败这些断言）。entity 路径 fixture 用本模块已注册且被 importOrmModel 填充的实体表（如 `NopMetaModule` 对应 entity 表）。
- [x] 失败路径测试：表不存在 / 无数据源 / DISABLED / sql querySpace null 或无匹配 / 实体未注册 至少各一条断言显式失败。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 统一查询 action 对 entity/external/sql 三类均返回真实行数据（非空壳、非固定 placeholder）
- [x] **端到端验证**（Minimum Rules #22）：从 GraphQL `queryTableData` 入口到最终行数据输出，三类 tableType 完整跑通
- [x] **接线验证**（Minimum Rules #23）：查询 action 运行时确实调用了 ORM（entity）/ withConnection（external/sql），有测试断言证明
- [x] **无静默跳过**（Minimum Rules #24）：新增的每个分派/失败分支在不可执行时显式失败，无空方法体/continue/吞异常
- [x] **新增功能测试**（Minimum Rules #25）：列出 queryTableData 三类分派 + 各失败路径的新增测试用例
- [x] `./mvnw clean install -pl nop-metadata -am -T 1C` BUILD SUCCESS（含测试）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0
- [x] `ai-dev/design/nop-metadata/01-architecture-baseline.md` 查询执行节与实现一致（若 Phase 1 已写，复核无漂移）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：所有条目 + Phase Exit Criteria 全部 `[x]` 后才能 `completed`。纯代码计划需构建验证。

- [x] D1/D2 设计裁定已落地设计文档且与实现一致
- [x] 统一查询 action 对三类 tableType 端到端可用（真实返回行数据）
- [x] querySpace→数据源解析能力已落地（解除 entity/sql 执行 deferred 的前置阻塞）
- [x] 不存在空壳实现（无空方法体/静默跳过/吞异常）
- [x] 必要 focused verification 已完成（三类表 + 失败路径测试全绿）
- [x] 受影响 owner docs 已同步（`01-architecture-baseline.md` 查询执行节）
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证查询 action 三路分派在运行时确实连通到 ORM / withConnection 并返回真实数据（端到端连通）
- [x] `./mvnw compile`（`-pl nop-metadata -am`）
- [x] `./mvnw test`（`-pl nop-metadata -am`）
- [x] checkstyle / 代码规范检查通过
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2026-07-16-0800-1-nop-metadata-federated-single-table-query.md --strict` 退出码 0

## Deferred But Adjudicated

（本 plan 为首个 P4 plan，暂无已裁定 deferred 项；执行中产生的优化项按规则归类记录于此。）

## Non-Blocking Follow-ups

- **既有 `resolveDataSourceOrThrow` 三处重复**（NopMetaTableBizModel / NopMetaQualityRuleBizModel / NopMetaProfilingRuleBizModel）：本 plan 新建独立共享组件，不强制重构既有三处（避免 scope 膨胀）；后续可统一收敛为单一组件。Why non-blocking：既有实现行为正确（取首条、显式失败），重复不构成 live defect。
- **baseline §2.7.1 D1 文述"多条匹配记录 warning"未落地**：本 plan 不新增 warning（与现状一致），若后续需 multi-match warning 应统一在三处实现。
- entity/sql 类型表的 Catalog/质量/剖析执行扩展（本 plan 提供了 querySpace 解析能力，但各功能的执行范围扩展属各自 follow-up，不在本 plan）。
- sql 表字段类型推断（方案 B/C，继承自 0700-1）。
- **sql 表 querySpace 的"平台 ORM querySpace fallback"**（D2 首版不做，移此）：若未来需让 sql 表 sourceSql 跑在平台 querySpace 上，需另行设计平台 querySpace 上跑任意 SQL 文本的机制。
- 复杂 filter 求值（嵌套 OR/AND/表达式），首版支持 TreeBean 标准叶子条件。

## Closure

Status Note: P4-1 单表数据查询接口落地。统一 `queryTableData` action 按 tableType 三路分派（entity→平台 ORM via IEntityDao.findAllByQuery / external→withConnection 原生 SELECT / sql→withConnection 执行 sourceSql 子查询），querySpace→数据源解析共享组件 `MetaDataSourceResolver` 解除 entity/sql 执行 deferred 的前置阻塞。三类 tableType 端到端返回真实行数据，失败路径全显式，无空壳实现。所有 Exit Criteria + Closure Gates 经独立子 agent closure audit 验证 PASS。
Completed: 2026-07-16

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（task id `ses_097904d8effeg1xjq8qQQ2WJzy`，fresh closure-audit session，read-only 核验 live code/tests/docs）
- Evidence:
  - **Phase 1 Exit Criteria**：§4.4 写入 `01-architecture-baseline.md:661-693`（D1 点名 entity 路径 API + 实体注册前置 + 失败语义；D2 裁定 sql querySpace null/无匹配显式失败，无 TBD）；querySpace 解析 found/not-found/DISABLED/null/multi-match 全显式（`MetaDataSourceResolver.java`）；`TestMetaDataSourceResolver` 5 用例全绿。
  - **Phase 2 Exit Criteria**（逐条 PASS）：
    - 三类返回真实行数据：entity `NopMetaTableBizModel.java:416 findAllByQuery`+`:424-429 orm_propValueByName`；external `:447 withConnection`+`:558 prepareStatement`+`:572 rs.next()`；sql `:470 withConnection`+`buildSqlSelectSql` 子查询。
    - 端到端（#22）：`testQueryEntityTableReturnsRows` / `testQueryExternalTableReturnsRows` / `testQuerySqlTableReturnsRows` 经 GraphQL/Bean 真实跑通。
    - 接线（#23）：entity `findAllByQuery`、external/sql `withConnection` 调用链连通，测试断言精确种子值（AMOUNT==10/NAME=="aaa"/VAL==100）证明真实 DB 往返。
    - 无静默跳过（#24）：表不存在 `:359` / 未知类型 `:372` / 实体悬空 `:383,390` / 未注册 `:397` / sql 空 `:464` / resolver null/no-match/DISABLED / 方言 `:498` / SQLException 包装不吞 `:586-589` 全显式抛 ErrorCode。
    - 新功能测试（#25）：`TestNopMetaTableQueryBizModel` 12 用例（3 分派 + 6 失败路径）+ `TestFilterToSqlTranslator` 18 用例 + `TestMetaDataSourceResolver` 5 用例。
  - **Closure Gates**：D1/D2 与实现一致（audit 后已同步 §4.4 entity 路径 API 描述）；querySpace 解析已落地；无空壳；owner doc §4.4 已同步；独立子 agent closure-audit 完成（本节）。
  - **Anti-Hollow 检查**：closure audit 追踪 `queryTableData` → ORM（entity `daoProvider().dao(name).findAllByQuery`）/ withConnection（external/sql）→ 真实行数据，三路均连通，由断言精确种子列值的测试证明（非空壳）。
  - `node ai-dev/tools/check-plan-checklist.mjs <plan> --strict` 退出码 0（38/38 items checked）。
  - `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0（0 findings，audit 独立复核 EXIT_CODE=0）。
  - `./mvnw clean install -pl nop-metadata -am -T 1C` BUILD SUCCESS；nop-metadata-service 135 tests 0 failure。
  - Deferred 项分类检查：Non-Blocking Follow-ups 均为 watch-only residual / optimization candidate（既有 resolveDataSourceOrThrow 三处重复、multi-match warning 未落地、entity/sql 的 Catalog/质量/剖析执行扩展、sql 字段类型推断、平台 ORM querySpace fallback、复杂 filter 求值），无 in-scope live defect 被降级。

Follow-up:

- 无剩余 plan-owned work。Non-Blocking Follow-ups 见 plan 尾部（既有 resolveDataSourceOrThrow 收敛、multi-match warning、entity/sql 的 Catalog/质量/剖析执行扩展、sql 字段类型推断方案 B/C、平台 ORM querySpace fallback、复杂 filter 求值），均不影响本 plan 的 supported baseline。
