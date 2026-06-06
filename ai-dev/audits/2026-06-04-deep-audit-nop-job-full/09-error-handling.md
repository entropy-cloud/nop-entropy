# 维度 09：错误处理与错误码

## 第 1 轮（初审）

### [维度09-01] Calendar 类群使用裸 IllegalArgumentException 而非 NopException

- **文件**: `nop-job/nop-job-core/src/main/java/io/nop/job/core/calendar/BaseCalendar.java:147,170`、`DailyCalendar.java:400,419,471,509,512,515,518`、`MonthlyCalendar.java:86,100,104,121`、`CronCalendar.java:188`
- **证据片段**:
  ```java
  // BaseCalendar.java:147
  throw new IllegalArgumentException("timeStamp must be greater 0");
  // DailyCalendar.java:400
  throw new IllegalArgumentException("Invalid time string '" + rangeStartingTimeString + "'");
  // MonthlyCalendar.java:86
  throw new IllegalArgumentException("The day parameter must be in the range of 1 to " + MAX_DAYS_IN_MONTH);
  ```

- **严重程度**: P2
- **现状**: Calendar 类群（从 Quartz 移植）共约 15 处使用裸 IllegalArgumentException。上层 CronExpression 已全部改用 NopException + ErrorCode。Calendar 类群残留裸 JDK 异常，不一致。
- **风险**: 裸 IllegalArgumentException 无法被上层统一捕获 NopException 提取结构化参数。
- **建议**: 统一为 NopException 并可选配 ErrorCode 或纯消息字符串。
- **信心水平**: 确定
- **误报排除**: 这些类属于 core 内部实现，按两档策略可用模块异常类。但 IllegalArgumentException 不属于 Nop 异常体系。
- **复核状态**: 未复核

### [维度09-02] 测试代码中使用 System.out.println

- **文件**: `nop-job/nop-job-core/src/test/java/io/nop/job/core/trigger/TestTrigger.java:97`
- **证据片段**:
  ```java
  System.out.println(StringHelper.join(times, "\n"));
  ```

- **严重程度**: P3
- **现状**: 测试代码使用 System.out.println 输出调试信息。
- **风险**: 仅测试代码，不影响生产。
- **建议**: 改用 SLF4J logger。
- **信心水平**: 确定
- **误报排除**: 确认为测试代码，非生产代码。
- **复核状态**: 未复核

### [维度09-03] CronExpression 中 9 处 NopException 无 .cause()（信息性确认：无需 cause）

- **文件**: `nop-job/nop-job-core/src/main/java/io/nop/job/core/utils/CronExpression.java:203,226,277,285,361,372,396,405,411,417`
- **证据片段**:
  ```java
  // 第 277-279 行（典型）
  throw new NopException(ERR_JOB_TRIGGER_PARSE_CRON_EXPR_FAIL)
          .param(ARG_CRON_EXPR, expression)
          .param("reason", "empty cron expression");
  ```

- **严重程度**: P3（信息性）
- **现状**: 9 处主动验证异常无需 .cause()。唯一需要 cause 的场景（第 100-103 行 catch-parse 异常）已正确处理。
- **风险**: 无实际风险。
- **建议**: 无需修改。记录为已审阅确认。
- **信心水平**: 确定
- **误报排除**: 已确认这些 NopException 都是主动验证错误，不存在被吞掉的原始异常。
- **复核状态**: 未复核

### [维度09-04] 4 个 ErrorCode 使用非标准命名（有兼容性原因）

- **文件**: `nop-job/nop-job-core/src/main/java/io/nop/job/core/JobCoreErrors.java:24-52`
- **证据片段**:
  ```java
  ErrorCode ERR_JOB_TIMEOUT = define("JOB_TIMEOUT", "Job task timed out");
  ErrorCode ERR_JOB_CANCELED = define("JOB_CANCELED", "Job fire/task canceled");
  ErrorCode ERR_JOB_OVERLAID = define("JOB_OVERLAID", "Job fire/task canceled by overlay");
  ErrorCode ERR_JOB_INVOKER_RETURNED_NULL = define("JOB_INVOKER_RETURNED_NULL", "Job invoker returned null promise");
  ```

