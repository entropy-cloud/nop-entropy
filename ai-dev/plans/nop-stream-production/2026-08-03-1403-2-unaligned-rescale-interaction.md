# 47 Unaligned Checkpoint + Rescale 交互 — Channel State Rescale 安全处理

> Plan Status: completed
> Last Reviewed: 2026-08-03
> Source: `ai-dev/backlog/nop-stream-production-roadmap.md` Stage 47; `ai-dev/design/nop-stream/checkpoint-design.md` §2.11（unaligned checkpoint 设计）; Stage 43 plan Deferred（`2026-08-03-0001-2-channel-heartbeat-unaligned-checkpoint.md:206-210` — "Unaligned + Rescale Interaction", successor Stage 47）; Stage 45 plan Non-Goals + Follow-up（`2026-08-03-0900-1-concurrent-checkpoint-multi-epoch.md:57,180,203` — "unaligned + rescale 叠加（Stage 47）", D4 unaligned 保持 single-in-flight successor）
> Mission: nop-stream-production
> Work Item: 47. Unaligned + rescale 交互
> Related: **Stage 43** (`2026-08-03-0001-2-channel-heartbeat-unaligned-checkpoint.md`, done — `ChannelState` + aligned→unaligned 回退 + replay); **Stage 35** (`2026-08-02-0955-5-keygroup-range-recovery.md`, done — keyed state rescale via `KeyGroupRangeRestoreFilter`); **Stage 45** (`2026-08-03-0900-1-concurrent-checkpoint-multi-epoch.md`, done — D4 unaligned 保持 single-in-flight, successor 本 Stage)

## Purpose

处理 unaligned checkpoint 与 rescale（并行度变化）的复杂叠加。当前存在一个**已确认的 live defect**：rescale 恢复路径（`GraphModelCheckpointExecutor.buildRescaledTaskState`）创建的是 plain `TaskStateSnapshot`（非 `TaskEpochSnapshot`），而 channel state 恢复钩子 `restoreChannelStateIfPresent` 的守卫是 `instanceof TaskEpochSnapshot`——rescale 时守卫失败，channel state **被静默丢弃**（plan guide #24 Silent No-Op violation），exactly-once 语义被破坏且无告警。

**关键语义事实**：restore 时 checkpoint 快照已经存在且已包含（或不包含）channel state。无法在 restore 时"降级为 aligned"——快照里已经有 channel state 了。因此首版策略是 **fail-fast 拒绝**：rescale 恢复路径检测到 channel state 存在时，抛出明确异常（`ERR_STREAM_CHANNEL_STATE_RESCALE_UNSUPPORTED`），拒绝恢复并提示用户使用 aligned checkpoint 做 rescale 恢复。完整的 in-flight data 跨并行度重映射（`InflightDataRescalingDescriptor`）属后续增强。

## Current Baseline

经 live 仓库核对（2026-08-03，独立子 agent 验证）：

- **`ChannelState`（Stage 43 引入）**：`nop-stream-core/.../checkpoint/ChannelState.java`（221 行），单字段 `Map<Integer, List<StreamElement>> recordsByChannel`（`TreeMap`，channelIndex → in-flight records）。无 rescale/redistribution 元数据——无 subtask 映射、无 parallelism 标注。
- **Keyed state rescale 已成立（Stage 35）**：`KeyGroupRangeRestoreFilter`（`nop-stream-core/.../common/state/shard/KeyGroupRangeRestoreFilter.java:41,70`）按 key-group range 过滤 keyed state；`targetKeyGroupRange` 在 Memory（`MemoryKeyedStateBackend.java:104`）和 RocksDB（`RocksDBKeyedStateBackend.java:157`）后端均有。channel state 无类比物。
- **【已确认 live defect】rescale 时 channel state 被静默丢弃**：
  - `GraphModelCheckpointExecutor.buildRescaledTaskState()`（`:1101-1148`）在 rescale 时创建 plain `TaskStateSnapshot merged = new TaskStateSnapshot(newLoc, -1)`（`:1108`），**不复制 channel state**。`TaskEpochSnapshot extends TaskStateSnapshot`（`TaskEpochSnapshot.java:23`），但 `buildRescaledTaskState` 创建的是父类 `TaskStateSnapshot`。
  - `restoreChannelStateIfPresent(invokable, taskState)`（`:1018-1029`）守卫为 `if (taskState instanceof TaskEpochSnapshot)`——rescale 时 `taskState` 是 plain `TaskStateSnapshot`，instanceof 失败，channel state **不被恢复**。
  - 非 rescale 路径（同并行度）：`stateLookup.lookup()` 返回原始 `TaskEpochSnapshot`（携带 `ChannelState`），channel state **被恢复**。
  - **影响**：如果用户在 unaligned checkpoint 后做 rescale 恢复，in-flight data 会静默丢失——exactly-once 语义被破坏且无告警。
