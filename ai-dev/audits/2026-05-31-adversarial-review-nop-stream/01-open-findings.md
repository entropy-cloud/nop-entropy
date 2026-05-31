# nop-stream 对抗性审查 — Round 5（第5轮全模块审查）

> 审查日期：2026-05-31
> 审查范围：nop-stream 全模块（10 个子模块），聚焦之前审查盲区 + 新代码 + 历史问题验证
> 审查方法：开放式发现导向，4 个并行探索 agent 分别聚焦 core 执行引擎、runtime+flink、CEP NFA、分布式执行+connector
> 去重：已阅读以下已有报告，本报告不重复其中内容：
> - `ai-dev/audits/2026-05-20-adversarial-review-nop-stream/`（Round 1+2，N1-N41）
> - `ai-dev/audits/2026-05-21-adversarial-review-nop-stream-design/`（D1-D13）
> - `ai-dev/audits/2026-05-22-adversarial-review-nop-stream/`（Round 1，N42-N72）
> - `ai-dev/audits/2026-05-22-adversarial-review-nop-stream-r2/`（Round 3，N73-N93）
> - `ai-dev/audits/2026-05-24-adversarial-review-nop-stream-r3/`（Round 4，N94-N105）
> 发现来源视角：异常路径侦探 + 模型攻击者 + 10x 规模运维者 + 死代码清道夫

---

## 发现分类

| 类别 | 数量 | 最严重 |
|------|------|--------|
| SharedBuffer 引用计数与内存管理 | 3 | P0 |
| JDBC 分布式注册表正确性 | 2 | P0 |
| WindowOperator 状态管理 | 3 | P1 |
| CEP 模型构建正确性 | 2 | P1 |
| Checkpoint 回调完整性 | 1 | P1 |
| Connector 生命周期 | 1 | P1 |
| 分布式执行调度 | 1 | P1 |
| Checkpoint 存储可靠性 | 1 | P1 |
| 资源管理与项目约定 | 4 | P2 |

---

## P0：SharedBuffer 引用计数 + JDBC 注册表

### [AR-1] Lockable.release() 在 refCounter <= 0 时返回 true — 双重释放导致 SharedBuffer 静默数据损坏

- **文件**: `nop-stream-cep/.../nfa/sharedbuffer/Lockable.java:54-60`
- **证据片段**:
  ```java
  boolean release() {
      if (refCounter.get() <= 0) {
          return true;  // "no more locks" — 但实际上是双重释放
      }
      return refCounter.decrementAndGet() == 0;
  }
  ```
- **严重程度**: P0
- **现状**: `release()` 在 `refCounter` 已经为 0 或负数时返回 `true`，表示"可以安全删除"。调用方 `SharedBufferAccessor.releaseNode()` 在收到 `true` 后会递归释放下游节点。如果存在双重释放路径（例如 NFA 的 `computeNextStates` 通过 `shouldDiscardPath` 和正常路径释放同一 buffer entry），第二个 `release()` 会看到 `refCounter <= 0` → 返回 `true` → 触发下游节点的过早删除。
- **风险**: SharedBuffer 图结构静默损坏——仍被其他计算状态引用的节点被过早释放，导致后续 `materializeMatch` 时 NPE 或错误的匹配结果。
- **建议**: `release()` 应在 `refCounter <= 0` 时返回 `false`（或抛异常），阻止级联双重释放。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

### [AR-2] JdbcClusterRegistry.registerCoordinator 非原子 DELETE+INSERT 且不验证 fencing token — HA 场景下脑裂

- **文件**: `nop-stream-runtime/.../cluster/JdbcClusterRegistry.java:50-73`
- **证据片段**:
  ```java
  public void registerCoordinator(String jobId, String coordinatorId, String fencingToken) {
      ensureTables();
      SQL deleteSql = ...sql("DELETE FROM ... WHERE job_id = ?", jobId)...;
      SQL insertSql = ...sql("INSERT INTO ... (job_id, coordinator_id, fencing_token, ...) VALUES (?,?,?,?)",
              jobId, coordinatorId, fencingToken, now)...;
      jdbcTemplate.txn().runInTransaction(querySpace, TransactionPropagation.REQUIRED, txn -> {
          jdbcTemplate.executeUpdate(deleteSql);   // 无条件删除
          jdbcTemplate.executeUpdate(insertSql);   // 无条件插入
          return null;
      });
  }
  ```
