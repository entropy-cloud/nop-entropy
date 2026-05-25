# 维度 03：API 表面积与契约一致性

## 第 1 轮（初审）

### [维度03-01] nop-stream-api 为空壳模块但被加入构建

- **文件**: `nop-stream/nop-stream-api/pom.xml:1-15`
- **证据片段**:
  ```xml
  <artifactId>nop-stream-api</artifactId>
  <!-- placeholder, planned but not implemented -->
  ```
- **严重程度**: P2
- **现状**: nop-stream-api 在根 pom.xml 的 `<modules>` 中声明为子模块，但没有任何 Java 源代码（src/main/java 为空）。所有核心接口（SourceFunction、SinkFunction、StreamOperator、IStateBackend、ICheckpointStorage 等）全部定义在 nop-stream-core 中，而非 nop-stream-api。
- **风险**: 外部消费者依赖 nop-stream-api 时期望获得 API-only 的轻量依赖（不含实现），但实际拿到的是空 JAR。这破坏了 Maven 模块分层约定（api 模块应仅含接口，core/runtime 模块含实现）。当项目规模增长后，缺少 api 模块会导致所有下游直接传递依赖 nop-stream-core 的内部类。
- **建议**: 将 nop-stream-core 中所有 `public interface`/`public abstract class`（如 SourceFunction、SinkFunction、StreamOperator、IStateBackend、ICheckpointStorage、DataStream、KeyedStream、StateDescriptor 及其子类）提取到 nop-stream-api 模块中，使 api 模块成为无实现依赖的纯接口包。
- **误报排除**: 这不是"未完成的计划功能"的合理搁置——接口已经存在于 nop-stream-core 中，问题在于模块边界划分错误。nop-stream-api 的空壳状态意味着 API 契约无法独立于实现被消费。
- **复核状态**: 未复核

---

### [维度03-02] connector 包 5 个公共接口/类从未被实现或引用（死 API）

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/connector/DynamicSplitRequest.java:12-14`、`DynamicSplitResponse.java:12-15`、`RestrictionTracker.java:12-21`、`WatermarkEstimator.java:12-21`、`SourceWorkUnit.java:16-51`
- **证据片段**:
  ```java
  // DynamicSplitRequest.java - 无任何实现类
  public interface DynamicSplitRequest extends Serializable {
      double getFraction();
  }

  // DynamicSplitResponse.java - 无任何实现类
  public interface DynamicSplitResponse<R> extends Serializable {
      R getPrimary();
      R getResidual();
  }

  // RestrictionTracker.java - 无任何实现类
  public interface RestrictionTracker<R> extends Serializable {
      boolean tryClaim(R restriction);
      R getRestriction();
      Object getProgress();
      Object snapshotWatermarkEstimatorState();
  }
  ```
- **严重程度**: P2
- **现状**: 这 5 个 `public` 类型位于 `io.nop.stream.core.connector` 包中，是 Flink SplitEnumerator/Source 模式的预定义接口，但经全局搜索确认：DynamicSplitRequest、DynamicSplitResponse、RestrictionTracker 在整个仓库中无任何 `implements` 或 `import`（除自身定义外）；WatermarkEstimator 仅被 RestrictionTracker 的方法签名和 SourceWorkUnit 的字段引用；SourceWorkUnit 无任何消费者。
- **风险**: 作为 public API 暴露了未经实现的契约，误导用户以为这是可扩展点。这些接口全部使用裸 `Object` 类型，缺乏类型安全。如果未来真的需要这些抽象，当前的签名可能需要重设计，届时这些已发布的 public 接口将成为破坏性变更的源头。
- **建议**: 将这些接口标记为 `@Internal` 或降级为 package-private，或者移除它们直到有实际实现需求。
- **误报排除**: 这不是"预留接口的正常状态"——即使预留，也应该有至少一个内部实现或测试驱动。这些接口连内部引用都没有（除循环引用外），属于纯死代码。
- **复核状态**: 未复核

---

### [维度03-03] OperatorSnapshotResult / StateSnapshot 使用 Map<String, Object> 而非类型安全结构

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/checkpoint/OperatorSnapshotResult.java:22-24`、`StateSnapshot.java:15`
- **证据片段**:
  ```java
  // OperatorSnapshotResult.java
  private final Map<String, Object> operatorStates;
  private final Map<String, Object> keyedStates;
  private final Map<String, Object> rawKeyedStates;

  // StateSnapshot.java
  private final Map<String, Object> stateData;
  ```
