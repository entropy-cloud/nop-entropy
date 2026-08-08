# 1 Historical P2 Core/State/Window Finding Disposition (nop-stream Independent Audit)

> Plan Status: completed
> Last Reviewed: 2026-08-08
> Source: `ai-dev/backlog/nop-stream-independent-audit-roadmap.md` (Stage 21); `ai-dev/audits/nop-stream-independent-audit/finding-corpus.md` (Shard 21, frozen); `ai-dev/audits/nop-stream-independent-audit/evidence-schema.md` (frozen, incl. Stage 18 Supplement); live repo HEAD
> Mission: nop-stream-independent-audit
> Work Item: 21. Historical P2 core/state/window finding disposition
> Related: Execution order `{1}` of 2 in this batch. **Hard dep on Stage 18 整体**（roadmap deps: 4,9,10,11,17,18,19）— Stage 18 provides the `disposition` validator infrastructure (`--shard`/`--strict`/`all` + `@@DISPOSITION` format + 5-value vocabulary + `roadmap-stage-<N>` sentinel). Stages 4/9/10/11/17/18/19 = done. Can run in parallel with Stage 22 (no mutual dependency). **Unblocks Stage 23** (documentation contract + readiness decision).

## Purpose

对 nop-stream 历史 P2 核心侧语料（Shard 21，19 条 finding：contract/test + checkpoint/state + window 域）逐条做 live revalidation 与唯一终态裁决。每条 finding 须区分"修复仍有效"（`revalidated`）、"被架构变更废止"（`stale`）、"仍为活缺陷且有 owner"（`active/successor owner`）、"接受为非阻塞残留"（`residual-risk`，P2 允许，须附 non-blocking rationale）、或"因所需 lane 未认定而无法裁决"（`blocked`）。复用 Stage 18 的 `disposition` validator 基础设施，不重复建设。

## Current Baseline

经 2026-08-08 冻结语料 + live repo 核对：

- **冻结语料 Shard 21**：19 条 finding（07-25 multi+open），domain cluster = core/state/window（含 contract/test）。IDs：
  - P2×17：`M7-2-P2-1`（nop-stream-flow pom depends on nop-stream-cep）、`M7-2-P2-2`（duplicate source tree）、`M7-2-P2-3`（operator interface Javadoc references non-existent types）、`M7-2-P2-4`（CheckpointedSourceFunction Javadoc says unused but production calls it）、`M7-2-P2-5`（DataStream API casts UnknownTypeInformation）、`M7-2-P2-6`（IWindowOperatorFactory performative type safety）、`M7-2-P2-7`（CheckpointCoordinator logs failure twice）、`M7-2-P2-9`（TestCountTrigger vacuous，recurrent: M8-2-P2-23）、`M7-2-P2-10`（TestCheckpointBarrier getter/setter round-trip）、`M7-2-P2-11`（3 checkpoint test classes map put/get round-trips）、`M7-2-P2-12`（TestCheckpointType enum count）、`M7-2-P2-13`（TestProcessingGuarantee constant boolean，recurrent: M8-2-P2-21）、`M7-2-P2-15`（TestCheckpointIDCounter AtomicLong semantics）、`M7-2-P2-16`（TestWindowOperatorBasic geometry primitives）、`M7-2-P2-19`（README package path drift）、`M7-2-P2-20`（README vs pom cep-dep contradiction）、`M7-2-P2-21`（README vs flow-pom contradiction）。
  - AR×2：`O7-2-AR-6`（JobGraphGenerator javadoc misplaced，`status_at_0802: left-for-followup`）、`O7-2-AR-7`（PartitionPolicy dead enum values，`status_at_0802: verified-fixed`）。
