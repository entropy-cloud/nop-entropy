# 维度 09：错误处理与错误码 — nop-code 模块

## 第 1 轮（初审）

nop-code 模块**遵守两层错误策略**。公共 API 层正确使用 NopException + ErrorCode + .param()。无裸 RuntimeException、无 printStackTrace、无中文错误消息、全面使用 SLF4J。

### [维度09-01] NopCodeException 已定义但从未使用

- **文件**: `nop-code-service/.../NopCodeException.java:1-14`
- **严重程度**: P3
- **现状**: 模块级异常类已定义但所有 throw 使用 `new NopException(...)` 而非 `new NopCodeException(...)`。
- **建议**: 要么全面使用 NopCodeException，要么删除。
- **信心水平**: 确定
- **复核状态**: 未复核

### [维度09-02] IProjectAnalyzer.analyzeIncremental 实现抛出裸 UnsupportedOperationException

- **文件**: `nop-code-core/.../ProjectAnalyzer.java:376-379`
- **严重程度**: P2
- **现状**: 接口方法实现抛出裸异常而非 NopException + ErrorCode。
- **建议**: 使用 NopException + 专用错误码或从接口移除方法。
- **信心水平**: 确定
- **复核状态**: 未复核

### [维度09-03] DeletedResourceStub 使用裸 UnsupportedOperationException (5处)

- **文件**: `nop-code-core/.../incremental/DeletedResourceStub.java:92-125`
- **严重程度**: P3（包内部类，影响有限）
- **复核状态**: 未复核

### [维度09-04] JavaFileAnalyzer 中空 catch 块 (2处)

- **文件**: `JavaFileAnalyzer.java:217-219,898-899`
- **证据片段**: 两个空 catch 块静默吞噬 JSON 解析异常，无日志。
- **严重程度**: P3
- **建议**: 添加 `LOG.debug(...)` 记录。
- **信心水平**: 确定
- **复核状态**: 未复核

### [维度09-05] JGraphT 重复边 catch 无日志 (2文件，3处)

- **文件**: `CriticalNodeAnalyzer.java:79-83`、`CommunityDetector.java:494-498,646-650`
- **严重程度**: P3
- **现状**: 2 个文件静默吞噬异常，GraphExporter 正确使用 LOG.debug。
- **建议**: 统一所有处使用 LOG.debug。
- **信心水平**: 确定
- **复核状态**: 未复核

### [维度09-06] ChangeAnalyzer.parseGitDiff 部分吞咽异常返回不完整数据

- **文件**: `ChangeAnalyzer.java:199-206`
- **严重程度**: P3
- **现状**: IOException/InterruptedException 被捕获后返回部分结果，调用者无法区分成功/失败。
- **建议**: 考虑向调用者信号失败状态。
- **信心水平**: 很可能
- **复核状态**: 未复核

### [维度09-07] saveReplacingExisting 属性复制失败以 TRACE 级别记录

- **文件**: `CodeIndexService.java:1464-1468`
- **严重程度**: P3
- **现状**: TRACE 级别在生产环境通常禁用，属性写入失败不可见。
- **建议**: 提升为 DEBUG 级别。
- **信心水平**: 确定
- **复核状态**: 未复核

## 最终保留项

| 编号 | 严重程度 | 文件 | 一句话摘要 |
|------|---------|------|-----------|
| 09-01 | P3 | NopCodeException.java | 已定义但从未使用 |
| 09-02 | P2 | ProjectAnalyzer.java:376 | 接口方法抛裸异常 |
| 09-03 | P3 | DeletedResourceStub.java | 5处裸异常 |
| 09-04 | P3 | JavaFileAnalyzer.java | 2处空 catch 块 |
| 09-05 | P3 | CriticalNodeAnalyzer+CommunityDetector | 3处无日志 catch |
| 09-06 | P3 | ChangeAnalyzer.java:199 | 部分数据静默返回 |
| 09-07 | P3 | CodeIndexService.java:1464 | TRACE 级别记录失败 |
