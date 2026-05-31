# 维度 21：单元测试有效性

## 第 1 轮（初审）

### [维度21-01] TestCepPatternBuilderModel 10 个 qualifier 测试仅 assertNotNull

- **文件**: `nop-stream-cep/src/test/.../TestCepPatternBuilderModel.java:186-316`
- **严重程度**: P1
- **命中反模式**: P-5 (assertNotNull 过度使用) + P-3 (仅测 happy path)
- **现状**: 10 个测试方法对 qualifier (oneOrMore, times, consecutive, greedy, optional, subtype 等) 仅断言 assertNotNull(pattern)。如果 buildFromModel 完全忽略所有 qualifier 设置，全部 10 个测试仍通过。
- **风险**: 无法捕获 qualifier 逻辑 bug。
- **建议**: 通过 pattern.getQuantifier()、pattern.getTimes() 等断言验证 qualifier 是否正确传播。
- **信心水平**: 确定
- **误报排除**: 变异测试验证确认。
- **复核状态**: 未复核

### [维度21-02] TestPatternStreamBuilder 全部 3 个测试仅验证非空

- **文件**: `nop-stream-cep/src/test/.../TestPatternStreamBuilder.java:51-117`
- **严重程度**: P1
- **命中反模式**: P-3 + P-5
- **现状**: 3 个测试均只验证返回对象非空和类型正确，无实际模式匹配行为验证。
- **风险**: PatternStream 是 CEP 公共 API 入口，缺少行为验证。
- **建议**: 至少一个测试构造包含匹配事件的流，验证 processMatch 被正确调用。
- **信心水平**: 确定
- **误报排除**: Builder 测试也应验证基本行为。
- **复核状态**: 未复核

### [维度21-03] TestAfterMatchSkipStrategies 7/11 个测试仅 getter 返回值

- **文件**: `nop-stream-cep/src/test/.../TestAfterMatchSkipStrategies.java:10-75`
- **严重程度**: P2
- **命中反模式**: P-1 + P-2
- **现状**: 7 个测试仅检查 isSkipStrategy()、getPatternName() 返回值。无 NFA 匹配行为验证。
- **建议**: 添加端到端测试验证不同 skip strategy 的匹配数量和内容。
- **信心水平**: 很可能
- **误报排除**: 单例验证有边际价值。
- **复核状态**: 未复核

### [维度21-04] TestConnectorConsistencyCapability 枚举排序测试依赖 ordinal

- **文件**: `nop-stream-connector/src/test/.../TestConnectorConsistencyCapability.java:52-71`
- **严重程度**: P2
- **命中反模式**: P-2
- **现状**: 测试依赖 enum ordinal() 大小关系，锁定声明顺序。
- **建议**: 删除或改为 compareTo 语义测试。
- **信心水平**: 很可能
- **误报排除**: ordinal 未用于序列化。
- **复核状态**: 未复核

### [维度21-05] TestFingerprintAndTerminationMode getter/setter 往返测试

- **文件**: `nop-stream-runtime/src/test/.../TestFingerprintAndTerminationMode.java:201-227, 340-356`
- **严重程度**: P2
- **命中反模式**: P-1
- **现状**: 4 个 getter/setter 往返测试，无行为验证。null 默认值测试有边际价值。
- **建议**: 保留 null 默认值测试，删除其余 3 个。
- **信心水平**: 确定
- **误报排除**: null 默认值测试有边际价值。
- **复核状态**: 未复核

## 有效测试 vs 低价值测试比例

| 分类 | 估计数量 | 占比 |
|------|---------|------|
| 高价值行为测试 | ~1380 | ~91% |
| 边际价值测试（已标记 low-value） | ~29 | ~2% |
| 低价值测试 | ~40 | ~3% |
| 有效但覆盖不足 | ~64 | ~4% |

## 最终保留项

| 编号 | 严重程度 | 文件 | 摘要 |
|------|---------|------|------|
| 21-01 | P1 | TestCepPatternBuilderModel.java | 10 个 qualifier 测试仅 assertNotNull |
| 21-02 | P1 | TestPatternStreamBuilder.java | 3 个测试仅验证非空 |
| 21-03 | P2 | TestAfterMatchSkipStrategies.java | 7 个测试仅 getter |
| 21-04 | P2 | TestConnectorConsistencyCapability.java | 枚举 ordinal 测试 |
| 21-05 | P2 | TestFingerprintAndTerminationMode.java | getter/setter 往返 |
