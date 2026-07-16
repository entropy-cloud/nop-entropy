# 0900-2 nop-metadata Reconciliation 对账（P4-5）

> Plan Status: completed
> Last Reviewed: 2026-07-16
> Source: `ai-dev/design/nop-metadata/nop-metadata-roadmap.md` P4-5；`ai-dev/design/nop-metadata/08-reconciliation.md`（§一~§八 + Open Questions）；`ai-dev/design/nop-metadata/01-architecture-baseline.md` §2.5（MetaTable）+ §4.4（查询执行）
> Related: `2026-07-16-0900-1-nop-metadata-data-contract.md`（P4-4，并行独立结果面）；P4-1 已完成（`queryTableData` 提供表行数据，本 plan 消费它取待对账数据）
> Draft Review: 经两轮独立子 agent 对抗性审查（含想象性分析 + live repo 核验）。R1 Blocker（B1 queryTableData 返回类型错 + B2 取数接线未设计）+ 5 Major 已全部收敛；R2 consensus YES（无 Blocker），R2 的 near-Blocker Major（queryTableData 访问机制：注入 NopMetaTableBizModel 具体类）+ FK 命名/策略大小写 Minor 已修复。可执行性共识达成。

## Purpose

把 P4-5（Reconciliation 对账）从 `todo` 推进到 `done`：新增 3 个实体（`NopMetaReconciliationConfig`/`NopMetaReconciliationResult`/`NopMetaReconciliationEntity`）并自动暴露 GraphQL CRUD，定义可插拔 `IReconciliationService` 接口 + 内置本地匹配实现（exact + levenshtein fuzzy，对 `NopMetaReconciliationEntity` 缓存候选集匹配），实现对账执行器（BizModel action 调 `queryTableData` 取表行传入执行器→逐行匹配→按钉死规则判 MATCHED/UNMATCHED/MULTIPLE→写结果统计与明细），实现人工确认 action。收口"实体与候选知识库可对账、结果可存、可人工确认"这一结果面。

## Current Baseline

