# 维度 09：错误处理与错误码

## 第 1 轮（初审）

### [维度09-01] NopCodeException 已定义但从未被使用（死代码）

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/NopCodeException.java:1-14`
- **证据片段**:
  ```java
  public class NopCodeException extends NopException {
      public NopCodeException(ErrorCode errorCode) {
          super(errorCode);
      }
      public NopCodeException(ErrorCode errorCode, Throwable cause) {
          super(errorCode, cause);
      }
  }
  ```
  但所有 throw 语句均使用 `new NopException(ERR_XXX)`，从未使用 NopCodeException。
- **严重程度**: P2
- **现状**: 模块级异常类已定义但全模块零引用。所有代码直接使用基类 NopException。
- **风险**: 死代码增加维护成本。模块级异常类机制失效。
- **建议**: 将 throw new NopException(...) 替换为 NopCodeException，或删除该类。
- **信心水平**: 确定
- **误报排除**: grep 确认全模块零引用。
- **复核状态**: 未复核

### [维度09-02] JavaFileAnalyzer 静默吞掉 JSON 解析异常，无任何日志

- **文件**: `nop-code/nop-code-lang-java/src/main/java/io/nop/code/lang/java/analyzer/JavaFileAnalyzer.java:207-214`
- **证据片段**:
  ```java
  try {
      Map<String, Object> parsed = JsonTool.parseMap(existingExtData);
      if (parsed != null) {
          extMap.putAll(parsed);
      }
  } catch (Exception e) {
      // ignore parse failure
  }
  ```
- **严重程度**: P2
- **现状**: 解析 extData JSON 时异常被静默吞掉，仅有一行注释，无日志输出。
- **风险**: extData 被损坏时无法通过日志排查。
- **建议**: 添加 LOG.debug 记录。
- **信心水平**: 确定
- **误报排除**: 不是空 catch 块，有注释说明意图，但同模块其他 catch 块均有 LOG 语句。
- **复核状态**: 未复核

### [维度09-03] ProjectAnalyzer 使用原始 UnsupportedOperationException

- **文件**: `nop-code/nop-code-core/src/main/java/io/nop/code/core/analyzer/ProjectAnalyzer.java:490-494`
- **证据片段**:
  ```java
  throw new UnsupportedOperationException(
          "Use analyzeIncremental(Path, ProjectAnalysisResult) or analyzeIncremental(Path, Path) instead");
  ```
- **严重程度**: P3
- **现状**: 接口方法占位实现使用 UnsupportedOperationException。
- **风险**: 极低。标准 JDK 模式用于"此重载已废弃"。
- **建议**: 保持现状即可。
- **信心水平**: 确定
- **误报排除**: 非业务错误场景。
- **复核状态**: 未复核

### [维度09-04] DeletedResourceStub 5 处 UnsupportedOperationException

- **文件**: `nop-code/nop-code-core/src/main/java/io/nop/code/core/incremental/DeletedResourceStub.java:94,99,114,119,124`
- **严重程度**: P3
- **现状**: 包私有 stub 类对不支持操作抛 UnsupportedOperationException。
- **风险**: 极低。内部 stub 模式。
- **建议**: 保持现状。
- **信心水平**: 确定
- **误报排除**: 内部 stub 类。
- **复核状态**: 未复核

### [维度09-05] CodeIndexService.saveReplacingExisting 属性更新失败以 TRACE 级别记录

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:1244-1248`
- **严重程度**: P3
- **现状**: 单个属性赋值失败以 LOG.trace 记录，生产环境默认不输出。
- **建议**: 提升至 LOG.debug。
- **信心水平**: 中
- **误报排除**: ORM save-or-update 竞争场景，跳过只读/计算属性是合理的。
- **复核状态**: 未复核

### [维度09-06] CommunityDetector/CriticalNodeAnalyzer 静默捕获 IllegalArgumentException

- **文件**: `CommunityDetector.java:496-498`, `CriticalNodeAnalyzer.java:81-83`, `GraphExporter.java:196-198`
- **严重程度**: P3
- **现状**: JGraphT addEdge 重复边异常被静默捕获，部分无日志、部分有 debug 日志，不一致。
- **建议**: 统一为 LOG.debug 级别。
- **信心水平**: 确定
- **误报排除**: JGraphT API 行为，catch 是正确处理。
- **复核状态**: 未复核

## 正面发现

1. ErrorCode 常量集中定义（NopCodeErrors 6个 + NopCodeCoreErrors 4个）
2. 公共 API 层全部使用 NopException + ErrorCode + .param() + .cause()
3. 异常链保留完整
4. 无硬编码中文错误消息
5. 无 bare RuntimeException/IllegalArgumentException 的 throw
6. 无空 catch 块
