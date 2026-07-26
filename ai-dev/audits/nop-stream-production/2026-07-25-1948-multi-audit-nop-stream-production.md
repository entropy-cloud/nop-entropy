> Audit Status: planned
> Audit Type: multi-dimensional
> Mission: nop-stream-production

# nop-stream Multi-Dimensional Audit Report

- **Audit Date**: 2026-07-25 19:48
- **Target**: `nop-stream/` (6 sub-modules: core / runtime / cep / connector / flow / fraud-example)
- **Scope**: ~817 hand-written Java files, ~64 `_gen` files, ~313 test files
- **Dimensions executed**: 01 (deps/boundaries), 02 (responsibility/files), 03 (API surface), 09 (error handling), 14 (async/txn/checkpoint), 15 (type safety), 16+21 (test coverage/effectiveness), 18 (doc-code consistency), 20 (cross-module contract)
- **Method**: Per `ai-dev/skills/deep-audit-prompts.md` — 5 parallel first-round sub-agents, each with the shared prefix + dimension body. Live code only; no historical audits/plans/bugs read.
- **Reference docs cross-checked**: `ai-dev/design/nop-stream/` (00-vision, README, 01-architecture-baseline, core-design, graph-model-design, checkpoint-design, state-management-design, window-design, time-model-design, cep-design, connector-design, stream-dsl-design, component-roadmap, mailbox-design, failover-design), `docs-for-ai/INDEX.md`, `docs-for-ai/04-reference/source-anchors.md`, `nop-stream/README.md`.

## Finding Summary

| Severity | Count | Verdict |
|---|---|---|
| **P0** | 8 | MUST fix — contract break, data loss, absent critical test |
| **P1** | 19 | MUST fix — contract drift, real defect |
| **P2** | 21 | Backlog — doc rot, naming, low-value tests |

Per mission-driver rule, only P0 + P1 drive remediation planning (27 findings). P2-only items are triaged to follow-up backlog.

---

# P0 Findings (Blocking)

## [P0-1] [维度03-1] `SingleOutputStreamOperator.forceNonParallel()` always throws — CEP `CEP.pattern()` non-keyed path crashes at runtime

- **File**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/datastream/SingleOutputStreamOperator.java:33`; impl `SingleOutputStreamOperatorImpl.java:46-50`; caller `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/PatternStreamBuilder.java:168`
- **Evidence**:
  ```java
  // Interface contract
  SingleOutputStreamOperator<T> forceNonParallel();
  // Javadoc: "Sets the parallelism and maximum parallelism of this operator to one.
  //           And mark this operator cannot set a non-1 degree of parallelism."

  // Sole implementation
  @Override
  public SingleOutputStreamOperator<T> forceNonParallel() {
      throw new UnsupportedOperationException(
              "forceNonParallel is not supported in this implementation");
  }

  // CEP.pattern() non-keyed branch (production path)
  patternStream = inputStream.keyBy(keySelector)
          .transform("GlobalCepOperator", outTypeInfo, operator)
          .forceNonParallel();   // always throws
  ```
- **Severity**: P0 — contract break: a documented public API whose only implementation throws unconditionally, invoked on the most common CEP entry path.
- **Risk**: Any call to `CEP.pattern(nonKeyedStream, pattern)` throws at build time. `TestForceNonParallel` only asserts the exception — codifying the broken API as "correct". Anyone trusting the Javadoc hits a runtime bomb.
- **Recommendation**: (a) delete the interface method and have CEP set `Transformation.setParallelism(1)` + a real `parallelismLocked` flag; or (b) actually implement it in `SingleOutputStreamOperatorImpl`.
- **Confidence**: certain
- **False-positive exclusion**: `SingleOutputStreamOperatorImpl` is the only concrete impl (grep confirms); `keyedStream.transform(...)` returns exactly that impl — the call cannot be redirected.

## [P0-2] [维度14-3] `TwoPhaseCommitSinkFunction.restoreFromEpoch` blindly rolls back ALL pending transactions (including durable-not-committed) — violates §6.4, loses data

- **File**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/functions/sink/TwoPhaseCommitSinkFunction.java:111-127`
- **Evidence**:
  ```java
  @Override
  public void restoreFromEpoch(long epochId, TaskStateSnapshot state) throws Exception {
      Map<Long, Object> pending = getPendingCommits();
      if (pending != null && !pending.isEmpty()) {
          synchronized (pending) {
              for (Object tx : pending.values()) {
                  try { rollback(); }      // rollback() takes no arg — only rolls back current tx, N times
                  catch (Exception e) { LOG.warn("Rollback failed for pending transaction during recovery: {}", tx, e); }
              }
              pending.clear();              // wipes ALL pending including durable-but-uncommitted
          }
      }
      recover(epochId);
  }
  ```
- **Severity**: P0 — data loss / exactly-once break.
- **Risk**: Direct violation of `checkpoint-design.md` §6.4 ("durable-but-not-committed transactions must NOT be aborted; recovery must retry commit") and §3.7 Lifecycle ("commit durable epochs; abort non-durable epochs"). Consequences: (1) transactions that were `preCommit` + manifest-durable but whose commit notification had not arrived are silently dropped → external side-effects permanently lost (catastrophic for non-idempotent sinks); (2) `state` parameter is completely ignored — pending map should be rebuilt from it but is instead cleared; (3) `rollback()` is called N times for the *same* (current) transaction, not once per pending tx. `TestTwoPhaseCommitSinkFunction.testRestoreFromEpochClearsPendingCommits:169` codifies the broken behavior as "expected".
- **Recommendation**: Rewrite `restoreFromEpoch`: (1) rebuild pendingCommits from `state` (not from in-memory map); (2) for each pending tx with `epochId <= N`, call `commit(eid)`; for `epochId > N`, call `abortTransaction(eid)` (requires changing `rollback()` to `abort(long epochId)`); (3) call `beginTransaction()` exactly once at the end.
- **Confidence**: certain
- **False-positive exclusion**: `checkpoint-design.md` §6.3/§6.4/§3.7 explicitly forbid unconditionally aborting durable transactions — not a "fresh start" design choice.

