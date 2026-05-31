# nop-stream 对抗性审查 — Round 12

> 审查日期：2026-05-30
> 审查范围：nop-stream 全模块（5 个活跃子模块），开放式发现导向
> 审查方法：4 个并行探索 agent 分别聚焦 (1) 模块结构与依赖关系，(2) CheckpointCoordinator/TaskManager/JobCoordinator 分布式协调，(3) WindowAggregationOperator/TimerService/InputGate/RecordWriter 执行引擎核心，(4) CEP Operator/NFA/SharedBuffer + Connector 序列化生命周期。然后交叉验证。
> 发现来源视角：代码生成受害者 + 异常路径侦探 + 安全防御者
> 去重：已阅读以下已有报告，本报告不重复其中已修复内容：
> - `2026-05-20-adversarial-review-nop-stream/` ~ `2026-05-30-adversarial-review-nop-stream-r11/`（Round 1~11）
> - `2026-05-25-deep-audit-nop-stream-full/` ~ `2026-05-30-deep-audit-nop-stream-full/`

---

## Round 11 修复确认

以下 Round 11 高优先级发现已在当前代码中确认修复：

| Round 11 编号 | 问题 | 修复确认 |
|---------------|------|---------|
| AR-1 | receiveAssignment 信号量泄漏 | ✅ 已修复：两个 early-return 路径均添加了 `capacitySemaphore.release()`（行 224、241） |
| AR-5 | WindowAggregationOperator trigger state 合并后未清理 | ✅ 已修复：`processElementWithMerging` 现在在行 327-333 清理源窗口 trigger state |
| AR-5 补充 | activeWindowsPerKey 未重建 | ✅ 已修复：`restoreState` 行 205-209 从 windowState 重建 |
| AR-7 | 非前进 watermark 转发 | ✅ 已修复：`processWatermark` 行 377-379 非前进时直接 return 不 emit |

---

## 新发现

### [AR-1] TwoPhaseCommitSinkFunction.saveState() 返回 null — pendingCommits 永不持久化，恢复后 exactly-once 失效

- **文件**: `nop-stream-core/.../common/functions/sink/TwoPhaseCommitSinkFunction.java:64-67`
- **证据片段**:
  ```java
  @Override
  public TaskStateSnapshot saveState(long epochId) throws Exception {
      return null;
  }
  ```
- **严重程度**: P0
- **现状**: `pendingCommits` map（存储已 preCommit 但未 commit 的事务句柄，按 checkpoint ID 索引）在 `saveState()` 中完全不被序列化。`StreamSinkOperator.processBarrier()` 在行 61 调用 `saveState(barrier.getId())` 并期望非 null 结果来合并到快照。由于返回 null，`participantState != null` 检查（行 62）为 false，整个 pending commits 状态丢失。

  恢复时 `restoreFromEpoch()` 尝试 rollback `pendingCommits` 中的事务，但该 map 为空（因为从未被持久化），所有悬挂的 prepared 事务被静默遗忘。
- **风险**: 任何使用 `TwoPhaseCommitSinkFunction` 的 sink（如 JDBC sink、消息队列 sink）在故障恢复后丢失所有 in-flight 事务。已 preCommit 的数据既不被 commit 也不被 rollback——外部系统中的 prepared 事务永远悬挂。这是 **exactly-once 语义的根本性违约**。
- **建议**: 在 `saveState()` 中将 `pendingCommits` 序列化为 `TaskStateSnapshot`：
  ```java
  @Override
  public TaskStateSnapshot saveState(long epochId) throws Exception {
      TaskStateSnapshot snapshot = new TaskStateSnapshot();
      snapshot.putOperatorState(PENDING_COMMITS_KEY, new TreeMap<>(pendingCommits));
      return snapshot;
  }
  ```
  并在 `restoreFromEpoch()` 中恢复。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探（2PC 状态持久化链路）

---

### [AR-2] ClassNameValidator 的 `[L` 前缀允许任意对象数组类实例化 — 反序列化 RCE 向量

- **文件**: `nop-stream-core/.../util/ClassNameValidator.java:23`
- **证据片段**:
  ```java
  private static final List<String> ALLOWED_PREFIXES = Collections.unmodifiableList(Arrays.asList(
          "io.nop.stream.",
          "io.nop.stream.core.",
          "java.",
          ...
          "[L",    // ← JVM 对象数组标记
          "[B", "[I", "[J", "[F", "[D", "[S", "[C", "[Z"
  ));
  ```
