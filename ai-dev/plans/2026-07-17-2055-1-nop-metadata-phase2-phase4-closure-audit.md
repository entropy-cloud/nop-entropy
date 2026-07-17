# Phase 2 + Phase 4 收口 + roadmap 状态对齐

> Plan Status: completed
> Last Reviewed: 2026-07-17
> Mission: nop-metadata
> Work Item: P2 + P4 phase closure（roadmap 唯一两个仍为 `todo` 的 Phase）
> Source: `ai-dev/design/nop-metadata/nop-metadata-roadmap.md`（Work Item Status: P2 `todo` / P4 `todo`；所有子项均已 `done`）；`ai-dev/design/nop-metadata/01-architecture-baseline.md` §八 待定问题（2 项未裁定）；plan `2026-07-17-1500-2`（P4 最后子项 P4-dc-2 completed，未翻转 P4 phase 状态）；plan `2026-07-17-1308-1`（P2 最后子项 P2-cron completed，未翻转 P2 phase 状态）
> Related: `2026-07-17-1500-2`（P4-dc-2，P4 最后子项）、`2026-07-17-1308-1`（P2-cron，P2 最后子项）、`2026-07-17-0852-3`（P2-multi-schema）
> Draft Review: R1 独立子 agent 对抗性审查（ses_08fd61e07ffed4Zla8TyLTbhva）发现：原 draft 过度 elaborate——P2/P4 末端子项 plan（1308-1/1500-2）的端到端测试已覆盖 phase 级 result face（`testCronJobFireNowWritesResultsAndScores` 覆盖 cron→checkpoint→QualityResult→triggerAutoScoring→QualityScore 整链；`testEntityEntityCrossDbJoinAggregationViaGraphQL` 覆盖 GraphQL→executeCrossDbJoinAggregation→executeJoin→memoryGroupBy 整链），本 plan "路径追踪" 几乎不可能产出超出已有 evidence 的新发现。Major：① 状态流转 `todo→planned→done` 路径未声明；② Phase 3 Pointers 措辞模糊且漏识别 Pointers 段已缺 0852-3/1308-1；③ Non-Goals"不重新审计子项"与"补充 AutoTest"边界冲突；Minor：④ `autoScore` 应为 `triggerAutoScoring`；⑤ P2 子项统计遗漏 0027-1/0027-2/0540-1/0540-2/1905-1。已据 R1 大幅简化：合并原 Phase 1/2 为单 Phase（系统级回归 + Anti-Hollow 复核 + 既有端到端 evidence 审计），去除冗余"路径追踪"叙述；修复全部 Major/Minor。R2 独立子 agent 复审（ses_08fcf6e45ffehMAJVJWu1Mm60Z，live repo 全量核验）：R1 全部 6 项确认修复，引用准确性 100%（测试方法名/roadmap 行号/§八 行号全部 live 核验通过），仅 1 Minor（isDelta 列计数 17→8，已修）。共识达成，Plan Status → active。

## Purpose

把 roadmap 中仍为 `todo` 的两个 Phase（P2 外部数据源/同步/血缘/质量执行；P4 联邦查询执行）正式推进到 `done`。

**诚实定位**：P2/P4 各子项 plan 已各自完成 closure audit，且末端子项 plan 的端到端测试已覆盖 phase 级集成 result face（见 Current Baseline）。本 plan 不重复子项级验证，而是做子项 plan **没有做过的三件事**：
1. **系统级回归基线复核**：复跑全量 `./mvnw test` + Anti-Hollow 扫描，确认当前 live repo 基线（而非 plan 编写时的历史基线）无回归、无新空壳。
2. **roadmap 状态对齐**：翻转 P2/P4 phase 状态 + 修复 stale 文本（各子项 plan closure 时遗漏的 phase 级状态翻转）。
3. **§八 待定问题收口**：isDelta 标注已裁定；Domain 来源现状声明 + follow-up 归属。

