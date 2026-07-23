> Audit Status: planned
> Audit Type: multi-dimensional
> Mission: nop-metadata

# Dimension 09: Error Handling & Error Codes

### [Dimension09-01] NopMetadataException missing String constructors

- **File**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/NopMetadataException.java:22-32`
- **Evidence**:
  ```java
  public class NopMetadataException extends NopException {
      public NopMetadataException(ErrorCode errorCode) { super(errorCode); }
      public NopMetadataException(ErrorCode errorCode, Throwable cause) { super(errorCode, cause); }
  }
  ```
- **Severity**: P2
- **Status**: The class provides only ErrorCode-based constructors. The error-handling.md guide requires `(String)` and `(String, Throwable)` constructors for internal paths.
- **Risk**: Module internal code must define intermediate ErrorCodes for simple messages or throw bare NopException, undermining consistency.
- **Suggestion**: Add `(String message)` and `(String message, Throwable cause)` constructors.
- **Confidence**: certain
- **Review Status**: unreviewed

---

### [Dimension09-02] NopMetaDataContractBizModel uses bare NopException instead of NopMetadataException

- **File**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaDataContractBizModel.java:43,72,99,109,119,133`
- **Evidence**:
  ```java
  throw new NopException(ERR_CONTRACT_NOT_FOUND).param("contractId", id);
  ```
- **Severity**: P3
- **Status**: 6 throw sites use bare `NopException` while other sites in the same class use `NopMetadataException`.
- **Risk**: Callers catching by type (`catch (NopMetadataException e)`) would miss these sites.
- **Suggestion**: Replace with `new NopMetadataException(ERR_CONTRACT_NOT_FOUND)`.
- **Confidence**: certain
- **Review Status**: unreviewed

---

### [Dimension09-03] MetaTableFieldResolver uses bare NopException

- **File**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/field/MetaTableFieldResolver.java:221,230,241`
- **Evidence**:
  ```java
  throw new NopException(errOnInvalid).param("metaTableId", table.getMetaTableId())...
  ```
- **Severity**: P3
- **Status**: Uses `NopException` instead of `NopMetadataException`.
- **Suggestion**: Replace with `new NopMetadataException(errOnInvalid)`.
- **Confidence**: certain
- **Review Status**: unreviewed

---

### [Dimension09-04] MetaQualityRuleExecutor uses bare NopException with fully-qualified class path

- **File**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/quality/MetaQualityRuleExecutor.java:639`
- **Evidence**:
  ```java
  throw new NopException(io.nop.metadata.service.NopMetadataErrors.ERR_QUALITY_EXPECT_PASS_WHEN_INVALID, e)
      .param(io.nop.metadata.service.NopMetadataErrors.ARG_QUALITY_RULE_ID, "<evalExpectPassWhen>")
  ```
- **Severity**: P3
- **Status**: Uses bare NopException with fully-qualified class paths instead of clean imports.
- **Suggestion**: Properly import `NopMetadataErrors` and use `new NopMetadataException(...)`.
- **Confidence**: certain
- **Review Status**: unreviewed

---

### [Dimension09-05] Inline string keys in .param() instead of ARG_* constants (widespread)

- **File**: Multiple files across the module (AggregationHelper.java, MetaAggregationExecutor.java, MetaTableProfiler.java, JoinFieldResolver.java, MemoryOrderByComparator.java, CrossDbInMemoryAggregationProcessor.java, MetaJoinExecutor.java, NopMetaDataContractBizModel.java)
- **Evidence**:
  ```java
  .param("metaTableId", metaTableId)  // should be .param(ARG_META_TABLE_ID, metaTableId)
  .param("error", messageOf(e))       // should be .param(ARG_ERROR, messageOf(e))
  ```
- **Severity**: P3
- **Status**: Widespread pattern. `NopMetadataArgs` defines ARG_* constants but many throw sites use inline string keys instead.
- **Risk**: Misspelled param keys go undetected (no compile-time check). Refactoring becomes dangerous.
- **Suggestion**: Replace inline string keys with corresponding ARG_* constants. Add missing constants to `NopMetadataArgs` for keys like `"contractId"`, `"tableName"`, `"url"`.
- **Confidence**: certain
- **Review Status**: unreviewed

---

### [Dimension09-06] SQLException caught with empty body in MetaTableProfiler

- **File**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/profiling/MetaTableProfiler.java:485`
- **Evidence**:
  ```java
  try {
      long v = rs.getLong(col);
      if (!rs.wasNull()) { return v; }
  } catch (SQLException ignore) {
      // 非数值列类型，回退按字符串解析
  }
  ```
- **Severity**: P3
- **Status**: SQLException is silently caught with an empty body. While there's a fallback, the exception is discarded without any logging.
- **Suggestion**: Add `LOG.trace()` or `LOG.debug()` before falling back.
- **Confidence**: likely
- **Review Status**: unreviewed

---

### [Dimension09-07] ErrorCode naming uses hyphens instead of dots for sub-domain separator (systemic)

- **File**: All ErrorCode definition interfaces (AggregationErrors, DataSourceErrors, FieldErrors, JoinErrors, LineageErrors, QualityErrors, ReconErrors, SqlErrors, MiscErrors)
- **Evidence**:
  ```java
  ErrorCode.define("nop.err.metadata.aggr-no-measure", ...)  // hyphen, not dot
  ```
- **Severity**: P2
- **Status**: The documented convention requires dots between sub-domain and error (e.g., `nop.err.metadata.aggr.no-measure`). All ~80+ error codes use hyphens instead.
- **Risk**: Downstream tooling parsing error codes by dots for sub-domain extraction will produce incorrect values.
- **Suggestion**: Either rename all error codes to use dots, or update `NopMetadataErrors.java` javadoc to document the hyphen convention.
- **Confidence**: certain
- **Review Status**: unreviewed

---

## Summary

| ID | Finding | Severity | Confidence |
|----|---------|----------|------------|
| 09-01 | NopMetadataException missing String constructors | P2 | certain |
| 09-02 | NopMetaDataContractBizModel uses bare NopException (6 sites) | P3 | certain |
| 09-03 | MetaTableFieldResolver uses bare NopException | P3 | certain |
| 09-04 | MetaQualityRuleExecutor uses bare NopException + FQN | P3 | certain |
| 09-05 | Inline .param() string keys instead of ARG_* constants | P3 | certain |
| 09-06 | Empty catch of SQLException in MetaTableProfiler | P3 | likely |
| 09-07 | Systemic hyphen vs dot in ErrorCode naming | P2 | certain |
