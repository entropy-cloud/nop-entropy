# nop-stream 用户指南

> 定位：nop-stream **用户面**文档——DataStream API、XDSL 声明式编排、连接器使用、分布式部署、触发语义对照。
> 运维契约（指标名表 / REST 运维 / 健康状态机 / 告警 / 治理 / 运维手册）见 owner doc `03-modules/nop-stream.md`；连接器能力矩阵见 `03-modules/nop-stream-connectors.md`；CDC 生产化操作手册见 `03-modules/nop-stream-cdc-cookbook.md`；版本迁移见 `03-modules/nop-stream-migration-guide.md`。

## 心智模型：三种入口，一种模型

nop-stream 的 canonical 模型是 **StreamModel**（可序列化算子图 + 组件注册表），可由三种入口合成，最终走同一条编译执行管线：

1. **XDSL 声明式定义**（`.stream.xml`，xdef 合同 `/nop/schema/stream/stream.xdef`）——产品推荐主入口
2. **Java DataStream API** 编程构造
3. **Delta 定制合成**（`x:extends` / `_delta` 目录，基于既有 `.stream.xml` 派生）

编译管线为**五阶段**：`StreamModel → StreamGraph → JobGraph → PartitionedPlan → DeploymentPlan`（层数口径裁定见设计文档 01-architecture §四 D71 三视角表；`GraphExecutionPlan` 不是第 6 个管线阶段，它是 LOCAL 模式下 DeploymentPlan 的运行时执行形态）。LOCAL 模式经 `GraphExecutionPlan` + `TaskExecutor` 直接执行；DISTRIBUTED 模式经 `IStreamExecutionDispatcher` 调度（见「分布式部署」节）。

## DataStream API

### 环境与执行

入口类 `io.nop.stream.core.environment.StreamExecutionEnvironment`：

```java
StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
env.setParallelism(2);
env.enableCheckpointing(60_000);           // 周期 checkpoint（ms）
// 语义组合规则：默认 processingGuarantee 为 STRICT_EXACTLY_ONCE（要求 REPLAYABLE source + 2PC sink）。
// fromElements/print 只满足 at-least-once，违配会在 build 期 fail-fast（nop.err.stream.invalid-state），
// 故入门示例显式声明降档（规则详见「连接器使用指引」的语义组合规则条目）。
env.getCheckpointConfig().setProcessingGuarantee(io.nop.stream.core.checkpoint.ProcessingGuarantee.AT_LEAST_ONCE);
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

`WindowedStream<T,K,W>`：`apply(WindowFunction)` / `aggregate(AggregateFunction)` / `reduce(ReduceFunction)` / `process(ProcessWindowFunction)` / `trigger(Trigger)` / `evictor(Evictor)`。**窗口算子执行需要 `nop-stream-runtime` 在 classpath**（缺失时 fail-fast 报 "WindowOperator requires nop-stream-runtime on classpath"，由**固定 FQCN 反射**发现的 `WindowOperatorFactoryImpl` 接线——写死类名的 `Class.forName` 反射而非 ServiceLoader，无 services 文件；第三方不能以 services 文件替换默认实现，需经 `setDefaultFactory` 显式注入。上文 checkpoint 执行器工厂才是真 `ServiceLoader` 发现，两者机制不同）。

能力边界（build 期 fail-fast，不静默降级）——三分口径：① `union` 全支持：Java API `DataStream.union(DataStream...)`（WI6，`UnionTransformation` 进执行图）与 XDSL `<union>` 元素均已落地（锚点 `TestUnionPipelineE2E` / `TestSelfUnionPipelineE2E`）；② 侧输出全支持：**提取 API**（`SingleOutputStreamOperator.getSideOutput(OutputTag)` 返回 `SideOutputDataStream` 视图，经 `sink(...)`/`print()`/`collect(...)` 消费）与 **XDSL 图级路由**（`<sideOutput tag condition>` tap 节点 + `<edge outputTag>` tag 边，WI8/plan 2332，见下节）均已落地；③ 残余边界：side-output 视图上的下游 graph 级 transform（`map`/`filter`/`keyBy` 等）仍 fail-fast（`ERR_STREAM_UNSUPPORTED`——Java 视图仅限 sink/print/collect 消费；XDSL 图级消费请用 `<edge outputTag>`）。锚点：`TestAdvancedTransforms` sideOutput 用例族、`TestSideOutputTapOperator`、`TestSideOutputPipelineE2E`、`SideOutputDataStream` javadoc。

### 窗口生命周期事件与 maxDelay 兜底出数（WI8/plan 2332）

- **生命周期事件**：`windowed.emitWindowEvents()` 启用后，窗口 OPEN/CLOSE/IDLE/RESUME 事件经专用 side-output tag（`<operatorUid>:window-events`）外发（`WindowLifecycleEvent`：type + key + windowStart/End）。OPEN = 首条记录入窗；CLOSE = 清理时间到达（GlobalWindows 无清理时间——仅 OPEN/IDLE、无 CLOSE）；IDLE = 上游水印转 idle；RESUME = 水印恢复或此后首条数据。消费方式：`singleOutputStreamOperator.getSideOutput(tag)`（Java API）。XDSL 声明面暂未开放（Deferred）。
- **maxDelay 兜底出数**：`windowed.maxDelay(ms)` 为 event-time 窗口挂 processing-time 兜底定时器（windowEnd + maxDelay）——watermark 停滞时到点强制出数（timing=EARLY、不清理，allowedLateness 语义兼容；processing-time 窗口配 maxDelay = 构造期 typed 拒绝）。TDengine MAX_DELAY 对标。**注意**：无数据不触发兜底（定时器随窗口创建注册）——watermark 永不推进时 maxDelay 是唯一出数路径（`maxDelayWithoutDataDoesNotFire` 钉定的是「无窗口数据则无兜底出数」）。
- **merge 语义**：session 等合并窗口的 maxDelay 定时器随合并删除/重注册；活动窗口 registry 随 checkpoint 持久化，恢复不重发 OPEN。

### XDSL sideOutput 图级路由（WI8/plan 2332）

`<sideOutput tag condition>` 声明在 transform 节点上，语义是**复制 tap**：该节点上游 transform 的输出记录**原样通过主输出**（tap 不过滤主流），其中匹配 condition 的记录**额外复制**进 tag 通道。tag 通道的消费方是声明了 `outputTag` 的边：

```xml
<transforms>
  <source id="src" bean="mySource"/>
  <sideOutput id="tap" tag="late" condition="event.score &lt; 60"/>
  <map id="tagMap"><source>enrich(event)</source></map>
  <sink id="mainSink" bean="mySink"/>
  <sink id="lateSink" bean="myLateSink"/>