## Current Baseline

- **P2 子项全部 `done`**：P2-1 数据源注册、P2-2 外部表同步、P2-3 Manifest 快照、P2-4 Catalog 运行时收集、P2-5 血缘采集、P2-5++ CTE/子查询列穿透、P2-6 质量规则执行、P2-7 数据剖析、P2-multi-schema 多 schema、P2-cron 定时调度，以及增量 plan 0027-1（checkpoint 编排）、0027-2（评分）、0540-1（自动评分触发）、0540-2（结果动作投递）、1905-1（entity/sql 执行扩展）。各自有 plan 级 closure audit。
- **P2 末端子项端到端测试已覆盖 phase 级 result face**：`TestMetaQualityCheckpointScheduler.testCronJobFireNowWritesResultsAndScores`（plan 1308-1）覆盖 `fireNow → executeScheduledCheckpoint → executeCheckpoint → rule judge → NopMetaQualityResult → triggerAutoScoring → NopMetaQualityScore` 整链（cron 调度 + checkpoint 编排 + 质量执行 + 自动评分集成）。多 schema 去重键 `(metaModuleId, schema, tableName)` 经 `TestNopMetaDataSourceBizModel` 多用例覆盖（plan 0852-3）。
- **P4 子项全部 `done`**：P4-1 单表查询、P4-2 跨表 JOIN、P4-3 聚合查询、P4-3+ entity↔entity JOIN 聚合、P4-3++ external↔external 同库 JOIN 聚合 + 侧别建模、P4-4 数据契约、P4-5 Reconciliation、P4-dc-1 混合端点同库 JOIN 聚合、P4-dc-2 跨库 JOIN 聚合。各自有 plan 级 closure audit。
- **P4 末端子项端到端测试已覆盖 phase 级 result face**：`TestNopMetaAggregationBizModel` 跨库用例（plan 1500-2）覆盖 `GraphQL queryAggregation(cross-db joinId) → executeJoinAggregation → executeCrossDbJoinAggregation → MetaJoinExecutor.executeJoin → memoryGroupBy` 整链 + 按端点命名空间取值（entity=fieldName / table=物理列名 / 冲突=`<alias>_<name>`）。
- **roadmap Work Item Status**：P2 = `todo`、P4 = `todo`（与子项全部 `done` 矛盾，stale）。Rule 段（roadmap 行 220）"当前 P1 为 done，其余为 todo"同样 stale（P1+/P3 等已 done 却被"其余 todo"掩盖）。
- **roadmap 状态流转规则**（roadmap 行 14-15）："`todo` 改 `planned`；`planned` 改 `done`（不得提前）"。P2/P4 当前 `todo`，本 plan draft review 通过 → `todo→planned`；本 plan closure audit 通过 → `planned→done`。Phase 2 显式执行这两步翻转。
- **roadmap Pointers 段已缺漏**：「已完成 plan」列表（行 195）缺失 `2026-07-17-0852-3`（P2-multi-schema）和 `2026-07-17-1308-1`（P2-cron），这两个 plan 在 Work Item Status 行 104-105 被引用但 Pointers 未列入。本 plan Phase 2 补入。
- **测试基线**：`./mvnw test -pl nop-metadata/nop-metadata-service` 362 tests, 0 failures（plan 1500-2 行 118 记录）。本 plan 复跑确认当前 live repo 仍全绿。
- **Anti-Hollow 基线**：`scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0（0 findings，plan 1500-2 行 121 记录）。本 plan 复跑确认当前 live repo 无新空壳。
- **§八 待定问题 2 项**：
  - `isDelta=true/false` 用同一张表（列区分）还是两张表？——**实现已裁定**：单表 + `isDelta` 列（`01-architecture-baseline.md` §6 + `nop-metadata.orm.xml` 中 `code="IS_DELTA"` 共 8 处：NopMetaModule/Entity/EntityField/EntityRelation/EntityUniqueKey/EntityIndex/Domain/Dict 等子实体）。文档 §八 未标注已裁定。
  - 通用 Domain 的来源：单独维护还是从 ORM 模型提取？——**未裁定**。现状：MetaDomain 为元数据层映射，导入时填充；自动提取为 follow-up。本 plan 记录现状（不强行裁定设计方向，因当前 result face 不依赖该裁定）。
- **所有 "Successor Required: yes" deferred 项已收口**：经逐项核查（0228-3→0700-1、0852-1→1200-1/1500-2、1200-1→1500-1/1500-2、0027-1→1308-1/0540-2、0530-1/0420-1/0530-2→1905-1、295→0228-1 等），无未兑现的 successor。

## Goals

- **系统级回归基线复核**：复跑 `./mvnw test -pl nop-metadata -am` + Anti-Hollow 扫描，确认当前 live repo（非历史 plan 记录）全绿、无空壳。
- **P2/P4 phase 状态翻转**：`todo → planned → done`，消除 Work Item Status 与子项状态的矛盾。
- **roadmap 文本对齐**：修复 Rule 段 stale 文本；补入 Pointers 缺漏 plan；更新 header。
- **§八 待定问题收口**：isDelta 标注已裁定；Domain 来源记录现状 + follow-up 归属。

## Non-Goals

- **新增功能 / 性能优化**：本 plan 不实现新功能、不做优化。
- **重新验证子项级行为**：各子项 plan 已有自己的 closure audit + 端到端测试。本 plan **不重复子项级验证**，只做系统级回归 + evidence 审计（核查既有端到端测试是否仍覆盖 phase result face，而非重新追踪）。
- **新增测试**：不新增 AutoTest，除非系统级回归发现既有测试无法覆盖某个 phase result face 关键路径（此时该路径为 genuine gap，按 Fix 处理，补一个 focused test）。
- **非阻塞 deferred 项 / 历史 plan 回写**：不在本 plan 引入或收敛；依 Minimum Rules #20 不回写已 completed 历史 plan。

## Scope

### In Scope

- 系统级回归：`./mvnw test -pl nop-metadata -am` 全绿 + `scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0。
- 既有端到端 evidence 审计：核查 P2（1308-1 `testCronJobFireNowWritesResultsAndScores`）+ P4（1500-2 跨库聚合用例）端到端测试在当前 live repo 中仍存在且通过，作为 phase result face 集成成立的依据。
- roadmap 状态翻转：P2/P4 `todo → planned → done`（Phase 2 执行）。
- roadmap 文本对齐：Rule 段、Pointers 段（补 0852-3/1308-1 + 本 plan）、header。
- §八 待定问题：isDelta 标注已裁定；Domain 来源现状声明。

