# nop-stream 重复代码与废弃代码审计

> Status: resolved
> Date: 2026-05-20
> Scope: nop-stream 全部 10 个子模块（344 Java 文件）
> Conclusion: 识别出 5 类共 12 组重复/废弃代码（5,364 行零引用废弃 + ~750 行重复）；提出 6 包扁平化重组方案，经 3 轮对抗性审查修正后定稿

## Context

nop-stream 经历了多次重构：从 Apache Flink 移植核心算法、创建 Nop Platform 简化版接口、增加 CEP 窗口集成。每次重构都留下了新旧共存的代码。本分析的目标是系统性地识别所有重复和废弃代码，为后续清理提供决策依据。

**分析方法**：12 个并行 explore agent 扫描全部 344 个 Java 文件的包结构、类继承关系、import 引用、@Deprecated 注解、注释标记（TODO/FIXME/HACK），以及跨模块引用。辅以直接文件比对、行数统计、以及每个 main 目录下文件的零引用检测（grep 类名排除自身，仅计 main 目录引用）。

---

## 总览

| 严重程度 | 类型 | 组别 | 受影响文件 | 可回收行数 |
|----------|------|------|-----------|-----------|
| **P0-HIGH** | 包级重复 | 1. operator vs operators | 23 文件 / 2 包 | ~4,200 |
| **P0-HIGH** | 类级重复 | 2. CepOperator vs CepWindowOperator | 2 文件 | ~320 |
| **P1-MEDIUM** | 死代码 | 3. runtime 模块大面积死代码 | 10 文件 | **2,854** |
| **P1-MEDIUM** | 死代码 | 4. core 模块死代码 | 21 文件 | **2,510** |
| **P1-MEDIUM** | 实现重复 | 5. TimerService + SimpleInternalTimer | 4 文件 | ~500 |
| **P1-MEDIUM** | 孤立代码 | 6. 图执行路径 | 5+ 文件 | ~1,600 |
| **P2-LOW** | 空壳模块 | 7. 4 个空模块 | 4 pom.xml | 0 |
| **P2-LOW** | 包组织 | 8. state 包分裂 | 4 文件 | 0 |
| **P2-LOW** | 包组织 | 9. sink 单文件包 | 1 文件 | 0 |

**核心数据**：
- nop-stream-core：185 文件 / 17,706 行，其中 **21 文件 / 2,510 行零引用废弃代码（14%）**
- nop-stream-runtime：18 文件 / ~5,000 行，其中 **10 文件 / 2,854 行零引用废弃代码（57%）**
- 整个 nop-stream **无任何外部模块依赖**

---

## 1. [P0] `operator` vs `operators` 包级重复

### 1.1 现状

`nop-stream-core` 中存在两个包名仅差一个 `s` 的包：

| 维度 | `operator`（单数） | `operators`（复数） |
|------|---------------------|----------------------|
| 包名 | `io.nop.stream.core.operator` | `io.nop.stream.core.operators` |
| 版权 | Nop Platform (canonical_entropy) | Apache Software Foundation |
| 文件数 | 3 | 20 |
| 起源 | Nop Platform 新写的简化接口 | 从 Apache Flink 移植的原始接口 |
| import 数 | **17 处**（10 个文件） | 仅 **1 处**（StreamExecutionEnvironment） |

**`operator`（单数）包内容（3 文件）：**

| 文件 | 行数 | 说明 |
|------|------|------|
| `StreamOperator.java` | 64 | 简化接口，6 个方法：getOutputType, getName, initialize, open, close, getChainingStrategy + 内嵌 ChainingStrategy 枚举 |
| `StreamOperatorFactory.java` | 42 | 工厂接口：createStreamOperator(TypeInformation), getParallelism, getName |
| `SimpleStreamOperatorFactory.java` | 51 | 工厂实现，getRawOperator() 返回 Object 类型（设计异味） |

**`operators`（复数）包内容（20 文件）：**

| 文件 | 行数 | 说明 |
|------|------|------|
| `StreamOperator.java` | ~200 | Flink 完整接口，extends CheckpointListener, KeyContext, Serializable，含 open/close/dispose/snapshotState/initializeState 等 |
| `AbstractStreamOperator.java` | ~170 | 算子基类实现 |
| `AbstractUdfStreamOperator.java` | ~160 | UDF 算子基类 |
| `ChainingStrategy.java` | 59 | 独立枚举（4 值：ALWAYS, NEVER, HEAD, HEAD_WITH_SOURCES） |
| `OneInputStreamOperator.java` | ~40 | 单输入算子接口 |
| `InternalTimerService.java` | ~80 | 内部定时器服务接口 |
| 其余 14 个文件 | ~1,100 | Input, Output, ChainingOutput, KeyContext, KeyExtractingOutput, TimestampedCollector, Triggerable, InternalTimer, ProcessingTimeService, StreamMap/Filter/FlatMap/Source/Sink |

### 1.2 混用混乱

`StreamExecutionEnvironment` 同时引用两个包的类：
- import `operators.StreamOperator`（复数）
- 但通过其他文件间接使用 `operator.SimpleStreamOperatorFactory`（单数）

