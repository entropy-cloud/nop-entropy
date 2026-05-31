# Adversarial Review: nop-stream (2026-05-30)

**审查范围**: nop-stream 全部 9 个子模块（422 个源文件、204 个测试文件）
**审查方法**: 开放式发现导向，未绑定固定维度。以"异常路径侦探"和"代码生成受害者"视角起步，后自然扩展到并发安全、序列化一致性、API 契约等领域。
**去重基线**: 已阅读 `ai-dev/audits/` 下所有 nop-stream 相关审计报告（2026-05-20 至 2026-05-28），共约 235+ 条历史发现。本报告仅收录此前未报告或未覆盖的发现。

---

## P0 发现

### [AR-1] SharedBufferAccessor.releaseNode 并行栈版本号脱锁 — 引用计数链式腐败

- **文件**: `nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/sharedbuffer/SharedBufferAccessor.java:263-269`
- **证据片段**:
  ```java
  while (!nodesToExamine.isEmpty()) {
      NodeId curNode = nodesToExamine.pop();
      Lockable<SharedBufferNode> curBufferNode = sharedBuffer.getEntry(curNode);
      if (curBufferNode == null) {
          continue;   // ← BUG: versionsToExamine 没有对应 pop
      }
      DeweyNumber currentVersion = versionsToExamine.pop();
  ```
- **严重程度**: P0
- **现状**: `nodesToExamine` 和 `versionsToExamine` 是必须 1:1 同步的两个并行栈。当 `getEntry` 返回 null（节点已被前一轮迭代释放）时，`continue` 跳过了 `versionsToExamine.pop()`。从此时起，所有后续节点消费错误的 DeweyNumber 版本号。
- **风险**:
  - `isCompatibleWith` 用错误版本号检查 → 该释放的边被跳过（内存泄漏），不该释放的边被释放（匹配数据损坏/静默数据丢失）
  - 引用计数腐败沿整个释放链级联传播
  - 触发条件：任何具有共享结构的模式（如 `A B+ C`，多个 B 事件指向同一个 C 事件的 SharedBuffer 节点）
- **建议**: 在 `continue` 之前加入 `versionsToExamine.pop()`，或将 null 检查合并到 pop 之后的逻辑中
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

### [AR-2] NFACompiler greedy LOOPING+until 的 originalStateMap 键名不匹配 — NPE

- **文件**: `nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/compiler/NFACompiler.java:606-613, 748-750`
- **证据片段**:
  ```java
  // createTimesState(looping, sinkState, ...) — LOOPING case
  if (untilCondition != null) {
      State<T> sinkStateCopy = copy(sinkState);
      originalStateMap.put(sinkState.getName(), sinkStateCopy); // key = looping.getName()
  }
  // ...
  // createSingletonState 查找时:
  singletonState.addProceed(
      originalStateMap.get(proceedState.getName()),  // key = outerSinkState.getName()
      ...);
  ```
- **严重程度**: P0
- **现状**: LOOPING 路径下 `originalStateMap` 以 `looping.getName()` 为键存储，但 `createSingletonState` 以 `proceedState.getName()`（= outerSinkState）为键查找。两个 State 对象由 `stateNameHandler.getUniqueInternalName()` 创建，名称不同 → 查找返回 null → `addProceed(null, condition)` 添加目标为 null 的转换 → NFA 处理时 NPE
- **风险**: 任何 `Pattern.begin("a").oneOrMore().until(cond).greedy().followedBy("b")` 模式在运行时崩溃
- **建议**: 统一 originalStateMap 的 key 为一致的 state name
- **信心水平**: 确定

### [AR-3] StreamReduceOperator.open() 无条件重置 values — 恢复后状态丢失

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/operators/StreamReduceOperator.java:57-60`
- **证据片段**:
  ```java
  @Override
  public void open() throws Exception {
      super.open();
      values = new HashMap<>();
  }
  ```
- **严重程度**: P0
- **现状**: 标准检查点恢复路径是 `restoreState()` → `open()`。`open()` 无条件执行 `values = new HashMap<>()`，销毁 `restoreState()` 刚恢复的数据。对比 `WindowAggregationOperator.open()` 使用 `if (this.windowState == null)` 保护
- **风险**: 每次检查点恢复后所有 reduce 状态丢失 — 静默数据丢失
- **建议**: 添加 `if (values == null)` 保护
- **信心水平**: 确定

### [AR-4] WindowAggregationOperator SimpleAccumulator 检查点使用 getLocalValue()/add() 而非 clone()/merge() — 数值腐败

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/operators/WindowAggregationOperator.java:549-552, 604-611`
- **严重程度**: P0
- **现状**: 序列化时用 `getLocalValue()` 捕获累加器值。反序列化时用 `new instance` + `acc.add(accValue)` 恢复。对于 AverageAccumulator（有 count + sum 字段），`getLocalValue()` 返回派生的平均值（如 5.0），`add(5.0)` 产生 `count=1, sum=5.0` — 与原始 `count=10, sum=50` 完全不同
- **风险**: 任何非平凡累加器（Average、Histogram 等）在检查点恢复后产生完全错误的聚合结果
- **建议**: 使用 `clone()` + `merge()` 语义保存和恢复累加器完整状态
- **信心水平**: 确定

