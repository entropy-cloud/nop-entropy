# 0900-1 nop-metadata 数据契约 MetaDataContract（P4-4）

> Plan Status: completed
> Completed: 2026-07-16
> Last Reviewed: 2026-07-16
> Source: `ai-dev/design/nop-metadata/nop-metadata-roadmap.md` P4-4；`ai-dev/design/nop-metadata/04-data-governance.md` §1.3 + §2.3 + §5.2 + §六 待定问题；`ai-dev/design/nop-metadata/01-architecture-baseline.md` §2.6（质量）+ §2.3.2（Catalog）+ §八 待定问题（SLA 格式）
> Related: `2026-07-16-0900-2-nop-metadata-reconciliation.md`（P4-5，并行独立结果面）；P4-1/P4-2/P4-3 已完成（联邦查询基础），本 plan 不依赖 P4-5
> Draft Review: 经两轮独立子 agent 对抗性审查（含想象性分析 + live repo 核验）。R1 Blocker（D2 数据结构/算法未钉死）+ 6 Major 已全部收敛；R2 consensus YES（无 Blocker），R2 的 2 个 Major（latestResult 截断风险→改 mediumtext、Catalog.lastModified v1 恒 null→baseline 披露）已修复。可执行性共识达成。

## Purpose

把 P4-4（数据契约 MetaDataContract）从 `todo` 推进到 `done`：新增 `NopMetaDataContract` 实体并自动暴露 GraphQL CRUD，实现契约状态生命周期（draft→active→deprecated→retired，强制流转），实现契约检查 action（聚合该契约引用的质量规则最新结果 + SLA 新鲜度判断），并裁定 §八 待定问题「SLA 定义格式：JSON Schema vs 自定义 DSL」。本 plan 收口"数据资产 SLA/质量承诺可定义、可检查、可观测"这一结果面。

## Current Baseline