`DataStreamImpl` 使用 `operator.StreamOperatorFactory` 和 `operator.StreamOperator`（单数），而其创建的算子（如 `StreamMap`、`StreamFilter`）实现的是 `operators.OneInputStreamOperator`（复数），后者 extends `operators.StreamOperator`（复数）。

**结果**：两个互不继承的 `StreamOperator` 接口同时存在，`instanceof` 和类型转换极易出错。

### 1.3 ChainingStrategy 重复

`ChainingStrategy` 存在于两处，**均无生产代码引用**：

| 版本 | 位置 | 值数 | 生产代码引用 | 测试代码引用 |
|------|------|------|-------------|-------------|
| 内嵌枚举 | `operator.StreamOperator.ChainingStrategy` | 3（ALWAYS, NEVER, HEAD） | 0 | 1（TestJobGraph.java 全限定引用） |
| 独立类 | `operators.ChainingStrategy` | 4（+HEAD_WITH_SOURCES） | 0（2 处已注释掉） | 6 个测试文件 |

注意：生产代码中仅 `WindowOperator` 和 `TimestampsAndWatermarksOperator` 有 `ChainingStrategy.ALWAYS` 的**已注释掉**的代码行。

### 1.4 交叉依赖

- `operator` 包的 `SimpleStreamOperatorFactory.getRawOperator()` 返回 `Object` 而非 `operators.StreamOperator`，说明单数包的工厂设计并未考虑与复数包的算子体系兼容
- 无任何文件从 `operator` 包 import 到 `operators` 包或反之（包间无编译依赖，仅通过运行时类型转换桥接）

### 1.5 建议

**统一到 `operators`（复数）包**，删除 `operator`（单数）包。理由：
1. 复数包是从 Flink 移植的完整体系，20 个文件形成了完整的算子抽象层
2. 所有实际算子（StreamMap/Filter/FlatMap/Source/Sink）都在复数包中
3. 复数包的 StreamOperator 有更完整的生命周期管理（state、checkpoint）

迁移工作：
1. 将 `operator.StreamOperatorFactory` 和 `SimpleStreamOperatorFactory` 移入 `operators` 包
2. 修改返回类型从 `operator.StreamOperator` 改为 `operators.StreamOperator`
3. 更新所有 10 个引用文件的 import
4. 统一 `ChainingStrategy` 到复数包的独立类（保留 HEAD_WITH_SOURCES）
5. 删除 `operator` 包

---

## 2. [P0] CepOperator vs CepWindowOperator 代码重复

### 2.1 基本信息

| 维度 | CepOperator | CepWindowOperator |
|------|-------------|-------------------|
| 模块 | nop-stream-cep | nop-stream-runtime |
| 路径 | `cep/operator/CepOperator.java` | `runtime/operators/windowing/cep/CepWindowOperator.java` |
| 行数 | 631 | 466 |
| 版权 | Apache Software Foundation | Nop Platform |
| 基类 | `AbstractUdfStreamOperator` | `AbstractUdfStreamOperator` |
| 接口 | `OneInputStreamOperator<IN, Map<String, List<IN>>>` | `OneInputStreamOperator<IN, Void>` |
| 生产引用 | fraud-example | **0** |
| 测试引用 | 多个 CEP 测试 | TestCepWindowOperator（597 行） |

### 2.2 重复分析

两个类共享以下高度相似的逻辑块：

| 共享逻辑块 | 相似度 | 说明 |
|-----------|--------|------|
| NFA 初始化和编译（`open()`） | ~95% | 相同的 NFACompiler.compile() + SharedBuffer 创建 |
| State 初始化（computationStates, elementQueueState, partialMatches） | ~95% | 字段定义完全相同，但两个类中都被注释掉 |
| 事件处理核心流程（processElement → advanceNFA） | ~80% | CepWindowOperator 在此基础上增加窗口聚合 |
| Pattern 匹配结果收集（processMatch） | ~85% | 相同的 SharedBuffer 查询逻辑 |
| 定时器回调 | ~70% | CepOperator 用 event-time timer，CepWindowOperator 用 window fire |

**CepWindowOperator 独有逻辑**：
- `InternalWindowFunction.apply()` 集成——在窗口触发时对窗口内事件做 CEP 匹配
- `Window` 类型参数和窗口生命周期管理
- 将 CEP 匹配结果作为窗口函数输出

### 2.3 关键问题

1. **CepWindowOperator 无生产代码引用**——仅有 TestCepWindowOperator 测试文件（597 行）
2. **两者都存在 state 未初始化问题**——`initializeState()` 中关键代码被注释掉，运行时必然 NPE
3. **代码来源不同但功能高度重叠**——CepWindowOperator 是从 CepOperator 复制后改编的
4. **辅助类同样仅有测试引用**：CepWindowAssigner（88 行）和 CepWindowTrigger（133 行）仅在 TestCepWindowOperator 中使用

### 2.4 建议

**删除 CepWindowOperator + CepWindowAssigner + CepWindowTrigger + TestCepWindowOperator**