- **已有 revalidation（可 cross-reference）**：
  - Stage 6（Java/local）：`M7-2-P2-3` **STALE**（EVID-S6-014/015，disposition `non-goal`；zero occurrences of TwoInputStreamOperator/MultipleInputStreamOperator in HEAD — "Final disposition owned by Stage 21"）；`M7-2-P2-5` **still live** at DataStreamImpl.java:140/183/204（"Final disposition owned by Stage 21"）。
  - Stage 7（XDSL）：`M7-2-P2-1` component-only（EVID-S7 series）；`M7-2-P2-5` e2e-proved + component-only（EVID-S7 series，XDSL path exercises the cast）。
  - Stage 11（window/time）：`M7-2-P2-6` **residual-risk**（EVID-S11-021，WindowedStreamImpl:194-240 still casts Object.class — type-safety theater contract drift）；`M7-2-P2-9` **residual-risk**（EVID-S11-022，TestCountTrigger is 15-line stub，firing semantics proven elsewhere）；`M7-2-P2-16` **residual-risk**（EVID-S11-023，TestWindowOperatorBasic tests geometry primitives not WindowOperator pipeline）。
  - Stage 17（test effectiveness）：`M7-2-P2-9/10/11/12/13/15/16` 均登记为 `live-residual`（vacuous tests），successor = active plan `2026-08-04-2300-3` (deferred-P2 owner，已 `completed`) 或 test-quality remediation successor。
  - Shard 18（Stage 18）recurrent 对应：`M8-2-P2-23`（recurrent partner of `M7-2-P2-9`）= `residual-risk`；`M8-2-P2-21`（recurrent partner of `M7-2-P2-13`）= `residual-risk`。
- **无先验 evidence 的 finding**（须从零 live 复验）：`M7-2-P2-2`（duplicate source tree）、`M7-2-P2-4`（CheckpointedSourceFunction Javadoc drift）、`M7-2-P2-7`（CheckpointCoordinator double-log）、`M7-2-P2-19`（README StreamExecutionEnvironment path drift）、`M7-2-P2-20`（README vs pom cep-dep）、`M7-2-P2-21`（README vs flow-pom）、`O7-2-AR-6`（javadoc misplaced）、`O7-2-AR-7`（PartitionPolicy dead enum）。
- **AR 项特殊状态**：
  - `O7-2-AR-6`（`status_at_0802: left-for-followup`）：corpus anchor `JobGraphGenerator.java:509-554`；须确认 javadoc 是否已修正。
  - `O7-2-AR-7`（`status_at_0802: verified-fixed`）：corpus anchor `PartitionPolicy.java`；须在当前 HEAD 确认 dead enum values 是否已移除。
- **真实 gap**：(1) Shard 21 的 19 条 finding 没有逐条 live-revalidation + 唯一终态裁决表；(2) doc-drift 类 P2（P2-19/20/21）无先验 evidence，须从零确认是否仍 live；(3) `O7-2-AR-6`（left-for-followup）状态不明，须确认 followup 是否完成。

## Goals

- 对 Shard 21 全部 **19 条** finding 逐条产出 live-revalidation 裁决，每条落到且只落到 `revalidated | stale | active/successor owner | residual-risk | blocked` 之一。
- **P2 特殊规则**：P2 可以落 `residual-risk`（须附 non-blocking rationale）；但任何 P2 经复验 reclassified 为 live P0/P1 的，须指向 `active/successor owner`（`owner_plan` 为仓库内存在的 plan 路径或 `roadmap-stage-<N>` sentinel 指向非 `done` 的 stage），不允许静默降级为 `residual-risk`。
- **区分"修复仍有效"（`revalidated`）与"被架构变更废止"（`stale`）**。
- 确认 `O7-2-AR-6`（left-for-followup）和 `O7-2-AR-7`（verified-fixed）在当前 HEAD 的状态。
- 复用 Stage 18 的 `disposition` validator（`--shard 21`），不重复建设基础设施。

## Non-Goals

- 枚举新 capability evidence row（Stages 6–16 已完成；本计划 cross-reference 已有 inventory_id）。
- 修复 nop-stream 产品代码（audit-only）。
- 处理 Shard 18/19/20/22（Stages 18/19/20/22）。
- 生产就绪结论（Stage 23）。

## Scope

### In Scope

- `ai-dev/audits/nop-stream-independent-audit/stage-21-hist-p2-core-state-window-disposition.md`（19 条 `@@DISPOSITION` 块 + header 统计）。

### Out Of Scope

- `disposition` validator 子命令实现（Stage 18 owns；本计划仅消费）。
- 其他 shard 的 finding。
- 任何 nop-stream 生产代码变更。

## Execution Plan

### Phase 1 - AR finding 裁决（2 条）与增量校验

Status: completed
Targets: `ai-dev/audits/nop-stream-independent-audit/stage-21-hist-p2-core-state-window-disposition.md`

