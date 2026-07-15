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

| XDSL | Java DataStream API |
|------|---------------------|
| `<source>` | `env.addSource(fn)` |
| `<map>` | `.map(fn)` |
| `<flatMap>` | `.flatMap(fn)` |
| `<filter>` | `.filter(fn)` |
| `<keyBy>` | `.keyBy(ks)` |
| `<window>` | `.window(wa)` |
| `<aggregate>` | `.aggregate(af)` |
| `<reduce>` | `.reduce(rf)` |
| `<sink>` | `.addSink(fn)` / `.print()` |
| `<union>` | `.union(stream1, stream2)` |
| `<edge partition="HASH">` | `.keyBy()` 隐式创建 |
| `<checkpoint>` | `env.enableCheckpointing(interval)` |

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
利用 x:extends 机制，可以在基础管线模型上叠加差量修改：
```xml
<stream x:extends="base-pipeline.stream.xml">
    <checkpoint interval="10000"/>  <!-- 覆盖 checkpoint 间隔 -->
    <transforms>
        <source id="tx-source" parallelism="4"/>  <!-- 调整并行度 -->
    </transforms>
</stream>
```

### 7.3 多后端适配
同一 StreamModel 可以通过不同的 DeploymentPlanProvider 在不同后端执行：
- LOCAL: `GraphExecutionPlan` + `TaskExecutor`
- DISTRIBUTED: `IStreamExecutionDispatcher` + `JobCoordinator`
- FLINK: `nop-stream-flink`（规划中）转换为 Flink DataStream

## 8. 文件组织

```
_nop/schema/stream/
├── stream.xdef          # 主元模型
├── cep.xdef             # CEP 模式元模型
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
