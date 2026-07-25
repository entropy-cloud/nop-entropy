# Checkpoint 并发与共享状态基础（G31, G33, P2）

> Plan Status: active
> Last Reviewed: 2026-07-25
> Draft Review: 2 轮独立子 agent 对抗性审查通过（ses_0666a54c3ffePjlUEv8jeHvTrk + ses_066616634ffeBee2MXTSDO627B）；F1–F5 Major 全 PASS，R1 Major（失败计数器污染）已修，Minors R2–R6 已清。
> Source: `ai-dev/backlog/nop-stream-production-roadmap.md` Stage 19；`ai-dev/analysis/nop-stream/08-gap-analysis.md` G31(`:99`)/G33(`:101`)（03-checkpoint: #10/`162/169`、#12/`163/171`）；`ai-dev/analysis/nop-stream/03-checkpoint-comparison.md` 行 158/162/163
> Mission: nop-stream-production
> Work Item: 19
> Related: Plan `2026-07-25-2200-1-async-snapshot-pipeline`（Stage 18，async persist 前置，仍为 draft）；`ai-dev/design/nop-stream/checkpoint-design.md` §2.x/§配置表/§不变量；Plan `2026-07-25-1500-1-mailbox-execution-model`（Stage 17，已完成，single-task 执行模型基础）

## Purpose

把 nop-stream 的 checkpoint 并发能力从「Coordinator 配置形同虚设 + minPause 完全未接线 + 文档/警告与 live 行为矛盾」推进到「Coordinator 真正尊重 `maxConcurrentCheckpoints` 与 `minPause`，多个 pending checkpoint 可安全共存，文档与 live 行为一致」。收口 G31 的「基础支持」语义（完整多 epoch task 级追踪属 Stage 45），并裁定 G33（SharedStateRegistry）的去留：经 live 核对，G33 在内存全量快照后端下无可消费的共享状态，唯一 load-bearing 消费者是 Stage 31 的 RocksDB 增量 SST 共享，故显式延后至 Stage 31，不在本 plan 建造空壳抽象。

## Current Baseline

> 全部为 live repo 核对结果（`CheckpointCoordinator.java` 583 行全文 + `CheckpointConfig.java` + grep 验证）。

