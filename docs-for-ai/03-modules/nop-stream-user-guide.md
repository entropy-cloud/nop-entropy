# nop-stream 用户指南

> 定位：nop-stream **用户面**文档——DataStream API、XDSL 声明式编排、连接器使用、分布式部署、触发语义对照。
> 运维契约（指标名表 / REST 运维 / 健康状态机 / 告警 / 治理 / 运维手册）见 owner doc `03-modules/nop-stream.md`；连接器能力矩阵见 `03-modules/nop-stream-connectors.md`；CDC 生产化操作手册见 `03-modules/nop-stream-cdc-cookbook.md`；版本迁移见 `03-modules/nop-stream-migration-guide.md`。

## 心智模型：三种入口，一种模型

nop-stream 的 canonical 模型是 **StreamModel**（可序列化算子图 + 组件注册表），可由三种入口合成，最终走同一条编译执行管线：

1. **XDSL 声明式定义**（`.stream.xml`，xdef 合同 `/nop/schema/stream/stream.xdef`）——产品推荐主入口
2. **Java DataStream API** 编程构造
3. **Delta 定制合成**（`x:extends` / `_delta` 目录，基于既有 `.stream.xml` 派生）

编译管线（xdef 头注释的权威表述）：`StreamModel → StreamGraph → JobGraph → PartitionedPlan → DeploymentPlan → GraphExecutionPlan`。LOCAL 模式经 `GraphExecutionPlan` + `TaskExecutor` 直接执行；DISTRIBUTED 模式经 `IStreamExecutionDispatcher` 调度（见「分布式部署」节）。

## DataStream API

### 环境与执行

入口类 `io.nop.stream.core.environment.StreamExecutionEnvironment`：

```java
StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
env.setParallelism(2);
env.enableCheckpointing(60_000);           // 周期 checkpoint（ms）
env.fromElements(1, 2, 3, 4, 5)
   .map(x -> x * 2)
   .filter(x -> x > 4)
   .print();
StreamExecutionResult result = env.execute("simple-pipeline");
```

**checkpoint 执行前提（enableCheckpointing 的诚实契约）**：声明了 `enableCheckpointing(...)` 的作业必须有 checkpoint 执行器工厂才会走 checkpoint 执行路径——`nop-stream-runtime` 在 classpath 时经 `META-INF/services` SPI 自动接线（`ServiceLoader` 发现 `ICheckpointExecutorFactory`，无需手工 setter）；多处实现时必须用 `StreamExecutionEnvironment.setCheckpointExecutorFactory(...)` 显式消歧（typed 拒绝任意静默选择）。classpath 上没有任何工厂时**快速失败**（typed 错误点名 `enableCheckpointing` 与工厂接线），不会静默落入无 checkpoint 的 LOCAL 执行。测试/脚手架中也可用静态 setter 显式接线（quickstart 的 `QuickstartSupport.registerCheckpointExecutorFactory()` 即此形态），执行后记得 `unregister` 清理。

常用环境方法（`StreamExecutionEnvironment.java`）：

| 方法 | 语义 |
|---|---|
| `getExecutionEnvironment()` / `createLocalEnvironment(int)` | 获取/构造执行环境（默认 parallelism=1、watermark 间隔 200ms） |
| `setParallelism(int)` / `setWatermarkInterval(long)` | 全局并行度 / 周期 watermark 发射间隔 |
| `enableCheckpointing(long interval)` / `getCheckpointConfig()` | 开启周期 checkpoint / 取 `CheckpointConfig`（全量键见下文触发语义节） |
| `setStateBackend(IStateBackend)` | 状态后端（memory / RocksDB） |
| `setDeploymentMode(DeploymentMode)` | `LOCAL`（默认）/ `DISTRIBUTED` |
| `fromElements(T...)` / `fromCollection(Collection)` | 内联有界 source |
| `addSource(SourceFunction, name)` / `addSource(Source, name)` | 自定义 source（后者为 FLIP-27 风格 split-based source，如 FileSource） |
| `execute(String jobName)` | 提交执行（同一环境只允许执行一次；至少需要一个 sink） |
| `buildJobGraph(String)` / `triggerSavepoint(String)` / `executeWithSavepoint(String)` | 只构建不执行（分布式 launch 路径）/ savepoint 族 |

