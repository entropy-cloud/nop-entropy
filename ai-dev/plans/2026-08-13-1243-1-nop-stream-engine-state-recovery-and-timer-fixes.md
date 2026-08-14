# 1 运行时引擎修复：Memory 后端恢复键物化（P0）+ 空闲期定时器触发（P1）+ barrier 超时解耦（P1）+ trigger 状态清理（P1）

> Plan Status: completed
> Last Reviewed: 2026-08-13（closure audit 通过）
> Source: `ai-dev/audits/2026-08-13-0805-open-audit-nop-stream-invariant-loop.md`（AR-01 P0 / AR-02 P1）+ `ai-dev/audits/2026-08-13-0805-multi-audit-nop-stream-invariant-loop.md`（P1-INV-2 / P1-INV-1）
> Related: `2026-08-13-0132-1-nop-stream-runtime-service-wiring.md`（AR-02 的修复残余来源）、`2026-08-13-1243-3`（跨模块契约，含 RocksDB 命名空间）

## Purpose

把 nop-stream 运行时引擎上四个"静默行为缺陷"收口：checkpoint 恢复后 Memory 后端数值键状态 100% 静默丢失（P0）；处理时间定时器在空闲期永不触发（P1，0132-1 生产接线修复的残余缺口）；barrier 对齐超时/unaligned 逃生检查被持续数据流饿死（P1）；`WindowOperator.triggerAccumulators` 无界泄漏 + timer 路径 PURGE 不对称（P1）。全部以 `Fix` 落地并附带 focused 回归测试。

## Current Baseline

（live repo 2026-08-13 复核，全部已源码验证）

- `MemoryStateSerDe.serializeKey`/`deserializeKey` 均为原样透传（`MemoryStateSerDe.java:814-820`）；`TypedNamespaceAndKey.equals/hashCode` 类敏感（`TypedNamespaceAndKey.java:29-39`）。JSON 持久化（默认 `storageType="local"`，`CheckpointConfig.java:47`）经 `TextScanner.parseInteger` 先试 `Integer.parseInt` → `Long(123)` 恢复成 `Integer(123)` → live 查找 Long 键全 miss → **键控状态静默从空开始**。RocksDB 后端经 `RocksDBKeyEncoder.jsonToKey` 按 keyType 重物化（`RocksDBKeyEncoder.java:258-267`），同 checkpoint 恢复正确——跨后端分叉。
- `ProcessingTimeServiceDriver.run()` 仅 `mailbox.put(...)`（非阻塞投递，`ProcessingTimeServiceDriver.java:112-128`）；`StreamTaskInvokable.processInputGate` 的 mail drain 只发生在 `inputGate.read()` 返回后的循环顶（`StreamTaskInvokable.java:730-737`）；`InputGate.readMultiChannel`/`readSingleChannel` 空闲期忙等（50ms bounded read + 10ms park，`continue retry`，**从不返回空**，`InputGate.java:505-540`）→ 空闲任务 fire mail 滞留 mailbox 直到下一条数据到达。`TestProcessingTimeWindowProductionE2E` 用有界源 + 源结束后末次 drain 掩盖此缺口（生产 `StreamSourceOperator` 的 drain 调用点只在数据面推进时触发）。**关键约束：`processInputGate` 唯一生产调用者（`StreamTaskInvokable.java:742-745`）把空返回当 EOS（`break` → finish → close），因此"空闲返回空"必须与"终止返回空"可区分，不得让空闲空返回触发任务终止。**
- `InputGate` 的 elapsed 检查（unaligned 1s 逃生 / 30s 超时）只在"整轮 sweep 零返回"后评估（`InputGate.java:508` vs `:515-536`）；对齐期间活跃 channel 持续出数据 → 每次 read 都在 `:508` 提前返回，检查永不执行 → 失败检测劣化到 coordinator 侧 `checkpointTimeout`（默认 600s）。现有 3 个相关测试全用 stuck 空 channel。
- `WindowOperator.triggerAccumulators` 全文件唯一写点 `:2062 put`（`:258` 字段声明、`:398` init、`:536` close 置空、`:543` 快照全量 clone、`:587` 恢复），零 `remove()`/`clear()` 调用；timer 路径 PURGE（`:814-819` event-time / `:884-889` processing-time）不调 `triggerContext.clear()`（元素路径 `:762-765`、cleanup 分支 `:828` 有 clear，不对称）→ 无界增长 + checkpoint 体积膨胀 + 恢复后死条目永存。**注意：`triggerContext.clear()` 内部会经 `getSimpleAccumulator`（`:2037-2039` → trigger.clear → miss 时 `:2062` put）重建条目，删除 hook 必须放在最后一次 clear 之后。** `TestTriggerAccumulatorsCheckpoint` 只测 round-trip 不测清理。
- 门禁体系（不变式 #1-#7）与全量测试基线：2895 tests / 0 failures（2026-08-13 I5 记录）。