- Item Types: `Proof`

- [x] 对 `O7-2-AR-6`（`status_at_0802: left-for-followup`，corpus anchor `JobGraphGenerator.java:509-554`）live 复验：确认 javadoc 是否已修正（若 anchor 漂移，搜索当前 HEAD 中 `determinePartitionType`/`hasNonVirtualOperator` 方法）。
- [x] 对 `O7-2-AR-7`（`status_at_0802: verified-fixed`，corpus anchor `PartitionPolicy.java`）live 复验：确认 `UNION`/`SINGLETON` dead enum values 是否已移除。
- [x] 每条 AR 写一条 `@@DISPOSITION`：仍 fixed → `revalidated`（附证据 + 新 anchor 如有漂移）；regression → `active/successor owner`；机制移除 → `stale`（附 `stale_rationale`）；仍未处理 → `residual-risk`（须附 non-blocking rationale，因 AR 非 P0/P1 可接受 residual）或 `active/successor owner`（如发现已 reclassify 为 live defect）。

Exit Criteria:

- [x] disposition 文件含 ≥2 条 `@@DISPOSITION` 覆盖全部 AR finding ID（`O7-2-AR-6`、`O7-2-AR-7`）
- [x] `node ai-dev/tools/check-nop-stream-audit-manifest.mjs disposition --shard 21`（**partial 模式，不带 `--strict`**）对已有 2 条 AR 行退出码 0——及早发现格式错误
- [x] 每条 `revalidated`/`stale`/`residual-risk` 附可复核证据
- [x] **无静默跳过**（Rule #24）：无法裁决的 AR 落 `blocked` + 命名 lane
- [x] `No owner-doc update required`（disposition 是审计基础设施）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - P2 finding 裁决（17 条）与全 shard 收口

Status: completed
Targets: `ai-dev/audits/nop-stream-independent-audit/stage-21-hist-p2-core-state-window-disposition.md`

- Item Types: `Proof`

- [x] 对 Shard 21 全部 P2（17）逐条 live 复验（同 Phase 1 方法），优先 cross-reference 已有 evidence：
  - **已有先验 evidence**（cross-reference 不重复劳动）：`M7-2-P2-1`（Stage 7 cross-reference note on flow→cep pom dep，非正式 evidence row）、`M7-2-P2-3`（Stage 6 EVID-S6-014/015 STALE/non-goal）、`M7-2-P2-5`（Stage 6 still-live + Stage 7 EVID-S7）、`M7-2-P2-6`（Stage 11 EVID-S11-021 residual-risk）、`M7-2-P2-9`（Stage 11 EVID-S11-022 + Stage 17 live-residual；recurrent partner M8-2-P2-23 = residual-risk in Shard 18）、`M7-2-P2-10/11/12/15`（Stage 17 live-residual）、`M7-2-P2-13`（Stage 17 live-residual；recurrent partner M8-2-P2-21 = residual-risk in Shard 18）、`M7-2-P2-16`（Stage 11 EVID-S11-023 + Stage 17 live-residual）。
  - **无先验 evidence**（须从零复验）：`M7-2-P2-2`（duplicate source tree — 确认 `nop-stream/src/main/java/io/nop/stream/flow/model/` 60-file duplicate tree 是否仍 git-tracked；Stage 7 cross-reference note 观察到这些是手写精简子类层、generated base 在 `_gen/` 下，供参考）、`M7-2-P2-4`（CheckpointedSourceFunction Javadoc — 确认"API 预留"注释是否已修正）、`M7-2-P2-7`（CheckpointCoordinator double-log — 确认 `onCompletePersistFailure` 是否仍 log 同一 message 两次）、`M7-2-P2-19`（README StreamExecutionEnvironment path — 确认路径 drift 是否已修正）、`M7-2-P2-20`（README vs pom cep-dep — 确认矛盾是否已修正）、`M7-2-P2-21`（README vs flow-pom — 确认矛盾是否已修正）。