### 常用算子

`DataStream<T>`（接口 `io.nop.stream.core.datastream.DataStream`）：

| 算子 | 说明 |
|---|---|
| `map(MapFunction<T,R>)` / `flatMap(FlatMapFunction<T,R>)` / `filter(FilterFunction<T>)` | 一进一出 / 一进多出 / 过滤 |
| `keyBy(KeySelector<T,K>)` | 按 key 分区，返回 `KeyedStream<T,K>` |
| `process(ProcessFunction<T,R>)` | 低阶算子（计时器 + 侧输出；keyed 形态见下） |
| `assignTimestampsAndWatermarks(WatermarkStrategy<T>)` | 事件时间水位（`forMonotonousTimestamps()` / `forBoundedOutOfOrderness(Duration)` / `noWatermarks()`）；env 级 watermarkInterval（默认 200ms） |
| `assignTimestampsAndWatermarks(WatermarkStrategy<T>, long watermarkInterval)` | 同上 + **节点级水位节奏**（plan 1326-2，F-04）：`0` = 逐事件发射、`>0` = 限频 + 周期 timer；XDSL `<timestampsAndWatermarks watermarkInterval="...">` 声明即经此重载接线（非默认值真实生效，root 级 `=0` 不再被丢弃） |
| `transform(String, TypeInformation<R>, OneInputStreamOperator<T,R>)` | 自定义算子接线 |
| `print()` / `sink(SinkFunction<T>)` / `collect(SinkFunction<T>)` | sink 收口 |

**水位 idleness（plan 1326-2，AR-9）**：`WatermarkStrategyWithIdleness`（`withIdleness(Duration)`）与 `SourceContext.markAsTemporarilyIdle()` 的 idle 状态**跨任务生效**——`WatermarkStatus` 经 RecordWriter 广播到下游分区，下游 `InputGate` 按 Flink `StatusWatermarkValve` 语义维护 per-channel idle：idle 通道不参与 min 水位合并（上游子任务静默后下游事件时间不被钉死）、全通道 idle 时向链转发 IDLE、通道复活时转发 ACTIVE。回归锚点：`TestInputGateWatermarkIdleness`（core）、`TestWatermarkIdlenessCrossTaskE2E`（跨任务拓扑：idle source 任务 + 活跃通道，下游水位持续推进）。

`KeyedStream<T,K>`：`timeWindow(size[, slide])`（事件时间滚动/滑动窗）、`countWindow(size[, slide])`（计数窗，默认接线见触发语义矩阵）、`window(WindowAssigner)`（自定义 assigner）、`reduce(ReduceFunction)`、`process(KeyedProcessFunction)`、`sum/min/max(int|String 字段名)`。

`WindowedStream<T,K,W>`：`apply(WindowFunction)` / `aggregate(AggregateFunction)` / `reduce(ReduceFunction)` / `process(ProcessWindowFunction)` / `trigger(Trigger)` / `evictor(Evictor)`。**窗口算子执行需要 `nop-stream-runtime` 在 classpath**（缺失时 fail-fast 报 "WindowOperator requires nop-stream-runtime on classpath"，由 ServiceLoader 风格发现的 `IWindowOperatorFactory` 接线）。

能力边界（build 期 fail-fast，不静默降级）：`union`/`connect`/侧输出提取的运行时 API 尚未提供——XDSL 中 `<union>`/`<sideOutput>` 元素在 build 期显式报错，Java API 无对应方法。

### 状态与计时器心智模型

- **Keyed state**：在 keyed 算子（`KeyedProcessFunction` / 窗口函数）内经 `getRuntimeContext().getKeyedStateStore()` 获取；非 keyed 上下文调用 fail-fast（"Keyed state is only available on a keyed stream"）。
- 状态类型：`ValueState<T>`（`value()/update()/clear()`）、`ListState<T>`、`MapState<K,V>`、`ReducingState<T>`、`AggregatingState<IN,OUT>`；描述子 `ValueStateDescriptor` 等（name + 类型，可设 TTL `StateTtlConfig`）。
- **计时器**：`ProcessFunction.Context.timerService()` → `TimerService`（`registerProcessingTimeTimer/registerEventTimeTimer/delete...`）；触发回调进 `onTimer(timestamp, ctx, out)`。计时器随 keyed state 一同 checkpoint/恢复。
- **状态后端**：`MemoryStateBackend`（默认）与 RocksDB 后端（`nop-stream-rocksdb`，增量快照）；状态重分区按 key-group（默认 maxParallelism=128）。
- 真实示例：fraud-example 的 `UserHistoryEnricher`（`KeyedProcessFunction` + keyed `ValueState` 做 per-user 均值富化，`UserHistoryEnricher.java`（fraud-example scenario 包））。

