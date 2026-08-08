# Stage 21 — Historical P2 Core/State/Window Finding Disposition (Shard 21, 19 findings)

> Status: produced by Stage 21 (plan `nop-stream-independent-audit/2026-08-08-2100-1-historical-p2-core-state-window-disposition.md`)
> Source corpus: `finding-corpus.md` Shard 21 (frozen at HEAD 2026-08-07; 19 findings, all from 2026-07-25 multi+open audit)
> Validator: `node ai-dev/tools/check-nop-stream-audit-manifest.mjs disposition --shard 21 --strict`
> All anchors revalidated against live repo HEAD on 2026-08-08.
> Disposition vocabulary: `revalidated | stale | active/successor owner | residual-risk | blocked` (finding-disposition 5-value, see `evidence-schema.md` Stage 18 Supplement)

## Disposition Summary

**Totals: 19 findings → 11 revalidated, 1 stale, 0 active/successor owner, 7 residual-risk, 0 blocked**

### Disposition × Severity Cross-Tab

| Disposition \ Severity | P0 | P1 | P2 | AR | Total |
| --- | --- | --- | --- | --- | --- |
| `revalidated` | 0 | 0 | 9 | 2 | 11 |
| `stale` | 0 | 0 | 1 | 0 | 1 |
| `active/successor owner` | 0 | 0 | 0 | 0 | 0 |
| `residual-risk` | 0 | 0 | 7 | 0 | 7 |
| `blocked` | 0 | 0 | 0 | 0 | 0 |
| **Total** | **0** | **0** | **17** | **2** | **19** |

### Disposition × Domain Cross-Tab

| Disposition \ Domain | contract/test | checkpoint/state | window | Total |
| --- | --- | --- | --- | --- |
| `revalidated` | 8 | 3 | 0 | 11 |
| `stale` | 1 | 0 | 0 | 1 |
| `active/successor owner` | 0 | 0 | 0 | 0 |
| `residual-risk` | 1 | 3 | 3 | 7 |
| `blocked` | 0 | 0 | 0 | 0 |
| **Total** | **10** | **6** | **3** | **19** |

### Cross-Cutting Concern Compliance

- **No P2 silently downgraded from a reclassified P0/P1**: every P2 here remained P2 after live revalidation (no P2 was found to be a hidden P0/P1). Zero findings carry `active/successor owner` because no still-live P2 was reclassified upward and no in-scope defect requires a fresh remediation plan owner (all residual-risk P2s carry explicit non-blocking rationale, which P2 permits).
- **Every P2 `residual-risk` has explicit non-blocking rationale**: all 7 P2 residual-risk blocks (M7-2-P2-5/6/9/10/13/15/16) carry `residual_rationale`. None is a silent drop.
- **AR reconfirmation at current HEAD**: O7-2-AR-6 (`status_at_0802: left-for-followup`) — the misplaced javadoc is now corrected (each of `hasNonVirtualOperator` and `determinePartitionType` carries its own matching javadoc); `revalidated`. O7-2-AR-7 (`status_at_0802: verified-fixed`) — `UNION`/`SINGLETON` dead enum values are removed (PartitionPolicy now declares only FORWARD/HASH/REBALANCE/BROADCAST); `revalidated`. Anchor drift handled in each block.
- **Recurrent consistency**: M7-2-P2-9 (recurrent: M8-2-P2-23) and M7-2-P2-13 (recurrent: M8-2-P2-21) are both `residual-risk`, CONSISTENT with their Shard 18 recurrent partners (M8-2-P2-23 = residual-risk, M8-2-P2-21 = residual-risk). Both recurrent pairs describe the same root cause (TestCountTrigger vacuous stub; TestProcessingGuarantee enum-metadata assertions) and reach the same disposition — no unexplained contradiction.
- **Doc-drift P2s (P2-19/20/21) and the flow→cep pom dep (P2-1)**: the nop-stream README was rewritten (now 41 lines) and now accurately reflects module dependencies (cep→core, flow→core+cep+xdefs), matching the live poms. The README-side drift that constituted these findings is gone; `revalidated`. The deeper design-doc (`component-roadmap.md`) IEvalFunction-provenance wording residue (nop-xlang vs nop-core) is a milder nuance owned by Stage 23's doc-convergence sweep — recorded in notes, not a live contradiction.
- **Test-quality P2s split by live revalidation**: P2-11 (3 checkpoint test classes) and P2-12 (TestCheckpointType) were rewritten with real behavior + serialization-fidelity tests + fail-fast negative controls (low-value parts tagged `@Tag("low-value")` per Stage 17 governance) → `revalidated`. P2-9/10/13/15/16 remain vacuous stubs → `residual-risk` (consistent with Stage 17 live-residual registry).

