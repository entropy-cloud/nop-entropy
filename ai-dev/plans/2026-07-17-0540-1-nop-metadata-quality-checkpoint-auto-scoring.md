# 0540-1 nop-metadata 质量检查点自动评分触发

> Plan Status: completed
> Mission: nop-metadata
> Work Item: Quality Checkpoint Auto-Scoring Trigger
> Last Reviewed: 2026-07-17
> Draft Review: 两轮独立对抗性审查（ses_0931ce5bbffeI66AfCTeu6U77o → ses_093131634ffeJc95bu000YLKCk）。首轮 REJECT（合并了高风险 nop-job 调度 Phase，拆分）；次轮 APPROVE-WITH-MINORS（无 Blocker，已修正中心 Decision 措辞 NEW-1）。共识达成。
> Source: deferred item from `2026-07-17-0027-2`（checkpoint 执行后自动触发评分），`Successor Required: yes`；设计意图来源 `06-data-quality-extended.md` §四/§五
> Related: `2026-07-17-0027-1-nop-metadata-quality-checkpoint-orchestration.md`、`2026-07-17-0027-2-nop-metadata-quality-scoring.md`、`2026-07-17-0540-2-nop-metadata-quality-checkpoint-result-actions.md`

## Purpose

把质量检查点（`NopMetaQualityCheckpoint`）执行与质量评分（`NopMetaQualityScore`）之间的断线接通：检查点执行（成功落盘 `NopMetaQualityResult`）后，对受影响逻辑表自动重算质量评分，无需用户再手动调一次 `computeQualityScore`。本 plan 关闭 0027-2 中 `Successor Required: yes` 的自动评分触发 deferred 项。

> **范围说明（draft review 收口）**：原 draft 曾把「nop-job 定时调度」与本项合并，经独立对抗性审查（task ses_0931ce5bbffeI66AfCTeu6U77o）裁定**拆分**——自动评分自包含、低风险、可独立验收；定时调度需先解决 nop-job 跨模块依赖方向/调度引擎承载点/invoker 注册约定等架构裁定（见 Non-Goals），属 design-first，不在本 plan。

## Current Baseline

已落地（live repo 核实）：

- **手动检查点执行**已可用：`NopMetaQualityCheckpointBizModel.executeCheckpoint(checkpointId, schemaPattern, context)`（`@BizMutation`，`.../service/entity/NopMetaQualityCheckpointBizModel.java:77`）→ 委托无状态 `MetaQualityCheckpointExecutor.execute()`（`.../service/quality/MetaQualityCheckpointExecutor.java:101`）。
  - 执行流程逐条遍历解析后的 `resolution.rules`（`:129`），每条 `rule.getEntityId()` 即目标 `NopMetaTable.metaTableId`（database-type 规则在 `executeSingleRule` :251 直接 SKIP，不解析表）。
  - 返回摘要 map（`:156-164`）：`{checkpointId, executedCount, passCount, failCount, errorCount, results:[...], errors:[...]}`。**当前摘要不含受影响表集合 / 不触发评分**。
- **手动评分**已可用：`NopMetaQualityScoreBizModel.computeQualityScore(metaTableId, context)`（`@BizMutation`，`.../service/entity/NopMetaQualityScoreBizModel.java:53`）→ 委托无状态 `MetaQualityScorer.score(metaTableId)`（`.../service/quality/MetaQualityScorer.java`，无 context 参数，**只算不写**）→ BizModel 落盘一行新 `NopMetaQualityScore`（`:57-65`）+ 返回摘要（含 `scoreId`）。
  - scorer 实例化与「score + 落盘」逻辑**全部在 `NopMetaQualityScoreBizModel`**（`ensureScorer()` `:82-87`）；`NopMetaQualityCheckpointBizModel` **无 scorer 字段**。
  - scorer 评分语义：读该表 **全部** 挂载规则的最新 `NopMetaQualityResult`（按 entityId = metaTableId，非仅 checkpoint 子集）→ 维度映射 → 加权 → 趋势 → 落盘。复用即「重算该表评分」，不需重写算法、不应复制落盘六行。
- **两者无连接**：`executeCheckpoint` 返回后无任何评分重算，评分必须用户手动再调一次。这是 0027-2 明确 deferred 的 `Successor Required: yes` 项。

真正剩余 gap：`executeCheckpoint` 与 `computeQualityScore` 之间无接线（自动评分触发）。

## Goals

