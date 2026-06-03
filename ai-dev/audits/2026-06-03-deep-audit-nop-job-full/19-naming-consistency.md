# 维度 19：命名与术语一致性

## 第 1 轮（初审）

### [维度19-01] IJobScheduler 中 suspend/pause 命名不一致

- **文件**: `nop-job/nop-job-api/src/main/java/io/nop/job/api/IJobScheduler.java:96-133`
- **证据片段**:
  ```java
  boolean resumeJob(@Name("jobName") String jobName);     // resume
  boolean suspendJob(@Name("jobName") String jobName);    // suspend
  default boolean pauseJobs(...) { if (suspendJob(...)) } // pause
  ```
- **严重程度**: P2
- **现状**: 同一接口中"暂停"概念使用了 suspend（核心方法）和 pause（批量方法）两个不同术语。对比 NopJobScheduleBizModel 使用 pause/resume 对。
- **风险**: 维护者不确定应使用 suspend 还是 pause。
- **建议**: 统一为 pause/suspend 中的一个（建议 pause，与 BizModel 一致）。
- **信心水平**: 高
- **误报排除**: 同一接口、同一概念、两个术语——这是真实的命名不一致。
- **复核状态**: 未复核

### [维度19-02] JobCoreErrors 中 5 个非标准错误码前缀

- **文件**: `nop-job/nop-job-core/src/main/java/io/nop/job/core/JobCoreErrors.java:24-37`
- **严重程度**: P3
- **现状**: ERR_JOB_TIMEOUT 等使用 "JOB_TIMEOUT" 而非 "nop.err.job.timeout" 前缀。代码注释说明这些是"status markers, not thrown exceptions"。
- **建议**: 低优先级，已知的设计取舍。可添加注释说明。
- **信心水平**: 高
- **误报排除**: 这些确实不是抛出的错误码，而是存储在 errorCode 字段中的状态标记。
- **复核状态**: 未复核