- **25 实体已建模**（已 `rg -c '<entity '` 核验=25，见 plan 0900-1）。**无任何 Reconciliation 实体**（已核验 orm.xml，无 Recon className）。
- 实体建模 + GraphQL 自动暴露 + BizModel action + 执行器组件模式均见 plan 0900-1 描述（同模块同模式）。
- **ErrorCode 惯例**：本模块**不**用集中式 `NopMetadataErrors`（空 interface）；新增 ErrorCode 按惯例内联为各 BizModel/Executor 内 `static final ErrorCode ERR_... = ErrorCode.define(...)`（见 `NopMetaTableBizModel`、`MetaQualityRuleExecutor`）。
- **表行数据取数契约（重要，B1/B2 核心）**：P4-1 `NopMetaTableBizModel.queryTableData(metaTableId, filter, limit, offset, context)` 是 `@BizQuery`，返回 **`Map<String,Object>`**（结构 `{tableType, items:[{<列名>:<值>, ...}, ...]}`，见 `buildQueryResult`）。它要求 `IServiceContext` 参数。**取数接线裁定（B2 方案 b + 访问机制钉死）**：仓库无跨 BizModel 注入另一 BizModel 的生产先例，且 `INopMetaTableBiz` 是空接口（无 queryTableData 声明）。本 plan 采用——**由 `NopMetaReconciliationConfigBizModel.executeReconciliation` action 内注入并调用 `NopMetaTableBizModel` 具体类（NopIoC bean）**：`@Inject NopMetaTableBizModel tableBizModel`（protected 字段），action 用自身 `IServiceContext` 调 `tableBizModel.queryTableData(tableId, null, null, null, context)` 取得 `items`（`List<Map>`），再把行列表传入 `ReconciliationExecutor.execute(config, items)`。执行器是纯组件，不持有 BizModel、不伪造 context、不复制取数逻辑。**替代方案（提取共享 fetcher 重构 P4-1）为 Non-Blocking Follow-up**，首版避免 scope 膨胀与 P4-1 重构。items 中每行 Map 的 key 为物理列名（external/sql）或 ORM 属性名（entity），`config.columnName` 须与之匹配（执行前经 `MetaTableFieldResolver` 校验，否则显式失败）。
- **非 BizModel 组件的 DAO 访问**：`LocalReconciliationService` 查询 `NopMetaReconciliationEntity` 经 NopIoC `@Inject IDaoProvider` / `@Inject IEntityDao<NopMetaReconciliationEntity>`（protected 字段），对齐 `MetaQueryContext`/其它 service bean 的 DAO 注入模式。
- 平台 IoC 约束：`@Inject` 须 protected/setter，不支持 private 字段注入（AGENTS.md）。
- **设计文档现状**：`08-reconciliation.md` 定义 3 实体模型（Config: configName/displayName/moduleId/serviceUrl/identifierSpace/schemaSpace/targetEntityType/autoMatch/autoMatchThreshold/columns[]（**多列**）; Result: configId/executeTime/tableId/statistics/details[]; Entity: entityId/entityName/entityType/identifierSpace/properties[]/lastSyncedAt）+ 标准 Reconciliation API 流程 + 匹配策略（exact/fuzzy/phonetic/semantic）+ 人工确认 + 属性扩展。**§3.2 evaluateMatch 判定规则**：单候选且≥阈值→matched；单候选<阈值或多候选→multiple；空→unmatched。**设计文档含大段 Java 代码**（§3.2/§3.3/§4.2/§5.3，含 `ReconciliationServiceFactory`/`ReconciliationService` 等不存在类型），违反 Minimum Rule #14，本 plan D 决策须清理。**Open Questions**：服务认证 / 结果版本化 / 流式对账 / 实体缓存定时刷新。
- **关键张力**：`08-reconciliation.md` 同时包含"调外部 HTTP 服务"与"本地 MatchStrategy"两条路径。本 plan D1 裁定首版取本地路径以确保 H2/AutoTest 环境可端到端验证。
- `NopMetaReconciliationEntity` 作为"候选实体缓存"——被本地匹配器作为候选集读取，也是外部匹配结果的缓存落点（设计 §2.3）。

## Goals

- 新增 3 实体（Config/Result/Entity）ORM 建模，自动暴露 GraphQL CRUD。
- 定义可插拔 `IReconciliationService` 接口（入参含 matchStrategy）+ 内置本地实现（`LocalReconciliationService`：exact + levenshtein fuzzy，候选来自 `NopMetaReconciliationEntity` 按 identifierSpace/type 过滤）。
- 实现对账执行器：由 BizModel action 调 `queryTableData` 取 `items`（行列表）传入执行器 → 逐列值匹配 → 按钉死的 status 判定规则判 MATCHED/UNMATCHED/MULTIPLE → 写 Result（统计 + 明细 JSON）。
- 实现人工确认 action（单条 + 批量，更新明细 status=MANUAL + selectedId）。
- 裁定首版范围（D1 服务模型 / D2 本地匹配策略集 / D3 明细存储形态 / D4 Config 模型简化 / D5 status 判定规则单一事实源 / D6 设计文档 Java 代码清理）并写入设计文档。
- 执行器（含匹配/阈值判定/失败路径）+ 人工确认各端到端/focused 测试，全在 H2 本地候选集跑通（无需外部 HTTP）。

## Non-Goals

- 数据契约 MetaDataContract（P4-4，plan 0900-1）。
- 外部 HTTP Reconciliation 服务实现（Wikidata/OpenRefine 兼容端点）：首版只定义接口 + 本地实现，外部 HTTP impl 为 follow-up（D1）。
- 语义匹配（semantic，需 embedding/模型）、语音匹配（phonetic）：follow-up（D2）。
- 多列对账（首版单列 columnName；多列 columns[] 为 follow-up，D4）。
- 属性扩展（expandProperties 把候选属性写回表）：follow-up。
- 实体缓存定时自动刷新、结果版本化、流式对账（设计 Open Questions，均 follow-up）。
- 候选实体自动入库（首版候选集由测试/管理手动维护；外部服务回填为 follow-up）。

