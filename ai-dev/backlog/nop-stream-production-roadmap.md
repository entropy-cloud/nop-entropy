# nop-stream 生产级完善路线图

> Last updated: 2026-08-04
> Sources: `ai-dev/analysis/nop-stream/08-gap-analysis.md`（73 条显式缺口 ID [G1-G68, D69-D73] + 6 条已解决附录 [R1-R6]，primary）, `ai-dev/backlog/completion-roadmap.md`（Phase 0—5 战略框架）, `ai-dev/backlog/nop-stream-flink-comparison-roadmap.md`（前序路线图，Items 9—13 已完成）

## Purpose

把 nop-stream 从「设计成熟、核心修补完成」推进到「完整分布式生产级流处理引擎」。本路线图驱动 73 条源码级对比缺口的逐一关闭与 Phase 1—6 能力建设，对齐 `completion-roadmap.md` 的全部成功标准。

Does not contain implementation details. Each `planned` stage is owned by its execution plan.

## Work Items

> **This is the only dynamic state block. Update status only here.**
> The roadmap is a human-AI alignment artifact: humans set items and their order;
> AI takes the first `todo` item, drafts/executes plans, and writes the item back to `done` when closure audit passes.
>
> 编号延续前序路线图（Items 1—13 已完成）。分组标题（Phase X）为组织视图，无独立状态。

### Phase 0 — 正确性缺口补齐与文档收敛

- 14. Session window merge 修复（G1，P0）: `done`
- 15. Timer checkpoint/restore + timer service 统一（G2, G16，P0/P1）: `done`
- 16. Multi-input barrier alignment（G4, G7，P1）: `done`（G5/G34 deferred to Stage 39 — 跨 JVM RPC prerequisite，见 `checkpoint-design.md:911`）
- 17. Mailbox 执行模型（G22，P1）: `done`
- 18. 异步两阶段 snapshot pipeline（G30, G44，P2）: `done`（plan `2026-07-25-2200-1-async-snapshot-pipeline`，completed）
- 19. Checkpoint 并发与共享状态（G31, G33，P2）: `done`（plan `ai-dev/plans/nop-stream-production/2026-07-25-2300-1-checkpoint-concurrency.md`，completed — G31 收口：Coordinator 尊重 `maxConcurrentCheckpoints` + minPause(last-completed) 接线 + stale 警告/文档修正 + 共存 pending 经 coordinator 路径独立 ACK/complete/abort/timeout 由 focused test 覆盖；G33 裁定延后 Stage 31 — 内存后端无共享状态可消费，引入空壳抽象违反 plan 指南 #22/#24）
- 20. Partial/subtask 级恢复（G28, G29，P2）: done（G29 — plan `2026-07-25-2200-2-partial-subtask-recovery`，completed；G28 design-gated，需先起草 region/drain/reconnect 设计文档，见 plan Deferred）
- 21. Evictor/Pane/Watermark 集成（G46—G48，P2）: `done`
- 22. 文档合同对齐与 source-anchors 补全（D69—D73，Doc）: `done`
- 23. 代码清理与 P3 次要改进（G68, G62, G64，P3）: done（plan `2026-07-25-2200-3-code-cleanup-p3`，completed；G62 降级为 Decision-only）

### Phase 1 — 分布式运行时基础

- 24. DeploymentPlan subtask 分配 + 平台 discovery 接入（G50, G51，P2）: done（plan `ai-dev/plans/nop-stream-production/2026-07-26-0207-1-deployment-plan-discovery.md`，completed）
- 25. Per-task failure detection + execution state machine（G52, G54—G56, G58，P2）: done（plan `ai-dev/plans/nop-stream-production/2026-07-26-0207-2-per-task-failure-detection.md`，completed；G55 region scheduling 明确 Out-of-Scope，属 Stage 27/44）
- 26. Buffer pool 抽象（G53，P2）: done（plan `ai-dev/plans/nop-stream-production/2026-07-26-0207-3-buffer-pool.md`，completed）
- 27. Targeted failover（G57，P2）: `done`（plan `ai-dev/plans/nop-stream-production/2026-07-26-0433-2-targeted-failover.md`，裁定交付 **NO-GO** — all-pipelined→单 region + drain/reconnect 不可设计；设计文档 `ai-dev/design/nop-stream/failover-design.md`；G57/G28(续)/per-region-counter deferred → Stage 44，需 blocking edge + supervision loop 前置）
- 28. 分布式 RPC 接口扩容 + 进程内 backpressure（G23, G26，P1）: done（plan `ai-dev/plans/nop-stream-production/2026-07-26-0433-1-rpc-dispatcher-backpressure.md`，completed — 控制面 RPC 暴露 terminate/abort/status + G26 dispatcher 最小化 Decision + G27 backpressure 契约 + CREDIT_BASED/ACK_WINDOW 永久排除）

### Phase 2 — 状态后端生产化

- 29. SerializerFingerprint schema 兼容性体系（G12, G40, G41, G59，P1/P2/P3）: `done`（plan `ai-dev/plans/nop-stream-production/2026-07-26-1000-1-serializer-fingerprint-schema-compat.md`，completed — `SerializerFingerprint` + `StateSchemaResolver` + `getState()`-time fail-fast + per-state JSON `schemaChecksum` 嵌入 + `CheckpointSerDe` `formatVersion` envelope；G12/G40/G41/G59 ✅ Closed）
- 30. RocksDB 状态后端核心: `done`（plan `ai-dev/plans/nop-stream-production/2026-08-02-0955-1-rocksdb-state-backend.md`，completed — 独立 `nop-stream-rocksdb` 模块 + `RocksDBKeyedStateBackend` 实现 `IInternalStateBackend` + 全 8 stateType 列族 + snapshot 互换兼容 + Stage 29 schema fingerprint 复用；578 tests pass）
- 31. 增量 checkpoint（SST 共享）: `done`（plan `ai-dev/plans/nop-stream-production/2026-08-02-0955-2-incremental-checkpoint-sst-sharing.md`，completed — Stage 30 已 landing 解除 blocker；`SharedStateRegistry` 引用计数（`ConcurrentHashMap.compute` per-key 原子）+ `SharedStateHandle`/`IncrementalSnapshotResult`（@DataBean）+ `RocksDBIncrementalSnapshotStrategy`（`Checkpoint.createCheckpoint` 单参 API + SST SHA-256 内容寻址 + 非 SST per-checkpoint 复制 + `sst-name-map.txt`）+ `ISegmentStore`/`LocalFileSegmentStore` side-channel + `EpochManifest.segments` 激活（`codec=identity` fail-fast）+ coordinator 段2 构建 segments（registry register + storeSegment 物化）+ subsumption GC（零引用物理删除）+ `restoreSharedStateRegistry` restart 恢复 + orphan cleanup；增量/全量基准 ratio≈0.35（≈2.9× 加速）；G33 收口）
- 32. State TTL（G42, G43，P2）: `done`（plan `ai-dev/plans/nop-stream-production/2026-08-02-0955-3-state-ttl.md`，completed — `StateTtlConfig`/`StateTtlUpdateType`/`TtlCleanupStrategy` + intrusive `TtlContext` per-state sidecar（存储/值分离）+ Memory/RocksDB lazy eviction（双重清理）+ snapshot 过期排除 + restore 后 TTL 存活 + Memory sweep / RocksDB `cleanupExpiredEntries()` 后台清理；TTL 不影响 schemaChecksum；native RocksDB compaction filter 裁定延后——rocksdbjni 无纯 Java 回调，纯 Java sweep 语义等价；G42/G43 ✅ Closed）
- 33. 状态迁移接线: `done`（plan `ai-dev/plans/nop-stream-production/2026-08-02-0955-6-state-migration-wiring.md`，completed — `StateMigrationFunction` 接口 + `StateMigrationRegistry`（`StreamComponents` 实现）+ memory/rocksdb 双后端 `verifySchemaCompatibility` 触发接线（checksum 不匹配→查迁移函数→命中则全量读-写迁移、未命中 fail-fast）+ 8 种 keyed state 类型 `MigratableKeyedState` 实现 + Integer→Long demo E2E 全链路（`CheckpointSerDe`+`LocalFileCheckpointStorage`，memory+rocksdb）；schemaVersion 四分支因 schemaVersion≡1 无触发源延后（`out-of-scope improvement`）；Stage 29 deferred 收口；13 新测试 0 failures）

### Phase 3 — 弹性与重分布

- 34. Key-Group 模型（G37—G39，P2）: `done`（plan `ai-dev/plans/nop-stream-production/2026-08-02-0955-4-key-group-model.md`，completed — KeyGroup/KeyGroupRange/KeyGroupAssignment（分层稳定哈希：内置类型 hashCode + POJO Murmur3-over-JSON，G38）+ key→group 映射（G37）+ KeyGroupRange 集合操作（G39）+ job-global maxParallelism（默认 128，shardCount 语义迁移 + getShardCount @Deprecated 别名）+ group→subtask 连续区间映射函数；RocksDB 可排序 key-group 二进制前缀 layout v2（**Stage 30 deferred「Binary composite key encoding」收口**，增量旧 SST fail-fast）；memory+rocksdb keyed 聚合 E2E 一致；生产 rescale 接线属 Stage 35）
- 35. KeyGroupRange 恢复 + RocksDB key-group 感知 restore: `done`（plan `ai-dev/plans/nop-stream-production/2026-08-02-0955-5-keygroup-range-recovery.md`，completed — executor dispatch（`GraphModelCheckpointExecutor.restoreTaskStatesFromSource` 承重重构为区间路由）+ TaskEpochSnapshot KeyGroupRange 归属物化（CheckpointSerDe 持久化）+ 全量 JSON in-memory 过滤/增量 SST range scan 双路径 + scale-down 多源合并 + parallelism 4↔16 E2E dispatch；Stage 31 deferred「Key-group range SST reading」收口；`KeyGroupRangeRestoreFilter` + Memory/RocksDB `targetKeyGroupRange` + `RocksDBIncrementalRestore.restoreRangeInto` 真实 SST range scan）
- 36. ~~BroadcastState 类型（G36，P2）~~ → 推迟，需先更新 vision §七: `todo`
- 37. StateShard→KeyGroup 迁移 + vision Non-Goal 更新: `done`（plan `ai-dev/plans/nop-stream-production/2026-08-02-0955-7-shard-to-keygroup-migration-and-vision-update.md`，completed — 不变量 #2 三处跨文档 drift 收口（`00-vision.md:89`/`checkpoint-design.md:1024`/`core-design.md:338` StateShard→KeyGroup）+ vision §四 Non-Goal 改写为 supported-with-migration + §七 key-group 重分布移入「保留」+ §六 决策点 #6 stateShardCount→maxParallelism + §8.5 类名笔误修正（KeyGroupRangeAssignment→KeyGroupAssignment）+ `checkpoint-design.md` §8.5.1 reshard migration 使用契约 + `state-management-design.md:96` 同步；Stage 35 deferred「maxParallelism 显式迁移」收口：离线 reshard 工具 `KeyGroupReshard`（core 纯逻辑）+ `MaxParallelismReshardMigration`（runtime I/O，原子写新 savepoint + reshard-report.json + `ReshardMigrationResult` 守恒校验）；focused test 11 + E2E 8，memory+rocksdb restore 聚合一致，anti-hollow（moved 断言）+ 全 fail-fast 边界）

