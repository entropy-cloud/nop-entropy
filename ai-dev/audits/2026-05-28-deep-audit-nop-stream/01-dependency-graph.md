# 维度 01：依赖图与模块边界

## 第 1 轮（初审）

### [维度01-01] nop-stream-connector 对 nop-message-core 的 optional 标注与 main 源码实际使用不一致

- **文件**: `nop-stream/nop-stream-connector/pom.xml:25-29`
- **证据片段**:
```xml
<dependency>
    <groupId>io.github.entropy-cloud</groupId>
    <artifactId>nop-message-core</artifactId>
    <optional>true</optional>
</dependency>
```
- **严重程度**: P2
- **现状**: `nop-message-core` 在 pom.xml 中标记为 `optional=true`，但 connector 的 `src/main/java` 中 `MessageSinkFunction.java` 和 `MessageSourceFunction.java` 均通过 `IMessageService` 等接口（来自 nop-api-core，被 nop-message-core 传递暴露）实现对消息服务的适配。`DebeziumCdcSourceFunction.java` 则直接 import `io.nop.message.debezium.*`（来自 `nop-message-debezium`，同样 optional）。IMessageService 来自 nop-api-core，编译不需要 nop-message-core。真正直接依赖 nop-message-debezium 的 DebeziumCdcSourceFunction 标注 optional 是正确的 CDC 连接器可选模式。文档上未标注"使用时需额外引入"的要求。
- **风险**: 用户使用 Message/Debezium 适配器时不知道需额外引入对应依赖。
- **建议**: 在 `StreamConnectors` 工厂类或模块 README 中注明使用 Message/Debezium 适配器时需额外引入对应依赖。
- **误报排除**: 这不是"optional 漏标导致编译失败"——IMessageService 来自 nop-api-core。optional 标注本身正确。
- **复核状态**: 未复核

### [维度01-02] nop-stream-fraud-example 使用 ${project.version} 硬编码版本而非依赖管理

- **文件**: `nop-stream/nop-stream-fraud-example/pom.xml:18-24`
- **证据片段**:
```xml
<dependency>
    <groupId>io.github.entropy-cloud</groupId>
    <artifactId>nop-stream-cep</artifactId>
    <version>${project.version}</version>
</dependency>
```
- **严重程度**: P3
- **现状**: 唯一一个在 reactor 内部依赖中显式写版本的子模块，与同一 reactor 内其他子模块写法不一致。
- **风险**: 维护成本极低但与其他模块写法不一致。
- **建议**: 移除 `<version>${project.version}</version>` 行，与 reactor 内其他模块保持一致。
- **误报排除**: 不是构建错误——reactor 构建时该版本始终正确解析。只是风格不一致。
- **复核状态**: 未复核

### [维度01-03] nop-bom 未管理 nop-stream-connector 和 nop-stream-runtime

- **文件**: `nop-bom/pom.xml:1106-1134`
- **证据片段**:
```xml
<!-- nop-bom 中仅管理以下 5 个模块 -->
<artifactId>nop-stream-api</artifactId>
<artifactId>nop-stream-core</artifactId>
<artifactId>nop-stream-flow</artifactId>
<artifactId>nop-stream-cep</artifactId>
<artifactId>nop-stream-checkpoint</artifactId>
<!-- 缺失: nop-stream-connector, nop-stream-runtime, nop-stream-fraud-example, nop-stream-flink -->
```
- **严重程度**: P2
- **现状**: `nop-bom` 只管理了 9 个 stream 子模块中的 5 个。缺失的 `nop-stream-connector` 和 `nop-stream-runtime` 是有实际源码的活跃模块。
- **风险**: 外部消费者引用 `nop-stream-connector` 或 `nop-stream-runtime` 时必须自行指定版本号，失去 BOM 统一版本管理的价值。
- **建议**: 如果 `nop-stream-connector` 和 `nop-stream-runtime` 对外可用，应加入 `nop-bom`。如果确定是内部模块，应在 BOM 注释中说明。
- **误报排除**: connector 和 runtime 是有实际源码的活跃模块，它们的缺失有实际的版本管理影响。placeholder 模块不需要进 BOM 是正常的。
- **复核状态**: 未复核

### [维度01-04] nop-stream-runtime 对 nop-dao 的 provided 依赖缺少使用说明

- **文件**: `nop-stream/nop-stream-runtime/pom.xml:33-37`
- **证据片段**:
```xml
<dependency>
    <groupId>io.github.entropy-cloud</groupId>
    <artifactId>nop-dao</artifactId>
    <scope>provided</scope>
</dependency>
```
- **严重程度**: P3
- **现状**: `JdbcCheckpointStorage.java` 和 `JdbcClusterRegistry.java` 直接 import `io.nop.dao.jdbc.IJdbcTemplate`（来自 nop-dao），provided scope 正确地将运行时依赖责任转移给部署环境。但缺少文档说明。
- **风险**: 使用 `nop-stream-runtime` 的下游模块如果在 classpath 上没有 `nop-dao`，JDBC 存储后端会在运行时 ClassNotFoundException。
- **建议**: 在模块 README 或 Javadoc 中注明使用 JDBC 后端时需引入 nop-dao。
- **误报排除**: provided scope 本身合理——runtime 模块不强制绑定 nop-dao，允许使用不依赖数据库的存储后端。
- **复核状态**: 未复核

## 依赖图

```
nop-stream (parent pom)
├── nop-stream-api (placeholder)
├── nop-stream-core → nop-commons, nop-core
├── nop-stream-checkpoint (placeholder)
├── nop-stream-flink (placeholder)
├── nop-stream-flow (placeholder)
├── nop-stream-cep → nop-stream-core, nop-xlang
├── nop-stream-connector → nop-stream-core, nop-batch-core, optional: nop-message-core, nop-message-debezium
├── nop-stream-runtime → nop-stream-core, provided: nop-dao, test: nop-message-core
└── nop-stream-fraud-example → nop-stream-cep

无循环依赖。依赖方向严格单向。
```
