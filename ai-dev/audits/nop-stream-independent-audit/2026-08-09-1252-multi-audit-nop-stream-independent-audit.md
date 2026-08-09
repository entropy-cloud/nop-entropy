> Audit Status: planned
> Audit Type: multi-dimensional
> Mission: nop-stream-independent-audit
> Disposition: P1 CONN-01 → remediation plan `ai-dev/plans/2026-08-09-1253-1-nop-stream-2pc-sink-fail-fast-parallel-commit.md` (closes the silent-data-loss path via a planning-time fail-fast gate; full subtask-qualified keying deferred to an explicit successor). P2 (CONN-02..05, CEP-01..05, API-01/02, TEST-01/02) and P3 (DOC-01, CEP-06, NAME-01) → follow-up backlog at `ai-dev/backlog/nop-stream-independent-audit-roadmap.md §Follow-up Backlog`.

# Multi-Dimensional Audit — `nop-stream/` (code, config, tests, public contracts)

- **Audit date**: 2026-08-09
- **Scope**: live code under `nop-stream/` (10 submodules) — `src/main` + `src/test`, configs (`pom.xml`, `_vfs`, `_module`, beans), and the public contract surface (exports, API, SPI, declared guarantees). Cross-referenced against the architecture docs (`ai-dev/design/nop-stream/`, `docs-for-ai/04-reference/source-anchors.md`, `docs-for-ai/01-repo-map/module-groups.md`).
- **Methodology**: per `ai-dev/skills/deep-audit-prompts.md` — dimensions 01/02/03 (architecture & boundary), 04/05/06 (model/codegen/delta), 07/08/09 (service/IoC/errors), 10/11 (XDSL/XMeta), 13 (security/guarantees), 16/21 (test coverage & effectiveness), 18/19/20 (doc-code / naming / cross-module contract). Dimensions 12/14 (GraphQL/async-txn) are N/A: nop-stream is a streaming engine with no GraphQL/BizModel surface. Findings were independently verified against live code (file + line + evidence) before inclusion; the headline P1 was verified end-to-end across sink → coordinator dispatch → design contract.
- **Baseline facts**: 84529 LOC main; 34 `_*.java` in 2 `_gen/` dirs (all valid codegen output, no hand-edits); 0 `_*.xml`; 0 `@Public`/`@PublicEvolving` annotation types defined anywhere (only `@Internal`, 103 uses); internal dep graph is a strict tree rooted at `nop-stream-core`; 0 imports of `org.apache.flink`/`io.netty`/`org.apache.zookeeper` (vision §三 constraints 7/8 honored).

---

## Severity summary

| Severity | Count | Drives remediation? |
|---|---|---|
| P0 | 0 | — |
| P1 | 1 | yes |
| P2 | 13 | backlog triage |
| P3 | 3 | record only |

---

## Findings

### [P1] CONN-01 — 2PC sink idempotency keyed on job-global `{epochId}` (not subtask-qualified) → silent data loss at sink parallelism > 1; exactly-once label unqualified

*Justification: real correctness defect (silent data loss) + core contract drift vs the documented transaction-identity format; latent because the default/proved path is parallelism=1, so P1 rather than P0 (does not break the bounded, e2e-proved parallelism=1 envelope).*

