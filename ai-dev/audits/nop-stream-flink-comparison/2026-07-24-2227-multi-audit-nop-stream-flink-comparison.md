> Audit Status: planned
> Audit Type: multi-dimensional
> Mission: nop-stream-flink-comparison

# Multi-Dimensional Audit Report: nop-stream (Flink Comparison Mission)

## 1. Scope & Method

**Target**: `nop-stream/` — all 6 submodules (`nop-stream-core`, `nop-stream-runtime`, `nop-stream-cep`, `nop-stream-connector`, `nop-stream-flow`, `nop-stream-fraud-example`)

**Dimensions examined** (clustered by mission relevance):
| Category | Dimensions | Coverage |
|---|---|---|
| Code correctness (Phase 1-4 deliverables) | 07 (BizModel), 15 (type safety), 14 (async/txn) | Full code review of all 4 phases |
| API surface & contract | 03, 12 | `ForwardPartitioner`, `StreamOperatorFactory`, `InternalTimerService`, `PendingCheckpoint`, `CheckpointMetrics` |
| Test coverage | 16, 21 | 257 test files across stream-core + stream-runtime; focused tests identified per phase |
| Doc-code consistency | 18, 19 | `docs-for-ai/INDEX.md`, `module-groups.md`, `source-anchors.md`, `error-handling.md`, `ai-dev/design/nop-stream/` |
| Error handling | 09 | 53 ErrorCodes, `StreamException` hierarchy, all `throw` statements |
| IoC & beans | 08 | 0 beans.xml files — framework module, no IoC |
| Module boundaries | 01, 02 | pom.xml deps, file sizing, module responsibilities |

**Baseline**: 1381 tests pass (0 failures, 0 errors, 4 skipped — pre-existing session window merge bug).

---

## 2. Findings

---

### [P1] Missing end-to-end processing time window integration test

- **File**: `nop-stream/nop-stream-core/src/test/java/io/nop/stream/core/jobgraph/TestJobGraphGenerator.java` (class exists, but `TestProcessingTimeWindowIntegration` does not exist anywhere in the codebase)
- **Evidence**: `grep -r TestProcessingTimeWindowIntegration nop-stream/` → no result. Plan Phase 1 Exit Criteria line 83 mandates this test.
- **Severity**: P1 — **Material**: an exit criterion of a completed phase was not satisfied. The processing-time timer implementation in `HeapInternalTimerService` works at the unit-test level, but was never verified end-to-end through the full pipeline (source with processing-time semantics → window operator → sink). Any regression in `TimerServiceManager.fireProcessingTimeTimers()` integration would go undetected by current tests.
- **Status**: The plan status is "completed" for Phase 1, but this exit criterion was not fulfilled.
- **Risk**: A wired-up regression in processing-time timer propagation across `AbstractStreamOperator` → `TimerServiceManager` → `HeapInternalTimerService.fireProcessingTimeTimers()` would not be caught.
- **Recommendation**: Create `TestProcessingTimeWindowIntegration` in `nop-stream-core/src/test/java/io/nop/stream/core/integration/` (mirroring `TestEventTimeWindowE2E.java` which covers event-time). At minimum: deploy a pipeline with `TumblingProcessingTimeWindows` + `ProcessingTimeTrigger`, advance the `MockProcessingTimeService`, and assert that the window fires at the expected time. Verify assertions trigger (not just assertNotNull).
- **Confidence**: Certain
- **False positive exclusion**: This is not a cosmetic nit — it's a missing test for a behavioral contract that the plan itself recognized as requiring verification.

---

### [P1] `CheckpointCoordinator.abortPendingCheckpoint` increments `numFailedCheckpoints` for aborts

