# 维度 20：跨模块契约一致性

## 第 1 轮（初审）

### 检查范围

nop-stream 对外暴露的接口：
1. nop-stream-api — 空壳，无实际接口
2. nop-stream-core 中的公共接口（SourceFunction、SinkFunction、StreamOperator、IStateBackend 等）
3. nop-stream-cep 中的公共 API（CEP.pattern()、PatternStream、CepPatternBuilder 等）
4. nop-stream-connector 中的连接器接口

调用 nop-stream 的外部模块：
- 搜索全仓库中 `import io.nop.stream.*` 的非 nop-stream 代码

### 检查结论

**零发现。**

原因：
1. nop-stream 作为底层框架模块，主要被上层应用（nop-stream-fraud-example）和测试代码使用，外部依赖很少。
2. nop-stream-api 为空壳，外部消费者直接依赖 nop-stream-core（这在维度03中已报告）。
3. nop-stream-connector 的接口（IBatchConsumerProvider 等）正确适配了 nop-batch-core 的接口，调用方向正确。
4. 未发现跨模块的硬编码依赖或版本不兼容问题。

**已知的跨模块问题**（已在其他维度中覆盖）：
- 维度03-01: nop-stream-api 空壳导致外部消费者无法依赖纯 API
- 维度03-09: RPC 接口参数类型跨模块耦合