### [AR-5] StreamTaskInvokable fan-out 路径仅关闭第一个 RecordWriter — 下游任务永久挂起

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/execution/StreamTaskInvokable.java:78, 272, 286`
- **证据片段**:
  ```java
  this.outputWriter = !fanOutWriters.isEmpty() ? fanOutWriters.get(0) : null;
  // ...
  } finally {
      if (outputWriter != null) {
          outputWriter.close();  // 只关闭第一个
      }
  }
  ```
- **严重程度**: P0
- **现状**: 当 vertex 有多个出边时，构造函数只存储 `fanOutWriters.get(0)` 作为 `outputWriter`。`invokeSource()` 和 `invokeMiddle()` 的 finally 块只关闭这一个。其余 fan-out writer 的 `ResultPartition` 永远不会关闭
- **风险**: 连接到其他出边的下游任务永久挂起等待数据或 end-of-stream 信号
- **建议**: 存储 `fanOutWriters` 列表，finally 块中关闭所有 writer
- **信心水平**: 确定

### [AR-6] GraphModelCheckpointExecutor 第二个 executeWithCheckpoint 重载丢弃所有 CheckpointConfig

- **文件**: `nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java:115-120`
- **严重程度**: P0
- **现状**: `executeWithCheckpoint(StreamModel, PartitionedPlan, DeploymentPlan)` 创建 `new CheckpointConfig()` 使用全部默认值（interval、timeout、maxConcurrent、storage 等），调用方提供的配置被静默丢弃。第一个重载正确接收 `CheckpointConfig` 参数
- **风险**: 通过 StreamModel 路径执行时，检查点间隔、超时、存储路径等关键配置全部使用默认值，生产环境下可能导致无检查点或存储路径错误
- **建议**: 从 StreamModel 或 DeploymentPlan 中提取配置，或直接传递 CheckpointConfig
- **信心水平**: 很可能

### [AR-7] PatternStreamBuilder inputSerializer 硬编码为 null — 检查点时 NPE

- **文件**: `nop-stream-cep/src/main/java/io/nop/stream/cep/PatternStreamBuilder.java:139`
- **证据片段**:
  ```java
  final TypeSerializer<IN> inputSerializer = null;
  //        inputStream.getType().createSerializer(inputStream.getExecutionConfig());
  ```
- **严重程度**: P0
- **现状**: 原始 Flink 代码的 `inputStream.getType().createSerializer(...)` 被注释掉，替换为 `null`。传入 `CepOperator` 的构造函数。任何需要序列化输入元素的状态后端操作（检查点、savepoint、恢复）将在运行时抛出 NPE
- **风险**: CEP 模式下的检查点完全不可用
- **建议**: 恢复类型推断逻辑或提供替代序列化器
- **信心水平**: 确定

### [AR-8] GraphExecutionPlan + SubtaskTask: 深拷贝算子链的 open() 调用目标错误

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/execution/GraphExecutionPlan.java:186-189, 277-283` + `nop-stream-runtime/src/main/java/io/nop/stream/runtime/taskmanager/SubtaskTask.java:41-42, 108-124`
- **严重程度**: P0
- **现状**: 对于 subtask `taskIndex > 0`，`GraphExecutionPlan.build()` 通过 Java 序列化创建深拷贝的 `OperatorChain`。但 `StreamExecutionEnvironment.execute()` 创建 `SubtaskTask` 时使用 `plan.getExecutionVertices().get(vertexId)`，这是包含原始链的旧 `JobVertex`。`SubtaskTask.openOperatorChains()` 打开的是原始链的算子，而非深拷贝的算子。深拷贝的算子在没有 `open()` 调用的情况下运行
- **风险**: `parallelism>1` 时未初始化的算子导致运行时失败（缺少运行时上下文、状态后端等）
- **建议**: `SubtaskTask` 应使用深拷贝后的 chain 而非原始 chain
- **信心水平**: 很可能

---

## P1 发现

### [AR-9] BatchConsumerSinkFunction 未重写 finish() — 流终止时数据丢失 + 资源泄漏

