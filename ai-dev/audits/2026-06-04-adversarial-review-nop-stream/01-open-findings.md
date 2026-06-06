# 对抗性审查：nop-stream

**日期**: 2026-06-04
**范围**: nop-stream 全模块（nop-stream-core, nop-stream-cep, nop-stream-connector, nop-stream-runtime, nop-stream-fraud-example）
**方法**: 开放式代码审查，无预设维度。3 个并行 agent 分区深度阅读后交叉验证。
**去重**: 已浏览 `ai-dev/audits/` 中已有的两份 nop-code 审计报告，无重叠（不同模块）。
**起始视角**: 异常路径侦探 + 10x 规模运维者 + 死代码清道夫

---

## 发现

### [AR-1] ResultPartition.close() 在队列满时丢弃 END_OF_STREAM 哨兵 → 消费者永久死锁

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/ResultPartition.java:127-130`
- **证据片段**:
  ```java
  public void close() {
      finished = true;
      queue.offer(END_OF_STREAM);   // offer() 在队列满时返回 false，哨兵被丢弃
  }
  ```
- **严重程度**: P0
- **现状**: `close()` 使用 `LinkedBlockingQueue.offer()` 放入 END_OF_STREAM 哨兵。`offer()` 是非阻塞的——当队列的 1024 个槽位已满时，哨兵被静默丢弃。下游消费者 `readSingleChannel()` 调用 `queue.take()` 阻塞等待哨兵，永远等不到。
- **风险**: 生产者速度超过消费者时（在单通道路径中最常见），下游 TaskExecutor 线程永久挂起。整个 pipeline 死锁。这是有界缓冲区关闭协议中的经典 bug。
- **建议**: 使用 `queue.put(END_OF_STREAM)`（阻塞），或在 `readSingleChannel()` 中增加对 `isFinished()` 的检查作为回退。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

### [AR-2] InputGate.readMultiChannel() 200 轮空转后提前终止 pipeline → 数据丢失

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/InputGate.java:265-268`
- **证据片段**:
  ```java
  emptyRounds++;
  if (emptyRounds >= 200) {
      return Optional.empty();      // 调用方将此视为 end-of-stream
  }
  LockSupport.parkNanos(10_000_000L);
  ```
- **严重程度**: P1
- **现状**: 当所有通道在 ~200 轮循环（约 2 秒）内返回 null 但未 finish 时，方法返回 `Optional.empty()`。调用方 `StreamTaskInvokable.processInputGate()` 将 `Optional.empty()` 解释为正常 end-of-stream，发出 `Watermark.MAX_WATERMARK` 并关闭 RecordWriter，终止整个 pipeline。
- **风险**: 上游生产者慢或突发性数据时（>2s 空闲），pipeline 被提前终止。在 barrier alignment 模式下尤其危险——被阻塞的通道外的剩余通道可能全部空闲，快速触发此限制。
- **建议**: 移除 200 轮硬编码限制，仅依赖 `isAllFinished()` 判断终止。如需超时，添加可配置参数。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

### [AR-3] InputGate.readSingleChannel() 将 InterruptedException 吞为 end-of-stream

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/InputGate.java:201-204`
- **证据片段**:
  ```java
  } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Optional.empty();   // 调用方将此视为正常结束
  }
  ```
- **严重程度**: P1
- **现状**: 当任务线程被中断（取消/关闭时），方法返回 `Optional.empty()`。调用方将其解释为正常 end-of-stream 并走正常完成路径（发出 MAX_WATERMARK、关闭输出）。这掩盖了取消原因，使任务看起来正常完成。
- **风险**: 任务取消时的静默数据丢失。取消原因被吞掉。
- **建议**: 重新抛出异常或使用取消标志区分正常 EOS 和中断。
- **信心水平**: 很可能

---

### [AR-4] SharedBuffer.flushCache() 在状态写入失败时丢失数据

- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/sharedbuffer/SharedBuffer.java:323-342`
- **证据片段**:
  ```java
  void flushCache() {
      if (!entryCache.isEmpty()) {
          Map<NodeId, Lockable<SharedBufferNode>> snapshot1 = new java.util.HashMap<>(entryCache);
          entryCache.clear();                    // 缓存先清空
          try {
              entries.putAll(snapshot1);          // 如果这里失败，数据只存在于局部变量
          } catch (Exception e) {
              throw new StreamException(...);     // snapshot1 随异常丢失
          }
      }
  }
  ```
