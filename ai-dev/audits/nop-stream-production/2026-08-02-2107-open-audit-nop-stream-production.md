> Audit Status: planned
> Audit Type: open-ended
> Mission: nop-stream-production
> Planned To: AR-1 [P1] (CEP SharedBufferAccessor stack desync) → `ai-dev/plans/nop-stream-production/2026-08-04-2300-2-checkpoint-state-backend-cep-correctness.md` Phase 4. P2 findings (AR-2/AR-3/AR-4) → roadmap Follow-up Backlog.

# Open-Ended Adversarial Audit: nop-stream-production (Round 2)

- **Date**: 2026-08-04
- **Auditor**: AI (open-ended adversarial review per `ai-dev/skills/open-ended-adversarial-review-prompt.md`)
- **Target**: `nop-stream/` — all 10 submodules (core / runtime / cep / connector / connector-batch / connector-debezium / connector-jdbc / rocksdb / flow / fraud-example)
- **Heuristics used**: dead-code scavenger, exception-path detective, code-generation victim, and (primary) **pattern-matching / NFA correctness under overlapping release** — driven by reading the CEP module, which prior rounds treated as a blind spot.
- **Priority rubric**: every finding is prefixed `[P0]` / `[P1]` / `[P2]` per the mission-driver grading (P0 blocking, P1 material, P2 trivial/non-blocking polish). Severity inside each entry is additionally graded with the open-ended prompt's scale for traceability.
- **Dedup baseline**:
  - Multi-audit `2026-08-02-2107-multi-audit-nop-stream-production.md` (2 P0 + 13 P1 + 23 P2) — read in full; its coverage of JobCoordinator concurrency, CheckpointCoordinator, RocksDB backend, InputGate, SupervisionLoop, SPI/doc drift, and hollow tests is **not re-reported** here.
  - Prior open-audit `2026-07-25-1948-open-audit-nop-stream-production.md` (AR-1..AR-7) — each item re-verified against current HEAD; status recorded in §"Prior-audit re-verification".

This audit is **open-ended and discovery-oriented**. Its value-add over the multi-audit is coverage of the modules that round explicitly flagged as blind spots (CEP, connectors, fraud-example) plus a fresh re-confirmation that the prior open-audit's headline items have actually been resolved.

---

## Findings

### [AR-1] `SharedBufferAccessor.releaseNode()` desynchronizes the parallel `nodesToExamine` / `versionsToExamine` stacks on the null-entry branch — corrupted refcounts or `Lockable over-release` crash on branching CEP patterns