---

## P2 Finding Dispositions (17)

@@DISPOSITION
finding_id: M7-2-P2-1
severity: P2
source_anchor: nop-stream/nop-stream-flow/pom.xml:20-23
disposition: revalidated
revalidation_evidence: The original defect was the CONTRADICTION between nop-stream-flow/pom.xml (depends on nop-stream-cep at :20-23) and the README/architecture (which claimed flow→core only). The README has been rewritten (now 41 lines) and README:18 now correctly states "nop-stream-flow | 活跃 | XDSL 声明式流编排，依赖 core + cep（CepPatternModel）+ nop-xdefs" — matching the flow pom.xml exactly (nop-stream-core :15-18, nop-stream-cep :20-23, nop-xdefs :25-28). The flow→cep dependency is intentional (flow uses CepPatternModel from cep) and is now documented. The contradiction that constituted the defect is resolved. Cross-ref Stage 7 cross-reference note on flow→cep pom dep
successor_note: anchor stable (flow/pom.xml:20-23 unchanged); the resolution is at the README side (README:18 now matches the pom)
@@END

@@DISPOSITION
finding_id: M7-2-P2-2
severity: P2
source_anchor: nop-stream/src/main/java/io/nop/stream/flow/model/
disposition: revalidated
revalidation_evidence: The duplicate source tree at nop-stream/src/main/java/io/nop/stream/flow/model/ (60 git-tracked files under the pom-parent) NO LONGER EXISTS. The nop-stream parent module (pom.xml aggregator) has NO src/ directory at all (verified: `ls nop-stream/src` reports no such directory). The legitimate generated flow model lives at nop-stream-flow/src/main/java/io/nop/stream/flow/model/_gen/ (e.g. _StreamModel.java) which is the codegen output for the flow module — not a duplicate. The duplicate tree that constituted the defect has been removed. No prior evidence row; from-scratch live revalidation (plan Phase 2)
successor_note: anchor disappeared (the entire nop-stream/src/ tree is gone); the legitimate generated flow model is now solely under nop-stream-flow/src/main/java/
@@END

@@DISPOSITION
finding_id: M7-2-P2-3
severity: P2
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/StreamOperator.java:28-31; OneInputStreamOperator.java:24-26; Input.java:28-35
disposition: stale
stale_rationale: The non-existent types the operator-interface Javadocs referenced (TwoInputStreamOperator, MultipleInputStreamOperator, AbstractStreamOperatorV2, AbstractInput) were NEVER implemented — two-input/multi-input operators are a deliberate non-goal in nop-stream (zero consumers by design). grep across the entire nop-stream tree returns ZERO occurrences of TwoInputStreamOperator/MultipleInputStreamOperator/AbstractStreamOperatorV2/AbstractInput in any .java file (including Javadocs). The operator-interface Javadocs have been corrected: StreamOperator.java:27-39 now references only the existing OneInputStreamOperator. The finding's premise (these types should exist, as the Javadoc implied) is invalidated by the architecture decision to make two-input operators a non-goal. Cross-ref Stage 6 EVID-S6-014/015 (disposition non-goal), Stage 11 EVID-S11-016 (non-goal, Anti-Hollow exemption), Stage 20 M7-2-P1-12 (zero TwoInputStreamOperator consumers confirmed)
successor_note: the referenced types were removed as a non-goal; the operator-interface Javadocs now reference only existing types
@@END