### Phase 4 — 分布式接入平台基础设施

- 38. Leader election / HA（G24, G25，P1）: `done`（plan `ai-dev/plans/nop-stream-production/2026-08-02-0955-8-leader-election-ha.md`，completed — WIRE 平台 `ILeaderElector`/`SysDaoLeaderElector` 进 `JobCoordinator`（HA lifecycle 状态机：start()→STANDBY + self-activation reconciliation when elector already leader，becomeLeader→ACTIVE/becomeFollower→STANDBY，禁用 whenElectionCompleted 作 ACTIVE 条件）+ standby coordinator（控制面方法 active gate 显式 warn-log 拒绝，含 reportTaskStatus/reportNodeTaskLiveness 闭合 #24 静默跳过）+ composite fencing token `leaderId@epoch#recoveryGen`（解耦 leadership/recovery fencing：epoch 仅 leadership 切换轮转，recoveryGen 每次 globalRecovery 递增，完整 token 经 updateFencingToken 推送）+ deactivate≠stop（failureDetector.shutdownNow 仅 stop/failJob 调用，leadership-loss 保留 detector 以便重新当选）+ Phase 3 真实 JDBC smoke check（`TestJobCoordinatorWithSysDaoLeaderElector` H2 + AutoTest，作为 `SysDaoLeaderElector` 首个生产用户的集成回传）+ 平台 finding F0a（`SysDaoLeaderElector` 不重放 leadership 给新 listener — coordinator-side self-activation workaround）记录回传 nop-sys-dao 团队；focused test 11 Phase 1 + 9 Phase 2 + 3 Phase 3 JDBC，全 anti-hollow；unblock Phase 4 Stage 39/40/42）
- 39. 控制面 RPC 跨 JVM + fencing token 统一（G23 续）: `done`（plan `ai-dev/plans/nop-stream-production/2026-08-02-2141-1-cross-jvm-control-rpc-fencing.md`，completed — Phase 1 fencing token String→long epoch 统一（`fencing_epoch = leaderEpochValue * EPOCH_SCALE + recoveryGen` 单一 long，数据面双键收敛为单 long 比较，两不变量证明；ClusterRegistry Option B 边界转换不迁移 DDL；非 HA recoveryGen seed=1 零回归）+ Phase 2 控制面跨 JVM RPC（`StreamControlRpcServer`=`MessageRpcServer`+`ReflectiveRpcService`+`CorrelatingRpcService`、`StreamControlRpcProxyFactory`=`RpcServiceProxyFactoryBean`+`MessageRpcClient`、`StreamControlRpcTransformer` void→oneWay、`RpcDistributedExecutor` 分布式 dispatcher + 长生命周期 `DistributedJobHandle`、首个 beans.xml `stream-control-rpc.beans.xml`）+ Phase 3 distributed abort（`JobCoordinator.registerDistributedAbortHandler`→`cancelTask` RPC 独立控制通道；G5/G34 Decision-only 不引入空壳 `CancelCheckpointMarker`）；focused test 5 Phase 1 + 2+1 Phase 2 + 1 Phase 3 + bean-bootstrap，全 anti-hollow；unblock Stage 40/42）
- 40. 数据面 IMessageService 跨 JVM: `done`（plan `ai-dev/plans/nop-stream-production/2026-08-02-2141-2-cross-jvm-data-plane-message-service.md`，completed — `IDataPlaneWireCodec` SPI（`SysDaoWireCodec`/`PulsarStringWireCodec`/`IdentityWireCodec`）+ `DataPlaneMessageServiceAdapter` 装饰器解决裸 envelope 与两后端的序列化阻抗（SysDao 仅持久化 ApiRequest.data / Pulsar Schema.STRING / barrier-watermark payload 非 DataBean，经 `DataPlaneWireSupport` 摊平），仅包装数据面视图使控制面 RPC 不受影响；dispatchers `setDataPlaneWireCodec` 注入，`stream-data-plane.beans.xml` 部署脚手架；`nop-stream-runtime` 无后端硬依赖。DB 后端 E2E 断言 record 经 `NopSysEvent` 表中转（anti-hollow）+ fencing + exactly-once；Pulsar 后端 codec round-trip 单测 + `@EnabledIfSystemProperty` 门禁 CI-broker E2E；backpressure 契约按后端拆分（Pulsar 队列饱和回压 / SysDao 不提供 producer 回压）。附带修复 Stage 39 遗留 nop-sys-dao stale fencing 测试。runtime 654/rocksdb 80/sys-dao 26 全绿；unblock Stage 42/43）
- 41. ClusterRegistry 收敛到平台 discovery: `done`（plan `ai-dev/plans/nop-stream-production/2026-08-03-0900-3-cluster-registry-discovery-convergence.md`，completed — D7 = Option B（对接共存）confirmed 2026-08-03：ClusterRegistry 保留为 runtime source of truth（coordinator 注册/fencing epoch/node lease/task assignment+attempt history），平台 discovery 提供跨系统可发现性；写方向 `StreamNodeAutoRegistration`（已落地）+ 读方向 `NodeDiscoveryConsistencyChecker`（新增，消费 `IDiscoveryClient.getInstances` 做 drift 检测，fail-loud `ERR_STREAM_DISCOVERY_DRIFT`）；`EmbeddedDistributedExecutor` 接线读方向（注册后 assertConsistent 证明写传播）；`StreamNodeAutoRegistration` javadoc + design doc §平台 discovery 注册/D7 审计/D73 收敛 deferred 措辞为最终关系；跨模块 smoke `TestStreamNodeAutoRegistrationWithSysDaoNamingService`（nop-sys-dao，real SysDaoNamingService，5 tests）+ multi-JVM `TestMiniStreamClusterProcessSpawn`（3 tests）+ `TestNodeDiscoveryConsistencyChecker`（8 tests）；平台 finding：SysDaoNamingService.unregisterInstance deleteEntityById session bug（类比 F0a，recorded））
- 42. 多 JVM 集成测试基建: `done`（plan `ai-dev/plans/nop-stream-production/2026-08-03-0001-1-multi-jvm-test-infrastructure.md`，completed — Phase 0 remote task deployment descriptor（`TaskDeploymentDescriptor` + `IStreamTaskRpcService.deployTask` default method + `SubtaskPlanBuilder` 本地重建 invokable + `JobCoordinator.remoteDeployMode`/`TaskManager.deployTask` 接线 + recovery 路径 redeploy）；Phase 1 standalone JVM entry points（`TaskManagerMain`/`JobCoordinatorMain` 真实 `public static void main` + `PollingJdbcMessageService` 跨 JVM H2 AUTO_SERVER 共享消息后端 + `SharedJdbcInfrastructure` + `ClusterLaunchConfig` key=value 解析 + SIGTERM 优雅退出 + fail-fast）；Phase 2 `MiniStreamCluster`（`ProcessBuilder` 真实进程编排 + H2 AUTO_SERVER=TRUE 共享 backing + 唯一 runId topic namespace + 日志聚合 + killTaskManager/restartTaskManager + health-check 轮询）；Phase 3 `TestMultiJvmExactlyOnceRecovery`（gated by `@EnabledIfSystemProperty`，真实多 JVM deploy/kill/recover/fencing 跨 JVM 验证，coordinator log 显示真实 `globalRecovery` + `Fencing epoch rotated` 由跨 JVM `deployTask` FAILED report 触发）；附带修复 `JdbcClusterRegistry.ensureTables()` 多 JVM 并发创建竞态（`CREATE TABLE IF NOT EXISTS`）；default `./mvnw test` 681/0/5 skipped（4 多 JVM gated + 1 历史），多 JVM 手动启用时 5/0/0 全绿）

### Phase 5 — 容错强化