</transforms>
<edges>
  <edge id="e0" from="src" to="tap"/>
  <edge id="e1" from="tap" to="mainSink"/>                        <!-- 主路径：全量记录 -->
  <edge id="e2" from="tap" to="tagMap" outputTag="late"/>          <!-- tag 通道：仅匹配记录 -->
  <edge id="e3" from="tagMap" to="lateSink"/>
</edges>
```

- **condition**：xpl expr，`event` 绑定到记录值（与 `<keyBy keyExpr>` 同绑定），按 truthy 解释（null/0/空 = false）；省略 condition = 全量复制（纯 tap）。求值经统一 expr 求值器（`IEvalAction`），无「不支持的 expr 形态」——非法表达式在 XDSL 解析期即被 xdef 拒绝。
- **tag 目标面**：tag 边的目标 transform 仅支持单输入一进一出形态（`map`/`filter`/`flatMap`/`process`/`custom`）；tag 边进 `sink` 被 typed 拒绝（sink 消费侧输出请走 Java API `getSideOutput(tag)` 视图）。tag 记录经部署期注册的 tag 消费方喂进目标算子链头（`StreamTaskInvokable.registerTagEdgeConsumer`），目标算子无感知。
- **分区语义**：tag 边跟随生产者分区（不携带 partitioner）；声明 `partition`（非 FORWARD）或 `keyExpr` 在 tag 边上一律 build 期 typed 拒绝（`ERR_STREAM_EDGE_ATTR_UNSUPPORTED`）。
- **配对校验（`validateDag`，build 期 typed 拒绝）**：tag id 是全局身份（注册为按 id 替换语义），因此 ① 声明了 tag 但无消费边、② 消费边引用未声明的 tag（from 非声明节点或 tag 不存在，`ERR_STREAM_REF_UNKNOWN`）、③ 同 tag 多条消费边、④ 两个 `<sideOutput>` 节点声明同 tag（`ERR_STREAM_DUPLICATE_ID`）——全部 fail-fast，不静默顶替。
- 运行链路：`StreamEdge.outputTag`（激活既有字段）→ `JobEdge.outputTagId` → 本地 `GraphExecutionPlan` / 远程 `RemoteGraphExecutionPlanBuilder` 双路径为 tag 边目标注册消费方、为生产者接线 tag 感知 writer（主记录不过 tag 边、tag 记录不过主边——无双投递）；tag 边不参与算子链化（始终跨顶点边）。
- 锚点：`TestAdvancedTransforms`（builder/tag 边图断言 + 配对校验矩阵）、`TestSideOutputTapOperator`（tap 复制语义/condition 消费）、`TestSideOutputPipelineE2E`（XDSL → execute → 分流/主流不变全链）、`TestRemoteSideOutputTagWiring`（远程部署路径注册）、`TestSideOutputChainingE2E`（HG-01 跨任务通道）。

### 范围重算与回补作业形态（WI10/plan 2334）

TDengine RECALCULATE/FILL_HISTORY 对标的两个重算作业形态。二者都是**独立有界作业**，复用 savepoint/restore 与 upsert sink 幂等，不引入新引擎机制。

**RECALCULATE（时间范围重放）**：`io.nop.stream.runtime.source.TimeRangeReplayableSource` 包装既有 REPLAYABLE source，按事件时间范围 [from, to)（含头不含尾）过滤重放，下游接 jdbc-2pc `dmlMode=UPSERT` sink 幂等回写。构造契约：`(SourceFunction delegate, TimestampAssigner<T> assigner, long from, long to)`——非 `ReplayableSourceFunction` 的 delegate 构造期 typed 拒绝（该 source 无 seek/offset 语义，不可范围重放）；`to <= from` typed 拒绝。发射语义：`collect` 分支范围内记录经 `collectWithTimestamp(e, 提取时间戳)` 再发射（下游事件时间窗口开箱可用）；`collectWithTimestamp` 分支范围内原样透传；范围外丢弃。offset 语义保留内层（`seek`/`getCurrentOffset`/`snapshotState`/`initializeState` 全套转发）；capability faces 转发（`getSourceConsistency` 逐字转发、`ParallelismCheckable`/`ConnectivityCheckable` 在内层实现时转发——包装 batch-loader 不丢 parallelism=1 防线与连通性探针；内层无探针契约时 `checkConnection` typed 拒绝而非假通过）。

RECALCULATE 作业配方（每条都是硬契约，锚点 `TestRecalculateBackfillE2E`）：

1. **独立提交**：每次重算用**新 jobId + 新 checkpoint storage 目录**——同 jobId 重提交会 auto-restore 已 seek 的 offset，重放为空。
2. **有界 source 提交契约**：只经「作业终结 MAX_WATERMARK 触发窗口出数」进入 2PC sink 的记录**不会被提交**（终结出数后无 checkpoint barrier，开放 epoch 永不 preCommit——`pendingCommits` 保持空）。有界 source 必须 (a) 数据发完后**显式 emitWatermark(≥ 最末窗口 end)** 让窗口在运行中出数，(b) **驻留 ≥ 一个 checkpoint interval**（如 800ms > 200ms）让周期 barrier 完成 preCommit+commit。wrapper 对 watermark 仅透传——终结 watermark 是内层 source 的责任。
3. **幂等回写**：sink 用 `dmlMode=UPSERT` + `keyColumns`（如 `k,win_end`）；泛型（非 ChangeEvent）结果行必须给 `opMapper`（纯重算回写用 `"c"`，重算只重写当前态、无删除）。同 key 重放 = 覆盖同值，表态不变即幂等成立。

**FILL_HISTORY（历史回补）**：`BatchLoaderSourceFunction`（批量装载）+ UPSERT sink 的独立作业；loader 返回空批即自然 EOS 终态（无需 DRAIN/cancel）。重跑回补作业重放全部装载行，UPSERT 同 key 覆盖，无重复行（锚点同 `TestRecalculateBackfillE2E`）。

逐源边界矩阵：

| 内层 source | 范围重放 | 说明 |
|---|---|---|
| `CollectionReplayableSource` | 支持 | offset = 下标；测试/cookbook 基准源 |
| `BatchLoaderSourceFunction` | 支持 | offset = 已发射计数（确定性遍历前提不变）；包装后 parallelism=1 防线经 capability 转发保持 |
| debezium-cdc | 不适用 | offset 是 binlog 位点**无时间语义**——按事件时间 seek 需位点↔时间戳映射表（Deferred）；范围重放请走 collection/batch-loader 路径 |
| 非 REPLAYABLE source（如 fraud demo 的 `DirectoryFileSourceFunction`） | typed 拒绝 | 构造期 `ERR_STREAM_INVALID_ARG`，错误含源类名——无 seek 语义不可范围重放 |

与 WI9 SKIP_GAP 的交互：SKIP_GAP 按**具体 source 类型**分派（仅 `CollectionReplayableSource` 支持），wrapper 包装后一律 typed 拒绝。RECALCULATE 是全新有界作业，正常不触发该路径。

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
    <!-- 参数化窗口（WI10）：<strategy strategyId="tx2" windowFnId="tumbling-event-time" duration="2s"/> -->
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

**参数化窗口声明（WI10）**：`windowFnId="tumbling-event-time"` 搭配 `duration` 属性声明任意时长的 tumbling event-time 窗口（如 `duration="2s"`、`duration="500ms"`）。旧白名单 id（`tumbling-event-time-1s`/`-5s`）继续兼容但不能与 `duration` 同时声明。

**allowedLateness 声明（WI10/D9）**：strategy 级属性或 window 节点级子元素均可声明。allowedLateness > 0 时，迟到记录在 cleanup 时点（window end + allowedLateness）前被接受并重新触发窗口（fire-then-update——迟到更新携带全量聚合）。行为约束：仅 event-time 窗口生效（GlobalWindows / processing-time 路径忽略该声明并保持 eager purge）。

**sideInputs 有限维表注入（WI11/plan 2357，WI0(c) 裁定语义）**：`<sideInputs><sideInput id="dict" from="dictSupplier" to="enrich" broadcast="true"/></sideInputs>` 声明构建期物化的有限维表只读快照——`from` 是 `Supplier<Map<String,Object>>` 类型的 bean id，builder 构建期调用 `get()` 恰一次并**冻结**快照（低频更新=重启作业刷新，bean 运行期不再被调用）；`to` 仅限 map/filter/flatMap 的 xpl-fn 形态（bean 形态 typed 拒绝——绑定到不了 IoC 装配的函数，静默无操作是禁止面）；消费端 xpl 体经 `sideInputs['<id>']` 绑定访问（key=sideInput id）。`broadcast` 仅支持 true（快照复制到所有并行实例的注入方式标记）；xdef 默认值 false 为历史遗留、现 typed 拒绝；无流式广播、无 BroadcastState（vision §七 #G36）。七类结构校验全部前移 validateDag（duplicate id 由 xdef 唯一性在 parse 层先行拒绝；builder 校验为程序化路径防御）。
**与 lookup 形态的差异化判定（四象限）**：①多维表→两形态均可（sideInput 多条声明、lookup 多 bean）；②大维表→**lookup 胜**（sideInput 全量复制有内存放大，lookup 走外部存储/有界本地物化）；③构建期注入→**sideInput 胜**（声明式、构建期一次物化、免 lookup 装配代码）；④共享只读快照→**sideInput 胜**（一处声明多处消费）。判定结论：**互补共存**——sideInput=构建期小/静态维表（字典/配置/码表），lookup=运行时大/热维表（外部存储、有界并发、物化刷新）；E11 富化主线维持 lookup 形态（场景矩阵裁定）。锚点：`TestWi11SideInputDeclaration`（12 用例）、`TestSideInputEnrichmentE2E`（两形态等价性 + 变异探针断线必红）。

**边级流控四属性裁定（WI14/plan 2360）**：边 `flowControlPolicy`/`queueCapacity`/`receiveWindow`/`packetSize` **维持 typed 拒绝**（`nop.err.stream.edge-attr-unsupported`）。裁定依据（对抗性审查 live 查证）：接线存在三个引擎级前置——①声明载体缺失（core StreamModel 无边声明，四属性仅 flow 层 StreamEdgeModel 可见）；②模型 id 与 vertex id（AtomicInteger 计数）空间断裂（无既有映射）；③链化把等并行度+forward 的边融合进单 vertex（最常见线性拓扑的模型边均为链内边，零 EdgePlan/JobEdge 载体）；另 `receiveWindow`/`packetSize` 全仓零行为消费点（接线即空壳）。登记为引擎级 successor（边配置通道+id 映射+链化语义+消费端补齐四项前置）。消费端现状：`EdgeConfig.queueCapacity`→`ResultPartition` 有界队列（真背压）已有 `TestBufferPoolWiring` 覆盖；`FlowControlPolicy` 仅 BLOCKING_QUEUE（G27 永久裁定）。

**生命周期回调裁定（WI13/plan 2359）**：`onStart`/`onEnd`/`onError` 三回调**显式记为 Java-only**——runtime 已有 job 事件总线（`StreamJobEventBus`，JOB_STARTED/JOB_FINISHED/JOB_FAILED 从真实协调器生命周期路径分发），是未来接线的指定缝；但 DSL 接线当前四缺：`streamCtx` 上下文类型未定义（新公共 API 面需授权）、onStart「任务启动前」时机契约未设计、本地 `execute()` 路径无总线覆盖（需动 core 内核）、无 owner 授权（WI0 通道不含 WI13，§六未列举）。声明面维持 fail-fast（`nop.err.stream.not-implemented`）。**未来接线的运行期契约预设**（授权后生效）：onStart=计划构建完成后任务启动前；onEnd=作业终态通知（err=null 表示正常）；onError=失败通知；**回调异常不得吞作业失败**——原始异常优先传播，回调异常仅附加（addSuppressed）不掩盖。交付语映射：时机契约以本记录 + 钉子 token 替代时机行为测试；WI27 的 lifecycle fixture 钉归属不变。重启触发=owner 授权 execute 生命周期监听 API + 四缺失项闭合。

**五注册表族裁定（WI12/plan 2358）**：`<streams>` **已从 schema 移除**（命名流引用无任何消费点，遗留声明由 xdef parse 层拒绝）；`<environments>` **接线**——恰一环境档直接应用其 `deploymentMode`（属性缺省保持 LOCAL 默认），多环境 typed reject（无运行时选择机制），`DISTRIBUTED` + 活动 checkpoint（enabled=true 且 interval>0）组合 typed reject（checkpoint 引擎路径先于分布式分支且不读 deploymentMode，组合下声明惰性；`enabled="false"` + DISTRIBUTED 合法）；`<requirements>` **接线（仅可静态判定子集）**——`TWO_PHASE_COMMIT_SINK` 声明须与图内解析出的 2PC sink bean 一致（声明失真 typed reject；未声明不拒，需求非普查）、`DISTRIBUTED_EXECUTION` 恒真放行、其余枚举值 typed reject（无构建期判定器）；`<checkpointParticipants>` **接线（契约钉）**——participant id 须为图中函数携带型 transform 的 bean 形态且 bean 实现 `CheckpointParticipant`（xpl 形态/无函数型/不实现均 typed reject——参与性由运行时询问函数实例决定）；`<coders>` **显式记为 Java-only**（wire format 在连接器层挂接，错误码保持 NOT_IMPLEMENTED；重启触发=wire format DSL 接入诉求出现）。

**lateData 迟到数据侧输出（WI10/plan 2356）**：`<window strategyRef="..." lateData="late-tag"/>` 把窗口算子丢弃面（skipped 且 `ts + allowedLateness <= watermark` 的记录，`isElementLate` 联动语义——allowedLateness=0 时越过 watermark 的 skipped 记录即 late）从「计数丢弃」改为「发往 tag 通道」。`<window>` 是 virtual build point（无自有顶点，窗口算子属于下游 `<aggregate>/<reduce>` 宿主），因此消费边从**宿主顶点**出发：`<edge from="<宿主 id>" to="<map|filter|flatMap|process|custom>" outputTag="late-tag"/>`。配对校验全部前移 `validateDag`（`validateSideOutputDeclarations` 扩展，build 期 typed 拒绝）：tag 全局唯一（与 `<sideOutput>` tag / 其他 window lateData tag 撞名 → `nop.err.stream.duplicate-id`）、宿主恰一（0 或 >1 → `nop.err.stream.invalid-arg`，不静默取第一宿主）、消费边恰一、from 必须是宿主（否则 `nop.err.stream.ref-unknown`）、目标类型/partition/keyExpr 约束同 tap 边；`lateData=""` 经 xdef 归一化为未声明（平台级可选 string 属性行为）。锚点：`TestWi10LateDataDeclaration`（结构校验 8 用例）、`TestLateDataSideOutputE2E`（数据面：main=[10,120]、late=[50] 反事实断言 + 变异探针断线必红）。

可用 transform 类型：`source`、`timestampsAndWatermarks`、`map`、`flatMap`、`filter`、`keyBy`（`keyExpr`）、`window`（`strategyRef`、`lateData`）、顶层 `<sideInputs>` 注册表（WI11，见下）、`aggregate`、`reduce`、`process`、`join`（`joinRef`，引用 `<joins>` 注册表）、`union`（多入边合并）、`cep`（`patternRef`）、`sink`、`custom`（customType）、`sideOutput`（`tag` + 可选 `condition`，复制 tap 语义，见上节）。边属性：`partition`（FORWARD/HASH，HASH 需 `keyExpr`）、`outputTag`（tag 通道消费边，配对规则见上节）等。CEP 模式在 `<patterns>` 块声明（内联 xpl `<where>` 谓词）。

   **WI27 非对称窗口 join timeout（2026-10-08）**：`<joins>/<joinSpec>` 支持对称 `timeout` 或非对称 `leftTimeout`/`rightTimeout`（duration 格式，恰一或双声明，与 timeout 互斥，未声明侧缺省 0=窗口末即完成）。语义边界：配对在记录到达时按窗口内叉积 eager 完成，配对集合恒等于对称 min(leftTimeout, rightTimeout) 的配对集合（ksql WITHIN(before,after) 窗偏移语义的 narrowing 转译）；非对称的真实效果是 LEFT 未匹配左的 (L,null) 完成时点解耦到 leftTimeout、长侧缓冲保留至自身 fire 点。窗口 join 仅 INNER/LEFT。

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

### 跨 maxParallelism 离线 reshard 恢复（合法路径）

keyBy 在**建图期**从当时配置的状态后端冻结 key-group maxParallelism（未配置时默认 128）。执行期守卫 `StreamExecutionEnvironment.validateKeyedRoutingParity` 要求图内冻结值与当前后端一致——**后置 `setStateBackend` 且 maxParallelism 不同会被响亮拒绝**（`R5 KR-3`），这是防"路由与属主公式分裂"的预期行为，不是缺陷。

若确实要跨 maxParallelism 恢复（例如用 `MaxParallelismReshardMigration` 把旧 checkpoint 128→256 重排后从产物恢复），必须走**建图前配置**入口，让 keyBy 冻结目标值：

```java
StreamExecutionEnvironment env = StreamModelDslBuilder.of(model, resolver)
        .withPreBuildEnvironment(e -> e.getCheckpointConfig()
                .setStateBackend(new RocksDBStateBackend(dir, 256)))  // 在 keyBy 冻结前注入
        .build();