理由：
- CepWindowOperator 无生产代码引用，state 初始化不可用（NPE）
- 687 行主代码 + 597 行测试 = 1,284 行可删除
- 如果未来需要 CEP+Window 集成，应在 CepOperator 之上构建 Window 适配层

---

## 3. [P1] runtime 模块大面积死代码

nop-stream-runtime 模块 18 个主文件中，**10 个文件（57%）在所有生产代码中无任何引用**：

| 文件 | 行数 | 版权 | 状态 |
|------|------|------|------|
| `EvictingWindowOperator.java` | 511 | Apache | 零引用，EvictingWindowOperator 的实现 |
| `CepWindowOperator.java` | 466 | Nop | 零引用，与 CepOperator 重复（见 §2） |
| `LocalFileCheckpointStorage.java` | 358 | Nop | 零引用，检查点本地存储 |
| `CheckpointCoordinator.java` | 338 | Nop | 零引用，检查点协调器 |
| `SimpleInternalTimerService.java` | 319 | Apache | 零引用，与 WindowOperatorTimerService 功能重叠（见 §5） |
| `JdbcCheckpointStorage.java` | 218 | Nop | 零引用，JDBC 检查点存储 |
| `TimestampsAndWatermarksOperator.java` | 215 | Nop | 零引用，时间戳/水位线算子 |
| `BarrierAligner.java` | 208 | Nop | 零引用，barrier 对齐器 |
| `CepWindowTrigger.java` | 133 | Nop | 零引用，仅被 TestCepWindowOperator 引用 |
| `CepWindowAssigner.java` | 88 | Nop | 零引用，仅被 TestCepWindowOperator 引用 |

**总计 2,854 行零引用废弃代码。**

仅以下 runtime 文件有生产引用：
- `WindowOperator.java`（1,168 行，12 处引用）——核心类
- `WindowOperatorTimerService.java`（175 行，被 WindowOperator 引用）
- `MergingWindowSet.java`（254 行，被 WindowOperator 引用）
- `InternalWindowFunction.java`（~50 行，接口定义）
- `AlignedBarrier.java`（71 行，被 CheckpointCoordinator 引用——但后者自身无引用）
- `CheckpointMetrics.java` / `CheckpointMetricsSnapshot.java`（被 CheckpointCoordinator 引用）

### 分析

整个 checkpoint 子系统（CheckpointCoordinator + 2 个 Storage + BarrierAligner + AlignedBarrier + Metrics）形成了**内部连通但外部孤立的代码岛**：这些类相互引用，但不被任何生产代码使用。虽然存在测试代码，但测试验证的是本身未被使用的功能。

### 建议

1. **立即可删除**（无生产引用 + 无近期接入计划）：
   - CepWindowOperator + CepWindowTrigger + CepWindowAssigner（687 行 + 597 行测试）
   - EvictingWindowOperator（511 行）
   - TimestampsAndWatermarksOperator（215 行）
   - SimpleInternalTimerService（319 行）

2. **保留但标注为未对接**（有设计价值但未接入）：
   - CheckpointCoordinator + Storage 实现 + BarrierAligner（~822 行）
   - 这些类代表 checkpoint 功能的设计原型，保留待后续对接

---

## 4. [P1] core 模块死代码

nop-stream-core 模块 185 个主文件中，**21 个文件（14%，2,510 行）在所有生产代码中无任何引用**：

### 4.1 图执行路径（已在 §6 详述）

| 文件 | 行数 |
|------|------|
| `JobGraphGenerator.java` | 474 |
| `StreamGraphGenerator.java` | 413 |

### 4.2 未使用的 Trigger / Evictor

| 文件 | 行数 | 说明 |
|------|------|------|
| `ProcessingTimeoutTrigger.java` | 168 | 处理时间超时触发器 |
| `ContinuousEventTimeTrigger.java` | 144 | 连续事件时间触发器 |
| `TimeEvictor.java` | 138 | 时间驱逐器 |
| `ContinuousProcessingTimeTrigger.java` | 136 | 连续处理时间触发器 |
| `DeltaEvictor.java` | 111 | Delta 驱逐器 |
| `DeltaTrigger.java` | 98 | Delta 触发器 |

**小计：795 行**。这些是 Flink 移植的完整触发器/驱逐器实现，但当前无任何算子使用它们。

### 4.3 未使用的 Accumulator（7 个文件）

| 文件 | 行数 |
|------|------|
| `AverageAccumulator.java` | 93 |
| `DoubleMinimum.java` | 92 |
| `LongMaximum.java` | 91 |
| `IntMinimum.java` | 91 |
| `IntMaximum.java` | 91 |
| `DoubleMaximum.java` | 91 |
| `ListAccumulator.java` | 65 |

**小计：614 行**。完整的累加器实现，无任何引用。

### 4.4 未使用的 Function 接口

| 文件 | 行数 |
|------|------|
| `TwoPhaseCommitSinkFunction.java` | 88 |
| `CoMapFunction.java` | 56 |
| `CheckpointedSourceFunction.java` | 35 |

**小计：179 行**。

### 4.5 其他

