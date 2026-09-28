# R3 可读性/结构发现（2026-09-29）

> 方法：双解析器方法长度扫描 + 全仓库文本零引用扫描（java/xml/xpl/xmeta/md/json/yaml，防反射误判）+ n-gram 测试重复检测。G1/G2/G3 整体拆分与 G5/G6/G10/G12/G14/G16/G18 已知 Deferred 项不重复报告。

## P1

### P1-1 三个"声明了但从未接线"的死特性面（注释/文档声称的语义与实际不符）

1. **RemoteResultPartition 心跳**（`RemoteResultPartition.java:270,284,305,341`）：`startHeartbeat` 全仓库零引用（唯一启动路径），`sendHeartbeatIfIdle` 仅被它和测试驱动；接收端无 `CONTROL_HEARTBEAT` 处理分支。`close():219-223` 注释详细描述"心跳与 EOS 排序契约"——生产不可能发生的场景。~60 行死代码 + 误导注释。
2. **TTL 后台清理**（`TtlContext.java:234` `sweepExpired` 零引用；`:163` `expiredKeys` 仅被它调用；`TtlCleanupStrategy.java:38` `backgroundCleanup` 标志接受参数、暴露 getter，但无任何后台清理器）。类 javadoc 与 `ai-dev/design/nop-stream/state-management-design.md` 声称支持 background cleanup——实际只有 lazy eviction。
3. **processWatermarkStatus1/2**（`AbstractStreamOperator.java:464,468`，public final 零主源调用，仅一个测试直调 status2）。主审核实 ✅：代码库不存在任何 TwoInput 算子（grep 零命中），任务分发 `StreamTaskInvokable:1016-1017` 只路由 `headInput.processWatermarkStatus`——双输入 WatermarkStatus 组合逻辑是为不存在的模型预留的死面。

### P1-2 零引用 public 顶层类 ×10

core：`checkpoint/SourceEnumeratorState`、`common/state/VoidNamespaceSerializer`（VoidNamespace 本体在用，serializer 零引用）、`connector/SourceWorkUnit`、`connector/DynamicSplitRequest`、`connector/DynamicSplitResponse`、`connector/RestrictionTracker`（FLIP-27/Beam 借鉴遗留）；runtime：`coordinator/TaskAssignmentMessage`；cep：`NopCepConstants`、`RichPatternFlatSelectFunction`、`RichPatternSelectFunction`。其中 5 个自 2026-05-31 api-surface 审计起滞留三轮整改。

### P1-1 附：plan-01 新增注释考古债

15 处 `(plan 01 quality-perf)` 编号注释分布 13 个 main 源文件（`NFA.java:722`、`JdbcTwoPhaseCommitSink.java:306`、`FileTwoPhaseCommitSink.java:522`、`FileSource.java:172`、`MemoryInternal{Appending,List}State.java:70/50`、`RocksDBInternal*State.java`×3、`RocksDBKeyedStateBackend.java:439`、`CloseSupport.java:14`、`CheckpointCoordinator.java`、`WindowOperator.java`）。`MemoryInternalAppendingState:72-76` 叠加考古句式并把 rationale 跨文件复制到 `MemoryInternalListState:50-51`。

## P2

### 结构/长方法

- `TaskManager.deployTask`（:483-623，141 行，新发现）：7 个守卫块重复"构造 NopException → reportDeployFailure → throw"；`reportDeployFailure`/`reportAssignmentFailure`（:630-663 vs :664+）20 行双胞胎；局部变量 `dJobId`/`aJobId` 前缀含义不明。
- `MaxParallelismReshardMigration.reshardVertexStates`（:171-296，126 行 7 参，新发现）：pool 构建/键计数/重分布/快照组装四阶段可拆。
- `TaskCheckpointWiring.java`（67-372）：全文件方法体零缩进（违反 4 空格约定，2278 拆出时未格式化）；`:160-188` vs `:209-225` wire/unwire 4 组 instanceof 镜像重复。
- G10 残余：3 个 `executeWithCheckpoint` 重载已路由共享 skeleton，`triggerSavepoint:296`/`executeWithSavepoint:357` 仍各持 prologue。

### 重复（新近引入）

- `FileSource.java`：B6 修复把保留字符校验复制成第 3 份（`:113` split serializer、`:178` enumerator state serializer 近逐字、`:199` appendCsvSection 变体）；4 字段 split 编码 `:117-118` vs `:182-185` 重复。可提取 `validateReservedChars` helper。
- `RemoteTaskDeploySupport.java:163-173` provisionStateBackends 与 `TaskCheckpointWiring.wireTaskCheckpointPipeline:177-188` 镜像，且存在**行为分歧**（remote 恒用 MemoryStateBackend，local 先取 checkpointConfig）——javadoc "mirror" 掩盖分歧，建议 owner 核实语义。

