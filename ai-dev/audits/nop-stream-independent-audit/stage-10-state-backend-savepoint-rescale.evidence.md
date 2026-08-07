# Stage 10 — State Backend, Savepoint & Rescale Evidence

> Status: produced by Stage 10 audit (plan `nop-stream-independent-audit/2026-08-08-1835-2-state-backend-savepoint-rescale-audit.md`)
> Domain: manifest a/g (state backend / RocksDB / storage / savepoint / rescale / reshard source surface + test lane)
> Lane policy: only `in-process` lane (single-JVM state backend snapshot/restore / savepoint / rescale dispatch / reshard) or stronger is credited for state-backend & rescale claims; `unit` is component-only. Any capability needing cross-JVM control-plane / HA is `blocked` or `residual-risk` per Stage 5 T2.
> Validator: `node ai-dev/tools/check-nop-stream-audit-manifest.mjs evidence` (parses `@@EVIDENCE` rows from `*.evidence.md` direct children of this dir)
> All source/test anchors in this file were verified against the live repo on 2026-08-08.

## State-Type × Backend Matrix (frozen by this audit — state backend)

This matrix adjudicates every supported state type across both backends. Each row cites the live
source anchor that creates/serializes the state type. The matrix changes neither the 11 evidence-row
fields nor the 7-value disposition vocabulary (frozen by Stage 4 `evidence-schema.md`).

| # | Backend | State type | Verdict | Live anchor (state getter + snapshot/restore) | Evidence row |
| --- | --- | --- | --- | --- | --- |
| ST1 | Memory | ValueState / MapState / ListState / ReducingState / AggregatingState + Internal (Appending/InternalAggregating/InternalList) | **SUPPORTED** | `MemoryKeyedStateBackend.getState/getMapState/...:166-306`; SerDe `MemoryStateSerDe` | EVID-S10-001 |
| ST2 | RocksDB | ValueState / MapState / ListState / ReducingState / AggregatingState + Internal overloads | **SUPPORTED** | `RocksDBKeyedStateBackend.getState/getMapState/...:444-593`; `RocksDBSnapshotSerDe.snapshotState():79-127` + `restoreState():408-473` | EVID-S10-002 |
| ST3 | Operator state (both backends reuse `MemoryOperatorStateBackend`) | 4 redistribution modes: NONE / UNION / BROADCAST / SPLIT_DISTRIBUTE | **SUPPORTED** | `MemoryOperatorStateBackend.restoreState(oldSnapshots,...):51-75` + `RedistributionMode` enum | EVID-S10-003 |
| ST4 | RocksDB | Key-layout version v2 (key-group big-endian sortable prefix) | **SUPPORTED** | `RocksDBKeyEncoder.KEY_LAYOUT_VERSION=2:67`; `encode():91-103` | EVID-S10-004 |

Adjudication rules applied (consistent with Stage 4 schema + Stage 5 supplement):
- A supported state type gets a snapshot → restore → value-assertion evidence row with `disposition: e2e-proved`
  when an in-process test traces the round-trip, or an honest weaker disposition when only a segment is exercised.
- `@Internal` SPI symbols (e.g. `IInternalStateBackend`, `MigratableKeyedState`) are counted anchors per manifest
  domain `internal-spi-markers` (NOT excluded).

---

## Rescale Combination Matrix (frozen by this audit — rescale/reshard)

This matrix adjudicates every supported AND rejected rescale combination. Each row cites the live source
anchor that implements or rejects it. The matrix changes neither the 11 evidence-row fields nor the 7-value
disposition vocabulary (frozen by Stage 4 `evidence-schema.md`).

