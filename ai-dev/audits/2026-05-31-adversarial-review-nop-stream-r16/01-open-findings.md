# nop-stream 对抗性审查 — Round 16

> 审查日期：2026-05-31
> 审查范围：nop-stream 全模块（5 个活跃子模块，340 个 Java 源文件），开放式发现导向
> 审查方法：4 个并行探索 agent 分别覆盖 (1) connector + state backend + TwoPhaseCommitSinkFunction，(2) CEP/NFA/SharedBuffer，(3) runtime/execution/transport/cluster，(4) core/operators/graph/datastream。然后对 13 个关键发现逐一在源码中验证（确认/推翻）。
> 发现来源视角：异常路径侦探 + 10x 规模运维者 + 新人开发者 + 未来破坏者
> 去重：已阅读以下已有报告，本报告不重复其中已修复或已报告内容：
> - `2026-05-22-adversarial-review-nop-stream/` ~ `2026-05-31-adversarial-review-nop-stream-r9/`（Round 1~15）
> - `2026-05-25-deep-audit-nop-stream-full/` ~ `2026-05-31-deep-audit-nop-stream-full/`

---

## 前轮已知未修复问题（简要引用）

以下之前报告的问题在当前代码中确认**仍然存在**（简要引用，重点补充现状变化）：

| 轮次/编号 | 问题 | 状态 |
|-----------|------|------|
| R15-AR-1 | CheckpointBarrierTracker 永久死锁 | ⚠️ 仍存在 |
| R15-AR-2 | InputGate 静默丢弃已对齐 barrier | ⚠️ 仍存在 |
| R15-AR-3 | WindowAggregationOperator 不调 trigger.onMerge() | ⚠️ 仍存在 |
| R15-AR-4 | ChainingOutput 静默丢弃 side-output | ⚠️ 仍存在 |
| R15-AR-5 | registerTasksAndTrackers tracker 覆盖 + index 错位 | ⚠️ 仍存在 |
| R14-AR-1 | WindowAggregationOperator serializeTriggerState `:` 分隔符 | ⚠️ 仍存在 |
| R8-AR-55 | RemoteGraphExecutionPlanBuilder 只为 taskIndex==0 注册 | ⚠️ 仍存在 |
| R8-AR-58 | WindowAggregationOperator resolveKey isInstance 方向反转 | ⚠️ 仍存在 |
| R8-AR-61 | InputGate 200 轮空循环返回 end-of-stream | ⚠️ 仍存在 |
| R8-AR-63 | StateTransition.equals() 不比较 condition | ⚠️ 仍存在 |
| R8-AR-64 | RemoteInputChannel.close() queue.offer() 满队列丢失 | ⚠️ 仍存在 |
| R8-AR-66 | CepOperator timestamp==currentWatermark 丢弃事件 | ⚠️ 仍存在 |
| R13-AR-3 | CheckpointCoordinator participant index 腐败 | ⚠️ 仍存在 |
| R13-AR-5 | WindowAggregationOperator `#` 分隔符 | ⚠️ 仍存在 |
| R13-AR-6 | WindowOperator mergeWindowContents 非累加器覆写 | ⚠️ 仍存在 |

---

## 新发现

### [AR-1] TwoPhaseCommitSinkFunction.saveState() 无锁遍历 synchronizedMap — checkpoint 并发时 CME 崩溃

- **文件**: `nop-stream-core/.../functions/sink/TwoPhaseCommitSinkFunction.java:66-71`
- **证据片段**:
  ```java
  // line 34: 构造函数
  this.pendingCommits = Collections.synchronizedMap(new TreeMap<>());

  // line 66-71: saveState() — 无 synchronized(pendingCommits)
  public TaskStateSnapshot saveState(long epochId) throws Exception {
      TaskStateSnapshot snapshot = new TaskStateSnapshot(new TaskLocation(), epochId);
      Map<Long, Object> copy = new TreeMap<>(pendingCommits);  // ← 遍历未持锁
      snapshot.putOperatorState(PENDING_COMMITS_KEY, copy);
      return snapshot;
  }

  // 对比 finishCommit() 正确持锁:
  synchronized (pending) {  // line 87
      toCommit = new TreeMap<>();
      for (Map.Entry<Long, Object> entry : pending.entrySet()) { ... }
  }
  ```