- **Priority**: **[P1]** — Material: silent refcount corruption (or hard operator crash via the over-release guard) in CEP matching whenever two overlapping `releaseNode` traversals touch the same subgraph. The common `begin().next()` + parallelism-1 unit test path does not exercise it; `followedByAny` / looping patterns with skip strategies and multiple concurrent partial matches do. Incorrect behavior on a correctness-critical path of a flagship feature (the `fraud-example` module is built on CEP).
- **File**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/sharedbuffer/SharedBufferAccessor.java:258-303`
- **Evidence**:
  ```java
  public void releaseNode(final NodeId node, final DeweyNumber version){
      Stack<NodeId> nodesToExamine = new Stack<>();
      Stack<DeweyNumber> versionsToExamine = new Stack<>();
      java.util.Set<NodeId> visited = new java.util.HashSet<>();
      nodesToExamine.push(node);
      versionsToExamine.push(version);

      while (!nodesToExamine.isEmpty()) {
          NodeId curNode = nodesToExamine.pop();           // pops 1 node
          if (!visited.add(curNode)) {
              versionsToExamine.pop();                      // OK: pops 1 version, stays in sync
              continue;
          }

          Lockable<SharedBufferNode> curBufferNode = sharedBuffer.getEntry(curNode);

          if (curBufferNode == null) {
              continue;                                     // BUG: does NOT pop a version
          }

          DeweyNumber currentVersion = versionsToExamine.pop();   // pops 1 version
          // ... iterate edges; on release push targetId + edge.getDeweyNumber() in lockstep (289-290)
      }
  }
  ```
- **现状**: The two stacks are pushed in lockstep (lines 262-263 init; lines 289-290 per released edge). The invariant the rest of the loop relies on is *"every iteration pops exactly one node AND one version"*. The already-visited branch honors it (line 268). The **`curBufferNode == null` branch at lines 274-276 does `continue` without popping a version.** From that iteration on, `versionsToExamine` is one element longer than `nodesToExamine`'s logical pairing: every subsequent iteration pairs the wrong `DeweyNumber` with the wrong `NodeId`, releasing edges that belong to a different path (corrupting unrelated matches' refcounts) or skipping edges that should have been released (memory leak). It also makes `versionsToExamine.pop()` (line 278) eventually throw `EmptyStackException` on a degenerate traversal, or trip `Lockable.release()`'s over-release guard (`Lockable` throws on refcount underflow), crashing the operator.
- **Reachability (confirmed)**: `releaseNode` is on the CEP hot path — called from `NFA.java:324,395,409,464,475,751`, `AfterMatchSkipStrategy.java:115`, and `CepOperator.java:561,621`. The `visited` set is **local to each `releaseNode` call** (line 261), so a node removed by call N (`removeEntry` at line 297) is observed as `null` by call N+1 over an overlapping subgraph. `SimpleKeyedStateStore.MapState.get` returns `null` for removed keys, so the null branch is definitely entered in production once any pruning overlaps — exactly what `followedByAny` + `SKIP_TO_FIRST` / `SKIP_PAST_LAST_EVENT` produce.
- **Risk**: For CEP jobs with branching patterns and after-match skip strategies (the realistic fraud-detection shape), SharedBuffer refcounts silently diverge. Outcomes, in increasing severity: (a) gradual unbounded `SharedBuffer`/`eventsBuffer` growth (entries never reach refcount 0) → checkpoint bloat → eventual OOM; (b) `Lockable over-release` exception → task FAILED → recovery loop, possibly non-recoverable if the corrupted state is checkpointed first; (c) wrong pattern extraction (the misaligned version can mark an unrelated edge as compatible) → false/missed fraud alerts. Combined with AR-2 below, the leaked entries never get a safety-net cleanup.
- **建议**: Trivial one-line fix — pop the version before continuing on the null branch:
  ```java
  if (curBufferNode == null) {
      versionsToExamine.pop();
      continue;
  }
  ```
  Add a regression test (`TestSharedBufferExtended`) that releases a node, then issues a second `releaseNode` whose traversal reaches the now-removed entry (the existing `testReleaseNodeContinuesAfterNullIntermediateNode` does **not** exercise this — it walks a live linear chain where `getEntry` never returns null).
- **信心水平**: 确定 (logic is unambiguous; reachability and test-gap confirmed by grep).
- **发现来源视角**: exception-path detective / dead-code scavenger (started from "what does the CEP safety-net cleanup actually guard?" and traced back into the release walk).

---

### [AR-2] `CepOperator` dangling-partial-match safety net only fires when `partialMatches.size() == 1` — stale entries from branching patterns are never reclaimed

- **Priority**: **[P2]** — Non-blocking polish, but a real amplifier of AR-1: the documented "remove dangling partial matches" safety net (comment at `CepOperator.java:539`) is gated on exactly one partial match and zero completed matches. Branching patterns (`followedByAny`, looping quantifiers) routinely hold >1 partial match, so the safety net is inert precisely where AR-1 leaks the most. Primary cleanup is supposed to be `NFA.advanceTime`, so this is a defense-in-depth gap, not a primary defect.
- **File**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java:540` (onEventTime) and `:600` (onProcessingTime) — duplicated block.
- **Evidence**:
  ```java
  // In order to remove dangling partial matches.
  if (nfaState.getPartialMatches().size() == 1 && nfaState.getCompletedMatches().isEmpty()) {
      boolean allTimedOut = true;
      for (Object pm : nfaState.getPartialMatches()) { ... }
      if (allTimedOut) { ... accessor.releaseNode(...); computationStates.clear(); }
  }
  ```
