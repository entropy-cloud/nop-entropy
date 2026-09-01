# nop-stream-core 模块审计（roadmap item 7，Phase M 首项）

> Status: resolved
> Date: 2026-09-01
> Scope: `nop-stream/nop-stream-core/` 全模块（364 main / 214 test Java 文件，live 核对于 worktree 根）：2026-05-20 duplicate-code audit 与 2026-06-30 code audit 的 core 相关组整改收口验证 + 产品化视角新增审计（D-GAP core 重点 / Flink-Beam 补评裁定 / 优雅性可靠性 / 空壳扫描 / 测试覆盖抽查）+ 小缺陷就地修复与大缺陷 Follow-up 化
> Conclusion: 两轮历史审计 core 相关发现整改收口**基本成立**（05-20 九组：6 组已清除/收编、2 组部分残留、1 组落地方式变化；06-30 seed+派生：大部分 landed，4 项 partial）；产品化审计发现小缺陷 15 项全部就地修复（10 项行为修复各配 focused 测试 + 5 项纯清理/合并经既有直接测试或编译验证）、大缺陷/治理项 4 项转 Follow-up items 21—24；D-GAP core 三项重点全部消化并产出 item 17 可引用证据；Flink/Beam 8 个低置信格 Phase M 级裁定：全部无需补评；hollow scan 6 个 high 发现全部核实为 guard 模式误报，工具 P1 规则已按消息语义分级修正；`./mvnw test -pl nop-stream -am -T 1C` 全绿
> Source: `ai-dev/backlog/nop-stream-productization-roadmap.md` item 7；plan `ai-dev/plans/nop-stream-productization/2026-09-01-0938-2-core-module-audit.md`
> Related: `2026-05-20-nop-stream-duplicate-code-audit.md`、`2026-06-30-nop-stream-code-audit.md`、`2026-08/2026-08-06-nop-stream-audit-baseline-and-roadmap-analysis.md`、`2026-09/2026-09-01-nop-stream-design-productization-gap-analysis.md`（D-GAP，item 6 产出）、`2026-09/2026-09-01-competitor-productization-synthesis-and-p-req.md`（P-REQ 矩阵）

## Context

- roadmap item 7（Phase M 首个审计项，deps item 6 已 done）：对 `nop-stream-core` 按产品标准完成模块审计并收口。前置 D-GAP 报告（item 6）已提供 core 审计重点三项（§3.1），本报告逐条消化。
- 审计方法沿用 2026-08-06 baseline 的证据分级（live 锚点优先于历史 plan 状态）；重复代码复查对照 05-20 方法论（import/引用计数 + 文件比对 + 零引用检测）。
- 所有 live 核对命令于 2026-09-01 在 worktree 根执行，`--glob '!target'` 排除生成物；两个独立 explore subagent（pipeline/windowing、state/checkpoint）完成核心路径优雅性审查，其高严重度发现均经本报告执行者逐条源码复核后才采信（其中 2 项经复核被否决，见 §2.3）。

## Phase 1 — 历史审计整改收口验证

### 1.1 2026-05-20 duplicate-code audit core 相关组核对表

> 状态判定：`已清除` = 文件/包已删除；`已收编` = 原死代码被生产接线启用；`部分残留` = 部分保留；`回潮` = 重新引入。§2（CepOperator，主体 cep、runtime 侧归 item 9/8）与 §3（runtime 死代码，归 item 8）**非本 plan 范围**，不在表内核对。

| 组 | 05-20 发现（时点） | live 状态 | 证据锚点 |
|---|---|---|---|
| §1 operator vs operators 包级重复 | 单数包 3 文件 + 双 StreamOperator 接口 + ChainingStrategy 双定义 | **已清除**（统一到 `operators` 复数包） | `ls nop-stream-core/.../core/operator/` → 目录不存在；`StreamOperatorFactory.java`/`SimpleStreamOperatorFactory.java`/`ChainingStrategy.java` 均在 `core/operators/`；单一 `StreamOperator` 接口（`rg "class StreamOperator" -g '*.java'` 唯一定义） |
| §4.1 图执行路径死代码（JobGraphGenerator 474 行 / StreamGraphGenerator 413 行） | 零生产引用，仅测试使用 | **已收编**（成为生产执行路径核心层） | `StreamExecutionEnvironment.execute()` 生产接线：`environment/StreamExecutionEnvironment.java:282-296`（`graphGenerator.generate(sinkList)` → `jobGraphGenerator.generate(streamGraph)` → `PartitionedPlanGenerator` → `generateDeploymentPlan`）；`GraphModelCheckpointExecutor.java`（runtime main）引用两者 |
| §4.2 未使用 Trigger/Evictor（6 文件 795 行） | 零引用死代码（含 ContinuousEventTimeTrigger/ContinuousProcessingTimeTrigger/ProcessingTimeoutTrigger/DeltaTrigger/TimeEvictor/DeltaEvictor） | **已收编（API surface + 行为测试）**：`ContinuousProcessingTimeTrigger` 被 runtime main 引用（`runtime/operators/windowing/WindowOperator.java:256,955`）；其余 5 类主代码仍无生产调用方，但**每类均有专属单测**（`core/windowing/triggers/Test{ProcessingTimeout,ContinuousEventTime,Delta}Trigger.java`、`evictors/Test{Time,Delta}Evictor.java`）+ 集成测试（`TestTimeEvictorIntegration`、`TestEvictorIntegration`），从「零引用零测试死代码」变为「Flink 兼容 API 预留 + 行为级证据」（item 17 触发语义映射表直接消费，见 §2.1） |
| §4.3 未使用 Accumulator（7 文件 614 行） | 零引用 | **部分残留（API 预留 + 测试）**：`Accumulator` 接口已被生产接线（`CountTrigger`/`ContinuousProcessingTimeTrigger`/`ProcessingTimeoutTrigger` 内部状态，`windowing/triggers/CountTrigger.java` 等 main 引用）；具体实现类（AverageAccumulator/IntMaximum 等）仍无 main 调用方，但 `TestAccumulators.java` 全覆盖 + `TestStateSchemaResolver.java` 消费 LongMaximum。归类：非缺陷（Flink 兼容 API surface），无需处置 |
| §4.4 未使用 Function 接口（TwoPhaseCommitSinkFunction/CoMapFunction/CheckpointedSourceFunction，179 行） | 零引用 | **已收编/已清除**：`TwoPhaseCommitSinkFunction` 深度生产接线（`runtime/checkpoint/CheckpointPlanBuilder.java`、`core/operators/StreamSinkOperator.java`、`connector/file/FileTwoPhaseCommitSink.java`）；`CheckpointedSourceFunction` 由 `connector-debezium/DebeziumCdcSourceFunction.java` 实现、`StreamSourceOperator` 引用；`CoMapFunction` 及 `common/functions/co/` 包已删除（`ls` 目录不存在，`rg CoMapFunction` 零命中） |
| §4.5 其他（StreamConstants.java 11 行；execution/jobgraph package-info） | 零引用 | **部分残留**：`StreamConstants.java` 为空类零引用（`rg -l StreamConstants` 排除自身零命中）→ **小缺陷 S-7 删除**；package-info 为包文档属正当保留 |
| §5 TimerService 实现重复（core 侧） | 接口层裁定非重复（core.time vs cep.time 定位不同）；实现层重复位于 runtime | **落地方式变化 + 发现缺陷**：core 侧现状 = `core.time.TimerService` 成为 ProcessFunction 用户面 API（`operators/ProcessOperator.java:49,138` 以 `InternalTimerServiceTimerWrapper` 包装 `HeapInternalTimerService` 实现；`common/functions/ProcessFunction.java` Context.timerService() 签名）。但该接口被标 `@Deprecated @Internal` 且 Javadoc 声称 "Not used within nop-stream"（`core/time/TimerService.java:26-30`，commit `591477284f` 引入）——**与 live 事实矛盾**（3 个 main 文件 import 使用）→ **小缺陷 S-8 修正注解与 Javadoc**。实现层（SimpleInternalTimerService 删除与否）归 item 8 |
| §6 孤立图执行路径（StreamGraphGenerator→StreamGraph→JobGraphGenerator→JobGraph→TaskExecutor→Task，1,836 行） | 生产代码从未使用，实际执行走 chain+push | **已收编**（五层管线成为唯一执行路径） | `StreamExecutionEnvironment.execute()` 全链接线（同 §4.1 锚点）；`TaskExecutor` 被 `runtime/execution/SupervisionLoop.java:750`、`runtime/taskmanager/TaskManager.java` 生产引用。05-20 时点的「两条执行路径」已收敛为一条 |
| §7 空壳模块（api/checkpoint/flink/flow 四模块仅 pom） | 4 空壳 | **已清除/已实现**：`ls nop-stream/` 现为 10 模块——api/checkpoint/flink 三模块**已删除**（reactor 不含）；`nop-stream-flow` **已实现**（XDSL 声明式编排 + Delta 定制，`flow/src/main/.../builder/`、`flow/model/StreamSideOutputModel.java` 等）。模块级结论（items 8—11 引用）：05-20/06-30 的空壳模块问题整体收口，勿重复立项 |
| §8 core/state vs core/common/state 包分裂 | 4 工具类分裂在 core.state | **已清除**：`core/state/` 目录不存在；`Keyed.java`/`PriorityComparable.java`/`PriorityComparator.java`/`KeyExtractorFunction.java` 全部位于 `core/common/state/`（`find` 锚点） |
| §9 core/sink 单文件包（PrintSink） | 单文件包 | **已清除**：`core/sink/` 目录不存在；`PrintSink.java` 位于 `core/operators/` |