## Goals

- Memory 后端 JSON checkpoint 恢复后，数值键（Long/Integer）键控状态按 keyType 重物化，与 RocksDB 后端行为一致（同一 checkpoint 两后端恢复结果相同）。
- 空闲任务（无数据流动）的处理时间定时器仍按墙钟触发（PT 窗口、cleanup 定时器、CEP PT 超时）。
- barrier 对齐期间活跃 channel 持续出数据时，unaligned 逃生（1s）与对齐超时 fail-fast（30s）仍按设计阈值评估，不被饿死。
- `triggerAccumulators` 随窗口清理/merge/retire 删除对应条目；timer 路径 PURGE 与元素路径对称调用 `triggerContext.clear()`。

## Non-Goals

- 不改 `TypedNamespaceAndKey` 的类敏感 equals/hashCode 语义本身（改为 Number 宽容是 AR-01 建议的备选方案，本计划走"恢复时重物化"主路径）。
- 不处理 POJO 键恢复问题（P2-INV-4，backlog；AR-01 只覆盖数值键 P0 变体；POJO 键修复依赖同一 deserializeKey 机制，执行时若重物化天然覆盖则标注关闭）。
- 不处理 RocksDB 命名空间迁移（P1-01-01 → plan 3）、`key.toString()` 算子级键（AR-20 P2，backlog）、RocksDBSnapshotSerDe 分组分隔符碰撞（AR-19 P2，backlog）。
- 不做性能基准（空闲期 drain 频率不做压力实证，测试覆盖正确性）。

## Scope

### In Scope

- AR-01（P0）：Memory 后端恢复路径按 keyType 重物化键 + 数值键 round-trip 回归测试（先红后绿）。
- AR-02（P1）：空闲期周期性 drain 机制（read 空转超时返回 或 驱动 mail 检查），保证 PT 定时器空闲期触发 + 空闲期回归测试。
- P1-INV-2（P1）：elapsed 检查与数据返回解耦（每次 read 入口评估或独立计时驱动）+ 持续流量回归测试。
- P1-INV-1（P1）：`triggerAccumulators` 清理路径补全（clearWindowContents / retire / merge / timer-PURGE）+ 清理回归测试。

### Out Of Scope

- CEP/NFA、RPC/transport 面（本次审计盲区项）。
- Debezium offset 注册表（AR-03 P1 → plan 3）。

## Execution Plan

### Phase 1 - Memory 后端恢复键物化（AR-01，P0）

