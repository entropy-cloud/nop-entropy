# 维度 01：依赖图与模块边界

## 第 1 轮（初审）

### [维度01-01] nop-stream-runtime 主代码依赖 nop-dataset 但未显式声明

- **文件**: `nop-stream/nop-stream-runtime/pom.xml:29-33`
- **证据片段**:
  ```xml
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-dao</artifactId>
      <scope>provided</scope>
  </dependency>
  ```
- **严重程度**: P3
- **现状**: `JdbcCheckpointStorage.java` 和 `JdbcClusterRegistry.java` 在主编译代码中 import 了 `io.nop.dao.jdbc.IJdbcTemplate`（来自 nop-dao）和 `io.nop.dataset.IDataRow`/`IDataSet`（来自 nop-dataset）。`nop-dao` 声明为 `provided` scope（合理），但 `nop-dataset` 没有显式声明。`nop-dataset` 通过 `nop-core → nop-stream-core` 传递到了 compile scope。
- **风险**: `nop-dataset` 的可用性完全依赖于 `nop-core` 的传递依赖链。如果未来 `nop-core` 重构移除了对 `nop-dataset` 的直接依赖，`nop-stream-runtime` 的编译将意外失败。短期内变动的可能性极低。
- **建议**: 不需要立即修改。如果追求显式依赖声明的完整性，可在 pom.xml 中添加 `nop-dataset` 为 `provided` scope 的显式声明。
- **信心水平**: 高（95%）
- **误报排除**: 不是设计问题，是显式声明完整性的建议。已通过 `mvn dependency:tree` 验证。
- **复核状态**: 未复核

### [维度01-02] 四个 placeholder 模块产出空 JAR，其中三个被列入 BOM

- **文件**: `nop-stream/nop-stream-api/pom.xml:12-14`, `nop-stream/nop-stream-checkpoint/pom.xml:12-13`, `nop-stream/nop-stream-flow/pom.xml:12-13`, `nop-stream/nop-stream-flink/pom.xml:12-13`
- **证据片段**:
  ```xml
  <!-- nop-stream-checkpoint/pom.xml lines 12-13 -->
  <artifactId>nop-stream-checkpoint</artifactId>
  <!-- placeholder, planned but not implemented -->
  ```
- **严重程度**: P3
- **现状**: 四个 placeholder 模块均无 src/ 目录。其中 `nop-stream-api`、`nop-stream-flow`、`nop-stream-checkpoint` 被列入 `nop-bom`，而 `nop-stream-flink` 未列入。`tests/pom.xml` 也引用了它们。
- **风险**: 空 JAR 不会冲突，但无意义地增加依赖树体积。`nop-stream-flink` 的缺失与其他 placeholder 的处理方式不一致。
- **建议**: 如果长期搁置，可考虑从 BOM 和 tests/pom.xml 中移除空模块声明。
- **信心水平**: 高（98%）
- **误报排除**: 这是常见的预占位做法，非功能性错误。
- **复核状态**: 未复核

### [维度01-03] nop-stream-fraud-example 仅依赖 nop-stream-cep 但直接使用 nop-stream-core 类

- **文件**: `nop-stream/nop-stream-fraud-example/pom.xml:18-23`
- **证据片段**:
  ```xml
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-stream-cep</artifactId>
  </dependency>
  ```
- **严重程度**: P3
- **现状**: `fraud-example` 直接使用了 12 个 `nop-stream-core` 类（state API），但这些依赖完全通过 `nop-stream-cep` 传递。
- **风险**: 如果未来 CEP 模块重构使其不再依赖 core，fraud-example 将编译失败。风险极低。
- **建议**: 无需立即修改。示例模块，依赖传递链稳定。
- **信心水平**: 高（95%）
- **误报排除**: 不是错误，是显式声明完整性的建议。
- **复核状态**: 未复核

## 完整依赖图

```
nop-stream (parent pom, reactor aggregator)
├── nop-stream-api           [placeholder, no src, no deps]
├── nop-stream-core          → nop-commons, nop-core
├── nop-stream-checkpoint    [placeholder, no src, no deps]
├── nop-stream-flink         [placeholder, no src, no deps]
├── nop-stream-flow          [placeholder, no src, no deps]
├── nop-stream-cep           → nop-stream-core
├── nop-stream-connector     → nop-stream-core
│                              optional: nop-batch-core, nop-message-core, nop-message-debezium
├── nop-stream-runtime       → nop-stream-core
│                              provided: nop-dao
│                              test: nop-message-core, HikariCP, h2
└── nop-stream-fraud-example → nop-stream-cep
```

## 总结

- **循环依赖**: 无
- **跨层违规**: 无
- **整体评估**: 依赖结构健康，所有 P3 问题均为显式声明完整性的建议。
