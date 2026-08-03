# 1 Concurrent Checkpoint 完整支持 — Task 级多 epoch barrier 追踪（Stage 45, G31 续）

> Plan Status: completed
> Last Reviewed: 2026-08-03
> Source: `ai-dev/backlog/nop-stream-production-roadmap.md` Stage 45（行 627-637）; Stage 19 plan Deferred（`2026-07-25-2300-1-checkpoint-concurrency.md` 行 147-152, 211-214）; `ai-dev/design/nop-stream/checkpoint-design.md` §2.8（行 208-223）、§13.2（行 1191-1203）、§13.2.1（行 1205-1211）
> Mission: nop-stream-production
> Work Item: 45. 多并发 checkpoint 完整支持（G31 续）
> Related: `2026-07-25-2300-1-checkpoint-concurrency.md`（Stage 19，Coordinator 层已收口）; `2026-08-03-0001-2-channel-heartbeat-unaligned-checkpoint.md`（Stage 43，unaligned checkpoint 单 in-flight）

## Purpose

把 `maxConcurrentCheckpoints > 1` 从「Coordinator 层可配置但 task 层强制单 in-flight」推进到「端到端真正可用」：task 侧能同时追踪多个 in-flight barrier，对齐、ACK、abort 互不干扰。收口 G31 的 task 级剩余工作面（Stage 19 显式 handoff 给 Stage 45）。

## Current Baseline

经 live repo 核对（2026-08-03）：

- **Coordinator 层多 epoch 已成立**（Stage 19，done）：`CheckpointCoordinator` 用 `ConcurrentHashMap<Long, PendingCheckpoint>` 按 epoch 追踪多个 pending；gating（`maxConcurrentCheckpoints`）+ minPause（last-completed）+ 每个 pending 独立 ACK/timeout/abort/cleanup。证据：`nop-stream-runtime/.../checkpoint/CheckpointCoordinator.java:154, 370-422, 424-452`；`TestCheckpointCoexistenceViaCoordinator`（5 tests，多 pending 共存/独立完成/独立 abort/独立 timeout/独立 storage failure）。
- **Task 侧强制单 in-flight（本 plan 工作面）**：
  - `CheckpointBarrierTracker`（`nop-stream-core/.../execution/CheckpointBarrierTracker.java:52-54, 90-93`）只有单一 `currentCheckpointId`/`operatorsToAck`；第二个 trigger 直接 `return false`。
  - `InputGate.handleBarrierNonRecursive()`（`nop-stream-core/.../execution/InputGate.java:79-84, 529-571`）只有单一 `pendingBarrier`；不同 id 的 barrier 到达时 **抛** `ERR_STREAM_CHECKPOINT_ABORTED`（`:562-567`）。
- **`BarrierAligner` 已删除**（`ai-dev/logs/2026/07-26.md:560`，零生产调用者）。**roadmap 行 634 与 completion-roadmap 行 268 仍引用 `BarrierAligner`，属 stale 文本**——本 plan 不以它为目标，仅 target `CheckpointBarrierTracker` + `InputGate`。
- **abort 不是 epoch 精准的**：distributed abort（`nop-stream-runtime/.../coordinator/JobCoordinator.java:1235-1265`）与 local abort（`GraphModelCheckpointExecutor.registerLocalAbortHandler`，`:728-758`）都对所有 assigned task 扫描式 `cancelTask`/`signalCancel`/`task.cancel()`，`IStreamTaskRpcService.cancelTask(jobId, vertexId, subtaskIndex)` **无 epoch 参数**。Coordinator 侧 `abortPendingCheckpoint`（`CheckpointCoordinator.java:813-847`）本身是 epoch 精准的；损失精准性的环节在 task 侧 RPC 传播。这意味着多 epoch 在途时 abort N 会误杀 N+1/N+2 的 barrier。
- **unaligned checkpoint（Stage 43）是单 in-flight 且正交**：`InputGate.switchToUnalignedAndEmit`（`:443`）与 `ChannelState`（`:110`）假设单 in-flight。本 plan 需让多 epoch aligned 与 Stage 43 unaligned 共存（决策点见下）。
- **设计文档显式 forward-looking 契约**：`checkpoint-design.md:216` 明确「Task 层未满足的硬约束…由 Stage 45 满足」；§13.2 行 1201「并发能力一致」不变量标注 task 层/对齐器层 ❌ 仍单 barrier，属 Stage 45；§13.2.1 行 1211 明确「若 Stage 43/Stage 45 出现真实 in-data-flow cancel marker 消费方，则在该 stage plan 重新裁定」。
- **G33（SharedStateRegistry）不是本 plan 范围**：Stage 19 已将其移交 Stage 31（已 done，live `CheckpointCoordinator.java:145, 1199-1210`）。