- **严重程度**: P1
- **现状**: 缓存 `entryCache` 在状态持久化完成之前被清空。如果 `entries.putAll()` 抛出异常（I/O 错误、序列化失败），数据仅存在于局部变量 `snapshot1` 中，异常传播后丢失。`entryCache` 已空，后续 `flushCache` 不会重试。
- **风险**: 任何可抛出异常的状态后端上，SharedBuffer 状态（部分匹配、事件引用）在 checkpoint 或 accessor 关闭时完全丢失。
- **建议**: 在 `putAll` 成功后再清空缓存，或在失败时将 snapshot 重新放回缓存。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

### [AR-5] CepOperator.onEventTime() 悬空清理导致 SharedBuffer 内存泄漏

- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java:412-431`
- **证据片段**:
  ```java
  // In order to remove dangling partial matches.
  if (nfaState.getPartialMatches().size() == 1 && nfaState.getCompletedMatches().isEmpty()) {
      boolean allTimedOut = true;
      for (Object pm : nfaState.getPartialMatches()) {
          // ... 使用 startTimestamp + wt 检查超时 ...
          if (wt <= 0 || timerService.currentWatermark() < cs.getStartTimestamp() + wt) {
              allTimedOut = false;
              break;
          }
      }
      if (allTimedOut) {
          computationStates.clear();   // 清除 NFA 状态但未释放 SharedBuffer 条目
      }
  }
  ```
- **严重程度**: P1
- **现状**: 此清理在 `advanceTime` 的 `SharedBufferAccessor` 作用域之外运行（`advanceTime` 在 line 407 通过 try-with-resources 关闭）。超时检查使用 `startTimestamp + wt`，而 `advanceTime` 对 per-state window 使用 `previousTimestamp`。两者语义不同。当 per-state window time 不同于 global window time 时，`advanceTime` 可能认为状态未超时（基于 `previousTimestamp`），但此清理认为已超时（基于 `startTimestamp`），直接清除 computation state 但 SharedBuffer 中引用的节点未被释放。
- **风险**: 使用不同 per-state window time 的模式下，SharedBuffer 条目泄漏——引用计数永不清零，内存持续增长。
- **建议**: 使用 `SharedBufferAccessor` 在清除前释放 `previousBufferEntry`；或移除此重复清理，完全依赖 `advanceTime`。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

### [AR-6] DebeziumCdcSourceFunction.run() 重入创建孤立的 Debezium 引擎

- **文件**: `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/DebeziumCdcSourceFunction.java:56-68`
- **证据片段**:
  ```java
  public void run(SourceContext<ChangeEvent> ctx) throws Exception {
      this.draining = false;
      initCompletionLatch();
      if (!draining) {                    // 恒为 true（line 57 刚设置为 false）
          source = new DebeziumMessageSource(config);
          // ...
      }
  }
  ```
- **严重程度**: P1
- **现状**: 如果 `run()` 被调用两次（例如恢复/重启后未先调用 `cancel()`），新的 `DebeziumMessageSource` 覆盖 `source` 字段，旧的 Debezium 引擎及其线程永远不会被停止。`if (!draining)` 检查恒为 true（line 57 刚设置 `draining = false`），不提供任何保护。
- **风险**: 泄漏 Debezium 引擎线程、重复 CDC 事件发射、资源耗尽。
- **建议**: 使 `run()` 不可重入，或在创建新 source 前停止旧的 source/subscription。
- **信心水平**: 确定
- **发现来源视角**: 10x 规模运维者

---

### [AR-7] EmbeddedDistributedExecutor 硬编码 60 秒超时

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/EmbeddedDistributedExecutor.java:139`
- **证据片段**:
  ```java
  waitForCompletion(taskManagers, 60);
  ```
- **严重程度**: P1
- **现状**: 分布式作业的完整执行超时硬编码为 60 秒。超时时抛出 `StreamException`，finally 块停止所有 TaskManager——可能导致 in-flight 数据和 checkpoint 状态丢失。
- **风险**: 任何超过 60 秒的真实工作负载静默失败并丢失数据。无配置机制调整超时。
- **建议**: 通过 `DeploymentPlan` 或构造函数参数使超时可配置。
- **信心水平**: 确定

---

