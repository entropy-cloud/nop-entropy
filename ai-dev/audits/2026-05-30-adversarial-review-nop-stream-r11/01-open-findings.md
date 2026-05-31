# nop-stream 对抗性审查 — Round 11

> 审查日期：2026-05-30
> 审查范围：nop-stream 全模块（10 个子模块，5 个有代码），开放式发现导向
> 审查方法：4 个并行探索 agent 分别聚焦 (1) 模块结构与依赖关系，(2) Checkpoint/Barrier 分布式协调，(3) CEP NFA/SharedBuffer/Window 算子，(4) Connector/SourceFunction/序列化。然后交叉验证并确认。
> 发现来源视角：异常路径侦探 + 10x 规模运维者 + 事务边界追踪者
> 去重：已阅读以下已有报告，本报告不重复其中已修复内容：
> - `2026-05-20-adversarial-review-nop-stream/`（Round 1+2）
> - `2026-05-22-adversarial-review-nop-stream/`（Round 1+3）
> - `2026-05-24-adversarial-review-nop-stream-r3/`（Round 4）
> - `2026-05-28-adversarial-review-nop-stream/`（Round 5+6）
> - `2026-05-29-adversarial-review-nop-stream/`（Round 7）
> - `2026-05-30-adversarial-review-nop-stream/`（Round 8+9）
> - `2026-05-30-adversarial-review-nop-stream-r10/`（Round 10，AR-1~AR-18）
> - `2026-05-25-deep-audit-nop-stream-full/`、`2026-05-28-deep-audit-nop-stream-full/`、`2026-05-30-deep-audit-nop-stream-full/`

---

## Round 10 修复确认

以下 Round 10 高优先级发现已在当前代码中修复：

| Round 10 编号 | 问题 | 修复确认 |
|---------------|------|---------|
| AR-1 | retryFailedCommits 硬编码 `true` | ✅ 已修复：`checkpointSuccessMap` 记录原始值，重试时使用 `originalSuccess` |
| AR-2 | shutdown() 不通知 participant | ✅ 已修复：现在调用 `notifyParticipantsFinishCommit(false)` + `notifyCheckpointAborted` |
| AR-3 | cancelTask 双重释放信号量 | ✅ 已修复：`AtomicBoolean semaphoreReleased` 确保 exactly-once 释放 |
| AR-4 | updateFencingToken 信号量泄漏 | ✅ 已修复：同上，使用 `semaphoreReleased` 保护 |
| AR-5 | WindowAgg Long 溢出丢弃数据 | ✅ 已修复：`currentWatermark != Long.MIN_VALUE` 守卫 |
| AR-6 | activeWindowsPerKey 未重建 | ✅ 已修复：restoreState 从 windowState 重建 |
| AR-7 | 非前进 watermark 转发 | ✅ 已修复：直接 return，不 emit |
| AR-8 | Timer 同时间戳重注册被丢弃 | ✅ 已修复：snapshot + removeAll 模式 |
| AR-9 | AT_LEAST_ONCE 多次触发快照 | ✅ 已修复：`barrierEmitted` 标记 |
| AR-10 | flush 失败后清空 buffer | ✅ 已修复：`buffer.clear()` 在 try 内 consume 成功后 |
| AR-11 | DebeziumCdcSource draining 不重置 | ✅ 已修复：`run()` 开头重置 `draining = false` |

---

## 新发现

### [AR-1] TaskManager.receiveAssignment 信号量泄漏 — 重复分配导致节点永久不可用

- **文件**: `nop-stream-runtime/.../taskmanager/TaskManager.java:211-222, 235-238`
- **证据片段**:
  ```java
  // 行 211: 获取信号量
  if (!capacitySemaphore.tryAcquire()) { ... return; }
  
  // 行 218-222: 重复任务检查，直接返回不释放
  String taskKey = taskKey(assignment);
  if (runningTasks.containsKey(taskKey)) {
      LOG.warn("Task {} already running, ignoring duplicate assignment", taskKey);
      return;   // ← 信号量已获取但未释放！
  }
  
  // 行 235-238: putIfAbsent 竞态，直接返回不释放
  RunningTask existing = runningTasks.putIfAbsent(taskKey, runningTask);
  if (existing != null) {
      return;   // ← 信号量已获取但未释放！
  }
  ```
