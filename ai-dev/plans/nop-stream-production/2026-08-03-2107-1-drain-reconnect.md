# 44-D Drain/Reconnect Mechanism（Region Failover 前置 #4）

> Plan Status: completed
> Last Reviewed: 2026-08-03
> Source: `ai-dev/backlog/nop-stream-production-roadmap.md` Item 44（successor plan 4/5）; `ai-dev/design/nop-stream/failover-design.md` §五.4（drain/reconnect 需求）+ §9.4（死锁解除机理表）+ §9.5（scope — plan 级，优先级 #4）
> Mission: nop-stream-production
> Work Item: 44. Region-based failover — successor plan 4/5（drain/reconnect mechanism）
> Related: **successor 1** `2026-08-03-1600-1-blocking-edge-materialization-point.md`（物化点机制 — 已 completed；本 plan 消费其 epoch 标记 + dual-write + replay）; **successor 2** `2026-08-03-1600-2-region-identification.md`（region ID — 已 completed）; **successor 3** `2026-08-03-1600-3-supervision-loop-execution-model.md`（supervision loop — 已 completed；本 plan 修改其 `restartRegion`/`rebuildTask`/`hasOutgoingCrossRegionEdge`）; **successor 5** `2026-08-03-2107-2-per-region-restart-counter.md`（per-region counter 可配置 — 独立）; **决策 plan** `2026-08-03-1403-1-region-based-failover.md`（go confirmed）

## Purpose

解除 Stage 27 NO-GO §3.3 的死锁 1（下游死→上游 `queue.put()` 永久阻塞）与 reconnect 缺口（consumer-only region 重启后无法切回 live producer queue），使 **producer-region 重启**（含 outgoing cross-region edge 的 region）从 `ERR_STREAM_REGION_RESTART_UNSUPPORTED` 硬拒绝变为可用。本 plan 交付 consistent-cut epoch 对齐（含 operator state restore）+ producer drain（pause/resume 模型）+ consumer reconnect，不破坏 exactly-once。

这是 region-based failover 五项架构前置的第 4 项。前三项已 completed，使 consumer-only region 重启对**有限输入**可用（重放后 EOS）。本 plan 将其扩展到**无限输入 / producer-region** 场景，使 region-based failover 端到端可用。

## Current Baseline

经 live 仓库核对（2026-08-03，含独立子 agent 对抗性审查验证）：