```

- 恢复侧的属主一致性校验（`R5 KR-2`）会以该后端的 maxParallelism 为 live routing 基准比对 checkpoint 戳，迁移产物（已按 256 重排）可正常通过。
- `withPreBuildEnvironment` 可多次调用（按序组合），钩子在模型遍历开始前、`applyCheckpointConfig` 之前执行。
- 端到端样例：fraud-example `TestS2OfflineReshardE2E`（真实 checkpoint → 离线迁移 128→256 → 从迁移产物恢复 → 全输入 exactly-once 断言）。

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

### 声明面支持矩阵（WI1 契约基线，2026-10-09）

> 本矩阵是 XDSL 声明面变化的**单一事实源**（xdsl-full-coverage roadmap Cross-Cutting #7，roadmap 存放于仓库 ai-dev/backlog/ 目录）。放行规则（放行三步法）：每个声明面放行必须同时完成「builder 接线 + 删除对应 fail-fast 钉子测试并改为放行行为断言 + 本矩阵迁移行」，三者缺一不得视为放行。锚点只写类名+方法名（行号易漂移）。

**表 A 已实现声明面**（能力 → 声明形态 → 代码锚点）：

| 能力 | 声明形态 | 锚点（nop-stream-flow builder 包） |
|---|---|---|
| 拓扑算子 | map/flatMap/filter/reduce/keyBy/union/process（bean 或内联 xpl） | `StreamModelDslBuilder.buildMap` 等 |
| 窗口 assigner 参数化 | `windowFnId="tumbling-event-time"` + `duration`；`sliding-event-time` + `duration`+`slide`（slide<=size 守卫）；`session-event-time` + `duration`；legacy 白名单 `global`/`tumbling-global`/`tumbling-event-time-1s`/`-5s`（冻结不移除）；bean 名优先解析。外部口径对照：HOP(t,slide,size)=sliding-event-time duration+slide、SESSION(t,gap)=session-event-time duration。`global` 无 duration 语义（GlobalWindows 无界）——其参数化形态 = GlobalWindows+evictorId+triggerId 组合（即 countWindow 等价物） | `AdvancedTransforms.resolveWindowAssigner` |
| 窗口控制已放行面 | `allowedLateness`（strategy 与节点双处，节点覆盖 strategy）；`maxDelay`（strategy 级兜底出数）；`accumulationMode=ACCUMULATING`；`emit="UPDATE"\|"CLOSE"`（UPDATE 强制 ACCUMULATING + EmitUpdateTrigger） | `AdvancedTransforms.buildWindow` |
| 窗口触发器（WI5） | `triggerId`（strategy 属性 / window 节点子元素，节点覆盖 strategy）：bean 名优先 → 目录 `event-time`/`processing-time`/`count:N`/`continuous-event-time:<dur>`/`continuous-processing-time:<dur>`/`purging:<inner>`/`processing-timeout:<inner>:<dur>` → 未知 id `nop.err.stream.ref-unknown`；DeltaTrigger 与自定义 Trigger 走 bean 形态（Delta 需 DeltaFunction bean）；`emit="UPDATE"` 拥有触发器槽位，与 triggerId 互斥拒绝；适用域：canMerge=false 族（count/purging:count/processing-timeout:/Delta bean）× merging assigner（session-event-time）构建期拒绝 | `AdvancedTransforms.resolveTrigger` |
| 早出结果（WI8） | `earlyResultsPeriod`（strategy 属性 / window 节点子元素，节点覆盖 strategy）：duration → ContinuousEventTimeTrigger（事件时间轴——水位先行一格（wm 过网格边界即以当时全量 pane fire，首 fire 的 pane 只含首元素），封顶窗端；建议 accumulationMode=ACCUMULATING 终值全量）。**触发器槽位三面互斥**（buildWindow 内、任何 resolve 之前按面计数）：triggerId（两级合计一面）/ emit="UPDATE" / earlyResultsPeriod 任二同现即 `nop.err.stream.invalid-arg`；处理时间轴早出经 triggerId 目录 `continuous-processing-time:<dur>`；allowedLateness/evictorId 正交可共存 | `AdvancedTransforms.buildWindow` |
| 窗口驱逐器（WI6） | `evictorId`（strategy 属性 / window 节点子元素，节点覆盖 strategy）：bean 名优先 → 目录 `count:N`（保留最新 N 条）/`time:<dur>`（逐出 ts ≤ pane 内元素最大事件时间 − dur，含等号；无时间戳不逐出）→ 未知 id `nop.err.stream.ref-unknown`；DeltaEvictor 走 bean 形态（需 DeltaFunction）。SPI 时序契约（不变）：两回调均在 fire 路径（evictBefore → 窗口函数 → evictAfter → 幸存者物理写回——GlobalWindows+evictor 状态有界保证）；两回调均收 pre-eviction 元素计数；`emit=UPDATE` 与 evictor 可共存（UPDATE 只拥有 trigger 槽位）；session×evictor 合并由 assigner 层承担（声明层不设限制） | `AdvancedTransforms.resolveEvictor` |
| 聚合 | `aggregate`（bean 与 `aggregatorRef` 恰一；内建目录 sum/count/avg/min/max + WI9 扩充 `maxBy`（保留最大整记录）/`distinct`（窗口内精确 dedup 计数——与 SQL 面差异为作用域非精度）/`composite`（expr=SqlRowAggregateSpec JSON，位置对应 List 输出）；bean 名优先遮蔽内建） | `AdvancedTransforms.resolveAggregatorRef`、`StreamSqlAggregations` |
| 等值 join | `<joins>`/`joinSpec`（INNER/LEFT/RIGHT/FULL）；window join（INNER/LEFT + duration + `timeout`/`leftTimeout`/`rightTimeout`） | `StreamModelDslBuilder.validateJoinDeclarations`、`AdvancedTransforms.buildJoin` |
| CEP | `<patterns>` 声明式（量词/where/until/within/gapWithin/skip 策略） | `AdvancedTransforms.buildCep` |
| sideOutput | `<sideOutput tag condition>` tap 顶点 + `<edge outputTag>` 消费边（配对校验） | `AdvancedTransforms.buildSideOutput`、`StreamModelDslBuilder.validateSideOutputDeclarations` |
| 窗口迟到数据侧输出（WI10） | `<window lateData="tag">` 声明面：迟到记录（skipped 且 ts+allowedLateness≤wm）经 tag 通道发往宿主顶点的下游 transform；tag 全局唯一/宿主恰一/消费边恰一/from=宿主 校验全部前移 validateDag | `StreamModelDslBuilder.validateSideOutputDeclarations`（lateData 段）、`AdvancedTransforms.buildWindow`（sideOutputLateData 接线） |
| sideInputs 有限维表注入（WI11） | `<sideInputs><sideInput id from to broadcast="true"/>`：from=Supplier bean id（构建期 get() 一次物化只读快照并冻结）；to=map/filter/flatMap 的 xpl-fn 形态（bean 形态拒绝）；消费端 xpl 体经 `sideInputs['<id>']` 绑定访问；七类校验（duplicate id xdef 唯一性 parse 层拒绝/broadcast 遗留默认 false 拒绝/to 不存在/to 非 map 族/to bean 形态/from 缺失/from 非 Supplier）全部前移 validateDag | `StreamModelDslBuilder.validateSideInputs`、`materializeSideInputs`、`XplFunctionSupport.newCallScope(bindings)` |
| 事件时间 | `timestampsAndWatermarks`（watermarkStrategyBean 或 xpl assigner+generator 至少一；`watermarkInterval=0` 逐事件） | `AdvancedTransforms.buildTimestampsAndWatermarks` |
| 边 | FORWARD/HASH+keyExpr；per-transform parallelism | `StreamModelDslBuilder.validateEdgeDeclarations`、`applyDeclaredParallelism` |
| 类型 | `<schemas>` 受管九类型（string/int/bigint/smallint/tinyint/float/double/boolean/bytes） | `StreamSchemaRegistry.resolveSchemas` |
| checkpoint 配置族 | enabled/interval/timeout/minPause/processingGuarantee/barrierAlignmentTimeout/maxConsecutiveCheckpointFailures/storageType(+s3)/storageConfig/jobId/pipelineId/jobTerminationMode | `StreamModelDslBuilder.applyCheckpointConfig` |
| 顶层 SQL | `<sql>`（顶层唯一内容；SELECT/WHERE/五聚合+DISTINCT 聚合/HAVING/GROUP BY/TUMBLE/HOP/SESSION/等值 join/UNION ALL/CASE/CAST/全局聚合） | `StreamModelDslBuilder.expandSqlModel`（SPI：nop-stream-sql `ISqlStreamCompiler`） |
| Delta | `x:extends` 显式路径 + 分层 Delta | `TestStreamModelDeltaExtends` |

**表 B fail-fast 面**（声明面 → 锚点 → 钉子测试 → 放行归属）：

| 声明面 | 锚点 | 钉子测试 | 放行归属 |
|---|---|---|---|
| `accumulationMode=ACCUMULATING_AND_RETRACTING` | 同上 | `TestWindowAccumulationModeDeclaration` | 保持拒绝（D10 spec-only，WI38） |
| 白名单外 `windowFnId`（bean 外未知 id） | `AdvancedTransforms.resolveWindowAssigner` default 分支 | `TestWindowAssignerBranches.unknownWindowFnIdFailsFast` | WI7 |
| `<streams>`/`<environments>`/`<requirements>`/`<checkpointParticipants>`/`<coders>` 注册表 | 同上 | `TestStreamModelDslBuilderFailFast`（streams/environments/requirements/checkpointParticipants/coders 各一钉） | WI12 |
| `onStart`/`onEnd`/`onError` 生命周期回调 | `StreamModelDslBuilder.failFastOnUnsupportedRegistries` | `TestStreamModelDslBuilderFailFast.lifecycleCallbacksFailsFast` | 保持拒绝（WI13/plan 2359 裁定 Java-only——runtime 事件总线为指定接线缝，四缺失项闭合前 DSL 面维持 fail-fast） |
| 边级流控四属性（flowControlPolicy/queueCapacity/receiveWindow/packetSize） | `StreamModelDslBuilder.validateEdgeDeclarations`（firstDeclaredFlowControlAttr） | `TestStreamModelEdgeContract`（参数化） | 保持拒绝（WI14/plan 2360 裁定：接线三引擎级前置+两属性零消费点，登记引擎级 successor） |
| source/sink 端点 8 属性（params/maxParallelism/outputType·inputType/consistencyCapability 非默认） | `StreamModelDslBuilder.failFastOnUnsupportedSourceConfig` / `failFastOnUnsupportedSinkConfig` | `TestStreamFlowAuditFixes`（8 钉各一） | WI15 |
| `<custom>` 的 `<params>` 与 `<source>` 体 | `AdvancedTransforms.buildCustom` | `TestAdvancedTransforms.customWithSourceBodyFailsFastInsteadOfSilentDrop` | WI15 |
| `<window>` 上的 parallelism（虚拟元素） | `AdvancedTransforms.buildWindow` | `TestStreamCheckpointAndWindowContract` | 保持拒绝（R9 只做静态诊断） |
| HASH 边 live 拒绝子面：HASH 无 keyExpr、非 HASH 边带 keyExpr、HASH 入 keyBy（冗余）/union、REBALANCE 与 BROADCAST 无消费方 | `StreamModelDslBuilder.validateEdgeDeclarations` | `TestStreamModelEdgeContract`、`TestAdvancedTransforms.hashEdgeIntoUnionFailsFast` | 保持拒绝（R9 只做静态诊断；HASH+keyExpr 进普通 transform 是合法路径） |
| `<sql>` 与 transforms/edges 并存（顶层唯一约束） | `StreamModelDslBuilder.expandSqlModel` | `TestStreamSqlCompiler`（nop-stream-sql） | 维持（WI0(e) 裁定，vision §六 #WI0-XDSL） |

**L1 静态规则映射（WI2，2026-10-09，全部 warning 上线）**：nop-lint XML 规则集 `nop-xdsl-*` 与本矩阵同源——`nop-xdsl-watermark-required`（R5：window/cep 无水位供给）、`nop-xdsl-credential-plaintext`（R7：固定 kind/attr 候选面的明文凭据）、`nop-xdsl-delta-derivation`（R8 收窄子面：派生模型 transforms x:override=replace 整体重写）、`nop-xdsl-edge-partition`（R9 静态子面：HASH 缺 keyExpr / 非 HASH 带 keyExpr / REBALANCE·BROADCAST 声明）、`nop-xdsl-transform-naming`（R10：kebab-case + 端点命名）、`nop-xdsl-undeclared-consumer`（R12：表 B 无消费方面的静态镜像）。规则目录见 `nop-lint/docs/rule-catalog.md`。**L2 模型感知层（WI3）**：`StreamMaintenanceMain lint` 子命令在解析后模型上一次报齐全部无消费方声明（R1/R12 同源）+ R2/R3/R4/R5 提示面 + R6/R13/R14 显式 SKIP 注记，conf-validate 裁定折叠（见 `03-modules/nop-stream.md` 提交前校验节）。**R13（测试接线）的 L1 半边裁定移交**：nop-lint 单文件 AST 无文件路径/注册表信号，该面随 WI21 场景注册表落地时以仓扫 check 工具实现（roadmap R13 行层标记已同步微调）。存量命中基线（2026-10-09 实扫 38 份 .stream.xml）：R5=1、R10=21 站点（13 坏 id + 8 未命名端点；roadmap v3 记 16 为 distinct-id+端点混合口径）、R12=2（负控夹具）、R7=R8=R9=0。

**消号台账（WI4 门禁接线，2026-10-09）**：两层门禁 + 两份机器台账——
- **L1**：`.github/workflows/compliance.yml` 的 `xdsl-lint` 作业对 nop-stream 子树 38 份语料（含 `_vfs/_delta` 层；排除 nop-lint 规则套件 fixtures——其归 TestNopRuleSuites 自门禁）跑六规则，nop-lint-xdsl-baseline.txt（仓库 ai-dev/tools/ 目录，24 entries）+ `--baseline-check --max-warnings 0` 只减不增（指纹为诊断内容 sha256，行号漂移不失效；新违规或消号未再生 baseline 均即红）。
- **L2**：`nop-stream-flow` 的 `TestXdslCorpusLintGate` 仓扫门禁（mvn test 通道）：WARNING 逐站点白名单（R4 存量 7 模型补 `<schemas>` 即消号、R5 存量 1（test-param-agg）补水位即消号、R12 存量 2（WI12/WI15 放行即删））、FAIL 文件级白名单（test 资源树整体 + 5 个 main 资源 demo/quickstart 模型的应用容器 bean 依赖显式登记）、R6/R13/R14 休眠 SKIP 注记不入门禁（激活义务 WI15/WI21）。
- **消号操作语义**：消号 = 修违规/放行 face + **同 PR** 再生 baseline（L1）或删白名单行（L2）——修违规不再生则 stale entries 即红；新增第 7 条规则时须再生 baseline。warn→error 升级条件 = 对应存量消号完毕（两阶段第二阶段）。

## 连接器使用指引

现有连接器族（file / message / jdbc / debezium / batch 桥接）的**逐连接器能力矩阵**（方向、交付语义、并行度、恢复语义）见 `03-modules/nop-stream-connectors.md`。使用要点：

- **语义组合规则**：端到端 exactly-once = 可重放 source（`REPLAYABLE`，offset/cursor 进 checkpoint）+ 两阶段提交 sink（`TWO_PHASE_COMMIT`）。`StreamRequirementValidator` 在 build 期校验：声明 `STRICT_EXACTLY_ONCE` 的管线若组合了 at-least-once source / 非 2PC sink，fail-fast。
- **2PC sink 并行能力**：`TwoPhaseCommitSinkFunction` 族 sink（file/jdbc 内建）支持任意有效并行度——per-subtask 隔离（独立 UDF 拷贝 + 台账复合键 `(epoch_id, subtask_id)` / 文件 `.sK` 后缀），exactly-once 在 P=N 成立（**LOCAL e2e 已证明且 CI 可复现；真实多 JVM 形态为 gated 手动验证**，启用命令见「多 JVM gated 测试（手动启用）」）。跨并行度**恢复**被 typed 拒绝（`ERR_STREAM_2PC_SINK_PARALLELISM_CHANGE_UNSUPPORTED`——恢复时须保持与快照相同的并行度）；未 override `copyForSubtask(int)` 的第三方子类在 P>1 部署期 fail-fast（基类默认）。见连接器指引「并行 2PC 能力说明」。
- **连接器 SPI 注册中心**（item 19）：全部端点连接器已按方向作用域类型名注册（source：`file`/`message`/`debezium-cdc`/`batch-loader`；sink：`file`/`message`/`jdbc-2pc`/`batch-consumer`），维护/探测入口与能力矩阵见 `03-modules/nop-stream-connectors.md`「SPI 注册中心与类型名」。
- **提交前校验**（item 20）：作业提交前可用 `StreamMaintenanceMain conf-validate file=<stream.xml>`（不启动作业即字段级报错）与 `dry-run file=<stream.xml>`（逐 source/sink 连通性探测）先行验证——命令、分层语义、exit code、逐族探测能力表与错误样例见 owner doc `03-modules/nop-stream.md`「提交前校验」节。
- **模型感知 lint**（xdsl roadmap WI3）：`StreamMaintenanceMain lint file=<stream.xml> [--connect]`——最佳实践预检（无消费方声明一次报齐为 WARNING + 类型/水位/声明式优先提示，R6/R13/R14 显式 SKIP 注记），conf-validate 裁定折叠进同一报告（exit 0/1/2 契约同族）；详见 owner doc `03-modules/nop-stream.md`「提交前校验」节 lint 条目。
- **凭据引用**（item 20）：连接器配置字段可写 `credential:{credentialId}#{field}` 引用（如 CDC 的 `databasePassword`）替代明文——引用串随配置序列化/checkpoint 持久（跨 JVM 可再解密），明文只在引擎侧瞬态路径存在；provider 缺失/凭据不存在均 fail-closed 显式报错。详见 owner doc「凭据引用与明文边界」节。
- 每个连接器的恢复语义（光标/offset 的 checkpoint 路径）在目录页逐项标注，此处不重复。