- **严重程度**: P2
- **现状**: 状态快照和检查点的核心数据结构全部使用 `Map<String, Object>` 存储状态。OperatorSnapshotResult 的三个 map 字段、StateSnapshot 的 stateData 字段、以及 WindowAggregationState 的 4 个 `Map<String, Object>` 字段，共同构成了检查点子系统的核心数据模型，全部缺少类型参数。
- **风险**: (1) 序列化/反序列化时无法在编译期发现类型不匹配；(2) 所有消费者都需要显式强制转型，运行时 ClassCastException 风险高；(3) 无法通过 IDE 重构追踪状态字段的使用关系。
- **建议**: 引入类型安全的状态表示，例如 `StateSnapshot` 可参数化为 `StateSnapshot<K>` 并内部使用 `Map<String, StateEntry<K>>`。如果短期不可行，至少在公共方法上增加注释并记录每个 key 对应的 value 类型契约。
- **误报排除**: 这不是"内部实现细节"——`OperatorSnapshotResult` 是 `StreamOperator.snapshotState()` 的公共返回类型，这是一个公共 API 契约的类型安全问题。
- **复核状态**: 未复核

---

### [维度03-04] SourceWorkUnit 使用多个 Object 类型字段破坏类型安全

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/connector/SourceWorkUnit.java:22-27`
- **证据片段**:
  ```java
  @DataBean
  public class SourceWorkUnit implements Serializable {
      private final String sourceId;
      private final String splitId;
      private final Object restriction;
      private final TaskLocation owner;
      private final long sizeEstimate;
      private final Object progress;
      private final Object watermarkEstimatorState;
  }
  ```
- **严重程度**: P2
- **现状**: `SourceWorkUnit` 作为 `@DataBean`（公共数据对象），其 `restriction`、`progress`、`watermarkEstimatorState` 三个字段都是 `Object` 类型，丢失了所有类型信息。
- **风险**: 消费者无法知道 restriction 的实际类型，只能通过运行时 `instanceof` 检查。这违反了 Java 泛型的基本实践。
- **建议**: 将 `SourceWorkUnit` 参数化为 `SourceWorkUnit<R>`，让 `restriction`、`progress`、`watermarkEstimatorState` 的类型得到约束。
- **误报排除**: 虽然当前 SourceWorkUnit 没有被使用（与发现 02 相关），但作为 public @DataBean，它仍然是一个已发布的公共 API 契约，类型安全缺陷需要独立记录。
- **复核状态**: 未复核

---

### [维度03-05] 3 个占位子模块（checkpoint/flink/flow）被加入根 pom 构建但无源码

- **文件**: `nop-stream/nop-stream-checkpoint/pom.xml:12-14`、`nop-stream/nop-stream-flink/pom.xml:12-14`、`nop-stream/nop-stream-flow/pom.xml:12-14`
- **证据片段**:
  ```xml
  <artifactId>nop-stream-checkpoint</artifactId>
  <!-- placeholder, planned but not implemented -->
  ```
- **严重程度**: P3
- **现状**: 加上 nop-stream-api，共有 4 个占位模块在根 pom.xml 的 `<modules>` 中被构建。每个模块只有 pom.xml 和 IDE 配置文件，无任何 Java 源码。
- **风险**: (1) 增加 CI 构建时间；(2) 消费者在 Maven Central 搜索时可能错误依赖这些空 JAR；(3) 如果这些模块的 artifactId 在未来改变了职责，已发布的空 JAR 坐标将成为混淆源。
- **建议**: 在空模块的 pom.xml 中添加 `<skip>true</skip>` 或在根 pom 中注释掉这些模块，直到有实际代码。
- **误报排除**: 这不是"计划功能的正常预留"——4 个空壳模块中有 3 个与 checkpoint/flink/flow 的职责重叠（checkpoint 相关代码已经完全在 nop-stream-core 和 nop-stream-runtime 中实现）。
- **复核状态**: 未复核

---

### [维度03-06] CoFlatMapFunction 和 CoMapFunction 在全仓库中无任何消费者或实现

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/functions/co/CoFlatMapFunction.java:44-45`、`CoMapFunction.java:39`
- **证据片段**:
  ```java
  public interface CoFlatMapFunction<IN1, IN2, OUT> extends StreamFunction, Serializable {
      void flatMap1(IN1 value, Collector<OUT> out) throws Exception;
      void flatMap2(IN2 value, Collector<OUT> out) throws Exception;
  }
  ```
