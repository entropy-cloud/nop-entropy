> Audit Status: closed
> Audit Type: open-ended
> Mission: nop-stream-invariant-loop
> Processed: 2026-08-13 — P0-01/P1-02 → plan `2026-08-13-0132-1`（已收口）；P0-02/P0-03/P1-01/P1-05 → plan `2026-08-13-0132-2`（已收口，closure audit APPROVE）；P1-03/P1-04 → plan `2026-08-13-0132-3`；P2-01~P2-10 → roadmap Follow-up Backlog（`ai-dev/backlog/nop-stream-invariant-loop-roadmap.md` 2026-08-13 P2 批次，含 source 追溯）

# nop-stream 开放式对抗审查报告（open-ended adversarial audit）

## 基本信息

- **审核模块**: `nop-stream/` 全模块组（重点：core / runtime / cep）
- **审核日期**: 2026-08-13（live 基线，working tree clean）
- **方法**: 4 个并行子 agent 深度探查（WindowOperator 族 / Checkpoint 机制 / CEP 族 / 执行层）→ 主 agent 逐条对 live 源码独立复核（本文所有发现均已用实际代码文本验证，未经验证的候选已丢弃）
- **盲区继承**: 本轮聚焦前次 multi-audit（`2026-08-12-1217-multi-audit-nop-stream-invariant-loop.md`，P0=1/P1=1/P2=21）未覆盖的区域（并发族、恢复路径、运行时服务接线、生成产物）；该报告全部发现经复核**仍为 live 未修复**（见 P2-09）。

## 去重声明

- 已确认仍 live 的前次发现：**P0-01**（WindowOperator merge fail-fast 零回归测试 + `MixedTypeWindowOperator` 死类，`TestWindowOperatorCorrectness.java:453-487` 仍零实例化、错误码零测试引用）、**P1-01**（beans.xml `ioc:configMethod`/`ioc:bean` 不存在语法，`stream-control-rpc.beans.xml:40-57`）——按去重规则简要引用，不重写（P2-09）。
- 已知修复项（R16-AR-1/5/6/7/8/9/11/18、RL-1..7、C2-RL-1/2、interim fail-fast 等）经复核 **live 均保持修复态**，未重报。
- 不变式门禁复核：`node ai-dev/tools/check-nop-stream-invariants.mjs all` exit 0（inventory/sync/scan-iterations/scan-output-contract/self-test 全绿，pin 0）；JUnit 门禁 10 类 102 tests 基线全绿。

---

## 发现清单（按优先级）

---

### [P0-01] CepOperator.open() 在无容器生产环境必然 NPE —— ProcessingTimeService 全仓零接线（新失败族：运行时服务注入完整性）

**P0 判定理由**: 生产环境 CEP 作业在算子 open 阶段即崩溃（默认配置），属 blocking 级错误行为；且暴露了一个门禁体系未覆盖的新失败族（服务接线完整性）。

- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java:356-368`；`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/AbstractStreamOperator.java:34,135-137`
- **证据片段**:
  ```java
  // CepOperator.open():347 -> registerCacheStatisticsTimer():356-368
  java.time.Duration interval = NopCepConfigs.CEP_CACHE_STATISTICS_INTERVAL.get();  // 默认 30min（>0）
  long firstFire = getProcessingTimeService().getCurrentProcessingTime() + cacheStatsIntervalMs;  // NPE
  this.cacheStatsTimerFuture = getProcessingTimeService().registerTimer(firstFire, this::onCacheStatisticsTimer);
  ```
  ```java
  // AbstractStreamOperator.java:135
  public ProcessingTimeService getProcessingTimeService() {
      return processingTimeService;   // 字段 :34 protected transient，全仓 grep 仅测试直接赋值
  }
  ```
  ```
  grep 证据：setProcessingTimeService 生产+测试全仓零调用；processingTimeService 赋值仅存在于
  测试（TestPeriodicWatermarkAdvancement.java:72,136,186 / CepTestUtils.injectProcessingTimeService 反射注入）
  ```
- **严重程度**: P0
- **现状**: `processingTimeService` 在 main 代码中**从未被任何生产路径赋值**。`CepOperator.open()` → `registerCacheStatisticsTimer()`（默认 interval 30min 非正判断不成立）→ `getProcessingTimeService()` 返回 null → NPE。全 320 个 cep 测试全绿的原因是每个测试都经 `CepTestUtils.injectProcessingTimeService` 手动注入 mock（`TestCepOperatorCacheStatistics.java:250-257` 显式断言"open() 应经 ProcessingTimeService 注册 timer"）——生产路径零 E2E 覆盖。连带影响：`inProcessingTime()` 模式的 `processElement`（`:466/:471`）同样 NPE；`onCacheStatisticsTimer` 定期统计（STRM-037 锚点声称的特性）死路。
- **风险**: 任何生产 CEP 作业（PatternStreamBuilder → transform → open）在 open 阶段崩溃，作业无法启动；且由于所有测试都注入 mock，门禁与测试体系对该缺陷完全免疫。
- **建议**: 运行时接线 `setProcessingTimeService`（在 `StreamTaskInvokable.setupSnapshotCallbacks` 同级提供真实实现，驱动 `HeapInternalTimerService.fireProcessingTimeTimers` 的调度线程）；在此之前至少将 `registerCacheStatisticsTimer` 改为 null 守卫 + 明确 WARN（与 `TimestampsAndWatermarksOperator.java:82` 同款模式）。
- **信心水平**: 确定（机制与测试规避路径均直接验证）
- **发现来源视角**: 异常路径侦探 + IoC 侦探

---

### [P0-02] SupervisionLoop 区域级重启后重建任务不接 checkpoint 管线 —— 重启后第一个 checkpoint 起永久超时，作业必死

**P0 判定理由**: 文档化生产特性组合（checkpoint + 区域监督重启，`maxRestartsPerRegion` 默认 3）在重启后必然触发 checkpoint 超时→abort→作业整体失败，属确定性错误行为。

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/SupervisionLoop.java:679-701`；`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/StreamTaskInvokable.java:255-263,360-370`；`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java:742-756,834-894`
- **证据片段**:
  ```java
  // SupervisionLoop.java:681/690/695 — 重建任务，全程无 setBarrierTracker/registerTask/addParticipant
  newInvokable = new StreamTaskInvokable(newChain, (RecordWriter<?>) null, newInputGate);
  newInvokable = new StreamTaskInvokable(newChain, oldOutputWriter, null);
  ```
  ```java
  // StreamTaskInvokable.java:255-259 — setBarrierTracker 是唯一 setupSnapshotCallbacks() 入口
  public void setBarrierTracker(CheckpointBarrierTracker tracker) {
      this.barrierTracker = tracker;
      if (tracker != null) {
          setupSnapshotCallbacks();
      }
  ```
  ```java
  // GraphModelCheckpointExecutor.java:744-756 — 周期 checkpoint 只注入启动时的 allInvokables
  for (StreamTaskInvokable inv : allInvokables) { inv.getBarrierTracker().triggerCheckpoint(...) }
  ```
