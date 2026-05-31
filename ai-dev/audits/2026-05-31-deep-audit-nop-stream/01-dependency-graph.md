# 维度 01：依赖图与模块边界

## 第 1 轮（初审）

### [维度01-01] nop-stream-connector: nop-message-core test scope -- 正确

- **文件**: `nop-stream/nop-stream-connector/pom.xml:26-30`
- **证据片段**:
  ```xml
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-message-core</artifactId>
      <scope>test</scope>
  </dependency>
  ```
- **严重程度**: P3 (确认正确)
- **现状**: nop-message-core 仅在 TestMessageAdapters 中使用，scope 正确。
- **风险**: 无
- **建议**: 无需修改
- **信心水平**: 确定
- **误报排除**: 验证仅 1 个测试文件引用。
- **复核状态**: 未复核

### [维度01-02] nop-stream-runtime: nop-dao provided scope 正确，文档小缺口

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
- **现状**: nop-dao 为 provided scope，用于 JdbcCheckpointStorage 和 JdbcClusterRegistry（均标 @Internal）。JdbcClusterRegistry 缺少与 JdbcCheckpointStorage 相同的 "Runtime dependency" Javadoc 注释。
- **风险**: 低，仅文档缺口
- **建议**: 在 JdbcClusterRegistry 添加与 JdbcCheckpointStorage 一致的 Javadoc 说明。
- **信心水平**: 确定
- **误报排除**: 验证两个类都使用 nop-dao 类型，nop-dataset 通过 nop-core 传递。
- **复核状态**: 未复核

### [维度01-03] nop-stream-connector: nop-batch-core optional -- 正确模式

- **文件**: `nop-stream/nop-stream-connector/pom.xml:20-24`
- **证据片段**:
  ```xml
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-batch-core</artifactId>
      <optional>true</optional>
  </dependency>
  ```
- **严重程度**: P3 (确认正确)
- **现状**: optional 正确用于 feature-gated 依赖，3 个主源码文件 + 4 个测试文件使用。
- **风险**: 无
- **建议**: 无需修改
- **信心水平**: 确定
- **误报排除**: 验证 BatchConsumerSinkFunction/BatchLoaderSourceFunction/StreamConnectors 都导入 nop-batch-core。
- **复核状态**: 未复核

### [维度01-04] nop-stream-connector: nop-message-debezium optional -- 正确模式

- **文件**: `nop-stream/nop-stream-connector/pom.xml:32-36`
- **证据片段**:
  ```xml
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-message-debezium</artifactId>
      <optional>true</optional>
  </dependency>
  ```
- **严重程度**: P3 (确认正确)
- **现状**: optional 正确用于 CDC 功能门控，1 个主源码文件使用。
- **风险**: 无
- **建议**: 无需修改
- **信心水平**: 确定
- **误报排除**: package-info.java 已文档化运行时需求。
- **复核状态**: 未复核

## 完整依赖图

```
nop-stream-core          → nop-commons, nop-core (无内部依赖) [407 Java files]
nop-stream-cep           → nop-stream-core (+ test-jar)       [111 Java files]
nop-stream-connector     → nop-stream-core, nop-batch-core(opt), nop-message-debezium(opt) [15 Java files]
nop-stream-runtime       → nop-stream-core, nop-dao(prov), nop-message-core(test), nop-stream-core:test-jar [88 Java files]
nop-stream-fraud-example → nop-stream-cep                     [14 Java files]
nop-stream-api           → (placeholder, no Java files)
nop-stream-checkpoint    → (placeholder, no Java files)
nop-stream-flink         → (placeholder, no Java files)
nop-stream-flow          → (placeholder, no Java files)
```

## 违规清单

无 P0-P2 违规。所有发现为 P3（确认正确的模式）。

## 合规模块

| 模块 | 状态 | 说明 |
|------|------|------|
| nop-stream-core | COMPLIANT | 基础层干净：仅 nop-commons + nop-core |
| nop-stream-runtime | COMPLIANT | provided/test scope 正确使用 |
| nop-stream-cep | COMPLIANT | 单一干净依赖 |
| nop-stream-connector | COMPLIANT | optional 正确用于 feature-gated |
| nop-stream-fraud-example | COMPLIANT | 最小依赖 |

## 总结评估

**依赖管理优秀**。无循环依赖、无反向依赖、无缺失依赖、无不必要依赖。Scope 正确（provided for JDBC, optional for batch/debezium, test for test infra）。
