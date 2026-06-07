# 维度 21：单元测试有效性

## 第 1 轮（初审）

### [维度21-01] TestEdgeProvenance 大量命中 P-1 反模式（纯 Getter/Setter 往返测试）

- **文件**: `nop-code/nop-code-core/src/test/java/io/nop/code/core/model/TestEdgeProvenance.java`
- **严重程度**: P2
- **现状**: 8 个测试方法中至少 5 个是纯 setter-then-getter 往返，无法捕获业务逻辑 bug。
- **建议**: 替换为测试 truncated 标志对下游分析器行为的影响。
- **信心水平**: 确定
- **误报排除**: 测试由 @DataBean 自动生成的 getter/setter，编译器已保证正确性。
- **复核状态**: 未复核

### [维度21-02] TestTruncatedFlag 完全命中 P-1 反模式

- **文件**: `nop-code/nop-code-core/src/test/java/io/nop/code/core/graph/TestTruncatedFlag.java`
- **严重程度**: P2
- **现状**: 2 个测试方法完全是 setter-then-getter 往返，未测试 truncated 标志的业务效果。
- **建议**: 测试"当 SymbolTable 被截断时，extractor 是否跳过处理"。
- **信心水平**: 确定
- **误报排除**: truncated 标志有明确的业务语义（防止在截断数据上运行分析），但未被测试。
- **复核状态**: 未复核

### [维度21-03] TestServiceLayerErrorPaths 命中 P-2 反模式（测试常量值）

- **文件**: `nop-code/nop-code-service/src/test/java/io/nop/code/service/impl/TestServiceLayerErrorPaths.java`
- **严重程度**: P3
- **现状**: 6 个测试方法全是"创建 NopException，assertEquals(errorCode, ex.getErrorCode())"，等价于测试常量赋值。
- **建议**: 嵌入到集成测试中作为附带检查，而非独立文件。
- **信心水平**: 确定
- **误报排除**: ErrorCode 常量的正确性由使用处的集成测试间接验证。
- **复核状态**: 未复核

### [维度21-04] TestChangeAnalyzer 部分测试验证过于宽松

- **文件**: `nop-code/nop-code-flow/src/test/java/io/nop/code/flow/TestChangeAnalyzer.java`
- **严重程度**: P3
- **现状**: testRiskScoringDimensionsPopulated 全是 assertNotNull，改为返回空对象测试仍通过。方法名不表达预期行为。
- **建议**: 添加具体值断言。
- **信心水平**: 高
- **误报排除**: assertNotNull 遍历枚举是 P-5 反模式的典型表现。
- **复核状态**: 未复核

## 最终保留项

| 编号 | 严重程度 | 文件路径 | 一句话摘要 |
|------|---------|---------|-----------|
| [维度21-01] | P2 | TestEdgeProvenance.java | 大量 P-1 反模式（getter/setter 往返测试） |
| [维度21-02] | P2 | TestTruncatedFlag.java | 完全 P-1 反模式 |
| [维度21-03] | P3 | TestServiceLayerErrorPaths.java | P-2 反模式（测试常量值） |
| [维度21-04] | P3 | TestChangeAnalyzer.java | 验证过于宽松 |
