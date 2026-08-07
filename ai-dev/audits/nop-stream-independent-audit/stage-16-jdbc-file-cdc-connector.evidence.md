# Stage 16 — JDBC / File / CDC Connector External-Effect Evidence

> Status: produced by Stage 16 audit (plan `nop-stream-independent-audit/2026-08-08-0610-2-jdbc-file-cdc-connector-external-effect-audit.md`)
> Domain: manifest a (public types, incl. `TwoPhaseCommitSinkFunction` base, `StreamRequirementValidator`, `ProcessingGuarantee`) + e (4 connector modules main java: `nop-stream-connector`, `nop-stream-connector-jdbc`, `nop-stream-connector-debezium`, `nop-stream-connector-batch`) + g (test lane); checkpoint coordinator barrier semantics (Stage 9) and data-plane transport (Stage 14) are referenced, NOT re-audited.
> Lane policy: a JDBC/file 2PC sink external-effect (commit / idempotent guard / saveState-first / abort / recover-safe re-commit) is credited only at `in-process` lane or stronger (a real embedded H2 / real NIO file system driving `commit()` end-to-end against `pendingCommits`). The real PostgreSQL sink integration requires the T5 lane which is `blocked` in `environment-qualification.md` (no gated PostgreSQL test; JDBC tests run on embedded H2 in MySQL-compat mode and are honestly classified T1, never passed off as PostgreSQL per Rule S5-1). The real source-DB CDC engine integration requires the T6 lane which is `blocked` (only a mocked offset-config test exists; honestly classified T1, never passed off as real-CDC per Rule S5-1).
> Validator: `node ai-dev/tools/check-nop-stream-audit-manifest.mjs evidence` (parses `@@EVIDENCE` rows from `*.evidence.md` direct children of this dir).
> All source/test anchors in this file were verified against the live repo on 2026-08-08 (line anchors cross-checked by reading each file; test method names confirmed by direct file read and by a green `./mvnw test` run: `TestJdbcTwoPhaseCommitSinkSkeleton`=19, `TestJdbcTwoPhaseCommitSinkDeep`=13, `TestFileTwoPhaseCommitSink`=12, `TestFileSourceCheckpointRestore`=3, `TestDebeziumCdcCheckpoint`=7, `TestDebeziumCdcSourceFunction`=10, all 0 failures / 0 errors; `TestDebeziumCdcSourceCompletion`=1 Skipped — the genuinely `@Disabled` test, recorded below, NOT silently passed). `TestTwoPhaseCommitSinkFunction` (core) covers the M7-2-P0-2 restoreFromEpoch invariant; `TestConnectorConsistencyCapability` (batch) covers the tier-label validation.

## Support / Reject Combination Matrix (frozen by this audit — JDBC / file / CDC connector)

This matrix adjudicates every supported and rejected JDBC/file 2PC sink external-effect and CDC source offset-restore capability. Each row cites the live source anchor that implements or rejects it and the evidence row that freezes the adjudication. The matrix changes neither the 11 evidence-row fields nor the 7-value disposition vocabulary (frozen by Stage 4 `evidence-schema.md`).

### JDBC Two-Phase-Commit Sink External-Effect Matrix (embedded H2, in-process lane)

