# nop-stream 对抗性审查 — Round 8（第8轮全模块审查）

> 审查日期：2026-05-31
> 审查范围：nop-stream 全模块（10 个子模块），聚焦近50次提交后的新代码状态 + 之前审查未覆盖的盲区
> 审查方法：开放式发现导向，4 个并行探索 agent 分别聚焦 core/operators/state、runtime/checkpoint/cluster/transport、CEP/NFA/SharedBuffer、connector/windowing
> 去重：已阅读以下已有报告，本报告不重复其中内容：
> - `ai-dev/audits/2026-05-31-adversarial-review-nop-stream/`（Round 5，AR-1~AR-16）
> - `ai-dev/audits/2026-05-31-adversarial-review-nop-stream-r6/`（Round 6，AR-17~AR-35）
> - `ai-dev/audits/2026-05-31-adversarial-review-nop-stream-r7/`（Round 7，AR-36~AR-54）
> - `ai-dev/audits/2026-05-31-deep-audit-nop-stream-full/`（21 维度深度审核，59 条发现）
> - `ai-dev/audits/2026-05-30-adversarial-review-nop-stream-r13/`（Round 13，AR-1~AR-17）
> - 以及 2026-05-20 至 2026-05-30 期间所有 nop-stream 审计报告
> 发现来源视角：10x 规模运维者 + 新人开发者 + 异常路径侦探

---

## 已修复确认

以下之前报告的问题经代码验证已修复：

| 旧编号 | 问题 | 修复方式 |
|--------|------|---------|
| R5-AR-6 | CepPatternBuilder 非起始 pattern where 条件未应用 | ✅ `buildWhere` 现在对每个非起始 pattern 调用 |
| R5-AR-4 | WindowOperator.windowNamespace identityHashCode | ✅ 改为 `toString()` |
| R13-AR-4 | GraphExecutionPlan topologicalSort 无环检测 | ✅ 已添加环检测 |
| R13-AR-6 | CountTrigger canMerge 导致 Session Window 计数器归零 | ✅ canMerge 改为 false |
| R13-AR-10 | RecordWriter selectChannel 整数溢出 | ✅ 已修复 |
| R13-AR-13 | ClassNameValidator 允许数组前缀 | ✅ 已限制 |

---

## 新发现

### [AR-55] RemoteGraphExecutionPlanBuilder 只为 taskIndex==0 注册 executionVertices — 分布式模式 parallelism>1 时 checkpoint 完全失效

- **文件**: `nop-stream-runtime/.../transport/RemoteGraphExecutionPlanBuilder.java:225-232`
- **证据片段**:
  ```java
  if (taskIndex == 0) {
      invokables.put(vertexId, invokable);
      JobVertex execVertex = new JobVertex(
              original.getId(), original.getName(), original.getParallelism(),
              original.getOperatorChains(), invokable);
      executionVertices.put(vertexId, execVertex);
  }
  ```
- **严重程度**: P0
- **现状**: `executionVertices` map 对每个 vertex 只存储 taskIndex==0 的 `JobVertex`。`CheckpointPlanBuilder.build()` 遍历 `executionPlan.getExecutionVertices()` 确定需要 ACK 的 task 数量。当 parallelism>1 时，只有 1 个 subtask 被注册到 checkpoint 计划中，其他 subtask 的 barrier 注入、snapshot ACK 全部缺失。结合同文件第 219-220 行硬编码 `"pipeline-0"`（AR-44 已报告），分布式执行路径的 checkpoint 管线在 parallelism>1 时完全不可用。
- **风险**: 任何 parallelism>1 的分布式流作业无法正确完成 checkpoint。作业看似运行但状态不可恢复。
- **建议**: 将所有 subtask 的 invokable 和 TaskLocation 注册到 `executionVertices`，或重构 `CheckpointPlanBuilder` 使其从 `subtasksMap` 构建 checkpoint 计划。
- **信心水平**: 确定
- **发现来源视角**: 10x 规模运维者

