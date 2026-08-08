# 2 Historical P0/P1 Core/State/Window Finding Disposition (nop-stream Independent Audit)

> Plan Status: active
> Last Reviewed: 2026-08-08
> Source: `ai-dev/backlog/nop-stream-independent-audit-roadmap.md` (Stage 19); `ai-dev/audits/nop-stream-independent-audit/finding-corpus.md` (Shard 19, frozen); `ai-dev/audits/nop-stream-independent-audit/evidence-schema.md` (frozen, incl. Stage 18 Supplement); live repo HEAD
> Mission: nop-stream-independent-audit
> Work Item: 19. Historical P0/P1 checkpoint/state/window finding disposition
> Related: Execution order `{2}` of 3 in this batch. **Hard dep on Stage 18 整体**（roadmap deps: 4,9,10,11,18）— Stage 18 须为 `done`（含 Phase 1 disposition validator 基础设施 + Phase 2/3 Shard 18 裁决）后本计划才可执行 Phase 2 的 recurrent 交叉核对（M7-2-P1-6 ↔ M8-2-P1-10）。Stage 18 提供的 `disposition` validator 子命令、`@@DISPOSITION` 格式、5-value 词表、`roadmap-stage-<N>` sentinel 机制均为本计划的前置依赖。Stages 4/9/10/11 = done。

## Purpose

对 nop-stream 历史 P0/P1 核心侧语料（Shard 19，16 条 finding：checkpoint/state + window + contract/test 域）逐条做 live revalidation 与唯一终态裁决。每条 finding 须区分"修复仍有效"与"被后续架构变更废止"。复用 Stage 18 建立的 `disposition` validator 基础设施。

## Current Baseline

经 2026-08-08 冻结语料 + live repo 核对：

- **冻结语料 Shard 19**：16 条 finding（07-25 multi+open），domain cluster = core/state/window（含 contract/test）。IDs：
  - P0×5：`M7-2-P0-2`（TwoPhaseCommitSinkFunction.restoreFromEpoch 数据丢失）、`M7-2-P0-3`（StreamSinkOperator.restoreState compounding）、`M7-2-P0-5`（serializer fingerprint ZERO test）、`M7-2-P0-7`（savepoint operatorId differential ZERO test）、`M7-2-P0-8`（stateShardCount rescale ZERO test）。
  - P1×11：`M7-2-P1-1`（StreamComponents Map<String,Object>）、`M7-2-P1-2`（getBean ignores clazz）、`M7-2-P1-3`（StreamSinkOperator TPCSF dead code）、`M7-2-P1-4`（StreamOperator.initializeState never called）、`M7-2-P1-6`（StateDescriptor fake type safety，recurrent: M8-2-P1-10）、`M7-2-P1-7`（IInternalStateBackend unconstrained ACC）、`M7-2-P1-11`（CheckpointBarrierTracker swallows snapshot errors）、`M7-2-P1-16`（TimestampsAndWatermarksOperator doc drift）、`M7-2-P1-17`（INDEX.md non-existent modules）、`M7-2-P1-18`（core package path drift）、`M7-2-P1-19`（CheckpointCoordinator mis-attribution）。
- **已有 revalidation（可 cross-reference）**：
  - Stage 6：`M7-2-P1-5` RESOLVED（注：P1-5 属 shard 20，不在此；但说明 07-25 历史项已有部分被复验）。
  - Stage 9：`M7-2-P1-4` residual-risk（EVID-S9 系列）。
  - Stage 10（state/savepoint）：`M7-2-P0-5/7/8` 的 savepoint/rescale ZERO-test 复验。
  - Stage 11（window/time）：window 域复验。
  - Stage 16：`M7-2-P0-2` FIXED（cross-ref Stage 19 formal owner — 即本计划）。
- **07-25 open rollup 的 O7-2-AR-1..7**：分属 shard 20/21/22，**不**在 shard 19（shard 19 全部来自 07-25 multi-audit）。
- **真实 gap**：(1) Shard 19 的 16 条 finding 没有逐条 live-revalidation + 唯一终态裁决表；(2) 部分 finding（如 M7-2-P0-2 sink 数据丢失）需确认是已被 Stage 9/10/16 修复还是仍 live；(3) ZERO-test 类 finding（M7-2-P0-5/7/8）需确认当前是否有补测试。

## Goals