| # | Combination | Verdict | Lane | Live anchor (implementing / rejecting) | Evidence row |
| --- | --- | --- | --- | --- | --- |
| R1 | Parallelism-only rescale + aligned (full) checkpoint | **SUPPORTED** | in-process | `GraphModelCheckpointExecutor.restoreTaskStatesFromSource:1085` (rescale detection) + `buildRescaledTaskState():1260-1306` + `KeyGroupRangeRestoreFilter.filterKeyedStates():70-83` | EVID-S10-015 |
| R2 | Parallelism-only rescale + incremental checkpoint | **RESIDUAL-RISK (confirmed live defect)** | in-process | `buildRescaledTaskState():1286-1289` keyed-merge reads `dataMap.get("states")`; incremental snapshot stores under `IncrementalSnapshotResult.MARKER_KEY` not `"states"` → `if (!(statesObj instanceof Map)) continue;` **silently drops incremental keyed state** | EVID-S10-019 |
| R3 | Parallelism-only rescale + channel state / unaligned checkpoint | **REJECTED (fail-fast)** | in-process | `GraphModelCheckpointExecutor.assertNoChannelStateOnRescale():1232-1248` throws `ERR_STREAM_CHANNEL_STATE_RESCALE_UNSUPPORTED` | EVID-S10-016 |
| R4 | maxParallelism change at restore time (no offline reshard) | **RESIDUAL-RISK (documented constraint, no runtime guard)** | in-process | `resolveMaxParallelism():1174-1193` reads only the current backend's maxParallelism; no cross-check vs checkpoint's stamped maxParallelism → keys silently misroute if violated | EVID-S10-018 |
| R5 | Same parallelism (1:1 strict lookup, no rescale) | **SUPPORTED** | in-process | `restoreTaskStatesFromSource:1115` (`stateLookup.lookup(taskLocation)` 1:1 strict path) | EVID-S10-020 |
| R6 | maxParallelism reshard (offline tool, Stage 37) | **SUPPORTED** | in-process | `MaxParallelismReshardMigration.migrate():79-88` + `reshardCheckpoint():106-278` + `KeyGroupReshard.redistributeStates():71-132` | EVID-S10-017 |

Adjudication rules applied (consistent with Stage 4 schema + Stage 5 supplement):
- A supported rescale combination gets a source-to-restore evidence row with `disposition: e2e-proved` when an
  in-process test traces the chain end-to-end, or an honest weaker disposition when only a segment is exercised.