@@DISPOSITION
finding_id: M7-2-P2-4
severity: P2
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/functions/source/CheckpointedSourceFunction.java:14-19; StreamSourceOperator.java:296-302,321-332
disposition: revalidated
revalidation_evidence: The misleading "API 预留，当前未被使用" Javadoc text is GONE. CheckpointedSourceFunction.java:15-28 now CORRECTLY documents the production call sites: "snapshotState is invoked by StreamSourceOperator.snapshotState (during barrier processing) ... initializeState is invoked by StreamSourceOperator.restoreState on recovery". The production calls exist: Stage 19 (M7-2-P1-4) confirmed CheckpointedSourceFunction.initializeState IS invoked at StreamSourceOperator.java:343 for sources. The Javadoc-vs-production drift that constituted the defect is resolved. No prior evidence row; from-scratch live revalidation (plan Phase 2)
successor_note: anchor drifted (14-19 → 15-28 Javadoc block in current HEAD; the misleading "API 预留" text removed and replaced with accurate call-site documentation)
@@END

@@DISPOSITION
finding_id: M7-2-P2-5
severity: P2
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/datastream/DataStreamImpl.java:135-186; KeyedStreamImpl.java:190-197; WindowedStreamImpl.java:184-242
disposition: residual-risk
residual_rationale: The unchecked cast `(TypeInformation<R>) UnknownTypeInformation.INSTANCE` STILL EXISTS at DataStreamImpl.java:140,183,204 (and symmetric sites in KeyedStreamImpl/WindowedStreamImpl). This is a deliberate type-erasure acceptance, not a runtime defect: the DataStream Java API is a builder for the type-erased StreamModel, and element types are not statically tracked at the Java-API layer (they flow through the XDSL model at resolution time). UnknownTypeInformation.INSTANCE is a singleton placeholder; the unchecked cast never causes a runtime ClassCastException because no value of the erased type is ever materialized through this cast — it only seeds the Transformation's declared output type which the XDSL path overrides. Stage 7 EVID-S7 e2e-proved the XDSL path exercises this cast without runtime failure. Non-blocking because (a) runtime behavior is correct (e2e-proven), (b) the cast is a documented design acceptance for the XDSL-driven model, and (c) no production consumer depends on the静态 TypeInformation for correctness. The formal type-erasure contract documentation is owned by Stage 23 (documentation contract, roadmap status todo). CONSISTENT with Stage 6 "still live — Final disposition owned by Stage 21"
note: P2 residual-risk permitted with non-blocking rationale; no P0/P1 reclassification (the cast is functionally correct, e2e-proven by Stage 7)
@@END

@@DISPOSITION
finding_id: M7-2-P2-6
severity: P2
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/datastream/WindowedStreamImpl.java:184-242; WindowOperatorFactoryImpl.java:121-160
disposition: residual-risk
residual_rationale: The performative type-safety defect STILL EXISTS: WindowedStreamImpl.java:194-195,209-210,224-225,239 still pass `(Class<T>)(Class<?>) Object.class`, `(Class<K>)(Class<?>) Object.class`, `(Class<ACC>)(Class<?>) Object.class` into IWindowOperatorFactory, and `UnknownTypeInformation.INSTANCE` at :196,211. The factory's Class<ACC>/IN/K parameters are thus always satisfied with Object.class (type-safety theater). This is a deliberate acceptance, not a runtime defect: the WindowedStream Java API does not statically know ACC/IN/K (they are bound at XDSL model resolution time, not at Java API construction time). Window result correctness is proven by Stage 11 e2e window tests (EVID-S11-021 residual-risk at the capability layer). Non-blocking because (a) window pipeline behavior is e2e-proven by Stage 11, (b) the Object.class placeholder never corrupts window output (the real types flow through the XDSL model), and (c) the factory interface shape is a contract-form residual, not a correctness defect. Stage 11 EVID-S11-021 registered this as residual-risk ("WindowedStreamImpl:194-240 still casts Object.class — type-safety theater contract drift; final disposition owned by Stage 21"). CONSISTENT with Stage 11 verdict
note: P2 residual-risk permitted with non-blocking rationale; no P0/P1 reclassification (window output correctness is e2e-proven by Stage 11)
@@END