- 检查点执行完成后，对受影响逻辑表集合自动重算质量评分并落盘，无需手动调 `computeQualityScore`。
- 自动评分复用既有 `MetaQualityScorer`（不重写评分算法、不复制维度映射逻辑）。
- 自动评分失败隔离：单表评分异常记入摘要 errors，不中断其他表评分、不回滚已落盘的 checkpoint store。

## Non-Goals

- **不**实现 cron 定时调度（nop-job 适配）。定时调度需先裁定：nop-metadata 是否新增 nop-job 依赖、调度引擎由哪个应用/模块承载、invoker 注册约定（`nopJobInvoker_<executorKind>`）vs 复用已有 `nopJobInvoker_beanMethod`、以及 `executeCheckpoint` 的 `IServiceContext` 参数如何从调度入口构造。这些属架构裁定（design-first），不在本 plan。
- **不**实现 notify/webhook/update_docs 动作（属 `2026-07-17-0540-2`）。
- **不**做 entity 级评分、ProfilingResult→评分映射、运行时维度权重覆盖（0027-2 已裁定 `Successor Required: no`）。
- **不**做 checkpoint 执行历史摘要行持久化。

## Scope

### In Scope

- 自动评分触发接线：`executeCheckpoint` 执行（含 store）后，对受影响表集合逐个重算评分。
- 「受影响表集合」的确定：取执行期间实际被判定（judge）的规则的去重 `rule.getEntityId()`（即真正命中某 NopMetaTable 的规则）；database-type SKIP 规则不纳入（其 entityId 不指向待评 metaTable）。
- 摘要新增受影响表评分结果（per-table scoreId/overallScore + 评分 errors），使自动评分在返回中可观测。
- 自动评分可选控制：`extConfig.autoScore=false`（或调用参数）时跳过自动评分，默认开启。

### Out Of Scope

- 定时调度、结果动作、entity 级评分、评分历史窗口。

## Execution Plan

### Phase 1 - 自动评分触发接线

Status: completed
Targets: `nop-metadata-service/.../service/quality/MetaQualityCheckpointExecutor.java`、`.../service/entity/NopMetaQualityCheckpointBizModel.java`

- Item Types: `Fix`（执行↔评分接线 gap，0027-2 确认的 contract gap）、`Decision`

