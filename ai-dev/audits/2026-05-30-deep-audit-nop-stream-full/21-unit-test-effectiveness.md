# 维度 21：单元测试有效性

## 审计范围

nop-stream 全部 195 个测试文件，对照 unit-test-antipatterns.md 反模式清单。

## 第 1 轮（初审）

### [维度21-01] 永真断言 — 测试不可能失败 [P1]

- **文件**: `nop-stream-cep/.../nfa/TestNFAExtended.java:626`
- **证据片段**:
```java
assertTrue(matches.isEmpty() || !matches.isEmpty());
```
- **严重程度**: P1
- **现状**: 逻辑永真式，无论 NFA timeout 逻辑是否正确都会通过。整个方法设置了 timeout 窗口和事件序列但断言什么都没验证。
- **命中反模式**: P-8（无效的负面测试）
- **建议**: 替换为对具体匹配结果的断言。
- **信心水平**: 确定
- **误报排除**: 永真断言在代码审查中容易被遗漏，但它是无法检测任何回归的硬伤。
- **复核状态**: 未复核

### [维度21-02] 12 个 qualifier 测试仅用 assertNotNull 作为唯一断言 [P1]

- **文件**: `nop-stream-cep/.../TestCepPatternBuilderModel.java`（多个方法）
- **严重程度**: P1
- **现状**: assertNotNull(pattern) 几乎不会失败——只要 builder 不抛异常就通过。qualifier 是否真正被应用到 pattern 上完全没有验证。将 qualifier 逻辑删除后 10/12 测试仍通过。
- **命中反模式**: P-5（过度使用 assertNotNull）
- **建议**: 验证 pattern 的实际属性而非仅非空。
- **信心水平**: 确定
- **误报排除**: 12 个方法全部只做 assertNotNull 是系统性问题。
- **复核状态**: 未复核

### [维度21-03] TestPatternStreamBuilder 三个方法全部只验证非空 [P1]

- **文件**: `nop-stream-cep/.../TestPatternStreamBuilder.java`
- **严重程度**: P1
- **现状**: 创建 pattern stream 并调用 process，但不喂入数据、不验证匹配结果。对比同模块的 TestCepPublicApiE2E（真正喂入事件并验证匹配结果），形成鲜明对比。
- **命中反模式**: P-5 + P-3
- **建议**: 添加实际数据流测试。
- **信心水平**: 确定
- **误报排除**: 对比同模块高质量测试后确认保护力缺失。
- **复核状态**: 未复核

### [维度21-04] TestMalformedPatternException 测试异常类层次而非行为 [P1]

- **文件**: `nop-stream-cep/.../pattern/TestMalformedPatternException.java`
- **严重程度**: P1
- **现状**: 三个方法仅检查 instanceof、toString 格式。这些是编译器已保证的语言级属性。异常的真正价值在于它是否在正确场景下被抛出（已在 TestPatternValidation 中覆盖）。
- **命中反模式**: P-1 + P-2
- **建议**: 低优先级，可标记 @Tag("low-value")。
- **信心水平**: 确定
- **误报排除**: 这些测试与有价值的 TestPatternValidation 职责重叠。
- **复核状态**: 未复核

### [维度21-05] TestWindowOperatorBasic 前两个方法均为低价值断言 [P1]

- **文件**: `nop-stream-runtime/.../operators/windowing/TestWindowOperatorBasic.java`
- **严重程度**: P1
- **现状**: 仅 assertNotNull + assertTrue(isEventTime())，等价于测试 new X() 不为 null。
- **命中反模式**: P-1 + P-5
- **建议**: 标记 @Tag("low-value")。
- **信心水平**: 确定
- **误报排除**: 类名暗示测试 WindowOperator 但实际不测试。
- **复核状态**: 未复核

### [维度21-06] 13 个 testToString 方法验证字符串格式 [P2]

- **文件**: 跨多个子模块的 13 个测试文件
- **严重程度**: P2
- **现状**: toString() 输出格式是实现细节，不是公共契约。测试只是镜像实现。
- **命中反模式**: P-2
- **建议**: 标记 @Tag("low-value")。
- **信心水平**: 确定
- **误报排除**: 如果 toString() 有序列化用途则例外，但当前无证据。
- **复核状态**: 未复核

### [维度21-07] TestCheckpointBarrier 全部 7 个方法为 getter/结构测试 [P2]

- **文件**: `nop-stream-core/.../checkpoint/TestCheckpointBarrier.java`
- **严重程度**: P2
- **现状**: CheckpointBarrier 是 @DataBean 式数据类，7 个方法均为 getter 往返或类型判断。
- **命中反模式**: P-1
- **建议**: 标记 @Tag("low-value")。
- **信心水平**: 确定
- **误报排除**: 部分方法有边际价值（验证基于类型的分支逻辑）。
- **复核状态**: 未复核

### [维度21-08] TestCepPatternBuilderModel.testBuildPatternConditionCount 逻辑有误 [P2]

- **文件**: `nop-stream-cep/.../TestCepPatternBuilderModel.java:128`
- **严重程度**: P2
- **现状**: 只检查 cond != null，如果 buildFromModel 忽略 where 条件但返回默认 trueFunction()，测试仍通过。
- **命中反模式**: P-5
- **建议**: 验证 condition 不是默认的 trueFunction。
- **信心水平**: 很可能
- **误报排除**: 测试意图是验证条件计数，但实现无法检测条件被忽略。
- **复核状态**: 未复核

## 整体评估

测试质量呈两极分化：
- **核心逻辑测试（~60%）**质量高：CEP NFA、Window Assigner、Checkpoint 并发安全等能捕获真实 bug
- **外围测试（~40%）**有凑覆盖率现象：永真断言、仅 assertNotNull、toString 格式验证
- 项目已使用 @Tag("low-value") 标记部分低价值测试，说明有自省意识