@@DISPOSITION
finding_id: M7-2-P2-7
severity: P2
source_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:579-590
disposition: revalidated
revalidation_evidence: CheckpointCoordinator.onCompletePersistFailure (now at :821-831) logs the failure message EXACTLY ONCE — `LOG.error("Failed checkpoint {} for job {}: {}", checkpointId, jobId, failMessage, cause)` at :824. There is NO second LOG.warn of the same message anywhere in the method (the method body :825-831 does metrics.recordFailure, status set, pendingCheckpoints.remove, decrementPendingCheckpointCount, notifyParticipantsFinishCommit, notifyCheckpointAborted — no logging). The original defect ("logs the same failure message twice (ERROR + WARN)") is FIXED. No prior evidence row; from-scratch live revalidation (plan Phase 2)
successor_note: anchor drifted (579-590 → 821-831 onCompletePersistFailure in current HEAD; file grew due to incremental-persist/storage additions)
@@END

@@DISPOSITION
finding_id: M7-2-P2-9
severity: P2
source_anchor: nop-stream/nop-stream-core/src/test/java/io/nop/stream/core/windowing/triggers/TestCountTrigger.java:1-15
disposition: residual-risk
residual_rationale: TestCountTrigger is STILL a 15-line stub: the single test testCountTriggerCannotMerge (:10-14) asserts only `CountTrigger.of(5).canMerge() == false`. There is NO onElement boundary test, NO clear/onMerge/merge coverage. The vacuous-test defect is still live. Non-blocking because (a) CountTrigger firing semantics are proven elsewhere (Stage 11 window e2e tests exercise the count-trigger firing path), (b) canMerge()==false is the only CountTrigger-specific contract that a unit test can meaningfully own without duplicating the window-pipeline e2e, and (c) this is a P2 test-quality gap, not a correctness defect. CONSISTENT with Stage 11 EVID-S11-022 (residual-risk), Stage 17 (live-residual), and the recurrent partner M8-2-P2-23 in Shard 18 (residual-risk — same TestCountTrigger root cause, same disposition, no contradiction). The successor for test-quality remediation is roadmap-stage-23 (documentation contract and test-effectiveness convergence, roadmap status todo)
note: recurrent partner M8-2-P2-23 = residual-risk in Shard 18; both describe the same TestCountTrigger vacuous-stub root cause — CONSISTENT, no unexplained contradiction
@@END

@@DISPOSITION
finding_id: M7-2-P2-10
severity: P2
source_anchor: nop-stream/nop-stream-core/src/test/java/io/nop/stream/core/checkpoint/TestCheckpointBarrier.java:14-91
disposition: residual-risk
residual_rationale: TestCheckpointBarrier (91 lines) is STILL a pure value-object round-trip: testCheckpointBarrier, testSnapshot, testPrepareClose, testIsCheckpoint, testIsSavepoint, testEquals, testHashCode, testToString (:16-91). No assertThrows, no serialization fidelity, no behavior beyond getter/setter + equals/hashCode/toString metadata. The vacuous-test defect is still live. Non-blocking because (a) CheckpointBarrier is a simple value object whose real semantics (barrier alignment, channel tracking) are exercised by the Stage 9 checkpoint barrier-alignment e2e tests, (b) the equals/hashCode/toString coverage is low-value but not zero-value for a value object, and (c) this is a P2 test-quality gap, not a correctness defect. CONSISTENT with Stage 17 (live-residual). Successor: roadmap-stage-23 (test-effectiveness convergence, todo)
note: P2 residual-risk permitted with non-blocking rationale; the barrier-alignment correctness is owned by Stage 9 e2e tests, not this value-object unit test
@@END