- **严重程度**: P0
- **现状**: `registerCoordinator` 的 DELETE 不带 `WHERE fencing_token = ?` 条件，不验证旧 fencing token 就直接覆盖。在 HA 场景下：
  1. Coordinator-A（epoch=1）注册成功
  2. Coordinator-B（epoch=2）注册成功，覆盖 A
  3. Coordinator-A（epoch=1）重新注册，无条件覆盖 B 的 fencing_token
  4. 结果：两个 coordinator 都认为自己持有 leadership
- **风险**: 双主/脑裂导致分布式执行的 checkpoint、task 分配等全局协调操作冲突。
- **建议**: 在 DELETE 中加入 `AND fencing_token < ?` 条件，实现 compare-and-set 语义。或改用 `UPDATE ... SET ... WHERE job_id = ? AND fencing_token < ?`，然后检查 affected rows。
- **信心水平**: 确定
- **发现来源视角**: 10x 规模运维者

---

## P1：WindowOperator 状态管理

### [AR-3] WindowOperator.addWindowElement 非 SimpleAccumulator 路径静默覆盖已有值 — 数据丢失

- **文件**: `nop-stream-runtime/.../operators/windowing/WindowOperator.java:742-756`
- **证据片段**:
  ```java
  // current != null 但不是 SimpleAccumulator 时：
  try {
      setWindowContents(key, window, (ACC) value);  // 旧值被静默丢弃
  } catch (ClassCastException e) {
      throw new StreamException(ERR_STREAM_TYPE_MISMATCH, e)...;
  }
  ```
- **严重程度**: P1
- **现状**: 当窗口的 accumulator 不是 `SimpleAccumulator` 类型时（例如 `ReduceFunction` 场景），每个新元素直接覆盖窗口内容，而不是将新值与旧值合并。第一个元素的聚合结果在第二个元素到达时被丢弃。
- **风险**: 使用 `ReduceFunction` 或非 `SimpleAccumulator` 的自定义聚合函数的窗口操作丢失所有中间结果，只保留最后一个元素。
- **建议**: 在非 SimpleAccumulator 路径中，应调用用户提供的 merge/reduce 函数合并新旧值，而非直接覆盖。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

### [AR-4] WindowOperator.windowNamespace 对非 TimeWindow 类型使用 System.identityHashCode — checkpoint 恢复后状态不可访问

- **文件**: `nop-stream-runtime/.../operators/windowing/WindowOperator.java:840-849`
- **证据片段**:
  ```java
  private String windowNamespace(W window) {
      if (window == null) { return "_null_window_"; }
      if (window instanceof TimeWindow) {
          TimeWindow tw = (TimeWindow) window;
          return "TW:" + tw.getStart() + "," + tw.getEnd();
      }
      return window.getClass().getName() + "@" + System.identityHashCode(window);
  }
  ```
- **严重程度**: P1
- **现状**: 对非 `TimeWindow` 类型的自定义窗口，namespace 使用 `System.identityHashCode(window)`。该值在每次 JVM 运行时不同。checkpoint 恢复后，反序列化的 Window 对象具有新的 identity hash code，旧的 namespace key 无法匹配 → 所有窗口状态静默丢失。
- **风险**: 任何使用自定义 Window 类型的窗口操作在 checkpoint 恢复后状态不可用。`TimeWindow` 不受影响。
- **建议**: 使用 `window.toString()` 或要求 Window 实现提供稳定的字符串表示，或直接使用 Window 对象作为 state key（依赖正确的 equals/hashCode）。
- **信心水平**: 确定
- **发现来源视角**: 模型攻击者

---