- **严重程度**: P0
- **现状**: `pendingCommits` 是 `Collections.synchronizedMap`。按其契约，遍历 entrySet 必须在 `synchronized(pendingCommits)` 块内。`new TreeMap<>(pendingCommits)` 内部调用 `entrySet().iterator()`。如果 `finishCommit()` 并发执行 `put`/`remove`（通过另一个线程），触发 `ConcurrentModificationException`，**checkpoint barrier 处理崩溃**。
- **风险**: Checkpoint 发生在定时器/独立线程。如果 commit 和 snapshot 竞争，checkpoint 失败。流式作业丧失 savepoint 能力，可能重启或丢失状态。
- **建议**: 在 `saveState()` 中用 `synchronized (pendingCommits)` 包裹遍历。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探（并发契约违反）

---

### [AR-2] WindowedStreamImpl.allowedLateness() 完全无效 — late-data API 是死代码

- **文件**: `nop-stream-core/.../datastream/WindowedStreamImpl.java:127-130, 141-168`
- **证据片段**:
  ```java
  // line 40: 存储
  private long allowedLateness = 0L;

  // line 127-130: setter
  public WindowedStreamImpl<T, K, W> allowedLateness(long allowedLateness) {
      this.allowedLateness = allowedLateness;
      return this;
  }

  // line 151-158: aggregate() — 未传递
  WindowAggregationOperator<...> operator =
      new WindowAggregationOperator<>(assigner, trigger, aggFn, keyedStream.getKeySelector());
  // ← allowedLateness 未传递，未设置

  // WindowAggregationOperator.java:55 — 默认值 0
  private long allowedLateness = 0;

  // WindowAggregationOperator.java:247 — 用 0 做判断
  if (timestamp < currentWatermark - allowedLateness) {
      LOG.debug("Dropping late element...");
      return;  // ← 永远用 0，用户设置值被忽略
  }
  ```
- **严重程度**: P1
- **现状**: `WindowedStreamImpl` 提供了 `allowedLateness()` setter，但三个聚合方法（`apply()`/`aggregate()`/`reduce()`）创建 `WindowAggregationOperator` 时均未传递该值。`WindowAggregationOperator` 有 `setAllowedLateness()` setter 但从未被调用。操作符始终使用默认值 0。
- **风险**: 用户调用 `windowedStream.allowedLateness(5000)` 后仍无 late-element 容忍。元素在 watermark 通过后立即丢弃。API 契约与实际行为不一致，数据静默丢失且极难发现。
- **建议**: 在三个聚合方法中创建 operator 后调用 `operator.setAllowedLateness(this.allowedLateness)`。
- **信心水平**: 确定
- **发现来源视角**: 新人开发者（API 承诺了但实际不工作）

---

### [AR-3] HeapInternalTimerService.advanceWatermark() 先移除所有同时间戳 timer 再触发 — deleteEventTimeTimer 在回调中是 no-op

- **文件**: `nop-stream-core/.../operators/HeapInternalTimerService.java:113-127, 72-81`
- **证据片段**:
  ```java
  // advanceWatermark(), line 113-127:
  for (Map.Entry<Long, Set<TimerEntry<N>>> entry : toFire) {
      Set<TimerEntry<N>> originalSet = eventTimeTimers.get(entry.getKey());
      List<TimerEntry<N>> timersToFire = new ArrayList<>(originalSet);
      originalSet.removeAll(timersToFire);  // line 118: 先全部移除

      for (TimerEntry<N> timer : timersToFire) {
          triggerable.onEventTime(...);      // line 121: 再触发
      }
  }

  // deleteEventTimeTimer(), line 72-81:
  public void deleteEventTimeTimer(N namespace, long time) {
      Set<TimerEntry<N>> timers = eventTimeTimers.get(time);  // set 已被清空
      if (timers != null) {
          timers.remove(new TimerEntry<>(...));  // no-op
      }
  }
  ```
- **严重程度**: P1
- **现状**: `advanceWatermark()` 在触发任何 timer 之前，将同一时间戳的所有 timer 从 set 中移除。在 `onEventTime` 回调中，`deleteEventTimeTimer()` 查找已清空的 set，删除操作无效。这打破了 Flink 标准模式：trigger 回调中删除自己的 timer（例如 `EventTimeTrigger.clear()` 调用 `deleteEventTimeTimer`）。
- **风险**: 窗口操作符中，trigger 回调无法取消同时间戳的其他 timer。一个本应被 `trigger.clear()` 取消的 timer 仍然触发，导致窗口重复触发或提前触发。与 Flink 行为不一致（Flink 逐个从优先队列取出并触发，允许回调取消尚未触发的同时间戳 timer）。
- **建议**: 改为逐个从 set 中取出并触发，而非批量快照后移除全部再逐个触发。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探（timer 生命周期语义）

---

### [AR-4] WindowAggregationOperator 合并路径将当前元素加入每个合并目标 — 多目标时重复计数

