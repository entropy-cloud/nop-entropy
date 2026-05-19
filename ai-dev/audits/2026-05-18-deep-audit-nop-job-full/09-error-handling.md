# 维度09：错误处理与错误码

## 第 1 轮（初审）

---

### 发现 09-1：CronExpression 构造函数丢失原始异常链

**文件路径**: `nop-job/nop-job-core/src/main/java/io/nop/job/core/utils/CronExpression.java`
**行号范围**: 93–97
**严重程度**: **P1**

**证据代码片段**:
```java
// 行 93–97
try {
    parse(expression);
} catch (Exception e) {
    throw new NopException(ERR_JOB_TRIGGER_PARSE_CRON_EXPR_FAIL).param(ARG_CRON_EXPR, expression);
}
```

**现状**: `catch (Exception e)` 捕获了原始异常 `e`，但 `new NopException(...)` 构造时**未将 `e` 作为 cause 传入**。按 Nop 规范应为 `new NopException(ERR_..., e)`。

**风险**: 原始异常栈（如具体的字段解析错误、NumberFormatException 等）被完全丢弃，生产环境排查 Cron 表达式问题时只能看到"解析失败"而无根因信息。

**建议**:
```java
} catch (Exception e) {
    throw new NopException(ERR_JOB_TRIGGER_PARSE_CRON_EXPR_FAIL, e)
        .param(ARG_CRON_EXPR, expression);
}
```

**误报排除**: 无误报。错误处理文档明确要求"保留原始异常链"。

---

### 发现 09-2：CronExpression 内部方法使用 IllegalArgumentException 可逃逸到外部调用方

**文件路径**: `nop-job/nop-job-core/src/main/java/io/nop/job/core/utils/CronExpression.java`
**行号范围**: 197–198, 219, 348–349, 357–358, 379–394
**严重程度**: **P2**

**证据代码片段**:
```java
// 行 197–198 (doNext 方法，被 next() 调用)
throw new IllegalArgumentException(
    "Invalid cron expression \"" + this.expression + "\" led to runaway search for next core");

// 行 219 (setDaysOfMonth 内部)
throw new IllegalArgumentException("Overflow in day for expression \"" + this.expression + "\"");

// 行 348–349
throw new IllegalArgumentException("Incrementer has more than two fields: '" + field
    + "' in expression \"" + this.expression + "\"");

// 行 390–391
throw new IllegalArgumentException(
    "Range less than minimum (" + min + "): '" + field + "' in expression \"" + this.expression + "\"");
```

**现状**: 构造函数（行 95–96）对 `parse()` 抛出的 `IllegalArgumentException` 做了 NopException 包装，但 `next()` 等公开方法内部调用 `doNext()` → `findNext()` 路径中仍有 `IllegalArgumentException` 未经包装，可直接逃逸到调用方。

**风险**: 调用 `CronExpression.next()` 等方法时，部分异常路径以 `IllegalArgumentException` 形式抛出，调用方按 NopException 统一处理时会遗漏这些异常，导致未捕获的错误和不可预测的行为。错误消息为硬编码英文，不支持 i18n。

**建议**: 在 `next()` 公开方法入口增加 try-catch 统一包装，或将内部所有 `IllegalArgumentException` 替换为对应的 NopException + ErrorCode。考虑到该文件源自 Spring Framework（头部 Copyright 2002-2018），最小改动方案是在公开方法边界统一包装。

**误报排除**: 该文件为 Spring Framework 衍生代码（Apache 2.0 License），已做了部分适配（构造函数包装），但适配不完整。这不影响其违反平台规范的事实，但解释了成因。

---

### 发现 09-3：MonthlyCalendar 使用 IllegalArgumentException

**文件路径**: `nop-job/nop-job-core/src/main/java/io/nop/job/core/calendar/MonthlyCalendar.java`
**行号范围**: 84–87
**严重程度**: **P2**

**证据代码片段**:
```java
// 行 84–87
public boolean isDayExcluded(int day) {
    if ((day < 1) || (day > MAX_DAYS_IN_MONTH)) {
        throw new IllegalArgumentException("The day parameter must be in the range of 1 to " + MAX_DAYS_IN_MONTH);
    }
    return excludeDays[day - 1];
}
```

**现状**: 直接抛出 `IllegalArgumentException`，包含硬编码英文消息，未使用 NopException + ErrorCode。

**风险**: 与平台其他参数校验使用 NopException 的模式不一致。调用方无法通过错误码程序化处理该异常。不支持 i18n。