- **严重程度**: P0
- **现状**: `SupervisionLoop.rebuildTask` 构建的新 `StreamTaskInvokable`（a）不调用 `setBarrierTracker` → 算子 `snapshotCallback` 为 null（`AbstractStreamOperator.java:400` 静默跳过 ACK）；（b）不在 `allInvokables` 列表中 → 永远收不到 barrier；（c）coordinator 的 `tasksToAck` 仍含该 TaskLocation（重启复用同一 taskLocation，`SupervisionLoop.java:699`）→ 每个新 checkpoint 永远无法 full-ACK → `checkpointTimeout` 后 abort（`CheckpointCoordinator.java:997` 定时器）→ `registerLocalAbortHandler`（:834-884）遍历启动时的 tasks，无 in-flight epoch → `abortMarked=true` → `checkAbortMarker`（:888-894）抛 `ERR_STREAM_CHECKPOINT_ABORTED` → 作业失败。唯一的区域重启 E2E（`TestSupervisionLoopReconnectE2E.java:161-163`）以 `null` coordinator/checkpointPlan 运行——checkpoint 组合零测试覆盖。
- **风险**: 启用 checkpoint 的本地/嵌入式作业（`submitAndRun` 为默认执行路径）一旦发生区域级重启（含物化点故障恢复场景），重启后无法再完成任何 checkpoint，最终作业必然失败；且重建算子亦不注册 CheckpointListener/Participant，`notifyCheckpointCompleted/Aborted` 永不达。
- **建议**: `rebuildTask` 后按原任务 wiring 补全：`setBarrierTracker`（含 setupSnapshotCallbacks）+ coordinator `registerTask`/participant/listener 重注册 + 将重建 invokable 并入 barrier 注入列表；并补"区域重启 + checkpoint 全链路"E2E（先红后绿）。
- **信心水平**: 确定（代码路径全程可追溯；`snapshotCallback==null` 静默跳过为显式逻辑）
- **发现来源视角**: 组合爆炸测试者（两个独立正确的机制叠加）

---

### [P0-03] EpochManifest 恢复路径不推进 checkpoint ID 计数器 —— 新 checkpoint ID ≤ 恢复 epoch，被旧 epoch 遮蔽，崩溃窗口内回滚丢数据

**P0 判定理由**: 本地执行主路径（`StreamExecutionEnvironment.execute()`）在 manifest 恢复后所有新 checkpoint 落于影子窗口，崩溃恢复回退到过期 epoch（数据丢失），属 blocking。

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java:940-971`；`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:885-905,1269-1271,401`；`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/storage/LocalFileCheckpointStorage.java:525-533,489-512`
- **证据片段**:
  ```java
  // GraphModelCheckpointExecutor.java:946-970 — manifest 恢复成功即 return，永不触碰计数器
  EpochManifest epochManifest = coordinator.restoreLatestEpochManifest();
  if (epochManifest != null) { ... restoreTaskStatesFromSource(...); return; }
  CompletedCheckpoint latestCheckpoint = coordinator.restoreFromCheckpoint();  // 唯一推进点
  ```
  ```java
  // CheckpointCoordinator.java:896-900 — 计数器推进只在 restoreFromCheckpoint（CompletedCheckpoint 路径）
  long restoredId = checkpoint.getCheckpointId();
  long currentCounter = checkpointIdCounter.get();
  if (restoredId >= currentCounter) { checkpointIdCounter.set(restoredId + 1); }
  ```
  ```java
  // CheckpointCoordinator.java:1269-1271 — restoreLatestEpochManifest 仅加载
  public EpochManifest restoreLatestEpochManifest() throws Exception {
      return checkpointStorage.loadLatestEpochManifest(jobId, pipelineId);
  }
  ```
- **严重程度**: P0
- **现状**: 每个 checkpoint 完成都会写 manifest（`storeEpochManifest` 在 sync/async/incremental 三路径均调用，`:529/:560/:612`）；恢复时 manifest 优先（executor :946-970）。但计数器（`new CheckpointIDCounter()` 启动即 0，:115）只在 `restoreFromCheckpoint` 推进。manifest 恢复后新 checkpoint ID 从 0 开始，全部 < 恢复 epoch R：`loadLatestEpochManifest`/`getLatestCheckpoint` 按 max-ID 选择（LocalFile :525-533；JDBC `ORDER BY ... DESC LIMIT 1`）→ 持续返回**过期 epoch R**；ID==R 时 ATOMIC_MOVE REPLACE（:500）覆写 manifest 文件。崩溃于影子窗口（0..R-1）→ 恢复回退到 R，期间所有已"完成"checkpoint 不可恢复；频繁重启的作业每次重启都重新进入影子窗口。
- **风险**: 数据丢失（回退到过期状态）+ 虚假 durable 点；`restoreSharedStateRegistry` 的 segment 引用计数在 id==R 覆写时悬挂（`checkpointSegments.put` 替换后旧 segments 永不注销）。分布式路径（JobCoordinator:1226 走 `restoreFromCheckpoint`）不受影响——本地/嵌入式为主流执行路径，受影响面大。
- **建议**: manifest 恢复成功后同样执行单调推进（`checkpointIdCounter.set(max(restoredId+1, current))`），与 :896-900 同逻辑；补"manifest 恢复 → 再触发 checkpoint → 新 ID > R"测试。
- **信心水平**: 确定（流程 + max-ID 选择 + 覆写语义均已逐行验证）
- **发现来源视角**: 异常路径侦探 / 10x 规模运维者

---

### [P1-01] WindowOperator aggregate/reduce 窗口 checkpoint 恢复必然失败：aggregate 函数经无参反射重建，匿名类/λ 无无参构造器（Memory + RocksDB 双后端）

**P1 判定理由**: 最常用窗口 API（reduce/aggregate）的作业一旦带窗口状态 checkpoint 后重启，恢复即崩溃（作业无法启动）；非阻塞但为高概率回归核心路径缺陷。

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryStateSerDe.java:426-454,638`；`nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/core/common/state/backend/rocksdb/RocksDBSnapshotSerDe.java:695-700`；`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperatorBuilder.java:147-149,205-246`
- **证据片段**:
  ```java
  // MemoryStateSerDe.java:430-435
  String aggregateFunctionTypeName = (String) stateInfo.get("aggregateFunctionType");
  Class<? extends AggregateFunction<?, ?, ?>> aggregateFunctionClass = (Class<? ...>) Class.forName(...);
  AggregateFunction<Object, Object, Object> aggregateFunction =
          aggregateFunctionClass.getDeclaredConstructor().newInstance();   // 无参反射
  ```
  ```java
  // WindowOperatorBuilder.java:205-206 — reduce() 把用户函数包成捕获型匿名类
  private static <T> AggregateFunction<T, T, T> reduceFunctionAsAggregate(ReduceFunction<T> reduceFunction) {
      return new AggregateFunction<T, T, T>() { ... };   // 构造器 = (ReduceFunction)，无无参构造器
  ```
  ```java
  // MemoryStateSerDe.java:638 — 快照只存类名
  info.put("aggregateFunctionType", state.descriptor.getAggregateFunction().getClass().getName());
  ```