- **文件**: `nop-stream-core/.../operators/WindowAggregationOperator.java:320-349`
- **证据片段**:
  ```java
  for (Tuple2<Collection<W>, W> mergeOp : mergeTargets) {
      Collection<W> toBeMerged = mergeOp.f0;
      W mergedWindow = mergeOp.f1;

      ACC mergedAcc = aggregationFunction.createAccumulator();
      mergedAcc = aggregationFunction.add(value, mergedAcc);  // line 326: 每个目标都加一次

      for (W sourceWindow : toBeMerged) {
          ACC sourceAcc = windowState.remove(sourceWk);
          if (sourceAcc != null) {
              mergedAcc = aggregationFunction.merge(mergedAcc, sourceAcc);
          }
      }
      windowState.put(mergedWk, mergedAcc);
  }
  ```
- **严重程度**: P1
- **现状**: 当 `MergingWindowAssigner.mergeWindows()` 返回多个合并目标（N > 1），当前元素的值被 `add()` 到**每个**合并目标的 accumulator 中。元素被计数 N 次。
- **风险**: 对于产生多个独立合并操作的 `MergingWindowAssigner`（如重叠 session window），聚合结果不正确（过度计数）。数据正确性违约。
- **建议**: 检查 `mergeTargets` 的大小；如果元素只属于其中一个合并目标，应只在该目标的 accumulator 中添加。或在合并完成后统一处理当前元素。
- **信心水平**: 确定（需确认是否有 MergingWindowAssigner 实际返回多目标）
- **发现来源视角**: 异常路径侦探（窗口合并语义）

---

### [AR-5] CheckpointIDCounter 恢复后不更新 — 恢复后 checkpoint ID 倒退

- **文件**: `nop-stream-runtime/.../execution/GraphModelCheckpointExecutor.java:71, 125, 587-626`
- **证据片段**:
  ```java
  // line 71 / 125: 每次执行都从 0 开始
  CheckpointIDCounter idCounter = new CheckpointIDCounter();

  // line 587-626: restoreFromCheckpoint() 恢复数据但从未更新 idCounter
  private static void restoreFromCheckpoint(...) throws Exception {
      // ... 恢复 task states ...
      // ← 没有: idCounter.set(restoredCheckpointId + 1);
  }
  ```
- **严重程度**: P1
- **现状**: 恢复 checkpoint ID=5 的检查点后，coordinator 生成新的 checkpoint ID 0, 1, 2...。这些 ID 与存储中旧的 checkpoint 文件冲突。`LocalFileCheckpointStorage` 使用 `{checkpointId}.checkpoint` 命名，新 ID 会覆写旧文件。`cleanupOldCheckpoints` 按降序 ID 排序，保留旧的高 ID 文件、删除新的低 ID 文件——与期望相反。
- **风险**: 恢复后 checkpoint 文件被错误覆写/清理。如果再次恢复，可能恢复到旧 epoch 的过时状态。与 AR-7 (LocalFileCheckpointStorage 按文件名 ID 排序) 形成级联效应。
- **建议**: 恢复后设置 `idCounter.set(restoredCheckpointId + 1)`。
- **信心水平**: 确定
- **发现来源视角**: 10x 规模运维者（恢复后状态一致性）

---

### [AR-6] CepOperator.onEventTime() 在 partialMatches.size()==1 时清空全部状态但不验证是否为 start state

- **文件**: `nop-stream-cep/.../operator/CepOperator.java:384-387`
- **证据片段**:
  ```java
  // In order to remove dangling partial matches.
  if (nfaState.getPartialMatches().size() == 1
          && nfaState.getCompletedMatches().isEmpty()) {
      computationStates.clear();  // ← 清空一切，不验证是否为 start state
  }
  ```
- **严重程度**: P1
- **现状**: 代码假设"只剩 1 个 partial match + 0 个 completed match"意味着该 match 一定是 start state。但未验证。场景：(1) `within(Time)` 窗口超时后只剩一个活跃 match；(2) `afterMatchSkipStrategy` 裁剪后只剩一个非 start match。清空后，该 match 被丢弃，SharedBuffer 中对应条目永不释放（内存泄漏）。
- **风险**: 有效进行中的 partial match 被静默丢弃。CEP 模式匹配漏报。
- **建议**: 检查剩余 match 是否确实是 start state（如 `NFAState.getStartState()`），而非盲目清空。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探（NFA 状态管理不变量）

---

### [AR-7] DeweyNumber.increase() int 溢出 — 高吞吐分支模式下版本号静默损坏