- **严重程度**: P1
- **现状**: `[L` 是 JVM 对象数组类型的前缀。类名 `[Lcom.evil.MaliciousClass;` 以 `[L` 开头，通过验证。在 `WindowAggregationOperator.deserializeWindowState()` 行 609，`Class.forName(accType).getDeclaredConstructor().newInstance()` 被调用——这意味着任何以 `[L` 开头的类名都能实例化。

  虽然在当前代码路径中 `accType` 来自序列化的 `@type` 字段（由 `serializeWindowState()` 写入），正常使用时不会产生恶意类名。但如果 checkpoint 数据被篡改（如中间人攻击共享存储），攻击者可以注入 `[Lcom.evil.Attacker;` 类名，触发该类的静态初始化块和构造函数。
- **风险**: 在共享 checkpoint 存储场景下（如 JDBC storage、分布式文件系统），攻击者可以构造恶意 checkpoint 数据实现远程代码执行。这是一个 defense-in-depth 缺失。
- **建议**: 将 `[L` 前缀验证改为检查完整的数组元素类型前缀，例如 `[Lio.nop.` 和 `[Ljava.`，或者改为在 `startsWith("[L")` 时验证后续的包名前缀。
- **信心水平**: 确定
- **发现来源视角**: 安全防御者

---

### [AR-3] RecordWriter.emitElement() 在有 partitionRouter 时只写 partitions[0] — watermark 广播缺失

- **文件**: `nop-stream-core/.../execution/RecordWriter.java:188-201`
- **证据片段**:
  ```java
  public void emitElement(StreamElement element) {
      try {
          if (partitioner != null) {
              for (ResultPartition partition : partitions) {
                  partition.write(element);     // ← 广播到所有分区
              }
          } else {
              partitions[0].write(element);     // ← 只写第一个分区
          }
      } catch (InterruptedException e) { ... }
  }
  ```
- **严重程度**: P1
- **现状**: `emitElement()` 用于发射 watermark 和 watermark status 等控制元素。当 `partitionRouter != null` 但 `partitioner == null` 时（这是 FORWARD 和 REBALANCE 路由的常见配置），`emitElement()` 只写入 `partitions[0]`。对比 `emitWatermark()` 和 `emitBarrier()`，它们始终广播到所有分区。

  这意味着使用 `partitionRouter`（而非 `partitioner`）的 RecordWriter 实例在通过 `emitElement()` 路径发射 watermark/watermark-status 时，下游只有 channel 0 收到，其余 channel 的 watermark 永不更新。
- **风险**: 使用 `ForwardPartitionRouter` 或 `RebalancePartitionRouter` 的 operator chain 中，下游 subtask 1~N-1 永远收不到 watermark status 更新。如果 watermark status 的 IDLE/ACTIVE 传播依赖此路径，下游算子的 watermark 逻辑可能永久停滞。
- **建议**: `emitElement()` 应始终广播到所有分区，与 `emitWatermark()` 和 `emitBarrier()` 行为一致：
  ```java
  public void emitElement(StreamElement element) {
      for (ResultPartition partition : partitions) {
          partition.write(element);
      }
  }
  ```
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探（watermark 传播路径）

---

### [AR-4] RecordWriter.selectChannel() 中 Math.abs(Integer.MIN_VALUE) 返回负数 — ArrayIndexOutOfBoundsException

- **文件**: `nop-stream-core/.../execution/RecordWriter.java:232-234`
- **证据片段**:
  ```java
  if (partitioner != null) {
      int channel = partitioner.partition(record.getValue(), partitions.length);
      return Math.abs(channel % partitions.length);
  }
  ```
- **严重程度**: P2
- **现状**: 当 `partitioner.partition()` 返回 `Integer.MIN_VALUE` 时，`Integer.MIN_VALUE % partitions.length` 可能仍为负数（取决于 partitions 数量），然后 `Math.abs(负数)` 通常为正——但 `Math.abs(Integer.MIN_VALUE)` 返回 `Integer.MIN_VALUE`（仍为负）。用此负值索引 `partitions[]` 数组（行 143）抛出 `ArrayIndexOutOfBoundsException`。

  对比 `HashPartitionRouter`（行 33）使用 `Math.floorMod()` 正确处理此边界。
