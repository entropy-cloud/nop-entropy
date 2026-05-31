# 维度 01：依赖图与模块边界

## 第 1 轮（初审）

### [维度01-01] nop-stream-runtime 依赖 nop-dao(provided) 但 JDBC 实现类放在 main source 中

- **文件**: `nop-stream/nop-stream-runtime/pom.xml:29-33` + `JdbcCheckpointStorage.java` + `JdbcClusterRegistry.java`
- **证据片段**:
  ```xml
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-dao</artifactId>
      <scope>provided</scope>
  </dependency>
  ```
- **严重程度**: P2
- **现状**: nop-stream-runtime 声明 nop-dao 为 provided scope，JdbcCheckpointStorage 和 JdbcClusterRegistry 深度使用 IJdbcTemplate、SQL、IDataRow 等接口。runtime 编译产物包含对 nop-dao API 的硬引用，但 nop-dao 在运行时可能不存在。
- **风险**: 如果消费者仅需 LocalFileCheckpointStorage 但误用 JdbcCheckpointStorage，会在运行时 ClassNotFoundException。两个 JDBC 实现类放在 main source 中与 provided 声明的意图存在结构张力。
- **建议**: 当前模式可接受。如果后续需要支持无 DAO 的轻量部署，建议将 JdbcCheckpointStorage 和 JdbcClusterRegistry 移至独立子模块。
- **信心水平**: 很可能
- **误报排除**: provided scope 的语义是"编译时可用、运行时由用户提供"。nop-stream-runtime 作为聚合模块，其消费者不一定都使用 JDBC 后端。
- **复核状态**: 未复核

### [维度01-02] nop-stream-api 为空占位符但被列入 reactor 构建

- **文件**: `nop-stream/nop-stream-api/pom.xml:12-14`
- **证据片段**:
  ```xml
  <!-- placeholder, planned but not implemented -->
  <!-- interfaces are in nop-stream-core; this module is reserved for future API extraction -->
  ```
- **严重程度**: P3
- **现状**: nop-stream-api 模块无任何 Java 代码、无依赖。同样 checkpoint/flink/flow 也是空占位符。
- **风险**: 4 个空占位模块每次构建都被 Maven 处理。Baseline 文档与实际代码不一致（baseline 声称 core 依赖 api，实际不依赖）。
- **建议**: 在 pom.xml 中为占位模块添加注释说明其状态。
- **信心水平**: 确定
- **误报排除**: 空模块作为架构预留是合理的 Nop 平台模式，非 bug。
- **复核状态**: 未复核

### [维度01-03] nop-stream-runtime 未聚合 cep/flow/checkpoint，与 baseline 描述不一致

- **文件**: `nop-stream/nop-stream-runtime/pom.xml:14-20`
- **证据片段**:
  ```xml
  <!-- CEP and connector integration is planned for future releases.
       Currently runtime depends only on nop-stream-core. -->
  ```
- **严重程度**: P3
- **现状**: 审计 baseline 声明 runtime 依赖 cep/flow/checkpoint，但当前仅依赖 core。经 grep 验证无任何 cep/flow/checkpoint import。
- **风险**: baseline 与 live code 不一致，可能误导后续开发规划。
- **建议**: 更新 baseline 以反映当前实际状态。
- **信心水平**: 确定
- **误报排除**: 不是对未完成功能的投诉，而是对 baseline 文档准确性的校准。
- **复核状态**: 未复核

## 完整依赖图

```
nop-stream (parent pom, packaging=pom)
├── nop-stream-api          → (空占位符)
├── nop-stream-core         → nop-commons, nop-core
├── nop-stream-checkpoint   → (空占位符)
├── nop-stream-flink        → (空占位符)
├── nop-stream-flow         → (空占位符)
├── nop-stream-cep          → nop-stream-core (含 test-jar)
├── nop-stream-connector    → nop-stream-core, nop-batch-core(optional), nop-message-debezium(optional)
├── nop-stream-runtime      → nop-stream-core, nop-dao(provided)
└── nop-stream-fraud-example→ nop-stream-cep
```

## 违规清单

| 规则 | 状态 |
|------|------|
| api 层不依赖实现 | 合规（空占位符） |
| core 层只依赖框架核心 | 合规 |
| 功能模块只依赖 core | 合规 |
| connector 可依赖外部 | 合规 |
| runtime 聚合各功能模块 | 部分实现 |
| example 只依赖需要的功能模块 | 合规 |
| 不允许循环依赖 | 合规 |
| 框架依赖只出现在 runtime/example | 合规 |

## 总结评估

nop-stream 模块依赖图整体健康，无 P0/P1 级别问题。无循环依赖，分层基本清晰。