- **File**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:336`
- **Evidence**:
  ```java
  // line 335-336
  metrics.incrementAbortedCheckpoints();  // <-- missing
  metrics.recordFailure("Aborted: " + reason);  // <-- calls incrementFailedCheckpoints() internally
  ```
  `recordFailure()` at `CheckpointMetrics.java:85-88`:
  ```java
  public void recordFailure(String cause) {
      incrementFailedCheckpoints();  // <-- also increments failure counter
      this.failureCause = cause;
  }
  ```
- **Severity**: P1 — **Material**: real metric pollution. Aborts and failures are distinct lifecycle events tracked by separate counters (`numAbortedCheckpoints` vs `numFailedCheckpoints`). Calling `recordFailure` (which increments `numFailedCheckpoints`) for an abort inflates the failure count, making the metrics untrustworthy. Additionally, `failureCause` is overwritten with "aborted" messages, masking true failures.
- **Status**: Abort path was reworked in Phase 4 but the metrics recording conflates abort/failure.
- **Risk**: Monitoring dashboards that alert on `numFailedCheckpoints` would fire false positives for aborts. Operational debugging would see `failureCause = "Aborted: Timeout"` when the real failure cause was overwritten.
- **Recommendation**: Replace line 336 with `metrics.recordAborted("Aborted: " + reason)` where `recordAborted()` increments `numAbortedCheckpoints` and sets `failureCause` separately without touching the failure counter. OR call `metrics.incrementAbortedCheckpoints()` and `metrics.setFailureCause("Aborted: " + reason)` as two separate calls that don't conflate counters.
- **Confidence**: Certain
- **False positive exclusion**: This is not a style preference — it's a concrete defect in operational metrics.

---

### [P1] `PendingCheckpoint.fail()` uses `ERR_STREAM_CHECKPOINT_ABORTED` error code

- **File**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/PendingCheckpoint.java:174-185`
- **Evidence**:
  ```java
  public synchronized void fail(String reason, Throwable cause) {
      checkValidTransition(Status.FAILED);
      if (status.compareAndSet(Status.RUNNING, Status.FAILED)) {
          isDisposed = true;
          if (!completableFuture.isDone()) {
              Exception error = cause != null
                      ? new StreamException(ERR_STREAM_CHECKPOINT_ABORTED, cause).param(ARG_REASON, reason)  // <-- ABORTED
                      : new StreamException(ERR_STREAM_CHECKPOINT_ABORTED).param(ARG_REASON, reason);        // <-- ABORTED
              completableFuture.completeExceptionally(error);
          }
      }
  }
  ```
- **Severity**: P1 — **Material**: semantic error code mismatch. A failed checkpoint should carry error semantics distinct from aborted checkpoints. Downstream consumers that inspect the `ErrorCode` cannot distinguish a true failure (e.g., storage-write exception) from an abort (e.g., timeout). The plan explicitly required in Phase 4 that "FAILED" status be added for this distinction, but the error code was not updated.
- **Status**: Phase 4 added `Status.FAILED` and `isValidTransition()` but did not introduce a corresponding `ERR_STREAM_CHECKPOINT_FAILED` error code.
- **Risk**: Any code that switches on ErrorCode cannot distinguish failures from aborts. Log analysis / alert routing is impaired.
- **Recommendation**: Add `ERR_STREAM_CHECKPOINT_FAILED` to `NopStreamErrors.java` and use it in `fail()` instead of `ERR_STREAM_CHECKPOINT_ABORTED`. Update `abort()` to continue using `ERR_STREAM_CHECKPOINT_ABORTED` (which it already does correctly).
- **Confidence**: Certain
- **False positive exclusion**: This is a direct contract violation of the plan's Phase 4 goal — the `FAILED` state was added but the error path still reports ABORTED.

---

### [P1] `docs-for-ai/` documents non-existent modules `nop-stream-checkpoint` and `nop-stream-flink`

- **Files**:
  - `docs-for-ai/INDEX.md:212` — mentions `nop-stream-checkpoint` and `nop-stream-flink`
  - `docs-for-ai/01-repo-map/module-groups.md:22-23` — mentions both modules as submodules
- **Evidence**: The actual POM (`nop-stream/pom.xml`) defines exactly 6 modules: `nop-stream-core`, `nop-stream-cep`, `nop-stream-connector`, `nop-stream-runtime`, `nop-stream-flow`, `nop-stream-fraud-example`. No POM or directory for `nop-stream-checkpoint` or `nop-stream-flink` exists.
  ```
  # module-groups.md (lines 22-23):
  | 流处理引擎 | nop-stream/ | ...、nop-stream-checkpoint（检查点存储抽象）、nop-stream-flink（Flink API 兼容层）
  # POM reality: no such modules
  ```
