# C3 异步链路正确性 — nop-stream-core-runtime（第 1 轮初审）

- 审计维度：C3（异步链路正确性）——异步组合的异常传播、取消/超时语义、上下文传播与时序假设；定向对照缺陷族 F1（异步快照持有活引用→快照撕裂）与 F2（失败路径"半个成功"）。
- 审计对象（live code，`_` 前缀生成文件不在发现对象内）：
  - `nop-stream/nop-stream-core/src/main/java`（439 files / 57,135 lines）
  - `nop-stream/nop-stream-runtime/src/main/java`（132 files / 29,922 lines）
- 工作目录：`/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`（盲审：未读 ai-dev/audits、plans、bugs、lessons 下任何文件）。
- 前置阅读：AGENTS.md、`docs-for-ai/INDEX.md`、owner doc `docs-for-ai/03-modules/nop-stream.md`、`ai-dev/skills/concurrency-resource-audit-prompt.md`（共享前缀 + C3 正文）、基线 `_tmp/cra-baseline-nop-stream-core-runtime.txt`（C3 async 节选 + C4 threads/schedule 节选用于圈定异步入口）。

## 检查范围说明

按 C3 执行步骤完成：① 全量盘点异步入口（下节清单）；② 逐链核对异常传播（`thenApply/whenComplete/handle/get(timeout)` 消费点、调度任务异常包容）；③ 取消/超时语义（checkpoint 超时 abort、terminate future.get 预算、`shutdownNow` 在途任务、ghost-task 检查——`orTimeout/completeOnTimeout` 全模块零使用，逐点核对 `future.get(timeout)` 超时后原任务去向：PendingCheckpoint 超时由 `timeoutScheduler` abort + `finishCommit(false)` 收敛，无幽灵持续写路径）；④ 上下文传播：`grep -r "ThreadLocal|IContext|ContextProvider|commonPool|parallelStream|ForkJoinPool"` 在两个模块 main sources **零命中**——nop-stream 不依赖请求上下文跨线程传播，此项为空集（如实记录，非未检）；⑤ 时序假设（restore-先于-open、监听器注册先于事件、double-subscribe 幂等——`SourceReaderOperator` restore 暂存/open flush 链、`CheckpointBarrierTracker` per-operator ACK 去重、`markTaskCompleted` 幂等守卫均核对通过）；⑥ F1/F2 定向检查（见 C3-001、C3-004；checkpoint persist 路径的 manifest 快照在 monitor 内构造、不可变对象交接 persist 线程的 F1 论证核对通过；`TwoPhaseCommitSinkFunction.saveState` 已有 monitor 内防御拷贝（RL-4））。

**未深读区域**（本轮以 grep + 抽读覆盖，未逐行）：`StreamTaskInvokable`（1480 行）主循环细节、`InputGate`/`ResultPartition`/`RemoteInputChannel` 的通道内部（主要属 C2/C8 口径）、`MemoryStateSerDe`/`CheckpointSerDe` 序列化往返（属 C5/F11）、连接器子模块（connector-* 不在本轮对象内，仅作为 C3-001 的受害方证据引用）。平台设施（nop-rpc `MessageRpcClient/Server`、nop-commons executor）按误报校准只审使用方式不重审实现。

**非发现但登记的声明边界核对**：`SourceCoordinatorRegistry` 以 vertexId 为 JVM 级键的 "v1 limitation"（javadoc 明文声明单执行假设）在 `OpsJobManager` 多作业同进程形态下无运行时守卫——依赖 `Transformation` 自增 id 在同 JVM 不撞号；DSL stable-id 路径（`Transformation.java:103`）+ 同 JVM 双作业理论上可撞。因属代码内明文声明边界且组合形态未在 owner doc 声明支持，本轮不立项，留复核阶段裁定。

## 异步入口清单

**nop-stream-core**

| 入口 | 线程模型 | 判定 |
|---|---|---|
| `TaskExecutor`（execution/task/TaskExecutor.java:116） | fixed pool（默认 CPU 核数）+ 无界队列，任务=子任务 | 关闭路径 shutdown/awaitTermination 齐备；`awaitCompletion` 吞异常为声明契约（调用方查 task.getError）——通过 |
| `ProcessingTimeServiceDriver`（execution/ProcessingTimeServiceDriver.java:85-133） | 自建 daemon 线程 tick 100ms，只投 mail 不执行回调 | 回调全在任务线程执行、异常 fail-fast 上抛任务循环——通过 |
| `TaskProcessingTimeService`（execution/TaskProcessingTimeService.java:68-132） | timer 注册表 + volatile nextTimerTimestamp 跨线程读 | fire 前摘除防重入、异常包装上抛——通过 |
| `TaskMailbox`（execution/TaskMailbox.java） | MPSC 无界双优先级队列 | close 语义/优先级正确；无界但生产者为 tick/屏障注入（有界速率）——通过 |
| `SourceReaderOperator.offerBarrier`（operators/SourceReaderOperator.java:523-554） | barrier-injector 线程 → mailbox control mail → 任务线程快照 | finished 后 inline 快照路径正确；preOpenSplits/restoredSplits 同步正确——通过 |
| `LocalSourceCoordinator`（source/coordinator/LocalSourceCoordinator.java） | enumerator 声明 coordinator 线程单线程；实际被 reader 任务线程跨线程调用 | **C3-001（P1）** |
| `CachingTableLookup`（connector/lookup/CachingTableLookup.java:193-227） | scan scheduler + refresh pool（后台刷新） | **C3-002（P2）、C3-007（P3）** |
| `InMemoryMaterializationPoint` | synchronized 全方法；无界 store 为声明边界 | 通过 |
| `TwoPhaseCommitSinkFunction`（common/functions/sink/TwoPhaseCommitSinkFunction.java:176-205） | participant.finishCommit 由 coordinator fanout 线程调用 | **C3-003（P2，与 runtime 侧合并）** |
| `CheckpointBarrierTracker` | trigger（注入线程）/ack（任务线程）/abort（超时线程）全 monitor + 回调锁外触发 | 通过 |

**nop-stream-runtime**