- **严重程度**: P1
- **现状**: 快照存 aggregate 函数的类名；恢复时 `getDeclaredConstructor().newInstance()` 无参重建。`reduce()` 路径（`WindowOperatorBuilder$1` 匿名类，构造器带捕获参数）与 lambda/内部类 aggregate() 均抛 `NoSuchMethodException` → `WindowOperator.open()` :423 `applyPendingRestoreState()` 恢复失败 → 作业无法从 checkpoint 重启。即使函数类有无参构造器，重建出的也是裸实例（用户字段状态静默丢失）。两后端（MemoryStateSerDe:430-435 / RocksDBSnapshotSerDe:695-700）同病。全仓无任何 descriptor 路径的 snapshot→restore 测试（`TestTriggerAccumulatorsCheckpoint.java:107-127` 走 9 参 null-descriptor 构造器；`TestCheckpointRecovery` 仅 ValueState）。
- **风险**: 生产中"窗口作业 + checkpoint + 重启"（升级/故障恢复）必然失败，且无自动降级（恢复异常直接使作业启动失败）；descriptor 路径是 `WindowedStreamImpl` 全部 API 的默认路径（`WindowOperatorBuilder.buildWindowOperator` 恒传 stateDesc）。
- **建议**: 恢复时优先用 live 算子持有的 descriptor 的 aggregate 函数（恢复前由 executor 传入），或把函数实例作为状态本体序列化（函数须 Serializable），或将匿名包装类改为可序列化数据类；三者任选并补 descriptor 路径 restore 测试（先红后绿）。
- **信心水平**: 确定（匿名类构造器签名 + 无守卫 newInstance + 恢复链路三处直接验证）
- **发现来源视角**: 代码生成受害者 / 异常路径侦探

---

### [P1-02] Processing-time 窗口在生产环境永不触发：fireProcessingTimeTimers 无任何生产驱动，且 PT cleanup timer 永不触发导致状态无限增长