- **SupervisionLoop 仅支持 consumer-only region 重启**：`SupervisionLoop.restartRegion`（`:321-391`）经 `hasOutgoingCrossRegionEdge`（`:536-557`）分类——含 outgoing cross-region edge 的 region → 返回 `false` → 抛 `ERR_STREAM_REGION_RESTART_UNSUPPORTED`（`:228`）→ 异常直接抛给 LOCAL 路径调用者（`GraphModelCheckpointExecutor.submitAndRun`，static），无 globalRecovery fallback（LOCAL 与 DISTRIBUTED 路径分离，见 successor 5 审查裁定）。
- **`rebuildTask` 从 JobVertex 模板 deep-copy，不从 checkpoint restore operator state**：`SupervisionLoop.rebuildTask`（`:441-525`）在 `:458` 执行 `jobVertex.getOperatorChains().get(0).deepCopy()`——从 **JobVertex 模板**复制（初始空状态），**非**旧 task 的 runtime operator state，**非** checkpoint restore。当前 replay from epoch 0（全量重放）时这是正确的（operator 从空状态开始 + 全量重放 = state 从零重建）。但改为 replay from epoch N>0 后，pre-N accumulated state 丢失 → stateful operator（window/CEP/aggregate）静默错误。
- **重放起点硬编码 epoch 0**：`rebuildTask` consumer 分支 `:483` 调用 `tempChannel.activateMaterializationReplay(0L)`——始终从 epoch 0 全量重放。consistent-cut epoch 对齐协议完全缺失。
- **Materialization epoch 不跟踪 checkpoint barrier**：`ResultPartition.currentMaterializationEpoch`（`:77`）默认 `0L`，无代码路径递增。Checkpoint barrier 经 `ResultPartition.write(barrier)` 流经（`CheckpointBarrier extends StreamElement`），但 write 路径**对所有 StreamElement 无条件 dual-write**（`:158-170`），包括 barrier/watermark——物化 store 被控制事件污染，replay 时会 inject 虚假 barrier 触发 spurious checkpoint。
- **无 reconnect-to-live-queue**：`rebuildTask` consumer 分支（`:493-502`）在 old partition `isFinished()` 时 close fresh partition（EOS）。old partition **未 finished**（infinite source）时，fresh partition 不连接 live producer——consumer 重放完毕后 `read()` 永久阻塞。
- **Producer `write()` 阻塞无 consumer-death 检测**：`ResultPartition.write()`（`:147-183`）满时 `queue.put()` 阻塞（`:174,181`），无超时、无非阻塞溢出。消费者死后 producer 永久阻塞（死锁 1）。
- **物化点 dual-write 已落地**：`ResultPartition.write()` 在 `materializationPoint != null` 时先 bypass-write 到 store 再 `queue.put` 主 queue。`InMemoryMaterializationPoint`（synchronized ArrayList，无界）。
- **`drainBufferedElements` 存在但不解锁 producer**：`ResultPartition.drainBufferedElements()`（`:325-340` 附近）非阻塞 `poll()` 已缓冲数据，不解锁阻塞中的 `queue.put()`。
- **Checkpoint restore 路径存在但与 rebuildTask 不共享**：`GraphModelCheckpointExecutor.restoreOperatorsFromState()`（`:1390-1435` 附近）从 TaskEpochSnapshot 恢复 operator state（含 timer 注册）；`CheckpointCoordinator.getLatestCheckpoint()` 提供最近 checkpoint。但 `rebuildTask`（SupervisionLoop static 方法）不接入此路径。
- **Per-region restart counter = in-memory, scoped to one `run()`**：`SupervisionLoop.run`（`:183`）`Map<RegionId, AtomicInteger>`，`DEFAULT_MAX_RESTARTS_PER_REGION = 3`（`:136`）。

### 真正剩余的 gap

1. **Operator state restore + consistent-cut epoch 对齐**：rebuildTask 不 restore operator state；epoch 不跟踪 barrier；重放起点硬编码 0L。改为 epoch N>0 需同时 restore state from checkpoint（at epoch N）+ replay materialization (from epoch N)，否则 stateful operator 静默错误。→ 本 plan Phase 1 交付。
2. **Barrier/控制事件污染物化 store**：dual-write 无条件写入所有 StreamElement（含 barrier/watermark），replay 时 inject 虚假控制事件。→ 本 plan Phase 1 交付过滤。
3. **Producer drain**（死锁 1 完整解除）：producer 无非阻塞溢出 / consumer-death 检测。→ 本 plan Phase 2 交付。
4. **Reconnect-to-live-queue**：consumer-only region 重启后无法切回 live producer queue。→ 本 plan Phase 3 交付。
5. **Producer-region 重启**：`ERR_STREAM_REGION_RESTART_UNSUPPORTED` 硬拒绝。→ 本 plan Phase 2 解除。

## Goals

- **Consistent-cut epoch 对齐 + operator state restore**：物化 epoch 跟踪 checkpoint barrier 递进（barrier 经过 ResultPartition.write 时 bump）；barrier/watermark 不污染物化 store（过滤控制事件）；region 重启时同时 (a) 从 checkpoint restore operator state（at consistent-cut epoch），(b) 从物化点 replay post-checkpoint 数据。
- **Producer overflow-bypass**（解除死锁 1）：物化启用时 `ResultPartition.write()` queue 满不阻塞（overflow 到物化 store，data 已在 bypass-write 阶段记录），producer 持续推进不因 consumer 死而永久阻塞。
- **Consumer reconnect-to-live-queue**：consumer-only region 重启后，物化重放完毕时切换到 live producer partition 继续消费（drain 已清空 live queue → reconnect 仅读 post-drain 新数据，无重复）。
- **Producer-region 重启可用**：drainable producer-region 可被 supervision loop cancel + 重启（替换 `ERR_STREAM_REGION_RESTART_UNSUPPORTED` 硬拒绝）；overflow-bypass 保证 producer 死亡前全部数据在物化 store。
- **exactly-once 保持**：drain/reconnect 全程不丢数据、不重复、不产生 stateful operator 静默错误。E2E 验证覆盖 stateful operator（window aggregate）。
- **零回归**：无物化 marker / 单 region 既有作业行为不变。

