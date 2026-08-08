# 3 Historical P0/P1 CEP/Connector/Runtime Finding Disposition (nop-stream Independent Audit)

> Plan Status: completed
> Last Reviewed: 2026-08-08
> Source: `ai-dev/backlog/nop-stream-independent-audit-roadmap.md` (Stage 20); `ai-dev/audits/nop-stream-independent-audit/finding-corpus.md` (Shard 20, frozen); `ai-dev/audits/nop-stream-independent-audit/evidence-schema.md` (frozen, incl. Stage 18 Supplement); live repo HEAD
> Mission: nop-stream-independent-audit
> Work Item: 20. Historical P0/P1 CEP/connector/runtime finding disposition
> Related: Execution order `{3}` of 3 in this batch. **Hard dep on Stage 18 整体**（roadmap deps: 4,12,13,14,15,16,18）— Stage 18 须提供 `disposition` validator 子命令（`--shard`/`--strict`/`all` 接线 + `@@DISPOSITION` 格式 + 5-value 词表 + `roadmap-stage-<N>` sentinel）后本计划才可执行。Stages 4/12/13/14/15/16 = done。可与 Stage 19 并行（无相互依赖）。

## Purpose

对 nop-stream 历史 P0/P1 分布式侧语料（Shard 20，15 条 finding：CEP + connector + coordinator/runtime 域）逐条做 live revalidation 与唯一终态裁决。含 4 条 `O7-2-AR-*`（已在 08-02 HEAD 标记 `status_at_0802: verified-fixed`），须确认其在当前 HEAD（08-02 之后）是否仍 fixed。复用 Stage 18 的 `disposition` validator 基础设施。

## Current Baseline

经 2026-08-08 冻结语料 + live repo 核对：

- **冻结语料 Shard 20**：15 条 finding（07-25 multi+open），domain cluster = CEP/connector/runtime。IDs：
  - P0×3：`M7-2-P0-1`（SingleOutputStreamOperator.forceNonParallel always throws，CEP 非 keyed 崩溃）、`M7-2-P0-4`（TestCepOperatorDanglingCleanup computes-but-never-asserts）、`M7-2-P0-6`（fencing-token rejection ZERO test）。
  - P1×8：`M7-2-P1-5`（StreamOperator.finish() never called）、`M7-2-P1-8`（InputGate.readSingleChannel swallows interrupt）、`M7-2-P1-9`（MessageSourceFunction swallows collect exceptions，数据丢失）、`M7-2-P1-10`（ResultPartition.close discards un-consumed records）、`M7-2-P1-12`（watermark multi-input combine unit-only）、`M7-2-P1-13`（TestCepOperatorStateBackendWiring couples internals）、`M7-2-P1-14`（TestAfterMatchSkipStrategies 100% metadata）、`M7-2-P1-15`（TestBatchConsumerSinkFunction happy-path-only）。
  - AR×4：`O7-2-AR-1`（OperatorChain.shallowCopyOperator shares mutable instance，`status_at_0802: verified-fixed`）、`O7-2-AR-2`（StreamConnectors hard-ref optional deps，`status_at_0802: verified-fixed`）、`O7-2-AR-3`（PartitionedPlanGenerator class-name string matching，`status_at_0802: verified-fixed`）、`O7-2-AR-4`（SimpleStreamOperatorFactory falls back to shared template，`status_at_0802: verified-fixed`）。