## 分布式部署

### 形态

- **LOCAL**：`env.execute()` 直接在本进程 `TaskExecutor` 执行（默认）。
- **DISTRIBUTED**：`nop-stream-runtime` 提供 `EmbeddedDistributedExecutor`（同 JVM 多 TaskManager）与 `RpcDistributedExecutor`（跨 JVM，控制面经 `IMessageService` 真实 RPC、coordinator↔task RPC proxy、`deployTask` RPC 远端重建 invokable）；数据面跨 JVM 传输经 `DataPlaneMessageServiceAdapter`（`SysDaoWireCodec` / `PulsarStringWireCodec` 线协议）。
- **集群注册/选主**：`JdbcClusterRegistry` / `JdbcLeaderElector`（共享 JDBC 库——H2 AUTO_SERVER 或等价 JDBC；节点自动注册 `StreamNodeAutoRegistration`；fencing epoch 防双主）。

### 启动与停止

多 JVM 独立进程形态的启动命令、REST 生命周期（`POST /jobs/{jobId}/stop?mode=CANCEL|DRAIN`）、savepoint 恢复、状态重置等**运维操作权威步骤见 owner doc `03-modules/nop-stream.md`「运维手册」节**（本文不重复）。用户侧要点：

1. 预置共享 JDBC 库（JDBC 2PC sink 需预建数据表；台账表 `stream_epoch_ledger_v2` 可由 `initializeLedgerTable()` 自动创建——旧 3 列 `stream_epoch_ledger` 表不迁移不读，升级行为见 migration guide「JDBC 2PC 台账 schema」节）。
2. 先启 TaskManager、后启 JobCoordinator（命令模板见 owner doc）。
3. 作业拓扑经 `pipelineFactoryClass`（`ClusterPipelineFactory`）在 coordinator 进程重建——XDSL 管线经 `RemotePipelineResolver` 用与本地完全相同的 `DslModelParser → StreamModelDslBuilder → buildJobGraph` 链在 TM 侧重建，fingerprint 跨 JVM 一致。
4. TM 失败自动 global recovery（fencing epoch 轮转 + 从最近 durable checkpoint 恢复）；健康状态机与告警见 owner doc。

