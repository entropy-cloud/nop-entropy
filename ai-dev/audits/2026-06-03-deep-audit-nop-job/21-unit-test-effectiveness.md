# 维度 21：单元测试有效性

## 第 1 轮（初审）

### [维度21-01] 测试整体保护力高

- **严重程度**: 信息性
- **现状**: 核心业务逻辑（Planner/Dispatcher/Completion/Timeout/BizModel/Store竞态）的测试有充分保护力。通过思维实验验证：关键逻辑改为错误实现后，测试会失败。

### [维度21-02] TestTrigger.testCron 的 System.out.println — P-2 反模式

- **文件**: `TestTrigger.java:97`
- **严重程度**: P3
- **现状**: 测试有充分的断言，System.out.println 纯粹是调试残留。命中 P-2（测试元数据属性）。
- **建议**: 删除行 97。
- **信心水平**: 高
- **复核状态**: 未复核

### [维度21-03] Mock Store 实现在多个测试中重复

- **文件**: TestJobE2E, TestJobCompletionProcessor, TestJobTimeoutChecker 各自定义独立的 Mock Store 实现
- **严重程度**: P3
- **现状**: Mock 类功能高度重复。如果 IJobScheduleStore 接口新增方法，需更新 4+ 个 Mock 类。
- **建议**: 考虑提取公共 StubJobStores 工具类。
- **信心水平**: 高
- **复核状态**: 未复核

### 正向确认

- 无 P-1（纯 getter/setter 往返）、P-4（与实现高度耦合）、P-7（隐式依赖）反模式
- 测试方法名清晰表达预期行为（P-6 通过）