- **文件**: `nop-stream-connector/src/main/java/io/nop/stream/connector/BatchConsumerSinkFunction.java:82-98`
- **严重程度**: P1
- **现状**: `SinkFunction` 定义了 `finish()` 作为流终止时的生命周期钩子。`AbstractUdfStreamOperator.finish()` 会调用 `((SinkFunction<?>) userFunction).finish()`。`BatchConsumerSinkFunction` 实现了 `AutoCloseable.close()` 但未重写 `finish()`。框架调用 `finish()` 时执行默认空操作。缓冲区中剩余记录永远不会刷新，consumer 不会关闭
- **风险**: 流终止时数据丢失 + IBatchConsumer 资源泄漏
- **建议**: 重写 `finish()` 调用 `flush()` + `consumer.close()`，或确保框架在 `finish()` 后调用 `close()`
- **信心水平**: 确定

### [AR-10] BatchConsumerSinkFunction.flush() 异常阻止 consumer.close() — 资源泄漏

- **文件**: `nop-stream-connector/src/main/java/io/nop/stream/connector/BatchConsumerSinkFunction.java:83-92`
- **证据片段**:
  ```java
  public void close() {
      flush();       // 如果抛异常
      if (consumer instanceof AutoCloseable) {
          ((AutoCloseable) consumer).close(); // 永远不会执行
      }
  }
  ```
- **严重程度**: P1
- **现状**: `close()` 中 `flush()` 抛异常时，`consumer.close()` 永远不会执行
- **风险**: IBatchConsumer 资源泄漏（数据库连接、文件句柄等）
- **建议**: 使用 try-finally 确保 consumer.close() 总是执行
- **信心水平**: 确定

### [AR-11] CepPatternBuilder.addQualifier 对 GroupPattern 调用 subtype() — UnsupportedOperationException

- **文件**: `nop-stream-cep/src/main/java/io/nop/stream/cep/model/builder/CepPatternBuilder.java:146-152`
- **严重程度**: P1
- **现状**: `addQualifier` 接收 `CepPatternPartModel`（`CepPatternSingleModel` 和 `CepPatternGroupModel` 的公共基类）。如果 group model 的 part 设置了 `subType`（继承自 `_CepPatternPartModel`），`pattern.subtype(...)` 会被调用到 `GroupPattern` 上，抛出 `UnsupportedOperationException`。构建器对 single 和 group part 无条件应用 `addQualifier`
- **风险**: 任何设置了 subType 的 group pattern 定义在构建时崩溃
- **建议**: 添加 `if (pattern instanceof GroupPattern) ... else ...` 分支处理
- **信心水平**: 确定

### [AR-12] CepPatternBuilder 量化方法对 GroupPattern 无保护 — 静默产生畸形 NFA

- **文件**: `nop-stream-cep/src/main/java/io/nop/stream/cep/model/builder/CepPatternBuilder.java:154-180`
- **严重程度**: P1
- **现状**: `Pattern.greedy()` 正确检查了 `checkIfNoGroupPattern()`，但 `oneOrMore`、`times`、`timesOrMore`、`consecutive`、`allowCombinations`、`optional` 等方法都没有 GroupPattern 保护。通过构建器，`CepPatternGroupModel` part 可以设置 `oneOrMore=true` 或 `times`，导致 `GroupPattern` 上应用这些量词，产生不正确的 NFA
- **风险**: 静默产生行为异常的 NFA，匹配结果不正确
- **建议**: 所有量化方法添加 GroupPattern 保护，或在构建器层面过滤
- **信心水平**: 很可能

### [AR-13] MemoryKeyedStateBackend 命名空间双重路径 — String vs Object 产生不兼容的键

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryKeyedStateBackend.java:88-95`
- **严重程度**: P1
- **现状**: `setCurrentNamespace(String)` 存储 String，而 `setTypedNamespace(N)` 存储任意对象（如 TimeWindow）。`TypedNamespaceAndKey.equals()` 包含类检查，所以 `String "window-1"` 和 `toString()` 返回 `"window-1"` 的对象是不同键。如果混用两种 API 路径，所有状态查找静默返回 null 或错误数据
- **风险**: 状态数据静默丢失或读取到错误窗口的数据
- **建议**: 统一命名空间 API，或在内部将所有命名空间规范化为同一种表示
- **信心水平**: 很可能

### [AR-14] GraphModelCheckpointExecutor registerTasksAndTrackers 每个链覆盖 barrier tracker

- **文件**: `nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java:399-407`
- **严重程度**: P1
- **现状**: 循环 `for (OperatorChain chain : chains)` 为每个链创建新的 `CheckpointBarrierTracker` 并调用 `invokable.setBarrierTracker(tracker)`。当一个 vertex 有多个算子链时，每次迭代覆盖前一个 tracker。只有最后一个链的 tracker 存活
- **风险**: 检查点屏障协调对所有链（除最后一个）丢失，导致检查点数据不完整
- **建议**: 使用复合 tracker 或每个链独立管理
- **信心水平**: 很可能

### [AR-15] NFACompiler NOT_FOLLOW 在 lastSink 非终态时静默丢弃（即使有全局窗口）

- **文件**: `nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/compiler/NFACompiler.java:338-348`
- **严重程度**: P1
- **现状**: 当 `lastSink.isFinal()` 为 false（NOT_FOLLOW 不是最后一个模式元素）时，整个 NOT_FOLLOW 被静默丢弃，无论窗口配置如何。没有 Pending 状态、Stop 状态或边条件被创建
- **风险**: 中间位置的 NOT_FOLLOW 语义完全丢失，匹配结果不正确
- **建议**: 即使 `lastSink` 非终态，也应创建适当的 stop/condition 边
- **信心水平**: 很可能

---

## P2 发现

### [AR-16] WindowAggregationOperator 序列化键 "#" 分隔符碰撞

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/operators/WindowAggregationOperator.java:561-562, 596-598`
- **严重程度**: P2
- **现状**: `serializeWindowKey` 用 `"#"` 连接 JSON 序列化的 key 和 window。如果 key 或 window 的 JSON 表示包含 `#`（如 String key `"a#b"`），`split("#", 2)` 产生错误的分割
- **风险**: 包含 `#` 的键/窗口在恢复时数据丢失
- **建议**: 使用 JSON 数组或结构化分隔符
- **信心水平**: 确定