**结论**：05-20 core 相关组无回潮；两项部分残留中 §4.3 裁定为正当 API 预留，§4.5 的 StreamConstants 转入 Phase 3 修复（S-7）。

### 1.2 2026-06-30 code audit core 相关发现核对表

> 先列全表再逐项核对。seed 清单 = plan Current Baseline 所列；派生规则 = 报告中「涉及文件位于 nop-stream-core」的其余发现。三态：landed / partial / regressed。

| # | 06-30 发现（章节） | 涉及 core 文件 | live 三态 | 证据锚点 |
|---|---|---|---|---|
| 1 | §6.1-1 必须修复：InputGate Optional 返回 null ×6（:320,336,342,353,386） | execution/InputGate.java | **landed** | `rg "return null" InputGate.java` 零命中；全部路径 `Optional.empty()`（:446,457,478,482,563,568,580,603,615,743,754 等） |
| 2 | §6.1-2 必须修复：WindowedStreamImpl 废弃 API 回退默认路径（:168） | datastream/WindowedStreamImpl.java | **landed** | 废弃路径整体删除：`WindowAggregationOperator`/`WindowAggregationFunction` 文件不存在（`find` 零命中）；现路径 = ServiceLoader 风格发现 `IWindowOperatorFactory`（:148-170）+ runtime 缺失时 **fail-fast**（:190 `throw new StreamException("WindowOperator requires nop-stream-runtime on classpath...")` ×4 处） |
| 3 | §6.1-3 必须修复：DataStream API 窗口聚合实际路径/WindowOperatorFactory 注册验证 | model/StreamComponents.java | **landed** | `runtime/operators/windowing/WindowOperatorFactoryImpl.java`（main）实现工厂；core 侧 `TestWindowedStreamFactoryFailFast.java` 验证 fail-fast 语义；`TestWindowOperatorUnificationE2E`（runtime）验证统一路径 |
| 4 | §2.1 UOE 桩：`forceNonParallel()` | datastream/SingleOutputStreamOperatorImpl.java | **landed** | :53 真实现（`transformation.lockParallelismToOne()`）；全链接线：`Transformation.lockParallelismToOne` → `StreamGraphGenerator:486-504` → `JobGraphGenerator:393` → `GraphExecutionPlan:521`；`datastream/TestForceNonParallel.java` + `TestParallelismLockedPropagation.java` |
| 5 | §2.1 UOE 桩：`ICheckpointExecutorFactory.executeWithCheckpoint(StreamModel)` | execution/ICheckpointExecutorFactory.java | **landed** | 接口 default 方法保留显式 UOE（:77-83，Rule #24 合规的 default-fallback）；runtime `CheckpointExecutorFactoryImpl.java:51-65` 覆写全部三档重载并委托 `GraphModelCheckpointExecutor.executeWithCheckpoint`；生产调用点 `StreamExecutionEnvironment.java:297` |
| 6 | §2.2 通配符导入 78 文件（main+test 混合） | core 内文件 | **partial（main 已清零，test 残留）** | main：全部 10 模块 `rg "import .*\.\*;" src/main` **0 命中**；test：core 169 / runtime 108 / cep 15 / flow 1 文件残留。归类：main 侧 landed；test 侧为代码风格治理项（非 live defect）→ **Follow-up item 22**（跨模块统一 sweep，单 plan 修 169 个测试文件违反最小 diff 原则且 items 8—11 将重复同类工作）。注：root pom `maven-checkstyle-plugin` 配置整体被注释（pom.xml:138-166），AvoidStarImport 规则实际未在构建期强制——已在报告 Conclusion 记录 |
| 7 | §2.2 InputGate 硬编码轮询（parkNanos 10ms）/对齐超时（30s 不可配） | execution/InputGate.java | **landed** | 对齐超时：`CheckpointConfig` 全链下发（:89-91 注释 + `GraphExecutionPlan.java:448` 4 参构造），常量仅剩 legacy 构造器兜底；轮询：重设计为 AR-02 有界 idle-drain（`IDLE_RETURN_THRESHOLD_MS=250ms` 契约注释 :69-87，parkNanos(10ms) 仅为有界等待的睡眠粒度），伴随专属测试 `TestInputGateAlignmentTimeout`/`TestInputGateAlignmentStarvationFix` |
| 8 | §6.2 execution 包 26 文件膨胀（建议拆分） | execution/ | **partial（子包化进行中）** | 现结构：根 31 文件 + `buffer/`、`flow/`、`materialization/`、`plan/`、`transport/` 五个子包（06-30 时仅 transport/plan 雏形）；建议的 `execution.runtime`（Task/SubtaskTask/TaskExecutor 下沉）未做。归类：认知负荷治理项非缺陷 → **Follow-up item 23**（移动 Task 族类 = 跨模块 import 变更） |
| 9 | §6.2 ShardPrefixedKey 重复（state.shard vs state.backend.memory） | common/state/ | **partial（已分化为两角色 + 仍近重复）** | 两类并存：`shard/ShardPrefixedKey.java`（public，key-group 路由，被 PartitionRouter/GraphExecutionPlan/rocksdb 消费）与 `backend/memory/ShardPrefixedKey.java`（package-private，仅 MemoryKeyedStateBackend/MemoryStateSerDe 的 HashMap key）；equals/hashCode 逐字段相同；且 public 版 Javadoc 声称 "Used internally by MemoryKeyedStateBackend" 而 memory 后端实际用的是私有副本（stale 文档）。归类：**小缺陷 S-1 合并**（Phase 3，序列化路径不受影响——MemoryStateSerDe:766 在序列化前解包 `.key`，类名不入持久化格式） |
| 10 | §6.3 P0 测试缺口：Operator State e2e | core | **landed** | `IOperatorStateStore`/`DefaultOperatorStateStore`（main）+ `checkpoint/TestE2EOperatorStateCheckpoint.java`、`TestE2EOperatorStateRedistribution.java`（core e2e）+ `operators/TestOperatorStateWiring.java` |
| 11 | §6.3 P1 测试缺口：分支/合并多链管线 | core | **基本 landed（union 语法糖残留）** | 分支：`integration/TestFanOutBoundedE2E.java` + SideOutput 体系（`streamrecord/SideOutputElement.java`、`runtime/TestSideOutputChainingE2E.java`）；多输入：`InputGate` 多通道 + `common/eventtime/TestWatermarkMultiInputCombineWire.java` + `runtime/TestUnalignedCheckpointMultiInput.java`；残留 = `DataStream.union()` 便捷 API 不存在（`rg union datastream/DataStream.java` 零命中）。归类：union API 为新功能非缺陷 → watch-only residual（记录于本报告，不立项） |
| 12 | §6.3 P2 测试缺口：execution.flow/plan/transport 单测 | core | **unchanged（各 1 个，低优先级维持）** | `execution/flow/TestFlowControl.java`、`execution/plan/TestPlanModels.java`、`execution/transport/TestStreamElementCodec.java` 各 1（与 06-30 时点相同，报告原文即标注「低」）。归类：watch-only |
| 13 | §6.3 P3 测试缺口：configuration/streamrecord/time 单测 | core | **not landed（低优先级维持）** | `src/test/.../configuration|streamrecord|time/` 目录均不存在（`find` 零命中）；间接覆盖存在（Configuration 被 e2e 广泛使用）。归类：watch-only（06-30 原文严重度「低」） |
| 14 | §1.2 TimerService 名称冲突（core.time @Deprecated vs cep.time 活跃） | core/time/TimerService.java | **partial → 转修复** | 见 §1.1 §5 行：接口实为 ProcessFunction 用户面 API 却被误标弃用 → **小缺陷 S-8**；cep.time.TimerService:34 Javadoc 引用 core 版（`{@link io.nop.stream.core.time.TimerService}`）——注解修正后该引用语义恢复正确 |
| 15 | §1.2 execution.transport vs runtime.transport 边界模糊（低） | core | **unchanged（watch-only）** | core `execution/transport/`（StreamElementCodec/StreamMessageEnvelope/TypeRegistry）与 runtime `transport/`（RemoteResultPartition/RemoteInputChannel）分工维持；06-30 原文严重度「低」且判定为组织问题非缺陷 |
| 16 | §1.2 checkpoint 包散布 core/runtime（模型 vs 执行分离，判定合理） | core | **unchanged（合理维持）** | `CheckpointPlan` 在 core、`CheckpointPlanBuilder` 在 runtime——06-30 原文已判定「分离合理」 |
| 17 | §2.3 已废弃 API 使用（WindowedStreamImpl 回退） | 同 #2 | **landed**（同 #2） | — |
| 18 | §3.2 common.functions.co 零测试（高，但属规划不实现功能） | core | **已清除** | CoMapFunction 及 co/ 包已删除（§1.1 §4.4 行） |
| 19 | §3.2 windowing.delta/utils 零测试（低） | core | **landed** | delta：TestDeltaTrigger/TestDeltaEvictor；windowing/utils：`windowing/utils/` 目录 `ls` 存在且 trigger/evictor 家族测试全覆盖（10 个公开 Trigger/Evictor 类型均有专属单测，见 §2.1） |
| 20 | §2.4 `_gen` 生成代码 | core 无 | **n/a** | `_gen` 仅存在于 cep 模块（归 item 9）；core 无生成文件 |