- **已有 revalidation（可 cross-reference）**：
  - Stage 6：`M7-2-P0-1` RESOLVED（LOCAL path）、`M7-2-P1-5` RESOLVED。
  - Stage 12（CEP）：`M7-2-P0-1` FIXED regression（**EVID-S12-002**，`finding_id: M7-2-P0-1`，`disposition: e2e-proved`）；`M7-2-P0-4` FIXED（**EVID-S12-014**，`finding_id: M7-2-P0-4`，`disposition: e2e-proved`；`TestCepOperatorDanglingCleanup:114-122` 现在断言 `partialMatchesEmpty`）；`M7-2-P1-13/14` residual-risk + Stage 17 successor。
  - Stage 13（control-plane/HA/fencing）：`M7-2-P0-6` **RESOLVED**（**EVID-S13-007 / EVID-S13-020**，`disposition: e2e-proved`；`TaskManager.receiveAssignment/triggerCheckpoint/deployTask` 现抛 `ERR_STREAM_FENCING_TOKEN_MISMATCH`，由 `TestFencingTokenRejection` + `TestFencingEpochUnification` 覆盖）。
  - Stage 15（batch/message）：`M7-2-P1-9` FIXED（**EVID-S15-008**）、`M7-2-P1-15` residual-risk。
  - **无先验 evidence 的 finding**（须从零复验）：`M7-2-P1-8`（InputGate.readSingleChannel swallows interrupt）、`M7-2-P1-10`（ResultPartition.close discards un-consumed records）、`M7-2-P1-12`（watermark multi-input combine unit-only）。**注意**：M7-2-P1-10 在任何 stage evidence 文件中均无出现（Stage 14 处理的是跨 JVM `RemoteResultPartition`，非此 finding 的 `ResultPartition.java:178-193` bounded-source EOS 数据丢失）。
- **O7-2-AR-1..4 verified-fixed 状态**：corpus 注明 `status_at_0802: verified-fixed`（per 08-02 open-audit prior-audit re-verification table）。本计划须确认 08-02 之后无 regression（即当前 HEAD 仍 fixed）。**已知 anchor 漂移**（历史锚点是 07-25 冻结的，代码已重构）：
  - `O7-2-AR-1`：corpus anchor `OperatorChain.java:206-235`（`shallowCopyOperator()`）已漂移——HEAD 中该行范围现为 `getOperators()`/Javadoc；原方法已改名为 `deepCopy()`（约 line 244-250），实现从"silently shares mutable instance"变为 `op.copyForSubtask()` + `UnsupportedOperationException` fail-fast。
  - `O7-2-AR-2`：corpus anchor `StreamConnectors.java:10-11; nop-stream-connector/pom.xml` 已漂移——`StreamConnectors.java` 已迁移至 `nop-stream-connector-batch/`（非 `nop-stream-connector/`）；`nop-stream-connector/pom.xml` 注释记录了 AR-2 迁移。
  - 复验时须搜索当前 HEAD 中对应重命名/迁移方法，确认修复逻辑存在后判 `revalidated`，附新 anchor 作为 revalidation evidence。
- **真实 gap**：(1) Shard 20 的 15 条 finding 没有逐条 live-revalidation + 唯一终态裁决表；(2) O7-2-AR-1..4 的 verified-fixed 须在当前 HEAD 再确认（含 anchor 漂移处理）；(3) M7-2-P0-6 已在 Stage 13 RESOLVED（须交叉核对一致性）；(4) M7-2-P1-8/10/12 无先验 evidence。

## Goals

- 对 Shard 20 全部 **15 条** finding 逐条产出 live-revalidation 裁决，每条落到 `revalidated | stale | active/successor owner | residual-risk | blocked` 之一。
- 确认 4 条 `O7-2-AR-*`（verified-fixed at 08-02）在当前 HEAD 仍 fixed（无 regression）；若已 regression，须落 `active/successor owner`；若被架构变更废止（整个机制已移除），落 `stale`（附 stale_rationale）。
- **区分"修复仍有效"（`revalidated`）与"被架构变更废止"（`stale`）**：修复仍有效 = 修复逻辑存在（可能在重命名/迁移后的位置），缺陷行为不再成立；被架构变更废止 = 原 finding 描述的机制/上下文已整体移除，finding 不再适用（附 stale_rationale 说明移除点）。
- 复用 Stage 18 的 `disposition` validator（`--shard 20`）。

## Non-Goals

- 枚举新 capability evidence row（Stages 6–16 已完成；cross-reference 已有 inventory_id）。
- 修复 nop-stream 产品代码（audit-only）。
- 处理 Shard 18/19/21/22（Stages 18/19/21/22）。
- 生产就绪结论（Stage 23）。

## Scope

### In Scope

- `ai-dev/audits/nop-stream-independent-audit/stage-20-hist-p0p1-cep-connector-runtime-disposition.md`（15 条 `@@DISPOSITION` 块 + header 统计）。

### Out Of Scope