**P1 判定理由**: 公开 API（Tumbling/SlidingProcessingTimeWindows + ProcessingTimeTrigger）的行为缺陷：窗口永不发射 + 清理定时器不触发（无界状态），属真实契约漂移。

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/HeapInternalTimerService.java:121-137`；`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java:1073-1084`；`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/windowing/triggers/ProcessingTimeTrigger.java:34-38`
- **证据片段**:
  ```
  grep 证据：fireProcessingTimeTimers 生产+测试全仓调用点仅测试（TestHeapInternalTimerService*.java 等）
  TimerServiceManager.fireProcessingTimeTimers(:42-59) 生产零调用者；timeServiceManager 生产零 setter
  ```
  ```java
  // WindowOperator.java:1074-1084 — 处理时间窗口注册 cleanup timer，永不触发
  if (cleanupTime == Long.MAX_VALUE) { ... } else if (windowAssigner.isEventTime()) {
      triggerContext.registerEventTimeTimer(cleanupTime);
  } else {
      triggerContext.registerProcessingTimeTimer(cleanupTime);   // 无驱动 → 永不触发
  }
  ```
  ```java
  // ProcessingTimeTrigger.java:34-38 — 唯一发射路径即 PT timer 回调
  if (window.maxTimestamp() <= ctx.getCurrentProcessingTime()) { ... ctx.registerProcessingTimeTimer(...) }
  ```
- **严重程度**: P1
- **现状**: `ProcessingTimeTrigger.onElement` 只注册 PT timer；PT timer 唯一的触发入口 `fireProcessingTimeTimers`（`HeapInternalTimerService:121`）无任何生产调用者（运行时仅有 heartbeat/checkpoint/failureDetector 三个调度线程，均与 PT timer 无关）。结果：处理时间窗口（`TumblingProcessingTimeWindows` 默认 trigger）**永不发射**；PT cleanup timer 永不触发 → 窗口状态（windowContents + MergingWindowSet）**无界增长**；`WindowOperator.onProcessingTime`（:791）与 merging guard（:630-638，用 `System.currentTimeMillis()` 墙钟对比 timer 时间基）同样死路。仅测试经手动 `fireProcessingTimeTimers` 驱动（`TestProcessingTimeWindowIntegration.java:107-123` 自建 mock 推进器）。
- **风险**: 使用处理时间窗口的作业静默零输出 + 内存泄漏；用户无从得知功能未接线。
- **建议**: 运行时注入真实 ProcessingTimeService（见 P0-01 同族修复）或删除/明确标注 PT 窗口 API 不可用；至少让 `TumblingProcessingTimeWindows` 系列在无驱动环境下 fail-fast 而非静默。
- **信心水平**: 确定（grep 全仓驱动缺失 + 注册/触发点直接验证）
- **发现来源视角**: 死代码清道夫（功能级死路）

---

### [P1-03] Fan-out（多输出）任务只 close writer[0] —— 边 2..N 的 EOS 永不送达，有界作业下游任务永久阻塞

**P1 判定理由**: 多输出拓扑 + 有界源必然悬挂（下游 read() 永久阻塞，SupervisionLoop 永不收敛），真实行为缺陷。

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/StreamTaskInvokable.java:116-123,451-452,480-482`；`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/InputGate.java:396-422`（readSingleChannel 悬挂）
- **证据片段**:
  ```java
  // StreamTaskInvokable.java:121 — fan-out 只保留 writer[0]
  this.outputWriter = !fanOutWriters.isEmpty() ? fanOutWriters.get(0) : null;
  ```
  ```java
  // StreamTaskInvokable.java:480-482 — invokeMiddle finally 只 close writer[0]
  if (outputWriter != null) { outputWriter.close(); }   // RecordWriter.close → ResultPartition.close → EOS
  ```
  ```java
  // InputGate.java:407-413 — 未 finish 的 channel 返回 null → 循环重试（永久悬挂）
  if (element == null) {
      if (channel.isFinished()) { return Optional.empty(); }
      continue;
  }
  ```
- **严重程度**: P1
- **现状**: `GraphExecutionPlan.build`（:407-427）为每个出边建独立 `RecordWriter`/`ResultPartition`；但任务关闭路径只关 `fanOutWriters.get(0)`。边 2..N 的 partition 永不 close → 下游 task 在 `readSingleChannel`/`readMultiChannel` 空转循环中永久阻塞（`ResultPartition.close()` :316-329 是唯一 EOS 来源）。叠加损害：`SupervisionLoop.java:690` 生产者区域重启复用 `oldOutputWriter`（writer[0]），重启后的 fan-out 生产者静默只喂边 1。`BroadcastingRecordWriterOutput.close()`（:687-703）遍历各 output 但 `RecordWriterOutput.close()` 是 no-op（:634-636）——数据面 fan-out 正常、关闭面残缺。
- **风险**: 一分流多 sink 拓扑（如流拆分到两表）配合有界源（测试/批场景）作业无法正常终止；取消路径外无任何兜底。
- **建议**: `invokeSource/invokeMiddle` finally 改为遍历关闭全部 `fanOutWriters`（或经 `BroadcastingRecordWriterOutput` 统一关闭）；补 fan-out 有界 E2E。
- **信心水平**: 确定（构造/关闭/EOS 链路逐行验证）
- **发现来源视角**: 边界条件组合者

---

### [P1-04] CEP 事件时间 timer 注册表：永不触发（超时仅靠 watermark 驱动）、无界增长、且 restoreState→open() 无条件重建导致恢复的 timer 全量丢失

**P1 判定理由**: 长运行事件时间 CEP 作业的内存/checkpoint 膨胀 + 恢复语义静默错误（open 覆盖 restore 结果），属真实缺陷（timer 持久化修复 AR-9 只落地了存储侧，未接线消费侧）。

- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java:278,307-314,422-424,437-445,455`
- **证据片段**:
  ```java
  // CepOperator.java:307-314 — 事件时间 timer 只进 TreeSet，无任何触发机制消费
  public void registerEventTimeTimer(VoidNamespace namespace, long time) {
      registeredEventTimeTimers.add(time);
  }
  public void deleteEventTimeTimer(...) { registeredEventTimeTimers.remove(time); }  // 全仓零调用者
  ```
  ```java
  // CepOperator.java:455 — onEventTime 唯一入口是 watermark（整批推进，不经 timer）
  if (!isProcessingTime) { onEventTime(currentWatermark); }
  ```
  ```java
  // CepOperator.java:278 + 437-445 — restore 的 timers 被 open() 无条件覆盖
  restoreState: registeredEventTimeTimers.addAll(timers);   // 先
  open():       registeredEventTimeTimers = new TreeSet<>(); // 后 → 恢复结果全丢
  ```
- **严重程度**: P1
- **现状**: `registeredEventTimeTimers` 是纯记账结构：注册（`bufferEvent` :502 每时间桶一个 + `processEvent` 每个新 start 一个 :668/:676）后永不触发、永不删除（`deleteEventTimeTimer` 零调用）；`snapshotState` :422-424 把整个集合拷入每个 checkpoint（载荷线性增长）；`onEventTime` :530 移除队列桶但不删对应 timer。恢复路径 restoreState 已重建集合（AR-9 `7fd60f0e9` 落地的持久化），但 open() :278 无条件 `new TreeSet<>()` 覆盖 → 恢复后的 timer 集为空，且下次快照不再携带旧 timer。窗口超时（`within()`）实际只在 watermark 推进时经 STEP 3 `advanceTime` 评估；watermark 停滞则超时永不触发且状态增长。
- **风险**: 长运行 CEP 作业堆 + checkpoint 载荷无界增长；未来把该注册表接上真实触发机制时（AR-9 的意图），恢复缺陷将直接导致超时不再调度。
- **建议**: open() 仅在 `registeredEventTimeTimers == null` 时初始化（与 restoreState :441-443 对称），并明确该注册表是否应有独立触发（对齐 `WindowOperator` 的 `HeapInternalTimerService` 用法）；补 restore→open 顺序测试（`TestCepCheckpointRestoreE2E:94-109` 已钉死 restore-before-open 顺序）。
- **信心水平**: 确定
- **发现来源视角**: 契约考古学家 / 异常路径侦探

---

### [P1-05] InputGate.markFinishedChannel 用 ConcurrentHashMap 桶序取"首个完成"的 alignment（注释声称按 ID 序），并发下泄漏兄弟 alignment 并永久屏蔽 barrier 超时/unaligned 降级

**P1 判定理由**: `maxConcurrentCheckpoints>1`（opt-in 配置）下两 alignment 同轮完成的竞态：barrier 静默丢弃 + 泄漏项永久绕过 timeout 门，属真实竞态缺陷。

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/InputGate.java:690-710,722-730,491-504`
- **证据片段**:
  ```java
  // InputGate.java:692-708 — values() 迭代为桶序，注释"emit the lowest completed one"不成立
  for (BarrierAlignment align : new ArrayList<>(inFlightAlignments.values())) {
      if (!align.receivedChannels.contains(channelIndex)) {
          align.receivedChannels.add(channelIndex);
          boolean fullyReceived = align.receivedChannels.size() >= channels.size();
          if (barrierAlignment && fullyReceived && completed == null) {
              inFlightAlignments.remove(align.checkpointId);   // 只移除第一个访问到的
              ...
              completed = align.firstBarrier;
          } else if (!barrierAlignment && fullyReceived) {
              inFlightAlignments.remove(align.checkpointId);
          }
      }
  }
  ```