- **严重程度**: P1
- **现状**: `tryAcquire()` 在行 211 成功获取信号量许可。如果后续检查发现任务已存在（行 219，coordinator 重试或 failover 场景）或 `putIfAbsent` 发现竞态（行 235），方法直接返回而不释放信号量。每次重复分配泄漏 1 个许可。
- **风险**: coordinator 故障转移或网络重试场景下，N 次重复分配使节点逐渐耗尽所有许可。节点永久拒绝新任务分配，`capacitySemaphore.availablePermits()` 归零，只能通过重启节点恢复。在 HA 配置下 coordinator 重试是常态。
- **建议**: 在两个 early-return 路径中添加 `capacitySemaphore.release()`，或使用 try-finally 模式统一管理信号量获取和释放。
- **信心水平**: 确定
- **发现来源视角**: 10x 规模运维者（HA 场景下的资源管理）

---

### [AR-2] MessageSourceFunction transient CountDownLatch 反序列化后 NPE — 分布式部署启动崩溃

- **文件**: `nop-stream-connector/.../connector/MessageSourceFunction.java:49, 112`
- **证据片段**:
  ```java
  // 行 49: transient 字段，反序列化后为 null
  private transient volatile CountDownLatch shutdownLatch = new CountDownLatch(1);
  
  // 行 112: 直接使用，无 null 检查
  shutdownLatch.await(1, TimeUnit.SECONDS);  // ← NPE after deserialization
  ```
- **严重程度**: P1
- **现状**: `SourceFunction` 继承 `Serializable`（`SourceFunction.java:22`），因此 `MessageSourceFunction` 是可序列化的。`shutdownLatch` 标记为 `transient`，Java 反序列化后字段为 `null`（inline 初始化器 `= new CountDownLatch(1)` 不在反序列化路径中执行）。`run()` 方法直接调用 `shutdownLatch.await()` 导致 NPE。
- **风险**: 任何分布式部署场景（TaskManager 序列化 SourceFunction 并发送到工作节点），MessageSourceFunction 在 `run()` 时立即崩溃。如果该 source 是 pipeline 的数据入口，整个 pipeline 无法启动。
- **建议**: 在 `run()` 方法开头添加 null 检查并重新初始化：
  ```java
  if (shutdownLatch == null) {
      shutdownLatch = new CountDownLatch(1);
  }
  ```
  或参考 DebeziumCdcSourceFunction 的 `initCompletionLatch()` 双重检查锁模式。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探（序列化/反序列化生命周期）

---

### [AR-3] TwoPhaseCommitSinkFunction.finishCommit 迭代 synchronizedMap 未同步 — ConcurrentModificationException

- **文件**: `nop-stream-core/.../functions/sink/TwoPhaseCommitSinkFunction.java:80-90`
- **证据片段**:
  ```java
  // 行 33: synchronizedMap 包装
  this.pendingCommits = Collections.synchronizedMap(new TreeMap<>());
  
  // 行 82: 迭代 entrySet 未持有 map 的 monitor lock
  for (Map.Entry<Long, Object> entry : pending.entrySet()) {  // ← 未 synchronized(pending)
      if (entry.getKey() <= epochId) {
          toCommit.put(entry.getKey(), entry.getValue());
      }
  }
  // 行 87-90: 在第二次循环中修改原始 map
  for (Long eid : toCommit.keySet()) {
      commit(eid);
      pending.remove(eid);   // ← 修改 map
  }
  ```
- **严重程度**: P2
- **现状**: `pendingCommits` 使用 `Collections.synchronizedMap` 包装。`finishCommit` 方法迭代 `pending.entrySet()` 但未在 `synchronized(pending)` 块内执行。根据 `synchronizedMap` 的契约，迭代时必须持有 map 对象的 monitor lock。如果另一个线程（如 checkpoint 超时回调调用 `restoreFromEpoch`）同时修改 `pendingCommits`，迭代器抛出 `ConcurrentModificationException`。
- **风险**: 使用 TwoPhaseCommitSinkFunction 的生产 pipeline 在 checkpoint 完成与超时并发时可能崩溃，导致事务悬挂或数据丢失。
- **建议**: 将 `finishCommit` 中对 `pending` 的所有操作包裹在 `synchronized(pending)` 块中。
- **信心水平**: 确定
- **发现来源视角**: 事务边界追踪者

---

### [AR-4] NFA.advanceTime pending 状态超时后跳过 releaseNode — SharedBuffer 内存泄漏

- **文件**: `nop-stream-cep/.../nfa/NFA.java:289-293`
- **证据片段**:
  ```java
  if (isTimeoutForPreviousEvent || isTimeoutForFirstEvent) {
      nfaState.setStateChanged();
      if (getState(computationState).isPending()) {
          potentialMatches.add(computationState);
          continue;   // ← 跳过下面的 releaseNode 调用
      }
      // ... 非_pending_路径正常处理超时 ...
      sharedBufferAccessor.releaseNode(
              computationState.getPreviousBufferEntry(), computationState.getVersion());
  }
  ```