### 死常量与未注册指标

- `CheckpointCoordinator.java:253-254`：`DEFAULT_COMMIT_RETRIES`、`CONSECUTIVE_FAILURE_THRESHOLD` 零引用（提交重试/连续失败阈值无实现挂钩）。
- `JobCoordinator.java:92`：`DEFAULT_LEASE_EXPIRE_THRESHOLD_MS` 零引用。
- `CepOperator.java:109`、`WindowOperator.java:191`：`LATE_ELEMENTS_DROPPED_METRIC_NAME="numLateRecordsDropped"` 两处零引用——迟到元素丢弃指标从未注册从未递增（观测缺口 + 死常量）。

### 说谎/失实注释

- `JobCoordinator.java:80`：javadoc 称 `triggerCheckpoint()` "sends CheckpointBarrierSignal to all source tasks"；实际走 `tryTriggerPendingCheckpoint` + barrier scheduler；`CheckpointBarrierSignal` 类本身零引用（死类）。
- `TaskManager.java:470` 引 `#24`、`:550-554` "The legacy code re-acquired… That extra acquire is removed"——考古型。
- `Task.markFailed:354/markScheduled:413/markRecovering:420`：javadoc 称 "Used by external orchestrators (e.g. JobCoordinator)"，实际零调用。
- `JobCoordinator.java:1497-1511,1281-1283`：recoveryPending 清除时点失实（见 summary R4-S2）。
- `MemoryKeyedStateBackend.java:425-433 vs 439-446`：两段缓存安全说明 ~90% 逐字重复。

### 单方法死 API（全仓库 grep 零引用核实）

`StreamElement.asLatencyMarker:132`；`HeapInternalTimerService.nextProcessingTimeTimer:222`（方法与同名字段并存易混淆）；`PatternStream.sideOutputLateData:70`；`StreamConnectors.fromBatchLoader×2/toBatchConsumer×2`（桥接门面零调用；模块内 BatchLoader/BatchConsumer 本体经 SPI 注册不是死代码）；`Task.markFailed/markScheduled/markRecovering`；`JobCoordinator` 死访问器 ×6（getLeaderElector:2133、getTaskTimeoutMs:2213、isAutoRecoverOnFailedReport:2227、getMaxStallRestarts:2256、getStallRecoveryCooldownMs:2264、getCheckpointStoragePath:2337）；`EngineMetrics` 死 getter ×4（getCompletedCount 有测试调用，删时同步测试）+ `TaskNodeMetrics` ×3；`MemoryBudget.getTotalBytes/getComponentAllocations:44-45`；`WindowedStreamImpl.getEvictor:128`。
**勘误（2026-09-29 plan 366 R1 审查发现）**：初稿误列 `CheckpointSerDe.stringToTaskLocation:706`（同类 :187/:580 在用）与 `JobCoordinator.stopPeriodicCheckpoints:1067`（shutdown:625 在用）——两者非死代码，不删，可评估 public→包私有收敛。

### 测试卫生

- `TestAwait.java`（74 行）在 core/runtime/connector/connector-batch/connector-debezium 5 个模块逐字副本。
- windowing 测试家族互抄（14+ 行块数 37-44）：TestTimerCheckpointRestoreE2E ↔ TestWindowOperatorBehavior/Integration/AccType/MapStateFallbackWriteBack——最后者是 plan-01 新增 A12 回归（298 行，第 5 份复制）。
- 21 个测试文件各自内嵌 `IStreamTaskRpcService` stub（16 类、7 种命名）；plan-01 新增 TestJobCoordinatorRecoveryPendingCleanup:133 注释自认 "same shape as sibling" 却复制第 21 份。
- `TestDebeziumCdcSourceCompletion.java:26` `@Disabled("Genuinely broken: … impossible by design")` 失效测试长期滞留。
- Kafka/Pulsar codec 测试互抄 42 块；TestCoordinatorRpcControlPlane 与 3 个 JobCoordinator 测试互抄 35-37 块。

## 已知项在 HEAD 状态核对

- **已消失** ✅：G7/G17/G8 删除确认（grep=0）；G9 归位；G4/G11/G13/G15 落地。
- **部分消失**：G10 skeleton 路由已做（残 2 prologue）；G3 `*ForTesting` 从 ~80 行降到 5 处。
- **仍在**：G5 四份 invoke 脚手架（:652/742/800/843）；G6（open 150 行/mergeWindowContents 145 行）；G12 serde 双子（783/679 行）；G16（AdvancedTransforms:249-263）；G18（RecordWriterOutput:1048/BroadcastingRecordWriterOutput:1132 内嵌）；H 组 Debezium `:189` 恒真 `if (!draining)`。
- 主源码注释掉的代码块：0（干净）。