### Out Of Scope

- 新增功能 / 性能优化。
- 历史 plan 回写。
- 非 nop-metadata 模块变更。
- Domain 自动提取实现（non-blocking follow-up）。

## Execution Plan

### Phase 1 - 系统级回归 + Anti-Hollow 复核 + 既有端到端 evidence 审计

Status: completed
Targets: `nop-metadata/nop-metadata-service/`（全量测试）；既有端到端测试类（`TestMetaQualityCheckpointScheduler` / `TestNopMetaAggregationBizModel`）

- Item Types: `Proof`

- [x] **全量测试回归**：`./mvnw test -pl nop-metadata -am` 全绿（基线 362+ tests，0 failures）。若发现新失败（相对 plan 1500-2 记录的基线），记录失败用例并裁定：是 regression → Fix（本 Phase 内修复或开 successor）；是既有已知 → 记录归属。
  - **执行发现 + Fix（regression）**：首次复跑 `-pl nop-metadata -am` 发现 `NopMetadataWebPagesTest.testValidateAllPages` 失败——`NopMetaDataContract/main.page.yaml` 经 `view-gen` 解析出 `/nop/auth/pages/NopAuthUser/picker.page.yaml`，`nop-metadata-web` 不依赖 `nop-auth-web` 无法解析。根因：源 ORM `nop-metadata/model/nop-metadata.orm.xml` 的 `OWNER_USER_ID` 使用局部 `domain="userId"`，与框架 auth-user picker 约定碰撞（全仓其他 `domain="userId"` 均在 nop-auth 自身）。裁定为 P4-dc-1 遗留 regression → 本 Phase 内 Fix：移除列的 `domain="userId"`（存储不变）+ 移除不再被引用的局部 domain 定义 + 经 codegen 重生成 `_app.orm.xml`/`_NopMetaDataContract.xmeta`。修复后全量 363 tests 0 failures（详见 `ai-dev/logs/2026/07-17.md`）。