- [x] 每条 P2 写一条 `@@DISPOSITION`。P2 落 `residual-risk` 须附 non-blocking rationale。仍 live 的 doc-drift P2 的自然 owner 是 Stage 23（文档契约与 readiness 判定，`todo`）——若落 `active/successor owner` 则 `owner_plan` 使用 `roadmap-stage-23` sentinel。
- [x] recurrent 项交叉核对一致性：`M7-2-P2-9`（recurrent: M8-2-P2-23）须与 Shard 18 `M8-2-P2-23` 裁决一致（均为 residual-risk 或有解释差异）；`M7-2-P2-13`（recurrent: M8-2-P2-21）须与 Shard 18 `M8-2-P2-21` 裁决一致。
- [x] header 写全 shard 统计：19 条 disposition 分布 × severity 交叉表、× domain 交叉表。
- [x] 全 shard 19 条完整性核对：每条恰好一条 `@@DISPOSITION`，无遗漏无重复。

Exit Criteria:

- [x] disposition 文件含恰好 19 条 `@@DISPOSITION`，覆盖 Shard 21 全部 finding ID（`M7-2-P2-1,2,3,4,5,6,7,9,10,11,12,13,15,16,19,20,21`、`O7-2-AR-6,7`）
- [x] `node ai-dev/tools/check-nop-stream-audit-manifest.mjs disposition --shard 21 --strict` 退出码 0（19 条完整、词表合法、字段依赖满足、`owner_plan` 为仓库路径或合法 `roadmap-stage-<N>` sentinel）
- [x] `node ai-dev/tools/check-nop-stream-audit-manifest.mjs self-test` 退出码 0
- [x] header 统计：disposition × severity 交叉表存在且 19 条合计一致
- [x] recurrent 项 `M7-2-P2-9` 与 Shard 18 `M8-2-P2-23` 裁决一致（"一致" = 二者描述同一 TestCountTrigger 根因；若 disposition 不同须在 `residual_rationale` 中解释时点差异，不允许无解释的矛盾结论）；`M7-2-P2-13` 与 `M8-2-P2-21` 同理
- [x] 每条 P2 `residual-risk` 附 non-blocking rationale；每条 P2 reclassified 为 P0/P1 的已指向 `active/successor owner`（不允许静默残留为 `residual-risk`）
- [x] **无静默跳过**（Rule #24）：无法裁决的 P2 落 `blocked` + 命名 lane
- [x] 若复验发现新 confirmed live defect（非已有 finding），已按 roadmap 规则指派 remediation plan
- [x] `No owner-doc update required`（disposition 是审计基础设施；doc-drift 的 owner 是 Stage 23，本计划仅裁决 finding 终态）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **纯审计/数据计划**：不改 nop-stream 生产代码。closure 以 validator 退出码 + disposition 完整性为证据。

- [x] Shard 21 全部 19 条 finding 各有恰好一条 `@@DISPOSITION`（completeness + no-dup）
- [x] 每条裁决值在 5-value 词表内，字段依赖满足
- [x] 不存在 P2 `residual-risk` 缺 non-blocking rationale
- [x] 不存在 P2 reclassified 为 P0/P1 仍静默降级为 `residual-risk`（须指向 `active/successor owner`）
- [x] 不存在被静默降级到 deferred/follow-up 的 in-scope finding
- [x] `active/successor owner` 的 `owner_plan` 为仓库内存在的 plan 路径或合法 `roadmap-stage-<N>` sentinel
- [x] O7-2-AR-6/7 在当前 HEAD 已 reconfirm（含 anchor 漂移处理）
- [x] `node ai-dev/tools/check-nop-stream-audit-manifest.mjs disposition --shard 21 --strict` 退出码 0
- [x] `node ai-dev/tools/check-nop-stream-audit-manifest.mjs self-test` 退出码 0
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 验证（a）每条 `revalidated` 有可复核证据，（b）每条 `active/successor owner` owner plan 真实存在，（c）无 finding 被静默丢弃

## Deferred But Adjudicated

（预期场景：某 finding 的 live 复验需 multi-JVM lane 但该 lane 有 defect——落 `blocked` + 命名 lane，是合法终态，非 deferred。P2 可以落 `residual-risk`，须附 non-blocking rationale。）

## Non-Blocking Follow-ups

