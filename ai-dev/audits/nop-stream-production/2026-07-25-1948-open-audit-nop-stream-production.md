> Audit Status: planned
> Audit Type: open-ended
> Mission: nop-stream-production

# Open-Ended Adversarial Audit: nop-stream-production

- **Date**: 2026-07-26
- **Auditor**: AI (open-ended adversarial review per `ai-dev/skills/open-ended-adversarial-review-prompt.md`)
- **Target**: `nop-stream/` — all 6 submodules (core / runtime / cep / connector / flow / fraud-example)
- **Heuristics used**: code-generation victim, 10x-scale operator, dead-code scavenger, exception-path detective, IoC detective
- **Dedup baseline**: 
  - Multi-audit `2026-07-25-1948-multi-audit-nop-stream-production.md` (8 P0 + 19 P1 + 21 P2) — assumed pre-existing; not re-reported unless impact changed.
  - Flink-comparison open-audit `nop-stream-flink-comparison/2026-07-24-2227-open-audit-...` (AR-1..AR-7) — checked for still-live status; confirmed-live items reported with current evidence.

This audit is **open-ended and discovery-oriented**. It does not reuse the multi-audit's dimension grid.

---

## Findings

### [AR-1] `OperatorChain.shallowCopyOperator()` silently shares mutable operator instance for unhandled operator types — parallelism > 1 state corruption