- **文件**: `nop-stream-cep/.../nfa/DeweyNumber.java:110-115`
- **证据片段**:
  ```java
  public DeweyNumber increase(int times) {
      int[] newDeweyNumber = Arrays.copyOf(deweyNumber, deweyNumber.length);
      newDeweyNumber[deweyNumber.length - 1] += times;  // ← int += int, 无溢出检查
      return new DeweyNumber(newDeweyNumber);
  }
  ```
- **严重程度**: P1
- **现状**: `increase()` 使用 int 加法无溢出检查。在 `NFA.computeNextStates()` 中，`times` 可以是 `totalTakeBranches - 1` 或更大。对于 `followedByOne("b").oneOrMore().followedByAny("c")...` 类模式，持续高吞吐处理会产生组合分支。约 2^31 次增量后，int 回绕为负值。
- **风险**: 负值导致 `isCompatibleWith()` 比较结果错误：不兼容版本被视为兼容（错误匹配）或兼容版本被视为不兼容（丢失匹配）。`releaseNode()` 可能释放错误边，导致 use-after-free。
- **建议**: 使用 `long` 或添加溢出检查（如 `Math.addExact`）。
- **信心水平**: 很可能（需要极端高吞吐场景才触发，但在长时间运行的生产作业中完全可能）
- **发现来源视角**: 10x 规模运维者（长时间运行下的边界条件）

---

### [AR-8] Lockable.release() 在 refCounter 已为 0 时返回 true — 静默双重释放

- **文件**: `nop-stream-cep/.../nfa/sharedbuffer/Lockable.java:54-63`
- **证据片段**:
  ```java
  boolean release() {
      int old;
      do {
          old = refCounter.get();
          if (old <= 0) {
              return true;    // ← "安全移除"信号，但计数器已耗尽
          }
      } while (!refCounter.compareAndSet(old, old - 1));
      return old == 1;
  }
  ```
- **严重程度**: P1
- **现状**: 当 `refCounter` 已为 0（或因先前 bug 变为负数），`release()` 返回 `true`，告诉调用者"没有更多引用，可以安全移除"。调用者（`releaseNode()`）随后移除 SharedBuffer 条目并级联删除关联事件。如果有 bug 导致额外 `release()` 调用（如 timeout 清理 + 正常 match 完成同时释放同一节点），条目被移除时其他 computation state 仍在引用它。
- **风险**: SharedBuffer 条目被过早移除，后续 `getEntry()` 返回 null → NPE 或静默丢失模式匹配。无 fail-fast 行为，极难追溯根因。
- **建议**: 当 `refCounter <= 0` 时至少记录 WARN 日志或抛出断言错误。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探（引用计数语义）

---

### [AR-9] JdbcClusterRegistry.registerNode() 设置 lease_expire_at=0 — 新节点注册后不可见

- **文件**: `nop-stream-runtime/.../cluster/JdbcClusterRegistry.java:105-108, 165-168`
- **证据片段**:
  ```java
  // registerNode, line 105-108:
  "INSERT INTO " + NODE_TABLE +
      " (..., lease_expire_at) VALUES (?,?,?,?,?,?)",
      nodeId, endpoint, capacity, now, now, 0L);  // ← 0L

  // getActiveNodes, line 165-168:
  "SELECT ... FROM " + NODE_TABLE +
      " WHERE lease_expire_at > ?", now);  // ← 0 > now 永远为 false
  ```
- **严重程度**: P1
- **现状**: 新注册节点的 `lease_expire_at` 为 0。`getActiveNodes()` 过滤 `lease_expire_at > now`，新节点不可见，直到第一次 `renewLease()` 心跳（默认 5 秒后）。如果 `JobCoordinator.assignTasks()` 在此窗口内运行，新节点被排除，降低并行度。
- **风险**: 与 `InMemoryClusterRegistry` 语义不一致（in-memory 版注册即可见）。分布式部署中节点注册后短暂不可用。
- **建议**: `registerNode()` 中设置 `lease_expire_at = now + defaultLeaseTimeoutMs`，或在注册完成后立即调用 `renewLease()`。
- **信心水平**: 确定
- **发现来源视角**: 10x 规模运维者（集群生命周期一致性）

---

### [AR-10] CheckpointCoordinator.checkpointSuccessMap 无限增长 — 内存泄漏

- **文件**: `nop-stream-runtime/.../checkpoint/CheckpointCoordinator.java:426-427, 465`
- **证据片段**:
  ```java
  // line 426-427:
  private void notifyParticipantsFinishCommit(long checkpointId, boolean success) {
      checkpointSuccessMap.put(checkpointId, success);  // ← 每个 checkpoint 都加一条
      ...
  }

  // line 465: 仅清理重试成功的失败 epoch
  if (stillFailing.isEmpty()) {
      it.remove();
      checkpointSuccessMap.remove(failedEpoch);  // 只清理失败 epoch
  }
  ```