---

### [AR-56] CheckpointCoordinator.registerTask/unregisterTask 非原子 read-then-replace — 并发注册丢失 task

- **文件**: `nop-stream-runtime/.../checkpoint/CheckpointCoordinator.java:354-372`
- **证据片段**:
  ```java
  public void registerTask(TaskLocation taskLocation) {
      Set<TaskLocation> current = this.tasksToAcknowledge;  // 读取
      if (!current.contains(taskLocation)) {
          Set<TaskLocation> newSet = ConcurrentHashMap.newKeySet();
          newSet.addAll(current);  // 基于旧值创建新集合
          newSet.add(taskLocation);
          this.tasksToAcknowledge = newSet;  // 替换
      }
  }
  ```
- **严重程度**: P1
- **现状**: `registerTask` 和 `unregisterTask` 使用 read-then-replace 模式更新 `tasksToAcknowledge`。两个并发调用可以读到相同的 `current`，各自创建新集合并替换，后写入的覆盖先写入的——丢失其中一个注册。`acknowledgeTask()` 检查 `tasksToAcknowledge.contains()`，丢失的 task 的 ACK 永远不被接受 → checkpoint 挂死。
- **风险**: 分布式执行中动态注册 task（如 scale-out）可能丢失 task 注册，导致 checkpoint 永远无法完成。配合 AR-17（operatorsToAck 不重置），产生级联的 checkpoint 死锁。
- **建议**: 使用 `ConcurrentHashMap.newKeySet()` 作为 `tasksToAcknowledge`，直接 `add`/`remove` 而非 copy-on-write 替换。或使用 `synchronized` 保护注册/注销操作。
- **信心水平**: 确定
- **发现来源视角**: 10x 规模运维者

---

### [AR-57] ProcessingTimeoutTrigger.onEventTime/onProcessingTime 将 CONTINUE 强转为 FIRE — 嵌套 trigger 语义失效

- **文件**: `nop-stream-core/.../windowing/triggers/ProcessingTimeoutTrigger.java:95-109`
- **证据片段**:
  ```java
  @Override
  public TriggerResult onProcessingTime(long timestamp, W window, TriggerContext ctx) {
      TriggerResult triggerResult = this.nestedTrigger.onProcessingTime(timestamp, window, ctx);
      if (shouldClearOnTimeout) {
          this.clear(window, ctx);
      }
      return triggerResult.isPurge() ? TriggerResult.FIRE_AND_PURGE : TriggerResult.FIRE;
      // ↑ CONTINUE 被转为 FIRE
  }

  @Override
  public TriggerResult onEventTime(long timestamp, W window, TriggerContext ctx) {
      TriggerResult triggerResult = this.nestedTrigger.onEventTime(timestamp, window, ctx);
      if (shouldClearOnTimeout) {
          this.clear(window, ctx);
      }
      return triggerResult.isPurge() ? TriggerResult.FIRE_AND_PURGE : TriggerResult.FIRE;
      // ↑ CONTINUE 被转为 FIRE
  }
  ```
- **严重程度**: P1
- **现状**: `onProcessingTime` 和 `onEventTime` 无条件将嵌套 trigger 的返回值映射为 `FIRE` 或 `FIRE_AND_PURGE`。当嵌套 trigger 返回 `CONTINUE`（表示"不触发，继续等待"），本应透传给上层，但被强制转为 `FIRE`。这破坏了任何使用内部定时器的嵌套 trigger 语义（如 `ContinuousEventTimeTrigger`、`ContinuousProcessingTimeTrigger`）。注意：该类标注为 `@Internal` 且文档注释 "API 预留，当前未被使用"。如果确认无人使用，可降级为 P2。
- **风险**: 使用 `ProcessingTimeoutTrigger.of(ContinuousEventTimeTrigger.of(...), timeout)` 模式时，窗口会在每次 event time timer 到达时强制触发，而非按预期间隔触发。
- **建议**: 仅在 `timestamp == timeoutTimestamp` 时强转 FIRE，否则透传嵌套 trigger 的原始返回值。
- **信心水平**: 确定
- **发现来源视角**: 新人开发者

