# Stage 19 — Historical P0/P1 Core/State/Window Finding Disposition (Shard 19, 16 findings)

> Status: produced by Stage 19 (plan `nop-stream-independent-audit/2026-08-08-2000-2-historical-p0p1-core-state-window-disposition.md`)
> Source corpus: `finding-corpus.md` Shard 19 (frozen at HEAD 2026-08-07; 16 findings, all from 2026-07-25 multi-audit)
> Validator: `node ai-dev/tools/check-nop-stream-audit-manifest.mjs disposition --shard 19 --strict`
> All anchors revalidated against live repo HEAD on 2026-08-08.
> Disposition vocabulary: `revalidated | stale | active/successor owner | residual-risk | blocked` (finding-disposition 5-value, see `evidence-schema.md` Stage 18 Supplement)

## Disposition Summary

**Totals: 16 findings → 15 revalidated, 0 stale, 1 active/successor owner, 0 residual-risk, 0 blocked**

### Disposition × Severity Cross-Tab

| Disposition \ Severity | P0 | P1 | P2 | AR | Total |
| --- | --- | --- | --- | --- | --- |
| `revalidated` | 5 | 10 | 0 | 0 | 15 |
| `stale` | 0 | 0 | 0 | 0 | 0 |
| `active/successor owner` | 0 | 1 | 0 | 0 | 1 |
| `residual-risk` | 0 | 0 | 0 | 0 | 0 |
| `blocked` | 0 | 0 | 0 | 0 | 0 |
| **Total** | **5** | **11** | **0** | **0** | **16** |

### Disposition × Domain Cross-Tab

| Disposition \ Domain | checkpoint/state | window | contract/test | Total |
| --- | --- | --- | --- | --- |
| `revalidated` | 8 | 0 | 7 | 15 |
| `stale` | 0 | 0 | 0 | 0 |
| `active/successor owner` | 0 | 0 | 1 | 1 |
| `residual-risk` | 0 | 0 | 0 | 0 |
| `blocked` | 0 | 0 | 0 | 0 |
| **Total** | **8** | **0** | **8** | **16** |

### Cross-Cutting Concern Compliance

- **No P0/P1 still-live defect is silently downgraded to `residual-risk`**: all 5 P0 and 10 of 11 P1 findings are `revalidated` (defect fixed against live code); the 1 remaining still-live P1 (M7-2-P1-16, TimestampsAndWatermarksOperator doc drift) falls to `active/successor owner` with `owner_plan: roadmap-stage-23` (Stage 23 = documentation contract, status `todo`). Zero P0/P1 are `residual-risk`.
- **The single `active/successor owner` carries a valid sentinel**: `roadmap-stage-23` points to Stage 23 which is `todo` (not `done`) in the roadmap — a legal successor owner per the Stage 18 validator.
- **Recurrent consistency**: M7-2-P1-6 (Shard 19) is recurrent with M8-2-P1-10 (Shard 18). Both describe the same `StateDescriptor` TypeSerializer-ref root cause; both are `revalidated` (design `state-management-design.md` §6.1 sanctions the escape hatch). The two dispositions are CONSISTENT — same root cause, same resolution, no contradiction.
- **ZERO-test findings (M7-2-P0-5/7/8)**: all three now have live regression coverage (Stage 10 + Stage 17 negative controls) — `revalidated`.

---

## P0 Finding Dispositions (5)