- A rejected combination gets `disposition: fail-fast` with a `rejection_proof` that actually asserts the throw
  (no silent allowance of an unsupported config — Rule #24).
- A combination carrying a documented constraint with no runtime guard gets `disposition: residual-risk` (NOT
  `fail-fast` and NOT `e2e-proved`) — Rule #24 (no silent skip / no silent downgrade).
- **R2 was NOT pre-judged SUPPORTED**: live code tracing of `buildRescaledTaskState:1286-1289` confirmed that the
  keyed-merge path drops incremental keyed state (the `"states"` key is absent on incremental snapshots). This is
  a confirmed live defect discovered by this audit, assigned to a successor remediation plan (see EVID-S10-019).

---

## Evidence Rows

### Phase 1 — State-Type × Backend Matrix & Full Snapshot/Restore

@@EVIDENCE
inventory_id: EVID-S10-001
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryKeyedStateBackend.java:166-306
declared_guarantee: Memory keyed state backend supports all 8 state types (ValueState/MapState/ListState/ReducingState/AggregatingState + Internal Appending/InternalAggregating/InternalList); each getState overload lazy-creates the state and registers its type
implementation_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryStateSerDe.java
runtime_wiring: wired
positive_proof: TestMemoryKeyedStateBackendSnapshotRestore
rejection_proof: TestStateSchemaCompatibility
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S10-002
source_anchor: nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/core/common/state/backend/rocksdb/RocksDBKeyedStateBackend.java:444-593
declared_guarantee: RocksDB keyed state backend supports all 8 state types (ValueState/MapState/ListState/ReducingState/AggregatingState + Internal overloads); each getState maps to one RocksDB column family, snapshot/restore byte-compatible with Memory backend
implementation_anchor: nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/core/common/state/backend/rocksdb/RocksDBSnapshotSerDe.java:79-127,408-473
runtime_wiring: wired
positive_proof: TestRocksDBStateTypes#testValueStatePersistsAcrossReopen
rejection_proof: TestRocksDBStateTypes#testSchemaMismatchThrows
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S10-003
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryOperatorStateBackend.java:51-75
declared_guarantee: Operator state backend supports 4 redistribution modes (NONE/UNION/BROADCAST/SPLIT_DISTRIBUTE); restoreState dispatches by mode and redistributes operator state across new parallelism
implementation_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryOperatorStateBackend.java:62-149
runtime_wiring: wired
positive_proof: TestMemoryOperatorStateBackend#testSplitDistributeRoundRobin
rejection_proof: TestMemoryOperatorStateBackend#testNONERestoreSingle
environment_class: in-process
required_lane: in-process
finding_id: M8-2-P1-7
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S10-004
source_anchor: nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/core/common/state/backend/rocksdb/RocksDBKeyEncoder.java:67,91-103
declared_guarantee: Key-layout version 2 — composite key is [keyGroupId:int32 BE][nsLen][nsJson][keyLen][keyJson]; big-endian key-group prefix makes lexicographic order numeric so keys of one group are SST-contiguous (Stage 35 range scan relies on this)
implementation_anchor: nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/core/common/state/backend/rocksdb/RocksDBKeyEncoder.java:91-103
runtime_wiring: wired
positive_proof: TestRocksDBKeyGroupPrefixLayout#newLayoutUsedAtRuntimeAndRoundTripsValueState
rejection_proof: TestRocksDBKeyGroupPrefixLayout#incrementalRestoreRejectsOldLayoutVersion
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

### Phase 2 — Incremental Checkpoint, Key-Layout Fail-Fast & Ref-Count Integrity

@@EVIDENCE
inventory_id: EVID-S10-005
source_anchor: nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/core/common/state/backend/rocksdb/RocksDBKeyedStateBackend.java:733-753
declared_guarantee: Incremental checkpoint lifecycle — snapshotIncremental creates a native RocksDB checkpoint, content-addresses SST files (SHA-256) and embeds an IncrementalSnapshotResult under MARKER_KEY; coordinator persists + registers shared-state refs; restore reconstructs the SST set and range-scans the target key-group range
implementation_anchor: nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/core/common/state/backend/rocksdb/incremental/RocksDBIncrementalSnapshotStrategy.java:60-112
runtime_wiring: wired
positive_proof: TestRocksDBIncrementalRangeRestore#incrementalRangeRestoreKeepsOnlyOwnedKeys
rejection_proof: TestRocksDBIncrementalRangeRestore#incrementalRestoreWithoutSegmentStoreFailsFast
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S10-006
source_anchor: nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/core/common/state/backend/rocksdb/RocksDBKeyedStateBackend.java:771-798
declared_guarantee: Key-layout version fail-fast (M8-2-P1-1) — restoreState detects the incremental marker and calls verifyKeyLayoutVersion(strict=true) BEFORE restoreIncremental scans SST files, so legacy/absent keyLayoutVersion throws ERR_STREAM_STATE_ERROR instead of silently corrupting state
implementation_anchor: nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/core/common/state/backend/rocksdb/RocksDBKeyEncoder.java:164-186
runtime_wiring: wired
positive_proof: TestRocksDBKeyGroupPrefixLayout#incrementalRestoreAcceptsCurrentLayoutVersion
rejection_proof: TestRocksDBIncrementalRestoreFailFast#typedMarkerRejectsLegacyLayoutVersionThroughRestoreState
environment_class: in-process
required_lane: in-process
finding_id: M8-2-P1-1
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S10-007
source_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:581-632
declared_guarantee: Incremental ref-count integrity (M8-2-P1-2) — executeIncrementalPersistAsync rolls back registered shared-state ref-counts (releaseIncrementalSegments) when storage persistence fails after segment registration, so a failed persist does not permanently leak ref-counts
implementation_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:604,619
runtime_wiring: partial
positive_proof: TestCheckpointCoordinatorIncrementalPersistRollback#storeCheckPointFailureRollsBackIncrementalSegments
rejection_proof: TestCheckpointCoordinatorIncrementalPersistRollback#storeEpochManifestFailureRollsBackIncrementalSegments
environment_class: in-process
required_lane: in-process
finding_id: M8-2-P1-2
disposition: residual-risk
@@END

@@EVIDENCE
inventory_id: EVID-S10-008
source_anchor: nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/core/common/state/backend/rocksdb/incremental/RocksDBIncrementalSnapshotStrategy.java:60-112
declared_guarantee: SST content-addressed sharing — doSnapshot reads every SST file and SHA-256 hashes it into a SharedStateHandle so the coordinator-side SharedStateRegistry can deduplicate identical SSTs across checkpoints by content hash
implementation_anchor: nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/core/common/state/backend/rocksdb/incremental/RocksDBIncrementalSnapshotStrategy.java:85-104
runtime_wiring: wired
positive_proof: TestRocksDBIncrementalSnapshotStrategy#deterministicDedupAcrossCheckpointsWithNoChanges
rejection_proof: none
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

### Phase 3 — Schema Migration, Savepoint & TTL

@@EVIDENCE
inventory_id: EVID-S10-009
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/StateSchemaResolver.java:67-94,204-212
declared_guarantee: Schema migration — verifySchemaCompatibility computes a SHA-256 checksum per state; on mismatch it consults findMigration(registry) and applies a registered StateMigrationFunction (applyMigration + replaceDescriptor), else throws ERR_STREAM_STATE_SCHEMA_MISMATCH (both Memory + RocksDB mirror this logic)
implementation_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryKeyedStateBackend.java:321-340
runtime_wiring: wired
positive_proof: TestStateMigrationEndToEnd#rocksdbIntegerToLongMigrationFullRoundTrip
rejection_proof: TestStateMigrationEndToEnd#rocksdbNoMigrationFailsFast
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S10-010
source_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java:311-369,1016-1049
declared_guarantee: Savepoint save/load/restore — triggerSavepoint materializes key-group ownership then storeSavepoint atomically writes the checkpoint + .metadata sidecar; restoreFromSavepointPath loads the savepoint and routes through restoreTaskStatesFromCheckpoint
implementation_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/storage/LocalFileCheckpointStorage.java:389-423,426-454
runtime_wiring: wired
positive_proof: TestSavepointEndToEnd#testGraphModelExecuteWithSavepointRestoresState
rejection_proof: TestSavepointEndToEnd#testRestoreFailsFastOnMissingTaskState
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S10-011
source_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java:1404-1453
declared_guarantee: Savepoint vertex-differential (M7-2-P0-7) — validateReverseVertexDifferential rejects restore when a stateful vertex present in the checkpoint is absent from the current graph (throws ERR_STREAM_SAVEPOINT_VERTEX_DIFFERENTIAL), and also rejects the forward direction (current vertex absent from checkpoint)
implementation_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java:1444-1453
runtime_wiring: wired
positive_proof: TestSavepointVertexSetDifferential#sameVertexSetRestoreSucceeds
rejection_proof: TestSavepointVertexSetDifferential#reverse_deletedVertexInCheckpointRejectsRestore
environment_class: in-process
required_lane: in-process
finding_id: M7-2-P0-7
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S10-012
source_anchor: nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/core/common/state/backend/rocksdb/RocksDBKeyedStateBackend.java:683-712,623-660
declared_guarantee: TTL eviction — three paths: lazy eviction on every read (TtlContext expiry check + delete), snapshot exclusion (expiredForSnapshot skips entries), background sweep (cleanupExpiredEntries runs at start of snapshotState deleting expired keys by single-key or prefix delete)
implementation_anchor: nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/core/common/state/backend/rocksdb/RocksDBSnapshotSerDe.java:66-72
runtime_wiring: wired
positive_proof: TestRocksDBStateTtl#listStateAndSweep
rejection_proof: TestRocksDBStateTtl#snapshotExcludesExpiredEntries
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S10-013
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/checkpoint/SerializerFingerprint.java:32-57
declared_guarantee: Schema fingerprint recovery-compat (M7-2-P0-5) — SerializerFingerprint carries a SHA-256 schemaChecksum + schemaVersion; StateSchemaResolver.fromDescriptor computes it deterministically and fingerprintsCompatible compares checksums so a restored state with a matching fingerprint restores without migration and a mismatch triggers migration/fail-fast
implementation_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/StateSchemaResolver.java:67-94,172-180
runtime_wiring: wired
positive_proof: TestStateSchemaFingerprintEndToEnd#snapshotSerializePersistReloadRestoreGetStateRoundTrip
rejection_proof: TestStateMigrationEndToEnd#memoryNoMigrationFailsFast
environment_class: in-process
required_lane: in-process
finding_id: M7-2-P0-5
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S10-014
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/StateSchemaResolver.java:204-212
declared_guarantee: State migration crash-recovery + accumulator risk disposition — there is no migration-in-progress marker, so a mid-scan crash during applyMigration leaves the checkpoint unusable (restart from previous is required); accumulator migration (Reducing/Aggregating/InternalAppending) operates on an opaque ACC whose correctness depends on the user-supplied migration function — incorrect migration silently corrupts state
implementation_anchor: none
runtime_wiring: partial
positive_proof: none
rejection_proof: none
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: residual-risk
@@END

### Phase 4 — Rescale Combination Matrix, Reshard & Historical Finding Revalidation

@@EVIDENCE
inventory_id: EVID-S10-015
source_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java:1085,1260-1306
declared_guarantee: Parallelism-only rescale + aligned checkpoint — when oldParallelism != newParallelism on a keyed vertex, buildRescaledTaskState unions all old subtasks' keyed snapshots and filters entries by the new subtask's KeyGroupRange via KeyGroupRangeRestoreFilter, so keyed state follows the key-group ownership under unchanged maxParallelism
implementation_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/shard/KeyGroupRangeRestoreFilter.java:70-83
runtime_wiring: wired
positive_proof: TestKeyGroupRescaleDispatchE2E#scaleUp_parallelism_4_to_16
rejection_proof: TestKeyGroupRescaleDispatchE2E#scaleDown_parallelism_16_to_4
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S10-016
source_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java:1232-1248
declared_guarantee: Parallelism-only rescale + channel state/unaligned checkpoint REJECTED — assertNoChannelStateOnRescale throws ERR_STREAM_CHANNEL_STATE_RESCALE_UNSUPPORTED when any old subtask snapshot carries a non-empty ChannelState, because in-flight channel data has no cross-parallelism redistribution metadata; aligned/empty channel state rescales proceed
implementation_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java:1232-1248
runtime_wiring: wired
positive_proof: TestChannelStateRescaleFailFast#rescaleWithEmptyChannelStateSucceeds
rejection_proof: TestChannelStateRescaleE2E#unalignedCheckpointThenRescale_failsFast
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: fail-fast
@@END

@@EVIDENCE
inventory_id: EVID-S10-017
source_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/reshard/MaxParallelismReshardMigration.java:79-88,106-278
declared_guarantee: maxParallelism reshard (Stage 37 offline tool) — migrate reads an old savepoint, re-hashes every keyed entry under the new maxParallelism via KeyGroupReshard.redistributeStates, redistributes entries to new owner subtasks, and writes a new savepoint with key-conservation invariant; validateArgs rejects old==new maxParallelism fail-fast
implementation_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/shard/KeyGroupReshard.java:71-132
runtime_wiring: wired
positive_proof: TestMaxParallelismReshardMigrationE2E#reshardUp128to256_conservesKeysAndRestoresUnderNewMaxParallelism_memory
rejection_proof: TestMaxParallelismReshardMigrationE2E#oldEqualsNewMaxParallelism_failsFast
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S10-018
source_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java:1174-1193
declared_guarantee: maxParallelism change at restore time is a documented constraint requiring offline reshard first; resolveMaxParallelism reads only the current execution plan backend's getMaxParallelism and does NOT cross-check against the checkpoint's stamped maxParallelism, so there is NO runtime fail-fast guard — if a user changes maxParallelism and restores directly, keys route to wrong subtasks silently
implementation_anchor: none
runtime_wiring: partial
positive_proof: none
rejection_proof: none
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: residual-risk
@@END

@@EVIDENCE
inventory_id: EVID-S10-019
source_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java:1286-1289
declared_guarantee: Parallelism-only rescale + incremental checkpoint — CONFIRMED LIVE DEFECT: buildRescaledTaskState keyed-merge reads dataMap.get("states") but an incremental checkpoint stores keyed state under IncrementalSnapshotResult.MARKER_KEY ("__incremental_checkpoint__") NOT "states"; the guard `if (!(statesObj instanceof Map)) continue;` therefore SILENTLY DROPS incremental keyed state during cross-subtask rescale-merge, producing empty keyed state for the new subtask. Contrast: the single-backend partial-range restore (targetKeyGroupRange + restoreRangeInto) DOES handle incremental correctly; only the cross-subtask rescale path drops it. MaxParallelismReshardMigration.reshardCheckpoint:158-163 fails fast on the same shape (no silent drop there). This defect is discovered by Stage 10 and assigned to a successor remediation plan.
implementation_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java:1343-1351
runtime_wiring: partial
positive_proof: none
rejection_proof: none
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: residual-risk
@@END

@@EVIDENCE
inventory_id: EVID-S10-020
source_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java:1115
declared_guarantee: Same-parallelism 1:1 strict lookup — when no rescale is detected (oldParallelism == newParallelism or non-keyed vertex), restoreTaskStatesFromSource takes the stateLookup.lookup(taskLocation) 1:1 strict path with no key-group re-routing, so each new subtask restores the exact old subtask snapshot at the same index
implementation_anchor: nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java:1109-1116
runtime_wiring: wired
positive_proof: TestKeyGroupRescaleDispatchE2E#unchangedParallelismStillWorks_4_to_4
rejection_proof: TestSavepointEndToEnd#testRestoreFailsFastOnMissingTaskState
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S10-021
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/StateSchemaResolver.java:67-94
declared_guarantee: M7-2-P0-5 revalidation — Serializer Fingerprint / stateFormatVersion recovery-compat formerly had ZERO tests; live coverage now exists via TestStateSchemaFingerprintEndToEnd (snapshot→serialize→persist→reload→restore→getState round-trip) plus the checksum-mismatch fail-fast path exercised by TestStateMigrationEndToEnd no-migration controls
implementation_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/checkpoint/SerializerFingerprint.java:32-57
runtime_wiring: wired
positive_proof: TestStateSchemaFingerprintEndToEnd#snapshotSerializePersistReloadRestoreGetStateRoundTrip
rejection_proof: TestStateMigrationEndToEnd#memoryNoMigrationFailsFast
environment_class: in-process
required_lane: in-process
finding_id: M7-2-P0-5
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S10-022
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/shard/KeyGroupReshard.java:71-132
declared_guarantee: M7-2-P0-8 revalidation — stateShardCount (now maxParallelism) change / rescale manifest formerly had ZERO tests; live coverage now exists via TestKeyGroupReshard (redistributeStates + key conservation), TestStateShardRescale (snapshot-2-restore-4 / snapshot-4-restore-2), TestMaxParallelismReshardMigrationE2E (128→256 + 256→128 reshard E2E), and TestKeyGroupRescaleDispatchE2E (parallelism rescale dispatch)
implementation_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/shard/KeyGroupRangeRestoreFilter.java:41-50
runtime_wiring: wired
positive_proof: TestKeyGroupReshard#reshardUpConservesKeysAndRoutesByNewMaxParallelism
rejection_proof: TestStateShardRouting#testConstructor_RejectsInvalidShardCount
environment_class: in-process
required_lane: in-process
finding_id: M7-2-P0-8
disposition: e2e-proved
@@END

---

## Cross-Reference Notes (final disposition owned by Stages 19-22; coverage gaps / confirmed defects flagged here)

- **M8-2-P1-1** (incremental restore bypasses keyLayoutVersion fail-fast): **RESOLVED.** `RocksDBKeyedStateBackend.restoreState():779,791` now calls `verifyKeyLayoutVersion(snapshotData, true)` (strict) on both the typed-marker and Map-marker branches BEFORE `restoreIncremental`. Guarded by `TestRocksDBIncrementalRestoreFailFast` (typed + map marker legacy rejection) and `TestRocksDBKeyGroupPrefixLayout#incrementalRestoreRejectsAbsentLayoutVersion/OldLayoutVersion`. EVID-S10-006. `disposition: e2e-proved`.
- **M8-2-P1-2** (incremental persist registers shared-state refs before storage persistence): **PARTIAL / residual-risk.** `CheckpointCoordinator.executeIncrementalPersistAsync:604,619` now calls `releaseIncrementalSegments` on storeCheckPoint/storeEpochManifest failure (rollback path present and tested by `TestCheckpointCoordinatorIncrementalPersistRollback`). Complete SST-level ref-count integrity under real RocksDB SST materialization (read-path content-hash verify is M8-2-P2-6; per-checkpoint native dir leak is M8-2-P2-5) remains a residual risk owned by the active remediation plan `2026-08-04-2300-2`. EVID-S10-007.
- **M7-2-P0-5** (Serializer Fingerprint recovery-compat ZERO tests): **RESOLVED.** `TestStateSchemaFingerprintEndToEnd#snapshotSerializePersistReloadRestoreGetStateRoundTrip` now exercises the full fingerprint round-trip; checksum-mismatch fail-fast covered by `TestStateMigrationEndToEnd#*NoMigrationFailsFast` (Memory + RocksDB). EVID-S10-013 / EVID-S10-021. `disposition: e2e-proved`.
- **M7-2-P0-7** (savepoint operatorId-set differential ZERO tests): **PARTIAL → e2e-proved at vertex level.** `validateReverseVertexDifferential:1404-1453` now rejects a deleted stateful vertex (reverse) and a new stateful vertex (forward). Guarded by `TestSavepointVertexSetDifferential` (6 cases). Granularity is **vertex-level only**; operatorId-level differential remains a successor feature plan (Non-Blocking Follow-up). EVID-S10-011. `disposition: e2e-proved` (vertex level).
- **M7-2-P0-8** (stateShardCount / maxParallelism change ZERO tests): **RESOLVED.** Stage 35 (parallelism rescale) + Stage 37 (maxParallelism reshard) added live coverage: `TestStateShardRescale`, `TestKeyGroupReshard`, `TestKeyGroupRescaleDispatchE2E`, `TestMaxParallelismReshardMigrationE2E`. EVID-S10-022. `disposition: e2e-proved`.

## Confirmed Live Defect Discovered by This Audit (assigned to successor remediation plan — NOT fixed here)

- **Parallelism-only rescale + incremental checkpoint silently drops keyed state.** `GraphModelCheckpointExecutor.buildRescaledTaskState:1286-1289` merges old subtasks' keyed snapshots via `toStateDataMap(...).get("states")`, but an incremental checkpoint stores keyed state under `IncrementalSnapshotResult.MARKER_KEY` (`"__incremental_checkpoint__"`) — NOT `"states"`. The guard `if (!(statesObj instanceof Map)) continue;` therefore skips the incremental keyed state entirely, yielding an **empty keyed state** for the new subtask on a parallelism rescale. This is a confirmed exactly-once-relevant defect. It does NOT affect (a) same-parallelism incremental restore (1:1 `restoreState` detects the marker → `restoreIncremental`), nor (b) single-backend partial-range incremental restore (`targetKeyGroupRange` + `restoreRangeInto`). The offline `MaxParallelismReshardMigration.reshardCheckpoint:158-163` correctly **fails fast** on the same shape (no silent drop there), confirming the executor's `continue` is the bug. EVID-S10-019. Assigned to a successor remediation plan per roadmap rule (evidence-only scope of Stage 10 — no production code change here). Until fixed, operators must NOT combine an incremental checkpoint with a parallelism change; use a full (aligned) checkpoint before rescaling.

## Coverage Gaps Found (assigned to successor remediation per roadmap rule — NOT confirmed live defects)

- **M8-2-P1-2 complete SST ref-count integrity under real RocksDB materialization** — the rollback path is tested; full SST-level ref-count correctness (including read-path content-hash verify M8-2-P2-6 and per-checkpoint native dir leak M8-2-P2-5) is owned by active plan `2026-08-04-2300-2`. Recorded as `residual-risk` (EVID-S10-007), not deferred.
- **Savepoint operatorId-level differential** — `validateReverseVertexDifferential` is vertex-level only; operatorId-level classification per checkpoint-design §8.6 is a successor feature plan (Non-Blocking Follow-up).
- **`schemaVersion` version-based branching** — currently dormant at `DEFAULT_SCHEMA_VERSION = 1`; a future feature when schema versioning is activated (Non-Blocking Follow-up).

## Non-Goals honored (not silently dropped)

- Barrier alignment / checkpoint lifecycle / recovery semantics = Stage 9 (only `finding_id` cross-ref of M8-2-P1-1/M8-2-P1-2 done here).
- Window/watermark/timer result semantics = Stage 11.
- CEP NFA state recovery = Stage 12.
- Distributed control-plane / data-plane transport = Stages 13/14.
- Connector transactional external effects = Stage 16.
- Fixing the confirmed live defect (EVID-S10-019) = successor remediation plan per roadmap rule.