## Goals

- task 侧（`CheckpointBarrierTracker` + `InputGate`）能同时追踪 ≥ `maxConcurrentCheckpoints` 个 in-flight barrier，对齐与 ACK 按 epoch 独立，互不污染。
- 多 epoch 下的 abort 是 epoch 精准的：abort epoch N 不影响在途的 N+1/N+2。
- `maxConcurrentCheckpoints=3` 端到端互不干扰有可观测测试（trigger → barrier 对齐 → ACK → complete 全链路，3 个 epoch 同时在途）。
- 多 epoch aligned 路径与 Stage 43 unaligned 路径的交互有明确裁定与测试。

## Non-Goals

- G33 `SharedStateRegistry`（已在 Stage 31 done）。
- unaligned + rescale 叠加交互（属 Stage 47）。
- 把 `CancelCheckpointMarker` 作为主 abort 机制（§13.2.1 裁定为 Decision-only；主 abort 仍为控制通道）。本 plan 仅在「abort 精准化」需要时重新裁定是否引入。
- Coordinator 层多 pending 逻辑（Stage 19 已 done，不重做）。
- 自动选择最优 `maxConcurrentCheckpoints`（配置项，非本 plan）。

## Scope

### In Scope

- `InputGate` 单 `pendingBarrier` → 多 epoch 对齐状态机（或等价机制）。
- `CheckpointBarrierTracker` 单 `currentCheckpointId` → 多 epoch ACK 追踪。
- abort 精准化：让 abort epoch N 不误杀其它在途 epoch（设计决策见 Phase 1）。
- `maxConcurrent=3` 端到端测试 + 现有 single-in-flight 断言测试的修正（`TestCheckpointBarrierTrackerConcurrency.testOverlappingTriggerReturnsFalse` 等）。
- unaligned 与多 epoch 的交互裁定 + 共存测试。
- stale roadmap/doc 文本修正（`BarrierAligner` 引用）。
- `checkpoint-design.md` §2.8/§13.2 forward-looking 标注转为「已满足」。

### Out Of Scope

- Stage 47（unaligned + rescale）。
- Stage 43 的 unaligned 实现本身（已 done，仅消费其契约）。
- 跨 JVM 多 epoch E2E（依赖 Stage 42 基建，可作为附带增强但不阻塞 closure）。

## Execution Plan

### Phase 1 - 多 epoch 设计裁定（Decision）

Status: completed
Targets: `ai-dev/design/nop-stream/checkpoint-design.md` §2.8/§2.11.1/§13.2/§13.2.1; 设计裁定记录

- Item Types: `Decision`

Phase 1 必须在编码前裁定 **4 个设计问题**，否则执行者只能自行发明算法：

- [x] **(D1) 多 barrier 对齐状态机模型**：在 pipelined streaming 中，同一 channel 上的 barrier 严格有序（N 先于 N+1 到达），但 Coordinator 可在 N 未完成 ACK 时注入 N+1。裁定并记录到 `checkpoint-design.md` 新增子章节：
  - per-channel per-barrier-id 进度追踪模型；N+1 只能在已交付 N 的 channel 上到达；某 channel 对 N 的 laggard 不阻塞 N+1 在其它 channel 开始对齐。
  - channel blocking/resume 按 barrier 边界管理：channel 交付 barrier N 后 block 在 N 之后（消费暂停）直到 N 全 channel 对齐完成；unblock 后 N+1 可自然流入。
  - 多 in-flight 时 aligned→unaligned 回退规则（Stage 43 的 `switchToUnalignedAndEmit` 如何与多 epoch 共存）。