**结论**：无 regressed 项；partial 项 4 个（#6 通配符 test 残留、#8 execution 包组织、#9 ShardPrefixedKey、#14 TimerService 注解）全部有归类：#9/#14 进 Phase 3 修复（S-1/S-8），#6/#8 转 Follow-up（items 22/23），#11/#12/#13/#15 watch-only。

### 1.3 2026-08-04-2300-1/2/3 remediation plans 收口复核（core 侧）

> 复核基准：P-REQ 综合报告 §2.3 #6 记录「三 plan 已由前序 production roadmap 收口」；本节验证该结论对 core 模块成立。

| Plan | core 侧修复点 | 复核结论 | 抽查证据 |
|---|---|---|---|
| 2300-1 coordinator-runtime-concurrency-recovery-hardening | `InputGate` 跨线程对齐状态；`NopStreamErrors` 错误码；`TestInputGateMailboxAbort` | **成立（landed）** | InputGate 对齐态改 `ConcurrentHashMap`（:110-117 P1 hardening 注释 + `import java.util.concurrent.ConcurrentHashMap`）；`NopStreamErrors.java` 83 个 ERR_STREAM 错误码；`execution/TestInputGateMailboxAbort.java` 存在 |
| 2300-2 checkpoint-state-backend-cep-correctness | **无 core 侧修复点**（Targets 全在 rocksdb/runtime/cep：RocksDBKeyedStateBackend/KeyEncoder、CheckpointCoordinator、JdbcCheckpointStorage、SharedBufferAccessor） | **不适用**（结论无需 core 侧复核；rocksdb/runtime/cep 侧归 item 8/11） | plan Targets 逐条 rg 核对 |
| 2300-3 contract-drift-config-test-integrity | core state SPI（IOperatorStateStore/KeyedStateStore/StateDescriptor）drift 收敛；core 空心测试（TestTaskExecutorDaemonThreads 删除 / TestSinkTransformation+TestOneInputTransformation 打标） | **成立（landed）** | TestTaskExecutorDaemonThreads.java 已删除（`find` 零命中）；TestSinkTransformation/TestOneInputTransformation 类级 `@Tag("low-value")` + Javadoc 说明（:23-28）；IOperatorStateStore 体系见核对表 #10 |

**结论**：「三 remediation plans 已收口」对 core 模块**成立**（2300-2 不适用）。

## Phase 2 — 产品化视角新增审计

### 2.1 D-GAP core 审计重点勾销清单（D-GAP 报告 §3.1 item 7 条目，逐条消化）