- **严重程度**: P2
- **现状**: 当 computation state 处于 pending 状态并超时时，它被加入 `potentialMatches` 然后通过 `continue` 跳过了 `sharedBufferAccessor.releaseNode()` 调用（行 312-313）。后续 `processMatchesAccordingToSkipStrategy` 处理这些 pending 匹配时，如果 after-match skip strategy 剪枝了该匹配（行 457-460 的逻辑），其 SharedBuffer 条目永远不会被释放。
- **风险**: 使用 after-match skip strategy 的 CEP pattern（如 `skipPastLast`）在存在超时 pending 状态的场景下，SharedBuffer 条目逐渐累积。长时间运行的 CEP pipeline 内存使用量持续增长，最终 OOM。
- **建议**: 在 `processMatchesAccordingToSkipStrategy` 中，当 pending 匹配被剪枝时，显式调用 `sharedBufferAccessor.releaseNode()` 释放其 buffer 条目。
- **信心水平**: 很可能（pending 状态超时需要特定的事件时序，且取决于 skip strategy 是否剪枝）
- **发现来源视角**: 异常路径侦探

---

### [AR-5] WindowAggregationOperator merge 后源窗口 trigger state 未清理 — session window 内存增长

- **文件**: `nop-stream-core/.../operators/WindowAggregationOperator.java:319-323, 491-497`
- **证据片段**:
  ```java
  // 行 319-323: 合并时注销了 timer 但未清理 trigger state
  if (!sourceWindow.equals(mergedWindow)) {
      TriggerContextImpl sourceCtx = new TriggerContextImpl(key, sourceWindow);
      unregisterEventTimeTimersForWindow(key, sourceWindow);  // 清理 timer
      activeWindows.remove(sourceWindow);                      // 从活跃集合移除
      // NOTE: sourceWindow 的 triggerState 未清理！
  }
  
  // 行 491-497: purgeWindow 只查找 mergedWindow 的 key
  Iterator<TriggerStateKey<K, W>> it = triggerState.keySet().iterator();
  while (it.hasNext()) {
      TriggerStateKey<K, W> k = it.next();
      if (k.windowKey.equals(wk)) {  // wk 是 merged window，不是 source window
          it.remove();
      }
  }
  ```
- **严重程度**: P2
- **现状**: session window 合并时，`processElementWithMerging` 正确注销了源窗口的 event-time timer 并从 `activeWindows` 移除，但未清理 `triggerState` map 中源窗口的触发器累积状态（如 CountTrigger 的计数器）。`purgeWindow` 只查找 merged window 的 key，无法清理源窗口的 trigger state。
- **风险**: 高频 session window 合并场景下，trigger state 持续增长。每个被合并的源窗口遗留一个 trigger state 条目。在 CEP 检测或高频事件流中，可产生大量短命 session window，trigger state 增长速度与事件吞吐量成正比。
- **建议**: 在 `processElementWithMerging` 的源窗口清理代码中（行 319-323），添加 trigger state 清理：
  ```java
  // 清理源窗口的 trigger state
  Iterator<TriggerStateKey<K, W>> tsIt = triggerState.keySet().iterator();
  while (tsIt.hasNext()) {
      TriggerStateKey<K, W> tsKey = tsIt.next();
      if (tsKey.windowKey.key.equals(key) && tsKey.windowKey.window.equals(sourceWindow)) {
          tsIt.remove();
      }
  }
  ```
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探（恢复路径完整性）

---

### [AR-6] Lockable.release() TOCTOU — ref counter 检查与递减非原子

- **文件**: `nop-stream-cep/.../nfa/sharedbuffer/Lockable.java:54-59`
- **证据片段**:
  ```java
  boolean release() {
      if (refCounter.get() <= 0) {
          return true;   // ← 检查
      }
      return refCounter.decrementAndGet() == 0;  // ← 递减（非原子的检查-递减）
  }
  ```
- **严重程度**: P3
- **现状**: `refCounter.get() <= 0` 检查和 `refCounter.decrementAndGet()` 是两个独立的原子操作，但组合后不保证原子性。在并发访问下，另一个线程可能在检查和递减之间调用 `lock()`（incrementAndGet），导致 ref counter 变为负值。
- **风险**: 当前 CEP 处理是单线程的（CepOperator 顺序处理元素），因此此竞态在现有架构下不会触发。但代码注释（行 28-31）声明了线程安全意图，如果未来引入并行 CEP 处理或异步 timer 回调，此问题会显现。
- **建议**: 使用 CAS 循环替代 check-then-act 模式：
  ```java
  boolean release() {
      int old;
      do {
          old = refCounter.get();
          if (old <= 0) return true;
      } while (!refCounter.compareAndSet(old, old - 1));
      return old == 1;
  }
  ```
