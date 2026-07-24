> Audit Status: planned
> Audit Type: open-ended
> Mission: nop-stream-flink-comparison

# Open-Ended Adversarial Audit: nop-stream (Flink Comparison Mission)

**Date**: 2026-07-24
**Auditor**: AI (open-ended adversarial review per `ai-dev/skills/open-ended-adversarial-review-prompt.md`)
**Target**: `nop-stream/` — all 6 submodules
**Heuristics used**: Code-generation victim, IoC detective, dead-code scavenger, 10x-scale operator, transaction-boundary tracker

This audit is **open-ended and discovery-oriented**. It does not reuse the dimensions of the 2026-07-24 multi-audit (`docs/audits/nop-stream-flink-comparison/2026-07-24-2227-multi-audit-nop-stream-flink-comparison.md`). Known findings from that report (P1: 6 items, P2: 2 items) are assumed pre-existing and not re-reported here unless the impact has materially changed.

---

## Findings

### [AR-1] OperatorChain.deepCopy() silently shares mutable operator state for unknown operator types

- **Files**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/jobgraph/OperatorChain.java:205-234`
- **Evidence**:
  ```java
  // OperatorChain.java:205-234
  private static StreamOperator<?> shallowCopyOperator(StreamOperator<?> op) {
      if (op instanceof StreamSourceOperator) { ... return new StreamSourceOperator<>(...); }
      if (op instanceof StreamMap) { ... return new StreamMap<>(...); }
      if (op instanceof StreamFilter) { ... return new StreamFilter<>(...); }
      if (op instanceof StreamFlatMap) { ... return new StreamFlatMap<>(...); }
      if (op instanceof StreamSinkOperator) { ... return new StreamSinkOperator<>(...); }
      if (op instanceof StreamReduceOperator) { ... return new StreamReduceOperator<>(...); }
      return op;  // <-- SILENT SHARED INSTANCE
  }
  ```
  Missing types (not handled): `CepOperator`, `ProcessOperator` (added in Plan 305), `WindowOperator` (runtime), `TimestampsAndWatermarksOperator`, `ChainingOutput`, and any user-defined custom operator.
- **Severity**: P1 — **Material: data corruption risk**. All parallel subtasks for an unhandled operator type receive the **same operator instance** with shared mutable state (output, watermark tracking, timer services, internal accumulators). This causes race conditions, double-processing, and checkpoint corruption.
- **Status**: Pre-existing; the `ProcessOperator` (Plan 305) was added without updating `shallowCopyOperator()`.
- **Risk**: Any multi-subtask pipeline using CEP (`CepOperator`), `ProcessFunction` (`ProcessOperator`), or `WindowOperator` (runtime) with parallelism > 1 will corrupt state across subtasks. Tests with `parallelism=1` (common in unit tests) do not expose this.
- **Recommendation**: Replace the `instanceof` chain with a `StreamOperator.copyForSubtask()` interface method. Every operator would then implement its own copy logic. Alternatively, register operator copy functions in a `Map<Class, Copier>`. At minimum, add cases for `CepOperator`, `ProcessOperator`, `WindowOperator`, `TimestampsAndWatermarksOperator`.
- **Confidence**: Certain
- **Discovery source**: Code-generation victim / 10x-scale operator

---

### [AR-2] StreamConnectors and connector classes cause hard linkage failure with optional dependencies

- **Files**:
  - `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/StreamConnectors.java:10-11` — imports `IBatchConsumerProvider`, `IBatchLoaderProvider` in method signatures
  - `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/BatchLoaderSourceFunction.java:13-16` — imports `IBatchLoaderProvider`, `IBatchTaskContext`, `BatchTaskContextImpl` in class body
  - `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/BatchConsumerSinkFunction.java:13-17` — imports `IBatchConsumerProvider`, `IBatchChunkContext`, `BatchChunkContextImpl`
  - `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/DebeziumCdcSourceFunction.java:18-20` — imports `DebeziumConfig`, `DebeziumMessageSource`, `ChangeEvent`
  - `nop-stream/nop-stream-connector/pom.xml:22-24, 33-36` — `nop-batch-core` (optional), `nop-message-debezium` (optional)
- **Evidence**:
  ```xml
  <!-- pom.xml:22-24 -->
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-batch-core</artifactId>
      <optional>true</optional>     <!-- <-- optional -->
  </dependency>
  ```
  ```java
  // StreamConnectors.java:26-31
  public static <S> DataStreamSource<S> fromBatchLoader(
          StreamExecutionEnvironment env,
          IBatchLoaderProvider<S> loaderProvider,   // <-- hard ref to optional dep
          String sourceName) { ... }
  ```
- **Severity**: P1 — **Material: `NoClassDefFoundError` at any use of `nop-stream-connector` without explicit `nop-batch-core` / `nop-message-debezium` deps**. Java class resolution fails when `StreamConnectors` class is loaded because its method signatures reference types from the optional dependency. Every downstream user of `nop-stream-connector` must add `nop-batch-core` and `nop-message-debezium` as explicit dependencies even if they never use batch/Debezium connectors.
- **Status**: Pre-existing. The `optional=true` marker only suppresses transitive propagation — it does not protect against class-loading failure.
- **Risk**: Silent runtime `NoClassDefFoundError` when `StreamConnectors.class`, `BatchLoaderSourceFunction.class`, or `DebeziumCdcSourceFunction.class` are loaded. The error occurs at class-init time, not at method-entry time.
- **Recommendation**: Extract batch-specific and debezium-specific connector classes into separate Maven modules (`nop-stream-connector-batch`, `nop-stream-connector-debezium`) with non-optional deps on the respective libraries. Keep only non-optional source/sink types (like `MessageSourceFunction`, `MessageSinkFunction` which depend on `nop-message-core` — which is test-scoped only, but `IMessageService` is from `nop-api-core`, not `nop-message-core`) in the base `nop-stream-connector` module.
- **Confidence**: Certain
- **Discovery source**: 10x-scale operator / IoC detective

---

### [AR-3] PartitionedPlanGenerator.inferPartitionPolicy() uses fragile class-name string matching

- **File**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/graph/PartitionedPlanGenerator.java:66-82`
- **Evidence**:
  ```java
  // PartitionedPlanGenerator.java:73-80
  String partitionerClassName = edge.getPartitioner().getClass().getName();
  if (partitionerClassName.contains("Hash") || partitionerClassName.contains("hash")) {
      return PartitionPolicy.HASH;
  } else if (partitionerClassName.contains("Rebalance") || partitionerClassName.contains("rebalance")) {
      return PartitionPolicy.REBALANCE;
  } else if (partitionerClassName.contains("Broadcast") || partitionerClassName.contains("broadcast")) {
      return PartitionPolicy.BROADCAST;
  }
  return PartitionPolicy.FORWARD;
  ```
