# 维度16：测试覆盖与质量

## 第 1 轮（初审）

### [维度16-01] 测试中使用 System.out.println 输出调试信息

- **文件**: `nop-job/nop-job-core/src/test/java/io/nop/job/core/trigger/TestTrigger.java:97`
- **证据片段**:
  ```java
  System.out.println(StringHelper.join(times, "\n"));
  ```
- **严重程度**: P3
- **现状**: testCron() 方法在断言前将中间结果打印到标准输出，调试遗留产物。
- **风险**: 极低，仅 CI 日志噪音。
- **建议**: 删除或改为 LOG.debug。
- **信心水平**: 确定
- **误报排除**: 确认是调试遗留。
- **复核状态**: 未复核

### [维度16-02] Mock Store 实现在多个测试文件中重复定义

- **文件**: TestJobTimeoutChecker.java:404-525、TestJobCompletionProcessor.java:325-380、TestJobE2E.java:233-323、TestBlockStrategies.java:114-129
- **证据片段**:
  ```java
  // TestJobTimeoutChecker.java:404-422
  static class MockTaskStore implements IJobTaskStore {
      private List<NopJobTask> runningTasks = new ArrayList<>();
      void addRunningTask(NopJobTask task) { runningTasks.add(task); }
      @Override
      public List<NopJobTask> fetchRunningTasks(int limit, IntRangeSet partitions) {
          return new ArrayList<>(runningTasks);
      }
      @Override public void updateTask(NopJobTask task) {}
      // ... 6 more empty methods
  }
  ```
- **严重程度**: P2
- **现状**: IJobScheduleStore 在 4 个测试文件中独立实现，IJobFireStore 3 次，IJobTaskStore 3 次，MockNamingService 4 次。
- **风险**: Store 接口新增方法时需逐一修改所有 mock，遗漏则编译失败。
- **建议**: 将共享 mock store 抽取到 `src/test/java/io/nop/job/coordinator/engine/mocks/` 包下。
- **信心水平**: 确定
- **误报排除**: 无。接口 Store 的 mock 实现确实在多处重复。
- **复核状态**: 未复核

## 合规确认项

- 22 个测试文件覆盖核心业务逻辑的所有关键路径（130+ 测试方法）。
- 错误路径覆盖充分。
- 无纯 getter/setter 测试、assertNotNull 遍历等低价值模式。
- 有效测试 vs 低价值测试比例约 100%。

## 最终保留项

| 编号 | 严重程度 | 文件 | 一句话摘要 |
|------|---------|------|----------|
| 16-01 | P3 | TestTrigger.java:97 | 测试中System.out.println调试遗留 |
| 16-02 | P2 | 多个coordinator测试 | Mock Store在4+个测试文件中重复定义 |