### [AR-5] WindowOperator timer 回调中的 purge 路径不清除 trigger 状态 — 内存泄漏 + 状态残留

- **文件**: `nop-stream-runtime/.../operators/windowing/WindowOperator.java:516-521`
- **证据片段**:
  ```java
  // onEventTime 中的 purge 处理
  if (triggerResult.isPurge()) {
      W stateWindow = ...;
      clearWindowContents(triggerContext.key, stateWindow);
      // ← 缺少 triggerContext.clear()！
  }
  ```
  对比 `processElementForRegularWindow` 中的 purge：
  ```java
  if (triggerResult.isPurge()) {
      clearWindowContents(key, window);
      triggerContext.clear();  // ← 这里正确调用了
  }
  ```
- **严重程度**: P1
- **现状**: `onEventTime` 和 `onProcessingTime` 的 purge 路径缺少 `triggerContext.clear()` 调用。这意味着 trigger 的内部状态（如 `CountTrigger` 的计数器）在窗口被清除后仍残留。如果同一 key+window 范围内创建了新窗口，trigger 状态可能被污染。
- **风险**: Trigger 状态内存泄漏；同一 key 的后续窗口可能继承错误的 trigger 状态。
- **建议**: 在 `onEventTime`/`onProcessingTime` 的 purge 路径中添加 `triggerContext.clear()`。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

## P1：CEP 模型构建正确性

### [AR-6] CepPatternBuilder 非起始 pattern 的 where 条件从未应用 — CEP 匹配条件静默失效

- **文件**: `nop-stream-cep/.../model/builder/CepPatternBuilder.java:54-76`
- **证据片段**:
  ```java
  // 起始 pattern（正确）：
  pattern = Pattern.begin(start, ...);
  pattern = buildSinglePattern(pattern, (CepPatternSingleModel) partModel);  // where/until 在此应用

  // 后续 pattern（bug）：
  do {
      pattern = addQualifier(pattern, partModel);  // 只添加量词（oneOrMore, times...）
      // ...
      if (nextModel instanceof CepPatternSingleModel) {
          pattern = buildFollow(pattern, followKind, nextModel.getName());  // 只创建关系
      }
      // NOTE: buildSinglePattern 从未被调用！
      // nextModel 的 where/until 条件被静默丢弃
      partModel = nextModel;
  } while (true);
  ```
- **严重程度**: P1
- **现状**: `buildSinglePattern`（应用 `where` 和 `until` 条件）只对起始 pattern 调用。后续 pattern 只调用 `addQualifier`（量词）和 `buildFollow`（关系类型）。所有非起始 pattern 的 `where` 条件被静默忽略。
- **风险**: 用户通过模型定义的 CEP pattern 如 `A where(x>1) followedBy B where(y>2)` 中，B 的 `where(y>2)` 条件不生效，B 会无条件匹配任何事件。这是模型驱动 CEP 构建路径的功能性 bug。
- **建议**: 在 `buildFollow` 或循环中为每个后续 pattern 调用 `buildSinglePattern`（或在 `addQualifier` 中统一处理 where/until）。
- **信心水平**: 确定
- **发现来源视角**: 模型攻击者

---

### [AR-7] SharedBuffer.advanceTime 只清理 cache 不清理 backing state — 内存泄漏

- **文件**: `nop-stream-cep/.../nfa/sharedbuffer/SharedBuffer.java:169-178`
- **证据片段**:
  ```java
  void advanceTime(long timestamp) {
      Iterator<Long> iterator = eventsCount.keys().iterator();
      while (iterator.hasNext()) {
          Long next = iterator.next();
          if (next < timestamp) {
              iterator.remove();
              eventsBufferCache.entrySet().removeIf(e ->
                      e.getKey() != null && e.getKey().getTimestamp() < timestamp);
              // ← entryCache、entries、eventsBuffer 从未被清理
          }
      }
  }
  ```