Status: completed
Targets: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryStateSerDe.java`、`TypedNamespaceAndKey.java`（仅参考，不修改）、`TestCheckpointSerDeConsistency.java` 或新增 `TestMemoryStateSerDeNumericKeyRestore`、既有 RocksDB 恢复测试族（`TestRocksDB*`，定位为跨后端对照）

- Item Types: `Fix | Proof | Proof | Proof | Proof`

- [x] Fix: `deserializeKey` 按 keyType 重物化（与 RocksDB `jsonToKey` 对齐；`JsonTool.parseBeanFromText(json, keyType)` 或等价路径；`MemoryStateSerDe` 已有 `keyType` 字段可用），使 `Long(123)` round-trip 后仍为 `Long`
- [x] Proof: 新增"Long 键 checkpoint round-trip 恢复后键控状态可读"测试——先红（现行为：恢复后 lookup miss）后绿（重物化后命中）；覆盖 Integer 键、Long 键（> 2^31 与 < 2^31 各一）、String 键回归
- [x] Proof: 新增"同一 checkpoint 快照两后端恢复结果一致"对照测试——同一 JSON checkpoint 分别经 Memory 与 RocksDB 后端恢复，断言 Long/Integer 键键控状态读取结果相同（复用既有 RocksDB 恢复测试族扩展或新增对照用例；`TestRocksDBSnapshotRestore` 已有同快照跨后端恢复先例）
- [x] Proof: 作业级重启 E2E——run1 写入 Long 键状态 + checkpoint → 重启恢复 run2 读取同一键命中（先红后绿；复用 `TestE2ECheckpointAndRecovery` 族或新增）
- [x] Proof: 执行类别清扫——Memory 后端其余"原样透传"序列化路径（timer 键、window 键、聚合状态键）逐一核对是否同受数值键物化问题影响；确认修复覆盖或显式标注不适用，结果记录在 plan 执行日志

**Phase 1 执行记录（类别清扫结果，2026-08-13）**：
- 键控状态（ValueState/MapState/ListState/ReducingState/AggregatingState/Internal* 全 8 种）：统一经 `deserializeKey` 重物化——修复天然覆盖全部键控状态类型（含 POJO 键，见下）。
- MapState 内层用户键：`restoreMapState` 已按 `mapKeyTypeName` 经 `deserializeValue` JSON 重物化（既有行为，不依赖本次修复）。
- Window 命名空间键：`deserializeNamespace` 将 TimeWindow 重建为 `new TimeWindow(((Number)start).longValue(), ...)`，GlobalWindow 走字符串哨兵——数值字段全部经 Number 提取，**不适用**（已免疫）。
- 聚合/还原状态键：与 ValueState 同一 `TypedNamespaceAndKey` 存储面，经同一 `deserializeKey`——**已覆盖**。
- 算子级 `key.toString()` 键（WindowOperator triggerAccumulators / paneTracking）：String 键面，AR-20 P2 backlog，不经后端键编码——**不适用**（独立失败面，plan Non-Goals 已标注）。
- **Timer 键（新发现，登记 backlog）**：`HeapInternalTimerService.TimerEntry.fromSerializableForm`（`(K) form.get("key")` 原样 cast）在 JSON checkpoint 持久化（`CheckpointSerDe.deserializeOperatorState` → `fromSerializableForm`）路径下与 AR-01 同机制受损：Long 定时器键 < 2^31 round-trip 成 Integer，恢复后 timer 回调经 `setCurrentKey` 访问键控状态 miss → 静默状态丢失。走 operator state（`internal-timers`）而非键控状态，**本次修复不覆盖**；修复需把 keyType 传入 timer restore 路径（`HeapInternalTimerService` 持 keyType 或 `WindowOperator` restore 时按 keyType 重物化）——登记 roadmap backlog（open-audit 批次 follow-up，P0 变体评估）。
- 无静默跳过：`deserializeKey` 重物化失败（parse 异常或结果类型不匹配）抛 `StreamException(ERR_STREAM_STATE_ERROR)`，不返回原对象。
- POJO 键（P2-INV-4 POJO 变体）：同一 `deserializeKey` 机制天然覆盖（JSON Map → 按 keyType 重物化为 POJO 实例）——**标注关闭**（plan Non-Goals 触发条件已满足）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `TestMemoryStateSerDeNumericKeyRestore`（或等价）全绿：Long 键（含 < 2^31 与 > 2^31）与 Integer 键经 JSON 持久化 round-trip 后键控状态读取命中，String 键回归不破坏
- [x] 同类测试在 RocksDB 后端等价用例维持全绿（跨后端一致性：同一 checkpoint 快照两后端恢复结果相同）
- [x] 类别清扫结果记录在 plan 执行日志（哪些路径已覆盖、哪些显式不适用及理由）
- [x] **端到端验证**：作业级重启 E2E——run1 写入 Long 键状态 + checkpoint → 重启恢复 run2 读取同一键（先红后绿）
- [x] 无静默跳过：重物化失败路径抛异常而非静默返回原对象
- [x] No owner-doc update required（state-management-design §6 描述序列化策略与 schema checksum 语义，未承诺键类型保持语义；恢复键物化为内部实现细节，执行时核对确认无需同步）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 空闲期处理时间定时器触发（AR-02，P1）

Status: completed
Targets: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/InputGate.java`、`StreamTaskInvokable.java`（processInputGate 空闲/EOS 区分）、新增空闲期回归测试