- 对 Shard 19 全部 **16 条** finding 逐条产出 live-revalidation 裁决，每条落到 `revalidated | stale | active/successor owner | residual-risk | blocked` 之一。
- 区分"修复仍有效"（`revalidated`，附修复证据）与"被架构变更废止"（`stale`，附锚点消失/上下文重构证据）。
- 复用 Stage 18 的 `disposition` validator（`--shard 19`），不重复建设基础设施。

## Non-Goals

- 枚举新 capability evidence row（Stages 6–16 已完成；本计划 cross-reference 已有 inventory_id）。
- 修复 nop-stream 产品代码（audit-only）。
- 处理 Shard 18/20/21/22（Stages 18/20/21/22）。
- 生产就绪结论（Stage 23）。

## Scope

### In Scope

- `ai-dev/audits/nop-stream-independent-audit/stage-19-hist-p0p1-core-state-window-disposition.md`（16 条 `@@DISPOSITION` 块 + header 统计）。

### Out Of Scope

- `disposition` validator 子命令实现（Stage 18 owns；本计划仅消费）。
- 其他 shard 的 finding。
- 任何 nop-stream 生产代码变更。

## Execution Plan

### Phase 1 - P0 finding 裁决（5 条）

Status: planned
Targets: `ai-dev/audits/nop-stream-independent-audit/stage-19-hist-p0p1-core-state-window-disposition.md`

- Item Types: `Proof`

- [ ] 对 Shard 19 全部 P0（5）逐条 live 复验：(a) 核对 anchor `file:line` 在 HEAD 是否存在（**注意**：历史锚点是 07-25 冻结的，代码可能已移动/重构——若行号不再匹配原缺陷，搜索当前 HEAD 中对应方法/逻辑确认修复是否存在，附新 anchor 作为证据）；(b) 若 anchor 存在，核对缺陷活行为是否仍成立；(c) cross-reference Stage 9/10/11/16 已有 evidence row（inventory_id：M7-2-P1-4→EVID-S9-014、M7-2-P0-2→Stage 16 FIXED，EVID-S16-014）；(d) 对 ZERO-test 类（M7-2-P0-5/7/8），确认当前测试树是否已补对应回归测试。
- [ ] 每条 P0 写一条 `@@DISPOSITION`。still-live P0 须落 `active/successor owner`（`owner_plan` 为仓库内存在的 plan 路径，或 `roadmap-stage-<N>` sentinel）；`revalidated` 须附修复证据；`stale` 须附锚点消失证据。

Exit Criteria:

- [ ] disposition 文件含 ≥5 条 `@@DISPOSITION` 覆盖全部 P0 finding ID
- [ ] `node ai-dev/tools/check-nop-stream-audit-manifest.mjs disposition --shard 19` 对已有 5 条 P0 行退出码 0（partial-check：P1/AR 允许未填）——及早发现格式错误
- [ ] 每条 still-live P0 落 `active/successor owner`（`owner_plan` 为仓库 plan 路径或 `roadmap-stage-<N>` sentinel）；不存在 P0 still-live 静默降级为 `residual-risk`
- [ ] 每条 `revalidated`/`stale` 附可复核证据
- [ ] **无静默跳过**（Rule #24）：无法裁决的 P0 落 `blocked` + 命名 lane
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - P1 finding 裁决（11 条）与全 shard 收口

Status: planned
Targets: `ai-dev/audits/nop-stream-independent-audit/stage-19-hist-p0p1-core-state-window-disposition.md`

- Item Types: `Proof`

- [ ] 对 Shard 19 全部 P1（11）逐条 live 复验（同 Phase 1 方法），优先 cross-reference Stages 6/9/10/11 evidence row。注意 recurrent 项 `M7-2-P1-6`（recurrent: M8-2-P1-10）须与 Shard 18 的对应裁决交叉核对一致性。
- [ ] 每条 P1 写一条 `@@DISPOSITION`。still-live P1 须落 `active/successor owner`。**doc-drift 类 P1**（M7-2-P1-16/17/18/19）：其中 M7-2-P1-16 经 Stage 11 确认 still-live（EVID-S11-013/020）；M7-2-P1-17/18/19（README/INDEX.md drift）**无先验 evidence**，须从零 live 复验确认是否仍 live。仍 live 的 doc-drift P1 的自然 owner 是 Stage 23（文档契约与 readiness 判定，`todo`，尚无 plan 文件）——其 `owner_plan` 使用 `roadmap-stage-23` sentinel（Stage 18 validator 已支持此 sentinel，指向一个非 `done` 的 roadmap stage 作为 successor owner）。**不允许** still-live P1 落 `residual-risk` 或推迟到 follow-up。
- [ ] header 写全 shard 统计：16 条 disposition 分布 × severity 交叉表、× domain 交叉表。
- [ ] 全 shard 16 条完整性核对：每条恰好一条 `@@DISPOSITION`。