### [AR-8] MessageSourceFunction.onMessage 异常静默杀死订阅

- **文件**: `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/MessageSourceFunction.java:110-121`
- **证据片段**:
  ```java
  subscription = messageService.subscribe(effectiveTopic, new IMessageConsumer() {
      @Override
      public Object onMessage(String t, Object msg, IMessageConsumeContext context) {
          // ...
          ctx.collect((T) msg);   // 异常传播到消息服务回调
          return null;
      }
  });
  ```
- **严重程度**: P1
- **现状**: 如果 `ctx.collect()` 抛出异常（背压、算子故障），异常传播到 `IMessageService` 回调。根据消息服务实现，可能静默杀死订阅。`run()` 循环继续等待 latch，不知道订阅已死。
- **风险**: 静默数据丢失或静默订阅死亡。
- **建议**: 在 `ctx.collect()` 外包裹 try-catch，记录错误并设置失败标志以中断 `run()` 循环。
- **信心水平**: 很可能

---

### [AR-9] EmbeddedDistributedExecutor finally 块中 coordinator.stop() 抛异常后 TaskManager 不会被停止

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/EmbeddedDistributedExecutor.java:149-154`
- **证据片段**:
  ```java
  } finally {
      coordinator.stop();
      for (TaskManager tm : taskManagers) {
          tm.stop();
      }
  }
  ```
- **严重程度**: P2
- **现状**: 如果 `coordinator.stop()` 抛出异常，for 循环永远不会执行。TaskManager 继续运行（持有线程、消息订阅等）。
- **风险**: 线程泄漏、资源泄漏。
- **建议**: 每个 `stop()` 调用包裹独立的 try-catch。
- **信心水平**: 确定

---

### [AR-10] PartitionedPlanGenerator 使用脆弱的类名匹配推断分区策略

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/graph/PartitionedPlanGenerator.java:67-75`
- **证据片段**:
  ```java
  String partitionerName = edge.getPartitioner().getClass().getSimpleName().toLowerCase();
  if (partitionerName.contains("hash")) {
      return PartitionPolicy.HASH;
  } else if (partitionerName.contains("rebalance")) {
      return PartitionPolicy.REBALANCE;
  } else if (partitionerName.contains("broadcast")) {
      return PartitionPolicy.BROADCAST;
  }
  ```
- **严重程度**: P2
- **现状**: 分区策略通过 `contains()` 匹配类简单名推断。名为 `MyHashBasedRouter` 的自定义分区器会被错误分类为 `HASH`，即使它实现的是 rebalance 语义。
- **风险**: 自定义分区器的静默错误路由。不会抛出错误。
- **建议**: 当分区器未实现 `PartitionPolicyAware` 时，默认为 `FORWARD` 或要求显式声明。移除类名启发式匹配。
- **信心水平**: 确定

---

### [AR-11] CheckpointBarrierTracker.acknowledgeOperator() 在持有锁时调用用户回调

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/CheckpointBarrierTracker.java:89,122-126`
- **证据片段**:
  ```java
  public synchronized void acknowledgeOperator(int operatorIndex, OperatorSnapshotResult snapshot) {
      // ... 状态变更 ...
      if (operatorsToAck.decrementAndGet() == 0) {
          if (completionCallback != null) {
              completionCallback.accept(snap);    // 用户代码在锁内执行
          }
      }
  }
  ```
- **严重程度**: P2
- **现状**: `completionCallback` 在持有 `synchronized` 锁时被调用。如果回调执行慢 I/O（如写入持久存储），它会阻塞此 tracker 上的所有其他 checkpoint 操作。
- **风险**: Checkpoint 延迟尖峰。如果回调获取其他锁，可能死锁。
- **建议**: 先捕获 snapshot，释放锁，再调用回调。
- **信心水平**: 很可能

---

### [AR-12] RecordWriter.emit() 广播时中断导致部分投递

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/RecordWriter.java:144-152`
- **证据片段**:
  ```java
  if (isBroadcast) {
      for (ResultPartition partition : partitions) {
          try {
              partition.write(record);
          } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              throw new StreamException(ERR_STREAM_INTERRUPTED_WRITE, e)...;
              // partitions[0..i-1] 已有记录，partitions[i..n-1] 没有
          }
      }
  }
  ```