## XDSL 声明式编排（`.stream.xml`）

### 文档结构

xdef 合同：`/nop/schema/stream/stream.xdef`（根元素 `<stream>`，模型名/版本/parallelism/watermarkInterval 属性）。主体结构：

```xml
<stream name="my-pipeline" x:schema="/nop/schema/stream/stream.xdef" ...>
  <checkpoint enabled="true" interval="100" processingGuarantee="STRICT_EXACTLY_ONCE"
             storageType="local"/>
  <windowingStrategies>
    <strategy strategyId="tx-window" windowFnId="txWindowAssigner"/>
  </windowingStrategies>
  <transforms>
    <source id="src" bean="mySource"/>
    <map id="upper"><source>return event.toString().toUpperCase();</source></map>
    <keyBy id="kb" keyExpr="event.userId"/>
    <window id="win" strategyRef="tx-window"/>
    <aggregate id="agg" bean="myAggregator"/>
    <sink id="out" bean="mySink"/>
  </transforms>
  <edges>
    <edge id="e0" from="src" to="upper"/>
    <edge id="e1" from="upper" to="kb" partition="FORWARD"/>
    ...
  </edges>
</stream>
```

可用 transform 类型：`source`、`timestampsAndWatermarks`、`map`、`flatMap`、`filter`、`keyBy`（`keyExpr`）、`window`（`strategyRef`）、`aggregate`、`reduce`、`process`、`cep`（`patternRef`）、`sink`、`custom`（customType）。边属性：`partition`（FORWARD/HASH，HASH 需 `keyExpr`）等。CEP 模式在 `<patterns>` 块声明（内联 xpl `<where>` 谓词）。

### bean / xpl 双函数形态

transform 的函数体两种形态（`StreamModelDslBuilder`，`StreamModelDslBuilder.java`（flow builder 包））：

- **bean 引用**：`bean="beanName"` 属性 → 经 `BeanFunctionResolver` 解析（生产 = NopIoC `BeanContainer`；测试可用 `InMemoryBeanFunctionResolver`）。`process`/`aggregate`/`cep` 仅支持 bean 形态。
- **内联 xpl**：transform 子元素 `<source>` 体内写 xpl——`map` 为 `(event)=>any`、`filter` 为 `(event)=>boolean`、`flatMap` 为 `(event,out)=>void`、`reduce` 为 `(a,b)=>any`、`sink` 为 `(event)=>void`、`source` 为 `(ctx)=>void`。
- **内联 xpl source 的取消模式**：source 体是长循环，取消必须经 `ctx` 观察——轮询 `ctx.isCancelled()`（生产上下文将其接到任务 mailbox 取消标志，与 `collect()` 协作中止异常同一信号源）。推荐写法 `while (!ctx.isCancelled()) { ...; ctx.collect(x); }`：取消后循环条件退出（优雅返回）或下一次 `collect()` 抛协作中止异常，两条路径均合法；不调用 `collect` 的循环体必须依赖轮询退出。
- **per-transform 并行度**：任一 `<transform parallelism="N">` 声明值被真实消费（解析顺序 transform 级 > stream 级 `<stream parallelism="...">` > 默认 1；未声明继承 stream 级）。生效链贯穿 `Transformation → StreamNode → JobVertex → 执行`（并行度不等的相邻顶点自动断链）。注意：`<window>` 是虚拟元素（无自身顶点）——并行度声明在后续 `<aggregate>`/`<reduce>`/`<process>` 上；HASH 边目标的声明值自动同步到隐式 partition 顶点（避免 FORWARD modulo 把数据集中到 subtask 0）。

### 执行 `.stream.xml` 的惯用法