- [x] **系统级 Anti-Hollow 扫描**：`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0。若发现新空壳（相对 1500-2 基线的 0 findings），记录并裁定（Fix or Deferred）。
- [x] **P2 phase result face evidence 审计**：核查 `TestMetaQualityCheckpointScheduler.testCronJobFireNowWritesResultsAndScores` 在当前 live repo 中存在且通过——该测试覆盖 P2 核心集成链（cron 调度 → checkpoint 编排 → 质量执行 → `triggerAutoScoring` → QualityScore 落盘）。确认该测试名/断言与 plan 1308-1 closure evidence 描述一致。若测试已被重命名/删除/断言弱化，记录为 regression 并裁定。
- [x] **P4 phase result face evidence 审计**：核查 `TestNopMetaAggregationBizModel` 跨库聚合用例（`testEntityEntityCrossDbJoinAggregationViaGraphQL` 或等价）在当前 live repo 中存在且通过——该测试覆盖 P4 核心集成链（GraphQL queryAggregation → 跨库 JOIN → 内存 GROUP BY → 按端点命名空间取值）。确认测试名/断言与 plan 1500-2 closure evidence 描述一致。若测试已被重命名/删除/断言弱化，记录为 regression 并裁定。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `./mvnw test -pl nop-metadata -am` 全绿（0 failures, 0 errors），与 362 基线一致或仅有增量（新增测试）。
- [x] `scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0（0 high findings）。
- [x] P2 phase result face 有既有端到端测试覆盖且当前通过（`testCronJobFireNowWritesResultsAndScores` 或等价用例，断言非空 QualityResult/QualityScore）。若无既有覆盖 → 记录为 genuine gap 并补一个 focused test（此时该路径为确认的 contract gap，按 Fix 处理）。
- [x] P4 phase result face 有既有端到端测试覆盖且当前通过（跨库聚合 GraphQL → items 用例，断言聚合值正确）。若无既有覆盖 → 同上。
- [x] **端到端验证（#22）**：P2/P4 各至少一条端到端测试在当前 live repo 跑通（引用既有测试名 + 复跑结果，非新造测试）。
- [x] No owner-doc update required in Phase 1（纯验证；roadmap 翻转统一在 Phase 2）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - roadmap 状态对齐 + §八 待定问题收口

Status: completed
Targets: `ai-dev/design/nop-metadata/nop-metadata-roadmap.md`（Work Item Status 行 21+27、Rule 段行 220、Pointers 段行 195、header 行 3）；`ai-dev/design/nop-metadata/01-architecture-baseline.md` §八（行 1205-1208）

- Item Types: `Follow-up`