- 43. Channel 心跳 + unaligned checkpoint（G6，P1）: `done`（plan `ai-dev/plans/nop-stream-production/2026-08-03-0001-2-channel-heartbeat-unaligned-checkpoint.md`，completed；channel 心跳 producer-sends-idle + consumer timeout 检测、aligned→unaligned 回退、ChannelState capture/persist/replay 端到端）
- 44. Region-based failover（G28 续，P2）: `done`（plan `ai-dev/plans/nop-stream-production/2026-08-03-1403-1-region-based-failover.md`，completed — vision 决策请求 + go/no-go 裁定 **go confirmed（选项 B 流式 + 物化点，2026-08-03，mission-driver proxy 确认同 Stage 41 D7 渠道）**；successor plans 状态：**5/5 completed** — `2026-08-03-1600-1-blocking-edge-materialization-point`（#1 物化点机制，✅ completed） / `2026-08-03-1600-2-region-identification`（#2 region 识别，✅ completed） / `2026-08-03-1600-3-supervision-loop-execution-model`（#3 supervision loop，✅ completed） / `2026-08-03-2107-1-drain-reconnect`（#4 drain/reconnect — overflow-bypass + consistent-cut + operator state restore + producer-region 重启 + consumer reconnect-to-live-queue，✅ completed） / `2026-08-03-2107-2-per-region-restart-counter`（#5 per-region counter 可配置，✅ completed）；吸收 Stage 27 deferred G57/G28 续/per-region counter → 归属 successor plans）
- 45. 多并发 checkpoint 完整支持（G31 续，P2）: `done`（plan `ai-dev/plans/nop-stream-production/2026-08-03-0900-1-concurrent-checkpoint-multi-epoch.md`，completed — task 级多 epoch 端到端：`CheckpointBarrierTracker` per-epoch ACK 追踪（D2 option b：`OperatorSnapshotResult` 携带 checkpointId 路由，42 处 call-site 零签名变更）+ `InputGate` per-barrier 对齐状态机（D1：aligned 序列化 + aborted straggler 丢弃，不再抛 overlapping）+ epoch 精准 abort（D3 option C：local handler epoch 感知，仅当无在途 epoch 才 cancel task，distributed epoch-RPC 留 successor）+ unaligned 保持 single-in-flight（D4，successor Stage 47）+ maxConcurrent=3 E2E + abort 中间 epoch 精准性测试；Coordinator 层 Stage 19 零回归；697 tests pass）
- 46. Coordinator HA 端到端 + HA checkpoint store（G32, G35，P2）: `done`（plan `ai-dev/plans/nop-stream-production/2026-08-03-0900-2-coordinator-ha-checkpoint-store.md`，completed — G32 failover-safe 重建路径（`activateAsLeader` → `rotateFencingEpochAndRestore(true)` → `restoreFromCheckpoint()` reload from `ICheckpointStorage` + counter advance + fail-loud）+ CompletedCheckpointStore 裁定「不引入」（§9.3.1）+ G35 design-gated 移 successor Stage 49（§5.3.1）+ `JdbcLeaderElector`（nop-stream-runtime 生产 JDBC lease 选举器，架构裁定不能用 SysDaoLeaderElector）+ `JobCoordinatorMain` HA 接线 + `MiniStreamCluster` ≥2 coordinator + 4 层测试矩阵（单进程/in-process JDBC HA/多 JVM failover/跨模块 SysDao smoke）；709 tests pass + 独立 closure audit 15/15 PASS）
- 47. Unaligned + rescale 交互: `done`（plan `ai-dev/plans/nop-stream-production/2026-08-03-1403-2-unaligned-rescale-interaction.md`，completed — 修复 rescale 时 channel state 静默丢弃 live defect（rescale 检测点 `assertNoChannelStateOnRescale` fail-fast 抛 `ERR_STREAM_CHANNEL_STATE_RESCALE_UNSUPPORTED`，不依赖 `instanceof TaskEpochSnapshot` 静默跳过）；首版 fail-fast 拒绝（D1 裁定，`checkpoint-design.md` §2.11.8）；D4 unaligned+multi-in-flight guard 直接断言测试（Stage 45 Follow-up 收口）；E2E 三场景（unaligned+rescale→fail-fast / aligned+rescale→OK / unaligned+同并行度→OK）；吸收 Stage 43/45 deferred；727 tests pass + 独立 closure audit 9/9 PASS）

### Phase 6 — 生态与上层

- 48. Kafka IMessageService: `done`（plan `ai-dev/plans/nop-stream-production/2026-08-03-2124-2-kafka-message-service.md`，completed — `nop-message-kafka` 从空壳到完整 `KafkaMessageService implements IMessageService`：`sendAsync`（`KafkaProducer<String,String>` + callback→`CompletableFuture`）+ `subscribe`（`KafkaConsumeTask` poll 循环处理 5 种 `IMessageConsumer.onMessage` 返回值语义：null→commitSync / CompletionStage→await / ConsumeLater→seek 不 commit / Acknowledge→ack-topic reply / 其他→reply+commit）+ `KafkaMessageSubscription` 5 方法（suspend/resume 映射 `KafkaConsumer.pause/resume`）+ `init()`/`destroy()` 生命周期 + `seekToPosition` 真实实现（非 stub，`seekToMessage` fail-loud）+ kafka-clients 3.5.0 经 `nop-dependencies` 管理 + `KafkaStringWireCodec`（nop-stream-runtime/transport，JSON String wire format 与 PulsarStringWireCodec 同构）+ E2E gated 测试（`@EnabledIfSystemProperty`）+ 组件级测试 30 tests pass + nop-stream-runtime 745 tests pass）
- 49. Source split 体系（FLIP-27 风格）: `done`（plan `ai-dev/plans/nop-stream-production/2026-08-04-0900-1-source-split-flip27.md`，completed — Phase 0 七项设计裁定 D1-D7[FLIP-27 范式/§4 Beam-SDF reject、coordinator-state checkpoint 落地 §2.6/§5.3 `sourceEnumeratorSnapshots`、split 经控制 RPC 下发、动态发现裁定、SourceApiTransformation 路由、旧 concrete 收敛、OperatorCoordinator bypass] + Phase 1 接口[`Source`/`SplitEnumerator`/`SourceReader`/`SourceSplit`/`SimpleVersionedSerializer`/`Boundedness` + `EpochManifest.sourceEnumeratorSnapshots` + `SourceEnumeratorSnapshot` @DataBean] + Phase 2 接线[`addSource(Source)` + `SourceApiTransformation` + `SourceReaderOperator` + `LocalSourceCoordinator` + `SourceCoordinatorRegistry` + `StreamGraphGenerator` 分支 + `CheckpointCoordinator` coordinator-state 段 + `CheckpointSerDe` Base64 序列化] + Phase 3 参考 `FileSource` E2E[`FileSplit`/`FileSplitEnumerator`/`FileSourceReader`/`FileSplitEnumeratorState` + 4 E2E tests + 3 checkpoint/restore tests]；45 new tests，全模块绿；D6 旧 concrete `SourceEnumerator`/`SourceSplit` 删除 + 测试迁移）
- 50. nop-stream-flow XDSL 声明式编排: `done`
- 51. Delta 定制 StreamModel: `done`（plan `ai-dev/plans/nop-stream-production/2026-08-04-0900-3-delta-customization-stream-model.md`，completed — 验证并固化 `.stream.xml` Delta（`x:extends` 显式 base path + `_delta/default/` 目录分层）端到端执行正确 + delta-unique 断言（防 silent no-op）+ transform 级 fingerprint 敏感性 + config-only by-design 不变裁定 + fail-fast 保持；`stream-dsl-design.md` §7.2 重写为最终 Delta 契约 + `00-vision.md` Delta 标记已落地；8 新测试，51/0/0 全绿；加载机制 DslModelParser 已有，无新增生产代码）
- 52. 事务型 JDBC sink（2PC）: `done`
- 53. CDC 深化 + 文件 sink: `done`（plan `ai-dev/plans/nop-stream-production/2026-08-04-2107-1-cdc-deepening-file-sink.md`，completed — Phase 1 CDC checkpoint offset 集成[`NopStreamOffsetBackingStore` implements Kafka Connect `OffsetBackingStore`（Debezium 2.4.0 约束适配：`offset.storage` FQCN 反射实例化 + connector-name registry 桥接实例）+ `DebeziumConfig implements Serializable` + `DebeziumCdcSourceFunction implements CheckpointedSourceFunction`（config 非 transient，`snapshotState`/`initializeState` 经 `"cdc-offsets"` key round-trip offset map）+ `DebeziumMessageSource`/`DebeziumEngineWrapper` 构造器 overload + `DebeziumEngineConfig.buildProperties(config, useCustomOffsetStore)` + `CheckpointedSourceFunction` Javadoc drift 修复] + Phase 2 exactly-once 文件 sink[`FileTwoPhaseCommitSink` extends `TwoPhaseCommitSinkFunction`（temp file + `Files.move(ATOMIC_MOVE)` + manifest 原子更新 + 幂等 commit 守卫 + final-exists/manifest-missing 边缘修复）+ `FilePendingCommit implements Serializable`]；27 新 tests 全绿；connector-design.md §5.4/§5.5 + §7 已知限制 #9-#11 + source-anchors STRM-034~036 同步）
- 54. CEP SharedBuffer 缓存改进（G65，P3）: `todo`
- 55. 推迟项跟踪：spill-to-disk（G66）/ adaptive scheduling（G67）: `todo`

## Status values

| Status | Meaning |
| --- | --- |
| `todo` | Not started, no plan |
| `planned` | Has execution plan, passed draft review |
| `done` | Complete, passed closure audit |

## Framework / platform reuse

| Capability | Provider | Notes |
| --- | --- | --- |
| 状态持久化 | `ICheckpointStorage` (LocalFile/JDBC) | DONE — LocalFile + `JdbcCheckpointStorage` 均已实现（生产就绪，Stage 19/31）；Stage 46 failover-safe 重建路径已接线 |
| 数据序列化 | `JsonTool`（保留）+ `SerializerFingerprint` schema 解析层（Phase 2） | 不引入二进制序列化体系（vision Non-Goal） |
| CEP 条件表达式 | `IEvalFunction` (nop-xlang) | 已使用 |
| 数据库访问 | `IJdbcTemplate` + `IDialect` | checkpoint storage 使用 |
| 批量数据源 | `IBatchLoader` / `IBatchConsumer` | 已桥接 |
| 消息队列（数据面） | `IMessageService`（SysDaoMessageService / PulsarMessageService） | DONE — 数据面跨 JVM 经 `IDataPlaneWireCodec` 适配两后端（Stage 40） |
| RPC（控制面） | `IRpcService` / `MessageRpcServer` / `SimpleRpcServer` / `RpcServiceProxyFactoryBean` | Phase 4 接线 |
| Leader 选举 | `ILeaderElector` / `SysDaoLeaderElector` | Phase 4 接入，nop-stream 为首个生产用户 |
| Discovery / 心跳 / lease | `IDiscoveryClient` / `INamingService` | Phase 4 接入 |
| 分布式锁 | `IResourceLockManager` + `SysDaoResourceLockManager` | DONE，按需使用 |
| 分区分配 | `IPartitionAssigner` + `WeightedPartitionAssigner` | DONE |
| Delta 定制 | Nop 可逆计算机制 | Phase 6 接入 |