### [AR-17] MergingWindowSet.addWindow 退化合并时 NoSuchElementException

- **文件**: `nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/MergingWindowSet.java:188, 195`
- **严重程度**: P2
- **现状**: 如果 `mergedWindows` 在 `remove(newWindow)` 后为空（只有新窗口本身参与合并），`mergedWindows.iterator().next()` 抛出 `NoSuchElementException`
- **风险**: 特定窗口分配器行为下的不可恢复崩溃
- **建议**: 在 `iterator().next()` 前检查 `mergedWindows.isEmpty()`
- **信心水平**: 很可能

### [AR-18] CheckpointCoordinator latestCompletedCheckpoint 无序覆盖

- **文件**: `nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:265`
- **严重程度**: P2
- **现状**: `latestCompletedCheckpoint = completed` 是普通 volatile 写入，没有与当前值比较。如果两个检查点并发完成（如 cp-5 和 cp-6），先完成 cp-6 的线程可能被后完成 cp-5 的线程覆盖
- **风险**: 后续 `restoreFromCheckpoint()` 可能恢复到旧的检查点
- **建议**: 使用 `compareAndSet` 或加锁确保单调递增
- **信心水平**: 很可能

### [AR-19] CheckpointCoordinator stopCheckpointScheduler 未同步

- **文件**: `nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:98, 146`
- **严重程度**: P2
- **现状**: `startCheckpointScheduler` 是 `synchronized` 的，但 `stopCheckpointScheduler` 不是。并发调用可能导致 scheduler 在刚创建后立即被关闭
- **风险**: 协调器进入不一致状态
- **建议**: `stopCheckpointScheduler` 也加 `synchronized`
- **信心水平**: 很可能

### [AR-20] CheckpointCoordinator failedCommitParticipants 使用列表索引而非 ID

- **文件**: `nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:429, 448`
- **严重程度**: P2
- **现状**: `finishCommit` 失败时存储参与者的列表索引。但 `participants` 是 `CopyOnWriteArrayList`，如果参与者在失败和重试之间增减，索引可能指向不同参与者或越界
- **风险**: 重试目标错误的参与者
- **建议**: 使用参与者 ID 而非索引
- **信心水平**: 很可能

### [AR-21] CheckpointCoordinator cleanupOldCheckpoints 跨管线删除

- **文件**: `nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:380, 384`
- **严重程度**: P2
- **现状**: `getAllCheckpoints(jobId)` 获取 job 下所有管线的检查点，但清理循环把它们当作当前管线的一部分来计数和删除
- **风险**: 可能删除其他管线的有效检查点
- **建议**: 按管线 ID 过滤
- **信心水平**: 很可能

### [AR-22] SharedBuffer.registerEvent 缓存/状态不一致

- **文件**: `nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/sharedbuffer/SharedBuffer.java:186-192`
- **严重程度**: P2
- **现状**: `eventsCount` 和 `eventsBufferCache` 先写入（总是成功），然后 `eventsBuffer.put()` 可能抛异常。异常后缓存已有条目但持久化状态没有，且 `eventsCount` 已递增。下次同时间戳的事件获取 id+1（跳过失败的 id）
- **风险**: 持久化状态后端 + 检查点场景下，事件在缓存中但不在状态中，恢复后丢失
- **建议**: 先写状态，成功后更新缓存；或使用事务语义
- **信心水平**: 很可能

### [AR-23] SharedBuffer.flushCache 先刷节点后刷事件 — 检查点恢复引用不存在的事件