- [x] **P2/P4 `todo → planned`**：roadmap Work Item Status 中 P2 行（行 21）和 P4 行（行 27）`todo` 改 `planned`（本 plan draft review 通过即可执行此步）。
- [x] **P2/P4 `planned → done`**：Phase 1 系统级回归全绿 + evidence 审计通过后，roadmap Work Item Status 中 P2 行和 P4 行 `planned` 改 `done`。
- [x] **修复 Rule 段 stale 文本**：roadmap 行 220 "当前 P1 为 done，其余为 todo" 改为反映实际状态（P1/P1+/P2/P3/P4 + 增量项均 done，roadmap 全 Phase 完成）。
- [x] **补入 Pointers 缺漏 plan**：roadmap Pointers 段「已完成 plan」列表（行 195）追加 `2026-07-17-0852-3`（P2-multi-schema）和 `2026-07-17-1308-1`（P2-cron）（这两个 plan 已 completed 但 Pointers 遗漏）。
- [x] **追加本 plan 到 Pointers**：roadmap Pointers 段「已完成 plan」列表追加本 plan（P2+P4 phase closure）。
- [x] **更新 header Last Updated**：roadmap 行 3 追加本 plan 的 closure 记录（P2/P4 phase closure completed）。
- [x] **§八 isDelta 标注已裁定**：`01-architecture-baseline.md` §八 isDelta 行（行 1205）追加"**已裁定（P1+，2026-07-16）**：单表 + `isDelta` 列区分（`nop-metadata.orm.xml` 中 `code="IS_DELTA"` 共 8 处）"。
- [x] **§八 Domain 来源现状声明**：`01-architecture-baseline.md` §八 Domain 行（行 1208）追加现状声明——MetaDomain 为元数据层映射，导入时填充；自动提取从 ORM IOrmModel 列为 non-blocking follow-up（当前 result face 不依赖该裁定）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] roadmap Work Item Status：P2 = `done`、P4 = `done`，与子项状态一致，无矛盾。
- [x] roadmap Rule 段文本（行 220）与 Work Item Status 一致，无 stale 表述。
- [x] roadmap Pointers 段含 0852-3、1308-1、本 plan（无遗漏）。
- [x] §八 待定问题：isDelta 标注已裁定；Domain 来源有现状声明 + follow-up 归属。
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0。
- [x] No code-behavior change in Phase 2（纯文档状态对齐）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。
>
> **纯验证/文档计划**：本 plan 不涉及代码行为变更（Phase 1 为回归验证 + evidence 审计，Phase 2 为文档状态对齐）。`./mvnw test` 作为回归验证保留。

