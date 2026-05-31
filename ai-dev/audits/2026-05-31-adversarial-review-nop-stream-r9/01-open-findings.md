# nop-stream 对抗性审查 — Round 15

> 审查日期：2026-05-31
> 审查范围：nop-stream 全模块（5 个活跃子模块），开放式发现导向
> 审查方法：4 个并行探索 agent 分别覆盖 (1) 最近修复验证 + 序列化/状态/Timer，(2) CEP/NFA/SharedBuffer，(3) connector/windowing 触发器/侧输出，(4) transport/runtime 执行引擎。然后逐文件验证关键代码路径。
> 发现来源视角：异常路径侦探 + 10x 规模运维者 + 新人开发者
> 去重：已阅读以下已有报告，本报告不重复其中已修复内容：
> - `2026-05-20-adversarial-review-nop-stream/` ~ `2026-05-31-adversarial-review-nop-stream/`（Round 1~14）
> - `2026-05-25-deep-audit-nop-stream-full/` ~ `2026-05-31-deep-audit-nop-stream-full/`

---

## 前轮已知未修复问题（简要引用）

以下之前报告的问题在当前代码中确认**仍然存在**（简要引用，不重复分析）：

| 轮次/编号 | 问题 | 状态 |
|-----------|------|------|
| R14-AR-1 | WindowAggregationOperator serializeTriggerState `:` 分隔符 | ⚠️ 仍存在（TimeWindow JSON 含 `:` → checkpoint 恢复失败） |
| R14-AR-2 | GraphModelCheckpointExecutor.stopSources() 关闭所有 invokable 的 outputWriter | ⚠️ 仍存在 |
| R14-AR-3 | WindowOperator.windowNamespace() 非	TimeWindow 使用 identityHashCode | ⚠️ 仍存在 |
| R14-AR-4 | SharedBuffer.advanceTime() 不清理 eventsBuffer 持久化状态 | ⚠️ 仍存在 |
| R8-AR-55 | RemoteGraphExecutionPlanBuilder 只为 taskIndex==0 注册 executionVertices | ⚠️ 仍存在 |
| R8-AR-56 | CheckpointCoordinator.registerTask 非原子 read-then-replace | ⚠️ 仍存在 |
| R8-AR-57 | ProcessingTimeoutTrigger 将 CONTINUE 强转为 FIRE | ⚠️ 仍存在 |
| R8-AR-58 | WindowAggregationOperator.resolveKey isInstance 检查方向反转 | ⚠️ 仍存在 |
| R8-AR-59 | SimpleStreamOperatorFactory 不可序列化时静默返回共享模板 | ⚠️ 仍存在 |
| R8-AR-61 | InputGate 200 轮空循环后返回 end-of-stream 不检查 isAllFinished | ⚠️ 仍存在 |
| R8-AR-63 | StateTransition.equals() 不比较 condition | ⚠️ 仍存在 |
| R8-AR-64 | RemoteInputChannel.close() queue.offer() 满队列丢失 END_OF_STREAM | ⚠️ 仍存在 |
| R8-AR-66 | CepOperator timestamp == currentWatermark 时丢弃事件 | ⚠️ 仍存在 |

---

## 新发现

### [AR-1] CheckpointBarrierTracker.triggerCheckpoint() 在 source 拒绝 barrier 后永久死锁所有后续 checkpoint

- **文件**: `nop-stream-core/.../execution/CheckpointBarrierTracker.java:55-86`
- **证据片段**:
  ```java
  public synchronized boolean triggerCheckpoint(...) throws Exception {
      if (operatorsToAck.get() > 0) {    // line 56: 永远为 true
          return false;
      }
      // ...
      this.operatorsToAck.set(count);     // line 69: 设为 N

      boolean accepted = ((StreamSourceOperator<?>) head).offerBarrier(barrier);
      if (!accepted) {                    // line 79: source 已有 pending barrier
          LOG.warn("Checkpoint {} rejected...", checkpointId);
          return false;                   // line 81: operatorsToAck 仍为 N！
      }
      return true;
  }
  ```
