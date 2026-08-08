# Stage 22 — Historical P2 CEP/connector/runtime Finding Disposition (Shard 22, 5 findings)

> Status: produced by Stage 22 (plan `nop-stream-independent-audit/2026-08-08-2100-2-historical-p2-cep-connector-runtime-disposition.md`)
> Source corpus: `finding-corpus.md` Shard 22 (frozen at HEAD 2026-08-07; 5 findings, all from 2026-07-25 multi+open audit)
> Validator: `node ai-dev/tools/check-nop-stream-audit-manifest.mjs disposition --shard 22 --strict`
> All anchors revalidated against live repo HEAD on 2026-08-08.
> Disposition vocabulary: `revalidated | stale | active/successor owner | residual-risk | blocked` (finding-disposition 5-value, see `evidence-schema.md` Stage 18 Supplement)

## Disposition Summary

**Totals: 5 findings → 2 revalidated, 0 stale, 0 active/successor owner, 3 residual-risk, 0 blocked**

### Disposition × Severity Cross-Tab

| Disposition \ Severity | P0 | P1 | P2 | AR | Total |
| --- | --- | --- | --- | --- | --- |
| `revalidated` | 0 | 0 | 1 | 1 | 2 |
| `stale` | 0 | 0 | 0 | 0 | 0 |
| `active/successor owner` | 0 | 0 | 0 | 0 | 0 |
| `residual-risk` | 0 | 0 | 3 | 0 | 3 |
| `blocked` | 0 | 0 | 0 | 0 | 0 |
| **Total** | **0** | **0** | **4** | **1** | **5** |

### Disposition × Domain Cross-Tab

| Disposition \ Domain | CEP | coordinator/runtime | connector | Total |
| --- | --- | --- | --- | --- |
| `revalidated` | 1 | 1 | 0 | 2 |
| `stale` | 0 | 0 | 0 | 0 |
| `active/successor owner` | 0 | 0 | 0 | 0 |
| `residual-risk` | 2 | 1 | 0 | 3 |
| `blocked` | 0 | 0 | 0 | 0 |
| **Total** | **3** | **2** | **0** | **5** |

### Cross-Cutting Concern Compliance