---

### [AR-58] WindowAggregationOperator.resolveKey isInstance 检查方向反转 — 类型不匹配时静默使用错误 key

- **文件**: `nop-stream-core/.../operators/WindowAggregationOperator.java:535`
- **证据片段**:
  ```java
  if (selectorKey != null && !selectorKey.getClass().isInstance(currentKeyField)) {
      LOG.warn("Key type mismatch: currentKeyField type={} but expected type={}. "
              + "Falling back to keySelector result.",
              currentKeyField.getClass().getName(), selectorKey.getClass().getName());
      return selectorKey;
  }
  ```
- **严重程度**: P1
- **现状**: `selectorKey.getClass().isInstance(currentKeyField)` 检查的是 `currentKeyField instanceof selectorKey.getClass()`。但日志消息说的是 "currentKeyField type=X but expected type=Y"，暗示意图是检查 `currentKeyField` 是否符合 `selectorKey` 的类型。如果 `currentKeyField` 是 `Long` 而 `selectorKey` 是 `Integer`，`Integer.class.isInstance(Long)` 返回 false，触发 fallback——这是正确的。但如果 `currentKeyField` 是 `Integer` 而 `selectorKey` 是 `Number`，`Number.class.isInstance(Integer)` 返回 true，不触发 fallback——这也是正确的。然而，如果 `currentKeyField` 是 `Long` 而 `selectorKey` 是 `Integer`，错误消息说 "expected Integer" 但实际返回 `selectorKey`（Integer）——行为正确但检查逻辑不直观。

  实际 bug 场景：如果 `currentKeyField` 是 `String`（错误类型）而 `selectorKey` 是 `Integer`，`Integer.class.isInstance(String)` 返回 false → 触发 fallback → 正确。但如果 `currentKeyField` 是 `Integer`（错误类型）而 `selectorKey` 是 `Number`（更宽泛），`Number.class.isInstance(Integer)` 返回 true → 不触发 fallback → 使用错误的 `currentKeyField`。

  根本问题：检查应该是 `!currentKeyField.getClass().equals(selectorKey.getClass())` 或 `!(currentKeyField instanceof selectorKey.getClass())`，而不是反过来。
- **风险**: JSON 反序列化后 key 类型变化时，某些子类/父类关系场景下 fallback 不触发，使用错误的 key 类型导致状态查找失败或数据错乱。
- **建议**: 改为 `!currentKeyField.getClass().equals(selectorKey.getClass())` 或 `!selectorKey.getClass().isAssignableFrom(currentKeyField.getClass())`。
- **信心水平**: 很可能
- **发现来源视角**: 异常路径侦探

---

### [AR-59] SimpleStreamOperatorFactory 在 NotSerializableException 时静默返回共享模板 — 并行执行共享可变状态

- **文件**: `nop-stream-core/.../operators/SimpleStreamOperatorFactory.java:54-57`
- **证据片段**:
  ```java
  } catch (java.io.NotSerializableException e) {
      // Operator contains non-serializable fields (e.g. lambdas).
      // Return the shared template instance instead of failing.
      return operator;
  }
  ```
