# nop-stream 对抗性审查 — Round 10

> 审查日期：2026-05-30
> 审查范围：nop-stream 全模块（10 个子模块），开放式发现导向
> 审查方法：3 个并行探索 agent 分别聚焦 (1) 执行引擎核心 + Window 算子 + Timer 服务，(2) CEP NFA/SharedBuffer/Pattern API，(3) 分布式运行时 + Checkpoint 协调器 + Connector。然后交叉验证并确认。
> 去重：已阅读以下已有报告，本报告不重复其中内容：
> - `2026-05-20-adversarial-review-nop-stream/`（Round 1+2, N1-N41）
> - `2026-05-22-adversarial-review-nop-stream/`（Round 1, N42-N72）
> - `2026-05-22-adversarial-review-nop-stream-r2/`（Round 3, N73-N93）
> - `2026-05-24-adversarial-review-nop-stream-r3/`（Round 4, N94-N105）
> - `2026-05-28-adversarial-review-nop-stream/`（Round 5, N106-N120）
> - `2026-05-28-adversarial-review-nop-stream-r2/`（Round 6, N121-N132）
> - `2026-05-29-adversarial-review-nop-stream/`（Round 7, AR-1~AR-18）
> - `2026-05-30-adversarial-review-nop-stream/`（Round 8+9, AR-1~AR-19 + 已确认未修复列表）
> - `2026-05-25-deep-audit-nop-stream-full/`（21 维度系统审计）
> - `2026-05-28-deep-audit-nop-stream-full/`（21 维度全量审计）
> - `2026-05-30-deep-audit-nop-stream-full/`（21 维度全量审计）
> 发现来源视角：异常路径侦探 + 10x 规模运维者 + 未来破坏者 + 事务边界追踪者

---

## 发现分类

| 类别 | 数量 | 最严重 |
|------|------|--------|
| Checkpoint 协调器正确性 | 2 | P0 |
| 分布式 TaskManager 信号量/状态 | 2 | P0 |
| Window 算子正确性 | 3 | P1 |
| Timer 服务正确性 | 1 | P1 |
| InputGate barrier 处理 | 1 | P1 |
| Connector 数据丢失 | 2 | P1 |
| CEP Pattern API 验证 | 3 | P2 |
| CEP NFAState 比较 | 1 | P2 |
| 执行引擎辅助 | 3 | P3 |

---

## P0：Checkpoint 协调器正确性

### [AR-1] CheckpointCoordinator.retryFailedCommits 用 `true` 重试 `success=false` 的提交 — 2PC 数据一致性违规

- **文件**: `nop-stream-runtime/.../checkpoint/CheckpointCoordinator.java:434-461, 427`
- **证据片段**:
  ```java
  // notifyParticipantsFinishCommit 中 (行 410-427):
  for (int i = 0; i < participants.size(); i++) {
      try {
          participants.get(i).finishCommit(checkpointId, success);  // success 可能是 false
          ...
      } catch (Exception e) {
          retries--;
          if (retries > 0) { ... }
          else {
              failedCommitParticipants.computeIfAbsent(checkpointId, ...)
                  .add(i);  // 记录失败的 participant
          }
      }
  }

  // retryFailedCommits 中 (行 444-447):
  for (Integer idx : failedIdx) {
      participants.get(idx).finishCommit(failedEpoch, true);  // ← 硬编码 true！
  }
  ```
- **严重程度**: P0
- **现状**: 当一个 checkpoint 被中止（`success=false`），`notifyParticipantsFinishCommit(checkpointId, false)` 通知 participants 回滚。如果某 participant 的 `finishCommit(false)` 本身失败（网络超时等），该 participant 被记录在 `failedCommitParticipants`。`retryFailedCommits` 在后续定时重试时调用 `finishCommit(failedEpoch, true)` — 告诉 participant **提交**一个本应**回滚**的事务。这是两阶段提交协议中的致命错误。
- **风险**: 应该回滚的外部事务（数据库写入、消息发送等）被错误提交。数据一致性被永久破坏。这在任何使用 TwoPhaseCommitSinkFunction 且 checkpoint 可能被中止的场景下触发。
- **建议**: `failedCommitParticipants` 需要同时记录原始的 `success` 值。重试时使用记录的值而非硬编码 `true`。
- **信心水平**: 确定
- **发现来源视角**: 事务边界追踪者（2PC 协议正确性）

