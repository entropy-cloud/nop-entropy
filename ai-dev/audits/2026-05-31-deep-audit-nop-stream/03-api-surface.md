# 维度 03：API 表面积与契约一致性

## 第 1 轮（初审）

### [维度03-01] nop-stream-api 为空壳模块，API 与实现未分离

- **文件**: `nop-stream/nop-stream-api/pom.xml:13-14`
- **证据片段**:
  ```xml
  <!-- placeholder, planned but not implemented -->
  <!-- interfaces are in nop-stream-core; this module is reserved for future API extraction -->
  ```
- **严重程度**: P2
- **现状**: nop-stream-api 目录下无任何 Java 源文件。所有公共接口（~80+ 个）全部定义在 nop-stream-core 中，与具体实现类混放在同一模块。
- **风险**: 外部消费者必须依赖整个 core 模块；违反 Nop 平台其他模块普遍采用的 api/core 分层惯例；未来重构将面临大量 import 变更。
- **建议**: 按计划将公共接口和 DTO 迁移至 nop-stream-api。至少包括 datastream/ 下的 5 个接口、functions/ 下所有函数接口、state/ 下状态接口、ICheckpointStorage 等。
- **信心水平**: 确定
- **误报排除**: pom.xml 注释已确认是计划中的工作，但当前状态导致接口与实现耦合。
- **复核状态**: 未复核

### [维度03-02] nop-stream-flow 同为空壳模块

- **文件**: `nop-stream/nop-stream-flow/pom.xml:13`
- **严重程度**: P3
- **现状**: 无 Java 源文件，pom.xml 标记为 placeholder。
- **风险**: 空模块参与 reactor 构建，认知负担。
- **建议**: 添加更详细说明或移除。
- **信心水平**: 确定
- **误报排除**: 非 bug。
- **复核状态**: 未复核

### [维度03-03] DataStreamImpl 暴露了接口中未声明的重载方法

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/datastream/DataStreamImpl.java:145-147, 188-190`
- **证据片段**:
  ```java
  public <R> SingleOutputStreamOperator<R> map(MapFunction<T, R> mapper, TypeInformation<R> typeInfo)
  public <R> SingleOutputStreamOperator<R> flatMap(FlatMapFunction<T, R> flatMapper, TypeInformation<R> typeInfo)
  ```
- **严重程度**: P2
- **现状**: DataStream<T> 接口只声明了单参数版本的 map 和 flatMap。DataStreamImpl<T> 额外提供了带 TypeInformation<R> 参数的重载。通过接口类型引用时无法使用类型安全重载。
- **风险**: 类型安全重载对用户不可见；其他实现（如 RemoteDataStream）会遗漏这些重载。
- **建议**: 将 TypeInformation 重载添加到 DataStream<T> 接口中。
- **信心水平**: 确定
- **误报排除**: 已确认 DataStream 接口中确实没有这两个方法签名。
- **复核状态**: 未复核

### [维度03-04] 多个 connector 包接口零实现（死 API）

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/connector/` 目录下 5 个文件
- **严重程度**: P2
- **现状**: RestrictionTracker, DynamicSplitRequest, DynamicSplitResponse, WatermarkEstimator, SourceWorkUnit 为 FLIP-27 预留接口，全模块无任何实现、import 或调用。仅 SourceWorkUnit 有 @Internal 注解。
- **风险**: 外部消费者可能实现这些接口并依赖它们，但未来版本如果重设计会导致不兼容。
- **建议**: 对所有预留接口添加 @Internal 注解和 Javadoc @apiNote 说明预留状态。
- **信心水平**: 确定
- **误报排除**: 已排除 DrainableSource（有 DebeziumCdcSourceFunction 实现）。
- **复核状态**: 未复核

### [维度03-05] CoMapFunction 和 CoFlatMapFunction 零使用

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/common/functions/co/CoMapFunction.java` + `CoFlatMapFunction.java`
- **严重程度**: P3
- **现状**: 这两个接口声明了双流连接操作，但模块不支持双流操作（无 ConnectedStreams 类）。零实现、零调用、零测试。
- **风险**: 仅增加 API 表面积。
- **建议**: 添加 @Deprecated 或移到 internal 包。
- **信心水平**: 确定
- **误报排除**: 已确认全模块无 import。
- **复核状态**: 未复核

### [维度03-06] Configuration 接口为空壳

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/configuration/Configuration.java:19-21`
- **证据片段**:
  ```java
  @Internal
  public interface Configuration { }
  ```
