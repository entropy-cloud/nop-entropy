# 维度 09：错误处理与错误码

**审计日期**: 2026-05-29
**审计范围**: nop-code 全模块的 throw 语句、ErrorCode 定义、异常处理

---

## 第 1 轮（初审）

### [维度09-01] ErrorCode 描述字符串中大量硬编码中文（8处）

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/NopCodeErrors.java:14-30`; `nop-code/nop-code-core/src/main/java/io/nop/code/core/NopCodeCoreErrors.java:11-15`
- **证据片段**:
  ```java
  ErrorCode ERR_INDEX_DIRECTORY_FAILED =
      define("nop.err.code.index-directory-failed", "索引目录失败: {path}", ARG_PATH);
  ErrorCode ERR_NO_ANALYZER_FOR_FILE =
      define("nop.err.code.no-analyzer-for-file", "没有为文件注册分析器: {filePath}", ARG_FILE_PATH);
  ErrorCode ERR_INCREMENTAL_NOT_SUPPORTED =
      define("nop.err.code.incremental-not-supported", "增量索引需要CodeIndexService实现");
  ErrorCode ERR_CODE_DIGEST_NOT_AVAILABLE =
      define("nop.err.code.digest-not-available", "SHA-256摘要算法不可用");
  ```
  同文件中较新的 ErrorCode（L33-45）已使用英文，中英混杂。
- **严重程度**: P2
- **现状**: 8 个 ErrorCode 使用中文描述。项目规范要求 "Error messages must be in English"。
- **风险**: 中文消息直接出现在 API 响应和日志中，对非中文开发者不可读；无对应 i18n 资源时中文泄漏到前端。
- **建议**: 替换为英文描述。
- **信心水平**: 高
- **误报排除**: 明确违反项目规范。
- **复核状态**: 未复核

### [维度09-02] GraphExporter 公共方法抛出 IllegalArgumentException

- **文件**: `nop-code/nop-code-graph/src/main/java/io/nop/code/graph/export/GraphExporter.java:27-36`
- **证据片段**:
  ```java
  public String export(CallGraph callGraph, SymbolTable symbolTable, String format, ...) {
      switch (format.toUpperCase()) {
          case "GRAPHML": ...
          case "MERMAID": ...
          case "JSON": ...
          default:
              throw new IllegalArgumentException("Unsupported format: " + format);
      }
  }
  ```
- **严重程度**: P2
- **现状**: 公共入口方法对不支持的格式抛裸 IllegalArgumentException，不携带 ErrorCode。
- **风险**: 无法被框架统一异常处理管线正确序列化，调用方无法通过 getErrorCode() 做程序化判断。
- **建议**: 定义 ERR_GRAPH_UNSUPPORTED_FORMAT ErrorCode，使用 NopException。
- **信心水平**: 高
- **误报排除**: 公共 API 不应抛裸 IllegalArgumentException。
- **复核状态**: 未复核

### [维度09-03] 多处 NopException throw 缺少 .param() 上下文参数（7处）

- **文件**: `CodeIndexService.java:2474,2527,2542,2557,1878`; `NopCodeSymbolBizModel.java:102,117`
- **证据片段**:
  ```java
  // CodeIndexService.java L2472-2474
  if (detector == null) {
      throw new NopException(ERR_CODE_FLOW_DETECTOR_NOT_AVAILABLE);  // indexId 可用但未传
  }
  // CodeIndexService.java L1877-1878
  throw new NopException(ERR_INCREMENTAL_FAILED).cause(e);  // indexId 可用但未传
  ```
- **严重程度**: P2
- **现状**: 7 处 throw 有可用的上下文变量但未通过 .param() 传递。其中 ERR_CODE_INDEX_ID_REQUIRED 声明了 ARG_INDEX_ID 但 throw 时未传。
- **风险**: 异常发生时缺少关键诊断信息（indexId、filePath 等），增加定位成本。
- **建议**: 每处 throw 追加 .param(ARG_INDEX_ID, indexId) 等上下文参数。
- **信心水平**: 高
- **误报排除**: 项目规范要求公共 API 使用 .param() 传递上下文。
- **复核状态**: 未复核

### [维度09-04] GraphExporter 局部定义 ErrorCode 未集中到模块级 Errors 类

- **文件**: `nop-code/nop-code-graph/src/main/java/io/nop/code/graph/export/GraphExporter.java:21-22`
- **证据片段**:
  ```java
  private static final ErrorCode ERR_GRAPH_EXPORT_FAILED =
          ErrorCode.define("nop.err.code.graph-export-failed", "Graph export failed");
  ```
- **严重程度**: P3
- **现状**: ErrorCode 定义在类内部，其他模块集中在 NopCodeCoreErrors/NopCodeErrors 接口中。
- **风险**: 分散定义难以全局检索和维护。
- **建议**: 在 io.nop.code.graph 包下创建 NopCodeGraphErrors.java 接口。
- **信心水平**: 中
- **误报排除**: 集中管理优于分散是项目约定。
- **复核状态**: 未复核

### [维度09-05] DigestHelper 使用 2-arg 构造器与 .cause() 模式不一致

- **文件**: `nop-code/nop-code-core/src/main/java/io/nop/code/core/util/DigestHelper.java:24,44`
- **证据片段**:
  ```java
  throw new NopException(ERR_CODE_DIGEST_NOT_AVAILABLE, e);   // 2-arg constructor
  ```
  对比：
  ```java
  throw new NopException(ERR_CODE_ANALYZE_PROJECT_FAILED).cause(e);  // .cause() 链式调用
  ```
- **严重程度**: P3
- **现状**: 仅此处使用 2-arg 构造器，其余全部使用 .cause() 链式调用。
- **风险**: 风格不一致。
- **建议**: 统一为 .cause() 链式调用。
- **信心水平**: 高
- **误报排除**: 功能正确，仅风格问题。
- **复核状态**: 未复核

### [维度09-06] ManifestStore 静默吞掉解析异常返回空列表，导致隐性全量重分析

- **文件**: `nop-code/nop-code-core/src/main/java/io/nop/code/core/incremental/ManifestStore.java:36-44`
- **证据片段**:
  ```java
  try {
      List<FileFingerprint> result = JsonTool.parseBeanFromText(content, ...);
      return result != null ? result : new ArrayList<>();
  } catch (Exception e) {
      LOG.warn("Failed to parse manifest file: {}", manifestFile, e);
      return new ArrayList<>();
  }
  ```
- **严重程度**: P2
- **现状**: manifest 文件内容损坏时，catch 吞掉异常返回空列表，调用者会认为所有文件都是新增的，触发全量分析。
- **风险**: 数据损坏被静默掩盖；全量重分析可能非常耗时；无指标暴露降级行为。
- **建议**: 改为 LOG.error 或抛出 IOException（方法签名已声明 throws IOException）。
- **信心水平**: 高
- **误报排除**: 返回空列表导致静默降级为全量分析且无指标暴露，属于隐性退化。
- **复核状态**: 未复核

### [维度09-07] ProjectAnalyzer 废弃方法抛 UnsupportedOperationException

- **文件**: `nop-code/nop-code-core/src/main/java/io/nop/code/core/analyzer/ProjectAnalyzer.java:477-481`
- **证据片段**:
  ```java
  public ProjectAnalysisResult analyzeIncremental(Path projectRoot, List<String> changedFilePaths) {
      throw new UnsupportedOperationException("Use analyzeIncremental(Path, ProjectAnalysisResult)...");
  }
  ```
- **严重程度**: P3
- **现状**: 废弃方法抛裸 UnsupportedOperationException，不携带 ErrorCode。
- **风险**: 通过接口引用调用时得到裸 Java 标准异常。
- **建议**: 使用 NopException + ErrorCode。
- **信心水平**: 中
- **误报排除**: 方法仍为接口契约的一部分。
- **复核状态**: 未复核

### [维度09-08] NopCodeSymbolBizModel GraphQL 加载器缺少 .param()（与09-03同类，2处）

- **文件**: `NopCodeSymbolBizModel.java:101-102,116-117`
- **证据片段**:
  ```java
  if (indexId == null)
      throw new NopException(NopCodeErrors.ERR_CODE_INDEX_ID_REQUIRED);  // 缺少 .param
  ```
  ERR_CODE_INDEX_ID_REQUIRED 声明了 ARG_INDEX_ID 但 throw 时未传。
- **严重程度**: P2
- **现状**: 2 处 GraphQL 接口层 throw 缺少 .param()，ErrorCode 声明了参数。
- **风险**: GraphQL 错误响应缺少 indexId 参数详情。
- **建议**: 追加 .param(ARG_INDEX_ID, indexId)。
- **信心水平**: 高
- **误报排除**: 公共 API 层必须传递参数上下文。
- **复核状态**: 未复核