- `disposition` validator 子命令实现（Stage 18 owns）。
- 其他 shard 的 finding。
- 任何 nop-stream 生产代码变更。

## Execution Plan

### Phase 1 - P0 finding 裁决（3 条）

Status: completed
Targets: `ai-dev/audits/nop-stream-independent-audit/stage-20-hist-p0p1-cep-connector-runtime-disposition.md`

- Item Types: `Proof`

- [x] 对 Shard 20 全部 P0（3）逐条 live 复验：(a) 核对 anchor `file:line` 在 HEAD 是否存在（**注意**：历史锚点是 07-25 冻结的，代码可能已移动/重构——若行号不匹配，搜索当前 HEAD 中对应方法确认修复是否存在，附新 anchor）；(b) cross-reference Stage 6/12/13 已有 evidence（`M7-2-P0-1`→EVID-S12-002 FIXED、`M7-2-P0-4`→EVID-S12-014 FIXED、`M7-2-P0-6`→EVID-S13-007/EVID-S13-020 RESOLVED）；(c) 三条 P0 均须与对应 stage 结论交叉核对一致性。
- [x] 每条 P0 写一条 `@@DISPOSITION`。still-live P0 须落 `active/successor owner`（`owner_plan` 为仓库 plan 路径或 `roadmap-stage-<N>` sentinel）。

Exit Criteria:

- [x] disposition 文件含 ≥3 条 `@@DISPOSITION` 覆盖全部 P0 finding ID
- [x] `node ai-dev/tools/check-nop-stream-audit-manifest.mjs disposition --shard 20`（**partial 模式，不带 `--strict`**）对已有 3 条 P0 行退出码 0——及早发现格式错误
- [x] 每条 still-live P0 落 `active/successor owner`；不存在 P0 still-live 静默降级为 `residual-risk`
- [x] `M7-2-P0-1` 裁决与 Stage 6/12（EVID-S12-002 FIXED）一致；`M7-2-P0-4` 裁决与 Stage 12（EVID-S12-014 FIXED）一致；`M7-2-P0-6` 裁决与 Stage 13（EVID-S13-007/020 RESOLVED）一致
- [x] 每条 `revalidated`/`stale` 附可复核证据
- [x] **无静默跳过**（Rule #24）：无法裁决的 P0 落 `blocked` + 命名 lane
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - P1 finding 裁决（8 条）

Status: completed
Targets: `ai-dev/audits/nop-stream-independent-audit/stage-20-hist-p0p1-cep-connector-runtime-disposition.md`

- Item Types: `Proof`

- [x] 对 Shard 20 全部 P1（8）逐条 live 复验，优先 cross-reference Stages 6/12/15 evidence row。`M7-2-P1-5`（Stage 6 标 RESOLVED，EVID-S6-011）、`M7-2-P1-9`（Stage 15 标 FIXED，EVID-S15-008）、`M7-2-P1-15`（Stage 15 标 residual-risk）、`M7-2-P1-13/14`（Stage 12 标 residual-risk + Stage 17 successor）须交叉核对一致性。**`M7-2-P1-8`/`M7-2-P1-10`/`M7-2-P1-12` 无先验 evidence**，须从零 live 复验（读代码/跑测试/trace 调用链）。
- [x] 每条 P1 写一条 `@@DISPOSITION`。still-live P1 须落 `active/successor owner`（`owner_plan` 为仓库 plan 路径或 `roadmap-stage-<N>` sentinel）。

Exit Criteria:

- [x] disposition 文件含恰好 11 条 `@@DISPOSITION`（3 P0 + 8 P1）覆盖全部 P0/P1 finding ID（≥11 可能掩盖重复 ID，须精确 11）
- [x] `node ai-dev/tools/check-nop-stream-audit-manifest.mjs disposition --shard 20`（partial 模式）对已有 11 条退出码 0
- [x] 每条 still-live P1 落 `active/successor owner`
- [x] `M7-2-P1-5` 裁决与 Stage 6（EVID-S6-011 RESOLVED）一致；`M7-2-P1-9` 裁决与 Stage 15 EVID-S15-008（FIXED）一致；`M7-2-P1-13/14` 裁决与 Stage 12 residual-risk + Stage 17 successor 一致
- [x] `M7-2-P1-8`/`M7-2-P1-10`/`M7-2-P1-12` 有从零复验的 live 证据（非空泛"已检查"）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - AR finding 裁决（4 条 verified-fixed 确认）与全 shard 收口

