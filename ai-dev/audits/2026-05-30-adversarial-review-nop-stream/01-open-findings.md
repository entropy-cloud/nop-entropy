# nop-stream 对抗性审查 — Round 1

> 审查日期：2026-05-30
> 审查范围：nop-stream 全模块（10 个子模块），开放式发现导向
> 审查方法：3 个并行探索 agent 分别聚焦 execution 核心 + runtime、CEP NFA/SharedBuffer 内部、connector + flow + checkpoint
> 去重：已阅读以下已有报告，本报告不重复其中内容：
> - `ai-dev/audits/2026-05-22-adversarial-review-nop-stream/`（Round 1，31 个发现 N42-N72，含 InputGate 递归 StackOverflow、barriersRemaining 下溢、topologicalSort 无环检测、WindowOperator timer 未注册、markIdle 空操作、SharedBufferAccessor NPE、NFA 无限循环、RecordWriter 忽略 partitioner、NFAState.equals 不可靠、BatchConsumerSinkFunction flush 重复处理等）
> - `ai-dev/audits/2026-05-25-deep-audit-nop-stream-full/`（20 维度系统性审查）
> - `ai-dev/audits/2026-05-27-deep-audit-nop-stream-r1/`（4 维度复查）
> - `ai-dev/audits/2026-05-28-deep-audit-nop-stream/`（9 维度深度审查，含依赖图、API、ORM、代码生成、Delta、BizModel、IoC、错误处理）
> - `ai-dev/analysis/2026-04-02-nop-stream-review.md`（综合分析）
> - `ai-dev/analysis/2026-05-20-nop-stream-duplicate-code-audit.md`（重复代码审计）
> 发现来源视角：异常路径侦探 + 10x 规模运维者 + 死代码清道夫 + 事务边界追踪者

---

## 发现分类

| 类别 | 数量 | 最严重 |
|------|------|--------|
| CEP SharedBuffer 正确性 | 4 | P0 |
| 执行引擎正确性 | 4 | P1 |
| Connector 资源管理/并发 | 6 | P1 |
| Checkpoint 正确性 | 1 | P1 |
| 代码质量/API 契约 | 4 | P2 |

---

## P0：CEP SharedBuffer 正确性

### [AR-1] SharedBufferAccessor.releaseNode 版本栈解同步 → 内存泄漏与潜在模式匹配损坏

- **文件**: `nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/sharedbuffer/SharedBufferAccessor.java:263-271`
- **证据片段**:
  ```java
  while (!nodesToExamine.isEmpty()) {
      NodeId curNode = nodesToExamine.pop();
      Lockable<SharedBufferNode> curBufferNode = sharedBuffer.getEntry(curNode);

      if (curBufferNode == null) {
          continue;   // ← 版本未弹出！栈解同步
      }

      DeweyNumber currentVersion = versionsToExamine.pop();
  ```
- **严重程度**: P0
- **现状**: `nodesToExamine` 和 `versionsToExamine` 是必须成对弹出的平行栈。当 `getEntry(curNode)` 返回 null（节点已被先前路径释放或从 cache 驱逐）时，代码 `continue` 而不弹出对应的版本。后续每次迭代弹出的版本都属于错误的节点。
- **具体触发场景**: 节点 A 有两条边 E1(V1)→B 和 E2(V2)→B 指向同一节点 B。releaseNode(A, V) 处理两条边，将 B 推入栈两次。第一次弹出 B+V2 处理后 B 的 refCount 归零被移除。第二次弹出 B 时 `getEntry(B)` 返回 null → `continue` 跳过 V1 → V1 及其子图中的所有节点/边永远不会被释放。
- **风险**: 长期运行的 CEP 作业中 SharedBuffer 内存持续增长直至 OOM。更严重时，解同步后的错误版本可能应用于其他节点的边，提前释放仍在使用中的条目，导致模式匹配结果损坏。
- **建议**: 在 `curBufferNode == null` 分支中弹出并丢弃对应版本：`versionsToExamine.pop();`。或在循环顶部同时弹出两者，然后判断。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探（追踪 releaseNode 的所有分支）

---

### [AR-2] CepOperator.registeredEventTimeTimers 无界增长 → 内存泄漏与 checkpoint 膨胀