- **G31 被引用的硬编码 bug 已不存在**：`03-checkpoint-comparison.md:162/169` 与 `08-gap-analysis.md:99` 引用的 `CheckpointCoordinator.java:196` 处 `int effectiveMaxConcurrent = Math.min(1, config.getMaxConcurrentCheckpoints())` 已被移除。live 代码（`CheckpointCoordinator.java:194-196`）现为 `int effectiveMaxConcurrent = config.getMaxConcurrentCheckpoints(); if (numPendingCheckpoints.get() >= effectiveMaxConcurrent)` —— 配置值被直接尊重，无 clamp。
- **但构造器警告与 live 行为矛盾（stale）**：`CheckpointCoordinator.java:91-95` 仍输出 `"maxConcurrentCheckpoints={} is configured ... but the current implementation only supports concurrent=1. Checkpoints will be limited to maxConcurrent=1."` —— 该警告是**事实性错误**：代码已不再降级到 1。配置 >1 时该警告会误导运维以为并发未生效。
- **设计文档 stale**：`checkpoint-design.md:183`「CheckpointCoordinator 强制 maxConcurrentCheckpoints=1（配置 >1 会被警告并降级到 1）……配置 >1 不会崩溃，但不会生效」与 `:874`「maxConcurrentCheckpoints | 1 | 当前实现强制为 1，配置 >1 会被降级」均与 live 行为冲突（代码已尊重配置）。`:965` 不变量「配置的 maxConcurrentCheckpoints 必须 Coordinator/task/对齐器各层一致，不得配置允许但实现拒绝」是 **forward-looking 契约**（非对现状的描述）：Coordinator 层将由本 plan 满足，task 层（单 barrier 对齐）仍属 Stage 45——保留该不变量并补注各层状态，而非删除。
- **注：§2.8 / line 183 与 Stage 18 plan 重叠**：Stage 18 plan（`2026-07-25-2200-1`）已声明会先把 line 183 临时改为「warn-only」表述。本 plan 的最终表述（Coordinator 尊重配置值 + task 层 Stage 45）以本 plan 为准：若 Stage 18 已先落地，本 Phase 覆盖为最终表述；若并行，避免互相 clobber。
- **`minPause` 完全未接线**：`CheckpointConfig.minPause`（默认 500ms，`CheckpointConfig.java:35/83-89/206-208`）存在 getter/setter/builder，但 `rg "minPause|getMinPause"` 在 `CheckpointCoordinator` 与 `GraphModelCheckpointExecutor` 内**零命中**。触发调度为纯 `scheduleAtFixedRate(interval, interval)`（`CheckpointCoordinator.java:144-169`），触发路径（`:194-222`）不检查距上次触发的间隔。→ 即使解禁并发，checkpoint 会按 interval 固定速率堆积，无 minPause 节流。
- **task 层仅支持单 checkpoint 对齐**：`checkpoint-design.md:183` 记录「CheckpointBarrierTracker 和 InputGate 也只支持单 checkpoint 对齐」。需区分两件事：(1) **Coordinator 端多 pending 共存**——只要不 ACK cp1，`tryTriggerPendingCheckpoint` 第二次调用即在 sync persist 下也自然产生第 2 个 pending（已被现有测试证明，见下条）；(2) **task 端单 barrier 流转**——任务侧一次只处理一个 barrier，这是 Stage 45 的重构面。async persist（Stage 18）只是生产场景下 pending 自然重叠的**另一个触发源**（cp N 持久化在途时触发 cp N+1），并非 Coordinator 多 pending 共存的前提。→ minPause / stale 修正 / Coordinator 多 pending 安全独立于 Stage 18 可测；async 重叠验证是 Stage 18 落地后的集成补充。
- **并发安全现状（多为已具备）**：`tryTriggerPendingCheckpoint`/`acknowledgeTask`/`completePendingCheckpoint`/`abortPendingCheckpoint` 均 `synchronized`（`:194/224/242/319`）；每个 `PendingCheckpoint` 在构造时获得独立的 ack 集合快照（`getTasksToAcknowledge()` `:396-398` 返回 `new HashSet`，传入 `PendingCheckpoint` 构造 `:205/211-213`）；`scheduleTimeout` 为每个 pending 独立调度（`:420-430`）。→ 多 pending 并存时 ack 集合不串扰，但**无任何测试验证共存中的 ≥2 pending 经 coordinator 路径独立 ACK/complete/abort 不串扰**（见下条 gap 定位）。
- **现有并发测试采样不全、且含 hollow 模式**：
  - `TestCheckpointCoordinator.testMaxConcurrentCheckpointsRespectsConfig`（`:130-145`）用 `maxConcurrentCheckpoints(3)` **经 `coordinator.tryTriggerPendingCheckpoint` 连续触发 3 个 pending 且均不 ACK**——三者真实同时在 coordinator 的 `pendingCheckpoints` map 中共存（numPending=3），第 4 个被正确拒绝。→ **gating 维度的多 pending 共存已被覆盖**。
  - `TestCheckpointLifecycleIsolation`（用 `(2)`）与 `TestCheckpointSuccessMapCleanup`（用 `(3)`）确为顺序（cp1 全 ACK 完成才触发 cp2）。
  - `TestCheckpointConcurrencySafety`（3 个测试）：`testConcurrentStartCheckpointScheduler_noDuplicateScheduler` 测并发启动调度器；`testConcurrentAcknowledgeTask_noCorruption` 用 **`new PendingCheckpoint(...)` 直接构造 + 手动 `forceComplete()` 完全绕过 `CheckpointCoordinator`**（`:91-108`）——这是 hollow 模式，不经过 `tryTriggerPendingCheckpoint`/`completePendingCheckpoint`/`pendingCheckpoints` map/`numPending`/`scheduleTimeout`/`cleanupOldCheckpoints`，无法证明 coordinator 端多 pending 安全。
