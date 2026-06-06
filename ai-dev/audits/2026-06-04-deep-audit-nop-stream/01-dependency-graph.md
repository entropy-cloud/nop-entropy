# 维度 01：依赖图与模块边界

## 完整依赖图

```
nop-stream (parent pom, packaging=pom)
├── nop-stream-api          → (无依赖, placeholder, 无src代码)
├── nop-stream-core         → nop-commons, nop-core
├── nop-stream-checkpoint   → (无依赖, placeholder, 无src代码)
├── nop-stream-flink        → (无依赖, placeholder, 无src代码)
├── nop-stream-flow         → (无依赖, placeholder, 无src代码)
├── nop-stream-cep          → nop-stream-core
├── nop-stream-connector    → nop-stream-core, (optional) nop-batch-core, (optional) nop-message-debezium, (test) nop-message-core
├── nop-stream-runtime      → nop-stream-core, (provided) nop-dao, (test) nop-stream-core[test-jar], (test) nop-message-core, (test) HikariCP, (test) h2
└── nop-stream-fraud-example → nop-stream-cep
```

## 第 1 轮（初审）

### [维度01-01] nop-stream-connector 的 optional 依赖在 main 代码中硬引用，缺少运行时防护

- **文件**: `nop-stream/nop-stream-connector/pom.xml:20-36`
- **证据片段**:
  ```xml
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-batch-core</artifactId>
      <optional>true</optional>
  </dependency>
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-message-debezium</artifactId>
      <optional>true</optional>
  </dependency>
  ```
  ```java
  // DebeziumCdcSourceFunction.java:14-16 — 直接 import，无 ClassNotFoundException 防护
  import io.nop.message.debezium.ChangeEvent;
  import io.nop.message.debezium.DebeziumConfig;
  import io.nop.message.debezium.DebeziumMessageSource;
  ```
- **严重程度**: P2
- **现状**: `nop-batch-core` 和 `nop-message-debezium` 声明为 `optional`，但 connector 的 4 个 main 类（BatchConsumerSinkFunction, BatchLoaderSourceFunction, DebeziumCdcSourceFunction, StreamConnectors）在类加载时即硬依赖这些类。没有 Class.forName 检测、没有反射加载、没有 try-catch ClassNotFoundException。
- **风险**: `optional` 在 Maven 中意味着依赖不会传递到下游。如果下游项目依赖 `nop-stream-connector` 但未显式引入 `nop-batch-core` 或 `nop-message-debezium`，类加载时即 `NoClassDefFoundError`。
- **建议**: (A) 去掉 `optional`，承认 connector 模块就是依赖 batch 和 debezium；(B) 把 batch 和 debezium 相关类拆成独立子模块。
- **信心水平**: 确定
- **误报排除**: 不是风格问题——Maven `optional` 语义与代码实际行为矛盾，会导致 NoClassDefFoundError。
- **复核状态**: 未复核

### [维度01-02] nop-stream-core 的 ClassNameValidator 硬编码 `io.nop.dao.` 白名单，但 core 不依赖 nop-dao

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/util/ClassNameValidator.java:19`
- **证据片段**:
  ```java
  private static final List<String> ALLOWED_PREFIXES = Collections.unmodifiableList(Arrays.asList(
          "io.nop.stream.",
          "io.nop.commons.",
          "io.nop.core.",
          "io.nop.dao.",    // ← core 不依赖 nop-dao
  ```
- **严重程度**: P3
- **现状**: core 不依赖 nop-dao，但白名单允许 `io.nop.dao.` 前缀的类名通过验证。
- **风险**: 低。只是字符串白名单，但允许了不在类路径上的包名空间。
- **建议**: 白名单应只包含 core 实际依赖的模块命名空间，或添加注释说明。
- **信心水平**: 很可能
- **误报排除**: 白名单与实际依赖边界不一致是信息性风险。
- **复核状态**: 未复核

## 已排除项

- 依赖方向：所有模块依赖方向正确，无反向依赖，无循环依赖。
- Placeholder 模块（api, checkpoint, flink, flow）：干净，无不应有的代码或依赖。
- `nop-stream-runtime` 对 `nop-dao` 的 `provided` 使用：合理，JDBC 存储是可选的。
- test-jar 共享：core 产生 test-jar，cep 和 runtime 消费，Maven 标准做法。
- `nop-stream-fraud-example` 依赖传递正确。

## 维度复核结论

待复核。

## 最终保留项

| 编号 | 严重程度 | 文件 | 一句话摘要 |
|------|---------|------|-----------|
| 01-01 | P2 | nop-stream-connector/pom.xml | optional 依赖在 main 代码中硬引用 |
| 01-02 | P3 | ClassNameValidator.java | 白名单包含不依赖的 dao 包 |