@@DISPOSITION
finding_id: M7-2-P2-11
severity: P2
source_anchor: nop-stream/nop-stream-core/src/test/java/io/nop/stream/core/checkpoint/TestTaskStateSnapshot.java; TestOperatorSnapshotResult.java; TestCompletedCheckpoint.java
disposition: revalidated
revalidation_evidence: The original defect ("map put/get round-trips, NO serialization fidelity tests") is RESOLVED. All three files were rewritten with real behavior + serialization-fidelity tests. TestTaskStateSnapshot (137 lines) now has testSerialization (:119-136 — real Java-serialization round-trip: writeObject → readObject → assert taskId + operatorState + keyedState preserved) plus testBuilder, testEmptyFactory, testIsEmpty, testEstimateSize, constructor assertions; low-value getStateCount tagged `@Tag("low-value")` (:74). TestOperatorSnapshotResult (165 lines) now has empty-factory singleton semantics, builder tests, and testCheckpointParallelismSerializedThroughSnapshot (:152 — serialization-through-snapshot test); low-value tagged (:73). TestCompletedCheckpoint (138 lines) now has builder, testSerialization (:119), boundary (assertNull for nonexistent task location :67); low-value tagged (:77). The "no serialization fidelity tests" defect is fixed; low-value put/get parts are governed by Stage 17 tagging. CONSISTENT with Stage 17 (which registered these as live-residual; the subsequent remediation resolved the serialization-fidelity gap)
successor_note: all three files expanded (TestTaskStateSnapshot grew to 137 lines, TestOperatorSnapshotResult to 165, TestCompletedCheckpoint to 138) with serialization-fidelity + behavior coverage
@@END

@@DISPOSITION
finding_id: M7-2-P2-12
severity: P2
source_anchor: nop-stream/nop-stream-core/src/test/java/io/nop/stream/core/checkpoint/TestCheckpointType.java:17-30
disposition: revalidated
revalidation_evidence: The original defect ("asserts enum member count and getName() constants") is MITIGATED. TestCheckpointType (63 lines) now ALSO has real behavior tests: testIsAuto (:32-37 — isBarrierAlignment/semantics per type), testIsFinalCheckpoint (:47-53), and testFromName (:55-62 — includes `assertThrows(StreamException.class, ...)` for unknown AND null inputs, a fail-fast negative control). The original metadata-only assertions (testCheckpointTypeEnumValues, testGetName) are retained but explicitly tagged `@Tag("low-value")` (:19,39,47) per Stage 17 test-effectiveness governance. The "only metadata" defect is resolved: the file now covers enum semantics + fail-fast rejection, not just count/getName. Low-value residuals are governed by Stage 17 tagging
successor_note: anchor expanded (17-30 → 17-63 full file); behavior tests (isAuto, isFinalCheckpoint, fromName-with-assertThrows) added, low-value parts tagged
@@END

@@DISPOSITION
finding_id: M7-2-P2-13
severity: P2
source_anchor: nop-stream/nop-stream-core/src/test/java/io/nop/stream/core/checkpoint/TestProcessingGuarantee.java:7-33
disposition: residual-risk
residual_rationale: TestProcessingGuarantee (33 lines) is STILL enum-metadata-style: testStrictExactlyOnceBarrierAlignment, testAtLeastOnceNoBarrierAlignment, testEffectivelyOnceNoAlignment, testBestEffortNoAlignment (:9-32) assert boolean returns from ProcessingGuarantee enum methods (isBarrierAlignment, requiresDurableCheckpoint). These now encode the semantic distinction between the 4 guarantees (barrier alignment vs durable checkpoint) but remain enum-method-return assertions without exercising the actual barrier-alignment/durability pipeline. The vacuous-test defect is still live (improved from pure-constant-boolean to semantic-boolean, but still metadata-level). Non-blocking because (a) the actual barrier-alignment behavior is proven by Stage 9 e2e tests (TestLocalExecutionBarrierAlignment and the multi-JVM recovery suite), (b) this unit test usefully documents the per-guarantee contract flags, and (c) this is a P2 test-quality gap. CONSISTENT with Stage 17 (live-residual) and the recurrent partner M8-2-P2-21 in Shard 18 (residual-risk — same TestProcessingGuarantee root cause, same disposition, no contradiction). Successor: roadmap-stage-23 (test-effectiveness convergence, todo)
note: recurrent partner M8-2-P2-21 = residual-risk in Shard 18; both describe the same TestProcessingGuarantee enum-metadata root cause — CONSISTENT, no unexplained contradiction
@@END