- **信心水平**: 确定（代码缺陷明确，但当前无运行时影响）
- **发现来源视角**: 异常路径侦探

---

### [AR-7] GraphExecutionPlan.build 只使用第一条 OperatorChain — 多 chain 顶点静默丢失逻辑

- **文件**: `nop-stream-core/.../execution/GraphExecutionPlan.java:187-189`
- **证据片段**:
  ```java
  OperatorChain chain = taskIndex == 0
          ? original.getOperatorChains().get(0)  // ← 只取第一条
          : original.getOperatorChains().get(0).deepCopy();
  ```
- **严重程度**: P3
- **现状**: 当前实现只使用 `getOperatorChains().get(0)`。如果 `JobVertex` 有多条 chain（`getOperatorChains()` 返回长度 > 1 的列表），额外的 chain 被静默丢弃。在现有代码路径中，`JobGraphGenerator` 为每个顶点创建单条 chain，因此此问题不会触发。
- **风险**: 如果未来引入多 chain 顶点支持（如分支或 side output），此限制会导致逻辑丢失且无任何错误信号。调试困难。
- **建议**: 添加防御性检查：如果 `getOperatorChains().size() > 1`，抛出 `UnsupportedOperationException` 或至少记录 WARNING 日志。
- **信心水平**: 确定

---

### [AR-8] CheckpointCoordinator.shutdown() 不设置 pending checkpoint 状态为 ABORTED

- **文件**: `nop-stream-runtime/.../checkpoint/CheckpointCoordinator.java:473-478`
- **证据片段**:
  ```java
  for (PendingCheckpoint pending : pendingCheckpoints.values()) {
      long checkpointId = pending.getCheckpointId();
      notifyParticipantsFinishCommit(checkpointId, false);
      notifyCheckpointAborted(checkpointId);
      pending.dispose();  // ← 不设置 status = ABORTED
  }
  ```
- **严重程度**: P3
- **现状**: `shutdown()` 正确通知了 participants 和 listeners（这是 Round 10 AR-2 的修复），但调用 `dispose()` 而非 `abort()`。`dispose()` 清理集合并取消 future，但 `status` 保持为 `RUNNING`。如果有代码在 shutdown 后检查 `pending.getStatus()`，会看到误导性的 `RUNNING` 状态。
- **风险**: 纯粹的状态一致性问题。当前 shutdown 后 coordinator 不再使用，无实际功能影响。但在调试 shutdown 问题时可能造成混淆。
- **建议**: 将 `pending.dispose()` 替换为 `pending.abort("Shutdown")`，或先 abort 再 dispose。
- **信心水平**: 确定

---

## 总评

### 最值得关注的 1 个方向

**TaskManager 的信号量生命周期管理仍有盲区。** Round 10 修复了 `cancelTask` 和 `updateFencingToken` 的双重释放/泄漏问题，引入了 `AtomicBoolean semaphoreReleased` 作为统一保护。但 `receiveAssignment` 的两个 early-return 路径（重复任务检查和 `putIfAbsent` 竞态）未覆盖此保护。这表明信号量管理的设计模式是 patch-by-case 而非系统性设计。建议将信号量获取/释放统一为 try-finally 模式，确保所有 exit 路径都正确释放。

### 本次审查的盲区自评

1. **未运行测试验证任何发现**：AR-1（信号量泄漏）和 AR-2（NPE）应可通过单元测试快速确认。
2. **未深入审查 CheckpointBarrierTracker.triggerCheckpoint 的 checkpoint ID 消耗问题**：当 barrier 被拒绝时 ID 已分配但无 barrier 注入，可能产生 zombie checkpoint。初步分析认为影响有限（zombie 会超时并被 abort），但未完整追踪所有路径。
3. **未审查 RecordWriter.emitElement 在 PartitionRouter 模式下的广播行为**：当 `partitioner == null` 但 `partitionRouter != null` 时，broadcast element（watermark、barrier）只发送到 `partitions[0]`。如果 watermark 传播依赖此路径，下游可能丢失 watermark 更新。但这需要追踪完整的 watermark 传播链路来确认。
4. **未检查 fraud-example 模块的端到端正确性**。
5. **未验证 CepOperator.onEventTime 中"仅剩 1 个 partial match"时清空 NFA 状态后的 DeweyNumber 版本重置问题**。

---

## 按严重程度分布表

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0      | 0    | —       |
| P1      | 2    | 信号量泄漏(1) + 反序列化 NPE(1) |
| P2      | 3    | 并发安全(1) + CEP 内存泄漏(1) + Window 内存泄漏(1) |
| P3      | 3    | 原子性(1) + 防御编码(1) + 状态一致性(1) |
