# 3 Testing, Module Safety & Documentation Remediation

> Plan Status: active
> Last Reviewed: 2026-07-25
> Source: `docs/audits/nop-stream-flink-comparison/2026-07-24-2227-multi-audit-nop-stream-flink-comparison.md` (findings 1, 4, 5, 6) and `docs/audits/nop-stream-flink-comparison/2026-07-24-2227-open-audit-nop-stream-flink-comparison.md` (findings AR-2, AR-3)
> Related: `docs/plans/nop-stream-flink-comparison/2026-07-24-1000-3-window-time-comparison.md`, `docs/plans/nop-stream-flink-comparison/2026-07-24-1000-4-cep-comparison.md`, `ai-dev/plans/318-distributed-comparison.md`

## Purpose

Close six independent P1 findings across testing gaps, module-boundary defects, documentation inaccuracy, and fragile inference logic — each small in effort but collectively material to code quality.

## Current Baseline

- No end-to-end processing-time window integration test exists (Phase 1 exit criterion unfilled).
- `nop-stream-flow` has 0 test files despite 30 hand-written Java source files.
- Session window merge has 4 `@Disabled` tests in `WindowOperator` — live defect in `mergeWindowContents()`.
- `docs-for-ai/INDEX.md` and `docs-for-ai/01-repo-map/module-groups.md` list `nop-stream-checkpoint` and `nop-stream-flink` as existing modules — they do not exist in the POM.
- `nop-stream-connector` uses `optional=true` deps with hard class references — causes `NoClassDefFoundError` for any user without explicit transitive deps.
- `PartitionedPlanGenerator.inferPartitionPolicy()` uses fragile class-name string matching — misclassifies custom partitioners.

## Goals

- Processing-time window end-to-end test exists and passes.
- `nop-stream-flow` has a smoke test covering XDSL load and fingerprint consistency.
- Session window merge works correctly for `EventTimeSessionWindows` — 4 tests re-enabled.
- `docs-for-ai/` module map accurately reflects current POM modules (or marks planned modules).
- `nop-stream-connector` does not cause hard linkage failures for optional dependencies.
- `PartitionedPlanGenerator` uses `instanceof` or interface-based partition policy inference.

## Non-Goals

- Full test coverage for `nop-stream-flow` — only a basic smoke test.
- Other `docs-for-ai/` inaccuracies beyond the module map.
- RocksDB state backend, unaligned checkpoint, or other roadmap items.

## Scope

### In Scope