| # | D-GAP core 重点（来源 P-REQ） | 消化结论 | 证据 |
|---|---|---|---|
| ① | `SerializerFingerprint.schemaVersion` 恒 1 预留分支的 core 侧核对（类型定义与指纹传播路径，与 item 8 联动） | **核对完成：预留字段语义一致，无隐藏版本分支**。类型定义：`checkpoint/SerializerFingerprint.java:36` `DEFAULT_SCHEMA_VERSION=1`，构造器 :48 将非正值钳制回 1（`schemaVersion > 0 ? schemaVersion : DEFAULT`），Javadoc :27-29 显式声明「forward-looking metadata for Stage 33」。指纹传播路径：**唯一构造点** `common/state/StateSchemaResolver.java:132`（恒传 DEFAULT_SCHEMA_VERSION + 类型签名 checksum）→ 持久化经 `rocksdb/RocksDBSnapshotSerDe`、`runtime/checkpoint/storage/CheckpointSerDe`、`core/checkpoint/StateSegmentDescriptor`（segment 级 checksum/schemaVersion，P-REQ-20 四分项之 ③ landed 部分）→ restore 期比对消费方 `rocksdb/RocksDBKeyedStateBackend` + `core/common/state/StateMigrationRegistry`（不匹配 → `ERR_STREAM_STATE_SCHEMA_MISMATCH` fail-fast）。core 侧不存在任何 `schemaVersion != 1` 的行为分支（`rg getSchemaVersion` 消费点均为透传/持久化/相等比较） | 上述锚点；结论供 item 8（manifest 级 stateFormatVersion 方向裁定）引用：core 侧无阻碍，字段落地增量全在 runtime manifest 层 |
| ② | 窗口 Trigger 家族 11 类行为一致性审计，产出「可直接引用为 item 17 触发语义映射表的行为级证据」 | **产出行为级证据表（下文），一致性结论：通过**。11 类 = `windowing/triggers/` 10 文件（Trigger/TriggerResult/EventTime/ProcessingTime/Count/Delta/Purging/ContinuousEventTime/ContinuousProcessingTime/ProcessingTimeout）+ `operators/Triggerable`。行为一致性验证：10 个公开 Trigger/Evictor 类型**每类一个专属单测**（见 §1.1 §4.2 锚点）+ 语义接线矩阵（下表）全覆盖；发现的唯一行为差异（CETT 的 Long.MAX_VALUE 守卫 vs CPTT 无守卫）经源码推演为**语义等价**（CPTT 中 MAX_VALUE 时刻必先命中 `time == window.maxTimestamp()` 短路分支，fireTimestamp 守卫分支不可达），非缺陷（推演记录见 §2.3 F-2 复核） | **触发语义接线矩阵（item 17 直接引用）**：TumblingEventTimeWindows→EventTimeTrigger.create()（assigners/TumblingEventTimeWindows.java:92）；SlidingEventTimeWindows→EventTimeTrigger（:76）；EventTimeSessionWindows→EventTimeTrigger（:50）；TumblingProcessingTimeWindows→ProcessingTimeTrigger（:53）；SlidingProcessingTimeWindows→ProcessingTimeTrigger（:70）；GlobalWindows→内部 NeverTrigger（:39）；`KeyedStreamImpl.countWindow(size)`→`PurgingTrigger.of(CountTrigger.of(size))` + GlobalWindows（datastream/KeyedStreamImpl.java:172）；`countWindow(size,slide)`→`CountTrigger.of(slide)`+`CountEvictor.of(size)`（:177）；runtime WindowOperator 对 CountTrigger/ContinuousProcessingTimeTrigger 的 accumulator 状态与 clear 语义集成（WindowOperator.java:256,955-956）+ `TestWindowOperatorTriggerAccumulatorCleanup`。**作业级触发等价物**：`CheckpointConfig` 11 配置项（D-GAP §1.1 已锚点）+ processing-time timer（HeapInternalTimerService + TestHeapInternalTimerService 三件套）+ DRAIN truncation。行为级测试证据：triggers/ 8 测试 + evictors/ 3 测试 + TestWindowEndToEnd/TestWindowTranslation/TestWindowingModel/TestWindowOverflow/TestWindowJsonSerialization |
| ③ | core 侧 per-path skip-vs-fail 语义实现一致性（`ChannelState.fromSerializableForm` best-effort 跳过 + LOG.warn，为 P-REQ-17 defer 裁定提供实现级锚点） | **锚点确认 + 发现一处违反该语义的缺陷（S-2）**。设计语义实现：`checkpoint/ChannelState.fromSerializableForm:169-212` 三类 best-effort 跳过（malformed channel index :179-184 / 非 List 值 :186-189 / 不可解码 in-flight record :199-212）**每处均 LOG.warn 且带 channel 上下文**，:204-210 注释显式引用 P1-09-01 与 exactly-once 降级可观测要求——per-element isolation 语义在 core 侧成立。**但审计发现 :198 `mapToEnvelope(...)` 在 per-element try 块之外**：envelope 重建内的类型转换异常（如 :240 `(String) m.get("type")` CCE）会中止整个 restore，恰好违反 P1-09-01 设计 → **小缺陷 S-2**（Phase 3 修复 + focused 测试）。修复后该锚点完整成立，P-REQ-17 defer 裁定的实现级依据无例外 | 上述锚点；修复见 Phase 3 |

**勾销对照**：D-GAP §3.1 item 7 行三项重点全部消化，无遗漏条目（D-GAP 未判定 core「无额外重点」，三项均执行了对应审计动作）。

### 2.2 Flink/Beam 产品化维度补评裁定（Phase M 级，plan 0753-3 follow-up 接手）

> 裁定对象：P-REQ 综合报告 §1.3 矩阵中 Flink/Beam 行全部 8 个非 high 置信及 no-evidence 单元格。逐格裁定「补评 / 无需补评」，两方向必有其一；本裁定一次落定，items 8—11 引用不重复裁定。

