# 维度 09：错误处理与错误码 — nop-code 模块审计报告

## 第 1 轮（初审）

### [维度09-01] NopCodeException 未被实际使用——死代码

- **文件**: `nop-code-service/src/main/java/io/nop/code/service/NopCodeException.java`（全文14行）
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
- **严重程度**: P3
- **现状**: 定义了模块级异常类，但整个 nop-code 模块中无一处引用它（0 import、0 throw）。
- **风险**: 无运行时风险，但违反两档策略的设计意图。
- **建议**: 在 service 模块的 throw 语句中统一替换为 NopCodeException，或删除死代码。
- **信心水平**: 95%
- **误报排除**: 搜索确认零引用。
- **复核状态**: 未复核

### [维度09-02] NopCodeException 缺少 (String) 构造器

- **文件**: `nop-code-service/src/main/java/io/nop/code/service/NopCodeException.java`（全文14行）
- **证据片段**:
  ```java
  // 只有 ErrorCode 构造器，缺少 (String message) 和 (String message, Throwable cause)
  ```
- **严重程度**: P3
- **现状**: 按两档策略，模块内部可使用字符串消息，但 NopCodeException 没有提供 String 构造器。
- **风险**: 当前因类未使用无影响。若要启用则需补充。
- **建议**: 若保留 NopCodeException，添加 (String) 构造器。
- **信心水平**: 90%
- **误报排除**: 可与09-01合并处理。
- **复核状态**: 未复核

### [维度09-03] GraphExporter.export() 的 ErrorCode 参数传递不一致

- **文件**: `nop-code-graph/src/main/java/io/nop/code/graph/export/GraphExporter.java:36,56`
- **证据片段**:
  ```java
  // 行36: 有 .param("format", format)
  throw new NopException(NopCodeCoreErrors.ERR_GRAPH_EXPORT_FAILED).param("format", format);
  
  // 行56: 无 .param()，丢失了上下文
  throw new NopException(NopCodeCoreErrors.ERR_GRAPH_EXPORT_FAILED).cause(e);
  ```
- **严重程度**: P2
- **现状**: 同一错误码两处使用，一处有 format 参数一处没有。GraphML 导出失败时错误消息缺少 format 信息。
- **风险**: 排查导出失败时缺少上下文信息。
- **建议**: 在行56补充 `.param("format", "GRAPHML")`。
- **信心水平**: 90%
- **误报排除**: 同一错误码在两处使用，参数不一致是确定事实。
- **复核状态**: 未复核

### [维度09-04] JavaFileAnalyzer 静默吞掉 JSON 解析异常——无任何日志

- **文件**: `nop-code-lang-java/src/main/java/io/nop/code/lang/java/analyzer/JavaFileAnalyzer.java:217-219`
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
- **现状**: 解析 extData JSON 失败时完全静默吞掉异常，没有日志记录。同文件中其他 catch 块都有 LOG.debug/warn。
- **风险**: extData 格式损坏时静默丢弃已有数据，无法追踪原因。
- **建议**: 添加 `LOG.debug("Failed to parse existing extData for symbol", e)`。
- **信心水平**: 95%
- **误报排除**: 同文件中其他两个 catch 块都有日志记录，此处是明确的遗漏。
- **复核状态**: 未复核

### [维度09-05] ChangeAnalyzer 捕获 IOException 后返回不完整结果

- **文件**: `nop-code-flow/src/main/java/io/nop/code/flow/ChangeAnalyzer.java:199-205`
- **证据片段**:
  ```java
  } catch (IOException e) {
      LOG.warn("Failed to parse git diff output", e);
      return result;    // 返回已收集的部分结果
  }
  ```
- **严重程度**: P3
- **现状**: git diff 失败时 LOG.warn 后返回部分结果。调用方无法区分"成功但结果较少"和"因异常而提前终止"。
- **风险**: 不完整的变更分析结果被当作正常结果使用，可能遗漏关键变更。
- **建议**: 在返回的 ChangeAnalysisResult 中增加 partial/truncated 标记，或将 IOException 包装向上抛出。
- **信心水平**: 70%
- **误报排除**: LOG.warn 已记录异常，部分结果返回可能是降级设计意图。
- **复核状态**: 未复核

## 合规确认项

- ✅ 日志使用规范：全模块 SLF4J，0 处 System.out/err
- ✅ 错误码使用规范：公共 API 正确使用 ErrorCode + .param()
- ✅ 异常链保留正确：3处 re-throw 均传入 cause
- ✅ 无中文硬编码错误消息
