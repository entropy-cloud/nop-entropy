# I2 聚焦对抗探查报告（门禁盲区 + 非族候选评估）

> 状态: active（I2 Phase 3 产出）
> 日期: 2026-08-12
> 来源: plan `2026-08-12-1217-3-nop-stream-invariants-cycle1-I2-invariant-driven-audit.md` Phase 3
> 范围: nop-stream 五族 `src/main` 变更型方法（gate-inventory.json 目标集）+ 登记盲区清单 + 4 个非族候选
> 方法: `open-ended-adversarial-review-prompt.md` 聚焦探查（非全仓漫游），每发现含 位置 / 场景 / 影响 / 族标注
> 去重: 已对照 I0 catalog §1（74 finding-ID → 失败族映射）、§3（live 修复状态表）、§6（I2 关注点）与
> R16/R9 修复确认表；不重复 I1 已 pin 的 residual（RL-1..5）与 Phase 2 已裁定的 watch-only（WO-1..3）

## 0. 探查范围声明（覆盖类清单）

聚焦于 gate-inventory.json 目标集 + 盲区，未做全仓漫游。实际逐项检查的类/方法：

- **core**：`TwoPhaseCommitSinkFunction`（saveState/setPendingCommits/finishCommit/restoreFromEpoch）、
  `StreamSinkOperator`（processElement/restoreState 异常传播）、`InputGate`（handleBarrierNonRecursive、
  markFinishedChannel、abortBarrierAlignment——Phase 2 WO-3 已端到端验证）、`ChainingOutput`（生产接线
  StreamTaskInvokable:171/:209）、`OperatorChain`（open/finish/close/deepCopy）、
  `WatermarkOutputMultiplexer`/`CombinedWatermarkStatus`（unregister 路径）、
  `SimpleStreamOperatorFactory`（createStreamOperator 副本语义）、`ResultPartition`（write/close 死锁类）、
  `CheckpointBarrierTracker`（triggerCheckpoint 拒绝路径）。
- **runtime**：`WindowOperator`（onEventTime cleanup :773-783 已动态验证 → RL-6；sideOutput :1015-1017；
  restoreState→open 延迟应用）、`WindowOperatorBuilder`（buildWindowOperator 14 参转发 :183-203）、
  `JdbcClusterRegistry`/`InMemoryClusterRegistry`（live 复核，RL-1..3）、`LocalFileCheckpointStorage`
  （Phase 2 WO-1 已动态验证）、`GraphModelCheckpointExecutor.registerTasksAndTrackers`（:625-698）、
  `CheckpointCoordinator`（restoreFromCheckpoint 单调守卫 :896-900）。
- **cep**：`CepOperator`（restoreState→open watermarkRestored 守卫 :249-252；processWatermark 单调推进
  :450-458）、`SharedBuffer`（flushCache/removeEvent/removeEntry 释放对称，门禁④已覆盖）。
- **connector-batch**：`BatchConsumerSinkFunction`（flush 失败保留语义，非族候选）。
- **connector**：`MessageSinkFunction`（非序列化字段，非族候选）。

## 1. 探查发现

### PR-1. ChainingOutput 静默丢弃 side-output（R15-AR-4 确认仍 live）

- **位置**：`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/ChainingOutput.java:84-86`
  （`collect(OutputTag, record)` 仅 LOG.warn + 丢弃）；生产接线 `StreamTaskInvokable.java:171/:209`
  （`currentOp.setOutput(new ChainingOutput<>(wiredInput))`）
- **场景**：链式执行路径（默认接线）下，任一 operator 调用 `output.collect(tag, record)` 的 side-output
  被静默丢弃。触发链实证：`WindowOperator.java:1015-1017` sideOutput(lateDataOutputTag) →
  `output.collect(lateDataOutputTag, element)` → ChainingOutput.collect(OutputTag,...) 丢弃。
  R16-AR-14（OperatorChain.processElement 广播）已随类重构消失（live `OperatorChain` 无该方法），
  但 ChainingOutput 侧输出丢弃点仍在。
- **影响**：late-data 侧输出、ProcessWindowFunction 多输出等在默认链式部署中静默丢失；契约
  （Output#collect(OutputTag, record) 必须转发）违约，无 fail-fast。
- **族标注**：**新族候选**——输出契约族（side-output 转发契约），不变式陈述候选：
  「任何 `Output.collect(OutputTag, X)` 调用必须被转发到注册的 side-output 消费者，不得静默丢弃」；
  触发证据 `ChainingOutput.java:84-86` + `WindowOperator.java:1015-1017`。R15-AR-4 为历史同源
  finding（r9/01-open-findings.md，原判 P1「仍存在」，本次 live 复核确认未修复）。
- **严重度参考**：P1-P2（契约违约 + 数据静默丢失；I3 裁决）。
- **裁决输入**：已入 red-list.md §3（RL-7）；修复方向（I4）= ChainingOutput.collect(OutputTag,...)
  转发至下游 side-output 通道或 fail-fast。

### PR-2. R15-AR-1..5 修复确认表对照复核（盲区 d）

| finding-ID | R16 头部状态 | live 复核（2026-08-12） | 结论 |
|---|---|---|---|
| R15-AR-1 CheckpointBarrierTracker 永久死锁 | 仍存在 | `CheckpointBarrierTracker.java:111-151` 已改 per-id inFlight + 拒绝路径清理（:133/:143 `inFlight.remove(checkpointId)` + LOG.warn + return false） | **fixed** |
| R15-AR-2 InputGate 静默丢弃已对齐 barrier | 仍存在 | per-id `inFlightAlignments`（:625-679），Phase 2 WO-3 端到端验证通过 | **fixed** |
| R15-AR-3 WindowAggregationOperator 不调 onMerge | 仍存在 | `WindowOperator.java:644` triggerContext.onMerge(mergedWindows) | **fixed** |
| R15-AR-4 ChainingOutput 静默丢弃 side-output | 仍存在 | `ChainingOutput.java:84-86` 仍只 LOG.warn + 丢弃 | **仍 live（PR-1 → RL-7）** |
| R15-AR-5 registerTasksAndTrackers 覆盖/错位 | 仍存在 | `GraphModelCheckpointExecutor.java:645-662` 用 invokable 实际 chain + 每 task 独立 tracker + findTaskLocationInPlan 匹配 vertexId+taskIndex | **fixed** |

## 2. 盲区逐项处置声明（盲区 a-d）

- **a) 新交错组合**：
  - *barrier 交错*：Phase 2 WO-3 端到端验证（aligned 排队 barrier 全新对齐 + AT_LEAST_ONCE 跨 channel
    交错 per-id 独立）——**检查后无问题**。
  - *timer 交错*：R15-AR-8（onEventTime cleanup 不 retire merging window）经动态验证确认泄漏 →
    **已升格 RL-6**（Phase 2）；onProcessingTime 路径（:790+）与 onEventTime 共用同一
    getMergingWindowSet 生命周期，无独立新发现。
  - *restore 与处理并发*：`CepOperator.java:249-252` watermarkRestored 守卫（restore 的 watermark
    不被 open() 覆写，且消费后重置）；`WindowOperator` restoreState→open 延迟应用（restoredTimerSnapshot/
    restoredPaneTrackingSnapshot）；`StreamReduceOperator.java:121-122` restoreState 纯透传——
    **检查后无问题**。
  - *并发交错*：2PC saveState/setPendingCommits 无锁路径已 pin（RL-4/RL-5）；ClusterRegistry 多实现
    一致性已 pin（RL-1/2/3）——无新发现。
- **b) refactor 引入的新方法 / 新 call-site 语义**：表完备性门禁（inventory/sync）绿，新方法全部入表；
  语义抽查发现 **PR-1**（WindowOperator.sideOutput call-site → ChainingOutput 丢弃链）；其余新方法
  （WindowOperator.onEventTime cleanup、InputGate.handleBarrierNonRecursive 等）语义已由 Phase 2
  动态验证覆盖——**结论：1 个新发现（PR-1）**。
- **c) 8 参数元组之外的跨 Operator 参数 / 配置传递链**：`WindowOperatorBuilder.buildWindowOperator`
  (:183-203) 14 参全转发（windowAssigner/windowSerializer/keySelector/keySerializer/keyClass/windowFn/
  trigger/allowedLateness/lateDataOutputTag/accClass/stateDesc/mergeFn/evictor/accumulationMode），
  其中 8 参元组由门禁① round-trip 锁定；evictor/accumulationMode/mergeFn/stateDesc 在 builder→operator
  链中均显式落位——**检查后无问题**（无参数遗漏）。
- **d) 修复确认表对照**：见 PR-2 表——R15-AR-1/2/3/5 全部 live 确认 fixed；R15-AR-4 仍 live（PR-1）。
  **R16 头部「仍存在」表未随时间同步，属审计证据滞后（非代码缺陷）；R15-AR-4 是唯一真实未修复项。**

## 3. 4 个非族候选升格评估

| 候选 | live 复核（2026-08-12） | 裁定 | 理由 |
|---|---|---|---|
| R13-AR-9 ResultPartition.close 死锁类 | `ResultPartition.java:316-329` close 用阻塞 `queue.put(END_OF_STREAM)` + InterruptedException 传播（P1-10 修复，注释明示「never drops in-flight data」）；`TestResultPartitionDeadlock` 覆盖 full-queue 阻塞语义与中断逃生 | **不升格** | 死锁疑点已被 P1-10 裁定为有意的背压契约（阻塞直至消费端腾位）+ 中断逃生 + 测试覆盖；无静默数据丢失路径 |
| R16-AR-19/AR-20 BatchConsumerSinkFunction buffer 增长 / 序列化 | `BatchConsumerSinkFunction.java:91-105` flush 失败保留 buffer + 抛错（「data retained for retry」）；`StreamSinkOperator.java:56-57` processElement 异常上抛（task 级 fail-fast，无 catch-continue 路径）→ buffer 无无限增长面；`MessageSinkFunction.java:28` 非序列化字段仍存在（connector 模块） | **不升格** | AR-19：正常执行路径 fail-fast（异常上抛 → task 失败），buffer 保留是有意的 retry 语义，catalog §3 R10-AR-10/R13-AR-2 已记录 fixed；AR-20：`MessageSinkFunction`/`MessageSourceFunction` 在 nop-stream-connector（**不在五族门禁模块范围**），且 `SimpleStreamOperatorFactory.java:89-95` 对非序列化算子已 fail-fast（不静默共享） |
| R8-AR-59 SimpleStreamOperatorFactory 共享模板 | `SimpleStreamOperatorFactory.java:48-96`：非 Serializable 非 shareable 算子现在显式抛 `StreamException`（:89-95 fail-fast），不再静默返回共享模板 | **不升格** | **live 已修复**（fail-fast 替换静默共享，与 Rule #24 一致）；无剩余静默面 |
| R16-AR-13 WatermarkOutputMultiplexer 停滞 | `WatermarkOutputMultiplexer.java:98-106` unregisterOutput 确实不调 updateCombinedWatermark()——但**该 multiplexer 无生产接线**（仅测试引用；生产路径 `TimestampsAndWatermarksOperator.java:149-170` 用自有 OperatorWatermarkOutput） | **不升格** | 缺陷存在但位于死代码路径（无生产调用方）；若未来接线复用 multiplexer，需在接线侧补 unregister→重算语义。列为 watch-only 提示，不构成当前支持基线内的行为问题 |

**综合结论**：4 个非族候选全部裁定**不升格**（3 个因 live 已修复或无生产接线，1 个因失败路径 fail-fast
+ 模块范围外）；无新不变式族派生输入（供 I6 的 Loop Rule 派生 Cycle 2 / I1 的候选 = 0，来自非族评估；
但 PR-1 的「输出契约族」为新族候选，详见 §1）。

## 4. 已知族新实例与历史 finding 对应

- PR-1 ↔ R15-AR-4（历史 finding-ID，live 确认未修复）——新族候选（输出契约族）。
- PR-2 表内 4 项 fixed 均有 live 代码证据（`文件:行`），非新实例。
- Phase 2 的 RL-6（R15-AR-8 升格）为已知族 F1 兄弟实例（对应历史 finding-ID R15-AR-8），
  已在 red-list.md §1 RL-6 记录。
- 门禁五族（F1-F5）内未发现新的兄弟实例（gate 全绿 + 聚焦探查无新增命中）。

## 5. 盲区自评与移交

- **盲区自评**：本次探查未做全仓漫游式深度审计（按计划范围）；可能遗漏——connector 模块内其他
  序列化/生命周期问题（模块范围外）、极端并发压力下的交错（需要专门 harness，见 Non-Blocking
  Follow-ups）、以及非 gate 目标集类的低概率缺陷。
- **移交**：PR-1 已入 red-list.md §3（RL-7）随权威版移交 I3；非族候选评估结论随本报告存档，
  I6 收口时按 Loop Rule 评估是否需要派生 Cycle 2 候选。