- **严重程度**: P2
- **现状**: 项目规范要求 ErrorCode 使用 nop.err.xxx.yyy 格式。这 4 个使用大写蛇形命名。文件头注释解释了原因：这些错误码存储在 task/fire 的 errorCode 字段中作为状态标记，为向后兼容保留。
- **风险**: 违反命名规范但无功能影响。
- **建议**: 在代码注释或设计文档中更显式记录此例外决策。
- **信心水平**: 确定
- **误报排除**: 已确认有明确的向后兼容性原因。
- **复核状态**: 未复核

### [维度09-05] LocalJobScheduler.checkActive() 抛 NopException 缺少 .param() 上下文

- **文件**: `nop-job/nop-job-core/src/main/java/io/nop/job/core/LocalJobScheduler.java:209-212`
- **证据片段**:
  ```java
  void checkActive() {
      if (!active)
          throw new NopException(ERR_JOB_SCHEDULER_NOT_ACTIVE);
  }
  ```

- **严重程度**: P2
- **现状**: 公共 API 错误码 ERR_JOB_SCHEDULER_NOT_ACTIVE 定义正确（在 JobApiErrors 中），但抛出时未附加任何上下文参数。
- **风险**: 缺少诊断信息（如 schedulerId、当前状态），增加排查难度。
- **建议**: 添加 .param("active", active) 等上下文参数。
- **信心水平**: 确定
- **误报排除**: 已确认调用方（addJob/removeJob/resumeJob）有具体 jobName 上下文，checkActive 本身也应提供可用信息。
- **复核状态**: 未复核

### [维度09-06] 测试代码中使用裸 RuntimeException 模拟错误

- **文件**: `nop-job/nop-job-coordinator/src/test/java/io/nop/job/coordinator/engine/TestJobTimeoutChecker.java:310,357`、`TestDefaultJobCancelHandler.java:237,268`
- **证据片段**:
  ```java
  throw new RuntimeException("simulated update failure for t2");
  ```

- **严重程度**: P3
- **现状**: 测试代码中使用裸 RuntimeException 模拟故障场景。被测代码的 catch 块只做 LOG 不检查 NopException 特征，不会导致测试失真。
- **风险**: 仅测试代码。
- **建议**: 保持现状或按团队习惯统一。
- **信心水平**: 确定
- **误报排除**: 已确认被测代码不检查 NopException 特征。
- **复核状态**: 未复核

### [维度09-07] 公共 API 层 BizModel 错误码定义在 service 而非 api 模块

- **文件**: `nop-job/nop-job-service/src/main/java/io/nop/job/service/NopJobErrors.java:1-64`
- **证据片段**:
  ```java
  package io.nop.job.service;
  import io.nop.api.core.exceptions.ErrorCode;
  public interface NopJobErrors {
      ErrorCode ERR_JOB_SCHEDULE_ALREADY_ARCHIVED = ErrorCode.define(
              "nop.err.job.schedule.already-archived",
              "Archived schedule cannot be enabled or resumed"
      );
      // ... 更多 BizModel 错误码
  }
  ```

- **严重程度**: P1
- **现状**: NopJobErrors 定义了 GraphQL BizModel 层面的公共 API 错误码，这些错误码通过 GraphQL API 直接暴露给前端。按两档策略，公共 API 的 ErrorCode 应定义在 -api 模块。对比 JobApiErrors（在 api 模块）和 JobCoreErrors（在 core 模块）位置正确。
- **风险**: 下游模块无法在不引入 service 依赖的情况下引用这些 ErrorCode。违反分层原则。
- **建议**: 将 NopJobErrors 中的公共 API 错误码迁移至 nop-job-api 模块。
- **信心水平**: 确定
- **误报排除**: 不是"看起来不优雅"——按两档策略，GraphQL 接口的 ErrorCode 必须在 api 层定义。
- **复核状态**: 未复核

### [维度09-08] JobCoreErrors 缺少版权头

- **文件**: `nop-job/nop-job-core/src/main/java/io/nop/job/core/JobCoreErrors.java:1-2`
- **证据片段**:
  ```java
  package io.nop.job.core;
  // 无版权头
  ```

- **严重程度**: P3
- **现状**: 同模块其他文件均有标准版权头注释，此文件缺失。
- **风险**: 代码规范问题。
- **建议**: 补充标准版权头。
- **信心水平**: 确定
- **误报排除**: 已确认同目录其他 Java 文件有版权头。
- **复核状态**: 未复核