- **严重程度**: P3
- **现状**: CoFlatMapFunction 和 CoMapFunction 是双流连接操作的函数接口。经搜索确认，全仓库中除了自身定义外无任何 `import` 或 `implements`。CoMapFunction 已标记了 `@Internal`，但 CoFlatMapFunction 没有。
- **风险**: CoFlatMapFunction 作为无 `@Internal` 标记的公共接口，暗示这是稳定的外部 API，但实际没有任何使用或测试覆盖。
- **建议**: 为 CoFlatMapFunction 添加 `@Internal` 注解，与 CoMapFunction 保持一致。或者移除这两个接口直到双流连接功能实际需要。
- **误报排除**: 这不是"合理的接口预留"——双流连接需要完整的运行时支持（TwoInputStreamOperator、DataStream.connect()、ConnectedStreams），这些配套设施全部不存在。
- **复核状态**: 未复核

---

### [维度03-07] IterationRuntimeContext 接口无任何实现

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/functions/IterationRuntimeContext.java:16`
- **证据片段**:
  ```java
  @Internal
  public interface IterationRuntimeContext extends RuntimeContext {
  }
  ```
- **严重程度**: P3
- **现状**: `IterationRuntimeContext` 继承了 `RuntimeContext`，但自身是一个空接口（无额外方法）。经搜索确认，整个仓库中没有任何类 `implements IterationRuntimeContext`。唯一引用点是 `AbstractRichFunction.getIterationRuntimeContext()` 和 `RichIterativeCondition.getIterationRuntimeContext()`——后者直接抛异常。
- **风险**: 这是一个永远无法正常返回的代码路径。
- **建议**: 考虑移除 IterationRuntimeContext 接口或在 RichFunction 接口中将 `getIterationRuntimeContext()` 标记为 `default` 方法抛 UnsupportedOperationException。
- **误报排除**: 这不是"合理的迭代支持预留"——迭代计算需要完整的运行时支持（超步同步、迭代头尾算子等），这些在 nop-stream 中完全不存在。
- **复核状态**: 未复核

---

### [维度03-08] core.time.TimerService 已标记 @Deprecated 但仍为 public，且无引用

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/time/TimerService.java:29-31`
- **证据片段**:
  ```java
  @Deprecated
  @Internal
  public interface TimerService {
      long currentProcessingTime();
      long currentWatermark();
      void registerProcessingTimeTimer(long time);
      void registerEventTimeTimer(long time);
      void deleteProcessingTimeTimer(long time);
      void deleteEventTimeTimer(long time);
  }
  ```
- **严重程度**: P3
- **现状**: 此 TimerService 已标记 `@Deprecated` 和 `@Internal`，且 javadoc 说明未被使用。同时，`io.nop.stream.cep.time.TimerService`（CEP 模块中的同名接口）是另一个独立的接口，有实际使用。
- **风险**: 两个同名接口（`core.time.TimerService` 和 `cep.time.TimerService`）在不同包中存在，容易导致开发者误导入。
- **建议**: 移除 `io.nop.stream.core.time.TimerService`，因为它已无任何引用者。
- **误报排除**: 这不是"正在使用的废弃 API"——经搜索确认零引用，且已标记 `@Internal`。
- **复核状态**: 未复核

---