- **严重程度**: P1
- **现状**: 成功提交的 checkpoint（常态）条目永不移除。10 秒间隔 → ~8,600 条/天。`ConcurrentHashMap<Long, Boolean>` 每条占 ~100 bytes（boxed Long + Boolean + HashMap.Entry 开销）。长期运行作业的堆内存持续增长。
- **风险**: 长期运行流式作业（数周/数月）的内存泄漏，最终 OOM。`failedCommitParticipants`（`ConcurrentSkipListMap<Long, Set<...>>`）有类似问题。
- **建议**: 为 `checkpointSuccessMap` 添加有界保留窗口（如只保留最近 N 个 epoch），或在 `retryFailedCommits()` 完成后清理成功条目。
- **信心水平**: 确定
- **发现来源视角**: 10x 规模运维者（长时间运行资源管理）

---

### [AR-11] TwoPhaseCommitSinkFunction.setPendingCommits() 接受任意 Map — 线程安全保证被静默破坏

- **文件**: `nop-stream-core/.../functions/sink/TwoPhaseCommitSinkFunction.java:61-63`
- **证据片段**:
  ```java
  public void setPendingCommits(Map<Long, Object> pending) {
      this.pendingCommits = pending;  // ← 接受任何 Map，非 volatile
  }
  ```
- **严重程度**: P1
- **现状**: 构造函数创建 `synchronizedMap`，但 `setPendingCommits()` 接受任意 Map。后续 `finishCommit()` 和 `restoreFromEpoch()` 中的 `synchronized(pending)` 块同步的是传入的 Map 对象。如果恢复代码传入非同步 Map，所有线程安全保证失效。`pendingCommits` 非 volatile，其他线程可能看不到更新（JMM 可见性问题）。
- **风险**: 恢复路径中 2PC 状态的竞态条件。与 AR-1 (saveState 无锁遍历) 形成级联。
- **建议**: `setPendingCommits()` 内部包装 `Collections.synchronizedMap()`，或将字段声明为 `volatile`。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探（恢复路径中的线程安全退化）

---

### [AR-12] NFA 无上界计算状态 — 组合分支模式下 OOM

- **文件**: `nop-stream-cep/.../nfa/NFA.java:348-425`
- **证据片段**:
  ```java
  for (ComputationState computationState : nfaState.getPartialMatches()) {
      final Collection<ComputationState> newComputationStates =
              computeNextStates(sharedBufferAccessor, computationState, event, timerService);
      newPartialMatches.addAll(statesToRetain);  // ← 无容量限制
  }
  ```
- **严重程度**: P1
- **现状**: 对每个入事件，NFA 遍历所有 partial match 并可创建多个新状态。`followedByAny` + `oneOrMore` + 链式组合下，partial match 数量指数增长。无上限、无反压机制。
- **风险**: 高基数或对抗性输入下 OOM 崩溃，无优雅降级。
- **建议**: 添加可配置的 partial match 上限，超限时丢弃最旧 match 或拒绝新事件。
- **信心水平**: 确定（Flink 生产环境也有此问题，但通常通过 `within` 时间窗口和 `afterMatchSkipStrategy` 限制）
- **发现来源视角**: 10x 规模运维者（资源耗尽）

---

### [AR-13] WatermarkOutputMultiplexer.unregisterOutput() 不重算 combined watermark — watermark 停滞

- **文件**: `nop-stream-core/.../eventtime/WatermarkOutputMultiplexer.java:98-106`
- **证据片段**:
  ```java
  public boolean unregisterOutput(String id) {
      final PartialWatermark output = watermarkPerOutputId.remove(id);
      if (output != null) {
          combinedWatermarkStatus.remove(output);
          return true;  // ← 没有 updateCombinedWatermark()!
      }
      return false;
  }

  // 对比 markIdle():
  public void markIdle() {
      state.setIdle(true);
      updateCombinedWatermark();  // ← 正确触发重算
  }
  ```
- **严重程度**: P2
- **现状**: 移除 output 后不调用 `updateCombinedWatermark()`。如果被移除的 output 持有最低 watermark（拖慢了 combined watermark），移除后 combined watermark 不会前进。
- **风险**: 源分区关闭后 watermark 停滞，直到下一次 `onPeriodicEmit()`。如果未配置周期发射或间隔很长，下游操作符永远看不到 watermark 推进。
- **建议**: 在 `unregisterOutput()` 中添加 `updateCombinedWatermark()` 调用。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探（watermark 生命周期管理）