没有独立的 `StreamModelManager`；标准加载链（测试与运行时同一链路）：

```java
IResource resource = VirtualFileSystem.instance().getResource("/nop/stream/demo/fraud-s2-file.stream.xml");
StreamModel model = (StreamModel) new DslModelParser().parseFromResource(resource);  // xdef 解析 + x:extends/_delta 合并
StreamExecutionEnvironment env = StreamModelDslBuilder.of(model, resolver).build();  // DSL → DataStream API 调用链
env.execute("job-name");   // 或 buildJobGraph(jobName) 供分布式 launch
```

`StreamModelDslBuilder` 按 `<edges>` 拓扑遍历 `<transforms>`，产出与手写 Java API 完全一致的 Transformation DAG（fraud-example `TestS2FileAggregationE2E.s2JavaVariantMatchesXdslOutput` 断言两种入口输出逐行一致）。

### Delta 定制

`.stream.xml` 支持平台标准 Delta 机制（`x:support-extends="true"`）：

- **显式路径继承**：`x:extends="/nop/stream/demo/fraud-s2-file.stream.xml"`，在派生文件中新增 transform（如黑名单 filter）并重接边。
- **分层 Delta**：`_vfs/_delta/<layer>/...` 目录下的同名文件经 `x:extends="super"` 自动叠加。
- **仅配置派生**：只改 `parallelism`/checkpoint 配置不改拓扑（fingerprint 不变，按设计）。
- 行为由 `nop-stream-flow` 的 `TestStreamModelDeltaExtends` 钉定。

### 实例锚点（fraud-example）

| 场景 | 文件 | 拓扑 |
|---|---|---|
| S1 复合场景 | `/nop/stream/demo/fraud-s1-cdc.stream.xml` | CDC source（`ReplayableCdcSourceFunction`）→ 事件时间水位 → 解码 → keyBy(userId) → keyed-state 均值富化 → 4 条并行 CEP 链（rapid-transactions / unusual-amount / geographic-anomaly / account-takeover，每条 `cep → keyBy → window → aggregate → JdbcTwoPhaseCommitSink`） |
| S2 文件聚合 | `/nop/stream/demo/fraud-s2-file.stream.xml` | 文件 source → 解析 → 水位 → keyBy(userId) → 滚动窗口聚合 → `FileTwoPhaseCommitSink` |
| S2 Delta 派生 | `/nop/stream/demo/fraud-s2-file-delta.stream.xml` | 在 S2 基础上 `x:extends` 插入黑名单 filter 并重接边 |

## 连接器使用指引

现有连接器族（file / message / jdbc / debezium / batch 桥接）的**逐连接器能力矩阵**（方向、交付语义、并行度、恢复语义）见 `03-modules/nop-stream-connectors.md`。使用要点：

- **语义组合规则**：端到端 exactly-once = 可重放 source（`REPLAYABLE`，offset/cursor 进 checkpoint）+ 两阶段提交 sink（`TWO_PHASE_COMMIT`）。`StreamRequirementValidator` 在 build 期校验：声明 `STRICT_EXACTLY_ONCE` 的管线若组合了 at-least-once source / 非 2PC sink，fail-fast。
- **2PC sink 并行能力**：`TwoPhaseCommitSinkFunction` 族 sink（file/jdbc 内建）支持任意有效并行度——per-subtask 隔离（独立 UDF 拷贝 + 台账复合键 `(epoch_id, subtask_id)` / 文件 `.sK` 后缀），exactly-once 在 P=N 成立（LOCAL + 真实多 JVM 已证明）。跨并行度**恢复**被 typed 拒绝（`ERR_STREAM_2PC_SINK_PARALLELISM_CHANGE_UNSUPPORTED`——恢复时须保持与快照相同的并行度）；未 override `copyForSubtask(int)` 的第三方子类在 P>1 部署期 fail-fast（基类默认）。见连接器指引「并行 2PC 能力说明」。
- **连接器 SPI 注册中心**（item 19）：全部端点连接器已按方向作用域类型名注册（source：`file`/`message`/`debezium-cdc`/`batch-loader`；sink：`file`/`message`/`jdbc-2pc`/`batch-consumer`），维护/探测入口与能力矩阵见 `03-modules/nop-stream-connectors.md`「SPI 注册中心与类型名」。
- **提交前校验**（item 20）：作业提交前可用 `StreamMaintenanceMain conf-validate file=<stream.xml>`（不启动作业即字段级报错）与 `dry-run file=<stream.xml>`（逐 source/sink 连通性探测）先行验证——命令、分层语义、exit code、逐族探测能力表与错误样例见 owner doc `03-modules/nop-stream.md`「提交前校验」节。
- **凭据引用**（item 20）：连接器配置字段可写 `credential:{credentialId}#{field}` 引用（如 CDC 的 `databasePassword`）替代明文——引用串随配置序列化/checkpoint 持久（跨 JVM 可再解密），明文只在引擎侧瞬态路径存在；provider 缺失/凭据不存在均 fail-closed 显式报错。详见 owner doc「凭据引用与明文边界」节。
- 每个连接器的恢复语义（光标/offset 的 checkpoint 路径）在目录页逐项标注，此处不重复。