- **25 实体已建模**于 `nop-metadata/model/nop-metadata.orm.xml`（Module/OrmModel/DataSource/SemanticType/Entity/EntityField/EntityRelation/EntityUniqueKey/EntityIndex/Domain/Dict/DictItem/Table/TableDimension/TableMeasure/TableFilter/TableJoin/Pipeline/LineageEdge/QualityRule/QualityResult/Manifest/Catalog/ProfilingRule/ProfilingResult，已 `rg -c '<entity '` 核验=25）。**无 `NopMetaDataContract` 实体**（已核验 orm.xml，无此 className）。
- 实体建模模式：ORM `<entity>` + 审计列（createdBy/createTime/updatedBy/updateTime/remark/version）+ `registerShortName="true"`（GraphQL 自动暴露 CRUD，无需手写 BizModel）。新增实体的 GraphQL CRUD 由 `CrudBizModel` 自动生成（P1 起 25 实体均如此）。
- 自定义 action 先例：`NopMetaModuleBizModel.releaseModule`（状态流转：DRAFTING→RELEASED，状态非法抛 inline ErrorCode `ERR_MODULE_NOT_DRAFTING` + `.param("status",...)`）；`NopMetaQualityRuleBizModel.executeQualityRule`（执行 → 写结果）；`NopMetaDataSourceBizModel.testConnection`。均落在 `.../service/entity/NopMeta*BizModel.java`。
- **ErrorCode 惯例（重要）**：本模块**不**用集中式 `NopMetadataErrors`（该文件实为空 interface）。真实惯例是把 `ErrorCode` 定义为各 BizModel/Executor 内的 `static final ErrorCode ERR_... = ErrorCode.define(...)`（见 `NopMetaModuleBizModel`、`MetaQualityRuleExecutor` 顶部）。新增 ErrorCode 走此内联方式，抛 `NopException` + `.param(...)`。
- 执行引擎先例：`MetaQualityRuleExecutor`（`.../service/quality/`）执行规则、判定结果、写 `NopMetaQualityResult`（含 status/actualValue/expectedValue/message/details）。`NopMetaQualityResult` 有 `qualityRuleId`+`executeTime` 时序，索引 `IX_NOP_META_QRESULT_RULE(qualityRuleId, executeTime)`（已核验 orm.xml:1536）。
- **SLA 新鲜度数据来源已就绪**：`NopMetaCatalog`（§2.3.2）有 `rowCount`/`lastModified`/`collectedAt`/`details(JSON)`，时序追加，索引 `IX_NOP_META_CATALOG_TABLE(metaTableId, collectedAt)`（已核验 orm.xml:1664）。`MetaCatalogCollector`（`.../service/catalog/`）已可收集。契约 `entityTableId` 之值即 `NopMetaTable.metaTableId`，可直接作为 Catalog `metaTableId` 查询键。**已知限制（重要）**：Catalog `lastModified` 在 v1 **恒为 null**（`MetaCatalogCollector` 始终 `setLastModified(null)` + `markUnavailable`，per §2.3.2 方言特定降级策略）；故 D2 的 `maxLatency`↔`lastModified` 路径在 v1 恒判 `unknown`，`dataStale` 永不触发——SLA 判定在 v1 实际只由 `refreshFrequency`↔`collectedAt` 驱动。`maxLatency` 路径为前向就绪（未来 Catalog 扩展填充 lastModified 后自动生效）。
- **dict 双处维护（重要）**：dict 既在 orm.xml `<dicts>` 内联声明（如 `meta/module-status`，orm.xml:10），又在 `_vfs/dict/meta/*.dict.yaml` 有运行时定义。新列引用 `ext:dict="meta/contract-status"` 须**两处都加**。平台 dict 值约定为**大写**（`DRAFTING/RELEASED/DEPRECATED`、`PASS/FAIL/ERROR/SKIP`、`ACTIVE/DISABLED`）。
- **设计文档现状**：`04-data-governance.md` §2.3 定义 MetaDataContract 模型（contractName/displayName/entityTableId→MetaTable/status/ownerUserId/schema(JSON)/sla(JSON)/qualityExpectations(JSON)/security(JSON)/latestResult(JSON)/tagSet/extConfig），状态流 draft→active→deprecated→retired。**注意 §2.3 status 枚举写 3 值（draft/active/deprecated）而流转图写 4 值（含 retired），自相矛盾，本 plan D1 须收口为 4 值**。§5.2 GraphQL 示例用 action 名 `executeDataContractCheck`。§六 + 架构基线 §八 均列「SLA 定义格式：JSON Schema vs 自定义 DSL？」为**未裁定**待定问题。
- 平台 IoC：`@Inject` 须用 `protected`/包级或 setter，不支持 private 字段注入（AGENTS.md）。

## Goals

- 新增 `NopMetaDataContract` 实体（ORM 建模），自动暴露 GraphQL CRUD。
- 实现状态生命周期 action（activate/deprecate/retire），非法流转显式失败（不静默跳过）。
- 实现 `checkContract` action：聚合该契约 `qualityExpectations` 引用的质量规则最新 `NopMetaQualityResult` + 基于 Catalog 最新快照的 SLA 新鲜度判断，写回 `latestResult`，返回结构化结果。
- 裁定 SLA 格式（D1）+ 契约检查语义（D2），写入设计文档（`04-data-governance.md` 收口待定问题 + 架构基线 §八 关闭对应待定项）。
- 状态流转、契约检查（含 SLA 新鲜度 + 质量聚合两条路径 + 失败路径）各有端到端/focused 测试。

## Non-Goals

- Reconciliation 对账（P4-5，successor/并行 plan 0900-2）。
- 契约自动定时调度执行（归 Non-Blocking Follow-up；本 plan 只提供同步 checkContract action）。
- `schema` 字段（JSON Schema 字段约束）的运行时逐字段校验执行引擎（D1 裁定其存储格式，运行时逐行校验为 follow-up）。
- ownerUserId 的权限强制（由 nop-auth RBAC 承担，不在本 plan 实现访问控制）。

## Scope

### In Scope