Status: completed
Targets: `ai-dev/audits/nop-stream-independent-audit/stage-20-hist-p0p1-cep-connector-runtime-disposition.md`

- Item Types: `Proof`

- [x] 对 4 条 `O7-2-AR-1..4`（`status_at_0802: verified-fixed`）在当前 HEAD 确认仍 fixed。**anchor 漂移处理**（corpus anchor 是 07-25 冻结的，代码已重构）：若 corpus anchor 不再匹配，搜索当前 HEAD 中对应的重命名/迁移方法（预记录：O7-2-AR-1 → `OperatorChain.deepCopy()` 约 line 244；O7-2-AR-2 → `nop-stream-connector-batch/StreamConnectors.java`），确认修复逻辑存在后判 `revalidated`，附新 anchor 作为 revalidation evidence。
- [x] 每条 AR 写一条 `@@DISPOSITION`：仍 fixed → `revalidated`（附 revalidation 证据 + 新 anchor）；若 regression → `active/successor owner`；若整个机制被架构变更移除使 finding 不再适用 → `stale`（附 `stale_rationale` 说明移除点）。
- [x] header 写全 shard 统计：15 条 disposition 分布 × severity 交叉表、× domain 交叉表。
- [x] 全 shard 15 条完整性核对。

Exit Criteria:

- [x] disposition 文件含恰好 15 条 `@@DISPOSITION`，覆盖 Shard 20 全部 finding ID（`M7-2-P0-1,4,6`、`M7-2-P1-5,8,9,10,12,13,14,15`、`O7-2-AR-1,2,3,4`）
- [x] `node ai-dev/tools/check-nop-stream-audit-manifest.mjs disposition --shard 20 --strict` 退出码 0（15 条完整、词表合法、字段依赖满足、`owner_plan` 为仓库路径或合法 `roadmap-stage-<N>` sentinel）
- [x] `node ai-dev/tools/check-nop-stream-audit-manifest.mjs self-test` 退出码 0
- [x] header 统计：disposition × severity 交叉表存在且 15 条合计一致
- [x] O7-2-AR-1..4 每条有当前 HEAD 的 fixed-confirmation（仍 fixed → `revalidated` + 证据 + 新 anchor；regression → `active/successor owner`；机制移除 → `stale` + rationale）
- [x] 不存在 P0/P1 still-live defect 静默降级为 `residual-risk`
- [x] **无静默跳过**（Rule #24）
- [x] 若复验发现新 confirmed live defect，已按 roadmap 规则指派 remediation plan
- [x] `No owner-doc update required`（disposition 是审计基础设施）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **纯审计/数据计划**：不改 nop-stream 生产代码。closure 以 validator 退出码 + disposition 完整性为证据。

- [x] Shard 20 全部 15 条 finding 各有恰好一条 `@@DISPOSITION`（completeness + no-dup）
- [x] 每条裁决值在 5-value 词表内，字段依赖满足
- [x] 不存在 P0/P1 still-live defect 静默降级为 `residual-risk`
- [x] 不存在被静默降级到 deferred/follow-up 的 in-scope finding
- [x] `active/successor owner` 的 `owner_plan` 为仓库内存在的 plan 路径或合法 `roadmap-stage-<N>` sentinel
- [x] O7-2-AR-1..4 verified-fixed 在当前 HEAD 已 reconfirm（含 anchor 漂移处理：新 anchor 记录在 revalidation_evidence 中）
- [x] `node ai-dev/tools/check-nop-stream-audit-manifest.mjs disposition --shard 20 --strict` 退出码 0
- [x] `node ai-dev/tools/check-nop-stream-audit-manifest.mjs self-test` 退出码 0
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 验证（a）每条 `revalidated` 有可复核证据，（b）每条 `active/successor owner` owner plan 真实存在，（c）无 finding 被静默丢弃

## Deferred But Adjudicated

（预期场景：某 finding 的 live 复验需 multi-JVM lane 但 T2/T3/T4 有 defect/blocked——落 `blocked` + 命名 lane，是合法终态，非 deferred。confirmed still-live P0/P1 不得 deferred——须指派 remediation plan。）