- **File**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/jobgraph/OperatorChain.java:206-235`
- **Evidence**:
  ```java
  // OperatorChain.java:206-235
  private static StreamOperator<?> shallowCopyOperator(StreamOperator<?> op) {
      if (op instanceof StreamSourceOperator) { ... return new StreamSourceOperator<>(...); }
      if (op instanceof StreamMap) { ... return new StreamMap<>(...); }
      if (op instanceof StreamFilter) { ... return new StreamFilter<>(...); }
      if (op instanceof StreamFlatMap) { ... return new StreamFlatMap<>(...); }
      if (op instanceof StreamSinkOperator) { ... return new StreamSinkOperator<>(...); }
      if (op instanceof StreamReduceOperator) { ... return new StreamReduceOperator<>(...); }
      return op;   // <-- SILENT SHARED INSTANCE for all other types
  }
  ```
  Unhandled operator types in production code: `ProcessOperator` (core), `CepOperator` (cep), `WindowOperator` (runtime), `TimestampsAndWatermarksOperator` (core). Each carries mutable `transient` per-subtask state (`HeapInternalTimerService`, `NFA`/`SharedBuffer`, `TimestampedCollector`, watermark generators).
- **Priority**: **[P1]** — Material: silent data corruption / race condition. When `parallelism > 1`, `GraphExecutionPlan.build()` calls `deepCopy()` once per subtask (line 284-285). For unhandled types, all N subtasks receive the **same operator instance** with shared mutable timer services, NFA state, and collectors. This directly undermines the mission's distributed exactly-once goals: CEP and keyed-process pipelines with parallelism > 1 produce incorrect results regardless of checkpoint correctness.
- **Status**: Known-unfixed (first reported 2026-07-24 flink-comparison AR-1; not covered by the 2026-07-25 multi-audit; confirmed still present at current HEAD). The `ProcessOperator` was added (Plan 305) without updating `shallowCopyOperator()`.
- **Risk**: Any multi-subtask pipeline using `CepOperator`, `ProcessOperator`, `WindowOperator`, or `TimestampsAndWatermarksOperator` with parallelism > 1 silently corrupts state across subtasks. Unit tests with `parallelism=1` (the common case) do not expose this.
- **Recommendation**: Replace the `instanceof` chain with a `StreamOperator.copyForSubtask()` interface method so every operator owns its copy logic. At minimum, add explicit cases for `ProcessOperator`, `CepOperator`, `WindowOperator`, `TimestampsAndWatermarksOperator`. If an operator genuinely cannot be copied, throw rather than silently return the shared instance.
- **Confidence**: Certain
- **Discovery source**: code-generation victim / 10x-scale operator

---

### [AR-2] `StreamConnectors` and connector classes hard-reference optional dependencies — `NoClassDefFoundError` at class-load time

- **Files**:
  - `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/StreamConnectors.java:10-11` — imports `IBatchConsumerProvider`, `IBatchLoaderProvider` in method signatures
  - `nop-stream/nop-stream-connector/pom.xml:22-24, 33-36` — `nop-batch-core` (optional), `nop-message-debezium` (optional)
- **Evidence**:
  ```java
  // StreamConnectors.java:10-11
  import io.nop.batch.core.IBatchConsumerProvider;
  import io.nop.batch.core.IBatchLoaderProvider;
  // ...
  public static <S> DataStreamSource<S> fromBatchLoader(
          StreamExecutionEnvironment env,
          IBatchLoaderProvider<S> loaderProvider,   // <-- hard ref to optional dep in signature
          String sourceName) { ... }
  ```
  ```xml
  <!-- pom.xml:22-24 -->
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-batch-core</artifactId>
      <optional>true</optional>
  </dependency>
  ```
- **Priority**: **[P1]** — Material: `NoClassDefFoundError`. Java class resolution fails when `StreamConnectors.class` is loaded because its method signatures reference types from the optional dependency. Every downstream user of `nop-stream-connector` must add `nop-batch-core` and `nop-message-debezium` as explicit dependencies even if they never use batch/Debezium connectors. The `optional=true` marker only suppresses transitive propagation — it does not protect against class-loading failure.
- **Status**: Known-unfixed (first reported 2026-07-24 flink-comparison AR-2; confirmed still present). Not covered by the 2026-07-25 multi-audit.
- **Risk**: Silent runtime `NoClassDefFoundError` when `StreamConnectors.class`, `BatchLoaderSourceFunction.class`, or `DebeziumCdcSourceFunction.class` are loaded. The error occurs at class-init time, not at method-entry time, making it hard to diagnose.
- **Recommendation**: Extract batch-specific and debezium-specific connector classes into separate Maven modules (`nop-stream-connector-batch`, `nop-stream-connector-debezium`) with non-optional deps on the respective libraries. Keep only types that depend on non-optional deps in the base `nop-stream-connector` module.
- **Confidence**: Certain
- **Discovery source**: IoC detective / 10x-scale operator

---

### [AR-3] Partitioner-to-policy inference uses fragile class-name string matching in TWO independent locations — silent misrouting for custom partitioners

- **Files**:
  - `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/graph/PartitionedPlanGenerator.java:83-99`
  - `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/GraphExecutionPlan.java:430-445`
- **Evidence**:
  ```java
  // PartitionedPlanGenerator.java:90-98
  String partitionerClassName = edge.getPartitioner().getClass().getName();
  if (partitionerClassName.contains("Hash") || partitionerClassName.contains("hash")) {
      return PartitionPolicy.HASH;
  } else if (partitionerClassName.contains("Rebalance") || partitionerClassName.contains("rebalance")) {
      return PartitionPolicy.REBALANCE;
  } else if (partitionerClassName.contains("Broadcast") || partitionerClassName.contains("broadcast")) {
      return PartitionPolicy.BROADCAST;
  }
  return PartitionPolicy.FORWARD;

  // GraphExecutionPlan.java:440-444 (SEPARATE location, same class of issue)
  if (edge.getPartitioner() != null) {
      return PartitionPolicy.HASH;   // <-- ANY non-null partitioner defaults to HASH
  }
  return PartitionPolicy.FORWARD;
  ```
- **Priority**: **[P1]** — Material: silent policy misclassification. A custom partitioner named `HashLookupPartitioner` (semantically broadcast) is classified as `HASH`. A `RebalanceToLeaderPartitioner` is classified as `REBALANCE`. Worse: `GraphExecutionPlan.resolvePartitionPolicy()` (the fallback when no DeploymentPlan edge-plan matches) defaults **any** non-null partitioner to `HASH` — so a `BroadcastPartitioner` or `RebalancePartitioner` that doesn't implement `PartitionPolicyAware` is silently treated as hash-partitioned, routing records to wrong downstream subtasks.
- **Status**: Known-unfixed (PartitionedPlanGenerator portion first reported 2026-07-24 flink-comparison AR-3; the `GraphExecutionPlan.resolvePartitionPolicy()` HASH-default is a newly identified second occurrence of the same root cause). Not covered by the 2026-07-25 multi-audit.
- **Risk**: Wrong partitioning in distributed execution — data sent to wrong downstream subtasks, causing incorrect results or deadlocks. The misclassification is silent (no warning, no error). The `GraphExecutionPlan` fallback is reachable via `GraphExecutionPlan.build(jobGraph)` (no DeploymentPlan) — the direct-build / test path.
- **Recommendation**: (1) Replace string matching with `instanceof` checks against known partitioner types. (2) Require all partitioners to implement `PartitionPolicyAware` (or add `PartitionPolicy getPolicy()` to the partitioner interface) and throw on unrecognized types. (3) Align `GraphExecutionPlan.resolvePartitionPolicy()` to use the same inference as `PartitionedPlanGenerator` rather than defaulting to HASH.
- **Confidence**: Certain
- **Discovery source**: code-generation victim / model attacker

---

### [AR-4] `SimpleStreamOperatorFactory.createStreamOperator()` silently falls back to shared template instance on serialization failure

- **File**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/SimpleStreamOperatorFactory.java:46-72`
- **Evidence**:
  ```java
  // SimpleStreamOperatorFactory.java:50-71
  if (operator instanceof Serializable) {
      try {
          // serialization deep copy...
      } catch (java.io.NotSerializableException e) {
          // Operator contains non-serializable fields (e.g. lambdas).
          // Return the shared template instance instead of failing.
          return operator;   // <-- SILENT SHARED INSTANCE
      } catch (Exception e) {
          throw new StreamException("Failed to create copy of operator via serialization: " + name, e);
      }
  }
  return operator;  // <-- also returns shared instance for non-Serializable
  ```