## 分布式部署

### 形态

- **LOCAL**：`env.execute()` 直接在本进程 `TaskExecutor` 执行（默认）。
- **DISTRIBUTED**：`nop-stream-runtime` 提供 `EmbeddedDistributedExecutor`（同 JVM 多 TaskManager）与 `RpcDistributedExecutor`（跨 JVM，控制面经 `IMessageService` 真实 RPC、coordinator↔task RPC proxy、`deployTask` RPC 远端重建 invokable）；数据面跨 JVM 传输经 `DataPlaneMessageServiceAdapter`（`SysDaoWireCodec` / `PulsarStringWireCodec` 线协议）。
- **集群注册/选主**：`JdbcClusterRegistry` / `JdbcLeaderElector`（共享 JDBC 库——H2 AUTO_SERVER 或等价 JDBC；节点自动注册 `StreamNodeAutoRegistration`；fencing epoch 防双主）。

### 启动与停止

多 JVM 独立进程形态的启动命令、REST 生命周期（`POST /jobs/{jobId}/stop?mode=CANCEL|DRAIN`）、savepoint 恢复、状态重置等**运维操作权威步骤见 owner doc `03-modules/nop-stream.md`「运维手册」节**（本文不重复）。用户侧要点：

1. 预置共享 JDBC 库（JDBC 2PC sink 需预建数据表 + `stream_epoch_ledger` 台账表）。
2. 先启 TaskManager、后启 JobCoordinator（命令模板见 owner doc）。
3. 作业拓扑经 `pipelineFactoryClass`（`ClusterPipelineFactory`）在 coordinator 进程重建——XDSL 管线经 `RemotePipelineResolver` 用与本地完全相同的 `DslModelParser → StreamModelDslBuilder → buildJobGraph` 链在 TM 侧重建，fingerprint 跨 JVM 一致。
4. TM 失败自动 global recovery（fencing epoch 轮转 + 从最近 durable checkpoint 恢复）；健康状态机与告警见 owner doc。

### 入口类现状（如实标注）

独立进程入口类 `JobCoordinatorMain` / `TaskManagerMain` 目前位于 `nop-stream-runtime` 的 **test scope**（`src/test/java/.../launch/`），经 runtime **test-jar** 导出供 fraud-example 的多 JVM gated 测试消费（依赖方向保持 main 单向）。生产部署如需独立进程入口，当前需消费该 test-jar 或自建 launch 类；「产品级 main-scope 启动入口」已登记为 Follow-up 候选。已实现的 main-scope 运维面（`OpsJobManager` 多作业模式、`StreamOpsHttpServer` REST、`StreamMaintenanceMain` 维护工具）不受此限制。

## 触发语义映射表（nop-stream ↔ Spark / SeaTunnel / Flink）

> 背景裁定（P-REQ-16，D-GAP 2026-09-01 go）：nop-stream 是**连续流**模型，不提供 micro-batch 式作业级触发参数（Spark Trigger 族）——触发语义由**窗口级 Trigger 家族** + **作业级等价物**（checkpoint 周期 / processing-time timer / DRAIN 截断）承载。本表每个公开 Trigger/Evictor 类型均有专属单测（锚点见下表）。

### 窗口级 Trigger / Evictor 家族（live 全量）