---

### [AR-2] CheckpointCoordinator.shutdown() 跳过 participant abort 通知 — 悬挂的 prepared 事务

- **文件**: `nop-stream-runtime/.../checkpoint/CheckpointCoordinator.java:464-478`
- **证据片段**:
  ```java
  public void shutdown() {
      stopCheckpointScheduler();
      timeoutScheduler.shutdownNow();
      for (PendingCheckpoint pending : pendingCheckpoints.values()) {
          pending.dispose();  // 清理内部状态，取消 CompletableFuture
      }
      pendingCheckpoints.clear();
      numPendingCheckpoints.set(0);
      listeners.clear();
      participants.clear();             // ← 直接清除，无 abort 通知
      failedCommitParticipants.clear();
  }
  ```
- **严重程度**: P0
- **现状**: `shutdown()` 对所有 pending checkpoint 调用 `pending.dispose()` 但从不调用 `notifyParticipantsFinishCommit(checkpointId, false)` 或 `notifyCheckpointAborted(checkpointId)`。如果任何 TwoPhaseCommitSinkFunction participant 已进入 prepared 阶段（phase 1 完成但 phase 2 未执行），其事务在 coordinator shutdown 后永远悬挂。外部资源（数据库锁、消息队列 offset）永不释放。
- **风险**: (1) 数据库连接/锁泄漏。(2) Coordinator 重启后无法确定悬挂事务的最终状态。(3) 在任何使用 2PC sink 的生产 pipeline 中，coordinator 重启是常规运维操作，每次都可能产生悬挂事务。
- **建议**: `shutdown()` 应先对所有 pending checkpoint 调用 `abortPendingCheckpoint()`（包含 participant abort 通知），然后再清理状态。
- **信心水平**: 确定
- **发现来源视角**: 事务边界追踪者

---

## P0：分布式 TaskManager 信号量/状态

### [AR-3] TaskManager.cancelTask() + RunningTask.run() finally 双重释放信号量 — 容量膨胀

- **文件**: `nop-stream-runtime/.../taskmanager/TaskManager.java:291-301, 442-455`
- **证据片段**:
  ```java
  // cancelTask() (行 291-301):
  public void cancelTask(String jobId, String vertexId, int subtaskIndex) {
      RunningTask task = runningTasks.remove(taskKey);
      if (task != null) {
          task.cancel();
          capacitySemaphore.release();    // ← 第 1 次释放
      }
  }

  // RunningTask.run() finally (行 442-455):
  } finally {
      ...
      runningTasks.remove(key);
      capacitySemaphore.release();        // ← 第 2 次释放（对已开始执行的 task）
  }
  ```
- **严重程度**: P0
- **现状**: `cancelTask()` 获取信号量后立即释放。但被取消的 task 如果已在 `try` 块中执行（过了行 417 的 canceled 检查），其 `finally` 块也会释放信号量。两次 release 导致信号量可用许可超过初始容量。N 次 cancel → N 个额外许可 → 可同时运行超过 `capacity` 个 task。
- **风险**: 节点容量控制完全失效。高负载下节点资源（内存、CPU、连接池）被超额使用，可能导致 OOM 或级联故障。每次 cancel 都使容量上限增加 1。
- **建议**: `cancelTask()` 中不释放信号量，让 `RunningTask.run()` 的 finally 统一负责。或者在 `cancelTask()` 中仅释放当 task 还未进入 `try` 块时的信号量（通过 `AtomicBoolean` 标记）。
- **信心水平**: 确定
- **发现来源视角**: 10x 规模运维者（资源管理）

---

### [AR-4] TaskManager.updateFencingToken() 对未启动 task 泄漏信号量 — 永久拒绝新任务