## Scope

### In Scope

- D1 裁定：`IReconciliationService` 可插拔接口 + 首版 `LocalReconciliationService`（本地候选集匹配）；外部 HTTP 实现移 follow-up。裁定写入 `08-reconciliation.md`。
- D2 裁定：首版匹配策略 exact + fuzzy(levenshtein)；phonetic/semantic 移 follow-up。
- D3 裁定：Result 明细存储形态——单 Result 行承载 `statistics`(JSON: totalRows/matchedRows/unmatchedRows/multipleMatches/matchRate) + `details`(mediumtext + stdDomain json，per-row 数组 {rowIndex,originalValue,status,candidates[],selectedId})，对齐 **ProfilingResult.tableStats/columnStats**（mediumtext+json）先例（**非** QualityResult.details，后者是 json-4000）。
- D4 裁定：Config 首版简化为**单列对账**（`columnName`/`matchStrategy`/`targetEntityType` 挂 Config 顶层），设计 §2.1 的多列 `columns[]` 移 follow-up；据此**重写** `08-reconciliation.md` §2.1 Config 模型为单列版本。
- D5 裁定（status 判定规则单一事实源）：采用**本 plan 钉死规则**并同步重写设计 §3.2：无候选→UNMATCHED；恰 1 候选且 score≥阈值→MATCHED；恰 1 候选且 score<阈值→MULTIPLE；≥2 候选→MULTIPLE（最高分候选仍列出，由人工确认选择）。autoMatch 关闭时，有候选一律→MULTIPLE（交人工）。
- D6：清理 `08-reconciliation.md` 中 §3.2/§3.3/§4.2/§5.3 的 Java 代码块（违反 Rule #14），重写为行为/契约描述（Minimum Rule #14）。
- 3 实体建模 + dict（reconciliation-status: MATCHED/UNMATCHED/MULTIPLE/MANUAL；match-strategy: EXACT/FUZZY）。
- 对账执行器实现（BizModel 取数 → 执行器匹配 → 阈值判定 → 写 Result）。
- 人工确认 action（单条 + 批量）。

### Out Of Scope

- 外部 HTTP 服务实现、phonetic/semantic 匹配、多列对账、属性扩展、缓存自动刷新、结果版本化、流式对账。
- 候选实体从外部自动入库。

## Execution Plan

### Phase 1 - 设计裁定 + 3 实体建模

Status: completed
Targets: `nop-metadata/model/nop-metadata.orm.xml`（新增 3 实体）；`ai-dev/design/nop-metadata/08-reconciliation.md`（§一/§二/§三/§五 收口 D1~D6）；新增 dict `meta/reconciliation-status`、`meta/match-strategy`

- Item Types: `Decision | Fix`