`io.nop.stream.core.windowing.triggers`（10 文件）+ `evictors` + `operators/Triggerable`：

| 类 | 语义 | 单测锚点 |
|---|---|---|
| `Trigger` / `TriggerResult` | 触发器基契约（onElement/onEventTime/onProcessingTime → CONTINUE/FIRE/PURGE/FIRE_AND_PURGE） | 家族共测 |
| `EventTimeTrigger` | 窗口 maxTimestamp 水位到达时 fire | `TestEventTimeTrigger` |
| `ProcessingTimeTrigger` | 处理时间到达窗口边界 fire | `TestProcessingTimeTrigger` |
| `CountTrigger` | 计数达到阈值 fire（内部 accumulator 状态） | `TestCountTrigger` |
| `PurgingTrigger` | 包装器：fire 后清空窗口内容 | `TestPurgingTrigger` |
| `ContinuousEventTimeTrigger` | 窗口内按事件时间周期 fire | `TestContinuousEventTimeTrigger` |
| `ContinuousProcessingTimeTrigger` | 窗口内按处理时间周期 fire（runtime `WindowOperator` 生产引用） | `TestContinuousProcessingTimeTrigger` |
| `DeltaTrigger` | 增量阈值触发 | `TestDeltaTrigger` |
| `ProcessingTimeoutTrigger` | 处理超时触发 | `TestProcessingTimeoutTrigger` |
| `CountEvictor` / `TimeEvictor` / `DeltaEvictor`（+ `Evictor` 基接口） | fire 前按数量/时间窗/增量逐出元素 | `TestCountEvictor` / `TestTimeEvictor`（含集成 `TestTimeEvictorIntegration`、`TestEvictorIntegration`）/ `TestDeltaEvictor` |
| `Triggerable`（`core.operators`） | 算子侧计时器回调接线契约 | `TestHeapInternalTimerService` 三件套 |

### assigner / countWindow 默认接线矩阵（8 行）

| 入口 | 默认 Trigger | 默认 Evictor | 锚点（`io.nop.stream.core` 下） |
|---|---|---|---|
| `TumblingEventTimeWindows` | `EventTimeTrigger.create()` | — | `TumblingEventTimeWindows.java` |
| `SlidingEventTimeWindows` | `EventTimeTrigger` | — | `SlidingEventTimeWindows.java` |
| `EventTimeSessionWindows` | `EventTimeTrigger` | — | `EventTimeSessionWindows.java` |
| `TumblingProcessingTimeWindows` | `ProcessingTimeTrigger` | — | `TumblingProcessingTimeWindows.java` |
| `SlidingProcessingTimeWindows` | `ProcessingTimeTrigger` | — | `SlidingProcessingTimeWindows.java` |
| `GlobalWindows` | 内部 `NeverTrigger`（须显式配 trigger） | — | `GlobalWindows.java` |
| `KeyedStream.countWindow(size)` | `PurgingTrigger.of(CountTrigger.of(size))` + GlobalWindows | — | `KeyedStreamImpl.java`（datastream 包） |
| `KeyedStream.countWindow(size, slide)` | `CountTrigger.of(slide)` + GlobalWindows | `CountEvictor.of(size)` | 同上 |

runtime 集成：`WindowOperator` 对 `CountTrigger`/`ContinuousProcessingTimeTrigger` 的 accumulator 状态与 clear 语义由 `WindowOperator.java`（runtime windowing 包） 承载（`TestWindowOperatorTriggerAccumulatorCleanup` 钉定）。

**Evictor 驱逐语义（plan 1326-2，AR-2/AR-3 对齐 Flink）**：evictor 的驱逐**物理生效**——被逐元素从窗口状态永久移除（不随下次 fire 重返），元素时间戳按**元素自身事件时间**持久化（`TimeEvictor` 按元素时间而非当前水位驱逐），`GlobalWindows + evictor`（`countWindow(size, slide)`）窗口状态有界（≤ evictor 容量）；两个 evictor 回调（evictBefore/evictAfter）均收到 pre-eviction 元素计数。回归锚点：`TestEvictorStateLifecycle`、`TestEvictorIntegration.testEvictionPersistsToStateOnProductionListStatePath`、`TestTimeEvictorIntegration`。

