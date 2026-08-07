# Stage 12 — CEP / NFA / SharedBuffer Evidence

> Status: produced by Stage 12 audit (plan `nop-stream-independent-audit/2026-08-08-0514-2-cep-nfa-sharedbuffer-audit.md`)
> Domain: manifest a/f/g (CEP public entry + NFA + SharedBuffer + AfterMatchSkipStrategy source surface in nop-stream-cep, the fraud-example module surface, and the in-process/operator-driven test lane)
> Lane policy: only `in-process` lane (single-JVM operator-chain wiring) or stronger is credited for CEP matching, SharedBuffer lifetime, dangling cleanup and checkpoint-continuation claims; `unit` is component-only. nop-stream-cep has ZERO `env.execute()`-level tests — every "E2E" test is operator-driven (in-process), honestly classified here. Any capability needing cross-JVM control-plane/HA is `blocked` or `residual-risk` per Stage 5.
> Validator: `node ai-dev/tools/check-nop-stream-audit-manifest.mjs evidence` (parses `@@EVIDENCE` rows from `*.evidence.md` direct children of this dir)
> All source/test anchors in this file were verified against the live repo on 2026-08-08.

## CEP Support/Reject Matrix (frozen by this audit)

This matrix adjudicates every supported CEP public-entry / NFA-matching / skip / timeout / SharedBuffer / checkpoint combination. Each row cites the live source anchor that implements it. The matrix changes neither the 11 evidence-row fields nor the 7-value disposition vocabulary (frozen by Stage 4 `evidence-schema.md`).

| # | Capability | Verdict | Lane | Live anchor (implementing) | Evidence row |
| --- | --- | --- | --- | --- | --- |
| CEP1 | Keyed CEP entry (`CEP.pattern()` → `PatternStream` → keyed `CepOperator`) | **SUPPORTED** | in-process | `CEP.pattern():38`; `PatternStreamBuilder.build():146-160` (keyed transform `"CepOperator"`) | EVID-S12-001 |
| CEP2 | Non-keyed CEP entry (`forceNonParallel`, M7-2-P0-1 FIXED) | **SUPPORTED** (regression proves fix) | in-process | `PatternStreamBuilder.build():161-169`; `SingleOutputStreamOperatorImpl.forceNonParallel():52-58` (calls `lockParallelismToOne()` no longer throws) | EVID-S12-002 |
| CEP3 | NFA linear matching (begin/next, TAKE) | **SUPPORTED** | in-process | `NFA.process():236-251` → `doProcess():356-433`; `computeNextStates():621-756` | EVID-S12-003 |
| CEP4 | NFA state transition (TAKE/IGNORE/PROCEED) | **SUPPORTED** | in-process | `NFA.computeNextStates():621-756`; `findFinalStateAfterProceed():780-806` | EVID-S12-004 |
| CEP5 | NFA branching matching (`followedByAny`, DeweyNumber versioning) | **SUPPORTED** | in-process | `NFA.computeNextStates():621-756` (DeweyNumber branching); `addComputationState():758-778` (`lockNode`) | EVID-S12-005 |
| CEP6 | SharedBuffer release/lockstep (O8-2-AR-1 null-branch fix) | **SUPPORTED** (null-branch fix proven; live) | in-process | `SharedBufferAccessor.releaseNode():258-308` (parallel stacks `:259-263` + null-branch fix `:274-281`) | EVID-S12-006 |
| CEP7 | Lockable refcount release (M7-2-P2-8 FIXED) | **SUPPORTED** (FIXED, throws `StreamRuntimeException`) | in-process | `Lockable.release():56-66` (now throws `StreamRuntimeException:62`); `releaseOrDetach():68-81` | EVID-S12-007 |
| CEP8 | Skip strategies (NoSkip/SkipPastLast/SkipToNext/SkipToFirst/SkipToLast) | **SUPPORTED** | in-process | `AfterMatchSkipStrategy.prune():101-123`; `SkipPastLastStrategy.getPruningId():36-49` | EVID-S12-008 |
| CEP9 | Event-time / processing-time timeout | **SUPPORTED** (processing-time non-deterministic caveat) | in-process | `NFA.advanceTime():265-346`; `CepOperator.processTimedOutSequences():718-732` | EVID-S12-009 |
| CEP10 | Dangling safety net (O8-2-AR-2 size==1 proven; size>1 gap) | **residual-risk** (size==1 proven; size>1 branching not reclaimed at operator level) | in-process | `CepOperator.onEventTime():540`; `onProcessingTime():600` (size==1 predicate) | EVID-S12-010 |
| CEP11 | CEP checkpoint continuation (NFAState + SharedBuffer + watermark + timer round-trip) | **SUPPORTED** | in-process | `CepOperator.snapshotState():419-426`; `restoreState():429-447`; `open():259` (applyPendingRestoreState) | EVID-S12-011 |
| CEP12 | Watermark persistence round-trip | **SUPPORTED** | in-process | `CepOperator.processWatermark():450-459`; watermark state `:415` | EVID-S12-012 |
| CEP13 | CEP state value classes non-Serializable (O8-2-AR-3) | **residual-risk** (latent Java-serialization risk; non-blocking, current backend uses platform serializer) | none | 7 non-Serializable classes (NFAState/ComputationState/EventId/NodeId/SharedBufferNode/SharedBufferEdge/Lockable) | EVID-S12-013 |
| CEP14 | Dangling cleanup test-effectiveness (M7-2-P0-4 FIXED) | **SUPPORTED** (FIXED, test now asserts) | in-process | `TestCepOperatorDanglingCleanup:114-122` now asserts `partialMatchesEmpty` | EVID-S12-014 |
| CEP-FRAUD | Fraud-example scope (4 linear patterns, no `env.execute()`) | **non-goal** (example surface, fail-fast/semantic only) | none | `fraud-detection.stream.xml:71-72`; `FraudDetectionDemo.java:52` (no `env.execute()`) | EVID-S12-021 |
| CEP-GAP | no-`env.execute()` CEP coverage gap | **residual-risk** (all "E2E" tests are operator-driven) | in-process | nop-stream-cep has ZERO `env.execute()` tests | EVID-S12-022 |

