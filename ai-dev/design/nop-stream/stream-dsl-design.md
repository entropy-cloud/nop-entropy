# nop-stream 声明式 DSL 设计

> Status: active
> Created: 2026-07-15
> Parent: `01-architecture-baseline.md`

## 1. 定位与目标

nop-stream DSL 是基于 XLang 元模型（xdef）的声明式流处理定义语言，用于以 XML 形式描述流处理管线的拓扑结构、算子配置、窗口策略和容错参数。

**核心目标**：
1. **三入口合一**：XDSL 定义、Java DataStream API、Delta 定制最终生成同一套 canonical StreamModel
2. **可逆计算**：支持 x:extends 继承和 x:override 合并，实现模型复用和差量定制
3. **可移植性**：StreamModel 与执行后端解耦，同一模型可在 local/runtime/flink 等不同后端执行

## 2. 设计参考

| 参考来源 | 借鉴内容 |
|----------|----------|
| `task.xdef` | 根元素 + xdef:define 复用模式、bean-package 映射、生命周期回调 |
| `batch.xdef` | 组件注册表模式（loader/processor/consumer）、 listeners 回调、retry/throttle 策略 |
| `workflow.xdef` | 图模式执行、条件分支、并行执行 |
| Apache Flink | Transformation DAG 模型、WindowingStrategy、ProcessingGuarantee |
| Apache Beam | Pipeline + PTransform 模型、窗口/触发器/累加模式 |

## 3. 元模型结构

```
stream.xdef (根)
├── checkpoint          → CheckpointConfigModel（容错配置）
├── environment         → StreamEnvironmentModel（运行环境）
├── windowingStrategies → WindowingStrategyModel[]（窗口策略注册表）
├── coders              → CoderModel[]（序列化器注册表）
├── schemas             → StreamSchemaModel[]（Schema 注册表）
├── requirements        → StreamRequirement[]（能力需求）
├── checkpointParticipants → String[]（checkpoint 参与者）
├── transforms          → TransformModel[]（DAG 顶点）
│   ├── source          → StreamSourceModel
│   ├── map             → StreamMapModel
│   ├── flatMap         → StreamFlatMapModel
│   ├── filter          → StreamFilterModel
│   ├── keyBy           → StreamKeyByModel
│   ├── window          → StreamWindowModel
│   ├── aggregate       → StreamAggregateModel
│   ├── reduce          → StreamReduceModel
│   ├── process         → StreamProcessModel
│   ├── cep             → StreamCepModel
│   ├── sink            → StreamSinkModel
│   ├── union           → StreamUnionModel
│   ├── sideOutput      → StreamSideOutputModel
│   └── custom          → StreamCustomModel
├── edges               → StreamEdgeModel[]（DAG 边）
├── patterns            → CepPatternGroupModel[]（CEP 模式）
├── onStart             → xpl（启动回调）
├── onEnd               → xpl（结束回调）
└── onError             → xpl（异常回调）
```

## 4. 核心设计决策

### 4.1 为什么用 DAG（transforms + edges）而不是链式

**选择**: transforms（顶点）+ edges（边）的显式 DAG 模式

**原因**:
- 流处理管线天然是 DAG（多 source、多 sink、union、side output）
- Java DataStream API 的链式调用在 XDSL 中难以表达分支和合并
- 显式边便于声明分区策略（FORWARD/HASH/REBALANCE/BROADCAST）
- 与 StreamGraphGenerator 的输入格式一致，减少转换层

**对比**: batch.xdef 使用 loader→processor→consumer 的链式模型，因为批处理天然是线性管线。流处理更复杂，需要 DAG。

### 4.2 组件注册表模式

**选择**: 在 StreamModel 根级别定义 windowingStrategies/coders/schemas 注册表

**原因**:
- 复用 batch.xdef 的组件注册表模式
- 窗口策略可在多个 transform 间共享（通过 strategyRef 引用）
- 序列化器和 Schema 可独立演进，不绑定到具体算子
- 与 StreamComponents 的 Java 结构完全对应

### 4.3 CEP 模式内联 vs 外部引用

**选择**: 支持两种方式
1. **内联**: `<patterns>` 直接定义在 `<stream>` 中
2. **外部引用**: `<cep patternRef="xxx"/>` 引用独立的 .cep.xml 文件