- **现状**: The gate `size() == 1` means whenever two or more partial matches are simultaneously stale (e.g. after `advanceTime` under-prunes, or after AR-1 corrupts the release walk), the cleanup is skipped entirely. The same block is duplicated in `onEventTime` and `onProcessingTime` with the same limitation.
- **风险**: Stale partial matches and their `SharedBuffer` entries are retained indefinitely under branching patterns. Standalone this is a slow leak; multiplied by AR-1 it is the difference between "self-healing" and "monotonic growth to OOM".
- **建议**: Generalize the cleanup to "all current partial matches are timed out" regardless of count (drop the `size() == 1` conjunct, or iterate all partial matches and release each timed-out one). De-duplicate the two copies into a shared helper.
- **信心水平**: 很可能 (the gate is directly visible at both sites; impact depends on pattern shape and AR-1).

---

### [AR-3] CEP state value classes dropped `implements Serializable` vs the upstream Flink originals — latent risk under any Java-serialization-based state backend

- **Priority**: **[P2]** — Non-blocking: the current state-serialization path is JSON-based (`MemoryStateSerDe.deserializeValue`, `nop-stream-core/.../MemoryStateSerDe.java:780`) and no Java `ObjectOutputStream.writeObject` was found on the RocksDB value path, so this very likely does **not** break production today. Recorded because (a) it is a real deviation from the Flink originals (every one of these classes `implements Serializable` upstream), (b) `Lockable.refCounter` is an `AtomicInteger` which is not meaningfully Java-serializable, and (c) if a future backend or the `IStreamSerializer` escape hatch (already flagged by the multi-audit on `StateDescriptor`) routes these through Java serialization, the failure would be an opaque `NotSerializableException` deep in checkpointing. Honest low confidence; do not spend remediation budget unless CEP-on-RocksDB checkpointing is independently confirmed broken.
- **Files**: `nop-stream-cep/.../nfa/NFAState.java:28`, `ComputationState.java:33`, `nfa/sharedbuffer/EventId.java:27`, `NodeId.java:26`, `SharedBufferNode.java:28`, `SharedBufferEdge.java:28`, `Lockable.java:35`. (Siblings `DeweyNumber`, `State`, `StateTransition`, `AfterMatchSkipStrategy` *do* implement `Serializable` — the omission is inconsistent within the same package.)
- **Evidence**:
  ```java
  public class NFAState { ... }              // upstream: implements Serializable
  public class ComputationState { ... }      // upstream: implements Serializable
  public class EventId implements Comparable<EventId> { ... }   // upstream: + Serializable
  public class NodeId { ... }                // upstream: implements Serializable
  public final class Lockable<T> {           // upstream: implements Serializable; refCounter is AtomicInteger
  ```
- **现状**: All seven CEP state classes are used as `ValueState`/`MapState` values/keys (`SharedBuffer.java:106-125`, `CepOperator.java:270`). None declares `Serializable`; `Lockable` additionally holds an `AtomicInteger`. The in-memory `SimpleKeyedStateStore` used by every CEP test stores live references and never serializes, so the gap is invisible to the test suite.
- **风险**: Latent. If the production RocksDB keyed-state value path (or a user-supplied `IStreamSerializer`) ever falls back to Java serialization for these types, checkpoint fails with `NotSerializableException` referencing `PriorityQueue`/`AtomicInteger`/synthetic classes — opaque diagnostic on the first production CEP checkpoint. No current evidence it breaks (JSON path + passing CEP checkpoint E2E tests suggest it works today).
- **建议**: Either restore `implements Serializable` on the seven classes (mark `Lockable.refCounter` `transient` and rebuild it on deserialize), or add a Javadoc invariant to `SharedBuffer`/`CepOperator` stating that CEP state must only ever be persisted via the JSON/`IStreamSerializer` path and will never round-trip through Java serialization.
- **信心水平**: 有趣的猜测 (deviation is certain; production impact is speculative and probably absent given JSON SerDe).

---

### [AR-4] `TestGeographicAnomalyPatternFix` re-implements the `city2` IterativeCondition inline instead of exercising `GeographicAnomalyPattern.createPattern()` — zero bug-catching power on the production condition

