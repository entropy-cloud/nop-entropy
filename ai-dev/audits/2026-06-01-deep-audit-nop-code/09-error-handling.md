# 维度 09：错误处理与错误码

## 第 1 轮（初审）

**检查范围**：49 个手写源文件（排除 _gen/、_*.java、测试代码），覆盖 nop-code-core、nop-code-service、nop-code-graph、nop-code-flow、nop-code-lang-java、nop-code-lang-typescript。

### [维度09-01] NopCodeErrors 中 2 个 ErrorCode 使用中文默认描述

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/NopCodeErrors.java:12-16`
- **证据片段**:
  ```java
  ErrorCode ERR_NO_ANALYZER_FOR_FILE =
          define("nop.err.code.no-analyzer-for-file", "没有为文件注册分析器: {filePath}", ARG_FILE_PATH);

  ErrorCode ERR_INCREMENTAL_FAILED =
          define("nop.err.code.incremental-failed", "增量索引失败");
  ```
- **严重程度**: P3
- **现状**: `NopCodeErrors` 中有 2 个 ErrorCode 的默认描述字符串为中文。同文件中其余 4 个 ErrorCode 均使用英文描述。
- **风险**: ErrorCode 默认描述会通过 GraphQL API 响应返回。中文消息在国际化场景下不利于前端展示和日志检索。
- **建议**: 统一改为英文默认描述，中文翻译放到 i18n 资源文件。
- **信心水平**: 确定
- **误报排除**: ErrorCode 默认描述会作为异常消息传播到 API 调用方，不是注释中的中文。
- **复核状态**: 未复核

### [维度09-02] NopCodeCoreErrors 中 2 个 ErrorCode 使用中文默认描述

- **文件**: `nop-code/nop-code-core/src/main/java/io/nop/code/core/NopCodeCoreErrors.java:9-13`
- **证据片段**:
  ```java
  ErrorCode ERR_CODE_ANALYZE_PROJECT_FAILED =
          define("nop.err.code.analyze-project-failed", "项目分析失败:{path}", ARG_PATH);

  ErrorCode ERR_CODE_DIGEST_NOT_AVAILABLE =
          define("nop.err.code.digest-not-available", "SHA-256摘要算法不可用");
  ```
- **严重程度**: P3
- **现状**: `NopCodeCoreErrors` 中有 2 个 ErrorCode 的默认描述字符串为中文。
- **风险**: 同发现 09-01。
- **建议**: 统一改为英文默认描述。
- **信心水平**: 确定
- **误报排除**: 同 09-01。
- **复核状态**: 未复核

### [维度09-03] GraphExporter 中 ErrorCode 内联定义而非集中到 Errors 类

- **文件**: `nop-code/nop-code-graph/src/main/java/io/nop/code/graph/export/GraphExporter.java:21-22`
- **证据片段**:
  ```java
  private static final ErrorCode ERR_GRAPH_EXPORT_FAILED =
          ErrorCode.define("nop.err.code.graph-export-failed", "Graph export failed");
  ```
- **严重程度**: P3
- **现状**: `GraphExporter` 直接在类内部定义了 ErrorCode，而不是像 `NopCodeErrors`/`NopCodeCoreErrors` 那样集中到 Errors 接口类中。`nop-code-graph` 子模块没有独立的 Errors 类。
- **风险**: 错误码分散定义不利于统一维护和排查。
- **建议**: 创建 `NopCodeGraphErrors` 接口类，将 `ERR_GRAPH_EXPORT_FAILED` 移入其中。
- **信心水平**: 很可能
- **误报排除**: 已确认 `nop-code-graph` 模块无 Errors 集中定义类，仅有此一处 ErrorCode 内联定义。
- **复核状态**: 未复核

### [维度09-04] ChangeAnalyzer 静默吞噬异常（无日志）

- **文件**: `nop-code/nop-code-flow/src/main/java/io/nop/code/flow/ChangeAnalyzer.java:243-245`
- **证据片段**:
  ```java
              } catch (Exception e) {
                  // fall through
              }
  ```
- **严重程度**: P3
- **现状**: `extractFilePathFromSymbol` 方法中，解析 `extData` JSON 失败时完全吞噬异常，无日志记录。同模块 `DeadCodeDetector`（第 382 行有 `LOG.warn`）和 `FlowDetector`（第 526 行有 `LOG.debug`）都记录了日志。
- **风险**: extData 格式异常时无法通过日志发现潜在的数据问题。
- **建议**: 添加 `LOG.debug` 级别的日志记录。
- **信心水平**: 确定
- **误报排除**: 同模块中其他类似代码都记录了日志，此处应保持一致。
- **复核状态**: 未复核

### [维度09-05] JavaFileAnalyzer 静默吞噬异常（无日志）

- **文件**: `nop-code/nop-code-lang-java/src/main/java/io/nop/code/lang/java/analyzer/JavaFileAnalyzer.java:212-214`
- **证据片段**:
  ```java
                      } catch (Exception e) {
                          // ignore parse failure
                      }
  ```
- **严重程度**: P3
- **现状**: 处理 sealed 类型的 `extData` 解析时，`JsonTool.parseMap` 失败后完全吞噬异常，无日志记录。
- **风险**: extData 是由分析器自身写入的，解析失败说明数据格式可能存在问题，完全不记录日志使 bug 无法被发现。
- **建议**: 添加 `LOG.debug` 级别的日志记录。
- **信心水平**: 很可能
- **误报排除**: 同文件第 551 行的 catch 块有 `LOG.debug` 记录，处理方式不一致。
- **复核状态**: 未复核

## 合规检查通过项

1. 无 RuntimeException/IllegalArgumentException 反模式（31 处 throw 均使用 NopException + ErrorCode）
2. 公共 API 层异常处理层次清晰
3. 日志使用规范（全部 SLF4J，无 System.out/System.err）
4. ErrorCode 命名规范（`nop.err.code.*` 前缀）

## 深挖第 2 轮追加

无新发现。深挖结束。

## 最终保留项

| 编号 | 严重程度 | 文件 | 一句话摘要 |
|------|---------|------|-----------|
| 09-01 | P3 | NopCodeErrors.java:12-16 | 2 个 ErrorCode 使用中文默认描述 |
| 09-02 | P3 | NopCodeCoreErrors.java:9-13 | 2 个 ErrorCode 使用中文默认描述 |
| 09-03 | P3 | GraphExporter.java:21-22 | ErrorCode 内联定义而非集中到 Errors 类 |
| 09-04 | P3 | ChangeAnalyzer.java:243-245 | 静默吞噬异常（无日志） |
| 09-05 | P3 | JavaFileAnalyzer.java:212-214 | 静默吞噬异常（无日志） |
