# nop-stream Independent Audit — Finding Corpus (Frozen)

> Status: frozen
> Frozen at: HEAD 2026-08-07
> Owner: nop-stream-independent-audit mission (Stage 4)
> Validator: `ai-dev/tools/check-nop-stream-audit-manifest.mjs corpus`
> Sources: 4 audit reports under `ai-dev/audits/nop-stream-production/` + deferred-P2 cross-references from 3 active remediation plans under `ai-dev/plans/nop-stream-production/`.

## ID Scheme

- `M8-2-*` — `2026-08-02-2107-multi-audit-nop-stream-production.md` (the consolidated matrix; canonical for its pair)
- `O8-2-AR-*` — `2026-08-02-2107-open-audit-nop-stream-production.md` (open rollup; only entries NOT already in the 08-02 multi-audit)
- `M7-2-*` — `2026-07-25-1948-multi-audit-nop-stream-production.md` (canonical for its pair)
- `O7-2-AR-*` — `2026-07-25-1948-open-audit-nop-stream-production.md` (open rollup; only entries NOT already in the 07-25 multi-audit)

Severity vocabulary: `P0 | P1 | P2 | AR` (AR = numbered action-request item carrying an `AR-N` code).
Domain vocabulary: `coordinator/runtime | checkpoint/state | window | CEP | connector | contract/test`.

## Cross-Shard Attribution Rule (authoritative — same finding is never registered twice)

The 6 domains are partitioned into two clusters, and severity into current vs historical:

- **Cluster "core/state/window"** = `{contract/test, checkpoint/state, window}`
- **Cluster "CEP/connector/runtime"** = `{CEP, connector, coordinator/runtime}`

Shards:

| Shard | Source round | Severity | Domain cluster | Count |
| --- | --- | --- | --- | --- |
| 18 | 08-02 (multi + open) | P0/P1/P2/AR | **ALL** (current production) | 42 |
| 19 | 07-25 (multi + open) | P0/P1 | core/state/window | 16 |
| 20 | 07-25 (multi + open) | P0/P1 | CEP/connector/runtime | 15 |
| 21 | 07-25 (multi + open) | P2 | core/state/window | 19 |
| 22 | 07-25 (multi + open) | P2 | CEP/connector/runtime | 5 |