- **G31 真实 gap（重新定位）**：多 pending **共存（gating）已被覆盖**；真实未覆盖的是「**共存中的 ≥2 pending 各自经 coordinator 路径独立 ACK/complete/abort/timeout 不串扰**」——现有测试要么只测 gating（不 ACK），要么用 hollow 绕过。本 plan Phase 2 补齐这一面。
- **G33 完全缺失**：`rg "SharedStateRegistry"` 在 nop-stream source = 0 命中（仅 `03-checkpoint-comparison.md` 与 `comparison.md` 对比 Flink）。`CompletedCheckpoint.taskStates` 为每 checkpoint 独立 `byte[]` 全量拷贝（`completePendingCheckpoint` `:256`、`MemoryOperatorStateBackend.snapshotState` `new HashMap`），无任何跨 checkpoint 共享语义。→ 内存后端下不存在可引用计数的共享状态。

## Goals

- **G31 基础支持落地**：Coordinator 真正尊重 `maxConcurrentCheckpoints`（去 stale 警告）与 `minPause`（接线节流），且 ≥2 个 pending checkpoint 共存时各自经 coordinator 路径独立 ACK/complete/abort/timeout 不串扰，由 focused test 证明（区别于现有只测 gating 或 hollow 绕过的测试）。
- **文档/警告与 live 对齐**：构造器警告与 `checkpoint-design.md:183/874` stale 表述修正为 live 行为，`:965` forward-looking 不变量补注各层状态（Coordinator 由本 plan 满足、task 层属 Stage 45），并显式标注 task 级多 epoch 追踪属 Stage 45。
- **G33 裁定诚实记录**：G33 不在本 plan 建造无消费者的空壳抽象；显式延后至 Stage 31 并写清 non-blocking 理由。

## Non-Goals

- task 级多 epoch barrier 追踪（`CheckpointBarrierTracker`/`InputGate` 重构为同时追踪多个 in-flight checkpoint）——属 Stage 45。
- unaligned checkpoint 下的多并发——属 Stage 45/47。
- SharedStateRegistry 实际引用计数实现 / 增量 SST 共享——属 Stage 31（内存全量后端无共享状态可消费）。
- async persist 框架本身——属 Stage 18（本 plan 仅在 Phase 2 注明与 Stage 18 的集成点）。
- backpressure gating（Flink `CheckpointRequestDecider` 的 backpressure 维度）——vision 未要求，minPause + maxConcurrent 已覆盖节流语义。
- RocksDB 状态后端——属 Stage 30。

## Scope

### In Scope

- `minPause`(last-completed) 在触发路径接线：上一个 checkpoint 完成后强制 `minPause` 间隔（节流，防堆积）。
- 失败计数器（`consecutiveTriggerFailures`）不被 minPause 节流 / numPending 拒绝误计为失败。
- 移除 `CheckpointCoordinator` 构造器 stale 警告（`:91-95`），替换为准确的行为描述。
- Coordinator 共存 pending 经 coordinator 路径独立 ACK/complete/abort/timeout 不串扰的验证 + focused test（区别于现有 gating-only / hollow 测试）。
- `checkpoint-design.md:183/874` stale 表述修正 + `:965` forward-looking 不变量补注各层状态（非删除）+ `08-gap-analysis.md:99` G31 状态更新。
- G33 裁定记录（Deferred But Adjudicated）。

### Out Of Scope

- task 层（CheckpointBarrierTracker / InputGate）多 epoch 重构（Stage 45）。
- SharedStateRegistry 实现（Stage 31）。
- async persist 框架（Stage 18）。
- unaligned checkpoint（Stage 43）。

## Execution Plan

### Phase 1 - minPause 接线 + stale 警告/文档修正