- `nop-stream/nop-stream-core/src/test/java/io/nop/stream/core/integration/` (new test)
- `nop-stream/nop-stream-flow/src/test/java/io/nop/stream/flow/model/` (new test)
- `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java`
- `nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/operators/windowing/TestSessionWindowAdvancedMerge.java`
- `docs-for-ai/INDEX.md`
- `docs-for-ai/01-repo-map/module-groups.md`
- `nop-stream/nop-stream-connector/pom.xml`
- `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/StreamConnectors.java`
- `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/BatchLoaderSourceFunction.java`
- `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/BatchConsumerSinkFunction.java`
- `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/DebeziumCdcSourceFunction.java`
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/graph/PartitionedPlanGenerator.java`

### Out Of Scope

- `source-anchors.md` updates — tracked in Follow-up Backlog (P2).
- `CheckpointMetricsSnapshot.toString()` missing `failureCause` — tracked in Follow-up Backlog (P2).

## Execution Plan

### Workstream A — Test Gaps

#### A.1 Processing-time window end-to-end test

Status: planned
Targets: `nop-stream/nop-stream-core/src/test/java/io/nop/stream/core/integration/TestProcessingTimeWindowIntegration.java`

- Item Types: `Fix`

- [ ] Create `TestProcessingTimeWindowIntegration` mirroring `TestEventTimeWindowE2E.java` but using `TumblingProcessingTimeWindows` + `ProcessingTimeTrigger`.
- [ ] Deploy pipeline with source → window → sink, advance `MockProcessingTimeService`, assert window fires at expected time.
- [ ] Verify assertions trigger (not just `assertNotNull`).

Exit Criteria:
- [ ] `TestProcessingTimeWindowIntegration` exists and passes.
- [ ] Processing-time timer propagation from `AbstractStreamOperator` → `TimerServiceManager` → `HeapInternalTimerService.fireProcessingTimeTimers()` is verified end-to-end.
- [ ] No owner-doc update required (internal test, no API or contract change).
- [ ] `ai-dev/logs/` corresponding date entry updated.

#### A.2 nop-stream-flow smoke test

Status: planned
Targets: `nop-stream/nop-stream-flow/src/test/java/io/nop/stream/flow/model/StreamModelSmokeTest.java`

- Item Types: `Fix`

- [ ] Create basic smoke test that loads an example `.stream.xml` via XDSL loader.
- [ ] Verify `StreamModel` structural integrity (sources, transforms, sinks).
- [ ] Run `StreamModelFingerprint` fingerprint consistency check.

Exit Criteria:
- [ ] `StreamModelSmokeTest` exists and passes.
- [ ] At least one XDSL round-trip (load → inspect → verify) works.
- [ ] No owner-doc update required (internal test, no API or contract change).
- [ ] `ai-dev/logs/` corresponding date entry updated.

#### A.3 Fix session window merge bug

Status: planned
Targets:
- `WindowOperator.java` (lines 599-609, merge path)
- Disabled test files

- Item Types: `Fix`

- [ ] Diagnose root cause: `mergeWindowContents()` not handling `EventTimeSessionWindows` correctly under new `WindowOperator`.
- [ ] Fix the merge logic — ensure session windows merge correctly for event-time.
- [ ] Re-enable all 4 disabled tests in `TestSessionWindowAdvancedMerge` and `TestSessionWindowWithPeriodicWatermark`.
- [ ] Verify no regression in non-session window tests.

Exit Criteria:
- [ ] All 4 previously `@Disabled` session window tests pass.
- [ ] `WindowOperator.mergeWindowContents()` handles `EventTimeSessionWindows` correctly.
- [ ] No new test disabled.
- [ ] No owner-doc update required (bug fix preserves existing contract).
- [ ] `ai-dev/logs/` corresponding date entry updated.

### Workstream B — Module & Documentation Fixes

#### B.1 Fix docs-for-ai module map

Status: planned
Targets:
- `docs-for-ai/INDEX.md`
- `docs-for-ai/01-repo-map/module-groups.md`

- Item Types: `Fix`

- [ ] Update `INDEX.md` line 212: remove or qualify `nop-stream-checkpoint` and `nop-stream-flink`.
- [ ] Update `module-groups.md` lines 22-23: remove entries or add explicit "(planned)" qualifier.
- [ ] Verify no other references to non-existent modules in `docs-for-ai/`.

Exit Criteria:
- [ ] `docs-for-ai/` no longer lists `nop-stream-checkpoint` or `nop-stream-flink` as existing modules without qualification.
- [ ] `grep -r "nop-stream-checkpoint\|nop-stream-flink" docs-for-ai/` returns only qualified or planned references.
- [ ] Doc link checker passes (`node ai-dev/tools/check-doc-links.mjs --strict`).
- [ ] `ai-dev/logs/` corresponding date entry updated.

#### B.2 Fix StreamConnectors optional-dep hard linkage

Status: planned
Targets:
- `nop-stream/nop-stream-connector/pom.xml`
- `StreamConnectors.java`
- `BatchLoaderSourceFunction.java`
- `BatchConsumerSinkFunction.java`
- `DebeziumCdcSourceFunction.java`

- Item Types: `Fix | Decision`

- [ ] Option A (preferred split): Extract batch-specific connector classes into `nop-stream-connector-batch` module with non-optional dep on `nop-batch-core`. Extract Debezium-specific classes into `nop-stream-connector-debezium` module with non-optional dep on `nop-message-debezium`. Base `nop-stream-connector` retains only non-optional source/sink types.
- [ ] Option B (minimal): Make `nop-batch-core` and `nop-message-debezium` non-optional deps on `nop-stream-connector`.
- [ ] Select option based on module-boundary impact assessment. Prefer Option A if `nop-stream-connector` is expected to be a leaf dependency.
- [ ] Update POM and class imports accordingly.

Exit Criteria:
- [ ] `nop-stream-connector` loads without `NoClassDefFoundError` when `nop-batch-core` and `nop-message-debezium` are not on the classpath.
- [ ] Batch and Debezium connector classes are either in separate modules or behind non-optional deps.
- [ ] All existing connector tests pass.
- [ ] No regression in user code that depends on `StreamConnectors.fromBatchLoader()` or `DebeziumCdcSourceFunction`.
- [ ] If Option A (module split) chosen: `docs-for-ai/01-repo-map/module-groups.md` updated; decision rationale recorded in `ai-dev/design/nop-stream/`. If Option B (non-optional deps) chosen: `No owner-doc update required`.
- [ ] `ai-dev/logs/` corresponding date entry updated.

#### B.3 Fix PartitionedPlanGenerator partition inference

Status: planned
Targets: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/graph/PartitionedPlanGenerator.java`