- **文件**: `nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/sharedbuffer/SharedBuffer.java:301-309`
- **严重程度**: P2
- **现状**: `flushCache()` 先写 nodes 到状态（步骤①），再写 events 到状态（步骤②）。如果步骤①成功但步骤②抛异常，节点引用的事件不在状态中。恢复时 `materializeMatch` 解引用这些事件 ID 时 NPE
- **风险**: 检查点恢复后 CEP 匹配数据损坏
- **建议**: 先刷 events 再刷 nodes
- **信心水平**: 很可能

### [AR-24] NFA 超时 pending 状态可能发出空匹配

- **文件**: `nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/NFA.java:289-297`
- **严重程度**: P2
- **现状**: pending 状态（有界 NOT_FOLLOW）超时后被路由到 `processMatchesAccordingToSkipStrategy` → `extractPatterns`。如果 pending 状态从 start state 经 PROCEED 链到达（未消费任何事件），`extractPatterns` 返回非空列表包含空 map `{}`，被 `materializeMatch` 后作为有效匹配发出
- **风险**: 空匹配事件被当作正常匹配处理，下游业务逻辑收到空数据
- **建议**: 在发出前检查匹配是否包含至少一个事件
- **信心水平**: 很可能

### [AR-25] CepOperator.computationStates.clear() 孤立 SharedBuffer 条目

- **文件**: `nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java:378-380`
- **严重程度**: P2
- **现状**: 当 partialMatches 大小为 1 且无 completedMatches 时，`computationStates.clear()` 清除 NFA 状态但不清理 SharedBuffer。孤立条目永远不被清理
- **风险**: 潜在内存泄漏，与 AR-1 的 releaseNode 栈脱锁 bug 叠加时更严重
- **建议**: 清除 NFA 状态时同步清理 SharedBuffer
- **信心水平**: 很可能

### [AR-26] CepOperator 处理时间路径（无 comparator）事件停止后部分匹配泄漏

- **文件**: `nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java:300-311`
- **严重程度**: P2
- **现状**: 处理时间模式下无 comparator 时，`advanceTime` 在事件注册前调用。如果第一个事件不匹配任何 TAKE 条件，不会注册定时器，未来超时清理完全依赖下一个事件的 `advanceTime` 调用。如果事件停止到达，部分匹配永远不会被清理
- **风险**: 事件流停止后的内存泄漏
- **建议**: 注册兜底定时器或使用全局清理机制
- **信心水平**: 很可能

### [AR-27] BatchConsumerSinkFunction 声称 IDEMPOTENT 但无幂等机制

- **文件**: `nop-stream-connector/src/main/java/io/nop/stream/connector/BatchConsumerSinkFunction.java:96-98`
- **严重程度**: P2
- **现状**: `getSinkConsistency()` 返回 `IDEMPOTENT`，但实现无去重、幂等键或 upsert 机制。flush 失败重试时记录可能被写两次
- **风险**: 框架基于此声明跳过去重逻辑，导致下游重复数据
- **建议**: 返回 `AT_LEAST_ONCE` 或实现真正的幂等机制
- **信心水平**: 确定

### [AR-28] BatchConsumerSinkFunction 非序列化字段在 Serializable 类中

- **文件**: `nop-stream-connector/src/main/java/io/nop/stream/connector/BatchConsumerSinkFunction.java:35-38, 52-55`
- **严重程度**: P2
- **现状**: `SinkFunction extends Serializable`。`consumer`（IBatchConsumer）和 `taskContext`（IBatchTaskContext）是非 transient 的 final 字段，既不实现 `Serializable`。运行时序列化（任务分发）抛出 `NotSerializableException`
- **风险**: 序列化场景下崩溃
- **建议**: 标记为 `transient` 并延迟初始化
- **信心水平**: 确定

### [AR-29] MemoryKeyedStateBackend getState 系列方法按名称查找无类型冲突检测

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryKeyedStateBackend.java:106-175`
- **严重程度**: P2
- **现状**: 所有 `getXxxState()` 方法仅按名称查找已有状态并盲目转型。如果先注册 `ValueStateDescriptor<>("foo", String.class)` 再注册 `ValueStateDescriptor<>("foo", Integer.class)`，第二次调用静默返回 String 类型的状态
- **风险**: 使用时 `ClassCastException`，而非注册时明确报错
- **建议**: 在查找时比较 descriptor 类型，不匹配则抛异常
- **信心水平**: 确定

### [AR-30] MemoryStateSerDe.restoreState 不验证 keyType

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryStateSerDe.java:84-129`
- **严重程度**: P2
- **现状**: 快照包含 `keyType`，但 `restoreState` 从不读取或验证。用 `keyType=String` 的快照恢复到 `keyType=Integer` 的后端时静默加载不兼容数据
- **风险**: 后续 `routeKey()` 产生错误哈希和损坏的查找
- **建议**: 恢复时比较 keyType
- **信心水平**: 很可能