- **文件**: `nop-stream-runtime/.../taskmanager/TaskManager.java:338-351, 415-420`
- **证据片段**:
  ```java
  // updateFencingToken() (行 343-349):
  runningTasks.entrySet().removeIf(entry -> {
      if (entry.getValue().getFencingToken().equals(oldToken)) {
          entry.getValue().cancel();   // 设置 canceled = true
          return true;                 // 从 map 中移除
      }
      return false;
  });
  // ← 未释放信号量！

  // RunningTask.run() (行 415-420):
  public void run() {
      if (canceled) {
          return;    // ← finally 块不执行！信号量在 receiveAssignment 时已获取
      }
  ```
- **严重程度**: P0
- **现状**: `updateFencingToken()` 调用 `cancel()` 并从 `runningTasks` 移除 entry。如果 task 的 `run()` 方法尚未进入 `try` 块（还在 executor 队列中），`canceled` 检查在行 417 导致立即 return。`finally` 块不执行，信号量不释放。但 entry 已从 `runningTasks` 移除。信号量许可永久丢失。
- **风险**: 每次 `updateFencingToken()` 调用可能泄漏 1 个许可。N 次全局恢复后，所有许可耗尽，节点永久拒绝新任务分配。`capacitySemaphore.availablePermits()` 归零且无法恢复，需要重启节点。
- **建议**: 在 `updateFencingToken()` 的 `removeIf` 中同时释放被取消 task 的信号量（如果 task 未进入 finally 路径），或使用 `AtomicBoolean` 确保 finally 始终执行。
- **信心水平**: 确定
- **发现来源视角**: 10x 规模运维者

---

## P1：Window 算子正确性

### [AR-5] WindowAggregationOperator allowedLateness + Long.MIN_VALUE 初始 watermark → Long 溢出，丢弃所有早期元素

- **文件**: `nop-stream-core/.../operators/WindowAggregationOperator.java:95, 226`
- **证据片段**:
  ```java
  // open() (行 95):
  this.currentWatermark = Long.MIN_VALUE;

  // processElement() (行 226):
  if (element.hasTimestamp() && timestamp < currentWatermark - allowedLateness) {
      LOG.debug("Dropping late element ...");
      return;    // ← 丢弃所有带 timestamp 的元素！
  }
  ```
- **严重程度**: P1
- **现状**: `currentWatermark` 初始值为 `Long.MIN_VALUE`（-9223372036854775808）。`Long.MIN_VALUE - allowedLateness` 发生 long 溢出，结果为 `Long.MAX_VALUE - allowedLateness + 1`（一个极大的正数）。`timestamp < (极大正数)` 对几乎所有正常 timestamp 都为 true。所有带 timestamp 的元素在第一个 watermark 到达前被当作 "late" 丢弃。
- **风险**: 任何配置了 `allowedLateness > 0` 的窗口管道在 watermark 初始化前完全丢失数据。这是默认无 watermark source 场景下的常见触发条件。
- **建议**: 在 late-element 检查前增加 `currentWatermark != Long.MIN_VALUE` 守卫，或使用 `Math.addExact` 检测溢出。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

### [AR-6] WindowAggregationOperator.restoreState 不重建 activeWindowsPerKey — MergingWindowAssigner 恢复后合并逻辑失效

- **文件**: `nop-stream-core/.../operators/WindowAggregationOperator.java:91-93, 184-199, 265`
- **证据片段**:
  ```java
  // open() (行 91-93):
  if (this.activeWindowsPerKey == null) {
      this.activeWindowsPerKey = new HashMap<>();
  }

  // restoreState() (行 184-199):
  this.windowState = new LinkedHashMap<>();
  deserializeWindowState(..., this.windowState);
  // ... 恢复 timers, triggerState ...
  rebuildTimerLookups();
  // NOTE: activeWindowsPerKey 未从 windowState 重建！

  // processElementWithMerging() (行 265):
  Set<W> activeWindows = activeWindowsPerKey.computeIfAbsent(key, k -> new LinkedHashSet<>());
  ```
