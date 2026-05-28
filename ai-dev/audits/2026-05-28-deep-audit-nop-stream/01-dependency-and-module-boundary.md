# 维度 01: 依赖图与模块边界

## 适用性
适用

## 检查范围
- nop-stream/pom.xml（父 POM）
- nop-stream/nop-stream-api/pom.xml
- nop-stream/nop-stream-core/pom.xml
- nop-stream/nop-stream-cep/pom.xml
- nop-stream/nop-stream-connector/pom.xml
- nop-stream/nop-stream-runtime/pom.xml
- nop-stream/nop-stream-checkpoint/pom.xml
- nop-stream/nop-stream-flink/pom.xml
- nop-stream/nop-stream-flow/pom.xml
- nop-stream/nop-stream-fraud-example/pom.xml

## 发现

### [维度01-01] 三个空壳占位模块引入维护负担
- **文件**: `nop-stream/nop-stream-api/pom.xml`, `nop-stream/nop-stream-checkpoint/pom.xml`, `nop-stream/nop-stream-flink/pom.xml`, `nop-stream/nop-stream-flow/pom.xml`
- **证据片段**:
  ```xml
  <!-- nop-stream-api -->
  <artifactId>nop-stream-api</artifactId>
  <!-- placeholder, planned but not implemented -->
  <!-- interfaces are in nop-stream-core; this module is reserved for future API extraction -->
  
  <!-- nop-stream-checkpoint -->
  <artifactId>nop-stream-checkpoint</artifactId>
  <!-- placeholder, planned but not implemented -->
  
  <!-- nop-stream-flink -->
  <artifactId>nop-stream-flink</artifactId>
  <!-- placeholder, planned but not implemented -->
  
  <!-- nop-stream-flow -->
  <artifactId>nop-stream-flow</artifactId>
  <!-- placeholder, planned but not implemented -->
  ```
- **严重程度**: P2
- **现状**: 4个（api/checkpoint/flink/flow）子模块为空壳占位符，无任何 Java 源文件或资源。它们被 Maven Reactor 构建，增加编译时间和开发者认知负担。nop-stream-api 的注释明确说明"接口在 nop-stream-core 中"。
- **风险**: 新开发者可能误以为 API 层已独立抽取而直接依赖 api 模块；空壳模块在 CI 中消耗构建时间。
- **建议**: 如果短期（3个月内）无实现计划，从父 POM 的 `<modules>` 中注释掉这些模块。保留 pom.xml 但注释掉引用，待实现时再启用。
- **误报排除**: 不是"不优雅"问题。4个空壳模块引入了真实的维护成本：Maven Reactor 必须处理它们，且"接口在 core 中"的注释说明设计意图未落地。
- **复核状态**: 未复核

### [维度01-02] nop-stream-core 的 API 和实现未分离，公开接口散落在 core 包中
- **文件**: `nop-stream/nop-stream-core/pom.xml` 和 `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/`
- **证据片段**:
  ```xml
  <!-- nop-stream-core/pom.xml -->
  <dependencies>
      <dependency>
          <groupId>io.github.entropy-cloud</groupId>
          <artifactId>nop-commons</artifactId>
      </dependency>
      <dependency>
          <groupId>io.github.entropy-cloud</groupId>
          <artifactId>nop-core</artifactId>
      </dependency>
  </dependencies>
  ```
  
  ```
  # 在 core 中找到 80+ 个 public interface，包括：
  io.nop.stream.core.common.functions.SourceFunction
  io.nop.stream.core.common.functions.SinkFunction
  io.nop.stream.core.common.state.State
  io.nop.stream.core.datastream.DataStream
  io.nop.stream.core.operators.StreamOperator
  io.nop.stream.core.checkpoint.storage.ICheckpointStorage
  ```
- **严重程度**: P2
- **现状**: nop-stream-core 包含了 80+ 个公开接口（SourceFunction、SinkFunction、DataStream、StreamOperator、ICheckpointStorage 等）和对应的实现类。这意味着下游模块（cep、connector、runtime）只要使用接口就被迫依赖 core 的全部实现（包括 state backend、operators、windowing 等）。
- **风险**: runtime/cep/connector 的编译依赖链过宽，core 中的任何内部变更都会触发下游全量重编译。未来若想替换核心实现，所有下游模块的传递依赖都会受影响。
- **建议**: 这与 01-01 相关。如果未来要将 API 抽取到 nop-stream-api，当前架构已为此留了空壳模块。评估是否值得现在执行此分离。
- **误报排除**: 在引擎模块中，API/实现分离是常见的架构模式（如 Flink 的 flink-core vs flink-runtime）。nop-stream-api 已预留但未实现，说明这是一个已知但未完成的架构决策。
- **复核状态**: 未复核

### [维度01-03] nop-stream-connector 依赖 nop-batch-core，扩大了模块边界
- **文件**: `nop-stream/nop-stream-connector/pom.xml`
- **证据片段**:
  ```xml
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-stream-core</artifactId>
  </dependency>
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-batch-core</artifactId>
  </dependency>
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-message-core</artifactId>
      <optional>true</optional>
  </dependency>
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-message-debezium</artifactId>
      <optional>true</optional>
  </dependency>
  ```
- **严重程度**: P3
- **现状**: connector 模块依赖了 nop-batch-core（批量处理核心）以及可选的 nop-message-core 和 nop-message-debezium。这使得 stream 模块间接依赖了 batch 处理的完整依赖树。
- **风险**: 依赖传递可能导致 classpath 膨胀。若 batch-core 有 breaking change，connector 模块会受影响。
- **建议**: 确认 batch-core 依赖是否仅用于 BatchLoaderSourceFunction 中的批量加载功能。如果是，考虑将此依赖标记为 `<optional>true</optional>` 以避免传递依赖扩散。
- **误报排除**: nop-batch-core 的依赖是非可选的，意味着即使只使用 connector 的消息功能（如 DebeziumCdcSourceFunction），也会拉入 batch-core 的全部依赖。
- **复核状态**: 未复核

## 维度总结
依赖方向整体合理：api（空壳）← core ← cep/connector/runtime ← fraud-example，无循环依赖。主要问题是（1）4个空壳模块未清理，（2）core 模块承担了 API 层角色，使下游依赖链过宽，（3）connector 对 batch-core 的非可选依赖。无 P0/P1 级别问题。