## [P0-3] [维度14-4] `StreamSinkOperator.restoreState` calls `restoreFromEpoch(-1, null)` immediately after restoring pending, then the real epoch call is a no-op

- **File**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/StreamSinkOperator.java:131-157`; compounding caller `nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java:929-974`
- **Evidence**:
  ```java
  // StreamSinkOperator.restoreState
  ((TwoPhaseCommitSinkFunction<Object>) userFunction).setPendingCommits(
          Collections.synchronizedMap(new TreeMap<>(pending)));   // just restored pending
  participant.restoreFromEpoch(-1, null);                          // immediately clears pending (see P0-2)
  ...
  tpcSink.restoreFromEpoch(-1, null);                              // same problem on else-branch

  // GraphModelCheckpointExecutor.restoreOperatorsFromState (line 944 + 955)
  ((AbstractStreamOperator<?>) op).restoreState(opResult);          // triggers restoreFromEpoch(-1)
  ...
  ((CheckpointParticipant) op).restoreFromEpoch(epochId, taskState); // real epochId, but pending is now empty
  ```
- **Severity**: P0 — data loss, compounding P0-2.
- **Risk**: Violates `checkpoint-design.md §3.2 CheckpointParticipant` contract note ("epochId is propagated from EpochManifest.epochId by GraphModelCheckpointExecutor.restoreOperatorsFromState, so participants can sense the real durable epoch instead of a hardcoded value"). `TestSavepointEndToEnd.java:170-242` comment explicitly says "restoreFromEpoch must receive the real durable epochId, not the hardcoded 0" — yet `StreamSinkOperator.restoreState` violates this. Combined with P0-2, **all sink pending transactions are lost on recovery**.
- **Recommendation**: Delete the `participant.restoreFromEpoch(-1, null)` and `tpcSink.restoreFromEpoch(-1, null)` calls inside `StreamSinkOperator.restoreState` — let only the real `epochId` call from `restoreOperatorsFromState` be responsible for participant recovery.
- **Confidence**: certain
- **False-positive exclusion**: `TwoPhaseCommitSinkFunction.restoreFromEpoch` is not idempotent (first call clears the map; second call sees empty map and cannot redo commit).

## [P0-4] [维度16-1] `TestCepOperatorDanglingCleanup.testDanglingCleanupReleasesSharedBuffer` computes `partialMatchesEmpty` but never asserts — dangling cleanup is effectively untested

- **File**: `nop-stream/nop-stream-cep/src/test/java/io/nop/stream/cep/operator/TestCepOperatorDanglingCleanup.java:81-99`
- **Evidence**:
  ```java
  operator.processWatermark(new Watermark(farFuture));
  NFAState state = operator.getNFAStateForTesting();
  boolean partialMatchesEmpty = state.getPartialMatches().size() <= 1
          && state.getCompletedMatches().isEmpty();
  // ↑ partialMatchesEmpty computed but NEVER asserted
  operator.close();
  ```
- **Severity**: P0 — absent test for changed behavior. The test name promises SharedBuffer leak protection but cannot detect the cleanup logic being broken (matches anti-pattern P-8 invalid negative test).
- **Risk**: SharedBuffer memory leaks (CEP's #1 resource risk) would not be caught. If dangling cleanup is removed entirely, this test still passes.
- **Recommendation**: Add `assertTrue(partialMatchesEmpty, "dangling partial matches should be cleaned up after far-future watermark");` before `operator.close()`, plus assert `operator.getPartialMatches()` size transition (N → 0/1).
- **Confidence**: certain
- **False-positive exclusion**: sibling tests like `testSharedBufferCleanedAfterWindowTimeout` do use real `assertTrue(state.getPartialMatches().size() <= 1)` assertions — only this one computes-and-discards.

## [P0-5] [维度16-2] Serializer Fingerprint / stateFormatVersion recovery-compatibility has ZERO tests

- **File**: entire `nop-stream-runtime/src/test/java/io/nop/stream/runtime/checkpoint/` directory
- **Evidence**: `ai-dev/design/nop-stream/checkpoint-design.md:706-789` explicitly defines four incompatibility scenarios (same version + different checksum / version< / version> / explicit migration), but:
  ```
  grep -rn "SerializerFingerprint" nop-stream-*/src/test   → 1 unrelated match
  grep -rn "stateFormatVersion"    nop-stream-*/src/test   → 0 matches
  grep -rn "StateMigrationFunction" nop-stream-*/src/test  → 0 matches
  ```
- **Severity**: P0 — absent test for a critical exactly-once guarantee.
- **Risk**: Any change that flips "reject on mismatch" to "ignore and continue recovery" would pass CI silently, allowing state to be deserialized incorrectly and silently corrupt exactly-once semantics.
- **Recommendation**: Add `TestSerializerFingerprintCompatibility`: (1) identical fingerprints → success; (2) different schemaChecksum → throws StreamException; (3) manifest.version < current → requires migration function; (4) manifest.version > current → rejected; (5) registered StateMigrationFunction → migration succeeds.
- **Confidence**: certain
- **False-positive exclusion**: design doc explicitly lists this as a required recovery-compatibility check — not "unimplemented, so untested".

## [P0-6] [维度16-3] Fencing token rejection of stale attempt output has ZERO tests

- **File**: entire `nop-stream-runtime/src/test/java/io/nop/stream/runtime/` directory
- **Evidence**: design §8.2 explicitly requires:
  > task restart: new attempt gets new token, old token output is rejected
  > sink commit: external transaction carries epoch identity, duplicate commit is idempotent
  > transport write: channel verifies attempt token, old attempt channel closed

  But `TestTaskManager:73-78` only sets the token without ever asserting rejection:
  ```java
  String fencingToken = UUID.randomUUID().toString();
  taskManager.updateFencingToken(fencingToken);
  // just constructs object, no assertion that old-token commit is rejected
  ```
- **Severity**: P0 — absent test for the core distributed-exactly-once guarantee.
- **Risk**: If fencing check is commented out or weakened (e.g. `if (!token.equals(currentToken)) reject()` → `if (false)`), no test fails. After distributed recovery, stale attempt output could pollute state undetectably.
- **Recommendation**: Add `TestFencingTokenRejection`: (1) task restart → old-token commit throws `ERR_STREAM_FENCING_TOKEN_MISMATCH`; (2) coordinator failover → old coordinator's commit notification rejected by sink; (3) transport write with mismatched token closes channel.
- **Confidence**: certain
- **False-positive exclusion**: `updateFencingToken` is implemented and invoked — only the rejection behavior is untested, not the field-passing.

## [P0-7] [维度16-4] Savepoint load operatorId-set differential scenarios have ZERO tests

- **File**: `nop-stream-runtime/src/test/java/io/nop/stream/runtime/checkpoint/TestSavepointApi.java`, `TestSavepointEndToEnd.java`
- **Evidence**: design §8.4 / §8.6 explicitly requires recovery to check:
  > operatorId set: missing-state operator → reject by policy or use initial state
  > new stateful operator → default to initial state, must be explicitly confirmed
  > deleted stateful operator → default reject, unless migration action drops its state
  > modified operatorId → default reject, unless old→new mapping provided

  But `TestSavepointApi` only uses the same operator set; `TestFingerprintAndTerminationMode` only tests whole-fingerprint mismatch. **No subset/superset/type-change granularity test exists.**
- **Severity**: P0 — absent test for savepoint compatibility matrix (the last line of defense in a fault-tolerant system).
- **Risk**: Users who modify the DAG after a savepoint (add/remove/rename operator) get unpredictable recovery: silently use wrong state, throw unfriendly error, or lose state.
- **Recommendation**: Add `TestSavepointCompatibilityMatrix` parameterized over the 8 change scenarios in design §8.6.
- **Confidence**: likely (design explicitly lists them; runtime impl may also be incomplete, but test absence is certain).

## [P0-8] [维度16-5] stateShardCount change / rescale manifest has ZERO tests

- **File**: `nop-stream-core/src/test/java/io/nop/stream/core/common/state/shard/TestStateShardRouting.java`
- **Evidence**: design §8.5 explicitly requires:
  > stateShardCount default immutable. Changing stateShardCount ≡ keyed state resharding, must provide explicit migration action and validation report.

  But `TestStateShardRouting` only tests shardCount=1/2/4 independence, stableHash determinism, and constructor-rejects-0/-1. **No "snapshot then restore with different shardCount" test exists**:
  ```java
  // line 219-249 — only same-shardCount snapshot/restore
  MemoryKeyedStateBackend<String> backend = new MemoryKeyedStateBackend<>(String.class, shardCount);
  // ... snapshot ...
  MemoryKeyedStateBackend<String> restored = new MemoryKeyedStateBackend<>(String.class, shardCount); // same!
  ```
- **Severity**: P0 — absent test for the rescale path.
- **Risk**: If a user changes stateShardCount and recovers, state is either silently lost or key routing gets corrupted (key A's state routed to key B's shard) — silent exactly-once break.
- **Recommendation**: Add `TestStateShardRescale`: (1) snapshot shardCount=2, restore shardCount=4, all keys still correct; (2) snapshot 4 restore 2; (3) verify stableHash routing equivalence after rescale.
- **Confidence**: certain
- **False-positive exclusion**: `StateShard.stableHash` is implemented, `restoreState` interface exists — only the cross-shardCount test is missing.

---

# P1 Findings (Material — must fix)

## [P1-1] [维度03-2] `StreamComponents` uses `Map<String, Object>` for strongly-typed registries mandated by design

- **File**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/model/StreamComponents.java:35-78`
- **Evidence**:
  ```java
  private final Map<String, Object> transforms;
  private final Map<String, Object> streams;
  private final Map<String, Object> windowingStrategies;
  // ... 7 registries, all Map<String, Object>
  public Object getTransform(String id) { return transforms.get(id); }
  ```
  But `core-design.md §1.2` and `README.md §2.3` require `Map<String, PTransform>`, `Map<String, PCollection>`, `Map<String, WindowingStrategy>`, `Map<String, Coder>`, `Map<String, Schema>`, `Map<String, StreamEnvironment>`, `Map<String, SideInput>`.