- **文件**: `nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java:209-257, 349-364`
- **证据片段**:
  ```java
  // 注册（行 239）
  @Override
  public void registerEventTimeTimer(VoidNamespace namespace, long time) {
      registeredEventTimeTimers.add(time);
  }

  // 删除（行 244）— 存在但已处理的 timer 从未调用
  @Override
  public void deleteEventTimeTimer(VoidNamespace namespace, long time) {
      registeredEventTimeTimers.remove(time);
  }

  // onEventTime 消费 timer（行 349-364）
  while (!sortedTimestamps.isEmpty()
          && sortedTimestamps.peek() <= timerService.currentWatermark()) {
      long timestamp = sortedTimestamps.poll();
      advanceTime(nfaState, timestamp);
      elementQueueState.remove(timestamp);
      // ❌ 未调用 deleteEventTimeTimer(timestamp)
  }
  ```
- **严重程度**: P1
- **现状**: `bufferEvent()` 通过 `registerTimer()` → `registerEventTimeTimer()` 向 `TreeSet<Long> registeredEventTimeTimers` 添加时间戳。当 `onEventTime` 处理并排空该时间戳的元素时，从 `elementQueueState` 移除但**从未调用 `deleteEventTimeTimer`**。TreeSet 条目永久残留。
- **风险**: (1) 内存泄漏：TreeSet 无界增长。(2) Checkpoint 膨胀：`forEachEventTimeTimer()` 在快照时写入每个 timer，长时间运行后 checkpoint 包含数百万过期条目，增加快照/恢复时间。(3) 恢复后过期 timer 触发空处理，浪费 CPU。
- **建议**: 在 `onEventTime` 的 while 循环中，`elementQueueState.remove(timestamp)` 后添加 `registeredEventTimeTimers.remove(timestamp)` 或通过 timerService 删除。
- **信心水平**: 确定
- **发现来源视角**: 10x 规模运维者（长时间运行下的内存增长）

---

### [AR-3] Lockable.release() TOCTOU 竞态 — 与其 Javadoc 声称的线程安全矛盾

- **文件**: `nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/sharedbuffer/Lockable.java:54-60`
- **证据片段**:
  ```java
  // Javadoc 声称: "lock() and release() are thread-safe for concurrent access"
  boolean release() {
      if (refCounter.get() <= 0) {       // step 1: 非原子读
          return true;
      }
      return refCounter.decrementAndGet() == 0;  // step 2: 原子减
  }
  ```
- **严重程度**: P1
- **现状**: `get()` 和 `decrementAndGet()` 之间的 check-then-act 序列不是原子的。两个线程可同时读到 `refCounter = 1`，都通过守卫，都递减 — 计数器变为 -1。负值后 `release()` 每次调用都返回 `true`（`<= 0` 守卫），导致 SharedBuffer 条目的级联提前移除。
- **风险**: 当前 CEP operator 单线程 per key 使用，是潜在缺陷。但 Javadoc 明确声称线程安全，且 SharedBuffer 使用 ConcurrentHashMap 存储 Lockable 值（暗示并发使用场景）。任何未来引入并发的改动都会立即触发此 bug。修复简单：CAS 循环 `compareAndSet(old, old - 1)`。
- **建议**: 改为 CAS 循环或移除 Javadoc 中的线程安全声明。
- **信心水平**: 很可能
- **发现来源视角**: IoC 侦探（API 契约与实现不一致）

---

### [AR-4] Lockable.hashCode() 可变性违反 Object.hashCode 契约

- **文件**: `nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/sharedbuffer/Lockable.java:87-90`
- **证据片段**:
  ```java
  @Override
  public int hashCode() {
      return Objects.hash(refCounter.get(), element);  // lock/release 时变化！
  }
  ```
- **严重程度**: P2
- **现状**: `hashCode()` 依赖 `refCounter.get()`，每次 `lock()`/`release()` 都会改变。将 `Lockable` 放入 `HashMap`/`HashSet` 后修改 refCount，会破坏哈希集合的不变量（桶位置不再匹配当前 hashCode）。
- **风险**: 当前 Lockable 作为 MapState 的值使用（非 key），直接影响有限。但 `SharedBufferNode.equals()` 通过 `Objects.equals(edges, ...)` 间接依赖 `Lockable.equals()`。如果未来代码将 Lockable 用作 Set 或 Map key，会静默崩溃。
- **建议**: `hashCode()` 应只依赖 `element`（不可变部分），或在文档中明确禁止在哈希集合中使用。
- **信心水平**: 确定

---