- **严重程度**: P1
- **现状**: 当 operator 包含非序列化字段（如 lambda、数据库连接等），`createStreamOperator()` 静默返回同一个 `operator` 实例。在 parallelism>1 时，所有 subtask 共享同一个 operator 对象，包括其所有可变状态（计数器、buffer、accumulator）。更严重的是，如果 operator 在 `open()` 中初始化资源，只有第一个调用 `open()` 的 subtask 成功初始化，后续 subtask 看到已初始化状态并可能并发访问。
- **风险**: parallelism>1 时，多个 subtask 并发读写同一个 operator 实例的可变字段，导致数据竞争、状态不一致或 NPE。
- **建议**: (1) 对 NotSerializableException 抛出明确异常，要求 operator 必须可序列化；(2) 或在 `createStreamOperator()` 中添加 LOG.warn，并在工厂创建阶段检测并行度>1 与非序列化 operator 的组合。
- **信心水平**: 确定
- **发现来源视角**: 10x 规模运维者

---

### [AR-60] GraphModelCheckpointExecutor.registerOperators 重复注册 CheckpointListener 和 CheckpointParticipant

- **文件**: `nop-stream-runtime/.../execution/GraphModelCheckpointExecutor.java:409-424`
- **证据片段**:
  ```java
  for (StreamOperator<?> op : operators) {
      if (op instanceof CheckpointListener) {
          coordinator.addListener((CheckpointListener) op);       // 第1次注册
      }
      if (op instanceof AbstractUdfStreamOperator) {
          Object udf = ((AbstractUdfStreamOperator<?, ?>) op).getUserFunction();
          if (udf instanceof CheckpointListener && udf != op) {
              coordinator.addListener((CheckpointListener) udf);  // 第2次注册（如果 op == udf）
          }
          // ...
      }
  }
  ```
- **严重程度**: P2
- **现状**: 当 `op` 是 `AbstractUdfStreamOperator` 且 `op == udf`（如 operator 本身就是 UDF），第一个 `if` 注册了 `op`，第三个 `if` 的 `udf != op` 检查会跳过——这种情况安全。但当 `op instanceof CheckpointListener` 且 `op` 也是 `AbstractUdfStreamOperator`，且 `udf != op` 但 `udf` 也是 `CheckpointListener`，`op` 和 `udf` 分别被注册——如果 `op` 在 `notifyCheckpointComplete` 时已经委托给 `udf`，则 `udf` 被通知两次。对于 `CheckpointParticipant` 也存在类似问题。
- **风险**: checkpoint 完成通知被重复发送，如果 listener 不幂等（如递增计数器、提交事务），会导致逻辑错误。
- **建议**: 使用 Set 去重 listener/participant 注册，或在注册前检查 `coordinator.getListeners().contains()`。
- **信心水平**: 很可能

---

### [AR-61] InputGate 200 轮空循环后返回 end-of-stream 但不检查 isAllFinished — 慢生产者场景过早终止

- **文件**: `nop-stream-core/.../execution/InputGate.java:260-268`
- **证据片段**:
  ```java
  if (isAllFinished()) {
      return Optional.empty();  // 正确的 EOS
  }

  emptyRounds++;
  if (emptyRounds >= 200) {
      return Optional.empty();  // 没有检查 isAllFinished！
  }
  LockSupport.parkNanos(10_000_000L);
  ```
- **严重程度**: P2
- **现状**: 当所有 channel 暂时为空（如上游 producer 暂停、网络抖动），200 轮 × 10ms = ~2 秒后，`InputGate` 返回 `Optional.empty()`。调用者 `StreamTaskInvokable` 将其视为 end-of-stream 并终止 task。虽然有 `isAllFinished()` 检查在前面（line 260），但该检查在 `emptyRounds++` 之前执行——如果所有 channel 在此时都恰好为空但未 finished，代码跳过 `isAllFinished()` 检查直接返回 empty。
- **风险**: 上游 producer 暂停超过 2 秒时，consumer task 被过早终止。在网络不稳定或背压场景下可能频繁触发。
- **建议**: 在 `emptyRounds >= 200` 分支中添加 `isAllFinished()` 检查：如果不是所有 channel 都 finished，继续等待而非返回 empty。
- **信心水平**: 确定
- **发现来源视角**: 10x 规模运维者

---