- **严重程度**: P0
- **现状**: 当 `offerBarrier()` 返回 false（source operator 已有 pending barrier），方法直接返回 false，但 `operatorsToAck` 在 line 69 已被设为非零值且**未重置回 0**。此后所有 `triggerCheckpoint()` 调用都命中 line 56 的 guard 并返回 false。由于没有 barrier 被注入，没有 operator 会调用 `acknowledgeOperator()`，`operatorsToAck` 永远不会递减。**所有后续 checkpoint 永久阻塞**。
- **风险**: 一次 source 拒绝 barrier（例如前一个 checkpoint 的 barrier 仍在队列中）导致该 task 的整个 checkpoint 管线永久失效。作业无法再进行任何 checkpoint，状态不可恢复。此 bug 无需并发、无需特殊网络条件——只需 checkpoint interval 短于 source 处理 barrier 的速度即可触发。
- **建议**: 在 `offerBarrier()` 返回 false 时，重置 `operatorsToAck` 为 0 并清理 `currentSnapshot`：
  ```java
  if (!accepted) {
      this.operatorsToAck.set(0);
      this.currentSnapshot = null;
      this.currentCheckpointId = -1;
      return false;
  }
  ```
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探（checkpoint 生命周期不变量破坏）

---

### [AR-2] InputGate.checkBarrierAlignmentComplete() 在上游 channel 完成（无 barrier）时静默丢弃已对齐的 checkpoint

- **文件**: `nop-stream-core/.../execution/InputGate.java:226-236, 345-349`
- **证据片段**:
  ```java
  // readMultiChannel 中，上游 channel finished 且未发 barrier
  if (channel.isFinished()) {
      if (pendingBarrier != null) {
          if (!barrierReceived[channelIndex]) {
              barrierReceived[channelIndex] = true;
              barriersRemaining--;
              checkBarrierAlignmentComplete();  // line 232
          }
      }
      continue;
  }

  // checkBarrierAlignmentComplete
  private void checkBarrierAlignmentComplete() {
      if (barriersRemaining <= 0 && pendingBarrier != null) {
          resetBarrierState();   // 清理了状态，但 barrier 从未被返回给调用者！
      }
  }
  ```
- **严重程度**: P1
- **现状**: 当 barrier alignment 启用（EXACTLY_ONCE）时，如果一个上游 channel 完成但从未发送 barrier（如 upstream crash/cancel），该 channel 被计入已接收 barrier（`barriersRemaining--`）。当所有 channel 都完成（或都已发送 barrier），`checkBarrierAlignmentComplete()` 重置 barrier 状态，但**从未将已对齐的 barrier 返回给调用者**。对比正常路径 `handleBarrierNonRecursive()`（line 295-298）会 `return Optional.of(aligned)`。
- **风险**: Checkpoint barrier 被静默丢弃，下游 operator 永远收不到该 checkpoint 的 barrier。CheckpointCoordinator 最终超时放弃，但在此窗口期内所有新 barrier 也被拒绝（见 AR-1），形成级联失效。
- **建议**: 在 `checkBarrierAlignmentComplete()` 中返回对齐的 barrier（需要重构为非 void 方法），或在 `readMultiChannel()` 中检测 `checkBarrierAlignmentComplete()` 的效果并发射 barrier。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探（barrier 对齐状态机遗漏路径）

---

### [AR-3] WindowAggregationOperator.processElementWithMerging() 从不调用 trigger.onMerge() — session window 触发器状态在合并后丢失

