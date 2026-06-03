# 维度17：代码风格与规范

## 第 1 轮（初审）

### [维度17-01] NopJobErrors 类声明缺少空格

- **文件**: `nop-job/nop-job-service/src/main/java/io/nop/job/service/NopJobErrors.java:12`
- **证据片段**:
  ```java
  public interface NopJobErrors{
  ```
- **严重程度**: P3
- **现状**: `{` 前缺少空格，应为 `NopJobErrors {`。同模块其他接口均使用空格。
- **风险**: 极低，编译无影响。
- **建议**: 添加空格保持一致性。
- **信心水平**: 确定
- **误报排除**: 无。
- **复核状态**: 未复核

### [维度17-02] 测试中 setExecutorKind 连续调用两次，第一次无效

- **文件**: TestJobConcurrency.java:332-333、TestJobCoordinatorScanner.java:467-468、TestJobStoreImpl.java:123-124、TestJobWorkerScanner.java:231-232
- **证据片段**:
  ```java
  schedule.setExecutorKind(EXECUTOR_KIND_TEST);  // "test" — 立即被覆盖
  schedule.setExecutorKind("testInvoker");         // "testInvoker" — 实际生效的值
  ```
- **严重程度**: P2
- **现状**: 4 个测试文件中 EXECUTOR_KIND_TEST 常量被设置后立即被另一个字符串覆盖。第一次调用完全无效。
- **风险**: 代码可读性降低。若参考这些方法编写涉及实际 invoker 解析的新测试会失败。
- **建议**: 删除无效的第一次调用。
- **信心水平**: 确定
- **误报排除**: 确认是死代码。
- **复核状态**: 未复核

## 最终保留项

| 编号 | 严重程度 | 文件 | 一句话摘要 |
|------|---------|------|----------|
| 17-01 | P3 | NopJobErrors.java:12 | 类声明缺少空格 |
| 17-02 | P2 | 4个测试文件 | setExecutorKind连续调用两次，第一次无效 |