| # | Capability | Verdict | Lane | Live anchor (implementing) | Evidence row |
| --- | --- | --- | --- | --- | --- |
| J1 | Commit atomicity — data rows + ledger entry in ONE `connection.commit()` (single JDBC txn, atomic) | **SUPPORTED** | in-process | `JdbcTwoPhaseCommitSink.commit():191-266` (independent conn `:213`, `setAutoCommit(false)` `:216`, data `:228-230`, ledger `:233`, `connection.commit()` `:235`, failure `connection.rollback()` `:246-252`, finally close `:258-262`, `pendingCommits.remove` `:265`) | EVID-S16-001 |
| J2 | Idempotent ledger guard — `ledgerExists` skip data write on recover-safe re-commit (no dup) | **SUPPORTED** | in-process | `JdbcTwoPhaseCommitSink.commit():219-225` (guard) + `ledgerExists():340-347` (SQL); ledger DDL `:300-336` | EVID-S16-002 |
| J3 | saveState-first — buffer moved to `pendingCommits[epochId]` BEFORE `super.saveState` (no lag-by-one-epoch) | **SUPPORTED** | in-process | `JdbcTwoPhaseCommitSink.saveState():170-182` (override; batch→pending `:176-177`, clear `:178`, `super.saveState` `:181`) | EVID-S16-003 |
| J4 | abort/rollback — `abort(epochId)` discards pending (data never written); `rollback()` clears buffer; commit failure rolls back | **SUPPORTED** | in-process | `abort():276-280`; `rollback():269-273`; commit-failure `connection.rollback():246-252` | EVID-S16-004 |
| J5 | TWO_PHASE_COMMIT label consistency — label matches real behavior (ledger idempotency truly prevents dups) | **SUPPORTED** | in-process | `getSinkConsistency():283-285` returns `TWO_PHASE_COMMIT`; ledger PK guard backs it (J2) | EVID-S16-005 |

### File Two-Phase-Commit Sink External-Effect Matrix (NIO file system, in-process lane)

| # | Capability | Verdict | Lane | Live anchor (implementing) | Evidence row |
| --- | --- | --- | --- | --- | --- |
| F1 | Commit atomic-rename + manifest — `Files.move(ATOMIC_MOVE)` temp→final + atomic manifest update | **SUPPORTED** | in-process | `FileTwoPhaseCommitSink.commit():150-197` (pending `:163`, manifest `:166`, idempotent guard `:169-172`, ATOMIC_MOVE `:185`, manifest set `:193`, `pendingCommits.remove` `:196`); `updateManifestAtomically():237-256` | EVID-S16-006 |
| F2 | final-exists / manifest-missing edge repair — rename succeeded but manifest write did not → repair manifest, skip rename | **SUPPORTED** | in-process | `FileTwoPhaseCommitSink.commit():174-181` (edge repair) — covered by a dedicated edge-case test | EVID-S16-007 |
| F3 | saveState-first temp file + abort deletes temp — temp written BEFORE `super.saveState`; `abort` deletes temp | **SUPPORTED** | in-process | `saveState():130-142` (temp file `.{epochId}.tmp` + `FilePendingCommit` `:137`); `abort():207-213` (delete temp) | EVID-S16-008 |
| F4 | Atomic manifest update — temp manifest + `ATOMIC_MOVE` + `REPLACE_EXISTING` (deterministic sorted write) | **SUPPORTED** | in-process | `updateManifestAtomically():237-256` (sorted `:241-244`, temp write `:245-253`, atomic move `:254-255`) | EVID-S16-009 |
| F5 | TWO_PHASE_COMMIT label consistency — label matches real behavior (manifest idempotency truly prevents dups) | **SUPPORTED** | in-process | `getSinkConsistency():109-111` returns `TWO_PHASE_COMMIT`; manifest key guard backs it (F1) | EVID-S16-010 |

### CDC Source Offset Restore + Tier-Label + Backend Limitation + Historical Revalidation Matrix