- [x] **(D2) ACK 路由 checkpointId 传播**：`CheckpointBarrierTracker.acknowledgeOperator(int, OperatorSnapshotResult)`（`:128`，**42 处调用点**含生产代码 `StreamTaskInvokable.java:331`）当前不携带 checkpointId，靠单一 `currentCheckpointId` 隐式路由。多 epoch 下必须显式路由。裁定并记录：
  - (a) 给 `acknowledgeOperator` 加 `long checkpointId` 参数（42 处 call-site 需更新）；或
  - (b) 在 snapshot 回调闭包中捕获 checkpointId（由 `triggerCheckpoint` 时绑定），不改 `acknowledgeOperator` 签名。评估两种方案的回归范围。
- [x] **(D3) abort 精准化路径**，三选一并记录到 §13.2.1：
  - (A) 扩展 `cancelTask` RPC 携带 epoch 参数——**注意：此选项触及跨模块公共 API（`IStreamTaskRpcService`），属 AGENTS.md Protected Area `plan-first`。若选此方案，Phase 4 实施前需独立 plan-first 升级**；
  - (B) 复活 `CancelCheckpointMarker` 作为 in-data-flow 精准 abort 信号（§13.2.1 留给本 stage 的裁定点）；
  - (C) task 侧维护 epoch→abort-state 查询，local handler 按 epoch 过滤（最小侵入，不改 RPC 契约）。
- [x] **(D4) aligned vs unaligned 多 epoch 首版方向**：从流处理理论论证首版范围。aligned 多 epoch 因 channel blocking 序列化，并发收益主要来自「Coordinator 在 N ACK/snapshot 期间注入 N+1」而非真正 barrier 并行；unaligned 多 epoch 才释放真正并发。裁定首版范围（建议：首版支持 aligned 多 in-flight，unaligned 保持 single-in-flight 限制，记录 unaligned 多 in-flight 作 successor）。

Exit Criteria:

- [x] `checkpoint-design.md` 新增/更新 §2.8 子章节写明 D1-D4 全部裁定 + 拒绝的替代方案及原因（design doc 只记录最终决策，不写代码层签名，见 guide 第 14 条）
- [x] D2 裁定包含 call-site 回归范围评估（42 处中生产代码 vs 测试代码分类）
- [x] D3 若选 (A)，plan 内显式标注「Phase 4 需 plan-first 升级（Protected Area）」
- [x] `ai-dev/logs/` 对应日期条目已更新
- [x] No new test required: 纯决策 Phase，无代码变更（guide #25）

### Phase 2 - InputGate 多 epoch barrier 对齐（Fix）

Status: completed
Targets: `nop-stream-core/.../execution/InputGate.java`; `nop-stream-core` 测试

- Item Types: `Fix`

- [x] 将 `InputGate` 单一 `pendingBarrier` 对齐逻辑改造为可同时容纳多个 in-flight barrier id 的对齐机制，**依 Phase 1 D1 裁定的状态机模型**：per-channel per-barrier-id 进度追踪；不同 id 的 barrier 不再互斥抛异常；channel blocking/resume 按 barrier 边界管理。
- [x] 保留 aligned→unaligned 回退（Stage 43）在多 epoch 下的正确性（依 Phase 1 D1/D4 裁定的组合策略）。

Exit Criteria:

- [x] **行为验证**：存在测试证明两个不同 id 的 barrier 可同时在途对齐，且任一完成对齐不影响另一个的对齐状态
- [x] **无静默跳过**（guide #24）：任何暂未支持的多 epoch 分支抛出明确异常而非静默 `continue`/吞异常
- [x] **新功能测试**（guide #25）：新增覆盖「多 barrier 并存对齐」「单 barrier 完成不影响其它」「aligned/unaligned 共存（依裁定）」的 focused tests，列明每个测试验证的行为
- [x] `./mvnw test -pl nop-stream/nop-stream-core -am` 通过
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - CheckpointBarrierTracker 多 epoch ACK 追踪（Fix）

Status: completed
Targets: `nop-stream-core/.../execution/CheckpointBarrierTracker.java`; `nop-stream-core` 测试

- Item Types: `Fix`