- **风险**: 如果用户提供的 `IPartitioner` 实现返回 `Integer.MIN_VALUE`（如 `hashCode()` 溢出），RecordWriter 崩溃。这属于防御性编码不足。
- **建议**: 改用 `Math.floorMod(channel, partitions.length)` 或在取模后添加 `(channel + partitions.length) % partitions.length` 的安全取模。
- **信心水平**: 确定

---

### [AR-5] CountTrigger.onMerge() 是 no-op 但 canMerge() 返回 true — Session Window 合并后计数器归零

- **文件**: `nop-stream-core/.../windowing/triggers/CountTrigger.java:71-78`
- **证据片段**:
  ```java
  @Override
  public boolean canMerge() {
      return true;
  }

  @Override
  public void onMerge(W window, OnMergeContext ctx)  {
      // ctx.mergePartitionedState(stateDesc);
  }
  ```
- **严重程度**: P1
- **现状**: `CountTrigger` 声明支持合并（`canMerge()=true`），但 `onMerge()` 是空实现——注释掉的 `mergePartitionedState` 调用从未被启用。当 Session Window 合并时，源窗口的 count state 不被合并到目标窗口。合并后的窗口计数从 0 开始，可能永远无法达到 `maxCount` 阈值。

  在 `WindowAggregationOperator.processElementWithMerging()` 中，合并时先 `trigger.clear()` 清理源窗口 state（行 326），然后对新窗口调用 `trigger.onElement()`。由于 `onMerge()` 未合并计数，新窗口的 count 从 1 开始（只有当前元素），丢失了源窗口中已累积的计数。
- **风险**: `CountTrigger.of(N)` 与 `SessionWindow` 组合使用时，窗口合并后计数器重置。高频事件流中 Session Window 频繁合并，导致 CountTrigger 几乎永远不会触发——数据堆积在窗口中直到 session timeout。
- **建议**: 取消注释 `ctx.mergePartitionedState(stateDesc)` 并验证 `OnMergeContext.mergePartitionedState()` 实现正确。或者如果当前不支持状态合并，将 `canMerge()` 改为 `return false` 以避免错误使用。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

### [AR-6] GraphExecutionPlan.topologicalSort() 不检测环 — 静默产出缺失顶点的执行计划

- **文件**: `nop-stream-core/.../execution/GraphExecutionPlan.java:374-408`
- **证据片段**:
  ```java
  private static List<String> topologicalSort(JobGraph jobGraph) {
      // ... Kahn's algorithm ...
      List<String> sorted = new ArrayList<>();
      while (!queue.isEmpty()) { ... }
      return sorted;   // ← 不检查 sorted.size() == jobGraph.getVertices().size()
  }
  ```
- **严重程度**: P2
- **现状**: Kahn's 算法对有环图会静默丢弃环中顶点（环内顶点入度永远不为 0，不会被加入 sorted）。返回的列表缺少这些顶点，后续执行计划构建会忽略整个环。没有错误信号。
- **风险**: 如果 `JobGraph` 因程序错误包含环（如循环依赖的 operator chain），运行时静默跳过环中所有算子。调试困难——任务"成功"但缺失输出。
- **建议**: 在排序后添加：
  ```java
  if (sorted.size() != jobGraph.getVertices().size()) {
      Set<String> missing = new HashSet<>(jobGraph.getVertices().keySet());
      missing.removeAll(sorted);
      throw new StreamException(ERR_STREAM_CYCLIC_JOB_GRAPH).param(ARG_DETAIL, "Cycle detected involving: " + missing);
  }
  ```
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

### [AR-7] CepOperator.currentWatermark (transient long) 反序列化后为 0L 而非 Long.MIN_VALUE — 初始 watermark 偏移

- **文件**: `nop-stream-cep/.../operator/CepOperator.java:158`
- **证据片段**:
  ```java
  private transient long currentWatermark = Long.MIN_VALUE;
  ```
- **严重程度**: P2
- **现状**: `currentWatermark` 是 `transient` 基本类型。Java 反序列化后基本类型字段初始化为默认值 `0L`（字段初始化器 `= Long.MIN_VALUE` 对 transient 字段不执行）。`open()` 方法（行 186-273）不重置 `currentWatermark`。因此反序列化后 `currentWatermark == 0L`。

  这意味着：
  - `processWatermark()` 中 `newWatermark > currentWatermark` 对 `newWatermark` 在 `(Long.MIN_VALUE, 0)` 范围内为 true（正确），但对 `newWatermark == 0` 为 false（跳过 watermark 推进）
  - `processElement()` 中 `timestamp > timerService.currentWatermark()` 对负时间戳元素为 false，元素被当作 late data 丢弃