### [AR-62] WindowOperator.snapshotState 对 triggerAccumulators 浅拷贝 — checkpoint 后的状态变更破坏已捕获快照

- **文件**: `nop-stream-runtime/.../operators/windowing/WindowOperator.java:309-315`
- **证据片段**:
  ```java
  @Override
  public OperatorSnapshotResult snapshotState(StateSnapshotContext context) throws Exception {
      OperatorSnapshotResult result = super.snapshotState(context);
      if (triggerAccumulators != null) {
          result.putOperatorState("trigger-accumulators", new HashMap<>(triggerAccumulators));
      }
      return result;
  }
  ```
- **严重程度**: P2
- **现状**: `new HashMap<>(triggerAccumulators)` 是浅拷贝——Map 的 key 和 value（`SimpleAccumulator` 对象）是共享引用。`SimpleAccumulator` 是可变对象（如 `LongCounter` 有 `add()`、`resetLocal()` 方法）。在 snapshot 返回后、checkpoint 数据被持久化前，如果 operator 继续处理元素并修改 accumulator 值（如 `CountTrigger.onElement` 递增计数器），checkpoint 中捕获的快照会被并发修改污染。
- **风险**: 从受污染的快照恢复后，trigger 状态不正确（如 CountTrigger 的计数偏高或偏低），导致窗口触发时机错误。
- **建议**: 使用深拷贝（如 `new LongCounter(acc.get())` 对每个 accumulator 创建独立副本），或在 checkpoint 持久化完成前冻结 trigger 状态修改。
- **信心水平**: 很可能
- **发现来源视角**: 异常路径侦探

---

### [AR-63] StateTransition.equals() 不比较 condition — 语义不同的转换被视为相等

- **文件**: `nop-stream-cep/.../nfa/StateTransition.java:70-78`
- **证据片段**:
  ```java
  @Override
  public boolean equals(Object obj) {
      if (obj instanceof StateTransition) {
          @SuppressWarnings("unchecked")
          StateTransition<T> other = (StateTransition<T>) obj;
          return action == other.action
                  && sourceState.getName().equals(other.sourceState.getName())
                  && targetState.getName().equals(other.targetState.getName());
          // condition 未参与比较
      } else {
          return false;
      }
  }
  ```
- **严重程度**: P2
- **现状**: 两个 `StateTransition` 只比较 source/target state name 和 action，不比较 `condition`。`State.equals()`（同文件 line 110）通过 `Objects.equals(stateTransitions, that.stateTransitions)` 间接使用 `StateTransition.equals()`。这意味着两个有相同 source/target/action 但不同 condition 的状态被视为相等。在 `NFACompiler.copyWithoutTransitiveNots` 和 `NFAFactoryCompiler.copy()` 中如果依赖 equals 去重，会导致条件被静默丢失。
- **风险**: NFA 编译阶段如果使用 Set 或 Map 对 StateTransition 去重，不同条件的转换被错误合并。
- **建议**: 在 `equals()` 中加入 `Objects.equals(condition, other.condition)`，或在 `hashCode()` 中也包含 condition。
- **信心水平**: 很可能
- **发现来源视角**: 异常路径侦探

---

### [AR-64] RemoteInputChannel.close() 使用 queue.offer() 注入 END_OF_STREAM — 满队列时读取线程永久阻塞

- **文件**: `nop-stream-runtime/.../transport/RemoteInputChannel.java:167-170`
- **证据片段**:
  ```java
  public void close() {
      if (subscription != null && !subscription.isCancelled()) {
          subscription.cancel();
      }
      if (!finished) {
          finished = true;
          queue.offer(END_OF_STREAM);  // offer() 不阻塞，满时返回 false
      }
  }
  ```