- **严重程度**: P1
- **现状**: `advanceTime` 清除了 `eventsCount` 和 `eventsBufferCache`，但不清除 `entryCache`、`entries`（backing MapState）、`eventsBuffer`（backing MapState）。旧的 SharedBufferNode 条目（包含对旧 EventId 的引用）无限期保留在 backing state 中。这些条目只能通过 `releaseNode` 显式释放，但如果 partial match 已超时被清理，其 buffer entry 可能永远不会被释放。
- **风险**: SharedBuffer 持续增长，不随 watermark 推进而缩小。长时间运行的 CEP job 会逐渐耗尽内存。
- **建议**: 在 `advanceTime` 中同时清理 `entries` 和 `eventsBuffer` 中过期的条目，或确保所有 partial match 超时时正确释放其 buffer entry。
- **信心水平**: 很可能
- **发现来源视角**: 10x 规模运维者

---

### [AR-8] CepOperator 在恰好 1 个 partial match 时清除 NFA 状态 — SharedBuffer 孤立条目

- **文件**: `nop-stream-cep/.../operator/CepOperator.java:378-380`
- **证据片段**:
  ```java
  // In order to remove dangling partial matches.
  if (nfaState.getPartialMatches().size() == 1 && nfaState.getCompletedMatches().isEmpty()) {
      computationStates.clear();
  }
  ```
- **严重程度**: P1
- **现状**: 当 NFA 只剩起始状态的 partial match 且无已完成 match 时，直接清除 `computationStates`。下一次 `getNFAState()` 会从头创建新 NFA 状态。但旧的 SharedBuffer 条目不会被释放——重新创建的起始状态没有 `previousBufferEntry` 引用旧 buffer entry，无法释放它们。
- **风险**: SharedBuffer 中的孤立条目永不释放，与 AR-7 叠加加剧内存泄漏。
- **建议**: 在清除前，遍历所有 partial match 并显式释放其 buffer entry。
- **信心水平**: 很可能
- **发现来源视角**: 10x 规模运维者

---

## P1：Checkpoint 回调完整性

### [AR-9] StreamSinkOperator/StreamSourceOperator 快照失败不通知 snapshotCallback — checkpoint 永远挂死

- **文件**:
  - `nop-stream-core/.../operators/StreamSinkOperator.java:55-84`
  - `nop-stream-core/.../operators/StreamSourceOperator.java:262-275`
- **证据片段**:
  ```java
  // StreamSinkOperator.processBarrier()
  public void processBarrier(CheckpointBarrier barrier) throws Exception {
      OperatorSnapshotResult snapshotResult = null;
      if (barrier.snapshot()) {
          snapshotResult = snapshotState(context);  // ← 如果抛异常
          // ...
      }
      if (snapshotCallback != null && snapshotResult != null) {
          snapshotCallback.accept(snapshotResult);  // ← 永远执行不到
      }
  }
  ```
- **严重程度**: P1
- **现状**: 如果 `snapshotState()` 抛出异常，异常直接向上传播，`snapshotCallback` 永远不会被调用。对比 `AbstractStreamOperator.processBarrier()` 有正确的错误处理（即使 snapshot 失败也通知 callback），但 Sink 和 Source operator 覆写了 `processBarrier()` 且**没有调用 super**。

  对 Source operator 更严重：`output.emitBarrier(barrier)` 也在 `snapshotCallback` 之后，所以 barrier 永远不会发往下游，整个 checkpoint 管线被阻塞。
- **风险**: 任何导致 Source 或 Sink operator 快照失败的 I/O 错误都会使 checkpoint 永远无法完成（coordinator 的 `operatorsToAck` 永远不归零）。
- **建议**: 在 `processBarrier()`/`injectBarrier()` 中添加 try-catch，确保即使快照失败也通知 callback（传递失败结果），或调用 `super.processBarrier()`。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

## P1：Connector 生命周期

### [AR-10] DebeziumCdcSourceFunction.run() 无条件设置 draining=false — drain 模式入口是死代码

