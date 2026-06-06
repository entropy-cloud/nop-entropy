# 维度 09：错误处理与错误码

## 第 1 轮（初审）

### [维度09-01] DeletedResourceStub 抛出 UnsupportedOperationException 而非 NopException

- **文件**: `nop-code/nop-code-core/src/main/java/io/nop/code/core/incremental/DeletedResourceStub.java:92-125`
- **证据片段**:
```java
@Override
public InputStream getInputStream() {
    throw new UnsupportedOperationException("Resource has been deleted: " + stdPath);
}
@Override
public OutputStream getOutputStream(boolean append) {
    throw new UnsupportedOperationException("DeletedResourceStub does not support output");
}
```
- **严重程度**: P2
- **现状**: DeletedResourceStub 是包私有的内部类，5 个方法均抛出 `UnsupportedOperationException`，未使用 ErrorCode 模式。
- **风险**: 如果 IResource 的调用方统一用 `catch (NopException)` 进行错误处理，会漏捕获这些异常。但调用路径已有外层 catch Exception 保护，实际影响有限。
- **建议**: 维持现状可接受，或改为 NopException 以统一风格。
- **信心水平**: 很可能
- **误报排除**: 包私有 + 调用路径已保护，实际影响有限。
- **复核状态**: 未复核

### [维度09-02] ProjectAnalyzer.analyzeIncremental(String) 抛出 UnsupportedOperationException

- **文件**: `nop-code/nop-code-core/src/main/java/io/nop/code/core/analyzer/ProjectAnalyzer.java:376-378`
- **证据片段**:
```java
@Override
public ProjectAnalysisResult analyzeIncremental(String projectRoot, List<String> changedFilePaths) {
    throw new UnsupportedOperationException("Use analyzeIncremental(String, ProjectAnalysisResult) instead");
}
```
- **严重程度**: P3
- **现状**: 该方法是接口方法的不完全实现，明确标记为不应使用。Java 标准库惯用模式。
- **风险**: 极低——这是"编程错误"场景而非运行时故障。
- **建议**: 维持现状。
- **信心水平**: 确定
- **误报排除**: 属于 API 设计意图，非错误处理缺陷。
- **复核状态**: 未复核

### [维度09-03] GraphExporter.exportGraphML 丢失 format 上下文参数

- **文件**: `nop-code/nop-code-graph/src/main/java/io/nop/code/graph/export/GraphExporter.java:58-63`
- **证据片段**:
```java
try {
    exporter.exportGraph(graph, writer);
} catch (ExportException e) {
    throw new NopException(NopCodeCoreErrors.ERR_GRAPH_EXPORT_FAILED).cause(e);
}
```
- **严重程度**: P2
- **现状**: `exportGraphML` 捕获 `ExportException` 后使用 `.cause(e)` 保留了异常链，但未使用 `.param()` 传递 format 参数。对比同类第 42 行的 `default` 分支，那里正确传递了 `.param("format", format)`。
- **风险**: GraphML 导出失败时错误消息不包含 format 信息，降低可诊断性。
- **建议**: 添加 `.param("format", "GRAPHML")` 以保持与 default 分支一致。
- **信心水平**: 确定
- **误报排除**: 同类的 default 分支已展示正确的 param 传递模式，此处确实缺失。
- **复核状态**: 未复核

### [维度09-04] CodeQueryService 857 行无任何日志记录

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeQueryService.java:1-857`
- **证据片段**: 文件头部无 Logger 导入，无 `static final Logger LOG` 声明。
- **严重程度**: P2
- **现状**: `CodeQueryService` 是 857 行的内部服务类，负责所有代码查询逻辑，但没有任何日志记录。大量方法在 `daoProvider == null` 时静默返回空集合或 null，无 WARN 日志。
- **风险**: 当 `daoProvider == null` 导致所有查询静默返回空结果时，无法通过日志快速定位问题根因。
- **建议**: 添加 SLF4J Logger，在关键降级路径添加 WARN 日志。
- **信心水平**: 确定
- **误报排除**: 其他同层级服务（CodeIndexService、CodeSearchService、CodeGraphService）均已正确使用 SLF4J Logger。
- **复核状态**: 未复核

### [维度09-05] CodeQueryService 中 CodeLanguage.valueOf 无保护

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeQueryService.java:55-56`
- **证据片段**:
```java
result.setLanguage(entity.getLanguage() != null
        ? CodeLanguage.valueOf(entity.getLanguage()) : null);
```
- **严重程度**: P2
- **现状**: `CodeLanguage.valueOf()` 在数据库中存储了不在枚举定义中的语言名称时会抛出 `IllegalArgumentException`，没有 catch 保护。该方法在 `entityToFileResult` 中被大量文件查询方法调用。
- **风险**: 数据库存在脏数据时导致整个查询结果构建失败。`IllegalArgumentException` 不属于 ErrorCode 体系。
- **建议**: 使用 try-catch 包装，降级为 null 或记录 WARN 日志，与同模块的 `CodeSearchService:102-106` 模式一致。
- **信心水平**: 很可能
- **误报排除**: 同模块已有处理此模式的标准做法。
- **复核状态**: 未复核

### [维度09-06] NopCodeSymbolBizModel 静默丢弃无效枚举值

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeSymbolBizModel.java:77-86`
- **证据片段**:
```java
kindList = kinds.stream()
        .map(k -> {
            try {
                return Enum.valueOf(CodeSymbolKind.class, k);
            } catch (IllegalArgumentException e) {
                return null;
            }
        })
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
```
- **严重程度**: P3
- **现状**: BizModel 层的 GraphQL 公共 API 方法在用户传入无效的 symbol kind 字符串时，静默忽略这些无效值，不记录日志也不返回错误。
- **风险**: 调用方传入拼写错误的 kind 值时得到"成功但结果不符合预期"的响应。
- **建议**: 添加 WARN 级别日志记录被忽略的无效 kind 值。
- **信心水平**: 很可能
- **误报排除**: 宽松过滤是一种设计选择，但缺少日志使得问题无法追踪。
- **复核状态**: 未复核

### [维度09-07] CodeIndexService.updateIndexStats 失败时仅 WARN 不抛出

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:1515-1527`
- **证据片段**:
```java
} catch (Exception e) {
    LOG.warn("Failed to update index stats for {}", indexId, e);
}
```
- **严重程度**: P3
- **现状**: `updateIndexStats` 在完整 try-catch 中运行，任何异常都被吞掉仅记录 WARN。
- **风险**: 如果数据库长时间不可用，用户可能长时间看到过时的统计信息。但 warn-and-continue 在此场景下是合理的降级策略。
- **建议**: 维持现状。
- **信心水平**: 确定
- **误报排除**: 有意的降级设计。
- **复核状态**: 未复核

## 整体评估

模块错误处理质量良好。公共 API 层严格遵循 ErrorCode 模式。主要改进点集中在 CodeQueryService 的日志和防御性编码上。