---

### [AR-14] OperatorChain.processElement() 广播而非链式传递 — 类型变换链会崩溃

- **文件**: `nop-stream-core/.../jobgraph/OperatorChain.java:102-122`
- **证据片段**:
  ```java
  public void processElement(StreamRecord<?> record) {
      for (StreamOperator<?> operator : operators) {
          if (operator instanceof Input) {
              Input input = (Input) operator;
              input.processElement(record);  // ← 同一 record 发给每个 operator
          }
      }
  }
  ```
- **严重程度**: P2
- **现状**: 将**相同** `StreamRecord<?>` 传给链中每个 operator，忽略了 Output 接线（`ChainingOutput`/`KeyExtractingOutput`）。类型变换链（如 `Map<String,Integer> → Filter<Integer>`）中 Filter 收到的是 `StreamRecord<String>` → `ClassCastException`。正常执行路径（`StreamTaskInvokable.wireOperators()`）正确链接，但 `OperatorChain.processElement()` 是一条并行代码路径。
- **风险**: 如果有任何代码调用此方法（测试、替代执行路径），产生静默数据腐败或异常。同时跳过 key-context 设置（`KeyExtractingOutput`），破坏 keyed 操作符。
- **建议**: 标记为 `@Deprecated` 并在 Javadoc 中警告，或修复为正确链式传递。
- **信心水平**: 确定
- **发现来源视角**: 新人开发者（两条 processElement 路径的语义不一致）

---

### [AR-15] LocalFileCheckpointStorage.getLatestCheckpoint() 按文件名 ID 排序 — 恢复后 ID 倒退时选错 checkpoint

- **文件**: `nop-stream-runtime/.../checkpoint/storage/LocalFileCheckpointStorage.java:100-105`
- **证据片段**:
  ```java
  Optional<Path> latest = files
      .filter(p -> p.toString().endsWith(CHECKPOINT_SUFFIX))
      .max((a, b) -> Long.compare(
          extractCheckpointId(a.getFileName().toString()),
          extractCheckpointId(b.getFileName().toString())));
  ```
- **严重程度**: P2
- **现状**: 与 AR-5 级联：恢复后 `idCounter` 从 0 开始，新 checkpoint 文件 `0.checkpoint`、`1.checkpoint` 与旧文件 `5.checkpoint` 共存。`getLatestCheckpoint()` 选择 `5.checkpoint`（ID 更大），但那是**前一次执行 epoch** 的旧 checkpoint。下次恢复时恢复到过时状态。
- **风险**: 静默状态回退。独立来看（如果 AR-5 被修复）此问题影响较小，但在当前状态下两者级联。
- **建议**: 修复 AR-5（确保 ID 单调递增），或改用文件修改时间排序。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探（恢复路径一致性）

---

### [AR-16] GraphModelCheckpointExecutor CANCEL 模式触发最终 checkpoint 但不等完成 — 浪费的 barrier

- **文件**: `nop-stream-runtime/.../execution/GraphModelCheckpointExecutor.java:291-296, 499-512`
- **证据片段**:
  ```java
  // line 291-294:
  case CANCEL:
  default:
      triggerFinalCheckpoint(allInvokables, coordinator);
      break;  // → 继续 shutdown

  // line 499-512:
  private static void triggerFinalCheckpoint(...) {
      PendingCheckpoint finalPending = coordinator.tryTriggerPendingCheckpoint(...);
      if (finalPending != null) {
          triggerBarrierOnAllInvokables(allInvokables, finalPending);
          // 没有 .get(), 没有 wait
      }
  }
  ```
- **严重程度**: P2
- **现状**: CANCEL 模式触发最终 checkpoint（向 operator 注入 barrier），但立即调用 `shutdown()` 释放 `PendingCheckpoint` 并取消其 future。Operator 可能已开始快照状态但被中断。不一致：要么跳过最终 checkpoint，要么等待完成。
- **风险**: 误导日志（"Triggered checkpoint X" 但立即被丢弃），浪费 operator 快照工作。
- **建议**: CANCEL 模式中移除 `triggerFinalCheckpoint` 调用，或添加有界等待。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探（任务生命周期管理）

---

### [AR-17] JobCoordinator.globalRecovery() 部分失败导致协调器处于分裂状态

- **文件**: `nop-stream-runtime/.../coordinator/JobCoordinator.java:393-437`
- **证据片段**:
  ```java
  public void globalRecovery() {
      String newToken = UUID.randomUUID().toString();
      fencingToken.getAndSet(newToken);  // token 已更新
      taskAssignmentMap.clear();         // 旧分配已清除
      allTaskLocations.clear();          // 旧位置已清除
      assignTasks();  // ← 可能抛 StreamException
  }
  ```