### 入口类现状（WI1 后：main-scope 生产入口）

独立进程入口类 `JobCoordinatorMain` / `TaskManagerMain` 及其支撑类（`SharedJdbcInfrastructure`/`PollingJdbcMessageService`）自 2026-10（plan 2328 / WI1）起位于 `nop-stream-runtime` 的 **main sources**（`io.nop.stream.runtime.launch` 包）——发布 runtime jar 直接含入口类。启动方式：① 启动脚本 `nop-stream/nop-stream-runtime/src/main/scripts/nop-stream-cluster.sh`（classpath 经 `dependency:build-classpath -DincludeScope=runtime` 自动装配；`NOP_STREAM_EXTRA_CLASSPATH` 追加 JDBC 驱动 jar，`jdbcDriver=` 指定驱动类）；② 手工 `java -cp <classpath>`（classpath = runtime jar + `dependency:build-classpath -DincludeScope=runtime` 输出 + 驱动 jar）。多 JVM gated 测试经 runtime test-jar 消费 harness（`MiniStreamCluster`，仍为 test-only）。main-scope 运维面（`OpsJobManager` 多作业模式、`StreamOpsHttpServer` REST、`StreamMaintenanceMain` 维护工具）与入口类同面可用。

### 多 JVM gated 测试（手动启用）

