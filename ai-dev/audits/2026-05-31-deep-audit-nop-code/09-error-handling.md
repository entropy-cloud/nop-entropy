# Audit Dimension 09: Error Handling and Error Codes — nop-code

---

### [维度09-01] Chinese Error Messages in NopCodeCoreErrors ErrorCode Definitions

- **File**: `nop-code-core/src/main/java/io/nop/code/core/NopCodeCoreErrors.java:11-15`
- **Evidence Snippet**:
```java
ErrorCode ERR_CODE_ANALYZE_PROJECT_FAILED =
        define("nop.err.code.analyze-project-failed", "项目分析失败:{path}", ARG_PATH);
ErrorCode ERR_CODE_DIGEST_NOT_AVAILABLE =
        define("nop.err.code.digest-not-available", "SHA-256摘要算法不可用");
```
- **Severity**: P1
- **Current State**: Both ErrorCode definitions use Chinese message strings.
- **Risk**: Violates AGENTS.md rule "Error messages must be in English."
- **Recommendation**: Replace with English equivalents.
- **Confidence**: Certain
- **False Positive Exclusion**: ErrorCode definition strings, verified in source.
- **Review Status**: Not reviewed

---

### [维度09-02] Chinese Error Messages in NopCodeErrors ErrorCode Definitions

- **File**: `nop-code-service/src/main/java/io/nop/code/service/NopCodeErrors.java:14-30`
- **Evidence Snippet**:
```java
ErrorCode ERR_INDEX_DIRECTORY_FAILED =
        define("nop.err.code.index-directory-failed", "索引目录失败: {path}", ARG_PATH);
ErrorCode ERR_INDEX_NOT_FOUND =
        define("nop.err.code.index-not-found", "索引未找到: {indexId}", ARG_INDEX_ID);
```
- **Severity**: P1
- **Current State**: 6 of 10 ErrorCode definitions use Chinese messages; remaining 4 use English.
- **Risk**: Inconsistent within same file; API-facing messages in Chinese.
- **Recommendation**: Replace all Chinese messages with English.
- **Confidence**: Certain
- **False Positive Exclusion**: Verified 6 Chinese + 4 English in same file.
- **Review Status**: Not reviewed

---

### [维度09-03] IllegalArgumentException in Public API Method (GraphExporter)

- **File**: `nop-code-graph/src/main/java/io/nop/code/graph/export/GraphExporter.java:35`
- **Evidence Snippet**:
```java
throw new IllegalArgumentException("Unsupported format: " + format);
```
- **Severity**: P2
- **Current State**: Public method throws IllegalArgumentException instead of NopException with ErrorCode. Same class uses NopException at line 55.
- **Risk**: Bypasses Nop platform's unified error handling.
- **Recommendation**: Use NopException with ErrorCode.
- **Confidence**: Certain
- **False Positive Exclusion**: Public API method, not private utility.
- **Review Status**: Not reviewed

---

### [维度09-04] Inline ErrorCode Definition in GraphExporter

- **File**: `nop-code-graph/src/main/java/io/nop/code/graph/export/GraphExporter.java:21-22`
- **Evidence Snippet**:
```java
private static final ErrorCode ERR_GRAPH_EXPORT_FAILED =
        ErrorCode.define("nop.err.code.graph-export-failed", "Graph export failed");
```
- **Severity**: P3
- **Current State**: Inline private ErrorCode instead of centralized error file. Module has no NopCodeGraphErrors.java.
- **Risk**: Harder to discover, reuse, and audit.
- **Recommendation**: Create centralized NopCodeGraphErrors.java.
- **Confidence**: Certain
- **Review Status**: Not reviewed

---

### [维度09-05] Missing .param() Context on Multiple NopException Throws

- **File**: `nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:2503,2556,2571,2586`
- **Evidence Snippet**:
```java
throw new NopException(ERR_CODE_FLOW_DETECTOR_NOT_AVAILABLE);
// indexId is in scope but not attached
```
- **Severity**: P2
- **Current State**: 7 of 14 NopException throws lack .param() to attach diagnostic context.
- **Risk**: Error diagnosis significantly harder without context parameters.
- **Recommendation**: Add .param(ARG_INDEX_ID, indexId) to all relevant throws.
- **Confidence**: Certain
- **False Positive Exclusion**: indexId in scope for all throws, verified.
- **Review Status**: Not reviewed

---

### [维度09-06] Silently Swallowed Exception in ChangeAnalyzer (JSON Parse)