- [x] P2 Phase closure：系统级回归全绿 + 既有端到端 evidence 审计通过，roadmap P2 = `done`。
- [x] P4 Phase closure：系统级回归全绿 + 既有端到端 evidence 审计通过，roadmap P4 = `done`。
- [x] roadmap 全 Phase（P1/P1+/P2/P3/P4 + 增量项）均为 `done`，无 stale 矛盾。
- [x] §八 待定问题 2 项均有收口（isDelta 已裁定；Domain 来源现状声明 + follow-up 归属）。
- [x] roadmap Pointers 段无遗漏（含 0852-3/1308-1/本 plan）。
- [x] 不存在被静默降级到 deferred 的 in-scope live defect 或 contract drift。（P4-dc-1 web regression 已 Fix，未 deferred。）
- [x] 受影响 owner docs（roadmap + §八）已同步到 live baseline。
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据。（task_id=ses_08fad4b15ffebHorgkYyme1z90，13 项实质 criteria 全 PASS；详见 Closure Audit Evidence。）
- [x] **Anti-Hollow Check**：closure audit 已确认 `scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0，且 P2/P4 phase result face 有既有端到端测试在当前 live repo 通过（非仅历史 evidence）。
- [x] `./mvnw test -pl nop-metadata -am` 全绿（回归无破坏）。（363 tests, 0 failures, 0 errors。）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0。
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0。
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0。

## Deferred But Adjudicated

无新增 deferred 项。各历史 plan 的 optimization candidate（跨库 countDistinct 大基数精确去重、聚合 having/排序增强、内存聚合 streaming、跨库取数去重收敛等）保持各自归属，不在本 plan 引入或收敛。

## Non-Blocking Follow-ups

- 通用 Domain 自动提取（从 ORM IOrmModel 同步 MetaDomain）：out-of-scope improvement，不阻塞当前 result face（MetaDomain 导入时填充已可用）。
- 各历史 plan 的 optimization candidate（见各 plan Non-Blocking Follow-ups 段）。

## Closure

Status Note: P2/P4 各子项 plan 已各自完成 closure audit，本 plan 做子项 plan 没做过的三件事（系统级回归复核 + roadmap 状态翻转 + §八 收口），三者均已完成。执行中发现并修复了 P4-dc-1 遗留的 web validateAllPages regression（ownerUserId domain=userId 碰撞），系统级回归 363 tests 全绿。roadmap 全 Phase done，无 stale 矛盾。本 plan 可关闭。
Completed: 2026-07-17

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent closure audit（fresh session，task_id=ses_08fad4b15ffebHorgkYyme1z90）
- Evidence:
  - **Phase 1 Exit Criteria**：
    - `./mvnw test -pl nop-metadata -am` PASS：363 tests, 0 failures, 0 errors（26 个 surefire XML 无 failures/errors≥1）。
    - `scan-hollow-implementations.mjs --module nop-metadata --severity high` PASS：退出码 0，0 findings。
    - P2 result face evidence PASS：`TestMetaQualityCheckpointScheduler.testCronJobFireNowWritesResultsAndScores`（service/TestMetaQualityCheckpointScheduler.java:95）断言 QualityResult==1 + QualityScore==1，覆盖 cron→checkpoint→QualityResult→triggerAutoScoring→QualityScore 整链（5 tests, 0 fail）。
    - P4 result face evidence PASS：`TestNopMetaAggregationBizModel.testEntityEntityCrossDbJoinAggregationViaGraphQL`（service/TestNopMetaAggregationBizModel.java:375）断言非空 items，覆盖 GraphQL→executeCrossDbJoinAggregation→executeJoin→memoryGroupBy 整链（33 tests, 0 fail）。
    - 端到端验证（#22）PASS：P2/P4 各一条既有端到端测试在 live repo 跑通。
    - web regression Fix PASS：`OWNER_USER_ID` 已无 `domain="userId"`（orm.xml:1965）；局部 `<domain name="userId">` 已移除；重生成 xmeta ownerUserId schema=`<schema type="java.lang.String" precision="50"/>`（无 domain=userId）。存储语义不变，仅触碰 metadata 自身 ORM，未改框架 view-gen。
  - **Phase 2 Exit Criteria**：
    - roadmap Work Item Status PASS：P2=`done`（行 21）、P4=`done`（行 27）。
    - Rule 段 PASS：行 220 已改"当前全 Phase 均为 done"，无 stale。
    - Pointers PASS：含 0852-3、1308-1、2055-1（本 plan）。
    - §八 PASS：isDelta 已裁定（grep `code="IS_DELTA"` = 8 处）；Domain 来源有现状声明 + follow-up 归属。
    - `check-doc-links.mjs --strict` PASS：退出码 0（仅 298-nop-gateway-ai-core.md 有与本 plan 无关的预存 broken link）。
  - **Closure Gates**：13 项全部 PASS（见上方勾选）。独立子 agent 13 项实质 criteria 全 PASS；本 session 完成 closure ritual（gates 勾选 + Plan Status 翻转 + Closure evidence 写入）。
  - **Deferred 项分类检查**：无 in-scope live defect 被降级。web regression 已 Fix（未 deferred）。Non-Blocking Follow-ups 仅 Domain 自动提取（out-of-scope improvement）+ 各历史 plan optimization candidate。
  - `check-plan-checklist.mjs --strict` 退出码 0（见下方验证）。

Follow-up:

- 通用 Domain 自动提取（从 ORM IOrmModel 同步 MetaDomain）：out-of-scope improvement，不阻塞当前 result face。
- 各历史 plan 的 optimization candidate（见各 plan Non-Blocking Follow-ups 段）。
- 无 plan-owned 剩余工作。