**建议**: 定义对应 ErrorCode 并使用 NopException：
```java
throw new NopException(JobCoreErrors.ERR_JOB_CALENDAR_INVALID_DAY)
    .param(ARG_DAY, day).param(ARG_MAX_DAYS, MAX_DAYS_IN_MONTH);
```

**误报排除**: 该文件源自 Terracotta（Copyright 2001-2009，Apache 2.0），属于第三方衍生代码。但作为 `nop-job-core` 的公开 API 的一部分，其异常行为会影响平台调用方。

---

### 发现 09-4：JobApiErrors 使用中文默认消息但缺少 @Locale 注解

**文件路径**: `nop-job/nop-job-api/src/main/java/io/nop/job/api/JobApiErrors.java`
**行号范围**: 14–19
**严重程度**: **P3**

**证据代码片段**:
```java
// 行 14–19
public interface JobApiErrors {
    String ARG_JOB_NAME = "jobName";

    ErrorCode ERR_JOB_UNKNOWN_JOB = define("nop.err.job.unknown-job", "未知的任务:{jobName}", ARG_JOB_NAME);

    ErrorCode ERR_JOB_SCHEDULER_NOT_ACTIVE = define("nop.err.job.scheduler-not-active", "调度器未激活");
}
```

**现状**: ErrorCode.define() 的第二参数使用了中文默认消息，但接口**未标注 `@Locale("zh-CN")`**。同模块的 `JobCoreErrors` 已正确标注。

**风险**: 平台 i18n 机制依赖 `@Locale` 注解识别默认消息语言。缺少该注解可能导致框架按默认 locale（通常 en）解析中文消息，产生编码或显示问题。影响范围有限但不一致。

**建议**: 添加 `@Locale("zh-CN")` 注解，与 `JobCoreErrors` 保持一致：
```java
@Locale("zh-CN")
public interface JobApiErrors {
```

**误报排除**: 平台规范（error-handling.md 示例）确实允许在 define() 中使用中文默认消息，这本身不是违规。问题仅在于缺少配套的 `@Locale` 注解。

---

### 发现 09-5：测试 Mock 类中直接抛出 RuntimeException

**文件路径**: `nop-job/nop-job-coordinator/src/test/java/io/nop/job/coordinator/engine/TestJobCompletionProcessor.java`
**行号范围**: 286–292, 311–315
**严重程度**: **P3**

**证据代码片段**:
```java
// 行 286–292 (MockRetryBridge 内部类)
@Override
public String onFireFailed(JobFireFailedEvent event) {
    callCount.incrementAndGet();
    this.lastEvent = event;
    if (shouldThrow) {
        throw new RuntimeException("bridge error");
    }
    return "retry-" + event.getJobFireId();
}

// 行 311–315 (MockAlarmHandler 内部类)
@Override
public void onFireFailed(JobAlarmEvent event) {
    failedCount.incrementAndGet();
    this.lastFailedEvent = event;
    if (throwOnFailed) throw new RuntimeException("alarm error");
}
```

**现状**: 测试中的 Mock 实现类使用 `RuntimeException` 模拟异常场景。

**风险**: 风险极低。这是测试代码中为验证异常处理路径而故意抛出的异常，不影响生产行为。仅在阅读测试时可能造成短暂困惑（为什么不用 NopException）。

**建议**: 可保持现状，也可改为 `NopException` 以保持风格统一。不阻塞发布。

**误报排除**: 无误报，但严格来说测试代码中 Mock 的异常模拟不适用生产规范。标记为 P3 是合适的。

---

## 排除项（检查后确认合规）

| 检查项 | 结果 |
|--------|------|
| **System.out / System.err** | 仅 `TestTrigger.java:97`（测试代码）存在一处 `System.out.println`，生产代码全部使用 SLF4J。✅ |
| **吞掉异常** | 所有 `catch (Exception e)` 均后续调用了 `LOG.error/warn(...)` 记录异常，未发现静默吞掉的情况。`CronExpression.isValidExpression()` 行 418 `catch(IllegalArgumentException ex) { return false; }` 是验证方法的合理返回语义。✅ |
| **NopException .param() 传递** | 所有业务 `throw new NopException(ERR_...)` 均正确调用了 `.param(...)` 传递上下文参数。✅ |
| **ErrorCode 命名规范** | `nop.err.job.*` 格式的错误码命名一致且符合 `nop.err.{模块}.{子域}.{错误}` 规范。`JOB_TIMEOUT` / `JOB_CANCELED` 等大写形式是作为数据库状态标记使用，不是抛出的异常，有注释说明。✅ |
| **ErrorCode.define() 中文消息** | `JobCoreErrors.java`（带 `@Locale("zh-CN")`）和 `NopJobErrors.java`（英文消息）均符合平台惯例，error-handling.md 示例本身就是中文 define。✅ |
| **异常链保留** | 除发现 09-1 外，其余 `catch` 后 re-throw 路径均正确传入了 cause。✅ |

