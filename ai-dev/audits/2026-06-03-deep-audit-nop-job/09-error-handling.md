# 维度09：错误处理与错误码

## 第 1 轮（初审）

### [维度09-01] JobCoreErrors.java 中 4 个 ErrorCode 描述使用中文

- **文件**: `nop-job/nop-job-core/src/main/java/io/nop/job/core/JobCoreErrors.java:16-43`
- **证据片段**:
  ```java
  ErrorCode ERR_JOB_TRIGGER_LOOP_COUNT_EXCEED_LIMIT = define("nop.err.job.trigger.loop-count-exceed-limit",
          "计算下一次触发时间时似乎陷入死循环，循环次数超过最大限制", ARG_LOOP_COUNT);
  
  ErrorCode ERR_JOB_TRIGGER_PARSE_CRON_EXPR_FAIL = define("nop.err.job.trigger.parse-cron-expr-fail",
          "解析定时器表达式失败:{cronExpr}", ARG_CRON_EXPR);
  
  ErrorCode ERR_JOB_EXECUTOR_REF_EMPTY = define("nop.err.job.executor-ref-empty",
          "Job的执行器引用为空");
  
  ErrorCode ERR_JOB_EXECUTOR_KIND_EMPTY = define("nop.err.job.executor-kind-empty",
          "Job的执行器类型为空");
  ```
- **严重程度**: P2
- **现状**: 4 个 ErrorCode 的 description 使用中文。文件包含 `@Locale("zh-CN")` 注解，但项目规范要求错误消息应为英文。
- **风险**: 违反 AGENTS.md 中"Error messages must be in English"规范。国际化场景下错误消息不可翻译。
- **建议**: 将描述改为英文，中文通过 i18n 资源文件提供。
- **信心水平**: 确定
- **误报排除**: 规范明确要求英文错误消息。
- **复核状态**: 未复核

### [维度09-02] JobApiErrors.java 公共 API 层 2 个 ErrorCode 描述使用中文

- **文件**: `nop-job/nop-job-api/src/main/java/io/nop/job/api/JobApiErrors.java:17-19`
- **证据片段**:
  ```java
  ErrorCode ERR_JOB_UNKNOWN_JOB = define("nop.err.job.unknown-job", "未知的任务:{jobName}", ARG_JOB_NAME);
  ErrorCode ERR_JOB_SCHEDULER_NOT_ACTIVE = define("nop.err.job.scheduler-not-active", "调度器未激活");
  ```
- **严重程度**: P2
- **现状**: nop-job-api 是跨模块公共 API 层，2 个 ErrorCode 使用中文描述。
- **风险**: 公共 API 层错误消息直接影响外部调用方。违反英文消息规范。
- **建议**: 改为英文：`"Unknown job: {jobName}"` 和 `"Job scheduler is not active"`。
- **信心水平**: 确定
- **误报排除**: 无。公共 API 层应优先遵循国际化规范。
- **复核状态**: 未复核

### [维度09-03] JobCoreErrors 中 5 个状态标记 ErrorCode 不遵循 nop.err.* 命名

- **文件**: `nop-job/nop-job-core/src/main/java/io/nop/job/core/JobCoreErrors.java:24-36`
- **证据片段**:
  ```java
  ErrorCode ERR_JOB_TIMEOUT = define("JOB_TIMEOUT", "Job task timed out");
  ErrorCode ERR_JOB_INVOKER_NOT_FOUND = define("JOB_INVOKER_NOT_FOUND", "Job invoker not found");
  ErrorCode ERR_JOB_CANCELED = define("JOB_CANCELED", "Job fire/task canceled");
  ErrorCode ERR_JOB_OVERLAID = define("JOB_OVERLAID", "Job fire/task canceled by overlay");
  ErrorCode ERR_JOB_EXECUTION_FAILED = define("JOB_EXECUTION_FAILED", "Job execution failed");
  ```
- **严重程度**: P3
- **现状**: 5 个 ErrorCode 使用裸字符串（如 `JOB_TIMEOUT`），注释说明是"status markers, not thrown exceptions"，值直接存储到数据库。有向后兼容性考量。
- **风险**: 命名不一致增加新人理解成本，但有注释解释且有数据兼容性约束。
- **建议**: 保持现状，在注释中更明确标注"新增状态标记请使用 nop.err.job.* 前缀"。
- **信心水平**: 确定
- **误报排除**: 注释已解释原因且有数据依赖。
- **复核状态**: 未复核

### [维度09-04] Calendar 类 14 处使用 IllegalArgumentException

- **文件**: `nop-job/nop-job-core/src/main/java/io/nop/job/core/calendar/` 下 BaseCalendar(2处)、MonthlyCalendar(4处)、DailyCalendar(7处)、CronCalendar(1处)
- **证据片段**:
  ```java
  // BaseCalendar.java:146-147
  if (timeStamp <= 0) {
      throw new IllegalArgumentException("timeStamp must be greater 0");
  }
  ```
- **严重程度**: P3
- **现状**: Calendar 类层次直接抛出 IllegalArgumentException。这些类可能从 Quartz 移植而来，是内部参数校验。
- **风险**: 异常不含 ErrorCode，但不太可能被外部代码直接触发。
- **建议**: 保持现状（移植代码改动成本高），或在 Calendar 公共接口文档中声明参数校验责任。
- **信心水平**: 很可能
- **误报排除**: 移植代码，改动成本高于收益。
- **复核状态**: 未复核

## 合规确认项

- NopJobErrors.java 的 7 个 ErrorCode 全部使用 nop.err.job.* 命名，描述全英文，有 ARG 常量。
- BizModel 中所有 throw 均使用 NopException + ErrorCode + .param() 模式。
- Store/Coordinator 层的 catch 块均正确记录日志，无吞掉异常的情况。
- 无 System.out/System.err 在生产代码中。

## 维度复核结论

待复核。

## 最终保留项

| 编号 | 严重程度 | 文件 | 一句话摘要 |
|------|---------|------|----------|
| 09-01 | P2 | JobCoreErrors.java:16-43 | 4个ErrorCode描述使用中文 |
| 09-02 | P2 | JobApiErrors.java:17-19 | 公共API层2个ErrorCode描述使用中文 |
| 09-03 | P3 | JobCoreErrors.java:24-36 | 5个状态标记ErrorCode不遵循nop.err.*命名 |
| 09-04 | P3 | Calendar类(14处) | 使用IllegalArgumentException而非NopException |