- **Severity**: P1 — **Material**: documented module map is inaccurate. Developers reading the docs will expect `nop-stream-checkpoint` (a checkpoint storage abstraction) and `nop-stream-flink` (a Flink API compatibility layer) to exist as independently buildable modules. This causes confusion and erodes trust in the documentation baseline.
- **Status**: The docs likely describe planned modules from the `completion-roadmap.md` or `component-roadmap.md`, but present them as current reality in `module-groups.md` (which is described as the authoritative module grouping reference).
- **Risk**: Misleads contributors about the project's current module boundary; wastes time searching for non-existent code.
- **Recommendation**: Either (a) remove these entries from `module-groups.md` and `INDEX.md` if they are no longer planned, or (b) add an explicit "(planned)" qualifier, or (c) create the modules if they are still in the roadmap. Fix the discrepancy in the source-of-truth module map.
- **Confidence**: Certain
- **False positive exclusion**: Factually wrong documentation — no interpretation ambiguity.

---

### [P1] `nop-stream-flow` has zero test coverage

- **File**: `nop-stream/nop-stream-flow/src/test/java/` — directory does not exist (no test sources at all)
- **Evidence**:
  ```
  ls nop-stream/nop-stream-flow/src/
  main/  (61 Java files — 30 hand-written + 31 generated _gen/)
  ```
  No `test/` directory. Zero test files for the entire module. The hand-written files are 9-line retention stubs that extend generated `_gen/` classes, but they compose into the stream-model DSL pipeline that other modules depend on.
- **Severity**: P1 — **Material**: a module with 30 source files and dependencies from other submodules (`nop-stream-cep`, `nop-stream-flow` itself depends on `nop-stream-core`) has zero tests. Changes to these model classes (e.g., `StreamModel`, `StreamSourceModel`, `StreamWindowModel`) can break deserialization or DSL compilation without any test feedback.
- **Status**: Pre-existing condition, not introduced by the Flink comparison mission.
- **Risk**: Silent regressions in the stream pipeline model (used by `StreamModelFingerprint`, `StreamRequirementValidator`, etc.) are untested.
- **Recommendation**: At minimum, add a basic smoke test that (a) loads an example `.stream.xml` via XDSL loader, (b) verifies `StreamModel` structural integrity, and (c) runs `StreamModelFingerprint` fingerprint consistency. Place in `nop-stream-flow/src/test/java/io/nop/stream/flow/model/`.
- **Confidence**: Certain
- **False positive exclusion**: Zero-test modules are always a risk. This module is not an app-starter or config-only module — it has hand-written Java code.

---

### [P1] Pre-existing session window merge bug — 4 tests disabled

- **Files**:
  - `nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/operators/windowing/TestSessionWindowAdvancedMerge.java` — entire class `@Disabled("WindowOperator session window merge not yet compatible with EventTimeSessionWindows")`
  - `nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/operators/windowing/TestSessionWindowWithPeriodicWatermark.java` — `testMultiKeyIndependentSessions()` disabled
- **Evidence**: 4 `@Disabled` tests from the plan's deferred-bug list. These are pre-existing `WindowOperator` bugs where `mergeWindowContents()` does not properly support EventTimeSessionWindows. The old `WindowAggregationOperator` (now deleted in Phase 2) was masking this bug.
- **Severity**: P1 — **Material**: live defect in `WindowOperator.mergeWindowContents()`. Session windows are a standard window type; support is incomplete. The plan noted this as "watch-only residual" but the plan closure acknowledged it as a follow-up requiring a future plan.
- **Status**: Known and deferred by the plan, but still a P1 when judged against the code quality bar (session windows are not edge-case, they are a core window type).
- **Risk**: Any user pipeline using `EventTimeSessionWindows` with the new `WindowOperator` (which is the only path since `WindowAggregationOperator` was deleted) will produce incorrect results — windows won't merge.
- **Recommendation**: File a new plan to fix `WindowOperator.mergeWindowContents()` for `EventTimeSessionWindows`. Reference `TestSessionWindowAdvancedMerge` as the test harness to validate.
- **Confidence**: Certain
- **False positive exclusion**: This is a real defect, not a cosmetic issue. The plan already acknowledged it.

