# Stage 20 — Historical P0/P1 CEP/connector/runtime Finding Disposition (Shard 20, 15 findings)

> Status: produced by Stage 20 (plan `nop-stream-independent-audit/2026-08-08-2000-3-historical-p0p1-cep-connector-runtime-disposition.md`)
> Source corpus: `finding-corpus.md` Shard 20 (frozen at HEAD 2026-08-07; 15 findings, all from 2026-07-25 multi+open audit)
> Validator: `node ai-dev/tools/check-nop-stream-audit-manifest.mjs disposition --shard 20 --strict`
> All anchors revalidated against live repo HEAD on 2026-08-08.
> Disposition vocabulary: `revalidated | stale | active/successor owner | residual-risk | blocked` (finding-disposition 5-value, see `evidence-schema.md` Stage 18 Supplement)

## Disposition Summary

**Totals: 15 findings → 15 revalidated, 0 stale, 0 active/successor owner, 0 residual-risk, 0 blocked**

### Disposition × Severity Cross-Tab

| Disposition \ Severity | P0 | P1 | P2 | AR | Total |
| --- | --- | --- | --- | --- | --- |
| `revalidated` | 3 | 8 | 0 | 4 | 15 |
| `stale` | 0 | 0 | 0 | 0 | 0 |
| `active/successor owner` | 0 | 0 | 0 | 0 | 0 |
| `residual-risk` | 0 | 0 | 0 | 0 | 0 |
| `blocked` | 0 | 0 | 0 | 0 | 0 |
| **Total** | **3** | **8** | **0** | **4** | **15** |

### Disposition × Domain Cross-Tab

| Disposition \ Domain | CEP | coordinator/runtime | connector | Total |
| --- | --- | --- | --- | --- |
| `revalidated` | 4 | 8 | 3 | 15 |
| `stale` | 0 | 0 | 0 | 0 |
| `active/successor owner` | 0 | 0 | 0 | 0 |
| `residual-risk` | 0 | 0 | 0 | 0 |
| `blocked` | 0 | 0 | 0 | 0 |
| **Total** | **4** | **8** | **3** | **15** |

### Cross-Cutting Concern Compliance

- **No P0/P1 still-live defect is silently downgraded to `residual-risk`**: all 3 P0 and 8 P1 findings are `revalidated` (defect fixed against live code). Zero P0/P1 are `residual-risk` or `active/successor owner`.
- **All 4 AR (`status_at_0802: verified-fixed`) reconfirmed at current HEAD**: no regression. Anchor drift handled — O7-2-AR-1 (method renamed `shallowCopyOperator`→`deepCopy`), O7-2-AR-2 (file migrated to `nop-stream-connector-batch/`), O7-2-AR-3 (line drift + second site relocated), O7-2-AR-4 (line expansion). New anchors recorded in each `revalidation_evidence`.
- **Stage 6/12/13/15 cross-reference consistency**: M7-2-P0-1 (Stage 6 RESOLVED + Stage 12 EVID-S12-002 FIXED), M7-2-P0-4 (Stage 12 EVID-S12-014 FIXED), M7-2-P0-6 (Stage 13 EVID-S13-007/020 RESOLVED), M7-2-P1-5 (Stage 6 EVID-S6-011 RESOLVED), M7-2-P1-9 (Stage 15 EVID-S15-008 FIXED) — all consistent with this Stage 20 `revalidated` verdict.
- **From-scratch revalidation (no prior evidence)**: M7-2-P1-8 (InputGate interrupt handling), M7-2-P1-10 (ResultPartition.close data loss) — both FIXED at HEAD with dedicated regression tests (`TestInputGateTermination`, `TestResultPartitionDeadlock`).
- **Test-effectiveness P1 (M7-2-P1-13/14/15)**: remediation plan `2026-08-04-2300-3-contract-drift-config-test-integrity.md` (`completed`) and predecessor `2026-07-26-0804-2-parallel-execution-cep-correctness.md` (`completed`) fully addressed these — tests rewritten with behavior coverage (P1-13/14) and boundary tests + thread-safety contract documentation (P1-15). All `revalidated`.
- **M7-2-P1-12 (watermark multi-input combine unit-only)**: Stage 11 EVID-S11-016 adjudicated the capability as `non-goal` (Anti-Hollow exemption — zero `TwoInputStreamOperator` consumers exist by design). The finding's self-exemption holds; `revalidated` by formal adjudication.

