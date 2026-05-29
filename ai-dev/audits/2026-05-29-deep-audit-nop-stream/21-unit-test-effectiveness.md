# 维度21：单元测试有效性

## 第 1 轮（初审）

### 检查范围说明

本维度与维度 16（测试覆盖与质量）高度重叠。维度 16 已详细审查了测试覆盖的盲区和质量问题。本维度侧重于测试是否真正能捕获 bug（反模式检查）。

维度 16 中已发现的关键测试有效性问题：
- [维度16-01] CepOperator processing-time 路径完全无测试（P1）
- [维度16-02] NFA 条件异常路径无测试（P2）
- [维度16-03] CheckpointBarrierTracker extra-ACK bug 已知但未修复（P2）

### 补充发现

除维度 16 已覆盖的问题外，对反模式清单（P-1 到 P-8）的检查结果：

- **P-1（纯 getter/setter 往返测试）**：未发现系统性模式。少量 `getXxx()` 测试验证了状态转换后的值，不属于纯 getter 往返。
- **P-2（测试元数据属性而非行为）**：未发现。
- **P-3（只测 happy path）**：已覆盖（见维度 16 发现 01、02）。
- **P-4（测试与实现高度耦合）**：部分 CepOperator 测试通过反射设置 `processingTimeService`，耦合度可接受。
- **P-5（过度使用 assertNotNull）**：未发现系统性模式。
- **P-6（测试方法名不表达预期行为）**：CEP 测试方法名如 `testStrictContiguity`、`testOptionalFollowedBy` 等表达清晰。
- **P-7（测试之间有隐式依赖）**：未发现。每个测试方法独立 setup。
- **P-8（无效的负面测试）**：`TestCheckpointBarrierTrackerConcurrency.testExtraAckTriggersCallbackAgainKnownIssue` 命中 P-8——断言 `>= 1` 几乎不可能失败，不构成有效验证。

**结论**：维度 16 已覆盖了主要的测试有效性问题。本维度无额外高价值发现。