- [x] **D1 裁定并写入 `08-reconciliation.md`**：首版采用可插拔 `IReconciliationService` 接口 + `LocalReconciliationService`（对 `NopMetaReconciliationEntity` 候选集做 exact/fuzzy 匹配）。外部 HTTP（OpenRefine/Wikidata 兼容）实现为 follow-up，因其依赖外部可用性与认证（设计 Open Questions 未决），无法在 H2/AutoTest 端到端验证。接口设计须使外部 impl 可后续插入不改执行器。
- [x] **D2 裁定并写入 `08-reconciliation.md` §5**：首版策略 exact（完全相等，score=1.0）+ fuzzy（levenshtein 归一化相似度）。phonetic/semantic 移 follow-up（需额外算法/模型依赖）。
- [x] **D3 裁定并写入 `08-reconciliation.md` §2.2**：Result 明细 = `statistics`(json-4000) + `details`(mediumtext + stdDomain json，per-row 数组) 单行承载，对齐 **ProfilingResult.tableStats/columnStats**（mediumtext+json）先例；不拆子表（超大表为 follow-up）。
- [x] **D4 裁定并重写 `08-reconciliation.md` §2.1 Config 模型**：首版 Config 简化为单列对账（`columnName`/`matchStrategy`/`targetEntityType` 顶层），删除/标注 `columns[]` 多列为 follow-up；`serviceUrl`/`schemaSpace`/`schedule` 标注为首版不启用（serviceUrl 归外部 HTTP impl follow-up）。
- [x] **D5 裁定并重写 `08-reconciliation.md` §3.2 evaluateMatch**：以本 plan 钉死规则替换原 Java 代码中的判定逻辑——无候选→UNMATCHED；恰 1 候选且 score≥阈值→MATCHED；恰 1 候选且 score<阈值→MULTIPLE；≥2 候选→MULTIPLE；`autoMatch=false` 时有候选一律→MULTIPLE。
- [x] **D6 清理 `08-reconciliation.md` Java 代码（Rule #14）**：将 §3.2/§3.3/§4.2/§5.3 的 Java 代码块重写为行为/契约描述（类签名移除，保留输入输出契约与判定语义）。
- [x] 新增 `NopMetaReconciliationConfig`：`configId`(PK,seq)/`configName`/`displayName`/`metaModuleId`(→NopMetaModule,nullable，对齐 FK 命名惯例)/`metaTableId`(→NopMetaTable)/`columnName`(待对账列)/`identifierSpace`/`targetEntityType`(nullable)/`matchStrategy`(dict:meta/match-strategy)/`autoMatch`(bool)/`autoMatchThreshold`(double)/`extConfig`(json-4000) + 审计列 + version。to-one: `metaTable`（join on metaTableId）、`metaModule`（join on metaModuleId）。索引 `IX_NOP_META_RECON_CONFIG_TABLE`(metaTableId)。
- [x] 新增 `NopMetaReconciliationResult`：`resultId`(PK,seq)/`configId`(→Config)/`metaTableId`(→NopMetaTable)/`executeTime`/`statistics`(json-4000)/`details`(mediumtext + stdDomain json，明细数组)/`extConfig`(json-4000) + 审计列 + version。to-one: `config`、`metaTable`。索引 `IX_NOP_META_RECON_RESULT_CONFIG`(configId, executeTime)（时序）。
- [x] 新增 `NopMetaReconciliationEntity`：`reconEntityId`(PK,seq)/`entityId`(业务标识)/`entityName`/`entityType`/`identifierSpace`/`properties`(json-4000)/`lastSyncedAt`/`extConfig`(json-4000) + 审计列 + version。索引 `IX_NOP_META_RECON_ENTITY_TYPE`(entityType, identifierSpace)（候选检索）。
- [x] 新增 dict `meta/reconciliation-status`（MATCHED/UNMATCHED/MULTIPLE/MANUAL，大写——status 枚举惯例）、`meta/match-strategy`（exact/fuzzy，**小写**——type/category 枚举惯例，对齐 tableType `entity`/`sql`/`external`、ruleType `not_null`/`unique` 等先例）：**两处都加**——orm.xml `<dicts>` 内联声明 + `nop-metadata-meta/src/main/resources/_vfs/dict/meta/*.dict.yaml`。
- [x] `./mvnw compile -pl nop-metadata -am` 通过；确认 3 实体 GraphQL CRUD 自动暴露。

Exit Criteria:

- [x] D1~D6 裁定写入 `08-reconciliation.md`（§一定位 + §二模型[Config 单列/Result 明细/Entity] + §三.2 判定规则 + §五策略 + Java 代码已清理）
- [x] `nop-metadata.orm.xml` 新增 3 实体，各含 PK/业务列/审计列/to-one/索引；`./mvnw compile -pl nop-metadata -am` 通过
- [x] 2 个 dict 在 orm.xml `<dicts>` 与 `_vfs/dict/` 两处均存在，大写值齐备
- [x] 3 实体 GraphQL findPage/get/save/delete 可用（AutoTest 或 GraphQL 验证）
- [x] **新功能测试（#25）**：3 实体 CRUD 至少有 focused test 覆盖 create+query
- [x] **无静默跳过（#24）**：本 phase 仅实体建模+文档，无新公共方法分支 → N/A（匹配/确认失败路径在 Phase 2 覆盖）
- [x] 受影响 owner docs 已同步（`08-reconciliation.md` D1~D6）；或 `No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 匹配服务 + 对账执行器 + 人工确认

Status: completed
Targets: `nop-metadata/nop-metadata-service/.../service/reconciliation/IReconciliationService.java`（新增接口）；`.../service/reconciliation/LocalReconciliationService.java`（新增）；`.../service/reconciliation/ReconciliationExecutor.java`（新增）；`.../service/entity/NopMetaReconciliationConfigBizModel.java`（新增 action，含内联 ErrorCode）；`.../service/entity/NopMetaReconciliationResultBizModel.java`（新增确认 action，含内联 ErrorCode）

- Item Types: `Fix | Proof`

- [x] 定义 `IReconciliationService` 接口：输入 `(value, targetType, identifierSpace, matchStrategy, limit)` → 返回候选列表（entityId/entityName/entityType/score/properties）。**纯接口，无外部依赖**，便于后续插外部 HTTP impl（入参含 `matchStrategy`，使策略可由调用方控制）。
- [x] 实现 `LocalReconciliationService`：按 `entityType`+`identifierSpace` 从 `NopMetaReconciliationEntity` 检索候选 → 按 `matchStrategy`(EXACT/FUZZY-levenshtein) 计算 score → 排序 + 取 limit。候选为空 → 返回空列表（**不静默伪造候选**）。
- [x] 实现 `ReconciliationExecutor.execute(config, rows)`（**纯组件，rows 由 BizModel 传入**，执行器不持有 BizModel、不伪造 context）：
  - 入参 `rows: List<Map<String,Object>>`（由 BizModel action 调 `queryTableData` 取得的 `items`），按 `config.columnName` 取每行该列的值。
  - 逐行调 `IReconciliationService` 取候选 → 按 **D5 钉死规则**判 status。
  - 汇总 statistics(totalRows/matchedRows/unmatchedRows/multipleMatches/matchRate) + details(per-row) → 写 `NopMetaReconciliationResult`。
- [x] 实现 `executeReconciliation(configId)` action（`@BizMutation`，落 `NopMetaReconciliationConfigBizModel`）：加载 config → 校验 `columnName` 在 `MetaTableFieldResolver` 解析字段集合内（否则显式失败）→ 经 `@Inject NopMetaTableBizModel tableBizModel`（protected 字段）调 `tableBizModel.queryTableData(config.metaTableId, null, null, null, context)` 取 `items`（**失败抛 ErrorCode**）→ 调 `ReconciliationExecutor.execute(config, items)` → 返回 Result。context 取 action 自身的 `IServiceContext`。
- [x] 失败路径显式（内联 ErrorCode）：config 不存在 / metaTableId 不存在 / columnName 非法 / queryTableData 失败 / result 越界 rowIndex 均抛 ErrorCode（不吞异常）；空候选→UNMATCHED 体现在结果（非整体异常、不静默 pass）。
- [x] 实现人工确认 action：`confirmMatch(resultId, rowIndex, selectedEntityId)`（更新 details[rowIndex].status=MANUAL + selectedId）+ `batchConfirmMatches(resultId, selections)`（`@BizMutation`，落 `NopMetaReconciliationResultBizModel`）。越界 rowIndex / result 不存在显式失败（不静默忽略）。**注**：rowIndex 为 details JSON 数组下标，首版语义绑定本次执行快照（重排/分页漂移为 follow-up，可后续引入 stable rowKey）。

Exit Criteria:

- [x] `LocalReconciliationService` 对真实候选集返回带 score 的候选（EXACT score=1.0；FUZZY levenshtein 归一化），空候选返回空列表（有测试断言）
- [x] `executeReconciliation` 端到端可用：对一个 config（指向有数据的表 + 已播种候选实体）执行，Result.statistics 与 details 反映真实匹配（MATCHED/UNMATCHED/MULTIPLE 计数与种子一致，非空壳）
- [x] **端到端验证（#22）**：从 `executeReconciliation` 入口 → queryTableData 取 items → 本地匹配 → D5 阈值判定 → Result 写入 的完整路径有测试跑通，断言真实匹配率/状态分布
- [x] **接线验证（#23）**：`IReconciliationService`/`LocalReconciliationService` 确实在 executor 运行时被调用（测试通过真实候选检索 + score 断言证明）；**executor 的 rows 确实由 BizModel 调用 `queryTableData` 取得**（非伪造数据——测试中 queryTableData 返回真实表行种子值，匹配结果与之对应）
- [x] **无静默跳过（#24）**：config/metaTableId 不存在 / columnName 非法 / queryTableData 失败 / result 越界 rowIndex 均显式失败抛 ErrorCode（测试覆盖至少 2 条）；空候选→UNMATCHED 不静默 pass 整体（有断言）
- [x] 人工确认 action：单条 + 批量均更新 details status=MANUAL + selectedId；越界失败（有测试）
- [x] **新功能测试（#25）**：新增测试覆盖——EXACT 匹配 MATCHED、FUZZY 匹配（阈值上→MATCHED/阈值下→MULTIPLE）、UNMATCHED（无候选）、MULTIPLE（多候选）、autoMatch=false→MULTIPLE、人工确认单条/批量、失败路径（config/tableId/columnName/越界）
- [x] 受影响 owner docs 已同步（`08-reconciliation.md` 执行流程/判定规则与实现一致）；或 `No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> 关闭条件：以下条目 + 每个 Phase 的 Exit Criteria 全部 `[x]` 后，`Plan Status` 改 `completed`（需独立 closure audit）。