- **严重程度**: P2
- **现状**: 广播期间，如果 `InterruptedException` 在写入部分分区后发生，记录被部分投递。下游 subtask 看到不一致的广播子集。
- **风险**: 广播流中各并行 subtask 状态不一致。
- **建议**: 记录此限制，或实现重试/两阶段投递。
- **信心水平**: 很可能

---

### [AR-13] StreamExecutionEnvironment.defaultCheckpointExecutorFactory 是 static mutable 但非 volatile

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/environment/StreamExecutionEnvironment.java:63`
- **证据片段**:
  ```java
  private static ICheckpointExecutorFactory defaultCheckpointExecutorFactory;
  ```
- **严重程度**: P2
- **现状**: 静态可变字段，跨所有 `StreamExecutionEnvironment` 实例共享。无 `volatile` 修饰。并行测试中一个测试设置此字段会影响所有其他测试，且跨线程不可见。
- **风险**: 并行测试中的交叉污染和竞态条件。
- **建议**: 添加 `volatile` 或改为实例级配置。
- **信心水平**: 确定

---

### [AR-14] InputGate.handleWatermarkNonRecursive() 不防逆向 watermark，内部状态可被永久腐蚀

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/InputGate.java:313-314`
- **证据片段**:
  ```java
  long oldWatermark = currentWatermarks[channelIndex];
  currentWatermarks[channelIndex] = watermark.getTimestamp();  // 盲目覆盖，即使更小
  ```
- **严重程度**: P2
- **现状**: 如果收到时间戳低于当前 watermark 的 watermark（违反单调性），`currentWatermarks[channelIndex]` 被更新为更小的值。发射守卫（`newMin > oldMin`）阻止发射逆向 watermark，但内部状态已退化——最小 watermark 卡在错误值直到该通道追上。
- **风险**: Watermark 传播停滞。基于 event-time 的 window 算子不会正确触发。
- **建议**: 添加单调性守卫：`currentWatermarks[channelIndex] = Math.max(currentWatermarks[channelIndex], watermark.getTimestamp())`。
- **信心水平**: 有趣的猜测（取决于上游是否保证单调 watermark）

---

### [AR-15] Lockable.release() 过度释放时静默返回成功

- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/sharedbuffer/Lockable.java:54-62`
- **证据片段**:
  ```java
  boolean release() {
      int old;
      do {
          old = refCounter.get();
          if (old <= 0) {
              return true;      // 返回"无更多引用"但未执行任何 CAS
          }
      } while (!refCounter.compareAndSet(old, old - 1));
      return old == 1;
  }
  ```
- **严重程度**: P2
- **现状**: 当 `refCounter` 已 ≤ 0 时，`release()` 返回 `true`（"可安全移除"）而不执行任何原子操作。双重释放不会报错，而是静默导致条目被删除。
- **风险**: 引用计数 bug 被掩盖而非暴露。可能导致 use-after-free：其他 computation state 引用的 SharedBuffer 条目被删除，后续 `getEntry`/`getEvent` 返回 null → NPE 或丢失匹配。
- **建议**: 当 `old <= 0` 时抛出 `IllegalStateException` 而非静默返回 `true`。
- **信心水平**: 确定

---

### [AR-16] NFA 无 window 约束时状态爆炸

- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/NFA.java:617-752`（computeNextStates）
- **严重程度**: P2
- **证据片段**: （逻辑分散在 computeNextStates 中——start state 总是重新添加，每个事件生成新 computation state）
- **现状**: 对于没有 `within()` 约束的 permissive 模式（如 `followedByAny` + `oneOrMore()`），每个事件产生新的 computation state。`advanceTime` 只在 `windowTime > 0` 时修剪状态，无 window 的模式会无限积累。无并发 partial match 数量上限。
- **风险**: 高吞吐流上的 OOM。
- **建议**: 编译时要求非平凡模式必须有 `within()`，或添加可配置的 partial match 上限。
- **信心水平**: 确定
- **发现来源视角**: 10x 规模运维者

---

### [AR-17] SharedBufferAccessor.extractPatterns() 指数路径爆炸

- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/sharedbuffer/SharedBufferAccessor.java:124-203`
- **严重程度**: P2
- **现状**: 对于 `followedByAny` 模式和多个共享事件，DFS 提取对每个分支做完整路径复制。无结果列表大小或计算时间限制。单个匹配提取可消耗无限 CPU 和内存。
- **风险**: CPU/内存耗尽 DoS。
- **建议**: 添加可配置的最大提取模式数量限制。
- **信心水平**: 确定

---

### [AR-18] MessageSourceFunction.subscription 非 volatile → cancel() 可能看到 null

- **文件**: `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/MessageSourceFunction.java:49,110,134-136`
- **证据片段**:
  ```java
  private IMessageSubscription subscription;    // 非 volatile

  // run() 中写入（run 线程）:
  subscription = messageService.subscribe(...);

  // cancel() 中读取（可能是另一个线程）:
  if (subscription != null) {
      subscription.cancel();
  }
  ```
- **严重程度**: P2
- **现状**: `subscription` 在 `run()` 线程中写入，在 `cancel()` 线程中读取。无 `volatile` 或同步，`cancel()` 可能看到过期的 null 值。
- **风险**: 资源泄漏——消息订阅在 `cancel()` 后继续消费。
- **建议**: 将 `subscription` 声明为 `volatile`。
- **信心水平**: 确定

---

### [AR-19] BatchConsumerSinkFunction.close() 中 flush 失败导致数据丢失

- **文件**: `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/BatchConsumerSinkFunction.java:92-108`
- **证据片段**:
  ```java
  public void close() {
      try {
          if (!flushed) {
              flush();             // 如果抛异常，buffer 数据仍在
              flushed = true;
          }
      } finally {
          if (consumer instanceof AutoCloseable) {
              ((AutoCloseable) consumer).close();   // consumer 被关闭，buffer 数据永远丢失
          }
      }
  }
  ```
- **严重程度**: P2
- **现状**: `close()` 先 `flush()`，然后在 finally 中关闭 consumer。如果 `flush()` 抛出（consumer 临时不可用），异常传播，consumer 在 finally 中被关闭。buffer 中的数据永远丢失。
- **风险**: close 时 consumer 不可用导致数据丢失。
- **建议**: catch flush 异常，记录未刷写的记录数，尝试 dead-letter 写入。
- **信心水平**: 很可能

---

### [AR-20] CepPatternBuilder 通过 model 定义的 eval 函数 + safeLoadClass 存在 RCE 风险

- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/model/builder/EvalPatternBuilder.java:27-29` 和 `CepPatternBuilder.java:158`
- **证据片段**:
  ```java
  // EvalFunctionCondition:
  return ConvertHelper.toTruthy(action.call2(null, value, ctx, null));

  // CepPatternBuilder.addQualifier:
  pattern.subtype(ClassHelper.safeLoadClass(partModel.getSubType()));
  ```
- **严重程度**: P2
- **现状**: CEP 模型文件中定义的 eval 函数和子类型类名在加载时直接执行/加载。如果模型文件来自不可信来源（如用户上传的规则定义），可实现任意代码执行。
- **风险**: 模型文件来自不可信来源时的 RCE。
- **建议**: 确保模型文件仅从可信路径加载。在文档中添加安全说明。
- **信心水平**: 确定（已知设计权衡，但值得记录）

---

### [AR-21] Fraud 示例中 GeographicAnomalyPattern 和 AccountTakeoverPattern 的 IterativeCondition 只检查第一个匹配事件

- **文件**: `nop-stream/nop-stream-fraud-example/src/main/java/io/nop/stream/fraud/pattern/GeographicAnomalyPattern.java:91-98` 和 `AccountTakeoverPattern.java:94-96`
- **证据片段**:
  ```java
  for (TransactionEvent city1Event : ctx.getEventsForPattern("city1")) {
      if (!value.getUserId().equals(city1Event.getUserId())) {
          return false;    // 第一个不匹配就返回，不检查后续
      }
      return !value.getCity().equals(city1Event.getCity());  // 只检查第一个
  }
  ```
- **严重程度**: P2
- **现状**: for 循环体内的 `return` 语句意味着只检查第一个 `city1Event`。如果存在多个 partial match（非 keyed 场景），第一个可能来自不同用户，直接返回 `false`，跳过来自相同用户的有效匹配。
- **风险**: 非 keyed 场景下的欺诈检测假阴性。作为模板代码可能误导后续开发者。
- **建议**: 遍历所有事件，如果任一匹配则返回 `true`。
- **信心水平**: 很可能

---