| 单元格 | 裁定 | 理由（含 P-REQ 消费链追溯） |
|---|---|---|
| Flink CONN 3@medium | **无需补评** | 该格仅被 §1.4 D2 维度观察定性消费（「Flink 生态在仓库外」的引擎型分类——结构性事实，非评分深度问题）；连接器生态的全部 P-REQ（P-REQ-22 目录、P-REQ-28 SPI/OLAP）来源均为 high 置信 primary（SeaTunnel 74 模块 / tis ~45 端 / Beam 51 io spot-check），无一以 Flink CONN 为依据 |
| Flink PERF 3@low | **无需补评** | D6 维度未派生任何 P-REQ（性能证据路线 = item 15 对 nop-stream 自身演练，非竞品基准复测）；矩阵注³已如实标注「无基准报告、标杆水位属业界认知」，置信度标注诚实 |
| Flink DOC 3@low | **无需补评** | D7 派生的 P-REQ-22..25 全部以 SeaTunnel/Spark/KS 源码级报告为来源；Flink DOC 未被任何 P-REQ 消费；文档站独立于代码仓库属结构性事实 |
| Beam DEPL 2@medium | **无需补评** | D3 派生的 P-REQ-15 来源 = ST-8 Helm chart + tis ④（均 high）；Beam DEPL 仅参与矩阵完备性，无 P-REQ 消费 |
| Beam OPS 2@medium | **无需补评** | D4 的 12 条 P-REQ 来源 = SPS/ST/KS/tis + Flink OPS（经 spot-check 已升 high，见 P-REQ 报告注²）；Beam OPS 无 P-REQ 消费 |
| Beam FT 2@medium | **无需补评** | D5 结论「nop-stream 容错不弱于竞品」的对比锚点为 SeaTunnel 2@medium / Spark 整查询重启 / tis 1@medium（均 high 置信 primary）；Beam FT 随 runner 选型变化（报告已注明），其置信度提升不改变该结论方向，且 D5 明确不产生 FT 对标 P-REQ |
| Beam DOC 2@low | **无需补评** | 同 Flink DOC：P-REQ-22..25 无 Beam 来源；无消费链 |
| Beam PERF no-evidence | **无需补评** | 维度对该形态**不适用**（可移植编程模型，性能取决于 runner）已显式标注为 no-evidence 而非猜测评分——这是正确处理；补评前提（选定具体 runner）超出本 roadmap scope，如未来需要应按 roadmap Rules 立 Follow-up 而非本裁定补做 |

**Phase M 级总结论**：8/8 单元格**全部无需补评**。共性依据：P-REQ 的全部裁定型输入均由 high 置信 primary 报告（SeaTunnel/Spark/KS/tis，live SHA 锚定）承载，Flink/Beam 的低置信格仅服务矩阵完备性与定性观察，其置信度提升不改变任何已做或将做（items 8—11 审计重点已由 D-GAP 自包含给出）的决策。**重启路径（记录）**：若 item 18 最终验收需要面向外部沟通的 Flink/Beam 基准/文档对比，属新 mission 决策，届时按 roadmap Rules 立项，不在 Phase M 内补做。plan 0753-3 的 Non-Blocking Follow-up（Flink/Beam 正式补评）至此**闭环**。

### 2.3 核心逻辑优雅性/可靠性审计（双 explore subagent + 执行者复核）

> 审查范围：五层编译管线（model/graph/jobgraph/execution.plan）、windowing、状态接口与 memory 后端、checkpoint 数据对象。subagent 发现中 2 项经复核**否决**（未采信为缺陷）：① DeltaEvictor 空 pane NPE——第二循环体在空 iterable 下永不执行，`lastElement.getValue()` 不可达；② CETT/CPTT 守卫差异——语义等价（见 §2.1 ②）。以下为采信项（含执行者补充验证）。

**B. 错误处理一致性（fail-fast vs 吞异常）——采信缺陷：**

| ID | 严重度 | 发现 | 锚点 | 处置 |
|---|---|---|---|---|
| S-2 | high | `ChannelState.fromSerializableForm` 的 `mapToEnvelope` 在 per-element try 之外，envelope 类型转换异常中止整个 restore，违反 P1-09-01 | ChannelState.java:198-199（对照 :240 `(String) m.get("type")`） | Phase 3 修复 |
| S-3 | high | `MemoryStateSerDe.serializeWithSerializer` catch 后静默 `return value`——自定义序列化器失败时快照格式静默降级（raw object 顶替 byte[]），零可观测性，违反 No-Silent-No-Op；同文件 `inferAccumulatorType`（:471-481）catch 后仅注释吞异常 | MemoryStateSerDe.java:878-887、:471-481 | Phase 3 修复（加 LOG.warn 保留降级行为本身——降级是 P2-09-02c 已裁定的兼容路径，缺的是可观测性） |
| S-4 | high | `OperatorSnapshotResult.empty()` 返回**共享可变单例**：`setCheckpointId/setCheckpointParallelism/setError` 会污染 JVM 全局 EMPTY（Stage 45 模式恰恰鼓励对快照结果调 setCheckpointId；maps 虽为 emptyMap 不可变，但字段可变）。当前生产路径未触发（所有 snapshotState 实现均 new 实例），属**埋雷式潜在缺陷**；测试 `testEmptySingleton` 反而把单例身份断言为契约 | OperatorSnapshotResult.java:19-20,53-55,77-99；TestOperatorSnapshotResult.java:33-37 | Phase 3 修复（empty() 改独立实例 + 契约测试反转） |
| S-5 | medium | `MemoryInternalAggregatingState`/`MemoryInternalAppendingState` 将 `StreamException`（含错误码）重包装为裸 `IOException`，擦除错误属性；同家族 public `MemoryAggregatingState.get()` 不包装——同族不一致 | MemoryInternalAggregatingState.java:123-166、MemoryInternalAppendingState.java:141-148 | Phase 3 修复（StreamException 原样重抛） |
| S-6 | medium | restore 路径 `stateType` 为 null 时裸 NPE（switch on null），无状态名上下文——对照 ChannelState 的防御性写法缺失 | MemoryStateSerDe.java:136-138 | Phase 3 修复（显式类型检查 + StreamException 上下文） |
| W-1 | low | `KeyGroupAssignment.stableHash` 序列化失败静默回退 `hashCode()`（其 Javadoc 自述该类哈希不得用于路由） | shard/KeyGroupAssignment.java:61-71 | Phase 3 修复（LOG.warn 可观测） |
| W-2 | low | region/* 与 checkpoint 值对象（StateSegmentDescriptor/CheckpointConfig/KeyGroupAssignment/LocalFileSegmentStore 等）裸 IAE/ISE 而非 StreamException+错误码 | Region.java:48-53、StateSegmentDescriptor.java:93-99、CheckpointConfig.java:176-203 等 | **watch-only residual**：符合 AGENTS.md 两级错误策略的第二级（模块内值对象前置条件校验，英文消息），统一为 StreamException 属风格收敛无行为收益（Why Not Blocking：非 live defect，异常均为 fail-fast） |

**C. 边界条件——采信缺陷：**

| ID | 严重度 | 发现 | 锚点 | 处置 |
|---|---|---|---|---|
| S-8a | medium | ContinuousEventTime/ProcessingTimeTrigger 构造器与工厂不校验 `interval <= 0` → 运行期 `timestamp % interval` 裸 ArithmeticException 或向过去注册定时器（对照 sliding assigner 校验 slide 的做法） | CETT:51-53,139-141；CPTT:50-52,131-133 | Phase 3 修复（fail-fast 校验） |
| S-9 | medium | `MemoryOperatorStateBackend.restoreSplitDistribute` 静默钳制（newParallelism<=0→1、taskIndex<0→0）且不校验 `taskIndex < newParallelism`——越界 taskIndex 与其他 subtask 重叠恢复同一批 operator state，无任何错误信号（违反 fail-fast；对照 KeyGroupAssignment.computeKeyGroupRangeForSubtaskIndex 越界即抛） | MemoryOperatorStateBackend.java:132-141 | Phase 3 修复（显式校验 + StreamException） |
| S-10 | medium | `JobGraphGenerator` 虚拟链上游未映射时 `findUpstreamVertex` 返回 null → 节点静默不映射 → `createJobEdges` **静默丢弃**触及该节点的边（拓扑无声变更，无 throw 无 log；JobGraph/PartitionedPlanGenerator 无补偿校验） | JobGraphGenerator.java:136-141,488-493,540-552 | Phase 3 修复（未映射端点 fail-fast） |
| S-11 | low | Evictor/WindowingStrategy 参数校验缺口：CountEvictor 负 maxCount（清空整个 pane）、TimeEvictor ≤0 windowSize（清空）、WindowingStrategy 负 allowedLateness 被接受 | CountEvictor.java:87-89、TimeEvictor.java:45-53、WindowingStrategy.java:25-32 | Phase 3 修复（fail-fast 校验） |
| W-3 | low | `TimeWindow.getWindowStartWithOffset` 无溢出保护（timestamp-offset 可溢出）；SETW.assignWindows 循环界 `timestamp - size` 极端负时间戳下溢 | TimeWindow.java:189-197、SlidingEventTimeWindows.java:57-59 | **watch-only residual**（Flink 同源算法语义，epoch-millis 实际输入远距溢出边界；改动会偏离 Flink 基线语义） |
| W-4 | low | `TaskEpochSnapshot.getKeyGroupRange` 不校验 start/end 一致性（部分写坏的 legacy 数据在读取期远端抛裸 IAE）；restore 路径 mapValue 对/namespace 反序列化无逐项类型校验（CCE 无上下文） | TaskEpochSnapshot.java:173-178、MemoryStateSerDe.java:224-229,810-817 | **Follow-up item 24**（restore 硬化与 S-3/S-6 同族但逐项守卫收益递减，且应与 item 21 的 SerDe 重构联动） |

**A. 重复代码复查（对照 05-20 方法论）——采信项：**

| ID | 严重度 | 发现 | 锚点 | 处置 |
|---|---|---|---|---|
| S-1 | medium | ShardPrefixedKey 双副本（见核对表 #9） | shard/ vs backend/memory/ | Phase 3 合并 |
| D-1 | high（结构） | `MemoryStateSerDe` restore*/snapshot* 成对克隆（restoreListState vs restoreInternalListState 28/30 行相同、aggregating 对 96%、snapshot 对 90%）；类名回退模板 5 处复制且 **2 处漂移缺失**（restoreReducing :346-349 / restoreAggregating :397-401 / restoreInternalAggregating :427 无 valueTypeName/accumulatorTypeName 回退，而 Appending :241-253 有——同名 legacy 快照 Appending 可恢复而 Reducing 抛错，**行为已分叉**）；entry-loop key 模板 8 处复制 | MemoryStateSerDe.java:280-342,396-453,624-763,172-318 | 漂移部分 = **小缺陷 S-12**（Phase 3 补齐回退）；结构性去重 = **Follow-up item 21** |
| D-2 | high（结构） | memory 状态类家族成对克隆（MemoryListState vs MemoryInternalListState 等 4 对）+ `applyMigration` 四处逐字复制 + 无意义同分支 if/else 化石四处 | MemoryListState.java:74-133 等四对 | **Follow-up item 21** |
| D-3 | high（结构） | `MemoryKeyedStateBackend` 8 个 getXxxState 重载 85% 同构 + rebindStateBackends 8 路 instanceof 阶梯（TtlAware 标记接口已存在） | MemoryKeyedStateBackend.java:177-316,460-481 | **Follow-up item 21** |
| D-4 | medium | windowing assigner/trigger 家族克隆：SETW vs SPTW ~85%（构造器 17/17 行逐字相同）、CETT vs CPTT ~80%（已漂移，见 §2.1 ②）、窗口溢出守卫 4 处复制、StreamGraphGenerator 节点+边创建样板 4 处复制（transformSource vs transformSourceApi ~85%） | SlidingEventTimeWindows.java:26-104 等 | **Follow-up item 21**（公开 API 家族重构需单独回归面） |
| W-5 | low | `OperatorSnapshotResult` vs `TaskStateSnapshot` 存取面重复；`StreamModel.getSourceCapabilities/getSinkCapabilities` 重复 | 两文件存取器段、StreamModel.java:103-132 | watch-only（量小，随 item 21 顺手评估） |