- **Severity**: P1 — canonical-model type contract drift; weakens fingerprint / savepoint compatibility guarantees.
- **Risk**: Any writer can register the wrong type with no compile- or runtime-time error; downstream readers must `(SomeType) obj` cast. The canonical model is the foundation of portability/fingerprint/savepoint checks per vision §1 success criterion #2.
- **Recommendation**: Narrow to strongly-typed maps per design doc.
- **Confidence**: certain

## [P1-2] [维度03-3] `StreamComponents.getBean(String id, Class<T> clazz)` ignores `clazz` and hardcodes lookup in windowingStrategies

- **File**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/model/StreamComponents.java:149-157`
- **Evidence**:
  ```java
  @SuppressWarnings("unchecked")
  public <T> T getBean(String id, Class<T> clazz) {
      Object bean = windowingStrategies.get(id);   // ignores clazz, hardcodes table
      if (bean == null) throw new StreamException(...);
      return (T) bean;                              // no isInstance check
  }
  ```
- **Severity**: P1 — signature lies about its contract; `ClassCastException` deferred to use site.
- **Risk**: Any caller passing `getBean("foo", Coder.class)` gets a `WindowAssigner` cast to `Coder`. Compounds P1-1.
- **Recommendation**: Maintain a `Map<Key<String,Class>,Object>` and actually validate `isInstance` before returning.
- **Confidence**: certain

## [P1-3] [维度03-4] `StreamSinkOperator`'s `TwoPhaseCommitSinkFunction` branches are unreachable dead code

- **File**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/StreamSinkOperator.java:56-95, 106-127`
- **Evidence**:
  ```java
  // TwoPhaseCommitSinkFunction class declaration (line 25)
  public abstract class TwoPhaseCommitSinkFunction<IN>
          implements SinkFunction<IN>, CheckpointParticipant { ... }

  // StreamSinkOperator
  if (userFunction instanceof CheckpointParticipant) { ... }              // TPCSF always hits
  else if (userFunction instanceof TwoPhaseCommitSinkFunction) { ... }    // dead
  // notifyCheckpointComplete: same pattern lines 107/110/112
  // notifyCheckpointAborted: same pattern lines 119/122/124
  ```