## Non-Blocking Follow-ups

- M7-2-P1-13/14/15（test-effectiveness P1）的 disposition 由本计划根据 live 复验裁定：若 remediation plan `2026-08-04-2300-3`（`completed`）已修复 → `revalidated`；若仍 live → `active/successor owner`。**注意**：`2026-08-04-2300-3` 已 `completed`，不能作为 still-live defect 的 owner；`roadmap-stage-17` 已 `done`，validator 会拒绝此 sentinel。因此仍 live 的 test-effectiveness P1 的 `owner_plan` 须指向 `roadmap-stage-23`（文档/契约收口，非 `done`）或由本计划触发创建新 remediation plan stub。不允许 still-live P1 落 `residual-risk`。

## Closure

Status Note: Shard 20 全部 15 条 finding（3 P0 + 8 P1 + 4 AR）逐条 live-revalidation 完成，全部裁决 `revalidated`（缺陷在当前 HEAD 已修复或经正式裁定不再成立）。纯审计/数据计划，不改 nop-stream 生产代码；closure 以 validator 退出码 + disposition 完整性 + 独立 closure audit 为证据。O7-2-AR-1..4 verified-fixed 在当前 HEAD reconfirm 无 regression（anchor 漂移已处理，新 anchor 记录在 revalidation_evidence）。P1-8/P1-10/P1-12 三条无先验 evidence 的 finding 从零 live 复验（P1-8/P1-10 FIXED 含 dedicated regression tests；P1-12 Anti-Hollow-exempted non-goal per Stage 11 EVID-S11-016）。test-effectiveness P1（P1-13/14/15）由已完成 remediation plan 全部修复。
Completed: 2026-08-08

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent (task ses_02139f06affem8urjs7O2C7x5t, general-type, fresh session)
- Evidence:
  - **Check 1 (Completeness + no-dup)**: PASS — 恰好 15 条 `@@DISPOSITION`，覆盖全部 Shard 20 finding ID（M7-2-P0-1,4,6 / M7-2-P1-5,8,9,10,12,13,14,15 / O7-2-AR-1,2,3,4），无重复、无遗漏
  - **Check 2 (Vocabulary legality)**: PASS — 全部 15 条 `revalidated`，在 5-value 词表内
  - **Check 3 (Conditional fields)**: PASS — 全部 `revalidated` 附非空 `revalidation_evidence`
  - **Check 4 (No silent P0/P1 downgrade)**: PASS — 全部 3 P0 + 8 P1 均 `revalidated`，零 `residual-risk` / `active/successor owner`
  - **Check 5 (Anti-Hollow spot-check, 3/3 verified against live source)**:
    - M7-2-P1-8: InputGate.java:378-399 `readSingleChannel` catch block (:390-398) calls `Thread.currentThread().interrupt()` (:396) + returns `Optional.empty()` (:397) — interrupt restored, not swallowed ✅
    - M7-2-P1-10: ResultPartition.java:316-329 `close()` uses blocking `queue.put(END_OF_STREAM)` (:320), NO `queue.clear()`; InterruptedException rethrown (:327) ✅
    - O7-2-AR-1: OperatorChain.java:244-250 `deepCopy()` calls `op.copyForSubtask()` per operator (:247); non-copyable throws UnsupportedOperationException ✅
  - **Check 6 (Header cross-tabs)**: PASS — disposition×severity (revalidated: 3 P0 + 8 P1 + 4 AR = 15) + disposition×domain (4 CEP + 8 coordinator/runtime + 3 connector = 15) 均一致
  - **Check 7 (Validator)**: PASS — `node ai-dev/tools/check-nop-stream-audit-manifest.mjs disposition --shard 20 --strict` exit 0 (73 disposition rows validated); `self-test` exit 0
  - **Deferred 项分类检查**: N/A — 本计划无 deferred 项；全部 15 条 in-scope finding 各有唯一终态裁决

Follow-up:

- no remaining plan-owned work
- Stage 23（文档契约与 production-readiness 判定，roadmap status `todo`）将消费本 shard 及 Stage 18/19 disposition 结果做最终 readiness 判定
