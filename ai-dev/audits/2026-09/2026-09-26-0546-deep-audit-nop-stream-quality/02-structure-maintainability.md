# 02 结构与长期可维护性（nop-stream-runtime / core）

> 审计代理：独立只读子代理；复核修正 3 处（见文末）。路径 `RT=` nop-stream-runtime/src/main/java/io/nop/stream/runtime，`CORE=` nop-stream-core/.../core。

## 总体结论

代码纪律性强（core→runtime 依赖单向、getter unmodifiable、catch 普遍有日志、No-Silent-No-Op 执行良好）。主要长期风险不是烂代码而是**结构熵**：巨型类 + 全静态编排器 + 重复的 checkpoint 周期调度循环。

## 发现清单

**巨型类/结构**
- [P1] RT/execution/GraphModelCheckpointExecutor.java（2009 行）：全静态编排，~50 个 static 方法 11 组职责（执行入口/JobGraph 构建/savepoint/coordinator 工厂/任务 checkpoint 接线/barrier 调度/存储工厂/恢复引擎 ~700 行/rescale 迁移 ~350 行/校验/abort）。无法注入替身。切面：RestoreEngine（最大内聚块）、RescaleStateMigrator、CheckpointStorageFactory、JobTerminationHandler。
- [P1] RT/coordinator/JobCoordinator.java（2366 行）：10+ 职责组（HA 选举/fencing epoch/失败检测/恢复编排双预算/分配 fan-out/checkpoint 触发 ACK/生命周期/RPC 端点/可观测/远程部署配置态/30+ setter）。切面：FencingEpochManager、TaskLivenessTracker、RecoveryBudget、CoordinatorConfiguration。
- [P1] RT/operators/windowing/WindowOperator.java（2331 行）：11 职责组 + 9 内部类，字符串拼接键重造状态命名空间（`__window_value__`、`\u0000` 分隔、`trigger_` 前缀删除 :987-988）。切面：WindowStateAdapters 包（9 内部类 ~300 行样板）、PaneTracker、onTimer 合并（最高性价比）、WindowSideChannels（高风险：键格式已进 checkpoint 数据）。
- [P1] RT/checkpoint/CheckpointCoordinator.java（1852 行）：10 职责组，4 个线程池（scheduler/timeoutScheduler/persistExecutor/retentionExecutor :186-213）分属不相干职责。切面：CheckpointRetentionService（自带线程池+周期，零共享可变状态，风险极低）、IncrementalCheckpointSupport、CommitRetryQueue、CheckpointHistory。
- [P1] 三套周期 checkpoint 触发循环并存：CheckpointCoordinator:348-414（"checkpoint-coordinator-*"，scheduleAtFixedRate:369）/ JobCoordinator:1048-1080（"jc-periodic-*"，scheduleWithFixedDelay:1064）/ GraphModelCheckpointExecutor:792-821（"barrier-injector-*"，scheduleAtFixedRate:808）。【复核修正】CheckpointCoordinator.startCheckpointScheduler 生产不可达（仅 3 处测试调用），运行时最多两套活跃。收敛方向：删除死路径，测试改走生产路径。
- [P1] WindowOperator.java:810-889 vs 892-962：onEventTime/onProcessingTime ~80 行近逐字复制（仅 trigger 回调、isEventTime 极性、注释 3 处差异）。抽 onTimer(boolean isEventTime) 模板。
- [P2] StreamTaskInvokable.java：invokeSource≈invokeSelfContained(~50 行)、invokeMiddle≈invokeSink(~35 行) 双重复制 → runWithTerminalGuards 骨架。
- [P2] WindowOperator.java:1775-2076：9 个嵌套状态适配类（5 个 NamespaceAware* 雷同）→ 独立包 + AbstractWrappingState 模板。

**静默降级（有日志但主路径缺恢复动作）**
- [P2] RemoteResultPartition.java:189-193：有界流 EOS 发送失败仅 WARN（复核追加：close 在 182 已 stopHeartbeat，且生产 builder RemoteGraphExecutionPlanBuilder.java:176 传 channelTimeoutMs=0 禁用超时兜底 → 下游可永等）。
- [P2] TaskManager.java:760-780：sendCheckpointAck 失败仅 LOG.error 无重试，靠 checkpoint 超时 abort 兜底。
- [P2] WindowOperatorFactoryImpl.java:198-237：dummy serializer `isImmutableType()=true`、`copy()` 返回原引用、`createInstance()` 失败静默 null（复核修正：全仓无活跃调用点，属契约错误+潜在风险）。
- [P2] JobCoordinator.java:784-845（复核修正位置）：triggerCheckpoint 四路返回 null 作控制流；CheckpointCoordinator 已有带 TriggerRejectionReason 的正确 API（tryTriggerCheckpointWithReason:451），属 API 双轨风格问题。
- [P3] WindowOperatorFactoryImpl.java:98-106 推断 accumulator 类型失败静默保留 Object.class；GraphModelCheckpointExecutor.java:601-609 parseVertexIdForSourceEnumerator 失败返回 -1；OpsJobManager.java:169-171 恢复视图失败继续 fresh；JdbcLeaderElector.java:296-300 DDL 失败永久跳过建表；JobCoordinator.java:896-899 collectAck 返回值被丢弃。