## Current baseline

**Already shipped（前序路线图 Items 9—13）:**
- InputGate barrier alignment enabled（`InputGate.handleBarrierNonRecursive()` 承担多输入对齐；`BarrierAligner` 类 `@Deprecated` 为 reference code，无生产调用者）、findCompletedCheckpointId 修复、abort 通道 local 路径
- TimestampsAndWatermarksOperator 自动插入、AccumulationMode/PaneInfo 接线、evictAfter
- CEP 统一 IKeyedStateBackend，移除 SimpleKeyedStateStore
- Operator State 基础体系 + 重分布
- StreamModel 做实（buildStreamModel 注册 StreamComponents，Fingerprint 生效）

**Main gaps (not yet closed):**
- ~~G1: Session window merge 对 AggregatingState 失败~~ ✅ Closed (item 14)
- ~~G2: Timer 无 checkpoint/restore~~ → Stage 15
- ~~G3: BarrierAligner 无生产调用者~~ ✅ Closed (item 9)
- ~~G8—G11, G13: Operator State 体系缺失~~ ✅ Closed (items 12a/12b)
- ~~G14, G15, G17: PaneInfo/AccumulationMode/SourceFunction watermark~~ ✅ Closed (item 10)
- ~~G16: 并行 timer service 重复~~ → Stage 15
- ~~G18, G19, G49: CEP 状态后端/SimpleKeyedStateStore~~ ✅ Closed (item 11)
- ~~G20: Watermark runtime→CepOperator 传播管路~~ ✅ Closed (items 10/11)
- ~~G21: OperatorChain double-open~~ ✅ Closed (item 9)
- ~~G4: 无 multi-input barrier alignment~~ ✅ Closed (item 16) — `InputGate.handleBarrierNonRecursive()`
- ~~G7: 无 channel blocking~~ ✅ Closed (item 16) — `InputGate.blockConsumption()`/`resumeConsumption()`
- G5, G34: CancelCheckpointMarker / abort data-channel propagation → Stage 39（deferred from item 16，跨 JVM RPC prerequisite，见 `checkpoint-design.md:911`）
- G22: 无 mailbox 执行模型 → Stage 17
- G23—G25: 无跨 JVM RPC / leader election → Stages 28/38/39
- G28—G35（除 G34）: Checkpoint 管线多项缺口 → Stages 18/19/20
- ~~G60, G61, G63: bulk cleanup / Jdbc dup-key / Timer O(n)~~ ✅ Closed (item 9)
- RocksDB 状态后端缺失 → Stages 30-31
- Key-Group / rescale 缺失 → Stages 34-35
- Unaligned checkpoint 缺失（G6）→ Stage 43 ✅ Closed (item 43)
- 文档与实现多处不一致（D69—D73）→ Stage 22 ✅ Closed (item 22)

## Stages

| # | Stage | Owner plan | Deps | Critical path | Reuse |
| --- | --- | --- | --- | --- | --- |
| 14 | Session window merge | `14-session-window-merge` | — | **Yes** | Flink `mergeNamespaces` |
| 15 | Timer checkpoint + 统一 | `15-timer-checkpoint` | — | **Yes** | Flink `snapshotTimersForKeyGroup` |
| 16 | Multi-input barrier + abort | `16-barrier-alignment` | 15 | **Yes** | existing `InputGate` alignment |
| 17 | Mailbox 执行模型 | `17-mailbox-model` | 16 | **Yes** | nop-stream 原生 minimal task queue |
| 18 | 异步两阶段 snapshot | `18-async-snapshot` | 17 | No | Flink `OperatorSnapshotFutures` |
| 19 | Checkpoint 并发与共享 | `19-checkpoint-concurrency` | 18 | No | — |
| 20 | Partial/subtask 恢复 | `20-partial-recovery` | 16 | No | — |
| 21 | Evictor/Pane/Watermark | `21-evictor-watermark` | — | No | existing window impl |
| 22 | 文档合同对齐 | `22-doc-alignment` | — | No | — |
| 23 | 代码清理与 P3 | `23-code-cleanup` | — | No | — |
| 24 | DeploymentPlan 分配 + discovery | `24-deployment-discovery` | Phase 0 | **Yes** | `IDiscoveryClient` / `INamingService` |
| 25 | Failure detection + state machine | `25-failure-detection` | 24 | **Yes** | — |
| 26 | Buffer pool | `26-buffer-pool` | 24 | No | — |
| 27 | Targeted failover | `27-targeted-failover` | 25 | **Yes** | — |
| 28 | RPC 接口扩容 + backpressure | `28-rpc-backpressure` | 24 | **Yes** | `nop-rpc` |
| 29 | SerializerFingerprint 兼容性 | `29-serializer-fingerprint` | — | **Yes** | nop-stream 原生（保留 JsonTool） |
| 30 | RocksDB 后端核心 | `30-rocksdb-backend` | 29 | **Yes** | RocksDB JNI |
| 31 | 增量 checkpoint | `31-incremental-checkpoint` | 30 | **Yes** | RocksDB native checkpoint (G45) |
| 32 | State TTL | `32-state-ttl` | 30 | No | RocksDB compaction filter |
| 33 | 状态迁移接线 | `33-state-migration` | 29 | No | existing `SerializerFingerprint` |
| 34 | Key-Group 模型 | `34-key-group` | Phase 2 | **Yes** | Flink KeyGroupRange |
| 35 | KeyGroupRange 恢复 | `35-keygroup-recovery` | 34, 31 | **Yes** | — |
| 36 | BroadcastState | `36-broadcast-state` | Phase 2 | No | — |
| 37 | StateShard→KeyGroup 迁移 | `37-shard-migration` | 34 | No | — |
| 38 | Leader election / HA | `38-leader-election` | Phase 1 | **Yes** | `ILeaderElector` / `SysDaoLeaderElector` |
| 39 | 跨 JVM RPC + fencing | `39-cross-jvm-rpc` | 38, 28 | **Yes** | `nop-rpc` 全套 |
| 40 | 数据面跨 JVM | `40-data-plane` | 39 | **Yes** | `SysDaoMessageService` / `PulsarMessageService` |
| 41 | ClusterRegistry 收敛 | `41-cluster-registry` | 38 | No | `IDiscoveryClient` / `INamingService` |
| 42 | 多 JVM 测试基建 | `42-multi-jvm-infra` | 39 | **Yes** | — |
| 43 | Channel 心跳 + unaligned | `43-unaligned-checkpoint` | Phase 4 | **Yes** | FLIP-76 |
| 44 | Region failover | `44-region-failover` | 43 | No | — |
| 45 | 多并发 checkpoint | `45-concurrent-checkpoint` | 43 | No | — |
| 46 | Coordinator HA E2E | `46-coordinator-ha` | 38, 43 | No | — |
| 47 | Unaligned + rescale | `47-unaligned-rescale` | 43, 35 | No | — |
| 48 | Kafka IMessageService | `48-kafka-message` | Phase 4 | No | `PulsarMessageService` 形态 |
| 49 | Source split 体系 | `49-source-split` | Phase 3 | No | FLIP-27 |
| 50 | XDSL 声明式编排 | `50-xsdl-orchestration` | Phase 0 | No | Nop XDSL |
| 51 | Delta 定制 | `51-delta-customization` | 50 | No | Nop 可逆计算 |
| 52 | 事务型 JDBC sink（2PC） | `52-jdbc-2pc-sink` | Phase 4 | No | `IBatchConsumer` |
| 53 | CDC 深化 + 文件 sink | `53-cdc-file-sink` | Phase 4 | No | `IBatchLoader` / Debezium |
| 54 | CEP SharedBuffer 缓存 | `54-cep-cache` | — | No | Guava Cache |
| 55 | 推迟项跟踪 | — | — | No | — |

### Stage details

#### 14. Session window merge 修复

> Status: see Work Items above

**Goal:** 修复 `WindowOperator.mergeWindowContents()` 对 AggregatingState 的合并路径，使 session window 端到端可用。

**Deliverables:**
- G1: 用 `state.mergeNamespaces()` 替代 `clear()+add()`，修复 AggregatingState merge 抛异常
- 启用当前 4 个 disabled session window 测试
- Owner-doc: `docs-for-ai/02-core-guides/testing.md` 记录测试限制历史

**Out of scope:** MergingWindowSet 本身（已实现）、非 session 窗口类型。

**Module / area:** nop-stream/nop-stream-core/window/

#### 15. Timer checkpoint/restore + timer service 统一

> Status: see Work Items above

**Goal:** 使 timer 参与 checkpoint/restore，并合并两个重复的 timer service 实现。

**Deliverables:**
- G2: `HeapInternalTimerService.snapshotTimersForKeyGroup()` + restore
- G16: 合并 `HeapInternalTimerService` 与 `WindowOperatorTimerService` 为单一实现
- kill 恢复后 window 正确触发的 E2E 测试

**Out of scope:** Timer 差量 checkpoint（优化项，后续）。

**Module / area:** nop-stream/nop-stream-core/time/, nop-stream/nop-stream-core/window/

#### 16. Multi-input barrier alignment + abort 通道

> Status: see Work Items above

**Goal:** 验证 InputGate 多输入对齐状态机（已实现于 `InputGate.handleBarrierNonRecursive()`），补齐 cancel marker 与 abort 数据通道传播。