- **Priority**: **[P2]** — Non-blocking polish: same hollow-test anti-pattern family the multi-audit already flagged for `TestTaskExecutorDaemonThreads` / `TestSinkTransformation`. Recorded because the class name advertises a "Fix" (implies guarding a real regression) yet it cannot catch any drift in the production pattern. Does not by itself warrant a remediation plan.
- **File**: `nop-stream/nop-stream-fraud-example/src/test/java/io/nop/stream/fraud/pattern/TestGeographicAnomalyPatternFix.java:19-60`
- **Evidence**:
  ```java
  @Test
  void testCity2FilterIteratesAllCity1Events() throws Exception {
      IterativeCondition<TransactionEvent> condition = new IterativeCondition<TransactionEvent>() {
          @Override
          public boolean filter(TransactionEvent value, Context<TransactionEvent> ctx) throws Exception {
              for (TransactionEvent city1Event : ctx.getEventsForPattern("city1")) { ... }   // re-declared inline
          }
      };
      // ... asserts on this locally-declared condition, NOT on GeographicAnomalyPattern.createPattern()
  }
  ```
- **现状**: Both `@Test` methods redeclare the `city2` filter logic verbatim and assert against the local copy. `GeographicAnomalyPattern.createPattern()` (the production method, `GeographicAnomalyPattern.java:73-102`) is never referenced. If the production condition drifts (e.g. someone changes the `.where(...)` predicate), this test still passes.
- **风险**: False coverage advertising a "fix"; future regressions in the real pattern condition go undetected. Maintenance cost with no verification value.
- **建议**: Rewrite to extract the real condition from `createPattern()` (or factor the condition into a testable static field on `GeographicAnomalyPattern`) and assert against *that*. Alternatively delete and rely on `TestGeographicAnomalyPattern` if it already covers the production path.
- **信心水平**: 确定.

---

## Prior-audit re-verification (positive signal)

The 2026-07-25 open-audit's headline items were each re-checked against current HEAD. **All material prior findings are now resolved.** Recorded here so downstream remediation planning does not re-scope already-fixed work, and to surface that the module has measurably progressed.

| Prior finding | Status at current HEAD | Evidence |
|---|---|---|
| prior-AR-1 `OperatorChain.shallowCopyOperator()` silent shared instance | **FIXED** | The `instanceof` chain is gone. Replaced by a proper `StreamOperator.copyForSubtask()` SPI method (`StreamOperator.java:181-188`) whose default impl **throws** if not overridden. `ProcessOperator`, `TimestampsAndWatermarksOperator`, `SourceReaderOperator`, `StreamMap/Filter/FlatMap`, `StreamSourceOperator` all override it. `grep shallowCopyOperator` → 0 hits in core. |
| prior-AR-2 `StreamConnectors` hard-references optional batch/debezium types | **FIXED** | Base `nop-stream-connector/pom.xml` has no `nop-batch-core`/`nop-message-debezium` deps; `StreamConnectors.java`, `BatchConsumerSinkFunction`, `BatchLoaderSourceFunction` moved to new `nop-stream-connector-batch/`; `DebeziumCdcSourceFunction` moved to `nop-stream-connector-debezium/`. The pom carries an explicit AR-2 remediation comment. |
| prior-AR-3 partitioner class-name string matching (two sites) | **FIXED** | `grep 'contains("Hash")'/'contains("Rebalance")'/'contains("Broadcast")'` → 0 hits. Only clean `PartitionPolicy.HASH` references remain (`PartitionRouter.java`, `DataStreamImpl.java:369`). |
| prior-AR-4 `SimpleStreamOperatorFactory` silent shared-instance fallback | **FIXED** | `createStreamOperator()` now returns a shared instance **only** for explicitly `isShareable()` operators (logged WARN, `:50-54`); on `NotSerializableException` it **throws** `StreamException` fail-fast (`:79-83`); non-Serializable non-shareable also throws (`:91-95`). The remaining `return operator` at `:99` is the separate `getRawOperator()` accessor. |
| prior-AR-5 `ResultPartition.close()` permit double-release race | not re-verified | P2 only; out of this round's focus. Left for the follow-up backlog. |
| prior-AR-6 `JobGraphGenerator.determinePartitionType` misplaced Javadoc | not re-verified | P2 only; left for the follow-up backlog. |
| prior-AR-7 `PartitionPolicy.UNION/SINGLETON` dead enum values | **FIXED** | `grep 'PartitionPolicy\.(UNION\|SINGLETON)'` → 0 references in `nop-stream/`. |

