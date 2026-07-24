# 316 Flink 核心源码结构审计

> Plan Status: active
> Last Reviewed: 2026-07-24
> Source: `docs/backlog/nop-stream-flink-comparison-roadmap.md` Item 1
> Related: `ai-dev/plans/317-nopstream-live-audit.md`

## Purpose

Systematically audit Flink's key source packages (streaming-api, runtime, checkpoint, state, window, CEP) to establish a precise Flink baseline for subsequent nop-stream comparison. Deliverable is an analysis document in `ai-dev/analysis/nop-stream/`.

## Current Baseline

- nop-stream design docs exist at `ai-dev/design/nop-stream/` (15 documents covering vision, architecture, core, graph, checkpoint, state, window, time, CEP, connector, DSL)
- `ai-dev/design/nop-stream/comparison.md` exists but is architecture-level, not source-level — lacks class-level mapping, method signatures, execution flow details
- No source-level audit of Flink has been done in this repo
- Flink source expected at `~/sources/flink/` (tag `release-1.20.0`); if absent, must clone
- `ai-dev/analysis/nop-stream/` is empty — no analysis artifacts exist yet
- Roadmap items 3-7 (checkpoint/barrier, state, window, CEP, distributed execution comparison) all depend on this audit

## Goals

- Produce a source-level audit of Flink's 6 key packages with class hierarchy maps, execution flow diagrams, and design pattern identification
- Record exact class names, method signatures, and inter-component wiring for each subsystem
- Establish the Flink baseline that items 3-7 will compare against

## Non-Goals

- Not a line-by-line code review of Flink — structural mapping, not exhaustive reading
- Not an analysis of nop-stream (separate plan: item 2)
- No nop-stream code changes
- Flink Table/SQL API, PyFlink, ML, Gelly, and other non-core modules are out of scope

## Scope

### In Scope

- Flink streaming-api: DataStream/KeyedStream/WindowedStream, Transformation hierarchy, TypeInformation
- Flink runtime execution model: StreamTask/Mailbox, task lifecycle, InputProcessor, CheckpointedInputGate
- Flink checkpoint coordinator: CheckpointCoordinator, PendingCheckpoint, CompletedCheckpointStore
- Flink state system: StateBackend, KeyedStateBackend, OperatorStateBackend, Key-Group
- Flink window/time: WindowOperator, InternalTimerService, WatermarkStrategy/StatusWatermarkValve
- Flink CEP: NFA/SharedBuffer/CepOperator
- Flink distributed execution: ExecutionGraph, Scheduler, Slot/ResourceManager, RPC abstraction

### Out Of Scope

- Flink Table/SQL API, PyFlink, ML, Gelly
- Detailed line-by-line reading of utility classes
- nop-stream code or comparison (delegated to items 2-8)

### Audit Depth Guardrails (stopping rule)

- Per package: document the **execution-relevant** classes (those that appear on the user-visible data/control path from entry point to effect). Utility/helper/exception classes are secondary — documented only if they carry non-trivial design decisions.
- Per class: document **public API surface** (entry methods, key fields, state transitions) plus **identity of primary collaborators** (by class name). Do NOT enumerate every private method or field.
- Target: 5-15 classes per subsystem (streaming-api, runtime, checkpoint, state, window/time, CEP, distributed), selected by their role in the execution path.
- Total deliverable length: ~10-30 pages (not 100+). Balance completeness with conciseness.

## Execution Plan

### Phase 1 - Flink Source Audit Deliverable

Status: planned
Targets: `~/sources/flink/` (flink-streaming-java, flink-runtime, flink-core, flink-cep)

- Item Types: `Proof | Decision | Follow-up`

- [ ] Clone or verify Flink source at `~/sources/flink/` (tag `release-1.20.0`)
- [ ] Audit streaming-api package: map DataStream/KeyedStream/WindowedStream APIs, Transformation hierarchy, TypeInformation
- [ ] Audit runtime package: map StreamTask lifecycle, Mailbox pattern, InputProcessor/CheckpointedInputGate
- [ ] Audit checkpoint package: map CheckpointCoordinator, PendingCheckpoint, CompletedCheckpointStore, CheckpointBarrierHandler
- [ ] Audit state package: map StateBackend hierarchy, KeyedStateBackend, OperatorStateBackend, Key-Group design
- [ ] Audit window/time package: map WindowOperator, InternalTimerService, WatermarkStrategy, StatusWatermarkValve
- [ ] Audit CEP package: map NFA/SharedBuffer/CepOperator, verify components nop-stream-cep peeled from
- [ ] Audit distributed execution: map ExecutionGraph state machine, Scheduler, Slot allocation, RPC abstraction
- [ ] Write deliverable at `ai-dev/analysis/nop-stream/01-flink-source-audit.md` covering all above. Deliverable structure: one section per Flink subsystem, each containing (a) class hierarchy table with key classes and their roles, (b) key method signatures for entry points, (c) wiring description showing how classes collaborate on the critical path, (d) design patterns observed. Coordinate with plan 2's deliverable (`02-nopstream-live-audit.md`) so both use a compatible schema — e.g., each comparison plan (items 3-7) expects to pair a "Flink X" subsection with a "nop-stream X" subsection; plan 2 should use the same subsystem partitioning.

Exit Criteria:

> Each Exit Criterion must be `[x]` before Phase Status becomes `completed`.

- [ ] Flink source verified available at `~/sources/flink/` with `release-1.20.0` checked out
- [ ] Deliverable `ai-dev/analysis/nop-stream/01-flink-source-audit.md` exists, covering all 6 audit areas with explicit class names, method signatures, and wiring descriptions
- [ ] Deliverable passes independent sub-agent review (different task_id, no Blocker remaining)
- [ ] No owner-doc update required (analysis-only plan, no live baseline change)
- [ ] `ai-dev/logs/` corresponding date entry updated

## Closure Gates

> All items below and all Phase Exit Criteria must be `[x]` before `Plan Status` can be `completed`.

- [ ] Flink baseline documented in `ai-dev/analysis/nop-stream/01-flink-source-audit.md` with sufficient detail to enable items 3-7
- [ ] Deliverable has passed independent sub-agent review with no Blocker
- [ ] `ai-dev/logs/` entry recorded
- [ ] Independent sub-agent closure-audit completed and evidence recorded
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <this-plan-file> --strict` exits 0 (no un-checked items, closure evidence present)

## Deferred But Adjudicated

(No deferred items at draft time)

## Non-Blocking Follow-ups

(No non-blocking follow-ups at draft time)

## Closure

Status Note: (to be filled on completion)
Completed: (to be filled on completion)

Closure Audit Evidence:

(to be filled by independent sub-agent on closure)

Follow-up:

- (to be filled on closure)