| # | Capability / Finding | Verdict | Lane | Live anchor | Evidence row |
| --- | --- | --- | --- | --- | --- |
| C1 | CDC offset snapshot/restore round-trip — `snapshotState` → `initializeState` (offset restore, resume no-dup/no-loss) | **SUPPORTED** | in-process (mocked) | `DebeziumCdcSourceFunction.snapshotState():212-227` (`CDC_OFFSETS_KEY` `:56`, store null→empty TreeMap `:220-221`, `toSerializable` `:225`); `initializeState():231-258` (null→fresh `:234`, entry null→fresh `:242`, `fromSerializable`→`setOffsets` `:252-257`) | EVID-S16-011 |
| C2 | CDC fail-fast non-Map state — non-Map CDC_OFFSETS entry throws `StreamException` | **PARTIALLY SUPPORTED — residual-risk** | unit | `initializeState():246-250` (throw); **no dedicated rejection test** → manual-trace + gap | EVID-S16-012 |
| C3 | CDC REPLAYABLE label + drain — label matches behavior (offset restore resumes no-dup); `truncateForDrain` stops consuming | **SUPPORTED** | in-process (mocked) | `getSourceConsistency():185-187` (`REPLAYABLE`); `truncateForDrain():190-203` | EVID-S16-013 |
| H1 | M7-2-P0-2 revalidation (sink external-effect view) — `restoreFromEpoch` durable re-commit / non-durable abort (FIXED) | **SUPPORTED (FIXED)** | unit | `TwoPhaseCommitSinkFunction.restoreFromEpoch():153-196` (durable re-commit `:169-181`, non-durable abort `:183-193`); formal disposition owned by Stage 19 | EVID-S16-014 |
| T1 | strict/effectively/at-least-once tier-label validation — STRICT accepts JDBC/file (`TWO_PHASE_COMMIT`) + CDC (`REPLAYABLE`) | **SUPPORTED** | in-process | `StreamRequirementValidator.validateConnectorConsistency():84-114` (source ≥ REPLAYABLE `:94-100`, sink ≥ TWO_PHASE_COMMIT `:102-108`, non-STRICT early-return `:88-90`); `ProcessingGuarantee.java:10-15` | EVID-S16-015 |
| B1 | Real PostgreSQL sink integration | **BLOCKED — unqualified lane** | none (T5 blocked) | `JdbcTwoPhaseCommitSink` binds `IJdbcTemplate`; real PG = T5 `blocked` (JDBC tests use embedded H2 → T1, Rule S5-1) | EVID-S16-016 |
| B2 | Real source-DB CDC engine integration | **BLOCKED — unqualified lane** | none (T6 blocked) | `DebeziumCdcSourceFunction.createMessageSource():120-123`; real engine = T6 `blocked` (only mocked offset test → T1, Rule S5-1) | EVID-S16-016 |
| D1 | `TestDebeziumCdcSourceCompletion` `@Disabled` disposition | **NON-GOAL (recorded, not fixed here)** | none | `@Disabled("Genuinely broken: run() loops until cancel()/truncateForDrain() — no natural completion path")` `:24-26`; run() loop `DebeziumCdcSourceFunction.run():144-148` | recorded here; successor = Stage 17 test-effectiveness |