- **严重程度**: P2
- **现状**: `queue` 是 `ArrayBlockingQueue(1024)`。当队列已满（producer 端 `send()` 快于 consumer 端 `read()`），`queue.offer(END_OF_STREAM)` 返回 false，END_OF_STREAM 信号丢失。consumer 线程在 `queue.take()`（line 120 附近）永久阻塞，因为 `finished` 虽为 true 但 `read()` 方法先检查队列再检查 finished 标志。
- **风险**: 分布式模式下，consumer 线程在 channel 关闭时永久阻塞，导致 task 无法正常停止。
- **建议**: 改为 `queue.put(END_OF_STREAM)`（阻塞直到有空间），或先 `queue.clear()` 再 `queue.offer(END_OF_STREAM)`，或在 `read()` 中检查 `finished` 标志时也返回 null。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

### [AR-65] GraphModelCheckpointExecutor.shutdown() 不等待 barrierScheduler 终止 — 未执行的 barrier 注入任务可能操作已关闭的 coordinator

- **文件**: `nop-stream-runtime/.../execution/GraphModelCheckpointExecutor.java:543-551`
- **证据片段**:
  ```java
  private static void shutdown(ScheduledExecutorService barrierScheduler, CheckpointCoordinator coordinator, TaskExecutor executor) {
      if (executor != null) {
          executor.shutdownNow();
      }
      if (barrierScheduler != null) {
          barrierScheduler.shutdownNow();  // 仅发送中断，不等待
      }
      coordinator.shutdown();              // 立即关闭
  }
  ```
- **严重程度**: P2
- **现状**: `barrierScheduler.shutdownNow()` 发送中断信号但立即返回。`coordinator.shutdown()` 在下一行被调用。如果 barrier scheduler 中有正在执行或待执行的 barrier 注入任务，它会在 coordinator 已关闭后尝试调用 `coordinator.tryTriggerPendingCheckpoint()`，导致操作已关闭的 coordinator（可能抛异常或静默失败）。
- **建议**: 在 `barrierScheduler.shutdownNow()` 后添加 `barrierScheduler.awaitTermination(timeout, unit)`，或在 coordinator.shutdown() 中添加防御性检查。
- **信心水平**: 很可能
- **发现来源视角**: 异常路径侦探

---

### [AR-66] CepOperator 在 timestamp == currentWatermark 时丢弃事件 — 语义偏差

- **文件**: `nop-stream-cep/.../operator/CepOperator.java:322-328`
- **证据片段**:
  ```java
  if (timestamp > timerService.currentWatermark()) {
      bufferEvent(value, timestamp);
  } else if (lateDataOutputTag != null) {
      output.collect(lateDataOutputTag, element);
  } else {
      numLateRecordsDropped.increment();
  }
  ```
- **严重程度**: P2
- **现状**: 使用严格大于 `>` 比较 watermark。timestamp 等于 watermark 的事件被视为 late data。Flink 的原始 CEP 实现使用 `>=`（watermark 标记的是"此时间戳及之前的数据已到达"）。这个偏差意味着恰好在 watermark 边界上的事件被丢弃或发送到 late output。
- **风险**: 对于高频事件流，watermark 边界上的事件被系统性地丢弃。在精确一次语义要求下可能导致数据不完整。
- **建议**: 改为 `timestamp >= timerService.currentWatermark()`。
- **信心水平**: 很可能
- **发现来源视角**: 新人开发者

---

### [AR-67] NFAStateNameHandler 使用 `:` 作为分隔符 — 用户 pattern name 中的冒号导致名称截断

- **文件**: `nop-stream-cep/.../nfa/compiler/NFAStateNameHandler.java:47-49`
- **证据片段**:
  ```java
  public static String getOriginalNameFromInternal(String internalName) {
      Guard.notNull(internalName, "internalName");
      return internalName.split(STATE_NAME_DELIM)[0];  // STATE_NAME_DELIM = ":"
  }
  ```