- **严重程度**: P1
- **现状**: `restoreState` 恢复了 `windowState`（包含所有窗口聚合数据）但未从其中重建 `activeWindowsPerKey`。`processElementWithMerging` 的合并逻辑只考虑 `activeWindowsPerKey` 中的窗口（恢复后为空）。恢复的窗口对新元素不可见，导致：新窗口不与已恢复窗口合并、独立窗口产生重复或不正确聚合结果、已恢复窗口的 timer 触发时聚合值可能已被错误覆盖。
- **风险**: Session Window（最常见的 MergingWindowAssigner）在 checkpoint 恢复后产生错误结果。在生产环境中，恢复是常态化操作。
- **建议**: `restoreState` 末尾从 `windowState` 中提取所有 active key-window 映射并填充 `activeWindowsPerKey`。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探（恢复路径完整性）

---

### [AR-7] WindowAggregationOperator.processWatermark 在非前进 watermark 时仍向下游发送 — 违反单调性契约

- **文件**: `nop-stream-core/.../operators/WindowAggregationOperator.java:356-361`
- **证据片段**:
  ```java
  public void processWatermark(Watermark mark) throws Exception {
      long newWatermark = mark.getTimestamp();
      if (newWatermark <= currentWatermark) {
          output.emitWatermark(mark);  // ← 非前进 watermark 仍发送！
          return;
      }
      currentWatermark = newWatermark;
      // ... 处理 timer ...
  }
  ```
- **严重程度**: P1
- **现状**: 当 `newWatermark <= currentWatermark` 时，方法直接返回但仍向下游发送 watermark。这违反了 watermark 单调递增契约。下游 WindowAggregationOperator 的 `processElement` 中的 late-element 检查可能因 watermark 回退而产生误判，下游 timer 服务可能重复触发。
- **风险**: 多 source union 场景下（不同 source 的 watermark 可能回退），下游算子可能重复触发窗口或错误丢弃元素。
- **建议**: 非前进 watermark 不应向下游发送。移除 `output.emitWatermark(mark)` 或将其改为 `output.emitWatermark(new Watermark(currentWatermark))`。
- **信心水平**: 确定

---

## P1：Timer 服务正确性

### [AR-8] HeapInternalTimerService.advanceWatermark 在回调中注册的同时间戳 timer 被静默丢弃

- **文件**: `nop-stream-core/.../operators/HeapInternalTimerService.java:102-122`
- **证据片段**:
  ```java
  public void advanceWatermark(long newWatermark) throws Exception {
      // ...
      for (Map.Entry<Long, Set<TimerEntry<N>>> entry : toFire) {
          List<TimerEntry<N>> timersToFire = new ArrayList<>(entry.getValue());
          for (TimerEntry<N> timer : timersToFire) {
              triggerable.onEventTime(...);  // 回调可能注册同时间戳的新 timer
          }
          eventTimeTimers.remove(entry.getKey());  // ← 删除该时间戳下所有 timer，包括刚注册的
      }
  }
  ```
- **严重程度**: P1
- **现状**: `onEventTime` 回调中注册的 timer 如果时间戳恰好等于当前正在处理的 timestamp，会被行 120 的 `eventTimeTimers.remove(entry.getKey())` 删除。这是一个合法的重调度场景（如清理 timer 在处理完后重新注册）。
- **风险**: 使用 HeapInternalTimerService 的所有算子（包括 WindowAggregationOperator）在 timer 回调中重调度时丢失 timer。窗口可能无法正确清理或触发。
- **建议**: 删除前先检查并保留新注册的 timer。例如：`Set<TimerEntry<N>> remaining = eventTimeTimers.get(entry.getKey()); if (remaining == null || remaining.isEmpty()) eventTimeTimers.remove(entry.getKey());`
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

## P1：InputGate barrier 处理

### [AR-9] InputGate AT_LEAST_ONCE 模式对每个 channel 都发送 barrier — 每个 checkpoint 触发 N 次快照