Status: planned
Targets: `nop-stream-runtime/.../checkpoint/CheckpointCoordinator.java:91-95,144-169,194-222`；`nop-stream-core/.../checkpoint/CheckpointConfig.java`（minPause，已有）；`ai-dev/design/nop-stream/checkpoint-design.md:183,874,965`；`ai-dev/analysis/nop-stream/08-gap-analysis.md:99`（G31 行）

- Item Types: `Fix | Decision | Proof`

- [ ] 在触发路径接线 `minPause`，锚点语义定为 **last-completed**（对齐 `checkpoint-design.md:873`「两次 checkpoint 之间的最小间隔」与 Flink `minPauseBetweenCheckpoints`）：即「上一个 checkpoint **完成**（complete）后须经过 ≥ `config.getMinPause()` 才允许触发下一个」。首次触发（无前序完成）不受限。若上一个 checkpoint 尚未完成，由 `maxConcurrentCheckpoints` gating 决定（不在此 item 重复）。
- [ ] 调度模型适配：当前 `scheduleAtFixedRate(interval, interval)`（`:144-169`）固定速率触发，与 last-completed minPause 节流冲突（minPause 计时起点是完成时刻，非固定速率）。倾向在 `tryTriggerPendingCheckpoint`（`:194-222`）内做 minPause 门控：命中时记 DEBUG/WARN + 返回 null，与 numPending 上限拒绝**可区分**（不同日志/metric）；调度器保持 fixed-rate 但每次调用受 trigger 内门控过滤。若实现期证明该方向有正确性障碍，改为自重调度并在代码注释记录理由。
- [ ] 移除构造器 stale 警告（`:91-95`），替换为与 live 行为一致的描述（Coordinator 尊重配置值；task 级多 epoch 追踪尚未支持，见 Stage 45）。
- [ ] **无静默跳过**：minPause 节流命中时不得静默 `continue` 吞掉；必须有可观测行为（日志 DEBUG/WARN + 返回 null），调用方可区分「因 minPause 节流」与「因 numPending 上限拒绝」。
- [ ] **失败计数器不被节流/拒绝污染**：当前 scheduler loop（`:147-156`）对**任何** `result == null` 都 `consecutiveTriggerFailures.incrementAndGet()`，到阈值 3 误报 ERROR「3 consecutive failures」。minPause 节流与 numPending 拒绝都返回 null → 会被误计为失败。须改造使「节流/拒绝」（正常背压，非失败）**不** inflate `consecutiveTriggerFailures`：仅「真失败」（如无 task 可 ACK、异常）才计数。实现方式可选（如 `tryTriggerPendingCheckpoint` 返回/暴露拒绝原因，或 scheduler 侧区分），但语义上节流/拒绝不得触发失败计数与 ERROR 日志。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] `minPause` 被 `CheckpointCoordinator` 触发路径实际检查（repo-observable：grep `getMinPause` 在 `CheckpointCoordinator` 有 ≥1 命中；且门控逻辑在 `tryTriggerPendingCheckpoint` 内基于「上次完成时间 + minPause」判定）。
- [ ] 新增 focused test 验证 **last-completed minPause 语义**：完成 cp1 后立即触发 cp2（间隔 < minPause）应被节流（返回 null）；等待 ≥ minPause 后再触发 cp2 应成功。断言「minPause 节流拒绝」与「numPending 上限拒绝」可区分（不同日志/metric/返回原因）。测试须用默认或 ≥100ms 的 `minPause`，禁止缩小到接近调度抖动量级（防 flaky）。
- [ ] **失败计数器回归测试**：在「minPause 节流」与「numPending 拒绝」连续多次发生时，`getConsecutiveTriggerFailures()` **不**自增、不触发 ERROR「consecutive failures」（断言计数保持 0）；只有真失败才计数。
- [ ] 构造器 stale 警告（原 `:91-95`）已删除/改写，不再声称「降级到 1 / 不会生效」。
- [ ] `checkpoint-design.md:183/874` stale 表述修正为「Coordinator 尊重配置值；task 级多 epoch 追踪为 Stage 45」；`:965` forward-looking 不变量**保留**并补注「Coordinator 层已满足；task 层单 barrier 属 Stage 45」。
- [ ] `08-gap-analysis.md:99`（G31 行）更新：标注「`Math.min(1,...)` 硬编码已移除；minPause(last-completed) 已接线；共存 pending gating 已测（`testMaxConcurrentCheckpointsRespectsConfig`）；共存 pending 独立 ACK/complete 见 Stage 19 Phase 2；task 级多 epoch 见 Stage 45」。
- [ ] **无静默跳过**：minPause 节流路径有断言覆盖，验证命中时返回 null + 日志，而非返回静默默认值。
- [ ] owner-doc 更新：`checkpoint-design.md` §配置表 + §并发约束段落已同步 live 行为。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - Coordinator 共存 pending 独立 ACK/complete/abort（G31 basic 补齐）

