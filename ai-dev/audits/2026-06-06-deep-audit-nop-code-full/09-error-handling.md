# 维度 09：错误处理与错误码

## 第 1 轮（初审）

### [维度09-01] 缺少模块级异常类

- **文件**: nop-code 模块整体
- **严重程度**: P3
- **现状**: nop-code 模块不存在任何 `NopCodeException` 或类似命名的模块级异常类。所有 throw 语句直接使用 `new NopException(ErrorCode)`。当前代码全部使用 ErrorCode 模式（比规范要求的更严格），实际运行没有问题。
- **风险**: 模块未来扩展时，内部实现如果需要使用英文字符串消息，无法使用类型化的模块异常类，可能回退到 `throw new RuntimeException(...)`。
- **建议**: 创建 `NopCodeException extends NopException`，同时提供 `(String)` 和 `(ErrorCode)` 双构造器。
- **信心水平**: 很可能
- **误报排除**: 当前代码比规范更严格（全部用 ErrorCode），不是真正的运行时问题。但作为预防性措施，避免未来回退到反模式。
- **复核状态**: 未复核

### [维度09-02] 使用 UnsupportedOperationException 绕过框架异常体系

- **文件**:
  - `nop-code-core/src/main/java/io/nop/code/core/analyzer/ProjectAnalyzer.java:387`
  - `nop-code-core/src/main/java/io/nop/code/core/incremental/DeletedResourceStub.java:94,99,114,119,124`
- **证据片段**:
  ```java
  // ProjectAnalyzer.java:387
  throw new UnsupportedOperationException("Use analyzeIncremental(String, ProjectAnalysisResult) instead");
  
  // DeletedResourceStub.java:94
  throw new UnsupportedOperationException("DeletedResourceStub does not support getInputStream");
  ```
- **严重程度**: P3
- **现状**: `ProjectAnalyzer` 抛出 `UnsupportedOperationException` 标记废弃方法；`DeletedResourceStub`（包私有内部桩类）的 5 个方法抛出此异常。
- **风险**: 这些异常绕过框架异常体系，上层无法统一处理。但影响范围有限：`DeletedResourceStub` 是包私有类，`ProjectAnalyzer` 的方法是接口方法的废弃实现。
- **建议**: 考虑在 `NopCodeCoreErrors` 中添加 `ERR_CODE_UNSUPPORTED_OPERATION` 错误码替换。优先级很低。
- **信心水平**: 确定
- **误报排除**: `UnsupportedOperationException` 在"不支持的操作"场景是 Java 标准用法，但 Nop 平台要求统一异常体系。这是局部问题，不是系统性反模式。
- **复核状态**: 未复核

### [维度09-03] 静默吞掉异常（空 catch 块）

- **文件**:
  - `nop-code-graph/src/main/java/io/nop/code/graph/community/CommunityDetector.java:507-509, 694-696`
  - `nop-code-graph/src/main/java/io/nop/code/graph/critical/CriticalNodeAnalyzer.java:86-88`
- **证据片段**:
  ```java
  // CommunityDetector.java:507-509
  } catch (IllegalArgumentException e) {
      // JGraphT addEdge throws on duplicate edges, safe to ignore
  }
  ```
  对比 GraphExporter.java:202-203 中的正确做法：
  ```java
  } catch (IllegalArgumentException e) {
      LOG.debug("Skipping duplicate edge {} -> {}", source, target);
  }
  ```
- **严重程度**: P3
- **现状**: 三处空 catch 块捕获 `IllegalArgumentException` 后不做任何处理。同类场景在 `GraphExporter` 中使用 `LOG.debug()` 记录。
- **风险**: 如果 JGraphT 未来行为变化，可能导致问题被隐藏。
- **建议**: 与 `GraphExporter` 保持一致，添加 `LOG.debug()` 记录。
- **信心水平**: 确定
- **误报排除**: 这不是"看起来不优雅"的问题。同文件中的同类场景使用了日志记录，三处遗漏是不一致的。
- **复核状态**: 未复核

### 正面合规项

- ErrorCode 定义完整（NopCodeCoreErrors 4 个 + NopCodeErrors 7 个）
- 公共 API 层全部使用 ErrorCode 模式
- .param() 上下文传递正确
- 异常链保留正确（.cause(e)）
- 无 RuntimeException/IllegalArgumentException 抛出
- 无中文错误消息
- 日志级别规范

## 最终保留项

| 编号 | 严重程度 | 文件路径 | 一句话摘要 |
|------|---------|---------|-----------|
| [维度09-01] | P3 | nop-code 整体 | 缺少模块级异常类 NopCodeException |
| [维度09-02] | P3 | ProjectAnalyzer.java, DeletedResourceStub.java | 使用 UnsupportedOperationException 绕过框架异常体系 |
| [维度09-03] | P3 | CommunityDetector.java, CriticalNodeAnalyzer.java | 空 catch 块未记录日志 |