**原因**:
- 简单场景内联更方便
- 复杂模式（如欺诈检测的 4 个模式）独立维护更清晰
- 与 batch.xdef 的 processor/consumer 外部 bean 引用模式一致

### 4.4 边分区策略

**选择**: 在 `<edge>` 上声明 partition 策略

**原因**:
- 分区策略是边的属性（数据在两个算子间如何分发）
- FORWARD（一对一）、HASH（按 key）、REBALANCE（轮询）、BROADCAST（广播）覆盖主要场景
- keyExpr 仅在 HASH 分区时需要，其他分区忽略

### 4.5 Checkpoint 配置位置

**选择**: 在 `<stream>` 根级别配置，而不是在每个算子上

**原因**:
- Checkpoint 是管线级配置，不是算子级配置
- 与 CheckpointConfig 的 Java 结构一致
- 算子级的 checkpoint 行为通过 CheckpointParticipant 声明

## 5. 与 Java API 的映射

`StreamModelDslBuilder`（`nop-stream-flow`）按拓扑顺序遍历 `transforms`/`edges`，逐节点将声明式 transform 翻译为 DataStream API 调用。下表给出每个 xdef 节点对应的 API：

| XDSL | Java DataStream API | 函数指定方式 | 备注 |
|------|---------------------|--------------|------|
| `<source bean="..."/>` | `env.addSource(fn, name)` | `bean` 属性 → `BeanFunctionResolver.resolve(bean, SourceFunction.class)` | 经 NopIoC `BeanContainer` 查找 |
| `<source><source>xpl</source></source>` | `env.addSource(fn, name)` | 内联 xpl → `XplSourceFunction` 包装 `IEvalFunction` | xpl 体接收 `SourceContext` |
| `<map bean="..."/>` | `.map(fn)` | `bean` 属性 → `MapFunction` | — |
| `<map><source>xpl</source></map>` | `.map(fn)` | 内联 xpl → `XplMapFunction` | xpl 体形如 `(event)=>any` |
| `<flatMap>` | `.flatMap(fn)` | `bean` 或内联 xpl (`XplFlatMapFunction`) | xpl 体形如 `(event,out)=>void`，`out` 绑定 `Collector` |
| `<filter>` | `.filter(fn)` | `bean` 或内联 xpl (`XplFilterFunction`) | 返回值按 truthy 转 `boolean` |
| `<keyBy keyExpr="!expr"/>` | `.keyBy(selector)` | `keyExpr`（`IEvalAction`）包装为 `EvalActionKeySelector` | 产出 `KeyedStream`，**不是** `DataStream` |
| `<window strategyRef="..."/>` | `keyedStream.window(assigner)` | 从 `<windowingStrategies>` 解析 strategy → `WindowAssigner` | 输入必须是 `KeyedStream`，产出 `WindowedStream` |
| `<aggregate bean="..."/>` | `windowedStream.aggregate(fn)` | **仅** `bean` 属性（AggregateFunction）；xdef 未声明内联 xpl | 输入必须是 `WindowedStream` |
| `<reduce bean="..."/>` / `<reduce><source>xpl</source></reduce>` | `keyedStream.reduce(fn)` 或 `windowedStream.reduce(fn)` | `bean` 或内联 xpl (`XplReduceFunction`) | 按上游流类型分发到 `KeyedStream` / `WindowedStream` |
| `<process bean="..."/>` | `dataStream.process(fn)` 或 `keyedStream.process(fn)` | `bean` 属性（`ProcessFunction` / `KeyedProcessFunction`） | 按上游流类型分发 |
| `<cep patternRef="..."/>` | `CEP.pattern(stream, pattern)` + `patternStream.process(fn)` | 从 `<patterns>` 或外部 `.cep.xml` 加载 `CepPatternModel` → `CepPatternBuilder` | 上游必须是 `KeyedStream` |
| `<union>` | `dataStream.union(other1, other2)` | edges 多入边识别 | **runtime-API-gap**：`DataStream.union()` 尚未在 `nop-stream-core` 实现（多输入算子），builder fail-fast 抛 `UnsupportedOperationException`（见 plan Deferred 节） |
| `<sideOutput tag="..."/>` | `SingleOutputStreamOperator.getSideOutput(OutputTag)` | — | **runtime-API-gap**：`getSideOutput` 尚未在 `nop-stream-core` 实现（仅 `ProcessFunction.Context.output` 可发射，无检索 API），builder fail-fast（见 plan Deferred 节） |
| `<timestampsAndWatermarks>` | `dataStream.assignTimestampsAndWatermarks(strategy)` | 内联 xpl 编译为 `TimestampAssigner` + `WatermarkGenerator` | — |
| `<custom customType="..."/>` | `dataStream.transform(...)` 自定义算子 | 从 bean 注册表查找算子工厂 | — |
| `<sink bean="..."/>` | `.sink(fn)` | `bean` 属性 → `SinkFunction` | — |
| `<sink><source>xpl</source></sink>` | `.sink(fn)` | 内联 xpl → `XplSinkFunction` | xpl 体形如 `(event)=>void` |
| `<edge partition="HASH">` | `.keyBy()` 隐式或显式 `PartitionTransformation` | — | FORWARD = 默认链式 |
| `<checkpoint interval="..."/>` | `env.enableCheckpointing(interval)` + `env.getCheckpointConfig().setXxx(...)` | — | `<checkpoint>` 子元素逐项翻译 |