Rules:
1. Shard 18 = the **current production** baseline; it takes the entire 08-02 pair (multi + its open rollup). Severity is unrestricted because the current round's open rollup contributes AR-coded items of mixed severity.
2. Shards 19–22 = the **historical** 07-25 pair (multi + its open rollup), sharded by severity × cluster. `contract/test` is grouped with the "core/state/window" cluster (the non-distributed side); this is the meaning of "core" in roadmap Stages 19/21.
3. **Intra-pair dedup**: each `open-audit` rollup is deduplicated against its own `multi-audit`; the open rollup contributes ONLY genuinely-new entries (the AR-N items).
4. **Recurrence is preserved**: a 07-25 finding that reappears in 08-02 keeps BOTH IDs (recurrence tracked), annotated `recurrent: <partner-id>`. These are distinct IDs — no double-registration of the same ID.
5. **No silent drop (Rule #24)**: every distinct finding in the 4 reports is registered. Findings that cannot be assigned to 18–22 are marked `pending-adjudication` — there are NONE in this freeze (all 97 findings resolve cleanly into the 5 shards).

## Deferred-P2 Owner-Plan Cross-Reference

The 3 active production remediation plans defer adjacent P2 items to the roadmap Follow-up Backlog. These are NOT new findings — they are existing shard-18 findings that also carry a deferred-tracking entry. Each annotated `deferred: <plan-basename>`:

| Owner plan | Deferred-P2 finding IDs |
| --- | --- |
| `2026-08-04-2300-1-coordinator-runtime-concurrency-recovery-hardening.md` | M8-2-P2-9, M8-2-P2-10, M8-2-P2-15 |
| `2026-08-04-2300-2-checkpoint-state-backend-cep-correctness.md` | M8-2-P2-1, M8-2-P2-5, M8-2-P2-6, M8-2-P2-7, O8-2-AR-3 |
| `2026-08-04-2300-3-contract-drift-config-test-integrity.md` | M8-2-P2-16, M8-2-P2-17, M8-2-P2-11, M8-2-P2-12, M8-2-P2-13, M8-2-P2-14, M8-2-P2-23 |

## Totals

- **Whole corpus: 97 findings** (38 from 08-02 multi + 4 from 08-02 open + 48 from 07-25 multi + 7 from 07-25 open).
- 08-02 multi-audit severity distribution: **P0=2, P1=13, P2=23, AR=0** — matches the reported P0×2 / P1×13 / P2×23.
- Recurrence pairs: 3 (StateDescriptor serializer; TestCountTrigger; TestProcessingGuarantee).
- 07-25 open AR-1..AR-7 are **verified-FIXED at the 08-02 HEAD** (per the 08-02 open-audit prior-audit re-verification table); they remain registered as historical findings (annotated `status_at_0802: verified-fixed`) so disposition Stages 19–22 can mark them closed without re-discovery.

---

## Shard 18 — Current Production (08-02 multi + 08-02 open) — 42 findings

- Total: 42
- IDs: M8-2-P0-1,M8-2-P0-2,M8-2-P1-1,M8-2-P1-2,M8-2-P1-3,M8-2-P1-4,M8-2-P1-5,M8-2-P1-6,M8-2-P1-7,M8-2-P1-8,M8-2-P1-9,M8-2-P1-10,M8-2-P1-11,M8-2-P1-12,M8-2-P1-13,M8-2-P2-1,M8-2-P2-2,M8-2-P2-3,M8-2-P2-4,M8-2-P2-5,M8-2-P2-6,M8-2-P2-7,M8-2-P2-8,M8-2-P2-9,M8-2-P2-10,M8-2-P2-11,M8-2-P2-12,M8-2-P2-13,M8-2-P2-14,M8-2-P2-15,M8-2-P2-16,M8-2-P2-17,M8-2-P2-18,M8-2-P2-19,M8-2-P2-20,M8-2-P2-21,M8-2-P2-22,M8-2-P2-23,O8-2-AR-1,O8-2-AR-2,O8-2-AR-3,O8-2-AR-4

- ID: M8-2-P0-1 | sev: P0 | domain: coordinator/runtime | report: 2026-08-02-2107-multi-audit-nop-stream-production.md | anchor: nop-stream-core JobCoordinator.java:889-921,958-1024,459-576 | desc: JobCoordinator globalRecovery/rotateFencingEpochAndRestore/assignTasks unsynchronized; concurrent recovery drivers corrupt coordinator state | deferred: -
- ID: M8-2-P0-2 | sev: P0 | domain: coordinator/runtime | report: 2026-08-02-2107-multi-audit-nop-stream-production.md | anchor: nop-stream-runtime TestTaskManagerDaemon.java:11-39 | desc: TestTaskManagerDaemon passes vacuously; TaskManager never started, no tm-task-* threads to assert on | deferred: -
- ID: M8-2-P1-1 | sev: P1 | domain: checkpoint/state | report: 2026-08-02-2107-multi-audit-nop-stream-production.md | anchor: RocksDBKeyedStateBackend.java:772-791 | desc: Incremental checkpoint restore bypasses mandated keyLayoutVersion fail-fast; silent corruption on legacy SST | deferred: -
- ID: M8-2-P1-2 | sev: P1 | domain: checkpoint/state | report: 2026-08-02-2107-multi-audit-nop-stream-production.md | anchor: CheckpointCoordinator.java:581-624 | desc: Incremental persist registers shared-state refs before storage persistence; storage failure permanently leaks ref-counts | deferred: -
- ID: M8-2-P1-3 | sev: P1 | domain: checkpoint/state | report: 2026-08-02-2107-multi-audit-nop-stream-production.md | anchor: JdbcCheckpointStorage.java:96-119,324-348,514-537 | desc: JdbcCheckpointStorage INSERT-then-UPDATE in single txn breaks PostgreSQL aborted-transaction state | deferred: -
- ID: M8-2-P1-4 | sev: P1 | domain: coordinator/runtime | report: 2026-08-02-2107-multi-audit-nop-stream-production.md | anchor: TaskManager.java:417-429 | desc: TaskManager.deployTask leaks one capacity permit on every redeploy of an occupied slot | deferred: -
- ID: M8-2-P1-5 | sev: P1 | domain: coordinator/runtime | report: 2026-08-02-2107-multi-audit-nop-stream-production.md | anchor: InputGate.java:90,99,107,575-633,640-660,692-702 | desc: InputGate mutated by two threads via non-thread-safe collections (LinkedHashMap/HashSets) | deferred: -
- ID: M8-2-P1-6 | sev: P1 | domain: coordinator/runtime | report: 2026-08-02-2107-multi-audit-nop-stream-production.md | anchor: SupervisionLoop.java:463-477,431-440 | desc: SupervisionLoop.waitForTerminal rebuilds while old task thread may still be alive (zombie task) | deferred: -
- ID: M8-2-P1-7 | sev: P1 | domain: contract/test | report: 2026-08-02-2107-multi-audit-nop-stream-production.md | anchor: state-management-design.md:407(§10.4),§11.9; core-design.md:419(§7.3) | desc: Design docs say Operator State not implemented but it IS implemented with full redistribution (contract drift) | deferred: -
- ID: M8-2-P1-8 | sev: P1 | domain: contract/test | report: 2026-08-02-2107-multi-audit-nop-stream-production.md | anchor: IOperatorStateStore.java:10-13 | desc: IOperatorStateStore SPI exposes 1 method while design specifies 3 (getUnionListState/getBroadcastState missing) | deferred: -
- ID: M8-2-P1-9 | sev: P1 | domain: contract/test | report: 2026-08-02-2107-multi-audit-nop-stream-production.md | anchor: KeyedStateStore.java:66-109 | desc: KeyedStateStore SPI exposes 5 methods while design says 2; ListState exposed despite design saying it must not be | deferred: -
- ID: M8-2-P1-10 | sev: P1 | domain: contract/test | report: 2026-08-02-2107-multi-audit-nop-stream-production.md | anchor: StateDescriptor.java:22,62-68 | desc: StateDescriptor carries TypeSerializer ref + getter/setter, contradicting design invariant §6.1 | recurrent: M7-2-P1-6 | deferred: -
- ID: M8-2-P1-11 | sev: P1 | domain: contract/test | report: 2026-08-02-2107-multi-audit-nop-stream-production.md | anchor: nop-stream-runtime/src/main/resources/_vfs/nop/stream/ (no _module) | desc: Missing _module marker; ioc:default beans may be silently skipped by global IoC discovery | deferred: -
- ID: M8-2-P1-12 | sev: P1 | domain: coordinator/runtime | report: 2026-08-02-2107-multi-audit-nop-stream-production.md | anchor: TestTaskExecutorDaemonThreads.java:13-37 | desc: TestTaskExecutorDaemonThreads tests its own inline lambda ThreadFactory, not the production one (tautology) | deferred: -
- ID: M8-2-P1-13 | sev: P1 | domain: contract/test | report: 2026-08-02-2107-multi-audit-nop-stream-production.md | anchor: TestSinkTransformation.java:26-334 | desc: TestSinkTransformation 17 @Test methods all verify constructor-storage round-trip on data-holder (same: TestOneInputTransformation) | deferred: -
- ID: M8-2-P2-1 | sev: P2 | domain: checkpoint/state | report: 2026-08-02-2107-multi-audit-nop-stream-production.md | anchor: RocksDBKeyedStateBackend.java:196-201 | desc: Options native handle leaked in RocksDBKeyedStateBackend.openDB() | deferred: 2026-08-04-2300-2-checkpoint-state-backend-cep-correctness.md
- ID: M8-2-P2-2 | sev: P2 | domain: checkpoint/state | report: 2026-08-02-2107-multi-audit-nop-stream-production.md | anchor: RocksDBKeyedStateBackend.java:836-859 | desc: RocksDBKeyedStateBackend.close() non-robust; exception in any close() skips the rest | deferred: -
- ID: M8-2-P2-3 | sev: P2 | domain: checkpoint/state | report: 2026-08-02-2107-multi-audit-nop-stream-production.md | anchor: MemoryStateSerDe.java:763-772 | desc: MemoryStateSerDe.serializeWithSerializer silently swallows serialization errors, falls back to raw value | deferred: -
- ID: M8-2-P2-4 | sev: P2 | domain: checkpoint/state | report: 2026-08-02-2107-multi-audit-nop-stream-production.md | anchor: CheckpointCoordinator.java:969-986; LocalFileCheckpointStorage.java:149-192 | desc: Checkpoint retention ignores pipelineId; maxRetained applied globally across pipelines | deferred: -
- ID: M8-2-P2-5 | sev: P2 | domain: checkpoint/state | report: 2026-08-02-2107-multi-audit-nop-stream-production.md | anchor: RocksDBIncrementalSnapshotStrategy.java:60-112; RocksDBKeyedStateBackend.java:733-753 | desc: Incremental snapshot leaks per-checkpoint native RocksDB checkpoint dir forever (disk leak) | deferred: 2026-08-04-2300-2-checkpoint-state-backend-cep-correctness.md
- ID: M8-2-P2-6 | sev: P2 | domain: checkpoint/state | report: 2026-08-02-2107-multi-audit-nop-stream-production.md | anchor: RocksDBIncrementalRestore.java:86-115 | desc: RocksDBIncrementalRestore does not verify segment content hash on read; corrupted SST silently proceeds | deferred: 2026-08-04-2300-2-checkpoint-state-backend-cep-correctness.md
- ID: M8-2-P2-7 | sev: P2 | domain: checkpoint/state | report: 2026-08-02-2107-multi-audit-nop-stream-production.md | anchor: JdbcCheckpointStorage.java (no override); CheckpointCoordinator.java:1316-1347,1354-1384 | desc: JdbcCheckpointStorage does not override loadRetainedEpochManifests; restart deletes segments referenced by retained non-latest checkpoints | deferred: 2026-08-04-2300-2-checkpoint-state-backend-cep-correctness.md
- ID: M8-2-P2-8 | sev: P2 | domain: checkpoint/state | report: 2026-08-02-2107-multi-audit-nop-stream-production.md | anchor: RocksDBMapState.java:216-227 | desc: RocksDBMapState.contains() returns false on TTL-expired entry without deleting it (lazy-eviction inconsistency) | deferred: -
- ID: M8-2-P2-9 | sev: P2 | domain: coordinator/runtime | report: 2026-08-02-2107-multi-audit-nop-stream-production.md | anchor: CheckpointCoordinator.java:933-951 | desc: CheckpointCoordinator.setTasksToAcknowledge not synchronized; race with registerTask/unregisterTask | deferred: 2026-08-04-2300-1-coordinator-runtime-concurrency-recovery-hardening.md
- ID: M8-2-P2-10 | sev: P2 | domain: coordinator/runtime | report: 2026-08-02-2107-multi-audit-nop-stream-production.md | anchor: JobCoordinator.java:536-565 | desc: JobCoordinator.assignTasks leaves registry and in-memory maps inconsistent when RPC dispatch throws mid-iteration | deferred: 2026-08-04-2300-1-coordinator-runtime-concurrency-recovery-hardening.md
- ID: M8-2-P2-11 | sev: P2 | domain: checkpoint/state | report: 2026-08-02-2107-multi-audit-nop-stream-production.md | anchor: CheckpointCoordinator.java:1292-1306 | desc: CheckpointCoordinator.validateIncrementalConfig throws bare JDK exceptions + non-English message (two-tier violation) | deferred: 2026-08-04-2300-3-contract-drift-config-test-integrity.md
- ID: M8-2-P2-12 | sev: P2 | domain: connector | report: 2026-08-02-2107-multi-audit-nop-stream-production.md | anchor: LocalSourceCoordinator.java:127,150,267,274; CheckpointCoordinator.java:1208-1211 | desc: LocalSourceCoordinator throws bare IllegalStateException (4 sites) + silent snapshot swallow at caller | deferred: 2026-08-04-2300-3-contract-drift-config-test-integrity.md
- ID: M8-2-P2-13 | sev: P2 | domain: coordinator/runtime | report: 2026-08-02-2107-multi-audit-nop-stream-production.md | anchor: InputGate.java:319-323,333-338 | desc: InputGate.blockConsumption/resumeConsumption throw bare IllegalArgumentException (two-tier violation) | deferred: 2026-08-04-2300-3-contract-drift-config-test-integrity.md
- ID: M8-2-P2-14 | sev: P2 | domain: coordinator/runtime | report: 2026-08-02-2107-multi-audit-nop-stream-production.md | anchor: StreamControlRpcServer.java:120-124 | desc: StreamControlRpcServer.CorrelatingRpcService wraps non-Exception Throwable in bare RuntimeException | deferred: 2026-08-04-2300-3-contract-drift-config-test-integrity.md
- ID: M8-2-P2-15 | sev: P2 | domain: coordinator/runtime | report: 2026-08-02-2107-multi-audit-nop-stream-production.md | anchor: JobCoordinator.java:422-435,383-407 | desc: JobCoordinator.failJob/stop do not cancel in-flight tasks on TaskManagers (zombie emissions after FAILED) | deferred: 2026-08-04-2300-1-coordinator-runtime-concurrency-recovery-hardening.md
- ID: M8-2-P2-16 | sev: P2 | domain: contract/test | report: 2026-08-02-2107-multi-audit-nop-stream-production.md | anchor: IStateBackend.java:23 | desc: IStateBackend Javadoc references non-existent RedisStateBackend; real RocksDBStateBackend not mentioned | deferred: 2026-08-04-2300-3-contract-drift-config-test-integrity.md
- ID: M8-2-P2-17 | sev: P2 | domain: contract/test | report: 2026-08-02-2107-multi-audit-nop-stream-production.md | anchor: README.md:5,7 vs 01-architecture-baseline.md:13,100 | desc: README says "五层执行管线" while architecture says "六阶段"; both include non-existent RuntimeTopology | deferred: 2026-08-04-2300-3-contract-drift-config-test-integrity.md
- ID: M8-2-P2-18 | sev: P2 | domain: checkpoint/state | report: 2026-08-02-2107-multi-audit-nop-stream-production.md | anchor: AbstractUdfStreamOperator.java:111-134 | desc: AbstractUdfStreamOperator.initializeState passes null operatorStateStore when no IStateBackend (silent NPE) | deferred: -
- ID: M8-2-P2-19 | sev: P2 | domain: coordinator/runtime | report: 2026-08-02-2107-multi-audit-nop-stream-production.md | anchor: TaskManager.java:577 | desc: TaskManager.updateFencingToken missing @Override (all sibling SPI methods have it) | deferred: -
- ID: M8-2-P2-20 | sev: P2 | domain: CEP | report: 2026-08-02-2107-multi-audit-nop-stream-production.md | anchor: TestWatermarkStateRobustness.java:10-42 | desc: TestWatermarkStateRobustness class name lies about what it tests (actually Quantifier/DeweyNumber) | deferred: -
- ID: M8-2-P2-21 | sev: P2 | domain: checkpoint/state | report: 2026-08-02-2107-multi-audit-nop-stream-production.md | anchor: TestProcessingGuarantee.java:9-32; TestLocalExecutionBarrierAlignment.java:18-52 | desc: TestProcessingGuarantee + TestLocalExecutionBarrierAlignment duplicate enum-metadata assertions | recurrent: M7-2-P2-13 | deferred: -
- ID: M8-2-P2-22 | sev: P2 | domain: coordinator/runtime | report: 2026-08-02-2107-multi-audit-nop-stream-production.md | anchor: TestFlowControl.java:9-25 | desc: TestFlowControl asserts hardcoded constants from production defaults (50/30/20 magic split) | deferred: -
- ID: M8-2-P2-23 | sev: P2 | domain: contract/test | report: 2026-08-02-2107-multi-audit-nop-stream-production.md | anchor: TestCountTrigger.java:10-14; TestMapStateDescriptor.java:9-21; TestE2EStorageTypeRouting.java:38-51 | desc: TestCountTrigger/TestMapStateDescriptor/TestE2EStorageTypeRouting low-value test nits (3 sub-files) | recurrent: M7-2-P2-9 | deferred: 2026-08-04-2300-3-contract-drift-config-test-integrity.md
- ID: O8-2-AR-1 | sev: AR | domain: CEP | report: 2026-08-02-2107-open-audit-nop-stream-production.md | anchor: SharedBufferAccessor.java:258-303 | desc: SharedBufferAccessor.releaseNode() desynchronizes parallel nodesToExamine/versionsToExamine stacks on null-entry branch; corrupted refcounts / over-release on branching CEP | ar: AR-1 | deferred: -
- ID: O8-2-AR-2 | sev: AR | domain: CEP | report: 2026-08-02-2107-open-audit-nop-stream-production.md | anchor: CepOperator.java:540,600 | desc: CepOperator dangling-partial-match safety net fires only when size()==1; stale entries from branching patterns never reclaimed (amplifier of AR-1) | ar: AR-2 | deferred: -
- ID: O8-2-AR-3 | sev: AR | domain: CEP | report: 2026-08-02-2107-open-audit-nop-stream-production.md | anchor: NFAState.java:28; ComputationState.java:33; EventId.java:27; NodeId.java:26; SharedBufferNode.java:28; SharedBufferEdge.java:28; Lockable.java:35 | desc: CEP state value classes dropped implements Serializable vs Flink originals (latent Java-serialization risk) | ar: AR-3 | deferred: 2026-08-04-2300-2-checkpoint-state-backend-cep-correctness.md
- ID: O8-2-AR-4 | sev: AR | domain: CEP | report: 2026-08-02-2107-open-audit-nop-stream-production.md | anchor: TestGeographicAnomalyPatternFix.java:19-60 | desc: TestGeographicAnomalyPatternFix re-implements city2 IterativeCondition inline instead of exercising production createPattern() (zero bug-catching power) | ar: AR-4 | deferred: -

## Shard 19 — Historical P0/P1 core/state/window (07-25) — 16 findings

- Total: 16
- IDs: M7-2-P0-2,M7-2-P0-3,M7-2-P0-5,M7-2-P0-7,M7-2-P0-8,M7-2-P1-1,M7-2-P1-2,M7-2-P1-3,M7-2-P1-4,M7-2-P1-6,M7-2-P1-7,M7-2-P1-11,M7-2-P1-16,M7-2-P1-17,M7-2-P1-18,M7-2-P1-19

- ID: M7-2-P0-2 | sev: P0 | domain: checkpoint/state | report: 2026-07-25-1948-multi-audit-nop-stream-production.md | anchor: TwoPhaseCommitSinkFunction.java:111-127 | desc: TwoPhaseCommitSinkFunction.restoreFromEpoch blindly rolls back ALL pending transactions (incl durable-not-committed); violates §6.4, loses data
- ID: M7-2-P0-3 | sev: P0 | domain: checkpoint/state | report: 2026-07-25-1948-multi-audit-nop-stream-production.md | anchor: StreamSinkOperator.java:131-157; GraphModelCheckpointExecutor.java:929-974 | desc: StreamSinkOperator.restoreState calls restoreFromEpoch(-1,null) right after restoring pending; real epoch call then no-op (compounds M7-2-P0-2)
- ID: M7-2-P0-5 | sev: P0 | domain: checkpoint/state | report: 2026-07-25-1948-multi-audit-nop-stream-production.md | anchor: nop-stream-runtime/src/test/.../checkpoint/ (whole dir) | desc: Serializer Fingerprint / stateFormatVersion recovery-compatibility has ZERO tests
- ID: M7-2-P0-7 | sev: P0 | domain: checkpoint/state | report: 2026-07-25-1948-multi-audit-nop-stream-production.md | anchor: TestSavepointApi.java; TestSavepointEndToEnd.java | desc: Savepoint load operatorId-set differential scenarios have ZERO tests
- ID: M7-2-P0-8 | sev: P0 | domain: checkpoint/state | report: 2026-07-25-1948-multi-audit-nop-stream-production.md | anchor: TestStateShardRouting.java:219-249 | desc: stateShardCount change / rescale manifest has ZERO tests
- ID: M7-2-P1-1 | sev: P1 | domain: contract/test | report: 2026-07-25-1948-multi-audit-nop-stream-production.md | anchor: StreamComponents.java:35-78 | desc: StreamComponents uses Map<String,Object> for strongly-typed registries mandated by design
- ID: M7-2-P1-2 | sev: P1 | domain: contract/test | report: 2026-07-25-1948-multi-audit-nop-stream-production.md | anchor: StreamComponents.java:149-157 | desc: StreamComponents.getBean(id,clazz) ignores clazz, hardcodes lookup in windowingStrategies
- ID: M7-2-P1-3 | sev: P1 | domain: checkpoint/state | report: 2026-07-25-1948-multi-audit-nop-stream-production.md | anchor: StreamSinkOperator.java:56-95,106-127 | desc: StreamSinkOperator TwoPhaseCommitSinkFunction branches are unreachable dead code (TPCSF always hits CheckpointParticipant)
- ID: M7-2-P1-4 | sev: P1 | domain: checkpoint/state | report: 2026-07-25-1948-multi-audit-nop-stream-production.md | anchor: StreamOperator.java:130-138; GraphModelCheckpointExecutor.java:929-974 | desc: StreamOperator.initializeState(TaskStateSnapshot) never called in production; ICheckpointedFunction recovery contract silently inactive
- ID: M7-2-P1-6 | sev: P1 | domain: contract/test | report: 2026-07-25-1948-multi-audit-nop-stream-production.md | anchor: StateDescriptor.java:16-57 | desc: StateDescriptor.getSerializer() decouples serializer type from the descriptor's own T (fake type safety) | recurrent: M8-2-P1-10
- ID: M7-2-P1-7 | sev: P1 | domain: contract/test | report: 2026-07-25-1948-multi-audit-nop-stream-production.md | anchor: IInternalStateBackend.java:24-52 | desc: IInternalStateBackend.getInternalAppendingState(ReducingStateDescriptor) declares unconstrained <ACC> type parameter
- ID: M7-2-P1-11 | sev: P1 | domain: checkpoint/state | report: 2026-07-25-1948-multi-audit-nop-stream-production.md | anchor: CheckpointBarrierTracker.java:98-143 | desc: CheckpointBarrierTracker.acknowledgeOperator silently swallows snapshot errors; failed checkpoint may be marked complete
- ID: M7-2-P1-16 | sev: P1 | domain: contract/test | report: 2026-07-25-1948-multi-audit-nop-stream-production.md | anchor: README.md:90; time-model-design.md:174; TimestampsAndWatermarksOperator.java:8 | desc: TimestampsAndWatermarksOperator documented under runtime/watermark but actually lives in core/operators
- ID: M7-2-P1-17 | sev: P1 | domain: contract/test | report: 2026-07-25-1948-multi-audit-nop-stream-production.md | anchor: docs-for-ai/INDEX.md:212 | desc: docs-for-ai/INDEX.md:212 references non-existent modules nop-stream-checkpoint and nop-stream-flink
- ID: M7-2-P1-18 | sev: P1 | domain: contract/test | report: 2026-07-25-1948-multi-audit-nop-stream-production.md | anchor: README.md:81-84 | desc: core package paths drift; state/time/functions actually live under common/
- ID: M7-2-P1-19 | sev: P1 | domain: contract/test | report: 2026-07-25-1948-multi-audit-nop-stream-production.md | anchor: README.md:82,89 | desc: CheckpointCoordinator and GraphModelCheckpointExecutor mis-attributed in README §1.2

## Shard 20 — Historical P0/P1 CEP/connector/runtime (07-25) — 15 findings

- Total: 15
- IDs: M7-2-P0-1,M7-2-P0-4,M7-2-P0-6,M7-2-P1-5,M7-2-P1-8,M7-2-P1-9,M7-2-P1-10,M7-2-P1-12,M7-2-P1-13,M7-2-P1-14,M7-2-P1-15,O7-2-AR-1,O7-2-AR-2,O7-2-AR-3,O7-2-AR-4

- ID: M7-2-P0-1 | sev: P0 | domain: CEP | report: 2026-07-25-1948-multi-audit-nop-stream-production.md | anchor: SingleOutputStreamOperator.java:33; SingleOutputStreamOperatorImpl.java:46-50; PatternStreamBuilder.java:168 | desc: SingleOutputStreamOperator.forceNonParallel() always throws; CEP.pattern() non-keyed path crashes at runtime
- ID: M7-2-P0-4 | sev: P0 | domain: CEP | report: 2026-07-25-1948-multi-audit-nop-stream-production.md | anchor: TestCepOperatorDanglingCleanup.java:81-99 | desc: TestCepOperatorDanglingCleanup computes partialMatchesEmpty but never asserts; dangling cleanup effectively untested
- ID: M7-2-P0-6 | sev: P0 | domain: coordinator/runtime | report: 2026-07-25-1948-multi-audit-nop-stream-production.md | anchor: nop-stream-runtime/src/test/.../runtime/; TestTaskManager.java:73-78 | desc: Fencing-token rejection of stale attempt output has ZERO tests
- ID: M7-2-P1-5 | sev: P1 | domain: coordinator/runtime | report: 2026-07-25-1948-multi-audit-nop-stream-production.md | anchor: StreamOperator.java:59-78; OperatorChain.java:99-149 | desc: StreamOperator.finish() lifecycle hook never called in production; buffered-data flush contract silently inactive
- ID: M7-2-P1-8 | sev: P1 | domain: coordinator/runtime | report: 2026-07-25-1948-multi-audit-nop-stream-production.md | anchor: InputGate.java:262-278 | desc: InputGate.readSingleChannel swallows InterruptedException and throws; breaks mailbox cooperative-cancel contract
- ID: M7-2-P1-9 | sev: P1 | domain: connector | report: 2026-07-25-1948-multi-audit-nop-stream-production.md | anchor: MessageSourceFunction.java:122-145 | desc: MessageSourceFunction silently swallows collect exceptions; source misreports normal completion (data loss)
- ID: M7-2-P1-10 | sev: P1 | domain: coordinator/runtime | report: 2026-07-25-1948-multi-audit-nop-stream-production.md | anchor: ResultPartition.java:178-193 | desc: ResultPartition.close() discards un-consumed records when queue is full; data loss on bounded-source EOS
- ID: M7-2-P1-12 | sev: P1 | domain: coordinator/runtime | report: 2026-07-25-1948-multi-audit-nop-stream-production.md | anchor: TestIndexedCombinedWatermarkStatus.java:14-22 | desc: Watermark multi-input combine has only unit tests, no e2e (self-exempts via Anti-Hollow exemption)
- ID: M7-2-P1-13 | sev: P1 | domain: CEP | report: 2026-07-25-1948-multi-audit-nop-stream-production.md | anchor: TestCepOperatorStateBackendWiring.java:139-166 | desc: TestCepOperatorStateBackendWiring couples to internal accessors getKeyedStateBackend()/getNFAStateForTesting() (P-4)
- ID: M7-2-P1-14 | sev: P1 | domain: CEP | report: 2026-07-25-1948-multi-audit-nop-stream-production.md | anchor: TestAfterMatchSkipStrategies.java:1-75 | desc: TestAfterMatchSkipStrategies is 100% metadata assertions (P-2); file name implies it is the strategy's main test
- ID: M7-2-P1-15 | sev: P1 | domain: connector | report: 2026-07-25-1948-multi-audit-nop-stream-production.md | anchor: TestBatchConsumerSinkFunction.java:22-103 | desc: TestBatchConsumerSinkFunction covers only happy paths (P-3); no boundary/concurrency tests
- ID: O7-2-AR-1 | sev: AR | domain: coordinator/runtime | report: 2026-07-25-1948-open-audit-nop-stream-production.md | anchor: OperatorChain.java:206-235 | desc: OperatorChain.shallowCopyOperator() silently shares mutable operator instance for unhandled types; parallelism>1 state corruption | ar: AR-1 | status_at_0802: verified-fixed
- ID: O7-2-AR-2 | sev: AR | domain: connector | report: 2026-07-25-1948-open-audit-nop-stream-production.md | anchor: StreamConnectors.java:10-11; nop-stream-connector/pom.xml:22-24,33-36 | desc: StreamConnectors + connector classes hard-reference optional deps; NoClassDefFoundError at class-load time | ar: AR-2 | status_at_0802: verified-fixed
- ID: O7-2-AR-3 | sev: AR | domain: coordinator/runtime | report: 2026-07-25-1948-open-audit-nop-stream-production.md | anchor: PartitionedPlanGenerator.java:83-99; GraphExecutionPlan.java:430-445 | desc: Partitioner-to-policy inference uses fragile class-name string matching in TWO locations; silent misrouting for custom partitioners | ar: AR-3 | status_at_0802: verified-fixed
- ID: O7-2-AR-4 | sev: AR | domain: coordinator/runtime | report: 2026-07-25-1948-open-audit-nop-stream-production.md | anchor: SimpleStreamOperatorFactory.java:46-72 | desc: SimpleStreamOperatorFactory.createStreamOperator() silently falls back to shared template instance on NotSerializableException | ar: AR-4 | status_at_0802: verified-fixed

## Shard 21 — Historical P2 core/state/window (07-25) — 19 findings

- Total: 19
- IDs: M7-2-P2-1,M7-2-P2-2,M7-2-P2-3,M7-2-P2-4,M7-2-P2-5,M7-2-P2-6,M7-2-P2-7,M7-2-P2-9,M7-2-P2-10,M7-2-P2-11,M7-2-P2-12,M7-2-P2-13,M7-2-P2-15,M7-2-P2-16,M7-2-P2-19,M7-2-P2-20,M7-2-P2-21,O7-2-AR-6,O7-2-AR-7

- ID: M7-2-P2-1 | sev: P2 | domain: contract/test | report: 2026-07-25-1948-multi-audit-nop-stream-production.md | anchor: nop-stream-flow/pom.xml:20-23 | desc: nop-stream-flow/pom.xml depends on nop-stream-cep, contradicting README/architecture (flow->core)
- ID: M7-2-P2-2 | sev: P2 | domain: contract/test | report: 2026-07-25-1948-multi-audit-nop-stream-production.md | anchor: nop-stream/src/main/java/io/nop/stream/flow/model/ | desc: nop-stream/src/main/java/io/nop/stream/flow/model/ is a duplicate source tree (60 files, git-tracked) under pom-parent
- ID: M7-2-P2-3 | sev: P2 | domain: contract/test | report: 2026-07-25-1948-multi-audit-nop-stream-production.md | anchor: StreamOperator.java:28-31; OneInputStreamOperator.java:24-26; Input.java:28-35 | desc: Public operator interface Javadocs reference non-existent types (TwoInputStreamOperator/MultipleInputStreamOperator/AbstractStreamOperatorV2/AbstractInput)
- ID: M7-2-P2-4 | sev: P2 | domain: contract/test | report: 2026-07-25-1948-multi-audit-nop-stream-production.md | anchor: CheckpointedSourceFunction.java:14-19; StreamSourceOperator.java:296-302,321-332 | desc: CheckpointedSourceFunction Javadoc says "API 预留，当前未被使用" but production actively calls its snapshotState/initializeState
- ID: M7-2-P2-5 | sev: P2 | domain: contract/test | report: 2026-07-25-1948-multi-audit-nop-stream-production.md | anchor: DataStreamImpl.java:135-186; KeyedStreamImpl.java:190-197; WindowedStreamImpl.java:184-242 | desc: DataStream API casts UnknownTypeInformation.INSTANCE (<?>) to TypeInformation<R> in 6+ entry points
- ID: M7-2-P2-6 | sev: P2 | domain: window | report: 2026-07-25-1948-multi-audit-nop-stream-production.md | anchor: WindowedStreamImpl.java:184-242; WindowOperatorFactoryImpl.java:121-160 | desc: IWindowOperatorFactory requires Class<ACC>/IN/K but WindowedStreamImpl always passes Object.class (performative type safety)
- ID: M7-2-P2-7 | sev: P2 | domain: checkpoint/state | report: 2026-07-25-1948-multi-audit-nop-stream-production.md | anchor: CheckpointCoordinator.java:579-590 | desc: CheckpointCoordinator.onCompletePersistFailure logs the same failure message twice (ERROR + WARN)
- ID: M7-2-P2-9 | sev: P2 | domain: window | report: 2026-07-25-1948-multi-audit-nop-stream-production.md | anchor: TestCountTrigger.java:1-15 | desc: TestCountTrigger entire file tests only canMerge()==false; no onElement boundary test | recurrent: M8-2-P2-23
- ID: M7-2-P2-10 | sev: P2 | domain: checkpoint/state | report: 2026-07-25-1948-multi-audit-nop-stream-production.md | anchor: TestCheckpointBarrier.java:14-91 | desc: TestCheckpointBarrier pure getter/setter round-trip on a value object
- ID: M7-2-P2-11 | sev: P2 | domain: checkpoint/state | report: 2026-07-25-1948-multi-audit-nop-stream-production.md | anchor: TestTaskStateSnapshot.java; TestOperatorSnapshotResult.java; TestCompletedCheckpoint.java | desc: TestTaskStateSnapshot/TestOperatorSnapshotResult/TestCompletedCheckpoint map put/get round-trips, no serialization fidelity tests
- ID: M7-2-P2-12 | sev: P2 | domain: checkpoint/state | report: 2026-07-25-1948-multi-audit-nop-stream-production.md | anchor: TestCheckpointType.java:17-30 | desc: TestCheckpointType asserts enum member count and getName() constants
- ID: M7-2-P2-13 | sev: P2 | domain: checkpoint/state | report: 2026-07-25-1948-multi-audit-nop-stream-production.md | anchor: TestProcessingGuarantee.java:7-33 | desc: TestProcessingGuarantee constant boolean assertions on enum switch | recurrent: M8-2-P2-21
- ID: M7-2-P2-15 | sev: P2 | domain: checkpoint/state | report: 2026-07-25-1948-multi-audit-nop-stream-production.md | anchor: TestCheckpointIDCounter.java:15-84 | desc: TestCheckpointIDCounter tests AtomicLong semantics, no concurrency test (the only real risk)
- ID: M7-2-P2-16 | sev: P2 | domain: window | report: 2026-07-25-1948-multi-audit-nop-stream-production.md | anchor: TestWindowOperatorBasic.java:23-72 | desc: TestWindowOperatorBasic tests TimeWindow geometry primitives; file name implies WindowOperator coverage it doesn't provide
- ID: M7-2-P2-19 | sev: P2 | domain: contract/test | report: 2026-07-25-1948-multi-audit-nop-stream-production.md | anchor: README.md:75 | desc: StreamExecutionEnvironment documented under datastream but actually at core/environment
- ID: M7-2-P2-20 | sev: P2 | domain: contract/test | report: 2026-07-25-1948-multi-audit-nop-stream-production.md | anchor: README §1.2/§1.4; component-roadmap §2.1/§2.5; cep pom.xml | desc: README says cep depends on nop-xlang but cep pom has nop-core; roadmap §2.1 vs §2.5 internally contradict
- ID: M7-2-P2-21 | sev: P2 | domain: contract/test | report: 2026-07-25-1948-multi-audit-nop-stream-production.md | anchor: README §1.2/§1.4; flow pom.xml | desc: README says flow depends only on core, but flow pom also depends on cep and xdefs
- ID: O7-2-AR-6 | sev: AR | domain: contract/test | report: 2026-07-25-1948-open-audit-nop-stream-production.md | anchor: JobGraphGenerator.java:509-554 | desc: JobGraphGenerator javadoc for determinePartitionType is misplaced (attached to hasNonVirtualOperator) | ar: AR-6 | status_at_0802: left-for-followup
- ID: O7-2-AR-7 | sev: AR | domain: contract/test | report: 2026-07-25-1948-open-audit-nop-stream-production.md | anchor: PartitionPolicy.java | desc: PartitionPolicy.UNION and SINGLETON are dead enum values; no production code ever produces them | ar: AR-7 | status_at_0802: verified-fixed

## Shard 22 — Historical P2 CEP/connector/runtime (07-25) — 5 findings

- Total: 5
- IDs: M7-2-P2-8,M7-2-P2-14,M7-2-P2-17,M7-2-P2-18,O7-2-AR-5

- ID: M7-2-P2-8 | sev: P2 | domain: CEP | report: 2026-07-25-1948-multi-audit-nop-stream-production.md | anchor: Lockable.java:54-79 | desc: Lockable.release throws bare IllegalStateException on ref-count underflow instead of StreamException/NopException
- ID: M7-2-P2-14 | sev: P2 | domain: coordinator/runtime | report: 2026-07-25-1948-multi-audit-nop-stream-production.md | anchor: TestJobTerminationContext.java:7-39 | desc: TestJobTerminationContext factory-method field assignment assertions
- ID: M7-2-P2-17 | sev: P2 | domain: CEP | report: 2026-07-25-1948-multi-audit-nop-stream-production.md | anchor: TestSharedBuffer.java:21-71 | desc: TestSharedBuffer overuses assertNotNull(id) where concrete EventId assertions exist in siblings
- ID: M7-2-P2-18 | sev: P2 | domain: CEP | report: 2026-07-25-1948-multi-audit-nop-stream-production.md | anchor: TestNFAState.java:11-80 | desc: TestNFAState equals/hashCode mirror tests; only testNotEqualWhenMatchesDiffer has real protection
- ID: O7-2-AR-5 | sev: AR | domain: coordinator/runtime | report: 2026-07-25-1948-open-audit-nop-stream-production.md | anchor: ResultPartition.java:178-193 | desc: ResultPartition.close() bufferPool permit double-release race during concurrent consumer reads (distinct permit-accounting angle vs M7-2-P1-10 data loss) | ar: AR-5 | status_at_0802: left-for-followup