- **严重程度**: P3
- **现状**: 空接口，@Internal 注解已标识。Javadoc 说明 "Placeholder configuration interface. Not yet implemented."
- **风险**: 极低。@Internal 标注足够。
- **建议**: 保留。
- **信心水平**: 确定
- **误报排除**: 有 @Internal 标注。
- **复核状态**: 未复核

### [维度03-07] StateSnapshot 公共 API 使用 Map<String, Object>

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/StateSnapshot.java:15-27`
- **证据片段**:
  ```java
  private final Map<String, Object> stateData;
  public Map<String, Object> getStateData() { return stateData; }
  public Map<String, Object> getStates() { ... }
  ```
- **严重程度**: P2
- **现状**: StateSnapshot 核心数据结构使用 Map<String, Object> 暴露内部序列化格式（"states"、"keyType" 等魔术字符串 key）。
- **风险**: 调用者需要知道内部序列化格式；序列化格式变更会导致静默的兼容性破坏。
- **建议**: 添加类型安全的访问方法（getKeyType(), getStateNames() 等），逐步替代 Map<String, Object>。
- **信心水平**: 很可能
- **误报排除**: StateSnapshot 是公共 API，理论上可被外部状态后端实现使用。Map<String, Object> 的使用集中在内部序列化层。
- **复核状态**: 未复核

### [维度03-08] ICheckpointExecutorFactory 存在两个同名但不同签名的 executeWithCheckpoint 重载

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/execution/ICheckpointExecutorFactory.java:29-32, 77-83`
- **证据片段**:
  ```java
  // 旧签名（抽象方法）
  StreamExecutionResult executeWithCheckpoint(JobGraph jobGraph, String jobName, CheckpointConfig checkpointConfig);
  // 新签名（default 方法，抛 UnsupportedOperationException）
  default StreamExecutionResult executeWithCheckpoint(StreamModel streamModel, PartitionedPlan partitionedPlan, DeploymentPlan deploymentPlan) {
      throw new UnsupportedOperationException(...);
  }
  ```
- **严重程度**: P2
- **现状**: StreamExecutionEnvironment.execute() 调用的是新版 default 方法。第三方实现如果只覆盖旧版，新版调用时会抛异常。
- **风险**: 运行时 UnsupportedOperationException。
- **建议**: 将新版 default 方法改为抽象方法，或在 Javadoc 中用 @implSpec 要求实现者必须覆盖。
- **信心水平**: 确定
- **误报排除**: 已确认 execute() 调用的是新版签名。
- **复核状态**: 未复核

### [维度03-09] RPC 接口引用 runtime 包内部类型

- **文件**: `runtime/src/main/java/io/nop/stream/runtime/rpc/IStreamTaskRpcService.java:18` + `IStreamCoordinatorRpcService.java:17`
- **严重程度**: P3
- **现状**: 两个 RPC 接口参数类型（TaskAssignment, CheckpointAckMessage）定义在 runtime 模块中。有 @Internal 标注。
- **风险**: 有限。@Internal 标注已限制外部使用。仅在 api 提取时会遇到循环依赖。
- **建议**: api 提取时将消息 DTO 移入 api 或 spi 模块。
- **信心水平**: 很可能
- **误报排除**: 已确认 @Internal 标注存在。
- **复核状态**: 未复核

### [维度03-10] nop-stream-core 包含大量实现类，API/impl 混合

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/`（目录级别）
- **严重程度**: P2
- **现状**: core 同时包含 ~80 个公共接口 + 30+ 个具体实现类 + 内部序列化代码 + 入口类 StreamExecutionEnvironment（含完整执行逻辑）。
- **风险**: 实现类变更可能意外破坏 API 消费者；与 Nop 平台其他模块分层模式不一致。
- **建议**: 与 03-01 协同处理，分阶段将接口迁入 api、实现迁入 runtime。
- **信心水平**: 确定
- **误报排除**: pom.xml 注释表明 api 提取是计划中的。
- **复核状态**: 未复核

### [维度03-11] ClusterRegistry 接口无 core 层抽象

- **文件**: `runtime/src/main/java/io/nop/stream/runtime/cluster/ClusterRegistry.java:16-99`
- **严重程度**: P3
- **现状**: ClusterRegistry 是分布式模式的关键 SPI，但完全定义和实现都在 runtime 模块。
- **风险**: 如果需要外部实现，实现者必须依赖 runtime 模块。
- **建议**: 如果预计会被外部实现，提取接口到 core 或 api。
- **信心水平**: 很可能
- **误报排除**: 当前只有一个模块需要集群管理。
- **复核状态**: 未复核
