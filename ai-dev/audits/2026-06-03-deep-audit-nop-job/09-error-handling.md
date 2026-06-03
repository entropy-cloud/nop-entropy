# 维度 09：错误处理审查

## 通过检查

- 无吞没异常 ✓
- 所有 catch 块使用 SLF4J 日志记录 ✓
- 生产代码中无 System.out/err ✓

## 发现

### [09-01] P2 — JobApiErrors 包含中文 ErrorCode 描述

- **文件**: JobApiErrors.java:17,19（nop-job-api 模块）
- **现状**: `JobApiErrors` 中有 2 个 ErrorCode 的 description 字段使用中文：
  - `"未知的任务:{jobName}"`
  - `"调度器未激活"`
- **违反规则**: AGENTS.md 要求错误消息使用英文。
- **建议**: 改为英文描述，中文信息通过 i18n 资源文件提供。

### [09-02] P2 — JobCoreErrors 包含中文 ErrorCode 描述

- **文件**: JobCoreErrors.java:15-16,18-19,39-40,42-43（nop-job-core 模块）
- **现状**: `JobCoreErrors` 中有 4 个 ErrorCode 的 description 字段使用中文，包括：
  - `"计算下一次触发时间时似乎陷入死循环..."`
  - 等
- **违反规则**: AGENTS.md 要求错误消息使用英文。
- **建议**: 改为英文描述。

### [09-03] P3 — 5 个状态标记 ErrorCode 使用非标准格式

- **文件**: JobCoreErrors.java:24-37
- **现状**: 5 个状态标记型 ErrorCode 使用 `JOB_TIMEOUT` 等裸标识符格式，而非标准的 `nop.err.job.*` 前缀格式。代码注释说明是为了向后兼容，但其中 `ERR_JOB_INVOKER_NOT_FOUND` 实际有被抛出的场景。
- **风险**: 格式不一致可能影响错误码的统一处理和文档生成。
- **建议**: 在可行的情况下逐步迁移到标准格式，或至少在文档中标注这些为保留的历史格式。

### [09-04] P3 — calendar 包中 14 处 IllegalArgumentException 抛出

- **文件**: DailyCalendar, BaseCalendar, MonthlyCalendar, CronCalendar
- **现状**: calendar 包中有 14 处直接抛出 `IllegalArgumentException`，这是从 Quartz 移植的代码。违反 AGENTS.md 中禁止使用裸 `RuntimeException` 的规则。
- **风险**: 这些异常绕过平台的错误处理框架，无法统一捕获和国际化。
- **建议**: 逐步替换为使用 `NopException` + 对应 ErrorCode。

### [09-05] P2 — NopJobTaskBizModel.delete() 缺少 .param() 上下文

- **文件**: NopJobTaskBizModel.java:26
- **现状**: `delete()` 方法在构建异常时缺少 `.param("jobTaskId", id)` 调用，导致错误消息中缺少关键的实体标识上下文。
- **建议**: 添加 `.param("jobTaskId", id)` 以便错误消息包含任务 ID。

### [09-06] P3 — ERR_JOB_TASK_DELETE_NOT_ALLOWED 描述不用户友好

- **文件**: NopJobErrors.java:43-46
- **现状**: `ERR_JOB_TASK_DELETE_NOT_ALLOWED` 的描述是内部实现细节而非用户友好的错误提示。
- **建议**: 修改为面向用户的描述。

### [09-07] P3 — LocalJobScheduler.addJob() 使用语义错误的错误码

- **文件**: LocalJobScheduler.java:81
- **现状**: `addJob()` 在"job already exists"场景下使用了 `ERR_JOB_UNKNOWN_JOB` 错误码，语义与实际情况不匹配。
- **建议**: 定义专门的 `ERR_JOB_ALREADY_EXISTS` 错误码。

### [09-08] P3 — DefaultJobExecutionContextBuilder 可能对 NPE 类型异常产生 null 描述

- **文件**: DefaultJobExecutionContextBuilder.java:28
- **现状**: `DefaultJobExecutionContextBuilder` 在处理 NPE 类型异常时可能产生 null 错误描述。
- **建议**: 对 null 异常消息添加 fallback 处理（如使用异常类名作为描述）。