| 文件 | 行数 |
|------|------|
| `StreamConstants.java` | 11 |
| `execution/package-info.java` | 12 |
| `jobgraph/package-info.java` | 12 |
| `StreamGraph.java`（部分方法可能被测试引用，但类本身未被生产路径使用） | — |

### 建议

- **Trigger/Evictor（795 行）**：保留，标注为 `@Internal` + Javadoc "移植自 Flink，未接入"。这些是通用组件，后续接入窗口机制时会用到。
- **Accumulator（614 行）**：同上，保留但标注。
- **Function 接口（179 行）**：保留，标注为"API 定义，预留扩展"。
- **图执行路径（887 行）**：见 §6。

---

## 5. [P1] TimerService 实现重复 + SimpleInternalTimer 内部类重复

### 5.1 接口层（非重复）

`TimerService` 接口存在于两处，但**定位不同，不是重复**：

| 接口 | 包 | 方法数 | 用途 |
|------|-----|--------|------|
| `core.time.TimerService` | `io.nop.stream.core.time` | 6 | 完整 Flink API（currentProcessingTime, currentWatermark, register*/delete*） |
| `cep.time.TimerService` | `io.nop.stream.cep.time` | 1 | CEP 专用子集（仅 currentProcessingTime），用于 NFA 条件求值 |

### 5.2 实现层重复

`InternalTimerService` 有两个实现类：

| 维度 | SimpleInternalTimerService | WindowOperatorTimerService |
|------|---------------------------|--------------------------|
| 路径 | `runtime/operators/windowing/` | `runtime/operators/` |
| 行数 | 319 | 175 |
| 泛型 | `<K, W extends Window>` | `<K, N>` |
| 去重机制 | HashSet（O(1)） | PriorityQueue.contains()（O(n)） |
| 版权 | Apache Software Foundation | Nop Platform |
| **生产引用** | **0** | **1**（WindowOperator L233） |

**关键发现**：SimpleInternalTimerService 是死代码（零引用），WindowOperatorTimerService 是正在使用的实现。

### 5.3 SimpleInternalTimer 内部类重复

两个实现类各自包含一个 `SimpleInternalTimer` 内部类：

| 维度 | SimpleInternalTimerService 内部版 | WindowOperatorTimerService 内部版 |
|------|----------------------------------|----------------------------------|
| 可见性 | `public static` | `private static` |
| 泛型 | `<K, W extends Window>` | `<K, N>` |
| 实现 | `InternalTimer<K, W>` | `InternalTimer<K, N>` |
| 字段 | timestamp, key, namespace | timestamp, key, namespace |
| 去重 | 使用 Keyed + PriorityComparable | 手动实现 equals/hashCode |

这两个内部类功能完全相同，仅泛型约束不同（Window vs N）。

### 5.4 建议

**删除 SimpleInternalTimerService（319 行）**。理由：
1. 零生产引用，是死代码
2. WindowOperatorTimerService 是实际在用的实现
3. 如未来需要更高效的去重（O(1) vs O(n)），可在 WindowOperatorTimerService 中引入 HashSet

如保留 WindowOperatorTimerService 中的 SimpleInternalTimer 内部类，可考虑将其提取为独立类（泛型改为 `<K, N>`），这样未来其他 TimerService 实现也能复用。

---

## 6. [P1] 孤立的图执行路径

### 6.1 现状

nop-stream-core 中存在一条完整的图执行路径，但**生产代码中从未使用**：

```
StreamGraphGenerator → StreamGraph → JobGraphGenerator → JobGraph → TaskExecutor → Task
```

| 文件 | 行数 | 生产引用 | 测试引用 |
|------|------|---------|---------|
| `execution/TaskExecutor.java` | 391 | **0** | TestTaskExecutor, TestEndToEndPipeline |
| `execution/Task.java` | 306 | **0**（仅 TaskExecutor 引用） | 间接通过 TaskExecutor 测试 |
| `jobgraph/JobGraphGenerator.java` | 474 | **0** | TestJobGraphGenerator |
| `graph/StreamGraph.java` | 252 | **0** | TestStreamGraphGenerator |
| `graph/StreamGraphGenerator.java` | 413 | **0** | TestStreamGraphGenerator |

**总计 1,836 行代码只服务于测试。**

实际执行路径 `StreamExecutionEnvironment.execute()` 使用 chain+push 模式，完全不经过 StreamGraph/JobGraph/TaskExecutor。

### 6.2 验证方法

逐文件搜索了所有 `import` 语句，确认：
- 没有任何 main 目录下的 Java 文件（排除自身）import 或引用这 5 个类
- StreamExecutionEnvironment.execute() 中无任何条件分支会创建 StreamGraph/JobGraph/TaskExecutor
- 无配置属性、SPI/META-INF、IoC bean 注册会触发这些类

### 6.3 建议

保留但标注为未对接。这些类代表"完整路径"的设计原型，未来可能需要对接。建议：
1. 在类头部添加 `@Internal` + Javadoc "设计原型，当前执行路径未使用"
2. 在 architecture.md 中明确记录两条执行路径的状态

---

## 7. [P2] 空壳模块

4 个子模块完全空壳，无任何 Java 源码：