**配置面/全局状态/可测试性**
- [P2] TaskManager.java:92 static volatile invokableWaitTimeoutMs 进程级全局可变配置。
- [P2] EngineMetrics.java:44-53 BY_JOB 缓存与 GAUGE_STATE_REFS 永不清理（多短作业 meter 泄漏）；TaskNodeMetrics.java:35,42-43,85-90 同病（复核：同名 meter 重复注册返回旧实例，TM 重启后 gauge 指向旧对象）。
- [P2] GraphModelCheckpointExecutor.java:1070-1076 存储目录配置 System.getProperty 深藏工具方法。
- [P2] 默认超时/阈值散落 7+ 类（JobCoordinator:90-97、InputGate:68-95、SupervisionLoop:139-157、TaskManager:84-92、CheckpointCoordinator:261-265）无集中配置表。
- [P3] StreamMetricsRegistries 静态 COMPOSITE 单例（现状可接受）。

**横切重复**
- [P2] 14 处 Executors.new* 手写命名 daemon ThreadFactory（TaskManager:164/169、JobCoordinator:422/1059、CheckpointCoordinator:308/362/978/1328、WebhookAlertChannel:109、GraphModelCheckpointExecutor:802、StreamOpsHttpServer:109、OpsJobManager:329、StreamMetricsReporter:76、TaskExecutor:116）→ 抽统一 ThreadFactory 工具。
- [P2] topic 命名三套：StreamTopicNaming.java:53（TOPIC_PREFIX="nop-stream"）、StreamControlRpcTopics.java:28-29、Embedded/RpcDistributedExecutor 内联 "nop-stream.control."+jobId → control/rpc 前缀并入统一常量。
- [P2] StreamTaskInvokable.java:335-848+960-1113 混装接线/四角色循环/mailbox/timer 驱动/输出适配器/liveness 六组字段簇 → RoleRunner 策略 + OutputAdapter 顶层化。

**依赖方向/API 边界（健康）**
- [P3] core 零反向 import runtime（核实通过）；JobCoordinator.java:758-760 getTaskAssignments unmodifiableMap 包可变 List 值。

## 巨型类职责清单与候选抽离切面（本审计核心产出）

### JobCoordinator（2366 行）
职责：①HA 选举 ②fencing epoch（EPOCH_SCALE 编解码/rotation/TM 推送）③失败检测 ④恢复编排+双预算 ⑤分配计划 fan-out ⑥checkpoint 触发与 ACK ⑦生命周期+4 种 terminate ⑧RPC 端点 ⑨可观测 ⑩远程部署配置态 ⑪30+ setter。
切面（按价值）：1. FencingEpochManager（②，:1579-1661+字段，风险：start() pre-set epoch 与 recoveryGen 同步分支 :521-536 语义精妙）；2. TaskLivenessTracker（③，纯数据结构，COMPLETED 移除语义 :941-957 随迁）；3. RecoveryBudget（④计数器，纯函数化易测，注意 :1488-1521 CAS 清位契约）；4. CoordinatorConfiguration（⑪，低风险搬运）。

### WindowOperator（2331 行）
职责：①元素累积（regular/merging 双路径）②触发器生命周期 ③merging 窗口集（~140 行）④Evictor ⑤Pane 追踪 ⑥迟到数据 ⑦cleanup 定时器 ⑧状态命名空间适配（9 内部类）⑨snapshot/restore（三旁路通道）⑩triggerAccumulators 字符串键旁路 ⑪发射与用户函数。
切面：1. WindowStateAdapters 包（⑧，风险最低）；2. PaneTracker（⑤，自带 G48 快照协议，须整体迁）；3. onTimer 合并（性价比最高）；4. WindowSideChannels（⑨⑩，高风险：`\u0000` 键格式已进 checkpoint 数据）。

### GraphModelCheckpointExecutor（2009 行，全静态）
切面：1. RestoreEngine（⑧ ~700 行，风险中：restoreTaskStatesFromSource 双 overload 有 pinned asymmetry）；2. RescaleStateMigrator（⑨，低风险）；3. CheckpointStorageFactory（⑦，顺带消除 System.getProperty）；4. JobTerminationHandler（③，CANCEL/DRAIN 不对称是设计契约）。

### CheckpointCoordinator（1852 行）
切面：1. CheckpointRetentionService（⑥ :1285-1473+retentionExecutor，风险极低）；2. IncrementalCheckpointSupport（④，风险中：段 1/2 monitor 内外分工 :548-558 是并发契约）；3. CommitRetryQueue（⑧，低风险）；4. CheckpointHistory（低价值后置）。

### TaskManager（1202 行）
切面：1. RunningTask 提顶层（⑨ ~300 行，几乎不依赖外部状态，风险极低）；2. FencingGuard（⑦，`activeEpoch != fencingEpoch → ERR_STREAM_FENCING_TOKEN_MISMATCH` 在 4 入口逐字重复 :676-681/700-707/739-744/796-802）；3. CompletedTaskLedger（:966-974 O(n) iterator 删一，非真 LRU）。

### InputGate（1155 行）
切面：1. BarrierAligner（②③ ~450 行独立状态机，风险中：pendingBarrierEmissions 与 retry 标号交互 :578-587，先加 characterization 测试）；2. WatermarkAggregator（④，纯数值算法，低风险）。

### StreamTaskInvokable（1114 行）
切面：1. RoleRunner 策略（②，风险中：AR-7×P1-5 守卫矩阵须逐分支等价）；2. RecordWriterOutput 顶层化（⑥，风险极低）；3. ProcessingTimeServices（④，低风险）。

## 静默吞异常位置清单（逐一核实）

主路径需处置：RemoteResultPartition EOS WARN、TaskManager ACK error、WindowOperatorFactoryImpl 双处、GraphModelCheckpointExecutor -1 哨兵、JdbcLeaderElector DDL、OpsJobManager fresh。可接受：JobCoordinator collectAck 返回值（内部有 WARN）、SourceCoordinatorRegistry close 忽略（有注释）、detectFailures/sendBarrier/periodic 触发等 LOG.error+下轮重试兜底（符合容错语义，不建议改）。