- **Files**:
  - `nop-stream/nop-stream-connector-jdbc/src/main/java/io/nop/stream/connector/jdbc/JdbcTwoPhaseCommitSink.java:74-75,219-233,300-311,340-355` (ledger PK = single-column `epoch_id`)
  - `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/file/FileTwoPhaseCommitSink.java:150-196,258-259,274` (manifest key = `String.valueOf(epochId)`, output file `epoch-{epochId}.txt`)
  - dispatch: `nop-stream/nop-stream-runtime/.../execution/GraphModelCheckpointExecutor.java:636-674` (registers every subtask's sink UDF as a separate `CheckpointParticipant`) + `.../checkpoint/CheckpointCoordinator.java:1091-1096` (`notifyParticipantsFinishCommit` calls `participant.finishCommit(checkpointId)` for every participant with the **same** job-global `checkpointId`)
- **Evidence (JDBC ledger schema + guard)**:
  ```java
  // JdbcTwoPhaseCommitSink.java:300-311 — PK is single-column epoch_id, no subtask column
  sb.append(d.escapeSQLName(LEDGER_EPOCH_COL)).append(" BIGINT NOT NULL, ");
  ...
  sb.append("PRIMARY KEY (").append(d.escapeSQLName(LEDGER_EPOCH_COL)).append(")");
  // :218-225 — guard fires on epoch_id alone, then silently returns
  if (ledgerExists(connection, checkpointId)) {
      LOG.info("Epoch {} already recorded in ledger — skipping data write (idempotent re-commit)", checkpointId);
      connection.commit(); committed = true; return;     // subtask-1 batch silently dropped
  }
  ```
  ```java
  // FileTwoPhaseCommitSink.java:258-259, 274
  String manifestKey(long epochId) { return String.valueOf(epochId); }   // not subtask-qualified
  ... return outputDirPath.resolve("epoch-" + epochId + ".txt");          // collides across subtasks
  ```
- **Current state**: Both 2PC sinks key their idempotency guard (ledger row / manifest entry / output filename) on the job-global `checkpointId` (`epochId`) only — with no `operatorId`/`subtaskIndex` component. With sink parallelism > 1, each parallel subtask is a distinct participant that receives `finishCommit(checkpointId)` with the **same** epoch. Whichever subtask commits first inserts the ledger row / manifest entry; the next subtask sees the guard hit and **silently returns**, dropping its in-memory batch (JDBC) or its output (File). No exception is raised (the JDBC path logs at `INFO` and returns; the File path removes the pending entry and returns). The guard is correct for *same-subtask* re-commit on recovery but wrong across *different* subtasks.
- **Risk**: exactly-once silently degrades to **data loss** in a configuration the graph path explicitly supports (`graph-model-design.md:350` "图模型路径支持 parallelism > 1 的拓扑结构"). `STRM-033`/`STRM-036` (docs-for-ai) declare "exactly-once output" with **no** parallelism=1 qualification. This is a direct drift from the documented transaction-identity contract:
  - `ai-dev/design/nop-stream/comparison.md:404`: documented transaction identity = `{jobId}:{pipelineId}:{operatorId}:{subtaskIndex}:{epochId}` (includes `subtaskIndex`).
  - `ai-dev/design/nop-stream/checkpoint-design.md:1079`: "sink pending transaction | 不允许跨 subtask 静默迁移".
  The implementation uses only `{epochId}`. There is no fail-fast guard that rejects sink parallelism > 1.
- **Mitigation context**: the default Transformation parallelism is 1 (`graph-model-design.md:350`) and the only E2E-proved paths (`TestE2EJdbcTwoPhaseCommitSink`, `TestFileTwoPhaseCommitSink`) exercise a single sink instance. Hence this is latent within the proved envelope, not active in the default path — which is why it is graded P1 and not P0.
- **Suggested fix**: make the idempotency key subtask-qualified to match the documented transaction identity — e.g. ledger PK `(operator_id, subtask_index, epoch_id)` and manifest key `"{operatorId}-{subtaskIndex}-{epochId}"`, with the sink receiving its stable `operatorId` + `subtaskIndex` (already available via `TaskLocation`/`VertexPlan`). Alternatively, fail-fast at planning time when a `TWO_PHASE_COMMIT` sink runs at parallelism > 1 until the key is qualified. Either closes the contract drift and removes the silent-loss path.
- **Confidence**: certain (verified sink schema + dispatch loop + design contract).
- **False-positive exclusion**: not a Nop-platform-convention false positive. The `participant.finishCommit(checkpointId)` dispatch is verifiably per-subtask (`GraphModelCheckpointExecutor.java:636-674` iterates `getSubtasks(vertexId)`), and the design's own transaction-id format includes `subtaskIndex`, so the collision is structurally real, not a misreading of single-instance semantics.
- **Review status**: open

---

### [P2] CONN-02 — `BatchConsumerSinkFunction` declares `IDEMPOTENT` but provides no idempotency mechanism; in-memory buffer is not checkpointed

*Justification: guarantee-label over-claim on a connector the validator uses for consistency gating; real but only material in AT_LEAST_ONCE pipelines.*

- **File**: `nop-stream/nop-stream-connector-batch/src/main/java/io/nop/stream/connector/batch/BatchConsumerSinkFunction.java:47-150`
- **Evidence**:
  ```java
  public class BatchConsumerSinkFunction<R> implements SinkFunction<R>, AutoCloseable {   // NOT CheckpointParticipant / ICheckpointedFunction
      private void flush() { ... consumer.consume(new ArrayList<>(buffer), chunkContext); buffer.clear(); }
      @Override public SinkConsistencyCapability getSinkConsistency() {
          return SinkConsistencyCapability.IDEMPOTENT;     // over-claim: pure pass-through, no dedupe
      }
  }
  ```
- **Current state**: pure pass-through to a user `IBatchConsumer`; up to `batchSize-1` records live only in an unsnapshotted in-memory buffer; on task failure the buffered records are lost; on replay already-flushed records are re-delivered. The sink provides no dedupe. `IDEMPOTENT` is ordered stronger than `AT_LEAST_ONCE` (`SinkConsistencyCapability` + `TestConnectorConsistencyCapability` codify the ordering).
- **Risk**: a consumer trusting the `IDEMPOTENT` label in an `AT_LEAST_ONCE` pipeline gets duplicates/loss. The `STRICT_EXACTLY_ONCE` path is protected (`StreamRequirementValidator` rejects `IDEMPOTENT` for strict pipelines), so no false exactly-once — hence P2.
- **Suggested fix**: either downgrade the label to `AT_LEAST_ONCE`, or implement dedupe + checkpoint the buffer.
- **Confidence**: certain.
- **Review status**: open

### [P2] CONN-03 — `BatchLoaderSourceFunction` implements `ReplayableSourceFunction` but `seek()`/offset are unused → replay contract not honored

*Justification: implemented-interface advertises a capability the run() loop ignores; label itself is honest so impact is bounded.*

- **File**: `nop-stream/nop-stream-connector-batch/src/main/java/io/nop/stream/connector/batch/BatchLoaderSourceFunction.java:33-105`
- **Evidence**:
  ```java
  while (running) {
      List<S> batch = loader.load(batchSize, chunkContext);   // always from beginning; ignores currentOffset
      ...
      for (S item : batch) { ctx.collect(item); currentOffset++; }
  }
  @Override public long getCurrentOffset() { return currentOffset; }   // snapshotted by operator
  @Override public void seek(long offset) { this.currentOffset = offset; }   // NO effect on loader.load()
  ```
- **Current state**: the operator does snapshot `currentOffset` and calls `seek` on restore, but `run()` never uses `currentOffset` to position `loader.load()`; recovery re-reads from the start. `getSourceConsistency()` honestly returns `AT_LEAST_ONCE`, so this is an interface-contract drift, not a label over-claim.
- **Risk**: hazard only if a caller relies on `ReplayableSourceFunction` positioning semantics.
- **Suggested fix**: either honor `seek` in `load`, or stop implementing `ReplayableSourceFunction`.
- **Confidence**: certain.
- **Review status**: open

### [P2] CONN-04 — `DebeziumCdcSourceFunction.source` is non-transient but holds a non-`Serializable` type

*Justification: latent serialization hazard inconsistent with the class's own documented transient/non-transient split.*

- **File**: `nop-stream/nop-stream-connector-debezium/src/main/java/io/nop/stream/connector/debezium/DebeziumCdcSourceFunction.java:62-76`
- **Evidence**:
  ```java
  private DebeziumConfig config;                              // non-transient, Serializable (documented)
  private transient volatile CountDownLatch completionLatch;  // transient (documented)
  private volatile DebeziumMessageSource source;              // NOT transient; DebeziumMessageSource is NOT Serializable
  private transient NopStreamOffsetBackingStore offsetStore;  // transient (documented)
  ```
- **Current state**: the STRM-035 contract deliberately documents `config` non-transient and `offsetStore` transient, but `source` (a live engine handle, `DebeziumMessageSource` does not `implements Serializable`) is left non-transient. In the normal lifecycle `source` is null at serialization time (created in `run()`, cleared in its `finally`), so this is latent.
- **Risk**: if the function is ever serialized while `run()` is active (task migration / certain redeploy paths), serialization throws `NotSerializableException`.
- **Suggested fix**: mark `source` `transient` to match the established split.
- **Confidence**: certain.
- **Review status**: open

### [P2] CONN-05 — `NopStreamOffsetBackingStore` uses a process-wide static registry keyed only by connector name

*Justification: conditional exactly-once hazard for same-name concurrent consumers; CDC sources are conventionally parallelism=1 but nothing enforces it.*

- **File**: `nop-message/nop-message-debezium/src/main/java/io/nop/message/debezium/engine/NopStreamOffsetBackingStore.java:56-57,78-87,96-102`
- **Evidence**:
  ```java
  private static final ConcurrentHashMap<String, ConcurrentHashMap<ByteBuffer, ByteBuffer>> REGISTRY = new ConcurrentHashMap<>();
  public static NopStreamOffsetBackingStore forConnector(String connectorName) {
      ConcurrentHashMap<ByteBuffer, ByteBuffer> shared = REGISTRY.computeIfAbsent(connectorName, k -> new ConcurrentHashMap<>());
      ...
      store.data = shared;     // source-function instance and engine-created instance share the SAME map
  }
  ```
- **Current state**: deliberate bridge for Debezium 2.4.0's reflection-based store instantiation (documented). Cross-JVM recovery is correct (`initializeState` re-populates an empty registry from the checkpoint). But within one JVM, two consumers sharing a connector name (parallelism > 1 on the CDC source, or two pipelines reusing a name) share/overwrite each other's offsets. `REGISTRY` is never cleaned (only the test helper `clearConnector`).
- **Risk**: broken exactly-once for same-name concurrent consumers; safe only under parallelism=1 + unique connector names, which is unenforced.
- **Suggested fix**: namespace the registry by a per-attempt/per-source identity, or fail-fast on duplicate connector-name registration.
- **Confidence**: certain.
- **Review status**: open

---

### [P2] CEP-01 — `SharedBuffer.registerEvent` overflow guard bypassed when the initial `id == Integer.MAX_VALUE`

*Justification: defensive throw that the logic can actually bypass; practically unreachable but logically leaky.*

- **File**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/sharedbuffer/SharedBuffer.java:236-260`
- **Evidence**:
  ```java
  EventId registerEvent(V value, long timestamp) {
      Integer id = eventsCount.get(timestamp);
      if (id == null) id = 0;
      EventId eventId = new EventId(id, timestamp);
      while (eventsBufferCache.asMap().containsKey(eventId) || hasEventInBuffer(eventId)) {
          id++;
          if (id == Integer.MAX_VALUE) { throw new StreamException(ERR_CEP_NFA_SHARED_BUFFER_ACCESS_FAILED)...; }
          eventId = new EventId(id, timestamp);
      }
      ...
  }
  ```
- **Current state**: the guard only fires *after* `id++` reaches MAX_VALUE; if `eventsCount.get(timestamp)` already returns MAX_VALUE, the `id++` wraps to MIN_VALUE, the equality check fails, and the loop iterates the whole int range. The error message claims protection not fully provided.
- **Risk**: practically unreachable (~2³¹ events at one timestamp), but the guard is logically leaky.
- **Suggested fix**: check for overflow *before* the increment / use `Math.addExact`.
- **Confidence**: certain.
- **Review status**: open

### [P2] CEP-02 — Public `NFA` constructor accepts null `windowTimes`; `advanceTime` dereferences without guard

*Justification: public type fails to enforce its own invariant; the documented NFACompiler path is safe.*

- **File**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/NFA.java:111-120,279-294`
- **Evidence**:
  ```java
  public NFA(..., final Map<String, Long> windowTimes, ...) {
      ...
      this.windowTimes = windowTimes;          // no null check, no defensive copy
  }
  // advanceTime:
  boolean isTimeoutForPreviousEvent =
          windowTimes.containsKey(currentStateName)   // NPE if windowTimes == null
                  && isStateTimedOut(...);
  ```
- **Current state**: `NFACompiler.NFAFactoryImpl.createNFA()` always supplies a non-null map, so the documented entry path is safe; but `NFA` is `public` and the constructor does not enforce the invariant.
- **Risk**: NPE if a caller bypasses `NFACompiler`.
- **Suggested fix**: null-check + empty-map default in the constructor.
- **Confidence**: certain.
- **Review status**: open

### [P2] CEP-03 — `CepOperator.copyForSubtask()` drops `stateBackend`; subtask copies silently fall to `MemoryKeyedStateBackend` if not re-injected

*Justification: fragile lifecycle contract with no test coverage; mirrors a cross-cutting pattern (WindowOperator) so not CepOperator-specific.*

- **File**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java:233-243,265-269`
- **Evidence**:
  ```java
  @Override public CepOperator<IN, KEY, OUT> copyForSubtask() {
      return new CepOperator<>(inputSerializer, isProcessingTime, nfaFactory, comparator,
              afterMatchSkipStrategy, getUserFunction(), lateDataOutputTag);   // stateBackend NOT propagated
  }
  // open() fallback:
  LOG.warn("CepOperator opened without a configured state backend; falling back to MemoryKeyedStateBackend...");
  keyedStateStore = new MemoryKeyedStateBackend<>(Object.class);
  ```
- **Current state**: the constructor-based copy propagates only 7 fields; `stateBackend` is not copied. `TestCepOperatorSubtaskCopy` only checks `assertNotSame` + user-function sharing, not `stateBackend` propagation. The framework's normal flow injects `stateBackend` per-subtask after copy, so the default path is fine, but the contract is fragile and undocumented.
- **Risk**: a deployment flow that injects `stateBackend` on the template and relies on `OperatorChain.deepCopy()` without re-injecting lands every CepOperator subtask on the Memory fallback → checkpoint consistency not guaranteed.
- **Suggested fix**: document the re-injection requirement and/or add a regression test that `stateBackend` survives `copyForSubtask`.
- **Confidence**: likely (mirrors WindowOperator pattern; framework path assumed correct).
- **Review status**: open

### [P2] CEP-04 — Duplicated dangling-cleanup logic between `onEventTime` and `onProcessingTime` (drift hazard + local `nfa` shadowing)

*Justification: not a current bug but a maintainability/future-correctness hazard with subtle local-variable shadowing.*

- **File**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java:539-568` (onEventTime) and `:599-628` (onProcessingTime)
- **Evidence**: two ~30-line near-copies of the "single partial match + empty completed matches → clear" cleanup. In `onProcessingTime` a local `NFAState nfa = getNFAState()` shadows the instance field `nfa`, requiring `this.nfa.getWindowTimes()` to disambiguate.
- **Current state**: both paths are behaviorally equivalent today.
- **Risk**: a future fix to one copy (timeout calc, multi-partial-match handling, refcount release) is easy to miss in the other.
- **Suggested fix**: extract a shared `cleanupDanglingPartialMatches(timerContext)` helper.
- **Confidence**: certain.
- **Review status**: open

### [P2] CEP-05 — `SharedBufferCacheConfig.cacheStatisticsInterval` field is dead; `CepOperator` re-reads the config directly

*Justification: config-propagation contract split between two sources for the same logical parameter.*

- **Files**: `nop-stream/nop-stream-cep/.../configuration/SharedBufferCacheConfig.java:31-61` + `.../operator/CepOperator.java:276,356-369`
- **Evidence**:
  ```java
  // SharedBufferCacheConfig exposes getCacheStatisticsInterval(), but SharedBuffer only reads slots:
  partialMatches = new SharedBuffer<>(keyedStateStore, inputSerializer, new SharedBufferCacheConfig());
  // CepOperator ignores the bean for the interval and re-reads the global config:
  private void registerCacheStatisticsTimer() {
      java.time.Duration interval = NopCepConfigs.CEP_CACHE_STATISTICS_INTERVAL.get();
      ...
  }
  ```
- **Current state**: `cacheStatisticsInterval` is never read by anyone; `CepOperator` bypasses the bean and re-reads `NopCepConfigs`. The 3-arg public constructor of the bean permits a custom interval that would be silently ignored.
- **Risk**: if a user constructs `SharedBufferCacheConfig` with a non-default interval, slots are honored but the timer fires at the global default.
- **Suggested fix**: have `CepOperator` read the interval from the injected `SharedBufferCacheConfig`, or remove the dead field.
- **Confidence**: certain.
- **Review status**: open

---

### [P2] API-01 — `@Internal` is applied to user-implemented function interfaces (incl. the 2PC-sink / checkpointed-source base classes) while siblings are unannotated; no `@Public` exists

*Justification: API-stability annotation policy is incoherent and contradicts the documented user-extensibility surface; no runtime breakage.*

- **Files**:
  - `nop-stream-core/.../common/functions/ReduceFunction.java:31`, `WindowFunction.java:37`, `AggregateFunction.java:81`, `StreamFunction.java:19`
  - `.../functions/sink/TwoPhaseCommitSinkFunction.java:24` (base class of `JdbcTwoPhaseCommitSink` / `FileTwoPhaseCommitSink` — STRM-033/036)
  - `.../functions/source/CheckpointedSourceFunction.java:29` (base class of `DebeziumCdcSourceFunction` — STRM-035)
  - NOT annotated: `MapFunction`, `FilterFunction`, `FlatMapFunction`, `ProcessFunction`, `SinkFunction`, `SourceFunction`, `KeySelector`, `WatermarkStrategy`, `DataStream`, `KeyedStream`, `WindowedStream`, `StreamExecutionEnvironment`
- **Evidence**:
  ```java
  // ReduceFunction.java:29-32 — Javadoc tells users to implement it, type is marked internal
   * @Internal
   */
  @Internal
  public interface ReduceFunction<T> extends StreamFunction {
  ```
  `@Internal` is defined in `nop-api-core` (`io.nop.api.core.annotations.core.Internal`) as "应用程序不应该直接调用". **No `@Public`/`@PublicEvolving` annotation type exists anywhere in the repo** (grep confirms 0 hits).
- **Current state**: there is no governing policy separating the stable user API from internal SPI. `@Internal` is applied inconsistently — including onto the very base classes the documented exactly-once sinks/sources extend (`TwoPhaseCommitSinkFunction`, `CheckpointedSourceFunction`), and onto `StreamFunction` which is the supertype of five *unannotated* user interfaces (so every "public" function interface inherits an "internal" marker). `AggregateFunction`'s own Javadoc contains a full user-coding example while being `@Internal`.
- **Risk**: users/tooling that honor `@Internal` get a contradictory signal about what is callable/extendable; the annotation carries no reliable meaning for nop-stream's surface.
- **Suggested fix**: adopt a stability policy — either introduce/use `@Public` for the stable user API (function interfaces, `DataStream` family, the 2PC/`CheckpointedSource` base classes users extend) and remove `@Internal` from those, or document that nop-stream follows an implicit "everything in `common.functions` + `datastream` + connector sink/source bases is public" convention and align the annotations to it.
- **Confidence**: certain.
- **Review status**: open

### [P2] API-02 — Internal graph IR (`StreamGraph`, `JobGraph`, `Transformation`) lack `@Internal` while their generators are `@Internal`

*Justification: same root annotation-policy gap as API-01, opposite direction (internal types unmarked); low impact.*

- **Files**: `.../graph/StreamGraph.java:44`, `.../jobgraph/JobGraph.java:59`, `.../transformation/Transformation.java:23` (none annotated) vs `.../graph/StreamGraphGenerator.java:80`, `.../jobgraph/JobGraphGenerator.java:79` (both `@Internal`).
- **Current state**: `StreamGraph`/`JobGraph`/`Transformation` are clearly internal graph IR (not in any user doc) yet lack `@Internal`.
- **Risk**: low (users rarely touch these directly); folded into the same annotation-policy fix as API-01.
- **Confidence**: certain.
- **Review status**: open

---

### [P2] TEST-01 — `testMergeTypeIncompatibilityThrowsException` never asserts the exception its name/Javadoc promise (the "Bug 3" fast-fail guard has no protection)

*Justification: a named correctness invariant ("type incompatibility throws") is advertised but unasserted; reverting the guard keeps the test green.*

- **File**: `nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/operators/windowing/TestWindowOperatorCorrectness.java:489-533`
- **Evidence**:
  ```java
  @Test
  void testMergeTypeIncompatibilityThrowsException() throws Exception {
      // This test verifies Bug 3 fix: type incompatibility during merge
      // causes a fast failure (exception), not silent data loss.
      ...
      try {
          // Send elements that cause merging - should NOT throw
          operator.processElement(new StreamRecord<>(10, 10));
          ...
          assertEquals(1, output.size());
          assertEquals("sum=60", output.getElements().get(0));   // only happy-path asserted
  ```
- **Current state**: the test's stated purpose is the type-incompatibility fast-fail guard, but the body runs only the happy path; there is no `assertThrows` and no try/catch asserting the exception type.
- **Risk**: if the `mergeWindowContents` fast-fail guard were deleted (regression to silent data loss), this test still passes → illusory coverage for a correctness guard.
- **Suggested fix**: add a case that actually feeds incompatible merge types and asserts the exception; or rename the test to reflect what it verifies.
- **Confidence**: certain.
- **Review status**: open

### [P2] TEST-02 — `testTerminateDrainTriggersTerminalCheckpoint` / `testTerminateSuspendTriggersSavepoint` only assert `isRunning()==false`; never verify the `TERMINAL_SAVEPOINT` barrier

*Justification: the DRAIN/SUSPEND terminal-checkpoint promise is unasserted; a sibling test in the same file proves the assertion is known and feasible.*

- **File**: `nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/coordinator/TestJobCoordinator.java:320-358`
- **Evidence** (DRAIN test, 320-343; the comment admits the check is missing):
  ```java
  coordinator.terminate(JobTerminationMode.DRAIN);
  assertFalse(coordinator.isRunning());
  // The mock RPC service should have received at least one barrier
  // For DRAIN, the barrier type should be TERMINAL_SAVEPOINT
  // ... the important thing is no crash
  ```
  Sibling `testTerminateExportSavepointContinuesRunning` (same file, 397-415) **does** verify it:
  ```java
  assertNotNull(mockRpcService.lastBarrier.get(), "A savepoint barrier should have been sent to task managers");
  assertEquals(CheckpointType.EXPORTED_SAVEPOINT, mockRpcService.lastBarrier.get().getCheckpointType(), ...);
  ```
- **Current state**: both DRAIN/SUSPEND tests' only real assertion is `assertFalse(coordinator.isRunning())`. Neither verifies `mockRpcService.lastBarrier` nor its `TERMINAL_SAVEPOINT` type. Production `JobCoordinator.terminateDrain()`/`terminateSuspend()` (`JobCoordinator.java:1335-1384`) call `tryTriggerPendingCheckpoint(TERMINAL_SAVEPOINT)` then `sendBarrierToAllTaskManagers(barrier)` before awaiting the future.
- **Risk**: a regression that makes DRAIN/SUSPEND just `stop()` without the terminal savepoint (→ data loss on drain) would not be caught.
- **Suggested fix**: assert the barrier and its `TERMINAL_SAVEPOINT` type, mirroring the EXPORT_SAVEPOINT test.
- **Confidence**: certain.
- **Review status**: open

---

### [P3] DOC-01 — STRM-037 wording: `SharedBuffer` RemovalListener logs on `wasEvicted()` (SIZE/COLLECTED/EXPIRED), not strictly SIZE

*Justification: doc/code nuance; in practice equivalent because the caches configure no weak/expiry flags.*

- **File**: `nop-stream/nop-stream-cep/.../nfa/sharedbuffer/SharedBuffer.java:154-170`
- **Current state**: STRM-037 says "RemovalListener only logs on SIZE eviction"; implementation logs on Guava's `wasEvicted()` equivalent. Since neither cache sets `weakKeys/weakValues/expireAfterWrite/expireAfterAccess`, `COLLECTED`/`EXPIRED` cannot fire, so observed behavior matches the doc's intent.
- **Suggested fix**: tighten the doc wording to "eviction (SIZE in current config)".
- **Review status**: open

### [P3] CEP-06 — `NFA.EventWrapper.close()` failure discards an already-computed successful match on release-time exception

*Justification: acceptable fail-forward CEP semantics; flagged for completeness.*

- **File**: `nop-stream/nop-stream-cep/.../nfa/NFA.java:236-251,574-580`
- **Current state**: try-with-resources: if `doProcess` returns a non-empty result but `releaseEvent` throws, the returned value is discarded and the release-time exception propagates. Refcount corruption would already be fatal; low probability.
- **Review status**: open

### [P3] NAME-01 — `NopCepErrors` constant names drift from their code strings / messages

*Justification: log-triage friction only; no runtime impact.*

- **File**: `nop-stream/nop-stream-cep/.../NopCepErrors.java:23-29`
- **Current state**: `ERR_CEP_NOT_CONDITION_DOES_NOT_SUPPORT_GROUP` ↔ code string `nop.err.cep.follow-not-does-support-group`; `ERR_CEP_PATTERN_PART_NOT_ALLOW_LOOP` ↔ message about back-reference, not loop.
- **Review status**: open

---

## Dimensions covered & outcome

| Dim | Name | Outcome |
|---|---|---|
| 01 | Dependency graph & module boundary | **clean** — strict tree rooted at `nop-stream-core`; only non-trivial edge `flow→cep`; 0 boundary violations; 0 forbidden imports (Flink/Netty/ZooKeeper). Runtime platform-WIRE deps (`nop-cluster-core`/`nop-rpc-core`/`nop-message-core`/`nop-dao`) are fully documented in `01-architecture-baseline.md §五`. |
| 02 | Module responsibility & file boundary | **clean** — 34 `_*.java` in 2 `_gen/` dirs are valid codegen (proper headers, no hand-edits); 0 `_*.xml`; `_vfs/` holds intentional hand-authored resources. `WindowOperator.java` is 2026 LOC (large but cohesive — single window-operator with trigger/evictor/merge/state). |
| 03 | API surface & contract consistency | **API-01, API-02** (annotation-policy gap). `execute()` routing + `DeploymentMode{LOCAL,DISTRIBUTED}` match the documented contract. |
| 05 | Codegen pipeline integrity | **clean** — `_gen/` outputs valid; source `.xdef` (`pattern.xdef`, `stream.xdef`) → handwritten thin subclasses → builder chain consistent. |
| 06 | Delta customization | **clean** — Delta fixtures under `nop-stream-flow` test resources use correct `x:extends`; no main-scope Delta abuse. |
| 08 | IoC & bean config | **clean** — only `nop-stream-runtime` has main-scope beans (`stream-control-rpc.beans.xml`, `stream-data-plane.beans.xml`) + the only `_module`; both files are the documented Stage 39/40 deployment scaffolds. No hand-edits to generated beans. |
| 09 | Error handling & error codes | **clean** — module exception classes `StreamException`/`StreamRuntimeException` extend `NopException`; `NopCepErrors`/`NopStreamErrors` use `ErrorCode.define()` + `.param()`; commit/abort paths are fail-loud (only best-effort temp cleanup is quiet). `NAME-01` (P3) is wording drift. |
| 10 | XDSL/XLang correctness | **clean** — `pattern.xdef`/`stream.xdef` schemas valid; builder ↔ model ↔ DSL consistent. |
| 11 | XMeta/BizModel alignment | **N/A** — nop-stream is a streaming engine with no GraphQL/BizModel surface. |
| 13 | Security & permission / guarantee labels | **CONN-01** (P1), **CONN-02..05** (P2). The `StreamRequirementValidator` enforces source≥`REPLAYABLE` / sink≥`TWO_PHASE_COMMIT` for `STRICT_EXACTLY_ONCE` pipelines, so labels are not cosmetic — which is exactly why CONN-01/02 matter. No SQL-injection / SSRF surface in connectors (parameterized JDBC via `IJdbcTemplate`; no URL host whitelisting). |
| 16/21 | Test coverage & effectiveness | **TEST-01, TEST-02** (P2). The other 10 critical-behavior tests examined (checkpoint recovery, coordinator, barrier propagation, state-backend snapshot/restore, timer restore, HA failover, window behavior) are strong with concrete value assertions and failure-path coverage; no P-7 hidden inter-test dependencies. |
| 18 | Doc-code consistency | **DOC-01** (P3). `STRM-001..037` source anchors verified present and pointing at the right classes (no missing/gap; STRM-034 is outside nop-stream in `nop-message-debezium`, by design). `module-groups.md` description matches the 10 live submodules. |
| 19 | Naming consistency | **NAME-01** (P3). |
| 20 | Cross-module contract consistency | **CONN-01** documents the cross-cutting transaction-identity drift (sink ↔ coordinator ↔ design `comparison.md:404`). |

**Verified-clean high-value areas** (checked, no finding): module dependency tree & boundary; vision §三 forbidden-import constraints; generated-file hygiene; IoC/`_module` registration; `execute()`/`DeploymentMode` routing; STRM-032 (CepOperator state-backend wiring) and STRM-037 (CEP cache primitives) both conform to docs; the core checkpoint/recovery/state-backend/windowing test suite is substantive.

---

## Audit blind spots / self-assessment

- **No live build/test run**: this pass is static (code/config/contract). `./mvnw test -pl nop-stream -am` was not executed in this session; the readiness record's 132 e2e-proved rows are taken at face value. A full green run would strengthen the TEST-01/TEST-02 claims (they are static-read findings, not run-confirmed).
- **Distributed/data-plane paths**: the cross-JVM RPC + data-plane wire-codec (`IDataPlaneWireCodec`, `MessageRpcServer`) were inspected at the contract level, not stress-tested; CONN-01 is the only finding that crosses into the distributed dispatch path, and it is configuration-conditional (sink parallelism).
- **RocksDB state backend** (`nop-stream-rocksdb`, 17 files): scanned for boundary/dependency only; deep incremental-snapshot/cleanup semantics not independently re-derived in this pass.
- **Fraud example** (`nop-stream-fraud-example`): treated as a demo, not audited for production correctness.
- **P1 grading note**: CONN-01 is graded P1 (latent within the proved parallelism=1 envelope) rather than P0 because the default Transformation parallelism is 1 (`graph-model-design.md:350`) and the only E2E-proved 2PC-sink paths use a single sink instance; it is, however, silent data loss in a supported configuration with no fail-fast guard, so a reviewer weighting "data loss" categorically may legitimately upgrade it to P0. Either grade drives remediation.

---

<AI_STEP_RESULT>issues</AI_STEP_RESULT>