- **严重程度**: P3
- **现状**: 如果用户定义的 pattern name 包含冒号（如 `"order:created"`），`getOriginalNameFromInternal` 会截断为 `"order"`。NFA 内部使用加后缀的名称（如 `"order:created.NOT_FOLLOW"`），但 `getOriginalNameFromInternal` 只取第一段。这在 `SharedBuffer` 操作和 `extractPatterns` 中用于关联 pattern name → 导致包含冒号的 pattern name 无法正确匹配到事件。
- **风险**: 使用冒号的 pattern name 导致 CEP 匹配结果中的 pattern 关联错误。
- **建议**: 使用不可能出现在标识符中的分隔符（如 `\0`），或在 `STATE_NAME_DELIM` 前后添加转义字符。
- **信心水平**: 确定
- **发现来源视角**: 新人开发者

---

### [AR-68] GlobalWindow 缺少 readResolve() — Java 反序列化打破单例约束

- **文件**: `nop-stream-core/.../windowing/windows/GlobalWindow.java:28-33`
- **证据片段**:
  ```java
  public final class GlobalWindow implements Serializable {
      private static final GlobalWindow INSTANCE = new GlobalWindow();

      public static GlobalWindow get() {
          return INSTANCE;
      }
      // 无 readResolve() 方法
  }
  ```
- **严重程度**: P3
- **现状**: `GlobalWindow` 使用单例模式但未实现 `readResolve()`。通过 Java 序列化/反序列化后会产生多个实例。如果代码使用 `== GlobalWindow.get()` 进行身份比较（而非 `equals()`），反序列化后的实例比较失败。虽然当前代码路径似乎都使用 `equals()`，但这是一个潜在的脆弱性。
- **建议**: 添加 `private Object readResolve() { return INSTANCE; }`。
- **信心水平**: 确定

---

## 历史问题状态更新

以下之前报告的问题在本轮审查中确认**仍然存在**（简要引用，不重复分析）：

| # | 描述 | 状态 | 变化 |
|---|------|------|------|
| R5-AR-1 | Lockable.release() refCounter<=0 返回 true | 仍存在 | 无变化 |
| R5-AR-2 | JdbcClusterRegistry.registerCoordinator 无 fencing token 校验 | 仍存在 | 无变化 |
| R5-AR-3 | WindowOperator 非 SimpleAccumulator 覆盖写入 | 仍存在 | 无变化 |
| R5-AR-5 | WindowOperator timer purge 不清除 trigger 状态（含 merging window 路径） | 仍存在 | merging 路径确认也未调用 triggerContext.clear() |
| R5-AR-7 | SharedBuffer.advanceTime 不清理 backing state | 仍存在 | 无变化 |
| R5-AR-8 | CepOperator 清除 NFA 状态产生孤立 SharedBuffer 条目 | 仍存在 | 无变化 |
| R5-AR-9 | Source/Sink 快照失败不通知 callback | 仍存在 | 无变化 |
| R5-AR-10 | DebeziumCdcSourceFunction draining 死代码 | 仍存在 | 无变化 |
| R5-AR-11 | SourceEnumerator.assignSplits 要求顺序调用 | 仍存在 | 无变化 |
| R5-AR-12 | JdbcCheckpointStorage INSERT-UPDATE 不检查 affected rows | 仍存在 | 无变化 |
| R6-AR-17 | CheckpointBarrierTracker operatorsToAck 不重置 | 仍存在 | 无变化 |
| R6-AR-18 | AbstractStreamOperator 吞掉 snapshot 异常 | 仍存在 | 无变化 |
| R6-AR-19 | CheckpointCoordinator counter 不推进 | 仍存在 | 无变化 |
| R6-AR-20 | InputGate 不检查 barrier ID | 仍存在 | 无变化 |
| R6-AR-21 | CepOperator timer 不被 checkpoint 持久化 | 仍存在 | 无变化 |
| R6-AR-23 | CheckpointConfig.jobId 默认随机 UUID | 仍存在 | 无变化 |
| R6-AR-24 | DebeziumCdcSourceFunction 声称 REPLAYABLE 但无 checkpoint | 仍存在 | 无变化 |
| R7-AR-36 | SharedBuffer.flushCache clear-before-write | 仍存在 | 无变化 |
| R7-AR-37 | NFAState STATE_COMPARATOR 使用 hashCode 而非 compareTo | 仍存在 | 无变化 |
| R7-AR-38 | TwoPhaseCommitSinkFunction saveState 无同步 | 仍存在 | 无变化 |
| R7-AR-42 | BatchConsumerSinkFunction 急切创建不可序列化 consumer | 仍存在 | 无变化 |
| R7-AR-43 | CheckpointCoordinator 存储失败不调 abort | 仍存在 | 无变化 |
| R7-AR-44 | JobCoordinator.assignTasks 硬编码 pipeline-0 | 仍存在 | 无变化 |