- **文件**: `nop-stream-core/.../operators/WindowAggregationOperator.java:321-362`
- **证据片段**:
  ```java
  for (Tuple2<Collection<W>, W> mergeOp : mergeTargets) {
      // ... 合并 source window 的 accumulator ...
      for (W sourceWindow : toBeMerged) {
          // ...
          if (!sourceWindow.equals(mergedWindow)) {
              TriggerContextImpl sourceCtx = new TriggerContextImpl(key, sourceWindow);
              trigger.clear(sourceWindow, sourceCtx);  // line 336: 清除 source trigger
              // ...
          }
      }
      // ... 存储合并后的 accumulator ...
      TriggerContextImpl triggerCtx = new TriggerContextImpl(key, mergedWindow);
      TriggerResult result = trigger.onElement(value, timestamp, mergedWindow, triggerCtx);
      // ❌ 从未调用 trigger.onMerge(mergedWindows, mergedWindow, triggerCtx)
  }
  ```
  对比 `WindowOperator.processElementForMergingWindow()` 正确调用了 `triggerContext.onMerge(mergedWindows)` (line 402)。
- **严重程度**: P1
- **现状**: `WindowAggregationOperator` 的 session window 合并路径只清除源窗口的 trigger 状态，但**不为合并后的窗口调用 `trigger.onMerge()`**。支持合并的触发器（如 `ContinuousEventTimeTrigger`、`EventTimeTrigger`）依赖 `onMerge()` 来重新注册 timer 和协调 fire timestamp。没有此调用：合并后窗口的 fire-timestamp 状态为空，已注册的 timer 被删除但未重新注册，合并后的窗口可能永远不触发或过早触发。
- **风险**: Session Window + WindowAggregationOperator + 任何支持合并的 trigger 组合下，窗口合并后触发器行为不正确。
- **建议**: 在合并循环中，对 merged window 调用 `trigger.onMerge(mergedWindows, mergedWindow, triggerCtx)`，与 `WindowOperator` 保持一致。
- **信心水平**: 确定
- **发现来源视角**: 代码生成受害者（WindowAggregationOperator 和 WindowOperator 两个不同的窗口算子对合并路径的实现不一致）

---

### [AR-4] ChainingOutput 静默丢弃侧输出记录 — WindowOperator late data 和 ProcessWindowFunction 多输出在算子链中丢失

- **文件**: `nop-stream-core/.../operators/ChainingOutput.java:81-84`
- **证据片段**:
  ```java
  @Override
  public <X> void collect(OutputTag<X> outputTag, StreamRecord<X> record) {
      LOG.warn("Side output '{}' discarded in simplified chaining mode...", outputTag);
  }
  ```
- **严重程度**: P1
- **现状**: `ChainingOutput` 是算子链（operator chaining）中连接两个算子的 Output 实现。当上游算子通过 side output（`OutputTag`）发射记录时，`ChainingOutput` 仅打印 WARN 日志并**静默丢弃记录**。影响范围：
  1. `WindowOperator` 的 late data output（通过 `lateDataOutputTag`）— 用户配置 late data 侧输出时，数据在链式管道中被丢弃。
  2. `ProcessWindowFunction` 的 `WindowContext.output(OutputTag, X)` — 用户自定义多输出在链式管道中失效。
  3. 任何使用 `Output.collect(OutputTag, StreamRecord)` 的自定义算子。
- **风险**: 用户按文档配置了 late data output tag 或 multi-output，但数据在运行时被静默丢弃。无异常、无错误日志（只有 WARN），问题极难诊断。尤其在生产环境中，late data 的丢失可能导致关键事件未被处理。
- **建议**: (1) 在 `ChainingOutput` 中将 side output 转发给下游算子的 `processElement`（如果下游支持）；(2) 或在算子链构建阶段检测 side output 依赖并阻止链式优化；(3) 至少将 WARN 升级为 ERROR 并抛出异常，让用户知道 side output 不可用。
- **信心水平**: 确定
- **发现来源视角**: 新人开发者（API 契约与实际行为不一致）

---

### [AR-5] registerTasksAndTrackers 为多链顶点用错误的 operator 列表构建 CheckpointBarrierTracker — operator index 错位导致 checkpoint 状态映射错误