- `roadmap-stage-23` sentinel 指向的 Stage 23（文档契约与 readiness 判定）plan 由后续 DRAFT_PLANS 轮次创建；本计划不负责创建 Stage 23 plan，只记录 successor 归属。
- 若某 P2 finding 经复验确认已 fixed（`revalidated`），无需 successor。
- **注意**：Stage 11/17 登记的 prior successor `2026-08-04-2300-3` 已 `completed`、`roadmap-stage-17` 已 `done`——validator 会拒绝这些 sentinel/path 作为 `owner_plan`。若某 test-quality P2 经复验确认仍 live，其 `owner_plan` 须指向 `roadmap-stage-23`（文档/契约收口，非 `done`）或由本计划触发创建新 remediation plan stub。不允许 still-live P2 落 `residual-risk` 而无 non-blocking rationale（P2 允许 residual-risk 但须附理由，不同于 P0/P1 必须有 owner）。

## Closure

Status Note: Shard 21 全部 19 条 finding（17 P2 + 2 AR）逐条完成 live revalidation 与唯一终态裁决，落点分布：11 revalidated / 1 stale / 7 residual-risk / 0 active/successor owner / 0 blocked。纯审计计划，不改 nop-stream 生产代码；closure 以 validator 退出码（strict shard 21 + self-test 均 0）+ disposition 完整性（19 条恰好覆盖、无重复、词表合法、字段依赖满足）+ 独立子 agent closure-audit PASS 为证据。recurrent 项（M7-2-P2-9↔M8-2-P2-23、M7-2-P2-13↔M8-2-P2-21）均与 Shard 18 裁决一致（双方均 residual-risk）。所有 residual-risk P2 附 non-blocking rationale；无 P2 被 reclassified 为 P0/P1 后静默降级。
Completed: 2026-08-08

Closure Audit Evidence:

- Reviewer / Agent: independent closure-audit subagent (fresh session, task id `ses_020cf3cb9ffeZtV8VqefImX3kw`, general agent type)
- Evidence:
  - Validator: `disposition --shard 21 --strict` exit 0 (92 disposition rows across all shards validated; all 19 Shard 21 findings covered); `self-test` exit 0 (all 5 checkers reject known-bad input)
  - Completeness/no-dup: 19 `@@DISPOSITION` blocks, 19 unique IDs (M7-2-P2-1,2,3,4,5,6,7,9,10,11,12,13,15,16,19,20,21 + O7-2-AR-6,7) — exactly matches Shard 21 corpus, no dup, no missing
  - Header stats: independent tally matches both cross-tabs (severity: revalidated{P2=9,AR=2}=11, stale{P2=1}=1, residual-risk{P2=7}=7; domain: contract/test=10, checkpoint/state=6, window=3; total=19)
  - Anti-Hollow spot check (6 findings verified against live repo HEAD, all PASS): O7-2-AR-7 (PartitionPolicy.java 16 lines, only FORWARD/HASH/REBALANCE/BROADCAST); M7-2-P2-7 (onCompletePersistFailure:821-831 single LOG.error at :824, no double-log); M7-2-P2-2 (`nop-stream/src` absent); M7-2-P2-11 (TestTaskStateSnapshot.testSerialization:119-136); M7-2-P2-5 (DataStreamImpl casts UnknownTypeInformation.INSTANCE at :140/183/204); M7-2-P2-9 (TestCountTrigger 15-line vacuous stub, only canMerge()==false)
  - Recurrent consistency: M7-2-P2-9 (Stage 21 residual-risk) ⟷ M8-2-P2-23 (Stage 18:395 residual-risk) CONSISTENT; M7-2-P2-13 (Stage 21 residual-risk) ⟷ M8-2-P2-21 (Stage 18:378 residual-risk) CONSISTENT
  - No silent drop: all 7 residual-risk blocks carry non-empty residual_rationale; severities = 17 P2 + 2 AR (no hidden P0/P1); 0 blocked
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file>` exit 0 (no unchecked in-scope items)
  - Anti-Hollow conclusion: all spot-checked dispositions backed by live repo HEAD evidence, not fabricated

Follow-up:

- `roadmap-stage-23`（文档契约与 readiness 判定，roadmap status `todo`）owns the formal doc-convergence sweep (component-roadmap.md IEvalFunction-provenance wording residue noted in M7-2-P2-20) and the test-effectiveness convergence for the 7 residual-risk P2 test-quality gaps. No plan-owned remediation work remains in this plan.
- No confirmed live defect discovered beyond the existing finding set (no new finding needed a fresh remediation plan).