- Item Types: `Fix | Fix | Fix`

- [x] Fix: 让空闲期 mail drain 可达——采用"空闲返回空 + 调用方区分空闲与 EOS"方案：`readSingleChannel`/`readMultiChannel` 在全 channel 空转 N 次后返回空 Optional（仅表示空闲）；`processInputGate` 改为在空返回时区分"空闲"（继续循环顶执行 `processAvailableMails()` 后重读）与"EOS"（`isAllFinished()` 为真才 `break`），**不得让空闲空返回触发任务终止**。此改动须覆盖 `read()` 唯一生产调用方（`StreamTaskInvokable.processInputGate`，`:742-745`）
- [x] Fix: **阈值与既有契约对齐（先决策后实现）**——空闲空转阈值必须大于 channel 超时阈值（150ms），否则空闲返回先于 `checkChannelTimeout` 触发，破坏生产者死亡快速失败（既有修复缺陷）；执行前核对 `TestInputGateTermination`（`testSlowProducerDoesNotCausePrematureTermination` 断言 500ms 内 read() 不返回）与 `TestInputGateSingleChannelRemoteLiveness`（`testSingleChannelGateSurfacesProducerDeath` 断言 150ms 处第二次 read 抛 `ERR_STREAM_CHANNEL_TIMEOUT`）——若空闲空返回与既有断言冲突，重写为"循环内连续调用 read() 直至真实空/EOS"并保留生产者死亡语义断言；其余直接调用 `inputGate.read()` 的测试（`TestGraphExecutionPlan`、`TestParallelGraphExecution`、`TestDataExchange`、`TestInputGateMailboxAbort` 等约 20 处循环）执行时逐一核对空返回语义
- [x] Fix: 新增"发射结束后空闲期窗口仍按墙钟触发"回归测试——测试需在空闲期断言中间状态：有界源 + 长空闲段（execute 异步线程 + 中途轮询断言，或分阶段发射并在空闲段断言窗口已发射）；**不得只用源结束后的末次 drain 断言**（那正是既有掩蔽机制）；PT 窗口边界触发 + cleanup 定时器触发（先红后绿）