- **Severity**: P1 — misleading branch structure; future refactor that drops CheckpointParticipant from TPCSF would silently switch commit path.
- **Recommendation**: Delete the three `else if (... instanceof TwoPhaseCommitSinkFunction)` branches, or reorder to make intent explicit.
- **Confidence**: certain

## [P1-4] [维度03-5] `StreamOperator.initializeState(TaskStateSnapshot)` is never called in production — `ICheckpointedFunction` recovery contract silently inactive

- **File**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/StreamOperator.java:130-138`; override `AbstractUdfStreamOperator.java:110-134`; production path `GraphModelCheckpointExecutor.java:929-974`
- **Evidence**:
  ```java
  // Interface default
  default void initializeState(TaskStateSnapshot taskStateSnapshot) throws Exception { }

  // AbstractUdfStreamOperator override — invokes ICheckpointedFunction.initializeState
  @Override
  public void initializeState(TaskStateSnapshot taskStateSnapshot) throws Exception {
      if (userFunction instanceof ICheckpointedFunction) { ... }
  }

  // Production restore path
  ((AbstractStreamOperator<?>) op).restoreState(opResult);   // calls restoreState, NOT initializeState
  ```
  Grep `\.initializeState\(` in main → 4 hits, all in `src/test/`. Production restore calls a different signature `restoreState(OperatorSnapshotResult)`.
- **Severity**: P1 — core public recovery contract for `ICheckpointedFunction` is silently inactive in production (tests pass, prod fails).
- **Risk**: Any user function implementing `ICheckpointedFunction` (a documented public SPI) will not receive the `initializeState` callback in production recovery.
- **Recommendation**: Have `restoreState(opResult)` explicitly call `initializeState(...)` to propagate FunctionInitializationContext to UDF, or deprecate `ICheckpointedFunction.initializeState` in Javadoc.
- **Confidence**: certain

## [P1-5] [维度03-6] `StreamOperator.finish()` lifecycle hook is never called in production — buffered data flush contract silently inactive

- **File**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/StreamOperator.java:59-78`; override `AbstractUdfStreamOperator.java:80-86`; `OperatorChain.java:99-149`
- **Evidence**:
  ```java
  void finish() throws Exception;   // Javadoc: "flush all remaining buffered data"

  // AbstractUdfStreamOperator
  @Override public void finish() throws Exception {
      super.finish();
      if (userFunction instanceof SinkFunction) ((SinkFunction<?>) userFunction).finish();
  }

  // OperatorChain — only open/close, no finish
  public void open()  { ... operators.get(i).open();  ... }
  public void close() { ... operators.get(i).close(); ... }
  ```
  `BatchConsumerSinkFunction.finish()` (connector:92-98) relies on this hook to flush buffered batches. In production it never fires — relies on a `flushed` flag inside `close()` as a fallback.
- **Severity**: P1 — documented 5-stage lifecycle (`open → processElement → processWatermark → finish → close`) degrades to 3 stages in production; future sinks that only override `finish()` will silently drop buffered data.
- **Recommendation**: Add `OperatorChain.finish()` and invoke it after source returns (before MAX_WATERMARK emit), or explicitly remove `finish()` from docs and fold into `close()`.
- **Confidence**: certain

## [P1-6] [维度15-1] `StateDescriptor.getSerializer()` decouples serializer type from the descriptor's own `T`

- **File**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/StateDescriptor.java:16-57`
- **Evidence**:
  ```java
  public class StateDescriptor<T> implements Serializable {
      private final Class<T> valueType;
      private TypeSerializer<?> serializer;          // <?>, not <T>
      ...
      @SuppressWarnings("unchecked")
      public <S> TypeSerializer<S> getSerializer() {  // <S> unconstrained, no relation to T
          return (TypeSerializer<S>) serializer;
      }
      public void setSerializer(TypeSerializer<?> serializer) { ... }
  }
  ```
- **Severity**: P1 — fake type safety on the state-serialization path; misconfigured serializer explodes at use site far from registration.
- **Risk**: `StateDescriptor<Integer>` allows `TypeSerializer<String> s = desc.getSerializer();` with no compile error; ClassCastException deferred to checkpoint/restore.
- **Recommendation**: Field → `TypeSerializer<T>`; setter → `setSerializer(TypeSerializer<T>)`; getter → `TypeSerializer<T> getSerializer()` (drop `<S>`).
- **Confidence**: certain

## [P1-7] [维度15-2] `IInternalStateBackend.getInternalAppendingState(ReducingStateDescriptor<IN>)` declares unconstrained `<ACC>` type parameter

- **File**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/IInternalStateBackend.java:24-52`
- **Evidence**:
  ```java
  <N, IN, ACC> InternalAppendingState<K, N, IN, ACC, ACC> getInternalAppendingState(
          ReducingStateDescriptor<IN> descriptor);   // descriptor carries no ACC info
  ```
- **Severity**: P1 — decorative type parameter; caller can request any ACC type, compiler does no checking. WindowOperator's many `@SuppressWarnings("unchecked")` are the symptom.
- **Risk**: Fake type safety — users believe compiler validates ACC, but it doesn't.
- **Recommendation**: Change reducing overload to `<N, IN> InternalAppendingState<K, N, IN, IN, IN> getInternalAppendingState(ReducingStateDescriptor<IN> descriptor);` (no ACC parameter).
- **Confidence**: certain

## [P1-8] [维度09-1 / 14-5] `InputGate.readSingleChannel` swallows InterruptedException and throws — breaks mailbox cooperative-cancel contract

- **File**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/InputGate.java:262-278` (compare multi-input `readMultiChannel():325-328`)
- **Evidence**:
  ```java
  // Single-input path
  } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new StreamException(ERR_STREAM_INVALID_STATE).param(ARG_DETAIL, "InputGate interrupted");  // no cause!
  }

  // Multi-input path (same file)
  } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Optional.empty();   // lets main loop observe cancel flag
  }
  ```
- **Severity**: P1 — error-handling anti-pattern (swallowed cause) + abort/cancel contract break. Violates `error-handling.md` "preserve cause" and `mailbox-design.md §3.5` cooperative-cancel "interrupt unblocks read → main loop top observes cancel flag → graceful exit".
- **Risk**: Single-input tasks (the most common form, e.g. forward operators) on abort go through FAILED instead of CANCELED; `GraphModelCheckpointExecutor.checkTaskFailures` then throws a second exception on top of the abort exception, complicating diagnosis.
- **Recommendation**: Align single-input path with multi-input: `Thread.currentThread().interrupt(); return Optional.empty();`. Let the main loop top (`mailboxExecutor.processAvailableMails()` / `isCancelled()`) decide exit.
- **Confidence**: certain

## [P1-9] [维度09-3] `MessageSourceFunction` silently swallows collect exceptions — source misreports normal completion

- **File**: `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/MessageSourceFunction.java:122-145`
- **Evidence**:
  ```java
  public Object onMessage(String t, Object msg, IMessageConsumeContext context) {
      synchronized (ctx) {
          try { ctx.collect((T) msg); }
          catch (Exception e) {
              LOG.error("Failed to collect message from topic {}", effectiveTopic, e);
              failed = true;
              return null;       // exception swallowed, just sets a flag
          }
      }
      return null;
  }
  // run() exits normally when failed=true, caller sees success
  ```
- **Severity**: P1 — violates `error-handling.md` "do not convert exceptions to return values"; source's `AT_LEAST_ONCE` declaration (line 160) does not exempt this — even at-least-once should propagate failures.
- **Risk**: Downstream sink fails → exception caught and swallowed → source exits normally → `invokeSource()` finally emits MAX_WATERMARK + closes output → downstream thinks normal EOS → entire pipeline silently exits "successful" but data was lost. No recovery triggered.
- **Recommendation**: Store throwable in a `volatile Throwable pendingError` field; before `run()` returns, `if (pendingError != null) throw pendingError`. Or re-throw as RuntimeException to let IMessageService handle it.
- **Confidence**: certain

## [P1-10] [维度14-1] `ResultPartition.close()` discards un-consumed records when queue is full — data loss on bounded source EOS

- **File**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/ResultPartition.java:178-193`; test `TestResultPartitionDeadlock.testDrainedElementsLostOnClose:60-70` codifies the loss as "expected"
- **Evidence**:
  ```java
  public void close() {
      finished = true;
      if (!queue.offer(END_OF_STREAM)) {
          if (bufferPool != null) { ... }
          queue.clear();            // discards all un-consumed records
          queue.offer(END_OF_STREAM);
      }
  }
  ```
