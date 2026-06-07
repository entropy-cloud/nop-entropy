# 维度 21：单元测试有效性

## 第 1 轮（初审）

### [维度21-01] TestNopRetryJobRetryBridge.testOnFireFailed_includesErrorInfo 仅用 assertNotNull 未验证实际内容

- **文件**: `nop-job/nop-job-retry-adapter/src/test/java/io/nop/job/retry/adapter/TestNopRetryJobRetryBridge.java:84-86`
- **证据片段**:
  ```java
  MockRetryTask task = retryEngine.getSubmittedTasks().get(0);
  assertNotNull(task.lastRequest);
  assertNotNull(task.lastRequest.getData());
  ```
- **严重程度**: P2（反模式 P-5：过度使用 assertNotNull）
- **现状**: 测试名为 "includesErrorInfo" 但不验证 error info 是否正确传递到 request data 中。
- **风险**: 无法捕获"error info 未传递到 retry task"的 bug。
- **建议**: 补充对 `task.lastRequest.getData()` 内容的断言。
- **信心水平**: 确定
- **误报排除**: 测试名明确承诺验证 "ErrorInfo"，但断言不验证任何 info 字段。
- **复核状态**: 未复核

---

### [维度21-02] TestDefaultJobTaskBuilder 两个测试方法名不表达预期行为

- **文件**: `nop-job/nop-job-coordinator/src/test/java/io/nop/job/coordinator/engine/TestDefaultJobTaskBuilder.java:14-48`
- **证据片段**:
  ```java
  @Test
  void testBuildSingleTask() { ... }
  @Test
  void testBuildWithNullSnapshots() { ... }
  ```
- **严重程度**: P3（反模式 P-6：测试方法名不表达预期行为）
- **现状**: 方法名描述操作而非预期结果。
- **建议**: 重命名为表达预期行为的名称。
- **信心水平**: 确定
- **误报排除**: 同模块其他测试名均采用更好的命名惯例。
- **复核状态**: 未复核
