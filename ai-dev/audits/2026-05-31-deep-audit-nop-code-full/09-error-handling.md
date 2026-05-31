# 审核维度 09：错误处理与错误码

## 第 1 轮（初审）

### [维度09-01] GraphExporter.export() 公共 API 路径使用裸 IllegalArgumentException

- **文件**: `nop-code/nop-code-graph/src/main/java/io/nop/code/graph/export/GraphExporter.java:35`
- **证据片段**:
  ```java
  throw new IllegalArgumentException("Unsupported format: " + format);
  ```
- **严重程度**: P2
- **现状**: GraphExporter.export() 被 CodeGraphService→CodeIndexService→BizModel exportGraph() 调用链使用，属于 GraphQL 接口可达的公共 API。使用裸 IllegalArgumentException 而非 ErrorCode 模式。
- **风险**: 无法通过 ErrorCode 机制提供结构化错误信息，与同文件 55 行 NopException(ERR_GRAPH_EXPORT_FAILED) 不一致。
- **建议**: 改为 `throw new NopException(ERR_GRAPH_UNSUPPORTED_FORMAT).param("format", format);`
- **信心水平**: 95%
- **误报排除**: 已确认调用链可达 GraphQL 公共 API。
- **复核状态**: 未复核

### [维度09-02] ErrorCode 描述消息中英文混用

- **文件**: `nop-code-service/.../NopCodeErrors.java` (行 15,18,21,24,27,30), `nop-code-core/.../NopCodeCoreErrors.java` (行 12,15)
- **证据片段**:
  ```java
  // 中文描述
  public static final ErrorCode ERR_INDEX_DIRECTORY_FAILED = ErrorCode.define("nop.err.code.index-directory-failed", "索引目录失败: {path}", "path");
  public static final ErrorCode ERR_NO_ANALYZER_FOR_FILE = ErrorCode.define("nop.err.code.no-analyzer-for-file", "没有为文件注册分析器: {filePath}", "filePath");
  // 英文描述（同文件）
  public static final ErrorCode ERR_CODE_INVALID_PATH = ErrorCode.define("nop.err.code.invalid-path", "Invalid path: {path}", "path");
  ```
- **严重程度**: P3
- **现状**: 同一套错误码定义中 8 个使用中文描述、7 个使用英文描述，风格不统一。
- **风险**: 按 Nop 平台规范 ErrorCode 描述应使用英文。
- **建议**: 将所有中文描述改为英文。
- **信心水平**: 95%
- **误报排除**: ErrorCode.define() 的描述参数通常作为开发参考和 fallback 消息。
- **复核状态**: 未复核

### [维度09-03] ERR_INCREMENTAL_FAILED 缺少 indexId 上下文参数

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:717`
- **证据片段**:
  ```java
  throw new NopException(ERR_INCREMENTAL_FAILED).cause(e);
  ```
- **严重程度**: P3
- **现状**: 错误码未附带 indexId 参数，运维排查时无法确定哪个索引失败。
- **建议**: 添加 `.param(ARG_INDEX_ID, indexId)`。
- **信心水平**: 90%
- **误报排除**: 可通过日志上下文推断，但 ErrorCode 应自包含。
- **复核状态**: 未复核

### 值得肯定的做法

1. 异常链在所有需要的位置都正确保留（.cause(e)）
2. 所有 catch 块都有合理处理（日志+降级/重抛），无静默吞掉
3. 无 System.out/System.err 使用
4. 单文件分析失败采用合理容错策略
5. InterruptedException 正确恢复中断标志