- D1 裁定：SLA 与 schema 存储格式（`schema`=mediumtext JSON Schema 文档；`sla`=结构化 JSON，约定键 refreshFrequency/maxLatency/retention）+ 收口 §2.3 status 4 值枚举。裁定写入设计文档。
- D2 裁定（钉死数据结构与算法）：`checkContract` 语义——qualityExpectations 形状 `{"qualityRuleIds":[...]}`；质量路径按 ruleId 取最新 QualityResult 汇总；SLA 路径 refreshFrequency↔collectedAt、maxLatency↔lastModified；status 归并 ERROR>FAIL>PASS。action 名统一 `checkContract`。
- `NopMetaDataContract` 实体建模 + dict（contract-status）。
- 状态流转 action（activate/deprecate/retire）+ 非法流转显式失败。
- `checkContract` action 实现 + `latestResult` 写回。
- 上述各路径 focused/端到端测试。

### Out Of Scope

- schema 字段逐行校验执行。
- 契约定时调度。
- 访问控制强制。
- 契约版本化（多版本快照）。

## Execution Plan

### Phase 1 - 设计裁定 + 实体建模

Status: completed
Targets: `nop-metadata/model/nop-metadata.orm.xml`（新增 NopMetaDataContract）；`ai-dev/design/nop-metadata/04-data-governance.md`（§2.3 + §六）；`ai-dev/design/nop-metadata/01-architecture-baseline.md`（§八 关闭 SLA 格式待定项）；新增 dict `meta/contract-status`

- Item Types: `Decision | Fix`

- [x] **D1 裁定并写入 `04-data-governance.md` §2.3 + 关闭架构基线 §八「SLA 格式」待定项**：
  - `schema` 列：存储 JSON Schema 文档（`domain="mediumtext"` + `stdDomain="json"`，**不用 json-4000**——宽表 JSON Schema 易超 4KB，对齐 Manifest.content/Catalog.details 先例），首版仅存储不执行逐行校验。
  - `sla` 列：结构化 JSON（`domain="json-4000"` + `stdDomain="json"`），约定键：`refreshFrequency`(`{interval,unit}`)、`maxLatency`(`{value,unit}`)、`retention`(`{period,unit}`)。未知键保留不报错（前向兼容）。
  - **收口 §2.3 status 枚举矛盾**：统一为 4 值 `DRAFT/ACTIVE/DEPRECATED/RETIRED`（大写，对齐平台 dict 惯例），删除旧 3 值表述。
  - 裁定理由（拒绝自定义 DSL）：JSON Schema/结构化 JSON 无需额外解析器、与平台 JSON 列原生对齐、可被 AI/外部工具直接消费；自定义 DSL 增加学习与维护成本无收益。
- [x] **D2 裁定并写入 `04-data-governance.md` §5.2（钉死数据结构与算法，不留发明空间）**：action 名统一为 `checkContract`（同步更新 §5.2 GraphQL 示例，废弃旧名 `executeDataContractCheck`）。`checkContract(contractId)` 行为——
  - **`qualityExpectations` 的 JSON 形状（钉死）**：`{"qualityRuleIds": ["<ruleId1>", "<ruleId2>", ...]}`（裸字符串数组，key 固定 `qualityRuleIds`）。空数组或缺 key 视为"无质量检查项"。
  - **质量路径**：对 `qualityRuleIds` 中每个 ruleId 取 `NopMetaQualityResult` 按 `executeTime desc` 最新一条（取不到记为 `no-result`）；汇总 `qualitySummary = {totalRules, passedRules, failedRules, noResultRules, details:[{ruleId, latestStatus, message}]}`（`latestStatus` 取 QualityResult.status 原值，无结果记 `no-result`）。
  - **SLA 路径（算法钉死）**：以 `entityTableId` 之值作为 `metaTableId` 取 `NopMetaCatalog` 按 `collectedAt desc` 最新一条（无 Catalog 记 `catalogAvailable=false`）：
    - `refreshFrequency`（若存在）：判定 `now - catalog.collectedAt > refreshFrequency` → `collectionStale=true`（采集过期）；
    - `maxLatency`（若存在）：判定 `now - catalog.lastModified > maxLatency` → `dataStale=true`（数据过期，lastModified 为空则该项记 `unknown` 不判定）；
    - `slaFresh = !collectionStale && !dataStale`；`slaSummary = {catalogAvailable, collectedAt, lastModified, collectionStale, dataStale, slaFresh}`。时间单位归一为毫秒比较。
  - **混合 status 归并规则（钉死）**：
    - 若 `qualityRuleIds` 为空且 `sla` 为空 → `status=ERROR`，message="契约无可检查项"（**不静默 pass**）。
    - 否则按优先级：`ERROR`（任一被引用 ruleId 在 QualityRule 表不存在 / Catalog 解析异常 / JSON 解析失败）> `FAIL`（任一质量 latestStatus=FAIL，或 `slaFresh=false`）> `PASS`（所有可判定项通过）。
    - 即：SLA stale 或 质量 FAIL 任一成立 → `FAIL`；全部通过 → `PASS`。
  - **汇总写回**：`latestResult`(mediumtext+stdDomain json: `{timestamp, status, message, qualitySummary, slaSummary}`)。用 mediumtext 而非 json-4000，因 qualitySummary.details 含每规则 message（QualityResult.message precision=1000），多规则易超 4KB，对齐 Manifest.content/Catalog.details 先例。