Status: planned
Targets: `CheckpointCoordinator.java:224-317,319-353,420-450`（acknowledge/complete/abort/timeout/cleanup）；新增 focused test（经 coordinator 路径的共存 pending 独立流转）

- Item Types: `Fix | Proof`

- [ ] 验证并（必要时）加固共存 ≥2 pending 经 coordinator 路径独立流转的正确性：`completePendingCheckpoint`/`abortPendingCheckpoint`/`scheduleTimeout`/`cleanupOldCheckpoints` 在多 pending 下不串扰、不误删、numPending 计数准确。基于 Current Baseline 已记录的 `synchronized` + 独立 ack 快照现状，逐路径核对而非盲改。此为**补齐现有测试缺口**（gating 共存已由 `testMaxConcurrentCheckpointsRespectsConfig` 覆盖，独立流转未覆盖），而非从零创建共存能力。
- [ ] 新增 focused test（**必须经 coordinator 路径**）：用 `coordinator.tryTriggerPendingCheckpoint` 触发 cp1 **不 ACK** → 再触发 cp2（二者真实共存于 `pendingCheckpoints` map，复用 `testMaxConcurrentCheckpointsRespectsConfig` 的构造方式）→ 对 cp1、cp2 分别 `coordinator.acknowledgeTask` 至 complete，断言两者各自经 `completePendingCheckpoint` 独立到达完成态、`getLatestCheckpoint` 各自正确、taskStates 内容不串扰（cp1 的 state 不出现在 cp2，反之亦然）、`numPending` 最终归零。**禁止**用 `new PendingCheckpoint(...)` + `forceComplete()` 绕过 coordinator（这是 `TestCheckpointConcurrencySafety.testConcurrentAcknowledgeTask_noCorruption` 的 hollow 模式，本 test 不得复制）。
- [ ] 覆盖 abort 场景：cp1 在途时 `coordinator.abortPendingCheckpoint(cp1)`，断言 cp2 不受影响、仍可独立 ACK/complete；numPending 正确归位。
- [ ] 覆盖 timeout 场景：cp1 在途时让其 `scheduleTimeout` 到期触发 abort，断言 cp2 不被误 abort、仍独立完成。
- [ ] 记录与 Stage 18 async persist 的集成点：本 Phase 在 sync 路径上完成共存 pending 独立流转证明（不依赖 async persist）。async 重叠场景（cp N 持久化在途时 cp N+1 已触发并独立完成）作为 Stage 18 落地后的集成验证项，记入 Non-Blocking Follow-ups。
- [ ] **无静默跳过**：多 pending 下任一 pending 的 complete/abort 失败必须传播，不得因「另一个 pending 在处理」而 catch-and-log 吞掉。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 新增 focused test 通过且**经 coordinator 路径**：用 `tryTriggerPendingCheckpoint` 构造 2 pending（不 ACK cp1），对二者分别 `acknowledgeTask` 至 complete，taskStates 各自正确不串扰（断言 cp1 state ∉ cp2，反之亦然），numPending 最终归零。
- [ ] abort 场景测试通过：cp1 在途 abort，cp2 不受影响独立完成（断言 cp2 达完成态、numPending 最终归零）。
- [ ] timeout 场景测试通过：cp1 超时 abort 不误伤 cp2（断言 cp2 仍独立完成）。
- [ ] **Anti-Hollow**：测试通过 `coordinator.tryTriggerPendingCheckpoint`/`acknowledgeTask`/`completePendingCheckpoint` 真实路径，**未**用 `new PendingCheckpoint`+`forceComplete()` 绕过（审查 test 源码确认无 `new PendingCheckpoint` 直接构造 coordinator-managed pending）。
- [ ] **端到端验证**：从 `tryTriggerPendingCheckpoint` 入口到 `completePendingCheckpoint` 写入 storage + `getLatestCheckpoint` 的多 pending 路径完整跑通；两个 pending 各自端到端独立完成。
- [ ] **接线验证**：多 pending 下 `scheduleTimeout`/`cleanupOldCheckpoints` 确实按 checkpointId 独立作用（断言 cp1 的 timeout 不误 abort cp2；cleanup 不误删仍在途的 pending）。
- [ ] **无静默跳过**：多 pending 下 complete/abort 失败传播有断言覆盖（不吞异常）。
- [ ] numPending 计数在多 pending complete/abort/timeout 后准确归零（断言 `getNumberOfPendingCheckpoints() == 0`）。
- [ ] owner-doc 更新：`checkpoint-design.md` 记录「Coordinator 端共存 pending 独立流转已落地（gating + 独立 ACK/complete/abort/timeout）；task 级多 epoch 追踪 = Stage 45；async persist 重叠验证依赖 Stage 18」。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