- **严重程度**: P1
- **现状**: `inFlightAlignments` 是 `ConcurrentHashMap`（:99，无插入序）。当 alignment N 与 N+1 同时各差一个 channel 且某 channel 恰好 finish 时，同一次调用内两者都变 fullyReceived：后访问者 `completed` 已非 null → aligned 分支不 remove → 泄漏一条 `receivedChannels.size() >= channels.size()` 的 alignment。泄漏项成为 `oldestAligning()`（:722-730 取 min id）→ timeout 检查（:491-504）要求 `oldest.receivedChannels.size() < channels.size()` → 恒 false → `barrierAlignmentTimeout` 与 `switchToUnalignedAndEmit` 对后续所有 checkpoint **永久失效**；同时 barrier N 被静默丢弃 → coordinator pending N 永不 full-ACK → 超时 abort → 本地执行路径 `abortMarked` → 作业整体失败（P0-02 同款 abort 链）。
- **风险**: 多源 + `maxConcurrentCheckpoints>1` + 有界源（channel finish）场景下作业失败或 checkpoint 语义错乱；默认 maxConcurrent=1 时不可达（故 P1 不升 P0）。
- **建议**: 收集完整后统一按 min checkpointId 选取发射对象并 remove 全部完成项；补"两 alignment 同轮完成"参数化测试。
- **信心水平**: 确定（代码行为可逐行推演；触发概率依赖时序）
- **发现来源视角**: 并发时序侦探

---

### [P2-01] checkpointSuccessMap 在 abort/fail 路径无界增长（已知"修复"的残余兄弟实例）

**P2 判定理由**: 每 abort/fail 泄漏一个 Long→Boolean 条目，慢速无界增长，无数据影响，非阻塞。

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:1091-1092,805,1128-1131,821-831,849-869`
- **证据片段**:
  ```java
  private void notifyParticipantsFinishCommit(long checkpointId, boolean success) {
      checkpointSuccessMap.put(checkpointId, success);   // abort(849-869)/persist失败(821-831) 也写入
      ...
  }
  // 移除仅两处：成功完成 :805；retryFailedCommits 有失败参与者且重试成功 :1131
  ```
- **严重程度**: P2
- **现状**: R16-AR-10 的"已修复"覆盖的是成功 checkpoint 的条目清理；abort/persist-fail 且所有参与者 `finishCommit(false)` 成功（`failedCommitParticipants` 无记录）的 epoch 永久驻留。abort 在该设计中高频（barrier 超时、snapshot 失败均触发）。
- **风险**: 长运行作业（尤其多 abort 场景）协调器堆缓慢无界增长。
- **建议**: abort/fail 通知后移除该 id 条目（或改为带上限/定期裁剪）。
- **信心水平**: 确定
- **发现来源视角**: 死代码清道夫（资源泄漏向）

---

### [P2-02] onCompletePersistFailure 不完成 PendingCheckpoint 的 future —— 持久化失败被伪装成 checkpoint 超时，终态 savepoint 失败被静默吞掉

**P2 判定理由**: 失败信号延迟/失真（等待方 block 满 checkpointTimeout 后抛 TimeoutException 而非 StorageError），非数据损坏。

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:821-831`；`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/PendingCheckpoint.java:175-186`；`GraphModelCheckpointExecutor.java:758-771`
- **证据片段**:
  ```java
  // onCompletePersistFailure（段3b）— 只设状态，不完成 future
  pending.getStatus().set(PendingCheckpoint.Status.FAILED);
  pendingCheckpoints.remove(checkpointId, pending);
  notifyParticipantsFinishCommit(checkpointId, false);
  ```
  ```java
  // PendingCheckpoint.java:175-186 — fail() 本可 completeExceptionally，但此处未调用
  public synchronized void fail(String reason, Throwable cause) { ... completableFuture.completeExceptionally(error); }
  ```