**Phase 2 执行记录（2026-08-13）**：
- **方案决策**：空闲返回空 + 调用方区分。`IDLE_RETURN_THRESHOLD_MS = 250`（> channel 心跳超时 150ms；生产 RemoteInputChannel 构造 `channelTimeoutMs=0` 禁用超时，无竞争）；空闲测量 = 全 channel 零返回累计时长（数据/barrier/watermark 任一元素重置）。
- **read() 契约变化与既有测试核对结果**：语义从"空 = EOS"变为"空 = 空闲或 EOS"。逐一核对 20+ 处 read 循环：写后即 close 的测试（TestInputGate/TestDataExchange/TestGraphExecutionPlan/TestParallelGraphExecution/TestInputGateBarrier*/ProcessingGuarantee 等）EOS 先于空闲返回，无需改动；`TestInputGateTermination.testSlowProducerDoesNotCausePrematureTermination` 与空闲空返回直接冲突 → 重写为"循环内连续 read 直至真实 EOS"（保留"慢生产者不导致提前终止"语义断言，额外断言迟到的记录值）；`TestInputGateMultiEpochBarrier.testAbortedAlignmentLetsNextBarrierAlignCleanly` 与 `TestInputGateMarkFinishedChannelRace` 3 个跨线程 reader 循环改为 `isAllFinished()` 判真 EOS（消除 250ms 空闲返回与跨线程写数据的竞态窗口）；`TestInputGateMailboxAbort`/对齐/超时/unaligned 测试的 while(true) 循环天然容忍空闲返回，零改动。生产者死亡语义验证：`TestInputGateSingleChannelRemoteLiveness` 3/3 全绿（150ms channel timeout 先于 250ms 空闲返回触发）。
- **接线验证**：`TestIdlePeriodProcessingTimeWindowE2E.testProcessingTimeWindowFiresDuringIdlePeriod`——两顶点图（source task → window task 跨 ResultPartition/InputGate），源发射 1 元素后空闲 1200ms；测试线程在空闲段中间轮询 sink 结果，断言窗口**已在空闲期发射**（`jobThread.isAlive()` 同时为真，排除 EOS drain 掩蔽）→ 证明空闲空返回使 `processInputGate` 循环顶 `processAvailableMails()` 在运行时被重新执行。`testProcessingTimeCleanupTimerFiresDuringIdlePeriod`——invokable 级直连断言 `internalTimerService.numProcessingTimeTimers()==0` 于空闲期（数据面无 EOS）。
- **先红后绿**：`IDLE_RETURN_THRESHOLD_MS=60000`（等价修复前永不空闲返回）→ 2/2 红；恢复 250 → 2/2 绿。
- **无静默跳过**：空闲返回不吞异常（InterruptedException 仍置中断位返回空）；`processInputGate` 中断/cancel 空返回退出（`Thread.currentThread().isInterrupted()` / `mailboxExecutor.isCancelled()` 检查），不忙等热循环。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 新增空闲期测试全绿：数据停止流动后，PT 窗口在墙钟边界发射（空闲段中间断言）；PT cleanup timer 触发（`numProcessingTimeTimers()==0` 断言）
- [x] 既有 PT 测试（`TestProcessingTimeWindowProductionE2E`、`TestCepProductionExecutionE2E` 等）回归全绿
- [x] barrier 对齐既有测试（stuck channel 用例与既有持续流量 E2E）回归全绿；空闲空返回不触发任务提前终止（EOS 语义回归断言）；`TestInputGateTermination`/`TestInputGateSingleChannelRemoteLiveness` 等 read() 契约测试按上项决策后的行为全绿
- [x] **端到端验证**：从 `env.addSource()` 到 sink 的空闲期 PT 窗口完整路径已验证（数据面空闲 → 墙钟触发 → 窗口结果输出）
- [x] **接线验证**：空闲空返回确实使 `processInputGate` 循环顶 `processAvailableMails()` 在运行时被重新执行（测试断言：空闲期 fire mail 被 drain 且 timer 回调执行；或代码审查确认调用链连通）
- [x] 无静默跳过：空闲返回路径不得吞异常；中断空返回不得造成忙等热循环（中断后 `isAllFinished()` 语义核对——`read()` 中断返回空且中断标志已置位时，`processInputGate` 不得无限重读空转；沿用既有 cancel 标志检查退出）
- [x] 若行为语义变化：`ai-dev/design/nop-stream/core-design.md` 或 window-design 同步；否则 `No owner-doc update required`（mailbox-design.md §3.4/§3.5/§7 已同步 read() 空闲有界语义与空闲期 drain 闭环）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - barrier 对齐超时/unaligned 逃生检查解耦（P1-INV-2，P1）