- **Priority**: **[P1]** — Material: latent contract drift. The `StreamOperatorFactory.createStreamOperator()` SPI contract promises an independent operator instance. The silent fallback violates this. Currently this path is **not reached in production** because `JobGraphGenerator.createOperatorFromFactory()` (line 412-413) special-cases `SimpleStreamOperatorFactory` and calls `getRawOperator()` directly, bypassing `createStreamOperator()`. However, the method remains a public SPI entry point: any future factory consumer, test harness, or codegen template that calls `createStreamOperator()` on a `SimpleStreamOperatorFactory` wrapping a non-serializable operator (e.g., one capturing a lambda) will silently get the shared template — causing cross-subtask state corruption identical to AR-1.
- **Status**: Known-unfixed (first reported 2026-07-24 flink-comparison AR-4; confirmed still present). Not covered by the 2026-07-25 multi-audit.
- **Risk**: Any subtask-specific caller of `createStreamOperator()` gets the same shared instance. Parallel subtasks share mutable state (output, timer registrations, checkpoint tracking), leading to silent data corruption. The `catch (NotSerializableException)` suggests this was intentional for lambdas, but the silent fallback is dangerous and undocumented in the SPI contract.
- **Recommendation**: At minimum, log a `WARN` when falling back to the shared instance. Better: throw `StreamException` unless the operator explicitly declares it is safe to share (e.g., via a `@ThreadSafe` / `@Shareable` marker interface).
- **Confidence**: Certain
- **Discovery source**: code-generation victim

---

### [AR-5] `ResultPartition.close()` bufferPool permit double-release race during concurrent consumer reads