- **Severity**: P1 — data loss; exactly-once break for bounded-source EOS with slow downstream.
- **Risk**: When producer calls `close()` with full queue (consumer behind), `queue.clear()` drops records whose source offsets already advanced. Recovery from durable offset won't replay them (offsets are "already emitted"). Violates `failover-design.md §2.2` by-reference LinkedBlockingQueue back-pressure invariant.
- **Recommendation**: Use blocking `queue.put(END_OF_STREAM)` (natural back-pressure until consumer drains), or add optional `awaitDrain(timeout)`. Minimum fix: if queue full on close, use `put()` instead of `offer()+clear()`, fall back to clear+ERROR log only on interrupt (emergency cancel).
- **Confidence**: certain
- **False-positive exclusion**: `RecordWriter.close()` is called from the normal completion path (`StreamTaskInvokable.java:385, 401`), not just emergency cancel.

## [P1-11] [维度14-2] `CheckpointBarrierTracker.acknowledgeOperator` silently swallows snapshot errors — failed checkpoint may be marked complete

- **File**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/CheckpointBarrierTracker.java:98-143`
- **Evidence**:
  ```java
  public void acknowledgeOperator(int operatorIndex, OperatorSnapshotResult snapshot) {
      synchronized (this) {
          // never checks snapshot.hasError()
          ...
          if (operatorsToAck.decrementAndGet() == 0) { snapshotToDeliver = snap; ... }
      }
  }
  ```
  Operator-side (`AbstractStreamOperator.processBarrier:297-322`) puts the exception into `OperatorSnapshotResult.error` and ACKs via callback. But tracker treats the failure result as a successful ACK.
- **Severity**: P1 — silent checkpoint corruption; design §3.4 requires "saveState() failure → checkpoint abort, do not propagate barrier".
- **Risk**: (a) Single-chain task: snapshot failure ACK makes `operatorsToAck==0`, task reports snapshot to coordinator, coordinator treats that operator's state as empty (failureResult has no states) → manifest durable with state lost; (b) Multi-chain: failed operator doesn't emit barrier to chain successor → 10-min checkpointTimeout fallback, but error remains invisible at recovery.
- **Recommendation**: In `acknowledgeOperator`, immediately after `synchronized` block entry, check `if (snapshot != null && snapshot.hasError()) { abortCheckpoint(snapshot.getError()); return; }`.
- **Confidence**: certain

## [P1-12] [维度16-6] Watermark multi-input combine has only unit tests, no e2e — test file self-exempts via "Anti-Hollow exemption"

- **File**: `nop-stream-core/src/test/java/io/nop/stream/core/common/eventtime/TestIndexedCombinedWatermarkStatus.java:14-22`
- **Evidence**:
  ```java
  /**
   * Unit tests for the multi-input watermark valve math (G47). ...
   * Runtime wiring (e2e) is deferred to the two-input-operator successor
   * because nop-stream has no two-input operator consumer (Anti-Hollow exemption).
   */
  ```
- **Severity**: P1 — protective valve logic has no regression guard when union/join/broadcast operators are introduced.
- **Recommendation**: Add a wire-test that simulates a fake multi-input operator driving the valve, asserting watermark output ordering and interaction with barrier processing.
- **Confidence**: likely

## [P1-13] [维度16-7] `TestCepOperatorStateBackendWiring` couples to internal accessors `getKeyedStateBackend()` / `getNFAStateForTesting()` (P-4 anti-pattern)

- **File**: `nop-stream/nop-stream-cep/src/test/java/io/nop/stream/cep/operator/TestCepOperatorStateBackendWiring.java:139-166`
- **Evidence**:
  ```java
  IKeyedStateBackend<?> keyedBackend = operator.getKeyedStateBackend();
  assertNull(keyedBackend, "Without setStateBackend, keyedStateBackend should remain null ...");
  NFAState state = operator.getNFAStateForTesting();   // internal test hook
  assertNotNull(state, ...);
  ```
- **Severity**: P1 — refactoring `AbstractStreamOperator` to auto-create a memory backend (a reasonable optimization) would fail this test without any behavioral regression.
- **Recommendation**: Refactor to input/output behavior test (extend `testConfiguredStateBackendIsUsed` pattern).
- **Confidence**: certain

## [P1-14] [维度16-9] `TestAfterMatchSkipStrategies` is 100% metadata assertions (P-2 anti-pattern) — file name implies it is the strategy's main test

- **File**: `nop-stream/nop-stream-cep/src/test/java/io/nop/stream/cep/nfa/aftermatch/TestAfterMatchSkipStrategies.java:1-75`
- **Evidence**: 13 tests, all `isSkipStrategy()` / `getPatternName()` / factory-method-return-type assertions. No NFA-driven functional test of skip behavior.
- **Severity**: P1 — reviewers mistake this file for "AfterMatchSkipStrategy behavior is covered". If `SkipPastLastEvent` semantics were changed to "skip to first event", all 13 pass.
- **Recommendation**: Consolidate factory tests into one method; add NFA-based tests over sequence `a1 a2 a3` asserting match count and positions differ across the 4 strategies. (`TestCepSkipStrategyE2E` already has partial coverage.)
- **Confidence**: certain

## [P1-15] [维度21-9] `TestBatchConsumerSinkFunction` covers only happy paths (P-3) — connector has no boundary/concurrency tests

- **File**: `nop-stream/nop-stream-connector/src/test/java/io/nop/stream/connector/TestBatchConsumerSinkFunction.java:22-103`
- **Evidence**: 5 tests, all happy path (plus `testNullProviderRejected`). Missing: `batchSize=0` rejected, `consume(null)` rejected, concurrent `consume` thread-safety.
- **Severity**: P1 — connectors are the weakest link in distributed scenarios; concurrency is the normal operating mode, not a "failure path".
- **Recommendation**: Add `testBatchSizeZeroRejected`, `testConsumeNullRejected`, `testConcurrentConsumeThreadSafe`.
- **Confidence**: likely

## [P1-16] [维度18-1] `TimestampsAndWatermarksOperator` is documented under runtime/watermark but actually lives in core/operators — runtime has no watermark package

- **Doc**: `ai-dev/design/nop-stream/README.md:90` and `time-model-design.md:174`
- **Code**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/TimestampsAndWatermarksOperator.java:8` (`package io.nop.stream.core.operators`)
- **Severity**: P1 — module-layer misclassification; `docs-for-ai/04-reference/source-anchors.md:213` STRM-031 correctly says `core/operators`, contradicting design doc.
- **Recommendation**: Update README §1.2 (remove runtime `watermark` row; add to core `operators` row) and time-model-design.md §6 title.