### [AR-31] MemoryMapState/MemoryListState 返回内部可变集合的活视图

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryMapState.java:74-76` + `MemoryListState.java:38-41`
- **严重程度**: P2
- **现状**: `entries()` 返回 `map.entrySet()`（活视图），`get()` 返回内部 `ArrayList`。外部代码可直接修改内部状态
- **风险**: 静默损坏状态数据
- **建议**: 返回不可修改视图或防御性拷贝
- **信心水平**: 确定

### [AR-32] GraphModelCheckpointExecutor stopSources 关闭所有 invokable 的输出而非仅 source

- **文件**: `nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java:334-345`
- **严重程度**: P2
- **现状**: SUSPEND 终止模式下，`stopSources` 遍历所有 invokable 并关闭其输出 writer。不过滤为仅 source 算子。关闭中间算子的输出破坏数据管线
- **风险**: SUSPEND 模式下管线损坏
- **建议**: 仅关闭 source invokable 的输出
- **信心水平**: 很可能

### [AR-33] TaskExecutor.awaitCompletion 吞掉 InterruptedException

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/execution/TaskExecutor.java:297-309, 326-349`
- **严重程度**: P2
- **现状**: 两个 `awaitCompletion()` 重载声明 `throws InterruptedException` 但通过通用 `catch (Exception e)` 捕获了 `InterruptedException`。异常被 DEBUG 级别日志吞掉，线程中断状态未恢复
- **风险**: 调用者永远无法正确响应线程中断，方法不可中断
- **建议**: 单独 catch `InterruptedException`，恢复中断状态并重新抛出
- **信心水平**: 确定

### [AR-34] TaskExecutor SubtaskTask 未纳入 submittedTasks 统计

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/execution/TaskExecutor.java:203-217, 254-280`
- **严重程度**: P2
- **现状**: `submitTask(SubtaskTask)` 只添加到 `taskFutures`，不添加到 `submittedTasks`。实际执行路径 `StreamExecutionEnvironment.execute()` 专用此方法。`getRunningTaskCount()` 等监控方法永远返回 0
- **风险**: 监控指标全部失效
- **建议**: 在 `submitTask(SubtaskTask)` 中也添加到 `submittedTasks`
- **信心水平**: 确定

### [AR-35] StreamExecutionEnvironment.execute() 重新包装内部 StreamException — 丢失错误码

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/environment/StreamExecutionEnvironment.java:207-290`
- **严重程度**: P2
- **现状**: 整个 try 块被 `catch (Exception e)` 包装为新的 `StreamException(ERR_STREAM_JOB_EXECUTE_FAILED)`。内部已携带特定错误码的 `StreamException`（如 "No sinks found" 的 `ERR_STREAM_INVALID_STATE`）被覆盖
- **风险**: 调用方检查错误码时看到错误的错误码
- **建议**: catch 块中判断 `e instanceof StreamException` 时直接抛出
- **信心水平**: 确定

### [AR-36] WindowAggregationOperator mergeWindowContents 非累加器路径始终取最后源 — 数据丢失

- **文件**: `nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java:823-826`
- **严重程度**: P2
- **现状**: 非累加器路径合并窗口时 `targetValue = sourceValue` 无条件替换。循环结束后结果是最后一个源窗口的值，而非正确合并。对于多元素无累加器窗口，所有先前元素静默丢失
- **风险**: 会话窗口合并时静默数据丢失
- **信心水平**: 很可能

### [AR-37] CepPatternBuilder 量化方法（oneOrMore/times 等）对 GroupPattern 无保护

- **文件**: `nop-stream-cep/src/main/java/io/nop/stream/cep/model/builder/CepPatternBuilder.java:154-180`
- **严重程度**: P2
- **现状**: 与 AR-12 重复描述角度不同：此处强调 CepPatternBuilder 从 XDSL 模型读取量化属性（`oneOrMore`、`times` 等）并无条件应用到 Pattern 对象。如果 part 是 GroupPattern，这些量化方法会静默执行（不像 `greedy()` 有保护检查），产生行为不确定的 NFA
- **信心水平**: 很可能

### [AR-38] PatternStreamBuilder.clean() 是空操作 — 非序列化闭包在序列化时崩溃

- **文件**: `nop-stream-cep/src/main/java/io/nop/stream/cep/PatternStreamBuilder.java:92-94`
- **严重程度**: P2
- **现状**: 原始 Flink `ClosureCleaner` 逻辑被完全移除。任何捕获非序列化状态的 lambda 或匿名类（作为 PatternProcessFunction 等传入）在框架尝试序列化算子时运行时失败，无早期警告
- **风险**: 生产环境下序列化时意外崩溃
- **建议**: 至少在构建时添加可序列化检查
- **信心水平**: 很可能