- **文件**: `nop-stream-runtime/.../execution/GraphModelCheckpointExecutor.java:399-407`
- **证据片段**:
  ```java
  for (OperatorChain chain : chains) {
      List<StreamOperator<?>> operators = chain.getOperators();

      CheckpointBarrierTracker tracker = new CheckpointBarrierTracker(
              taskLocation, operators, mappings,     // ← 用循环变量 chain 的 operators
              snapshot -> coordinator.acknowledgeTask(taskLocation, snapshot.getCheckpointId(), snapshot)
      );

      invokable.setBarrierTracker(tracker);           // ← 每次循环覆盖前一个 tracker
  }
  ```
  而 `setBarrierTracker()` → `setupSnapshotCallbacks()` 使用 `operatorChain.getOperators()`（invokable 的主链）注册 callback：
  ```java
  private void setupSnapshotCallbacks() {
      List<StreamOperator<?>> operators = operatorChain.getOperators();  // invokable 的主链
      for (int i = 0; i < operators.size(); i++) {
          final int opIndex = i;
          ((AbstractStreamOperator<?>) operators.get(i)).setSnapshotCallback(
              snapshot -> barrierTracker.acknowledgeOperator(opIndex, snapshot)
          );
      }
  }
  ```
- **严重程度**: P1
- **现状**: 当一个 vertex 有多个 operator chain 时：
  1. **Tracker 覆盖**：循环为每个 chain 创建 tracker 并调用 `invokable.setBarrierTracker(tracker)`，但每次调用覆盖前一个。只有最后一个 chain 的 tracker 存活。
  2. **Operator index 错位**：存活的 tracker 持有最后一个 chain 的 operators 列表（如 [OpC, OpD]），但 `setupSnapshotCallbacks()` 在 invokable 的主链 operators（如 [OpA, OpB, OpC]）上注册 callback，使用主链 index（0, 1, 2）。当 `acknowledgeOperator(0, snapshot)` 被调用时，tracker 将其解释为 chain 中的第 0 个 operator（OpC），而非主链的第 0 个（OpA）。状态映射错位。
- **风险**: 多链 vertex 的 checkpoint 状态存储在错误的 operator key 下。恢复时状态不匹配，可能导致 operator 使用其他 operator 的状态或状态丢失。
- **建议**: 不在 chain 循环中创建 tracker。在循环外用 invokable 的主链 operators 创建一次 tracker。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探（checkpoint 管线构建逻辑与算子链生命周期交互）

---

### [AR-6] StreamTaskInvokable.invokeMiddle()/invokeSink() 不关闭 InputGate — 远程 channel 订阅泄漏

- **文件**: `nop-stream-core/.../execution/StreamTaskInvokable.java:278-298`
- **证据片段**:
  ```java
  private void invokeMiddle() throws Exception {
      try {
          if (headInput != null) {
              processInputGate(headInput);
              headInput.processWatermark(Watermark.MAX_WATERMARK);
          }
      } finally {
          if (outputWriter != null) {
              outputWriter.close();    // ✅ 关闭 output
              // ❌ inputGate 从未关闭！
          }
      }
  }

  private void invokeSink() throws Exception {
      if (headInput != null) {
          processInputGate(headInput);   // 如果抛异常，inputGate 不关闭
          headInput.processWatermark(Watermark.MAX_WATERMARK);
      }
      // ❌ 无 finally 块，inputGate 和 inputChannel 从不关闭
  }
  ```
- **严重程度**: P2
- **现状**: `invokeMiddle()` 的 finally 块只关闭 `outputWriter`，不关闭 `inputGate`。`invokeSink()` 完全没有 finally 块。对比 `invokeSource()` 有 finally 块关闭 `outputWriter`。InputGate 内部的 channel（尤其是 `RemoteInputChannel`）持有的 `IMessageSubscription` 在 channel 关闭时才会取消。如果 InputGate 从不关闭，subscription 永不取消，消息服务持续向已废弃的 consumer 投递消息。
- **风险**: (1) 资源泄漏：RemoteInputChannel 的 IMessageSubscription 不被取消。(2) 本地 InputChannel 对应的 ResultPartition queue 不被标记为 finished，下游 consumer 可能永久阻塞。(3) `invokeSink()` 如果 `processInputGate()` 抛异常，没有任何资源清理。
- **建议**: 在 `invokeMiddle()` 和 `invokeSink()` 中添加 finally 块关闭 inputGate。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探（资源生命周期不对称）

