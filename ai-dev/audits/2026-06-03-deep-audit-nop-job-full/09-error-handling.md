# 维度 09：错误处理与错误码

## 第 1 轮（初审）

### [维度09-01] LocalJobScheduler.addJob 误用 ERR_JOB_UNKNOWN_JOB 错误码

- **文件**: `nop-job/nop-job-core/src/main/java/io/nop/job/core/LocalJobScheduler.java:78-83`
- **证据片段**:
  ```java
  ScheduledJob existing = jobs.get(spec.getJobName());
  if (existing != null) {
      if (!allowUpdate) {
          throw new NopException(io.nop.job.api.JobApiErrors.ERR_JOB_UNKNOWN_JOB)
                  .param(ARG_JOB_NAME, spec.getJobName());
      }
  ```
- **严重程度**: P1
- **现状**: 当 addJob(…, allowUpdate=false) 被调用且 job 已存在时，抛出 `ERR_JOB_UNKNOWN_JOB`（"未知的任务"）。实际语义是"任务已存在且不允许更新"。错误码与实际语义完全相反。
- **风险**: 客户端收到与实际情况相反的错误码，误导排查方向。如果调用方依赖此错误码做分支判断会导致逻辑错误。此接口是 nop-job-api 中的跨模块公共 API。
- **建议**: 新增专用 ErrorCode（如 `ERR_JOB_ALREADY_EXISTS`），在此处使用。
- **信心水平**: 高
- **误报排除**: 确认 `ERR_JOB_UNKNOWN_JOB` 定义描述为"未知的任务:{jobName}"，与"已存在"语义完全相反。
- **复核状态**: 未复核

### [维度09-02] NopJobTaskBizModel.delete() 缺少 .param() 诊断上下文

- **文件**: `nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobTaskBizModel.java:24-27`
- **证据片段**:
  ```java
  @Override
  public boolean delete(String id, io.nop.core.context.IServiceContext context) {
      throw new NopException(ERR_JOB_TASK_DELETE_NOT_ALLOWED);
  }
  ```
- **严重程度**: P2
- **现状**: GraphQL mutation 的 delete 方法直接抛出异常，未通过 `.param()` 传入 `id` 参数。
- **风险**: 错误响应中不包含正在操作的 task ID，运维排查时缺乏定位信息。对比同模块其他 BizModel 均传入 `.param()` 上下文。
- **建议**: 添加 `.param("jobTaskId", id)` 以与其他 BizModel 保持一致。
- **信心水平**: 高
- **误报排除**: 同模块 NopJobFireBizModel 等均遵循 `.param()` 模式。
- **复核状态**: 未复核

### [维度09-03] Calendar 内部类使用 IllegalArgumentException（与 CronExpression 不一致）

- **文件**: 4 个 Calendar 类（MonthlyCalendar.java、DailyCalendar.java、BaseCalendar.java、CronCalendar.java）
- **证据片段**:
  ```java
  // MonthlyCalendar.java:86
  throw new IllegalArgumentException("The day parameter must be in the range of 1 to " + MAX_DAYS_IN_MONTH);
  ```
- **严重程度**: P3
- **现状**: 4 个 Calendar 类共约 14 处 `throw new IllegalArgumentException`。同包的 CronExpression 对类似验证场景已使用 NopException + ErrorCode 模式。
- **风险**: Calendar 验证失败时没有 ErrorCode，调用方无法程序化区分不同验证错误。但这些类是纯内部实现。
- **建议**: 低优先级。如果未来 Calendar 配置通过用户输入暴露则应提升为 NopException + ErrorCode。
- **信心水平**: 中高
- **误报排除**: 这些 Calendar 类源自 Quartz/Spring 的移植代码，使用 IllegalArgumentException 是上游惯例。
- **复核状态**: 未复核

### [维度09-04] JobApiErrors 中文描述缺少 @Locale 注解（与 JobCoreErrors/NopJobErrors 不一致）

- **文件**: `nop-job/nop-job-api/src/main/java/io/nop/job/api/JobApiErrors.java:14-19`
- **证据片段**:
  ```java
  public interface JobApiErrors {
      String ARG_JOB_NAME = "jobName";
      ErrorCode ERR_JOB_UNKNOWN_JOB = define("nop.err.job.unknown-job", "未知的任务:{jobName}", ARG_JOB_NAME);
      ErrorCode ERR_JOB_SCHEDULER_NOT_ACTIVE = define("nop.err.job.scheduler-not-active", "调度器未激活");
  }
  ```
- **严重程度**: P3
- **现状**: JobApiErrors 的两个 ErrorCode 描述使用中文但该接口没有 `@Locale("zh-CN")` 注解。对比 JobCoreErrors 有 @Locale("zh-CN")，NopJobErrors 无 @Locale 且描述为英文。三处不一致。
- **风险**: 缺少 @Locale 注解可能导致中文描述被当作默认语言返回给非中文客户端。
- **建议**: 统一策略：添加 @Locale("zh-CN") 或将描述改为英文。
- **信心水平**: 中
- **误报排除**: 已确认 NopJobErrors 的描述全部为英文且无 @Locale 注解，两者模式不一致是客观事实。
- **复核状态**: 未复核

## 积极发现

- 异常链保留良好
- 无异常吞没
- .param() 使用规范（除发现 2）
- 公共 API 错误处理达标
- 无 RuntimeException 裸抛
- 后台扫描器错误隔离良好