- [x] **Decision（接线点）**：裁定评分在 BizModel 层接，不改 executor 的构造依赖——executor 在执行循环中收集「实际被判定规则的去重 metaTableId」并加入返回摘要（新增 `affectedTableIds`）；`executeCheckpoint` 取摘要后，**注入 `INopMetaQualityScoreBiz`（`NopMetaQualityScoreBizModel`），逐表调其既有 `computeQualityScore(metaTableId, context)`（含 score + 落盘 + 返回 scoreId），零落盘逻辑复制**（不在 CheckpointBizModel 内 new scorer、不复制 ScoreBizModel 落盘六行；`NopMetaQualityCheckpointBizModel` 当前无 scorer）。executor 不感知 scorer。
- [x] executor 在执行循环中收集 affected metaTableId 集合（仅实际 judge 的规则；database SKIP 规则不纳入），加入返回摘要 `affectedTableIds`。
- [x] BizModel `executeCheckpoint` 在 executor 返回后，按 `affectedTableIds` 逐表调 `computeQualityScore`，per-table try/catch + clearSession 失败隔离（对齐既有 per-rule 隔离模式），失败记入摘要 errors 不中断。
- [x] 自动评分受 `extConfig.autoScore` 控制（不改 `executeCheckpoint` 既有签名），默认开启；关闭时跳过且摘要标注 skipped。
- [x] 摘要新增 per-table 评分结果（scoreId/overallScore）与评分 errors。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `executeCheckpoint` 执行后，受影响表存在新增 `NopMetaQualityScore` 行（scoreTime 接近执行时刻）；测试断言执行前后该表评分行数 +1，且新行 overallScore 与手动 `computeQualityScore` 结果一致。
- [x] database-type SKIP 规则的 entityId 不触发评分（不产生对无效 tableId 的评分调用 / 不抛 table-not-found）。
- [x] 自动评分复用 `MetaQualityScorer`：代码追踪证明调用链 `executeCheckpoint → BizModel 评分循环 → scorer.score → 落盘`，无新增评分算法、无维度映射逻辑复制。
- [x] **新增功能测试覆盖（Rule #25）**：列出新增测试——(a) 自动评分落盘行数+1 且 overallScore 与手动一致；(b) 多受影响表逐表评分；(c) 单表评分失败隔离（mock scorer 抛错 → 该表进 errors、其他表仍评分、checkpoint store 不回滚）；(d) `autoScore=false` 跳过。
- [x] **端到端验证**：测试从 `executeCheckpoint`（入口）到 `NopMetaQualityScore` 新行（出口）完整跑通。
- [x] **接线验证**：断言 `MetaQualityScorer.score` 在 `executeCheckpoint` 路径中被调用（计数器/标志位），证明执行与评分运行时连通。
- [x] **无静默跳过**：单表评分失败不吞异常——记入摘要 errors；无空 catch/continue。
- [x] owner doc `01-architecture-baseline.md` §2.7.3/§2.7.4 新增「自动评分触发」裁定段落（D 编号续接），`nop-metadata-roadmap.md` 更新。
- [x] `ai-dev/logs/2026/07-17.md` 对应条目已更新。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] 0027-2「checkpoint 执行后自动触发评分」deferred 项已落地并收口（依 Rule #20 不回写 0027-2；收口证据记入本 plan `Closure` 段与 `ai-dev/logs/2026/07-17.md`）
- [x] 自动评分复用既有 scorer（无算法重写），在手动触发路径生效（定时路径由后续调度 plan 接入时自动受益）
- [x] 不存在被静默降级到 deferred 的 in-scope 缺口
- [x] owner docs（`01-architecture-baseline.md` §2.7.3/§2.7.4、`nop-metadata-roadmap.md`）已同步到 live baseline
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）执行→评分调用链运行时连通，（b）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw compile -pl nop-metadata -am`
- [x] `./mvnw test -pl nop-metadata/nop-metadata-service -am`
- [x] checkstyle / 代码规范检查通过
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Deferred But Adjudicated

### cron 定时调度（nop-job 适配）

- Classification: `out-of-scope improvement`（相对本 plan 结果面）
- Why Not Blocking Closure: 自动评分触发自包含且可独立验收（手动触发→自动评分即可用）。定时调度需先裁定 nop-job 跨模块依赖方向、调度引擎承载点、invoker 注册约定 vs 复用已有 `nopJobInvoker_beanMethod`（`nop-job-local/.../app-local-scheduler.beans.xml`，可经 jobParams 零新代码调用 bean 方法）、以及 `executeCheckpoint` 的 `IServiceContext` 如何从调度入口构造——属架构裁定（design-first），独立于自动评分结果面。design lead 待记录于 `ai-dev/design/nop-metadata/`（或 nop-job 设计）后独立成 plan。
- Successor Required: `yes`
- Successor Path: 后续 design-first → plan（nop-job 调度集成）

## Non-Blocking Follow-ups

- 定时调度接入后，自动评分在 cron 路径自动生效（本 plan 评分逻辑与触发源解耦）。
- 评分历史时间窗口（非仅最新结果，聚合最近 N 天）——0027-2 同源 follow-up。

## Closure

Status Note: 全部 In-Scope（D6 接线点裁定 + executor 收集 affectedTableIds + BizModel 自动评分循环 + extConfig.autoScore 控制 + per-table 失败隔离 + 摘要新增 scoreResults/errors + 4 端到端 AutoTest + owner doc §2.7.3/§2.7.4 更新）落地。自动评分触发复用既有 `MetaQualityScorer`（不在 CheckpointBizModel 内 new scorer、不复制落盘六行），关闭 0027-2「checkpoint 执行后自动触发评分」deferred 项（Successor Required: yes）。独立 closure-audit（explore subagent ses_092fb59d8ffemd0eZzDSzsYR18，adversarial live-code 核查并实际执行测试）VERDICT=PASS：14 项检查（8 Exit Criteria + 6 Anti-Hollow/anti-pattern）逐条对照 live code 全部 PASS，无 Blocker/Major。
Completed: 2026-07-17

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure-audit subagent（task_id ses_092fb59d8ffemd0eZzDSzsYR18，explore 类型，adversarial 模式，逐条 live-code 核查并实际执行测试）
- Audit Session: ses_092fb59d8ffemd0eZzDSzsYR18
- Evidence:
  - **VERDICT: PASS — can close plan.** 14 项检查全 PASS。
  - Exit Criteria 逐条验证：
    - EC1（executeCheckpoint 自动评分受影响表）：`NopMetaQualityCheckpointBizModel.executeCheckpoint:114` → `triggerAutoScoring:131-175` → `scoreBizModel.computeQualityScore:154` → `NopMetaQualityScoreBizModel.computeQualityScore:53-75` → `ensureScorer().score()` + `dao().saveEntity(row):65`。接线真实，非 stub。PASS。
    - EC2（database SKIP 不触发评分）：`MetaQualityCheckpointExecutor.execute:149-153` 显式 `if (!QUALITY_ENTITY_TYPE_DATABASE.equals(rule.getEntityType()))` 排除 database 规则。PASS。
    - EC3（复用 MetaQualityScorer，无算法重写）：CheckpointBizModel **无 MetaQualityScorer 字段**（grep 确认仅出现在注释中），仅注入 `scoreBizModel`；评分循环只调 `computeQualityScore`，不调 `scorer.score` 也不复制 `saveEntity`/落盘逻辑（grep `saveEntity|newEntity` 在 CheckpointBizModel 零匹配）。PASS。
    - EC4（新增测试覆盖）：4 个 D6 测试——(a) `testAutoScoreWritesRowAndMatchesManual`（countScores 0→1 + overallScore 与手动一致）、(b) `testAutoScoreMultipleAffectedTables`（两表各 +1）、(c) `testAutoScoreFailureIsolation`（OK 表评分、FAIL 表 0 评分、QualityResult store 不回滚）、(d) `testAutoScoreDisabledSkips`（autoScore=false → 0 新增 score 行 + QualityResult 正常写入）。PASS。
    - EC5（端到端验证）：`testAutoScoreWritesRowAndMatchesManual` 从 GraphQL `executeCheckpoint` 入口到 `countScores==1` 出口完整跑通。PASS。
    - EC6（接线验证）：`testAutoScoreWritesRowAndMatchesManual:242-243` 断言 NopMetaQualityScore 行存在（该行只有 scorer.score→saveEntity 运行才可能产生），且 `:250-255` 手动调 computeQualityScore 比对 overallScore 一致，证明运行时调用链连通。PASS。
    - EC7（无静默跳过）：`triggerAutoScoring:163-172` catch 块记录 `source=autoScore` + metaTableId + error 到摘要 errors，`testAutoScoreFailureIsolation:327-328` 断言 errors 含 autoScore + failTableId。无空 catch/continue。PASS。
    - EC8（owner doc 更新）：`01-architecture-baseline.md` §2.7.3 D6（:604-609，含受影响表集合定义/接线点裁定/失败隔离/extConfig.autoScore/摘要新增）+ §2.7.4「自动触发」交叉引用（:654）。描述与 live 实现一致。PASS。
  - Anti-Hollow 检查：
    - (9) 运行时调用链连通：测试产出真实 NopMetaQualityScore 行（非类型系统-only）。PASS。
    - (10) 无空方法体/no-op/silent continue：`triggerAutoScoring`（131-175）/`readAutoScoreConfig`（182-199）均为真实实现，catch 块非空。PASS。
    - (11) CheckpointBizModel 无自有 scorer 字段（仅注入 scoreBizModel）。PASS。
    - (12) 评分循环不复制 saveEntity/落盘逻辑（grep 零匹配）。PASS。
    - (13) 每表评分成功后 `orm().flushSession()`（:156）。PASS。
    - (14) 每表评分失败时 `orm().clearSession()`（:171）。PASS。
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（无未勾选项 + Closure Evidence 已写入）。
  - Anti-Hollow 自动扫描：`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0（Critical/High/Medium/Low 全 0）。
  - `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0。
  - 测试结果：`TestNopMetaQualityCheckpointBizModel` 12 tests 0 failure；`./mvnw test -pl nop-metadata/nop-metadata-service -am` 254 tests 0 failure/0 error，EXIT=0。
  - Deferred 项分类检查：cron 定时调度（nop-job 适配）为 out-of-scope improvement（Successor Required: yes，架构裁定 design-first），无 in-scope live defect 被降级。

Follow-up:

- no remaining plan-owned work（所有 In-Scope 已落地；Deferred 项 cron 定时调度为 out-of-scope improvement，Successor Required=yes，独立 design-first plan）。
- Non-Blocking Follow-ups（plan 已列）：定时调度接入后自动评分在 cron 路径自动生效（评分逻辑与触发源解耦）；评分历史时间窗口（非仅最新结果，聚合最近 N 天，0027-2 同源 follow-up）。
