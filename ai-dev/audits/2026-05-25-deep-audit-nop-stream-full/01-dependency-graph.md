# 维度 01：依赖图与模块边界

## 第 1 轮（初审）

### 完整依赖图

```
nop-stream (parent pom, packaging=pom)
│
├── nop-stream-api
│   └── (无外部依赖, placeholder)
│
├── nop-stream-core
│   ├── nop-commons ──► nop-api-core
│   └── nop-core ──► nop-dataset, nop-javac
│
├── nop-stream-cep
│   ├── nop-stream-core (传递: nop-commons, nop-core)
│   └── nop-xlang (用于 precompile/gen-cep-xdsl.xgen 代码生成)
│       └── nop-xdefs, nop-antlr4-common
│
├── nop-stream-connector
│   ├── nop-stream-core (传递: nop-commons, nop-core)
│   ├── nop-batch-core ──► nop-xlang (传递)
│   ├── nop-message-core (optional=true)
│   └── nop-message-debezium (optional=true)
│
├── nop-stream-runtime
│   ├── nop-stream-core
│   ├── nop-stream-cep (传递: nop-xlang)
│   ├── nop-message-core (compile scope)
│   ├── nop-dao (scope=provided)
│   ├── HikariCP (test)
│   └── h2 (test)
│
├── nop-stream-flow (placeholder)
├── nop-stream-checkpoint (placeholder)
├── nop-stream-flink (placeholder)
│
└── nop-stream-fraud-example
    └── nop-stream-cep (显式 ${project.version}, 传递: core, xlang)
```

---

### [维度01-01] nop-stream-runtime 声明了 nop-stream-cep 依赖但主代码和测试代码均未使用

- **文件**: `nop-stream/nop-stream-runtime/pom.xml:26-29`
- **证据片段**:
  ```xml
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-stream-cep</artifactId>
  </dependency>
  ```
- **严重程度**: P3
- **现状**: nop-stream-runtime 在 pom.xml 中声明了对 nop-stream-cep 的 compile scope 依赖，但经全量扫描，runtime 模块的 main 代码和 test 代码均无任何 `import io.nop.stream.cep.*` 引用。该依赖仅导致 nop-xlang 作为不必要的传递依赖被引入 runtime 的编译类路径。
- **风险**: 不修复的后果是：(1) runtime 的传递依赖表面比实际需要的大，下游消费者会意外获得 cep 和 xlang 的传递依赖；(2) 模块间的依赖关系失真，给后续开发者造成误导，以为 runtime 依赖了 cep 功能。
- **建议**: 将 `nop-stream-cep` 从 runtime 的 pom.xml 中移除。如果未来确实需要 CEP 功能，应在实际使用时再加回。
- **误报排除**: 这不是"看起来不优雅"的问题。这是一个实际存在的不必要依赖声明，通过全量 import 扫描确认零引用。当前构建也能在不依赖 cep 的情况下编译通过。
- **复核状态**: 未复核

---

### [维度01-02] nop-stream-runtime 声明了 nop-message-core 为 compile scope 但主代码仅使用 nop-api-core 的 IMessageService 接口

- **文件**: `nop-stream/nop-stream-runtime/pom.xml:31-34`
- **证据片段**:
  ```xml
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-message-core</artifactId>
  </dependency>
  ```
  runtime 主代码中实际使用的 import：
  ```java
  // RemoteGraphExecutionPlanBuilder.java:10
  import io.nop.api.core.message.IMessageService;

  // RemoteInputChannel.java:10-13
  import io.nop.api.core.message.IMessageConsumeContext;
  import io.nop.api.core.message.IMessageConsumer;
  import io.nop.api.core.message.IMessageService;
  import io.nop.api.core.message.IMessageSubscription;
  ```
  只有测试代码使用了 `nop-message-core` 的具体实现：
  ```java
  // TestRemoteDataExchange.java:4
  import io.nop.message.core.local.LocalMessageService;
  ```
- **严重程度**: P2
- **现状**: runtime 的主代码仅依赖 `io.nop.api.core.message.IMessageService` 等接口（来自 nop-api-core），但 pom.xml 将 nop-message-core 声明为 compile scope。实际只有测试代码 `TestRemoteDataExchange` 使用了 `LocalMessageService`（nop-message-core 的具体实现）。
- **风险**: 不修复的后果是：(1) runtime 作为 compile 依赖会将 nop-message-core 传递给所有下游消费者，导致不必要的类路径膨胀；(2) 如果 nop-message-core 内部依赖发生变化，可能导致 runtime 模块出现意外的编译或运行时问题；(3) 违反接口与实现分离原则——runtime 主代码只需要消息接口，不应绑定具体实现。
- **建议**: 将 `nop-message-core` 的 scope 改为 `test`，因为它仅在测试中使用。如果 runtime 模块在运行时需要依赖 nop-message-core（例如通过 IoC 注入），应在应用层（app 模块）声明依赖，而非框架层。
- **误报排除**: 这不是"Maven 最佳实践要求显式声明依赖"的情况。runtime 主代码实际 import 的是 `io.nop.api.core.message` 包（属于 nop-api-core），而非 `io.nop.message.core`。将接口依赖的运行时实现错误地声明为 compile scope 是真实的依赖边界问题。
- **复核状态**: 未复核

