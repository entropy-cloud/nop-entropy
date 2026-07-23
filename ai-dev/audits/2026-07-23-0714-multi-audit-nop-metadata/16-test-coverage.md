> Audit Status: planned
> Audit Type: multi-dimensional
> Mission: nop-metadata

# Dimension 16: Test Coverage & Quality

### [Dimension16-01] Minimal AutoTest snapshot coverage (only 1 test case out of 82)

- **File**: `nop-metadata/nop-metadata-service/src/test/java/io/nop/metadata/service/TestAutoNopMetaClassificationCrud.java:1-29`
- **Evidence**: Only 1 test file extends `JunitAutoTestCase` with `@EnableSnapshot`. All other 81 test files use `JunitBaseTestCase`.
- **Severity**: P2
- **Status**: ~98% of test methods lack AutoTest snapshot recording. Database state changes and response structure changes not captured for regression.
- **Suggestion**: Convert key BizModel integration tests to use `JunitAutoTestCase` with `@EnableSnapshot`.
- **Confidence**: certain
- **Review Status**: unreviewed

---

### [Dimension16-02] P-1 anti-pattern: Getter/setter round-trip tests in TestCheckpointExtConfigDataBean

- **File**: `nop-metadata/nop-metadata-service/src/test/java/io/nop/metadata/service/TestCheckpointExtConfigDataBean.java:27-91`
- **Evidence**: 4 test methods testing pure getter/setter on `@DataBean` POJO.
- **Severity**: P3
- **Status**: Tests have near-zero regression protection for boilerplate properties.
- **Suggestion**: Remove 4 pure getter/setter tests. Keep JSON serialization tests.
- **Confidence**: certain
- **Review Status**: unreviewed

---

### [Dimension16-03] P-1 anti-pattern: Repetitive CRUD tests across multiple entities

- **File**: `TestNopMetaClassificationTagLabelCrud.java:42-82`, `TestNopMetaBusinessDomainDataProductCrud`, `TestNopMetaReconciliationCrud`
- **Evidence**: Multiple tests follow identical "create via DAO → read via GraphQL → delete → assert fields" pattern.
- **Severity**: P2
- **Status**: Entity-level CRUD tests for different entities duplicate same pattern without testing domain-specific behavior.
- **Suggestion**: Reduce to 1-2 representative entity CRUD tests. Focus on custom BizModel method tests.
- **Confidence**: likely
- **Review Status**: unreviewed

---

### [Dimension16-04] Four BizModel interface methods lack test coverage

- **File**: `TestNopMetaBizInterfaceCompleteness.java:37-97` (contract), no test files exercise these methods
- **Evidence**: `judgeByRuleId`, `activateContract`, `deprecateContract`, `retireContract` have zero test coverage.
- **Severity**: P2
- **Status**: 4 custom mutation methods (not auto-generated CRUD) have no regression protection.
- **Suggestion**: Add tests for these 4 uncovered methods.
- **Confidence**: certain
- **Review Status**: unreviewed

---

### [Dimension16-05] Concurrent test lacks shared state verification (potential flakiness)

- **File**: `nop-metadata/nop-metadata-service/src/test/java/io/nop/metadata/service/TestCheckpointActionDispatcherConcurrency.java:28-65`
- **Evidence**: Each thread creates its own `summary()` map - no shared mutable state tested.
- **Severity**: P2
- **Status**: Test does NOT actually test concurrent access to shared state. False sense of concurrency coverage.
- **Suggestion**: Either restructure to share state, or document the test is for stateless parallelism.
- **Confidence**: likely
- **Review Status**: unreviewed

---

### [Dimension16-06] P-5 anti-pattern: assertNotNull as precondition guards (minor)

- **File**: `TestNopMetaUniqueKeysEnforced.java:49-113`
- **Evidence**: `assertNotNull(model, ...)` used as precondition before real assertions.
- **Severity**: P3
- **Status**: Precondition guards improve error messages but add noise.
- **Suggestion**: Make helpers null-safe to eliminate precondition asserts.
- **Confidence**: speculative
- **Review Status**: unreviewed

---

### [Dimension16-07] Data auth test bypasses framework enforcement verification

- **File**: `nop-metadata/nop-metadata-service/src/test/java/io/nop/metadata/service/TestDataAuthRowLevelScoping.java:44-205`
- **Evidence**: Test only parses XML structure, does NOT verify framework enforcement.
- **Severity**: P2
- **Status**: Deliberate limitation (documented in comments) - XML validity checked but not runtime enforcement.
- **Suggestion**: Add end-to-end data auth filter test, or document gap referencing framework-level tests.
- **Confidence**: likely
- **Review Status**: unreviewed

---

### [Dimension16-08] Aggregation tests lack snapshot integration

- **File**: `TestAggregationCategoricalAndTemporal.java:36-68` and related
- **Evidence**: No `@EnableSnapshot` or `output()` usage in aggregation test files.
- **Severity**: P3
- **Status**: Aggregation logic tests provide pass/fail but cannot detect unplanned changes in result structure.
- **Suggestion**: Evaluate snapshot feasibility for deterministic aggregation results.
- **Confidence**: speculative
- **Review Status**: unreviewed

---

### [Dimension16-09] Test uses Thread.sleep(1100ms) causing test slowness

- **File**: `nop-metadata/nop-metadata-service/src/test/java/io/nop/metadata/service/TestNopMetaQualityRuleBizModel.java:264-281`
- **Evidence**: `Thread.sleep(1100)` to force time difference for time-series append test.
- **Severity**: P2
- **Status**: 1.1s forced sleep adds measurable wall-clock time.
- **Suggestion**: Replace with explicit timestamp manipulation.
- **Confidence**: certain
- **Review Status**: unreviewed

---

### [Dimension16-10] Large test files approaching maintainability threshold

- **File**: `TestNopMetaLineageEdgeBizModel.java` (~1000+ lines), `TestNopMetaQualityCheckpointBizModel.java` (~795 lines), `TestNopMetaDataSourceBizModel.java` (~843 lines)
- **Evidence**: 5 test files exceed 600 lines each.
- **Severity**: P3
- **Status**: Large files create merge conflicts and reduce navigability.
- **Suggestion**: Split into domain-focused test classes.
- **Confidence**: likely
- **Review Status**: unreviewed

---

## Summary

| ID | Finding | Severity | Confidence |
|----|---------|----------|------------|
| 16-01 | Minimal AutoTest snapshot coverage | P2 | certain |
| 16-02 | Getter/setter round-trip tests | P3 | certain |
| 16-03 | Repetitive CRUD tests across entities | P2 | likely |
| 16-04 | 4 BizModel interface methods untested | P2 | certain |
| 16-05 | Concurrent test lacks shared state verification | P2 | likely |
| 16-06 | assertNotNull precondition guards | P3 | speculative |
| 16-07 | Data auth test bypasses enforcement verification | P2 | likely |
| 16-08 | Aggregation tests lack snapshot integration | P3 | speculative |
| 16-09 | Thread.sleep(1100ms) causing test slowness | P2 | certain |
| 16-10 | Large test files (>600 lines) | P3 | likely |