## P1：执行引擎正确性

### [AR-5] AbstractStreamOperator.processBarrier 吞掉快照异常 → 不完整 checkpoint 被存为有效

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/operators/AbstractStreamOperator.java:262-285`
- **证据片段**:
  ```java
  public void processBarrier(CheckpointBarrier barrier) throws Exception {
      OperatorSnapshotResult snapshotResult = null;
      Exception snapshotError = null;
      if (barrier.snapshot()) {
          try {
              StateSnapshotContext context = new StateSnapshotContext(barrier.getId(), barrier.getTimestamp());
              snapshotResult = snapshotState(context);
              this.lastSnapshotResult = snapshotResult;
          } catch (Exception e) {
              snapshotError = e;
          }
      }
      if (output != null) {
          output.emitBarrier(barrier);  // barrier 仍然转发
      }
      if (snapshotCallback != null) {
          if (snapshotResult != null) {
              snapshotCallback.accept(snapshotResult);
          } else if (snapshotError != null) {
              OperatorSnapshotResult failureResult = new OperatorSnapshotResult();
              snapshotCallback.accept(failureResult);  // 空 result 通知 callback
          }
          // snapshotError 本身：未记录、未重抛、未存储
      }
  }
  ```
- **严重程度**: P1
- **现状**: 当 `snapshotState()` 抛异常时，错误被捕获但完全吞掉——不记录日志、不重新抛出、不存储在结果中。barrier 仍转发给下游。空的 `OperatorSnapshotResult` 发送给 callback。CheckpointBarrierTracker 将其确认为成功 checkpoint，不完整 checkpoint 被存储为有效。恢复时 operator 状态丢失。
- **风险**: 快照失败被静默吞掉，导致恢复后数据丢失。与 N53（BatchConsumerSinkFunction flush 重复处理）叠加：如果 flush 失败触发了快照异常，数据可能既重复又丢失。
- **建议**: (1) 至少记录 `snapshotError` 的 WARN 日志。(2) 将错误信息存入 `OperatorSnapshotResult` 的错误字段。(3) 考虑将错误传播给 callback 或设置 barrier 为失败状态。
- **信心水平**: 确定
- **发现来源视角**: 事务边界追踪者（checkpoint 一致性）

---

### [AR-6] InputGate.readMultiChannel 200 轮空读超时 → 慢上游时 task 过早退出

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/execution/InputGate.java:204-266`
- **证据片段**:
  ```java
  private Optional<StreamElement> readMultiChannel() {
      int emptyRounds = 0;
      while (true) {
          // ... round-robin read all channels ...

          if (isAllFinished()) {
              return Optional.empty();
          }

          emptyRounds++;
          if (emptyRounds >= 200) {
              return Optional.empty();  // ← 过早退出！
          }
          Thread.sleep(10);
      }
  }
  ```
- **严重程度**: P1
- **现状**: 连续 200 轮完整 round-robin 无数据后，方法返回 `Optional.empty()`。调用方 `StreamTaskInvokable.processInputGate` 将此解释为 end-of-stream 并退出处理循环。以 50ms/channel 超时 + 10ms sleep 估算，约 200 × (50ms × N + 10ms) ≈ 数十秒无数据即触发。如果上游 task 较慢（处理密集型算子、数据库查询），下游 task 可能在上游仍在产出数据时退出。
- **风险**: 上游处理速度不均匀时，下游 task 静默丢失后续数据。这不是边界情况——任何涉及 I/O 密集型 source 的 pipeline 都可能触发。
- **建议**: 移除此超时，改为仅依赖 `isAllFinished()` 检测。如果需要空闲超时，应作为显式配置参数而非硬编码常量。
- **信心水平**: 确定
- **发现来源视角**: 10x 规模运维者（慢生产者场景）

---