分布式能力（exactly-once 恢复、coordinator failover、fencing、并行 2PC 真实多进程形态等）的端到端测试**默认跳过**（`@EnabledIfSystemProperty(named = "nop.stream.test.multi-jvm.enabled", matches = "true")`），CI 中不运行（F-07 诚实化：文档不再以这些测试声称「CI 可复现的已证明」——它们是**手动验证**锚点）。需要本地/手动复现时：

```bash
# runtime 侧（exactly-once 恢复 / coordinator failover / 健康告警 / 进程拉起）：
./mvnw test -pl nop-stream/nop-stream-runtime -am \
    -Dtest='io.nop.stream.runtime.multijvm.*' \
    -Dnop.stream.test.multi-jvm.enabled=true

# fraud-example 侧（S1/S2 场景、并行 2PC、背压、rescale 恢复、稳定性演练）：
./mvnw test -pl nop-stream/nop-stream-fraud-example -am \
    -Dtest='*MultiJvm*' \
    -Dnop.stream.test.multi-jvm.enabled=true
```

前置条件：本机可编译 test-jar（`-am` 拉起依赖）、多进程 JVM 资源（每测试按场景拉起 2-3 个 JVM）、H2 AUTO_SERVER 可用（控制面共享库）。数据面 Kafka/Pulsar 真 broker gated 测试另有独立开关 `nop.stream.test.kafka.enabled` / `nop.stream.test.pulsar.enabled`（同样手动启用）。