> **关闭条件**：所有条目及每个 Phase 的 Exit Criteria 全部 `[x]` 后才能 `completed`。

- [ ] G31 基础支持收敛：`minPause`(last-completed) 接线 + stale 警告/文档修正 + Coordinator 共存 pending 经 coordinator 路径独立 ACK/complete/abort/timeout 不串扰，由 focused test 证明（区别于现有 gating-only / hollow 测试）。
- [ ] 不存在被静默降级到 deferred 的 in-scope live defect（G33 已诚实裁定为 optimization candidate 并附 roadmap scope-change 同步，见 Deferred）。
- [ ] 受影响 owner docs（`checkpoint-design.md:183/874` stale 修正、`:965` 不变量补注、`08-gap-analysis.md:99` G31 行）已同步 live baseline。
- [ ] G33 roadmap scope-change 已同步（Stage 19 deliverable 移除 G33 → Stage 31，见 Non-Blocking Follow-ups），无 roadmap owner-doc drift。
- [ ] 必要 focused verification（minPause last-completed 节流断言、共存 pending 独立 ACK/complete/abort/timeout 断言、numPending 计数断言、Anti-Hollow 经 coordinator 路径断言）已完成。
- [ ] 独立子 agent closure-audit 已完成并记录证据。
- [ ] **Anti-Hollow Check**：closure audit 验证（a）minPause 节流在运行时确实被触发路径执行（非仅字段存在），（b）共存 pending 独立流转测试**经 coordinator 路径**（`tryTriggerPendingCheckpoint`/`acknowledgeTask`/`completePendingCheckpoint`），非 `new PendingCheckpoint`+`forceComplete()` hollow 绕过，（c）无空方法体/静默跳过。
- [ ] `./mvnw test -pl nop-stream -am -T 1C` 通过。
- [ ] checkstyle / 代码规范检查通过。

## Deferred But Adjudicated

### G33 — SharedStateRegistry 引用计数