| 模块 | 规划用途 | 状态 |
|------|---------|------|
| `nop-stream-api` | 公共 API 接口定义 | 空，连 src/ 目录都不存在 |
| `nop-stream-checkpoint` | 检查点模块 | 空 |
| `nop-stream-flink` | Flink 后端适配 | 空 |
| `nop-stream-flow` | NopFlow 声明式编排 | 空 |

保留 pom.xml 但在文档中标注"规划中，未实现"。

---

## 8. [P2] `core/state` vs `core/common/state` 包分裂

| 包 | 文件数 | 内容 | 引用数 |
|-----|--------|------|--------|
| `core.state` | 4 | Keyed, KeyExtractorFunction, PriorityComparable, PriorityComparator | 9 |
| `core.common.state` | 18+ | Flink State API 类型（State, ValueState, MapState, ListState, Descriptor, Backend） | 93 |

两者不是重复，但包名易混淆。建议将 `core.state` 的 4 个工具类移入 `core.operators` 包（它们只服务于 InternalTimer）。

---

## 9. [P2] `core/sink` 单文件包

`sink/PrintSink.java` 是 `core.sink` 包的唯一文件。可移入 `operators` 或 `util` 包。

---

## 综合建议优先级

| 优先级 | 动作 | 删除/回收行数 | 风险 |
|--------|------|-------------|------|
| **P0-1** | 统一 operator/operators 包 | ~157 行（3 文件） | 中（需更新 10 文件 import） |
| **P0-2** | 删除 CepWindowOperator + 辅助类 + 测试 | 1,284 行（4 文件） | 低（无生产引用） |
| **P1-1** | 删除 SimpleInternalTimerService | 319 行 | 低（零引用） |
| **P1-2** | 删除 EvictingWindowOperator | 511 行 | 低（零引用） |
| **P1-3** | 删除 TimestampsAndWatermarksOperator | 215 行 | 低（零引用） |
| **P1-4** | 标注图执行路径 + checkpoint 子系统为未对接 | 0（标注） | 无 |
| **P1-5** | 标注未使用的 Trigger/Evictor/Accumulator/Function 为预留 | 0（标注） | 无 |
| **P2-1** | 合并/重命名 state 包 | 移动 4 文件 | 低 |
| **P2-2** | 移动 PrintSink | 移动 1 文件 | 无 |

**立即可安全删除的代码总量**：~2,486 行（5 个文件组，全部零生产引用）

---

## Open Questions

- [ ] `operator`（单数）包的 StreamOperator 接口的方法签名（getOutputType, getName, initialize）是否在复数版中有对应？如果没有，是否需要保留这些方法？
- [ ] 图执行路径（TaskExecutor/JobGraph）是否计划在近期对接？如果计划对接，标注为未对接即可；如果不计划，应考虑移到 test scope
- [ ] CepWindowOperator 的 CEP+Window 集成场景是否仍有需求？如果有，应先修复 state 未初始化 bug
- [ ] CheckpointCoordinator + Storage 实现是否近期有接入计划？如果没有，可考虑整体移到独立分支保存
- [ ] 未使用的 Trigger/Evictor 是否应该保留作为"API 预留"？或者移到独立 module？

---

## 附录 A：数据收集方法

| 批次 | Agent/工具 | 任务 | 关键发现 |
|------|------------|------|---------|
| 第 1 批 | explore ×3 | 包结构、类继承、跨模块引用 | 344 文件、StreamOperator 双重定义、无外部消费者 |
| 第 1 批 | 直接工具 | 行数统计、import 引用计数 | 定量数据 |
| 第 2 批 | explore ×4 | StreamOperator 详细对比、CepOperator 对比、全量重复搜索、包结构分析 | operator vs operators、CepOperator 重复、TimerService 重复 |
| 第 2 批 | 直接工具 | TimerService/State 文件比对、行数统计 | State 包分裂确认 |
| 第 3 批（验证） | explore ×5 | CepWindowOperator 引用验证、全量类名搜索、图执行路径验证、connector/fraud-example 审计、ChainingStrategy 验证 | **CepWindowOperator 有测试**、**SimpleInternalTimerService 是死代码**（非 WindowOperatorTimerService）|
| 第 3 批 | 直接工具 | 全文件零引用检测、行数求和 | **runtime 57% 死代码**、**core 14% 死代码** |

## 附录 B：nop-stream-core 零引用文件完整清单