- Item Types: `Fix`

- [ ] Replace string-based class-name matching with `instanceof` checks against known partitioner interfaces.
- [ ] Add `PartitionPolicy getPolicy()` method to `IPartitioner` interface (or create a `PartitionPolicyAware` marker).
- [ ] Implement `getPolicy()` on `ForwardPartitioner` and any other known partitioners.
- [ ] Add coverage for `UNION` and `SINGLETON` policies or add a clear error for unrecognized partitioners.

Exit Criteria:
- [ ] `PartitionedPlanGenerator.inferPartitionPolicy()` uses `instanceof` or interface-based dispatch — no `getName().contains(...)`.
- [ ] Custom partitioners that don't declare a policy cause a clear error or are classified via the new interface.
- [ ] Existing partition inference tests pass unchanged.
- [ ] `UNION` and `SINGLETON` policies have real inference paths or are explicitly marked unimplemented with error.
- [ ] No owner-doc update required (internal refactor, documented interface).
- [ ] `ai-dev/logs/` corresponding date entry updated.

## Closure Gates

- [ ] All in-scope P1 findings across 6 areas are closed.
- [ ] Processing-time window E2E test exists, session window tests re-enabled.
- [ ] `nop-stream-flow` has basic XDSL smoke test.
- [ ] `docs-for-ai/` module map is accurate.
- [ ] `nop-stream-connector` does not cause hard linkage with optional deps.
- [ ] `PartitionedPlanGenerator` uses type-safe partition inference.
- [ ] No in-scope P1 live defect or contract drift deferred to follow-up.
- [ ] Owner docs updated: `docs-for-ai/INDEX.md`, `docs-for-ai/01-repo-map/module-groups.md`.
- [ ] Independent sub-agent closure-audit completed and evidence recorded.
- [ ] Anti-Hollow Check: each workstream's code changes are real (not stubs); test assertions verify behavior.
- [ ] End-to-end verification: processing-time window test runs from source to sink.
- [ ] `./mvnw compile -pl nop-stream -am`
- [ ] `./mvnw test -pl nop-stream/nop-stream-core -am`
- [ ] `./mvnw test -pl nop-stream/nop-stream-flow -am` (if module has tests after this plan)
- [ ] `./mvnw test -pl nop-stream/nop-stream-runtime -am`
- [ ] Doc link checker passes.
- [ ] Checkstyle / code convention pass.

## Deferred But Adjudicated

None.

## Non-Blocking Follow-ups

- P2 items from both audits are tracked in the Follow-up Backlog section of the mission roadmap.

## Closure

Status Note: *To be filled on completion.*
Completed:
