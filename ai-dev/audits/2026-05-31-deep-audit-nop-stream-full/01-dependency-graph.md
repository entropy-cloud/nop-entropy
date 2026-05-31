# 维度 01：依赖图与模块边界

## 第 1 轮（初审）

### [维度01-01] nop-stream-core 依赖 nop-core 而非 nop-stream-api，与设计文档不一致

- **文件**: `nop-stream/nop-stream-core/pom.xml:13-22` 及 `nop-stream/nop-stream-api/pom.xml:12-14`
- **证据片段**:
  ```xml
  <!-- nop-stream-core/pom.xml -->
  <artifactId>nop-stream-core</artifactId>
  <dependencies>
      <dependency>
          <groupId>io.github.entropy-cloud</groupId>
          <artifactId>nop-commons</artifactId>
      </dependency>
      <dependency>
          <groupId>io.github.entropy-cloud</groupId>
          <artifactId>nop-core</artifactId>
      </dependency>
  ```
  ```xml
  <!-- nop-stream-api/pom.xml -->
  <artifactId>nop-stream-api</artifactId>
  <!-- placeholder, planned but not implemented -->
  <!-- interfaces are in nop-stream-core; this module is reserved for future API extraction -->
  ```
- **严重程度**: P3
- **现状**: 架构文档 `ai-dev/design/nop-stream/architecture.md` §2.1 明确定义依赖方向为 `core → api`，即 nop-stream-core 应依赖 nop-stream-api。但 nop-stream-core 的 pom.xml 实际依赖 `nop-commons` 和 `nop-core`，不依赖 `nop-stream-api`。nop-stream-api 目前是空的 placeholder 模块，注释说明"interfaces are in nop-stream-core; this module is reserved for future API extraction"。
- **风险**: 当前不影响编译和功能。但设计文档与代码现实不一致，如果未来开发者依据设计文档推导依赖方向可能产生误判。
- **建议**: (A) 按计划执行 API 提取；(B) 更新 architecture.md §2.1 反映现状。
- **信心水平**: 确定
- **误报排除**: 不是误报——设计文档白纸黑字写着 `core → api`，代码明显不一致。但当前是受控的技术债，注释已解释理由。
- **复核状态**: 未复核

### [维度01-02] nop-stream-runtime 主代码直接依赖 nop-dao (provided scope)，架构文档未声明此依赖

- **文件**: `nop-stream/nop-stream-runtime/pom.xml:29-33` 及 `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/storage/JdbcCheckpointStorage.java:19-21` 及 `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/cluster/JdbcClusterRegistry.java:19-22`
- **证据片段**:
  ```xml
  <!-- pom.xml -->
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-dao</artifactId>
      <scope>provided</scope>
  </dependency>
  ```
  ```java
  // JdbcCheckpointStorage.java
  import io.nop.core.lang.sql.SQL;
  import io.nop.dao.jdbc.IJdbcTemplate;
  import io.nop.dataset.IDataRow;
  ```
  ```java
  // JdbcClusterRegistry.java
  import io.nop.core.lang.sql.SQL;
  import io.nop.dao.jdbc.IJdbcTemplate;
  import io.nop.dataset.IDataRow;
  import io.nop.dataset.IDataSet;
  ```
- **严重程度**: P2
- **现状**: nop-stream-runtime 主代码中有两个类直接使用 `IJdbcTemplate`。nop-dao 声明为 `provided` scope，编译时可用但运行时需要消费者提供。架构文档 §2.1 对 runtime 的描述为"→ core"，没有提到对 nop-dao 的依赖。
- **风险**: `provided` scope 是处理可选基础设施依赖的合理方式。但架构文档未反映这一依赖关系，且 JDBC 实现类与 nop-dao 深度耦合（SQL builder、事务管理、DDL 自动建表），实际上构成了一个可选子模块的体量。
- **建议**: (A) 在架构文档 §2.1 和 §7.1 中补充 runtime 对 nop-dao 的 provided 依赖说明；(B) 长期考虑将 JDBC 相关实现拆到独立子模块。
- **信心水平**: 确定
- **误报排除**: 不是误报——架构文档 §2.1 只写了 `runtime → core`，完全没有提到 nop-dao。provided scope 使用合理，但文档缺位是真实的。
- **复核状态**: 未复核

## 合规模块清单

| 模块 | 合规要点 |
|------|---------|
| nop-stream-core | 仅依赖 nop-commons + nop-core，无循环依赖 |
| nop-stream-cep | 仅依赖 nop-stream-core，符合 cep → core |
| nop-stream-connector | 依赖 core + 可选 batch-core/message-debezium，方向正确 |
| nop-stream-fraud-example | 仅依赖 cep，传递获得 core，方向合理 |
| nop-stream-api | placeholder，无依赖，符合规则 |
| nop-stream-checkpoint | placeholder，无依赖 |
| nop-stream-flink | placeholder，无依赖 |
| nop-stream-flow | placeholder，无依赖 |
| nop-stream-runtime | 依赖 core + nop-dao(provided) + nop-message-core(test)，无循环依赖 |

## 循环依赖检查

**结论：无循环依赖。** 所有已实现模块的依赖方向为单向。

## 完整依赖图

```
nop-stream-fraud-example → nop-stream-cep → nop-stream-core → nop-commons
        │                                         │            └──→ nop-core
        └── (uses core types transitively via cep) │
                                                   │
nop-stream-connector → nop-stream-core             nop-stream-runtime → nop-stream-core
        │                    │                           │
        ├── nop-batch-core (optional)                   ├── nop-dao (provided)
        └── nop-message-debezium (optional)             └── nop-message-core (test)

nop-stream-api     [placeholder]
nop-stream-checkpoint [placeholder]
nop-stream-flink   [placeholder]
nop-stream-flow    [placeholder]
```