---

## P0 Finding Dispositions (3)

@@DISPOSITION
finding_id: M7-2-P0-1
severity: P0
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/datastream/SingleOutputStreamOperator.java:33; nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/datastream/SingleOutputStreamOperatorImpl.java:46-50; nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/PatternStreamBuilder.java:168
disposition: revalidated
revalidation_evidence: SingleOutputStreamOperatorImpl.forceNonParallel():52-58 now calls transformation.lockParallelismToOne() instead of unconditionally throwing UnsupportedOperationException. The non-keyed CEP entry path (PatternStreamBuilder.build() synthesizes NullByteKeySelector + keyBy + transform + forceNonParallel) builds and runs without crashing. Proven by regression tests TestCepNonKeyedEntryE2E#cepPatternOnNonKeyedStreamBuildsWithoutThrowing + #cepPatternOnNonKeyedStreamProducesMatches. Cross-ref Stage 6 EVID-S6-013 (RESOLVED, LOCAL path) and Stage 12 EVID-S12-002 (FIXED, disposition: e2e-proved). CONSISTENT with Stage 6/12 verdicts
successor_note: anchor drifted (46-50 → 52-58 in current HEAD); the lock flag propagates Transformation→StreamNode→JobVertex→GraphExecutionPlan
@@END

@@DISPOSITION
finding_id: M7-2-P0-4
severity: P0
source_anchor: nop-stream/nop-stream-cep/src/test/java/io/nop/stream/cep/operator/TestCepOperatorDanglingCleanup.java:81-99
disposition: revalidated
revalidation_evidence: TestCepOperatorDanglingCleanup#testDanglingCleanupReleasesSharedBuffer now computes partialMatchesEmpty (:114-115) AND asserts it assertTrue (:116-119), AND additionally asserts operator.getPartialMatches().isEmpty() (:121-122) to confirm SharedBuffer entries were released. The test would now fail if the dangling-cleanup logic were deleted (explicit "P0-4 fix" comment at :111-113). Cross-ref Stage 12 EVID-S12-014 (FIXED, disposition: e2e-proved). CONSISTENT with Stage 12 verdict
successor_note: anchor drifted (81-99 → 111-122 in current HEAD); test rewritten with explicit assertions
@@END

@@DISPOSITION
finding_id: M7-2-P0-6
severity: P0
source_anchor: nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/; nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/taskmanager/TestTaskManager.java:73-78
disposition: revalidated
revalidation_evidence: Fencing-token rejection of stale attempt output formerly had ZERO tests. Now TaskManager.receiveAssignment(:262) throws ERR_STREAM_FENCING_TOKEN_MISMATCH (:276), TaskManager.deployTask(:367) throws (:389), TaskManager.triggerCheckpoint(:514) throws (:521) — was silent LOG.warn+return. Guarded by TestFencingTokenRejection (stale-token assignment/checkpoint trigger throw) + TestFencingEpochUnification. Cross-ref Stage 13 EVID-S13-007 / EVID-S13-020 (RESOLVED, disposition: e2e-proved). CONSISTENT with Stage 13 verdict
successor_note: anchor drifted (TestTaskManager.java:73-78 → the fencing logic now lives in TaskManager.java production code :262/:276/:367/:389/:514/:521, with dedicated regression tests TestFencingTokenRejection + TestFencingEpochUnification)
@@END

## P1 Finding Dispositions (8)