- **文件**: `nop-stream-core/.../execution/InputGate.java:278-283`
- **证据片段**:
  ```java
  if (!barrierAlignment) {
      // AT_LEAST_ONCE: emit barrier immediately on first receipt, don't block
      if (barriersRemaining <= 0) {
          resetBarrierState();
      }
      return Optional.of(barrier);  // ← 每个 channel receipt 都返回 barrier
  }
  ```
- **严重程度**: P1
- **现状**: AT_LEAST_ONCE 模式下，每个 input channel 收到 barrier 时都返回 `Optional.of(barrier)`。如果有 N 个 input channel，N 个 barrier 被发送到下游算子链。每个都触发一次 `processBarrier()` → `snapshotState()` → snapshot callback。单个 checkpoint ID 产生 N 份快照。
- **风险**: (1) Checkpoint 存储膨胀（同一 checkpoint 数据写入 N 次）。(2) 快照回调被调用 N 次，可能触发不正确的状态更新。(3) 下游的 barrier alignment 逻辑可能被 N 个重复 barrier 扰乱。
- **建议**: AT_LEAST_ONCE 模式只在第一个 barrier 到达时发送一次，后续 channel 的 barrier 静默消费。
- **信心水平**: 确定
- **发现来源视角**: 事务边界追踪者

---

## P1：Connector 数据丢失

### [AR-10] BatchConsumerSinkFunction.flush() 在 consumer.consume() 失败时清除缓冲 — 数据永久丢失

- **文件**: `nop-stream-connector/.../connector/BatchConsumerSinkFunction.java:66-78`
- **证据片段**:
  ```java
  private void flush() {
      if (buffer.isEmpty()) { return; }
      try {
          consumer.consume(new ArrayList<>(buffer), chunkContext);
      } finally {
          buffer.clear();  // ← 无论 consume 是否成功，缓冲都被清空
      }
  }
  ```
- **严重程度**: P1
- **现状**: `flush()` 的 `finally` 块无条件执行 `buffer.clear()`。如果 `consumer.consume()` 抛异常，缓冲中的所有记录被永久丢弃。之前的审计（AR-14, Round 8）报告了 `close()` 中 flush 失败后 consumer 清理缺失，但未报告 `flush()` 本身的数据丢失问题——这是更上游的根因。
- **风险**: 任何 consume 失败（数据库超时、网络错误、数据格式异常）导致整批数据永久丢失。默认 batchSize=100 时，每次失败最多丢失 100 条记录。
- **建议**: 仅在 `consume()` 成功后清除缓冲。失败时保留数据以允许重试。
- **信心水平**: 确定
- **发现来源视角**: 事务边界追踪者

---

### [AR-11] DebeziumCdcSourceFunction.draining 标志在 run() 重新调用时不重置 — 恢复后静默无数据

- **文件**: `nop-stream-connector/.../connector/DebeziumCdcSourceFunction.java:32, 59, 69, 104-105`
- **证据片段**:
  ```java
  // 行 32:
  private volatile boolean draining = false;

  // 行 104-105 (truncateForDrain):
  draining = true;

  // 行 59 (run):
  if (!draining) {          // ← 恢复后 draining 仍为 true
      source = new DebeziumMessageSource(config);
      ...
  }

  // 行 69:
  while (running && !draining) {  // ← 恢复后条件为 false，立即退出
  ```
- **严重程度**: P1
- **现状**: `draining` 是实例字段，设置后从不重置。如果 source 函数经过 `truncateForDrain()` → 正常退出 → checkpoint 恢复 → 重新 `run()` 的生命周期，`draining` 仍为 `true`。`run()` 跳过 source 创建并立即退出循环。任务以"成功"状态完成但未处理任何数据。
- **风险**: CDC pipeline 在 graceful drain + checkpoint 恢复场景下静默停止数据摄入。数据丢失且无任何错误信号。
- **建议**: 在 `run()` 方法开始时重置 `draining = false`，或在 `cancel()` 中重置。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

## P2：CEP Pattern API 验证

### [AR-12] Pattern.times(from, to) 不验证 from <= to — 静默创建无效 NFA