- **File**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/ResultPartition.java:178-193`
- **Evidence**:
  ```java
  // ResultPartition.java:178-193
  public void close() {
      finished = true;
      if (!queue.offer(END_OF_STREAM)) {
          if (bufferPool != null) {
              int discarded = queue.size();        // <-- snapshot N
              for (int i = 0; i < discarded; i++) {
                  bufferPool.release();            // <-- release N permits
              }
          }
          queue.clear();                           // <-- clears M elements (M <= N if consumer read between)
          queue.offer(END_OF_STREAM);
      }
  }
  ```
  The producer thread calls `close()`; the consumer thread concurrently calls `read()` which does `queue.take()` + `bufferPool.release()`. Between `queue.size()` (returns N) and `queue.clear()`, the consumer may have already consumed (N−M) elements and released (N−M) permits via its own `read()`. The `close()` then releases N more permits. Total releases for those elements: N (from close) + (N−M) (from consumer) = 2N−M, but only N permits were ever acquired for them. The `Semaphore` ends up with more available permits than its configured capacity.
- **Priority**: **[P2]** — Non-blocking polish: weakens the global backpressure guarantee (BufferPool can exceed nominal capacity) in a narrow race during partition shutdown. Does not cause crashes or hangs (over-release, not under-release). The data-loss aspect of `close()` discarding queued elements is already covered by multi-audit P1-10; this finding is the distinct permit-accounting consequence.
- **Status**: New (permit-accounting angle not covered by multi-audit P1-10 which covers data loss).
- **Risk**: The cross-partition global in-flight element bound (`BufferPool.totalCapacity`) can be temporarily exceeded, undermining the memory-budget guarantee that the pool exists to enforce. In a fan-out with many partitions closing concurrently under load, the cumulative over-release could be non-trivial.
- **Recommendation**: Drain the queue element-by-element (poll + release per element) under no consumer contention, or accept the sentinel-via-clear path but compute `discarded` as the actual number removed by `clear()` (e.g., drain to a list and count). Alternatively, make `close()` not release permits at all and rely on `BufferPool.close()` (which releases the full capacity) to unblock producers — the per-partition permit accounting would then self-correct.
- **Confidence**: Likely (race is real; practical impact depends on shutdown timing)
- **Discovery source**: exception-path detective / 10x-scale operator

---

### [AR-6] `JobGraphGenerator` javadoc for `determinePartitionType` is misplaced — attached to `hasNonVirtualOperator`

- **File**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/jobgraph/JobGraphGenerator.java:509-554`
- **Evidence**:
  ```java
  // Lines 509-518: javadoc describing determinePartitionType / ResultPartitionType
  /**
   * Determines the ResultPartitionType based on the StreamEdge's partitioner.
   * ...
   */
  // Line 523: but this javadoc is actually attached to hasNonVirtualOperator
  private boolean hasNonVirtualOperator(List<StreamNode> chain) { ... }
  // Line 546: determinePartitionType has NO javadoc
  private ResultPartitionType determinePartitionType(StreamEdge streamEdge) { ... }
  ```
- **Priority**: **[P2]** — Non-blocking polish: doc-code mismatch. The javadoc block at lines 509-518 describes `determinePartitionType` but is syntactically attached to `hasNonVirtualOperator` (a completely unrelated method). `determinePartitionType` itself has no javadoc. IDEs and javadoc tools will render the wrong documentation for both methods.
- **Status**: New (not covered by prior audits).
- **Risk**: Misleads developers reading the code or generated docs. A developer inspecting `hasNonVirtualOperator` sees documentation about partition types, and vice versa.
- **Recommendation**: Move the javadoc block to immediately precede `determinePartitionType` (line 546). Add a brief javadoc to `hasNonVirtualOperator` describing its actual purpose (detecting whether a chain contains at least one non-virtual / concrete operator).
- **Confidence**: Certain

---

### [AR-7] `PartitionPolicy.UNION` and `PartitionPolicy.SINGLETON` are dead enum values — no production code ever produces them

- **Files**:
  - `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/plan/PartitionPolicy.java` — declares `UNION` and `SINGLETON`
  - `PartitionedPlanGenerator.inferPartitionPolicy()` — never returns either value
  - `GraphExecutionPlan.resolvePartitionPolicy()` — never returns either value
  - `rg "PartitionPolicy\.(UNION|SINGLETON)" nop-stream/ --include '*.java'` → **0 production references**
- **Evidence**:
  ```java
  // PartitionPolicy.java
  public enum PartitionPolicy {
      FORWARD, HASH, REBALANCE, BROADCAST, UNION, SINGLETON
  }
  ```
