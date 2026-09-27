# nop-stream 代码质量与性能深度审计

> Status: resolved
> Date: 2026-09-28
> Scope: nop-stream（core / runtime / cep / flow / rocksdb / connector*）
> Conclusion: 共记录 93 项发现（P0 缺陷 7、P1 性能 10、P1 质量 8）。高置信、高收益项进入修复计划 `ai-dev/plans/nop-stream-quality-perf/01-nop-stream-quality-perf-remediation.md`；其余按归属记录为 watch-only / successor backlog。A12 为计划对抗性审查阶段新发现并核实的活缺陷，已纳入计划 Phase 2。

## Context

- 用户要求对 nop-stream 做深度代码质量审计（可读性、长期可维护性、性能），并以 JMH/JFR 驱动性能优化直至边际收益 <2%。
- 审计方法：5 个并行只读子代理分别覆盖 (1) core 执行热路径、(2) runtime 协调/窗口/checkpoint、(3) CEP、(4) 状态后端+序列化、(5) flow builder+connectors；对最关键发现逐一与 live 代码抽查核对（标注 ✅ 的为已人工验证）。
- 基线：`./mvnw test -pl nop-stream-core,nop-stream-flow,nop-stream-runtime,nop-stream-cep,nop-stream-rocksdb` 全绿（2026-09-28，`_tmp/nop-stream-audit-baseline.log`，EXIT=0）。
- 模块规模：core 46K 行 / runtime 28K / cep 12K / flow 8K / rocksdb 4K；无任何现存 JMH 基准。

## 审计发现清单

严重度：`P0`=已验证的正确性缺陷（数据丢失/永久卡死/静默丢数据），`P1`=高影响性能或结构性问题，`P2`=中低影响。`✅`=已与 live 代码人工核对。

### A. 正确性缺陷（runtime / coordinator / checkpoint）

| # | 位置 | 问题 | 级别 | 验证 |
|---|------|------|------|------|
| A1 | `JobCoordinator.java:1410-1528` | `recoveryPending` 仅在 fan-out 段的 finally 清除；`rotateFencingEpochCoreLocked`（DB 写）或 `prepareAssignmentsLocked` 抛异常时提前退出，标志永久置位 → 后续所有 requestRecovery CAS 失败、所有 checkpoint 触发被抑制，作业永久卡死 | P0 | ✅ |
| A2 | `CheckpointCoordinator.java:256,1190` | abort 的 epoch 在 `failedCommitParticipants` 为空时不在 `checkpointSuccessMap` 中移除 → 长期运行作业 map 无界增长 | P0 | 子代理核实 |
| A3 | `JobCoordinator.java:1968-1979` | 终态 savepoint 失败/超时后仍上报 JOB_FINISHED + `health.onFinished`（静默数据丢失语义） | P0 | 子代理核实 |
| A4 | `JobCoordinator.java:602-634` | `stop()` 仅 gate 在 `running`；standby 实例可 shutdown 共享 CheckpointCoordinator，杀掉 active 的 persist/timeout executor | P0 | 子代理核实 |
| A5 | `ResultPartition.java:263-293` + `InMemoryMaterializationPoint.java:41,61-72` | 背压+materialization 边队列满时记录静默移出 live 路径（仅存 bypass store），无日志/计数；bypass store 为无界 synchronized ArrayList 且不计 BufferPool 限额 → 无全局内存上界 | P0 | 子代理核实 |
| A6 | `SupervisionLoop.java:486-488` | region 重启时 live map 缺 key 仅 WARN 跳过；被取消任务计为终态，可"成功"退出且 region 有洞 | P0 | 子代理核实 |
| A7 | `CheckpointBarrierTracker.java:339-347` | `getCurrentCheckpointId()` 无锁迭代 `LinkedHashMap inFlight`，与 synchronized 写并发可 CME/不一致 | P1 | ✅ |
| A8 | `StreamTaskInvokable.java:866-871,918-921` | `invokeSink`/`invokeSelfContained` 的 finally 顺序 close，无 suppress；`operatorChain.close()` 抛出时跳过 `closeInputGate()`（泄漏 remote channel 订阅）并吞掉 inputError；`invokeMiddle` 已有 `closeChainAndGate` 未复用 | P1 | 子代理核实 |
| A9 | `JobCoordinator.java:940-944` | 日志 bug：`state={} cause={}` 传入两个 `getTerminalState()`，cause 被丢弃 | P2 | 子代理核实 |
| A10 | `RunningTask.java:130-136` | `completedTasks` 超限随机驱逐一条（非 LRU），刚完成任务结果可能被逐出 | P2 | 子代理核实 |
| A11 | 锁序 | recoveryLock→CC monitor→RPC→commitExecutor→用户 JDBC 链式嵌套；ACK 线程可被慢 sink commit 饿死；无文档化锁序 | P2 | 子代理核实 |
| A12 | `WindowOperator.java:1397-1403` | `addWindowElement` 的 `current instanceof List` 分支只 `list.add(value)` 无 `setWindowContents` 写回：MapState 回退 + evictor + RocksDB 等拷贝语义后端时第 2 条起记录被静默丢弃（内存后端 `MemoryMapState.get` 返回活引用侥幸正确）；plan 对抗性审查（2026-09-28）发现并经人工核实 | P0 | ✅ |