| 文件 | 行数 | 分类 |
|------|------|------|
| `jobgraph/JobGraphGenerator.java` | 474 | 图执行路径 |
| `graph/StreamGraphGenerator.java` | 413 | 图执行路径 |
| `windowing/triggers/ProcessingTimeoutTrigger.java` | 168 | 未使用 Trigger |
| `windowing/triggers/ContinuousEventTimeTrigger.java` | 144 | 未使用 Trigger |
| `windowing/evictors/TimeEvictor.java` | 138 | 未使用 Evictor |
| `windowing/triggers/ContinuousProcessingTimeTrigger.java` | 136 | 未使用 Trigger |
| `windowing/evictors/DeltaEvictor.java` | 111 | 未使用 Evictor |
| `windowing/triggers/DeltaTrigger.java` | 98 | 未使用 Trigger |
| `common/accumulators/AverageAccumulator.java` | 93 | 未使用 Accumulator |
| `common/accumulators/DoubleMinimum.java` | 92 | 未使用 Accumulator |
| `common/accumulators/LongMaximum.java` | 91 | 未使用 Accumulator |
| `common/accumulators/IntMinimum.java` | 91 | 未使用 Accumulator |
| `common/accumulators/IntMaximum.java` | 91 | 未使用 Accumulator |
| `common/accumulators/DoubleMaximum.java` | 91 | 未使用 Accumulator |
| `common/accumulators/ListAccumulator.java` | 65 | 未使用 Accumulator |
| `common/functions/sink/TwoPhaseCommitSinkFunction.java` | 88 | 未使用 Function |
| `common/functions/co/CoMapFunction.java` | 56 | 未使用 Function |
| `common/functions/source/CheckpointedSourceFunction.java` | 35 | 未使用 Function |
| `StreamConstants.java` | 11 | 常量 |
| `execution/package-info.java` | 12 | 包信息 |
| `jobgraph/package-info.java` | 12 | 包信息 |

## 附录 C：nop-stream-runtime 零引用文件完整清单

| 文件 | 行数 | 分类 |
|------|------|------|
| `windowing/EvictingWindowOperator.java` | 511 | 未使用算子 |
| `windowing/cep/CepWindowOperator.java` | 466 | 与 CepOperator 重复 |
| `checkpoint/storage/LocalFileCheckpointStorage.java` | 358 | 未接入 Checkpoint |
| `checkpoint/CheckpointCoordinator.java` | 338 | 未接入 Checkpoint |
| `windowing/SimpleInternalTimerService.java` | 319 | 与 WindowOperatorTimerService 功能重叠 |
| `checkpoint/storage/JdbcCheckpointStorage.java` | 218 | 未接入 Checkpoint |
| `operators/TimestampsAndWatermarksOperator.java` | 215 | 未使用算子 |
| `checkpoint/barrier/BarrierAligner.java` | 208 | 未接入 Checkpoint |
| `windowing/cep/CepWindowTrigger.java` | 133 | CepWindowOperator 辅助类 |
| `windowing/cep/CepWindowAssigner.java` | 88 | CepWindowOperator 辅助类 |

---

## 10. 建议的包结构与模块分工

> 以下设计基于当前代码审计结果、设计文档约束（architecture.md, core-design.md, cep-design.md）、Nop 平台惯例（对照 nop-batch-core、nop-message-core 的包组织），以及 3 轮对抗性审查（Oracle + 2 个 Explore agent）的修正。
>
> 状态：已通过对抗性审查，审查意见已纳入

### 10.1 设计原则

1. **扁平领域包**：遵循 Nop 平台惯例（`nop-batch.core.consumer`、`nop-batch.core.loader`），使用 2 层深度的领域驱动包名，避免过深嵌套
2. **依赖单向**：`core ← runtime`，`core ← cep`，`core ← connector`，不允许反向依赖
3. **接口与实现分离**：core 定义接口 + 简单实现，runtime 提供 WindowOperator 等重型实现
4. **最小可见性**：内部实现类用 `@Internal` 标注，公共 API 保持稳定契约
5. **务实优先**：当前无外部消费者，不做过早拆分。模块级结构保持不变，仅重组包内部

### 10.2 模块级结构（不变）

```
nop-stream/
├── nop-stream-core          核心抽象 + 简单执行引擎
├── nop-stream-runtime       窗口/定时器/检查点实现
├── nop-stream-cep           CEP 引擎（独立可用）
├── nop-stream-connector     Source/Sink 适配层
├── nop-stream-fraud-example 欺诈检测演示
│
│  ── 保留 pom.xml，标注"规划中" ──
├── nop-stream-api           [空]
├── nop-stream-checkpoint    [空]
├── nop-stream-flink         [空]
└── nop-stream-flow          [空]
```

**不拆新模块的理由**：无外部消费者，拆分只增加 pom 维护成本。

### 10.3 nop-stream-core 包结构（重组后）

**当前问题**：42 个包，`operator`/`operators` 分裂，`common/*` 嵌套过深，`state` 包分裂。

**对比 Nop 平台惯例**：`nop-batch-core` 使用扁平领域包（`consumer/`、`loader/`、`processor/`、`filter/`），2 层深度。`nop-message-core` 更扁平（仅 `local/`、`reflection/`）。nop-stream 当前 42 个包远超 Nop 常规。

**建议**：合并为 6 个一级包，按领域概念组织，深度不超过 2 层：