**Deliverables:**
- G4: 多输入 barrier 对齐运行时（InputGate 状态机）✅ verified — `InputGate.java:347`，`GraphExecutionPlan.java:285` 接线，`TestInputGateBarrierAlignment` 覆盖
- G5: `CancelCheckpointMarker` 事件类型 — Stage 39 Phase 3 裁定 **Decision-only**（不引入；主 abort 机制为 `cancelTask` RPC 控制通道，已满足 checkpoint-design §13.2 line 1133 独立控制通道契约；无 in-data-flow marker 消费方，引入空壳违反 plan guide #24。Successor：Stage 43/45 若出现真实消费方再裁定。见 `checkpoint-design.md` §13.2.1）
- G7: `Input.blockConsumption()`/`resumeConsumption()` channel blocking ✅ verified — `InputGate.java:220/234/245`，`TestInputGateBlockingApi` 覆盖
- G34: abort 信号通过数据 channel 传播 — Stage 39 Phase 3 裁定 **Decision-only**（同 G5；distributed abort 经 `JobCoordinator.registerDistributedAbortHandler`→`cancelTask` RPC 独立控制通道落地，local 执行仍由 `registerLocalAbortHandler`→`inputGate.resumeConsumptionAll()`+`task.cancel()` 承担。见 `checkpoint-design.md` §8.7/§13.2/§13.2.1）

**Out of scope:** unaligned checkpoint（Stage 43）。

**Module / area:** nop-stream/nop-stream-core/checkpoint/, nop-stream/nop-stream-runtime/checkpoint/

#### 17. Mailbox 执行模型

> Status: done (2026-07-25) — plan `2026-07-25-1500-1-mailbox-execution-model`

**Goal:** 引入最小化单线程任务队列，使 checkpoint barrier 与 timer 正确交错。**不移植 Flink MailboxProcessor 全套机制**，实现 nop-stream 原生的 `processInput`/`processMail` 交错循环。

**Deliverables:**
- G22: 最小化 task queue + `processInput`/`processMail` 交错（非 Flink Mail/MailboxExecutor/suspend-resume 全套）
- 单线程避免并发同步问题的验证

**Out of scope:** 异步算子（vision Non-Goal）。

**Module / area:** nop-stream/nop-stream-runtime/task/

#### 18. 异步两阶段 snapshot pipeline

> Status: see Work Items above

**Goal:** 把同步 snapshot 改为同步 phase（状态冻结）+ 异步 phase（持久化），降低 checkpoint 延迟。

**Deliverables:**
- ✅ G30, G44: coordinator 侧 async persist pipeline（专用 `checkpoint-persist-*` executor，三段模型：段 1 ACK 线程 CAS+快照 → 段 2 executor I/O 不持锁 → 段 3a/3b 重新获取 monitor）。**Note**: draft review 修正了初稿「引入 task 侧 `OperatorSnapshotFutures` 等价物」方向——nop-stream 无 task 侧持久化，改为 coordinator 侧 async persist。Plan `2026-07-25-2200-1-async-snapshot-pipeline` (completed)

**Out of scope:** RocksDB 增量 checkpoint（Stage 31）。

**Module / area:** nop-stream/nop-stream-core/checkpoint/, nop-stream/nop-stream-runtime/checkpoint/

#### 19. Checkpoint 并发与共享状态

> Status: see Work Items above

**Goal:** 解开 `maxConcurrentCheckpoints=1` 硬上限，引入 shared state registry。

**Deliverables:**
- G31: 多并发 checkpoint 基础支持（完整多 epoch 追踪在 Stage 45）— ✅ done（Coordinator 尊重配置值；minPause(last-completed) 接线；共存 pending 独立 ACK/complete/abort/timeout 经 coordinator 路径覆盖；stale 警告/文档修正）
- ~~G33: `SharedStateRegistry` 引用计数~~ → 移交 Stage 31（plan `2026-07-25-2300-1` Deferred 裁定：内存全量后端下 `CompletedCheckpoint.taskStates` 为独立 `byte[]`/`HashMap` 拷贝，无跨 checkpoint 共享状态可消费；唯一 load-bearing 消费者为 Stage 31 RocksDB 增量 SST 共享。本 plan 不引入无消费者空壳抽象）

**Out of scope:** unaligned 下的多并发（Stage 45）。

**Module / area:** nop-stream/nop-stream-core/checkpoint/, nop-stream/nop-stream-runtime/checkpoint/

#### 20. Partial/subtask 级恢复

> Status: G29 done（plan `2026-07-25-2200-2-partial-subtask-recovery`，completed）；G28 design-gated

**Goal:** 支持 subtask 粒度的状态恢复，而非全局恢复。

**Deliverables:**
- G28: partial failover 基础（完整 region failover 在 Stage 44）— design-gated，deferred
- G29: subtask-level granular restoration — done（`restoreFromEpoch` epochId 透传 + 多 subtask 独立恢复验证）

**Out of scope:** region-aware scheduling（Stage 44；Stage 27 裁定 no-go，未引入 region 概念）。

**Module / area:** nop-stream/nop-stream-runtime/recovery/

#### 21. Evictor/Pane/Watermark 集成

> Status: see Work Items above

**Goal:** 补齐窗口/时间模型的剩余集成缺口。

**Deliverables:**
- G46: `Evictor.evictAfter()` 调用接线
- G47: `StatusWatermarkValve` 等效（多输入 watermark 合并）
- G48: early/on-time/late pane tracking
- Owner-doc: `time-model-design.md` 更新

**Out of scope:** 并行源 watermark 对齐（需 coordinator，Phase 5+）。

**Module / area:** nop-stream/nop-stream-core/window/, nop-stream/nop-stream-core/time/

#### 22. 文档合同对齐与 source-anchors 补全

> Status: done (2026-07-25) — plan `2026-07-25-1500-3-doc-contract-alignment`

**Goal:** 消除文档与实现的落差，补全 source-anchors 的 nop-stream 条目。

**Deliverables:**
- D69: EFFECTIVELY_ONCE 语义文档（nop-stream 独有）
- D70: Epoch-based recovery 文档（nop-stream 独有）
- D71: 四层图模型差异文档
- D72: IMessageService 数据面设计文档
- D73: ClusterRegistry JDBC durability 文档
- `01-architecture-baseline.md` §四执行管线修订
- `docs-for-ai/04-reference/source-anchors.md` 添加 nop-stream 锚点
- `cep-design.md` 纠正 SimpleKeyedStateStore 说法

**Out of scope:** 代码变更（纯文档）。

**Module / area:** ai-dev/design/nop-stream/, docs-for-ai/

#### 23. 代码清理与 P3 次要改进

> Status: see Work Items above

**Goal:** 清理代码债务与 P3 次要改进项。

**Deliverables:**
- G68: OperatorChain javadoc 修复（forward vs reverse）
- G62: MergingState 中间接口 — **Decision-only**（不引入无消费方的空壳接口；设计决策记录于 `state-management-design.md` §2.4，延后至有真实消费方的 window/state 重构 successor）
- G64: 反射工厂加载优化（消除空 catch，保持既有 call-site fail-fast）
- 空 else 块清理、`CheckpointMetricsSnapshot.toString()` 补 failureCause
- `PartitionPolicy` 死代码处理
- BarrierAligner + AlignedBarrier 删除（跨 plan deferred 死代码）；@Deprecated 死类清理

**Out of scope:** 功能性变更。

**Module / area:** nop-stream/nop-stream-core/, nop-stream/nop-stream-runtime/

#### 24. DeploymentPlan subtask 分配 + 平台 discovery 接入

> Status: see Work Items above

**Goal:** 通过 DeploymentPlan 驱动 subtask 到节点的分配，接入平台 discovery 实现节点注册与发现。**不引入 Flink SlotSharingGroup**（vision 约束 7 明确排除）。

**Deliverables:**
- G50: `DeploymentPlan` 承载 co-location 语义（subtask→node 映射），替代 slot 概念
- G51: 节点注册/发现通过 `IDiscoveryClient`/`INamingService` WIRE 平台

**Out of scope:** slot pool、SlotSharingGroup（vision Non-Goal）、跨 JVM resource manager（Phase 4 WIRE 平台）。

**Module / area:** nop-stream/nop-stream-runtime/scheduler/, nop-stream/nop-stream-runtime/cluster/

#### 25. Per-task failure detection + execution state machine

> Status: see Work Items above

**Goal:** 在 nop-stream 现有 `Task`/`SubtaskTask` 上补齐生命周期状态与 task 级故障检测。**不引入 Flink ExecutionGraph/ExecutionVertex 三层调度模型**（vision §十明确排除）。

**Deliverables:**
- G52: per-task 心跳/超时
- G54: `Task` 生命周期补齐中间状态（基于现有 Task 状态机扩展，非 ExecutionState 枚举移植）
- G55, G56: execution attempt tracking（fencing 需要）+ retry 记录
- G58: `SubtaskTask.cancel` 规范化、`Task.java` 状态补齐

**Out of scope:** region-aware scheduling（Stage 44 容错阶段）、Flink ExecutionGraph 三层抽象。

**Module / area:** nop-stream/nop-stream-runtime/task/, nop-stream/nop-stream-runtime/scheduler/

#### 26. Buffer pool 抽象

> Status: see Work Items above

**Goal:** 引入最小化有界缓冲 SPI 用于进程内 flow control。**不引入 Flink NetworkBufferPool/LocalBufferPool 层次**（跨 JVM flow control 由 IMessageService 后端提供）。

**Deliverables:**
- G53: `IBufferPool` SPI + 基于有界队列的 Memory 实现（进程内 backpressure）

**Module / area:** nop-stream/nop-stream-runtime/transport/

#### 27. Targeted failover

> Status: done（裁定交付 NO-GO）

**Goal:** 裁定 targeted failover 在当前架构下是否可行。裁定结果：**NO-GO**。

**Deliverables:**
- G57: targeted failover 可行性裁定文档 `ai-dev/design/nop-stream/failover-design.md`（NO-GO：all-pipelined→单 region + drain/reconnect 不可设计）