@@DISPOSITION
finding_id: M7-2-P1-5
severity: P1
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/StreamOperator.java:59-78; nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/jobgraph/OperatorChain.java:99-149
disposition: revalidated
revalidation_evidence: StreamOperator.finish() lifecycle hook is NO LONGER silently inactive. StreamTaskInvokable calls operatorChain.finish() at :402 (SOURCE role), :448 (MIDDLE), :476 (SINK), :508/:523 (SELF_CONTAINED) before MAX_WATERMARK emission and operator close — directly addressing the prior "buffered-data flush contract silently inactive" defect. Explicit "P1-5" comment at StreamTaskInvokable.java:397-400 documents the fix. Cross-ref Stage 6 EVID-S6-011 (RESOLVED, LOCAL path). CONSISTENT with Stage 6 verdict
successor_note: anchor drifted (StreamOperator.java:59-78 / OperatorChain.java:99-149 → the fix is at the CALL SITE StreamTaskInvokable.java:397-402,448,476,508,523 in current HEAD)
@@END

@@DISPOSITION
finding_id: M7-2-P1-8
severity: P1
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/InputGate.java:262-278
disposition: revalidated
revalidation_evidence: InputGate.readSingleChannel() at InputGate.java:378-399 no longer swallows InterruptedException. The catch block (:390-398) now restores interrupt status via Thread.currentThread().interrupt() (:396) and returns Optional.empty() (:397), cooperating with the caller's loop-break contract. Explicit "P1-8" comment at :391-395 documents the fix. Symmetric with readMultiChannel (:444-447). Regression coverage: TestInputGateTermination#testSingleChannelInterruptReturnsEmpty (unit: asserts empty return + interrupt flag set) + TestTaskLifecycle#testSubtaskTaskCancelViaInterruptReachesCanceled (e2e: SubtaskTask ends CANCELED not FAILED). Mailbox cooperative-cancel contract verified at StreamTaskInvokable.processInputGate:535-550. No prior evidence row; from-scratch live revalidation (plan Phase 2)
successor_note: anchor drifted (262-278 → 378-399 in current HEAD; file grew due to Stage 43/45 barrier/unaligned-checkpoint additions)
@@END

@@DISPOSITION
finding_id: M7-2-P1-9
severity: P1
source_anchor: nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/MessageSourceFunction.java:122-145
disposition: revalidated
revalidation_evidence: MessageSourceFunction.run() no longer silently swallows collect exceptions. The subscriber thread captures failures into a pendingError field (MessageSourceFunction.java:152-155, explicit "P1-9" comment) and run() rethrows after the loop exits (:174-180), surfacing the failure to the invokable so the task is marked FAILED (not mistaken normal completion / data loss). Javadoc at :62-64 documents the capture-and-rethrow contract. Cross-ref Stage 15 EVID-S15-008 (FIXED). CONSISTENT with Stage 15 verdict
successor_note: anchor drifted (122-145 → the capture-and-rethrow mechanism spans :62-64,133,150-155,174-180 in current HEAD)
@@END

@@DISPOSITION
finding_id: M7-2-P1-10
severity: P1
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/ResultPartition.java:178-193
disposition: revalidated
revalidation_evidence: ResultPartition.close() at ResultPartition.java:316-329 no longer discards un-consumed records. The data-loss path (queue.clear() + offer) is gone; close() now uses blocking queue.put(END_OF_STREAM) that respects backpressure and preserves all in-flight data (:318-329). InterruptedException is propagated (not swallowed) so cancel/abort still unblocks the producer (:320-328). Explicit "P1-10" Javadoc at :301-315 documents the fix. Regression coverage: TestResultPartitionDeadlock#testFullQueueDrainsAfterCloseNoDataLoss (:88, 4 records on capacity-4 queue, close() in another thread, all 4 remain — no data loss) + #testInterruptDuringCloseRethrowsAndDoesNotDropData (:134) + #testCloseOnFullQueueBackpressuresUntilConsumerDrains (:16). Note: distinct from O7-2-AR-5 (cross-JVM RemoteResultPartition permit double-release). No prior evidence row in any stage; from-scratch live revalidation (plan Phase 2). Fix attributable to commit ecfa94204 (2026-07-26)
successor_note: anchor drifted (178-193 → 316-329 in current HEAD; file grew to 527 lines due to Stage 43/44 drainBufferedElements/injectFront/materialization-point additions)
@@END