## [P1-17] [维度18-2] `docs-for-ai/INDEX.md:212` references non-existent modules `nop-stream-checkpoint` and `nop-stream-flink`

- **Doc**: `docs-for-ai/INDEX.md:212` — lists 8 modules including `nop-stream-checkpoint` and `nop-stream-flink`.
- **Code**: `nop-stream/` has only 6 sub-modules: core/cep/connector/runtime/flow/fraud-example.
- **Severity**: P1 — stale doc that misroutes AI/developers to non-existent modules; README §1.3 explicitly states checkpoint is NOT a separate module.
- **Recommendation**: Delete the two module references; align with `nop-stream/README.md`'s 6-module list.

## [P1-18] [维度18-3] `core` package paths drift — `state`, `time` (Watermark), `functions` actually live under `common/`

- **Doc**: `README.md:81-84` lists `state`, `time`, `functions` as top-level packages.
- **Code**: `IStateBackend` etc. at `core/common/state/*`; `WatermarkStrategy` etc. at `core/common/eventtime/*`; `MapFunction` etc. at `core/common/functions/*`. Top-level `time/` package contains only `TimerService.java`.
- **Severity**: P1 — developers following doc to import or locate classes fail; `source-anchors.md` STRM-012/014/015 correctly use `common/` paths, contradicting design doc.
- **Recommendation**: Update README §1.2 to actual package paths.

## [P1-19] [维度18-4] `CheckpointCoordinator` and `GraphModelCheckpointExecutor` are mis-attributed in README §1.2