**D. 优雅性/可靠性（其余采信）：**

| ID | 严重度 | 发现 | 锚点 | 处置 |
|---|---|---|---|---|
| S-13 | medium | 死代码：`StreamGraphGenerator.detectWindowingStrategies` 已空壳化（P1-1 修复后仅剩注释）但仍被 `populateStreamModel:138` 调用；`StreamNode.windowAssigner/trigger` 字段及存取器全仓库（main+test）零外部使用 | StreamGraphGenerator.java:178-187,138；StreamNode.java:99-105,221-250 | Phase 3 清理 |
| S-7 | low | `StreamConstants.java` 空类零引用（05-20 §4.5 残留） | core/StreamConstants.java | Phase 3 删除 |
| S-8b | medium | `core.time.TimerService` 误标 `@Deprecated @Internal` + Javadoc 与事实矛盾（见核对表 #14）；`KeyedProcessFunction` 残留 unused import | TimerService.java:26-30、KeyedProcessFunction.java:3 | Phase 3 修正 |
| W-6 | medium | `EpochManifest` 浅不可变（TaskStateSnapshot/ChannelState 内部可变 Map 经 getter 暴露，对照 getShards 的 unmodifiable 包装不一致）；`MemoryKeyedStateBackend` 构造后可变字段无类级线程契约文档；`MemoryStateSerDe.snapshotState` 空态返回 null 迫使逐调用点判空 | EpochManifest.java:69-91、MemoryKeyedStateBackend.java:106-123、MemoryStateSerDe.java:69-71 | **watch-only residual**（D-2 深度不可变化涉及 runtime 消费路径行为审计，超本 plan 范围；Why Not Blocking：当前消费方均为快照后只读路径，无已证实的并发写缺陷） |
| W-7 | low | `Transformation→StreamNode→JobVertex` parallelism/locked 三层手工传播（新属性需改 3 类 + 2 生成器）；StreamGraphGenerator 双重 DAG 遍历；OperatorChain operators/keySelectors 平行索引表 | Transformation/StreamNode/JobVertex 各字段段 | watch-only（记录为 item 21 重构时的设计输入） |
| W-8 | low | `OperatorSnapshotResult`/`TaskStateSnapshot.estimateSize` 把条目数当 size 返回 | 两类 estimateSize | watch-only |

**明确核验无发现的维度**：编译管线 catch 块全部 attach suppressed + StreamException 重抛（OperatorChain:108-186 等，exemplary）；epoch/checkpoint id 全链 long 无窄化；JobGraphGenerator/PartitionedPlanGenerator 无除以 parallelism 的下标算术；>80 行多职责方法未发现（最长 canChain ~53 行）。

### 2.4 空壳/静默跳过扫描（hollow scan）