- **严重程度**: P2
- **现状**: 如果 `assignTasks()` 中途失败（如某节点刚掉线），`taskAssignmentMap` 部分填充，`fencingToken` 已轮转。下次 `detectFailures` tick 检测未分配 task → 再次 `globalRecovery()` → 同样失败 → **恢复循环**。
- **风险**: 协调器卡在恢复循环中无法恢复。单个节点在 assignTasks 调用期间掉线即可触发。
- **建议**: 使 `assignTasks()` 容忍缺失的 RPC 服务（记录日志并继续），或在 `globalRecovery()` 中回滚失败。
- **信心水平**: 很可能
- **发现来源视角**: 10x 规模运维者（分布式协调弹性）

---

### [AR-18] InMemoryClusterRegistry 硬编码 LEASE_TIMEOUT_MS 忽略 per-renewal 超时 — 与 JDBC 实现不一致

- **文件**: `nop-stream-runtime/.../cluster/InMemoryClusterRegistry.java:54-82`
- **证据片段**:
  ```java
  // renewLease 接受 leaseTimeoutMs 但不存储:
  public boolean renewLease(String nodeId, long leaseTimeoutMs) {
      leaseTimestamps.put(nodeId, System.currentTimeMillis());  // 只存时间戳
      return true;
  }

  // getNodeLease / getActiveNodes 使用硬编码 15000ms:
  return new LeaseInfo(nodeId, timestamp, timestamp + LEASE_TIMEOUT_MS, true);
  ```
- **严重程度**: P2
- **现状**: `renewLease()` 接受自定义超时但静默忽略。`InMemoryClusterRegistry` 使用硬编码 15 秒，`JdbcClusterRegistry` 存储并使用 per-node 超时。两个实现语义不一致。
- **风险**: 使用自定义超时的测试/生产场景中，in-memory 版本的节点在 15 秒后被错误标记为过期。
- **建议**: 存储每个节点的超时值，或存储计算后的过期时间。
- **信心水平**: 确定
- **发现来源视角**: 新人开发者（两个 registry 实现行为不一致）

---

### [AR-19] BatchConsumerSinkFunction.flush() 失败后 buffer 无限增长 — OOM

- **文件**: `nop-stream-connector/.../BatchConsumerSinkFunction.java:60-81`
- **证据片段**:
  ```java
  public void consume(R value) {
      buffer.add(value);           // 始终添加
      if (buffer.size() >= batchSize) {
          flush();                 // 如果抛异常，buffer 未清空
      }
  }
  ```
- **严重程度**: P2
- **现状**: `flush()` 抛异常时 `buffer.clear()` 不会执行。如果调用者捕获异常并继续调用 `consume()`，buffer 无限增长。line 79 注释说"data retained for retry"但没有重试机制。
- **风险**: 短暂下游故障（如 DB 超时）转变为永久 OOM。
- **建议**: 在 `flush()` 失败后清空 buffer 或设置容量上限。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探（错误路径资源管理）

---

### [AR-20] MessageSinkFunction / MessageSourceFunction 非序列化字段 — 分布式部署时崩溃

- **文件**: `nop-stream-connector/.../MessageSinkFunction.java:27` / `MessageSourceFunction.java:39,49`
- **证据片段**:
  ```java
  // MessageSinkFunction.java:27:
  private final IMessageService messageService;  // 非 Serializable，非 transient

  // MessageSourceFunction.java:39:
  private final IMessageService messageService;  // 非 Serializable，非 transient
  // MessageSourceFunction.java:49:
  private IMessageSubscription subscription;      // 非 Serializable，非 transient
  ```
- **严重程度**: P2
- **现状**: 这些类实现了 `Serializable`（通过 `SinkFunction`/`SourceFunction`），但持有非序列化的 `IMessageService` 和 `IMessageSubscription`。Java 序列化时抛 `NotSerializableException`。
- **风险**: 嵌入式/测试模式下可能工作（同一个 JVM），分布式执行时崩溃。这与 `DebeziumCdcSourceFunction` 有相同的系统性问题。
- **建议**: 为这些字段添加 `transient`，并在 `open()` 中通过 IoC 查找获取引用。
- **信心水平**: 确定
- **发现来源视角**: 代码生成受害者（序列化规范不完整）

---

### [AR-21] CepPatternBuilder 不在模型加载时验证 NOT group pattern — 运行时崩溃