- [x] 新增 `NopMetaDataContract` 实体到 `nop-metadata.orm.xml`：列含 `contractId`(PK,seq)/`contractName`/`displayName`/`entityTableId`(→NopMetaTable)/`status`(dict:meta/contract-status)/`ownerUserId`(`domain="userId"` 对齐平台 user 标识，precision=50)/`schema`(mediumtext+stdDomain json)/`sla`(json-4000)/`qualityExpectations`(json-4000)/`security`(json-4000)/`latestResult`(mediumtext+stdDomain json)/`tagSet`/`extConfig`(json-4000) + 审计列 + `version`。to-one relation `metaTable`（ref NopMetaTable，join on entityTableId=metaTableId，tagSet=pub,ref-pub）。索引 `IX_NOP_META_CONTRACT_TABLE`(entityTableId)。
- [x] 新增 dict `meta/contract-status`：**两处都加**——orm.xml `<dicts>` 内联声明 + `_vfs/dict/meta/contract-status.dict.yaml` 运行时定义；值为大写 `DRAFT/ACTIVE/DEPRECATED/RETIRED`。
- [x] 确认 GraphQL CRUD 自动暴露（`registerShortName="true"`），`./mvnw compile -pl nop-metadata -am` 通过。

Exit Criteria:

- [x] D1/D2 裁定已写入 `04-data-governance.md`（§2.3 存储格式 + status 4 值收口 + §5.2 检查语义与 action 名 checkContract），架构基线 §八「SLA 格式」待定项已标注**已裁定**
- [x] `nop-metadata.orm.xml` 新增 `NopMetaDataContract`，含 PK/业务列/审计列/to-one(metaTable)/索引；`./mvnw compile -pl nop-metadata -am` 通过
- [x] dict `meta/contract-status` 在 orm.xml `<dicts>` 与 `_vfs/dict/` 两处均存在，4 个大写状态值齐备
- [x] GraphQL findPage/get/save/delete 对 NopMetaDataContract 可用（AutoTest 或 GraphQL 调用验证）
- [x] **新功能测试（#25）**：新增实体的 CRUD 至少有 AutoTest 或 focused test 覆盖 create+query
- [x] **无静默跳过（#24）**：本 phase 仅实体建模，无新公共方法分支 → N/A（状态/检查的失败路径在 Phase 2 覆盖）
- [x] 受影响 owner docs 已同步（`04-data-governance.md` + 架构基线 §八）；或 `No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 状态生命周期 + 契约检查执行

Status: completed
Targets: `nop-metadata/nop-metadata-service/.../service/entity/NopMetaDataContractBizModel.java`（新增 BizModel，含内联 ErrorCode）；`.../service/contract/MetaContractChecker.java`（新增执行组件，含内联 ErrorCode）

- Item Types: `Fix | Proof`

- [x] 实现 `NopMetaDataContractBizModel` 状态流转 action：`activateContract`/`deprecateContract`/`retireContract`（`@BizMutation`），按 D2 前置状态校验：DRAFT→ACTIVE、ACTIVE→DEPRECATED、DEPRECATED→RETIRED；非法前置状态抛内联 `static final ErrorCode ERR_CONTRACT_INVALID_TRANSITION`（**不静默跳过/不静默改状态**）。已 RETIRED 不可再流转（显式失败）。ErrorCode 按模块惯例内联于 BizModel/Checker 顶部（**不**写入空 interface `NopMetadataErrors`）。
- [x] 实现 `MetaContractChecker`（`.../service/contract/`），严格按 D2 钉死的结构与算法：
  - 质量路径：解析 `qualityExpectations`(JSON) 取 `qualityRuleIds` 数组 → 对每个 ruleId 先校验 QualityRule 存在（不存在→status=ERROR），再取最新 QualityResult（`orderBy executeTime desc`，take 1）→ 汇总 qualitySummary。
  - SLA 路径：解析 `sla`(JSON) → 按 entityTableId 查最新 Catalog（`orderBy collectedAt desc`）→ refreshFrequency↔collectedAt、maxLatency↔lastModified → slaSummary（lastModified 为空记 unknown 不判定）。
  - 按 D2 归并规则计算 status（ERROR>FAIL>PASS，无可检查项→ERROR）。
  - 失败路径显式：JSON 解析失败 / ruleId 不存在 / 无可检查项 均显式失败或 status=ERROR + 明确 message（不吞异常、不静默 pass）。
- [x] 实现 `checkContract(contractId)` action（`@BizMutation`）：调 `MetaContractChecker` → 写回实体 `latestResult` → 返回结果 Map。契约不存在抛 ErrorCode（不 NPE）。
- [x] 状态流转与 checkContract 的关系：checkContract 不受 status 阻断（DRAFT 可预检）；该行为已在 D2 文档化。无前置状态阻断 checkContract。

Exit Criteria:

- [x] 状态流转 action 可用：合法流转成功更新 status（DRAFT→ACTIVE 等）；非法流转（如 DRAFT→RETIRED、RETIRED→ACTIVE）显式失败抛 ErrorCode（有测试断言异常）
- [x] `checkContract` 端到端可用：对一个 ACTIVE 契约（引用已有 qualityRule + 已有 Catalog）执行，`latestResult` 写入且 `status` 与质量/SLA 真实状态一致（真实 PASS/FAIL/ERROR，非空壳）
- [x] **端到端验证（#22）**：从 `checkContract` 入口 → 质量结果聚合 + Catalog 新鲜度判断 → `latestResult` 写回的完整路径有测试跑通，断言真实计算值（如 failedRules 计数、slaFresh=true/false，且值由 D2 钉死的算法唯一确定）
- [x] **接线验证（#23）**：`MetaContractChecker` 确实在 `checkContract` 运行时被调用（测试中通过真实 DB 查到 QualityResult/Catalog 并反映到结果，证明调用链连通）
- [x] **无静默跳过（#24）**：JSON 解析失败 / ruleId 不存在 / 无可检查项 / 非法状态流转 / 契约不存在 均显式失败或 status=ERROR（测试覆盖至少 2 条失败路径）
- [x] **新功能测试（#25）**：新增测试覆盖——状态合法流转、状态非法流转失败、checkContract 质量 PASS/FAIL、checkContract SLA fresh/stale（→FAIL）、checkContract 无可检查项 ERROR、混合（质量 PASS+SLA stale→FAIL）
- [x] 受影响 owner docs 已同步（`04-data-governance.md` §5.2 行为/action 名与实现一致）；或 `No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> 关闭条件：以下条目 + 每个 Phase 的 Exit Criteria 全部 `[x]` 后，`Plan Status` 改 `completed`（需独立 closure audit）。