Status: completed
Targets: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/InputGate.java`（与 Phase 2 同一 read 循环，须在 Phase 2 之后的最终循环结构上实施）、`TestInputGateAlignmentTimeout.java` / `TestUnalignedCheckpointBackpressure.java`

- Item Types: `Fix | Fix`

- [x] Fix: elapsed 检查移到每次 `read()` 入口评估（与数据返回解耦）——在 Phase 2 改造后的最终循环结构上实施：每次 `read()` 入口（无论随后是否从某个 channel 读到数据）评估 oldest in-flight 对齐的 elapsed；保持多 in-flight epoch 语义（oldest 对齐基准）与 Stage 43/45 行为不变（unaligned 逃生 → `switchToUnalignedAndEmit`；超时 → `ERR_STREAM_BARRIER_ALIGNMENT_TIMEOUT`）
- [x] Fix: 新增"对齐期间活跃 channel 持续出数据"回归测试——背压慢流持续有数据时，unaligned 逃生（1s）与对齐超时（30s，测试可配置缩短阈值）在预期时间触发（先红后绿）；同时断言空闲期（Phase 2 场景）elapsed 检查不被跳过

**Phase 3 执行记录（2026-08-13）**：
- **实现**：`checkAlignmentElapsed()` 提取为独立方法（oldest in-flight + 未全收 + elapsed 超限 → unaligned 逃生/超时抛错），在 `readMultiChannel()` 每次循环入口调用（pendingBarrierEmissions drain 之后、channel sweep 之前）；sweep 级旧检查删除。fully-received（pending emission）对齐不触发闸门；多 in-flight D4 守卫、`ERR_STREAM_BARRIER_ALIGNMENT_TIMEOUT` 错误码不变。
- **测试（先红后绿）**：`TestInputGateAlignmentStarvationFix`（3 用例）——(a) 对齐超时（300ms 配置）在 p1 持续写数据流时触发（pre-fix 饿死：3/3 红，15.8s 超时窗口跑满；post-fix 0.9s 全绿）；(b) unaligned 逃生（200ms 阈值）在持续流量下发射 barrier；(c) 空闲期对齐（Phase 2 场景）elapsed 检查不被空闲返回跳过。traffic 计数断言保证"持续流量"场景真实（非 stuck channel 误配）。
- **端到端**：既有 `TestUnalignedCheckpointBackpressure`（barrier → 逃生 → ChannelState 捕获 → 恢复重放）维持全绿（83 条 runtime 回归批次内）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 新增持续流量测试全绿：数据持续流动中对齐超时/逃生按阈值触发；现有 stuck-channel 测试（检查原行为）回归全绿
- [x] 与 Phase 2 组合后最终循环结构复核：空闲空返回路径与入口 elapsed 检查不互相干扰（Phase 2 空闲期测试 + Phase 3 持续流量测试同跑全绿）
- [x] **端到端验证**：unaligned checkpoint 路径端到端（barrier 触发 → 逃生 → ChannelState 捕获 → 恢复）完整走通
- [x] 无静默跳过：超时路径继续 fail-fast 抛 `ERR_STREAM_BARRIER_ALIGNMENT_TIMEOUT`（现有错误码不变）
- [x] checkpoint-design.md §2.4/§2.11 若描述超时语义变化则同步；否则 `No owner-doc update required`（§2.4 已补"elapsed 评估与数据返回解耦"说明，阈值/错误码/逃生语义不变）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - triggerAccumulators 生命周期清理（P1-INV-1，P1）

Status: completed
Targets: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java`、`TestTriggerAccumulatorsCheckpoint.java`（扩展）

- Item Types: `Fix | Fix | Fix | Fix`

- [x] Fix: **先决策后实现：删除时机必须在最后一次 `triggerContext.clear()` 之后**——关键约束：`clearWindowContents`（`:1395-1423`）在 purge/cleanup 路径中先于 `triggerContext.clear()` 执行，而 `triggerContext.clear()` 内部经 `trigger.clear(window, this)` → `CountTrigger`/`ContinuousProcessingTimeTrigger` 的 clear 会调用 `getSimpleAccumulator(...)`（`:2037-2039` → trigger clear → `getSimpleAccumulator` 在 miss 时 `accums.put`，`:2062`）**重建已删条目**。因此删除 hook 不得放在 `clearWindowContents` 内（会被随后 clear 重建），必须放在最后一次 `triggerContext.clear()` 之后（如各 purge 分支末尾，或删除后二次核对条目是否重建——以"清理完成后 map 中该窗口条目不存在"为最终断言）。实现方案二选一并在 plan 日志留痕：(a) 各 purge/cleanup 路径末尾按前缀删除；(b) 删除后断言核对
- [x] Fix: 补全三处不对称 purge 路径的 `triggerContext.clear()`——(a) onEventTime timer PURGE（`:814-819`）、(b) onProcessingTime timer PURGE（`:884-889`，结构与 event-time 相同）、(c) 合并路径元素 purge（`:726-728`，有 clearWindowContents 无 triggerContext.clear()）；与元素路径（`:762-765`）与 cleanup 分支（`:828`，已有 clear）对称
- [x] Fix: **合并路径键基准**——`getSimpleAccumulator` 的 stateKey 嵌入 `triggerContext.window`（实际窗口），`clearWindowContents` 收到的是 stateWindow，合并场景二者可不同；合并函数循环（`:694-698`，window=合并窗口 m）为正确删除点，前缀删除使用合并窗口 m 而非 stateWindow；条目键格式与 `getSimpleAccumulator` 的 stateKey 构造（`:2050`）严格一致
- [x] Fix: 扩展 `TestTriggerAccumulatorsCheckpoint` 或新增测试——窗口清理/merge/retire 后 `triggerAccumulators` 对应条目被移除（先红后绿，**断言放在清理完成后**，防 clear 重建掩蔽）；恢复后无死条目残留；快照体积不再含死条目