- **File**: `nop-code-flow/src/main/java/io/nop/code/flow/ChangeAnalyzer.java:229-241`
- **Evidence Snippet**:
```java
} catch (Exception e) {
    // fall through
}
```
- **Severity**: P2
- **Current State**: JSON parse exception completely silently swallowed.
- **Risk**: Malformed extData causes invisible symbols with no diagnostic trail.
- **Recommendation**: Add DEBUG-level log.
- **Confidence**: Certain
- **Review Status**: Not reviewed

---

### [维度09-07] Silently Swallowed Exception in FlowDetector (JSON Parse)

- **File**: `nop-code-flow/src/main/java/io/nop/code/flow/FlowDetector.java:525-527`
- **Evidence Snippet**:
```java
} catch (Exception e) {
    // fall through
}
```
- **Severity**: P2
- **Current State**: Identical pattern to 09-06. DeadCodeDetector in same module handles correctly with LOG.warn.
- **Risk**: Same as 09-06.
- **Recommendation**: Add DEBUG-level log.
- **Confidence**: Certain
- **Review Status**: Not reviewed

---

### [维度09-08] Silently Swallowed IllegalArgumentException in NopCodeSymbolBizModel

- **File**: `nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeSymbolBizModel.java:74-78`
- **Evidence Snippet**:
```java
try {
    return Enum.valueOf(CodeSymbolKind.class, k);
} catch (IllegalArgumentException e) {
    return null;
}
```
- **Severity**: P3
- **Current State**: Invalid kind string silently filtered out without logging.
- **Risk**: API callers may not realize filter parameters are ignored.
- **Recommendation**: Add DEBUG log.
- **Confidence**: Certain
- **Review Status**: Not reviewed

---

### [维度09-09] Empty Catch Block Without Comment (CommunityDetector)

- **File**: `nop-code-graph/src/main/java/io/nop/code/graph/community/CommunityDetector.java:648-649`
- **Severity**: P3
- **Current State**: Empty catch block with no comment.
- **Recommendation**: Add clarifying comment or named `ignored` variable.
- **Confidence**: Likely
- **Review Status**: Not reviewed

---

### [维度09-10] IOException Wrapping Without ErrorCode in ProjectAnalyzer

- **File**: `nop-code-core/src/main/java/io/nop/code/core/analyzer/ProjectAnalyzer.java:149-153`
- **Evidence Snippet**:
```java
throw new IOException("Interrupted during parallel analysis", e);
```
- **Severity**: P2
- **Current State**: Public method declares throws IOException instead of using NopException.
- **Risk**: Callers get raw IOException instead of structured NopException.
- **Recommendation**: Convert to NopException with ErrorCode.
- **Confidence**: Certain
- **Review Status**: Not reviewed

---

## Positive Findings

- All logging uses SLF4J; no System.out/System.err
- No bare RuntimeException or NullPointerException throws
- Exception chain preservation (.cause()) properly used
- ErrorCode pattern broadly adopted (14 NopException throws)
- Centralized error files correctly use ErrorCode.define()

## Summary

| Severity | Count | IDs |
|----------|-------|-----|
| P1 | 2 | 09-01, 09-02 |
| P2 | 5 | 09-03, 09-05, 09-06, 09-07, 09-10 |
| P3 | 4 | 09-04, 09-08, 09-09, 09-10 |

## 最终保留项

| 编号 | 严重程度 | 文件路径 | 摘要 |
|------|---------|---------|------|
| 09-01 | P1 | NopCodeCoreErrors.java | ErrorCode 使用中文消息 |
| 09-02 | P1 | NopCodeErrors.java | 6/10 ErrorCode 使用中文消息 |
| 09-03 | P2 | GraphExporter.java | 公共 API 抛 IllegalArgumentException |
| 09-05 | P2 | CodeIndexService.java | 7 处 NopException 缺少 .param() |
| 09-06 | P2 | ChangeAnalyzer.java | JSON 解析异常被静默吞掉 |
| 09-07 | P2 | FlowDetector.java | JSON 解析异常被静默吞掉 |
| 09-10 | P2 | ProjectAnalyzer.java | 公共方法抛 IOException |
| 09-04 | P3 | GraphExporter.java | 内联 ErrorCode 定义 |
| 09-08 | P3 | NopCodeSymbolBizModel.java | 静默吞掉无效枚举输入 |
| 09-09 | P3 | CommunityDetector.java | 空 catch 块无注释 |
