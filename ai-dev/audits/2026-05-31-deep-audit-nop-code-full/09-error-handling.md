# 维度09：错误处理与错误码

**模块**: nop-code
**审计日期**: 2026-05-31

---

## 第 1 轮（初审）

### [维度09-01] NopCodeErrors 中 ErrorCode 消息混用中英文

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/NopCodeErrors.java:13-16`
- **证据片段**:
  ```java
  ErrorCode ERR_NO_ANALYZER_FOR_FILE =
          define("nop.err.code.no-analyzer-for-file", "没有为文件注册分析器: {filePath}", ARG_FILE_PATH);
  ErrorCode ERR_INCREMENTAL_FAILED =
          define("nop.err.code.incremental-failed", "增量索引失败");
  ```
- **严重程度**: P2
- **现状**: 7 个 ErrorCode 中 2 个使用中文描述消息，5 个使用英文。
- **风险**: 面向开发者的错误消息在国际化环境中降低可诊断性。
- **建议**: 将中文消息替换为英文。
- **信心水平**: 95%
- **误报排除**: 已确认是手写代码，非生成产物。
- **复核状态**: 未复核

---

### [维度09-02] NopCodeCoreErrors 同样混用中文错误消息

- **文件**: `nop-code/nop-code-core/src/main/java/io/nop/code/core/NopCodeCoreErrors.java:10-13`
- **证据片段**:
  ```java
  ErrorCode ERR_CODE_ANALYZE_PROJECT_FAILED =
          define("nop.err.code.analyze-project-failed", "项目分析失败:{path}", ARG_PATH);
  ErrorCode ERR_CODE_DIGEST_NOT_AVAILABLE =
          define("nop.err.code.digest-not-available", "SHA-256摘要算法不可用");
  ```
- **严重程度**: P2
- **现状**: 3 个 ErrorCode 中 2 个使用中文。
- **风险**: 同 09-01。
- **建议**: 替换为英文。
- **信心水平**: 95%
- **误报排除**: 已确认是手写代码。
- **复核状态**: 未复核

---

### [维度09-03] GraphExporter 将 ErrorCode 定义为类私有字段

- **文件**: `nop-code/nop-code-graph/src/main/java/io/nop/code/graph/export/GraphExporter.java:21-22`
- **证据片段**:
  ```java
  private static final ErrorCode ERR_GRAPH_EXPORT_FAILED =
          ErrorCode.define("nop.err.code.graph-export-failed", "Graph export failed");
  ```
- **严重程度**: P3
- **现状**: 分散的 ErrorCode 定义降低可发现性。
- **建议**: 低优先级，可合并到 NopCodeCoreErrors。
- **信心水平**: 70%
- **误报排除**: graph 模块仅此一个 throw 点，影响有限。
- **复核状态**: 未复核

---

### [维度09-04] DigestHelper 异常链传递方式不一致

- **文件**: `nop-code/nop-code-core/src/main/java/io/nop/code/core/util/DigestHelper.java:22,42`
- **证据片段**:
  ```java
  throw new NopException(ERR_CODE_DIGEST_NOT_AVAILABLE, e);
  // vs 模块其余代码统一使用:
  throw new NopException(errorCode).cause(e);
  ```
- **严重程度**: P3
- **现状**: 两参数构造器 vs 链式调用风格混用。
- **建议**: 统一为 `.cause(e)` 链式风格。
- **信心水平**: 90%
- **误报排除**: 功能正确，仅风格不一致。
- **复核状态**: 未复核

---

### [维度09-05] FlowDetector 静默吞掉 JSON 解析异常（无日志）

- **文件**: `nop-code/nop-code-flow/src/main/java/io/nop/code/flow/FlowDetector.java:525-527`
- **证据片段**:
  ```java
  } catch (Exception e) {
      // fall through
  }
  ```
- **严重程度**: P2
- **现状**: 解析 extData JSON 时捕获异常后无任何日志记录。同模块 DeadCodeDetector 同位置有 LOG.warn。
- **风险**: extData 格式异常时无法诊断为什么文件路径解析失败。
- **建议**: 添加 `LOG.debug("Failed to parse extData", e)`。
- **信心水平**: 85%
- **误报排除**: 已对比 DeadCodeDetector 和 ImpactAnalyzer 同位置使用 LOG.warn。
- **复核状态**: 未复核

---

### [维度09-06] ChangeAnalyzer.parseGitDiff 异常时返回空结果而非抛出

- **文件**: `nop-code/nop-code-flow/src/main/java/io/nop/code/flow/ChangeAnalyzer.java:198-204`
- **证据片段**:
  ```java
  } catch (IOException e) {
      LOG.warn("Failed to parse git diff output", e);
      return result;
  }
  ```
- **严重程度**: P2
- **现状**: git 进程执行失败时返回可能部分填充或空的 result，调用者无法区分"无变更"和"执行失败"。
- **风险**: 产出不准确的变更影响报告。
- **建议**: 在 result 中增加 partial 标记，或包装为 NopException 向上传播。
- **信心水平**: 70%
- **误报排除**: "尽量继续分析"可能是设计意图，但不区分成功/失败是结构性问题。
- **复核状态**: 未复核

---

### [维度09-07] CodeIndexService 增量索引异常重新包装丢失上下文

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:652-655`
- **证据片段**:
  ```java
  } catch (NopException e) {
      if (e.getErrorCode() != null && e.getErrorCode().equals(ERR_INCREMENTAL_FAILED))
          throw e;
      throw new NopException(ERR_INCREMENTAL_FAILED).cause(e);
  }
  ```
- **严重程度**: P2
- **现状**: 包装新异常时未传 `.param()` 附加 indexId 上下文，新异常堆栈指向此处而非原始 throw 点。
- **风险**: 调试时需展开 cause chain，缺少 indexId 无法定位失败索引。
- **建议**: 添加 `.param(ARG_INDEX_ID, indexId)`。
- **信心水平**: 80%
- **误报排除**: `.cause(e)` 保留了异常链，但缺少上下文参数是实质问题。
- **复核状态**: 未复核

---

### [维度09-08] "service not available" 类 ErrorCode 未传递上下文参数

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:1305,1358,1373,1388`
- **证据片段**:
  ```java
  throw new NopException(ERR_CODE_FLOW_DETECTOR_NOT_AVAILABLE);          // line 1305
  throw new NopException(ERR_CODE_CHANGE_ANALYZER_NOT_AVAILABLE);        // line 1373
  ```
- **严重程度**: P2
- **现状**: 4 个 "not available" 异常未传递 `.param()` 附加上下文。
- **风险**: 多个 indexId 并发时无法区分触发源。
- **建议**: 添加 `.param(ARG_INDEX_ID, indexId)`。
- **信心水平**: 85%
- **误报排除**: ErrorCode 定义中不需要参数占位符也能传 param，仅影响运行时错误详情。
- **复核状态**: 未复核
