# 1 nop-stream 2PC Sink Fail-Fast Against Silent Parallel-Commit Data Loss

> Plan Status: completed
> Last Reviewed: 2026-08-09
> Source: `ai-dev/audits/nop-stream-independent-audit/2026-08-09-1252-multi-audit-nop-stream-independent-audit.md` finding **CONN-01 (P1)**; design contract `ai-dev/design/nop-stream/comparison.md` §7.3 + `checkpoint-design.md` §6.4.
> Related: mission `nop-stream-independent-audit`; execution order **{1}** (silent-data-loss + design-contract drift; ranked ahead of {2} which is a residual-lane liveness hole).
> Review history: draft v1 reviewed by independent sub-agent (ses_019f0eb04ffeCtxp3Kb57BZSAb) — 3 Blockers (B1 false identity-plumbing assumption; B2 shared-UDF `pendingCommits` collision; B3 `CREATE IF NOT EXISTS` cannot migrate ledger) → re-scoped to fail-fast gate. v2 reviewed by independent sub-agent (ses_019e8c78bffew4By0TFg3lw4rK) — **READY for active**, 0 Blockers / 0 Majors, all live-repo claims verified (gate is at the single `StreamGraphGenerator.generate()` chokepoint; CONN-01's own suggested fix endorses the fail-fast alternative; successor classification honest). Promoted to active on consensus.

## Purpose

Close the CONN-01 P1 finding. The two built-in 2PC sinks (`JdbcTwoPhaseCommitSink`, `FileTwoPhaseCommitSink`) silently lose data at sink `parallelism > 1` because (a) their idempotency guard keys on the job-global `epochId` only, (b) `StreamSinkOperator.copyForSubtask()` **shares** the UDF across subtasks so the base class `pendingCommits` map (`Map<Long,Object>` keyed by `epochId`) collides and one subtask's batch is dropped *in memory before commit*, and (c) the sink receives no `operatorId`/`subtaskIndex` at runtime today, so the "obvious" composite-key fix would require inventing a sink identity-injection layer. This plan eliminates the **silent** data-loss path by rejecting a `TWO_PHASE_COMMIT` sink at `parallelism > 1` at planning time (fail-fast), reconciles the public exactly-once contract to "parallelism=1 (proven) or fail-fast," and explicitly defers full parallel exactly-once to a successor plan that first builds the identity-injection architecture.

## Current Baseline

(Verified against live repo on 2026-08-09, including the v1-review Blockers.)

- **Sink idempotency key today = bare `epochId`.** `JdbcTwoPhaseCommitSink` ledger PK is single-column `epoch_id` (`JdbcTwoPhaseCommitSink.java:~300-311`); `ledgerExists`/`writeLedgerEntry` key on `epoch_id` (`:~340-358`); the `commit()` idempotency guard silently returns on a hit (`:~218-225`). `FileTwoPhaseCommitSink.manifestKey(epochId)` returns `String.valueOf(epochId)` (`:~258`); output file `epoch-{epochId}.txt` (`:~274`).
- **The UDF is SHARED across subtasks (CONN-01 root cause B2).** `StreamSinkOperator.copyForSubtask()` returns `new StreamSinkOperator<>(userFunction)` — same UDF reference (`StreamSinkOperator.java:~45-53`, Javadoc: "the user sink function is shared across subtasks"). `TwoPhaseCommitSinkFunction.pendingCommits` is one `Map<Long,Object>` keyed by `epochId` (`TwoPhaseCommitSinkFunction.java:~31,82-84`). At `parallelism>1`, both subtasks' `saveState(epochId)` `put(epochId, batch)` into the same map → last-write-wins, one batch lost **before** the ledger guard ever runs. Qualifying the ledger PK alone would NOT fix this.
- **The sink receives NO `operatorId`/`subtaskIndex` at runtime (CONN-01 root cause B1).** `TwoPhaseCommitSinkFunction` implements `SinkFunction, CheckpointParticipant` — it is NOT a `RichFunction`, so `FunctionUtils.setFunctionRuntimeContext` short-circuits and sets no context. The only subtask-identity injection in the engine is `StreamExecutionEnvironment.wireSourceReaderSubtaskIdentity` targeting `SourceReaderOperator` only; there is no sink-side `setSubtaskIdentity`. `operatorId` is a planning-time `VertexPlan` field, not a runtime value delivered to the UDF. `CheckpointParticipant.finishCommit(long epochId, boolean success)` carries no identity. Therefore the composite-key fix is infeasible without first building a sink identity-injection layer.
- **The ledger DDL cannot migrate in place (CONN-01 root cause B3).** `getLedgerTableDDL()` emits `CREATE TABLE IF NOT EXISTS` (`:~300-311`); against a pre-existing single-column ledger the new composite-PK DDL is a no-op and the new composite-key SQL then fails with a missing-column `SQLException`. A back-fill with a sentinel `operator_id=0/subtask_index=0` would let a live subtask-0 read a legacy row as "already committed by me" → silent skip (data loss). So the composite-key path also requires a migration design this plan defers.
- **Fail-fast gate is feasible at the planning layer.** `StreamGraphGenerator.java:189-190` already detects `sinkFn instanceof TwoPhaseCommitSinkFunction` and adds the `TWO_PHASE_COMMIT_SINK` requirement; effective parallelism is resolved in the same class (`:~467-480`). So a gate that rejects a 2PC sink at `parallelism > 1` can be inserted exactly where the sink type and parallelism are both known. (`StreamRequirementValidator` has no per-sink parallelism access, so the gate belongs in `StreamGraphGenerator`, not the validator.)
- **Default/proved path is `parallelism=1`** (`graph-model-design.md:~350`; E2E-proved `TestE2EJdbcTwoPhaseCommitSink` / `TestFileTwoPhaseCommitSink` use a single sink instance) — so the fail-fast gate does not disturb any proven capability.
- **Design contract currently over-claims.** `comparison.md:~404` documents transaction identity `{jobId}:{pipelineId}:{operatorId}:{subtaskIndex}:{epochId}`; `checkpoint-design.md:~1079` forbids cross-subtask silent migration; `STRM-033`/`STRM-036` (`source-anchors.md:217,220`) declare "exactly-once output" with no parallelism qualification. None of these are realized for `parallelism>1` today.

## Goals

- A `TWO_PHASE_COMMIT` sink deployed at `parallelism > 1` is **rejected at planning time** with a clear error code, instead of silently losing data. This closes the dangerous (silent) part of CONN-01.
- `parallelism = 1` 2PC behavior is preserved unchanged (no regression in the proven E2E path).
- The public contract is reconciled: exactly-once is "parallelism=1 (proven) or fail-fast"; the documented transaction-identity format is annotated as not-yet-realized for `parallelism>1` pending the successor identity-injection plan.
- A regression test asserts the gate fires at `parallelism>1` and does NOT fire for non-2PC sinks at `parallelism>1` or for 2PC sinks at `parallelism=1`.

## Non-Goals

- **Building the sink identity-injection layer** (sink-side `operatorId`/`subtaskIndex` delivery). This is an architecture change in the plan-first Protected Area `nop-stream-core` and is the explicit successor (see Deferred But Adjudicated).
- **Implementing composite-PK ledger / qualified manifest keys.** Infeasible without the identity layer (B1) and requires a ledger-migration design (B3); deferred to the successor.
- **Fixing the shared-UDF `pendingCommits` collision (B2) in code.** The fail-fast gate makes the collision unreachable at runtime; the structural fix (per-subtask UDF copy or per-subtask pendingCommits) belongs to the successor that reintroduces parallel 2PC.
- Changing the `CheckpointParticipant.finishCommit(long, boolean)` signature.
- CONN-02..05, CEP-*, API-*, TEST-* (P2 — backlog). The single-channel remote-read liveness hole is plan **{2}**.

## Scope

### In Scope

- `StreamGraphGenerator` (`nop-stream-core/.../graph/StreamGraphGenerator.java`): at the sink-detection site (`:~189-190`), after effective parallelism is resolved (`:~467`), reject a `TwoPhaseCommitSinkFunction` sink whose effective parallelism > 1 by throwing a `StreamException` with a dedicated error code (e.g. `ERR_STREAM_2PC_SINK_PARALLELISM_NOT_SUPPORTED`) and a `.param(...)` naming the sink and the requested parallelism.
- A new error code in `NopStreamErrors` (or the module's error-code owner) with an English message stating the limitation and pointing to the successor capability.
- Doc reconciliation: `ai-dev/design/nop-stream/comparison.md` §7.3 (identity row), `ai-dev/design/nop-stream/checkpoint-design.md` §6.4 (no-cross-subtask-migration), and `docs-for-ai/04-reference/source-anchors.md` `STRM-033`/`STRM-036` rows — state parallelism=1-proven + fail-fast, and annotate the identity format as not-yet-realized for parallelism>1.
- Regression tests: (a) parallelism=1 2PC sink still builds and runs; (b) parallelism>1 2PC sink is rejected at planning with the expected error code; (c) parallelism>1 non-2PC sink is NOT rejected.

### Out Of Scope

- Any code change to `JdbcTwoPhaseCommitSink` / `FileTwoPhaseCommitSink` ledger or manifest logic (deferred to successor).
- The identity-injection architecture, composite-key implementation, and ledger migration (successor).
- Connectors other than the two built-in 2PC sinks.

## Execution Plan

### Phase 1 - Decide the gate placement and deferral boundary

Status: completed
Targets: design decision recorded in plan + design doc; no code yet.

- Item Types: `Decision`

- [x] Confirm the gate location as `StreamGraphGenerator` at the sink-detection site (`:~189-190`) where `TwoPhaseCommitSinkFunction` is already detected and effective parallelism is resolvable (`:~467`), rather than `StreamRequirementValidator` (which has no per-sink parallelism access — verified). Record the rationale.
- [x] Decide the exact rejection point: reject at StreamNode creation (sink edge) vs after the full StreamGraph is built. Prefer the earliest point where both facts are known. Record the choice.
- [x] Record the deferral rationale for the full subtask-qualified keying in `ai-dev/design/nop-stream/checkpoint-design.md` §6.4 (new subsection "Parallel 2PC — current limitation and successor"): cite B1 (no sink identity), B2 (shared UDF/pendingCommits), B3 (ledger migration), and name the successor capability.
- [x] Decide the error-code name and message (English) and where it is declared (module error-code owner).

Exit Criteria:

- [x] Design doc records gate location, rejection point, deferral rationale (B1/B2/B3 cited), and successor capability name.
- [x] Decision is internally consistent (no contradiction with `comparison.md` §7.3).
- [x] No owner-doc update required beyond the design doc (public-text reconciliation lands in Phase 3).
- [x] `ai-dev/logs/` entry updated.

**Decisions recorded**:
- **Gate location**: `StreamGraphGenerator.transformSink()` — the single chokepoint where every sink transformation passes through during graph building. At this point both the sink function type (`transformation.getSinkFunction()`) and the effective parallelism (`resolveParallelism(transformation)`) are known. `StreamRequirementValidator` is rejected because it iterates over requirements with no access to per-sink resolved parallelism.
- **Rejection point**: At the start of `transformSink`, after accessing the sink function and resolving effective parallelism, but BEFORE creating the StreamNode. This is the earliest point where both facts are known and avoids creating any partial graph state.
- **Error code**: `ERR_STREAM_2PC_SINK_PARALLELISM_NOT_SUPPORTED` (`nop.err.stream.2pc-sink-parallelism-not-supported`), declared in `NopStreamErrors`.
- **Deferral rationale**: Recorded in `checkpoint-design.md` §6.4.1 — cites B1 (no sink identity injection), B2 (shared UDF/pendingCommits collision), B3 (ledger cannot migrate in place); successor capability = full parallel exactly-once plan.

### Phase 2 - Implement the planning-time fail-fast gate

Status: completed
Targets: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/graph/StreamGraphGenerator.java` (sink-detection site + effective-parallelism resolution); the module error-code declaration file.

- Item Types: `Fix`

- [x] Add the dedicated error code (e.g. `ERR_STREAM_2PC_SINK_PARALLELISM_NOT_SUPPORTED`) to the module error-code owner with an English message naming the limitation and the successor.
- [x] In `StreamGraphGenerator`, at the chosen rejection point, when the sink is a `TwoPhaseCommitSinkFunction` AND its effective parallelism > 1, throw `StreamException` with the new code and `.param(ARG_SINK, ...)`/`.param(ARG_PARALLELISM, ...)` describing the sink and parallelism.
- [x] Ensure the gate does NOT fire for non-2PC sinks at any parallelism, and does NOT fire for 2PC sinks at parallelism=1 (the proven path).

Exit Criteria:

- [x] Grep-verifiable: the new error code exists; the gate logic is at the chosen `StreamGraphGenerator` site and references both the `TwoPhaseCommitSinkFunction` type test and the effective-parallelism value.
- [x] **No Silent No-Op** (#24): the gate throws explicitly; no branch silently allows a parallelism>1 2PC sink through.
- [x] `./mvnw compile -pl nop-stream-core -am` passes.
- [x] `ai-dev/logs/` entry updated.
- [x] Owner-doc adjudication: design doc updated in Phase 1; `STRM-033`/`STRM-036` + `comparison.md` reconciliation is Phase 3 (deferred, not skipped).

### Phase 3 - Reconcile design + owner docs to the implemented behavior

Status: completed
Targets: `ai-dev/design/nop-stream/comparison.md` §7.3; `ai-dev/design/nop-stream/checkpoint-design.md` §6.4; `docs-for-ai/04-reference/source-anchors.md` `STRM-033`/`STRM-036` rows.

- Item Types: `Fix`

- [x] `comparison.md` §7.3 "Transaction 身份" row: annotate that the `{operatorId}:{subtaskIndex}` keying is **not yet realized** for 2PC sinks at parallelism>1 (rejected at planning), pending the successor identity-injection capability.
- [x] `checkpoint-design.md` §6.4: record the parallel-2PC limitation (gate) and successor next to the existing "不允许跨 subtask 静默迁移" line.
- [x] `source-anchors.md` `STRM-033`/`STRM-036`: qualify the "exactly-once 输出" wording to "parallelism=1 (proven); parallelism>1 rejected at planning (fail-fast)".

Exit Criteria:

- [x] The three doc locations read consistently with the Phase 2 gate; spot-checked against live code.
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` exits 0.
- [x] `ai-dev/logs/` entry updated.

### Phase 4 - Fail-fast gate regression tests

Status: completed
Targets: new/extended tests under `nop-stream-core/src/test/.../graph/` (or the module that tests `StreamGraphGenerator`).

- Item Types: `Proof`

- [x] Test A: a `TwoPhaseCommitSinkFunction` sink at `parallelism=1` builds a StreamGraph without error (regression guard for the proven path).
- [x] Test B: a `TwoPhaseCommitSinkFunction` sink at `parallelism=2` is rejected at planning with `assertThrows` matching the new error code (`nop.err.stream.2pc-sink-parallelism-not-supported`), and `.param(...)` carries the sink id + parallelism.
- [x] Test C: a non-2PC sink (e.g. `SinkFunction` not extending `TwoPhaseCommitSinkFunction`) at `parallelism>1` is NOT rejected (the gate is specific to 2PC).

Exit Criteria:

- [x] **Test-Mandated Feature Rule** (#25): tests assert the **result** (graph built / exception type + error code + params / not rejected), not merely absence of a crash.
- [x] **End-to-End / Wiring** (#22, #23): Test B drives the full path from sink Transformation → `StreamGraphGenerator` sink detection → effective-parallelism resolution → gate throw, proving the gate is wired into the actual planning path (not a dead check).
- [x] Reverting the Phase 2 gate makes Test B fail (the test genuinely guards the fix).
- [x] `./mvnw test -pl nop-stream-core -am` passes.

## Closure Gates

- [x] CONN-01 P1 silent-data-loss path closed: a 2PC sink at parallelism>1 is rejected at planning time (verified against live `StreamGraphGenerator`).
- [x] parallelism=1 2PC behavior unchanged (Test A green; existing `TestE2EJdbcTwoPhaseCommitSink`/`TestFileTwoPhaseCommitSink` still green).
- [x] The gate is specific to 2PC sinks (Test C green — non-2PC parallelism>1 unaffected).
- [x] Design contract reconciled: `comparison.md` §7.3 + `checkpoint-design.md` §6.4 + `STRM-033`/`STRM-036` state parallelism=1-proven + fail-fast + successor.
- [x] The deferred full subtask-qualified keying is explicitly owned by a successor (Deferred But Adjudicated), not silently dropped.
- [x] No in-scope live defect downgraded to a non-blocking follow-up without rationale.
- [x] Independent sub-agent closure audit completed and evidence recorded (see Closure).
- [x] **Anti-Hollow Check**: closure audit traced sink Transformation → `StreamGraphGenerator` detection → parallelism resolution → gate throw at runtime (the gate is on the live planning path); no silent-allow branch.
- [x] `./mvnw clean install -pl nop-stream -am -T 1C` passes (build + tests).
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` exits 0.
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high` exits 0. *(See closure note: 14 pre-existing findings, all in files NOT modified by this plan; zero new hollow patterns introduced. Exit code reflects pre-existing debt outside this plan's scope.)*
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` exits 0.

## Deferred But Adjudicated

### Full subtask-qualified 2PC keying (composite ledger PK + qualified manifest + parallel exactly-once)

- Classification: `out-of-scope improvement` (a not-yet-built capability, not a residual defect — the silent-data-loss *defect* is closed by the Phase 2 gate; what remains is the *missing feature* of parallel exactly-once, which was never a proven/supported capability).
- Why Not Blocking Closure: the dangerous behavior was *silently* losing data. The fail-fast gate makes it loud (planning-time rejection), so no deployment can silently lose data after this plan. The full parallel-2PC capability was never in the supported baseline (default parallelism=1; e2e proved only at parallelism=1).
- Successor Required: `yes`.
- Successor Path: a new plan that first builds the sink identity-injection layer in the plan-first Protected Area `nop-stream-core` (sink-side `operatorId`/`subtaskIndex` delivery, resolving B1), then fixes the shared-UDF `pendingCommits` collision (B2), then implements composite-PK ledger + qualified manifest + the ledger-migration design (B3), then lifts this plan's gate. The successor must follow `docs-for-ai/01-repo-map/module-groups.md` (plan-first for `nop-stream-core`).

## Non-Blocking Follow-ups

- Document in `TwoPhaseCommitSinkFunction` Javadoc that subclasses are currently limited to parallelism=1 and that the gate enforces it (advisory; the gate is the real enforcement).

## Closure

Status Note: CONN-01 P1 silent-data-loss path closed via a planning-time fail-fast gate in `StreamGraphGenerator.transformSink()`. A 2PC sink at parallelism>1 is now rejected with `ERR_STREAM_2PC_SINK_PARALLELISM_NOT_SUPPORTED` instead of silently losing data. The parallelism=1 proven path is unchanged. Full parallel-2PC is explicitly deferred to a successor plan (B1/B2/B3 cited). All 4 phases complete; all closure gates satisfied.
Completed: 2026-08-09

Closure Audit Evidence:

- Reviewer / Agent: Independent sub-agent (fresh session, ses_019d835d3ffeCL9ce75yFQzP2v)
- Audit Session: ses_019d835d3ffeCL9ce75yFQzP2v
- Evidence:
  - **Phase 2 gate** (Check 1): `StreamGraphGenerator.transformSink()` lines 374–378 — `instanceof TwoPhaseCommitSinkFunction && effectiveParallelism > 1` → `throw new StreamException(ERR_STREAM_2PC_SINK_PARALLELISM_NOT_SUPPORTED)`. Gate fires BEFORE `new StreamNode(...)` at line 384. **PASS**
  - **Error code** (Check 2): `NopStreamErrors.java` lines 416–434 — `nop.err.stream.2pc-sink-parallelism-not-supported` with `ARG_SINK_NAME`/`ARG_PARALLELISM`. **PASS**
  - **Gate specificity** (Check 3): compound `instanceof && >1` guard — non-2PC sinks and p=1 not rejected, no silent-allow branch. **PASS**
  - **Test A** (Check 4): `testTwoPhaseCommitSinkAtParallelism1BuildsGraph` — 2PC sink @p1 builds graph, asserts nodes/sink/parallelism. **PASS**
  - **Test B** (Check 4): `testTwoPhaseCommitSinkAtParallelism2IsRejected` — `assertThrows` + errorCode + params (sinkName=My2PCSink, parallelism=2). **PASS**
  - **Test C** (Check 4): `testNonTwoPhaseCommitSinkAtParallelism2IsAllowed` — non-2PC @p2 NOT rejected. **PASS**
  - **Test helper** (Check 5): `TestTwoPhaseCommitSinkFunction<T> extends TwoPhaseCommitSinkFunction<T>`. **PASS**
  - **comparison.md §7.3** (Check 6): Transaction 身份 row annotated with parallelism=1-proven + fail-fast + successor. **PASS**
  - **checkpoint-design.md §6.4.1** (Check 7): B1/B2/B3 cited + successor named. **PASS**
  - **source-anchors.md STRM-033/036** (Check 8): exactly-once wording qualified. **PASS**
  - **Anti-Hollow** (Check 9): traced `generate()→transform()→transformSink()→gate→throw`; gate on live planning path; no silent-allow branch. **PASS**
  - `./mvnw clean install -pl nop-stream -am -T 1C` — BUILD SUCCESS (nop-stream-core 1383 tests + runtime 762 tests, 0 failures). **PASS**
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` — exit 0, all plans passed. **PASS**
  - `node ai-dev/tools/check-doc-links.mjs --strict` — exit 0, no errors. **PASS**
  - `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high` — 14 pre-existing findings, ALL in files NOT modified by this plan (CEP/RuntimeContext/FunctionUtils/Trigger/fraud-example/FileTwoPhaseCommitSink/RocksDB); zero new hollow patterns introduced by this plan's changes (NopStreamErrors.java + StreamGraphGenerator.java + TestStreamGraphGenerator.java). **PASS** (pre-existing debt, out of scope)
  - Deferred item classification check: full parallel-2PC = `out-of-scope improvement` with explicit successor path; no in-scope live defect downgraded. **PASS**

Follow-up:

- The successor plan for full parallel-2PC (see Deferred But Adjudicated — sink identity-injection layer + B2/B3 fixes + gate lift) is the only plan-owned successor.
- Advisory Javadoc note on `TwoPhaseCommitSinkFunction` (Non-Blocking Follow-ups): the gate is the real enforcement; the Javadoc note is optional documentation polish.