### [AR-7] StreamTaskInvokable.processInputGate 失败 → 上游 task 死锁

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/execution/StreamTaskInvokable.java:314-331`
- **证据片段**:
  ```java
  private void processInputGate(Input<Object> headInput) throws Exception {
      while (true) {
          Optional<StreamElement> elementOpt = inputGate.read();
          if (!elementOpt.isPresent()) {
              break;
          }
          StreamElement element = elementOpt.get();
          if (element.isRecord()) {
              headInput.processElement((StreamRecord<Object>) element.asRecord());
              // ↑ 如果抛异常，异常传播出 invokeMiddle/invokeSink
          }
          // ...
      }
  }
  ```
- **严重程度**: P1
- **现状**: 如果 `headInput.processElement()` 抛异常（用户函数错误），异常传播出 `invokeMiddle()` 或 `invokeSink()`，`outputWriter` 在 finally 块中关闭但不通知上游生产者。上游 `ResultPartition` 仍接受写入，但下游 `InputGate` 永远不再读取，导致上游 task 永远阻塞在 `ResultPartition.write()` 的 `queue.put()` 上。
- **风险**: 多 task 图执行中，中间或 sink task 的失败导致死锁——上游 task 阻塞在满队列上，整个作业永远不完成也不可见地失败。
- **建议**: 在 `ResultPartition` 中引入中断/取消机制（如 poison pill 或 `close()` 标记），或在 `StreamTaskInvokable` 的异常处理路径中主动关闭 `inputGate` 的上游连接。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探（追踪 processElement 异常的传播路径）

---

### [AR-8] GraphExecutionPlan.build 只使用第一个 OperatorChain — 其余链静默丢弃

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/execution/GraphExecutionPlan.java:187-189`
- **证据片段**:
  ```java
  for (int taskIndex = 0; taskIndex < parallelism; taskIndex++) {
      OperatorChain chain = taskIndex == 0
              ? original.getOperatorChains().get(0)  // 只取第一条链
              : original.getOperatorChains().get(0).deepCopy();
  ```
- **严重程度**: P1
- **现状**: 代码总是取 `getOperatorChains().get(0)`，忽略 vertex 的所有其他 operator chain。如果 `JobVertex` 有多条 operator chain（如 union 多输入的有效配置），只有第一条被执行。其余静默丢弃，无错误、无警告。此外，如果 `getOperatorChains()` 返回空 list，`IndexOutOfBoundsException` 而非描述性错误。
- **风险**: 多链 vertex（如 union 后的多分支处理）静默丢失数据处理分支。
- **建议**: 要么遍历所有 chain 创建对应 task，要么在 `getOperatorChains().size() > 1` 时抛出明确的 unsupported 异常。
- **信心水平**: 确定

---

## P1：Connector 资源管理/并发

### [AR-9] BatchConsumerSinkFunction 不重写 finish() → finish 时缓冲数据未刷写

- **文件**: `nop-stream-connector/src/main/java/io/nop/stream/connector/BatchConsumerSinkFunction.java:31, 58-64`
- **证据片段**:
  ```java
  public class BatchConsumerSinkFunction<R> implements SinkFunction<R>, AutoCloseable {
      // ...
      @Override
      public void consume(R value) {
          buffer.add(value);
          if (buffer.size() >= batchSize) {
              flush();
          }
      }
      // 未重写 SinkFunction.finish() — 默认 no-op
  }
  ```
- **严重程度**: P1
- **现状**: 框架生命周期为 `AbstractUdfStreamOperator.finish()` → `SinkFunction.finish()`（默认 no-op）→ `StreamSinkOperator.close()` → `AutoCloseable.close()` → `flush()`。数据最终通过 `close()` 路径刷写。但 `finish()` 到 `close()` 之间可能触发最终 checkpoint，此时缓冲数据尚未持久化。如果作业在 `close()` 执行前失败，缓冲数据丢失（默认 batchSize=100 时最多 99 条记录）。
- **风险**: 有限流作业结束时，`finish()` 与 `close()` 之间的故障窗口内数据丢失。`close()` 中 `flush()` 失败时 consumer 的 `close()` 也不被调用（资源泄漏）。
- **建议**: (1) 重写 `finish()` 调用 `flush()`。(2) `close()` 中使用 try-finally 确保 consumer 清理。
- **信心水平**: 确定
- **发现来源视角**: 事务边界追踪者（数据完整性窗口）

---

### [AR-10] MessageSourceFunction.subscription 非 volatile → cancel() 可能看不到引用

- **文件**: `nop-stream-connector/src/main/java/io/nop/stream/connector/MessageSourceFunction.java:48, 103, 122-123`
- **证据片段**:
  ```java
  // 行 48：非 volatile
  private IMessageSubscription subscription;

  // 行 103：run() 线程写入
  subscription = messageService.subscribe(effectiveTopic, new IMessageConsumer() { ... });

  // 行 122-123：cancel() 线程读取
  if (subscription != null) {   // 可能看到陈旧 null
      subscription.cancel();
  }
  ```