```
io.nop.stream.core/
│
├── datastream/               [公共 API] 用户直接使用的流式 API（保留当前包名）
│   ├── DataStream.java
│   ├── DataStreamImpl.java
│   ├── KeyedStream.java
│   ├── KeyedStreamImpl.java
│   ├── WindowedStream.java
│   ├── WindowedStreamImpl.java
│   └── SingleOutputStreamOperator.java
│
├── operator/                 [算子体系] 合并 operator/ + operators/，统一入口
│   │                         当前：operator(单数, 3文件, 18 imports) + operators(复数, 20文件, 45 imports)
│   │                         合并后：保留 operators 版 StreamOperator（完整生命周期 + checkpoint）
│   ├── StreamOperator.java           ← 保留 operators 版（extends CheckpointListener, KeyContext, Serializable）
│   ├── OneInputStreamOperator.java
│   ├── AbstractStreamOperator.java
│   ├── AbstractUdfStreamOperator.java
│   ├── StreamOperatorFactory.java    ← 从 operator(单数) 移入，改返回类型
│   ├── SimpleStreamOperatorFactory.java
│   ├── ChainingStrategy.java         ← 保留独立类版（4 值），删除 operator.StreamOperator 内嵌版
│   ├── InternalTimerService.java     ← 定时器接口（仅 4 文件引用，留在 operator 包内）
│   ├── InternalTimer.java
│   ├── Triggerable.java
│   ├── ProcessingTimeService.java
│   ├── Output.java
│   ├── ChainingOutput.java
│   ├── KeyExtractingOutput.java
│   ├── Input.java
│   ├── KeyContext.java
│   ├── TimestampedCollector.java
│   └── (内置算子：StreamMap, StreamFilter, StreamFlatMap, StreamSource, StreamSink)
│
├── state/                    [状态抽象] 合并 common/state/ + state/（工具类移入）
│   ├── State.java / StateDescriptor.java
│   ├── ValueState.java / ValueStateDescriptor.java
│   ├── MapState.java / MapStateDescriptor.java
│   ├── ListState.java / ListStateDescriptor.java
│   ├── InternalListState.java / AppendingState.java
│   ├── KeyedStateStore.java
│   ├── VoidNamespace.java / VoidNamespaceSerializer.java
│   ├── CheckpointListener.java
│   ├── (工具类从 state/ 移入：Keyed, PriorityComparable, PriorityComparator, KeyExtractorFunction)
│   └── backend/memory/MemoryKeyedStateBackend.java
│
├── window/                   [窗口抽象] 重命名 windowing/ → window/（语义更精确）
│   ├── WindowAssigner.java          ← 保留二级子包（assigners/, triggers/, evictors/, windows/）
│   │   ... (TumblingEventTimeWindows, SlidingEventTimeWindows, GlobalWindows, MergingWindowAssigner)
│   ├── Trigger.java / TriggerResult.java
│   │   ... (EventTimeTrigger, CountTrigger, + 未使用的标注 @Internal)
│   ├── Evictor.java
│   │   ... (CountEvictor, + 未使用的标注 @Internal)
│   └── Window.java / TimeWindow.java / GlobalWindow.java
│
├── common/                   [通用基础设施] 保留当前 common/ 结构，不做拆分
│   ├── eventtime/            ← 18 文件，完整的 watermark 体系，Oracle 审查指出提案遗漏了此包
│   │   (WatermarkStrategy, WatermarkGenerator, TimestampAssigner, WatermarkOutput, ...)
│   ├── functions/            ← 保留当前名，不拆 source/sink/impl/co 子包
│   │   (MapFunction, FilterFunction, FlatMapFunction, ReduceFunction, AggregateFunction, 
│   │    SourceFunction, SinkFunction, RuntimeContext, ...)
│   ├── typeinfo/             ← 保留当前名
│   ├── typeutils/            ← 保留当前名
│   ├── accumulators/         ← 未使用的累加器标注 @Internal
│   └── state/simple/         ← SimpleKeyedStateStore（状态简单实现）
│
├── environment/              [执行环境] 保留当前包名
│   └── StreamExecutionEnvironment.java
│
├── transformation/           [内部] Transformation DAG 节点
│
├── graph/                    [内部-未对接] StreamGraph + StreamGraphGenerator（标注 @Internal）
├── execution/                [内部-未对接] TaskExecutor + Task（标注 @Internal）
├── jobgraph/                 [内部-未对接] JobGraphGenerator（标注 @Internal）
│
├── streamrecord/             [内部] StreamRecord, StreamElement, Watermark
├── time/                     [内部] TimerService（用户可见接口，6 方法）
├── checkpoint/               [内部] OperatorSnapshotResult, TaskStateSnapshot
├── configuration/            [内部] Configuration
├── exceptions/               [内部] 异常类
├── sink/                     [内部] PrintSink（可合并到 operator）
└── util/                     [内部] Collector, OutputTag, Clock
```

**关键设计决策说明**：

1. **为什么 `common/` 保留不拆**：Oracle 审查指出 `common/` 下有 `eventtime/`（18 文件）、`functions/`（~20 文件）等较大子包，拆分后每个子包都太小。Nop 平台惯例也使用 `common` 作为基础设施包。保留 `common/` 结构，仅合并 `common/state/` + `state/` → `state/`。

2. **为什么 `timer/` 不作为独立包**：Oracle 审查指出 timer 相关仅 4 文件（TimerService, InternalTimerService, InternalTimer, ProcessingTimeService），其中 InternalTimerService/InternalTimer/Triggerable/ProcessingTimeService 已在 `operator/` 包内。TimerService（用户接口）留在 `time/`，内部定时器留在 `operator/`。