---

## P3 发现

### [AR-39] SharedBufferAccessor.extractPatterns 双重 getEntry 查找

- **文件**: `nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/sharedbuffer/SharedBufferAccessor.java:187-191`
- **严重程度**: P3
- **现状**: 对同一 `target` 调用两次 `getEntry()`。应提取为局部变量
- **信心水平**: 确定

### [AR-40] CepPatternBuilder getAfterMatchSkipStrategy 无 default 分支

- **文件**: `nop-stream-cep/src/main/java/io/nop/stream/cep/model/builder/CepPatternBuilder.java:190-211`
- **严重程度**: P3
- **现状**: switch 语句无 default 分支。新增枚举值时静默降级为 noSkip
- **信心水平**: 确定

### [AR-41] CepPatternGroupModel/SingleModel setType() 是空操作

- **文件**: `nop-stream-cep/src/main/java/io/nop/stream/cep/model/CepPatternGroupModel.java:22-23` + `CepPatternSingleModel.java:22-23`
- **严重程度**: P3
- **现状**: `setType(String type) {}` 为空方法，`getType()` 返回硬编码值。基类 `_type` 字段始终为 null
- **信心水平**: 确定

### [AR-42] Quantifier.Times equals/hashCode 不一致

- **文件**: `nop-stream-cep/src/main/java/io/nop/stream/cep/pattern/Quantifier.java:238-242`
- **严重程度**: P3
- **现状**: `equals()` 用 `toMillis()` 比较窗口时间，`hashCode()` 用 `Duration.hashCode()`（纳秒精度）。理论上的 equals/hashCode 不一致
- **信心水平**: 有趣的猜测

### [AR-43] PatternProcessFunctionBuilder 参数名误导

- **文件**: `nop-stream-cep/src/main/java/io/nop/stream/cep/PatternProcessFunctionBuilder.java:138`
- **严重程度**: P3
- **现状**: `TimeoutSelectBuilder` 构造函数参数 `flatSelectFunction` 实际类型是 `PatternSelectFunction`（非 flat）
- **信心水平**: 确定

### [AR-44] MemoryStateSerDe 快照/恢复字段名键不一致

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryStateSerDe.java:301-303, 348-349`
- **严重程度**: P3
- **现状**: `restoreReducingState` 和 `restoreAggregatingState` 直接读 `"valueType"` 和 `"accumulatorType"`，而其他方法先读 `"valueTypeName"` 再降级。格式迁移时会 break
- **信心水平**: 确定

### [AR-45] DebeziumCdcSourceFunction draining 条件始终为 true

- **文件**: `nop-stream-connector/src/main/java/io/nop/stream/connector/DebeziumCdcSourceFunction.java:57,60`
- **严重程度**: P3
- **现状**: `this.draining = false` 后立即 `if (!draining)` — 条件恒为 true
- **信心水平**: 确定

### [AR-46] BatchLoaderSourceFunction 复用 chunk context

- **文件**: `nop-stream-connector/src/main/java/io/nop/stream/connector/BatchLoaderSourceFunction.java:59`
- **严重程度**: P3
- **现状**: 单个 `IBatchChunkContext` 在所有批次加载间复用，可能累积不正确的状态
- **信心水平**: 很可能

### [AR-47] MockTransactionGenerator BigDecimal(double) 精度丢失

- **文件**: `nop-stream-fraud-example/src/main/java/io/nop/stream/fraud/util/MockTransactionGenerator.java:40,103`
- **严重程度**: P3
- **现状**: `new BigDecimal(Math.random() * 100)` 捕获浮点表示伪影。金融数据应使用 `BigDecimal.valueOf()`
- **信心水平**: 确定

### [AR-48] GraphExecutionPlan 拓扑排序不检测环 — 静默丢弃节点

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/execution/GraphExecutionPlan.java:374-409`
- **严重程度**: P3
- **现状**: Kahn 算法不验证 `sorted.size() == totalVertices`。有环 JobGraph 中环内节点被静默排除，不报错
- **风险**: 含环 JobGraph 静默产出不完整结果
- **信心水平**: 确定

### [AR-49] GraphExecutionPlan 多入边仅用第一条边的 EdgeConfig

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/execution/GraphExecutionPlan.java:244-258`
- **严重程度**: P3
- **现状**: merge 点的 InputGate 使用第一条入边的 EdgeConfig。不同边需要不同流控策略时配置错误
- **信心水平**: 很可能

### [AR-50] JobGraphGenerator 死代码：keySelectors 列表和 opIndex 变量

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/jobgraph/JobGraphGenerator.java:353-356, 376, 381`
- **严重程度**: P3
- **现状**: `keySelectors` 列表构建后从未读取，被 `filteredKeySelectors` 替代。`opIndex` 声明并递增但从未使用
- **信心水平**: 确定

