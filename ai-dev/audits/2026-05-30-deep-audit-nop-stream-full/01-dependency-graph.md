# 维度 01：依赖图与模块边界

## 第 1 轮（初审）

### 依赖图

```
nop-stream (parent pom, packaging=pom)
├── nop-stream-api          [EMPTY placeholder, no sources]
├── nop-stream-checkpoint   [EMPTY placeholder, no sources]
├── nop-stream-flink        [EMPTY placeholder, no sources]
├── nop-stream-flow         [EMPTY placeholder, no sources]
│
├── nop-stream-core         ──→ nop-commons, nop-core
│
├── nop-stream-cep          ──→ nop-stream-core
│
├── nop-stream-connector    ──→ nop-stream-core
│                           ──→ [optional] nop-batch-core
│                           ──→ [optional] nop-message-core
│                           ──→ [optional] nop-message-debezium
│
├── nop-stream-runtime      ──→ nop-stream-core
│                           ──→ [provided] nop-dao
│                           ──→ [test] nop-message-core
│                           ──→ [test] HikariCP, H2
│
└── nop-stream-fraud-example──→ nop-stream-cep (→ nop-stream-core)
```

**无循环依赖。** 依赖方向严格自底向上。

---

### [维度01-01] nop-stream-runtime 的 main 代码直接编译依赖 nop-dao，但仅以 provided 声明

- **文件**: `nop-stream/nop-stream-runtime/pom.xml:29-33`
- **证据片段**:
```xml
<dependency>
    <groupId>io.github.entropy-cloud</groupId>
    <artifactId>nop-dao</artifactId>
    <scope>provided</scope>
</dependency>
```
- **严重程度**: P2
- **现状**: `nop-stream-runtime` 的 main 源码中有两个类（`JdbcCheckpointStorage.java` 和 `JdbcClusterRegistry.java`）直接使用了 `io.nop.dao.jdbc.IJdbcTemplate`、`io.nop.dataset.IDataRow` 等 nop-dao API。这些类编译打包到 JAR 中，但 nop-dao 声明为 provided scope。
- **风险**: 如果下游使用 JDBC 存储但未在运行时 classpath 引入 nop-dao，会出现 ClassNotFoundException。
- **建议**: 当前 provided scope 是合理设计选择（JDBC 存储为可选功能）。建议在 `JdbcCheckpointStorage` 和 `JdbcClusterRegistry` 的类 Javadoc 中明确文档化运行时 classpath 要求。
- **信心水平**: 确定
- **误报排除**: 涉及 main scope 代码对 provided scope 依赖的硬引用，是真实的模块边界设计点。
- **复核状态**: 未复核

---

### [维度01-02] nop-stream-connector 的 nop-message-core optional 依赖在 main 代码中无直接使用

- **文件**: `nop-stream/nop-stream-connector/pom.xml:26-30`
- **证据片段**:
```xml
<dependency>
    <groupId>io.github.entropy-cloud</groupId>
    <artifactId>nop-message-core</artifactId>
    <optional>true</optional>
</dependency>
```
- **严重程度**: P3
- **现状**: `nop-message-core` 被声明为 optional 依赖，但 main 代码不引用它的任何类。`MessageSourceFunction.java` 和 `MessageSinkFunction.java` 只使用了 `io.nop.api.core.message.IMessageService`（来自 nop-api-core 传递依赖）。`nop-message-core` 的导入只出现在测试中。
- **风险**: 开发者可能误以为 main 代码可以使用 nop-message-core 的类，引入不必要的耦合。
- **建议**: 将 `nop-message-core` 的 scope 改为 `test`。
- **信心水平**: 很可能
- **误报排除**: `nop-batch-core` 和 `nop-message-debezium` 的 optional 声明是合理的（main 代码确实使用了它们），问题仅限于 nop-message-core。
- **复核状态**: 未复核

---

### [维度01-03] 四个空占位符子模块在主构建中增加维护噪声

- **文件**:
  - `nop-stream/nop-stream-api/pom.xml:12-15`
  - `nop-stream/nop-stream-checkpoint/pom.xml:12-14`
  - `nop-stream/nop-stream-flink/pom.xml:12-14`
  - `nop-stream/nop-stream-flow/pom.xml:12-14`
- **证据片段**:
```xml
<!-- nop-stream-api -->
<artifactId>nop-stream-api</artifactId>
<!-- placeholder, planned but not implemented -->
<!-- interfaces are in nop-stream-core; this module is reserved for future API extraction -->

<!-- nop-stream-checkpoint / nop-stream-flink / nop-stream-flow -->
<artifactId>nop-stream-checkpoint</artifactId>
<!-- placeholder, planned but not implemented -->
```
- **严重程度**: P3
- **现状**: 四个子模块完全为空——没有 src/ 目录、没有 Java 文件、没有资源文件。每个模块只包含一个 pom.xml。它们被包含在父 pom 的 `<modules>` 列表中，每次 mvn install 都会被构建。
- **风险**: reactor 构建时间开销、日志噪声、对新开发者造成困惑。
- **建议**: 从父 pom 的 `<modules>` 中移除（保留目录），或通过 Maven profile 控制。
- **信心水平**: 确定
- **误报排除**: 空模块在 reactor 构建中有实际的时间和认知成本。
- **复核状态**: 未复核

---

### [维度01-04] nop-stream-fraud-example 作为示例代码参与主构建

- **文件**: `nop-stream/pom.xml:25`；`nop-stream/nop-stream-fraud-example/pom.xml`
- **证据片段**:
```xml
<!-- 父 pom -->
<module>nop-stream-fraud-example</module>
```
- **严重程度**: P2
- **现状**: fraud-example 是一个示例/演示模块，被直接包含在 nop-stream 父 pom 的 `<modules>` 中，参与标准 mvn install 构建，发布到 Maven 仓库。整个项目中所有其他 demo/example 代码都位于独立的 nop-demo 模块下。
- **风险**: 发布污染（示例代码作为正式 artifact 发布）、与项目其他 demo 的组织方式不一致。
- **建议**: 将 fraud-example 移到 nop-demo 目录下，或通过 Maven profile 将其从默认构建中排除。
- **信心水平**: 确定
- **误报排除**: 可量化的模块边界问题：示例代码不应与框架代码同版本发布，且与项目惯例不一致。
- **复核状态**: 未复核

---

## 维度复核结论

（待复核）

## 子项复核结论

（待复核）

## 最终保留项

（待复核后填写）