This is a strong positive trajectory: the structural shared-state and class-loading defects that previously undermined the distributed-execution / exactly-once contract have been closed at the SPI boundary (copy + shareability contract) and the module boundary (connector split), rather than patched inline.

---

## 总评 (Global assessment)

The 1–3 directions most worth attention now:

1. **CEP correctness under overlapping `releaseNode` traversals (AR-1).** This is the single highest-value finding. It is exactly the "lights-out" class of bug the prior rounds did not catch: invisible to single-parallelism / linear-pattern unit tests, material on the branching + skip-strategy patterns that real fraud/anomaly detection uses, and a one-line fix. It should be fixed and regression-tested before any production CEP rollout — which is squarely in scope for the `nop-stream-production` mission.
2. **CEP cleanup defense-in-depth (AR-2).** Not a primary defect, but the safety net that should contain AR-1's leaks is gated out for the patterns that need it most. Cheap to generalize; do it together with AR-1.
3. **Connector modules are in markedly better shape than the round-1 baseline suggested.** The JDBC two-phase-commit sink has a sound epoch-ledger idempotency guard (single-transaction data+ledger write, atomic rollback), `BatchConsumerSinkFunction` documents its single-thread contract (P1-15) and fails fast on null, and the Debezium source round-trips the CDC offset through `NopStreamOffsetBackingStore` correctly. The remaining connector-side notes are low-confidence (AR-3) or test-quality (AR-4).

净观感：相比 2026-07-25 基线，模块在生产硬化方向上有实质性进步（前一轮的结构性发现全部在 SPI/模块边界层面被关闭）。当前最值得立刻处理的是 CEP 子系统在重叠释放路径下的正确性——这是一个被前两轮审查灯下黑的真实缺陷。

---

## 本次审查的盲区自评 (Blind spots)

- **未运行测试**：未执行 `./mvnw test -pl nop-stream -am`。AR-1 的根因是机械证明（代码结构层面无可争议），但没有用复现用例确认它在真实 CEP 作业上崩溃。建议下游 remediation 先写一个 `followedByAny` + `SKIP_TO_FIRST` 的复现测试来锁定行为。
- **BatchLoaderSourceFunction / StreamConnectors.fromBatchLoader**：只读了 sink 侧，batch source 侧的检查点参与/错误处理未深读。
- **Kafka/Pulsar source 与 `@EnabledIfSystemProperty` E2E**：本轮未执行；AR-1 之外未做端到端验证。
- **`nop-stream-flow` XDSL 生成模板**：仍主要由 `_gen` 模型类构成，`precompile/gen-stream-xdsl.xgen` 模板的生成正确性未审计（与 2026-07-25 的盲区声明一致）。
- **WindowOperator 会话窗口合并 + 检查点的交互**：multi-audit 已标记 4 个 `@Disabled` 测试；本轮未深挖合并逻辑。
- **CEP 的 NFA.advanceTime 与算子层窗口语义差异 / `SharedBuffer.advanceTime` 不清理 `eventsBuffer`**：作为 AR-1 的潜在放大器被 explorer 提出，但因依赖"用户混用 global + per-state window"等前提，未单独成条（避免凑数）；如 AR-1 修复后仍观察到内存增长，应回查这两点。

---

## 按严重程度分布表

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0      | 0    | —       |
| P1      | 1    | CEP SharedBuffer refcount 正确性（AR-1，重叠释放路径下并行栈失配） |
| P2      | 3    | CEP 安全网清理门槛失效（AR-2）、CEP 状态类缺失 Serializable（AR-3，潜在）、fraud-example 空心测试（AR-4） |

**Total new findings: 4** (1 P1 + 3 P2; none overlapping with the 2026-08-02 multi-audit's 38 findings or the resolved prior-AR-1..AR-7 set).

<AI_STEP_RESULT>issues</AI_STEP_RESULT>
