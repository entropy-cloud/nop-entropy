# 维度 19：命名与术语一致性 + 维度 20：跨模块契约一致性

## 第 1 轮（初审）

### [维度19-01] CEP ErrorCode 字符串与 Java 常量名语义矛盾

- **文件**: `nop-stream-cep/.../NopCepErrors.java:27-29`
- **证据片段**:
```java
// 常量名: ERR_CEP_NOT_CONDITION_DOES_NOT_SUPPORT_GROUP
// error code 字符串: nop.err.cep.follow-not-does-support-group
// 语义矛盾：常量名说 "NOT condition does NOT support"，error code 说 "follow-not-does-support"
```
- **严重程度**: P2
- **现状**: error code 字符串 `follow-not-does-support-group` 可被误读为 "does support"（缺少否定）。常量名用 NOT_CONDITION，error code 用 follow-not。
- **建议**: 将 error code 改为 `nop.err.cep.not-condition-not-support-group`。
- **信心水平**: 确定
- **误报排除**: ErrorCode 的字符串是公共契约，语义矛盾会误导消费者。
- **复核状态**: 未复核

### [维度19-02] windowTime vs windowSize 双词汇描述同一概念

- **文件**: CepPatternPartModel（windowTime/getWindowTime）vs Pattern/Quantifier.Times（windowSize/getWindowSize）
- **严重程度**: P3
- **现状**: 模型层用 windowTime，API 层用 windowSize，NFACompiler 中赋值时混用。
- **建议**: 统一为 windowTime 或 windowSize。
- **信心水平**: 确定
- **误报排除**: 新贡献者可能认为两者是不同概念。
- **复核状态**: 未复核

### [维度19-03] CEP 模型枚举命名风格不一致

- **文件**: FollowKind（camelCase）vs AfterMatchSkipStrategyKind（UPPER_SNAKE_CASE）
- **严重程度**: P3
- **现状**: 同属 CEP 模型层的两个枚举使用相反的命名风格。
- **建议**: 统一风格或补充风格说明。
- **信心水平**: 确定
- **误报排除**: XDSL 模型文件中需要一致的命名风格。
- **复核状态**: 未复核

### [维度19-04] FraudAlert.java 使用 Apache License 头，同模块其他文件使用 Nop Platform 版权头

- **文件**: `nop-stream-fraud-example/.../FraudAlert.java:1-17`
- **严重程度**: P3
- **现状**: 该文件使用 Apache License 2.0 头部，而同模块其他文件使用 Nop Platform 版权声明。
- **建议**: 替换为 Nop Platform 标准版权声明，或注明原始来源。
- **信心水平**: 确定
- **误报排除**: 版权归属不一致可能引发许可证合规问题。
- **复核状态**: 未复核

## 维度 20：跨模块契约一致性 — 零发现

- SPI 机制正确实现（ICheckpointExecutorFactory、IDeploymentPlanProvider）
- Connector 外部依赖使用正确（optional scope，接口匹配）
- 配置项一致（无 @InjectValue，配置通过 IConfigReference 定义）
- RPC 接口方向正确