**Phase 4 执行记录（2026-08-13）**：
- **方案决策（留痕）**：选 (a) 各 purge/cleanup 路径末尾按前缀删除——`removeTriggerAccumulators(key, window)` 以 `"trigger_" + key + SEP + window + SEP` 前缀删除（与 `getSimpleAccumulator` 的 stateKey 构造 `:2050` 严格一致），置于每个路径最后一次 `triggerContext.clear()` 之后。理由：(a) 比 (b)（删除后核对）少一次扫描且删除点语义明确；`triggerContext.clear()` 是重建的唯一来源，其后无 `getSimpleAccumulator` 调用，前缀删除即终态。
- **六处删除点**：元素路径 purge（`:762-765` 之后）、合并元素 purge（`:729-731` 之后，新增 clear）、onEventTime timer PURGE（`:814-819` 之后，新增 clear）、onProcessingTime timer PURGE（`:884-889` 之后，新增 clear）、event-time cleanup 分支（`:828` 之后）、processing-time cleanup 分支（`:898` 之后）、合并循环（`:694-698`，window=m 基准）。
- **测试（先红后绿）**：新增 `TestWindowOperatorTriggerAccumulatorCleanup`（5 用例）——元素路径 FIRE_AND_PURGE 后条目移除、event-time timer PURGE 后条目移除（processWatermark 驱动真实 timer 路径）、processing-time timer PURGE 后条目移除（`fireProcessingTimeTimers` 驱动真实 timer 路径）、merge 后合并出窗口条目移除（key 基准 = 合并窗口 m；剩余 1 条 = 合并结果窗口条目，由 trigger.onMerge 创建并被第二元素复用）、purge 后快照/恢复无死条目。git stash 还原修复 → 5/5 红；恢复 → 5/5 绿。
- **断言时序**：全部断言在清理完成后（purge 分支 / timer fire / merge 循环结束之后），防 `triggerContext.clear()` 重建掩蔽。
- **`:2047` 已关闭守卫**：`removeTriggerAccumulators` 空 map 早退，不触碰 `getSimpleAccumulator` 的"已关闭"守卫语义（该守卫仍只由 trigger clear 触发）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 新增清理测试全绿：清理完成后条目被移除（map 大小断言 + 快照体积不再含死条目；断言时序在最后一次 clear 之后）
- [x] 三处 timer/合并路径 PURGE 后 `triggerContext.clear()` 被调用（event-time + processing-time + 合并路径三处计数/标志断言）
- [x] 既有窗口测试（`TestWindowOperator*`、E2E 窗口 6/6+7/7 基线）回归全绿
- [x] 无静默跳过：清理路径不得吞异常；`:2047` 的"已关闭"守卫行为不变
- [x] `ai-dev/design/nop-stream/window-design.md` 若描述触发状态生命周期则同步；否则 `No owner-doc update required`（§8.5 已补 triggerAccumulators 生命周期闭环说明，与既有"清除窗口全部状态"契约对齐）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] AR-01（P0）已修复：Memory 后端数值键恢复重物化 + 先红后绿测试证据在案
- [x] AR-02（P1）已修复：空闲期 PT 定时器按墙钟触发 + 回归测试证据在案
- [x] P1-INV-2（P1）已修复：elapsed 检查与数据返回解耦 + 持续流量测试证据在案
- [x] P1-INV-1（P1）已修复：triggerAccumulators 清理路径 + 测试证据在案
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect（四项均为确认缺陷，必须 Fix 落地）
- [x] 受影响的 owner docs 已同步到 live baseline，或明确写明 No owner-doc update required
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）空闲期 drain/elapsed 检查在运行时调用链连通（Phase 2/3 的接线证据），（b）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw test -pl nop-stream -am -T 1C`（或 `-pl nop-stream-core,nop-stream-runtime`）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream-core --severity high` exit 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream-runtime --severity high` exit 0（Phase 4 改动了 runtime 的 WindowOperator）
- [x] mjs 不变式门禁 `node ai-dev/tools/check-nop-stream-invariants.mjs all` exit 0（若本计划改动影响 output-contract/wiring 注册表行号，同步注册表）
- [x] `node ai-dev/tools/check-plan-checklist.mjs 2026-08-13-1243-1-nop-stream-engine-state-recovery-and-timer-fixes.md --strict` exit 0

## Deferred But Adjudicated

### POJO 键恢复（P2-INV-4 的 POJO 变体）

- Classification: `watch-only residual`
- Why Not Blocking Closure: 本次 scope 覆盖数值键 P0（AR-01）；POJO 键为 P2 backlog 条目（P2-INV-4），同一 `deserializeKey` 重物化机制若天然覆盖则在 Phase 1 标注关闭；否则维持 backlog 触发条件
- Successor Required: `no`

### 空闲期 drain 频率性能基准

- Classification: `optimization candidate`
- Why Not Blocking Closure: 正确性（墙钟触发）由本计划保障；100ms 空转返回的具体频率为工程选型，不做压力实证
- Successor Required: `no`

## Non-Blocking Follow-ups

- AR-02 修复后评估"空闲期定时器墙钟触发"是否沉淀为不变式 #7 wiring 族扩展候选（open-audit 总评建议，I6 评估）
- 类别清扫发现的其他兄弟路径若超出本 scope，登记 backlog

## Closure

Status Note: 四项确认缺陷（AR-01 P0 / AR-02 P1 / P1-INV-2 P1 / P1-INV-1 P1）全部以 Fix 落地，先红后绿证据在案，独立 closure audit PASS。
Completed: 2026-08-13

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session `ses_006443c5fffeguO9ilpiYMMCrU`）
- Audit Session: `ses_006443c5fffeguO9ilpiYMMCrU`
- Evidence:
  - 每条 Exit Criterion 的验证结果：4 Phase 全部 PASS（PASS + live code path / test name 见 audit 报告逐条：MemoryStateSerDe.deserializeKey :836-858 / InputGate idle :453-458,:576-581 / checkAlignmentElapsed :512,:595-616 / processInputGate :730-764 / WindowOperator.removeTriggerAccumulators :961-967 + 7 处调用点；测试族 TestMemoryStateSerDeNumericKeyRestore(5) / TestRocksDBSnapshotRestore(18) / TestE2ECheckpointAndRecovery(5) / TestIdlePeriodProcessingTimeWindowE2E(2) / TestInputGateAlignmentStarvationFix(3) / TestWindowOperatorTriggerAccumulatorCleanup(5) / TestInputGateTermination 重写）
  - 每条 Closure Gate 的验证结果：13/13 PASS（全量 `./mvnw test -pl nop-stream -am -T 1C` = nop-stream 组 2913 tests / 0 failures；scan-hollow core/runtime exit 0；mjs invariants all exit 0（output-contract 注册表 WindowOperator 发射点 1136/1966 → 1192/2022 已同步）；check-plan-checklist --strict exit 0）
  - Anti-Hollow 检查结果：驱动→fire mail→drainAndRun→TimerServiceManager→HeapInternalTimerService 全链运行时连通（audit 报告 #6 逐行追踪）+ scan-hollow exit 0；接线由 TestIdlePeriodProcessingTimeWindowE2E 运行时证明
  - Deferred 项分类检查：P2-INV-4 POJO 变体 = watch-only residual 且被同一 deserializeKey 机制天然覆盖（标注关闭）；空闲期 drain 频率 = optimization candidate；Timer 键（AR-22）为类别清扫新发现登记 backlog（非 in-scope 降级——走 operator state 面，plan Non-Goals 已界定时限）

Follow-up:

- 无 remaining plan-owned work。Non-blocking：AR-22（Timer 键 JSON 恢复物化）已登记 roadmap backlog（P0 变体评估）；"空闲期定时器墙钟触发"沉淀为不变式 #7 wiring 族扩展候选（open-audit 总评建议，I6 评估）
