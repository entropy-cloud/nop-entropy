# 维度09：错误处理与错误码

## 第 1 轮（初审）

### [维度09-01] GraphExporter 使用 IllegalArgumentException/RuntimeException 而非 NopException

- **文件**: `nop-code/nop-code-graph/src/main/java/io/nop/code/graph/export/GraphExporter.java:30,50`
- **证据片段**:
```java
// L30
throw new IllegalArgumentException("Unsupported format: " + format);
// L50
throw new RuntimeException("GraphML export failed", e);
```
- **严重程度**: P2
- **现状**: `nop-code-graph` 子模块完全未定义任何 `ErrorCode`，也未使用 `NopException`。
- **风险**: 用户可感知的 API 错误无法 i18n 和统一处理。
- **建议**: 新建 `NopCodeGraphErrors` 类定义错误码。
- **误报排除**: 不是平台内部类，是面向 API 层的错误。
- **复核状态**: 未复核

---

### [维度09-02] CodeIndexService 中 4 处 UnsupportedOperationException 硬编码消息

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:2303,2356,2371,2386`
- **证据片段**:
```java
throw new UnsupportedOperationException("FlowDetector not available");
throw new UnsupportedOperationException("ChangeAnalyzer not available");
throw new UnsupportedOperationException("DeadCodeDetector not available");
```
- **严重程度**: P2
- **现状**: 面向 BizModel 层的服务方法使用 JDK 原生异常，错误无法 i18n 和按 code 分支。
- **建议**: 在 `NopCodeErrors` 中定义对应错误码。
- **误报排除**: 直接传播到 GraphQL/REST 调用方的 API 层错误。
- **复核状态**: 未复核

---

### [维度09-03] DigestHelper 使用 IllegalStateException 而已存在对应 ErrorCode

- **文件**: `nop-code/nop-code-core/src/main/java/io/nop/code/core/util/DigestHelper.java:20,40`
- **证据片段**:
```java
throw new IllegalStateException("SHA-256 not available", e);
// 而 NopCodeCoreErrors 已定义:
ErrorCode ERR_CODE_DIGEST_NOT_AVAILABLE = define("nop.err.code.digest-not-available", "SHA-256摘要算法不可用");
```
- **严重程度**: P2
- **现状**: 已定义了对应错误码但未使用，异常类型也不符合规范。
- **建议**: 改用 `NopException(ERR_CODE_DIGEST_NOT_AVAILABLE)`。
- **误报排除**: 错误码已定义在同一个包中。
- **复核状态**: 未复核

---

### [维度09-04] ProjectAnalyzer 中 IOException 硬编码英文消息

- **文件**: `nop-code/nop-code-core/src/main/java/io/nop/code/core/analyzer/ProjectAnalyzer.java:141,143`
- **证据片段**:
```java
throw new IOException("Interrupted during parallel analysis", e);
throw new IOException("Failed during parallel analysis", e.getCause());
```
- **严重程度**: P2
- **现状**: 并行执行分支的硬编码英文消息，`ExecutionException.getCause()` 传播可能丢失堆栈。
- **建议**: 改用 `NopException` + ErrorCode。
- **误报排除**: 中间层硬编码消息在堆栈跟踪中可见。
- **复核状态**: 未复核

---

### [维度09-05] ProjectAnalyzer.analyzeIncremental 使用 UnsupportedOperationException

- **文件**: `nop-code/nop-code-core/src/main/java/io/nop/code/core/analyzer/ProjectAnalyzer.java:412-413`
- **证据片段**:
```java
throw new UnsupportedOperationException(
    "Use analyzeIncremental(Path, ProjectAnalysisResult) or analyzeIncremental(Path, Path) instead");
```
- **严重程度**: P2
- **现状**: 接口默认实现应使用 ErrorCode。
- **建议**: 在 `NopCodeCoreErrors` 中定义对应错误码。
- **误报排除**: 接口方法的默认实现。
- **复核状态**: 未复核

---

### [维度09-06] DeletedResourceStub 中 5 处 UnsupportedOperationException

- **文件**: `nop-code/nop-code-core/src/main/java/io/nop/code/core/incremental/DeletedResourceStub.java:94,99,114,119,124`
- **证据片段**:
```java
throw new UnsupportedOperationException("Resource has been deleted: " + stdPath);
throw new UnsupportedOperationException("DeletedResourceStub does not support output");
```
- **严重程度**: P3
- **现状**: 内部桩类，错误消息无法 i18n。由于是 package-private，降级为 P3。
- **建议**: 定义错误码或保持现状（内部类）。
- **误报排除**: 内部实现类。
- **复核状态**: 未复核

---

### [维度09-07] NopCodeErrors 错误码描述中硬编码中文

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/NopCodeErrors.java:15-33`
- **证据片段**:
```java
ErrorCode ERR_INDEX_DIRECTORY_FAILED = define("nop.err.code.index-directory-failed", "索引目录失败: {path}", ARG_PATH);
ErrorCode ERR_NO_ANALYZER_FOR_FILE = define("nop.err.code.no-analyzer-for-file", "没有为文件注册分析器: {filePath}", ARG_FILE_PATH);
```
- **严重程度**: P3
- **现状**: 7 个错误码中 6 个使用中文描述。应通过 i18n 资源文件管理。
- **建议**: 默认描述使用英文，中文翻译放在 i18n 文件中。
- **误报排除**: 系统性问题，不是个别遗漏。
- **复核状态**: 未复核