Exit Criteria:

- [ ] disposition 文件含恰好 16 条 `@@DISPOSITION`，覆盖 Shard 19 全部 finding ID（`M7-2-P0-2,3,5,7,8`、`M7-2-P1-1,2,3,4,6,7,11,16,17,18,19`）
- [ ] `node ai-dev/tools/check-nop-stream-audit-manifest.mjs disposition --shard 19 --strict` 退出码 0（16 条完整、词表合法、字段依赖满足、`owner_plan` 为仓库路径或合法 `roadmap-stage-<N>` sentinel）
- [ ] `node ai-dev/tools/check-nop-stream-audit-manifest.mjs self-test` 退出码 0
- [ ] header 统计：disposition × severity 交叉表存在且 16 条合计一致
- [ ] recurrent 项 `M7-2-P1-6` 与 Shard 18 `M8-2-P1-10` 裁决一致（"一致" = 二者描述同一 StateDescriptor 根因；若二者 disposition 不同须在 `residual_rationale`/`stale_rationale` 中解释时点差异，不允许矛盾结论——如一方 `revalidated` 另一方 `active/successor owner` 而无解释）
- [ ] 不存在 P0/P1 still-live defect 静默降级为 `residual-risk`（still-live P1 doc-drift 须落 `active/successor owner` + `roadmap-stage-23` sentinel，不得降级）
- [ ] **无静默跳过**（Rule #24）
- [ ] 若复验发现新 confirmed live defect（非已有 finding），已按 roadmap 规则指派 remediation plan
- [ ] `No owner-doc update required`（disposition 是审计基础设施）
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **纯审计/数据计划**：不改 nop-stream 生产代码。closure 以 validator 退出码 + disposition 完整性为证据。

- [ ] Shard 19 全部 16 条 finding 各有恰好一条 `@@DISPOSITION`（completeness + no-dup）
- [ ] 每条裁决值在 5-value 词表内，字段依赖满足
- [ ] 不存在 P0/P1 still-live defect 静默降级为 `residual-risk`
- [ ] 不存在被静默降级到 deferred/follow-up 的 in-scope finding
- [ ] `active/successor owner` 的 `owner_plan` 为仓库内存在的 plan 路径或合法 `roadmap-stage-<N>` sentinel
- [ ] `node ai-dev/tools/check-nop-stream-audit-manifest.mjs disposition --shard 19 --strict` 退出码 0
- [ ] `node ai-dev/tools/check-nop-stream-audit-manifest.mjs self-test` 退出码 0
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 验证（a）每条 `revalidated` 有可复核证据，（b）每条 `active/successor owner` owner plan 真实存在，（c）无 finding 被静默丢弃

## Deferred But Adjudicated

（预期场景：某 finding 的 live 复验需 multi-JVM lane 但 T2 有 defect——落 `blocked` + 命名 T2，是合法终态，非 deferred。confirmed still-live P0/P1 不得 deferred——须指派 remediation plan。）

## Non-Blocking Follow-ups

- `roadmap-stage-23` sentinel 指向的 Stage 23（文档契约与 readiness 判定）plan 由后续 DRAFT_PLANS 轮次创建；本计划不负责创建 Stage 23 plan，只记录 successor 归属。
- 若某 P0/P1 finding 经复验确认已 fixed（`revalidated`），无需 successor。

## Closure

Status Note: <<完成或关闭时填写>>
Completed: <<YYYY-MM-DD>>

Closure Audit Evidence:

- Reviewer / Agent: <<独立审阅者或独立子 agent>>
- Evidence: <<逐条 Exit Criterion / Closure Gate 验证结果 + validator 退出码 + Anti-Hollow 检查>>

Follow-up:

- <<只记录 non-blocking follow-up；confirmed live defect 不得出现在这里>>