---

### [维度01-03] nop-stream-fraud-example 硬编码 Java 17 编译版本，与项目基线不一致

- **文件**: `nop-stream/nop-stream-fraud-example/pom.xml:14-18`
- **证据片段**:
  ```xml
  <properties>
      <maven.compiler.source>17</maven.compiler.source>
      <maven.compiler.target>17</maven.compiler.target>
      <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  </properties>
  ```
  根 pom.xml 的默认设置：
  ```xml
  <maven.compiler.source>11</maven.compiler.source>
  <maven.compiler.target>11</maven.compiler.target>
  <maven.compiler.release>11</maven.compiler.release>
  ```
  nop-stream-runtime 的设置：
  ```xml
  <maven.compiler.source>21</maven.compiler.source>
  <maven.compiler.target>21</maven.compiler.target>
  ```
- **严重程度**: P3
- **现状**: nop-stream-fraud-example 覆盖了根 pom 的 Java 版本为 17，而同级的 nop-stream-runtime 覆盖为 21。项目根 pom 基线为 11。一个模块内的子模块使用了不一致的 Java 版本目标（17 vs 21 vs 11），且没有注释说明原因。
- **风险**: 虽然当前 fraud-example 的源码不需要 Java 21 特性，但如果其他模块使用了 Java 21 特性（如 record patterns、sealed classes），而 fraud-example 作为下游依赖版本不匹配可能导致 class 文件兼容性问题。更实际地说，版本不一致增加了 CI 环境配置的复杂度，且违反了仓库内的编译一致性。
- **建议**: 统一 fraud-example 的 Java 编译版本。如果项目统一使用 Java 21，则改为 21；如果仅需保持根 pom 的 11，则删除此覆盖。
- **误报排除**: 这不是"看起来不优雅"的问题。一个仓库内同一模块的子模块使用不同的 Java 编译版本，这是一个可量化的配置不一致，可能导致 class 文件版本冲突。
- **复核状态**: 未复核

---

## 合规的模块清单

| 模块 | 合规评估 |
|------|---------|
| **nop-stream-api** | 合规。无外部依赖，符合 API 层不应依赖实现层的规则。Placeholder 状态。 |
| **nop-stream-core** | 合规。仅依赖 nop-commons 和 nop-core，不依赖 dao 或 runtime。依赖层次正确。 |
| **nop-stream-cep** | 合规。依赖 core + nop-xlang（用于 precompile xgen 代码生成）。仅依赖 core，符合规则。 |
| **nop-stream-connector** | 合规。依赖 core + nop-batch-core + nop-message-core(optional) + nop-message-debezium(optional)。不直接依赖 runtime。nop-batch-core 用于 BatchConsumerSinkFunction 和 BatchLoaderSourceFunction 适配器，是合理的集成层依赖。 |
| **nop-stream-flow** | 合规。Placeholder，无额外依赖。 |
| **nop-stream-checkpoint** | 合规。Placeholder，无额外依赖。 |
| **nop-stream-flink** | 合规。Placeholder，无额外依赖。 |
| **nop-stream-fraud-example** | 基本合规。仅依赖 cep，符合规则。有 Java 版本不一致的 P3 问题。 |

---

## 特别审查结论

**1. nop-stream-runtime 依赖 nop-dao（provided scope）是否合理**

结论：合理。runtime 模块的 `JdbcClusterRegistry` 和 `JdbcCheckpointStorage` 直接使用 `IJdbcTemplate`、`SQL` 等 nop-dao API 实现 JDBC 存储后端。`provided` scope 是正确的选择。

**2. nop-stream-connector 依赖 nop-batch-core 是否合理**

结论：合理。connector 的核心功能是将 nop-batch 的批处理消费者/加载器适配为 nop-stream 的 SourceFunction/SinkFunction。这是设计上的集成适配器层。

**3. 循环依赖检查**

结论：未发现循环依赖。所有依赖关系构成有向无环图（DAG）。

**4. 隐性耦合检查**

结论：未发现严重隐性耦合。所有子模块的 import 均与 pom.xml 声明的依赖一致。
深挖第 2 轮追加完成

---

## 维度复核结论

| 编号 | 复核结论 | 理由 |
|------|---------|------|
| 01-01 | **保留 P3** | 证据100%验证。runtime的main和test目录零`import io.nop.stream.cep.*`。P3合理——依赖漂移但无运行时故障。注意：02-01为同一发现的重复，合并至此。 |
| 01-02 | **保留 P2** | 证据100%验证。runtime主代码仅用`io.nop.api.core.message.*`接口，只有TestRemoteDataExchange用`LocalMessageService`。compile scope应改为test。 |
| 01-03 | **保留 P3** | 证据验证通过。三版本并存属实（11/17/21）。注意：02-04为同一发现的重复，合并至此。 |