- 扫描命令与退出码：`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream/nop-stream-core --severity high` → **6 high / exit 1**（P1 UnsupportedOperationException 模式）。
- **逐条核实结论：6/6 为 guard 模式误报**（非空壳）：`RuntimeContext.java:27,31` + `StreamingRuntimeContext.java:53,61`（keyed 访问守卫，Flink 同款 fail-fast，含 `@Tag` 测试覆盖 keyed 路径）；`Trigger.java:104`（不支持 merge 的触发器显式拒绝，assigner 家族 onMerge 语义）；`FunctionUtils.java:69`（工具类构造守卫）。另复核 6 处**多行 UOE**（行级正则不可见，人工补审）：StreamOperator:185 / AbstractStreamOperator:105,111 / TwoPhaseCommitSinkFunction:111（copyForSubtask 防静默共享状态守卫）、RecordWriter:139（不支持流控策略显式拒绝，G27）、ICheckpointExecutorFactory:81（default-fallback，runtime 已覆写）——**12 处 UOE 全部为合法显式失败，core main 无真实空壳桩**。
- **误报处置（路径 b：修正工具检测规则）**：P1 原规则将一切单行 UOE 定为 high——与 plan guide Rule #24 矛盾（Rule #24 本身认可显式 UOE 为「暂缓实现」的正确做法；空壳的标志是空方法体/静默跳过，不是显式抛异常）。修正为按**消息语义分级**：stub 标记（not implemented/not yet/unimplemented/stub/placeholder）保持 **high**；guard 标记（only available/only supported/does not support/not supported/utility class/does not implement）降 **low**（信息级，仍报告可审）；其余降 **medium**（需人工判断，与 P2b/P3b 同级）。修正后 `--severity high` 对 core exit 0；真实 stub（如历史 forceNonParallel 桩）仍会被 high 拦截——门禁更精准而非放松。工具改动见 `ai-dev/tools/scan-hollow-implementations.mjs`（P1 severityFor 分级 + 报告按严重度分桶）。
- **工具已知盲区（记录为 Non-Blocking Follow-up，不改）**：多行 UOE（throw 与消息串分行）不被行级正则命中——本轮已人工补审 core 12 处全为守卫；扩大检测属工具增强，与 items 8—11 扫描基线一致性相关，留待统一决策。

### 2.5 测试覆盖抽查

| 包 | 代表性测试（≥2 命中确认） |
|---|---|
| datastream | TestForceNonParallel、TestWindowedStreamFactoryFailFast、TestKeyedStreamAggregation、TestParallelismLockedPropagation |
| model | TestStreamComponents、TestStreamModelFingerprint、TestStreamRequirementValidator |
| graph | TestStreamGraphGenerator、TestFingerprintValidation、TestStreamModelPopulation |
| jobgraph | TestJobGraphGenerator、TestJobGraph、TestOperatorChainLifecycle + region/ 子包 |
| execution | TestInputGate 家族（13 个）、TestCheckpointBarrierTracker 家族、TestGraphExecutionPlan |
| operators | TestHeapInternalTimerService 三件套（含 SnapshotRestore）、TestOperatorLifecycle、TestOperatorSubtaskIsolation |
| windowing | TestWindowEndToEnd、TestWindowTranslation、TestWindowingModel + triggers/evictors 11 测试 |
| common/state | TestDefaultOperatorStateStore、TestMemoryStateBackendSnapshotRestore、TestStateSchemaResolver + backend/memory 家族（TestMemoryStateSerDe 等 10+） |

**行数 top-5 文件直接覆盖**（`wc -l` 排序）：

| 文件（行数） | 直接测试 |
|---|---|
| StreamTaskInvokable（1001） | TestStreamTaskInvokableProcessInput / ProcessingTimeWiring / ActivityLiveness |
| InputGate（974） | TestInputGate + 12 个专项（Alignment/MailboxAbort/MultiEpoch/UnalignedFallback 等） |
| MemoryStateSerDe（919） | TestMemoryStateSerDe / ContainerValueRestore / NumericKeyRestore + TestStateSnapshotRoundTrip |
| GraphExecutionPlan（749） | TestGraphExecutionPlan + TestParallelGraphExecution + TestGraphModelExecution |
| StreamGraphGenerator（678） | TestStreamGraphGenerator + TestStreamModelPopulation |

**结论**：主要包与 top-5 文件覆盖充分，无明显缺口；既有缺口维持 06-30 低优先级判定（configuration/streamrecord/time 无专属单测、execution.flow/plan/transport 各 1 测试——watch-only，见核对表 #12/#13）。

## Phase 3 — 缺陷处置（摘要，逐项映射见 §2.3 处置列）

### 小缺陷就地修复（行为变更项均附 focused 测试）

| ID | 修复 | focused 测试（验证的新行为） |
|---|---|---|
| S-1 | 删除 `backend/memory/ShardPrefixedKey`，MemoryKeyedStateBackend/MemoryStateSerDe 统一用 `shard/ShardPrefixedKey`（getKey() 替代包内字段直取）；public 版 Javadoc 补记合并事实 | TestMemoryKeyedStateBackendRouteKeyOverflow（改用 getKeyGroupId() 后 4/4 绿）+ TestMemoryKeyedStateBackendSnapshotRestore/TestKeyGroupRangeBackendRestore/TestStateSnapshotRoundTrip 直接回归（纯合并无新行为） |
| S-2 | `mapToEnvelope` 移入 per-element try（P1-09-01 隔离覆盖 envelope 重建） | **新增** TestChannelStateRestoreIsolation（3 用例：畸形 type 字段只跳过该记录、其他 channel 存活、合法数据全量恢复不过度跳过） |
| S-3 | serializeWithSerializer/inferAccumulatorType 失败路径 LOG.warn（保留 P2-09-02c 降级行为本身） | **新增** TestMemoryStateSerDeAuditFixes.testFailingSerializerFallsBackWithWarning（Logback ListAppender 断言 WARN + 快照仍成功 + raw 值 round-trip） |
| S-4 | `empty()`/空 Builder 返回独立实例（删共享可变单例 EMPTY） | TestOperatorSnapshotResult 更新 2 处：testEmptyInstancesAreIndependent（污染不扩散）+ testBuilderEmptyBuildsIndependentEmpty |
| S-5 | MemoryInternalAggregating/AppendingState 的 StreamException 原样重抛（不再擦码为 IOException） | TestMemoryInternalAggregatingState.testStreamExceptionNotWrappedIntoIOException（**新增**） |
| S-6 | restore 循环：非 Map 状态项/stateType 缺失 → StreamException（带状态名/实际类型） | TestMemoryStateSerDeAuditFixes.testMissingStateTypeFailsFastWithStateName + testNonMapStateEntryFailsFast（**新增**） |
| S-8a | CETT/CPTT `of(Duration)` 对 null/≤0 interval fail-fast | TestContinuousEventTimeTrigger/TestContinuousProcessingTimeTrigger.testNonPositiveIntervalFailsFast（**新增**，各 3 断言） |
| S-8b | TimerService 去掉误标 @Deprecated/@Internal + Javadoc 修正为事实（ProcessFunction 用户面 API）；KeyedProcessFunction 删 unused import | 纯注解/导入变更，No new test required（编译即验证） |
| S-9 | restoreSplitDistribute 对 newParallelism≤0 / taskIndex 越界 fail-fast（原静默钳制 + 步幅重叠） | TestMemoryOperatorStateBackend.testSplitDistributeInvalidParallelismFailsFast + testSplitDistributeOutOfRangeTaskIndexFailsFast（**新增**） |
| S-10 | JobGraphGenerator 未映射边端点收集 + fail-fast（原静默丢边=拓扑无声变更） | TestJobGraphGenerator.testUnmappedEdgeEndpointFailsFast（**新增**，反射直调 createJobEdges 复现 mapping-gap，断言错误含 "1->2"） |
| S-11 | CountEvictor 非正 maxCount / TimeEvictor 非正 windowSize / WindowingStrategy 负 allowedLateness fail-fast | TestCountEvictor/TestTimeEvictor 追加 2 用例 + **新增** TestWindowingStrategyValidation（2 用例） |
| S-12 | restoreReducing/Aggregating/InternalAggregating 补 valueTypeName/accumulatorTypeName legacy 回退（与 Appending/List 对齐） | TestMemoryStateSerDeAuditFixes.testReducingStateRestoresFromLegacyTypeNameKeys（**新增**：真实快照改写为 legacy 拼写后恢复成功且值保持 12L） |
| S-13 | 删 detectWindowingStrategies 空方法+调用（P1-1 说明性注释移至调用点）、StreamNode 死字段 windowAssigner/trigger 及存取器 | 行为无变化（原方法体为空、字段零引用），既有管线测试（TestStreamGraphGenerator 等）回归 |
| S-7 | 删 StreamConstants.java（空类零引用，05-20 §4.5 残留） | 零引用删除，编译即验证 |
| W-1 | KeyGroupAssignment.stableHash 回退路径 LOG.warn（JVM-variable 路由可观测） | 行为不变（回退值不变），No new test required |