Adjudication rules applied (consistent with Stage 4 schema + Stage 5 supplement):
- A supported CEP capability gets a source-to-output evidence row with `disposition: e2e-proved` when an in-process (operator-driven) test traces the chain end-to-end, or an honest weaker disposition when only a segment is exercised.
- A non-deterministic lane (processing-time wall-clock) is honestly annotated, never silently upgraded to a deterministic-claim `e2e-proved` (Rule #24).
- The dangling safety net's `size>1` branching gap is explicitly marked `residual-risk` + successor (Stage 17), never silently treated as fully `e2e-proved`.
- The non-Serializable latent risk is explicitly marked `residual-risk` + non-blocking rationale, never silently ignored.

---

## Evidence Rows

### Phase 1 — CEP Public Entry Path & NFA Linear Matching

@@EVIDENCE
inventory_id: EVID-S12-001
source_anchor: nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/CEP.java:38; nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/PatternStreamBuilder.java:146-160
declared_guarantee: Keyed CEP entry — CEP.pattern(DataStream, Pattern) returns a PatternStream; PatternStreamBuilder.build() constructs a CepOperator and, when the input is a KeyedStream, wires it via keyedStream.transform("CepOperator", outTypeInfo, operator) so the keyed state backend partitions CEP state (NFAState + SharedBuffer MapStates) per key. No separate KeyedCEPPatternOperator class exists — keyed and non-keyed paths use the same CepOperator with a different transform name
implementation_anchor: nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java:98
runtime_wiring: wired
positive_proof: TestCepPublicApiE2E#testCepPatternCreationFromKeyedStream
rejection_proof: TestCepOperatorBasic#testKeyedStateIsolation
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S12-002
source_anchor: nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/PatternStreamBuilder.java:161-169; nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/datastream/SingleOutputStreamOperatorImpl.java:52-58
declared_guarantee: Non-keyed CEP entry (M7-2-P0-1 revalidation) — for a non-KeyedStream input, PatternStreamBuilder.build() synthesizes a NullByteKeySelector, calls keyBy(keySelector).transform("GlobalCepOperator", outTypeInfo, operator).forceNonParallel(). The historical M7-2-P0-1 defect (forceNonParallel unconditionally threw UnsupportedOperationException) is FIXED: SingleOutputStreamOperatorImpl.forceNonParallel() now calls transformation.lockParallelismToOne() instead of throwing, so the non-keyed CEP path builds and runs without crashing
implementation_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/datastream/SingleOutputStreamOperatorImpl.java:52-58
runtime_wiring: wired
positive_proof: TestCepNonKeyedEntryE2E#cepPatternOnNonKeyedStreamBuildsWithoutThrowing
rejection_proof: TestCepNonKeyedEntryE2E#cepPatternOnNonKeyedStreamProducesMatches
environment_class: in-process
required_lane: in-process
finding_id: M7-2-P0-1
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S12-003
source_anchor: nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/NFA.java:236-251; nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/NFA.java:356-433
declared_guarantee: NFA linear matching — NFA.process() delegates to doProcess() which advances each ComputationState through the compiled state graph; for a linear pattern (begin().next()/.followedBy()) each event either TAKEs the transition (locking the event into SharedBuffer via addComputationState→lockNode) or IGNOREs it, producing a single non-branching match path from start to final state. processMatchesAccordingToSkipStrategy releases the completed-match nodes
implementation_anchor: nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/NFA.java:621-756
runtime_wiring: wired
positive_proof: TestNFA#testSimplePatternMatch
rejection_proof: TestNFA#testPatternNoMatch
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S12-004
source_anchor: nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/NFA.java:621-756; nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/NFA.java:780-806
declared_guarantee: NFA state transition (TAKE/IGNORE/PROCEED) — computeNextStates() iterates each StateTransition of the current state: TAKE advances the state and locks the event (addComputationState); IGNORE stays in the same state (no SharedBuffer write); PROCEED skips ahead to a later state without consuming the event (findFinalStateAfterProceed). The NFACompiler emits the transition set for every supported pattern quantifier; this is the core matching primitive both linear and branching patterns build on
implementation_anchor: nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/compiler/NFACompiler.java:1
runtime_wiring: wired
positive_proof: TestNFAExtended#testStrictContinuityWithResults
rejection_proof: TestNFAExtended#testStrictContinuityNoResults
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

### Phase 2 — Branching Matching, Skip Strategies, Timeout & SharedBuffer Lifetime

@@EVIDENCE
inventory_id: EVID-S12-005
source_anchor: nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/NFA.java:621-756; nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/NFA.java:758-778
declared_guarantee: NFA branching matching — for followedByAny the TAKE transition is non-deterministic, so computeNextStates() emits multiple ComputationStates each carrying a distinct DeweyNumber version (child = parent version + branch index). addComputationState() registers each branch and calls lockNode() on its SharedBuffer entry so the branching event is retained for every outstanding branch. TestNFAExtended#testFollowedByAnyBranchingWithSkipPastLastEvent exercises a 3-way followedByAny branch with SkipPastLastEvent and asserts SharedBuffer node count stays bounded (<= events.size()) after pruning — proving branching produces matches AND releases nodes rather than leaking
implementation_anchor: nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/sharedbuffer/SharedBufferAccessor.java:237-248
runtime_wiring: wired
positive_proof: TestNFAExtended#testFollowedByAnyBranchingWithSkipPastLastEvent
rejection_proof: TestNFAExtended#testFollowedByAnyProducesMoreMatches
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S12-006
source_anchor: nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/sharedbuffer/SharedBufferAccessor.java:258-308
declared_guarantee: SharedBuffer release/lockstep (O8-2-AR-1 revalidation) — releaseNode() walks the SharedBuffer graph using two parallel stacks (nodesToExamine + versionsToExamine) that must stay lockstep (1 node + 1 version popped per iteration). The historical O8-2-AR-1 defect (on a null curBufferNode branch the code popped the node but NOT the version, desynchronizing the stacks and corrupting refcounts / over-releasing on branching CEP) is LIVE-FIXED: the null branch at :274-281 now pops the version explicitly before continue, preserving the lockstep invariant. releaseOrDetach() handles edge/node refcount release. Null-branch regression TestSharedBufferExtended#testReleaseNodePopsVersionOnNullEntry:307 proves the fix (X must be released, L must be released, P only released if null branch consumed its version)
implementation_anchor: nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/sharedbuffer/SharedBufferAccessor.java:274-281
runtime_wiring: wired
positive_proof: TestSharedBufferExtended#testReleaseNodePopsVersionOnNullEntry
rejection_proof: TestSharedBufferExtended#testReleaseNodesWithLongPath
environment_class: in-process
required_lane: in-process
finding_id: O8-2-AR-1
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S12-007
source_anchor: nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/sharedbuffer/Lockable.java:56-66; nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/sharedbuffer/Lockable.java:68-81
declared_guarantee: Lockable refcount release (M7-2-P2-8 revalidation) — the historical M7-2-P2-8 defect (Lockable.release() threw a bare IllegalStateException on ref-count underflow, violating the two-tier exception policy) is FIXED: release() now throws StreamRuntimeException("Lockable over-release: refCounter went negative") at :62 (a platform exception, not a bare JDK exception), resets the counter to 0 defensively, and uses compareAndSet for the decrement. releaseOrDetach() :68-81 mirrors this for the detach path. TestLockableOverRelease#testOverReleaseDoesNotThrowBareIllegalStateException asserts the exception type is NOT a bare IllegalStateException
implementation_anchor: nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/sharedbuffer/Lockable.java:56-81
runtime_wiring: wired
positive_proof: TestLockableOverRelease#testOverReleaseDoesNotThrowBareIllegalStateException
rejection_proof: TestLockable#testReleaseThrowsWhenCounterAlreadyZero
environment_class: in-process
required_lane: in-process
finding_id: M7-2-P2-8
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S12-008
source_anchor: nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/aftermatch/AfterMatchSkipStrategy.java:101-123; nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/aftermatch/SkipPastLastStrategy.java:36-49
declared_guarantee: Skip strategy matrix — AfterMatchSkipStrategy.prune() computes a pruningId from the completed match and calls releaseNode() to free SharedBuffer entries that must no longer participate in future matches. All 5 strategies are supported: NoSkip (keep all overlapping), SkipPastLastEvent (release up to and including last matched event), SkipToNext (skip to the next event after the first), SkipToFirst (skip to the first named pattern event), SkipToLast (skip to the last named pattern event). TestCepSkipStrategyE2E exercises all 5 + oneOrMore variants end-to-end via the NFA
implementation_anchor: nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/aftermatch/AfterMatchSkipStrategy.java:44-82
runtime_wiring: wired
positive_proof: TestCepSkipStrategyE2E#testSkipPastLastEventSkipsOverlappingMatches
rejection_proof: TestCepSkipStrategyE2E#testNoSkipProducesAllMatches
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S12-009
source_anchor: nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/NFA.java:265-346; nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java:718-732
declared_guarantee: Event-time/processing-time timeout — NFA.advanceTime() compares each partial match's startTimestamp + windowTime against the advancing timestamp; partial matches whose window has elapsed are emitted into the timeout collection. CepOperator.processTimedOutSequences() forwards timed-out partial matches to the user function ONLY when it implements TimedOutPartialMatchHandler. CAVEAT: processing-time timeout relies on CepOperator.TimerServiceImpl.currentProcessingTime() which derives from the processing-time service; processing-time tests use real wall-clock advances (non-deterministic) — event-time timeout is deterministic via watermark. Both timeout modes are exercised in-process
implementation_anchor: nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java:685-707
runtime_wiring: wired
positive_proof: TestCepOperatorTimeout#testTimeoutWithProcessingTime
rejection_proof: TestCepOperatorTimeout#testNoTimeoutWhenPatternCompletes
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

### Phase 3 — Dangling Safety Net, Checkpoint Continuation & Serializable Risk

@@EVIDENCE
inventory_id: EVID-S12-010
source_anchor: nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java:540; nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java:600
declared_guarantee: Dangling safety net (O8-2-AR-2 revalidation) — onEventTime():540 and onProcessingTime():600 guard the dangling-cleanup block with predicate `nfaState.getPartialMatches().size() == 1 && nfaState.getCompletedMatches().isEmpty()`; when true and the single partial match's window has elapsed (allTimedOut), releaseNode() frees its SharedBuffer entry and computationStates.clear() removes it. The safety net fires ONLY when size==1, so stale partial-match entries left by branching patterns (size>1) are NOT reclaimed at the CepOperator level — this is a residual coverage gap. size==1 in-process proven; size>1 branching gap deferred to Stage 17 (test effectiveness)
implementation_anchor: nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java:556-565
runtime_wiring: wired
positive_proof: TestCepOperatorDanglingCleanup#testDanglingCleanupReleasesSharedBuffer
rejection_proof: TestCepOperatorDanglingCleanup#testNoCleanupWhenPatternStillActive
environment_class: in-process
required_lane: in-process
finding_id: O8-2-AR-2
disposition: residual-risk
@@END

@@EVIDENCE
inventory_id: EVID-S12-011
source_anchor: nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java:419-426; nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java:429-447; nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java:259
declared_guarantee: CEP checkpoint continuation (end-to-end) — snapshotState() writes WATERMARK_STATE_NAME (currentWatermark) and EVENT_TIME_TIMERS_STATE_NAME (registered event-time timers) as operator state alongside the platform-snapshot of NFAState (computationStates) and SharedBuffer (eventsBuffer/entries MapStates). restoreState() reads them back, sets currentWatermark + watermarkRestored=true, repopulates registeredEventTimeTimers. open() at :259 runs AFTER state-backend creation and applies pending restore state (applyPendingRestoreState). TestCepCheckpointRestoreE2E snapshots mid-pattern, closes the operator, restores a NEW CepOperator from the snapshot, feeds the completing event, and asserts the match completes — proving NFAState + SharedBuffer + watermark + timer all round-trip and the restored operator continues matching
implementation_anchor: nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java:259
runtime_wiring: wired
positive_proof: TestCepCheckpointRestoreE2E#testE2ENfaStateSurvivesCheckpointRestore
rejection_proof: TestCepCheckpointRestoreE2E#testE2ESharedBufferSurvivesCheckpointRestore
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S12-012
source_anchor: nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java:450-459; nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java:415
declared_guarantee: Watermark persistence — processWatermark() advances currentWatermark monotonically (only when newWatermark > currentWatermark) and triggers onEventTime(currentWatermark) in event-time mode before delegating to super.processWatermark. The currentWatermark value is checkpointed via WATERMARK_STATE_NAME (:415) in snapshotState and restored (with watermarkRestored=true) in restoreState, so a restored operator does not regress watermark and re-fire already-elapsed timers. TestCepOperatorWatermarkPersistence verifies the snapshot→restore→advancement round-trip in 4 methods
implementation_anchor: nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java:419-447
runtime_wiring: wired
positive_proof: TestCepOperatorWatermarkPersistence#testWatermarkPersistedInSnapshot
rejection_proof: TestCepOperatorWatermarkPersistence#testWatermarkRestoredFromSnapshot
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S12-013
source_anchor: nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/NFAState.java:28; nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/ComputationState.java:33; nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/sharedbuffer/EventId.java:27; nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/sharedbuffer/NodeId.java:26; nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/sharedbuffer/SharedBufferNode.java:28; nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/sharedbuffer/SharedBufferEdge.java:28; nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/sharedbuffer/Lockable.java:35
declared_guarantee: CEP state non-Serializable risk (O8-2-AR-3) — 7 CEP state value classes (NFAState, ComputationState, EventId, NodeId, SharedBufferNode, SharedBufferEdge, Lockable) dropped `implements Serializable` vs their Flink originals (verified: none declare Serializable). This is a latent Java-serialization risk: if a future state backend switches to Java serialization for these MapState values, checkpoint/restore would break with NotSerializableException. NON-BLOCKING RATIONALE: the current platform state backend uses platform TypeSerializers (not Java serialization) for CEP state, so the non-Serializable classes never traverse a Java ObjectOutputStream today. Successor ownership: watch-only; a successor remediation plan is required only if the platform switches to Java serialization for CEP state
implementation_anchor: none
runtime_wiring: wired
positive_proof: none
rejection_proof: none
environment_class: none
required_lane: unit
finding_id: O8-2-AR-3
disposition: residual-risk
@@END

@@EVIDENCE
inventory_id: EVID-S12-014
source_anchor: nop-stream/nop-stream-cep/src/test/java/io/nop/stream/cep/operator/TestCepOperatorDanglingCleanup.java:82
declared_guarantee: M7-2-P0-4 revalidation — the historical M7-2-P0-4 defect (TestCepOperatorDanglingCleanup computed partialMatchesEmpty but never asserted it, leaving dangling cleanup effectively untested) is FIXED: testDanglingCleanupReleasesSharedBuffer now computes partialMatchesEmpty (:114-115) and asserts it assertTrue (:116-119), AND additionally asserts operator.getPartialMatches().isEmpty() (:121-122) to confirm SharedBuffer entries were released. The test would now fail if the dangling-cleanup logic were deleted
implementation_anchor: nop-stream/nop-stream-cep/src/test/java/io/nop/stream/cep/operator/TestCepOperatorDanglingCleanup.java:114-122
runtime_wiring: wired
positive_proof: TestCepOperatorDanglingCleanup#testDanglingCleanupReleasesSharedBuffer
rejection_proof: TestCepOperatorDanglingCleanup#testNoCleanupWhenPatternStillActive
environment_class: in-process
required_lane: in-process
finding_id: M7-2-P0-4
disposition: e2e-proved
@@END

### Phase 4 — Historical Finding Revalidation & Fraud-Example Scope

@@EVIDENCE
inventory_id: EVID-S12-015
source_anchor: nop-stream/nop-stream-cep/src/test/java/io/nop/stream/cep/operator/TestCepOperatorStateBackendWiring.java:88-166
declared_guarantee: M7-2-P1-13 revalidation — TestCepOperatorStateBackendWiring couples to internal accessors getKeyedStateBackend()/getNFAStateForTesting() (P-4 test smell). Live revalidation confirms the coupling persists (the file Javadoc at :32 documents the coupling and the :111 comment notes the decoupling intent). The tests still exercise real CepOperator matching/snapshot behavior, so this is a test-quality residual (coupling to internal accessors) not a production defect. Successor ownership: Stage 17 (test effectiveness) / active remediation plan 2026-08-04-2300-3
implementation_anchor: nop-stream/nop-stream-cep/src/test/java/io/nop/stream/cep/operator/TestCepOperatorStateBackendWiring.java:88
runtime_wiring: wired
positive_proof: TestCepOperatorStateBackendWiring#testConfiguredStateBackendProcessesMatchesAndSnapshotsState
rejection_proof: none
environment_class: in-process
required_lane: in-process
finding_id: M7-2-P1-13
disposition: residual-risk
@@END

@@EVIDENCE
inventory_id: EVID-S12-016
source_anchor: nop-stream/nop-stream-cep/src/test/java/io/nop/stream/cep/nfa/TestAfterMatchSkipStrategies.java:1
declared_guarantee: M7-2-P1-14 revalidation — TestAfterMatchSkipStrategies was historically 100% metadata assertions (P-2). Live revalidation confirms partial-fix: the file name still implies it is the skip-strategy's main test, but the strategy's real NFA behavior is now proven by TestCepSkipStrategyE2E (8 in-process methods, EVID-S12-008). The metadata-only coverage residual remains; successor ownership: Stage 17 (test effectiveness) / active remediation plan 2026-08-04-2300-3
implementation_anchor: nop-stream/nop-stream-cep/src/test/java/io/nop/stream/cep/operator/TestCepSkipStrategyE2E.java:93-252
runtime_wiring: wired
positive_proof: TestCepSkipStrategyE2E#testSkipPastLastEventSkipsOverlappingMatches
rejection_proof: none
environment_class: in-process
required_lane: in-process
finding_id: M7-2-P1-14
disposition: residual-risk
@@END

@@EVIDENCE
inventory_id: EVID-S12-017
source_anchor: nop-stream/nop-stream-cep/src/test/java/io/nop/stream/cep/nfa/sharedbuffer/TestSharedBuffer.java:21-71
declared_guarantee: M7-2-P2-17 revalidation — TestSharedBuffer overuses assertNotNull(id) where concrete EventId assertions exist in sibling tests (P-2 test smell). Live revalidation confirms the residual persists: TestSharedBuffer uses assertNotNull(id)/assertNotNull(nodeId) at :29/:66/:84/:85 instead of asserting concrete EventId equality. Real SharedBuffer lifecycle behavior is proven by TestSharedBufferExtended (EVID-S12-006). Successor ownership: Stage 17 (test effectiveness) / active remediation plan 2026-08-04-2300-3
implementation_anchor: nop-stream/nop-stream-cep/src/test/java/io/nop/stream/cep/nfa/sharedbuffer/TestSharedBuffer.java:29
runtime_wiring: wired
positive_proof: TestSharedBufferExtended#testSharedBufferFullLifecycle
rejection_proof: none
environment_class: in-process
required_lane: in-process
finding_id: M7-2-P2-17
disposition: residual-risk
@@END

@@EVIDENCE
inventory_id: EVID-S12-018
source_anchor: nop-stream/nop-stream-cep/src/test/java/io/nop/stream/cep/nfa/TestNFAState.java:11-80
declared_guarantee: M7-2-P2-18 revalidation — TestNFAState equals/hashCode are mirror tests; only testNotEqualWhenMatchesDiffer (:53) has real protection. Live revalidation confirms the residual persists: the file still tests equals/hashCode round-trips on the value object (testEqualsWithNonEmptyPartialMatchesDoesNotThrow :18, testHashCodeConsistencyWithMultipleElements :68) which mirror the production equals/hashCode rather than testing boundary behavior. Successor ownership: Stage 17 (test effectiveness) / active remediation plan 2026-08-04-2300-3
implementation_anchor: nop-stream/nop-stream-cep/src/test/java/io/nop/stream/cep/nfa/TestNFAState.java:53
runtime_wiring: wired
positive_proof: TestNFAState#testNotEqualWhenMatchesDiffer
rejection_proof: none
environment_class: unit
required_lane: unit
finding_id: M7-2-P2-18
disposition: residual-risk
@@END

@@EVIDENCE
inventory_id: EVID-S12-019
source_anchor: nop-stream/nop-stream-cep/src/test/java/io/nop/stream/cep/nfa/TestWatermarkStateRobustness.java:10-42
declared_guarantee: M8-2-P2-20 revalidation — TestWatermarkStateRobustness class name lies about what it tests: it actually tests Quantifier.Times hashCode consistency (:13) and DeweyNumber-based comparison (:35), NOT watermark state robustness (there is no watermark I/O in the file). Live revalidation confirms the misleading class name persists. Real watermark persistence behavior is proven by TestCepOperatorWatermarkPersistence (EVID-S12-012). Successor ownership: Stage 17 (test effectiveness) / active remediation plan 2026-08-04-2300-3
implementation_anchor: nop-stream/nop-stream-cep/src/test/java/io/nop/stream/cep/operator/TestCepOperatorWatermarkPersistence.java:82
runtime_wiring: wired
positive_proof: TestCepOperatorWatermarkPersistence#testWatermarkPersistedInSnapshot
rejection_proof: none
environment_class: in-process
required_lane: in-process
finding_id: M8-2-P2-20
disposition: residual-risk
@@END

@@EVIDENCE
inventory_id: EVID-S12-020
source_anchor: nop-stream/nop-stream-fraud-example/src/test/java/io/nop/stream/fraud/TestGeographicAnomalyPatternFix.java:19-60
declared_guarantee: O8-2-AR-4 revalidation — TestGeographicAnomalyPatternFix re-implements the city2 IterativeCondition inline instead of exercising the production createPattern() (zero bug-catching power). Live revalidation confirms the test still re-implements the condition inline (testCity2FilterIteratesAllCity1Events :19, testCity2FilterReturnsFalseForSameUserSameCity :63) rather than invoking the production GeographicAnomalyPattern. This is a test-effectiveness residual; the production pattern itself is linear (no branching) and its semantic surface is bounded by the example-module include rule. Successor ownership: Stage 17 (test effectiveness)
implementation_anchor: none
runtime_wiring: partial
positive_proof: none
rejection_proof: none
environment_class: unit
required_lane: unit
finding_id: O8-2-AR-4
disposition: residual-risk
@@END

@@EVIDENCE
inventory_id: EVID-S12-021
source_anchor: nop-stream/nop-stream-fraud-example/src/main/resources/_vfs/nop/stream/demo/fraud-detection.stream.xml:71-72; nop-stream/nop-stream-fraud-example/src/main/java/io/nop/stream/fraud/FraudDetectionDemo.java:52
declared_guarantee: Fraud-example scope — the example module ships one production stream.xml with a single <cep patternRef="rapid-transaction-pattern"> (:71-72) using a LINEAR pattern (begin("first").next("second").within(30s), no followedByAny/branching, :102). FraudDetectionDemo.main (:52) constructs NFA + SharedBuffer directly but does NOT call env.execute() (verified: no env.execute call anywhere in the fraud-example main sources). Per manifest include/exclude rules, example-module anchors are eligible ONLY for fail-fast/semantic rows, never for production-capability claims. All 4 example patterns (RapidTransaction/GeographicAnomaly/UnusualAmount/AccountTakeover) are linear
implementation_anchor: nop-stream/nop-stream-fraud-example/src/main/java/io/nop/stream/fraud/FraudDetectionDemo.java:52
runtime_wiring: partial
positive_proof: none
rejection_proof: none
environment_class: none
required_lane: unit
finding_id: none
disposition: non-goal
@@END

@@EVIDENCE
inventory_id: EVID-S12-022
source_anchor: nop-stream/nop-stream-cep/src/test/java/io/nop/stream/cep/operator/TestCepCheckpointRestoreE2E.java:114
declared_guarantee: no-env-execute coverage gap — nop-stream-cep has ZERO tests that call StreamExecutionEnvironment.execute() (verified: no env.execute anywhere in the cep test tree). Every "E2E"-suffixed CEP test (TestCepCheckpointRestoreE2E, TestCepPublicApiE2E, TestCepNonKeyedEntryE2E, TestCepSkipStrategyE2E) is operator-driven (instantiates CepOperator directly, drives processElement/processWatermark/snapshotState/restoreState in a single JVM). This is an honest coverage gap: full-graph (source→CEP→sink) CEP behavior with real scheduler/timer threads is not exercised by any test. The operator-driven in-process lane still proves CEP matching/SharedBuffer/checkpoint semantics (EVID-S12-001..012), so this is non-blocking. Successor ownership: Stage 17 (test effectiveness)
implementation_anchor: nop-stream/nop-stream-cep/src/test/java/io/nop/stream/cep/operator/TestCepCheckpointRestoreE2E.java:114
runtime_wiring: wired
positive_proof: TestCepCheckpointRestoreE2E#testE2ENfaStateSurvivesCheckpointRestore
rejection_proof: none
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: residual-risk
@@END

---

## Cross-Reference Notes (final disposition owned by Stages 20/22; coverage/test-effectiveness gaps flagged to Stage 17)

- **M7-2-P0-1** (forceNonParallel throws): **e2e-proved (FIXED).** `SingleOutputStreamOperatorImpl.forceNonParallel():52-58` now calls `transformation.lockParallelismToOne()` instead of throwing `UnsupportedOperationException`. Regression `TestCepNonKeyedEntryE2E#cepPatternOnNonKeyedStreamBuildsWithoutThrowing` + `#cepPatternOnNonKeyedStreamProducesMatches` prove both build and run. EVID-S12-002.
- **M7-2-P0-4** (dangling cleanup never asserts): **e2e-proved (FIXED).** `TestCepOperatorDanglingCleanup:114-122` now asserts `partialMatchesEmpty` and `operator.getPartialMatches().isEmpty()`. EVID-S12-014.
- **M7-2-P2-8** (Lockable.release bare exception): **e2e-proved (FIXED).** `Lockable.release():62` now throws `StreamRuntimeException("Lockable over-release: ...")` (platform exception), not bare `IllegalStateException`. EVID-S12-007.
- **O8-2-AR-1** (releaseNode parallel stacks lockstep): **e2e-proved (LIVE-FIXED).** Null-branch fix at `SharedBufferAccessor.releaseNode():274-281` now pops the version before continue, preserving the lockstep invariant. Regression `TestSharedBufferExtended#testReleaseNodePopsVersionOnNullEntry:307`. EVID-S12-006.
- **O8-2-AR-2** (dangling safety net size==1): **residual-risk.** `size==1` in-process proven (`TestCepOperatorDanglingCleanup`); `size>1` branching stale entries are NOT reclaimed at the CepOperator level — no operator-level test covers this. Deferred to Stage 17 (test effectiveness). EVID-S12-010.
- **O8-2-AR-3** (CEP state non-Serializable): **residual-risk.** 7 value classes non-Serializable; non-blocking because current state backend uses platform TypeSerializers (not Java serialization). Watch-only; successor plan required only if platform switches to Java serialization for CEP state. EVID-S12-013.
- **O8-2-AR-4** (TestGeographicAnomalyPatternFix zero bug-catching power): **residual-risk.** Test still re-implements city2 condition inline. Stage 17 successor. EVID-S12-020.
- **M7-2-P1-13** (TestCepOperatorStateBackendWiring couples internal accessors): **residual-risk.** Coupling persists; tests still exercise real behavior. Stage 17 / plan `2026-08-04-2300-3`. EVID-S12-015.
- **M7-2-P1-14** (TestAfterMatchSkipStrategies 100% metadata): **residual-risk.** Partial-fix; real behavior proven by `TestCepSkipStrategyE2E`. Stage 17 / plan `2026-08-04-2300-3`. EVID-S12-016.
- **M7-2-P2-17** (TestSharedBuffer assertNotNull): **residual-risk.** Residual persists; real lifecycle proven by `TestSharedBufferExtended`. Stage 17 / plan `2026-08-04-2300-3`. EVID-S12-017.
- **M7-2-P2-18** (TestNFAState mirror tests): **residual-risk.** Residual persists. Stage 17 / plan `2026-08-04-2300-3`. EVID-S12-018.
- **M8-2-P2-20** (TestWatermarkStateRobustness misleading class name): **residual-risk.** Tests Quantifier/DeweyNumber, not watermark; real watermark proven by `TestCepOperatorWatermarkPersistence`. Stage 17 / plan `2026-08-04-2300-3`. EVID-S12-019.

## Non-Goals honored (not silently dropped)

- General window behavior, watermark generation/propagation = Stage 11 (CEP has its own `io.nop.stream.cep.time.TimerService`; CepOperator watermark/timer checkpoint verified here via EVID-S12-011/EVID-S12-012).
- State backend encoding / savepoint / rescale = Stage 10 (CEP state round-trip behavior verified here, not backend encoding).
- NFA new feature / new skip strategy development = out of scope.
- Fixing confirmed live defects discovered by this audit = assigned to active/successor remediation plan per roadmap rules (none newly discovered here; O8-2-AR-1 already fixed, others are pre-existing residuals).
- `env.execute()`-level CEP coverage = `residual-risk` + Stage 17 successor (EVID-S12-022).
- Fraud-example module as production-capability evidence = `non-goal` (example include rule; EVID-S12-021).
- Dangling safety net `size>1` branching gap = `residual-risk` + Stage 17 successor (EVID-S12-010).
- CEP state non-Serializable latent risk = `residual-risk` + non-blocking rationale (EVID-S12-013).