- **No P2 silently downgraded from a reclassified P0/P1**: every P2 here remained P2 after live revalidation (no P2 was found to be a hidden P0/P1). Zero findings carry `active/successor owner` because no still-live P2 was reclassified upward and no in-scope defect requires a fresh remediation plan owner (all residual-risk P2s carry explicit non-blocking rationale, which P2 permits).
- **Every P2 `residual-risk` has explicit non-blocking rationale**: all 3 P2 residual-risk blocks (M7-2-P2-14/17/18) carry `residual_rationale`. None is a silent drop. Each references the capability-evidence owner that proves the real behavior (runtime lifecycle tests / TestSharedBufferExtended / Stage 12 CEP e2e) and names `roadmap-stage-23` (documentation contract and test-effectiveness convergence, roadmap status `todo`) as the successor for test-quality remediation.
- **AR reconfirmation at current HEAD**: O7-2-AR-5 (`status_at_0802: left-for-followup`) — the `ResultPartition.close()` bufferPool permit double-release race (concurrent consumer reads angle, DISTINCT from M7-2-P1-10's data-loss angle) is structurally impossible at current HEAD because `close()` does NOT interact with `bufferPool` at all; `revalidated`. The permit-accounting angle is distinct from the M7-2-P1-10 data-loss angle (disposed `revalidated` in Stage 20) and was revalidated independently from scratch.
- **Cross-reference consistency**: M7-2-P2-8 `revalidated` is CONSISTENT with Stage 12 EVID-S12-007 (e2e-proved FIXED) and Stage 17 (closed FIXED). M7-2-P2-14/17/18 `residual-risk` are CONSISTENT with Stage 12 (EVID-S12-017/018 residual-risk) and Stage 17 (live-residual). No unexplained contradiction with prior stages.
- **Successor ownership for still-live test-quality P2s**: the prior successors named in Stage 12 (plan `2026-08-04-2300-3`, now `completed`; `roadmap-stage-17`, now `done`) are NO LONGER valid `owner_plan` targets (validator rejects completed plans and done stages). The still-live test-quality P2s are P2 (which permits `residual-risk` with non-blocking rationale), so they do NOT require `active/successor owner`; their residual_rationale names `roadmap-stage-23` (`todo`, non-done) as the successor lane for future test-effectiveness convergence. No P2 was silently dropped or silently downgraded.

---

## AR Finding Disposition (1)

@@DISPOSITION
finding_id: O7-2-AR-5
severity: AR
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/ResultPartition.java:178-193
disposition: revalidated
revalidation_evidence: The AR-5 defect is "ResultPartition.close() bufferPool permit double-release race during concurrent consumer reads" — a permit-accounting angle DISTINCT from M7-2-P1-10 (data-loss angle, disposed revalidated in Stage 20). Live trace of close() → bufferPool interaction at current HEAD confirms the permit double-release race is structurally impossible. (1) close() at ResultPartition.java:316-329 does NOT touch bufferPool at all: it sets finished=true and calls queue.put(END_OF_STREAM) — there is NO bufferPool.acquire() and NO bufferPool.release() anywhere in close(). Since close() never releases a permit, it cannot double-release one. (2) Permit accounting for the END_OF_STREAM sentinel is balanced 0/0: close() enqueues END_OF_STREAM with 0 permits acquired (no acquire call); read() at :267-276 consumes END_OF_STREAM and returns null WITHOUT calling bufferPool.release() (the `if (element == END_OF_STREAM) return null` at :269-271 short-circuits before the release at :272-274); read(timeout) at :286-299 mirrors this (the `if (element == END_OF_STREAM) return null` at :292-293 short-circuits before release at :295-297). So the sentinel neither acquires nor releases a permit — no leak, no double-release. (3) Concurrent consumer reads (read/read-timeout/drainBufferedElements) all use LinkedBlockingQueue.take()/poll() which are thread-safe: each element is handed to exactly ONE consumer, so each element's single release happens exactly once across all concurrent consumers — no double-release from concurrency. (4) write() acquires exactly 1 permit per data element (:231/:238) and releases on failure paths (:235 offer-fail, :243 interrupt) — balanced with the single release per consumed element. Therefore the AR-5 permit-accounting angle (close() causing a bufferPool permit double-release race during concurrent consumer reads) is FIXED at current HEAD. The fix is attributable to the close() rewrite that also resolved M7-2-P1-10 (Stage 20): the old close() that could interact with queue contents/permits was replaced by the sentinel-put close() that is permit-neutral. status_at_0802: left-for-followup — the followup is COMPLETE at current HEAD. No prior evidence row (Stage 13/14 evidence files have no AR-5 row; Stage 20 disposed the data-loss angle M7-2-P1-10, not the permit-accounting angle AR-5); from-scratch live revalidation of the permit-accounting angle (plan Phase 1). Note: a separate permit-accounting nuance exists in the Stage 43 recovery path injectFront() (:430-468), which releases a permit when it encounters the EOS sentinel during its drain phase (:441-443) — but this is a different code path (unaligned-checkpoint recovery, not close()/concurrent-read) and is NOT the AR-5 angle; it is recorded here as an observation for a future audit round, not a confirmed AR-5 regression
successor_note: anchor drifted (178-193 → 316-329 close() in current HEAD; file grew to 527 lines due to Stage 43/44 bufferPool/materialization-point additions); close() is now permit-neutral (no bufferPool interaction), making the double-release race structurally impossible
@@END

## P2 Finding Dispositions (4)

@@DISPOSITION
finding_id: M7-2-P2-8
severity: P2
source_anchor: nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/sharedbuffer/Lockable.java:54-79
disposition: revalidated
revalidation_evidence: Lockable.release() at Lockable.java:56-66 now throws `new StreamRuntimeException("Lockable over-release: refCounter went negative")` at :62 — a platform exception (io.nop.stream.core.exceptions.StreamRuntimeException), NOT a bare `IllegalStateException`. The original defect ("Lockable.release throws bare IllegalStateException on ref-count underflow instead of StreamException/NopException") is FIXED. The method also defensively resets the counter to 0 (:61) and uses compareAndSet for the decrement (:64). releaseOrDetach() at :68-81 mirrors this with the same StreamRuntimeException (:74). Cross-ref Stage 12 EVID-S12-007 (e2e-proved FIXED — TestLockableOverRelease#testOverReleaseDoesNotThrowBareIllegalStateException asserts the exception type is NOT a bare IllegalStateException). CONSISTENT with Stage 12 and Stage 17 verdicts (Stage 17 marked this closed/FIXED)
successor_note: anchor drifted (54-79 → 56-66 release + 68-81 releaseOrDetach in current HEAD); the bare-IllegalStateException is replaced by StreamRuntimeException at :62/:74
@@END

@@DISPOSITION
finding_id: M7-2-P2-14
severity: P2
source_anchor: nop-stream/nop-stream-core/src/test/java/io/nop/stream/core/checkpoint/TestJobTerminationContext.java:7-39
disposition: residual-risk
residual_rationale: TestJobTerminationContext (39 lines) is STILL factory-method field-assignment level: testCancelFactory/testDrainFactory/testSuspendFactory/testExportSavepointFactory (:9-38) assert the mode/flags/timeout/namespace fields populated by the JobTerminationContext factory methods (cancel/drain/suspend/exportSavepoint). These verify that each factory produces a context with the correct termination-mode semantics (e.g. cancel→isAbortTransactions=true/isWaitForSinkCommit=false; drain→isWaitForSinkCommit=true/timeout=60000), which is a meaningful contract-level check, but the file does NOT exercise the actual job-termination pipeline (no job execution, no sink-commit-cut, no transaction-abort end-to-end). The vacuous/factory-method-only defect is still live at the test-effectiveness level. Non-blocking because (a) the real job-termination lifecycle (FAILED→cancel tasks, drain→sink commit, suspend→savepoint) is owned by the runtime lifecycle e2e tests (Stage 13/14 control-plane and multi-JVM recovery suites prove the termination pipeline end-to-end), (b) the factory-method field assertions are low-value-but-not-zero-value (they pin the per-mode contract flags), and (c) this is a P2 test-quality gap, not a correctness defect. CONSISTENT with Stage 17 (live-residual, runtime domain). Successor: roadmap-stage-23 (documentation contract and test-effectiveness convergence, roadmap status todo) — the prior successor plan 2026-08-04-2300-3 is completed and roadmap-stage-17 is done, so neither is a valid owner_plan target; this P2 is permitted to carry residual-risk with non-blocking rationale
note: P2 residual-risk permitted with non-blocking rationale; no P0/P1 reclassification (the real job-termination pipeline is e2e-proven by Stage 13/14, not this factory-method unit test)
@@END

@@DISPOSITION
finding_id: M7-2-P2-17
severity: P2
source_anchor: nop-stream/nop-stream-cep/src/test/java/io/nop/stream/cep/nfa/sharedbuffer/TestSharedBuffer.java:21-71
disposition: residual-risk
residual_rationale: The original defect ("TestSharedBuffer overuses assertNotNull(id) where concrete EventId assertions exist in siblings") is PARTIALLY mitigated but the residual persists. The file expanded from 71 lines (corpus freeze) to 319 lines with substantial concrete assertions: testAdvanceTimePreventsEventIdCollision (:214-238) uses assertNotEquals on EventId + assertEquals on event.getId(); testAdvanceTimeCountersResetAndNoCollision (:272-295) asserts concrete EventId.getId() values (:292-293); testCacheEvictionUnderPressure (:136-164) asserts event.getElement().getId() (:161). HOWEVER the early methods in the original corpus range still use assertNotNull(id)/assertNotNull(nodeId) where concrete assertions are now available in the SAME file: assertNotNull(id) at :29 (testRegisterAndRetrieveEvent), assertNotNull(nodeId) at :66 (testSharedBufferNodeRegistration), assertNotNull(nodeId1/nodeId2) at :84-85 (testRetrieveByCondition) — these could assert concrete EventId equality (as the advance-time methods now do) but still use the weaker assertNotNull. The overuse residual is reduced (concrete assertions now exist in-file) but not eliminated in the early methods. Non-blocking because (a) the real SharedBuffer lifecycle behavior (register/put/lock/release/extract/materialize/advance-time/cache-eviction) IS proven — both by the expanded methods in this file AND by TestSharedBufferExtended (Stage 12 EVID-S12-006 e2e-proved), (b) the assertNotNull calls in early methods are low-value-but-not-zero-value (they confirm non-null registration), and (c) this is a P2 test-quality gap, not a correctness defect. CONSISTENT with Stage 12 EVID-S12-017 (residual-risk, "real lifecycle proven by TestSharedBufferExtended") and Stage 17 (live-residual, CEP domain). Successor: roadmap-stage-23 (documentation contract and test-effectiveness convergence, roadmap status todo)
note: P2 residual-risk permitted with non-blocking rationale; the SharedBuffer lifecycle correctness is e2e-proven by TestSharedBufferExtended (Stage 12 EVID-S12-006) and the expanded methods in this file; no P0/P1 reclassification
@@END

@@DISPOSITION
finding_id: M7-2-P2-18
severity: P2
source_anchor: nop-stream/nop-stream-cep/src/test/java/io/nop/stream/cep/nfa/TestNFAState.java:11-80
disposition: residual-risk
residual_rationale: The original defect ("TestNFAState equals/hashCode mirror tests; only testNotEqualWhenMatchesDiffer has real protection") is PARTIALLY mitigated but the residual persists. The file now has 4 tests (81 lines): testEqualsWithNonEmptyPartialMatchesDoesNotThrow (:18-35) verifies equals/hashCode do NOT throw on non-empty PriorityQueue partial matches (meaningful — guards against the Comparator-based queue comparison throwing during equals); testEqualsWithNonEmptyCompletedMatchesDoesNotThrow (:38-50) mirrors this for completed matches; testNotEqualWhenMatchesDiffer (:53-65) tests inequality (real protection); testHashCodeConsistencyWithMultipleElements (:68-80) tests hashCode determinism. So "only testNotEqualWhenMatchesDiffer has real protection" is no longer literally true (the non-empty-equals-does-not-throw tests add real protection against a Comparator-throws regression). HOWEVER the file is STILL equals/hashCode-level testing — it does NOT exercise NFA state-transition behavior, partial-match progression, or ComputationState lifecycle. The mirror-test nature (testing the value object's equals/hashCode rather than NFA matching behavior) persists. Non-blocking because (a) the real NFA matching behavior (pattern detection, skip strategies, branching, SharedBuffer interaction) IS proven by Stage 12 CEP e2e tests (EVID-S12-001..006, TestCepOperatorDanglingCleanup, TestCepNonKeyedEntryE2E, etc.), (b) the equals/hashCode non-throwing coverage is low-value-but-not-zero-value (NFAState is checkpointed and must serialize/equals without throwing), and (c) this is a P2 test-quality gap, not a correctness defect. CONSISTENT with Stage 12 EVID-S12-018 (residual-risk) and Stage 17 (live-residual, CEP domain). Successor: roadmap-stage-23 (documentation contract and test-effectiveness convergence, roadmap status todo)
note: P2 residual-risk permitted with non-blocking rationale; the NFA matching correctness is e2e-proven by Stage 12 CEP tests, not this equals/hashCode unit test; no P0/P1 reclassification
@@END