3. **为什么保留 `datastream/` 而非拆出 `api/`**：与 Nop 平台惯例一致（`nop-batch-core` 的 API 直接在 core 包内，不单独拆 api 模块）。

### 10.4 nop-stream-runtime 包结构（清理后）

清理死代码后保留 8 文件，~2,200 行：

```
io.nop.stream.runtime/
├── operators/
│   ├── windowing/
│   │   ├── WindowOperator.java              (1168 行，核心)
│   │   ├── MergingWindowSet.java            (254 行)
│   │   ├── InternalWindowFunction.java      (接口)
│   │   └── functions/                       (窗口函数接口)
│   ├── WindowOperatorTimerService.java      (175 行，定时器实现)
│   └── TimestampsAndWatermarksOperator.java (215 行，标注 @Internal)
│
├── checkpoint/                (标注"设计原型，未接入执行路径")
│   ├── CheckpointCoordinator.java
│   ├── PendingCheckpoint.java
│   ├── metrics/
│   ├── barrier/
│   └── storage/
│       ├── LocalFileCheckpointStorage.java
│       └── JdbcCheckpointStorage.java
│
└── (已删除：CepWindowOperator, EvictingWindowOperator, SimpleInternalTimerService)
```

### 10.5 对抗性审查结论

Oracle + 2 个 Explore agent 审查后，以下问题已纳入修正：

| 审查意见 | 原提案 | 修正后 |
|---------|--------|--------|
| import 数描述不准确 | "operator 17 处，operators 1 处"（仅计 StreamOperator 接口） | 修正为 operator(单数) 18 总 import，operators(复数) 45 总 import |
| `timer/` 独立包过度设计 | 提议 timer/ 为 6 个一级包之一 | 取消，timer 相关留在 operator/ 和 time/ |
| 遗漏 `eventtime/` 包 | 未提及 18 个 watermark 文件 | 保留在 common/eventtime/ |
| 深层嵌套违反 Nop 惯例 | 提议 window/assigners/, state/backend/memory/ 等 | 改为 2 层深度，保留 window/ 的子包（领域确实需要） |
| `function/` 拆 4 子包 | 提议 source/, sink/, impl/, co/ | 改为保留 common/functions/ 扁平结构 |
| Step 2 风险被低估 | 标注"中风险" | 两个 StreamOperator 接口不兼容（方法签名无交集），需重写 SimpleStreamOperatorFactory |
| nop-stream-api 模块 | 提议保留空壳 | 确认保留空壳，等外部消费者出现时再拆 |
| CEP 独立性声明 | "CEP 只需 core 类型系统" | 修正：CEP 的 CepOperator 依赖 core 的 operator/state 抽象层，非仅类型系统 |

### 10.6 依赖关系图

```
                    ┌─────────────┐
                    │ fraud-example│
                    └──────┬──────┘
                           │
                    ┌──────▼──────┐
                    │     cep     │
                    └──────┬──────┘
                           │ (依赖 operator/state 抽象 + NFA 自身)
        ┌──────────────────┼──────────────────┐
        │                  │                  │
 ┌──────▼──────┐   ┌───────▼───────┐   ┌─────▼──────┐
 │  runtime    │   │   connector   │   │  (未来:    │
 │ (Window,    │   │ (Source/Sink) │   │   flow)    │
 │  Checkpoint)│   └───────┬───────┘   └────────────┘
 └──────┬──────┘           │
        │                  │
        └────────┬─────────┘
                 │
          ┌──────▼──────┐
          │    core     │
          │ (抽象 +     │
          │  简单执行)   │
          └─────────────┘
```

### 10.7 迁移路径

建议分 3 步执行，每步可独立验证：

**第 1 步：删除死代码（低风险，~2 min）**
- 删除 CepWindowOperator + CepWindowAssigner + CepWindowTrigger + TestCepWindowOperator（1,284 行）
- 删除 EvictingWindowOperator（511 行）
- 删除 SimpleInternalTimerService（319 行）
- 验证：`mvn test` 通过（TestCepWindowOperator 随主代码一起删除）

**第 2 步：统一 operator 包（中风险，~1 h）**
- 删除 `operator`（单数）包的 StreamOperator + 内嵌 ChainingStrategy 枚举
- 将 StreamOperatorFactory + SimpleStreamOperatorFactory 移入 `operators`（复数）包
- **重写 SimpleStreamOperatorFactory**：getRawOperator() 返回 Object → 改为返回 operators.StreamOperator
- 更新 13 个 main 文件 + 若干 test 文件的 import
- 验证：`mvn test` 通过

**第 3 步：包重命名和整理（可选，低优先级，~2 h）**
- `common/state/` + `state/` → `state/`（将 state/ 工具类移入 common/state/ 或反之）
- `windowing/` → `window/`
- 删除 `sink/`（PrintSink 移入 `operators/`）
- 验证：`mvn test` 通过

**不做的重命名**（Oracle 审查建议保留）：
- `common/functions/` → 保留，不拆子包
- `common/typeinfo/` + `common/typeutils/` → 保留，不值得重命名
- `common/eventtime/` → 保留，18 文件的完整领域包