@@DISPOSITION
finding_id: M7-2-P0-2
severity: P0
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/functions/sink/TwoPhaseCommitSinkFunction.java:111-127
disposition: revalidated
revalidation_evidence: TwoPhaseCommitSinkFunction.restoreFromEpoch():152-196 no longer blindly rolls back ALL pending. It now partitions pending into toCommit (eid<=epochId → commit(eid) :169-181, retained on commit-failure for subsuming commit) and toAbort (eid>epochId → abort(eid) :183-193), honoring checkpoint-design §6.4 (durable-but-not-committed MUST be re-committed, not rolled back). New abort(long) (:63-64) defaults to rollback() for back-compat with 13+ legacy subclasses. Javadoc :126-151 documents the original defect and fix. Cross-ref EVID-S9-008/012 (TestExactlyOnceCorrectnessFixes#testRestoreFromEpochRollsbackPendingCommits + #testSubsumingCommitCommitsAllPendingTransactions) and EVID-S16-014 (sink external-effect view). Stage 16 cross-tagged this as FIXED; Stage 19 is the formal owner
successor_note: anchor drifted (111-127 → 152-196 in current HEAD); live location is restoreFromEpoch():152-196
@@END

@@DISPOSITION
finding_id: M7-2-P0-3
severity: P0
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/StreamSinkOperator.java:131-157; nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java:929-974
disposition: revalidated
revalidation_evidence: StreamSinkOperator.restoreState():141-161 no longer invokes restoreFromEpoch with sentinel (-1,null) arguments that cleared the just-restored pendingCommits map. It now ONLY rebuilds the pendingCommits map from the durable snapshot (the "participant-pending-commits" operator-state entry, :153-157). The comment at :143-149 explicitly documents the removed sentinel call. The real epoch restore is owned by GraphModelCheckpointExecutor.restoreOperatorsFromState which dispatches restoreFromEpoch(epochId, taskState) with the REAL epochId at GraphModelCheckpointExecutor.java:1525 (operator branch) and :1535 (udf branch). Cross-ref EVID-S9-013 (manual-trace StreamSinkOperator:143-149)
successor_note: StreamSinkOperator anchor drifted (131-157 → 141-161); GraphModelCheckpointExecutor anchor drifted (929-974 → 1499-1544 restoreOperatorsFromState, with restoreFromEpoch dispatch at :1525/:1535)
@@END

@@DISPOSITION
finding_id: M7-2-P0-5
severity: P0
source_anchor: nop-stream/nop-stream-runtime/src/test/.../checkpoint/ (whole dir)
disposition: revalidated
revalidation_evidence: Serializer Fingerprint / stateFormatVersion recovery-compat formerly had ZERO tests. Live coverage now exists: TestStateSchemaFingerprintEndToEnd#snapshotSerializePersistReloadRestoreGetStateRoundTrip (full snapshot→CheckpointSerDe serialize→LocalFileCheckpointStorage persist→reload→deserialize→MemoryKeyedStateBackend.restoreState→getState round-trip, asserts schemaChecksum per keyed state) plus the checksum-mismatch fail-fast path (getState with mismatched descriptor type throws ERR_STREAM_STATE_SCHEMA_MISMATCH). Cross-ref EVID-S10-013 / EVID-S10-021 and Stage 17 negative control TestStreamModelFingerprintRecoveryCompat#differentFingerprintRecoveryThrows
@@END

@@DISPOSITION
finding_id: M7-2-P0-7
severity: P0
source_anchor: TestSavepointApi.java; TestSavepointEndToEnd.java
disposition: revalidated
revalidation_evidence: Savepoint operatorId-set differential formerly had ZERO tests. Live coverage now exists: GraphModelCheckpointExecutor.validateReverseVertexDifferential rejects restore when a stateful vertex present in the checkpoint is absent from the current graph (reverse, throws ERR_STREAM_SAVEPOINT_VERTEX_DIFFERENTIAL) and also rejects the forward direction (current stateful vertex absent from checkpoint). Guarded by TestSavepointVertexSetDifferential (forward + reverse regression guards, 6 cases). Cross-ref EVID-S10-011. Note: granularity is vertex-level only; operatorId-level differential remains a successor feature (Stage 10 Non-Blocking Follow-up), not a regression of this ZERO-test
@@END

@@DISPOSITION
finding_id: M7-2-P0-8
severity: P0
source_anchor: nop-stream/nop-stream-core/src/test/java/io/nop/stream/core/common/state/shard/TestStateShardRouting.java:219-249
disposition: revalidated
revalidation_evidence: stateShardCount (now maxParallelism) change / rescale manifest formerly had ZERO tests. Live coverage now exists: TestStateShardRescale#snapshotTwoRestoreFour_PreservesAllKeys + snapshotFourRestoreTwo (snapshot-2-restore-4 / snapshot-4-restore-2 key conservation), TestKeyGroupReshard#reshardUpConservesKeysAndRoutesByNewMaxParallelism, TestMaxParallelismReshardMigrationE2E#reshardUp128to256_conservesKeysAndRestoresUnderNewMaxParallelism_memory + oldEqualsNewMaxParallelism_failsFast, TestKeyGroupRescaleDispatchE2E (parallelism rescale dispatch). Cross-ref EVID-S10-022 and Stage 17 negative controls
@@END

## P1 Finding Dispositions (11)

@@DISPOSITION
finding_id: M7-2-P1-1
severity: P1
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/model/StreamComponents.java:35-78
disposition: revalidated
revalidation_evidence: StreamComponents no longer uses Map<String,Object> for its registries. The fields are now strongly typed: Map<String, Transformation<?>> transforms (:45), Map<String, StreamEdge> streams (:46), Map<String, WindowingStrategy> windowingStrategies (:47), List<StreamRequirement> requirements (:48), Set<String> checkpointParticipants (:49). The untyped Map<String,Object> registry is gone. Cross-ref EVID-S6 (Java API/graph audit)
successor_note: anchor drifted (35-78 → 45-49 field declarations in current HEAD)
@@END

@@DISPOSITION
finding_id: M7-2-P1-2
severity: P1
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/model/StreamComponents.java:149-157
disposition: revalidated
revalidation_evidence: StreamComponents.getBean(String id, Class<T> clazz):213-232 no longer ignores clazz. It now validates: throws ERR_STREAM_NULL_ARG if id/clazz empty (:215-220), throws ERR_STREAM_INVALID_STATE if bean not found (:222-223), throws ERR_STREAM_TYPE_MISMATCH if !clazz.isInstance(bean) (:225-230, carrying expected/actual type params), and returns clazz.cast(bean) (:231). Lookup is via lookupAcrossRegistries(id) (:234-240, transforms → streams → windowingStrategies) — no longer hardcoded to windowingStrategies only. The original defect (ignores clazz, hardcoded windowingStrategies lookup) is resolved
successor_note: anchor drifted (149-157 → 213-232 in current HEAD)
@@END

@@DISPOSITION
finding_id: M7-2-P1-3
severity: P1
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/StreamSinkOperator.java:56-95,106-127
disposition: revalidated
revalidation_evidence: The unreachable TwoPhaseCommitSinkFunction-specific dead branches are removed. StreamSinkOperator now dispatches polymorphically: processBarrier (:68-109) gates on `userFunction instanceof CheckpointParticipant` (:77, the live snapshot path for TPCSF sinks) with failure routing (:100-107); notifyCheckpointComplete (:119-127) and notifyCheckpointAborted (:129-137) gate on `instanceof CheckpointParticipant` (skip direct commit/abort — owned by CheckpointCoordinator) else `instanceof CheckpointListener` (legacy direct callback). No `instanceof TwoPhaseCommitSinkFunction` branches remain in processBarrier/notifyCheckpoint paths (the only TPCSF reference is restoreState:150, which is live and necessary). Both branches are reachable depending on sink type — normal polymorphic dispatch, not dead code
successor_note: anchor drifted (56-95,106-127 → 68-109 processBarrier, 119-137 notify methods in current HEAD)
@@END

@@DISPOSITION
finding_id: M7-2-P1-4
severity: P1
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/StreamOperator.java:130-138; nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java:929-974
disposition: revalidated
revalidation_evidence: The ICheckpointedFunction / recovery contract is NO LONGER silently inactive. The production recovery path is active via three mechanisms: (1) CheckpointedSourceFunction.initializeState IS invoked for sources at StreamSourceOperator.java:343; (2) operator state is restored via StreamOperator.restoreState(OperatorSnapshotResult) (e.g. StreamSinkOperator.restoreState:141-161); (3) CheckpointParticipant.restoreFromEpoch(epochId, taskState) is dispatched by GraphModelCheckpointExecutor.restoreOperatorsFromState at :1525 (operator) and :1535 (udf) with the real epochId. The data-loss defect (recovery contract silently inactive) is fixed. The generic StreamOperator.initializeState(TaskStateSnapshot) default hook (StreamOperator.java:135-137) remains a default no-op for non-source operators by deliberate design (the production path chose restoreState/restoreFromEpoch); this contract-shape residual is tracked at the capability layer by EVID-S9-014 (7-value residual-risk) but is NOT the data-loss P1 defect. Cross-ref EVID-S9-014
note: Stage 9 adjudicated the capability row as residual-risk ("contract-shape residual, not a data-loss defect; final disposition owned by Stage 19"); the finding-level defect (silently-inactive recovery = data loss) is revalidated here because the recovery contract is demonstrably active on the live path
successor_note: StreamOperator anchor drifted (130-138 → 135-137 in current HEAD); GraphModelCheckpointExecutor anchor drifted (929-974 → 1499-1544)
@@END

@@DISPOSITION
finding_id: M7-2-P1-6
severity: P1
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/StateDescriptor.java:16-57
disposition: revalidated
revalidation_evidence: StateDescriptor.getSerializer() (:62-64) / setSerializer() (:66-68) decoupling of the TypeSerializer ref from the descriptor's own T is now EXPLICITLY SANCTIONED by design. state-management-design.md §6.1 (line 229) documents the TypeSerializer ref + getter/setter as an "可选 escape hatch（显式 opt-in）" and explains the IStreamSerializer sub-interface dispatch path. Code (StateDescriptor.java:22 private TypeSerializer<T> serializer) and design now agree; the contract drift is resolved. CONSISTENT with Shard 18 M8-2-P1-10 (recurrent partner, same root cause, same `revalidated` disposition — no contradiction)
successor_note: anchor expanded (16-57 → 16-85 full class; the serializer field is :22, getSerializer :62-64, setSerializer :66-68 in current HEAD)
@@END

@@DISPOSITION
finding_id: M7-2-P1-7
severity: P1
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/IInternalStateBackend.java:24-52
disposition: revalidated
revalidation_evidence: The unconstrained <ACC> type parameter is removed. getInternalAppendingState(ReducingStateDescriptor) (:39-40) now declares only <N, IN> and returns InternalAppendingState<K,N,IN,IN,IN> — for reduce semantics IN is simultaneously the input, accumulator and output type (no free ACC). The aggregating overload (:57-58) declares <N,IN,ACC,OUT> but ACC is bound by the AggregateFunction carried in AggregatingStateDescriptor<IN,ACC,OUT>, not unconstrained. Javadoc (:26-58) documents the type-parameter derivation. The original defect (unconstrained <ACC> on the ReducingState overload) is resolved
successor_note: anchor drifted (24-52 → 24-70 in current HEAD)
@@END

@@DISPOSITION
finding_id: M7-2-P1-11
severity: P1
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/CheckpointBarrierTracker.java:98-143
disposition: revalidated
revalidation_evidence: CheckpointBarrierTracker.acknowledgeOperator():153-246 no longer silently swallows snapshot errors. At :193 `if (snapshot != null && snapshot.hasError())` captures the error (:194 abortError), records the checkpointId (:195), removes ONLY that epoch (:200), and routes the error to the CheckpointFailureListener abort callback (:235-242 abortCallback.reportFailure). A failed snapshot is NEVER delivered as a successful snapshotToDeliver (the success path at :220-224/:227-230 is only reached when snapshot has no error). The full-arity constructor (:90-100) accepts an explicit abortCallback; the comment at :50-59/:81-89 documents the P1-11 closure. Cross-ref EVID-S9-015 (TestCheckpointBarrierTrackerErrorPropagation#testSnapshotErrorRoutesToAbortCallbackAndDoesNotDeliverSuccess)
successor_note: anchor drifted (98-143 → 153-246 acknowledgeOperator in current HEAD)
@@END

@@DISPOSITION
finding_id: M7-2-P1-16
severity: P1
source_anchor: README.md:90; ai-dev/design/nop-stream/time-model-design.md:174; nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/TimestampsAndWatermarksOperator.java:8
disposition: active/successor owner
owner_plan: roadmap-stage-23
revalidation_evidence: TimestampsAndWatermarksOperator confirmed to live in nop-stream-core/operators (not runtime). Stage 11 (EVID-S11-013/020) confirmed this doc-vs-code drift still-live and assigned documentation convergence to Stage 23. Live revalidation notes PARTIAL correction since Stage 11: time-model-design.md:174 now correctly states "core 模块（nop-stream-core/.../operators/）" and source-anchors.md:215 correctly states "core/operators"; the nop-stream README was rewritten (41 lines) and no longer mis-places the operator. However a formal doc-convergence sweep (every README + design doc + the bundled SessionEventTimeWindows→EventTimeSessionWindows naming drift cited in EVID-S11-020) has not been completed; Stage 23 (documentation contract, roadmap status `todo`) owns the formal convergence and readiness verdict
note: still-live P1 doc-drift; per schema rule P0/P1 still-live must have an owner (active/successor owner), NOT residual-risk. roadmap-stage-23 sentinel is valid (Stage 23 = `todo`, not `done`)
@@END

@@DISPOSITION
finding_id: M7-2-P1-17
severity: P1
source_anchor: docs-for-ai/INDEX.md:212
disposition: revalidated
revalidation_evidence: docs-for-ai/INDEX.md no longer references the non-existent modules nop-stream-checkpoint / nop-stream-flink. INDEX.md:212 now reads "这是一个根 pom.xml 驱动的大型 Maven 多模块仓库" and :219 lists the REAL nop-stream submodules (nop-stream-core, nop-stream-cep, nop-stream-runtime, nop-stream-connector, -connector-batch, -connector-debezium, nop-stream-flow, nop-stream-fraud-example) with no reference to the phantom modules. docs-for-ai/01-repo-map/module-groups.md:23 explicitly states "仓库内不存在 nop-stream-flink / nop-stream-checkpoint 子模块". Grep of INDEX.md for `nop-stream-checkpoint|nop-stream-flink` returns zero hits. The doc drift is corrected
note: no prior evidence row; from-scratch live revalidation (plan Phase 2) confirms no longer live
@@END

@@DISPOSITION
finding_id: M7-2-P1-18
severity: P1
source_anchor: README.md:81-84
disposition: revalidated
revalidation_evidence: The nop-stream README was rewritten (now 41 lines) and no longer makes the drifted core package-path claims. The original anchor (README:81-84) no longer exists. The underlying fact remains true (state/time/functions classes live under io.nop.stream.core.common.state / .common.functions / .common.typeutils — verified by package declarations), but the README no longer contradicts it: the current README describes only module-level responsibilities (nop-stream-core = StreamModel/StreamGraph/JobGraph/执行引擎/算子/状态管理/Checkpoint 类型定义) without asserting java package paths. The misleading package-path documentation that constituted the defect is gone
note: no prior evidence row; from-scratch live revalidation (plan Phase 2) confirms no longer live. README rewrite removed the drifted claims
successor_note: anchor disappeared (README:81-84 — file rewritten to 41 lines); no successor location because the drift content was removed
@@END

@@DISPOSITION
finding_id: M7-2-P1-19
severity: P1
source_anchor: README.md:82,89
disposition: revalidated
revalidation_evidence: The nop-stream README was rewritten (now 41 lines) and now CORRECTLY attributes CheckpointCoordinator and GraphModelCheckpointExecutor to nop-stream-runtime. README:14 states "nop-stream-runtime | 活跃 | 窗口算子、Checkpoint 协调器与存储实现、分布式执行框架" — both CheckpointCoordinator (nop-stream-runtime/.../checkpoint/CheckpointCoordinator.java) and GraphModelCheckpointExecutor (nop-stream-runtime/.../execution/GraphModelCheckpointExecutor.java) do live in nop-stream-runtime, matching the README. The original mis-attribution (README §1.2 :82,:89) no longer exists. The doc drift is corrected
note: no prior evidence row; from-scratch live revalidation (plan Phase 2) confirms no longer live
successor_note: anchor disappeared (README:82,89 — file rewritten to 41 lines); the corrected attribution is at README:14
@@END