- **Doc**: `README.md:82` lists `CheckpointCoordinator` under core.checkpoint; `:89` lists `GraphModelCheckpointExecutor` under runtime.checkpoint.
- **Code**: `CheckpointCoordinator` at `nop-stream-runtime/.../runtime/checkpoint/` (runtime, not core); `GraphModelCheckpointExecutor` at `nop-stream-runtime/.../runtime/execution/` (execution, not checkpoint). README §1.3 contradicts §1.2.
- **Severity**: P1 — developers cannot locate critical classes from the doc.
- **Recommendation**: Update README §1.2: remove `CheckpointCoordinator` from core.checkpoint row; add runtime `execution` row containing `GraphModelCheckpointExecutor` etc.

---

# P2 Findings (Backlog / polish)

> P2-only items do not by themselves drive a remediation plan. Recorded for backlog triage.

### Architecture / Boundaries

- **[P2-1] [维度01-1]** `nop-stream-flow/pom.xml:20-23` depends on `nop-stream-cep` (used by generated `_StreamModel.java:104` for `CepPatternModel`), contradicting README §1.4 / architecture-baseline §2 which say `flow → core`. Real compile-time dependency. Recommend updating doc or decoupling.
- **[P2-2] [维度02-1]** `nop-stream/src/main/java/io/nop/stream/flow/model/` is a duplicate source tree (60 files, git-tracked) under the pom-parent module (which is `packaging=pom` and never compiles). 30 `_gen` files already diverged from the canonical copy. Recommend `git rm -r nop-stream/src/`.

### API surface / docs

- **[P2-3] [维度03-7]** Public operator interface Javadocs reference non-existent types `TwoInputStreamOperator`, `MultipleInputStreamOperator`, `AbstractStreamOperatorV2`, `AbstractInput` (which vision §4 explicitly lists as Non-Goals). Files: `StreamOperator.java:28-31`, `OneInputStreamOperator.java:24-26`, `Input.java:28-35`.
- **[P2-4] [维度03-8]** `CheckpointedSourceFunction.java:14-19` Javadoc says "API 预留，当前未被使用" but production `StreamSourceOperator.java:296-302, 321-332` actively calls its `snapshotState`/`initializeState`.

### Type safety / generics

- **[P2-5] [维度15-3]** DataStream API casts `UnknownTypeInformation.INSTANCE` (typed `<?>`) to `TypeInformation<R>` in 6+ entry points (`DataStreamImpl.java:135-186`, `KeyedStreamImpl.java:190-197`, `WindowedStreamImpl.java:184-242`), propagating `Object.class` as `Class<R>`. `core-design.md §2.4` already admits this. Recommend exposing `UnknownTypeInformation` only via `TypeInformation<?>`.
- **[P2-6] [维度15-4]** `IWindowOperatorFactory` requires `Class<ACC>`/`Class<IN>`/`Class<K>` parameters but `WindowedStreamImpl.java:184-242` always passes `(Class<T>) (Class<?>) Object.class`; the factory uses them only to build a dummy serializer (`WindowOperatorFactoryImpl.createDummySerializer:121-160`). Performative type safety. Recommend dropping the `Class<...>` params in favor of optional `TypeSerializer<...>`.

### Error handling

- **[P2-7] [维度09-2]** `CheckpointCoordinator.onCompletePersistFailure:579-590` logs the same failure message twice (ERROR at line 582, WARN at line 589) with identical template + cause. Log noise; affects failure-rate metrics.
- **[P2-8] [维度09-4]** `Lockable.release:54-79` (cep sharedbuffer) throws bare `IllegalStateException` on ref-count underflow instead of `StreamException`/`NopException`. Bypasses platform exception hierarchy.

### Tests (low-value, P-1/P-2/P-5 anti-patterns — backlog cleanup)

- **[P2-9] [维度16-8]** `TestCountTrigger.java:1-15` — entire file tests only `canMerge()` returns false. No `onElement` boundary test (count=max-1 CONTINUE vs count=max FIRE). High-risk trigger has no behavioral test.
- **[P2-10] [维度21-1]** `TestCheckpointBarrier.java:14-91` — pure getter/setter round-trip on a value object.
- **[P2-11] [维度21-2]** `TestTaskStateSnapshot.java`, `TestOperatorSnapshotResult.java`, `TestCompletedCheckpoint.java` — map put/get round-trips, no serialization fidelity tests.
- **[P2-12] [维度21-3]** `TestCheckpointType.java:17-30` — asserts enum member count and `getName()` constants. Already `@Tag("low-value")` but should be deleted.
- **[P2-13] [维度21-4]** `TestProcessingGuarantee.java:7-33` — constant boolean assertions on enum switch; redundant with `TestInputGateProcessingGuarantee`.
- **[P2-14] [维度21-5]** `TestJobTerminationContext.java:7-39` — factory-method field assignment assertions; real behavior covered by `TestFingerprintAndTerminationMode`.
- **[P2-15] [维度21-6]** `TestCheckpointIDCounter.java:15-84` — tests `AtomicLong` semantics, no concurrency test (the only real risk).
- **[P2-16] [维度21-7]** `TestWindowOperatorBasic.java:23-72` — tests `TimeWindow` geometry primitives; file name implies WindowOperator coverage it doesn't provide.
- **[P2-17] [维度21-8]** `TestSharedBuffer.java:21-71` — overuses `assertNotNull(id)` where concrete `EventId` value assertions exist in sibling tests.
- **[P2-18] [维度21-10]** `TestNFAState.java:11-80` — equals/hashCode mirror tests; only `testNotEqualWhenMatchesDiffer` has real protection.

### Doc consistency

- **[P2-19] [维度18-6]** `StreamExecutionEnvironment` documented under `datastream` (README §1.2:75) but actually at `core/environment/`.
- **[P2-20] [维度18-7]** README §1.2/§1.4 and component-roadmap §2.1 say cep depends on `nop-xlang`, but cep `pom.xml` has no such dependency; `IEvalFunction` comes from `nop-core` (`io.nop.core.lang.eval`). Also component-roadmap §2.1 vs §2.5 internally contradict.
- **[P2-21] [维度18-8]** README §1.2/§1.4 say flow depends only on core, but flow `pom.xml` also depends on `cep` and `xdefs`.