- **严重程度**: P1
- **现状**: 经典 Java 内存模型违规：`subscription` 无 `volatile`，`run()` 写入与 `cancel()` 读取之间无 happens-before 关系。如果 `cancel()` 在 `run()` 开始后很快调用，可能看到 `subscription == null` 即使 `run()` 已赋值。订阅泄漏（永不取消），持续向死 SourceContext 投递消息。
- **风险**: 订阅泄漏。对比 `DebeziumCdcSourceFunction`（同模块的兄弟类）已正确标记 `subscription` 为 `volatile`，确认这是遗漏。
- **建议**: 声明为 `private volatile IMessageSubscription subscription;`
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探（并发可见性）

---

### [AR-11] MessageSourceFunction.shutdownLatch 是 transient 但反序列化后未重初始化 → NPE

- **文件**: `nop-stream-connector/src/main/java/io/nop/stream/connector/MessageSourceFunction.java:49, 111-112`
- **证据片段**:
  ```java
  // 行 49：transient + 内联初始化
  private transient volatile CountDownLatch shutdownLatch = new CountDownLatch(1);

  // 行 111-112：反序列化后直接使用，null guard 缺失
  while (running) {
      shutdownLatch.await(1, TimeUnit.SECONDS);  // 反序列化后 NPE！
  }
  ```
- **严重程度**: P1
- **现状**: `transient` 字段在 Java 反序列化后为 null（构造器不调用，内联初始化器不执行）。`run()` 方法直接调用 `shutdownLatch.await()` 导致 NPE。兄弟类 `DebeziumCdcSourceFunction` 已正确处理此问题（使用 `initCompletionLatch()` 双检锁模式）。
- **风险**: 反序列化后 source 立即崩溃。
- **建议**: 采用与 `DebeziumCdcSourceFunction.initCompletionLatch()` 相同的延迟初始化模式。
- **信心水平**: 确定

---

### [AR-12] MessageSourceFunction.run() cancel-before-run 竞态 → 订阅泄漏

- **文件**: `nop-stream-connector/src/main/java/io/nop/stream/connector/MessageSourceFunction.java:99-113, 117-125`
- **证据片段**:
  ```java
  // run() — 订阅在 running 检查之前创建
  public void run(SourceContext<T> ctx) throws Exception {
      subscription = messageService.subscribe(effectiveTopic, ...);  // 无条件创建
      while (running) {  // 如果 cancel() 已设 running=false，立即退出
          shutdownLatch.await(1, TimeUnit.SECONDS);
      }
  }

  // cancel() — 如果在 run() 之前调用，subscription 为 null
  public void cancel() {
      running = false;
      if (subscription != null) { subscription.cancel(); }  // null
  }
  ```
- **严重程度**: P1
- **现状**: 竞态场景：(1) `cancel()` 先调用 → `running=false`, `subscription=null`。(2) `run()` 启动 → 创建订阅（忽略 `running` 标志），进入 while 循环。(3) while 看到 `running=false`，立即退出。(4) 新创建的订阅永远不被取消。`DebeziumCdcSourceFunction` 在创建 source 前检查 `!draining` 标志以防范此竞态。
- **建议**: 在 `run()` 中创建订阅前检查 `running`，或创建后立即二次检查并在 false 时取消。
- **信心水平**: 很可能

---

### [AR-13] DebeziumCdcSourceFunction.source 和 subscription 非 transient 但持有非序列化对象

- **文件**: `nop-stream-connector/src/main/java/io/nop/stream/connector/DebeziumCdcSourceFunction.java:34-35`
- **证据片段**:
  ```java
  private volatile DebeziumMessageSource source;    // 非 transient
  private volatile ICancellable subscription;        // 非 transient
  ```
  `DebeziumMessageSource` 内部包含 `ConcurrentHashMap` 和 `DebeziumEngineWrapper`，均非 `Serializable`。
- **严重程度**: P2
- **现状**: `SourceFunction` 通过 `StreamFunction` 继承 `Serializable`。`completionLatch` 已标记 `transient` 并有重初始化守卫，说明作者考虑了序列化但遗漏了这两个字段。分布式部署时框架序列化此函数将抛 `NotSerializableException`。
- **风险**: 分布式部署（GraphModel 执行路径中 task 在线程池执行时序列化传递）会失败。
- **建议**: 标记为 `transient` 并在 `run()` 中延迟初始化。
- **信心水平**: 确定

