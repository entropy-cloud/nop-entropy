# 14 Post-Completion Housekeeping

> Plan Status: active
> Plan Type: maintenance
> Mission: nop-stream-flink-comparison
> Work Item: follow-up backlog
> Last Reviewed: 2026-07-25
> Source: `docs/backlog/nop-stream-flink-comparison-roadmap.md` Follow-up Backlog (P2 items); `docs/audits/nop-stream-flink-comparison/2026-07-24-2227-multi-audit-nop-stream-flink-comparison.md`; `docs/audits/nop-stream-flink-comparison/2026-07-24-2227-open-audit-nop-stream-flink-comparison.md`
> Related: All completed implementation plans (9-12b, watermark, cep-state)

## Purpose

Sweep the Follow-up Backlog items from the completed audit cycle: three small P2 code-quality fixes, owner-doc gap (`source-anchors.md` has zero nop-stream entries), and roadmap status update to mark items 1-2 as superseded (their work was consumed by the now-completed comparison analyses 3-7).

Plans 12a and 13 are already `active` in `ai-dev/plans/nop-stream-flink-comparison/` — this plan does not touch them.

## Current Baseline

- `CheckpointMetricsSnapshot.toString()` at `nop-stream-runtime/../checkpoint/metrics/CheckpointMetricsSnapshot.java:80-89` omits `failureCause` from output
- `OperatorChain.open()` javadoc at `nop-stream-core/../jobgraph/OperatorChain.java:93` says "forward order" but implementation at lines 98-110 iterates in reverse
- `PartitionPolicy` enum at `nop-stream-core/../execution/plan/PartitionPolicy.java:10-18` declares `UNION` and `SINGLETON` never referenced by production code
- `source-anchors.md` (193 lines) has zero matches for "nop-stream" or any major nop-stream class
- Roadmap items 1-2 remain `todo` but their work was consumed by the now-completed comparison analyses 3-7
- Plans 12a and 13 already exist as `active` in `ai-dev/plans/nop-stream-flink-comparison/`; stale `draft` copies exist in `docs/plans/nop-stream-flink-comparison/`
- `WindowOperator` empty else blocks (AR-5): already fixed (confirmed by watermark-fixes plan closure evidence)
- Multi-input deferred items (BarrierAligner wiring, multi-input watermark, BroadcastState) all depend on multi-input operator infrastructure not in codebase — deferred to future architectural plan

## Goals

- Fix 3 P2 code-quality issues (toString, javadoc, dead enum values)
- Add nop-stream anchor entries to `source-anchors.md`
- Update roadmap: mark items 1-2 as `done` (superseded by comparison analyses)
- Clean up stale `docs/plans/` duplicates of plans 12a and 13

## Non-Goals

- Multi-input operator infrastructure (requires architecture-level plan)
- RocksDB state backend
- Plans 12a and 13 execution or review (already `active` in `ai-dev/plans/`)

## Scope

### In Scope

- `CheckpointMetricsSnapshot.toString()`: add `failureCause` to output
- `OperatorChain.open()` javadoc: correct "forward order" to "reverse order"
- `PartitionPolicy`: add `@Deprecated` javadoc to `UNION` and `SINGLETON` (safe path, avoids doc churn — the enum type references in `docs-for-ai/02-core-guides/xdef-and-xdsl.md` are type-level and unaffected)
- `source-anchors.md`: add nop-stream anchor entries for major classes
- `docs/backlog/nop-stream-flink-comparison-roadmap.md`: update items 1-2 to `done`
- Remove stale `docs/plans/nop-stream-flink-comparison/2026-07-25-1600-1-operator-state-basic.md` and `2026-07-25-1600-3-streammodel-rectify.md` (canonical copies are in `ai-dev/plans/`)

### Out Of Scope

- Multi-input infrastructure
- Reviewing or activating plans 12a/13 (already `active`)

## Execution Plan

### Phase 1 — Code-quality fixes

Status: planned
Targets:
- `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/metrics/CheckpointMetricsSnapshot.java`
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/jobgraph/OperatorChain.java`
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/plan/PartitionPolicy.java`

Item Types: `Fix`

- [ ] `Fix` Add `", failureCause='" + failureCause + '\''` before the closing `'}'` in `CheckpointMetricsSnapshot.toString()`
- [ ] `Fix` Change `OperatorChain.open()` javadoc line 93 from "forward order" to "reverse order"
- [ ] `Fix` Add `@Deprecated` javadoc to `UNION` and `SINGLETON` in `PartitionPolicy` enum; recommended text: `@deprecated Reserved for future use — currently not wired to any Partitioner implementation.`