**Deferred → Stage 44 successor plans（go confirmed 2026-08-03，blocking edge vision 决策已批准）：** G57 实现、G28（续，partial/region 恢复）、per-region restart 计数器。前置 = blocking edge + region 概念 + supervision loop + drain/reconnect + per-region 计数器（5 项各独立 successor plan，优先级排序见 `failover-design.md` §9.5）。

**Out of scope:** 完整 region-based failover（Stage 44，含跨 JVM）；region-aware scheduling（G55）。

**Module / area:** ai-dev/design/nop-stream/failover-design.md（裁定文档；无代码变更）

#### 28. 分布式 RPC 接口扩容 + 进程内 backpressure

> Status: see Work Items above

**Goal:** 扩展 RPC 接口为完整调度控制面。**不实现 credit-based/ACK_WINDOW 网络层 flow control**（G27）——这属于 Flink Netty 网络栈概念，nop-stream 跨 JVM flow control 由 `IMessageService` 后端（Pulsar/DB）提供。

**Deliverables:**
- G23: `IStreamTaskRpcService`/`IStreamCoordinatorRpcService` 扩容（local 实现完整）
- G26: `IStreamExecutionDispatcher` 从 2 methods 扩展
- G27: 进程内 backpressure 通过 Stage 26 有界缓冲实现；跨 JVM 由 IMessageService 后端提供（不重建网络层 flow control）

**Out of scope:** credit-based flow control、ACK_WINDOW（Flink Netty 网络栈概念，vision 约束 7 排除）、跨 JVM 传输（Stage 39）。

**Module / area:** nop-stream/nop-stream-runtime/rpc/, nop-stream/nop-stream-runtime/transport/

#### 29. SerializerFingerprint schema 兼容性体系

> Status: see Work Items above

**Goal:** 建立 checkpoint 恢复时的 schema 兼容性检查。**这是 storage 层的内部实现，不向上暴露**。序列化实现固定为 `JsonTool`，不暴露序列化接口，不引入 Flink Type-based 二进制序列化（见 `state-management-design.md` §6 核心原则）。

**Deliverables:**
- G12: `SerializerFingerprint` 作为 checkpoint manifest **内部元数据**（schema 结构指纹，非序列化器指纹）。具体 schema 描述可采用平台 `record-object.xdef` 机制，但实现细节屏蔽在 storage 内部
- G40, G41: state name → schema 记录机制（**内部自动管理**，算子不接触）。`StateDescriptor` 不携带 serializer 或 schema 引用
- G59: CheckpointSerDe schema versioning
- 设计文档更新：`state-management-design.md` §6 已明确原则

**Out of scope:** 向算子/用户暴露 schema 接口、Flink `TypeSerializer`/`TypeSerializerSnapshot` 二进制体系、`IStreamSerializer` 接口、多序列化器 SPI。

**Module / area:** nop-stream/nop-stream-core/state/, nop-stream/nop-stream-core/checkpoint/

#### 30. RocksDB 状态后端核心

> Status: see Work Items above

**Goal:** 实现 RocksDB 状态后端，突破内存上限。

**Deliverables:**
- `RocksDBStateBackend` + `RocksDBKeyedStateBackend`
- 所有 keyed state（Value/Map/List/Reducing/Aggregating）落到 RocksDB 列族
- 10 GB 状态稳定运行基准

**Out of scope:** 增量 checkpoint（Stage 31）、State TTL（Stage 32）。

**Module / area:** nop-stream/nop-stream-core/state/（新建 rocksdb 子包）

#### 31. 增量 checkpoint（SST 共享）

> Status: see Work Items above

**Goal:** 基于 RocksDB native checkpoint 实现 SST 内容寻址与共享。

**Deliverables:**
- G45, (G30 续): SST 文件内容寻址 + `SharedStateRegistry` 引用计数
- 多 checkpoint 共享同一 SST，subsumption 时清理
- 增量 checkpoint < 5s 基准

**Module / area:** nop-stream/nop-stream-core/state/rocksdb/

#### 32. State TTL

> Status: see Work Items above

**Goal:** 实现 keyed state 的生存时间与过期清理。

**Deliverables:**
- G42: `StateTtlConfig`
- G43: TTL 装饰器 + RocksDB compaction filter / memory lazy eviction

**Module / area:** nop-stream/nop-stream-core/state/

#### 33. 状态迁移接线

> Status: see Work Items above

**Goal:** 接线状态迁移机制，基于 SerializerFingerprint（非二进制 TypeSerializer）。

**Deliverables:**
- `SerializerFingerprint` 比对 + `StateMigrationFunction` 注册与触发（基于 JSON schema 差异检测）
- Integer→Long 迁移 action 验证

**Module / area:** nop-stream/nop-stream-core/state/, nop-stream/nop-stream-core/checkpoint/

#### 34. Key-Group 模型

> Status: see Work Items above

**Goal:** 用 Key-Group 替代固定 StateShard，支持并行度变化时的状态重分布。

**Deliverables:**
- G37: `maxParallelism`（默认 128）+ key→group 映射
- G38: 稳定哈希替代 `Object.hashCode()`
- G39: StateShard range 交集/分割能力
- Owner-doc: `state-management-design.md` 记录决策

**Out of scope:** rescale 恢复（Stage 35）、自动迁移（Stage 37）。

**Module / area:** nop-stream/nop-stream-core/state/

#### 35. KeyGroupRange 恢复 + RocksDB key-group 感知 restore

> Status: see Work Items above

**Goal:** 实现 rescale 时的局部恢复。

**Deliverables:**
- KeyGroupRange 交集恢复
- RocksDB SST 文件 key-group 前缀 + range 交集读取
- parallelism=4→16、16→4 savepoint restore 测试

**Module / area:** nop-stream/nop-stream-core/state/, nop-stream/nop-stream-core/state/rocksdb/

#### 36. BroadcastState（推迟 — 需 vision 决策）

> Status: see Work Items above

**Goal:** ~~实现 BroadcastState 类型支持配置流/规则流。~~ **推迟**：vision §七 核心取舍明确将"广播流"列入"去除"。实现前必须先通过 vision 决策流程（如 Stage 37 的先例）更新 §七，否则不做。

**Deliverables:**
- G36:（推迟）`BroadcastStateDescriptor` + `IBroadcastState` + Memory 后端
- 前置：vision §七 更新决策

**Out of scope:** 在 vision 未更新前实现。

#### 37. StateShard→KeyGroup 迁移 + vision 更新

> Status: see Work Items above

**Goal:** 提供存量 savepoint 的迁移路径，并更新 vision Non-Goal 边界。

**Deliverables:**
- 一次性迁移 action
- `00-vision.md` Non-Goal 更新：savepoint rescale 纳入目标

**Module / area:** `ai-dev/design/nop-stream/00-vision.md`

#### 38. Leader election / HA

> Status: see Work Items above

**Goal:** 为 JobCoordinator 接入 leader 选举实现 HA。**nop-stream 是 `SysDaoLeaderElector` 首个生产用户。**

**Deliverables:**
- G24: `ILeaderElector` + `SysDaoLeaderElector` 接入 `JobCoordinator`
- G25: standby coordinator + fencing
- 平台集成问题回传 nop-sys-dao

**Out of scope:** 完整 HA 测试矩阵（Stage 46）。

**Module / area:** nop-stream/nop-stream-runtime/cluster/

#### 39. 跨 JVM RPC + fencing token 统一

> Status: see Work Items above

**Goal:** 控制面 RPC 跨 JVM，统一 fencing token 为单调 epoch。

**Deliverables:**
- `MessageRpcServer`/`SimpleRpcServer` 暴露 `IStreamTaskRpcService`
- `RpcServiceProxyFactoryBean` + `ClusterRpcClient` 远程代理
- stream UUID fencing token → 单调 `LeaderEpoch.epoch`
- abort 控制通道 distributed 路径（`cancelTask` RPC）
- G5: `CancelCheckpointMarker` 事件类型（作为已恢复 channel 的补充通知，非主 abort 机制；主 abort 仍为控制通道，见 `checkpoint-design.md:911`）
- G34: abort 信号跨 JVM 数据 channel 传播（G5 的延续，distributed abort 协议设计项）

**Module / area:** nop-stream/nop-stream-runtime/rpc/

#### 40. 数据面 IMessageService 跨 JVM

> Status: see Work Items above

**Goal:** 数据面跨 JVM 传输，复用平台 IMessageService。

**Deliverables:**
- `RemoteResultPartition`/`RemoteInputChannel` 注入 `SysDaoMessageService`（DB）或 `PulsarMessageService`
- 两种后端各验证一次

**Module / area:** nop-stream/nop-stream-runtime/transport/

#### 41. ClusterRegistry 收敛到平台 discovery

> Status: see Work Items above

**Goal:** 用平台 discovery 替代/对接自建 `JdbcClusterRegistry`。

**Deliverables:**
- `IDiscoveryClient` + `INamingService` + `AutoRegistration` 接入
- 决策点：完全替换 vs 对接（需人确认）

**Module / area:** nop-stream/nop-stream-runtime/cluster/

#### 42. 多 JVM 集成测试基建

> Status: see Work Items above

**Goal:** 建立进程编排与 kill/restart 的集成测试基建。

**Deliverables:**
- 进程编排（N 个 TaskManager JVM + 1 个 JobCoordinator JVM）
- 端口/消息主题分配、日志聚合、进程级 kill/restart
- CI 集成

**Module / area:** nop-stream/nop-stream-runtime/test/（新建）

#### 43. Channel 心跳 + unaligned checkpoint

> Status: see Work Items above

**Goal:** 实现 channel 级心跳与 unaligned checkpoint（背压逃生）。

**Deliverables:**
- `RemoteInputChannel` 心跳/超时检测
- G6: channel state 持久化 + priority event + aligned→unaligned timeout fallback
- 持续背压下 unaligned checkpoint 完成测试