- **文件**: `nop-stream-cep/.../model/builder/CepPatternBuilder.java:86-107`
- **证据片段**:
  ```java
  // default case 在 buildFollowGroup 中:
  default:
      throw new StreamRuntimeException(ERR_CEP_NOT_CONDITION_DOES_NOT_SUPPORT_GROUP);
  ```
- **严重程度**: P2
- **现状**: 模型允许 `followKind = notNext/notFollowedBy` 用于 group pattern（模型层无验证），但 builder 在构建时抛异常。如果模型从外部来源加载（如 XLang 模型文件），这是运行时错误，错误消息不指出根因。
- **风险**: 看似有效的模型配置在运行时崩溃。验证应在模型加载时进行。
- **建议**: 在 `CepPatternGroupModel` 层添加验证约束。
- **信心水平**: 确定
- **发现来源视角**: 新人开发者（错误消息质量）

---

### [AR-22] OperatorChain.open() Javadoc 说 "forward order" 但实现是 reverse order

- **文件**: `nop-stream-core/.../jobgraph/OperatorChain.java:131-149`
- **证据片段**:
  ```java
  // Javadoc line 132:
  // * <p><strong>Implementation Note:</strong> The operators are opened in forward order.

  // Implementation line 141:
  for (int i = operators.size() - 1; i >= 0; i--) {  // ← reverse order
  ```
- **严重程度**: P3
- **现状**: 逆序是正确的（先打开下游使其就绪，再打开上游开始生产）。Javadoc 错误描述了实际行为。
- **风险**: 未来开发者可能"修正"代码以匹配注释，破坏正确的初始化顺序。
- **建议**: 修正 Javadoc 为 "reverse order"。
- **信心水平**: 确定
- **发现来源视角**: 新人开发者（文档误导）

---

## 总评

### 最值得关注的 3 个方向

1. **TwoPhaseCommitSinkFunction 的并发安全缺陷是本轮最严重发现**（AR-1）。`saveState()` 无锁遍历 `synchronizedMap` 在 checkpoint 线程与 commit 线程并发时必然崩溃。更危险的是 `setPendingCommits()`（AR-11）接受任意 Map，静默破坏线程安全。两个问题叠加：恢复后使用非同步 Map + checkpoint 线程并发访问 = 确定性 CME。这是一个跨层问题：2PC 协议的并发安全性在恢复路径中被破坏。

2. **WindowedStreamImpl.allowedLateness() 是一个完全无效的 API**（AR-2）。setter 存在、字段存储、但三个聚合方法均未传递。这不是边界条件——late-data handling 是窗口操作的核心 API。用户依赖此功能时数据静默丢失。更广泛地说，这暴露了 WindowedStreamImpl 和 WindowAggregationOperator 之间的"胶水层"质量风险：是否有其他参数被类似地遗忘传递？

3. **CEP 子系统存在系统性风险组合**（AR-6, 7, 8, 12）：CepOperator 清空非 start state（漏报）、DeweyNumber int 溢出（版本损坏）、Lockable 双重释放（数据腐败）、NFA 无界状态（OOM）。四个问题单独看都可防御，但组合在一起构成"正确性空洞"：高吞吐下版本号溢出 → Lockable 语义被破坏 → SharedBuffer 条目被过早释放 → CepOperator 在不确定状态下清空 computation state → 模式匹配彻底失效。这不是单个 bug，而是一个级联故障链。

### 本次审查的盲区自评

1. **没有运行测试验证任何发现**。AR-2（allowedLateness）和 AR-1（saveState CME）应可通过简单单元测试复现。
2. **没有审查 MemoryStateSerDe 的完整序列化/反序列化往返**。非代码生成的 accumulator 类在 JSON 序列化路径下可能静默损坏。
3. **没有验证 DebeziumCdcSourceFunction 的分布式部署路径**。非 transient 运行时字段的序列化问题需要实际分布式环境验证。
4. **没有深入审查 `BarrierAligner`（runtime 模块独立实现）与 `InputGate` barrier 对齐的一致性**。
5. **没有审查 fraud-example 子模块的完整运行路径**，它可能暴露 connector 层的实际使用问题。

---

## 按严重程度分布表

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0      | 1    | TwoPhaseCommitSinkFunction 并发崩溃 |
| P1      | 10   | late-data API(1) + timer 语义(1) + 窗口计数(1) + checkpoint ID(1) + CEP 正确性(3) + 集群注册(1) + 内存泄漏(1) + 2PC 线程安全(1) |
| P2      | 9    | watermark 停滞(1) + operator chain(1) + checkpoint 存储(1) + 生命周期(1) + 恢复(1) + registry 不一致(1) + buffer OOM(1) + 序列化(1) + CEP 验证(1) |
| P3      | 1    | Javadoc 错误(1) |