- **文件**: `nop-stream-connector/.../DebeziumCdcSourceFunction.java:56-60`
- **证据片段**:
  ```java
  public void run(SourceContext<ChangeEvent> ctx) throws Exception {
      this.draining = false;       // ← 无条件重置
      initCompletionLatch();

      if (!draining) {             // ← 永远为 true
          source = new DebeziumMessageSource(config);
          // ...
      }
  ```
- **严重程度**: P1
- **现状**: `run()` 方法开头无条件设置 `draining = false`，随后的 `if (!draining)` 检查永远为 true。即使在 DRAIN 恢复场景中 `truncateForDrain()` 已被调用，`run()` 重新进入时会重置 draining 状态，重新创建 source 并订阅，完全绕过 drain 逻辑。
- **风险**: DRAIN 模式的恢复路径不可用——`if (!draining)` 守卫是死代码，设计意图无法实现。
- **建议**: 移除 `this.draining = false` 的无条件重置，或重新设计 drain 恢复流程。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

## P1：分布式执行调度

### [AR-11] SourceEnumerator.assignSplits 要求调用方按特定顺序调用 — 分布式环境导致 split 分配饥饿

- **文件**: `nop-stream-runtime/.../source/SourceEnumerator.java:124-148`
- **证据片段**:
  ```java
  public List<String> assignSplits(int subtaskIndex) {
      Iterator<String> it = unassignedSplits.iterator();
      while (it.hasNext()) {
          String splitId = it.next();
          int target = nextSubtaskIndex % totalParallelism;
          if (target == subtaskIndex) {
              it.remove();
              assignedSplits.put(splitId, subtaskIndex);
              nextSubtaskIndex++;
          } else {
              continue;  // ← 不递增 nextSubtaskIndex！
          }
      }
      return assigned;
  }
  ```
- **严重程度**: P1
- **现状**: `assignSplits` 使用 round-robin 分配，但 `nextSubtaskIndex` 只在匹配时递增。如果 `subtaskIndex` 与当前 `nextSubtaskIndex % parallelism` 不匹配，方法遍历所有 split 但分配零个。调用方必须按 0, 1, 2, ... 的顺序调用。
- **风险**: 在分布式环境中，TaskManager 以任意顺序请求分配 → 可能所有请求都返回空列表 → split 分配饥饿。
- **建议**: 改为 `assignAllSplits()` 的批分配模式（该方法已存在且无此问题），或修改 round-robin 逻辑使其能处理乱序请求。
- **信心水平**: 确定
- **发现来源视角**: 10x 规模运维者

---

## P1：Checkpoint 存储可靠性

### [AR-12] JdbcCheckpointStorage INSERT-then-UPDATE 不检查 affected rows — 静默写入失败

- **文件**: `nop-stream-runtime/.../checkpoint/storage/JdbcCheckpointStorage.java:88-108`
- **证据片段**:
  ```java
  try {
      jdbcTemplate.executeUpdate(sql);  // INSERT
  } catch (Exception e) {
      // INSERT 失败 → 尝试 UPDATE
      jdbcTemplate.executeUpdate(updateSql);  // 可能 0 rows affected
  }
  ```
- **严重程度**: P1
- **现状**: INSERT 失败时 catch 所有 `Exception`（不仅仅是唯一约束冲突），然后 fallback 到 UPDATE。如果 INSERT 因非主键冲突原因失败（字段溢出、连接中断），UPDATE 可能更新 0 行（因为行不存在）且不报错。调用者认为写入成功但实际数据未持久化。
- **风险**: Checkpoint 数据静默丢失。恢复时找不到 checkpoint → job 从头开始或失败。
- **建议**: 检查 UPDATE 的 affected rows，如果为 0 则抛出异常。同时缩小 catch 范围到唯一约束异常。
- **信心水平**: 很可能
- **发现来源视角**: 异常路径侦探

---

## P2：资源管理与项目约定

### [AR-13] WindowOperator.triggerAccumulators 缺少 transient 修饰符 — 序列化双重写入

- **文件**: `nop-stream-runtime/.../operators/windowing/WindowOperator.java:187`
- **证据片段**:
  ```java
  protected Map<String, SimpleAccumulator<?>> triggerAccumulators;  // 非 transient
  ```