- **严重程度**: P2
- **现状**: async persist（savepoint/终态 checkpoint，`executeWithSavepoint` :348/:479 与 `JobCoordinator` :1439-1485 均带 `.get(checkpointTimeout)`）失败时 future 永不完结 → 等待方阻塞满超时后抛 TimeoutException，真实存储错误被掩盖；`triggerFinalCheckpoint`（:758-771）catch 后仅 LOG 且不重抛（对照 `triggerTerminalSavepoint` :464-490 正确重抛），CANCEL 模式终态 checkpoint 失败静默变成"成功"。
- **建议**: 段3b 调用 `pending.fail(...)`（或构造新的 FAILED future 完成）；`triggerFinalCheckpoint` 失败后按终态契约重抛。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

### [P2-03] InMemoryClusterRegistry.getNodeLease 无锁双 map 读，与 evictExpiredNodes 并发时可 NPE（getActiveNodes 有防御性 null 检查，此处没有）

**P2 判定理由**: 竞态可致未检查 NPE 崩溃；当前生产无调度式 evict 调用者，触发概率低。

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/cluster/InMemoryClusterRegistry.java:98-108,110-123`
- **证据片段**:
  ```java
  public LeaseInfo getNodeLease(String nodeId) {
      Long startAt = leaseStartTimes.get(nodeId);
      if (startAt == null) { return null; }
      long expireAt = leaseExpireTimes.get(nodeId);   // 无锁、无 null 检查；evictor 并发 remove 后 NPE
      ...
  }
  ```
  ```java
  // 对照 :130-134 — getActiveNodes 对同样双 map 读取做了防御
  Long expireAt = leaseExpireTimes.get(entry.getKey());
  if (expireAt != null && expireAt > now) { ... }
  ```
- **严重程度**: P2
- **现状**: `evictExpiredNodes`（:110-123）在 `synchronized(nodes)` 内同时 remove 三张 map；`getNodeLease` 不加锁跨两次 get。交错：startAt 非空 → evictor 移除 → expireAt null → 自动拆箱 NPE。JDBC 实现单行 SQL 读天然无此问题——两实现语义又一次分叉（不变式 #5 族内新实例）。
- **建议**: 与 `getActiveNodes` 一致补 null 防御（或统一在 `synchronized(nodes)` 内读）。
- **信心水平**: 确定（锁纪律可证；崩溃需精确时序）
- **发现来源视角**: 10x 规模运维者

---

### [P2-04] WindowOperator.triggerAccumulators 永不裁剪 + 纯 FIRE 路径不调 triggerContext.clear() —— 长运行窗口作业 map 无界 + 复发窗口复用陈旧累加器

**P2 判定理由**: 内存/checkpoint 载荷随 key×window 累计增长；陈旧累加器复用影响非自清 trigger（CountTrigger 自清故当下安全），非阻塞。

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java:1937-1965,707-717,515-523`
- **证据片段**:
  ```java
  // :1944-1957 — stateKey = "trigger_" + key + SEP + window + SEP + descriptor；只增不删
  String stateKey = "trigger_" + key + STATE_KEY_SEPARATOR + window + STATE_KEY_SEPARATOR + descriptor.getName();
  SimpleAccumulator<T> existing = (SimpleAccumulator<T>) accums.get(stateKey);
  if (existing != null) { return existing; }
  accums.put(stateKey, acc);
  ```
  ```java
  // :707-717 — 纯 FIRE 不调 triggerContext.clear()（仅 purge 路径 :716 调）
  if (triggerResult.isFire()) { ... emitWindowContents(key, window, contents); }
  if (triggerResult.isPurge()) { clearWindowContents(key, window); triggerContext.clear(); }
  ```
- **严重程度**: P2
- **现状**: `triggerAccumulators` 条目仅创建（:1944-1957）、快照（:515-523）、恢复（:558）、close 置空（:508），窗口清理/发射路径零移除。tumbling 窗口同 (key, window-range) 跨天复发时复用陈旧条目；非自清 trigger（如 ContinuousEventTimeTrigger 的 onMerge 读新窗口空累加器）在会话合并后丢失发射时间。每次 checkpoint 全量序列化该 map（载荷线性增长）。
- **建议**: 在 cleanup timer 触发路径（`clearWindowContents`/`deleteCleanupTimer`）同步删除对应 `trigger_*` 条目；合并路径迁移旧窗口条目到新窗口。
- **信心水平**: 确定（增删路径全文件 grep 无 remove）
- **发现来源视角**: 10x 规模运维者

---

### [P2-05] 带 evictor 的 descriptor 路径不建 elementTimestampsState —— TimeEvictor 静默永不驱逐（潜在）

**P2 判定理由**: 当前 TimeEvictor 标 @Internal 未被生产使用（潜伏缺陷），但公共 API 面存在且静默行为错误。

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java:442-453,1230-1233,876-885`
- **证据片段**:
  ```java
  // :442-453 — elementTimestampsState 只在 null-descriptor fallback 分支创建
  } else {  // windowStateDescriptor == null
      ...
      elementTimestampsState = this.keyedStateBackend.getMapState(timestampsDescriptor);
  }
  ```
  ```java
  // :1230-1233 — descriptor 路径（全部 WindowedStreamImpl API）storeElementTimestamp 早退
  if (elementTimestampsState == null || evictor == null) { return; }
  ```
  ```java
  // :876-885 — emitWindowContents 无时间戳可读时一律用当前 watermark 兜底
  } else { elementTimestamp = internalTimerService.currentWatermark(); }
  ```
- **严重程度**: P2
- **现状**: 所有 descriptor 路径（builder 恒传 stateDesc）`elementTimestampsState` 为 null → 元素时间戳从不记录 → `emitWindowContents` 全部元素拿到相同 watermark 时间戳 → `TimeEvictor.evict`（evictCutoff = maxTs - windowSize，全部元素等值）**永不驱逐**。合并路径（:1350-1494）也不合并时间戳列表，索引错位。
- **建议**: descriptor 路径同样创建时间戳状态（或把时间戳并入窗口内容状态）；合并路径同步迁移时间戳。
- **信心水平**: 确定（代码行为）；影响范围 = TimeEvictor 实际使用场景（猜测）
- **发现来源视角**: 契约考古学家

---

### [P2-06] 合并路径 pane 跟踪键与实际窗口/状态窗口错位：pane 泄漏 + DISCARDING 模式清错命名空间

**P2 判定理由**: merging 窗口 + pane 跟踪组合下的状态泄漏与错误清除，非默认路径。

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java:917-943,681-683,667,912-914`
- **证据片段**:
  ```java
  // :917-943（摘要）— pane 键按“实际窗口”插入
  paneKey(key, window)   // computePaneInfo 中，window = 发射时的 actual window
  ```
  ```java
  // :681-683 — 合并路径清除用 stateWindow（存储命名空间）
  if (triggerResult.isPurge()) { clearWindowContents(key, stateWindow); ... }
  ```
