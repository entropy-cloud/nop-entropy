# 维度 01：依赖图与模块边界

## 第 1 轮（初审）

### [维度01-01] nop-stream-cep 声明了 nop-xlang 编译依赖，但所有源码均无任何 nop-xlang 包导入

- **文件**: `nop-stream/nop-stream-cep/pom.xml:20-23`
- **证据片段**:
  ```xml
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-xlang</artifactId>
  </dependency>
  ```
- **严重程度**: P3
- **现状**: nop-xlang 作为编译依赖引入，但在 nop-stream-cep 的所有编译产物（main + test）中未使用任何来自该模块的类。`IEvalFunction` 来自 `io.nop.core.lang.eval`（nop-core），不属于 nop-xlang。
- **风险**: 增加不必要的编译类路径和传递依赖，误导开发者认为本模块使用了 XLang。
- **建议**: 若确认运行时无需 XLang，移除该依赖。若未来计划使用，添加注释说明。
- **误报排除**: 已排除 nop-xlang 通过 IEvalFunction 间接使用的可能——IEvalFunction 在 nop-core 包中。
- **复核状态**: 未复核

### [维度01-02] nop-stream-connector 将 nop-message-core 声明为 optional 但仅测试使用

- **文件**: `nop-stream/nop-stream-connector/pom.xml:25-29`
- **证据片段**:
  ```xml
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-message-core</artifactId>
      <optional>true</optional>
  </dependency>
  ```
- **严重程度**: P3
- **现状**: 主源码 `MessageSourceFunction` 和 `MessageSinkFunction` 使用的 `IMessageService` 来自 `io.nop.api.core.message`（nop-api-core），不是 `nop-message-core`。仅测试文件 `TestMessageAdapters.java` 使用了 `LocalMessageService`。对比 runtime 模块将同类依赖声明为 `<scope>test</scope>`。
- **风险**: optional 暗示"主源码需要，消费者可能需要自行引入"，但实际上主源码不依赖此模块。
- **建议**: 改为 `<scope>test</scope>`，与 runtime 模块保持一致。
- **误报排除**: 已确认 DebeziumCdcSourceFunction 使用的是 nop-message-debezium，与 nop-message-core 无关。
- **复核状态**: 未复核

## 已验证合规项

- nop-stream-core: 仅依赖 nop-commons + nop-core，边界干净
- nop-stream-runtime: nop-dao 为 provided scope，语义正确
- nop-stream-connector: nop-message-debezium 为 optional，语义合理
- nop-stream-fraud-example: 仅依赖 nop-stream-cep，传递依赖使用合理
- 无循环依赖，无缺失依赖声明
- 4 个 placeholder 模块状态符合预期