- **严重程度**: P2
- **现状**: `triggerAccumulators` 没有 `transient` 修饰符，但 `snapshotState()`/`restoreState()` 手动处理了它的序列化。Java 默认序列化会再次尝试序列化该字段，可能导致 `NotSerializableException`（如果 accumulator 不可序列化）或双重序列化。对比同文件中 `windowContentsState` 等字段都有 `transient` 修饰。
- **风险**: Java 序列化路径（如果被触发）会导致不可预知的行为。
- **建议**: 添加 `transient` 修饰符。
- **信心水平**: 确定

---

### [AR-14] CollectionReplayableSource.run() long→int 强制转换溢出

- **文件**: `nop-stream-runtime/.../source/CollectionReplayableSource.java:31`
- **证据片段**:
  ```java
  ctx.collect(data.get((int) currentOffset));  // long → int 截断
  ```
- **严重程度**: P2
- **现状**: `currentOffset` 是 `long`，`data.get()` 接受 `int`。如果 `seek()` 传入 offset > `Integer.MAX_VALUE`，`(int)` 强转会溢出为负数 → `ArrayIndexOutOfBoundsException`。API 契约违反：`seek(long)` 暗示支持大 offset。
- **风险**: 极端大数据量场景下 source 崩溃。
- **建议**: 在 `seek()` 中添加边界检查，或将 `currentOffset` 改为 `int` 并更新 `seek` 的参数类型。
- **信心水平**: 确定

---

### [AR-15] JobCoordinator.globalRecovery 只更新 TaskManager 类型的 RPC 服务 fencing token

- **文件**: `nop-stream-runtime/.../coordinator/JobCoordinator.java:413-418`
- **证据片段**:
  ```java
  for (IStreamTaskRpcService rpc : taskRpcServices.values()) {
      if (rpc instanceof TaskManager) {        // ← instanceof 检查
          ((TaskManager) rpc).updateFencingToken(newToken);
      }
  }
  ```
- **严重程度**: P2
- **现状**: `updateFencingToken` 不在 `IStreamTaskRpcService` 接口中，只能通过 `instanceof TaskManager` 调用。自定义 RPC 实现的 fencing token 永远不会被更新。recovery 后旧 token 的任务仍在运行，新 checkpoint 使用新 token → 旧任务的 ACK 被拒绝。
- **风险**: 使用非 TaskManager 的 RPC 实现时，分布式恢复的 fencing token 不一致。
- **建议**: 将 `updateFencingToken` 方法加入 `IStreamTaskRpcService` 接口。
- **信心水平**: 很可能

---

### [AR-16] EmbeddedDistributedExecutor 使用 java.io.tmpdir 违反项目约定

- **文件**: `nop-stream-runtime/.../execution/EmbeddedDistributedExecutor.java:91-92`
- **证据片段**:
  ```java
  new LocalFileCheckpointStorage(
      System.getProperty("java.io.tmpdir") + "/nop-stream-checkpoint/" + jobId);
  ```
- **严重程度**: P2
- **现状**: 使用 `System.getProperty("java.io.tmpdir")`（在 macOS/Linux 上解析为 `/tmp`）而非项目约定的 `_tmp/` 目录。同样模式出现在 `GraphModelCheckpointExecutor.createStorage()` 第 578 行。AGENTS.md 明确禁止使用系统级 `/tmp/`。
- **风险**: 在 opencode 非交互模式下触发外部目录权限提示，阻塞执行。
- **建议**: 改为使用 `<project-root>/_tmp/` 或 Nop 平台的临时目录机制。
- **信心水平**: 确定
- **发现来源视角**: 死代码清道夫

---

## 历史问题状态更新

以下之前报告的问题在本轮审查中确认**仍然存在**（简要引用，不重复分析）：