- **Severity**: P1 — **Material: silent policy misclassification for custom partitioners**. A custom partitioner named `HashLookupPartitioner` (semantically a broadcast or forward) is classified as `HASH`. A `RebalanceToLeaderPartitioner` (semantically forward) is classified as `REBALANCE`. Furthermore, the method never infers `UNION` or `SINGLETON` even though the `PartitionPolicy` enum declares them.
- **Status**: Pre-existing; the `ForwardPartitioner` class exists and is handled via `PartitionPolicyAware` interface at line 70-72, but only for partitioners that explicitly implement it.
- **Risk**: Wrong partitioning in distributed execution — data may be sent to wrong downstream subtasks, causing incorrect results or deadlocks. The misclassification is silent (no warning, no error).
- **Recommendation**: Replace string matching with `instanceof` checks against known partitioner interfaces (`IPartitioner` specializations with known policies). Add explicit error for unrecognized partitioner types or a `PartitionPolicy getPolicy()` method to the partitioner interface. Add `UNION` and `SINGLETON` coverage.
- **Confidence**: Certain
- **Discovery source**: Model attacker / code-generation victim

---

### [AR-4] SimpleStreamOperatorFactory.createStreamOperator() silently falls back to shared instance on serialization failure

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
          throw new StreamException("Failed to create copy...");
      }
  }
  return operator;  // <-- also returns shared instance for non-Serializable
  ```
- **Severity**: P1 — **Material: silent fallback to shared mutable state**. When an operator contains non-serializable fields (e.g., lambdas, runtime-constructed resources), the serialization deep copy silently falls back to returning the shared template instance. While this path is not reached via `JobGraphGenerator.createOperatorFromFactory()` (which uses `getRawOperator()` directly), it IS reachable via any code that calls `createStreamOperator()` on a `SimpleStreamOperatorFactory` — e.g., if `StreamOperatorFactory` is used as a direct factory SPI.
- **Status**: Pre-existing. The `catch (NotSerializableException)` suggests this was intentional for lambdas, but the silent fallback is dangerous.
- **Risk**: Any subtask-specific caller of `createStreamOperator()` gets the same shared instance. Parallel subtasks share mutable state (output, timer registrations, checkpoint tracking), leading to silent data corruption.
- **Recommendation**: At minimum, log a `WARN` when falling back to the shared instance. Better: throw `StreamException` unless the operator explicitly declares it is safe to share (e.g., via a `@ThreadSafe` marker interface).
- **Confidence**: Certain
- **Discovery source**: Code-generation victim

---

### [AR-5] WindowOperator has empty else blocks indicating incomplete refactoring

- **File**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java:606, 663`
- **Evidence**:
  ```java
  // WindowOperator.java:599-609
  MergingWindowSet<W> mergingWindows;
  if (windowAssigner instanceof MergingWindowAssigner) {
      mergingWindows = getMergingWindowSet();
      W stateWindow = mergingWindows.getStateWindow(triggerContext.window);
      if (stateWindow == null) {
          // Timer firing for non-existent window...
          return;
      } else {
          // <-- EMPTY ELSE BLOCK
      }
  } else {
      mergingWindows = null;
  }
  ```
  Same pattern at `onProcessingTime()` line 662-663.