@@DISPOSITION
finding_id: M7-2-P1-12
severity: P1
source_anchor: nop-stream/nop-stream-core/src/test/java/io/nop/stream/core/common/eventtime/TestIndexedCombinedWatermarkStatus.java:14-22
disposition: revalidated
revalidation_evidence: The finding "watermark multi-input combine has only unit tests, no e2e (self-exempts via Anti-Hollow exemption)" is resolved by formal adjudication. TestIndexedCombinedWatermarkStatus (145 lines) remains unit-only BUT the valve IndexedCombinedWatermarkStatus.forInputsCount(N) is N-capable by design and there are ZERO TwoInputStreamOperator implementations and ZERO runtime callers of processWatermark1/2 in the entire nop-stream codebase (no connect/union/join/coGroup operators exist). The unit tests (:26-144) verify min-combine, idleness exclusion, no-regress, and N>2 capability. Stage 11 EVID-S11-016 adjudicated the capability as `non-goal` with Anti-Hollow exemption per time-model-design G47 (no supported consumer to wire e2e). The Javadoc at :14-18 explicitly records: "Runtime wiring (e2e) is deferred to the two-input-operator successor because nop-stream has no two-input operator consumer (Anti-Hollow exemption)." The self-exemption holds; this is the sanctioned design state, not a live defect. CONSISTENT with Stage 11 EVID-S11-016 (non-goal) verdict. No prior evidence row at finding-disposition layer; from-scratch live revalidation (plan Phase 2)
successor_note: anchor expanded (14-22 → 14-145 full file in current HEAD; the valve math is unchanged, the test file grew with N-input and idleness coverage)
@@END

@@DISPOSITION
finding_id: M7-2-P1-13
severity: P1
source_anchor: nop-stream/nop-stream-cep/src/test/java/io/nop/stream/cep/operator/TestCepOperatorStateBackendWiring.java:139-166
disposition: revalidated
revalidation_evidence: TestCepOperatorStateBackendWiring no longer couples to internal accessors getKeyedStateBackend()/getNFAStateForTesting(). The file (176 lines) was rewritten to verify behavior via observable output and snapshotState() — explicit "P1-13 fix" Javadoc at :29-43 documents the decoupling. testConfiguredStateBackendProcessesMatchesAndSnapshotsState (:88-120) asserts pattern output (:105-107) + non-empty snapshot state (:115-117) "without coupling to getKeyedStateBackend() / getNFAStateForTesting()" (:111 comment). Zero calls to getKeyedStateBackend() or getNFAStateForTesting() remain in the file. Plan 2026-07-26-0804-2-parallel-execution-cep-correctness.md Phase 3 (completed) credited at :42. CONSISTENT with Stage 12 EVID-S12-015 (which recorded the coupling as live-residual; the subsequent completed remediation plans resolved it). Stage 17 successor registry is superseded by the completed fix
successor_note: anchor drifted (139-166 → the full file 1-176 was rewritten; the prior coupling at :139-166 no longer exists)
@@END

