# 维度 09：错误处理与错误码 — nop-code 模块

## 第 1 轮（初审）

### [维度09-01] 模块缺少模块级异常类 NopCodeException

- **文件**: 整个 nop-code 模块
- **证据片段**: nop-code 没有定义 NopCodeException。所有 throw 直接使用 NopException。
- **严重程度**: P2
- **现状**: 无法使用简洁的英文字符串消息（模式二），所有内部实现也只能使用 ErrorCode。
- **风险**: 增加了内部代码的错误码维护负担。
- **建议**: 创建 NopCodeException extends NopException，同时提供 (String) 和 (ErrorCode) 构造器。
- **信心水平**: 确定
- **误报排除**: error-handling.md 明确要求每个模块定义异常类。
- **复核状态**: 未复核

### [维度09-02] 4 条 ErrorCode 消息使用中文

- **文件**: `nop-code-service/.../NopCodeErrors.java:13,16`, `nop-code-core/.../NopCodeCoreErrors.java:10,13`
- **证据片段**:
  ```java
  ErrorCode.define("nop.err.code.no-analyzer-for-file", "没有为文件注册分析器: {filePath}");
  ErrorCode.define("nop.err.code.incremental-failed", "增量索引失败");
  ErrorCode.define("nop.err.code.project-analysis-failed", "项目分析失败:{path}");
  ErrorCode.define("nop.err.code.sha256-not-available", "SHA-256摘要算法不可用");
  ```
- **严重程度**: P2
- **现状**: 4 条 ErrorCode 使用中文消息，同文件中其他 ErrorCode 使用英文。
- **风险**: 违反 error-handling.md "错误消息统一使用英文" 规范。
- **建议**: 统一改为英文。
- **信心水平**: 确定
- **误报排除**: 规范明确要求英文。
- **复核状态**: 未复核

### [维度09-03] GraphExporter 内联定义 ErrorCode，未放入统一错误码文件

- **文件**: `nop-code-graph/.../export/GraphExporter.java:21-22`
- **证据片段**:
  ```java
  private static final ErrorCode ERR_GRAPH_EXPORT_FAILED =
          ErrorCode.define("nop.err.code.graph-export-failed", "Graph export failed");
  ```
- **严重程度**: P3
- **现状**: ErrorCode 定义散落在实现类的私有字段中。
- **风险**: 不利于错误码统一管理和去重。
- **建议**: 移入 NopCodeErrors.java。
- **信心水平**: 确定
- **误报排除**: 当前只有一个内联定义，风险有限。
- **复核状态**: 未复核

### [维度09-04] 3 条 ErrorCode 抛出时缺少 .param() 上下文

- **文件**: `nop-code-service/.../impl/CodeIndexService.java:1309,1362,1377,1392`
- **证据片段**: ERR_CODE_FLOW_DETECTOR_NOT_AVAILABLE 等抛出时缺少 indexId 参数。
- **严重程度**: P3
- **现状**: "not available" 类型错误缺少 indexId，排查和前端展示无法定位具体索引。
- **建议**: 补充 .param(ARG_INDEX_ID, indexId)。
- **信心水平**: 确定
- **误报排除**: 规范要求 .param() 附带上下文参数。
- **复核状态**: 未复核

### [维度09-05] ChangeAnalyzer 和 JavaFileAnalyzer 中存在静默吞异常

- **文件**: `nop-code-flow/.../ChangeAnalyzer.java:243`, `nop-code-lang-java/.../JavaFileAnalyzer.java:212`
- **证据片段**:
  ```java
  } catch (Exception e) {
      // fall through
  }
  ```
- **严重程度**: P3
- **现状**: extData JSON 解析失败时完全静默，无 LOG.debug。
- **风险**: 与 FlowDetector.java:526 的同类处理不一致（后者有 LOG.debug）。
- **建议**: 至少添加 LOG.debug 级别日志。
- **信心水平**: 确定
- **误报排除**: 同模块 FlowDetector 已有正确模式作为对比。
- **复核状态**: 未复核

## 合规确认

- 无 RuntimeException/IllegalArgumentException 反模式: PASS
- 异常链保留总体良好: PASS
- 全部使用 SLF4J，无 System.out/err: PASS