Exit Criteria:

- [ ] `CheckpointMetricsSnapshot.toString()` output includes `failureCause`
- [ ] `OperatorChain.open()` javadoc correctly describes reverse-order iteration
- [ ] `PartitionPolicy.UNION` and `SINGLETON` carry `@Deprecated` annotation with explanatory javadoc
- [ ] **No new test required**: changes affect only toString output, javadoc text, and enum deprecation annotation — no behavioral change to existing code paths. Verify existing tests still compile and pass.
- [ ] `./mvnw compile -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime -am` passes
- [ ] `./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime -am` passes
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` corresponding date entry updated

### Phase 2 — Owner-doc sync + roadmap update + cleanup

Status: planned
Targets:
- `docs-for-ai/04-reference/source-anchors.md`
- `docs/backlog/nop-stream-flink-comparison-roadmap.md`
- `docs/plans/nop-stream-flink-comparison/2026-07-25-1600-1-operator-state-basic.md`
- `docs/plans/nop-stream-flink-comparison/2026-07-25-1600-3-streammodel-rectify.md`

Item Types: `Fix | Follow-up`

- [ ] `Fix` Add nop-stream anchor entries to `source-anchors.md`. Priority classes (max ~15):
  - Pipeline model: `StreamModel`, `StreamComponents`, `StreamModelFingerprint`
  - Graph construction: `StreamGraphGenerator`, `JobGraphGenerator`, `PartitionedPlanGenerator`, `ForwardPartitioner`
  - Window/time: `WindowOperator`, `WindowOperatorBuilder`, `HeapInternalTimerService`
  - Checkpoint/state: `PendingCheckpoint`, `CheckpointCoordinator`, `CheckpointMetrics`, `BarrierAligner`
- [ ] `Fix` Update roadmap items 1-2 from `todo` to `done` in the Work Items section, with note: "Superseded — work consumed by comparison analyses 3-7. No standalone deliverable produced."
- [ ] `Fix` Remove stale duplicate files: `docs/plans/nop-stream-flink-comparison/2026-07-25-1600-1-operator-state-basic.md` and `2026-07-25-1600-3-streammodel-rectify.md` (canonical active copies exist in `ai-dev/plans/nop-stream-flink-comparison/`)
- [ ] Run: `node ai-dev/tools/check-doc-links.mjs --strict` — must exit 0

Exit Criteria:

- [ ] `source-anchors.md` has nop-stream entries for all classes listed above
- [ ] Roadmap items 1-2 are `done` in the Work Items block with supersession note
- [ ] `docs/plans/nop-stream-flink-comparison/` no longer has stale `draft` copies of plans 12a and 13
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` exits 0
- [ ] `ai-dev/logs/` corresponding date entry updated

## Closure Gates

- [ ] All 3 P2 code-quality issues fixed (toString, javadoc, dead enum values)
- [ ] `source-anchors.md` has nop-stream entries
- [ ] Roadmap items 1-2 marked `done`
- [ ] Stale `docs/plans/` duplicates of plans 12a/13 removed
- [ ] No in-scope live defect or contract drift deferred to follow-up
- [ ] Independent sub-agent closure-audit completed and evidence recorded
- [ ] **Anti-Hollow Check**: Not applicable — this plan does not introduce new components or modify runtime behavior. All 3 fixes are cosmetic/maintenance (toString output, javadoc text, deprecation annotation).
- [ ] `./mvnw compile -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime -am`
- [ ] `./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime -am`
- [ ] `source-anchors.md` synchronized with live baseline (nop-stream entries added)
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-flink-comparison/2026-07-25-1800-4-post-completion-housekeeping.md --strict` exits 0
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` exits 0

## Deferred But Adjudicated

### Multi-input operator infrastructure (BarrierAligner wiring, multi-input watermark, BroadcastState)

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: All three deferred items from completed plans require multi-input operators (ConnectedStreams, connect(), union()) not in codebase. Addressing piecemeal without the infrastructure would create unverifiable code. Requires an architecture-level plan beyond this scope.
- Successor Required: `yes`

## Non-Blocking Follow-ups

- (none beyond deferred items above)

## Closure

Status Note: <<filled on completion>>
Completed: YYYY-MM-DD

Closure Audit Evidence:

- Reviewer / Agent: <<independent reviewer>>
- Evidence: <<PASS/FAIL results for each exit criterion and closure gate>>