- **文件**: `nop-stream-cep/.../pattern/Pattern.java:474-484`
- **证据片段**:
  ```java
  public Pattern<T, F> times(int from, int to, @Nullable Duration windowTime) {
      checkIfNoNotPattern();
      checkIfQuantifierApplied();
      this.quantifier = Quantifier.times(quantifier.getConsumingStrategy());
      if (from == 0) {
          this.quantifier.optional();
          from = 1;
      }
      this.times = Quantifier.Times.of(from, to, windowTime);  // no from <= to check
      return this;
  }
  ```
- **严重程度**: P2
- **现状**: `times(5, 3)` 或 `times(0, 0)`（变为 `times(1, 0)`）静默通过验证。对比 `times(int)` 方法（行 446）验证了 `times > 0`。错误在 `NFACompiler` 阶段以晦涩的方式暴露。
- **建议**: 添加 `if (from > to) throw new IllegalArgumentException(...)` 验证。
- **信心水平**: 确定

---

### [AR-13] Pattern.timesOrMore(int) 不验证 times > 0

- **文件**: `nop-stream-cep/.../pattern/Pattern.java:510-516`
- **严重程度**: P2
- **现状**: `timesOrMore(0)` 或负值被接受，与 `times(int)` 的验证不一致。
- **建议**: 添加 `Preconditions.checkArgument(times > 0)` 验证。
- **信心水平**: 确定

---

### [AR-14] Pattern.allowCombinations() 不验证是否用于循环量词模式

- **文件**: `nop-stream-cep/.../pattern/Pattern.java:531-534`
- **严重程度**: P2
- **现状**: Javadoc 声明 "Applicable only to looping and times patterns" 但无运行时验证。对 SINGLE 模式调用 `allowCombinations()` 静默设置不合法的 quantifier 属性。
- **建议**: 添加 quantifier 类型验证。
- **信心水平**: 很可能

---

## P2：CEP NFAState 比较

### [AR-15] NFAState.STATE_COMPARATOR 使用 DeweyNumber.hashCode() 排序 — hash 碰撞导致等价状态判断不等

- **文件**: `nop-stream-cep/.../nfa/NFAState.java:115-119`
- **证据片段**:
  ```java
  private static final Comparator<ComputationState> STATE_COMPARATOR =
      Comparator.<ComputationState, String>comparing(ComputationState::getCurrentStateName)
          .thenComparing(c -> c.getVersion() == null ? 0 : c.getVersion().hashCode())
          .thenComparingLong(ComputationState::getStartTimestamp)
          .thenComparingLong(ComputationState::getPreviousTimestamp);
  ```
- **严重程度**: P2
- **现状**: `DeweyNumber.hashCode()` 基于 `Arrays.hashCode(int[])`，不是单射函数。两个不同的 DeweyNumber（如 `[1, 0]` 和 `[0, 1]` 可能碰撞）在相同 state name + timestamps 下被 comparator 判断为等价。`NFAState.equals()` 和 `hashCode()` 通过 `sortedCopy()` 使用此 comparator。如果两个 `NFAState` 包含相同 ComputationState 集合但插入顺序不同，TimSort 保留插入序 → `Arrays.equals()` 对两个不同序的数组返回 `false` → 逻辑等价的 `NFAState` 判断不等。
- **风险**: Checkpoint 恢复后 NFA 状态比较失败（如果恢复路径依赖 equals）。单元测试中 NFA 状态断言 flaky。
- **建议**: 使用 DeweyNumber 的字典序比较器替代 hashCode 排序。
- **信心水平**: 很可能

---

## P3：执行引擎辅助

### [AR-16] Task.state=COMPLETED 但 error != null — closeOperatorChains 失败时状态不一致

- **文件**: `nop-stream-core/.../execution/Task.java:159-237`
- **严重程度**: P3
- **现状**: task body 成功（`state = COMPLETED`）后，`closeOperatorChains()` 在 finally 中失败 → `this.error` 被设置。Task 报告 `state=COMPLETED` 但 `getError() != null`。监控逻辑检查 `isFinished()`（COMPLETED 为 true）但可能不检查 error。
- **建议**: close 失败时将状态改为 FAILED，或引入单独的 close-error 字段。
- **信心水平**: 确定