| 入口 | 线程模型 | 判定 |
|---|---|---|
| `JobCoordinator.failureDetector`（coordinator/JobCoordinator.java:693） | 单线程 scheduleAtFixedRate | 每轮异常包容（detectFailures 内部）——通过 |
| `JobCoordinator.periodicCheckpointScheduler`（1309-1325） | scheduleWithFixedDelay + try/catch + incrementTriggerFailures | 通过 |
| `JobCoordinator.barrierFanoutExecutors`（983, 2945, 2932-2950） | per-node 单线程 fanout；stop() drain 5s → shutdownNow | 通过（CC-06 已加固） |
| `JobCoordinator.terminateWithTerminalSavepoint`（2468-2535） | future.get(timeout) + 中断恢复 + 失败 failJob | 通过（plan 369 C2 已修静默跳过） |
| `CheckpointCoordinator.persistExecutor`（checkpoint/CheckpointCoordinator.java:640-671, 1097-1101） | fixed pool，异步持久化 + RejectedExecutionException inline 兜底 | 通过（F1 论证：monitor 内构造不可变 manifest） |
| `CheckpointCoordinator.commitFanoutExecutor`（1002, 1059, 1137, 1086-1095） | 1 线程 + 队列 1000 + CallerRunsPolicy | **C3-003（P2）** |
| `CheckpointCoordinator.timeoutScheduler`（1467-1477） | 超时 abort（future.isDone 预检） | 通过 |
| `RetentionCleaner.scheduleRetentionCleanup`（checkpoint/RetentionCleaner.java:111-142） | CAS 串行化 + trailing rerun + Rejected 兜底 | 通过 |
| `TaskCheckpointWiring.startBarrierScheduler`（execution/TaskCheckpointWiring.java:275-288） | 单线程 barrier 注入；CopyOnWriteArrayList 防 CME | 通过 |
| `TaskCheckpointWiring.registerLocalAbortHandler`（320-375） | 超时调度线程跨线程 epoch-precise abort | 通过（InputGate 并发安全结构有注释论证） |
| `GraphModelCheckpointExecutor.triggerTerminalSavepoint`（536-546） | future.get(checkpointTimeout) | 通过 |
| `SupervisionLoop`（execution/SupervisionLoop.java:311-388） | 监督线程轮询 + region 重启（drain-snapshot-redrain-attach 四步） | 通过（CC-02 重放竞态有闭环论证） |
| `TaskManager.heartbeat/commitExecutor/terminalReportExecutor`（taskmanager/TaskManager.java:343, 1012, 1107-1114） | 心跳单线程（整轮 try 包容）；2PC commit 专用池（有界队列 + CallerRuns 计数）；终态上报池（shutdown 前置 drain） | 通过（plan 358/2436 已加固） |
| `WebhookAlertChannel`（alert/WebhookAlertChannel.java:110-157） | 有界队列 256 + 单线程 drain loop + per-event 异常包容 | **C3-006（P3，delivered 无界累积）** |
| `PollingJdbcMessageService`（launch/PollingJdbcMessageService.java:96, 189-192, 293, 305-338） | 2 线程调度池上每订阅一个 poll 任务 | **C3-004（P2）、C3-005（P3）、C3-008（P3）** |
| `StreamControlRpcProxyFactory/Server`（rpc/*.java） | 平台 MessageRpcClient + 单线程 timer；doStop 销毁 timer | 通过（生命周期宿主于 main 类 shutdown） |
| `StreamMetricsReporter`（ops/StreamMetricsReporter.java:74-121） | scheduleAtFixedRate + 整轮 try | 通过 |
| `OpsJobManager.governanceSweeper`（ops/OpsJobManager.java:342-364） | scheduleAtFixedRate + 整轮 try（R5 CC-07） | 通过 |
| `StreamOpsHttpServer`（ops/StreamOpsHttpServer.java） | JDK HttpServer dispatcher；同步 handler，ops 面低频 | 通过（慢 handler 只占 dispatcher，无共享可变状态未守卫迭代） |
| `SavepointMaterializer`（checkpoint/core/SavepointMaterializer.java:76-104） | future.get(termination 预算) | 通过 |

---

## 发现条目

### [nop-stream-core-runtime-C3-001] LocalSourceCoordinator 的 SplitEnumerator 被多个 reader 任务线程无锁并发调用，checkpoint 快照可与枚举器变更交叠（快照撕裂）

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/source/coordinator/LocalSourceCoordinator.java:186, 255-267, 296-314`（对照契约 `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/source/SplitEnumerator.java:19-28`）
- **证据片段**:
  ```java
  // ReaderChannel.requestSplits（reader 任务线程调用）——未持 enumeratorLock：
  public void requestSplits(int subtaskIndex, Optional<Throwable> reason) {
      ...
      try {
          owner.enumerator.handleSplitRequest(subtaskIndex, reason);   // :260
      } catch (Exception e) { LOG.warn(...); }
      owner.enumeratorQueried = true;
  }
  // registerReader 同样锁外：enumerator.addReader(subtaskIndex);      // :186
  // 而 checkpoint 路径持锁：
  public SourceEnumeratorSerializedState snapshotState(long checkpointId) {
      synchronized (enumeratorLock) { ... enumerator.snapshotState(checkpointId); ... }  // :297-302
  ```
- **严重程度**: P1
- **并发场景**: LOCAL 模式 FLIP-27 source-api 作业。类契约（:46-49 与 `SplitEnumerator` javadoc "exactly one enumerator per source vertex; it runs on the JobCoordinator"）声明 enumerator 单线程归属；实际 `requestSplits` 由每个 `SourceReader` 在自身任务线程的 poll 空闲点调用（实证调用方：`FileSourceReader.java:417`、`MessageSourceReader.java:137`、`BatchLoaderSourceReader.java:140`、`DebeziumCdcSourceReader.java:216`），`registerReader→addReader` 在任务 open() 线程执行。parallelism≥2 时即存在 ≥2 个任务线程并发进入同一 enumerator 实例；checkpoint 持久化线程（增量模式为 persist executor 线程，经 `CheckpointCoordinator.buildEpochManifest→snapshotSourceEnumerators→coord.snapshotState`）持 `enumeratorLock` 快照——但 `requestSplits`/`addReader` 不持该锁，锁对这两条路径形同虚设。另 `enumerator` 字段非 volatile，`requestSplits` 锁外裸读，存在读到 null/半初始化引用的理论窗口。
- **触发时序**: T1 = subtask-0 任务线程进入 `FileSplitEnumerator.handleSplitRequest→assignAvailableSplitsTo`（读 `discoveredFiles` 构建 `computeSizeAwarePlan`，随后写 `assignedFiles`——check-then-act 复合序列）；T2 = subtask-1 任务线程同时进入同一 `handleSplitRequest`（两个 reader 同时空闲请求 split 是稳态行为，如目录源两 subtask 同时读完各自 split）→ 两线程都看到同一文件未分配 → 同一 split 双重分配（或计划互相覆盖漏分）。同构地：T1 = 任务线程在 `handleSplitRequest` 中变更枚举器状态；T2 = persist 线程持 `enumeratorLock` 执行 `enumerator.snapshotState` 序列化 → 快照捕获半变更状态（`assignedFiles` 已加、`splitById` 未加），durable manifest 撕裂，restore 后该 split 重投（与 reader 位点快照不一致）或丢失。
- **现状**: `enumeratorLock` 只保护了 `startEnumerator/snapshotState/close` 三个入口；`ReaderChannel.requestSplits`、`registerReader` 的 `addReader` 两条高频路径完全绕开，与类文档声明的线程归属契约不符。
- **风险**: 并行 source 作业（owner doc 声明支持 FLIP-27 split source + 恢复语义）在稳态运行即可触发 split 双重分配/漏分配；增量 checkpoint 的 enumerator 快照可撕裂，restore 语义（P1-6.7-008 同族"恢复不丢位点"）被绕过——恢复后重复读或跳读文件。
- **建议**: 最小修法是把 `handleSplitRequest`/`addReader` 收敛进 `enumeratorLock`（`ReaderChannel.requestSplits` 改为 `owner.handleSplitRequestLocked(...)`，`registerReader` 的 addReader 移入锁内）；或将 `enumerator` 字段收敛为volatile + 全入口持锁的单一门面方法。中期可对照 Flink `SourceCoordinator` 引入单协调线程 + mailbox（平台 `SyncThreadPoolExecutor` 或复用 `TaskMailbox` 模式），消除"哪个线程先 open 就在哪跑"的隐式归属。
- **失效面枚举**（F3 同族清扫——`LocalSourceCoordinator` 全部 enumerator 触点）: ① `startEnumerator`（:110-169，持锁，已正确）；② `registerReader→addReader`（:186，**未持锁**，同族缺陷本条一并修）；③ `ReaderChannel.requestSplits→handleSplitRequest`（:260，**未持锁**，本条主证）；④ `snapshotState`（:297，持锁，已正确但依赖②③同锁才成立）；⑤ `close`（:334，持锁，已正确）；⑥ `finishedSplits` 报告（:284，ConcurrentHashMap.newKeySet，安全）。连接器侧 4 个 enumerator 实现均为无同步的普通类（`FileSplitEnumerator` 用 CHM 集合但 assign 计划是复合序列），修 core 侧锁内收敛后即全部受保护。
- **信心水平**: 确定（锁缺口与跨线程调用路径为代码事实；后果依赖 enumerator 实现，`FileSplitEnumerator` 的复合 assign 序列已核）。
- **误报排除**: 不属于"synchronized/CHM 本身使用"误报——缺陷是**该持锁的入口未持同一把锁**的复合操作错配；也不是低频路径粗锁问题——requestSplits 是 reader 每轮空闲都走的热路径；`FileSplitEnumerator` 的 CHM 字段不使复合 assign 序列原子化，不能据此判安全。
- **复核状态**: 未复核

### [nop-stream-core-runtime-C3-002] CachingTableLookup 后台刷新无 in-flight 去重：delegate 慢/挂死时重复刷新任务在无界队列中线性累积

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/connector/lookup/CachingTableLookup.java:193, 205-227`
- **证据片段**:
  ```java
  refreshPool = Executors.newFixedThreadPool(refreshMaxConcurrency, ...);   // :193 无界队列
  ...
  private void scanAndDispatch() {
      ...
      for (Map.Entry<Object, Entry> e : map.entrySet()) {
          if (closed) { return; }
          if (now - e.getValue().loadNanos >= refreshAge) {
              // the worker pool's size is the concurrency bound (review F-2)
              refreshPool.execute(() -> refreshOne(e.getKey()));            // :224 每轮每过期键重投
          }
      }
  }
  ```
- **严重程度**: P2
- **并发场景**: scan 调度线程（单线程 fixed-delay，轮间不重叠）与 refreshPool 工作线程。`loadNanos` 只在 `refreshOne` **完成**时续期（:245），队列中排队的重复任务不阻止下一轮 scan 再次投递同一键。
- **触发时序**: T1 = refreshPool 全部 `refreshMaxConcurrency` 个线程阻塞在 `delegate.lookup`（JDBC 无 socket 超时/慢查询，单次 10s+）；T2 = scan 每 `refreshIntervalMs` 醒来，对全部 `maxEntries` 个已过期键各投一个新任务；T3 = 队列长度按 `maxEntries × (单位时间轮数)` 线性增长，无任何上限；交叠后果：内存无界增长（每任务 = 一个 runnable + key 引用），delegate 恢复后积压任务倾泻形成重复查询风暴（每键 N 个陈旧刷新任务依次执行，全部打到 DB）。
- **现状**: 扫描与执行分离的设计正确（F-2 review 已确认并发上限=池大小），但缺少"该键已有 in-flight 刷新"的去重标记，且 `newFixedThreadPool` 的无界 `LinkedBlockingQueue` 使积压不可见、不可拒。
- **风险**: 维表 lookup 是 owner doc WI11 声明的热路径组件；DB 抖动（正是该类 javadoc 声明要自愈的场景）反而触发队列累积；长时运行作业内存缓涨 + 恢复期 DB 二次打爆。
- **建议**: 给 `Entry` 加 `AtomicBoolean refreshing` 标记（CAS 成功才投递，`refreshOne` finally 复位），一个字段即同时消灭重复投递与风暴；或改用平台 `io.nop.commons.cache.IAsyncCache` / 有界队列 + `QueueOverflowPolicy.DROP` 并打点。
- **失效面枚举**（F3 同族清扫）: ① `scanAndDispatch`（本条）；② per-record `lookup` miss 路径（:144，同步、无此问题）；③ `refreshOne` 重入读（:240，last-write-wins 幂等，无累积）；全类仅一个异步投递点，无其他兄弟入口。同族模式 grep：模块内 `scheduleWithFixedDelay`+`execute` 组合仅此一处（TaskCheckpointWiring/JobCoordinator 的 schedule 均无按条目重投形态）。
- **信心水平**: 确定（累积机制为代码事实；"挂死 delegate"前提在 JDBC 维表场景是常规故障模型）。
- **误报排除**: 不是"有界声明存在的队列"误报——`newFixedThreadPool` 队列无界且无 QueueOverflowPolicy 配置；也不是单纯慢的 P3——累积是单调的、与声明包络无关。
- **复核状态**: 未复核

### [nop-stream-core-runtime-C3-003] CheckpointCoordinator commit-fanout 的 CallerRuns 兜底在持有 coordinator monitor 的线程上执行 sink commit I/O，并打破 finishCommit 去重守卫依赖的单线程串行假设

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:731-733, 1002-1013, 1086-1095`；`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/functions/sink/TwoPhaseCommitSinkFunction.java:190-200`
- **证据片段**:
  ```java
  // executePersistAsync：持久化线程持 monitor 调 success 回调
  synchronized (this) { onCompletePersistSuccess(completed, pending, true); }   // :731-733
  // success 回调内（要求持 monitor）投递 commit fan-out：
  getOrCreateCommitFanoutExecutor().execute(() -> { retryFailedCommits(); ...
          notifyParticipantsFinishCommit(fanoutCheckpointId, true); ... });     // :1002
  // 池定义：1 线程 + 队列 1000 + CallerRunsPolicy
  new ThreadPoolExecutor(1, 1, 0L, MILLISECONDS, new LinkedBlockingQueue<>(1000),
          ..., new ThreadPoolExecutor.CallerRunsPolicy());                      // :1088-1092
  // TwoPhaseCommitSinkFunction.finishCommit：check-then-act 去重
  synchronized (pending) { if (!pending.containsKey(eid)) continue; }           // :192-195
  commit(eid);                                                                 // :196 锁外
  synchronized (pending) { pending.remove(eid); }
  ```
- **严重程度**: P2
- **并发场景**: checkpoint-persist 线程（持 coordinator monitor 的 success 回调）× checkpoint-commit-fwd 单线程 × （饱和时）CallerRuns 内联执行。fan-out 任务含 sink `finishCommit` 的 JDBC/文件 I/O。
- **触发时序**: T1 = commit-fanout 线程在慢 sink（单次 commit 60s）上爬行，每完成一个 checkpoint 入队 1-2 个任务而消费速率 ~1/60s；周期 checkpoint 1s 一次 → 约 17 分钟后队列满 1000；T2 = 下一次 `executePersistAsync` 完成，其持久化线程在 `synchronized(this)` 内执行 `execute(...)` → CallerRuns 命中 → **该线程持 coordinator monitor 串行执行 `retryFailedCommits()` + sink commit I/O**；交叠后果 A：monitor 被持有 60s+，期间所有 `acknowledgeTask`/`tryTriggerPendingCheckpoint`/`abortPendingCheckpoint`（全 synchronized）阻塞——checkpoint 管线整体冻结、ACK 超时级联；后果 B：此时 fanout 线程可能同时在跑 `retryFailedCommits`，两线程对同一 `failedCommitParticipants` epoch 各调一次 `participant.finishCommit(E, true)` → `TwoPhaseCommitSinkFunction.finishCommit` 的 `containsKey→commit→remove` 三步非原子（:192-199），两线程都在对方 remove 前通过 containsKey → `commit(eid)` 双重执行（file 2PC 二次 rename 失败进入 failedCommitParticipants 重试循环；无幂等账本的 sink 产生重复提交）。
- **现状**: CallerRuns 是 plan 369 R5-CC-10 的裁定（"不丢通知"），但裁定针对的是 TaskManager 侧 `saturatedCommitQueuePolicy`（:310-318，注释明确论证了"调用方不是 checkpoint monitor"）；`CheckpointCoordinator` 侧同名兜底**没有**这条论证——它的三个 execute 调用点全部在 monitor 内，CallerRuns 语义在持锁栈上放大为"锁内 I/O + 串行假设破裂"。
- **风险**: 慢 sink + 常规 checkpoint 间隔即可进入；进入后 checkpoint 吞吐塌缩且可观测性差（monitor 阻塞不产生任何计数）；双重 commit 的外显取决于 sink 幂等性（jdbc-2pc 有台账幂等、file 2PC 无）。
- **建议**: ① `onCompletePersistSuccess/Failure`/`abortPendingCheckpoint` 内的 execute 改为"CAS 抢占 + 拒绝时记 pending 标记、锁外重投"（平台 `HighWatermarkSemaphore` 或复制 RetentionCleaner 的 CAS+recheck 模式）；② 或将 CallerRuns 替换为 AbortPolicy + 补偿重投；③ `TwoPhaseCommitSinkFunction.finishCommit` 把 `commit(eid)` 纳入 `synchronized(pending)`（commit 慢会拉长临界区，可与①配合仅对重复 commit 风险窗口加守卫：commit 前 CAS `pending.computeIfPresent` 摘除后再提交，失败则回插）。
- **失效面枚举**（F3 同族清扫）: `getOrCreateCommitFanoutExecutor().execute` 共 3 处：success 回调（:1002，monitor 内）、persist-failure 回调（:1059，monitor 内）、abort 路径（:1137，synchronized 方法内）——**三处全部命中同一缺陷**，无一锁外。兄弟对照：TaskManager `saturatedCommitQueuePolicy`（:310-318）调用方为 dispatch 线程（不持 monitor），已有正确论证与计数，不命中。`check-then-act` 守卫兄弟：`restoreFromEpoch` 的 commit/remove（:250-261）仅恢复单线程路径调用，不并发，不立项。
- **信心水平**: 很可能（队列饱和前提需要慢 sink 持续存在，但一旦饱和则 monitor 内 I/O 是确定性执行路径；双重 commit 依赖 fanout 线程恰好同时在跑 retry）。
- **误报排除**: 不是"CallerRuns 本身"误报——问题在**调用点持 monitor**这一复合语境；也不是低频管理路径粗锁——commit fan-out 是每个 checkpoint 的常驻路径；队列容量 1000 的"有界声明"与"单线程消费 + 60s/任务"的规模组合矛盾（约 17 分钟必满），不属"上限配置与使用方规模声明矛盾"豁免之外。
- **复核状态**: 未复核

### [nop-stream-core-runtime-C3-004] PollingJdbcMessageService 消费回调抛异常后游标仍前进：失败消息被永久跳过，声明的 at-least-once 被打破（半个成功）

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/launch/PollingJdbcMessageService.java:328-333`
- **证据片段**:
  ```java
  try {
      consumer.onMessage(topic, message, ctx);
  } catch (Exception e) {
      LOG.error("Consumer onMessage failed for topic={} id={}", topic, row.id, e);  // :330-331
  }
  lastSeenId.set(row.id);          // :333 异常路径同样前进游标 → 消息永久丢弃
  ```
- **严重程度**: P2（类 javadoc 声明为测试基础设施；生产面为 SysDaoMessageService，故不判 P1）
- **并发场景**: poller 调度线程（单消费者）× 消息生产者线程。失败路径无重投、无死信、无中间态标记。
- **触发时序**: T1 = poll 批量取回 deployTask / checkpoint commit 行；T2 = 消费 `consumer.onMessage` 因瞬时故障抛异常（DB 连接闪断、反序列化成功但处理失败）；T3 = 游标照样推进 → 该控制面消息**永不重投**；交叠后果：TM 侧缺一条 `deployTask` 行 → 子任务停滞直至 60s liveness 检测触发全局恢复（自愈存在但以恢复风暴为代价）；缺一条 commit 通知 → 依赖 checkpoint 超时 abort 兜底。类 javadoc（:69-70）声明 "at-least-once delivery"，本路径使失败消息成为 at-most-once，声明与实现矛盾。
- **现状**: 仅日志可见（F9 家族：吞异常后状态不一致——队列行已"消费"而业务未处理，且失败对外不可见）。
- **风险**: 测试基础设施下表现为偶发"部署行丢失→恢复延迟"，污染 e2e 稳定性归因；若该类被按 javadoc 契约复用到非测试场景，控制面消息静默丢失。
- **建议**: 与反序列化失败路径（:322-326，毒丸跳过是合理显式语义）区分：consumer 异常时**不推进游标**（下轮重投，at-least-once 兑现）或引入有限重试 + 显式死信标记；至少把 javadoc 的 delivery 语义声明改写为与实现一致。
- **失效面枚举**（F3 同族清扫）: 本文件游标推进点共 2 处：反序列化失败（:325，毒丸跳过——显式声明可接受）、consumer 异常（:333，本条）。兄弟实现：`SysDaoMessageService`（生产面）的 CLAIM/WAITING 行模型有重扫语义，不命中；`DataPlaneMessageServiceAdapter.AdaptingConsumer` 解码失败按 `WireDecodeFailureAware` typed 转发（fail-fast 契约），不命中。
- **信心水平**: 确定。
- **误报排除**: 不是"测试代码按生产标准报 P1"的误报——已按其声明定位降为 P2；也不是毒丸跳过（那是有界显式策略）——本条是**可重试的瞬时失败**被当成毒丸处理。
- **复核状态**: 未复核

### [nop-stream-core-runtime-C3-005] PollingJdbcMessageService.sendAsync 是伪异步：JDBC INSERT 在调用方线程同步执行，异常绕过 CompletionStage 异常通道

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/launch/PollingJdbcMessageService.java:188-192`
- **证据片段**:
  ```java
  @Override
  public java.util.concurrent.CompletionStage<Void> sendAsync(String topic, Object message, MessageSendOptions options) {
      send(topic, message, options);   // :190 同步 JDBC INSERT + 同步抛异常
      return java.util.concurrent.CompletableFuture.completedFuture(null);
  }
  ```
- **严重程度**: P3
- **并发场景**: 平台 `MessageRpcClient`（控制面 RPC）经 `IMessageService.sendAsync` 发请求/回执——RPC 客户端的超时定时器与 `.exceptionally` 链均按异步完成建模；此处全部 I/O 与异常都发生在调用方线程。
- **触发时序**: T1 = RPC 客户端线程调 `sendAsync`；T2 = INSERT 抛异常（DB 闪断）→ 异常**同步**抛出到调用方栈，pre-completed future 根本未返回；客户端 `.handle/.exceptionally` 注册的异常通道不生效，失败以裸异常形态出现在与异步契约不同的位置（fanout executor 任务里被 catch + log，行为恰好等价但归属错位）。慢 DB 时调用方线程被阻塞的时长不受 future 超时语义控制。
- **现状**: 功能上"最终等价"（失败仍可见），但接口契约（`CompletionStage` 异步完成 + 异常进 future）被违反；与 `DataPlaneMessageServiceAdapter.sendAsync`（真委托）行为不对称。
- **风险**: 低——控制面低频；主要危害是未来任何依赖"sendAsync 不阻塞调用方、异常走 future"的新调用方（如把数据面 send 从 `send` 切到 `sendAsync`）会静默继承同步阻塞。
- **建议**: `CompletableFuture.runAsync(() -> send(...), ...)`（指定模块自建执行器而非 commonPool）或至少 javadoc 显式声明同步执行语义。
- **失效面枚举**（F3 同族清扫）: `IMessageService` 实现的 sendAsync 共 3 处：`PollingJdbcMessageService.sendAsync`（本条）、内层 `ConsumeContext.sendAsync`（:411-416，显式 UnsupportedOperationException，fail-fast 合规）、`DataPlaneMessageServiceAdapter.sendAsync`（:74，真委托，合规）。
- **信心水平**: 确定（契约违反为代码事实；后果评估为低）。
- **误报排除**: 不按"同步 send 存在即合理"豁免——被违反的是 SPI 异步方法自身的签名契约，且同模块已有正确实现形成对照。
- **复核状态**: 未复核

### [nop-stream-core-runtime-C3-006] WebhookAlertChannel.delivered 在生产路径无界累积（声明为测试可观测，但生产 drain loop 无条件追加、永不清理）

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/alert/WebhookAlertChannel.java:67, 180, 134-136`
- **证据片段**:
  ```java
  private final List<AlertEvent> delivered = new CopyOnWriteArrayList<>();   // :67
  ...
  if (response.statusCode() >= 200 && response.statusCode() < 300) {
      delivered.add(event);                                                   // :180 成功即追加
      return;
  }
  ...
  public void close() { deliveryExecutor.shutdownNow(); }                     // :134 队列未 drain，close 丢弃在途告警
  ```
- **严重程度**: P3
- **并发场景**: drain loop 单线程写，运维/测试线程经 `getDelivered()` 读（CopyOnWriteArrayList 读安全）。
- **触发时序**: 无需竞态——单调增长：每个成功投递的告警事件永久驻留；天/周级长驻 JC 进程 + 持续 RECOVERY/DEGRADED 事件流下线性累积（F4 无界账本家族，纯内存形态）。
- **现状**: 字段 javadoc 标注 "Delivered alerts (test observability)"，但追加在无任何测试开关守卫的生产投递路径上；`close()` 亦不 drain `pending`（256 容量内在途告警静默丢弃——与"best-effort"契约一致，危害低，一并登记）。
- **风险**: 内存缓涨（单条 AlertEvent 小，增速取决于告警量）；更实际的是误导——长驻进程 `getDelivered()` 无限膨胀使该"可观测面"不可用。
- **建议**: 环形上界（如保留最近 256 条，`ArrayDeque` + 同步）或仅在测试构造器（`forTest` 标志）启用追加。
- **失效面枚举**（F3 同族清扫）: alert 包内进程级累积面共 2 处：`WebhookAlertChannel.delivered`（本条）、`AlertService` 路由（无累积，转发即忘）。同族对齐：CheckpointCoordinator 观测历史有 `maxEntries` + 治理清扫双约束（已正确），本条是唯一无界者。
- **信心水平**: 确定（增长为代码事实；量级评估为低）。
- **误报排除**: 不属"有界声明存在的缓存"豁免——无任何上界；不按纯 C5 口径剔除——增长点就在本维度审计的异步 drain loop 内，属异步链路的长期运行语义。
- **复核状态**: 未复核

### [nop-stream-core-runtime-C3-007] CachingTableLookup.Entry.loadNanos 为非 volatile long：refresh 工作线程写、任务线程读的数据竞争（后果良性）

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/connector/lookup/CachingTableLookup.java:85-94, 141, 244-245`
- **证据片段**:
  ```java
  static final class Entry {
      /** Volatile: written by refresh workers, read by the task thread. */
      volatile Object value;
      long loadNanos;                 // :88 非 volatile，同被跨线程读写
  ...
  if (entry != null && now - entry.loadNanos < ...ttl...) {   // :141 任务线程读
  ...
  entry.value = ...; entry.loadNanos = System.nanoTime();     // :244-245 工作线程写
  ```
- **严重程度**: P3（理论风险，未具体化出损害时序——按纪律降级并如实标注）
- **并发场景**: refreshPool 工作线程（写 `value`→`loadNanos`）× 任务线程（读 `loadNanos`→`value`，hit 判定）× scan 线程（读 `loadNanos` 判过期）。
- **触发时序**: 无法构造出可见损害的交错：最坏交错是任务线程读到旧 `loadNanos`（判定过期→走同步 `delegate.lookup` 重查，:144 自愈且幂等）或读到新 value 配旧 age（多一次同步查询）；JMM 下 long 可撕裂（非 volatile 64 位），但 nanoTime 高位恒 0 使实际撕裂不可达。同步重查路径自身线程安全。
- **现状**: 字段注释只声明了 `value` 的 volatile 意图，`loadNanos` 的跨线程读取是无意的数据竞争。
- **风险**: 仅性能（缓存命中率抖动）+ 严格 JMM 意义上的未定义可见性；无正确性后果。
- **建议**: `loadNanos` 加 `volatile`（Entry 已是近自由访问对象，代价可忽略），与 `value` 的注释契约对齐。
- **失效面枚举**（F3 同族清扫）: `Entry` 跨线程字段全集 = `value`（volatile，已正确）、`loadNanos`（本条）；`Entry` 无其他可变字段。同构对照：`RemoteResultPartition.backlogCacheAt/backlogCacheValue`（:93-94，非 volatile 但全方法 synchronized，已正确）。
- **信心水平**: 确定（竞争存在）/ 确定（后果良性）。
- **误报排除**: 主动标注为良性以免复核升级——不是"看到共享字段就报线程不安全"：价值在于使字段契约与注释一致，防未来在 `loadNanos` 上叠加正确性逻辑。
- **复核状态**: 未复核

### [nop-stream-core-runtime-C3-008] PollingJdbcMessageService 的 2 线程调度池承载全部订阅的 poll：单个慢消费阻塞其他 topic 的投递节奏

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/launch/PollingJdbcMessageService.java:96-100, 293`
- **证据片段**:
  ```java
  this.poller = Executors.newScheduledThreadPool(2, r -> {          // :96 固定 2 线程
      Thread t = new Thread(r, "polling-jdbc-msg"); t.setDaemon(true); return t;
  });
  ...
  this.task = poller.scheduleWithFixedDelay(this::poll, pollIntervalMs, pollIntervalMs, ...);  // :293 每订阅一个任务
  ```
- **严重程度**: P3（测试基础设施声明范围内）
- **并发场景**: 多订阅（控制面 per-node topic + 协调器 uplink + 每条数据面 edge 一个 topic）共享 2 个 poller 线程；`consumer.onMessage` 在 poll 线程内同步执行（TM 侧 dispatch 可能耗时数十 ms 至秒级）。
- **触发时序**: T1 = 线程 0 执行 jobA 数据面 topic 的 poll，consumer 处理一批记录耗时 500ms；T2 = 期间线程 1 在执行另一慢 poll；其余订阅的到期 poll 在队列中等待 → 控制面（barrier 触发 RPC、cancel、ACK 上行）投递延迟最高一个慢 poll 时长。`scheduleWithFixedDelay` 保证不堆积，仅延迟，无丢失。
- **现状**: 池大小 2 为硬编码，与订阅数无关。
- **风险**: 测试/演练形态下的时延噪声与伪超时（依赖固定超时的测试偶发红）；无丢失、无堆积。
- **建议**: 池大小 = `max(2, 订阅数)` 或配置化；控制面与数据面拆分独立调度池。
- **失效面枚举**（F3 同族清扫）: 模块内共享调度池承载多业务订阅的仅此一处；`StreamControlRpcProxyFactory` 每 topic 独立 timer（:62，已隔离）、`JobCoordinator.barrierFanoutExecutors` per-node 隔离（:2932-2950，已隔离）。
- **信心水平**: 确定。
- **误报排除**: 类 javadoc 明示测试基础设施定位（生产用 SysDaoMessageService/Pulsar），按校准降 P3 如实登记而非拔高。
- **复核状态**: 未复核

---

## 计数与移交

| 严重程度 | 数量 | 条目 |
|---|---|---|
| P0 | 0 | — |
| P1 | 1 | C3-001 |
| P2 | 3 | C3-002, C3-003, C3-004 |
| P3 | 4 | C3-005, C3-006, C3-007, C3-008 |

F1/F2 定向结论：checkpoint 持久化主链（manifest monitor 内快照 → 不可变交接 → 失败回滚增量段）与 2PC sink 快照（monitor 内防御拷贝）通过 F1 核对；F2 命中一处（C3-004 消费失败即跳过，无恢复语义）。C10 保护力推演提示：C3-001 无并发测试会红（并行 source + 并发 split 请求场景缺失），C3-003 队列饱和路径无可测钩子（`commitQueueDepth` gauge 只覆盖 TaskManager 侧，CheckpointCoordinator 侧 fanout 队列无观测）——留 C10 维度汇总。

# C3 异步链路正确性 — nop-stream-core-runtime（深挖第 2 轮追加）

- 承接：`C3-async-chains.md`（第 1 轮，C3-001…C3-008）。本轮只输出新发现，编号接续。
- 盲审边界：仅读取本维度文件与 live code；未读 ai-dev/audits、plans、bugs、lessons 下其他内容。
- 工作目录：`/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`。

## 深挖第 2 轮追加

### 本轮覆盖的盲区（对应深挖指令第 2 条）

| 盲区 | 排查对象 | 结论 |
|---|---|---|
| 未排查的 CompletableFuture 链异常去向 | `StreamControlRpcServer.CorrelatingRpcService.callAsync`（`stage.handle` 双分支收口，异常转 ErrorResponse）；`PendingCheckpoint.abort/fail/forceFail/dispose`（异常/取消通道全覆盖，`dispose` 的 `cancel(false)` 使等待方收 `CancellationException`——逐一核对全部 `get(timeout)` 等待方 `SavepointMaterializer:94`、`GraphModelCheckpointExecutor:545,781`、`JobCoordinator:2490`，均以宽 `catch (Exception)` 收敛为显式 StreamException，无静默）；`DataPlaneMessageServiceAdapter`（纯委托） | 通过，无新发现 |
| 取消/超时后幽灵任务 | `TimerRegistrationFuture.cancel`（摘除先于 markFired 的窗口内 cancel 返回 false、回调照跑——in-flight 语义，无双发）；`TaskProcessingTimeService.fireDueTimers`（fire 前摘除 + 全 task 线程序列化）；`RunningTask.cancel`（mailbox cancel + interrupt + 条件 remove，`SubtaskTask.cancel` 为 CAS+interrupt 非阻塞，无 join 挂死 abort 持锁线程）；`TaskManager.deployTask`（R6 CC-02 compute 原子置换 + 条件 remove + fencing 拒绝，重复部署/迟到 stale 部署均有 typed 出口）；`ProcessingTimeServiceDriver`/barrier-injector（均在 invoke/stop 的 shutdownNow 关闭路径内）；`EmbeddedDistributedExecutor.waitForCompletion`（deadline 轮询有界） | 通过，无新发现 |
| 回调抛异常杀死调度线程 | 全模块 `scheduleAtFixedRate/scheduleWithFixedDelay` 站点复扫：barrier-injector（`TaskCheckpointWiring:275-285` 整轮 try + incrementTriggerFailures）、`JobCoordinator.failureDetector/periodicCheckpointScheduler`、`StreamMetricsReporter`、`OpsJobManager.governanceSweeper`、`RetentionCleaner`、`PollingJdbcMessageService.poll`（:309-337 整体 try）、`StreamJobEventBus.fire`/`JobHealthStateMachine.transition`（per-listener try）——均有整轮包容。唯一裸露点：`CachingTableLookup.scanAndDispatch` 内 `refreshPool.execute` 的 `RejectedExecutionException`（close 竞态窗口）会杀死 scan 调度——但 close 后 `lookup` 本就 fail-fast，调度死亡不可达任何消费者，按噪声阈值不立项 | 通过，无新发现（一处 close 竞态 REE，不可达消费者，不立项） |
| double-subscribe/重复回调幂等性 | `CheckpointCoordinator.registerTask`（Set 语义幂等）；listener/participant 注册表（`SupervisionLoop.rewireCheckpointPipeline:1277-1291` unwire→remove-old-invokable→wire 配对闭环，CoWList 快照迭代与 fanout 线程安全）；`PendingCheckpoint.acknowledgeTask`（set remove + map put 双幂等，后端 at-least-once 重投的重复 ACK 安全）；`CheckpointBarrierTracker.triggerCheckpoint` 重入返回 false（overlap 拒绝）；`LocalSourceCoordinator.ReaderChannel.pendingSplits`（addAll 与 copy+clear 同 monitor，无丢失）；数据面重复投递由 `StreamMessageEnvelope.epochId` fencing 过滤（`RemoteInputChannel.EnvelopeConsumer:634`）；`StreamOpsHttpServer` 其余 handler 经 ops 服务读 CHM 快照面 | 通过，无新发现 |
| F1/F2 定向面漏网（其他快照/多目标提交路径） | checkpoint 主链与 2PC sink 快照第 1 轮已核对；本轮新覆盖：**REST queryable-state 读面（命中，C3-009）**、`TwoPhaseCommitSinkFunction.finishCommit` 的 `else commit(epochId)` 空表回退分支（fanout 线程 commit 当前 epoch 与 task 线程 invoke 并发——依赖子类 per-epoch 事务句柄隔离的声明契约，无共享句柄证据，不立项）、GMCE SUSPEND `stopSources`（在 `SupervisionLoop.run` 返回即全部任务终态之后执行，无并发）、`unwire/wire` 与 commit fanout 交叠（CoW 快照 + 旧实例持有 durable pending 属声明语义） | **命中 1 处（C3-009）** |
| 第 1 轮未深读区域补扫 | `StreamTaskInvokable`（1480 行）主循环逐段：cancel/EOS 终态区分（`InputLoopExitReason`）、A8/G11 关闭对称（`closeChainAndGate` 先链后门、suppressed 保留）、side-output 路由（HashMap 注册在 task 提交前完成，executor 队列建立 happens-before）、`EdgeWriterOutput` tag 路由幂等；runtime 未扫包 `cluster/deploy/event/health/maintain/metrics/protocol/source`（异步入口仅 `JobHealthStateMachine`，synchronized + 非阻塞 offer 监听器，通过）；`SysDaoMessageService` 位于 `nop-sys/nop-sys-dao`，超出本轮审计对象边界（仅作第 1 轮引用），未审 | C3-009 之外无新发现 |

---

## 发现条目

### [nop-stream-core-runtime-C3-009] queryable-state REST 读面对运行中任务的 keyed 状态做无同步跨线程快照：任务线程逐条写入与 HTTP worker 迭代裸并发（快照撕裂 + CME）

- **文件**:
  - `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/ops/StreamOpsHttpServer.java:123-124, 240-264`
  - `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/taskmanager/TaskManager.java:149-158`
  - `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/task/StreamTaskInvokable.java:613-638`
  - `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryKeyedStateBackend.java:109, 634-636`
  - `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryStateSerDe.java:141-147, 318-342`
  - `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryValueState.java:23`
- **证据片段**:
  ```java
  // StreamOpsHttpServer —— 4 个 http worker 线程执行 handler（非任务线程）：
  httpServer.setExecutor(Executors.newFixedThreadPool(4,
          NopStreamThreadFactory.named("stream-ops-http")));            // :123-124
  ...
  StateSnapshot snapshot = taskManager.keyedStateSnapshot(jobId, vertexId, subtaskIndex);  // :254
  // TaskManager —— 直接解引用 invokable，无 mailbox 交接、无暂停/对齐守卫：
  RunningTask task = runningTasks.get(taskKey);
  return task.invokable.keyedStateSnapshot();                           // :158
  // MemoryKeyedStateBackend —— states 为裸 HashMap；快照委托 serde：
  private final Map<String, Object> states = new HashMap<>();           // :109
  public StateSnapshot snapshotState() throws Exception {
      return new MemoryStateSerDe(this).snapshotState(states);          // :634-636
  }
  // MemoryStateSerDe —— 两层无锁迭代：states → 每个状态的 storage：
  for (Map.Entry<String, Object> entry : states.entrySet()) { ... }     // :141-147
  for (Object eObj : storage.entrySet()) {                              // :330
      ... entryWriter.write(entry, e.getValue(), valueSer); ...         // :331-339
  }
  // MemoryValueState —— 任务线程逐条写的 storage（同一 HashMap）：
  final Map<TypedNamespaceAndKey, T> storage = new HashMap<>();         // :23
  ```
- **严重程度**: P2
- **并发场景**: `stream-ops-http` worker 线程（WI14 queryable-state-lite 读面，`GET /jobs/{id}/vertices/{v}/subtasks/{i}/state`）× 任务线程（逐条 `processElement → state.update()/add()` 写 `states`/`storage` HashMap）× （次要）scan/TTL 清理线程不涉及。任务侧唯一正确快照路径是 barrier 处理时的 `snapshotState`（与写在同一任务线程串行）；REST 路径绕开了这一线程归属。
- **触发时序**: T1 = 运维/监控对运行中作业轮询 state 端点（该端点的设计用途就是被轮询），http worker 进入 `MemoryStateSerDe.snapshotKeyedState` 的 `for (Object eObj : storage.entrySet())`（:330）；T2 = 任务线程同时在处理下一条记录，`MemoryValueState.update → storage.put(newKey, v)` 触发该 HashMap 扩容/新增 bucket；T3 交叠后果（三选一，取决于命中窗口）：① 迭代器检测到 modCount 变化抛 `ConcurrentModificationException` → 被 handler 的 `catch (StreamException e)` 之外的路径逸出 → 500（宽 catch 只捕 StreamException，:258-261——REE/CME 以 500 形式对外，可观测但查询失败）；② 无 CME 的窗口内读到撕裂快照：`states` 层迭代到 state-A 的新值与 state-B 的旧值混布，或 `storage` 层某 key 的 payload 在 `defensiveCopy` 执行时值已被后写覆盖一半（copy 非原子于业务对象的多字段变更）→ 响应返回一个**从未存在过的状态组合**，监控据此告警/对比即误判；③ 迭代跨越扩容窗口漏读 bucket 条目 → 快照缺 key。T2 若并发于 `states` 结构变更（算子 open/restore 后首次 getState 注册新 state——仅启动窗口）则后果同构于①。
- **现状**: 端点 javadoc 声明 "read-only keyed-state snapshot ... the same JSON-native StateSnapshot the checkpoint path persists"（`TaskManager:142-148`）——但 checkpoint 路径的撕裂防护（R5/R6 系列裁定：monitor 内快照、freeze-on-task-thread）全部依赖"快照在任务线程执行"这一前提；REST 路径复用了同一个 `snapshotState()` 却没有复用它的线程归属前提，无任何守卫（无 mailbox 交接、无只读视图、无 synchronized）。
- **风险**: 该端点是 WI14 声明的可观测面，被监控轮询是常态而非边缘用法；带 keyed 状态的热作业（窗口/定时器类，owner doc 声明的主场景）下每次轮询都在赌窗口。后果限于查询面（500 / 错误数据），不损坏任务状态与持久化资产——故不判 P1；但"可观测面返回从未存在过的状态"在故障定位场景比查询失败更有害（静默误导）。
- **建议**: 三选一（成本递增）：① 最小修——快照经任务 mailbox 交接执行（`invokable.getMailboxExecutor()` 已存在，投递 `keyedStateSnapshot` mail 并在 mail 内产出不可变 `StateSnapshot`，HTTP 线程 `get(timeout)` 等待；任务卡死时走 typed 超时）；② 只读视图——为内存后端提供 CHM 包装的 storage（写放大可忽略：状态写入非本维度热路径瓶颈口径）；③ 文档降级——在端点 handler 前置 `invokable` 忙标记（checkpoint 对齐中拒绝查询）并 javadoc 声明"可能与任务写入并发，结果不保证一致"，把现状从隐性缺陷变为显式边界。推荐 ①：与平台 mailbox 设计（`ai-dev/design/nop-stream/mailbox-design.md` 注明的 control-plane anchor）一致。
- **失效面枚举**（F3 同族清扫——`snapshotState` 的全部跨线程读点）: ① checkpoint 路径 `AbstractStreamOperator.snapshotCallback → barrierTracker.acknowledgeOperator`（任务线程 processBarrier 内执行，与写串行，**安全**）；② restore 路径 `restoreState`（任务启动单线程，open 前，**安全**）；③ REST 路径 `keyedStateSnapshot`（本条，**缺陷**）；④ `MemoryOperatorStateBackend.snapshotState`（仅被 ① 复用，任务线程，**安全**）；⑤ RocksDB 后端（nop-stream-rocksdb，不在本轮对象内，其快照走自身句柄语义——修复 ①③ 收敛后需在 rocksdb 维度复核同族）；⑥ `StreamOpsHttpServer` 其余读端点（/jobs、/checkpoints、/metrics 经 ops 服务与 Micrometer registry，读 CHM/registry 快照面，**安全**）。同构对照：第 1 轮 C3-001（enumerator 快照撕裂）同属"快照执行线程与写线程分离且锁不对齐"家族，但那是持久化资产（P1）；本条撕裂的是查询响应（P2）。
- **信心水平**: 确定（跨线程无同步为代码事实；CME/撕裂窗口在"轮询监控 + 带 keyed 状态热作业"下必然可命中）。
- **误报排除**: 不属于"无状态单例并发调用"豁免——被并发访问的是任务私有的可变状态后端；也不是"REST 低频路径粗锁合理"豁免——缺陷不是锁粒度而是**完全无同步**，且后果是错误数据而非仅延迟；第 1 轮 C3-007 已按纪律把良性可见性竞态标为 P3，本条与其区别在于后果可具体化为对外可见的错误查询结果/500，非良性。
- **复核状态**: 未复核

---

## 计数与移交

| 严重程度 | 数量 | 条目 |
|---|---|---|
| P0 | 0 | — |
| P1 | 0 | — |
| P2 | 1 | C3-009 |
| P3 | 0 | — |

第 2 轮累计（两轮合计）：P0=0，P1=1（C3-001），P2=4（C3-002/003/004/009），P3=4（C3-005/006/007/008）。

C10 保护力移交追加：C3-009 无并发测试会红（无"HTTP 轮询 state 端点 × 任务线程并发写 keyed 状态"的测试；现有 state 端点测试若存在必然是任务静止时查询——恰是缺陷不可达的窗口），留 C10 维度汇总。

---

# C3 异步链路正确性 — 维度复核结论（独立复核子代理，2026-10-14）

- 复核人：独立复核子代理。本轮解除盲审：已读 `ai-dev/audits/2026-09/`（r1–r4、platform-conformance、code-review-full）与 `ai-dev/audits/2026-10/`（r5/r6、adversarial-review）下 nop-stream 相关审计，及 `git log`。
- 复核方法：逐条重读 live code 与全部调用方实证——connector 4 个 reader 的 `requestSplits` 调用点（行号逐一比对命中）、`CheckpointCoordinator` 三处 fanout `execute` 的持锁栈、`TwoPhaseCommitSinkFunction.finishCommit` 去重窗口、`MemoryStateSerDe` 两层无锁迭代、`WebhookAlertChannel` 生产投递路径与 `getDelivered` 消费面（仅测试消费）、`PollingJdbcMessageService` 游标推进点与 `SysDaoMessageService`/`DataPlaneMessageServiceAdapter` 兄弟对照。
- 判定口径：`ai-dev/skills/concurrency-resource-audit-prompt.md` 误报校准 6 条 + 复核指令三规则（写不出具体时序的并发发现降级标理论风险；已声明边界 + fail-fast 守卫非缺陷；测试基础设施定位代码按校准第 6 条降级）。

## 维度复核结论（逐条判定）

| 编号 | 判定 | 理由 |
|---|---|---|
| C3-001 | **已保留 P1** | 锁缺口为代码事实：`ReaderChannel.requestSplits`（:260）与 `registerReader→addReader`（:186）锁外调用 enumerator，`snapshotState`（:297）持 `enumeratorLock`，锁对两条高频路径形同虚设；`enumerator` 字段确非 volatile（:61）。跨线程调用方逐一实证：FileSourceReader:417 / MessageSourceReader:137 / BatchLoaderSourceReader:140 / DebeziumCdcSourceReader:216 均在 reader 任务线程调用，行号与发现完全一致；checkpoint 路径 `buildEpochManifest→snapshotSourceEnumerators→coord.snapshotState` 确认（非增量 ：650 monitor 内 / 增量 ：757 persist 线程）。`FileSplitEnumerator.handleSplitRequest` 的 assign 序列（:207-215）确为 check-then-act 复合序列，CHM 集合不使其原子。触发时序具体（并行度 ≥2 稳态空闲请求即达），维持 P1。 |
| C3-002 | **已保留 P2** | 机制为代码事实：`loadNanos` 仅在 `refreshOne` 完成时续期（:245），队列中排队任务不续期；`scanAndDispatch`（:222-224）每轮对全部过期键重投；`newFixedThreadPool`（:193）队列无界无拒绝策略。触发时序具体可量化（全部刷新线程阻塞慢 delegate → 每轮 maxEntries 次重投线性累积 + 恢复期倾泻）。不属"有界声明"豁免——队列无任何上界声明。plan 2338 closure review F-2 只核了"并发上限=池大小"，未覆盖队列累积与 in-flight 去重，非已知。 |
| C3-003 | **已保留 P2** | 三处 `execute` 全部在持 monitor 栈上实证确认（success 回调经 `executePersistAsync:731-733` / `completePersistSynchronously`，failure 回调 ：1061，abort 于 synchronized 方法 ：1139）；池定义 1 线程 + 队列 1000 + CallerRuns（:1086-1095）确认。CallerRuns 命中时任务体在持锁线程内联执行 sink commit I/O，机制确定。双重 commit 窗口实证：`finishCommit` 的 containsKey→commit→remove 三步跨锁非原子（:189-200），两线程并发跑 `retryFailedCommits` 可对同 epoch 双提交。饱和前提已量化（~17 分钟），发现自评"很可能"恰当。r4/R5 CC-10 裁定与指标（commitQueueDepth/commitCallerRuns）仅覆盖 TaskManager 侧且论证了"调用方非 monitor"；本侧 fanout executor 为 plan 2326（R6 CC-01 修复）新增，无该论证、无观测——非已知，修复引入的新缺口。 |
| C3-004 | **降级 P2→P3** | 机制为代码事实且非理论（:328-333 消费异常后游标照样前进，确定性复现），发现对 F2"半个成功"家族的定性准确。但复核按校准第 6 条降级：类 javadoc（:40-44、:66-70）明文声明测试基础设施定位，生产面 `SysDaoMessageService` 为 WAITING/CLAIMED 行模型带重扫语义（实证 ：112-113、:235-245）；两条下游均有既有自愈兜底（TM 60s liveness 恢复、checkpoint 超时 abort），实际后果限于 e2e 时延噪声与归因污染。测试面内低危，P3。javadoc 交付语义声明与实现不一致的修正建议仍成立。 |
| C3-005 | **已保留 P3** | 伪异步为代码事实（:188-192 同步 `send` + pre-completed future，异常走调用方栈绕过 future 通道）；对照实现 `DataPlaneMessageServiceAdapter.sendAsync`（transport/:74-77）确为真委托，不对称成立。控制面低频、失败仍可见（fanout executor catch+log），P3 恰当。SPI 签名契约违反属实，不属"同步 send 即合理"豁免。 |
| C3-006 | **已保留 P3** | 单调增长为代码事实：`delivered.add`（:180）在生产 drain 路径无条件追加，无上界无淘汰无测试开关守卫；`close()`（:134-136）不 drain pending 一并确认。`getDelivered()` 全库仅测试消费（TestAlertChannels），但累积发生在生产投递路径，不因消费面在测试而豁免（无"有界声明存在"）。量级评估低（告警事件小、增速受事件率限），P3 恰当。r5 CC-06（drainLoop 死亡）为同类不同缺陷，已由 R5 per-event 包容修复（:139-156 实证在位），与本条无重叠。 |
| C3-007 | **已保留 P3** | 数据竞争为代码事实（:88 非 volatile long，:141 任务线程读 / :244-245 工作线程写），初审已按纪律自标"理论风险，未具体化"：最坏后果为一次多余同步重查（:144 自愈幂等），nanoTime 高位恒 0 使 long 撕裂实际不可达，无正确性后果。P3（低于复核指令 P2 下限，更严格）作为字段契约与注释对齐的卫生项保留，价值在防未来叠加正确性逻辑。 |
| C3-008 | **已保留 P3** | 池大小 2 硬编码（:96）+ 每订阅一个 `scheduleWithFixedDelay` 任务（:293）+ consumer 内联执行（:329）全部实证；`scheduleWithFixedDelay` 保证无堆积无丢失，仅时延让渡。类 javadoc 明示测试基础设施定位，按校准第 6 条 P3 恰当，与 C3-004 降级口径一致。 |
| C3-009 | **已保留 P2** | 全链代码事实实证：4 线程 http worker（:122-124）→ `TaskManager.keyedStateSnapshot` 直接解引用 invokable 无 mailbox 交接（:149-158）→ `MemoryKeyedStateBackend.states` 裸 HashMap（:109）→ `MemoryStateSerDe` 两层无锁迭代（:141-147、:330）← 任务线程经 `MemoryValueState.storage` 裸 HashMap（:23）逐条写。handler 宽 catch 仅 `StreamException`，CME 以未捕获形态逸出成 500。触发时序具体（端点设计用途即被监控轮询 + keyed 热作业稳态写入）。后果限查询面（500 / 撕裂快照），不损任务状态与持久化资产，P2 恰当非 P1。r6 KR-07 为 checkpoint 裸键覆盖（不同缺陷），WI14 端点此前仅结构核查无线程归属审查——非已知。 |

**现状归属总述**：9 条全部为**新发现**。历史审计交叉核对无一命中本维度缺陷签名：r6 CC-04（`SourceCoordinatorRegistry` 泄漏/串台，与 C3-001 同类不同缺陷）、r6 KR-07（checkpoint keyed 快照裸键覆盖，与 C3-009 同子系统不同缺陷）、r5 CC-06（drainLoop 死亡，已修复，与 C3-006 同类不同缺陷）、r4/R5 CC-10 + plan 2436（TaskManager 侧 commit 池 CallerRuns 裁定，与 C3-003 的 CheckpointCoordinator 侧相对）、plan 2338 review F-2（CachingTableLookup 并发上限，与 C3-002 不同面向）。另：第 1 轮"非发现但登记"的 `SourceCoordinatorRegistry` v1 limitation 即 r6 CC-04 已立案项（P2，保留中），复核确认其归属，不在本维度重复立项。

## 最终保留项

| 编号 | 最终严重程度 | 文件路径 | 一句话摘要 | 现状标注 |
|---|---|---|---|---|
| C3-001 | P1 | nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/source/coordinator/LocalSourceCoordinator.java | SplitEnumerator 被 reader 任务线程锁外并发调用（requestSplits/addReader 绕开 enumeratorLock），checkpoint 快照可撕裂、split 可双重分配 | 新发现 |
| C3-002 | P2 | nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/connector/lookup/CachingTableLookup.java | 后台刷新无 in-flight 去重 + 无界队列：delegate 慢/挂死时重复刷新任务线性累积，恢复期重复查询风暴 | 新发现 |
| C3-003 | P2 | nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java | commit-fanout 的 CallerRuns 兜底在持 coordinator monitor 的线程上内联执行 sink commit I/O（三处 execute 全在锁内），并打破 finishCommit 去重的单线程串行假设 | 新发现 |
| C3-004 | P3（降级） | nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/launch/PollingJdbcMessageService.java | 消费回调异常后游标仍前进，失败消息永久跳过，声明的 at-least-once 被打破（测试基础设施定位，降级） | 新发现 |
| C3-005 | P3 | nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/launch/PollingJdbcMessageService.java | sendAsync 伪异步：JDBC INSERT 同步执行于调用方线程，异常绕过 CompletionStage 异常通道 | 新发现 |
| C3-006 | P3 | nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/alert/WebhookAlertChannel.java | delivered 列表在生产投递路径无界累积（声明为测试可观测但无守卫），close 亦不 drain pending | 新发现 |
| C3-007 | P3 | nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/connector/lookup/CachingTableLookup.java | Entry.loadNanos 非 volatile 跨线程读写的数据竞争（理论风险，未具体化，后果良性自愈） | 新发现 |
| C3-008 | P3 | nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/launch/PollingJdbcMessageService.java | 2 线程调度池承载全部订阅 poll：单个慢消费阻塞其他 topic 投递节奏（仅时延，无丢失；测试基础设施定位） | 新发现 |
| C3-009 | P2 | nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/ops/StreamOpsHttpServer.java + TaskManager.java + core/state/backend/memory/* | queryable-state REST 读面对运行中任务的 keyed 状态做无同步跨线程快照（裸 HashMap 双层迭代 vs 任务线程写入）：CME/撕裂快照/漏读 | 新发现 |

**复核计数**：已保留 9 / 降级 1（C3-004 P2→P3）/ 驳回 0；最终 P0=0、P1=1（C3-001）、P2=3（C3-002/003/009）、P3=5（C3-004~008）。现状标注：新发现 9，已知未修复 0，已修复 0。