---

## 总评

### 最值得关注的 3 个方向

1. **分布式执行路径的 checkpoint 可用性仍然是最严重的系统性问题**（AR-55）。`RemoteGraphExecutionPlanBuilder` 只为 taskIndex==0 注册 executionVertices，意味着 parallelism>1 的分布式作业完全无法进行 checkpoint。这与 AR-44（硬编码 pipeline-0）和 AR-56（registerTask 非原子丢失注册）形成了"分布式 checkpoint 完全不可用"的三重确认。三轮审查都持续报告这个方向的问题，但根因定位越来越精确：从最初发现 pipeline-0 硬编码（R5-AR-44），到现在发现整个 executionVertices 注册只覆盖第一个 subtask。

2. **ProcessingTimeoutTrigger 的语义 bug 是一个未被发现的 "陷阱 API"**（AR-57）。虽然当前标注为未使用，但作为 `@Internal` 公开 API，一旦有人使用就会触发：所有嵌套 trigger 的 event time / processing time timer 回调都被强转为 FIRE，使 `ContinuousEventTimeTrigger` 等需要精确控制触发时机的 trigger 完全失效。这种 bug 的特点是"代码看起来合理，但在特定组合下静默失效"，非常适合作为未来使用者的陷阱。

3. **SimpleStreamOperatorFactory 的共享模板问题揭示了并行执行的潜在风险**（AR-59）。当 operator 不可序列化时，所有 subtask 共享同一个 operator 实例。这不是边界情况——lambda 是 Java 中最常见的不可序列化模式，而 operator 的 lambda 使用在 Nop 平台中很常见。结合之前报告的多个 operator 线程安全问题（AR-41 CepOperator processElement vs timer 并发），共享实例会放大已有的线程安全缺陷。

### 本次审查的盲区自评

1. **nop-stream-flow 模块**：仍然为空壳模块，无法审查。
2. **Flink 集成路径**：nop-stream-flink 仍为空，未审查。
3. **大规模并行压力测试**：所有 parallelism>1 的问题（AR-55/56/59）都是代码分析，没有实际运行验证。
4. **序列化兼容性**：没有验证 checkpoint 数据在不同版本间的向前/向后兼容性。
5. **性能基准**：所有性能相关发现（如 triggerAccumulators 浅拷贝的竞态窗口）都是理论分析。
6. **新增测试的有效性**：近50次提交增加了大量测试（3643 行新增），但未审查测试是否真正覆盖了之前报告的 bug 的修复。

---

## 严重程度分布表

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0      | 1    | 分布式 checkpoint parallelism>1 失效 |
| P1      | 4    | registerTask 非原子丢失、ProcessingTimeoutTrigger 语义、resolveKey 检查反转、共享模板可变状态 |
| P2      | 6    | checkpoint 重复通知、InputGate 过早 EOS、浅拷贝竞态、StateTransition equals、RemoteInputChannel 阻塞、shutdown 竞态、CEP watermark 语义 |
| P3      | 2    | pattern name 分隔符、GlobalWindow 单例 |