- **`InputGate.switchToUnalignedAndEmit`**（`InputGate.java:463`）：Stage 45 在 `:464-471` 添加了 D4 fail-fast guard——unaligned + multi-in-flight 时抛异常（Stage 47 successor placeholder）。
- **无 `InflightDataRescalingDescriptor`**：grep 全仓零匹配。channel state 无任何跨并行度重映射机制。
- **Error code 文件**：`nop-stream-core/.../exceptions/NopStreamErrors.java`（无 `ERR_STREAM_CHANNEL_STATE_RESCALE_UNSUPPORTED`，需新增）。
- **Stage 43 Deferred**："Unaligned + Rescale Interaction" — `out-of-scope improvement`，successor = 本 Stage。
- **Stage 45 D4 裁定**："首版支持 aligned 多 in-flight，unaligned 保持 single-in-flight 限制"。D4 fail-fast guard（`InputGate.java:464-471`）为本 Stage 的 focused test 补充对象。

### 真正剩余的 gap

- **Channel state rescale 静默丢弃**（已确认 live defect）— in scope（必须修复为 fail-fast）。
- **Unaligned checkpoint + rescale 叠加语义未定义** — in scope（首版 fail-fast 拒绝 + design doc 定义交互语义）。
- **D4 unaligned + multi-in-flight**（Stage 45 deferred）— in scope（focused test 补充）。

## Goals

- **修复 live defect**：rescale 恢复路径不再静默丢弃 channel state——检测到 channel state 存在时 fail-fast 抛出 `ERR_STREAM_CHANNEL_STATE_RESCALE_UNSUPPORTED`，而非依赖 `instanceof TaskEpochSnapshot` 静默跳过。
- **定义交互语义**：在 `checkpoint-design.md` §2.11 新增 rescale 子章节，明确 unaligned checkpoint + rescale 的交互行为（首版 = fail-fast 拒绝；future = `InflightDataRescalingDescriptor` 重映射）。
- **D4 fail-fast guard focused test**：补全 Stage 45 留下的 `InputGate.java:464-471` unaligned+multi-in-flight guard 的直接断言测试。
- **端到端验证**：unaligned checkpoint 后 rescale 恢复时 fail-fast 正确触发（而非静默丢弃）；同并行度恢复正常工作（无回归）。

## Non-Goals

- 完整的 in-flight data 跨新并行度重映射实现（`InflightDataRescalingDescriptor` 完整算法）——首版 fail-fast 拒绝，完整重映射属后续增强。
- `CheckpointConfig` 新增配置项——首版 fail-fast 是无条件行为（rescale + channel state = 拒绝），不需要用户配置策略。
- unaligned multi-in-flight 完整支持（Stage 45 D4 successor beyond focused test 补充）。
- 非 channel state 的 rescale 问题（keyed state rescale 已在 Stage 35 done）。

## Scope

### In Scope