### [AR-22] handleBarrierNonRecursive() 误导性缩进

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/InputGate.java:296-301`
- **证据片段**:
  ```java
              }   // 关闭 if (!barrierAlignment)，12 空格缩进

          if (barriersRemaining <= 0) {    // 8 空格缩进，但在 if (!barrierReceived) 内部
              CheckpointBarrier aligned = pendingBarrier;
              resetBarrierState();
              return Optional.of(aligned);
          }
      } else {
  ```
- **严重程度**: P2
- **现状**: `if (barriersRemaining <= 0)` 块在 `if (!barrierReceived[channelIndex])` 内部，但缩进级别与外层方法体相同（8 空格而非 12 空格）。Java 语义由花括号决定，当前逻辑正确，但视觉嵌套与实际嵌套不匹配。
- **风险**: 任何未来的自动格式化或手动修正缩进的操作都可能破坏花括号嵌套，将 barrier alignment 逻辑变成死代码。
- **建议**: 修正缩进为 12 空格。
- **信心水平**: 确定

---

### [AR-23] GraphModelCheckpointExecutor DRAIN 模式在 awaitCompletion 后才触发 terminal savepoint

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java:86-87`
- **证据片段**:
  ```java
  submitAndRun(execPlan, tasks, executor);             // 阻塞到所有任务完成
  handleJobTermination(allInvokables, coordinator, checkpointConfig);  // DRAIN 模式触发 savepoint
  ```
- **严重程度**: P2
- **现状**: `submitAndRun` 调用 `awaitCompletion()` 阻塞到所有任务完成。然后 `handleJobTermination(DRAIN)` 触发 terminal savepoint。DRAIN 模式的语义应是：停止 source → 处理 in-flight 数据 → savepoint。但当前实现在所有任务完成后才 savepoint，且从未调用 `truncateForDrain()`。
- **风险**: 对无界 source，DRAIN 模式永远不会终止（等同于 CANCEL）。对有界 source，terminal savepoint 无实际意义（无状态可保存）。
- **建议**: DRAIN 模式应在 `awaitCompletion()` 之前对 source 调用 `truncateForDrain()`。
- **信心水平**: 确定

---

## 总评

nop-stream 是一个功能丰富的流处理引擎，核心执行管线（StreamGraph → JobGraph → PartitionedPlan → TaskExecutor）架构清晰，测试覆盖较全面（90+ 测试类）。本次审查发现的三个最值得关注的方向：

1. **执行管线关闭协议的鲁棒性**：AR-1（ResultPartition 死锁）、AR-2（InputGate 提前终止）、AR-3（InterruptedException 吞噬）是同一主题的三个变体——"关闭/终止"路径在生产路径中是二等公民。这三个问题在任何真实的背压或慢消费者场景下都会触发，建议作为一组一起修复。

2. **CEP SharedBuffer 的锁/引用计数正确性**：AR-4（flushCache 数据丢失）、AR-5（dangling cleanup 泄漏）、AR-15（过度释放静默成功）构成了一个连锁问题——SharedBuffer 的引用计数协议在错误路径上不够健壮。对于 CEP 引擎来说，这是影响生产可用的核心问题。

3. **分布式执行框架的成熟度**：AR-7（硬编码超时）、AR-9（finally 块泄漏）、AR-23（DRAIN 模式不完整）表明 EmbeddedDistributedExecutor 和 GraphModelCheckpointExecutor 的分布式模式还处于早期阶段，不适合生产工作负载。

## 本次审查的盲区自评

1. **代码生成管线**：未审查 `_gen/` 目录下的生成文件与模板的一致性（XDSL → StreamModel 的生成链路）。
2. **nop-stream-api / nop-stream-flow / nop-stream-flink / nop-stream-checkpoint**：README 标注为"规划中"，本次审查聚焦于活跃模块。
3. **序列化正确性**：TypeRegistry 和 StreamElementCodec 的跨进程序列化/反序列化未深入验证。
4. **性能热点**：未做基准测试，无法判断窗口算子、状态后端在高吞吐下的表现。
5. **nop-stream-core 中的 state sharding**：发现了 state shard 相关测试但未深入审查其正确性。

## 按严重程度分布表

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0      | 1    | 执行管线死锁 |
| P1      | 7    | 数据丢失、资源泄漏、静默终止 |
| P2      | 14   | 内存泄漏、设计缺陷、代码质量 |
| P3      | 0    | — |