## 汇总

| # | 严重程度 | 文件 | 问题 |
|---|---------|------|------|
| 09-1 | **P1** | CronExpression.java:95-97 | 异常链丢失，cause `e` 未传入 NopException |
| 09-2 | **P2** | CronExpression.java:197-394 | 内部 IllegalArgumentException 可通过 next() 逃逸 |
| 09-3 | **P2** | MonthlyCalendar.java:84-87 | IllegalArgumentException 未经 NopException 包装 |
| 09-4 | **P3** | JobApiErrors.java:14-19 | 使用中文默认消息但缺少 @Locale 注解 |
| 09-5 | **P3** | TestJobCompletionProcessor.java:286-315 | 测试 Mock 中 RuntimeException（不阻塞） |

## 深挖第 2 轮追加

**维度 09 无新发现。** 深挖验证范围：
- coordinator 模块（JobPlannerScannerImpl、JobDispatcherScannerImpl、JobTimeoutCheckerImpl、JobCompletionProcessorImpl、DefaultJobCancelHandler）中的错误处理均已检查，catch 块使用 `LOG.warn`/`LOG.error` 记录异常后继续处理，符合 Nop 平台规范
- worker 模块（JobWorkerScannerImpl）中的异常处理通过 `whenComplete` 回调处理异步异常，模式正确
- retry-adapter 模块（NopRetryJobRetryBridge）中的异常处理使用 NopException + ErrorCode，符合规范
- 所有 catch 块均未吞掉异常（均有日志记录或重新抛出）
- 日志使用 SLF4J，未发现 System.out/System.err 使用

## 维度复核结论

| 编号 | 标题 | 判断 | 理由 |
|------|------|------|------|
| 09-1 | CronExpression 构造函数丢失原始异常链 | 保留 P1 | 证据准确。`CronExpression.java:95-96` 中 `catch (Exception e)` 捕获异常后 `new NopException(ERR_JOB_TRIGGER_PARSE_CRON_EXPR_FAIL)` 确实未传入 `e` 作为 cause。原始异常栈被完全丢弃，生产排查只能看到"解析失败"无根因。 |
| 09-2 | CronExpression 内部 IllegalArgumentException 可逃逸 | 保留 P2 | 证据准确。`CronExpression.java:197-198` 等处 `throw new IllegalArgumentException(...)` 在 `next()` 调用链中可逃逸到调用方，绕过构造函数的 NopException 包装。 |
| 09-3 | MonthlyCalendar 使用 IllegalArgumentException | 保留 P2 | 证据准确。`MonthlyCalendar.java:84` 直接 `throw new IllegalArgumentException("The day parameter must be in the range of 1 to " + ...)`，硬编码英文消息，无 ErrorCode。 |
| 09-4 | JobApiErrors 缺少 @Locale 注解 | 保留 P3 | 证据准确。`JobApiErrors.java` 无 `@Locale("zh-CN")` 注解，而 `JobCoreErrors.java:8` 有。中文消息可能因缺少 locale 标记被框架按默认 locale 错误解析。 |
| 09-5 | 测试 Mock 类抛出 RuntimeException | 驳回 | 测试代码中的 Mock 内部类，不影响生产。标记为审计发现意义不大。 |

### 最终保留项

| 编号 | 严重程度 | 文件路径 | 一句话摘要 |
|------|---------|---------|-----------|
| 09-1 | P1 | `CronExpression.java:95-96` | 构造函数丢失原始异常链，生产排查无根因 |
| 09-2 | P2 | `CronExpression.java:197-198` | 内部 IllegalArgumentException 可逃逸到调用方 |
| 09-3 | P2 | `MonthlyCalendar.java:84` | 使用硬编码英文 IllegalArgumentException，无 ErrorCode |
| 09-4 | P3 | `JobApiErrors.java` | 缺少 @Locale 注解 |