- [x] 3 Reconciliation 实体建模完成，CRUD 自动暴露且可验证
- [x] `IReconciliationService` 接口（含 matchStrategy 入参）+ `LocalReconciliationService`（EXACT + levenshtein FUZZY）完成，空候选返回空列表
- [x] `ReconciliationExecutor` + `executeReconciliation` action 端到端返回真实匹配结果（MATCHED/UNMATCHED/MULTIPLE 统计真实，非空壳）；取数由 BizModel 调 queryTableData 取 items 传入 executor（B2 方案 b）
- [x] 人工确认 action（单条 + 批量）完成，越界显式失败
- [x] D1（服务模型）/D2（策略集）/D3（明细形态）/D4（Config 单列）/D5（status 判定规则）/D6（Java 代码清理）已写入 `08-reconciliation.md`
- [x] 不存在空壳实现（无空方法体/静默跳过/吞异常）
- [x] 不存在被静默降级到 deferred 的 in-scope live defect 或 contract drift
- [x] 受影响 owner docs（`08-reconciliation.md`）已同步
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证 BizModel 调 queryTableData 取真实 items 传入 executor + LocalReconciliationService 匹配 + 写回真实 statistics/details（端到端连通，非伪造数据）
- [x] `./mvnw compile`（`-pl nop-metadata -am`）
- [x] `./mvnw test`（`-pl nop-metadata -am`）
- [x] checkstyle / 代码规范检查通过
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2026-07-16-0900-2-nop-metadata-reconciliation.md --strict` 退出码 0

## Deferred But Adjudicated

（执行中产生的优化项按规则归类记录于此。预判：超大表明细 JSON、rowIndex stable rowKey 可能作为 optimization candidate deferred。）

## Non-Blocking Follow-ups

- 外部 HTTP Reconciliation 服务实现（OpenRefine/Wikidata 兼容端点 + 认证）：D1 首版只定义接口 + 本地实现，外部 HTTP impl 可后续插入不改执行器。Why non-blocking：依赖外部可用性与认证（设计 Open Questions 未决），无法在 H2/AutoTest 端到端验证；本地实现已使对账结果面成立。
- **提取共享 table-data fetcher（重构 queryTableData）**：首版注入 `NopMetaTableBizModel` 具体类调 queryTableData 取数；未来可把取数逻辑提取为共享无状态服务（解除 BizModel 间直接依赖）。Why non-blocking：首版注入方案行为正确、可测，直接依赖不构成 live defect。
- phonetic/semantic 匹配策略（需额外算法/模型依赖）：D2 首版 EXACT + levenshtein 已覆盖核心对账场景。
- 多列对账（Config.columns[]）：D4 首版单列已成立对账结果面，多列为增量。
- 属性扩展（expandProperties 写回表）、实体缓存定时自动刷新、结果版本化、流式对账（设计 Open Questions）：均不影响对账执行/结果/确认结果面。
- 人工确认 stable rowKey（替代 details JSON 数组下标）：首版 rowIndex 绑定执行快照，重排/分页漂移风险已知，引入 stable rowKey 为 follow-up。

## Closure

Status Note: P4-5 Reconciliation 结果面已完整落地——3 实体建模 + GraphQL CRUD 自动暴露 + 可插拔 IReconciliationService 接口（内置 LocalReconciliationService，exact + levenshtein fuzzy，候选来自 NopMetaReconciliationEntity）+ 对账执行器（BizModel 调 queryTableData 取 items 传入纯组件 executor → D5 钉死规则判 MATCHED/UNMATCHED/MULTIPLE → 写 statistics+details）+ 人工确认 action（单条/批量，越界显式失败）。D1~D6 全部写入 08-reconciliation.md（重写为 final，清理 Java 代码）。所有失败路径显式抛 ErrorCode（config/table/column/queryTableData/越界），无空壳实现、无静默跳过。187 tests 全绿（原 164 + 8 CRUD + 15 BizModel）。剩余项均为已裁定的 Non-Blocking Follow-up（外部 HTTP impl / 多列 / stable rowKey 等），无 in-scope live defect。
Completed: 2026-07-16

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure-audit 子 agent（explore, fresh session, task_id=ses_096d45e12ffeJQJkc1YSIGWaEi，read-only）
- Audit Session: ses_096d45e12ffeJQJkc1YSIGWaEi
- Evidence:
  - Phase 1 Exit Criteria 全部 PASS：3 实体在 orm.xml（L1903-2101）含 PK/业务列/审计列/to-one/索引；2 dict 在 orm.xml `<dicts>`(L100-109) + `_vfs/dict/meta/*.dict.yaml` 两处；CRUD 由 `TestNopMetaReconciliationCrud`（8 tests）验证。
  - Phase 2 Exit Criteria 全部 PASS：`IReconciliationService`（matchStrategy 入参）+ `LocalReconciliationService`（exact=1.0/fuzzy=levenshtein，空候选→emptyList 不伪造）+ `ReconciliationExecutor`（纯组件，rows 由 BizModel 传入，D5 五条规则在 judgeStatus L120-139）+ `executeReconciliation`（校验 columnName + 注入 NopMetaTableBizModel 调 queryTableData + 落 Result）+ `confirmMatch/batchConfirmMatches`（越界/result 不存在显式失败）均经 `TestNopMetaReconciliationBizModel`（15 tests）端到端验证。
  - Closure Gates 全部 PASS：3 实体建模/接口+本地服务/执行器端到端/确认 action/D1-D6 文档/无空壳/无静默降级/owner-doc 同步/Anti-Hollow 调用链（executeReconciliation→queryTableData→executor→reconcile→persist 全真实代码，无 TODO/空方法/吞异常）。
  - Anti-Hollow 检查 PASS：端到端测试用 H2 external 表真实行（queryTableData 取 items）+ 真实候选实体播种，断言 statistics 计数（matchedRows/unmatchedRows/multipleMatches/matchRate）与种子一致，非空壳。
  - `./mvnw test -pl nop-metadata/nop-metadata-service -am` → 187 tests, 0 failures, 0 errors。
  - `node ai-dev/tools/check-plan-checklist.mjs ... --strict` 退出码 0。
  - Deferred 项分类检查：Non-Blocking Follow-ups 均为 optimization candidate / out-of-scope improvement，无 in-scope live defect 被降级。

Follow-up:

- 外部 HTTP Reconciliation 服务实现、多列对账、phonetic/semantic 匹配、属性扩展、stable rowKey 等均为已记录的 Non-Blocking Follow-up（不影响当前 supported baseline）。无剩余 plan-owned live defect。