**Module / area:** nop-stream/nop-stream-runtime/transport/, nop-stream/nop-stream-runtime/checkpoint/

#### 44. Region-based failover

> Status: `done` — go confirmed（blocking edge vision 决策已批准，选项 B 流式 + 物化点，2026-08-03）；successor plans：**5/5 completed**（#1 物化点机制 ✅ / #2 region 识别 ✅ / #3 supervision loop ✅ / #4 drain/reconnect ✅ `2026-08-03-2107-1` / #5 per-region counter 可配置 ✅ `2026-08-03-2107-2`）

**Goal:** pipelined region 识别 + 区域级 task 重启，缩小故障爆炸半径。

**Deliverables:**
- G28（续）: 1000 vertex 中单 task 失败只重启 region（< 10 vertex）

**Module / area:** nop-stream/nop-stream-runtime/recovery/

#### 45. 多并发 checkpoint 完整支持

> Status: see Work Items above

**Goal:** 解开 `maxConcurrentCheckpoints=1`，支持多 epoch 同时追踪。

**Deliverables:**
- G31（续）: `CheckpointBarrierTracker` + `InputGate` 多 epoch 追踪（`BarrierAligner` 已删除，对齐由 `InputGate` 内联承担）
- maxConcurrent=3 互不干扰测试

**Module / area:** nop-stream/nop-stream-runtime/checkpoint/

#### 46. Coordinator HA 端到端 + HA checkpoint store

> Status: see Work Items above

**Goal:** 补齐 coordinator HA 测试矩阵与 HA checkpoint store。

**Deliverables:**
- G32: `CompletedCheckpointStore` 等价物，store 冗余
- G35: operator coordinator ACK tracking
- 完整测试矩阵：leader 切换、脑裂、fencing、commit uncertainty

**Module / area:** nop-stream/nop-stream-runtime/checkpoint/, nop-stream/nop-stream-runtime/cluster/

#### 47. Unaligned + rescale 交互

> Status: see Work Items above

**Goal:** 处理 unaligned checkpoint 与 rescale 的复杂叠加。

**Deliverables:**
- in-flight data 跨新并行度重映射（`InflightDataRescalingDescriptor` 等价物）
- 首版限制：rescale 时强制 aligned

**Module / area:** nop-stream/nop-stream-runtime/checkpoint/

#### 48. Kafka IMessageService

> Status: see Work Items above

**Goal:** 实现 `nop-message-kafka`（当前空模块）。

**Deliverables:**
- 按 `PulsarMessageService` 形态实现 Kafka 后端

**Module / area:** nop-message-kafka/

#### 49. Source split 体系

> Status: see Work Items above

**Goal:** 实现 FLIP-27 风格的 Source split 体系。

**Deliverables:**
- `SourceEnumerator`（动态 split 发现分配）
- `SourceReader`（split-based 消费）

**Module / area:** nop-stream/nop-stream-connector/

#### 50. nop-stream-flow XDSL 声明式编排

> Status: see Work Items above

**Goal:** 实现 XDSL 声明式 StreamModel 编排，兑现三入口承诺。

**Deliverables:**
- `nop-stream-flow` 模块
- XDSL StreamModel 与等价 DataStream API fingerprint 一致

**Module / area:** nop-stream/nop-stream-flow/（新建）

#### 51. Delta 定制 StreamModel

> Status: see Work Items above

**Goal:** 接入 Nop 可逆计算机制，支持模型层差量修改。

**Deliverables:**
- Delta 修改 StreamModel 后 fingerprint 正确反映变更

**Module / area:** nop-stream/nop-stream-flow/

#### 52. 事务型 JDBC sink（2PC）

> Status: see Work Items above

**Goal:** 实现两阶段提交的 JDBC sink，保证 exactly-once 输出。

**Deliverables:**
- `TwoPhaseCommitSinkFunction` 的 JDBC 实现（preCommit → commit → abort）
- 幂等 commit 验证

**Module / area:** nop-stream/nop-stream-connector/

#### 53. CDC 深化 + 文件 sink

> Status: see Work Items above

**Goal:** 深化 CDC 桥接与文件 sink 的 exactly-once 支持。

**Deliverables:**
- CDC（Debezium 桥接深化）
- 文件 sink（temp file + atomic rename + manifest commit）

**Module / area:** nop-stream/nop-stream-connector/

#### 54. CEP SharedBuffer 缓存改进

> Status: see Work Items above

**Goal:** 改进 CEP SharedBuffer 缓存，恢复 Flink CEP 的 LRU 驱逐行为。

**Deliverables:**
- G65: Guava Cache 替代 ConcurrentHashMap

**Module / area:** nop-stream/nop-stream-cep/

#### 55. 推迟项跟踪

> Status: see Work Items above

**Goal:** 记录当前路线图不做的 P3 项，保持 gap 计数完整。

**Deliverables:**
- G66: spill-to-disk for large buffers（推迟）
- G67: adaptive scheduling（推迟）

**Out of scope:** 本路线图范围内实现。

## Dependency graph

> 完整依赖矩阵以 Stages table 为准（conflicts resolve in favor of table）。下图展示关键路径依赖。

```mermaid
graph TD
    subgraph "Phase 0 — 正确性"
        P14["14. Session window merge"]
        P15["15. Timer checkpoint"]
        P16["16. Barrier alignment"]
        P17["17. Mailbox model"]
        P18["18. Async snapshot"]
        P21["21. Evictor/Watermark"]
        P22["22. Doc alignment"]
        P23["23. Code cleanup"]
    end
    subgraph "Phase 1 — 分布式运行时"
        P24["24. DeploymentPlan+discovery"]
        P25["25. Failure detection"]
        P27["27. Targeted failover"]
        P28["28. RPC expansion"]
    end
    subgraph "Phase 2 — 状态后端"
        P29["29. Serializer snapshot"]
        P30["30. RocksDB backend"]
        P31["31. Incremental checkpoint"]
    end
    subgraph "Phase 3 — 弹性"
        P34["34. Key-Group"]
        P35["35. KeyGroup recovery"]
    end
    subgraph "Phase 4 — 分布式接线"
        P38["38. Leader election"]
        P39["39. Cross-JVM RPC"]
        P40["40. Data plane"]
        P42["42. Multi-JVM infra"]
    end
    subgraph "Phase 5 — 容错"
        P43["43. Unaligned checkpoint"]
        P46["46. Coordinator HA"]
        P47["47. Unaligned+rescale"]
    end

    P15 --> P16 --> P17 --> P18
    P24 --> P25 --> P27
    P24 --> P28
    P29 --> P30 --> P31
    P30 --> P32["32. State TTL"]
    P34 --> P35
    P31 --> P35
    P28 --> P39
    P38 --> P39 --> P40
    P38 --> P41["41. ClusterRegistry"]
    P39 --> P42
    P43 --> P44["44. Region failover"]
    P43 --> P45["45. Concurrent ckpt"]
    P38 --> P46
    P43 --> P46
    P43 --> P47
    P35 --> P47
```

## Cross-cutting concerns

| Concern | Notes |
| --- | --- |
| 对比深度 | 源码级（精确到类名、方法签名），每个发现须附带代码引用 |
| 避免重复造轮 | 分布式能力（网络、HA、lease、discovery）一律 WIRE 平台，不自建。流处理专有逻辑（operator state、RocksDB、unaligned、key-group）才 BUILD |
| Verification baseline | 代码变更后 `mvn test -pl nop-stream -am -T 1C` 必须通过 |
| 空壳检测 | 每个实现 stage 必须包含 Anti-Hollow 检查（接口有实现调用） |
| Owner-doc 同步 | 设计变更必须同步更新 `ai-dev/design/nop-stream/` 对应文档；`source-anchors.md` 在 Stage 22 补全 |
| Gap 关闭追踪 | 每个 stage 关闭时在 `08-gap-analysis.md` 对应 gap 行末标记 ✅ + 关闭 plan 路径 |
| 跨 stage 缺口 | G28 跨 Stage 20/27/44；G31 跨 Stage 19/45。各 stage 范围不重叠，逐步深化 |
| 与 completion-roadmap 的关系 | 本路线图是 `completion-roadmap.md` Phase 0—5 的执行落地。Phase 划分如有冲突以 completion-roadmap 为准 |
| completion-roadmap 隐含项 | Phase 0.7（端到端并行度>1 验证）折叠到 Stage 42 多 JVM 测试基建；Phase 2.3（operator state rescale-to-changed-parallelism）折叠到 Stage 35 KeyGroupRange 恢复；Phase 3.8（分布式部署指南）折叠到 Stage 22 文档对齐 |

## Rules

- This file is a state index and coarse decomposition, not an execution plan.
- Each `planned` stage is owned by its execution plan.
- Status changes happen only in the Work Items block at the top.
- Milestones are derived from dependency status, never marked prematurely.
- Framework/platform reuse explicitly noted (see Framework / platform reuse table) — do not rebuild existing capabilities.

## Design Decision Points（需人确认）

引用自 `08-gap-analysis.md` §Design Decision Points 与 `completion-roadmap.md` §七。

| # | Issue | Stage 前置 | Recommendation |
|---|-------|----------|---------------|
| D1 | Key-Group vs StateShard 迁移路径 | Stage 34 前 | StateShard 增加 maxParallelism + 稳定哈希（最小侵入） |
| D2 | 是否引入 mailbox 模型 | Stage 17 前 | 最小化单线程 task queue（nop-stream 原生，非 Flink MailboxProcessor 移植） |
| D3 | 分布式 RPC 传输选型 | Stage 39 前 | IMessageService（与现有体系一致）/ gRPC / Akka |
| D4 | OperatorState 分发模式 | 已落地 | UNION/BROADCAST/SPLIT_DISTRIBUTE 已实现 |
| D5 | Timer checkpoint 策略 | Stage 15 前 | 全量 checkpoint（先正确再优化） |
| D6 | 是否引入 Guava 依赖 | Stage 53 前 | 添加 Guava（与 Flink CEP 一致） |
| D7 | ClusterRegistry 取舍 | 已落地 (Stage 41) | 对接共存（Option B）：ClusterRegistry 留作 runtime source of truth，平台 discovery 提供跨系统可发现性（写 `StreamNodeAutoRegistration` + 读 `NodeDiscoveryConsistencyChecker`）；完全替换被拒（blast radius 超 single-plan） |
| D8 | leader elector 后端 | Stage 38 前 | JDBC（零基建）vs Nacos/Zookeeper |
| D9 | 数据面 IMessageService 默认后端 | Stage 40 前 | SysDaoMessageService（DB）vs Pulsar |

