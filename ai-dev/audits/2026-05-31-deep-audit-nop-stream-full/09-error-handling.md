# 维度 09：错误处理与错误码

## 第 1 轮（初审）

### 正面发现

nop-stream 建立了完整的模块级异常层级：`StreamRuntimeException → NopException`，`StreamException → StreamRuntimeException`，`CheckpointStorageException → StreamException`，`MalformedPatternException → StreamRuntimeException`。

ErrorCode 集中定义：`NopStreamErrors.java`（45 个 ErrorCode）和 `NopCepErrors.java`（9 个 ErrorCode），全英文。约 97% 的 throw 使用 ErrorCode 模式。

### [维度09-01] fraud-example 8 处裸 IllegalArgumentException

- **文件**: `nop-stream-fraud-example/.../AccountTakeoverPattern.java:143,153,158,161,164` 及其他 3 个 pattern 文件
- **证据片段**:
  ```java
  throw new IllegalArgumentException("Match must contain 'login', 'change', and 'withdraw' events");
  ```
- **严重程度**: P2
- **现状**: 4 个 pattern 文件中共 8 处裸 IllegalArgumentException，不属于模块异常体系
- **风险**: 上游无法统一 catch StreamException 处理
- **建议**: 改用 `throw new StreamException(ERR_STREAM_INVALID_ARG).param(...)`
- **信心水平**: 确定
- **误报排除**: 虽然是 example 模块，但作为示范代码应遵循平台规范
- **复核状态**: 未复核

### [维度09-05] SkipToElementStrategy 混用异常类型

- **文件**: `nop-stream-cep/.../aftermatch/SkipToElementStrategy.java:61-64,77`
- **严重程度**: P1
- **现状**: 同一方法中使用 StreamException(String)（无 ErrorCode）、IllegalStateException 和正确的 StreamException(ERR_STREAM_SKIP_NO_MATCH)
- **建议**: 统一改为 ErrorCode 模式
- **信心水平**: 确定
- **误报排除**: 同文件第 81 行已正确使用 ErrorCode，不一致是 bug
- **复核状态**: 未复核

### [维度09-11] RichIterativeCondition 抛出裸 IllegalStateException

- **文件**: `nop-stream-cep/.../conditions/RichIterativeCondition.java:56`
- **严重程度**: P1
- **现状**: `throw new IllegalStateException("The runtime context has not been initialized.")` 不在异常体系中
- **风险**: CEP 引擎调用方 catch StreamException 时会漏掉此错误
- **建议**: 改用 StreamRuntimeException(ERR_STREAM_INVALID_STATE)
- **信心水平**: 确定
- **误报排除**: 源自 Apache Flink，可能未完全适配
- **复核状态**: 未复核

### [维度09-14] LocalFileCheckpointStorage.ensureDirectoryExists 吞掉异常

- **文件**: `nop-stream-runtime/.../checkpoint/storage/LocalFileCheckpointStorage.java:346-352`
- **严重程度**: P1
- **证据片段**:
  ```java
  private void ensureDirectoryExists(String dir) {
      try {
          Files.createDirectories(Paths.get(dir));
      } catch (Exception e) {
          LOG.warn("Failed to create directory: {}", dir, e);
      }
  }
  ```
- **现状**: 目录创建失败后仅记录 warn 日志继续执行，后续操作会因目录不存在而失败
- **建议**: 改为 throw new CheckpointStorageException(ERR_STREAM_CHECKPOINT_ERROR, e)
- **信心水平**: 确定
- **误报排除**: 不是幂等性设计——后续文件写入会失败但错误信息不相关
- **复核状态**: 未复核

### [维度09-06~10] 5 处 StreamException(String) 无 ErrorCode

- **文件**: SimpleStreamOperatorFactory.java:59, Task.java:197, SourceEnumerator.java:126, EmbeddedDistributedExecutor.java:193, DeweyNumber.java:180
- **严重程度**: P2
- **现状**: 使用字符串构造器而非 ErrorCode
- **建议**: 补充 ErrorCode
- **复核状态**: 未复核

### [维度09-13] JdbcCheckpointStorage DDL 异常日志级别过低

- **文件**: JdbcCheckpointStorage.java:439-441
- **严重程度**: P2
- **现状**: DDL 异常用 LOG.debug，生产环境默认不输出
- **建议**: 提升为 LOG.warn 或使用 IF NOT EXISTS DDL
- **复核状态**: 未复核

## 维度复核结论

异常体系设计优秀（97% 合规率）。P1 发现集中在少数混用场景和异常吞掉。fraud-example 的裸 IllegalArgumentException 虽然 P2 但应修复作为示范。

## 最终保留项

| 编号 | 严重程度 | 文件 | 摘要 |
|------|---------|------|------|
| 09-05 | P1 | SkipToElementStrategy.java | 混用异常类型 |
| 09-11 | P1 | RichIterativeCondition.java | 裸 IllegalStateException |
| 09-14 | P1 | LocalFileCheckpointStorage.java | 吞掉目录创建异常 |
| 09-01 | P2 | fraud-example 4个文件 | 8 处裸 IllegalArgumentException |
| 09-06 | P2 | SimpleStreamOperatorFactory.java | StreamException(String) 无 ErrorCode |
| 09-07 | P2 | Task.java | StreamException(String) 无 ErrorCode |
| 09-08 | P2 | SourceEnumerator.java | StreamException(String) 无 ErrorCode |
| 09-09 | P2 | EmbeddedDistributedExecutor.java | StreamException(String) 无 ErrorCode |
| 09-10 | P2 | DeweyNumber.java | StreamException(String) 无 ErrorCode |
| 09-13 | P2 | JdbcCheckpointStorage.java | DDL 异常日志级别过低 |