Adjudication rules applied (consistent with Stage 4 schema + Stage 5 supplement):
- A JDBC/file 2PC sink external-effect gets an entry-to-effect evidence row with `disposition: e2e-proved` when an in-process test traces the path from `saveState`/buffer through `pendingCommits` to `commit` (data+ledger atomic / ATOMIC_MOVE+manifest) and back to `pendingCommits.remove` — wiring actually connected over a real embedded H2 / real NIO file system.
- A capability whose only path is fail-fast but lacks a dedicated rejection test (CDC non-Map state) gets `disposition: residual-risk` with `rejection_proof: manual-trace:...` and a named gap → Stage 17 successor (Anti-Hollow Check / Rule #24 — no silent `e2e-proved`).
- A capability needing real PostgreSQL / real CDC engine whose T5/T6 lane is `blocked` gets `disposition: blocked` naming the unqualified lane (T5/T6 `@@LANE`) + Rule S5-1 — embedded H2 / mocked offset is NEVER cited as PostgreSQL/real-CDC qualification (Anti-Hollow Check (c)).
- M7-2-P0-2 (corpus `checkpoint/state`, shard 19) is revalidated here from the sink-external-effect view (`environment_class: unit`, `required_lane: unit`); its formal disposition is owned by Stage 19 (core/state/window shard) — this row cross-references, does not replace.
- The tier-label validation (`ProcessingGuarantee` + `StreamRequirementValidator.validateConnectorConsistency`) is adjudicated at `in-process` via `TestConnectorConsistencyCapability`: STRICT_EXACTLY_ONCE accepts JDBC/file (`TWO_PHASE_COMMIT`) + CDC (`REPLAYABLE`); EFFECTIVELY_ONCE / AT_LEAST_ONCE tier is non-applicable to these connectors because they all declare the strongest labels (validator early-returns for non-STRICT `:88-90`).
- `TestDebeziumCdcSourceCompletion` is `@Disabled` (genuinely broken run() loop with no natural completion path) — its disabled state + reason are recorded here and cross-referenced to Stage 17; it is NOT silently skipped (Rule #24) and NOT fixed in this plan (Non-Goal).

---

## Evidence Rows

### Phase 1 — JDBC Two-Phase-Commit Sink External-Effect Evidence (in-process)

@@EVIDENCE
inventory_id: EVID-S16-001
source_anchor: nop-stream/nop-stream-connector-jdbc/src/main/java/io/nop/stream/connector/jdbc/JdbcTwoPhaseCommitSink.java:191-266
declared_guarantee: Commit atomicity — commit(checkpointId) opens a NEW independent JDBC connection (openConnection :213), setAutoCommit(false) (:216), checks the idempotent ledger guard, writes the data batch (writeDataRows :228-230) and the ledger entry (writeLedgerEntry :233) in the SAME single transaction, then connection.commit() (:235); on SQLException/RTE it connection.rollback() (:246-252) and finally restores autoCommit + closes the connection (:253-262); on success it removes the pending entry (:265). Data + ledger are atomically written or not at all.
implementation_anchor: nop-stream/nop-stream-connector-jdbc/src/main/java/io/nop/stream/connector/jdbc/JdbcTwoPhaseCommitSink.java:228-235
runtime_wiring: wired
positive_proof: TestJdbcTwoPhaseCommitSinkSkeleton#testFullCycleCommitWritesDataAndLedger
rejection_proof: TestJdbcTwoPhaseCommitSinkDeep#testEachCommitUsesIndependentTransaction
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S16-002
source_anchor: nop-stream/nop-stream-connector-jdbc/src/main/java/io/nop/stream/connector/jdbc/JdbcTwoPhaseCommitSink.java:219-225
declared_guarantee: Idempotent ledger guard — before writing data, commit() checks ledgerExists(connection, checkpointId) (:219-225; SQL helper ledgerExists :340-347 queries the epoch_id PK). If the epoch is already recorded (e.g. after recovery of a durable-but-uncommitted epoch), the data write is skipped and connection.commit() is a no-op — recover-safe re-commit produces NO duplicate data rows and NO duplicate ledger rows (epoch_id PK). This is what makes the TWO_PHASE_COMMIT label genuinely exactly-once rather than at-least-once.
implementation_anchor: nop-stream/nop-stream-connector-jdbc/src/main/java/io/nop/stream/connector/jdbc/JdbcTwoPhaseCommitSink.java:340-347
runtime_wiring: wired
positive_proof: TestJdbcTwoPhaseCommitSinkDeep#testIdempotentCommitNoDuplicates
rejection_proof: TestJdbcTwoPhaseCommitSinkDeep#testIdempotentCommitGuardAcrossMultipleEpochs
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S16-003
source_anchor: nop-stream/nop-stream-connector-jdbc/src/main/java/io/nop/stream/connector/jdbc/JdbcTwoPhaseCommitSink.java:170-182
declared_guarantee: saveState-first / recover — saveState(epochId) override moves the in-memory currentBuffer batch into pendingCommits[epochId] BEFORE delegating to super.saveState (:176-181). This is critical because StreamSinkOperator.processBarrier calls saveState before preCommit; without this override the batch would lag by one epoch and be permanently lost on restore. preCommit(:185-187) is a no-op (saveState already moved the batch). The serialized snapshot therefore carries the epoch-N batch under PENDING_COMMITS_KEY in THIS checkpoint.
implementation_anchor: nop-stream/nop-stream-connector-jdbc/src/main/java/io/nop/stream/connector/jdbc/JdbcTwoPhaseCommitSink.java:176-181
runtime_wiring: wired
positive_proof: TestJdbcTwoPhaseCommitSinkDeep#testSaveStateCapturesEpochNInThisCheckpoint
rejection_proof: TestJdbcTwoPhaseCommitSinkDeep#testSaveStateLagByOneEpochIsAvoided
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S16-004
source_anchor: nop-stream/nop-stream-connector-jdbc/src/main/java/io/nop/stream/connector/jdbc/JdbcTwoPhaseCommitSink.java:276-280
declared_guarantee: abort/rollback — abort(epochId) (:276-280) discards pendingCommits[epochId] without any JDBC cleanup (data was never written: commit writes data+ledger atomically, so an uncommitted epoch leaves no JDBC footprint); rollback() (:269-273) clears the in-memory currentBuffer; and commit() failure runs connection.rollback() (:246-252) to undo the partial JDBC transaction. abort on a non-existent epoch is a safe no-op.
implementation_anchor: nop-stream/nop-stream-connector-jdbc/src/main/java/io/nop/stream/connector/jdbc/JdbcTwoPhaseCommitSink.java:276-280,269-273
runtime_wiring: wired
positive_proof: TestJdbcTwoPhaseCommitSinkDeep#testAbortClearsPendingEntry
rejection_proof: TestJdbcTwoPhaseCommitSinkDeep#testAbortOnNonExistentEpochIsSafe
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S16-005
source_anchor: nop-stream/nop-stream-connector-jdbc/src/main/java/io/nop/stream/connector/jdbc/JdbcTwoPhaseCommitSink.java:283-285
declared_guarantee: TWO_PHASE_COMMIT label consistency — getSinkConsistency() returns SinkConsistencyCapability.TWO_PHASE_COMMIT, and the label is backed by genuine exactly-once behavior rather than an over-claim: the epoch ledger (epoch_id PK, EVID-S16-002) makes commit recover-safe and idempotent, so a re-commit of a durable-but-uncommitted epoch produces no duplicate data rows. StreamRequirementValidator accepts TWO_PHASE_COMMIT for STRICT_EXACTLY_ONCE (EVID-S16-015).
implementation_anchor: nop-stream/nop-stream-connector-jdbc/src/main/java/io/nop/stream/connector/jdbc/JdbcTwoPhaseCommitSink.java:283-285
runtime_wiring: wired
positive_proof: TestJdbcTwoPhaseCommitSinkSkeleton#testGetSinkConsistencyReturnsTwoPhaseCommit
rejection_proof: TestJdbcTwoPhaseCommitSinkDeep#testIdempotentCommitNoDuplicates
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

### Phase 2 — File Two-Phase-Commit Sink External-Effect Evidence (in-process)

@@EVIDENCE
inventory_id: EVID-S16-006
source_anchor: nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/file/FileTwoPhaseCommitSink.java:150-197
declared_guarantee: Commit atomic-rename + manifest — commit(checkpointId) loads the manifest (:166), skips if the idempotent manifest guard already records the epoch (:169-172), then Files.move(tempPath, finalPath, ATOMIC_MOVE) (:185) atomically renames the temp file to the final file, and updateManifestAtomically(:193) atomically writes the manifest; pendingCommits.remove(:196) on success. The data file is made durable by an atomic filesystem rename and the manifest by an atomic temp+ATOMIC_MOVE+REPLACE_EXISTING, so a crash between saveState and commit recovers with no duplicates and no data loss.
implementation_anchor: nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/file/FileTwoPhaseCommitSink.java:184-194
runtime_wiring: wired
positive_proof: TestFileTwoPhaseCommitSink#testCommitAtomicRenamesAndUpdatesManifest
rejection_proof: TestFileTwoPhaseCommitSink#testIdempotentCommitNoDuplicateRename
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S16-007
source_anchor: nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/file/FileTwoPhaseCommitSink.java:174-181
declared_guarantee: final-exists / manifest-missing edge repair — if a crash happened AFTER the atomic rename but BEFORE the manifest write, commit() detects Files.exists(finalPath) (:174), repairs the manifest (sets the property + updateManifestAtomically :177-178), skips the rename, and removes the pending entry (:179-180). The data is already durable; the repair path never throws. A dedicated edge-case test simulates the crash (manual rename, empty manifest) and asserts the manifest is repaired and the final content preserved.
implementation_anchor: nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/file/FileTwoPhaseCommitSink.java:174-181
runtime_wiring: wired
positive_proof: TestFileTwoPhaseCommitSink#testCommitFinalExistsManifestMissingRepairsManifest
rejection_proof: none
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S16-008
source_anchor: nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/file/FileTwoPhaseCommitSink.java:130-142
declared_guarantee: saveState-first temp file + abort deletes temp — saveState(epochId) override writes the buffer to a temp file .{epochId}.tmp (writeLines :136) and records a FilePendingCommit in pendingCommits[epochId] (:137) BEFORE delegating to super.saveState (:141), mirroring the JDBC sink's saveState-first pattern so the batch is captured in THIS checkpoint (no lag-by-one-epoch). abort(epochId) (:207-213) deletes the temp file (deleteIfExistsQuiet :211) and removes the pending entry — safe no-op if already renamed.
implementation_anchor: nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/file/FileTwoPhaseCommitSink.java:207-213
runtime_wiring: wired
positive_proof: TestFileTwoPhaseCommitSink#testSaveStateWritesTempFileAndClearsBuffer
rejection_proof: TestFileTwoPhaseCommitSink#testAbortDeletesTempFile
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S16-009
source_anchor: nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/file/FileTwoPhaseCommitSink.java:237-256
declared_guarantee: Atomic manifest update — updateManifestAtomically(manifest) serializes the manifest to manifest.properties.tmp with deterministic sorted keys (TreeMap sort :241-244, manual sorted write :245-253 to avoid Properties.store non-determinism), then Files.move(tempManifest, finalManifest, ATOMIC_MOVE, REPLACE_EXISTING) (:254-255) atomically replaces the manifest. The manifest update is atomic on the filesystem, so a crash mid-write leaves either the old or the new manifest — never a torn file.
implementation_anchor: nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/file/FileTwoPhaseCommitSink.java:254-255
runtime_wiring: wired
positive_proof: TestFileTwoPhaseCommitSink#testCommitAtomicRenamesAndUpdatesManifest
rejection_proof: TestFileTwoPhaseCommitSink#testCoordinatorFinishCommitDrivesFileCommit
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S16-010
source_anchor: nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/file/FileTwoPhaseCommitSink.java:109-111
declared_guarantee: TWO_PHASE_COMMIT label consistency — getSinkConsistency() returns SinkConsistencyCapability.TWO_PHASE_COMMIT, and the label is backed by genuine exactly-once behavior rather than an over-claim: the per-epoch manifest key (EVID-S16-006) makes commit recover-safe and idempotent, so a re-commit of a durable-but-uncommitted epoch produces no duplicate final file. StreamRequirementValidator accepts TWO_PHASE_COMMIT for STRICT_EXACTLY_ONCE (EVID-S16-015).
implementation_anchor: nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/file/FileTwoPhaseCommitSink.java:109-111
runtime_wiring: wired
positive_proof: TestFileTwoPhaseCommitSink#testGetSinkConsistencyReturnsTwoPhaseCommit
rejection_proof: TestFileTwoPhaseCommitSink#testIdempotentCommitNoDuplicateRename
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

### Phase 3 — CDC Offset Restore, M7-2-P0-2 Revalidation, Tier-Label, Backend Limitation Evidence

@@EVIDENCE
inventory_id: EVID-S16-011
source_anchor: nop-stream/nop-stream-connector-debezium/src/main/java/io/nop/stream/connector/debezium/DebeziumCdcSourceFunction.java:212-258
declared_guarantee: CDC offset snapshot/restore round-trip — snapshotState(checkpointId) (:212-227) serializes the NopStreamOffsetBackingStore offset map into the OperatorSnapshotResult under CDC_OFFSETS_KEY (:56) via toSerializable (:225) (store null → empty TreeMap :220-221); initializeState(state) (:231-258) restores it: state null → fresh store (:234), offset entry null → fresh store (:242), non-Map → fail-fast (:246-250), otherwise fromSerializable → setOffsets (:252-257). On recovery a fresh source function rebuilds the offset store from the checkpoint and the engine resumes from the checkpointed position (no duplicates, no data loss).
implementation_anchor: nop-stream/nop-stream-connector-debezium/src/main/java/io/nop/stream/connector/debezium/DebeziumCdcSourceFunction.java:231-258
runtime_wiring: wired
positive_proof: TestDebeziumCdcCheckpoint#testSnapshotRestoreRoundTrip
rejection_proof: TestDebeziumCdcCheckpoint#testCdcCheckpointKillRecoverNoDuplicates
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S16-012
source_anchor: nop-stream/nop-stream-connector-debezium/src/main/java/io/nop/stream/connector/debezium/DebeziumCdcSourceFunction.java:246-250
declared_guarantee: CDC fail-fast non-Map state — initializeState fails fast: if the CDC_OFFSETS_KEY entry is not a Map it throws StreamException(ERR_STREAM_STATE_ERROR) naming the state key (:246-250), so a corrupted/torn checkpoint state is rejected rather than silently coerced into an empty offset store (which would restart the source from scratch and re-deliver data). GAP: there is no dedicated rejection test that feeds a non-Map entry and asserts the throw — coverage is manual-trace only.
implementation_anchor: nop-stream/nop-stream-connector-debezium/src/main/java/io/nop/stream/connector/debezium/DebeziumCdcSourceFunction.java:246-250
runtime_wiring: partial
positive_proof: manual-trace:nop-stream/nop-stream-connector-debezium/src/main/java/io/nop/stream/connector/debezium/DebeziumCdcSourceFunction.java:246-250
rejection_proof: none
environment_class: unit
required_lane: in-process
finding_id: none
disposition: residual-risk
@@END

@@EVIDENCE
inventory_id: EVID-S16-013
source_anchor: nop-stream/nop-stream-connector-debezium/src/main/java/io/nop/stream/connector/debezium/DebeziumCdcSourceFunction.java:185-203
declared_guarantee: CDC REPLAYABLE label + drain — getSourceConsistency() returns SourceConsistencyCapability.REPLAYABLE (:185-187), and the label is backed by genuine replayable behavior: offset restore (EVID-S16-011) lets the engine resume from the checkpointed position no-dup/no-loss. truncateForDrain() (:190-203) cancels the subscription, stops the source, and counts down the completion latch so the stream can be drained for savepoint/redeploy (DrainableSource SPI). The label does NOT over-claim TRANSACTIONAL_READ.
implementation_anchor: nop-stream/nop-stream-connector-debezium/src/main/java/io/nop/stream/connector/debezium/DebeziumCdcSourceFunction.java:190-203
runtime_wiring: wired
positive_proof: TestDebeziumCdcSourceFunction#testSourceConsistencyIsReplayable
rejection_proof: TestDebeziumCdcSourceFunction#testTruncateForDrainStopsSource
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S16-014
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/functions/sink/TwoPhaseCommitSinkFunction.java:153-196
declared_guarantee: M7-2-P0-2 revalidation (sink external-effect view) — restoreFromEpoch(epochId, state) (:153-196) no longer blindly rolls back ALL pending (the prior P0 defect). It separates durable-but-not-committed pending (epochId <= N → commit(eid) :169-181, on commit-failure retain pending for subsuming commit) from non-durable in-flight (epochId > N → abort(eid) :183-193). This honors §6.4: durable-but-not-committed sink transactions MUST be re-committed, not rolled back, so exactly-once survives a crash in the saveState→commit window. The new abort(long) (:63-64) defaults to rollback() for back-compat with 13+ legacy subclasses. declared_guarantee context: sink-external-effect revalidation (formal disposition owned by Stage 19, shard 19 core/state/window).
implementation_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/functions/sink/TwoPhaseCommitSinkFunction.java:169-193
runtime_wiring: wired
positive_proof: TestTwoPhaseCommitSinkFunction#testRestoreFromEpoch_durablePendingIsCommittedNotAborted
rejection_proof: TestJdbcTwoPhaseCommitSinkDeep#testRestoreFromEpochMixedDurableAndNonDurable
environment_class: unit
required_lane: unit
finding_id: M7-2-P0-2
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S16-015
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/model/StreamRequirementValidator.java:84-114
declared_guarantee: strict/effectively/at-least-once tier-label validation — validateConnectorConsistency (ProcessingGuarantee.java:10-15 enum) only enforces connector consistency when guarantee == STRICT_EXACTLY_ONCE (:88-90 early-return otherwise): every source must be >= REPLAYABLE (:94-100) and every sink must be >= TWO_PHASE_COMMIT (:102-108), else StreamException (:110-113). Adjudication: under STRICT_EXACTLY_ONCE the JDBC/file sink (TWO_PHASE_COMMIT) + CDC source (REPLAYABLE) combination is correctly ACCEPTED; under EFFECTIVELY_ONCE / AT_LEAST_ONCE / BEST_EFFORT the validator early-returns and these connectors (which all declare the strongest labels) incur no additional external-effect difference — the tier gate is non-applicable to them.
implementation_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/model/StreamRequirementValidator.java:94-108
runtime_wiring: wired
positive_proof: TestConnectorConsistencyCapability#validateConnectorConsistency_debeziumPlusTwoPhaseCommit_passes
rejection_proof: TestConnectorConsistencyCapability#validateConnectorConsistency_skipsWhenNotStrictExactlyOnce
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S16-016
source_anchor: nop-stream/nop-stream-connector-jdbc/src/main/java/io/nop/stream/connector/jdbc/JdbcTwoPhaseCommitSink.java:213
declared_guarantee: External-system real integration blocked — (1) JdbcTwoPhaseCommitSink binds IJdbcTemplate and the real PostgreSQL sink integration requires a gated PostgreSQL test (T5 lane); no such gated test exists in the repo and the JDBC 2PC tests run on embedded H2 in MySQL-compat mode (T1) — per Rule S5-1 the embedded H2 test is NEVER cited as PostgreSQL qualification. (2) DebeziumCdcSourceFunction.createMessageSource (DebeziumCdcSourceFunction.java:120-123) wraps a real Debezium engine; the real source-DB CDC engine integration requires T6, but only a mocked offset-config test exists (T1) — per Rule S5-1 the mocked test is NEVER cited as real-CDC qualification. Both lanes are blocked in environment-qualification.md and block the Stage 23 ready verdict until provisioned.
implementation_anchor: none
runtime_wiring: unwired
positive_proof: none
rejection_proof: none
environment_class: none
required_lane: in-process
finding_id: none
disposition: blocked
@@END