**关键约束**：
- `reduce` / `window` / `aggregate` 出现在 `KeyedStream` 或 `WindowedStream` 上，**不是** `DataStream`：`reduce(fn)` 同时存在于 `KeyedStream` 和 `WindowedStream`；`window(assigner)` 在 `KeyedStream` 上；`aggregate(fn)` 在 `WindowedStream` 上。builder 跟踪当前流的类型状态以正确分发。
- 两种函数指定方式（`bean` 属性 + 内联 `<source>xpl</source>`）在所有支持内联函数的节点上等价；`aggregate` 是例外——xdef 第 156 行 `xdef:ref="StreamTransformModel"` 继承 `bean` 属性但未声明 `<source>` 子元素，因此 AggregateFunction **只能经 `bean` 属性**提供。
- 解析的 `transforms` 子类型按 `xdef:bean-sub-type-prop="type"` 判别：builder 用 Java `instanceof` 在 `StreamSourceModel`/`StreamMapModel`/... 之间分发，对未知或未实现类型 fail-fast 抛 `UnsupportedOperationException`。

## 6. 与 StreamModel Java 类的对应

| xdef 模型 | Java 类 |
|-----------|---------|
| `StreamModel` | `io.nop.stream.core.model.StreamModel` |
| `CheckpointConfigModel` | `io.nop.stream.core.checkpoint.CheckpointConfig` |
| `WindowingStrategyModel` | `io.nop.stream.core.windowing.WindowingStrategy` |
| `StreamSourceModel` | `io.nop.stream.core.transformation.SourceTransformation` |
| `StreamSinkModel` | `io.nop.stream.core.transformation.SinkTransformation` |
| `StreamEdgeModel` | `io.nop.stream.core.graph.StreamEdge` |
| `StreamRequirementModel` | `io.nop.stream.core.model.StreamRequirement` |

## 7. 扩展点

### 7.1 自定义算子
通过 `<custom>` 节点扩展新的算子类型，只需指定 bean 名称和参数。

### 7.2 Delta 定制

`.stream.xml` 支持 Nop 平台的可逆计算 Delta 定制机制。Delta 在**模型层**叠加差量修改，合并后的 `flow.model.StreamModel` 经既有 `StreamModelDslBuilder` 构建+执行，与手写的等价管线行为一致。加载机制由标准 `DslModelParser` 提供（`stream.xdef` 声明 `xdef:support-extends="true"`），无需专用解析器。

**不变量**：Delta 只能修改模型（transforms/edges/config），不能 patch runtime object 来改变语义（vision 不变量 #10）。合并后的模型受 Stage 50 已有的全部 fail-fast 约束——delta 不绕过任何检查。

#### 两种 Delta 入口

| 入口 | 用法 | 激活条件 | 典型场景 |
|------|------|----------|----------|
| `x:extends` (显式 base path) | `x:extends="/nop/stream/test/base.stream.xml"` | 始终生效（解析时合并） | 在特定基础管线上叠加差量，产出独立文件 |
| `_delta/<layer>/` (目录分层) | `_delta/default/nop/stream/test/xxx.stream.xml` + `x:extends="super"` | `default` 层自动激活；自定义层需 `nop.core.vfs.delta-layer-ids=<layer>` | 覆盖基础产品管线（不改原文件、保留升级路径） |