---

### [AR-14] BatchConsumerSinkFunction.close() 在 flush() 失败时跳过 consumer 清理

- **文件**: `nop-stream-connector/src/main/java/io/nop/stream/connector/BatchConsumerSinkFunction.java:81-91`
- **证据片段**:
  ```java
  @Override
  public void close() {
      flush();                           // 如果抛异常
      if (consumer instanceof AutoCloseable) {
          ((AutoCloseable) consumer).close();  // 永远执行不到
      }
  }
  ```
- **严重程度**: P2
- **现状**: `flush()` 失败时异常传播出 `close()`，consumer 的 `close()` 永远不调用，泄漏资源（数据库连接、文件句柄、网络 socket）。
- **建议**: 使用 try-finally 确保 consumer 清理。
- **信心水平**: 确定

---

## P2：代码质量/API 契约

### [AR-15] BatchConsumerSinkFunction.getSinkConsistency() 声称 IDEMPOTENT 但无幂等保障

- **文件**: `nop-stream-connector/src/main/java/io/nop/stream/connector/BatchConsumerSinkFunction.java:93-96`
- **证据片段**:
  ```java
  @Override
  public SinkConsistencyCapability getSinkConsistency() {
      return SinkConsistencyCapability.IDEMPOTENT;
  }
  ```
- **严重程度**: P2
- **现状**: `IDEMPOTENT` 意味着重新处理相同记录产生相同结果。但 `BatchConsumerSinkFunction` 是通用适配器，对底层 `IBatchConsumerProvider` 无控制。如果 consumer 写入非幂等存储（追加日志、递增计数器），此声明为假。一致性验证测试使用此声明决定 `STRICT_EXACTLY_ONCE` 验证，错误声明可能允许无效 pipeline 配置。
- **建议**: 改为 `AT_LEAST_ONCE`（安全默认值），或委托给底层 consumer provider。
- **信心水平**: 很可能

---

### [AR-16] SharedBufferAccessor.extractPatterns 双重 getEntry 调用 → 性能损失与潜在 NPE

- **文件**: `nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/sharedbuffer/SharedBufferAccessor.java:186-194`
- **证据片段**:
  ```java
  extractionStates.push(
      new ExtractionState(
          target != null
              ? Tuple2.of(
                  target,
                  sharedBuffer.getEntry(target) != null       // 第 1 次查询：null 检查
                      ? sharedBuffer.getEntry(target).getElement()  // 第 2 次查询：取元素
                      : null)
              : null,
          edge.getDeweyNumber(),
          newPath));
  ```
- **严重程度**: P2
- **现状**: `getEntry(target)` 调用两次——一次 null 检查，一次取元素。每次调用检查 ConcurrentHashMap cache 后回退到 `MapState.get()`（可能涉及反序列化）。两次调用之间，条目可能被 cache 驱逐或并发释放，导致第二次返回 null → NPE。
- **建议**: 单次获取存入局部变量。
- **信心水平**: 确定

---

### [AR-17] SharedBuffer cache/state 引用别名在序列化后端下损坏

- **文件**: `nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/sharedbuffer/SharedBuffer.java:288-303, 248-260`
- **严重程度**: P2
- **现状**: `getEntry()` 返回的 Java 对象引用同时存在于 cache 和 state backend。修改共享对象时两者都变。这只在 `MemoryKeyedStateBackend`（引用语义）下正确。如果使用序列化后端（如 RocksDB），`entries.get(nodeId)` 返回反序列化副本，cache 中的修改不会反映到 state，第二次 `getEntry` cache miss 后读到过期副本。
- **风险**: CEP operator 在非内存 state backend 下静默损坏 SharedBuffer 状态。限制了部署选项但未文档化或强制执行。
- **建议**: 要么文档化仅支持内存 state backend，要么在 `upsertEntry` 中同时更新 state（牺牲性能换正确性）。
- **信心水平**: 很可能

---

### [AR-18] CepOperator.onProcessingTime 局部变量 nfa 遮蔽类字段 NFA nfa

- **文件**: `nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java:381`
- **证据片段**:
  ```java
  // onProcessingTime（行 381）
  NFAState nfa = getNFAState();   // ← 遮蔽 this.nfa (NFA<IN> 类型)

  // 对比 onEventTime（行 346）
  NFAState nfaState = getNFAState();  // 不遮蔽
  ```
