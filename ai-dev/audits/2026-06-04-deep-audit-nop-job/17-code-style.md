# 维度 17：代码风格与规范

## 第 1 轮（初审）

### [维度17-01] TestTrigger.java 使用 System.out.println 输出调试信息

- **文件**: `nop-job/nop-job-core/src/test/java/io/nop/job/core/trigger/TestTrigger.java:97`
- **证据片段**:
  ```java
  System.out.println(StringHelper.join(times, "\n"));
  ```
- **严重程度**: P2
- **现状**: 测试文件中使用 `System.out.println` 而非 LOG 或断言。
- **风险**: CI 环境中产生噪声输出。
- **建议**: 移除 `System.out.println`，改用 LOG.debug 或断言验证。
- **信心水平**: 确定
- **误报排除**: 全项目搜索仅此一处使用 System.out。
- **复核状态**: 未复核

---

### [维度17-02] 测试辅助方法中 executorKind 被冗余设置两次

- **文件**: `TestJobCoordinatorScanner.java:470-471`、`TestJobConcurrency.java:333-334`、`TestJobWorkerScanner.java:232-233`
- **证据片段**:
  ```java
  schedule.setExecutorKind(EXECUTOR_KIND_TEST);  // "test"
  schedule.setExecutorKind("testInvoker");       // 覆盖
  ```
- **严重程度**: P3
- **建议**: 删除第一行无效赋值。
- **信心水平**: 确定
- **误报排除**: 三个文件存在相同模式。
- **复核状态**: 未复核

---

### [维度17-03] 部分测试文件重复定义状态常量而非使用 _NopJobCoreConstants

- **文件**: `TestNopJobScheduleBizModel.java:37-41`
- **证据片段**:
  ```java
  private static final int SCHEDULE_STATUS_DISABLED = 0;
  private static final int SCHEDULE_STATUS_ENABLED = 10;
  ```
- **严重程度**: P3
- **建议**: 优先使用 `_NopJobCoreConstants` 中的常量。
- **信心水平**: 很可能
- **误报排除**: `TestJobFireStoreRace` 直接引用常量，两种风格并存。
- **复核状态**: 未复核