---

# Cross-Dimension Summary

## Coverage by sub-agent

| Dimensions | Sub-agent | P0 | P1 | P2 |
|---|---|---|---|---|
| 01 / 02 / 20 | deps & boundaries | 0 | 0 | 2 |
| 03 / 15 | API surface & type safety | 1 | 7 | 2 |
| 09 / 14 | error handling & async/checkpoint | 2 | 4 | 2 |
| 16 / 21 | test coverage & effectiveness | 5 | 4 | 10 |
| 18 | doc-code consistency | 0 | 4 | 3 |
| **Total** | | **8** | **19** | **19** |

(Note: P2-19/20/21 from dim 18 are listed separately, so total P2 = 21 if doc P2s from 18 are counted individually; the dimension-18 sub-agent reported 8 findings total: 4×P1 + 4×P2, where one finding 18-5 covers three runtime packages.)

## Design invariants check (vision §8, 15 invariants)

All 15 design invariants are **honored by code** (no P0 invariant violation). The 4 P0 checkpoint findings are about implementation correctness and missing tests, not invariant violations. Vision §10 reject-list (DataSource.getConnection, independent barrier thread, self-managed HashMap window state, per-match CEP event copy) — none reintroduced. Vision §4 Non-Goals (two-stream join, SQL API, Flink runtime copy, dynamic parallelism, async operators) — none violated.

## What was checked and clean

- **Module dependency direction**: core never reverse-depends on sub-modules; runtime/cep/connector have zero cross-imports; no cycles. All cross-process integration goes through Nop platform SPI (`IJdbcTemplate`/`IMessageService`/`IBatchLoader`/`IEvalFunction`) — no platform bypass.
- **Module responsibilities**: each sub-module's packages match its declared role (with the documented exceptions in P1-16/18/19).
- **`_gen` directories**: no hand-written files leaked in.
- **Barrier injection path**: source-only via mailbox, matching `mailbox-design.md §3`.
- **Cross-module contracts**: connector↔batch via `IBatchLoader`; runtime.checkpoint uses `IJdbcTemplate+IDialect`; transport uses `IMessageService`; cep uses `IEvalFunction`.
- **Large classes** (>500 lines: WindowOperator 1984, NFACompiler 1100, GraphModelCheckpointExecutor 1025, NFA 984, JobCoordinator 982, CheckpointCoordinator 889): each single-responsibility Flink-semantics port; no P1/P2 from size alone.

## Overall Assessment

nop-stream's architecture discipline is **strong at the macro level** — clean module boundaries, no platform-bypass, 15 design invariants all honored, no Non-Goal violations. The **serious problems concentrate in two areas**:

1. **Checkpoint recovery path correctness (P0-2, P0-3, P1-4, P1-5, P1-11)** — `TwoPhaseCommitSinkFunction.restoreFromEpoch` violates the durable-transaction contract and `StreamSinkOperator.restoreState` compounds it; the public `initializeState` / `finish` lifecycle hooks are silently inactive in production; snapshot errors are swallowed by the barrier tracker. Together these can cause **silent data loss on recovery or sink close** — the exact scenario exactly-once is supposed to prevent.

2. **Critical exactly-once invariants have no regression tests (P0-4 through P0-8)** — Serializer Fingerprint compatibility, fencing-token rejection, savepoint operatorId differential, stateShardCount rescale, and the dangling-cleanup assertion are all absent. A maintainer could weaken any of these invariants and CI would stay green.

The doc-vs-code drift (P1-16..19) is systematic but mechanical — README §1.2's package table was written before the `common/` reorganization and never reconciled. All findings in this category are "fix the doc", not "fix the code".

The 19 P2 findings are mostly low-value getter/setter tests that should be batched for cleanup; they do not block.

## Recommended Remediation Order

1. **Immediate (P0)**: Fix `restoreFromEpoch` + `restoreState` recovery contract (P0-2, P0-3); fix `forceNonParallel` API (P0-1); add the 5 missing test suites (P0-4 through P0-8).
2. **Short-term (P1)**: Fix `ResultPartition.close()` data loss (P1-10); fix `CheckpointBarrierTracker` silent error swallow (P1-11); fix `MessageSourceFunction` exception swallow (P1-9); fix `InputGate` interrupt handling (P1-8); activate `finish()` lifecycle (P1-5); activate `initializeState()` propagation (P1-4); fix type-safety SPI signatures (P1-1, P1-2, P1-6, P1-7).
3. **Medium-term (P1 doc)**: Reconcile README §1.2 package table with actual layout (P1-16..19); fix `docs-for-ai/INDEX.md` (P1-17).
4. **Backlog (P2)**: Batch cleanup of low-value tests; remove duplicate source tree (P2-2); fix stale Javadocs (P2-3, P2-4).

## Audit Blind Spots

- **Concurrency stress tests**: this audit did not run multi-threaded stress harnesses; `Lockable`/`SharedBuffer`/`ResultPartition` were inspected statically. Concurrency bugs may exist that only surface under load.
- **Distributed mode end-to-end**: `IStreamExecutionDispatcher` / `EmbeddedDistributedExecutor` were not exercised against a real multi-TaskManager setup; only code reading.
- **Connector external-system integration**: nop-batch / message-queue / CDC source correctness against real external systems was not validated.
- **Performance / back-pressure**: no latency/throughput measurement; `MemoryBudget` allocation correctness inferred from code only.
- **XDSL `flow` module**: marked "规划中" but has `_gen` artifacts and zero tests (P0-8 if in use, P2 if WIP); status needs human confirmation before triage.

<AI_STEP_RESULT>issues</AI_STEP_RESULT>