---

### [维度09-08] NopCodeCoreErrors 错误码描述中硬编码中文

- **文件**: `nop-code/nop-code-core/src/main/java/io/nop/code/core/NopCodeCoreErrors.java:12,15`
- **证据片段**:
```java
ErrorCode ERR_CODE_ANALYZE_PROJECT_FAILED = define("nop.err.code.analyze-project-failed", "项目分析失败:{path}", ARG_PATH);
ErrorCode ERR_CODE_DIGEST_NOT_AVAILABLE = define("nop.err.code.digest-not-available", "SHA-256摘要算法不可用");
```
- **严重程度**: P3
- **现状**: 同维度09-07。
- **复核状态**: 未复核

---

### [维度09-09] ERR_CODE_INDEX_ID_REQUIRED 使用时缺少 .param() 传值

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeSymbolBizModel.java:102,117`
- **证据片段**:
```java
if (indexId == null)
    throw new NopException(NopCodeErrors.ERR_CODE_INDEX_ID_REQUIRED);
// 已定义 ARG_INDEX_ID 但未 .param(ARG_INDEX_ID, indexId)
```
- **严重程度**: P3
- **现状**: 已定义参数常量但抛出时未传值。
- **建议**: 保持 `.param()` 调用的一致性。
- **复核状态**: 未复核

---

### [维度09-10] CodeSymbolKind.valueOf(tag) 结果变量未使用

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:764-767`
- **证据片段**:
```java
try {
    CodeSymbolKind kind = CodeSymbolKind.valueOf(tag);
} catch (IllegalArgumentException ignored) {
}
```
- **严重程度**: P3
- **现状**: `kind` 变量赋值后从未使用，更像是未完成的代码。
- **建议**: 改为断言形式或删除。
- **复核状态**: 未复核

---

### [维度09-11] ManifestStore 静默吞掉 JSON 解析异常

- **文件**: `nop-code/nop-code-core/src/main/java/io/nop/code/core/incremental/ManifestStore.java:40-42`
- **证据片段**:
```java
} catch (Exception e) {
    return new ArrayList<>();
}
```
- **严重程度**: P3
- **现状**: manifest 文件损坏时异常被完全吞掉，无日志。
- **建议**: 添加 `LOG.warn()` 日志。
- **复核状态**: 未复核

---

### [维度09-12] ChangeAnalyzer 静默吞掉 IOException/InterruptedException

- **文件**: `nop-code/nop-code-flow/src/main/java/io/nop/code/flow/ChangeAnalyzer.java:145-149`
- **证据片段**:
```java
} catch (IOException e) {
    return result;
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    return result;
}
```
- **严重程度**: P3
- **现状**: git diff 失败时直接返回可能为空的 result，无日志。
- **建议**: 添加日志记录。
- **复核状态**: 未复核

---

### [维度09-13] NopCodeApplication 使用 System.out.println

- **文件**: `nop-code/nop-code-app/src/main/java/io/nop/code/app/NopCodeApplication.java:31`
- **证据片段**:
```java
System.out.println("started");
```
- **严重程度**: P3
- **现状**: 使用 System.out 而非 SLF4J。
- **建议**: 改为 LOG.info()。
- **复核状态**: 未复核

---

## 统计摘要

| 指标 | 数量 |
|------|------|
| 非测试 throw 语句 | 24 |
| 使用 NopException + ErrorCode | 5 (21%) |
| 使用 JDK 原生异常 | 19 (79%) |
| ErrorCode 定义总数 | 9 |
| 遵循命名规范 | 9/9 (100%) |
| 硬编码中文描述 | 8/9 (89%) |
| System.out/err 使用 | 1 |
| 静默吞异常（无日志） | 2 |
