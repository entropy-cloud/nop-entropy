# 维度19：命名与术语一致性

## 第 1 轮（初审）

### [维度19-01] ERR_RPC_INVOKER_MISSING_PARAM 命名不符合模块内约定

- **文件**: `nop-job/nop-job-service/src/main/java/io/nop/job/service/NopJobErrors.java:50-53`
- **证据片段**:
  ```java
  ErrorCode ERR_RPC_INVOKER_MISSING_PARAM = ErrorCode.define(
          "nop.err.job.rpc-invoker-missing-param",
          "RPC invoker missing required parameter: {paramName}"
  );
  ```
- **严重程度**: P2
- **现状**: NopJobErrors 中所有其他错误码常量以 `ERR_JOB_` 为前缀（如 ERR_JOB_SCHEDULE_ALREADY_ARCHIVED），唯独此常量缺少 `JOB_` 中间段。不过其实际字符串值 `nop.err.job.*` 符合规范。
- **风险**: 命名不一致增加认知负担，不影响运行时。
- **建议**: 重命名为 `ERR_JOB_RPC_INVOKER_MISSING_PARAM`。
- **信心水平**: 确定
- **误报排除**: 无。
- **复核状态**: 未复核

### [维度19-02] JobCoreErrors 状态标记型错误码使用裸字符串

- **文件**: `nop-job/nop-job-core/src/main/java/io/nop/job/core/JobCoreErrors.java:24-36`
- **证据片段**:
  ```java
  ErrorCode ERR_JOB_TIMEOUT = define("JOB_TIMEOUT", "Job task timed out");
  ErrorCode ERR_JOB_CANCELED = define("JOB_CANCELED", "Job fire/task canceled");
  ```
- **严重程度**: P3
- **现状**: 5 个 ErrorCode 使用裸字符串（JOB_TIMEOUT 等），注释说明是数据库存储值、有向后兼容性考量。
- **风险**: 命名不一致但已有数据依赖。
- **建议**: 保持现状，加强注释说明。
- **信心水平**: 确定
- **误报排除**: 注释已解释原因，且值直接写入数据库。
- **复核状态**: 未复核

## 最终保留项

| 编号 | 严重程度 | 文件 | 一句话摘要 |
|------|---------|------|----------|
| 19-01 | P2 | NopJobErrors.java:50 | ERR_RPC_INVOKER_MISSING_PARAM缺少JOB_前缀 |
| 19-02 | P3 | JobCoreErrors.java:24-36 | 状态标记ErrorCode使用裸字符串 |