### 大缺陷/治理项转 Follow-up（roadmap items 21—24，来源标注本 plan，2026-09-01 已落库）

- **item 21** core 重复代码收敛第二轮（MemoryStateSerDe 克隆家族 + memory 状态类 4 对克隆 + 8 重载同构 + windowing assigner/trigger 家族克隆——报告 §2.3 D-1..D-4 锚点）。
- **item 22** 测试代码通配符导入清理（core 169 / runtime 108 / cep 15 / flow 1 文件，main 已清零）。
- **item 23** execution 根包重组（Task 执行族下沉子包，跨模块 import 变更）。
- **item 24** 状态恢复路径防御性校验补全（mapValue 逐对/namespace 守卫/KeyGroupRange 一致性，与 item 21 联动）。

### 回归验证（2026-09-01 执行记录）

- `./mvnw test -pl nop-stream -am -T 1C` → **BUILD SUCCESS**（core/rocksdb/connector×4/runtime/cep/flow/fraud-example 全模块绿；新增 focused 测试 16 个用例全过）。
- `./mvnw clean install -pl nop-stream -am -T 1C -DskipTests` → BUILD SUCCESS。
- `node ai-dev/tools/check-nop-stream-invariants.mjs` → 退出码 **0**（CI fail-fast 门禁）。
- `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream/nop-stream-core --severity high` → 退出码 **0**（工具 P1 消息语义分级修正后，6 个 guard 误报降为 low 信息级；stub 消息仍为 high——分级逻辑经临时合成用例验证（not implemented/not yet implemented→high、guard 模式×3→low、其他→medium），并由独立 closure audit session（ses_fa4f7c567ffezlrg0acXrjKG3J）独立复评确认真实 stub 仍被拦截）。
- `node ai-dev/tools/check-doc-links.mjs --strict` → 退出码 0（报告新增后，见 closure 记录）。
- 端到端验证（Rule #22，如适用）：本 plan 修复未触及执行管线行为路径的语义（S-10 为 fail-fast 新增分支、S-9/S-11 为参数校验），全模块 E2E 套件（TestE2ESimplePipeline/TestEventTimeWindowE2E/TestE2EOperatorStateCheckpoint/TestE2EOperatorStateRedistribution 等）随回归绿即覆盖。
- Owner-doc 裁定：`No owner-doc update required`——修复均为 core 内部实现缺陷收敛，不改用户可见契约（TimerService 注解修正是**恢复**事实契约）；trigger 语义证据表（§2.1 ②）由 item 17 届时引用本报告，不在本轮改 docs-for-ai。

## Conclusion

- **历史审计收口**：05-20 core 九组（6 清除/收编 + 2 部分残留转处置 + 1 落地方式变化并发现注解缺陷）；06-30 20 项核对（14 landed / 4 partial 全归类 / 2 低优先级维持）；2300-1/2/3 core 侧收口复核成立（2300-2 不适用）。无回潮、无未处置 partial/regressed。
- **产品化审计**：D-GAP 三项重点全部消化（① schemaVersion 无隐藏分支、② 触发语义证据表产出、③ skip-vs-fail 锚点成立并修复其例外 S-2）；Flink/Beam 8 格全部裁定无需补评（Phase M 级一次落定，0753-3 follow-up 闭环）；优雅性审计采信 15 项缺陷（2 项 subagent 发现经复核否决）、10 项 watch-only residual、4 项结构重复转 Follow-up。
- **修复与收口**：15 项小缺陷全部就地修复（10 项行为变更各配 focused 新测试/测试更新，验证正确结果而非仅无异常；5 项纯清理/合并以既有直接测试或编译验证）；4 项 Follow-up（items 21—24）落 roadmap；全量回归绿 + 三工具门禁 exit 0。
- **被否决的方案**：将 CETT/CPTT 守卫差异当缺陷修复（否决：语义等价，改动引入无意义偏差）；将 region 值对象 IAE 统一为 StreamException（否决：第二级错误策略已容许，风格收敛无行为收益）；在本 plan 内清理 169 个测试文件的通配符导入（否决：违反最小 diff，items 8—11 会重复同类工作，跨模块统一 sweep 更优）；将 hollow scan 6 个守卫 UOE 改写为其他异常类型以消除告警（否决：守卫语义正确且是 Flink 同款模式，误报根因在工具无消息语义分级）。
- **后续工作**：items 8—11 直接引用本报告 §1.1 §7 行（空壳模块结论）、§2.2（Flink/Beam 裁定）、§2.1 ②（触发语义证据）；item 17 引用 §2.1 ② 证据表；Follow-up items 21—24 待调度。

## References

- `ai-dev/analysis/2026-05-20-nop-stream-duplicate-code-audit.md`、`2026-06-30-nop-stream-code-audit.md`（历史审计原文）
- `ai-dev/analysis/2026-08/2026-08-06-nop-stream-audit-baseline-and-roadmap-analysis.md`（方法论）
- `ai-dev/analysis/2026-09/2026-09-01-nop-stream-design-productization-gap-analysis.md`（D-GAP §3.1 core 重点）
- `ai-dev/analysis/2026-09/2026-09-01-competitor-productization-synthesis-and-p-req.md`（P-REQ 矩阵 §1.3）
- `ai-dev/plans/nop-stream-productization/2026-09-01-0938-2-core-module-audit.md`（执行 plan）
- 双 explore subagent 审计 session：pipeline/windowing（task `ses_fa52091feffeJOVPffwgwU5pLL`）、state/checkpoint（task `ses_fa5206cccffeL0FHAcsXQOUvIv`）