@@DISPOSITION
finding_id: M7-2-P1-14
severity: P1
source_anchor: nop-stream/nop-stream-cep/src/test/java/io/nop/stream/cep/nfa/aftermatch/TestAfterMatchSkipStrategies.java:1-75
disposition: revalidated
revalidation_evidence: TestAfterMatchSkipStrategies (184 lines) is no longer 100% metadata assertions. The file was rewritten with explicit "P1-14 fix" Javadoc (:30-43): (a) allFactoryMethodsProduceExpectedMetadata (:67-92) consolidates the prior 13 metadata-only methods into one; (b) real NFA behavior tests added — noSkipProducesAllOverlappingMatches (:140-148), skipPastLastEventProducesSingleMatch (:150-158), skipToNextProducesSameStartExcludedMatches (:160-170), strategiesProduceDifferentMatchCountsOnSameInput (:172-183, anti-hollow: asserts noSkip > skipPastLastEvent, fails if skip logic deleted). The runWithStrategy helper (:103-127) drives a real CepOperator with each strategy on the same input sequence. Plan 2026-07-26-0804-2-parallel-execution-cep-correctness.md Phase 3 (completed) credited at :42. CONSISTENT with Stage 12 EVID-S12-016 (which recorded partial-fix with TestCepSkipStrategyE2E; the subsequent completed remediation fully resolved the metadata-only residual). Stage 17 successor registry is superseded by the completed fix
successor_note: anchor drifted (1-75 → the full file 1-184 was rewritten; real NFA behavior coverage now in-file)
@@END

@@DISPOSITION
finding_id: M7-2-P1-15
severity: P1
source_anchor: nop-stream/nop-stream-connector-batch/src/test/java/io/nop/stream/connector/batch/TestBatchConsumerSinkFunction.java:22-103
disposition: revalidated
revalidation_evidence: TestBatchConsumerSinkFunction (130 lines) is no longer happy-path-only. Boundary tests explicitly marked "P1-15 boundary tests" (:102): testBatchSizeZeroRejected (:104-111), testBatchSizeNegativeRejected (:113-118), testConsumeNullRejected (:120-129). Plus happy-path and flush-lifecycle coverage: testBufferAndFlush (:22-39), testCloseFlushesRemaining (:41-56), testMultipleFlushes (:58-78), testBatchSizeOne (:85-100), testNullProviderRejected (:80-83). The "concurrency" dimension named in the corpus desc is N/A by design: BatchConsumerSinkFunction Javadoc (:39-45, explicit "Thread-safety contract (P1-15)") documents that the nop-stream operator model executes each subtask on a single task thread, so consume() is NOT designed for concurrent invocation and the internal buffer is unsynchronized by design. Implementation (BatchConsumerSinkFunction.java:47-151) confirms single-threaded contract with fail-fast boundary checks (consume(:77-89) rejects null, constructor (:62-74) rejects batchSize<1). Plan 2026-08-04-2300-3-contract-drift-config-test-integrity.md (completed) addressed the boundary gap. CONSISTENT with Stage 15 (residual-risk, partial) and Stage 17 (live-residual partial) — the subsequent completed remediation resolved both boundary + concurrency-documentation dimensions
successor_note: anchor migrated (the test moved from nop-stream-connector/ to nop-stream-connector-batch/ per O7-2-AR-2; file expanded 22-103 → 1-130 in current HEAD)
@@END

## AR Finding Dispositions (4 — verified-fixed reconfirmation)

@@DISPOSITION
finding_id: O7-2-AR-1
severity: AR
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/jobgraph/OperatorChain.java:206-235
disposition: revalidated
revalidation_evidence: OperatorChain no longer silently shares mutable operator instances for unhandled types. The method was renamed shallowCopyOperator()→deepCopy() at OperatorChain.java:244-250; it now calls op.copyForSubtask() per operator (:247, per-subtask copy) and the StreamOperator.copyForSubtask() default (StreamOperator.java:181-189) throws UnsupportedOperationException for non-Shareable/undecorated ops — fail-fast instead of silent mutable sharing. Javadoc at OperatorChain.java:226-243 explicitly documents the AR-1 contract. Wired live from GraphExecutionPlan.java:377, SupervisionLoop.java:556, RemoteGraphExecutionPlanBuilder.java:150. Regression test TestOperatorSubtaskIsolation exists. status_at_0802: verified-fixed RECONFIRMED at current HEAD — no regression
successor_note: anchor drifted (206-235 → method renamed to deepCopy() at 244-250 in current HEAD; corpus range 206-235 is now getOperators()/Javadoc)
@@END