- **Severity**: P2 — **Trivial: dead code / style**. Empty `else` does not affect behavior, but indicates the `return` statement in the `if` branch may have been a premature addition that left the `else` branch orphaned. The pattern is suspicious: if `stateWindow == null`, the method returns. But `stateWindow` is only used later at line 614. If `stateWindow` were truly needed, the code path would be inconsistent.
- **Status**: Pre-existing structural residue from the WindowOperator refactoring (Plan 303, Phase 2 WindowAggregationOperator deletion).
- **Risk**: Confuses code readers. May mask a real logic gap: if `stateWindow` is `null`, the method returns early even if there was meaningful work to do (e.g., a timer callback for a non-merging window that happens to also be a `MergingWindowAssigner`).
- **Recommendation**: Remove the empty `else {}` blocks. If the early return for `stateWindow == null` is correct, the `else` is unnecessary dead code.
- **Confidence**: Certain

---

### [AR-6] OperatorChain.open() javadoc contradicts implementation (forward vs. reverse order)

- **File**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/jobgraph/OperatorChain.java:92-93, 98-110`
- **Evidence**:
  ```java
  // Javadoc line 92-93 (emphasis mine):
  // "*The operators are opened in forward order.*"

  // Implementation lines 98-110:
  public void open() {
      ...
      for (int i = operators.size() - 1; i >= 0; i--) {   // <-- REVERSE order
          operators.get(i).open();
          ...
      }
  }
  ```
  Same reverse order for `close()` (line 142): `for (int i = operators.size() - 1; i >= 0; i--)`.
- **Severity**: P2 — **Trivial but misleading: doc-code contradiction**. The actual reverse-open may be intentional (preparing downstream operators before upstream ones), but the javadoc says the opposite. A developer relying on the javadoc to write an `open()` hook that assumes forward-order initialization will encounter subtle bugs.
- **Status**: Pre-existing; the reverse-open may be correct for the nop-stream execution model (output wiring needs downstream operators ready first), but it is undocumented and undocumented deviation from Flink convention.
- **Risk**: Operator `open()` hooks that depend on upstream state (e.g., reading watermark from predecessor) will fail silently when opened in reverse order. Conversely, `close()` is correctly reverse-ordered (matching typical resource-teardown convention).
- **Recommendation**: Either (a) fix the javadoc to say "reverse order" and document the rationale, or (b) if forward order was intended, reverse the loop direction. After fixing, verify that no operator `open()` hook depends on the (current or corrected) order.
- **Confidence**: Likely

---

### [AR-7] PartitionPolicy enum values UNION and SINGLETON are dead code

- **Files**:
  - `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/plan/PartitionPolicy.java:10-18` — declares `UNION` and `SINGLETON`
  - `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/graph/PartitionedPlanGenerator.java:66-82` — never infers either value
  - grep over entire `nop-stream/` for "PartitionPolicy.UNION" and "PartitionPolicy.SINGLETON" — no production references
- **Evidence**:
  ```java
  // PartitionPolicy.java:10-18
  public enum PartitionPolicy {
      FORWARD, HASH, REBALANCE, BROADCAST, UNION, SINGLETON
  }
  ```
  `rg "PartitionPolicy\.(UNION|SINGLETON)" nop-stream/ --include '*.java'` → no results in production source (test files may reference them for completeness, but no runtime code ever produces them).
- **Severity**: P2 — **Trivial: dead enum values**. These values exist in the contract enum but no code path ever sets them. They represent planned-but-not-implemented partitioning strategies.
- **Status**: Pre-existing design gap.
- **Risk**: Code that switches on `PartitionPolicy` and exhaustively covers all enum values (e.g., in serialization or plan building) will compile, but the branches for `UNION` and `SINGLETON` are dead (never exercised). Newer code that relies on these values for routing decisions would silently misroute.
- **Recommendation**: Either (a) remove `UNION` and `SINGLETON` and add them back when implementations exist, or (b) add a `@Deprecated` javadoc tag noting they are reserved for future use.
- **Confidence**: Certain
- **Discovery source**: Dead-code scavenger

---

## Summary

| Severity | Count | Major Categories |
|----------|-------|-----------------|
| P0       | 0     | — |
| P1       | 4     | Shared mutable state across subtasks (2 issues), optional dependency hard linkage (1), fragile string-based partitioner inference (1) |
| P2       | 3     | Empty else blocks, doc-code contradiction, dead enum values |
| P3       | 0     | — |

## Total Findings: 7 (new, not in previous multi-audit)

## Verdict on Mission Contract

**Accelerated risk**: The `OperatorChain.deepCopy()` silent-sharing bug (AR-1) directly undermines the mission's Phase 5-7 goals (distributed execution, exactly-once correctness). Any distributed pipeline using `CepOperator` or `ProcessOperator` with parallelism > 1 will produce incorrect results regardless of checkpoint correctness. This P1 should be triaged ahead of all other issues.

**Module-boundary concern**: The connector optional-dependency issue (AR-2) means `nop-stream-connector` is effectively non-usable without manually adding `nop-batch-core` and `nop-message-debezium`. This violates the "optional dependency" contract and will cause frustration for first-time users.

## Global Assessment

The nop-stream module shows solid progress from the earlier audit baseline (June 2026 coverage issues, 283/303/304 plan fixes). The code quality is generally good — NopException usage is consistent, imports follow convention, no `@Inject private` anti-patterns, no bare RuntimeException in production code. The issues found here are structural rather than cosmetic: shared-state problems in the deep-copy chain, optional dependency misuse, and fragile inference logic. These are exactly the kind of issues that "lights-out" changes (new operator types, new partitioners) would silently amplify.

## Audit Blind Spots

- **Performance**: No profiling or benchmark was run. The `SimpleStreamOperatorFactory` serialization deep copy, if triggered, is expensive; its actual invocation frequency is unknown.
- **Serialization round-trip fidelity**: The serialization-based copy in `SimpleStreamOperatorFactory` may fail silently for graph-like operator structures (circular references, non-serializable anonymous classes).
- **WindowOperator merge correctness for session windows**: The existing multi-audit already flags this (4 `@Disabled` tests). This audit did not re-examine the merge logic in detail.
- **End-to-end CEP distributed test**: Not executed. The `TestDistributedExactlyOnce` test does not cover CEP pipelines.
- **Third-party dependency audit**: Not performed.

<AI_STEP_RESULT>issues</AI_STEP_RESULT>