@@DISPOSITION
finding_id: M7-2-P2-15
severity: P2
source_anchor: nop-stream/nop-stream-core/src/test/java/io/nop/stream/core/checkpoint/TestCheckpointIDCounter.java:15-84
disposition: residual-risk
residual_rationale: TestCheckpointIDCounter (84 lines) is STILL single-threaded AtomicLong-semantics wrapping: testDefaultConstructor, testConstructorWithInitialValue, testGetAndIncrement, testIncrementAndGet, testSet, testCompareAndSet, testSequentialIncrements (:24-78). There is NO concurrency test (no multi-thread, no ConcurrentHashSet/Executor) — the "only real risk" named in the corpus desc (concurrent checkpoint-ID allocation) remains untested. The vacuous-test defect is still live. Non-blocking because (a) CheckpointIDCounter is a thin AtomicLong wrapper and checkpoint-ID allocation is coordinator-single-threaded in the production path (no concurrent allocator), making the concurrency risk theoretical, (b) the AtomicLong CAS semantics are guaranteed by the JDK, and (c) this is a P2 test-quality gap, not a correctness defect. CONSISTENT with Stage 17 (live-residual). Successor: roadmap-stage-23 (test-effectiveness convergence, todo)
note: P2 residual-risk permitted with non-blocking rationale; the concurrency risk is theoretical because ID allocation is coordinator-single-threaded
@@END

@@DISPOSITION
finding_id: M7-2-P2-16
severity: P2
source_anchor: nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/operators/windowing/TestWindowOperatorBasic.java:23-72
disposition: residual-risk
residual_rationale: TestWindowOperatorBasic (72 lines) STILL tests only TimeWindow geometry primitives and window/trigger CREATION: testTumblingEventTimeWindowsCreation, testEventTimeTriggerCreation, testTimeWindowProperties, testTimeWindowIntersects, testTimeWindowCover (:25-72). The file name implies WindowOperator coverage it does NOT provide (no WindowOperator pipeline, no processElement/onElement/trigger firing). The misleading-name + geometry-only defect is still live. Non-blocking because (a) the WindowOperator pipeline behavior IS proven by Stage 11 window e2e tests (EVID-S11-023 residual-risk at the capability layer), (b) the TimeWindow geometry primitives (intersects, cover) are legitimately tested here, and (c) this is a P2 test-quality/misleading-name gap, not a correctness defect. CONSISTENT with Stage 11 EVID-S11-023 (residual-risk) and Stage 17 (live-residual). Successor: roadmap-stage-23 (test-effectiveness convergence, todo)
note: P2 residual-risk permitted with non-blocking rationale; the WindowOperator pipeline correctness is owned by Stage 11 e2e tests, not this geometry-primitive unit test
@@END

@@DISPOSITION
finding_id: M7-2-P2-19
severity: P2
source_anchor: nop-stream/README.md:75
disposition: revalidated
revalidation_evidence: The original defect was "StreamExecutionEnvironment documented under datastream but actually at core/environment" (README.md:75). The nop-stream README was rewritten (now 41 lines) and the original anchor README.md:75 NO LONGER EXISTS. The current README no longer documents a StreamExecutionEnvironment package path: the "Quick Start" section (:24-33) uses `StreamExecutionEnvironment.getExecutionEnvironment()` without asserting any package path. The underlying fact remains true (StreamExecutionEnvironment lives at nop-stream-core/.../core/environment/StreamExecutionEnvironment.java — verified by package declaration), but the README no longer contradicts it. The misleading package-path documentation that constituted the defect is gone. No prior evidence row; from-scratch live revalidation (plan Phase 2)
successor_note: anchor disappeared (README:75 — file rewritten to 41 lines); no successor location because the drift content was removed
@@END