- **严重程度**: P3
- **现状**: 局部变量 `nfa`（类型 `NFAState`）遮蔽实例字段 `nfa`（类型 `NFA<IN>`）。在 `onProcessingTime` 内无法通过简单名称访问 `this.nfa`。如果开发者添加需要 NFA 实例的代码并使用 `nfa` 以为指的是字段，会得到类型不匹配的编译错误——不危险但令人困惑。
- **建议**: 重命名为 `nfaState`，与 `onEventTime` 保持一致。
- **信心水平**: 确定

---

## 已知未修复问题确认

以下问题在 2026-05-22 审查中报告（N42-N72），经代码验证确认**仍未修复**：

| 原编号 | 摘要 | 严重程度 | 现状变化 |
|--------|------|---------|---------|
| N42 | InputGate.readMultiChannel 递归 StackOverflow | P0 | 未修复 |
| N43 | InputGate barriersRemaining 下溢 | P0 | 未修复 |
| N44 | GraphExecutionPlan.topologicalSort 不检测循环 | P0 | 未修复 |
| N45 | WindowOperator timer 未注册 | P0 | 未修复 |
| N46 | markIdle/markActive 空操作 | P1 | 未修复 |
| N50 | RecordWriter 忽略 partitioner | P0 | 未修复 |
| N55 | TimestampsAndWatermarksOperator 每次 processElement 创建新对象 | P2 | 未修复 |
| N58 | StreamExecutionEnvironment 线程池泄漏 | P1 | 未修复 |
| N59 | GraphExecutionPlan 单 ResultPartition per edge | P1 | 未修复 |

注：以上仅列出影响最高的未修复项。完整列表见原始报告。

---

## 总评

### 最值得关注的 3 个方向

1. **CEP SharedBuffer 引用计数机制脆弱性**（AR-1 + AR-3 + AR-4 + AR-16）：`releaseNode` 的栈解同步是一个确定性的内存泄漏/数据损坏 bug（P0），在长时间运行 + 模式分支的 CEP 作业中必然触发。加上 `Lockable.release()` 的 TOCTOU 竞态（虽然当前单线程下不触发但 API 合同错误）和 `extractPatterns` 的双重获取，整个 SharedBuffer 的引用计数系统需要在正确性层面做系统性审查，而非单个 patch。

2. **执行引擎的容错能力缺失**（AR-5 + AR-6 + AR-7 + AR-8）：快照错误被静默吞掉（AR-5）、慢上游导致 task 过早退出（AR-6）、task 失败导致上游死锁（AR-7）、多链 vertex 静默丢数据（AR-8）——这四个问题组合意味着 nop-stream 在任何非平凡失败场景下都无法保证数据完整性。上一个审查发现的 N42-N50（P0 级）同样集中在执行引擎，说明这一层需要系统性重审而非逐个修补。

3. **Connector 的序列化/并发模式不完整**（AR-10 + AR-11 + AR-12 + AR-13）：`MessageSourceFunction` 有 3 个独立的并发/序列化 bug（subscription volatile 缺失、transient latch NPE、cancel-before-run 竞态），而同模块的 `DebeziumCdcSourceFunction` 已正确处理了前两个。这说明两个类是从不同阶段的代码成熟度写成的，`MessageSourceFunction` 应参照 `DebeziumCdcSourceFunction` 做系统性对齐。

### 本次审查的盲区自评

1. **nop-stream-flow 模块**：由于该模块文件较少且结构性较低，审查深度不足。可能遗漏了流程编排层面的逻辑错误。
2. **nop-stream-flink 适配层**：未审查 Flink 集成的正确性。
3. **nop-stream-runtime 的分布式执行路径**：GraphModel checkpoint executor 的完整生命周期（特别是 job coordinator → task manager → operator 的分布式交互）未完整追踪。
4. **Checkpoint 恢复路径**：快照写入的完整性和恢复时的反序列化路径未深入验证。
5. **性能基准**：所有性能相关发现均为理论分析，无实际负载测试数据支撑。

---

## 按严重程度分布表

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0      | 1    | CEP SharedBuffer 引用计数解同步 |
| P1      | 10   | 执行引擎正确性(4) + CEP 正确性(2) + Connector 并发/序列化(4) |
| P2      | 6    | API 契约(2) + 代码质量(2) + 序列化(1) + 资源管理(1) |
| P3      | 1    | 命名一致性 |