- **Priority**: **[P2]** — Non-blocking polish: dead enum values. They exist in the contract enum but no code path ever sets them. Code that exhaustively switches on `PartitionPolicy` will compile with dead branches for `UNION`/`SINGLETON` that are never exercised.
- **Status**: Known-unfixed (first reported 2026-07-24 flink-comparison AR-7; confirmed 0 references at current HEAD). Not covered by the 2026-07-25 multi-audit.
- **Risk**: Low. New code relying on these values for routing decisions would silently misroute. Primarily a maintenance/confusion cost.
- **Recommendation**: Either remove `UNION` and `SINGLETON` and re-add when implementations exist, or add a `@ReservedForFutureUse` javadoc tag noting they are unimplemented.
- **Confidence**: Certain
- **Discovery source**: dead-code scavenger

---

## Summary

| Severity | Count | Major Categories |
|----------|-------|-----------------|
| P0       | 0     | — |
| P1       | 4     | Shared mutable state across subtasks (AR-1, AR-4), optional-dependency hard linkage (AR-2), fragile string-based partitioner inference in two locations (AR-3) |
| P2       | 3     | Permit double-release race (AR-5), javadoc misplacement (AR-6), dead enum values (AR-7) |

**Total new findings: 7** (4 P1 + 3 P2; none overlapping with the 2026-07-25 multi-audit's 48 findings).

## Verdict on Mission Contract

The most acceleration-worthy finding is **AR-1** (`shallowCopyOperator` silent sharing). It directly undermines the mission's distributed-execution and exactly-once goals: any CEP or keyed-process pipeline with `parallelism > 1` silently corrupts state across subtasks, regardless of checkpoint correctness. This is the kind of bug that passes all single-parallelism unit tests and only manifests in production-scale distributed runs — exactly the scenario the `nop-stream-production` mission targets.

**AR-3** is the second concern: partitioner-policy inference by class-name substring matching exists in TWO independent locations (`PartitionedPlanGenerator` and `GraphExecutionPlan`), and the `GraphExecutionPlan` fallback defaults any non-null partitioner to `HASH`. Custom or future partitioners will be silently misrouted.

## Global Assessment

The module has demonstrably improved since the June 2026 audit baseline. Several previously-reported defects are now confirmed **fixed** at current HEAD:

- ✅ `StreamTaskInvokable` now calls `operatorChain.open()`/`close()` in all four role paths (Finding 2 / lifecycle — fixed)
- ✅ `GraphExecutionPlan.build()` now handles the `fanOutWriters + inputGate` case via a 4-arg constructor (Finding 8 — fixed)
- ✅ `JobEdge` now implements `equals()`/`hashCode()` (Finding 9 — fixed)
- ✅ `JobGraphGenerator.canChain()` now checks `ChainingStrategy` (NEVER/HEAD) and `factory.isChainable()` (Finding 1 — fixed)
- ✅ `WindowOperator` empty `else {}` blocks removed (flink-comparison AR-5 — fixed)
- ✅ `OperatorChain.open()` javadoc now correctly says "reverse order" (flink-comparison AR-6 — fixed)
- ✅ `LocalFileCheckpointStorage` path-traversal guard with `SAFE_ID_PATTERN` + canonical-path validation (security — addressed)

The remaining live findings (AR-1 through AR-4) are structural rather than cosmetic: the operator-copy chain silently shares mutable state, the connector module is effectively unusable without manually adding optional deps, and partitioner inference relies on class-name string matching. These are exactly the issues that "lights-out" changes (new operator types, new partitioners) would silently amplify.

## Audit Blind Spots

- **Performance / scaling**: No profiling or benchmark was run. The `SimpleStreamOperatorFactory` serialization deep copy (if ever reached) and the `hasNonVirtualOperator` double-creation of operators (called during chain identification AND vertex creation) have unknown real-world cost.
- **WindowOperator session-window merge correctness under checkpoint**: The multi-audit flags 4 `@Disabled` tests; this audit did not re-examine the merge logic in depth.
- **End-to-end CEP distributed test with parallelism > 1**: Not executed. AR-1 predicts corruption but no test demonstrates it at scale.
- **JdbcCheckpointStorage full SQL review**: Only the first 100 lines were examined; transaction boundaries and DDL idempotency beyond `ensureTable()` not verified.
- **Stream-flow XDSL module**: The `nop-stream-flow` module consists almost entirely of `_gen` model classes; the `precompile/gen-stream-xdsl.xgen` template was not audited for generation correctness.

<AI_STEP_RESULT>issues</AI_STEP_RESULT>
