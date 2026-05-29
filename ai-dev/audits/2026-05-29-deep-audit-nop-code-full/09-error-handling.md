# 维度09：错误处理与错误码

## 第 1 轮（初审）

### [维度09-01] NopCodeErrors 中 6 个 ErrorCode 使用中文消息字符串

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/NopCodeErrors.java`
- **行号**: L14-30
- **证据片段**:
  ```java
  ErrorCode ERR_INDEX_DIRECTORY_FAILED = define("nop.err.code.index-directory-failed",
      "索引目录失败: {path}", ARG_PATH);
  ErrorCode ERR_NO_ANALYZER_FOR_FILE = define("nop.err.code.no-analyzer-for-file",
      "没有为文件注册分析器: {filePath}", ARG_FILE_PATH);
  ```
- **严重程度**: P2
- **现状**: NopCodeErrors 中 10 个 ErrorCode 有 6 个使用中文描述消息。AGENTS.md 明确要求错误消息必须使用英文。
- **风险**: 违反项目规范，中文错误消息影响国际化场景和跨团队协作。
- **建议**: 将所有中文错误消息替换为英文。
- **信心水平**: 确定
- **误报排除**: 明确的规范违反。
- **复核状态**: 未复核

### [维度09-02] GraphExporter.export() 使用 IllegalArgumentException 而非 NopException

- **文件**: `nop-code/nop-code-graph/src/main/java/io/nop/code/graph/export/GraphExporter.java`
- **行号**: L34-35
- **证据片段**:
  ```java
  default:
      throw new IllegalArgumentException("Unsupported format: " + format);
  ```
- **严重程度**: P2
- **现状**: 同类中另一处（L55）已正确使用 NopException(ERR_GRAPH_EXPORT_FAILED).cause(e)。此处使用裸 IllegalArgumentException 不一致。
- **风险**: 不一致的错误处理模式。
- **建议**: 定义 ERR_GRAPH_UNSUPPORTED_FORMAT ErrorCode 并替换 IllegalArgumentException。
- **信心水平**: 确定
- **误报排除**: 同类中已存在 NopException 使用先例。
- **复核状态**: 未复核

## 通过项

1. 模块级错误码类存在（NopCodeCoreErrors + NopCodeErrors）
2. ErrorCode 命名遵循 nop.err.code.{描述} 格式
3. .param() 上下文传递正确
4. 异常链通过 .cause(e) 正确保留
5. 无裸 RuntimeException/NullPointerException
6. 无 System.out/System.err
7. 日志统一使用 SLF4J