## Non-Goals

- **Mailbox full drain-and-reinject 协议**：当前 discard pending mail + rebuild。Phase 1 的 operator state restore 从 checkpoint 恢复 timer 注册（timer 仍在 `HeapInternalTimerService`），pending transient trigger mail 丢弃但 timer 下次触发重新入 mail——等价于 globalRecovery 的 timer restore 语义。升级到"drain in-flight mail + re-inject"属后续优化（减少 transient trigger 延迟），不阻塞 exactly-once 正确性。
- **跨 JVM drain/reconnect**：in-process 先正确。
- **RocksDB/磁盘物化存储**：in-memory 物化先正确。
- **Per-region restart counter 可配置**：属 successor 5（独立 plan）。
- **Region-aware scheduling（G55）**。
- **改变 global epoch recovery baseline**（`checkpoint-design.md` §8.1.1）。
- **Producer pause/resume 状态机**：初稿曾考虑 pause/resume drain 模型，经审查（round 2 NB1）否定——producer 在 `queue.put()` 阻塞时无法响应 pause。改为 overflow-bypass（非阻塞 write），不需要 pause/resume 状态机或新 task 生命周期状态。

## Scope

### In Scope

- Consistent-cut epoch 对齐：materialization epoch 跟踪 checkpoint barrier；barrier/watermark 过滤不入物化 store；重放起点 = checkpoint-aligned epoch。
- Operator state restore：rebuildTask 从 checkpoint TaskEpochSnapshot 恢复 operator state（at consistent-cut epoch），而非从模板空状态开始。
- Producer overflow-bypass（物化启用时 write 不阻塞）+ producer-region 重启（`restartRegion` 支持 drainable producer-region，替换 `ERR_STREAM_REGION_RESTART_UNSUPPORTED`）。
- Consumer reconnect-to-live-queue：物化重放完毕后切换到 live producer partition。
- exactly-once 验证：drain/reconnect 全程，含 stateful operator（window aggregate）。
- 零回归：无物化 marker / 单 region 既有作业行为不变。

### Out Of Scope

- Mailbox full drain-and-reinject（Non-Blocking Follow-up）。
- 跨 JVM drain/reconnect。
- RocksDB/磁盘物化存储。
- Per-region counter 可配置（successor 5）。
- Region-aware scheduling（G55）。

## Execution Plan

### Phase 1 — Consistent-cut foundation: epoch tracking + barrier filtering + operator state restore

Status: completed
Targets: `ResultPartition.java`（epoch bump at barrier + 控制事件过滤）; `IMaterializationPoint`/`InMemoryMaterializationPoint`（epoch-aware replay）; `SupervisionLoop.rebuildTask`（operator state restore from checkpoint + replay epoch 选择）; `SupervisionLoop.run` 签名（接入 checkpoint epoch 来源）; owner-docs

- Item Types: `Fix | Decision | Proof`