- [x] `NopMetaDataContract` 实体建模完成，CRUD 自动暴露且可验证
- [x] 状态生命周期 action 完成，非法流转显式失败（有测试）
- [x] `checkContract` action 完成，质量 + SLA 双路径端到端返回真实计算结果（非空壳）
- [x] D1（SLA 格式）+ D2（检查语义）已写入设计文档并关闭架构基线 §八 待定项
- [x] 不存在空壳实现（无空方法体/静默跳过/吞异常）
- [x] 不存在被静默降级到 deferred 的 in-scope live defect 或 contract drift
- [x] 受影响 owner docs（`04-data-governance.md` + `01-architecture-baseline.md` §八）已同步
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证 checkContract 在运行时确实聚合了真实 QualityResult/Catalog 并写回 latestResult（端到端连通）
- [x] `./mvnw compile`（`-pl nop-metadata -am`）
- [x] `./mvnw test`（`-pl nop-metadata -am`）
- [x] checkstyle / 代码规范检查通过
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2026-07-16-0900-1-nop-metadata-data-contract.md --strict` 退出码 0

## Deferred But Adjudicated

（执行中产生的优化项按规则归类记录于此。预判：schema 逐行校验、契约定时调度可能作为 optimization candidate deferred。）

## Non-Blocking Follow-ups

- `schema`(JSON Schema) 字段的运行时逐行数据校验执行引擎（D1 仅裁定存储格式，逐行校验为 follow-up，不影响契约定义/检查结果面）。
- 契约定时调度执行（需对接 nop-job 或 cron，首版只提供同步 checkContract action）。
- `ownerUserId` 的访问控制强制（由 nop-auth RBAC 承担，非本 plan 范围）。

## Closure

Status Note: P4-4（数据契约 MetaDataContract）已完成。NopMetaDataContract 实体建模 + GraphQL CRUD 自动暴露；状态生命周期（DRAFT→ACTIVE→DEPRECATED→RETIRED，非法流转显式失败）；checkContract action（D2 钉死算法：质量结果聚合 + SLA 新鲜度双路径，ERROR>FAIL>PASS 归并，写回 latestResult）；D1（SLA 格式裁定 JSON Schema/结构化 JSON，拒绝自定义 DSL）+ D2（检查语义）已写入 04-data-governance.md 并关闭架构基线 §八 待定项。共新增 17 个测试（4 CRUD + 13 状态/检查），全部 PASS，全模块 164 测试 0 失败。
Completed: 2026-07-16

Closure Audit Evidence:

- Reviewer / Agent: 独立 explore 子 agent（task ses_096fcfaa9ffeCF0Mtv0J2p4imm），read-only 审计，未修改任何文件。
- Evidence:
  - Gate 1（实体建模）PASS：`nop-metadata/model/nop-metadata.orm.xml:1817-1888` 含全部列 + to-one metaTable + 索引 IX_NOP_META_CONTRACT_TABLE；`_app.orm.xml:1589-1655` 镜像一致。
  - Gate 2（dict）PASS：orm.xml `<dicts>` 4 大写值 + `contract-status.dict.yaml` 运行时定义齐备。
  - Gate 3（BizModel）PASS：activate/deprecate/retire（@BizMutation）+ 非法流转抛 ERR_CONTRACT_INVALID_TRANSITION；checkContract（@BizMutation）加载实体（不存在抛错不 NPE）→ 调 Checker → 写回 latestResult。
  - Gate 4（Checker D2）PASS：质量路径（ruleId 存在性校验 + executeTime desc 取最新 + qualitySummary）；SLA 路径（collectedAt desc 取最新 + refreshFrequency/maxLatency + slaSummary，lastModified null→unknown）；归并 ERROR>FAIL>PASS + 无可检查项→ERROR；无吞异常/无静默 pass。
  - Gate 5（Anti-Hollow）PASS：测试用真实 QualityRule/QualityResult/Catalog 行，断言 passedRules/failedRules 计数、slaFresh true/false、status 真实计算值（非空壳）。
  - Gate 6（Docs）PASS：04-data-governance.md §2.3 D1 + §5.2 D2 + §六 裁定；01-architecture-baseline.md §八 SLA 格式裁定。
  - Gate 7（无空壳）PASS：源码无 TODO/FIXME/空方法体；return null 均为合法控制流分支（catalogAvailable=false / 未配置 / 空输入）。
  - 验证命令：`./mvnw test -pl nop-metadata/nop-metadata-service -am` → 164 tests, 0 failures, 0 errors。`check-plan-checklist --strict` exit 0。

Follow-up:

- 无剩余 plan-owned work。Non-Blocking Follow-ups 已记录于本文件（schema 逐行校验、契约定时调度、ownerUserId 访问控制）。后继 P4-5（Reconciliation 对账，plan 0900-2）并行独立，不依赖本 plan。