**`x:extends="super"`** 在 `_delta/<layer>/` 上下文中引用同 VFS 路径的 base 模型（pre-delta）。**`x:extends="/path"`** 引用任意 base 模型。

#### 覆盖语义

`transforms` / `edges` 是 keyed list（`xdef:key-attr="id"`）。XDSL 合并规则：
- **同 id 的 transform/edge**：delta 覆盖 base 的属性（delta 属性优先，未指定的继承 base）。
- **delta 新增 id**：追加到合并结果。
- **删除**：`x:override="remove"`。
- **整体替换**：`x:override="replace"`。

顶层非 keyed 元素（如 `<checkpoint>`）：delta 的属性覆盖 base 的对应属性。

#### Fingerprint 与 Delta

`core.model.StreamModel.computeFingerprint()` 哈希 DAG 拓扑身份（transform id 集合 → `dagTopologyHash`、requirements、checkpointParticipants），**不哈希** checkpoint interval / parallelism 等运行时配置。因此：

- **transform 级 delta**（增/删 transform 或改 transform id）：transform id 集合变化 → fingerprint 变化。**敏感。**
- **config-only delta**（仅改 checkpoint interval / parallelism，transforms/edges 不变）：transform id 集合不变 → fingerprint 不变。**by-design 不敏感**（fingerprint = DAG 拓扑身份，非运行时配置）。

精确 fingerprint **相等**（XDSL-built vs Java-API-built 产生相同指纹）结构上不可能（`Transformation.id` 静态自增 + `toString` 未重写），属 Stage 50 独立 successor，不在 Delta 关注范围。

#### 示例

`x:extends` 显式 base path（transform 级 delta）：
```xml
<stream x:schema="/nop/schema/stream/stream.xdef"
        xmlns:x="/nop/schema/xdsl.xdef"
        x:extends="/nop/stream/test/base.stream.xml">
    <transforms>
        <filter id="newFilter">
            <source>return event.size() > 0;</source>
        </filter>
    </transforms>
    <edges>
        <edge id="e1" from="map" to="newFilter" partition="FORWARD"/>
        <edge id="e2" from="newFilter" to="out" partition="FORWARD"/>
    </edges>
</stream>
```

`_delta/default/` config-only delta（仅改 checkpoint）：
```xml
<stream x:schema="/nop/schema/stream/stream.xdef"
        xmlns:x="/nop/schema/xdsl.xdef"
        x:extends="super">
    <checkpoint interval="10000"/>
</stream>
```

**自定义 layer** 需显式激活（非 default 层不自动生效）：
```java
@NopTestProperty(name = "nop.core.vfs.delta-layer-ids", value = "prod")
```

#### 验证（Stage 51）

测试覆盖见 `nop-stream-flow` 测试类：`TestStreamModelDeltaExtends`（x:extends + _delta/default/ 执行正确性 + delta-unique 断言）、`TestStreamModelDeltaFingerprint`（transform 级敏感 + config-only by-design）、`TestStreamModelDeltaFailFast`（delta 不绕过 fail-fast）。每个 delta 用例用 delta-unique 属性断言（delta 引入的 transform id / 仅 delta 能产生的输出）证明 delta 确被应用，而非误加载 base。

### 7.3 多后端适配
同一 StreamModel 可以通过不同的 DeploymentPlanProvider 在不同后端执行：
- LOCAL: `GraphExecutionPlan` + `TaskExecutor`
- DISTRIBUTED: `IStreamExecutionDispatcher` + `JobCoordinator`
- FLINK: `nop-stream-flink`（规划中）转换为 Flink DataStream

## 8. 文件组织

```
_nop/schema/stream/
├── stream.xdef          # 主元模型
├── pattern.xdef         # CEP 模式元模型（由 `<patterns>` 内联引用，或外部 .cep.xml 引用）
└── resource-spec.xdef   # 资源规格元模型

application/
├── pipeline.stream.xml  # 流处理管线定义
├── patterns/
│   ├── rapid-transaction.cep.xml
│   └── account-takeover.cep.xml
└── _delta/
    └── prod/
        └── pipeline.stream.xml  # 生产环境差量定制
```