@@DISPOSITION
finding_id: M7-2-P2-20
severity: P2
source_anchor: nop-stream/README.md §1.2/§1.4; ai-dev/design/nop-stream/component-roadmap.md §2.1/§2.5; nop-stream/nop-stream-cep/pom.xml
disposition: revalidated
revalidation_evidence: The headline defect — "README says cep depends on nop-xlang but cep pom has nop-core" — is RESOLVED. The README was rewritten and README:15 now correctly states "nop-stream-cep | 活跃 | CEP 引擎（NFA + Pattern API + 声明式模型），依赖 core（IEvalFunction 经 nop-core 传递）", matching the cep pom.xml which declares nop-stream-core (:15-18) as its only stream dependency (plus guava :26-29). The README-vs-pom contradiction is gone. The secondary clause (component-roadmap §2.1 vs §2.5 internal wording) has a mild residue: §2.1 C6 row says "依赖 C1, nop-core（IEvalFunction 经 io.nop.core.lang.eval）" while §2.5 dependency diagram shows "← nop-xlang" and line 15 says "nop-xlang 的 IEvalFunction" — a provenance-wording nuance (the actual package io.nop.core.lang.eval.IEvalFunction confirms nop-core is correct, making §2.1 accurate and the §2.5 diagram/line 15 imprecise). This residue is a deeper design-doc wording issue owned by Stage 23's doc-convergence sweep, not the README-vs-pom contradiction that constituted the finding. The finding's primary defect is fixed
note: the README-vs-pom contradiction (the finding's headline) is resolved; the residual design-doc IEvalFunction-provenance wording (nop-xlang vs nop-core in component-roadmap.md §2.5 diagram/line 15) is a mild nuance owned by Stage 23 doc-convergence, not a live README contradiction
successor_note: README:15 now matches cep pom.xml; the original §1.2/§1.4 README sections no longer exist (file rewritten to 41 lines)
@@END

@@DISPOSITION
finding_id: M7-2-P2-21
severity: P2
source_anchor: nop-stream/README.md §1.2/§1.4; nop-stream/nop-stream-flow/pom.xml
disposition: revalidated
revalidation_evidence: The original defect was "README says flow depends only on core, but flow pom also depends on cep and xdefs". The README was rewritten and README:18 now CORRECTLY states "nop-stream-flow | 活跃 | XDSL 声明式流编排，依赖 core + cep（CepPatternModel）+ nop-xdefs", matching the flow pom.xml exactly (nop-stream-core :15-18, nop-stream-cep :20-23, nop-xdefs :25-28). The README-vs-flow-pom contradiction is resolved — the README now documents all three dependencies that the pom declares. No prior evidence row; from-scratch live revalidation (plan Phase 2)
successor_note: README:18 now matches flow pom.xml; the original §1.2/§1.4 README sections no longer exist (file rewritten to 41 lines)
@@END

## AR Finding Dispositions (2)

@@DISPOSITION
finding_id: O7-2-AR-6
severity: AR
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/jobgraph/JobGraphGenerator.java:509-554
disposition: revalidated
revalidation_evidence: The misplaced-javadoc defect (determinePartitionType javadoc attached to hasNonVirtualOperator) is CORRECTED at current HEAD. hasNonVirtualOperator (now at :531) carries its OWN correct javadoc (:520-530 — "Checks whether the given operator chain contains at least one operator that is actually backed by a non-virtual factory"). determinePartitionType (now at :568) carries its OWN correct javadoc (:554-567 — "Determines the ResultPartitionType based on the StreamEdge's partitioner" with PIPELINED/PIPELINED_BOUNDED documentation). Each method's javadoc now matches its own behavior — the misplacement is gone. status_at_0802: left-for-followup — the followup is COMPLETE at current HEAD. No prior evidence row; from-scratch live revalidation (plan Phase 1)
successor_note: anchor drifted (509-554 → hasNonVirtualOperator at 531 with javadoc 520-530, determinePartitionType at 568 with javadoc 554-567 in current HEAD; the two methods are now separated by findUpstreamVertex :540-552)
@@END

@@DISPOSITION
finding_id: O7-2-AR-7
severity: AR
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/plan/PartitionPolicy.java
disposition: revalidated
revalidation_evidence: The dead enum values UNION and SINGLETON are REMOVED. PartitionPolicy.java:10-16 now declares only four values: FORWARD, HASH, REBALANCE, BROADCAST. The enum body (:12-15) contains no UNION, no SINGLETON. The dead-values defect is fixed and remains fixed. status_at_0802: verified-fixed RECONFIRMED at current HEAD — no regression. No prior evidence row; from-scratch live revalidation (plan Phase 1)
successor_note: anchor stable (PartitionPolicy.java:10-16); file is 16 lines total
@@END