- **风险**: 如果使用负时间戳（如 epoch 前的日期、偏移时间戳），反序列化后的 CepOperator 可能在初始阶段错误丢弃元素。对大多数业务场景（正时间戳），影响有限。
- **建议**: 在 `open()` 方法开头添加 `currentWatermark = Long.MIN_VALUE;`
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探（transient 字段生命周期）

---

### [AR-8] StreamSinkOperator.restoreState() 不恢复 pendingCommits — 与 AR-1 形成双重遗漏

- **文件**: `nop-stream-core/.../operators/StreamSinkOperator.java:117-129`
- **证据片段**:
  ```java
  @Override
  public void restoreState(OperatorSnapshotResult snapshotResult) throws Exception {
      super.restoreState(snapshotResult);
      if (userFunction instanceof TwoPhaseCommitSinkFunction) {
          @SuppressWarnings("unchecked")
          TwoPhaseCommitSinkFunction<Object> tpcSink = (TwoPhaseCommitSinkFunction<Object>) userFunction;
          if (snapshotResult == null || snapshotResult.isEmpty()) {
              tpcSink.rollback();
          }
          tpcSink.beginTransaction();
      }
  }
  ```
- **严重程度**: P2
- **现状**: 即使 AR-1 被修复（`saveState()` 正确保存 pendingCommits），`restoreState()` 也不从快照中恢复 pendingCommits。它只做 rollback + beginTransaction，丢失了所有未提交事务的上下文。`CheckpointParticipant.restoreFromEpoch()` 路径（行 100-115）会尝试 rollback pendingCommits 中的事务，但 `StreamSinkOperator.restoreState()` 不调用此路径——它绕过了 `restoreFromEpoch()`。
- **风险**: 与 AR-1 形成双重遗漏：即使 saveState 正确保存了 pendingCommits，restoreState 也不会读取和恢复它们。2PC 的 exactly-once 保证在恢复路径上完全不工作。
- **建议**: `restoreState()` 应从快照中提取 pendingCommits 并通过 `setPendingCommits()` 恢复到 sink function，然后调用 `restoreFromEpoch()` 进行正确的 rollback。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探（2PC 恢复链路）

---

## 总评

### 最值得关注的 1 个方向

**TwoPhaseCommitSinkFunction 的 2PC 状态持久化链路存在双重断裂。** AR-1（saveState 返回 null）和 AR-8（restoreState 不恢复 pendingCommits）组合意味着 nop-stream 的 exactly-once 语义对 2PC sink 是一个从未真正工作过的特性——不是偶发故障，而是设计层面的缺失。`pendingCommits` map 在内存中正确维护，在 commit 路径上正确使用，但从未跨越 checkpoint/restore 边界。这是一个需要端到端修复的系统性问题，不是单点 patch。

### 本次审查的盲区自评

1. **未运行测试验证任何发现**：AR-1（saveState 返回 null）和 AR-5（CountTrigger.onMerge 为 no-op）应可通过单元测试快速确认。
2. **未深入审查 ProcessingTimeoutTrigger.onProcessingTime 的误触发问题**：行 96-100 对任何 processing-time timer 都返回 FIRE，包括嵌套 trigger 自己注册的 timer。但由于该类标注为 `@Internal` 且 Javadoc 声明"API 预留，当前未被使用"，实际影响为零。
3. **未审查 fraud-example 模块的端到端正确性**。
4. **未验证 CheckpointCoordinator.retryFailedCommits 的 `checkpointSuccessMap.getOrDefault(failedEpoch, true)` 在旧 epoch 被清理后的行为**。
5. **未深入分析 InputGate 非对齐模式下不同 checkpoint ID 的 barrier 交叉问题**。

---

## 按严重程度分布表

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0      | 1    | 2PC 状态持久化缺失 |
| P1      | 3    | 安全验证缺陷(1) + Watermark 广播缺失(1) + Trigger 合并逻辑错误(1) |
| P2      | 4    | 整数溢出(1) + DAG 验证(1) + 反序列化初始化(1) + 2PC 恢复缺失(1) |