**窗口 ListState 元素类型（plan 1326-2，F-05）**：`WindowedStream` 的 apply/aggregate/reduce/process 从流入 `TypeInformation` 推断 IN 元素类型写入状态描述符——bean 元素在 RocksDB 后端与 Memory-JSON checkpoint 恢复后类型正确。若流入类型不可推断（`UnknownTypeInformation`），工厂打 WARN（bean 元素将以 LinkedHashMap 形态从 RocksDB/JSON 恢复路径返回）。回归锚点：`TestWindowBeanElementTypeRestore`（runtime）、`TestRocksDBWindowListStateBeanElements`（rocksdb）。

### 作业级等价物（三组）

| nop-stream 机制 | 语义 | 配置/锚点 |
|---|---|---|
| `CheckpointConfig` 配置项族 | 周期状态持久化（= 「autocheckpoint 间隔」等价物）：`checkpointInterval`（默认 60000ms）、`checkpointTimeout`（600000）、`barrierAlignmentTimeout`（30000）、`minPause`（500）、`maxConcurrentCheckpoints`（1）、`maxRetainedCheckpoints`（5）、`maxConsecutiveCheckpointFailures`（3）、`asyncSnapshotEnabled/ThreadPoolSize`、`unalignedCheckpointEnabled`（默认 true）+ `unalignedThreshold`（1000）、`maxRestartsPerRegion`（3）、`processingGuarantee`、`storageType`/`storageConfig`、`jobId`/`pipelineId`/`jobTerminationMode` | `CheckpointConfig.java`（core checkpoint 包）（XDSL 面经 `<checkpoint>` 暴露其中的 interval/timeout/processingGuarantee 等子集） |
| processing-time timer | `TimerService.registerProcessingTimeTimer` —— key 粒度定时回调（延迟逻辑/超告警），随 keyed state checkpoint | `TimerService.java`（core time 包） + `HeapInternalTimerService` |
| DRAIN 截断 | `jobTerminationMode=DRAIN` / REST `stop?mode=DRAIN`：终态 checkpoint 后停机，完整处理存量数据（一次性消费全部已有数据后停止） | owner doc「停止作业」节 |

### 与竞品概念对照

| nop-stream 机制 | Spark Structured Streaming | Flink | SeaTunnel |
|---|---|---|---|
| 连续流模型（无作业级 trigger 参数） | 默认 micro-batch（trigger 未指定时尽快连续微批） | 连续流（同族） | 引擎批/流模式（作业级选择，非 trigger 参数） |
| `CheckpointConfig.checkpointInterval` | `Trigger.ProcessingTime(interval)` 的节奏等价物（微批间隔即处理与持久化节奏）+ continuous 模式 `checkpointInterval` | `execution.checkpointing.interval` | 引擎级 checkpoint 参数（Flink/Spark 引擎透传） |
| DRAIN 截断 | `Trigger.Once` / `Trigger.AvailableNow`（一次性消费存量后停止） | stop-with-savepoint `--drain` | 批模式（一次性消费） |
| 窗口级 Trigger 家族（8 trigger + 3 evictor） | 无用户面窗口 Trigger（窗口由微批节奏驱动 fire；`Trigger.Continuous` 实验态除外） | 同族 API（nop-stream 窗口 API 与 Flink 同源，类名/语义一一对应） | 无用户面窗口 Trigger（窗口函数由引擎实现） |
| `CountTrigger` / `ContinuousXxxTrigger` / `DeltaTrigger` / `ProcessingTimeoutTrigger` | 无直接等价（需 foreachBatch 自定义） | 同名类（Flink 兼容；其中 4 类在 nop-stream 为 API 预留 + 专属单测，main 无生产调用方） | 无 |
| processing-time timer（`TimerService`） | 无 timer API（`flatMapGroupsWithState` + timeout 近似） | `TimerService`（同源） | 无 |

一句话结论：Spark 把「触发」做成作业级参数、Flink/nop-stream 把「触发」做成窗口级 Trigger + 作业级 checkpoint 周期；nop-stream 的窗口 Trigger 家族与 Flink 同源同语义，与 Spark 的差异是连续流 vs 微批的模型差异（by design，非缺口）。