- 新增 `ERR_STREAM_CHANNEL_STATE_RESCALE_UNSUPPORTED` error code（`NopStreamErrors.java`）。
- 修复 `buildRescaledTaskState` / `restoreChannelStateIfPresent` 静默丢弃 channel state 的 live defect（→ fail-fast）。
- `checkpoint-design.md` §2.11 unaligned + rescale 交互小节。
- D4 fail-fast guard（`InputGate.java:464-471`）focused test 补充。
- E2E 验证：rescale + channel state → fail-fast；同并行度 + channel state → 正常恢复（无回归）。

### Out Of Scope

- `InflightDataRescalingDescriptor` 完整重映射算法实现。
- `CheckpointConfig` rescale 策略配置项（首版无条件 fail-fast）。
- unaligned multi-in-flight 完整支持。
- 非 unaligned 场景的 rescale（已由 Stage 35 覆盖）。

## Execution Plan

### Phase 1 — Channel state rescale 语义裁定（Decision）

Status: completed
Targets: `ai-dev/design/nop-stream/checkpoint-design.md`（§2.11 新增 rescale 子章节）

- Item Types: `Decision`

Phase 1 必须在编码前裁定 **2 个设计问题**（D3 为 future-work 设计探索，非阻塞决策）：

- [x] **(D1) rescale + channel state 首版策略**，裁定并记录到 `checkpoint-design.md` §2.11 新增子章节：
  - **语义事实**：restore 时 checkpoint 快照已存在且已包含 channel state（若 unaligned mode 拍摄）。无法在 restore 时"降级为 aligned"。
  - (A) **fail-fast 拒绝**（推荐首版）：rescale 恢复路径检测到 channel state 存在 → 抛 `ERR_STREAM_CHANNEL_STATE_RESCALE_UNSUPPORTED`，拒绝恢复。用户需使用 aligned checkpoint 做 rescale 恢复。最简、正确性有保证。
  - (B) **channel state 重映射**：实现 `InflightDataRescalingDescriptor` 按 key-group 重映射 in-flight records。复杂度高（channel state 的 records 可能没有 key），属后续增强。
  - (C) **静默丢弃 + warn 日志**：当前行为的显式化版本——**禁止**（plan guide #24 明确禁止静默跳过）。
  - 预期裁定：(A) fail-fast 拒绝为首版策略，(B) 留 successor。
- [x] **(D2) live defect 修复位置**：
  - 当前 `buildRescaledTaskState`（`:1108`）创建 plain `TaskStateSnapshot` → `restoreChannelStateIfPresent`（`:1020`）的 `instanceof TaskEpochSnapshot` 守卫静默跳过。
  - 裁定修复方式：(a) 在 `buildRescaledTaskState` 中显式检查源 snapshot 是否含 channel state，若有则 fail-fast；或 (b) 修正 `restoreChannelStateIfPresent` 守卫为显式检查 channel state 字段而非 instanceof（但 rescale 路径的 plain `TaskStateSnapshot` 本身不携带 channel state，需从源 `TaskEpochSnapshot` 检查）；或 (c) 在 rescale 检测点（`:975` `boolean rescale`）增加 channel state 检查。
- [x] **(D3) `InflightDataRescalingDescriptor` future-work 设计探索**（非阻塞，记录 open questions）：
  - 探索 channel state 的 in-flight records 如何按新并行度重新分配的语义方向（类似 keyed state 的 key-group 映射）。
  - 记录核心 open question：channel state 的 records 可能没有 key——如何决定分配到哪个新 subtask？可能的方案（round-robin / 附带 key-group 元数据 / 拒绝无 key records）列出但不裁定（首版不实现）。
  - 此项不产出"可直接消费的契约"，仅记录设计方向供未来 successor plan 参考。

Exit Criteria:

- [x] `checkpoint-design.md` §2.11 新增 rescale 子章节含 D1-D2 全部裁定 + D3 设计探索 + 拒绝的替代方案及原因。
- [x] D1 裁定明确首版策略（预期 fail-fast 拒绝）。
- [x] D2 裁定明确 live defect 修复位置（不依赖 instanceof 静默跳过）。
- [x] D3 记录了设计方向 + open questions（明确标注为 future-work 探索，非可消费契约）。
- [x] `ai-dev/logs/` 对应日期条目已更新。
- [x] No new test required: 纯决策 Phase，无代码变更（guide #25）。

### Phase 2 — Live defect 修复 + fail-fast 实现（Fix）

Status: completed
Targets: `nop-stream-core/.../exceptions/NopStreamErrors.java`（新增 error code）；`nop-stream-runtime/.../execution/GraphModelCheckpointExecutor.java`（`buildRescaledTaskState` / `restoreChannelStateIfPresent` / rescale 检测点）；`nop-stream-runtime` 测试

- Item Types: `Fix`

- [x] **新增 error code**：在 `NopStreamErrors.java` 新增 `ERR_STREAM_CHANNEL_STATE_RESCALE_UNSUPPORTED`（描述：rescale 恢复路径检测到 channel state 存在，无法跨并行度重映射 in-flight data）。
- [x] **修复静默丢弃 live defect**：依 Phase 1 D2 裁定的修复位置，使 rescale 恢复路径在源 checkpoint 含 channel state 时 fail-fast 抛出 `ERR_STREAM_CHANNEL_STATE_RESCALE_UNSUPPORTED`，不依赖 `instanceof TaskEpochSnapshot` 静默跳过。具体行为：rescale 检测（`GraphModelCheckpointExecutor.java:975` `boolean rescale`）为 true 时，检查源 `TaskEpochSnapshot.getChannelState()` 非空 → 抛异常。
- [x] **非 rescale 路径无回归**：同并行度恢复路径（`stateLookup.lookup()` 返回原始 `TaskEpochSnapshot`）行为不变，channel state 正常恢复。

Exit Criteria:

- [x] **行为验证**：存在测试证明 rescale 恢复路径在 channel state 存在时**不再静默丢弃**——fail-fast 抛 `ERR_STREAM_CHANNEL_STATE_RESCALE_UNSUPPORTED`。
- [x] **无静默跳过**（#24）：修复后的路径在 channel state rescale 不支持时显式失败（抛异常 with error code），不返回 null/空/静默 continue。
- [x] **新功能测试**（#25）：新增覆盖以下场景的 focused tests：
  - rescale + channel state 存在 → fail-fast 抛 `ERR_STREAM_CHANNEL_STATE_RESCALE_UNSUPPORTED`
  - rescale + 无 channel state（aligned checkpoint）→ 正常恢复（无回归）
  - 同并行度 + channel state → 正常恢复（无回归）
- [x] **Anti-Hollow**：fail-fast 路径在运行时确实被触发（非仅类型存在）。
- [x] `./mvnw test -pl nop-stream/nop-stream-runtime -am` 通过。
- [x] owner-doc：`checkpoint-design.md` §2.11 rescale 子节已更新（Phase 1 裁定 + Phase 2 实现）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 — D4 fail-fast guard focused test + 端到端验证（Proof）

Status: completed
Targets: `nop-stream-core` 测试（D4 guard）；`nop-stream-runtime` 测试（E2E）

- Item Types: `Proof`

- [x] **D4 fail-fast guard focused test**：为 `InputGate.java:464-471`（Stage 45 留下的 unaligned + multi-in-flight guard）补充直接断言测试——unaligned enabled + 2+ in-flight barriers → 抛 `ERR_STREAM_INVALID_STATE`（非静默行为）。
- [x] **端到端测试：unaligned checkpoint + 同并行度恢复**：unaligned checkpoint 后同并行度恢复，channel state 正确 replay，exactly-once 保持（无丢数/无重复）。此测试验证现有功能无回归。
- [x] **端到端测试：unaligned checkpoint + rescale 恢复**：unaligned checkpoint 后 rescale 恢复，验证 fail-fast 正确触发（抛 `ERR_STREAM_CHANNEL_STATE_RESCALE_UNSUPPORTED`），而非静默丢弃 channel state。
- [x] **端到端测试：aligned checkpoint + rescale 恢复**（无回归基线）：aligned checkpoint（无 channel state）后 rescale 恢复正常工作（Stage 35 keyed state rescale 不受影响）。