### B. 正确性缺陷（state / serde / connectors）

| # | 位置 | 问题 | 级别 | 验证 |
|---|------|------|------|------|
| B1 | `RocksDBKeyedStateBackend.java:208-212` | `listColumnFamilies` 失败被静默吞掉（无日志）→ 既有 CF 被当作不存在重建，旧状态静默不可见 | P0 | 子代理核实 |
| B2 | `RocksDBSnapshotSerDe.java:343-344` + `MemoryStateSerDe.java:281` | restore 先 `clearAllStates` 再校验快照内容 → 损坏快照把后端清空且无回滚 | P1 | 子代理核实 |
| B3 | `RocksDBInternalAggregatingState.java:80` | `getAccumulator()` 用 `descriptor.getValueType()` 反序列化，而 `get()` 用 `storageValueType` → Object 型 descriptor 下同一状态两个访问器返回不同运行时类型 | P1 | 子代理核实 |
| B4 | `JdbcTwoPhaseCommitSink.java:301-307` | 幂等 re-commit 路径提前 return，未清 `pendingCommits` 条目 → stale 条目永久滞留（file sink 两个分支都清了） | P1 | 子代理核实 |
| B5 | `FileTwoPhaseCommitSink.java:488-494,455-465` | manifest 以裸 `key=value` 写出、以 `Properties.load` 读回（`\` 是转义符）→ Windows 路径静默损坏 | P1 | 子代理核实 |
| B6 | `FileSplitEnumeratorStateSerializer`（FileSource.java:170-176） | 写出 splitById 路径不校验保留字符（`\|`、`\n`），restore 时才抛 Malformed → checkpoint 成功但永远无法恢复 | P1 | 子代理核实 |
| B7 | `MemoryStateSerDe/RocksDBSnapshotSerDe` 类加载 | `Class.forName` 无 TCCL fallback；`ClassNameValidator` 白名单仅 `io.nop.*`+JDK，无 escape hatch（对比 `StreamDeserializationFilter` 有系统属性）→ 用户域值类快照无法在任何地方恢复 | P1 | 子代理核实 |
| B8 | `RocksDBKeyedStateBackend.java:194-225` | `RocksDB.open` 抛出时 dbOptions/cfOptions JNI 句柄不关闭 | P2 | 子代理核实 |
| B9 | `CepOperator.java:459-460,482-483` | `deleteProcessingTimeTimer`/`forEachProcessingTimeTimer` 为空实现（静默 no-op），processing-time 定时器无法取消 | P2 | 子代理核实 |
| B10 | `SharedBufferAccessor.java:247,266-276` | match 物化丢事件 NPE 被包装丢失 eventId；lockNode 遇 null 节点静默跳过（引用计数少加锁） | P2 | 子代理核实 |
| B11 | `Lockable.java:60-70,101-115` | 已零计数 release 先 set(0) 再抛；equals/hashCode 含可变 refcount，缓存中相等性不稳定 | P2 | 子代理核实 |
| B12 | `StreamExecutionEnvironment.java:699-701` | 用户显式禁用 checkpoint 时 `buildJobGraph` 静默强制开启 | P2 | 子代理核实 |
| B13 | 错误码不一致 | `JdbcTwoPhaseCommitSink` 用 NULL_ARG 表达范围违规；`FileTwoPhaseCommitSinkConnectorFactory` 泄漏 JDK `UnsupportedCharsetException` | P2 | 子代理核实 |

### C. 性能（core 执行热路径）

| # | 位置 | 问题 | 级别 | 验证 |
|---|------|------|------|------|
| C1 | `InputGate.java:710-721` | 多通道轮询扫描中每个空通道阻塞 `channel.read(50ms)`（`CHANNEL_POLL_TIMEOUT_MS=50`）→ 2 输入且一路饱和一路空闲时每条记录最坏延迟 ~50ms；idle 阈值因饱和通道重置永不生效 | P1 | ✅（常量+调用确认） |
| C2 | `BufferPool.java:56` + `ResultPartition.java:265-335` | 全作业共享的**公平** Semaphore 逐记录 acquire/release（最慢 JDK 原语）+ `LinkedBlockingQueue` 双锁 → 每记录每边 4 次同步操作，单一全局争用点 | P1 | ✅ |
| C3 | `InputGate.java:804-818,617,637` + `StreamTaskInvokable.java:962-990` | 每记录 1-2 个 `Optional<StreamElement>` 分配 + 拆箱 | P1 | 子代理核实 |
| C4 | `StreamTaskInvokable.java:950,988,1005-1009,1106-1113` | 每记录 4 次时钟读（markActivity/markProgress/CoreMetrics nanoTime 对 + emitStart/emitTime），NOOP 度量句柄也不短路 | P1 | 子代理核实 |
| C5 | `StreamTaskInvokable.java:1112` | `writer.emit(record.copy(...))` 每记录每跳分配新 StreamRecord | P2 | 子代理核实 |
| C6 | `InputGate.java:828-849,1111-1119,1169-1191` | 对齐期每记录 CHM 迭代 + 时钟读；watermark 两次 O(n) 通道扫描（可 running-min） | P2 | 子代理核实 |
| C7 | `RebalancePartitionRouter.java:31` | 单线程上下文中用 `AtomicInteger.getAndIncrement()`，可退化 plain int | P2 | 子代理核实 |
| C8 | `ResultPartition.java:53,176-215` | LBQ 可换 SPSC/MPSC 无锁队列（本地路径单生产单消费） | P2 | 子代理核实 |

### D. 性能（runtime / windowing / checkpoint）

| # | 位置 | 问题 | 级别 | 验证 |
|---|------|------|------|------|
| D1 | `WindowOperator.java:1360-1421` | MapState 回退路径每记录 `get(WINDOW_VALUE_KEY)`+全量 `put` → List 累加器 O(n) 每记录、O(n²) 每窗口 | P1 | ✅ |
| D2 | `WindowOperator.java:1434-1447` | 有 evictor 时每记录 elementTimestamps 全量 get+add+put（装箱 Long + O(n²) 每窗口） | P1 | ✅ |
| D3 | `CheckpointSerDe.java:351-365` | 校验和 = 对全状态树 2 次额外完整 JSON 序列化 + 1 次 parse + normalizeNumbersDeep；sync 模式下 inline 于 CC monitor 的 ACK 线程 | P1 | ✅ |
| D4 | `CheckpointCoordinator.java:335,406,437,771-832` | 单一 `synchronized(this)` 粗锁；persist 成功回调在锁内做阻塞 RPC fan-out；abort 路径同锁内 cancelTask fan-out → ACK 吞吐塌缩到 RPC 延迟 | P1 | 子代理核实 |
| D5 | `WindowOperator.java:784,1271-1301` | 每条记录都 `registerCleanupTimer`（TimerEntry 分配+HashSet+TreeMap），可按 pane 去重 | P2 | 子代理核实 |
| D6 | `WindowOperator.java:671` + `MergingWindowSet.java:88,105-115` | 会话窗口 merging 路径每记录新建 `MergingWindowSet` 并全量重读/重写 state list | P2 | 子代理核实 |
| D7 | `JobCoordinator.java:1577-1592` | fencing 轮转在 `recoveryLock` 内做 DB 写 + 逐 TM RPC（违反自身"锁内无阻塞 IO"契约） | P2 | 子代理核实 |
| D8 | `RetentionCleaner.java:171` | 清理旧 checkpoint 时全量反序列化所有保留 payload 只为取 id | P2 | 子代理核实 |
| D9 | `JdbcCheckpointStorage.java:123,152,214,263,352,541,595,647` | 每次读/删前都跑 `tableExists()` catalog 查询 | P2 | 子代理核实 |
| D10 | `WindowOperator.java:927-933` | `removeTriggerAccumulators` 每次清理全键空间 removeIf 扫描 | P2 | 子代理核实 |
| D11 | `RemoteResultPartition.java:159-170` | 每记录 valueType 查找 + synchronized sendLock + 时钟读 | P2 | 子代理核实 |

### E. 性能（CEP）

| # | 位置 | 问题 | 级别 | 验证 |
|---|------|------|------|------|
| E1 | `SharedBuffer.java:278-290` | `advanceTime` 全 eventsCount 键迭代 + 全 cache keySet removeIf；每个 drained bucket 调一次 → watermark 排空 N 桶 = N 次全扫描 | P1 | ✅ |
| E2 | `NFA.java:411-414,429,718-733` | 每事件 2 个 PriorityQueue + 每计算状态 1 个 ArrayList/ConditionContext + 决策图集合，~5P 集合分配/事件 | P1 | 子代理核实 |
| E3 | `NFA.java:976-995 vs 926-952` | PROCEED 边用户条件被评估两次（createDecisionGraph + findFinalStateAfterProceed） | P1 | 子代理核实 |
| E4 | `SharedBufferAccessor.java:265-276` + `SharedBuffer.java:407-437` | lock/release/put 每操作 2-4 次 cache put + backing MapState put 写穿 → 持久后端下每事件每状态多次磁盘写 | P1 | 子代理核实 |
| E5 | `SharedBuffer.java:318-331` | registerEvent 2 次状态操作 + 冲突循环可触发额外 state get | P2 | 子代理核实 |
| E6 | `SharedBufferAccessor.java:186-226` | extractPatterns 每分支整路径 Stack 拷贝 | P2 | 子代理核实 |
| E7 | `CepOperator.java:1018-1021,1066,1070` | 每桶 sort 建 Stream 机制；drain 时全量 dueTimestamps 拷贝；每事件新 SharedBufferAccessor；每缓存操作 ScopedId 分配 | P2 | 子代理核实 |

### F. 性能（状态后端）

| # | 位置 | 问题 | 级别 | 验证 |
|---|------|------|------|------|
| F1 | `RocksDBInternalListState.java:52`、`RocksDBInternalAggregatingState.java:59`、`RocksDBInternalAppendingState.java:97` | 内部状态绕过 `(key,namespace)→bytes` 缓存，直接 `buildStorageKey` → 每访问重跑 JsonTool.serialize×2+UTF-8 编码 | P1 | 子代理核实 |
| F2 | `MemoryInternalAppendingState.java:70`、`MemoryInternalListState.java:50` | 每访问 `new TypedNamespaceAndKey(...)`，而孪生 `MemoryInternalAggregatingState` 用了 backend 缓存（注释还声称"其他 flavor 也用了"） | P1 | ✅ |
| F3 | `RocksDBListState.java:117-144` | `add()` 全列表读-改-写（get+JSON 全解析+全序列化+put）→ 每追加元素 O(n) JSON | P1 | 子代理核实 |
| F4 | `RocksDBReducingState.java:87-113,130` | 每 add 做 GET+反序列化+反射建 accumulator+序列化+PUT；Constructor 未记忆化 | P2 | 子代理核实 |
| F5 | `RocksDBSnapshotSerDe.java:299-320,437-446,589-604` | 快照/恢复双向每值 2 次 JSON round-trip | P2 | 子代理核实 |
| F6 | `RocksDBKeyedStateBackend.java:642-663` 等 | 前缀删除/逐行 put 无 WriteBatch/DeleteRange | P2 | 子代理核实 |
| F7 | `RocksDBMapState.java:244-261` | entries/keys/values/iterator 各自 collectMap 全扫描全解码，不懒加载 | P2 | 子代理核实 |
| F8 | `MemoryArrayListState.java:39-64` | operator state `add()` 每元素全列表拷贝 → 累积 O(n²) 分配 | P2 | 子代理核实 |
| F9 | schema 指纹 | 每次 `getState()` 命中都算双 SHA-256（descriptor 未变可短路） | P2 | 子代理核实 |

### G. 可读性 / 可维护性

| # | 位置 | 问题 | 级别 | 验证 |
|---|------|------|------|------|
| G1 | `JobCoordinator.java`（2356 行） | god class：HA 选举/fencing、失败检测+双重启预算、checkpoint 触发、终止四模式、事件总线、~20 对 setter；建议拆 HaLeadershipController/FailureDetector/TerminationService | P1 | 子代理核实 |
| G2 | `InputGate.java`（1289 行） | 五职责合一（通道扫描/对齐状态机/watermark 阀/未对齐捕获/空闲策略）；有清晰提取缝 `BarrierAlignmentTracker`/`WatermarkValve` | P1 | 子代理核实 |
| G3 | `CepOperator.java`（1342 行） | 六职责合一；~80 行 `*ForTesting` 访问器在 main sources | P1 | 子代理核实 |
| G4 | `StreamTaskInvokable.java:246-333` | `wireOperators` 两个重载逐字重复 ~35 行内部链组装 | P2 | 子代理核实 |
| G5 | `StreamTaskInvokable.java:696-925` | invokeSource/Middle/Sink/SelfContained 四份同构脚手架 | P2 | 子代理核实 |
| G6 | `WindowOperator.java` | appending/list/MapState 三分支 switch 骨架在 6 处近重复（~200 行）；`open` 149 行、`mergeWindowContents` 144 行 | P2 | 子代理核实 |
| G7 | `WindowOperator.java:1911-1962` | `protected static class Timer` 死代码（零引用） | P2 | ✅ |
| G8 | `StreamModelDslBuilder.java:796-805` | `resolveFunction` 死代码且与 `resolveFunctionOrXpl` 错误锚定不一致；`buildSource`/`buildSink` 重复 bean-vs-xpl 决策树；`applyCheckpointConfig` 12 个手写默认值守卫 | P2 | ✅（死代码确认） |
| G9 | `StreamTaskInvokable.java:567-583` | javadoc 挂错方法（closeOutputWriters 的说明挂在 closeInputGate 上） | P2 | 子代理核实 |
| G10 | `GraphModelCheckpointExecutor.java:115-406` | executeWithCheckpoint×3/triggerSavepoint/executeWithSavepoint 重复 10 步 prologue（~250 行） | P2 | 子代理核实 |
| G11 | teardown 模式 | first-error+addSuppressed+typed rethrow 在 ≥5 处重复，可提取 `closeAll` | P2 | 子代理核实 |
| G12 | `MemoryStateSerDe` vs `RocksDBSnapshotSerDe` | serde 骨架近逐字重复（resolveTypeName/loadClass/resolveAggregateFunction/stateType switch） | P2 | 子代理核实 |
| G13 | `SubtaskTask.java:120-123` | `while(...){...;break;}` 单次迭代循环伪装成重试 | P2 | 子代理核实 |
| G14 | namespace 双轨 | internal states 各自持有 currentNamespace 与 backend 级并行，设错一个即静默错域 | P2 | 子代理核实 |
| G15 | `StreamModelDslBuilder.java:691-833` | public 面过宽（应为包私）；`build()` 硬编码 createTestEnvironment | P2 | 子代理核实 |
| G16 | `AdvancedTransforms.java:242-263` | 硬编码魔法字符串窗口目录（隐藏 1s/5s 尺寸） | P2 | 子代理核实 |
| G17 | `FileSplit.java:108-117` | 嵌套 Cursor 类零引用死代码 | P2 | 子代理核实 |
| G18 | `StreamTaskInvokable.java:1073-1226` | RecordWriterOutput 内部类 ~150 行可提升为独立文件；GraphExecutionPlan.createSubtasks 16 参数 | P2 | 子代理核实 |

### H. 连接器 / flow 杂项

- `FileTwoPhaseCommitSink.java:594-600` `deleteIfExistsQuiet` 吞 IOException 无日志；`DebeziumCdcSourceFunction.java:189` 恒真死条件；`MessageSourceFunction.java:184-190` 失败路径不自取消。
- `StreamConfValidator` 对 factory 双重解析；`FileSourceReader.readNextLine` 逐字节读 + 每行新 BAOS。
- BeanFunctionResolver 三实现重复 resolve/type-check/throw 三元组。
- SPI 总体判定：一致、有类型化参数描述与能力词汇，健康。

## 改进建议与归属

- **本计划修复**（高置信+高收益，见 plan）：A1、A2、A7、A8、A9、A12；B1、B3、B4、B5、B6；C1、C2、C4（NOOP 短路）；D1、D2、D3（带版本 Decision）；E1、E3；F1、F2、F3；G7、G8（死代码删除部分）、G9、G11、G13、G4。
- **JFR 第二轮候选**：C3、C5、C6、C7、D5、E2、E4、E7、F4、F8（以 JFR 火焰图证据决定是否动，收敛判据 <2%）。
- **Successor backlog（结构性大改，需独立 plan）**：G1 JobCoordinator 拆分、G2 InputGate 拆分、G3 CepOperator 拆分、A11 锁序重构、D4 CC 锁内 RPC 外移、G10/G12 serde/执行会话合并。
- **watch-only（已移交 successor ownership）**：A3/A4/A5/A6 行为变更类修复需要与 owner 确认语义后单独立项（本计划不动用户可见行为契约）；B2 restore 原子性、B7 类加载白名单涉及兼容性契约，同理。详见计划 Deferred 节的 `moved to explicit successor ownership` 裁定。

## 与先行计划裁定的交叉核对（2026-09-28 执行期补充）

本审计初稿遗漏了 `ai-dev/plans/360/2277/2278/2279`（2026-09-26/27 已收口）及其裁定书 `ai-dev/audits/evidence/nop-stream-perf-{360,2279}/convergence*.md`。经核对，本清单多个发现在先行计划中已有**实测裁定**，归属修正如下（未列出的 A/B/G 组缺陷与可读性项经 HEAD 核实仍未处理，归入计划 Phase 2/4）：

| 本清单项 | 先行裁定 | 修正后归属 |
|---|---|---|
| C1 InputGate 50ms 轮询（吞吐论断） | **被实证驳斥**：2279 InputGateReadLoopBench 证明通道 poll 信号驱动，饱和流 50ms 只是超时上限、吞吐零成本（117ns/op）；360 R2 亦裁定"伤延迟非吞吐" | 关闭（吞吐口径）；空闲唤醒延迟小项受 250ms/150ms 契约约束，维持 360 不实施裁定 |
| C2 BufferPool 公平信号量 | 2279 BufferPoolPermitBench 量化：畅通边 157ns <2%；批量记账需许可守恒/公平 FIFO/captureInFlightData 契约重设计（公平 FIFO 为已裁定契约） | Deferred（2279 归属，successor 条件已登记） |
| C4 每记录 4 次时钟读 | 2279 F5 watch-only（G52 liveness 语义耦合，降频需重设计判定周期） | watch-only（2279 归属） |
| D1 MapState 回退路径 RMW | 2279 F3：src/main 唯一构造点全路径传非 null descriptor，生产不可达 | 取消优化；A12 缺陷本身仍修（public 构造器路径可达），严重度降为 public-API 潜在缺陷 |
| D2 evictor 时间戳 RocksDB O(n²) | 2279 F2 实测 128µs/op（33×），Deferred 需列表格式设计 | Deferred（2279 归属） |
| D3 CheckpointSerDe 校验和多轮序列化 | 2279 F1 量化（读侧 78% 份额=canonical 契约本体），Deferred 需格式 v2 | Deferred（2279 归属） |
| E1 SharedBuffer.advanceTime 全扫描 | 2279 Q1/Q1b 台账重构后终态 JFR（jfr-final-cep64-r2）无此热点帧 | 关闭（被 2279 重构覆盖） |
| E2 NFA 分配池化 | 360 watch-only（分散无单点 <2%~5%） | watch-only（360 归属） |
| E4-E7 SharedBuffer 写穿/ScopedId 家族 | 360：key-scoped 缓存已实施（-41.3%）、LocalCache 替换 Deferred、ScopedId hashCode 已实施（-3.6%）、equals 为固有成本 | 按 360 归属 |
| F3 RocksDBListState O(n) 追加 | 360 实测 25µs@100/112µs@1000，Deferred 需 merge operator/列表格式 | Deferred（360 归属） |
| F4-F9 状态后端杂项 | 360 各轮实测（RocksDB 前缀缓存、accumulator 前向缓存、TypedNamespaceAndKey 复用均已实施保留） | 多数已被 360 修复；internal-state 路径遗漏即本清单 F1/F2（未裁定，进计划 Phase 3） |

**结论修正**：93 项发现中，除上表已裁定项外，真正未被处理且进入计划的是：Phase 2 的 11 项缺陷（含 A12）+ Phase 3 的 3 个性能候选（E3/F1/F2）+ Phase 4 的 G 组可读性残留。

## Conclusion

- 审计结论已收敛：93 项发现中 26 项进入 `ai-dev/plans/nop-stream-quality-perf/01-nop-stream-quality-perf-remediation.md` 执行；其余已按上两节归属（先行计划裁定 / successor backlog / watch-only），无悬空。计划草稿经独立子代理对抗性审查（2026-09-28，含想象性分析），9 个问题（0 Blocker、4 Major）已全部回写计划文本；执行期基线修正（先行裁定交叉核对）亦已回写。
- 被否决的方案：一次性修复全部 93 项——原因：结构性拆分（G1-G3）与行为变更类缺陷（A3-A6）各自需要独立的 owner 语义确认与设计，混入本计划会破坏"一个计划一个结果面"。
- 后续工作：`ai-dev/plans/nop-stream-quality-perf/01-nop-stream-quality-perf-remediation.md`

## References

- 基线日志：`_tmp/nop-stream-audit-baseline.log`（2026-09-28，5 模块全绿）
- 计划：`ai-dev/plans/nop-stream-quality-perf/01-nop-stream-quality-perf-remediation.md`
- 相关历史：`ai-dev/plans/nop-stream-independent-audit/`、`ai-dev/plans/nop-stream-productization/`