- [x] 将 `CheckpointBarrierTracker` 单一 `currentCheckpointId`/`operatorsToAck`/`currentSnapshot` 改造为按 epoch 独立追踪的 ACK 集合（每个 in-flight epoch 拥有独立的 ack 计数与 snapshot）。
- [x] **ACK 路由 checkpointId 传播**：依 Phase 1 D2 裁定实现——无论选 (a) 加参数还是 (b) 闭包捕获，必须使 `acknowledgeOperator` 的 snapshot 路由到正确的 epoch 条目。更新全部受影响 call-site（42 处，含生产代码 `StreamTaskInvokable.java:331`）。
- [x] `triggerCheckpoint` 不再因「有一个在途」直接拒绝；改为按 epoch 注册新追踪项（受 Coordinator 层 `maxConcurrentCheckpoints` 已有的 gating 约束）。
- [x] 审查 `StreamSourceOperator.offerBarrier`（`:114-129`）的多 barrier 契约：当前 javadoc 说 overlap 由 tracker guard，移除 guard 后 source 是否支持并发 in-flight barrier 排队（经 mailbox）；若不支持需补保护或更新契约。
- [x] `notifyCheckpointAborted` 按 Phase 1 D3 裁定精准清理对应 epoch，不影响其它在途 epoch。

Exit Criteria:

- [x] **行为验证**：存在测试证明 ≥2 个 epoch 同时 trigger 均被接受、各自独立 ACK 完成互不污染
- [x] 修正 `TestCheckpointBarrierTrackerConcurrency.testOverlappingTriggerReturnsFalse`（`:19`）等断言 single-in-flight 的测试：改为断言多 epoch 行为（不得删除真实保护，见 guide bug-fix 规则）
- [x] **接线验证**（guide #23）：`CheckpointBarrierTracker` 的新多 epoch 路径在 task 运行时确实被 `InputGate` 对齐完成回调触发（端到端或计数器/标志位断言）——Phase 2 与 Phase 3 强耦合，建议在同一落地周期内验证集成
- [x] **新功能测试**（guide #25）：列明每个新增测试验证的多 epoch ACK 行为
- [x] `./mvnw test -pl nop-stream/nop-stream-core -am` 通过
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - abort 精准化实现 + 端到端验证（Fix + Proof）

Status: completed
Targets: 依 Phase 1 裁定的落点（控制面 RPC / marker / task 侧过滤）；`nop-stream-runtime` 测试

- Item Types: `Fix | Proof`

- [x] 按 Phase 1 选型实现 epoch 精准 abort（替换当前 sweep-all 的 `cancelTask`/local handler 行为）。
- [x] `maxConcurrentCheckpoints=3` 端到端测试：3 个 epoch 同时在途，各自独立完成；中途 abort 其中 1 个，其余 2 个不受影响继续完成。

Exit Criteria:

- [x] **端到端验证**（guide #22）：存在一条从 `triggerCheckpoint` 经 task 侧 barrier 对齐/ACK 到 `completePendingCheckpoint` 的完整路径测试，且 `maxConcurrent=3` 时 3 个 epoch 互不干扰
- [x] **abort 精准性验证**：存在测试证明 abort epoch N 时，在途的 N±1 epoch 不被误杀（可观测：未被 abort 的 epoch 仍能 ACK 完成）
- [x] **接线验证**（guide #23）：精准 abort 路径在运行时确实被 coordinator→task 调用（非空壳）
- [x] **新功能测试**（guide #25）：显式列出 abort 精准化新增测试
- [x] `./mvnw test -pl nop-stream -am -T 1C` 通过
- [x] `checkpoint-design.md` §13.2「并发能力一致」不变量与 §13.2.1 精准 abort 裁定更新为「已满足（Stage 45）」
- [x] stale 文本修正：roadmap 行 634 / completion-roadmap 行 268 删除 `BarrierAligner` 引用（BarrierAligner 已删除）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] task 侧多 epoch barrier 追踪端到端可用（`maxConcurrent=3` 互不干扰 E2E 通过）
- [x] abort 在多 epoch 在途时是 epoch 精准的（测试证明）
- [x] 多 epoch aligned 与 Stage 43 unaligned 的组合策略已裁定并有测试
- [x] Coordinator 层（Stage 19）多 pending 逻辑零回归（现有 `TestCheckpointCoexistenceViaCoordinator` 等全绿）
- [x] 不存在被静默降级到 deferred 的 in-scope 缺口
- [x] 受影响 owner docs（`checkpoint-design.md` §2.8/§2.11.1/§13.2/§13.2.1；roadmap/completion-roadmap stale 引用）已同步
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）`InputGate`/`CheckpointBarrierTracker` 多 epoch 路径在运行时被调用（端到端测试覆盖），（b）无空方法体/静默跳过/no-op
- [x] `./mvnw test -pl nop-stream -am -T 1C` 通过
- [x] checkstyle / 代码规范检查通过
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high` 退出码 0

## Deferred But Adjudicated

### 跨 JVM 多 epoch E2E（多 TaskManager JVM 下 maxConcurrent=3）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 本 plan 的多 epoch 正确性可在单 JVM embedded/本地 task 路径完整验证（task 侧重构与跨 JVM 无关；Coordinator 多 pending 已在 Stage 19 经 coordinator 路径覆盖）。跨 JVM 编排属 Stage 42 基建；如本 plan 期间可低成本附带验证则纳入，否则作为 Stage 42/后续增强。
- Successor Required: `no`（可选附带；否则 watch-only）

## Non-Blocking Follow-ups

- 多 epoch 下 async persist 重叠验证（Stage 18 留下的交互复核：cp N 持久化在途时 cp N+1 触发并独立完成）——可顺带在 Phase 4 E2E 中观察，不单列硬门禁。
- unaligned + rescale 叠加（Stage 47）。

## Closure

Status Note: Stage 45 把 task 层从「单 in-flight」推进到「aligned 多 in-flight 端到端可用」。CheckpointBarrierTracker 按 epoch 独立追踪 ACK（D2 option b：OperatorSnapshotResult 携带 checkpointId 路由，42 处 call-site 零签名变更）；InputGate per-barrier 对齐状态机（D1：aligned 序列化对齐 + aborted straggler 丢弃）；abort epoch 精准（D3 option C：local handler epoch 感知，仅当无在途 epoch 才 cancel）；unaligned 保持 single-in-flight（D4，successor Stage 47）。maxConcurrent=3 E2E + abort 精准性经独立 closure audit 验证全 PASS。
Completed: 2026-08-03

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（task `ses_03aceda8dffeqq6YpFnTVT6JwR`，read-only verification against live repo）
- Audit Session: ses_03aceda8dffeqq6YpFnTVT6JwR
- Evidence:
  - Phase 1 Exit Criteria: PASS — `checkpoint-design.md` §2.8.1 D1-D4 裁定 + 拒绝替代方案；D2 含 42 处 call-site 回归范围评估；D3 选 (C) 非 (A)（无 plan-first 升级需求）
  - Phase 2 Exit Criteria: PASS — `InputGate.java` per-barrier 对齐（`L89` LinkedHashMap）；handleBarrierNonRecursive 不抛 overlapping（`L561-619`）；abortBarrierAlignment（`L678-688`）；`TestInputGateMultiEpochBarrier` 3 测试
  - Phase 3 Exit Criteria: PASS — `CheckpointBarrierTracker.java` per-epoch EpochAckState（`L66, L354-364`）；triggerCheckpoint 接受重叠（`L111-141`）；acknowledgeOperator 按 checkpointId 路由（`L150-162`，valid ≥ 0，-1 回退）；notifyCheckpointAborted 单 epoch（`L304-310`）；`TestCheckpointBarrierTrackerConcurrency` 多 epoch 测试
  - Phase 4 Exit Criteria: PASS — `GraphModelCheckpointExecutor.registerLocalAbortHandler` epoch 精准（`L728-776`，条件 cancel + abortBarrierAlignment）；`TestMultiEpochCheckpointE2E` maxConcurrent=3 E2E + abort 中间 epoch（coordinator.abortPendingCheckpoint → setAbortHandler 接线）
  - Closure Gates: 全部 PASS（见上）
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
  - Anti-Hollow 检查：多 epoch 路径经 E2E 测试覆盖（tracker→coordinator→future.get）；无空方法体；早返回均配 LOG.debug/warn（`CheckpointBarrierTracker.java:171,177`）；`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high` 退出码 0（high 发现均为历史 UnsupportedOperationException，非本次新增）
  - Deferred 项分类检查：跨 JVM 多 epoch E2E 明确为 out-of-scope improvement（Stage 42 基建），非 in-scope live defect 降级

Follow-up:

- D4 unaligned+multi fail-fast 防护分支（`InputGate.java:464-471`）为防御性 guard，未单独直接断言（aligned 序列化使其在正常路径不可达）；建议作为 Stage 47（unaligned+rescale）的 focused test 补充
- distributed 路径 epoch 精准 RPC（option A）留作 successor（需独立 plan-first 升级 Protected Area `IStreamTaskRpcService`）
- 无其它 remaining plan-owned work