## 触发语义映射表（nop-stream ↔ Spark / SeaTunnel / Flink）

> 背景裁定（P-REQ-16，D-GAP 2026-09-01 go）：nop-stream 是**连续流**模型，不提供 micro-batch 式作业级触发参数（Spark Trigger 族）——触发语义由**窗口级 Trigger 家族** + **作业级等价物**（checkpoint 周期 / processing-time timer / DRAIN 截断）承载。本表每个公开 Trigger/Evictor 类型均有专属单测（锚点见下表）。

### 窗口级 Trigger / Evictor 家族（live 全量）

`io.nop.stream.core.windowing.triggers`（11 文件，含包私有抽象基类 `ContinuousIntervalTrigger`）+ `evictors` + `operators/Triggerable`：

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
| keyed state 只读端点（WI14/plan 2340） | TM 自托管 ops server 可用：`GET /jobs/{jobId}/vertices/{vertexId}/subtasks/{idx}/state` 返回该 subtask 的 keyed stateData 快照（只读，零写回；JC-only 进程 503 `TASK_MANAGER_UNAVAILABLE`；任务未运行 404 `TASK_NOT_FOUND`；无 keyed backend = 空快照）。queryable-state-lite 裁定：不做写回/通用注册表/JC→TM read-RPC | `TaskManager.keyedStateSnapshot` + `StreamOpsHttpServer.handleKeyedStateQuery` |
| `CheckpointConfig` 配置项族 | 周期状态持久化（= 「autocheckpoint 间隔」等价物）：`checkpointInterval`（默认 60000ms）、`checkpointTimeout`（600000）、`barrierAlignmentTimeout`（30000）、`minPause`（500）、`maxConcurrentCheckpoints`（1）、`maxRetainedCheckpoints`（5）、`maxConsecutiveCheckpointFailures`（3）、`asyncSnapshotEnabled/ThreadPoolSize`、`unalignedCheckpointEnabled`（默认 true）+ `unalignedThreshold`（1000）、`maxRestartsPerRegion`（3）、`processingGuarantee`、`storageType`/`storageConfig`、`jobId`/`pipelineId`/`jobTerminationMode`。**storageType=s3（WI13/plan 2339）**：S3 兼容对象存储 checkpoint 后端（`S3CheckpointStorage`），storageConfig 键 `s3.endpoint`/`s3.bucket`（均必填——bucket 同时是 auto-restore 恢复意图标记）/`s3.accessKey`/`s3.secretKey`（secretKey 不回显；credentialId 集成为 follow-up）/`s3.region`/`s3.pathStyleAccess`（默认 true，MinIO 形态）/`s3.basePrefix`（默认 nop-stream-checkpoints）；MinIO 需先建桶；**边界**：s3 覆盖 createStorage 直连入口（嵌入式/单进程），分布式执行器与 savepoint override 仍落 local（Deferred）；gated E2E 锚点 `TestS3CheckpointStorageMinioE2E` | `CheckpointConfig.java`（core checkpoint 包）（XDSL 面经 `<checkpoint>` 暴露其中的 interval/timeout/processingGuarantee 等子集） |
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

> **声明式 SQL 接口**：除 DataStream API 与 XDSL `.stream.xml` 外，nop-stream 另提供 `<sql>` 声明式 SQL 查询入口（SELECT/WHERE/GROUP BY/聚合/TUMBLE/join/UNION ALL 子集），详见 `03-modules/nop-stream-sql.md`。

> **DISTINCT 双口径裁定（WI0(d)，2026-10-09；WI9 修正措辞）**：COUNT(DISTINCT) 的**精确口径**在 `<sql>` SQL 面（见 `03-modules/nop-stream-sql.md`，参数值域精确去重）；XDSL 声明面聚合目录的 `distinct` 聚合为**窗口作用域内的精确 dedup 计数**（与 SQL 面的差异是作用域/生命周期而非精度——内存上界=组内去重值基数，随窗清除）。两口径边界见 vision §六 #WI0-XDSL。