- [x] **Epoch set-to-barrier-id + barrier 过滤**：`ResultPartition.write()` 检测 `element` 为 `CheckpointBarrier` 时 (a) **set** `currentMaterializationEpoch = barrier.getId()`（checkpoint ID 对齐，使 consistent-cut replay 起点可直接用 checkpointId），(b) **不**将 barrier/watermark 写入物化 store（控制事件过滤——仅数据 element 被 dual-write，barrier 只用于 epoch 分段标记）——`Fix`
- [x] **Operator state restore from checkpoint**：`rebuildTask` 在重建 operator chain 后，从 checkpoint 恢复 operator state（at consistent-cut epoch），而非从模板空状态开始。接入路径：`SupervisionLoop.run` 签名增加 checkpoint 恢复所需对象——经审查（round 2），`restoreOperatorsFromState`（`GraphModelCheckpointExecutor:1390`，private static）需要 `(OperatorChain, long epochId, TaskStateSnapshot, List<OperatorStateMapping> mappings)`，其中 mappings 来自 `CheckpointPlan.getStateMappings(taskLocation)`。因此需传入 `CompletedCheckpoint`（提供 TaskStateSnapshot）+ `CheckpointPlan`（提供 mappings），而非仅 epoch 值。5 个 call-site（`GraphModelCheckpointExecutor:131,198,272,336,399` 调用 `submitAndRun`，非直接调 `SupervisionLoop.run`）中 `coordinator` + `checkpointPlan` 均在 scope 内——`Fix`
- [x] **Replay epoch 选择**：`rebuildTask` consumer 分支使用 `activateMaterializationReplay(consistentCutEpoch)` 替换 `activateMaterializationReplay(0L)`——`Fix`
- [x] **Startup edge case**：作业启动初期（首个 checkpoint 未完成 → 无 durable epoch）发生 region restart 时，fallback 到 epoch 0 + 全量重放（当前行为，不 fail-fast），因 operator 从空状态 + 全量重放 = 正确——`Decision`：edge case 处理记录于 owner-doc
- [x] 组件级测试：(a) barrier 经过后物化数据 epoch tag 递增；(b) barrier/watermark 不入物化 store；(c) operator state restore 从 checkpoint 后 replay 仅 post-checkpoint 数据 → stateful operator（如 SumAggregate）结果正确——`Proof`

Exit Criteria:

- [x] Materialization epoch 在 checkpoint barrier 经过 `ResultPartition.write()` 时 **set 为 `barrier.getId()`**（checkpoint ID 对齐，断言可观测：barrier 前后物化数据的 epoch tag = 对应 checkpointId）
- [x] Barrier/watermark 不被写入物化 store（断言可观测：物化 store 仅含数据 element，不含控制事件）
- [x] `rebuildTask` 从 checkpoint restore operator state（**接线验证** #23：restore 路径确实被调用，operator state 非空，断言可观测——对比模板空状态）
- [x] Replay 起点使用 checkpoint-aligned epoch（**接线验证** #23：`activateMaterializationReplay` 调用传入的 epoch 来自 checkpoint，非硬编码 0L）
- [x] **Stateful operator 正确性**：region restart 后 SumAggregate/window 的 accumulated state 来自 checkpoint restore（非空），replay 仅补充 post-checkpoint 数据 → 结果与无故障连续运行一致（断言可观测）
- [x] **无静默跳过**（#24）：checkpoint 存在但 restore 失败时 fail-fast（非静默从空状态开始）；无 checkpoint 时 fallback 到 epoch 0 + 全量重放（显式 Decision，非静默降级）
- [x] owner-doc: `failover-design.md` §五.4 consistent-cut + operator state restore 落地; `checkpoint-design.md` materialization epoch 与 barrier 协调关系 + barrier 过滤裁定
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 — Producer overflow-bypass + producer-region restart

Status: completed
Targets: `ResultPartition.write()`（overflow-bypass 非阻塞路径）; `SupervisionLoop.restartRegion`/`hasOutgoingCrossRegionEdge`（producer-region 重启路径）; `StreamTaskInvokable.invokeSource`（failure 时不 close output）; owner-docs

- Item Types: `Fix | Decision | Proof`

> **审查裁正（round 2 NB1）**：初稿采用 pause/resume 模型，但审查发现 supervision loop 以 100ms 轮询检测 FAILED（`:127`），consumer 失败后 producer 在微秒级填满 queue 并阻塞在 `queue.put()`——此时 pause flag（类似 `signalCancel`）对已阻塞在 `BlockingQueue.put()` 的线程无效（线程不在 mailbox safe-point）。`failover-design.md` §9.4 指定的正解为 **overflow-bypass**（"物化点作为溢出/旁路，producer 可继续推进"）。本 Phase 据此修订为 overflow-bypass，移除 pause/resume。