## Follow-up Backlog

> P2/P3 findings not warranting a dedicated stage. Source audit paths preserved for traceability.

### `source-anchors.md` has zero nop-stream entries

- **Source**: `ai-dev/audits/nop-stream-flink-comparison/2026-07-24-2227-multi-audit-nop-stream-flink-comparison.md` (P2)
- **Description**: `source-anchors.md` (193 lines) has zero matches for "nop-stream" or any major nop-stream class.
- **Recommendation**: Add anchor entries in Stage 22.

### `CheckpointMetricsSnapshot.toString()` omits `failureCause`

- **Source**: 同上 (P2)
- **Recommendation**: Stage 23 附带修复。

### `WindowOperator` has empty else blocks

- **Source**: `2026-07-24-2227-open-audit-nop-stream-flink-comparison.md` (P2)
- **Recommendation**: Stage 23 附带清理。

### `OperatorChain.open()` javadoc contradicts implementation

- **Source**: 同上 (P2)
- **Recommendation**: Stage 23 附带修复。

### `PartitionPolicy` enum values `UNION` and `SINGLETON` are dead code

- **Source**: 同上 (P2)
- **Recommendation**: Stage 23 附带处理。

### 08-gap-analysis P2 计数不匹配

- **Source**: `08-gap-analysis.md` Priority Summary
- **Description**: 声称 P2=43，显式列出仅 31 条（G28-G58）。
- **Recommendation**: 修复 08 分类计数；不影响本路线图执行。

---

> 以下 P2 findings 来自 2026-07-25/26 nop-stream-production 审计轮（multi-audit + open-audit），不单独建 plan，按 mission-driver 规则归入 backlog。每条保留 source audit 路径以保可追溯。

### nop-stream-flow 依赖 nop-stream-cep（与 README §1.4 / architecture-baseline §2 矛盾）

- **Source**: `ai-dev/audits/nop-stream-production/2026-07-25-1948-multi-audit-nop-stream-production.md` [P2-1]
- **Description**: `nop-stream-flow/pom.xml:20-23` 依赖 `nop-stream-cep`（被生成 `_StreamModel.java:104` 的 `CepPatternModel` 使用）。文档说 `flow → core`。
- **Recommendation**: 更新文档或解耦。

### nop-stream/src/ 重复源码树（60 文件，pom-parent 下不编译）

- **Source**: 同上 [P2-2]
- **Description**: `nop-stream/src/main/java/io/nop/stream/flow/model/` 是重复源码树（git-tracked），30 个 `_gen` 文件已偏离规范副本。
- **Recommendation**: `git rm -r nop-stream/src/`。

### 公共算子接口 Javadoc 引用不存在的类型

- **Source**: 同上 [P2-3]
- **Description**: `StreamOperator.java:28-31`、`OneInputStreamOperator.java:24-26`、`Input.java:28-35` Javadoc 引用 `TwoInputStreamOperator`/`MultipleInputStreamOperator`/`AbstractStreamOperatorV2`/`AbstractInput`（vision §4 Non-Goals）。
- **Recommendation**: 修正 Javadoc。

### CheckpointedSourceFunction Javadoc 说「未使用」但生产实际调用

- **Source**: 同上 [P2-4]
- **Description**: `CheckpointedSourceFunction.java:14-19` 说「API 预留，当前未被使用」，但 `StreamSourceOperator.java:296-302,321-332` 调用其 `snapshotState`/`initializeState`。
- **Recommendation**: 修正 Javadoc。

### DataStream API 强转 UnknownTypeInformation 为 Class<R>

- **Source**: 同上 [P2-5]
- **Description**: `DataStreamImpl.java:135-186` 等 6+ 入口把 `UnknownTypeInformation.INSTANCE`（`<?>`）强转为 `TypeInformation<R>`，传播 `Object.class`。
- **Recommendation**: 仅通过 `TypeInformation<?>` 暴露 `UnknownTypeInformation`。

### IWindowOperatorFactory performative Class<...> 参数

- **Source**: 同上 [P2-6]
- **Description**: `WindowedStreamImpl.java:184-242` 总传 `(Class<T>)(Class<?>)Object.class`，工厂仅用于建 dummy serializer。
- **Recommendation**: 改用可选 `TypeSerializer<...>`。

### CheckpointCoordinator.onCompletePersistFailure 重复日志

- **Source**: 同上 [P2-7]
- **Description**: `:579-590` 同一失败信息 ERROR（582）+ WARN（589）记两次。
- **Recommendation**: 去重，影响 failure-rate 指标。

### Lockable.release 抛裸 IllegalStateException

- **Source**: 同上 [P2-8]
- **Description**: cep sharedbuffer `Lockable.release:54-79` 引用计数下溢抛裸 `IllegalStateException`，绕过平台异常体系。
- **Recommendation**: 改 `StreamException`/`NopException`。

### TestCountTrigger 仅测 canMerge() 返回 false

- **Source**: 同上 [P2-9]
- **Description**: `TestCountTrigger.java:1-15` 无 `onElement` 边界测试。
- **Recommendation**: 补 count=max-1 CONTINUE vs count=max FIRE。

### TestCheckpointBarrier 纯 getter/setter 往返

- **Source**: 同上 [P2-10]
- **Recommendation**: 评估删除或补序列化保真测试。

### TestTaskStateSnapshot 等纯 map put/get 往返

- **Source**: 同上 [P2-11]
- **Description**: `TestTaskStateSnapshot`/`TestOperatorSnapshotResult`/`TestCompletedCheckpoint` 无序列化保真测试。
- **Recommendation**: 批量清理。

### TestCheckpointType 断言枚举数量与 getName 常量

- **Source**: 同上 [P2-12]
- **Description**: 已 `@Tag("low-value")`，应删除。
- **Recommendation**: 删除。

### TestProcessingGuarantee 冗余于 TestInputGateProcessingGuarantee

- **Source**: 同上 [P2-13]
- **Recommendation**: 删除。

### TestJobTerminationContext 工厂字段断言，已被 TestFingerprintAndTerminationMode 覆盖

- **Source**: 同上 [P2-14]
- **Recommendation**: 删除。

### TestCheckpointIDCounter 仅测 AtomicLong 语义，无并发测试

- **Source**: 同上 [P2-15]
- **Recommendation**: 删除或补并发测试（唯一真实风险）。

### TestWindowOperatorBasic 测 TimeWindow 几何原语，文件名误导

- **Source**: 同上 [P2-16]
- **Recommendation**: 重命名或删除。

### TestSharedBuffer 过度用 assertNotNull

- **Source**: 同上 [P2-17]
- **Recommendation**: 用具体 EventId 值断言。

### TestNFAState equals/hashCode 镜像测试

- **Source**: 同上 [P2-18]
- **Description**: 仅 `testNotEqualWhenMatchesDiffer` 有真实保护。
- **Recommendation**: 精简。

### StreamExecutionEnvironment 文档归 datastream，实在 core/environment

- **Source**: 同上 [P2-19]
- **Recommendation**: 更新 README §1.2。

### cep 文档说依赖 nop-xlang，实际依赖 nop-core

- **Source**: 同上 [P2-20]
- **Description**: `IEvalFunction` 来自 `nop-core`（`io.nop.core.lang.eval`）；component-roadmap §2.1 与 §2.5 内部矛盾。
- **Recommendation**: 更新文档。

### flow 文档说只依赖 core，实际依赖 cep + xdefs

- **Source**: 同上 [P2-21]
- **Recommendation**: 更新 README §1.2/§1.4。

### ResultPartition.close() bufferPool permit double-release race

- **Source**: `ai-dev/audits/nop-stream-production/2026-07-25-1948-open-audit-nop-stream-production.md` [AR-5]
- **Description**: `close()` 的 `queue.size()` 与 `queue.clear()` 之间消费端并发 release 导致 permit 过释放（区别于 multi-audit P1-10 的数据丢失角度）。
- **Recommendation**: 逐元素 drain+release，或让 `close()` 不 release permit 改由 `BufferPool.close()` 兜底。

### JobGraphGenerator determinePartitionType javadoc 错位

- **Source**: 同上 [AR-6]
- **Description**: `:509-518` javadoc 描述 `determinePartitionType` 却挂在 `hasNonVirtualOperator`（`:523`）上；`determinePartitionType`（`:546`）无 javadoc。
- **Recommendation**: 移动 javadoc 块。

### PartitionPolicy.UNION / SINGLETON 死枚举值

- **Source**: 同上 [AR-7]
- **Description**: 生产代码从不产生这两个值（0 引用）。
- **Recommendation**: 删除或加 `@ReservedForFutureUse`。

## References

- `ai-dev/analysis/nop-stream/08-gap-analysis.md` — 73 gap 条目的完整分类表
- `ai-dev/backlog/completion-roadmap.md` — Phase 0—5 战略框架
- `ai-dev/design/nop-stream/checkpoint-design.md` — Checkpoint 协议权威
- `ai-dev/design/nop-stream/state-management-design.md` — 状态后端设计
- `ai-dev/design/nop-stream/01-architecture-baseline.md` — 架构基线与执行管线定义
- `ai-dev/backlog/nop-stream-flink-comparison-roadmap.md` — 前序路线图（Items 1—13 已完成）
- `.opencode/skills/mission-driver/references/roadmap-template.md` — roadmap 结构模板