---

### [P2] `source-anchors.md` has zero nop-stream entries

- **File**: `docs-for-ai/04-reference/source-anchors.md` — full 193 lines searched, zero matches for "nop-stream" or any `nop-stream` class/interface name
- **Evidence**: `grep -i "nop-stream\|StreamOperator\|CheckpointCoordinator\|WindowOperator\|HeapInternalTimer" docs-for-ai/04-reference/source-anchors.md` → no results
- **Severity**: P2 — **Trivial/non-blocking**: the file is supposed to be the anchor registry for implementation references, but its absence for `nop-stream` (45k+ lines, >370 source files) is a maintenance gap. Does not block users or cause incorrect behavior.
- **Status**: Pre-existing.
- **Risk**: Readers cannot navigate from docs to code anchors; source-anchors.md is less useful as a cross-reference.
- **Recommendation**: Add anchor entries for the major nop-stream classes: `HeapInternalTimerService`, `JobGraphGenerator`, `ForwardPartitioner`, `WindowOperator`, `PendingCheckpoint`, `CheckpointCoordinator`, `CheckpointMetrics`, `StreamModel`, `WindowOperatorBuilder`, etc.
- **Confidence**: Certain
- **False positive exclusion**: Pure documentation oversight — no behavioral impact.

---

### [P2] `CheckpointMetricsSnapshot.toString()` omits `failureCause`