---

### [AR-7] SubtaskTask.isFinished() 将 CANCELING 视为已完成 — cancel 请求发出后即报告完成

- **文件**: `nop-stream-core/.../execution/SubtaskTask.java:126-129`
- **证据片段**:
  ```java
  public boolean isFinished() {
      State s = state.get();
      return s == State.COMPLETED || s == State.FAILED || s == State.CANCELED || s == State.CANCELING;
  }
  ```
- **严重程度**: P2
- **现状**: `CANCELING` 表示 cancel 请求已发出但 task 线程**仍在执行**。`isFinished()` 将其视为完成。依赖 `isFinished()` 的逻辑（如 `GraphModelCheckpointExecutor.waitForCompletion` 循环、metrics 收集）可能在 task 仍在写共享状态时认为其已完成。对比 `cancel()` 方法（line 104）在 RUNNING→CANCELING 后立即 interrupt 线程，但线程收到 interrupt 后可能需要时间完成清理。
- **风险**: 依赖 `isFinished()` 的清理逻辑过早执行，可能与仍在运行的 task 线程产生竞态。
- **建议**: 从 `isFinished()` 中移除 `State.CANCELING`，或新增 `isTerminal()` 方法区分"已终止"和"正在终止"。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探（任务生命周期语义）

---

### [AR-8] WindowOperator.onEventTime() 清理计时器路径不退休 merging window — MergingWindowSet 无限增长

- **文件**: `nop-stream-runtime/.../operators/windowing/WindowOperator.java:522-536`
- **证据片段**:
  ```java
  if (windowAssigner.isEventTime()
          && isCleanupTime(triggerContext.window, timer.getTimestamp())) {
      W stateWindow = mergingWindows != null
              ? mergingWindows.getStateWindow(triggerContext.window)
              : triggerContext.window;
      if (stateWindow != null) {
          clearWindowContents(triggerContext.key, stateWindow);
          triggerContext.clear();
          // ❌ 缺少 mergingWindows.retireWindow(triggerContext.window)
      }
  }

  if (mergingWindows != null) {
      mergingWindows.persist();  // 持久化了包含已清理窗口的 mapping
  }
  ```
  对比 `processElementForMergingWindow()` line 414-415 正确调用 `mergingWindows.retireWindow(actualWindow)`。
- **严重程度**: P2
- **现状**: 当 cleanup timer 触发时，窗口内容被清除、trigger 状态被清除，但 `MergingWindowSet` 的映射中该窗口**未被退休**。`persist()` 将包含已清理窗口的映射写入状态。随时间推移，`MergingWindowSet` 的映射表无限增长，每次 checkpoint 持久化更多无用条目。
- **风险**: Session Window 长期运行作业的 checkpoint 大小无限增长，恢复时间线性增加。
- **建议**: 在 cleanup timer 路径中添加 `mergingWindows.retireWindow(triggerContext.window)`。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探（窗口生命周期管理不对称）

---

### [AR-9] InputGate.handleBarrierNonRecursive 不验证新 channel 的 barrier ID — 不同 channel 的重叠 barrier 被静默合并

- **文件**: `nop-stream-core/.../execution/InputGate.java:273-279`
- **证据片段**:
  ```java
  if (!barrierReceived[channelIndex]) {
      barrierReceived[channelIndex] = true;
      if (pendingBarrier == null) {              // line 275: 第一个 barrier
          pendingBarrier = barrier;
          barriersRemaining = channels.size();
      }
      // ❌ 缺少: barrier.getId() != pendingBarrier.getId() 检查
      barriersRemaining--;                       // line 279: 静默计入
  }
  ```
  对比 `else` 分支（line 301-305）对已接收 barrier 的 channel 正确检查了 ID。