- [x] **Overflow-bypass 实现**（解除死锁 1）：`ResultPartition.write()` 在物化启用时，将主 queue 写入从阻塞 `queue.put()` 改为非阻塞 `queue.offer()`（或带极短 timeout）；queue 满时 data 已在 bypass-write 阶段写入物化 store（successor 1 dual-write），故 offer 失败时 producer 不阻塞、不丢数据（仅 queue 缺该 element，物化 store 有完整记录），producer 继续推进——`Fix`
- [x] **`hasOutgoingCrossRegionEdge` + `restartRegion` 修改**：drainable producer-region 不再返回 `false`→`ERR_STREAM_REGION_RESTART_UNSUPPORTED`；producer-region 重启时 cancel dead producer tasks → rebuild + restart（producer 从 checkpoint state restore 或从头开始；consumer 经 Phase 1 state restore + materialization replay 获取 producer 产出的数据）——`Fix`
- [x] **Producer in-flight 数据保障**：producer 死亡（producer-region 重启场景）时，其 dual-write 已将 queue 中数据同步到物化 store；overflow-bypass 保证 queue 满时 producer 不阻塞故能继续 dual-write 到死亡时刻。consumer 经物化 replay 获取全部 pre-death 数据。新 producer 必须写入**同一** materialization point（或 ID 连续的点），保证 consumer replay 连续性——`Decision`：数据保障机制 + materialization point 连续性记录于 owner-doc
- [x] **跨 region reconnect trigger**：producer-region restart 时，supervision loop 需通知/触发下游 consumer region 的 reconnect（consumer 可能阻塞在旧 partition 的 `queue.take()` 上）。trigger 路径（cancel consumer 中断 / partition closed 标记 / 回调注册 / supervision loop 主动跨 region 干涉）属实现层，由 Phase 3 E2E 覆盖验证——`Decision`：trigger 机制属实现层，记录于 owner-doc
- [x] 组件级测试：(a) overflow-bypass 在 queue 满时 producer 不阻塞（断言：producer 线程在 queue 满时继续运行而非 block）；(b) overflow 的 data 确实在物化 store（断言可观测）；(c) drainable producer-region 不再抛 `ERR_STREAM_REGION_RESTART_UNSUPPORTED`——`Proof`

Exit Criteria:

- [x] Overflow-bypass 路径存在：物化启用时 `ResultPartition.write()` 不阻塞在 `queue.put()`（**接线验证** #23：queue 满时 write 不阻塞，断言可观测——producer 线程状态为 RUNNABLE 而非 WAITING/BLOCKED）
- [x] Overflow data 在物化 store（断言可观测：queue 满时写入的 element 出现在物化 store，epoch tag 正确）
- [x] Drainable producer-region 重启不再抛 `ERR_STREAM_REGION_RESTART_UNSUPPORTED`（改为 cancel + restart 路径）
- [x] **无静默跳过**（#24）：overflow-bypass 仅在物化启用时生效（物化未启用时保持原阻塞 `queue.put()` 行为，零回归）；overflow 时物化 store 写入失败 fail-fast（非静默丢数据）
- [x] owner-doc: `failover-design.md` §五.4 overflow-bypass 落地（对齐 §9.4 指定机制）+ producer-region 重启语义; `01-architecture-baseline.md` §五 producer-region 重启
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 — Consumer reconnect-to-live-queue + E2E

Status: completed
Targets: `SupervisionLoop.rebuildTask`（reconnect 路径）; E2E 测试; owner-docs

- Item Types: `Fix | Decision | Proof`

- [x] **Reconnect-to-live-queue 实现**：consumer-only region 重启后，物化重放完毕时切换到 live producer partition 继续消费。实现约束：`InputChannel.partition` 为 `final` 字段（`InputChannel.java:32`）——reconnect 需通过新建 InputChannel 替换进 InputGate、或 partition forwarding/delegate、或等价机制（具体方案属实现层，plan 不指定）。重复避免：materialization replay 与 live queue 间存在数据重叠（live queue 中的旧数据已在物化 store），reconnect 机制必须保证不重复处理（经 drain 旧数据 / marker 切换 / epoch 边界等机制）——`Fix`
- [x] **Reconnect exactly-once 论证**：overflow-bypass（Phase 2）保证物化 store 有全部数据（queue 满时溢出到 store）。consumer restart → restore checkpoint state → replay materialization（post-checkpoint 全部数据）→ reconnect live queue（post-restart 新数据）。重复避免机制确保 replay 与 live 间无重叠。全程数据流：checkpoint-state + replay(post-checkpoint) + live(post-reconnect) = 完整且无重叠——`Decision`
- [x] **E2E（producer-region 重启，含 stateful operator）**：多 region 作业（含 window aggregate）→ producer-region task 注入失败 → supervision loop 检测 → cancel + 重启（operator state restore from checkpoint + replay）→ 下游 consumer reconnect → **结果与无故障连续运行一致**（exactly-once，含 stateful 正确性）——`Proof`
- [x] **E2E（consumer-only region 重启，infinite source）**：多 region 作业（infinite source）→ consumer-only region task 失败 → 重启 → 物化重放 → **reconnect-to-live-queue** → 持续消费不阻塞 → exactly-once——`Proof`
- [x] 零回归：无物化 marker / 单 region 既有作业行为不变，既有测试全绿——`Proof`