- **严重程度**: P2
- **现状**: merging 模式下 `stateWindow != actualWindow`（合并后），pane 键（按 actual）与清除键（按 stateWindow）不匹配 → `(key, actualWindow)` pane 条目泄漏；checkpoint 恢复（open():408-416）会复活泄漏条目并以陈旧 paneIndex 误判 LATE/isFirst。DISCARDING 模式：`emitWindowContents`（:912-914）按 actual window 清，而内容存于 stateWindow 命名空间（:667）→ 合并窗口 fire-and-discard 后状态窗口内容未清。
- **建议**: pane 键与清除路径统一命名空间基准；DISCARDING 清 stateWindow。
- **信心水平**: 很可能（插入/清除键基准差异可证；影响路径为 merging+pane 组合）
- **发现来源视角**: 组合爆炸测试者

---

### [P2-07] 合并路径 purge 跳过 triggerContext.clear()（对照常规路径的不对称）

**P2 判定理由**: merging 窗口 + PurgingTrigger 组合下触发器状态残留（计时器/累加器），PurgingTrigger 需显式接线故影响面窄。

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java:681-683` vs `:714-717`
- **证据片段**:
  ```java
  // 常规路径 :714-717
  if (triggerResult.isPurge()) { clearWindowContents(key, window); triggerContext.clear(); }
  // 合并路径 :681-683
  if (triggerResult.isPurge()) { clearWindowContents(key, stateWindow); }   // 无 triggerContext.clear()
  ```
- **严重程度**: P2
- **现状**: 合并窗口 purge 后触发器注册的 timer 与累加器状态存活；count 类 trigger 从 purge 前值继续计数（下个元素立即误触发），event-time trigger 的过期 timer 稍后对已清内容再次触发。
- **建议**: 合并路径补 `triggerContext.clear()`（与常规路径对齐）。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

### [P2-08] CepOperator STEP-5 "dangling partial match" 清理使用错误超时基准（startTimestamp），且绕过 TimedOutPartialMatchHandler

**P2 判定理由**: 当前仅触及常驻 start state（size==1 守卫使破坏性路径巧合安全）；谓词本身语义错误，属潜伏地雷。

- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java:540-568,600-628`
- **证据片段**:
  ```java
  // :540-554 — 超时判定用 startTimestamp + wt
  if (wt <= 0 || timerService.currentWatermark() < cs.getStartTimestamp() + wt) {
      allTimedOut = false; ...
  ```
  ```java
  // NFA.java:281-293 — 真实逐状态超时按 previousTimestamp 计算（PREVIOUS_AND_CURRENT）
  isTimeoutForPreviousEvent = windowTimes.containsKey(currentStateName)
          && isStateTimedOut(computationState, timestamp, computationState.getPreviousTimestamp(), ...);
  ```
- **严重程度**: P2
- **现状**: STEP-5 用 `startTimestamp`（start state 为 -1）判定，与 NFA 逐状态窗口（PREVIOUS_AND_CURRENT）语义不一致；一旦 watermark > wt-1 即每次 onEventTime 清空 computationStates（start state 重建，DeweyNumber 版本重置）；清理路径不触发 `processTimedOutSequences`（超时事件静默丢弃，不通知 TimedOutPartialMatchHandler）。当前 size==1 守卫使其只清 start state（重建即恢复）——安全性依赖巧合而非正确谓词。
- **建议**: 以 `previousTimestamp` + 真实 `isStateTimedOut` 语义判定；清理前走超时通知路径。
- **信心水平**: 很可能（机制确定；破坏性可达性当前间接）
- **发现来源视角**: 契约考古学家

---

### [P2-09] 前次 multi-audit 发现复核：全部仍 live 未修复（含 P0-01/P1-01）

**P2 判定理由**: 去重规则要求已知未修复 + 高影响项报告并标注"已知未修复"；本次复核无变化。

- **文件**: 见 `ai-dev/audits/2026-08-12-1217-multi-audit-nop-stream-invariant-loop.md`
- **证据片段**:
  ```
  TestWindowOperatorCorrectness.java:453-487 MixedTypeWindowOperator 仍零实例化（仅类声明+构造器）
  WindowOperator.java:1482 ERR_STREAM_WINDOW_NON_ACCUMULATOR_MERGE_CONFLICT 仍零测试引用
  stream-control-rpc.beans.xml:40-57 仍含 ioc:configMethod="..." / ioc:bean="true"
  ```
- **严重程度**: P2（引用级；原判级 P0/P1 不变）
- **现状**: P0-01（merge fail-fast 零回归测试）、P1-01（beans.xml 不存在 IoC 语法）、P2-01~P2-23 全部仍在 live 代码中，无新代码依赖或恶化。
- **风险**: P0-01 的"删 throw 全测试仍绿"风险持续；P1-01 部署模板照抄即失败。
- **建议**: 按 multi-audit 优先修复建议执行（本报告不重复展开）。
- **信心水平**: 确定

---

### [P2-10] mjs scan-output-contract V4 扫描器盲区：嵌套泛型 OutputTag 声明（`OutputTag<List<X>>`）逃逸发射点枚举

**P2 判定理由**: 门禁工具本身的扫描盲区（非当前 live 违约），属工具完备性提示。