Exit Criteria:

- [x] **端到端验证**（#22）：从 `StreamExecutionEnvironment` 到 sink 的 unaligned checkpoint + rescale 恢复完整路径已验证（fail-fast 正确触发，exactly-once 不被静默破坏）。
- [x] **接线验证**（#23）：`buildRescaledTaskState` / rescale 检测点修复路径在运行时确实被调用（E2E 或 mock verify）。
- [x] **新功能测试**（#25）：显式列出每个新增 E2E / focused test 验证的行为。
- [x] D4 guard 有直接断言测试（Stage 45 Follow-up 收口）。
- [x] `./mvnw test -pl nop-stream -am -T 1C` 通过。
- [x] `checkpoint-design.md` §2.11 unaligned + rescale 交互小节为最终状态。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

- [x] Channel state rescale 静默丢弃 live defect 已修复（显式 fail-fast，非 instanceof 静默跳过）。
- [x] `ERR_STREAM_CHANNEL_STATE_RESCALE_UNSUPPORTED` error code 已新增并在 fail-fast 路径使用。
- [x] 首版 rescale + unaligned 策略已裁定并实现（fail-fast 拒绝）。
- [x] D4 fail-fast guard 有直接断言测试（Stage 45 Follow-up 收口）。
- [x] unaligned checkpoint + rescale 恢复端到端验证通过（fail-fast 正确触发，exactly-once 不被静默破坏）。
- [x] 不存在被静默降级到 deferred 的 in-scope live defect（channel state 静默丢弃已修复，非降级）。
- [x] 受影响 owner docs 已同步（`checkpoint-design.md` §2.11 rescale 子节）。
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据。
- [x] **Anti-Hollow Check**：closure audit 已验证（a）fail-fast 路径在运行时被调用（E2E 覆盖），（b）无 instanceof 静默跳过残留。
- [x] `./mvnw test -pl nop-stream -am -T 1C` 通过。
- [x] checkstyle / 代码规范检查通过。
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0。
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high` 退出码 0。

## Deferred But Adjudicated

### InflightDataRescalingDescriptor 完整重映射算法

- Classification: `optimization candidate`
- Why Not Blocking Closure: 首版 fail-fast 拒绝保证 rescale + unaligned 的 exactly-once 正确性（通过拒绝恢复而非静默丢弃）。完整 in-flight data 跨新并行度重映射是可用性优化（避免 rescale 时必须使用 aligned checkpoint），非正确性前置。Phase 1 D3 已记录设计方向 + open questions 供未来 successor plan 参考。
- Successor Required: yes
- Successor Path: 后续增强 plan（roadmap 未分配独立 Stage）

### Unaligned multi-in-flight 完整支持

- Classification: `optimization candidate`
- Why Not Blocking Closure: Stage 45 D4 裁定 unaligned 保持 single-in-flight，fail-fast guard 已存在（`InputGate.java:464-471`）。本 plan 补充 guard 的 focused test。完整 unaligned multi-in-flight 属后续增强。aligned multi-in-flight 已满足 `maxConcurrentCheckpoints > 1` 的核心价值；unaligned multi-in-flight 是并发性能优化，非正确性前置。
- Successor Required: no

## Non-Blocking Follow-ups

- `InflightDataRescalingDescriptor` 的无 key records 处理策略（Phase 1 D3 标注为 open question，future successor plan 裁定）。
- 未来可考虑在 trigger 时检测"可能需要 rescale"场景并提示用户使用 aligned mode（减少 restore 时 fail-fast 的用户 surprise）。

## Closure

Status Note: Channel state rescale 静默丢弃 live defect 已修复（rescale 检测点 fail-fast 抛 `ERR_STREAM_CHANNEL_STATE_RESCALE_UNSUPPORTED`，不依赖 `instanceof TaskEpochSnapshot` 静默跳过）。首版 unaligned + rescale 交互语义已裁定并实现（fail-fast 拒绝）。D4 unaligned+multi-in-flight guard 有直接断言测试（Stage 45 Follow-up 收口）。所有 in-scope 项已 landed，剩余项为 `optimization candidate`（`InflightDataRescalingDescriptor` 完整重映射、unaligned multi-in-flight 完整支持）。
Completed: 2026-08-03

Closure Audit Evidence:

- Reviewer / Agent: independent closure-audit subagent (session `ses_0399972f3ffeKLVlIecLM2OVcL`, general type, fresh session — not the implementation session).
- Evidence:
  - **Exit Criteria** (per Phase):
    - Phase 1: PASS — `checkpoint-design.md:390-439` §2.11.8 covers D1 (fail-fast chosen, rejected B/C table `:402-408`), D2 (fix-location option c table `:412-424`), D3 (future-work open questions marked "非可消费契约" `:426-439`).
    - Phase 2: PASS — `ERR_STREAM_CHANNEL_STATE_RESCALE_UNSUPPORTED` defined at `NopStreamErrors.java:294-300` (params vertexId/oldParallelism/newParallelism), imported `GraphModelCheckpointExecutor.java:60`, thrown at `:1136`. Rescale branch calls `assertNoChannelStateOnRescale` at `:989` BEFORE per-subtask loop / `buildRescaledTaskState` / `restoreChannelStateIfPresent`. `TestChannelStateRescaleFailFast` (7 tests) covers channel-state-present→throw, partial→throw, empty/null/plain→OK.
    - Phase 3: PASS — `TestInputGateUnalignedFallback.testUnalignedMultiInFlightFailsFastD4Guard` (`:253-283`) asserts `nop.err.stream.invalid-state`. `TestChannelStateRescaleE2E` (3 tests via `executeWithSavepoint`): unaligned+rescale→fail-fast, aligned+rescale→OK, unaligned+same-p→OK.
  - **Closure Gates**: all 13 PASS — verified against live code (error code exists & used `:60,1136`; live defect fixed `:989`; no instanceof silent skip on rescale path; wiring confirmed `:1126-1142` called at `:989`; Anti-Hollow E2E drives full `executeWithSavepoint`→restore→check path; deferred items honestly `optimization candidate`).
  - **Anti-Hollow Check**: PASS — (a) `unalignedCheckpointThenRescale_failsFast` E2E proves the fail-fast fires at runtime through the real restore path (`assertThrows` + error code assertion), (b) no instanceof silent skip remains on the rescale path (the rescale branch fails fast upstream; `restoreChannelStateIfPresent`'s instanceof guard now only serves the non-rescale path).
  - **`node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict`**: exit code 0 (post-closure).
  - **`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high`**: exit code 0 (no blocking high-severity hollow findings from this plan's changes; pre-existing findings all use correct `UnsupportedOperationException` fail-fast pattern).
  - **`./mvnw test -pl nop-stream -am -T 1C`**: 727 tests, 0 failures, 0 errors.
  - **Deferred classification honesty**: PASS — the only in-scope live defect (silent channel-state drop) was FIXED, not downgraded. `InflightDataRescalingDescriptor` + unaligned multi-in-flight are `optimization candidate` with explicit `Why Not Blocking Closure`.

Follow-up:

- `InflightDataRescalingDescriptor` 无 key records 处理策略（Phase 1 D3 标注为 open question，future successor plan 裁定）。
- 未来可考虑在 trigger 时检测"可能需要 rescale"场景并提示用户使用 aligned mode（减少 restore 时 fail-fast 的用户 surprise）。
- no remaining plan-owned work.