---

### [AR-17] TaskExecutor.submitTask(SubtaskTask) 不加入 submittedTasks — 监控 API 返回不完整

- **文件**: `nop-stream-core/.../execution/TaskExecutor.java:203-217`
- **严重程度**: P3
- **现状**: `submitTask(Task)` 同时加入 `submittedTasks` 和 `taskFutures`，但 `submitTask(SubtaskTask)` 只加入 `taskFutures`。`getAllTasks()`、`getTaskCount()` 等方法只查 `submittedTasks`，不包括 SubtaskTask。
- **建议**: 统一加入两个 map。
- **信心水平**: 确定

---

### [AR-18] GraphExecutionPlan.build 只用第一条 incoming edge 的 EdgeConfig 构建 InputGate

- **文件**: `nop-stream-core/.../execution/GraphExecutionPlan.java:256`
- **严重程度**: P3
- **现状**: union 多输入场景下，所有 channel 合入同一 InputGate，但只用第一条 edge 的 EdgeConfig。异构 edge 配置被忽略。
- **建议**: 多 edge 场景下使用最严格配置或抛出 unsupported 异常。
- **信心水平**: 很可能

---

## 总评

### 最值得关注的 3 个方向

1. **CheckpointCoordinator 的 2PC 协议实现有两个独立致命缺陷**（AR-1 + AR-2）：`retryFailedCommits` 用 `true` 重试本应 `false` 的提交（AR-1）是 2PC 协议的根本性错误，在任何使用 TwoPhaseCommitSinkFunction 的生产 pipeline 中都可能导致数据不一致。`shutdown()` 不通知 participant abort（AR-2）则在每次 coordinator 重启时产生悬挂事务。这两个问题组合意味着 nop-stream 的 exactly-once 语义在分布式模式下不可信。

2. **TaskManager 信号量管理存在双重释放 + 泄漏的对偶问题**（AR-3 + AR-4）：`cancelTask()` 对已运行 task 双重释放（容量膨胀），`updateFencingToken()` 对未运行 task 泄漏许可（永久拒绝）。两个问题的根因是信号量的生命周期管理分散在 `cancelTask()`、`RunningTask.run()` finally、和 `updateFencingToken()` 三个位置，缺乏统一的 owner。这需要系统性重构而非逐个 patch。

3. **WindowAggregationOperator 在三个独立层面有正确性问题**（AR-5 + AR-6 + AR-7）：Long 溢出丢弃所有数据（AR-5）、恢复后合并逻辑失效（AR-6）、watermark 单调性违反（AR-7）。前两个问题在 Round 8+9 中未被报告（AR-5 的溢出路径和 AR-6 的 activeWindowsPerKey 未重建），说明 Window 算子的恢复路径和初始化时序是之前审查的盲区。

### 本次审查的盲区自评

1. **未深入审查 NFA.advanceTime 的超时计算**（AR-4 in CEP agent 报告了 window timeout 的方向性错误，但未验证实际触发频率）。
2. **未运行测试验证任何发现**：AR-5（Long 溢出）和 AR-10（flush 数据丢失）应可通过单元测试快速确认。
3. **未审查 fraud-example 模块的端到端正确性**：FraudDetectionJob 作为示例代码，可能包含误导用户的模式。
4. **未分析 CheckpointCoordinator 的 `notifyCheckpointAborted` 的调用方是否正确处理了 abort 信号**。
5. **未验证 `DebeziumCdcSourceFunction` 的 `source`/`subscription` 非 transient 字段在分布式部署时的实际影响**（上次审查已报告但未修复）。

---

## 按严重程度分布表

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0      | 4    | Checkpoint 2PC 正确性(2) + TaskManager 信号量(2) |
| P1      | 7    | Window 算子正确性(3) + Timer(1) + InputGate(1) + Connector 数据丢失(2) |
| P2      | 4    | CEP Pattern API 验证(3) + NFAState 比较(1) |
| P3      | 3    | 执行引擎辅助(3) |