| # | 描述 | 状态 | 变化 |
|---|------|------|------|
| N42 | InputGate 递归 CME | 仍存在 | 无变化 |
| N43 | barriersRemaining 下溢 | 仍存在 | 无变化 |
| N44 | topologicalSort 无环检测 | 仍存在 | 被 RemoteGraphExecutionPlanBuilder 复制（N104） |
| N58 | TaskExecutor 线程池泄漏 | 仍存在 | execute() 路径仍未 shutdown |
| N77 | WindowAggregationOperator trigger state key 碰撞 | 仍存在 | 无变化 |
| N78 | 三套独立 timer 实现 | 仍存在 | N73/N74 修复了具体 bug，架构问题未变 |
| N79 | CepOperator 每事件 flush cache | 仍存在 | 无变化 |
| N81 | NFACompiler 静默丢弃 NOT_FOLLOW | 仍存在 | 无变化 |
| N90 | CEP 零测试覆盖关键功能 | 仍存在 | 无变化 |
| N97 | 所有 Subtask 共享 OperatorChain | 仍存在 | RemoteGraphExecutionPlanBuilder 中 taskIndex==0 仍共享原始 chain |
| N99 | StreamElementCodec 丢失 timestamp | 仍存在 | 无变化 |
| N103 | Fencing token 不一致 | 仍存在 | 无变化 |

---

## 总评

### 最值得关注的 3 个方向

1. **CEP 模型构建路径的功能性缺陷是最值得关注的发现**（AR-6）：`CepPatternBuilder` 对非起始 pattern 完全忽略 `where` 条件，这意味着通过 XLang 模型定义的 CEP pattern（平台的核心价值主张——声明式开发）只有第一个步骤的过滤条件生效。这不是边界情况——是模型驱动 CEP 构建路径的系统性失效。与之相关的 AR-7 和 AR-8 构成了 CEP 模块的"正确性 + 内存管理"双重问题：pattern 条件失效导致过多匹配，SharedBuffer 清理不当导致内存不释放。

2. **WindowOperator 存在三个独立的状态正确性问题**（AR-3/AR-4/AR-5）：非 SimpleAccumulator 路径的数据丢失、非 TimeWindow 的恢复失败、timer purge 路径的 trigger 状态泄漏。三个问题互相独立但都指向同一个根因：WindowOperator 的实现路径覆盖不完整——有正常的代码路径（processElement 中的 purge 正确调用了 triggerContext.clear()），但其他路径（timer 回调）遗漏了。这是一种"实现了主路径但遗漏了分支"的典型模式。

3. **分布式协调的 HA 正确性是空白区域**（AR-2）：`JdbcClusterRegistry.registerCoordinator` 的非原子 DELETE+INSERT 在 HA 场景下必然导致脑裂。虽然当前可能只在测试中使用 `InMemoryClusterRegistry`，但 JDBC 实现已存在且接口已暴露。配合 AR-9（Source/Sink 快照失败导致 checkpoint 挂死）和 AR-11（split 分配饥饿），分布式执行的"可工作性"高度依赖于单节点、有序、无错误的理想场景。

### 本次审查的盲区自评

1. **nop-stream-flow 模块**：空壳模块，仍然无法审查。
2. **性能基准**：所有性能相关发现（SharedBuffer 内存泄漏、NFAState 的 O(n log n) equals/hashCode）都是理论分析，没有实际性能数据。
3. **多 job 并行执行**：没有分析多个 StreamExecutionEnvironment 实例并行运行时的资源隔离。
4. **Flink 集成路径**：nop-stream-flink 目录为空，没有审查 Flink 集成的实现状态。
5. **序列化兼容性**：没有验证 checkpoint 数据在不同版本间的兼容性。
6. **BarrierAligner 的并发压力测试**：虽然确认了 BarrierAligner 的 in-order 完成语义是正确的（验证否决了 agent 的发现），但没有验证高并发下的性能特征。

---

## 严重程度分布表

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0      | 2    | SharedBuffer 引用计数损坏 + JDBC 注册表脑裂 |
| P1      | 10   | WindowOperator 状态管理、CEP 模型构建、Checkpoint 回调、分布式调度 |
| P2      | 4    | 序列化、API 契约、接口设计、项目约定 |