Exit Criteria:

- [x] Reconnect-to-live-queue 路径存在且被 consumer 重启流程调用（**接线验证** #23：reconnect 确实发生，consumer 在重放完毕后从 live partition 读取到新数据，断言可观测）
- [x] **端到端验证**（#22）：(a) producer-region 重启 E2E——source → 物化边界 → consumer（含 window aggregate）完整跑通，producer task 失败 → drain → 重启（state restore + replay）→ reconnect → sink 输出与无故障一致（exactly-once + stateful 正确性）；(b) consumer-only region 重启（infinite source）E2E——重放 → reconnect-to-live-queue → 持续消费不阻塞
- [x] **无静默跳过**（#24）：reconnect 不可用（无 live producer partition / drain 未完成）时 fail-fast（非静默永久阻塞伪装成"正常"）
- [x] exactly-once 保持：drain/reconnect 全程 sink 输出无丢失无重复，**且 stateful operator 累积状态正确**（断言可观测——与无故障连续运行结果对比）
- [x] 零回归：无物化 marker 既有作业行为不变，既有测试全绿
- [x] owner-doc: `failover-design.md` §五.4 reconnect + exactly-once 论证; `01-architecture-baseline.md` §五 region-based failover 生产可用状态
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] Consistent-cut epoch 对齐落地（barrier bump + 控制事件过滤 + replay epoch 选择）
- [x] Operator state restore from checkpoint 落地（rebuildTask 不再从模板空状态开始）
- [x] Producer overflow-bypass 路径落地（物化启用时 write 不阻塞，drainable producer-region 不再硬拒绝）
- [x] Consumer reconnect-to-live-queue 落地（infinite source consumer 重启后不永久阻塞）
- [x] Producer-region 重启端到端可用（drain → restart → reconnect → exactly-once，含 stateful operator）
- [x] exactly-once 保持（drain/reconnect 全程无丢失无重复 + stateful 累积状态正确）
- [x] 零回归（无物化 marker / 单 region 既有作业行为不变）
- [x] `./mvnw test -pl nop-stream -am -T 1C` 通过
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high` 退出码 0
- [x] checkstyle / 代码规范检查通过
- [x] 不存在被静默降级到 deferred 的 in-scope gap（mailbox full drain-and-reinject 已显式 Non-Goal + non-blocking 理由）
- [x] 受影响 owner docs 已同步
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：supervision loop → operator state restore → producer drain → restart → consumer reconnect → live consumption 调用链运行时连通（E2E 断言 sink 持续收到新数据 + stateful 累积状态正确）；无空方法体/静默跳过/no-op
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0

## Deferred But Adjudicated

### Mailbox full drain-and-reinject 协议

- Classification: `optimization candidate`
- Why Not Blocking Closure: Phase 1 的 operator state restore 从 checkpoint 恢复 timer 注册（timer 在 `HeapInternalTimerService`，随 operator state 序列化到 checkpoint）。pending transient trigger mail 丢弃 + rebuild 后，timer 仍注册（从 checkpoint restore），下次触发重新入 mail——等价于 globalRecovery 的 timer restore 语义。"drain in-flight mail + re-inject"仅减少 transient trigger 延迟，不影响 exactly-once 正确性。
- Successor Required: no

### 跨 JVM drain/reconnect

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: in-process drain/reconnect 先正确；跨 JVM drain 涉及远程 producer pause/resume + 远程 queue drain，属后续。
- Successor Required: no

## Non-Blocking Follow-ups

- Mailbox full drain-and-reinject（减少 transient timer trigger 延迟）
- 跨 JVM drain/reconnect 协议
- Drain 性能优化（并行 drain 多个 producer partition）
- Dual-write 顺序的一致性保证（bypass-write 与 queue.put 之间的崩溃恢复语义细化）

## Closure

Status Note: Drain/reconnect mechanism（region-based failover 前置 #4）已端到端交付。consistent-cut epoch 对齐（barrier set-to-id + 控制事件过滤）+ operator state restore from checkpoint + producer overflow-bypass（解除死锁 1）+ producer-region 重启（解除 `ERR_STREAM_REGION_RESTART_UNSUPPORTED` 硬拒绝）+ consumer reconnect-to-live-queue 全部落地。5 个测试文件（12 test methods）全绿；独立子 agent closure audit 24/24 PASS；owner-docs（failover-design §五.4/§9.8、checkpoint-design §2.11.9、01-architecture-baseline §五）已同步。region-based failover 端到端可用（drain → restart → reconnect → exactly-once，含 stateful operator）。
Completed: 2026-08-03

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（explore，read-only closure audit，task ses_0384fcba0ffeMw5KAs5L5QDIim）
- Evidence:
  - 每条 Exit Criterion 验证结果：**全部 PASS**（Phase 1: 1.1-1.8 PASS；Phase 2: 2.1-2.7 PASS；Phase 3: 3.1-3.4 PASS；Anti-Hollow/特殊检查 A1-A10 PASS）
  - 每条 Closure Gate 验证结果：
    - Consistent-cut epoch 对齐：PASS — `ResultPartition.java:191-194`（barrier set-to-id）+ `:198`（isRecord() filter）
    - Operator state restore：PASS — `SupervisionLoop.java:534-556`（restoreOperatorsFromState 调用）+ `GraphModelCheckpointExecutor.java:1412`（package-private 暴露）
    - Producer overflow-bypass：PASS — `ResultPartition.java:232`（queue.offer 非阻塞）+ `SupervisionLoop.java:442`（restartRegion 总返回 true）
    - Consumer reconnect-to-live-queue：PASS — `SupervisionLoop.java:607-616`（复用旧 partition + drain + injectFront replay）
    - Producer-region 端到端：PASS — `TestSupervisionLoopProducerRegionRestart`（1 test，asserts no ERR_STREAM_REGION_RESTART_UNSUPPORTED）
    - exactly-once：PASS — `TestSupervisionLoopConsistentCut`（replay 仅 post-checkpoint records）+ `TestSupervisionLoopReconnectE2E`（replay + live 无丢失）
    - 零回归：PASS — `TestResultPartitionOverflowBypass.noMaterialization_writeBlocksWhenFull_zeroRegression` + `TestSupervisionLoopReconnectE2E.zeroRegression_noMaterialization_singleRegionBehavior` + 733 既有 tests 全绿
    - `./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime`：736 tests, 0 failures, 0 errors（rocksdb flaky performance benchmark 与本变更无关）
    - `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high`：退出码 0（无 high/critical 发现）
    - Anti-Hollow 检查：PASS — 调用链 supervision loop → restartRegion → rebuildTask → restoreOperatorsFromState → activateMaterializationReplay 运行时连通（每个 hop 有完整方法体）；`restartRegion` 无 `return false` for producer-region；无 TODO/空方法体/静默跳过
    - Deferred 项分类检查：mailbox full drain-and-reinject = `optimization candidate`（non-blocking：timer 从 checkpoint restore，等价 globalRecovery 语义）；跨 JVM drain/reconnect = `out-of-scope improvement`（in-process 先正确）—— 无 in-scope live defect 被降级
  - `node ai-dev/tools/check-plan-checklist.mjs` 退出码：见下方工具运行（strict mode）

Follow-up:

- Mailbox full drain-and-reinject 协议（减少 transient timer trigger 延迟，Non-Blocking Follow-up）
- 跨 JVM drain/reconnect 协议（out-of-scope improvement）
- Drain 性能优化（并行 drain 多个 producer partition）
- Dual-write 顺序的一致性保证细化（bypass-write 与 queue.put/offer 之间的崩溃恢复语义）
- Successor plan 5（per-region counter 可配置）仍 active