### [AR-51] StreamExecutionEnvironment.buildStreamModel 创建空 StreamComponents

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/environment/StreamExecutionEnvironment.java:364-373`
- **严重程度**: P3
- **现状**: `new StreamComponents()` 创建但从未填充。`StreamRequirementValidator.validate()` 空通过，`computeFingerprint()` 产生不完整哈希
- **信心水平**: 确定

### [AR-52] DataStreamImpl flatMap 不推断输出类型

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/datastream/DataStreamImpl.java:172-178`
- **严重程度**: P3
- **现状**: `map()` 调用 `inferMapReturnType()` 尝试推断输出类型，`flatMap()` 始终使用 `UnknownTypeInformation`。不一致
- **信心水平**: 确定

### [AR-53] MessageSourceFunction InterruptedException 泄漏订阅

- **文件**: `nop-stream-connector/src/main/java/io/nop/stream/connector/MessageSourceFunction.java:115-117`
- **严重程度**: P3
- **现状**: `shutdownLatch.await()` 抛出 `InterruptedException` 时传播异常而不调用 `subscription.cancel()`
- **信心水平**: 很可能

---

## 总评

本模块已经历多轮深度审计（累计 235+ 条历史发现），本次审查仍发现 53 条新问题，其中 8 条 P0、7 条 P1。

**最值得关注的 3 个方向：**

1. **SharedBuffer 引用计数正确性（AR-1）**: 这是整个 CEP 模块的数据完整性基石。`releaseNode` 的并行栈脱锁 bug 不是偶发的——它在任何具有共享子结构的模式中都会触发。与 AR-25（computationStates.clear 孤立条目）形成连锁效应：一旦引用计数因脱锁而错误，孤立条目会无限积累。这不是"特定输入下崩溃"的 bug，而是"特定输入下静默数据损坏"的 bug，危害更大。

2. **检查点/恢复路径的系统性缺陷（AR-3, AR-4, AR-7, AR-6）**: StreamReduceOperator 恢复后状态丢失、WindowAggregationOperator 累加器数值腐败、CEP inputSerializer 为 null 导致检查点 NPE、CheckpointConfig 被丢弃——这四条加在一起意味着**当前 nop-stream 的检查点恢复机制在实际使用中几乎不可用**。这不是个别遗漏，而是检查点路径缺少端到端集成测试的系统性表现。

3. **算子生命周期不完整（AR-5, AR-8, AR-9）**: fan-out 只关闭第一个 writer、深拷贝算子链不调用 open()、sink function 不调用 finish()——这些都指向同一个根因：**算子生命周期管理缺少统一框架**，每个路径（source/middle/sink/self-contained/distributed）各自处理生命周期，且都有遗漏。

## 本次审查的盲区自评

1. **未深入审查分布式执行路径**（EmbeddedDistributedExecutor、TaskManager、RPC 交互）——这涉及 IMessageService 的跨节点通信，需要更多时间追踪消息传递语义
2. **未验证 XDSL 模型到 Pattern 的完整转换链路**——CepPatternModel 的 XML 定义 → CepPatternBuilder → Pattern → NFACompiler → NFA 的端到端正确性
3. **未检查并发场景下的 SharedBuffer 线程安全性**——当前 CEP 是单线程的，但如果未来改为多线程，许多内部状态（eventsCount、entryCache）不是线程安全的
4. **未审查 Watermark 对齐和空闲检测的完整语义**——已知 N46（markIdle/markActive 是空操作）但未追踪其对下游算子的完整影响链

---

## 严重程度分布表

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0      | 8    | 数据完整性(AR-1,AR-4)、状态恢复(AR-3,AR-7,AR-6)、资源泄漏(AR-5)、算子初始化(AR-8)、NFA编译(AR-2) |
| P1      | 7    | 生命周期缺失(AR-9,AR-10)、模型构建(AR-11,AR-12,AR-15)、状态管理(AR-13)、检查点协调(AR-14) |
| P2      | 18   | 序列化/恢复缺陷(AR-16,AR-22,AR-23,AR-24,AR-30)、并发安全(AR-18,AR-19,AR-20,AR-21)、监控失效(AR-34,AR-35)、内存泄漏(AR-25,AR-26)、API语义(AR-27,AR-29,AR-31,AR-36,AR-37,AR-38)、生命周期(AR-32,AR-33) |
| P3      | 20   | 代码质量(AR-39,AR-43,AR-44,AR-50,AR-52)、健壮性(AR-40,AR-41,AR-42,AR-45,AR-46,AR-48,AR-49,AR-51)、示例质量(AR-47)、资源管理(AR-53) |