@@DISPOSITION
finding_id: O7-2-AR-2
severity: AR
source_anchor: nop-stream/nop-stream-connector-batch/src/main/java/io/nop/stream/connector/batch/StreamConnectors.java:10-11; nop-stream/nop-stream-connector/pom.xml:22-24,33-36
disposition: revalidated
revalidation_evidence: StreamConnectors no longer causes NoClassDefFoundError at class-load time in the base connector. StreamConnectors.java was migrated to nop-stream-connector-batch/ (StreamConnectors.java:10-11 imports nop-batch-core types IBatchConsumerProvider/IBatchLoaderProvider); nop-stream-connector-batch/pom.xml:20-23 owns nop-batch-core as a legitimate compile dep. The base nop-stream-connector/pom.xml:14-29 carries the AR-2 migration comment and :31-50 lists only nop-stream-core + test deps — no nop-batch-core, no nop-message-debezium. The base connector is structurally class-loadable without optional deps. status_at_0802: verified-fixed RECONFIRMED at current HEAD — no regression
successor_note: anchor migrated (StreamConnectors.java moved from nop-stream-connector/ to nop-stream-connector-batch/; pom.xml comment records the AR-2 migration at :14-29)
@@END

@@DISPOSITION
finding_id: O7-2-AR-3
severity: AR
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/graph/PartitionedPlanGenerator.java:83-99; nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/GraphExecutionPlan.java:430-445
disposition: revalidated
revalidation_evidence: Both partitioner-to-policy inference sites no longer use fragile class-name string matching. Site 1: PartitionedPlanGenerator.inferPartitionPolicy at PartitionedPlanGenerator.java:84-108 uses instanceof PartitionPolicyAware polymorphic dispatch (:88-90) then fail-fast throw (:98-107) for unidentified partitioners — explicit "AR-3" comment at :91-97. Site 2: GraphExecutionPlan.resolvePartitionPolicy at GraphExecutionPlan.java:541-569 uses the same instanceof + fail-fast pattern (:554-556, throw :560-569). New interface PartitionPolicyAware (execution/plan/PartitionPolicyAware.java:12-14) declares getPartitionPolicy(); ForwardPartitioner and KeySelectorPartitioner implement it. The only remaining getClass().getName() is a diagnostic param on the thrown exception (:107), NOT a routing decision. Regression test TestPartitionPolicyInference#testClassNameSubstringNoLongerMatches (:50-57, partitioner whose class name contains "Hash" but does not implement PartitionPolicyAware asserts inference throws). status_at_0802: verified-fixed RECONFIRMED at current HEAD — no regression
successor_note: anchor drifted (PartitionedPlanGenerator 83-99 → 84-108; GraphExecutionPlan 430-445 → 541-569 resolvePartitionPolicy in current HEAD)
@@END

@@DISPOSITION
finding_id: O7-2-AR-4
severity: AR
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/SimpleStreamOperatorFactory.java:46-72
disposition: revalidated
revalidation_evidence: SimpleStreamOperatorFactory.createStreamOperator() at SimpleStreamOperatorFactory.java:47-96 no longer silently falls back to a shared template instance on NotSerializableException. The NotSerializableException catch (:69-83) throws StreamException fail-fast (explicit "AR-4" comment at :70-78 documents the prior silent-sharing-corrupts-parallel-execution bug). The non-Serializable non-Shareable path (:89-95) also throws. The only shared-instance return (:50-54) is the explicit, LOG.warn-documented Shareable opt-out (operator.isShareable() true). All three branches verified: Shareable opt-out (documented), NotSerializableException (throw), non-Serializable non-Shareable (throw) — no silent fallback anywhere. status_at_0802: verified-fixed RECONFIRMED at current HEAD — no regression
successor_note: anchor drifted (46-72 → 47-96 in current HEAD; method expanded with more thorough fail-fast handling)
@@END