- **File**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/metrics/CheckpointMetricsSnapshot.java:80-90`
- **Evidence**:
  ```java
  @Override
  public String toString() {
      return "CheckpointMetricsSnapshot{" +
              "numCompletedCheckpoints=" + numCompletedCheckpoints +
              ", numFailedCheckpoints=" + numFailedCheckpoints +
              ", numAbortedCheckpoints=" + numAbortedCheckpoints +
              ", latestCheckpointSize=" + latestCheckpointSize +
              ", latestCheckpointDuration=" + latestCheckpointDuration +
              ", totalStateSize=" + totalStateSize +
              ", lastCheckpointTimestamp=" + lastCheckpointTimestamp +
              '}';
  }
  ```
  The `failureCause` field (line 26) is not included.
- **Severity**: P2 — **Trivial**: `toString()` is used for logging/debugging; the omission makes debugging failures harder because the failure cause is not visible in log output. Does not affect correctness.
- **Status**: Introduced in Phase 4 when `failureCause` was added but `toString()` was not updated.
- **Risk**: Debugging checkpoint failures requires separate inspection of `getFailureCause()`. Not actionable during normal monitoring.
- **Recommendation**: Add `", failureCause='" + failureCause + '\''` to the `toString()` output.
- **Confidence**: Certain
- **False positive exclusion**: Pure omission — no behavioral impact.

---

## 3. Reinforced Findings (implemented correctly, reviewed)

The following mission deliverables were audited and confirmed correct:

| Plan Phase | Deliverable | Verdict | Evidence |
|---|---|---|---|
| Phase 1 | `HeapInternalTimerService` no silent no-op | ✅ Pass | All 4 public methods (`registerProcessingTimeTimer`, `deleteProcessingTimeTimer`, `fireProcessingTimeTimers`, `advanceWatermark`) have implementations. No empty bodies. |
| Phase 1 | `advanceWatermark()` uses `while + pollFirstEntry()` | ✅ Pass | `HeapInternalTimerService.java:146-152` — correct pattern. |
| Phase 1 | `TimerServiceManager.fireProcessingTimeTimers()` exists | ✅ Pass | `TimerServiceManager.java:42-50` — iterates all services, fires processing time timers. |
| Phase 2 | `WindowAggregationOperator` deleted | ✅ Pass | Full grep search confirms no `WindowAggregationOperator.java` or `WindowAggregationState.java` exists. |
| Phase 2 | Old test coverage migrated to new `WindowOperator` | ✅ Pass | `TestWindowOperatorIntegration`, `TestWindowOperatorBehavior`, `TestWindowOperatorCorrectness`, `TestWindowOperatorUnificationE2E` etc. cover window aggregation semantics. |
| Phase 3 | `ForwardPartitioner` class exists | ✅ Pass | `io.nop.stream.core.graph.ForwardPartitioner` — marker `IPartitioner` impl. |
| Phase 3 | `canChain()` checks `ForwardPartitioner` | ✅ Pass | `JobGraphGenerator.java:268`: `!(edge.getPartitioner() instanceof ForwardPartitioner)`. |
| Phase 3 | `StreamOperatorFactory.isChainable()` default method | ✅ Pass | `StreamOperatorFactory.java:51-53`: `default boolean isChainable() { return true; }`. |
| Phase 3 | `canChain()` checks `isChainable()` | ✅ Pass | `JobGraphGenerator.java:296-298`: `factory != null && !factory.isChainable()`. |
| Phase 3 | Chain tests exist | ✅ Pass | `TestJobGraphGenerator.java` covers null/ForwardPartitioner/isChainable() scenarios. |
| Phase 4 | `PendingCheckpoint.Status.FAILED` exists | ✅ Pass | `PendingCheckpoint.java:30`: `RUNNING, COMPLETED, ABORTED, FAILED`. |
| Phase 4 | State transition validation | ✅ Pass | `isValidTransition()` at line 46-54; `checkValidTransition()` at line 56-61. |
| Phase 4 | `CheckpointMetrics.failureCause` field | ✅ Pass | `CheckpointMetrics.java:24`: `volatile String failureCause`. `recordFailure()` at line 85-88. `CheckpointMetricsSnapshot` includes it at line 26. |
| Phase 4 | Failure paths set `failureCause` in `CheckpointCoordinator` | ✅ Pass | Lines 259, 276: `metrics.recordFailure(...)` in storage/EpochManifest failure paths. |

---

## 4. Summary Statistics

| Metric | Value |
|---|---|
| Dimensions examined | 8 (01, 02, 03, 07, 08, 09, 12, 14, 15, 16, 18, 19, 21) |
| Total findings | 9 |
| P1 (material) | 6 |
| P2 (trivial) | 2 |
| Reinforced (pass) | 14 |
| P0 (blocking) | 0 |

---

## 5. Prioritized Remediation Recommendations

| Priority | Finding | Effort | Owner |
|---|---|---|---|
| 1 | Fix `CheckpointCoordinator.abortPendingCheckpoint` metrics — use separate counter for aborts | 1 file, ~3 lines | Checkpoint owner |
| 2 | Fix `PendingCheckpoint.fail()` — add `ERR_STREAM_CHECKPOINT_FAILED` error code | 2 files, ~5 lines | Checkpoint owner |
| 3 | Create `TestProcessingTimeWindowIntegration` — end-to-end processing time window test | 1 file, ~80 lines | Test owner |
| 4 | Add `nop-stream-flow` smoke test — XDSL load + fingerprint consistency | 1 file, ~40 lines | Model owner |
| 5 | Fix `docs-for-ai/` module map — qualify non-existent modules or remove | 2 files, ~4 lines | Docs owner |
| 6 | Fix session window merge bug in `WindowOperator.mergeWindowContents()` | New plan (deferred from mission) | Window operator owner |
| 7 | Add `failureCause` to `CheckpointMetricsSnapshot.toString()` | 1 file, 1 line | Checkpoint owner |
| 8 | Add nop-stream anchors to `source-anchors.md` | 1 file, ~15 lines | Docs owner |

---

## 6. Audit Blind Spots

- **No end-to-end distributed test was run** — the `./mvnw test` baseline reports 1381 passing tests, but distributed integration tests in `nop-stream-runtime` (e.g., `TestDistributedExactlyOnce`) were not individually verified.
- **No performance profiling** — the audit is structural/correctness-only. The `while + pollFirstEntry()` pattern in `advanceWatermark()` is algorithmically correct but no microbenchmark was run to verify throughput improvement over the old `headMap` approach.
- **No third-party dependency audit** — `nop-stream-core` depends on `nop-commons` and `nop-core` transitively; no CVEs or API-compatibility checks were performed.
- **`IWindowOperatorFactory` auto-discovery path** — the SPI/`ServiceLoader` wiring between `WindowOperatorBuilder` and `WindowOperatorFactoryImpl` was not traced end-to-end through META-INF/services.

---

<AI_STEP_RESULT>issues</AI_STEP_RESULT>