- **严重程度**: P2
- **现状**: `else` 分支（`barrierReceived[channelIndex] == true`，即同一 channel 收到第二个 barrier）正确抛出异常检测 ID 不匹配。但 `if` 分支（新 channel 收到第一个 barrier）**不检查 barrier ID**。如果 channel 0 收到 checkpoint 1 的 barrier，channel 1 收到 checkpoint 2 的 barrier（coordinator 在 checkpoint 1 完成前触发 checkpoint 2），两个不同 checkpoint 的 barrier 被静默合并为一个对齐事件。
- **风险**: `maxConcurrentCheckpoints > 1` 时，不同 checkpoint 的 barrier 被混合对齐，下游 operator 收到的 barrier ID 不正确。Checkpoint 2 的 barrier 可能被作为 checkpoint 1 的一部分处理，反之亦然。
- **建议**: 在 line 278 后添加 ID 检查：
  ```java
  if (pendingBarrier != null && barrier.getId() != pendingBarrier.getId()) {
      throw new StreamException(...);
  }
  ```
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探（barrier 对齐完整性 — R13 AR-4 修复了同 channel 情况，遗漏了跨 channel 情况）

---

## 总评

### 最值得关注的 3 个方向

1. **CheckpointBarrierTracker 的不变量破坏是本轮最严重发现**（AR-1）。一次 source 拒绝 barrier 即可导致该 task 永久丧失 checkpoint 能力。这是一个确定性 bug（非边界条件），触发条件仅仅是 checkpoint interval 短于 source 处理 barrier 的速度。更危险的是，AR-2（InputGate 静默丢弃对齐完成的 barrier）与 AR-1 形成级联效应：当上游 task 因 channel 完成丢失 barrier 后，下游 task 的 source barrier 队列可能积压，触发 AR-1 的永久死锁。

2. **WindowAggregationOperator 和 WindowOperator 的 session window 合并路径存在系统性不一致**（AR-3）。两个窗口算子对同一场景（session window + merging trigger）的实现不同：WindowOperator 正确调用 `trigger.onMerge()`，WindowAggregationOperator 完全遗漏。这不是偶然——两个类各自独立实现了合并逻辑，没有共享的合并骨架。这表明需要将窗口合并的 trigger 生命周期管理提取为共享抽象。

3. **ChainingOutput 的 side-output 静默丢弃是一个 API 契约违约**（AR-4）。用户按文档使用 `lateDataOutputTag` 或 `ProcessWindowFunction.output(OutputTag, X)` 时，数据在链式管道中静默丢失。这不是边界条件——side output 是 Flink 的一级 API 特性，而算子链是默认优化（所有 operator 默认被链化）。这意味着在大多数部署中，side output 实际上不工作。

### 本次审查的盲区自评

1. **没有运行测试验证任何发现**。AR-1（CheckpointBarrierTracker 死锁）应可通过快速触发两次 checkpoint 并让第一次的 barrier 未被 source 消费来复现。
2. **没有深入审查 `BarrierAligner`（runtime 模块中的独立 barrier 对齐实现）**。如果 runtime 的 `BarrierAligner` 与 core 的 `InputGate` barrier 对齐逻辑存在不一致，可能有额外问题。
3. **没有验证 `GraphExecutionPlan.build()` 的多链 vertex 处理**。AR-5 发现 registerTasksAndTrackers 的多链问题，但 `build()` 方法本身对多链 vertex 的处理也可能有问题。
4. **没有审查 `TwoPhaseCommitSinkFunction` 的 `pendingCommits` 在 checkpoint 恢复后的重试逻辑**。

---

## 按严重程度分布表

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0      | 1    | checkpoint 永久死锁（source 拒绝 barrier 后不变量破坏） |
| P1      | 4    | barrier 静默丢弃(1) + trigger onMerge 遗漏(1) + side-output 丢失(1) + tracker index 错位(1) |
| P2      | 4    | InputGate 泄漏(1) + isFinished 语义(1) + MergingWindowSet 增长(1) + 跨 channel barrier ID(1) |
| P3      | 0    | — |