### [维度03-09] RPC 接口参数类型跨模块耦合——runtime 接口参数引用同模块类而非 api 模块类型

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/rpc/IStreamTaskRpcService.java:10-11`、`IStreamCoordinatorRpcService.java:10`
- **证据片段**:
  ```java
  // IStreamTaskRpcService.java
  import io.nop.stream.core.checkpoint.CheckpointBarrier;  // 来自 core
  import io.nop.stream.runtime.cluster.TaskAssignment;       // 来自同模块 runtime

  public interface IStreamTaskRpcService {
      void receiveAssignment(TaskAssignment assignment);
      void triggerCheckpoint(CheckpointBarrier barrier, String fencingToken);
  }
  ```
- **严重程度**: P2
- **现状**: 两个 RPC 接口的方法参数使用了 runtime 模块内部的类型（`TaskAssignment`、`CheckpointAckMessage`），而非 core 模块的公共类型。这意味着如果有外部系统需要实现这些 RPC 接口，它必须依赖整个 nop-stream-runtime 模块。
- **风险**: RPC 接口的设计意图是允许远程端点独立实现，但参数类型的模块归属使得远程端点必须传递性依赖 runtime 模块的所有依赖。这违反了接口-实现分离原则。
- **建议**: 将 `TaskAssignment`、`CheckpointAckMessage` 等消息类型移到 nop-stream-core（或未来创建的 nop-stream-api）中，使 RPC 接口只依赖轻量模块。或者，如果 RPC 接口本身就是 runtime 内部的实现细节，应标记为 `@Internal` 或降低可见性。
- **误报排除**: 这不是"内部接口不需要关注模块边界"——RPC 接口天然是跨进程/跨模块边界使用的。
- **复核状态**: 未复核

---

### [维度03-10] SinkFunction 接口方法命名 consume 与 Flink 的 invoke/run 不一致

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/functions/SinkFunction.java:26`
- **证据片段**:
  ```java
  @FunctionalInterface
  public interface SinkFunction<T> extends StreamFunction {
      void consume(T value) throws Exception;

      default SinkConsistencyCapability getSinkConsistency() {
          return SinkConsistencyCapability.AT_LEAST_ONCE;
      }
  }
  ```
- **严重程度**: P3
- **现状**: `SinkFunction` 的核心方法命名为 `consume()`，而 `TwoPhaseCommitSinkFunction` 同时暴露了 `invoke()` 方法（且 `consume()` 默认委托给 `invoke()`）。这意味着 `TwoPhaseCommitSinkFunction` 的实现者需要知道应该实现 `invoke()` 而非 `consume()`——这个隐式约定未在接口文档中明确说明。
- **风险**: TwoPhaseCommitSinkFunction 的实现者可能误覆盖 consume() 而遗漏 invoke() 中的事务逻辑。
- **建议**: 在 TwoPhaseCommitSinkFunction 中将 `consume()` 标记为 final（或在抽象类中实现），消除方法分叉。
- **误报排除**: 这不是"命名风格偏好"——同一个接口有两个语义等价但调用路径不同的公共方法，且没有文档约束哪个应该被覆盖。
- **复核状态**: 未复核
深挖第 2 轮追加完成

---

## 维度复核结论

| 编号 | 复核结论 | 理由 |
|------|---------|------|
| 03-01 | **保留 P2** | api模块空壳属实。将接口从core提取到api的建议方向正确。 |
| 03-02 | **保留 P2** | 5个公共接口/类全仓库零implements，死API确认。 |
| 03-03 | **降级至 P3** | Map<String,Object>在此场景是常见设计选择（动态状态），且有类型安全的getXxxState方法。P3更合适。 |
| 03-04 | **降级至 P3** | SourceWorkUnit本身是死代码（03-02已确认无消费者），类型安全缺陷无实际影响。与03-02合并处理。 |
| 03-05 | **驳回（与02-03完全重复）** | 同一事实（4个空壳模块），已由02-03记录为P3。合并至02-03。 |
| 03-06 | **保留 P3** | 双流连接接口零使用确认。CoFlatMapFunction缺@Internal标记。 |
| 03-07 | **保留 P3** | IterationRuntimeContext空接口零实现确认。"永远无法正常返回"属实。 |
| 03-08 | **保留 P3** | core.time.TimerService零引用、@Deprecated确认。注意：19-01为此发现的命名角度重复，合并至此。 |
| 03-09 | **保留 P2** | RPC接口参数使用runtime模块内部类型确认。将消息类型移到core有改善模块边界的价值。 |
| 03-10 | **降级至 P3** | TwoPhaseCommitSinkFunction已@Internal，consume()→invoke()委托关系代码中清晰可见（仅3行）。P3更合适。 |