- Classification: `optimization candidate`
- Why Not Blocking Closure: 经 live 核对（`CompletedCheckpoint.taskStates` 为每 checkpoint 独立 `byte[]` 全量拷贝，`MemoryOperatorStateBackend.snapshotState` `new HashMap`），内存全量后端下**不存在跨 checkpoint 共享状态**，故无任何可引用计数的共享对象。SharedStateRegistry 的唯一 load-bearing 消费者是 Stage 31 的 RocksDB 增量 SST 内容寻址共享。G31「基础并发支持」的正确性不依赖共享状态引用计数（各 pending 全量拷贝独立留存）。在本 plan 引入 registry 抽象而无真实消费者，将构成 plan 指南所禁止的空壳抽象（Minimum Rules #22/#24 + 历史 lesson 8）。故延后至有真实消费者的 Stage 31。
- Successor Required: `yes`
- Successor Path: Stage 31（增量 checkpoint / RocksDB SST 共享，roadmap 行 31）

### task 级多 epoch barrier 追踪（CheckpointBarrierTracker / InputGate 同时追踪多个 in-flight checkpoint）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: roadmap 明确将「完整多 epoch 追踪」划归 Stage 45；本 plan 的 G31 范围为「Coordinator 端基础支持」，task 级重构是独立工作面且依赖 unaligned checkpoint（Stage 43）。Coordinator 侧共存 pending 独立流转在 Phase 2 已覆盖（经 coordinator 路径，不依赖 task 级多 epoch）；task 侧单 barrier 流转不阻塞 Coordinator 多 pending 正确性。
- Successor Required: `yes`
- Successor Path: Stage 45（多并发 checkpoint 完整支持，roadmap 行 45）

## Non-Blocking Follow-ups

- **roadmap scope-change 同步（必须，防 owner-doc drift）**：本 plan 将 G33（SharedStateRegistry 引用计数）从 roadmap Stage 19 deliverable 移交 Stage 31。plan 关闭前须更新 `ai-dev/backlog/nop-stream-production-roadmap.md`：Stage 19 deliverable 行移除 G33；Stage 31 deliverable 行**确认已含** SharedStateRegistry（live `:449` Stage 31 已写「SST 文件内容寻址 + `SharedStateRegistry` 引用计数」，故动作是「确认归属」而非重复补入）；在 Work Items 的 Stage 19/31 说明处记录本次 scope change（引用本 plan 的 Deferred 裁定理由）。Stage 18 plan（`2026-07-25-2200-1`）Deferred 段「G33 → Stage 19」的 successor 指针随 roadmap 更新自动失效（以 roadmap 为权威源，无需回写 Stage 18 plan）。
- async persist 重叠场景的完整验证（cp N 持久化在途时 cp N+1 触发并独立完成）：依赖 Stage 18 落地后补；若 Stage 18 已落地，Phase 2 即覆盖；否则记录为 Stage 18 的集成验证项。
- `cleanupOldCheckpoints` 当前为 bulk cleanup（`getAllCheckpoints` 全量读 + 逐个 delete），多 pending 高频完成下可评估改为精确 subsume（参考 Flink `addCheckpointAndSubsumeOldestOne`）——非正确性问题，性能优化，可由 Stage 31 共享状态清理一并评估。
- minPause 锚点已落定为 last-completed（Phase 1）；与 async persist（DURABLE 时机，影响「完成时刻」定义）的交互复核，留作 Stage 18 集成时的检查点。

## Closure

Status Note: <<完成时填写>>
Completed: <<YYYY-MM-DD>>

Closure Audit Evidence:

- Reviewer / Agent: <<独立子 agent>>
- Audit Session: <<session ID>>
- Evidence:
  - 每条 Exit Criterion 验证结果（PASS/FAIL + live code path / test name）
  - 每条 Closure Gate 验证结果
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
  - `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high` 退出码 0
  - Deferred 项分类检查：确认 G33（optimization candidate）与 task 多 epoch（out-of-scope）均非 in-scope live defect 被降级

Follow-up:

- G33 SharedStateRegistry → Stage 31
- task 级多 epoch barrier 追踪 → Stage 45
- async persist 重叠验证 → Stage 18 落地后补