- **文件**: `ai-dev/tools/check-nop-stream-invariants.mjs:795-808`
- **证据片段**:
  ```javascript
  const tagDeclRe = /\bOutputTag\s*<[^>]*>\s+(?:final\s+)?([A-Za-z_$][\w$]*)/g;
  // OutputTag<List<Map<String,Integer>>> tag — [^>]* 停在第一个 >，后续 > tag 使 \s+ 不匹配 → 整个声明漏检
  ```
- **严重程度**: P2
- **现状**: 声明正则 `[^>]*` 不支持嵌套泛型：`OutputTag<List<...>>` 形式声明变量后，其 `.collect(name, ...)` 调用不被识别为 V4 发射点 → 新发射点可静默逃逸注册表（配合 roster 的 C2-PR-2 触发条件"raw OutputTag 声明"是独立形态，此形态未被覆盖）。
- **建议**: 声明正则改为括号配对式解析或 `OutputTag\s*<(?:[^<>]|<[^>]*>)*>`；self-test 补嵌套泛型 fixture。
- **信心水平**: 确定（正则行为可直接验证）
- **发现来源视角**: 代码生成受害者（门禁工具侧）

---

## 全量验证基线

- `node ai-dev/tools/check-nop-stream-invariants.mjs all` → exit 0（本轮实测）
- JUnit 门禁 10 类 / 102 tests 基线（2026-08-12 Cycle 2 / I5 记录，代码自彼时无变更）
- 全量 2833 tests / 0 failures 基线同上

## 按严重程度分布

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0 | 3 | 生产运行时服务零接线（CEP open NPE）、区域重启切断 checkpoint 管线、manifest 恢复不推进 ID 计数器（影子窗口丢数据） |
| P1 | 5 | 窗口 checkpoint 恢复崩溃（双后端）、PT 窗口永不触发、fan-out EOS 悬挂、CEP timer 注册表死账/恢复覆盖、InputGate alignment 泄漏竞态 |
| P2 | 10 | 慢泄漏/失败信号失真（2）、并发 NPE 竞态（1）、窗口内部状态生命周期（3）、CEP 清理语义（1）、前次发现引用（1）、门禁工具盲区（1） |

## 总评

nop-stream 门禁体系（不变式 #1-#6）在**结构性契约**（参数传递、输出转发、同步集合、ID 单调、注册表枚举）上表现扎实且与 live 双向吻合，但本次开放式审查暴露了一个**门禁体系之外的系统性盲区：运行时服务注入（wiring）与恢复路径的"接线完整性"**——恰好是 mission 已沉淀六条不变式都不覆盖的维度：

1. **P0-01 是最深的发现**：`ProcessingTimeService` 在 main 代码零接线（连 setter 都没有），却同时被 CepOperator（open 即 NPE）、CEP 处理时间模式、处理时间窗口、periodic watermark 四类功能依赖。测试全部经注入 mock 规避，生产路径零 E2E——这是"测试绿灯但生产必崩"的教科书案例，且 STRM-037 锚点还把该特性写成已实现。**该族（运行时服务注入完整性）符合 roadmap 新族判定标准，建议按 Loop Rule 派生 Cycle 3 / I1 沉淀为"生产 wiring 存在性"门禁**（例如：`setProcessingTimeService` 必须存在生产调用点，否则新增依赖该服务的调用即红——可仿 V1 类级枚举思路做成"服务接线点注册表"）。
2. **恢复路径是第二深盲区**：P0-03（manifest 恢复不推进计数器）、P1-01（aggregate 函数无参反射重建）都只在"checkpoint 后重启"时爆发，而全仓 2833 个测试中 descriptor 路径的 snapshot→restore 往返近乎为零覆盖——与 multi-audit 的 P0-01（修复无测试）同根：**门禁覆盖传递与注册，不覆盖恢复行为**。
3. **P0-02 是组合爆炸实例**：两个各自正确的机制（checkpoint 管线 + 区域监督重启）叠加即死，且现有 E2E 恰好用 `null` coordinator 运行——测试设计规避了组合面。

## 优先修复建议（按序）

1. P0-01 族：运行时注入 ProcessingTimeService（一次接线，四处受益）；同时为 CepOperator 补 null 守卫兜底。
2. P0-03：manifest 恢复路径补计数器单调推进（与 :896-900 同逻辑），一行级修复 + 测试。
3. P0-02：`rebuildTask` 后重接 checkpoint 管线（setBarrierTracker + registerTask + barrier 注入列表）+ 组合 E2E。
4. P1-01：恢复时优先复用 live aggregate 函数实例或改函数实例序列化；补 descriptor 路径 restore 测试。
5. P1 批次：fan-out close 遍历（P1-03）、CEP timer 注册表接线/初始化对称（P1-04）、markFinishedChannel min-ID 选取（P1-05）、PT 窗口接线（P1-02，与 P0-01 同族）。
6. P2 批次：triggerAccumulators 裁剪 → checkpointSuccessMap 裁剪 → getNodeLease 防御 → 合并路径对称性 → 门禁扫描器嵌套泛型。

## 本次审核盲区自评

1. **未重跑全量测试**：以 live 源码 + 基线记录为准（working tree clean），P0-01 的"生产必崩"结论基于静态接线证据，未做"删掉注入后跑生产路径"的实证（生产路径 E2E 不存在是根因）。
2. **执行层 E2E 组合未实证**：P0-02/P1-03/P1-05 的触发依赖时序/拓扑组合，静态推演已闭环但未构造运行测试验证。
3. **未审计 `_gen/` 生成代码与 flow 的 XDSL 模型**（同前次 multi-audit 盲区）。
4. **nop-stream-runtime 的 RPC/transport 层**（TaskExecutor/JobCoordinator 消息路径）本轮仅经子 agent 浅扫，未逐行复核。
5. **性能量化缺失**：P2-04/P2-01 的泄漏速率未做压力实证。

<AI_STEP_RESULT>issues</AI_STEP_RESULT>
