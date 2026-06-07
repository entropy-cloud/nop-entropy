# 维度 17：代码风格与规范

## 第 1 轮（初审）

### [维度17-01] 测试文件中 System.out.println 残留

- **文件**: `nop-job-core/src/test/java/io/nop/job/core/trigger/TestTrigger.java:97`
- **证据片段**:
```java
System.out.println(StringHelper.join(times, "\n"));
```
- **严重程度**: P3
- **现状**: testCron() 中用 System.out.println 打印调试信息。
- **建议**: 删除此行或改为 LOG.debug。
- **信心水平**: 高
- **复核状态**: 未复核

### [维度17-02] 测试中 setExecutorKind 重复调用

- **文件**: `TestJobCoordinatorScanner.java:470-471`, `TestJobConcurrency.java:332-333`, `TestJobWorkerScanner.java:231-232`
- **证据片段**:
```java
schedule.setExecutorKind(EXECUTOR_KIND_TEST);
schedule.setExecutorKind("testInvoker");
```
- **严重程度**: P3
- **现状**: 第一行被第二行覆盖。常量 EXECUTOR_KIND_TEST 误导读者。
- **建议**: 删除第一行或统一使用常量。
- **信心水平**: 高
- **复核状态**: 未复核

### 正向确认

- 源文件 import 分组遵循 java.* → jakarta.* → third-party → io.nop.* 规范
- 命名遵循 PascalCase/camelCase/UPPER_SNAKE_CASE
