# 维度 21：单元测试有效性

## 第 1 轮（初审）

### [维度21-01] TestDefaultJobCancelHandler 中 8 个测试方法零断言（命中 P-5）

- **文件**: `nop-job/nop-job-coordinator/src/test/java/io/nop/job/coordinator/engine/TestDefaultJobCancelHandler.java:152-309`
- **证据片段**:
  ```java
  void testCancelRunningTask_invokerNotFound() {
      setupEmptyContainer();
      handler.cancelRunningTask(schedule, fire, task);
      // 无任何 assert
  }
  ```
- **严重程度**: P2
- **现状**: 12 个测试方法中有 8 个完全没有断言（占 67%），依赖"无异常即通过"隐式行为。同文件另外 4 个方法使用了有意义的 assertTrue 断言。
- **风险**: 如果实现改为静默调用不该调用的方法，测试不会捕获。
- **建议**: 统一添加 `assertDoesNotThrow(...)` 或验证 invoker 未被调用。
- **信心水平**: 高
- **误报排除**: 同文件中 4 个有断言的方法证明开发者知道如何写有效测试。
- **复核状态**: 未复核

### [维度21-02] TestDefaultJobTaskBuilder 断言仅 assertNotNull，无法区分正确与错误实现（命中 P-5+P-3）

- **文件**: `TestDefaultJobTaskBuilder.java:15-34, 37-48`
- **严重程度**: P2
- **现状**: `testBuildSingleTask` 和 `testBuildWithNullSnapshots` 仅用 assertNotNull 检查 payload 非 null。若实现改为始终写入空 map，测试仍通过。
- **建议**: 断言 payload 中的 jobFireId 和 jobParamsSnapshot 内容。
- **信心水平**: 高
- **误报排除**: 已验证"改成错误实现后测试仍通过"。
- **复核状态**: 未复核

### [维度21-03] TestNopRetryJobRetryBridge 断言不验证 error info 内容（命中 P-5+P-6）

- **文件**: `TestNopRetryJobRetryBridge.java:72-86`
- **严重程度**: P2
- **现状**: 方法名 testOnFireFailed_includesErrorInfo 但断言只检查 data 非 null，未验证 errorCode/errorMessage 内容。若实现删除了 error 信息传递，测试仍通过。
- **建议**: 断言 data 中的 errorCode 和 errorMessage 值。
- **信心水平**: 高
- **误报排除**: 方法名说 "includesErrorInfo" 但未验证 info 是否真的 included。
- **复核状态**: 未复核

## 测试整体评价

nop-job 模块约 85% 的测试方法使用了有意义的断言。发现的 14 个反模式主要集中在一个文件（TestDefaultJobCancelHandler）的零断言问题和少数文件中的 assertNotNull 弱断言。未命中 P-1（getter/setter 测试）、P-4（实现耦合）、P-7（隐式依赖）、P-8（无效负面测试）等更严重的反模式。
