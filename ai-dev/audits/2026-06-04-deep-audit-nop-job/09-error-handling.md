# 维度 09：错误处理与错误码

## 第 1 轮（初审）

### [维度09-01] Calendar 类使用 IllegalArgumentException 而非 NopException

- **文件**: `nop-job/nop-job-core/src/main/java/io/nop/job/core/calendar/CronCalendar.java:188`、`DailyCalendar.java:400,419,471,509`、`MonthlyCalendar.java:86,100,104,121`、`BaseCalendar.java:147,170`
- **证据片段**:
  ```java
  // BaseCalendar.java:147
  throw new IllegalArgumentException("timeStamp must be greater 0");
  // DailyCalendar.java:400
  throw new IllegalArgumentException("Invalid time string '" + rangeStartingTimeString + "'");
  ```
- **严重程度**: P2
- **现状**: calendar 包中 12 处抛出 `IllegalArgumentException`，而非 `NopException`。这些类作为调度引擎内部组件，错误缺少 ErrorCode 标识和 `.param()` 上下文信息。
- **风险**: 当 calendar 参数校验失败时，错误会以 `IllegalArgumentException` 形式传播，不利于统一错误监控和国际化。不过实际场景中这些是内部调用链路。
- **建议**: 如果这些 calendar 类需要对外暴露公共 API，可替换为 `NopException(ERR_JOB_CALENDAR_INVALID_PARAM).param(...)`。如果是纯粹内部实现，可保持现状。
- **信心水平**: 很可能
- **误报排除**: 这些 calendar 类确实在框架调用链中可能被外部输入间接触发。
- **复核状态**: 未复核

---

### [维度09-02] JobCoreErrors 中部分 ErrorCode 使用非标准命名前缀

- **文件**: `nop-job/nop-job-core/src/main/java/io/nop/job/core/JobCoreErrors.java:24-26,31-33,37-38,51-52`
- **证据片段**:
  ```java
  ErrorCode ERR_JOB_TIMEOUT = define("JOB_TIMEOUT", "Job task timed out");
  ErrorCode ERR_JOB_CANCELED = define("JOB_CANCELED", "Job fire/task canceled");
  ErrorCode ERR_JOB_OVERLAID = define("JOB_OVERLAID", "Job fire/task canceled by overlay");
  ErrorCode ERR_JOB_INVOKER_RETURNED_NULL = define("JOB_INVOKER_RETURNED_NULL", "Job invoker returned null promise");
  ```
- **严重程度**: P3
- **现状**: 4 个 ErrorCode 使用不遵循 `nop.err.*` 前缀的字符串。代码注释说明这是为了与存储在数据库中的历史数据兼容。
- **风险**: 不一致的 ErrorCode 命名模式可能导致运维工具无法统一识别。实际影响有限。
- **建议**: 维持现状是合理的（数据库历史数据兼容）。
- **信心水平**: 确定
- **误报排除**: 这些值确实被写入数据库字段，需要向后兼容。
- **复核状态**: 未复核

---

### [维度09-03] JobCoreErrors 上存在多余的 @Locale("zh-CN") 注解

- **文件**: `nop-job/nop-job-core/src/main/java/io/nop/job/core/JobCoreErrors.java:8`
- **证据片段**:
  ```java
  @Locale("zh-CN")
  public interface JobCoreErrors {
  ```
- **严重程度**: P3
- **现状**: 所有 ErrorCode 的 description 都是英文字符串，但标注了 `@Locale("zh-CN")`。其他两个 Errors 类均无此注解。
- **风险**: 可能影响错误描述的国际化解析逻辑。
- **建议**: 移除 `@Locale("zh-CN")` 注解。
- **信心水平**: 很可能
- **误报排除**: `NopJobErrors.java` 和 `JobApiErrors.java` 均无此注解。
- **复核状态**: 未复核

---

**其余检查项为零发现**：无模块级异常类（直接使用 NopException），全部 throw 使用 NopException + ErrorCode + .param()，无吞异常，异常链保留正确，日志使用 SLF4J 规范。
